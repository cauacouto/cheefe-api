# Chat entre cliente e chef — Design

**Data:** 2026-10-21
**Status:** aprovado em conversa, aguardando revisão do spec
**Relacionados:** notificações (`2026-10-21-notificacoes-design.md`, evento 7 `MENSAGEM_RECEBIDA`)

## Objetivo

Permitir que o cliente converse com um chef — inclusive **antes** de contratar, para tirar dúvidas — com as mensagens chegando **em tempo real**. O mesmo canal em tempo real passa a entregar as notificações (fim do polling do sininho).

## Decisões

| Decisão | Escolha |
|---|---|
| Quem inicia | Só o **cliente**, a qualquer momento (sem precisar de agendamento). O chef só responde. |
| Granularidade | **Uma conversa por par** cliente–chef (`UNIQUE(cliente_id, chefe_id)`). |
| Conteúdo | Só texto, até 2000 caracteres. Sem anexos, edição, exclusão, "digitando" ou "visto". |
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

## Rotas

| Rota | Papel | Regra | Respostas |
|---|---|---|---|
| `POST /conversas` `{ "chefeId": "uuid" }` | CLIENTE | cria ou devolve a existente para (cliente logado, chef). Chef precisa estar `ativo` | 201 (nova) / 200 (existente) com `ConversaDto`; chef inexistente/apagado → 404 |
| `GET /conversas` | CLIENTE, CHEFE | as conversas em que sou participante, `ultimaMensagemEm` desc | 200 `[ConversaDto]` |
| `GET /conversas/{id}/mensagens?antesDe={mensagemId}&size=30` | participantes | página para trás (mais novas primeiro), `size` máx. 100 | 200 `[MensagemDto]`; não participante/inexistente → 404 |
| `POST /conversas/{id}/mensagens` `{ "texto": "..." }` | participantes | grava, atualiza `ultimaMensagemEm`, notifica e faz push | 201 `MensagemDto`; texto vazio/>2000 → 400; não participante → 404; chef apagado (remetente ou destinatário) → 403 (`ContaInativaException`) |
| `PATCH /conversas/{id}/lidas` | participantes | marca `lidaEm` nas mensagens **recebidas** ainda não lidas | 204; não participante → 404 |
| `GET /eventos` | qualquer autenticado | abre o stream SSE do usuário | 200 `text/event-stream` |

`ConversaDto`: `{ id, outroParticipante: { id, nome, imageUrl, tipo }, ultimaMensagem: { texto (prévia ≤ 100), enviadaEm, minha: boolean } | null, naoLidas: long, ultimaMensagemEm }`.
`MensagemDto`: `{ id, conversaId, texto, enviadaEm, minha: boolean, lida: boolean }` (`minha` relativo a quem pede).

Papéis no `SecurityConfiguration`: `POST /conversas` → `hasRole(CLIENTE)`; `/conversas/**` restante e `/eventos` → `authenticated()`.

## Tempo real (SSE)

- **`SseRegistry`** (componente): `Map<UUID, List<SseEmitter>>` thread-safe; `abrir(UUID usuarioId)` cria `SseEmitter` (timeout 30 min), remove-se em `onCompletion`/`onTimeout`/`onError`; `enviar(UUID usuarioId, String evento, Object dados)` entrega a todas as abas, descartando emitters que falharem.
- **Heartbeat**: `@Scheduled(fixedRate = 25_000)` envia comentário SSE (`: ping`) a todos (exige `@EnableScheduling`).
- **`GET /eventos`** (`EventosController`): `produces = text/event-stream`, devolve `sseRegistry.abrir(usuario.id())`.
- **Async dispatch**: o SSE usa dispatch assíncrono do servlet; o `SecurityConfiguration` precisa liberar `DispatcherType.ASYNC` (`dispatcherTypeMatchers(ASYNC).permitAll()`) para não responder 403 no meio do stream.
- **Eventos SSE**:
  - `mensagem` → `MensagemDto` do ponto de vista do destinatário (`minha=false`) — enviado ao **destinatário** e às **outras abas do remetente** (`minha=true`).
  - `notificacao` → `{ "total": n }` (contagem de não lidas) a cada notificação criada.
- **Push só depois do commit**: `MensagemEnviadaEvent` e `NotificacaoCriadaEvent` tratados por listeners `@TransactionalEventListener(phase = AFTER_COMMIT)`; falha no push nunca desfaz a mensagem.

## Notificação `MENSAGEM_RECEBIDA` (evento 7)

- Mensagem: `"Nova mensagem de {nomeRemetente}"`, `referenciaId` = id da conversa.
- Criada no envio (mesma transação, como as de agendamento) **somente se** não existir notificação `MENSAGEM_RECEBIDA` **não lida** para o destinatário com o mesmo `referenciaId`.
- `PATCH /conversas/{id}/lidas` também marca como lidas as notificações `MENSAGEM_RECEBIDA` daquela conversa do usuário.

## Exclusão de conta

- `UsuarioService.deletar`: antes de apagar o usuário, apaga mensagens e conversas do cliente (`MensagemRepository.deleteByConversa_Cliente_Id`, `ConversaRepository.deleteByCliente_Id`).
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

- `ChatServiceTest` (Mockito): abrir duplicada devolve a mesma (`criada=false`); não participante → 404 em listar/enviar/marcar; texto é `strip()`; 10 mensagens seguidas → 1 notificação `MENSAGEM_RECEBIDA`; após `marcarLidas`, nova mensagem gera nova notificação.
- `ConversaIntegrationTest` (H2 + cadeia real, limpeza no `@AfterEach` na ordem das FKs: mensagens → conversas → notificações → … ):
  - cliente abre → 201; de novo → 200 com o mesmo id; chef em `POST /conversas` → 403;
  - troca de mensagens, `GET /conversas` com `naoLidas` e prévia; `PATCH /lidas` zera e marca a notificação da conversa;
  - paginação com `antesDe`;
  - terceiro usuário → 404 em todas as rotas da conversa;
  - chef apagado: enviar → 403, histórico → 200;
  - `DELETE /usuario/me` de cliente com conversas → 204 e conversas/mensagens removidas.
- `SseRegistryTest`: duas abas recebem; emitter que falha é descartado; usuário sem conexão não quebra.
- `TempoRealListenerTest`: `MensagemEnviadaEvent` envia `mensagem` ao destinatário (`minha=false`) e ao remetente (`minha=true`); `NotificacaoCriadaEvent` envia `notificacao` com a contagem.
- `EventosIntegrationTest`: `GET /eventos` autenticado inicia async com `text/event-stream`; sem cookie → 401.

## Impacto no front

- Perfil do chef: botão "Conversar" → `POST /conversas` → abre a conversa.
- Ao logar: `new EventSource(API + "/eventos", { withCredentials: true })`; tratar `mensagem` (atualiza a conversa aberta/lista) e `notificacao` (atualiza o sininho). O polling de notificações vira só fallback.
- Tela de conversas: `GET /conversas`; conversa: `GET /conversas/{id}/mensagens` + rolagem com `antesDe`; ao abrir, `PATCH /conversas/{id}/lidas`.

## Fora de escopo

Anexos, "digitando", "visto", editar/apagar mensagem, denúncia de mensagem (pode entrar na moderação), múltiplas instâncias (pub/sub), notificações por e-mail/push.
