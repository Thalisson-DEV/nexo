-- Tipo de estoque patrimonial associado a uma obra.
INSERT INTO tipos_estoque (
    id,
    nome,
    versao,
    data_criacao,
    usuario_criacao,
    ativo
)
VALUES (
    gen_random_uuid(),
    'OBRA',
    0,
    CURRENT_TIMESTAMP,
    'SISTEMA',
    TRUE
)
ON CONFLICT (nome) DO NOTHING;

-- Contexto, planejamento e estado operacional da solicitacao.
ALTER TABLE solicitacoes_material
    ADD COLUMN contexto VARCHAR(20),
    ADD COLUMN id_agrupador UUID,
    ADD COLUMN tipo_tecnico VARCHAR(3),
    ADD COLUMN id_obra UUID,
    ADD COLUMN tipo_obra_snapshot VARCHAR(20),
    ADD COLUMN ocorrencia VARCHAR(100),
    ADD COLUMN id_almoxarifado_atendimento UUID REFERENCES estoques(id),
    ADD COLUMN id_equipe_saque UUID,
    ADD COLUMN data_prevista_retirada DATE,
    ADD COLUMN observacao VARCHAR(1000),
    ADD COLUMN status_separacao VARCHAR(30),
    ADD COLUMN separacao_iniciada_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN id_usuario_inicio_separacao UUID,
    ADD COLUMN separacao_concluida_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN id_usuario_conclusao_separacao UUID,
    ADD COLUMN encerrada_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN id_usuario_encerramento UUID,
    ADD COLUMN motivo_encerramento VARCHAR(500),
    ADD COLUMN motivo_cancelamento VARCHAR(50);

ALTER TABLE solicitacoes_material
    ADD CONSTRAINT ck_solicitacoes_material_contexto
        CHECK (contexto IS NULL OR contexto IN ('COMERCIAL', 'TECNICO')),
    ADD CONSTRAINT ck_solicitacoes_material_tipo_tecnico
        CHECK (tipo_tecnico IS NULL OR tipo_tecnico IN ('221', '921', '222', '922')),
    ADD CONSTRAINT ck_solicitacoes_material_tipo_obra
        CHECK (tipo_obra_snapshot IS NULL OR tipo_obra_snapshot IN ('OBRA', 'CORRETIVA', 'PREVENTIVA', 'LPT')),
    ADD CONSTRAINT ck_solicitacoes_material_separacao
        CHECK (status_separacao IS NULL OR status_separacao IN (
            'AGUARDANDO_AGENDAMENTO',
            'PRONTO_PARA_SEPARACAO',
            'EM_SEPARACAO',
            'SEPARADO'
        )),
    ADD CONSTRAINT ck_solicitacoes_material_motivo_cancelamento
        CHECK (motivo_cancelamento IS NULL OR motivo_cancelamento IN ('REAGRUPAMENTO'));

-- Snapshots e acumuladores operacionais por item. O campo legado
-- quantidade_entregue continua sendo a projecao liquida comercial.
ALTER TABLE itens_solicitacao
    ADD COLUMN direcao VARCHAR(15),
    ADD COLUMN classificacao_tecnica_snapshot VARCHAR(20),
    ADD COLUMN quantidade_entregue_bruta NUMERIC(12, 3) NOT NULL DEFAULT 0,
    ADD COLUMN quantidade_entrega_estornada NUMERIC(12, 3) NOT NULL DEFAULT 0,
    ADD COLUMN quantidade_devolvida_bruta NUMERIC(12, 3) NOT NULL DEFAULT 0,
    ADD COLUMN quantidade_devolucao_estornada NUMERIC(12, 3) NOT NULL DEFAULT 0;

ALTER TABLE itens_solicitacao
    ADD CONSTRAINT ck_itens_solicitacao_direcao
        CHECK (direcao IS NULL OR direcao IN ('ADICAO', 'DEVOLUCAO')),
    ADD CONSTRAINT ck_itens_solicitacao_classificacao_tecnica
        CHECK (classificacao_tecnica_snapshot IS NULL OR classificacao_tecnica_snapshot IN ('COMUM', 'CONCRETO')),
    ADD CONSTRAINT ck_itens_solicitacao_acumuladores_nao_negativos
        CHECK (
            quantidade_entregue_bruta >= 0
            AND quantidade_entrega_estornada >= 0
            AND quantidade_devolvida_bruta >= 0
            AND quantidade_devolucao_estornada >= 0
        );
