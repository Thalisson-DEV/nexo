INSERT INTO motivos_pendencia (
    id, versao, data_criacao, usuario_criacao, data_ultima_alteracao, usuario_ultima_alteracao, ativo, nome, descricao
) VALUES (
    gen_random_uuid(),
    0,
    CURRENT_TIMESTAMP,
    'SISTEMA',
    CURRENT_TIMESTAMP,
    'SISTEMA',
    TRUE,
    'Pendente Reserva',
    'Pendente Reserva'
) ON CONFLICT (nome) DO NOTHING;
