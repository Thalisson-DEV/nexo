-- V64: central de chamados com autosserviço, triagem, SLA e evidências

CREATE TABLE topicos_chamado (
    id UUID PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    nome VARCHAR(100) NOT NULL,
    descricao VARCHAR(500) NOT NULL,
    prazo_atendimento_horas INTEGER NOT NULL CHECK (prazo_atendimento_horas > 0),
    ordem_exibicao INTEGER NOT NULL DEFAULT 0,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE chamados (
    id UUID PRIMARY KEY,
    protocolo VARCHAR(32) NOT NULL UNIQUE,
    id_topico UUID NOT NULL REFERENCES topicos_chamado(id),
    assunto VARCHAR(150) NOT NULL,
    descricao TEXT NOT NULL,
    prioridade VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    usuario_solicitante_id UUID NOT NULL,
    usuario_responsavel_id UUID,
    solucao TEXT,
    data_abertura TIMESTAMP WITH TIME ZONE NOT NULL,
    data_limite_atendimento TIMESTAMP WITH TIME ZONE NOT NULL,
    data_inicio_atendimento TIMESTAMP WITH TIME ZONE,
    data_resolucao TIMESTAMP WITH TIME ZONE,
    data_fechamento TIMESTAMP WITH TIME ZONE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_chamados_prioridade CHECK (prioridade IN ('BAIXA', 'NORMAL', 'ALTA', 'CRITICA')),
    CONSTRAINT ck_chamados_status CHECK (status IN (
        'ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_SOLICITANTE', 'RESOLVIDO', 'FECHADO', 'CANCELADO'))
);

CREATE TABLE anexos_chamado (
    id UUID PRIMARY KEY,
    id_chamado UUID NOT NULL REFERENCES chamados(id),
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL CHECK (tamanho_bytes > 0),
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_chamados_solicitante_data
    ON chamados (usuario_solicitante_id, data_abertura DESC);
CREATE INDEX idx_chamados_fila
    ON chamados (status, prioridade, data_limite_atendimento)
    WHERE ativo = TRUE AND status IN ('ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_SOLICITANTE');
CREATE INDEX idx_chamados_responsavel
    ON chamados (usuario_responsavel_id, status)
    WHERE usuario_responsavel_id IS NOT NULL AND ativo = TRUE;
CREATE INDEX idx_anexos_chamado ON anexos_chamado (id_chamado);

-- Catálogo inicial amplo o suficiente para uma central corporativa, sem amarrar o domínio a uma área.
INSERT INTO topicos_chamado (
    id, codigo, nome, descricao, prazo_atendimento_horas, ordem_exibicao,
    versao, data_criacao, usuario_criacao, ativo)
VALUES
    (gen_random_uuid(), 'ACESSO_SENHA', 'Acesso e senha',
     'Login, senha, bloqueio de conta ou autenticação.', 8, 10, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'SISTEMA_ERRO', 'Erro em sistema',
     'Falha, indisponibilidade ou comportamento inesperado em um sistema.', 8, 20, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'ACESSO_SOLICITACAO', 'Solicitação de acesso',
     'Novo acesso, alteração de perfil ou permissão.', 24, 30, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'EQUIPAMENTO', 'Equipamento',
     'Computador, celular, impressora, periférico ou outro equipamento.', 24, 40, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'REDE_CONECTIVIDADE', 'Rede e conectividade',
     'Internet, Wi-Fi, VPN ou comunicação de rede.', 8, 50, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'DADOS_RELATORIOS', 'Dados e relatórios',
     'Divergência de dados, extração ou relatório.', 24, 60, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'DUVIDA_ORIENTACAO', 'Dúvida ou orientação',
     'Ajuda para executar um processo ou utilizar uma funcionalidade.', 24, 70, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'MELHORIA', 'Sugestão de melhoria',
     'Ideia de evolução para sistema, serviço ou processo.', 72, 80, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'OUTROS', 'Outros',
     'Assunto que não se enquadra nos demais tópicos.', 48, 90, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
SELECT gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, nome
FROM (VALUES
    ('chamado:read'),
    ('chamado:write'),
    ('chamado:manage')
) AS novas(nome)
WHERE NOT EXISTS (SELECT 1 FROM permissoes p WHERE p.nome = novas.nome);

-- Todo perfil existente recebe o autosserviço. A visibilidade continua restrita ao dono no backend.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.ativo = TRUE
  AND permissao.nome IN ('chamado:read', 'chamado:write')
  AND NOT EXISTS (
      SELECT 1 FROM perfil_permissoes existente
      WHERE existente.id_perfil = perfil.id AND existente.id_permissao = permissao.id
  );

-- A visão da fila completa e as mudanças de atendimento nascem apenas no perfil administrador.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
JOIN permissoes permissao ON permissao.nome = 'chamado:manage'
WHERE perfil.nome = 'ADMINISTRADOR'
  AND NOT EXISTS (
      SELECT 1 FROM perfil_permissoes existente
      WHERE existente.id_perfil = perfil.id AND existente.id_permissao = permissao.id
  );
