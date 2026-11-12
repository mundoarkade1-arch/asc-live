package com.asclive.tvbox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * Bolinha de carregamento que gira. Desenhada à mão e girada por um relógio próprio, para
 * girar mesmo em aparelho com as animações do sistema desligadas (comum em TV Box).
 * Só gira enquanto está visível na tela, então não gasta nada quando escondida.
 */
final class LoadingSpinner extends View {

    private static final long TICK_MS = 40L;
    private static final float STEP_DEG = 24f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float angle;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            angle = (angle + STEP_DEG) % 360f;
            invalidate();
            handler.postDelayed(this, TICK_MS);
        }
    };

    LoadingSpinner(Context c) {
        super(c);
        float w = Ui.dp(c, 5);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(0x33FFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(C.LIME);
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
    protected void onDraw(Canvas canvas) {
        float half = arc.getStrokeWidth() / 2f;
        box.set(half, half, getWidth() - half, getHeight() - half);
        canvas.drawOval(box, track);
        canvas.drawArc(box, angle, 100f, false, arc);
    }
}
