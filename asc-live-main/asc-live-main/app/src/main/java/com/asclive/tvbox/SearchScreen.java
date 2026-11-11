package com.asclive.tvbox;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela "Buscar": a pessoa digita o nome do canal (SearchKeyboard) e a busca é feita em
 * memória pelo nome do canal (sem acento). Cada resolução aparece como um canal.
 */
final class SearchScreen extends GroupGridScreen {

    private static final int MAX_RESULTS = 300;
    private static final String HINT = "Aperte OK em \"Digitar\" e escreva o nome do canal.";

    private final LinearLayout root;
    private final TextView button;
    private final TextView status;
    private String lastQuery = "";

    SearchScreen(ShellActivity act) {
        super(act);
        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C.BG);

        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(act, 36), Ui.dp(act, 22), Ui.dp(act, 36), Ui.dp(act, 6));
        top.addView(Ui.title(act, "Buscar canal", 34, C.TEXT));
        button = Ui.button(act, "Digitar", Ui.LIME);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.leftMargin = Ui.dp(act, 28);
        top.addView(button, blp);
        status = Ui.text(act, HINT, 18, C.TEXT2);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
        slp.leftMargin = Ui.dp(act, 24);
        top.addView(status, slp);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));
        root.addView(gridHolder, new LinearLayout.LayoutParams(-1, 0, 1f));
        button.setOnClickListener(v -> askQuery());
        showNotice("Digite o nome de um canal.\nExemplos: premiere, sportv, globo, espn.");
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

    /** Seta para cima na primeira linha dos resultados: volta ao botão "Digitar". */
    @Override
    boolean handleUp(View focused) {
        if (focused == grid) {
            button.requestFocus();
            return true;
        }
        return false;
    }

    @Override
    void onResume() {
        adapter.notifyDataSetChanged();
    }

    private void askQuery() {
        SearchKeyboard.show(act, "Buscar canal", new SearchKeyboard.Listener() {
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
            List<ChannelGroup> out = new ArrayList<>();
            for (ChannelGroup g : Catalog.allGroups(act)) {
                boolean all = true;
                for (String w : words) {
                    if (!g.key().contains(w)) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    out.add(g);
                    if (out.size() >= MAX_RESULTS) {
                        break;
                    }
                }
            }
            return out;
        }, new Bg.Done<List<ChannelGroup>>() {
            @Override
            public void ok(List<ChannelGroup> v) {
                setGroups(v, "Nenhum canal encontrado para \"" + lastQuery + "\".");
                status.setText(v.isEmpty() ? "" : v.size() + (v.size() == 1
                        ? " canal encontrado" : " canais encontrados"));
                if (!v.isEmpty()) {
                    grid.requestFocus();
                }
            }

            @Override
            public void fail(Exception e) {
                showNotice(Screen.errorText(e));
                status.setText("");
            }
        });
    }
}
