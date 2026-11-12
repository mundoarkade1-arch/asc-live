package com.asclive.tvbox;

import android.content.Context;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/**
 * Guarda em arquivo (armazenamento privado do app) as categorias e os canais ao vivo,
 * para o app abrir com a lista na hora. Validade: 6 horas.
 * O arquivo é criptografado (Secure), porque canais de lista M3U carregam o endereço com
 * usuário e senha.
 * NUNCA chamar na thread principal (lê e escreve disco).
 */
final class LiveCache {

    /** Depois deste tempo o guardado ainda é mostrado, mas é atualizado por trás. */
    static final long TTL_MS = 6L * 60L * 60L * 1000L;

    private static final int MAX_FILE_BYTES = 12 * 1024 * 1024;
    private static final String FILE_NAME = "asc_live_channels.dat";

    /** O que foi guardado: categorias, canais e a hora em que foi lido. */
    static final class Entry {
        final List<Catalog.Cat> cats;
        final List<XtreamApi.Item> items;
        final long time;
        final String owner;

        Entry(List<Catalog.Cat> cats, List<XtreamApi.Item> items, long time, String owner) {
            this.cats = cats;
            this.items = items;
            this.time = time;
            this.owner = owner;
        }

        boolean fresh() {
            return System.currentTimeMillis() - time < TTL_MS;
        }
    }

    /** Depois de "Atualizar", o que foi guardado antes desta hora é ignorado. */
    private static volatile long ignoreBefore = 0L;

    private LiveCache() {
    }

    private static File file(Context c) {
        return new File(c.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    /** Resumo embaralhado de quem é a lista (para o guardado de uma lista não valer em outra). */
    static String ownerKey(CredentialStore.Credentials c) {
        String raw = c.mode + "|" + c.server + "|" + c.user + "|" + c.m3uUrl;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(raw.hashCode());
        }
    }

    /** Devolve o guardado, ou null se não houver, se for de outra lista ou se estiver ruim. */
    static Entry load(Context c, String owner) {
        try {
            File f = file(c);
            if (!f.exists() || f.length() <= 0 || f.length() > MAX_FILE_BYTES) {
                return null;
            }
            byte[] data = readAll(f);
            String json = Secure.reveal(new String(data, StandardCharsets.UTF_8));
            if (json.isEmpty()) {
                return null;
            }
            org.json.JSONObject root = new org.json.JSONObject(json);
            if (!owner.equals(root.optString("o", ""))) {
                return null;
            }
            long time = root.optLong("t", 0L);
            if (time <= 0 || time < ignoreBefore || time > System.currentTimeMillis() + 60000L) {
                return null;
            }
            JSONArray ca = root.getJSONArray("c");
            List<Catalog.Cat> cats = new ArrayList<>(ca.length());
            for (int i = 0; i < ca.length(); i++) {
                JSONArray o = ca.getJSONArray(i);
                cats.add(new Catalog.Cat(o.optString(0), o.optString(1)));
            }
            JSONArray ia = root.getJSONArray("i");
            List<XtreamApi.Item> items = new ArrayList<>(ia.length());
            for (int i = 0; i < ia.length() && items.size() < Catalog.MAX_LIVE; i++) {
                JSONArray o = ia.getJSONArray(i);
                Map<String, String> headers = new LinkedHashMap<>();
                if (o.length() > 6 && o.opt(6) instanceof org.json.JSONObject) {
                    org.json.JSONObject h = o.optJSONObject(6);
                    java.util.Iterator<String> names = h.keys();
                    while (names.hasNext()) {
                        String name = names.next();
                        headers.put(name, h.optString(name, ""));
                    }
                }
                items.add(new XtreamApi.Item(XtreamApi.Kind.LIVE, o.optString(0), o.optString(1),
                        o.optString(2), o.optString(3), o.optString(4),
                        o.length() > 8 ? o.optString(8) : "", o.optString(5),
                        o.length() > 7 ? o.optString(7) : o.optString(5), headers));
            }
            if (items.isEmpty()) {
                return null;
            }
            return new Entry(cats, items, time, owner);
        } catch (Exception e) {
            return null;
        }
    }

    /** Guarda a lista. Devolve o que foi guardado (com a hora de agora). */
    static Entry save(Context c, String owner, List<Catalog.Cat> cats,
                      List<XtreamApi.Item> items) {
        long now = System.currentTimeMillis();
        Entry entry = new Entry(cats, items, now, owner);
        if (items == null || items.isEmpty()) {
            return entry;
        }
        try {
            JSONArray ca = new JSONArray();
            for (Catalog.Cat g : cats) {
                JSONArray o = new JSONArray();
                o.put(g.id);
                o.put(g.name);
                ca.put(o);
            }
            JSONArray ia = new JSONArray();
            for (XtreamApi.Item it : items) {
                JSONArray o = new JSONArray();
                o.put(it.id);
                o.put(it.name);
                o.put(it.icon);
                o.put(it.categoryId);
                o.put(it.epgId);
                o.put(it.url);
                org.json.JSONObject h = new org.json.JSONObject();
                for (Map.Entry<String, String> e : it.headers.entrySet()) {
                    h.put(e.getKey(), e.getValue());
                }
                o.put(h);
                o.put(it.originalUrl);
                o.put(it.ext);
                ia.put(o);
            }
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("o", owner);
            root.put("t", now);
            root.put("c", ca);
            root.put("i", ia);
            byte[] data = Secure.seal(root.toString()).getBytes(StandardCharsets.UTF_8);
            if (data.length > MAX_FILE_BYTES) {
                return entry; // grande demais: não guarda (o app só fica um pouco mais lento)
            }
            File f = file(c);
            File tmp = new File(f.getPath() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                out.write(data);
            } finally {
                out.close();
            }
            if (!tmp.renameTo(f)) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                //noinspection ResultOfMethodCallIgnored
                tmp.renameTo(f);
            }
        } catch (Exception ignored) {
            // sem espaço ou erro de disco: o app só fica um pouco mais lento na próxima vez
        }
        return entry;
    }

    /** "Atualizar": esquece o guardado agora (a regra vale na hora) e apaga o arquivo depois. */
    static void clear(final Context c) {
        ignoreBefore = System.currentTimeMillis() + 1L;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            //noinspection ResultOfMethodCallIgnored
            file(app).delete();
        }).start();
    }

    private static byte[] readAll(File f) throws java.io.IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }
}
