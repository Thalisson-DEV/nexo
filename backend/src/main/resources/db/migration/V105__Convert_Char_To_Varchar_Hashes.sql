-- V105: Converte colunas CHAR(64) para VARCHAR(64) para evitar falhas de validação de schema no Hibernate.
-- O Hibernate mapeia Strings nativamente para VARCHAR, e quando o banco de dados tem 'bpchar' (CHAR),
-- a validação rigorosa (hibernate.ddl-auto=validate) falha na inicialização da aplicação.

ALTER TABLE revisoes_transcricao_sst ALTER COLUMN hash_conteudo TYPE VARCHAR(64);
ALTER TABLE versoes_ata_sst ALTER COLUMN sha256_conteudo TYPE VARCHAR(64);
ALTER TABLE tentativas_geracao_ata_sst ALTER COLUMN hash_insumos TYPE VARCHAR(64);
ALTER TABLE copias_assinadas_ata_sst ALTER COLUMN sha256 TYPE VARCHAR(64);
ALTER TABLE evidencias_fotograficas_reuniao_sst ALTER COLUMN sha256 TYPE VARCHAR(64);
ALTER TABLE comunicacoes_ata_cipa_sst ALTER COLUMN sha256_evidencia TYPE VARCHAR(64);

ALTER TABLE sst_documentos_governanca ALTER COLUMN sha256 TYPE VARCHAR(64);

ALTER TABLE normalizacoes_transcricao_sst ALTER COLUMN hash_insumos TYPE VARCHAR(64);
ALTER TABLE normalizacoes_transcricao_sst ALTER COLUMN sha256_conteudo TYPE VARCHAR(64);
ALTER TABLE arquivos_modelo_ata_sst ALTER COLUMN sha256 TYPE VARCHAR(64);
ALTER TABLE modelos_ata_sst ALTER COLUMN hash_arquivo_docx TYPE VARCHAR(64);
ALTER TABLE arquivos_ata_gerada_sst ALTER COLUMN hash_modelo_docx TYPE VARCHAR(64);
ALTER TABLE arquivos_ata_gerada_sst ALTER COLUMN sha256 TYPE VARCHAR(64);
ALTER TABLE importacoes_referencia_governanca_sst ALTER COLUMN sha256_fonte TYPE VARCHAR(64);
ALTER TABLE referencias_matriz_treinamento_sst ALTER COLUMN sha256_fonte TYPE VARCHAR(64);
