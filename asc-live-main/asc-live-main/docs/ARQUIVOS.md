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
| M3uApi | Parser M3U: URLs, metadados e cabeçalhos por canal |
| LivePlayer | Reprodução Media3 com DataSource e cabeçalhos por transmissão |
| XtreamApi, LiveCache | Formato de reprodução e persistência de metadados IPTV |
| `app/src/test/.../M3uApiHttpTest.java` | Teste local de download, redirect e erro HTTP da playlist M3U |
| `app/src/test/.../XtreamApiTest.java` | Testes de geração de URLs Xtream |
| `app/src/test/.../PlaybackDiagnosticsTest.java` | Testes do relatório persistente e sanitização |
| PlaybackDiagnostics, PlaybackDiagnosticsDialog | Histórico privado, visualização e exportação do diagnóstico IPTV |

## Futebol (dados)
| Arquivo | Para que serve |
|---|---|
| FootballApi, Match | jogos do dia pela API pública da ESPN (cache de 15 min) |
| Broadcasts | quais canais passam o jogo (tabela + nome dos times) |
| `assets/ligas.json` | ligas consultadas (editável) |
| `assets/transmissoes.json` | emissoras por liga (editável) |

## Build e atualização
| Arquivo | Para que serve |
|---|---|
| `gradlew`, `gradlew.bat`, `gradle/wrapper` | Gradle Wrapper 8.7 para build local e CI |
| `.github/workflows/android.yml` | Testes, APKs, artefatos e Release assinado por tag |
| `app/build.gradle` | Assinatura Release opcional por variáveis/segredos |
| `Updater`, `UpdateUi` | Consulta GitHub, valida SHA-256 e abre o instalador Android |

## Ferramentas
Ui, C, Bg, Dialogs, ImageLoader, AppPrefs.
