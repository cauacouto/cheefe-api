-- Onde o chef atende: só na cidade de origem. cidade_busca é a cidade normalizada (sem acento, minúscula,
-- espaços únicos), calculada pela API ao gravar, para a busca e o agendamento compararem do mesmo jeito.
SET search_path TO chefhome;

ALTER TABLE chefe ADD COLUMN cidade       varchar(100);
ALTER TABLE chefe ADD COLUMN estado       varchar(2);
ALTER TABLE chefe ADD COLUMN cidade_busca varchar(100);

CREATE INDEX idx_chefe_cidade ON chefe (estado, cidade_busca);
