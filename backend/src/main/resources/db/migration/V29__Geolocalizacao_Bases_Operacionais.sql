-- Adicionar campos de geolocalização e raio na tabela de bases operacionais
ALTER TABLE bases_operacionais 
ADD COLUMN latitude NUMERIC(10, 8),
ADD COLUMN longitude NUMERIC(11, 8),
ADD COLUMN raio INTEGER DEFAULT 0;

COMMENT ON COLUMN bases_operacionais.latitude IS 'Latitude georreferenciada da base';
COMMENT ON COLUMN bases_operacionais.longitude IS 'Longitude georreferenciada da base';
COMMENT ON COLUMN bases_operacionais.raio IS 'Raio de atuação em metros onde a localização é considerada válida';
