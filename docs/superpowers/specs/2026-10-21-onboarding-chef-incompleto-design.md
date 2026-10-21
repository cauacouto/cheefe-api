# Onboarding mínimo e perfil de chef incompleto — Design

**Data:** 2026-10-21
**Status:** aprovado em conversa, aguardando revisão do spec
**Depende de:** `feat/autorizacao` (lote 10) — mesma área (`ChefeController`)

## Objetivo

Depois do primeiro login com Google, o usuário **novo** passa por um onboarding curto que pede só **nome** e **tipo** (cliente ou chef). O chef pode entrar na plataforma com o perfil incompleto e completá-lo depois; enquanto incompleto, não aparece na busca e não pode ser agendado. Quem já tem conta não passa pelo onboarding (comportamento atual do BFF: conta existente → `{front}/`, nova → `{front}/cadastro`).

## Decisões

| Decisão | Escolha |
|---|---|
| Campos do onboarding | `tipoUsuario` e `nome` obrigatórios para todos; campos de chef opcionais. |
| Perfil completo do chef | `nacionalidade`, `especialidade`, `senioridade`, `anosDeExperiencia`, `idiomas` e `valorHora` preenchidos (textos não em branco). |
| Representação | **Calculada** (`ChefeModel.isPerfilCompleto()` + `Specification`); sem coluna nova. |
| Chef incompleto pode | logar, ver/editar o próprio perfil, cadastrar pratos. |
| Chef incompleto não pode | aparecer em `GET /chefe` e `GET /chefe/destaque`; ser escolhido em agendamento. |
| Anos de experiência | passam a ser `Integer` (nulo = não informado; `0` é um valor válido). |

## Contrato

### `POST /auth/register` (JSON)

- Obrigatórios: `tipoUsuario`, `nome`. Para `CHEFE`, os demais campos (`nacionalidade`, `especialidade`, `senioridade`, `anosDeExperiencia`, `idiomas`, `valorHora`) são opcionais.
- Chef criado só com nome → **201**, conta criada e logada; `perfilCompleto=false`.

### `GET /chefe/me` (novo, papel CHEFE)

```json
{ "id": "...", "nome": "...", "nacionalidade": null, "especialidade": null, "anosDeExperiencia": null,
  "senioridade": null, "disponivel": true, "idiomas": null, "valorHora": null,
  "perfilCompleto": false,
  "camposPendentes": ["nacionalidade", "especialidade", "senioridade", "anosDeExperiencia", "idiomas", "valorHora"] }
```

`camposPendentes` segue a ordem acima e lista só os que faltam; vazio quando completo.

### Efeitos em rotas existentes

| Rota | Mudança |
|---|---|
| `GET /chefe` | só chefs **ativos e completos** |
| `GET /chefe/destaque` | só chefs **ativos e completos** |
| `POST /agendamentos`, `PUT /agendamentos/{id}` (com `chefeIds`) | chef incompleto → **400** `"chef com perfil incompleto"` |
| `PATCH /chefe/me` | inalterado; ao preencher o último campo o chef passa a ser listado |

## Componentes

- **`ChefeModel`**: remove `@NotBlank` de `nacionalidade` e `idiomas` e `@NotNull` de `anosDeExperiencia`; `anosDeExperiencia` vira `Integer`; novos `boolean isPerfilCompleto()` e `List<String> camposPendentes()`.
- **`ChefeSpecification.perfilCompleto()`**: predicado com os seis campos não nulos e, para `nacionalidade`/`idiomas`, não vazios após `trim`. `ChefeService.mostrarChefes` combina `ativo().and(perfilCompleto())`.
- **`ChefeRepository.findMaisAgendados`**: a query passa a filtrar também os seis campos (mesma regra).
- **`agendamentoService.validarChefes`**: além de existir/ativo/disponível, exige `isPerfilCompleto()`; senão `AgendamentoInvalidoException("chef com perfil incompleto")` (400).
- **`ChefePerfilDto`** (novo record): campos de `ChefeResponseDto` + `boolean perfilCompleto` + `List<String> camposPendentes`. `ChefeService.buscarPerfil(UUID chefeId)` → `ChefePerfilDto`; inexistente → `ChefeNaoEncontradoException`.
- **`ChefeController`**: `GET /chefe/me`; matcher `hasRole(CHEFE)` para `GET /chefe/me` no `SecurityConfiguration`.
- **`ChefeResponseDto`**: `anosDeExperiencia` vira `Integer`.
- **`RegisterRequestDto`**: sem mudança de campos (os de chef já são opcionais no DTO); a falha atual vinha das anotações da entidade.

## Banco de dados

Remover `@NotBlank`/`@NotNull` da entidade não altera colunas já criadas por `ddl-auto: update`. Bancos existentes precisam rodar uma vez:

```sql
ALTER TABLE chefe ALTER COLUMN nacionalidade DROP NOT NULL;
ALTER TABLE chefe ALTER COLUMN idiomas DROP NOT NULL;
ALTER TABLE chefe ALTER COLUMN anos_de_experiencia DROP NOT NULL;
```

(Vira migration versionada no lote 9 — Flyway.) O H2 dos testes é recriado a cada execução.

## Erros

| Situação | Status |
|---|---|
| Onboarding sem `tipoUsuario` ou `nome` | 400 (já existe via `@Valid`) |
| Agendar chef incompleto | 400 |
| `GET /chefe/me` por cliente | 403 |

## Testes (TDD)

- `ChefeModelTest`: `isPerfilCompleto` falso com cada um dos seis campos ausente (e com `idiomas` em branco); verdadeiro com todos; `camposPendentes` na ordem do contrato.
- `LoginServiceTest`: registrar chef só com nome → salvo, token `CHEFE`.
- Integração (`AutorizacaoIntegrationTest` ou classe própria, H2 + cadeia real):
  - chef incompleto não aparece em `GET /chefe` nem em `GET /chefe/destaque`; completo aparece;
  - `POST /agendamentos` com chef incompleto → 400;
  - `GET /chefe/me` de chef recém-criado → `perfilCompleto=false` e os seis `camposPendentes`; após `PATCH /chefe/me` com todos os campos → `perfilCompleto=true`, `camposPendentes=[]` e passa a aparecer em `GET /chefe`;
  - cliente em `GET /chefe/me` → 403.
- Registro de chef só com nome via `POST /auth/register` (fluxo HTTP) → 201.

## Impacto no front

- Tela `/cadastro`: pergunta nome (pré-preenchido via `GET /auth/register`) e "Sou cliente / Sou chef". Envia `POST /auth/register` com `{ tipoUsuario, nome }` (cliente pode mandar `endereco: []`).
- Chef: após login, consultar `GET /chefe/me`; se `perfilCompleto=false`, mostrar aviso "Complete seu perfil para aparecer na busca" com os `camposPendentes`, e formulário que envia `PATCH /chefe/me`.

## Fora de escopo

Onboarding de cliente com endereço obrigatório; upload de foto de perfil; aprovação/moderação de chefs; notificações.
