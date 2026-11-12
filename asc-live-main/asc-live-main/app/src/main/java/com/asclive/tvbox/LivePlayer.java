package com.asclive.tvbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
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

    /** Falha somente depois de uma janela longa sem READY/erro; BUFFERING continua sendo estado. */
    static final long STALL_MS = 45000L;
    private static final int MAX_RECONNECTS = 3;
    private static final String PREFS_LAST = "asc_live_last";
    private static final String USER_AGENT = "ASC-Cine/1.0 (Android)";

    private static LivePlayer instance;
    private static final ExecutorService WARM = Executors.newSingleThreadExecutor();
    private static String lastWarmHost = "";
    private static long lastWarmAt = 0L;

    private final Context app;
    private final ExoPlayer player;
    private final DefaultExtractorsFactory extractors;
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
    /** Evita que erro, STATE_ENDED e o watchdog processem a mesma tentativa duas vezes. */
    private boolean failureHandled;
    private boolean triedXtreamFormat;
    private String forcedUrl;
    private long attemptStartedAt;
    private String lastFailure = "none";
    private int lastState = Player.STATE_IDLE;
    private PlaybackDiagnostics.Session diagnostics;
    private int attemptNumber;

    private final Runnable stall = new Runnable() {
        @Override
        public void run() {
            stallArmed = false;
            if (active) {
                Log.e("ASC.LivePlayer", "stage=watchdog_timeout state=" + stateName(lastState)
                        + " elapsedMs=" + (System.currentTimeMillis() - attemptStartedAt)
                        + " lastFailure=" + lastFailure);
                diagFailure("watchdog_timeout", "state=" + stateName(lastState)
                        + ",elapsedMs=" + (System.currentTimeMillis() - attemptStartedAt)
                        + ",lastFailure=" + lastFailure);
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
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true);
        extractors = new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES);
        // Buffer de ao vivo: abre rápido (0,5 s) e guarda pouco (1,5 s a 8 s).
        DefaultLoadControl load = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(2500, 15000, 1200, 3000)
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
                lastState = state;
                Log.i("ASC.LivePlayer", "state=" + stateName(state)
                        + " stage=player_callback");
                diagEvent("state", "state=" + stateName(state));
                handleState(state);
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                lastFailure = errorSummary(error);
                logPlaybackError("player_error", error);
                diagFailure("player_error", "code=" + error.errorCode
                        + ",name=" + PlaybackException.getErrorCodeName(error.errorCode)
                        + ",http=" + httpCode(error) + ",cause=" + errorSummary(error));
                if (active) {
                    onFail();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Log.i("ASC.LivePlayer", "isPlaying=" + isPlaying + " stage=player_callback");
                diagEvent("is_playing_changed", "value=" + isPlaying);
            }
        });
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onLoadStarted(EventTime eventTime, LoadEventInfo info,
                                      MediaLoadData data) {
                Log.d("ASC.LivePlayer", "stage=http_request_started type="
                        + mediaType(data) + " content=" + contentType(info));
                diagEvent("http_request_started", "type=" + mediaType(data)
                        + ",content=" + contentType(info));
            }

            @Override
            public void onLoadCompleted(EventTime eventTime, LoadEventInfo info,
                                        MediaLoadData data) {
                Log.d("ASC.LivePlayer", "stage=http_response type=" + mediaType(data)
                        + " content=" + contentType(info) + " bytes=" + info.bytesLoaded);
                diagEvent("http_response", "type=" + mediaType(data)
                        + ",content=" + contentType(info) + ",bytes=" + info.bytesLoaded);
            }

            @Override
            public void onLoadError(EventTime eventTime, LoadEventInfo info,
                                    MediaLoadData data, IOException error,
                                    boolean wasCanceled) {
                lastFailure = errorSummary(error);
                Log.e("ASC.LivePlayer", "stage=http_error type=" + mediaType(data)
                        + " content=" + contentType(info) + " canceled=" + wasCanceled
                        + " error=" + errorSummary(error));
                diagFailure("http_error", "type=" + mediaType(data)
                        + ",content=" + contentType(info) + ",canceled=" + wasCanceled
                        + ",http=" + httpCode(error) + ",error=" + errorSummary(error));
            }

            @Override
            public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData data) {
                Log.i("ASC.LivePlayer", "stage=format_detected mime="
                        + safeText(data.trackFormat == null ? null : data.trackFormat.sampleMimeType)
                        + " codecs=" + safeText(data.trackFormat == null
                        ? null : data.trackFormat.codecs));
                diagEvent("format_detected", "mime="
                        + safeText(data.trackFormat == null ? null : data.trackFormat.sampleMimeType)
                        + ",codecs=" + safeText(data.trackFormat == null
                        ? null : data.trackFormat.codecs));
            }

            @Override
            public void onVideoDecoderInitialized(EventTime eventTime, String decoderName,
                                                   long initializedTimestampMs,
                                                   long initializationDurationMs) {
                Log.i("ASC.LivePlayer", "stage=video_decoder decoder="
                        + safeText(decoderName) + " initMs=" + initializationDurationMs);
                diagEvent("video_decoder", "decoder=" + safeText(decoderName)
                        + ",initMs=" + initializationDurationMs);
            }

            @Override
            public void onAudioDecoderInitialized(EventTime eventTime, String decoderName,
                                                   long initializedTimestampMs,
                                                   long initializationDurationMs) {
                Log.i("ASC.LivePlayer", "stage=audio_decoder decoder="
                        + safeText(decoderName) + " initMs=" + initializationDurationMs);
                diagEvent("audio_decoder", "decoder=" + safeText(decoderName)
                        + ",initMs=" + initializationDurationMs);
            }

            @Override
            public void onRenderedFirstFrame(EventTime eventTime, Object output,
                                             long renderTimeMs) {
                Log.i("ASC.LivePlayer", "stage=first_frame renderMs=" + renderTimeMs);
                diagEvent("first_frame", "renderMs=" + renderTimeMs);
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
        if (g == null || g.variants == null || g.variants.isEmpty()) {
            if (listener != null && g != null) listener.onFailed(g);
            return;
        }
        try {
        token++;
        main.removeCallbacks(stall);
        stallArmed = false;
        group = g;
        triedXtreamFormat = false;
        forcedUrl = null;
        int start = versao >= 0 && versao < g.variants.size() ? versao : autoVariant(app, g);
        order = buildOrder(g, start);
        pos = 0;
        round = 0;
        active = true;
        attemptNumber = 0;
        if (diagnostics != null) {
            diagnostics.finish("replaced", "nova_sessao=true");
        }
        diagnostics = PlaybackDiagnostics.start(app, safeName(g), safeId(g));
        diagEvent("play_requested", "requestedVariant=" + versao);
        startAttempt();
        } catch (Exception e) {
            active = false;
            Log.e("ASC.LivePlayer", "play failed: " + e.getClass().getSimpleName());
            diagFailure("play_failed", "error=" + errorSummary(e));
            if (diagnostics != null) {
                diagnostics.finish("failed", "stage=play");
            }
            if (listener != null) listener.onFailed(g);
        }
    }

    /** Para de tocar e solta a conexão (servidores IPTV limitam telas ao mesmo tempo). */
    void stop() {
        token++;
        active = false;
        main.removeCallbacks(stall);
        stallArmed = false;
        player.stop();
        player.clearMediaItems();
        if (diagnostics != null) {
            diagnostics.finish("stopped", "active=" + active);
            diagnostics = null;
        }
    }

    private static int[] buildOrder(ChannelGroup g, int start) {
        int n = g.variants.size();
        if (n == 0) return new int[0];
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
        if (g.variants == null || g.variants.isEmpty() || order.length == 0
                || pos < 0 || pos >= order.length) {
            onFail();
            return;
        }
        final int vi = order[pos];
        attemptNumber++;
        attemptStartedAt = System.currentTimeMillis();
        lastFailure = "none";
        lastState = Player.STATE_IDLE;
        String url = forcedUrl != null ? forcedUrl : Catalog.urlOf(app, g.variants.get(vi).item);
        forcedUrl = null;
        attemptOk = false;
        failureHandled = false;
        if (url == null) {
            lastFailure = "missing_media_url";
            diagFailure("missing_media_url", "attempt=" + attemptNumber);
            Log.e("ASC.LivePlayer", "media source missing for selected channel");
            onFail();
            return;
        }
        Uri mediaUri;
        try {
            mediaUri = Uri.parse(url);
            String scheme = mediaUri.getScheme();
            if (mediaUri.getHost() == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                lastFailure = "invalid_http_uri";
                diagFailure("invalid_http_uri", "attempt=" + attemptNumber);
                Log.e("ASC.LivePlayer", "media source has invalid scheme or host");
                onFail();
                return;
            }
        } catch (Exception e) {
            lastFailure = errorSummary(e);
            diagFailure("invalid_uri_exception", "attempt=" + attemptNumber
                    + ",error=" + errorSummary(e));
            Log.e("ASC.LivePlayer", "media source URI is invalid: " + e.getClass().getSimpleName());
            onFail();
            return;
        }
        if (listener != null) {
            listener.onOpening(g, vi);
        }
        Log.i("ASC.LivePlayer", "stage=validated_url channel=" + safeName(g) + " id="
                + safeId(g) + " media=" + mediaKind(url) + " uri=" + uriSummary(mediaUri));
        diagEvent("attempt_started", "attemptId=" + attemptId() + ",media=" + mediaKind(url)
                + ",uri=" + uriSummary(mediaUri));
        XtreamApi.Item item = g.variants.get(vi).item;
        Map<String, String> headers = new LinkedHashMap<>(item.headers);
        if (!headers.containsKey("User-Agent")) {
            headers.put("User-Agent", USER_AGENT);
        }
        DefaultHttpDataSource.Factory channelHttp = new DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true);
        MediaItem mediaItem = buildMediaItem(mediaUri);
        Log.d("ASC.LivePlayer", "stage=media_item_created media=" + mediaKind(url)
                + " uri=" + uriSummary(mediaUri) + " headers=" + headerSummary(headers));
        diagEvent("media_item_created", "attemptId=" + attemptId() + ",media=" + mediaKind(url)
                + ",uri=" + uriSummary(mediaUri) + ",headers=" + headerSummary(headers));
        try {
            MediaSource source = new DefaultMediaSourceFactory(channelHttp, extractors)
                    .createMediaSource(mediaItem);
            Log.d("ASC.LivePlayer", "media source configured channel=" + safeName(g)
                    + " media=" + mediaKind(url)
                    + " headers=" + headerSummary(headers));
            diagEvent("media_source_configured", "attemptId=" + attemptId()
                    + ",media=" + mediaKind(url) + ",headers=" + headerSummary(headers));
            player.setMediaSource(source);
            player.prepare();
            player.setPlayWhenReady(true);
        } catch (Exception e) {
            lastFailure = errorSummary(e);
            Log.e("ASC.LivePlayer", "stage=prepare_failed error=" + errorSummary(e));
            diagFailure("prepare_failed", "attempt=" + attemptNumber
                    + ",error=" + errorSummary(e));
            onFail();
            return;
        }
        main.removeCallbacks(stall);
        stallArmed = true;
        main.postDelayed(stall, STALL_MS);
        Log.d("ASC.LivePlayer", "stage=prepared_waiting_for_ready watchdogMs=" + STALL_MS);
        diagEvent("prepared_waiting_for_ready", "attemptId=" + attemptId()
                + ",watchdogMs=" + STALL_MS);
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
                round = 0;
                order = buildOrder(group, vi); // se travar mais tarde, volta a tentar tudo
                pos = 0;
                if (listener != null) {
                    listener.onPlaying(group, vi);
                }
                Log.i("ASC.LivePlayer", "playing channel=" + safeName(group)
                        + " id=" + safeId(group));
                diagEvent("playing", "variant=" + vi);
            }
        } else if (state == Player.STATE_BUFFERING) {
            Log.d("ASC.LivePlayer", "buffering channel=" + safeName(group));
            diagEvent("buffering", "attempt=" + attemptNumber);
            if (attemptOk && !stallArmed) {
                stallArmed = true; // travou no meio da transmissão
                main.postDelayed(stall, STALL_MS);
            }
        } else if (state == Player.STATE_ENDED) {
            diagFailure("ended", "attempt=" + attemptNumber);
            onFail();
        }
    }

    /** Erro ou travada: próxima versão; se acabaram, reconecta (1 s, 2 s, 4 s); depois, erro. */
    private void onFail() {
        if (failureHandled) {
            return;
        }
        failureHandled = true;
        Log.e("ASC.LivePlayer", "stage=fail_transition state=" + stateName(lastState)
                + " elapsedMs=" + (System.currentTimeMillis() - attemptStartedAt)
                + " reason=" + lastFailure);
        diagEvent("fail_transition", "state=" + stateName(lastState)
                + ",elapsedMs=" + (System.currentTimeMillis() - attemptStartedAt)
                + ",reason=" + lastFailure);
        main.removeCallbacks(stall);
        stallArmed = false;
        if (order.length == 0) {
            active = false;
            if (listener != null && group != null) listener.onFailed(group);
            return;
        }
        if (pos + 1 < order.length) {
            Log.w("ASC.LivePlayer", "stage=retry_next_variant");
            diagEvent("retry_next_variant", "nextIndex=" + (pos + 1));
            pos++;
            startAttempt();
            return;
        }
        if (round < MAX_RECONNECTS) {
            Log.w("ASC.LivePlayer", "stage=retry_reconnect round=" + (round + 1));
            diagEvent("retry_reconnect", "round=" + (round + 1));
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
        if (!triedXtreamFormat && !Catalog.isM3u(Catalog.creds(app))) {
            String primary = Catalog.urlOf(app, group.variants.get(order[0]).item);
            String alternate = alternateFormat(primary);
            if (alternate != null) {
                Log.w("ASC.LivePlayer", "stage=retry_alternate_xtream_format");
                diagEvent("retry_alternate_xtream_format", "format_change=true");
                triedXtreamFormat = true;
                forcedUrl = alternate;
                round = 0;
                order = new int[]{order[0]};
                pos = 0;
                startAttempt();
                return;
            }
        }
        active = false;
        Log.e("ASC.LivePlayer", "stage=failed_all_attempts");
        diagFailure("failed_all_attempts", "lastFailure=" + lastFailure);
        if (diagnostics != null) {
            diagnostics.finish("failed", "firstFailureRecorded=true");
        }
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

    private static String safeName(ChannelGroup g) {
        return g == null || g.nome == null ? "unknown" : g.nome;
    }

    private static String safeId(ChannelGroup g) {
        if (g == null || g.item() == null || g.item().id == null) return "unknown";
        String id = g.item().id;
        return id.length() > 12 ? id.substring(0, 12) : id;
    }

    private static String causeType(Throwable error) {
        Throwable t = error;
        for (int i = 0; t != null && i < 6; i++, t = t.getCause()) {
            String n = t.getClass().getSimpleName();
            if (n.contains("Http") || n.contains("Response") || n.contains("Parser")
                    || n.contains("Decoder") || n.contains("DataSource")) return n;
        }
        return error == null ? "unknown" : error.getClass().getSimpleName();
    }

    private void diagEvent(String stage, String details) {
        if (diagnostics != null) {
            diagnostics.event(stage, details);
        }
    }

    private void diagFailure(String stage, String details) {
        if (diagnostics != null) {
            diagnostics.firstFailure(stage, details);
        }
    }

    private String attemptId() {
        return diagnostics == null ? "unknown" : diagnostics.id() + "-a" + attemptNumber;
    }

    static MediaItem buildMediaItem(Uri uri) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);
        if (uri != null && pathOf(uri.toString()).endsWith(".m3u8")) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        }
        return builder.build();
    }

    private static String stateName(int state) {
        switch (state) {
            case Player.STATE_IDLE: return "IDLE";
            case Player.STATE_BUFFERING: return "BUFFERING";
            case Player.STATE_READY: return "READY";
            case Player.STATE_ENDED: return "ENDED";
            default: return "UNKNOWN";
        }
    }

    private static String mediaKind(String url) {
        return pathOf(url).endsWith(".m3u8") ? "HLS" : "PROGRESSIVE_OR_TS";
    }

    private static String uriSummary(Uri uri) {
        if (uri == null) return "null";
        return "scheme=" + safeText(uri.getScheme()) + ",chars=" + uri.toString().length()
                + ",query=" + (uri.getQuery() != null) + ",sha256=" + shortHash(uri.toString());
    }

    private static String shortHash(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 6; i++) out.append(String.format(Locale.ROOT, "%02x", hash[i]));
            return out.toString();
        } catch (Exception ignored) {
            return "unavailable";
        }
    }

    private static String mediaType(MediaLoadData data) {
        if (data == null) return "unknown";
        if (data.dataType == C.DATA_TYPE_MANIFEST) return "manifest";
        if (data.dataType == C.DATA_TYPE_MEDIA) return "media";
        return String.valueOf(data.dataType);
    }

    private static String contentType(LoadEventInfo info) {
        if (info == null || info.responseHeaders == null) return "unknown";
        for (Map.Entry<String, java.util.List<String>> entry : info.responseHeaders.entrySet()) {
            if (entry.getKey() != null && "content-type".equalsIgnoreCase(entry.getKey())
                    && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return safeText(entry.getValue().get(0));
            }
        }
        return "unknown";
    }

    private static String errorSummary(Throwable error) {
        if (error == null) return "none";
        StringBuilder out = new StringBuilder();
        Throwable current = error;
        for (int i = 0; current != null && i < 6; i++, current = current.getCause()) {
            if (i > 0) out.append(" <- ");
            out.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isEmpty()) {
                out.append(":").append(sanitizeMessage(message));
            }
        }
        return out.toString();
    }

    private static void logPlaybackError(String stage, PlaybackException error) {
        Log.e("ASC.LivePlayer", "stage=" + stage + " code=" + error.errorCode
                + " name=" + safeText(PlaybackException.getErrorCodeName(error.errorCode))
                + " http=" + httpCode(error)
                + " cause=" + errorSummary(error));
    }

    private static String sanitizeMessage(String message) {
        String s = message.replaceAll("https?://\\S+", "<url>");
        return s.length() > 220 ? s.substring(0, 220) : s;
    }

    private static String safeText(String text) {
        return text == null || text.isEmpty() ? "unknown" : sanitizeMessage(text);
    }

    private static int httpCode(Throwable error) {
        Throwable t = error;
        for (int i = 0; t != null && i < 6; i++, t = t.getCause()) {
            if (t instanceof HttpDataSource.InvalidResponseCodeException) {
                return ((HttpDataSource.InvalidResponseCodeException) t).responseCode;
            }
        }
        return -1;
    }

    private static String headerSummary(Map<String, String> headers) {
        return "ua=" + headers.containsKey("User-Agent")
                + ",referer=" + headers.containsKey("Referer")
                + ",cookie=" + headers.containsKey("Cookie")
                + ",origin=" + headers.containsKey("Origin");
    }

    private static String alternateFormat(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        String suffix = q >= 0 ? url.substring(q) : "";
        if (path.endsWith(".ts")) return path.substring(0, path.length() - 3) + "m3u8" + suffix;
        if (path.endsWith(".m3u8")) return path.substring(0, path.length() - 5) + "ts" + suffix;
        return null;
    }

    // ---------- Qual versão abrir ----------

    /** Cada canal tem uma versão só (a resolução está no nome do canal): sempre a primeira. */
    static int autoVariant(Context c, ChannelGroup g) {
        return 0;
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
