-- V85: Simplificação do módulo Financeiro
--
-- O desenho anterior era híbrido: parte dos campos tinha coluna tipada no núcleo e
-- parte era criada pela configuração e gravada como chave/valor, com opções,
-- limites e condições também cadastráveis. Na prática isso permitia configurar
-- estados inconsistentes (uma condição apontando para um campo removido, um limite
-- numérico num campo de texto) e obrigava a manter dois regimes de validação.
--
-- Agora **todo campo é canônico**: o catálogo em código (CampoCanonico) é a fonte
-- única de chave, tipo, fonte de referência, opções, limites e condição de
-- visibilidade. A configuração da variante decide apenas apresentação — se o campo
-- aparece, com que rótulo, em que ordem e se é obrigatório. Some, com isso, toda a
-- estrutura de seções, opções, valores chave/valor, alçadas e parcelas.
--
-- Como não há dados em produção, as tabelas são recriadas em vez de migradas. O
-- cadastro de fornecedores é preservado: não faz parte desta mudança.

-- ═══════════════════════════════════════════════════════════════════════════════
-- Remoção do desenho antigo
-- ═══════════════════════════════════════════════════════════════════════════════

DROP TABLE IF EXISTS financeiro_parcela_pagamento CASCADE;
DROP TABLE IF EXISTS financeiro_pagamento_valor_campo CASCADE;
DROP TABLE IF EXISTS financeiro_pagamento_anexo CASCADE;
DROP TABLE IF EXISTS financeiro_solicitacao_pagamento CASCADE;

DROP TABLE IF EXISTS financeiro_compra_valor_campo CASCADE;
DROP TABLE IF EXISTS financeiro_compra_anexo CASCADE;
DROP TABLE IF EXISTS financeiro_solicitacao_compra CASCADE;

DROP TABLE IF EXISTS financeiro_opcao_campo CASCADE;
DROP TABLE IF EXISTS financeiro_campo_formulario CASCADE;
DROP TABLE IF EXISTS financeiro_secao_formulario CASCADE;
DROP TABLE IF EXISTS financeiro_formulario_solicitacao CASCADE;

DROP TABLE IF EXISTS financeiro_alcada_aprovacao CASCADE;

-- As sequências não pertencem às tabelas (são independentes), então sobrevivem ao
-- DROP e precisam ser recriadas para que a numeração recomece do zero.
DROP SEQUENCE IF EXISTS seq_financeiro_compra_numero;
DROP SEQUENCE IF EXISTS seq_financeiro_pagamento_numero;

-- ═══════════════════════════════════════════════════════════════════════════════
-- Template do formulário: variante + campos, sem nível intermediário
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

-- Só o que a configuração de fato decide. Chave, tipo, fonte de referência,
-- opções, limites e condição de exibição são propriedades do campo canônico e
-- vivem em código — não há coluna para elas aqui.
CREATE TABLE financeiro_campo_formulario (
    id UUID PRIMARY KEY,
    formulario_id UUID NOT NULL REFERENCES financeiro_formulario_solicitacao(id),
    campo_canonico VARCHAR(40) NOT NULL,
    rotulo VARCHAR(150) NOT NULL,
    ajuda VARCHAR(500),
    placeholder VARCHAR(150),
    obrigatorio BOOLEAN NOT NULL DEFAULT FALSE,
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

CREATE INDEX idx_financeiro_campo_formulario ON financeiro_campo_formulario (formulario_id, ordem);

-- Cada canônico grava numa coluna própria: duas ocorrências na mesma variante
-- disputariam a mesma coluna. O domínio já recusa; o banco é a última linha.
CREATE UNIQUE INDEX uk_campo_formulario_canonico
    ON financeiro_campo_formulario (formulario_id, campo_canonico);

-- ═══════════════════════════════════════════════════════════════════════════════
-- Solicitação de compra
-- ═══════════════════════════════════════════════════════════════════════════════

-- Numeração legível: é por ela que as equipes se referem à solicitação.
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
    tipo_despesa VARCHAR(20),
    tem_nf BOOLEAN,
    numero_nf VARCHAR(60),
    justificativa_sem_nf TEXT,
    descricao TEXT,
    observacoes TEXT,
    veiculo_id UUID,
    regime_veiculo VARCHAR(20),
    natureza_servico VARCHAR(40),
    motivo_servico VARCHAR(30),
    possui_compra_previa BOOLEAN,
    solicitacao_compra_id UUID,
    motivo_sem_compra VARCHAR(40),

    -- Fluxo de autorização. Quem pode decidir é questão de permissão, não de valor.
    status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
    usuario_solicitante_id UUID NOT NULL,
    data_submissao TIMESTAMP WITH TIME ZONE,
    usuario_decisao_id UUID,
    data_decisao TIMESTAMP WITH TIME ZONE,
    valor_aprovado NUMERIC(14, 2),
    observacao_decisao TEXT,
    motivo_rejeicao TEXT,
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
--
-- O vínculo com a compra é uma referência simples em solicitacao_compra_id — sem
-- chave estrangeira, sem saldo e sem trava de valor. Serve para rastrear a origem
-- e para herdar o preenchimento; o que se paga pode legitimamente diferir do que
-- se autorizou comprar.
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE SEQUENCE seq_financeiro_pagamento_numero START WITH 1 INCREMENT BY 1;

CREATE TABLE financeiro_solicitacao_pagamento (
    id UUID PRIMARY KEY,
    numero BIGINT NOT NULL DEFAULT nextval('seq_financeiro_pagamento_numero'),
    formulario_id UUID NOT NULL REFERENCES financeiro_formulario_solicitacao(id),

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
    tipo_despesa VARCHAR(20),
    tem_nf BOOLEAN,
    numero_nf VARCHAR(60),
    justificativa_sem_nf TEXT,
    descricao TEXT,
    observacoes TEXT,
    veiculo_id UUID,
    regime_veiculo VARCHAR(20),
    natureza_servico VARCHAR(40),
    motivo_servico VARCHAR(30),
    possui_compra_previa BOOLEAN,
    solicitacao_compra_id UUID,
    motivo_sem_compra VARCHAR(40),

    -- Fluxo de autorização
    status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
    usuario_solicitante_id UUID NOT NULL,
    data_submissao TIMESTAMP WITH TIME ZONE,
    usuario_decisao_id UUID,
    data_decisao TIMESTAMP WITH TIME ZONE,
    valor_aprovado NUMERIC(14, 2),
    observacao_decisao TEXT,
    motivo_rejeicao TEXT,
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

-- ═══════════════════════════════════════════════════════════════════════════════
-- Carga das quatro variantes
--
-- Reproduz os quatro formulários originais. Repare que não há mais opções nem
-- condições a semear: "Natureza", "Motivo", "Tipo de Despesa" e "Motivo de não
-- haver compra" viraram campos canônicos apoiados em enumerações, e as condições
-- de visibilidade vivem no catálogo em código.
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE FUNCTION pg_temp.f_form(p_tipo TEXT, p_codigo TEXT, p_titulo TEXT, p_descricao TEXT, p_padrao BOOLEAN)
RETURNS UUID AS $$
DECLARE v_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO financeiro_formulario_solicitacao
        (id, tipo_solicitacao, codigo, titulo, descricao, padrao, versao, data_criacao, ativo, usuario_criacao)
    VALUES (v_id, p_tipo, p_codigo, p_titulo, p_descricao, p_padrao, 0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA');
    RETURN v_id;
END; $$ LANGUAGE plpgsql;

-- Acrescenta o campo ao fim da variante, como faz FormularioSolicitacao.adicionarCampo.
CREATE FUNCTION pg_temp.f_campo(p_form UUID, p_canonico TEXT, p_rotulo TEXT, p_obrigatorio BOOLEAN)
RETURNS VOID AS $$
BEGIN
    INSERT INTO financeiro_campo_formulario
        (id, formulario_id, campo_canonico, rotulo, obrigatorio, ordem,
         versao, data_criacao, ativo, usuario_criacao)
    VALUES (gen_random_uuid(), p_form, p_canonico, p_rotulo, p_obrigatorio,
            (SELECT COUNT(*) FROM financeiro_campo_formulario WHERE formulario_id = p_form),
            0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA');
END; $$ LANGUAGE plpgsql;

DO $seed$
DECLARE
    v_form UUID;
BEGIN

-- ───────────────────────────────────────────────────────────────────────────────
-- COMPRA / PADRAO
-- ───────────────────────────────────────────────────────────────────────────────
v_form := pg_temp.f_form('COMPRA', 'PADRAO', 'Solicitação de Compra',
    'Pedido de autorização para aquisição de material ou serviço.', TRUE);

PERFORM pg_temp.f_campo(v_form, 'SOLICITANTE_COLABORADOR', 'Nome do Solicitante', TRUE);
PERFORM pg_temp.f_campo(v_form, 'BASE', 'BASE', TRUE);
PERFORM pg_temp.f_campo(v_form, 'PROCESSO', 'Setor / Centro de Custo', TRUE);
PERFORM pg_temp.f_campo(v_form, 'FORNECEDOR', 'Fornecedor (cadastrado)', FALSE);
PERFORM pg_temp.f_campo(v_form, 'FORNECEDOR_NOME', 'Fornecedor (não cadastrado)', FALSE);
PERFORM pg_temp.f_campo(v_form, 'FORNECEDOR_RAZAO_SOCIAL', 'Razão Social', FALSE);
PERFORM pg_temp.f_campo(v_form, 'QUANTIDADE', 'Quantidade', TRUE);
PERFORM pg_temp.f_campo(v_form, 'VALOR', 'Valor', TRUE);
PERFORM pg_temp.f_campo(v_form, 'PRIORIDADE', 'Grau de Prioridade', TRUE);
PERFORM pg_temp.f_campo(v_form, 'LOCAL_AQUISICAO', 'Local de Aquisição', TRUE);
PERFORM pg_temp.f_campo(v_form, 'FORMA_PAGAMENTO', 'Forma de Pagamento', TRUE);
PERFORM pg_temp.f_campo(v_form, 'QUANTIDADE_PARCELAS', 'Parcelamento', TRUE);
PERFORM pg_temp.f_campo(v_form, 'DESCRICAO', 'Descrição da Compra / Serviço', TRUE);
PERFORM pg_temp.f_campo(v_form, 'TEM_NF', 'Tem NF', TRUE);
PERFORM pg_temp.f_campo(v_form, 'JUSTIFICATIVA_SEM_NF', 'Justificativa por não haver NF', TRUE);
PERFORM pg_temp.f_campo(v_form, 'OBSERVACOES', 'Observações, OS ou Rateio', FALSE);

-- ───────────────────────────────────────────────────────────────────────────────
-- COMPRA / FROTA
-- ───────────────────────────────────────────────────────────────────────────────
v_form := pg_temp.f_form('COMPRA', 'FROTA', 'Solicitação de Compra — Frota',
    'Manutenção, peças e serviços de veículos.', FALSE);

PERFORM pg_temp.f_campo(v_form, 'SOLICITANTE_COLABORADOR', 'Nome do Solicitante', TRUE);
PERFORM pg_temp.f_campo(v_form, 'BASE', 'BASE', TRUE);
PERFORM pg_temp.f_campo(v_form, 'PROCESSO', 'Setor / Centro de Custo', TRUE);
PERFORM pg_temp.f_campo(v_form, 'NATUREZA_SERVICO', 'Natureza', TRUE);
PERFORM pg_temp.f_campo(v_form, 'MOTIVO_SERVICO', 'Motivo', TRUE);
PERFORM pg_temp.f_campo(v_form, 'VEICULO', 'Placa', TRUE);
PERFORM pg_temp.f_campo(v_form, 'REGIME_VEICULO', 'Veículo Próprio ou Locado?', TRUE);
PERFORM pg_temp.f_campo(v_form, 'FORNECEDOR', 'Fornecedor (cadastrado)', FALSE);
PERFORM pg_temp.f_campo(v_form, 'FORNECEDOR_NOME', 'Fornecedor (não cadastrado)', FALSE);
PERFORM pg_temp.f_campo(v_form, 'FORNECEDOR_RAZAO_SOCIAL', 'Razão Social', FALSE);
PERFORM pg_temp.f_campo(v_form, 'QUANTIDADE', 'Quantidade', TRUE);
PERFORM pg_temp.f_campo(v_form, 'VALOR', 'Valor', TRUE);
PERFORM pg_temp.f_campo(v_form, 'PRIORIDADE', 'Grau de Prioridade', TRUE);
PERFORM pg_temp.f_campo(v_form, 'LOCAL_AQUISICAO', 'Local de Aquisição', TRUE);
PERFORM pg_temp.f_campo(v_form, 'FORMA_PAGAMENTO', 'Forma de Pagamento', TRUE);
PERFORM pg_temp.f_campo(v_form, 'QUANTIDADE_PARCELAS', 'Parcelamento', TRUE);
PERFORM pg_temp.f_campo(v_form, 'DESCRICAO', 'Descrição da Compra / Serviço', TRUE);
PERFORM pg_temp.f_campo(v_form, 'TEM_NF', 'Tem NF', TRUE);
PERFORM pg_temp.f_campo(v_form, 'JUSTIFICATIVA_SEM_NF', 'Justificativa por não haver NF', TRUE);
PERFORM pg_temp.f_campo(v_form, 'OBSERVACOES', 'Observações, OS ou Rateio', FALSE);

-- ───────────────────────────────────────────────────────────────────────────────
-- COMPRA / OBRA_CIVIL
-- ───────────────────────────────────────────────────────────────────────────────
v_form := pg_temp.f_form('COMPRA', 'OBRA_CIVIL', 'Aprovação de Compra — Obras Civis',
    'Aquisições apropriadas a uma obra. Substitui a base pelo contrato da obra.', FALSE);

PERFORM pg_temp.f_campo(v_form, 'SOLICITANTE_COLABORADOR', 'Solicitante', TRUE);
PERFORM pg_temp.f_campo(v_form, 'OBRA', 'Obra', TRUE);
PERFORM pg_temp.f_campo(v_form, 'PROCESSO', 'Setor / Centro de Custo', FALSE);
PERFORM pg_temp.f_campo(v_form, 'FORNECEDOR', 'Fornecedor (cadastrado)', FALSE);
PERFORM pg_temp.f_campo(v_form, 'FORNECEDOR_NOME', 'Fornecedor (não cadastrado)', FALSE);
PERFORM pg_temp.f_campo(v_form, 'FORNECEDOR_RAZAO_SOCIAL', 'Razão Social', FALSE);
PERFORM pg_temp.f_campo(v_form, 'QUANTIDADE', 'Quantidade', TRUE);
PERFORM pg_temp.f_campo(v_form, 'VALOR', 'Valor', TRUE);
PERFORM pg_temp.f_campo(v_form, 'LOCAL_AQUISICAO', 'Local de Aquisição', TRUE);
PERFORM pg_temp.f_campo(v_form, 'FORMA_PAGAMENTO', 'Forma de Pagamento', TRUE);
PERFORM pg_temp.f_campo(v_form, 'QUANTIDADE_PARCELAS', 'Parcelamento', TRUE);
PERFORM pg_temp.f_campo(v_form, 'VENCIMENTO', 'Vencimento', TRUE);
PERFORM pg_temp.f_campo(v_form, 'DADOS_PAGAMENTO', 'Dados para pagamento (PIX, AG, C/C)', FALSE);
PERFORM pg_temp.f_campo(v_form, 'DESCRICAO', 'Descrição da Compra / Serviço', TRUE);
PERFORM pg_temp.f_campo(v_form, 'TEM_NF', 'Tem NF', TRUE);
PERFORM pg_temp.f_campo(v_form, 'JUSTIFICATIVA_SEM_NF', 'Justificativa por não haver NF', TRUE);
PERFORM pg_temp.f_campo(v_form, 'OBSERVACOES', 'Observação, Rateio, OS, etc.', FALSE);

-- ───────────────────────────────────────────────────────────────────────────────
-- PAGAMENTO / PADRAO
--
-- Um único formulário cobre os dois casos originais: a pergunta de origem governa
-- a exibição do vínculo com a compra ou do motivo de não haver compra. Os campos
-- herdados continuam visíveis e são pré-preenchidos pelo endpoint de
-- pré-preenchimento, em vez de sumirem da tela.
-- ───────────────────────────────────────────────────────────────────────────────
v_form := pg_temp.f_form('PAGAMENTO', 'PADRAO', 'Solicitação de Pagamento',
    'Pedido de autorização de pagamento, com ou sem compra prévia.', TRUE);

PERFORM pg_temp.f_campo(v_form, 'POSSUI_COMPRA_PREVIA', 'Já foi feita solicitação de compra antes?', TRUE);
PERFORM pg_temp.f_campo(v_form, 'SOLICITACAO_COMPRA', 'Solicitação de compra aprovada', TRUE);
PERFORM pg_temp.f_campo(v_form, 'MOTIVO_SEM_COMPRA', 'Motivo de não haver solicitação de compra', TRUE);
PERFORM pg_temp.f_campo(v_form, 'SOLICITANTE_COLABORADOR', 'Nome do Solicitante do Pagamento', TRUE);
PERFORM pg_temp.f_campo(v_form, 'BASE', 'Base', TRUE);
PERFORM pg_temp.f_campo(v_form, 'PROCESSO', 'Setor / Centro de Custo', TRUE);
PERFORM pg_temp.f_campo(v_form, 'OBRA', 'Obra', FALSE);
PERFORM pg_temp.f_campo(v_form, 'FORNECEDOR', 'Fornecedor (cadastrado)', FALSE);
PERFORM pg_temp.f_campo(v_form, 'FORNECEDOR_NOME', 'Fornecedor (não cadastrado)', FALSE);
PERFORM pg_temp.f_campo(v_form, 'FORNECEDOR_RAZAO_SOCIAL', 'Razão Social', FALSE);
PERFORM pg_temp.f_campo(v_form, 'VALOR', 'Valor', TRUE);
PERFORM pg_temp.f_campo(v_form, 'VENCIMENTO', 'Vencimento único ou 1ª parcela', TRUE);
PERFORM pg_temp.f_campo(v_form, 'FORMA_PAGAMENTO', 'Forma de Pagamento', TRUE);
PERFORM pg_temp.f_campo(v_form, 'QUANTIDADE_PARCELAS', 'Parcelamento', TRUE);
PERFORM pg_temp.f_campo(v_form, 'DADOS_PAGAMENTO', 'Dados Bancários (Banco, AG, Conta, Pix)', TRUE);
PERFORM pg_temp.f_campo(v_form, 'TIPO_DESPESA', 'Tipo de Despesa', TRUE);
PERFORM pg_temp.f_campo(v_form, 'TEM_NF', 'Tem NF', TRUE);
PERFORM pg_temp.f_campo(v_form, 'NUMERO_NF', 'nº Nota Fiscal', TRUE);
PERFORM pg_temp.f_campo(v_form, 'JUSTIFICATIVA_SEM_NF', 'Justificativa por não haver NF', TRUE);
PERFORM pg_temp.f_campo(v_form, 'OBSERVACOES', 'Observações, OS ou Rateio', FALSE);

END $seed$;
