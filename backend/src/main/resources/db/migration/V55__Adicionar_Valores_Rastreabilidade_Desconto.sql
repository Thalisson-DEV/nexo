-- V55: Rastreabilidade de valores do desconto por etapa
-- valor_sugerido : somatório dos itens lançados na solicitação (substitui o antigo valorTotal calculado)
-- valor_validado : valor confirmado pelo validador (usuario_validador_id / data_validacao)
-- valor_baixado  : valor efetivamente baixado (usuario_baixa_id / data_baixa) -- é o valor que conta no fim

ALTER TABLE desconto
ADD COLUMN valor_sugerido NUMERIC(12, 2),
ADD COLUMN valor_validado NUMERIC(12, 2),
ADD COLUMN valor_baixado NUMERIC(12, 2);

-- Backfill do sugerido com o somatório dos itens ativos (mesma regra do antigo cálculo em memória)
UPDATE desconto d
SET valor_sugerido = COALESCE((
        SELECT SUM(i.valor)
        FROM desconto_item i
        WHERE i.desconto_id = d.id
          AND i.ativo = TRUE
    ), 0);

ALTER TABLE desconto ALTER COLUMN valor_sugerido SET DEFAULT 0;
ALTER TABLE desconto ALTER COLUMN valor_sugerido SET NOT NULL;

-- Descontos que já passaram pelas etapas herdam o valor vigente à época,
-- pois antes desta versão não havia valor próprio por etapa.
UPDATE desconto SET valor_validado = valor_sugerido WHERE status IN ('VALIDADO', 'BAIXADO');
UPDATE desconto SET valor_baixado = valor_validado WHERE status = 'BAIXADO';
