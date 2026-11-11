package com.asclive.tvbox;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Quais canais da lista passam um jogo. Nenhuma fonte grátis é 100% certa, então são duas camadas:
 * 1) a tabela assets/transmissoes.json (liga -> emissoras), que o dono edita sem mexer no código;
 * 2) canais de evento da própria lista, com o nome de um dos times (ex.: "Flamengo x Palmeiras").
 *
 * Faz leitura de arquivo e percorre a lista de canais: NUNCA chamar na thread principal.
 * Cada resolução de um canal (SD, HD, FHD, 4K) é um canal à parte, como no ASC TV Box.
 */
final class Broadcasts {

    /** Uma emissora (ex.: Premiere) e os canais dela na lista (Premiere 1, 2, 3...). */
    static final class Broadcast {
        final String emissora;
        final List<ChannelGroup> canais;

        Broadcast(String emissora, List<ChannelGroup> canais) {
            this.emissora = emissora;
            this.canais = canais;
        }
    }

    private static final String EVENT_TITLE = "Canais do jogo";
    private static final int MAX_PER_BROADCAST = 120;
    private static final int MIN_TEAM_LEN = 4;

    private static JSONObject table;

    private Broadcasts() {
    }

    /**
     * Emissoras que passam o jogo, com os canais de cada uma. Os canais de evento (nome do time)
     * vêm primeiro. Emissoras sem nenhum canal na lista não aparecem. Pode devolver lista vazia.
     */
    static List<Broadcast> channelsFor(Context ctx, Match m) throws IOException {
        List<ChannelGroup> all = Catalog.allGroups(ctx);
        List<Broadcast> out = new ArrayList<>();
        Set<String> used = new HashSet<>();

        // Camada 2: canais com o nome de um dos times.
        List<ChannelGroup> event = new ArrayList<>();
        String home = ChannelNames.queryKey(m.casa);
        String away = ChannelNames.queryKey(m.fora);
        for (ChannelGroup g : all) {
            if (matches(g.key(), home, MIN_TEAM_LEN) || matches(g.key(), away, MIN_TEAM_LEN)) {
                event.add(g);
                if (event.size() >= MAX_PER_BROADCAST) {
                    break;
                }
            }
        }
        sortNatural(event);
        if (!event.isEmpty()) {
            for (ChannelGroup g : event) {
                used.add(g.key());
            }
            out.add(new Broadcast(EVENT_TITLE, event));
        }

        // Camada 1: tabela de emissoras da liga.
        JSONObject t = loadTable(ctx);
        JSONObject names = t.optJSONObject("_nomes");
        JSONArray words = t.optJSONArray(m.ligaId);
        if (words == null) {
            words = t.optJSONArray("*");
        }
        if (words != null) {
            for (int i = 0; i < words.length(); i++) {
                String word = words.optString(i, "").trim();
                String q = ChannelNames.queryKey(word);
                if (q.isEmpty()) {
                    continue;
                }
                List<ChannelGroup> hit = new ArrayList<>();
                for (ChannelGroup g : all) {
                    if (!used.contains(g.key()) && matches(g.key(), q, 2)) {
                        hit.add(g);
                        if (hit.size() >= MAX_PER_BROADCAST) {
                            break;
                        }
                    }
                }
                if (hit.isEmpty()) {
                    continue;
                }
                sortNatural(hit);
                for (ChannelGroup g : hit) {
                    used.add(g.key());
                }
                out.add(new Broadcast(niceName(names, word), hit));
            }
        }
        return out;
    }

    /**
     * A chave do canal começa com o texto, ou o texto começa uma das palavras dela
     * ("globo" acha "globo sp"; "flamengo" acha "tv flamengo"). minLen evita textos curtos demais.
     */
    private static boolean matches(String key, String q, int minLen) {
        if (q == null || q.length() < minLen) {
            return false;
        }
        return key.startsWith(q) || key.contains(" " + q);
    }

    /**
     * Ordem: pelo nome sem a qualidade (nome mais curto primeiro, depois alfabética: 1, 2, ..., 10)
     * e, no mesmo canal, da melhor resolução para a pior (FHD, HD, SD, 4K).
     */
    private static void sortNatural(List<ChannelGroup> list) {
        Collections.sort(list, new Comparator<ChannelGroup>() {
            @Override
            public int compare(ChannelGroup a, ChannelGroup b) {
                String ka = ChannelNames.key(a.nome);
                String kb = ChannelNames.key(b.nome);
                if (ka.length() != kb.length()) {
                    return ka.length() < kb.length() ? -1 : 1;
                }
                int c = ka.compareTo(kb);
                if (c != 0) {
                    return c;
                }
                int ra = ChannelNames.rank(a.quality());
                int rb = ChannelNames.rank(b.quality());
                return ra == rb ? 0 : (ra < rb ? -1 : 1);
            }
        });
    }

    private static String niceName(JSONObject names, String word) {
        if (names != null) {
            String n = names.optString(word.toLowerCase(Locale.ROOT), "");
            if (!n.isEmpty()) {
                return n;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String part : word.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static synchronized JSONObject loadTable(Context ctx) {
        if (table != null) {
            return table;
        }
        try {
            InputStream in = ctx.getApplicationContext().getAssets().open("transmissoes.json");
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0 && bos.size() < 256 * 1024) {
                    bos.write(buf, 0, n);
                }
                table = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
            } finally {
                in.close();
            }
        } catch (Exception e) {
            table = new JSONObject();
        }
        return table;
    }
}
