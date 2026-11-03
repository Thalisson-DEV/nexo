import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import {
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterPanelComponent,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  cesFilter,
  cesListState
} from '@ces/ui';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { getErrorMessage } from '../../../core/models/shared.models';
import { EventoProgramacao, TipoEventoProgramacao } from '../../../core/models/obras-programacao.models';
import { ObrasProgramacaoService } from '../../../core/services/obras-programacao.service';
import { LIMITE_EXPORTACAO, dataIso, dataLocal, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';
import { formatarDataHora } from '../obras-ui';
import { ProgramacaoDetalheComponent } from './programacao-detalhe.component';
import { TIPO_EVENTO } from './programacao.logic';

const ORDEM_TIPOS: TipoEventoProgramacao[] = ['REPROGRAMADA', 'EDITADA', 'CANCELADA', 'EXCLUIDA'];
const TIPOS: CesSelectOption<TipoEventoProgramacao>[] = ORDEM_TIPOS.map((value) => ({ value, label: TIPO_EVENTO[value].rotulo }));

type MudouFiltro = 'EQUIPE' | 'PERIODO';
const OPCOES_MUDOU: CesSelectOption<MudouFiltro | null>[] = [
  { label: 'Qualquer mudança', value: null },
  { label: 'Trocou de equipe', value: 'EQUIPE' },
  { label: 'Mudou o período', value: 'PERIODO' }
];

function periodo(inicio?: string | null, fim?: string | null): string {
  return inicio ? `${inicio} a ${fim}` : '—';
}

/** Tudo o que mudou nas programações depois de criadas. */
@Component({
  selector: 'app-programacao-historico',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    ProgramacaoDetalheComponent
  ],
  template: `
    <div class="ces-page">
      <ces-page-header title="Histórico de reprogramações" icon="pi pi-history"
        [breadcrumb]="[{ label: 'Obras' }, { label: 'Programação', route: '/obras/programacao' }, { label: 'Histórico' }]"
        description="Edições, reprogramações, cancelamentos e exclusões de programações."></ces-page-header>

      <ces-filter-panel #painel storageKey="obras-programacao-historico" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-multiselect label="Tipo" [options]="tipos" [(value)]="lista.filters.tipos" [filter]="false" placeholder="Todos"></ces-multiselect>
          <ces-select label="O que mudou" [options]="opcoesMudou" [(value)]="lista.filters.mudou"></ces-select>
          <ces-text-input label="Obra" [(value)]="lista.filters.obra" placeholder="Número da obra"></ces-text-input>
          <ces-text-input label="Equipe" [(value)]="lista.filters.equipe" placeholder="De origem ou de destino"></ces-text-input>
          <ces-text-input label="Usuário" [(value)]="lista.filters.usuario" placeholder="Quem alterou"></ces-text-input>
          <ces-datepicker label="Alteradas a partir de" [value]="dataLocal(lista.filters.periodo().from)" [clearable]="true" (valueChange)="periodo('from', $any($event))"></ces-datepicker>
          <ces-datepicker label="Alteradas até" [value]="dataLocal(lista.filters.periodo().to)" [clearable]="true" (valueChange)="periodo('to', $any($event))"></ces-datepicker>
        </div>
      </ces-filter-panel>

      <div class="ces-quick-filters hist-atalhos" role="group" aria-label="Mostrar só os tipos escolhidos">
        <button type="button" class="ces-quick-filter" [attr.aria-pressed]="!tiposAplicados().length" (click)="definirTipos([])">
          Todos <span class="hist-contagem">{{ contagemTotal() ?? '–' }}</span>
        </button>
        <span class="hist-divisor" aria-hidden="true"></span>
        @for (t of ordemTipos; track t) {
          <button type="button" class="ces-quick-filter" [attr.aria-pressed]="tiposAplicados().includes(t)" (click)="alternarTipo(t)" [attr.aria-label]="tipoEvento[t].rotulo + ': ' + (contagens()[t] ?? 'contagem indisponível')">
            <i [class]="tipoEvento[t].icone" aria-hidden="true"></i> {{ tipoEvento[t].rotulo }} <span class="hist-contagem">{{ contagens()[t] ?? '–' }}</span>
          </button>
        }
      </div>

      <ces-data-table
        title="Eventos"
        mode="server"
        storageKey="obras-programacao-historico"
        exportFileName="historico-programacoes"
        searchPlaceholder="Obra, equipe, programação, usuário ou observação"
        rowActionLabel="Abrir programação"
        rowActionIcon="pi pi-eye"
        noResultsTitle="Nenhum evento com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar o histórico' : 'Nenhuma programação foi alterada ainda'"
        [stateDescription]="resultado.error() ?? 'Edições, reprogramações, cancelamentos e exclusões aparecem aqui.'"
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
        (rowActivate)="abrir($event)"
      >
        <ng-template cesCell="tipo" let-row>
          <ces-status [tone]="tipoDe(row).tom" [icon]="tipoDe(row).icone">{{ tipoDe(row).rotulo }}</ces-status>
        </ng-template>
      </ces-data-table>
    </div>

    <app-programacao-detalhe [(aberto)]="detalheAberto" [idProgramacao]="idDetalhe()" (alterada)="lista.reload()"></app-programacao-detalhe>
  `,
  styles: [`
    .hist-atalhos { margin-bottom: 0.75rem; }
    .hist-contagem { display: inline-flex; align-items: center; justify-content: center; min-width: 1.375rem; height: 1.25rem; padding: 0 0.375rem; border-radius: 999px; background: var(--ces-surface-muted); color: var(--ces-foreground-muted); font-size: 0.7rem; font-weight: 700; font-variant-numeric: tabular-nums; }
    .ces-quick-filter[aria-pressed='true'] .hist-contagem { background: var(--ces-surface); color: var(--ces-foreground); }
    .hist-divisor { width: 1px; align-self: stretch; margin: 0.25rem 0.125rem; background: var(--ces-border); }
    .hist-atalhos .pi { font-size: 0.75rem; }
  `]
})
export class ProgramacaoHistoricoComponent {
  private readonly api = inject(ObrasProgramacaoService);

  protected readonly tipos = TIPOS;
  protected readonly ordemTipos = ORDEM_TIPOS;
  protected readonly tipoEvento = TIPO_EVENTO;
  protected readonly opcoesMudou = OPCOES_MUDOU;
  protected readonly dataLocal = dataLocal;
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly detalheAberto = signal(false);
  protected readonly idDetalhe = signal<string | null>(null);

  protected readonly lista = cesListState({
    storageKey: 'obras-programacao-historico',
    filters: {
      tipos: cesFilter.list<TipoEventoProgramacao>([], { label: 'Tipo', param: 'tipo', itemLabel: (t) => TIPO_EVENTO[t].rotulo }),
      mudou: cesFilter.value<MudouFiltro | null>(null, { label: 'O que mudou', param: 'mudou', options: () => OPCOES_MUDOU }),
      obra: cesFilter.value<string>('', { label: 'Obra', param: 'obra' }),
      equipe: cesFilter.value<string>('', { label: 'Equipe', param: 'equipe' }),
      usuario: cesFilter.value<string>('', { label: 'Usuário', param: 'usuario' }),
      periodo: cesFilter.range({ from: null, to: null }, { label: 'Alteração' })
    },
    sort: [{ key: 'dataCriacao', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<EventoProgramacao>(
    (pedido) => this.api.historico({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly exportar = exportacaoPadrao((pagina) => this.api.historico({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<EventoProgramacao>[] = [
    { key: 'data', header: 'Data e hora', sortField: 'dataCriacao', priority: 1, value: (e) => e.data, display: (e) => formatarDataHora(e.data) },
    { key: 'tipo', header: 'Tipo', type: 'custom', sortField: 'tipo', priority: 1, value: (e) => TIPO_EVENTO[e.tipo].rotulo },
    { key: 'numeroProgramacao', header: 'Programação', sortField: 'numeroProgramacao', priority: 1, value: (e) => e.numeroProgramacao, display: (e) => `#${e.numeroProgramacao}` },
    { key: 'numeroObra', header: 'Obra', sortField: 'numeroObra', priority: 1, value: (e) => e.numeroObra },
    { key: 'equipeOrigem', header: 'Equipe de origem', sortField: 'equipeOrigem', value: (e) => e.equipeOrigem },
    { key: 'equipeDestino', header: 'Equipe de destino', sortField: 'equipeDestino', value: (e) => e.equipeDestino },
    { key: 'periodoOriginal', header: 'Período original', value: (e) => periodo(e.inicioOriginal, e.fimOriginal) },
    { key: 'periodoNovo', header: 'Período novo', value: (e) => periodo(e.inicioNovo, e.fimNovo) },
    { key: 'usuario', header: 'Usuário', sortField: 'usuarioCriacao', value: (e) => e.usuario },
    { key: 'observacao', header: 'Observação', value: (e) => e.observacao }
  ];

  protected readonly rotulo = (e: EventoProgramacao): string => `Programação ${e.numeroProgramacao}, ${TIPO_EVENTO[e.tipo].rotulo}`;

  protected readonly tiposAplicados = computed(() => this.lista.applied().tipos);
  /** Quantos eventos há de cada tipo no recorte, ignorando os atalhos, para o número não sumir ao filtrar. */
  protected readonly contagens = signal<Partial<Record<TipoEventoProgramacao, number>>>({});
  protected readonly contagemTotal = computed(() => {
    const c = this.contagens();
    return ORDEM_TIPOS.every((t) => c[t] !== undefined) ? ORDEM_TIPOS.reduce((soma, t) => soma + (c[t] ?? 0), 0) : null;
  });

  constructor() {
    const consulta = computed(() => {
      const { tipos: _tipos, ...semTipo } = this.filtro();
      return semTipo;
    }, { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) });
    toObservable(consulta)
      .pipe(
        switchMap((filtro) => forkJoin(ORDEM_TIPOS.map((tipo) =>
          this.api.historico({ ...filtro, tipos: [tipo], page: 0, size: 1 }).pipe(
            map((r) => [tipo, r.page?.totalElements ?? 0] as const),
            catchError(() => of([tipo, undefined] as const))
          )
        ))),
        takeUntilDestroyed()
      )
      .subscribe((pares) => this.contagens.set(Object.fromEntries(pares.filter(([, n]) => n !== undefined))));
  }

  private filtro() {
    const { filters: f, query } = this.lista.request();
    return {
      tipos: f.tipos,
      mudou: f.mudou,
      obra: f.obra.trim() || null,
      equipe: f.equipe.trim() || null,
      usuario: f.usuario.trim() || null,
      de: f.periodo.from,
      ate: f.periodo.to,
      q: query || null
    };
  }

  protected definirTipos(tipos: readonly TipoEventoProgramacao[]): void {
    this.lista.set('tipos', [...tipos]);
  }

  /** Atalho: liga ou desliga o tipo, somando aos já escolhidos. */
  protected alternarTipo(tipo: TipoEventoProgramacao): void {
    const atuais = this.tiposAplicados();
    const proximos = atuais.includes(tipo) ? atuais.filter((t) => t !== tipo) : [...atuais, tipo];
    this.definirTipos(ORDEM_TIPOS.filter((t) => proximos.includes(t)));
  }

  protected periodo(ponta: 'from' | 'to', data: Date | null): void {
    const atual = this.lista.filters.periodo();
    const valor = dataIso(data);
    if (atual[ponta] !== valor) this.lista.filters.periodo.set({ ...atual, [ponta]: valor });
  }

  protected tipoDe(evento: EventoProgramacao) {
    return TIPO_EVENTO[evento.tipo];
  }

  protected abrir(evento: EventoProgramacao): void {
    if (evento.tipo === 'EXCLUIDA') return;
    this.idDetalhe.set(evento.idProgramacao);
    this.detalheAberto.set(true);
  }
}
