package com.asclive.tvbox;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Tela principal: barra lateral fina + a tela escolhida (Futebol, Ao vivo,
 * Favoritos, Busca, Ajustes). Só uma tela fica montada por vez, para poupar memória.
 */
public class ShellActivity extends Activity implements Sidebar.Listener {

    private FrameLayout content;
    private Sidebar sidebar;
    private Screen current;
    private int currentId = -1;
    private boolean armed = true;

    /** Texto digitado na barra Pesquisar da Início (a tela de resultados lê e apaga). */
    String pendingQuery;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        AppPrefs.applyWindow(this);
        if (Catalog.creds(this) == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.appBackground(this));
        content = new FrameLayout(this);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -1);
        clp.leftMargin = Ui.dp(this, 72);
        root.addView(content, clp);
        Profiles.P p = Profiles.current(this);
        sidebar = new Sidebar(this, this, p.name, p.avatar);
        root.addView(sidebar);
        setContentView(root);
        showScreen(Sidebar.FOOTBALL, true);
        DeviceLink.syncInBackground(this);
        UpdateUi.checkOnce(this);
        showWelcomeOnce();
    }

    /** Boas-vindas: aparece só na primeira vez, com o essencial do controle remoto. */
    private void showWelcomeOnce() {
        if (AppPrefs.welcomeDone(this)) {
            return;
        }
        AppPrefs.setWelcomeDone(this);
        content.post(() -> Dialogs.info(this, "Bem-vindo ao ASC Live",
                "Futebol: escolha o jogo e depois o canal (cada canal com a sua resolução).\n"
                        + "Ao vivo: canais por categoria. Segure OK em um canal para favoritar; "
                        + "a tecla das três listras mostra a programação.\n"
                        + "Assistindo: cima e baixo trocam de canal, OK abre a lista e os "
                        + "números digitam o canal."));
    }

    // ---------- Telas ----------

    void showScreen(int id, boolean force) {
        if (!force && id == currentId) {
            if (current != null) {
                current.focusContent();
            }
            return;
        }
        if (current != null) {
            current.onHide();
        }
        content.removeAllViews();
        Screen s;
        switch (id) {
            case Sidebar.FAVORITES:
                s = new FavoritesScreen(this);
                break;
            case Sidebar.SEARCH:
                s = new SearchScreen(this);
                break;
            case Sidebar.LIVE:
                s = new LiveScreen(this);
                break;
            case Sidebar.SETTINGS:
                s = new SettingsScreen(this);
                break;
            default:
                id = Sidebar.FOOTBALL;
                s = new FootballScreen(this);
                break;
        }
        current = s;
        currentId = id;
        sidebar.setActive(id);
        content.addView(s.view(), new FrameLayout.LayoutParams(-1, -1));
        armed = true;
        s.onShow();
    }

    /** Troca de tela a pedido do conteúdo (não da barra) sem a barra piscar. */
    private void switchFromContent(int id) {
        sidebar.holdCollapsed(true);
        showScreen(id, true);
        content.postDelayed(() -> sidebar.holdCollapsed(false), 1500);
    }

    /** A tela avisa que já tem conteúdo: se a pessoa não mexeu na barra, o foco vai para ele. */
    void focusReady(Screen s) {
        if (s != current || !armed) {
            return;
        }
        if (sidebar.hasFocus() || getCurrentFocus() == null) {
            if (s.focusContent()) {
                armed = false;
            }
        }
    }

    // ---------- Barra lateral ----------

    @Override
    public void onSelect(int id) {
        if (id == Sidebar.PROFILE) {
            Intent i = new Intent(this, ProfilesActivity.class);
            i.putExtra("pick", true);
            startActivity(i);
            finish();
            return;
        }
        if (id == Sidebar.REFRESH) {
            Catalog.invalidate();
            ImageLoader.clear();
            final android.content.Context app = getApplicationContext();
            Catalog.clearCache(app);
            FootballApi.clearCache(app);
            showScreen(currentId < 0 ? Sidebar.FOOTBALL : currentId, true);
            return;
        }
        showScreen(id, false);
    }

    @Override
    public boolean onRight() {
        return current != null && current.focusContent();
    }

    @Override
    public void onTouched() {
        armed = false;
    }

    // ---------- Teclas ----------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getRepeatCount() == 0 && current != null && !sidebar.hasFocus()
                    && current.handleMenu()) {
                return true;
            }
            armed = false;
            sidebar.focusActive();
            return true;
        }
        if ((keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                && smartKey(keyCode)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Setas que a tela não tratou. Aqui o foco fica esperto:
     * - esquerda sem vizinho na mesma linha: vai para o item do menu na mesma altura;
     * - direita sem vizinho (fim da faixa ou da grade): trava, não desce;
     * - cima sem nada em cima: não abre o menu (a grade leva ao botão de categoria).
     * Devolve true quando a tecla foi tratada.
     */
    private boolean smartKey(int keyCode) {
        View f = getCurrentFocus();
        if (f == null || current == null || sidebar.hasFocus()) {
            return false;
        }
        boolean list = f instanceof android.widget.AbsListView;
        View ref = f;
        if (list) {
            View sel = ((android.widget.AbsListView) f).getSelectedView();
            if (sel != null) {
                ref = sel;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (current.handleUp(f)) {
                return true;
            }
            if (list) {
                return true;
            }
            View n = f.focusSearch(View.FOCUS_UP);
            return !(n != null && n != f && inContent(n));
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            // No fim da tela (Ajustes, grades, Início): para, não vai para a barra de ícones.
            if (list) {
                return true;
            }
            View n = f.focusSearch(View.FOCUS_DOWN);
            return !(n != null && n != f && inContent(n));
        }
        boolean left = keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
        if (!list && hasSideNeighbor(f, left ? View.FOCUS_LEFT : View.FOCUS_RIGHT)) {
            return false;
        }
        if (left) {
            int[] loc = new int[2];
            ref.getLocationOnScreen(loc);
            armed = false;
            sidebar.focusNearest(loc[1] + ref.getHeight() / 2);
        }
        return true;
    }

    private boolean inContent(View v) {
        View p = v;
        while (p != null) {
            if (p == content) {
                return true;
            }
            android.view.ViewParent pp = p.getParent();
            p = pp instanceof View ? (View) pp : null;
        }
        return false;
    }

    /** Existe um item à esquerda/direita, na MESMA linha, dentro da tela? */
    private boolean hasSideNeighbor(View f, int dir) {
        View n = f.focusSearch(dir);
        if (n == null || n == f || !inContent(n)) {
            return false;
        }
        int[] a = new int[2];
        int[] b = new int[2];
        f.getLocationOnScreen(a);
        n.getLocationOnScreen(b);
        int cy = b[1] + n.getHeight() / 2;
        return cy >= a[1] && cy <= a[1] + f.getHeight();
    }

    @Override
    public void onBackPressed() {
        if (current != null && !sidebar.hasFocus() && current.handleBack()) {
            return;
        }
        if (currentId == Sidebar.SEARCH) {
            showScreen(Sidebar.FOOTBALL, true);
            return;
        }
        if (currentId == Sidebar.FOOTBALL) {
            // Na Início, Voltar pergunta se quer sair.
            Dialogs.confirm(this, "Sair", "Deseja sair do ASC Live?", "Sair", this::exitApp);
            return;
        }
        if (sidebar.hasFocus()) {
            showScreen(Sidebar.FOOTBALL, false);
        } else {
            armed = false;
            sidebar.focusActive();
        }
    }

    /** Fecha o app por completo: tira das tarefas recentes e encerra o processo. */
    private void exitApp() {
        finishAndRemoveTask();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> android.os.Process.killProcess(android.os.Process.myPid()), 400);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (current != null) {
            current.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (current != null) {
            current.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (current != null) {
            current.onHide();
        }
        if (isFinishing()) {
            LivePlayer.release();
        }
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            ImageLoader.clear();
        }
    }
}
