-- V169: módulo de gestão de frota — Fase 0 (ADR docs/adr/0001-modulo-gestao-frota.md).
--
-- Aditiva. Placa, modelo, fabricante e base continuam em `veiculos` (RH); a frota guarda
-- a ficha 1:1 pelo mesmo id. Nenhuma tabela existente é alterada.
-- Rollback: DROP das tabelas frota_* (ordem inversa), DELETE das permissões 'frota:%',
-- dos perfis GESTOR_FROTA/OPERACAO_FROTA e da regra 'frota.vencimento_proximo'.

-- ── Parâmetros editáveis sem deploy (linha única) ─────────────────────────────────
CREATE TABLE frota_parametros (
    id UUID PRIMARY KEY,
    -- Dias antes do vencimento em que um documento/CNH gera aviso, ex.: "30,15,7,0".
    faixas_alerta_dias VARCHAR(100) NOT NULL DEFAULT '30,15,7,0',
    -- Acima disto, a média de km por dia entre duas leituras marca a leitura como inconsistente.
    limite_km_dia INTEGER NOT NULL DEFAULT 1500 CHECK (limite_km_dia > 0),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

INSERT INTO frota_parametros (id, usuario_criacao)
VALUES ('00000000-0000-0000-0000-00000000f001', 'SISTEMA');

-- ── Catálogos ──────────────────────────────────────────────────────────────────────
CREATE TABLE frota_tipos_veiculo (
    id UUID PRIMARY KEY,
    nome VARCHAR(80) NOT NULL,
    descricao VARCHAR(255),
    -- Categoria mínima de CNH exigida para conduzir (A, B, C, D, E). Opcional.
    categoria_cnh_minima VARCHAR(2),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX uk_frota_tipos_veiculo_nome ON frota_tipos_veiculo (UPPER(nome));

CREATE TABLE frota_tipos_implemento (
    id UUID PRIMARY KEY,
    nome VARCHAR(80) NOT NULL,
    descricao VARCHAR(255),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX uk_frota_tipos_implemento_nome ON frota_tipos_implemento (UPPER(nome));

-- ── Ficha do veículo ───────────────────────────────────────────────────────────────
CREATE TABLE frota_fichas_veiculo (
    id UUID PRIMARY KEY,
    id_veiculo UUID NOT NULL UNIQUE REFERENCES veiculos(id),
    -- Projeção de placa e base do RH, mantida pelos eventos VeiculoCadastrado/VeiculoAlterado,
    -- para que a listagem da frota filtre e ordene sem atravessar o módulo.
    placa VARCHAR(10) NOT NULL,
    id_base_operacional UUID REFERENCES bases_operacionais(id),
    id_tipo_veiculo UUID REFERENCES frota_tipos_veiculo(id),
    ano_fabricacao INTEGER,
    ano_modelo INTEGER,
    chassi VARCHAR(30),
    renavam VARCHAR(20),
    cor VARCHAR(40),
    combustivel VARCHAR(20),
    capacidade_tanque_litros NUMERIC(8, 2),
    consumo_referencia_km_l NUMERIC(6, 2),
    situacao VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    regime VARCHAR(20) NOT NULL DEFAULT 'PROPRIO',
    locadora VARCHAR(150),
    custo_mensal_locacao NUMERIC(12, 2),
    inicio_locacao DATE,
    fim_locacao DATE,
    provedor_telemetria VARCHAR(20),
    id_rastreador VARCHAR(80),
    observacao VARCHAR(1000),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_frota_ficha_situacao CHECK (situacao IN (
        'ATIVO', 'EM_MANUTENCAO', 'PARADO', 'RESERVA', 'BAIXADO')),
    CONSTRAINT ck_frota_ficha_regime CHECK (regime IN ('PROPRIO', 'LOCADO')),
    CONSTRAINT ck_frota_ficha_combustivel CHECK (combustivel IS NULL OR combustivel IN (
        'DIESEL_S10', 'DIESEL_S500', 'GASOLINA', 'ETANOL', 'FLEX', 'GNV', 'ELETRICO')),
    CONSTRAINT ck_frota_ficha_telemetria CHECK (provedor_telemetria IS NULL OR provedor_telemetria IN (
        'UBICALY', 'ZUQ')),
    CONSTRAINT ck_frota_ficha_custo CHECK (custo_mensal_locacao IS NULL OR custo_mensal_locacao >= 0),
    CONSTRAINT ck_frota_ficha_locacao_periodo CHECK (
        fim_locacao IS NULL OR inicio_locacao IS NULL OR fim_locacao >= inicio_locacao)
);
CREATE INDEX idx_frota_fichas_placa ON frota_fichas_veiculo (placa);
CREATE INDEX idx_frota_fichas_base ON frota_fichas_veiculo (id_base_operacional);
CREATE INDEX idx_frota_fichas_tipo ON frota_fichas_veiculo (id_tipo_veiculo);
CREATE INDEX idx_frota_fichas_situacao ON frota_fichas_veiculo (situacao);
CREATE UNIQUE INDEX uk_frota_fichas_rastreador
    ON frota_fichas_veiculo (provedor_telemetria, id_rastreador)
    WHERE id_rastreador IS NOT NULL;

-- Todo veículo já cadastrado ganha a ficha vazia; os novos ganham pelo evento do RH.
INSERT INTO frota_fichas_veiculo (id, id_veiculo, placa, id_base_operacional, usuario_criacao, ativo)
SELECT gen_random_uuid(), v.id, v.placa, v.id_base_operacional, 'SISTEMA', COALESCE(v.ativo, TRUE)
FROM veiculos v
WHERE NOT EXISTS (SELECT 1 FROM frota_fichas_veiculo f WHERE f.id_veiculo = v.id);

-- ── Implementos e acoplamentos ─────────────────────────────────────────────────────
CREATE TABLE frota_implementos (
    id UUID PRIMARY KEY,
    identificacao VARCHAR(60) NOT NULL,
    id_tipo_implemento UUID NOT NULL REFERENCES frota_tipos_implemento(id),
    fabricante VARCHAR(80),
    modelo VARCHAR(100),
    numero_serie VARCHAR(80),
    ano_fabricacao INTEGER,
    id_base_operacional UUID NOT NULL REFERENCES bases_operacionais(id),
    situacao VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    observacao VARCHAR(1000),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_frota_implemento_situacao CHECK (situacao IN (
        'ATIVO', 'EM_MANUTENCAO', 'PARADO', 'RESERVA', 'BAIXADO'))
);
CREATE UNIQUE INDEX uk_frota_implementos_identificacao ON frota_implementos (UPPER(identificacao));
CREATE INDEX idx_frota_implementos_base ON frota_implementos (id_base_operacional);

CREATE TABLE frota_acoplamentos_implemento (
    id UUID PRIMARY KEY,
    id_implemento UUID NOT NULL REFERENCES frota_implementos(id),
    id_veiculo UUID NOT NULL REFERENCES veiculos(id),
    inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    fim TIMESTAMP WITH TIME ZONE,
    observacao VARCHAR(500),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_frota_acoplamento_periodo CHECK (fim IS NULL OR fim >= inicio)
);
-- Um implemento só está acoplado a um veículo por vez.
CREATE UNIQUE INDEX uk_frota_acoplamento_aberto
    ON frota_acoplamentos_implemento (id_implemento) WHERE fim IS NULL AND ativo = TRUE;
CREATE INDEX idx_frota_acoplamentos_veiculo ON frota_acoplamentos_implemento (id_veiculo, inicio DESC);

-- ── Condutores e vínculos ──────────────────────────────────────────────────────────
CREATE TABLE frota_condutores (
    id UUID PRIMARY KEY,
    id_colaborador UUID NOT NULL UNIQUE REFERENCES colaboradores(id),
    -- 🔒 Número da CNH cifrado (AES-256 GCM) + blind index para busca exata.
    cnh_numero VARCHAR(255),
    cnh_numero_hash VARCHAR(255),
    cnh_categoria VARCHAR(2),
    cnh_validade DATE,
    cnh_primeira_habilitacao DATE,
    observacao VARCHAR(1000),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_frota_condutor_categoria CHECK (cnh_categoria IS NULL OR cnh_categoria IN (
        'A', 'B', 'C', 'D', 'E', 'AB', 'AC', 'AD', 'AE'))
);
CREATE UNIQUE INDEX uk_frota_condutores_cnh ON frota_condutores (cnh_numero_hash)
    WHERE cnh_numero_hash IS NOT NULL;
CREATE INDEX idx_frota_condutores_validade ON frota_condutores (cnh_validade);

CREATE TABLE frota_vinculos_condutor (
    id UUID PRIMARY KEY,
    id_veiculo UUID NOT NULL REFERENCES veiculos(id),
    id_colaborador UUID NOT NULL REFERENCES colaboradores(id),
    inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    fim TIMESTAMP WITH TIME ZONE,
    origem VARCHAR(20) NOT NULL,
    id_origem UUID,
    observacao VARCHAR(500),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_frota_vinculo_periodo CHECK (fim IS NULL OR fim >= inicio),
    CONSTRAINT ck_frota_vinculo_origem CHECK (origem IN ('MANUAL', 'CHECKLIST', 'TURNO', 'IMPORTACAO'))
);
-- Um condutor aberto por veículo e um veículo aberto por condutor.
CREATE UNIQUE INDEX uk_frota_vinculo_veiculo_aberto
    ON frota_vinculos_condutor (id_veiculo) WHERE fim IS NULL AND ativo = TRUE;
CREATE UNIQUE INDEX uk_frota_vinculo_condutor_aberto
    ON frota_vinculos_condutor (id_colaborador) WHERE fim IS NULL AND ativo = TRUE;
CREATE INDEX idx_frota_vinculos_veiculo ON frota_vinculos_condutor (id_veiculo, inicio DESC);
CREATE INDEX idx_frota_vinculos_colaborador ON frota_vinculos_condutor (id_colaborador, inicio DESC);

-- ── Documentos ─────────────────────────────────────────────────────────────────────
CREATE TABLE frota_documentos (
    id UUID PRIMARY KEY,
    id_veiculo UUID REFERENCES veiculos(id),
    id_implemento UUID REFERENCES frota_implementos(id),
    tipo VARCHAR(30) NOT NULL,
    descricao VARCHAR(150),
    numero VARCHAR(80),
    data_emissao DATE,
    data_vencimento DATE,
    observacao VARCHAR(1000),
    arquivo_chave VARCHAR(400),
    arquivo_nome VARCHAR(255),
    arquivo_content_type VARCHAR(150),
    arquivo_tamanho_bytes BIGINT,
    arquivo_sha256 VARCHAR(64),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_frota_documento_titular CHECK (
        (id_veiculo IS NOT NULL AND id_implemento IS NULL)
        OR (id_veiculo IS NULL AND id_implemento IS NOT NULL)),
    CONSTRAINT ck_frota_documento_tipo CHECK (tipo IN (
        'CRLV', 'LICENCIAMENTO', 'IPVA', 'SEGURO', 'TACOGRAFO', 'ANTT', 'INSPECAO_VEICULAR',
        'LAUDO_IMPLEMENTO', 'CONTRATO_LOCACAO', 'OUTRO')),
    CONSTRAINT ck_frota_documento_periodo CHECK (
        data_vencimento IS NULL OR data_emissao IS NULL OR data_vencimento >= data_emissao)
);
CREATE INDEX idx_frota_documentos_veiculo ON frota_documentos (id_veiculo) WHERE id_veiculo IS NOT NULL;
CREATE INDEX idx_frota_documentos_implemento ON frota_documentos (id_implemento) WHERE id_implemento IS NOT NULL;
CREATE INDEX idx_frota_documentos_vencimento ON frota_documentos (data_vencimento) WHERE ativo = TRUE;

-- ── Odômetro: livro-razão somente inserção ─────────────────────────────────────────
CREATE TABLE frota_leituras_odometro (
    id UUID PRIMARY KEY,
    id_veiculo UUID NOT NULL REFERENCES veiculos(id),
    km BIGINT NOT NULL CHECK (km >= 0),
    lido_em TIMESTAMP WITH TIME ZONE NOT NULL,
    origem VARCHAR(20) NOT NULL,
    -- Registro de origem (checklist, abastecimento, OS...). Nulo só no lançamento manual.
    id_origem UUID,
    id_usuario_registro UUID,
    situacao VARCHAR(20) NOT NULL,
    motivo_situacao VARCHAR(500),
    km_anterior BIGINT,
    data_anulacao TIMESTAMP WITH TIME ZONE,
    id_usuario_anulacao UUID,
    justificativa_anulacao VARCHAR(1000),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_frota_odometro_origem CHECK (origem IN (
        'MANUAL', 'CHECKLIST', 'ABASTECIMENTO', 'MANUTENCAO', 'TELEMETRIA', 'IMPORTACAO')),
    CONSTRAINT ck_frota_odometro_situacao CHECK (situacao IN ('VALIDA', 'INCONSISTENTE', 'ANULADA'))
);
-- O mesmo registro de origem (ex.: checklist reenviado pelo app) nunca gera duas leituras.
CREATE UNIQUE INDEX uk_frota_odometro_origem
    ON frota_leituras_odometro (origem, id_origem) WHERE id_origem IS NOT NULL;
CREATE INDEX idx_frota_odometro_veiculo ON frota_leituras_odometro (id_veiculo, lido_em DESC);
CREATE INDEX idx_frota_odometro_inconsistentes
    ON frota_leituras_odometro (lido_em DESC) WHERE situacao = 'INCONSISTENTE';

-- ── Memória de avisos de vencimento (um aviso por alvo + faixa + vencimento) ───────
CREATE TABLE frota_alertas_vencimento (
    id UUID PRIMARY KEY,
    tipo_alvo VARCHAR(20) NOT NULL,
    id_alvo UUID NOT NULL,
    dias_antecedencia INTEGER NOT NULL CHECK (dias_antecedencia >= 0),
    data_vencimento DATE NOT NULL,
    data_avaliacao DATE NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_frota_alerta_tipo CHECK (tipo_alvo IN ('DOCUMENTO', 'CNH')),
    CONSTRAINT uk_frota_alerta UNIQUE (tipo_alvo, id_alvo, dias_antecedencia, data_vencimento)
);

-- ── Permissões ─────────────────────────────────────────────────────────────────────
INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
SELECT gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, nome
FROM (VALUES
    ('frota:veiculo:read'),
    ('frota:veiculo:write'),
    ('frota:condutor:read'),
    ('frota:condutor:write'),
    ('frota:documento:read'),
    ('frota:documento:write'),
    ('frota:odometro:write'),
    ('frota:odometro:anular'),
    ('frota:configuracao:write')
) AS p(nome)
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfis_acesso (id, nome, versao, data_criacao, usuario_criacao, ativo)
VALUES
    (gen_random_uuid(), 'GESTOR_FROTA', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'OPERACAO_FROTA', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (nome) DO NOTHING;

-- ADMINISTRADOR e GESTOR_FROTA: tudo da frota. O gestor também lê as referências do RH
-- usadas nos formulários (bases, colaboradores, veículos).
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.nome IN ('ADMINISTRADOR', 'GESTOR_FROTA')
  AND (permissao.nome LIKE 'frota:%'
       OR (perfil.nome = 'GESTOR_FROTA' AND permissao.nome IN (
           'rh:veiculo:read', 'rh:colaborador:read', 'rh:base-operacional:read')))
ON CONFLICT DO NOTHING;

-- OPERACAO_FROTA: acompanha a base, lança KM, troca condutor e mantém documentos.
-- Não anula leitura nem mexe em configuração.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.nome = 'OPERACAO_FROTA'
  AND permissao.nome IN (
      'frota:veiculo:read',
      'frota:condutor:read',
      'frota:condutor:write',
      'frota:documento:read',
      'frota:documento:write',
      'frota:odometro:write',
      'rh:veiculo:read',
      'rh:colaborador:read',
      'rh:base-operacional:read')
ON CONFLICT DO NOTHING;

-- ── Regra de fábrica para o aviso de vencimento ────────────────────────────────────
DO $$
DECLARE
    v_regra UUID;
BEGIN
    SELECT id INTO v_regra
      FROM regras_notificacao
     WHERE chave_gatilho = 'frota.vencimento_proximo' AND sistema = TRUE
     LIMIT 1;

    IF v_regra IS NULL THEN
        v_regra := gen_random_uuid();
        INSERT INTO regras_notificacao (
            id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
            titulo_template, corpo_template, deeplink_template, limiar,
            cooldown_minutos, enviar_email, versao, data_criacao, usuario_criacao, ativo)
        VALUES (
            v_regra,
            'frota.vencimento_proximo',
            'Documento ou CNH perto do vencimento',
            'Avisa quem mantém a documentação da frota quando um documento de veículo, '
                || 'de implemento ou uma CNH entra numa faixa de aviso.',
            TRUE,
            'OPERACIONAL',
            'NORMAL',
            '{{descricao}} de {{referencia}} {{prazo}}',
            '{{descricao}} de {{referencia}} {{prazo}} (vencimento em {{dataVencimento}}).',
            '{{link}}',
            NULL,
            0,
            TRUE,
            0,
            CURRENT_TIMESTAMP,
            'SISTEMA',
            TRUE);

        INSERT INTO destinatarios_regra_notificacao (
            id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
        VALUES (
            gen_random_uuid(), v_regra, 'PERMISSAO', NULL, 'frota:documento:write', 0,
            CURRENT_TIMESTAMP, 'SISTEMA', TRUE);
    END IF;
END $$;
