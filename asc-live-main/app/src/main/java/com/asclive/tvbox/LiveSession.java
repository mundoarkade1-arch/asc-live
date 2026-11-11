package com.asclive.tvbox;

import java.util.ArrayList;
import java.util.List;

/**
 * Passa para o player a lista de canais em que a pessoa está (para trocar de canal com
 * cima/baixo), qual canal abrir e qual versão. Fica em memória: o endereço do canal nunca
 * vai dentro de um Intent. Só usar na thread principal.
 */
final class LiveSession {

    static List<ChannelGroup> list = new ArrayList<>();
    static int index;
    /** Versão pedida (posição em ChannelGroup.variants); -1 = escolher sozinho. */
    static int variant = -1;

    private LiveSession() {
    }

    static void set(List<ChannelGroup> channels, int at, int version) {
        list = channels == null ? new ArrayList<ChannelGroup>() : channels;
        index = Math.max(0, Math.min(at, Math.max(0, list.size() - 1)));
        variant = version;
    }

    static ChannelGroup current() {
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }
}
