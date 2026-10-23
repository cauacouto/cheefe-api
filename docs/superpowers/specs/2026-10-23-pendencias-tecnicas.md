# Pendências técnicas das revisões (avaliações, bloqueio de conta, filtro no chat)

Itens adiados nas revisões de `2026-10-22-avaliacoes`, `2026-10-22-bloqueio-conta` e `2026-10-23-filtro-chat` (ledgers em `.superpowers/sdd/`). Migration desta etapa: **V10**.

## Banco (V10)

1. `avaliacao_tag`: chave primária `(avaliacao_id, tag)` (hoje sem PK no Postgres).
2. Índice em `avaliacao (autor_id)` (exclusão de conta e consultas por autor).
3. Índice parcial para a rotina de publicação: `avaliacao (publicado_em) WHERE publicado_em IS NULL AND anonimo = false` — ou o equivalente que a consulta `idsComNomeParaPublicar` realmente use.
4. Entidades refletindo o mesmo schema onde o `MigracoesPostgresTest` compara.

## Código

5. `UsuarioService.deletar`: apagar também as notificações `CONTA_BLOQUEADA` dos moderadores que apontam para os bloqueios do cliente (hoje os bloqueios somem e as notificações ficam apontando para nada).
6. Avaliação repetida ao mesmo tempo (mesmo autor, corrida no unique `uk_avaliacao_agendamento_autor_avaliado`): responder **409** "Você já avaliou essa pessoa neste agendamento", não a mensagem genérica de integridade.
7. `AgendamentoMapper` (`clienteNotaMedia`): sem NPE se `usuario` for nulo.
8. `FiltroConteudo`: recusar na inicialização termo com menos de 3 caracteres (o `***` nunca pode deixar um texto maior que o limite da coluna).

## Testes que faltam

9. Rotina de publicação: um id que falha não impede os demais (log e segue); avaliação já publicada mantém o horário; anônima não é tocada.
10. `Reputacao.media`: fronteira do HALF_UP (ex.: soma/total que dá 4,45 → 4,5; 4,44 → 4,4) e `total < 5` → nulo.
11. Lista pública com `resposta` preenchida; moderador em `GET /clientes/{id}/reputacao` → 403.
12. Denúncia de avaliação repetida ao mesmo tempo → um 201 e um 409.
13. Chat: o evento de tempo real (SSE) da mensagem sai mascarado; `editar` chama `verificar` antes de gravar (teste de ordem); fila de denúncia de mensagem com `textoOriginal` nulo quando nada foi mascarado.
14. Postgres: o bloqueio automático (transação `REQUIRES_NEW`) não fica esperando a trava da conversa quando o ódio vem numa mensagem do chat (teste com timeout).
15. Exclusão de conta do cliente que avaliou **dois** chefs: os dois contadores descontados; notificação `AVALIACAO_RESPONDIDA` some.
16. Moderador chamando `POST`/`GET` de avaliação → 403; chef autor com ódio na avaliação → 403 e conta bloqueada.

## Fora

Os adiados que são escolha de produto (anônima publicada na hora, 409 x 404 em comentário com nome não publicado, `notaMedia` sempre primeira chave) ficam como estão.
