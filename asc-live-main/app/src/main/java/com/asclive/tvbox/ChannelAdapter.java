package com.asclive.tvbox;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

/** Adaptador da grade de canais (Ao vivo, Favoritos, Buscar). Reaproveita os cartões. */
final class ChannelAdapter extends BaseAdapter {

    /** Canais mostrados agora. A lista é trocada por inteira (não copiada). */
    List<ChannelGroup> items = new ArrayList<>();
    private final Context ctx;
    private final TvGrid grid;
    private final int pw;

    ChannelAdapter(Context ctx, TvGrid grid) {
        this.ctx = ctx;
        this.grid = grid;
        this.pw = Ui.gridPw(ctx);
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
            card = new ChannelCard(ctx, pw);
            card.setGridHost(grid);
        }
        ChannelGroup g = items.get(position);
        card.bind(g, Favorites.isFavorite(ctx, g.key));
        return card;
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
