# Flyway e privilégio mínimo no banco (lote 9) — Design

**Data:** 2026-10-21
**Status:** aprovado em conversa, aguardando revisão do spec
**Origem:** revisão de engenharia (lote 9 — M3, M7) + pedido do usuário de trabalhar com o menor privilégio possível no banco.

## Objetivo

1. O schema passa a ser **versionado** (Flyway) em vez de criado/alterado pelo `ddl-auto: update`.
2. A API conecta com um usuário que **só manipula dados** (`SELECT/INSERT/UPDATE/DELETE`); quem altera o schema é outro usuário, usado **só no passo de migração**, fora do processo da API.
3. A API **não sobe** se o schema divergir das entidades (`ddl-auto: validate`).

## Decisões

| Decisão | Escolha |
|---|---|
| Quando as migrations rodam | **Passo separado** (`./mvnw flyway:migrate` / job do pipeline), antes de subir a API. A API nunca recebe a senha de migração. |
| Banco atual (local, só dados de teste) | **Recriado do zero**; não há migração de dados. |
| Schema | `chefhome` (o `public` deixa de ser usado; `REVOKE CREATE ON SCHEMA public FROM PUBLIC`). |
| Testes de migração | **Testcontainers** (Postgres 17): sempre no CI; local só com Docker ligado (`disabledWithoutDocker`). Os testes atuais continuam no H2 com `create-drop`. |

## Usuários e permissões

Script `db/setup/01_usuarios.sql`, executado **uma vez** como `postgres` (superusuário) com `psql`; senhas por variável do psql, nunca no arquivo:

```bash
psql -U postgres -d chefe_api -v senha_migracao='...' -v senha_app='...' -f db/setup/01_usuarios.sql
```

| Role | Atributos | Permissões |
|---|---|---|
| `chefhome_migracao` | `LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE` | dono do schema `chefhome` (cria/altera/apaga objetos nele) |
| `chefhome_app` | `LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE` | `CONNECT` no banco; `USAGE` no schema; `SELECT, INSERT, UPDATE, DELETE` em tabelas e `USAGE, SELECT` em sequences — **inclusive futuras**, via `ALTER DEFAULT PRIVILEGES FOR ROLE chefhome_migracao IN SCHEMA chefhome` |

Também no script: `REVOKE ALL ON DATABASE <banco> FROM PUBLIC` + `GRANT CONNECT` só aos dois; `REVOKE CREATE ON SCHEMA public FROM PUBLIC`; `ALTER ROLE chefhome_app SET search_path = chefhome`. O script é idempotente onde o Postgres permite (`DO $$ ... IF NOT EXISTS ... $$`).

## Migrations

Pasta `src/main/resources/db/migration`, schema `chefhome`.

- **`V1__schema_inicial.sql`** — todas as tabelas atuais, geradas a partir das entidades (dialeto PostgreSQL) e revisadas à mão: `usuarios`, `chefe`, `endereco`, `agendamentos` (+ tabela de junção com chefes), `pratos`, `notificacao`, `denuncia`; PKs, FKs, uniques (`usuarios.email`, `chefe.email`), defaults (`chefe.suspenso false`, `chefe.disponivel true`, `chefe.ativo true`) e índices:
  - `notificacao(destinatario_id, lida, criada_em)` (já declarado na entidade);
  - `denuncia(chefe_id, status)` e `denuncia(cliente_id, chefe_id, status)` (apontados na revisão da moderação; também declarados via `@Index` na entidade `Denuncia`);
  - índices nas FKs usadas em consultas: `endereco(usuario_id)`, `agendamentos(usuario_id)`, `pratos(chefe_model_id)`.
- Toda mudança futura de schema = novo `V{n}__descricao.sql` (o chat será `V2__chat.sql`). Migrations aplicadas **nunca** são editadas.

## Configuração

### Migração (passo separado)

- `pom.xml`: `flyway-maven-plugin` (versão `${flyway.version}` do Boot) com dependências `flyway-database-postgresql` e `postgresql`; configurado por variáveis: `FLYWAY_URL`, `FLYWAY_USER` (= `chefhome_migracao`), `FLYWAY_PASSWORD`, `flyway.schemas=chefhome`, `flyway.locations=filesystem:src/main/resources/db/migration`.
- Comando: `./mvnw flyway:migrate`.

### API

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}            # jdbc:postgresql://host:5432/chefe_api?currentSchema=chefhome
    username: ${DATABASE_USERNAME}  # chefhome_app
    password: ${DATABASE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: false                  # a API nunca migra
```

- Dependências: `spring-boot-starter-flyway` **não** entra no runtime da API (evita que alguém religue migração no processo com a credencial errada); o Flyway fica no plugin e no escopo `test` (para o teste de migração).
- `application.example.yml` e README atualizados; o usuário ajusta o próprio `application-prod.yml` (passo descrito no README).

## Teste de migração (`MigracoesPostgresIT`)

`@Testcontainers(disabledWithoutDocker = true)`, `PostgreSQLContainer("postgres:17-alpine")`:

1. Executa `db/setup/01_usuarios.sql` como superusuário do container (senhas de teste).
2. Roda `Flyway.configure().dataSource(url, "chefhome_migracao", senha).schemas("chefhome").load().migrate()` → todas as migrations aplicadas, sem erro.
3. Sobe o contexto Spring (perfil `test` + propriedades dinâmicas apontando para o container, usuário **`chefhome_app`**, `ddl-auto=validate`) → **contexto sobe** = schema bate com as entidades.
4. Como `chefhome_app`: `INSERT`/`SELECT` numa tabela funciona; `CREATE TABLE x(...)` e `DROP TABLE usuarios` falham com permissão negada.
5. Como `chefhome_app`: criar tabela no `public` também falha.

O CI (`.github/workflows`) já roda em `ubuntu-latest`, que tem Docker — o teste roda sempre lá.

## Recriar o banco local (README)

1. `dropdb chefe_api && createdb chefe_api` (como `postgres`).
2. `psql ... -f db/setup/01_usuarios.sql` com as duas senhas.
3. `FLYWAY_URL=jdbc:postgresql://localhost:5432/chefe_api FLYWAY_USER=chefhome_migracao FLYWAY_PASSWORD=... ./mvnw flyway:migrate`.
4. Ajustar `DATABASE_URL` (com `?currentSchema=chefhome`), `DATABASE_USERNAME=chefhome_app`, `DATABASE_PASSWORD`; `ddl-auto: validate`; subir a API.

Os `ALTER TABLE` manuais do README (onboarding, moderação) são removidos: o `V1` já nasce com o schema certo.

## Erros

| Situação | Resultado |
|---|---|
| Schema diverge das entidades | API não sobe (`SchemaManagementException`), mensagem aponta tabela/coluna |
| API tenta DDL (bug ou invasão) | Postgres recusa (`permission denied`) |
| Migration falha no deploy | `flyway:migrate` sai com erro; API antiga continua rodando (passo anterior ao deploy) |

## Fora de escopo

Pool de conexões separado por função; backups; segredos em cofre (Vault/Secrets Manager); migração de dados do banco antigo; row-level security.
