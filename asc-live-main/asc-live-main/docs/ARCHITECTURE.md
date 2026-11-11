# ARCHITECTURE

```
MainActivity (porta de entrada: senha do app -> perfis -> login ou tela principal)
 ├─ ProfilesActivity   escolher / gerenciar perfis
 ├─ LoginActivity      servidor | M3U | código do aparelho
 ├─ ShellActivity      barra lateral (Sidebar) + UMA tela por vez (Screen)
 │    ├─ FootballScreen   jogos do dia -> BroadcastDialog (emissora -> canal -> versão)
 │    ├─ LiveScreen       categorias + grade de canais
 │    ├─ FavoritesScreen  canais favoritos
 │    ├─ SearchScreen     busca de canal
 │    └─ SettingsScreen
 └─ LivePlayerActivity    tela cheia; usa o LivePlayer (um ExoPlayer só)
```

Dados de canais: `Catalog` é a única ponte para a lista (Xtream ou M3U). Ordem: memória, arquivo
(`LiveCache`, 6 h), servidor. Só canais AO VIVO são lidos. `Catalog.allGroups` devolve UM `ChannelGroup`
por canal da lista (cada resolução é um canal, como no ASC TV Box); a chave de busca é calculada só quando
alguém pede. Programação (EPG): `EpgCache` (1 min, 80 canais) nos cartões e no player; `EpgDialog` na tecla
de menu.

Futebol: `FootballApi` (ESPN, cache de 15 min) devolve `Match`; `Broadcasts` cruza o jogo com os
canais da lista usando `assets/transmissoes.json` e o nome dos times.

Player: `LivePlayer` cria o ExoPlayer uma vez. Erro ou travada de 8 s: reconecta (1, 2 e 4 s) e só então
mostra o erro (cada canal tem uma versão só).

Tudo que faz rede ou disco pesado roda em `Bg` (ou thread própria) e nunca na thread principal.
Armazenamento privado: login (Keystore), favoritos e cache de canais (`Secure`), perfis, ajustes.

Foco: a barra lateral é um overlay (não refaz o layout). Seta direita volta ao conteúdo; Voltar no
conteúdo vai à barra; Voltar na barra vai a Futebol; Voltar em Futebol pergunta se quer sair.
