-- Ferramentas do chef: folga entre agendamentos confirmados.
SET search_path TO chefhome;

ALTER TABLE chefe ADD COLUMN folga_minutos integer NOT NULL DEFAULT 60
    CONSTRAINT chefe_folga_minutos_check CHECK (folga_minutos BETWEEN 0 AND 240 AND folga_minutos % 15 = 0);
