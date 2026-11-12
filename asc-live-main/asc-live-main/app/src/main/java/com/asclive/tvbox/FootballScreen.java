package com.asclive.tvbox;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tela "Futebol": jogos do dia (escudos, horário, placar e selo AO VIVO). OK no jogo abre as
 * emissoras (BroadcastDialog). Se a fonte de jogos falhar, o app acha os jogos nos nomes dos
 * canais da própria lista (ListGames). Se nada aparecer, mostra "sem jogos agora".
 */
final class FootballScreen extends Screen {

    private final LinearLayout root;
    private final TextView subtitle;
    private final TextView notice;
    private final ListView list;
    private final MatchAdapter adapter = new MatchAdapter();
    private final List<Match> matches = new ArrayList<>();
    private boolean loading;
    private boolean busy;

    FootballScreen(ShellActivity act) {
        super(act);
        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.BOTTOM);
        top.setPadding(Ui.dp(act, 36), Ui.dp(act, 22), Ui.dp(act, 36), Ui.dp(act, 8));
        top.addView(Ui.title(act, "Futebol", 34, C.TEXT));
        subtitle = Ui.text(act, "", 18, C.TEXT2);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.leftMargin = Ui.dp(act, 20);
        slp.bottomMargin = Ui.dp(act, 5);
        top.addView(subtitle, slp);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout body = new FrameLayout(act);
        list = new ListView(act);
        list.setDivider(null);
        list.setDividerHeight(Ui.dp(act, 10));
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(Ui.shape(0x00000000, Ui.dp(act, 16), C.LIME, Ui.dp(act, 3)));
        list.setDrawSelectorOnTop(true);
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(act, 36), Ui.dp(act, 8), Ui.dp(act, 36), Ui.dp(act, 30));
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> openMatch(pos));
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));
        notice = Ui.text(act, "", 20, C.TEXT2);
        notice.setGravity(android.view.Gravity.CENTER);
        notice.setPadding(Ui.dp(act, 40), 0, Ui.dp(act, 40), 0);
        body.addView(notice, new FrameLayout.LayoutParams(-1, -1));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    @Override
    View view() {
        return root;
    }

    @Override
    View firstFocus() {
        return matches.isEmpty() ? null : list;
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
                matches.clear();
                matches.addAll(v);
                adapter.notifyDataSetChanged();
                if (matches.isEmpty()) {
                    showNotice("Sem jogos agora.\nTente de novo mais tarde.");
                } else {
                    notice.setVisibility(View.GONE);
                    act.focusReady(FootballScreen.this);
                }
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

    private void openMatch(int pos) {
        if (pos < 0 || pos >= matches.size() || busy) {
            return;
        }
        final Match m = matches.get(pos);
        busy = true;
        Ui.toast(act, "Procurando os canais...");
        bg(() -> Broadcasts.channelsFor(act, m),
                new Bg.Done<List<Broadcasts.Broadcast>>() {
                    @Override
                    public void ok(List<Broadcasts.Broadcast> v) {
                        busy = false;
                        BroadcastDialog.show(act, m, v);
                    }

                    @Override
                    public void fail(Exception e) {
                        busy = false;
                        Dialogs.info(act, m.casa + " x " + m.fora, Screen.errorText(e));
                    }
                });
    }

    private final class MatchAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return matches.size();
        }

        @Override
        public Object getItem(int position) {
            return matches.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            MatchCard card = convert instanceof MatchCard ? (MatchCard) convert
                    : new MatchCard(act);
            card.bind(matches.get(position));
            return card;
        }
    }
}
