/**
 * Destino ao clicar — catálogo das rotas reais do Nexo.
 *
 * Mora ao lado do formulário de regras, e não em `notificacoes-ui.ts`, porque aquele arquivo é
 * alcançado pelo sininho e pelo mural — presentes no shell da aplicação. A lista de rotas só
 * interessa a esta tela de administração e não tem por que pesar no pacote inicial.
 */

/**
 * Destinos oferecidos no formulário de regra.
 *
 * O campo era texto livre, e foi o texto livre que produziu regras de fábrica apontando para rotas
 * inexistentes: a rota `**` do Angular redireciona para `home`, então o defeito era silencioso —
 * quem clicava no aviso caía na tela inicial sem nenhum erro.
 *
 * `requer` é a variável que o caminho consome. O formulário só mostra o destino quando o gatilho
 * escolhido fornece essa variável, evitando de saída um template que o backend recusaria.
 */
export interface DestinoDeeplink {
  label: string;
  valor: string;
  requer?: string;
}

export interface GrupoDestinoDeeplink {
  label: string;
  items: DestinoDeeplink[];
}

export const DESTINOS_DEEPLINK: GrupoDestinoDeeplink[] = [
  {
    label: 'Descontos em folha',
    items: [
      { label: 'Detalhe do desconto', valor: '/dp/descontos/{{idDesconto}}', requer: 'idDesconto' },
      { label: 'Lançamento de descontos', valor: '/dp/descontos/lancamento' }
    ]
  },
  {
    label: 'Chamados',
    items: [
      {
        label: 'Detalhe — visão de quem abriu',
        valor: '/chamados/meus/{{idChamado}}',
        requer: 'idChamado'
      },
      {
        label: 'Detalhe — visão do atendimento',
        valor: '/chamados/fila/{{idChamado}}',
        requer: 'idChamado'
      },
      { label: 'Fila de atendimento', valor: '/chamados/fila' },
      { label: 'Meus chamados', valor: '/chamados/meus' }
    ]
  },
  {
    label: 'Financeiro',
    items: [
      {
        label: 'Detalhe da compra',
        valor: '/financeiro/compras/{{idSolicitacao}}',
        requer: 'idSolicitacao'
      },
      {
        label: 'Detalhe do pagamento',
        valor: '/financeiro/pagamentos/{{idSolicitacao}}',
        requer: 'idSolicitacao'
      },
      { label: 'Aprovações de compra', valor: '/financeiro/aprovacoes/compras' },
      { label: 'Aprovações de pagamento', valor: '/financeiro/aprovacoes/pagamentos' }
    ]
  },
  {
    label: 'Almoxarifado',
    items: [
      {
        label: 'Detalhe da solicitação de material',
        valor: '/almoxarifado/solicitacoes/{{idSolicitacao}}',
        requer: 'idSolicitacao'
      },
      { label: 'Solicitações de material', valor: '/almoxarifado/solicitacoes' },
      { label: 'Painel de solicitações', valor: '/almoxarifado/dashboard/solicitacoes' },
      { label: 'Painel de solicitações comerciais', valor: '/almoxarifado/dashboard/solicitacoes/comercial' },
      { label: 'Painel de solicitações técnicas', valor: '/almoxarifado/dashboard/solicitacoes/tecnico' }
    ]
  },
  {
    label: 'Segurança do trabalho',
    items: [
      { label: 'Painel de desvios', valor: '/sst/dashboards/desvios' },
      { label: 'Meus desvios', valor: '/sst/meus-desvios' },
      { label: 'Inspeções', valor: '/sst/inspecoes' }
    ]
  },
  {
    label: 'Geral',
    items: [
      { label: 'Mural de comunicados', valor: '/mural' },
      { label: 'Minhas notificações', valor: '/notificacoes' },
      { label: 'Tela inicial', valor: '/home' }
    ]
  }
];

/**
 * Rotas reais de `app.routes.ts`, no formato do Angular (`:param` é um segmento qualquer).
 *
 * Existe para o formulário conseguir avisar quando alguém digita um caminho que não leva a lugar
 * nenhum. Precisa acompanhar `app.routes.ts` — é uma cópia, e uma cópia desatualizada só produz
 * alarme falso, nunca bloqueio: a validação é um aviso, não um impedimento.
 */
export const ROTAS_CONHECIDAS: string[] = [
  '/home',
  '/manuais',
  '/manual-descontos',
  '/manual-sst',
  '/mural',
  '/mural/:id',
  '/notificacoes',
  '/notificacoes/preferencias',
  '/notificacoes/admin/comunicados',
  '/notificacoes/admin/comunicados/novo',
  '/notificacoes/admin/comunicados/:id/editar',
  '/notificacoes/admin/regras',
  '/notificacoes/admin/regras/nova',
  '/notificacoes/admin/regras/:id/editar',
  '/notificacoes/admin/politicas-roteamento',
  '/notificacoes/admin/politicas-roteamento/nova',
  '/notificacoes/admin/politicas-roteamento/:id/editar',
  '/admin/cadastros/colaboradores',
  '/admin/cadastros/veiculos',
  '/admin/cadastros/bases-operacionais',
  '/admin/cadastros/contratos',
  '/admin/cadastros/acoes',
  '/admin/cadastros/acoes-valores',
  '/admin/cadastros/tipos-servico',
  '/admin/cadastros/centros-custo',
  '/admin/cadastros/equipes',
  '/admin/cadastros/funcoes',
  '/admin/cadastros/processos',
  '/admin/cadastros/tipos-equipe',
  '/admin/cadastros/motivos-desconto',
  '/admin/cadastros/tipos-equipamento-desconto',
  '/admin/cadastros/municipios',
  '/admin/cadastros/perfis-acesso',
  '/admin/cadastros/permissoes',
  '/admin/cadastros/usuarios',
  '/admin/cadastros/status',
  '/almoxarifado/transferencia-lote',
  '/almoxarifado/entrada-avulsa',
  '/almoxarifado/baixa-avulsa',
  '/almoxarifado/devolucao-comercial',
  '/almoxarifado/materiais',
  '/almoxarifado/avaliacoes',
  '/almoxarifado/centros-investimento',
  '/almoxarifado/estoques',
  '/almoxarifado/tipos-estoque',
  '/almoxarifado/solicitacoes',
  '/almoxarifado/solicitacoes/:id',
  '/almoxarifado/saldos',
  '/almoxarifado/saldos/relatorio',
  '/almoxarifado/dashboard/solicitacoes',
  '/almoxarifado/dashboard/solicitacoes/comercial',
  '/almoxarifado/dashboard/solicitacoes/tecnico',
  '/almoxarifado/movimentacoes',
  '/almoxarifado/movimentacoes/:id',
  '/execucao/comercial',
  '/execucao/comercial/:id',
  '/execucao/dashboard-operacional',
  '/execucao/dashboard-turnos',
  '/execucao/turnos',
  '/execucao/turnos/:id/detalhamento',
  '/sst/formularios',
  '/sst/formularios/novo',
  '/sst/formularios/:id',
  '/sst/inspecoes',
  '/sst/inspecoes/nova',
  '/sst/inspecoes/:id',
  '/sst/meus-desvios',
  '/sst/metas-inspecao',
  '/sst/metas-inspecao/acompanhamento',
  '/sst/dashboards/inspecoes',
  '/sst/dashboards/desvios',
  '/dp/descontos/lancamento',
  '/dp/descontos/novo',
  '/dp/descontos/:id',
  '/chamados/meus',
  '/chamados/meus/:id',
  '/chamados/novo',
  '/chamados/fila',
  '/chamados/fila/:id',
  '/chamados/admin/topicos',
  '/chamados/admin/equipes',
  '/financeiro/compras',
  '/financeiro/compras/nova',
  '/financeiro/compras/:id',
  '/financeiro/compras/:id/editar',
  '/financeiro/pagamentos',
  '/financeiro/pagamentos/nova',
  '/financeiro/pagamentos/:id',
  '/financeiro/pagamentos/:id/editar',
  '/financeiro/aprovacoes/compras',
  '/financeiro/aprovacoes/pagamentos',
  '/financeiro/admin/fornecedores',
  '/financeiro/admin/formularios',
  '/financeiro/admin/formularios/:id'
];

const SEGMENTOS_CONHECIDOS = ROTAS_CONHECIDAS.map((rota) => rota.slice(1).split('/'));

/**
 * Diz se o caminho digitado bate com alguma rota real. `{{variavel}}` conta como um segmento
 * preenchido em tempo de envio, então casa com `:param`.
 *
 * Deeplink vazio é válido: significa "aviso não clicável".
 */
export function rotaConhecida(deeplink: string | null | undefined): boolean {
  const caminho = (deeplink ?? '').trim().split(/[?#]/)[0];
  if (!caminho) return true;
  if (!caminho.startsWith('/')) return false;

  const partes = caminho.slice(1).replace(/\/$/, '').split('/');
  return SEGMENTOS_CONHECIDOS.some(
    (rota) =>
      rota.length === partes.length &&
      rota.every((segmento, indice) => segmento.startsWith(':') || segmento === partes[indice])
  );
}
