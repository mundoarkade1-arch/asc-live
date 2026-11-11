package com.asclive.tvbox;

/** Um jogo do dia (vem da API pública da ESPN). Só leitura. */
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

    Match(String id, String ligaId, String liga, String casa, String fora, String escudoCasa,
          String escudoFora, long horario, int status, String statusText, String placar) {
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
    }

    boolean isLive() {
        return status == LIVE;
    }
}
