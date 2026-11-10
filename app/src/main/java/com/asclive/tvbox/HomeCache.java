package com.asclive.tvbox;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Guarda em arquivo (armazenamento privado do app) os itens mais novos de Filmes e de Séries,
 * para o banner e o Top 10 da Início aparecerem na hora. Validade: 6 horas.
 * O arquivo é criptografado (Secure), porque itens de lista M3U carregam o endereço do vídeo.
 * NUNCA chamar na thread principal (lê e escreve disco).
 */
final class HomeCache {

    /** Depois deste tempo o guardado ainda é mostrado, mas é atualizado por trás. */
    static final long TTL_MS = 6L * 60L * 60L * 1000L;

    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;

    /** O que foi guardado. */
    static final class Entry {
        final List<XtreamApi.Item> items;
        final long time;

        Entry(List<XtreamApi.Item> items, long time) {
            this.items = items;
            this.time = time;
        }

        boolean fresh() {
            return System.currentTimeMillis() - time < TTL_MS;
        }
    }

    /** Depois de "Atualizar", o que foi guardado antes desta hora é ignorado. */
    private static volatile long ignoreBefore = 0L;

    private HomeCache() {
    }

    private static File file(Context c, XtreamApi.Kind kind) {
        return new File(c.getApplicationContext().getFilesDir(),
                "asc_home_" + kind.name().toLowerCase(java.util.Locale.ROOT) + ".dat");
    }

    /** Resumo embaralhado de quem é a lista (para o guardado de uma lista não valer em outra). */
    static String ownerKey(CredentialStore.Credentials c) {
        String raw = c.mode + "|" + c.server + "|" + c.user + "|" + c.m3uUrl;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(raw.hashCode());
        }
    }

    /** Devolve o guardado, ou null se não houver, se for de outra lista ou se estiver ruim. */
    static Entry load(Context c, String owner, XtreamApi.Kind kind) {
        try {
            File f = file(c, kind);
            if (!f.exists() || f.length() <= 0 || f.length() > MAX_FILE_BYTES) {
                return null;
            }
            byte[] data = readAll(f);
            String json = Secure.reveal(new String(data, StandardCharsets.UTF_8));
            if (json.isEmpty()) {
                return null;
            }
            JSONObject root = new JSONObject(json);
            if (!owner.equals(root.optString("o", ""))) {
                return null;
            }
            long time = root.optLong("t", 0L);
            if (time <= 0 || time < ignoreBefore || time > System.currentTimeMillis() + 60000L) {
                return null;
            }
            JSONArray a = root.getJSONArray("i");
            List<XtreamApi.Item> out = new ArrayList<>();
            for (int i = 0; i < a.length() && out.size() < Catalog.RECENT_MAX; i++) {
                JSONObject o = a.getJSONObject(i);
                XtreamApi.Kind k = XtreamApi.Kind.valueOf(o.getString("k"));
                XtreamApi.Item it = new XtreamApi.Item(k, o.optString("i"), o.optString("n"),
                        o.optString("c"), o.optString("g"), "", o.optString("e"),
                        o.optString("u"));
                it.rating = o.optDouble("r", 0);
                it.added = o.optLong("a", 0);
                out.add(it);
            }
            if (out.isEmpty()) {
                return null;
            }
            return new Entry(out, time);
        } catch (Exception e) {
            return null;
        }
    }

    static void save(Context c, String owner, XtreamApi.Kind kind, List<XtreamApi.Item> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        try {
            JSONArray a = new JSONArray();
            for (XtreamApi.Item it : items) {
                JSONObject o = new JSONObject();
                o.put("k", it.kind.name());
                o.put("i", it.id);
                o.put("n", it.name);
                o.put("c", it.icon);
                o.put("g", it.categoryId);
                o.put("e", it.ext);
                o.put("u", it.url);
                o.put("r", it.rating);
                o.put("a", it.added);
                a.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("o", owner);
            root.put("t", System.currentTimeMillis());
            root.put("i", a);
            byte[] data = Secure.seal(root.toString()).getBytes(StandardCharsets.UTF_8);
            File f = file(c, kind);
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
            // sem espaço ou erro de disco: a Início só fica um pouco mais lenta
        }
    }

    /** "Atualizar": esquece o guardado agora (a regra vale na hora) e apaga os arquivos depois. */
    static void clear(final Context c) {
        ignoreBefore = System.currentTimeMillis() + 1L;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            for (XtreamApi.Kind k : new XtreamApi.Kind[]{XtreamApi.Kind.MOVIE,
                    XtreamApi.Kind.SERIES}) {
                //noinspection ResultOfMethodCallIgnored
                file(app, k).delete();
            }
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
