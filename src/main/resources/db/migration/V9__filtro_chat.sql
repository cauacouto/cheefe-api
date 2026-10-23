-- Filtro de conteúdo no chat: o texto como foi escrito, antes do ***, só para a moderação.
SET search_path TO chefhome;

-- nulo quando nada foi mascarado (o original é o próprio texto)
ALTER TABLE mensagem ADD COLUMN texto_original varchar(2000);
-- cópia do original no momento da denúncia, como a de texto_denunciado
ALTER TABLE denuncia_mensagem ADD COLUMN texto_original varchar(2000);
