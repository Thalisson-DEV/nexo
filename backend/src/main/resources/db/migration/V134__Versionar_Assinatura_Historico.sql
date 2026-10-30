-- O formato 1 assinava representacoes que o PostgreSQL pode alterar de forma
-- irreversivel (offset e nanossegundos). Esses eventos passam a ser tratados
-- como baseline legado NAO_VERIFICADO. Novos eventos usam o formato 2, que
-- assina UTC com precisao de microssegundos e todos os metadados auditaveis.
ALTER TABLE eventos_solicitacao_material
    ADD COLUMN versao_formato_hash INTEGER NOT NULL DEFAULT 1;

ALTER TABLE eventos_solicitacao_material
    ALTER COLUMN ocorrido_em TYPE TIMESTAMP(6) WITH TIME ZONE;
