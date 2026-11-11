package com.asclive.tvbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReader;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * O player de TV ao vivo. Existe UM ExoPlayer só (criado uma vez e reaproveitado): trocar de
 * canal é setMediaItem + prepare, nunca recriar. Só usar na thread principal.
 *
 * Estabilidade: se o canal der erro ou travar por mais de 8 s, tenta a próxima versão do mesmo
 * canal (FHD, HD, SD...). Se todas falharem, reconecta sozinho 3 vezes (espera 1 s, 2 s, 4 s)
 * antes de avisar o erro. Lembra a última versão que funcionou de cada canal.
 *
 * Nunca registra em log: endereço do canal, usuário, senha, servidor.
 */
final class LivePlayer {

    /** Quem acompanha o que o player está fazendo (a tela). Sempre na thread principal. */
    interface Listener {
        /** Começou a abrir esta versão do canal. */
        void onOpening(ChannelGroup g, int variantIndex);

        /** O vídeo está tocando. */
        void onPlaying(ChannelGroup g, int variantIndex);

        /** Todas as versões e todas as tentativas falharam. */
        void onFailed(ChannelGroup g);
    }

    static final long STALL_MS = 8000L;
    private static final int MAX_RECONNECTS = 3;
    private static final String PREFS_LAST = "asc_live_last";
    private static final String USER_AGENT = "ASC-Live/1.0 (Android)";

    private static LivePlayer instance;
    private static final ExecutorService WARM = Executors.newSingleThreadExecutor();
    private static String lastWarmHost = "";
    private static long lastWarmAt = 0L;

    private final Context app;
    private final ExoPlayer player;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;

    private ChannelGroup group;
    private int[] order = new int[0];
    private int pos;
    private int round;
    private int token;
    private boolean active;
    private boolean attemptOk;
    private boolean stallArmed;

    private final Runnable stall = new Runnable() {
        @Override
        public void run() {
            stallArmed = false;
            if (active) {
                onFail();
            }
        }
    };

    static synchronized LivePlayer get(Context c) {
        if (instance == null) {
            instance = new LivePlayer(c.getApplicationContext());
        }
        return instance;
    }

    /** Solta o player de vez (o app vai fechar). */
    static synchronized void release() {
        if (instance != null) {
            instance.stop();
            instance.player.release();
            instance = null;
        }
    }

    private LivePlayer(Context app) {
        this.app = app;
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setConnectTimeoutMs(4000)
                .setReadTimeoutMs(6000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true);
        DefaultExtractorsFactory extractors = new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReader.FLAG_ALLOW_NON_IDR_KEYFRAMES);
        // Buffer de ao vivo: abre rápido (0,5 s) e guarda pouco (1,5 s a 8 s).
        DefaultLoadControl load = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(1500, 8000, 500, 1000)
                .build();
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(app)
                .setEnableDecoderFallback(true);
        player = new ExoPlayer.Builder(app, renderers)
                .setLoadControl(load)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http, extractors))
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        // Limite de 1080p (o TX9 e aparelhos parecidos não aguentam mais que isso).
        player.setTrackSelectionParameters(player.getTrackSelectionParameters()
                .buildUpon().setMaxVideoSize(1920, 1080).build());
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                handleState(state);
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (active) {
                    onFail();
                }
            }
        });
    }

    ExoPlayer player() {
        return player;
    }

    void setListener(Listener l) {
        listener = l;
    }

    ChannelGroup current() {
        return group;
    }

    /** Posição (em variants) da versão que está abrindo ou tocando. */
    int currentVariant() {
        return group != null && pos < order.length ? order[pos] : 0;
    }

    // ---------- Tocar ----------

    /**
     * Abre um canal. versao = posição em g.variants; -1 escolhe sozinho (a última que funcionou,
     * senão a preferida nos Ajustes).
     */
    void play(ChannelGroup g, int versao) {
        token++;
        main.removeCallbacks(stall);
        stallArmed = false;
        group = g;
        int start = versao >= 0 && versao < g.variants.size() ? versao : autoVariant(app, g);
        order = buildOrder(g, start);
        pos = 0;
        round = 0;
        active = true;
        startAttempt();
    }

    /** Para de tocar e solta a conexão (servidores IPTV limitam telas ao mesmo tempo). */
    void stop() {
        token++;
        active = false;
        main.removeCallbacks(stall);
        stallArmed = false;
        player.stop();
        player.clearMediaItems();
    }

    private static int[] buildOrder(ChannelGroup g, int start) {
        int n = g.variants.size();
        int[] o = new int[n];
        o[0] = start;
        int k = 1;
        for (int i = 0; i < n; i++) {
            if (i != start) {
                o[k++] = i;
            }
        }
        return o;
    }

    private void startAttempt() {
        if (group == null) {
            return;
        }
        final ChannelGroup g = group;
        final int vi = order[pos];
        String url = Catalog.urlOf(app, g.variants.get(vi).item);
        attemptOk = false;
        if (url == null) {
            onFail();
            return;
        }
        if (listener != null) {
            listener.onOpening(g, vi);
        }
        MediaItem.Builder b = new MediaItem.Builder().setUri(url);
        if (pathOf(url).endsWith(".m3u8")) {
            b.setMimeType(MimeTypes.APPLICATION_M3U8); // não precisa adivinhar o formato
        }
        player.setMediaItem(b.build());
        player.prepare();
        player.setPlayWhenReady(true);
        main.removeCallbacks(stall);
        stallArmed = true;
        main.postDelayed(stall, STALL_MS);
    }

    private void handleState(int state) {
        if (!active || group == null) {
            return;
        }
        if (state == Player.STATE_READY) {
            main.removeCallbacks(stall);
            stallArmed = false;
            if (!attemptOk) {
                attemptOk = true;
                int vi = order[pos];
                remember(app, group, vi);
                round = 0;
                order = buildOrder(group, vi); // se travar mais tarde, volta a tentar tudo
                pos = 0;
                if (listener != null) {
                    listener.onPlaying(group, vi);
                }
            }
        } else if (state == Player.STATE_BUFFERING) {
            if (attemptOk && !stallArmed) {
                stallArmed = true; // travou no meio da transmissão
                main.postDelayed(stall, STALL_MS);
            }
        } else if (state == Player.STATE_ENDED) {
            onFail();
        }
    }

    /** Erro ou travada: próxima versão; se acabaram, reconecta (1 s, 2 s, 4 s); depois, erro. */
    private void onFail() {
        main.removeCallbacks(stall);
        stallArmed = false;
        if (pos + 1 < order.length) {
            pos++;
            startAttempt();
            return;
        }
        if (round < MAX_RECONNECTS) {
            long wait = 1000L << round;
            round++;
            order = new int[]{order[0]}; // nas reconexões, só a primeira versão
            pos = 0;
            final int t = token;
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (t == token && active) {
                        startAttempt();
                    }
                }
            }, wait);
            return;
        }
        active = false;
        player.stop();
        if (listener != null && group != null) {
            listener.onFailed(group);
        }
    }

    private static String pathOf(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        int q = u.indexOf('?');
        return q >= 0 ? u.substring(0, q) : u;
    }

    // ---------- Última versão que funcionou ----------

    private static SharedPreferences last(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS_LAST, Context.MODE_PRIVATE);
    }

    private static void remember(Context c, ChannelGroup g, int variantIndex) {
        last(c).edit().putString(g.key, g.variants.get(variantIndex).item.id).apply();
    }

    /**
     * Versão para começar quando a pessoa não escolheu: 1) a última que funcionou; 2) a
     * qualidade preferida dos Ajustes (ou a mais próxima abaixo); 4K só se não houver outra.
     */
    static int autoVariant(Context c, ChannelGroup g) {
        String id = last(c).getString(g.key, null);
        if (id != null) {
            for (int i = 0; i < g.variants.size(); i++) {
                if (g.variants.get(i).item.id.equals(id)) {
                    return i;
                }
            }
        }
        int want = ChannelNames.rank(AppPrefs.preferredQuality(c));
        int lastNon4k = -1;
        for (int i = 0; i < g.variants.size(); i++) {
            ChannelGroup.Variant v = g.variants.get(i);
            if (ChannelNames.UHD4K.equals(v.quality)) {
                continue;
            }
            lastNon4k = i;
            if (ChannelNames.rank(v.quality) >= want) {
                return i;
            }
        }
        return lastNon4k >= 0 ? lastNon4k : 0;
    }

    // ---------- Pré-aquecer ----------

    /**
     * Ao focar um canal na grade: resolve o nome do servidor (DNS) e abre uma conexão curta
     * (sem pedir o vídeo), para o canal abrir mais rápido depois. Não gasta uma "tela" do
     * servidor, porque nenhum pedido de vídeo é feito.
     */
    static void prewarm(Context c, ChannelGroup g) {
        if (g == null || g.variants.isEmpty()) {
            return;
        }
        final String url = Catalog.urlOf(c, g.variants.get(autoVariant(c, g)).item);
        if (url == null) {
            return;
        }
        WARM.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Uri u = Uri.parse(url);
                    String host = u.getHost();
                    if (host == null) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    synchronized (WARM) {
                        if (host.equals(lastWarmHost) && now - lastWarmAt < 60000L) {
                            return;
                        }
                        lastWarmHost = host;
                        lastWarmAt = now;
                    }
                    int port = u.getPort() > 0 ? u.getPort()
                            : ("https".equalsIgnoreCase(u.getScheme()) ? 443 : 80);
                    InetAddress addr = InetAddress.getByName(host);
                    Socket s = new Socket();
                    try {
                        s.connect(new InetSocketAddress(addr, port), 1500);
                    } finally {
                        try {
                            s.close();
                        } catch (Exception ignored) {
                            // nada a fazer
                        }
                    }
                } catch (Exception ignored) {
                    // pré-aquecer é só ajuda: se falhar, o canal abre normalmente
                }
            }
        });
    }
}
