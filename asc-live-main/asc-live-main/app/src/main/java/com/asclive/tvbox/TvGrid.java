package com.asclive.tvbox;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.widget.GridView;

/**
 * Grade de cartões para controle remoto. A GridView controla foco, seleção e clique
 * (as linhas NÃO são focáveis). Acrescenta o "OK longo" e acende o cartão selecionado.
 */
final class TvGrid extends GridView {

    interface LongListener {
        void onLong(int position);
    }

    private LongListener longListener;
    private boolean longDone;

    TvGrid(Context c) {
        super(c);
        setSelector(new ColorDrawable(0));
        setFocusable(true);
        setClipChildren(false);
        setClipToPadding(false);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setStretchMode(NO_STRETCH);
    }

    void setLongListener(LongListener l) {
        longListener = l;
    }

    private static boolean isOk(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isOk(keyCode)) {
            if (event.getRepeatCount() == 0) {
                event.startTracking();
                longDone = false;
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (isOk(keyCode)) {
            longDone = true;
            int pos = getSelectedItemPosition();
            if (longListener != null && pos >= 0) {
                longListener.onLong(pos);
            }
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isOk(keyCode)) {
            if (longDone) {
                longDone = false;
                return true;
            }
            int pos = getSelectedItemPosition();
            View v = getSelectedView();
            if (!event.isCanceled() && pos >= 0 && v != null
                    && getAdapter() != null) {
                performItemClick(v, pos, getAdapter().getItemId(pos));
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        refreshCards();
    }

    void refreshCards() {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof ChannelCard) {
                ((ChannelCard) v).refreshSelected();
            }
        }
    }
}
