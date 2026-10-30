-- Telemetria técnica do app de campo (mobile): erros, falhas de envio, retomadas,
-- tempo de abertura. Aditiva: nenhuma tabela existente é alterada.
-- LGPD: sem identificação de pessoa. `id_aparelho` é um identificador aleatório
-- gerado no aparelho, sem vínculo com usuário, login ou equipe.
-- Retenção: 90 dias (expurgo diário em TelemetriaMobileUseCases).
-- Rollback: DROP TABLE telemetria_mobile;  (nada depende desta tabela)

CREATE TABLE telemetria_mobile (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recebido_em  TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    ocorrido_em  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    tipo         VARCHAR(40) NOT NULL,
    id_aparelho  VARCHAR(64),
    versao_app   VARCHAR(40),
    dados        JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_telemetria_mobile_tipo_ocorrido ON telemetria_mobile (tipo, ocorrido_em DESC);
CREATE INDEX idx_telemetria_mobile_recebido ON telemetria_mobile (recebido_em);

COMMENT ON TABLE telemetria_mobile IS 'Eventos técnicos do app de campo, sem dados pessoais. Retenção de 90 dias.';
