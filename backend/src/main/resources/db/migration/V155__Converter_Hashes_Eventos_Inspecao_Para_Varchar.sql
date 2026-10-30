-- Padroniza os hashes criados na V153 com o tipo esperado pelos mapeamentos String do Hibernate.
-- Em PostgreSQL, CHAR é exposto como bpchar e não é considerado equivalente a VARCHAR
-- durante a validação estrita do schema (hibernate.ddl-auto=validate).

ALTER TABLE eventos_inspecao_seguranca
    ALTER COLUMN hash_anterior TYPE VARCHAR(64),
    ALTER COLUMN hash_atual TYPE VARCHAR(64);
