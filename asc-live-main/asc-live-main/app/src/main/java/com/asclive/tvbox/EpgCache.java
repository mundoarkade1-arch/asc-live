package com.asclive.tvbox;

import android.os.Handler;
import android.os.Looper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Guarda na memória a programação (EPG) dos canais já consultados (igual ao ASC TV Box).
 *
 * Economia de memória: no máximo 80 canais guardados, cada um por 1 minuto.
 * Economia de rede: no máximo 2 pedidos ao servidor ao mesmo tempo.
 *
 * Quem pede (get) é avisado SEMPRE na thread principal e pode mexer na tela direto.
 * Não registra senhas nem endereços em log.
 */
final class EpgCache {

    /** epg vem null quando o canal não tem guia, ou quando nada passa agora. */
    interface Listener {
        void onEpg(XtreamApi.Epg epg);
    }

    interface ScheduleListener {
        void onSchedule(List<XtreamApi.Program> programs);
    }

    private static final int MAX_ENTRIES = 80;
    private static final long TTL_MS = 60000L;

    private static final class CacheEntry {
        final XtreamApi.Epg epg;
        final long savedAt;

        CacheEntry(XtreamApi.Epg epg, long savedAt) {
            this.epg = epg;
            this.savedAt = savedAt;
        }
    }

    private static final Map<String, CacheEntry> CACHE =
            new LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private EpgCache() {
    }

    /**
     * Busca a programação de um canal. Se já estiver guardada e valendo, avisa na hora.
     * Se o pedido ao servidor falhar, avisa com null e não guarda o resultado.
     * Chamar na thread principal.
     */
    static void get(final String server, final String user, final String pass,
                    final String streamId, final Listener listener) {
        final String key = server + "|" + user + "|" + streamId;
        CacheEntry e = fresh(key);
        if (e != null) {
            listener.onEpg(e.epg);
            return;
        }
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                XtreamApi.Epg result = null;
                boolean ok = false;
                try {
                    result = XtreamApi.epg(server, user, pass, streamId);
                    ok = true;
                } catch (Exception ignored) {
                    // sem rede ou servidor fora do ar: avisa com null abaixo
                }
                if (ok) {
                    synchronized (CACHE) {
                        CACHE.put(key, new CacheEntry(result, System.currentTimeMillis()));
                    }
                }
                final XtreamApi.Epg out = result;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onEpg(out);
                    }
                });
            }
        });
    }

    /** Busca o programa atual e os prÃ³ximos itens sem bloquear a tela. */
    static void getSchedule(final String server, final String user, final String pass,
                            final String streamId, final ScheduleListener listener) {
        POOL.execute(() -> {
            List<XtreamApi.Program> result = null;
            try {
                result = XtreamApi.schedule(server, user, pass, streamId);
            } catch (Exception ignored) {
                // a tela mostra programaÃ§Ã£o indisponÃ­vel
            }
            final List<XtreamApi.Program> out = result;
            MAIN.post(() -> listener.onSchedule(out));
        });
    }

    /** Esvazia tudo (faltou memória ou trocou de login). */
    static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    /** Entrada guardada que ainda vale: menos de 1 minuto e o programa ainda não acabou. */
    private static CacheEntry fresh(String key) {
        CacheEntry e;
        synchronized (CACHE) {
            e = CACHE.get(key);
        }
        if (e == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - e.savedAt > TTL_MS) {
            return null;
        }
        if (e.epg != null && now >= e.epg.end) {
            return null;
        }
        return e;
    }
}
