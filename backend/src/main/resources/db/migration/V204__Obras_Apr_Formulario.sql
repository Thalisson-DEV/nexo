-- A APR passa a ser o formulário preenchido antes da execução, e a ocorrência pode dizer que o serviço não tem condição de ser feito.
ALTER TABLE turno_aprs ADD COLUMN formulario JSONB;

ALTER TABLE turno_ocorrencias ADD COLUMN impede_execucao BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE turno_ocorrencias ADD CONSTRAINT ck_turno_ocorrencias_impedimento_com_obra CHECK (NOT impede_execucao OR id_obra IS NOT NULL);
