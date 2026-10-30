ALTER TABLE desconto
ADD COLUMN motivo_rejeicao TEXT,
ADD COLUMN usuario_rejeicao_id UUID,
ADD COLUMN data_rejeicao TIMESTAMP WITH TIME ZONE;
