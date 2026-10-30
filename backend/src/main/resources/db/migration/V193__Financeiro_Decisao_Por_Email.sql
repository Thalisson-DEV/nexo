-- V193: decisão de compras e pagamentos direto pelo e-mail, sem entrar no Nexo.
--
-- Cada aprovador recebe, no aviso de "aguardando aprovação", um link só dele. O link
-- leva a uma página mínima de confirmação, sem login, e vale para UMA decisão, até
-- 7 dias depois do envio. Quem decide é o dono do link: a decisão é gravada no nome
-- dele, com a mesma alçada que ele teria logado.
--
-- Guarda-se o hash SHA-256 do token, nunca o token: quem lê o banco (ou um backup)
-- não consegue montar um link válido.

CREATE TABLE financeiro_decisao_email (
    id UUID PRIMARY KEY,
    hash_token VARCHAR(64) NOT NULL,
    id_solicitacao UUID NOT NULL,
    tipo_solicitacao VARCHAR(20) NOT NULL,
    id_usuario UUID NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    usado_em TIMESTAMP WITH TIME ZONE,
    acao VARCHAR(20),
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_fin_decisao_email_hash UNIQUE (hash_token),
    CONSTRAINT ck_fin_decisao_email_tipo CHECK (tipo_solicitacao IN ('COMPRA', 'PAGAMENTO')),
    CONSTRAINT ck_fin_decisao_email_acao CHECK (acao IS NULL OR acao IN ('APROVAR', 'REJEITAR')),
    CONSTRAINT ck_fin_decisao_email_uso CHECK ((usado_em IS NULL) = (acao IS NULL))
);

CREATE INDEX idx_fin_decisao_email_solicitacao ON financeiro_decisao_email (id_solicitacao);
