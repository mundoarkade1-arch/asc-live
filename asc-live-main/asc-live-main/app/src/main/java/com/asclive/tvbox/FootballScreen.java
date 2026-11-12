package com.asclive.tvbox;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tela "Início": Jogos do Dia numa faixa de cartões (escudos, horário, placar, AO VIVO).
 * Ao parar em um jogo, embaixo aparecem o jogo em destaque e os "Canais que vão transmitir".
 * OK no canal abre o player. Se a ESPN falhar, os jogos vêm dos nomes dos canais da lista
 * (ListGames). Se nada aparecer, mostra "sem jogos agora".
 */
final class FootballScreen extends Screen {

    private static final int MAX_CARDS = 40;

    private final FrameLayout root;
    private final TextView subtitle;
    private final TextView liveTag;
    private final LinearLayout strip;
    private final TextView notice;
    private final BigGame big;
    private final TextView chanCount;
    private final ListView chanList;
    private final TextView chanNotice;
    private final ChanAdapter chanAdapter = new ChanAdapter();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Match> matches = new ArrayList<>();
    private final List<GameCard> cards = new ArrayList<>();
    private final List<ChannelGroup> flat = new ArrayList<>();
    private String rowSub = "";
    private int selected = -1;
    private int loadToken;
    private boolean loading;

    FootballScreen(ShellActivity act) {
        super(act);
        root = new FrameLayout(act);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // ---- Título ----
        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 6));
        top.addView(Ui.title(act, "Jogos do Dia", 30, C.TEXT));
        liveTag = Ui.limeTag(act, "Ao Vivo");
        liveTag.setVisibility(View.GONE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.leftMargin = Ui.dp(act, 14);
        top.addView(liveTag, tlp);
        top.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1f));
        subtitle = Ui.text(act, "", 17, C.TEXT2);
        top.addView(subtitle);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // ---- Faixa de jogos ----
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.setPadding(Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 8));
        strip = new LinearLayout(act);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        hs.addView(strip, new FrameLayout.LayoutParams(-2, -2));
        col.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        // ---- Jogo em destaque + canais ----
        LinearLayout detail = new LinearLayout(act);
        detail.setOrientation(LinearLayout.HORIZONTAL);
        detail.setPadding(Ui.dp(act, 4), Ui.dp(act, 6), Ui.dp(act, 4), 0);
        big = new BigGame(act);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, -1, 4f);
        blp.rightMargin = Ui.dp(act, 14);
        detail.addView(big, blp);

        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.glass(act, 22));
        panel.setPadding(Ui.dp(act, 16), Ui.dp(act, 12), Ui.dp(act, 16), Ui.dp(act, 8));
        LinearLayout ph = new LinearLayout(act);
        ph.setOrientation(LinearLayout.HORIZONTAL);
        ph.setGravity(Gravity.CENTER_VERTICAL);
        ph.addView(Ui.icon(act, R.drawable.ic_tv, 26, C.LIME));
        LinearLayout.LayoutParams ptl = new LinearLayout.LayoutParams(0, -2, 1f);
        ptl.leftMargin = Ui.dp(act, 12);
        ph.addView(Ui.title(act, "Canais que vão transmitir", 20, C.TEXT), ptl);
        chanCount = Ui.text(act, "", 16, C.LIME);
        ph.addView(chanCount);
        panel.addView(ph, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout listBox = new FrameLayout(act);
        chanList = new ListView(act);
        chanList.setDivider(null);
        chanList.setDividerHeight(Ui.dp(act, 8));
        chanList.setVerticalScrollBarEnabled(false);
        chanList.setSelector(Ui.shape(0x00000000, Ui.dp(act, 14), C.LIME, Ui.dp(act, 3)));
        chanList.setDrawSelectorOnTop(true);
        chanList.setClipToPadding(false);
        chanList.setPadding(Ui.dp(act, 3), Ui.dp(act, 10), Ui.dp(act, 3), Ui.dp(act, 8));
        chanList.setAdapter(chanAdapter);
        chanList.setOnItemClickListener((parent, view, pos, id) -> play(pos));
        listBox.addView(chanList, new FrameLayout.LayoutParams(-1, -1));
        chanNotice = Ui.text(act, "", 18, C.TEXT2);
        chanNotice.setGravity(Gravity.CENTER);
        chanNotice.setPadding(Ui.dp(act, 24), 0, Ui.dp(act, 24), 0);
        chanNotice.setVisibility(View.GONE);
        listBox.addView(chanNotice, new FrameLayout.LayoutParams(-1, -1));
        panel.addView(listBox, new LinearLayout.LayoutParams(-1, 0, 1f));
        detail.addView(panel, new LinearLayout.LayoutParams(0, -1, 6f));
        col.addView(detail, new LinearLayout.LayoutParams(-1, 0, 1f));

        notice = Ui.text(act, "", 20, C.TEXT2);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(Ui.dp(act, 40), 0, Ui.dp(act, 40), 0);
        notice.setVisibility(View.GONE);
        root.addView(notice, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override
    View view() {
        return root;
    }

    @Override
    View firstFocus() {
        return cards.isEmpty() ? null : cards.get(Math.max(0, selected));
    }

    @Override
    String hints() {
        return "OK:Abrir o canal|Setas:Trocar de jogo e de canal|Esquerda:Menu";
    }

    @Override
    boolean handleUp(View focused) {
        // Primeira linha da lista de canais: cima volta para o jogo.
        if (focused == chanList && !cards.isEmpty()) {
            cards.get(Math.max(0, selected)).requestFocus();
            return true;
        }
        return false;
    }

    @Override
    void onShow() {
        subtitle.setText(new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR"))
                .format(new Date()));
        load();
    }

    @Override
    void onResume() {
        // Voltou do player: atualiza os placares (o cache de 15 minutos evita rede à toa).
        load();
    }

    @Override
    void onHide() {
        super.onHide();
        h.removeCallbacksAndMessages(null);
    }

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        if (matches.isEmpty()) {
            showNotice("Buscando os jogos de hoje...");
        }
        bg(() -> ListGames.merge(act, FootballApi.today(act)), new Bg.Done<List<Match>>() {
            @Override
            public void ok(List<Match> v) {
                loading = false;
                showMatches(v);
            }

            @Override
            public void fail(Exception e) {
                loading = false;
                if (matches.isEmpty()) {
                    showNotice("Sem jogos agora.\nTente de novo mais tarde.");
                }
            }
        });
    }

    private void showNotice(String text) {
        notice.setText(text);
        notice.setVisibility(View.VISIBLE);
    }

    private void showMatches(List<Match> v) {
        int n = Math.min(MAX_CARDS, v.size());
        boolean same = n == cards.size() && n == Math.min(MAX_CARDS, matches.size());
        for (int i = 0; same && i < n; i++) {
            if (!v.get(i).id.equals(matches.get(i).id)) {
                same = false;
            }
        }
        boolean hadFocus = strip.hasFocus();
        matches.clear();
        matches.addAll(v);
        boolean anyLive = false;
        for (Match m : matches) {
            if (m.isLive()) {
                anyLive = true;
                break;
            }
        }
        liveTag.setVisibility(anyLive ? View.VISIBLE : View.GONE);
        if (n == 0) {
            strip.removeAllViews();
            cards.clear();
            selected = -1;
            flat.clear();
            chanAdapter.notifyDataSetChanged();
            big.clear();
            chanCount.setText("");
            chanNotice.setVisibility(View.GONE);
            showNotice("Sem jogos agora.\nTente de novo mais tarde.");
            return;
        }
        notice.setVisibility(View.GONE);
        if (same) {
            for (int i = 0; i < n; i++) {
                cards.get(i).bind(matches.get(i));
            }
            if (selected >= 0 && selected < n) {
                big.bind(matches.get(selected));
            }
            return;
        }
        strip.removeAllViews();
        cards.clear();
        selected = -1;
        int cw = Math.round(Ui.screenW(act) * 0.15f);
        int chh = Math.round(Ui.screenH(act) * 0.21f);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            GameCard card = new GameCard(act);
            card.bind(matches.get(i));
            card.setOnFocusChangeListener((view, has) -> {
                if (has) {
                    selectMatch(idx);
                }
            });
            card.setOnClickListener(view -> {
                if (flat.isEmpty()) {
                    Ui.toast(act, "Nenhum canal da sua lista parece passar este jogo.");
                } else {
                    chanList.requestFocus();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cw, chh);
            lp.rightMargin = Ui.dp(act, 12);
            strip.addView(card, lp);
            cards.add(card);
        }
        selectMatch(0);
        if (hadFocus) {
            cards.get(0).requestFocus();
        }
        act.focusReady(this);
    }

    /** Mostra o jogo em destaque na hora e procura os canais dele (sem pressa, 200 ms depois). */
    private void selectMatch(int i) {
        if (i < 0 || i >= matches.size() || i == selected) {
            return;
        }
        selected = i;
        final Match m = matches.get(i);
        big.bind(m);
        rowSub = m.casa + " x " + m.fora + (m.liga.isEmpty() ? "" : " - " + m.liga);
        h.removeCallbacksAndMessages(null);
        h.postDelayed(() -> loadChannels(m), 200L);
    }

    private void loadChannels(final Match m) {
        final int token = ++loadToken;
        chanNotice.setText("Procurando os canais...");
        chanNotice.setVisibility(View.VISIBLE);
        bg(() -> Broadcasts.channelsFor(act, m), new Bg.Done<List<Broadcasts.Broadcast>>() {
            @Override
            public void ok(List<Broadcasts.Broadcast> v) {
                if (token != loadToken) {
                    return;
                }
                flat.clear();
                for (Broadcasts.Broadcast b : v) {
                    flat.addAll(b.canais);
                }
                chanAdapter.notifyDataSetChanged();
                chanCount.setText(flat.isEmpty() ? "" : String.valueOf(flat.size()));
                if (flat.isEmpty()) {
                    chanNotice.setText("Nenhum canal da sua lista parece passar este jogo.\n"
                            + "Procure o canal em Buscar.");
                    chanNotice.setVisibility(View.VISIBLE);
                } else {
                    chanNotice.setVisibility(View.GONE);
                }
            }

            @Override
            public void fail(Exception e) {
                if (token != loadToken) {
                    return;
                }
                flat.clear();
                chanAdapter.notifyDataSetChanged();
                chanCount.setText("");
                chanNotice.setText(Screen.errorText(e));
                chanNotice.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Abre o player. Cima e baixo trocam entre os canais desta lista. */
    private void play(int pos) {
        if (pos < 0 || pos >= flat.size()) {
            return;
        }
        LiveSession.set(new ArrayList<>(flat), pos, -1);
        act.startActivity(new Intent(act, LivePlayerActivity.class));
    }

    // ---------- Cartão do jogo (faixa) ----------

    private static final class GameCard extends LinearLayout {
        private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final TextView league;
        private final TextView homeName;
        private final TextView awayName;
        private final TextView vs;
        private final TextView time;
        private final TextView liveTag;
        private final ImageView homeShield;
        private final ImageView awayShield;

        GameCard(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setFocusable(true);
            setClickable(true);
            setBackground(Ui.focusBg(c, C.GLASS, C.GLASS_HI, 18, C.GLASS_EDGE, Ui.dp(c, 1)));
            setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));

            league = Ui.title(c, "", 15, C.TEXT);
            league.setSingleLine(true);
            league.setEllipsize(TextUtils.TruncateAt.END);
            league.setGravity(Gravity.CENTER);
            addView(league, new LayoutParams(-1, -2));

            LinearLayout row = new LinearLayout(c);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            int sh = Math.round(Ui.screenH(c) * 0.075f);
            homeShield = shield(c);
            awayShield = shield(c);
            row.addView(homeShield, new LayoutParams(sh, sh));
            vs = Ui.title(c, "X", 20, C.TEXT2);
            LayoutParams vl = new LayoutParams(-2, -2);
            vl.setMargins(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
            row.addView(vs, vl);
            row.addView(awayShield, new LayoutParams(sh, sh));
            addView(row, new LayoutParams(-1, 0, 1f));

            LinearLayout names = new LinearLayout(c);
            names.setOrientation(HORIZONTAL);
            homeName = nameView(c);
            awayName = nameView(c);
            names.addView(homeName, new LayoutParams(0, -2, 1f));
            names.addView(awayName, new LayoutParams(0, -2, 1f));
            addView(names, new LayoutParams(-1, -2));

            LinearLayout bottom = new LinearLayout(c);
            bottom.setOrientation(VERTICAL);
            bottom.setGravity(Gravity.CENTER_HORIZONTAL);
            liveTag = Ui.limeTag(c, "AO VIVO");
            bottom.addView(liveTag);
            time = Ui.title(c, "", 16, C.TEXT);
            bottom.addView(time);
            LayoutParams bl = new LayoutParams(-1, -2);
            bl.topMargin = Ui.dp(c, 6);
            addView(bottom, bl);
        }

        private static ImageView shield(Context c) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return v;
        }

        private static TextView nameView(Context c) {
            TextView t = Ui.text(c, "", 14, C.TEXT);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            return t;
        }

        void bind(Match m) {
            league.setText(m.liga);
            homeName.setText(m.casa);
            awayName.setText(m.fora);
            ImageLoader.load(homeShield, m.escudoCasa, 140);
            ImageLoader.load(awayShield, m.escudoFora, 140);
            if (m.status == Match.LIVE) {
                liveTag.setVisibility(VISIBLE);
                time.setText(m.statusText.isEmpty() ? clock.format(new Date(m.horario))
                        : m.statusText);
                vs.setText(m.placar.isEmpty() ? "X" : m.placar);
                vs.setTextColor(C.LIME);
            } else if (m.status == Match.FINISHED) {
                liveTag.setVisibility(GONE);
                time.setText(m.statusText.isEmpty() ? "Encerrado" : m.statusText);
                vs.setText(m.placar.isEmpty() ? "X" : m.placar);
                vs.setTextColor(C.TEXT);
            } else {
                liveTag.setVisibility(GONE);
                time.setText(m.statusText.isEmpty() ? clock.format(new Date(m.horario))
                        : m.statusText);
                vs.setText("X");
                vs.setTextColor(C.TEXT2);
            }
        }
    }

    // ---------- Jogo em destaque (embaixo, à esquerda) ----------

    private static final class BigGame extends LinearLayout {
        private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final TextView league;
        private final TextView liveTag;
        private final ImageView homeShield;
        private final ImageView awayShield;
        private final TextView vs;
        private final TextView homeName;
        private final TextView awayName;
        private final TextView time;

        BigGame(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setBackground(Ui.glass(c, 22));
            setPadding(Ui.dp(c, 18), Ui.dp(c, 12), Ui.dp(c, 18), Ui.dp(c, 12));

            league = Ui.title(c, "", 20, C.TEXT);
            league.setSingleLine(true);
            league.setEllipsize(TextUtils.TruncateAt.END);
            league.setGravity(Gravity.CENTER);
            addView(league, new LayoutParams(-1, -2));
            liveTag = Ui.limeTag(c, "AO VIVO");
            LayoutParams lp = new LayoutParams(-2, -2);
            lp.topMargin = Ui.dp(c, 6);
            addView(liveTag, lp);
            liveTag.setVisibility(INVISIBLE);

            LinearLayout row = new LinearLayout(c);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            int sh = Math.round(Ui.screenH(c) * 0.11f);
            homeShield = new ImageView(c);
            awayShield = new ImageView(c);
            homeShield.setScaleType(ImageView.ScaleType.FIT_CENTER);
            awayShield.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(homeShield, new LayoutParams(sh, sh));
            vs = Ui.title(c, "X", 36, C.TEXT);
            LayoutParams vl = new LayoutParams(-2, -2);
            vl.setMargins(Ui.dp(c, 22), 0, Ui.dp(c, 22), 0);
            row.addView(vs, vl);
            row.addView(awayShield, new LayoutParams(sh, sh));
            addView(row, new LayoutParams(-1, 0, 1f));

            LinearLayout names = new LinearLayout(c);
            names.setOrientation(HORIZONTAL);
            homeName = Ui.title(c, "", 19, C.TEXT);
            awayName = Ui.title(c, "", 19, C.TEXT);
            for (TextView t : new TextView[]{homeName, awayName}) {
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER);
            }
            names.addView(homeName, new LayoutParams(0, -2, 1f));
            names.addView(awayName, new LayoutParams(0, -2, 1f));
            addView(names, new LayoutParams(-1, -2));

            time = Ui.title(c, "", 22, C.TEXT);
            time.setGravity(Gravity.CENTER);
            LayoutParams tl = new LayoutParams(-1, -2);
            tl.topMargin = Ui.dp(c, 6);
            addView(time, tl);
        }

        void clear() {
            league.setText("");
            homeName.setText("");
            awayName.setText("");
            time.setText("");
            liveTag.setVisibility(INVISIBLE);
            homeShield.setImageDrawable(null);
            awayShield.setImageDrawable(null);
        }

        void bind(Match m) {
            league.setText(m.liga);
            homeName.setText(m.casa);
            awayName.setText(m.fora);
            ImageLoader.load(homeShield, m.escudoCasa, 240);
            ImageLoader.load(awayShield, m.escudoFora, 240);
            liveTag.setVisibility(m.status == Match.LIVE ? VISIBLE : INVISIBLE);
            if (m.status == Match.SCHEDULED) {
                vs.setText("X");
                vs.setTextColor(C.TEXT);
                time.setText(m.statusText.isEmpty()
                        ? clock.format(new Date(m.horario)) + "  \u00b7  Hoje" : m.statusText);
            } else {
                vs.setText(m.placar.isEmpty() ? "X" : m.placar);
                vs.setTextColor(m.status == Match.LIVE ? C.LIME : C.TEXT);
                time.setText(m.statusText.isEmpty()
                        ? (m.status == Match.FINISHED ? "Encerrado" : "") : m.statusText);
            }
        }
    }

    // ---------- Lista de canais do jogo ----------

    private final class ChanAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return flat.size();
        }

        @Override
        public Object getItem(int position) {
            return flat.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            ChannelRow r = convert instanceof ChannelRow ? (ChannelRow) convert
                    : new ChannelRow(act, false);
            ChannelGroup g = flat.get(position);
            r.bind(position + 1, g, Favorites.isFavorite(act, g.key()), rowSub);
            return r;
        }
    }
}
