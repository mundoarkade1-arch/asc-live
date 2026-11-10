package com.asclive.tvbox;

import android.content.Context;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Cartão grande de filme ou série: capa 2:3 com cantos de 16dp, nota, selo NOVO e nome.
 * No foco: aumenta 6%, ganha contorno limão e um cantinho branco deslocado atrás
 * ("sombra sólida"). Sem sombra com blur (pesada em TV Box antigo).
 * Nas faixas da Home o próprio cartão recebe o foco; na grade quem recebe é a GridView e o
 * cartão só fica "selecionado" (ver TvGrid).
 */
final class Card extends FrameLayout {

    private static final long NEW_SECONDS = 14L * 24L * 3600L;

    final int pw;
    final int ph;
    private final ImageView image;
    private final LinearLayout placeholder;
    private final TextView phInitial;
    private final TextView phTitle;
    private final View outline;
    private final View corner;
    private final TextView title;
    private final TextView rating;
    private final TextView badgeNew;
    private View gridHost;

    Card(Context c, int posterW, boolean focusableCard) {
        super(c);
        pw = posterW;
        ph = posterW * 3 / 2;
        int pad = Ui.dp(c, 10);
        int titleH = Ui.dp(c, 46);
        setClipChildren(false);
        setClipToPadding(false);
        setLayoutParams(new LayoutParams(pw + pad, ph + Ui.dp(c, 12) + titleH));
        float r = Ui.dp(c, 16);

        corner = new View(c);
        corner.setBackground(Ui.shape(C.TEXT, r, 0, 0));
        corner.setVisibility(View.INVISIBLE);
        addView(corner, new LayoutParams(pw, ph));
        corner.setTranslationX(Ui.dp(c, 8));
        corner.setTranslationY(Ui.dp(c, 8));

        FrameLayout poster = new FrameLayout(c);
        poster.setBackground(Ui.shape(C.SURFACE, r, 0, 0));
        final float radius = r;
        poster.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        poster.setClipToOutline(true);
        addView(poster, new LayoutParams(pw, ph));

        placeholder = new LinearLayout(c);
        placeholder.setOrientation(LinearLayout.VERTICAL);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        phInitial = Ui.title(c, "", 48, C.LIME);
        phInitial.setGravity(Gravity.CENTER);
        phTitle = Ui.text(c, "", 14, C.TEXT2);
        phTitle.setGravity(Gravity.CENTER);
        phTitle.setMaxLines(3);
        phTitle.setEllipsize(TextUtils.TruncateAt.END);
        placeholder.addView(phInitial);
        placeholder.addView(phTitle);
        poster.addView(placeholder, new LayoutParams(-1, -1));

        image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.addView(image, new LayoutParams(-1, -1));

        rating = Ui.pill(c, "", 13, 0xCC101010, C.TEXT);
        LayoutParams rl = new LayoutParams(-2, -2);
        rl.gravity = Gravity.TOP | Gravity.END;
        rl.setMargins(0, Ui.dp(c, 8), Ui.dp(c, 8), 0);
        poster.addView(rating, rl);

        badgeNew = Ui.pill(c, "NOVO", 12, C.LIME, C.BG);
        badgeNew.setTypeface(Ui.bold(c));
        LayoutParams nl = new LayoutParams(-2, -2);
        nl.gravity = Gravity.TOP | Gravity.START;
        nl.setMargins(Ui.dp(c, 8), Ui.dp(c, 8), 0, 0);
        poster.addView(badgeNew, nl);

        outline = new View(c);
        outline.setBackground(Ui.shape(0x00000000, r, C.LIME, Ui.dp(c, 3)));
        outline.setVisibility(View.INVISIBLE);
        addView(outline, new LayoutParams(pw, ph));

        title = Ui.text(c, "", 16, C.TEXT);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams tl = new LayoutParams(pw, titleH);
        tl.topMargin = ph + Ui.dp(c, 12);
        addView(title, tl);

        if (focusableCard) {
            setFocusable(true);
            setClickable(true);
            setLongClickable(true);
        }
    }

    int cardW() {
        return pw + Ui.dp(getContext(), 10);
    }

    int cardH() {
        return ph + Ui.dp(getContext(), 12) + Ui.dp(getContext(), 46);
    }

    /** Na grade: avisa qual View de grade tem o foco de verdade. */
    void setGridHost(View host) {
        gridHost = host;
    }

    void bind(XtreamApi.Item it) {
        bindRaw(it.name, it.icon, it.rating, it.added);
    }

    void bindRaw(String name, String iconUrl, double rate, long added) {
        title.setText(name);
        phInitial.setText(Profiles.initial(name));
        phTitle.setText(name);
        ImageLoader.load(image, iconUrl, 480);
        if (rate > 0) {
            rating.setText(String.format(java.util.Locale.US, "\u2605 %.1f", rate));
            rating.setVisibility(View.VISIBLE);
        } else {
            rating.setVisibility(View.GONE);
        }
        boolean fresh = added > 0 && (System.currentTimeMillis() / 1000L - added) < NEW_SECONDS;
        badgeNew.setVisibility(fresh ? View.VISIBLE : View.GONE);
        // Reinicia o aspecto de foco sem animar (o cartão pode estar sendo reaproveitado).
        outline.setVisibility(View.INVISIBLE);
        corner.setVisibility(View.INVISIBLE);
        animate().cancel();
        setScaleX(1f);
        setScaleY(1f);
        setTranslationZ(0f);
    }

    void showFocus(boolean on) {
        outline.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        corner.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        float s = on ? 1.06f : 1f;
        animate().scaleX(s).scaleY(s).setDuration(120).start();
        // Fica por cima dos vizinhos enquanto está maior (sem mudar a ordem das views).
        setTranslationZ(on ? Ui.dp(getContext(), 4) : 0f);
    }

    /** Na grade: o cartão só acende se estiver selecionado E a grade tiver o foco. */
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

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect prev) {
        super.onFocusChanged(gainFocus, direction, prev);
        if (gridHost == null) {
            showFocus(gainFocus);
        }
    }
}
