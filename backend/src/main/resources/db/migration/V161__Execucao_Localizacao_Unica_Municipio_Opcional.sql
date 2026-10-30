-- Lançamento de campo em etapa única: o encarregado informa apenas a localização
-- (GPS coletado automaticamente no dispositivo). O município deixa de ser obrigatório.
-- Execuções antigas mantêm o município registrado; nada é apagado.
ALTER TABLE execucoes_comercial
    ALTER COLUMN id_municipio DROP NOT NULL;
