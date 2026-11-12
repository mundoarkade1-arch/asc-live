package com.asclive.tvbox;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Janela da programação de um canal (tecla das três listras), como no ASC TV Box:
 * mostra o que passa agora (em limão) e os próximos programas, com os horários.
 *
 * A programação só é pedida ao servidor quando a janela abre, e só no login por servidor
 * (a lista M3U não tem guia). Fecha com OK no botão "Fechar", Voltar ou a tecla das três
 * listras de novo. Não registra senhas nem endereços em log.
 */
final class EpgDialog {

    private EpgDialog() {
    }

    /** Abre a janela e pede a programação do canal. Chamar na thread principal. */
    static void show(final Activity act, final String server, final String user,
                     final String pass, final String streamId, String channelName) {
        if (act == null || act.isFinishing() || act.isDestroyed()) {
            return;
        }
        final Dialog d = new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.75f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.shape(C.SMOKE_DEEP, Ui.dp(act, 20), C.GLASS_EDGE, Ui.dp(act, 1)));
        int pad = Ui.dp(act, 26);
        box.setPadding(pad, pad, pad, pad);

        box.addView(Ui.text(act, "Programação", 16, C.TEXT2));

        TextView title = Ui.title(act, channelName == null ? "" : channelName, 26, C.TEXT);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(title);

        final TextView status = Ui.text(act, "Carregando...", 19, C.TEXT2);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = Ui.dp(act, 14);
        box.addView(status, slp);

        final LinearLayout rows = new LinearLayout(act);
        rows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.dp(act, 8);
        box.addView(rows, rlp);

        TextView close = Ui.button(act, "Fechar", Ui.DARK);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Ui.dp(act, 18);
        box.addView(close, clp);
        close.setOnClickListener(v -> d.dismiss());

        d.setContentView(box, new ViewGroup.LayoutParams(Ui.dp(act, 640),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // A tecla das três listras, APERTADA de novo, fecha a janela. Só conta o aperto
        // (ACTION_DOWN, primeira vez): a "tecla solta" do aperto que abriu a janela não fecha.
        d.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    d.dismiss();
                }
                return true;
            }
            return false;
        });

        Dialogs.show(d, act);
        close.requestFocus();

        Bg.run(act, () -> XtreamApi.schedule(server, user, pass, streamId),
                new Bg.Done<List<XtreamApi.Program>>() {
                    @Override
                    public void ok(List<XtreamApi.Program> v) {
                        if (d.isShowing()) {
                            fill(act, status, rows, v);
                        }
                    }

                    @Override
                    public void fail(Exception e) {
                        if (d.isShowing()) {
                            fill(act, status, rows, null);
                        }
                    }
                });
    }

    private static void fill(Activity act, TextView status, LinearLayout rows,
                             List<XtreamApi.Program> programs) {
        if (programs == null) {
            status.setTextColor(C.ERROR);
            status.setText("Não foi possível carregar a programação.");
            return;
        }
        if (programs.isEmpty()) {
            status.setTextColor(C.TEXT2);
            status.setText("Programação indisponível para este canal.");
            return;
        }
        status.setVisibility(View.GONE);
        long now = System.currentTimeMillis();
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (XtreamApi.Program p : programs) {
            boolean current = p.start <= now && now < p.end;
            addRow(act, rows, fmt.format(new Date(p.start)) + " - " + fmt.format(new Date(p.end)),
                    p.title, current);
        }
    }

    private static void addRow(Activity act, LinearLayout rows, String time, String name,
                               boolean current) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6));

        TextView t = Ui.text(act, time, 19, current ? C.LIME : C.TEXT2);
        row.addView(t, new LinearLayout.LayoutParams(Ui.dp(act, 150), -2));

        TextView n = Ui.text(act, name, 21, current ? C.LIME : C.TEXT);
        n.setMaxLines(2);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (current) {
            n.setTypeface(Ui.bold(act));
        }
        row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));

        rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
}
