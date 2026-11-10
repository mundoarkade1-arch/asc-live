package com.asclive.tvbox;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Perfis do aparelho (no máximo 5). A LISTA (login) é uma só para o aparelho (ver
 * CredentialStore). Cada perfil tem os seus favoritos, bloqueados, "Continue assistindo" e
 * o que já foi assistido: esses dados ficam em arquivos separados, cujo nome termina com o
 * id do perfil. Aqui só ficam nome, avatar e qual perfil está ativo.
 */
public final class Profiles {

    public static final int MAX = 5;
    private static final String PREFS = "asc_profiles";
    private static final String DEFAULT_ID = "p1";
    private static final String DEFAULT_NAME = "Visitante";

    /** Os 12 avatares desenhados em vetor (6 masculinos primeiro, depois 6 femininos). */
    private static final int[] AVATARS = {
            R.drawable.avatar_01, R.drawable.avatar_02, R.drawable.avatar_03,
            R.drawable.avatar_04, R.drawable.avatar_05, R.drawable.avatar_06,
            R.drawable.avatar_07, R.drawable.avatar_08, R.drawable.avatar_09,
            R.drawable.avatar_10, R.drawable.avatar_11, R.drawable.avatar_12};

    public static final class P {
        public final String id;
        public final String name;
        public final int avatar;

        P(String id, String name, int avatar) {
            this.id = id;
            this.name = name;
            this.avatar = avatar;
        }
    }

    private static List<P> cache;
    private static String cacheCurrent;

    private Profiles() {
    }

    public static int avatarCount() {
        return AVATARS.length;
    }

    /** Desenho (recurso) do avatar de número i (0 a 11). */
    public static int avatarRes(int i) {
        return AVATARS[Math.abs(i) % AVATARS.length];
    }

    /** Primeira letra do nome, em maiúscula. */
    public static String initial(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        return name.trim().substring(0, 1).toUpperCase();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<P> list(Context c) {
        if (cache != null) {
            return new ArrayList<>(cache);
        }
        List<P> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length() && out.size() < MAX; i++) {
                JSONObject o = a.getJSONObject(i);
                String id = o.optString("id", "");
                if (id.matches("[a-z0-9]{1,12}")) {
                    out.add(new P(id, cutName(o.optString("name", "")), o.optInt("av", 0)));
                }
            }
        } catch (Exception ignored) {
            // lista estragada: recomeça com o perfil padrão
        }
        if (out.isEmpty()) {
            out.add(new P(DEFAULT_ID, DEFAULT_NAME, 0));
            write(c, out);
        }
        cache = out;
        return new ArrayList<>(out);
    }

    public static synchronized String currentId(Context c) {
        List<P> all = list(c);
        String cur = cacheCurrent != null ? cacheCurrent : prefs(c).getString("current", "");
        for (P p : all) {
            if (p.id.equals(cur)) {
                cacheCurrent = cur;
                return cur;
            }
        }
        cacheCurrent = all.get(0).id;
        return cacheCurrent;
    }

    public static P current(Context c) {
        String id = currentId(c);
        for (P p : list(c)) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return list(c).get(0);
    }

    public static void setCurrent(Context c, String id) {
        synchronized (Profiles.class) {
            cacheCurrent = id;
            prefs(c).edit().putString("current", id).apply();
        }
        // Fora do bloqueio de Profiles (UserLists também chama Profiles: evita travar).
        UserLists.reset();
    }

    // ---------- Primeira vez ----------

    /**
     * true quando o aparelho ainda não passou pela criação do primeiro perfil.
     * Quem já usava o app com mais de um perfil, ou com um perfil que não é o "Visitante"
     * padrão, não precisa passar por isso.
     */
    public static synchronized boolean needsWelcome(Context c) {
        if (prefs(c).getBoolean("welcomed", false)) {
            return false;
        }
        List<P> all = list(c);
        if (all.size() == 1 && all.get(0).id.equals(DEFAULT_ID)
                && DEFAULT_NAME.equals(all.get(0).name) && all.get(0).avatar == 0) {
            return true;
        }
        markWelcomed(c);
        return false;
    }

    public static synchronized void markWelcomed(Context c) {
        prefs(c).edit().putBoolean("welcomed", true).apply();
    }

    /** Id do perfil padrão (o primeiro da lista). */
    public static synchronized String firstId(Context c) {
        return list(c).get(0).id;
    }

    // ---------- Criar, mudar e excluir ----------

    /** Cria um perfil. Devolve null se já houver 5. */
    public static synchronized P add(Context c, String name, int avatar) {
        List<P> all = list(c);
        if (all.size() >= MAX) {
            return null;
        }
        String id = "p" + Long.toString(System.currentTimeMillis() % 100000000L, 36);
        P p = new P(id, cutName(name), avatar);
        all.add(p);
        write(c, all);
        return p;
    }

    public static synchronized void update(Context c, String id, String name, int avatar) {
        List<P> all = list(c);
        List<P> out = new ArrayList<>();
        for (P p : all) {
            out.add(p.id.equals(id) ? new P(id, cutName(name), avatar) : p);
        }
        write(c, out);
    }

    /**
     * Apaga o perfil e os dados dele (Continue assistindo, favoritos, bloqueados e o que
     * foi assistido). NUNCA apaga o login do aparelho. Não apaga o último perfil.
     */
    public static boolean remove(Context c, String id) {
        // Garante que um login antigo (por perfil) já foi copiado para o login único.
        CredentialStore.migrateLegacy(c);
        boolean ok = removeLocked(c, id);
        if (ok) {
            CredentialStore.clearLegacy(c, id);
            PlaybackProgress.clearProfile(c, id);
        }
        return ok;
    }

    private static boolean removeLocked(Context c, String id) {
        String newCurrent = null;
        synchronized (Profiles.class) {
            List<P> all = list(c);
            if (all.size() <= 1) {
                return false;
            }
            List<P> out = new ArrayList<>();
            for (P p : all) {
                if (!p.id.equals(id)) {
                    out.add(p);
                }
            }
            write(c, out);
            String[] files = {"asc_continue_", "asc_lists_"};
            for (String f : files) {
                c.getApplicationContext().getSharedPreferences(f + id, Context.MODE_PRIVATE)
                        .edit().clear().apply();
            }
            if (id.equals(cacheCurrent)) {
                newCurrent = out.get(0).id;
            }
        }
        if (newCurrent != null) {
            setCurrent(c, newCurrent);
        }
        return true;
    }

    private static void write(Context c, List<P> all) {
        try {
            JSONArray a = new JSONArray();
            for (P p : all) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                o.put("av", p.avatar);
                a.put(o);
            }
            prefs(c).edit().putString("list", a.toString()).apply();
            cache = new ArrayList<>(all);
        } catch (Exception ignored) {
            // não acontece com texto simples
        }
    }

    /** Nome do perfil: até 16 letras. Nome todo em MAIÚSCULAS (teclado) vira "Maria Silva". */
    private static String cutName(String s) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) {
            t = "Perfil";
        }
        if (t.length() > 16) {
            t = t.substring(0, 16).trim();
        }
        if (t.equals(t.toUpperCase()) && !t.equals(t.toLowerCase())) {
            StringBuilder b = new StringBuilder();
            boolean up = true;
            String low = t.toLowerCase();
            for (int i = 0; i < low.length(); i++) {
                char ch = low.charAt(i);
                b.append(up ? Character.toUpperCase(ch) : ch);
                up = ch == ' ';
            }
            t = b.toString();
        }
        return t;
    }
}
