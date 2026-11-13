package com.asclive.tvbox;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Uma linha de canal em vidro fumê: número (opcional), logo, nome, texto de baixo (programa ou
 * categoria), selo da resolução e estrela de favorito. Quem dá o contorno de foco é a lista.
 */
final class ChannelRow extends LinearLayout {

    /** Id do canal que a linha mostra agora (a resposta do guia só vale se ainda for o mesmo). */
    String boundId = "";
    private final boolean numbered;
    private final TextView number;
    private final ImageView logo;
    private final TextView name;
    private final TextView sub;
    private final TextView badge;
    private final TextView star;
    private final ImageView play;

    ChannelRow(Context c, boolean numbered) {
        super(c);
        this.numbered = numbered;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(Ui.glass(c, 14));
        setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        setLayoutParams(new AbsListView.LayoutParams(-1, Ui.dp(c, 62)));

        number = Ui.title(c, "", 17, C.TEXT2);
        number.setGravity(Gravity.CENTER);
        addView(number, new LayoutParams(Ui.dp(c, 46), -2));
        number.setVisibility(numbered ? VISIBLE : GONE);

        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LayoutParams llp = new LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48));
        llp.leftMargin = Ui.dp(c, 4);
        addView(logo, llp);

        LinearLayout words = new LinearLayout(c);
        words.setOrientation(VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        name = Ui.title(c, "", 18, C.TEXT);
        name.setSingleLine(false);
        name.setMaxLines(2);
        name.setEllipsize(null);
        words.addView(name);
        sub = Ui.text(c, "", 14, C.TEXT2);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(sub);
        LayoutParams wlp = new LayoutParams(0, -2, 1f);
        wlp.leftMargin = Ui.dp(c, 12);
        addView(words, wlp);

        star = Ui.pill(c, "\u2605", 14, 0x00000000, C.GLOW);
        star.setVisibility(GONE);
        addView(star);

        badge = Ui.pill(c, "", 12, 0x33FFFFFF, C.TEXT);
        badge.setTypeface(Ui.bold(c));
        LayoutParams blp = new LayoutParams(-2, -2);
        blp.leftMargin = Ui.dp(c, 8);
        addView(badge, blp);

        play = Ui.icon(c, R.drawable.ic_play, 22, C.ACCENT);
        LayoutParams plp = new LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
        plp.leftMargin = Ui.dp(c, 12);
        addView(play, plp);
        play.setVisibility(numbered ? GONE : VISIBLE);
    }

    void bind(int n, ChannelGroup g, boolean favorite, String subtitle) {
        boundId = g.item().id;
        if (numbered) {
            number.setText(String.format(java.util.Locale.US, "%03d", n));
        }
        name.setText(g.nome);
        sub.setText(subtitle == null ? "" : subtitle);
        ImageLoader.load(logo, g.logo, 160);
        String label = g.variants.get(0).label();
        badge.setText(label);
        badge.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
        star.setVisibility(favorite ? View.VISIBLE : View.GONE);
    }

    void setSub(String s) {
        sub.setText(s == null ? "" : s);
    }
}
