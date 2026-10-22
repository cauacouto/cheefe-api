# Avaliações (cliente ↔ chef) e bloqueio automático por discurso de ódio ou ameaça — Design

**Data:** 2026-10-22
**Status:** v1 — aprovado em conversa (partes 1, 2 e 3 + bloqueio automático), aguardando revisão do spec
**Branch:** `feat/avaliacoes`

## Objetivo

Depois de um agendamento concluído, o cliente avalia cada chef e cada chef avalia o cliente, no modelo da Uber (estrelas + tags, média anônima), com uma diferença: os comentários sobre chefs são públicos, porque passam confiança a quem vai receber alguém em casa. Conteúdo de ódio ou ameaça tira o autor do app na hora.

## Decisões

| Decisão | Escolha |
|---|---|
| Sentidos | Cliente → cada chef do agendamento; cada chef → cliente |
| Formato | Nota 1–5 (obrigatória) + até 5 tags da lista do lado certo + comentário opcional (até 1000) |
| Prazo | Até 7 dias depois da conclusão (`concluido_em`), para os dois lados |
| Mudar depois de enviar | Não (como na Uber) |
| Média | Só aparece com 5 avaliações ou mais; antes, nula ("Novo") |
| Comentário sobre o chef | Público no perfil do chef; anônimo por padrão ("Cliente verificado · outubro de 2026"); o cliente pode escolher aparecer pelo primeiro nome |
| Comentário com nome | Só é publicado quando o chef avalia o cliente daquele agendamento ou quando os 7 dias acabam |
| Comentário sobre o cliente | Privado: só a moderação lê |
| Reputação do cliente | Média, total e tags visíveis só a chefs que têm agendamento com ele |
| Resposta | O chef responde uma vez, em público, ao comentário publicado sobre ele; pode editar a resposta |
| Filtro de conteúdo | Lista em três categorias: ofensa (vira `***`), discurso de ódio e ameaça à integridade física (bloqueiam a conta na hora) |
| Bloqueio automático | Conteúdo não é publicado; a conta do autor fica bloqueada na hora (sessões caem); a moderação confirma (banimento) ou reverte |
| Denúncia | O chef denuncia comentário sobre ele; a moderação arquiva ou remove o comentário (a nota continua contando) |
| Média sem consulta extra | Contadores `soma_notas` / `total_avaliacoes` em `chefe` e `usuarios`, atualizados na mesma transação com trava no avaliado |

## Regras

### Quem avalia

- Só agendamento `CONCLUIDO`, dentro de 7 dias de `concluido_em` (`agora <= concluido_em + 7 dias`). Sem `concluido_em` (concluídos antes desta versão) → não avaliável (409).
- O cliente dono avalia cada chef **que está no agendamento** (`chefes`); quem recusou ou desistiu saiu e não aparece.
- Cada chef do agendamento avalia o cliente.
- Uma avaliação por (agendamento, autor, avaliado); repetida → 409.
- Quem não participa do agendamento → 404. Chef ou cliente bloqueado não age (ver Bloqueio).
- Nota fora de 1–5, tag do outro lado, mais de 5 tags, comentário com mais de 1000 caracteres → 400.

### Tags

| Sobre o chef (`TagAvaliacaoChef`) | Rótulo | Sobre o cliente (`TagAvaliacaoCliente`) | Rótulo |
|---|---|---|---|
| `PONTUAL` | Pontual | `EDUCADO` | Educado |
| `COMIDA_EXCELENTE` | Comida excelente | `RECEPTIVO` | Recebeu bem |
| `SIMPATICO` | Simpático | `COZINHA_ORGANIZADA` | Cozinha organizada |
| `COZINHA_LIMPA` | Deixou a cozinha limpa | `PAGOU_COMO_COMBINADO` | Pagou como combinado |
| `APRESENTACAO_CAPRICHADA` | Apresentação caprichada | `MUDOU_EM_CIMA_DA_HORA` | Mudou o combinado em cima da hora |
| `ATRASOU` | Atrasou | `DEMOROU_PARA_RECEBER` | Demorou para receber |
| `COMIDA_ABAIXO_DO_ESPERADO` | Comida abaixo do esperado | `COZINHA_SEM_CONDICOES` | Cozinha sem condições |
| `DEIXOU_SUJEIRA` | Deixou sujeira | `FALTA_DE_EDUCACAO` | Falta de educação |
| `NAO_CUMPRIU_O_COMBINADO` | Não cumpriu o combinado | | |

### Média e anonimato

- A nota entra na média do avaliado no momento em que a avaliação é criada (também a de comentário com nome ainda não publicado).
- `notaMedia` = `soma_notas / total_avaliacoes` com uma casa decimal, **nula se `total_avaliacoes < 5`**.
- Nenhuma resposta da API liga uma nota ou tag ao autor, exceto o comentário público em que o próprio cliente escolheu aparecer pelo primeiro nome. A lista pública não traz ids de autor nem de agendamento, e é ordenada por data de publicação (não de criação).
- Contagem de tags (`GET .../reputacao`) só agrega.

### Comentário público sobre o chef

- Anônimo (padrão): publicado na hora (`publicado_em = agora`), exibido como "Cliente verificado · {mês} de {ano}".
- Com nome (`anonimo = false`): `publicado_em` fica nulo até o chef avaliar o cliente daquele agendamento (publica na mesma transação) ou até a rotina publicar depois dos 7 dias; exibido como "{primeiro nome} · {mês} de {ano}".
- Avaliação sem comentário também aparece na lista pública (nota + tags), sempre anônima.
- Comentário removido pela moderação: "Comentário removido pela moderação" no lugar do texto; nota e tags continuam.

### Resposta do chef

- Só o chef avaliado, só em avaliação **publicada** com comentário (removido pela moderação não aceita resposta nova), até 1000 caracteres, passa pelo mesmo filtro. Responde uma vez; responder de novo substitui (edição). Não apaga a avaliação.

### Filtro de conteúdo

- Lista em `src/main/resources/moderacao/termos.yml` (fora do código, revisável), com as categorias `ofensa`, `odio` e `ameaca`.
- Comparação por palavra/expressão inteira, sem diferenciar maiúsculas nem acentos.
- `ofensa` → o termo vira `***` no texto exibido.
- `odio` ou `ameaca` → a avaliação (ou resposta) **não é gravada**, a conta do autor é **bloqueada** e a requisição responde 403 com "Conteúdo bloqueado e conta suspensa para análise da moderação".
- O texto original (antes do `***`) fica em `comentario_original` / `resposta_original`, visível só à moderação.
- Aplica-se a comentários e respostas de avaliação. O chat fica para uma etapa seguinte.

### Bloqueio de conta

- `usuarios` e `chefe` ganham `bloqueado_em` e `bloqueio_motivo`.
- Conta bloqueada: o `SecurityFilter` deixa de autenticar o token (as sessões abertas caem na próxima requisição: uma consulta por id) e o login com Google é recusado (redireciona ao front com `?erro=conta-bloqueada`).
- Bloqueio automático cria uma `bloqueio_conta` (urgente) na fila da moderação, com o texto que disparou, a categoria e o termo.
- Moderação: `PATCH /moderacao/bloqueios/{id}/confirmar` → banimento definitivo (chef: também `ativo = false`, como o banimento atual); `PATCH /moderacao/bloqueios/{id}/reverter` → desbloqueia. Ambos na auditoria (`CONFIRMAR_BLOQUEIO`, `REVERTER_BLOQUEIO`).
- Moderadores são notificados de cada bloqueio automático.

### Denúncia de comentário

- O chef denuncia comentário publicado sobre ele: `{ motivo: OFENSIVA | DADOS_PESSOAIS | FALSA | SPAM | OUTRO, descricao? }`; repetida → 409.
- Moderação: arquivar ou remover o comentário (`comentario_removido = true`). Auditoria: `ARQUIVAR_DENUNCIA_AVALIACAO`, `REMOVER_COMENTARIO_AVALIACAO`.

### Notificações

| Evento | Para | Texto |
|---|---|---|
| `AVALIACAO_RECEBIDA` | avaliado | "Você recebeu uma nova avaliação" (sem nota nem autor) |
| `AVALIACAO_RESPONDIDA` | cliente autor | "Chef {nome} respondeu sua avaliação" |
| `AVALIACAO_DENUNCIADA` | moderadores | "Nova denúncia de avaliação ({MOTIVO})" |
| `CONTA_BLOQUEADA` | moderadores | "Conta bloqueada automaticamente ({CATEGORIA}) — revisar" |

## API

| Rota | Quem | O que faz |
|---|---|---|
| `POST /agendamentos/{id}/avaliacoes` | cliente dono ou chef do agendamento | `{ chefeId (só cliente), nota, tags[], comentario?, anonimo? }` → 201 `{ id }` |
| `GET /agendamentos/{id}/avaliacoes/pendentes` | cliente dono ou chef | `[{ avaliado: { id, nome, tipo }, prazo }]` — quem ainda pode ser avaliado |
| `GET /chefe/{id}/avaliacoes?page=&size=` | qualquer logado | página (até 50): `{ nota, tags[{codigo, rotulo}], comentario, autor, quando ("outubro de 2026"), resposta }`, publicadas, mais recentes primeiro |
| `GET /chefe/{id}/reputacao` | qualquer logado | `{ notaMedia, totalAvaliacoes, tags: [{codigo, rotulo, total}] }` |
| `PUT /avaliacoes/{id}/resposta` | chef avaliado | `{ texto }` → 200 |
| `POST /avaliacoes/{id}/denuncias` | chef avaliado | `{ motivo, descricao? }` → 201 |
| `GET /clientes/{id}/reputacao` | chef com algum agendamento com o cliente | como a do chef; sem comentários |
| `GET /moderacao/denuncias-avaliacoes`, `PATCH .../{id}/arquivar`·`remover-comentario` | moderação | fila de denúncias (com o texto original) |
| `GET /moderacao/comentarios-de-clientes?page=` | moderação | comentários privados sobre clientes |
| `GET /moderacao/bloqueios?status=`, `PATCH /moderacao/bloqueios/{id}/confirmar`·`reverter` | moderação | fila de bloqueios automáticos |

- Respostas de chef (busca, destaque, perfil `/chefe/me`) ganham `notaMedia` e `totalAvaliacoes`. A busca aceita `sort=notaMedia` (desc; "Novo" por último).
- `GET /agendamentos/recebidos` ganha `clienteNotaMedia` e `clienteTotalAvaliacoes`.

## Dados (V7)

- `agendamentos.concluido_em` (gravado por `concluirPeloChef` e `concluirPeloSistema`).
- `avaliacao`: `id`, `agendamento_id` (FK cascade), `autor_id`, `autor_tipo`, `avaliado_id`, `avaliado_tipo`, `nota` (CHECK 1–5), `comentario`, `comentario_original`, `anonimo` (default true), `publicado_em`, `comentario_removido` (default false), `resposta`, `resposta_original`, `respondida_em`, `criada_em`; unique (`agendamento_id`, `autor_id`, `avaliado_id`); índices (`avaliado_id`, `publicado_em`).
- `avaliacao_tag`: `avaliacao_id` (FK cascade), `tag` (CHECK com as duas listas).
- `denuncia_avaliacao`: como `denuncia_mensagem`, FK cascade em `avaliacao`.
- `bloqueio_conta`: `id`, `conta_id`, `conta_tipo`, `categoria` (`ODIO`, `AMEACA`), `termo`, `texto`, `status` (`ABERTO`, `CONFIRMADO`, `REVERTIDO`), `criado_em`, `resolvido_em`, `resolvido_por`.
- `chefe` e `usuarios`: `soma_notas` e `total_avaliacoes` (default 0), `bloqueado_em`, `bloqueio_motivo`.
- CHECKs de `acao_moderacao.acao` e `notificacao.evento` com os valores novos.
- LGPD: a exclusão de conta do cliente apaga as avaliações que ele escreveu e as que recebeu (cascata dos agendamentos dele) e **desconta** as notas dos contadores dos chefs antes; bloqueios ligados a ele também saem.

## Rotina

A `AvaliacaoRotina` (mesmo `chefhome.rotina.cron`, desligada nos testes) publica comentários com nome cujo prazo de 7 dias acabou.

## Erros

| Caso | Status |
|---|---|
| Não participa do agendamento / avaliação de outro | 404 |
| Fora do status, do prazo, repetida, resposta sem comentário publicado | 409 |
| Nota, tags, tamanho, motivo inválidos | 400 |
| Conteúdo de ódio ou ameaça | 403 (e conta bloqueada) |
| Conta bloqueada tentando agir | 401 (sessão não autenticada) |

## Testes

- **Unidade (Clock fixo):** prazo de 7 dias (exato e 1 minuto depois), sem `concluido_em`, validações de nota e tags; contadores e média nula abaixo de 5; filtro (inteira, sem maiúscula/acento, sem pegar pedaço de palavra, as três categorias).
- **Integração (H2):** quem avalia e 404/409; cliente não avalia chef que saiu; anonimato (sem id de autor; "Cliente verificado"); comentário com nome oculto até o chef avaliar e até a rotina; comentário sobre cliente nunca público; reputação do cliente 404 para chef sem agendamento; resposta (uma, editável, outro chef 404); denúncia e remoção; busca ordenada por nota; recebidos com a média do cliente; notificações; ódio/ameaça → 403, nada gravado, conta bloqueada, sessão derrubada, login recusado, moderação confirma e reverte; LGPD (contadores do chef voltam ao valor sem as notas do cliente apagado).
- **Postgres real:** V7 aplica e o `validate` passa; CHECKs batem com os enums; cascata; avaliações simultâneas ao mesmo chef não perdem contagem.

## Pontos de atenção da revisão

1. Anonimato vazando por outro caminho (id do autor, ordem da lista, texto de notificação).
2. Avaliar e apagar a conta ao mesmo tempo: contadores certos.
3. Comentário com nome e chef avaliando no último minuto dos 7 dias: publica uma vez só.
4. Falso positivo do filtro de ódio/ameaça: a reversão devolve a conta inteira (login e ações).
5. O `SecurityFilter` passa a consultar o banco por requisição: só por id, sem N+1, e moderadores continuam sem consulta.

## Nova conta depois do banimento

- Já coberto: o login é só pelo Google e a conta banida fica marcada pelo e-mail; ela não loga (logo também não consegue se apagar para recadastrar). Voltar exige outra conta Google, o que pede um telefone novo.
- Não usar IP: CGNAT das operadoras bloquearia inocentes, o IP muda/contorna-se fácil (4G, VPN) e é dado pessoal (LGPD) sem ganho proporcional.
- Próxima etapa (fora deste spec): telefone verificado por SMS no cadastro, um telefone por conta, banido junto com a conta.

## Fora do escopo

Filtro no chat e verificação de telefone (etapa seguinte); edição da avaliação; média ponderada por recência; avaliação de agendamentos concluídos antes desta versão.
