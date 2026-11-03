# 🍽️ ChefHome — Agendamento de Refeições em Domicílio

---


![Java](https://img.shields.io/badge/Java-17-000?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-000?style=for-the-badge&logo=springboot)
![Docker](https://img.shields.io/badge/Docker-000?style=for-the-badge&logo=docker)
![Maven](https://img.shields.io/badge/Maven-000?style=for-the-badge&logo=apachemaven)
![MySQL](https://img.shields.io/badge/MySQL-000?style=for-the-badge&logo=mysql)

 ---
> Plataforma que conecta chefs profissionais a clientes que desejam experiências gastronômicas exclusivas no conforto de casa.
 
> ⚠️ **Projeto em andamento** — funcionalidades ainda estão sendo desenvolvidas.
 
---
 
## 📋 Sobre o Projeto
 
O **ChefHome** é uma aplicação web que permite que usuários agendem refeições (almoço, jantar, brunch, etc.) preparadas por chefs cadastrados diretamente em suas residências. Chefs podem gerenciar sua agenda, cardápio e disponibilidade, enquanto clientes encontram e reservam o profissional ideal para cada ocasião.
 
---
 
## ✨ Funcionalidades Planejadas
 
### 👤 Para Usuários (Clientes)
- Cadastro e autenticação
- Busca e visualização de chefs disponíveis
- Agendamento de refeições (almoço, jantar, brunch, etc.)
- Escolha de data, horário e número de pessoas
- Histórico de agendamentos
- Avaliação do chef após o serviço
 
### 👨‍🍳 Para Chefs
- Cadastro com perfil profissional
--

## 🚀 Tecnologias Utilizadas
 
**Backend**
- Java 17+
- Spring Boot

---

## ⚙️ Configuração

O `src/main/resources/application.yml` não é versionado. Use o modelo [`application.example.yml`](application.example.yml) e defina as variáveis de ambiente:

| Variável | Uso |
|---|---|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | OAuth Client do Google (tipo **Web**) |
| `FRONT_URL` | URL do front, sem barra no final (ex.: `http://localhost:5173`) |
| `AUTH_COOKIE_SECURE` | `false` só em dev via HTTP; `true` em produção |
| `JWT_KEY` | segredo aleatório longo (`openssl rand -base64 48`) |
| `MODERACAO_EMAILS` | e-mails (separados por vírgula) que entram como moderação ao logar com o Google; não existem contas de moderação no banco |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | conexão da API: `jdbc:postgresql://host:5432/chefe_api?currentSchema=chefhome`, usuário **`chefhome_app`** (só manipula dados) |
| `FLYWAY_URL` / `FLYWAY_USER` / `FLYWAY_PASSWORD` | **só no passo de migração**, nunca no ambiente da API: usuário `chefhome_migracao` |
| `R2_ENDPOINT`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_PUBLIC_URL` | bucket de fotos dos pratos (Cloudflare R2 ou outro compatível com S3, com leitura pública) |
| `CHEFHOME_PEPPER_TELEFONE` | opcional enquanto a verificação de telefone está parada; segredo aleatório longo (`openssl rand -base64 32`; em `prod`, se definido, pelo menos 32 caracteres) do hash dos telefones banidos; trocar depois esvazia a lista de banidos |
| `CHEFHOME_TELEFONE_FALSO` | só em desenvolvimento: `true` liga a verificação falsa (código `000000`) para testar o fluxo de `/telefone`; em `prod` a API não sobe com ela |

A aplicação **não sobe** sem o registro do Google e sem `FRONT_URL`.

### 🗄️ Banco de dados (privilégio mínimo)

O schema é versionado com **Flyway** (`src/main/resources/db/migration`) e fica no schema `chefhome`. São dois usuários:

| Usuário | Pode | Usado por |
|---|---|---|
| `chefhome_migracao` | criar/alterar/apagar tabelas no schema `chefhome` | só o passo `flyway:migrate` |
| `chefhome_app` | só `SELECT`/`INSERT`/`UPDATE`/`DELETE` | a API (`ddl-auto: validate`: ela não sobe se o schema divergir das entidades) |

Mesmo que a API seja comprometida, ela não consegue criar, alterar ou apagar tabelas, nem mexer no histórico de migrations.

#### Local com Docker (recomendado)

```bash
cp .env.example .env              # preencha as 3 senhas (openssl rand -base64 24); .env não é versionado
docker compose up -d postgres     # 1ª subida cria banco, schema chefhome e os dois usuários
docker compose run --rm migracao  # aplica as migrations como chefhome_migracao (repita a cada V novo)
```

A API (IntelliJ) conecta em `jdbc:postgresql://localhost:5433/chefe_api?currentSchema=chefhome` com `DATABASE_USERNAME=chefhome_app` e `DATABASE_PASSWORD` = `CHEFHOME_SENHA_APP` do `.env`. A porta é **5433** para não conflitar com um Postgres instalado na máquina (`CHEFHOME_PORTA`). Para recomeçar do zero: `docker compose down -v` (**apaga todos os dados**).

Confira `spring.jpa.hibernate.ddl-auto: validate` também no seu `application-prod.yml`: um `update` lá sobrescreve o `validate` e a API voltaria a tentar alterar o schema.

#### Neon (ou outro Postgres gerenciado)

Não há superusuário: rode o setup como o dono do banco (`neondb_owner`), que tem `CREATEROLE`. O script detecta o banco gerenciado e segue (só avisa que não pode desligar o log de comandos, que o provedor não expõe).

```bash
read -rs -p "senha chefhome_migracao: " SENHA_MIGRACAO; echo
read -rs -p "senha chefhome_app: " SENHA_APP; echo
psql "postgresql://neondb_owner@<host-do-neon>/neondb?sslmode=require" -v ON_ERROR_STOP=1 \
     -v senha_migracao="$SENHA_MIGRACAO" -v senha_app="$SENHA_APP" -v nome_banco=neondb \
     -f db/setup/01_usuarios.sql
FLYWAY_URL="jdbc:postgresql://<host-do-neon>/neondb?sslmode=require" \
FLYWAY_USER=chefhome_migracao FLYWAY_PASSWORD="$SENHA_MIGRACAO" ./mvnw flyway:migrate
unset SENHA_MIGRACAO SENHA_APP
```

API: `DATABASE_URL=jdbc:postgresql://<host-do-neon-pooler>/neondb?sslmode=require&currentSchema=chefhome`, usuário `chefhome_app`. Migrations usam o host **direto** (sem `-pooler`); a API pode usar o pooler.

#### VPS

O mesmo `docker-compose.yml`: senhas fortes no `.env` (permissão `600`), porta só em `127.0.0.1` (já é o padrão — **nunca** exponha a 5432 para a internet; a API na mesma máquina conecta localmente), e `docker compose run --rm migracao` como passo do deploy antes de subir a nova versão da API.

#### Sem Docker (Postgres instalado)

```bash
read -rs -p "senha chefhome_migracao: " SENHA_MIGRACAO; echo
read -rs -p "senha chefhome_app: " SENHA_APP; echo
dropdb -U postgres --if-exists chefe_api && createdb -U postgres chefe_api   # ATENÇÃO: apaga os dados
psql -U postgres -d chefe_api -v ON_ERROR_STOP=1 \
     -v senha_migracao="$SENHA_MIGRACAO" -v senha_app="$SENHA_APP" -v nome_banco=chefe_api \
     -f db/setup/01_usuarios.sql
# SEMPRE com FLYWAY_USER=chefhome_migracao: rodando como postgres, as tabelas nascem sem permissão para a API
FLYWAY_URL=jdbc:postgresql://localhost:5432/chefe_api \
FLYWAY_USER=chefhome_migracao FLYWAY_PASSWORD="$SENHA_MIGRACAO" ./mvnw flyway:migrate
unset SENHA_MIGRACAO SENHA_APP
```

**Deploy:** rode a migração (`docker compose run --rm migracao` ou `./mvnw flyway:migrate` com as variáveis `FLYWAY_*`) **antes** de subir a nova versão da API. Se a migration falhar, a versão anterior continua no ar.

**Mudou uma entidade?** Crie `src/main/resources/db/migration/V{n}__descricao.sql` com o SQL equivalente. Migrations já aplicadas **nunca** são editadas. O teste `MigracoesPostgresTest` (Postgres real via Testcontainers; roda no CI e localmente com Docker ligado) falha se o schema não bater com as entidades ou se a API ganhar algum privilégio de DDL.

### 🧪 Testes e CI

```bash
./mvnw clean test -Dspring.profiles.active=test
```

- A configuração de teste fica em `src/test/resources/application-test.yml` (H2 em memória, credenciais fictícias) e não entra no jar.
- Com o **Docker ligado**, os testes também rodam num Postgres real (Testcontainers): migrations, privilégios do `chefhome_app` e concorrência do chat. Sem Docker eles são **pulados**, e pulado não conta como verificado.
- A CI (`.github/workflows/rotinaCi.yml`) roda em todo push na `main`/`develop` e em todo Pull Request: build com os testes e uma varredura do histórico inteiro com o **gitleaks**, que falha se algum segredo for commitado.

### 🐳 Imagem da API

```bash
docker build -t chefhome-api .
docker run --rm -p 8080:8080 --env-file .env.api chefhome-api
```

A imagem roda só com a JRE e com um usuário sem privilégios; os testes não rodam no build (rodam na CI). Toda a configuração entra por variáveis de ambiente (tabela acima), num arquivo como o `.env.api` do exemplo, que **não** é versionado nem copiado para a imagem.

### 🔐 Login com Google (BFF)

O login é conduzido pela própria API (Authorization Code + PKCE). O navegador nunca recebe tokens do Google, só cookies HttpOnly.

1. No Google Cloud Console, crie um OAuth Client ID tipo **Web** com *Authorized redirect URI* `http://localhost:8080/login/oauth2/code/google` (e `https://<api-de-produção>/login/oauth2/code/google`).
2. Atrás de proxy/PaaS com TLS, mantenha `server.forward-headers-strategy: framework`.
3. Front e API precisam ser *same-site* (ex.: `app.dominio.com` e `api.dominio.com`), porque os cookies são `SameSite=Lax`.

Fluxo para o front:

| Passo | Chamada |
|---|---|
| Entrar | navegar para `{API}/oauth2/authorization/google` |
| Conta existe | volta em `{FRONT_URL}/` com cookie `accessToken` |
| Conta nova | volta em `{FRONT_URL}/cadastro`; `GET /auth/register` traz `email`, `nome`, `foto`; `POST /auth/register` (JSON, sem `email`; a foto de perfil é a do Google) cria a conta e já loga |
| Erro/cancelamento | volta em `{FRONT_URL}/login?erro=google` |
| Sessão | `GET /auth/me` → `{ id, email, tipo }` ou 401 |
| Sair | `POST /auth/logout` |

Todas as chamadas do front precisam de `credentials: 'include'`.

### 🛡️ Rotas por papel

> **Mudança de contrato (menus).** O chef deixou de ter `valorHora` e uma única `especialidade` (agora `especialidades`, de 1 a 3) e cada preço vem de um menu. `POST /agendamentos` recebe `menuId`, `ocasiao`, `restricoes` e `observacoes` no lugar de `chefeIds` e `dataHoraFinal`, e um agendamento tem **um** chef: nas respostas, `chefes` (lista) virou `chefe` (objeto). A migration V15 converte os dados antigos: de vários chefs fica o de menor id, quem recusou ou desistiu continua sendo o chef do pedido, agendamento sem nenhum chef nem histórico é apagado (com as denúncias dele), e os antigos ficam sem `menu` e mantêm o `valorTotal`.

Toda identidade vem do cookie de sessão: nenhuma rota recebe id ou e-mail de quem está logado. Papel errado → **403**; recurso de outra pessoa → **404** (igual a inexistente); ação não permitida no status atual → **409**.

Listas paginadas respondem `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`; a ordenação é a do servidor (o parâmetro `sort` é ignorado, exceto na busca de chefs).

| Recurso | Rota | Papel |
|---|---|---|
| Sessão | `GET /auth/me` | qualquer logado |
| Notificações | `GET /notificacoes?somenteNaoLidas=`, `GET /notificacoes/nao-lidas/contagem`, `PATCH /notificacoes/{id}/lida`, `PATCH /notificacoes/lidas` (sempre só as próprias) | qualquer logado |
| Tempo real | `GET /eventos` (SSE, `text/event-stream`; eventos `mensagem`, `mensagem_editada`, `mensagem_apagada`, `mensagens_lidas` e `notificacao`) | qualquer logado |
| Telefone | `POST /telefone/codigo`, `POST /telefone/verificar`: **503** por enquanto (verificação parada; veja "Telefone verificado") | CLIENTE, CHEFE |
| Usuário | `GET` · `PUT` · `DELETE /usuario/me` (o e-mail não é editável; o DELETE encerra a sessão e apaga também as conversas, mensagens e denúncias de mensagens do cliente, e as avaliações dos agendamentos dele, com as notas que ele deu saindo da média dos chefs); `PUT /usuario/me/foto` (multipart `foto`) | CLIENTE |
| Endereços | `GET` · `POST /usuario/enderecos` | CLIENTE |
| Chefs | `GET /chefe?page=&size=&sort=&cidade=&estado=&especialidade=&ocasiao=&valorMinimo=&valorMaximo=` (filtros opcionais e combináveis: `cidade` exige `estado` e ignora acento, maiúsculas e espaços, senão **400**; `estado` sozinho filtra pela UF; especialidade ou ocasião inválida → **400**; `especialidade` filtra quem a tem na lista; `ocasiao` e a faixa `valorMinimo`/`valorMaximo` olham os menus **ativos**: o chef aparece se algum menu tem a ocasião e o preço por pessoa dentro da faixa, os dois no mesmo menu; ordenação só por `nome`, `precoAPartirDe` (menor preço por pessoa entre os menus ativos), `anosDeExperiencia` ou `notaMedia` — esta sempre da maior para a menor, com os "Novo" por último, e vale sozinha se vier junto com `precoAPartirDe`), `GET /chefe/destaque` (mais agendados primeiro, sem contar os recusados, cancelados e expirados); os dois paginados até 50, só com chefs visíveis (ativos, disponíveis, perfil completo — inclui ao menos um menu ativo —, não suspensos e não ocultados por denúncias) e com `especialidades`, `precoAPartirDe`, `notaMedia` e `totalAvaliacoes` | qualquer logado |
| Avaliações de um chef | `GET /chefe/{id}/avaliacoes?page=&size=` (publicadas, mais recentes primeiro, até 50) e `GET /chefe/{id}/reputacao` (veja "Avaliações"); chef bloqueado ou inativo → 404 | qualquer logado |
| Agenda do chef | `GET /chefe/me/agenda`, `POST /chefe/me/agenda/bloqueios` `{inicio, fim}`, `DELETE /chefe/me/agenda/bloqueios/{id}`, `PUT /chefe/me/agenda/dias-fixos` `{dias}` (veja "Agenda do chef") | CHEFE |
| Agenda de um chef | `GET /chefe/{id}/agenda?de=&ate=` (datas `dd-MM-yyyy`, janela de até 90 dias): quando o chef não atende, sem motivo; chef não visível → 404 | qualquer logado |
| Perfil do chef | `GET /chefe/me` (inclui `especialidades`, `precoAPartirDe`, `perfilCompleto` e `camposPendentes`, que lista `menu` quando o chef não tem nenhum menu ativo); `PATCH /chefe/me` (`especialidades` é uma lista de 1 a 3 sem repetição, senão **400**; não há mais `valorHora`: o preço é de cada menu; inclui `cidade` e `estado` (UF) de origem, obrigatórios para aparecer na busca e ser agendado: o chef só atende nessa cidade; `folgaMinutos` (0 a 240, múltiplo de 15, padrão 60; fora da regra → **400**) é a folga entre agendamentos confirmados, devolvida também no `GET /chefe/me`); `PUT /chefe/me/foto` (multipart `foto`); `PATCH /chefe/me/inativar` (pausa: some da busca e não recebe agendamentos novos) e `PATCH /chefe/me/ativar` (volta); `DELETE /chefe/me` (apaga a conta: não loga nem opera mais) | CHEFE |
| Pratos | `GET /pratos?page=&size=` (paginado, até 50) e `GET /pratos/chefe/{chefeId}`: só pratos de chefs visíveis na busca (chef pausado, suspenso, banido ou ocultado por denúncias não aparece) | qualquer logado |
| Meus pratos | `POST /pratos` (multipart `dados` + `foto`), `PUT /pratos/{id}`, `GET /pratos/meus` (todos os do chef, mesmo pausado) | CHEFE |
| Menus do chef | `POST /menus` (201), `PUT /menus/{id}`, `PATCH /menus/{id}/pausar`·`ativar`, `GET /menus/meus` (todos, inclusive os pausados). Corpo: `{ nome (até 100), descricao? (até 1000), precoPorPessoa (> 0), minimoConvidados, maximoConvidados (1 a 50, máximo >= mínimo), duracaoMinutos (60 a 720, múltiplo de 30), ingredientesInclusos, ocasioes (1 ou mais, sem repetir), itens (1 a 15) }`, cada item `{ pratoId, etapa (COUVERT, ENTRADA, PRINCIPAL, SOBREMESA), ordem }` com pratos do próprio chef e sem repetir prato, senão **400**. Contato no nome ou na descrição → **400** `CONTATO_NAO_PERMITIDO`. Até 20 menus por chef (**409** além disso); menu de outro chef ou inexistente → **404**. Editar um menu não altera agendamentos já feitos | CHEFE |
| Menus de um chef | `GET /chefe/{id}/menus` (só os ativos): chef que não aparece na busca → **404**. Se o chef pausa o último menu ativo, ele sai da busca (perde o `menu` do perfil completo), então esta rota e `GET /chefe/{id}/agenda` respondem **404** | qualquer logado |
| Agendamentos do cliente | `POST /agendamentos` (o cliente reserva um **menu**: `{ menuId, dataHoraInicial, enderecoId, quantidadePessoas, ocasiao, restricoes?, observacoes? }`; o chef vem do menu, `dataHoraFinal = dataHoraInicial + duracaoMinutos` e `valorTotal = precoPorPessoa × quantidadePessoas`. `ocasiao` é obrigatória (`JANTAR_ROMANTICO`, `ANIVERSARIO`, `FAMILIA`, `AMIGOS`, `EMPRESA`, `CELEBRACAO`, `OUTRA`) e não precisa ser uma das ocasiões do menu. `restricoes` é uma lista de `{ tipo, quantidade }` (`VEGETARIANO`, `VEGANO`, `SEM_GLUTEN`, `SEM_LACTOSE`, `ALERGIA_FRUTOS_DO_MAR`, `ALERGIA_AMENDOIM`, `ALERGIA_CASTANHAS`, `ALERGIA_OVO`): sem repetir tipo e cada quantidade de 1 até `quantidadePessoas` (a soma pode passar do total), senão **400**. `observacoes` (até 1000) passa pelo filtro do chat: palavrão e contato viram `***`, ódio ou ameaça bloqueiam a conta; o texto original fica só para a moderação e nunca aparece nas respostas. **404** se o menu não existe; **409** "Menu indisponível" se o menu está pausado ou o chef não aparece na busca, e "Este menu atende de {min} a {max} pessoas" fora do limite. As respostas trazem `chefe` — `{ id, nome, imageUrl, especialidades }` —, `menu` — `{ id, nome, precoPorPessoa, duracaoMinutos, ingredientesInclusos }`, a cópia gravada no pedido: editar o menu depois não muda o agendamento —, `ocasiao`, `restricoes` e `observacoes`; agendamentos de antes dos menus vêm com `menu` e `ocasiao` nulos. O endereço precisa ser do cliente e da mesma cidade e UF de origem do chef, senão **409** "Chef {nome} não atende em {cidade}" — vale também ao trocar o endereço no `PUT` e ao confirmar; **409** se o chef já tiver um agendamento confirmado que se sobreponha — pedidos pendentes podem se sobrepor, e encostar, como 18h–20h e 20h–22h, não conflita), `GET /agendamentos/meus?page=&size=` (paginado, até 50, data mais distante primeiro), `PUT /agendamentos/{id}` (só `PENDENTE`; todos os campos opcionais: `dataHoraInicial`, `enderecoId`, `quantidadePessoas`, `menuId`, `ocasiao`, `restricoes` — lista vazia limpa —, `observacoes` — em branco limpa. Trocar o menu ou a quantidade de pessoas (um `menuId` ou uma quantidade **diferentes** dos atuais; mandar os mesmos valores, ou só mudar o endereço, não mexe na cópia nem no valor) recopia o menu atual (preço e duração de agora, não os do pedido original) e recalcula o fim e o valor, com as validações da criação (menu ativo, chef visível, limites de convidados); o menu novo precisa ser do mesmo chef, senão **409** "Para trocar de chef, cancele e faça um novo pedido"; trocar só o horário mantém a duração do pedido; agendamento de antes dos menus não se edita: **409** "Agendamento antigo: cancele e faça um novo pedido"), `PATCH /agendamentos/{id}/cancelar` (veja "Ciclo do agendamento"), `PATCH /agendamentos/{id}/detalhes` `{ restricoes?, observacoes? }` (só `CONFIRMADO` e até 48h antes do início; **substitui** as restrições e as observações, e o que não vier vale como vazio; mesmas validações e filtro do pedido; grava `detalhesConfirmadosEm`, entra na linha do tempo como `DETALHES_ALTERADOS` e avisa o chef; 200 com o agendamento) e `PATCH /agendamentos/{id}/detalhes/confirmar` (sem corpo: o cliente diz que os detalhes estão certos; `DETALHES_CONFIRMADOS` na linha do tempo e aviso ao chef; repetir é 200 e não avisa de novo). Fora de `CONFIRMADO` ou a 48h ou menos do início → **409** "Os detalhes só podem ser alterados até 48h antes"; de outro cliente → **404**. As respostas de agendamento trazem `detalhesConfirmadosEm` (nulo até o cliente agir) | CLIENTE |
| Agendamentos do chef | `GET /agendamentos/recebidos?page=&size=` (paginado como `/agendamentos/meus`), `PATCH /agendamentos/{id}/confirmar` (só chef do agendamento, só `PENDENTE`; **409** se o chef já tiver outro confirmado no mesmo horário), `PATCH /agendamentos/{id}/recusar`·`desistir`·`concluir` | CHEFE |
| Linha do tempo | `GET /agendamentos/{id}/historico` (cliente dono ou chef do agendamento, mesmo depois de recusar ou desistir) | CLIENTE, CHEFE |
| Avaliar | `POST /agendamentos/{id}/avaliacoes`, `GET /agendamentos/{id}/avaliacoes/pendentes` (cliente dono ou chef do agendamento; veja "Avaliações") | CLIENTE, CHEFE |
| Responder e denunciar avaliação | `PUT /avaliacoes/{id}/resposta` `{texto}` → 200 `{resposta}` (o texto como ficou, depois do filtro), `POST /avaliacoes/{id}/denuncias` `{motivo, descricao?}` → 201 `{id, status}` (só o chef avaliado) | CHEFE |
| Reputação do cliente | `GET /clientes/{id}/reputacao` (só chef que tem ou teve agendamento com o cliente, fora os que recusou; para os demais, 404) | CHEFE |
| Meus clientes | `GET /clientes?page=&size=` (clientes com ao menos um agendamento `CONFIRMADO` ou `CONCLUIDO` com o chef, do mais recente para o mais antigo, até 50 por página: `primeiroNome`, `foto`, `totalAgendamentos`, `ultimoAgendamento`, `proximoAgendamento`), `GET /clientes/{id}/historico` (reputação, restrições frequentes, a nota privada e os 20 agendamentos mais recentes com este chef, sem os recusados; cancelado e expirado vêm sem restrições; mesma regra de acesso da reputação, 404 para os demais), `PUT /clientes/{id}/nota` `{texto}` (até 2000 caracteres, uma por chef e cliente, substitui a anterior; passa pelo filtro como as outras; 200 com `{texto, atualizadaEm}`, e texto vazio apaga: 204) | CHEFE |
| Denúncias | `POST /denuncias` (só de chef com quem teve agendamento confirmado e já ocorrido) | CLIENTE |
| Abrir conversa | `POST /conversas` `{chefeId}` (cliente) ou `{clienteId}` (chef); 201 nova, 200 se já existe. O chef só abre com cliente que já atendeu (agendamento `CONCLUIDO`; senão 403); mandar o id do lado errado, os dois ou nenhum é 400 | CLIENTE, CHEFE |
| Chat | `GET /conversas?page=&size=` (paginado como `/notificacoes`, mais recente primeiro, até 50 por página); `GET /conversas/{id}/mensagens?antesDe=&size=` (mais novas primeiro, até 100); `POST /conversas/{id}/mensagens`; `PATCH /conversas/{id}/lidas` ("visto"); `PATCH /conversas/{id}/mensagens/{mid}` (só o autor, até 15 min); `DELETE /conversas/{id}/mensagens/{mid}` (só o autor); `POST /conversas/{id}/mensagens/{mid}/denuncias` `{motivo, descricao}` (só quem recebeu; motivos `OFENSIVA`, `ASSEDIO`, `SPAM`, `GOLPE`, `CONTEUDO_IMPROPRIO`, `OUTRO`). Com o chef apagado ou banido, o histórico continua visível, mas ninguém envia mais (403) | CLIENTE, CHEFE (participantes) |
| Moderação | `GET /moderacao/denuncias?status=`, `PATCH /moderacao/denuncias/{id}/arquivar`, `PATCH /moderacao/chefes/{id}/suspender`·`reativar`·`banir`, `GET /moderacao/denuncias-mensagens?status=` (com o texto do momento da denúncia), `PATCH /moderacao/denuncias-mensagens/{id}/arquivar`·`remover-mensagem`, `GET /moderacao/bloqueios?status=ABERTO` (fila de contas bloqueadas automaticamente, com o texto que disparou), `PATCH /moderacao/bloqueios/{id}/confirmar`·`reverter`, `GET /moderacao/denuncias-avaliacoes?status=` (com o texto original e o autor), `PATCH /moderacao/denuncias-avaliacoes/{id}/arquivar`·`remover-comentario`, `GET /moderacao/comentarios-de-clientes?page=` (o que os chefs escreveram sobre clientes), `GET /moderacao/acoes` (histórico: quem fez cada ação; no banco a API só insere nessa tabela, nunca altera nem apaga) (para quem não modera, a área responde 404) | MODERADOR |

**Contato protegido.** Telefone (e qualquer sequência de 8 ou mais dígitos, inclusive por extenso ou disfarçada), e-mail, @ de rede social e links nunca passam: no chat e nas avaliações viram `***`, o original fica só para a moderação e nada acontece com a conta. Na `MensagemDto`, `contatoOculto` é `true` só para o autor (nas rotas e nos eventos em tempo real), para o front avisar: "Por segurança, telefones, e-mails e redes sociais ficam ocultos no ChefHome." Datas, horários, valores e CEP não contam. Em pratos (`POST /pratos`, `PUT /pratos/{id}`) o contato é recusado: **400** com `"codigo": "CONTATO_NAO_PERMITIDO"`.

**Bloqueio automático.** Mensagens do chat (ao enviar e ao editar), comentários e respostas de avaliações passam pelo mesmo filtro. Palavrão comum vira `***` para os dois lados (o original fica guardado só para a moderação, inclusive na fila de denúncias de mensagens, em `textoOriginal`). Ódio ou ameaça responde **403**, nada é gravado nem chega ao outro lado, e a conta do autor é suspensa na hora: as sessões abertas caem, inclusive as conexões em tempo real, e o login é recusado. O caso entra na fila `GET /moderacao/bloqueios`, e a moderação confirma (chef fica inativo) ou reverte (a conta volta a logar e operar com o mesmo cookie). Toda decisão fica no histórico `GET /moderacao/acoes`. A lista de termos, revisável, fica em `src/main/resources/moderacao/termos.yml`; a aplicação não sobe com termo de menos de 3 caracteres (o `***` nunca pode passar o limite do texto).

### 📱 Telefone verificado (parado)

A verificação de telefone está **parada** até a escolha do canal (o WhatsApp pode servir para avisos, nunca como canal de conversa entre cliente e chef: a conversa fica no chat, veja "Contato protegido"). Hoje **nada exige telefone**: cliente e chef agendam, conversam e avaliam sem ele, e o chef aparece na busca normalmente. O Twilio foi retirado.

O que ficou pronto para quando o canal for escolhido (basta uma implementação de `VerificadorTelefone`):

- `POST /telefone/codigo` `{ "telefone" }` e `POST /telefone/verificar` `{ "telefone", "codigo" }` respondem **503** "Verificação de telefone ainda não disponível". Em desenvolvimento, com `CHEFHOME_TELEFONE_FALSO=true`, funcionam com o código `000000` para testar o fluxo.
- As regras do `TelefoneService`: só celular do Brasil (400), um número por conta entre clientes e chefs (409), número banido (403), um pedido por minuto por conta, contando também os recusados (429), código errado (422). Banido e "de outra conta" só são revelados depois do código certo.
- `GET /auth/me` e `GET /chefe/me` trazem `telefoneVerificado`, só como informação.
- No banco (V12): o telefone verificado nas contas e a tabela `telefone_banido`. Quando a moderação confirma um bloqueio automático ou bane um chef **com telefone verificado**, a API grava só um hash (`SHA-256` do segredo `CHEFHOME_PEPPER_TELEFONE` + o número, `banido_em` só com o dia); sem telefone verificado, nada é gravado. O telefone some com a conta (`DELETE /usuario/me` e `DELETE /chefe/me`).

O telefone de contato do cliente (`telefone` em `PUT /usuario/me`) continua separado e livre.

### 📅 Ciclo do agendamento

| De | Ação | Quem | Regra | Vai para |
|---|---|---|---|---|
| `PENDENTE` | `PATCH /confirmar` | chef do agendamento | antes do início, sem conflito de horário | `CONFIRMADO` |
| `PENDENTE` | `PATCH /cancelar` | cliente | antes do início | `CANCELADO` |
| `CONFIRMADO` | `PATCH /cancelar` | cliente | até 24h antes do início | `CANCELADO` |
| `PENDENTE` | `PATCH /recusar` | chef do agendamento | antes do início | `RECUSADO`; o chef continua no agendamento e vê a linha do tempo |
| `CONFIRMADO` | `PATCH /desistir` | chef do agendamento | antes do início | `CANCELADO`; o chef continua no agendamento e vê a linha do tempo |
| `CONFIRMADO` | `PATCH /concluir` | chef do agendamento | depois do início | `CONCLUIDO` |
| `CONFIRMADO` | (rotina) | sistema | depois do fim | `CONCLUIDO` |
| `PENDENTE` | (rotina) | sistema | depois do início | `EXPIRADO` |

- Criar, editar o horário e confirmar também dão **409** ("Chef indisponível nesse horário") quando o horário cai num bloqueio ou dia fixo da agenda do chef (veja "Agenda do chef"). A rotina nunca expira nem recusa nada por causa da agenda.
- **Cidade:** o chef só atende na cidade de origem dele (`cidade` e `estado` do perfil). Criar, trocar endereço e confirmar dão **409** "Chef {nome} não atende em {cidade}" quando o endereço é de outra cidade ou UF; a comparação ignora acento, maiúsculas e espaços.
- **Recusou:** para o chef o pedido deixa de existir: ele não vê mais nada do cliente (some dos recebidos, histórico e ações dão **404**, os avisos do pedido são apagados). Quem desiste de um agendamento confirmado continua vendo o agendamento e a linha do tempo. Nos recebidos, agendamento `CANCELADO` (pelo chef ou pelo cliente) ou `EXPIRADO` não traz endereço (fica só cidade e UF), restrições nem observações do cliente: esses dados só aparecem em `PENDENTE`, `CONFIRMADO` e `CONCLUIDO`.
- `CANCELADO`, `RECUSADO`, `CONCLUIDO` e `EXPIRADO` são finais. Fora do status ou do prazo → **409** com o motivo; agendamento de outra pessoa → **404**.
- Cancelar, recusar e desistir aceitam um corpo opcional `{ "motivo": "<código>", "descricao": "<até 300 caracteres>" }`:
  - cliente (`/cancelar`): `MUDANCA_DE_PLANOS`, `DATA_OU_HORARIO`, `ENCONTREI_OUTRA_OPCAO`, `VALOR`, `OUTRO`;
  - chef (`/recusar`, `/desistir`): `AGENDA_INDISPONIVEL`, `LOCAL_FORA_DA_AREA`, `NUMERO_DE_PESSOAS`, `IMPREVISTO`, `OUTRO`.
- Cada mudança entra na linha do tempo (`GET /agendamentos/{id}/historico`), que a API só consegue acrescentar, nunca alterar; ela some junto com o agendamento quando a conta do cliente é apagada.
- O outro lado é notificado (cancelamento, recusa, desistência, expiração), com o motivo.
- Lembretes (mesma rotina, só notificação no app): o cliente recebe "confirme os detalhes" quando faltam 3 dias para um agendamento `CONFIRMADO` (pulado se foi confirmado com menos de 48h de antecedência) e o chef recebe o resumo do serviço (menu, pessoas e restrições) quando falta 1 dia. Cada um sai uma vez só.
- Uma rotina roda a cada 15 minutos para concluir e expirar; os prazos usam o relógio do servidor, então o fuso de produção precisa ser o dos horários informados pelos clientes.
- O app é intermediador: registra e avisa, sem multa nem reembolso; para quem não comparece existe a denúncia, que vale para agendamento confirmado ou concluído que já aconteceu.

### 🗓️ Agenda do chef

O chef diz quando não atende, e o cliente não consegue agendar nesses horários. Dois jeitos: **períodos avulsos** (um dia, férias, uma tarde) e **dias fixos da semana** (ex.: toda segunda). Todas as rotas do chef exigem conta ativa.

- `GET /chefe/me/agenda` → `{ "bloqueios": [{ "id", "inicio", "fim" }], "diasFixos": ["MONDAY", ...] }` (só os bloqueios que ainda não terminaram, por início).
- `POST /chefe/me/agenda/bloqueios` `{ "inicio": "dd-MM-yyyy HH:mm", "fim": "dd-MM-yyyy HH:mm" }` → **201** `{ id, inicio, fim }`. `fim` depois do início, no futuro e a até 365 dias do início, senão **400**. Bloqueios podem se sobrepor entre si. Até 100 bloqueios futuros por chef (**409** além disso).
- `DELETE /chefe/me/agenda/bloqueios/{id}` → **204**; de outro chef → **404**.
- `PUT /chefe/me/agenda/dias-fixos` `{ "dias": ["MONDAY", ...] }` (sem repetição; lista vazia limpa) → **200** com a lista.
- **Confirmados não são derrubados:** um bloqueio ou dia novo que pegue agendamento `CONFIRMADO` (que ainda não terminou) responde **409** "Você tem agendamento confirmado nesse período", com o id e o horário dos que conflitam; o chef desiste antes (`PATCH /agendamentos/{id}/desistir`) e o cliente é avisado pelo fluxo normal. Pedidos `PENDENTE` no período continuam: o chef recusa um por um.
- **Conflito de horário:** o agendamento `[início, fim)` conflita com um bloqueio que se sobreponha (encostar, como 18h–20h e 20h–22h, não conflita) ou com um dia fixo em que caia qualquer parte dele (no fuso do servidor, como os demais prazos). Vale em `POST /agendamentos`, `PUT /agendamentos/{id}` (novo horário) e `PATCH /agendamentos/{id}/confirmar` → **409** "Chef indisponível nesse horário".
- **Consulta pública** `GET /chefe/{id}/agenda?de=&ate=` (qualquer logado; `dd-MM-yyyy`; padrão hoje até hoje + 30 dias; janela máxima de 90 dias, senão **400**) → `[{ "inicio", "fim" }]`: bloqueios, dias fixos (dias inteiros) e agendamentos confirmados dentro da janela, ordenados e mesclados (o que se sobrepõe ou encosta vira um só), sem motivo nem id. Chef não visível na busca → **404**.
- **Folga entre agendamentos:** o chef define `folgaMinutos` no `PATCH /chefe/me` (0 a 240, múltiplo de 15, padrão 60). Um pedido conflita com um agendamento `CONFIRMADO` do chef se se sobrepõe a `[início − folga, fim + folga)` do confirmado, com a folga de agora: encostar na borda da folga não conflita, e o mesmo vale em `POST /agendamentos`, `PUT /agendamentos/{id}` e `PATCH /agendamentos/{id}/confirmar` (**409**). A folga não vale para bloqueios nem dias fixos, não derruba confirmados já existentes ao mudar, e a consulta pública mostra cada confirmado com a folga somada, recortado na janela. Pedidos `PENDENTE` continuam podendo se sobrepor.
- Criar ou mexer na agenda e confirmar agendamento usam a mesma trava do chef, então uma confirmação e um bloqueio simultâneos nunca deixam um confirmado dentro de um bloqueio.
- Fora do escopo: horário de atendimento por dia, feriados, fuso por chef e sincronização com Google Agenda.

### ⭐ Avaliações

Depois de um agendamento `CONCLUIDO`, o cliente avalia o chef e o chef avalia o cliente, até **7 dias** depois da conclusão (fora do prazo, ou sem conclusão → **409**; quem não está no agendamento → **404**). Uma avaliação por pessoa avaliada, sem edição (repetida → **409**).

```json
POST /agendamentos/{id}/avaliacoes
{ "chefeId": "<opcional; se vier, precisa ser o chef do agendamento, senão 400>", "nota": 5, "tags": ["PONTUAL"], "comentario": "opcional, até 1000", "anonimo": true }
```

- `GET /agendamentos/{id}/avaliacoes/pendentes` → `[{ "id", "nome", "tipo", "prazo" }]`: quem ainda posso avaliar (vazio fora do status ou do prazo).
- Tags (até 5, só da lista do lado certo, senão **400**):
  - cliente sobre o chef: `PONTUAL`, `COMIDA_EXCELENTE`, `SIMPATICO`, `COZINHA_LIMPA`, `APRESENTACAO_CAPRICHADA`, `ATRASOU`, `COMIDA_ABAIXO_DO_ESPERADO`, `DEIXOU_SUJEIRA`, `NAO_CUMPRIU_O_COMBINADO`;
  - chef sobre o cliente: `EDUCADO`, `RECEPTIVO`, `COZINHA_ORGANIZADA`, `PAGOU_COMO_COMBINADO`, `MUDOU_EM_CIMA_DA_HORA`, `DEMOROU_PARA_RECEBER`, `COZINHA_SEM_CONDICOES`, `FALTA_DE_EDUCACAO`.
- **Média:** `notaMedia` = soma ÷ total com uma casa, **nula com menos de 5 avaliações** (o front mostra "Novo"). A nota conta na hora em que a avaliação é criada.
- **Anonimato:** nenhuma resposta liga nota ou tag ao autor. A lista pública (`GET /chefe/{id}/avaliacoes`) traz `{ id, nota, tags[{codigo, rotulo}], comentario, autor, quando, resposta }`, sem id de autor nem de agendamento, ordenada pela publicação; `quando` é só mês e ano (`"outubro de 2026"`). O `id` é o da avaliação (para responder ou denunciar).
- **Comentário com nome** (`anonimo: false`): aparece com o primeiro nome do cliente, mas só depois que o chef avalia o cliente daquele agendamento ou, se ele não avaliar, quando acaba o prazo de 7 dias (uma rotina de 15 em 15 minutos publica). Anônimo é o padrão e sai na hora como "Cliente verificado"; avaliação sem comentário é sempre anônima.
- **Sobre o cliente:** o que o chef escreve nunca é público, só a moderação lê. A reputação do cliente (média, total e tags) aparece só para chefs que têm ou tiveram agendamento com ele, e nos recebidos (`clienteNotaMedia`, `clienteTotalAvaliacoes`).
- **Resposta do chef:** uma por avaliação publicada com comentário (responder de novo substitui); o cliente é avisado na primeira.
- **Filtro:** comentários e respostas passam pelo filtro de conteúdo: palavrão vira `***` (o original fica para a moderação), ódio ou ameaça → **403**, nada é gravado e a conta é bloqueada (veja "Bloqueio automático").
- **Denúncia:** o chef denuncia comentário publicado sobre ele (`OFENSIVA`, `DADOS_PESSOAIS`, `FALSA`, `SPAM`, `OUTRO`; repetida → **409**). A moderação arquiva ou remove o comentário: no lugar do texto fica "Comentário removido pela moderação" (e o nome do autor some), mas nota e tags continuam.
- **Conta apagada:** as avaliações dos agendamentos do cliente vão junto, e as notas que ele deu saem da média dos chefs.

### 💬 Chat em tempo real (SSE)

O front abre **uma** conexão por aba e recebe tudo por ela:

```js
const eventos = new EventSource(`${API}/eventos`, { withCredentials: true });
eventos.addEventListener('mensagem', e => mostrar(JSON.parse(e.data)));      // MensagemDto (minha=true nas outras abas de quem enviou)
eventos.addEventListener('mensagens_lidas', e => marcarVisto(JSON.parse(e.data))); // {conversaId, ateId, lidaEm}
eventos.addEventListener('notificacao', e => atualizarSino(JSON.parse(e.data).total));  // a cada notificação criada ou lida, em todas as abas
```

- Os eventos só saem **depois do commit**; se a entrega falhar (aba fechada, rede), a mensagem continua gravada e aparece no próximo `GET /conversas/{id}/mensagens`.
- O servidor manda um `: ping` a cada 25 s e fecha a conexão depois de 30 min; o `EventSource` reconecta sozinho. Cada usuário tem no máximo 5 conexões abertas: a mais antiga recebe o evento `encerrada` e é fechada; nesse evento o front deve chamar `eventos.close()` (senão o navegador só tenta reconectar depois de 24 h).
- Atrás de proxy (nginx), desligue o buffering de `/eventos` (`proxy_buffering off;`) para os eventos não chegarem atrasados.
- **Limitação:** as conexões ficam na memória da instância. Com mais de uma instância da API, um usuário só recebe os eventos gerados na instância em que está conectado. Para escalar horizontalmente, é preciso um pub/sub entre instâncias (ex.: Redis ou `LISTEN/NOTIFY` do Postgres).
