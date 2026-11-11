package com.asclive.tvbox;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Telas da atualização automática (usa o Updater: HTTPS, SHA-256, só do repositório certo). */
final class UpdateUi {

    private static boolean checked;

    private UpdateUi() {
    }

    /** Uma vez por abertura do app, em silêncio: só aparece algo se houver versão nova. */
    static void checkOnce(Activity a) {
        if (checked) {
            return;
        }
        checked = true;
        check(a, false);
    }

    static void checkNow(Activity a) {
        Ui.toast(a, "Procurando atualização...");
        check(a, true);
    }

    private static void check(final Activity a, final boolean verbose) {
        Updater.checkAsync(a, new Updater.CheckListener() {
            @Override
            public void onUpdateAvailable(Updater.Info info) {
                if (!a.isFinishing() && !a.isDestroyed()) {
                    offer(a, info);
                }
            }

            @Override
            public void onNoUpdate() {
                if (verbose && !a.isFinishing() && !a.isDestroyed()) {
                    Ui.toast(a, "Você já tem a versão mais nova.");
                }
            }
        });
    }

    private static void offer(final Activity a, final Updater.Info info) {
        Dialogs.confirm(a, "Nova versão " + info.name,
                "Quer atualizar agora? O app baixa o arquivo, confere a segurança e abre o "
                        + "instalador do Android.", "Atualizar", () -> install(a, info));
    }

    private static void install(final Activity a, Updater.Info info) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.shape(C.SURFACE, Ui.dp(a, 20), C.LINE, Ui.dp(a, 1)));
        int p = Ui.dp(a, 28);
        box.setPadding(p, p, p, p);
        box.addView(Ui.title(a, "Baixando atualização", 24, C.TEXT));
        final TextView pct = Ui.text(a, "Aguarde...", 18, C.LIME);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(a, 12);
        box.addView(pct, lp);
        d.setContentView(box, new android.view.ViewGroup.LayoutParams(Ui.dp(a, 480),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setCancelable(false);
        d.show();
        Updater.downloadAndInstallAsync(a, info, new Updater.InstallListener() {
            @Override
            public void onProgress(int percent) {
                pct.setText(percent >= 0 ? percent + "%" : "Baixando...");
            }

            @Override
            public void onInstallerOpened() {
                d.dismiss();
            }

            @Override
            public void onFailed(String message) {
                d.dismiss();
                if (!a.isFinishing() && !a.isDestroyed()) {
                    Dialogs.info(a, "Não deu para atualizar", message);
                }
            }
        });
    }
}
