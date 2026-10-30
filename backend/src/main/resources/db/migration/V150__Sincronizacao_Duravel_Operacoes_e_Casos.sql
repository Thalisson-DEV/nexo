CREATE TABLE operacoes_sincronizacao (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), versao BIGINT, data_criacao TIMESTAMPTZ NOT NULL DEFAULT now(), data_ultima_alteracao TIMESTAMPTZ,
 usuario_criacao VARCHAR(255), usuario_ultima_alteracao VARCHAR(255), ativo BOOLEAN NOT NULL DEFAULT TRUE, data_desativacao TIMESTAMPTZ,
 id_operacao_local UUID NOT NULL, id_registro_local UUID, tipo VARCHAR(30) NOT NULL, id_usuario_origem UUID NOT NULL,
 instante_origem TIMESTAMPTZ NOT NULL, payload_original JSONB NOT NULL, hash_original VARCHAR(64) NOT NULL,
 status VARCHAR(35) NOT NULL, tentativas INTEGER NOT NULL DEFAULT 0,
 CONSTRAINT uk_operacao_local_usuario UNIQUE(id_operacao_local,id_usuario_origem),
 CONSTRAINT ck_operacao_tipo CHECK(tipo IN ('TURNO','EXECUCAO','SOLICITACAO','TRANSFERENCIA','INSPECAO','ANEXO')),
 CONSTRAINT ck_operacao_status CHECK(status IN ('SALVO_NO_SERVIDOR','EM_TRATAMENTO','RESOLVIDO','REJEITADO'))
);
CREATE INDEX ix_operacoes_origem_status ON operacoes_sincronizacao(id_usuario_origem,status);
CREATE TABLE casos_sincronizacao (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), versao BIGINT, data_criacao TIMESTAMPTZ NOT NULL DEFAULT now(), data_ultima_alteracao TIMESTAMPTZ,
 usuario_criacao VARCHAR(255), usuario_ultima_alteracao VARCHAR(255), ativo BOOLEAN NOT NULL DEFAULT TRUE, data_desativacao TIMESTAMPTZ,
 id_operacao UUID NOT NULL UNIQUE REFERENCES operacoes_sincronizacao(id), status VARCHAR(40) NOT NULL DEFAULT 'ABERTO', motivo VARCHAR(2000) NOT NULL,
 id_responsavel UUID, justificativa_ultima VARCHAR(4000), data_resolucao TIMESTAMPTZ,
 CONSTRAINT ck_caso_status CHECK(status IN ('ABERTO','EM_ANALISE','AGUARDANDO_REPROCESSAMENTO','RESOLVIDO','REJEITADO'))
);
CREATE INDEX ix_casos_status_data ON casos_sincronizacao(status,data_criacao);
CREATE TABLE versoes_caso_sincronizacao (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), versao BIGINT, data_criacao TIMESTAMPTZ NOT NULL DEFAULT now(), data_ultima_alteracao TIMESTAMPTZ,
 usuario_criacao VARCHAR(255), usuario_ultima_alteracao VARCHAR(255), ativo BOOLEAN NOT NULL DEFAULT TRUE, data_desativacao TIMESTAMPTZ,
 id_caso UUID NOT NULL REFERENCES casos_sincronizacao(id), numero INTEGER NOT NULL, payload JSONB NOT NULL, justificativa VARCHAR(4000) NOT NULL,
 id_usuario UUID NOT NULL, hash VARCHAR(64) NOT NULL, criada_em TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(id_caso,numero)
);
CREATE TABLE anexos_sincronizacao (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), versao BIGINT, data_criacao TIMESTAMPTZ NOT NULL DEFAULT now(), data_ultima_alteracao TIMESTAMPTZ,
 usuario_criacao VARCHAR(255), usuario_ultima_alteracao VARCHAR(255), ativo BOOLEAN NOT NULL DEFAULT TRUE, data_desativacao TIMESTAMPTZ,
 id_operacao UUID NOT NULL REFERENCES operacoes_sincronizacao(id), id_anexo_local UUID NOT NULL, nome VARCHAR(255) NOT NULL,
 content_type VARCHAR(150) NOT NULL, sha256 VARCHAR(64) NOT NULL, conteudo BYTEA NOT NULL, tamanho_bytes BIGINT NOT NULL, UNIQUE(id_operacao,id_anexo_local)
);
INSERT INTO permissoes (id, nome, versao, data_criacao, usuario_criacao, ativo) VALUES (gen_random_uuid(),'sincronizacao:read',0,now(),'SISTEMA',true) ON CONFLICT (nome) DO NOTHING;
INSERT INTO permissoes (id, nome, versao, data_criacao, usuario_criacao, ativo) VALUES (gen_random_uuid(),'sincronizacao:manage',0,now(),'SISTEMA',true) ON CONFLICT (nome) DO NOTHING;
INSERT INTO perfil_permissoes (id_perfil,id_permissao)
SELECT p.id,perm.id FROM perfis_acesso p CROSS JOIN permissoes perm
WHERE p.nome='ADMINISTRADOR' AND perm.nome IN ('sincronizacao:read','sincronizacao:manage') ON CONFLICT DO NOTHING;
INSERT INTO perfil_permissoes (id_perfil,id_permissao)
SELECT DISTINCT pp.id_perfil,novo.id FROM perfil_permissoes pp
JOIN permissoes existente ON existente.id=pp.id_permissao CROSS JOIN permissoes novo
WHERE existente.nome='sincronizacao:manage' AND novo.nome='sincronizacao:read' ON CONFLICT DO NOTHING;
