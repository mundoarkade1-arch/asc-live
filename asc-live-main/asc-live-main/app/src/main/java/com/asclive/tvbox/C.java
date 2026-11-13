package com.asclive.tvbox;

/**
 * Paleta ASC Live (as mesmas de res/values/colors.xml, para usar direto no código).
 * Regra de uso: 70% fundo, 20% secundária (cards, barras, menus), 10% destaque.
 */
final class C {
    // 70% - fundo
    static final int BG = 0xFF03231F;
    // 20% - cards, barras e menus (um pouco mais claros que o fundo)
    static final int SURFACE = 0xFF0A3A33;
    static final int SURFACE_FOCUS = 0xFF0F4B42;
    // 10% - destaque (botão principal, item selecionado, barra de progresso, ao vivo)
    static final int ACCENT = 0xFF0E9F7E;
    static final int ACCENT_40 = 0x660E9F7E;
    // Dourado suave: SÓ em pequenos indicadores (ícone selecionado, marcador, estrela)
    static final int GLOW = 0xFFD4B06A;
    // Texto
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT2 = 0xFFB5CFCA;
    static final int TEXT3 = 0xFF86A8A1;
    // Borda de 1 px: branco com 8% de opacidade
    static final int LINE = 0x14FFFFFF;
    static final int ERROR = 0xFFE58C8C;
    /** Barras sobre o vídeo (topo, informações do canal). */
    static final int SHADE = 0xE6031A17;
    /** Cards: um pouco mais claros que o fundo, com borda sutil. */
    static final int GLASS = 0xF20A3A33;
    static final int GLASS_HI = 0xFF0F4B42;
    static final int GLASS_EDGE = 0x14FFFFFF;
    /** Menus e janelas. */
    static final int MENU = 0xF00A3A33;
    static final int SMOKE = 0xE60A3A33;
    static final int SMOKE_DEEP = 0xF70A3A33;
    /** Trilho de barras de progresso. */
    static final int TRACK = 0x33FFFFFF;

    private C() {
    }
}
