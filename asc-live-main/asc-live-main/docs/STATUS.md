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

## Rodada 12 (abertura direta do canal)
- [x] Removida a capa opaca intermediÃ¡ria com "Abrindo..."; o PlayerView aparece imediatamente.
- [x] A abertura continua usando o item exato selecionado, sem escolher outra resoluÃ§Ã£o.
- [x] Buffer ao vivo ajustado para 2,5–15 s, com margem maior para oscilaÃ§Ãµes de IPTV e rebuffer de 3 s.
- [ ] Validar no TV Box: tempo atÃ© o primeiro quadro, travamentos e reconexÃµes.

## Rodada 13 (navegacao, futebol, EPG e tema)
- [x] Sidebar substituida por navegacao superior compacta com foco visivel.
- [x] Rodape de dicas removido da casca principal.
- [x] Jogos do dia reorganizados em cards por campeonato, com estados carregando, erro e vazio.
- [x] Canais correspondentes aparecem dentro do card do jogo e abrem com OK.
- [x] EPG mostra o atual com progresso e ate seis proximos programas.
- [x] Tema centralizado em salvia, marfim e vermilion, sem gradientes.
- [ ] Compilar no GitHub Actions e validar no TV Box.

## Controle remoto no player
- Baixo = próximo canal, Cima = canal anterior (como no ASC TV Box). OK = lista rápida. Números = digita o canal.
- Menu (três listras) = programação do canal. Voltar = sai do player (ou fecha a lista).

## Rodada 14 (correções finais antes do teste no aparelho)
- [x] Player protegido contra grupos/variantes inválidos e falhas de inicialização; erro não encerra o app.
- [x] ESPN validada com HTTP 200 para `bra.1` e `bra.copa_do_brazil`; filtro local usa margem de 8 horas.
- [x] Jogos agora distinguem erro de rede de nenhum jogo e têm botão Tentar de novo.
- [x] TheSportsDB v1 (chave pública demo 3, sem chave do usuário) adicionada como fonte reserva.
- [x] Barra superior corrigida para acentos nítidos e foco preenchido sem borda vermelha.
- [x] Canais e EpgDialog mostram o programa atual e até cinco próximos; Xtream solicita 10 itens.
- [ ] Compilar no GitHub Actions e validar no TV Box.

## Rodada 15 (correção após teste do dono)
- [x] Corrigada a consulta ESPN: o endpoint não aceita intervalo de datas; agora consulta ontem, hoje e amanhã separadamente.
- [x] Os IDs das ligas retornam HTTP 200; eventos do dia passam a ser processados corretamente.
- [x] Xtream passa a tentar `.ts`, formato mais compatível para canais ao vivo, e tenta `.m3u8` como reserva no mesmo canal.
- [ ] Validar abertura de canal e jogos no TV Box com a nova versão.

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
## Rodada 16 (ajuste de canais e cards de jogos)
- [x] OK/Enter do controle remoto agora dispara o clique da grade mesmo quando o aparelho não marca o evento como tracking.
- [x] Cards de jogos deixaram de listar linhas grandes de canais.
- [x] Emissoras e canais aparecem como ícones horizontais compactos.
- [x] Clique no card abre o primeiro canal transmissor; clique no ícone abre aquele canal específico.
- [ ] Validar o fluxo completo no TV Box.
## Rodada 17 (identidade ASC Live e tela inicial esportiva)
- [x] Paleta migrada para preto, superfícies escuras e ciano `#00D9FF`.
- [x] Criado cabeçalho com marca, busca, relógio, data, conectividade e ajustes.
- [x] Navegação lateral compacta adicionada e integrada às telas existentes.
- [x] Tela inicial refeita com faixa horizontal de jogos, foco remoto e painel de destaque.
- [x] Canais relacionados aparecem no painel da partida e abrem o player existente diretamente.
- [ ] Validar visual e foco no TV Box.
