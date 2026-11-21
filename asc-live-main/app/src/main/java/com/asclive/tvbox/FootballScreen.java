package com.asclive.tvbox;

import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Futebol para o torcedor. Uma tela que rola para baixo:
 * 1) o time do coração (cartão grande com o próximo jogo) ou o convite para escolher um;
 * 2) jogos em destaque de hoje (OK abre a janelinha com os canais confirmados);
 * 3) meus times (os com ★);
 * 4) outros times: OK abre a página do time (jogos, canais, escalação); segurar OK marca a ★.
 * Dentro da página do time, Voltar (ou o botão Voltar) volta para cá.
 */
final class FootballScreen extends Screen implements TeamView.Host {

    private static final int FIRST_TEAMS = 24;
    private static final int MORE_TEAMS = 36;

    private final FrameLayout root;
    private final ScrollView hubScroll;
    private final LinearLayout hub;
    private final FrameLayout teamHolder;
    private final TextView subtitle;

    private final List<Match> matches = new ArrayList<>();
    private List<Teams.Team> teams = new ArrayList<>();
    private List<Match> heartGames = new ArrayList<>();
    private final Map<String, TeamCard> cards = new HashMap<>();
    private final Map<String, View> keyed = new HashMap<>();
    private long lastLoad;
    private boolean matchesLoaded;
    private boolean matchesError;
    private boolean teamsLoaded;
    private boolean teamsError;
    private boolean loading;
    private int teamsShown = FIRST_TEAMS;
    private String teamFilter = "";
    private List<Teams.Team> filtered;

    private View first;
    private TeamView teamView;
    private String lastTeamId = "";

    FootballScreen(ShellActivity act) {
        super(act);
        root = new FrameLayout(act);
        hubScroll = new ScrollView(act);
        hubScroll.setVerticalScrollBarEnabled(false);
        hubScroll.setClipToPadding(false);
        hub = new LinearLayout(act);
        hub.setOrientation(LinearLayout.VERTICAL);
        hub.setPadding(Ui.dp(act, 8), Ui.dp(act, 8), Ui.dp(act, 8), Ui.dp(act, 30));
        hubScroll.addView(hub, new ScrollView.LayoutParams(-1, -2));
        root.addView(hubScroll, new FrameLayout.LayoutParams(-1, -1));
        teamHolder = new FrameLayout(act);
        teamHolder.setVisibility(View.GONE);
        root.addView(teamHolder, new FrameLayout.LayoutParams(-1, -1));
        subtitle = Ui.text(act, "", 15, C.TEXT2);
        renderHub();
    }

    @Override View view() { return root; }

    @Override View firstFocus() {
        if (teamView != null) return teamView.firstFocus();
        return first;
    }

    @Override String hints() { return ""; }

    @Override void onShow() {
        subtitle.setText(new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR"))
                .format(new Date()));
        load();
    }

    @Override void onResume() {
        // Voltando do player: só recarrega se os dados já estão velhos (evita piscar a tela).
        if (teamView == null && System.currentTimeMillis() - lastLoad > 60000L) load();
    }

    @Override void onHide() {
        super.onHide();
        if (teamView != null) teamView.release();
    }

    @Override boolean handleBack() {
        if (teamView != null) {
            close();
            return true;
        }
        return false;
    }

    // ---------- Carregar ----------

    private static final class Data {
        List<Match> matches = new ArrayList<>();
        boolean matchesError;
        List<Teams.Team> teams = new ArrayList<>();
        boolean teamsError;
    }

    private void load() {
        if (loading) return;
        loading = true;
        lastLoad = System.currentTimeMillis();
        bg(() -> {
            Data d = new Data();
            try {
                FootballApi.Result r = FootballApi.todayResult(act);
                d.matches = ListGames.merge(act, r.matches);
                d.matchesError = r.error && d.matches.isEmpty();
            } catch (Exception e) {
                d.matchesError = true;
            }
            try {
                d.teams = Teams.all(act);
            } catch (Exception e) {
                d.teamsError = true;
            }
            return d;
        }, new Bg.Done<Data>() {
            @Override public void ok(Data d) {
                loading = false;
                matches.clear();
                matches.addAll(d.matches);
                teams = d.teams;
                matchesLoaded = true;
                matchesError = d.matchesError;
                teamsLoaded = true;
                teamsError = d.teamsError;
                String key = teamView == null ? focusKey() : "";
                renderHub();
                restoreFocus(key);
                act.focusReady(FootballScreen.this);
            }
            @Override public void fail(Exception e) {
                loading = false;
                matchesLoaded = true;
                matchesError = true;
                teamsLoaded = true;
                teamsError = true;
                renderHub();
            }
        });
        loadHeartGames();
    }

    private void loadHeartGames() {
        final Teams.Team heart = FavTeams.heart(act);
        if (heart == null) {
            heartGames = new ArrayList<>();
            return;
        }
        bg(() -> Fixtures.forTeam(act, heart), new Bg.Done<List<Match>>() {
            @Override public void ok(List<Match> v) {
                heartGames = v;
                if (teamView == null) {
                    String key = focusKey();
                    renderHub();
                    restoreFocus(key);
                }
            }
            @Override public void fail(Exception e) {
                // fica sem "próximo jogo"; o resto da tela continua
            }
        });
    }

    // ---------- Montar a tela ----------

    private void renderHub() {
        hub.removeAllViews();
        cards.clear();
        keyed.clear();
        first = null;

        LinearLayout heading = new LinearLayout(act);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.title(act, "FUTEBOL", 27, C.TEXT);
        title.setTypeface(Ui.bold(act), Typeface.BOLD);
        heading.addView(title);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
        slp.leftMargin = Ui.dp(act, 18);
        if (subtitle.getParent() != null) {
            ((android.view.ViewGroup) subtitle.getParent()).removeView(subtitle);
        }
        heading.addView(subtitle, slp);
        hub.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        Teams.Team heart = FavTeams.heart(act);
        if (heart == null) {
            addInvite();
        } else {
            addHero(heart);
        }

        label("JOGOS EM DESTAQUE");
        addFeatured();

        List<Teams.Team> favs = FavTeams.list(act);
        if (!favs.isEmpty()) {
            label("MEUS TIMES");
            HorizontalScrollView rail = new HorizontalScrollView(act);
            rail.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(act);
            row.setPadding(0, Ui.dp(act, 4), Ui.dp(act, 12), Ui.dp(act, 4));
            for (Teams.Team t : favs) {
                TeamCard c = teamCard(t);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) c.getLayoutParams();
                lp.rightMargin = Ui.dp(act, 10);
                row.addView(c, lp);
            }
            rail.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
            hub.addView(rail, new LinearLayout.LayoutParams(-1, -2));
        }

        addOthers(favs);
    }

    private void label(String text) {
        TextView t = Ui.text(act, text, 13, C.TEXT2);
        t.setTypeface(Ui.bold(act), Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(act, 20);
        lp.bottomMargin = Ui.dp(act, 8);
        hub.addView(t, lp);
    }

    /** Convite quando ainda não há time do coração. */
    private void addInvite() {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.glass(act, 20));
        card.setPadding(Ui.dp(act, 24), Ui.dp(act, 18), Ui.dp(act, 24), Ui.dp(act, 18));
        card.addView(Ui.title(act, "★  Escolha o seu time do coração", 24, C.TEXT));
        TextView t = Ui.text(act, "Ele vira a tela principal: próximo jogo, canais e escalação."
                + "\nBusque o time ou escolha na lista lá embaixo (segure OK para marcar a ★).",
                15, C.TEXT2);
        t.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = Ui.dp(act, 6);
        card.addView(t, tlp);
        TextView find = Ui.button(act, "Buscar meu time", Ui.ACCENT);
        find.setOnClickListener(v -> askTeam());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = Ui.dp(act, 14);
        card.addView(find, blp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(act, 12);
        hub.addView(card, lp);
        first = find;
    }

    /** Cartão grande do time do coração. */
    private void addHero(final Teams.Team heart) {
        LinearLayout card = new LinearLayout(act);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(Ui.glassFocusBg(act, 20));
        card.setPadding(Ui.dp(act, 22), Ui.dp(act, 16), Ui.dp(act, 22), Ui.dp(act, 16));
        ImageView shield = new ImageView(act);
        shield.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.load(shield, heart.logo, 240);
        card.addView(shield, new LinearLayout.LayoutParams(Ui.dp(act, 92), Ui.dp(act, 92)));
        LinearLayout words = new LinearLayout(act);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView tag = Ui.text(act, "♥  MEU TIME", 13, C.GOLD);
        tag.setTypeface(Ui.bold(act), Typeface.BOLD);
        words.addView(tag);
        TextView name = Ui.title(act, heart.name, 30, C.TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(name);
        TextView next = Ui.text(act, nextGameText(heart), 16, C.TEXT2);
        next.setLineSpacing(0, 1.15f);
        words.addView(next);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, -2, 1f);
        wlp.leftMargin = Ui.dp(act, 20);
        card.addView(words, wlp);
        TextView go = Ui.text(act, "Jogos, canais e escalação  ›", 16, C.GOLD);
        card.addView(go, new LinearLayout.LayoutParams(-2, -2));
        card.setOnClickListener(v -> openTeam(heart));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(act, 12);
        hub.addView(card, lp);
        keyed.put("hero", card);
        first = card;
    }

    private String nextGameText(Teams.Team heart) {
        Match next = null;
        for (Match m : heartGames) {
            if (m.status != Match.FINISHED && (next == null || m.horario < next.horario)) {
                next = m;
            }
        }
        if (next == null) {
            return heartGames.isEmpty() ? "Buscando o próximo jogo..." : "Sem jogos marcados.";
        }
        boolean home = heart.id.equals(next.casaId);
        String when = new SimpleDateFormat("EEE, dd/MM 'às' HH:mm", new Locale("pt", "BR"))
                .format(new Date(next.horario));
        String adv = home ? next.fora : next.casa;
        return (next.isLive() ? "AO VIVO agora: " : "Próximo jogo: ") + when + "\n"
                + (home ? "vs " : "@ ") + adv + "  ·  " + next.liga;
    }

    /** Fileira de jogos de hoje. */
    private void addFeatured() {
        if (!matchesLoaded) {
            hub.addView(Skeleton.matchRail(act), new LinearLayout.LayoutParams(-1,
                    Ui.dp(act, 190)));
            return;
        }
        if (matchesError || matches.isEmpty()) {
            TextView t = Ui.text(act, matchesError ? "Sem conexão para buscar os jogos."
                    : "Nenhum jogo hoje.", 17, C.TEXT2);
            hub.addView(t, new LinearLayout.LayoutParams(-1, -2));
            if (matchesError) {
                TextView retry = Ui.button(act, "Tentar de novo", Ui.DARK);
                retry.setOnClickListener(v -> {
                    matchesLoaded = false;
                    renderHub();
                    load();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.topMargin = Ui.dp(act, 8);
                hub.addView(retry, lp);
                if (first == null) first = retry;
            }
            return;
        }
        HorizontalScrollView rail = new HorizontalScrollView(act);
        rail.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(act);
        row.setPadding(0, Ui.dp(act, 4), Ui.dp(act, 12), Ui.dp(act, 4));
        List<Match> sorted = new ArrayList<>();
        List<Match> others = new ArrayList<>();
        for (Match m : matches) {
            (involvesFavorite(m) ? sorted : others).add(m);
        }
        sorted.addAll(others);
        for (Match m : sorted) {
            View c = matchCard(m);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(act, 282),
                    Ui.dp(act, 176));
            lp.rightMargin = Ui.dp(act, 10);
            row.addView(c, lp);
            if (first == null) first = c;
        }
        rail.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
        hub.addView(rail, new LinearLayout.LayoutParams(-1, -2));
    }

    private boolean involvesFavorite(Match m) {
        for (Teams.Team t : FavTeams.list(act)) {
            if (m.hasTeam(t.id)) return true;
        }
        return false;
    }

    private View matchCard(final Match match) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 10));
        card.setBackground(Ui.glassFocusBg(act, 16));
        TextView league = Ui.text(act, (involvesFavorite(match) ? "★  " : "") + match.liga,
                12, C.TEXT2);
        league.setGravity(Gravity.CENTER);
        league.setMaxLines(1);
        league.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(league, new LinearLayout.LayoutParams(-1, Ui.dp(act, 22)));
        LinearLayout teamsRow = new LinearLayout(act);
        teamsRow.setGravity(Gravity.CENTER);
        addShield(teamsRow, match.escudoCasa, 42);
        teamsRow.addView(Ui.title(act, "  X  ", 17, C.TEXT2));
        addShield(teamsRow, match.escudoFora, 42);
        card.addView(teamsRow, new LinearLayout.LayoutParams(-1, Ui.dp(act, 50)));
        TextView names = Ui.text(act, match.casa + "  x  " + match.fora, 15, C.TEXT);
        names.setGravity(Gravity.CENTER);
        names.setMaxLines(2);
        names.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(names, new LinearLayout.LayoutParams(-1, Ui.dp(act, 42)));
        TextView status = Ui.text(act, statusText(match), 14,
                match.status == Match.LIVE ? C.GOLD : C.TEXT2);
        status.setGravity(Gravity.CENTER);
        card.addView(status, new LinearLayout.LayoutParams(-1, Ui.dp(act, 26)));
        card.setOnClickListener(v -> MatchDialog.show(act, match));
        keyed.put("m:" + match.id, card);
        return card;
    }

    private void addShield(LinearLayout row, String url, int size) {
        ImageView shield = new ImageView(act);
        shield.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.load(shield, url, 90);
        row.addView(shield, new LinearLayout.LayoutParams(Ui.dp(act, size), Ui.dp(act, size)));
    }

    private String statusText(Match m) {
        if (m.status == Match.LIVE) return (m.statusText == null || m.statusText.isEmpty()
                ? "AO VIVO" : m.statusText) + (m.placar.isEmpty() ? "" : "  " + m.placar);
        if (m.status == Match.FINISHED) return m.placar.isEmpty() ? "Encerrado" : "Final  " + m.placar;
        if (m.statusText != null && !m.statusText.isEmpty()) return m.statusText;
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(m.horario));
    }

    // ---------- Outros times ----------

    private int columns() {
        float density = act.getResources().getDisplayMetrics().density;
        float widthDp = Ui.screenW(act) / density - Ui.SIDEBAR_DP - 30 - 20 - 20;
        return Math.max(3, Math.min(8, (int) (widthDp / (TeamCard.W_DP + 10))));
    }

    private void addOthers(List<Teams.Team> favs) {
        boolean searching = !teamFilter.isEmpty();
        label(searching ? "RESULTADOS PARA \"" + teamFilter.toUpperCase(Locale.getDefault()) + "\""
                : "OUTROS TIMES");
        LinearLayout bar = new LinearLayout(act);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView find = Ui.button(act, "Buscar time", Ui.DARK);
        find.setOnClickListener(v -> askTeam());
        bar.addView(find, new LinearLayout.LayoutParams(-2, -2));
        if (searching) {
            TextView clear = Ui.button(act, "Limpar busca", Ui.DARK);
            clear.setOnClickListener(v -> {
                teamFilter = "";
                filtered = null;
                teamsShown = FIRST_TEAMS;
                renderHub();
                if (first != null) first.requestFocus();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.leftMargin = Ui.dp(act, 10);
            bar.addView(clear, clp);
        }
        TextView hint = Ui.text(act, "OK abre o time  ·  segure OK para marcar a ★", 14,
                C.TEXT3);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.leftMargin = Ui.dp(act, 16);
        bar.addView(hint, hlp);
        hub.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        if (first == null) first = find;

        if (!teamsLoaded) {
            hub.addView(Skeleton.matchRail(act), new LinearLayout.LayoutParams(-1,
                    Ui.dp(act, 150)));
            return;
        }
        if (teamsError && teams.isEmpty()) {
            TextView t = Ui.text(act, "Não foi possível carregar a lista de times agora.",
                    16, C.TEXT2);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(act, 12);
            hub.addView(t, lp);
            return;
        }
        List<Teams.Team> pool = new ArrayList<>();
        for (Teams.Team t : searching && filtered != null ? filtered : teams) {
            boolean fav = false;
            for (Teams.Team f : favs) {
                if (f.id.equals(t.id)) {
                    fav = true;
                    break;
                }
            }
            if (!fav || searching) pool.add(t);
        }
        if (pool.isEmpty()) {
            TextView t = Ui.text(act, searching ? "Nenhum time encontrado."
                    : "Todos os times já estão nos seus favoritos.", 16, C.TEXT2);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(act, 12);
            hub.addView(t, lp);
            return;
        }
        int cols = columns();
        int count = Math.min(pool.size(), teamsShown);
        LinearLayout row = null;
        for (int i = 0; i < count; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(act);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
                rlp.topMargin = Ui.dp(act, 10);
                hub.addView(row, rlp);
            }
            TeamCard c = teamCard(pool.get(i));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) c.getLayoutParams();
            lp.rightMargin = Ui.dp(act, 10);
            row.addView(c, lp);
        }
        if (pool.size() > count) {
            TextView more = Ui.button(act, "Mostrar mais times  (" + (pool.size() - count) + ")",
                    Ui.DARK);
            more.setOnClickListener(v -> {
                teamsShown += MORE_TEAMS;
                renderHub();
                // O foco volta para o primeiro time novo.
                TeamCard c = cards.get(pool.get(Math.min(count, pool.size() - 1)).id);
                if (c != null) c.requestFocus();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = Ui.dp(act, 14);
            hub.addView(more, lp);
        }
    }

    private TeamCard teamCard(final Teams.Team t) {
        final String heartId = FavTeams.heartId(act);
        TeamCard c = new TeamCard(act, t, FavTeams.isFavorite(act, t.id), t.id.equals(heartId));
        c.setOnClickListener(v -> openTeam(t));
        c.setOnLongClickListener(v -> {
            boolean now = FavTeams.toggle(act, t);
            Ui.toast(act, now ? t.name + " nos favoritos." : t.name + " saiu dos favoritos.");
            lastTeamId = t.id;
            loadHeartGames();
            renderHub();
            focusLast();
            return true;
        });
        cards.put(t.id, c);
        return c;
    }

    /** Quem tem o foco agora (para devolver depois de redesenhar a tela). */
    private String focusKey() {
        View f = root.findFocus();
        if (f == null) return "";
        if (f instanceof TeamCard) return "t:" + ((TeamCard) f).team.id;
        for (Map.Entry<String, View> e : keyed.entrySet()) {
            if (e.getValue() == f) return e.getKey();
        }
        return "";
    }

    private void restoreFocus(String key) {
        if (key.isEmpty()) return;
        View v = key.startsWith("t:") ? cards.get(key.substring(2)) : keyed.get(key);
        if (v != null) v.requestFocus();
    }

    private void focusLast() {
        TeamCard c = cards.get(lastTeamId);
        View target = c != null ? c : first;
        if (target != null) target.requestFocus();
    }

    // ---------- Buscar time ----------

    private void askTeam() {
        SearchKeyboard.show(act, "Buscar time", new SearchKeyboard.Listener() {
            @Override public void onSearch(String text) {
                final String q = text.trim();
                bg(() -> Teams.search(act, q), new Bg.Done<List<Teams.Team>>() {
                    @Override public void ok(List<Teams.Team> v) {
                        teamFilter = q;
                        filtered = v;
                        teamsShown = FIRST_TEAMS;
                        renderHub();
                        if (!v.isEmpty()) {
                            TeamCard c = cards.get(v.get(0).id);
                            if (c != null) c.requestFocus();
                        }
                    }
                    @Override public void fail(Exception e) {
                        Ui.toast(act, "Não foi possível buscar times agora.");
                    }
                });
            }
            @Override public void onCancel() { }
        });
    }

    // ---------- Página do time ----------

    void openTeam(Teams.Team team) {
        if (teamView != null) {
            teamView.release();
        }
        lastTeamId = team.id;
        teamView = new TeamView(act, team, this);
        teamHolder.removeAllViews();
        teamHolder.addView(teamView, new FrameLayout.LayoutParams(-1, -1));
        hubScroll.setVisibility(View.GONE);
        teamHolder.setVisibility(View.VISIBLE);
        teamView.post(() -> {
            if (teamView != null) teamView.firstFocus().requestFocus();
        });
    }

    @Override public void close() {
        if (teamView == null) return;
        teamView.release();
        teamView = null;
        teamHolder.removeAllViews();
        teamHolder.setVisibility(View.GONE);
        hubScroll.setVisibility(View.VISIBLE);
        renderHub();
        hub.post(this::focusLast);
    }

    @Override public void changed() {
        loadHeartGames();
    }
}
