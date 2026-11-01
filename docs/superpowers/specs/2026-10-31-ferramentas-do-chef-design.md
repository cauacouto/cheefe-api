# Ferramentas do chef (histórico do cliente, lembretes e folga) — Design

## Objetivo

Fazer o chef preferir tocar o negócio pelo ChefHome em vez de planilha e WhatsApp: guardar o que ele sabe de cada cliente, lembrar os dois lados antes do jantar e respeitar o tempo de deslocamento e compras entre um serviço e outro. É o centro da estratégia "ferramenta primeiro": a plataforma vale para o chef mesmo com os clientes que ele já tem. Depende de `2026-10-31-menus-pedido-detalhado-design.md` (um chef por agendamento, ocasião, restrições e observações) e de `2026-10-31-contato-protegido-design.md` (filtro de contato).

## Decisões (aprovadas)

- **Histórico automático, notas privadas e aviso de "um ano depois":** o chef vê os agendamentos que fez com cada cliente, escreve uma nota que só ele lê e é avisado quando faz um ano de uma ocasião que atendeu.
- O chef só vê o **próprio** histórico com o cliente, nunca o de outros chefs.
- O chef passa a poder **abrir conversa com clientes com quem já tem agendamento concluído** (hoje só o cliente abre). Com desconhecidos, continua proibido.
- **Lembrete 3 dias antes**; o cliente confirma ou ajusta restrições e observações **até 48h antes**. Convidados não mudam por aqui (alteram o preço): continuam pelo chat. O chef recebe um **resumo 1 dia antes**.
- **Folga única** definida pelo chef, aplicada antes e depois de cada agendamento confirmado na checagem de conflito.
- Lembretes e avisos só por notificação no app (a que já existe, com SSE).

## Regras

### Meus clientes e histórico

**`GET /clientes?page=&size=`** (CHEFE, conta ativa): clientes com pelo menos um agendamento `CONFIRMADO` ou `CONCLUIDO` com este chef, ordenados pelo agendamento mais recente. Paginado até 50, no formato das outras listas.

```json
{ "id": "…", "primeiroNome": "Ana", "foto": "…", "totalAgendamentos": 3,
  "ultimoAgendamento": "12-09-2026 19:30", "proximoAgendamento": "20-11-2026 19:30" }
```

**`GET /clientes/{id}/historico`** (CHEFE, conta ativa): só para chef que tem ou teve agendamento (qualquer status) com o cliente; para os demais, **404** (mesma regra de `GET /clientes/{id}/reputacao`).

```json
{
  "cliente": { "id": "…", "primeiroNome": "Ana", "foto": "…" },
  "reputacao": { "notaMedia": 4.8, "totalAvaliacoes": 6, "tags": [ … ] },
  "restricoesFrequentes": [{ "tipo": "VEGETARIANO", "agendamentos": 3 }],
  "nota": { "texto": "Prefere vinho tinto. Cozinha pequena, levar fogareiro.", "atualizadaEm": "…" },
  "agendamentos": [{ "id": 41, "dataHoraInicial": "…", "status": "CONCLUIDO", "menu": { … },
                     "quantidadePessoas": 8, "ocasiao": "ANIVERSARIO", "restricoes": [ … ] }]
}
```

- `agendamentos`: só os deste chef com este cliente, data mais recente primeiro, até os 20 mais recentes.
- `restricoesFrequentes`: por tipo, em quantos agendamentos `CONFIRMADO` ou `CONCLUIDO` deste chef com o cliente o tipo apareceu; ordem decrescente.
- `reputacao`: a mesma de `GET /clientes/{id}/reputacao`.
- `nota`: nula se o chef não escreveu.

**`PUT /clientes/{id}/nota`** `{ "texto": "…" }` (CHEFE, conta ativa, mesma regra de acesso do histórico): até 2000 caracteres; uma nota por par chef e cliente, que substitui a anterior; texto vazio apaga a nota (**204**). Passa por `ModeracaoConteudoService.verificar`: ódio ou ameaça bloqueiam a conta do chef; palavrão e contato viram `***`; o original fica em `textoOriginal` quando difere. Resposta **200** `{ texto, atualizadaEm }` com o texto como ficou.

### Aviso de um ano

- Rotina diária (`chefhome.rotina.aviso-um-ano.cron`, padrão `0 0 9 * * *`, fuso do servidor).
- Para cada agendamento `CONCLUIDO` com `ocasiao` em `ANIVERSARIO`, `JANTAR_ROMANTICO`, `EMPRESA` ou `CELEBRACAO`, cuja data de início mais um ano cai **daqui a 30 dias ou menos** e ainda não passou, e com `aviso_um_ano_em` nulo:
  - se o chef está ativo e não bloqueado, o cliente existe, e os dois **não** têm agendamento `PENDENTE` ou `CONFIRMADO` futuro: notifica o chef (`UM_ANO_DE_CLIENTE`, referência = id do cliente), mensagem "Em {n} dias faz um ano do {ocasião} que você fez para {primeiro nome}";
  - em todos os casos grava `aviso_um_ano_em` (não reavalia depois).
- Agendamento de 29 de fevereiro: um ano depois = 28 de fevereiro.

### Chef abre conversa

- `POST /conversas` passa a aceitar CHEFE com `{ "clienteId": "…" }`: permitido só se o chef tem pelo menos um agendamento `CONCLUIDO` com o cliente (senão **403** "Você só pode iniciar conversa com clientes que já atendeu"). **201** nova, **200** se já existe (a conversa é a mesma do par cliente e chef, como hoje).
- CLIENTE continua com `{ "chefeId": "…" }`, sem mudança. Chef mandando `chefeId` ou cliente mandando `clienteId` → **400**.
- Chef inativo ou bloqueado não abre conversa (mesma regra de envio de mensagem).

### Lembretes

Rodam na rotina de agendamentos que já existe (a cada 15 minutos).

| Lembrete | Quando | Para | Condição | Grava |
|---|---|---|---|---|
| Confirme os detalhes (`LEMBRETE_DETALHES`) | `agora ≥ início − 72h` | cliente | `CONFIRMADO`, `agora < início − 48h`, `lembrete_cliente_em` nulo | `lembrete_cliente_em` |
| Resumo do serviço (`RESUMO_DO_SERVICO`) | `agora ≥ início − 24h` | chef | `CONFIRMADO`, `agora < início`, `resumo_chef_em` nulo | `resumo_chef_em` |

- Confirmado com menos de 48h de antecedência: o lembrete do cliente é pulado (não grava nada; a condição nunca mais fecha). O resumo do chef sai na próxima execução se já estiver dentro das 24h.
- Mensagem do resumo: "Amanhã às {HH:mm}: {menu} para {n} pessoas, {k} restrições" (ou "hoje às" quando for no mesmo dia); referência = id do agendamento.

**Ajustar ou confirmar detalhes** (CLIENTE dono, agendamento `CONFIRMADO`, `agora < início − 48h`; fora do status ou do prazo → **409** "Os detalhes só podem ser alterados até 48h antes"):

- `PATCH /agendamentos/{id}/detalhes` `{ "restricoes": [ … ], "observacoes": "…" }`: mesmas validações do pedido (spec de menus); observações pelo filtro. Substitui os dois campos. Grava `detalhes_confirmados_em = agora`, entra na linha do tempo (`DETALHES_ALTERADOS`) e notifica o chef (`DETALHES_ALTERADOS`).
- `PATCH /agendamentos/{id}/detalhes/confirmar` (sem corpo): grava `detalhes_confirmados_em = agora`, entra na linha do tempo (`DETALHES_CONFIRMADOS`) e notifica o chef (`DETALHES_CONFIRMADOS`). Repetir é idempotente (**200**, não notifica de novo).
- As respostas de agendamento ganham `detalhesConfirmadosEm` (nulo até o cliente agir).
- `PENDENTE` continua editável pelo `PUT /agendamentos/{id}` (spec de menus); estas rotas são só para `CONFIRMADO`.

### Folga entre agendamentos

- `ChefeModel.folgaMinutos`: de 0 a 240, múltiplo de 15, padrão 60. Editável em `PATCH /chefe/me` (fora da regra → **400**) e devolvido em `GET /chefe/me`. Não entra em `camposPendentes` (tem padrão).
- **Conflito entre agendamentos** (criar, `PUT` de horário ou menu, confirmar): o intervalo `[início, fim)` do pedido conflita com um `CONFIRMADO` do mesmo chef se se sobrepõe a `[início_c − folga, fim_c + folga)`, com a folga **atual** do chef. Encostar na borda da folga não conflita.
- A folga **não** se aplica a bloqueios nem a dias fixos (o chef já os define com a margem que quiser).
- **Agenda pública** (`GET /chefe/{id}/agenda`): cada confirmado aparece como `[início − folga, fim + folga)`, recortado na janela e mesclado com o resto, como hoje.
- Mudar a folga não altera nem derruba confirmados existentes; só vale para as próximas checagens. `PENDENTE` continua podendo se sobrepor.
- A trava do chef que já protege confirmação e agenda cobre a leitura da folga.

## Componentes

- **`ClienteDoChefService`** (novo): lista de clientes, histórico, restrições frequentes, nota. `ClienteDoChefController` (novo) com `/clientes`, `/clientes/{id}/historico`, `/clientes/{id}/nota`; a rota de reputação atual continua onde está.
- **`NotaCliente`** (entidade nova) e repositório.
- **`AvisoUmAnoRotina`** (nova), no mesmo padrão de `AgendamentoRotina`/`AvaliacaoRotina` (um id que falha não impede os demais: log e segue).
- **`AgendamentoRotina`**: ganha os dois lembretes.
- **`AgendamentoService`**: `ajustarDetalhes` e `confirmarDetalhes`; checagem de conflito com folga (a consulta de sobreposição de confirmados recebe a folga).
- **`AgendaService`**: agenda pública com folga.
- **`ChatService.abrirConversa`**: ramo do chef.
- **`SecurityConfiguration`**: `POST /conversas` passa a `hasAnyRole(CLIENTE, CHEFE)`; `GET /clientes`, `GET /clientes/*/historico` e `PUT /clientes/*/nota` com `hasRole(CHEFE)`; `PATCH /agendamentos/*/detalhes` e `/detalhes/confirmar` com `hasRole(CLIENTE)`.
- **Enums:** `EventoNotificacao` ganha `LEMBRETE_DETALHES`, `RESUMO_DO_SERVICO`, `DETALHES_ALTERADOS`, `DETALHES_CONFIRMADOS`, `UM_ANO_DE_CLIENTE`; `AcaoAgendamento` ganha `DETALHES_ALTERADOS`, `DETALHES_CONFIRMADOS`.

## Dados (V16)

- `chefe`: `folga_minutos int NOT NULL DEFAULT 60`, check `0..240` e múltiplo de 15.
- `nota_cliente`: `chefe_id` (FK), `cliente_id` (FK), `texto varchar(2000) NOT NULL`, `texto_original varchar(2000)`, `atualizada_em timestamp(6) NOT NULL`; PK `(chefe_id, cliente_id)`.
- `agendamentos`: `lembrete_cliente_em`, `resumo_chef_em`, `detalhes_confirmados_em`, `aviso_um_ano_em` (`timestamp(6)`, nulos).
- Índices parciais para as rotinas: confirmados sem lembrete (`status = 'CONFIRMADO' AND lembrete_cliente_em IS NULL`), sem resumo (`… AND resumo_chef_em IS NULL`), concluídos sem aviso (`status = 'CONCLUIDO' AND aviso_um_ano_em IS NULL`).
- CHECKs recriados com todos os valores atuais mais os novos: `notificacao_evento_check` (os cinco eventos) e o de `agendamento_historico.acao` (`DETALHES_ALTERADOS`, `DETALHES_CONFIRMADOS`).
- Grants do `chefhome_app` no padrão das migrations anteriores; entidades com o mesmo schema.
- Exclusão de conta: `UsuarioService.deletar` apaga as notas sobre o cliente e, dos outros, as notificações que apontam para dados dele: `UM_ANO_DE_CLIENTE` (referência = id do cliente; a mensagem cita o primeiro nome), `RESUMO_DO_SERVICO`, `DETALHES_ALTERADOS` e `DETALHES_CONFIRMADOS` (referência = id do agendamento). O soft delete do chef apaga as notas dele.

## Erros

| Situação | Status |
|---|---|
| nota acima de 2000 caracteres, folga fora da regra, corpo errado em `POST /conversas` | 400 |
| ódio ou ameaça na nota | 403 e conta bloqueada |
| chef abrindo conversa com cliente que não atendeu | 403 |
| histórico ou nota de cliente que o chef nunca atendeu | 404 |
| ajustar ou confirmar detalhes fora de `CONFIRMADO` ou depois de 48h antes | 409 |

## Testes

- **Meus clientes e histórico:** só clientes com confirmado ou concluído; ordenação; o chef B não vê agendamentos do cliente com o chef A; 404 para cliente nunca atendido; restrições frequentes contando por agendamento.
- **Nota:** criar, substituir, apagar com texto vazio; palavrão e contato mascarados com original guardado; ódio bloqueia a conta; notas somem com a conta do cliente e com a do chef.
- **Aviso de um ano:** dentro e fora da janela de 30 dias; só as quatro ocasiões; envio único; não envia (mas grava) com agendamento futuro entre os dois ou chef inativo; 29 de fevereiro.
- **Chef abre conversa:** com cliente atendido (201, e 200 na segunda vez); sem agendamento concluído (403); corpos trocados (400).
- **Lembretes:** janela de 72h e 24h com relógio fixo (`RelogioConfig`); envio único; confirmado com menos de 48h pula o do cliente; resumo "hoje às" e "amanhã às".
- **Detalhes:** ajustar e confirmar até 48h antes; 409 depois e fora de `CONFIRMADO`; notificação ao chef; linha do tempo; confirmar duas vezes não notifica de novo; observações mascaradas.
- **Folga:** conflito dentro da folga; encostar na borda não conflita; folga 0 igual ao comportamento atual; bloqueios e dias fixos sem folga; agenda pública com folga; mudar a folga não afeta confirmados; concorrência entre confirmação e mudança de folga (Postgres).
- `MigracoesPostgresTest` com a V16.

## Fora do escopo

- Lembretes por WhatsApp, SMS ou e-mail.
- Datas especiais cadastradas manualmente pelo chef ou pelo cliente.
- Folga diferente por menu ou por dia.
- Mudança de convidados após a confirmação (continua pelo chat; o spec de pagamento decide se vira fluxo próprio).
- Lembrete de pagamento (spec de pagamento).
- Exportar a lista de clientes.
