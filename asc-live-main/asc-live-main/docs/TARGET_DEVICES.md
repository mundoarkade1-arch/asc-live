# TARGET_DEVICES

Aparelho de teste do dono: **TX9** (Amlogic S905W, Android 7.1, 1 GB de RAM, 1080p).

Regras para esse aparelho: poucas views, um fundo por tela, sem blur, sem Palette, imagens reduzidas
e em RGB_565, cache de imagens de 16 MB, buffer de TV ao vivo curto (1,5 a 8 s),
vídeo limitado a 1080p (4K só se a pessoa escolher, e pode travar), `hardware.touchscreen` e `leanback` como `required=false`.
Densidade: os tamanhos dos cartões são calculados pela largura da tela (`Ui.gridPw`).
