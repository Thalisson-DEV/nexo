import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import {
  CesBooleanControlComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterPanelComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesMultiSelectComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  cesAttainmentBand,
  cesFilter,
  cesListState
} from '@ces/ui';
import { map } from 'rxjs';

import { ConsultaTurnosFiltros, StatusTurno, TurnoConsulta } from '../../../core/models/dashboard.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { DashboardService } from '../../../core/services/dashboard.service';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, dataIso, dataLocal } from '../../../shared/listagem/listagem';
import { duracao, percentual } from '../dashboards/execucao-dashboards.utils';
import { RecorteDashboardService } from '../dashboards/recorte-dashboard.service';
import { situacaoDoTurno } from './turno-list.utils';

const STATUS: CesSelectOption<StatusTurno | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Em turno', value: 'ABERTO' },
  { label: 'Encerrado', value: 'FECHADO' }
];
/** Padrão: turnos abertos nos últimos 30 dias (a consulta aberta varre o histórico inteiro). */
export const DIAS_PADRAO_TURNOS = 30;

function inicioPadrao(): string {
  const d = new Date();
  d.setDate(d.getDate() - DIAS_PADRAO_TURNOS);
  return dataIso(d)!;
}

@Component({
  selector: 'app-turno-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CurrencyPipe,
    DatePipe,
    CesBooleanControlComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesMultiSelectComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    AuditDialogComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts>
      <ces-page-header
        title="Turnos"
        icon="pi pi-calendar-clock"
        [breadcrumb]="[{ label: 'Execução' }, { label: 'Turnos' }]"
        description="Turnos das equipes: abertura, fechamento, notas lançadas, produção e atingimento da meta do dia."
        helpLabel="Ajuda sobre turnos"
        (help)="ajuda.show()"
      ></ces-page-header>

      <ces-filter-panel #painel storageKey="execucao-turnos" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Equipe" [options]="recorte.opcoesEquipes()" [(value)]="lista.filters.idEquipe" [clearable]="true" [filter]="true"
            [loading]="recorte.carregandoEquipes()" placeholder="Todas"></ces-select>
          <ces-multiselect label="Base operacional" [options]="recorte.opcoesBases()" [(value)]="lista.filters.idBaseOperacional" [clearable]="true"
            selectedItemsLabel="{0} bases" [maxSelectedLabels]="2" [loading]="recorte.carregando()" placeholder="Todas"></ces-multiselect>
          <ces-multiselect label="Processo" [options]="recorte.opcoesProcessos()" [(value)]="lista.filters.idProcesso" [clearable]="true"
            selectedItemsLabel="{0} processos" [maxSelectedLabels]="2" [loading]="recorte.carregando()" placeholder="Todos"></ces-multiselect>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.status"></ces-select>
          <ces-datepicker label="Abertos a partir de" [value]="dataLocal(lista.filters.abertura().from)" [clearable]="true" (valueChange)="abertura('from', $event)"></ces-datepicker>
          <ces-datepicker label="Abertos até" [value]="dataLocal(lista.filters.abertura().to)" [clearable]="true" [minDate]="dataLocal(lista.filters.abertura().from)"
            (valueChange)="abertura('to', $event)"></ces-datepicker>
          <ces-boolean-control label="Só os abertos hoje" kind="toggle" [inline]="true" [value]="lista.filters.apenasHoje()" (valueChange)="lista.filters.apenasHoje.set($event)"></ces-boolean-control>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Turnos"
        mode="server"
        storageKey="execucao-turnos"
        exportFileName="turnos"
        searchPlaceholder="Equipe, placa ou quem abriu"
        rowActionLabel="Relatório"
        rowActionIcon="pi pi-external-link"
        noResultsTitle="Nenhum turno encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os turnos' : 'Nenhum turno aberto'"
        [stateDescription]="resultado.error() ?? 'Os turnos aparecem aqui quando as equipes os abrem no aplicativo de campo.'"
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
        (rowActivate)="verRelatorio($event)"
      >
        <div table-filters class="ces-quick-filters" role="group" aria-label="Atalhos de turnos">
          <button type="button" class="ces-quick-filter" [attr.aria-pressed]="lista.applied().status === 'ABERTO'" (click)="alternarEmTurno()">
            <i class="pi pi-play-circle" aria-hidden="true"></i> Em turno
          </button>
          <button type="button" class="ces-quick-filter" [attr.aria-pressed]="lista.applied().apenasHoje" (click)="lista.set('apenasHoje', !lista.applied().apenasHoje)">
            <i class="pi pi-calendar" aria-hidden="true"></i> Abertos hoje
          </button>
        </div>
        <ng-template cesCell="equipe" let-row>
          <span class="ces-cell-strong">{{ row.codigoEquipe || 'Equipe removida' }}</span>
          <span class="ces-cell-muted">{{ local(row) }}</span>
        </ng-template>
        <ng-template cesCell="abertura" let-row>
          <span>{{ row.dataInicio | date: 'dd/MM/yyyy HH:mm' }}</span>
          @if (row.abertoPor) {
            <span class="ces-cell-muted">por {{ row.abertoPor }}</span>
          }
        </ng-template>
        <ng-template cesCell="fechamento" let-row>
          @if (row.dataFim) {
            {{ row.dataFim | date: 'dd/MM/yyyy HH:mm' }}
          } @else {
            <span class="ces-cell-muted">Em aberto</span>
          }
        </ng-template>
        <ng-template cesCell="status" let-row>
          <ces-status [tone]="situacao(row).tom" [icon]="situacao(row).icone.replace('pi ', '')">{{ situacao(row).rotulo }}</ces-status>
        </ng-template>
        <ng-template cesCell="meta" let-row>
          @if (row.percentualMetaDiaria !== null) {
            <ces-status [tone]="faixa(row.percentualMetaDiaria)">{{ percentual(row.percentualMetaDiaria) }}</ces-status>
            <span class="ces-cell-muted">de {{ row.metaDiaria | currency: 'BRL' }}</span>
          } @else {
            <span class="ces-cell-muted">Sem meta</span>
          }
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button icon="pi pi-print" [label]="'Imprimir turno de ' + rotulo(row)" size="icon-sm" (pressed)="imprimir(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-list" [label]="'Notas do turno de ' + rotulo(row)" size="icon-sm" [disabled]="row.notas === 0" (pressed)="verNotas(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações do turno de ' + rotulo(row)" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <app-audit-dialog #auditoria></app-audit-dialog>
    <app-ajuda-listagem #ajuda titulo="Turnos" entidadePlural="turnos" busca="equipe, placa ou quem abriu" [podeCriar]="false">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-filter"></i> Recorte inicial</h4>
        <p class="help-text-desc">A lista abre nos turnos dos últimos {{ diasPadrao }} dias. Tire o chip de abertura (ou mude as datas) para ver o histórico mais antigo.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-exclamation-triangle"></i> Aberto desde outro dia</h4>
        <p class="help-text-desc">Turno aberto há mais de um dia aparece em amarelo: quase sempre a equipe esqueceu de fechar.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class TurnoListComponent {
  private readonly dashboard = inject(DashboardService);
  private readonly router = inject(Router);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');
  protected readonly recorte = inject(RecorteDashboardService);

  protected readonly statusOpcoes = STATUS;
  protected readonly diasPadrao = DIAS_PADRAO_TURNOS;
  protected readonly dataLocal = dataLocal;
  protected readonly percentual = percentual;
  protected readonly situacao = situacaoDoTurno;
  protected readonly faixa = cesAttainmentBand;

  protected readonly lista = cesListState({
    storageKey: 'execucao-turnos',
    filters: {
      idEquipe: cesFilter.value<string | null>(null, { label: 'Equipe', param: 'equipe', format: (id): string | null => (id ? this.nome(this.recorte.opcoesEquipes(), id) : null) }),
      idBaseOperacional: cesFilter.list<string>([], { label: 'Base', param: 'base', itemLabel: (id) => this.nome(this.recorte.opcoesBases(), id), countLabel: (n) => `${n} bases` }),
      idProcesso: cesFilter.list<string>([], { label: 'Processo', param: 'processo', itemLabel: (id) => this.nome(this.recorte.opcoesProcessos(), id), countLabel: (n) => `${n} processos` }),
      status: cesFilter.value<StatusTurno | null>(null, { label: 'Status', type: 'string', options: () => STATUS }),
      apenasHoje: cesFilter.value<boolean>(false, { label: 'Abertura', param: 'hoje', format: (v): string | null => (v ? 'hoje' : null) }),
      abertura: cesFilter.range({ from: inicioPadrao(), to: null }, { label: 'Abertura' })
    },
    sort: [{ key: 'abertura', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<TurnoConsulta>((pedido) => this.dashboard.consultarTurnos(this.filtro(pedido)), { errorMessage: getErrorMessage });
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = (_evento: unknown, limite: number) =>
    this.dashboard.consultarTurnos({ ...this.filtro(this.lista.request()), page: 0, size: limite }).pipe(map((r) => r.content));

  protected readonly colunas: CesDataTableColumn<TurnoConsulta>[] = [
    { key: 'equipe', header: 'Equipe', type: 'custom', priority: 1, value: (t) => t.codigoEquipe, display: (t) => t.codigoEquipe ?? 'Equipe removida' },
    { key: 'abertura', header: 'Abertura', type: 'custom', priority: 1, sortField: 'dataInicio', value: (t) => t.dataInicio, display: (t) => new Date(t.dataInicio).toLocaleString('pt-BR') },
    { key: 'fechamento', header: 'Fechamento', type: 'custom', sortField: 'dataFim', value: (t) => t.dataFim, display: (t) => (t.dataFim ? new Date(t.dataFim).toLocaleString('pt-BR') : 'Em aberto') },
    { key: 'duracao', header: 'Duração', value: (t) => t.duracaoMinutos, display: (t) => duracao(t.duracaoMinutos) },
    { key: 'status', header: 'Status', type: 'custom', priority: 1, sortField: 'status', value: (t) => t.status, display: (t) => situacaoDoTurno(t).rotulo },
    { key: 'notas', header: 'Notas', type: 'integer', value: (t) => t.notas },
    { key: 'producao', header: 'Produção', type: 'currency', value: (t) => t.producao },
    { key: 'meta', header: 'Meta do dia', type: 'custom', value: (t) => t.percentualMetaDiaria, display: (t) => (t.percentualMetaDiaria !== null ? percentual(t.percentualMetaDiaria) : 'Sem meta') }
  ];
  protected readonly rotulo = (t: TurnoConsulta) => t.codigoEquipe ?? 'equipe removida';

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.recorte.carregar(destroyRef);
    this.recorte.carregarEquipes(destroyRef);
  }

  private nome(opcoes: readonly CesSelectOption<string>[], id: string): string {
    return opcoes.find((o) => o.value === id)?.label ?? 'selecionado';
  }

  private filtro(pedido: { page: number; size: number; sort: readonly { key: string; direction: string }[] }): ConsultaTurnosFiltros {
    const { filters, query } = this.lista.request();
    return {
      idEquipe: filters.idEquipe ?? undefined,
      idBaseOperacional: filters.idBaseOperacional.length ? filters.idBaseOperacional : undefined,
      idProcesso: filters.idProcesso.length ? filters.idProcesso : undefined,
      status: filters.status ?? undefined,
      apenasHoje: filters.apenasHoje || undefined,
      aberturaDe: filters.abertura.from ?? undefined,
      aberturaAte: filters.abertura.to ?? undefined,
      buscaGlobal: query || undefined,
      page: pedido.page,
      size: pedido.size,
      sort: pedido.sort.length ? pedido.sort.map((r) => `${r.key},${r.direction}`) : ['dataInicio,desc']
    };
  }

  protected local(t: TurnoConsulta): string {
    return [t.baseOperacional, t.processo].filter(Boolean).join(' · ');
  }

  protected abertura(limite: 'from' | 'to', data: Date | null): void {
    this.lista.filters.abertura.update((atual) => ({ ...atual, [limite]: dataIso(data) }));
  }

  protected alternarEmTurno(): void {
    this.lista.set('status', this.lista.applied().status === 'ABERTO' ? null : 'ABERTO');
  }

  protected verRelatorio(t: TurnoConsulta): void {
    window.open(this.router.serializeUrl(this.router.createUrlTree(['/execucao/turnos', t.id, 'detalhamento'])), '_blank');
  }

  protected imprimir(t: TurnoConsulta): void {
    void this.router.navigate(['/execucao/turnos', t.id, 'detalhamento'], { queryParams: { mode: 'print' } });
  }

  protected verNotas(t: TurnoConsulta): void {
    void this.router.navigate(['/execucao/comercial'], { queryParams: { idTurno: t.id } });
  }

  protected logs(t: TurnoConsulta): void {
    this.auditoria().show(t.id, `Turno ${t.codigoEquipe ?? ''}`, { status: 'Status', dataInicio: 'Abertura', dataFim: 'Fechamento', idVeiculo: 'Veículo' });
  }
}
