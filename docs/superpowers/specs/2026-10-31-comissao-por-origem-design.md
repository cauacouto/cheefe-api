# Comissão por origem do cliente e página pública do chef — Design

## Objetivo

Tirar do chef o motivo para fugir da plataforma: clientes que **ele** traz pagam comissão menor que clientes que a plataforma traz. Para isso o chef ganha um link público para divulgar (Instagram, cartão, WhatsApp) e o ChefHome passa a registrar a origem de cada relação cliente e chef. Depende de `2026-10-31-pagamento-com-retencao-design.md`, cuja taxa única é substituída aqui.

## Decisões (aprovadas)

- **Taxas:** 15% para clientes vindos da plataforma (`PLATAFORMA`) e 5% para clientes trazidos pelo chef (`CHEF`). Os 5% cobrem processamento e infraestrutura.
- **A origem vale para sempre no par cliente e chef** e é definida pelo **primeiro contato**. O chef não converte em `CHEF` um cliente que o encontrou pela busca.
- **Página pública do chef, sem login**, com convite que acompanha a pessoa pelo login do Google.
- O cliente não vê a origem: é regra comercial entre chef e plataforma.

## Regras

### Taxas

- `chefhome.comissao.taxa-plataforma` (padrão 15) e `chefhome.comissao.taxa-chef` (padrão 5), cada uma de 0 a 50; fora disso a API não sobe. Substituem `chefhome.pagamento.taxa-percentual` do spec de pagamento.
- **O agendamento grava `origemCliente` ao ser criado**, copiada do vínculo do par. Ao criar o repasse, a taxa vem dessa cópia, nunca do vínculo: assim o repasse não depende de o cliente ou o vínculo ainda existirem (conta apagada e anonimizada, spec de pagamento).
- O repasse grava `origemCliente` e `taxaPercentual`; mudar a configuração depois não altera repasses existentes.

### Vínculo cliente e chef

- Um registro por par `(cliente, chef)`: `origem` (`CHEF` ou `PLATAFORMA`), `criadoEm`.
- Nasce no **primeiro contato**, o que vier primeiro:
  - `POST /conversas` feito pelo cliente para aquele chef;
  - `POST /agendamentos` do cliente com menu daquele chef.
- `origem = CHEF` se a requisição traz um convite válido **daquele chef**; senão `PLATAFORMA`.
- Depois de criado, **nunca muda**: um convite novo do mesmo chef não altera um vínculo `PLATAFORMA`, e um convite de outro chef não afeta este par.
- Criação idempotente e segura contra corrida: PK `(cliente_id, chefe_id)` com `INSERT … ON CONFLICT DO NOTHING`; quem perde a corrida lê o vínculo gravado.
- A conversa aberta pelo chef (spec de ferramentas do chef) exige agendamento concluído, então o vínculo já existe nesse caso.
- Conta do cliente apagada: os vínculos dele somem (o repasse já gravou o que precisava). Conta do chef apagada: os vínculos dele somem.

### Apelido e página pública

- `chefe.apelido`: único, de 3 a 30 caracteres, `[a-z0-9-]`, sem começar nem terminar com hífen, e fora de uma lista de reservados (`admin`, `api`, `publico`, `moderacao`, `chefhome`, `login`, `cadastro`). Fora disso → **400**; já usado por outro chef → **409**.
- Definido e trocado em `PATCH /chefe/me`. Entra em `camposPendentes()` (chef sem apelido não aparece na busca), com sugestão automática no onboarding a partir do nome (sem acento, minúsculas, hífens, sufixo numérico se já existir). Trocar o apelido faz o link antigo parar de funcionar (sem redirecionamento).
- **`GET /publico/chefes/{apelido}`** (sem login): `{ apelido, nome, foto, especialidades, cidade, estado, anosDeExperiencia, senioridade, idiomas, notaMedia, totalAvaliacoes, precoAPartirDe, menus: [ativos, com pratos], avaliacoes: [as 5 publicadas mais recentes, formato da lista pública atual] }`. Nunca e-mail, telefone ou id interno de conta além do necessário para reservar (`chefeId`). Chef não visível na busca → **404**. Busca do apelido sem diferenciar maiúsculas.
- Resposta com `Cache-Control: public, max-age=60`.

### Convite

- **`POST /publico/chefes/{apelido}/convite`** (sem login): o front chama ao abrir a página. Chef não visível → **404**. Resposta **204** com o cookie:
  - `convite = <chefeId>.<expiraEm>.<assinatura>`, assinatura HMAC-SHA256 com o segredo `CHEFHOME_SEGREDO_CONVITE` (obrigatório em `prod`, pelo menos 32 caracteres; valor fixo de desenvolvimento nos outros perfis);
  - `HttpOnly`, `SameSite=Lax`, `Secure` conforme `AUTH_COOKIE_SECURE`, `Path=/`, validade de 30 dias.
- Abrir o link de outro chef substitui o cookie (um convite por vez).
- No primeiro contato, o serviço lê o cookie: assinatura inválida, expirado ou de outro chef → tratado como ausente (sem erro). O cookie **não** é apagado depois do uso (vale para o primeiro contato com aquele chef; vínculos já existentes o ignoram).
- O cookie atravessa o login com Google porque o fluxo BFF já trabalha com front e API no mesmo site (`SameSite=Lax`, ver README "Login com Google").

### O que o chef vê

- **`GET /chefe/me/convite`** (CHEFE): `{ apelido, link, clientesPorConvite, agendamentosPorConvite }`; `link` = `{FRONT_URL}/c/{apelido}`; contagens de vínculos `CHEF` do chef e de agendamentos `CONCLUIDO` desses clientes com ele.
- Agendamentos recebidos (`GET /agendamentos/recebidos` e detalhe) e repasses (`GET /chefe/me/repasses`) ganham `origemCliente`; os repasses também `taxaPercentual`.
- Nenhuma resposta para o cliente expõe a origem.

### Segurança

- `SecurityConfiguration`: `permitAll` só para `GET /publico/chefes/*` e `POST /publico/chefes/*/convite`. O resto continua autenticado.
- O `POST` do convite é público e sem corpo; não altera dado nenhum no banco (só define o cookie), então não precisa de proteção CSRF além do `SameSite`.

## Componentes

- **`VinculoClienteChefe`** (entidade) e `VinculoService.garantir(clienteId, chefeId, convite)`, chamado por `ChatService.abrirConversa` (ramo do cliente) e `AgendamentoService.criar`, antes de gravar.
- **`ConviteCookie`** (componente): gera e valida o cookie assinado; leitura via `HttpServletRequest` repassada pelo controller ao serviço (o serviço não depende de servlet).
- **`PublicoController`** e **`PaginaPublicaService`**.
- **`ApelidoService`**: validação, reservados e sugestão.
- **`PoliticaComissao`** (classe pura): origem → taxa, a partir da configuração; usada pelo `PagamentoService` ao criar o repasse.
- `ChefeMapper`/DTOs com `apelido`; `ChefePerfilDto` e `camposPendentes` atualizados.

## Dados (V18)

- `chefe`: `apelido varchar(30)`, índice único em `lower(apelido)` onde não nulo.
- `cliente_chefe_vinculo`: `cliente_id` (FK), `chefe_id` (FK), `origem varchar NOT NULL`, `criado_em timestamp(6) NOT NULL`; PK `(cliente_id, chefe_id)`; índice em `(chefe_id, origem)`.
- `agendamentos`: `origem_cliente varchar` (backfill pelo vínculo do par; `PLATAFORMA` nos anonimizados).
- `repasse`: `origem_cliente varchar NOT NULL` (backfill `PLATAFORMA` nos existentes).
- Backfill: um vínculo `PLATAFORMA` para cada par que já tem conversa ou agendamento, com `criado_em` = o primeiro deles.
- Apelido dos chefs existentes: gerado pela mesma regra da sugestão durante a migração (SQL equivalente: nome sem acento, minúsculas, hífens, sufixo numérico em colisão), para ninguém sumir da busca.
- Grants e entidades no padrão das migrations anteriores (`MigracoesPostgresTest`).

## Erros

| Situação | Status |
|---|---|
| apelido fora do formato ou reservado | 400 |
| apelido já usado | 409 |
| página pública ou convite de chef inexistente ou não visível | 404 |
| cookie de convite inválido ou expirado | sem erro: ignorado |

## Testes

- **Origem:** primeiro contato por conversa e por agendamento, com e sem convite; convite de outro chef; vínculo `PLATAFORMA` existente + convite novo continua `PLATAFORMA`; convite expirado e com assinatura alterada ignorados; corrida de dois primeiros contatos simultâneos (Postgres) grava um vínculo só.
- **`PoliticaComissaoTest`** e repasse com 15% e 5%; taxa gravada; configuração fora de 0 a 50 impede a subida.
- **Página pública:** sem login; sem e-mail e telefone; só menus ativos e 5 avaliações; 404 para chef não visível e apelido inexistente; apelido sem diferenciar maiúsculas; `Cache-Control`.
- **Apelido:** formato, reservados, único (409), sugestão com sufixo em colisão, chef sem apelido fora da busca, troca invalida o link antigo.
- **Convite atravessando o login:** no fluxo completo do Google que já existe (`GoogleLoginFluxoCompletoIntegrationTest`), abrir a página, logar e fazer o primeiro pedido gera vínculo `CHEF`.
- **Visibilidade:** `origemCliente` nas respostas do chef e ausente nas do cliente; `GET /chefe/me/convite` com as contagens.
- **Segurança:** só as duas rotas públicas sem login; as demais seguem 401.
- **Migração:** backfill de vínculos e apelidos (`MigracoesPostgresTest` com a V18).

## README

Seções "Página pública e convite" e "Comissão": as duas taxas, a regra do primeiro contato, o cookie de convite e o segredo `CHEFHOME_SEGREDO_CONVITE` na tabela de variáveis.

## Fora do escopo

- Mensalidade ou plano pago para o chef.
- Detecção de fraude (chef reservando com uma conta de cliente própria, convites em massa).
- Redirecionamento de apelido antigo.
- Programa de indicação entre clientes.
- Taxa negociada por chef.
- Página pública com SEO renderizada no servidor (o front decide).
