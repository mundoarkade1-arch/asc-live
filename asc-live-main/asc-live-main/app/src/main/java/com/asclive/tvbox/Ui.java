package com.asclive.tvbox;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

/** Ferramentas visuais: medidas, formas, fontes, botões e rótulos no estilo do app. */
final class Ui {

    static final int PRIMARY = 0;
    static final int DARK = 1;
    static final int LIME = 2;

    /** Alturas e larguras fixas do quadro principal (em dp). */
    static final int TOP_BAR_DP = 76;
    static final int HINT_BAR_DP = 0;
    static final int SIDEBAR_DP = 0;

    private static Typeface bold;
    private static Typeface medium;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static int screenW(Context c) {
        return c.getResources().getDisplayMetrics().widthPixels;
    }

    static int screenH(Context c) {
        return c.getResources().getDisplayMetrics().heightPixels;
    }

    static GradientDrawable shape(int fill, float radiusPx, int stroke, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) {
            g.setStroke(strokePx, stroke);
        }
        return g;
    }

    /** Painel de vidro fumê: fundo claro transparente com borda fina. */
    static GradientDrawable glass(Context c, float radiusDp) {
        return shape(C.GLASS, dp(c, radiusDp), C.GLASS_EDGE, dp(c, 1));
    }

    /**
     * Fundo do app: degradê escuro com um brilho limão suave no canto. Os painéis de vidro
     * (transparentes) ficam por cima dele. Sem imagem e sem blur: leve para TV Box antigo.
     */
    static Drawable appBackground(Context c) {
        return shape(C.BG, 0, 0, 0);
    }

    static synchronized Typeface bold(Context c) {
        if (bold == null) {
            try {
                bold = ResourcesCompat.getFont(c.getApplicationContext(), R.font.poppins_bold);
            } catch (Exception ignored) {
                bold = null;
            }
            if (bold == null) {
                bold = Typeface.DEFAULT_BOLD;
            }
        }
        return bold;
    }

    static synchronized Typeface medium(Context c) {
        if (medium == null) {
            try {
                medium = ResourcesCompat.getFont(c.getApplicationContext(), R.font.poppins_medium);
            } catch (Exception ignored) {
                medium = null;
            }
            if (medium == null) {
                medium = Typeface.DEFAULT;
            }
        }
        return medium;
    }

    /** Texto comum (fonte do sistema). */
    static TextView text(Context c, CharSequence t, float sp, int color) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        return v;
    }

    /** Título: fonte de destaque, em negrito. */
    static TextView title(Context c, CharSequence t, float sp, int color) {
        TextView v = text(c, t, sp, color);
        v.setTypeface(bold(c));
        return v;
    }

    /** Botão que recebe foco. style: PRIMARY (branco), DARK (escuro com borda) ou LIME. */
    static TextView button(Context c, CharSequence label, int style) {
        TextView b = new TextView(c);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setTypeface(medium(c));
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(dp(c, 22), dp(c, 14), dp(c, 22), dp(c, 14));
        int fill;
        int textColor;
        int stroke;
        int strokePx;
        if (style == PRIMARY) {
            fill = C.TEXT;
            textColor = C.BG;
            stroke = C.TEXT;
            strokePx = 0;
        } else if (style == LIME) {
            fill = C.LIME;
            textColor = C.BG;
            stroke = C.LIME;
            strokePx = 0;
        } else {
            fill = C.SURFACE;
            textColor = C.TEXT;
            stroke = C.LINE;
            strokePx = dp(c, 1);
        }
        b.setTextColor(textColor);
        b.setBackground(focusBg(c, fill, style == DARK ? C.SURFACE_FOCUS : fill, 14, stroke,
                strokePx));
        return b;
    }

    /** Fundo com contorno limão quando recebe foco. */
    static StateListDrawable focusBg(Context c, int fill, int fillFocus, float radiusDp,
                                     int stroke, int strokePx) {
        float r = dp(c, radiusDp);
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = shape(fillFocus, r, C.LIME, dp(c, 3));
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[]{android.R.attr.state_pressed}, on);
        s.addState(new int[]{}, shape(fill, r, stroke, strokePx));
        return s;
    }

    /** Retângulo limão com texto preto (como "3D" na página de referência). */
    static TextView limeTag(Context c, CharSequence t) {
        TextView v = title(c, t, 13, C.BG);
        v.setBackground(shape(C.LIME, dp(c, 6), 0, 0));
        v.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        return v;
    }

    /** Pílula escura pequena (nota, gênero). */
    static TextView pill(Context c, CharSequence t, float sp, int fill, int color) {
        TextView v = text(c, t, sp, color);
        v.setBackground(shape(fill, dp(c, 20), 0, 0));
        v.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        return v;
    }

    static ImageView icon(Context c, int res, int sizeDp, int tint) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(tint);
        v.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(c, sizeDp),
                dp(c, sizeDp)));
        return v;
    }

    /** Pinta uma parte do texto (como "assistindo" em limão). */
    static CharSequence highlight(String full, String part, int color) {
        android.text.SpannableString sp = new android.text.SpannableString(full);
        int i = full.indexOf(part);
        if (i >= 0) {
            sp.setSpan(new android.text.style.ForegroundColorSpan(color), i, i + part.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sp;
    }

    /** Largura da capa nas grades (6 colunas, conforme a largura da tela). */
    static int gridPw(Context c) {
        int content = screenW(c) - dp(c, 72) - dp(c, 56);
        return content / 6 - dp(c, 20);
    }

    /** Deixa o sistema escolher onde começa o foco. */
    static void focus(View v) {
        if (v != null) {
            v.requestFocus();
        }
    }

    static void toast(Activity a, String msg) {
        android.widget.Toast.makeText(a, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}
