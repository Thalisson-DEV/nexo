import { Component, computed, inject, signal, effect, untracked } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FooterComponent } from './shared/components/footer/footer.component';
import { HeaderComponent } from './shared/components/header/header.component';
import { SidebarComponent } from './shared/components/sidebar/sidebar.component';
import { MuralHostComponent } from './shared/components/mural-host/mural-host.component';
import { AtualizacaoDisponivelComponent } from './shared/components/atualizacao-disponivel/atualizacao-disponivel.component';
import { SessaoReautenticacaoComponent } from './shared/components/sessao-reautenticacao/sessao-reautenticacao.component';
import { AuthService } from './core/services/auth.service';
import { ThemeService } from './core/services/theme.service';
import { MenuItem } from 'primeng/api';
import { removerModulosOcultos } from './core/white-label/modulos-ocultos';
import { filter } from 'rxjs';

import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';
import { CesReportNavigatorComponent } from './design-system/patterns/report-navigator';
import { ReportNavigatorRegistryService } from './core/services/report-navigator-registry.service';
import { ScrollMemoryDirective } from './shared/directives/scroll-memory.directive';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    HeaderComponent,
    SidebarComponent,
    ToastModule,
    ConfirmDialogModule,
    MuralHostComponent,
    AtualizacaoDisponivelComponent,
    SessaoReautenticacaoComponent,
    CesReportNavigatorComponent,
    ScrollMemoryDirective
  ],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  protected readonly router = inject(Router);
  private readonly navigatorRegistry = inject(ReportNavigatorRegistryService);

  protected readonly isMobile = signal(window.innerWidth < 1024);
  protected readonly menuVisible = signal(window.innerWidth >= 1024);
  protected readonly hideLayout = signal(false);
  protected readonly layoutWorkspace = signal(false);
  /** Rotas que gerenciam a própria rolagem e margens (workspace do Copiloto e dashboards com painel lateral). */
  protected readonly layoutFullBleed = signal(false);
  private menuAntesWorkspace: boolean | null = null;
  protected readonly reportNavigatorSource = signal<string | null>(null);
  protected readonly reportNavigatorOpen = signal(false);
  protected readonly currentRouteId = signal<string | number | null>(null);

  protected readonly activeDataSource = computed(() => {
    const src = this.reportNavigatorSource();
    return src ? this.navigatorRegistry.resolve(src) : null;
  });

  /**
   * Entradas de menu que dependem de permissão. O `authGuard` continua sendo quem
   * impõe o acesso na navegação — aqui só escondemos o que seria inútil clicar.
   * (O restante do menu ainda é estático — ver CLAUDE.md §9 "Menu Dinâmico".)
   */
  private readonly permissaoPorRota: Record<string, string> = {
    '/copiloto': 'assistente:chat:use',
    '/sst/governanca/painel': 'sst:governanca:read',
    '/sst/governanca/composicoes': 'sst:governanca:read',
    '/sst/governanca/reunioes': 'sst:governanca:read',
    '/sst/governanca/documentos': 'sst:governanca:read',
    '/sst/governanca/treinamentos': 'sst:governanca:read',
    '/sst/governanca/acoes': 'sst:governanca:read',
    '/sst/governanca/cipa/painel': 'sst:governanca:read',
    '/sst/governanca/cipa/composicoes': 'sst:governanca:read',
    '/sst/governanca/cipa/reunioes': 'sst:governanca:read',
    '/sst/governanca/cipa/documentos': 'sst:governanca:read',
    '/sst/governanca/cipa/treinamentos': 'sst:governanca:read',
    '/sst/governanca/cipa/acoes': 'sst:governanca:read',
    '/sst/governanca/brigada/painel': 'sst:governanca:read',
    '/sst/governanca/brigada/composicoes': 'sst:governanca:read',
    '/sst/governanca/brigada/reunioes': 'sst:governanca:read',
    '/sst/governanca/brigada/documentos': 'sst:governanca:read',
    '/sst/governanca/brigada/treinamentos': 'sst:governanca:read',
    '/sst/governanca/brigada/acoes': 'sst:governanca:read',
    '/sst/governanca/configuracoes': 'sst:governanca:write',
    '/sst/metas-inspecao': 'sst:meta:write',
    '/sst/metas-inspecao/acompanhamento': 'sst:meta:read',
    '/notificacoes/admin/comunicados': 'notificacao:comunicado:manage',
    '/notificacoes/automacoes': 'automacao:read',
    '/admin/cadastros/coordenadores': 'rh:coordenador:read',
    '/admin/cadastros/supervisores': 'rh:supervisor:read',
    '/rh/coordenadores': 'rh:coordenador:read',
    '/rh/supervisores': 'rh:supervisor:read',
    '/obras': 'obras:obra:read',
    '/obras/nova': 'obras:obra:write',
    '/obras/cadastros/status': 'obras:cadastros:read',
    '/obras/cadastros/agentes': 'obras:cadastros:read',
    '/obras/cadastros/tipos-anexos': 'obras:cadastros:read',
    '/almoxarifado/solicitacoes': 'estoque:solicitacao:read',
    '/almoxarifado/solicitacoes/kanban': 'estoque:solicitacao:read',
    '/almoxarifado/solicitacoes/calendario': 'estoque:solicitacao:read',
    '/almoxarifado/solicitacoes/nova': 'estoque:solicitacao:create',
    '/almoxarifado/solicitacoes-tecnicas': 'estoque:solicitacao:read',
    '/almoxarifado/solicitacoes-tecnicas/kanban': 'estoque:solicitacao:read',
    '/almoxarifado/solicitacoes-tecnicas/nova': 'estoque:solicitacao:create',
    '/almoxarifado/solicitacoes-tecnicas/devolucao': 'estoque:solicitacao:create',
    '/almoxarifado/motivos-pendencia': 'estoque:motivo-rejeicao:read',
    '/almoxarifado/motivos-rejeicao': 'estoque:motivo-rejeicao:read',
    '/almoxarifado/devolucao-comercial': 'estoque:movimentacao:write',
    '/financeiro/compras': 'financeiro:compra:read',
    '/financeiro/compras/nova': 'financeiro:compra:write',
    '/financeiro/pagamentos': 'financeiro:pagamento:read',
    '/financeiro/pagamentos/nova': 'financeiro:pagamento:write',
    '/frota/veiculos': 'frota:veiculo:read',
    '/frota/implementos': 'frota:veiculo:read',
    '/frota/condutores': 'frota:condutor:read',
    '/frota/documentos': 'frota:documento:read',
    '/frota/odometro': 'frota:veiculo:read',
    '/frota/configuracoes': 'frota:veiculo:read',
    '/admin/cadastros/status': 'iam:status:read',
    '/admin/cadastros/tratamento-erros': 'sincronizacao:read',
    '/admin/cadastros/tipos-servico': 'execucao:tipo-servico:read',
  };

  private temPermissao(permissao: string): boolean {
    return this.auth.currentUser()?.permissoes?.includes(permissao) ?? false;
  }

  private aplicarPermissoesNoMenu(items: MenuItem[]): MenuItem[] {
    return items.map((item) => {
      const permissao = typeof item.routerLink === 'string' ? this.permissaoPorRota[item.routerLink] : undefined;
      const filhos = item.items ? this.aplicarPermissoesNoMenu(item.items) : undefined;
      // Um grupo cujos filhos ficaram todos ocultos não deve sobrar vazio no menu.
      const grupoVazio = !!filhos && filhos.length > 0 && filhos.every((filho) => filho.visible === false);

      return {
        ...item,
        ...(permissao ? { visible: this.temPermissao(permissao) } : {}),
        ...(grupoVazio ? { visible: false } : {}),
        ...(filhos ? { items: filhos } : {})
      };
    });
  }

  constructor() {
    // Aplicar preferências do usuário ao carregar ou mudar usuário,
    // preservando a preferência explicitamente salva no navegador pelo usuário
    effect(() => {
      const user = this.auth.currentUser();
      const savedMode = localStorage.getItem('ces_theme_mode');
      const isDark = savedMode ? savedMode === 'dark' : user?.tema === 'dark';
      this.theme.setDarkMode(isDark);

      const savedColor = localStorage.getItem('ces_primary_color');
      const color = savedColor || user?.corPrimaria;
      if (color) this.theme.updatePrimaryColor(color);

      const savedRipple = localStorage.getItem('ces_ripple_enabled');
      const ripple = savedRipple !== null ? savedRipple !== 'false' : (user?.animacoesAtivadas ?? true);
      this.theme.setRipple(ripple);
    });

    // O menu é montado uma vez; ao entrar/sair da sessão as entradas dependentes
    // de permissão precisam ser reavaliadas.
    effect(() => {
      this.auth.currentUser();
      untracked(() => this.menuItems.update((items) => this.aplicarPermissoesNoMenu(items)));
    });

    window.addEventListener('resize', () => {
      const mobile = window.innerWidth < 1024;
      if (this.isMobile() !== mobile) {
        this.isMobile.set(mobile);
        this.menuVisible.set(!mobile && !this.layoutWorkspace());
      }
    });

    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe(() => {
      this.atualizarEstadoRota();
    });

    this.atualizarEstadoRota();
  }

  private atualizarEstadoRota(): void {
    let route = this.router.routerState.root;
    while (route.firstChild) {
      route = route.firstChild;
    }
    const data = route.snapshot.data || {};
    const params = route.snapshot.params || {};
    const queryParams = route.snapshot.queryParams || {};

    const isFullscreen = !!data['fullscreen'];
    const navSource = (data['reportNavigator'] as string) || null;
    const isPrint = queryParams['mode'] === 'print';

    this.hideLayout.set(isFullscreen);
    const workspace = !!data['workspace'];
    if (workspace && !this.layoutWorkspace()) {
      this.menuAntesWorkspace = this.menuVisible();
      this.menuVisible.set(false);
    } else if (!workspace && this.layoutWorkspace() && this.menuAntesWorkspace !== null) {
      this.menuVisible.set(this.isMobile() ? false : this.menuAntesWorkspace);
      this.menuAntesWorkspace = null;
    }
    this.layoutWorkspace.set(workspace);
    this.layoutFullBleed.set(workspace || !!data['fullBleed']);
    this.reportNavigatorSource.set(isPrint ? null : navSource);
    this.currentRouteId.set(params['id'] ?? queryParams['idEstoque'] ?? null);
  }

  protected readonly menuItems = signal<MenuItem[]>(removerModulosOcultos([
    { label: 'Copiloto', icon: 'pi pi-sparkles', routerLink: '/copiloto', badge: 'IA', styleClass: 'ces-sidebar-destaque' },
    // Admin guarda só o que é transversal ao sistema inteiro — quem entra e o que
    // cada um pode. Cadastro de domínio mora no módulo a que pertence: quem cadastra
    // colaborador é o DP, quem cadastra material é o almoxarifado. Ninguém precisa
    // ser administrador para manter o próprio cadastro.
    {
      label: 'Admin',
      icon: 'pi pi-unlock',
      items: [
        {
          label: 'Controle de Acesso',
          icon: 'pi pi-lock',
          items: [
            { label: 'Usuários', icon: 'pi pi-users', routerLink: '/admin/cadastros/usuarios' },
            { label: 'Perfis de Acesso', icon: 'pi pi-id-card', routerLink: '/admin/cadastros/perfis-acesso'},
            { label: 'Permissões', icon: 'pi pi-key', routerLink: '/admin/cadastros/permissoes'},
            { label: 'Sincronismo', icon: 'pi pi-sync', routerLink: '/admin/cadastros/tratamento-erros' }
          ]
        },
        {
          label: 'Sistema',
          icon: 'pi pi-server',
          items: [
            { label: 'Status do Sistema', icon: 'pi pi-server', routerLink: '/admin/cadastros/status'}
          ]
        }
      ]
    },
    {
      label: 'Comunicação',
      icon: 'pi pi-bell',
      items: [
        {
          label: 'Avisos',
          icon: 'pi pi-inbox',
          items: [
            { label: 'Mural de Comunicados', icon: 'pi pi-megaphone', routerLink: '/mural' },
            { label: 'Minhas Notificações', icon: 'pi pi-bell', routerLink: '/notificacoes' }
          ]
        },
        {
          label: 'Configurações',
          icon: 'pi pi-sliders-h',
          items: [
            { label: 'Preferências de Notificação', icon: 'pi pi-sliders-h', routerLink: '/notificacoes/preferencias' }
          ]
        },
        {
          label: 'Cadastros',
          icon: 'pi pi-cog',
          items: [
            { label: 'Comunicados', icon: 'pi pi-megaphone', routerLink: '/notificacoes/admin/comunicados' },
            { label: 'Automações', icon: 'pi pi-sitemap', routerLink: '/notificacoes/automacoes' }
          ]
        }
      ]
    },
    {
      label: 'Execução',
      icon: 'pi pi-bolt',
      items: [
        {
          label: 'Comercial',
          icon: 'pi pi-briefcase',
          items: [
            { label: 'Execuções Comerciais', icon: 'pi pi-list', routerLink: '/execucao/comercial' },
            { label: 'Turnos', icon: 'pi pi-calendar-clock', routerLink: '/execucao/turnos' },
            { label: 'Metas de faturamento', icon: 'pi pi-flag', routerLink: '/admin/cadastros/metas-faturamento' }
          ]
        },
        {
          label: 'Obras',
          icon: 'pi pi-building',
          items: [
            { label: 'Painel de Obras', icon: 'pi pi-list', routerLink: '/obras', routerLinkActiveOptions: { exact: true } },
            { label: 'Nova Obra', icon: 'pi pi-plus-circle', routerLink: '/obras/nova' },
            { label: 'Solicitar Materiais', icon: 'pi pi-cart-plus', routerLink: '/almoxarifado/solicitacoes-tecnicas/nova' }
          ]
        },
        {
          label: 'Relatórios',
          icon: 'pi pi-chart-bar',
          items: [
            { label: 'Dashboard Operacional', icon: 'pi pi-chart-line', routerLink: '/execucao/dashboard-operacional' },
            { label: 'Dashboard de Turnos', icon: 'pi pi-clock', routerLink: '/execucao/dashboard-turnos' }
          ]
        },
        {
          label: 'Cadastros',
          icon: 'pi pi-cog',
          items: [
            { label: 'Bases Operacionais', icon: 'pi pi-building', routerLink: '/admin/cadastros/bases-operacionais' },
            { label: 'Municípios', icon: 'pi pi-map', routerLink: '/admin/cadastros/municipios' },
            { label: 'Veículos', icon: 'pi pi-car', routerLink: '/admin/cadastros/veiculos'},
            { label: 'Equipes', icon: 'pi pi-sitemap', routerLink: '/admin/cadastros/equipes' },
            { label: 'Tipos de Equipe', icon: 'pi pi-tags', routerLink: '/admin/cadastros/tipos-equipe' },
            { label: 'Tipos de Serviço', icon: 'pi pi-tags', routerLink: '/admin/cadastros/tipos-servico' },
            { label: 'Coordenadores', icon: 'pi pi-sitemap', routerLink: '/admin/cadastros/coordenadores' },
            { label: 'Supervisores', icon: 'pi pi-users', routerLink: '/admin/cadastros/supervisores' },
            { label: 'Status de Obras', icon: 'pi pi-tag', routerLink: '/obras/cadastros/status' },
            { label: 'Agentes Responsáveis', icon: 'pi pi-users', routerLink: '/obras/cadastros/agentes' },
            { label: 'Tipos de Anexos', icon: 'pi pi-paperclip', routerLink: '/obras/cadastros/tipos-anexos' }
          ]
        }
      ]
    },
    {
      label: 'Frota',
      icon: 'pi pi-car',
      items: [
        { label: 'Veículos', icon: 'pi pi-car', routerLink: '/frota/veiculos' },
        { label: 'Condutores', icon: 'pi pi-id-card', routerLink: '/frota/condutores' },
        { label: 'Implementos', icon: 'pi pi-link', routerLink: '/frota/implementos' },
        { label: 'Documentos e vencimentos', icon: 'pi pi-file', routerLink: '/frota/documentos' },
        { label: 'Conferência de KM', icon: 'pi pi-gauge', routerLink: '/frota/odometro' },
        { label: 'Configurações', icon: 'pi pi-cog', routerLink: '/frota/configuracoes' }
      ]
    },
    {
      label: 'Almoxarifado',
      icon: 'pi pi-box',
      items: [
        {
          label: 'Solicitações Comerciais',
          icon: 'pi pi-shopping-cart',
          items: [
            { label: 'Gerenciar Solicitações', icon: 'pi pi-list', routerLink: '/almoxarifado/solicitacoes', routerLinkActiveOptions: { exact: true } },
            { label: 'Nova Solicitação', icon: 'pi pi-plus-circle', routerLink: '/almoxarifado/solicitacoes/nova' },
            { label: 'Nova Devolução', icon: 'pi pi-undo', routerLink: '/almoxarifado/devolucao-comercial' },
            { label: 'Quadro', icon: 'pi pi-objects-column', routerLink: '/almoxarifado/solicitacoes/kanban' },
            { label: 'Calendário de saques', icon: 'pi pi-calendar', routerLink: '/almoxarifado/solicitacoes/calendario' },
          ]
        },
        {
          label: 'Solicitações Técnicas',
          icon: 'pi pi-wrench',
          items: [
            { label: 'Gerenciar Solicitações', icon: 'pi pi-list', routerLink: '/almoxarifado/solicitacoes-tecnicas', routerLinkActiveOptions: { exact: true } },
            { label: 'Nova Solicitação', icon: 'pi pi-plus-circle', routerLink: '/almoxarifado/solicitacoes-tecnicas/nova' },
            { label: 'Nova Devolução', icon: 'pi pi-undo', routerLink: '/almoxarifado/solicitacoes-tecnicas/devolucao' },
            { label: 'Kanban', icon: 'pi pi-objects-column', routerLink: '/almoxarifado/solicitacoes-tecnicas/kanban' },
          ]
        },
        {
          label: 'Operações de Estoque',
          icon: 'pi pi-shopping-bag',
          items: [
            { label: 'Movimentações', icon: 'pi pi-history', routerLink: '/almoxarifado/movimentacoes' },
            { label: 'Transferências', icon: 'pi pi-arrow-right-arrow-left', routerLink: '/almoxarifado/transferencia-lote' },
            { label: 'Entrada Avulsa', icon: 'pi pi-plus-circle', routerLink: '/almoxarifado/entrada-avulsa' },
            { label: 'Baixa Avulsa', icon: 'pi pi-minus-circle', routerLink: '/almoxarifado/baixa-avulsa' }
          ]
        },
        {
          label: 'Relatórios',
          icon: 'pi pi-chart-bar',
          items: [
            { label: 'Relatório de Movimentações', icon: 'pi pi-chart-line', routerLink: '/almoxarifado/dashboard/movimentacoes' },
            { label: 'Painel de Solicitações', icon: 'pi pi-chart-pie', routerLink: '/almoxarifado/dashboard/solicitacoes' },
            { label: 'Solicitações Comerciais', icon: 'pi pi-shopping-cart', routerLink: '/almoxarifado/dashboard/solicitacoes/comercial' },
            { label: 'Solicitações Técnicas', icon: 'pi pi-wrench', routerLink: '/almoxarifado/dashboard/solicitacoes/tecnico' },
            { label: 'Saldos', icon: 'pi pi-search-plus', routerLink: '/almoxarifado/saldos' }
          ]
        },
        {
          label: 'Cadastros',
          icon: 'pi pi-cog',
          items: [
            { label: 'Materiais', icon: 'pi pi-box', routerLink: '/almoxarifado/materiais' },
            { label: 'Avaliações Técnicas', icon: 'pi pi-check-square', routerLink: '/almoxarifado/avaliacoes' },
            { label: 'Centros de Investimento', icon: 'pi pi-chart-line', routerLink: '/almoxarifado/centros-investimento' },
            { label: 'Estoques', icon: 'pi pi-database', routerLink: '/almoxarifado/estoques' },
            { label: 'Tipos de Estoque', icon: 'pi pi-tags', routerLink: '/almoxarifado/tipos-estoque' },
            { label: 'Motivos de Pendência', icon: 'pi pi-exclamation-triangle', routerLink: '/almoxarifado/motivos-pendencia' },
            { label: 'Motivos de Rejeição', icon: 'pi pi-times-circle', routerLink: '/almoxarifado/motivos-rejeicao' }
          ]
        }
      ]
    },
    {
      label: 'Financeiro',
      icon: 'pi pi-wallet',
      items: [
        {
          label: 'Minhas Solicitações',
          icon: 'pi pi-file-edit',
          items: [
            { label: 'Nova Compra', icon: 'pi pi-plus-circle', routerLink: '/financeiro/compras/nova' },
            { label: 'Novo Pagamento', icon: 'pi pi-plus-circle', routerLink: '/financeiro/pagamentos/nova' },
            { label: 'Compras', icon: 'pi pi-shopping-cart', routerLink: '/financeiro/compras', routerLinkActiveOptions: { exact: true } },
            { label: 'Pagamentos', icon: 'pi pi-credit-card', routerLink: '/financeiro/pagamentos', routerLinkActiveOptions: { exact: true } }
          ]
        },
        {
          label: 'Aprovações',
          icon: 'pi pi-check-circle',
          items: [
            { label: 'Compras Pendentes', icon: 'pi pi-shopping-cart', routerLink: '/financeiro/aprovacoes/compras' },
            { label: 'Pagamentos Pendentes', icon: 'pi pi-credit-card', routerLink: '/financeiro/aprovacoes/pagamentos' }
          ]
        },
        {
          label: 'Gestão',
          icon: 'pi pi-folder-open',
          items: [
            { label: 'Solicitações', icon: 'pi pi-list', routerLink: '/financeiro/gestao/solicitacoes', permission: 'financeiro:compra:read' },
            { label: 'Documentos', icon: 'pi pi-folder', routerLink: '/financeiro/documentos', permission: 'financeiro:documento:read' }
          ]
        },
        {
          label: 'Relatórios',
          icon: 'pi pi-chart-bar',
          items: [
            { label: 'Painel do Financeiro', icon: 'pi pi-chart-line', routerLink: '/financeiro/dashboard' }
          ]
        },
        {
          label: 'Cadastros',
          icon: 'pi pi-cog',
          items: [
            { label: 'Centros de Custo', icon: 'pi pi-wallet', routerLink: '/admin/cadastros/centros-custo' },
            { label: 'Contratos', icon: 'pi pi-file-edit', routerLink: '/admin/cadastros/contratos' },
            { label: 'Ações', icon: 'pi pi-bolt', routerLink: '/admin/cadastros/acoes' },
            { label: 'Ações x Valores', icon: 'pi pi-money-bill', routerLink: '/admin/cadastros/acoes-valores' },
            { label: 'Fornecedores', icon: 'pi pi-building', routerLink: '/financeiro/admin/fornecedores' },
            { label: 'Formulários Financeiros', icon: 'pi pi-file-edit', routerLink: '/financeiro/admin/formularios' }
          ]
        }
      ]
    },
    {
      label: 'Departamento Pessoal',
      icon: 'pi pi-users',
      items: [
        {
          label: 'Descontos em Folha',
          icon: 'pi pi-money-bill',
          items: [
            { label: 'Dashboard de Descontos', icon: 'pi pi-chart-line', routerLink: '/dp/descontos/dashboard' },
            { label: 'Consulta de Descontos', icon: 'pi pi-list', routerLink: '/dp/descontos/lancamento' },
            { label: 'Novo Desconto', icon: 'pi pi-file-edit', url: '/dp/descontos/novo', target: '_blank' }
          ]
        },
        {
          label: 'Pendências',
          icon: 'pi pi-inbox',
          items: [
            { label: 'Aguardando Validação', icon: 'pi pi-check-square', routerLink: '/dp/descontos/validacao' },
            { label: 'Aguardando Baixa', icon: 'pi pi-wallet', routerLink: '/dp/descontos/baixa' }
          ]
        },
        {
          label: 'Cadastros',
          icon: 'pi pi-cog',
          items: [
            { label: 'Colaboradores', icon: 'pi pi-user-plus', routerLink: '/admin/cadastros/colaboradores' },
            { label: 'Funções', icon: 'pi pi-briefcase', routerLink: '/admin/cadastros/funcoes'},
            { label: 'Processos', icon: 'pi pi-list-check', routerLink: '/admin/cadastros/processos' },
            { label: 'Motivos de Desconto', icon: 'pi pi-exclamation-triangle', routerLink: '/admin/cadastros/motivos-desconto' },
            { label: 'Tipos Equipamento', icon: 'pi pi-box', routerLink: '/admin/cadastros/tipos-equipamento-desconto' }
          ]
        }
      ]
    },
    {
      label: 'Segurança',
      icon: 'pi pi-shield',
      items: [
        {
          label: 'Minhas Ações',
          icon: 'pi pi-user',
          items: [
            { label: 'Nova Inspeção', icon: 'pi pi-plus-circle', routerLink: '/sst/inspecoes/nova' },
            { label: 'Meus Desvios', icon: 'pi pi-exclamation-circle', routerLink: '/sst/meus-desvios' }
          ]
        },
        {
          label: 'Gestão',
          icon: 'pi pi-check-square',
          items: [
            { label: 'Inspeções', icon: 'pi pi-list', routerLink: '/sst/inspecoes', routerLinkActiveOptions: { exact: true } },
            { label: 'Desvios', icon: 'pi pi-exclamation-triangle', routerLink: '/sst/desvios' }
          ]
        },
        {
          label: 'CIPA',
          icon: 'pi pi-users',
          items: [
            { label: 'Visão geral', icon: 'pi pi-home', routerLink: '/sst/governanca/cipa/painel' },
            { label: 'Composição da CIPA', icon: 'pi pi-users', routerLink: '/sst/governanca/cipa/composicoes' },
            { label: 'Reuniões e atas', icon: 'pi pi-calendar', routerLink: '/sst/governanca/cipa/reunioes' },
            { label: 'Documentos', icon: 'pi pi-folder', routerLink: '/sst/governanca/cipa/documentos' },
            { label: 'Treinamentos', icon: 'pi pi-graduation-cap', routerLink: '/sst/governanca/cipa/treinamentos' },
            { label: 'Inspeções e ações', icon: 'pi pi-check-square', routerLink: '/sst/governanca/cipa/acoes' },
            { label: 'Configurações', icon: 'pi pi-cog', routerLink: '/sst/governanca/configuracoes' }
          ]
        },
        {
          label: 'Brigada de Incêndio',
          icon: 'pi pi-shield',
          items: [
            { label: 'Visão geral', icon: 'pi pi-home', routerLink: '/sst/governanca/brigada/painel' },
            { label: 'Equipe de brigadistas', icon: 'pi pi-users', routerLink: '/sst/governanca/brigada/composicoes' },
            { label: 'Reuniões e atas', icon: 'pi pi-calendar', routerLink: '/sst/governanca/brigada/reunioes' },
            { label: 'Documentos', icon: 'pi pi-folder', routerLink: '/sst/governanca/brigada/documentos' },
            { label: 'Treinamentos', icon: 'pi pi-graduation-cap', routerLink: '/sst/governanca/brigada/treinamentos' },
            { label: 'Inspeções e ações', icon: 'pi pi-check-square', routerLink: '/sst/governanca/brigada/acoes' },
            { label: 'Configurações', icon: 'pi pi-cog', routerLink: '/sst/governanca/configuracoes' }
          ]
        },
        {
          label: 'Acidentalidade',
          icon: 'pi pi-heart',
          items: [
            { label: 'Eventos de Segurança', icon: 'pi pi-list', routerLink: '/sst/eventos', routerLinkActiveOptions: { exact: true } },
            { label: 'Registrar Evento', icon: 'pi pi-plus-circle', routerLink: '/sst/eventos/novo' }
          ]
        },
        {
          label: 'Relatórios',
          icon: 'pi pi-chart-bar',
          items: [
            { label: 'Dashboard de Inspeções', icon: 'pi pi-chart-line', routerLink: '/sst/dashboards/inspecoes' },
            { label: 'Dashboard de Desvios', icon: 'pi pi-exclamation-triangle', routerLink: '/sst/dashboards/desvios' },
            { label: 'Dashboard de Acidentalidade', icon: 'pi pi-heart', routerLink: '/sst/dashboards/eventos' },
            { label: 'Acompanhamento de Metas', icon: 'pi pi-flag', routerLink: '/sst/metas-inspecao/acompanhamento' }
          ]
        },
        {
          label: 'Cadastros',
          icon: 'pi pi-cog',
          items: [
            { label: 'Tipos de Inspeção', icon: 'pi pi-tags', routerLink: '/sst/tipos-inspecao' },
            { label: 'Formulários de Inspeção', icon: 'pi pi-file-edit', routerLink: '/sst/formularios' },
            { label: 'Metas de Inspeção', icon: 'pi pi-flag', routerLink: '/sst/metas-inspecao' },
            { label: 'Atividades de SST', icon: 'pi pi-briefcase', routerLink: '/sst/atividades' },
            { label: 'Classificações de Eventos', icon: 'pi pi-tags', routerLink: '/sst/classificacoes-eventos' },
            { label: 'Locais de Lesão', icon: 'pi pi-map-marker', routerLink: '/sst/locais-lesao' },
            { label: 'Naturezas de Lesão', icon: 'pi pi-heart', routerLink: '/sst/naturezas-lesao' },
            { label: 'Agentes Causadores', icon: 'pi pi-bolt', routerLink: '/sst/agentes-causadores' }
          ]
        }
      ]
    },
    {
      label: 'Chamados',
      icon: 'pi pi-ticket',
      items: [
        {
          label: 'Autosserviço',
          icon: 'pi pi-user',
          items: [
            { label: 'Meus chamados', icon: 'pi pi-list', routerLink: '/chamados/meus', routerLinkActiveOptions: { exact: true } },
            { label: 'Novo chamado', icon: 'pi pi-plus-circle', routerLink: '/chamados/novo' }
          ]
        },
        {
          label: 'Atendimento',
          icon: 'pi pi-headphones',
          items: [
            { label: 'Fila da central', icon: 'pi pi-inbox', routerLink: '/chamados/fila' }
          ]
        },
        {
          label: 'Relatórios',
          icon: 'pi pi-chart-bar',
          items: [
            { label: 'Dashboard de Chamados', icon: 'pi pi-chart-bar', routerLink: '/chamados/dashboard' }
          ]
        },
        {
          label: 'Cadastros',
          icon: 'pi pi-cog',
          items: [
            { label: 'Tópicos', icon: 'pi pi-tags', routerLink: '/chamados/admin/topicos' },
            { label: 'Equipes de atendimento', icon: 'pi pi-users', routerLink: '/chamados/admin/equipes' }
          ]
        }
      ]
    },
    {
      label: 'Treinamento',
      icon: 'pi pi-graduation-cap',
      items: [
        {
          label: 'Guias e Manuais',
          icon: 'pi pi-book',
          items: [
            { label: 'Central de Treinamento', icon: 'pi pi-graduation-cap', routerLink: '/manuais' },
            { label: 'Manual de Descontos', icon: 'pi pi-file-edit', routerLink: '/manual-descontos' },
            { label: 'Manual de SST', icon: 'pi pi-shield', routerLink: '/manual-sst' }
          ]
        }
      ]
    }
  ]));

  protected toggleMenu() {
    this.menuVisible.update(v => !v);
  }

  protected expandAndFocus(item: MenuItem) {
    this.menuItems.update(items => {
      items.forEach(i => i.expanded = (i === item));
      return [...items];
    });
    this.menuVisible.set(true);
  }

  protected logout() {
    this.auth.logout();
  }
}
