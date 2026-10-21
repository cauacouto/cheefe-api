# Onboarding mínimo, perfil de chef incompleto e foto de perfil Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Onboarding que pede só nome e tipo (cliente também informa endereço), chef entrando com o perfil incompleto e completando depois, e troca da foto de perfil.

**Architecture:** "Perfil completo" é calculado (`ChefeModel.isPerfilCompleto()` + `ChefeSpecification.perfilCompleto()`). Não existe coluna nova. A visibilidade exige que o chef esteja ativo, disponível e completo. A foto reaproveita `ImagemStorageService` com `PastaImagem.PERFIL`.

**Tech Stack:** Java 17, Spring Boot 4.0.5, Spring Security 7, JPA (H2 nos testes), JUnit 5 + Mockito, MockMvc.

**Spec:** `docs/superpowers/specs/2026-10-21-onboarding-chef-incompleto-design.md` (v2)

## Global Constraints

- Campos de perfil completo, nesta ordem exata (é também a ordem de `camposPendentes`): `nacionalidade`, `especialidade`, `senioridade`, `anosDeExperiencia`, `idiomas`, `valorHora`. Para `nacionalidade` e `idiomas`, conta como preenchido só se não ficar em branco depois de `trim`.
- `anosDeExperiencia` vira `Integer` em `ChefeModel` e `ChefeResponseDto`: nulo significa não informado, e `0` é válido.
- Um chef visível e agendável precisa estar `ativo`, `disponivel` e com perfil completo.
- Cliente sem endereço no onboarding → 400 `"Informe ao menos um endereço"`. Agendar chef incompleto → 400 `"chef com perfil incompleto"`.
- A foto vai para a pasta `PastaImagem.PERFIL` e a resposta é `{ "imageUrl": "..." }`. Chef apagado → 403 (`ChefeModel.exigirAtivo()`).
- Testes: `./mvnw -q clean test -Dspring.profiles.active=test`. Para contar, some `tests=` dos `target/surefire-reports/TEST-*.xml`. A suíte inteira precisa estar verde ao fim de cada task.
- Testes de integração que gravam no H2 limpam o banco num `@AfterEach`, na ordem das chaves estrangeiras.
- **Não commitar** `src/main/resources/application-prod.yml` nem `application.example.yml`, que são do usuário. Commit sempre por caminho: `git commit -m ... -- <arquivos>`.
- Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Chef completa o perfil mandando `"idiomas":"   "`**: continua incompleto e fora da busca. Testes nas Tasks 1 e 2.
2. **Chef antigo gravado com `anosDeExperiencia=0`** (o default do `int` de antes): conta como informado e não passa a ser tratado como incompleto. Teste na Task 1.
3. **Chef completo, mas pausado**: continua fora da busca e dos destaques. Teste na Task 2.
4. **Cliente com `endereco` contendo um apartamento sem complemento**: 400, e nada é gravado (nem conta, nem endereço). Teste na Task 3.
5. **Troca de foto com arquivo inválido**: 400, sem chamar o storage, e a `imageUrl` anterior fica como estava. Teste na Task 5.

---

### Task 1: Perfil completo no `ChefeModel`

**Files:**
- Modify: `domin/ChefeModel.java` (remove `@NotBlank` de `nacionalidade`/`idiomas` e `@NotNull` de `anosDeExperiencia`; `anosDeExperiencia` passa a `Integer`), `Dtos/ChefeResponseDto.java` (`Integer anosDeExperiencia`, inclusive no construtor a partir da entidade)
- Create test: `src/test/java/com/couto/chefe_api/domin/ChefeModelTest.java`

**Interfaces:**
- Produces:
  - `boolean ChefeModel.isPerfilCompleto()`
  - `List<String> ChefeModel.camposPendentes()`, com os nomes exatos da Global Constraints e só os que faltam

- [ ] **Step 1: Escrever os testes que falham:**
  - `completoComTodosOsCampos`
  - `incompletoSemCadaCampo`, parametrizado nos 6 campos: o campo ausente deixa o perfil incompleto, e `camposPendentes()` contém exatamente ele
  - `idiomasEmBrancoContaComoPendente` (`"   "`)
  - `zeroAnosDeExperienciaContaComoInformado`
  - `pendentesNaOrdemDoContrato`: um chef vazio → os 6 campos, na ordem
- [ ] **Step 2:** Rodar `-Dtest=ChefeModelTest` → FAIL (os métodos não existem).
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar a suíte completa → PASS. Se algum teste montar `ChefeResponseDto` com `int`, ajustar.
- [ ] **Step 5:** Commit `feat: computed chef profile completeness`.

---

### Task 2: Visibilidade e agendamento exigem perfil completo

**Files:**
- Modify: `Chefe/ChefeSpecification.java` (`perfilCompleto()`), `Services/ChefeService.java` (`mostrarChefes`: `ativo().and(disponivel()).and(perfilCompleto())`), `repositorys/ChefeRepository.java` (as duas queries de `findMaisAgendados` com os 6 campos), `Services/agendamentoService.java` (`validarChefes`)
- Test: `Services/ChefeServiceIntegrationTest.java`, `Services/AgendamentoServiceTest.java`

**Interfaces:**
- Consumes: `ChefeModel.isPerfilCompleto()` (Task 1).
- Produces: `ChefeSpecification.perfilCompleto()`. `validarChefes` passa a checar nesta ordem:
  1. ids inexistentes → 404;
  2. inativo ou indisponível → 409, como hoje;
  3. incompleto → `AgendamentoInvalidoException("chef com perfil incompleto")`.

- [ ] **Step 1: Ajustar os helpers de chef dos testes para criar chefs COMPLETOS.** Todos passam a preencher `especialidade=BRASILEIRA`, `senioridade=HEAD_CHEF` e `anosDeExperiencia=5`: `ChefeServiceIntegrationTest.chefe`, `AgendamentoServiceTest.chefe`, `AutorizacaoIntegrationTest.novoChefe` e `PausaChefIntegrationTest.novoChefe`. Sem isso eles somem da busca. Depois, escrever os testes que falham:
  - `ChefeServiceIntegrationTest.chefIncompletoNaoApareceNaBuscaNemNosDestaques`
  - `ChefeServiceIntegrationTest.chefComIdiomasEmBrancoNaoAparece`
  - `ChefeServiceIntegrationTest.chefCompletoMasPausadoNaoAparece`
  - `AgendamentoServiceTest.agendarChefIncompletoDa400`: `AgendamentoInvalidoException` com a mensagem `"chef com perfil incompleto"`, e o `save` não é chamado
- [ ] **Step 2:** Rodar `-Dtest='ChefeServiceIntegrationTest,AgendamentoServiceTest'` → FAIL.
- [ ] **Step 3:** Implementar. Na query JPQL de `findMaisAgendados`, os textos usam `TRIM(c.nacionalidade) <> ''` e `TRIM(c.idiomas) <> ''`, além de `IS NOT NULL` nos seis campos, e o `countQuery` tem a mesma regra.
- [ ] **Step 4:** Rodar a suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: hide and refuse booking for chefs with incomplete profile`.

---

### Task 3: Onboarding: chef só com nome e cliente com endereço obrigatório

**Files:**
- Modify: `Dtos/RegisterRequestDto.java` (`List<@Valid EnderecoRequestDto> endereco`), `Security/LoginService.java` (`register`)
- Test: `Security/LoginServiceTest.java`, `Security/OnboardingIntegrationTest.java` (novo; `@SpringBootTest`, MockMvc com `springSecurity()`, cookie `registroToken` gerado por `TokenService.gerarTokenRegistro`, limpeza do banco no `@AfterEach`)

**Interfaces:**
- Produces: `LoginService.register` lança `RegraDeNegocioException("Informe ao menos um endereço")` para `CLIENTE` com `endereco` nulo ou vazio, antes de qualquer `save`.

- [ ] **Step 1: Escrever os testes que falham:**
  - `LoginServiceTest.clienteSemEnderecoDa400`, com `null` e com `List.of()`: `RegraDeNegocioException`, e o `save` nunca é chamado;
  - `LoginServiceTest.registerClienteSemEnderecoNaoQuebra` **muda de sentido**: renomeie para `clienteSemEnderecoDa400` e ajuste os outros testes de cliente para mandarem um endereço válido;
  - `OnboardingIntegrationTest.chefSoComNomeEhCriado`: `POST /auth/register` com `{"tipoUsuario":"CHEFE","nome":"Bia"}` → 201, e o chef gravado tem `isPerfilCompleto()=false`;
  - `OnboardingIntegrationTest.clienteSemEnderecoDa400` e `clienteComEnderecoVazioDa400`;
  - `OnboardingIntegrationTest.apartamentoSemComplementoDa400SemGravarNada`: → 400, e não existe conta com o e-mail do token;
  - `OnboardingIntegrationTest.clienteComEnderecoValidoEhCriado`: → 201, com 1 endereço gravado.
- [ ] **Step 2:** Rodar → FAIL. O teste do chef só com nome tende a passar já depois da Task 1, e tudo bem: ele vira teste de regressão.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar a suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: minimal onboarding with required address for clients`.

---

### Task 4: `GET /chefe/me`

**Files:**
- Create: `Dtos/ChefePerfilDto.java`, um `record` com os campos de `ChefeResponseDto` mais `boolean perfilCompleto` e `List<String> camposPendentes`
- Modify: `Services/ChefeService.java` (`ChefePerfilDto buscarPerfil(UUID chefeId)`, inexistente → `ChefeNaoEncontradoException`), `Controller/ChefeController.java` (`GET /chefe/me`), `Security/SecurityConfiguration.java` (`GET /chefe/me` → `hasRole(CHEFE)`, antes do matcher genérico)
- Test: `Security/OnboardingIntegrationTest.java`

**Interfaces:**
- Consumes: `isPerfilCompleto()` e `camposPendentes()` (Task 1).

- [ ] **Step 1: Escrever os testes que falham:**
  - `chefRecemCriadoVeCamposPendentes`: `GET /chefe/me` → 200, `$.perfilCompleto=false`, e `$.camposPendentes` é igual aos 6 campos na ordem;
  - `completarPerfilViaPatchTornaVisivel`: um `PATCH /chefe/me` com os 6 campos → `GET /chefe/me` com `perfilCompleto=true` e `camposPendentes=[]`, e o chef aparece em `GET /chefe`;
  - `clienteEmChefeMeDa403`.
- [ ] **Step 2:** Rodar → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar a suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: chef can read own profile with pending fields`.

---

### Task 5: Foto de perfil e o README

**Files:**
- Create: `Services/FotoPerfilService.java`, `Dtos/FotoPerfilDto.java` (`record(String imageUrl)`)
- Modify: `Controller/UsuarioController.java` (`PUT /usuario/me/foto`, multipart `foto`), `Controller/ChefeController.java` (`PUT /chefe/me/foto`), `Security/SecurityConfiguration.java` (`PUT /chefe/me/foto` → `hasRole(CHEFE)`), `README.md` (as rotas novas na tabela "Rotas por papel" e o `ALTER TABLE` do spec, na seção de configuração)
- Test: `Services/FotoPerfilServiceTest.java` (Mockito), `Security/FotoPerfilIntegrationTest.java` (`@SpringBootTest` com `@MockitoBean ImagemStorageService`)

**Interfaces:**
- Produces:
  - `FotoPerfilService.trocarFotoUsuario(UUID usuarioId, MultipartFile foto)` → `String`: usuário inexistente → `UsuarioException`;
  - `FotoPerfilService.trocarFotoChefe(UUID chefeId, MultipartFile foto)` → `String`: chef inexistente → `ChefeNaoEncontradoException`; `exigirAtivo()` vem **antes** do upload.

- [ ] **Step 1: Escrever os testes que falham:**
  - `FotoPerfilServiceTest.trocaFotoDoUsuarioGravaUrl`: o storage devolve `"https://cdn/perfil/x.png"` → a conta grava essa URL, e o storage foi chamado com `PastaImagem.PERFIL`;
  - `FotoPerfilServiceTest.fotoInvalidaMantemAnterior`: o storage lança `ImagemInvalidaException` → a exceção sobe e o `imageUrl` continua o anterior;
  - `FotoPerfilServiceTest.chefApagadoNaoTrocaFoto`: → `ContaInativaException`, e o storage não é chamado;
  - `FotoPerfilIntegrationTest.clienteTrocaFoto`: `PUT /usuario/me/foto` (multipart, com o mock devolvendo uma URL) → 200 e `$.imageUrl` igual;
  - `FotoPerfilIntegrationTest.clienteEmFotoDeChefDa403`.
- [ ] **Step 2:** Rodar → FAIL.
- [ ] **Step 3:** Implementar e atualizar o README.
- [ ] **Step 4:** Rodar a suíte completa com `clean test` → PASS.
- [ ] **Step 5:** Commit `feat: profile photo upload for clients and chefs`.
