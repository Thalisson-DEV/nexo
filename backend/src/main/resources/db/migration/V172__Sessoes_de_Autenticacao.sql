-- =====================================================================
-- V172: Sessões de autenticação por aparelho (refresh token rotativo)
--
-- As equipes de campo passam dias sem sinal. Com só um JWT de 24h, o
-- token vencia no bolso do eletricista e o primeiro 401 apagava a sessão
-- local. Cada login passa a abrir uma sessão por aparelho, renovável sem
-- senha enquanto estiver em uso.
--
-- * O segredo do refresh token nunca é gravado: só o SHA-256 dele.
-- * hash_token_anterior guarda o token imediatamente anterior. Ele é
--   aceito como retentativa (a resposta da renovação se perdeu no sinal
--   fraco); um token mais antigo revoga a sessão (sinal de cópia).
-- * expira_em desliza a cada renovação; expira_em_maximo é o teto absoluto.
--
-- Tabela técnica de segurança, não de negócio: não estende BaseEntity
-- (a auditoria JSONB gravaria um log a cada renovação) e sessões
-- encerradas há mais de 30 dias são expurgadas fisicamente.
-- =====================================================================

CREATE TABLE sessoes_autenticacao (
    id                    UUID PRIMARY KEY,
    versao                BIGINT       NOT NULL DEFAULT 0,
    id_usuario            UUID         NOT NULL REFERENCES usuarios (id),
    hash_token_atual      VARCHAR(64)  NOT NULL,
    hash_token_anterior   VARCHAR(64),
    dispositivo           VARCHAR(255),
    criada_em             TIMESTAMPTZ  NOT NULL,
    ultima_renovacao_em   TIMESTAMPTZ  NOT NULL,
    expira_em             TIMESTAMPTZ  NOT NULL,
    expira_em_maximo      TIMESTAMPTZ  NOT NULL,
    revogada_em           TIMESTAMPTZ,
    motivo_revogacao      VARCHAR(40)
);

CREATE INDEX idx_sessoes_autenticacao_usuario_ativas
    ON sessoes_autenticacao (id_usuario)
    WHERE revogada_em IS NULL;

CREATE INDEX idx_sessoes_autenticacao_expira_em
    ON sessoes_autenticacao (expira_em);
