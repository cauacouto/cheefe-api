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
| `DATABASE_*`, `CLOUDINARY_*` | banco e upload de imagens |

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
| Conta nova | volta em `{FRONT_URL}/cadastro`; `GET /auth/register` traz `email`, `nome`, `foto`; `POST /auth/register` (multipart `dados` + `foto` opcional, sem `email`) cria a conta e já loga |
| Erro/cancelamento | volta em `{FRONT_URL}/login?erro=google` |
| Sessão | `GET /auth/me` → `{ id, email, tipo }` ou 401 |
| Sair | `POST /auth/logout` |

Todas as chamadas do front precisam de `credentials: 'include'`.
