-- Usuários e schema do ChefHome com privilégio mínimo.
-- Rodar como superusuário (postgres), conectado ao banco da aplicação. Idempotente.
--
--   psql -U postgres -d chefe_api -v ON_ERROR_STOP=1 \
--        -v senha_migracao='...' -v senha_app='...' -v nome_banco=chefe_api \
--        -f db/setup/01_usuarios.sql
--
-- chefhome_migracao: dono do schema; usado SÓ pelo passo de migração (Flyway), nunca pela API.
-- chefhome_app:      usado pela API; só SELECT/INSERT/UPDATE/DELETE. Não cria, altera nem apaga tabelas.
-- Senhas entram por variável do psql e nunca ficam neste arquivo.

\set ON_ERROR_STOP on

SELECT format('CREATE ROLE chefhome_migracao LOGIN PASSWORD %L', :'senha_migracao')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'chefhome_migracao') \gexec

SELECT format('CREATE ROLE chefhome_app LOGIN PASSWORD %L', :'senha_app')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'chefhome_app') \gexec

-- reaplica atributos e senhas a cada execução (permite rotacionar senha rodando de novo)
ALTER ROLE chefhome_migracao WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'senha_migracao';
ALTER ROLE chefhome_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'senha_app';

-- só os dois conectam no banco; ninguém cria nada no schema public
REVOKE ALL ON DATABASE :"nome_banco" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"nome_banco" TO chefhome_migracao, chefhome_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA IF NOT EXISTS chefhome AUTHORIZATION chefhome_migracao;
ALTER SCHEMA chefhome OWNER TO chefhome_migracao;
REVOKE ALL ON SCHEMA chefhome FROM PUBLIC;
GRANT USAGE ON SCHEMA chefhome TO chefhome_app;

-- tabelas e sequences criadas no futuro pelas migrations já nascem acessíveis (só DML) para a API
ALTER DEFAULT PRIVILEGES FOR ROLE chefhome_migracao IN SCHEMA chefhome
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO chefhome_app;
ALTER DEFAULT PRIVILEGES FOR ROLE chefhome_migracao IN SCHEMA chefhome
    GRANT USAGE, SELECT ON SEQUENCES TO chefhome_app;

-- se o setup rodar depois de migrations já aplicadas, cobre o que já existe
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA chefhome TO chefhome_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA chefhome TO chefhome_app;

-- o histórico de migrations é só do Flyway: a API não pode adulterá-lo
SELECT 'REVOKE INSERT, UPDATE, DELETE ON chefhome.flyway_schema_history FROM chefhome_app'
WHERE EXISTS (SELECT FROM pg_tables WHERE schemaname = 'chefhome' AND tablename = 'flyway_schema_history') \gexec

ALTER ROLE chefhome_migracao SET search_path = chefhome;
ALTER ROLE chefhome_app SET search_path = chefhome;
