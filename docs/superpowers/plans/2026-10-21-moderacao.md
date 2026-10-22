# Moderação de chefs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admins definidos em `ADMIN_EMAILS`, denúncias de chef feitas por clientes atendidos, ocultação automática com 3 denunciantes distintos, e uma fila do admin com as ações arquivar, suspender, reativar e banir, com os eventos de notificação 4 a 6.

**Architecture:** O papel `ADMIN` vem da configuração e não do banco: o id do admin é `UUID.nameUUIDFromBytes(email)`. A visibilidade do chef ganha dois critérios calculados na consulta: não estar suspenso e ter menos de 3 denunciantes distintos com denúncia aberta. As notificações reaproveitam o `NotificacaoService` da fase 1.

**Tech Stack:** Java 17, Spring Boot 4.0.5, Spring Security 7, JPA (H2 nos testes), JUnit 5 + Mockito, MockMvc.

**Spec:** `docs/superpowers/specs/2026-10-21-moderacao-chefs-design.md` (com o onboarding e as notificações já na `main`)

## Global Constraints

- `admin.emails: ${ADMIN_EMAILS:}`, separados por vírgula. A comparação é feita depois de `trim().toLowerCase(Locale.ROOT)`. O id do admin é `UUID.nameUUIDFromBytes(emailNormalizado.getBytes(UTF_8))`.
- O login do admin é checado **antes** da busca de conta: o e-mail que está na lista vira ADMIN mesmo que tenha conta de cliente. O redirect é para `{front}/admin`.
- `POST /auth/register` com `tipoUsuario: ADMIN` → 400 `"tipo de usuário inválido"`.
- Elegibilidade para denunciar: o agendamento é **do cliente**, está **CONFIRMADO**, tem `dataHoraInicial < agora` e o `chefeId` faz parte dele. Senão → 404. Uma denúncia ABERTA do mesmo cliente para o mesmo chef já existindo → 409.
- Motivos: `COMPORTAMENTO`, `NAO_COMPARECEU`, `HIGIENE`, `PERFIL_FALSO`, `OUTRO`. `descricao`: obrigatória, não em branco, até 1000 caracteres.
- Chef visível e agendável: ativo, disponível, perfil completo, `!suspenso` e menos de 3 clientes distintos com denúncia `ABERTA`. Suspenso ou oculto, ao ser agendado → 409 (`ChefeIndisponivelException`).
- Ações do admin:
  - arquivar: `ABERTA` vira `ARQUIVADA`; se já estiver resolvida → 409;
  - suspender: `suspenso=true`, e as denúncias abertas do chef viram `PROCEDENTE`;
  - reativar: `suspenso=false`;
  - banir: `ativo=false`, e as abertas viram `PROCEDENTE`;
  - chef inexistente → 404 em todas.
- Mensagens das notificações:
  - `DENUNCIA_CRIADA` → cada admin: `"Nova denúncia contra o chef {nome} ({MOTIVO})"`, com `referenciaId` igual ao id da denúncia;
  - `CHEF_OCULTO`, só quando a contagem passa de 2 para 3 → o chef: `"Seu perfil foi ocultado da busca enquanto denúncias são analisadas"`; cada admin: `"Chef {nome} foi ocultado automaticamente (3 denúncias)"`. O `referenciaId` é o id do chef;
  - `CHEF_SUSPENSO` → o chef: `"Seu perfil foi suspenso"`;
  - `CHEF_REATIVADO` → o chef: `"Seu perfil foi reativado"`.
- Testes: `./mvnw -q clean test -Dspring.profiles.active=test`. Para contar, some `tests=` dos `TEST-*.xml`. A suíte precisa estar verde ao fim de cada task, e os testes de integração limpam o H2 na ordem das chaves estrangeiras: denúncias → agendamentos → endereços → usuários → chefs, e as notificações à parte.
- O `application-test.yml` ganha `admin.emails: admin@chefhome.test`.
- **Não commitar** `src/main/resources/application-prod.yml` nem `application.example.yml`. Commit sempre por caminho. Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **E-mail de admin com maiúsculas ou espaços** em `ADMIN_EMAILS` (por exemplo ` Admin@ChefHome.test `) → é reconhecido. Teste na Task 1.
2. **Admin chamando `GET /auth/me`** → 200 com `tipo=ADMIN`, e não o 401 de "conta apagada". Teste na Task 1.
3. **A 4ª denúncia de outro cliente** não gera um segundo `CHEF_OCULTO`. E, depois de arquivar uma das três, uma nova denúncia volta a gerar o aviso. Teste na Task 2.
4. **Chef suspenso** continua logando e vê `suspenso=true` em `GET /chefe/me`, mas não aparece na busca e não pode ser agendado. Teste na Task 3.
5. **Admin tentando denunciar ou agendar** → 403, e cliente ou chef em `/admin/**` → 403. Teste na Task 4.

---

### Task 1: Identidade de admin

**Files:**
- Create: `Security/AdminRegistry.java`, com `@Component` e `@Value("${admin.emails:}") String emails`
- Modify:
  - `enums/TipoUsuario.java` (acrescenta `ADMIN`)
  - `Dtos/LoginResult.java`, que ganha o terceiro campo `TipoUsuario tipo`, nulo no cadastro pendente
  - `Security/LoginService.java`: `loginGoogle` checa admin primeiro; `contaAtiva` trata `ADMIN` como `adminRegistry.ehAdmin(email)`; `register` recusa `ADMIN`
  - `Security/bff/GoogleLoginSuccessHandler.java`: `tipo == ADMIN` → redirect `/admin`
  - `Security/SecurityConfiguration.java`: `/admin/**` → `hasRole("ADMIN")`
  - `src/main/resources/application-test.yml`
- Test: `Security/AdminRegistryTest.java`, `Security/LoginServiceTest.java`, `Security/bff/GoogleLoginSuccessHandlerTest.java`, `Security/AdminIntegrationTest.java` (novo)

**Interfaces:**
- Produces:
  - `AdminRegistry.ehAdmin(String email)` → `boolean`
  - `AdminRegistry.idDe(String email)` → `UUID`
  - `AdminRegistry.ids()` → `List<UUID>`, um por admin configurado
  - `LoginResult(String accessToken, String registroToken, TipoUsuario tipo)`

- [ ] **Step 1: Escrever os testes que falham:**
  - `AdminRegistryTest`: reconhece `" Admin@ChefHome.test "`; `idDe` é estável (mesmo e-mail com caixas diferentes dá o mesmo UUID); `ids()` com 2 e-mails devolve 2; configuração vazia → nenhum admin.
  - `LoginServiceTest`:
    - `emailDeAdminRecebeTokenAdmin`, mesmo com uma conta de cliente com esse e-mail, e com `result.tipo() == ADMIN`;
    - `registerComTipoAdminDa400`;
    - `contaAtivaDeAdmin`;
    - os `LoginResult` dos testes existentes passam a ter 3 argumentos.
  - `GoogleLoginSuccessHandlerTest.adminVaiParaAdmin` → redirect `http://localhost:5173/admin`.
  - `AdminIntegrationTest`:
    - `adminVeAuthMe` → 200 e `$.tipo` igual a `ADMIN`;
    - `clienteEmRotaAdminDa403`, com `GET /admin/denuncias` → 403 antes mesmo de o controller existir;
    - `adminEmRotaDeClienteDa403`, com `GET /usuario/me`.
- [ ] **Step 2:** Rodar → FAIL.
- [ ] **Step 3:** Implementar. Os `switch` em `contaAtiva` e em `register` ganham o caso `ADMIN`.
- [ ] **Step 4:** Rodar a suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: admin role from ADMIN_EMAILS`.

---

### Task 2: Denúncia de chef

**Files:**
- Create: `enums/MotivoDenuncia.java`, `enums/StatusDenuncia.java`, `domin/Denuncia.java`, `repositorys/DenunciaRepository.java`, `Dtos/DenunciaRequestDto.java`, `Dtos/DenunciaCriadaDto.java`, `Services/DenunciaService.java`, `Controller/DenunciaController.java`
- Modify: `Security/SecurityConfiguration.java` (`POST /denuncias` → `hasRole(CLIENTE)`)
- Test: `Services/DenunciaServiceTest.java`, `Security/DenunciaIntegrationTest.java`

**Interfaces:**
- Consumes: `AdminRegistry.ids()` (Task 1) e `NotificacaoService.notificar(...)` (fase 1).
- Produces:
  - `DenunciaRepository`:
    - `boolean existsByClienteIdAndChefeIdAndStatus(UUID, UUID, StatusDenuncia)`
    - `long countDistinctClienteByChefeIdAndStatus(UUID chefeId, StatusDenuncia status)`, implementado com `@Query("select count(distinct d.cliente.id) from Denuncia d where d.chefe.id = :chefeId and d.status = :status")`
  - `DenunciaService.criar(DenunciaRequestDto dto, UUID clienteId)` → `DenunciaCriadaDto(Long id, StatusDenuncia status)`
  - `DenunciaRequestDto(@NotNull Long agendamentoId, @NotNull UUID chefeId, @NotNull MotivoDenuncia motivo, @NotBlank @Size(max = 1000) String descricao)`

- [ ] **Step 1: Escrever os testes que falham:**
  - `DenunciaServiceTest`:
    - `elegivelCriaAberta`;
    - não elegível → `RecursoNaoEncontradoException`, em quatro casos: agendamento de outro cliente, `PENDENDE`, futuro, chef fora do agendamento;
    - `duplicadaDa409`;
    - `notificaCadaAdmin`, com a mensagem exata;
    - `terceiroDenuncianteOcultaENotificaUmaVez`: a contagem passa de 2 para 3 → um `CHEF_OCULTO` para o chef e um para cada admin; um quarto denunciante distinto não gera outro `CHEF_OCULTO`.
  - `DenunciaIntegrationTest`, pela API, com agendamento `CONFIRMADO` no passado gravado no H2: `POST /denuncias` → 201; repetida → 409; chef → 403; corpo sem `descricao` → 400.
- [ ] **Step 2:** Rodar → FAIL.
- [ ] **Step 3:** Implementar. A ocultação é avisada quando a contagem **depois** do `save` for exatamente 3.
- [ ] **Step 4:** Rodar a suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: clients can report chefs they were served by`.

---

### Task 3: Visibilidade com suspensão e denúncias

**Files:**
- Modify:
  - `domin/ChefeModel.java`: `@Column(nullable = false) @ColumnDefault("false") private boolean suspenso`
  - `Chefe/ChefeSpecification.java`: `naoSuspenso()` e `semDenunciasEmExcesso()`, esta última com subquery `count(distinct cliente) < 3` sobre `Denuncia` com status `ABERTA`
  - `Services/ChefeService.java`: `mostrarChefes` combina os dois critérios novos
  - `repositorys/ChefeRepository.java`: `VISIVEL` ganha `AND c.suspenso = false AND (SELECT COUNT(DISTINCT d.cliente.id) FROM Denuncia d WHERE d.chefe = c AND d.status = 'ABERTA') < 3`
  - `Services/agendamentoService.java`: `validarChefes` trata suspenso ou oculto como indisponível (409)
  - `Dtos/ChefePerfilDto.java`: acrescenta `boolean suspenso` e `boolean ocultoPorDenuncias`
  - `Services/ChefeService.buscarPerfil`, que calcula o `ocultoPorDenuncias` pelo repositório de denúncias
  - `README.md`: o `ALTER TABLE chefe ADD COLUMN IF NOT EXISTS suspenso boolean NOT NULL DEFAULT false;`
- Test: `Services/ChefeServiceIntegrationTest.java`, `Services/AgendamentoServiceTest.java`, `Security/DenunciaIntegrationTest.java`

**Interfaces:**
- Consumes: `DenunciaRepository.countDistinctClienteByChefeIdAndStatus` (Task 2).
- Produces: `ChefePerfilDto(..., boolean suspenso, boolean ocultoPorDenuncias)`.

- [ ] **Step 1: Escrever os testes que falham:**
  - `ChefeServiceIntegrationTest`: `suspensoNaoApareceNaBuscaNemNosDestaques`; `comTresDenunciantesAbertosNaoAparece`; `comDuasDenunciasOuArquivadasAparece`.
  - `AgendamentoServiceTest.agendarChefSuspensoDa409`. Para o caso de chef oculto por denúncias, usar o `DenunciaRepository` mockado, que passa a ser injetado no serviço.
  - `DenunciaIntegrationTest.chefSuspensoVeAvisoNoPerfil`: `GET /chefe/me` → `suspenso=true`; `GET /chefe` não lista o chef.
- [ ] **Step 2:** Rodar → FAIL.
- [ ] **Step 3:** Implementar. A regra Java de "oculto por denúncias" usa o repositório; no `validarChefes`, é uma consulta por chef.
- [ ] **Step 4:** Rodar a suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: hide suspended or heavily reported chefs`.

---

### Task 4: Fila e ações do admin

**Files:**
- Create: `Dtos/DenunciaAdminDto.java`, `Services/ModeracaoService.java`, `Controller/AdminController.java`
- Modify: `README.md` (rotas `/denuncias` e `/admin/**` na tabela de rotas por papel, e `ADMIN_EMAILS` na tabela de variáveis)
- Test: `Services/ModeracaoServiceTest.java`, `Security/AdminIntegrationTest.java`

**Interfaces:**
- Produces: `ModeracaoService` com:
  - `Page<DenunciaAdminDto> listar(StatusDenuncia, Pageable)`, das mais antigas para as mais novas
  - `void arquivar(Long id, String adminEmail)`
  - `void suspender(UUID chefeId, String adminEmail)`
  - `void reativar(UUID chefeId)`
  - `void banir(UUID chefeId, String adminEmail)`
- Rotas:
  - `GET /admin/denuncias?status=ABERTA`
  - `PATCH /admin/denuncias/{id}/arquivar` → 204
  - `PATCH /admin/chefes/{id}/suspender|reativar|banir` → 204
- `DenunciaAdminDto`: `{id, motivo, descricao, status, criadaEm, chefe: {id, nome}, cliente: {id, nome}, agendamentoId}`.

- [ ] **Step 1: Escrever os testes que falham:**
  - `ModeracaoServiceTest`:
    - `arquivarGravaQuemResolveu`;
    - `arquivarResolvidaDa409`;
    - `suspenderMarcaProcedentesENotifica`, com `CHEF_SUSPENSO`;
    - `reativarNotifica`, com `CHEF_REATIVADO`;
    - `banirDesativaEMarcaProcedentes`;
    - chef inexistente → `ChefeNaoEncontradoException`.
  - `AdminIntegrationTest`:
    - `filaListaAbertas`;
    - `arquivarFazChefVoltar`: 3 denúncias, o chef some, uma é arquivada, o chef volta a `GET /chefe`;
    - `banidoNaoLoga`: `loginGoogle` com o e-mail do chef banido → `ContaInativaException`;
    - `clienteNaoUsaAdmin` → 403;
    - `adminNaoDenunciaNemAgenda`: `POST /denuncias` e `POST /agendamentos` com o cookie de admin → 403.
- [ ] **Step 2:** Rodar → FAIL.
- [ ] **Step 3:** Implementar e atualizar o README.
- [ ] **Step 4:** Rodar a suíte completa com `clean test` → PASS. Rodar também `-Dsurefire.runOrder=alphabetical` → PASS.
- [ ] **Step 5:** Commit `feat: admin moderation queue and chef actions`.
