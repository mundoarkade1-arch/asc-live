package com.asclive.tvbox;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Canais favoritos de cada perfil. Guarda a CHAVE do canal (ChannelGroup.key(): nome sem acento,
 * COM a resolução), então cada resolução (SD, HD, FHD, 4K) é um favorito à parte. Criptografado
 * (Secure).
 */
final class Favorites {

    private static final int MAX = 500;

    private static String owner = "";
    private static Set<String> keys;

    private Favorites() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(
                "asc_favs_" + Profiles.currentId(c), Context.MODE_PRIVATE);
    }

    private static void ensure(Context c) {
        String id = Profiles.currentId(c);
        if (id.equals(owner) && keys != null) {
            return;
        }
        owner = id;
        keys = new LinkedHashSet<>();
        try {
            String raw = Secure.reveal(prefs(c).getString("fav", ""));
            if (!raw.isEmpty()) {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length() && keys.size() < MAX; i++) {
                    keys.add(a.getString(i));
                }
            }
        } catch (Exception ignored) {
            keys = new LinkedHashSet<>();
        }
    }

    /** Esquece o que está em memória (trocou de perfil). */
    static synchronized void reset() {
        owner = "";
        keys = null;
    }

    static synchronized boolean isFavorite(Context c, String key) {
        ensure(c);
        return keys.contains(key);
    }

    /** Favorita ou desfavorita. Devolve true se agora é favorito. */
    static synchronized boolean toggle(Context c, String key) {
        ensure(c);
        boolean now;
        if (keys.contains(key)) {
            keys.remove(key);
            now = false;
        } else {
            keys.add(key);
            while (keys.size() > MAX) {
                keys.remove(keys.iterator().next());
            }
            now = true;
        }
        try {
            JSONArray a = new JSONArray();
            for (String k : keys) {
                a.put(k);
            }
            prefs(c).edit().putString("fav", Secure.seal(a.toString())).apply();
        } catch (Exception ignored) {
            // não acontece com texto simples
        }
        return now;
    }

    static synchronized List<String> all(Context c) {
        ensure(c);
        return new ArrayList<>(keys);
    }
}
