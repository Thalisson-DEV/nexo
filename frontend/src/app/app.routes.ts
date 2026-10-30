import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { publicGuard } from './core/guards/public.guard';
import { AvaliacoesMaterialComponent } from './features/almoxarifado/avaliacoes-material/avaliacoes-material.component';
import { MateriaisComponent } from './features/almoxarifado/materiais/materiais.component';
import { CentrosInvestimentoComponent } from './features/almoxarifado/centros-investimento/centros-investimento.component';
import { designSystemCatalogRoutes } from './design-system/catalog/design-system-catalog.routes';

export const routes: Routes = [
  ...designSystemCatalogRoutes,
  {
    path: 'login',
    canActivate: [publicGuard],
    // Tela cheia também na reautenticação, quando ainda existe sessão local.
    data: { fullscreen: true },
    loadComponent: () => import('./features/iam/login/login.component').then(m => m.LoginComponent)
  },
  {
    // Decisão pelo link do e-mail: sem login de propósito. O token no caminho é a
    // credencial; o backend confere prazo, uso único, status e alçada.
    path: 'decisao/:token',
    data: { fullscreen: true },
    loadComponent: () => import('./features/financeiro/decisao-email/decisao-email.component').then(m => m.DecisaoEmailComponent)
  },
  {
    // A Central roda dentro do shell autenticado: usa o cabeçalho de página, o breadcrumb e a
    // busca padrão do produto. O conteúdo também documenta credenciais padrão de primeiro
    // acesso, que não devem ficar em rota pública.
    path: 'manuais',
    canActivate: [authGuard],
    loadComponent: () => import('./features/manuais/central-treinamento.component').then(m => m.CentralTreinamentoComponent),
    title: 'Central de Treinamento e Manuais - Nexo'
  },
  {
    path: 'central-treinamento',
    redirectTo: 'manuais',
    pathMatch: 'full'
  },
  {
    path: 'treinamento',
    redirectTo: 'manuais',
    pathMatch: 'full'
  },
  {
    path: 'manual-descontos',
    loadComponent: () => import('./features/dp/descontos/manual-descontos/manual-descontos.component').then(m => m.ManualDescontosComponent),
    title: 'Manual - Descontos em Folha'
  },
  {
    path: 'manual-sst',
    loadComponent: () => import('./features/sst/inspecoes/manual-inspecoes/manual-inspecoes.component').then(m => m.ManualInspecoesComponent),
    title: 'Manual - Inspeções SST'
  },
  {
    path: 'home',
    canActivate: [authGuard],
    loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent)
  },
  {
    path: 'copiloto',
    canActivate: [authGuard],
    data: { permission: 'assistente:chat:use', workspace: true },
    title: 'Copiloto',
    loadComponent: () => import('./features/copiloto/copiloto-workspace.component').then(m => m.CopilotoWorkspaceComponent)
  },
  {
    path: 'copiloto/conectar-ia',
    canActivate: [authGuard],
    data: { permission: 'assistente:conexao:use' },
    title: 'Conectar sua IA - Nexo',
    loadComponent: () => import('./features/copiloto/conectar-ia/conectar-ia.component').then(m => m.ConectarIaComponent)
  },
  {
    // Consentimento de uma IA externa (OAuth). Sem guard: sem sessão, a própria tela manda ao
    // login e volta para cá com o mesmo pedido.
    path: 'conectar-ia/autorizar',
    data: { fullscreen: true },
    title: 'Conectar sua IA - Nexo',
    loadComponent: () => import('./features/copiloto/conectar-ia/autorizar-ia.component').then(m => m.AutorizarIaComponent)
  },
  {
    path: 'copiloto/:id',
    canActivate: [authGuard],
    data: { permission: 'assistente:chat:use', workspace: true },
    title: 'Copiloto',
    loadComponent: () => import('./features/copiloto/copiloto-workspace.component').then(m => m.CopilotoWorkspaceComponent)
  },
  {
    path: 'admin/cadastros',
    canActivate: [authGuard],
    children: [
      {
        path: 'colaboradores',
        data: { permission: 'rh:colaborador:read' },
        loadComponent: () => import('./features/rh/colaboradores/colaboradores.component').then(m => m.ColaboradoresComponent)
      },
      {
        path: 'coordenadores',
        data: { permission: 'rh:coordenador:read' },
        loadComponent: () => import('./features/rh/coordenadores/coordenadores.component').then(m => m.CoordenadoresComponent)
      },
      {
        path: 'supervisores',
        data: { permission: 'rh:supervisor:read' },
        loadComponent: () => import('./features/rh/supervisores/supervisores.component').then(m => m.SupervisoresComponent)
      },
      {
        path: 'veiculos',
        data: { permission: 'rh:veiculo:read' },
        loadComponent: () => import('./features/rh/veiculos/veiculos.component').then(m => m.VeiculosComponent)
      },
      {
        path: 'bases-operacionais',
        data: { permission: 'rh:base-operacional:read' },
        loadComponent: () => import('./features/rh/bases-operacionais/bases-operacionais.component').then(m => m.BasesOperacionaisComponent)
      },
      {
        path: 'contratos',
        data: { permission: 'contrato:contrato:read' },
        loadComponent: () => import('./features/contrato/contratos/contratos.component').then(m => m.ContratosComponent)
      },
      {
        path: 'acoes',
        data: { permission: 'contrato:acao:read' },
        loadComponent: () => import('./features/contrato/acoes/acoes.component').then(m => m.AcoesComponent)
      },
      {
        path: 'tipos-servico',
        data: { permission: 'execucao:tipo-servico:read' },
        loadComponent: () => import('./features/execucao/tipos-servico/tipos-servico.component').then(m => m.TiposServicoComponent)
      },
      {
        path: 'acoes-valores',
        data: { permission: 'contrato:acao-valor:read' },
        loadComponent: () => import('./features/contrato/acoes-valores/acoes-valores.component').then(m => m.AcoesValoresComponent)
      },
      {
        path: 'centros-custo',
        data: { permission: 'rh:centro-faturamento:read' },
        loadComponent: () => import('./features/rh/centros-faturamento/centros-faturamento.component').then(m => m.CentrosFaturamentoComponent)
      },
      {
        path: 'equipes',
        data: { permission: 'rh:equipe:read' },
        loadComponent: () => import('./features/rh/equipes/equipes.component').then(m => m.EquipesComponent)
      },
      {
        path: 'metas-faturamento',
        data: { permission: 'rh:meta-faturamento:read' },
        loadComponent: () => import('./features/rh/metas-faturamento/metas-faturamento.component').then(m => m.MetasFaturamentoComponent)
      },
      {
        path: 'funcoes',
        data: { permission: 'rh:funcao:read' },
        loadComponent: () => import('./features/rh/funcoes/funcoes.component').then(m => m.FuncoesComponent)
      },
      {
        path: 'processos',
        data: { permission: 'rh:processo-negocio:read' },
        loadComponent: () => import('./features/rh/processos/processos.component').then(m => m.ProcessosComponent)
      },
      {
        path: 'tipos-equipe',
        data: { permission: 'rh:tipo-equipe:read' },
        loadComponent: () => import('./features/rh/tipos-equipe/tipos-equipe.component').then(m => m.TiposEquipeComponent)
      },
      {
        path: 'motivos-desconto',
        data: { permission: 'desconto:motivo:read' },
        loadComponent: () => import('./features/rh/motivos-desconto/motivos-desconto.component').then(m => m.MotivosDescontoComponent)
      },
      {
        path: 'tipos-equipamento-desconto',
        data: { permission: 'desconto:tipo-equipamento:read' },
        loadComponent: () => import('./features/rh/tipos-equipamento-desconto/tipos-equipamento-desconto.component').then(m => m.TiposEquipamentoDescontoComponent)
      },
      {
        path: 'municipios',
        data: { permission: 'rh:municipio:read' },
        loadComponent: () => import('./features/rh/municipios/municipios.component').then(m => m.MunicipiosComponent)
      },
      {
        path: 'perfis-acesso',
        data: { permission: 'iam:perfil:read' },
        loadComponent: () => import('./features/iam/perfis-acesso/perfis-acesso.component').then(m => m.PerfisAcessoComponent)
      },
      {
        path: 'permissoes',
        data: { permission: 'iam:permissao:read' },
        loadComponent: () => import('./features/iam/permissoes/permissoes.component').then(m => m.PermissoesComponent)
      },
      {
        path: 'usuarios',
        data: { permission: 'iam:usuario:read' },
        loadComponent: () => import('./features/iam/usuarios/usuarios.component').then(m => m.UsuariosComponent)
      },
      {
        path: 'status',
        data: { permission: 'iam:status:read' },
        loadComponent: () => import('./features/iam/status/status.component').then(m => m.StatusComponent)
      },
      {
        path: 'tratamento-erros',
        data: { permission: 'sincronizacao:read' },
        loadComponent: () => import('./features/admin/sync-error-cases/sync-error-cases.component').then(m => m.SyncErrorCasesComponent)
      }
    ]
  },
  {
    path: 'rh',
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'coordenadores', pathMatch: 'full' },
      {
        path: 'coordenadores',
        data: { permission: 'rh:coordenador:read' },
        loadComponent: () => import('./features/rh/coordenadores/coordenadores.component').then(m => m.CoordenadoresComponent)
      },
      {
        path: 'supervisores',
        data: { permission: 'rh:supervisor:read' },
        loadComponent: () => import('./features/rh/supervisores/supervisores.component').then(m => m.SupervisoresComponent)
      }
    ]
  },
  {
    path: 'almoxarifado',
    children: [
      { path: '', redirectTo: 'avaliacoes', pathMatch: 'full' },
      {
        path: 'transferencia-lote',
        loadComponent: () => import('./features/almoxarifado/movimentacoes-estoque/transferencias/stock-transfer.component').then(m => m.StockTransferComponent)
      },
      {
        path: 'entrada-avulsa',
        loadComponent: () => import('./features/almoxarifado/movimentacoes-estoque/entrada-avulsa/stock-entry.component').then(m => m.StockEntryComponent)
      },
      {
        path: 'baixa-avulsa',
        loadComponent: () => import('./features/almoxarifado/movimentacoes-estoque/baixa-avulsa/stock-exit.component').then(m => m.StockExitComponent)
      },
      {
        path: 'devolucao-comercial',
        data: { permission: 'estoque:movimentacao:write' },
        loadComponent: () => import('./features/almoxarifado/movimentacoes-estoque/devolucao-comercial/devolucao-comercial.component').then(m => m.DevolucaoComercialComponent)
      },
      {
        path: 'materiais',
        data: { permission: 'estoque:material:read' },
        component: MateriaisComponent
      },
      {
        path: 'avaliacoes',
        data: { permission: 'estoque:avaliacao-material:read' },
        component: AvaliacoesMaterialComponent
      },
      {
        path: 'centros-investimento',
        data: { permission: 'estoque:centro-investimento:read' },
        component: CentrosInvestimentoComponent
      },
      {
        path: 'estoques',
        data: { permission: 'estoque:estoque:read' },
        loadComponent: () => import('./features/almoxarifado/estoques/estoques.component').then(m => m.EstoquesComponent)
      },
      {
        path: 'tipos-estoque',
        data: { permission: 'estoque:tipo-estoque:read' },
        loadComponent: () => import('./features/almoxarifado/tipos-estoque/tipos-estoque.component').then(m => m.TiposEstoqueComponent)
      },
      {
        path: 'motivos-pendencia',
        data: { permission: 'estoque:motivo-rejeicao:read' },
        loadComponent: () => import('./features/almoxarifado/motivos-pendencia/motivos-pendencia.component').then(m => m.MotivosPendenciaComponent)
      },
      {
        path: 'motivos-rejeicao',
        data: { permission: 'estoque:motivo-rejeicao:read' },
        loadComponent: () => import('./features/almoxarifado/motivos-rejeicao/motivos-rejeicao.component').then(m => m.MotivosRejeicaoComponent)
      },
      {
        path: 'solicitacoes',
        data: { permission: 'estoque:solicitacao:read', fullBleed: true },
        loadComponent: () => import('./features/almoxarifado/solicitacoes-material/solicitacoes-material.component').then(m => m.SolicitacoesMaterialComponent)
      },
      {
        path: 'solicitacoes/kanban',
        data: { permission: 'estoque:solicitacao:read', modo: 'kanban', fullBleed: true },
        loadComponent: () => import('./features/almoxarifado/solicitacoes-material/solicitacoes-material.component').then(m => m.SolicitacoesMaterialComponent)
      },
      {
        path: 'solicitacoes/calendario',
        data: { permission: 'estoque:solicitacao:read', modo: 'calendario', fullBleed: true },
        loadComponent: () => import('./features/almoxarifado/solicitacoes-material/solicitacoes-material.component').then(m => m.SolicitacoesMaterialComponent)
      },
      {
        path: 'solicitacoes/nova',
        data: { permission: 'estoque:solicitacao:create' },
        loadComponent: () => import('./features/almoxarifado/solicitacoes-material/solicitacao-material-form/solicitacao-material-form.component').then(m => m.SolicitacaoMaterialFormComponent)
      },
      {
        path: 'solicitacoes-tecnicas',
        data: { permission: 'estoque:solicitacao:read' },
        loadComponent: () => import('./features/almoxarifado/solicitacoes-material/solicitacoes-material-tecnicas.component').then(m => m.SolicitacoesMaterialTecnicasComponent)
      },
      {
        path: 'solicitacoes-tecnicas/kanban',
        data: { permission: 'estoque:solicitacao:read', modo: 'kanban' },
        loadComponent: () => import('./features/almoxarifado/solicitacoes-material/solicitacoes-material-tecnicas.component').then(m => m.SolicitacoesMaterialTecnicasComponent)
      },
      {
        path: 'solicitacoes-tecnicas/nova',
        data: { permission: 'estoque:solicitacao:create' },
        loadComponent: () => import('./features/almoxarifado/solicitacoes-material/solicitacao-tecnica-form/solicitacao-tecnica-form.component').then(m => m.SolicitacaoTecnicaFormComponent)
      },
      {
        path: 'solicitacoes-tecnicas/devolucao',
        data: { permission: 'estoque:solicitacao:create', modo: 'devolucao' },
        loadComponent: () => import('./features/almoxarifado/solicitacoes-material/solicitacao-tecnica-form/solicitacao-tecnica-form.component').then(m => m.SolicitacaoTecnicaFormComponent)
      },
      {
        path: 'solicitacoes-tecnicas/:id',
        data: { permission: 'estoque:solicitacao:read', fullscreen: true, reportNavigator: 'solicitacao-tecnica' },
        loadComponent: () => import('./features/almoxarifado/solicitacoes-material/solicitacao-tecnica-detalhe/solicitacao-tecnica-detalhe.component').then(m => m.SolicitacaoTecnicaDetalheComponent)
      },
      {
        path: 'solicitacoes/tecnicas/:id',
        redirectTo: 'solicitacoes-tecnicas/:id',
        pathMatch: 'full'
      },
      {
        path: 'saldos',
        data: { permission: 'estoque:saldo:read' },
        loadComponent: () => import('./features/almoxarifado/saldos/saldos.component').then(m => m.SaldosComponent)
      },
      {
        path: 'saldos/relatorio',
        data: { permission: 'estoque:saldo:read', fullscreen: true, reportNavigator: 'saldo' },
        loadComponent: () => import('./features/almoxarifado/saldos/relatorio-saldo/relatorio-saldo.component').then(m => m.RelatorioSaldoComponent)
      },
      {
        path: 'solicitacoes/:id',
        data: { permission: 'estoque:solicitacao:read', fullscreen: true, reportNavigator: 'solicitacao-material' },
        loadComponent: () => import('./features/almoxarifado/solicitacoes-material/relatorio-solicitacao-material/relatorio-solicitacao-material.component').then(m => m.RelatorioSolicitacaoMaterialComponent)
      },
      {
        path: 'dashboard/solicitacoes',
        data: { permission: 'estoque:solicitacao:read', fullBleed: true, modoPainel: 'GERAL' },
        loadComponent: () => import('./features/almoxarifado/dashboard/solicitacoes-dashboard/solicitacoes-dashboard.component').then(m => m.SolicitacoesDashboardComponent)
      },
      {
        path: 'dashboard/solicitacoes/comercial',
        data: { permission: 'estoque:solicitacao:read', fullBleed: true, modoPainel: 'COMERCIAL' },
        loadComponent: () => import('./features/almoxarifado/dashboard/solicitacoes-dashboard/solicitacoes-dashboard.component').then(m => m.SolicitacoesDashboardComponent)
      },
      {
        path: 'dashboard/solicitacoes/tecnico',
        data: { permission: 'estoque:solicitacao:read', fullBleed: true, modoPainel: 'TECNICO' },
        loadComponent: () => import('./features/almoxarifado/dashboard/solicitacoes-dashboard/solicitacoes-dashboard.component').then(m => m.SolicitacoesDashboardComponent)
      },
      {
        path: 'movimentacoes',
        data: { permission: 'estoque:movimentacao:read' },
        loadComponent: () => import('./features/almoxarifado/movimentacoes-estoque/movimentacoes-estoque.component').then(m => m.MovimentacoesEstoqueComponent)
      },
      {
        path: 'movimentacoes/:id',
        data: { permission: 'estoque:movimentacao:read', fullscreen: true, reportNavigator: 'movimentacao' },
        loadComponent: () => import('./features/almoxarifado/movimentacoes-estoque/relatorio-movimentacao-estoque/relatorio-movimentacao-estoque.component').then(m => m.RelatorioMovimentacaoEstoqueComponent)
      }
    ]
  },
  {
    path: 'execucao',
    canActivate: [authGuard],
    children: [
      {
        path: 'comercial',
        data: { permission: 'execucao:comercial:read' },
        loadComponent: () => import('./features/execucao/execucao-list/execucao-list.component').then(m => m.ExecucaoListComponent)
      },
      {
        path: 'turnos',
        data: { permission: 'execucao:comercial:read' },
        loadComponent: () => import('./features/execucao/turno-list/turno-list.component').then(m => m.TurnoListComponent)
      },
      {
        path: 'dashboard-operacional',
        data: { permission: 'execucao:comercial:read', fullBleed: true },
        loadComponent: () => import('./features/execucao/dashboards/operacional/dashboard-operacional.component').then(m => m.DashboardOperacionalComponent)
      },
      {
        path: 'dashboard-turnos',
        data: { permission: 'execucao:comercial:read', fullBleed: true },
        loadComponent: () => import('./features/execucao/dashboards/turnos/dashboard-turnos.component').then(m => m.DashboardTurnosComponent)
      },
      {
        path: 'turnos/:id/detalhamento',
        data: { permission: 'execucao:comercial:read', fullscreen: true, reportNavigator: 'turno', backFallback: '/execucao/turnos' },
        loadComponent: () => import('./features/execucao/turno-detalhamento/turno-detalhamento.component').then(m => m.TurnoDetalhamentoComponent)
      },
      {
        path: 'comercial/:id',
        data: { permission: 'execucao:comercial:read', fullscreen: true, reportNavigator: 'execucao-comercial', backFallback: '/execucao/comercial' },
        loadComponent: () => import('./features/execucao/execucao-relatorio/execucao-relatorio.component').then(m => m.ExecucaoRelatorioComponent)
      }
    ]
  },
  {
    path: 'frota',
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'veiculos', pathMatch: 'full' },
      {
        path: 'veiculos',
        data: { permission: 'frota:veiculo:read' },
        loadComponent: () => import('./features/frota/veiculos/frota-veiculos.component').then(m => m.FrotaVeiculosComponent)
      },
      {
        path: 'veiculos/:id',
        data: { permission: 'frota:veiculo:read' },
        loadComponent: () => import('./features/frota/veiculos/frota-veiculo-detalhe.component').then(m => m.FrotaVeiculoDetalheComponent)
      },
      {
        path: 'implementos',
        data: { permission: 'frota:veiculo:read' },
        loadComponent: () => import('./features/frota/implementos/frota-implementos.component').then(m => m.FrotaImplementosComponent)
      },
      {
        path: 'condutores',
        data: { permission: 'frota:condutor:read' },
        loadComponent: () => import('./features/frota/condutores/frota-condutores.component').then(m => m.FrotaCondutoresComponent)
      },
      {
        path: 'documentos',
        data: { permission: 'frota:documento:read' },
        loadComponent: () => import('./features/frota/documentos/frota-documentos.component').then(m => m.FrotaDocumentosComponent)
      },
      {
        path: 'odometro',
        data: { permission: 'frota:veiculo:read' },
        loadComponent: () => import('./features/frota/odometro/frota-odometro.component').then(m => m.FrotaOdometroComponent)
      },
      {
        path: 'configuracoes',
        data: { permission: 'frota:veiculo:read' },
        loadComponent: () => import('./features/frota/configuracoes/frota-configuracoes.component').then(m => m.FrotaConfiguracoesComponent)
      }
    ]
  },
  {
    path: 'obras',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        data: { permission: 'obras:obra:read' },
        loadComponent: () => import('./features/obras/lista/obras-lista.component').then(m => m.ObrasListComponent)
      },
      {
        path: 'nova',
        data: { permission: 'obras:obra:write' },
        loadComponent: () => import('./features/obras/form/obra-form.component').then(m => m.ObraFormComponent)
      },
      {
        path: 'novo',
        redirectTo: 'nova',
        pathMatch: 'full'
      },
      {
        path: 'cadastros/status',
        data: { permission: 'obras:cadastros:read' },
        loadComponent: () => import('./features/obras/cadastros/status-obras/status-obras.component').then(m => m.StatusObrasComponent)
      },
      {
        path: 'cadastros/agentes',
        data: { permission: 'obras:cadastros:read' },
        loadComponent: () => import('./features/obras/cadastros/agentes-responsaveis/agentes-responsaveis.component').then(m => m.AgentesResponsaveisComponent)
      },
      {
        path: 'cadastros/tipos-anexos',
        data: { permission: 'obras:cadastros:read' },
        loadComponent: () => import('./features/obras/cadastros/tipos-anexos/tipos-anexos.component').then(m => m.TiposAnexosComponent)
      },
      {
        path: ':id/editar',
        data: { permission: 'obras:obra:write' },
        loadComponent: () => import('./features/obras/form/obra-form.component').then(m => m.ObraFormComponent)
      },
      {
        path: ':id',
        data: { permission: 'obras:obra:read', fullscreen: true, reportNavigator: 'obra' },
        loadComponent: () => import('./features/obras/detalhe/obra-detalhe.component').then(m => m.ObraDetalheComponent)
      }
    ]
  },
  {
    path: 'sst',
    children: [
      {
        path: 'governanca',
        redirectTo: 'governanca/painel',
        pathMatch: 'full'
      },
      {
        path: 'governanca/composicoes/novo',
        canActivate: [authGuard],
        data: { permission: 'sst:governanca:write' },
        loadComponent: () => import('./features/sst/governanca/governanca-composicao-form.component').then(m => m.GovernancaComposicaoFormComponent)
      },
      {
        path: 'governanca/composicoes/:id/editar',
        canActivate: [authGuard],
        data: { permission: 'sst:governanca:write' },
        loadComponent: () => import('./features/sst/governanca/governanca-composicao-form.component').then(m => m.GovernancaComposicaoFormComponent)
      },
      ...(['reunioes', 'documentos'] as const).map(secao => ({
        path: `governanca/${secao}/novo`,
        canActivate: [authGuard],
        data: {
          permission: 'sst:governanca:write',
          cadastro: secao === 'reunioes' ? 'reuniao' : 'documento'
        },
        loadComponent: () => import('./features/sst/governanca/governanca-cadastro.component').then(m => m.GovernancaCadastroComponent)
      })),
      {
        path: 'governanca/reunioes/:id',
        canActivate: [authGuard],
        data: { permission: 'sst:governanca:read' },
        loadComponent: () => import('./features/sst/governanca/reuniao-governanca.component').then(m => m.ReuniaoGovernancaComponent)
      },
      ...(['painel', 'composicoes', 'reunioes', 'documentos', 'treinamentos', 'acoes'] as const).map(secao => ({
        path: `governanca/${secao}`,
        canActivate: [authGuard],
        data: { permission: 'sst:governanca:read', secao },
        loadComponent: () => import('./features/sst/governanca/governanca.component').then(m => m.GovernancaSstComponent)
      })),
      {
        path: 'governanca/cipa/reunioes/:id',
        canActivate: [authGuard],
        data: { permission: 'sst:governanca:read', tipoCorpo: 'CIPA' },
        loadComponent: () => import('./features/sst/governanca/reuniao-governanca.component').then(m => m.ReuniaoGovernancaComponent)
      },
      {
        path: 'governanca/brigada/reunioes/:id',
        canActivate: [authGuard],
        data: { permission: 'sst:governanca:read', tipoCorpo: 'BRIGADA' },
        loadComponent: () => import('./features/sst/governanca/reuniao-governanca.component').then(m => m.ReuniaoGovernancaComponent)
      },
      ...(['painel', 'composicoes', 'reunioes', 'documentos', 'treinamentos', 'acoes'] as const).flatMap(secao => [
        {
          path: `governanca/cipa/${secao}`,
          canActivate: [authGuard],
          data: { permission: 'sst:governanca:read', tipoCorpo: 'CIPA', secao },
          loadComponent: () => import('./features/sst/governanca/governanca.component').then(m => m.GovernancaSstComponent)
        },
        {
          path: `governanca/brigada/${secao}`,
          canActivate: [authGuard],
          data: { permission: 'sst:governanca:read', tipoCorpo: 'BRIGADA', secao },
          loadComponent: () => import('./features/sst/governanca/governanca.component').then(m => m.GovernancaSstComponent)
        }
      ]),
      {
        path: 'governanca/configuracoes',
        canActivate: [authGuard],
        data: { permission: 'sst:governanca:write' },
        loadComponent: () => import('./features/sst/governanca/governanca-configuracoes.component').then(m => m.GovernancaConfiguracoesComponent)
      },
      {
        path: 'formularios',
        canActivate: [authGuard],
        data: { permission: 'sst:formulario:read' },
        loadComponent: () => import('./features/sst/formularios/formularios.component').then(m => m.FormulariosComponent)
      },
      {
        path: 'formularios/novo',
        canActivate: [authGuard],
        data: { permission: 'sst:formulario:write' },
        loadComponent: () => import('./features/sst/formularios/formulario-builder.component').then(m => m.FormularioBuilderComponent)
      },
      {
        path: 'formularios/:id',
        canActivate: [authGuard],
        data: { permission: 'sst:formulario:write' },
        loadComponent: () => import('./features/sst/formularios/formulario-builder.component').then(m => m.FormularioBuilderComponent)
      },
      {
        path: 'desvios',
        canActivate: [authGuard],
        data: { permission: 'sst:inspecao:read' },
        loadComponent: () => import('./features/sst/desvios/desvios.component').then(m => m.DesviosComponent)
      },
      {
        path: 'inspecoes',
        canActivate: [authGuard],
        data: { permission: 'sst:inspecao:read' },
        loadComponent: () => import('./features/sst/inspecoes/inspecoes.component').then(m => m.InspecoesComponent)
      },
      {
        path: 'inspecoes/nova',
        canActivate: [authGuard],
        data: { permission: 'sst:inspecao:write' },
        loadComponent: () => import('./features/sst/inspecoes/inspecao-form.component').then(m => m.InspecaoFormComponent)
      },
      {
        path: 'meus-desvios',
        canActivate: [authGuard],
        data: { permission: 'sst:inspecao:read' },
        loadComponent: () => import('./features/sst/meus-desvios/meus-desvios.component').then(m => m.MeusDesviosComponent)
      },
      {
        path: 'inspecoes/:id/editar',
        canActivate: [authGuard],
        data: { permission: 'sst:inspecao:write' },
        loadComponent: () => import('./features/sst/inspecoes/inspecao-form.component').then(m => m.InspecaoFormComponent)
      },
      {
        path: 'inspecoes/:id',
        canActivate: [authGuard],
        data: { permission: 'sst:inspecao:read', fullscreen: true, reportNavigator: 'inspecao-sst' },
        loadComponent: () => import('./features/sst/inspecoes/inspecao-detalhe.component').then(m => m.InspecaoDetalheComponent)
      },
      {
        path: 'metas-inspecao',
        canActivate: [authGuard],
        data: { permission: 'sst:meta:read' },
        loadComponent: () => import('./features/sst/metas-inspecoes/metas-inspecao.component').then(m => m.MetasInspecaoComponent)
      },
      {
        path: 'metas-inspecao/acompanhamento',
        canActivate: [authGuard],
        data: { permission: 'sst:meta:read' },
        loadComponent: () => import('./features/sst/metas-inspecoes/acompanhamento-metas.component').then(m => m.AcompanhamentoMetasComponent)
      },
      {
        path: 'dashboards/inspecoes',
        canActivate: [authGuard],
        data: { permission: 'sst:dashboard:read', fullBleed: true },
        loadComponent: () => import('./features/sst/dashboards/dashboard-inspecoes.component').then(m => m.DashboardInspecoesComponent)
      },
      {
        path: 'dashboards/desvios',
        canActivate: [authGuard],
        data: { permission: 'sst:dashboard:read', fullBleed: true },
        loadComponent: () => import('./features/sst/dashboards/dashboard-desvios.component').then(m => m.DashboardDesviosComponent)
      },
      {
        path: 'dashboards/eventos',
        canActivate: [authGuard],
        data: { permission: 'sst:dashboard:read', fullBleed: true },
        loadComponent: () => import('./features/sst/dashboards/dashboard-eventos.component').then(m => m.DashboardEventosComponent)
      },
      // Eventos de segurança (acidentalidade). 'novo' precisa vir antes de ':id' —
      // invertida, a rota de detalhe capturaria a palavra 'novo' como identificador.
      {
        path: 'eventos',
        canActivate: [authGuard],
        data: { permission: 'sst:evento:read' },
        loadComponent: () => import('./features/sst/eventos/eventos.component').then(m => m.EventosComponent)
      },
      {
        path: 'eventos/novo',
        canActivate: [authGuard],
        data: { permission: 'sst:evento:write' },
        loadComponent: () => import('./features/sst/eventos/evento-form.component').then(m => m.EventoFormComponent)
      },
      {
        path: 'eventos/:id',
        canActivate: [authGuard],
        data: { permission: 'sst:evento:read', fullscreen: true, reportNavigator: 'evento-sst' },
        loadComponent: () => import('./features/sst/eventos/evento-detalhe.component').then(m => m.EventoDetalheComponent)
      },
      {
        path: 'eventos/:id/editar',
        canActivate: [authGuard],
        data: { permission: 'sst:evento:write' },
        loadComponent: () => import('./features/sst/eventos/evento-form.component').then(m => m.EventoFormComponent)
      },
      {
        path: 'atividades',
        canActivate: [authGuard],
        data: { permission: 'sst:catalogo:read' },
        loadComponent: () => import('./features/sst/cadastros/atividades-sst.component').then(m => m.AtividadesSstComponent)
      },
      {
        path: 'tipos-inspecao',
        canActivate: [authGuard],
        data: { permission: 'sst:catalogo:read' },
        loadComponent: () => import('./features/sst/cadastros/tipos-inspecao.component').then(m => m.TiposInspecaoComponent)
      },
      {
        path: 'classificacoes-eventos',
        canActivate: [authGuard],
        data: { permission: 'sst:catalogo:read', tipoCatalogo: 'CLASSIFICACAO_EVENTO' },
        loadComponent: () => import('./features/sst/cadastros/catalogos-sst.component').then(m => m.CatalogosSstComponent)
      },
      {
        path: 'locais-lesao',
        canActivate: [authGuard],
        data: { permission: 'sst:catalogo:read', tipoCatalogo: 'LOCAL_LESAO' },
        loadComponent: () => import('./features/sst/cadastros/catalogos-sst.component').then(m => m.CatalogosSstComponent)
      },
      {
        path: 'naturezas-lesao',
        canActivate: [authGuard],
        data: { permission: 'sst:catalogo:read', tipoCatalogo: 'NATUREZA_LESAO' },
        loadComponent: () => import('./features/sst/cadastros/catalogos-sst.component').then(m => m.CatalogosSstComponent)
      },
      {
        path: 'agentes-causadores',
        canActivate: [authGuard],
        data: { permission: 'sst:catalogo:read', tipoCatalogo: 'AGENTE_CAUSADOR' },
        loadComponent: () => import('./features/sst/cadastros/catalogos-sst.component').then(m => m.CatalogosSstComponent)
      },
      {
        path: 'catalogos',
        redirectTo: 'classificacoes-eventos',
        pathMatch: 'full'
      }
    ]
  },
  {
    path: 'dp',
    canActivate: [authGuard],
    children: [
      {
        path: 'descontos/lancamento',
        data: { permission: 'desconto:read' },
        loadComponent: () => import('./features/dp/descontos/lancamento/descontos-lancamento.component').then(m => m.DescontosLancamentoComponent)
      },
      {
        path: 'descontos/novo',
        data: { permission: 'desconto:solicitar', fullscreen: true },
        loadComponent: () => import('./features/dp/descontos/desconto-form/desconto-form.component').then(m => m.DescontoFormComponent)
      },
      // Estes caminhos fixos têm que vir antes de `descontos/:id` — na ordem inversa,
      // "dashboard" seria lido como o identificador de um termo e a tela abriria em erro.
      {
        path: 'descontos/dashboard',
        data: { permission: 'desconto:dashboard:read', fullBleed: true },
        loadComponent: () => import('./features/dp/descontos/dashboard/dashboard-descontos.component').then(m => m.DashboardDescontosComponent)
      },
      // Filas de trabalho: a mesma consulta, recortada pelo estágio que ainda exige ação.
      {
        path: 'descontos/validacao',
        data: { permission: 'desconto:read', modo: 'validar' },
        loadComponent: () => import('./features/dp/descontos/lancamento/descontos-lancamento.component').then(m => m.DescontosLancamentoComponent)
      },
      {
        path: 'descontos/baixa',
        data: { permission: 'desconto:read', modo: 'baixar' },
        loadComponent: () => import('./features/dp/descontos/lancamento/descontos-lancamento.component').then(m => m.DescontosLancamentoComponent)
      },
      {
        path: 'descontos/:id',
        data: { permission: 'desconto:read', fullscreen: true, reportNavigator: 'desconto' },
        loadComponent: () => import('./features/dp/descontos/relatorio-desconto/relatorio-desconto.component').then(m => m.RelatorioDescontoComponent)
      }
    ]
  },
  {
    path: 'chamados',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: '', redirectTo: 'meus', pathMatch: 'full' },
      {
        path: 'meus',
        data: { permission: 'chamado:read', modo: 'solicitante' },
        loadComponent: () => import('./features/chamados/lista/chamados-lista.component').then(m => m.ChamadosListaComponent)
      },
      {
        path: 'novo',
        data: { permission: 'chamado:write' },
        loadComponent: () => import('./features/chamados/novo/chamado-novo.component').then(m => m.ChamadoNovoComponent)
      },
      {
        path: 'meus/:id',
        data: { permission: 'chamado:read', modo: 'solicitante' },
        loadComponent: () => import('./features/chamados/detalhe/chamado-detalhe.component').then(m => m.ChamadoDetalheComponent)
      },
      {
        path: 'fila',
        data: { permission: 'chamado:manage', modo: 'central' },
        loadComponent: () => import('./features/chamados/lista/chamados-lista.component').then(m => m.ChamadosListaComponent)
      },
      {
        path: 'fila/:id',
        data: { permission: 'chamado:manage', modo: 'central' },
        loadComponent: () => import('./features/chamados/detalhe/chamado-detalhe.component').then(m => m.ChamadoDetalheComponent)
      },
      {
        path: 'dashboard',
        data: { permission: 'chamado:manage', fullBleed: true },
        loadComponent: () => import('./features/chamados/dashboard/chamados-dashboard.component').then(m => m.ChamadosDashboardComponent)
      },
      {
        path: 'admin/topicos',
        data: { permission: 'chamado:manage' },
        loadComponent: () => import('./features/chamados/admin/topicos/chamados-topicos.component').then(m => m.ChamadosTopicosComponent)
      },
      {
        path: 'admin/equipes',
        data: { permission: 'chamado:manage' },
        loadComponent: () => import('./features/chamados/admin/equipes/chamados-equipes.component').then(m => m.ChamadosEquipesComponent)
      }
    ]
  },
  {
    path: 'financeiro',
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'compras', pathMatch: 'full' },
      {
        path: 'compras',
        data: { permission: 'financeiro:compra:read', tipo: 'COMPRA', modo: 'minhas' },
        loadComponent: () => import('./features/financeiro/solicitacoes/solicitacoes-financeiras.component').then(m => m.SolicitacoesFinanceirasComponent)
      },
      {
        path: 'pagamentos',
        data: { permission: 'financeiro:pagamento:read', tipo: 'PAGAMENTO', modo: 'minhas' },
        loadComponent: () => import('./features/financeiro/solicitacoes/solicitacoes-financeiras.component').then(m => m.SolicitacoesFinanceirasComponent)
      },
      {
        path: 'aprovacoes/compras',
        data: { permission: 'financeiro:compra:aprovar', tipo: 'COMPRA', modo: 'aprovacao' },
        loadComponent: () => import('./features/financeiro/solicitacoes/solicitacoes-financeiras.component').then(m => m.SolicitacoesFinanceirasComponent)
      },
      {
        path: 'aprovacoes/pagamentos',
        data: { permission: 'financeiro:pagamento:aprovar', tipo: 'PAGAMENTO', modo: 'aprovacao' },
        loadComponent: () => import('./features/financeiro/solicitacoes/solicitacoes-financeiras.component').then(m => m.SolicitacoesFinanceirasComponent)
      },
      {
        path: 'compras/nova',
        data: { permission: 'financeiro:compra:write', tipo: 'COMPRA' },
        loadComponent: () => import('./features/financeiro/solicitacoes/solicitacao-financeira-form.component').then(m => m.SolicitacaoFinanceiraFormComponent)
      },
      {
        path: 'pagamentos/nova',
        data: { permission: 'financeiro:pagamento:write', tipo: 'PAGAMENTO' },
        loadComponent: () => import('./features/financeiro/solicitacoes/solicitacao-financeira-form.component').then(m => m.SolicitacaoFinanceiraFormComponent)
      },
      {
        path: 'compras/:id/editar',
        data: { permission: 'financeiro:compra:write', tipo: 'COMPRA' },
        loadComponent: () => import('./features/financeiro/solicitacoes/solicitacao-financeira-form.component').then(m => m.SolicitacaoFinanceiraFormComponent)
      },
      {
        path: 'pagamentos/:id/editar',
        data: { permission: 'financeiro:pagamento:write', tipo: 'PAGAMENTO' },
        loadComponent: () => import('./features/financeiro/solicitacoes/solicitacao-financeira-form.component').then(m => m.SolicitacaoFinanceiraFormComponent)
      },
      {
        path: 'compras/:id',
        data: { permission: 'financeiro:compra:read', tipo: 'COMPRA', fullscreen: true, reportNavigator: 'financeiro' },
        loadComponent: () => import('./features/financeiro/solicitacoes/solicitacao-financeira-detalhe.component').then(m => m.SolicitacaoFinanceiraDetalheComponent)
      },
      {
        path: 'pagamentos/:id',
        data: { permission: 'financeiro:pagamento:read', tipo: 'PAGAMENTO', fullscreen: true, reportNavigator: 'financeiro' },
        loadComponent: () => import('./features/financeiro/solicitacoes/solicitacao-financeira-detalhe.component').then(m => m.SolicitacaoFinanceiraDetalheComponent)
      },
      {
        path: 'gestao/solicitacoes',
        data: { permission: 'financeiro:compra:read', tipo: 'AMBOS', modo: 'todas' },
        loadComponent: () => import('./features/financeiro/solicitacoes/solicitacoes-financeiras.component').then(m => m.SolicitacoesFinanceirasComponent)
      },
      {
        path: 'documentos',
        data: { permission: 'financeiro:documento:read' },
        loadComponent: () => import('./features/financeiro/documentos/documentos-financeiros.component').then(m => m.DocumentosFinanceirosComponent)
      },
      {
        path: 'gestao/solicitacoes',
        data: { permission: 'financeiro:compra:read', tipo: 'AMBOS', modo: 'todas' },
        loadComponent: () => import('./features/financeiro/solicitacoes/solicitacoes-financeiras.component').then(m => m.SolicitacoesFinanceirasComponent)
      },
      {
        path: 'dashboard',
        data: { permission: 'financeiro:dashboard:read', fullBleed: true },
        loadComponent: () => import('./features/financeiro/dashboard/dashboard-financeiro.component').then(m => m.DashboardFinanceiroComponent)
      },
      {
        path: 'admin/fornecedores',
        data: { permission: 'financeiro:fornecedor:read' },
        loadComponent: () => import('./features/financeiro/fornecedores/fornecedores.component').then(m => m.FornecedoresComponent)
      },
      {
        path: 'admin/formularios',
        data: { permission: 'financeiro:formulario:read' },
        loadComponent: () => import('./features/financeiro/formularios/formularios-financeiros.component').then(m => m.FormulariosFinanceirosComponent)
      },
      {
        path: 'admin/formularios/:id',
        data: { permission: 'financeiro:formulario:write' },
        loadComponent: () => import('./features/financeiro/formularios/formulario-financeiro-builder.component').then(m => m.FormularioFinanceiroBuilderComponent)
      }
    ]
  },
  {
    path: 'notificacoes',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: '', pathMatch: 'full', loadComponent: () => import('./features/notificacoes/minhas-notificacoes/minhas-notificacoes.component').then(m => m.MinhasNotificacoesComponent) },
      {
        // Caixa de entrada e preferências não exigem permissão: o escopo é sempre o próprio
        // usuário, resolvido no servidor.
        path: 'preferencias',
        loadComponent: () => import('./features/notificacoes/preferencias/preferencias-notificacao.component').then(m => m.PreferenciasNotificacaoComponent)
      },
      // O mural mora em /mural (é o destino que o backend renderiza nos deeplinks de
      // comunicado). Mantido aqui só para não quebrar links antigos.
      { path: 'mural', redirectTo: '/mural', pathMatch: 'full' },
      {
        path: 'admin/comunicados',
        data: { permission: 'notificacao:comunicado:manage' },
        loadComponent: () => import('./features/notificacoes/admin/comunicados/comunicados.component').then(m => m.ComunicadosComponent)
      },
      {
        path: 'admin/comunicados/novo',
        data: { permission: 'notificacao:comunicado:manage' },
        loadComponent: () => import('./features/notificacoes/admin/comunicados/comunicado-form.component').then(m => m.ComunicadoFormComponent)
      },
      {
        path: 'admin/comunicados/:id/editar',
        data: { permission: 'notificacao:comunicado:manage' },
        loadComponent: () => import('./features/notificacoes/admin/comunicados/comunicado-form.component').then(m => m.ComunicadoFormComponent)
      },
      // As regras de notificação e as políticas de roteamento viraram automações (V175).
      { path: 'admin/regras', redirectTo: 'automacoes', pathMatch: 'full' },
      { path: 'admin/regras/nova', redirectTo: 'automacoes', pathMatch: 'full' },
      { path: 'admin/regras/:id/editar', redirectTo: 'automacoes', pathMatch: 'full' },
      { path: 'admin/politicas-roteamento', redirectTo: 'automacoes', pathMatch: 'full' },
      { path: 'admin/politicas-roteamento/nova', redirectTo: 'automacoes', pathMatch: 'full' },
      { path: 'admin/politicas-roteamento/:id/editar', redirectTo: 'automacoes', pathMatch: 'full' },
      {
        path: 'automacoes',
        data: { permission: 'automacao:read' },
        loadComponent: () => import('./features/notificacoes/automacoes/automacoes-lista.component').then(m => m.AutomacoesListaComponent)
      },
      {
        // Destino do aviso de aprovação: quem aprova não precisa administrar automações.
        path: 'automacoes/aprovacoes/:id',
        loadComponent: () => import('./features/notificacoes/automacoes/aprovacao-automacao.component').then(m => m.AprovacaoAutomacaoComponent)
      },
      {
        path: 'automacoes/:id',
        data: { permission: 'automacao:read' },
        loadComponent: () => import('./features/notificacoes/automacoes/automacao-detalhe.component').then(m => m.AutomacaoDetalheComponent)
      },
      {
        path: 'automacoes/:id/editar',
        data: { permission: 'automacao:manage', fullscreen: true },
        loadComponent: () => import('./features/notificacoes/automacoes/designer/automacao-designer.component').then(m => m.AutomacaoDesignerComponent)
      },
      {
        path: 'automacoes/:id/execucoes/:execucaoId',
        data: { permission: 'automacao:read', fullscreen: true },
        loadComponent: () => import('./features/notificacoes/automacoes/automacao-execucao.component').then(m => m.AutomacaoExecucaoComponent)
      }
    ]
  },
  {
    // Destino renderizado pelo backend nos deeplinks de comunicado: /mural/{idComunicado}.
    path: 'mural',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/notificacoes/mural/mural.component').then(m => m.MuralComponent)
      },
      {
        path: ':id',
        loadComponent: () => import('./features/notificacoes/mural/mural.component').then(m => m.MuralComponent)
      }
    ]
  },
  {
    path: '',
    redirectTo: 'home',
    pathMatch: 'full'
  },
  {
    // Deeplink para uma rota que não existe (mais) não deve estourar no console nem deixar o
    // usuário parado: cai no início.
    path: '**',
    redirectTo: 'home'
  }
];
