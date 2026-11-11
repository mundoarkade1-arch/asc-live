package com.asclive.tvbox;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Porta de entrada (sem tela própria): pede a senha do app se houver, depois decide entre
 * entrar na lista, criar o primeiro perfil ou escolher o perfil.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        AppPrefs.applyWindow(this);
        getWindow().getDecorView().setBackgroundColor(C.BG);
        if (AppLock.isEnabled(this)) {
            askLock();
        } else {
            route();
        }
    }

    private void askLock() {
        PinDialog.show(this, "Senha do app", "Digite a senha para abrir.",
                new PinDialog.Listener() {
                    @Override
                    public void onPin(PinDialog d, String pin) {
                        if (AppLock.check(MainActivity.this, pin)) {
                            d.dismiss();
                            route();
                        } else {
                            long wait = AppLock.lockedSeconds(MainActivity.this);
                            d.reset();
                            d.setMessage(wait > 0 ? "Muitos erros. Espere " + wait + " s."
                                    : "Senha incorreta.", true);
                        }
                    }

                    @Override
                    public void onCancel() {
                        finish();
                    }
                });
    }

    /**
     * Sem lista (primeira vez): pede a lista. Com lista: primeira vez do aparelho = criar o
     * primeiro perfil; depois disso, SEMPRE a tela "Quem está assistindo?" (mesmo com um
     * perfil só).
     */
    private void route() {
        if (Catalog.creds(this) == null) {
            startActivity(new Intent(this, LoginActivity.class));
        } else if (Profiles.needsWelcome(this)) {
            startActivity(new Intent(this, ProfilesActivity.class).putExtra("welcome", true));
        } else {
            startActivity(new Intent(this, ProfilesActivity.class).putExtra("pick", true));
        }
        finish();
    }
}
