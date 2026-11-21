package com.asclive.tvbox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * Esqueleto de carregamento: blocos de vidro com um brilho que passa da esquerda para a direita.
 * Uma View só desenha tudo (um brilho único para a tela inteira), então é leve em TV Box.
 * O brilho tem relógio próprio (como o LoadingSpinner): anda mesmo com as animações do sistema
 * desligadas e só roda enquanto a view está visível. Não recebe foco nem clique.
 */
final class Skeleton extends View {

    private static final long TICK_MS = 40L;
    private static final float SWEEP_PX_PER_TICK_DP = 22f;

    private static final int GRID = 0;
    private static final int LIST = 1;

    private final int mode;
    // medidas em px
    private final int padL;
    private final int padT;
    private final int cols;
    private final int cellW;
    private final int cellH;
    private final int gapX;
    private final int gapY;
    private final int posterW;
    private final int posterH;
    private final int radius;
    private final boolean withEpg;
    private final boolean withLogo;
    private final boolean lines;
    private final int lineH;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF box = new RectF();
    private LinearGradient gradient;
    private float sweepW;
    private float offset;
    private final float step;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            offset += step;
            if (offset > getWidth() + sweepW) {
                offset = -sweepW;
            }
            invalidate();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private Skeleton(Context c, int mode, int padL, int padT, int cols, int cellW, int cellH,
                     int gapX, int gapY, int posterW, int posterH, int radius, boolean withEpg,
                     boolean withLogo, boolean lines) {
        super(c);
        this.mode = mode;
        this.padL = padL;
        this.padT = padT;
        this.cols = cols;
        this.cellW = cellW;
        this.cellH = cellH;
        this.gapX = gapX;
        this.gapY = gapY;
        this.posterW = posterW;
        this.posterH = posterH;
        this.radius = radius;
        this.withEpg = withEpg;
        this.withLogo = withLogo;
        this.lines = lines;
        this.lineH = Ui.dp(c, 10);
        this.step = Ui.dp(c, SWEEP_PX_PER_TICK_DP);
        setFocusable(false);
        setClickable(false);
        base.setStyle(Paint.Style.FILL);
        base.setColor(C.SKELETON);
        shine.setStyle(Paint.Style.FILL);
        offset = -1f;
    }

    /** Grade de cartões de canal (mesma medida do ChannelCard/ChannelAdapter). */
    static Skeleton cards(Context c, boolean withEpg) {
        int pw = Ui.gridPw(c);
        int ph = pw * 3 / 4;
        int cellW = pw + Ui.dp(c, 10);
        int cellH = ph + Ui.dp(c, 12) + Ui.dp(c, 42) + (withEpg ? Ui.dp(c, 36) : 0);
        return new Skeleton(c, GRID, Ui.dp(c, 28), Ui.dp(c, 10), 6, cellW, cellH,
                Ui.dp(c, 10), Ui.dp(c, 6), pw, ph, Ui.dp(c, 16), withEpg, false, true);
    }

    /** Lista de linhas de canal (mesma medida do ChannelRow, na tela Ao vivo). */
    static Skeleton channelRows(Context c) {
        return new Skeleton(c, LIST, Ui.dp(c, 4), Ui.dp(c, 4), 1, 0, Ui.dp(c, 62),
                0, Ui.dp(c, 8), 0, 0, Ui.dp(c, 14), false, true, false);
    }

    /** Lista de linhas simples (categorias). */
    static Skeleton textRows(Context c) {
        return new Skeleton(c, LIST, Ui.dp(c, 10), Ui.dp(c, 12), 1, 0, Ui.dp(c, 44),
                0, Ui.dp(c, 4), 0, 0, Ui.dp(c, 12), false, false, false);
    }

    /** Fileira de cartões de jogo (tela Futebol): 282 dp de largura, como os cartões reais. */
    static Skeleton matchRail(Context c) {
        int w = Ui.dp(c, 282);
        int h = Ui.dp(c, 186);
        return new Skeleton(c, GRID, 0, Ui.dp(c, 12), 8, w + Ui.dp(c, 10), h,
                Ui.dp(c, 0), Ui.dp(c, 6), w, h, Ui.dp(c, 16), false, false, false);
    }

    private void update() {
        boolean want = isShown();
        if (want && !running) {
            running = true;
            handler.post(tick);
        } else if (!want && running) {
            running = false;
            handler.removeCallbacks(tick);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        update();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        update();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sweepW = Math.max(Ui.dp(getContext(), 180), w / 4f);
        gradient = new LinearGradient(0, 0, sweepW, 0,
                new int[]{0x00FFFFFF, C.SKELETON_SHINE, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        shine.setShader(gradient);
        offset = -sweepW;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mode == GRID) {
            drawCards(canvas, false);
            matrixTo();
            drawCards(canvas, true);
        } else {
            drawRows(canvas, false);
            matrixTo();
            drawRows(canvas, true);
        }
    }

    private void matrixTo() {
        if (gradient != null) {
            matrix.setTranslate(offset, 0);
            gradient.setLocalMatrix(matrix);
        }
    }

    private void block(Canvas canvas, float l, float t, float r, float b, float rad, boolean glow) {
        box.set(l, t, r, b);
        canvas.drawRoundRect(box, rad, rad, glow ? shine : base);
    }

    private void drawCards(Canvas canvas, boolean glow) {
        int rows = lines ? (getHeight() - padT) / (cellH + gapY) + 2 : 1;
        for (int row = 0; row < rows; row++) {
            float top = padT + row * (cellH + gapY);
            if (top > getHeight()) {
                break;
            }
            for (int col = 0; col < cols; col++) {
                float left = padL + col * (cellW + gapX);
                if (left + posterW > getWidth()) {
                    break;
                }
                block(canvas, left, top, left + posterW, top + posterH, radius, glow);
                if (!lines) {
                    continue;
                }
                float ty = top + posterH + Ui.dp(getContext(), 16);
                block(canvas, left, ty, left + posterW * 0.82f, ty + lineH, lineH / 2f, glow);
                block(canvas, left, ty + lineH + Ui.dp(getContext(), 8), left + posterW * 0.5f,
                        ty + lineH * 2 + Ui.dp(getContext(), 8), lineH / 2f, glow);
                if (withEpg) {
                    float ey = ty + lineH * 2 + Ui.dp(getContext(), 20);
                    block(canvas, left, ey, left + posterW * 0.65f, ey + lineH * 0.8f,
                            lineH / 2f, glow);
                }
            }
        }
    }

    private void drawRows(Canvas canvas, boolean glow) {
        float left = padL;
        float right = getWidth() - padL;
        for (float top = padT; top < getHeight(); top += cellH + gapY) {
            block(canvas, left, top, right, top + cellH, radius, glow);
            if (withLogo) {
                float cy = top + cellH / 2f;
                float s = Ui.dp(getContext(), 44);
                float lx = left + Ui.dp(getContext(), 16);
                block(canvas, lx, cy - s / 2f, lx + s, cy + s / 2f, s / 2f, glow);
                float tx = lx + s + Ui.dp(getContext(), 14);
                block(canvas, tx, cy - lineH - 2, tx + (right - tx) * 0.55f, cy - 2,
                        lineH / 2f, glow);
                block(canvas, tx, cy + 6, tx + (right - tx) * 0.3f, cy + 6 + lineH * 0.8f,
                        lineH / 2f, glow);
            } else {
                float cy = top + cellH / 2f;
                float tx = left + Ui.dp(getContext(), 14);
                block(canvas, tx, cy - lineH / 2f, tx + (right - tx) * 0.7f, cy + lineH / 2f,
                        lineH / 2f, glow);
            }
        }
    }
}
