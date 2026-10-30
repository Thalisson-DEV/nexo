-- O HMAC e calculado sobre o JSON canonico compacto produzido pela aplicacao.
-- JSONB reserializa o mesmo valor com espacos e ordem propria de chaves, o
-- que fazia a primeira leitura da cadeia aparecer como VIOLADO. A verificacao
-- tambem canoniza o valor lido para manter compatibilidade com esses eventos.
ALTER TABLE eventos_solicitacao_material
    ALTER COLUMN payload_canonico TYPE TEXT
    USING payload_canonico::text;

ALTER TABLE eventos_solicitacao_material
    ADD CONSTRAINT ck_eventos_solicitacao_payload_json
    CHECK (payload_canonico IS JSON);
