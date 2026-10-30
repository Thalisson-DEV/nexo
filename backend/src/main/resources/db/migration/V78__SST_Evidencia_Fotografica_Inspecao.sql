-- V78: Evidência fotográfica obrigatória nas inspeções de segurança.
--
-- Toda inspeção lançada a partir de agora carrega ao menos uma fotografia que comprova
-- sua factualidade. O binário fica no Cloudflare R2; aqui guardam-se apenas os metadados
-- e a chave do objeto.
--
-- id_inspecao é NULO de propósito: a foto sobe ANTES do lançamento (cada uma em sua
-- própria requisição, para ser retentável em campo com sinal ruim) e só é vinculada
-- quando a inspeção é registrada. O expurgo recolhe as pendentes que nunca forem usadas.
--
-- As inspeções já existentes ficam sem evidência: a regra vale para os novos lançamentos
-- e não há como reconstruir retroativamente uma foto que nunca foi tirada.
CREATE TABLE evidencias_inspecao (
    id UUID PRIMARY KEY,
    id_inspecao UUID REFERENCES inspecoes_seguranca(id),
    id_usuario_envio UUID NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    object_key VARCHAR(1024) NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_evidencia_inspecao_object_key UNIQUE (object_key)
);

CREATE INDEX idx_evidencias_inspecao ON evidencias_inspecao(id_inspecao);

-- Suporta a varredura do expurgo sem varrer as evidências já vinculadas.
CREATE INDEX idx_evidencias_inspecao_pendentes
    ON evidencias_inspecao(data_criacao)
    WHERE id_inspecao IS NULL;
