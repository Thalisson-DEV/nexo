-- Meta de faturamento mensal da equipe (opcional). Quando cadastrada, o aplicativo
-- de campo mostra ao encarregado quanto já foi executado no mês e quanto falta.
ALTER TABLE equipes
    ADD COLUMN meta_faturamento_mensal NUMERIC(12, 2);

ALTER TABLE equipes
    ADD CONSTRAINT ck_equipes_meta_faturamento_positiva
    CHECK (meta_faturamento_mensal IS NULL OR meta_faturamento_mensal > 0);
