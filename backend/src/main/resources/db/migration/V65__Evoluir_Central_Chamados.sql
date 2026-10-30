-- V65: equipes, timeline, catálogo administrável e SLA de primeira resposta/resolução

CREATE TABLE equipes_atendimento_chamado (
    id UUID PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(500),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE equipe_atendimento_chamado_membros (
    id_equipe UUID NOT NULL REFERENCES equipes_atendimento_chamado(id),
    usuario_id UUID NOT NULL,
    PRIMARY KEY (id_equipe, usuario_id)
);

INSERT INTO equipes_atendimento_chamado (
    id, codigo, nome, descricao, versao, data_criacao, usuario_criacao, ativo)
VALUES (
    gen_random_uuid(), 'CENTRAL_ATENDIMENTO', 'Central de Atendimento',
    'Equipe padrão responsável pela triagem inicial dos chamados.',
    0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (codigo) DO NOTHING;

ALTER TABLE topicos_chamado
    ADD COLUMN prioridade_padrao VARCHAR(20),
    ADD COLUMN id_equipe_padrao UUID REFERENCES equipes_atendimento_chamado(id),
    ADD COLUMN sla_primeira_resposta_horas INTEGER,
    ADD COLUMN sla_resolucao_horas INTEGER,
    ADD COLUMN pausar_sla_aguardando_solicitante BOOLEAN,
    ADD COLUMN prazo_reabertura_horas INTEGER;

UPDATE topicos_chamado
SET prioridade_padrao = CASE
        WHEN codigo IN ('ACESSO_SENHA', 'SISTEMA_ERRO', 'REDE_CONECTIVIDADE') THEN 'ALTA'
        WHEN codigo = 'MELHORIA' THEN 'BAIXA'
        ELSE 'NORMAL'
    END,
    id_equipe_padrao = (SELECT id FROM equipes_atendimento_chamado
                        WHERE codigo = 'CENTRAL_ATENDIMENTO'),
    sla_primeira_resposta_horas = CASE
        WHEN codigo IN ('ACESSO_SENHA', 'SISTEMA_ERRO', 'REDE_CONECTIVIDADE') THEN 2
        WHEN codigo = 'MELHORIA' THEN 24
        WHEN codigo = 'OUTROS' THEN 12
        ELSE LEAST(8, prazo_atendimento_horas)
    END,
    sla_resolucao_horas = prazo_atendimento_horas,
    pausar_sla_aguardando_solicitante = TRUE,
    prazo_reabertura_horas = 72;

ALTER TABLE topicos_chamado
    ALTER COLUMN prioridade_padrao SET NOT NULL,
    ALTER COLUMN id_equipe_padrao SET NOT NULL,
    ALTER COLUMN sla_primeira_resposta_horas SET NOT NULL,
    ALTER COLUMN sla_resolucao_horas SET NOT NULL,
    ALTER COLUMN pausar_sla_aguardando_solicitante SET NOT NULL,
    ALTER COLUMN prazo_reabertura_horas SET NOT NULL,
    ADD CONSTRAINT ck_topicos_chamado_prioridade CHECK (
        prioridade_padrao IN ('BAIXA', 'NORMAL', 'ALTA', 'CRITICA')),
    ADD CONSTRAINT ck_topicos_chamado_sla CHECK (
        sla_primeira_resposta_horas > 0
        AND sla_resolucao_horas > 0
        AND sla_primeira_resposta_horas <= sla_resolucao_horas),
    ADD CONSTRAINT ck_topicos_chamado_reabertura CHECK (prazo_reabertura_horas > 0);

ALTER TABLE chamados
    ADD COLUMN id_equipe_atendimento UUID REFERENCES equipes_atendimento_chamado(id),
    ADD COLUMN data_limite_primeira_resposta TIMESTAMP WITH TIME ZONE,
    ADD COLUMN data_limite_resolucao TIMESTAMP WITH TIME ZONE,
    ADD COLUMN pausar_sla_aguardando_solicitante BOOLEAN,
    ADD COLUMN prazo_reabertura_horas INTEGER,
    ADD COLUMN data_primeira_resposta TIMESTAMP WITH TIME ZONE,
    ADD COLUMN data_inicio_pausa_sla TIMESTAMP WITH TIME ZONE,
    ADD COLUMN total_segundos_pausa_sla BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN data_ultima_interacao TIMESTAMP WITH TIME ZONE;

UPDATE chamados chamado
SET id_equipe_atendimento = topico.id_equipe_padrao,
    data_limite_primeira_resposta = chamado.data_abertura
        + topico.sla_primeira_resposta_horas * INTERVAL '1 hour',
    data_limite_resolucao = chamado.data_limite_atendimento,
    pausar_sla_aguardando_solicitante = topico.pausar_sla_aguardando_solicitante,
    prazo_reabertura_horas = topico.prazo_reabertura_horas,
    data_primeira_resposta = CASE
        WHEN chamado.status <> 'ABERTO' THEN chamado.data_inicio_atendimento
        ELSE NULL
    END,
    data_ultima_interacao = COALESCE(chamado.data_ultima_alteracao, chamado.data_abertura)
FROM topicos_chamado topico
WHERE topico.id = chamado.id_topico;

ALTER TABLE chamados
    ALTER COLUMN id_equipe_atendimento SET NOT NULL,
    ALTER COLUMN data_limite_primeira_resposta SET NOT NULL,
    ALTER COLUMN data_limite_resolucao SET NOT NULL,
    ALTER COLUMN pausar_sla_aguardando_solicitante SET NOT NULL,
    ALTER COLUMN prazo_reabertura_horas SET NOT NULL,
    ALTER COLUMN data_ultima_interacao SET NOT NULL,
    ADD CONSTRAINT ck_chamados_prazo_reabertura CHECK (prazo_reabertura_horas > 0);

CREATE TABLE interacoes_chamado (
    id UUID PRIMARY KEY,
    id_chamado UUID NOT NULL REFERENCES chamados(id),
    tipo VARCHAR(30) NOT NULL,
    mensagem TEXT,
    interno BOOLEAN NOT NULL DEFAULT FALSE,
    usuario_autor_id UUID NOT NULL,
    status_anterior VARCHAR(30),
    status_novo VARCHAR(30),
    equipe_anterior_id UUID,
    equipe_nova_id UUID,
    responsavel_anterior_id UUID,
    responsavel_novo_id UUID,
    data_evento TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_interacoes_chamado_tipo CHECK (tipo IN (
        'ABERTURA', 'MENSAGEM_PUBLICA', 'COMENTARIO_INTERNO', 'ALTERACAO_STATUS',
        'ALTERACAO_PRIORIDADE', 'ATRIBUICAO', 'ENCAMINHAMENTO', 'REABERTURA',
        'CANCELAMENTO', 'EVIDENCIA_ADICIONADA'))
);

INSERT INTO interacoes_chamado (
    id, id_chamado, tipo, mensagem, interno, usuario_autor_id,
    status_novo, equipe_nova_id, data_evento,
    versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), chamado.id, 'ABERTURA', 'Chamado importado para a timeline: ' || chamado.assunto,
       FALSE, chamado.usuario_solicitante_id, 'ABERTO', chamado.id_equipe_atendimento,
       chamado.data_abertura, 0, chamado.data_abertura, 'SISTEMA', TRUE
FROM chamados chamado
WHERE NOT EXISTS (
    SELECT 1 FROM interacoes_chamado interacao WHERE interacao.id_chamado = chamado.id
);

ALTER TABLE topicos_chamado DROP COLUMN prazo_atendimento_horas;
ALTER TABLE chamados DROP COLUMN data_limite_atendimento;

DROP INDEX IF EXISTS idx_chamados_fila;
CREATE INDEX idx_chamados_fila
    ON chamados (id_equipe_atendimento, status, prioridade, data_limite_resolucao)
    WHERE ativo = TRUE AND status IN ('ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_SOLICITANTE');

CREATE INDEX idx_interacoes_chamado_timeline
    ON interacoes_chamado (id_chamado, data_evento);
