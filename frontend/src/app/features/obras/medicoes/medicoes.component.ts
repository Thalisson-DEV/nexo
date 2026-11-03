import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
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
  CesTabPanelDirective,
  CesTabsComponent,
  CesTextInputComponent,
  cesFilter,
  cesListState
} from '@ces/ui';
import { map } from 'rxjs';
import { MedicaoDetalhe, MedicaoResumo, ObraAMedir, OpcoesMedicoes, StatusMedicao, TipoMedicao } from '../../../core/models/obras-medicoes.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AuthService } from '../../../core/services/auth.service';
import { ObrasMedicoesService } from '../../../core/services/obras-medicoes.service';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, dataIso, dataLocal } from '../../../shared/listagem/listagem';
import { RecorteDashboardService } from '../../execucao/dashboards/recorte-dashboard.service';
import { formatarMoeda } from '../obras-ui';
import { MedicaoDetalheDrawerComponent } from './medicao-detalhe-drawer.component';
import { MedirObraDrawerComponent } from './medir-obra-drawer.component';
import {
  PRIORIDADES,
  STATUS_MEDICAO,
  TIPO_MEDICAO,
  competenciasRecentes,
  noRecorte,
  opcoesDistintas,
  rotuloCompetencia
} from './medicoes.logic';

type Aba = 'a-medir' | 'geradas';

const TIPOS: CesSelectOption<TipoMedicao>[] = [
  { label: 'Parcial', value: 'PARCIAL' },
  { label: 'Por ciclo', value: 'CICLO' }
];
const STATUS: CesSelectOption<StatusMedicao>[] = [
  { label: 'Gerada', value: 'GERADA' },
  { label: 'Estornada', value: 'ESTORNADA' }
];

/**
 * Medições das obras. "A medir" lista as obras com serviço concluído e ainda não medido, a mais valiosa primeiro,
 * e abre a medição da obra num painel; "Medições geradas" é o histórico, com estorno no detalhe.
 */
@Component({
  selector: 'app-medicoes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
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
    CesTabPanelDirective,
    CesTabsComponent,
    CesTextInputComponent,
    AjudaListagemComponent,
    MedicaoDetalheDrawerComponent,
    MedirObraDrawerComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts>
      <ces-page-header title="Medições" icon="pi pi-calculator"
        [breadcrumb]="[{ label: 'Obras' }, { label: 'Medições' }]"
        description="Serviços concluídos levados à cobrança, com o valor congelado na programação. O que é medido não entra em outra medição."
        helpLabel="Ajuda sobre medições" (help)="ajuda.show()">
        <ces-button actions label="Pré-fechamento" icon="pi pi-clipboard" variant="outline" (pressed)="ir('/obras/pre-fechamento')"></ces-button>
        <ces-button actions label="Fechamento de obra" icon="pi pi-file-check" variant="outline" (pressed)="ir('/obras/fechamento')"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="obras-medicoes" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))"
        description="Base, município e supervisor valem para as duas abas; os demais, só para a aba aberta.">
        <div class="ces-filter-grid">
          <ces-multiselect label="Base operacional" [options]="opcoesBases()" [(value)]="lista.filters.bases" [clearable]="true" [filter]="true"
            selectedItemsLabel="{0} bases" [maxSelectedLabels]="2" placeholder="Todas"></ces-multiselect>
          <ces-multiselect label="Município" [options]="opcoesMunicipios()" [(value)]="lista.filters.municipios" [clearable]="true" [filter]="true"
            selectedItemsLabel="{0} municípios" [maxSelectedLabels]="2" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Supervisor" [options]="opcoesSupervisores()" [(value)]="lista.filters.supervisores" [clearable]="true" [filter]="true"
            selectedItemsLabel="{0} supervisores" [maxSelectedLabels]="2" placeholder="Todos"></ces-multiselect>
          @if (aba() === 'a-medir') {
            <ces-multiselect label="Status da obra" [options]="opcoesStatus()" [(value)]="lista.filters.statusObra" [clearable]="true" placeholder="Todos"></ces-multiselect>
            <ces-multiselect label="Prioridade" [options]="prioridadesOpcoes" [(value)]="lista.filters.prioridades" [clearable]="true" placeholder="Todas"></ces-multiselect>
          } @else {
            <ces-multiselect label="Tipo" [options]="tiposOpcoes" [(value)]="lista.filters.tipos" [clearable]="true" placeholder="Todos"></ces-multiselect>
            <ces-multiselect label="Situação" [options]="statusOpcoes" [(value)]="lista.filters.status" [clearable]="true" placeholder="Todas"></ces-multiselect>
            <ces-select label="Competência a partir de" [options]="competencias" [(value)]="lista.filters.competenciaDe" [clearable]="true" placeholder="Qualquer"></ces-select>
            <ces-select label="Competência até" [options]="competencias" [(value)]="lista.filters.competenciaAte" [clearable]="true" placeholder="Qualquer"></ces-select>
            <ces-text-input label="Gerada por" placeholder="Login de quem gerou" [value]="lista.filters.usuario()" (valueChange)="lista.filters.usuario.set($event)"></ces-text-input>
            <ces-datepicker label="Gerada a partir de" [value]="dataLocal(lista.filters.gerada().from)" [clearable]="true" (valueChange)="gerada('from', $event)"></ces-datepicker>
            <ces-datepicker label="Gerada até" [value]="dataLocal(lista.filters.gerada().to)" [clearable]="true" [minDate]="dataLocal(lista.filters.gerada().from)"
              (valueChange)="gerada('to', $event)"></ces-datepicker>
          }
        </div>
      </ces-filter-panel>

      <section class="ces-kpis medicoes-kpis" aria-label="Indicadores do que está a medir" aria-live="polite">
        <div class="ces-kpi" [attr.data-tone]="aMedir().length ? 'info' : 'success'">
          <span class="ces-kpi__icon"><i class="pi pi-building" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Obras a medir</span>
            <span class="ces-kpi__value">{{ aMedir().length }}</span>
            <span class="ces-kpi__detail">Com serviço concluído não medido</span></span>
        </div>
        <div class="ces-kpi" data-tone="brand">
          <span class="ces-kpi__icon"><i class="pi pi-wallet" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Valor a medir</span>
            <span class="ces-kpi__value">{{ moeda(valorAMedir()) }}</span>
            <span class="ces-kpi__detail">{{ servicosAMedir() }} {{ servicosAMedir() === 1 ? 'serviço' : 'serviços' }}</span></span>
        </div>
        <div class="ces-kpi" [attr.data-tone]="maisAntiga() ? 'warning' : 'neutral'">
          <span class="ces-kpi__icon"><i class="pi pi-hourglass" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Esperando medição há mais tempo</span>
            <span class="ces-kpi__value">{{ maisAntiga()?.ultimaExecucao ?? '—' }}</span>
            <span class="ces-kpi__detail">{{ maisAntiga() ? 'Obra ' + maisAntiga()!.numero + ', pela última execução' : 'Nada pendente' }}</span></span>
        </div>
      </section>

      <ces-tabs [value]="aba()" (valueChange)="trocarAba($any($event))" [tabs]="abas()" ariaLabel="Medições">
        <ng-template cesTabPanel="a-medir">
          <ces-data-table title="Obras a medir" storageKey="obras-a-medir" exportFileName="obras-a-medir"
            searchPlaceholder="Obra, título ou município" [rowActionLabel]="podeGerar ? 'Medir' : 'Ver serviços'" rowActionIcon="pi pi-calculator"
            noResultsTitle="Nenhuma obra encontrada com essa busca."
            [columns]="colunasAMedir" [rows]="aMedir()" [rowKey]="chaveObra" [rowLabel]="rotuloObra" [state]="estadoAMedir()"
            [stateTitle]="erroAMedir() ? 'Não foi possível carregar as obras a medir' : 'Nada pendente de medição'"
            [stateDescription]="erroAMedir() ?? 'Todos os serviços concluídos já foram medidos. A execução apontada pelas equipes aparece aqui quando o serviço é concluído.'"
            [filtered]="recorteObraAtivo()" (adjustFilters)="painel.expandAndFocus()" (clearFilters)="lista.reset()"
            (rowActivate)="medir($event)">
            <ng-template cesCell="obra" let-row>
              <span class="ces-cell-strong">{{ row.numero }}</span>
              <span class="ces-cell-muted">{{ row.titulo }}</span>
            </ng-template>
            <ng-template cesCell="local" let-row>
              <span>{{ row.contexto?.municipio ?? '—' }}</span>
              <span class="ces-cell-muted">{{ local(row) }}</span>
            </ng-template>
          </ces-data-table>
        </ng-template>

        <ng-template cesTabPanel="geradas">
          <ces-data-table title="Medições geradas" mode="server" storageKey="obras-medicoes" exportFileName="medicoes-obras"
            searchPlaceholder="Número da medição (#12), obra ou título" rowActionLabel="Ver medição" rowActionIcon="pi pi-angle-right"
            noResultsTitle="Nenhuma medição encontrada com esse recorte."
            [columns]="colunasGeradas" [rows]="resultado.rows()" [totalRecords]="resultado.total()" [rowLabel]="rotuloMedicao" [state]="resultado.state()"
            [stateTitle]="resultado.error() ? 'Não foi possível carregar as medições' : 'Nenhuma medição gerada ainda'"
            [stateDescription]="resultado.error() ?? 'As medições aparecem aqui depois de geradas na aba A medir.'"
            [filtered]="lista.filtered()" [exportRows]="exportar" [exportLimit]="limiteExportacao"
            [(page)]="lista.page" [(pageSize)]="lista.pageSize" [(sort)]="lista.sort" [(query)]="lista.query"
            (lazyLoad)="lista.load($event)" (adjustFilters)="painel.expandAndFocus()" (clearFilters)="lista.reset()" (rowActivate)="verMedicao($event)">
            <ng-template cesCell="numero" let-row>
              <span class="ces-cell-strong">#{{ row.numero }}</span>
              <span class="ces-cell-muted">{{ tipoDe(row) }}</span>
            </ng-template>
            <ng-template cesCell="obra" let-row>
              <span class="ces-cell-strong">{{ row.numeroObra }}</span>
              <span class="ces-cell-muted">{{ row.tituloObra }}</span>
            </ng-template>
            <ng-template cesCell="status" let-row>
              <ces-status [tone]="statusDe(row).tom" [icon]="statusDe(row).icone">{{ statusDe(row).rotulo }}</ces-status>
              @if (row.status === 'ESTORNADA') { <span class="ces-cell-muted" [title]="row.justificativaEstorno">{{ row.justificativaEstorno }}</span> }
            </ng-template>
            <ng-template cesCell="gerada" let-row>
              <span>{{ row.dataCriacao | date: 'dd/MM/yyyy HH:mm' }}</span>
              <span class="ces-cell-muted">por {{ row.usuarioCriacao }}</span>
            </ng-template>
          </ces-data-table>
        </ng-template>
      </ces-tabs>
    </div>

    <app-medir-obra-drawer [(aberto)]="medindo" [idObra]="obraEmMedicao()" (gerada)="aoGerar($event)"></app-medir-obra-drawer>
    <app-medicao-detalhe-drawer [(aberto)]="vendo" [idMedicao]="medicaoAberta()" (alterada)="aoAlterar()"></app-medicao-detalhe-drawer>

    <app-ajuda-listagem #ajuda titulo="Medições" entidadePlural="medições" busca="número da medição, obra ou título" [podeCriar]="false">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-calculator"></i> Parcial e por ciclo</h4>
        <p class="help-text-desc">A parcial mede o que está concluído até hoje e entra na competência do mês corrente. A por ciclo escolhe a competência e mede só o que foi concluído até o último dia dela, mostrando o saldo sobre o valor de projeto antes e depois.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-wallet"></i> De onde vem o valor</h4>
        <p class="help-text-desc">Quantidade e valor são a soma dos apontamentos de cada serviço, com o valor congelado na programação. Material não é medido. Kit marcado como executado antes do sistema não entra.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-undo"></i> Estorno</h4>
        <p class="help-text-desc">Estornar exige justificativa: os serviços voltam a concluídos e podem entrar em outra medição. A medição estornada continua no histórico.</p>
      </div>
    </app-ajuda-listagem>
  `,
  styles: `
    .medicoes-kpis { margin-bottom: 1rem; }
  `
})
export class MedicoesComponent {
  private readonly api = inject(ObrasMedicoesService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly recorte = inject(RecorteDashboardService);

  protected readonly podeGerar = (inject(AuthService).currentUser()?.permissoes ?? []).includes('obras:medicao:write');
  protected readonly moeda = formatarMoeda;
  protected readonly tipo = TIPO_MEDICAO;
  protected readonly status = STATUS_MEDICAO;
  protected readonly tiposOpcoes = TIPOS;
  protected readonly statusOpcoes = STATUS;
  protected readonly competencias = competenciasRecentes(new Date(), 24);
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;

  protected readonly prioridadesOpcoes = PRIORIDADES;
  protected readonly dataLocal = dataLocal;

  protected readonly aba = signal<Aba>('a-medir');
  private readonly todasAMedir = signal<ObraAMedir[]>([]);
  private readonly opcoesGeradas = signal<OpcoesMedicoes>({ municipios: [], supervisores: [] });
  private readonly carregandoAMedir = signal(true);
  protected readonly erroAMedir = signal<string | null>(null);
  protected readonly medindo = signal(false);
  protected readonly obraEmMedicao = signal<string | null>(null);
  protected readonly vendo = signal(false);
  protected readonly medicaoAberta = signal<string | null>(null);

  protected readonly valorAMedir = computed(() => this.aMedir().reduce((t, o) => t + o.valor, 0));
  protected readonly servicosAMedir = computed(() => this.aMedir().reduce((t, o) => t + o.servicos, 0));
  protected readonly maisAntiga = computed<ObraAMedir | null>(() => {
    const chave = (o: ObraAMedir) => o.ultimaExecucao?.split('/').reverse().join('') ?? '';
    const comData = this.aMedir().filter((o) => !!o.ultimaExecucao);
    return comData.length ? comData.reduce((a, b) => (chave(b) < chave(a) ? b : a)) : null;
  });
  protected readonly estadoAMedir = computed(() =>
    this.carregandoAMedir() ? 'loading' : this.erroAMedir() ? 'error' : this.aMedir().length ? 'ready' : 'empty'
  );
  protected readonly abas = computed(() => [
    { value: 'a-medir', label: 'A medir', icon: 'pi pi-hourglass', badge: this.aMedir().length },
    { value: 'geradas', label: 'Medições geradas', icon: 'pi pi-history' }
  ]);

  protected readonly lista = cesListState({
    storageKey: 'obras-medicoes',
    filters: {
      bases: cesFilter.list<string>([], { label: 'Base', param: 'base', itemLabel: (id) => this.nome(this.opcoesBases(), id), countLabel: (n) => `${n} bases` }),
      municipios: cesFilter.list<string>([], { label: 'Município', param: 'municipio', itemLabel: (id) => this.nome(this.opcoesMunicipios(), id), countLabel: (n) => `${n} municípios` }),
      supervisores: cesFilter.list<string>([], { label: 'Supervisor', param: 'supervisor', itemLabel: (id) => this.nome(this.opcoesSupervisores(), id), countLabel: (n) => `${n} supervisores` }),
      statusObra: cesFilter.list<string>([], { label: 'Status da obra', param: 'statusObra', countLabel: (n) => `${n} status` }),
      prioridades: cesFilter.list<string>([], { label: 'Prioridade', param: 'prioridade', itemLabel: (v) => PRIORIDADES.find((p) => p.value === v)?.label ?? v }),
      tipos: cesFilter.list<TipoMedicao>([], { label: 'Tipo', param: 'tipo', itemLabel: (t) => TIPO_MEDICAO[t] }),
      status: cesFilter.list<StatusMedicao>([], { label: 'Situação', param: 'situacao', itemLabel: (s) => STATUS_MEDICAO[s].rotulo }),
      competenciaDe: cesFilter.value<string | null>(null, { label: 'Competência de', param: 'de', type: 'string', format: (v): string | null => (v ? rotuloCompetencia(v) : null) }),
      competenciaAte: cesFilter.value<string | null>(null, { label: 'Competência até', param: 'ate', type: 'string', format: (v): string | null => (v ? rotuloCompetencia(v) : null) }),
      usuario: cesFilter.value<string>('', { label: 'Gerada por', param: 'usuario', format: (v): string | null => v || null }),
      gerada: cesFilter.range({ from: null, to: null }, { label: 'Gerada' })
    },
    sort: [{ key: 'numero', direction: 'desc' }]
  });

  /** A medir no recorte da obra (base, município, supervisor, status e prioridade), filtrado na tela. */
  protected readonly aMedir = computed(() => {
    const f = this.lista.applied();
    const recorte = { bases: f.bases, municipios: f.municipios, supervisores: f.supervisores, status: f.statusObra, prioridades: f.prioridades };
    return this.todasAMedir().filter((o) => noRecorte(o.contexto, recorte));
  });
  protected readonly recorteObraAtivo = computed(() => {
    const f = this.lista.applied();
    return !!(f.bases.length || f.municipios.length || f.supervisores.length || f.statusObra.length || f.prioridades.length);
  });
  protected readonly opcoesBases = computed(() => {
    const daAMedir = opcoesDistintas(this.todasAMedir(), (o) => o.contexto?.idBaseOperacional, (o) => o.contexto?.baseOperacional);
    const ids = new Set(daAMedir.map((o) => o.value));
    return [...daAMedir, ...this.recorte.opcoesBases().filter((b) => !ids.has(b.value))].sort((a, b) => a.label.localeCompare(b.label, 'pt-BR'));
  });
  protected readonly opcoesMunicipios = computed(() =>
    opcoesDistintas(
      [...this.todasAMedir().map((o) => ({ id: o.contexto?.idMunicipio, nome: o.contexto?.municipio })), ...this.opcoesGeradas().municipios],
      (o) => o.id,
      (o) => o.nome
    )
  );
  protected readonly opcoesSupervisores = computed(() =>
    opcoesDistintas(
      [...this.todasAMedir().map((o) => ({ id: o.contexto?.idSupervisor, nome: o.contexto?.supervisor })), ...this.opcoesGeradas().supervisores],
      (o) => o.id,
      (o) => o.nome
    )
  );
  protected readonly opcoesStatus = computed(() => opcoesDistintas(this.todasAMedir(), (o) => o.contexto?.statusObra, (o) => o.contexto?.statusObra));
  protected readonly resultado = this.lista.results<MedicaoResumo>((pedido) => this.api.listar(this.filtro(pedido)), { errorMessage: getErrorMessage });
  protected readonly exportar = (_evento: unknown, limite: number) =>
    this.api.listar({ ...this.filtro(this.lista.request()), page: 0, size: limite }).pipe(map((r) => r.content));

  protected readonly colunasAMedir: CesDataTableColumn<ObraAMedir>[] = [
    { key: 'obra', header: 'Obra', type: 'custom', priority: 1, value: (o) => o.numero, display: (o) => `${o.numero} ${o.titulo}` },
    { key: 'local', header: 'Município', type: 'custom', value: (o) => o.contexto?.municipio, display: (o) => [o.contexto?.municipio, this.local(o)].filter(Boolean).join(' · ') },
    { key: 'servicos', header: 'Serviços', type: 'integer', value: (o) => o.servicos },
    { key: 'valor', header: 'Valor a medir', type: 'currency', priority: 1, value: (o) => o.valor },
    { key: 'ultimaExecucao', header: 'Última execução', value: (o) => o.ultimaExecucao?.split('/').reverse().join('-'), display: (o) => o.ultimaExecucao ?? '—' }
  ];
  protected readonly colunasGeradas: CesDataTableColumn<MedicaoResumo>[] = [
    { key: 'numero', header: 'Medição', type: 'custom', priority: 1, sortField: 'numero', value: (m) => m.numero, display: (m) => `#${m.numero} ${TIPO_MEDICAO[m.tipo]}` },
    { key: 'obra', header: 'Obra', type: 'custom', priority: 1, value: (m) => m.numeroObra, display: (m) => `${m.numeroObra ?? ''} ${m.tituloObra ?? ''}` },
    { key: 'competencia', header: 'Competência', sortField: 'competencia', value: (m) => m.competencia, display: (m) => rotuloCompetencia(m.competencia) },
    { key: 'servicos', header: 'Serviços', type: 'integer', value: (m) => m.servicos },
    { key: 'valorTotal', header: 'Valor', type: 'currency', priority: 1, sortField: 'valorTotal', value: (m) => m.valorTotal },
    { key: 'status', header: 'Situação', type: 'custom', sortField: 'status', value: (m) => m.status, display: (m) => STATUS_MEDICAO[m.status].rotulo },
    { key: 'gerada', header: 'Gerada', type: 'custom', sortField: 'dataCriacao', value: (m) => m.dataCriacao, display: (m) => `${new Date(m.dataCriacao).toLocaleString('pt-BR')} por ${m.usuarioCriacao ?? ''}` }
  ];
  protected readonly chaveObra = (o: ObraAMedir) => o.idObra;
  protected readonly rotuloObra = (o: ObraAMedir) => `obra ${o.numero}`;
  protected readonly rotuloMedicao = (m: MedicaoResumo) => `medição ${m.numero}`;

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((p) => this.aba.set(p.get('aba') === 'geradas' ? 'geradas' : 'a-medir'));
    this.recorte.carregar(this.destroyRef);
    this.carregarAMedir();
    // Sem as opções, os filtros de município e supervisor das geradas ficam só com as obras a medir.
    this.api.opcoes().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: (o) => this.opcoesGeradas.set(o), error: () => undefined });
  }

  protected local(o: ObraAMedir): string {
    return [o.contexto?.baseOperacional, o.contexto?.supervisor].filter(Boolean).join(' · ');
  }

  private nome(opcoes: readonly { label: string; value: string }[], id: string): string {
    return opcoes.find((o) => o.value === id)?.label ?? 'selecionado';
  }

  protected gerada(limite: 'from' | 'to', data: Date | null): void {
    this.lista.filters.gerada.update((atual) => ({ ...atual, [limite]: dataIso(data) }));
  }

  private carregarAMedir(): void {
    this.carregandoAMedir.set(true);
    this.erroAMedir.set(null);
    this.api.obrasAMedir().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (obras) => {
        this.todasAMedir.set(obras);
        this.carregandoAMedir.set(false);
      },
      error: (e) => {
        this.erroAMedir.set(getErrorMessage(e));
        this.carregandoAMedir.set(false);
      }
    });
  }

  private filtro(pedido: { page: number; size: number; sort: readonly { key: string; direction: string }[] }) {
    const { filters, query } = this.lista.request();
    return {
      tipos: filters.tipos.length ? filters.tipos : undefined,
      status: filters.status.length ? filters.status : undefined,
      competenciaDe: filters.competenciaDe ?? undefined,
      competenciaAte: filters.competenciaAte ?? undefined,
      idsBases: filters.bases.length ? filters.bases : undefined,
      idsMunicipios: filters.municipios.length ? filters.municipios : undefined,
      idsSupervisores: filters.supervisores.length ? filters.supervisores : undefined,
      usuario: filters.usuario || undefined,
      geradaDe: filters.gerada.from ?? undefined,
      geradaAte: filters.gerada.to ?? undefined,
      q: query || undefined,
      page: pedido.page,
      size: pedido.size,
      sort: pedido.sort.length ? pedido.sort.map((r) => `${r.key},${r.direction}`) : ['numero,desc']
    };
  }

  protected trocarAba(aba: Aba): void {
    this.aba.set(aba);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { aba: aba === 'geradas' ? 'geradas' : null }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  protected medir(o: ObraAMedir): void {
    this.obraEmMedicao.set(o.idObra);
    this.medindo.set(true);
  }

  protected verMedicao(m: MedicaoResumo): void {
    this.medicaoAberta.set(m.id);
    this.vendo.set(true);
  }

  protected aoGerar(m: MedicaoDetalhe): void {
    this.carregarAMedir();
    this.lista.reload();
    this.medicaoAberta.set(m.medicao.id);
    this.vendo.set(true);
  }

  protected aoAlterar(): void {
    this.carregarAMedir();
    this.lista.reload();
  }

  protected statusDe(m: MedicaoResumo) {
    return STATUS_MEDICAO[m.status];
  }

  protected tipoDe(m: MedicaoResumo): string {
    return TIPO_MEDICAO[m.tipo];
  }

  protected ir(rota: string): void {
    void this.router.navigate([rota]);
  }
}
