# STATUS — ASC Live

Plano completo em `docs/PLANO.md`. **Todas as etapas (0 a 10) foram escritas.**
O código inteiro foi compilado no ambiente do agente contra um `android.jar` real (API 33), com
classes de teste no lugar do `androidx.media3` e `androidx.core`. O **build do GitHub Actions** e o
**teste no aparelho (TX9)** ainda são a prova final: ninguém abriu o app num TV Box ainda.

## Rodada 11 (ajuste pedido pelo dono, depois de testar no aparelho)
O dono achou o app "estranho" e pediu: **mesma aparência**, mas o **canal funcionando como no ASC TV Box**.
Feito (compilado só por sintaxe; o build do Actions e o teste no TX9 são a prova final):
- [x] Sem agrupar resoluções: cada canal aparece com a SUA resolução (Premiere 1 SD, HD, FHD...). OK abre
      direto, sem tela de escolher resolução (`ChannelGroup` agora é 1 canal = 1 versão).
- [x] Cartões mostram "Agora" e "A seguir" (guia de programação), pedido só dos canais na tela
      (`EpgCache` + `ChannelAdapter`). Só no login por servidor (M3U não tem guia).
- [x] Tecla das três listras sobre um canal (grade ou player) abre a programação (`EpgDialog`).
      Segurar OK continua favoritando.
- [x] Player: Baixo = próximo canal, Cima = canal anterior (igual ao TV Box). Faixa de informação mostra o
      programa de agora. Esquerda/Direita não trocam mais "versão".
- [x] Futebol: jogo -> emissora -> canais, cada um com a sua resolução -> assiste (sem tela de versão).
- [x] Ajustes: tirada a "Qualidade preferida" (não faz mais sentido).
- Lista: já lia SÓ canais ao vivo (Xtream: `get_live_streams`; M3U: filtra `isLive`). Nada mudou aí.
- **Favoritos antigos não valem mais** (a chave agora inclui a resolução): é só favoritar de novo.

## Etapas
- [x] 0  Repositório criado (dono)
- [x] 1  Renomear e limpar
- [x] 2  Ler só os canais ao vivo (Catalog, LiveCache)
- [x] 3  Agrupar versões SD/HD/FHD/4K (ChannelNames, ChannelGroup)
- [x] 4  Player ao vivo rápido e estável (LivePlayer, LivePlayerActivity)
- [x] 5  Tela Ao vivo (LiveScreen)
- [x] 6  Favoritos e Buscar de canais
- [x] 7  Jogos do dia (FootballApi, Match, assets/ligas.json)
- [x] 8  Quais canais passam o jogo (Broadcasts, assets/transmissoes.json)
- [x] 9  Tela Futebol (FootballScreen, MatchCard, BroadcastDialog)
- [x] 10 Acabamento (Ajustes, boas-vindas, ícone e banner novos, limpeza)

**Próximo passo: testar no aparelho** e corrigir o que aparecer (ver "Para testar" abaixo).

## Controle remoto no player
- Baixo = próximo canal, Cima = canal anterior (como no ASC TV Box). OK = lista rápida. Números = digita o canal.
- Menu (três listras) = programação do canal. Voltar = sai do player (ou fecha a lista).

## Para testar (o que ninguém viu rodando)
1. Build do GitHub passa? (a API do `androidx.media3` 1.4.1 foi escrita de memória; se o build reclamar
   de algum método do `LivePlayer`, é lá.)
2. Login por servidor Xtream e por M3U; a tela Ao vivo mostra categorias e canais.
3. Canal abre rápido? Troca com cima/baixo? Cai para HD/SD sozinho quando o FHD falha?
4. Futebol: aparecem jogos de hoje? (fonte ESPN, não oficial) Os canais por emissora fazem sentido?
5. Foco do controle remoto em todas as telas (setas, OK, Voltar, menu).

## Pontos de atenção
- `assets/transmissoes.json` é uma sugestão inicial de emissoras por campeonato. **O dono precisa
  conferir e ajustar** (é só editar o arquivo, sem mexer em código).
- ESPN: se mudar a API ou cair, a tela Futebol mostra "Sem jogos agora" e o resto do app segue.
- O pré-aquecimento só resolve o DNS e abre uma conexão curta (sem pedir o vídeo), para não gastar
  uma "tela" do servidor IPTV.
- Jogos encerrados aparecem depois dos que vão começar (ao vivo sempre primeiro).
- Nenhum endereço de canal, usuário, senha ou servidor é registrado em log.
