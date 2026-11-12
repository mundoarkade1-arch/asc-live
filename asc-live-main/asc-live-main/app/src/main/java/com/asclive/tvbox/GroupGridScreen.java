package com.asclive.tvbox;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Base das telas que mostram uma grade de canais (Ao vivo, Favoritos, Buscar).
 * OK abre o player direto; OK longo favorita ou desfavorita; a tecla das três listras abre a
 * programação do canal; focar um canal por 300 ms pré-aquece a conexão.
 */
abstract class GroupGridScreen extends Screen {

    private static final long PREWARM_MS = 300L;

    final TvGrid grid;
    final ChannelAdapter adapter;
    /** Grade + aviso ("Carregando...", "Nenhum favorito"), um por cima do outro. */
    final FrameLayout gridHolder;
    private final TextView notice;
    private final Handler h = new Handler(Looper.getMainLooper());

    GroupGridScreen(ShellActivity act) {
        super(act);
        grid = new TvGrid(act);
        adapter = new ChannelAdapter(act, grid);
        ChannelAdapter.setup(grid, adapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> open(pos));
        grid.setLongListener(this::longPick);
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                schedulePrewarm(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                h.removeCallbacksAndMessages(null);
            }
        });
        notice = Ui.text(act, "", 20, C.TEXT2);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(Ui.dp(act, 40), 0, Ui.dp(act, 40), 0);
        gridHolder = new FrameLayout(act);
        gridHolder.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        gridHolder.addView(notice, new FrameLayout.LayoutParams(-1, -1));
        notice.setVisibility(View.GONE);
    }

    /** Troca os canais mostrados. Se não houver nenhum, mostra emptyText. */
    void setGroups(List<ChannelGroup> groups, String emptyText) {
        adapter.items = groups;
        adapter.notifyDataSetChanged();
        if (groups.isEmpty()) {
            showNotice(emptyText);
        } else {
            notice.setVisibility(View.GONE);
        }
    }

    void showNotice(String text) {
        notice.setText(text);
        notice.setVisibility(View.VISIBLE);
    }

    boolean hasChannels() {
        return adapter.getCount() > 0;
    }

    /** Abre o player no canal da posição, com a lista de agora (cima/baixo troca de canal). */
    void open(int pos) {
        if (pos < 0 || pos >= adapter.items.size()) {
            return;
        }
        LiveSession.set(new ArrayList<>(adapter.items), pos, -1);
        act.startActivity(new Intent(act, LivePlayerActivity.class));
    }

    private void longPick(int pos) {
        if (pos < 0 || pos >= adapter.items.size()) {
            return;
        }
        ChannelGroup g = adapter.items.get(pos);
        boolean now = Favorites.toggle(act, g.key());
        Ui.toast(act, now ? "Canal favoritado." : "Canal tirado dos favoritos.");
        adapter.notifyDataSetChanged();
        onFavoriteChanged(pos, g, now);
    }

    /** Tecla das três listras sobre um canal: abre a programação (só login por servidor). */
    @Override
    boolean handleMenu() {
        if (!grid.hasFocus()) {
            return false;
        }
        int pos = grid.getSelectedItemPosition();
        if (pos < 0 || pos >= adapter.items.size()) {
            return false;
        }
        CredentialStore.Credentials c = Catalog.creds(act);
        if (c == null || Catalog.isM3u(c)) {
            Ui.toast(act, "A programa\u00e7\u00e3o s\u00f3 existe em lista por servidor.");
            return true;
        }
        ChannelGroup g = adapter.items.get(pos);
        EpgDialog.show(act, c.server, c.user, c.pass, g.item().id, g.nome);
        return true;
    }

    /** A pessoa favoritou ou desfavoritou. A tela de Favoritos tira o canal da lista aqui. */
    void onFavoriteChanged(int pos, ChannelGroup g, boolean favorite) {
    }

    private void schedulePrewarm(final int pos) {
        h.removeCallbacksAndMessages(null);
        if (pos < 0 || pos >= adapter.items.size()) {
            return;
        }
        final ChannelGroup g = adapter.items.get(pos);
        h.postDelayed(() -> LivePlayer.prewarm(act, g), PREWARM_MS);
    }

    @Override
    void onHide() {
        super.onHide();
        h.removeCallbacksAndMessages(null);
    }

    @Override
    void onPause() {
        h.removeCallbacksAndMessages(null);
    }
}
