package com.asclive.tvbox;

import android.net.Uri;
import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Busca categorias e canais AO VIVO no servidor Xtream (ASC Live: só TV ao vivo e Futebol).
 *
 * Economia de memória: a resposta é lida aos poucos (streaming). Cada canal é entregue
 * ao listener e esquecido; a lista inteira nunca fica na memória desta classe.
 *
 * Faz acesso à rede: NUNCA chamar na thread principal.
 * Não registra senhas nem endereços em log.
 */
public final class XtreamApi {

    /** No ASC Live só existe canal ao vivo. */
    public enum Kind { LIVE }

    /** Recebe cada canal assim que ele é lido. Devolve false para parar a leitura. */
    public interface ResultListener {
        boolean onItem(Item item);
    }

    public static final class Category {
        public final String id;
        public final String name;

        Category(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Item {
        public final Kind kind;
        public final String id;
        public final String name;
        public final String icon;
        public final String categoryId;
        public final String epgId;
        public final String ext;
        /**
         * Endereço direto do canal. Só é usado por itens de lista M3U (que já trazem o endereço
         * pronto). Nos itens do servidor Xtream fica vazio e o endereço é montado em streamUrl.
         * Contém usuário e senha em muitas listas: nunca mostrar nem registrar em log.
         */
        public final String url;

        Item(Kind kind, String id, String name, String icon, String categoryId,
             String epgId, String ext) {
            this(kind, id, name, icon, categoryId, epgId, ext, "");
        }

        Item(Kind kind, String id, String name, String icon, String categoryId,
             String epgId, String ext, String url) {
            this.kind = kind;
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.categoryId = categoryId;
            this.epgId = epgId;
            this.ext = ext;
            this.url = url == null ? "" : url;
        }
    }

    /**
     * Programação de um canal: o que passa agora e o que vem a seguir.
     * Horários em milissegundos (como System.currentTimeMillis()).
     * nextTitle fica vazio e nextStart fica 0 quando não há "a seguir".
     */
    public static final class Epg {
        public final String title;
        public final long start;
        public final long end;
        public final String nextTitle;
        public final long nextStart;

        Epg(String title, long start, long end, String nextTitle, long nextStart) {
            this.title = title;
            this.start = start;
            this.end = end;
            this.nextTitle = nextTitle;
            this.nextStart = nextStart;
        }
    }

    /**
     * Um programa da lista de programação de um canal (usada na tecla das três listras).
     * Horários em milissegundos (como System.currentTimeMillis()).
     */
    public static final class Program {
        public final String title;
        public final long start;
        public final long end;

        Program(String title, long start, long end) {
            this.title = title;
            this.start = start;
            this.end = end;
        }
    }

    private static final class Slot {
        final String title;
        final long start;
        final long end;

        Slot(String title, long start, long end) {
            this.title = title;
            this.start = start;
            this.end = end;
        }
    }

    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_REDIRECTS = 5;
    private static final long MAX_BYTES = 80L * 1024L * 1024L;
    private static final int MAX_CATEGORIES = 3000;
    private static final int MAX_NAME = 200;
    private static final int MAX_EPG_SLOTS = 8;
    private static final int MAX_SCHEDULE_READ = 12;
    private static final int SCHEDULE_SHOWN = 6;
    private static final String USER_AGENT = "ASC-Cine/1.0 (Android)";

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private XtreamApi() {
    }

    // ---------- Chamadas públicas ----------

    /** Categorias de canais ao vivo (get_live_categories). */
    public static List<Category> liveCategories(String server, String user, String pass)
            throws IOException {
        String url = apiUrl(server, user, pass, "get_live_categories");
        HttpURLConnection conn = open(url);
        try {
            checkOk(conn);
            JsonReader r = reader(conn);
            try {
                List<Category> out = new ArrayList<>();
                if (r.peek() != JsonToken.BEGIN_ARRAY) {
                    throw new IOException("resposta invalida");
                }
                r.beginArray();
                while (r.hasNext() && out.size() < MAX_CATEGORIES) {
                    if (r.peek() != JsonToken.BEGIN_OBJECT) {
                        r.skipValue();
                        continue;
                    }
                    String id = "";
                    String name = "";
                    r.beginObject();
                    while (r.hasNext()) {
                        String key = r.nextName();
                        if ("category_id".equals(key)) {
                            id = readText(r);
                        } else if ("category_name".equals(key)) {
                            name = readText(r);
                        } else {
                            r.skipValue();
                        }
                    }
                    r.endObject();
                    if (isNumber(id) && !name.isEmpty()) {
                        out.add(new Category(id, cut(name)));
                    }
                }
                return out;
            } finally {
                closeQuietly(r);
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Lê TODOS os canais ao vivo (get_live_streams) aos poucos e avisa cada um pelo listener.
     * Nada fica na memória aqui: cada canal é entregue e esquecido. Para quando o listener
     * devolver false. NUNCA chamar na thread principal.
     */
    public static void streamLive(String server, String user, String pass,
                                  ResultListener listener) throws IOException {
        String url = apiUrl(server, user, pass, "get_live_streams");
        HttpURLConnection conn = open(url);
        try {
            checkOk(conn);
            JsonReader r = reader(conn);
            try {
                if (r.peek() != JsonToken.BEGIN_ARRAY) {
                    throw new IOException("resposta invalida");
                }
                r.beginArray();
                while (r.hasNext()) {
                    if (r.peek() != JsonToken.BEGIN_OBJECT) {
                        r.skipValue();
                        continue;
                    }
                    Item item = readItem(r);
                    if (item == null) {
                        continue;
                    }
                    if (!listener.onItem(item)) {
                        return;
                    }
                }
            } finally {
                closeQuietly(r);
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Tira acentos e passa para minúsculas. Se o texto só tem letras simples (ASCII),
     * pula a conta pesada do Normalizer.
     */
    static String normalizeText(String s) {
        if (s == null) {
            return "";
        }
        boolean ascii = true;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) {
                ascii = false;
                break;
            }
        }
        if (ascii) {
            return s.toLowerCase(Locale.ROOT).trim();
        }
        String d = Normalizer.normalize(s, Normalizer.Form.NFD);
        return MARKS.matcher(d).replaceAll("").toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Programação de UM canal (o que passa agora e o que vem a seguir).
     * Devolve null se o servidor não tem guia para o canal ou se nada passa agora.
     * Pede poucos programas por vez, nunca o guia inteiro.
     */
    public static Epg epg(String server, String user, String pass, String streamId)
            throws IOException {
        if (!isNumber(streamId)) {
            return null;
        }
        String url = apiUrl(server, user, pass, "get_short_epg")
                + "&stream_id=" + streamId + "&limit=4";
        HttpURLConnection conn = open(url);
        try {
            checkOk(conn);
            JsonReader r = reader(conn);
            try {
                List<Slot> slots = new ArrayList<>();
                if (r.peek() != JsonToken.BEGIN_OBJECT) {
                    // Sem guia: alguns servidores devolvem uma lista vazia
                    return null;
                }
                r.beginObject();
                while (r.hasNext()) {
                    String key = r.nextName();
                    if ("epg_listings".equals(key) && r.peek() == JsonToken.BEGIN_ARRAY) {
                        r.beginArray();
                        while (r.hasNext() && slots.size() < MAX_EPG_SLOTS) {
                            if (r.peek() != JsonToken.BEGIN_OBJECT) {
                                r.skipValue();
                                continue;
                            }
                            Slot s = readSlot(r);
                            if (s != null) {
                                slots.add(s);
                            }
                        }
                        while (r.hasNext()) {
                            r.skipValue();
                        }
                        r.endArray();
                    } else {
                        r.skipValue();
                    }
                }
                r.endObject();

                long now = System.currentTimeMillis();
                for (int i = 0; i < slots.size(); i++) {
                    Slot s = slots.get(i);
                    if (s.start <= now && now < s.end) {
                        if (i + 1 < slots.size()) {
                            Slot n = slots.get(i + 1);
                            return new Epg(s.title, s.start, s.end, n.title, n.start);
                        }
                        return new Epg(s.title, s.start, s.end, "", 0L);
                    }
                }
                return null;
            } finally {
                closeQuietly(r);
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Lista de programação de UM canal: o que passa agora e os próximos (no máximo 6 programas,
     * já sem os que terminaram). Devolve lista vazia se o servidor não tem guia para o canal.
     * Só deve ser chamada quando a pessoa pede a programação (tecla das três listras).
     */
    public static List<Program> schedule(String server, String user, String pass, String streamId)
            throws IOException {
        List<Program> out = new ArrayList<>();
        if (!isNumber(streamId)) {
            return out;
        }
        String url = apiUrl(server, user, pass, "get_short_epg")
                + "&stream_id=" + streamId + "&limit=10";
        HttpURLConnection conn = open(url);
        try {
            checkOk(conn);
            JsonReader r = reader(conn);
            try {
                if (r.peek() != JsonToken.BEGIN_OBJECT) {
                    // Sem guia: alguns servidores devolvem uma lista vazia
                    return out;
                }
                long now = System.currentTimeMillis();
                r.beginObject();
                while (r.hasNext()) {
                    String key = r.nextName();
                    if ("epg_listings".equals(key) && r.peek() == JsonToken.BEGIN_ARRAY) {
                        r.beginArray();
                        int read = 0;
                        while (r.hasNext() && read < MAX_SCHEDULE_READ
                                && out.size() < SCHEDULE_SHOWN) {
                            if (r.peek() != JsonToken.BEGIN_OBJECT) {
                                r.skipValue();
                                continue;
                            }
                            read++;
                            Slot s = readSlot(r);
                            if (s != null && s.end > now) {
                                out.add(new Program(s.title, s.start, s.end));
                            }
                        }
                        while (r.hasNext()) {
                            r.skipValue();
                        }
                        r.endArray();
                    } else {
                        r.skipValue();
                    }
                }
                r.endObject();
                return out;
            } finally {
                closeQuietly(r);
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Endereço para tocar um canal ao vivo.
     * Itens de lista M3U já trazem o endereço pronto (item.url): ele é devolvido como está.
     */
    public static String streamUrl(String server, String user, String pass, Item item) {
        if (item.url != null && !item.url.isEmpty()) {
            return item.url;
        }
        // O login pode ter sido salvo com uma ou mais barras no fim. Duas barras antes de
        // /live fazem alguns servidores responderem 404, embora a lista/API funcione.
        String base = server == null ? "" : server.replaceAll("/+$", "");
        String u = Uri.encode(user) + "/" + Uri.encode(pass) + "/";
        // .ts é o formato ao vivo mais compatível entre servidores Xtream.
        return base + "/live/" + u + item.id + ".m3u8";
    }

    // ---------- Leitura de um canal ----------

    private static Item readItem(JsonReader r) throws IOException {
        String id = "";
        String name = "";
        String icon = "";
        String cat = "";
        String epg = "";
        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            switch (key) {
                case "stream_id":
                    id = readText(r);
                    break;
                case "name":
                    name = readText(r);
                    break;
                case "stream_icon":
                    icon = readText(r);
                    break;
                case "category_id":
                    cat = readText(r);
                    break;
                case "epg_channel_id":
                    epg = readText(r);
                    break;
                default:
                    r.skipValue();
                    break;
            }
        }
        r.endObject();

        if (!isNumber(id) || name.isEmpty()) {
            return null;
        }
        String lowerIcon = icon.toLowerCase(Locale.ROOT);
        if (!lowerIcon.startsWith("http://") && !lowerIcon.startsWith("https://")) {
            icon = "";
        }
        return new Item(Kind.LIVE, id, cut(name), icon, cat, epg.trim(), "");
    }

    /** Lê um programa do guia. Devolve null se faltar título ou horário válido. */
    private static Slot readSlot(JsonReader r) throws IOException {
        String title = "";
        String start = "";
        String stop = "";
        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            if ("title".equals(key)) {
                title = readText(r);
            } else if ("start_timestamp".equals(key)) {
                start = readText(r);
            } else if ("stop_timestamp".equals(key)) {
                stop = readText(r);
            } else {
                r.skipValue();
            }
        }
        r.endObject();

        long s = parseSeconds(start);
        long e = parseSeconds(stop);
        String t = decodeTitle(title);
        if (t.isEmpty() || s <= 0 || e <= s) {
            return null;
        }
        return new Slot(cut(t), s * 1000L, e * 1000L);
    }

    private static long parseSeconds(String s) {
        if (!isNumber(s)) {
            return 0L;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /** O título do guia costuma vir em Base64. Se não for, usa o texto como veio. */
    private static String decodeTitle(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        try {
            byte[] bytes = Base64.decode(raw, Base64.DEFAULT);
            String s = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!s.isEmpty() && looksLikeText(s)) {
                return s;
            }
        } catch (IllegalArgumentException ignored) {
            // não era Base64: usa o texto original
        }
        return raw.trim();
    }

    private static boolean looksLikeText(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\uFFFD' || (c < 32 && c != '\n' && c != '\t' && c != '\r')) {
                return false;
            }
        }
        return true;
    }

    // ---------- Ajudas ----------

    private static String apiUrl(String server, String user, String pass, String action)
            throws IOException {
        try {
            return server + "/player_api.php?username=" + URLEncoder.encode(user, "UTF-8")
                    + "&password=" + URLEncoder.encode(pass, "UTF-8")
                    + "&action=" + action;
        } catch (Exception e) {
            throw new IOException("endereco");
        }
    }

    /** Lê texto, número ou nulo como texto. Qualquer outra coisa é ignorada. */
    private static String readText(JsonReader r) throws IOException {
        JsonToken t = r.peek();
        if (t == JsonToken.STRING || t == JsonToken.NUMBER) {
            return r.nextString().trim();
        }
        if (t == JsonToken.NULL) {
            r.nextNull();
            return "";
        }
        r.skipValue();
        return "";
    }

    private static boolean isNumber(String s) {
        return s != null && s.matches("\\d{1,12}");
    }

    private static String cut(String s) {
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
    }

    private static void checkOk(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("resposta " + code);
        }
    }

    private static JsonReader reader(HttpURLConnection conn) throws IOException {
        InputStream in = new BufferedInputStream(new LimitedStream(conn.getInputStream(), MAX_BYTES));
        JsonReader r = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        r.setLenient(true);
        return r;
    }

    private static void closeQuietly(JsonReader r) {
        try {
            r.close();
        } catch (IOException ignored) {
            // nada a fazer
        }
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
