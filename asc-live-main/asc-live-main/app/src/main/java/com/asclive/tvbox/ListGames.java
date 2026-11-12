package com.asclive.tvbox;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Acha jogos do dia olhando os NOMES dos canais da própria lista do usuário.
 * Listas de IPTV costumam ter canais de evento, como "Flamengo x Palmeiras 21:30".
 * Serve de plano B quando a fonte de jogos (ESPN) falha ou não tem o jogo.
 * Só lê a lista que já está guardada. NUNCA chamar na thread principal.
 */
final class ListGames {

    private static final int MAX_GAMES = 60;
    private static final Pattern SPLIT =
            Pattern.compile("\\s+(?:x|vs\\.?|v)\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile("\\b(\\d{1,2})\\s*[:h]\\s*(\\d{2})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACKETS = Pattern.compile("\\[[^\\]]*\\]|\\([^)]*\\)");
    private static final Pattern TAGS = Pattern.compile(
            "\\b(?:fhd|uhd|hd|sd|4k|h\\.?26[45]|hevc|50\\s*fps|60\\s*fps|ao vivo|live)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RIGHT_CUT =
            Pattern.compile("\\s[-\\u2013|]\\s|\\||\\b\\d{1,2}\\s*[:h]\\s*\\d{2}\\b");

    private ListGames() {
    }

    /**
     * Junta os jogos da fonte (ESPN) com os achados na lista. Nunca falha: se a lista não
     * puder ser lida, devolve só os jogos da fonte. Jogos que a fonte já tem não se repetem.
     */
    static List<Match> merge(Context ctx, List<Match> source) {
        List<Match> out = new ArrayList<>(source);
        try {
            for (Match g : scan(ctx)) {
                boolean dup = false;
                for (Match s : source) {
                    if (sameGame(s, g)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    out.add(g);
                }
            }
        } catch (Exception ignored) {
            // sem lista agora: ficam só os jogos da fonte
        }
        return out;
    }

    /** Jogos que aparecem nos nomes dos canais ("Time A x Time B"). */
    static List<Match> scan(Context ctx) throws java.io.IOException {
        List<XtreamApi.Item> items = Catalog.allLive(ctx);
        List<Match> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (XtreamApi.Item it : items) {
            if (out.size() >= MAX_GAMES) {
                break;
            }
            Match m = parse(it.name);
            if (m == null) {
                continue;
            }
            String k = ChannelNames.queryKey(m.casa) + "|" + ChannelNames.queryKey(m.fora);
            if (seen.add(k)) {
                out.add(m);
            }
        }
        Collections.sort(out, new Comparator<Match>() {
            @Override
            public int compare(Match a, Match b) {
                // Com horário primeiro (pela hora); sem horário no fim.
                long ta = a.horario <= 0 ? Long.MAX_VALUE : a.horario;
                long tb = b.horario <= 0 ? Long.MAX_VALUE : b.horario;
                return ta < tb ? -1 : (ta > tb ? 1 : 0);
            }
        });
        return out;
    }

    /** Lê um nome de canal. Devolve o jogo, ou null se o nome não parece "A x B". */
    static Match parse(String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = rawName.replace('\u00d7', 'x');
        Matcher sp = SPLIT.matcher(name);
        if (!sp.find()) {
            return null;
        }
        String left = cleanLeft(name.substring(0, sp.start()));
        String right = cleanRight(name.substring(sp.end()));
        if (!plausible(left) || !plausible(right)) {
            return null;
        }
        long when = timeIn(name);
        String note = when > 0 ? "" : "Na sua lista";
        String id = "lista:" + ChannelNames.queryKey(left) + "|" + ChannelNames.queryKey(right);
        return new Match(id, "lista", "Da sua lista", left, right, "", "", when,
                Match.SCHEDULED, note, "");
    }

    private static boolean plausible(String team) {
        if (team.length() < 3 || team.length() > 32) {
            return false;
        }
        // Precisa ter letras (nome de time), não só números.
        for (int i = 0; i < team.length(); i++) {
            if (Character.isLetter(team.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String cleanLeft(String s) {
        String t = BRACKETS.matcher(s).replaceAll(" ");
        t = TAGS.matcher(t).replaceAll(" ");
        int cut = Math.max(Math.max(t.lastIndexOf(':'), t.lastIndexOf('|')),
                Math.max(t.lastIndexOf(" - "), t.lastIndexOf(" \u2013 ")));
        if (cut >= 0) {
            t = t.substring(cut + 1);
        }
        t = TIME.matcher(t).replaceAll(" ");
        return tidy(t);
    }

    private static String cleanRight(String s) {
        String t = BRACKETS.matcher(s).replaceAll(" ");
        Matcher m = RIGHT_CUT.matcher(t);
        if (m.find()) {
            t = t.substring(0, m.start());
        }
        t = TAGS.matcher(t).replaceAll(" ");
        return tidy(t);
    }

    private static String tidy(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        while (!t.isEmpty() && !Character.isLetterOrDigit(t.charAt(0))) {
            t = t.substring(1).trim();
        }
        while (!t.isEmpty() && !Character.isLetterOrDigit(t.charAt(t.length() - 1))) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }

    /** Hoje no horário achado no nome (ex.: 21:30), ou 0 se o nome não tem horário. */
    private static long timeIn(String name) {
        Matcher m = TIME.matcher(name);
        if (!m.find()) {
            return 0L;
        }
        int h;
        int min;
        try {
            h = Integer.parseInt(m.group(1));
            min = Integer.parseInt(m.group(2));
        } catch (NumberFormatException e) {
            return 0L;
        }
        if (h > 23 || min > 59) {
            return 0L;
        }
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, min);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ---------- Comparar jogos ----------

    private static boolean sameGame(Match a, Match b) {
        return (sameTeam(a.casa, b.casa) && sameTeam(a.fora, b.fora))
                || (sameTeam(a.casa, b.fora) && sameTeam(a.fora, b.casa));
    }

    /** Mesmo time, mesmo com nome escrito diferente ("Atlético-MG" e "Atletico Mineiro"). */
    private static boolean sameTeam(String x, String y) {
        String kx = ChannelNames.queryKey(x);
        String ky = ChannelNames.queryKey(y);
        if (kx.isEmpty() || ky.isEmpty()) {
            return false;
        }
        if (kx.equals(ky)) {
            return true;
        }
        String cx = core(kx);
        String cy = core(ky);
        return (!cx.isEmpty() && (" " + ky + " ").contains(" " + cx + " "))
                || (!cy.isEmpty() && (" " + kx + " ").contains(" " + cy + " "));
    }

    /** A palavra mais comprida do nome (a mais característica do time). */
    private static String core(String key) {
        String best = "";
        for (String w : key.split(" ")) {
            if (w.length() >= 4 && w.length() > best.length()) {
                best = w;
            }
        }
        return best;
    }
}
