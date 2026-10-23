# Avaliações (cliente ↔ chef) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cliente avalia cada chef e cada chef avalia o cliente depois de um agendamento concluído (estrelas + tags + comentário), com média anônima nas listas, comentários públicos sobre chefs, resposta e denúncia, e a reputação do cliente visível só a chefs.

**Architecture:** Tabela `avaliacao` (+ `avaliacao_tag`, `denuncia_avaliacao`) e contadores `soma_notas`/`total_avaliacoes` nas contas, atualizados na mesma transação com trava no avaliado. Todo texto passa pelo `ModeracaoConteudoService` do plano 1. Comentário com nome fica sem `publicado_em` até o chef avaliar o cliente ou a rotina publicar depois de 7 dias.

**Tech Stack:** Java 17, Spring Boot 4.0.5, Spring Data JPA (Specification), Hibernate 7, Flyway (V8), JUnit 5, Mockito, MockMvc, H2, Testcontainers (Postgres 17).

**Spec:** `docs/superpowers/specs/2026-10-22-avaliacoes-design.md`. Plano 2 de 2; depende do plano 1 (`docs/superpowers/plans/2026-10-22-bloqueio-conta.md`) já mesclado no branch.

## Global Constraints

- Pacotes por camada (`domain`, `repository`, `service`, `controller`, `dto`, `exception`, `enums`, `security`); português.
- Migration desta etapa: **V8**.
- Só agendamento `CONCLUIDO`, até `concluido_em + 7 dias` (inclusive); sem `concluido_em` → 409. Uma avaliação por (agendamento, autor, avaliado) → repetida 409. Não participa → 404.
- Nota 1–5; até 5 tags, só da lista do lado certo; comentário e resposta até 1000 caracteres → senão 400. Sem edição depois de enviar.
- `notaMedia` = `soma_notas / total_avaliacoes`, uma casa decimal (HALF_UP), **nula se `total_avaliacoes < 5`**. A nota conta na média ao ser criada.
- Lista pública: sem id de autor nem de agendamento; autor `"Cliente verificado"` (anônimo, padrão) ou o primeiro nome; quando = `"{mês} de {ano}"` em português (ex.: `"outubro de 2026"`) do `publicado_em`; ordem `publicado_em` desc; comentário removido → `"Comentário removido pela moderação"`.
- Comentário com nome: `publicado_em` nulo até o chef avaliar o cliente daquele agendamento ou a rotina publicar após os 7 dias.
- Comentário sobre cliente: nunca aparece fora da moderação. Reputação do cliente: só chef com algum agendamento com ele.
- Notificações: `AVALIACAO_RECEBIDA` → avaliado, `"Você recebeu uma nova avaliação"`; `AVALIACAO_RESPONDIDA` → cliente autor, `"Chef {nome} respondeu sua avaliação"`; `AVALIACAO_DENUNCIADA` → moderadores, `"Nova denúncia de avaliação ({MOTIVO})"`.
- Tags, rótulos e motivos de denúncia: exatamente os do spec (`TagAvaliacaoChef`, `TagAvaliacaoCliente`; `MotivoDenunciaAvaliacao` = `OFENSIVA, DADOS_PESSOAIS, FALSA, SPAM, OUTRO`).
- Testes com Docker ligado; contagem por nome nos `TEST-*.xml` (0/0/0). Commits por caminho com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Anonimato pela ordem ou pelo tempo:** a lista pública ordena por `publicado_em`, e uma avaliação anônima publicada logo depois de o chef concluir não revela o cliente mais do que o mês/ano. Nenhum campo de id. Teste na Task 3.
2. **Cliente que avalia um chef que saiu do agendamento** (recusou/desistiu antes da conclusão) → 404, e ele não aparece em `/pendentes`. Teste na Task 2.
3. **Duas avaliações ao mesmo tempo para o mesmo chef** (dois clientes) → `total_avaliacoes` aumenta 2. Teste na Task 2 (Postgres).
4. **Apagar a conta de um cliente que avaliou** → contadores do chef voltam ao valor sem a nota dele, e a reputação do cliente some. Teste na Task 5.
5. **Comentário com ódio/ameaça na avaliação** → 403, nenhuma avaliação gravada, contador intacto, conta bloqueada (plano 1). Teste na Task 2.

---

### Task 1: V8, entidades e enums

**Files:**
- Create: `src/main/resources/db/migration/V8__avaliacoes.sql`, `enums/TagAvaliacaoChef.java`, `enums/TagAvaliacaoCliente.java`, `enums/MotivoDenunciaAvaliacao.java`, `domain/Avaliacao.java`, `domain/DenunciaAvaliacao.java`, `repository/AvaliacaoRepository.java`, `repository/DenunciaAvaliacaoRepository.java`
- Modify: `domain/Agendamento.java` (`concluidoEm`, gravado em `concluirPeloChef` e `concluirPeloSistema`), `domain/UserModel.java` e `domain/ChefeModel.java` (`int somaNotas`, `int totalAvaliacoes`, default 0), `enums/TipoAcaoModeracao.java` (+ `ARQUIVAR_DENUNCIA_AVALIACAO`, `REMOVER_COMENTARIO_AVALIACAO`), `enums/EventoNotificacao.java` (+ os três da Global Constraints)
- Test: `MigracoesPostgresTest.java`, `domain/AgendamentoTransicoesTest.java`

**Interfaces:**
- Produces:
  - `TagAvaliacaoChef` / `TagAvaliacaoCliente` com `String rotulo()` (valores do spec); `MotivoDenunciaAvaliacao`.
  - `Avaliacao` (tabela `avaliacao`, colunas do spec "Dados"): `Long id`, `Agendamento agendamento` (`@OnDelete CASCADE`), `UUID autorId`, `TipoUsuario autorTipo`, `UUID avaliadoId`, `TipoUsuario avaliadoTipo`, `int nota`, `String comentario`, `String comentarioOriginal`, `boolean anonimo` (default true), `LocalDateTime publicadoEm`, `boolean comentarioRemovido`, `String resposta`, `String respostaOriginal`, `LocalDateTime respondidaEm`, `LocalDateTime criadaEm`, `@ElementCollection Set<String> tags` (tabela `avaliacao_tag`, colunas `avaliacao_id`, `tag`).
  - `DenunciaAvaliacao` (tabela `denuncia_avaliacao`), espelho de `DenunciaMensagem` com `avaliacao`.
  - `AvaliacaoRepository`: `boolean existsByAgendamento_IdAndAutorIdAndAvaliadoId(Long, UUID, UUID)`, `Page<Avaliacao> findByAvaliadoIdAndAvaliadoTipoAndPublicadoEmIsNotNullOrderByPublicadoEmDescIdDesc(UUID, TipoUsuario, Pageable)`, `List<Avaliacao> findByAutorIdOrAvaliadoId(UUID, UUID)`, `List<Long> idsComNomeParaPublicar(LocalDateTime limite)` (`anonimo = false AND publicado_em IS NULL AND agendamento.concluidoEm <= :limite`), contagem de tags por avaliado (`SELECT t, COUNT(a) ... GROUP BY t`).

- [ ] **Step 1: Testes:** Postgres — CHECKs de `avaliacao.nota` (1–5), `autor_tipo`/`avaliado_tipo`, `avaliacao_tag.tag` (união das duas listas), `denuncia_avaliacao.motivo`/`status`, `acao_moderacao.acao`, `notificacao.evento`; cascata a partir do agendamento. Unidade — `concluirPeloChef`/`concluirPeloSistema` gravam `concluidoEm = agora`.
- [ ] **Step 2:** FAIL. **Step 3:** V8 (`SET search_path TO chefhome;`, CHECKs drop+add com todos os valores, unique, índice `(avaliado_id, publicado_em)`, FKs cascade) + código. **Step 4:** PASS; suíte → PASS.
- [ ] **Step 5:** Commit `feat: reviews tables, counters and tags (V8)`.

---

### Task 2: Avaliar

**Files:**
- Create: `dto/AvaliarRequestDto.java`, `dto/AvaliacaoPendenteDto.java`, `service/AvaliacaoService.java`, `controller/AvaliacaoController.java`, `exception/AvaliacaoForaDoPrazoException.java`
- Modify: `repository/ChefeRepository.java` e `repository/UsuarioRepository.java` (`@Lock(PESSIMISTIC_WRITE) travarPorId(UUID)`), `security/SecurityConfiguration.java`
- Test: `domain/AvaliacaoRegrasTest.java` ou `service/AvaliacaoServiceTest.java`, `security/AvaliacaoIntegrationTest.java`, `AvaliacaoConcorrenciaPostgresTest.java`

**Interfaces:**
- Consumes: Task 1; `ModeracaoConteudoService.verificar(String, UsuarioAutenticado)` (plano 1).
- Produces:
  - `record AvaliarRequestDto(UUID chefeId, @NotNull @Min(1) @Max(5) Integer nota, @Size(max = 5) List<String> tags, @Size(max = 1000) String comentario, Boolean anonimo)`.
  - `AvaliacaoService.avaliar(Long agendamentoId, UsuarioAutenticado autor, AvaliarRequestDto dto) : Long` — cliente: `chefeId` obrigatório e precisa estar em `agendamento.chefes`; chef: avalia o cliente (`chefeId` ignorado); valida tags contra a lista do lado; texto via `verificar`; trava o avaliado e soma no contador; `publicadoEm = agora` se anônimo (ou sem comentário), nulo se com nome; se o autor é chef, publica na mesma transação o comentário com nome que o cliente deixou sobre ele nesse agendamento; notifica `AVALIACAO_RECEBIDA`.
  - `AvaliacaoService.pendentes(Long agendamentoId, UsuarioAutenticado) : List<AvaliacaoPendenteDto>` com `record AvaliacaoPendenteDto(UUID id, String nome, TipoUsuario tipo, LocalDateTime prazo)`.
  - Rotas: `POST /agendamentos/{id}/avaliacoes` → 201 `{ "id": n }`; `GET /agendamentos/{id}/avaliacoes/pendentes`; ambas `hasAnyRole(CLIENTE, CHEFE)`.
- Prazo/status: `AvaliacaoForaDoPrazoException extends ConflitoException` (`"O prazo para avaliar (7 dias depois da conclusão) já passou"` / `"Só é possível avaliar agendamentos concluídos"`).

- [ ] **Step 1: Testes** (Clock fixo nos de unidade; agendamentos com `concluidoEm` controlado nos de integração):
  - prazo: exatamente 7 dias → passa; 7 dias e 1 minuto → 409; sem `concluidoEm` → 409; status `CONFIRMADO` → 409;
  - nota 0/6 → 400; tag do outro lado → 400; 6 tags → 400; repetida → 409;
  - `clienteNaoAvaliaChefQueSaiu` → 404 e ausente de `/pendentes` (Review Focus 2); terceiro → 404;
  - contadores: avaliar soma `nota` em `somaNotas` e 1 em `totalAvaliacoes` do avaliado;
  - publicação: anônimo → `publicadoEm` preenchido; com nome → nulo; chef avalia o cliente → o comentário com nome do cliente sobre ele é publicado;
  - `odioNaAvaliacaoBloqueiaENaoGrava` → 403, nenhuma `Avaliacao`, contador intacto, autor bloqueado (Review Focus 5);
  - notificação `AVALIACAO_RECEBIDA` sem nota/autor no texto;
  - Postgres: dois clientes avaliando o mesmo chef em paralelo → `totalAvaliacoes` = 2 (Review Focus 3).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS; suíte → PASS.
- [ ] **Step 5:** Commit `feat: clients and chefs rate each other after a completed booking`.

---

### Task 3: Reputação e lista pública

**Files:**
- Create: `dto/AvaliacaoPublicaDto.java`, `dto/ReputacaoDto.java`
- Modify: `service/AvaliacaoService.java`, `controller/AvaliacaoController.java`, `dto/ChefeResponseDto.java`, `dto/ChefePerfilDto.java`, `mapper/ChefeMapper.java`, `dto/ListaAgendamentoChefeDto.java`, `mapper/AgendamentoMapper.java`, `service/ChefeService.java` (sort `notaMedia`), `repository/ChefeSpecification.java`, `security/SecurityConfiguration.java`
- Test: `security/AvaliacaoIntegrationTest.java`, `security/ListagensIntegrationTest.java`

**Interfaces:**
- Produces:
  - `static BigDecimal Reputacao.media(int soma, int total)` (ou método nas entidades) → nulo se `total < 5`, senão `soma/total` com 1 casa HALF_UP.
  - `record AvaliacaoPublicaDto(Long id, int nota, List<Tag> tags, String comentario, String autor, String quando, String resposta)` com `record Tag(String codigo, String rotulo)`; `id` é o da avaliação (para responder/denunciar), nunca o do autor.
  - `record ReputacaoDto(BigDecimal notaMedia, int totalAvaliacoes, List<TagContagem> tags)` com `record TagContagem(String codigo, String rotulo, long total)`.
  - Rotas: `GET /chefe/{id}/avaliacoes?page=&size=` (até 50), `GET /chefe/{id}/reputacao` (qualquer logado); `GET /clientes/{id}/reputacao` (CHEFE com agendamento com o cliente, senão 404).
  - `ChefeResponseDto`/`ChefePerfilDto` + `BigDecimal notaMedia`, `int totalAvaliacoes`; `ListaAgendamentoChefeDto` + `clienteNotaMedia`, `clienteTotalAvaliacoes`.
  - Busca: `notaMedia` entra em `ORDENAVEIS`; quando pedido, a ordenação vira uma `Specification` com `ORDER BY CASE WHEN total_avaliacoes >= 5 THEN soma_notas * 1.0 / total_avaliacoes END DESC NULLS LAST` (e sai do `Sort`).

- [ ] **Step 1: Testes:** lista pública com `"Cliente verificado"`, primeiro nome quando `anonimo=false` e publicado, sem comentário com nome pendente, `"Comentário removido pela moderação"`, `quando = "outubro de 2026"`, sem campo de id de autor no JSON (`$.content[0].autorId` não existe) e ordem por `publicadoEm` (Review Focus 1); comentário sobre cliente nunca em rota pública; reputação com média nula abaixo de 5 e `4.4` com notas 5,5,4,4,4; contagem de tags; `/clientes/{id}/reputacao` → 404 para chef sem agendamento com o cliente; busca `sort=notaMedia` com "Novo" por último; recebidos com a média do cliente.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS; suíte → PASS.
- [ ] **Step 5:** Commit `feat: chef reputation in lists, public reviews and client reputation for chefs`.

---

### Task 4: Resposta do chef, denúncia e moderação

**Files:**
- Create: `dto/RespostaAvaliacaoDto.java`, `dto/DenunciaAvaliacaoRequestDto.java`, `dto/DenunciaAvaliacaoModeracaoDto.java`, `dto/ComentarioClienteModeracaoDto.java`
- Modify: `service/AvaliacaoService.java`, `controller/AvaliacaoController.java`, `service/ModeracaoService.java`, `controller/ModeracaoController.java`, `security/SecurityConfiguration.java`
- Test: `security/AvaliacaoIntegrationTest.java`, `service/ModeracaoServiceTest.java`

**Interfaces:**
- Produces:
  - `PUT /avaliacoes/{id}/resposta` `{ texto (≤1000) }` → 200: só o chef avaliado, só avaliação publicada com comentário não removido (senão 404 para outro chef, 409 para o resto); texto via `verificar`; substitui a anterior; notifica `AVALIACAO_RESPONDIDA` (só na primeira resposta).
  - `POST /avaliacoes/{id}/denuncias` `{ motivo, descricao? }` → 201 `DenunciaCriadaDto`: só o chef avaliado; repetida → 409; notifica `AVALIACAO_DENUNCIADA`.
  - Moderação: `GET /moderacao/denuncias-avaliacoes?status=` (com `comentarioOriginal`), `PATCH /moderacao/denuncias-avaliacoes/{id}/arquivar`, `PATCH .../{id}/remover-comentario` (`comentarioRemovido = true`, denúncias abertas da avaliação → `PROCEDENTE`), `GET /moderacao/comentarios-de-clientes?page=` (avaliações com `avaliadoTipo = CLIENTE` e comentário). Auditoria `ARQUIVAR_DENUNCIA_AVALIACAO` / `REMOVER_COMENTARIO_AVALIACAO` (`denunciaId` = id da denúncia).

- [ ] **Step 1: Testes:** responder (uma, edição substitui, outro chef 404, avaliação sem comentário 409, comentário com nome ainda não publicado 409); resposta com ofensa sai com `***`; denunciar e repetir (409); remover → lista pública mostra `"Comentário removido pela moderação"` e a nota continua na reputação; moderação vê o original; auditoria; cliente/chef nas rotas de moderação → 404.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS; suíte → PASS.
- [ ] **Step 5:** Commit `feat: chef replies, review reports and moderation`.

---

### Task 5: Rotina de publicação, LGPD e documentação

**Files:**
- Create: `service/AvaliacaoRotina.java`
- Modify: `service/UsuarioService.java`, `README.md`
- Test: `service/AvaliacaoRotinaIntegrationTest.java`, `security/AvaliacaoIntegrationTest.java`

**Interfaces:**
- Produces:
  - `AvaliacaoRotina.publicar()` com `@Scheduled(cron = "${chefhome.rotina.cron:0 */15 * * * *}")` (desligada nos testes como a do agendamento): para cada id de `idsComNomeParaPublicar(agora - 7 dias)`, publica numa transação própria, com `try/catch` que loga e segue.
  - `UsuarioService.deletar`: antes da cascata, para cada avaliação que o cliente **escreveu** sobre um chef, trava o chef e desconta `nota`/1 dos contadores; apaga as notificações `AVALIACAO_RECEBIDA`/`AVALIACAO_RESPONDIDA` que apontam para avaliações dele (via `apagarDasReferencias`); a cascata do agendamento leva `avaliacao`, `avaliacao_tag` e `denuncia_avaliacao`.

- [ ] **Step 1: Testes:** rotina publica o comentário com nome após 7 dias e não mexe no que ainda está no prazo, rodar duas vezes publica uma vez; `contaApagadaDescontaOsContadoresDoChef` (chef com 5 avaliações, uma do cliente apagado → total 4, média volta a nula) e a reputação do cliente some (Review Focus 4).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar; README: seção "Avaliações" (regras, rotas, tags, anonimato, moderação).
- [ ] **Step 4:** Suíte completa → PASS, também com `-Dsurefire.runOrder=alphabetical`.
- [ ] **Step 5:** Commit `feat: publish named reviews after 7 days; account deletion keeps reputations right; docs`.
