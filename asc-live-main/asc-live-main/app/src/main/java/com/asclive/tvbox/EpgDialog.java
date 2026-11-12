package com.asclive.tvbox;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Janela da programação de um canal (OK em tela cheia): mostra o que passa agora e o que vem
 * a seguir, com os horários, num painel de vidro embaixo da tela. O canal continua passando
 * por trás. O azul do app só aparece como destaque (etiqueta e barra), nunca como letra.
 *
 * A programação só é pedida ao servidor quando a janela abre, e só no login por servidor
 * (a lista M3U não tem guia: nesse caso server vem null e a janela avisa que não há).
 * Fecha com OK no botão "Fechar", Voltar ou a tecla das três listras. Não registra senhas
 * nem endereços em log.
 */
final class EpgDialog {

    /** Cinza claro para textos de apoio sobre o vidro (o cinza comum some em cima do vídeo). */
    private static final int SOFT = 0xFFE6E6E6;
    /** Vidro: escuro e transparente, deixa o canal aparecer, mas segura a leitura. */
    private static final int GLASS_FILL = 0x99101010;
    private static final int GLASS_BORDER = 0x55FFFFFF;
    private static final int SHADOW = 0xFF000000;

    private EpgDialog() {
    }

    /** Texto com sombra leve, para ler bem mesmo em cima de imagem clara. */
    private static TextView label(Activity act, CharSequence t, float sp, int color) {
        TextView v = Ui.text(act, t, sp, color);
        v.setShadowLayer(6f, 0f, 1f, SHADOW);
        return v;
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
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        FrameLayout outer = new FrameLayout(act);
        outer.setPadding(Ui.dp(act, 48), 0, Ui.dp(act, 48), Ui.dp(act, 28));

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.shape(GLASS_FILL, Ui.dp(act, 22), GLASS_BORDER, Ui.dp(act, 1)));
        int pad = Ui.dp(act, 28);
        box.setPadding(pad, pad - Ui.dp(act, 4), pad, pad - Ui.dp(act, 6));

        TextView title = Ui.title(act, channelName == null ? "" : channelName, 32, C.TEXT);
        title.setShadowLayer(6f, 0f, 1f, SHADOW);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(title);

        final TextView status = label(act, "Carregando...", 24, SOFT);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = Ui.dp(act, 12);
        box.addView(status, slp);

        final LinearLayout rows = new LinearLayout(act);
        rows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.dp(act, 6);
        box.addView(rows, rlp);

        TextView close = Ui.button(act, "Fechar", Ui.DARK);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.topMargin = Ui.dp(act, 16);
        clp.gravity = Gravity.END;
        box.addView(close, clp);
        close.setOnClickListener(v -> d.dismiss());

        outer.addView(box, new FrameLayout.LayoutParams(-1, -2));
        d.setContentView(outer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
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

        if (server == null || streamId == null) {
            fill(act, status, rows, new java.util.ArrayList<XtreamApi.Program>());
            return;
        }

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
            status.setTextColor(SOFT);
            status.setText("Programação indisponível para este canal.");
            return;
        }
        status.setVisibility(View.GONE);
        long now = System.currentTimeMillis();
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        XtreamApi.Program current = null;
        for (XtreamApi.Program p : programs) {
            if (p.start <= now && now < p.end) {
                current = p;
                break;
            }
        }
        if (current != null) {
            addTag(act, rows, "AGORA", true);
            addRow(act, rows, fmt.format(new Date(current.start)) + " - "
                    + fmt.format(new Date(current.end)), current.title, true);
            ProgressBar progress = new ProgressBar(act, null,
                    android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            long total = Math.max(1L, current.end - current.start);
            int value = (int) Math.max(0L, Math.min(1000L,
                    (now - current.start) * 1000L / total));
            progress.setProgress(value);
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(C.LIME));
            progress.setProgressBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0x44FFFFFF));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, Ui.dp(act, 6));
            plp.topMargin = Ui.dp(act, 4);
            rows.addView(progress, plp);
        }
        boolean tagged = false;
        int added = 0;
        for (XtreamApi.Program p : programs) {
            if (p == current || p.end <= now) {
                continue;
            }
            if (added++ >= 3) {
                break;
            }
            if (!tagged) {
                addTag(act, rows, "A SEGUIR", false);
                tagged = true;
            }
            addRow(act, rows, fmt.format(new Date(p.start)) + " - " + fmt.format(new Date(p.end)),
                    p.title, false);
        }
    }

    /** Etiqueta "AGORA" (destaque azul com letra escura) ou "A SEGUIR" (vidro com letra branca). */
    private static void addTag(Activity act, LinearLayout rows, String text, boolean highlight) {
        TextView tag;
        if (highlight) {
            tag = Ui.limeTag(act, text);
        } else {
            tag = Ui.title(act, text, 13, C.TEXT);
            tag.setBackground(Ui.shape(0x44FFFFFF, Ui.dp(act, 6), 0, 0));
            tag.setPadding(Ui.dp(act, 10), Ui.dp(act, 4), Ui.dp(act, 10), Ui.dp(act, 4));
        }
        tag.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(act, 10);
        lp.bottomMargin = Ui.dp(act, 2);
        rows.addView(tag, lp);
    }

    private static void addRow(Activity act, LinearLayout rows, String time, String name,
                               boolean current) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(act, 5), 0, Ui.dp(act, 5));

        TextView t = label(act, time, 24, current ? C.TEXT : SOFT);
        row.addView(t, new LinearLayout.LayoutParams(Ui.dp(act, 190), -2));

        TextView n = label(act, name, current ? 30 : 26, C.TEXT);
        n.setMaxLines(2);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (current) {
            n.setTypeface(Ui.bold(act));
        }
        row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));

        rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
}
