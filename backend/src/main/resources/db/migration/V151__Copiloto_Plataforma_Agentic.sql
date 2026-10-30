-- Copiloto como plataforma agentic: historico navegavel de conversas, timeline de ferramentas,
-- anexos, resultados tabulares reutilizaveis, artefatos e observabilidade por execucao.
-- O modelo nunca recebe acesso direto a estas tabelas.

-- ---------------------------------------------------------------------------------------------
-- Conversas: ordenacao por atividade, contexto de origem no Nexo e titulo gerado automaticamente.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE assistente_conversa ADD COLUMN IF NOT EXISTS ultima_atividade_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE assistente_conversa ADD COLUMN IF NOT EXISTS contexto_json TEXT;
ALTER TABLE assistente_conversa ADD COLUMN IF NOT EXISTS titulo_definido_pelo_usuario BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE assistente_conversa
SET ultima_atividade_em = COALESCE(data_ultima_alteracao, data_criacao)
WHERE ultima_atividade_em IS NULL;

ALTER TABLE assistente_conversa ALTER COLUMN ultima_atividade_em SET NOT NULL;
ALTER TABLE assistente_conversa ALTER COLUMN ultima_atividade_em SET DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_assistente_conversa_usuario_atividade
    ON assistente_conversa (usuario_id, ultima_atividade_em DESC)
    WHERE ativo = TRUE;

-- ---------------------------------------------------------------------------------------------
-- Mensagens: pacotes de streaming (replay da timeline), anexos e metricas do provedor.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE assistente_mensagem ADD COLUMN IF NOT EXISTS pacotes_json TEXT;
ALTER TABLE assistente_mensagem ADD COLUMN IF NOT EXISTS arquivos_json TEXT;
ALTER TABLE assistente_mensagem ADD COLUMN IF NOT EXISTS modelo VARCHAR(160);
ALTER TABLE assistente_mensagem ADD COLUMN IF NOT EXISTS tokens_entrada INTEGER;
ALTER TABLE assistente_mensagem ADD COLUMN IF NOT EXISTS tokens_saida INTEGER;
ALTER TABLE assistente_mensagem ADD COLUMN IF NOT EXISTS custo_estimado NUMERIC(14, 6);
ALTER TABLE assistente_mensagem ADD COLUMN IF NOT EXISTS duracao_ms BIGINT;
ALTER TABLE assistente_mensagem ADD COLUMN IF NOT EXISTS ciclos INTEGER;
ALTER TABLE assistente_mensagem ADD COLUMN IF NOT EXISTS chamadas_ferramenta INTEGER;
ALTER TABLE assistente_mensagem ADD COLUMN IF NOT EXISTS erro VARCHAR(500);

-- ---------------------------------------------------------------------------------------------
-- Execucoes de ferramentas: a tabela criada na V68 nunca foi escrita. Passa a registrar toda
-- chamada do agente (auditoria de SQL, Python, web, dados), sem dados pessoais nos argumentos.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE assistente_execucao_ferramenta ALTER COLUMN dataset DROP NOT NULL;
ALTER TABLE assistente_execucao_ferramenta ALTER COLUMN dataset_versao DROP NOT NULL;
ALTER TABLE assistente_execucao_ferramenta ADD COLUMN IF NOT EXISTS conversa_id UUID;
ALTER TABLE assistente_execucao_ferramenta ADD COLUMN IF NOT EXISTS usuario_id UUID;
ALTER TABLE assistente_execucao_ferramenta ADD COLUMN IF NOT EXISTS ferramenta VARCHAR(120);
ALTER TABLE assistente_execucao_ferramenta ADD COLUMN IF NOT EXISTS tool_call_id VARCHAR(160);
ALTER TABLE assistente_execucao_ferramenta ADD COLUMN IF NOT EXISTS ciclo INTEGER;
ALTER TABLE assistente_execucao_ferramenta ADD COLUMN IF NOT EXISTS risco VARCHAR(20);
ALTER TABLE assistente_execucao_ferramenta ADD COLUMN IF NOT EXISTS argumentos_json TEXT;
ALTER TABLE assistente_execucao_ferramenta ADD COLUMN IF NOT EXISTS resumo VARCHAR(1000);
ALTER TABLE assistente_execucao_ferramenta ADD COLUMN IF NOT EXISTS erro VARCHAR(1000);
ALTER TABLE assistente_execucao_ferramenta ADD COLUMN IF NOT EXISTS resultado_codigo VARCHAR(20);
ALTER TABLE assistente_execucao_ferramenta ADD COLUMN IF NOT EXISTS linhas INTEGER;

CREATE INDEX IF NOT EXISTS idx_assistente_execucao_conversa
    ON assistente_execucao_ferramenta (conversa_id, data_criacao);
CREATE INDEX IF NOT EXISTS idx_assistente_execucao_ferramenta_nome
    ON assistente_execucao_ferramenta (ferramenta, data_criacao);

-- ---------------------------------------------------------------------------------------------
-- Arquivos: anexos enviados pelo usuario e arquivos gerados (PDF, XLSX, CSV, graficos do Python).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assistente_arquivo (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    conversa_id UUID REFERENCES assistente_conversa(id),
    mensagem_id UUID,
    codigo VARCHAR(20),
    origem VARCHAR(20) NOT NULL,
    nome VARCHAR(255) NOT NULL,
    tipo_conteudo VARCHAR(160) NOT NULL,
    extensao VARCHAR(20),
    categoria VARCHAR(30) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    armazenamento VARCHAR(20) NOT NULL,
    chave_armazenamento VARCHAR(300),
    status VARCHAR(20) NOT NULL,
    resumo_json TEXT,
    texto_extraido TEXT,
    dados_json TEXT,
    erro VARCHAR(500),
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_assistente_arquivo_origem CHECK (origem IN ('UPLOAD', 'GERADO')),
    CONSTRAINT ck_assistente_arquivo_armazenamento CHECK (armazenamento IN ('BANCO', 'R2'))
);

CREATE INDEX IF NOT EXISTS idx_assistente_arquivo_conversa
    ON assistente_arquivo (conversa_id, data_criacao)
    WHERE ativo = TRUE;
CREATE INDEX IF NOT EXISTS idx_assistente_arquivo_usuario
    ON assistente_arquivo (usuario_id, data_criacao DESC)
    WHERE ativo = TRUE;

CREATE TABLE IF NOT EXISTS assistente_arquivo_conteudo (
    arquivo_id UUID PRIMARY KEY REFERENCES assistente_arquivo(id),
    conteudo BYTEA NOT NULL
);

-- ---------------------------------------------------------------------------------------------
-- Resultados tabulares (r1, r2...): toda consulta, calculo ou leitura de planilha fica registrada
-- na conversa, para graficos, dashboards, relatorios e comparacoes posteriores sem reconsulta.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assistente_resultado (
    id UUID PRIMARY KEY,
    conversa_id UUID NOT NULL REFERENCES assistente_conversa(id),
    mensagem_id UUID,
    codigo VARCHAR(20) NOT NULL,
    origem VARCHAR(120) NOT NULL,
    natureza VARCHAR(20) NOT NULL,
    titulo VARCHAR(300) NOT NULL,
    colunas_json TEXT NOT NULL,
    linhas_json TEXT NOT NULL,
    total_linhas INTEGER NOT NULL,
    truncado BOOLEAN NOT NULL DEFAULT FALSE,
    consulta_json TEXT,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_assistente_resultado_codigo UNIQUE (conversa_id, codigo)
);

-- ---------------------------------------------------------------------------------------------
-- Artefatos: dashboards, graficos, tabelas, documentos e relatorios em especificacao declarativa.
-- O frontend renderiza somente componentes confiaveis; nenhum script do modelo e executado.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assistente_artefato (
    id UUID PRIMARY KEY,
    conversa_id UUID NOT NULL REFERENCES assistente_conversa(id),
    mensagem_id UUID,
    codigo VARCHAR(20) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    titulo VARCHAR(300) NOT NULL,
    descricao VARCHAR(1000),
    especificacao_json TEXT NOT NULL,
    arquivo_id UUID REFERENCES assistente_arquivo(id),
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_assistente_artefato_codigo UNIQUE (conversa_id, codigo)
);

CREATE INDEX IF NOT EXISTS idx_assistente_artefato_conversa
    ON assistente_artefato (conversa_id, data_criacao)
    WHERE ativo = TRUE;

-- ---------------------------------------------------------------------------------------------
-- Acoes de escrita futuras: plano -> preview -> confirmacao -> execucao -> auditoria.
-- Nenhuma ferramenta de escrita e registrada por padrao; a tabela prepara o fluxo auditavel.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assistente_acao_pendente (
    id UUID PRIMARY KEY,
    conversa_id UUID NOT NULL REFERENCES assistente_conversa(id),
    mensagem_id UUID,
    usuario_id UUID NOT NULL,
    ferramenta VARCHAR(120) NOT NULL,
    argumentos_json TEXT NOT NULL,
    previa_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    resultado_json TEXT,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    decidida_em TIMESTAMP WITH TIME ZONE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_assistente_acao_pendente_status
        CHECK (status IN ('PENDENTE', 'CONFIRMADA', 'REJEITADA', 'EXPIRADA', 'FALHOU'))
);

CREATE INDEX IF NOT EXISTS idx_assistente_acao_pendente_conversa
    ON assistente_acao_pendente (conversa_id, data_criacao);
