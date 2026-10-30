-- V58: Carga inicial do módulo Financeiro
--
-- Reproduz os quatro formulários do Google Forms como duas famílias — COMPRA (com
-- as variantes PADRAO, FROTA e OBRA_CIVIL) e PAGAMENTO — preservando as
-- especificidades de cada processo.
--
-- Tudo aqui é configuração, não código: a partir do primeiro boot, campos podem
-- ser acrescentados, renomeados, reordenados ou removidos pela API de formulários.
-- As faixas de alçada também são dados; ajuste os limites conforme a política da
-- diretoria.

-- ═══════════════════════════════════════════════════════════════════════════════
-- Funções auxiliares (escopo de sessão; descartadas ao fim da migration)
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE FUNCTION pg_temp.f_form(p_tipo TEXT, p_codigo TEXT, p_titulo TEXT, p_descricao TEXT, p_padrao BOOLEAN)
RETURNS UUID AS $$
DECLARE v_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO financeiro_formulario_solicitacao
        (id, tipo_solicitacao, codigo, titulo, descricao, padrao, versao, data_criacao, ativo, usuario_criacao)
    VALUES (v_id, p_tipo, p_codigo, p_titulo, p_descricao, p_padrao, 0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA');
    RETURN v_id;
END; $$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.f_secao(p_form UUID, p_titulo TEXT, p_descricao TEXT, p_ordem INTEGER)
RETURNS UUID AS $$
DECLARE v_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO financeiro_secao_formulario
        (id, formulario_id, titulo, descricao, ordem, versao, data_criacao, ativo, usuario_criacao)
    VALUES (v_id, p_form, p_titulo, p_descricao, p_ordem, 0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA');
    RETURN v_id;
END; $$ LANGUAGE plpgsql;

-- Campo apoiado numa coluna tipada do núcleo.
CREATE FUNCTION pg_temp.f_can(p_secao UUID, p_canonico TEXT, p_chave TEXT, p_tipo TEXT, p_fonte TEXT,
                              p_rotulo TEXT, p_obrigatorio BOOLEAN, p_ordem INTEGER)
RETURNS UUID AS $$
DECLARE v_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO financeiro_campo_formulario
        (id, secao_id, chave, campo_canonico, tipo_campo, fonte_referencia, rotulo, obrigatorio, ordem,
         versao, data_criacao, ativo, usuario_criacao)
    VALUES (v_id, p_secao, p_chave, p_canonico, p_tipo, p_fonte, p_rotulo, p_obrigatorio, p_ordem,
            0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA');
    RETURN v_id;
END; $$ LANGUAGE plpgsql;

-- Campo livre, gravado como chave/valor.
CREATE FUNCTION pg_temp.f_din(p_secao UUID, p_chave TEXT, p_tipo TEXT, p_rotulo TEXT,
                              p_obrigatorio BOOLEAN, p_ordem INTEGER)
RETURNS UUID AS $$
DECLARE v_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO financeiro_campo_formulario
        (id, secao_id, chave, tipo_campo, rotulo, obrigatorio, ordem,
         versao, data_criacao, ativo, usuario_criacao)
    VALUES (v_id, p_secao, p_chave, p_tipo, p_rotulo, p_obrigatorio, p_ordem,
            0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA');
    RETURN v_id;
END; $$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.f_opcoes(p_campo UUID, p_valores TEXT[], p_rotulos TEXT[])
RETURNS VOID AS $$
BEGIN
    INSERT INTO financeiro_opcao_campo (id, campo_id, valor, rotulo, ordem, versao, data_criacao, ativo, usuario_criacao)
    SELECT gen_random_uuid(), p_campo, p_valores[i], p_rotulos[i], i - 1, 0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA'
    FROM generate_subscripts(p_valores, 1) AS i;
END; $$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.f_cond(p_campo UUID, p_chave_governante TEXT, p_valores TEXT)
RETURNS VOID AS $$
BEGIN
    UPDATE financeiro_campo_formulario
       SET condicao_campo_chave = p_chave_governante, condicao_valores = p_valores
     WHERE id = p_campo;
END; $$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.f_lim(p_campo UUID, p_tamanho INTEGER, p_min NUMERIC, p_max NUMERIC)
RETURNS VOID AS $$
BEGIN
    UPDATE financeiro_campo_formulario
       SET tamanho_maximo = p_tamanho, valor_minimo = p_min, valor_maximo = p_max
     WHERE id = p_campo;
END; $$ LANGUAGE plpgsql;

-- ═══════════════════════════════════════════════════════════════════════════════
-- Formulários
-- ═══════════════════════════════════════════════════════════════════════════════

DO $seed$
DECLARE
    v_form UUID;
    v_secao UUID;
    v_campo UUID;

    -- Naturezas do formulário de frota, na ordem original
    c_nat_valores TEXT[] := ARRAY[
        'COMPRA_E_REFORMA_DE_PNEUS','SUSPENSAO_FREIOS','ARREFECIMENTO','TRANSMISSAO','ELETRICA',
        'CHAPARIA_E_PINTURA','HIDRAULICA','TAPECARIA','LAVAGEM_E_LUBRIFICACAO','ACESSORIOS','BATERIA',
        'BORRACHARIA','MECANICA','PNEUMATICA','POSTO_DE_MOLAS','REFRIGERACAO_E_CLIMATIZACAO',
        'TACOGRAFO','TORNEADORA'];
    c_nat_rotulos TEXT[] := ARRAY[
        'Compra e Reforma de Pneus','Suspensão / Freios','Arrefecimento','Transmissão','Elétrica',
        'Chaparia e Pintura','Hidráulica','Tapeçaria','Lavagem e Lubrificação','Acessórios','Bateria',
        'Borracharia','Mecânica','Pneumática','Posto de Molas','Refrigeração e Climatização',
        'Tacógrafo','Torneadora'];

    c_mot_valores TEXT[] := ARRAY['DESGASTE_NATURAL','ADEQUACAO_DE_MELHORIA','PROBLEMA_MECANICO','MAU_USO_DESCONTO','OUTRA'];
    c_mot_rotulos TEXT[] := ARRAY['Desgaste natural','Adequação de Melhoria','Problema mecânico','Mau Uso / Desconto','Outra'];
BEGIN

-- ───────────────────────────────────────────────────────────────────────────────
-- COMPRA / PADRAO
-- ───────────────────────────────────────────────────────────────────────────────
v_form := pg_temp.f_form('COMPRA', 'PADRAO', 'Solicitação de Compra',
    'Pedido de autorização para aquisição de material ou serviço.', TRUE);

v_secao := pg_temp.f_secao(v_form, 'Identificação', NULL, 0);
PERFORM pg_temp.f_can(v_secao, 'SOLICITANTE_COLABORADOR', 'solicitanteColaboradorId', 'REFERENCIA', 'COLABORADOR', 'Nome do Solicitante', TRUE, 0);
PERFORM pg_temp.f_can(v_secao, 'BASE', 'baseId', 'REFERENCIA', 'BASE_OPERACIONAL', 'BASE', TRUE, 1);
PERFORM pg_temp.f_can(v_secao, 'PROCESSO', 'processoId', 'REFERENCIA', 'PROCESSO', 'Setor / Centro de Custo', TRUE, 2);

v_secao := pg_temp.f_secao(v_form, 'Fornecedor',
    'Escolha um fornecedor cadastrado ou informe o nome, se ainda não existir.', 1);
PERFORM pg_temp.f_can(v_secao, 'FORNECEDOR', 'fornecedorId', 'REFERENCIA', 'FORNECEDOR', 'Fornecedor (cadastrado)', FALSE, 0);
PERFORM pg_temp.f_can(v_secao, 'FORNECEDOR_NOME', 'fornecedorNome', 'TEXTO', NULL, 'Fornecedor (não cadastrado)', FALSE, 1);
PERFORM pg_temp.f_can(v_secao, 'FORNECEDOR_RAZAO_SOCIAL', 'fornecedorRazaoSocial', 'TEXTO', NULL, 'Razão Social', FALSE, 2);

v_secao := pg_temp.f_secao(v_form, 'Compra', NULL, 2);
PERFORM pg_temp.f_can(v_secao, 'QUANTIDADE', 'quantidade', 'DECIMAL', NULL, 'Quantidade', TRUE, 0);
v_campo := pg_temp.f_can(v_secao, 'VALOR', 'valor', 'MOEDA', NULL, 'Valor', TRUE, 1);
PERFORM pg_temp.f_lim(v_campo, NULL, 0.01, NULL);
PERFORM pg_temp.f_can(v_secao, 'PRIORIDADE', 'prioridade', 'SELECAO_UNICA', NULL, 'Grau de Prioridade', TRUE, 2);
PERFORM pg_temp.f_can(v_secao, 'LOCAL_AQUISICAO', 'localAquisicao', 'SELECAO_UNICA', NULL, 'Local de Aquisição', TRUE, 3);
PERFORM pg_temp.f_can(v_secao, 'FORMA_PAGAMENTO', 'formaPagamento', 'SELECAO_UNICA', NULL, 'Forma de Pagamento', TRUE, 4);
v_campo := pg_temp.f_can(v_secao, 'QUANTIDADE_PARCELAS', 'quantidadeParcelas', 'INTEIRO', NULL, 'Parcelamento', TRUE, 5);
PERFORM pg_temp.f_lim(v_campo, NULL, 1, 48);
v_campo := pg_temp.f_can(v_secao, 'DESCRICAO', 'descricao', 'TEXTO_LONGO', NULL, 'Descrição da Compra / Serviço', TRUE, 6);
PERFORM pg_temp.f_lim(v_campo, 2000, NULL, NULL);

v_secao := pg_temp.f_secao(v_form, 'Nota Fiscal', NULL, 3);
PERFORM pg_temp.f_can(v_secao, 'TEM_NF', 'temNf', 'BOOLEANO', NULL, 'Tem NF', TRUE, 0);
v_campo := pg_temp.f_din(v_secao, 'justificativaSemNf', 'TEXTO_LONGO', 'Justificativa por não haver NF', TRUE, 1);
PERFORM pg_temp.f_cond(v_campo, 'temNf', 'false');

v_secao := pg_temp.f_secao(v_form, 'Complemento', NULL, 4);
v_campo := pg_temp.f_can(v_secao, 'OBSERVACOES', 'observacoes', 'TEXTO_LONGO', NULL, 'Observações, OS ou Rateio', FALSE, 0);
PERFORM pg_temp.f_lim(v_campo, 2000, NULL, NULL);

-- ───────────────────────────────────────────────────────────────────────────────
-- COMPRA / FROTA
-- ───────────────────────────────────────────────────────────────────────────────
v_form := pg_temp.f_form('COMPRA', 'FROTA', 'Solicitação de Compra — Frota',
    'Manutenção, peças e serviços de veículos.', FALSE);

v_secao := pg_temp.f_secao(v_form, 'Identificação', NULL, 0);
PERFORM pg_temp.f_can(v_secao, 'SOLICITANTE_COLABORADOR', 'solicitanteColaboradorId', 'REFERENCIA', 'COLABORADOR', 'Nome do Solicitante', TRUE, 0);
PERFORM pg_temp.f_can(v_secao, 'BASE', 'baseId', 'REFERENCIA', 'BASE_OPERACIONAL', 'BASE', TRUE, 1);
PERFORM pg_temp.f_can(v_secao, 'PROCESSO', 'processoId', 'REFERENCIA', 'PROCESSO', 'Setor / Centro de Custo', TRUE, 2);

v_secao := pg_temp.f_secao(v_form, 'Veículo e Natureza', NULL, 1);
v_campo := pg_temp.f_din(v_secao, 'naturezaServico', 'SELECAO_UNICA', 'Natureza', TRUE, 0);
PERFORM pg_temp.f_opcoes(v_campo, c_nat_valores, c_nat_rotulos);
v_campo := pg_temp.f_din(v_secao, 'motivoSolicitacao', 'SELECAO_UNICA', 'Motivo', TRUE, 1);
PERFORM pg_temp.f_opcoes(v_campo, c_mot_valores, c_mot_rotulos);
PERFORM pg_temp.f_can(v_secao, 'VEICULO', 'veiculoId', 'REFERENCIA', 'VEICULO', 'Placa', TRUE, 2);
PERFORM pg_temp.f_can(v_secao, 'REGIME_VEICULO', 'regimeVeiculo', 'SELECAO_UNICA', NULL, 'Veículo Próprio ou Locado?', TRUE, 3);

v_secao := pg_temp.f_secao(v_form, 'Fornecedor',
    'Escolha um fornecedor cadastrado ou informe o nome, se ainda não existir.', 2);
PERFORM pg_temp.f_can(v_secao, 'FORNECEDOR', 'fornecedorId', 'REFERENCIA', 'FORNECEDOR', 'Fornecedor (cadastrado)', FALSE, 0);
PERFORM pg_temp.f_can(v_secao, 'FORNECEDOR_NOME', 'fornecedorNome', 'TEXTO', NULL, 'Fornecedor (não cadastrado)', FALSE, 1);
PERFORM pg_temp.f_can(v_secao, 'FORNECEDOR_RAZAO_SOCIAL', 'fornecedorRazaoSocial', 'TEXTO', NULL, 'Razão Social', FALSE, 2);

v_secao := pg_temp.f_secao(v_form, 'Compra', NULL, 3);
PERFORM pg_temp.f_can(v_secao, 'QUANTIDADE', 'quantidade', 'DECIMAL', NULL, 'Quantidade', TRUE, 0);
v_campo := pg_temp.f_can(v_secao, 'VALOR', 'valor', 'MOEDA', NULL, 'Valor', TRUE, 1);
PERFORM pg_temp.f_lim(v_campo, NULL, 0.01, NULL);
PERFORM pg_temp.f_can(v_secao, 'PRIORIDADE', 'prioridade', 'SELECAO_UNICA', NULL, 'Grau de Prioridade', TRUE, 2);
PERFORM pg_temp.f_can(v_secao, 'LOCAL_AQUISICAO', 'localAquisicao', 'SELECAO_UNICA', NULL, 'Local de Aquisição', TRUE, 3);
PERFORM pg_temp.f_can(v_secao, 'FORMA_PAGAMENTO', 'formaPagamento', 'SELECAO_UNICA', NULL, 'Forma de Pagamento', TRUE, 4);
v_campo := pg_temp.f_can(v_secao, 'QUANTIDADE_PARCELAS', 'quantidadeParcelas', 'INTEIRO', NULL, 'Parcelamento', TRUE, 5);
PERFORM pg_temp.f_lim(v_campo, NULL, 1, 48);
v_campo := pg_temp.f_can(v_secao, 'DESCRICAO', 'descricao', 'TEXTO_LONGO', NULL, 'Descrição da Compra / Serviço', TRUE, 6);
PERFORM pg_temp.f_lim(v_campo, 2000, NULL, NULL);

v_secao := pg_temp.f_secao(v_form, 'Nota Fiscal', NULL, 4);
PERFORM pg_temp.f_can(v_secao, 'TEM_NF', 'temNf', 'BOOLEANO', NULL, 'Tem NF', TRUE, 0);
v_campo := pg_temp.f_din(v_secao, 'justificativaSemNf', 'TEXTO_LONGO', 'Justificativa por não haver NF', TRUE, 1);
PERFORM pg_temp.f_cond(v_campo, 'temNf', 'false');

v_secao := pg_temp.f_secao(v_form, 'Complemento', NULL, 5);
v_campo := pg_temp.f_can(v_secao, 'OBSERVACOES', 'observacoes', 'TEXTO_LONGO', NULL, 'Observações, OS ou Rateio', FALSE, 0);
PERFORM pg_temp.f_lim(v_campo, 2000, NULL, NULL);

-- ───────────────────────────────────────────────────────────────────────────────
-- COMPRA / OBRA_CIVIL
-- ───────────────────────────────────────────────────────────────────────────────
v_form := pg_temp.f_form('COMPRA', 'OBRA_CIVIL', 'Aprovação de Compra — Obras Civis',
    'Aquisições apropriadas a uma obra. Substitui a base pelo contrato da obra.', FALSE);

v_secao := pg_temp.f_secao(v_form, 'Identificação', NULL, 0);
PERFORM pg_temp.f_can(v_secao, 'SOLICITANTE_COLABORADOR', 'solicitanteColaboradorId', 'REFERENCIA', 'COLABORADOR', 'Solicitante', TRUE, 0);
PERFORM pg_temp.f_can(v_secao, 'OBRA', 'obraId', 'REFERENCIA', 'OBRA', 'Obra', TRUE, 1);
PERFORM pg_temp.f_can(v_secao, 'PROCESSO', 'processoId', 'REFERENCIA', 'PROCESSO', 'Setor / Centro de Custo', FALSE, 2);

v_secao := pg_temp.f_secao(v_form, 'Fornecedor',
    'Escolha um fornecedor cadastrado ou informe o nome, se ainda não existir.', 1);
PERFORM pg_temp.f_can(v_secao, 'FORNECEDOR', 'fornecedorId', 'REFERENCIA', 'FORNECEDOR', 'Fornecedor (cadastrado)', FALSE, 0);
PERFORM pg_temp.f_can(v_secao, 'FORNECEDOR_NOME', 'fornecedorNome', 'TEXTO', NULL, 'Fornecedor (não cadastrado)', FALSE, 1);
PERFORM pg_temp.f_can(v_secao, 'FORNECEDOR_RAZAO_SOCIAL', 'fornecedorRazaoSocial', 'TEXTO', NULL, 'Razão Social', FALSE, 2);

v_secao := pg_temp.f_secao(v_form, 'Compra', NULL, 2);
PERFORM pg_temp.f_can(v_secao, 'QUANTIDADE', 'quantidade', 'DECIMAL', NULL, 'Quantidade', TRUE, 0);
v_campo := pg_temp.f_can(v_secao, 'VALOR', 'valor', 'MOEDA', NULL, 'Valor', TRUE, 1);
PERFORM pg_temp.f_lim(v_campo, NULL, 0.01, NULL);
PERFORM pg_temp.f_can(v_secao, 'LOCAL_AQUISICAO', 'localAquisicao', 'SELECAO_UNICA', NULL, 'Local de Aquisição', TRUE, 2);
PERFORM pg_temp.f_can(v_secao, 'FORMA_PAGAMENTO', 'formaPagamento', 'SELECAO_UNICA', NULL, 'Forma de Pagamento', TRUE, 3);
v_campo := pg_temp.f_can(v_secao, 'QUANTIDADE_PARCELAS', 'quantidadeParcelas', 'INTEIRO', NULL, 'Parcelamento', TRUE, 4);
PERFORM pg_temp.f_lim(v_campo, NULL, 1, 48);
PERFORM pg_temp.f_can(v_secao, 'VENCIMENTO', 'vencimento', 'DATA', NULL, 'Vencimento', TRUE, 5);
PERFORM pg_temp.f_can(v_secao, 'DADOS_PAGAMENTO', 'dadosPagamento', 'TEXTO_LONGO', NULL, 'Dados para pagamento (PIX, AG, C/C)', FALSE, 6);
v_campo := pg_temp.f_can(v_secao, 'DESCRICAO', 'descricao', 'TEXTO_LONGO', NULL, 'Descrição da Compra / Serviço', TRUE, 7);
PERFORM pg_temp.f_lim(v_campo, 2000, NULL, NULL);

v_secao := pg_temp.f_secao(v_form, 'Nota Fiscal', NULL, 3);
PERFORM pg_temp.f_can(v_secao, 'TEM_NF', 'temNf', 'BOOLEANO', NULL, 'Tem NF', TRUE, 0);
v_campo := pg_temp.f_din(v_secao, 'justificativaSemNf', 'TEXTO_LONGO', 'Justificativa por não haver NF', TRUE, 1);
PERFORM pg_temp.f_cond(v_campo, 'temNf', 'false');

v_secao := pg_temp.f_secao(v_form, 'Complemento', NULL, 4);
v_campo := pg_temp.f_can(v_secao, 'OBSERVACOES', 'observacoes', 'TEXTO_LONGO', NULL, 'Observação, Rateio, OS, etc.', FALSE, 0);
PERFORM pg_temp.f_lim(v_campo, 2000, NULL, NULL);

-- ───────────────────────────────────────────────────────────────────────────────
-- PAGAMENTO / PADRAO
--
-- Um único formulário cobre os dois casos do Google Forms: a pergunta inicial
-- governa a exibição do vínculo com a compra ou do motivo de não haver compra.
-- Os campos herdados continuam visíveis e são pré-preenchidos pelo endpoint de
-- pré-preenchimento, em vez de sumirem da tela.
-- ───────────────────────────────────────────────────────────────────────────────
v_form := pg_temp.f_form('PAGAMENTO', 'PADRAO', 'Solicitação de Pagamento',
    'Pedido de autorização de pagamento, com ou sem compra prévia.', TRUE);

v_secao := pg_temp.f_secao(v_form, 'Origem', NULL, 0);
PERFORM pg_temp.f_can(v_secao, 'POSSUI_COMPRA_PREVIA', 'possuiCompraPrevia', 'BOOLEANO', NULL,
    'Já foi feita solicitação de compra antes?', TRUE, 0);
v_campo := pg_temp.f_can(v_secao, 'SOLICITACAO_COMPRA', 'solicitacaoCompraId', 'REFERENCIA', 'SOLICITACAO_COMPRA',
    'Solicitação de compra aprovada', TRUE, 1);
PERFORM pg_temp.f_cond(v_campo, 'possuiCompraPrevia', 'true');
v_campo := pg_temp.f_din(v_secao, 'motivoSemCompra', 'SELECAO_UNICA', 'Motivo de não haver solicitação de compra', TRUE, 2);
PERFORM pg_temp.f_opcoes(v_campo,
    ARRAY['COMBUSTIVEL_FORNECEDOR_CADASTRADO','DESPESA_MENSAL_RECORRENTE','OUTRA'],
    ARRAY['Combustíveis em fornecedor cadastrado',
          'Despesa mensal recorrente (água, energia, plano de saúde, sistemas, locação, telefonia, internet, serviços contratados, seguros)',
          'Outra']);
PERFORM pg_temp.f_cond(v_campo, 'possuiCompraPrevia', 'false');

v_secao := pg_temp.f_secao(v_form, 'Identificação',
    'Preenchido automaticamente quando houver compra vinculada.', 1);
PERFORM pg_temp.f_can(v_secao, 'SOLICITANTE_COLABORADOR', 'solicitanteColaboradorId', 'REFERENCIA', 'COLABORADOR', 'Nome do Solicitante do Pagamento', TRUE, 0);
PERFORM pg_temp.f_can(v_secao, 'BASE', 'baseId', 'REFERENCIA', 'BASE_OPERACIONAL', 'Base', TRUE, 1);
PERFORM pg_temp.f_can(v_secao, 'PROCESSO', 'processoId', 'REFERENCIA', 'PROCESSO', 'Setor / Centro de Custo', TRUE, 2);
PERFORM pg_temp.f_can(v_secao, 'OBRA', 'obraId', 'REFERENCIA', 'OBRA', 'Obra', FALSE, 3);

v_secao := pg_temp.f_secao(v_form, 'Fornecedor',
    'Escolha um fornecedor cadastrado ou informe o nome, se ainda não existir.', 2);
PERFORM pg_temp.f_can(v_secao, 'FORNECEDOR', 'fornecedorId', 'REFERENCIA', 'FORNECEDOR', 'Fornecedor (cadastrado)', FALSE, 0);
PERFORM pg_temp.f_can(v_secao, 'FORNECEDOR_NOME', 'fornecedorNome', 'TEXTO', NULL, 'Fornecedor (não cadastrado)', FALSE, 1);
PERFORM pg_temp.f_can(v_secao, 'FORNECEDOR_RAZAO_SOCIAL', 'fornecedorRazaoSocial', 'TEXTO', NULL, 'Razão Social', FALSE, 2);

v_secao := pg_temp.f_secao(v_form, 'Pagamento', NULL, 3);
v_campo := pg_temp.f_can(v_secao, 'VALOR', 'valor', 'MOEDA', NULL, 'Valor', TRUE, 0);
PERFORM pg_temp.f_lim(v_campo, NULL, 0.01, NULL);
PERFORM pg_temp.f_can(v_secao, 'VENCIMENTO', 'vencimento', 'DATA', NULL, 'Vencimento único ou 1ª parcela', TRUE, 1);
PERFORM pg_temp.f_can(v_secao, 'FORMA_PAGAMENTO', 'formaPagamento', 'SELECAO_UNICA', NULL, 'Forma de Pagamento', TRUE, 2);
v_campo := pg_temp.f_can(v_secao, 'QUANTIDADE_PARCELAS', 'quantidadeParcelas', 'INTEIRO', NULL, 'Parcelamento', TRUE, 3);
PERFORM pg_temp.f_lim(v_campo, NULL, 1, 48);
PERFORM pg_temp.f_can(v_secao, 'DADOS_PAGAMENTO', 'dadosPagamento', 'TEXTO_LONGO', NULL, 'Dados Bancários (Banco, AG, Conta, Pix)', TRUE, 4);
v_campo := pg_temp.f_din(v_secao, 'tipoDespesa', 'SELECAO_UNICA', 'Tipo de Despesa', TRUE, 5);
PERFORM pg_temp.f_opcoes(v_campo,
    ARRAY['MATERIAL','SERVICO','COMBUSTIVEL','LOCACAO','UTILIDADES','TRIBUTOS','MANUTENCAO','OUTRA'],
    ARRAY['Material','Serviço','Combustível','Locação','Utilidades (água, energia, telefonia, internet)',
          'Tributos e taxas','Manutenção','Outra']);

v_secao := pg_temp.f_secao(v_form, 'Nota Fiscal', NULL, 4);
PERFORM pg_temp.f_can(v_secao, 'TEM_NF', 'temNf', 'BOOLEANO', NULL, 'Tem NF', TRUE, 0);
v_campo := pg_temp.f_can(v_secao, 'NUMERO_NF', 'numeroNf', 'TEXTO', NULL, 'nº Nota Fiscal', TRUE, 1);
PERFORM pg_temp.f_cond(v_campo, 'temNf', 'true');
v_campo := pg_temp.f_din(v_secao, 'justificativaSemNf', 'TEXTO_LONGO', 'Justificativa por não haver NF', TRUE, 2);
PERFORM pg_temp.f_cond(v_campo, 'temNf', 'false');

v_secao := pg_temp.f_secao(v_form, 'Complemento', NULL, 5);
v_campo := pg_temp.f_can(v_secao, 'OBSERVACOES', 'observacoes', 'TEXTO_LONGO', NULL, 'Observações, OS ou Rateio', FALSE, 0);
PERFORM pg_temp.f_lim(v_campo, 2000, NULL, NULL);

END $seed$;

-- ═══════════════════════════════════════════════════════════════════════════════
-- Alçadas de aprovação
--
-- Faixas contíguas e sem sobreposição, cobrindo de zero ao infinito para que
-- nenhuma solicitação fique sem aprovador. Os limites são ponto de partida —
-- ajuste pela API de alçadas conforme a política da diretoria.
-- ═══════════════════════════════════════════════════════════════════════════════

INSERT INTO financeiro_alcada_aprovacao
    (id, tipo_solicitacao, descricao, valor_minimo, valor_maximo, permissao_requerida,
     versao, data_criacao, ativo, usuario_criacao)
VALUES
    (gen_random_uuid(), 'COMPRA',    'Até R$ 5.000 — Coordenação',           0.00,      5000.00,  'financeiro:compra:aprovar:n1',    0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA'),
    (gen_random_uuid(), 'COMPRA',    'De R$ 5.000,01 a R$ 30.000 — Gerência', 5000.01,  30000.00, 'financeiro:compra:aprovar:n2',    0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA'),
    (gen_random_uuid(), 'COMPRA',    'Acima de R$ 30.000 — Diretoria',        30000.01, NULL,     'financeiro:compra:aprovar:n3',    0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA'),
    (gen_random_uuid(), 'PAGAMENTO', 'Até R$ 5.000 — Coordenação',            0.00,     5000.00,  'financeiro:pagamento:aprovar:n1', 0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA'),
    (gen_random_uuid(), 'PAGAMENTO', 'De R$ 5.000,01 a R$ 30.000 — Gerência', 5000.01,  30000.00, 'financeiro:pagamento:aprovar:n2', 0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA'),
    (gen_random_uuid(), 'PAGAMENTO', 'Acima de R$ 30.000 — Diretoria',        30000.01, NULL,     'financeiro:pagamento:aprovar:n3', 0, CURRENT_TIMESTAMP, TRUE, 'SISTEMA');
