package com.asclive.tvbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Um canal da lista. Como no ASC TV Box, cada canal aparece com a SUA resolução:
 * "Premiere 1 SD", "Premiere 1 HD" e "Premiere 1 FHD" são três canais separados, e a pessoa
 * escolhe qual quer assistir. (O nome da classe e a lista "variants" ficam por compatibilidade
 * com o player; a lista sempre tem UMA só versão.)
 *
 * Tudo que é pesado (chave de busca, qualidade) é calculado só quando alguém pede, para
 * montar a lista de milhares de canais rápido em TV Box antigo.
 */
final class ChannelGroup {

    /** A (única) versão do canal. */
    static final class Variant {
        final XtreamApi.Item item;
        private String quality;
        private int hevc = -1;

        Variant(XtreamApi.Item item) {
            this.item = item;
        }

        /** SD, HD, FHD, UHD4K ou OUTRO (ver ChannelNames). */
        String quality() {
            if (quality == null) {
                quality = ChannelNames.quality(item.name);
            }
            return quality;
        }

        boolean hevc() {
            if (hevc < 0) {
                hevc = ChannelNames.isHevc(item.name) ? 1 : 0;
            }
            return hevc == 1;
        }

        /** Texto curto para o selo ("FHD", "HD", "SD", "4K"; vazio se o nome não diz). */
        String label() {
            String q = quality();
            if (ChannelNames.UHD4K.equals(q)) {
                return "4K";
            }
            return ChannelNames.OUTRO.equals(q) ? "" : q;
        }
    }

    /** Nome como está na lista (com a resolução, se o nome tiver). */
    final String nome;
    final String logo;
    final String categoryId;
    /** Sempre com UM item. */
    final List<Variant> variants;
    private String key;

    private ChannelGroup(XtreamApi.Item item) {
        this.nome = item.name;
        this.logo = item.icon;
        this.categoryId = item.categoryId;
        this.variants = Collections.singletonList(new Variant(item));
    }

    static ChannelGroup single(XtreamApi.Item item) {
        return new ChannelGroup(item);
    }

    XtreamApi.Item item() {
        return variants.get(0).item;
    }

    /** Qualidade do canal (SD, HD, FHD, UHD4K ou OUTRO). */
    String quality() {
        return variants.get(0).quality();
    }

    /**
     * Chave para busca e favoritos: nome sem acento, em minúsculas, COM a qualidade
     * ("premiere 1 fhd"). Calculada na primeira vez que alguém pede.
     */
    String key() {
        String k = key;
        if (k == null) {
            k = ChannelNames.fullKey(nome);
            key = k;
        }
        return k;
    }

    /** Um canal para cada item da lista, na ordem em que aparecem. */
    static List<ChannelGroup> build(List<XtreamApi.Item> channels) {
        List<ChannelGroup> out = new ArrayList<>(channels.size());
        for (XtreamApi.Item it : channels) {
            out.add(new ChannelGroup(it));
        }
        return out;
    }
}
