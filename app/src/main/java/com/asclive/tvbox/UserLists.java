package com.asclive.tvbox;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Favoritos e bloqueados do perfil ativo. As duas listas são criptografadas (Secure),
 * porque itens de lista M3U guardam o endereço do vídeo, que pode ter usuário e senha.
 * Um item é reconhecido pelo tipo e pelo nome (assim vale também para listas M3U).
 */
final class UserLists {

    private static final int MAX_FAVORITES = 300;
    private static final int MAX_BLOCKED = 500;

    private static String owner = "";
    private static List<XtreamApi.Item> favs;
    private static Map<String, String> blocked;

    private UserLists() {
    }

    /** Esquece o que está na memória (chamar ao trocar de perfil). */
    static synchronized void reset() {
        owner = "";
        favs = null;
        blocked = null;
    }

    static String keyOf(XtreamApi.Item it) {
        return it.kind.name() + ":" + it.name.toLowerCase(Locale.ROOT);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(
                "asc_lists_" + Profiles.currentId(c), Context.MODE_PRIVATE);
    }

    private static void ensure(Context c) {
        String id = Profiles.currentId(c);
        if (id.equals(owner) && favs != null && blocked != null) {
            return;
        }
        owner = id;
        favs = new ArrayList<>();
        blocked = new LinkedHashMap<>();
        SharedPreferences p = prefs(c);
        try {
            String f = Secure.reveal(p.getString("fav", ""));
            if (!f.isEmpty()) {
                JSONArray a = new JSONArray(f);
                for (int i = 0; i < a.length() && favs.size() < MAX_FAVORITES; i++) {
                    JSONObject o = a.getJSONObject(i);
                    XtreamApi.Kind kind = XtreamApi.Kind.valueOf(o.getString("k"));
                    XtreamApi.Item it = new XtreamApi.Item(kind, o.optString("i"),
                            o.optString("n"), o.optString("c"), o.optString("g"), "",
                            o.optString("e"), o.optString("u"));
                    it.rating = o.optDouble("r", 0);
                    it.added = o.optLong("a", 0);
                    favs.add(it);
                }
            }
        } catch (Exception ignored) {
            favs = new ArrayList<>();
        }
        try {
            String b = Secure.reveal(p.getString("blk", ""));
            if (!b.isEmpty()) {
                JSONArray a = new JSONArray(b);
                for (int i = 0; i < a.length() && blocked.size() < MAX_BLOCKED; i++) {
                    JSONObject o = a.getJSONObject(i);
                    blocked.put(o.getString("k"), o.optString("n"));
                }
            }
        } catch (Exception ignored) {
            blocked = new LinkedHashMap<>();
        }
    }

    // ---------- Favoritos ----------

    static synchronized boolean isFavorite(Context c, XtreamApi.Item it) {
        ensure(c);
        String k = keyOf(it);
        for (XtreamApi.Item f : favs) {
            if (keyOf(f).equals(k)) {
                return true;
            }
        }
        return false;
    }

    /** Liga ou desliga o favorito. Devolve true se agora É favorito. */
    static synchronized boolean toggleFavorite(Context c, XtreamApi.Item it) {
        ensure(c);
        String k = keyOf(it);
        for (int i = 0; i < favs.size(); i++) {
            if (keyOf(favs.get(i)).equals(k)) {
                favs.remove(i);
                saveFavs(c);
                return false;
            }
        }
        favs.add(0, it);
        while (favs.size() > MAX_FAVORITES) {
            favs.remove(favs.size() - 1);
        }
        saveFavs(c);
        return true;
    }

    static synchronized List<XtreamApi.Item> favorites(Context c) {
        ensure(c);
        return new ArrayList<>(favs);
    }

    private static void saveFavs(Context c) {
        try {
            JSONArray a = new JSONArray();
            for (XtreamApi.Item it : favs) {
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
            prefs(c).edit().putString("fav", Secure.seal(a.toString())).apply();
        } catch (Exception ignored) {
            // não acontece com texto simples
        }
    }

    // ---------- Bloqueados ----------

    static synchronized boolean isBlocked(Context c, XtreamApi.Item it) {
        ensure(c);
        return blocked.containsKey(keyOf(it));
    }

    static synchronized void block(Context c, XtreamApi.Item it) {
        ensure(c);
        blocked.put(keyOf(it), it.name);
        while (blocked.size() > MAX_BLOCKED) {
            String first = blocked.keySet().iterator().next();
            blocked.remove(first);
        }
        saveBlocked(c);
    }

    static synchronized void unblock(Context c, String key) {
        ensure(c);
        if (blocked.remove(key) != null) {
            saveBlocked(c);
        }
    }

    /** Pares {chave, nome}. */
    static synchronized List<String[]> blockedList(Context c) {
        ensure(c);
        List<String[]> out = new ArrayList<>();
        for (Map.Entry<String, String> e : blocked.entrySet()) {
            out.add(new String[]{e.getKey(), e.getValue()});
        }
        return out;
    }

    private static void saveBlocked(Context c) {
        try {
            JSONArray a = new JSONArray();
            for (Map.Entry<String, String> e : blocked.entrySet()) {
                JSONObject o = new JSONObject();
                o.put("k", e.getKey());
                o.put("n", e.getValue());
                a.put(o);
            }
            prefs(c).edit().putString("blk", Secure.seal(a.toString())).apply();
        } catch (Exception ignored) {
            // não acontece com texto simples
        }
    }
}
