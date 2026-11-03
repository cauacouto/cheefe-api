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

-- Lembretes, resumo, confirmação dos detalhes e aviso de um ano: cada um é gravado uma vez, para as rotinas não repetirem.
ALTER TABLE agendamentos
    ADD COLUMN lembrete_cliente_em      timestamp(6),
    ADD COLUMN resumo_chef_em           timestamp(6),
    ADD COLUMN detalhes_confirmados_em  timestamp(6),
    ADD COLUMN aviso_um_ano_em          timestamp(6);

-- só o que ainda falta: as rotinas varrem estes índices e eles ficam pequenos
CREATE INDEX idx_agendamentos_sem_lembrete ON agendamentos (data_hora_inicial)
    WHERE status = 'CONFIRMADO' AND lembrete_cliente_em IS NULL;
CREATE INDEX idx_agendamentos_sem_resumo ON agendamentos (data_hora_inicial)
    WHERE status = 'CONFIRMADO' AND resumo_chef_em IS NULL;
CREATE INDEX idx_agendamentos_sem_aviso_um_ano ON agendamentos (data_hora_inicial)
    WHERE status = 'CONCLUIDO' AND aviso_um_ano_em IS NULL;

ALTER TABLE notificacao DROP CONSTRAINT notificacao_evento_check;
ALTER TABLE notificacao ADD CONSTRAINT notificacao_evento_check CHECK (evento IN (
    'AGENDAMENTO_CRIADO','AGENDAMENTO_EDITADO','AGENDAMENTO_CONFIRMADO','DENUNCIA_CRIADA','CHEF_OCULTO',
    'CHEF_SUSPENSO','CHEF_REATIVADO','MENSAGEM_RECEBIDA','MENSAGEM_DENUNCIADA',
    'AGENDAMENTO_CANCELADO','AGENDAMENTO_RECUSADO','CHEF_DESISTIU','AGENDAMENTO_EXPIRADO',
    'CONTA_BLOQUEADA','AVALIACAO_RECEBIDA','AVALIACAO_RESPONDIDA','AVALIACAO_DENUNCIADA',
    'LEMBRETE_DETALHES','RESUMO_DO_SERVICO','DETALHES_ALTERADOS','DETALHES_CONFIRMADOS','UM_ANO_DE_CLIENTE'));

-- o CHECK foi declarado em linha na V6: o nome é o que o Postgres gerou
ALTER TABLE agendamento_historico DROP CONSTRAINT agendamento_historico_acao_check;
ALTER TABLE agendamento_historico ADD CONSTRAINT agendamento_historico_acao_check CHECK (acao IN (
    'CRIADO','EDITADO','CONFIRMADO','CANCELADO','RECUSADO','DESISTENCIA','CONCLUIDO','EXPIRADO',
    'DETALHES_ALTERADOS','DETALHES_CONFIRMADOS'));
