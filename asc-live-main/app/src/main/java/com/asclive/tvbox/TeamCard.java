package com.asclive.tvbox;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Cartão de um time: brasão e nome em vidro. Mostra ♥ se é o time do coração e ★ se é favorito.
 * OK abre o time; segurar OK marca/desmarca a estrelinha (quem monta o cartão liga isso).
 */
final class TeamCard extends FrameLayout {

    static final int W_DP = 150;
    static final int H_DP = 146;

    final Teams.Team team;
    private final TextView badge;

    TeamCard(Context c, Teams.Team team, boolean favorite, boolean heart) {
        super(c);
        this.team = team;
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setBackground(Ui.glassFocusBg(c, 16));
        setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, W_DP), Ui.dp(c, H_DP)));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 8));
        ImageView shield = new ImageView(c);
        shield.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.load(shield, team.logo, 160);
        col.addView(shield, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64)));
        TextView name = Ui.text(c, team.name, 15, C.TEXT);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = Ui.dp(c, 10);
        col.addView(name, nlp);
        addView(col, new LayoutParams(-1, -1));

        badge = Ui.text(c, "", 18, C.GOLD);
        LayoutParams bl = new LayoutParams(-2, -2);
        bl.gravity = Gravity.TOP | Gravity.END;
        bl.setMargins(0, Ui.dp(c, 6), Ui.dp(c, 10), 0);
        addView(badge, bl);
        setStatus(favorite, heart);
    }

    void setStatus(boolean favorite, boolean heart) {
        badge.setText(heart ? "♥" : (favorite ? "★" : ""));
    }
}
