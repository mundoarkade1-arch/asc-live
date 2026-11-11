package com.asclive.tvbox;

import android.view.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tela "Favoritos": os canais favoritos do perfil (OK longo tira da lista). */
final class FavoritesScreen extends GroupGridScreen {

    private static final String EMPTY = "Nenhum favorito ainda.\n\n"
            + "Em Ao vivo ou em Buscar, segure o botão OK sobre um canal para favoritar.";

    private boolean loading;

    FavoritesScreen(ShellActivity act) {
        super(act);
    }

    @Override
    View view() {
        return gridHolder;
    }

    @Override
    View firstFocus() {
        return hasChannels() ? grid : null;
    }

    @Override
    void onShow() {
        load();
    }

    @Override
    void onResume() {
        load();
    }

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        if (!hasChannels()) {
            showNotice("Carregando favoritos...");
        }
        bg(() -> {
            Set<String> keys = new HashSet<>(Favorites.all(act));
            List<ChannelGroup> out = new ArrayList<>();
            if (!keys.isEmpty()) {
                for (ChannelGroup g : Catalog.allGroups(act)) {
                    if (keys.contains(g.key())) {
                        out.add(g);
                    }
                }
            }
            return out;
        }, new Bg.Done<List<ChannelGroup>>() {
            @Override
            public void ok(List<ChannelGroup> v) {
                loading = false;
                setGroups(v, EMPTY);
                act.focusReady(FavoritesScreen.this);
            }

            @Override
            public void fail(Exception e) {
                loading = false;
                showNotice(Screen.errorText(e));
            }
        });
    }

    @Override
    void onFavoriteChanged(int pos, ChannelGroup g, boolean favorite) {
        if (favorite) {
            return;
        }
        List<ChannelGroup> rest = new ArrayList<>(adapter.items);
        rest.remove(g);
        setGroups(rest, EMPTY);
        if (!rest.isEmpty()) {
            grid.setSelection(Math.min(pos, rest.size() - 1));
        }
    }
}
