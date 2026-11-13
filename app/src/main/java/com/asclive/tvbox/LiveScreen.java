package com.asclive.tvbox;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tela "Canais": categorias à esquerda, lista de canais no meio (número, logo, nome, programa
 * e resolução) e, à direita, o canal escolhido com o programa de agora e o próximo.
 * Cada canal aparece com a sua resolução (SD, HD, FHD, 4K): é só escolher qual assistir.
 * OK abre o canal; OK segurado favorita; a tecla das três listras mostra a programação.
 */
final class LiveScreen extends Screen {

    private static final long PREWARM_MS = 300L;

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
    private final TvGrid grid;
    private final ChanAdapter adapter = new ChanAdapter();
    private final TextView notice;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Handler hp = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final boolean epgOn;

    private final List<String> catIds = new ArrayList<>();
    private final List<String> catNames = new ArrayList<>();
    private final List<Integer> catCounts = new ArrayList<>();
    private final Map<String, String> catById = new HashMap<>();
    private final Map<String, String> nowText = new HashMap<>();
    private final Map<String, Long> askedAt = new HashMap<>();
    private List<ChannelGroup> all = new ArrayList<>();
    private List<ChannelGroup> shown = new ArrayList<>();
    private int chosen;
    private boolean loading;
    private String detailId = "";

    // Painel da direita
    private final ImageView dLogo;
    private final TextView dBadge;
    private final TextView dName;
    private final TextView dCat;
    private final TextView dProg;
    private final TextView dTime;
    private final LinearLayout dBar;
    private final View dFill;
    private final View dRest;
    private final TextView dNext;

    LiveScreen(ShellActivity act) {
        super(act);
        CredentialStore.Credentials cr = Catalog.creds(act);
        epgOn = cr != null && !Catalog.isM3u(cr);

        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(Ui.dp(act, 4), Ui.dp(act, 10), Ui.dp(act, 4), 0);

        // ---- Categorias ----
        FrameLayout catPanel = new FrameLayout(act);
        catPanel.setBackground(Ui.glass(act, 22));
        catList = new ListView(act);
        catList.setDivider(null);
        catList.setDividerHeight(Ui.dp(act, 4));
        catList.setVerticalScrollBarEnabled(false);
        catList.setSelector(Ui.shape(0x00000000, Ui.dp(act, 12), C.ACCENT, Ui.dp(act, 2)));
        catList.setDrawSelectorOnTop(true);
        catList.setClipToPadding(false);
        catList.setPadding(Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12));
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
            focusChannels();
        });
        catList.setOnKeyListener((v, keyCode, e) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && e.getAction() == KeyEvent.ACTION_DOWN) {
                focusChannels();
                return true;
            }
            return false;
        });
        catPanel.addView(catList, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams cpl = new LinearLayout.LayoutParams(0, -1, 2.8f);
        cpl.rightMargin = Ui.dp(act, 12);
        cpl.bottomMargin = Ui.dp(act, 6);
        root.addView(catPanel, cpl);

        // ---- Canais ----
        FrameLayout chanPanel = new FrameLayout(act);
        grid = new TvGrid(act);
        grid.setNumColumns(1);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(Ui.dp(act, 8));
        grid.setSelector(Ui.shape(0x00000000, Ui.dp(act, 14), C.ACCENT, Ui.dp(act, 3)));
        grid.setDrawSelectorOnTop(true);
        grid.setPadding(Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 12));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> open(pos));
        grid.setLongListener(this::longPick);
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                onChannelSelected(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                hp.removeCallbacksAndMessages(null);
            }
        });
        grid.setOnKeyListener((v, keyCode, e) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && e.getAction() == KeyEvent.ACTION_DOWN) {
                focusCategories();
                return true;
            }
            return false;
        });
        chanPanel.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        notice = Ui.text(act, "", 20, C.TEXT2);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(Ui.dp(act, 24), 0, Ui.dp(act, 24), 0);
        chanPanel.addView(notice, new FrameLayout.LayoutParams(-1, -1));
        notice.setVisibility(View.GONE);
        LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(0, -1, 4.8f);
        chl.rightMargin = Ui.dp(act, 12);
        root.addView(chanPanel, chl);

        // ---- Detalhes do canal escolhido ----
        LinearLayout side = new LinearLayout(act);
        side.setOrientation(LinearLayout.VERTICAL);
        FrameLayout preview = new FrameLayout(act);
        preview.setBackground(Ui.glass(act, 18));
        dLogo = new ImageView(act);
        dLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        dLogo.setPadding(Ui.dp(act, 22), Ui.dp(act, 22), Ui.dp(act, 22), Ui.dp(act, 22));
        preview.addView(dLogo, new FrameLayout.LayoutParams(-1, -1));
        TextView live = Ui.limeTag(act, "AO VIVO");
        FrameLayout.LayoutParams lvl = new FrameLayout.LayoutParams(-2, -2);
        lvl.gravity = Gravity.TOP | Gravity.START;
        lvl.setMargins(Ui.dp(act, 12), Ui.dp(act, 12), 0, 0);
        preview.addView(live, lvl);
        dBadge = Ui.pill(act, "", 13, 0x66000000, C.TEXT);
        dBadge.setTypeface(Ui.bold(act));
        FrameLayout.LayoutParams bdl = new FrameLayout.LayoutParams(-2, -2);
        bdl.gravity = Gravity.TOP | Gravity.END;
        bdl.setMargins(0, Ui.dp(act, 12), Ui.dp(act, 12), 0);
        preview.addView(dBadge, bdl);
        side.addView(preview, new LinearLayout.LayoutParams(-1, Math.round(Ui.screenH(act) * 0.2f)));

        dName = Ui.title(act, "", 24, C.TEXT);
        dName.setSingleLine(true);
        dName.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = Ui.dp(act, 12);
        side.addView(dName, nl);
        dCat = Ui.text(act, "", 15, C.TEXT2);
        dCat.setSingleLine(true);
        dCat.setEllipsize(TextUtils.TruncateAt.END);
        side.addView(dCat, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout prog = new LinearLayout(act);
        prog.setOrientation(LinearLayout.VERTICAL);
        prog.setBackground(Ui.glass(act, 18));
        prog.setPadding(Ui.dp(act, 16), Ui.dp(act, 12), Ui.dp(act, 16), Ui.dp(act, 12));
        dProg = Ui.title(act, "", 20, C.TEXT);
        dProg.setMaxLines(2);
        dProg.setEllipsize(TextUtils.TruncateAt.END);
        prog.addView(dProg, new LinearLayout.LayoutParams(-1, -2));
        dTime = Ui.text(act, "", 15, C.TEXT2);
        prog.addView(dTime, new LinearLayout.LayoutParams(-1, -2));
        dBar = new LinearLayout(act);
        dBar.setOrientation(LinearLayout.HORIZONTAL);
        dBar.setBackground(Ui.shape(C.TRACK, Ui.dp(act, 3), 0, 0));
        dFill = new View(act);
        dFill.setBackground(Ui.shape(C.ACCENT, Ui.dp(act, 3), 0, 0));
        dRest = new View(act);
        dBar.addView(dFill, new LinearLayout.LayoutParams(0, -1, 0f));
        dBar.addView(dRest, new LinearLayout.LayoutParams(0, -1, 100f));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(act, 5));
        bl.topMargin = Ui.dp(act, 8);
        prog.addView(dBar, bl);
        dBar.setVisibility(View.GONE);
        TextView nextHead = Ui.title(act, "Próximos Programas", 16, C.TEXT);
        LinearLayout.LayoutParams nhl = new LinearLayout.LayoutParams(-1, -2);
        nhl.topMargin = Ui.dp(act, 12);
        prog.addView(nextHead, nhl);
        dNext = Ui.text(act, "", 16, C.TEXT2);
        dNext.setMaxLines(7);
        dNext.setEllipsize(TextUtils.TruncateAt.END);
        prog.addView(dNext, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.topMargin = Ui.dp(act, 12);
        side.addView(prog, pl);
        root.addView(side, new LinearLayout.LayoutParams(0, -1, 3.2f));
    }

    @Override
    View view() {
        return root;
    }

    @Override
    View firstFocus() {
        return catAdapter.getCount() > 0 ? catList : null;
    }

    @Override
    String hints() {
        return "";
    }

    @Override
    void onShow() {
        load();
    }

    @Override
    void onResume() {
        // Voltou do player: os favoritos podem ter mudado (a estrela da linha).
        adapter.notifyDataSetChanged();
    }

    @Override
    void onHide() {
        super.onHide();
        h.removeCallbacksAndMessages(null);
        hp.removeCallbacksAndMessages(null);
    }

    @Override
    void onPause() {
        hp.removeCallbacksAndMessages(null);
    }

    private void focusChannels() {
        if (grid.getCount() > 0) {
            grid.requestFocus();
        }
    }

    private void focusCategories() {
        catList.requestFocus();
    }

    // ---------- Carregar ----------

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

    private void showNotice(String text) {
        notice.setText(text);
        notice.setVisibility(View.VISIBLE);
    }

    private void show(Data d) {
        all = d.groups;
        catIds.clear();
        catNames.clear();
        catCounts.clear();
        catById.clear();
        catIds.add(null);
        catNames.add("Todos os canais");
        catCounts.add(all.size());
        Map<String, Integer> count = new HashMap<>();
        for (ChannelGroup g : all) {
            Integer n = count.get(g.categoryId);
            count.put(g.categoryId, n == null ? 1 : n + 1);
        }
        for (Catalog.Cat c : d.cats) {
            Integer n = count.get(c.id);
            if (n == null || n == 0) {
                continue;
            }
            catIds.add(c.id);
            catNames.add(c.name);
            catCounts.add(n);
            catById.put(c.id, c.name);
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
        // Quem passa rápido pelas categorias não monta a lista a cada tecla.
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
        shown = out;
        adapter.notifyDataSetChanged();
        grid.setSelection(0);
        if (out.isEmpty()) {
            showNotice("Nenhum canal nesta categoria.");
        } else {
            notice.setVisibility(View.GONE);
        }
        updateDetail(out.isEmpty() ? null : out.get(0));
    }

    // ---------- Ações no canal ----------

    private void onChannelSelected(int pos) {
        if (pos < 0 || pos >= shown.size()) {
            hp.removeCallbacksAndMessages(null);
            return;
        }
        final ChannelGroup g = shown.get(pos);
        updateDetail(g);
        hp.removeCallbacksAndMessages(null);
        hp.postDelayed(() -> LivePlayer.prewarm(act, g), PREWARM_MS);
    }

    /** Abre o player no canal, com a lista de agora (cima/baixo troca de canal). */
    private void open(int pos) {
        if (pos < 0 || pos >= shown.size()) {
            return;
        }
        LiveSession.set(new ArrayList<>(shown), pos, -1);
        act.startActivity(new Intent(act, LivePlayerActivity.class));
    }

    private void longPick(int pos) {
        if (pos < 0 || pos >= shown.size()) {
            return;
        }
        ChannelGroup g = shown.get(pos);
        boolean now = Favorites.toggle(act, g.key());
        Ui.toast(act, now ? "Canal favoritado." : "Canal tirado dos favoritos.");
        adapter.notifyDataSetChanged();
    }

    /** Tecla das três listras sobre um canal: abre a programação (só login por servidor). */
    @Override
    boolean handleMenu() {
        if (!grid.hasFocus()) {
            return false;
        }
        int pos = grid.getSelectedItemPosition();
        if (pos < 0 || pos >= shown.size()) {
            return false;
        }
        CredentialStore.Credentials c = Catalog.creds(act);
        if (c == null || Catalog.isM3u(c)) {
            Ui.toast(act, "A programa\u00e7\u00e3o s\u00f3 existe em lista por servidor.");
            return true;
        }
        ChannelGroup g = shown.get(pos);
        EpgDialog.show(act, c.server, c.user, c.pass, g.item().id, g.nome);
        return true;
    }

    // ---------- Painel da direita ----------

    private String catName(ChannelGroup g) {
        String n = catById.get(g.categoryId);
        return n == null ? "" : n;
    }

    private void setProgress(int pct) {
        if (pct < 0) {
            dBar.setVisibility(View.GONE);
            return;
        }
        ((LinearLayout.LayoutParams) dFill.getLayoutParams()).weight = pct;
        ((LinearLayout.LayoutParams) dRest.getLayoutParams()).weight = 100 - pct;
        dBar.requestLayout();
        dBar.setVisibility(View.VISIBLE);
    }

    private void updateDetail(ChannelGroup g) {
        if (g == null) {
            detailId = "";
            dName.setText("");
            dCat.setText("");
            dLogo.setImageDrawable(null);
            dBadge.setVisibility(View.GONE);
            dProg.setText("");
            dTime.setText("");
            dNext.setText("");
            setProgress(-1);
            return;
        }
        final String id = g.item().id;
        detailId = id;
        dName.setText(g.nome);
        dCat.setText(catName(g));
        ImageLoader.load(dLogo, g.logo, 360);
        String label = g.variants.get(0).label();
        dBadge.setText(label);
        dBadge.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
        dTime.setText("");
        dNext.setText("");
        setProgress(-1);
        if (!epgOn) {
            dProg.setText("Sem guia de programação nesta lista.");
            return;
        }
        dProg.setText("Carregando programação...");
        CredentialStore.Credentials c = Catalog.creds(act);
        if (c == null) {
            return;
        }
        EpgCache.getSchedule(c.server, c.user, c.pass, id, programs -> {
            if (!id.equals(detailId)) {
                return;
            }
            if (programs == null || programs.isEmpty()) {
                dProg.setText("Programação indisponível.");
                return;
            }
            long now = System.currentTimeMillis();
            XtreamApi.Program current = null;
            for (XtreamApi.Program p : programs) {
                if (p.start <= now && now < p.end) { current = p; break; }
            }
            XtreamApi.Program shown = current != null ? current : programs.get(0);
            dProg.setText(shown.title);
            dTime.setText(clock.format(new Date(shown.start)) + " - "
                    + clock.format(new Date(shown.end)));
            long span = shown.end - shown.start;
            setProgress(current != null && span > 0
                    ? (int) Math.max(0, Math.min(100, (now - shown.start) * 100 / span)) : -1);
            StringBuilder next = new StringBuilder();
            int added = 0;
            for (XtreamApi.Program p : programs) {
                if (p == current || p.end <= now || added >= 5) continue;
                if (next.length() > 0) next.append("\n");
                next.append(clock.format(new Date(p.start))).append("   ").append(p.title);
                added++;
            }
            dNext.setText(next.toString());
        });
    }

    // ---------- Linhas ----------

    private String subFor(ChannelGroup g) {
        String t = nowText.get(g.item().id);
        return t != null && !t.isEmpty() ? t : catName(g);
    }

    /** Pede ao servidor o programa de agora da linha (só login por servidor). */
    private void rowEpg(final ChannelRow row, ChannelGroup g) {
        if (!epgOn) {
            return;
        }
        final String id = g.item().id;
        long now = System.currentTimeMillis();
        Long when = askedAt.get(id);
        if (when != null && now - when < 20000L) {
            return;
        }
        if (askedAt.size() > 400) {
            askedAt.clear();
            nowText.clear();
        }
        askedAt.put(id, now);
        CredentialStore.Credentials c = Catalog.creds(act);
        if (c == null) {
            return;
        }
        final String fallback = catName(g);
        EpgCache.get(c.server, c.user, c.pass, id, epg -> {
            String t = epg == null ? "" : epg.title;
            nowText.put(id, t);
            if (id.equals(row.boundId)) {
                row.setSub(t.isEmpty() ? fallback : t);
            }
        });
    }

    private final class ChanAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            ChannelRow r = convert instanceof ChannelRow ? (ChannelRow) convert
                    : new ChannelRow(act, true);
            ChannelGroup g = shown.get(position);
            r.bind(position + 1, g, Favorites.isFavorite(act, g.key()), subFor(g));
            rowEpg(r, g);
            return r;
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
            CatRow r = convert instanceof CatRow ? (CatRow) convert : new CatRow(act);
            r.bind(catNames.get(position), catCounts.get(position), position == chosen);
            return r;
        }
    }

    /** Uma categoria: nome à esquerda e quantos canais à direita. */
    private static final class CatRow extends LinearLayout {
        private final TextView name;
        private final TextView count;

        CatRow(ShellActivity a) {
            super(a);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
            name = Ui.text(a, "", 19, C.TEXT);
            name.setSingleLine(false);
            name.setMaxLines(2);
            name.setEllipsize(null);
            addView(name, new LayoutParams(0, -2, 1f));
            count = Ui.text(a, "", 15, C.TEXT2);
            LayoutParams cl = new LayoutParams(-2, -2);
            cl.leftMargin = Ui.dp(a, 8);
            addView(count, cl);
        }

        void bind(String title, int n, boolean on) {
            name.setText(title);
            name.setTextColor(C.TEXT);
            name.setTypeface(on ? Ui.bold(getContext()) : android.graphics.Typeface.DEFAULT);
            count.setText(String.valueOf(n));
            setBackground(on ? Ui.shape(C.GLASS_HI, Ui.dp(getContext(), 12), 0, 0) : null);
        }
    }
}
