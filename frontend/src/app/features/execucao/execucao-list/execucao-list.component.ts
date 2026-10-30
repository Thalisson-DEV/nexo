import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  CesAutocompleteComponent,
  CesBooleanControlComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterPanelComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  cesFilter,
  cesListState
} from '@ces/ui';
import { map } from 'rxjs';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup } from '../../../core/lookup/store/remote-lookup';
import {
  ExecucaoComercialResponse,
  ExecucaoFilters,
  OperacaoMaterial,
  StatusExecucao,
  TIPOS_NOTA,
  TIPO_SERVICO_NAO_INFORMADO,
  TipoNota,
  rotuloTipoServico
} from '../../../core/models/execucao.models';
import { generateReferenceOptions, getErrorMessage } from '../../../core/models/shared.models';
import { ContratoService } from '../../../core/services/contrato.service';
import { ExecucaoService } from '../../../core/services/execucao.service';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, dataIso, dataLocal } from '../../../shared/listagem/listagem';
import { RecorteDashboardService } from '../dashboards/recorte-dashboard.service';
import { STATUS_EXECUCAO } from './execucao-list.utils';
import { NumeroOficialDialogComponent } from './numero-oficial-dialog.component';

const STATUS: CesSelectOption<StatusExecucao | null>[] = [
  { label: 'Todos', value: null },
  ...(Object.keys(STATUS_EXECUCAO) as StatusExecucao[]).map((s) => ({ label: STATUS_EXECUCAO[s].rotulo, value: s }))
];
const OPERACOES: CesSelectOption<OperacaoMaterial | null>[] = [
  { label: 'Todas', value: null },
  { label: 'Aplicado', value: 'APLICADO' },
  { label: 'Retirado', value: 'RETIRADO' }
];
const TIPOS_LANCAMENTO: CesSelectOption<TipoNota | null>[] = [
  { label: 'Todos', value: null },
  ...(Object.keys(TIPOS_NOTA) as TipoNota[]).map((t) => ({ label: TIPOS_NOTA[t].rotulo, value: t }))
];
/** Valor do filtro "Tipo de serviço" para as notas lançadas sem tipo. */
export const SEM_TIPO_SERVICO = 'sem-tipo';

/** Padrão: lançamentos dos últimos 30 dias (a consulta aberta varre o histórico inteiro). */
export const DIAS_PADRAO_EXECUCOES = 30;

function inicioPadrao(): string {
  const d = new Date();
  d.setDate(d.getDate() - DIAS_PADRAO_EXECUCOES);
  return dataIso(d)!;
}

@Component({
  selector: 'app-execucao-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    CesAutocompleteComponent,
    CesBooleanControlComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AuditDialogComponent,
    AjudaListagemComponent,
    NumeroOficialDialogComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts>
      <ces-page-header
        title="Execuções"
        icon="pi pi-bolt"
        [breadcrumb]="[{ label: 'Execução' }, { label: 'Comercial' }]"
        description="Notas lançadas pelas equipes: ações, materiais aplicados e retirados e o valor faturado."
        helpLabel="Ajuda sobre execuções"
        (help)="ajuda.show()"
      ></ces-page-header>

      <ces-filter-panel #painel storageKey="execucao-comercial" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-text-input label="Nota" [value]="lista.filters.referenciaNota() ?? ''" (valueChange)="lista.filters.referenciaNota.set($event || null)" placeholder="Ex.: 123456"></ces-text-input>
          <ces-select label="Tipo de lançamento" [options]="tiposLancamento" [(value)]="lista.filters.tipoNota"></ces-select>
          <ces-select label="Referência" [options]="referencias" [(value)]="lista.filters.referencia" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Equipe" [options]="recorte.opcoesEquipes()" [(value)]="lista.filters.idEquipe" [clearable]="true" [filter]="true"
            [loading]="recorte.carregandoEquipes()" placeholder="Todas"></ces-select>
          <ces-select label="Tipo de serviço" [options]="tiposServico()" [(value)]="lista.filters.tipoServico" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.status"></ces-select>
          <ces-select label="Ação" [options]="acoes()" [(value)]="lista.filters.idAcao" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-autocomplete label="Material" placeholder="Código ou descrição" [options]="materiais.items()" [ngModel]="lista.filters.idMaterial()"
            (ngModelChange)="escolherMaterial($event)" [loading]="materiais.loading()" [status]="materiais.status()" [clearable]="true"
            (search)="materiais.search($event)" (loadMore)="materiais.loadNext()" (retry)="materiais.retry()"></ces-autocomplete>
          <ces-select label="Operação do material" [options]="operacoes" [(value)]="lista.filters.operacaoMaterial"></ces-select>
          <ces-datepicker label="Lançadas a partir de" [value]="dataLocal(lista.filters.lancamento().from)" [clearable]="true" (valueChange)="lancamento('from', $event)"></ces-datepicker>
          <ces-datepicker label="Lançadas até" [value]="dataLocal(lista.filters.lancamento().to)" [clearable]="true" [minDate]="dataLocal(lista.filters.lancamento().from)"
            (valueChange)="lancamento('to', $event)"></ces-datepicker>
          <ces-boolean-control label="Só as lançadas hoje" kind="toggle" [inline]="true" [value]="lista.filters.apenasHoje()" (valueChange)="lista.filters.apenasHoje.set($event)"></ces-boolean-control>
          <ces-boolean-control label="Só avulsas sem número oficial" kind="toggle" [inline]="true" [value]="lista.filters.semNumeroOficial()" (valueChange)="lista.filters.semNumeroOficial.set($event)"></ces-boolean-control>
          <ces-boolean-control label="Só notas alteradas" kind="toggle" [inline]="true" [value]="lista.filters.alterada()" (valueChange)="lista.filters.alterada.set($event)"></ces-boolean-control>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Execuções"
        mode="server"
        storageKey="execucao-comercial"
        exportFileName="execucoes"
        searchPlaceholder="Nº de poste ou posto"
        rowActionLabel="Ver nota"
        rowActionIcon="pi pi-external-link"
        noResultsTitle="Nenhuma execução encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as execuções' : 'Nenhuma execução lançada'"
        [stateDescription]="resultado.error() ?? 'As notas aparecem aqui quando as equipes as lançam no aplicativo de campo.'"
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
        (rowActivate)="verNota($event)"
      >
        <ng-template cesCell="nota" let-row>
          <span class="nota-cel">
            <span class="ces-cell-strong">{{ row.referenciaNota }}</span>
            @if (row.aguardandoNumeroOficial) {
              <ces-status tone="warning" icon="pi-clock">Sem nº oficial</ces-status>
            } @else if (tipoDe(row); as tipo) {
              <ces-status [tone]="tipo.tom">{{ tipo.rotulo }}</ces-status>
            }
            @if (row.alterada) {
              <ces-status tone="warning" icon="pi-pencil" [attr.title]="'Alterada ' + (row.totalAlteracoes === 1 ? '1 vez' : row.totalAlteracoes + ' vezes') + ' depois de sincronizada'">Alterada</ces-status>
            }
          </span>
        </ng-template>
        <ng-template cesCell="status" let-row>
          <ces-status [tone]="status(row.statusExecucao)?.tom ?? 'neutral'" [icon]="status(row.statusExecucao)?.icone?.replace('pi ', '')">
            {{ status(row.statusExecucao)?.rotulo ?? row.statusExecucao }}
          </ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            @if (row.tipoNota === 'AVULSA') {
              <ces-icon-button icon="pi pi-pencil" [label]="(row.aguardandoNumeroOficial ? 'Informar número oficial da nota ' : 'Corrigir número oficial da nota ') + row.referenciaNota"
                size="icon-sm" (pressed)="informarNumero(row)"></ces-icon-button>
            }
            <ces-icon-button icon="pi pi-print" [label]="'Imprimir nota ' + row.referenciaNota" size="icon-sm" (pressed)="imprimir(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações da nota ' + row.referenciaNota" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <app-audit-dialog #auditoria></app-audit-dialog>
    <app-numero-oficial-dialog [(visible)]="numeroOficialAberto" [execucao]="avulsaEscolhida()" (informado)="lista.reload()"></app-numero-oficial-dialog>
    <app-ajuda-listagem #ajuda titulo="Execuções" artigoPlural="as" entidadePlural="execuções" busca="nº de poste ou posto" [podeCriar]="false">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-filter"></i> Recorte inicial</h4>
        <p class="help-text-desc">A lista abre nas notas lançadas nos últimos {{ diasPadrao }} dias. Vindo de um turno ("Notas do turno"), mostra todas as notas dele, sem limite de data.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-file-edit"></i> Nota avulsa e ocorrência</h4>
        <p class="help-text-desc">A nota avulsa é lançada em campo com um número provisório gerado pelo app. Use o lápis da linha para informar o número oficial; o provisório fica guardado. Na ocorrência comercial, material da Concessionária fica só no registro e não mexe no saldo da equipe. Os três tipos contam faturamento e meta.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-pencil"></i> Nota alterada</h4>
        <p class="help-text-desc">A equipe (pelo app, até 7 dias depois do lançamento, mesmo offline) e o escritório (no relatório da nota, a qualquer tempo) podem corrigir serviços e materiais de uma nota já sincronizada. A nota ganha o selo "Alterada", o valor é recalculado e o saldo do caminhão é ajustado. O relatório mostra o histórico de alterações com o lançamento original, quem alterou, de onde e por quê.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-box"></i> Material</h4>
        <p class="help-text-desc">Filtrar por material traz as notas que usaram aquele material; combine com "Operação do material" para separar aplicados de retirados.</p>
      </div>
    </app-ajuda-listagem>
  `,
  styles: `
    .nota-cel { display: inline-flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
  `
})
export class ExecucaoListComponent {
  private readonly execucoes = inject(ExecucaoService);
  private readonly router = inject(Router);
  private readonly lookupStore = inject(LookupStore);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');
  protected readonly recorte = inject(RecorteDashboardService);

  protected readonly statusOpcoes = STATUS;
  protected readonly operacoes = OPERACOES;
  protected readonly tiposLancamento = TIPOS_LANCAMENTO;
  protected readonly numeroOficialAberto = signal(false);
  protected readonly avulsaEscolhida = signal<ExecucaoComercialResponse | null>(null);
  protected readonly referencias: CesSelectOption<string>[] = generateReferenceOptions();
  protected readonly diasPadrao = DIAS_PADRAO_EXECUCOES;
  protected readonly dataLocal = dataLocal;
  protected readonly acoes = signal<CesSelectOption<string>[]>([]);
  protected readonly tiposServico = signal<CesSelectOption<string>[]>([{ label: TIPO_SERVICO_NAO_INFORMADO, value: SEM_TIPO_SERVICO }]);
  private readonly nomeMaterial = signal<Record<string, string>>({});

  protected readonly materiais = new RemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchMaterials({ query, cursor }),
    selectedValues: () => (this.lista.filters.idMaterial() ? [this.lista.filters.idMaterial()!] : []),
    destroyRef: inject(DestroyRef)
  });

  /** Vindo de um turno (`?idTurno=`) as notas dele aparecem todas, sem o limite de data. */
  private readonly doTurno = inject(ActivatedRoute).snapshot.queryParamMap.has('idTurno');

  protected readonly lista = cesListState({
    storageKey: 'execucao-comercial',
    filters: {
      idTurno: cesFilter.value<string | null>(null, { label: 'Turno', param: 'idTurno', format: (id): string | null => (id ? 'notas do turno' : null) }),
      referenciaNota: cesFilter.value<string | null>(null, { label: 'Nota', param: 'nota' }),
      tipoNota: cesFilter.value<TipoNota | null>(null, { label: 'Tipo de lançamento', param: 'tipoNota', type: 'string', options: () => TIPOS_LANCAMENTO }),
      semNumeroOficial: cesFilter.value<boolean>(false, { label: 'Avulsas', param: 'semNumero', format: (v): string | null => (v ? 'sem número oficial' : null) }),
      alterada: cesFilter.value<boolean>(false, { label: 'Notas', param: 'alterada', format: (v): string | null => (v ? 'alteradas' : null) }),
      referencia: cesFilter.value<string | null>(null, { label: 'Referência', param: 'ref' }),
      idEquipe: cesFilter.value<string | null>(null, { label: 'Equipe', param: 'equipe', format: (id): string | null => (id ? this.nome(this.recorte.opcoesEquipes(), id) : null) }),
      tipoServico: cesFilter.value<string | null>(null, { label: 'Tipo de serviço', param: 'tipo', format: (id): string | null => (id ? this.nome(this.tiposServico(), id) : null) }),
      status: cesFilter.value<StatusExecucao | null>(null, { label: 'Status', type: 'string', options: () => STATUS }),
      idAcao: cesFilter.value<string | null>(null, { label: 'Ação', param: 'acao', format: (id): string | null => (id ? this.nome(this.acoes(), id) : null) }),
      idMaterial: cesFilter.value<string | null>(null, { label: 'Material', param: 'material', format: (id): string | null => (id ? (this.nomeMaterial()[id] ?? 'material selecionado') : null) }),
      operacaoMaterial: cesFilter.value<OperacaoMaterial | null>(null, { label: 'Operação', param: 'operacao', type: 'string', options: () => OPERACOES }),
      apenasHoje: cesFilter.value<boolean>(false, { label: 'Lançamento', param: 'hoje', format: (v): string | null => (v ? 'hoje' : null) }),
      lancamento: cesFilter.range(this.doTurno ? { from: null, to: null } : { from: inicioPadrao(), to: null }, { label: 'Lançamento' })
    },
    sort: [{ key: 'lancamento', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<ExecucaoComercialResponse>((pedido) => this.execucoes.listarExecucoes(this.filtro(pedido)), { errorMessage: getErrorMessage });
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = (_evento: unknown, limite: number) =>
    this.execucoes.listarExecucoes({ ...this.filtro(this.lista.request()), page: 0, size: limite }).pipe(map((r) => r.content));

  protected readonly colunas: CesDataTableColumn<ExecucaoComercialResponse>[] = [
    { key: 'nota', header: 'Nota', type: 'custom', priority: 1, sortField: 'referenciaNota', value: (e) => e.referenciaNota, display: (e) => e.referenciaNota },
    { key: 'tipoNota', header: 'Lançamento', priority: 2, sortField: 'tipoNota', value: (e) => e.tipoNota, display: (e) => TIPOS_NOTA[e.tipoNota]?.rotulo ?? 'Comum' },
    { key: 'tipoServico', header: 'Tipo de serviço', priority: 1, value: (e) => e.tipoServico?.descricao, display: (e) => rotuloTipoServico(e.tipoServico) },
    // A nota guarda só o id da equipe: o backend não ordena pelo código.
    { key: 'equipe', header: 'Equipe', priority: 1, value: (e) => e.equipe?.codigo, display: (e) => e.equipe?.codigo ?? '—' },
    { key: 'lancamento', header: 'Lançamento', type: 'custom', sortField: 'dataLancamento', value: (e) => e.dataLancamento, display: (e) => new Date(e.dataLancamento).toLocaleString('pt-BR') },
    { key: 'status', header: 'Status', type: 'custom', sortField: 'statusExecucao', value: (e) => e.statusExecucao, display: (e) => STATUS_EXECUCAO[e.statusExecucao]?.rotulo ?? e.statusExecucao },
    { key: 'faturado', header: 'Faturado', type: 'currency', sortField: 'valorTotalFaturado', value: (e) => e.valorTotalFaturado },
    // Na exportação, deixa claro que a nota não é mais como foi lançada.
    { key: 'alteracoes', header: 'Alterações', priority: 3, sortField: 'totalAlteracoes', value: (e) => e.totalAlteracoes,
      display: (e) => (e.alterada ? `Alterada (${e.totalAlteracoes}x)` : 'Como lançada') }
  ];
  protected readonly rotulo = (e: ExecucaoComercialResponse) => e.referenciaNota;

  constructor() {
    this.recorte.carregarEquipes(inject(DestroyRef));
    inject(ContratoService)
      .listarAcoesAtivas()
      .subscribe({ next: (r) => this.acoes.set(r.content.map((a) => ({ label: a.nome, value: a.id }))), error: () => undefined });
    // Inclui os desativados: notas antigas continuam com o tipo que tinham.
    this.execucoes.listarTiposServico({ size: 200, sort: 'descricao,asc' }).subscribe({
      next: (r) =>
        this.tiposServico.set([
          ...r.content.map((t) => ({ label: `${rotuloTipoServico(t)}${t.ativo ? '' : ' (inativo)'}`, value: t.id })),
          { label: TIPO_SERVICO_NAO_INFORMADO, value: SEM_TIPO_SERVICO }
        ]),
      error: () => undefined
    });
  }

  private nome(opcoes: readonly CesSelectOption<string>[], id: string): string {
    return opcoes.find((o) => o.value === id)?.label ?? 'selecionado';
  }

  private filtro(pedido: { page: number; size: number; sort: readonly { key: string; direction: string }[] }): ExecucaoFilters {
    const { filters: f, query } = this.lista.request();
    const de = dataLocal(f.lancamento.from);
    const ate = dataLocal(f.lancamento.to);
    // "Até" inclui o dia inteiro escolhido.
    ate?.setHours(23, 59, 59, 999);
    return {
      idTurno: f.idTurno ?? undefined,
      referenciaNota: f.referenciaNota?.trim() || undefined,
      referencia: f.referencia ?? undefined,
      idEquipe: f.idEquipe ?? undefined,
      idTipoServico: f.tipoServico && f.tipoServico !== SEM_TIPO_SERVICO ? f.tipoServico : undefined,
      semTipoServico: f.tipoServico === SEM_TIPO_SERVICO || undefined,
      tipoNota: f.tipoNota ?? undefined,
      aguardandoNumeroOficial: f.semNumeroOficial || undefined,
      alterada: f.alterada || undefined,
      status: f.status ?? undefined,
      idAcao: f.idAcao ?? undefined,
      idMaterial: f.idMaterial ?? undefined,
      operacaoMaterial: f.operacaoMaterial ?? undefined,
      apenasHoje: f.apenasHoje || undefined,
      dataLancamentoDe: de?.toISOString(),
      dataLancamentoAte: ate?.toISOString(),
      buscaGlobal: query || undefined,
      page: pedido.page,
      size: pedido.size,
      sort: pedido.sort.length ? pedido.sort.map((r) => `${r.key},${r.direction}`) : ['dataLancamento,desc']
    };
  }

  protected escolherMaterial(id: string | null): void {
    const opcao = this.materiais.items().find((m) => m.value === id);
    if (opcao) this.nomeMaterial.update((n) => ({ ...n, [opcao.value]: opcao.label }));
    this.lista.filters.idMaterial.set(id);
  }

  protected lancamento(limite: 'from' | 'to', data: Date | null): void {
    this.lista.filters.lancamento.update((atual) => ({ ...atual, [limite]: dataIso(data) }));
  }

  protected status(s: StatusExecucao): (typeof STATUS_EXECUCAO)[StatusExecucao] | null {
    return STATUS_EXECUCAO[s] ?? null;
  }

  protected verNota(e: ExecucaoComercialResponse): void {
    window.open(this.router.serializeUrl(this.router.createUrlTree(['/execucao/comercial', e.id])), '_blank');
  }

  /** Selo da coluna Nota: só avulsa e ocorrência ganham selo; a comum fica limpa. */
  protected tipoDe(e: ExecucaoComercialResponse): (typeof TIPOS_NOTA)[TipoNota] | null {
    return e.tipoNota && e.tipoNota !== 'COMUM' ? TIPOS_NOTA[e.tipoNota] : null;
  }

  protected informarNumero(e: ExecucaoComercialResponse): void {
    this.avulsaEscolhida.set(e);
    this.numeroOficialAberto.set(true);
  }

  protected imprimir(e: ExecucaoComercialResponse): void {
    void this.router.navigate(['/execucao/comercial', e.id], { queryParams: { mode: 'print' } });
  }

  protected logs(e: ExecucaoComercialResponse): void {
    this.auditoria().show(e.id, e.referenciaNota, {
      referenciaNota: 'Ref. nota',
      statusExecucao: 'Status',
      valorTotalFaturado: 'Valor faturado',
      totalAlteracoes: 'Alterações'
    });
  }
}
