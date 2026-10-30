-- V86: Remove as permissões que a simplificação do financeiro deixou sem endpoint
--
-- As alçadas por faixa de valor deixaram de existir: quem pode decidir é questão
-- de permissão (`financeiro:{compra,pagamento}:aprovar`), não do valor da
-- solicitação. Com elas somem as `:aprovar:nN` e o cadastro `financeiro:alcada:*`.
--
-- A baixa de parcelas também saiu: o pagamento não gera mais cronograma, e
-- registrar a liquidação é assunto do fluxo de tesouraria, não deste módulo.
--
-- As permissões genéricas `:aprovar` permanecem — são elas que os endpoints de
-- decisão cobram.

DELETE FROM perfil_permissoes
WHERE id_permissao IN (
    SELECT id FROM permissoes
    WHERE nome IN (
        'financeiro:compra:aprovar:n1',
        'financeiro:compra:aprovar:n2',
        'financeiro:compra:aprovar:n3',
        'financeiro:pagamento:aprovar:n1',
        'financeiro:pagamento:aprovar:n2',
        'financeiro:pagamento:aprovar:n3',
        'financeiro:pagamento:baixar',
        'financeiro:alcada:read',
        'financeiro:alcada:write'
    )
);

DELETE FROM permissoes
WHERE nome IN (
    'financeiro:compra:aprovar:n1',
    'financeiro:compra:aprovar:n2',
    'financeiro:compra:aprovar:n3',
    'financeiro:pagamento:aprovar:n1',
    'financeiro:pagamento:aprovar:n2',
    'financeiro:pagamento:aprovar:n3',
    'financeiro:pagamento:baixar',
    'financeiro:alcada:read',
    'financeiro:alcada:write'
);
