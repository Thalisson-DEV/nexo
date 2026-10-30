-- V74: mural de comunicados — a origem "humana" do sistema de notificações.
--
-- Fase 2. Um comunicado é escrito por um administrador e vale para um público, não
-- para um usuário: por isso existe UMA linha em `comunicados` por mais amplo que seja
-- o alcance, e o estado individual (visto, dispensado, adiado, confirmado) nasce em
-- `interacoes_comunicado` só quando o usuário age. O caminho oposto — uma linha por
-- destinatário — é o de `notificacoes` (V73), e lá ele se justifica porque o conteúdo
-- e o estado são individuais desde o início.

CREATE TABLE comunicados (
    id UUID PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    resumo VARCHAR(300),
    conteudo TEXT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    apresentacao VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    -- Vídeo entra por link externo (YouTube/Vimeo). Upload de vídeo estouraria o limite
    -- de multipart de 15 MB e traria transcodificação junto, sem ganho para o caso de uso.
    video_url VARCHAR(500),
    acao_rotulo VARCHAR(50),
    acao_deeplink VARCHAR(500),
    -- Janela de exibição. `data_inicio` no futuro é o que significa "agendado";
    -- não há status próprio para isso, o que dispensa um job só para virar a chave.
    data_inicio TIMESTAMP WITH TIME ZONE,
    data_fim TIMESTAMP WITH TIME ZONE,
    publicado_em TIMESTAMP WITH TIME ZONE,
    fixado BOOLEAN NOT NULL DEFAULT FALSE,
    -- Compliance: exige clique explícito de "Li e entendi" e registra quem confirmou.
    exige_confirmacao BOOLEAN NOT NULL DEFAULT FALSE,
    -- Opt-in: também cria uma notificação por destinatário na caixa de entrada.
    -- Fica desligado por padrão porque um comunicado para toda a empresa vira uma
    -- linha por usuário em `notificacoes` — custo que só se paga quando o assunto
    -- realmente precisa do sininho.
    notificar_no_sininho BOOLEAN NOT NULL DEFAULT FALSE,
    prioridade_notificacao VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_comunicados_tipo CHECK (tipo IN (
        'DICA', 'CHANGELOG', 'AVISO', 'MANUTENCAO', 'URGENTE')),
    CONSTRAINT ck_comunicados_apresentacao CHECK (apresentacao IN ('MODAL', 'BANNER', 'CARD')),
    CONSTRAINT ck_comunicados_status CHECK (status IN ('RASCUNHO', 'PUBLICADO', 'ARQUIVADO')),
    CONSTRAINT ck_comunicados_prioridade CHECK (prioridade_notificacao IN (
        'BAIXA', 'NORMAL', 'ALTA', 'CRITICA')),
    CONSTRAINT ck_comunicados_janela CHECK (data_fim IS NULL OR data_inicio IS NULL OR data_fim > data_inicio),
    -- Publicado sem janela de início seria um comunicado que nunca aparece.
    CONSTRAINT ck_comunicados_publicado_tem_janela CHECK (
        status <> 'PUBLICADO' OR (data_inicio IS NOT NULL AND publicado_em IS NOT NULL))
);

-- Público-alvo. Vários segmentos no mesmo comunicado se somam (OR): "perfil X mais o
-- usuário Y". TODOS torna os demais irrelevantes e o domínio impede a combinação.
CREATE TABLE segmentos_comunicado (
    id UUID PRIMARY KEY,
    id_comunicado UUID NOT NULL REFERENCES comunicados(id),
    tipo_alvo VARCHAR(20) NOT NULL,
    -- Usado por PERFIL e USUARIO; nulo nos demais.
    id_alvo UUID,
    -- Usado por TIPO_USUARIO (ADMINISTRATIVO/CAMPO); nulo nos demais.
    valor_alvo VARCHAR(50),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_segmentos_comunicado_tipo CHECK (tipo_alvo IN (
        'TODOS', 'PERFIL', 'USUARIO', 'TIPO_USUARIO')),
    CONSTRAINT ck_segmentos_comunicado_alvo CHECK (
        (tipo_alvo = 'TODOS' AND id_alvo IS NULL AND valor_alvo IS NULL)
        OR (tipo_alvo IN ('PERFIL', 'USUARIO') AND id_alvo IS NOT NULL)
        OR (tipo_alvo = 'TIPO_USUARIO' AND valor_alvo IS NOT NULL))
);

CREATE TABLE anexos_comunicado (
    id UUID PRIMARY KEY,
    id_comunicado UUID NOT NULL REFERENCES comunicados(id),
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

-- Estado individual. Sem FK para usuarios, seguindo a convenção de não cruzar módulos
-- no schema.
CREATE TABLE interacoes_comunicado (
    id UUID PRIMARY KEY,
    id_comunicado UUID NOT NULL REFERENCES comunicados(id),
    id_usuario UUID NOT NULL,
    visualizado_em TIMESTAMP WITH TIME ZONE,
    -- "Não exibir novamente": tira do mural para sempre, para este usuário.
    dispensado_em TIMESTAMP WITH TIME ZONE,
    -- "Ver depois": some até a data e volta sozinho.
    adiado_ate TIMESTAMP WITH TIME ZONE,
    confirmado_em TIMESTAMP WITH TIME ZONE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_interacoes_comunicado UNIQUE (id_comunicado, id_usuario)
);

-- Montagem do mural: parte sempre dos publicados vigentes.
CREATE INDEX idx_comunicados_vigentes
    ON comunicados (data_inicio DESC)
    WHERE ativo = TRUE AND status = 'PUBLICADO';

CREATE INDEX idx_segmentos_comunicado_comunicado
    ON segmentos_comunicado (id_comunicado)
    WHERE ativo = TRUE;

CREATE INDEX idx_anexos_comunicado_comunicado
    ON anexos_comunicado (id_comunicado)
    WHERE ativo = TRUE;

-- O mural filtra "o que este usuário já dispensou/adiou" a cada abertura.
CREATE INDEX idx_interacoes_comunicado_usuario
    ON interacoes_comunicado (id_usuario, id_comunicado)
    WHERE ativo = TRUE;

-- Relatório de quem confirmou a leitura.
CREATE INDEX idx_interacoes_comunicado_confirmacao
    ON interacoes_comunicado (id_comunicado)
    WHERE ativo = TRUE AND confirmado_em IS NOT NULL;
