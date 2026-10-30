-- V56: Módulo Financeiro — solicitações de compra e de pagamento com formulários moldáveis
--
-- Modelo híbrido: os campos com regra de negócio (valor, vencimento, parcelamento,
-- fornecedor, ...) têm coluna tipada no "núcleo" das solicitações, replicado em
-- compra e pagamento; os demais campos são criados pela configuração e gravados
-- como chave/valor nas tabelas *_valor_campo, sem migration.
--
-- Todas as tabelas seguem a BaseEntity (UUID, versão, auditoria, soft delete e
-- snapshot JSONB).

-- ═══════════════════════════════════════════════════════════════════════════════
-- Cadastros de apoio
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE financeiro_fornecedor (
    id UUID PRIMARY KEY,
    nome_fantasia VARCHAR(150) NOT NULL,
    razao_social VARCHAR(200),
    -- Somente dígitos: CNPJ (14) ou CPF (11). É a chave natural do cadastro.
    documento VARCHAR(14),
    -- 🔒 Cifrado em repouso (AES-256 GCM) — por isso não é pesquisável.
    dados_bancarios TEXT,
    observacoes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    motivo_bloqueio TEXT,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE UNIQUE INDEX uk_financeiro_fornecedor_documento
    ON financeiro_fornecedor (documento) WHERE documento IS NOT NULL;
CREATE INDEX idx_financeiro_fornecedor_busca
    ON financeiro_fornecedor USING gin (immutable_unaccent(lower(nome_fantasia)) gin_trgm_ops);

CREATE TABLE financeiro_alcada_aprovacao (
    id UUID PRIMARY KEY,
    tipo_solicitacao VARCHAR(20) NOT NULL,
    descricao VARCHAR(150) NOT NULL,
    valor_minimo NUMERIC(14, 2) NOT NULL,
    -- NULL representa faixa sem teto.
    valor_maximo NUMERIC(14, 2),
    permissao_requerida VARCHAR(100) NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB,

    CONSTRAINT ck_alcada_faixa CHECK (valor_maximo IS NULL OR valor_maximo >= valor_minimo)
);

CREATE INDEX idx_financeiro_alcada_tipo ON financeiro_alcada_aprovacao (tipo_solicitacao, valor_minimo);

-- ═══════════════════════════════════════════════════════════════════════════════
-- Template do formulário moldável
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE financeiro_formulario_solicitacao (
    id UUID PRIMARY KEY,
    tipo_solicitacao VARCHAR(20) NOT NULL,
    codigo VARCHAR(40) NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    descricao VARCHAR(500),
    padrao BOOLEAN NOT NULL DEFAULT FALSE,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB,

    CONSTRAINT uk_formulario_tipo_codigo UNIQUE (tipo_solicitacao, codigo)
);

CREATE TABLE financeiro_secao_formulario (
    id UUID PRIMARY KEY,
    formulario_id UUID NOT NULL REFERENCES financeiro_formulario_solicitacao(id),
    titulo VARCHAR(150) NOT NULL,
    descricao VARCHAR(500),
    ordem INTEGER NOT NULL DEFAULT 0,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE INDEX idx_financeiro_secao_formulario ON financeiro_secao_formulario (formulario_id, ordem);

CREATE TABLE financeiro_campo_formulario (
    id UUID PRIMARY KEY,
    secao_id UUID NOT NULL REFERENCES financeiro_secao_formulario(id),
    -- Chave publicada no schema e usada no payload; única dentro do formulário.
    chave VARCHAR(60) NOT NULL,
    -- Preenchido apenas nos campos apoiados numa coluna tipada do núcleo.
    campo_canonico VARCHAR(40),
    tipo_campo VARCHAR(20) NOT NULL,
    fonte_referencia VARCHAR(30),
    rotulo VARCHAR(150) NOT NULL,
    ajuda VARCHAR(500),
    placeholder VARCHAR(150),
    obrigatorio BOOLEAN NOT NULL DEFAULT FALSE,
    ordem INTEGER NOT NULL DEFAULT 0,
    tamanho_maximo INTEGER,
    valor_minimo NUMERIC(14, 4),
    valor_maximo NUMERIC(14, 4),
    -- Visibilidade condicional: chave do campo governante e valores que exibem este.
    condicao_campo_chave VARCHAR(60),
    condicao_valores VARCHAR(500),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE INDEX idx_financeiro_campo_secao ON financeiro_campo_formulario (secao_id, ordem);

CREATE TABLE financeiro_opcao_campo (
    id UUID PRIMARY KEY,
    campo_id UUID NOT NULL REFERENCES financeiro_campo_formulario(id),
    -- Valor persistido nas solicitações; o rótulo pode ser renomeado sem invalidá-las.
    valor VARCHAR(100) NOT NULL,
    rotulo VARCHAR(255) NOT NULL,
    ordem INTEGER NOT NULL DEFAULT 0,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE INDEX idx_financeiro_opcao_campo ON financeiro_opcao_campo (campo_id, ordem);

-- ═══════════════════════════════════════════════════════════════════════════════
-- Solicitação de compra
-- ═══════════════════════════════════════════════════════════════════════════════

-- Numeração legível: é por ela que as equipes se referem à solicitação, inclusive
-- ao vincular um pagamento a uma compra aprovada.
CREATE SEQUENCE seq_financeiro_compra_numero START WITH 1 INCREMENT BY 1;

CREATE TABLE financeiro_solicitacao_compra (
    id UUID PRIMARY KEY,
    numero BIGINT NOT NULL DEFAULT nextval('seq_financeiro_compra_numero'),
    formulario_id UUID NOT NULL REFERENCES financeiro_formulario_solicitacao(id),

    -- Núcleo tipado (superconjunto; cada variante usa o que lhe interessa)
    solicitante_colaborador_id UUID,
    base_id UUID,
    processo_id UUID,
    obra_id UUID,
    fornecedor_id UUID REFERENCES financeiro_fornecedor(id),
    fornecedor_nome VARCHAR(150),
    fornecedor_razao_social VARCHAR(200),
    valor NUMERIC(14, 2),
    quantidade NUMERIC(14, 4),
    prioridade VARCHAR(20),
    local_aquisicao VARCHAR(20),
    forma_pagamento VARCHAR(30),
    quantidade_parcelas INTEGER,
    vencimento DATE,
    dados_pagamento TEXT,          -- 🔒 cifrado em repouso
    tem_nf BOOLEAN,
    numero_nf VARCHAR(60),
    descricao TEXT,
    observacoes TEXT,
    veiculo_id UUID,
    regime_veiculo VARCHAR(20),

    -- Fluxo de autorização
    status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
    usuario_solicitante_id UUID NOT NULL,
    data_submissao TIMESTAMP WITH TIME ZONE,
    usuario_decisao_id UUID,
    data_decisao TIMESTAMP WITH TIME ZONE,
    valor_aprovado NUMERIC(14, 2),
    observacao_decisao TEXT,
    motivo_rejeicao TEXT,
    -- Snapshot da alçada aplicada, preservado mesmo que a faixa mude depois.
    alcada_id UUID,
    alcada_descricao VARCHAR(150),
    usuario_cancelamento_id UUID,
    data_cancelamento TIMESTAMP WITH TIME ZONE,
    motivo_cancelamento TEXT,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE UNIQUE INDEX idx_compra_numero ON financeiro_solicitacao_compra (numero);
CREATE INDEX idx_compra_status ON financeiro_solicitacao_compra (status);
CREATE INDEX idx_compra_solicitante ON financeiro_solicitacao_compra (usuario_solicitante_id);
CREATE INDEX idx_compra_base ON financeiro_solicitacao_compra (base_id);
CREATE INDEX idx_compra_fornecedor ON financeiro_solicitacao_compra (fornecedor_id);

CREATE TABLE financeiro_compra_valor_campo (
    id UUID PRIMARY KEY,
    solicitacao_id UUID NOT NULL REFERENCES financeiro_solicitacao_compra(id),
    chave VARCHAR(60) NOT NULL,
    valor_texto TEXT,
    valor_numero NUMERIC(18, 4),
    valor_data DATE,
    -- Posição dentro de um campo de seleção múltipla; zero nos demais.
    ordem INTEGER NOT NULL DEFAULT 0,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE INDEX idx_compra_valor_chave ON financeiro_compra_valor_campo (solicitacao_id, chave);

CREATE TABLE financeiro_compra_anexo (
    id UUID PRIMARY KEY,
    solicitacao_id UUID NOT NULL REFERENCES financeiro_solicitacao_compra(id),
    nome_arquivo VARCHAR(255) NOT NULL,
    -- Chave do objeto no storage; não é URL pública (o download passa pelo backend).
    object_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE INDEX idx_compra_anexo_solicitacao ON financeiro_compra_anexo (solicitacao_id);

-- ═══════════════════════════════════════════════════════════════════════════════
-- Solicitação de pagamento
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE SEQUENCE seq_financeiro_pagamento_numero START WITH 1 INCREMENT BY 1;

CREATE TABLE financeiro_solicitacao_pagamento (
    id UUID PRIMARY KEY,
    numero BIGINT NOT NULL DEFAULT nextval('seq_financeiro_pagamento_numero'),
    formulario_id UUID NOT NULL REFERENCES financeiro_formulario_solicitacao(id),

    -- Vínculo opcional com uma compra aprovada. Uma compra admite vários pagamentos,
    -- desde que a soma respeite o valor autorizado (regra aplicada na aplicação).
    possui_compra_previa BOOLEAN,
    solicitacao_compra_id UUID REFERENCES financeiro_solicitacao_compra(id),

    -- Núcleo tipado (mesmas colunas da compra)
    solicitante_colaborador_id UUID,
    base_id UUID,
    processo_id UUID,
    obra_id UUID,
    fornecedor_id UUID REFERENCES financeiro_fornecedor(id),
    fornecedor_nome VARCHAR(150),
    fornecedor_razao_social VARCHAR(200),
    valor NUMERIC(14, 2),
    quantidade NUMERIC(14, 4),
    prioridade VARCHAR(20),
    local_aquisicao VARCHAR(20),
    forma_pagamento VARCHAR(30),
    quantidade_parcelas INTEGER,
    vencimento DATE,
    dados_pagamento TEXT,          -- 🔒 cifrado em repouso
    tem_nf BOOLEAN,
    numero_nf VARCHAR(60),
    descricao TEXT,
    observacoes TEXT,
    veiculo_id UUID,
    regime_veiculo VARCHAR(20),

    -- Fluxo de autorização
    status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
    usuario_solicitante_id UUID NOT NULL,
    data_submissao TIMESTAMP WITH TIME ZONE,
    usuario_decisao_id UUID,
    data_decisao TIMESTAMP WITH TIME ZONE,
    valor_aprovado NUMERIC(14, 2),
    observacao_decisao TEXT,
    motivo_rejeicao TEXT,
    alcada_id UUID,
    alcada_descricao VARCHAR(150),
    usuario_cancelamento_id UUID,
    data_cancelamento TIMESTAMP WITH TIME ZONE,
    motivo_cancelamento TEXT,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE UNIQUE INDEX idx_pagamento_numero ON financeiro_solicitacao_pagamento (numero);
CREATE INDEX idx_pagamento_status ON financeiro_solicitacao_pagamento (status);
CREATE INDEX idx_pagamento_compra ON financeiro_solicitacao_pagamento (solicitacao_compra_id);
CREATE INDEX idx_pagamento_solicitante ON financeiro_solicitacao_pagamento (usuario_solicitante_id);
CREATE INDEX idx_pagamento_vencimento ON financeiro_solicitacao_pagamento (vencimento);

CREATE TABLE financeiro_pagamento_valor_campo (
    id UUID PRIMARY KEY,
    solicitacao_id UUID NOT NULL REFERENCES financeiro_solicitacao_pagamento(id),
    chave VARCHAR(60) NOT NULL,
    valor_texto TEXT,
    valor_numero NUMERIC(18, 4),
    valor_data DATE,
    ordem INTEGER NOT NULL DEFAULT 0,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE INDEX idx_pagamento_valor_chave ON financeiro_pagamento_valor_campo (solicitacao_id, chave);

CREATE TABLE financeiro_pagamento_anexo (
    id UUID PRIMARY KEY,
    solicitacao_id UUID NOT NULL REFERENCES financeiro_solicitacao_pagamento(id),
    nome_arquivo VARCHAR(255) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE INDEX idx_pagamento_anexo_solicitacao ON financeiro_pagamento_anexo (solicitacao_id);

-- Cronograma derivado de valor + parcelamento + 1º vencimento. Materializado para
-- que o financeiro possa liquidar vencimento a vencimento.
CREATE TABLE financeiro_parcela_pagamento (
    id UUID PRIMARY KEY,
    solicitacao_id UUID NOT NULL REFERENCES financeiro_solicitacao_pagamento(id),
    numero INTEGER NOT NULL,
    valor NUMERIC(14, 2) NOT NULL,
    vencimento DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    data_pagamento TIMESTAMP WITH TIME ZONE,
    usuario_baixa_id UUID,
    observacao_baixa TEXT,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE INDEX idx_parcela_solicitacao ON financeiro_parcela_pagamento (solicitacao_id, numero);
CREATE INDEX idx_parcela_vencimento ON financeiro_parcela_pagamento (vencimento, status);
