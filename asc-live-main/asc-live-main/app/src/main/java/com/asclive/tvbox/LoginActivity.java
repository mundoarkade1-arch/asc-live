package com.asclive.tvbox;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Entrada na lista: servidor + usuário + senha (Xtream), link M3U, ou código do aparelho
 * (a lista é enviada de longe pelo ASC Admin). A lista é testada ANTES de ser salva.
 */
public class LoginActivity extends Activity {

    private static final long POLL_MS = 5000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean switching;
    private boolean busy;
    private boolean visible;
    private EditText etServer;
    private EditText etUser;
    private EditText etPass;
    private EditText etM3u;
    private LinearLayout panelServer;
    private LinearLayout panelM3u;
    private LinearLayout panelCode;
    private TextView tabServer;
    private TextView tabM3u;
    private TextView tabCode;
    private TextView status;
    private TextView codeTv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        AppPrefs.applyWindow(this);
        switching = getIntent().getBooleanExtra("switch", false);
        build();
        loadCode();
        select(0);
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(C.BG);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(this, 40), Ui.dp(this, 30), Ui.dp(this, 40), Ui.dp(this, 30));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        setContentView(sv);

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.addView(Ui.title(this, "ASC", 40, C.TEXT));
        LinearLayout.LayoutParams tg = new LinearLayout.LayoutParams(-2, -2);
        tg.leftMargin = Ui.dp(this, 12);
        TextView cine = Ui.title(this, "LIVE", 30, C.BG);
        cine.setBackground(Ui.shape(C.LIME, Ui.dp(this, 8), 0, 0));
        cine.setPadding(Ui.dp(this, 12), Ui.dp(this, 2), Ui.dp(this, 12), Ui.dp(this, 2));
        brand.addView(cine, tg);
        col.addView(brand);

        TextView t = Ui.title(this, "Entrar na sua lista", 34,
                C.TEXT);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = Ui.dp(this, 18);
        col.addView(t, tl);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabServer = Ui.button(this, "Servidor", Ui.DARK);
        tabM3u = Ui.button(this, "Link M3U", Ui.DARK);
        tabCode = Ui.button(this, "Código do aparelho", Ui.DARK);
        tabs.addView(tabServer, tabLp(0));
        tabs.addView(tabM3u, tabLp(Ui.dp(this, 10)));
        tabs.addView(tabCode, tabLp(Ui.dp(this, 10)));
        LinearLayout.LayoutParams tbl = new LinearLayout.LayoutParams(-2, -2);
        tbl.topMargin = Ui.dp(this, 22);
        col.addView(tabs, tbl);
        tabServer.setOnClickListener(v -> select(0));
        tabM3u.setOnClickListener(v -> select(1));
        tabCode.setOnClickListener(v -> select(2));

        int w = Ui.dp(this, 760);

        panelServer = panel();
        etServer = field("Servidor (ex.: http://meuservidor.com:8080)",
                InputType.TYPE_TEXT_VARIATION_URI);
        etUser = field("Usuário", InputType.TYPE_CLASS_TEXT);
        etPass = field("Senha", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        etPass.setImeOptions(EditorInfo.IME_ACTION_DONE);
        panelServer.addView(etServer, fieldLp());
        panelServer.addView(etUser, fieldLp());
        panelServer.addView(etPass, fieldLp());
        TextView go1 = Ui.button(this, "Entrar", Ui.PRIMARY);
        go1.setOnClickListener(v -> loginXtream());
        panelServer.addView(go1, fieldLp());
        col.addView(panelServer, new LinearLayout.LayoutParams(w, -2));

        panelM3u = panel();
        etM3u = field("Link da lista (http:// ou https://)", InputType.TYPE_TEXT_VARIATION_URI);
        etM3u.setImeOptions(EditorInfo.IME_ACTION_DONE);
        panelM3u.addView(etM3u, fieldLp());
        TextView go2 = Ui.button(this, "Entrar", Ui.PRIMARY);
        go2.setOnClickListener(v -> loginM3u());
        panelM3u.addView(go2, fieldLp());
        col.addView(panelM3u, new LinearLayout.LayoutParams(w, -2));

        panelCode = panel();
        TextView exp = Ui.text(this, "Digite este código no ASC Admin para enviar sua lista.",
                20, C.TEXT2);
        panelCode.addView(exp);
        codeTv = Ui.title(this, "...", 46, C.TEXT);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.topMargin = Ui.dp(this, 22);
        panelCode.addView(codeTv, cl);
        TextView wait = Ui.text(this, "Esta tela confere sozinha. Quando a lista chegar, "
                + "o app entra.", 17, C.TEXT2);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.topMargin = Ui.dp(this, 16);
        panelCode.addView(wait, wl);
        col.addView(panelCode, new LinearLayout.LayoutParams(w, -2));

        status = Ui.text(this, "", 18, C.TEXT2);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(w, -2);
        sl.topMargin = Ui.dp(this, 14);
        col.addView(status, sl);
    }

    private LinearLayout.LayoutParams tabLp(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = left;
        return lp;
    }

    private LinearLayout panel() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, Ui.dp(this, 18), 0, 0);
        return l;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 12);
        return lp;
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(C.TEXT3);
        e.setTextColor(C.TEXT);
        e.setTextSize(22);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        e.setPadding(Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 16));
        e.setBackground(Ui.focusBg(this, C.SURFACE, C.SURFACE_FOCUS, 14, C.LINE, Ui.dp(this, 1)));
        e.setFocusable(true);
        return e;
    }

    private void select(int i) {
        panelServer.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        panelM3u.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        panelCode.setVisibility(i == 2 ? View.VISIBLE : View.GONE);
        style(tabServer, i == 0);
        style(tabM3u, i == 1);
        style(tabCode, i == 2);
        status.setText("");
        if (i == 0) {
            etServer.requestFocus();
        } else if (i == 1) {
            etM3u.requestFocus();
        } else {
            tabCode.requestFocus();
        }
    }

    private void style(TextView tab, boolean on) {
        tab.setBackground(Ui.focusBg(this, on ? C.LIME : C.SURFACE,
                on ? C.LIME : C.SURFACE_FOCUS, 14, C.LINE, Ui.dp(this, 1)));
        tab.setTextColor(on ? C.BG : C.TEXT);
    }

    // ---------- Entrar ----------

    private void setStatus(String text, boolean error) {
        status.setTextColor(error ? C.ERROR : C.TEXT);
        status.setText(text);
    }

    private static String message(LoginChecker.Result r) {
        switch (r) {
            case EMPTY:
                return "Preencha todos os campos.";
            case BAD_SERVER:
                return "O endereço do servidor não parece certo.";
            case BAD_URL:
                return "O link não parece certo. Ele deve começar com http:// ou https://";
            case NETWORK:
                return "Não foi possível conectar. Confira a internet e o endereço.";
            case AUTH:
                return "Usuário ou senha incorretos.";
            case EXPIRED:
                return "Esta conta está vencida.";
            case NOT_M3U:
                return "Este link não parece ser uma lista M3U.";
            default:
                return "Algo deu errado. Tente de novo.";
        }
    }

    private void loginXtream() {
        if (busy) {
            return;
        }
        final String server = etServer.getText().toString().trim();
        final String user = etUser.getText().toString().trim();
        final String pass = etPass.getText().toString().trim();
        busy = true;
        setStatus("Testando...", false);
        Bg.run(this, () -> LoginChecker.checkXtream(server, user, pass),
                new Bg.Done<LoginChecker.Result>() {
                    @Override
                    public void ok(LoginChecker.Result r) {
                        busy = false;
                        if (r == LoginChecker.Result.OK) {
                            String origin = LoginChecker.normalizeServer(server);
                            CredentialStore.save(LoginActivity.this, new CredentialStore
                                    .Credentials(CredentialStore.MODE_XTREAM,
                                    origin == null ? server : origin, user, pass, ""));
                            enter();
                        } else {
                            setStatus(message(r), true);
                        }
                    }

                    @Override
                    public void fail(Exception e) {
                        busy = false;
                        setStatus("Algo deu errado. Tente de novo.", true);
                    }
                });
    }

    private void loginM3u() {
        if (busy) {
            return;
        }
        final String url = etM3u.getText().toString().trim();
        busy = true;
        setStatus("Testando...", false);
        Bg.run(this, () -> LoginChecker.checkM3u(url), new Bg.Done<LoginChecker.Result>() {
            @Override
            public void ok(LoginChecker.Result r) {
                busy = false;
                if (r == LoginChecker.Result.OK) {
                    CredentialStore.save(LoginActivity.this, new CredentialStore.Credentials(
                            CredentialStore.MODE_M3U, "", "", "", url));
                    enter();
                } else {
                    setStatus(message(r), true);
                }
            }

            @Override
            public void fail(Exception e) {
                busy = false;
                setStatus("Algo deu errado. Tente de novo.", true);
            }
        });
    }

    /**
     * A lista foi salva (vale para o aparelho todo). Primeira vez: criar o perfil; depois:
     * escolher o perfil. Se foi só uma troca de lista (Ajustes), volta direto para o app.
     */
    private void enter() {
        Catalog.invalidate();
        Intent i = new Intent(this, ShellActivity.class);
        if (switching) i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }

    // ---------- Código do aparelho ----------

    private void loadCode() {
        Bg.run(this, () -> DeviceLink.getDisplayCode(this), new Bg.Done<String>() {
            @Override
            public void ok(String v) {
                codeTv.setText(v);
            }

            @Override
            public void fail(Exception e) {
                codeTv.setText("indisponível");
            }
        });
    }

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            if (!visible) {
                return;
            }
            Bg.run(LoginActivity.this, () -> {
                DeviceLink.Reply r = DeviceLink.poll(LoginActivity.this);
                if (r.state == DeviceLink.State.OFFLINE) {
                    return "offline";
                }
                if (DeviceLink.isNewer(LoginActivity.this, r)) {
                    LoginChecker.Result v = DeviceLink.verify(r);
                    if (v == LoginChecker.Result.OK) {
                        DeviceLink.apply(LoginActivity.this, r);
                        return "ok";
                    }
                    return "falhou:" + message(v);
                }
                return r.state == DeviceLink.State.WAITING ? "waiting" : "";
            }, new Bg.Done<String>() {
                @Override
                public void ok(String res) {
                    if ("ok".equals(res)) {
                        enter();
                    } else if ("offline".equals(res)) {
                        setStatus("Sem conexão com o painel. Confira a internet.", true);
                    } else if (res.startsWith("falhou:")) {
                        setStatus("Chegou uma lista do painel, mas o teste falhou. "
                                + res.substring(7), true);
                    }
                }

                @Override
                public void fail(Exception e) {
                    setStatus("Erro ao falar com o painel.", true);
                }
            });
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        handler.removeCallbacks(poller);
        handler.postDelayed(poller, 1500L);
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacks(poller);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (switching) {
            finish();
        } else {
            finishAffinity();
        }
    }
}
