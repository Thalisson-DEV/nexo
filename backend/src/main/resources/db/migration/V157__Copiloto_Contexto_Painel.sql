-- Copiloto contextual nos dashboards: a conversa registra a superficie onde comecou (workspace ou
-- painel) e cada pergunta guarda o snapshot do contexto de tela usado naquele momento.

ALTER TABLE assistente_conversa
    ADD COLUMN IF NOT EXISTS origem VARCHAR(20) NOT NULL DEFAULT 'WORKSPACE',
    ADD COLUMN IF NOT EXISTS origem_id VARCHAR(80);

ALTER TABLE assistente_conversa
    DROP CONSTRAINT IF EXISTS ck_assistente_conversa_origem;
ALTER TABLE assistente_conversa
    ADD CONSTRAINT ck_assistente_conversa_origem CHECK (origem IN ('WORKSPACE', 'DASHBOARD'));

CREATE INDEX IF NOT EXISTS idx_assistente_conversa_usuario_origem
    ON assistente_conversa (usuario_id, origem, origem_id, ultima_atividade_em DESC)
    WHERE ativo = TRUE;

ALTER TABLE assistente_mensagem
    ADD COLUMN IF NOT EXISTS contexto_json TEXT;

COMMENT ON COLUMN assistente_conversa.origem IS 'Superficie de inicio da conversa: WORKSPACE ou DASHBOARD';
COMMENT ON COLUMN assistente_conversa.origem_id IS 'Identificador do painel quando origem = DASHBOARD';
COMMENT ON COLUMN assistente_mensagem.contexto_json IS 'Snapshot do contexto de tela (painel, periodo, filtros) da pergunta';
