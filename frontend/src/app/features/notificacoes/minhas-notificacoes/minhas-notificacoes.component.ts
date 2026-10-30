import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFilterPanelComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';

import { CategoriaNotificacao, NotificacaoResponse, PrioridadeNotificacao } from '../../../core/models/notificacao.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { NotificacoesStore } from '../../../core/services/notificacoes-store.service';
import { NotificacoesService } from '../../../core/services/notificacoes.service';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { paginaPadrao } from '../../../shared/listagem/listagem';
import { CATEGORIA_ICONES, CATEGORIA_OPTIONS, PRIORIDADE_OPTIONS, PRIORIDADE_TOM, formatarDataHora, tempoRelativo } from '../notificacoes-ui';

const CATEGORIAS: CesSelectOption<CategoriaNotificacao | null>[] = [{ label: 'Todas', value: null }, ...CATEGORIA_OPTIONS];
const PRIORIDADES: CesSelectOption<PrioridadeNotificacao | null>[] = [{ label: 'Todas', value: null }, ...PRIORIDADE_OPTIONS];

@Component({
  selector: 'app-minhas-notificacoes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesBooleanControlComponent,
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts>
      <ces-page-header
        title="Minhas notificações"
        icon="pi pi-bell"
        [breadcrumb]="[{ label: 'Comunicação' }, { label: 'Minhas notificações' }]"
        description="Avisos do sistema para você. Clique numa notificação com destino para abrir o registro."
        helpLabel="Ajuda sobre notificações"
        (help)="ajuda.show()"
      >
        @if (store.temNaoLidas()) {
          <ces-button actions variant="outline" icon="pi pi-check-circle" label="Marcar todas como lidas" (pressed)="marcarTodasLidas()"></ces-button>
        }
        <ces-button actions variant="ghost" icon="pi pi-inbox" label="Arquivar lidas" (pressed)="arquivarLidas()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="minhas-notificacoes" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Categoria" [options]="categorias" [(value)]="lista.filters.categoria"></ces-select>
          <ces-select label="Prioridade" [options]="prioridades" [(value)]="lista.filters.prioridade"></ces-select>
          <ces-boolean-control label="Apenas não lidas" kind="toggle" [inline]="true" [value]="lista.filters.naoLidas()" (valueChange)="lista.filters.naoLidas.set($event)"></ces-boolean-control>
          <ces-boolean-control label="Incluir arquivadas" kind="toggle" [inline]="true" [value]="lista.filters.arquivadas()" (valueChange)="lista.filters.arquivadas.set($event)"></ces-boolean-control>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Notificações"
        mode="server"
        storageKey="minhas-notificacoes"
        searchPlaceholder="Título ou texto"
        noResultsTitle="Nenhuma notificação encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as notificações' : 'Nenhuma notificação por aqui'"
        [stateDescription]="resultado.error() ?? 'Quando algo precisar da sua atenção, o aviso aparece aqui e no sino do topo.'"
        [filtered]="lista.filtered()"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        [rowClickable]="true"
        (rowActivate)="abrir($event)"
      >
        @if (resumoCategorias().length) {
          <div table-filters class="ces-quick-filters" role="group" aria-label="Não lidas por categoria">
            @for (chip of resumoCategorias(); track chip.categoria) {
              <button type="button" class="ces-quick-filter" [attr.aria-pressed]="lista.applied().categoria === chip.categoria" (click)="filtrarCategoria(chip.categoria)">
                <i [class]="chip.icone" aria-hidden="true"></i> {{ chip.rotulo }} · {{ chip.quantidade }}
              </button>
            }
          </div>
        }
        <ng-template cesCell="notificacao" let-row>
          <span class="ces-cell-strong">
            @if (!lida(row)) {
              <ces-status tone="brand" emphasis="solid">Nova</ces-status>
            }
            {{ row.titulo }}
          </span>
          <span>{{ row.corpo }}</span>
          <span class="ces-cell-muted" [title]="dataCompleta(row)">
            <i [class]="icone(row)" aria-hidden="true"></i> {{ row.categoriaRotulo }} · {{ quando(row) }}{{ row.deeplink ? ' · abrir registro' : '' }}
          </span>
        </ng-template>
        <ng-template cesCell="prioridade" let-row>
          <ces-status [tone]="tom(row.prioridade)">{{ row.prioridadeRotulo }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            @if (row.deeplink) {
              <ces-icon-button icon="pi pi-external-link" [label]="'Abrir registro: ' + row.titulo" tooltip="Abrir registro" size="icon-sm" (pressed)="abrir(row)"></ces-icon-button>
            }
            <ces-icon-button [icon]="lida(row) ? 'pi pi-envelope' : 'pi pi-check'" [label]="(lida(row) ? 'Marcar como não lida: ' : 'Marcar como lida: ') + row.titulo"
              size="icon-sm" (pressed)="alternarLeitura(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-inbox" [label]="'Arquivar: ' + row.titulo" size="icon-sm" (pressed)="arquivar(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <app-ajuda-listagem #ajuda titulo="Minhas notificações" artigoPlural="as" entidadePlural="notificações" busca="título ou texto" [podeCriar]="false">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-external-link"></i> Abrir</h4>
        <p class="help-text-desc">Notificações com destino levam ao registro que as gerou e ficam lidas. As demais são só aviso; marque como lida no ícone da linha.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-exclamation-triangle"></i> Prioridade crítica</h4>
        <p class="help-text-desc">Avisos críticos chegam sempre, mesmo com a categoria desligada nas suas preferências.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-inbox"></i> Arquivar</h4>
        <p class="help-text-desc">Tira da caixa de entrada sem apagar. Ligue "Incluir arquivadas" nos filtros para rever.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class MinhasNotificacoesComponent {
  private readonly service = inject(NotificacoesService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly router = inject(Router);
  protected readonly store = inject(NotificacoesStore);

  protected readonly categorias = CATEGORIAS;
  protected readonly prioridades = PRIORIDADES;

  protected readonly lista = cesListState({
    storageKey: 'minhas-notificacoes',
    filters: {
      categoria: cesFilter.value<CategoriaNotificacao | null>(null, { label: 'Categoria', type: 'string', options: () => CATEGORIAS }),
      prioridade: cesFilter.value<PrioridadeNotificacao | null>(null, { label: 'Prioridade', type: 'string', options: () => PRIORIDADES }),
      naoLidas: cesFilter.value<boolean>(false, { label: 'Leitura', param: 'naoLidas', format: (v): string | null => (v ? 'apenas não lidas' : null) }),
      arquivadas: cesFilter.value<boolean>(false, { label: 'Arquivadas', format: (v): string | null => (v ? 'incluídas' : null) })
    },
    sort: [{ key: 'data', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<NotificacaoResponse>(
    (pedido) => {
      const { filters, query } = this.lista.request();
      return this.service.listar({
        buscaGlobal: query || undefined,
        categoria: filters.categoria ?? undefined,
        prioridade: filters.prioridade ?? undefined,
        apenasNaoLidas: filters.naoLidas || undefined,
        incluirArquivadas: filters.arquivadas || undefined,
        ...(paginaPadrao(pedido) as { page: number; size: number; sort?: string })
      });
    },
    { errorMessage: getErrorMessage }
  );

  protected readonly colunas: CesDataTableColumn<NotificacaoResponse>[] = [
    { key: 'notificacao', header: 'Notificação', type: 'custom', priority: 1, value: (n) => n.titulo, display: (n) => n.titulo },
    { key: 'prioridade', header: 'Prioridade', type: 'custom', width: '8rem', value: (n) => n.prioridade, display: (n) => n.prioridadeRotulo },
    { key: 'data', header: 'Recebida', type: 'date', sortField: 'dataCriacao', width: '8rem', value: (n) => n.dataCriacao }
  ];
  protected readonly rotulo = (n: NotificacaoResponse) => n.titulo;

  /** Não lidas por categoria (só as que têm alguma) como filtros rápidos. */
  protected readonly resumoCategorias = computed(() =>
    Object.entries(this.store.naoLidasPorCategoria())
      .filter(([, quantidade]) => (quantidade ?? 0) > 0)
      .map(([categoria, quantidade]) => ({
        categoria: categoria as CategoriaNotificacao,
        quantidade: quantidade ?? 0,
        rotulo: CATEGORIA_OPTIONS.find((o) => o.value === categoria)?.label ?? categoria,
        icone: CATEGORIA_ICONES[categoria as CategoriaNotificacao] ?? 'pi pi-bell'
      }))
  );

  /** Leitura alterada nesta tela antes de a lista recarregar (atualização otimista). */
  private readonly leituras = signal<Record<string, boolean>>({});

  protected lida(n: NotificacaoResponse): boolean {
    return this.leituras()[n.id] ?? n.lida;
  }

  protected tom(p: PrioridadeNotificacao) {
    return PRIORIDADE_TOM[p] ?? 'neutral';
  }

  protected quando(n: NotificacaoResponse): string {
    return tempoRelativo(n.dataCriacao);
  }

  protected dataCompleta(n: NotificacaoResponse): string {
    return formatarDataHora(n.dataCriacao);
  }

  protected icone(n: NotificacaoResponse): string {
    return CATEGORIA_ICONES[n.categoria] ?? 'pi pi-bell';
  }

  protected filtrarCategoria(categoria: CategoriaNotificacao): void {
    this.lista.set('categoria', this.lista.applied().categoria === categoria ? null : categoria);
  }

  /** Abrir marca como lida e navega para o registro (quando houver destino). */
  protected abrir(n: NotificacaoResponse): void {
    if (!this.lida(n)) this.marcar(n, true);
    if (n.deeplink) void this.router.navigateByUrl(n.deeplink);
  }

  protected alternarLeitura(n: NotificacaoResponse): void {
    this.marcar(n, !this.lida(n));
  }

  protected arquivar(n: NotificacaoResponse): void {
    this.service.arquivar(n.id).subscribe({
      next: () => {
        this.store.atualizar();
        this.lista.reload();
      },
      error: (e) => this.erro(e)
    });
  }

  protected marcarTodasLidas(): void {
    this.service.marcarTodasLidas().subscribe({
      next: (resumo) => {
        this.store.aplicarResumo(resumo);
        this.lista.reload();
      },
      error: (e) => this.erro(e)
    });
  }

  protected async arquivarLidas(): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: 'Arquivar notificações lidas',
      message: 'As notificações já lidas saem da sua caixa de entrada. Você pode revê-las incluindo as arquivadas nos filtros.',
      confirmLabel: 'Arquivar'
    });
    if (!ok) return;
    this.service.arquivarLidas().subscribe({
      next: (resumo) => {
        this.store.aplicarResumo(resumo);
        this.store.carregarRecentes();
        this.notificacoes.success('Notificações lidas arquivadas.');
        this.lista.reload();
      },
      error: (e) => this.erro(e)
    });
  }

  /** Atualiza a linha na hora e volta atrás se o servidor recusar. */
  private marcar(n: NotificacaoResponse, lida: boolean): void {
    this.leituras.update((l) => ({ ...l, [n.id]: lida }));
    (lida ? this.service.marcarLida(n.id) : this.service.marcarNaoLida(n.id)).subscribe({
      next: () => this.store.carregarResumo(),
      error: (e) => {
        this.leituras.update((l) => ({ ...l, [n.id]: !lida }));
        this.erro(e);
      }
    });
  }

  /** 404 = a notificação sumiu (inclusive de outro usuário): recarrega a lista. */
  private erro(e: unknown): void {
    if ((e as { status?: number })?.status === 404) {
      this.notificacoes.info('Esta notificação não está mais disponível. A lista foi recarregada.');
      this.lista.reload();
      return;
    }
    this.notificacoes.error(getErrorMessage(e));
  }
}
