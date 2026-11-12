package com.asclive.tvbox;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.util.Log;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

/**
 * Player de TV ao vivo em tela cheia. Uma Activity só: trocar de canal não abre outra tela.
 *
 * Controle remoto:
 * - Baixo / Cima: próximo canal / canal anterior (da lista de onde a pessoa veio), como no
 *   ASC TV Box. Cada canal tem a sua resolução no nome (SD, HD, FHD...).
 * - OK: lista rápida de canais. Números: digita o canal. Voltar: sai (ou fecha a lista).
 * - Tecla das três listras: programação do canal (login por servidor).
 * O PlayerView aparece imediatamente, sem uma tela intermediária opaca.
 */
public class LivePlayerActivity extends Activity implements LivePlayer.Listener {

    private static final long ZAP_DELAY_MS = 250L;
    private static final long INFO_MS = 3500L;
    private static final long NUMBER_MS = 1500L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LivePlayer lp;
    private PlayerView pv;
    private FrameLayout root;
    private ProgressBar loading;
    private TextView playbackState;

    private LinearLayout infoBar;
    private TextView infoNumber;
    private TextView infoName;
    private TextView infoNow;
    private String infoId = "";
    private TextView infoQuality;

    private TextView numberView;
    private final StringBuilder typed = new StringBuilder();

    private FrameLayout panel;
    private ListView panelList;
    private boolean panelOpen;
    private boolean failed;

    private final Runnable hideInfo = () -> infoBar.setVisibility(View.GONE);
    private final Runnable commitNumber = this::commitTyped;
    private final Runnable doPlay = this::playCurrent;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        AppPrefs.applyWindow(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (LiveSession.list.isEmpty()) {
            finish();
            return;
        }
        try {
            buildUi();
            setContentView(root);
            lp = LivePlayer.get(this);
        } catch (Exception e) {
            Log.e("ASC.LivePlayer", "activity init failed: " + e.getClass().getSimpleName());
            if (root == null) {
                root = new FrameLayout(this);
                root.setBackgroundColor(Color.BLACK);
            }
            setContentView(root);
            lp = null;
            showPlaybackError();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (lp == null) {
            return;
        }
        if (pv == null) return;
        pv.setPlayer(lp.player());
        lp.setListener(this);
        playCurrent();
    }

    @Override
    protected void onStop() {
        main.removeCallbacksAndMessages(null);
        if (lp != null) {
            lp.setListener(null);
            lp.stop();
            if (pv != null) pv.setPlayer(null);
        }
        super.onStop();
    }

    // ---------- Montagem da tela ----------

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        pv = new PlayerView(this);
        pv.setUseController(false);
        pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        pv.setShutterBackgroundColor(Color.BLACK);
        root.addView(pv, new FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        FrameLayout.LayoutParams loadingLp = new FrameLayout.LayoutParams(
                Ui.dp(this, 48), Ui.dp(this, 48), Gravity.CENTER);
        root.addView(loading, loadingLp);

        playbackState = Ui.text(this, "", 18, C.TEXT2);
        playbackState.setGravity(Gravity.CENTER);
        playbackState.setPadding(Ui.dp(this, 24), Ui.dp(this, 16),
                Ui.dp(this, 24), Ui.dp(this, 16));
        playbackState.setBackground(Ui.shape(C.SHADE, Ui.dp(this, 12), 0, 0));
        playbackState.setVisibility(View.GONE);
        playbackState.setFocusable(true);
        playbackState.setOnClickListener(v -> playCurrent());
        FrameLayout.LayoutParams stateLp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        root.addView(playbackState, stateLp);

        // Faixa de informação (embaixo): número, nome e versão.
        infoBar = new LinearLayout(this);
        infoBar.setOrientation(LinearLayout.HORIZONTAL);
        infoBar.setGravity(Gravity.CENTER_VERTICAL);
        infoBar.setBackgroundColor(C.SHADE);
        infoBar.setPadding(Ui.dp(this, 40), Ui.dp(this, 18), Ui.dp(this, 40), Ui.dp(this, 18));
        infoNumber = Ui.title(this, "", 30, C.LIME);
        infoBar.addView(infoNumber);
        LinearLayout nameBox = new LinearLayout(this);
        nameBox.setOrientation(LinearLayout.VERTICAL);
        infoName = Ui.title(this, "", 28, C.TEXT);
        nameBox.addView(infoName);
        infoNow = Ui.text(this, "", 18, C.LIME);
        infoNow.setSingleLine(true);
        infoNow.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nowLp = new LinearLayout.LayoutParams(-1, -2);
        nowLp.topMargin = Ui.dp(this, 4);
        nameBox.addView(infoNow, nowLp);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1f);
        ilp.leftMargin = Ui.dp(this, 22);
        infoBar.addView(nameBox, ilp);
        infoQuality = Ui.limeTag(this, "");
        infoBar.addView(infoQuality);
        infoBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams iblp = new FrameLayout.LayoutParams(-1, -2);
        iblp.gravity = Gravity.BOTTOM;
        root.addView(infoBar, iblp);

        // Número sendo digitado (canto de cima, à direita).
        numberView = Ui.title(this, "", 48, C.LIME);
        numberView.setBackground(Ui.shape(C.SHADE, Ui.dp(this, 14), 0, 0));
        numberView.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), Ui.dp(this, 8));
        numberView.setVisibility(View.GONE);
        FrameLayout.LayoutParams nvlp = new FrameLayout.LayoutParams(-2, -2);
        nvlp.gravity = Gravity.TOP | Gravity.END;
        nvlp.setMargins(0, Ui.dp(this, 30), Ui.dp(this, 40), 0);
        root.addView(numberView, nvlp);

        // Lista rápida de canais (OK).
        panel = new FrameLayout(this);
        panel.setBackgroundColor(0xEE101010);
        panel.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20));
        panelList = new ListView(this);
        panelList.setDivider(null);
        panelList.setVerticalScrollBarEnabled(false);
        panelList.setSelector(Ui.shape(C.SURFACE_FOCUS, Ui.dp(this, 10), C.LIME, Ui.dp(this, 2)));
        panelList.setDrawSelectorOnTop(false);
        panelList.setAdapter(new PanelAdapter());
        panelList.setOnItemClickListener((parent, view, position, id) -> {
            LiveSession.index = position;
            closePanel();
            schedulePlay(0L);
        });
        panel.addView(panelList, new FrameLayout.LayoutParams(-1, -1));
        panel.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(this, 420), -1);
        plp.gravity = Gravity.END;
        root.addView(panel, plp);
    }

    private final class PanelAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return LiveSession.list.size();
        }

        @Override
        public Object getItem(int position) {
            return LiveSession.list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            TextView t = convert instanceof TextView ? (TextView) convert
                    : Ui.text(LivePlayerActivity.this, "", 20, C.TEXT);
            ChannelGroup g = LiveSession.list.get(position);
            t.setText((position + 1) + "   " + g.nome);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setTextColor(position == LiveSession.index ? C.LIME : C.TEXT);
            t.setPadding(Ui.dp(LivePlayerActivity.this, 14), Ui.dp(LivePlayerActivity.this, 12),
                    Ui.dp(LivePlayerActivity.this, 14), Ui.dp(LivePlayerActivity.this, 12));
            return t;
        }
    }

    // ---------- Tocar e trocar de canal ----------

    /** Abre o canal atual da sessão. */
    private void playCurrent() {
        main.removeCallbacks(doPlay);
        ChannelGroup g = LiveSession.current();
        if (g == null) {
            finish();
            return;
        }
        failed = false;
        showInfo(g, LiveSession.variant >= 0 ? LiveSession.variant
                : LivePlayer.autoVariant(this, g));
        int want = LiveSession.variant;
        LiveSession.variant = -1;
        try {
            lp.play(g, want);
        } catch (Exception e) {
            Log.e("ASC.LivePlayer", "channel start failed: " + e.getClass().getSimpleName());
            onFailed(g);
        }
    }

    /** Troca de canal na tela já, e só abre o vídeo depois de um instante (zapear rápido). */
    private void schedulePlay(long delay) {
        ChannelGroup g = LiveSession.current();
        if (g != null) {
            showInfo(g, LivePlayer.autoVariant(this, g));
        }
        main.removeCallbacks(doPlay);
        main.postDelayed(doPlay, delay);
    }

    private void zap(int delta) {
        int n = LiveSession.list.size();
        if (n <= 1) {
            return;
        }
        LiveSession.index = ((LiveSession.index + delta) % n + n) % n;
        LiveSession.variant = -1;
        schedulePlay(ZAP_DELAY_MS);
    }

    // ---------- Avisos na tela ----------

    private void showInfo(ChannelGroup g, int variantIndex) {
        int number = LiveSession.index + 1;
        infoNumber.setText(String.valueOf(number));
        infoName.setText(g.nome);
        String q = "";
        if (variantIndex >= 0 && variantIndex < g.variants.size()) {
            q = g.variants.get(variantIndex).label();
            if (g.variants.size() > 1 && !q.isEmpty()) {
                q = q + "  \u25C0 \u25B6";
            }
        }
        infoQuality.setText(q);
        infoQuality.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
        infoBar.setVisibility(View.VISIBLE);
        main.removeCallbacks(hideInfo);
        main.postDelayed(hideInfo, INFO_MS);
        showNowPlaying(g);
    }

    /** Programa que passa agora no canal (só login por servidor; a lista M3U não tem guia). */
    private void showNowPlaying(ChannelGroup g) {
        final String id = g.item().id;
        infoId = id;
        infoNow.setText("");
        final CredentialStore.Credentials c = Catalog.creds(this);
        if (c == null || Catalog.isM3u(c)) {
            return;
        }
        EpgCache.get(c.server, c.user, c.pass, id, epg -> {
            if (epg == null || !id.equals(infoId)) {
                return;
            }
            String t = "Agora: " + epg.title;
            if (epg.nextTitle != null && !epg.nextTitle.isEmpty()) {
                t = t + "   \u2022   A seguir: " + epg.nextTitle;
            }
            infoNow.setText(t);
        });
    }

    /** Tecla das três listras: programação do canal. Sem guia, só mostra a faixa de informação. */
    private void openSchedule() {
        ChannelGroup g = LiveSession.current();
        if (g == null) {
            return;
        }
        CredentialStore.Credentials c = Catalog.creds(this);
        if (c == null || Catalog.isM3u(c)) {
            showInfo(g, lp.currentVariant());
            return;
        }
        EpgDialog.show(this, c.server, c.user, c.pass, g.item().id, g.nome);
    }

    // ---------- Respostas do LivePlayer ----------

    @Override
    public void onOpening(ChannelGroup g, int variantIndex) {
        showPlaybackLoading();
        if (variantIndex > 0 && g.variants.size() > 1) {
            String l = g.variants.get(variantIndex).label();
        }
    }

    @Override
    public void onPlaying(ChannelGroup g, int variantIndex) {
        failed = false;
        if (loading != null) loading.setVisibility(View.GONE);
        if (playbackState != null) playbackState.setVisibility(View.GONE);
        showInfo(g, variantIndex);
    }

    @Override
    public void onFailed(ChannelGroup g) {
        failed = true;
        showPlaybackError();
        infoName.setText(g.nome);
        infoNow.setText("Canal indisponível. Use cima ou baixo para trocar.");
        infoNow.setTextColor(C.ERROR);
        infoBar.setVisibility(View.VISIBLE);
    }

    private void showPlaybackLoading() {
        if (loading != null) loading.setVisibility(View.VISIBLE);
        if (playbackState != null) playbackState.setVisibility(View.GONE);
    }

    private void showPlaybackError() {
        if (loading != null) loading.setVisibility(View.GONE);
        if (playbackState != null) {
            playbackState.setText("Não foi possível reproduzir este canal.\nPressione OK para tentar novamente.");
            playbackState.setTextColor(C.TEXT);
            playbackState.setVisibility(View.VISIBLE);
            playbackState.requestFocus();
        }
    }

    // ---------- Lista rápida ----------

    private void openPanel() {
        panelOpen = true;
        panel.setVisibility(View.VISIBLE);
        ((BaseAdapter) panelList.getAdapter()).notifyDataSetChanged();
        panelList.setSelection(Math.max(0, LiveSession.index - 3));
        panelList.requestFocus();
        panelList.setSelection(LiveSession.index);
    }

    private void closePanel() {
        panelOpen = false;
        panel.setVisibility(View.GONE);
        root.requestFocus();
    }

    // ---------- Números ----------

    private void typeDigit(int d) {
        if (typed.length() >= 4) {
            typed.setLength(0);
        }
        typed.append(d);
        numberView.setText(typed.toString());
        numberView.setVisibility(View.VISIBLE);
        main.removeCallbacks(commitNumber);
        main.postDelayed(commitNumber, NUMBER_MS);
    }

    private void commitTyped() {
        main.removeCallbacks(commitNumber);
        if (typed.length() == 0) {
            return;
        }
        int n = Integer.parseInt(typed.toString());
        typed.setLength(0);
        numberView.setVisibility(View.GONE);
        if (n >= 1 && n <= LiveSession.list.size() && n - 1 != LiveSession.index) {
            LiveSession.index = n - 1;
            LiveSession.variant = -1;
            schedulePlay(0L);
        } else if (n < 1 || n > LiveSession.list.size()) {
            ChannelGroup g = LiveSession.current();
            if (g != null) {
                showInfo(g, lp.currentVariant());
            }
            Ui.toast(this, "Esse canal não existe.");
        }
    }

    // ---------- Teclas ----------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (panelOpen) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closePanel();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        boolean first = event.getRepeatCount() == 0;
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            if (first) {
                typeDigit(keyCode - KeyEvent.KEYCODE_0);
            }
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            if (first) {
                typeDigit(keyCode - KeyEvent.KEYCODE_NUMPAD_0);
            }
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                zap(1); // próximo canal (como no ASC TV Box)
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_UP:
                zap(-1); // canal anterior
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true; // cada canal já tem a sua resolução: nada a trocar aqui
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (first) {
                    if (typed.length() > 0) {
                        commitTyped();
                    } else {
                        openPanel();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (first) {
                    openSchedule();
                }
                return true;
            case KeyEvent.KEYCODE_INFO:
                if (first && LiveSession.current() != null) {
                    showInfo(LiveSession.current(), lp.currentVariant());
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
