package com.asclive.tvbox;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Um jogo (vem da API pública da ESPN). Só leitura. */
final class Match {

    static final int SCHEDULED = 0;
    static final int LIVE = 1;
    static final int FINISHED = 2;

    final String id;
    /** Código da liga na ESPN (ex.: bra.1): é a chave do arquivo transmissoes.json. */
    final String ligaId;
    final String liga;
    final String casa;
    final String fora;
    final String escudoCasa;
    final String escudoFora;
    /** Hora do começo, em milissegundos (System.currentTimeMillis). */
    final long horario;
    /** SCHEDULED, LIVE ou FINISHED. */
    final int status;
    /** Texto curto do andamento: "45'", "Intervalo", "Encerrado", "Adiado"... Pode ser vazio. */
    final String statusText;
    /** "2 x 1"; vazio se o jogo ainda não começou. */
    final String placar;
    /** Id do time da casa/de fora na ESPN (vazio se a fonte não diz). */
    final String casaId;
    final String foraId;
    /** Estádio (pode ser vazio). */
    final String local;
    /** Emissoras que a própria fonte informa para o jogo (pode ser vazio). */
    final List<String> emissoras;

    Match(String id, String ligaId, String liga, String casa, String fora, String escudoCasa,
          String escudoFora, long horario, int status, String statusText, String placar) {
        this(id, ligaId, liga, casa, fora, escudoCasa, escudoFora, horario, status, statusText,
                placar, "", "", "", null);
    }

    Match(String id, String ligaId, String liga, String casa, String fora, String escudoCasa,
          String escudoFora, long horario, int status, String statusText, String placar,
          String casaId, String foraId, String local, List<String> emissoras) {
        this.id = id;
        this.ligaId = ligaId;
        this.liga = liga;
        this.casa = casa;
        this.fora = fora;
        this.escudoCasa = escudoCasa;
        this.escudoFora = escudoFora;
        this.horario = horario;
        this.status = status;
        this.statusText = statusText;
        this.placar = placar;
        this.casaId = casaId == null ? "" : casaId;
        this.foraId = foraId == null ? "" : foraId;
        this.local = local == null ? "" : local;
        this.emissoras = emissoras == null ? Collections.<String>emptyList() : emissoras;
    }

    boolean isLive() {
        return status == LIVE;
    }

    /** O time (pelo id da ESPN) joga neste jogo? */
    boolean hasTeam(String teamId) {
        return teamId != null && !teamId.isEmpty()
                && (teamId.equals(casaId) || teamId.equals(foraId));
    }

    /** O adversário de quem tem o id dado ("" se o id não joga aqui). */
    String opponentOf(String teamId) {
        if (teamId.equals(casaId)) {
            return fora;
        }
        return teamId.equals(foraId) ? casa : "";
    }

    JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("li", ligaId);
            o.put("ln", liga);
            o.put("c", casa);
            o.put("f", fora);
            o.put("ec", escudoCasa);
            o.put("ef", escudoFora);
            o.put("h", horario);
            o.put("s", status);
            o.put("t", statusText);
            o.put("p", placar);
            o.put("ic", casaId);
            o.put("if", foraId);
            o.put("lo", local);
            JSONArray b = new JSONArray();
            for (String e : emissoras) {
                b.put(e);
            }
            o.put("b", b);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static Match fromJson(JSONObject o) {
        List<String> b = new ArrayList<>();
        JSONArray a = o.optJSONArray("b");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                b.add(a.optString(i, ""));
            }
        }
        return new Match(o.optString("id"), o.optString("li"), o.optString("ln"),
                o.optString("c"), o.optString("f"), o.optString("ec"), o.optString("ef"),
                o.optLong("h"), o.optInt("s"), o.optString("t"), o.optString("p"),
                o.optString("ic"), o.optString("if"), o.optString("lo"), b);
    }
}
