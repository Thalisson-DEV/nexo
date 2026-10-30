-- Padroniza os hashes criados na V119 com o tipo esperado pelos mapeamentos String do Hibernate.
-- Em PostgreSQL, CHAR e exposto como bpchar e nao e considerado equivalente a VARCHAR
-- durante a validacao estrita do schema (hibernate.ddl-auto=validate).

ALTER TABLE comandos_idempotentes_solicitacao
    ALTER COLUMN hash_payload TYPE VARCHAR(64);

ALTER TABLE eventos_solicitacao_material
    ALTER COLUMN hash_anterior TYPE VARCHAR(64),
    ALTER COLUMN hash_atual TYPE VARCHAR(64);

ALTER TABLE lotes_importacao_solicitacao
    ALTER COLUMN token_hash TYPE VARCHAR(64),
    ALTER COLUMN hash_arquivo TYPE VARCHAR(64);
