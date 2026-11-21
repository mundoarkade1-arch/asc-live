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
            throw new IOException("sem jogos");
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

    /** Jogos do time (por id; se a fonte não deu id, pelo nome), em ordem de horário. */
    static List<Match> forTeam(Context ctx, Teams.Team team) throws IOException {
        List<Match> out = new ArrayList<>();
        for (Match m : all(ctx)) {
            if (m.hasTeam(team.id) || sameName(team.name, m.casa) || sameName(team.name, m.fora)) {
                out.add(m);
            }
        }
        if (out.isEmpty()) {
            out.addAll(teamSchedule(team));
        }
        sort(out);
        return out;
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
        final String range = day(-DAYS_BACK) + "-" + day(DAYS_AHEAD);
        List<String[]> leagues = FootballApi.leagues(app);
        List<Match> out = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Callable<List<Match>>> jobs = new ArrayList<>();
            for (final String[] l : leagues) {
                jobs.add(new Callable<List<Match>>() {
                    @Override
                    public List<Match> call() throws Exception {
                        return fetchLeague(l[0], l[1], range);
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
                } catch (Exception ignored) {
                    // essa liga falhou: as outras continuam
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

    private static List<Match> fetchLeague(String id, String name, String range)
            throws Exception {
        List<Match> out = new ArrayList<>();
        if (!id.matches("[a-z0-9._-]{1,60}")) {
            return out;
        }
        JSONObject root = FootballApi.getJson(FootballApi.base() + id
                + "/scoreboard?limit=500&dates=" + range, MAX_BYTES);
        readEvents(root.optJSONArray("events"), id, name, out);
        return out;
    }

    /** Plano B: o calendário do próprio time (rota da ESPN), se a janela não trouxe nada. */
    private static List<Match> teamSchedule(Teams.Team team) {
        List<Match> out = new ArrayList<>();
        try {
            if (!team.ligaId.matches("[a-z0-9._-]{1,60}") || !team.id.matches("[0-9]{1,12}")) {
                return out;
            }
            JSONObject root = FootballApi.getJson(FootballApi.base() + team.ligaId + "/teams/"
                    + team.id + "/schedule", MAX_BYTES);
            readEvents(root.optJSONArray("events"), team.ligaId, team.ligaId, out);
        } catch (Exception ignored) {
            // sem calendário do time agora
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
