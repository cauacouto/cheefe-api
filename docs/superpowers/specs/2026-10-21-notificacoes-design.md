# Notificações dentro do app — Design

**Data:** 2026-10-21
**Status:** aprovado em conversa, aguardando revisão do spec
**Relacionados:** moderação (`2026-10-21-moderacao-chefs-design.md`) e chat (a especificar) acrescentam eventos depois.

## Objetivo

Avisar cliente, chef e moderador, **dentro do app**, sobre o que acontece com eles: o front mostra um "sininho" com a contagem de não lidas e a lista de notificações. Sem e-mail, sem serviço externo.

## Decisões

| Decisão | Escolha |
|---|---|
| Canal | Só in-app (tabela + rotas). |
| Tempo real | Fora de escopo: o front consulta a contagem periodicamente (ex.: 30 s). Se o chat trouxer WebSocket/SSE, as notificações podem reaproveitar. |
| Geração | Eventos de domínio do Spring publicados pelos serviços; `NotificacaoListener` com `@TransactionalEventListener(phase = BEFORE_COMMIT)` grava **na mesma transação** (atômico com a ação). |
| Texto | Mensagem pronta em português gravada na notificação (o front só exibe). |
| Retenção | Sem limpeza automática por enquanto. |

## Eventos

| # | `EventoNotificacao` | Quando | Destinatários | Mensagem | `referenciaId` | Fase |
|---|---|---|---|---|---|---|
| 1 | `AGENDAMENTO_CRIADO` | `POST /agendamentos` (201) | cada chef do agendamento | `"{nomeCliente} pediu um agendamento para {dd/MM} às {HH:mm}"` | id do agendamento | **agora** |
| 2 | `AGENDAMENTO_EDITADO` | `PUT /agendamentos/{id}` (200) | cada chef do agendamento (após a edição) | `"{nomeCliente} alterou o agendamento de {dd/MM}"` | id do agendamento | **agora** |
| 3 | `AGENDAMENTO_CONFIRMADO` | `PATCH /agendamentos/{id}/confirmar` (200) | o cliente | `"Chef {nomeChef} confirmou seu agendamento de {dd/MM}"` | id do agendamento | **agora** |
| 4 | `DENUNCIA_CRIADA` | `POST /denuncias` | cada moderador | `"Nova denúncia contra o chef {nomeChef} ({motivo})"` | id da denúncia | moderação |
| 5 | `CHEF_OCULTO` | denúncia que leva o chef a 3 denunciantes distintos | o chef e cada moderador | chef: `"Seu perfil foi ocultado da busca enquanto denúncias são analisadas"`; moderador: `"Chef {nomeChef} foi ocultado automaticamente (3 denúncias)"` | id do chef | moderação |
| 6 | `CHEF_SUSPENSO` / `CHEF_REATIVADO` | ações do moderador | o chef | `"Seu perfil foi suspenso"` / `"Seu perfil foi reativado"` | id do chef | moderação |
| 7 | `MENSAGEM_RECEBIDA` | nova mensagem no chat | o outro participante | a definir no spec do chat | id da conversa | chat |

`{dd/MM}` e `{HH:mm}` usam `dataHoraInicial` do agendamento. O enum já nasce com todos os valores; as fases 2 e 3 só publicam os eventos.

## Modelo de dados

### Tabela `notificacao` (entidade `Notificacao`)

| Campo | Tipo | Regra |
|---|---|---|
| `id` | Long (identity) | |
| `destinatarioId` | UUID | obrigatório; índice `(destinatario_id, lida, criada_em)` |
| `destinatarioTipo` | `TipoUsuario` (`STRING`) | obrigatório |
| `evento` | `EventoNotificacao` (`STRING`) | obrigatório |
| `mensagem` | String ≤ 300 | obrigatória |
| `referenciaId` | String ≤ 64 | opcional (Long ou UUID como texto) |
| `lida` | boolean | default `false` |
| `criadaEm` | `LocalDateTime` | `@PrePersist` |

Moderadores (fase 2) usam o mesmo id do login: `UUID.nameUUIDFromBytes(email normalizado)`.

## Rotas (qualquer autenticado; sempre só as próprias)

| Rota | Resposta |
|---|---|
| `GET /notificacoes?somenteNaoLidas=false&page=0&size=20` | 200, página `{ content: [{id, evento, mensagem, referenciaId, lida, criadaEm}], ... }`, mais novas primeiro |
| `GET /notificacoes/nao-lidas/contagem` | 200 `{ "total": n }` |
| `PATCH /notificacoes/{id}/lida` | 204; de outra pessoa ou inexistente → 404; já lida → 204 (idempotente) |
| `PATCH /notificacoes/lidas` | 204; marca todas as do usuário |

Sem cookie → 401 (regra existente).

## Componentes

- `enums/EventoNotificacao`; `domin/Notificacao`; `repositorys/NotificacaoRepository` (`Page<Notificacao> findByDestinatarioIdOrderByCriadaEmDesc`, idem com `lida=false`, `long countByDestinatarioIdAndLidaFalse`, `Optional<Notificacao> findByIdAndDestinatarioId`, update em lote para marcar todas).
- Eventos (records em `Services/eventos/`): `AgendamentoCriadoEvent(Long agendamentoId)`, `AgendamentoEditadoEvent(Long agendamentoId)`, `AgendamentoConfirmadoEvent(Long agendamentoId)`.
- `agendamentoService` injeta `ApplicationEventPublisher` e publica após salvar em `criarAgendamento`, `atualizarAgedamento` e `confirmarAgendamento`.
- `Services/NotificacaoListener` (`@TransactionalEventListener(phase = BEFORE_COMMIT)`) carrega o agendamento e grava uma notificação por destinatário.
- `Services/NotificacaoService`: `listar(UUID, boolean somenteNaoLidas, Pageable)`, `contarNaoLidas(UUID)`, `marcarLida(Long id, UUID)`, `marcarTodasLidas(UUID)`.
- `Controller/NotificacaoController` (`/notificacoes`), `Dtos/NotificacaoDto`, `Dtos/ContagemDto(long total)`.

## Erros

| Situação | Status |
|---|---|
| Sem cookie | 401 |
| Marcar notificação de outra pessoa / inexistente | 404 |

## Testes (TDD)

- `NotificacaoListenerTest` (unitário): agendamento com 2 chefs → 2 notificações `AGENDAMENTO_CRIADO` com a mensagem exata; confirmado → 1 para o cliente.
- Integração (H2 + cadeia real, limpando o banco no `@AfterEach`):
  - `POST /agendamentos` → o chef vê 1 não lida em `/notificacoes/nao-lidas/contagem` e a mensagem em `/notificacoes`;
  - `PUT /agendamentos/{id}` → notificação `AGENDAMENTO_EDITADO` para o chef;
  - `PATCH /confirmar` → o cliente recebe `AGENDAMENTO_CONFIRMADO`;
  - **atomicidade**: agendamento recusado (ex.: chef incompleto → 400) não gera notificação;
  - cliente A não vê nem marca notificação de B (404); `PATCH /notificacoes/lidas` zera a contagem.

## Impacto no front

Sininho com `GET /notificacoes/nao-lidas/contagem` em polling; ao abrir, `GET /notificacoes`; clique abre o item por `evento` + `referenciaId` e chama `PATCH /notificacoes/{id}/lida`; "marcar todas" → `PATCH /notificacoes/lidas`.

## Fora de escopo

E-mail/push; tempo real; preferências de notificação por usuário; limpeza/retenção; eventos de cancelar/recusar/concluir (ações inexistentes).
