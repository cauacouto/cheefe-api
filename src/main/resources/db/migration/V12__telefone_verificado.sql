-- Telefone verificado por SMS (Twilio Verify): um por conta, e o hash dos telefones de contas banidas.
SET search_path TO chefhome;

ALTER TABLE usuarios ADD COLUMN telefone_verificado    varchar(20);
ALTER TABLE usuarios ADD COLUMN telefone_verificado_em timestamp(6);
ALTER TABLE chefe    ADD COLUMN telefone_verificado    varchar(20);
ALTER TABLE chefe    ADD COLUMN telefone_verificado_em timestamp(6);

-- um telefone por conta dentro de cada tabela; entre clientes e chefs a checagem fica no serviço
CREATE UNIQUE INDEX uk_usuarios_telefone_verificado ON usuarios (telefone_verificado)
    WHERE telefone_verificado IS NOT NULL;
CREATE UNIQUE INDEX uk_chefe_telefone_verificado ON chefe (telefone_verificado)
    WHERE telefone_verificado IS NOT NULL;

-- SHA-256(pepper + telefone E.164) em hex: nem o número nem a conta, para ficar depois que a conta some
CREATE TABLE telefone_banido (
    hash      varchar(64)  NOT NULL,
    -- só o dia: com hora exata daria para ligar o hash à ação da moderação (acao_moderacao) pelo horário
    banido_em date         NOT NULL,
    PRIMARY KEY (hash)
);

-- a API grava, consulta e apaga (reativar chef); nunca altera um hash
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'chefhome_app') THEN
        REVOKE UPDATE, TRUNCATE ON telefone_banido FROM chefhome_app;
    END IF;
END
$$;
