package com.asclive.tvbox;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Jogos do dia em cards compactos, agrupados por campeonato e com canais no prÃ³prio card. */
final class FootballScreen extends Screen {

    private final FrameLayout root;
    private final LinearLayout cardsBox;
    private final TextView notice;
    private final TextView retry;
    private final TextView subtitle;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Match> matches = new ArrayList<>();
    private boolean loading;
    private View first;

    FootballScreen(ShellActivity act) {
        super(act);
        root = new FrameLayout(act);
        LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(act, 8), Ui.dp(act, 8), Ui.dp(act, 8), Ui.dp(act, 8));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout heading = new LinearLayout(act);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.title(act, "Jogos do Dia", 30, C.TEXT);
        title.setTypeface(Ui.bold(act), Typeface.BOLD);
        heading.addView(title);
        subtitle = Ui.text(act, "", 16, C.TEXT2);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
        slp.leftMargin = Ui.dp(act, 18);
        heading.addView(subtitle, slp);
        content.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(act);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        cardsBox = new LinearLayout(act);
        cardsBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(-1, -2);
        boxLp.topMargin = Ui.dp(act, 12);
        scroll.addView(cardsBox, boxLp);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        notice = Ui.text(act, "", 20, C.TEXT2);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(Ui.dp(act, 48), 0, Ui.dp(act, 48), 0);
        root.addView(notice, new FrameLayout.LayoutParams(-1, -1));
        retry = Ui.button(act, "Tentar de novo", Ui.DARK);
        retry.setVisibility(View.GONE);
        FrameLayout.LayoutParams retryLp = new FrameLayout.LayoutParams(-2, Ui.dp(act, 52));
        retryLp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
        retryLp.topMargin = Ui.dp(act, 110);
        root.addView(retry, retryLp);
        retry.setOnClickListener(v -> load());
    }

    @Override
    View view() {
        return root;
    }

    @Override
    View firstFocus() {
        return first;
    }

    @Override
    String hints() {
        return "";
    }

    @Override
    void onShow() {
        subtitle.setText(new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR"))
                .format(new Date()));
        load();
    }

    @Override
    void onResume() {
        load();
    }

    @Override
    void onHide() {
        super.onHide();
        main.removeCallbacksAndMessages(null);
    }

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        if (matches.isEmpty()) {
            showNotice("Buscando os jogos de hoje...");
        }
        bg(() -> FootballApi.todayResult(act), new Bg.Done<FootballApi.Result>() {
            @Override
            public void ok(FootballApi.Result result) {
                loading = false;
                if (result.error) {
                    showError();
                } else {
                    render(ListGames.merge(act, result.matches));
                }
            }

            @Override
            public void fail(Exception e) {
                loading = false;
                showError();
                if (matches.isEmpty()) {
                    showNotice("NÃ£o foi possÃ­vel carregar os jogos.\nTente Atualizar.");
                }
            }
        });
    }

    private void render(List<Match> value) {
        matches.clear();
        if (value != null) {
            matches.addAll(value);
        }
        cardsBox.removeAllViews();
        first = null;
        if (matches.isEmpty()) {
            showNotice("Sem jogos hoje.");
            return;
        }
        notice.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);

        Map<String, List<Match>> byLeague = new LinkedHashMap<>();
        for (Match match : matches) {
            String league = match.liga == null || match.liga.isEmpty() ? "Futebol" : match.liga;
            List<Match> list = byLeague.get(league);
            if (list == null) {
                list = new ArrayList<>();
                byLeague.put(league, list);
            }
            list.add(match);
        }
        for (Map.Entry<String, List<Match>> group : byLeague.entrySet()) {
            TextView league = Ui.title(act, group.getKey(), 20, C.TEXT);
            league.setTypeface(Ui.bold(act), Typeface.BOLD);
            league.setPadding(Ui.dp(act, 4), Ui.dp(act, 8), 0, Ui.dp(act, 4));
            cardsBox.addView(league, new LinearLayout.LayoutParams(-1, -2));
            for (Match match : group.getValue()) {
                addMatchCard(match);
            }
        }
        if (first != null) {
            act.focusReady(this);
        }
    }

    private void addMatchCard(final Match match) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(act, 16), Ui.dp(act, 12), Ui.dp(act, 16), Ui.dp(act, 12));
        card.setBackground(Ui.glass(act, 16));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.bottomMargin = Ui.dp(act, 10);
        cardsBox.addView(card, clp);

        LinearLayout matchLine = new LinearLayout(act);
        matchLine.setGravity(Gravity.CENTER_VERTICAL);
        matchLine.setFocusable(true);
        matchLine.setClickable(true);
        matchLine.setPadding(Ui.dp(act, 4), Ui.dp(act, 2), Ui.dp(act, 4), Ui.dp(act, 8));
        matchLine.setBackground(Ui.focusBg(act, 0x00000000, C.SURFACE_FOCUS, 12, C.LIME,
                Ui.dp(act, 2)));
        addShield(matchLine, match.escudoCasa);
        TextView teams = Ui.title(act, match.casa + "  x  " + match.fora, 20, C.TEXT);
        teams.setSingleLine(true);
        teams.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(act, 12);
        matchLine.addView(teams, tlp);
        TextView status = Ui.text(act, statusText(match), 17,
                match.status == Match.LIVE ? C.LIME : C.TEXT2);
        status.setGravity(Gravity.END);
        matchLine.addView(status, new LinearLayout.LayoutParams(Ui.dp(act, 150), -2));
        addShield(matchLine, match.escudoFora);
        card.addView(matchLine, new LinearLayout.LayoutParams(-1, -2));
        if (first == null) {
            first = matchLine;
        }

        LinearLayout channels = new LinearLayout(act);
        channels.setOrientation(LinearLayout.VERTICAL);
        TextView loading = Ui.text(act, "Procurando canais...", 14, C.TEXT2);
        channels.addView(loading);
        card.addView(channels, new LinearLayout.LayoutParams(-1, -2));
        loadChannels(match, channels);
    }

    private void addShield(LinearLayout row, String url) {
        ImageView shield = new ImageView(act);
        shield.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.load(shield, url, 90);
        row.addView(shield, new LinearLayout.LayoutParams(Ui.dp(act, 38), Ui.dp(act, 38)));
    }

    private String statusText(Match m) {
        if (m.status == Match.LIVE) {
            return (m.statusText == null || m.statusText.isEmpty() ? "AO VIVO" : m.statusText)
                    + (m.placar.isEmpty() ? "" : "  " + m.placar);
        }
        if (m.status == Match.FINISHED) {
            return m.placar.isEmpty() ? "Encerrado" : "Final  " + m.placar;
        }
        if (m.statusText != null && !m.statusText.isEmpty()) {
            return m.statusText;
        }
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(m.horario));
    }

    private void loadChannels(final Match match, final LinearLayout target) {
        bg(() -> Broadcasts.channelsFor(act, match), new Bg.Done<List<Broadcasts.Broadcast>>() {
            @Override
            public void ok(List<Broadcasts.Broadcast> groups) {
                target.removeAllViews();
                int count = 0;
                for (Broadcasts.Broadcast group : groups) {
                    TextView label = Ui.text(act, group.emissora, 14, C.TEXT2);
                    label.setTypeface(Ui.bold(act), Typeface.BOLD);
                    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
                    llp.topMargin = Ui.dp(act, 6);
                    target.addView(label, llp);
                    for (ChannelGroup channel : group.canais) {
                        addChannel(target, channel);
                        count++;
                    }
                }
                if (count == 0) {
                    target.addView(Ui.text(act, "Nenhum canal correspondente na sua lista.", 14,
                            C.TEXT3));
                }
            }

            @Override
            public void fail(Exception e) {
                target.removeAllViews();
                target.addView(Ui.text(act, "NÃ£o foi possÃ­vel encontrar os canais.", 14, C.ERROR));
            }
        });
    }

    private void addChannel(LinearLayout target, final ChannelGroup channel) {
        ChannelRow row = new ChannelRow(act, false);
        row.bind(0, channel, Favorites.isFavorite(act, channel.key()), "Abrir canal");
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(Ui.focusBg(act, C.SURFACE, C.SURFACE_FOCUS, 12, C.LIME,
                Ui.dp(act, 2)));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, Ui.dp(act, 58));
        rlp.topMargin = Ui.dp(act, 4);
        target.addView(row, rlp);
        row.setOnClickListener(v -> play(channel));
    }

    private void play(ChannelGroup channel) {
        List<ChannelGroup> list = new ArrayList<>();
        list.add(channel);
        LiveSession.set(list, 0, -1);
        act.startActivity(new Intent(act, LivePlayerActivity.class));
    }

    private void showNotice(String text) {
        notice.setText(text);
        notice.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
    }

    private void showError() {
        if (!matches.isEmpty()) return;
        notice.setText("Sem conexão para buscar os jogos.");
        notice.setVisibility(View.VISIBLE);
        retry.setVisibility(View.VISIBLE);
    }
}
