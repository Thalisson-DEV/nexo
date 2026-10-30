-- Base operacional de saque do estoque: base (Módulo RH) onde o estoque recebe seus materiais.
-- Coluna nullable para não quebrar estoques já cadastrados (o cadastro/edição passa a exigir,
-- via validação na request; registros antigos ficam sem base até serem editados).
ALTER TABLE estoques
    ADD COLUMN id_base_operacional UUID REFERENCES bases_operacionais(id);

CREATE INDEX idx_estoques_base_operacional ON estoques(id_base_operacional);
