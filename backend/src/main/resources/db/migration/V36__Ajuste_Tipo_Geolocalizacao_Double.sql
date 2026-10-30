-- Alinha o tipo das colunas de geolocalizacao com as entidades JPA.
-- As entidades BaseOperacional e TelemetriaEvento mapeiam latitude/longitude
-- como Double (double precision / float8), mas as migrations originais
-- (V29, V30) criaram as colunas como NUMERIC, causando falha na validacao
-- do Hibernate (ddl-auto: validate):
--   wrong column type ... found [numeric], but expecting [float(53)]

ALTER TABLE bases_operacionais
    ALTER COLUMN latitude  TYPE double precision USING latitude::double precision,
    ALTER COLUMN longitude TYPE double precision USING longitude::double precision;

ALTER TABLE telemetria_eventos
    ALTER COLUMN latitude  TYPE double precision USING latitude::double precision,
    ALTER COLUMN longitude TYPE double precision USING longitude::double precision;
