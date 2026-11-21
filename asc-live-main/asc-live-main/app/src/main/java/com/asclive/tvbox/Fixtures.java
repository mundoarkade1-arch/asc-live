package com.asclive.tvbox;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Calendário de jogos de todas as ligas (de ligas.json) numa janela de datas: alguns dias
 * para trás (resultados) e algumas semanas para frente (próximos jogos). Uma consulta por liga,
 * guardada em arquivo por 30 minutos; todos os times usam a mesma lista.
 * Faz rede e disco: NUNCA chamar na thread principal.
 */
final class Fixtures {

    private static final int DAYS_BACK = 10;
    private static final int DAYS_AHEAD = 35;
    private static final long TTL_MS = 30L * 60L * 1000L;
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private static final int MAX_CACHE_BYTES = 6 * 1024 * 1024;
    private static final String CACHE_FILE = "fixtures.json";

    private static List<Match> memo;
    private static long memoTime;
    /** Último motivo de falha de rede (para mostrar na tela e achar o problema). */
    private static volatile String lastError = "";
    /** O que cada passo da busca de jogos do time achou (aparece na tela se não achar nada). */
    private static volatile String diag = "";

    static String lastDiag() {
        return diag;
    }

    private Fixtures() {
    }

    /** Todos os jogos da janela, em ordem de horário. */
    static synchronized List<Match> all(Context ctx) throws IOException {
        long now = System.currentTimeMillis();
        if (memo != null && now - memoTime < TTL_MS) {
            return memo;
        }
        Context app = ctx.getApplicationContext();
        File f = new File(app.getCacheDir(), CACHE_FILE);
        long[] when = new long[1];
        List<Match> cached = readCache(f, when);
        if (cached != null && now - when[0] < TTL_MS) {
            memo = cached;
            memoTime = when[0];
            return memo;
        }
        List<Match> fresh = download(app);
        if (fresh.isEmpty()) {
            if (cached != null) {
                memo = cached;
                memoTime = now;
                return memo;
            }
            throw new IOException("sem jogos" + (lastError.isEmpty() ? "" : " (" + lastError + ")"));
        }
        writeCache(f, fresh);
        memo = fresh;
        memoTime = now;
        return memo;
    }

    static synchronized void clearCache(Context ctx) {
        memo = null;
        //noinspection ResultOfMethodCallIgnored
        new File(ctx.getApplicationContext().getCacheDir(), CACHE_FILE).delete();
    }

    /** Até onde olhar para a frente na liga do próprio time (para mostrar TODOS os próximos). */
    private static final int TEAM_DAYS_AHEAD = 240;

    /** Jogos do time (por id; se a fonte não deu id, pelo nome), em ordem de horário. */
    static List<Match> forTeam(Context ctx, Teams.Team team) throws IOException {
        List<Match> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        StringBuilder d = new StringBuilder();
        d.append("time ").append(team.name).append(" (").append(team.id).append(") liga ")
                .append(team.ligaId);
        // 1) A liga do time sozinha, olhando longe: um pedido pequeno e rápido.
        try {
            String ligaName = team.ligaId;
            for (String[] l : FootballApi.leagues(ctx.getApplicationContext())) {
                if (l[0].equals(team.ligaId)) {
                    ligaName = l[1];
                }
            }
            if (team.ligaId.matches("[a-z0-9._-]{1,60}")) {
                List<Match> liga = fetchLeagueResilient(team.ligaId, ligaName, TEAM_DAYS_AHEAD);
                int mine = 0;
                for (Match m : liga) {
                    if (isTeam(team, m) && seen.add(m.ligaId + "|" + m.id)) {
                        out.add(m);
                        mine++;
                    }
                }
                d.append("; liga: ").append(liga.size()).append(" jogos, ").append(mine)
                        .append(" do time");
            }
        } catch (Exception e) {
            d.append("; liga: erro ").append(String.valueOf(e.getMessage()));
        }
        // 2) Todas as ligas (traz também Libertadores, Copa do Brasil etc.).
        IOException failure = null;
        try {
            int mine = 0;
            List<Match> every = all(ctx);
            for (Match m : every) {
                if (isTeam(team, m) && seen.add(m.ligaId + "|" + m.id)) {
                    out.add(m);
                    mine++;
                }
            }
            d.append("; todas: ").append(every.size()).append(" jogos, ").append(mine)
                    .append(" novos do time");
        } catch (IOException e) {
            failure = e;
            d.append("; todas: erro ").append(String.valueOf(e.getMessage()));
        }
        // 3) Calendário do próprio time (rota da ESPN), sempre: pode ter jogos mais à frente.
        List<Match> own = teamSchedule(team);
        int added = 0;
        for (Match m : own) {
            if (seen.add(m.ligaId + "|" + m.id)) {
                out.add(m);
                added++;
            }
        }
        d.append("; calendário do time: ").append(own.size()).append(" jogos, ").append(added)
                .append(" novos");
        diag = d.toString();
        if (out.isEmpty() && failure != null) {
            throw failure;
        }
        sort(out);
        return out;
    }

    private static boolean isTeam(Teams.Team team, Match m) {
        return m.hasTeam(team.id) || sameName(team.name, m.casa) || sameName(team.name, m.fora);
    }

    /** Jogos em que algum time tem todas as palavras digitadas. */
    static List<Match> search(Context ctx, String text) throws IOException {
        String q = ChannelNames.queryKey(text);
        List<Match> out = new ArrayList<>();
        if (q.isEmpty()) {
            return out;
        }
        String[] words = q.split(" ");
        for (Match m : all(ctx)) {
            if (hasWords(m.casa, words) || hasWords(m.fora, words)) {
                out.add(m);
            }
        }
        sort(out);
        return out;
    }

    private static boolean hasWords(String team, String[] words) {
        String k = ChannelNames.queryKey(team);
        for (String w : words) {
            if (!k.contains(w)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameName(String a, String b) {
        String ka = ChannelNames.queryKey(a);
        return !ka.isEmpty() && ka.equals(ChannelNames.queryKey(b));
    }

    private static void sort(List<Match> list) {
        Collections.sort(list, new Comparator<Match>() {
            @Override
            public int compare(Match a, Match b) {
                return a.horario < b.horario ? -1 : (a.horario > b.horario ? 1 : 0);
            }
        });
    }

    // ---------- Rede ----------

    private static String day(int shift) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, shift);
        return String.format(Locale.US, "%04d%02d%02d", c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static List<Match> download(Context app) {
        List<String[]> leagues = FootballApi.leagues(app);
        List<Match> out = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<List<Match>>> jobs = new ArrayList<>();
            for (final String[] l : leagues) {
                jobs.add(new Callable<List<Match>>() {
                    @Override
                    public List<Match> call() throws Exception {
                        return fetchLeagueResilient(l[0], l[1], DAYS_AHEAD);
                    }
                });
            }
            Set<String> seen = new HashSet<>();
            for (Future<List<Match>> f : pool.invokeAll(jobs, 40, TimeUnit.SECONDS)) {
                try {
                    for (Match m : f.get()) {
                        if (seen.add(m.ligaId + "|" + m.id)) {
                            out.add(m);
                        }
                    }
                } catch (Exception e) {
                    // essa liga falhou: as outras continuam
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    lastError = String.valueOf(c.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        sort(out);
        return out;
    }

    /**
     * Uma liga, com plano B: se o pedido grande falhar, tenta sem "limit", depois uma janela
     * menor, e por fim o dia de hoje. Só falha se todos falharem.
     */
    private static List<Match> fetchLeagueResilient(String id, String name, int ahead)
            throws Exception {
        List<Match> out = new ArrayList<>();
        if (!id.matches("[a-z0-9._-]{1,60}")) {
            return out;
        }
        String wide = day(-DAYS_BACK) + "-" + day(ahead);
        String normal = day(-DAYS_BACK) + "-" + day(DAYS_AHEAD);
        String mid = day(-3) + "-" + day(14);
        String[] queries = {"?limit=500&dates=" + wide, "?dates=" + wide,
                "?limit=500&dates=" + normal, "?dates=" + mid, ""};
        Exception last = null;
        for (String q : queries) {
            try {
                JSONObject root = FootballApi.getJson(FootballApi.base() + id + "/scoreboard" + q,
                        MAX_BYTES);
                readEvents(root.optJSONArray("events"), id, name, out);
                return out;
            } catch (Exception e) {
                last = e;
                lastError = String.valueOf(e.getMessage());
            }
        }
        throw last == null ? new IOException("sem resposta") : last;
    }

    /** O calendário do próprio time (rota da ESPN), com mais de uma tentativa. */
    private static List<Match> teamSchedule(Teams.Team team) {
        List<Match> out = new ArrayList<>();
        if (!team.ligaId.matches("[a-z0-9._-]{1,60}") || !team.id.matches("[0-9]{1,12}")) {
            return out;
        }
        String[] tails = {"", "?fixture=true"};
        Set<String> seen = new HashSet<>();
        for (String tail : tails) {
            try {
                JSONObject root = FootballApi.getJson(FootballApi.base() + team.ligaId
                        + "/teams/" + team.id + "/schedule" + tail, MAX_BYTES);
                List<Match> part = new ArrayList<>();
                readEvents(root.optJSONArray("events"), team.ligaId, team.ligaId, part);
                for (Match m : part) {
                    if (seen.add(m.ligaId + "|" + m.id)) {
                        out.add(m);
                    }
                }
            } catch (Exception ignored) {
                // essa rota não respondeu: tenta a outra
            }
        }
        return out;
    }

    private static void readEvents(JSONArray events, String ligaId, String ligaName,
                                   List<Match> out) {
        for (int i = 0; events != null && i < events.length(); i++) {
            Match m = FootballApi.parseEvent(ligaId, ligaName, events.optJSONObject(i));
            if (m != null) {
                out.add(m);
            }
        }
    }

    // ---------- Cache ----------

    private static List<Match> readCache(File f, long[] when) {
        try {
            if (!f.exists() || f.length() > MAX_CACHE_BYTES) {
                return null;
            }
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            JSONObject root = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
            when[0] = root.optLong("t", 0L);
            JSONArray a = root.getJSONArray("m");
            List<Match> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                out.add(Match.fromJson(a.getJSONObject(i)));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeCache(File f, List<Match> matches) {
        try {
            JSONArray a = new JSONArray();
            for (Match m : matches) {
                a.put(m.toJson());
            }
            JSONObject root = new JSONObject();
            root.put("t", System.currentTimeMillis());
            root.put("m", a);
            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_CACHE_BYTES) {
                return;
            }
            FileOutputStream out = new FileOutputStream(f);
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
        } catch (Exception ignored) {
            // sem espaço: só não guarda
        }
    }
}
