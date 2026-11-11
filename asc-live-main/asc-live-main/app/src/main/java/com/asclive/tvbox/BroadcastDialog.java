package com.asclive.tvbox;

import android.app.Activity;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/**
 * Do jogo até o vídeo: emissoras (Premiere, SporTV...) -> canais da emissora, cada um com a sua
 * resolução (Premiere 1 SD, Premiere 1 HD, Premiere 1 FHD...) -> assiste. Se só houver uma
 * emissora, ela é pulada e já aparece a lista de canais.
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

    /** Lista os canais da emissora, cada um com a sua resolução. OK abre o canal escolhido. */
    private static void pickChannel(final Activity act, final Broadcasts.Broadcast b) {
        if (b.canais.size() == 1) {
            play(act, b, 0);
            return;
        }
        String[] labels = new String[b.canais.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = b.canais.get(i).nome;
        }
        Dialogs.list(act, b.emissora, labels, which -> play(act, b, which));
    }

    /** Abre o player. Cima e baixo trocam entre os canais da mesma emissora. */
    private static void play(Activity act, Broadcasts.Broadcast b, int channel) {
        LiveSession.set(new ArrayList<>(b.canais), channel, -1);
        act.startActivity(new Intent(act, LivePlayerActivity.class));
    }
}
