-- Contato protegido: telefone, e-mail, @ e links viram *** no chat. A flag avisa o autor da mensagem.
SET search_path TO chefhome;

ALTER TABLE mensagem ADD COLUMN contato_oculto boolean NOT NULL DEFAULT false;
