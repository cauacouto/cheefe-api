# Ferramentas do chef Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar ao chef histórico e nota privada de cada cliente, aviso de um ano, conversa iniciada por ele com quem já atendeu, lembretes antes do serviço, confirmação de detalhes pelo cliente e folga entre agendamentos.

**Architecture:** Seis tarefas independentes em comportamento, que compartilham só a migration V16 e os enums de evento. A folga entra na checagem de conflito já existente, alargando o intervalo do confirmado. Histórico e nota ficam num serviço novo, `ClienteDoChefService`. Lembretes e aviso de um ano são rotinas no padrão de `AgendamentoRotina`: um id por transação, com releitura sob trava.

**Tech Stack:** Java 17, Spring Boot 4.0.5, Spring Data JPA, Hibernate 7, Flyway, MapStruct (`MapeamentoEstrito`), JUnit 5, MockMvc, H2, Testcontainers (Postgres 17).

**Spec:** `docs/superpowers/specs/2026-10-31-ferramentas-do-chef-design.md`

## Global Constraints

- Pacotes por camada (`domain`, `repository`, `service`, `controller`, `dto`, `exception`, `enums`, `security`); nomes, comentários e mensagens em português.
- Uma migration só nesta branch: `src/main/resources/db/migration/V16__ferramentas_do_chef.sql`, com `SET search_path TO chefhome;`. Cada task acrescenta a sua parte (a V16 ainda não foi aplicada). Enums por nome (`varchar` com CHECK). CHECK alterado = `DROP` e `ADD` com o mesmo nome e todos os valores atuais mais os novos (ver V8).
- `MigracoesPostgresTest` compara schema e entidades; o H2 dos testes nasce das entidades. Entidades com FK para conta usam `@OnDelete(CASCADE)` quando a linha some com a conta.
- Recusado não existe para o chef, e cancelado ou expirado não mostra a ele endereço, restrições e observações (`Agendamento.visivelPara`, `Agendamento.detalhesDoClienteVisiveisAoChef`). Toda resposta nova ao chef segue isso.
- `ModeracaoConteudoService.verificar(texto, autor)` (devolve `ConteudoVerificado`) roda **antes** de qualquer trava ou gravação, e **depois** da checagem de dono (404 primeiro), como no `PUT /agendamentos/{id}`.
- Rotinas: `@Scheduled` com cron por propriedade; `"-"` em `src/test/resources/application-test.yml` (os testes chamam o método direto). Um id que falha é logado e não impede os demais. Cada id numa transação própria, relendo com `travarPorId`.
- Relógio sempre pelo `Clock` injetado (`RelogioConfig`); nos testes, `@MockitoBean Clock` ou `Clock.fixed`.
- Papel errado → 403; recurso de outra pessoa → 404; estado proibido → 409; validação → 400. Datas no JSON `dd-MM-yyyy HH:mm`. Listas paginadas até 50.
- Testes com **Docker ligado**: `./mvnw -q clean test -Dspring.profiles.active=test`, depois `python3 .superpowers/sdd/2026-10-21-chat/count.py` com 0 falhas, 0 erros e **0 pulados**; também com `-Dsurefire.runOrder=alphabetical`.
- Commits por caminho (`git add -- <arquivos>`), em inglês, terminando com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Recusado e cancelado no histórico do cliente:** recusado não aparece; cancelado ou expirado aparece sem restrições, e não conta em `restricoesFrequentes`. Teste na Task 2.
2. **Status mudou entre a seleção da rotina e o envio** (cancelado depois de entrar na lista de lembretes): a releitura sob trava pula sem notificar e sem gravar. Teste na Task 5.
3. **Agendamento antigo sem menu** no resumo do chef: "Amanhã às 19:30: seu agendamento para 8 pessoas, 0 restrições", sem NPE. Teste na Task 5.
4. **Dois agendamentos do mesmo par na janela do aviso de um ano** (dois aniversários atendidos em dias próximos): uma notificação só por chef e cliente por execução, e os dois ficam marcados. Teste na Task 6.
5. **`PATCH /detalhes` com um dos campos ausente:** ausente vale como vazio (`restricoes` nulas viram `[]`, `observacoes` nula é apagada), porque o spec diz que o corpo substitui os dois. Restrição acima de `quantidadePessoas` → 400. Teste na Task 4.

---

### Task 1: Folga entre agendamentos

**Files:**
- Create: `V16__ferramentas_do_chef.sql`
- Modify: `domain/ChefeModel.java`, `dto/ChefeRequestDto.java`, `dto/ChefePerfilDto.java`, `mapper/ChefeMapper.java`, `service/ChefeService.java`, `service/AgendamentoService.java`, `service/AgendaService.java`, `README.md`
- Test: `security/FolgaIntegrationTest.java`, `FolgaConcorrenciaPostgresTest.java`

**Interfaces:**
- Produces: `ChefeModel.folgaMinutos` (`int`, padrão 60, `@ColumnDefault("60")`) e `Duration ChefeModel.folga()`. `ChefeRequestDto.folgaMinutos` (`@Min(0) @Max(240) Integer`). Múltiplo de 15 checado em `ChefeService.atualizarDados` (`RegraDeNegocioException`, "A folga deve ser múltipla de 15 minutos"). `ChefePerfilDto.folgaMinutos`. `ChefeMapper.toModel` ignora `folgaMinutos`.

**V16 desta task:** `ALTER TABLE chefe ADD COLUMN folga_minutos integer NOT NULL DEFAULT 60 CONSTRAINT chefe_folga_minutos_check CHECK (folga_minutos BETWEEN 0 AND 240 AND folga_minutos % 15 = 0);`

- [ ] **Step 1: Escrever `FolgaIntegrationTest`, falhando**

  Com um confirmado das 18:00 às 20:00 e folga de 60:
  - `pedidoDentroDaFolgaDepoisDa409` (início 20:30);
  - `pedidoDentroDaFolgaAntesDa409` (fim 17:30);
  - `encostarNaBordaDaFolgaNaoConflita` (início 21:00; e fim 17:00);
  - `folgaZeroIgualAoComportamentoAtual` (início 20:00 → 201);
  - `confirmarDentroDaFolgaDa409`;
  - `putDeHorarioDentroDaFolgaDa409`;
  - `oProprioAgendamentoNaoConflitaComSigoMesmo` (`PUT` do confirmado não se mede contra ele);
  - `bloqueioEDiaFixoNaoUsamFolga` (bloqueio terminando às 18:00 e pedido às 18:00 → 201);
  - `agendaPublicaMostraConfirmadoComFolga` (período 17:00–21:00; um confirmado fora da janela cuja folga entra nela aparece recortado);
  - `mudarAFolgaNaoDerrubaConfirmados` (folga 240 → os dois confirmados continuam);
  - `patchDeFolgaInvalidaDa400` (-15, 255, 50) e `getChefeMeTrazFolga` (padrão 60).

- [ ] **Step 2: Rodar e ver falhar**

  Run: `./mvnw -q test -Dspring.profiles.active=test -Dtest=FolgaIntegrationTest`
  Expected: FAIL.

- [ ] **Step 3: Implementar**

  `exigirHorarioLivre` passa a `existeConfirmadoSobreposto(chefeId, inicio.minus(folga), fim.plus(folga), ignorarId)`, sem mudar a consulta. Isso equivale a `[início_c − folga, fim_c + folga)`. Em `consultaPublica`, busca `confirmadosSobrepostos(chefeId, inicio.minus(folga), fim.plus(folga))` e soma a folga a cada período antes do `mesclar`. `atualizarDados` trava o chef com `chefeRepository.travar(List.of(id))` em vez de `findById`, para a confirmação e a troca de folga se serializarem. A checagem de bloqueio (`criarBloqueio`) não muda.

- [ ] **Step 4: Escrever `FolgaConcorrenciaPostgresTest`**

  No padrão de `AgendamentoConcorrenciaPostgresTest`. Confirmar um pedido que começa 30 min depois de outro confirmado e, ao mesmo tempo, mudar a folga de 0 para 60. Asserções: nenhuma exceção além de 409, e o estado final bate com uma das duas ordens.

- [ ] **Step 5: Rodar a suíte completa**, 0 falhas, 0 erros e 0 pulados nas duas ordens.

- [ ] **Step 6: Commit** `feat: chef's buffer time between confirmed bookings`

---

### Task 2: Meus clientes, histórico e nota privada

**Files:**
- Create: `domain/NotaCliente.java`, `domain/NotaClienteId.java`, `repository/NotaClienteRepository.java`, `service/ClienteDoChefService.java`, `controller/ClienteDoChefController.java`, `dto/ClienteDoChefDto.java`, `dto/HistoricoClienteDto.java`, `dto/NotaClienteDto.java`, `dto/NotaClienteRequestDto.java`
- Modify: `repository/AgendamentoRepository.java`, `service/ChefeService.java` (soft delete apaga as notas do chef), `security/SecurityConfiguration.java`, `V16`, `README.md`
- Test: `security/ClienteDoChefIntegrationTest.java`

**Interfaces:**
- Consumes: `AvaliacaoService.reputacaoDoCliente(UUID clienteId, UsuarioAutenticado chef)` (já exige chef ativo e `chefConheceOCliente`, e dá 404 a quem não pode); `AgendamentoMapper.menuDoAgendamento`.
- Produces:
  - `NotaCliente`: PK `(chefe_id, cliente_id)`, `texto`, `textoOriginal`, `atualizadaEm`; `@OnDelete(CASCADE)` nas duas FKs.
  - `ClienteDoChefDto(UUID id, String primeiroNome, String foto, long totalAgendamentos, LocalDateTime ultimoAgendamento, LocalDateTime proximoAgendamento)`, contando só `CONFIRMADO` e `CONCLUIDO` deste chef:
    - `total` = quantidade;
    - `ultimo` = maior início `≤ agora`;
    - `proximo` = menor início `> agora` entre os `CONFIRMADO`;
    - ordem por maior início, depois id do cliente.
  - `HistoricoClienteDto(Cliente cliente, ReputacaoDto reputacao, List<RestricaoFrequente> restricoesFrequentes, NotaClienteDto nota, List<Item> agendamentos)`:
    - `Item(Long id, LocalDateTime dataHoraInicial, StatusAgendamento status, MenuDoAgendamentoDto menu, Integer quantidadePessoas, OcasiaoMenu ocasiao, List<RestricaoDto> restricoes)`, sem os `RECUSADO`, os 20 mais recentes;
    - em `CANCELADO` e `EXPIRADO`, `restricoes` vem vazio;
    - `RestricaoFrequente(TipoRestricao tipo, long agendamentos)`, contando só `CONFIRMADO` e `CONCLUIDO`, em ordem decrescente.
  - `NotaClienteDto(String texto, LocalDateTime atualizadaEm)`; `NotaClienteRequestDto(@Size(max = 2000) String texto)`.
  - Rotas: `GET /clientes` (página até 50), `GET /clientes/{id}/historico`, `PUT /clientes/{id}/nota` (200 com a nota; texto nulo ou em branco → apaga e 204). As três com `hasRole(CHEFE)` e chef ativo. `GET /clientes/{id}/reputacao` continua onde está. Primeiro nome = primeira palavra do nome.

**V16 desta task:** `nota_cliente` (`chefe_id` e `cliente_id` com `ON DELETE CASCADE`, `texto varchar(2000) NOT NULL`, `texto_original varchar(2000)`, `atualizada_em timestamp(6) NOT NULL`, PK).

- [ ] **Step 1: Escrever `ClienteDoChefIntegrationTest`, falhando**

  Testes:
  - `listaSoClientesComConfirmadoOuConcluido` (pendente, recusado e cancelado não entram), `ordemPeloAgendamentoMaisRecente`, `ultimoEProximoAgendamento`;
  - `historicoSoDesteChef` (o chef B não vê os agendamentos da Ana com o chef A), `historicoDeClienteNuncaAtendidoDa404`, `restricoesFrequentesContamPorAgendamento`, `vinteMaisRecentes`;
  - Review Focus 1: `historicoSemRecusadoECanceladoSemRestricoes`;
  - nota: `notaCriadaSubstituidaEApagadaComTextoVazio` (204), `notaComPalavraoEContatoMascaradaComOriginalGuardado`, `odioNaNotaBloqueiaOChefENaoGrava` (403), `notaDeClienteNuncaAtendidoDa404SemModerar` (404 e conta não bloqueada), `notaAcimaDe2000Da400`;
  - `notasSomemComAContaDoClienteEComADoChef`; `clienteNasRotasDeChefDa403`.

- [ ] **Step 2: Rodar e ver falhar**

  Run: `./mvnw -q test -Dspring.profiles.active=test -Dtest=ClienteDoChefIntegrationTest`

- [ ] **Step 3: Implementar**

  Consultas de agregação por cliente no `AgendamentoRepository` (uma para a página, uma para a contagem), sem uma consulta por cliente. Nota: checar o acesso (`chefConheceOCliente`), depois `verificar`, depois gravar. O soft delete de chef (`ChefeService.deletar`) chama `notaClienteRepository.deleteByChefe_Id`.

- [ ] **Step 4: Rodar a suíte completa**, 0/0/0 nas duas ordens.

- [ ] **Step 5: Commit** `feat: chefs see their history with each client and keep a private note`

---

### Task 3: Chef abre conversa com quem já atendeu

**Files:**
- Modify: `dto/AbrirConversaDto.java`, `controller/ConversaController.java`, `service/ChatService.java`, `security/SecurityConfiguration.java`, `README.md`
- Create: `exception/ConversaNaoPermitidaException.java`
- Test: `security/ConversaIntegrationTest.java`

**Interfaces:**
- Produces:
  - `AbrirConversaDto(UUID chefeId, UUID clienteId)`, os dois opcionais. Cliente manda só `chefeId`, chef manda só `clienteId`; qualquer outra combinação → 400.
  - `ChatService.abrirConversa(UsuarioAutenticado autor, AbrirConversaDto dto) : ConversaAberta`.
  - `ConversaNaoPermitidaException`: 403 "Você só pode iniciar conversa com clientes que já atendeu".
  - `AgendamentoRepository.existsByUsuario_IdAndChefe_IdAndStatus(UUID, UUID, StatusAgendamento)`.
  - `POST /conversas` com `hasAnyRole(CLIENTE, CHEFE)`.

- [ ] **Step 1: Testes, falhando:** `chefAbreConversaComClienteAtendido` (201, e 200 na segunda vez, com a mesma conversa que o cliente veria); `chefSemConcluidoComOClienteDa403`; `corposTrocadosDao400` (chef com `chefeId`, cliente com `clienteId`, os dois, nenhum); `chefInativoNaoAbre` (403).

- [ ] **Step 2: Rodar e ver falhar.** `./mvnw -q test -Dspring.profiles.active=test -Dtest=ConversaIntegrationTest`

- [ ] **Step 3: Implementar.** O ramo do chef usa `exigirAtivo`, depois exige um `CONCLUIDO` com o cliente, e responde com `paraDto(conversa, chefeId, CHEFE)`. A corrida segue tratada como hoje (constraint única e nova transação).

- [ ] **Step 4: Suíte completa**, 0/0/0 nas duas ordens.

- [ ] **Step 5: Commit** `feat: chefs can start a conversation with clients they already served`

---

### Task 4: Cliente confirma ou ajusta os detalhes até 48h antes

**Files:**
- Create: `dto/DetalhesAgendamentoDto.java`, `exception/DetalhesForaDoPrazoException.java`, `service/eventos/DetalhesDoAgendamentoEvent.java`
- Modify: `enums/EventoNotificacao.java`, `enums/AcaoAgendamento.java`, `domain/Agendamento.java`, `service/AgendamentoService.java`, `service/NotificacaoListener.java`, `controller/AgendamentoController.java`, as três DTOs de resposta, `mapper/AgendamentoMapper.java`, `security/SecurityConfiguration.java`, `V16`, `README.md`
- Test: `security/DetalhesAgendamentoIntegrationTest.java`

**Interfaces:**
- Produces:
  - Enums: `EventoNotificacao` ganha os cinco eventos do spec (`LEMBRETE_DETALHES`, `RESUMO_DO_SERVICO`, `DETALHES_ALTERADOS`, `DETALHES_CONFIRMADOS`, `UM_ANO_DE_CLIENTE`), todos nesta task, porque o CHECK é um só. `AcaoAgendamento` ganha `DETALHES_ALTERADOS` e `DETALHES_CONFIRMADOS`.
  - `Agendamento.detalhesConfirmadosEm` e `boolean Agendamento.detalhesEditaveis(LocalDateTime agora)` (`CONFIRMADO` e `agora < início − 48h`).
  - `DetalhesAgendamentoDto(@Valid List<@NotNull @Valid RestricaoDto> restricoes, @Size(max = 1000) String observacoes)`.
  - `AgendamentoService.ajustarDetalhes(Long id, UsuarioAutenticado cliente, DetalhesAgendamentoDto dto)` e `confirmarDetalhes(Long id, UsuarioAutenticado cliente)`.
  - Rotas `PATCH /agendamentos/{id}/detalhes` e `PATCH /agendamentos/{id}/detalhes/confirmar` com `hasRole(CLIENTE)`; 200 com `AgendamentoResponseDto`.
  - `DetalhesForaDoPrazoException`: 409 "Os detalhes só podem ser alterados até 48h antes".
  - As três DTOs de resposta ganham `detalhesConfirmadosEm`.
  - Notificação ao chef, com referência = id do agendamento:
    - `DETALHES_ALTERADOS` "{primeiro nome} alterou os detalhes do agendamento de {dd/MM}";
    - `DETALHES_CONFIRMADOS` "{primeiro nome} confirmou os detalhes do agendamento de {dd/MM}".

**V16 desta task:**
- `agendamentos`: `lembrete_cliente_em`, `resumo_chef_em`, `detalhes_confirmados_em` e `aviso_um_ano_em`, todas `timestamp(6)`. As quatro colunas entram aqui, com as entidades.
- `notificacao_evento_check` recriado com os cinco eventos novos.
- `agendamento_historico_acao_check` (nome gerado pelo Postgres para o CHECK em linha da V6) recriado com as duas ações novas.
- Índices parciais do spec.

- [ ] **Step 1: Escrever `DetalhesAgendamentoIntegrationTest`, falhando**

  Testes:
  - `ajustarAte48hAntesSubstituiEConfirma`: grava restrições e observações, `detalhesConfirmadosEm`, linha `DETALHES_ALTERADOS`, notificação ao chef;
  - `confirmarAte48hAntes`; `confirmarDuasVezesNaoNotificaDeNovo` (200 e uma notificação);
  - `depoisDe48hAntesDa409`, testado na borda exata com relógio fixo; `foraDeConfirmadoDa409`;
  - `observacoesMascaradasEOriginalGuardado`; `odioNasObservacoesBloqueia`;
  - `deOutroClienteDa404SemModerar`; `chefNaRotaDa403`;
  - Review Focus 5: `campoAusenteValeComoVazio` e `restricaoAcimaDaQuantidadeDa400`;
  - `respostasTrazemDetalhesConfirmadosEm`.

- [ ] **Step 2: Rodar e ver falhar.** `./mvnw -q test -Dspring.profiles.active=test -Dtest=DetalhesAgendamentoIntegrationTest`

- [ ] **Step 3: Implementar**

  Ordem: `existsByIdAndUsuario_Id` (404), depois `verificar`, depois `travarPorId`, depois `detalhesEditaveis`. Restrições pelas mesmas validações do pedido (`restricoesDe` e `validarRestricoes`). Notificação por evento depois do commit, no padrão de `AgendamentoEditadoEvent` e `NotificacaoListener`. `UsuarioService.deletar`: `RESUMO_DO_SERVICO`, `DETALHES_ALTERADOS` e `DETALHES_CONFIRMADOS` entram no conjunto apagado por `agendamentoRepository.idsDoCliente`.

- [ ] **Step 4: Suíte completa**, 0/0/0 nas duas ordens.

- [ ] **Step 5: Commit** `feat: clients confirm or adjust booking details until 48h before`

---

### Task 5: Lembretes antes do serviço

**Files:**
- Modify: `service/AgendamentoRotina.java`, `service/AgendamentoService.java`, `repository/AgendamentoRepository.java`, `README.md`
- Test: `service/LembretesIntegrationTest.java`

**Interfaces:**
- Consumes: eventos e colunas da Task 4.
- Produces:
  - `AgendamentoRepository.idsParaLembrarCliente(LocalDateTime agora)`: `CONFIRMADO`, `lembreteClienteEm` nulo, `inicio ≤ agora + 72h` e `inicio > agora + 48h`.
  - `idsParaResumirAoChef(LocalDateTime agora)`: `CONFIRMADO`, `resumoChefEm` nulo, `inicio ≤ agora + 24h` e `inicio > agora`.
  - `AgendamentoService.lembrarCliente(Long id)` e `resumirAoChef(Long id)`: `@Transactional`, `travarPorId`, recheca as condições e só então notifica e grava o horário.
  - Mensagem ao cliente (`LEMBRETE_DETALHES`, referência = id do agendamento): "Seu agendamento de {dd/MM} às {HH:mm} é em 3 dias: confirme as restrições e observações até {dd/MM} às {HH:mm}" (o prazo é o início − 48h).
  - Mensagem ao chef (`RESUMO_DO_SERVICO`): "Amanhã às {HH:mm}: {menu} para {n} pessoas, {k} restrições".
    - "Hoje às" quando o início é no mesmo dia do relógio.
    - `{menu}` = `menuNome`, ou "seu agendamento" sem menu.
    - `{k}` = quantidade de tipos de restrição; "1 restrição" no singular.

- [ ] **Step 1: Escrever `LembretesIntegrationTest`, falhando**, com `Clock` fixo:
  - `lembreteDoClienteNaJanelaDe72h` (envia em 72h, não em 72h + 1 min);
  - `lembreteUmaVezSo`;
  - `confirmadoComMenosDe48hPulaOLembreteDoCliente` (nada gravado);
  - `resumoDoChefNaJanelaDe24h`, `resumoHojeAsEAmanhaAs`, `resumoUmaVezSo`;
  - `pendenteNaoRecebeLembrete`;
  - Review Focus 2: `canceladoDepoisDeSelecionadoNaoRecebe` (`@MockitoSpyBean` em `AgendamentoService` cancelando antes do `lembrarCliente`);
  - Review Focus 3: `resumoDeAgendamentoAntigoSemMenu`;
  - `umIdQueFalhaNaoImpedeOsDemais`.

- [ ] **Step 2: Rodar e ver falhar.** `./mvnw -q test -Dspring.profiles.active=test -Dtest=LembretesIntegrationTest`

- [ ] **Step 3: Implementar.** `AgendamentoRotina.processar` ganha os dois laços depois dos atuais, com o mesmo `try/catch` por id.

- [ ] **Step 4: Suíte completa**, 0/0/0 nas duas ordens.

- [ ] **Step 5: Commit** `feat: reminders three days before for the client and the day before for the chef`

---

### Task 6: Aviso de um ano e fechamento

**Files:**
- Create: `service/AvisoUmAnoRotina.java`
- Modify: `repository/AgendamentoRepository.java`, `service/AgendamentoService.java`, `service/UsuarioService.java`, `src/test/resources/application-test.yml`, `MigracoesPostgresTest.java`, `README.md`
- Test: `service/AvisoUmAnoIntegrationTest.java`, `service/UsuarioServiceIntegrationTest.java`

**Interfaces:**
- Produces:
  - `AvisoUmAnoRotina.avisar()` com `@Scheduled(cron = "${chefhome.rotina.aviso-um-ano.cron:0 0 9 * * *}")`; `chefhome.rotina.aviso-um-ano.cron: "-"` no yml de teste.
  - `AgendamentoRepository.candidatosAoAvisoDeUmAno(LocalDateTime de, LocalDateTime ate)`: ids `CONCLUIDO`, `ocasiao` em `ANIVERSARIO`, `JANTAR_ROMANTICO`, `EMPRESA`, `CELEBRACAO`, `avisoUmAnoEm` nulo, `inicio` em `(de, ate]`. A rotina passa `de = agora − 1 ano − 1 dia` e `ate = agora + 30 dias − 1 ano + 1 dia`, e filtra em Java: `inicio.plusYears(1)` está em `(agora, agora + 30 dias]`. `plusYears` já leva 29/02 para 28/02.
  - `AgendamentoService.avisarUmAno(List<Long> idsDoPar)`, numa transação por par (chef, cliente). Ela grava `avisoUmAnoEm` nos agendamentos do par que completam um ano dentro da janela e notifica no máximo uma vez, pelo de aniversário mais próximo, só se:
    - o chef está ativo e não bloqueado;
    - os dois não têm `PENDENTE` nem `CONFIRMADO` futuro.
  - Mensagem (`UM_ANO_DE_CLIENTE`, referência = id do cliente): "Em {n} dias faz um ano {da ocasião} que você fez para {primeiro nome}".
    - `{n}` = dias de calendário até o aniversário; `n = 1` → "Em 1 dia"; `n = 0` → "Hoje faz um ano …".
    - `{da ocasião}`: "do aniversário", "do jantar romântico", "do evento de empresa", "da celebração".
  - `UsuarioService.deletar` apaga `UM_ANO_DE_CLIENTE` com referência = id do cliente (`notificacaoService.apagarDasReferencias` com a string do UUID).

- [ ] **Step 1: Escrever `AvisoUmAnoIntegrationTest`, falhando**, com `Clock` fixo:
  - `dentroDaJanelaDe30DiasNotificaOChef` e `foraDaJanelaNao` (31 dias, e aniversário já passado);
  - `soAsQuatroOcasioes` (`FAMILIA` não);
  - `envioUnico` (rodar duas vezes);
  - `comAgendamentoFuturoEntreOsDoisNaoEnviaMasGrava`; `chefInativoNaoEnviaMasGrava`;
  - `vinteENoveDeFevereiro`;
  - Review Focus 4: `doisAgendamentosDoMesmoParUmAvisoSo`;
  - `textoComUmEZeroDias`.

  Em `UsuarioServiceIntegrationTest`: `contaApagadaApagaAvisoDeUmAnoEAvisosDeDetalhesDosChefs`.

- [ ] **Step 2: Rodar e ver falhar.** `./mvnw -q test -Dspring.profiles.active=test -Dtest='AvisoUmAnoIntegrationTest,UsuarioServiceIntegrationTest'`

- [ ] **Step 3: Implementar**, agrupando os candidatos por (chef, cliente) antes de chamar o serviço.

- [ ] **Step 4: Fechar.**
  - `MigracoesPostgresTest`: CHECK de `folga_minutos` e os novos valores dos CHECKs de evento e ação; `chefhome_app` com DML em `nota_cliente` e sem DDL.
  - README: rotas novas, "Lembretes e avisos", folga no "Ciclo do agendamento" e na "Agenda do chef", e o `POST /conversas` do chef.

- [ ] **Step 5: Suíte completa**, 0/0/0 nas duas ordens.

- [ ] **Step 6: Commit** `feat: one-year notice for special occasions; docs for the chef tools`
