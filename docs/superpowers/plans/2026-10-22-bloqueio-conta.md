# Bloqueio automático de conta e filtro de conteúdo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Um filtro de conteúdo (ofensa → `***`; ódio ou ameaça → bloqueio) e o bloqueio de conta que tira o autor do app na hora (sessões e login), com fila de moderação para confirmar ou reverter.

**Architecture:** `FiltroConteudo` (puro, sem banco) analisa um texto contra uma lista em YAML. `ModeracaoConteudoService` é o ponto que os recursos de texto usam: devolve o texto exibível ou bloqueia a conta (transação própria) e lança 403. O bloqueio é um campo nas contas (`bloqueado_em`) conferido pelo `SecurityFilter` a cada requisição e no login. A moderação confirma (banimento) ou reverte pela fila `bloqueio_conta`.

**Tech Stack:** Java 17, Spring Boot 4.0.5, Spring Security 7, Hibernate 7, Flyway (V7), JUnit 5, Mockito, MockMvc, H2, Testcontainers (Postgres 17).

**Spec:** `docs/superpowers/specs/2026-10-22-avaliacoes-design.md` (seções "Filtro de conteúdo", "Bloqueio de conta", "Nova conta depois do banimento"). Este é o plano 1 de 2; o plano 2 (avaliações) consome o `ModeracaoConteudoService`.

## Global Constraints

- Pacotes por camada: `domain`, `repository`, `service`, `controller`, `dto`, `exception`, `enums`, `security`, `config`. Nomes, mensagens e comentários em português.
- Migration desta etapa: **V7** (o plano 2 usa V8). Nenhuma migration aplicada é editada.
- Filtro: lista em `src/main/resources/moderacao/termos.yml`, categorias `ofensa`, `odio`, `ameaca`; comparação por palavra/expressão inteira, sem diferenciar maiúsculas nem acentos; `ofensa` vira `***`.
- Ódio/ameaça: nada é gravado, a conta do autor é bloqueada e a resposta é **403** com `"Conteúdo bloqueado e conta suspensa para análise da moderação"`.
- Conta bloqueada: o token deixa de autenticar (401 nas rotas protegidas) e o login com Google redireciona para `{front}/login?erro=conta-bloqueada`. Moderadores nunca consultam o banco.
- Moderação: `CONFIRMAR_BLOQUEIO` (banimento; chef também `ativo = false`), `REVERTER_BLOQUEIO` (desbloqueia), ambos na auditoria `acao_moderacao`. Notificação `CONTA_BLOQUEADA` para cada moderador: `"Conta bloqueada automaticamente ({CATEGORIA}) — revisar"`.
- Testes: `./mvnw -q clean test -Dspring.profiles.active=test` **com Docker ligado**; contar `tests/failures/errors/skipped` pelo nome nos `TEST-*.xml` (0 falhas, 0 erros, 0 pulados). Commits por caminho, mensagem terminando em `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Falso positivo por pedaço de palavra:** um termo da lista dentro de outra palavra (ex.: termo `mata` em "tomate", "matar a fome") não pode disparar nada. Teste na Task 2.
2. **Texto com o termo em maiúsculas, com acento diferente ou cercado de pontuação** (`"PALAVRÃO!"`, `"(palavrao)"`) é pego. Teste na Task 2.
3. **Requisição já em andamento quando a conta é bloqueada:** a próxima requisição da mesma sessão recebe 401, sem esperar o token expirar. Teste na Task 4.
4. **Reverter devolve tudo:** depois de `reverter`, login e ações voltam a funcionar (inclusive para chef, cujo `ativo` não foi mexido). Teste na Task 5.
5. **O bloqueio grava mesmo com a requisição falhando:** a resposta é 403 e a transação do recurso é desfeita, mas o bloqueio e a entrada na fila ficam. Teste na Task 3.

---

### Task 1: V7, entidades e enums do bloqueio

**Files:**
- Create: `src/main/resources/db/migration/V7__bloqueio_conta.sql`, `enums/CategoriaConteudo.java`, `enums/StatusBloqueio.java`, `domain/BloqueioConta.java`, `repository/BloqueioContaRepository.java`
- Modify: `domain/UserModel.java`, `domain/ChefeModel.java`, `enums/TipoAcaoModeracao.java`, `enums/EventoNotificacao.java`, `repository/UsuarioRepository.java`, `repository/ChefeRepository.java`
- Test: `MigracoesPostgresTest.java`

**Interfaces:**
- Produces:
  - `CategoriaConteudo { OFENSA, ODIO, AMEACA }`; `StatusBloqueio { ABERTO, CONFIRMADO, REVERTIDO }`.
  - `TipoAcaoModeracao` + `CONFIRMAR_BLOQUEIO`, `REVERTER_BLOQUEIO`; `EventoNotificacao` + `CONTA_BLOQUEADA`.
  - `UserModel` e `ChefeModel` + `LocalDateTime bloqueadoEm`, `String bloqueioMotivo` (colunas `bloqueado_em`, `bloqueio_motivo` varchar(255)).
  - `BloqueioConta` (tabela `bloqueio_conta`): `Long id` (identity), `UUID contaId`, `TipoUsuario contaTipo` (STRING), `CategoriaConteudo categoria` (STRING, só `ODIO`/`AMEACA` no CHECK), `String termo`, `String texto` (length 2000), `StatusBloqueio status` (default `ABERTO`), `LocalDateTime criadoEm`, `LocalDateTime resolvidoEm`, `String resolvidoPor`.
  - `BloqueioContaRepository`: `Page<BloqueioConta> findByStatusOrderByCriadoEmAscIdAsc(StatusBloqueio, Pageable)`, `void deleteByContaId(UUID contaId)`.
  - `UsuarioRepository.existsByIdAndBloqueadoEmIsNotNull(UUID)`, `ChefeRepository.existsByIdAndBloqueadoEmIsNotNull(UUID)`.

- [ ] **Step 1: Testes no Postgres real** (`MigracoesPostgresTest`): `checksDeEnumBatemComOsEnumsJava` ganha `bloqueio_conta.categoria` (só `ODIO`, `AMEACA`), `bloqueio_conta.status` (`StatusBloqueio`), `bloqueio_conta.conta_tipo` (`CLIENTE`, `CHEFE`); `acao_moderacao.acao` e `notificacao.evento` passam a exigir os valores novos.
- [ ] **Step 2:** `./mvnw -q test -Dspring.profiles.active=test -Dtest=MigracoesPostgresTest` → FAIL (tabela/valores não existem).
- [ ] **Step 3:** V7 (`SET search_path TO chefhome;`): colunas em `usuarios` e `chefe`; `bloqueio_conta` com os CHECKs e índice `(status, criado_em)`; drop + add de `acao_moderacao_acao_check` e `notificacao_evento_check` com todos os valores atuais + os novos. Entidades, enums e repositórios conforme Interfaces.
- [ ] **Step 4:** O teste → PASS; suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: account block columns and moderation queue table (V7)`.

---

### Task 2: Filtro de conteúdo

**Files:**
- Create: `service/FiltroConteudo.java`, `src/main/resources/moderacao/termos.yml`
- Test: `service/FiltroConteudoTest.java`

**Interfaces:**
- Consumes: `CategoriaConteudo` (Task 1).
- Produces:
  - `record ResultadoFiltro(String textoExibido, Optional<Violacao> violacao)` e `record Violacao(CategoriaConteudo categoria, String termo)` (aninhados em `FiltroConteudo`).
  - `FiltroConteudo(Map<CategoriaConteudo, List<String>> termos)` (construtor usado pelos testes) e um `@Bean`/construtor de produção que lê `classpath:moderacao/termos.yml` (`ofensa:`, `odio:`, `ameaca:` como listas).
  - `ResultadoFiltro analisar(String texto)`: texto nulo/branco → `ResultadoFiltro(texto, empty)`. Ódio tem prioridade sobre ameaça, ameaça sobre ofensa; havendo violação de ódio/ameaça, `textoExibido` é irrelevante (pode ser o original).
- Algoritmo (não determinado pela assinatura): normalizar texto e termos com `Normalizer.Form.NFD` removendo marcas (`\p{M}`) e em minúsculas; quebrar o texto original em palavras (`[\p{L}\p{N}]+`) guardando as posições; um termo de várias palavras casa com uma sequência consecutiva de palavras normalizadas iguais; ofensa substitui o trecho original casado por `***`.
- `termos.yml` de produção: uma lista inicial de termos comuns em português para cada categoria, revisável pelo dono do produto; os testes **não** dependem dela (usam o construtor com termos fictícios).

- [ ] **Step 1: Testes** (`FiltroConteudoTest`, termos fictícios: ofensa `palavrao`, `bobo alegre`; ódio `odiento`; ameaça `vou te pegar`, `mata`):
  - `ofensaViraAsteriscos`: `"Que palavrao feio"` → `"Que *** feio"`, sem violação;
  - `ignoraMaiusculasAcentosEPontuacao`: `"PALAVRÃO!"` → `"***!"`; `"(Palavrao)"` → `"(***)"` (Review Focus 2);
  - `naoPegaPedacoDePalavra`: `"tomate e matar a fome"` com termo `mata` → sem violação, texto intacto (Review Focus 1);
  - `expressaoDeVariasPalavras`: `"ele é bobo alegre"` → `"ele é ***"`; `"Vou   te pegar"` → violação `AMEACA` / `vou te pegar`;
  - `odioTemPrioridade`: texto com `odiento` e `palavrao` → violação `ODIO`;
  - `nuloOuBranco` → sem violação, devolve o mesmo valor.
- [ ] **Step 2:** `-Dtest=FiltroConteudoTest` → FAIL. **Step 3:** Implementar. **Step 4:** PASS; suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: content filter (offensive words masked; hate and threats detected)`.

---

### Task 3: Moderação de conteúdo e bloqueio

**Files:**
- Create: `service/ModeracaoConteudoService.java`, `service/BloqueioContaService.java`, `exception/ConteudoBloqueadoException.java`
- Test: `service/ModeracaoConteudoServiceIntegrationTest.java`

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces:
  - `ConteudoBloqueadoException extends NegocioException` (403, mensagem exata da Global Constraints).
  - `BloqueioContaService.bloquear(UUID contaId, TipoUsuario contaTipo, Violacao violacao, String texto)` com `@Transactional(propagation = REQUIRES_NEW)`: grava `bloqueadoEm = now(clock)` e `bloqueioMotivo = categoria` na conta, cria `BloqueioConta` `ABERTO`, notifica cada moderador (`CONTA_BLOQUEADA`, `referenciaId` = id do bloqueio).
  - `ModeracaoConteudoService.verificar(String texto, UsuarioAutenticado autor) : String` — devolve o texto exibível (com `***`); em ódio/ameaça chama `bloquear(...)` e lança `ConteudoBloqueadoException`.

- [ ] **Step 1: Testes** (`@SpringBootTest`, `@MockitoBean FiltroConteudo` ou um filtro de teste com termos fictícios via `@TestConfiguration`):
  - `ofensaSoMascara`: devolve o texto com `***`, conta não bloqueada, nenhuma linha em `bloqueio_conta`;
  - `odioBloqueiaMesmoComARequisicaoFalhando`: chamar `verificar` dentro de um `TransactionTemplate` que depois lança → `ConteudoBloqueadoException`; após o rollback, a conta tem `bloqueadoEm` e há um `BloqueioConta ABERTO` com o termo e o texto (Review Focus 5);
  - `moderadoresSaoAvisados`: notificação `CONTA_BLOQUEADA` com `"Conta bloqueada automaticamente (ODIO) — revisar"`;
  - `chefTambemEhBloqueado` (`contaTipo = CHEFE`).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS; suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: blocking hate or threats suspends the author's account`.

---

### Task 4: Conta bloqueada não autentica nem loga

**Files:**
- Create: `exception/ContaBloqueadaException.java`
- Modify: `security/SecurityFilter.java`, `security/LoginService.java`, `security/bff/GoogleLoginSuccessHandler.java`
- Test: `security/ContaBloqueadaIntegrationTest.java`, `security/SecurityFilterTest.java`, `security/LoginServiceTest.java`

**Interfaces:**
- Consumes: Task 1 (`existsByIdAndBloqueadoEmIsNotNull`).
- Produces:
  - `LoginService.contaBloqueada(UsuarioAutenticado usuario) : boolean` — `CLIENTE`/`CHEFE` consultam o repositório; `MODERADOR` → `false` sem banco.
  - `SecurityFilter` só autentica se `!loginService.contaBloqueada(usuario)` (além da regra atual de moderador).
  - `LoginService.loginGoogle`: conta encontrada e bloqueada → lança `ContaBloqueadaException`; o `GoogleLoginSuccessHandler` a trata como a `ContaInativaException`, mas redirecionando para `frontUrl + "/login?erro=conta-bloqueada"`.

- [ ] **Step 1: Testes:**
  - `ContaBloqueadaIntegrationTest.sessaoAbertaCaiNaProximaRequisicao`: cliente com cookie válido faz `GET /usuario/me` → 200; marcar `bloqueadoEm`; mesma chamada com o mesmo cookie → 401 (Review Focus 3); idem para chef em `GET /chefe/me`;
  - `moderadorNaoConsultaOBanco` (`SecurityFilterTest`: `loginService.contaBloqueada` não é chamado com o mock de moderador, ou devolve `false` sem repositório);
  - `LoginServiceTest.contaBloqueadaNaoLoga` → `ContaBloqueadaException`; teste do handler: redirect `.../login?erro=conta-bloqueada`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS; suíte completa → PASS.
- [ ] **Step 5:** Commit `feat: blocked accounts lose their sessions and cannot log in`.

---

### Task 5: Fila de bloqueios na moderação

**Files:**
- Create: `dto/BloqueioContaDto.java`
- Modify: `service/ModeracaoService.java`, `controller/ModeracaoController.java`, `service/UsuarioService.java`, `README.md`
- Test: `service/ModeracaoServiceTest.java`, `security/ContaBloqueadaIntegrationTest.java`

**Interfaces:**
- Consumes: Tasks 1, 3, 4.
- Produces:
  - `record BloqueioContaDto(Long id, UUID contaId, TipoUsuario contaTipo, String nome, CategoriaConteudo categoria, String termo, String texto, StatusBloqueio status, LocalDateTime criadoEm)`.
  - `ModeracaoService.listarBloqueios(StatusBloqueio, Pageable) : Page<BloqueioContaDto>`, `confirmarBloqueio(Long id, String moderadorEmail)`, `reverterBloqueio(Long id, String moderadorEmail)` — só `ABERTO` (senão 409); confirmar: status `CONFIRMADO` e, se chef, `ativo = false`; reverter: `bloqueadoEm`/`bloqueioMotivo` nulos e status `REVERTIDO`; ambos gravam `resolvidoEm`/`resolvidoPor` e auditoria (`chefeId` = conta se chef, `denunciaId` = id do bloqueio).
  - Rotas: `GET /moderacao/bloqueios?status=ABERTO`, `PATCH /moderacao/bloqueios/{id}/confirmar`, `PATCH /moderacao/bloqueios/{id}/reverter` (já cobertas por `/moderacao/**`).
  - `UsuarioService.deletar` também apaga `bloqueio_conta` do cliente (`deleteByContaId`).

- [ ] **Step 1: Testes:**
  - `ModeracaoServiceTest`: confirmar chef → `ativo=false` + auditoria `CONFIRMAR_BLOQUEIO`; reverter → desbloqueado + `REVERTER_BLOQUEIO`; resolvido → 409 sem auditoria;
  - `ContaBloqueadaIntegrationTest.reverterDevolveLoginEAcoes`: chef bloqueado → 401; moderador reverte → mesmo cookie volta a 200 em `GET /chefe/me` e o login não lança (Review Focus 4);
  - `filaSoParaModeracao`: cliente/chef em `/moderacao/bloqueios` → 404.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar; README: rotas na tabela da moderação e um parágrafo sobre bloqueio automático.
- [ ] **Step 4:** Suíte completa → PASS, também com `-Dsurefire.runOrder=alphabetical`.
- [ ] **Step 5:** Commit `feat: moderation queue confirms or reverts automatic blocks`.
