package com.asclive.tvbox;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Jogos do dia, vindos da API pública (e não oficial) da ESPN, sem chave. As ligas ficam em
 * assets/ligas.json (o dono pode editar). Só HTTPS, só JSON, cache de 15 minutos em arquivo.
 * Se a ESPN falhar, devolve lista vazia (ou o último cache do mesmo dia) e o app continua.
 *
 * Faz acesso à rede e a disco: NUNCA chamar na thread principal.
 */
final class FootballApi {

    private static final String BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer/";
    private static final long TTL_MS = 15L * 60L * 1000L;
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private static final int TIMEOUT_MS = 8000;
    private static final String CACHE_FILE = "football_today.json";
    private static final String USER_AGENT = "ASC-Live/1.0 (Android)";

    private FootballApi() {
    }

    private static final class League {
        final String id;
        final String nome;

        League(String id, String nome) {
            this.id = id;
            this.nome = nome;
        }
    }

    /** Jogos de hoje (no fuso do aparelho) e jogos que estão rolando agora. */
    static List<Match> today(Context ctx) {
        Context app = ctx.getApplicationContext();
        String day = dayKey(System.currentTimeMillis());
        Cached cached = readCache(app, day);
        if (cached != null && System.currentTimeMillis() - cached.time < TTL_MS) {
            return cached.matches;
        }
        List<League> leagues = readLeagues(app);
        List<Match> out = new ArrayList<>();
        int[] okCount = {0};
        if (!leagues.isEmpty()) {
            fetchAll(leagues, day, out, okCount);
        }
        sort(out);
        if (okCount[0] > 0) {
            writeCache(app, day, out);
            return out;
        }
        // Nenhuma liga respondeu: usa o último cache do dia, se houver.
        return cached != null ? cached.matches : out;
    }

    /** Apaga o cache de jogos (Ajustes > limpar cache). */
    static void clearCache(Context ctx) {
        //noinspection ResultOfMethodCallIgnored
        new File(ctx.getApplicationContext().getCacheDir(), CACHE_FILE).delete();
    }

    // ---------- Busca ----------

    private static void fetchAll(List<League> leagues, final String day, List<Match> out,
                                 int[] okCount) {
        // Pede de ontem a amanhã e filtra pelo dia local: o fuso da ESPN pode ser outro.
        final String range = shiftDay(-1) + "-" + shiftDay(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<List<Match>>> jobs = new ArrayList<>();
            for (final League l : leagues) {
                jobs.add(new Callable<List<Match>>() {
                    @Override
                    public List<Match> call() throws Exception {
                        return fetchLeague(l, range, day);
                    }
                });
            }
            List<Future<List<Match>>> results = pool.invokeAll(jobs, 25, TimeUnit.SECONDS);
            Set<String> seen = new HashSet<>();
            for (Future<List<Match>> f : results) {
                try {
                    List<Match> part = f.get();
                    okCount[0]++;
                    for (Match m : part) {
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
    }

    private static List<Match> fetchLeague(League l, String range, String day) throws Exception {
        if (!l.id.matches("[a-z0-9._-]{1,60}")) {
            return new ArrayList<>();
        }
        URL url = new URL(BASE + l.id + "/scoreboard?dates=" + range);
        if (!"https".equals(url.getProtocol())) {
            throw new IllegalStateException("so https");
        }
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json");
        try {
            if (conn.getResponseCode() != 200) {
                throw new IllegalStateException("resposta");
            }
            String body = readBody(conn.getInputStream());
            JSONObject root = new JSONObject(body);
            JSONArray events = root.optJSONArray("events");
            List<Match> out = new ArrayList<>();
            if (events == null) {
                return out;
            }
            for (int i = 0; i < events.length(); i++) {
                Match m = parseEvent(l, events.optJSONObject(i));
                if (m != null && (m.isLive() || day.equals(dayKey(m.horario)))) {
                    out.add(m);
                }
            }
            return out;
        } finally {
            conn.disconnect();
        }
    }

    private static String readBody(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > MAX_BYTES) {
                    throw new IllegalStateException("grande demais");
                }
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    // ---------- Leitura de um jogo ----------

    private static Match parseEvent(League l, JSONObject ev) {
        try {
            if (ev == null) {
                return null;
            }
            String id = ev.optString("id", "");
            long time = parseDate(ev.optString("date", ""));
            JSONArray comps = ev.optJSONArray("competitions");
            if (id.isEmpty() || time <= 0 || comps == null || comps.length() == 0) {
                return null;
            }
            JSONArray cs = comps.getJSONObject(0).optJSONArray("competitors");
            if (cs == null) {
                return null;
            }
            String casa = "";
            String fora = "";
            String escCasa = "";
            String escFora = "";
            String golsCasa = "";
            String golsFora = "";
            for (int i = 0; i < cs.length(); i++) {
                JSONObject c = cs.getJSONObject(i);
                JSONObject team = c.optJSONObject("team");
                if (team == null) {
                    continue;
                }
                String name = team.optString("displayName", team.optString("name", ""));
                String logo = https(team.optString("logo", ""));
                String score = c.optString("score", "");
                if ("home".equals(c.optString("homeAway", ""))) {
                    casa = name;
                    escCasa = logo;
                    golsCasa = score;
                } else {
                    fora = name;
                    escFora = logo;
                    golsFora = score;
                }
            }
            if (casa.isEmpty() || fora.isEmpty()) {
                return null;
            }
            int status = Match.SCHEDULED;
            String text = "";
            JSONObject st = ev.optJSONObject("status");
            JSONObject type = st == null ? null : st.optJSONObject("type");
            if (type != null) {
                String state = type.optString("state", "pre");
                String tname = type.optString("name", "").toUpperCase(Locale.ROOT);
                String desc = type.optString("description", "").toLowerCase(Locale.ROOT);
                if (tname.contains("POSTPONED")) {
                    text = "Adiado";
                } else if (tname.contains("CANCEL")) {
                    text = "Cancelado";
                } else if ("in".equals(state)) {
                    status = Match.LIVE;
                    if (desc.contains("half") && !desc.contains("1st") && !desc.contains("2nd")
                            && !desc.contains("first") && !desc.contains("second")) {
                        text = "Intervalo";
                    } else {
                        text = st.optString("displayClock", "");
                    }
                } else if ("post".equals(state)) {
                    status = Match.FINISHED;
                    text = "Encerrado";
                }
            }
            String placar = "";
            if (status != Match.SCHEDULED && !golsCasa.isEmpty() && !golsFora.isEmpty()) {
                placar = golsCasa + " x " + golsFora;
            }
            return new Match(id, l.id, l.nome, casa, fora, escCasa, escFora, time, status,
                    text, placar);
        } catch (Exception e) {
            return null;
        }
    }

    private static String https(String url) {
        if (url == null) {
            return "";
        }
        if (url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("http://")) {
            return "https://" + url.substring(7);
        }
        return "";
    }

    /** Datas da ESPN: "2026-11-10T22:00Z" (às vezes com segundos). */
    private static long parseDate(String s) {
        String[] formats = {"yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (String f : formats) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat(f, Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                return fmt.parse(s).getTime();
            } catch (Exception ignored) {
                // tenta o próximo formato
            }
        }
        return 0L;
    }

    // ---------- Dia local ----------

    /** "20261110": o dia no fuso do aparelho. */
    private static String dayKey(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return String.format(Locale.US, "%04d%02d%02d", c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static String shiftDay(int days) {
        return dayKey(System.currentTimeMillis() + days * 86400000L);
    }

    /** Ao vivo primeiro; depois os que vão começar; por último os encerrados. Cada grupo por hora. */
    private static void sort(List<Match> list) {
        Collections.sort(list, new Comparator<Match>() {
            @Override
            public int compare(Match a, Match b) {
                int ra = rank(a);
                int rb = rank(b);
                if (ra != rb) {
                    return ra < rb ? -1 : 1;
                }
                return a.horario < b.horario ? -1 : (a.horario > b.horario ? 1 : 0);
            }

            private int rank(Match m) {
                return m.status == Match.LIVE ? 0 : (m.status == Match.SCHEDULED ? 1 : 2);
            }
        });
    }

    // ---------- Ligas (assets) e cache ----------

    private static List<League> readLeagues(Context app) {
        List<League> out = new ArrayList<>();
        try {
            InputStream in = app.getAssets().open("ligas.json");
            JSONArray a = new JSONArray(readBody(in));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String id = o.optString("id", "");
                if (!id.isEmpty()) {
                    out.add(new League(id, o.optString("nome", id)));
                }
            }
        } catch (Exception ignored) {
            // sem arquivo de ligas: sem jogos
        }
        return out;
    }

    private static final class Cached {
        final long time;
        final List<Match> matches;

        Cached(long time, List<Match> matches) {
            this.time = time;
            this.matches = matches;
        }
    }

    private static Cached readCache(Context app, String day) {
        try {
            File f = new File(app.getCacheDir(), CACHE_FILE);
            if (!f.exists() || f.length() > MAX_BYTES) {
                return null;
            }
            String body = readBody(new FileInputStream(f));
            JSONObject root = new JSONObject(body);
            if (!day.equals(root.optString("d", ""))) {
                return null;
            }
            JSONArray a = root.getJSONArray("m");
            List<Match> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Match(o.optString("id"), o.optString("li"), o.optString("ln"),
                        o.optString("c"), o.optString("f"), o.optString("ec"), o.optString("ef"),
                        o.optLong("h"), o.optInt("s"), o.optString("t"), o.optString("p")));
            }
            return new Cached(root.optLong("t", 0L), out);
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeCache(Context app, String day, List<Match> matches) {
        try {
            JSONArray a = new JSONArray();
            for (Match m : matches) {
                JSONObject o = new JSONObject();
                o.put("id", m.id);
                o.put("li", m.ligaId);
                o.put("ln", m.liga);
                o.put("c", m.casa);
                o.put("f", m.fora);
                o.put("ec", m.escudoCasa);
                o.put("ef", m.escudoFora);
                o.put("h", m.horario);
                o.put("s", m.status);
                o.put("t", m.statusText);
                o.put("p", m.placar);
                a.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("d", day);
            root.put("t", System.currentTimeMillis());
            root.put("m", a);
            File f = new File(app.getCacheDir(), CACHE_FILE);
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
