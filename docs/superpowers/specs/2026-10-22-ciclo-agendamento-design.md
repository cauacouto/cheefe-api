# Ciclo de vida do agendamento — Design

**Data:** 2026-10-22
**Status:** v1 — aprovado em conversa (partes 1, 2 e 3), aguardando revisão do spec
**Branch:** `feat/ciclo-agendamento`

## Objetivo

Fechar o ciclo do agendamento para o fluxo principal funcionar de ponta a ponta: o cliente cancela, o chef recusa ou desiste, o que aconteceu vira concluído, o que ninguém confirmou expira, e cada lado é avisado. Destrava as avaliações (fora deste spec).

Hoje o agendamento nasce `PENDENTE` e só pode virar `CONFIRMADO`. `CANCELADO` e `CONCLUIDO` existem no enum sem nenhuma rota que leve a eles.

**Princípio:** o app é intermediador. Registra o que aconteceu e avisa as partes. Não aplica multa nem reembolso e não responde pelo comparecimento; para isso existe a denúncia.

## Decisões

| Decisão | Escolha |
|---|---|
| Recusa do chef | Status próprio `RECUSADO` |
| Recusa num agendamento com vários chefs | Só aquele chef sai; o valor é recalculado; vira `RECUSADO` quando não sobra nenhum chef |
| Prazo de cancelamento do cliente | `PENDENTE` a qualquer momento antes do início; `CONFIRMADO` até 24h antes do início |
| Chef desistir de um confirmado | Pode, antes do início; mesma regra da recusa; sem chefs → `CANCELADO` |
| Conclusão | Automática depois do fim, e o chef pode marcar antes (só depois do início) |
| Pendente que chega no horário | Vira `EXPIRADO` (status próprio) |
| Motivo | Opcional, até 300 caracteres, em cancelar, recusar e desistir |
| Modelagem | Transições como métodos da entidade + tabela de histórico só de inserção |

## Estados e transições

`StatusAgendamento` passa a ser `PENDENTE, CONFIRMADO, CANCELADO, CONCLUIDO, RECUSADO, EXPIRADO` (gravado pelo nome desde a V5; a ordem não importa).

| De | Ação | Quem | Regra | Vai para |
|---|---|---|---|---|
| `PENDENTE` | confirmar (já existe) | chef do agendamento | antes do início; sem conflito de horário | `CONFIRMADO` |
| `PENDENTE` | cancelar | cliente dono | antes do início | `CANCELADO` |
| `CONFIRMADO` | cancelar | cliente dono | até 24h antes do início (exatamente 24h antes ainda pode) | `CANCELADO` |
| `PENDENTE` | recusar | chef do agendamento | antes do início | chef sai, valor recalculado; sem chefs → `RECUSADO` |
| `CONFIRMADO` | desistir | chef do agendamento | antes do início | chef sai, valor recalculado; sem chefs → `CANCELADO` |
| `CONFIRMADO` | concluir | chef do agendamento | depois do início | `CONCLUIDO` |
| `CONFIRMADO` | concluir | sistema (rotina) | depois do fim | `CONCLUIDO` |
| `PENDENTE` | expirar | sistema (rotina) | depois do início | `EXPIRADO` |

- `CANCELADO`, `RECUSADO`, `CONCLUIDO` e `EXPIRADO` são finais. `PUT /agendamentos/{id}` continua só para `PENDENTE`.
- **O horário vale mesmo antes da rotina rodar:** um `PENDENTE` cujo início já passou não pode ser confirmado, cancelado, recusado nem editado (409). Muda o comportamento atual, que deixa confirmar depois do horário.
- "Antes do início" = `agora < dataHoraInicial`; "depois do início" = `agora >= dataHoraInicial`; "depois do fim" = `agora >= dataHoraFinal`. `agora` vem do `Clock` injetável.
- O prazo de 24h: `agora <= dataHoraInicial - 24h`.
- Chef apagado ou banido não age (`ChefeModel.exigirAtivo`, como hoje).
- Quem não é o cliente dono, ou não é chef do agendamento, recebe **404** (igual a inexistente). Ação fora do status ou do prazo → **409**, com a mensagem dizendo o porquê.
- Ao sair de `CONFIRMADO`, o horário do chef fica livre: a regra de conflito (lote 11) só considera `CONFIRMADO`.
- A denúncia (`POST /denuncias`) passa a aceitar agendamento `CONFIRMADO` ou `CONCLUIDO` cujo início já passou.

### Na entidade

`Agendamento` ganha métodos que validam a transição e alteram o estado, sem acesso a banco:

- `cancelarPeloCliente(LocalDateTime agora)`
- `recusar(ChefeModel chefe, LocalDateTime agora)` → resultado indica se o agendamento continua (com chefs restantes) ou virou `RECUSADO`
- `desistir(ChefeModel chefe, LocalDateTime agora)` → idem, ou `CANCELADO`
- `concluirPeloChef(LocalDateTime agora)`, `concluirPeloSistema(LocalDateTime agora)`
- `expirar(LocalDateTime agora)`
- `confirmar(LocalDateTime agora)` (a checagem de conflito continua no serviço)

O recálculo do valor quando um chef sai usa a mesma regra de hoje (`calcularValor`), que passa para um lugar acessível ao serviço e à entidade sem duplicar.

## API

Todas as rotas novas respondem **200 sem corpo** (como o `confirmar` atual). Corpo opcional `{ "motivo": "..." }` (até 300 caracteres; em branco vira nulo) em cancelar, recusar e desistir.

| Rota | Papel |
|---|---|
| `PATCH /agendamentos/{id}/cancelar` | CLIENTE dono |
| `PATCH /agendamentos/{id}/recusar` | CHEFE do agendamento |
| `PATCH /agendamentos/{id}/desistir` | CHEFE do agendamento |
| `PATCH /agendamentos/{id}/concluir` | CHEFE do agendamento (sem corpo) |
| `GET /agendamentos/{id}/historico` | cliente dono ou chef atual do agendamento |

`GET /agendamentos/{id}/historico` → `[{ "acao", "autor": { "id", "nome", "tipo" } | null, "motivo", "ocorridoEm" }]`, do mais antigo ao mais novo. `autor` nulo = sistema. Chef que já saiu do agendamento recebe 404.

Rotas no `SecurityConfiguration`: `PATCH /agendamentos/*/cancelar` → CLIENTE; `PATCH /agendamentos/*/recusar|desistir|concluir` → CHEFE; `GET /agendamentos/*/historico` → CLIENTE ou CHEFE.

## Histórico (V6)

Tabela `agendamento_historico`:

| Coluna | Tipo |
|---|---|
| `id` | bigint identity |
| `agendamento_id` | bigint NOT NULL, FK `agendamentos` **ON DELETE CASCADE** |
| `acao` | varchar CHECK (`CRIADO`, `EDITADO`, `CONFIRMADO`, `CANCELADO`, `RECUSADO`, `DESISTENCIA`, `CONCLUIDO`, `EXPIRADO`) |
| `autor_id` | uuid, nulo = sistema |
| `autor_tipo` | varchar CHECK (`CLIENTE`, `CHEFE`), nulo = sistema |
| `motivo` | varchar(300) |
| `ocorrido_em` | timestamp NOT NULL |

Índice `(agendamento_id, ocorrido_em)`.

- **Só de inserção para a API:** a V6 revoga `UPDATE`, `DELETE` e `TRUNCATE` do `chefhome_app` (mesmo padrão da `acao_moderacao`, V2).
- **LGPD:** a exclusão de conta já apaga os agendamentos do cliente; o `ON DELETE CASCADE` leva o histórico junto, executado pelo banco, sem a API precisar de `DELETE` na tabela. A entidade usa `@OnDelete(action = CASCADE)` para o schema do H2 dos testes ter o mesmo comportamento.
- Só o id do autor é guardado; o nome é resolvido na leitura (sem duplicar dado pessoal).
- Agendamentos anteriores à V6 ficam sem histórico.
- `CRIADO`, `EDITADO` e `CONFIRMADO` também são registrados, para a linha do tempo ficar completa a partir de agora.
- A V6 também atualiza os CHECKs de `agendamentos.status` (+ `RECUSADO`, `EXPIRADO`) e de `notificacao.evento` (+ os eventos abaixo).

## Notificações

Eventos novos em `EventoNotificacao`. Mesma mecânica de hoje: eventos de domínio publicados pelo serviço e tratados pelo `NotificacaoListener` (`BEFORE_COMMIT`); a contagem segue pelo SSE depois do commit. `referenciaId` = id do agendamento.

| Evento | Para quem | Texto |
|---|---|---|
| `AGENDAMENTO_CANCELADO` | cada chef do agendamento | "{cliente} cancelou o agendamento de {dd/MM}" + ": {motivo}" |
| `AGENDAMENTO_RECUSADO` | cliente | com chefs restantes: "Chef {nome} recusou seu pedido de {dd/MM}. Novo valor: R$ {valor}" + motivo; sem chefs: "Seu pedido de {dd/MM} foi recusado" + motivo |
| `CHEF_DESISTIU` | cliente | com chefs restantes: "Chef {nome} desistiu do agendamento de {dd/MM}. Novo valor: R$ {valor}" + motivo; sem chefs: "O agendamento de {dd/MM} foi cancelado: o chef {nome} desistiu" + motivo |
| `AGENDAMENTO_EXPIRADO` | cliente | "Seu pedido de {dd/MM} expirou sem confirmação" |

- O motivo entra encurtado (até 120 caracteres, com "…") para caber nos 300 da notificação; o texto completo fica no histórico.
- Conclusão não notifica por enquanto (vai junto com as avaliações).

## Rotina

`AgendamentoRotina` com `@Scheduled(fixedDelay = 15 min)` (o `@EnableScheduling` já existe):

1. busca ids de `CONFIRMADO` com `dataHoraFinal <= agora` e conclui cada um (`concluirPeloSistema`, histórico com autor nulo, sem notificação);
2. busca ids de `PENDENTE` com `dataHoraInicial <= agora` e expira cada um (histórico + `AGENDAMENTO_EXPIRADO`).

- Cada agendamento numa transação própria, relido com trava (`PESSIMISTIC_WRITE`) e com o status conferido de novo: uma falha não para os demais, e duas execuções (ou a rotina e um chef concluindo ao mesmo tempo) não duplicam histórico nem notificação.
- Agendamentos antigos sem `dataHoraInicial`/`dataHoraFinal` são ignorados.
- O `Clock` injetável passa a ser usado também em `Agendamento.criadoEm`.

## Erros

| Caso | Status |
|---|---|
| Agendamento de outro cliente / chef que não está nele | 404 |
| Papel errado | 403 |
| Status não permite a ação / fora do prazo | 409 (`AgendamentoTransicaoInvalidaException`, mensagem com o motivo) |
| Motivo com mais de 300 caracteres | 400 |
| Chef apagado ou banido | 403 (`ContaInativaException`) |

## Testes

- **Unidade (entidade, `Clock` fixo):** cada linha da tabela de transições, com o caso válido e o 409: cancelar confirmado com 24h e 23h59 de antecedência; concluir antes do início; recusar pendente depois do início; qualquer ação a partir de estado final; recusa e desistência parciais (chef sai, valor recalculado, status mantido) e com o último chef (`RECUSADO` / `CANCELADO`).
- **Integração pela API (H2):** 404 para quem não é dono/chef; 403 para papel errado e chef banido; histórico com autor, motivo e horário; `GET /historico` e chef que saiu → 404; notificações com os textos acima e contagem pelo SSE; horário liberado depois do cancelamento aceita outro confirmado; denúncia de agendamento `CONCLUIDO`; rotina com relógio adiantado conclui e expira, e rodar duas vezes não duplica nada.
- **Postgres real (Testcontainers):** V6 aplica e o `validate` passa; `chefhome_app` sem `UPDATE`/`DELETE` no histórico; apagar a conta do cliente leva o histórico pela cascata; CHECKs batem com os enums Java.

## Pontos de atenção da revisão

1. Corrida entre a rotina e uma ação manual (ex.: chef concluindo no mesmo instante): um resultado só, sem 500.
2. Prazo de 24h e horários usam o relógio da aplicação: o fuso de produção precisa ser o mesmo dos horários que o cliente informa.
3. Recusa/desistência parcial: valor recalculado e a notificação com o valor novo.

## Fora do escopo

Avaliações; notificação de conclusão; chef desmarcar dias na agenda; multa ou reembolso; o cliente ver os motivos nas listas (fica no histórico).
