# STATUS — ASC Live

Projeto nasceu como cópia do ASC Cine. O plano completo está em `docs/PLANO.md`.
**Nada foi compilado ainda** (sem Android SDK no ambiente do agente). O primeiro build do GitHub Actions é o teste real.

## Etapas
- [x] 0  Repositório criado (feito pelo dono)
- [x] 1  Renomear e limpar (feito, ver abaixo)
- [ ] 2  Ler só os canais ao vivo (Catalog)
- [ ] 3  Agrupar versões SD/HD/FHD/4K
- [ ] 4  Player ao vivo rápido e estável
- [ ] 5  Tela Ao vivo
- [ ] 6  Favoritos e Buscar de canais
- [ ] 7  Jogos do dia (dados)
- [ ] 8  Quais canais passam o jogo
- [ ] 9  Tela Futebol
- [ ] 10 Acabamento

**Próxima etapa: 2.**

## O que a Etapa 1 fez
- Pacote `com.asclive.tvbox`; nome "ASC Live"; chave Keystore `asc_live_key`; `Updater.REPOSITORY` = `mundoarkade1-arch/asc-live`; APK `ASC-Live.apk`.
- Apagadas TODAS as telas e o player de filme/série: HomeScreen, HeroView, GridScreen, ExploreScreen, DetailActivity, SearchScreen,
  FavoritesScreen, PlayerActivity, PlayQueue, ContinueStore, SeriesProgress, Nav (e as entradas no manifest).
- Menu lateral: Futebol, Ao vivo, Favoritos, Buscar, Atualizar, Ajustes. As quatro telas são `PlaceholderScreen` (só título).
- **Hoje o app NÃO toca nenhum vídeo.** Isso é de propósito; o player ao vivo vem na Etapa 4.

## Sobra interna do Cine (código sem tela, o usuário não alcança)
- `Catalog`, `XtreamApi`, `M3uApi`, `SearchIndex`, `HomeCache`, `PlaybackProgress` ainda têm métodos de filme/série.
- **Etapa 2 deve apagar esses métodos** e deixar só canais ao vivo. Depois da Etapa 2, apagar também HomeCache, PlaybackProgress e SearchIndex se ninguém mais usar.

## Erros conhecidos
- Nenhum informado ainda. Se o build falhar, cole só as linhas do erro.
