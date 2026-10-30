import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal, viewChild } from '@angular/core';
import {
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterPanelComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';
import { map } from 'rxjs';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup as BuscaColaboradores } from '../../../core/lookup/store/remote-lookup';
import { DesvioDetalhadoResponse } from '../../../core/models/desvio.models';
import { UsuarioResponse } from '../../../core/models/iam.models';
import { MeuDesvioResponse, TratarMeuDesvioRequest } from '../../../core/models/meu-desvio.models';
import { EquipeResponse, VeiculoResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { GRAVIDADE_LABELS, GravidadeDesvio, STATUS_DESVIO_LABELS, StatusDesvio } from '../../../core/models/sst.models';
import { AuthService } from '../../../core/services/auth.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, dataIso, dataLocal } from '../../../shared/listagem/listagem';
import { TratamentoMeuDesvioDialogComponent } from '../meus-desvios/tratamento-meu-desvio-dialog.component';
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
import { DesvioDetalheDialogComponent } from './desvio-detalhe-dialog.component';
import { DesviosLookups, DesviosLookupsService } from './desvios-lookups.service';
import { DesviosFiltrosView, desvioAtendeFiltros, montarDesvioFiltro } from './desvios.utils';

type Intervalo = { from: string | null; to: string | null };
const VAZIO: Intervalo = { from: null, to: null };

/** Fim do dia local (para os limites "até" com data e hora). */
function fimDoDia(iso: string | null): Date | null {
  const d = dataLocal(iso);
  if (d) d.setHours(23, 59, 59, 999);
  return d;
}

@Component({
  selector: 'app-sst-desvios',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    AjudaListagemComponent,
    DesvioDetalheDialogComponent,
    TratamentoMeuDesvioDialogComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts>
      <ces-page-header
        title="Desvios"
        icon="pi pi-exclamation-triangle"
        [breadcrumb]="[{ label: 'SST' }, { label: 'Desvios' }]"
        description="Não conformidades apontadas nas inspeções: gravidade, prazo, responsáveis e tratamento."
        helpLabel="Ajuda sobre desvios"
        (help)="ajuda.show()"
      ></ces-page-header>

      <ces-filter-panel #painel storageKey="sst-desvios" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-multiselect label="Status" [options]="opcoesStatus" [(value)]="lista.filters.status" [filter]="false" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Gravidade" [options]="opcoesGravidade" [(value)]="lista.filters.gravidade" [filter]="false" placeholder="Todas"></ces-multiselect>
          <ces-select label="Prazo" [options]="opcoesPrazo" [(value)]="lista.filters.prazoSituacao"></ces-select>
          <ces-multiselect label="Inspetor" [options]="inspetoresOpcoes()" [(value)]="lista.filters.inspetor" placeholder="Todos"
            [loading]="inspetores.loading()" (opened)="inspetores.open()" (search)="inspetores.search($event)"></ces-multiselect>
          <ces-multiselect label="Responsável" [options]="colaboradores.items()" [(value)]="lista.filters.colaborador" placeholder="Todos"
            [loading]="colaboradores.loading()" (search)="colaboradores.search($event)" description="Digite ao menos 2 letras do nome."></ces-multiselect>
          <ces-multiselect label="Base operacional" [options]="opcoes().bases" [(value)]="lista.filters.base" placeholder="Todas"></ces-multiselect>
          <ces-multiselect label="Centro de custo" [options]="opcoes().centros" [(value)]="lista.filters.centroCusto" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Equipe" [options]="equipesOpcoes()" [(value)]="lista.filters.equipe" placeholder="Todas"
            [loading]="equipes.loading()" (opened)="equipes.open()" (search)="equipes.search($event)"></ces-multiselect>
          <ces-multiselect label="Veículo" [options]="veiculosOpcoes()" [(value)]="lista.filters.veiculo" placeholder="Todos"
            [loading]="veiculos.loading()" (opened)="veiculos.open()" (search)="veiculos.search($event)"></ces-multiselect>
          <ces-multiselect label="Processo da inspeção" [options]="opcoes().processos" [(value)]="lista.filters.processo" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Processo do inspetor" [options]="opcoes().processos" [(value)]="lista.filters.processoInspetor" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Formulário" [options]="opcoes().formularios" [(value)]="lista.filters.formulario" placeholder="Todos"></ces-multiselect>
          <ces-datepicker label="Inspeção de" [value]="dataLocal(lista.filters.inspecao().from)" [clearable]="true" (valueChange)="intervalo('inspecao', 'from', $event)"></ces-datepicker>
          <ces-datepicker label="Inspeção até" [value]="dataLocal(lista.filters.inspecao().to)" [clearable]="true" (valueChange)="intervalo('inspecao', 'to', $event)"></ces-datepicker>
          <ces-datepicker label="Prazo de" [value]="dataLocal(lista.filters.prazo().from)" [clearable]="true" (valueChange)="intervalo('prazo', 'from', $event)"></ces-datepicker>
          <ces-datepicker label="Prazo até" [value]="dataLocal(lista.filters.prazo().to)" [clearable]="true" (valueChange)="intervalo('prazo', 'to', $event)"></ces-datepicker>
          <ces-datepicker label="Resolução de" [value]="dataLocal(lista.filters.resolucao().from)" [clearable]="true" (valueChange)="intervalo('resolucao', 'from', $event)"></ces-datepicker>
          <ces-datepicker label="Resolução até" [value]="dataLocal(lista.filters.resolucao().to)" [clearable]="true" (valueChange)="intervalo('resolucao', 'to', $event)"></ces-datepicker>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Desvios"
        mode="server"
        storageKey="sst-desvios"
        exportFileName="desvios"
        searchPlaceholder="Descrição ou ação corretiva"
        rowActionLabel="Ver detalhes"
        rowActionIcon="pi pi-eye"
        noResultsTitle="Nenhum desvio encontrado com esse recorte."
        [columns]="colunas"
        [rows]="linhas()"
        [totalRecords]="total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os desvios' : 'Nenhum desvio registrado'"
        [stateDescription]="resultado.error() ?? 'Os desvios aparecem aqui quando uma inspeção aponta não conformidade.'"
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
        (rowActivate)="detalhe.open($event.id)"
      >
        <ng-template cesCell="gravidade" let-row>
          <ces-status [tone]="gravidadeTom(row.gravidade)" [icon]="gravidadeIcone(row.gravidade)">{{ gravidadeLabel(row.gravidade) }}</ces-status>
        </ng-template>
        <ng-template cesCell="status" let-row>
          <ces-status [tone]="statusTom(row.status)" [icon]="statusIcone(row.status)" emphasis="outline">{{ statusLabel(row.status) }}</ces-status>
        </ng-template>
        <ng-template cesCell="desvio" let-row>
          <span class="ces-cell-strong" [title]="row.pergunta ?? ''">{{ row.pergunta || 'Pergunta não informada' }}</span>
          @if (row.descricao) {
            <span class="ces-cell-muted" [title]="row.descricao">{{ row.descricao }}</span>
          }
        </ng-template>
        <ng-template cesCell="responsaveis" let-row>
          @if (row.colaboradores?.length) {
            <span>{{ row.colaboradores[0].nomeCompleto }}</span>
            @if (row.colaboradores.length > 1) {
              <span class="ces-cell-muted">+{{ row.colaboradores.length - 1 }} pessoas</span>
            }
          } @else {
            <span class="ces-cell-muted">Sem responsável</span>
          }
        </ng-template>
        <ng-template cesCell="local" let-row>
          <span>{{ row.baseOperacional?.nome || '—' }}</span>
          <span class="ces-cell-muted">{{ row.equipe?.codigo || row.equipe?.nome || 'Sem equipe' }}</span>
        </ng-template>
        <ng-template cesCell="prazo" let-row>
          @if (row.prazo) {
            <span>{{ row.prazo | date: 'dd/MM/yyyy' }}</span>
          } @else {
            <span class="ces-cell-muted">Sem prazo</span>
          }
          @if (row.vencido) {
            <ces-status tone="danger" icon="pi-clock">{{ row.diasEmAtraso ? row.diasEmAtraso + ' dias em atraso' : 'Vencido' }}</ces-status>
          }
        </ng-template>
        <ng-template cesRowActions let-row>
          @if (podeTratar) {
            <ces-icon-button icon="pi pi-wrench" [label]="'Tratar desvio: ' + (row.pergunta || row.id)" size="icon-sm" (pressed)="abrirTratamento(row)"></ces-icon-button>
          }
        </ng-template>
      </ces-data-table>
    </div>

    <app-desvio-detalhe-dialog #detalhe [canWrite]="podeTratar" (alterado)="linhaAlterada($event)" />
    <app-tratamento-meu-desvio-dialog #tratamento [loading]="salvando()" (salvar)="salvarTratamento($event)" />

    <app-ajuda-listagem #ajuda titulo="Desvios" entidadePlural="desvios" busca="descrição ou ação corretiva" [podeCriar]="false">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-filter"></i> Recorte inicial</h4>
        <p class="help-text-desc">A lista abre com os desvios <strong>Abertos</strong> e <strong>Em tratamento</strong>, mais urgentes primeiro (prazo). Tire o chip de status para ver também os resolvidos.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-wrench"></i> Tratamento</h4>
        <p class="help-text-desc">Quem tem permissão de escrita em inspeções registra a ação corretiva, o prazo e o novo status pela chave da linha. A linha se atualiza sem recarregar a lista.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class DesviosComponent {
  private readonly sst = inject(SstService);
  private readonly rh = inject(RhService);
  private readonly usuarios = inject(UsuariosService);
  private readonly notificacoes = inject(NotificationService);
  private readonly lookupStore = inject(LookupStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly tratamento = viewChild.required<TratamentoMeuDesvioDialogComponent>('tratamento');

  protected readonly podeTratar = inject(AuthService).currentUser()?.permissoes?.includes('sst:inspecao:write') ?? false;
  protected readonly opcoesStatus = OPCOES_STATUS_DESVIO;
  protected readonly opcoesGravidade = OPCOES_GRAVIDADE;
  protected readonly opcoesPrazo = OPCOES_SITUACAO_PRAZO;
  protected readonly gravidadeTom = (g: GravidadeDesvio) => GRAVIDADE_TOM[g];
  protected readonly gravidadeIcone = (g: GravidadeDesvio) => GRAVIDADE_ICONE[g];
  protected readonly statusTom = (s: StatusDesvio) => STATUS_DESVIO_TOM[s];
  protected readonly statusIcone = (s: StatusDesvio) => STATUS_DESVIO_ICONE[s];
  protected readonly dataLocal = dataLocal;

  private readonly lookups = signal<DesviosLookups | null>(null);
  protected readonly opcoes = computed(() => {
    const l = this.lookups();
    const por = (itens: readonly { id: string; nome?: string; titulo?: string }[] = []): CesSelectOption<string>[] =>
      itens.map((i) => ({ label: i.nome ?? i.titulo ?? i.id, value: i.id }));
    return { bases: por(l?.bases), centros: por(l?.centrosCusto), processos: por(l?.processos), formularios: por(l?.formularios) };
  });
  /** Nomes de pessoas, equipes e veículos escolhidos (chips). */
  private readonly rotulos = signal<Record<string, string>>({});

  protected readonly inspetores = new RemoteLookup<UsuarioResponse>(
    {
      identify: (u) => u.id,
      selectedKeys: () => this.lista.filters.inspetor(),
      load: ({ query, page, size }) => this.usuarios.listarUsuarios({ ativo: true, buscaGlobal: query || undefined, page, size, sort: 'login,asc' })
    },
    this.destroyRef
  );
  protected readonly inspetoresOpcoes = computed<CesSelectOption<string>[]>(() =>
    this.inspetores.items().map((u) => ({ label: u.colaborador?.nomeCompleto ?? u.login, value: u.id }))
  );
  protected readonly colaboradores = new BuscaColaboradores<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () => this.lista.filters.colaborador(),
    destroyRef: this.destroyRef
  });
  protected readonly equipes = new RemoteLookup<EquipeResponse>(
    {
      identify: (e) => e.id,
      selectedKeys: () => this.lista.filters.equipe(),
      load: ({ query, page, size }) => this.rh.listarEquipes({ ativo: true, buscaGlobal: query || undefined, page, size, sort: 'codigo,asc' })
    },
    this.destroyRef
  );
  protected readonly equipesOpcoes = computed<CesSelectOption<string>[]>(() => this.equipes.items().map((e) => ({ label: e.codigo, value: e.id })));
  protected readonly veiculos = new RemoteLookup<VeiculoResponse>(
    {
      identify: (v) => v.id,
      selectedKeys: () => this.lista.filters.veiculo(),
      load: ({ query, page, size }) => this.rh.listarVeiculos({ ativo: true, buscaGlobal: query || undefined, page, size, sort: 'placa,asc' })
    },
    this.destroyRef
  );
  protected readonly veiculosOpcoes = computed<CesSelectOption<string>[]>(() => this.veiculos.items().map((v) => ({ label: v.placa, value: v.id })));

  protected readonly lista = cesListState({
    storageKey: 'sst-desvios',
    filters: {
      status: cesFilter.list<StatusDesvio>([...DESVIOS_EM_ABERTO], {
        label: 'Status',
        itemLabel: (s) => STATUS_DESVIO_LABELS[s] ?? s,
        format: (v): string | null => (!v.length ? null : v.length === 2 && DESVIOS_EM_ABERTO.every((s) => v.includes(s)) ? 'Em aberto' : v.map((s) => STATUS_DESVIO_LABELS[s] ?? s).join(', '))
      }),
      gravidade: cesFilter.list<GravidadeDesvio>([], { label: 'Gravidade', itemLabel: (g) => GRAVIDADE_LABELS[g] ?? g, countLabel: (n) => `${n} selecionadas` }),
      prazoSituacao: cesFilter.value<SituacaoPrazo | null>(null, { label: 'Prazo', param: 'situacaoPrazo', type: 'string', options: () => OPCOES_SITUACAO_PRAZO }),
      inspetor: cesFilter.list<string>([], { label: 'Inspetor', itemLabel: (id) => this.rotulo_(id, 'inspetor selecionado') }),
      colaborador: cesFilter.list<string>([], { label: 'Responsável', param: 'responsavel', itemLabel: (id) => this.rotulo_(id, 'colaborador selecionado') }),
      base: cesFilter.list<string>([], { label: 'Base', itemLabel: (id) => this.nomeEm(this.opcoes().bases, id), countLabel: (n) => `${n} selecionadas` }),
      centroCusto: cesFilter.list<string>([], { label: 'Centro de custo', param: 'centro', itemLabel: (id) => this.nomeEm(this.opcoes().centros, id) }),
      equipe: cesFilter.list<string>([], { label: 'Equipe', itemLabel: (id) => this.rotulo_(id, 'equipe selecionada'), countLabel: (n) => `${n} selecionadas` }),
      veiculo: cesFilter.list<string>([], { label: 'Veículo', itemLabel: (id) => this.rotulo_(id, 'veículo selecionado') }),
      processo: cesFilter.list<string>([], { label: 'Processo', itemLabel: (id) => this.nomeEm(this.opcoes().processos, id) }),
      processoInspetor: cesFilter.list<string>([], { label: 'Processo do inspetor', param: 'processoInspetor', itemLabel: (id) => this.nomeEm(this.opcoes().processos, id) }),
      formulario: cesFilter.list<string>([], { label: 'Formulário', itemLabel: (id) => this.nomeEm(this.opcoes().formularios, id) }),
      pergunta: cesFilter.list<string>([], { label: 'Pergunta', countLabel: (n) => `${n} perguntas`, itemLabel: () => '1 pergunta' }),
      inspecao: cesFilter.range({ ...VAZIO }, { label: 'Inspeção' }),
      prazo: cesFilter.range({ ...VAZIO }, { label: 'Prazo entre' }),
      resolucao: cesFilter.range({ ...VAZIO }, { label: 'Resolução' })
    },
    sort: [{ key: 'prazo', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<DesvioDetalhadoResponse>((pedido) => this.sst.listarDesvios(this.filtro(pedido)), { errorMessage: getErrorMessage });
  /** Linhas tratadas aqui: atualizadas (ou retiradas, se saíram do recorte) sem recarregar. */
  private readonly ajustes = signal<{ trocadas: Record<string, DesvioDetalhadoResponse>; removidas: Set<string>; base: unknown }>({ trocadas: {}, removidas: new Set(), base: null });
  protected readonly linhas = computed(() => {
    const linhas = this.resultado.rows();
    const ajustes = this.ajustes();
    if (ajustes.base !== linhas) return linhas;
    return linhas.filter((l) => !ajustes.removidas.has(l.id)).map((l) => ajustes.trocadas[l.id] ?? l);
  });
  protected readonly total = computed(() => {
    const ajustes = this.ajustes();
    return this.resultado.total() - (ajustes.base === this.resultado.rows() ? ajustes.removidas.size : 0);
  });
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = (_evento: unknown, limite: number) =>
    this.sst.listarDesvios({ ...this.filtro(this.lista.request()), page: 0, size: limite }).pipe(map((r) => r.content));

  protected readonly colunas: CesDataTableColumn<DesvioDetalhadoResponse>[] = [
    { key: 'gravidade', header: 'Gravidade', type: 'custom', priority: 1, sortField: 'gravidade', value: (d) => d.gravidade, display: (d) => GRAVIDADE_LABELS[d.gravidade] },
    { key: 'status', header: 'Status', type: 'custom', priority: 1, sortField: 'status', value: (d) => d.status, display: (d) => STATUS_DESVIO_LABELS[d.status] },
    { key: 'desvio', header: 'Desvio', type: 'custom', priority: 1, value: (d) => d.pergunta, display: (d) => [d.pergunta, d.descricao].filter(Boolean).join(' — ') },
    { key: 'responsaveis', header: 'Responsáveis', type: 'custom', value: (d) => d.colaboradores?.map((c) => c.nomeCompleto).join(', ') },
    { key: 'inspetor', header: 'Inspetor', value: (d) => d.inspetor, display: (d) => d.inspetor || '—' },
    { key: 'local', header: 'Base / equipe', type: 'custom', value: (d) => `${d.baseOperacional?.nome ?? ''} ${d.equipe?.codigo ?? ''}`.trim() },
    { key: 'prazo', header: 'Prazo', type: 'custom', sortField: 'prazo', value: (d) => d.prazo, display: (d) => (d.prazo ? new Date(`${d.prazo.substring(0, 10)}T00:00:00`).toLocaleDateString('pt-BR') : 'Sem prazo') },
    { key: 'inspecao', header: 'Inspeção', type: 'date', sortField: 'inspecao.dataRealizacao', value: (d) => d.dataInspecao }
  ];
  protected readonly rotulo = (d: DesvioDetalhadoResponse) => d.pergunta || 'Desvio';

  protected readonly salvando = signal(false);
  private emTratamento: DesvioDetalhadoResponse | null = null;

  constructor() {
    inject(DesviosLookupsService).carregar().subscribe({
      next: (l) => {
        this.lookups.set(l);
        this.equipes.setSeed(l.equipes);
        this.veiculos.setSeed(l.veiculos);
        this.lembrar([...l.equipes.map((e) => [e.id, e.codigo]), ...l.veiculos.map((v) => [v.id, v.placa])] as [string, string][]);
      },
      error: () => undefined
    });
  }

  private lembrar(pares: [string, string][]): void {
    this.rotulos.update((r) => ({ ...r, ...Object.fromEntries(pares) }));
  }

  private rotulo_(id: string, padrao: string): string {
    return (
      this.rotulos()[id] ??
      this.inspetoresOpcoes().find((o) => o.value === id)?.label ??
      this.colaboradores.items().find((o) => o.value === id)?.label ??
      padrao
    );
  }

  private nomeEm(opcoes: readonly CesSelectOption<string>[], id: string): string {
    return opcoes.find((o) => o.value === id)?.label ?? 'selecionado';
  }

  /** Visão dos filtros no formato que `montarDesvioFiltro` (e a revalidação de linha) espera. */
  private visao(): DesviosFiltrosView {
    const f = this.lista.request().filters;
    return {
      status: [...f.status],
      gravidade: [...f.gravidade],
      apenasVencidos: f.prazoSituacao === 'VENCIDOS' ? true : null,
      semPrazo: f.prazoSituacao === 'SEM_PRAZO' ? true : null,
      idInspetor: [...f.inspetor],
      idColaborador: [...f.colaborador],
      idFormulario: [...f.formulario],
      idProcesso: [...f.processo],
      idProcessoInspetor: [...f.processoInspetor],
      idBaseOperacional: [...f.base],
      idCentroCusto: [...f.centroCusto],
      idEquipe: [...f.equipe],
      idVeiculo: [...f.veiculo],
      idPergunta: [...f.pergunta],
      dataInspecaoDe: dataLocal(f.inspecao.from),
      dataInspecaoAte: fimDoDia(f.inspecao.to),
      prazoDe: dataLocal(f.prazo.from),
      prazoAte: dataLocal(f.prazo.to),
      dataResolucaoDe: dataLocal(f.resolucao.from),
      dataResolucaoAte: fimDoDia(f.resolucao.to)
    };
  }

  private filtro(pedido: { page: number; size: number; sort: readonly { key: string; direction: string }[] }) {
    const ordem = pedido.sort.map((r) => `${r.key},${r.direction}`)[0] ?? 'prazo,asc';
    return montarDesvioFiltro(this.visao(), this.lista.request().query ?? '', pedido.page * pedido.size, pedido.size, ordem);
  }

  protected intervalo(filtro: 'inspecao' | 'prazo' | 'resolucao', limite: 'from' | 'to', data: Date | null): void {
    this.lista.filters[filtro].update((atual) => ({ ...atual, [limite]: dataIso(data) }));
  }

  protected gravidadeLabel(g: GravidadeDesvio): string {
    return GRAVIDADE_LABELS[g] ?? g;
  }

  protected statusLabel(s: StatusDesvio): string {
    return STATUS_DESVIO_LABELS[s] ?? s;
  }

  protected abrirTratamento(d: DesvioDetalhadoResponse): void {
    this.emTratamento = d;
    // O diálogo de tratamento usa o formato de "meu desvio"; os campos usados existem nos dois.
    this.tratamento().open(d as unknown as MeuDesvioResponse);
  }

  protected salvarTratamento(request: TratarMeuDesvioRequest): void {
    const d = this.emTratamento;
    if (this.salvando() || !d) return;
    this.salvando.set(true);
    this.sst.tratarDesvio(d.idInspecao, d.id, { status: request.status, acaoCorretiva: request.acaoCorretiva || undefined, prazo: request.prazo }).subscribe({
      next: () => {
        this.salvando.set(false);
        this.tratamento().close();
        this.notificacoes.success('Tratamento registrado.');
        this.linhaAlterada(d.id);
      },
      error: (e) => {
        this.salvando.set(false);
        this.tratamento().close();
        this.notificacoes.error(getErrorMessage(e));
        if ([404, 409].includes((e as { status?: number })?.status ?? 0)) this.lista.reload();
      }
    });
  }

  /** Revalida a linha alterada: continua no recorte (atualiza) ou sai dele (some da página). */
  protected linhaAlterada(id: string): void {
    this.sst.buscarDesvioPorId(id).subscribe({
      next: (atualizado) => {
        const base = this.resultado.rows();
        this.ajustes.update((a) => {
          const atual = a.base === base ? a : { trocadas: {}, removidas: new Set<string>(), base };
          if (desvioAtendeFiltros(atualizado, this.visao(), this.lista.request().query ?? '')) {
            return { ...atual, trocadas: { ...atual.trocadas, [id]: atualizado } };
          }
          return { ...atual, removidas: new Set([...atual.removidas, id]) };
        });
      },
      error: (e) => {
        if ([404, 409].includes((e as { status?: number })?.status ?? 0)) this.lista.reload();
      }
    });
  }
}
