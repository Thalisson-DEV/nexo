-- V20: Seed de Usuário Administrador Padrão

-- Garante que o perfil ADMINISTRADOR existe
INSERT INTO perfis_acesso (id, nome, versao, data_criacao, usuario_criacao, ativo)
VALUES (gen_random_uuid(), 'ADMINISTRADOR', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (nome) DO NOTHING;

-- Insere um usuário administrativo padrão
INSERT INTO usuarios (id, login, email, senha_hash, id_perfil, versao, data_criacao, usuario_criacao, ativo)
SELECT 
    gen_random_uuid(), 
    'USRADMIN',
    'admin@exemplo.com.br',
    'wDtVxyv3IE4tJGVBTXL0G/+OD75U2/tTqUHqdHL04DmC9DkFkVNHW5pnLQplOiXYZ9eHIolkYPDReUvzqSRMyTZBUcaTkuBbJ+hqsya81SxPM49bLnXVpw==',
    id, 
    0, 
    CURRENT_TIMESTAMP, 
    'SISTEMA', 
    TRUE
FROM perfis_acesso 
WHERE nome = 'ADMINISTRADOR'
ON CONFLICT (login) DO NOTHING;
