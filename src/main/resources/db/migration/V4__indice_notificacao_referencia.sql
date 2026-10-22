-- Notificações por registro de origem: a exclusão de conta apaga as que apontam para os dados do cliente
-- (evento IN ... AND referencia_id IN ...) sem varrer a tabela inteira.
SET search_path TO chefhome;

CREATE INDEX idx_notificacao_referencia ON notificacao (referencia_id, evento);
