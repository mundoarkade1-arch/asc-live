package com.asclive.tvbox;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Cabeçalho compacto da TV: marca, busca, conectividade, relógio e ajustes. */
final class MainHeader extends LinearLayout {
    interface Listener { void onSearch(); void onSettings(); }

    private final TextView clock;
    private final TextView date;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
            date.setText(new SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(now));
            postDelayed(this, 30000L);
        }
    };

    MainHeader(Context c, Listener listener) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.dp(c, 22), Ui.dp(c, 8), Ui.dp(c, 22), Ui.dp(c, 8));
        setBackground(Ui.shape(0x00000000, 0, 0, 0));

        TextView brand = Ui.title(c, "ASC LIVE", 24, C.TEXT);
        brand.setTypeface(Ui.bold(c), Typeface.BOLD);
        addView(brand, new LayoutParams(Ui.dp(c, 145), -2));

        TextView search = Ui.text(c, "⌕  Buscar canais, jogos e times", 16, C.TEXT2);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setFocusable(true);
        search.setClickable(true);
        search.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        search.setBackground(Ui.softFocusBg(c, C.SURFACE_FOCUS, 12));
        search.setOnClickListener(v -> listener.onSearch());
        LayoutParams searchLp = new LayoutParams(0, Ui.dp(c, 46), 1f);
        searchLp.leftMargin = Ui.dp(c, 20);
        searchLp.rightMargin = Ui.dp(c, 22);
        addView(search, searchLp);

        ImageView wifi = Ui.icon(c, R.drawable.ic_refresh, 20, C.ACCENT);
        addView(wifi, new LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));

        LinearLayout time = new LinearLayout(c);
        time.setOrientation(VERTICAL);
        time.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        clock = Ui.title(c, "--:--", 22, C.TEXT);
        clock.setGravity(Gravity.END);
        date = Ui.text(c, "", 12, C.TEXT2);
        date.setGravity(Gravity.END);
        time.addView(clock);
        time.addView(date);
        LayoutParams timeLp = new LayoutParams(Ui.dp(c, 92), -2);
        timeLp.leftMargin = Ui.dp(c, 12);
        addView(time, timeLp);

        TextView settings = Ui.text(c, "⚙", 24, C.TEXT2);
        settings.setGravity(Gravity.CENTER);
        settings.setFocusable(true);
        settings.setClickable(true);
        settings.setBackground(Ui.softFocusBg(c, C.SURFACE_FOCUS, 12));
        settings.setOnClickListener(v -> listener.onSettings());
        LayoutParams settingsLp = new LayoutParams(Ui.dp(c, 48), Ui.dp(c, 46));
        settingsLp.leftMargin = Ui.dp(c, 10);
        addView(settings, settingsLp);
        post(tick);
    }

    void startClock() { removeCallbacks(tick); post(tick); }
    void stopClock() { removeCallbacks(tick); }
}
