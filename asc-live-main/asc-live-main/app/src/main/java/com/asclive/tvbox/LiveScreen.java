package com.asclive.tvbox;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela "Ao vivo": primeiro uma GRADE DE CATEGORIAS (vidro fumê). OK em uma categoria abre os
 * canais dela. Voltar volta para as categorias.
 * Como no ASC TV Box, cada canal aparece com a sua resolução (SD, HD, FHD, 4K): é só escolher
 * qual assistir. OK abre o canal direto.
 */
final class LiveScreen extends GroupGridScreen {

    private static final int COLS = 4;

    private static final class Data {
        final List<Catalog.Cat> cats;
        final List<ChannelGroup> groups;

        Data(List<Catalog.Cat> cats, List<ChannelGroup> groups) {
            this.cats = cats;
            this.groups = groups;
        }
    }

    private final FrameLayout root;
    private final GridView catGrid;
    private final CatAdapter catAdapter = new CatAdapter();
    private final LinearLayout channelsBox;
    private final TextView crumb;
    private final List<String> catIds = new ArrayList<>();
    private final List<String> catNames = new ArrayList<>();
    private final List<Integer> catCounts = new ArrayList<>();
    private List<ChannelGroup> all = new ArrayList<>();
    private int lastCat;
    private boolean inChannels;
    private boolean loading;

    LiveScreen(ShellActivity act) {
        super(act);
        root = new FrameLayout(act);

        // ---- Grade de categorias ----
        catGrid = new GridView(act);
        catGrid.setNumColumns(COLS);
        catGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        catGrid.setHorizontalSpacing(Ui.dp(act, 14));
        catGrid.setVerticalSpacing(Ui.dp(act, 14));
        catGrid.setVerticalScrollBarEnabled(false);
        catGrid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        catGrid.setClipToPadding(false);
        catGrid.setPadding(Ui.dp(act, 36), Ui.dp(act, 24), Ui.dp(act, 36), Ui.dp(act, 30));
        catGrid.setSelector(Ui.shape(0x00000000, Ui.dp(act, 16), C.LIME, Ui.dp(act, 3)));
        catGrid.setDrawSelectorOnTop(true);
        catGrid.setAdapter(catAdapter);
        catGrid.setOnItemClickListener((parent, view, pos, id) -> openCategory(pos));
        root.addView(catGrid, new FrameLayout.LayoutParams(-1, -1));

        // ---- Canais da categoria escolhida ----
        channelsBox = new LinearLayout(act);
        channelsBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        head.setPadding(Ui.dp(act, 36), Ui.dp(act, 22), Ui.dp(act, 36), Ui.dp(act, 6));
        crumb = Ui.title(act, "", 30, C.TEXT);
        crumb.setSingleLine(true);
        crumb.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(crumb, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView hint = Ui.text(act, "Voltar: categorias", 16, C.TEXT2);
        head.addView(hint, new LinearLayout.LayoutParams(-2, -2));
        channelsBox.addView(head, new LinearLayout.LayoutParams(-1, -2));
        channelsBox.addView(gridHolder, new LinearLayout.LayoutParams(-1, 0, 1f));
        channelsBox.setVisibility(View.GONE);
        root.addView(channelsBox, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override
    View view() {
        return root;
    }

    @Override
    View firstFocus() {
        if (inChannels) {
            return hasChannels() ? grid : null;
        }
        return catAdapter.getCount() > 0 ? catGrid : null;
    }

    @Override
    void onShow() {
        load();
    }

    @Override
    void onResume() {
        // Voltou do player: os favoritos podem ter mudado (a estrela do cartão).
        adapter.notifyDataSetChanged();
    }

    @Override
    boolean handleBack() {
        if (inChannels) {
            showCategories();
            return true;
        }
        return false;
    }

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        showNotice("Carregando canais...");
        channelsBox.setVisibility(View.VISIBLE);
        catGrid.setVisibility(View.GONE);
        bg(() -> new Data(Catalog.liveCategories(act), Catalog.allGroups(act)),
                new Bg.Done<Data>() {
                    @Override
                    public void ok(Data d) {
                        loading = false;
                        show(d);
                    }

                    @Override
                    public void fail(Exception e) {
                        loading = false;
                        showNotice(Screen.errorText(e));
                    }
                });
    }

    private void show(Data d) {
        all = d.groups;
        catIds.clear();
        catNames.clear();
        catCounts.clear();
        catIds.add(null);
        catNames.add("Todos os canais");
        catCounts.add(all.size());
        for (Catalog.Cat c : d.cats) {
            int n = 0;
            for (ChannelGroup g : all) {
                if (c.id.equals(g.categoryId)) {
                    n++;
                }
            }
            if (n == 0) {
                continue;
            }
            catIds.add(c.id);
            catNames.add(c.name);
            catCounts.add(n);
        }
        lastCat = 0;
        catAdapter.notifyDataSetChanged();
        inChannels = false;
        channelsBox.setVisibility(View.GONE);
        catGrid.setVisibility(View.VISIBLE);
        act.focusReady(this);
    }

    private void openCategory(int pos) {
        if (pos < 0 || pos >= catIds.size()) {
            return;
        }
        lastCat = pos;
        String id = catIds.get(pos);
        List<ChannelGroup> out;
        if (id == null) {
            out = all;
        } else {
            out = new ArrayList<>();
            for (ChannelGroup g : all) {
                if (id.equals(g.categoryId)) {
                    out.add(g);
                }
            }
        }
        crumb.setText(catNames.get(pos));
        setGroups(out, "Nenhum canal nesta categoria.");
        grid.setSelection(0);
        inChannels = true;
        catGrid.setVisibility(View.GONE);
        channelsBox.setVisibility(View.VISIBLE);
        if (hasChannels()) {
            grid.requestFocus();
        }
    }

    private void showCategories() {
        inChannels = false;
        channelsBox.setVisibility(View.GONE);
        catGrid.setVisibility(View.VISIBLE);
        catGrid.setSelection(lastCat);
        catGrid.requestFocus();
    }

    /** Um quadro de vidro com o nome da categoria e quantos canais ela tem. */
    private static final class CatTile extends LinearLayout {
        private final TextView name;
        private final TextView count;

        CatTile(ShellActivity a) {
            super(a);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setBackground(Ui.glass(a, 16));
            setPadding(Ui.dp(a, 18), Ui.dp(a, 10), Ui.dp(a, 18), Ui.dp(a, 10));
            setLayoutParams(new AbsListView.LayoutParams(-1, Ui.dp(a, 96)));
            name = Ui.title(a, "", 20, C.TEXT);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(name, new LayoutParams(-1, -2));
            count = Ui.text(a, "", 14, C.TEXT2);
            LayoutParams cl = new LayoutParams(-2, -2);
            cl.topMargin = Ui.dp(a, 6);
            addView(count, cl);
        }

        void bind(String title, int n, boolean first) {
            name.setText(title);
            name.setTextColor(first ? C.LIME : C.TEXT);
            count.setText(n + (n == 1 ? " canal" : " canais"));
        }
    }

    private final class CatAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return catNames.size();
        }

        @Override
        public Object getItem(int position) {
            return catNames.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            CatTile t = convert instanceof CatTile ? (CatTile) convert : new CatTile(act);
            t.bind(catNames.get(position), catCounts.get(position), position == 0);
            return t;
        }
    }
}
