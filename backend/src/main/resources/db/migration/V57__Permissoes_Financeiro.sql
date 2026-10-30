-- V57: Permissões do módulo Financeiro
--
-- As permissões `:aprovar` liberam o endpoint de decisão; as `:aprovar:nN` são as
-- alçadas por faixa de valor, cobradas em tempo de execução conforme o valor da
-- solicitação. Um aprovador precisa das duas: a genérica para acessar a operação
-- e a da faixa correspondente para concluí-la.

INSERT INTO permissoes (id, versao, data_criacao, ativo, nome)
SELECT gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, nome
FROM (VALUES
    -- Cadastros de apoio
    ('financeiro:fornecedor:read'),
    ('financeiro:fornecedor:write'),
    ('financeiro:fornecedor:homologar'),
    ('financeiro:alcada:read'),
    ('financeiro:alcada:write'),
    ('financeiro:formulario:read'),
    ('financeiro:formulario:write'),

    -- Solicitações de compra
    ('financeiro:compra:read'),
    ('financeiro:compra:write'),
    ('financeiro:compra:aprovar'),
    -- Atuar sobre solicitações de terceiros (correção pelo financeiro)
    ('financeiro:compra:administrar'),
    ('financeiro:compra:aprovar:n1'),
    ('financeiro:compra:aprovar:n2'),
    ('financeiro:compra:aprovar:n3'),

    -- Solicitações de pagamento
    ('financeiro:pagamento:read'),
    ('financeiro:pagamento:write'),
    ('financeiro:pagamento:aprovar'),
    ('financeiro:pagamento:administrar'),
    ('financeiro:pagamento:baixar'),
    ('financeiro:pagamento:aprovar:n1'),
    ('financeiro:pagamento:aprovar:n2'),
    ('financeiro:pagamento:aprovar:n3')
) AS novas(nome)
WHERE NOT EXISTS (SELECT 1 FROM permissoes p WHERE p.nome = novas.nome);

-- O perfil ADMINISTRADOR recebe tudo; os demais perfis são montados na aplicação.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT pa.id, p.id
FROM perfis_acesso pa
CROSS JOIN permissoes p
WHERE pa.nome = 'ADMINISTRADOR'
  AND p.nome LIKE 'financeiro:%'
  AND NOT EXISTS (
      SELECT 1 FROM perfil_permissoes pp
      WHERE pp.id_perfil = pa.id AND pp.id_permissao = p.id
  );
