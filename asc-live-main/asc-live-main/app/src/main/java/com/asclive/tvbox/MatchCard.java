package com.asclive.tvbox;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Linha de um jogo: liga e hora (ou AO VIVO em limão) à esquerda; casa, escudos e placar no centro.
 * Não recebe foco: quem recebe é a ListView (ver FootballScreen), que desenha o contorno limão.
 */
final class MatchCard extends LinearLayout {

    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final TextView league;
    private final TextView time;
    private final LinearLayout liveBox;
    private final TextView liveMinute;
    private final TextView homeName;
    private final TextView awayName;
    private final ImageView homeShield;
    private final ImageView awayShield;
    private final TextView score;

    MatchCard(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(Ui.glass(c, 16));
        setPadding(Ui.dp(c, 22), Ui.dp(c, 10), Ui.dp(c, 22), Ui.dp(c, 10));
        setLayoutParams(new AbsListView.LayoutParams(-1, Ui.dp(c, 92)));

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(VERTICAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        league = Ui.text(c, "", 14, C.TEXT2);
        league.setSingleLine(true);
        league.setEllipsize(TextUtils.TruncateAt.END);
        left.addView(league);
        time = Ui.title(c, "", 26, C.TEXT);
        LayoutParams tlp = new LayoutParams(-2, -2);
        tlp.topMargin = Ui.dp(c, 4);
        left.addView(time, tlp);
        liveBox = new LinearLayout(c);
        liveBox.setOrientation(HORIZONTAL);
        liveBox.setGravity(Gravity.CENTER_VERTICAL);
        liveBox.addView(Ui.limeTag(c, "AO VIVO"));
        liveMinute = Ui.text(c, "", 16, C.LIME);
        LayoutParams mlp = new LayoutParams(-2, -2);
        mlp.leftMargin = Ui.dp(c, 8);
        liveBox.addView(liveMinute, mlp);
        LayoutParams llp = new LayoutParams(-2, -2);
        llp.topMargin = Ui.dp(c, 4);
        left.addView(liveBox, llp);
        addView(left, new LayoutParams(Ui.dp(c, 210), -1));

        homeName = Ui.title(c, "", 22, C.TEXT);
        homeName.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        homeName.setSingleLine(true);
        homeName.setEllipsize(TextUtils.TruncateAt.END);
        addView(homeName, new LayoutParams(0, -2, 1f));

        homeShield = shield(c);
        awayShield = shield(c);
        LayoutParams hs = new LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52));
        hs.leftMargin = Ui.dp(c, 14);
        addView(homeShield, hs);

        score = Ui.title(c, "", 30, C.TEXT);
        score.setGravity(Gravity.CENTER);
        addView(score, new LayoutParams(Ui.dp(c, 110), -2));

        LayoutParams as = new LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52));
        as.rightMargin = Ui.dp(c, 14);
        addView(awayShield, as);

        awayName = Ui.title(c, "", 22, C.TEXT);
        awayName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        awayName.setSingleLine(true);
        awayName.setEllipsize(TextUtils.TruncateAt.END);
        addView(awayName, new LayoutParams(0, -2, 1f));
    }

    private static ImageView shield(Context c) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    void bind(Match m) {
        league.setText(m.liga);
        homeName.setText(m.casa);
        awayName.setText(m.fora);
        ImageLoader.load(homeShield, m.escudoCasa, 160);
        ImageLoader.load(awayShield, m.escudoFora, 160);
        if (m.status == Match.LIVE) {
            time.setVisibility(View.GONE);
            liveBox.setVisibility(View.VISIBLE);
            liveMinute.setText(m.statusText);
            score.setText(m.placar.isEmpty() ? "0 x 0" : m.placar);
            score.setTextColor(C.LIME);
        } else if (m.status == Match.FINISHED) {
            liveBox.setVisibility(View.GONE);
            time.setVisibility(View.VISIBLE);
            time.setText(m.statusText.isEmpty() ? "Encerrado" : m.statusText);
            time.setTextSize(18);
            time.setTextColor(C.TEXT2);
            score.setText(m.placar);
            score.setTextColor(C.TEXT);
        } else {
            liveBox.setVisibility(View.GONE);
            time.setVisibility(View.VISIBLE);
            boolean note = !m.statusText.isEmpty();
            time.setText(note ? m.statusText : clock.format(new Date(m.horario)));
            time.setTextSize(note ? 18 : 26);
            time.setTextColor(note ? C.TEXT2 : C.TEXT);
            score.setText("x");
            score.setTextColor(C.TEXT2);
        }
    }
}
