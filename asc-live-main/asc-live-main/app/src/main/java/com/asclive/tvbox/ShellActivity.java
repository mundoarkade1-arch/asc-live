package com.asclive.tvbox;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

/** Casca das telas com navegaÃ§Ã£o superior; nÃ£o hÃ¡ menu lateral nem rodapÃ© de dicas. */
public class ShellActivity extends Activity {

    private FrameLayout content;
    private TopBar topBar;
    private Screen current;
    private int currentId = -1;

    /** Texto digitado na busca, mantido para compatibilidade com as telas existentes. */
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
        topBar = new TopBar(this, this::onSelect);
        root.addView(topBar, new FrameLayout.LayoutParams(-1, Ui.dp(this, Ui.TOP_BAR_DP)));
        content = new FrameLayout(this);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -1);
        clp.topMargin = Ui.dp(this, Ui.TOP_BAR_DP + 12);
        clp.leftMargin = Ui.dp(this, 20);
        clp.rightMargin = Ui.dp(this, 20);
        clp.bottomMargin = Ui.dp(this, 14);
        root.addView(content, clp);
        setContentView(root);
        showScreen(Sidebar.FOOTBALL, true);
        topBar.requestInitialFocus();
        DeviceLink.syncInBackground(this);
        UpdateUi.checkOnce(this);
        showWelcomeOnce();
    }

    private void showWelcomeOnce() {
        if (AppPrefs.welcomeDone(this)) {
            return;
        }
        AppPrefs.setWelcomeDone(this);
        content.post(() -> Dialogs.info(this, "Bem-vindo ao ASC Live",
                "Use a barra no topo para escolher InÃ­cio, Canais, Favoritos, Buscar, Atualizar ou Ajustes."));
    }

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
        content.addView(s.view(), new FrameLayout.LayoutParams(-1, -1));
        s.onShow();
    }

    void focusReady(Screen s) {
        if (s == current && topBar.hasFocus()) {
            s.focusContent();
        }
    }

    private void onSelect(int id) {
        if (id == Sidebar.REFRESH) {
            Catalog.invalidate();
            ImageLoader.clear();
            Catalog.clearCache(getApplicationContext());
            FootballApi.clearCache(getApplicationContext());
            showScreen(currentId < 0 ? Sidebar.FOOTBALL : currentId, true);
            return;
        }
        showScreen(id, false);
        if (current != null) {
            current.focusContent();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && current != null
                && event.getRepeatCount() == 0 && current.handleMenu()) {
            return true;
        }
        if (topBar.hasFocus() && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return current != null && current.focusContent();
        }
        if (!topBar.hasFocus() && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            View f = getCurrentFocus();
            View n = f == null ? null : f.focusSearch(View.FOCUS_UP);
            if (n == null || n == f || !isInContent(n)) {
                topBar.requestFocus();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isInContent(View v) {
        View p = v;
        while (p != null) {
            if (p == content) {
                return true;
            }
            android.view.ViewParent parent = p.getParent();
            p = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (current != null && current.handleBack()) {
            return;
        }
        if (currentId != Sidebar.FOOTBALL) {
            showScreen(Sidebar.FOOTBALL, false);
            return;
        }
        Dialogs.confirm(this, "Sair", "Deseja sair do ASC Live?", "Sair", this::exitApp);
    }

    private void exitApp() {
        finishAndRemoveTask();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> android.os.Process.killProcess(android.os.Process.myPid()), 400);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (topBar != null) {
            topBar.start();
        }
        if (current != null) {
            current.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (topBar != null) {
            topBar.stop();
        }
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
