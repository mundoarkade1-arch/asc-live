package com.asclive.tvbox;

import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela "Ao vivo": categorias à esquerda e grade de canais (logo + nome) à direita.
 * Como no ASC TV Box, cada canal aparece com a sua resolução (SD, HD, FHD, 4K): é só escolher
 * qual assistir. OK abre o canal direto.
 */
final class LiveScreen extends GroupGridScreen {

    private static final class Data {
        final List<Catalog.Cat> cats;
        final List<ChannelGroup> groups;

        Data(List<Catalog.Cat> cats, List<ChannelGroup> groups) {
            this.cats = cats;
            this.groups = groups;
        }
    }

    private final LinearLayout root;
    private final ListView catList;
    private final CatAdapter catAdapter = new CatAdapter();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<String> catIds = new ArrayList<>();
    private final List<String> catNames = new ArrayList<>();
    private List<ChannelGroup> all = new ArrayList<>();
    private int chosen;
    private boolean loading;

    LiveScreen(ShellActivity act) {
        super(act);
        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(C.BG);

        catList = new ListView(act);
        catList.setDivider(null);
        catList.setVerticalScrollBarEnabled(false);
        catList.setSelector(Ui.shape(C.SURFACE_FOCUS, Ui.dp(act, 12), C.LIME, Ui.dp(act, 2)));
        catList.setDrawSelectorOnTop(false);
        catList.setPadding(Ui.dp(act, 14), Ui.dp(act, 18), Ui.dp(act, 8), Ui.dp(act, 18));
        catList.setClipToPadding(false);
        catList.setAdapter(catAdapter);
        catList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                pickCategory(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        catList.setOnItemClickListener((parent, view, pos, id) -> {
            pickCategory(pos);
            if (hasChannels()) {
                grid.requestFocus();
            }
        });
        catList.setOnKeyListener((v, keyCode, e) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && e.getAction() == KeyEvent.ACTION_DOWN) {
                if (hasChannels()) {
                    grid.requestFocus();
                }
                return true;
            }
            return false;
        });
        grid.setOnKeyListener((v, keyCode, e) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && e.getAction() == KeyEvent.ACTION_DOWN) {
                int pos = grid.getSelectedItemPosition();
                int cols = Math.max(1, grid.getNumColumns());
                if (pos < 0 || pos % cols == 0) {
                    catList.requestFocus();
                    return true;
                }
            }
            return false;
        });
        root.addView(catList, new LinearLayout.LayoutParams(Ui.dp(act, 270), -1));
        root.addView(gridHolder, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    @Override
    View view() {
        return root;
    }

    @Override
    View firstFocus() {
        if (catAdapter.getCount() > 0) {
            return catList;
        }
        return null;
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

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        showNotice("Carregando canais...");
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
        catIds.add(null);
        catNames.add("Todos os canais");
        for (Catalog.Cat c : d.cats) {
            catIds.add(c.id);
            catNames.add(c.name);
        }
        chosen = 0;
        catAdapter.notifyDataSetChanged();
        applyCategory(0);
        act.focusReady(this);
    }

    private void pickCategory(final int pos) {
        if (pos == chosen || pos < 0 || pos >= catIds.size()) {
            return;
        }
        chosen = pos;
        catAdapter.notifyDataSetChanged();
        // Quem passa rápido pelas categorias não monta a grade a cada tecla.
        h.removeCallbacksAndMessages(null);
        h.postDelayed(() -> applyCategory(pos), 120);
    }

    private void applyCategory(int pos) {
        String id = pos < catIds.size() ? catIds.get(pos) : null;
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
        setGroups(out, "Nenhum canal nesta categoria.");
        grid.setSelection(0);
    }

    @Override
    void onHide() {
        super.onHide();
        h.removeCallbacksAndMessages(null);
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
            TextView t = convert instanceof TextView ? (TextView) convert
                    : Ui.text(act, "", 19, C.TEXT);
            t.setText(catNames.get(position));
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setTextColor(position == chosen ? C.LIME : C.TEXT);
            t.setTypeface(position == chosen ? Ui.bold(act) : Ui.medium(act));
            t.setPadding(Ui.dp(act, 16), Ui.dp(act, 13), Ui.dp(act, 12), Ui.dp(act, 13));
            return t;
        }
    }
}
