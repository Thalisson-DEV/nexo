-- V15: Seed de dados iniciais compatível com BaseEntity (Auditoria e UUID)
-- Centros de Faturamento, Bases Operacionais, Centros de Investimento, Tipos de Estoque, Funções, Perfis e Processos

-- 1. Centros de Faturamento
INSERT INTO centros_faturamento (id, nome, versao, data_criacao, usuario_criacao, ativo) VALUES 
(uuid_generate_v4(), 'NORTE', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE), 
(uuid_generate_v4(), 'CENTRO NORTE', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

-- 2. Bases Operacionais (Vinculadas aos Centros de Faturamento)
INSERT INTO bases_operacionais (id, nome, id_centro_custo, versao, data_criacao, usuario_criacao, ativo) 
SELECT uuid_generate_v4(), 'JUAZEIRO', id, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE FROM centros_faturamento WHERE nome = 'NORTE';

INSERT INTO bases_operacionais (id, nome, id_centro_custo, versao, data_criacao, usuario_criacao, ativo) 
SELECT uuid_generate_v4(), 'REMANSO', id, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE FROM centros_faturamento WHERE nome = 'NORTE';

INSERT INTO bases_operacionais (id, nome, id_centro_custo, versao, data_criacao, usuario_criacao, ativo) 
SELECT uuid_generate_v4(), 'JACOBINA', id, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE FROM centros_faturamento WHERE nome = 'CENTRO NORTE';

INSERT INTO bases_operacionais (id, nome, id_centro_custo, versao, data_criacao, usuario_criacao, ativo) 
SELECT uuid_generate_v4(), 'BONFIM', id, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE FROM centros_faturamento WHERE nome = 'CENTRO NORTE';

-- 3. Centros de Investimento
INSERT INTO centros_investimento (id, codigo, versao, data_criacao, usuario_criacao, ativo) VALUES 
(uuid_generate_v4(), '670', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE), 
(uuid_generate_v4(), '610', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

-- 4. Tipos de Estoque
INSERT INTO tipos_estoque (id, nome, versao, data_criacao, usuario_criacao, ativo) VALUES 
(uuid_generate_v4(), 'CAMPO', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE), 
(uuid_generate_v4(), 'FISICO', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

-- 5. Funções
INSERT INTO funcoes (id, nome, descricao, versao, data_criacao, usuario_criacao, ativo) VALUES 
(uuid_generate_v4(), 'ELETRICISTA', 'Profissional técnico de campo', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
(uuid_generate_v4(), 'ENCARREGADO', 'Líder de equipe de campo', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
(uuid_generate_v4(), 'COORDENADOR', 'Gestor de operação ou setor', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
(uuid_generate_v4(), 'ANALISTA', 'Profissional de suporte administrativo ou técnico', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
(uuid_generate_v4(), 'AUXILIAR', 'Suporte operacional básico', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

-- 6. Perfis de Acesso
INSERT INTO perfis_acesso (id, nome, versao, data_criacao, usuario_criacao, ativo) VALUES 
(uuid_generate_v4(), 'ADMINISTRADOR', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
(uuid_generate_v4(), 'BACKOFFICE_COMERCIAL', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
(uuid_generate_v4(), 'BACKOFFICE_TECNICO', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
(uuid_generate_v4(), 'COORDENADOR_TECNICO', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
(uuid_generate_v4(), 'COORDENADOR_COMERCIAL', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

-- 7. Processos de Negócio
INSERT INTO processos_negocio (id, nome, versao, data_criacao, usuario_criacao, ativo) VALUES 
(uuid_generate_v4(), 'TI', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
(uuid_generate_v4(), 'COMERCIAL', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
(uuid_generate_v4(), 'OBRAS', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
(uuid_generate_v4(), 'MANUTENCAO', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
(uuid_generate_v4(), 'LINHA VIVA', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);
