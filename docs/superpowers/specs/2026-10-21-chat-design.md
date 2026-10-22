# Chat entre cliente e chef — Design

**Data:** 2026-10-21
**Status:** v2 — aprovado; acrescenta editar, apagar, denunciar mensagem e "visto". Implementado **depois da moderação** (a denúncia de mensagem usa o admin e a fila da moderação).
**Relacionados:** notificações (`2026-10-21-notificacoes-design.md`, evento 7 `MENSAGEM_RECEBIDA`); moderação (`2026-10-21-moderacao-chefs-design.md`: papel ADMIN, fila e ações sobre chefs)

## Objetivo

Permitir que o cliente converse com um chef — inclusive **antes** de contratar, para tirar dúvidas — com as mensagens chegando **em tempo real**. O mesmo canal em tempo real passa a entregar as notificações (fim do polling do sininho).

## Decisões

| Decisão | Escolha |
|---|---|
| Quem inicia | Só o **cliente**, a qualquer momento (sem precisar de agendamento). O chef só responde. |
| Granularidade | **Uma conversa por par** cliente–chef (`UNIQUE(cliente_id, chefe_id)`). |
| Conteúdo | Só texto, até 2000 caracteres. Sem anexos nem "digitando". |
| Editar | Só o autor, até **15 minutos** após o envio; mostra "editada". |
| Apagar | Só o autor, a qualquer momento; ambos veem "mensagem apagada"; o texto original fica guardado internamente (prova para denúncias). |
| Denunciar | Só o **destinatário**, uma vez por mensagem; motivo obrigatório; vai para a fila do admin com **cópia do texto** no momento da denúncia. |
| Visto | O remetente vê se a mensagem foi lida (`lidaEm`); evento SSE `mensagens_lidas`. |
| Tempo real | **SSE** (`GET /eventos`) para receber; REST para enviar. Push **depois do commit**. |
| Escala | Conexões SSE em memória (uma instância). Mais de uma instância exigirá pub/sub (ex.: Redis) — fora de escopo. |
| Notificação de mensagem | Cria `MENSAGEM_RECEBIDA` só se o destinatário **não tem** notificação não lida dessa conversa (sem spam). |
| Conta apagada | Cliente apagado → conversas e mensagens dele são apagadas junto. Chef apagado/banido → conversas ficam como histórico, sem novas mensagens. |

## Modelo de dados

### `conversa` (entidade `Conversa`)

| Campo | Tipo | Regra |
|---|---|---|
| `id` | Long (identity) | |
| `cliente` | `@ManyToOne UserModel` | obrigatório |
| `chefe` | `@ManyToOne ChefeModel` | obrigatório |
| `criadaEm` | `LocalDateTime` | `@PrePersist` |
| `ultimaMensagemEm` | `LocalDateTime` | atualizado a cada mensagem; inicial = `criadaEm` |

`@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"cliente_id", "chefe_id"}))`.

### `mensagem` (entidade `Mensagem`)

| Campo | Tipo | Regra |
|---|---|---|
| `id` | Long (identity) | |
| `conversa` | `@ManyToOne Conversa` | obrigatório; índice `(conversa_id, id)` |
| `remetenteId` | UUID | obrigatório |
| `remetenteTipo` | `TipoUsuario` (`STRING`) | `CLIENTE` ou `CHEFE` |
| `texto` | String ≤ 2000 | obrigatório, não em branco (normalizado com `strip()`) |
| `enviadaEm` | `LocalDateTime` | `@PrePersist` |
| `lidaEm` | `LocalDateTime` | nula até o destinatário marcar como lida |
| `editadaEm` | `LocalDateTime` | nula se nunca editada |
| `apagadaEm` | `LocalDateTime` | nula se não apagada pelo autor |
| `removidaPorModeracao` | boolean | default `false`; admin removeu |

Mensagem apagada ou removida: o DTO devolve `texto = null` e `apagada = true` (o texto continua na tabela).

### `denuncia_mensagem` (entidade `DenunciaMensagem`)

| Campo | Tipo | Regra |
|---|---|---|
| `id` | Long (identity) | |
| `mensagem` | `@ManyToOne Mensagem` | obrigatório; `UNIQUE(mensagem_id, denunciante_id)` |
| `denuncianteId` / `denuncianteTipo` | UUID / `TipoUsuario` | o destinatário da mensagem |
| `autorId` / `autorTipo` | UUID / `TipoUsuario` | autor da mensagem (para o admin agir) |
| `textoDenunciado` | String ≤ 2000 | cópia do texto no momento da denúncia |
| `motivo` | `MotivoDenunciaMensagem` (`OFENSIVA`, `ASSEDIO`, `SPAM`, `GOLPE`, `CONTEUDO_IMPROPRIO`, `OUTRO`), `STRING` | obrigatório |
| `descricao` | String ≤ 1000 | opcional |
| `status` | `StatusDenuncia` (`ABERTA`, `ARQUIVADA`, `PROCEDENTE`) | default `ABERTA` |
| `criadaEm`, `resolvidaEm`, `resolvidaPor` | | como na moderação de chefs |

## Rotas

| Rota | Papel | Regra | Respostas |
|---|---|---|---|
| `POST /conversas` `{ "chefeId": "uuid" }` | CLIENTE | cria ou devolve a existente para (cliente logado, chef). Chef precisa estar `ativo` | 201 (nova) / 200 (existente) com `ConversaDto`; chef inexistente/apagado → 404 |
| `GET /conversas` | CLIENTE, CHEFE | as conversas em que sou participante, `ultimaMensagemEm` desc | 200 `[ConversaDto]` |
| `GET /conversas/{id}/mensagens?antesDe={mensagemId}&size=30` | participantes | página para trás (mais novas primeiro), `size` máx. 100 | 200 `[MensagemDto]`; não participante/inexistente → 404 |
| `POST /conversas/{id}/mensagens` `{ "texto": "..." }` | participantes | grava, atualiza `ultimaMensagemEm`, notifica e faz push | 201 `MensagemDto`; texto vazio/>2000 → 400; não participante → 404; chef apagado (remetente ou destinatário) → 403 (`ContaInativaException`) |
| `PATCH /conversas/{id}/lidas` | participantes | marca `lidaEm` nas mensagens **recebidas** ainda não lidas; SSE `mensagens_lidas` para o remetente | 204; não participante → 404 |
| `PATCH /conversas/{id}/mensagens/{mensagemId}` `{ "texto": "..." }` | autor | até 15 min após o envio; grava `editadaEm`; SSE `mensagem_editada` | 200 `MensagemDto`; não é o autor/não participa → 404; fora do prazo ou apagada → 409; texto inválido → 400 |
| `DELETE /conversas/{id}/mensagens/{mensagemId}` | autor | grava `apagadaEm`; SSE `mensagem_apagada` | 204; não é o autor/não participa → 404; já apagada → 204 |
| `POST /conversas/{id}/mensagens/{mensagemId}/denuncias` `{ "motivo": "OFENSIVA", "descricao": "..." }` | destinatário | cria `DenunciaMensagem` `ABERTA` com cópia do texto; notifica admins | 201 `{id, status}`; autor denunciando a própria / não participante → 404; repetida → 409; mensagem apagada → 409 |
| `GET /admin/denuncias-mensagens?status=ABERTA` | ADMIN | fila paginada (mais antigas primeiro): id, motivo, descrição, textoDenunciado, autor (id, nome, tipo), denunciante (id, nome), conversaId, criadaEm | 200 |
| `PATCH /admin/denuncias-mensagens/{id}/arquivar` | ADMIN | `ARQUIVADA` | 204; já resolvida → 409 |
| `PATCH /admin/denuncias-mensagens/{id}/remover-mensagem` | ADMIN | mensagem `removidaPorModeracao=true`; denúncias abertas dessa mensagem → `PROCEDENTE`; SSE `mensagem_apagada` aos participantes | 204 |
| `GET /eventos` | qualquer autenticado | abre o stream SSE do usuário | 200 `text/event-stream` |

`ConversaDto`: `{ id, outroParticipante: { id, nome, imageUrl, tipo }, ultimaMensagem: { texto (prévia ≤ 100), enviadaEm, minha: boolean } | null, naoLidas: long, ultimaMensagemEm }`.
`MensagemDto`: `{ id, conversaId, texto, enviadaEm, minha: boolean, lida: boolean, lidaEm, editada: boolean, apagada: boolean }` (`minha` relativo a quem pede; `texto=null` quando `apagada`).

Se o autor for chef, o admin usa as ações já existentes da moderação (`/admin/chefes/{id}/suspender|banir`). Sanções a clientes ficam fora de escopo.

Papéis no `SecurityConfiguration`: `POST /conversas` → `hasRole(CLIENTE)`; `/conversas/**` restante e `/eventos` → `authenticated()`.

## Tempo real (SSE)

- **`SseRegistry`** (componente): `Map<UUID, List<SseEmitter>>` thread-safe; `abrir(UUID usuarioId)` cria `SseEmitter` (timeout 30 min), remove-se em `onCompletion`/`onTimeout`/`onError`; `enviar(UUID usuarioId, String evento, Object dados)` entrega a todas as abas, descartando emitters que falharem.
- **Heartbeat**: `@Scheduled(fixedRate = 25_000)` envia comentário SSE (`: ping`) a todos (exige `@EnableScheduling`).
- **`GET /eventos`** (`EventosController`): `produces = text/event-stream`, devolve `sseRegistry.abrir(usuario.id())`.
- **Async dispatch**: o SSE usa dispatch assíncrono do servlet; o `SecurityConfiguration` precisa liberar `DispatcherType.ASYNC` (`dispatcherTypeMatchers(ASYNC).permitAll()`) para não responder 403 no meio do stream.
- **Eventos SSE**:
  - `mensagem` → `MensagemDto` do ponto de vista do destinatário (`minha=false`) — enviado ao **destinatário** e às **outras abas do remetente** (`minha=true`).
  - `notificacao` → `{ "total": n }` (contagem de não lidas) a cada notificação criada.
  - `mensagem_editada` → `MensagemDto` atualizado; `mensagem_apagada` → `{ conversaId, mensagemId }`; `mensagens_lidas` → `{ conversaId, ateId, lidaEm }` (para o "visto" do remetente).
- **Push só depois do commit**: `MensagemEnviadaEvent` e `NotificacaoCriadaEvent` tratados por listeners `@TransactionalEventListener(phase = AFTER_COMMIT)`; falha no push nunca desfaz a mensagem.

## Notificação `MENSAGEM_RECEBIDA` (evento 7)

- Mensagem: `"Nova mensagem de {nomeRemetente}"`, `referenciaId` = id da conversa.
- Criada no envio (mesma transação, como as de agendamento) **somente se** não existir notificação `MENSAGEM_RECEBIDA` **não lida** para o destinatário com o mesmo `referenciaId`.
- `PATCH /conversas/{id}/lidas` também marca como lidas as notificações `MENSAGEM_RECEBIDA` daquela conversa do usuário.

## Notificação de denúncia de mensagem

- Novo `EventoNotificacao.MENSAGEM_DENUNCIADA` → cada admin: `"Nova denúncia de mensagem ({motivo})"`, `referenciaId` = id da denúncia.

## Exclusão de conta

- `UsuarioService.deletar`: antes de apagar o usuário, apaga denúncias de mensagens das conversas dele, mensagens e conversas do cliente (`DenunciaMensagemRepository.deleteByMensagem_Conversa_Cliente_Id`, `MensagemRepository.deleteByConversa_Cliente_Id`, `ConversaRepository.deleteByCliente_Id`).
- Chef apagado/banido: conversas permanecem; `POST /conversas/{id}/mensagens` → 403 para ambos os lados; `GET` continua funcionando (histórico).

## Componentes

- `domin/Conversa`, `domin/Mensagem`; `repositorys/ConversaRepository`, `repositorys/MensagemRepository`.
- `Services/ChatService`: `abrirConversa(UUID clienteId, UUID chefeId)` → `(ConversaDto, boolean criada)`, `listarConversas(UsuarioAutenticado)`, `listarMensagens(Long conversaId, UsuarioAutenticado, Long antesDe, int size)`, `enviar(Long conversaId, UsuarioAutenticado, String texto)`, `marcarLidas(Long conversaId, UsuarioAutenticado)`.
- `Services/eventos/MensagemEnviadaEvent(Long mensagemId)`, `Services/eventos/NotificacaoCriadaEvent(UUID destinatarioId)`.
- `Services/SseRegistry`, `Services/TempoRealListener` (AFTER_COMMIT: mensagens e contagem de notificações).
- `NotificacaoService.notificar` publica `NotificacaoCriadaEvent`; novo `NotificacaoService.marcarLidasDaReferencia(UUID, EventoNotificacao, String)`; consulta de existência de não lida por (destinatário, evento, referência).
- `Controller/ConversaController` (`/conversas`), `Controller/EventosController` (`/eventos`).
- DTOs: `AbrirConversaDto(@NotNull UUID chefeId)`, `EnviarMensagemDto(@NotBlank @Size(max=2000) String texto)`, `ConversaDto`, `MensagemDto`, `ParticipanteDto`.

## Erros

| Situação | Status |
|---|---|
| Chef tenta `POST /conversas` | 403 |
| Conversa inexistente ou de que não participo | 404 |
| Chef inexistente/apagado ao abrir conversa | 404 |
| Enviar com chef apagado/banido | 403 |
| Texto em branco ou > 2000 | 400 |

## Testes (TDD)

- `ChatServiceTest` (Mockito) — inclui: editar após 15 min → 409; editar/apagar por quem não é o autor → 404; apagar é idempotente; denúncia copia o texto e continua com o texto original mesmo se a mensagem for editada/apagada depois; autor não denuncia a própria; denúncia repetida → 409; `marcarLidas` publica `mensagens_lidas`; abrir duplicada devolve a mesma (`criada=false`); não participante → 404 em listar/enviar/marcar; texto é `strip()`; 10 mensagens seguidas → 1 notificação `MENSAGEM_RECEBIDA`; após `marcarLidas`, nova mensagem gera nova notificação.
- `ConversaIntegrationTest` (H2 + cadeia real, limpeza no `@AfterEach` na ordem das FKs: mensagens → conversas → notificações → … ):
  - cliente abre → 201; de novo → 200 com o mesmo id; chef em `POST /conversas` → 403;
  - troca de mensagens, `GET /conversas` com `naoLidas` e prévia; `PATCH /lidas` zera e marca a notificação da conversa;
  - paginação com `antesDe`;
  - terceiro usuário → 404 em todas as rotas da conversa;
  - chef apagado: enviar → 403, histórico → 200;
  - `DELETE /usuario/me` de cliente com conversas (e denúncias de mensagens) → 204 e tudo removido;
  - editar/apagar/denunciar pela API; admin vê a denúncia com o texto original, remove a mensagem → ambos veem `apagada=true`; cliente/chef em `/admin/denuncias-mensagens` → 403;
  - "visto": após `PATCH /lidas` do destinatário, o remetente vê `lida=true` e `lidaEm`.
- `SseRegistryTest`: duas abas recebem; emitter que falha é descartado; usuário sem conexão não quebra.
- `TempoRealListenerTest`: `MensagemEnviadaEvent` envia `mensagem` ao destinatário (`minha=false`) e ao remetente (`minha=true`); `NotificacaoCriadaEvent` envia `notificacao` com a contagem.
- `EventosIntegrationTest`: `GET /eventos` autenticado inicia async com `text/event-stream`; sem cookie → 401.

## Impacto no front

- Perfil do chef: botão "Conversar" → `POST /conversas` → abre a conversa.
- Ao logar: `new EventSource(API + "/eventos", { withCredentials: true })`; tratar `mensagem` (atualiza a conversa aberta/lista) e `notificacao` (atualiza o sininho). O polling de notificações vira só fallback.
- Tela de conversas: `GET /conversas`; conversa: `GET /conversas/{id}/mensagens` + rolagem com `antesDe`; ao abrir, `PATCH /conversas/{id}/lidas`.

## Fora de escopo

Anexos, "digitando", sanções a clientes, múltiplas instâncias (pub/sub), notificações por e-mail/push.
