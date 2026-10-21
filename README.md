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
| `DATABASE_*` | banco de dados |
| `R2_ENDPOINT`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_PUBLIC_URL` | bucket de fotos dos pratos (Cloudflare R2 ou outro compatível com S3, com leitura pública) |

A aplicação **não sobe** sem o registro do Google e sem `FRONT_URL`.

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
| Usuário | `GET` · `PUT` · `DELETE /usuario/me` (o e-mail não é editável; o DELETE encerra a sessão) | CLIENTE |
| Endereços | `GET` · `POST /usuario/enderecos` | CLIENTE |
| Chefs | `GET /chefe`, `GET /chefe/destaque` | qualquer logado |
| Perfil do chef | `PATCH /chefe/me`; `PATCH /chefe/me/inativar` (pausa: some da busca e não recebe agendamentos novos) e `PATCH /chefe/me/ativar` (volta); `DELETE /chefe/me` (apaga a conta: não loga nem opera mais) | CHEFE |
| Pratos | `GET /pratos`, `GET /pratos/chefe/{chefeId}` | qualquer logado |
| Meus pratos | `POST /pratos` (multipart `dados` + `foto`), `PUT /pratos/{id}`, `GET /pratos/meus` | CHEFE |
| Agendamentos do cliente | `POST /agendamentos` (endereço precisa ser do cliente), `GET /agendamentos/meus`, `PUT /agendamentos/{id}` (só `PENDENTE`) | CLIENTE |
| Agendamentos do chef | `GET /agendamentos/recebidos`, `PATCH /agendamentos/{id}/confirmar` (só chef do agendamento, só `PENDENTE`) | CHEFE |
