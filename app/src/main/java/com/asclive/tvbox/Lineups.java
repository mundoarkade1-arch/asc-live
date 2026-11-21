package com.asclive.tvbox;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Escalação de um jogo, da ESPN (rota "summary", campo "rosters"). A ESPN só publica a
 * escalação perto do jogo (cerca de 1 hora antes) ou depois dele; antes disso a lista vem vazia
 * e a tela usa a escalação do último jogo do time como "provável".
 * Faz rede: NUNCA chamar na thread principal.
 */
final class Lineups {

    static final class Player {
        final String number;
        final String name;
        final String position;

        Player(String number, String name, String position) {
            this.number = number;
            this.name = name;
            this.position = position;
        }
    }

    /** Escalação de um time num jogo. */
    static final class Side {
        final String teamId;
        final String teamName;
        final String formation;
        final List<Player> starters = new ArrayList<>();
        final List<Player> subs = new ArrayList<>();

        Side(String teamId, String teamName, String formation) {
            this.teamId = teamId;
            this.teamName = teamName;
            this.formation = formation;
        }
    }

    private static final long TTL_MS = 10L * 60L * 1000L;
    private static final int MAX_BYTES = 6 * 1024 * 1024;
    private static final Map<String, Object[]> MEMO = new HashMap<>();

    private Lineups() {
    }

    /** Escalações dos dois times (lista vazia se a ESPN ainda não publicou). */
    static List<Side> fetch(Context ctx, Match m) throws Exception {
        if (m.id.startsWith("lista:") || m.id.startsWith("tsdb-")
                || !m.ligaId.matches("[a-z0-9._-]{1,60}") || !m.id.matches("[0-9A-Za-z_-]{1,40}")) {
            return new ArrayList<>();
        }
        String key = m.ligaId + "|" + m.id;
        synchronized (MEMO) {
            Object[] hit = MEMO.get(key);
            if (hit != null && System.currentTimeMillis() - (Long) hit[0] < TTL_MS) {
                @SuppressWarnings("unchecked")
                List<Side> cached = (List<Side>) hit[1];
                return cached;
            }
        }
        JSONObject root = FootballApi.getJson(FootballApi.base() + m.ligaId + "/summary?event="
                + m.id, MAX_BYTES);
        List<Side> out = new ArrayList<>();
        JSONArray rosters = root.optJSONArray("rosters");
        for (int i = 0; rosters != null && i < rosters.length(); i++) {
            JSONObject r = rosters.optJSONObject(i);
            if (r == null) {
                continue;
            }
            JSONObject team = r.optJSONObject("team");
            Side side = new Side(team == null ? "" : team.optString("id", ""),
                    team == null ? "" : team.optString("displayName", ""),
                    r.optString("formation", ""));
            JSONArray list = r.optJSONArray("roster");
            for (int j = 0; list != null && j < list.length(); j++) {
                JSONObject p = list.optJSONObject(j);
                if (p == null) {
                    continue;
                }
                JSONObject athlete = p.optJSONObject("athlete");
                String name = athlete == null ? "" : athlete.optString("displayName",
                        athlete.optString("fullName", ""));
                if (name.isEmpty()) {
                    continue;
                }
                JSONObject pos = p.optJSONObject("position");
                Player player = new Player(p.optString("jersey", ""), name,
                        pos == null ? "" : pos.optString("abbreviation", ""));
                if (p.optBoolean("starter", false)) {
                    side.starters.add(player);
                } else {
                    side.subs.add(player);
                }
            }
            if (!side.starters.isEmpty()) {
                out.add(side);
            }
        }
        synchronized (MEMO) {
            if (MEMO.size() > 40) {
                MEMO.clear();
            }
            MEMO.put(key, new Object[]{System.currentTimeMillis(), out});
        }
        return out;
    }

    /** A escalação do time com esse id, ou null. */
    static Side of(List<Side> sides, String teamId) {
        for (Side s : sides) {
            if (s.teamId.equals(teamId)) {
                return s;
            }
        }
        return null;
    }
}
