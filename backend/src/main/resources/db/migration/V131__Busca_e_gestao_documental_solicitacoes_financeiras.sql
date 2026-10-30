-- Busca e ordenacao das solicitacoes financeiras e codigo pagavel dos documentos.
--
-- O nome do fornecedor e desnormalizado porque a solicitacao guarda apenas o UUID
-- do fornecedor cadastrado. Assim a ordenacao e estavel e nao depende de uma
-- associacao JPA com o cadastro. Os dados de pagamento permanecem cifrados; a
-- coluna de busca recebe somente blind indices gerados pela aplicacao.

ALTER TABLE financeiro_solicitacao_compra
    ADD COLUMN fornecedor_ordenacao VARCHAR(200),
    ADD COLUMN dados_pagamento_busca TEXT;

ALTER TABLE financeiro_solicitacao_pagamento
    ADD COLUMN fornecedor_ordenacao VARCHAR(200),
    ADD COLUMN dados_pagamento_busca TEXT;

ALTER TABLE financeiro_fornecedor
    ADD COLUMN dados_bancarios_busca TEXT;

UPDATE financeiro_solicitacao_compra s
SET fornecedor_ordenacao = immutable_unaccent(lower(COALESCE(
        f.nome_fantasia, s.fornecedor_nome, s.fornecedor_razao_social)))
FROM financeiro_fornecedor f
WHERE f.id = s.fornecedor_id;

UPDATE financeiro_solicitacao_compra
SET fornecedor_ordenacao = immutable_unaccent(lower(COALESCE(
        fornecedor_nome, fornecedor_razao_social)))
WHERE fornecedor_ordenacao IS NULL;

UPDATE financeiro_solicitacao_pagamento s
SET fornecedor_ordenacao = immutable_unaccent(lower(COALESCE(
        f.nome_fantasia, s.fornecedor_nome, s.fornecedor_razao_social)))
FROM financeiro_fornecedor f
WHERE f.id = s.fornecedor_id;

UPDATE financeiro_solicitacao_pagamento
SET fornecedor_ordenacao = immutable_unaccent(lower(COALESCE(
        fornecedor_nome, fornecedor_razao_social)))
WHERE fornecedor_ordenacao IS NULL;

CREATE INDEX idx_compra_fornecedor_ordenacao
    ON financeiro_solicitacao_compra (fornecedor_ordenacao);
CREATE INDEX idx_pagamento_fornecedor_ordenacao
    ON financeiro_solicitacao_pagamento (fornecedor_ordenacao);
CREATE INDEX idx_compra_dados_pagamento_busca ON financeiro_solicitacao_compra
    USING gin (dados_pagamento_busca gin_trgm_ops);
CREATE INDEX idx_pagamento_dados_pagamento_busca ON financeiro_solicitacao_pagamento
    USING gin (dados_pagamento_busca gin_trgm_ops);
CREATE INDEX idx_fornecedor_dados_bancarios_busca ON financeiro_fornecedor
    USING gin (dados_bancarios_busca gin_trgm_ops);

-- Linha digitavel de boleto, codigo de barras ou PIX copia-e-cola. E metadado
-- operacional do documento, separado do numero impresso e da chave da NF-e.
ALTER TABLE financeiro_documento
    ADD COLUMN codigo_pagamento VARCHAR(512);

CREATE INDEX idx_documento_codigo_pagamento ON financeiro_documento
    USING gin (immutable_unaccent(lower(codigo_pagamento)) gin_trgm_ops);

COMMENT ON COLUMN financeiro_solicitacao_compra.dados_pagamento_busca IS
    'Blind indices com pepper para busca exata de termos dos dados de pagamento cifrados.';
COMMENT ON COLUMN financeiro_solicitacao_pagamento.dados_pagamento_busca IS
    'Blind indices com pepper para busca exata de termos dos dados de pagamento cifrados.';
COMMENT ON COLUMN financeiro_fornecedor.dados_bancarios_busca IS
    'Blind indices com pepper para busca exata de termos dos dados bancarios cifrados.';
COMMENT ON COLUMN financeiro_documento.codigo_pagamento IS
    'Linha digitavel, codigo de barras ou PIX copia-e-cola associado ao documento.';
