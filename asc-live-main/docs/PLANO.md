# PLANO — ASC Live (só TV ao vivo + Futebol)

Este arquivo vai na pasta `docs/` do projeto novo. Toda IA lê ele primeiro.
Cada etapa é pequena (cabe no limite grátis). Ao terminar, a IA marca a etapa no `docs/STATUS.md`
e a próxima IA continua de onde parou.

## 1. O que é
App Android nativo (Java 8, minSdk 21), cópia do ASC Cine, mas só com **TV ao vivo** e **Futebol**.
Mesmas cores: `#101010`, `#FFFFFF`, `#C8FF00` (arquivo `C.java` e `colors.xml`). Mesmas regras do `AGENTS.md`.

## 2. Telas
```
Login (igual ao do Cine: servidor | M3U | código do aparelho)
Shell (menu lateral + uma tela por vez)
 ├─ Futebol      jogos do dia → toque no jogo → canais que passam → escolher a versão → assiste
 ├─ Ao vivo      categorias → grade de canais → assiste
 ├─ Favoritos    canais favoritos
 ├─ Buscar       busca de canal
 └─ Ajustes
Player ao vivo   tela cheia, cima/baixo troca canal, OK mostra lista, número digita canal
```
Sai do projeto: Filmes, Séries, Explorar, Detalhe, Top 10, Banner, progresso de reprodução, Continue assistindo.
Fica: Login, Perfis, Updater, DeviceLink, CredentialStore, Secure, ImageLoader, Ui, Card, Screen, Sidebar, TvGrid, Bg, C.

## 3. O que já existe no código (ajuda muito)
- `XtreamApi.Kind` já tem `LIVE`; já existem `Epg` e `Program` (guia de programação).
- `M3uApi` já separa canais ao vivo (`Sample.live`, `G_LIVE`, `LIVE_EXT`).
- `PlayerActivity` já usa ExoPlayer com `DefaultLoadControl` (buffer de filme, grande demais para canal).

## 4. Decisões

**A) Agrupar versões do mesmo canal.**
A lista traz "Premiere 1 SD", "Premiere 1 HD", "Premiere 1 FHD", "Premiere 1 4K".
Cria-se uma chave: nome em minúsculas, sem acento, sem `SD HD FHD UHD 4K H265 HEVC 50fps` e sem `[ ]`.
Todos com a mesma chave viram **um canal com várias versões**, ordenadas: FHD, HD, SD, 4K ao final
(4K só se o aparelho aguenta; o TX9 não aguenta, então 4K fica por último).
Classe nova: `ChannelGroup { key, nome, logo, List<Variant> }`.

**B) Fonte dos jogos do dia.**
A lista IPTV não traz tabela de jogos. Precisa de uma fonte externa, sem chave e grátis.
Escolhido: API pública da ESPN (`site.api.espn.com/apis/site/v2/sports/soccer/<liga>/scoreboard`),
ligas: `bra.1` (Brasileirão A), `bra.2` (B), `conmebol.libertadores`, `conmebol.sudamericana`,
`bra.copa_do_brazil`, `uefa.champions`, `eng.1`, `esp.1`, `ita.1`, `ger.1`, `fra.1`, `fifa.world`.
Ela é não oficial: se falhar, o app mostra "sem jogos agora" e continua funcionando.
Só HTTPS, só JSON, sempre fora da thread principal (`Bg`), cache de 15 min em arquivo.

**C) Qual canal transmite qual jogo.**
Nenhuma fonte grátis é 100% certa. Regra em duas camadas:
1. Tabela por campeonato em arquivo editável (`assets/transmissoes.json`), por exemplo
   `"bra.1": ["premiere", "sportv", "globo"]`, `"conmebol.libertadores": ["globo", "espn", "sportv"]`.
   O dono ajusta sem mexer no código.
2. Se a lista IPTV tiver canal de evento com o nome do time (ex.: "Flamengo x Palmeiras"), ele aparece também.
Cada palavra da tabela vira busca na lista de canais (`ChannelGroup.key` contém a palavra).
Ex.: "premiere" acha Premiere 1, 2, 3… e cada um mostra suas versões.

**D) Abrir canal o mais rápido possível (o centro do app).**
1. Um único `ExoPlayer` criado uma vez e reaproveitado. Trocar canal = `setMediaItem` + `prepare`, nunca recriar.
2. `DefaultLoadControl` de ao vivo: `setBufferDurationsMs(1500, 8000, 500, 1000)`, sem `setTargetBufferBytes` gigante.
3. Pré-aquecer: ao focar um canal na grade por 300 ms, já resolver a URL final e abrir DNS/conexão (`HEAD` ou conexão curta).
4. Preferir `.m3u8` (HLS) quando a lista oferece; para `.ts` direto usar `ProgressiveMediaSource`/TS extractor com
   `DefaultExtractorsFactory` e `FLAG_ALLOW_NON_IDR_KEYFRAMES`.
5. Manter o `OkHttp`-like simples: `DefaultHttpDataSource` com `connectTimeout 4000`, `readTimeout 6000`,
   `setKeepPostFor302Redirects(true)`, `setAllowCrossProtocolRedirects(true)`.
6. Manter a tela do player já montada (sem Activity nova a cada canal): o player fica numa `Activity` que só troca o canal.
7. Mostrar logo do canal + "Abrindo…" na hora, antes do vídeo chegar (sensação de instantâneo).

**E) Estabilidade.**
- Se der erro ou travar mais de 8 s: tentar automaticamente a próxima versão do mesmo canal (FHD → HD → SD).
- Reconectar sozinho (3 tentativas, espera 1 s, 2 s, 4 s) antes de mostrar erro.
- `setEnableDecoderFallback(true)`; limitar a 1080p no TX9 (`TrackSelectionParameters`).
- Lembrar a última versão que funcionou para cada canal (SharedPreferences) e começar por ela.
- Nunca registrar em log: URL do canal, usuário, senha, servidor (regra do `AGENTS.md`).

**F) Nome e pacote.** `ASC Live`, pacote `com.asclive.tvbox`, chave de Keystore `asc_live_key`.
Reaproveita o mesmo Supabase do código do aparelho.

## 5. Etapas

Cada etapa tem: objetivo, arquivos, **prompt para colar**, e como saber que acabou.
Faça **uma etapa por vez**. Pode trocar de IA entre etapas.

### Etapa 0 — Preparar o projeto novo (sem IA)
1. No GitHub, crie um repositório novo `asc-live`.
2. Suba os arquivos do `asc-cine-main` (ou use "Use this template/Fork" e renomeie).
3. Coloque este `PLANO.md` em `docs/`.
4. Crie os 4 Secrets do Actions (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
Pronto quando: o repositório existe com `docs/PLANO.md`.

### Etapa 1 — Renomear e limpar
Objetivo: virar "ASC Live", sem filmes e séries.
Arquivos: `app/build.gradle`, `AndroidManifest.xml`, `strings.xml`, `Updater.java`, pasta `java`
(mudar pacote para `com.asclive.tvbox`), apagar telas de filme/série.
Prompt:
> Leia `AGENTS.md`, `docs/PLANO.md` e `docs/ARQUIVOS.md`. Faça SÓ a Etapa 1: renomeie o app para "ASC Live" com pacote
> `com.asclive.tvbox`, troque a chave do Keystore para `asc_live_key`, e remova HomeScreen, HeroView, GridScreen, ExploreScreen,
> DetailActivity, ContinueStore, SeriesProgress, PlaybackProgress, HomeCache, SearchIndex de filmes. Deixe o app abrindo
> Login → Shell vazio com o menu: Futebol, Ao vivo, Favoritos, Buscar, Ajustes. Devolva os arquivos COMPLETOS alterados.
> Atualize `docs/STATUS.md` e `docs/ARQUIVOS.md`. Não faça mais nada.
Pronto quando: o build do GitHub passa e o app abre com o menu.

### Etapa 2 — Ler só os canais ao vivo
Objetivo: `Catalog` devolve categorias e canais ao vivo (Xtream e M3U), com cache em arquivo.
Arquivos: `Catalog.java`, `XtreamApi.java` (usar `Kind.LIVE`, `get_live_categories`, `get_live_streams`), `M3uApi.java`.
Prompt:
> Leia `AGENTS.md` e `docs/PLANO.md`. Faça SÓ a Etapa 2: em `Catalog`, `XtreamApi` e `M3uApi`, APAGUE todo código de filmes e séries (Kind.MOVIE, Kind.SERIES, EPISODE, detalhes, episódios, Top 10, HomeCache, PlaybackProgress, SearchIndex) e deixe apenas canais ao vivo.
> Métodos: `liveCategories()`, `liveChannels(categoriaId)`, `allLive()`. Xtream usa `get_live_categories` e `get_live_streams`;
> M3U usa só itens classificados como ao vivo. Guarde o resultado em arquivo criptografado (como o `HomeCache`), validade 6 h:
> abrir com cache na hora, atualizar por trás. Tudo fora da thread principal (`Bg`). Sem log de URL ou senha.
Pronto quando: existe `Catalog.allLive()` com cache funcionando.

### Etapa 3 — Agrupar versões (SD/HD/FHD/4K)
Objetivo: a decisão 4A.
Arquivos novos: `ChannelGroup.java`, `ChannelNames.java`.
Prompt:
> Leia `AGENTS.md` e `docs/PLANO.md` (decisão A). Faça SÓ a Etapa 3: crie `ChannelNames.key(String)` (normaliza nome) e
> `ChannelNames.quality(String)` (devolve SD, HD, FHD, UHD4K ou OUTRO), e `ChannelGroup` com a lista de versões ordenada
> FHD, HD, SD, 4K. Crie `Catalog.allGroups()` que agrupa `allLive()`. Escreva um teste simples em `main()` ou comentário
> com 10 exemplos reais ("Premiere 1 FHD", "PREMIERE 1 [HD]", "Sport TV 2 4K H265", "SporTV 2 SD"…).

### Etapa 4 — Player ao vivo rápido e estável
Objetivo: decisões D e E. É a etapa mais importante.
Arquivos: `PlayerActivity.java` → `LivePlayerActivity.java`, novo `LivePlayer.java` (singleton do ExoPlayer).
Prompt:
> Leia `AGENTS.md` e `docs/PLANO.md` (decisões D e E). Faça SÓ a Etapa 4: crie `LivePlayer` (um ExoPlayer reaproveitado,
> LoadControl de ao vivo 1500/8000/500/1000, timeouts 4 s/6 s, decoder fallback, 1080p máximo) e `LivePlayerActivity` em tela cheia.
> Método `play(ChannelGroup g, int versao)`. Erro ou travada > 8 s: tenta a próxima versão e reconecta (1 s, 2 s, 4 s).
> Cima/baixo troca de canal na lista atual; OK abre a lista rápida; mostra logo + "Abrindo…" na hora; lembra a última versão boa.
> Remova o código de filme (barra de progresso, pular 10 s, legendas). Nada de log de URL.

### Etapa 5 — Tela "Ao vivo"
Objetivo: categorias à esquerda ou em cima, grade de canais (logo + nome), OK abre o player.
Arquivos: `LiveScreen.java` (baseada em `GridScreen`), `CardAdapter.java`.
Prompt:
> Leia `AGENTS.md` e `docs/PLANO.md`. Faça SÓ a Etapa 5: `LiveScreen` com categorias e grade de `ChannelGroup` (cartão com logo).
> Ao focar um canal por 300 ms, chame `LivePlayer.prewarm(grupo)` (resolver URL e abrir conexão). OK abre `LivePlayerActivity`.
> Se o canal tem mais de uma versão, mostrar um selo (FHD, HD…) no cartão. Mesmo visual e cores do app.

### Etapa 6 — Favoritos e Buscar canal
Prompt:
> Leia `AGENTS.md` e `docs/PLANO.md`. Faça SÓ a Etapa 6: `FavoritesScreen` de canais (OK longo favorita/desfavorita, guardar com `Secure`
> por perfil) e `SearchScreen` de canais usando o `SearchKeyboard` e a busca por `ChannelNames.key`. Sem índice em disco: a busca é em memória.

### Etapa 7 — Jogos do dia (dados)
Objetivo: decisão B, sem tela ainda.
Arquivos novos: `Match.java`, `FootballApi.java`, `assets/ligas.json`.
Prompt:
> Leia `AGENTS.md` e `docs/PLANO.md` (decisão B). Faça SÓ a Etapa 7: `FootballApi.today()` busca o scoreboard da ESPN das ligas
> do `assets/ligas.json` em paralelo (`Bg`), converte para `Match { id, liga, casa, fora, escudoCasa, escudoFora, horario, status, placar }`,
> ordena por horário (ao vivo primeiro), usa o fuso do aparelho, cache de 15 min em arquivo. Só HTTPS. Se falhar, devolve lista vazia
> e não derruba o app. Não crie tela.

### Etapa 8 — Quais canais passam o jogo (dados)
Objetivo: decisão C.
Arquivos novos: `Broadcasts.java`, `assets/transmissoes.json`.
Prompt:
> Leia `AGENTS.md` e `docs/PLANO.md` (decisão C). Faça SÓ a Etapa 8: `Broadcasts.channelsFor(Match m)` lê `assets/transmissoes.json`
> (liga → lista de palavras, ex.: premiere, sportv, globo, espn, tnt sports, paramount, dazn), procura em `Catalog.allGroups()` os
> `ChannelGroup` cuja chave contém cada palavra e devolve a lista agrupada por emissora (Premiere → [Premiere 1, 2, 3…], Sport TV → […]).
> Também inclui canais cujo nome contém o nome do time da casa ou de fora. Preencha o JSON com as ligas do `ligas.json`.

### Etapa 9 — Tela "Futebol" (a parte principal)
Fluxo: Jogos do dia → toque no jogo → emissoras → toque na emissora → versões (SD, HD, FHD, 4K) → assiste.
Arquivos: `FootballScreen.java`, `MatchCard.java`, `BroadcastDialog.java`.
Prompt:
> Leia `AGENTS.md` e `docs/PLANO.md`. Faça SÓ a Etapa 9: `FootballScreen` com lista de jogos do dia (escudos, horário, placar, selo AO VIVO
> em limão). OK no jogo abre `BroadcastDialog` com as emissoras (Premiere, Sport TV…). OK na emissora mostra os canais dela
> (Premiere 1, Premiere 2…), e OK no canal mostra as versões (SD, HD, FHD, 4K) para escolher; ao escolher chama `LivePlayerActivity`.
> Se só houver uma opção em algum nível, pule esse nível. Mesmo visual, cores e foco do app. Controle remoto: setas, OK, Voltar.

### Etapa 10 — Acabamento e teste
Prompt:
> Leia `AGENTS.md` e `docs/PLANO.md`. Faça SÓ a Etapa 10: Ajustes (limpar cache, escolher qualidade preferida SD/HD/FHD), tela de
> boas-vindas, ícone/banner novos "ASC Live", revisar `docs/STATUS.md`, `ARQUIVOS.md`, `DECISIONS.md`, conferir foco do controle remoto
> em todas as telas e se não sobrou nenhum código de filme/série.

## 6. Como trocar de IA sem perder nada
1. A IA que estava trabalhando termina a etapa e atualiza `docs/STATUS.md` (o que foi feito, o que falta, erros conhecidos).
2. Você sobe os arquivos no GitHub e espera o build passar.
3. Na outra IA, cole o **prompt-base** abaixo e depois o prompt da próxima etapa.

**Prompt-base (cole sempre no começo):**
> Este é um projeto Android em Java (app de TV ao vivo para TV Box). O dono é leigo e fala por ditado; use português simples.
> Leia `AGENTS.md`, `docs/STATUS.md` e `docs/PLANO.md`. Siga as regras. Faça SÓ a etapa que eu pedir, devolva arquivos COMPLETOS,
> e atualize `docs/STATUS.md`. Se faltar um arquivo, me peça o nome dele; não invente.

**Se o limite acabar no meio de uma etapa:** peça "liste em 5 linhas o que já fez e o que falta", cole isso no `STATUS.md`
e passe para outra IA continuar a mesma etapa.

## 7. Dicas para gastar menos limite
- Envie só os arquivos que a etapa cita, não o projeto inteiro.
- Peça arquivo completo só dos que mudaram.
- Se der erro de build, cole só as linhas do erro e o nome do arquivo.
- Uma etapa por conversa nova.
