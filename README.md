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

Toda identidade vem do cookie de sessão: nenhuma rota recebe id ou e-mail de quem está logado. Papel errado → **403**; recurso de outra pessoa → **404** (igual a inexistente); ação não permitida no status atual → **409**.

| Recurso | Rota | Papel |
|---|---|---|
| Sessão | `GET /auth/me` | qualquer logado |
| Notificações | `GET /notificacoes?somenteNaoLidas=`, `GET /notificacoes/nao-lidas/contagem`, `PATCH /notificacoes/{id}/lida`, `PATCH /notificacoes/lidas` (sempre só as próprias) | qualquer logado |
| Tempo real | `GET /eventos` (SSE, `text/event-stream`; eventos `mensagem`, `mensagem_editada`, `mensagem_apagada`, `mensagens_lidas` e `notificacao`) | qualquer logado |
| Usuário | `GET` · `PUT` · `DELETE /usuario/me` (o e-mail não é editável; o DELETE encerra a sessão e apaga também as conversas, mensagens e denúncias de mensagens do cliente); `PUT /usuario/me/foto` (multipart `foto`) | CLIENTE |
| Endereços | `GET` · `POST /usuario/enderecos` | CLIENTE |
| Chefs | `GET /chefe`, `GET /chefe/destaque` (só chefs ativos, disponíveis e com perfil completo) | qualquer logado |
| Perfil do chef | `GET /chefe/me` (inclui `perfilCompleto` e `camposPendentes`); `PATCH /chefe/me`; `PUT /chefe/me/foto` (multipart `foto`); `PATCH /chefe/me/inativar` (pausa: some da busca e não recebe agendamentos novos) e `PATCH /chefe/me/ativar` (volta); `DELETE /chefe/me` (apaga a conta: não loga nem opera mais) | CHEFE |
| Pratos | `GET /pratos`, `GET /pratos/chefe/{chefeId}` | qualquer logado |
| Meus pratos | `POST /pratos` (multipart `dados` + `foto`), `PUT /pratos/{id}`, `GET /pratos/meus` | CHEFE |
| Agendamentos do cliente | `POST /agendamentos` (endereço precisa ser do cliente), `GET /agendamentos/meus`, `PUT /agendamentos/{id}` (só `PENDENTE`) | CLIENTE |
| Agendamentos do chef | `GET /agendamentos/recebidos`, `PATCH /agendamentos/{id}/confirmar` (só chef do agendamento, só `PENDENTE`) | CHEFE |
| Denúncias | `POST /denuncias` (só de chef com quem teve agendamento confirmado e já ocorrido) | CLIENTE |
| Abrir conversa | `POST /conversas` `{chefeId}` (201 nova, 200 se já existe; só o cliente inicia) | CLIENTE |
| Chat | `GET /conversas`; `GET /conversas/{id}/mensagens?antesDe=&size=` (mais novas primeiro, até 100); `POST /conversas/{id}/mensagens`; `PATCH /conversas/{id}/lidas` ("visto"); `PATCH /conversas/{id}/mensagens/{mid}` (só o autor, até 15 min); `DELETE /conversas/{id}/mensagens/{mid}` (só o autor); `POST /conversas/{id}/mensagens/{mid}/denuncias` `{motivo, descricao}` (só quem recebeu; motivos `OFENSIVA`, `ASSEDIO`, `SPAM`, `GOLPE`, `CONTEUDO_IMPROPRIO`, `OUTRO`). Com o chef apagado ou banido, o histórico continua visível, mas ninguém envia mais (403) | CLIENTE, CHEFE (participantes) |
| Moderação | `GET /moderacao/denuncias?status=`, `PATCH /moderacao/denuncias/{id}/arquivar`, `PATCH /moderacao/chefes/{id}/suspender`·`reativar`·`banir`, `GET /moderacao/denuncias-mensagens?status=` (com o texto do momento da denúncia), `PATCH /moderacao/denuncias-mensagens/{id}/arquivar`·`remover-mensagem`, `GET /moderacao/acoes` (histórico: quem fez cada ação; no banco a API só insere nessa tabela, nunca altera nem apaga) (para quem não modera, a área responde 404) | MODERADOR |

### 💬 Chat em tempo real (SSE)

O front abre **uma** conexão por aba e recebe tudo por ela:

```js
const eventos = new EventSource(`${API}/eventos`, { withCredentials: true });
eventos.addEventListener('mensagem', e => mostrar(JSON.parse(e.data)));      // MensagemDto (minha=true nas outras abas de quem enviou)
eventos.addEventListener('mensagens_lidas', e => marcarVisto(JSON.parse(e.data))); // {conversaId, ateId, lidaEm}
eventos.addEventListener('notificacao', e => atualizarSino(JSON.parse(e.data).total));
```

- Os eventos só saem **depois do commit**; se a entrega falhar (aba fechada, rede), a mensagem continua gravada e aparece no próximo `GET /conversas/{id}/mensagens`.
- O servidor manda um `: ping` a cada 25 s e fecha a conexão depois de 30 min; o `EventSource` reconecta sozinho. Cada usuário tem no máximo 5 conexões abertas: a mais antiga recebe o evento `encerrada` e é fechada; nesse evento o front deve chamar `eventos.close()` (senão o navegador só tenta reconectar depois de 24 h).
- Atrás de proxy (nginx), desligue o buffering de `/eventos` (`proxy_buffering off;`) para os eventos não chegarem atrasados.
- **Limitação:** as conexões ficam na memória da instância. Com mais de uma instância da API, um usuário só recebe os eventos gerados na instância em que está conectado. Para escalar horizontalmente, é preciso um pub/sub entre instâncias (ex.: Redis ou `LISTEN/NOTIFY` do Postgres).
