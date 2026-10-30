-- 1. REESTRUTURAÇÃO DA TABELA DE LOGS (Auditoria Global)
DROP TABLE IF EXISTS logs_sistema CASCADE;
CREATE TABLE logs_sistema (
    id UUID PRIMARY KEY,
    versao BIGINT DEFAULT 0,
    tabela VARCHAR(100) NOT NULL,
    registro_id UUID NOT NULL,
    operacao VARCHAR(20) NOT NULL,
    dados_anteriores JSONB,
    dados_novos JSONB,
    usuario_criacao VARCHAR(100),
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    usuario_ultima_alteracao VARCHAR(100),
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    ativo BOOLEAN DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_logs_tabela_registro ON logs_sistema(tabela, registro_id);

-- 2. ADIÇÃO DE COLUNAS DE BASEENTITY EM TODAS AS TABELAS DE NEGÓCIO
DO $$ 
DECLARE 
    t_name RECORD;
BEGIN
    FOR t_name IN (
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name NOT IN ('logs_sistema', 'event_publication', 'flyway_schema_history')
    ) LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS versao BIGINT DEFAULT 0', t_name.table_name);

        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS data_ultima_alteracao TIMESTAMP WITH TIME ZONE', t_name.table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS usuario_criacao VARCHAR(100)', t_name.table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS usuario_ultima_alteracao VARCHAR(100)', t_name.table_name);
    END LOOP;
END $$;

-- 3. EXPANSÃO DE CAMPOS PARA DADOS CRIPTOGRAFADOS

-- IAM
ALTER TABLE usuarios ALTER COLUMN senha_hash TYPE VARCHAR(500);

-- RH
ALTER TABLE colaboradores ALTER COLUMN cpf TYPE VARCHAR(255);

-- Execução de Campo
ALTER TABLE execucoes_comercial ALTER COLUMN referencia_nota TYPE VARCHAR(255);
ALTER TABLE execucoes_comercial ALTER COLUMN num_poste TYPE VARCHAR(255);
ALTER TABLE execucoes_comercial ALTER COLUMN num_posto TYPE VARCHAR(255);
ALTER TABLE execucoes_comercial ALTER COLUMN num_serie_medidor TYPE VARCHAR(255);

-- Logística
ALTER TABLE solicitacoes_material ALTER COLUMN assinatura_almox TYPE TEXT;
ALTER TABLE solicitacoes_material ALTER COLUMN assinatura_equipe TYPE TEXT;
ALTER TABLE itens_solicitacao ALTER COLUMN num_serie TYPE VARCHAR(255);

-- 4. COMENTÁRIOS DE SEGURANÇA (REMOVER EM PROD)
COMMENT ON TABLE logs_sistema IS 'Trilha de auditoria imutável com snapshots JSONB';
COMMENT ON COLUMN colaboradores.cpf IS '🔒 CRIPTOGRAFADO AES-256';
