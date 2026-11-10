package com.asclive.tvbox;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Ajustes: perfis, lista, código do aparelho, senhas, bloqueados, atualização e mais. */
final class SettingsScreen extends Screen {

    private interface PinDone {
        void done(String pin);
    }

    private final ScrollView sv;
    private final LinearLayout col;
    private View firstRow;

    SettingsScreen(ShellActivity act) {
        super(act);
        sv = new ScrollView(act);
        sv.setBackgroundColor(C.BG);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(act, 36), Ui.dp(act, 22), Ui.dp(act, 36), Ui.dp(act, 40));
        sv.addView(col, new FrameLayout.LayoutParams(Ui.dp(act, 980), -2));
    }

    @Override
    View view() {
        return sv;
    }

    @Override
    View firstFocus() {
        return firstRow;
    }

    @Override
    void onShow() {
        build();
        act.focusReady(this);
    }

    @Override
    void onResume() {
        build();
    }

    private void build() {
        col.removeAllViews();
        firstRow = null;
        col.addView(Ui.title(act, "Ajustes", 34, C.TEXT));

        section("Perfil e lista");
        row("Perfis", Profiles.current(act).name, this::openProfiles);
        row("Trocar de lista", "Vale para todos", () -> Dialogs.confirm(act, "Trocar de lista",
                "A lista é uma só para o aparelho. Se você trocar, ela muda para TODOS os "
                        + "perfis. Os favoritos e o que cada perfil já assistiu continuam.",
                "Trocar", () -> {
                    Intent i = new Intent(act, LoginActivity.class);
                    i.putExtra("switch", true);
                    act.startActivity(i);
                }));
        codeBlock();

        section("Segurança");
        row("Controle parental", ParentalStore.isEnabled(act) ? "Ligado" : "Desligado",
                this::parentalClick);
        row("Senha para abrir o app (4 números)", AppLock.isEnabled(act) ? "Ligada" : "Desligada",
                this::appLockClick);
        row("Bloquear capturas de tela", AppPrefs.isSecure(act) ? "Ligado" : "Desligado", () -> {
            AppPrefs.setSecure(act, !AppPrefs.isSecure(act));
            AppPrefs.applyWindow(act);
            build();
        });
        row("Itens bloqueados", String.valueOf(UserLists.blockedList(act).size()),
                this::blockedClick);

        section("Aplicativo");
        row("Procurar atualização", "Versão " + versionName(), () -> UpdateUi.checkNow(act));
        row("Apagar dados de uso", "", this::clearUsage);
        row("Sobre o aplicativo e o aparelho", "", this::about);
    }

    private void section(String name) {
        TextView t = Ui.limeTag(act, name.toUpperCase());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(act, 26);
        lp.bottomMargin = Ui.dp(act, 8);
        col.addView(t, lp);
    }

    private void row(String label, String value, final Runnable action) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(Ui.dp(act, 20), Ui.dp(act, 16), Ui.dp(act, 20), Ui.dp(act, 16));
        r.setBackground(Ui.focusBg(act, C.SURFACE, C.SURFACE_FOCUS, 14, C.LINE, Ui.dp(act, 1)));
        TextView l = Ui.text(act, label, 19, C.TEXT);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!value.isEmpty()) {
            r.addView(Ui.text(act, value, 18, C.LIME));
        }
        r.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(act, 8);
        col.addView(r, lp);
        if (firstRow == null) {
            firstRow = r;
        }
    }

    private void codeBlock() {
        LinearLayout b = new LinearLayout(act);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(Ui.dp(act, 20), Ui.dp(act, 16), Ui.dp(act, 20), Ui.dp(act, 16));
        b.setBackground(Ui.shape(C.SURFACE, Ui.dp(act, 14), C.LIME_40, Ui.dp(act, 1)));
        b.addView(Ui.text(act, "Código do aparelho", 16, C.TEXT2));
        TextView code = Ui.title(act, "...", 30, C.LIME);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.topMargin = Ui.dp(act, 6);
        b.addView(code, clp);
        TextView hint = Ui.text(act, "Digite este código no ASC Admin para enviar sua lista.",
                16, C.TEXT2);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.topMargin = Ui.dp(act, 6);
        b.addView(hint, hlp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(act, 8);
        col.addView(b, lp);
        bg(() -> DeviceLink.getDisplayCode(act), new Bg.Done<String>() {
            @Override
            public void ok(String v) {
                code.setText(v);
            }

            @Override
            public void fail(Exception e) {
                code.setText("indisponível");
            }
        });
    }

    // ---------- Perfis ----------

    private void openProfiles() {
        Intent i = new Intent(act, ProfilesActivity.class);
        i.putExtra("manage", true);
        act.startActivity(i);
    }

    // ---------- Controle parental e senha do app ----------

    private void parentalClick() {
        if (!ParentalStore.isEnabled(act)) {
            askNewPin("Controle parental", pin -> {
                if (ParentalStore.enable(act, pin)) {
                    Ui.toast(act, "Controle parental ligado.");
                    Catalog.invalidate();
                    build();
                }
            });
            return;
        }
        askCurrentParental(() -> Dialogs.choose(act, "Controle parental",
                new String[]{"Desligar", "Trocar a senha"}, which -> {
                    if (which == 0) {
                        ParentalStore.disable(act);
                        Catalog.invalidate();
                        Ui.toast(act, "Controle parental desligado.");
                        build();
                    } else {
                        askNewPin("Nova senha", pin -> {
                            ParentalStore.enable(act, pin);
                            Ui.toast(act, "Senha trocada.");
                        });
                    }
                }));
    }

    private void askCurrentParental(final Runnable ok) {
        PinDialog.show(act, "Controle parental", "Digite a senha atual.",
                new PinDialog.Listener() {
                    @Override
                    public void onPin(PinDialog d, String pin) {
                        if (ParentalStore.check(act, pin)) {
                            d.dismiss();
                            ok.run();
                        } else {
                            long wait = ParentalStore.lockedSeconds(act);
                            d.reset();
                            d.setMessage(wait > 0 ? "Muitos erros. Espere " + wait + " s."
                                    : "Senha incorreta.", true);
                        }
                    }

                    @Override
                    public void onCancel() {
                    }
                });
    }

    private void appLockClick() {
        if (!AppLock.isEnabled(act)) {
            askNewPin("Senha para abrir o app", pin -> {
                if (AppLock.enable(act, pin)) {
                    Ui.toast(act, "Senha ligada.");
                    build();
                }
            });
            return;
        }
        PinDialog.show(act, "Senha do app", "Digite a senha atual para desligar.",
                new PinDialog.Listener() {
                    @Override
                    public void onPin(PinDialog d, String pin) {
                        if (AppLock.check(act, pin)) {
                            d.dismiss();
                            AppLock.disable(act);
                            Ui.toast(act, "Senha desligada.");
                            build();
                        } else {
                            long wait = AppLock.lockedSeconds(act);
                            d.reset();
                            d.setMessage(wait > 0 ? "Muitos erros. Espere " + wait + " s."
                                    : "Senha incorreta.", true);
                        }
                    }

                    @Override
                    public void onCancel() {
                    }
                });
    }

    /** Pede 4 números duas vezes (digitar e confirmar). */
    private void askNewPin(final String title, final PinDone done) {
        PinDialog.show(act, title, "Escolha uma senha de 4 números.",
                new PinDialog.Listener() {
                    @Override
                    public void onPin(PinDialog d, final String first) {
                        d.dismiss();
                        PinDialog.show(act, title, "Digite a mesma senha de novo.",
                                new PinDialog.Listener() {
                                    @Override
                                    public void onPin(PinDialog d2, String again) {
                                        if (first.equals(again)) {
                                            d2.dismiss();
                                            done.done(first);
                                        } else {
                                            d2.reset();
                                            d2.setMessage("As senhas são diferentes.", true);
                                        }
                                    }

                                    @Override
                                    public void onCancel() {
                                    }
                                });
                    }

                    @Override
                    public void onCancel() {
                    }
                });
    }

    // ---------- Bloqueados, dados e sobre ----------

    private void blockedClick() {
        final List<String[]> list = UserLists.blockedList(act);
        if (list.isEmpty()) {
            Ui.toast(act, "Nenhum item bloqueado.");
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = list.get(i)[1];
        }
        Dialogs.list(act, "Itens bloqueados (OK para desbloquear)", names, which -> {
            final String[] entry = list.get(which);
            Dialogs.confirm(act, entry[1], "Desbloquear este item?", "Desbloquear", () -> {
                UserLists.unblock(act, entry[0]);
                build();
            });
        });
    }

    private void clearUsage() {
        Dialogs.confirm(act, "Apagar dados de uso",
                "Isso apaga as imagens guardadas e os dados temporários. A lista do "
                        + "aparelho, os favoritos, as senhas e os perfis continuam.",
                "Apagar", () -> {
                    ImageLoader.clear();
                    final android.content.Context app = act.getApplicationContext();
                    new Thread(() -> SearchIndex.clear(app)).start();
                    Ui.toast(act, "Dados de uso apagados.");
                });
    }

    private String versionName() {
        try {
            PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            return pi.versionName == null ? "?" : pi.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private void about() {
        android.app.ActivityManager am = (android.app.ActivityManager)
                act.getSystemService(android.content.Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
        }
        String ram = mi.totalMem > 0 ? (mi.totalMem / (1024 * 1024)) + " MB" : "?";
        android.util.DisplayMetrics dm = act.getResources().getDisplayMetrics();
        String msg = act.getString(R.string.app_name) + " " + versionName()
                + "\nAparelho: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nMemória: " + ram
                + "\nTela: " + dm.widthPixels + "x" + dm.heightPixels;
        Dialogs.info(act, "Sobre", msg);
    }
}
