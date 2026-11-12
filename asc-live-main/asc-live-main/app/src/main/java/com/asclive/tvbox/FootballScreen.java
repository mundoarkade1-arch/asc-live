package com.asclive.tvbox;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
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

/** Tela inicial esportiva: jogos em faixa horizontal e destaque/canais abaixo. */
final class FootballScreen extends Screen {
    private final FrameLayout root;
    private final LinearLayout cardsBox;
    private final TextView notice;
    private final TextView retry;
    private final TextView subtitle;
    private final LinearLayout detailBox;
    private final LinearLayout detailChannels;
    private final TextView detailStatus;
    private final TextView detailTeams;
    private final TextView detailTime;
    private final ImageView detailHome;
    private final ImageView detailAway;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Match> matches = new ArrayList<>();
    private final Map<String, List<Broadcasts.Broadcast>> broadcastCache = new HashMap<>();
    private final Map<String, Boolean> requested = new HashMap<>();
    private boolean loading;
    private View first;
    private Match selected;

    FootballScreen(ShellActivity act) {
        super(act);
        root = new FrameLayout(act);
        LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(act, 8), Ui.dp(act, 8), Ui.dp(act, 8), 0);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout heading = new LinearLayout(act);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.title(act, "JOGOS DO DIA", 27, C.TEXT);
        title.setTypeface(Ui.bold(act), Typeface.BOLD);
        heading.addView(title);
        subtitle = Ui.text(act, "", 15, C.TEXT2);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
        slp.leftMargin = Ui.dp(act, 18);
        heading.addView(subtitle, slp);
        content.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView rail = new HorizontalScrollView(act);
        rail.setHorizontalScrollBarEnabled(false);
        cardsBox = new LinearLayout(act);
        cardsBox.setOrientation(LinearLayout.HORIZONTAL);
        cardsBox.setGravity(Gravity.CENTER_VERTICAL);
        cardsBox.setPadding(0, Ui.dp(act, 12), Ui.dp(act, 12), Ui.dp(act, 12));
        rail.addView(cardsBox, new HorizontalScrollView.LayoutParams(-2, -1));
        content.addView(rail, new LinearLayout.LayoutParams(-1, Ui.dp(act, 210)));

        detailBox = new LinearLayout(act);
        detailBox.setOrientation(LinearLayout.HORIZONTAL);
        detailBox.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 10));
        content.addView(detailBox, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout highlight = new LinearLayout(act);
        highlight.setOrientation(LinearLayout.VERTICAL);
        highlight.setGravity(Gravity.CENTER);
        highlight.setPadding(Ui.dp(act, 22), Ui.dp(act, 14), Ui.dp(act, 22), Ui.dp(act, 14));
        highlight.setBackground(Ui.glass(act, 18));
        TextView cap = Ui.text(act, "JOGO EM DESTAQUE", 13, C.LIME);
        cap.setTypeface(Ui.bold(act), Typeface.BOLD);
        highlight.addView(cap);
        LinearLayout shields = new LinearLayout(act);
        shields.setGravity(Gravity.CENTER);
        detailHome = shieldView();
        detailAway = shieldView();
        shields.addView(detailHome, new LinearLayout.LayoutParams(Ui.dp(act, 62), Ui.dp(act, 62)));
        TextView x = Ui.title(act, "  X  ", 22, C.TEXT2);
        shields.addView(x);
        shields.addView(detailAway, new LinearLayout.LayoutParams(Ui.dp(act, 62), Ui.dp(act, 62)));
        highlight.addView(shields);
        detailTeams = Ui.title(act, "Selecione um jogo", 19, C.TEXT);
        detailTeams.setGravity(Gravity.CENTER);
        detailTeams.setMaxLines(2);
        highlight.addView(detailTeams, new LinearLayout.LayoutParams(-1, -2));
        detailStatus = Ui.text(act, "", 15, C.TEXT2);
        detailStatus.setGravity(Gravity.CENTER);
        highlight.addView(detailStatus);
        detailTime = Ui.text(act, "", 15, C.TEXT2);
        detailTime.setGravity(Gravity.CENTER);
        highlight.addView(detailTime);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, -1, 1.1f);
        hlp.rightMargin = Ui.dp(act, 12);
        detailBox.addView(highlight, hlp);

        LinearLayout channelsPanel = new LinearLayout(act);
        channelsPanel.setOrientation(LinearLayout.VERTICAL);
        channelsPanel.setPadding(Ui.dp(act, 16), Ui.dp(act, 12), Ui.dp(act, 12), Ui.dp(act, 8));
        channelsPanel.setBackground(Ui.glass(act, 18));
        TextView channelsTitle = Ui.title(act, "CANAIS QUE VÃO TRANSMITIR", 16, C.TEXT);
        channelsTitle.setTypeface(Ui.bold(act), Typeface.BOLD);
        channelsPanel.addView(channelsTitle);
        ScrollView channelsScroll = new ScrollView(act);
        channelsScroll.setVerticalScrollBarEnabled(false);
        detailChannels = new LinearLayout(act);
        detailChannels.setOrientation(LinearLayout.VERTICAL);
        channelsScroll.addView(detailChannels, new ScrollView.LayoutParams(-1, -2));
        channelsPanel.addView(channelsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        detailBox.addView(channelsPanel, new LinearLayout.LayoutParams(0, -1, 1f));

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

    private ImageView shieldView() {
        ImageView v = new ImageView(act);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    @Override View view() { return root; }
    @Override View firstFocus() { return first; }
    @Override String hints() { return ""; }

    @Override void onShow() {
        subtitle.setText(new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR"))
                .format(new Date()));
        load();
    }

    @Override void onResume() { load(); }
    @Override void onHide() { super.onHide(); main.removeCallbacksAndMessages(null); }

    private void load() {
        if (loading) return;
        loading = true;
        if (matches.isEmpty()) showNotice("Buscando os jogos de hoje...");
        bg(() -> FootballApi.todayResult(act), new Bg.Done<FootballApi.Result>() {
            @Override public void ok(FootballApi.Result result) {
                loading = false;
                if (result.error) showError();
                else render(ListGames.merge(act, result.matches));
            }
            @Override public void fail(Exception e) { loading = false; showError(); }
        });
    }

    private void render(List<Match> value) {
        matches.clear();
        if (value != null) matches.addAll(value);
        cardsBox.removeAllViews();
        broadcastCache.clear();
        requested.clear();
        first = null;
        selected = null;
        if (matches.isEmpty()) {
            detailBox.setVisibility(View.GONE);
            showNotice("Nenhum jogo hoje.");
            return;
        }
        notice.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        detailBox.setVisibility(View.VISIBLE);
        for (Match match : matches) addMatchCard(match);
        selectMatch(matches.get(0));
        if (first != null) act.focusReady(this);
    }

    private void addMatchCard(final Match match) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 10));
        card.setBackground(Ui.softFocusBg(act, C.SURFACE_FOCUS, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(act, 282), -1);
        lp.rightMargin = Ui.dp(act, 10);
        cardsBox.addView(card, lp);
        TextView league = Ui.text(act, match.liga, 12, C.TEXT2);
        league.setGravity(Gravity.CENTER);
        league.setMaxLines(1);
        league.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(league, new LinearLayout.LayoutParams(-1, Ui.dp(act, 22)));
        LinearLayout teams = new LinearLayout(act);
        teams.setGravity(Gravity.CENTER);
        addShield(teams, match.escudoCasa, 42);
        TextView versus = Ui.title(act, "  X  ", 17, C.TEXT2);
        teams.addView(versus);
        addShield(teams, match.escudoFora, 42);
        card.addView(teams, new LinearLayout.LayoutParams(-1, Ui.dp(act, 50)));
        TextView names = Ui.text(act, match.casa + "  x  " + match.fora, 15, C.TEXT);
        names.setGravity(Gravity.CENTER);
        names.setMaxLines(2);
        names.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(names, new LinearLayout.LayoutParams(-1, Ui.dp(act, 42)));
        TextView status = Ui.text(act, statusText(match), 14,
                match.status == Match.LIVE ? C.LIME : C.TEXT2);
        status.setGravity(Gravity.CENTER);
        card.addView(status, new LinearLayout.LayoutParams(-1, Ui.dp(act, 26)));
        if (first == null) first = card;
        card.setOnFocusChangeListener((v, has) -> { if (has) selectMatch(match); });
        card.setOnClickListener(v -> {
            selectMatch(match);
            ChannelGroup channel = primary(match);
            if (channel != null) play(channel);
            else Ui.toast(act, "Carregando os canais desta partida.");
        });
        requestChannels(match);
    }

    private void selectMatch(Match match) {
        selected = match;
        detailHome.setImageDrawable(null);
        detailAway.setImageDrawable(null);
        ImageLoader.load(detailHome, match.escudoCasa, 180);
        ImageLoader.load(detailAway, match.escudoFora, 180);
        detailTeams.setText(match.casa + "  x  " + match.fora);
        detailStatus.setText(statusText(match));
        detailStatus.setTextColor(match.status == Match.LIVE ? C.LIME : C.TEXT2);
        detailTime.setText(new SimpleDateFormat("dd/MM  HH:mm", Locale.getDefault())
                .format(new Date(match.horario)));
        renderChannels(match);
        requestChannels(match);
    }

    private void requestChannels(final Match match) {
        if (requested.containsKey(match.id)) return;
        requested.put(match.id, true);
        bg(() -> Broadcasts.channelsFor(act, match), new Bg.Done<List<Broadcasts.Broadcast>>() {
            @Override public void ok(List<Broadcasts.Broadcast> groups) {
                broadcastCache.put(match.id, groups == null ? new ArrayList<>() : groups);
                if (selected == match) renderChannels(match);
            }
            @Override public void fail(Exception e) {
                broadcastCache.put(match.id, new ArrayList<>());
                if (selected == match) renderChannels(match);
            }
        });
    }

    private void renderChannels(Match match) {
        detailChannels.removeAllViews();
        List<Broadcasts.Broadcast> groups = broadcastCache.get(match.id);
        if (groups == null) {
            detailChannels.addView(Ui.text(act, "Procurando canais...", 14, C.TEXT2));
            return;
        }
        int count = 0;
        for (Broadcasts.Broadcast group : groups) {
            if (group.canais.isEmpty()) continue;
            TextView label = Ui.text(act, group.emissora, 13, C.TEXT2);
            label.setTypeface(Ui.bold(act), Typeface.BOLD);
            detailChannels.addView(label, new LinearLayout.LayoutParams(-1, Ui.dp(act, 24)));
            for (ChannelGroup channel : group.canais) {
                addChannelLine(detailChannels, channel, match);
                if (++count >= 12) break;
            }
            if (count >= 12) break;
        }
        if (count == 0) detailChannels.addView(Ui.text(act,
                "Nenhum canal relacionado na sua lista.", 14, C.TEXT3));
    }

    private void addChannelLine(LinearLayout target, final ChannelGroup channel, Match match) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(Ui.dp(act, 6), Ui.dp(act, 4), Ui.dp(act, 8), Ui.dp(act, 4));
        row.setBackground(Ui.softFocusBg(act, C.SURFACE_FOCUS, 10));
        ImageView logo = shieldView();
        ImageLoader.load(logo, channel.logo, 120);
        row.addView(logo, new LinearLayout.LayoutParams(Ui.dp(act, 42), Ui.dp(act, 42)));
        TextView name = Ui.text(act, channel.nome, 14, C.TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, -2, 1f);
        nlp.leftMargin = Ui.dp(act, 10);
        row.addView(name, nlp);
        TextView play = Ui.text(act, "▶", 20, C.LIME);
        row.addView(play, new LinearLayout.LayoutParams(Ui.dp(act, 34), -2));
        target.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(act, 54)));
        row.setOnClickListener(v -> play(channel));
    }

    private ChannelGroup primary(Match match) {
        List<Broadcasts.Broadcast> groups = broadcastCache.get(match.id);
        if (groups == null) return null;
        ChannelGroup fallback = null;
        for (Broadcasts.Broadcast group : groups) {
            if (!group.canais.isEmpty() && fallback == null) fallback = group.canais.get(0);
            if (!group.canais.isEmpty() && !"Canais do jogo".equals(group.emissora)) {
                return group.canais.get(0);
            }
        }
        return fallback;
    }

    private void addShield(LinearLayout row, String url, int size) {
        ImageView shield = shieldView();
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
