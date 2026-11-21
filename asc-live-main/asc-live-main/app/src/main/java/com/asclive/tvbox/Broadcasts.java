package com.asclive.tvbox;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Quais canais da lista passam um jogo. Nenhuma fonte grátis é 100% certa, então são duas camadas:
 * 1) a tabela assets/transmissoes.json (liga -> emissoras), que o dono edita sem mexer no código;
 * 2) canais de evento da própria lista, com o nome dos times (ex.: "Flamengo x Palmeiras");
 *    os que têm os dois times vêm primeiro, mesmo se o nome do time estiver escrito diferente.
 *
 * Faz leitura de arquivo e percorre a lista de canais: NUNCA chamar na thread principal.
 * Cada resolução de um canal (SD, HD, FHD, 4K) é um canal à parte, como no ASC TV Box.
 */
final class Broadcasts {

    /** Uma emissora (ex.: Premiere) e os canais dela na lista (Premiere 1, 2, 3...). */
    static final class Broadcast {
        final String emissora;
        final List<ChannelGroup> canais;

        Broadcast(String emissora, List<ChannelGroup> canais) {
            this.emissora = emissora;
            this.canais = canais;
        }
    }

    private static final String EVENT_TITLE = "Canais do jogo";
    private static final int MAX_PER_BROADCAST = 120;
    private static final int MIN_TEAM_LEN = 4;

    private static JSONObject table;

    private Broadcasts() {
    }

    /**
     * Emissoras que passam o jogo, com os canais de cada uma. Os canais de evento (nome do time)
     * vêm primeiro. Emissoras sem nenhum canal na lista não aparecem. Pode devolver lista vazia.
     */
    static List<Broadcast> channelsFor(Context ctx, Match m) throws IOException {
        List<ChannelGroup> all = Catalog.allGroups(ctx);
        List<Broadcast> out = new ArrayList<>();
        Set<String> used = new HashSet<>();

        // Camada 2: canais com o nome dos times. Os que têm os DOIS times vêm primeiro.
        List<String> homeTok = tokens(m.casa);
        List<String> awayTok = tokens(m.fora);
        List<ChannelGroup> both = new ArrayList<>();
        List<ChannelGroup> one = new ArrayList<>();
        for (ChannelGroup g : all) {
            boolean h = hasAll(g.key(), homeTok);
            boolean a = hasAll(g.key(), awayTok);
            if (h && a) {
                both.add(g);
            } else if (h || a) {
                one.add(g);
            }
            if (both.size() + one.size() >= MAX_PER_BROADCAST) {
                break;
            }
        }
        if (both.isEmpty() && one.isEmpty()) {
            // Plano B: só a palavra mais característica de cada time.
            String hw = longest(homeTok);
            String aw = longest(awayTok);
            for (ChannelGroup g : all) {
                if (matches(g.key(), hw, MIN_TEAM_LEN) || matches(g.key(), aw, MIN_TEAM_LEN)) {
                    one.add(g);
                    if (one.size() >= MAX_PER_BROADCAST) {
                        break;
                    }
                }
            }
        }
        sortNatural(both);
        sortNatural(one);
        List<ChannelGroup> event = new ArrayList<>(both);
        event.addAll(one);
        if (!event.isEmpty()) {
            for (ChannelGroup g : event) {
                used.add(g.key());
            }
            out.add(new Broadcast(EVENT_TITLE, event));
        }

        // Camada 1: tabela de emissoras da liga.
        JSONObject t = loadTable(ctx);
        JSONObject names = t.optJSONObject("_nomes");
        JSONArray words = t.optJSONArray(m.ligaId);
        if (words == null) {
            words = t.optJSONArray("*");
        }
        if (words != null) {
            for (int i = 0; i < words.length(); i++) {
                String word = words.optString(i, "").trim();
                String q = ChannelNames.queryKey(word);
                if (q.isEmpty()) {
                    continue;
                }
                List<ChannelGroup> hit = new ArrayList<>();
                for (ChannelGroup g : all) {
                    if (!used.contains(g.key()) && matches(g.key(), q, 2)) {
                        hit.add(g);
                        if (hit.size() >= MAX_PER_BROADCAST) {
                            break;
                        }
                    }
                }
                if (hit.isEmpty()) {
                    continue;
                }
                sortNatural(hit);
                for (ChannelGroup g : hit) {
                    used.add(g.key());
                }
                out.add(new Broadcast(niceName(names, word), hit));
            }
        }
        return out;
    }

    /** Quem diz o quê: emissoras do Brasil informadas pela grade de TV, por jogo (para a tela). */
    private static final Map<String, String> INFO = new HashMap<>();

    /** Emissoras (nomes) que a grade de TV do Brasil informa para o jogo, ou "" se não souber. */
    static String infoFor(Match m) {
        synchronized (INFO) {
            String v = INFO.get(m.ligaId + "|" + m.id);
            return v == null ? "" : v;
        }
    }

    /**
     * Só os canais CERTOS do jogo (nada de "costuma passar"). Três fontes, nesta ordem:
     * 1) canais de evento da própria lista com os DOIS times no nome (ex.: "Santos x Palmeiras");
     * 2) a programação (EPG) da própria lista: canal esportivo cujo programa tem os dois times
     *    (só para jogos nas próximas 36 horas e só em lista por servidor);
     * 3) a grade de TV do Brasil (TheSportsDB): as emissoras que ela informa para o jogo,
     *    procuradas pelo nome inteiro na lista (nada de nome parecido).
     * As emissoras que a ESPN informa NÃO são usadas: são dos EUA e davam canal aleatório.
     * Pode devolver lista vazia: ainda não há transmissão confirmada. Cada resolução é um canal.
     * NUNCA chamar na thread principal.
     */
    static List<ChannelGroup> confirmedFor(Context ctx, Match m) throws IOException {
        List<ChannelGroup> all = Catalog.allGroups(ctx);
        List<ChannelGroup> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        List<String> homeTok = tokens(m.casa);
        List<String> awayTok = tokens(m.fora);

        // 1) Canais de evento com os dois times.
        List<ChannelGroup> both = new ArrayList<>();
        for (ChannelGroup g : all) {
            if (hasAll(g.key(), homeTok) && hasAll(g.key(), awayTok)) {
                both.add(g);
                if (both.size() >= MAX_PER_BROADCAST) {
                    break;
                }
            }
        }
        sortNatural(both);
        for (ChannelGroup g : both) {
            if (used.add(g.key())) {
                out.add(g);
            }
        }

        // 2) Programação da própria lista.
        List<ChannelGroup> epg = epgHits(ctx, all, m, homeTok, awayTok);
        sortNatural(epg);
        for (ChannelGroup g : epg) {
            if (used.add(g.key())) {
                out.add(g);
            }
        }

        // 3) Grade de TV do Brasil.
        List<String> names = tvNames(m);
        StringBuilder info = new StringBuilder();
        for (String name : names) {
            if (info.length() > 0) {
                info.append(", ");
            }
            info.append(name);
            List<ChannelGroup> hit = wholeNameHits(all, used, name);
            sortNatural(hit);
            for (ChannelGroup g : hit) {
                if (used.add(g.key())) {
                    out.add(g);
                }
            }
        }
        synchronized (INFO) {
            INFO.put(m.ligaId + "|" + m.id, info.toString());
        }
        return out;
    }

    // ---------- Fonte 2: programação (EPG) da própria lista ----------

    private static final long EPG_WINDOW_MS = 36L * 3600L * 1000L;
    private static final long EPG_NEAR_MS = 4L * 3600L * 1000L;
    private static final long EPG_TTL_MS = 5L * 60L * 1000L;
    private static final int EPG_MAX_CHANNELS = 100;

    private static final class EpgEntry {
        final long at;
        final List<XtreamApi.Program> programs;

        EpgEntry(long at, List<XtreamApi.Program> programs) {
            this.at = at;
            this.programs = programs;
        }
    }

    private static final Map<String, EpgEntry> EPG = new HashMap<>();

    private static List<ChannelGroup> epgHits(Context ctx, List<ChannelGroup> all, final Match m,
                                              final List<String> homeTok,
                                              final List<String> awayTok) {
        List<ChannelGroup> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (m.status == Match.FINISHED || m.horario - now > EPG_WINDOW_MS) {
            return out;
        }
        final CredentialStore.Credentials c = Catalog.creds(ctx);
        if (c == null || Catalog.isM3u(c)) {
            return out;
        }
        // Só canais de emissoras esportivas (as palavras da tabela transmissoes.json).
        Set<String> brands = new HashSet<>();
        JSONObject t = loadTable(ctx);
        Iterator<String> keys = t.keys();
        while (keys.hasNext()) {
            JSONArray a = t.optJSONArray(keys.next());
            for (int i = 0; a != null && i < a.length(); i++) {
                String q = ChannelNames.queryKey(a.optString(i, "").trim());
                if (q.length() >= 3) {
                    brands.add(q);
                }
            }
        }
        List<ChannelGroup> cands = new ArrayList<>();
        for (ChannelGroup g : all) {
            for (String q : brands) {
                if (startsWithWord(g.key(), q)) {
                    cands.add(g);
                    break;
                }
            }
            if (cands.size() >= EPG_MAX_CHANNELS) {
                break;
            }
        }
        if (cands.isEmpty()) {
            return out;
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<ChannelGroup>> jobs = new ArrayList<>();
            for (final ChannelGroup g : cands) {
                jobs.add(new Callable<ChannelGroup>() {
                    @Override
                    public ChannelGroup call() throws Exception {
                        List<XtreamApi.Program> ps = programsOf(c, g.item().id);
                        for (XtreamApi.Program p : ps) {
                            String key = ChannelNames.queryKey(p.title);
                            if (hasAll(key, homeTok) && hasAll(key, awayTok)
                                    && Math.abs(p.start - m.horario) <= EPG_NEAR_MS) {
                                return g;
                            }
                        }
                        return null;
                    }
                });
            }
            for (Future<ChannelGroup> f : pool.invokeAll(jobs, 20, TimeUnit.SECONDS)) {
                try {
                    ChannelGroup g = f.get();
                    if (g != null) {
                        out.add(g);
                    }
                } catch (Exception ignored) {
                    // esse canal não respondeu: os outros continuam
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        return out;
    }

    private static List<XtreamApi.Program> programsOf(CredentialStore.Credentials c,
                                                      String streamId) throws IOException {
        String key = c.server + "|" + c.user + "|" + streamId;
        long now = System.currentTimeMillis();
        synchronized (EPG) {
            EpgEntry e = EPG.get(key);
            if (e != null && now - e.at < EPG_TTL_MS) {
                return e.programs;
            }
        }
        List<XtreamApi.Program> ps = XtreamApi.schedule(c.server, c.user, c.pass, streamId);
        synchronized (EPG) {
            if (EPG.size() > 400) {
                EPG.clear();
            }
            EPG.put(key, new EpgEntry(now, ps));
        }
        return ps;
    }

    // ---------- Fonte 3: grade de TV do Brasil (TheSportsDB) ----------

    private static final long TV_TTL_MS = 30L * 60L * 1000L;

    private static final class TvDay {
        final long at;
        final List<String[]> rows;

        TvDay(long at, List<String[]> rows) {
            this.at = at;
            this.rows = rows;
        }
    }

    private static final Map<String, TvDay> TV = new HashMap<>();

    /** Nomes das emissoras do Brasil que a grade informa para este jogo (sem repetir). */
    private static List<String> tvNames(Match m) {
        List<String> out = new ArrayList<>();
        Set<String> days = new java.util.LinkedHashSet<>();
        Date when = new Date(m.horario);
        SimpleDateFormat local = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        days.add(local.format(when));
        days.add(utc.format(when));
        Set<String> seen = new HashSet<>();
        for (String day : days) {
            for (String[] row : tvDay(day)) {
                boolean straight = sameTeam(row[0], m.casa) && sameTeam(row[1], m.fora);
                boolean swapped = sameTeam(row[0], m.fora) && sameTeam(row[1], m.casa);
                if (!(straight || swapped)) {
                    continue;
                }
                String ch = row[2].trim();
                if (!ch.isEmpty() && seen.add(ChannelNames.queryKey(ch))) {
                    out.add(ch);
                }
            }
        }
        return out;
    }

    /** Linhas {casa, fora, canal} do Brasil num dia (yyyy-MM-dd). Vazio se a rede falhar. */
    private static List<String[]> tvDay(String day) {
        long now = System.currentTimeMillis();
        synchronized (TV) {
            TvDay d = TV.get(day);
            if (d != null && now - d.at < TV_TTL_MS) {
                return d.rows;
            }
        }
        List<String[]> rows = new ArrayList<>();
        try {
            JSONObject root = FootballApi.getJson(
                    "https://www.thesportsdb.com/api/v1/json/3/eventstv.php?d=" + day
                            + "&s=Soccer", 6 * 1024 * 1024);
            JSONArray a = root.optJSONArray("tvevents");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String country = clean(o.optString("strCountry", "")).toLowerCase(Locale.ROOT);
                if (!(country.contains("brazil") || country.contains("brasil"))) {
                    continue;
                }
                rows.add(new String[]{clean(o.optString("strHomeTeam", "")),
                        clean(o.optString("strAwayTeam", "")),
                        clean(o.optString("strChannel", ""))});
            }
        } catch (Exception e) {
            return rows; // sem rede agora: não guarda, tenta de novo da próxima vez
        }
        synchronized (TV) {
            if (TV.size() > 20) {
                TV.clear();
            }
            TV.put(day, new TvDay(now, rows));
        }
        return rows;
    }

    private static String clean(String s) {
        return s == null || "null".equals(s) ? "" : s;
    }

    private static final Set<String> TEAM_NOISE = new HashSet<>(java.util.Arrays.asList(
            "fc", "ec", "sc", "cf", "ac", "clube", "club", "futebol", "de", "da", "do"));

    private static String teamKey(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : ChannelNames.queryKey(s).split(" ")) {
            if (w.isEmpty() || TEAM_NOISE.contains(w)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(w);
        }
        return sb.toString();
    }

    /**
     * Mesmo time? Nome igual (sem "FC", "EC"...) ou o mesmo nome com a sigla do estado no fim
     * (ex.: "Botafogo" e "Botafogo RJ"). "Atlético" sozinho NUNCA vale: há vários (MG, GO, PR).
     * Na dúvida, diz que não: é melhor não confirmar do que confirmar o canal errado.
     */
    private static boolean sameTeam(String a, String b) {
        String ka = teamKey(a);
        String kb = teamKey(b);
        if (ka.isEmpty() || kb.isEmpty()) {
            return false;
        }
        if (ka.equals(kb)) {
            return true;
        }
        String shortK = ka.length() <= kb.length() ? ka : kb;
        String longK = shortK == ka ? kb : ka;
        if (shortK.length() < 6 || shortK.equals("atletico") || !longK.startsWith(shortK + " ")) {
            return false;
        }
        return longK.substring(shortK.length() + 1).matches("[a-z]{2}");
    }

    /** A chave do canal é o texto, ou começa com ele seguido de espaço ("globo" acha "globo sp"). */
    private static boolean startsWithWord(String key, String q) {
        return key.equals(q) || key.startsWith(q + " ");
    }

    /**
     * Canais da lista cuja chave começa com o nome da emissora (palavra inteira). Se o nome tem
     * mais de uma palavra e nada bate (ex.: "ESPN Brasil"), tenta só a primeira palavra.
     */
    private static List<ChannelGroup> wholeNameHits(List<ChannelGroup> all, Set<String> used,
                                                    String name) {
        List<ChannelGroup> out = new ArrayList<>();
        String q = ChannelNames.queryKey(name);
        if (q.length() < 3) {
            return out;
        }
        collect(all, used, q, out);
        if (out.isEmpty()) {
            int sp = q.indexOf(' ');
            if (sp >= 3) {
                collect(all, used, q.substring(0, sp), out);
            }
        }
        return out;
    }

    private static void collect(List<ChannelGroup> all, Set<String> used, String q,
                                List<ChannelGroup> out) {
        for (ChannelGroup g : all) {
            if (!used.contains(g.key()) && startsWithWord(g.key(), q)) {
                out.add(g);
                if (out.size() >= MAX_PER_BROADCAST) {
                    return;
                }
            }
        }
    }

    private static final Set<String> STOP = new HashSet<>(java.util.Arrays.asList(
            "club", "clube", "futbol", "futebol", "football"));

    /** Palavras do nome do time (sem acento, minúsculas, 4 letras ou mais). */
    private static List<String> tokens(String team) {
        List<String> out = new ArrayList<>();
        String key = ChannelNames.queryKey(team);
        for (String w : key.split(" ")) {
            if (w.length() >= MIN_TEAM_LEN && !STOP.contains(w)) {
                out.add(w);
            }
        }
        if (out.isEmpty() && key.length() >= MIN_TEAM_LEN) {
            out.add(key);
        }
        return out;
    }

    /** O canal tem TODAS as palavras do time? (lista vazia = não) */
    private static boolean hasAll(String key, List<String> toks) {
        if (toks.isEmpty()) {
            return false;
        }
        for (String t : toks) {
            if (!matches(key, t, MIN_TEAM_LEN)) {
                return false;
            }
        }
        return true;
    }

    private static String longest(List<String> toks) {
        String best = "";
        for (String t : toks) {
            if (t.length() > best.length()) {
                best = t;
            }
        }
        return best;
    }

    /**
     * A chave do canal começa com o texto, ou o texto começa uma das palavras dela
     * ("globo" acha "globo sp"; "flamengo" acha "tv flamengo"). minLen evita textos curtos demais.
     */
    private static boolean matches(String key, String q, int minLen) {
        if (q == null || q.length() < minLen) {
            return false;
        }
        return key.startsWith(q) || key.contains(" " + q);
    }

    /**
     * Ordem: pelo nome sem a qualidade (nome mais curto primeiro, depois alfabética: 1, 2, ..., 10)
     * e, no mesmo canal, da melhor resolução para a pior (FHD, HD, SD, 4K).
     */
    private static void sortNatural(List<ChannelGroup> list) {
        Collections.sort(list, new Comparator<ChannelGroup>() {
            @Override
            public int compare(ChannelGroup a, ChannelGroup b) {
                String ka = ChannelNames.key(a.nome);
                String kb = ChannelNames.key(b.nome);
                if (ka.length() != kb.length()) {
                    return ka.length() < kb.length() ? -1 : 1;
                }
                int c = ka.compareTo(kb);
                if (c != 0) {
                    return c;
                }
                int ra = ChannelNames.rank(a.quality());
                int rb = ChannelNames.rank(b.quality());
                return ra == rb ? 0 : (ra < rb ? -1 : 1);
            }
        });
    }

    private static String niceName(JSONObject names, String word) {
        if (names != null) {
            String n = names.optString(word.toLowerCase(Locale.ROOT), "");
            if (!n.isEmpty()) {
                return n;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String part : word.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static synchronized JSONObject loadTable(Context ctx) {
        if (table != null) {
            return table;
        }
        try {
            InputStream in = ctx.getApplicationContext().getAssets().open("transmissoes.json");
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0 && bos.size() < 256 * 1024) {
                    bos.write(buf, 0, n);
                }
                table = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
            } finally {
                in.close();
            }
        } catch (Exception e) {
            table = new JSONObject();
        }
        return table;
    }
}
