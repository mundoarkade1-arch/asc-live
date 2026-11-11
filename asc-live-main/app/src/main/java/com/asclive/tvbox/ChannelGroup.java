package com.asclive.tvbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Um canal com várias versões (SD, HD, FHD, 4K). A lista traz "Premiere 1 SD", "Premiere 1 HD"...
 * como canais separados; aqui eles viram UM canal só, com as versões em ordem de preferência:
 * FHD, HD, (sem qualidade), SD e, por último, 4K. Dentro da mesma qualidade, H265/HEVC fica depois.
 */
final class ChannelGroup {

    /** Uma versão do canal. */
    static final class Variant {
        final XtreamApi.Item item;
        /** SD, HD, FHD, UHD4K ou OUTRO (ver ChannelNames). */
        final String quality;
        final boolean hevc;

        Variant(XtreamApi.Item item, String quality, boolean hevc) {
            this.item = item;
            this.quality = quality;
            this.hevc = hevc;
        }

        /** Texto curto para o selo ("FHD", "HD", "SD", "4K"; vazio se o nome não diz). */
        String label() {
            if (ChannelNames.UHD4K.equals(quality)) {
                return "4K";
            }
            return ChannelNames.OUTRO.equals(quality) ? "" : quality;
        }
    }

    final String key;
    final String nome;
    final String logo;
    /** Versões, da mais indicada para a menos indicada. Nunca vazia. */
    final List<Variant> variants;
    /** Categorias em que o canal aparece (as versões podem estar em categorias diferentes). */
    final Set<String> categoryIds;

    private ChannelGroup(String key, String nome, String logo, List<Variant> variants,
                         Set<String> categoryIds) {
        this.key = key;
        this.nome = nome;
        this.logo = logo;
        this.variants = variants;
        this.categoryIds = categoryIds;
    }

    private static final Comparator<Variant> ORDER = new Comparator<Variant>() {
        @Override
        public int compare(Variant a, Variant b) {
            int ra = ChannelNames.rank(a.quality);
            int rb = ChannelNames.rank(b.quality);
            if (ra != rb) {
                return ra < rb ? -1 : 1;
            }
            if (a.hevc != b.hevc) {
                return a.hevc ? 1 : -1;
            }
            return 0; // Collections.sort é estável: mantém a ordem da lista
        }
    };

    /** Junta canais com a mesma chave. Os grupos saem na ordem em que aparecem na lista. */
    static List<ChannelGroup> build(List<XtreamApi.Item> channels) {
        Map<String, List<Variant>> map = new LinkedHashMap<>();
        for (XtreamApi.Item it : channels) {
            String key = ChannelNames.key(it.name);
            if (key.isEmpty()) {
                continue;
            }
            List<Variant> list = map.get(key);
            if (list == null) {
                list = new ArrayList<>(2);
                map.put(key, list);
            }
            list.add(new Variant(it, ChannelNames.quality(it.name), ChannelNames.isHevc(it.name)));
        }
        List<ChannelGroup> out = new ArrayList<>(map.size());
        for (Map.Entry<String, List<Variant>> e : map.entrySet()) {
            List<Variant> vs = e.getValue();
            Collections.sort(vs, ORDER);
            String logo = "";
            Set<String> cats = new HashSet<>();
            for (Variant v : vs) {
                if (logo.isEmpty() && !v.item.icon.isEmpty()) {
                    logo = v.item.icon;
                }
                cats.add(v.item.categoryId);
            }
            out.add(new ChannelGroup(e.getKey(), ChannelNames.display(vs.get(0).item.name),
                    logo, Collections.unmodifiableList(vs), cats));
        }
        return out;
    }

    /** Teste simples: java ChannelGroup (agrupa exemplos e imprime). */
    public static void main(String[] args) {
        String[] names = {
                "Premiere 1 SD", "Premiere 1 4K H265", "PREMIERE 1 [HD]", "Premiere 1 FHD",
                "Premiere 2 HD", "Sport TV 2 4K H265", "SporTV 2 SD", "Sport TV 2 FHD H265",
                "Sport TV 2 FHD", "Telecine Pipoca", "BR: Globo SP FHD", "Globo SP HD"
        };
        List<XtreamApi.Item> items = new ArrayList<>();
        int id = 1;
        for (String n : names) {
            items.add(new XtreamApi.Item(XtreamApi.Kind.LIVE, String.valueOf(id++), n, "",
                    "1", "", ""));
        }
        for (ChannelGroup g : build(items)) {
            StringBuilder sb = new StringBuilder();
            for (Variant v : g.variants) {
                sb.append(v.label().isEmpty() ? "-" : v.label()).append(v.hevc ? "(h265)" : "")
                        .append(' ');
            }
            System.out.println("[" + g.key + "] " + g.nome + " : " + sb.toString().trim());
        }
    }
}
