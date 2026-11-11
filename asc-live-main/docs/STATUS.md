# STATUS — ASC Live

Plano completo em `docs/PLANO.md`. **Todas as etapas (0 a 10) foram escritas.**
O código inteiro foi compilado no ambiente do agente contra um `android.jar` real (API 33), com
classes de teste no lugar do `androidx.media3` e `androidx.core`. O **build do GitHub Actions** e o
**teste no aparelho (TX9)** ainda são a prova final: ninguém abriu o app num TV Box ainda.

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
- Cima = próximo canal, Baixo = canal anterior (se achar invertido, troque em `LivePlayerActivity.onKeyDown`).
- Esquerda / Direita = versão do canal (FHD, HD, SD...). OK = lista rápida. Números = digita o canal.
- Voltar = sai do player (ou fecha a lista).

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
