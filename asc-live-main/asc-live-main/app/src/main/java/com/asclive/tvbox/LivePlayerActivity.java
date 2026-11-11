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
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

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
 * Mostra o logo e "Abrindo..." na hora, antes de o vídeo chegar.
 */
public class LivePlayerActivity extends Activity implements LivePlayer.Listener {

    private static final long ZAP_DELAY_MS = 250L;
    private static final long INFO_MS = 3500L;
    private static final long NUMBER_MS = 1500L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LivePlayer lp;
    private PlayerView pv;
    private FrameLayout root;

    private LinearLayout cover;
    private ImageView coverLogo;
    private TextView coverName;
    private TextView coverNote;

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
        buildUi();
        setContentView(root);
        lp = LivePlayer.get(this);
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
            pv.setPlayer(null);
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

        // Capa: logo + "Abrindo..." (também mostra o erro).
        cover = new LinearLayout(this);
        cover.setOrientation(LinearLayout.VERTICAL);
        cover.setGravity(Gravity.CENTER);
        cover.setBackgroundColor(C.BG);
        coverLogo = new ImageView(this);
        coverLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cover.addView(coverLogo, new LinearLayout.LayoutParams(Ui.dp(this, 200),
                Ui.dp(this, 120)));
        coverName = Ui.title(this, "", 32, C.TEXT);
        coverName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = Ui.dp(this, 14);
        cover.addView(coverName, nlp);
        coverNote = Ui.text(this, "Abrindo...", 20, C.LIME);
        coverNote.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.topMargin = Ui.dp(this, 10);
        cover.addView(coverNote, clp);
        root.addView(cover, new FrameLayout.LayoutParams(-1, -1));

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
        showCover(g, "Abrindo...");
        showInfo(g, LiveSession.variant >= 0 ? LiveSession.variant
                : LivePlayer.autoVariant(this, g));
        int want = LiveSession.variant;
        LiveSession.variant = -1;
        lp.play(g, want);
    }

    /** Troca de canal na tela já, e só abre o vídeo depois de um instante (zapear rápido). */
    private void schedulePlay(long delay) {
        ChannelGroup g = LiveSession.current();
        if (g != null) {
            showCover(g, "Abrindo...");
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

    private void showCover(ChannelGroup g, String note) {
        cover.setVisibility(View.VISIBLE);
        coverName.setText(g.nome);
        coverNote.setText(note);
        coverNote.setTextColor(C.LIME);
        ImageLoader.load(coverLogo, g.logo, 480);
    }

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
        String note = "Abrindo...";
        if (variantIndex > 0 && g.variants.size() > 1) {
            String l = g.variants.get(variantIndex).label();
            note = l.isEmpty() ? "Tentando outra versão..." : "Tentando versão " + l + "...";
        }
        coverNote.setText(note);
        coverNote.setTextColor(C.LIME);
    }

    @Override
    public void onPlaying(ChannelGroup g, int variantIndex) {
        failed = false;
        cover.setVisibility(View.GONE);
        showInfo(g, variantIndex);
    }

    @Override
    public void onFailed(ChannelGroup g) {
        failed = true;
        cover.setVisibility(View.VISIBLE);
        coverNote.setText("Este canal não abriu. Use cima ou baixo para trocar de canal.");
        coverNote.setTextColor(C.ERROR);
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
