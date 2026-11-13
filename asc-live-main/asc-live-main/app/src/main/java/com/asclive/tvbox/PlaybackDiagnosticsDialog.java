package com.asclive.tvbox;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Janela pequena para consultar e exportar a última tentativa de reprodução. */
final class PlaybackDiagnosticsDialog {

    private PlaybackDiagnosticsDialog() {
    }

    static void show(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.75f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(activity, 28), Ui.dp(activity, 24),
                Ui.dp(activity, 28), Ui.dp(activity, 24));
        box.setBackground(Ui.shape(C.SMOKE_DEEP, Ui.dp(activity, 16),
                C.GLASS_EDGE, Ui.dp(activity, 1)));
        box.addView(Ui.title(activity, "Diagnóstico de reprodução", 24, C.TEXT));

        TextView report = Ui.text(activity, "Carregando diagnóstico...", 15, C.TEXT2);
        report.setTextIsSelectable(false);
        report.setGravity(Gravity.START);
        report.setLineSpacing(0, 1.15f);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(report);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, Ui.dp(activity, 420));
        scrollLp.topMargin = Ui.dp(activity, 14);
        box.addView(scroll, scrollLp);

        TextView export = Ui.button(activity, "Exportar relatório", Ui.PRIMARY);
        export.setOnClickListener(v -> PlaybackDiagnostics.exportLatest(activity));
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, -2);
        buttonLp.topMargin = Ui.dp(activity, 14);
        box.addView(export, buttonLp);

        TextView close = Ui.button(activity, "Fechar", Ui.DARK);
        close.setOnClickListener(v -> dialog.dismiss());
        box.addView(close, new LinearLayout.LayoutParams(-1, -2));

        dialog.setContentView(box, new android.view.ViewGroup.LayoutParams(
                Ui.dp(activity, 760), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        export.requestFocus();
        Dialogs.show(dialog, activity);
        Bg.run(activity, () -> PlaybackDiagnostics.latest(activity), new Bg.Done<String>() {
            @Override
            public void ok(String value) {
                report.setText(value);
            }

            @Override
            public void fail(Exception error) {
                report.setText("Não foi possível ler o diagnóstico.");
            }
        });
    }
}
