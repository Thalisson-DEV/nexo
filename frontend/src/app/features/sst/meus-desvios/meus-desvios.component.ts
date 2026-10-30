import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, Signal, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import {
  CesButtonComponent,
  CesCalloutComponent,
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
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';

import { MeuDesvioResponse, TratarMeuDesvioRequest } from '../../../core/models/meu-desvio.models';
import { CentroFaturamentoResponse, EquipeResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { GRAVIDADE_LABELS, GravidadeDesvio, STATUS_DESVIO_LABELS, StatusDesvio } from '../../../core/models/sst.models';
import { AuthService } from '../../../core/services/auth.service';
import { CentrosCustoLookupService } from '../../../core/services/centros-custo-lookup.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { dataIso, dataLocal } from '../../../shared/listagem/listagem';
import {
  DESVIOS_EM_ABERTO,
  GRAVIDADE_ICONE,
  GRAVIDADE_TOM,
  OPCOES_GRAVIDADE,
  OPCOES_SITUACAO_PRAZO,
  OPCOES_STATUS_DESVIO,
  STATUS_DESVIO_ICONE,
  STATUS_DESVIO_TOM,
  SituacaoPrazo
} from '../sst-ui';
import { MeusDesviosFiltrosView, MeusDesviosLookupService, desvioAtendeFiltros, montarMeuDesvioFiltro } from './meus-desvios.utils';
import { TratamentoMeuDesvioDialogComponent } from './tratamento-meu-desvio-dialog.component';

/** O endpoint de meus desvios não filtra "sem prazo". */
const OPCOES_PRAZO = OPCOES_SITUACAO_PRAZO.filter((o) => o.value !== 'SEM_PRAZO');

/** Limite do tratamento em lote no servidor. */
const LIMITE_LOTE = 100;

type Atalho = { chave: string; rotulo: string; icone: string; status?: StatusDesvio; vencidos?: boolean };
const ATALHOS: Atalho[] = [
  { chave: 'ABERTO', rotulo: 'Abertos', icone: 'pi pi-exclamation-circle', status: 'ABERTO' },
  { chave: 'EM_TRATAMENTO', rotulo: 'Em tratamento', icone: 'pi pi-wrench', status: 'EM_TRATAMENTO' },
  { chave: 'RESOLVIDO', rotulo: 'Resolvidos', icone: 'pi pi-check-circle', status: 'RESOLVIDO' },
  { chave: 'VENCIDOS', rotulo: 'Vencidos', icone: 'pi pi-clock', vencidos: true }
];

@Component({
  selector: 'app-meus-desvios',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesListShortcutsDirective,
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesSelectComponent,
    CesStatusComponent,
    AjudaListagemComponent,
    TratamentoMeuDesvioDialogComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts>
      <ces-page-header
        title="Meus desvios"
        icon="pi pi-exclamation-circle"
        [breadcrumb]="[{ label: 'SST' }, { label: 'Meus desvios' }]"
        description="Desvios sob a sua responsabilidade: registre a ação corretiva, o prazo e a resolução — um a um ou em lote."
        helpLabel="Ajuda sobre meus desvios"
        (help)="ajuda.show()"
      ></ces-page-header>

      <ces-filter-panel #painel storageKey="sst-meus-desvios" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-multiselect label="Status" [options]="opcoesStatus" [(value)]="lista.filters.status" [filter]="false" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Gravidade" [options]="opcoesGravidade" [(value)]="lista.filters.gravidade" [filter]="false" placeholder="Todas"></ces-multiselect>
          <ces-select label="Prazo" [options]="opcoesPrazo" [(value)]="lista.filters.prazoSituacao"></ces-select>
          <ces-multiselect label="Processo" [options]="processos()" [(value)]="lista.filters.processo" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Centro de custo" [options]="centros()" [(value)]="lista.filters.centroCusto" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Base operacional" [options]="bases()" [(value)]="lista.filters.base" placeholder="Todas"></ces-multiselect>
          <ces-multiselect label="Equipe" [options]="equipesOpcoes()" [(value)]="lista.filters.equipe" placeholder="Todas"
            [loading]="equipes.loading()" (opened)="equipes.open()" (search)="equipes.search($event)"></ces-multiselect>
          <ces-datepicker label="Inspeção de" [value]="dataLocal(lista.filters.inspecao().from)" [clearable]="true" (valueChange)="intervalo('from', $event)"></ces-datepicker>
          <ces-datepicker label="Inspeção até" [value]="dataLocal(lista.filters.inspecao().to)" [clearable]="true" (valueChange)="intervalo('to', $event)"></ces-datepicker>
        </div>
      </ces-filter-panel>

      @if (contextoPrioridade) {
        <ces-callout tone="info" title="Contexto da prioridade">{{ contextoPrioridade }}</ces-callout>
      }

      <ces-data-table
        title="Meus desvios"
        mode="server"
        storageKey="sst-meus-desvios"
        searchPlaceholder="Descrição ou ação corretiva"
        [rowActionLabel]="podeTratar ? 'Tratar' : undefined"
        rowActionIcon="pi pi-wrench"
        noResultsTitle="Nenhum desvio encontrado com esse recorte."
        [columns]="colunas"
        [rows]="linhas()"
        [totalRecords]="total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os seus desvios' : 'Nenhum desvio sob sua responsabilidade'"
        [stateDescription]="resultado.error() ?? 'Quando uma inspeção apontar um desvio para você, ele aparece aqui.'"
        [filtered]="lista.filtered()"
        [selectable]="podeTratar"
        [selectionLimit]="limiteLote"
        [(selection)]="selecionados"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (rowActivate)="abrirTratamento($event)"
      >
        <div table-filters class="ces-quick-filters" role="group" aria-label="Atalhos de situação">
          @for (atalho of atalhos; track atalho.chave) {
            <button type="button" class="ces-quick-filter" [attr.aria-pressed]="atalhoAtivo() === atalho.chave" (click)="alternarAtalho(atalho)">
              <i [class]="atalho.icone" aria-hidden="true"></i> {{ atalho.rotulo }}
            </button>
          }
        </div>
        <ces-button selection-actions size="sm" icon="pi pi-check-square" label="Tratar selecionados" (pressed)="abrirTratamentoEmLote()"></ces-button>
        <ng-template cesCell="gravidade" let-row>
          <ces-status [tone]="gravidadeTom(row.gravidade)" [icon]="gravidadeIcone(row.gravidade)">{{ gravidadeLabel(row.gravidade) }}</ces-status>
        </ng-template>
        <ng-template cesCell="status" let-row>
          <ces-status [tone]="statusTom(row.status)" [icon]="statusIcone(row.status)" emphasis="outline">{{ statusLabel(row.status) }}</ces-status>
        </ng-template>
        <ng-template cesCell="desvio" let-row>
          <span class="ces-cell-strong" [title]="row.pergunta ?? ''">{{ row.pergunta || 'Pergunta não informada' }}</span>
          @if (row.descricao) {
            <span class="ces-cell-muted">{{ row.descricao }}</span>
          }
          @if (row.acaoCorretiva) {
            <span class="ces-cell-muted"><i class="pi pi-wrench" aria-hidden="true"></i> {{ row.acaoCorretiva }}</span>
          }
        </ng-template>
        <ng-template cesCell="local" let-row>
          <span>{{ row.baseOperacional?.nome || '—' }}</span>
          <span class="ces-cell-muted">{{ row.equipe?.codigo || 'Sem equipe' }}</span>
        </ng-template>
        <ng-template cesCell="prazo" let-row>
          @if (row.prazo) {
            <span>{{ row.prazo | date: 'dd/MM/yyyy' }}</span>
          } @else {
            <span class="ces-cell-muted">Sem prazo</span>
          }
          @if (row.vencido) {
            <ces-status tone="danger" icon="pi-clock">Vencido</ces-status>
          }
        </ng-template>
      </ces-data-table>
    </div>

    <app-tratamento-meu-desvio-dialog #tratamento [loading]="salvando()" (salvar)="salvarTratamento($event)" />

    <app-ajuda-listagem #ajuda titulo="Meus desvios" entidadePlural="desvios" busca="descrição ou ação corretiva" [podeCriar]="false">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-filter"></i> Atalhos e recorte inicial</h4>
        <p class="help-text-desc">A lista abre com os desvios em aberto. Os atalhos acima da tabela mostram só abertos, em tratamento, resolvidos ou vencidos; clique de novo para desfazer.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-check-square"></i> Tratamento em lote</h4>
        <p class="help-text-desc">Marque até {{ limiteLote }} desvios (inclusive de páginas diferentes) e use "Tratar selecionados" para registrar o mesmo status, ação e prazo em todos.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class MeusDesviosComponent {
  private readonly sst = inject(SstService);
  private readonly rh = inject(RhService);
  private readonly notificacoes = inject(NotificationService);
  private readonly tratamento = viewChild.required<TratamentoMeuDesvioDialogComponent>('tratamento');
  private readonly destroyRef = inject(DestroyRef);

  protected readonly podeTratar = inject(AuthService).currentUser()?.permissoes?.includes('sst:inspecao:write') ?? false;
  protected readonly limiteLote = LIMITE_LOTE;
  protected readonly atalhos = ATALHOS;
  protected readonly opcoesStatus = OPCOES_STATUS_DESVIO;
  protected readonly opcoesGravidade = OPCOES_GRAVIDADE;
  protected readonly opcoesPrazo = OPCOES_PRAZO;
  protected readonly dataLocal = dataLocal;
  protected readonly gravidadeTom = (g: GravidadeDesvio) => GRAVIDADE_TOM[g];
  protected readonly gravidadeIcone = (g: GravidadeDesvio) => GRAVIDADE_ICONE[g];
  protected readonly statusTom = (s: StatusDesvio) => STATUS_DESVIO_TOM[s];
  protected readonly statusIcone = (s: StatusDesvio) => STATUS_DESVIO_ICONE[s];
  /** Texto do painel de desvios quando a pessoa chega por uma prioridade. */
  protected readonly contextoPrioridade: string = (globalThis.history?.state as { contextoPrioridade?: string } | null)?.contextoPrioridade ?? '';

  protected readonly processos = signal<CesSelectOption<string>[]>([]);
  protected readonly bases = signal<CesSelectOption<string>[]>([]);
  protected readonly centros = signal<CesSelectOption<string>[]>([]);
  protected readonly equipes: RemoteLookup<EquipeResponse> = new RemoteLookup<EquipeResponse>(
    {
      identify: (e) => e.id,
      selectedKeys: () => this.lista.filters.equipe(),
      load: ({ query, page, size }) => this.rh.listarEquipes({ ativo: true, buscaGlobal: query || undefined, page, size, sort: 'codigo,asc' })
    },
    this.destroyRef
  );
  protected readonly equipesOpcoes: Signal<CesSelectOption<string>[]> = computed<CesSelectOption<string>[]>(() => this.equipes.items().map((e) => ({ label: e.codigo, value: e.id })));

  protected readonly lista = cesListState({
    storageKey: 'sst-meus-desvios',
    filters: {
      status: cesFilter.list<StatusDesvio>([...DESVIOS_EM_ABERTO], {
        label: 'Status',
        itemLabel: (s) => STATUS_DESVIO_LABELS[s] ?? s,
        format: (v): string | null => (!v.length ? null : v.length === 2 && DESVIOS_EM_ABERTO.every((s) => v.includes(s)) ? 'Em aberto' : v.map((s) => STATUS_DESVIO_LABELS[s] ?? s).join(', '))
      }),
      gravidade: cesFilter.list<GravidadeDesvio>([], { label: 'Gravidade', itemLabel: (g) => GRAVIDADE_LABELS[g] ?? g, countLabel: (n) => `${n} selecionadas` }),
      prazoSituacao: cesFilter.value<SituacaoPrazo | null>(null, { label: 'Prazo', param: 'situacaoPrazo', type: 'string', options: () => OPCOES_PRAZO }),
      processo: cesFilter.list<string>([], { label: 'Processo', itemLabel: (id) => this.nomeEm(this.processos(), id) }),
      centroCusto: cesFilter.list<string>([], { label: 'Centro de custo', param: 'centro', itemLabel: (id) => this.nomeEm(this.centros(), id) }),
      base: cesFilter.list<string>([], { label: 'Base', itemLabel: (id) => this.nomeEm(this.bases(), id), countLabel: (n) => `${n} selecionadas` }),
      equipe: cesFilter.list<string>([], { label: 'Equipe', itemLabel: (id) => this.nomeEm(this.equipesOpcoes(), id), countLabel: (n) => `${n} selecionadas` }),
      inspecao: cesFilter.range({ from: null, to: null }, { label: 'Inspeção' })
    },
    sort: [{ key: 'prazo', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<MeuDesvioResponse>((pedido) => this.sst.listarMeusDesvios(this.filtro(pedido)), {
    // 404 = o usuário não está vinculado a um colaborador ativo.
    errorMessage: (e) =>
      (e as { status?: number })?.status === 404 ? getErrorMessage(e) || 'Seu usuário precisa estar vinculado a um colaborador ativo.' : getErrorMessage(e)
  });

  protected readonly selecionados = signal<readonly MeuDesvioResponse[]>([]);
  /** Linhas tratadas aqui: trocadas ou retiradas do recorte sem recarregar a página. */
  private readonly ajustes = signal<{ trocadas: Record<string, MeuDesvioResponse>; removidas: Set<string>; base: unknown }>({ trocadas: {}, removidas: new Set(), base: null });
  protected readonly linhas = computed(() => {
    const linhas = this.resultado.rows();
    const a = this.ajustes();
    return a.base !== linhas ? linhas : linhas.filter((l) => !a.removidas.has(l.id)).map((l) => a.trocadas[l.id] ?? l);
  });
  protected readonly total = computed(() => this.resultado.total() - (this.ajustes().base === this.resultado.rows() ? this.ajustes().removidas.size : 0));

  protected readonly atalhoAtivo = computed(() => {
    const f = this.lista.applied();
    if (f.prazoSituacao === 'VENCIDOS' && !f.status.length) return 'VENCIDOS';
    return f.status.length === 1 && f.prazoSituacao === null ? f.status[0] : null;
  });

  protected readonly colunas: CesDataTableColumn<MeuDesvioResponse>[] = [
    { key: 'gravidade', header: 'Gravidade', type: 'custom', priority: 1, sortField: 'gravidade', value: (d) => d.gravidade, display: (d) => GRAVIDADE_LABELS[d.gravidade] },
    { key: 'status', header: 'Status', type: 'custom', priority: 1, value: (d) => d.status, display: (d) => STATUS_DESVIO_LABELS[d.status] },
    { key: 'desvio', header: 'Desvio', type: 'custom', priority: 1, value: (d) => d.pergunta, display: (d) => [d.pergunta, d.descricao].filter(Boolean).join(' — ') },
    { key: 'processo', header: 'Processo', value: (d) => d.processo?.nome, display: (d) => d.processo?.nome ?? '—' },
    { key: 'local', header: 'Base / equipe', type: 'custom', value: (d) => `${d.baseOperacional?.nome ?? ''} ${d.equipe?.codigo ?? ''}`.trim() },
    { key: 'prazo', header: 'Prazo', type: 'custom', sortField: 'prazo', value: (d) => d.prazo },
    { key: 'resolucao', header: 'Resolvido em', type: 'date', value: (d) => d.dataResolucao },
    { key: 'inspecao', header: 'Inspeção', type: 'date', value: (d) => d.dataInspecao }
  ];
  protected readonly rotulo = (d: MeuDesvioResponse) => d.pergunta || 'Desvio';

  protected readonly salvando = signal(false);
  private emTratamento: MeuDesvioResponse | null = null;
  private emLote = false;

  constructor() {
    inject(MeusDesviosLookupService)
      .carregar()
      .subscribe({
        next: (r) => {
          this.processos.set((r.processos.content ?? []).map((p) => ({ label: p.nome, value: p.id })));
          this.bases.set((r.bases.content ?? []).map((b) => ({ label: b.nome, value: b.id })));
        },
        error: () => undefined
      });
    inject(CentrosCustoLookupService)
      .listar()
      .subscribe({ next: (c: CentroFaturamentoResponse[]) => this.centros.set(c.map((x) => ({ label: x.nome, value: x.id }))), error: () => undefined });
    // Links antigos (`?vencidos=true`, `?apenasVencidos=true`) e notificações.
    const query = inject(ActivatedRoute).snapshot.queryParamMap;
    if ((query.get('vencidos') === 'true' || query.get('apenasVencidos') === 'true') && !query.has('situacaoPrazo')) {
      this.lista.set('prazoSituacao', 'VENCIDOS');
    }
  }

  private nomeEm(opcoes: readonly CesSelectOption<string>[], id: string): string {
    return opcoes.find((o) => o.value === id)?.label ?? 'selecionado';
  }

  private visao(): MeusDesviosFiltrosView {
    const f = this.lista.request().filters;
    return {
      status: [...f.status],
      gravidade: [...f.gravidade],
      apenasVencidos: f.prazoSituacao === 'VENCIDOS' ? true : null,
      idProcesso: [...f.processo],
      idBaseOperacional: [...f.base],
      idCentroCusto: [...f.centroCusto],
      idEquipe: [...f.equipe],
      dataDe: dataLocal(f.inspecao.from),
      dataAte: dataLocal(f.inspecao.to)
    };
  }

  private filtro(pedido: { page: number; size: number; sort: readonly { key: string; direction: string }[] }) {
    const ordem = pedido.sort.map((r) => `${r.key},${r.direction}`)[0] ?? 'prazo,asc';
    return montarMeuDesvioFiltro(null, this.visao(), this.lista.request().query ?? '', pedido.page * pedido.size, pedido.size, ordem);
  }

  protected intervalo(limite: 'from' | 'to', data: Date | null): void {
    this.lista.filters.inspecao.update((atual) => ({ ...atual, [limite]: dataIso(data) }));
  }

  protected alternarAtalho(atalho: Atalho): void {
    if (this.atalhoAtivo() === atalho.chave) {
      this.lista.reset();
      return;
    }
    this.lista.filters.status.set(atalho.status ? [atalho.status] : []);
    this.lista.filters.prazoSituacao.set(atalho.vencidos ? 'VENCIDOS' : null);
    this.lista.apply();
  }

  protected gravidadeLabel(g: GravidadeDesvio): string {
    return GRAVIDADE_LABELS[g] ?? g;
  }

  protected statusLabel(s: StatusDesvio): string {
    return STATUS_DESVIO_LABELS[s] ?? s;
  }

  protected abrirTratamento(d: MeuDesvioResponse): void {
    if (!this.podeTratar) return;
    this.emLote = false;
    this.emTratamento = d;
    this.tratamento().open(d);
  }

  protected abrirTratamentoEmLote(): void {
    const n = this.selecionados().length;
    if (n < 1 || n > LIMITE_LOTE) {
      this.notificacoes.warning(`Selecione entre 1 e ${LIMITE_LOTE} desvios.`);
      return;
    }
    this.emLote = true;
    this.emTratamento = null;
    this.tratamento().open(null, n);
  }

  protected salvarTratamento(request: TratarMeuDesvioRequest): void {
    if (this.salvando()) return;
    if (this.emLote) return this.salvarLote(request);
    const d = this.emTratamento;
    if (!d) return;
    this.salvando.set(true);
    this.sst.tratarMeuDesvio(d.id, request).subscribe({
      next: (atualizado) => {
        this.salvando.set(false);
        this.tratamento().close();
        this.notificacoes.success('Tratamento registrado.');
        this.aplicarLinha(atualizado);
        this.selecionados.update((s) => s.filter((x) => x.id !== atualizado.id));
      },
      error: (e) => this.falharEscrita(e)
    });
  }

  private salvarLote(request: TratarMeuDesvioRequest): void {
    const idsDesvios = [...new Set(this.selecionados().map((d) => d.id))];
    if (!idsDesvios.length || idsDesvios.length > LIMITE_LOTE) return;
    this.salvando.set(true);
    this.sst.tratarMeusDesviosEmMassa({ idsDesvios, ...request }).subscribe({
      next: (r) => {
        this.salvando.set(false);
        this.tratamento().close();
        this.selecionados.set([]);
        this.notificacoes.success(`${r.totalAtualizados} ${r.totalAtualizados === 1 ? 'desvio atualizado' : 'desvios atualizados'}.`);
        this.lista.reload();
      },
      error: (e) => this.falharEscrita(e)
    });
  }

  /** O desvio tratado continua no recorte (troca a linha) ou sai dele (some da página). */
  private aplicarLinha(atualizado: MeuDesvioResponse): void {
    const base = this.resultado.rows();
    const continua = desvioAtendeFiltros(atualizado, null, this.visao());
    this.ajustes.update((a) => {
      const atual = a.base === base ? a : { trocadas: {}, removidas: new Set<string>(), base };
      return continua
        ? { ...atual, trocadas: { ...atual.trocadas, [atualizado.id]: atualizado } }
        : { ...atual, removidas: new Set([...atual.removidas, atualizado.id]) };
    });
  }

  private falharEscrita(e: unknown): void {
    this.salvando.set(false);
    this.tratamento().close();
    this.notificacoes.error(getErrorMessage(e));
    if ([404, 409].includes((e as { status?: number })?.status ?? 0)) this.lista.reload();
  }
}
