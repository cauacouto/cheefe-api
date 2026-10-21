# Notificações dentro do app (fase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar a tabela, as rotas e a geração de notificações para os eventos de agendamento: criado, editado e confirmado.

**Architecture:** `agendamentoService` publica eventos do Spring. Um `NotificacaoListener` com `@TransactionalEventListener(BEFORE_COMMIT)` grava as notificações na mesma transação. `NotificacaoController` expõe a lista, a contagem e a marcação de lidas, sempre restritas ao dono pelo `UsuarioAutenticado`.

**Tech Stack:** Java 17, Spring Boot 4.0.5, Spring Security 7, JPA (H2 nos testes), JUnit 5 + Mockito, MockMvc.

**Spec:** `docs/superpowers/specs/2026-10-21-notificacoes-design.md`

## Global Constraints

- Mensagens exatas, com `{dd/MM}` e `{HH:mm}` vindos de `dataHoraInicial`:
  - `AGENDAMENTO_CRIADO`: `"{nomeCliente} pediu um agendamento para {dd/MM} às {HH:mm}"`
  - `AGENDAMENTO_EDITADO`: `"{nomeCliente} alterou o agendamento de {dd/MM}"`
  - `AGENDAMENTO_CONFIRMADO`: `"Chef {nomeChef} confirmou seu agendamento de {dd/MM}"`
- Destinatários: criado e editado vão para **cada chef** do agendamento; confirmado vai para o **cliente**. O `nomeChef` é o do chef que confirmou.
- O `referenciaId` é o id do agendamento como texto.
- O enum `EventoNotificacao` já nasce com: `AGENDAMENTO_CRIADO`, `AGENDAMENTO_EDITADO`, `AGENDAMENTO_CONFIRMADO`, `DENUNCIA_CRIADA`, `CHEF_OCULTO`, `CHEF_SUSPENSO`, `CHEF_REATIVADO` e `MENSAGEM_RECEBIDA`.
- Notificação de outra pessoa ou inexistente → 404. Marcar como lida é idempotente (204).
- Testes: `./mvnw -q clean test -Dspring.profiles.active=test`. Para contar, some `tests=` dos `TEST-*.xml`. A suíte inteira precisa estar verde ao fim de cada task. Testes de integração limpam o H2 num `@AfterEach`, na ordem das chaves estrangeiras (notificações não têm FK).
- **Não commitar** `src/main/resources/application-prod.yml` nem `application.example.yml`. Commit sempre por caminho. Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Agendamento recusado** (por exemplo, chef incompleto → 400): nenhuma notificação é gravada. Teste na Task 2.
2. **Agendamento com 2 chefs**: os dois recebem a notificação de criação, e a confirmação de um deles avisa o cliente com o nome **de quem confirmou**. Teste na Task 2.
3. **`PATCH /notificacoes/lidas` com outra pessoa logada ao mesmo tempo**: só as notificações de quem chamou mudam. Teste na Task 1.
4. **Contagem depois de marcar uma notificação como lida duas vezes**: diminui só uma vez, e a segunda chamada responde 204. Teste na Task 1.
5. **Agendamento editado mudando os chefs**: os chefs **novos** recebem a notificação de edição, porque o destino é calculado depois da edição. Teste na Task 2.

---

### Task 1: Núcleo (tabela, serviço e rotas)

**Files:**
- Create: `enums/EventoNotificacao.java`, `domin/Notificacao.java` (com o índice `@Index(columnList = "destinatarioId, lida, criadaEm")`), `repositorys/NotificacaoRepository.java`, `Services/NotificacaoService.java`, `Controller/NotificacaoController.java`, `Dtos/NotificacaoDto.java` (`record(Long id, EventoNotificacao evento, String mensagem, String referenciaId, boolean lida, LocalDateTime criadaEm)`), `Dtos/ContagemDto.java` (`record(long total)`)
- Test: `Security/NotificacaoIntegrationTest.java`

**Interfaces:**
- Produces:
  - `NotificacaoService`:
    - `Page<NotificacaoDto> listar(UUID destinatarioId, boolean somenteNaoLidas, Pageable pageable)`, das mais novas para as mais antigas
    - `long contarNaoLidas(UUID destinatarioId)`
    - `void marcarLida(Long id, UUID destinatarioId)`, que lança `RecursoNaoEncontradoException("notificação não encontrada")` (404)
    - `void marcarTodasLidas(UUID destinatarioId)`
    - `void notificar(UUID destinatarioId, TipoUsuario tipo, EventoNotificacao evento, String mensagem, String referenciaId)`, usado pela Task 2
  - Rotas:
    - `GET /notificacoes?somenteNaoLidas=false&page&size`
    - `GET /notificacoes/nao-lidas/contagem`
    - `PATCH /notificacoes/{id}/lida` → 204
    - `PATCH /notificacoes/lidas` → 204

- [ ] **Step 1: Escrever os testes que falham.** As notificações são gravadas direto pelo repositório, para dois usuários A e B com cookies gerados pelo `TokenService`:
  - `listaSoAsProprias`: A tem 2 notificações e B tem 1 → `GET /notificacoes` de A devolve 2, a mais nova primeiro;
  - `filtraNaoLidas`;
  - `contagemDeNaoLidas`: → `{"total":2}`;
  - `marcarLidaDuasVezesBaixaUmaVez`: as duas chamadas → 204, e a contagem cai de 2 para 1;
  - `marcarLidaDeOutraPessoaDa404`: e a notificação de B continua não lida;
  - `marcarTodasSoAfetaQuemChamou`: a contagem de A vai a 0 e a de B continua 1;
  - `semCookieDa401`.
- [ ] **Step 2:** Rodar `-Dtest=NotificacaoIntegrationTest` → FAIL.
- [ ] **Step 3:** Implementar. Para "marcar todas", usar um `@Modifying @Query("update Notificacao n set n.lida = true where n.destinatarioId = :id and n.lida = false")`.
- [ ] **Step 4:** Rodar a suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: in-app notifications storage and endpoints`.

---

### Task 2: Eventos de agendamento

**Files:**
- Create: `Services/eventos/AgendamentoCriadoEvent.java`, `AgendamentoEditadoEvent.java`, `AgendamentoConfirmadoEvent.java`, `Services/NotificacaoListener.java`
- Modify: `Services/agendamentoService.java` (recebe `ApplicationEventPublisher` e publica depois do `save` em criar, editar e confirmar)
- Test: `Services/NotificacaoListenerTest.java` (Mockito), `Security/NotificacaoIntegrationTest.java`, `Services/AgendamentoServiceTest.java` (acrescentar o `@Mock ApplicationEventPublisher`)

**Interfaces:**
- Consumes: `NotificacaoService.notificar(...)` (Task 1).
- Produces:
  - `record AgendamentoCriadoEvent(Long agendamentoId)`
  - `record AgendamentoEditadoEvent(Long agendamentoId)`
  - `record AgendamentoConfirmadoEvent(Long agendamentoId, UUID chefeId)`
  - `NotificacaoListener` com um método `@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)` para cada evento. Cada método carrega o agendamento por id (`AgendamentoRepository.findById`) e chama `notificar`.

- [ ] **Step 1: Escrever os testes que falham:**
  - `NotificacaoListenerTest.criadoNotificaCadaChef`: um agendamento de "Ana" em `25/10 18:00` com 2 chefs → 2 chamadas a `notificar` com `"Ana pediu um agendamento para 25/10 às 18:00"`, tipo `CHEFE` e `referenciaId` igual ao id;
  - `NotificacaoListenerTest.confirmadoNotificaClienteComNomeDeQuemConfirmou`: os chefs "Bia" e "Caio", e Caio confirma → `"Chef Caio confirmou seu agendamento de 25/10"` para o cliente;
  - `AgendamentoServiceTest`: criar, editar e confirmar publicam o evento certo, verificado com `verify(publisher).publishEvent(new AgendamentoCriadoEvent(id))` e equivalentes;
  - `NotificacaoIntegrationTest`, pela API real:
    - `criarAgendamentoNotificaOChef`: o chef vê a contagem 1 e a mensagem;
    - `editarNotificaOsChefsDepoisDaEdicao`: trocar X por Y via `chefeIds` → Y recebe `AGENDAMENTO_EDITADO`;
    - `confirmarNotificaOCliente`;
    - `agendamentoRecusadoNaoNotifica`: com um chef incompleto, o `POST /agendamentos` dá 400 e o total de notificações continua 0.
- [ ] **Step 2:** Rodar → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar a suíte completa → PASS. Acrescentar ao README uma linha "Notificações" na tabela "Rotas por papel" (qualquer usuário logado).
- [ ] **Step 5:** Commit `feat: notify chefs and clients about booking events`.
