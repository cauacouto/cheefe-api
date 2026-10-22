# Ciclo de vida do agendamento Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cancelar (cliente), recusar e desistir (chef, saindo do agendamento), concluir (chef ou rotina) e expirar (rotina), com motivos pré-definidos + texto livre, histórico só de inserção e notificações.

**Architecture:** As transições são métodos da entidade `Agendamento` (regra de status e horário, sem banco). O `AgendamentoService` carrega, checa dono/papel, aplica a transição, grava uma linha em `agendamento_historico` e publica o evento de notificação. Uma `AgendamentoRotina` agendada conclui e expira, um agendamento por transação, com trava.

**Tech Stack:** Java 17, Spring Boot 4.0.5, Spring Data JPA / Hibernate 7, Flyway (V6), Spring `@Scheduled`, JUnit 5 + Mockito + MockMvc, H2 e Testcontainers (Postgres 17).

**Spec:** `docs/superpowers/specs/2026-10-22-ciclo-agendamento-design.md` (v2)

## Global Constraints

- Pacotes por camada: `domain`, `repository`, `service` (`service/eventos`), `controller`, `dto`, `exception`, `enums`, `security`. Nomes e mensagens em português.
- `StatusAgendamento` = `PENDENTE, CONFIRMADO, CANCELADO, CONCLUIDO, RECUSADO, EXPIRADO`, gravado pelo nome.
- Prazo do cliente para cancelar `CONFIRMADO`: `agora <= dataHoraInicial - 24h`. "Antes do início" = `agora < dataHoraInicial`; "depois do início" = `agora >= dataHoraInicial`; "depois do fim" = `agora >= dataHoraFinal`. `agora` sempre de `LocalDateTime.now(clock)` (bean `Clock` já existe em `config/RelogioConfig`).
- Quem não é o cliente dono / não é chef do agendamento → 404 (`AgendamentoException`). Status ou prazo inválido → 409 `AgendamentoTransicaoInvalidaException` (nova, `extends ConflitoException`). Papel errado → 403. Chef apagado/banido → 403 (`ChefeModel.exigirAtivo`).
- Corpo de cancelar/recusar/desistir é opcional: `{ "motivo": "<código>", "descricao": "<até 300>" }`; `descricao` em branco vira nulo; código inválido ou do outro lado → 400.
- `MotivoCancelamento` (cliente): `MUDANCA_DE_PLANOS` "Mudança de planos", `DATA_OU_HORARIO` "Preciso mudar a data ou o horário", `ENCONTREI_OUTRA_OPCAO` "Encontrei outra opção", `VALOR` "O valor ficou alto", `OUTRO` "Outro motivo".
- `MotivoRecusa` (chef): `AGENDA_INDISPONIVEL` "Não tenho agenda nesse horário", `LOCAL_FORA_DA_AREA` "O local fica fora da minha área de atendimento", `NUMERO_DE_PESSOAS` "Não atendo essa quantidade de pessoas", `IMPREVISTO` "Tive um imprevisto", `OUTRO` "Outro motivo".
- Rotas novas respondem 200 sem corpo (como `PATCH /confirmar`).
- `agendamento_historico` é só de inserção para `chefhome_app` (REVOKE UPDATE, DELETE, TRUNCATE) e cai em cascata com o agendamento (`ON DELETE CASCADE`).
- Testes: `./mvnw -q clean test -Dspring.profiles.active=test` com Docker ligado; contar `tests/failures/errors/skipped` por nome nos `TEST-*.xml` (0 falhas, 0 erros, 0 pulados). Commit por caminho, mensagem terminando em `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Corpo ausente, `{}` ou `descricao` só com espaços** nas três rotas com motivo: aceito, motivo e descrição nulos no histórico. Teste na Task 3.
2. **Agendamento antigo sem `dataHoraInicial`/`dataHoraFinal`:** cancelar e recusar funcionam (não há horário para vencer); concluir pelo chef → 409; a rotina ignora. Testes nas Tasks 2 e 6.
3. **Chef que já saiu (recusou/desistiu) tenta agir de novo no mesmo agendamento** (recusar, concluir, ver histórico) → 404. Teste na Task 3.
4. **Rotina e chef concluindo ao mesmo tempo** / rotina rodando duas vezes: uma conclusão só, um registro de histórico, sem 500. Teste na Task 6.
5. **Cancelar duas vezes** (ou agir sobre estado final) → 409 com mensagem dizendo o status atual, sem nova linha de histórico nem notificação. Teste na Task 3.

---

### Task 1: Schema V6, enums e histórico

**Files:**
- Create: `src/main/resources/db/migration/V6__ciclo_agendamento.sql`, `enums/AcaoAgendamento.java`, `enums/MotivoCancelamento.java`, `enums/MotivoRecusa.java`, `enums/MotivoAgendamento.java`, `domain/AgendamentoHistorico.java`, `repository/AgendamentoHistoricoRepository.java`
- Modify: `enums/StatusAgendamento.java`, `enums/EventoNotificacao.java`
- Test: `MigracoesPostgresTest.java`

**Interfaces:**
- Produces:
  - `StatusAgendamento` + `RECUSADO`, `EXPIRADO`.
  - `EventoNotificacao` + `AGENDAMENTO_CANCELADO`, `AGENDAMENTO_RECUSADO`, `CHEF_DESISTIU`, `AGENDAMENTO_EXPIRADO`.
  - `AcaoAgendamento { CRIADO, EDITADO, CONFIRMADO, CANCELADO, RECUSADO, DESISTENCIA, CONCLUIDO, EXPIRADO }`.
  - `interface MotivoAgendamento { String name(); String rotulo(); }`, implementada por `MotivoCancelamento` e `MotivoRecusa` (valores e rótulos da Global Constraints).
  - `AgendamentoHistorico` (tabela `agendamento_historico`): `Long id` (identity), `Agendamento agendamento` (`@ManyToOne(fetch = LAZY, optional = false)` + `@OnDelete(action = OnDeleteAction.CASCADE)`), `AcaoAgendamento acao` (STRING), `UUID autorId`, `TipoUsuario autorTipo` (STRING), `String motivo` (código), `String descricao` (length 300), `LocalDateTime ocorridoEm`.
  - `AgendamentoHistoricoRepository extends JpaRepository<AgendamentoHistorico, Long>` com `List<AgendamentoHistorico> findByAgendamento_IdOrderByOcorridoEmAscIdAsc(Long agendamentoId)`.

- [ ] **Step 1: Testes no Postgres real** (`MigracoesPostgresTest`):
  - `checksDeEnumBatemComOsEnumsJava` ganha `agendamento_historico.acao` (`AcaoAgendamento`), `agendamento_historico.autor_tipo` (só `CLIENTE`, `CHEFE`) e `agendamento_historico.motivo` (união dos códigos de `MotivoCancelamento` e `MotivoRecusa`, sem repetir `OUTRO`); `agendamentos.status` e `notificacao.evento` já são cobertos e passam a exigir os valores novos.
  - `historicoEhSoDeInsercaoParaAApi`: como `chefhome_app`, `INSERT` em `agendamento_historico` funciona; `UPDATE`, `DELETE` e `TRUNCATE` → SQLState `42501` (seguir `auditoriaDaModeracaoEhSoDeInsercaoParaAApi`, re-rodando o setup antes).
  - `apagarAgendamentoLevaOHistorico`: como `chefhome_app`, apagar o agendamento apaga as linhas de histórico dele.
- [ ] **Step 2:** `./mvnw -q test -Dspring.profiles.active=test -Dtest=MigracoesPostgresTest` → FAIL (tabela e valores não existem).
- [ ] **Step 3:** V6: `SET search_path TO chefhome;` recria `agendamentos_status_check` e `notificacao_evento_check` com os valores novos (drop + add pelo mesmo nome), cria `agendamento_historico` (colunas e CHECKs do spec, FK `ON DELETE CASCADE`, índice `idx_agendamento_historico_agendamento (agendamento_id, ocorrido_em)`) e, no bloco `DO $$ ... IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'chefhome_app')`, `REVOKE UPDATE, DELETE, TRUNCATE ON agendamento_historico FROM chefhome_app` (como na V2). Enums, entidade e repositório conforme Interfaces.
- [ ] **Step 4:** O teste da Step 2 → PASS; suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: booking history table, new statuses and reasons (V6)`.

---

### Task 2: Transições na entidade

**Files:**
- Create: `exception/AgendamentoTransicaoInvalidaException.java`
- Modify: `domain/Agendamento.java`, `service/AgendamentoService.java` (só mover `calcularValor`)
- Test: `domain/AgendamentoTransicoesTest.java`

**Interfaces:**
- Consumes: `StatusAgendamento` (Task 1).
- Produces (em `Agendamento`, todos lançam `AgendamentoTransicaoInvalidaException(String mensagem)` quando o status ou o horário não permite):
  - `static BigDecimal calcularValor(List<ChefeModel> chefes, LocalDateTime inicio, LocalDateTime fim)` (movido do serviço, mesma regra).
  - `void confirmar(LocalDateTime agora)` — `PENDENTE` e antes do início → `CONFIRMADO`.
  - `void exigirEditavel(LocalDateTime agora)` — `PENDENTE` e antes do início (usada pelo `PUT`).
  - `void cancelarPeloCliente(LocalDateTime agora)` — `PENDENTE` antes do início, ou `CONFIRMADO` com `agora <= dataHoraInicial - 24h` → `CANCELADO`.
  - `boolean recusar(ChefeModel chefe, LocalDateTime agora)` — `PENDENTE` antes do início; remove o chef de `chefes`; com chefs restantes recalcula `valorTotal` e devolve `true`; sem chefs → `RECUSADO`, devolve `false`.
  - `boolean desistir(ChefeModel chefe, LocalDateTime agora)` — mesmo, a partir de `CONFIRMADO`; sem chefs → `CANCELADO`.
  - `void concluirPeloChef(LocalDateTime agora)` — `CONFIRMADO` e depois do início → `CONCLUIDO`.
  - `boolean concluirPeloSistema(LocalDateTime agora)` / `boolean expirarPeloSistema(LocalDateTime agora)` — devolvem `false` (sem lançar) se o status ou o horário já não se aplica (`CONFIRMADO` e depois do fim; `PENDENTE` e depois do início).
- Datas nulas (agendamentos antigos): sem `dataHoraInicial`, "antes do início" é verdadeiro e "depois do início" é falso.

- [ ] **Step 1: Testes** (`AgendamentoTransicoesTest`, `Clock` fixo, sem Spring), uma asserção por linha da tabela do spec, incluindo:
  - `cancelarConfirmadoComExatamente24hPode` / `cancelarConfirmadoCom23h59Da409`;
  - `cancelarPendenteDepoisDoInicioDa409`, `recusarPendenteDepoisDoInicioDa409`, `confirmarDepoisDoInicioDa409`, `editarDepoisDoInicioDa409`;
  - `concluirPeloChefAntesDoInicioDa409`, `concluirPeloChefDepoisDoInicioConclui`;
  - `recusaParcialTiraOChefERecalculaEMantemPendente` (2 chefs de R$ 100/h e R$ 50/h, 2h → depois da recusa do segundo, `valorTotal` = 200.00), `recusaDoUltimoChefViraRecusado`, `desistenciaParcialMantemConfirmado`, `desistenciaDoUltimoViraCancelado`;
  - `estadoFinalNaoMudaMais` (cada ação a partir de `CANCELADO`, `RECUSADO`, `CONCLUIDO`, `EXPIRADO` → 409, mensagem contém o status);
  - `sistemaNaoConcluiAntesDoFimNemExpiraAntesDoInicio` (devolvem `false`);
  - `semDatasCancelaERecusaMasNaoConclui`.
- [ ] **Step 2:** `-Dtest=AgendamentoTransicoesTest` → FAIL (métodos não existem).
- [ ] **Step 3:** Implementar conforme Interfaces; o serviço passa a chamar `Agendamento.calcularValor`.
- [ ] **Step 4:** O teste → PASS; suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: booking state transitions on the entity`.

---

### Task 3: Rotas de cancelar, recusar, desistir e concluir + histórico

**Files:**
- Create: `dto/CancelamentoRequestDto.java`, `dto/RecusaRequestDto.java`, `service/HistoricoAgendamentoService.java`
- Modify: `service/AgendamentoService.java`, `controller/AgendamentoController.java`, `security/SecurityConfiguration.java`
- Test: `security/CicloAgendamentoIntegrationTest.java`, `service/AgendamentoServiceTest.java` (construtor/mocks)

**Interfaces:**
- Consumes: Task 1 (histórico, enums), Task 2 (transições).
- Produces:
  - `record CancelamentoRequestDto(MotivoCancelamento motivo, @Size(max = 300) String descricao)`, `record RecusaRequestDto(MotivoRecusa motivo, @Size(max = 300) String descricao)`.
  - `HistoricoAgendamentoService.registrar(Agendamento agendamento, AcaoAgendamento acao, UUID autorId, TipoUsuario autorTipo, MotivoAgendamento motivo, String descricao)` — grava com `ocorridoEm = now(clock)`, `descricao` em branco → nulo.
  - `AgendamentoService` (recebe `Clock` e `HistoricoAgendamentoService`): `cancelarAgendamento(Long id, UUID clienteId, CancelamentoRequestDto dto)`, `recusarAgendamento(Long id, UUID chefeId, RecusaRequestDto dto)`, `desistirDoAgendamento(Long id, UUID chefeId, RecusaRequestDto dto)`, `concluirAgendamento(Long id, UUID chefeId)`. `dto` pode ser nulo.
  - `criarAgendamento`, `atualizarAgendamento` e `confirmarAgendamento` passam a registrar `CRIADO`, `EDITADO`, `CONFIRMADO`; `atualizarAgendamento` usa `exigirEditavel(agora)` e `confirmarAgendamento` usa `confirmar(agora)` (mantém a trava e a checagem de conflito).
  - Rotas (200 sem corpo; corpo `@RequestBody(required = false) @Valid`): `PATCH /agendamentos/{id}/cancelar` (CLIENTE), `PATCH /agendamentos/{id}/recusar`, `/desistir`, `/concluir` (CHEFE). Matchers no `SecurityConfiguration` ao lado do `confirmar`.

- [ ] **Step 1: Testes de integração** (`CicloAgendamentoIntegrationTest`, helpers como no `ConflitoHorarioIntegrationTest`, agendamentos com início em `now + 10 dias` salvos pelo repositório):
  - `clienteCancelaPendenteComMotivoEDescricao` → 200, status `CANCELADO`, histórico `CANCELADO` com `autorTipo=CLIENTE`, `motivo=MUDANCA_DE_PLANOS`, `descricao`;
  - `cancelarSemCorpoComCorpoVazioOuDescricaoEmBranco` → 200 nos três, histórico com motivo e descrição nulos (Review Focus 1);
  - `cancelarDuasVezesDa409SemNovoHistorico` (Review Focus 5);
  - `motivoDoOutroLadoDa400`: cliente com `AGENDA_INDISPONIVEL` → 400; chef com `VALOR` → 400; `descricao` com 301 caracteres → 400;
  - `recusaParcialEDepoisOChefQueSaiuRecebe404`: agendamento com Bia e Caio, Bia recusa → 200, ainda `PENDENTE`, só Caio, valor recalculado; Bia recusa de novo, conclui ou vê histórico → 404 (Review Focus 3);
  - `desistenciaDoUltimoChefCancelaELiberaOHorario`: confirmado, chef desiste → `CANCELADO`; outro cliente agenda e o chef confirma o mesmo horário → 200;
  - `chefConcluiDepoisDoInicio` (agendamento com início no passado) → `CONCLUIDO`;
  - `donoEPapel`: outro cliente cancela → 404; chef de fora recusa → 404; chef na rota de cancelar → 403; cliente na rota de recusar → 403; chef banido recusa → 403;
  - `criarEditarEConfirmarRegistramHistorico`.
- [ ] **Step 2:** `-Dtest=CicloAgendamentoIntegrationTest` → FAIL (404 nas rotas).
- [ ] **Step 3:** Implementar conforme Interfaces; ajustar o construtor/mocks do `AgendamentoServiceTest` (adicionar `Clock` fixo e mock do `HistoricoAgendamentoService`).
- [ ] **Step 4:** O teste → PASS; suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: cancel, refuse, withdraw and complete bookings, with history`.

---

### Task 4: Notificações

**Files:**
- Create: `service/eventos/AgendamentoCanceladoEvent.java`, `service/eventos/ChefSaiuDoAgendamentoEvent.java`, `service/eventos/AgendamentoExpiradoEvent.java`
- Modify: `service/AgendamentoService.java`, `service/NotificacaoListener.java`
- Test: `service/NotificacaoListenerTest.java`, `security/CicloAgendamentoIntegrationTest.java`

**Interfaces:**
- Consumes: Task 3 (rotas), Task 1 (eventos de notificação, motivos).
- Produces:
  - `record AgendamentoCanceladoEvent(Long agendamentoId, MotivoAgendamento motivo, String descricao)`.
  - `record ChefSaiuDoAgendamentoEvent(Long agendamentoId, String nomeChef, boolean recusa, boolean restamChefes, MotivoAgendamento motivo, String descricao)` — `recusa=true` para recusar, `false` para desistir; o nome vai no evento porque o chef já saiu de `chefes` quando o listener roda.
  - `record AgendamentoExpiradoEvent(Long agendamentoId)` (publicado pela rotina, Task 6).
  - Listener (`BEFORE_COMMIT`, como os atuais), textos exatos do spec; `referenciaId` = id do agendamento; data `dd/MM`; valor `R$ 300,00` (formato pt-BR); sufixo `": {rótulo}"` se há motivo e `" — {descrição até 120 caracteres + …}"` se há descrição.

- [ ] **Step 1: Testes** (`NotificacaoListenerTest`, mocks como os existentes):
  - `cancelamentoAvisaCadaChef` → `"Ana cancelou o agendamento de 25/10: Mudança de planos"` para cada chef, evento `AGENDAMENTO_CANCELADO`;
  - `recusaParcialAvisaOClienteComONovoValor` → `"Chef Bia recusou seu pedido de 25/10. Novo valor: R$ 300,00: O local fica fora da minha área de atendimento — fico em Santo André"`;
  - `recusaTotal` → `"Seu pedido de 25/10 foi recusado"`; `desistenciaTotal` → `"O agendamento de 25/10 foi cancelado: o chef Bia desistiu"`;
  - `expiracao` → `"Seu pedido de 25/10 expirou sem confirmação"`, evento `AGENDAMENTO_EXPIRADO`;
  - `descricaoLongaEhEncurtadaECabeNos300` (descrição de 300 caracteres → mensagem ≤ 300, termina em `…`).
  - Integração: depois de `PATCH /recusar`, `GET /notificacoes` do cliente traz `AGENDAMENTO_RECUSADO` com `referenciaId` do agendamento.
- [ ] **Step 2:** Rodar → FAIL. **Step 3:** Implementar. **Step 4:** Rodar → PASS; suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: notifications for cancelled, refused, withdrawn and expired bookings`.

---

### Task 5: `GET /agendamentos/{id}/historico`

**Files:**
- Create: `dto/HistoricoAgendamentoDto.java`
- Modify: `service/AgendamentoService.java`, `controller/AgendamentoController.java`, `security/SecurityConfiguration.java`
- Test: `security/CicloAgendamentoIntegrationTest.java`

**Interfaces:**
- Produces: `record HistoricoAgendamentoDto(AcaoAgendamento acao, Autor autor, Motivo motivo, String descricao, @JsonFormat(pattern = "dd-MM-yyyy HH:mm") LocalDateTime ocorridoEm)` com `record Autor(UUID id, String nome, TipoUsuario tipo)` e `record Motivo(String codigo, String rotulo)`; `autor`/`motivo` nulos quando ausentes. `AgendamentoService.historico(Long id, UsuarioAutenticado usuario) : List<HistoricoAgendamentoDto>` — cliente dono ou chef atual, senão 404. Nome do autor resolvido na leitura (`UsuarioRepository`/`ChefeRepository`, buscando os ids distintos de uma vez); rótulo do motivo pelo código em `MotivoCancelamento` ou `MotivoRecusa`.
- Rota: `GET /agendamentos/{id}/historico` → `hasAnyRole(CLIENTE, CHEFE)`.

- [ ] **Step 1: Testes:** `historicoEmOrdemComAutorEMotivo` (criar → recusa parcial com motivo → cancelar: três linhas em ordem, cada uma com o nome do autor e, quando houver, `motivo.rotulo` em português); `historicoDeOutroDa404`; `chefQueSaiuNaoVeOHistorico` (já coberto na Task 3: reaproveitar).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS; suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: booking timeline endpoint`.

---

### Task 6: Rotina de conclusão e expiração

**Files:**
- Create: `service/AgendamentoRotina.java`
- Modify: `repository/AgendamentoRepository.java`, `service/AgendamentoService.java`
- Test: `service/AgendamentoRotinaIntegrationTest.java`

**Interfaces:**
- Consumes: Tasks 2–4.
- Produces:
  - `AgendamentoRepository`: `@Query List<Long> idsParaConcluir(@Param("agora") LocalDateTime agora)` (`CONFIRMADO` e `dataHoraFinal <= agora`), `List<Long> idsParaExpirar(LocalDateTime agora)` (`PENDENTE` e `dataHoraInicial <= agora`), `@Lock(PESSIMISTIC_WRITE) Optional<Agendamento> travarPorId(Long id)`.
  - `AgendamentoService.concluirPeloSistema(Long id)` / `expirarPeloSistema(Long id)` (`@Transactional`): trava, chama o método da entidade, e só se devolver `true` grava histórico (autor nulo) e, na expiração, publica `AgendamentoExpiradoEvent`.
  - `AgendamentoService.concluirAgendamento` (Task 3) passa a carregar com `travarPorId` também, para a corrida com a rotina.
  - `AgendamentoRotina.processar()` com `@Scheduled(fixedDelayString = "PT15M")`: para cada id, chama o serviço num `try/catch` que loga e segue.

- [ ] **Step 1: Testes** (`@SpringBootTest`, `@MockitoBean Clock` ou `Clock` de teste adiantado, chamando `processar()` direto):
  - `concluiOsConfirmadosQueJaTerminaramEExpiraOsPendentesQueJaComecaram` (e não mexe nos outros);
  - `rodarDuasVezesNaoDuplicaHistoricoNemNotificacao`;
  - `agendamentoSemDatasEhIgnorado` (Review Focus 2);
  - `falhaEmUmNaoImpedeOsOutros` (um id que lança no serviço via spy; os demais processados);
  - `chefConcluindoEnquantoARotinaConcluiFicaUmaConclusaoSo`: concluir pelo chef e `processar()` em paralelo (latch, como nos testes de concorrência existentes) → um `CONCLUIDO`, uma linha `CONCLUIDO` no histórico, nenhuma resposta 500 (Review Focus 4).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS; suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: scheduled completion and expiry of bookings`.

---

### Task 7: Denúncia de concluído, relógio no `criadoEm` e documentação

**Files:**
- Modify: `service/DenunciaService.java`, `domain/Agendamento.java`, `service/AgendamentoService.java`, `README.md`
- Test: `security/DenunciaIntegrationTest.java`, `security/CicloAgendamentoIntegrationTest.java`

**Interfaces:**
- `DenunciaService.criar` aceita agendamento `CONFIRMADO` ou `CONCLUIDO` com início no passado.
- `criarAgendamento` define `criadoEm = now(clock)`; o `@PrePersist` só preenche se estiver nulo.

- [ ] **Step 1: Testes:** `denunciaDeAgendamentoConcluido` → 201; `criadoEmVemDoRelogio` (relógio fixo no `AgendamentoServiceTest`).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar; README: rotas novas na tabela de papéis (com as listas de motivos), estados e regras de prazo numa seção curta "Ciclo do agendamento", rotina de 15 min.
- [ ] **Step 4:** Suíte completa → PASS, também com `-Dsurefire.runOrder=alphabetical`.
- [ ] **Step 5:** Commit `feat: reports on completed bookings; clock on creation; document the booking lifecycle`.
