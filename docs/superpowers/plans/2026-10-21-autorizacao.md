# Autorização por dono e papel (lote 10) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fazer cada pessoa agir só sobre o que é dela, e exigir o papel certo (CLIENTE/CHEFE) por rota, fechando C2, C3 e C4.

**Architecture:** O papel é checado por rota no `SecurityConfiguration` (`hasRole`). O dono é checado nos serviços por consultas `findByIdAnd<Dono>_Id`: recurso alheio é igual a inexistente e devolve 404. Controllers recebem `@AuthenticationPrincipal UsuarioAutenticado` e repassam `usuario.id()`; nenhuma identidade vem do cliente.

**Tech Stack:** Java 17, Spring Boot 4.0.5, Spring Security 7, Spring Data JPA, JUnit 5 + Mockito, MockMvc + `spring-security-test`, H2 (perfil test).

**Spec:** `docs/superpowers/specs/2026-10-21-autorizacao-design.md`

## Global Constraints

- Papel errado → **403**. Recurso inexistente **ou** de outra pessoa → **404** (mesma exceção de "não encontrado"). Status que não permite a ação → **409**.
- Identidade **nunca** vem de path, query ou body: sempre de `UsuarioAutenticado` (`id()`, `email()`, `tipo()`).
- Sem papel `ADMIN`. Rotas removidas: `GET /usuario`, `GET /usuario/{id}`, `DELETE /usuario/{id}`, `PUT /usuario?id=`, `PATCH /chefe/{id}`, `PATCH /chefe/{id}/inativar`, `DELETE /chefe/{id}`, `PUT /pratos?id=`, `GET /pratos/pratos/meus-pratos`, `GET /agendamentos/usuario/{id}`, `GET /agendamentos/chefe/{id}`, `POST /agendamentos?email=`.
- Exceções 404 já existentes: `UsuarioException`, `EnderecoException`, `AgendamentoException`, `ChefeNaoEncontradoException`, `PratoNaoEncontradoException`. 409 de status: `AgendamentoNaoEditavelException(StatusAgendamento)`.
- Nomes em português, Lombok `@RequiredArgsConstructor`, sem renomear pacotes ou classes existentes (lote 14).
- Testes: `./mvnw -q test -Dspring.profiles.active=test` (classe: `-Dtest=Nome`). A suíte inteira precisa estar verde no fim de cada task.
- **Não commitar** `src/main/resources/application-prod.yml`, que está staged e é do usuário. Commit sempre por caminho: `git commit -m ... -- <arquivos>`.
- Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **`PUT /usuario/me` tentando trocar o e-mail**: o e-mail é a identidade do Google e não pode mudar, senão o próximo login cria conta duplicada. O campo sai do `UserRequestDto`, e um `"email"` no JSON é ignorado. Teste na Task 2.
2. **Depois de `DELETE /usuario/me` ou `DELETE /chefe/me`**: o cookie `accessToken` volta expirado. A pessoa não deve continuar "logada" numa conta que acabou de apagar. Testes nas Tasks 2 e 3.
3. **Token válido de conta já apagada** chamando `GET /usuario/me`: responde 404, não 500. Teste na Task 2.
4. **`POST /agendamentos` com `"usuarioId"`/`"email"` de outra pessoa no corpo**: os campos são ignorados e o agendamento é criado para quem está logado. Teste na Task 5.
5. **Agendamento com dois chefs**: qualquer um dos dois pode confirmar. Um chef fora do agendamento recebe 404, e uma segunda confirmação recebe 409. Teste na Task 5.

---

### Task 1: Papéis por rota e a base do teste de autorização

**Files:**
- Modify: `src/main/java/com/couto/chefe_api/Security/SecurityConfiguration.java` (bloco `authorizeHttpRequests`)
- Create: `src/test/java/com/couto/chefe_api/Security/AutorizacaoIntegrationTest.java`

**Interfaces:**
- Produces: `AutorizacaoIntegrationTest`, com `@SpringBootTest`, `@ActiveProfiles("test")` e MockMvc via `webAppContextSetup(context).apply(springSecurity())`. Os helpers ficam disponíveis para as tasks seguintes:
  - `UserModel novoCliente()` grava no H2 um cliente com e-mail `cliente-<uuid>@x.com`, nome `"Cliente"` e lista de endereços vazia;
  - `ChefeModel novoChefe()` grava um chef ativo e disponível, com nome, nacionalidade `"Brasileira"`, idiomas `"pt"`, `valorHora` 100;
  - `Cookie cookieDe(UserModel)` e `Cookie cookieDe(ChefeModel)` geram o cookie `accessToken` via `TokenService.gerarToken(new UsuarioAutenticado(id, email, tipo))`.
- Matchers, nesta ordem e antes de `anyRequest().authenticated()`:
  - `hasRole("CLIENTE")`: `/usuario/**`, `POST /agendamentos`, `PUT /agendamentos/*`, `GET /agendamentos/meus`
  - `hasRole("CHEFE")`: `PATCH /chefe/me`, `PATCH /chefe/me/inativar`, `DELETE /chefe/me`, `POST /pratos`, `PUT /pratos/*`, `GET /pratos/meus`, `GET /agendamentos/recebidos`, `PATCH /agendamentos/*/confirmar`

- [ ] **Step 1: Escrever os testes que falham** (`AutorizacaoIntegrationTest`):
  - `chefNaoAcessaRotasDeCliente`: com o cookie de chef, `GET /usuario/me` → 403, `POST /agendamentos` (JSON `{}`) → 403 e `GET /agendamentos/meus` → 403.
  - `clienteNaoAcessaRotasDeChef`: com o cookie de cliente, `PATCH /chefe/me` (JSON `{}`) → 403, `GET /pratos/meus` → 403, `PATCH /agendamentos/1/confirmar` → 403 e `GET /agendamentos/recebidos` → 403.
  - `semCookieDa401`: `GET /usuario/me` → 401.
- [ ] **Step 2:** Rodar `-Dtest=AutorizacaoIntegrationTest`. Esperado: FAIL (hoje volta 404 ou 200 no lugar de 403).
- [ ] **Step 3:** Adicionar os matchers.
- [ ] **Step 4:** Rodar a suíte completa → PASS. Se algum teste existente quebrar por causa do papel, o teste ajusta o papel do usuário; a regra não muda.
- [ ] **Step 5:** Commit `feat: require CLIENTE/CHEFE role per route`.

---

### Task 2: Usuário e endereços pelo `/me`

**Files:**
- Modify: `Controller/UsuarioController.java`, `User/UsuarioService.java`, `Services/EnderecoService.java`, `Dtos/UserRequestDto.java` (remove `email`)
- Test: `User/UsuarioServiceIntegrationTest.java`, `Services/EnderecoServiceTest.java`, `Controller/ValidacaoRequisicoesTest.java`, `Security/AutorizacaoIntegrationTest.java`

**Interfaces:**
- Consumes: os helpers da Task 1 e `AuthCookies` (`expirar(ACCESS_TOKEN, PATH_RAIZ)`, `adicionar`).
- Produces:
  - `UsuarioService`:
    - `UserResponseDto buscar(UUID usuarioId)`
    - `UserResponseDto atualizar(UserRequestDto dto, UUID usuarioId)`
    - `void deletar(UUID usuarioId)`
    - `listarUsuarios` e `buscarPorId` saem.
  - `EnderecoService`:
    - `List<EnderecoResponseDto> listarEnderecos(UUID usuarioId)`
    - `EnderecoResponseDto adicionarEndereco(EnderecoRequestDto dto, UUID usuarioId)`
    - usuário inexistente → `UsuarioException`.
  - `UsuarioController(UsuarioService, EnderecoService, AuthCookies)`:
    - `GET /usuario/me`, `PUT /usuario/me` e `DELETE /usuario/me`; o DELETE responde 204 e expira o `accessToken`;
    - `GET /usuario/enderecos` e `POST /usuario/enderecos` continuam;
    - todos recebem `@AuthenticationPrincipal UsuarioAutenticado`.
  - `UserRequestDto(nome, telefone, endereco)`, sem `email`.

- [ ] **Step 1: Escrever os testes que falham:**
  - `UsuarioServiceIntegrationTest`: troque as chamadas para `atualizar(dto, usuarioId)` e acrescente `atualizarNaoMudaOEmail`. O DTO sem e-mail é aplicado e o e-mail gravado continua o original.
  - `EnderecoServiceTest`: troque os stubs `findByEmail("ana@x.com")` por `findById(id)` e chame com o `UUID`.
  - `ValidacaoRequisicoesTest`:
    - registre `new AuthenticationPrincipalArgumentResolver()` com `setCustomArgumentResolvers` e coloque um `UsuarioAutenticado` no `SecurityContextHolder` dentro do `@BeforeEach`, limpando no `@AfterEach`;
    - `put("/usuario").param("id", …)` vira `put("/usuario/me")`;
    - a asserção `hasItems("email", "endereco[0].cep")` vira `hasItems("endereco[0].cep")`.
  - `AutorizacaoIntegrationTest`:
    - `clienteVeSoOsProprioDados`: `GET /usuario/me` com o cookie de A → 200 e `$.email` igual ao de A;
    - `rotasAntigasPorIdNaoExistem`: `GET /usuario/{idDeB}` e `DELETE /usuario/{idDeB}` com o cookie de A → 404, e B continua no banco;
    - `listagemDeUsuariosNaoExiste`: `GET /usuario` com o cookie de A → 404 ou 405, sem lista no corpo;
    - `putIgnoraEmailNoCorpo`: `PUT /usuario/me` com `"email":"outro@x.com"` → 200, e o e-mail no banco não muda;
    - `deleteMeApagaEExpiraCookie`: `DELETE /usuario/me` → 204, o `Set-Cookie` contém `accessToken=` com `Max-Age=0`, e A não está mais no banco;
    - `tokenDeContaApagadaDa404`: com o cookie de um cliente já apagado, `GET /usuario/me` → 404.
- [ ] **Step 2:** Rodar as quatro classes → FAIL.
- [ ] **Step 3:** Implementar. Cada busca por e-mail do usuário logado é trocada por `findById(usuarioId)`.
- [ ] **Step 4:** Rodar a suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: user and address endpoints scoped to the logged-in client`.

---

### Task 3: Chef pelo `/me`

**Files:**
- Modify: `Controller/ChefeController.java`
- Test: `Controller/ValidacaoRequisicoesTest.java`, `Security/AutorizacaoIntegrationTest.java`

**Interfaces:**
- Consumes: `ChefeService.atualizarDados(ChefeRequestDto, UUID)`, `inativar(UUID)` e `deletar(UUID)`, sem mudança, e `AuthCookies`.
- Produces: `ChefeController(ChefeService, AuthCookies)` com:
  - `PATCH /chefe/me`;
  - `PATCH /chefe/me/inativar` → 204;
  - `DELETE /chefe/me` → 204, expirando o `accessToken`;
  - `GET /chefe` e `GET /chefe/destaque` continuam iguais.

- [ ] **Step 1: Escrever os testes que falham:**
  - `ValidacaoRequisicoesTest`: `patch("/chefe/" + id)` vira `patch("/chefe/me")`.
  - `AutorizacaoIntegrationTest`:
    - `chefEditaSoOProprioPerfil`: `PATCH /chefe/me` com `{"nome":"Novo"}` e o cookie de X → 200; o nome de X muda e o de Y não;
    - `rotaAntigaDeChefPorIdNaoExiste`: `PATCH /chefe/{idDeY}` com o cookie de X → 404, e Y continua igual;
    - `clienteNaoEditaChef`: `PATCH /chefe/me` com o cookie de cliente → 403;
    - `deleteChefMeInativaEExpiraCookie`: `DELETE /chefe/me` → 204, X fica com `ativo=false`, e o `Set-Cookie` tem `accessToken=` com `Max-Age=0`.
- [ ] **Step 2:** Rodar → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar a suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: chef profile endpoints scoped to the logged-in chef`.

---

### Task 4: Pratos com dono

**Files:**
- Modify: `Controller/PratosController.java` (remove a dependência de `ChefeRepository`), `Services/PratosService.java`, `repositorys/PatrosRepository.java`
- Test: `Services/PratosServiceTest.java`, `Controller/ValidacaoRequisicoesTest.java`, `Security/AutorizacaoIntegrationTest.java`

**Interfaces:**
- Produces:
  - `PatrosRepository.findByIdAndChefeModel_Id(Integer id, UUID chefeId)` → `Optional<Pratos>`.
  - `PratosService`:
    - `cadastrarPratos(CadastraPratosDtoRequest, MultipartFile, UUID chefeId)`, sem mudança;
    - `CadastratPratosDtoResponse updatePratos(CadastraPratosDtoRequest dto, Integer pratoId, UUID chefeId)`; prato inexistente ou de outro chef → `PratoNaoEncontradoException`;
    - `listaPratos(UUID)`, sem mudança.
  - `PratosController(PratosService)` com `POST /pratos`, `PUT /pratos/{id}` e `GET /pratos/meus`, todos usando `usuario.id()`; `GET /pratos` e `GET /pratos/chefe/{chefeId}` continuam iguais.

- [ ] **Step 1: Escrever os testes que falham:**
  - `PratosServiceTest`:
    - `updatePratoDeOutroChefDa404`: `findByIdAndChefeModel_Id(1, outroId)` vazio → `PratoNaoEncontradoException`, e `save` nunca é chamado;
    - `updatePratoProprioAtualiza`.
  - `ValidacaoRequisicoesTest`: `put("/pratos").param("id","1")` vira `put("/pratos/1")`.
  - `AutorizacaoIntegrationTest`:
    - `chefNaoEditaPratoDeOutroChef`: um prato de Y gravado no H2; `PUT /pratos/{idDoPrato}` com o cookie de X → 404, e o prato continua igual;
    - `meusPratosListaSoOsDoChef`: `GET /pratos/meus` com o cookie de X → só os pratos de X.
- [ ] **Step 2:** Rodar → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar a suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: dish edits restricted to the owning chef`.

---

### Task 5: Agendamentos com dono e o README

**Files:**
- Modify: `Controller/AgendamentoController.java`, `Services/agendamentoService.java`, `repositorys/AgendamentoRepository.java`, `repositorys/EndercoRepository.java`, `README.md` (seção "Rotas por papel")
- Test: `Services/AgendamentoServiceTest.java`, `Controller/ValidacaoRequisicoesTest.java`, `Security/AutorizacaoIntegrationTest.java`

**Interfaces:**
- Produces:
  - `EndercoRepository.findByIdAndUsuario_Id(Long id, UUID usuarioId)` → `Optional<EnderecoModel>`.
  - `AgendamentoRepository.findByIdAndUsuario_Id(Long id, UUID usuarioId)` → `Optional<Agendamento>`.
  - `agendamentoService`:
    - `criarAgendamento(AgendamentoResquestDto dto, UUID usuarioId)`: cliente inexistente → `UsuarioException`; endereço que não é do cliente → `EnderecoException`;
    - `listarAgendamentosUsuario(UUID usuarioId)` e `listarAgendamentosChefe(UUID chefeId)`, sem mudança;
    - `atualizarAgedamento(AtualizarAgendamentoRequestDto dto, Long id, UUID usuarioId)`: agendamento alheio → `AgendamentoException`; `enderecoId` alheio → `EnderecoException`; status diferente de `PENDENDE` → `AgendamentoNaoEditavelException`;
    - `confirmarAgendamento(Long id, UUID chefeId)`: inexistente ou chef fora de `getChefes()` → `AgendamentoException`; status diferente de `PENDENDE` → `AgendamentoNaoEditavelException`.
  - `AgendamentoController`:
    - `POST /agendamentos`, sem `?email`;
    - `GET /agendamentos/meus`;
    - `PUT /agendamentos/{id}`;
    - `GET /agendamentos/recebidos`;
    - `PATCH /agendamentos/{id}/confirmar` → 200.

- [ ] **Step 1: Escrever os testes que falham:**
  - `AgendamentoServiceTest`:
    - troque `findByEmail(EMAIL)` por `findById(USUARIO_ID)` e `endercoRepository.findById(1L)` por `findByIdAndUsuario_Id(1L, USUARIO_ID)`;
    - `criarComEnderecoDeOutroUsuarioDa404`;
    - `atualizarAgendamentoDeOutroUsuarioDa404`;
    - `atualizarComEnderecoDeOutroUsuarioDa404`;
    - `confirmarPorChefForaDoAgendamentoDa404`;
    - `confirmarPorQualquerChefDoAgendamento`, com dois chefs: o segundo confirma → `CONFIRMADO`;
    - `confirmarJaConfirmadoDa409`.
  - `ValidacaoRequisicoesTest`: tire `.param("email", …)` dos POSTs de agendamento.
  - `AutorizacaoIntegrationTest`:
    - `clienteNaoAgendaComEnderecoDeOutro`: `POST /agendamentos` com o cookie de A e o `enderecoId` de B → 404;
    - `campoDeIdentidadeNoCorpoEhIgnorado`: o POST com `"email":"<emailDeB>"` e `"usuarioId":"<idDeB>"` → 201, e o agendamento gravado pertence a A;
    - `clienteNaoEditaAgendamentoDeOutro`: `PUT /agendamentos/{idDeB}` com o cookie de A → 404;
    - `meusListaSoOsDoCliente`;
    - `clienteNaoConfirma`: `PATCH /agendamentos/{id}/confirmar` com o cookie de cliente → 403;
    - `chefForaDoAgendamentoNaoConfirma`: com o cookie de Y, num agendamento só de X → 404;
    - `chefDoAgendamentoConfirma`: → 200 e status `CONFIRMADO`; uma segunda chamada → 409;
    - `recebidosListaSoOsDoChef`.
- [ ] **Step 2:** Rodar → FAIL.
- [ ] **Step 3:** Implementar. Escrever no README a tabela "Rotas por papel", copiada da seção *Rotas* do spec, e a lista de mudanças para o front.
- [ ] **Step 4:** Rodar a suíte completa com `clean test` → PASS. Rodar também `grep -rn "findByEmail" src/main/java --include=*.java`: o esperado é que só restem os usos do `LoginService`, que são o login e não a identificação do usuário logado.
- [ ] **Step 5:** Commit `feat: bookings scoped to owner client and assigned chefs`.
