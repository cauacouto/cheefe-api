# Menus com preço por pessoa e pedido detalhado Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar a cobrança por hora por menus com preço por pessoa, com um chef por agendamento e pedido com ocasião, restrições e observações.

**Architecture:** O `Menu` nasce ao lado do modelo atual (Task 1). O agendamento passa a ter um chef só, ainda aceitando `chefeIds` com um id (Task 2), e depois é reservado por `menuId`, guardando uma cópia do menu (Task 3). Só então `valorHora` e `especialidade` saem do perfil, a busca passa a olhar os menus (Task 4) e a migração dos dados antigos é provada no Postgres (Task 5). A suíte fica verde ao fim de cada task.

**Tech Stack:** Java 17, Spring Boot 4.0.5, Spring Data JPA (Specification), Hibernate 7, Flyway, MapStruct (`MapeamentoEstrito`), JUnit 5, MockMvc, H2, Testcontainers (Postgres 17).

**Spec:** `docs/superpowers/specs/2026-10-31-menus-pedido-detalhado-design.md`

## Global Constraints

- Pacotes por camada (`domain`, `repository`, `service`, `controller`, `dto`, `exception`, `enums`, `security`); nomes, comentários e mensagens em português.
- Uma migration só nesta branch: `src/main/resources/db/migration/V15__menus_pedido_detalhado.sql`, com `SET search_path TO chefhome;`. Cada task acrescenta a sua parte ao mesmo arquivo (a V15 ainda não foi aplicada em lugar nenhum). Enums por nome (`varchar` com CHECK).
- `ddl-auto: validate` e `MigracoesPostgresTest`: toda coluna da V15 existe na entidade com o mesmo tipo, e vice-versa. O H2 dos testes nasce das entidades.
- Entidades alteradas por gravação comum usam `@DynamicUpdate` (já vale para `ChefeModel`; vale para `Agendamento` e `Menu`).
- Contadores da conta só mudam pelas consultas atômicas existentes; nada aqui mexe neles.
- `ModeracaoConteudoService.verificar` roda **antes** de qualquer trava ou gravação.
- Papel errado → 403; recurso de outra pessoa → 404; ação proibida no estado atual → 409; validação → 400.
- Valores `numeric(10,2)`, `BigDecimal` com `setScale(2, HALF_UP)`. Datas no JSON `dd-MM-yyyy HH:mm`.
- Testes com **Docker ligado**: `./mvnw -q clean test -Dspring.profiles.active=test`, depois `python3 .superpowers/sdd/2026-10-21-chat/count.py` com 0 falhas, 0 erros e **0 pulados**; ao fim de cada task, também com `-Dsurefire.runOrder=alphabetical`.
- Commits por caminho (`git add -- <arquivos>`), em inglês, terminando com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Agendamento antigo em que todos os chefs saíram** (recusou ou desistiu, zero linhas em `agendamento_chefe`): a V15 não pode quebrar no `NOT NULL`. O chef vem do histórico; sem rastro, o agendamento é apagado. Teste na Task 5.
2. **Agendamento antigo sem menu** (`menu`, `ocasiao` e cópia nulos): listas e detalhe respondem sem NPE, com `menu: null` e `restricoes: []`; cancelar, confirmar e concluir funcionam; o `PUT` responde 409 "Agendamento antigo: cancele e faça um novo pedido". Teste na Task 3.
3. **Duração do menu empurra o fim para dentro de um bloqueio, dia fixo ou confirmado:** o início está livre, mas `início + duracaoMinutos` não; todas as checagens usam o fim calculado. Teste na Task 3.
4. **`PUT` reduz `quantidadePessoas` abaixo de uma restrição já gravada:** a validação vale para o estado final, combinando o que veio com o que estava gravado → 400. Teste na Task 3.
5. **`ocasiao` junto com `valorMinimo`/`valorMaximo`:** valem para o **mesmo** menu. Chef com menu de aniversário a R$ 300 e de família a R$ 150 não aparece em `ocasiao=ANIVERSARIO&valorMaximo=200`. Teste na Task 4.

---

### Task 1: Menu

**Files:**
- Create: `domain/Menu.java`, `domain/MenuItem.java`, `enums/OcasiaoMenu.java`, `enums/EtapaMenu.java`, `repository/MenuRepository.java`, `service/MenuService.java`, `controller/MenuController.java`, `dto/MenuRequestDto.java`, `dto/MenuItemRequestDto.java`, `dto/MenuResponseDto.java`, `exception/MenuNaoEncontradoException.java`, `src/main/resources/db/migration/V15__menus_pedido_detalhado.sql`
- Modify: `exception/ContatoNaoPermitidoException.java`, `service/PratosService.java`, `security/SecurityConfiguration.java`
- Test: `security/MenuIntegrationTest.java`, `MenuDeTeste.java` (em `src/test/java/com/couto/chefe_api/`)

**Interfaces:**
- Produces:
  - `Menu`: `Long id` (IDENTITY), `ChefeModel chefe`, `nome`, `descricao`, `BigDecimal precoPorPessoa`, `int minimoConvidados`, `int maximoConvidados`, `int duracaoMinutos`, `boolean ingredientesInclusos`, `boolean ativo = true`, `LocalDateTime criadoEm`, `Set<OcasiaoMenu> ocasioes` (`@ElementCollection`, tabela `menu_ocasiao`, `@BatchSize(size = 50)`), `List<MenuItem> itens` (`@OneToMany` com `orphanRemoval`, `@BatchSize(size = 50)`).
  - `MenuItem`: id composto `(menu_id, prato_id)`, `Pratos prato`, `EtapaMenu etapa`, `int ordem`.
  - `EtapaMenu { COUVERT, ENTRADA, PRINCIPAL, SOBREMESA }` nesta ordem; `OcasiaoMenu { JANTAR_ROMANTICO, ANIVERSARIO, FAMILIA, AMIGOS, EMPRESA, CELEBRACAO, OUTRA }`.
  - `MenuService`: `MenuResponseDto criar(MenuRequestDto, UUID chefeId)`, `editar(Long id, MenuRequestDto, UUID chefeId)`, `pausar(Long id, UUID chefeId)`, `ativar(Long id, UUID chefeId)`, `List<MenuResponseDto> meus(UUID chefeId)`, `List<MenuResponseDto> doChefe(UUID chefeId)`.
  - `MenuRepository`: `long countByChefe_Id(UUID)`, `boolean existsByChefe_IdAndAtivoTrue(UUID)`.
  - `MenuResponseDto`: `id, nome, descricao, precoPorPessoa, minimoConvidados, maximoConvidados, duracaoMinutos, ingredientesInclusos, ativo, ocasioes, itens[{ pratoId, nomePrato, descricao, imageUrl, etapa, ordem }]`, com itens por etapa e ordem.
  - `ContatoNaoPermitidoException(String onde)`: detalhe `"Não é permitido colocar telefone, e-mail, rede social ou link no " + onde`, código `CONTATO_NAO_PERMITIDO`. `PratosService` passa `"prato"`.
  - `MenuDeTeste` (`@Component` em `src/test`, achado pelo component scan): `Menu ativo(ChefeModel chefe)` e `Menu ativo(ChefeModel chefe, BigDecimal preco, OcasiaoMenu... ocasioes)`, criando um prato próprio; padrão R$ 100,00, 1 a 50 convidados, 120 minutos, ingredientes inclusos.

**V15 desta task:** `menu` (`chefe_id` FK `ON DELETE CASCADE`, checks dos limites do spec, índice `(chefe_id, ativo)`), `menu_item` (`menu_id` e `prato_id` com `ON DELETE CASCADE`, PK `(menu_id, prato_id)`), `menu_ocasiao` (cascade, PK `(menu_id, ocasiao)`, índice em `ocasiao`). Entidades com `@OnDelete(CASCADE)`: os testes limpam com `chefeRepository.deleteAll()`.

- [ ] **Step 1: Escrever `MenuIntegrationTest`, falhando**

  Testes: `criaMenuComItensOrdenadosPorEtapa`; `cadaRegraInvalidaDa400` (tabela do spec: nome vazio ou com 101 caracteres, descrição com 1001, preço 0, mínimo 0 e 51, máximo < mínimo e 51, duração 59, 721 e 75, sem ocasião, ocasião repetida, 0 e 16 itens); `pratoDeOutroChefDa400`; `pratoRepetidoDa400`; `contatoNoNomeOuDescricaoDa400ComCodigo` (`$.codigo` = `CONTATO_NAO_PERMITIDO`, `$.detail` termina em "no menu"); `vinteEUmMenusDa409ContandoPausados`; `pausarSomeDaPaginaPublicaEAtivarVolta`; `editarOuPausarMenuDeOutroChefDa404`; `chefInativoDa403`; `paginaPublicaDeChefNaoVisivelDa404`; `clienteEmRotaDeChefDa403`; `meusTrazOsPausados`.

- [ ] **Step 2: Rodar e ver falhar**

  Run: `./mvnw -q test -Dspring.profiles.active=test -Dtest=MenuIntegrationTest`
  Expected: FAIL, rotas 404.

- [ ] **Step 3: Implementar o Menu**

  Em `criar` e `editar`, travar o chef com `chefeRepository.travar(List.of(chefeId))` antes de contar (dois `POST` simultâneos não passam de 20). `editar` substitui itens e ocasiões. `doChefe` usa `chefeRepository.visivel` (senão `ChefeNaoEncontradoException`). Regras do DTO por Bean Validation, exceto: múltiplo de 30, máximo ≥ mínimo, sem repetir (`RegraDeNegocioException`). Rotas no `SecurityConfiguration`: `POST /menus`, `PUT /menus/*`, `PATCH /menus/*/pausar|ativar` e `GET /menus/meus` com `hasRole(CHEFE)`. `GET /chefe/{id}/menus` no `MenuController`, para qualquer logado.

- [ ] **Step 4: Rodar a suíte completa**

  Run: comandos da seção Global Constraints. Expected: 0 falhas, 0 erros, 0 pulados nas duas ordens (`MigracoesPostgresTest` cobre a V15).

- [ ] **Step 5: Commit** `feat: chefs publish menus with price per person`

---

### Task 2: Um chef por agendamento

Mudança de modelo sem mudar a cobrança: `valorHora` continua valendo aqui e `chefeIds` continua no corpo, aceitando um id só.

**Files:**
- Modify: `domain/Agendamento.java`, `domain/ChefeModel.java` (`mappedBy = "chefe"`), `repository/AgendamentoRepository.java`, `service/AgendamentoService.java`, `service/AgendaService.java`, `service/AvaliacaoService.java`, `service/DenunciaService.java`, `service/NotificacaoListener.java`, `service/eventos/ChefSaiuDoAgendamentoEvent.java`, `mapper/AgendamentoMapper.java`, `dto/AgendamentoResponseDto.java`, `dto/ListaAgendamentoUsuarioDto.java`, `dto/ListaAgendamentoChefeDto.java`, `dto/AvaliarRequestDto.java` se preciso, `V15`, `README.md`
- Test: todos os 23 arquivos com `setChefes` (mecânico), `service/AgendamentoServiceTest.java`, `domain/AgendamentoTransicoesTest.java`, `security/CicloAgendamentoIntegrationTest.java`, `security/AvaliacaoIntegrationTest.java`, `security/ListagensSemNMais1IntegrationTest.java`

**Interfaces:**
- Produces:
  - `Agendamento.chefe`: `@ManyToOne(fetch = LAZY, optional = false)`, coluna `chefe_id`. Saem `chefes` e `getChefes()`.
  - `Agendamento.recusar(LocalDateTime agora)` → `RECUSADO`; `desistir(LocalDateTime agora)` → `CANCELADO`; sem retorno; o chef permanece no agendamento. Sai `retirar`.
  - `Agendamento.calcularValor(ChefeModel chefe, LocalDateTime inicio, LocalDateTime fim)`, provisório até a Task 3.
  - `AgendaService.exigirDisponivel(ChefeModel chefe, LocalDateTime inicio, LocalDateTime fim)`; `AgendaIndisponivelException` sem nome.
  - `ChefSaiuDoAgendamentoEvent` sem `restam`.
  - Respostas: `AgendamentoResponseDto.chefe`, `ListaAgendamentoUsuarioDto.chefe`, `ListaAgendamentoChefeDto.chefe`, todos `ChefeResumoDto`. Saem `chefes`, `nomesChefes` e `especialidade` das listas.

**V15 desta task:** `ALTER TABLE agendamentos ADD COLUMN chefe_id uuid REFERENCES chefe`, preencher, `SET NOT NULL`, índice `(chefe_id, status)`, `DROP TABLE agendamento_chefe`:

```sql
UPDATE agendamentos a SET chefe_id = (SELECT min(ac.chefe_id::text)::uuid FROM agendamento_chefe ac WHERE ac.agendamento_id = a.id);
-- todos os chefs saíram: vale quem recusou ou desistiu por último
UPDATE agendamentos a SET chefe_id = (SELECT h.autor_id FROM agendamento_historico h
    WHERE h.agendamento_id = a.id AND h.autor_tipo = 'CHEFE' AND h.acao IN ('RECUSADO','DESISTENCIA')
    ORDER BY h.ocorrido_em DESC, h.id DESC LIMIT 1) WHERE a.chefe_id IS NULL;
-- sem rastro de chef: dado de teste, o projeto não está em produção
DELETE FROM denuncia WHERE agendamento_id IN (SELECT id FROM agendamentos WHERE chefe_id IS NULL);
DELETE FROM agendamentos WHERE chefe_id IS NULL;
```

- [ ] **Step 1: Ajustar os testes do novo ciclo, falhando**

  Em `CicloAgendamentoIntegrationTest`: `recusarViraRecusadoEOChefContinuaNoAgendamento` (`status` = `RECUSADO`, `chefe.id` preservado, `GET /agendamentos/{id}/historico` com 200 para o chef) e `desistirViraCanceladoEOChefContinuaVendoAHistoria`. Apagar os testes de "recalcula quando um chef sai" e de agendamento com dois chefs (o caso deixa de existir). Em `AgendamentoServiceTest`: `doisChefIdsDa400`. Em `AvaliacaoIntegrationTest`: trocar `clienteNaoAvaliaChefQueSaiu...` por `agendamentoRecusadoNaoPodeSerAvaliado` (409); `chefeIdErradoDa400`; `semChefeIdAvaliaOChefDoAgendamento`.

- [ ] **Step 2: Rodar e ver falhar**

  Run: `./mvnw -q test -Dspring.profiles.active=test -Dtest='CicloAgendamentoIntegrationTest,AvaliacaoIntegrationTest'`
  Expected: FAIL nas asserções novas.

- [ ] **Step 3: Implementar**

  Consultas de `AgendamentoRepository`: `JOIN a.chefes c WHERE c.id = :chefeId` vira `a.chefe.id = :chefeId`; nas listas, `@EntityGraph(attributePaths = "chefe")` (o `ListagensSemNMais1IntegrationTest` continua sem N+1). `validarChefes` recebe a lista e devolve o único `ChefeModel`; mais de um id distinto → `AgendamentoInvalidoException`. `doChef` e `chefeDoAgendamento` comparam com `getChefe()`, sem o filtro "ainda está no agendamento". `DenunciaService`: `dto.chefeId()` igual ao chef do agendamento, senão 404. `AvaliacaoService.avaliadoPor`: `chefeId` opcional para o cliente; veio e não é o chef → 400. `NotificacaoListener`: o texto de saída perde o caso "restam chefs". README: tabela de rotas e "Ciclo do agendamento".

- [ ] **Step 4: Trocar os dados de teste**

  Mecânico nos 23 arquivos: `setChefes(new ArrayList<>(List.of(x)))` vira `setChefe(x)`. `getChefes()` em asserções vira `getChefe()`.

- [ ] **Step 5: Rodar a suíte completa**

  Expected: 0 falhas, 0 erros, 0 pulados nas duas ordens.

- [ ] **Step 6: Commit** `refactor: one chef per booking; refusing keeps the chef on the record`

---

### Task 3: Pedido por menu

**Files:**
- Create: `domain/RestricaoAgendamento.java`, `enums/TipoRestricao.java`, `dto/RestricaoDto.java`, `dto/MenuDoAgendamentoDto.java`, `exception/MenuIndisponivelException.java`, `exception/ConvidadosForaDoLimiteException.java`, `exception/TrocaDeChefException.java`, `exception/AgendamentoSemMenuException.java`
- Modify: `domain/Agendamento.java`, `service/AgendamentoService.java`, `dto/AgendamentoRequestDto.java`, `dto/AtualizarAgendamentoRequestDto.java`, as três DTOs de resposta, `mapper/AgendamentoMapper.java`, `V15`, `README.md`
- Test: `security/PedidoPorMenuIntegrationTest.java`, e todo teste que chama `POST /agendamentos` ou `PUT` (10 arquivos com `chefeIds`)

**Interfaces:**
- Consumes: `MenuDeTeste.ativo(...)`, `Menu` (Task 1); `Agendamento.chefe` (Task 2).
- Produces:
  - `TipoRestricao { VEGETARIANO, VEGANO, SEM_GLUTEN, SEM_LACTOSE, ALERGIA_FRUTOS_DO_MAR, ALERGIA_AMENDOIM, ALERGIA_CASTANHAS, ALERGIA_OVO }`.
  - `RestricaoAgendamento` (`@Embeddable`): `TipoRestricao tipo`, `int quantidade`; tabela `agendamento_restricao`, cascade, `@BatchSize(size = 50)`.
  - `Agendamento`: `Menu menu` (nulável, `@OnDelete(SET_NULL)`), `menuNome`, `precoPorPessoa`, `duracaoMinutos`, `ingredientesInclusos`, `OcasiaoMenu ocasiao`, `Set<RestricaoAgendamento> restricoes`, `observacoes`, `observacoesOriginal`.
  - `Agendamento.aplicarMenu(Menu menu, int pessoas)`: grava a cópia, o fim e o valor. `Agendamento.calcularValor(BigDecimal precoPorPessoa, int pessoas)` substitui a versão provisória.
  - `AgendamentoRequestDto`: `@NotNull Long menuId`, `@NotNull @Future dataHoraInicial`, `@NotNull Long enderecoId`, `@NotNull @Positive Integer quantidadePessoas`, `@NotNull OcasiaoMenu ocasiao`, `@Valid List<RestricaoDto> restricoes`, `@Size(max = 1000) String observacoes`. Saem `dataHoraFinal` e `chefeIds`.
  - `AtualizarAgendamentoRequestDto`: todos opcionais: `dataHoraInicial`, `enderecoId`, `quantidadePessoas`, `menuId`, `ocasiao`, `restricoes`, `observacoes`. Saem `dataHoraFinal` e `chefeIds`.
  - `MenuDoAgendamentoDto(Long id, String nome, BigDecimal precoPorPessoa, Integer duracaoMinutos, Boolean ingredientesInclusos)`, montado da **cópia**. Respostas ganham `menu`, `ocasiao`, `restricoes`, `observacoes`; nunca `observacoesOriginal`.
  - Mensagens: `MenuIndisponivelException` "Menu indisponível"; `ConvidadosForaDoLimiteException(min, max)` "Este menu atende de {min} a {max} pessoas"; `TrocaDeChefException` "Para trocar de chef, cancele e faça um novo pedido"; `AgendamentoSemMenuException` "Agendamento antigo: cancele e faça um novo pedido". Todas 409. `MenuNaoEncontradoException` (Task 1) é 404.

**V15 desta task:** em `agendamentos`: `menu_id bigint REFERENCES menu ON DELETE SET NULL`, `menu_nome varchar(100)`, `preco_por_pessoa numeric(10,2)`, `duracao_minutos int`, `ingredientes_inclusos boolean`, `ocasiao varchar(255)` com CHECK da lista, `observacoes varchar(1000)`, `observacoes_original varchar(1000)`. `agendamento_restricao` (`agendamento_id` com `ON DELETE CASCADE`, `tipo` com CHECK, `quantidade int`, PK `(agendamento_id, tipo)`).

- [ ] **Step 1: Escrever `PedidoPorMenuIntegrationTest`, falhando**

  Testes:
  - `fimEValorVemDoMenu`: menu de 120 min a R$ 150,50, 8 pessoas, início 19:30 → fim 21:30, `valorTotal` 1204.00, `chefe.id` do menu, `menu.nome`.
  - `convidadosForaDoLimiteDa409`: abaixo e acima; `$.detail` "Este menu atende de 2 a 10 pessoas".
  - `menuPausadoOuChefNaoVisivelDa409`, `menuInexistenteDa404`.
  - `restricoesInvalidasDao400`: tipo repetido, quantidade 0, quantidade acima do total; `somaDasRestricoesPodePassarDoTotal` → 201.
  - `observacoesMascaradasEOriginalGuardado`: "sem coentro 21 98888-7777" → `observacoes` "sem coentro ***", original na entidade e ausente do JSON.
  - `odioNasObservacoesBloqueiaENaoGrava`: 403, zero agendamentos, conta bloqueada.
  - `ocasiaoDoPedidoNaoPrecisaSerDoMenu`: 201.
  - `editarMenuNaoAlteraAgendamentoExistente`: `PUT /menus/{id}` com preço e duração novos; a resposta do agendamento continua igual.
  - `putTrocaMenuDoMesmoChefRecalcula`, `putTrocaMenuDeOutroChefDa409`, `putQuantidadeRecalculaValor`, `putHorarioMantemADuracaoDaCopia`.
  - Review Focus 3: `fimCalculadoCaiEmBloqueioDa409`, `fimCalculadoCaiNumDiaFixoDa409`, `fimCalculadoSobrepoeConfirmadoDa409`.
  - Review Focus 4: `putReduzQuantidadeAbaixoDaRestricaoGravadaDa400`.
  - Review Focus 2: `agendamentoAntigoSemMenuRespondeSemErroECicloFunciona` e `putEmAgendamentoAntigoDa409`, gravando o agendamento direto pelo repositório.

- [ ] **Step 2: Rodar e ver falhar**

  Run: `./mvnw -q test -Dspring.profiles.active=test -Dtest=PedidoPorMenuIntegrationTest`
  Expected: FAIL, ou erro de compilação nos campos novos até criar as estruturas vazias.

- [ ] **Step 3: Implementar**

  `criarAgendamento`, nesta ordem: `verificar(observacoes)`; carregar o menu (404); `menu.isAtivo()` e `chefeRepository.visivel(chefe)`, senão 409; limite de convidados; restrições; `validarPeriodo`; endereço; `aplicarMenu`; `exigirMesmaCidade`; `exigirHorarioLivre`; `agendaService.exigirDisponivel`. `atualizarAgendamento`: `verificar` antes de `travarPorId`; `menu` nulo → `AgendamentoSemMenuException`; aplicar os campos; `menuId` ou `quantidadePessoas` no corpo → recarregar o menu e revalidar como na criação (menu de outro chef → `TrocaDeChefException`); horário sem troca de menu → fim pela `duracaoMinutos` da cópia; validar restrições no estado final. Sempre revalidar cidade, horário e agenda. `observacoesOriginal` só quando o exibido difere do enviado. Sai o `calcularValor` provisório.

- [ ] **Step 4: Trocar os testes que usam a API**

  Nos 10 arquivos com `chefeIds`: criar `menus.ativo(chefe)` (`@Autowired MenuDeTeste menus`) e enviar `menuId`, sem `dataHoraFinal`, com `"ocasiao":"FAMILIA"`. Cálculos de valor passam a `preço × pessoas`.

- [ ] **Step 5: Rodar a suíte completa**

  Expected: 0 falhas, 0 erros, 0 pulados nas duas ordens.

- [ ] **Step 6: Commit** `feat: bookings reserve a menu, with occasion, dietary restrictions and notes`

---

### Task 4: Perfil e busca pelos menus

**Files:**
- Modify: `domain/ChefeModel.java`, `dto/ChefeRequestDto.java`, `dto/ChefeResponseDto.java`, `dto/ChefePerfilDto.java`, `dto/ChefeResumoDto.java`, `dto/RegisterRequestDto.java`, `mapper/ChefeMapper.java`, `mapper/AgendamentoMapper.java`, `repository/ChefeRepository.java`, `repository/ChefeSpecification.java`, `repository/MenuRepository.java`, `service/ChefeService.java`, `controller/ChefeController.java`, `V15`, `README.md`
- Test: `security/BuscaPorMenuIntegrationTest.java`, `BuscaNotaMediaPostgresTest.java`, `security/OnboardingIntegrationTest.java`, `domain/ChefeModelTest.java`, e os arquivos com `setValorHora` e `setEspecialidade` (35)

**Interfaces:**
- Consumes: `MenuRepository`, `MenuDeTeste` (Tasks 1 e 3).
- Produces:
  - `ChefeModel.especialidades`: `Set<Especialidades>` (`@ElementCollection`, tabela `chefe_especialidade`, `@BatchSize(size = 50)`, `@OnDelete(CASCADE)`). Saem `especialidade` e `valorHora`.
  - `ChefeModel.camposPendentes()`, só campos da conta, nesta ordem: `nacionalidade, especialidades, senioridade, anosDeExperiencia, idiomas, cidade, estado`.
  - `ChefePerfilDto.de(ChefeModel, boolean ocultoPorDenuncias, boolean temMenuAtivo, BigDecimal precoAPartirDe)`: acrescenta `"menu"` ao fim de `camposPendentes` e calcula `perfilCompleto` sobre a lista final.
  - `ChefeRepository.VISIVEL`: troca `c.especialidade IS NOT NULL` e `c.valorHora IS NOT NULL` por `c.especialidades IS NOT EMPTY` e `EXISTS (SELECT 1 FROM Menu m WHERE m.chefe = c AND m.ativo = true)`.
  - `ChefeSpecification`: `perfilCompleto()` e `comMenuAtivo(OcasiaoMenu ocasiao, BigDecimal minimo, BigDecimal maximo)` (um único `EXISTS`, com todos os filtros no mesmo menu); `daEspecialidade` com `cb.isMember`; `ordenadoPorPreco(Sort.Direction, Sort demais)`, com subconsulta `min(precoPorPessoa)` dos menus ativos e o id como desempate. Saem `valorMinimoMaior` e `valorMaximoMenor`.
  - `MenuRepository.menoresPrecos(Collection<UUID> chefes)`: linhas `{chefeId, min}`, uma consulta por página.
  - `ChefeResponseDto`: sai `valorHora`, `especialidade` vira `Set<Especialidades> especialidades`, entra `BigDecimal precoAPartirDe`. `ChefeResumoDto`: `especialidade` vira `especialidades`.
  - `ChefeRequestDto.especialidades`: `@Size(min = 1, max = 3) List<Especialidades>`; repetida → 400 em `ChefeService`. Sai `valorHora`. `RegisterRequestDto` perde `valorHora`, e `especialidade` vira `especialidades`.
  - `GET /chefe`: novo `ocasiao` (`OcasiaoMenu`). `ORDENAVEIS` troca `valorHora` por `precoAPartirDe`; com `notaMedia` e `precoAPartirDe` juntos, `notaMedia` vale e `precoAPartirDe` é ignorado.

**V15 desta task:** `chefe_especialidade` (`chefe_id` com cascade, `especialidade` com CHECK, PK), `INSERT ... SELECT id, especialidade FROM chefe WHERE especialidade IS NOT NULL`, depois `DROP COLUMN valor_hora` e `DROP COLUMN especialidade`.

- [ ] **Step 1: Escrever `BuscaPorMenuIntegrationTest`, falhando**

  Testes: `filtraPorOcasiao`; `ocasiaoInvalidaDa400`; `faixaDePrecoValeSobreOPrecoPorPessoa`; Review Focus 5: `ocasiaoEFaixaValemParaOMesmoMenu`; `chefSoComMenuPausadoSomeDaBusca`; `chefSemMenuTemMenuEmCamposPendentes` (`perfilCompleto` false, `camposPendentes` contém `menu`); `ordenaPorPrecoAPartirDeNasDuasDirecoes`; `precoAPartirDeIgnoraMenuPausado`; `especialidadeFiltraQuemTemNaLista`; `patchComZeroOuQuatroEspecialidadesDa400`; `patchComEspecialidadeRepetidaDa400`; `buscaNaoFazUmaConsultaPorChef` (seguir `ListagensSemNMais1IntegrationTest`).

- [ ] **Step 2: Rodar e ver falhar**

  Run: `./mvnw -q test -Dspring.profiles.active=test -Dtest=BuscaPorMenuIntegrationTest`
  Expected: FAIL.

- [ ] **Step 3: Implementar**

  `mostrarChefes` e `chefesMaisAgendados` completam a página com `menoresPrecos` depois do `map(chefeMapper::toDto)`. `ChefeMapper.toDto` ignora `precoAPartirDe`, que o serviço preenche. O `ordenadoPorPreco` entra como o `ordenadoPorNotaMedia`: a ordem inteira vai para a Specification.

- [ ] **Step 4: Trocar os dados de teste**

  Nos 35 arquivos: sai `setValorHora`, `setEspecialidade(x)` vira `setEspecialidades(Set.of(x))`. Fixture cujo chef precisa aparecer na busca ganha `menus.ativo(chefe)` logo depois do `save`. `BuscaNotaMediaPostgresTest` filtra por um preço de menu único, no lugar de `valorHora`.

- [ ] **Step 5: Rodar a suíte completa**

  Expected: 0 falhas, 0 erros, 0 pulados nas duas ordens.

- [ ] **Step 6: Commit** `feat: chefs list up to three specialties; search looks at their active menus`

---

### Task 5: Migração dos dados antigos e fechamento

**Files:**
- Modify: `MigracoesPostgresTest.java`, `README.md`, `application.example.yml` se citar `valorHora`
- Test: `MigracoesPostgresTest.java`

**Interfaces:**
- Consumes: a V15 completa.

- [ ] **Step 1: Escrever o teste de conversão**

  Seguindo o teste V4→V5 de `MigracoesPostgresTest` (container próprio, `.target("14")`, inserir, migrar até a V15). `v14ParaV15ConverteOsDadosAntigos`, com asserções SQL:
  - agendamento com dois chefs → `chefe_id` = o de menor id; `menu_id` nulo; `valor_total` igual ao de antes;
  - Review Focus 1: agendamento recusado sem linhas em `agendamento_chefe`, com histórico `RECUSADO` do chef → `chefe_id` = esse chef;
  - agendamento sem chef e sem histórico, com uma denúncia → agendamento e denúncia apagados;
  - chef com `especialidade` → uma linha em `chefe_especialidade`; `valor_hora`, `chefe.especialidade` e a tabela `agendamento_chefe` não existem mais.

- [ ] **Step 2: Rodar**

  Run: `./mvnw -q test -Dspring.profiles.active=test -Dtest=MigracoesPostgresTest`
  Expected: PASS. Se falhar, corrigir a V15.

- [ ] **Step 3: Conferir privilégios e README**

  `MigracoesPostgresTest` confirma que `chefhome_app` tem DML nas tabelas novas e nenhum DDL. No README: tabela de rotas (menus, `GET /chefe` com `ocasiao`, `precoAPartirDe`), corpo novo de `POST /agendamentos`, "Ciclo do agendamento" sem "o chef sai e o valor é recalculado", nota de contrato quebrado (`chefes` vira `chefe`). Nenhuma menção restante a `valorHora` ou `chefeIds`.

- [ ] **Step 4: Rodar a suíte completa**

  Expected: 0 falhas, 0 erros, 0 pulados nas duas ordens.

- [ ] **Step 5: Commit** `test: V15 converts old bookings and chefs; docs for the menu model`
