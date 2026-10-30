-- =====================================================================
-- V91: Eventos de SST — a metade reativa da governança de segurança.
--
-- Um agregado só para toda a escala da ocorrência (acidente típico, de
-- trajeto, quase-acidente, incidente sem lesão, dano material e doença
-- ocupacional). Tabelas separadas por tipo quebrariam a pirâmide de Bird:
-- é justamente a base de quase-acidentes contada no MESMO eixo dos
-- acidentes que torna o indicador preditivo.
--
-- A consequência é que colunas de lesão e de CAT são anuláveis e ficam
-- condicionadas ao tipo. Isso é validado no domínio, não por NOT NULL —
-- escolha consciente, registrada no design da mudança.
--
-- Duas regras de modelagem convivem aqui, e são opostas de propósito:
--
--   * PESSOAS E UNIDADES ORGANIZACIONAIS SÃO COPIADAS (id + texto):
--     atividade, gestor, técnicos, envolvidos, base, equipe e veículo.
--     Elas rotacionam, e um registro consultado num processo trabalhista
--     cinco anos depois precisa mostrar quem respondia NAQUELE dia.
--
--   * CATÁLOGOS SÃO REFERENCIADOS por FK (classificação, local e natureza
--     da lesão, agente causador). São vocabulário estável: corrigir um
--     nome ali deve corrigir em todo evento que aponta para ele. O soft
--     delete garante que uma entrada desativada continue legível.
--
-- Todo nome de pessoa copiado vai CIFRADO (AES-256 GCM, via
-- CryptoConverterImpl), como no RH. Copiar o nome é o que faz o registro
-- sobreviver à rotatividade; cifrar a cópia é o que impede o SST de virar
-- um segundo diretório de pessoas em texto claro. Por isso essas colunas
-- são largas: guardam o ciphertext em base64, não o nome.
--
-- Referências a colaborador, base, equipe, veículo e processo ficam SEM
-- FK, preservando o isolamento entre módulos (mesmo padrão da V89).
-- =====================================================================

-- ═══════════════════════════════════════════════════════════════════════
-- Sequência do identificador legível
--
-- O número EVT-AAAA-NNNNNN é citado em CAT, em e-mail e em processo — um
-- UUID não se lê em voz alta. Vem de sequence e não de MAX(numero)+1:
-- transações concorrentes recebem valores distintos sem esperar uma pela
-- outra, e um número consumido por transação desfeita não é reaproveitado.
-- ═══════════════════════════════════════════════════════════════════════

CREATE SEQUENCE IF NOT EXISTS seq_evento_sst START WITH 1 INCREMENT BY 1;

-- ═══════════════════════════════════════════════════════════════════════
-- Evento
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE eventos_sst (
    id UUID PRIMARY KEY,

    -- EVT-AAAA-NNNNNN. Único e nunca reaproveitado.
    numero_registro VARCHAR(20) NOT NULL,

    -- ACIDENTE_TIPICO | ACIDENTE_TRAJETO | QUASE_ACIDENTE |
    -- INCIDENTE_SEM_LESAO | DANO_MATERIAL | DOENCA_OCUPACIONAL
    tipo VARCHAR(30) NOT NULL,
    -- REGISTRADO | EM_TRATATIVA | CONCLUIDO | CANCELADO
    status VARCHAR(20) NOT NULL,

    -- O atraso de comunicação NÃO é gravado: é a subtração destas duas
    -- colunas contra o prazo legal do tipo (24h para acidentes e doença,
    -- 72h para os demais). Sinalizador gravado apodrece quando a regra muda.
    data_ocorrencia TIMESTAMP WITH TIME ZONE NOT NULL,
    data_comunicacao TIMESTAMP WITH TIME ZONE NOT NULL,

    -- ---------- Snapshot: atividade e gestor ----------
    id_atividade UUID NOT NULL,
    atividade_nome VARCHAR(120) NOT NULL,
    id_gestor UUID NOT NULL,
    gestor_nome VARCHAR(512) NOT NULL,          -- 🔒 cifrado
    gestor_funcao VARCHAR(120),
    -- Gestor informado no evento por divergir do cadastrado na atividade
    -- (uma cobertura de férias). O cadastro da atividade não é alterado.
    gestor_substituto BOOLEAN NOT NULL DEFAULT FALSE,

    -- ---------- Snapshot: onde ----------
    id_base_operacional UUID NOT NULL,
    base_nome VARCHAR(120) NOT NULL,
    id_equipe UUID,
    equipe_codigo VARCHAR(60),
    id_veiculo UUID,
    veiculo_placa VARCHAR(20),

    -- ---------- Referências vivas ----------
    id_classificacao UUID NOT NULL,
    id_processo UUID,
    id_local_lesao UUID,
    id_natureza_lesao UUID,
    id_agente_causador UUID,

    -- ---------- Circunstância ----------
    -- SIM | NAO | NAO_APLICAVEL. Três valores porque "não há filmagem" e
    -- "não havia câmera ali" são respostas diferentes: a primeira é perda
    -- de evidência, a segunda é característica do local.
    telemetria VARCHAR(20) NOT NULL,
    filmagem VARCHAR(20) NOT NULL,
    local_ocorrencia VARCHAR(255),
    latitude NUMERIC(10, 8),
    longitude NUMERIC(11, 8),
    descricao VARCHAR(4000) NOT NULL,
    dias_perdidos INTEGER,

    -- ---------- CAT ----------
    possui_cat BOOLEAN NOT NULL DEFAULT FALSE,
    tipo_cat VARCHAR(30),                        -- INICIAL | REABERTURA | COMUNICACAO_OBITO
    numero_cat VARCHAR(40),
    data_emissao_cat DATE,

    -- ---------- Tratativa e desfecho ----------
    tratativa VARCHAR(4000),
    data_conclusao TIMESTAMP WITH TIME ZONE,
    data_cancelamento TIMESTAMP WITH TIME ZONE,
    motivo_cancelamento VARCHAR(500),
    motivo_reabertura VARCHAR(500),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_evento_sst_numero UNIQUE (numero_registro),
    CONSTRAINT ck_evento_sst_dias_perdidos CHECK (dias_perdidos IS NULL OR dias_perdidos >= 0),
    -- A comunicação nunca antecede a ocorrência. Esta é uma das poucas
    -- invariantes que o banco consegue garantir sem conhecer o tipo.
    CONSTRAINT ck_evento_sst_comunicacao CHECK (data_comunicacao >= data_ocorrencia),

    CONSTRAINT fk_evento_sst_atividade FOREIGN KEY (id_atividade) REFERENCES atividades_sst (id),
    CONSTRAINT fk_evento_sst_classificacao FOREIGN KEY (id_classificacao) REFERENCES catalogos_sst (id),
    CONSTRAINT fk_evento_sst_local_lesao FOREIGN KEY (id_local_lesao) REFERENCES catalogos_sst (id),
    CONSTRAINT fk_evento_sst_natureza_lesao FOREIGN KEY (id_natureza_lesao) REFERENCES catalogos_sst (id),
    CONSTRAINT fk_evento_sst_agente FOREIGN KEY (id_agente_causador) REFERENCES catalogos_sst (id)
);

-- Ordem padrão da listagem e recorte de todo indicador: data da ocorrência.
CREATE INDEX ix_evento_sst_ocorrencia ON eventos_sst (data_ocorrencia DESC);
CREATE INDEX ix_evento_sst_comunicacao ON eventos_sst (data_comunicacao);
CREATE INDEX ix_evento_sst_status ON eventos_sst (status);
CREATE INDEX ix_evento_sst_tipo ON eventos_sst (tipo);
CREATE INDEX ix_evento_sst_base ON eventos_sst (id_base_operacional);
CREATE INDEX ix_evento_sst_atividade ON eventos_sst (id_atividade);
CREATE INDEX ix_evento_sst_gestor ON eventos_sst (id_gestor);
CREATE INDEX ix_evento_sst_classificacao ON eventos_sst (id_classificacao);
CREATE INDEX ix_evento_sst_equipe ON eventos_sst (id_equipe);
CREATE INDEX ix_evento_sst_veiculo ON eventos_sst (id_veiculo);
CREATE INDEX ix_evento_sst_processo ON eventos_sst (id_processo);
CREATE INDEX ix_evento_sst_local_lesao ON eventos_sst (id_local_lesao);

-- Busca rápida: a mesma expressão immutable_unaccent(lower(...)) usada na
-- Specification, para que o índice seja de fato aproveitado.
CREATE INDEX ix_evento_sst_busca_numero
    ON eventos_sst USING gin (immutable_unaccent(lower(numero_registro)) gin_trgm_ops);
CREATE INDEX ix_evento_sst_busca_descricao
    ON eventos_sst USING gin (immutable_unaccent(lower(descricao)) gin_trgm_ops);

-- ═══════════════════════════════════════════════════════════════════════
-- Técnicos de segurança responsáveis
--
-- Ao menos um por evento (invariante do domínio). O id sustenta o filtro e
-- o ranking; o nome ao lado é o retrato do dia do registro.
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE evento_sst_tecnicos (
    id UUID PRIMARY KEY,
    id_evento UUID NOT NULL,
    id_colaborador UUID NOT NULL,
    nome VARCHAR(512) NOT NULL,                  -- 🔒 cifrado
    funcao VARCHAR(120),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_evento_tecnico_evento FOREIGN KEY (id_evento)
        REFERENCES eventos_sst (id) ON DELETE CASCADE
);

CREATE INDEX ix_evento_tecnico_evento ON evento_sst_tecnicos (id_evento);
CREATE INDEX ix_evento_tecnico_colaborador ON evento_sst_tecnicos (id_colaborador);

-- ═══════════════════════════════════════════════════════════════════════
-- Envolvidos
--
-- Colaborador e terceiro dividem a tabela porque dividem o papel no evento;
-- o que muda é como cada um se identifica. O colaborador traz o id do RH
-- (é por ele que se agrupa, já que o nome está cifrado). O terceiro não tem
-- cadastro: identifica-se pelo par nome + empresa, e o nome ganha um BLIND
-- INDEX para ser buscável apesar de cifrado — busca exata, por construção.
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE evento_sst_envolvidos (
    id UUID PRIMARY KEY,
    id_evento UUID NOT NULL,
    tipo VARCHAR(20) NOT NULL,                   -- COLABORADOR | TERCEIRO
    papel VARCHAR(20) NOT NULL,                  -- ACIDENTADO | TESTEMUNHA | ENVOLVIDO
    id_colaborador UUID,                         -- só para COLABORADOR
    nome VARCHAR(512) NOT NULL,                  -- 🔒 cifrado
    nome_hash VARCHAR(255),                      -- 🔍 blind index, só para TERCEIRO
    funcao VARCHAR(120),
    base_operacional VARCHAR(120),
    empresa VARCHAR(150),                        -- obrigatória para TERCEIRO

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_evento_envolvido_evento FOREIGN KEY (id_evento)
        REFERENCES eventos_sst (id) ON DELETE CASCADE,
    -- As duas formas de identificação são exclusivas: colaborador tem id,
    -- terceiro tem empresa. Aqui o banco consegue ajudar.
    CONSTRAINT ck_evento_envolvido_identificacao CHECK (
        (tipo = 'COLABORADOR' AND id_colaborador IS NOT NULL AND empresa IS NULL)
        OR (tipo = 'TERCEIRO' AND id_colaborador IS NULL AND empresa IS NOT NULL))
);

CREATE INDEX ix_evento_envolvido_evento ON evento_sst_envolvidos (id_evento);
CREATE INDEX ix_evento_envolvido_colaborador ON evento_sst_envolvidos (id_colaborador);
CREATE INDEX ix_evento_envolvido_papel ON evento_sst_envolvidos (papel);
-- O filtro por nome de terceiro é igualdade sobre o hash — não há LIKE possível.
CREATE INDEX ix_evento_envolvido_nome_hash ON evento_sst_envolvidos (nome_hash);

-- ═══════════════════════════════════════════════════════════════════════
-- Anexos
--
-- Só metadados: o binário mora no armazenamento de objetos (R2). Diferente
-- da evidência de inspeção, o upload acontece DEPOIS do registro — um
-- acidente é registrado da base, no desktop, depois do fato, e não existe
-- aqui o problema de conexão de campo que justifica o pré-upload.
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE evento_sst_anexos (
    id UUID PRIMARY KEY,
    id_evento UUID NOT NULL,
    contexto VARCHAR(20) NOT NULL,               -- EVENTO | CAT | TRATATIVA
    tipo VARCHAR(20) NOT NULL,                   -- FOTO | DOCUMENTO
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    object_key VARCHAR(1024) NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_evento_anexo_evento FOREIGN KEY (id_evento)
        REFERENCES eventos_sst (id) ON DELETE CASCADE,
    CONSTRAINT ck_evento_anexo_tamanho CHECK (tamanho_bytes > 0)
);

CREATE INDEX ix_evento_anexo_evento ON evento_sst_anexos (id_evento);
