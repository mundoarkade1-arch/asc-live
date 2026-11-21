package com.asclive.tvbox;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Times favoritos (estrelinha) e o time do coração (o principal, que abre a tela de Futebol).
 * Guarda id, nome e brasão para a tela abrir na hora, sem esperar a internet.
 * Valem para o aparelho todo. Leve: só texto pequeno em SharedPreferences.
 */
final class FavTeams {

    private static final int MAX = 30;
    private static final String PREFS = "asc_teams";

    private FavTeams() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Favoritos, na ordem em que foram marcados. */
    static synchronized List<Teams.Team> list(Context c) {
        List<Teams.Team> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                out.add(Teams.Team.fromJson(a.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            // lista corrompida: começa vazia
        }
        return out;
    }

    static synchronized boolean isFavorite(Context c, String teamId) {
        for (Teams.Team t : list(c)) {
            if (t.id.equals(teamId)) {
                return true;
            }
        }
        return false;
    }

    /** Marca ou desmarca a estrelinha. Devolve true se agora é favorito. */
    static synchronized boolean toggle(Context c, Teams.Team team) {
        List<Teams.Team> all = list(c);
        boolean now = true;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(team.id)) {
                all.remove(i);
                now = false;
                break;
            }
        }
        if (now) {
            all.add(team);
            while (all.size() > MAX) {
                all.remove(0);
            }
        } else if (team.id.equals(heartId(c))) {
            prefs(c).edit().remove("heart").apply();
        }
        save(c, all);
        return now;
    }

    /** O time do coração, ou null se ainda não escolheu. */
    static synchronized Teams.Team heart(Context c) {
        String id = heartId(c);
        for (Teams.Team t : list(c)) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    static synchronized String heartId(Context c) {
        return prefs(c).getString("heart", "");
    }

    /** Define o time do coração (e já o põe nos favoritos). */
    static synchronized void setHeart(Context c, Teams.Team team) {
        if (!isFavorite(c, team.id)) {
            toggle(c, team);
        }
        prefs(c).edit().putString("heart", team.id).apply();
    }

    private static void save(Context c, List<Teams.Team> all) {
        try {
            JSONArray a = new JSONArray();
            for (Teams.Team t : all) {
                a.put(t.toJson());
            }
            prefs(c).edit().putString("list", a.toString()).apply();
        } catch (Exception ignored) {
            // não acontece com texto simples
        }
    }
}
