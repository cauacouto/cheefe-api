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

**Criar (ou recriar) o banco local:**

```bash
# 1. banco novo (como superusuário postgres)
dropdb -U postgres --if-exists chefe_api && createdb -U postgres chefe_api

# 2. usuários e schema (senhas só na linha de comando, nunca em arquivo versionado)
psql -U postgres -d chefe_api -v ON_ERROR_STOP=1 \
     -v senha_migracao='SENHA_MIGRACAO' -v senha_app='SENHA_APP' -v nome_banco=chefe_api \
     -f db/setup/01_usuarios.sql

# 3. migrations (passo separado; a API nunca recebe esta senha)
FLYWAY_URL=jdbc:postgresql://localhost:5432/chefe_api \
FLYWAY_USER=chefhome_migracao FLYWAY_PASSWORD='SENHA_MIGRACAO' ./mvnw flyway:migrate

# 4. API com o usuário de aplicação
#    DATABASE_URL=jdbc:postgresql://localhost:5432/chefe_api?currentSchema=chefhome
#    DATABASE_USERNAME=chefhome_app   DATABASE_PASSWORD='SENHA_APP'
```

**Deploy:** rode `./mvnw flyway:migrate` (com as variáveis `FLYWAY_*`) **antes** de subir a nova versão da API. Se a migration falhar, a versão anterior continua no ar.

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
| Usuário | `GET` · `PUT` · `DELETE /usuario/me` (o e-mail não é editável; o DELETE encerra a sessão); `PUT /usuario/me/foto` (multipart `foto`) | CLIENTE |
| Endereços | `GET` · `POST /usuario/enderecos` | CLIENTE |
| Chefs | `GET /chefe`, `GET /chefe/destaque` (só chefs ativos, disponíveis e com perfil completo) | qualquer logado |
| Perfil do chef | `GET /chefe/me` (inclui `perfilCompleto` e `camposPendentes`); `PATCH /chefe/me`; `PUT /chefe/me/foto` (multipart `foto`); `PATCH /chefe/me/inativar` (pausa: some da busca e não recebe agendamentos novos) e `PATCH /chefe/me/ativar` (volta); `DELETE /chefe/me` (apaga a conta: não loga nem opera mais) | CHEFE |
| Pratos | `GET /pratos`, `GET /pratos/chefe/{chefeId}` | qualquer logado |
| Meus pratos | `POST /pratos` (multipart `dados` + `foto`), `PUT /pratos/{id}`, `GET /pratos/meus` | CHEFE |
| Agendamentos do cliente | `POST /agendamentos` (endereço precisa ser do cliente), `GET /agendamentos/meus`, `PUT /agendamentos/{id}` (só `PENDENTE`) | CLIENTE |
| Agendamentos do chef | `GET /agendamentos/recebidos`, `PATCH /agendamentos/{id}/confirmar` (só chef do agendamento, só `PENDENTE`) | CHEFE |
| Denúncias | `POST /denuncias` (só de chef com quem teve agendamento confirmado e já ocorrido) | CLIENTE |
| Moderação | `GET /moderacao/denuncias?status=`, `PATCH /moderacao/denuncias/{id}/arquivar`, `PATCH /moderacao/chefes/{id}/suspender`·`reativar`·`banir` (para quem não modera, a área responde 404) | MODERADOR |
