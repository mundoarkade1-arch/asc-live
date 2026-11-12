package com.asclive.tvbox;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Janelas simples, feitas para controle remoto (botões grandes, foco visível). */
final class Dialogs {

    interface OnPick {
        void pick(int index);
    }

    private Dialogs() {
    }

    private static Dialog base(Activity a, LinearLayout box) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.75f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.shape(C.SMOKE_DEEP, Ui.dp(a, 20), C.GLASS_EDGE, Ui.dp(a, 1)));
        int p = Ui.dp(a, 28);
        box.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        d.setContentView(sv, new android.view.ViewGroup.LayoutParams(Ui.dp(a, 520),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        return d;
    }

    private static void head(Activity a, LinearLayout box, String title, String msg) {
        TextView t = Ui.title(a, title, 24, C.TEXT);
        box.addView(t);
        if (msg != null && !msg.isEmpty()) {
            TextView m = Ui.text(a, msg, 16, C.TEXT2);
            m.setLineSpacing(0, 1.2f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(a, 10);
            box.addView(m, lp);
        }
    }

    private static void addButton(Activity a, LinearLayout box, Dialog d, String label,
                                  int style, Runnable action, boolean first) {
        TextView b = Ui.button(a, label, style);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 12);
        box.addView(b, lp);
        b.setOnClickListener(v -> {
            d.dismiss();
            if (action != null) {
                action.run();
            }
        });
        if (first) {
            b.requestFocus();
        }
    }

    static void show(Dialog d, Activity a) {
        if (!a.isFinishing() && !a.isDestroyed()) {
            d.show();
        }
    }

    static void info(Activity a, String title, String msg) {
        LinearLayout box = new LinearLayout(a);
        Dialog d = base(a, box);
        head(a, box, title, msg);
        addButton(a, box, d, "OK", Ui.PRIMARY, null, true);
        show(d, a);
    }

    static void confirm(Activity a, String title, String msg, String yes, Runnable onYes) {
        LinearLayout box = new LinearLayout(a);
        Dialog d = base(a, box);
        head(a, box, title, msg);
        addButton(a, box, d, "Cancelar", Ui.DARK, null, true);
        addButton(a, box, d, yes, Ui.PRIMARY, onYes, false);
        show(d, a);
    }

    /** Lista de opções (cada uma é um botão). O botão "Voltar" fecha sem escolher. */
    static void choose(Activity a, String title, String[] labels, OnPick pick) {
        LinearLayout box = new LinearLayout(a);
        Dialog d = base(a, box);
        head(a, box, title, null);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            addButton(a, box, d, labels[i], Ui.DARK, () -> pick.pick(idx), i == 0);
        }
        addButton(a, box, d, "Voltar", Ui.DARK, null, false);
        show(d, a);
    }

    /** Lista longa (categorias): ListView, que cuida do foco e rola sozinha. */
    static void list(Activity a, String title, String[] labels, OnPick pick) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.75f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.shape(C.SMOKE_DEEP, Ui.dp(a, 20), C.GLASS_EDGE, Ui.dp(a, 1)));
        int p = Ui.dp(a, 24);
        box.setPadding(p, p, p, p);
        box.addView(Ui.title(a, title, 24, C.TEXT));
        android.widget.ListView lv = new android.widget.ListView(a);
        lv.setDivider(null);
        lv.setSelector(Ui.shape(0x00000000, Ui.dp(a, 10), C.LIME, Ui.dp(a, 2)));
        lv.setDrawSelectorOnTop(true);
        lv.setVerticalScrollBarEnabled(false);
        lv.setAdapter(new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return labels.length;
            }

            @Override
            public Object getItem(int i) {
                return labels[i];
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convert, android.view.ViewGroup parent) {
                TextView t = convert instanceof TextView ? (TextView) convert
                        : Ui.text(a, "", 20, C.TEXT);
                t.setText(labels[i]);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
                return t;
            }
        });
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            d.dismiss();
            pick.pick(pos);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 420));
        lp.topMargin = Ui.dp(a, 12);
        box.addView(lv, lp);
        d.setContentView(box, new android.view.ViewGroup.LayoutParams(Ui.dp(a, 560),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        show(d, a);
    }

    /** Escolha de avatar (sem avatar marcado antes). */
    static void avatars(Activity a, String name, OnPick pick) {
        avatars(a, name, 0, pick);
    }

    /**
     * Escolha de avatar: os 12 bonequinhos numa grade de 6 por linha, para o controle remoto.
     * O foco começa no avatar atual (selected).
     */
    static void avatars(Activity a, String name, int selected, OnPick pick) {
        LinearLayout box = new LinearLayout(a);
        Dialog d = base(a, box);
        head(a, box, "Escolha um avatar", null);
        int perRow = 6;
        LinearLayout line = null;
        for (int i = 0; i < Profiles.avatarCount(); i++) {
            final int idx = i;
            if (i % perRow == 0) {
                line = new LinearLayout(a);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
                rl.topMargin = Ui.dp(a, 14);
                box.addView(line, rl);
            }
            ImageView img = new ImageView(a);
            img.setImageResource(Profiles.avatarRes(i));
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setFocusable(true);
            img.setClickable(true);
            int pad = Ui.dp(a, 5);
            img.setPadding(pad, pad, pad, pad);
            img.setBackground(Ui.focusBg(a, 0x00000000, 0x00000000, 36,
                    i == selected ? C.LIME_40 : 0x00000000,
                    i == selected ? Ui.dp(a, 2) : 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 64),
                    Ui.dp(a, 64));
            lp.setMargins(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
            line.addView(img, lp);
            img.setOnClickListener((View v) -> {
                d.dismiss();
                pick.pick(idx);
            });
            if (i == selected) {
                img.requestFocus();
            }
        }
        show(d, a);
    }
}
