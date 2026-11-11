package com.asclive.tvbox;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ponte única entre o app e a lista do usuário (servidor Xtream ou arquivo M3U).
 * ASC Live: só canais AO VIVO. NUNCA chamar na thread principal (pode fazer rede e disco).
 *
 * Ordem de busca da lista: 1) memória; 2) arquivo guardado (LiveCache, validade de 6 horas:
 * se estiver velho, é mostrado na hora e atualizado por trás); 3) a lista do servidor.
 * O controle parental é aplicado na hora de devolver (o que fica
 * guardado é a lista completa).
 */
final class Catalog {

    /** Limite de canais guardados (protege a memória do aparelho). */
    static final int MAX_LIVE = 30000;

    static final class Cat {
        final String id;
        final String name;
        final boolean adult;

        Cat(String id, String name) {
            this.id = id;
            this.name = name;
            this.adult = ParentalStore.isAdultCategory(name);
        }
    }

    private static CredentialStore.Credentials cachedCreds;
    private static LiveCache.Entry snap;
    private static boolean refreshing;
    /** Agrupamento já feito (vale enquanto a lista em memória e o controle parental forem os mesmos). */
    private static List<ChannelGroup> groupsMemo;
    private static LiveCache.Entry groupsFrom;
    private static boolean groupsParental;
    /** Só uma leitura lenta por vez (várias telas pedindo ao mesmo tempo não repetem a rede). */
    private static final Object LOAD_LOCK = new Object();

    private Catalog() {
    }

    // ---------- Login em uso ----------

    /** O login é UM SÓ para o aparelho: trocar de perfil não recarrega nem pede login. */
    static synchronized CredentialStore.Credentials creds(Context ctx) {
        if (cachedCreds == null) {
            cachedCreds = CredentialStore.load(ctx);
        }
        return cachedCreds;
    }

    /** Esquece o login e os dados em memória (trocou de lista). */
    static synchronized void invalidate() {
        cachedCreds = null;
        snap = null;
    }

    /** "Atualizar": esquece a lista em memória e apaga o arquivo guardado. Mantém o login. */
    static void clearCache(Context ctx) {
        synchronized (Catalog.class) {
            snap = null;
        }
        LiveCache.clear(ctx);
    }

    static boolean isM3u(CredentialStore.Credentials c) {
        return c != null && CredentialStore.MODE_M3U.equals(c.mode);
    }

    private static CredentialStore.Credentials need(Context ctx) throws IOException {
        CredentialStore.Credentials c = creds(ctx);
        if (c == null) {
            throw new IOException("sem login");
        }
        return c;
    }

    // ---------- Canais ao vivo ----------

    /** Categorias de canais ao vivo (sem as adultas, se o controle parental estiver ligado). */
    static List<Cat> liveCategories(Context ctx) throws IOException {
        LiveCache.Entry e = snapshot(ctx);
        boolean parental = ParentalStore.isEnabled(ctx);
        List<Cat> out = new ArrayList<>();
        for (Cat g : e.cats) {
            if (!parental || !g.adult) {
                out.add(g);
            }
        }
        return out;
    }

    /** Canais de UMA categoria, já sem os adultos (controle parental). */
    static List<XtreamApi.Item> liveChannels(Context ctx, String categoryId) throws IOException {
        List<XtreamApi.Item> out = new ArrayList<>();
        for (XtreamApi.Item it : allLive(ctx)) {
            if (it.categoryId.equals(categoryId)) {
                out.add(it);
            }
        }
        return out;
    }

    /** TODOS os canais ao vivo, já sem os adultos (controle parental). */
    static List<XtreamApi.Item> allLive(Context ctx) throws IOException {
        LiveCache.Entry e = snapshot(ctx);
        boolean parental = ParentalStore.isEnabled(ctx);
        Set<String> adultCats = new HashSet<>();
        if (parental) {
            for (Cat g : e.cats) {
                if (g.adult) {
                    adultCats.add(g.id);
                }
            }
        }
        List<XtreamApi.Item> out = new ArrayList<>(e.items.size());
        for (XtreamApi.Item it : e.items) {
            if (parental && (adultCats.contains(it.categoryId)
                    || ParentalStore.isAdultName(it.name))) {
                continue;
            }
            out.add(it);
        }
        return out;
    }

    /**
     * Canais ao vivo agrupados: as versões SD/HD/FHD/4K do mesmo canal viram um só.
     * A lista devolvida é compartilhada: quem usa NÃO deve alterá-la.
     */
    static List<ChannelGroup> allGroups(Context ctx) throws IOException {
        LiveCache.Entry e = snapshot(ctx);
        boolean parental = ParentalStore.isEnabled(ctx);
        synchronized (Catalog.class) {
            if (groupsMemo != null && groupsFrom == e && groupsParental == parental) {
                return groupsMemo;
            }
        }
        List<ChannelGroup> built = ChannelGroup.build(allLive(ctx));
        synchronized (Catalog.class) {
            groupsMemo = built;
            groupsFrom = e;
            groupsParental = parental;
        }
        return built;
    }

    /** Endereço para tocar um canal (contém login: nunca mostrar nem registrar em log). */
    static String urlOf(Context ctx, XtreamApi.Item channel) {
        CredentialStore.Credentials c = creds(ctx);
        if (c == null) {
            return null;
        }
        return XtreamApi.streamUrl(c.server, c.user, c.pass, channel);
    }

    // ---------- Leitura da lista (memória, arquivo, servidor) ----------

    private static LiveCache.Entry snapshot(Context ctx) throws IOException {
        final CredentialStore.Credentials c = need(ctx);
        final String owner = LiveCache.ownerKey(c);
        LiveCache.Entry hit = inMemory(owner);
        if (hit != null) {
            return hit;
        }
        synchronized (LOAD_LOCK) {
            hit = inMemory(owner);
            if (hit != null) {
                return hit;
            }
            LiveCache.Entry saved = LiveCache.load(ctx, owner);
            if (saved != null) {
                put(saved);
                if (!saved.fresh()) {
                    refreshInBackground(ctx, c, owner);
                }
                return saved;
            }
            LiveCache.Entry fresh = compute(ctx, c, owner);
            put(fresh);
            return fresh;
        }
    }

    private static synchronized LiveCache.Entry inMemory(String owner) {
        return snap != null && owner.equals(snap.owner) ? snap : null;
    }

    private static synchronized void put(LiveCache.Entry e) {
        snap = e;
    }

    /** Lê a lista do servidor, guarda em arquivo e devolve (lento: é a conta pesada). */
    private static LiveCache.Entry compute(Context ctx, CredentialStore.Credentials c,
                                           String owner) throws IOException {
        final List<XtreamApi.Item> items = new ArrayList<>();
        final Map<String, Cat> known = new LinkedHashMap<>();
        if (isM3u(c)) {
            M3uApi.streamLive(c.m3uUrl, false, item -> {
                if (items.size() >= MAX_LIVE) {
                    return false;
                }
                items.add(item);
                if (!known.containsKey(item.categoryId)) {
                    known.put(item.categoryId, new Cat(item.categoryId, item.categoryId));
                }
                return true;
            });
        } else {
            try {
                for (XtreamApi.Category g : XtreamApi.liveCategories(c.server, c.user, c.pass)) {
                    known.put(g.id, new Cat(g.id, g.name));
                }
            } catch (IOException ignored) {
                // sem categorias: montamos pelos canais, abaixo
            }
            XtreamApi.streamLive(c.server, c.user, c.pass, item -> {
                if (items.size() >= MAX_LIVE) {
                    return false;
                }
                items.add(item);
                return true;
            });
        }
        if (items.isEmpty()) {
            throw new IOException("sem canais");
        }
        // Só categorias que têm canal; categorias que faltam na lista entram como "Outros".
        Set<String> used = new HashSet<>();
        for (XtreamApi.Item it : items) {
            used.add(it.categoryId);
        }
        List<Cat> cats = new ArrayList<>();
        for (Cat g : known.values()) {
            if (used.remove(g.id)) {
                cats.add(g);
            }
        }
        for (String id : used) {
            cats.add(new Cat(id, "Outros"));
        }
        return LiveCache.save(ctx, owner, cats, items);
    }

    /** Atualiza o arquivo guardado sem atrapalhar a tela (vale na próxima vez que abrir). */
    private static void refreshInBackground(final Context ctx,
                                            final CredentialStore.Credentials c,
                                            final String owner) {
        synchronized (Catalog.class) {
            if (refreshing) {
                return;
            }
            refreshing = true;
        }
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                LiveCache.Entry fresh = compute(app, c, owner);
                synchronized (Catalog.class) {
                    if (snap != null && owner.equals(snap.owner)) {
                        snap = fresh;
                    }
                }
            } catch (Exception ignored) {
                // sem internet agora: continua valendo o que já está guardado
            } finally {
                synchronized (Catalog.class) {
                    refreshing = false;
                }
            }
        }).start();
    }

    /** Dias que faltam para a conta Xtream vencer (-1 se não se sabe ou não é Xtream). */
    static long daysToExpiry(Context ctx) {
        CredentialStore.Credentials c = creds(ctx);
        if (c == null || isM3u(c)) {
            return -1;
        }
        LoginChecker.lastExpiry = 0L;
        LoginChecker.checkXtream(c.server, c.user, c.pass);
        long exp = LoginChecker.lastExpiry;
        if (exp <= 0) {
            return -1;
        }
        long left = exp * 1000L - System.currentTimeMillis();
        return left < 0 ? 0 : left / 86400000L;
    }
}
