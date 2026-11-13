package com.asclive.tvbox;

import android.content.Context;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Cartão de canal: logo no centro, selo da resolução (FHD, HD, SD...) no canto, estrela se for
 * favorito, o nome embaixo e, no login por servidor, o programa de agora e o próximo (EPG). No foco: aumenta 6%, ganha contorno limão e um cantinho branco atrás
 * (sem sombra com blur, que pesa em TV Box antigo). Na grade quem recebe foco é a GridView
 * (ver TvGrid); o cartão só fica "selecionado".
 */
final class ChannelCard extends FrameLayout {

    final int pw;
    final int ph;
    private final ImageView image;
    private final TextView initial;
    private final TextView badge;
    private final TextView star;
    private final TextView title;
    private final TextView now;
    private final TextView next;
    /** Id do canal que o cartão mostra agora (a resposta do guia só vale se ainda for o mesmo). */
    String boundId = "";
    private final View outline;
    private final View corner;
    private View gridHost;

    ChannelCard(Context c, int posterW, boolean withEpg) {
        super(c);
        pw = posterW;
        ph = posterW * 3 / 4;
        int pad = Ui.dp(c, 10);
        int titleH = Ui.dp(c, 42);
        int lineH = Ui.dp(c, 17);
        int epgH = withEpg ? lineH * 2 + Ui.dp(c, 2) : 0;
        setClipChildren(false);
        setClipToPadding(false);
        setLayoutParams(new LayoutParams(pw + pad, ph + Ui.dp(c, 12) + titleH + epgH));
        float r = Ui.dp(c, 16);

        corner = new View(c);
        corner.setBackground(Ui.shape(C.TEXT, r, 0, 0));
        corner.setVisibility(View.INVISIBLE);
        addView(corner, new LayoutParams(pw, ph));
        corner.setTranslationX(Ui.dp(c, 8));
        corner.setTranslationY(Ui.dp(c, 8));

        FrameLayout poster = new FrameLayout(c);
        poster.setBackground(Ui.shape(C.GLASS, r, C.GLASS_EDGE, Ui.dp(c, 1)));
        final float radius = r;
        poster.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        poster.setClipToOutline(true);
        addView(poster, new LayoutParams(pw, ph));

        initial = Ui.title(c, "", 44, C.TEXT);
        initial.setGravity(Gravity.CENTER);
        poster.addView(initial, new LayoutParams(-1, -1));

        image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int ip = Ui.dp(c, 18);
        image.setPadding(ip, ip, ip, ip);
        poster.addView(image, new LayoutParams(-1, -1));

        badge = Ui.pill(c, "", 12, C.ACCENT, C.BG);
        badge.setTypeface(Ui.bold(c));
        LayoutParams bl = new LayoutParams(-2, -2);
        bl.gravity = Gravity.TOP | Gravity.END;
        bl.setMargins(0, Ui.dp(c, 8), Ui.dp(c, 8), 0);
        poster.addView(badge, bl);

        star = Ui.pill(c, "\u2605", 14, 0xCC03231F, C.GLOW);
        LayoutParams sl = new LayoutParams(-2, -2);
        sl.gravity = Gravity.TOP | Gravity.START;
        sl.setMargins(Ui.dp(c, 8), Ui.dp(c, 8), 0, 0);
        poster.addView(star, sl);

        outline = new View(c);
        outline.setBackground(Ui.shape(0x00000000, r, C.ACCENT, Ui.dp(c, 3)));
        outline.setVisibility(View.INVISIBLE);
        addView(outline, new LayoutParams(pw, ph));

        title = Ui.text(c, "", 16, C.TEXT);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams tl = new LayoutParams(pw, titleH);
        tl.topMargin = ph + Ui.dp(c, 12);
        addView(title, tl);

        now = Ui.text(c, "", 12, C.TEXT);
        now.setSingleLine(true);
        now.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams nl = new LayoutParams(pw, lineH);
        nl.topMargin = ph + Ui.dp(c, 12) + titleH;
        addView(now, nl);
        next = Ui.text(c, "", 12, C.TEXT2);
        next.setSingleLine(true);
        next.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams xl = new LayoutParams(pw, lineH);
        xl.topMargin = ph + Ui.dp(c, 12) + titleH + lineH;
        addView(next, xl);
        now.setVisibility(withEpg ? View.VISIBLE : View.GONE);
        next.setVisibility(withEpg ? View.VISIBLE : View.GONE);
    }

    /** Escreve o programa de agora e o próximo (texto vazio apaga). */
    void setEpg(String nowText, String nextText) {
        now.setText(nowText == null ? "" : nowText);
        next.setText(nextText == null ? "" : nextText);
    }

    void setGridHost(View host) {
        gridHost = host;
    }

    void bind(ChannelGroup g, boolean favorite) {
        title.setText(g.nome);
        initial.setText(initialOf(g.nome));
        ImageLoader.load(image, g.logo, 320);
        // Cada canal tem a sua resolução: o selo mostra qual é (FHD, HD, SD, 4K).
        String label = g.variants.get(0).label();
        badge.setText(label);
        badge.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
        star.setVisibility(favorite ? View.VISIBLE : View.GONE);
        // Cartão reaproveitado: começa sem aspecto de foco e sem animar.
        outline.setVisibility(View.INVISIBLE);
        corner.setVisibility(View.INVISIBLE);
        animate().cancel();
        setScaleX(1f);
        setScaleY(1f);
        setTranslationZ(0f);
    }

    private static String initialOf(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        return name.trim().substring(0, 1).toUpperCase();
    }

    void showFocus(boolean on) {
        outline.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        corner.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        float s = on ? 1.06f : 1f;
        animate().scaleX(s).scaleY(s).setDuration(120).start();
        setTranslationZ(on ? Ui.dp(getContext(), 4) : 0f);
    }

    /** O cartão só acende se estiver selecionado E a grade tiver o foco. */
    void refreshSelected() {
        showFocus(isSelected() && gridHost != null && gridHost.hasFocus());
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (gridHost != null) {
            refreshSelected();
        }
    }
}
