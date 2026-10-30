-- V139: regra de alerta para incidentes do plano de observabilidade.
--
-- O gatilho é fornecido pelo catálogo Java e esta migration apenas registra a
-- configuração inicial no motor existente. Não há tabela, coluna ou outro schema
-- de negócio novo.

DO $$
DECLARE
    v_regra UUID;
BEGIN
    SELECT id
      INTO v_regra
      FROM regras_notificacao
     WHERE chave_gatilho = 'observability.incidente_aberto'
       AND nome = 'Incidente de observabilidade aberto'
     LIMIT 1;

    IF v_regra IS NULL THEN
        v_regra := gen_random_uuid();
        INSERT INTO regras_notificacao (
            id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
            titulo_template, corpo_template, deeplink_template, limiar,
            cooldown_minutos, enviar_email, versao, data_criacao, usuario_criacao,
            ativo)
        VALUES (
            v_regra,
            'observability.incidente_aberto',
            'Incidente de observabilidade aberto',
            'Avisa a equipe técnica quando um componente monitorado entra em falha ou degradação sustentada.',
            TRUE,
            'SISTEMA',
            'ALTA',
            'Incidente em {{componente}}',
            '[{{ambiente}}] {{componente}} está {{estadoInicial}}. {{resumo}}',
            '{{linkStatus}}',
            NULL,
            0,
            TRUE,
            0,
            CURRENT_TIMESTAMP,
            'SISTEMA',
            TRUE);
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM destinatarios_regra_notificacao
         WHERE id_regra = v_regra
           AND tipo = 'PERMISSAO'
           AND valor_alvo = 'chamado:manage'
           AND ativo = TRUE) THEN
        INSERT INTO destinatarios_regra_notificacao (
            id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao,
            usuario_criacao, ativo)
        VALUES (
            gen_random_uuid(), v_regra, 'PERMISSAO', NULL, 'chamado:manage', 0,
            CURRENT_TIMESTAMP, 'SISTEMA', TRUE);
    END IF;
END $$;
