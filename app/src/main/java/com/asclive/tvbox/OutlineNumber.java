package com.asclive.tvbox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Número grande só de contorno (sem preenchimento), em limão, para o Top 10. */
final class OutlineNumber extends View {

    private final String text;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    OutlineNumber(Context c, String text, int heightPx) {
        super(c);
        this.text = text;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(c, 4));
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(C.LIME);
        paint.setTypeface(Ui.bold(c));
        paint.setTextSize(heightPx * 0.78f);
        setFocusable(false);
    }

    /** Largura necessária para o texto. */
    int textWidth() {
        return Math.round(paint.measureText(text)) + getResources().getDisplayMetrics()
                .densityDpi / 160 * 6;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawText(text, getPaddingLeft() + paint.getStrokeWidth(),
                getHeight() * 0.80f, paint);
    }
}
