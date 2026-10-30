import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesDrawerComponent,
  CesFilterPanelComponent,
  CesFormDrawerComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTone,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';
import { catchError, map, of } from 'rxjs';

import { PerfilAcessoResponse } from '../../../../core/models/iam.models';
import {
  ComunicadoResponse,
  RelatorioConfirmacaoResponse,
  SegmentoComunicadoResponse,
  StatusComunicado,
  TipoComunicado
} from '../../../../core/models/notificacao.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { ComunicadosService } from '../../../../core/services/comunicados.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { AjudaListagemComponent } from '../../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, paginaPadrao } from '../../../../shared/listagem/listagem';
import { STATUS_COMUNICADO_OPTIONS, TIPO_COMUNICADO_ICONES, TIPO_COMUNICADO_OPTIONS, TIPO_USUARIO_OPTIONS, formatarDataHora } from '../../notificacoes-ui';

const TIPOS: CesSelectOption<TipoComunicado | null>[] = [{ label: 'Todos', value: null }, ...TIPO_COMUNICADO_OPTIONS];
const STATUS: CesSelectOption<StatusComunicado | null>[] = [{ label: 'Todos', value: null }, ...STATUS_COMUNICADO_OPTIONS];
const CONFIRMACAO: CesSelectOption<boolean | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Exigem confirmação', value: true },
  { label: 'Não exigem', value: false }
];
const SITUACAO: CesSelectOption<boolean | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Ativos', value: true },
  { label: 'Inativos', value: false }
];
const TOM_STATUS: Record<StatusComunicado, CesTone> = { RASCUNHO: 'neutral', PUBLICADO: 'success', ARQUIVADO: 'neutral' };
const TOM_TIPO: Record<TipoComunicado, CesTone> = { DICA: 'warning', CHANGELOG: 'brand', AVISO: 'info', MANUTENCAO: 'neutral', URGENTE: 'danger' };

@Component({
  selector: 'app-comunicados',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesAsyncStateComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesDrawerComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novo()">
      <ces-page-header
        title="Comunicados"
        icon="pi pi-megaphone"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Comunicação' }, { label: 'Comunicados' }]"
        description="Mural de avisos, dicas e novidades: rascunho, publicação (agora ou agendada), público-alvo e confirmação de leitura."
        helpLabel="Ajuda sobre comunicados"
        (help)="ajuda.show()"
      >
        <ces-button actions icon="pi pi-plus" label="Novo comunicado" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="comunicados" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Tipo" [options]="tipos" [(value)]="lista.filters.tipo"></ces-select>
          <ces-select label="Status" [options]="status" [(value)]="lista.filters.status"></ces-select>
          <ces-select label="Confirmação de leitura" [options]="confirmacao" [(value)]="lista.filters.exigeConfirmacao"></ces-select>
          <ces-select label="Situação do registro" [options]="situacao" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Comunicados"
        mode="server"
        storageKey="comunicados"
        exportFileName="comunicados"
        searchPlaceholder="Título ou resumo"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo comunicado"
        noResultsTitle="Nenhum comunicado encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os comunicados' : 'Nenhum comunicado criado'"
        [stateDescription]="resultado.error() ?? 'Crie um comunicado para avisar as equipes pelo mural e pelo sino.'"
        [filtered]="lista.filtered()"
        [exportRows]="exportar"
        [exportLimit]="limiteExportacao"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="novo()"
        (rowActivate)="editar($event)"
      >
        <ng-template cesCell="titulo" let-row>
          <span class="ces-cell-strong">
            <i [class]="icone(row)" aria-hidden="true"></i> {{ row.titulo }}
            @if (row.fixado) {
              <i class="pi pi-thumbtack" aria-label="Fixado no topo"></i>
            }
            @if (row.anexos?.length) {
              <span class="ces-cell-muted"><i class="pi pi-paperclip" aria-hidden="true"></i> {{ row.anexos.length }}</span>
            }
          </span>
          @if (row.resumo) {
            <span class="ces-cell-muted">{{ row.resumo }}</span>
          }
          <span class="ces-cell-chips"><ces-status [tone]="tomTipo(row.tipo)">{{ row.tipoRotulo }}</ces-status></span>
        </ng-template>
        <ng-template cesCell="status" let-row>
          <ces-status [tone]="tomStatus(row.status)" [emphasis]="row.status === 'ARQUIVADO' ? 'outline' : 'soft'">{{ row.statusRotulo }}</ces-status>
        </ng-template>
        <ng-template cesCell="vigencia" let-row>
          <ces-status [tone]="vigencia(row).tom" emphasis="outline">{{ vigencia(row).rotulo }}</ces-status>
          @if (row.dataInicio) {
            <span class="ces-cell-muted">de {{ quando(row.dataInicio) }}</span>
          }
          @if (row.dataFim) {
            <span class="ces-cell-muted">até {{ quando(row.dataFim) }}</span>
          }
        </ng-template>
        <ng-template cesCell="publico" let-row>
          <span class="ces-cell-chips">
            @for (segmento of row.segmentos; track segmento.id) {
              <ces-status tone="neutral" emphasis="outline">{{ alvo(segmento) }}</ces-status>
            }
          </span>
        </ng-template>
        <ng-template cesCell="entrega" let-row>
          @if (row.notificarNoSininho) {
            <span><i class="pi pi-bell" aria-hidden="true"></i> Sino</span>
          }
          @if (row.exigeConfirmacao) {
            <span><i class="pi pi-verified" aria-hidden="true"></i> Exige confirmação</span>
          }
          @if (!row.notificarNoSininho && !row.exigeConfirmacao) {
            <span class="ces-cell-muted">Somente no mural</span>
          }
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            @if (row.status === 'RASCUNHO') {
              <ces-icon-button icon="pi pi-send" [label]="'Publicar ' + row.titulo" size="icon-sm" (pressed)="abrirPublicacao(row)"></ces-icon-button>
            }
            @if (row.status === 'PUBLICADO') {
              <ces-icon-button icon="pi pi-inbox" [label]="'Arquivar ' + row.titulo" size="icon-sm" (pressed)="arquivar(row)"></ces-icon-button>
            }
            @if (row.exigeConfirmacao) {
              <ces-icon-button icon="pi pi-verified" [label]="'Relatório de confirmação de ' + row.titulo" size="icon-sm" (pressed)="abrirRelatorio(row)"></ces-icon-button>
            }
            <ces-icon-button icon="pi pi-trash" [label]="'Remover ' + row.titulo" size="icon-sm" (pressed)="remover(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="publicacaoAberta" title="Publicar comunicado" [description]="selecionado()?.titulo" [dirty]="!!(inicio() || fim())" size="sm">
      <ces-callout tone="info">Sem início, o comunicado entra no ar agora. Com início futuro ele já fica publicado e aparece quando a janela começar.</ces-callout>
      <ces-datepicker label="Início da vigência" [showTime]="true" [(value)]="inicio" [clearable]="true" [showOptional]="true" placeholder="Publicar agora"></ces-datepicker>
      <ces-datepicker label="Fim da vigência" [showTime]="true" [(value)]="fim" [clearable]="true" [showOptional]="true" placeholder="Até arquivar"
        [minDate]="inicio()" [error]="erroPeriodo()"></ces-datepicker>
      @if (selecionado()?.segmentos?.length) {
        <p class="ces-cell-muted">Será entregue a:</p>
        <span class="ces-cell-chips">
          @for (segmento of selecionado()!.segmentos; track segmento.id) {
            <ces-status tone="neutral" emphasis="outline">{{ alvo(segmento) }}</ces-status>
          }
        </span>
      }
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Voltar" (pressed)="publicacaoAberta.set(false)"></ces-button>
        <ces-button icon="pi pi-send" label="Publicar" [loading]="salvando()" (pressed)="publicar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-drawer [(visible)]="relatorioAberto" title="Relatório de confirmação" [description]="selecionado()?.titulo" size="lg">
      @if (carregandoRelatorio()) {
        <ces-async-state state="loading" title="Carregando o relatório"></ces-async-state>
      } @else if (relatorio(); as dados) {
        <p class="ces-cell-muted">Prova de comunicação para auditoria: o denominador é o total de destinatários.</p>
        <div class="ces-kpis">
          <div class="ces-kpi" data-tone="info"><span class="ces-kpi__body"><span class="ces-kpi__label">Destinatários</span><span class="ces-kpi__value">{{ dados.totalDestinatarios }}</span></span></div>
          <div class="ces-kpi" data-tone="success"><span class="ces-kpi__body"><span class="ces-kpi__label">Confirmaram</span><span class="ces-kpi__value">{{ dados.totalConfirmados }}</span></span></div>
          <div class="ces-kpi" data-tone="neutral"><span class="ces-kpi__body"><span class="ces-kpi__label">Visualizaram</span><span class="ces-kpi__value">{{ dados.totalVisualizados }}</span></span></div>
          <div class="ces-kpi" [attr.data-tone]="cobertura() === 100 ? 'success' : 'warning'"><span class="ces-kpi__body"><span class="ces-kpi__label">Cobertura</span><span class="ces-kpi__value">{{ cobertura() }}%</span></span></div>
        </div>
        <ces-data-table [caption]="'Confirmados (' + dados.confirmados.length + ')'" [columns]="colunasConfirmados" [rows]="dados.confirmados"
          [rowKey]="chavePessoa" [state]="dados.confirmados.length ? 'ready' : 'empty'" stateTitle="Ninguém confirmou ainda"></ces-data-table>
        <ces-data-table [caption]="'Pendentes (' + dados.pendentes.length + ') · ' + abriramSemConfirmar() + ' abriram sem confirmar'" [columns]="colunasPendentes"
          [rows]="dados.pendentes" [rowKey]="chavePessoa" [state]="dados.pendentes.length ? 'ready' : 'empty'" stateTitle="Todos confirmaram">
          <ng-template cesCell="situacao" let-row>
            @if (row.visualizadoEm) {
              <ces-status tone="warning" [title]="'Abriu em ' + quando(row.visualizadoEm)">Abriu, não confirmou</ces-status>
            } @else {
              <ces-status tone="neutral" emphasis="outline">Nunca abriu</ces-status>
            }
          </ng-template>
        </ces-data-table>
      }
    </ces-drawer>

    <app-ajuda-listagem #ajuda titulo="Comunicados" entidadePlural="comunicados" busca="título ou resumo" rotuloNovo="Novo comunicado">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-sync"></i> Ciclo de vida</h4>
        <p class="help-text-desc"><strong>Rascunho</strong>: tudo pode mudar e nada foi entregue. <strong>Publicado</strong>: com início futuro aparece como "Agendado"; depois disso só o texto pode ser corrigido. <strong>Arquivado</strong>: sai do mural de todos; o relatório de confirmação continua disponível.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-verified"></i> Público-alvo e confirmação</h4>
        <p class="help-text-desc">Combine perfis, tipos de usuário e pessoas. Exigir confirmação ("Li e entendi") bloqueia o adiamento e habilita o relatório de quem confirmou, abriu sem confirmar ou nunca viu.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class ComunicadosComponent {
  private readonly service = inject(ComunicadosService);
  private readonly usuarios = inject(UsuariosService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacaoDialogo = inject(CesConfirmationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly tipos = TIPOS;
  protected readonly status = STATUS;
  protected readonly confirmacao = CONFIRMACAO;
  protected readonly situacao = SITUACAO;
  protected readonly perfis = signal<PerfilAcessoResponse[]>([]);
  /** Logins dos usuários citados nos segmentos da página. */
  private readonly logins = signal<Record<string, string>>({});

  protected readonly lista = cesListState({
    storageKey: 'comunicados',
    filters: {
      tipo: cesFilter.value<TipoComunicado | null>(null, { label: 'Tipo', type: 'string', options: () => TIPOS }),
      status: cesFilter.value<StatusComunicado | null>(null, { label: 'Status', type: 'string', options: () => STATUS }),
      exigeConfirmacao: cesFilter.value<boolean | null>(null, { label: 'Confirmação', param: 'confirmacao', options: () => CONFIRMACAO }),
      ativo: cesFilter.value<boolean | null>(true, { label: 'Situação', param: 'situacao', options: () => SITUACAO })
    },
    sort: [{ key: 'criado', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<ComunicadoResponse>((pedido) => this.service.listar(this.filtro(pedido)), { errorMessage: getErrorMessage });
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = (_evento: unknown, limite: number) =>
    this.service.listar({ ...this.filtro(this.lista.request()), page: 0, size: limite }).pipe(map((r) => r.content));

  protected readonly colunas: CesDataTableColumn<ComunicadoResponse>[] = [
    { key: 'titulo', header: 'Comunicado', type: 'custom', priority: 1, sortField: 'titulo', value: (c) => c.titulo, display: (c) => c.titulo },
    { key: 'apresentacao', header: 'Apresentação', value: (c) => c.apresentacaoRotulo, display: (c) => c.apresentacaoRotulo },
    { key: 'status', header: 'Status', type: 'custom', priority: 1, value: (c) => c.status, display: (c) => c.statusRotulo },
    { key: 'vigencia', header: 'Vigência', type: 'custom', value: (c) => this.vigencia(c).rotulo, display: (c) => this.vigencia(c).rotulo },
    { key: 'publico', header: 'Público-alvo', type: 'custom', value: (c) => c.segmentos.map((s) => this.alvo(s)).join(', '), display: (c) => c.segmentos.map((s) => this.alvo(s)).join(', ') },
    { key: 'entrega', header: 'Entrega', type: 'custom', value: (c) => this.entrega(c), display: (c) => this.entrega(c) },
    { key: 'criado', header: 'Criado em', type: 'date', sortField: 'dataCriacao', value: (c) => c.dataCriacao }
  ];
  protected readonly rotulo = (c: ComunicadoResponse) => c.titulo;

  // Publicação
  protected readonly publicacaoAberta = signal(false);
  protected readonly selecionado = signal<ComunicadoResponse | null>(null);
  protected readonly inicio = signal<Date | null>(null);
  protected readonly fim = signal<Date | null>(null);
  protected readonly salvando = signal(false);
  protected readonly erroPeriodo = computed(() => {
    const inicio = this.inicio();
    const fim = this.fim();
    return inicio && fim && fim <= inicio ? 'O fim precisa ser depois do início.' : undefined;
  });

  // Relatório
  protected readonly relatorioAberto = signal(false);
  protected readonly relatorio = signal<RelatorioConfirmacaoResponse | null>(null);
  protected readonly carregandoRelatorio = signal(false);
  protected readonly cobertura = computed(() => {
    const r = this.relatorio();
    return !r || r.totalDestinatarios === 0 ? 0 : Math.round((r.totalConfirmados / r.totalDestinatarios) * 100);
  });
  protected readonly abriramSemConfirmar = computed(() => this.relatorio()?.pendentes.filter((p) => !!p.visualizadoEm).length ?? 0);
  protected readonly chavePessoa = (p: { idUsuario: string }) => p.idUsuario;
  protected readonly colunasConfirmados: CesDataTableColumn<RelatorioConfirmacaoResponse['confirmados'][number]>[] = [
    { key: 'login', header: 'Usuário', priority: 1 },
    { key: 'confirmadoEm', header: 'Confirmou em', value: (p) => p.confirmadoEm, display: (p) => formatarDataHora(p.confirmadoEm) }
  ];
  protected readonly colunasPendentes: CesDataTableColumn<RelatorioConfirmacaoResponse['pendentes'][number]>[] = [
    { key: 'login', header: 'Usuário', priority: 1 },
    { key: 'situacao', header: 'Situação', type: 'custom', value: (p) => (p.visualizadoEm ? 'Abriu' : 'Nunca abriu') }
  ];

  constructor() {
    this.usuarios.listarPerfisAtivos().subscribe({ next: (p) => this.perfis.set(p), error: () => undefined });
    // O formulário volta com `?publicar=<id>` quando o autor escolhe publicar logo após gravar.
    const idParaPublicar = this.route.snapshot.queryParamMap.get('publicar');
    if (idParaPublicar) {
      this.service.buscar(idParaPublicar).subscribe({ next: (c) => this.abrirPublicacao(c), error: () => undefined });
      void this.router.navigate([], { relativeTo: this.route, queryParams: { publicar: null }, queryParamsHandling: 'merge', replaceUrl: true });
    }
    effect(() => {
      const ids = this.resultado.rows().flatMap((c) => c.segmentos.filter((s) => s.tipoAlvo === 'USUARIO' && !!s.idAlvo).map((s) => s.idAlvo as string));
      untracked(() => this.carregarLogins(ids));
    });
  }

  private filtro(pedido: Parameters<typeof paginaPadrao>[0]) {
    const { filters, query } = this.lista.request();
    return {
      buscaGlobal: query || undefined,
      tipo: filters.tipo ?? undefined,
      status: filters.status ?? undefined,
      exigeConfirmacao: filters.exigeConfirmacao ?? undefined,
      ativo: filters.ativo ?? undefined,
      ...(paginaPadrao(pedido) as { page: number; size: number; sort?: string })
    };
  }

  private carregarLogins(ids: readonly string[]): void {
    const conhecidos = this.logins();
    new Set(ids.filter((id) => !(id in conhecidos))).forEach((id) =>
      this.usuarios
        .buscarUsuarioPorId(id)
        .pipe(catchError(() => of(null)))
        .subscribe((u) => this.logins.update((l) => ({ ...l, [id]: u?.login ?? 'Usuário' })))
    );
  }

  protected icone(c: ComunicadoResponse): string {
    return TIPO_COMUNICADO_ICONES[c.tipo] ?? 'pi pi-megaphone';
  }

  protected tomTipo(t: TipoComunicado): CesTone {
    return TOM_TIPO[t] ?? 'neutral';
  }

  protected tomStatus(s: StatusComunicado): CesTone {
    return TOM_STATUS[s] ?? 'neutral';
  }

  /** Não existe status "agendado": os indicadores vêm prontos do servidor. */
  protected vigencia(c: ComunicadoResponse): { rotulo: string; tom: CesTone } {
    if (c.status !== 'PUBLICADO') return { rotulo: '—', tom: 'neutral' };
    if (c.agendado) return { rotulo: 'Agendado', tom: 'warning' };
    if (c.vigente) return { rotulo: 'No ar', tom: 'success' };
    return { rotulo: 'Fora do ar', tom: 'neutral' };
  }

  protected alvo(s: SegmentoComunicadoResponse): string {
    if (s.descricaoAlvo) return s.descricaoAlvo;
    if (s.tipoAlvo === 'TODOS') return 'Todos os usuários';
    if (s.tipoAlvo === 'TIPO_USUARIO') return TIPO_USUARIO_OPTIONS.find((o) => o.value === s.valorAlvo)?.label ?? s.valorAlvo ?? '—';
    if (s.tipoAlvo === 'PERFIL') return this.perfis().find((p) => p.id === s.idAlvo)?.nome ?? 'Perfil de acesso';
    return (s.idAlvo && this.logins()[s.idAlvo]) || 'Usuário';
  }

  private entrega(c: ComunicadoResponse): string {
    const partes = [c.notificarNoSininho ? 'Sino' : null, c.exigeConfirmacao ? 'Exige confirmação' : null].filter(Boolean);
    return partes.length ? partes.join(' · ') : 'Somente no mural';
  }

  protected quando(valor: string | null): string {
    return formatarDataHora(valor);
  }

  protected novo(): void {
    void this.router.navigate(['/notificacoes/admin/comunicados/novo']);
  }

  /** Rascunho abre o formulário completo; publicado, o mesmo formulário só para o texto. Arquivado não edita. */
  protected editar(c: ComunicadoResponse): void {
    if (c.status === 'ARQUIVADO') {
      this.notificacoes.info('Comunicados arquivados não podem ser editados.');
      return;
    }
    void this.router.navigate(['/notificacoes/admin/comunicados', c.id, 'editar']);
  }

  protected abrirPublicacao(c: ComunicadoResponse): void {
    this.selecionado.set(c);
    this.inicio.set(null);
    this.fim.set(null);
    this.publicacaoAberta.set(true);
  }

  protected publicar(): void {
    const atual = this.selecionado();
    if (!atual || this.erroPeriodo()) return;
    const inicio = this.inicio();
    this.salvando.set(true);
    this.service.publicar(atual.id, { dataInicio: inicio?.toISOString() ?? null, dataFim: this.fim()?.toISOString() ?? null }).subscribe({
      next: () => {
        this.publicacaoAberta.set(false);
        this.concluir(inicio && inicio.getTime() > Date.now() ? 'Comunicado publicado e agendado.' : 'Comunicado publicado.');
      },
      error: (e) => this.falhar(e)
    });
  }

  protected async arquivar(c: ComunicadoResponse): Promise<void> {
    const ok = await this.confirmacaoDialogo.confirm({
      title: 'Arquivar comunicado',
      message: `"${c.titulo}" sai do mural de todos os destinatários. O relatório de confirmação continua disponível.`,
      confirmLabel: 'Arquivar'
    });
    if (!ok) return;
    this.service.arquivar(c.id).subscribe({ next: () => this.concluir('Comunicado arquivado.'), error: (e) => this.falhar(e) });
  }

  protected async remover(c: ComunicadoResponse): Promise<void> {
    const ok = await this.confirmacaoDialogo.confirm({
      title: 'Remover comunicado',
      message: `"${c.titulo}" deixa de aparecer nas listagens.`,
      confirmLabel: 'Remover',
      variant: 'destructive'
    });
    if (!ok) return;
    this.service.remover(c.id).subscribe({ next: () => this.concluir('Comunicado removido.'), error: (e) => this.falhar(e) });
  }

  protected abrirRelatorio(c: ComunicadoResponse): void {
    this.selecionado.set(c);
    this.relatorio.set(null);
    this.carregandoRelatorio.set(true);
    this.relatorioAberto.set(true);
    this.service.relatorioConfirmacao(c.id).subscribe({
      next: (dados) => {
        this.relatorio.set(dados);
        this.carregandoRelatorio.set(false);
      },
      error: (e) => {
        this.carregandoRelatorio.set(false);
        this.falhar(e);
      }
    });
  }

  private concluir(mensagem: string): void {
    this.salvando.set(false);
    this.notificacoes.success(mensagem);
    this.lista.reload();
  }

  private falhar(e: unknown): void {
    this.salvando.set(false);
    this.notificacoes.error(getErrorMessage(e));
  }
}
