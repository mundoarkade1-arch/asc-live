# DECISIONS — decisões e motivos

1. **Pacote `com.asclive.tvbox`** e chave de Keystore `asc_live_key`, para não conflitar com o app antigo.
2. **Telas montadas em código Java** (sem XML de layout): menos ids cruzados para errar e mais leve.
3. **Reaproveitado do ASC Cine** só a lógica: login, perfis, atualização, código do aparelho,
   segurança, imagens. Filmes, séries, Top 10, banner e progresso foram apagados.
4. **Código do aparelho:** mesmo Supabase do app antigo (o ASC Admin continua igual).
5. **Só canais ao vivo na lista.** `Catalog` guarda a lista completa e aplica o controle parental
   na hora de devolver (ligar ou desligar o parental não pede rede).
6. **(SUBSTITUÍDA pela 18)** Versões do mesmo canal viram um canal (chave = nome sem acento e sem SD/HD/FHD/4K/H265/50fps/
   colchetes/"BR:"; "Sport TV" = "SporTV"). Ordem: FHD, HD, sem qualidade, SD, 4K. H265 depois da
   mesma qualidade (o TX9 pode não aguentar). 4K só abre sozinho se for a única opção.
7. **Fonte dos jogos:** API pública da ESPN (não oficial, sem chave). Pede de ontem a amanhã e filtra
   pelo dia do aparelho (o fuso da ESPN pode ser outro). Se falhar: "Sem jogos agora".
8. **Qual canal passa qual jogo:** tabela editável (`transmissoes.json`) + canais da lista com o nome
   de um dos times (mínimo de 4 letras). Nenhuma fonte grátis é 100% certa.
9. **Player (LivePlayer):** ExoPlayer único; buffer 1500/8000/500/1000 ms; timeouts 4 s e 6 s; fallback de
   decodificador; 1080p máximo; `.m3u8` identificado sem adivinhar; TS direto com
   `FLAG_ALLOW_NON_IDR_KEYFRAMES`. Trocar de canal = `setMediaItem` + `prepare`.
10. **Estabilidade:** erro ou 8 s travado -> próxima versão; acabaram as versões -> 3 reconexões
    (1, 2, 4 s) só da primeira versão; só então mostra erro. Última versão boa por canal fica em
    SharedPreferences (`asc_live_last`, guarda só o id do canal, nunca o endereço).
11. **Ao sair do player** (Voltar ou app em segundo plano) o vídeo para e a conexão é solta: servidores
    IPTV limitam telas ao mesmo tempo.
12. **Pré-aquecer** (focar 300 ms em um canal): só DNS + conexão TCP curta, sem pedir o vídeo, para não
    gastar uma "tela" do servidor.
13. **Zapping:** a troca de canal aparece na tela na hora, mas o vídeo só pede o novo canal 250 ms
    depois (quem zapeia rápido não abre vários canais à toa).
14. **Favoritos** guardam a chave do canal, então valem para todas as versões; ficam por perfil
    (arquivo `asc_favs_<perfil>`, criptografado).
15. **Busca em memória** (sem índice em disco), pela chave do canal: sem acento, sem ligar para HD/4K.
16. **Controle remoto:** nas telas, a grade e as listas controlam o foco (linhas não são focáveis).
    A Ao vivo tem categorias à esquerda; direita leva à grade, esquerda na primeira coluna volta
    às categorias.
17. **Removido:** "Itens bloqueados" (só existia na tela de detalhe do Cine) e `UserLists`.
18. **Cada resolução é um canal (como no ASC TV Box).** O agrupamento SD/HD/FHD/4K (decisão 6) e a tela de
    escolher resolução foram tirados a pedido do dono: a lista mostra "Premiere 1 SD", "Premiere 1 HD"... e
    OK abre o canal escolhido direto. `ChannelGroup` ficou com uma só versão (a classe e a lista `variants`
    ficaram para não mexer no player). Favoritos e busca usam `ChannelGroup.key()` (nome com resolução).
19. **Programação (EPG) igual ao TV Box:** "Agora / A seguir" nos cartões (só dos canais visíveis, 1 min de
    cache) e janela completa na tecla de menu. OK longo continua favoritando (o TV Box usa OK longo para a
    programação; aqui ele é dos favoritos). Player: Baixo = próximo canal, Cima = anterior (como no TV Box).
