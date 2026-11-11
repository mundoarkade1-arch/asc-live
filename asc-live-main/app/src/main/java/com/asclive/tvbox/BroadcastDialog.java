package com.asclive.tvbox;

import android.app.Activity;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/**
 * Do jogo até o vídeo: emissoras (Premiere, SporTV...) -> canais da emissora (Premiere 1, 2...) ->
 * versões do canal (SD, HD, FHD, 4K) -> assiste. Se só houver uma opção em um nível, ele é pulado.
 */
final class BroadcastDialog {

    private BroadcastDialog() {
    }

    static void show(final Activity act, final Match m, final List<Broadcasts.Broadcast> list) {
        final String title = m.casa + " x " + m.fora;
        if (list.isEmpty()) {
            Dialogs.info(act, title, "Nenhum canal da sua lista parece passar este jogo. "
                    + "Procure o canal em Buscar, ou ajuste as emissoras no arquivo "
                    + "transmissoes.json.");
            return;
        }
        if (list.size() == 1) {
            pickChannel(act, list.get(0));
            return;
        }
        String[] labels = new String[list.size()];
        for (int i = 0; i < labels.length; i++) {
            Broadcasts.Broadcast b = list.get(i);
            labels[i] = b.emissora + "  (" + b.canais.size()
                    + (b.canais.size() == 1 ? " canal)" : " canais)");
        }
        Dialogs.list(act, title, labels, which -> pickChannel(act, list.get(which)));
    }

    private static void pickChannel(final Activity act, final Broadcasts.Broadcast b) {
        if (b.canais.size() == 1) {
            pickVersion(act, b, 0);
            return;
        }
        String[] labels = new String[b.canais.size()];
        for (int i = 0; i < labels.length; i++) {
            ChannelGroup g = b.canais.get(i);
            int n = g.variants.size();
            labels[i] = n > 1 ? g.nome + "   (" + n + " versões)" : g.nome;
        }
        Dialogs.list(act, b.emissora, labels, which -> pickVersion(act, b, which));
    }

    private static void pickVersion(final Activity act, final Broadcasts.Broadcast b,
                                    final int channel) {
        final ChannelGroup g = b.canais.get(channel);
        if (g.variants.size() == 1) {
            play(act, b, channel, 0);
            return;
        }
        String[] labels = new String[g.variants.size()];
        for (int i = 0; i < labels.length; i++) {
            ChannelGroup.Variant v = g.variants.get(i);
            String l = v.label().isEmpty() ? "Versão padrão" : v.label();
            if (ChannelNames.UHD4K.equals(v.quality)) {
                l = "4K (pesado, pode travar)";
            }
            if (v.hevc) {
                l = l + "  H265";
            }
            labels[i] = l;
        }
        Dialogs.list(act, g.nome, labels, which -> play(act, b, channel, which));
    }

    /** Abre o player. Cima e baixo trocam entre os canais da mesma emissora. */
    private static void play(Activity act, Broadcasts.Broadcast b, int channel, int version) {
        LiveSession.set(new ArrayList<>(b.canais), channel, version);
        act.startActivity(new Intent(act, LivePlayerActivity.class));
    }
}
