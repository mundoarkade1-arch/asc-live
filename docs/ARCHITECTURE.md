# ARCHITECTURE

```
MainActivity (porta de entrada: senha do app -> perfis -> login ou início)
 ├─ ProfilesActivity   escolher / gerenciar perfis
 ├─ LoginActivity      servidor | M3U | código do aparelho (consulta a cada 30 s)
 └─ ShellActivity      barra lateral (Sidebar) + UMA tela por vez (Screen)
      ├─ HomeScreen (HeroView, faixas, Top 10)   ├─ GridScreen (Filmes/Séries)
      ├─ ExploreScreen   ├─ SearchScreen   ├─ FavoritesScreen   └─ SettingsScreen
 ├─ DetailActivity     filme/série (temporadas, episódios, favoritar, bloquear)
 └─ PlayerActivity     ExoPlayer + controles por controle remoto
```

Dados: `Catalog` é a única ponte para a lista (Xtream ou M3U): categorias, páginas de 40, 200 mais
recentes, Top 10, destaques, detalhes, episódios, busca, endereços e chaves de progresso.
Tudo que faz rede roda em `Bg` (3 threads) e só volta à tela se ela ainda existir.

Armazenamento (todo em SharedPreferences privado): login e Continue assistindo (AES-GCM / Keystore),
favoritos e bloqueados (`Secure`), progresso (chaves SHA-1), perfis, ajustes.
Índice de busca: arquivo em `search_index/` (TTL 2 h, até 200000 itens e 60 MB).

Foco: a barra lateral é um overlay (não refaz o layout das telas). Seta direita volta ao conteúdo;
Voltar no conteúdo vai à barra; Voltar na barra vai ao Início; Voltar no Início fecha o app.
