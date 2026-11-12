package com.asclive.tvbox;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Barra de cima: logo do app (o mesmo ícone de TV com play do banner), busca, hora e data.
 * A busca e a engrenagem respondem ao toque; com o controle remoto, use a barra lateral.
 */
final class TopBar extends LinearLayout {

    interface Listener {
        void onSearch();

        void onSettings();
    }

    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("EEE, d 'de' MMM", new Locale("pt", "BR"));
    private final TextView clock;
    private final TextView date;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            h.postDelayed(this, 20000L);
        }
    };

    TopBar(Context c, final Listener listener) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.dp(c, 28), Ui.dp(c, 8), Ui.dp(c, 28), Ui.dp(c, 4));

        // ---- Logo: TV com play (ícone do banner) + ASC LIVE ----
        FrameLayout mark = new FrameLayout(c);
        ImageView tv = new ImageView(c);
        tv.setImageResource(R.drawable.ic_tv);
        tv.setColorFilter(C.LIME);
        mark.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        ImageView play = new ImageView(c);
        play.setImageResource(R.drawable.ic_play);
        play.setColorFilter(C.LIME);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16));
        plp.gravity = Gravity.CENTER;
        plp.topMargin = Ui.dp(c, 6);
        mark.addView(play, plp);
        addView(mark, new LayoutParams(Ui.dp(c, 46), Ui.dp(c, 46)));

        LinearLayout words = new LinearLayout(c);
        words.setOrientation(VERTICAL);
        SpannableString name = new SpannableString("ASC LIVE");
        name.setSpan(new ForegroundColorSpan(C.LIME), 4, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        words.addView(Ui.title(c, name, 26, C.TEXT));
        TextView tag = Ui.text(c, "TV AO VIVO E FUTEBOL", 10, C.TEXT2);
        tag.setLetterSpacing(0.12f);
        words.addView(tag);
        LayoutParams wlp = new LayoutParams(-2, -2);
        wlp.leftMargin = Ui.dp(c, 12);
        addView(words, wlp);

        // ---- Busca ----
        LinearLayout search = new LinearLayout(c);
        search.setOrientation(HORIZONTAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setBackground(Ui.shape(C.GLASS, Ui.dp(c, 26), C.GLASS_EDGE, Ui.dp(c, 1)));
        search.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
        search.setClickable(true);
        search.setOnClickListener(v -> listener.onSearch());
        search.addView(Ui.icon(c, R.drawable.ic_search, 22, C.TEXT2));
        TextView hint = Ui.text(c, "Buscar jogos, canais ou campeonatos...", 17, C.TEXT2);
        hint.setSingleLine(true);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams hlp = new LayoutParams(0, -2, 1f);
        hlp.leftMargin = Ui.dp(c, 12);
        search.addView(hint, hlp);
        LayoutParams slp = new LayoutParams(0, Ui.dp(c, 46), 1f);
        slp.setMargins(Ui.dp(c, 40), 0, Ui.dp(c, 40), 0);
        addView(search, slp);

        // ---- Hora e data ----
        LinearLayout when = new LinearLayout(c);
        when.setOrientation(VERTICAL);
        when.setGravity(Gravity.END);
        clock = Ui.title(c, "", 28, C.TEXT);
        clock.setGravity(Gravity.END);
        when.addView(clock);
        date = Ui.text(c, "", 14, C.TEXT2);
        date.setGravity(Gravity.END);
        when.addView(date);
        addView(when, new LayoutParams(-2, -2));

        ImageView gear = Ui.icon(c, R.drawable.ic_settings, 30, C.TEXT);
        gear.setClickable(true);
        gear.setOnClickListener(v -> listener.onSettings());
        LayoutParams glp = new LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30));
        glp.leftMargin = Ui.dp(c, 22);
        addView(gear, glp);
        refresh();
    }

    private void refresh() {
        Date now = new Date();
        clock.setText(clockFmt.format(now));
        String d = dateFmt.format(now);
        if (!d.isEmpty()) {
            d = Character.toUpperCase(d.charAt(0)) + d.substring(1);
        }
        date.setText(d);
    }

    /** Começa a atualizar a hora (chamar quando a tela volta). */
    void start() {
        h.removeCallbacks(tick);
        refresh();
        h.postDelayed(tick, 20000L);
    }

    /** Para de atualizar (a tela saiu de cena). */
    void stop() {
        h.removeCallbacks(tick);
    }
}
