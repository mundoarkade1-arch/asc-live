package com.asclive.tvbox;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Página de um time (para o torcedor): botões de ★ favorito e ♥ time do coração, lista dos
 * próximos jogos e últimos resultados, e ao lado a escalação do jogo selecionado.
 * OK num jogo que ainda vai acontecer abre a janelinha com os canais confirmados.
 * Se a ESPN ainda não publicou a escalação do jogo, mostra a do último jogo como "provável".
 */
final class TeamView extends LinearLayout {

    interface Host {
        /** Voltar para a tela de Futebol. */
        void close();

        /** A estrelinha ou o ♥ mudou (a tela de Futebol precisa se redesenhar). */
        void changed();
    }

    private static final long LINEUP_DELAY_MS = 300L;

    private final ShellActivity act;
    private final Teams.Team team;
    private final Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final TextView favBtn;
    private final TextView heartBtn;
    private final LinearLayout gamesBox;
    private final LinearLayout lineupBox;
    private final List<Match> all = new ArrayList<>();
    private Bg.Handle gamesTask;
    private Bg.Handle lineupTask;
    private View firstRow;
    private Match shown;

    TeamView(ShellActivity act, Teams.Team team, Host host) {
        super(act);
        this.act = act;
        this.team = team;
        this.host = host;
        setOrientation(VERTICAL);

        // ---- Cabeçalho ----
        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(act, 6), Ui.dp(act, 4), Ui.dp(act, 6), Ui.dp(act, 8));
        ImageView shield = new ImageView(act);
        shield.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.load(shield, team.logo, 200);
        head.addView(shield, new LayoutParams(Ui.dp(act, 72), Ui.dp(act, 72)));
        LinearLayout words = new LinearLayout(act);
        words.setOrientation(VERTICAL);
        TextView name = Ui.title(act, team.name, 30, C.TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(name);
        words.addView(Ui.text(act, "Jogos, canais e escalação", 15, C.TEXT2));
        LayoutParams wlp = new LayoutParams(0, -2, 1f);
        wlp.leftMargin = Ui.dp(act, 16);
        head.addView(words, wlp);
        favBtn = Ui.button(act, "", Ui.DARK);
        favBtn.setOnClickListener(v -> {
            FavTeams.toggle(act, team);
            refreshButtons();
            host.changed();
        });
        head.addView(favBtn, buttonLp());
        heartBtn = Ui.button(act, "", Ui.DARK);
        heartBtn.setOnClickListener(v -> {
            if (team.id.equals(FavTeams.heartId(act))) {
                return;
            }
            FavTeams.setHeart(act, team);
            Ui.toast(act, team.name + " agora é o seu time do coração.");
            refreshButtons();
            host.changed();
        });
        head.addView(heartBtn, buttonLp());
        TextView back = Ui.button(act, "Voltar", Ui.DARK);
        back.setOnClickListener(v -> host.close());
        head.addView(back, buttonLp());
        addView(head, new LayoutParams(-1, -2));

        // ---- Corpo: jogos à esquerda, escalação à direita ----
        LinearLayout body = new LinearLayout(act);
        body.setOrientation(HORIZONTAL);
        body.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 8));

        ScrollView gamesScroll = new ScrollView(act);
        gamesScroll.setVerticalScrollBarEnabled(false);
        gamesScroll.setBackground(Ui.glass(act, 18));
        gamesBox = new LinearLayout(act);
        gamesBox.setOrientation(VERTICAL);
        gamesBox.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 12));
        gamesScroll.addView(gamesBox, new ScrollView.LayoutParams(-1, -2));
        LayoutParams glp = new LayoutParams(0, -1, 1.2f);
        glp.rightMargin = Ui.dp(act, 12);
        body.addView(gamesScroll, glp);

        ScrollView lineupScroll = new ScrollView(act);
        lineupScroll.setVerticalScrollBarEnabled(false);
        lineupScroll.setBackground(Ui.glass(act, 18));
        lineupBox = new LinearLayout(act);
        lineupBox.setOrientation(VERTICAL);
        lineupBox.setPadding(Ui.dp(act, 18), Ui.dp(act, 14), Ui.dp(act, 18), Ui.dp(act, 14));
        lineupScroll.addView(lineupBox, new ScrollView.LayoutParams(-1, -2));
        body.addView(lineupScroll, new LayoutParams(0, -1, 1f));
        addView(body, new LayoutParams(-1, 0, 1f));

        refreshButtons();
        gamesBox.addView(Ui.text(act, "Carregando jogos...", 16, C.TEXT2));
        lineupBox.addView(Ui.text(act, "Selecione um jogo para ver a escalação.", 16, C.TEXT2));
        loadGames();
    }

    private LayoutParams buttonLp() {
        LayoutParams lp = new LayoutParams(-2, -2);
        lp.leftMargin = Ui.dp(act, 10);
        return lp;
    }

    private void refreshButtons() {
        boolean fav = FavTeams.isFavorite(act, team.id);
        boolean heart = team.id.equals(FavTeams.heartId(act));
        favBtn.setText(fav ? "★  Favorito" : "☆  Favoritar");
        heartBtn.setText(heart ? "♥  Meu time" : "♥  Time do coração");
    }

    /** Onde começa o foco. */
    View firstFocus() {
        return firstRow != null ? firstRow : favBtn;
    }

    /** Cancela o que estiver carregando (a página foi fechada). */
    void release() {
        h.removeCallbacksAndMessages(null);
        if (gamesTask != null) {
            gamesTask.cancel();
        }
        if (lineupTask != null) {
            lineupTask.cancel();
        }
    }

    // ---------- Jogos ----------

    private void loadGames() {
        gamesTask = Bg.run(act, () -> Fixtures.forTeam(act, team), new Bg.Done<List<Match>>() {
            @Override
            public void ok(List<Match> v) {
                all.clear();
                all.addAll(v);
                renderGames();
            }

            @Override
            public void fail(Exception e) {
                gamesBox.removeAllViews();
                gamesBox.addView(Ui.text(act, "Não foi possível buscar os jogos agora.\n"
                        + "Confira a internet e tente de novo.", 16, C.TEXT2));
            }
        });
    }

    private void renderGames() {
        gamesBox.removeAllViews();
        firstRow = null;
        List<Match> next = new ArrayList<>();
        List<Match> done = new ArrayList<>();
        for (Match m : all) {
            (m.status == Match.FINISHED ? done : next).add(m);
        }
        Collections.sort(done, new Comparator<Match>() {
            @Override
            public int compare(Match a, Match b) {
                return a.horario < b.horario ? 1 : (a.horario > b.horario ? -1 : 0);
            }
        });
        if (next.isEmpty() && done.isEmpty()) {
            gamesBox.addView(Ui.text(act, "Nenhum jogo encontrado nas próximas semanas.",
                    16, C.TEXT2));
            lineupBox.removeAllViews();
            lineupBox.addView(Ui.text(act, "Sem jogos para mostrar a escalação.", 16, C.TEXT2));
            return;
        }
        if (!next.isEmpty()) {
            section("PRÓXIMOS JOGOS");
            for (Match m : next) {
                View r = row(m);
                if (firstRow == null) {
                    firstRow = r;
                }
            }
        }
        if (!done.isEmpty()) {
            section("ÚLTIMOS RESULTADOS");
            int n = 0;
            for (Match m : done) {
                View r = row(m);
                if (firstRow == null) {
                    firstRow = r;
                }
                if (++n >= 5) {
                    break;
                }
            }
        }
        Match pick = !next.isEmpty() ? next.get(0) : done.get(0);
        showLineup(pick, 0);
    }

    private void section(String text) {
        TextView t = Ui.text(act, text, 13, C.TEXT2);
        t.setTypeface(Ui.bold(act), Typeface.BOLD);
        LayoutParams lp = new LayoutParams(-1, -2);
        lp.topMargin = gamesBox.getChildCount() == 0 ? 0 : Ui.dp(act, 14);
        lp.bottomMargin = Ui.dp(act, 8);
        gamesBox.addView(t, lp);
    }

    private boolean isHome(Match m) {
        return team.id.equals(m.casaId) || (m.casaId.isEmpty()
                && ChannelNames.queryKey(team.name).equals(ChannelNames.queryKey(m.casa)));
    }

    private View row(final Match m) {
        final boolean home = isHome(m);
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(Ui.glassFocusBg(act, 12));
        row.setPadding(Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8));

        String d = new SimpleDateFormat("EEE dd/MM", new Locale("pt", "BR"))
                .format(new Date(m.horario));
        String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(m.horario));
        TextView when = Ui.text(act, d + "\n" + t, 14, C.TEXT);
        when.setLineSpacing(0, 1.1f);
        row.addView(when, new LayoutParams(Ui.dp(act, 92), -2));

        ImageView opp = new ImageView(act);
        opp.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.load(opp, home ? m.escudoFora : m.escudoCasa, 100);
        row.addView(opp, new LayoutParams(Ui.dp(act, 34), Ui.dp(act, 34)));

        LinearLayout words = new LinearLayout(act);
        words.setOrientation(VERTICAL);
        TextView vs = Ui.title(act, (home ? m.fora : m.casa), 16, C.TEXT);
        vs.setSingleLine(true);
        vs.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(vs);
        TextView sub = Ui.text(act, (home ? "Em casa" : "Fora") + "  ·  " + m.liga, 12, C.TEXT2);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(sub);
        LayoutParams wlp = new LayoutParams(0, -2, 1f);
        wlp.leftMargin = Ui.dp(act, 10);
        row.addView(words, wlp);

        TextView right = Ui.title(act, rightText(m, home), 15,
                m.status == Match.LIVE ? C.GOLD : C.TEXT);
        row.addView(right, new LayoutParams(-2, -2));

        LayoutParams rlp = new LayoutParams(-1, -2);
        rlp.bottomMargin = Ui.dp(act, 6);
        gamesBox.addView(row, rlp);
        row.setOnFocusChangeListener((v, has) -> {
            if (has) {
                showLineup(m, LINEUP_DELAY_MS);
            }
        });
        row.setOnClickListener(v -> {
            if (m.status == Match.FINISHED) {
                showLineup(m, 0);
            } else {
                MatchDialog.show(act, m);
            }
        });
        return row;
    }

    /** "AO VIVO 1 x 0", "V 2 x 1" (vitória), "E 1 x 1", "D 0 x 3" ou vazio. */
    private String rightText(Match m, boolean home) {
        if (m.status == Match.LIVE) {
            return "AO VIVO" + (m.placar.isEmpty() ? "" : "  " + m.placar);
        }
        if (m.status != Match.FINISHED) {
            return m.statusText == null ? "" : m.statusText;
        }
        if (m.placar.isEmpty()) {
            return "Encerrado";
        }
        try {
            String[] p = m.placar.split("x");
            int a = Integer.parseInt(p[0].trim());
            int b = Integer.parseInt(p[1].trim());
            int mine = home ? a : b;
            int his = home ? b : a;
            String r = mine > his ? "V" : (mine < his ? "D" : "E");
            return r + "  " + m.placar;
        } catch (Exception e) {
            return m.placar;
        }
    }

    // ---------- Escalação ----------

    private static final class LineupResult {
        final Lineups.Side side;
        final String note;

        LineupResult(Lineups.Side side, String note) {
            this.side = side;
            this.note = note;
        }
    }

    private void showLineup(final Match m, long delay) {
        if (m == shown && delay > 0) {
            return;
        }
        shown = m;
        h.removeCallbacksAndMessages(null);
        if (lineupTask != null) {
            lineupTask.cancel();
        }
        h.postDelayed(() -> loadLineup(m), delay);
    }

    private void loadLineup(final Match m) {
        lineupBox.removeAllViews();
        lineupBox.addView(Ui.text(act, "Buscando a escalação...", 16, C.TEXT2));
        lineupTask = Bg.run(act, () -> {
            Lineups.Side side = sideOf(Lineups.fetch(act, m));
            if (side != null) {
                return new LineupResult(side, m.status == Match.FINISHED
                        ? "Escalação do jogo" : "Escalação confirmada");
            }
            if (m.status != Match.FINISHED) {
                // Ainda não publicada: usa o último jogo encerrado do time como "provável".
                Match last = lastFinishedBefore(m);
                if (last != null) {
                    Lineups.Side old = sideOf(Lineups.fetch(act, last));
                    if (old != null) {
                        String adv = isHome(last) ? last.fora : last.casa;
                        return new LineupResult(old, "Provável (repete a do último jogo, contra "
                                + adv + ")");
                    }
                }
            }
            return new LineupResult(null, "");
        }, new Bg.Done<LineupResult>() {
            @Override
            public void ok(LineupResult r) {
                if (m != shown) {
                    return;
                }
                renderLineup(r);
            }

            @Override
            public void fail(Exception e) {
                if (m != shown) {
                    return;
                }
                lineupBox.removeAllViews();
                lineupBox.addView(Ui.text(act, "Não foi possível buscar a escalação agora.",
                        16, C.TEXT2));
            }
        });
    }

    private Lineups.Side sideOf(List<Lineups.Side> sides) {
        Lineups.Side s = Lineups.of(sides, team.id);
        if (s != null) {
            return s;
        }
        for (Lineups.Side x : sides) {
            if (ChannelNames.queryKey(x.teamName).equals(ChannelNames.queryKey(team.name))) {
                return x;
            }
        }
        return null;
    }

    private Match lastFinishedBefore(Match m) {
        Match best = null;
        for (Match x : all) {
            if (x.status == Match.FINISHED && x.horario < m.horario
                    && (best == null || x.horario > best.horario)) {
                best = x;
            }
        }
        return best;
    }

    private void renderLineup(LineupResult r) {
        lineupBox.removeAllViews();
        TextView title = Ui.text(act, "ESCALAÇÃO", 13, C.TEXT2);
        title.setTypeface(Ui.bold(act), Typeface.BOLD);
        lineupBox.addView(title);
        if (r.side == null) {
            TextView t = Ui.text(act, "A escalação ainda não foi divulgada.\n"
                    + "Ela costuma sair cerca de 1 hora antes do jogo.", 16, C.TEXT2);
            t.setLineSpacing(0, 1.15f);
            LayoutParams lp = new LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(act, 10);
            lineupBox.addView(t, lp);
            return;
        }
        TextView note = Ui.text(act, r.note, 14, C.GOLD);
        note.setLineSpacing(0, 1.1f);
        LayoutParams nlp = new LayoutParams(-1, -2);
        nlp.topMargin = Ui.dp(act, 6);
        lineupBox.addView(note, nlp);
        if (!r.side.formation.isEmpty()) {
            TextView f = Ui.title(act, "Formação " + r.side.formation, 20, C.TEXT);
            LayoutParams flp = new LayoutParams(-1, -2);
            flp.topMargin = Ui.dp(act, 8);
            lineupBox.addView(f, flp);
        }
        for (Lineups.Player p : r.side.starters) {
            LinearLayout line = new LinearLayout(act);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView num = Ui.title(act, p.number, 15, C.GOLD);
            num.setGravity(Gravity.CENTER);
            line.addView(num, new LayoutParams(Ui.dp(act, 34), -2));
            TextView nm = Ui.text(act, p.name, 16, C.TEXT);
            nm.setSingleLine(true);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(nm, new LayoutParams(0, -2, 1f));
            line.addView(Ui.text(act, p.position, 13, C.TEXT2), new LayoutParams(-2, -2));
            LayoutParams llp = new LayoutParams(-1, -2);
            llp.topMargin = Ui.dp(act, 5);
            lineupBox.addView(line, llp);
        }
        if (!r.side.subs.isEmpty()) {
            TextView sl = Ui.text(act, "RESERVAS", 13, C.TEXT2);
            sl.setTypeface(Ui.bold(act), Typeface.BOLD);
            LayoutParams slp = new LayoutParams(-1, -2);
            slp.topMargin = Ui.dp(act, 14);
            lineupBox.addView(sl, slp);
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (Lineups.Player p : r.side.subs) {
                if (n++ > 0) {
                    sb.append(", ");
                }
                sb.append(p.name);
                if (n >= 12) {
                    break;
                }
            }
            TextView subs = Ui.text(act, sb.toString(), 14, C.TEXT2);
            subs.setLineSpacing(0, 1.15f);
            LayoutParams sbl = new LayoutParams(-1, -2);
            sbl.topMargin = Ui.dp(act, 4);
            lineupBox.addView(subs, sbl);
        }
    }
}
