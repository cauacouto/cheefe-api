# Autorização por dono e papel (lote 10) — Design

**Data:** 2026-10-21
**Status:** aprovado em conversa, aguardando revisão do spec
**Branch:** `feat/autorizacao`
**Origem:** revisão de engenharia, achados C2, C3, C4 (e B5)

## Objetivo

Fechar as falhas críticas de autorização: hoje qualquer usuário logado lista e apaga outros usuários, cria agendamento em nome de terceiros, lê/edita agendamentos alheios, confirma agendamentos sendo cliente e edita qualquer chef ou prato. Depois deste lote, **cada pessoa só age sobre o que é dela**, e ações de chef/cliente exigem o papel correspondente.

## Decisões

| Decisão | Escolha |
|---|---|
| Administrador | **Não existe.** Rotas que só fariam sentido para admin (listar todos os usuários, buscar/apagar por id) são removidas. |
| Escopo dos agendamentos | **Só proteger as ações existentes** (criar, editar, confirmar, listar). Cancelar/recusar/concluir ficam fora. |
| Onde mora a regra de papel | `SecurityConfiguration`, por rota (`hasRole`). Um lugar só, auditável. |
| Onde mora a regra de dono | Nos serviços, comparando com o `id` do principal (`UsuarioAutenticado`). |
| Identidade | **Nunca** vem do cliente (path, query ou body). Sempre do principal do cookie. |
| Papel errado | **403** (Spring Security). |
| Recurso de outra pessoa | **404**, idêntico a "não existe" — não revela quais ids existem. |
| Status que não permite a ação | **409** (`ConflitoException`/`AgendamentoNaoEditavelException`). |

## Rotas

Papel: `C` = `ROLE_CLIENTE`, `F` = `ROLE_CHEFE`, `*` = qualquer autenticado.

### Usuário (cliente)

| Antes | Depois | Papel | Regra |
|---|---|---|---|
| `GET /usuario` (lista todos) | **removida** | — | — |
| `GET /usuario/{id}` | `GET /usuario/me` | C | dados do próprio cliente |
| `PUT /usuario?id=` | `PUT /usuario/me` | C | atualiza o próprio cliente |
| `DELETE /usuario/{id}` | `DELETE /usuario/me` | C | apaga a própria conta (cascata do A9 permanece; ver *Fora de escopo*) |
| `GET /usuario/enderecos` | igual | C | já usa o principal; passa a usar `id` em vez de e-mail |
| `POST /usuario/enderecos` | igual | C | idem |

### Chef

| Antes | Depois | Papel | Regra |
|---|---|---|---|
| `GET /chefe`, `GET /chefe/destaque` | iguais | * | leitura pública entre logados |
| `PATCH /chefe/{id}` | `PATCH /chefe/me` | F | atualiza o próprio perfil |
| `PATCH /chefe/{id}/inativar` | `PATCH /chefe/me/inativar` | F | inativa o próprio perfil |
| `DELETE /chefe/{id}` | `DELETE /chefe/me` | F | soft delete do próprio perfil |

### Prato

| Antes | Depois | Papel | Regra |
|---|---|---|---|
| `GET /pratos`, `GET /pratos/chefe/{chefeId}` | iguais | * | leitura |
| `POST /pratos` | igual | F | dono = chef logado (por `id`, sem lookup por e-mail) |
| `PUT /pratos?id=` | `PUT /pratos/{id}` | F | só o chef dono do prato; outro chef → 404 |
| `GET /pratos/pratos/meus-pratos` (path duplicado) | `GET /pratos/meus` | F | pratos do chef logado |

### Agendamento

| Antes | Depois | Papel | Regra |
|---|---|---|---|
| `POST /agendamentos?email=` | `POST /agendamentos` | C | cliente = logado; `enderecoId` precisa ser do cliente logado, senão 404 |
| `GET /agendamentos/usuario/{usuarioId}` | `GET /agendamentos/meus` | C | agendamentos do cliente logado |
| `PUT /agendamentos/{id}` | igual | C | só o cliente dono (senão 404); só se `PENDENTE` (senão 409); novo `enderecoId`, se enviado, precisa ser do cliente |
| `GET /agendamentos/chefe/{chefeId}` | `GET /agendamentos/recebidos` | F | agendamentos em que o chef logado está |
| `PATCH /agendamentos/{id}/confirmar` | igual | F | só um chef **presente no agendamento** (senão 404); só se `PENDENTE` (senão 409) |

## Componentes

- **`SecurityConfiguration`** — matchers por papel, antes do `anyRequest().authenticated()`:
  - `hasRole("CLIENTE")`: `/usuario/**`, `POST /agendamentos`, `PUT /agendamentos/*`, `GET /agendamentos/meus`
  - `hasRole("CHEFE")`: `PATCH /chefe/me`, `PATCH /chefe/me/inativar`, `DELETE /chefe/me`, `POST /pratos`, `PUT /pratos/*`, `GET /pratos/meus`, `GET /agendamentos/recebidos`, `PATCH /agendamentos/*/confirmar`
  - Demais rotas autenticadas seguem `authenticated()`; públicas não mudam.
- **Controllers** (`UsuarioController`, `ChefeController`, `PratosController`, `AgendamentoController`) — recebem `@AuthenticationPrincipal UsuarioAutenticado` e passam `usuario.id()` aos serviços. Nenhum parâmetro de identidade vindo do cliente.
- **Serviços** — assinaturas passam a receber o `UUID` do dono:
  - `UsuarioService`: `buscar(UUID usuarioId)`, `atualizar(UserRequestDto, UUID usuarioId)`, `deletar(UUID usuarioId)`; remove `listarUsuarios`.
  - `EnderecoService`: `listarEnderecos(UUID usuarioId)`, `adicionarEndereco(EnderecoRequestDto, UUID usuarioId)`.
  - `ChefeService`: `atualizarDados(ChefeRequestDto, UUID chefeId)`, `inativar(UUID chefeId)`, `deletar(UUID chefeId)` — chamados só com o id do principal.
  - `PratosService`: `cadastrarPratos(dto, foto, UUID chefeId)`, `updatePratos(dto, Integer pratoId, UUID chefeId)` (prato de outro chef → `PratoNaoEncontradoException`), `listaPratos(UUID chefeId)`.
  - `agendamentoService`: `criarAgendamento(dto, UUID usuarioId)`, `listarAgendamentosUsuario(UUID usuarioId)`, `listarAgendamentosChefe(UUID chefeId)`, `atualizarAgedamento(dto, Long id, UUID usuarioId)`, `confirmarAgendamento(Long id, UUID chefeId)`. Agendamento alheio → `AgendamentoException` (404); endereço alheio → `EnderecoException` (404); status ≠ `PENDENTE` → 409.
- **Repositórios** — consultas de dono quando úteis: `EndercoRepository.findByIdAndUsuario_Id(Long, UUID)`, `AgendamentoRepository.findByIdAndUsuario_Id(Long, UUID)`, `PatrosRepository.findByIdAndChefeModel_Id(Integer, UUID)`; para confirmar, buscar por id e checar `chefes.anyMatch(id)`.
- Buscas por e-mail para identificar o logado (`findByEmail` em `EnderecoService`, `PratosController`, `agendamentoService`) **saem**.

## Erros

| Situação | Status |
|---|---|
| Sem cookie / token inválido | 401 (já existe) |
| Papel errado | 403 |
| Recurso inexistente **ou** de outra pessoa | 404 |
| Ação não permitida no status atual | 409 |

## Testes (TDD)

- **Serviços (unitários, Mockito):** para cada operação com dono — dono consegue; não dono recebe a exceção 404; status errado recebe 409 (editar/confirmar agendamento); endereço alheio na criação/edição de agendamento → 404; chef fora do agendamento não confirma.
- **`AutorizacaoIntegrationTest`** (`@SpringBootTest` + MockMvc + `springSecurity()`, H2, cookies gerados pelo `TokenService`) reproduzindo os ataques da revisão:
  - cliente A não lê/edita/apaga dados do cliente B (rotas `/me` só alcançam A; rotas antigas por id não existem mais → 404/405);
  - cliente A não cria agendamento com endereço de B (404) nem em nome de B (não há como informar B);
  - cliente A não edita agendamento de B (404);
  - cliente não confirma agendamento (403);
  - chef X não confirma agendamento em que não está (404);
  - cliente não edita chef (403); chef X não edita prato do chef Y (404);
  - chef não acessa `/usuario/me` (403); cliente não acessa `/pratos/meus` (403);
  - fluxos felizes de cada rota nova devolvem 2xx.

## Impacto no front

Rotas com `{id}` de usuário/chef viram `/me`; `?email=` some do POST de agendamento; listagens viram `/agendamentos/meus` e `/agendamentos/recebidos`; `/pratos/pratos/meus-pratos` vira `/pratos/meus`; `PUT /pratos?id=` vira `PUT /pratos/{id}`. O README ganha a tabela de rotas por papel.

## Fora de escopo

- Cancelar, recusar e concluir agendamentos; confirmação individual por chef (requer schema → depois do Flyway).
- `DELETE /usuario/me` ainda apaga o histórico em cascata (A9) — regra de exclusão/anonimização em lote próprio.
- Listagem de chefs/pratos pública para visitantes não logados.
- Papel `ADMIN`.
