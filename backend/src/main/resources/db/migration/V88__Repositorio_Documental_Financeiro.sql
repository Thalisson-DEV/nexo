-- V88: Repositório documental do financeiro
--
-- Os arquivos do módulo viviam em duas tabelas separadas — uma por domínio de
-- solicitação — e pertenciam ao agregado da solicitação. Isso produzia dois
-- problemas que andam juntos:
--
--   1. O anexo herdava as travas de estado da solicitação. Como só era possível
--      anexar enquanto ela estava editável, não havia como anexar a nota fiscal
--      nem o boleto **depois da aprovação** — que é exatamente quando os dois
--      chegam.
--   2. Não existia lugar onde a contabilidade procurasse um documento. Achar uma
--      nota exigia saber de qual compra ou pagamento ela tinha vindo, e um boleto
--      recebido por e-mail, sem pedido correspondente, simplesmente não tinha
--      onde ser guardado.
--
-- Passa a existir uma tabela única, `financeiro_documento`, com o documento como
-- agregado próprio: a solicitação de origem é uma referência opcional, e o
-- documento carrega os metadados pelos quais se procura (natureza, número,
-- emissor, competência, vencimento, valor) e a trilha da conferência contábil.
--
-- As linhas existentes são migradas, não descartadas, e o contexto que faltava
-- nelas é preenchido a partir da solicitação de origem.

-- ═══════════════════════════════════════════════════════════════════════════════
-- Tabela
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE financeiro_documento (
    id UUID PRIMARY KEY,

    -- Arquivo. object_key é a chave no storage; não é URL pública (o download
    -- passa pelo backend). hash_conteudo é o SHA-256, que denuncia o mesmo
    -- arquivo reenviado por caminhos diferentes.
    nome_arquivo VARCHAR(255) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    hash_conteudo VARCHAR(64),

    -- Classificação
    tipo VARCHAR(30) NOT NULL,
    situacao VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    numero_documento VARCHAR(60),
    chave_acesso VARCHAR(44),

    -- Emissor: o cadastrado tem precedência; o nome livre atende quem nunca foi
    -- cadastrado.
    fornecedor_id UUID REFERENCES financeiro_fornecedor(id),
    emissor_nome VARCHAR(200),

    -- Datas e valor. A competência é gravada sempre no dia 1: é o mês que a
    -- contabilidade fecha, e ele não coincide com a emissão.
    data_emissao DATE,
    data_vencimento DATE,
    competencia DATE,
    valor NUMERIC(14, 2),

    -- Contexto copiado da solicitação no momento do envio. É foto, não join: se a
    -- solicitação for corrigida depois, o documento continua pertencendo ao
    -- rateio sob o qual foi aceito.
    base_id UUID,
    processo_id UUID,
    obra_id UUID,

    descricao TEXT,
    observacoes TEXT,

    -- Origem. Referência solta de propósito: o documento sobrevive ao ciclo da
    -- solicitação, e pode não ter nenhuma.
    origem_tipo VARCHAR(20),
    origem_solicitacao_id UUID,
    origem_numero BIGINT,

    -- Trilha
    usuario_envio_id UUID NOT NULL,
    usuario_conferencia_id UUID,
    data_conferencia TIMESTAMP WITH TIME ZONE,
    observacao_conferencia TEXT,
    motivo_pendencia TEXT,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE INDEX idx_documento_situacao ON financeiro_documento (situacao);
CREATE INDEX idx_documento_tipo ON financeiro_documento (tipo);
CREATE INDEX idx_documento_origem ON financeiro_documento (origem_solicitacao_id);
CREATE INDEX idx_documento_competencia ON financeiro_documento (competencia);
CREATE INDEX idx_documento_vencimento ON financeiro_documento (data_vencimento);
CREATE INDEX idx_documento_fornecedor ON financeiro_documento (fornecedor_id);

-- A detecção de duplicidade é uma agregação por hash; sem índice ela varre a
-- tabela toda a cada abertura da tela.
CREATE INDEX idx_documento_hash ON financeiro_documento (hash_conteudo);

-- A busca rápida compara texto sem acento e sem caixa (ver V40).
CREATE INDEX idx_documento_busca ON financeiro_documento
    USING gin (immutable_unaccent(lower(nome_arquivo)) gin_trgm_ops);

-- ═══════════════════════════════════════════════════════════════════════════════
-- Migração dos anexos existentes
--
-- O contexto que o anexo não tinha (fornecedor, valor, base, centro de custo,
-- obra, vencimento e o número da NF) é herdado da solicitação de origem — a mesma
-- herança que o envio novo faz em código. usuario_envio_id cai no solicitante
-- porque a tabela antiga guardava apenas o login de auditoria, não o id.
-- ═══════════════════════════════════════════════════════════════════════════════

INSERT INTO financeiro_documento (
    id, nome_arquivo, object_key, content_type, tamanho_bytes,
    tipo, situacao, numero_documento,
    fornecedor_id, emissor_nome,
    data_vencimento, competencia, valor,
    base_id, processo_id, obra_id,
    origem_tipo, origem_solicitacao_id, origem_numero,
    usuario_envio_id,
    versao, data_criacao, data_ultima_alteracao, usuario_criacao, usuario_ultima_alteracao,
    ativo, data_desativacao)
SELECT
    a.id, a.nome_arquivo, a.object_key, a.content_type, a.tamanho_bytes,
    a.tipo, 'PENDENTE',
    CASE WHEN a.tipo = 'NOTA_FISCAL' THEN s.numero_nf END,
    s.fornecedor_id, COALESCE(s.fornecedor_nome, s.fornecedor_razao_social),
    s.vencimento, date_trunc('month', COALESCE(s.vencimento, a.data_criacao::date))::date, s.valor,
    s.base_id, s.processo_id, s.obra_id,
    'COMPRA', s.id, s.numero,
    s.usuario_solicitante_id,
    0, a.data_criacao, a.data_ultima_alteracao, a.usuario_criacao, a.usuario_ultima_alteracao,
    a.ativo, a.data_desativacao
FROM financeiro_compra_anexo a
JOIN financeiro_solicitacao_compra s ON s.id = a.solicitacao_id;

INSERT INTO financeiro_documento (
    id, nome_arquivo, object_key, content_type, tamanho_bytes,
    tipo, situacao, numero_documento,
    fornecedor_id, emissor_nome,
    data_vencimento, competencia, valor,
    base_id, processo_id, obra_id,
    origem_tipo, origem_solicitacao_id, origem_numero,
    usuario_envio_id,
    versao, data_criacao, data_ultima_alteracao, usuario_criacao, usuario_ultima_alteracao,
    ativo, data_desativacao)
SELECT
    a.id, a.nome_arquivo, a.object_key, a.content_type, a.tamanho_bytes,
    a.tipo, 'PENDENTE',
    CASE WHEN a.tipo = 'NOTA_FISCAL' THEN s.numero_nf END,
    s.fornecedor_id, COALESCE(s.fornecedor_nome, s.fornecedor_razao_social),
    s.vencimento, date_trunc('month', COALESCE(s.vencimento, a.data_criacao::date))::date, s.valor,
    s.base_id, s.processo_id, s.obra_id,
    'PAGAMENTO', s.id, s.numero,
    s.usuario_solicitante_id,
    0, a.data_criacao, a.data_ultima_alteracao, a.usuario_criacao, a.usuario_ultima_alteracao,
    a.ativo, a.data_desativacao
FROM financeiro_pagamento_anexo a
JOIN financeiro_solicitacao_pagamento s ON s.id = a.solicitacao_id;

DROP TABLE IF EXISTS financeiro_compra_anexo CASCADE;
DROP TABLE IF EXISTS financeiro_pagamento_anexo CASCADE;

-- ═══════════════════════════════════════════════════════════════════════════════
-- Permissões
--
-- `conferir` nasce separada de `write` porque são papéis diferentes: quem envia o
-- documento é o solicitante ou o financeiro; quem aceita é a contabilidade. Fundir
-- as duas faria o aceite valer nada, já que quem envia se aprovaria sozinho.
-- ═══════════════════════════════════════════════════════════════════════════════

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
SELECT gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, nome
FROM (VALUES
    ('financeiro:documento:read'),
    ('financeiro:documento:write'),
    ('financeiro:documento:conferir'),
    -- Atuar sobre documento de terceiros (limpeza de envio equivocado)
    ('financeiro:documento:gerir'),
    ('financeiro:dashboard:read')
) AS novas(nome)
WHERE NOT EXISTS (SELECT 1 FROM permissoes p WHERE p.nome = novas.nome);

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT pa.id, p.id
FROM perfis_acesso pa
CROSS JOIN permissoes p
WHERE pa.nome = 'ADMINISTRADOR'
  AND p.nome IN ('financeiro:documento:read', 'financeiro:documento:write',
                 'financeiro:documento:conferir', 'financeiro:documento:gerir',
                 'financeiro:dashboard:read')
ON CONFLICT DO NOTHING;

-- Quem já lê solicitações passa a ler o repositório: são os mesmos documentos que
-- ele hoje só consegue ver abrindo cada solicitação uma a uma. O DISTINCT é
-- necessário — um mesmo perfil costuma ter as duas permissões de leitura, e sem
-- ele a linha seria proposta duas vezes.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT DISTINCT pp.id_perfil, novo.id
FROM perfil_permissoes pp
JOIN permissoes existente ON existente.id = pp.id_permissao
CROSS JOIN permissoes novo
WHERE novo.nome = 'financeiro:documento:read'
  AND existente.nome IN ('financeiro:compra:read', 'financeiro:pagamento:read')
ON CONFLICT DO NOTHING;

-- Quem já abre solicitação continua conseguindo anexar. Sem esta herança, a
-- migração tiraria de todo mundo uma capacidade que já existia.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT DISTINCT pp.id_perfil, novo.id
FROM perfil_permissoes pp
JOIN permissoes existente ON existente.id = pp.id_permissao
CROSS JOIN permissoes novo
WHERE novo.nome = 'financeiro:documento:write'
  AND existente.nome IN ('financeiro:compra:write', 'financeiro:pagamento:write')
ON CONFLICT DO NOTHING;

-- O dashboard vai para quem decide e para quem administra: são as pessoas que já
-- respondem pelos números que ele agrega. A conferência contábil fica só com o
-- ADMINISTRADOR, para ser distribuída de propósito a quem tem o papel.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT DISTINCT pp.id_perfil, novo.id
FROM perfil_permissoes pp
JOIN permissoes existente ON existente.id = pp.id_permissao
CROSS JOIN permissoes novo
WHERE novo.nome = 'financeiro:dashboard:read'
  AND existente.nome IN ('financeiro:compra:aprovar', 'financeiro:pagamento:aprovar',
                         'financeiro:compra:administrar', 'financeiro:pagamento:administrar')
ON CONFLICT DO NOTHING;
