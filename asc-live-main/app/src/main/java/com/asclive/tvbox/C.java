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
    // Dourado fosco: contorno de foco, item selecionado do menu, barra de progresso,
    // estrelas e setas de play. O verde continua nos botões, etiquetas e fundos.
    static final int GOLD = 0xFFD4B06A;
    // Texto
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT2 = 0xFFB5CFCA;
    static final int TEXT3 = 0xFF86A8A1;
    // Borda de 1 px: branco com 8% de opacidade
    static final int LINE = 0x14FFFFFF;
    static final int ERROR = 0xFFE58C8C;
    /** Barras sobre o vídeo (topo, informações do canal). */
    static final int SHADE = 0xE6031A17;
    /**
     * Vidro (glassmorfismo): o verde do painel fica translúcido e leva um brilho branco que
     * vai de cima/esquerda (mais claro) para baixo/direita (mais escuro). O fundo aparece por
     * trás. Sem blur de verdade (pesa em TV Box); o efeito vem da transparência + brilho + borda.
     */
    static final int GLASS = 0xB30A3A33;          // cor média do vidro (usos sem degradê)
    static final int GLASS_TOP = 0x4DFFFFFF;      // brilho branco do canto de cima (30%)
    static final int GLASS_BOTTOM = 0x0FFFFFFF;   // brilho do canto de baixo (6%)
    static final int GLASS_TINT = 0xA60A3A33;     // verde por baixo do brilho
    static final int GLASS_HI = 0xD90F4B42;       // vidro em foco: mais claro e mais firme
    static final int GLASS_EDGE = 0x33FFFFFF;     // borda fina: branco 20%
    /** Menus e janelas: vidro mais firme, para o texto ler bem. */
    static final int MENU = 0xD90A3A33;
    static final int SMOKE = 0xE60A3A33;
    static final int SMOKE_DEEP = 0xF20A3A33;
    static final int GLASS_STRONG_TINT = 0xE60A3A33;
    /** Esqueleto de carregamento: bloco de vidro e brilho que passa. */
    static final int SKELETON = 0x1AFFFFFF;
    static final int SKELETON_SHINE = 0x2EFFFFFF;
    /** Trilho de barras de progresso. */
    static final int TRACK = 0x33FFFFFF;

    private C() {
    }
}
