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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Lista de times (com brasão) das ligas de assets/ligas.json, vinda da ESPN. Guardada em arquivo
 * por 7 dias. Serve para escolher o time do coração e para a busca de times.
 * Faz rede e disco: NUNCA chamar na thread principal.
 */
final class Teams {

    /** Um time. */
    static final class Team {
        final String id;
        final String name;
        final String abbr;
        final String logo;
        /** Liga de origem (a primeira de ligas.json em que o time aparece). */
        final String ligaId;
        /** Posição dessa liga em ligas.json (para ordenar: ligas do Brasil primeiro). */
        final int order;
        private String key;

        Team(String id, String name, String abbr, String logo, String ligaId, int order) {
            this.id = id;
            this.name = name;
            this.abbr = abbr == null ? "" : abbr;
            this.logo = logo == null ? "" : logo;
            this.ligaId = ligaId == null ? "" : ligaId;
            this.order = order;
        }

        /** Nome sem acento e em minúsculas, para buscar. */
        String key() {
            if (key == null) {
                key = ChannelNames.queryKey(name);
            }
            return key;
        }

        JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("i", id);
                o.put("n", name);
                o.put("a", abbr);
                o.put("l", logo);
                o.put("g", ligaId);
                o.put("o", order);
                return o;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        static Team fromJson(JSONObject o) {
            return new Team(o.optString("i"), o.optString("n"), o.optString("a"),
                    o.optString("l"), o.optString("g"), o.optInt("o", 99));
        }
    }

    private static final long TTL_MS = 7L * 24L * 3600L * 1000L;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final String CACHE_FILE = "teams.json";

    private static List<Team> memo;

    private Teams() {
    }

    /** Todos os times, ordenados (ligas do Brasil primeiro, depois as outras; nome dentro). */
    static synchronized List<Team> all(Context ctx) throws IOException {
        if (memo != null && !memo.isEmpty()) {
            return memo;
        }
        Context app = ctx.getApplicationContext();
        File f = new File(app.getCacheDir(), CACHE_FILE);
        List<Team> cached = readCache(f, false);
        if (cached != null) {
            memo = cached;
            return memo;
        }
        List<Team> fresh = download(app);
        if (fresh.isEmpty()) {
            // Sem rede: usa o cache velho, se existir.
            List<Team> old = readCache(f, true);
            if (old != null) {
                memo = old;
                return memo;
            }
            throw new IOException("sem times");
        }
        writeCache(f, fresh);
        memo = fresh;
        return memo;
    }

    static synchronized void clearCache(Context ctx) {
        memo = null;
        //noinspection ResultOfMethodCallIgnored
        new File(ctx.getApplicationContext().getCacheDir(), CACHE_FILE).delete();
    }

    /** O time com esse id, ou null. */
    static Team byId(Context ctx, String id) throws IOException {
        for (Team t : all(ctx)) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** Times cujo nome tem todas as palavras digitadas (ou a sigla é igual). */
    static List<Team> search(Context ctx, String text) throws IOException {
        String q = ChannelNames.queryKey(text);
        List<Team> out = new ArrayList<>();
        if (q.isEmpty()) {
            return out;
        }
        String[] words = q.split(" ");
        for (Team t : all(ctx)) {
            boolean ok = true;
            for (String w : words) {
                if (!t.key().contains(w)) {
                    ok = false;
                    break;
                }
            }
            if (ok || ChannelNames.queryKey(t.abbr).equals(q)) {
                out.add(t);
            }
        }
        return out;
    }

    // ---------- Rede ----------

    private static List<Team> download(Context app) {
        final List<String[]> leagues = FootballApi.leagues(app);
        List<Team> out = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<List<Team>>> jobs = new ArrayList<>();
            for (int i = 0; i < leagues.size(); i++) {
                final String id = leagues.get(i)[0];
                final int order = i;
                jobs.add(new Callable<List<Team>>() {
                    @Override
                    public List<Team> call() throws Exception {
                        return fetchLeague(id, order);
                    }
                });
            }
            Set<String> seen = new HashSet<>();
            for (Future<List<Team>> f : pool.invokeAll(jobs, 25, TimeUnit.SECONDS)) {
                try {
                    for (Team t : f.get()) {
                        if (seen.add(t.id)) {
                            out.add(t);
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

    private static List<Team> fetchLeague(String ligaId, int order) throws Exception {
        if (!ligaId.matches("[a-z0-9._-]{1,60}")) {
            return new ArrayList<>();
        }
        JSONObject root = FootballApi.getJson(FootballApi.base() + ligaId + "/teams", MAX_BYTES);
        List<Team> out = new ArrayList<>();
        JSONArray sports = root.optJSONArray("sports");
        JSONObject sport = sports == null ? null : sports.optJSONObject(0);
        JSONArray leagues = sport == null ? null : sport.optJSONArray("leagues");
        JSONObject league = leagues == null ? null : leagues.optJSONObject(0);
        JSONArray teams = league == null ? null : league.optJSONArray("teams");
        for (int i = 0; teams != null && i < teams.length(); i++) {
            JSONObject t = teams.getJSONObject(i).optJSONObject("team");
            if (t == null) {
                continue;
            }
            String id = t.optString("id", "");
            String name = t.optString("displayName", t.optString("name", ""));
            if (id.isEmpty() || name.isEmpty()) {
                continue;
            }
            String logo = "";
            JSONArray logos = t.optJSONArray("logos");
            if (logos != null && logos.length() > 0) {
                logo = logos.getJSONObject(0).optString("href", "");
            }
            if (logo.startsWith("http://")) {
                logo = "https://" + logo.substring(7);
            }
            out.add(new Team(id, name, t.optString("abbreviation", ""), logo, ligaId, order));
        }
        return out;
    }

    private static void sort(List<Team> list) {
        Collections.sort(list, new Comparator<Team>() {
            @Override
            public int compare(Team a, Team b) {
                if (a.order != b.order) {
                    return a.order < b.order ? -1 : 1;
                }
                return a.name.compareToIgnoreCase(b.name);
            }
        });
    }

    // ---------- Cache ----------

    private static List<Team> readCache(File f, boolean ignoreAge) {
        try {
            if (!f.exists() || f.length() > MAX_BYTES) {
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
            if (!ignoreAge && System.currentTimeMillis() - root.optLong("t", 0L) > TTL_MS) {
                return null;
            }
            JSONArray a = root.getJSONArray("l");
            List<Team> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                out.add(Team.fromJson(a.getJSONObject(i)));
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeCache(File f, List<Team> teams) {
        try {
            JSONArray a = new JSONArray();
            for (Team t : teams) {
                a.put(t.toJson());
            }
            JSONObject root = new JSONObject();
            root.put("t", System.currentTimeMillis());
            root.put("l", a);
            FileOutputStream out = new FileOutputStream(f);
            try {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
        } catch (Exception ignored) {
            // sem espaço: só não guarda
        }
    }
}
