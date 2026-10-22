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

-- nada desta sessão vai para o log do servidor, nem em caso de erro: senhas nunca aparecem em log,
-- mesmo com log_statement=ddl/all ligado (comum em VPS). Em banco gerenciado (Neon etc.) quem roda o
-- script não é superusuário e não pode mudar isso; o provedor não expõe esse log, então só avisamos.
DO $$
BEGIN
    SET log_statement = 'none';
    SET log_min_error_statement = 'panic';
EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'sem permissão para desligar o log de comandos (banco gerenciado); seguindo';
END
$$;

-- roles nascem sem senha; a senha é definida só no ALTER ROLE abaixo
SELECT 'CREATE ROLE chefhome_migracao LOGIN'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'chefhome_migracao') \gexec

SELECT 'CREATE ROLE chefhome_app LOGIN'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'chefhome_app') \gexec

-- (re)define as senhas a cada execução: rodar de novo rotaciona
ALTER ROLE chefhome_migracao WITH LOGIN PASSWORD :'senha_migracao';
ALTER ROLE chefhome_app WITH LOGIN PASSWORD :'senha_app';

-- reforça os atributos (já são o padrão de CREATE ROLE); sem superusuário, alguns não podem ser
-- alterados, mas roles criadas por um não-superusuário nunca os têm
DO $$
BEGIN
    ALTER ROLE chefhome_migracao NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    ALTER ROLE chefhome_app NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'sem permissão para reforçar atributos das roles (banco gerenciado); seguindo';
END
$$;

-- sem superusuário, criar o schema em nome de chefhome_migracao e definir os default privileges dela
-- exige ser membro da role: participação temporária, removida no fim do script
GRANT chefhome_migracao TO CURRENT_USER;

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

-- Sem GRANT ... ON ALL TABLES: as permissões de cada tabela nascem dos default privileges acima e das
-- restrições das próprias migrations (ex.: auditoria só de inserção). Re-rodar o setup (para trocar
-- senha) não pode devolver à API privilégios que uma migration retirou.

-- o histórico de migrations é só do Flyway: a API não pode adulterá-lo
SELECT 'REVOKE INSERT, UPDATE, DELETE ON chefhome.flyway_schema_history FROM chefhome_app'
WHERE EXISTS (SELECT FROM pg_tables WHERE schemaname = 'chefhome' AND tablename = 'flyway_schema_history') \gexec

ALTER ROLE chefhome_migracao SET search_path = chefhome;
ALTER ROLE chefhome_app SET search_path = chefhome;

REVOKE chefhome_migracao FROM CURRENT_USER;
