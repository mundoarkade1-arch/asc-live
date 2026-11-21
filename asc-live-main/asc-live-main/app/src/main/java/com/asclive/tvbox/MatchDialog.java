package com.asclive.tvbox;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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

/**
 * Janelinha de um jogo: mostra SÓ os canais confirmados para transmitir. Cada canal aparece uma
 * vez, com as qualidades dele em botões (FHD, HD, SD, 4K): escolher uma abre o canal direto.
 * Se nada está confirmado, diz isso e oferece (separado e avisando) os canais que costumam passar.
 */
final class MatchDialog {

    private final ShellActivity act;
    private final Match match;
    private final Dialog dialog;
    private final LinearLayout list;
    private final TextView title;

    private MatchDialog(ShellActivity act, Match match) {
        this.act = act;
        this.match = match;
        dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.75f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.glassStrong(act, 20));
        int p = Ui.dp(act, 26);
        box.setPadding(p, Ui.dp(act, 22), p, Ui.dp(act, 20));

        // Cabeçalho: escudos, times, liga e horário.
        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(shield(match.escudoCasa), new LinearLayout.LayoutParams(Ui.dp(act, 56),
                Ui.dp(act, 56)));
        LinearLayout words = new LinearLayout(act);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView teams = Ui.title(act, match.casa + "  x  " + match.fora, 22, C.TEXT);
        teams.setSingleLine(true);
        teams.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(teams);
        String when = new SimpleDateFormat("EEE, dd/MM 'às' HH:mm", new Locale("pt", "BR"))
                .format(new Date(match.horario));
        TextView sub = Ui.text(act, match.liga + "  ·  " + when, 15, C.TEXT2);
        words.addView(sub);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, -2, 1f);
        wlp.leftMargin = Ui.dp(act, 14);
        wlp.rightMargin = Ui.dp(act, 14);
        head.addView(words, wlp);
        head.addView(shield(match.escudoFora), new LinearLayout.LayoutParams(Ui.dp(act, 56),
                Ui.dp(act, 56)));
        box.addView(head, new LinearLayout.LayoutParams(-1, -2));

        title = Ui.text(act, "Procurando canais...", 16, C.TEXT2);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = Ui.dp(act, 16);
        tlp.bottomMargin = Ui.dp(act, 8);
        box.addView(title, tlp);

        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, new ScrollView.LayoutParams(-1, -2));
        box.addView(sv, new LinearLayout.LayoutParams(-1, Ui.dp(act, 300)));

        dialog.setContentView(box, new android.view.ViewGroup.LayoutParams(Ui.dp(act, 680),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    static void show(ShellActivity act, Match match) {
        final MatchDialog d = new MatchDialog(act, match);
        Dialogs.show(d.dialog, act);
        d.loadConfirmed();
    }

    // ---------- Carregar ----------

    private void loadConfirmed() {
        Bg.run(act, () -> Broadcasts.confirmedFor(act, match), new Bg.Done<List<ChannelGroup>>() {
            @Override
            public void ok(List<ChannelGroup> v) {
                if (v == null || v.isEmpty()) {
                    showNone();
                } else {
                    title.setText("Canais confirmados para este jogo");
                    fill(v);
                }
            }

            @Override
            public void fail(Exception e) {
                title.setText("Não foi possível ler a sua lista agora.");
            }
        });
    }

    private void loadProbable() {
        title.setText("Procurando canais que costumam passar...");
        list.removeAllViews();
        Bg.run(act, () -> {
            List<ChannelGroup> flat = new ArrayList<>();
            for (Broadcasts.Broadcast b : Broadcasts.channelsFor(act, match)) {
                if (!"Canais do jogo".equals(b.emissora)) {
                    flat.addAll(b.canais);
                }
            }
            return flat;
        }, new Bg.Done<List<ChannelGroup>>() {
            @Override
            public void ok(List<ChannelGroup> v) {
                if (v == null || v.isEmpty()) {
                    title.setText("Nenhum canal desse tipo na sua lista.");
                    return;
                }
                title.setText("Costumam passar (NÃO confirmado)");
                fill(v);
            }

            @Override
            public void fail(Exception e) {
                title.setText("Não foi possível ler a sua lista agora.");
            }
        });
    }

    private void showNone() {
        String info = Broadcasts.infoFor(match);
        title.setText(info.isEmpty() ? "Ainda não há canal confirmado para este jogo."
                : "Nenhum desses canais está na sua lista.");
        list.removeAllViews();
        String st = Broadcasts.statusFor(match);
        if (!st.isEmpty()) {
            TextView stv = Ui.text(act, "(" + st + ")", 12, C.TEXT2);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
            sl.bottomMargin = Ui.dp(act, 10);
            list.addView(stv, sl);
        }
        if (!info.isEmpty()) {
            TextView where = Ui.text(act, "Segundo a grade de TV do Brasil, este jogo passa em: "
                    + info, 15, C.TEXT);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
            wl.bottomMargin = Ui.dp(act, 12);
            list.addView(where, wl);
        }
        TextView more = Ui.button(act, "Ver canais que costumam passar", Ui.DARK);
        more.setOnClickListener(v -> loadProbable());
        list.addView(more, new LinearLayout.LayoutParams(-1, -2));
        more.post(more::requestFocus);
    }

    // ---------- Montar a lista ----------

    private void fill(List<ChannelGroup> flat) {
        list.removeAllViews();
        // Um canal por linha; as qualidades dele viram botões.
        Map<String, List<ChannelGroup>> byChannel = new LinkedHashMap<>();
        for (ChannelGroup g : flat) {
            String k = ChannelNames.key(g.nome);
            List<ChannelGroup> l = byChannel.get(k);
            if (l == null) {
                l = new ArrayList<>();
                byChannel.put(k, l);
            }
            l.add(g);
        }
        View first = null;
        for (List<ChannelGroup> versions : byChannel.values()) {
            sortByQuality(versions);
            View row = addRow(versions);
            if (first == null) {
                first = row;
            }
        }
        if (first != null) {
            final View f = first;
            f.post(f::requestFocus);
        }
    }

    private static void sortByQuality(List<ChannelGroup> l) {
        java.util.Collections.sort(l, (a, b) -> Integer.compare(ChannelNames.rank(a.quality()),
                ChannelNames.rank(b.quality())));
    }

    /** Linha de um canal. Devolve o primeiro botão de qualidade (para o foco). */
    private View addRow(final List<ChannelGroup> versions) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.glass(act, 14));
        row.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8));
        ImageView logo = new ImageView(act);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.load(logo, versions.get(0).logo, 120);
        row.addView(logo, new LinearLayout.LayoutParams(Ui.dp(act, 44), Ui.dp(act, 44)));
        TextView name = Ui.title(act, ChannelNames.display(versions.get(0).nome), 18, C.TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, -2, 1f);
        nlp.leftMargin = Ui.dp(act, 12);
        row.addView(name, nlp);
        View firstChip = null;
        for (int i = 0; i < versions.size(); i++) {
            final int at = i;
            ChannelGroup g = versions.get(i);
            String label = g.variants.get(0).label();
            TextView chip = Ui.text(act, label.isEmpty() ? "Abrir" : label, 16, C.TEXT);
            chip.setTypeface(Ui.bold(act));
            chip.setGravity(Gravity.CENTER);
            chip.setFocusable(true);
            chip.setClickable(true);
            chip.setPadding(Ui.dp(act, 16), Ui.dp(act, 10), Ui.dp(act, 16), Ui.dp(act, 10));
            chip.setBackground(Ui.glassFocusBg(act, 10));
            chip.setOnClickListener(v -> play(versions, at));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.leftMargin = Ui.dp(act, 8);
            row.addView(chip, clp);
            if (firstChip == null) {
                firstChip = chip;
            }
        }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.bottomMargin = Ui.dp(act, 8);
        list.addView(row, rlp);
        return firstChip;
    }

    private void play(List<ChannelGroup> versions, int at) {
        dialog.dismiss();
        LiveSession.set(new ArrayList<>(versions), at, -1);
        act.startActivity(new Intent(act, LivePlayerActivity.class));
    }

    private ImageView shield(String url) {
        ImageView v = new ImageView(act);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.load(v, url, 120);
        return v;
    }
}
