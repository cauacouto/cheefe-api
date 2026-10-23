# Agenda do chef (indisponibilidade) — Design

## Objetivo

O chef diz quando não atende, e o cliente não consegue agendar nesses horários. Ficou fora do escopo de `2026-10-22-ciclo-agendamento-design.md` ("chef desmarcar dias na agenda").

## Decisões (aprovadas)

- **Períodos avulsos** (início e fim: um dia, uma semana de férias, uma tarde) **e dias fixos da semana** (ex.: toda segunda).
- Bloqueio ou dia fixo que pega agendamento **CONFIRMADO** → **409** (o chef desiste antes, e o cliente é avisado pelo fluxo atual). **PENDENTES** no período continuam; o chef recusa um por um.

## Regras

### Rotas do chef (CHEFE, chef ativo — `exigirAtivo`)

- `GET /chefe/me/agenda` → `{ bloqueios: [{ id, inicio, fim }], diasFixos: ["MONDAY", ...] }` (bloqueios que ainda não terminaram, por início).
- `POST /chefe/me/agenda/bloqueios` `{ inicio, fim }` (formato `dd-MM-yyyy HH:mm`, como os agendamentos) → **201** `{ id, inicio, fim }`.
  - `fim > inicio`, `fim` no futuro, duração até **365 dias** → senão **400**.
  - Sobrepõe agendamento CONFIRMADO do chef → **409** "Você tem agendamento confirmado nesse período" (com o id e o horário dos que conflitam no `detail` ou numa propriedade).
  - Bloqueios podem se sobrepor entre si (não é erro); encostar (fim de um = início do outro) não conflita com agendamento.
  - Até **100** bloqueios futuros por chef → senão **409**.
- `DELETE /chefe/me/agenda/bloqueios/{id}` → **204**; de outro chef → **404**.
- `PUT /chefe/me/agenda/dias-fixos` `{ dias: ["MONDAY", ...] }` (`DayOfWeek`, sem repetição; lista vazia limpa) → **200** com a lista.
  - Algum agendamento CONFIRMADO **futuro** do chef cai num dia novo → **409** (mesma mensagem).

### O que fica indisponível

- Um horário `[inicio, fim)` de agendamento conflita com:
  - bloqueio `[b.inicio, b.fim)` que se sobrepõe (encostar não conflita);
  - dia fixo: qualquer parte do agendamento cai nesse dia da semana (no fuso do servidor, como os demais prazos).
- Validado em `POST /agendamentos` (todos os chefs escolhidos), `PUT /agendamentos/{id}` (novo horário) e `PATCH /agendamentos/{id}/confirmar` (o chef que confirma) → **409** "Chef indisponível nesse horário" (cita o nome do chef quando são vários).
- A rotina não expira nem recusa nada por causa da agenda.

### Consulta pública

- `GET /chefe/{id}/agenda?de=&ate=` (qualquer logado; datas `dd-MM-yyyy`; padrão hoje → hoje + 30 dias; janela máxima **90 dias**, senão **400**) → `[{ inicio, fim }]`: os períodos indisponíveis dentro da janela, juntando bloqueios, dias fixos (expandidos em dias inteiros) e agendamentos **confirmados** do chef, ordenados e **mesclados** (períodos que se sobrepõem ou encostam viram um só). Sem motivo, sem id, sem dizer o que é agendamento.
- Chef não visível (bloqueado, inativo, suspenso, oculto, perfil incompleto) → **404**, como a lista pública de avaliações.

## Dados (V11)

- `agenda_bloqueio`: `id bigint identity PK`, `chefe_id uuid not null` FK `chefe(id)` **ON DELETE CASCADE**, `inicio timestamp(6) not null`, `fim timestamp(6) not null`, `criado_em timestamp(6) not null`, `CHECK (fim > inicio)`, índice `(chefe_id, fim)`.
- `agenda_dia_fixo`: `chefe_id uuid` FK cascade, `dia_semana varchar(16)` com CHECK dos 7 valores, PK `(chefe_id, dia_semana)`.
- Entidades com `@OnDelete(CASCADE)` (H2 = Postgres); `MigracoesPostgresTest` compara.

## Concorrência

- Criar bloqueio/dias fixos e confirmar agendamento do mesmo chef ao mesmo tempo: usar a mesma trava do chef que a confirmação já usa (`ChefeRepository.travar`, ordem por id) antes de checar conflito, para não nascer um confirmado dentro de um bloqueio recém-criado.

## README

Seção "Agenda do chef": rotas, regras de conflito, consulta pública; linha na tabela "Rotas por papel"; "Ciclo do agendamento" cita o 409 de indisponível.

## Testes

- CRUD dos bloqueios e dias fixos; 400/404/409; agendar, editar e confirmar dentro de bloqueio/dia fixo → 409, encostando → ok; dia fixo pegando só parte do agendamento → 409; pendente no período continua; consulta pública mesclada, sem detalhes, janela 90 dias, chef oculto 404; corrida confirmar × bloquear (Postgres, se viável).

## Fora do escopo

Horário de atendimento por dia (ex.: só das 18h às 23h); feriados; fuso por chef; sincronizar com Google Agenda.
