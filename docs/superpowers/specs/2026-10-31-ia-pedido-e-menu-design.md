# IA de pedido, proposta de menu e lista de compras — Design

## Objetivo

Usar IA onde ela tira trabalho chato das pessoas: o cliente descreve o jantar em texto livre e recebe o pedido preenchido e chefs compatíveis; o chef recebe um rascunho de menu para o pedido e uma lista de compras estimada. É o diferencial "o chef responde orçamentos em minutos" e a vitrine de IA aplicada do projeto. Depende de `2026-10-31-menus-pedido-detalhado-design.md` (menus, ocasião, restrições) e de `2026-10-31-contato-protegido-design.md` (filtro de contato).

## Decisões (aprovadas)

- **Spring AI com saída estruturada**, atrás de uma interface própria (`AssistenteIa`), com provedor escolhido por configuração e uma implementação falsa para testes e desenvolvimento. Mesmo padrão do gateway de pagamento e do verificador de telefone.
- **A IA só sugere; uma pessoa sempre confirma.** Nenhum agendamento, menu, mensagem, valor ou agenda muda sem um clique humano.
- **Limite diário por pessoa e IA opcional:** sem limite ou sem provedor, o app funciona igual pelo caminho manual.
- **O modelo interpreta e redige; o código decide.** Busca de chefs, preço, validação e permissão ficam no Java. Toda saída do modelo é tratada como entrada não confiável.

## Regras

### Interpretar pedido (cliente)

**`POST /ia/pedido/interpretar`** `{ "texto": "jantar de aniversário pra 8 pessoas sábado à noite, 2 vegetarianos, até 200 por pessoa, gosto de comida italiana" }` (CLIENTE, conta ativa; texto de 1 a 1000 caracteres, senão **400**).

1. O texto passa por `ModeracaoConteudoService.verificar` (mesma regra de todo texto livre: ódio ou ameaça bloqueiam a conta). O texto exibido (com `***`) é o que vai ao modelo.
2. O modelo devolve um `PedidoInterpretado` estruturado:

```json
{
  "ocasiao": "ANIVERSARIO",
  "quantidadePessoas": 8,
  "data": "2026-11-07",
  "horario": "20:00",
  "restricoes": [{ "tipo": "VEGETARIANO", "quantidade": 2 }],
  "orcamentoMaximoPorPessoa": 200.00,
  "especialidades": ["ITALIANA"],
  "observacoes": null,
  "camposFaltando": ["endereco"]
}
```

3. **Validação no código** (campo inválido vira nulo e entra em `camposFaltando`, sem erro):
   - enums fora das listas (`OcasiaoMenu`, `TipoRestricao`, `Especialidades`);
   - `quantidadePessoas` fora de 1 a 50; restrição com quantidade fora de 1 a `quantidadePessoas`;
   - data no passado ou a mais de 365 dias; horário fora de `HH:mm`;
   - orçamento menor ou igual a zero;
   - datas relativas ("sábado", "amanhã") são resolvidas pelo modelo com a data atual informada no prompt e conferidas aqui.
   - `observacoes` passa pelo `DetectorContato` (contato vira `***`).
4. **Sugestão de chefs pelo código:** a busca existente (`ChefeService`/`ChefeSpecification`) com cidade e UF do endereço mais recente do cliente (sem endereço → sem filtro de cidade e `endereco` em `camposFaltando`), `ocasiao`, primeira especialidade e `valorMaximo = orcamentoMaximoPorPessoa`, ordenada por `notaMedia`; até 10 chefs.
5. Resposta **200** `{ "rascunho": { …PedidoInterpretado validado… }, "chefesSugeridos": [ ChefeResumoDto ] }`. Nada é gravado além do contador de uso. O front abre o formulário de pedido preenchido; o envio continua pelo `POST /agendamentos`.

### Proposta de menu (chef)

**`POST /ia/agendamentos/{id}/proposta-menu`** (CHEFE do agendamento, conta ativa, agendamento `PENDENTE`; de outro chef → **404**; outro status → **409**).

- Entrada do modelo: pratos do chef (id, nome, descrição, ingredientes), `quantidadePessoas`, `ocasiao`, `restricoes`, `observacoes` (já filtradas).
- Saída validada:

```json
{
  "nomeSugerido": "Jantar italiano de aniversário",
  "itens": [{ "pratoId": 7, "etapa": "ENTRADA", "motivo": "leve e vegetariana" }],
  "mensagemParaCliente": "Montei um menu em 4 tempos com opções vegetarianas…",
  "alertas": ["O risoto leva caldo de frutos do mar; há 1 alérgico."],
  "precoReferenciaPorPessoa": 180.00
}
```

  - `itens`: só pratos do próprio chef; id desconhecido ou repetido é descartado; etapa fora de `EtapaMenu` é descartada; no máximo 15. Sem nenhum item válido → **503** `IA_INDISPONIVEL` (resposta inútil conta como falha).
  - `mensagemParaCliente` (até 1000) e `alertas` (até 5, cada um até 200) passam pelo `DetectorContato`.
  - `precoReferenciaPorPessoa` é calculado **pelo código**: preço por pessoa do menu ativo do chef com maior sobreposição de pratos com a proposta (empate: o mais barato); sem menu ativo → nulo. O modelo nunca define preço.
- O chef edita o rascunho e salva pelo `POST /menus` normal (regras do spec de menus). A mensagem é só um texto sugerido para o chef copiar no chat.

### Lista de compras

**`POST /ia/agendamentos/{id}/lista-compras`** (CHEFE do agendamento, conta ativa, agendamento `CONFIRMADO`; outro status → **409**): gera e grava a lista.

- Entrada do modelo: os itens **atuais** do menu do agendamento (nome, descrição, ingredientes de cada prato), `quantidadePessoas`, `restricoes`. Agendamento antigo sem menu → **409** "Agendamento sem menu".
- Saída validada: `itens` de 1 a 80 `{ ingrediente (até 100), quantidade (> 0), unidade (g, kg, ml, l, un, maço, dente, colher, xícara, pitada), categoria (HORTIFRUTI, ACOUGUE, PEIXARIA, LATICINIOS, MERCEARIA, PADARIA, BEBIDAS, OUTROS) }` e `avisos` (até 5). Item inválido é descartado; sem itens válidos → **503**.
- Gravada com `geradaEm` e `versaoPrompt`; gerar de novo substitui a anterior (e conta no limite). Resposta **201** com a lista.

**`GET /agendamentos/{id}/lista-compras`** (cliente dono ou chef do agendamento): a lista gravada, com `"estimativa": true` e `ingredientesInclusos` do agendamento; sem lista → **404**.

### Ingredientes do prato

- `Pratos.ingredientes`: opcional, até 1000 caracteres, em `POST /pratos` e `PUT /pratos/{id}` (multipart `dados`). Passa pelo `DetectorContato` como nome e descrição (contato → **400**, spec de contato protegido).
- Aparece nas respostas de pratos e nos itens de menu. O prompt usa o campo quando preenchido e, sem ele, pede ao modelo que estime pelo nome e descrição.

### Limites e falhas

| Operação | Quem | Limite diário padrão | Propriedade |
|---|---|---|---|
| `INTERPRETAR_PEDIDO` | cliente | 20 | `chefhome.ia.limite.interpretar` |
| `PROPOSTA_MENU` e `LISTA_COMPRAS` (somadas) | chef | 30 | `chefhome.ia.limite.chef` |

- Dia no fuso do servidor. O contador é incrementado **antes** da chamada ao modelo (tentativa conta, inclusive com falha), com `INSERT … ON CONFLICT DO UPDATE … WHERE contagem < limite RETURNING`; sem linha de volta → **429** ProblemDetail `"codigo": "IA_LIMITE_DIARIO"`, detalhe com o horário em que libera.
- Provedor fora, timeout (10 s), JSON que não valida contra o tipo esperado, ou resposta inútil → **503** `"codigo": "IA_INDISPONIVEL"`. Uma nova tentativa automática só em erro de rede, nunca em resposta inválida.
- Moderador nas rotas de IA → **403**.

### Prompts e segurança

- Prompts como templates versionados em `src/main/resources/ia/prompts/` (`interpretar-pedido.st`, `proposta-menu.st`, `lista-compras.st`), cada um com uma constante de versão.
- O texto do usuário vai em uma seção delimitada do prompt e é descrito como dado a interpretar, nunca como instrução. O `AssistenteIa` não expõe ferramentas ao modelo: uma injeção, no máximo, produz um rascunho ruim, barrado pela validação e pela revisão humana.
- **Dados enviados ao provedor:** só o texto filtrado, enums, quantidades, datas e pratos (id interno, nome, descrição, ingredientes). Nunca nome, e-mail, telefone, endereço, foto ou id de conta.
- **Logs:** em `prod`, sem prompt nem resposta completos; só operação, versão do prompt, duração, tokens e resultado (ok, inválido, timeout). Em desenvolvimento, prompt e resposta em `DEBUG`.
- O README documenta o provedor de IA como operador de dados (LGPD) e o que é enviado.

### Configuração

- `chefhome.ia.provedor`: `falso` (padrão em desenvolvimento e teste), `anthropic` ou `openai` (starters do Spring AI); modelo e chave por `${VAR}`. Chaves nunca em log.
- Em `prod` sem provedor configurado, a API **sobe** e as rotas de IA respondem **503** `IA_INDISPONIVEL` (a IA é opcional).
- `AssistenteIaFalso`: determinístico, sem rede; extrai números, ocasiões e restrições por palavras-chave e devolve propostas e listas fixas montadas com os pratos recebidos. Serve aos testes e ao desenvolvimento do front.

### Avaliação

- `src/test/resources/ia/avaliacao/pedidos.yml`: de 15 a 20 textos de pedido reais e variados (gírias, datas relativas, restrições escritas de jeitos diferentes) com os campos esperados.
- `IaAvaliacaoTest` roda só com o perfil Maven `ia-avaliacao` e a chave do provedor no ambiente (fora do CI): chama o provedor real e imprime acerto por campo. Serve para comparar versões de prompt e modelos.

## Componentes

- `AssistenteIa` (interface: `interpretarPedido`, `proporMenu`, `gerarListaCompras`), `AssistenteIaSpringAi` (usa `ChatClient` com `.entity(...)`), `AssistenteIaFalso`.
- Records de entrada e saída (`PedidoInterpretado`, `PropostaMenu`, `ListaCompras`) e validadores puros (`ValidadorPedidoInterpretado`, `ValidadorPropostaMenu`, `ValidadorListaCompras`).
- `IaPedidoService`, `IaMenuService`, `IaListaComprasService` (orquestram: permissão, limite, chamada, validação, filtro, gravação).
- `UsoIaService` (contador atômico) e `IaController`.
- `ChefeService`: método de sugestão reaproveitando a busca; `MenuService`: preço de referência por sobreposição.

## Dados (V19)

- `pratos`: `ingredientes varchar(1000)`.
- `lista_compras`: `agendamento_id` (PK, FK), `itens jsonb NOT NULL`, `avisos jsonb`, `gerada_em timestamp(6) NOT NULL`, `versao_prompt varchar(20) NOT NULL`. Some com o agendamento.
- `uso_ia_diario`: `usuario_id uuid`, `dia date`, `operacao varchar`, `contagem int NOT NULL`; PK `(usuario_id, dia, operacao)`. Linhas com mais de 30 dias são apagadas por uma rotina diária. Some com a conta.
- Grants e entidades no padrão das migrations anteriores.

## Erros

| Situação | Status |
|---|---|
| texto vazio ou acima de 1000 caracteres; ingredientes acima de 1000 | 400 |
| contato nos ingredientes | 400 `CONTATO_NAO_PERMITIDO` |
| ódio ou ameaça no texto | 403 e conta bloqueada |
| moderador nas rotas de IA | 403 |
| agendamento de outro chef; lista inexistente | 404 |
| proposta fora de `PENDENTE`; lista fora de `CONFIRMADO` ou sem menu | 409 |
| limite diário | 429 `IA_LIMITE_DIARIO` |
| provedor fora, timeout, resposta inválida ou inútil, IA desligada | 503 `IA_INDISPONIVEL` |

## Testes

- **Validadores (unitários):** cada regra de descarte (enum inválido, quantidade fora, data passada, prato de outro chef, prato repetido, etapa inválida, item de lista inválido); resposta sem nada válido vira falha.
- **Interpretar:** com o falso, rascunho e chefes sugeridos pela busca existente; sem endereço → sem filtro de cidade e `endereco` faltando; ódio no texto bloqueia; observações com contato mascaradas.
- **Proposta:** só pratos do chef; preço de referência pelo menu mais parecido e nulo sem menu; permissões e status.
- **Lista:** gerar, gravar, substituir; cliente e chef leem; 409 sem menu e fora de `CONFIRMADO`.
- **Limites:** 429 no limite, contagem somada do chef, virada do dia (relógio fixo), concorrência no contador (Postgres: N chamadas simultâneas não passam do limite).
- **Falhas:** provedor lançando exceção, timeout e JSON inválido → 503; nova tentativa só em erro de rede.
- **Privacidade:** captura do que o falso recebe: sem nome, e-mail, telefone, endereço nem id de conta.
- **Configuração:** `prod` sem provedor sobe e responde 503.
- **`MigracoesPostgresTest`** com a V19.

## Fora do escopo

- IA respondendo no chat em nome do chef.
- IA criando pedido, menu ou agendamento sozinha.
- Edição manual da lista de compras e envio dela por mensagem.
- Preço definido pela IA.
- Imagem de prato gerada por IA.
- Agentes com ferramentas que alteram dados.
- Jev ou outro modelo de decisão tipada (pode substituir o `interpretarPedido` depois, atrás da mesma interface).
