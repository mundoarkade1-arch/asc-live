package com.asclive.tvbox;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tela provisória (só um título e um aviso). Será trocada nas próximas etapas. */
final class PlaceholderScreen extends Screen {

    private final LinearLayout box;

    PlaceholderScreen(ShellActivity act, String title, String note) {
        super(act);
        box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(C.BG);
        TextView t = new TextView(act);
        t.setText(title);
        t.setTextColor(C.LIME);
        t.setTextSize(36);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        TextView n = new TextView(act);
        n.setText(note);
        n.setTextColor(C.TEXT2);
        n.setTextSize(20);
        n.setGravity(Gravity.CENTER);
        box.addView(n);
    }

    @Override
    View view() {
        return box;
    }
}
