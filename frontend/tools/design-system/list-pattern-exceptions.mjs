/**
 * Exceções da guarda do padrão de listagem (docs/design-system/components/list-page.md).
 *
 * Listagens usam `ces-data-table`. Os arquivos abaixo importam `TableModule` de `primeng/table`
 * para tabelas que NÃO são listagens — por isso não migram — e cada grupo diz por quê. Uma tela de
 * listagem nova não entra aqui: use `ces-data-table` (modo servidor para dados paginados).
 */
export const TABLE_MODULE_EXCEPTIONS = {
  /** Itens editáveis dentro de formulários de movimentação e solicitação (linha a linha, sem paginação de servidor). */
  'tabela de itens em formulário': [
    'src/app/features/almoxarifado/movimentacoes-estoque/baixa-avulsa/stock-exit.component.ts',
    'src/app/features/almoxarifado/movimentacoes-estoque/devolucao-comercial/devolucao-comercial.component.ts',
    'src/app/features/almoxarifado/movimentacoes-estoque/entrada-avulsa/stock-entry.component.ts',
    'src/app/features/almoxarifado/movimentacoes-estoque/transferencias/stock-transfer.component.ts',
    'src/app/features/almoxarifado/solicitacoes-material/solicitacao-material-form/solicitacao-material-form.component.ts',
    'src/app/features/almoxarifado/solicitacoes-material/solicitacao-tecnica-acao/solicitacao-tecnica-acao.component.ts',
    'src/app/features/almoxarifado/solicitacoes-material/solicitacao-tecnica-form/solicitacao-tecnica-form.component.ts',
    'src/app/features/almoxarifado/solicitacoes-material/solicitacao-tecnica-planejamento/solicitacao-tecnica-planejamento.component.ts',
    'src/app/features/dp/descontos/desconto-form/desconto-form.component.ts',
    'src/app/features/sst/governanca/governanca-membros-etapa.component.ts',
    'src/app/features/sst/governanca/reuniao-governanca.component.ts',
    'src/app/features/sst/governanca/governanca-configuracoes.component.ts'
  ],
  /** Relatórios em tela cheia para impressão (fora do escopo do padrão de listagem, design.md "Non-Goals"). */
  'relatório para impressão': [
    'src/app/features/almoxarifado/solicitacoes-material/relatorio-solicitacao-material/relatorio-solicitacao-material.component.ts',
    'src/app/features/dp/descontos/relatorio-desconto/relatorio-desconto.component.ts'
  ],
  /** Rankings e quadros resumidos dentro de painéis (dados já agregados, sem paginação). */
  'quadro dentro de painel': [
    'src/app/features/chamados/dashboard/chamados-dashboard.component.ts',
    'src/app/features/dp/descontos/dashboard/dashboard-descontos.component.ts',
    'src/app/features/financeiro/dashboard/dashboard-financeiro.component.ts',
    'src/app/features/sst/metas-inspecoes/acompanhamento-metas.component.ts'
  ],
  /** Manuais com tabelas de referência estáticas. */
  'conteúdo de manual': [
    'src/app/features/dp/descontos/manual-descontos/manual-descontos.component.ts',
    'src/app/features/sst/inspecoes/manual-inspecoes/manual-inspecoes.component.ts'
  ]
};
