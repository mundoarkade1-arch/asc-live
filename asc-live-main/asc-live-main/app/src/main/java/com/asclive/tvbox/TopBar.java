package com.asclive.tvbox;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** NavegaÃ§Ã£o superior compacta, pensada para foco por controle remoto. */
final class TopBar extends LinearLayout {

    interface Listener {
        void onSelect(int id);
    }

    TopBar(Context c, final Listener listener) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 8));
        setBackground(Ui.shape(C.SHADE, Ui.dp(c, 16), 0, 0));

        LinearLayout brand = new LinearLayout(c);
        brand.setOrientation(VERTICAL);
        TextView name = Ui.title(c, "ASC LIVE", 24, C.TEXT);
        name.setTypeface(Ui.bold(c), Typeface.BOLD);
        brand.addView(name);
        brand.addView(Ui.text(c, "TV AO VIVO E FUTEBOL", 9, C.TEXT2));
        LayoutParams blp = new LayoutParams(Ui.dp(c, 150), -2);
        addView(brand, blp);

        addNav(c, listener, Sidebar.FOOTBALL, R.drawable.ic_tv, "InÃ­cio");
        addNav(c, listener, Sidebar.LIVE, R.drawable.ic_tv, "Canais");
        addNav(c, listener, Sidebar.FAVORITES, R.drawable.ic_bookmark, "Favoritos");
        addNav(c, listener, Sidebar.SEARCH, R.drawable.ic_search, "Buscar");
        addNav(c, listener, Sidebar.REFRESH, R.drawable.ic_refresh, "Atualizar");
        addNav(c, listener, Sidebar.SETTINGS, R.drawable.ic_settings, "Ajustes");
    }

    private void addNav(Context c, Listener listener, int id, int iconId, String label) {
        LinearLayout item = new LinearLayout(c);
        item.setOrientation(HORIZONTAL);
        item.setGravity(Gravity.CENTER);
        item.setFocusable(true);
        item.setClickable(true);
        item.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
        item.setBackground(Ui.focusBg(c, 0x00000000, C.SURFACE_FOCUS, 12, C.LIME, Ui.dp(c, 2)));
        ImageView icon = Ui.icon(c, iconId, 18, C.TEXT);
        item.addView(icon);
        TextView text = Ui.text(c, label, 15, C.TEXT);
        text.setTypeface(Ui.medium(c), Typeface.BOLD);
        LayoutParams tlp = new LayoutParams(-2, -2);
        tlp.leftMargin = Ui.dp(c, 6);
        item.addView(text, tlp);
        LayoutParams ilp = new LayoutParams(-2, Ui.dp(c, 44));
        ilp.leftMargin = Ui.dp(c, 3);
        addView(item, ilp);
        item.setOnClickListener(v -> listener.onSelect(id));
    }

    void requestInitialFocus() {
        if (getChildCount() > 1) {
            getChildAt(1).requestFocus();
        }
    }

    void start() {
    }

    void stop() {
    }
}
