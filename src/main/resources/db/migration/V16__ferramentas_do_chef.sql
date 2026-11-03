-- Ferramentas do chef: folga entre agendamentos confirmados.
SET search_path TO chefhome;

ALTER TABLE chefe ADD COLUMN folga_minutos integer NOT NULL DEFAULT 60
    CONSTRAINT chefe_folga_minutos_check CHECK (folga_minutos BETWEEN 0 AND 240 AND folga_minutos % 15 = 0);

-- Anotação privada do chef sobre um cliente: uma por par, some com a conta de qualquer um dos dois.
CREATE TABLE nota_cliente (
    chefe_id       uuid          NOT NULL,
    cliente_id     uuid          NOT NULL,
    texto          varchar(2000) NOT NULL,
    texto_original varchar(2000),
    atualizada_em  timestamp(6)  NOT NULL,
    PRIMARY KEY (chefe_id, cliente_id),
    CONSTRAINT fk_nota_cliente_chefe FOREIGN KEY (chefe_id) REFERENCES chefe ON DELETE CASCADE,
    CONSTRAINT fk_nota_cliente_cliente FOREIGN KEY (cliente_id) REFERENCES usuarios ON DELETE CASCADE
);
