-- V13: Ajuste Geral de BaseEntity para todas as tabelas de negócio
-- Garante que todas as tabelas tenham as colunas necessárias para Auditoria e Soft Delete

DO $$ 
DECLARE 
    t_name RECORD;
BEGIN
    FOR t_name IN (
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name NOT IN ('logs_sistema', 'event_publication', 'flyway_schema_history')
        AND table_type = 'BASE TABLE'
    ) LOOP
        -- 1. Controle de Versão (Optimistic Locking)
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS versao BIGINT DEFAULT 0', t_name.table_name);

        -- 2. Auditoria de Datas
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP', t_name.table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS data_ultima_alteracao TIMESTAMP WITH TIME ZONE', t_name.table_name);

        -- 3. Auditoria de Usuários
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS usuario_criacao VARCHAR(100)', t_name.table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS usuario_ultima_alteracao VARCHAR(100)', t_name.table_name);

        -- 4. Soft Delete
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS ativo BOOLEAN DEFAULT TRUE', t_name.table_name);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS data_desativacao TIMESTAMP WITH TIME ZONE', t_name.table_name);
        
        -- 5. Garantir que data_criacao e ativo não sejam nulos (se já existiam nulos, atualizar primeiro)
        EXECUTE format('UPDATE %I SET data_criacao = CURRENT_TIMESTAMP WHERE data_criacao IS NULL', t_name.table_name);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN data_criacao SET NOT NULL', t_name.table_name);
        
        EXECUTE format('UPDATE %I SET ativo = TRUE WHERE ativo IS NULL', t_name.table_name);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN ativo SET NOT NULL', t_name.table_name);

    END LOOP;
END $$;
