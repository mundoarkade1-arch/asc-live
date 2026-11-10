package com.asclive.tvbox;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ponte única entre o app e a lista do usuário (servidor Xtream ou arquivo M3U).
 * Só trata FILMES e SÉRIES. NUNCA chamar na thread principal (faz rede).
 * Guarda na memória só coisas pequenas (categorias, os 200 itens mais novos, detalhes).
 */
final class Catalog {

    static final int PAGE = 40;
    static final int RECENT_MAX = 200;

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

    static final class Page {
        final List<XtreamApi.Item> items;
        /** Quantos itens vieram do servidor (antes de tirar bloqueados). Serve de "offset". */
        final int raw;
        final boolean end;

        Page(List<XtreamApi.Item> items, int raw, boolean end) {
            this.items = items;
            this.raw = raw;
            this.end = end;
        }
    }

    static final class Ep {
        final String id;
        final String title;
        final int season;
        final int number;
        final String ext;
        final String url;

        Ep(String id, String title, int season, int number, String ext, String url) {
            this.id = id;
            this.title = title;
            this.season = season;
            this.number = number;
            this.ext = ext;
            this.url = url;
        }
    }

    private static CredentialStore.Credentials cachedCreds;
    private static String owner = "";
    private static boolean searchWarmed;
    private static final java.util.Set<String> REFRESHING = new java.util.HashSet<>();
    private static final Map<String, List<Cat>> CATS = new HashMap<>();
    private static final Map<String, List<XtreamApi.Item>> RECENT = new HashMap<>();
    private static final Map<String, XtreamApi.Details> DETAILS =
            new LinkedHashMap<String, XtreamApi.Details>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, XtreamApi.Details> e) {
                    return size() > 40;
                }
            };

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

    /** Esquece o login e os dados em memória (trocou de lista, ou "Atualizar"). */
    static synchronized void invalidate() {
        cachedCreds = null;
        searchWarmed = false;
        owner = "";
        CATS.clear();
        RECENT.clear();
        DETAILS.clear();
    }

    static boolean isM3u(CredentialStore.Credentials c) {
        return c != null && CredentialStore.MODE_M3U.equals(c.mode);
    }

    private static synchronized void checkOwner(CredentialStore.Credentials c) {
        String o = c.mode + "|" + c.server + "|" + c.user + "|" + c.m3uUrl;
        if (!o.equals(owner)) {
            owner = o;
            CATS.clear();
            RECENT.clear();
            DETAILS.clear();
        }
    }

    private static CredentialStore.Credentials need(Context ctx) throws IOException {
        CredentialStore.Credentials c = creds(ctx);
        if (c == null) {
            throw new IOException("sem login");
        }
        checkOwner(c);
        return c;
    }

    // ---------- Categorias e páginas ----------

    static List<Cat> categories(Context ctx, XtreamApi.Kind kind) throws IOException {
        CredentialStore.Credentials c = need(ctx);
        synchronized (Catalog.class) {
            List<Cat> hit = CATS.get(kind.name());
            if (hit != null) {
                return hit;
            }
        }
        List<Cat> out = new ArrayList<>();
        if (isM3u(c)) {
            for (String g : M3uApi.categories(c.m3uUrl, kind)) {
                out.add(new Cat(g, g));
            }
        } else {
            for (XtreamApi.Category g : XtreamApi.categories(c.server, c.user, c.pass, kind)) {
                out.add(new Cat(g.id, g.name));
            }
        }
        synchronized (Catalog.class) {
            CATS.put(kind.name(), out);
        }
        return out;
    }

    /** Categorias que podem aparecer fora da área adulta (respeita o controle parental). */
    static List<Cat> visibleCategories(Context ctx, XtreamApi.Kind kind) throws IOException {
        List<Cat> all = categories(ctx, kind);
        if (!ParentalStore.isEnabled(ctx)) {
            return all;
        }
        List<Cat> out = new ArrayList<>();
        for (Cat c : all) {
            if (!c.adult) {
                out.add(c);
            }
        }
        return out;
    }

    /** Uma página (40) de uma categoria. catId nulo = qualquer categoria. */
    static Page page(Context ctx, XtreamApi.Kind kind, String catId, int offset,
                     boolean allowAdult) throws IOException {
        CredentialStore.Credentials c = need(ctx);
        boolean skipAdult = ParentalStore.isEnabled(ctx) && !allowAdult;
        List<XtreamApi.Item> raw;
        if (isM3u(c)) {
            raw = M3uApi.page(c.m3uUrl, kind, catId, offset, PAGE, skipAdult);
        } else {
            raw = XtreamApi.items(c.server, c.user, c.pass, kind, catId, offset, PAGE);
        }
        return new Page(filter(ctx, raw, allowAdult), raw.size(), raw.size() < PAGE);
    }

    /** Tira bloqueados e (com controle parental ligado) itens adultos. */
    static List<XtreamApi.Item> filter(Context ctx, List<XtreamApi.Item> in, boolean allowAdult) {
        boolean parental = ParentalStore.isEnabled(ctx) && !allowAdult;
        List<XtreamApi.Item> out = new ArrayList<>();
        for (XtreamApi.Item it : in) {
            if (it.kind != XtreamApi.Kind.MOVIE && it.kind != XtreamApi.Kind.SERIES) {
                continue;
            }
            if (UserLists.isBlocked(ctx, it)) {
                continue;
            }
            if (parental && (ParentalStore.isAdultName(it.name)
                    || ParentalStore.isAdultCategory(it.categoryId))) {
                continue;
            }
            out.add(it);
        }
        return out;
    }

    // ---------- Destaques e Top 10 ----------

    /**
     * Os itens mais recentes do tipo (campo "added", até 200), já sem bloqueados e adultos.
     * Ordem de busca: 1) memória; 2) arquivo guardado (validade de 6 horas: se estiver velho,
     * é mostrado na hora e atualizado por trás, para a próxima vez); 3) a lista do servidor.
     */
    static List<XtreamApi.Item> recent(Context ctx, XtreamApi.Kind kind) throws IOException {
        final CredentialStore.Credentials c = need(ctx);
        List<XtreamApi.Item> base;
        synchronized (Catalog.class) {
            base = RECENT.get(kind.name());
        }
        if (base == null) {
            final String ownerKey = HomeCache.ownerKey(c);
            HomeCache.Entry saved = HomeCache.load(ctx, ownerKey, kind);
            if (saved != null) {
                base = saved.items;
                if (!saved.fresh()) {
                    refreshInBackground(ctx, c, kind, ownerKey);
                }
            } else {
                base = computeRecent(ctx, c, kind);
                HomeCache.save(ctx, ownerKey, kind, base);
            }
            synchronized (Catalog.class) {
                RECENT.put(kind.name(), base);
            }
        }
        return filter(ctx, base, false);
    }

    /** Lê a lista do servidor e devolve os 200 mais novos (lento: é a conta pesada). */
    private static List<XtreamApi.Item> computeRecent(Context ctx, CredentialStore.Credentials c,
                                                      XtreamApi.Kind kind) throws IOException {
        final List<XtreamApi.Item> top = new ArrayList<>();
        if (isM3u(c)) {
            List<Cat> cats = categories(ctx, kind);
            String first = null;
            for (Cat g : cats) {
                if (!g.adult) {
                    first = g.id;
                    break;
                }
            }
            top.addAll(M3uApi.page(c.m3uUrl, kind, first, 0, 60, true));
        } else {
            XtreamApi.streamAll(c.server, c.user, c.pass, kind, item -> {
                insertNewest(top, item);
                return true;
            });
        }
        return top;
    }

    /** Atualiza o arquivo guardado sem atrapalhar a tela (vale na próxima vez que abrir). */
    private static void refreshInBackground(final Context ctx,
                                            final CredentialStore.Credentials c,
                                            final XtreamApi.Kind kind, final String ownerKey) {
        final String id = ownerKey + kind.name();
        synchronized (Catalog.class) {
            if (!REFRESHING.add(id)) {
                return;
            }
        }
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                List<XtreamApi.Item> fresh = computeRecent(app, c, kind);
                HomeCache.save(app, ownerKey, kind, fresh);
            } catch (Exception ignored) {
                // sem internet agora: continua valendo o que já está guardado
            } finally {
                synchronized (Catalog.class) {
                    REFRESHING.remove(id);
                }
            }
        }).start();
    }

    private static void insertNewest(List<XtreamApi.Item> top, XtreamApi.Item it) {
        int n = top.size();
        if (n >= RECENT_MAX && it.added < top.get(n - 1).added) {
            return;
        }
        int pos = n;
        while (pos > 0 && top.get(pos - 1).added <= it.added) {
            pos--;
        }
        top.add(pos, it);
        if (top.size() > RECENT_MAX) {
            top.remove(top.size() - 1);
        }
    }

    /** Top 10 de hoje: dos 200 mais novos, os 10 de maior nota (M3U: os 10 primeiros). */
    static List<XtreamApi.Item> top10(Context ctx, XtreamApi.Kind kind) throws IOException {
        List<XtreamApi.Item> recent = recent(ctx, kind);
        List<XtreamApi.Item> sorted = new ArrayList<>(recent);
        if (!isM3u(need(ctx))) {
            Collections.sort(sorted, new Comparator<XtreamApi.Item>() {
                @Override
                public int compare(XtreamApi.Item a, XtreamApi.Item b) {
                    return Double.compare(b.rating, a.rating);
                }
            });
        }
        return new ArrayList<>(sorted.subList(0, Math.min(10, sorted.size())));
    }

    /** Cinco destaques: filmes e séries bem avaliados e recentes, intercalados. */
    static List<XtreamApi.Item> hero(Context ctx) throws IOException {
        return heroFrom(top10(ctx, XtreamApi.Kind.MOVIE), top10(ctx, XtreamApi.Kind.SERIES));
    }

    /** Monta os 5 destaques a partir dos Top 10 (qualquer um dos dois pode ser nulo). */
    static List<XtreamApi.Item> heroFrom(List<XtreamApi.Item> movies,
                                         List<XtreamApi.Item> series) {
        if (movies == null) {
            movies = new ArrayList<>();
        }
        if (series == null) {
            series = new ArrayList<>();
        }
        List<XtreamApi.Item> out = new ArrayList<>();
        int i = 0;
        while (out.size() < 5 && (i < movies.size() || i < series.size())) {
            if (i < movies.size()) {
                out.add(movies.get(i));
            }
            if (out.size() < 5 && i < series.size()) {
                out.add(series.get(i));
            }
            i++;
        }
        return out;
    }

    // ---------- Detalhes e episódios ----------

    static XtreamApi.Details details(Context ctx, XtreamApi.Item it) throws IOException {
        CredentialStore.Credentials c = need(ctx);
        if (isM3u(c)) {
            return new XtreamApi.Details("", "", "");
        }
        String key = it.kind.name() + it.id;
        synchronized (Catalog.class) {
            XtreamApi.Details hit = DETAILS.get(key);
            if (hit != null) {
                return hit;
            }
        }
        XtreamApi.Details d = XtreamApi.details(c.server, c.user, c.pass, it.kind, it.id);
        synchronized (Catalog.class) {
            DETAILS.put(key, d);
        }
        return d;
    }

    static List<Ep> episodes(Context ctx, XtreamApi.Item series) throws IOException {
        CredentialStore.Credentials c = need(ctx);
        List<Ep> out = new ArrayList<>();
        if (isM3u(c)) {
            for (M3uApi.Episode e : M3uApi.episodes(c.m3uUrl, series.name)) {
                out.add(new Ep("", e.title, e.season, e.number, "", e.url));
            }
        } else {
            for (XtreamApi.Episode e : XtreamApi.episodes(c.server, c.user, c.pass, series.id)) {
                out.add(new Ep(e.id, e.title, e.season, e.number, e.ext, ""));
            }
        }
        Collections.sort(out, new Comparator<Ep>() {
            @Override
            public int compare(Ep a, Ep b) {
                if (a.season != b.season) {
                    return a.season < b.season ? -1 : 1;
                }
                return a.number < b.number ? -1 : (a.number == b.number ? 0 : 1);
            }
        });
        return out;
    }

    // ---------- Endereços e chaves de progresso ----------

    static String urlOf(Context ctx, XtreamApi.Item movie) {
        CredentialStore.Credentials c = creds(ctx);
        if (c == null) {
            return null;
        }
        return XtreamApi.streamUrl(c.server, c.user, c.pass, movie);
    }

    static String urlOf(Context ctx, Ep ep) {
        CredentialStore.Credentials c = creds(ctx);
        if (c == null) {
            return null;
        }
        if (!ep.url.isEmpty()) {
            return ep.url;
        }
        XtreamApi.Item e = new XtreamApi.Item(XtreamApi.Kind.EPISODE, ep.id, ep.title, "", "", "",
                ep.ext);
        return XtreamApi.streamUrl(c.server, c.user, c.pass, e);
    }

    /** Chave de progresso de um vídeo (filme ou episódio). */
    static String progressKey(Context ctx, boolean episode, String id, String url) {
        CredentialStore.Credentials c = creds(ctx);
        if (c == null) {
            return "";
        }
        if (isM3u(c)) {
            return PlaybackProgress.urlKey(url);
        }
        return episode ? PlaybackProgress.episodeKey(c.server, c.user, id)
                : PlaybackProgress.movieKey(c.server, c.user, id);
    }

    static String seriesKey(Context ctx, XtreamApi.Item series) {
        CredentialStore.Credentials c = creds(ctx);
        if (c == null) {
            return "";
        }
        return isM3u(c) ? "ms|" + c.m3uUrl + "|" + series.name
                : "xs|" + c.server + "|" + c.user + "|" + series.id;
    }

    // ---------- Busca ----------

    /** Busca filmes e séries pelo nome. Avisa cada resultado; para ao devolver false. */
    static void search(Context ctx, String query, SearchIndex.Cancel cancel,
                       XtreamApi.ResultListener listener) throws IOException {
        CredentialStore.Credentials c = need(ctx);
        boolean skipAdult = ParentalStore.isEnabled(ctx);
        final boolean[] stop = {false};
        XtreamApi.ResultListener wrap = item -> {
            if (cancel != null && cancel.isCancelled()) {
                stop[0] = true;
                return false;
            }
            List<XtreamApi.Item> one = new ArrayList<>(1);
            one.add(item);
            if (filter(ctx, one, false).isEmpty()) {
                return true;
            }
            boolean more = listener.onItem(item);
            if (!more) {
                stop[0] = true;
            }
            return more;
        };
        XtreamApi.Kind[] kinds = {XtreamApi.Kind.MOVIE, XtreamApi.Kind.SERIES};
        if (isM3u(c)) {
            for (XtreamApi.Kind k : kinds) {
                if (stop[0]) {
                    return;
                }
                M3uApi.searchStream(c.m3uUrl, k, query, skipAdult, 100, wrap);
            }
            return;
        }
        // Xtream: filmes e séries pesquisados ao mesmo tempo (mais rápido).
        final Object lock = new Object();
        final XtreamApi.ResultListener safe = item -> {
            synchronized (lock) {
                return !stop[0] && wrap.onItem(item);
            }
        };
        final IOException[] errors = new IOException[kinds.length];
        Thread[] threads = new Thread[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            final int at = i;
            final XtreamApi.Kind k = kinds[i];
            threads[i] = new Thread(() -> {
                try {
                    SearchIndex.search(ctx, c.server, c.user, c.pass, k, query, skipAdult, 100,
                            cancel, safe);
                } catch (IOException e) {
                    errors[at] = e;
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (errors[0] != null && errors[1] != null) {
            throw errors[0];
        }
    }

    /**
     * Deixa o índice da busca pronto em segundo plano (prioridade baixa), alguns segundos
     * depois de a Início abrir. Assim, quando a pessoa pesquisa, já está tudo pronto.
     */
    static void warmSearchIndex(Context ctx) {
        final CredentialStore.Credentials c = creds(ctx);
        if (c == null || isM3u(c)) {
            return;
        }
        synchronized (Catalog.class) {
            if (searchWarmed) {
                return;
            }
            searchWarmed = true;
        }
        final Context app = ctx.getApplicationContext();
        for (final XtreamApi.Kind k : new XtreamApi.Kind[]{XtreamApi.Kind.MOVIE,
                XtreamApi.Kind.SERIES}) {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    return;
                }
                SearchIndex.warmUp(app, c.server, c.user, c.pass, k);
            });
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
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
