# Onboarding mínimo e perfil de chef incompleto — Design

**Data:** 2026-10-21
**Status:** v2 — aprovado; acrescenta endereço obrigatório do cliente, foto de perfil e a regra de visibilidade com a pausa do chef
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
| Endereço do cliente | **obrigatório no onboarding**: `CLIENTE` precisa enviar ao menos um endereço válido. Chef: opcional. |
| Foto de perfil | a do Google é a padrão; troca depois por rota própria (fora do onboarding, para não travar a entrada). |
| Chef visível/agendável | **ativo** (não apagado) **e disponível** (não pausado via `/chefe/me/inativar`) **e perfil completo**. A moderação (spec próprio) acrescenta "não suspenso e < 3 denunciantes distintos com denúncia aberta". |

## Contrato

### `POST /auth/register` (JSON)

- Obrigatórios para todos: `tipoUsuario`, `nome`.
- `CLIENTE`: `endereco` com **pelo menos um** item; cada item validado como o `EnderecoRequestDto` (`@Valid` nos elementos; apartamento exige complemento). Ausente ou vazio → **400** `"Informe ao menos um endereço"`; item inválido → **400** com `campos`.
- `CHEFE`: `endereco` opcional; `nacionalidade`, `especialidade`, `senioridade`, `anosDeExperiencia`, `idiomas`, `valorHora` opcionais.
- Chef criado só com nome → **201**, conta criada e logada; `perfilCompleto=false`.

### `PUT /usuario/me/foto` (CLIENTE) e `PUT /chefe/me/foto` (CHEFE) — novos

- Multipart com a parte `foto`. Validação igual à dos pratos (`ValidadorImagem`: JPEG/PNG/WEBP, até 5MB, conteúdo confere com o tipo) e envio pelo `ImagemStorageService` na pasta `PastaImagem.PERFIL` (`chefe-api/perfil/`).
- Resposta **200** `{ "imageUrl": "https://<R2_PUBLIC_URL>/chefe-api/perfil/<uuid>.<ext>" }`; o `imageUrl` da conta é atualizado.
- Arquivo inválido → **400**; falha do storage → **502**; chef apagado → **403** (`ContaInativaException`).

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
| `GET /chefe` | só chefs **ativos, disponíveis e completos** |
| `GET /chefe/destaque` | só chefs **ativos, disponíveis e completos** |
| `POST /agendamentos`, `PUT /agendamentos/{id}` (com `chefeIds`) | chef incompleto → **400** `"chef com perfil incompleto"` (pausado/apagado já dá 409 hoje via `ChefeIndisponivelException`) |
| `PATCH /chefe/me` | inalterado; ao preencher o último campo o chef passa a ser listado |

## Componentes

- **`ChefeModel`**: remove `@NotBlank` de `nacionalidade` e `idiomas` e `@NotNull` de `anosDeExperiencia`; `anosDeExperiencia` vira `Integer`; novos `boolean isPerfilCompleto()` e `List<String> camposPendentes()`.
- **`ChefeSpecification.perfilCompleto()`**: predicado com os seis campos não nulos e, para `nacionalidade`/`idiomas`, não vazios após `trim`. `ChefeService.mostrarChefes` combina `ativo().and(perfilCompleto())`.
- **`ChefeRepository.findMaisAgendados`**: a query passa a filtrar também os seis campos (mesma regra).
- **`agendamentoService.validarChefes`**: além de existir/ativo/disponível, exige `isPerfilCompleto()`; senão `AgendamentoInvalidoException("chef com perfil incompleto")` (400).
- **`ChefePerfilDto`** (novo record): campos de `ChefeResponseDto` + `boolean perfilCompleto` + `List<String> camposPendentes`. `ChefeService.buscarPerfil(UUID chefeId)` → `ChefePerfilDto`; inexistente → `ChefeNaoEncontradoException`.
- **`ChefeController`**: `GET /chefe/me`; matcher `hasRole(CHEFE)` para `GET /chefe/me` no `SecurityConfiguration`.
- **`ChefeResponseDto`**: `anosDeExperiencia` vira `Integer`.
- **`RegisterRequestDto`**: `List<@Valid EnderecoRequestDto> endereco`; os campos de chef continuam opcionais (a falha atual vinha das anotações da entidade).
- **`LoginService.register`**: para `CLIENTE`, endereço ausente/vazio → `RegraDeNegocioException("Informe ao menos um endereço")` (400), antes de qualquer gravação.
- **`FotoPerfilService`** (novo): `String trocarFotoUsuario(UUID usuarioId, MultipartFile foto)` e `String trocarFotoChefe(UUID chefeId, MultipartFile foto)` — usam `ImagemStorageService.enviar(foto, PastaImagem.PERFIL)` e gravam `imageUrl`; chef passa por `exigirAtivo()`.
- **`UsuarioController`** `PUT /usuario/me/foto`, **`ChefeController`** `PUT /chefe/me/foto` (multipart); `SecurityConfiguration`: `/usuario/**` já é CLIENTE; acrescentar `PUT /chefe/me/foto` em `hasRole(CHEFE)`.
- **`Dtos/FotoPerfilDto`** (novo): `record(String imageUrl)`.

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
| Onboarding de cliente sem endereço / com endereço inválido | 400 |
| Foto inválida / storage fora | 400 / 502 |
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
- Registro de cliente: sem `endereco` → 400; `endereco: []` → 400; apartamento sem complemento → 400; com um endereço válido → 201 e endereço gravado.
- Chef pausado (`/chefe/me/inativar`) e completo não aparece em `GET /chefe`; ao `/ativar` volta.
- Foto de perfil: `FotoPerfilServiceTest` com `ImagemStorageService` mockado (grava a URL na conta; inválida não chama o storage; chef apagado → 403); integração `PUT /usuario/me/foto` com storage falso devolve 200 e `imageUrl`; cliente em `PUT /chefe/me/foto` → 403.

## Impacto no front

- Tela `/cadastro`: pergunta nome (pré-preenchido via `GET /auth/register`) e "Sou cliente / Sou chef". Cliente também informa o endereço (o `GET /enderecos/cep/{cep}` público ajuda a preencher). Envia `POST /auth/register` com `{ tipoUsuario, nome, endereco: [...] }`.
- "Trocar foto" no perfil: `PUT /usuario/me/foto` ou `PUT /chefe/me/foto` (multipart `foto`).
- Chef: após login, consultar `GET /chefe/me`; se `perfilCompleto=false`, mostrar aviso "Complete seu perfil para aparecer na busca" com os `camposPendentes`, e formulário que envia `PATCH /chefe/me`.

## Fora de escopo

Moderação de chefs (spec próprio: `2026-10-21-moderacao-chefs-design.md`); notificações (spec próprio, a definir).
