export type NavigationParameterType = 'UUID' | 'ENUM';
export type NavigationParameterDestination = 'PATH' | 'QUERY';

export interface NavigationParameter {
  name: string;
  type: NavigationParameterType;
  destination: NavigationParameterDestination;
  required: boolean;
  allowedValues?: readonly string[];
}

export interface NavigationTarget {
  route: string;
  routeAuthority: string;
  parameters: readonly NavigationParameter[];
}

const ID_PATH: NavigationParameter = {
  name: 'id', type: 'UUID', destination: 'PATH', required: true
};
const PRINT_QUERY: NavigationParameter = {
  name: 'mode', type: 'ENUM', destination: 'QUERY', required: false,
  allowedValues: ['print']
};

function target(
  route: string,
  routeAuthority: string,
  parameters: readonly NavigationParameter[] = []
): NavigationTarget {
  return { route, routeAuthority, parameters };
}

/**
 * Destinos de navegação que o Copiloto pode oferecer como botão. Espelha as chaves do catálogo do
 * backend (navigation-actions.yaml); chaves sem rota no frontend são descartadas pelo resolvedor.
 */
export const COPILOTO_DESTINOS: Record<string, NavigationTarget> = {
  CES_HOME: target('/home', 'assistente:chat:use'),
  IAM_USUARIOS: target('/admin/cadastros/usuarios', 'iam:usuario:read'),
  IAM_PERFIS_ACESSO: target('/admin/cadastros/perfis-acesso', 'iam:perfil:read'),
  IAM_PERMISSOES: target('/admin/cadastros/permissoes', 'iam:permissao:read'),

  RH_COLABORADORES: target('/admin/cadastros/colaboradores', 'rh:colaborador:read'),
  RH_FUNCOES: target('/admin/cadastros/funcoes', 'rh:funcao:read'),
  RH_PROCESSOS: target('/admin/cadastros/processos', 'rh:processo-negocio:read'),
  RH_BASES_OPERACIONAIS: target('/admin/cadastros/bases-operacionais', 'rh:base-operacional:read'),
  RH_MUNICIPIOS: target('/admin/cadastros/municipios', 'rh:municipio:read'),
  RH_VEICULOS: target('/admin/cadastros/veiculos', 'rh:veiculo:read'),
  RH_EQUIPES: target('/admin/cadastros/equipes', 'rh:equipe:read'),
  RH_TIPOS_EQUIPE: target('/admin/cadastros/tipos-equipe', 'rh:tipo-equipe:read'),
  RH_CENTROS_CUSTO: target('/admin/cadastros/centros-custo', 'rh:centro-faturamento:read'),

  CONTRATOS: target('/admin/cadastros/contratos', 'contrato:contrato:read'),
  CONTRATO_ACOES: target('/admin/cadastros/acoes', 'contrato:acao:read'),
  CONTRATO_ACOES_VALORES: target('/admin/cadastros/acoes-valores', 'contrato:acao-valor:read'),
  EXECUCAO_TIPOS_SERVICO: target('/admin/cadastros/tipos-servico', 'execucao:tipo-servico:read'),
  DESCONTO_MOTIVOS: target('/admin/cadastros/motivos-desconto', 'desconto:motivo:read'),
  DESCONTO_TIPOS_EQUIPAMENTO: target(
    '/admin/cadastros/tipos-equipamento-desconto', 'desconto:tipo-equipamento:read'
  ),

  DESCONTOS_LISTA: target('/dp/descontos/lancamento', 'desconto:read'),
  DESCONTO_NOVO: target('/dp/descontos/novo', 'desconto:solicitar'),
  AUTOMACOES: target('/notificacoes/automacoes', 'automacao:read'),
  AUTOMACAO_EDITOR: target('/notificacoes/automacoes/:id/editar', 'automacao:manage', [ID_PATH]),
  DESCONTO_DETALHE: target('/dp/descontos/:id', 'desconto:read', [ID_PATH, PRINT_QUERY]),

  SST_FORMULARIOS: target('/sst/formularios', 'sst:formulario:read'),
  SST_FORMULARIO_NOVO: target('/sst/formularios/novo', 'sst:formulario:write'),
  SST_FORMULARIO_EDITAR: target('/sst/formularios/:id', 'sst:formulario:write', [ID_PATH]),
  SST_INSPECOES: target('/sst/inspecoes', 'sst:inspecao:read'),
  SST_INSPECAO_NOVA: target('/sst/inspecoes/nova', 'sst:inspecao:write'),
  SST_INSPECAO_DETALHE: target('/sst/inspecoes/:id', 'sst:inspecao:read', [ID_PATH]),
  SST_DESVIOS: target('/sst/desvios', 'sst:inspecao:read'),
  SST_MEUS_DESVIOS: target('/sst/meus-desvios', 'sst:inspecao:read'),
  SST_DASHBOARD_INSPECOES: target('/sst/dashboards/inspecoes', 'sst:dashboard:read'),
  SST_DASHBOARD_DESVIOS: target('/sst/dashboards/desvios', 'sst:dashboard:read'),

  CHAMADOS_MEUS: target('/chamados/meus', 'chamado:read'),
  CHAMADO_NOVO: target('/chamados/novo', 'chamado:write'),
  CHAMADO_MEU_DETALHE: target('/chamados/meus/:id', 'chamado:read', [ID_PATH]),
  CHAMADOS_FILA: target('/chamados/fila', 'chamado:manage'),
  CHAMADO_FILA_DETALHE: target('/chamados/fila/:id', 'chamado:manage', [ID_PATH]),
  CHAMADOS_TOPICOS: target('/chamados/admin/topicos', 'chamado:manage'),
  CHAMADOS_EQUIPES: target('/chamados/admin/equipes', 'chamado:manage'),

  FINANCEIRO_COMPRAS: target('/financeiro/compras', 'financeiro:compra:read'),
  FINANCEIRO_COMPRA_NOVA: target('/financeiro/compras/nova', 'financeiro:compra:write'),
  FINANCEIRO_COMPRA_DETALHE: target(
    '/financeiro/compras/:id', 'financeiro:compra:read', [ID_PATH]
  ),
  FINANCEIRO_COMPRA_EDITAR: target(
    '/financeiro/compras/:id/editar', 'financeiro:compra:write', [ID_PATH]
  ),
  FINANCEIRO_PAGAMENTOS: target('/financeiro/pagamentos', 'financeiro:pagamento:read'),
  FINANCEIRO_PAGAMENTO_NOVO: target(
    '/financeiro/pagamentos/nova', 'financeiro:pagamento:write'
  ),
  FINANCEIRO_PAGAMENTO_DETALHE: target(
    '/financeiro/pagamentos/:id', 'financeiro:pagamento:read', [ID_PATH]
  ),
  FINANCEIRO_PAGAMENTO_EDITAR: target(
    '/financeiro/pagamentos/:id/editar', 'financeiro:pagamento:write', [ID_PATH]
  ),
  FINANCEIRO_APROVAR_COMPRAS: target(
    '/financeiro/aprovacoes/compras', 'financeiro:compra:aprovar'
  ),
  FINANCEIRO_APROVAR_PAGAMENTOS: target(
    '/financeiro/aprovacoes/pagamentos', 'financeiro:pagamento:aprovar'
  ),
  FINANCEIRO_FORNECEDORES: target(
    '/financeiro/admin/fornecedores', 'financeiro:fornecedor:read'
  ),
  FINANCEIRO_FORMULARIOS: target(
    '/financeiro/admin/formularios', 'financeiro:formulario:read'
  ),
  FINANCEIRO_FORMULARIO_EDITAR: target(
    '/financeiro/admin/formularios/:id', 'financeiro:formulario:write', [ID_PATH]
  ),

  ALMOX_MATERIAIS: target('/almoxarifado/materiais', 'estoque:material:read'),
  ALMOX_AVALIACOES: target('/almoxarifado/avaliacoes', 'estoque:avaliacao-material:read'),
  ALMOX_CENTROS_INVESTIMENTO: target(
    '/almoxarifado/centros-investimento', 'estoque:centro-investimento:read'
  ),
  ALMOX_ESTOQUES: target('/almoxarifado/estoques', 'estoque:estoque:read'),
  ALMOX_TIPOS_ESTOQUE: target('/almoxarifado/tipos-estoque', 'estoque:tipo-estoque:read'),
  ALMOX_SOLICITACOES: target('/almoxarifado/solicitacoes-tecnicas', 'estoque:solicitacao:read'),
  ALMOX_SOLICITACOES_TECNICAS: target(
    '/almoxarifado/solicitacoes-tecnicas', 'estoque:solicitacao:read'
  ),
  ALMOX_SOLICITACAO_TECNICA_NOVA: target(
    '/almoxarifado/solicitacoes-tecnicas/nova', 'estoque:solicitacao:create'
  ),
  ALMOX_SOLICITACAO_DETALHE: target(
    '/almoxarifado/solicitacoes/:id', 'estoque:solicitacao:read', [ID_PATH]
  ),
  ALMOX_SOLICITACAO_TECNICA_DETALHE: target(
    '/almoxarifado/solicitacoes-tecnicas/:id', 'estoque:solicitacao:read', [ID_PATH]
  ),
  ALMOX_DASHBOARD_SOLICITACOES: target(
    '/almoxarifado/dashboard/solicitacoes', 'estoque:solicitacao:read'
  ),
  ALMOX_SALDOS: target('/almoxarifado/saldos', 'estoque:saldo:read'),
  ALMOX_SALDOS_RELATORIO: target('/almoxarifado/saldos/relatorio', 'estoque:saldo:read'),
  ALMOX_MOVIMENTACOES: target('/almoxarifado/movimentacoes', 'estoque:movimentacao:read'),
  ALMOX_MOVIMENTACAO_DETALHE: target(
    '/almoxarifado/movimentacoes/:id', 'estoque:movimentacao:read', [ID_PATH]
  ),

  EXECUCAO_COMERCIAL: target('/execucao/comercial', 'execucao:comercial:read'),
  EXECUCAO_TURNOS: target('/execucao/turnos', 'execucao:comercial:read'),
  EXECUCAO_DASHBOARD_OPERACIONAL: target(
    '/execucao/dashboard-operacional', 'obras:turno:read'
  ),
  EXECUCAO_DASHBOARD_TURNOS: target(
    '/execucao/dashboard-turnos', 'execucao:comercial:read'
  ),
  EXECUCAO_COMERCIAL_DETALHE: target(
    '/execucao/comercial/:id', 'execucao:comercial:read', [ID_PATH, PRINT_QUERY]
  ),
  EXECUCAO_TURNO_DETALHE: target(
    '/execucao/turnos/:id/detalhamento', 'execucao:comercial:read', [ID_PATH]
  ),
  // Destinos usados pelos cartões de cadastro (link para o registro gravado).
  OBRAS: target('/obras', 'obras:obra:read'),
  OBRA_DETALHE: target('/obras/:id', 'obras:obra:read', [ID_PATH]),
  OBRAS_STATUS: target('/obras/cadastros/status', 'obras:cadastros:read'),
  OBRAS_AGENTES: target('/obras/cadastros/agentes', 'obras:cadastros:read'),
  OBRAS_TIPOS_ANEXO: target('/obras/cadastros/tipos-anexos', 'obras:cadastros:read'),
  SST_EVENTOS: target('/sst/eventos', 'sst:evento:read'),
  SST_EVENTO_DETALHE: target('/sst/eventos/:id', 'sst:evento:read', [ID_PATH]),
  SST_GOVERNANCA_ACOES: target('/sst/governanca/acoes', 'sst:governanca:read'),
  SST_METAS_INSPECAO: target('/sst/metas-inspecao', 'sst:meta:read'),
  SST_TIPOS_INSPECAO: target('/sst/tipos-inspecao', 'sst:catalogo:read'),
  SST_ATIVIDADES: target('/sst/atividades', 'sst:catalogo:read')
};
