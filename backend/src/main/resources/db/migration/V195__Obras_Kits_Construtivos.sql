-- =====================================================================
-- V195: Kits construtivos e histórico de importações de Obras
--
-- Kit é o catálogo de engenharia aplicado nos pontos do planejamento da obra.
-- Cada kit pertence a um contrato e tem itens de serviço (Ação do contrato) ou
-- de material (Material do estoque), referenciados por UUID sem FK entre
-- módulos. O código é único entre os kits ativos do mesmo contrato, para que
-- um kit desativado possa ser recriado com o mesmo código.
--
-- O histórico de importações é compartilhado pelas importações de Obras
-- (kits, carteira, serviços/orçamento previstos) e guarda o relatório por linha.
-- =====================================================================

CREATE TABLE kits_construtivos (
    id UUID PRIMARY KEY,
    id_contrato UUID NOT NULL,
    codigo VARCHAR(40) NOT NULL,
    descricao VARCHAR(300) NOT NULL,
    grupo VARCHAR(60),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_kits_construtivos_contrato_codigo_ativo
    ON kits_construtivos (id_contrato, codigo)
    WHERE ativo = TRUE;
CREATE INDEX idx_kits_construtivos_contrato ON kits_construtivos (id_contrato);
CREATE INDEX idx_kits_construtivos_grupo ON kits_construtivos (grupo);

-- Sem linha para o kit = aplicável a qualquer classe de tensão.
CREATE TABLE kits_construtivos_tensoes (
    id_kit UUID NOT NULL REFERENCES kits_construtivos (id),
    classe_tensao VARCHAR(20) NOT NULL,
    PRIMARY KEY (id_kit, classe_tensao),
    CONSTRAINT ck_kits_construtivos_tensoes_classe
        CHECK (classe_tensao IN ('BT', 'MT_15', 'MT_25', 'MT_36', 'AT_72', 'AT_138'))
);

CREATE TABLE itens_kits_construtivos (
    id UUID PRIMARY KEY,
    id_kit UUID NOT NULL REFERENCES kits_construtivos (id),
    tipo VARCHAR(20) NOT NULL,
    id_acao UUID,
    id_material UUID,
    operacao VARCHAR(20) NOT NULL,
    unidade VARCHAR(20),
    quantidade NUMERIC(14, 4) NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_itens_kits_tipo CHECK (tipo IN ('SERVICO', 'MATERIAL')),
    CONSTRAINT ck_itens_kits_operacao CHECK (operacao IN ('INSTALAR', 'RETIRAR')),
    CONSTRAINT ck_itens_kits_referencia CHECK (
        (tipo = 'SERVICO' AND id_acao IS NOT NULL AND id_material IS NULL)
        OR (tipo = 'MATERIAL' AND id_material IS NOT NULL AND id_acao IS NULL)
    ),
    CONSTRAINT ck_itens_kits_quantidade CHECK (quantidade > 0)
);

CREATE INDEX idx_itens_kits_kit ON itens_kits_construtivos (id_kit);
CREATE INDEX idx_itens_kits_acao ON itens_kits_construtivos (id_acao);
CREATE INDEX idx_itens_kits_material ON itens_kits_construtivos (id_material);

CREATE TABLE importacoes_obras (
    id UUID PRIMARY KEY,
    tipo VARCHAR(30) NOT NULL,
    id_contrato UUID,
    nome_arquivo VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_linhas INTEGER NOT NULL DEFAULT 0,
    inseridos INTEGER NOT NULL DEFAULT 0,
    atualizados INTEGER NOT NULL DEFAULT 0,
    erros INTEGER NOT NULL DEFAULT 0,
    detalhe_erros JSONB NOT NULL DEFAULT '[]'::jsonb,
    mensagem VARCHAR(1000),
    data_conclusao TIMESTAMP WITH TIME ZONE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_importacoes_obras_tipo CHECK (tipo IN ('KITS', 'CARTEIRA', 'ORCAMENTO')),
    CONSTRAINT ck_importacoes_obras_status CHECK (status IN ('PROCESSANDO', 'CONCLUIDA', 'FALHOU'))
);

CREATE INDEX idx_importacoes_obras_tipo_data ON importacoes_obras (tipo, data_criacao DESC);
