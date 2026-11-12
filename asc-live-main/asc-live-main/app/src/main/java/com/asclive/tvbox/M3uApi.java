package com.asclive.tvbox;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

/**
 * Lê uma lista M3U e separa só os canais AO VIVO (ASC Live: filmes e séries são ignorados).
 *
 * Economia de memória: a lista é lida aos poucos (linha por linha). Cada canal ao vivo é
 * entregue ao listener e esquecido; a lista inteira nunca fica na memória desta classe.
 * Para listas gigantes, a leitura também para depois de 200 MB ou de 1 minuto e devolve
 * o que já achou.
 *
 * Faz acesso à rede: NUNCA chamar na thread principal.
 * Os endereços dos canais costumam conter usuário e senha: nunca mostrar nem registrar em log.
 */
public final class M3uApi {

    /** Recebe cada item da lista. Devolve false para parar a leitura. */
    private interface Visitor {
        boolean entry(String url, String group, String name, String icon, String tvgId,
                      int counter, String originalUrl, Map<String, String> headers);
    }

    /** Uma tarefa que lê a lista já aberta. */
    private interface Job {
        void run(Reader reader) throws IOException;
    }

    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_REDIRECTS = 5;
    private static final long MAX_BYTES = 200L * 1024L * 1024L;
    private static final long MAX_SECONDS = 60L;
    /** Limite de seguranca; linhas normais nao sao truncadas silenciosamente. */
    private static final int MAX_LINE = 1024 * 1024;
    private static final int MAX_NAME = 200;
    private static final String USER_AGENT = "ASC-Cine/1.0 (Android)";

    private static final Pattern ATTR = Pattern.compile("([\\w-]+)=\"([^\"]*)\"");
    private static final Pattern EP = Pattern.compile(
            "\\bS\\d{1,2}\\s?E\\d{1,3}\\b|\\bT\\d{1,2}\\s?E\\d{1,3}\\b|\\b\\d{1,2}x\\d{2,3}\\b|temporada",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO_EXT = Pattern.compile(
            "\\.(mp4|mkv|avi|mov|wmv|flv|mpg|mpeg|webm)$");
    private static final Pattern LIVE_EXT = Pattern.compile("\\.(m3u8|ts)$");
    private static final Pattern G_SERIES = Pattern.compile(
            "s[e\u00e9]ries?|novelas?|animes?|doramas?|temporada");
    private static final Pattern G_LIVE = Pattern.compile(
            "canais|canal|24\\s?h|ao vivo|live|\\btv\\b|esporte|abertos|not[i\u00ed]cias");
    private static final Pattern G_MOVIE = Pattern.compile(
            "filmes?|movies?|\\bvod\\b|cine|lan[c\u00e7]amentos?");

    /** Resumo da última leitura (sem endereços nem senhas), para mostrar na tela se falhar. */
    private static volatile int dLines;
    private static volatile int dExtinf;
    private static volatile int dUrls;
    private static volatile int dLive;
    private static volatile String dFirst = "";

    private M3uApi() {
    }

    private static String diag() {
        return "lista: foram lidas " + dLines + " linhas, " + dExtinf + " canais (EXTINF), "
                + dUrls + " links, " + dLive + " ao vivo. Começo do arquivo: \"" + dFirst + "\".";
    }

    // ---------- Chamada pública ----------

    /**
     * Lê a lista e avisa cada canal AO VIVO pelo listener (categoria = group-title, que vai
     * em Item.categoryId). Se hideAdult for verdadeiro, itens adultos (pelo nome ou pela
     * categoria) são pulados. Para quando o listener devolver false.
     * Se a leitura falhar no meio depois de achar algo, termina sem erro (devolve o que deu).
     * NUNCA chamar na thread principal.
     */
    public static void streamLive(String m3uUrl, boolean hideAdult,
                                  XtreamApi.ResultListener listener) throws IOException {
        final int[] found = {0};
        dLines = 0;
        dExtinf = 0;
        dUrls = 0;
        dLive = 0;
        dFirst = "";
        try {
            readList(m3uUrl, reader -> walk(reader, (url, grp, name, icon, tvgId, counter,
                                                    originalUrl, headers) -> {
                if (!isLive(url, grp, name)) {
                    return true;
                }
                if (hideAdult && (ParentalStore.isAdultCategory(grp)
                        || ParentalStore.isAdultName(name))) {
                    return true;
                }
                found[0]++;
                dLive = found[0];
                return listener.onItem(new XtreamApi.Item(XtreamApi.Kind.LIVE,
                        String.valueOf(counter), cut(name), icon, grp, tvgId, "", url,
                        originalUrl, headers));
            }));
        } catch (IOException e) {
            if (found[0] == 0) {
                String msg = e.getMessage();
                if (msg != null && msg.startsWith("lista:")) {
                    throw e;
                }
                throw new IOException("lista: falha ao ler (" + e.getClass().getSimpleName()
                        + "). " + diag());
            }
        }
        if (found[0] == 0) {
            throw new IOException(diag());
        }
    }

    // ---------- Leitura da lista ----------

    /** Abre a lista, entrega a leitura à tarefa e fecha tudo no fim. */
    private static void readList(String m3uUrl, Job job) throws IOException {
        if (m3uUrl == null || !isHttp(m3uUrl.trim())) {
            throw new IOException("lista: o endereço da lista não parece certo.");
        }
        HttpURLConnection conn = open(m3uUrl.trim());
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("lista: o servidor respondeu código " + code + ".");
            }
            Reader reader = new InputStreamReader(
                    new LimitedStream(conn.getInputStream(), MAX_BYTES), StandardCharsets.UTF_8);
            try {
                job.run(reader);
            } finally {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // nada a fazer
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Lê a lista linha por linha e entrega cada item ao "visitor". Para quando o visitor
     * devolver false, quando a lista acabar ou quando passar de 1 minuto.
     */
    private static void walk(Reader reader, Visitor visitor) throws IOException {
        long deadline = System.nanoTime() + MAX_SECONDS * 1000000000L;
        Lines lines = new Lines(reader);
        StringBuilder sb = new StringBuilder();

        boolean have = false;
        String name = "";
        String logo = "";
        String group = "";
        String tvgId = "";
        Map<String, String> channelHeaders = new LinkedHashMap<>();
        int counter = 0;
        int count = 0;

        while (lines.next(sb)) {
            if ((++count & 1023) == 0 && System.nanoTime() > deadline) {
                return;
            }
            String line = sb.toString().trim();
            if (line.isEmpty()) {
                continue;
            }
            dLines++;
            if (dFirst.isEmpty()) {
                dFirst = line.length() > 0 && line.charAt(0) == '#'
                        ? line.substring(0, Math.min(line.length(), 12))
                        : "(não começa com #EXTM3U)";
            }
            if (line.startsWith("#EXTINF")) {
                dExtinf++;
                int comma = line.lastIndexOf(',');
                String head = comma > 0 ? line.substring(0, comma) : line;
                String title = comma >= 0 ? line.substring(comma + 1).trim() : "";
                String tvgName = "";
                logo = "";
                group = "";
                tvgId = "";
                channelHeaders = new LinkedHashMap<>();
                Matcher m = ATTR.matcher(head);
                while (m.find()) {
                    String key = m.group(1).toLowerCase(Locale.ROOT);
                    String value = m.group(2).trim();
                    if ("tvg-logo".equals(key)) {
                        logo = value;
                    } else if ("group-title".equals(key)) {
                        group = value;
                    } else if ("tvg-id".equals(key)) {
                        tvgId = value;
                    } else if ("tvg-name".equals(key)) {
                        tvgName = value;
                    }
                }
                name = title.isEmpty() ? tvgName : title;
                if (group.isEmpty()) {
                    group = "Outros";
                }
                have = true;
                continue;
            }
            if (line.regionMatches(true, 0, "#EXTVLCOPT:", 0, 11)) {
                if (have) {
                    parseHeaderOption(line.substring(11), channelHeaders);
                }
                continue;
            }
            if (line.regionMatches(true, 0, "#EXTHTTP:", 0, 9)) {
                if (have) {
                    parseHttpMetadata(line.substring(9), channelHeaders);
                }
                continue;
            }
            if (line.charAt(0) == '#' || !have) {
                continue;
            }
            have = false;
            StreamInfo stream = parseStreamLine(line, channelHeaders);
            if (!isHttp(stream.effectiveUrl) || name.isEmpty()) {
                continue;
            }
            counter++;
            dUrls++;

            String icon = isHttp(logo) ? logo : "";
            if (!visitor.entry(stream.effectiveUrl, group, name, icon, tvgId, counter,
                    stream.originalUrl, stream.headers)) {
                return;
            }
        }
    }

    /**
     * Diz se o item é canal ao vivo (e não filme nem série). Mesmas regras do app original:
     * primeiro o caminho do endereço, depois a extensão, depois o nome da categoria.
     */
    static boolean isLive(String url, String group, String name) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        path = path.toLowerCase(Locale.ROOT);
        String g = group.toLowerCase(Locale.ROOT);

        if (path.contains("/series/") || path.contains("/movie/")) {
            return false;
        }
        if (path.contains("/live/")) {
            return true;
        }
        boolean episodeLike = EP.matcher(name).find() || G_SERIES.matcher(g).find();
        if (VIDEO_EXT.matcher(path).find()) {
            return false;
        }
        if (LIVE_EXT.matcher(path).find()) {
            return true;
        }
        if (G_LIVE.matcher(g).find()) {
            return true;
        }
        if (episodeLike) {
            return false;
        }
        if (G_MOVIE.matcher(g).find()) {
            return false;
        }
        return true;
    }

    // ---------- Ajudas ----------

    private static boolean isHttp(String s) {
        return s != null && (s.regionMatches(true, 0, "http://", 0, 7)
                || s.regionMatches(true, 0, "https://", 0, 8));
    }

    static final class StreamInfo {
        final String originalUrl;
        final String effectiveUrl;
        final Map<String, String> headers;

        StreamInfo(String originalUrl, String effectiveUrl, Map<String, String> headers) {
            this.originalUrl = originalUrl;
            this.effectiveUrl = effectiveUrl;
            this.headers = headers;
        }
    }

    /** Separa URL|opcoes sem transformar opcoes desconhecidas em cabecalhos. */
    static StreamInfo parseStreamLine(String raw, Map<String, String> inherited) {
        String original = raw == null ? "" : raw.trim();
        int pipe = original.indexOf('|');
        String url = pipe < 0 ? original : original.substring(0, pipe).trim();
        Map<String, String> headers = new LinkedHashMap<>();
        if (inherited != null) headers.putAll(inherited);
        if (pipe >= 0 && pipe + 1 < original.length()) {
            String options = original.substring(pipe + 1);
            for (String option : options.split("&")) {
                parseHeaderOption(option, headers);
            }
        }
        return new StreamInfo(original, url, headers);
    }

    static void parseHeaderOption(String option, Map<String, String> headers) {
        if (option == null) return;
        int eq = option.indexOf('=');
        if (eq <= 0) return;
        String name = canonicalHeader(option.substring(0, eq));
        if (name == null) return;
        String value = decodeHeaderValue(option.substring(eq + 1).trim());
        if (!value.isEmpty()) headers.put(name, value);
    }

    static void parseHttpMetadata(String raw, Map<String, String> headers) {
        String value = raw == null ? "" : raw.trim();
        try {
            JSONObject object = new JSONObject(value);
            java.util.Iterator<String> names = object.keys();
            while (names.hasNext()) {
                String rawName = names.next();
                String name = canonicalHeader(rawName);
                if (name != null) {
                    String headerValue = object.optString(rawName, "");
                    if (!headerValue.isEmpty()) headers.put(name, headerValue);
                }
            }
            return;
        } catch (Exception ignored) {
            // Algumas listas usam a mesma sintaxe chave=valor do EXTVLCOPT.
        }
        for (String option : value.split("&")) parseHeaderOption(option, headers);
    }

    private static String canonicalHeader(String raw) {
        String key = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if ("user-agent".equals(key) || "http-user-agent".equals(key)) return "User-Agent";
        if ("referer".equals(key) || "referrer".equals(key)
                || "http-referer".equals(key) || "http-referrer".equals(key)) return "Referer";
        if ("cookie".equals(key) || "http-cookie".equals(key)) return "Cookie";
        if ("origin".equals(key) || "http-origin".equals(key)) return "Origin";
        if ("accept".equals(key) || "http-accept".equals(key)) return "Accept";
        return null;
    }

    private static String decodeHeaderValue(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String cut(String s) {
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
    }

    /** Abre o endereço seguindo redirecionamentos (inclusive de http para https). */
    private static HttpURLConnection open(String address) throws IOException {
        URL url = new URL(address);
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new IOException("redirecionamento sem destino");
                }
                url = new URL(url, location);
                String protocol = url.getProtocol();
                if (!"http".equals(protocol) && !"https".equals(protocol)) {
                    throw new IOException("redirecionamento invalido");
                }
                continue;
            }
            return conn;
        }
        throw new IOException("redirecionamentos demais");
    }

    /** Lê linha por linha. Linhas maiores que o limite são cortadas (o resto é descartado). */
    private static final class Lines {
        private final Reader reader;
        private final char[] buf = new char[8192];
        private int len = 0;
        private int pos = 0;
        private boolean tooLong;

        Lines(Reader reader) {
            this.reader = reader;
        }

        /** Põe a próxima linha em sb. Devolve false quando a lista acabou. */
        boolean next(StringBuilder sb) throws IOException {
            sb.setLength(0);
            tooLong = false;
            boolean any = false;
            while (true) {
                if (pos >= len) {
                    len = reader.read(buf, 0, buf.length);
                    pos = 0;
                    if (len < 0) {
                        len = 0;
                        if (tooLong) {
                            throw new IOException("lista: uma linha da playlist excede "
                                    + MAX_LINE + " caracteres.");
                        }
                        return any;
                    }
                    if (len == 0) {
                        continue;
                    }
                }
                while (pos < len) {
                    char c = buf[pos++];
                    any = true;
                    if (c == '\n' || c == '\r') {
                        if (tooLong) {
                            throw new IOException("lista: uma linha da playlist excede "
                                    + MAX_LINE + " caracteres.");
                        }
                        return true;
                    }
                    if (sb.length() < MAX_LINE) {
                        sb.append(c);
                    } else {
                        tooLong = true;
                    }
                }
            }
        }
    }

    /** Corta a leitura se a resposta passar do limite de bytes. */
    private static final class LimitedStream extends FilterInputStream {
        private long left;

        LimitedStream(InputStream in, long max) {
            super(in);
            this.left = max;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) {
                throw new IOException("resposta grande demais");
            }
            int b = super.read();
            if (b >= 0) {
                left--;
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (left <= 0) {
                throw new IOException("resposta grande demais");
            }
            int n = super.read(buf, off, (int) Math.min(len, left));
            if (n > 0) {
                left -= n;
            }
            return n;
        }
    }
}
