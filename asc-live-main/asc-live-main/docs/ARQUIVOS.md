# ARQUIVOS — o que cada arquivo faz (pasta `app/src/main/java/com/asclive/tvbox`)

## Entrada e login
| Arquivo | Para que serve |
|---|---|
| MainActivity | porta de entrada: senha do app -> perfis -> login ou tela principal |
| LoginActivity, LoginChecker | login por servidor Xtream, link M3U ou código do aparelho |
| ProfilesActivity, Profiles | perfis (até 5) |
| AppLock, ParentalStore, PinDialog | senha do app e controle parental |
| CredentialStore, Secure | login do aparelho e criptografia (Keystore, chave `asc_live_key`) |
| DeviceLink | código do aparelho (mesmo Supabase do ASC Admin) |
| Updater, UpdateUi | atualização do app pelo GitHub |

## Lista de canais
| Arquivo | Para que serve |
|---|---|
| XtreamApi | servidor Xtream: categorias, canais ao vivo, guia (EPG), endereço do canal |
| M3uApi | lista M3U: lê só canais ao vivo |
| LiveCache | arquivo guardado (criptografado, 6 h) com categorias e canais |
| Catalog | ponte única: `liveCategories`, `liveChannels`, `allLive`, `allGroups`, `urlOf`, `clearCache` |
| ChannelNames | nome do canal -> chave (`fullKey` com resolução), qualidade (SD/HD/FHD/UHD4K), H265 |
| ChannelGroup | UM canal da lista (nome com resolução, logo, categoria); chave e qualidade calculadas sob demanda |
| EpgCache | programação "agora / a seguir" dos canais (1 min, 80 canais, 2 pedidos por vez) |
| EpgDialog | janela da programação do canal (tecla de menu) |

## Player
| Arquivo | Para que serve |
|---|---|
| LivePlayer | UM ExoPlayer reaproveitado; buffer de ao vivo; troca de versão e reconexão; pré-aquecer |
| LivePlayerActivity | tela cheia: zapping, versão, lista rápida, número do canal |
| LiveSession | lista de canais, posição e versão passadas ao player (em memória) |

## Telas
| Arquivo | Para que serve |
|---|---|
| ShellActivity, Sidebar, Screen | tela principal: menu lateral + uma tela por vez |
| FootballScreen, MatchCard, BroadcastDialog | Futebol: jogos -> emissoras -> canais (cada um com a sua resolução) |
| LiveScreen | Ao vivo: categorias + grade de canais |
| FavoritesScreen, Favorites | canais favoritos por perfil |
| SearchScreen, SearchKeyboard | buscar canal |
| GroupGridScreen, ChannelAdapter, ChannelCard, TvGrid | base e peças da grade de canais |
| SettingsScreen | Ajustes (limpar cache, segurança, atualização) |

## Futebol (dados)
| Arquivo | Para que serve |
|---|---|
| FootballApi, Match | jogos do dia pela API pública da ESPN (cache de 15 min) |
| Broadcasts | quais canais passam o jogo (tabela + nome dos times) |
| `assets/ligas.json` | ligas consultadas (editável) |
| `assets/transmissoes.json` | emissoras por liga (editável) |

## Ferramentas
Ui, C, Bg, Dialogs, ImageLoader, AppPrefs.
