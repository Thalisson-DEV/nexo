-- =====================================================================
-- V175: Motor de automações
--
-- As regras de notificação viram automações: um gatilho, condições com "e/ou",
-- variáveis, passos encadeados (e-mail, aviso no sininho, IA, consultas,
-- aprovação) e histórico por etapa. Esta migração cria as tabelas do motor e
-- prepara a conversão das regras existentes, que é feita pela aplicação na
-- inicialização (regra convertida guarda o id da automação e fica desligada).
--
-- Execuções e etapas não estendem a auditoria genérica (logs_sistema): são o
-- próprio registro de auditoria da automação, gravado passo a passo, e passariam
-- dezenas de linhas de log por execução.
-- =====================================================================

CREATE TABLE automacoes (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(500),
    tipo VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
    origem VARCHAR(30) NOT NULL DEFAULT 'USUARIO',
    id_dono UUID NOT NULL,
    numero_versao INTEGER NOT NULL DEFAULT 1,
    -- Cópias da versão atual para o tick e o listener não abrirem o JSON.
    tipo_gatilho VARCHAR(64) NOT NULL,
    chave_evento VARCHAR(80),
    ultimo_disparo TIMESTAMP WITH TIME ZONE,
    proxima_verificacao TIMESTAMP WITH TIME ZONE,
    ativada_em TIMESTAMP WITH TIME ZONE,
    id_regra_origem UUID,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_automacoes_tipo CHECK (tipo IN ('EMAIL', 'ALERTA', 'ROTINA', 'APROVACAO', 'DADOS_IA', 'GERAL')),
    CONSTRAINT ck_automacoes_status CHECK (status IN ('RASCUNHO', 'ATIVA', 'PAUSADA', 'ARQUIVADA')),
    CONSTRAINT ck_automacoes_origem CHECK (origem IN ('USUARIO', 'COPILOTO', 'SISTEMA', 'REGRA_CONVERTIDA'))
);

CREATE INDEX idx_automacoes_evento ON automacoes (chave_evento) WHERE status = 'ATIVA' AND ativo = TRUE;
CREATE INDEX idx_automacoes_gatilho ON automacoes (tipo_gatilho) WHERE status = 'ATIVA' AND ativo = TRUE;
CREATE UNIQUE INDEX uk_automacoes_regra_origem ON automacoes (id_regra_origem) WHERE id_regra_origem IS NOT NULL;

-- Cada edição salva grava uma versão nova; execuções apontam para a versão que rodaram.
CREATE TABLE automacao_versoes (
    id UUID PRIMARY KEY,
    id_automacao UUID NOT NULL REFERENCES automacoes (id),
    numero INTEGER NOT NULL,
    definicao JSONB NOT NULL,
    nota VARCHAR(300),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_automacao_versoes UNIQUE (id_automacao, numero)
);

CREATE TABLE automacao_execucoes (
    id UUID PRIMARY KEY,
    id_automacao UUID NOT NULL REFERENCES automacoes (id),
    id_versao UUID NOT NULL REFERENCES automacao_versoes (id),
    status VARCHAR(20) NOT NULL,
    modo VARCHAR(20) NOT NULL,
    -- Idempotência do disparo: o mesmo evento ou o mesmo horário não geram duas execuções.
    chave_disparo VARCHAR(200) NOT NULL,
    saida_gatilho JSONB,
    id_referencia UUID,
    iniciada_por UUID,
    email_testador VARCHAR(200),
    lease_ate TIMESTAMP WITH TIME ZONE,
    retomar_em TIMESTAMP WITH TIME ZONE,
    aguardando VARCHAR(30),
    cancelamento_pedido BOOLEAN NOT NULL DEFAULT FALSE,
    erro VARCHAR(2000),
    mensagem VARCHAR(500),
    passos_executados INTEGER NOT NULL DEFAULT 0,
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    iniciada_em TIMESTAMP WITH TIME ZONE,
    finalizada_em TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_automacao_execucoes_status CHECK (status IN
        ('NA_FILA', 'EXECUTANDO', 'AGUARDANDO', 'SUCESSO', 'FALHA', 'CANCELADA', 'TEMPO_ESGOTADO')),
    CONSTRAINT ck_automacao_execucoes_modo CHECK (modo IN ('AUTOMATICA', 'MANUAL', 'TESTE', 'REENVIO')),
    CONSTRAINT uk_automacao_execucoes_disparo UNIQUE (id_automacao, chave_disparo)
);

CREATE INDEX idx_automacao_execucoes_lista ON automacao_execucoes (id_automacao, criada_em DESC);
CREATE INDEX idx_automacao_execucoes_pendentes ON automacao_execucoes (status, retomar_em)
    WHERE status IN ('NA_FILA', 'EXECUTANDO', 'AGUARDANDO');

CREATE TABLE automacao_etapas (
    id UUID PRIMARY KEY,
    id_execucao UUID NOT NULL REFERENCES automacao_execucoes (id),
    no_id VARCHAR(40) NOT NULL,
    iteracao VARCHAR(400) NOT NULL DEFAULT '',
    tipo VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    tentativa INTEGER NOT NULL DEFAULT 0,
    entradas JSONB,
    saidas JSONB,
    erro VARCHAR(2000),
    inicio TIMESTAMP WITH TIME ZONE,
    fim TIMESTAMP WITH TIME ZONE,
    proxima_em TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_automacao_etapas UNIQUE (id_execucao, no_id, iteracao)
);

CREATE TABLE automacao_aprovacoes (
    id UUID PRIMARY KEY,
    id_execucao UUID NOT NULL REFERENCES automacao_execucoes (id),
    id_automacao UUID NOT NULL REFERENCES automacoes (id),
    no_id VARCHAR(40) NOT NULL,
    iteracao VARCHAR(400) NOT NULL DEFAULT '',
    titulo VARCHAR(200) NOT NULL,
    detalhes TEXT,
    opcoes JSONB NOT NULL,
    aprovadores JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    resposta VARCHAR(120),
    comentario VARCHAR(1000),
    respondida_por UUID,
    respondida_em TIMESTAMP WITH TIME ZONE,
    expira_em TIMESTAMP WITH TIME ZONE,
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_automacao_aprovacoes_status CHECK (status IN ('PENDENTE', 'DECIDIDA', 'EXPIRADA', 'CANCELADA'))
);

CREATE INDEX idx_automacao_aprovacoes_pendentes ON automacao_aprovacoes (status) WHERE status = 'PENDENTE';

-- Arquivos produzidos numa execução (ex.: o PDF do Copiloto) e anexados depois.
CREATE TABLE automacao_arquivos (
    id UUID PRIMARY KEY,
    id_execucao UUID NOT NULL REFERENCES automacao_execucoes (id),
    nome VARCHAR(255) NOT NULL,
    tipo_conteudo VARCHAR(120) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    conteudo BYTEA NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_automacao_arquivos_execucao ON automacao_arquivos (id_execucao);

-- "Não repetir o mesmo aviso por N minutos": o silêncio das regras antigas, por automação, passo e assunto.
CREATE TABLE automacao_silencios (
    id_automacao UUID NOT NULL REFERENCES automacoes (id),
    no_id VARCHAR(40) NOT NULL,
    chave VARCHAR(200) NOT NULL,
    ultimo_envio TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id_automacao, no_id, chave)
);

-- Avisos publicados por automações.
ALTER TABLE notificacoes DROP CONSTRAINT IF EXISTS ck_notificacoes_origem;
ALTER TABLE notificacoes ADD CONSTRAINT ck_notificacoes_origem
    CHECK (origem IN ('REGRA', 'COMUNICADO', 'SISTEMA', 'AUTOMACAO'));

-- Regra convertida: aponta para a automação que a substituiu.
ALTER TABLE regras_notificacao ADD COLUMN id_automacao UUID;

-- Permissões: gerenciar (criar, editar, ativar, executar) e acompanhar (ver e responder aprovações).
INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'automacao:manage'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'automacao:read')
ON CONFLICT (nome) DO NOTHING;

-- Quem já administrava regras de notificação passa a administrar automações.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT DISTINCT pp.id_perfil, nova.id
FROM perfil_permissoes pp
JOIN permissoes antiga ON antiga.id = pp.id_permissao AND antiga.nome = 'notificacao:regra:manage'
CROSS JOIN permissoes nova
WHERE nova.nome IN ('automacao:manage', 'automacao:read')
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome IN ('automacao:manage', 'automacao:read')
ON CONFLICT DO NOTHING;
