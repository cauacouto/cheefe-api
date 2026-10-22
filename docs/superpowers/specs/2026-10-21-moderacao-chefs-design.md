# Moderação de chefs — Design

**Data:** 2026-10-21
**Status:** v2 — aprovado; papel renomeado para MODERADOR/rotas `/moderacao/**`; não-moderador autenticado recebe **404** (a rota parece não existir)
**Depende de:** onboarding (`2026-10-21-onboarding-chef-incompleto-design.md`) — regra de visibilidade e `GET /chefe/me`

## Objetivo

Chefs aparecem assim que completam o perfil; a moderação é **posterior, por denúncia** de clientes que já foram atendidos. Um moderador revisa as denúncias e pode arquivar, suspender ou banir. Como proteção enquanto o moderador não age, um chef com denúncias de 3 clientes diferentes some automaticamente da busca.

## Decisões

| Decisão | Escolha |
|---|---|
| Modelo | Aprovação automática + denúncia + revisão por moderador. |
| Quem é moderador | E-mails em `MODERACAO_EMAILS` (configuração). Login pelo Google com um desses e-mails → token `tipo=MODERADOR`, `ROLE_MODERADOR`. Moderador **não** tem conta de cliente/chef. |
| Quem denuncia | Só o cliente dono de um agendamento **CONFIRMADO** com o chef, cujo **início já passou**. |
| Uma denúncia por vez | Um cliente tem no máximo **uma denúncia ABERTA** por chef (segunda → 409). |
| Ocultação automática | Chef com **≥ 3 denúncias ABERTAS de clientes distintos** sai da busca/destaques e não pode ser agendado até o moderador decidir. Calculada na consulta (sem flag). |
| Ações do moderador | arquivar denúncia; suspender chef (some, mas loga); reativar suspensão; banir (`ativo=false`). |

## Identidade do moderador

- `moderacao.emails` (lista, de `MODERACAO_EMAILS`, separada por vírgula; comparação case-insensitive após `trim`).
- `TipoUsuario` ganha `MODERADOR`. `LoginService.loginGoogle`: se o e-mail está em `moderacao.emails` → `LoginResult` com access token de `UsuarioAutenticado(UUID.nameUUIDFromBytes(email), email, MODERADOR)`; checado **antes** de procurar conta de cliente/chef.
- `GoogleLoginSuccessHandler`: token de moderador → redirect `{front}/moderacao`.
- `GET /auth/me` devolve `tipo: "MODERADOR"` normalmente.
- Moderador não acessa rotas de cliente/chef (já barradas por papel).

## Modelo de dados

### Tabela `denuncia` (entidade `Denuncia`)

| Campo | Tipo | Regra |
|---|---|---|
| `id` | Long (identity) | |
| `chefe` | `@ManyToOne` `ChefeModel` | obrigatório |
| `cliente` | `@ManyToOne` `UserModel` | obrigatório |
| `agendamento` | `@ManyToOne` `Agendamento` | obrigatório |
| `motivo` | enum `MotivoDenuncia` (`COMPORTAMENTO`, `NAO_COMPARECEU`, `HIGIENE`, `PERFIL_FALSO`, `OUTRO`), `STRING` | obrigatório |
| `descricao` | String ≤ 1000 | obrigatória, não em branco |
| `status` | enum `StatusDenuncia` (`ABERTA`, `ARQUIVADA`, `PROCEDENTE`), `STRING` | default `ABERTA` |
| `criadaEm` | `LocalDateTime` | `@PrePersist` |
| `resolvidaEm` | `LocalDateTime` | quando sai de `ABERTA` |
| `resolvidaPor` | String (e-mail do moderador) | quando sai de `ABERTA` |

### `ChefeModel`

- Novo `boolean suspenso`, `@Column(nullable = false)` + `@ColumnDefault("false")`.

Bancos existentes (se o `ddl-auto: update` não aplicar o default):

```sql
ALTER TABLE chefe ADD COLUMN IF NOT EXISTS suspenso boolean NOT NULL DEFAULT false;
```

## Regra de visibilidade/agendamento (final)

Um chef aparece em `GET /chefe` e `GET /chefe/destaque` e pode ser escolhido num agendamento se:

`ativo` **e** `disponivel` **e** perfil completo **e** `!suspenso` **e** `count(distinct cliente das denúncias ABERTAS) < 3`.

- `ChefeSpecification.naoSuspenso()` e `ChefeSpecification.semDenunciasEmExcesso()` (subquery `count(distinct d.cliente) from Denuncia d where d.chefe = c and d.status = ABERTA < 3`); `findMaisAgendados` com a mesma regra.
- `agendamentoService.validarChefes`: chef suspenso ou oculto por denúncias → `ChefeIndisponivelException` (409), igual a pausado.
- `GET /chefe/me` (do onboarding) ganha `suspenso` e `ocultoPorDenuncias` (boolean) para o front avisar o chef.

## Rotas

### Cliente

| Rota | Regra | Respostas |
|---|---|---|
| `POST /denuncias` `{ agendamentoId, chefeId, motivo, descricao }` | agendamento do cliente logado, `CONFIRMADO`, `dataHoraInicial` no passado, e `chefeId` presente nele (um agendamento pode ter vários chefs, então a denúncia aponta qual) | 201 `{id, status}`; agendamento inexistente/alheio/não elegível ou chef fora dele → 404; denúncia ABERTA do mesmo cliente para o mesmo chef → 409; validação → 400 |

### Moderador (`ROLE_MODERADOR`)

| Rota | Efeito | Respostas |
|---|---|---|
| `GET /moderacao/denuncias?status=ABERTA&page=0&size=20` | fila paginada (mais antigas primeiro) com id, motivo, descrição, status, criadaEm, chef (id, nome), cliente (id, nome), agendamentoId | 200 |
| `PATCH /moderacao/denuncias/{id}/arquivar` | `ABERTA` → `ARQUIVADA`, `resolvidaEm/Por` | 204; inexistente → 404; já resolvida → 409 |
| `PATCH /moderacao/chefes/{id}/suspender` | `suspenso=true`; denúncias ABERTAS do chef → `PROCEDENTE` | 204; inexistente → 404 |
| `PATCH /moderacao/chefes/{id}/reativar` | `suspenso=false` (denúncias já resolvidas não mudam) | 204; inexistente → 404 |
| `PATCH /moderacao/chefes/{id}/banir` | `ativo=false` (como conta apagada: não loga nem opera); denúncias ABERTAS → `PROCEDENTE` | 204; inexistente → 404 |

`SecurityConfiguration`: `/moderacao/**` → `hasRole("MODERADOR")`; `POST /denuncias` → `hasRole("CLIENTE")`. O `AcessoNegadoHandler` responde **404** (ProblemDetail "Not Found") quando o acesso negado é a um caminho `/moderacao/**`; nas demais rotas segue 403.

Motivação: nomes genéricos como `/admin` são alvo de varredura automática; e moderadores não existem no banco (vêm da configuração), então não há conta privilegiada a ser roubada por vazamento ou injeção.

## Componentes

- `enums/MotivoDenuncia`, `enums/StatusDenuncia`, `TipoUsuario.MODERADOR`.
- `domin/Denuncia`, `repositorys/DenunciaRepository` (consultas: existe ABERTA por cliente+chef; ABERTAS por chef; página por status ordenada por `criadaEm`).
- `Services/DenunciaService`: `criar(DenunciaRequestDto, UUID clienteId)`.
- `Services/ModeracaoService`: `listar(StatusDenuncia, Pageable)`, `arquivar(Long, String moderadorEmail)`, `suspender(UUID, String)`, `reativar(UUID)`, `banir(UUID, String)`.
- `Controller/DenunciaController` (`/denuncias`), `Controller/ModeracaoController` (`/moderacao`).
- DTOs: `DenunciaRequestDto(@NotNull Long agendamentoId, @NotNull UUID chefeId, @NotNull MotivoDenuncia motivo, @NotBlank @Size(max=1000) String descricao)`, `DenunciaCriadaDto(Long id, StatusDenuncia status)`, `DenunciaModeracaoDto(...)`.
- `LoginService`/`GoogleLoginSuccessHandler`: identidade de moderador (seção acima). Config `moderacao.emails: ${MODERACAO_EMAILS:}`.

## Erros

| Situação | Status |
|---|---|
| Não-moderador autenticado em `/moderacao/**` | **404** (oculta a existência da rota); sem cookie → 401 como qualquer rota |
| Não-cliente em `POST /denuncias` | 403 |
| Agendamento inexistente, alheio, não confirmado, futuro, ou chef fora dele | 404 |
| Denúncia ABERTA duplicada (cliente + chef) | 409 |
| Arquivar denúncia já resolvida | 409 |
| Corpo inválido | 400 |

## Testes (TDD)

- `LoginServiceTest`: e-mail em `moderacao.emails` (com maiúsculas/espaços) → token `MODERADOR`, mesmo que exista conta de cliente com o e-mail; fora da lista → fluxo normal.
- `GoogleLoginSuccessHandlerTest`: moderador → redirect `/moderacao`.
- `DenunciaServiceTest`: elegível cria `ABERTA`; agendamento alheio/pendente/futuro/chef fora → 404; duplicada → 409.
- `ModeracaoServiceTest`: arquivar; arquivar já resolvida → 409; suspender marca `PROCEDENTE`; banir → `ativo=false`.
- Integração (H2 + cadeia real), com limpeza do banco no `@AfterEach`:
  - 3 denúncias de clientes distintos ocultam o chef de `GET /chefe` e o agendamento com ele → 409; arquivar uma → volta a aparecer;
  - 3 denúncias do **mesmo** cliente não são possíveis (409 na 2ª);
  - suspenso some da busca, mas `GET /chefe/me` funciona e mostra `suspenso=true`; reativar → volta;
  - banido não loga (`loginGoogle` → `ContaInativaException`) e recebe 403 nas operações;
  - cliente/chef em `/moderacao/**` → 403; moderador em `POST /denuncias` → 403.

## Impacto no front

- Tela de denúncia no histórico do cliente (agendamentos `CONFIRMADO` passados): escolhe o chef, motivo e descrição → `POST /denuncias`.
- Área `/moderacao`: fila (`GET /moderacao/denuncias`), ações arquivar/suspender/reativar/banir.
- Chef: aviso quando `GET /chefe/me` indicar `suspenso` ou `ocultoPorDenuncias`.

## Fora de escopo

Notificações (spec próprio); recurso/contestação do chef; histórico de ações do moderador além de `resolvidaPor/Em`; painel de métricas; gestão de moderadores pela interface.
