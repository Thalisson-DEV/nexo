-- V49: Seed Inicial de Tipos de Equipamento e Motivos de Desconto

-- Inserindo Tipos de Equipamento
INSERT INTO tipo_equipamento (id, descricao, versao, data_criacao, ativo) VALUES 
(gen_random_uuid(), 'EPI', 0, CURRENT_TIMESTAMP, true),
(gen_random_uuid(), 'EPC', 0, CURRENT_TIMESTAMP, true),
(gen_random_uuid(), 'Ferramental', 0, CURRENT_TIMESTAMP, true),
(gen_random_uuid(), 'Frota', 0, CURRENT_TIMESTAMP, true);

-- Inserindo Motivos de Desconto (com tetos de parcelas sugeridos)
INSERT INTO motivo_desconto (id, descricao, teto_parcelas, versao, data_criacao, ativo) VALUES 
(gen_random_uuid(), 'Perda', 6, 0, CURRENT_TIMESTAMP, true),
(gen_random_uuid(), 'Mau Uso', 12, 0, CURRENT_TIMESTAMP, true),
(gen_random_uuid(), 'Avaria', 3, 0, CURRENT_TIMESTAMP, true),
(gen_random_uuid(), 'Outros', 12, 0, CURRENT_TIMESTAMP, true);
