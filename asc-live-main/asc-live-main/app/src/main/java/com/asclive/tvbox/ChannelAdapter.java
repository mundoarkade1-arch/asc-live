package com.asclive.tvbox;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador da grade de canais (Ao vivo, Favoritos, Buscar). Reaproveita os cartões.
 * No login por servidor, cada cartão mostra o programa de agora e o próximo (EPG), pedido ao
 * servidor só dos canais que aparecem na tela (como no ASC TV Box).
 */
final class ChannelAdapter extends BaseAdapter {

    /** Canais mostrados agora. A lista é trocada por inteira (não copiada). */
    List<ChannelGroup> items = new ArrayList<>();
    private final Context ctx;
    private final TvGrid grid;
    private final int pw;
    /** Só o login por servidor tem guia de programação. */
    private final boolean epgOn;
    private static final long ASK_AGAIN_MS = 20000L;
    private static final int MAX_MEMO = 400;
    private final Map<String, String[]> nowText = new HashMap<>();
    private final Map<String, Long> askedAt = new HashMap<>();

    ChannelAdapter(Context ctx, TvGrid grid) {
        this.ctx = ctx;
        this.grid = grid;
        this.pw = Ui.gridPw(ctx);
        CredentialStore.Credentials c = Catalog.creds(ctx);
        this.epgOn = c != null && !Catalog.isM3u(c);
    }

    int cardW() {
        return pw + Ui.dp(ctx, 10);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ChannelCard card;
        if (convertView instanceof ChannelCard) {
            card = (ChannelCard) convertView;
        } else {
            card = new ChannelCard(ctx, pw, epgOn);
            card.setGridHost(grid);
        }
        ChannelGroup g = items.get(position);
        card.bind(g, Favorites.isFavorite(ctx, g.key()));
        showNow(card, g.item());
        return card;
    }

    /** Escreve no cartão o programa de agora. Usa o que já foi consultado; senão pede ao servidor. */
    private void showNow(final ChannelCard card, XtreamApi.Item item) {
        final String id = item.id;
        card.boundId = id;
        card.setEpg("", "");
        if (!epgOn) {
            return;
        }
        String[] known = nowText.get(id);
        long now = System.currentTimeMillis();
        Long when = askedAt.get(id);
        if (known != null && when != null && now - when < ASK_AGAIN_MS) {
            card.setEpg(known[0], known[1]);
            return;
        }
        if (when != null && now - when < ASK_AGAIN_MS) {
            return; // já pedido: a resposta preenche o cartão quando chegar
        }
        if (askedAt.size() > MAX_MEMO) {
            askedAt.clear();
            nowText.clear();
        }
        askedAt.put(id, now);
        CredentialStore.Credentials c = Catalog.creds(ctx);
        if (c == null) {
            return;
        }
        EpgCache.get(c.server, c.user, c.pass, id, epg -> {
            String[] text = new String[2];
            if (epg == null) {
                text[0] = "Programa\u00e7\u00e3o indispon\u00edvel";
                text[1] = "";
            } else {
                text[0] = "Agora: " + epg.title;
                text[1] = (epg.nextTitle == null || epg.nextTitle.isEmpty())
                        ? "" : "A seguir: " + epg.nextTitle;
            }
            nowText.put(id, text);
            if (id.equals(card.boundId)) {
                card.setEpg(text[0], text[1]);
            }
        });
    }

    /** Configura a grade: 6 colunas de cartões. */
    static void setup(TvGrid g, ChannelAdapter a) {
        Context c = g.getContext();
        g.setNumColumns(6);
        g.setColumnWidth(a.cardW());
        g.setHorizontalSpacing(Ui.dp(c, 10));
        g.setVerticalSpacing(Ui.dp(c, 6));
        g.setPadding(Ui.dp(c, 28), Ui.dp(c, 10), Ui.dp(c, 28), Ui.dp(c, 30));
        g.setAdapter(a);
    }
}
