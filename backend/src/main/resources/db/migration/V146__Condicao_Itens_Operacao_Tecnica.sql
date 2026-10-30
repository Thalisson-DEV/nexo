ALTER TABLE itens_operacao_solicitacao
    ADD COLUMN condicao VARCHAR(20);

UPDATE itens_operacao_solicitacao
   SET condicao = 'NOVO'
 WHERE condicao IS NULL;

ALTER TABLE itens_operacao_solicitacao
    ALTER COLUMN condicao SET NOT NULL;

ALTER TABLE itens_operacao_solicitacao
    ADD CONSTRAINT chk_itens_operacao_condicao
        CHECK (condicao IN ('NOVO', 'SUCATA'));

