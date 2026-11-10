package com.asclive.tvbox;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

/** Adaptador da grade de capas (Filmes, Séries, Busca, Favoritos). Reaproveita os cartões. */
final class CardAdapter extends BaseAdapter {

    final List<XtreamApi.Item> items = new ArrayList<>();
    private final Context ctx;
    private final TvGrid grid;
    private final int pw;

    CardAdapter(Context ctx, TvGrid grid) {
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
        Card card;
        if (convertView instanceof Card) {
            card = (Card) convertView;
        } else {
            card = new Card(ctx, pw, false);
            card.setGridHost(grid);
        }
        card.bind(items.get(position));
        return card;
    }

    /** Configura a grade: 6 colunas de cartões grandes. */
    static void setup(TvGrid g, CardAdapter a) {
        Context c = g.getContext();
        g.setNumColumns(6);
        g.setColumnWidth(a.cardW());
        g.setHorizontalSpacing(Ui.dp(c, 10));
        g.setVerticalSpacing(Ui.dp(c, 6));
        g.setPadding(Ui.dp(c, 28), Ui.dp(c, 10), Ui.dp(c, 28), Ui.dp(c, 30));
        g.setAdapter(a);
    }
}
