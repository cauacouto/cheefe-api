# Menus com preço por pessoa e pedido detalhado — Design

## Objetivo

Trocar o modelo de cobrança por hora (`valorHora × horas × chefs`) pelo modelo do mercado: o chef publica **menus com preço por pessoa** e o cliente reserva um menu, informando ocasião, restrições alimentares e observações. É a base dos specs seguintes: histórico do cliente (ferramentas do chef), pagamento com retenção e IA de pedido e menu. Segundo spec de diferenciação; depende só do contato protegido (`2026-10-31-contato-protegido-design.md`).

## Decisões (aprovadas)

- **Um chef por agendamento**, preço pelo menu: `precoPorPessoa × quantidadePessoas`. A função de vários chefs por agendamento sai; quando precisa de equipe, o chef leva auxiliares e embute no preço.
- **Menu fixo:** o chef monta com pratos que já cadastrou, por etapa. Ajustes finos vão em observações ou no chat. Menu com escolhas por etapa fica para depois.
- **O menu define a duração:** o cliente informa só o início; o fim é calculado.
- **Cada menu diz se os ingredientes estão inclusos**; o preço por pessoa já reflete essa escolha.
- **Restrições alimentares:** lista fechada com quantidade de pessoas, mais texto livre (observações).
- **Ocasiões no menu e na busca:** o chef marca para quais ocasiões cada menu serve, a busca filtra por isso e o pedido registra a ocasião.
- O agendamento **guarda uma cópia do menu** (nome, preço por pessoa, duração, ingredientes inclusos) no momento da reserva e de cada troca de menu; mudar o menu depois não altera agendamentos existentes. Sem versionamento de menu.

## Regras

### Menu

| Campo | Regra |
|---|---|
| `nome` | obrigatório, até 100 caracteres |
| `descricao` | opcional, até 1000 caracteres |
| `precoPorPessoa` | obrigatório, maior que zero, 2 casas |
| `minimoConvidados` | obrigatório, de 1 a 50 |
| `maximoConvidados` | obrigatório, de `minimoConvidados` a 50 |
| `duracaoMinutos` | obrigatório, de 60 a 720, múltiplo de 30 |
| `ingredientesInclusos` | obrigatório (`true`/`false`) |
| `ocasioes` | 1 ou mais, sem repetição: `JANTAR_ROMANTICO`, `ANIVERSARIO`, `FAMILIA`, `AMIGOS`, `EMPRESA`, `CELEBRACAO`, `OUTRA` |
| `itens` | 1 a 15 itens `{ pratoId, etapa, ordem }`; etapa em `COUVERT`, `ENTRADA`, `PRINCIPAL`, `SOBREMESA`; prato precisa ser do próprio chef (de outro → **400**), sem repetir o mesmo prato |

- Fora dessas regras → **400**.
- Nome e descrição passam pelo `DetectorContato` do spec 1: contato → **400** `CONTATO_NAO_PERMITIDO`.
- A etapa fica no item, não no prato: o mesmo prato pode ser entrada num menu e principal em outro.
- Até **20 menus** por chef, contando pausados (**409** além disso).
- Sem exclusão: o menu é pausado (agendamentos apontam para ele). Menu pausado some da página pública e não pode ser reservado; agendamentos já feitos com ele seguem normalmente.

### Rotas de menu

| Rota | Papel | Resposta |
|---|---|---|
| `POST /menus` | CHEFE | **201** com o menu |
| `PUT /menus/{id}` | CHEFE (dono) | **200**; de outro chef → **404** |
| `PATCH /menus/{id}/pausar`, `PATCH /menus/{id}/ativar` | CHEFE (dono) | **200** |
| `GET /menus/meus` | CHEFE | todos os menus do chef, inclusive pausados |
| `GET /chefe/{id}/menus` | qualquer logado | só menus ativos; chef não visível → **404** |

Todas as rotas do chef exigem conta ativa (`exigirAtivo`), como a agenda. A resposta do menu traz os itens com nome, descrição e foto do prato, ordenados por etapa e ordem.

### Perfil e busca do chef

- `valorHora` sai do perfil, do `ChefeRequestDto` e das respostas.
- `especialidade` vira `especialidades`: lista de 1 a 3, sem repetição (fora disso → **400**).
- `camposPendentes()`: sai `valorHora`, `especialidade` vira `especialidades`, entra `menu` (chef sem nenhum menu ativo). Perfil completo continua sendo condição para aparecer na busca e ser agendado.
- Busca `GET /chefe`:
  - `especialidade=` filtra quem tem aquela especialidade na lista;
  - `ocasiao=` (novo) filtra quem tem pelo menos um menu ativo com aquela ocasião; ocasião inválida → **400**;
  - `valorMinimo`/`valorMaximo` passam a valer sobre o preço por pessoa: o chef aparece se tem pelo menos um menu ativo nessa faixa;
  - ordenação: `valorHora` sai e entra `precoAPartirDe` (menor preço por pessoa entre os menus ativos do chef). As demais (`nome`, `anosDeExperiencia`, `notaMedia`) seguem iguais.
- `ChefeResponseDto` (o da busca) e `ChefePerfilDto` ganham `precoAPartirDe` e `especialidades`; `ChefeResumoDto` (o das listas de agendamento) troca `especialidade` por `especialidades`.

### Agendamento

**Pedido** (`POST /agendamentos`):

```json
{
  "menuId": 12,
  "dataHoraInicial": "20-11-2026 19:30",
  "enderecoId": 3,
  "quantidadePessoas": 8,
  "ocasiao": "ANIVERSARIO",
  "restricoes": [{ "tipo": "VEGETARIANO", "quantidade": 2 }, { "tipo": "ALERGIA_FRUTOS_DO_MAR", "quantidade": 1 }],
  "observacoes": "Uma pessoa não gosta de coentro."
}
```

- `ocasiao` obrigatória (mesma lista de ocasiões do menu). Não precisa ser uma das ocasiões do menu: a ocasião do menu é para a busca; a do pedido informa o chef.
- `restricoes`: tipos `VEGETARIANO`, `VEGANO`, `SEM_GLUTEN`, `SEM_LACTOSE`, `ALERGIA_FRUTOS_DO_MAR`, `ALERGIA_AMENDOIM`, `ALERGIA_CASTANHAS`, `ALERGIA_OVO`; sem repetir tipo; cada quantidade de 1 a `quantidadePessoas`. A soma pode passar do total (uma pessoa pode ser vegana e sem glúten), mas cada item não pode. Fora disso → **400**.
- `observacoes`: opcional, até 1000 caracteres; passa por `ModeracaoConteudoService.verificar` (palavrão e contato viram `***`, ódio ou ameaça bloqueiam a conta, como no chat).
- O chef vem do menu; `dataHoraFinal = dataHoraInicial + duracaoMinutos`.
- `valorTotal = precoPorPessoa × quantidadePessoas`, 2 casas.

**Novos motivos de 409:**

- menu inexistente → **404** (como chef inexistente hoje); menu pausado ou de chef não visível → **409** "Menu indisponível";
- `quantidadePessoas` fora de `minimoConvidados..maximoConvidados` → **409** "Este menu atende de {min} a {max} pessoas".

As regras atuais continuam: endereço do cliente e da cidade do chef, conflito com confirmado, bloqueio e dia fixo da agenda, `@Future`.

**Edição** (`PUT /agendamentos/{id}`, só `PENDENTE`): pode trocar `dataHoraInicial`, `enderecoId`, `quantidadePessoas`, `ocasiao`, `restricoes`, `observacoes` e `menuId`, desde que o novo menu seja do mesmo chef (de outro → **409** "Para trocar de chef, cancele e faça um novo pedido"). Trocar o menu ou a quantidade recalcula a cópia, o fim e o valor, com as mesmas validações da criação.

**Ciclo com um chef só:**

- `PATCH /recusar` → `RECUSADO`; `PATCH /desistir` → `CANCELADO`. Sem recálculo nem "o chef sai do agendamento".
- **Recusou:** para o chef, o pedido deixa de existir: some de `/agendamentos/recebidos`, histórico e ações dão **404**, não conta para `GET /clientes/{id}/reputacao` (outro agendamento com o mesmo cliente continua valendo) e os avisos do pedido, que citam o cliente, são apagados. Decisão do usuário: quem recusou não vê mais nada do cliente. O cliente continua vendo o pedido e quem recusou.
- **Desistiu:** o chef continua vendo o agendamento e a linha do tempo (ele já tinha aceitado o serviço).
- Confirmar, concluir, cancelar, expirar, rotinas, prazos e notificações: mesmas regras, com `chefe` no lugar de `chefes`.

### Contrato da API (mudanças que quebram)

- `AgendamentoResponseDto`, `ListaAgendamentoUsuarioDto`, `ListaAgendamentoChefeDto`: `chefes[]` vira `chefe` (resumo do chef); entram `menu` (`{ id, nome, precoPorPessoa, duracaoMinutos, ingredientesInclusos }`, a cópia do agendamento), `ocasiao`, `restricoes[]` e `observacoes`.
- Avaliação (`POST /agendamentos/{id}/avaliacoes`): `chefeId` deixa de ser necessário para o cliente; se vier, precisa ser o chef do agendamento (senão **400**).
- Mensagens de 409 que citavam o nome de um entre vários chefs passam a falar do chef do agendamento.
- O README documenta as mudanças na tabela de rotas e no "Ciclo do agendamento".

## Componentes

- **Domínio:** `Menu`, `MenuItem` (prato, etapa, ordem), enums `OcasiaoMenu`, `EtapaMenu`, `TipoRestricao`; `RestricaoAgendamento` (embutível: tipo e quantidade); `Agendamento` troca `List<ChefeModel> chefes` por `ChefeModel chefe`, ganha `menu` (referência), a cópia (`menuNome`, `precoPorPessoa`, `duracaoMinutos`, `ingredientesInclusos`), `ocasiao`, `restricoes`, `observacoes`, `observacoesOriginal`. `calcularValor` passa a receber preço por pessoa e quantidade.
- **`MenuService`** (novo): criar, editar, pausar, ativar, listar; valida pratos do chef e chama o `DetectorContato`.
- **`MenuController`** (novo) e rota pública em `ChefeController`.
- **`AgendamentoService`**: resolve o menu, copia, calcula fim e valor; os usos de `getChefes()` em `AgendamentoService`, `AgendaService`, `AvaliacaoService`, `DenunciaService`, `ChatService`, `HistoricoAgendamentoService`, notificações e mappers passam para `getChefe()`.
- **`ChefeSpecification`**: filtros por especialidade (lista), ocasião e faixa de preço via `exists` em `menu`; ordenação por `precoAPartirDe` por subconsulta (`min(preco_por_pessoa)` dos menus ativos).

## Dados (V15)

- `menu`: `id`, `chefe_id` (FK), `nome varchar(100)`, `descricao varchar(1000)`, `preco_por_pessoa numeric(10,2)`, `minimo_convidados int`, `maximo_convidados int`, `duracao_minutos int`, `ingredientes_inclusos boolean`, `ativo boolean`, `criado_em timestamp(6)`; checks dos limites; índice em `(chefe_id, ativo)`.
- `menu_item`: `menu_id` (FK), `prato_id` (FK), `etapa varchar`, `ordem int`; PK `(menu_id, prato_id)`.
- `menu_ocasiao`: `menu_id` (FK), `ocasiao varchar`; PK `(menu_id, ocasiao)`; índice em `ocasiao`.
- `chefe_especialidade`: `chefe_id` (FK), `especialidade varchar`; PK `(chefe_id, especialidade)`. Copia `chefe.especialidade` atual.
- `agendamentos`: `chefe_id` (FK), `menu_id` (FK, nulo nos antigos), `menu_nome varchar(100)`, `preco_por_pessoa numeric(10,2)`, `duracao_minutos int`, `ingredientes_inclusos boolean` (nulos nos antigos), `ocasiao varchar` (nulo nos antigos), `observacoes varchar(1000)`, `observacoes_original varchar(1000)`.
- `agendamento_restricao`: `agendamento_id` (FK), `tipo varchar`, `quantidade int`; PK `(agendamento_id, tipo)`.
- Migração dos dados (o projeto ainda não está em produção): `agendamento.chefe_id` recebe o chef de `agendamento_chefe` (o de menor id quando houver mais de um); depois `chefe_id` vira `NOT NULL` e `agendamento_chefe` é apagada. Agendamentos antigos ficam com menu nulo e mantêm o `valorTotal`. Por fim saem `chefe.valor_hora` e `chefe.especialidade`.
- Enums por nome, como o resto do schema (V5). Grants do `chefhome_app` nas tabelas novas no mesmo padrão das migrations anteriores. Entidades com o mesmo schema (`MigracoesPostgresTest`).

## Erros

| Situação | Status |
|---|---|
| menu fora das regras, prato de outro chef, restrição inválida | 400 |
| contato no nome ou na descrição do menu | 400 `CONTATO_NAO_PERMITIDO` |
| especialidades fora de 1 a 3, ocasião inválida na busca | 400 |
| menu de outro chef (rotas do chef) ou menu inexistente | 404 |
| mais de 20 menus | 409 |
| menu pausado ou chef não visível na reserva | 409 "Menu indisponível" |
| convidados fora do limite do menu | 409 |
| trocar para menu de outro chef no `PUT` | 409 |

## Testes

- **Menu:** cada regra da tabela (400), prato de outro chef, prato repetido, contato no texto, limite de 20, pausar e ativar, menu pausado fora da página pública, menu de outro chef → 404.
- **Busca:** filtro por ocasião e por faixa de preço; ordenação por `precoAPartirDe`; chef sem menu ativo invisível e com `menu` em `camposPendentes`; especialidades múltiplas no filtro.
- **Agendamento:** fim e valor calculados; convidados abaixo e acima do limite; menu pausado; restrição com quantidade acima do total; observações mascaradas e original guardado; `PUT` trocando para menu do mesmo chef (recalcula) e de outro chef (409); recusar → `RECUSADO` e desistir → `CANCELADO`; chef que recusou não vê mais nada do cliente; chef que desistiu continua vendo o histórico.
- **Cópia do menu:** editar preço e duração do menu não altera agendamento existente.
- **Contrato:** respostas com `chefe`, `menu`, `ocasiao`, `restricoes`, `observacoes`; avaliação sem `chefeId` e com `chefeId` errado.
- **Migração:** `MigracoesPostgresTest` com a V15; teste em Postgres com dados no formato antigo (agendamento com dois chefs, chef com `especialidade` e `valor_hora`) migrando como descrito.
- Os testes atuais que montam agendamentos com vários chefs ou com `valorHora` são reescritos para o modelo novo (helper que cria chef com menu ativo).

## Fora do escopo

- Menu com escolhas por etapa.
- Chef semanal (marmitas), aulas de culinária e outros formatos de serviço.
- Fotos próprias do menu (usa as dos pratos).
- Preço por dia da semana, horário ou temporada; desconto por quantidade.
- Pagamento, sinal e taxa da plataforma (spec de pagamento).
- Histórico do cliente para o chef e lembretes (spec de ferramentas do chef).
