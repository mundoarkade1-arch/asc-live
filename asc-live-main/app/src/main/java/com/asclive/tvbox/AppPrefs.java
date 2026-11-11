package com.asclive.tvbox;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.WindowManager;

/** Ajustes simples do aparelho (valem para todos os perfis). */
final class AppPrefs {

    private static final String FILE = "asc_settings";

    private AppPrefs() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean isSecure(Context c) {
        return p(c).getBoolean("secure", false);
    }

    static void setSecure(Context c, boolean on) {
        p(c).edit().putBoolean("secure", on).apply();
    }

    /** Qualidade preferida ao abrir um canal: SD, HD ou FHD (padrão FHD). */
    static String preferredQuality(Context c) {
        String q = p(c).getString("quality", ChannelNames.FHD);
        return ChannelNames.SD.equals(q) || ChannelNames.HD.equals(q) ? q : ChannelNames.FHD;
    }

    static void setPreferredQuality(Context c, String q) {
        p(c).edit().putString("quality", q).apply();
    }

    /** A tela de boas-vindas só aparece uma vez. */
    static boolean welcomeDone(Context c) {
        return p(c).getBoolean("welcome", false);
    }

    static void setWelcomeDone(Context c) {
        p(c).edit().putBoolean("welcome", true).apply();
    }

    /** Bloqueia capturas de tela quando o ajuste está ligado. Chamar no começo de cada tela. */
    static void applyWindow(Activity a) {
        if (isSecure(a)) {
            a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
}
