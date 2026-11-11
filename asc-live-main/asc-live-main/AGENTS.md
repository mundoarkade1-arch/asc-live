# AGENTS.md — instruções para quem continuar este projeto

Leia primeiro: `docs/STATUS.md`, `docs/PLANO.md`, depois `docs/DECISIONS.md` e `docs/ARQUIVOS.md`.

## O dono
- Muito leigo, fala por ditado de voz (se a frase não faz sentido, pergunte).
- Português simples, frases curtas, uma ideia por frase, uma pergunta por vez.
- Tudo pelo site do GitHub, sem terminal. Nunca peça senha, chave de assinatura nem o link da lista.
- Se algo der errado, não culpe: peça print ou o texto do erro e devolva o arquivo COMPLETO corrigido.

## Regras do projeto
- Java 8, minSdk 21, targetSdk/compileSdk 34. Sem Kotlin, sem Compose, sem WebView.
- Dependências: só `androidx.media3` (exoplayer, exoplayer-hls, ui, 1.4.1) e `androidx.core`.
- App só de TV ao vivo e Futebol (ASC Live). Nada de filmes e séries. O plano está em `docs/PLANO.md`; faça UMA etapa por vez.
- Rede e disco SEMPRE fora da thread principal (use `Bg`).
- Nunca registrar em log: senha, link M3U, servidor/usuário/senha, endereço de vídeo, chave do aparelho.
- Telas montadas em código (sem XML de layout) para reduzir erro de id: veja `Ui`, `Card`, `Screen`.
- Em GridView/ListView a lista controla foco, seleção e clique; as linhas NÃO são focáveis.
- Cores só pela paleta (`C.java` e `colors.xml`). Texto sobre limão é sempre `#101010`.
- Nome do app: só em `strings.xml` (`app_name`). Repositório do Updater: só em `Updater.REPOSITORY`.
- Serviço do código do aparelho (Supabase): constantes no topo de `DeviceLink.java`.
- Ao fim de cada etapa: atualizar `docs/STATUS.md` e, se mudou, `DECISIONS.md` e `ARQUIVOS.md`.
