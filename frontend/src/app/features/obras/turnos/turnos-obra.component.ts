import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  CesButtonComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterPanelComponent,
  CesListShortcutsDirective,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  cesAttainmentBand,
  cesFilter,
  cesListState
} from '@ces/ui';
import { map, take, tap } from 'rxjs';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FiltroTurnosObra, IndicadoresTurnosObra, MetaTurnoFiltro, StatusTurnoObra, TurnoObraDetalhe, TurnoObraLinha } from '../../../core/models/obras-turnos.models';
import { ObrasTurnosService } from '../../../core/services/obras-turnos.service';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, dataIso, dataLocal } from '../../../shared/listagem/listagem';
import { duracao, percentual } from '../../execucao/dashboards/execucao-dashboards.utils';
import { RecorteDashboardService } from '../../execucao/dashboards/recorte-dashboard.service';
import { situacaoDoTurno } from '../../execucao/turno-list/turno-list.utils';
import { formatarMoeda } from '../obras-ui';
import { horasTotais } from './turnos-obra.logic';

const STATUS: CesSelectOption<StatusTurnoObra | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Em turno', value: 'ABERTO' },
  { label: 'Encerrado', value: 'FECHADO' }
];
const METAS: CesSelectOption<MetaTurnoFiltro | null>[] = [
  { label: 'Todas', value: null },
  { label: 'Bateu a meta', value: 'BATEU' },
  { label: 'Não bateu a meta', value: 'NAO_BATEU' }
];
const DIAS_PADRAO = 30;

function inicioPadrao(): string {
  const d = new Date();
  d.setDate(d.getDate() - DIAS_PADRAO);
  return dataIso(d)!;
}

/** Turnos das equipes de Obras, no molde da listagem de turnos do comercial, com o executado vindo dos apontamentos. */
@Component({
  selector: 'app-turnos-obra',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CurrencyPipe,
    DatePipe,
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesListShortcutsDirective,
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AjudaListagemComponent,
  ],
  template: `
    <div class="ces-page" cesListShortcuts>
      <ces-page-header title="Turnos" icon="pi pi-calendar-clock"
        [breadcrumb]="[{ label: 'Obras' }, { label: 'Turnos' }]"
        description="Jornadas das equipes de Obras: quem trabalhou, em quais obras, quanto executou contra a meta do dia e o que foi registrado."
        helpLabel="Ajuda sobre turnos" (help)="ajuda.show()">
        <ces-button actions label="Boletim" icon="pi pi-th-large" variant="outline" (pressed)="irParaBoletim()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="obras-turnos" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Equipe" [options]="equipes()" [(value)]="lista.filters.idEquipe" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-text-input label="Obra" placeholder="Número da obra" [value]="lista.filters.obra()" (valueChange)="lista.filters.obra.set($event)"></ces-text-input>
          <ces-multiselect label="Base operacional" [options]="recorte.opcoesBases()" [(value)]="lista.filters.idBaseOperacional" [clearable]="true"
            selectedItemsLabel="{0} bases" [maxSelectedLabels]="2" [loading]="recorte.carregando()" placeholder="Todas"></ces-multiselect>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.status"></ces-select>
          <ces-select label="Meta do dia" [options]="metaOpcoes" [(value)]="lista.filters.meta"></ces-select>
          <ces-datepicker label="Abertos a partir de" [value]="dataLocal(lista.filters.abertura().from)" [clearable]="true" (valueChange)="abertura('from', $event)"></ces-datepicker>
          <ces-datepicker label="Abertos até" [value]="dataLocal(lista.filters.abertura().to)" [clearable]="true" [minDate]="dataLocal(lista.filters.abertura().from)"
            (valueChange)="abertura('to', $event)"></ces-datepicker>
        </div>
      </ces-filter-panel>

      <section class="ces-kpis turnos-kpis" aria-label="Indicadores dos turnos" aria-live="polite">
        <button type="button" class="ces-kpi" data-tone="info" [attr.aria-pressed]="semAtalho()" (click)="limparAtalhos()">
          <span class="ces-kpi__icon"><i class="pi pi-calendar-clock" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Turnos</span>
            <span class="ces-kpi__value">{{ ind().turnos }}</span>
            <span class="ces-kpi__detail">{{ horas(ind().minutosTotais) }} no total</span></span>
        </button>
        <button type="button" class="ces-kpi" [attr.data-tone]="ind().emAndamento ? 'success' : 'neutral'" [attr.aria-pressed]="lista.applied().status === 'ABERTO'" (click)="alternarStatus('ABERTO')">
          <span class="ces-kpi__icon"><i class="pi pi-play-circle" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Em andamento</span>
            <span class="ces-kpi__value">{{ ind().emAndamento }}</span>
            <span class="ces-kpi__detail">Ainda não fechados</span></span>
        </button>
        <button type="button" class="ces-kpi" [attr.data-tone]="ind().comMeta && ind().bateuMeta === ind().comMeta ? 'success' : 'neutral'" [attr.aria-pressed]="lista.applied().meta === 'BATEU'" (click)="alternarMeta('BATEU')">
          <span class="ces-kpi__icon"><i class="pi pi-flag" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Bateu a meta</span>
            <span class="ces-kpi__value">{{ ind().bateuMeta }} de {{ ind().comMeta }}</span>
            <span class="ces-kpi__detail">Turnos com meta no dia</span></span>
        </button>
        <button type="button" class="ces-kpi" [attr.data-tone]="ind().semRelatorio ? 'warning' : 'neutral'" [attr.aria-pressed]="lista.applied().semRelatorio" (click)="lista.set('semRelatorio', !lista.applied().semRelatorio)">
          <span class="ces-kpi__icon"><i class="pi pi-file-excel" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Sem relatório</span>
            <span class="ces-kpi__value">{{ ind().semRelatorio }}</span>
            <span class="ces-kpi__detail">Fechados sem relatório diário</span></span>
        </button>
        <button type="button" class="ces-kpi" [attr.data-tone]="ind().comOcorrencias ? 'danger' : 'neutral'" [attr.aria-pressed]="lista.applied().comOcorrencia" (click)="lista.set('comOcorrencia', !lista.applied().comOcorrencia)">
          <span class="ces-kpi__icon"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Com ocorrências</span>
            <span class="ces-kpi__value">{{ ind().comOcorrencias }}</span>
            <span class="ces-kpi__detail">Turnos com algum registro</span></span>
        </button>
        <div class="ces-kpi" data-tone="neutral">
          <span class="ces-kpi__icon"><i class="pi pi-check-square" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Serviços executados</span>
            <span class="ces-kpi__value">{{ ind().servicosExecutados }}</span>
            <span class="ces-kpi__detail">{{ moeda(ind().valorExecutado) }}</span></span>
        </div>
      </section>

      <ces-data-table title="Turnos" mode="server" storageKey="obras-turnos" exportFileName="turnos-obras"
        searchPlaceholder="Equipe, placa, quem abriu ou obra" rowActionLabel="Ver turno" rowActionIcon="pi pi-angle-right"
        noResultsTitle="Nenhum turno encontrado com esse recorte."
        [columns]="colunas" [rows]="resultado.rows()" [totalRecords]="resultado.total()" [rowLabel]="rotulo" [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os turnos' : 'Nenhum turno de Obras ainda'"
        [stateDescription]="resultado.error() ?? 'Os turnos aparecem aqui quando a equipe abre pelo aplicativo ou quando o supervisor abre em nome dela.'"
        [filtered]="lista.filtered()" [exportRows]="exportar" [exportLimit]="limiteExportacao"
        [(page)]="lista.page" [(pageSize)]="lista.pageSize" [(sort)]="lista.sort" [(query)]="lista.query"
        (lazyLoad)="lista.load($event)" (adjustFilters)="painel.expandAndFocus()" (clearFilters)="lista.reset()" (rowActivate)="ver($event)">
        <ng-template cesCell="equipe" let-row>
          <span class="ces-cell-strong">{{ row.codigoEquipe || 'Equipe removida' }}</span>
          <span class="ces-cell-muted">{{ local(row) }}</span>
        </ng-template>
        <ng-template cesCell="abertura" let-row>
          <span>{{ row.dataInicio | date: 'dd/MM/yyyy HH:mm' }}</span>
          @if (row.abertoPor) { <span class="ces-cell-muted">por {{ row.abertoPor }}</span> }
        </ng-template>
        <ng-template cesCell="fechamento" let-row>
          @if (row.dataFim) { {{ row.dataFim | date: 'dd/MM/yyyy HH:mm' }} } @else { <span class="ces-cell-muted">Em aberto</span> }
        </ng-template>
        <ng-template cesCell="status" let-row>
          <ces-status [tone]="situacao(row).tom" [icon]="situacao(row).icone.replace('pi ', '')">{{ situacao(row).rotulo }}</ces-status>
        </ng-template>
        <ng-template cesCell="obras" let-row>
          @if (row.obras.length) {
            <span [title]="row.obras.join(', ')">{{ row.obras[0] }}{{ row.obras.length > 1 ? ' +' + (row.obras.length - 1) : '' }}</span>
          } @else { <span class="ces-cell-muted">Sem atividade</span> }
        </ng-template>
        <ng-template cesCell="meta" let-row>
          <span>{{ row.realizado | currency: 'BRL' }}</span>
          @if (row.percentualMeta != null) {
            <span class="turnos-meta"><ces-status [tone]="faixa(row.percentualMeta)">{{ percentual(row.percentualMeta) }}</ces-status> <span class="ces-cell-muted">de {{ row.metaDiaria | currency: 'BRL' }}</span></span>
          } @else { <span class="ces-cell-muted">Sem meta</span> }
        </ng-template>
        <ng-template cesCell="registros" let-row>
          <span class="turnos-registros">
            @if (row.semRelatorio) { <ces-status tone="warning" icon="pi-file-excel">Sem relatório</ces-status> }
            @if (row.ocorrencias) { <ces-status tone="danger" icon="pi-exclamation-triangle">{{ row.ocorrencias === 1 ? '1 ocorrência' : row.ocorrencias + ' ocorrências' }}</ces-status> }
            @if (row.aprs) { <ces-status [tone]="row.aprsEmRascunho ? 'warning' : 'neutral'" icon="pi-shield">{{ rotuloApr(row) }}</ces-status> }
            @if (row.substitutos) { <ces-status tone="info" icon="pi-sync">{{ row.substitutos === 1 ? '1 substituto' : row.substitutos + ' substitutos' }}</ces-status> }
            @if (!row.semRelatorio && !row.ocorrencias && !row.aprs && !row.substitutos) { <span class="ces-cell-muted">—</span> }
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <app-ajuda-listagem #ajuda titulo="Turnos de Obras" entidadePlural="turnos" busca="equipe, placa, quem abriu ou obra" [podeCriar]="false">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-filter"></i> Recorte e indicadores</h4>
        <p class="help-text-desc">A lista abre nos últimos {{ diasPadrao }} dias. Os indicadores resumem o recorte de equipe, base, obra e período. Clique em um deles para ver só aqueles turnos: os números continuam os mesmos.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-wallet"></i> De onde vem o realizado</h4>
        <p class="help-text-desc">O realizado é a soma dos apontamentos do turno, pelo valor congelado em cada programação. Material não tem valor.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-clock"></i> Encerrado pelo sistema</h4>
        <p class="help-text-desc">Turno ainda aberto no horário de corte é encerrado pelo sistema, como no comercial. Sem nota lançada, o fim fica igual à abertura.</p>
      </div>
    </app-ajuda-listagem>
  `,
  styles: `
    .turnos-kpis { margin-bottom: 1rem; }
    .turnos-meta { display: flex; flex-wrap: wrap; gap: 0.25rem; align-items: center; }
    .turnos-registros { display: flex; flex-wrap: wrap; gap: 0.25rem; }
  `
})
export class TurnosObraComponent {
  private readonly api = inject(ObrasTurnosService);
  private readonly router = inject(Router);
  protected readonly recorte = inject(RecorteDashboardService);

  protected readonly statusOpcoes = STATUS;
  protected readonly metaOpcoes = METAS;
  protected readonly diasPadrao = DIAS_PADRAO;
  protected readonly dataLocal = dataLocal;
  protected readonly percentual = percentual;
  protected readonly situacao = situacaoDoTurno;
  protected readonly faixa = cesAttainmentBand;
  protected readonly horas = horasTotais;
  protected readonly moeda = formatarMoeda;
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly equipes = signal<CesSelectOption<string>[]>([]);
  private readonly indicadores = signal<IndicadoresTurnosObra | null>(null);

  protected readonly lista = cesListState({
    storageKey: 'obras-turnos',
    filters: {
      idEquipe: cesFilter.value<string | null>(null, { label: 'Equipe', param: 'equipe', format: (id): string | null => (id ? this.nome(this.equipes(), id) : null) }),
      idBaseOperacional: cesFilter.list<string>([], { label: 'Base', param: 'base', itemLabel: (id) => this.nome(this.recorte.opcoesBases(), id), countLabel: (n) => `${n} bases` }),
      obra: cesFilter.value<string>('', { label: 'Obra', param: 'obra', format: (v): string | null => v || null }),
      status: cesFilter.value<StatusTurnoObra | null>(null, { label: 'Status', type: 'string', options: () => STATUS }),
      meta: cesFilter.value<MetaTurnoFiltro | null>(null, { label: 'Meta', type: 'string', options: () => METAS }),
      semRelatorio: cesFilter.value<boolean>(false, { label: 'Relatório', param: 'semRelatorio', format: (v): string | null => (v ? 'sem relatório' : null) }),
      comOcorrencia: cesFilter.value<boolean>(false, { label: 'Ocorrências', param: 'comOcorrencia', format: (v): string | null => (v ? 'com ocorrências' : null) }),
      abertura: cesFilter.range({ from: inicioPadrao(), to: null }, { label: 'Abertura' })
    },
    sort: [{ key: 'abertura', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<TurnoObraLinha>(
    (pedido) => this.api.listar(this.filtro(pedido)).pipe(tap((r) => this.indicadores.set(r.indicadores))),
    { errorMessage: getErrorMessage }
  );
  protected readonly exportar = (_evento: unknown, limite: number) =>
    this.api.listar({ ...this.filtro(this.lista.request()), page: 0, size: limite }).pipe(map((r) => r.content));

  protected readonly ind = computed<IndicadoresTurnosObra>(() =>
    this.indicadores() ?? { turnos: 0, emAndamento: 0, minutosTotais: 0, comMeta: 0, bateuMeta: 0, semRelatorio: 0, comOcorrencias: 0, servicosExecutados: 0, valorExecutado: 0 }
  );
  protected readonly semAtalho = computed(() => {
    const a = this.lista.applied();
    return !a.status && !a.meta && !a.semRelatorio && !a.comOcorrencia;
  });

  protected readonly colunas: CesDataTableColumn<TurnoObraLinha>[] = [
    { key: 'equipe', header: 'Equipe', type: 'custom', priority: 1, sortField: 'equipe', value: (t) => t.codigoEquipe, display: (t) => t.codigoEquipe ?? 'Equipe removida' },
    { key: 'abertura', header: 'Abertura', type: 'custom', priority: 1, sortField: 'dataInicio', value: (t) => t.dataInicio, display: (t) => new Date(t.dataInicio).toLocaleString('pt-BR') },
    { key: 'fechamento', header: 'Fechamento', type: 'custom', sortField: 'dataFim', value: (t) => t.dataFim, display: (t) => (t.dataFim ? new Date(t.dataFim).toLocaleString('pt-BR') : 'Em aberto') },
    { key: 'duracao', header: 'Duração', sortField: 'duracao', value: (t) => t.duracaoMinutos, display: (t) => duracao(t.duracaoMinutos) },
    { key: 'status', header: 'Status', type: 'custom', priority: 1, sortField: 'status', value: (t) => t.status, display: (t) => situacaoDoTurno(t).rotulo },
    { key: 'obras', header: 'Obras', type: 'custom', value: (t) => t.obras.join(', '), display: (t) => t.obras.join(', ') },
    { key: 'meta', header: 'Realizado × meta', type: 'custom', sortField: 'meta', value: (t) => t.realizado, display: (t) => (t.percentualMeta != null ? `${formatarMoeda(t.realizado)} (${percentual(t.percentualMeta)})` : formatarMoeda(t.realizado)) },
    { key: 'registros', header: 'Registros', type: 'custom', value: (t) => t.ocorrencias, display: (t) => `${t.semRelatorio ? 'Sem relatório; ' : ''}${t.ocorrencias} ocorrências; ${t.aprs} APRs` }
  ];
  protected readonly rotulo = (t: TurnoObraLinha) => t.codigoEquipe ?? 'equipe removida';

  constructor() {
    this.recorte.carregar(inject(DestroyRef));
    this.api.boletim().pipe(take(1)).subscribe((b) => this.equipes.set(b.equipes.map((e) => ({ value: e.idEquipe, label: e.codigo }))));
  }

  private nome(opcoes: readonly CesSelectOption<string>[], id: string): string {
    return opcoes.find((o) => o.value === id)?.label ?? 'selecionada';
  }

  private filtro(pedido: { page: number; size: number; sort: readonly { key: string; direction: string }[] }): FiltroTurnosObra {
    const { filters, query } = this.lista.request();
    return {
      idEquipe: filters.idEquipe ?? undefined,
      idBaseOperacional: filters.idBaseOperacional.length ? filters.idBaseOperacional : undefined,
      obra: filters.obra || undefined,
      status: filters.status ?? undefined,
      meta: filters.meta ?? undefined,
      semRelatorio: filters.semRelatorio || undefined,
      comOcorrencia: filters.comOcorrencia || undefined,
      de: filters.abertura.from ?? undefined,
      ate: filters.abertura.to ?? undefined,
      q: query || undefined,
      page: pedido.page,
      size: pedido.size,
      sort: pedido.sort.length ? pedido.sort.map((r) => `${r.key},${r.direction}`) : ['dataInicio,desc']
    };
  }

  protected local(t: TurnoObraLinha): string {
    return [t.baseOperacional, t.placa, `${t.membros} ${t.membros === 1 ? 'pessoa' : 'pessoas'}`].filter(Boolean).join(' · ');
  }

  protected rotuloApr(t: TurnoObraLinha): string {
    const base = t.aprs === 1 ? '1 APR' : `${t.aprs} APRs`;
    return t.aprsEmRascunho ? `${base} (${t.aprsEmRascunho} em rascunho)` : base;
  }

  protected abertura(limite: 'from' | 'to', data: Date | null): void {
    this.lista.filters.abertura.update((atual) => ({ ...atual, [limite]: dataIso(data) }));
  }

  protected alternarStatus(status: StatusTurnoObra): void {
    this.lista.set('status', this.lista.applied().status === status ? null : status);
  }

  protected alternarMeta(meta: MetaTurnoFiltro): void {
    this.lista.set('meta', this.lista.applied().meta === meta ? null : meta);
  }

  protected limparAtalhos(): void {
    this.lista.set('status', null);
    this.lista.set('meta', null);
    this.lista.set('semRelatorio', false);
    this.lista.set('comOcorrencia', false);
  }

  protected irParaBoletim(): void {
    void this.router.navigate(['/obras/boletim']);
  }

  protected ver(t: Pick<TurnoObraDetalhe, 'id'>): void {
    void this.router.navigate(['/obras/turnos', t.id]);
  }
}
