# ASC Live

App Android nativo (Java) para TV Box, só com **TV ao vivo e Futebol** (sem filmes e séries). Veja `docs/PLANO.md`.
Os canais funcionam como no ASC TV Box: cada canal aparece com a sua resolução e abre direto.
Lê uma lista IPTV por servidor (Xtream Codes), link M3U ou código do aparelho.

- Cores: preto `#101010`, branco `#FFFFFF` e verde-limão `#C8FF00`.
- Feito para TV Box antigo (exemplo: TX9, Android 7.1, 1 GB de RAM).
- Controle remoto: setas, OK, Voltar e a tecla de menu (três listras).
- O APK é gerado e assinado pelo GitHub Actions a cada mudança na `main`.

Documentos para quem for continuar o projeto: pasta `docs/` e arquivo `AGENTS.md`.

## Como o APK nasce
1. Você sobe os arquivos para o GitHub (branch `main`).
2. O Actions compila, assina e publica uma Release (`v1`, `v2`...) com `ASC-Live.apk` e `version.json`.
3. O app instalado olha o `version.json` e oferece a atualização.

Sem os 4 Secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) o APK sai
só como arquivo de teste e **não** é publicado.
