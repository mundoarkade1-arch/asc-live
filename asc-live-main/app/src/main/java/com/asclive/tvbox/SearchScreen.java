package com.asclive.tvbox;

import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Busca do topo da tela. A pessoa digita (SearchKeyboard) e a busca procura, de uma vez:
 * JOGOS (dia, hora e canais confirmados), TIMES (abre a página do time) e CANAIS (pelo nome,
 * sem acento; cada resolução aparece como um canal). Três abas mostram cada resultado.
 */
final class SearchScreen extends GroupGridScreen {

    private static final int MAX_RESULTS = 300;
    private static final int MAX_GAMES = 30;
    private static final int LOOKUP_GAMES = 12;
    private static final int TAB_GAMES = 0;
    private static final int TAB_TEAMS = 1;
    private static final int TAB_CHANNELS = 2;
    private static final String HINT = "Aperte OK em \"Digitar\" e escreva um time ou canal.";

    private final LinearLayout root;
    private final TextView button;
    private final TextView status;
    private final LinearLayout tabsRow;
    private final TextView[] tabs = new TextView[3];
    private final ScrollView gamesScroll;
    private final LinearLayout gamesBox;
    private final ScrollView teamsScroll;
    private final LinearLayout teamsBox;
    private String lastQuery = "";
    private int tab = TAB_CHANNELS;
    private int gameCount;
    private int teamCount;
    private int channelCount;
    private View firstGame;
    private View firstTeam;

    private static final class Found {
        List<ChannelGroup> channels = new ArrayList<>();
        List<Match> games = new ArrayList<>();
        List<Teams.Team> teams = new ArrayList<>();
    }

    SearchScreen(ShellActivity act) {
        super(act);
        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(act, 36), Ui.dp(act, 14), Ui.dp(act, 36), Ui.dp(act, 6));
        top.addView(Ui.title(act, "Buscar", 34, C.TEXT));
        button = Ui.button(act, "Digitar", Ui.ACCENT);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.leftMargin = Ui.dp(act, 28);
        top.addView(button, blp);
        status = Ui.text(act, HINT, 18, C.TEXT2);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
        slp.leftMargin = Ui.dp(act, 24);
        top.addView(status, slp);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        tabsRow = new LinearLayout(act);
        tabsRow.setPadding(Ui.dp(act, 36), 0, Ui.dp(act, 36), Ui.dp(act, 6));
        String[] names = {"Jogos", "Times", "Canais"};
        for (int i = 0; i < 3; i++) {
            final int at = i;
            tabs[i] = Ui.button(act, names[i], Ui.DARK);
            tabs[i].setOnClickListener(v -> selectTab(at, true));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(act, 10);
            tabsRow.addView(tabs[i], lp);
        }
        tabsRow.setVisibility(View.GONE);
        root.addView(tabsRow, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout content = new FrameLayout(act);
        gamesBox = new LinearLayout(act);
        gamesBox.setOrientation(LinearLayout.VERTICAL);
        gamesBox.setPadding(Ui.dp(act, 30), Ui.dp(act, 6), Ui.dp(act, 30), Ui.dp(act, 20));
        gamesScroll = new ScrollView(act);
        gamesScroll.setVerticalScrollBarEnabled(false);
        gamesScroll.addView(gamesBox, new ScrollView.LayoutParams(-1, -2));
        gamesScroll.setVisibility(View.GONE);
        content.addView(gamesScroll, new FrameLayout.LayoutParams(-1, -1));
        teamsBox = new LinearLayout(act);
        teamsBox.setOrientation(LinearLayout.VERTICAL);
        teamsBox.setPadding(Ui.dp(act, 30), Ui.dp(act, 6), Ui.dp(act, 30), Ui.dp(act, 20));
        teamsScroll = new ScrollView(act);
        teamsScroll.setVerticalScrollBarEnabled(false);
        teamsScroll.addView(teamsBox, new ScrollView.LayoutParams(-1, -2));
        teamsScroll.setVisibility(View.GONE);
        content.addView(teamsScroll, new FrameLayout.LayoutParams(-1, -1));
        content.addView(gridHolder, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        button.setOnClickListener(v -> askQuery());
        showNotice("Digite o nome de um time ou canal.\nExemplos: santos, flamengo, premiere, sportv.");
    }

    @Override
    View view() {
        return root;
    }

    @Override
    View firstFocus() {
        return button;
    }

    @Override
    void onShow() {
        act.focusReady(this);
    }

    /** Seta para cima na primeira linha dos resultados: volta para as abas (ou o botão). */
    @Override
    boolean handleUp(View focused) {
        if (focused == grid) {
            (tabsRow.getVisibility() == View.VISIBLE ? tabs[tab] : button).requestFocus();
            return true;
        }
        return false;
    }

    @Override
    void onResume() {
        adapter.notifyDataSetChanged();
    }

    private void askQuery() {
        SearchKeyboard.show(act, "Buscar time ou canal", new SearchKeyboard.Listener() {
            @Override
            public void onSearch(String text) {
                search(text);
            }

            @Override
            public void onCancel() {
            }
        });
    }

    private void search(final String text) {
        final String q = ChannelNames.queryKey(text);
        if (q.isEmpty()) {
            return;
        }
        lastQuery = text.trim();
        status.setText("Procurando \"" + lastQuery + "\"...");
        final String[] words = q.split(" ");
        bg(() -> {
            Found f = new Found();
            for (ChannelGroup g : Catalog.allGroups(act)) {
                boolean all = true;
                for (String w : words) {
                    if (!g.key().contains(w)) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    f.channels.add(g);
                    if (f.channels.size() >= MAX_RESULTS) {
                        break;
                    }
                }
            }
            try {
                f.teams = Teams.search(act, lastQuery);
            } catch (Exception ignored) {
                // sem lista de times agora: ficam os outros resultados
            }
            try {
                f.games = pickGames(Fixtures.search(act, lastQuery));
            } catch (Exception ignored) {
                // sem calendário agora
            }
            return f;
        }, new Bg.Done<Found>() {
            @Override
            public void ok(Found f) {
                show(f);
            }

            @Override
            public void fail(Exception e) {
                tabsRow.setVisibility(View.GONE);
                showGrid();
                showNotice(Screen.errorText(e));
                status.setText("");
            }
        });
    }

    /** Jogos que vão acontecer (ou estão rolando); se não há, os últimos encerrados. */
    private static List<Match> pickGames(List<Match> found) {
        List<Match> next = new ArrayList<>();
        List<Match> done = new ArrayList<>();
        for (Match m : found) {
            (m.status == Match.FINISHED ? done : next).add(m);
        }
        List<Match> out = new ArrayList<>();
        if (!next.isEmpty()) {
            for (Match m : next) {
                out.add(m);
                if (out.size() >= MAX_GAMES) break;
            }
        } else {
            for (int i = Math.max(0, done.size() - 5); i < done.size(); i++) {
                out.add(done.get(i));
            }
        }
        return out;
    }

    private void show(Found f) {
        gameCount = f.games.size();
        teamCount = f.teams.size();
        channelCount = f.channels.size();
        setGroups(f.channels, "Nenhum canal encontrado para \"" + lastQuery + "\".");
        buildGames(f.games);
        buildTeams(f.teams);
        tabs[TAB_GAMES].setText("Jogos (" + gameCount + ")");
        tabs[TAB_TEAMS].setText("Times (" + teamCount + ")");
        tabs[TAB_CHANNELS].setText("Canais (" + channelCount + ")");
        tabsRow.setVisibility(View.VISIBLE);
        status.setText(gameCount + teamCount + channelCount == 0
                ? "Nada encontrado para \"" + lastQuery + "\"." : "Resultados para \"" + lastQuery
                + "\"");
        selectTab(gameCount > 0 ? TAB_GAMES : (teamCount > 0 ? TAB_TEAMS : TAB_CHANNELS),
                gameCount + teamCount + channelCount > 0);
    }

    private void selectTab(int which, boolean focusContent) {
        tab = which;
        for (int i = 0; i < 3; i++) {
            tabs[i].setBackground(i == which
                    ? Ui.focusBg(act, C.ACCENT, C.ACCENT, 14, C.ACCENT, 0)
                    : Ui.focusBg(act, C.SURFACE, C.SURFACE_FOCUS, 14, C.LINE, Ui.dp(act, 1)));
        }
        gamesScroll.setVisibility(which == TAB_GAMES ? View.VISIBLE : View.GONE);
        teamsScroll.setVisibility(which == TAB_TEAMS ? View.VISIBLE : View.GONE);
        gridHolder.setVisibility(which == TAB_CHANNELS ? View.VISIBLE : View.GONE);
        if (!focusContent) {
            return;
        }
        if (which == TAB_GAMES && firstGame != null) {
            firstGame.requestFocus();
        } else if (which == TAB_TEAMS && firstTeam != null) {
            firstTeam.requestFocus();
        } else if (which == TAB_CHANNELS && hasChannels()) {
            grid.requestFocus();
        } else {
            tabs[which].requestFocus();
        }
    }

    private void showGrid() {
        gamesScroll.setVisibility(View.GONE);
        teamsScroll.setVisibility(View.GONE);
        gridHolder.setVisibility(View.VISIBLE);
    }

    // ---------- Jogos ----------

    private void buildGames(List<Match> games) {
        gamesBox.removeAllViews();
        firstGame = null;
        if (games.isEmpty()) {
            gamesBox.addView(Ui.text(act, "Nenhum jogo encontrado nas próximas semanas.",
                    18, C.TEXT2));
            return;
        }
        final Map<String, TextView> channelText = new HashMap<>();
        for (int i = 0; i < games.size(); i++) {
            final Match m = games.get(i);
            LinearLayout row = new LinearLayout(act);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackground(Ui.glassFocusBg(act, 14));
            row.setPadding(Ui.dp(act, 16), Ui.dp(act, 10), Ui.dp(act, 16), Ui.dp(act, 10));

            String d = new SimpleDateFormat("EEE dd/MM", new Locale("pt", "BR"))
                    .format(new Date(m.horario));
            String t = new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(new Date(m.horario));
            TextView when = Ui.title(act, d + "\n" + t, 16, C.TEXT);
            when.setLineSpacing(0, 1.1f);
            row.addView(when, new LinearLayout.LayoutParams(Ui.dp(act, 116), -2));

            LinearLayout words = new LinearLayout(act);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView teams = Ui.title(act, m.casa + "  x  " + m.fora, 18, C.TEXT);
            teams.setSingleLine(true);
            teams.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(teams);
            words.addView(Ui.text(act, m.liga + (m.status == Match.LIVE ? "  ·  AO VIVO"
                    : (m.status == Match.FINISHED ? "  ·  Encerrado " + m.placar : "")),
                    14, m.status == Match.LIVE ? C.GOLD : C.TEXT2));
            row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView ch = Ui.text(act, i < LOOKUP_GAMES && m.status != Match.FINISHED
                    ? "Procurando canais..." : (m.status == Match.FINISHED ? ""
                    : "OK para ver os canais"), 14, C.TEXT2);
            ch.setGravity(Gravity.END);
            ch.setMaxLines(2);
            ch.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(ch, new LinearLayout.LayoutParams(Ui.dp(act, 330), -2));
            channelText.put(m.id, ch);

            row.setOnClickListener(v -> {
                if (m.status != Match.FINISHED) MatchDialog.show(act, m);
            });
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = Ui.dp(act, 8);
            gamesBox.addView(row, rlp);
            if (firstGame == null) firstGame = row;
        }
        lookupChannels(games, channelText);
    }

    /** Escreve em cada jogo os canais CONFIRMADOS (ou que ainda não há). */
    private void lookupChannels(final List<Match> games, final Map<String, TextView> texts) {
        bg(() -> {
            Map<String, String> out = new LinkedHashMap<>();
            for (int i = 0; i < games.size() && i < LOOKUP_GAMES; i++) {
                Match m = games.get(i);
                if (m.status == Match.FINISHED) continue;
                List<String> names = new ArrayList<>();
                for (ChannelGroup g : Broadcasts.confirmedFor(act, m)) {
                    String n = ChannelNames.display(g.nome);
                    String base = ChannelNames.key(g.nome);
                    boolean dup = false;
                    for (String x : names) {
                        if (ChannelNames.key(x).equals(base)) {
                            dup = true;
                            break;
                        }
                    }
                    if (!dup) names.add(n);
                }
                out.put(m.id, join(names));
            }
            return out;
        }, new Bg.Done<Map<String, String>>() {
            @Override
            public void ok(Map<String, String> v) {
                for (Map.Entry<String, String> e : v.entrySet()) {
                    TextView t = texts.get(e.getKey());
                    if (t == null) continue;
                    if (e.getValue().isEmpty()) {
                        t.setText("Canal ainda não confirmado");
                        t.setTextColor(C.TEXT3);
                    } else {
                        t.setText("▶  " + e.getValue());
                        t.setTextColor(C.GOLD);
                    }
                }
            }

            @Override
            public void fail(Exception e) {
                for (TextView t : texts.values()) {
                    if ("Procurando canais...".contentEquals(t.getText())) {
                        t.setText("OK para ver os canais");
                    }
                }
            }
        });
    }

    private static String join(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size() && i < 3; i++) {
            if (i > 0) sb.append(", ");
            sb.append(names.get(i));
        }
        if (names.size() > 3) sb.append("  +").append(names.size() - 3);
        return sb.toString();
    }

    // ---------- Times ----------

    private void buildTeams(List<Teams.Team> found) {
        teamsBox.removeAllViews();
        firstTeam = null;
        if (found.isEmpty()) {
            teamsBox.addView(Ui.text(act, "Nenhum time encontrado.", 18, C.TEXT2));
            return;
        }
        float density = act.getResources().getDisplayMetrics().density;
        float widthDp = Ui.screenW(act) / density - Ui.SIDEBAR_DP - 30 - 20 - 60;
        int cols = Math.max(3, Math.min(8, (int) (widthDp / (TeamCard.W_DP + 10))));
        LinearLayout row = null;
        for (int i = 0; i < found.size() && i < 48; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(act);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
                rlp.bottomMargin = Ui.dp(act, 10);
                teamsBox.addView(row, rlp);
            }
            final Teams.Team t = found.get(i);
            final TeamCard c = new TeamCard(act, t, FavTeams.isFavorite(act, t.id),
                    t.id.equals(FavTeams.heartId(act)));
            c.setOnClickListener(v -> act.openTeam(t));
            c.setOnLongClickListener(v -> {
                boolean now = FavTeams.toggle(act, t);
                c.setStatus(now, t.id.equals(FavTeams.heartId(act)));
                Ui.toast(act, now ? t.name + " nos favoritos." : t.name + " saiu dos favoritos.");
                return true;
            });
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) c.getLayoutParams();
            lp.rightMargin = Ui.dp(act, 10);
            row.addView(c, lp);
            if (firstTeam == null) firstTeam = c;
        }
    }
}
