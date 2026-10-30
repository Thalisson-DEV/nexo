-- =====================================================================
-- V70: Metas de inspeção de segurança
-- (a versão 69 foi pulada: consta como aplicada no banco de desenvolvimento
--  por um script que não existe no repositório)
-- Quantas inspeções cada alvo (uma função inteira ou um colaborador
-- específico) deve realizar por janela de apuração. A duração é definida
-- pela periodicidade + data de início da vigência: as janelas se repetem
-- a partir dessa âncora até o fim da vigência (nulo = indeterminada).
-- Referências a funções/colaboradores ficam sem FK, preservando o
-- isolamento entre os módulos (mesmo padrão de inspecoes_seguranca).
-- =====================================================================

CREATE TABLE metas_inspecao (
    id UUID PRIMARY KEY,
    escopo VARCHAR(20) NOT NULL,
    id_funcao UUID,
    id_colaborador UUID,
    quantidade INTEGER NOT NULL,
    periodicidade VARCHAR(20) NOT NULL,
    vigencia_inicio DATE NOT NULL,
    vigencia_fim DATE,
    descricao VARCHAR(200),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    -- O alvo é exatamente um: função OU colaborador.
    CONSTRAINT ck_meta_alvo_exclusivo CHECK (
        (id_funcao IS NOT NULL AND id_colaborador IS NULL)
        OR (id_funcao IS NULL AND id_colaborador IS NOT NULL)
    ),
    CONSTRAINT ck_meta_quantidade_positiva CHECK (quantidade > 0),
    CONSTRAINT ck_meta_vigencia_coerente CHECK (
        vigencia_fim IS NULL OR vigencia_fim >= vigencia_inicio
    ),
    -- PERSONALIZADO usa a própria vigência como janela, logo exige fim.
    CONSTRAINT ck_meta_personalizado_com_fim CHECK (
        periodicidade <> 'PERSONALIZADO' OR vigencia_fim IS NOT NULL
    )
);

-- Resolução da meta aplicável a um colaborador e validação de sobreposição.
CREATE INDEX idx_metas_inspecao_funcao
    ON metas_inspecao(id_funcao)
    WHERE ativo = TRUE AND id_funcao IS NOT NULL;

CREATE INDEX idx_metas_inspecao_colaborador
    ON metas_inspecao(id_colaborador)
    WHERE ativo = TRUE AND id_colaborador IS NOT NULL;

CREATE INDEX idx_metas_inspecao_vigencia
    ON metas_inspecao(vigencia_inicio, vigencia_fim)
    WHERE ativo = TRUE;

-- Acompanhamento das metas: conta as inspeções de cada inspetor na janela.
CREATE INDEX idx_inspecoes_inspetor_data
    ON inspecoes_seguranca(id_inspetor, data_realizacao)
    WHERE ativo = TRUE;
