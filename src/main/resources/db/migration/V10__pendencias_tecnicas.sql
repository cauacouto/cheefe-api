-- Pendências das revisões de avaliações e chat: chave da tabela de tags e índices que faltavam.
SET search_path TO chefhome;

-- o mapeamento é um Set: (avaliação, tag) nunca repete; a chave também serve ao índice antigo por avaliação
ALTER TABLE avaliacao_tag ADD CONSTRAINT pk_avaliacao_tag PRIMARY KEY (avaliacao_id, tag);
DROP INDEX idx_avaliacao_tag_avaliacao;

-- exclusão de conta e consultas por autor
CREATE INDEX idx_avaliacao_autor ON avaliacao (autor_id);

-- a rotina de publicação (idsComNomeParaPublicar) só olha comentários com nome ainda esperando; o prazo
-- fica em agendamentos.concluido_em, então o índice parcial é pela chave do join
CREATE INDEX idx_avaliacao_a_publicar ON avaliacao (agendamento_id) WHERE publicado_em IS NULL AND anonimo = false;
