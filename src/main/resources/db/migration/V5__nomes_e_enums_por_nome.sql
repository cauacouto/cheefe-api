-- Sobras da renomeação: nome certo da tabela de junção, enums gravados pelo nome (reordenar ou
-- inserir um valor no enum não muda mais o significado das linhas) e a coluna morta chefe.password
-- (resto do login por OTP, nunca lida).
SET search_path TO chefhome;

ALTER TABLE agedamento_chefe RENAME TO agendamento_chefe;
ALTER INDEX idx_agedamento_chefe_chefe RENAME TO idx_agendamento_chefe_chefe;

ALTER TABLE agendamentos DROP CONSTRAINT agendamentos_status_check;
ALTER TABLE agendamentos ALTER COLUMN status TYPE varchar(255) USING (
    CASE status WHEN 0 THEN 'PENDENTE' WHEN 1 THEN 'CONFIRMADO' WHEN 2 THEN 'CANCELADO' WHEN 3 THEN 'CONCLUIDO' END);
ALTER TABLE agendamentos ADD CONSTRAINT agendamentos_status_check
    CHECK (status IN ('PENDENTE','CONFIRMADO','CANCELADO','CONCLUIDO'));

ALTER TABLE endereco DROP CONSTRAINT endereco_tipo_de_residencia_check;
ALTER TABLE endereco ALTER COLUMN tipo_de_residencia TYPE varchar(255) USING (
    CASE tipo_de_residencia WHEN 0 THEN 'CASA' WHEN 1 THEN 'APARTAMENTO' END);
ALTER TABLE endereco ADD CONSTRAINT endereco_tipo_de_residencia_check
    CHECK (tipo_de_residencia IN ('CASA','APARTAMENTO'));

ALTER TABLE chefe DROP COLUMN password;
