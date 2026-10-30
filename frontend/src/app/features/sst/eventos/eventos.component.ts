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
  CesTone,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup as BuscaColaboradores } from '../../../core/lookup/store/remote-lookup';
import { EquipeResponse, VeiculoResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import {
  EventoSstResumoResponse,
  REGISTRO_EVIDENCIA_LABELS,
  RegistroEvidencia,
  STATUS_EVENTO_LABELS,
  StatusEventoSst,
  TIPO_EVENTO_LABELS,
  TipoEventoSst,
  opcoesDe
} from '../../../core/models/sst-evento.models';
import { AuthService } from '../../../core/services/auth.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { dataIso, dataLocal } from '../../../shared/listagem/listagem';
import { EventosImportacaoComponent } from './eventos-importacao.component';
import { EventosLookups, EventosLookupsService } from './eventos-lookups.service';
import { EventosFiltrosView, baixarCsv, montarEventoFiltro, montarEventoFiltroExportacao } from './eventos.utils';

const SIM_NAO: CesSelectOption<boolean | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Sim', value: true },
  { label: 'Não', value: false }
];
const TIPOS = opcoesDe(TIPO_EVENTO_LABELS);
const STATUS = opcoesDe(STATUS_EVENTO_LABELS);
const EVIDENCIAS = opcoesDe(REGISTRO_EVIDENCIA_LABELS);
const VAZIO = { from: null, to: null };
type Catalogo = 'classificacoes' | 'atividades' | 'processos' | 'bases' | 'centrosCusto' | 'locaisLesao';

const TOM_TIPO: Record<TipoEventoSst, CesTone> = {
  ACIDENTE_TIPICO: 'danger',
  ACIDENTE_TRAJETO: 'warning',
  INCIDENTE_SEM_LESAO: 'neutral',
  DANO_MATERIAL: 'info',
  DOENCA_OCUPACIONAL: 'danger'
} as Record<TipoEventoSst, CesTone>;
const TOM_STATUS: Record<StatusEventoSst, CesTone> = {
  REGISTRADO: 'danger',
  EM_TRATATIVA: 'warning',
  CONCLUIDO: 'success',
  CANCELADO: 'neutral'
} as Record<StatusEventoSst, CesTone>;

/**
 * Listagem de eventos de segurança — a visão do SESMT sobre a acidentalidade.
 *
 * A tabela é montada **sem nenhuma consulta extra**: atividade, gestor, base, equipe e
 * veículo já vêm copiados no evento (regra de snapshot). Os filtros usam os mesmos nomes de
 * parâmetro que o drill-down do dashboard manda (`?idAtividade=`, `?tipo=`...).
 */
@Component({
  selector: 'app-sst-eventos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
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
    EventosImportacaoComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="canWrite" (newRequested)="novo()">
      <ces-page-header
        title="Eventos de segurança"
        icon="pi pi-exclamation-triangle"
        [breadcrumb]="[{ label: 'Segurança (SST)' }, { label: 'Eventos de segurança' }]"
        description="Acidentes, incidentes e doenças ocupacionais: comunicação, lesão, CAT e tratativa. Clique na linha para abrir o evento."
        helpLabel="Ajuda sobre eventos de segurança"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-download" label="Exportar CSV" [loading]="exportando()" (pressed)="exportar()"></ces-button>
        @if (canWrite) {
          <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacaoAberta.set(true)"></ces-button>
        }
        @if (canWrite) {
          <ces-button actions icon="pi pi-plus" label="Registrar evento" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel #painel storageKey="sst-eventos" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-multiselect label="Tipo de ocorrência" [options]="tipos" [(value)]="lista.filters.tipo" [filter]="false" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Status" [options]="statusOpcoes" [(value)]="lista.filters.status" [filter]="false" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Classificação" [options]="opcoes('classificacoes')" [(value)]="lista.filters.idClassificacao" placeholder="Todas"></ces-multiselect>
          <ces-multiselect label="Atividade" [options]="opcoes('atividades')" [(value)]="lista.filters.idAtividade" placeholder="Todas"></ces-multiselect>
          <ces-multiselect label="Base operacional" [options]="opcoes('bases')" [(value)]="lista.filters.idBaseOperacional" placeholder="Todas"></ces-multiselect>
          <ces-multiselect label="Centro de custo" [options]="opcoes('centrosCusto')" [(value)]="lista.filters.idCentroCusto" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Processo" [options]="opcoes('processos')" [(value)]="lista.filters.idProcesso" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Equipe" [options]="equipesOpcoes()" [(value)]="lista.filters.idEquipe" placeholder="Todas"
            [loading]="equipes.loading()" (opened)="equipes.open()" (search)="equipes.search($event)"></ces-multiselect>
          <ces-multiselect label="Veículo" [options]="veiculosOpcoes()" [(value)]="lista.filters.idVeiculo" placeholder="Todos"
            [loading]="veiculos.loading()" (opened)="veiculos.open()" (search)="veiculos.search($event)"></ces-multiselect>
          <ces-multiselect label="Gestor responsável" [options]="colaboradores.items()" [(value)]="lista.filters.idGestor" placeholder="Todos"
            [loading]="colaboradores.loading()" (search)="colaboradores.search($event)" description="Digite ao menos 2 letras do nome."></ces-multiselect>
          <ces-multiselect label="Técnico de segurança" [options]="colaboradores.items()" [(value)]="lista.filters.idTecnico" placeholder="Todos"
            [loading]="colaboradores.loading()" (search)="colaboradores.search($event)"></ces-multiselect>
          <ces-multiselect label="Colaborador envolvido" [options]="colaboradores.items()" [(value)]="lista.filters.idColaboradorEnvolvido" placeholder="Todos"
            [loading]="colaboradores.loading()" (search)="colaboradores.search($event)"></ces-multiselect>
          <ces-multiselect label="Local da lesão" [options]="opcoes('locaisLesao')" [(value)]="lista.filters.idLocalLesao" placeholder="Todos"></ces-multiselect>
          <ces-select label="Lesão" [options]="simNao" [(value)]="lista.filters.comLesao"></ces-select>
          <ces-select label="CAT emitida" [options]="simNao" [(value)]="lista.filters.possuiCat"></ces-select>
          <ces-multiselect label="Telemetria" [options]="evidencias" [(value)]="lista.filters.telemetria" [filter]="false" placeholder="Todas"></ces-multiselect>
          <ces-multiselect label="Filmagem" [options]="evidencias" [(value)]="lista.filters.filmagem" [filter]="false" placeholder="Todas"></ces-multiselect>
          <ces-text-input label="Nome do colaborador" [(value)]="lista.filters.nomeColaborador"></ces-text-input>
          <ces-text-input label="Nome do terceiro" [(value)]="lista.filters.nomeTerceiro"></ces-text-input>
          <ces-datepicker label="Ocorridos a partir de" [value]="dataLocal(lista.filters.ocorrencia().from)" [clearable]="true" (valueChange)="periodo('ocorrencia', 'from', $event)"></ces-datepicker>
          <ces-datepicker label="Ocorridos até" [value]="dataLocal(lista.filters.ocorrencia().to)" [clearable]="true" (valueChange)="periodo('ocorrencia', 'to', $event)"></ces-datepicker>
          <ces-datepicker label="Comunicados a partir de" [value]="dataLocal(lista.filters.comunicacao().from)" [clearable]="true" (valueChange)="periodo('comunicacao', 'from', $event)"></ces-datepicker>
          <ces-datepicker label="Comunicados até" [value]="dataLocal(lista.filters.comunicacao().to)" [clearable]="true" (valueChange)="periodo('comunicacao', 'to', $event)"></ces-datepicker>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Eventos"
        mode="server"
        storageKey="sst-eventos"
        searchPlaceholder="Nº do registro, descrição, local, atividade ou base"
        rowActionLabel="Abrir evento"
        rowActionIcon="pi pi-external-link"
        [emptyActionLabel]="canWrite ? 'Registrar evento' : undefined"
        noResultsTitle="Nenhum evento encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os eventos' : 'Nenhum evento registrado'"
        [stateDescription]="resultado.error() ?? 'Acidentes, incidentes e doenças ocupacionais registrados aparecem aqui.'"
        [filtered]="lista.filtered()"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="novo()"
        (rowActivate)="abrir($event)"
      >
        <ng-template cesCell="registro" let-row>
          <span class="ces-cell-strong">{{ row.numeroRegistro }}</span>
          @if (row.comunicacaoAtrasada) {
            <ces-status tone="danger" icon="pi-clock" emphasis="outline">{{ row.horasAteComunicacao }}h para comunicar</ces-status>
          } @else {
            <span class="ces-cell-muted">Comunicado em {{ row.horasAteComunicacao }}h</span>
          }
        </ng-template>
        <ng-template cesCell="tipo" let-row><ces-status [tone]="tipoTone(row.tipo)">{{ tipoLabel(row.tipo) }}</ces-status></ng-template>
        <ng-template cesCell="status" let-row><ces-status [tone]="statusTone(row.status)">{{ statusLabel(row.status) }}</ces-status></ng-template>
        <ng-template cesCell="atividade" let-row>
          <span [title]="row.atividadeNome">{{ row.atividadeNome }}</span>
          <span class="ces-cell-muted">{{ row.classificacaoNome || 'Sem classificação' }}</span>
        </ng-template>
        <ng-template cesCell="local" let-row>
          <span [title]="row.baseNome">{{ row.baseNome }}</span>
          <span class="ces-cell-muted">{{ row.equipeCodigo || 'Sem equipe' }}{{ row.veiculoPlaca ? ' · ' + row.veiculoPlaca : '' }}</span>
        </ng-template>
        <ng-template cesCell="lesao" let-row>
          @if (row.comLesao) {
            <ces-status tone="danger">{{ row.quantidadeAcidentados }} acidentado(s){{ row.diasPerdidos ? ' · ' + row.diasPerdidos + 'd perdidos' : '' }}</ces-status>
          } @else {
            <span class="ces-cell-muted">Sem lesão</span>
          }
          @if (row.possuiCat) { <ces-status tone="brand">CAT emitida</ces-status> }
        </ng-template>
      </ces-data-table>
    </div>

    <app-eventos-importacao [(visible)]="importacaoAberta" [lookups]="lookups()" (processado)="lista.reload()"></app-eventos-importacao>

    <app-ajuda-listagem #ajuda titulo="Eventos de segurança" artigoPlural="os" entidadePlural="eventos" busca="nº do registro, descrição, local, atividade ou base"
      rotuloNovo="Registrar evento" [podeCriar]="canWrite" [importacao]="canWrite">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-clock"></i> Prazo de comunicação</h4>
        <p class="help-text-desc">Cada tipo de evento tem um prazo legal para comunicação; o registro mostra em quantas horas foi comunicado e marca os atrasados.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-chart-bar"></i> Vindo do painel</h4>
        <p class="help-text-desc">Os links do painel de eventos abrem esta lista já filtrada pelo item clicado.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class EventosComponent {
  private readonly router = inject(Router);
  private readonly sst = inject(SstService);
  private readonly rh = inject(RhService);
  private readonly notifications = inject(NotificationService);
  private readonly lookupStore = inject(LookupStore);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly canWrite = (inject(AuthService).currentUser()?.permissoes ?? []).includes('sst:evento:write');
  protected readonly lookups = signal<EventosLookups | null>(null);
  protected readonly exportando = signal(false);
  protected readonly importacaoAberta = signal(false);
  protected readonly tipos = TIPOS;
  protected readonly statusOpcoes = STATUS;
  protected readonly evidencias = EVIDENCIAS;
  protected readonly simNao = SIM_NAO;
  protected readonly dataLocal = dataLocal;
  /** Nomes de pessoas, equipes e veículos escolhidos (chips). */
  private readonly rotulos = signal<Record<string, string>>({});

  protected readonly colaboradores = new BuscaColaboradores<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () => [...this.lista.filters.idGestor(), ...this.lista.filters.idTecnico(), ...this.lista.filters.idColaboradorEnvolvido()],
    destroyRef: this.destroyRef
  });
  protected readonly equipes = new RemoteLookup<EquipeResponse>(
    {
      identify: (e) => e.id,
      selectedKeys: () => this.lista.filters.idEquipe(),
      load: ({ query, page, size }) => this.rh.listarEquipes({ ativo: true, buscaGlobal: query || undefined, page, size, sort: 'codigo,asc' })
    },
    this.destroyRef
  );
  protected readonly equipesOpcoes = computed<CesSelectOption<string>[]>(() => this.equipes.items().map((e) => ({ label: e.codigo, value: e.id })));
  protected readonly veiculos = new RemoteLookup<VeiculoResponse>(
    {
      identify: (v) => v.id,
      selectedKeys: () => this.lista.filters.idVeiculo(),
      load: ({ query, page, size }) => this.rh.listarVeiculos({ ativo: true, buscaGlobal: query || undefined, page, size, sort: 'placa,asc' })
    },
    this.destroyRef
  );
  protected readonly veiculosOpcoes = computed<CesSelectOption<string>[]>(() => this.veiculos.items().map((v) => ({ label: v.placa, value: v.id })));

  protected readonly lista = cesListState({
    storageKey: 'sst-eventos',
    filters: {
      tipo: cesFilter.list<TipoEventoSst>([], { label: 'Tipo', itemLabel: (t) => TIPO_EVENTO_LABELS[t] ?? t }),
      status: cesFilter.list<StatusEventoSst>([], { label: 'Status', itemLabel: (s) => STATUS_EVENTO_LABELS[s] ?? s }),
      idClassificacao: cesFilter.list<string>([], { label: 'Classificação', itemLabel: (id) => this.nome('classificacoes', id) }),
      idAtividade: cesFilter.list<string>([], { label: 'Atividade', itemLabel: (id) => this.nome('atividades', id) }),
      idBaseOperacional: cesFilter.list<string>([], { label: 'Base', itemLabel: (id) => this.nome('bases', id), countLabel: (n) => `${n} selecionadas` }),
      idCentroCusto: cesFilter.list<string>([], { label: 'Centro de custo', itemLabel: (id) => this.nome('centrosCusto', id) }),
      idProcesso: cesFilter.list<string>([], { label: 'Processo', itemLabel: (id) => this.nome('processos', id) }),
      idEquipe: cesFilter.list<string>([], { label: 'Equipe', itemLabel: (id) => this.rotulo_(id, 'equipe selecionada'), countLabel: (n) => `${n} selecionadas` }),
      idVeiculo: cesFilter.list<string>([], { label: 'Veículo', itemLabel: (id) => this.rotulo_(id, 'veículo selecionado') }),
      idGestor: cesFilter.list<string>([], { label: 'Gestor', itemLabel: (id) => this.rotulo_(id, 'gestor selecionado') }),
      idTecnico: cesFilter.list<string>([], { label: 'Técnico', itemLabel: (id) => this.rotulo_(id, 'técnico selecionado') }),
      idColaboradorEnvolvido: cesFilter.list<string>([], { label: 'Envolvido', itemLabel: (id) => this.rotulo_(id, 'colaborador selecionado') }),
      idLocalLesao: cesFilter.list<string>([], { label: 'Local da lesão', itemLabel: (id) => this.nome('locaisLesao', id) }),
      telemetria: cesFilter.list<RegistroEvidencia>([], { label: 'Telemetria', itemLabel: (v) => REGISTRO_EVIDENCIA_LABELS[v] ?? v }),
      filmagem: cesFilter.list<RegistroEvidencia>([], { label: 'Filmagem', itemLabel: (v) => REGISTRO_EVIDENCIA_LABELS[v] ?? v }),
      comLesao: cesFilter.value<boolean | null>(null, { label: 'Lesão', options: () => SIM_NAO }),
      possuiCat: cesFilter.value<boolean | null>(null, { label: 'CAT', options: () => SIM_NAO }),
      nomeColaborador: cesFilter.value<string>('', { label: 'Colaborador' }),
      nomeTerceiro: cesFilter.value<string>('', { label: 'Terceiro' }),
      ocorrencia: cesFilter.range({ ...VAZIO }, { label: 'Ocorrência' }),
      comunicacao: cesFilter.range({ ...VAZIO }, { label: 'Comunicação' })
    },
    sort: [{ key: 'ocorrencia', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<EventoSstResumoResponse>(
    (pedido) =>
      this.sst.listarEventos(
        montarEventoFiltro(this.visao(), pedido.query, pedido.page * pedido.size, pedido.size, pedido.sort.map((r) => `${r.key},${r.direction}`)[0] ?? 'dataOcorrencia,desc')
      ),
    { errorMessage: getErrorMessage }
  );

  protected readonly colunas: CesDataTableColumn<EventoSstResumoResponse>[] = [
    { key: 'registro', header: 'Registro', type: 'custom', priority: 1, sortField: 'numeroRegistro', value: (e) => e.numeroRegistro },
    { key: 'tipo', header: 'Tipo', type: 'custom', priority: 1, sortField: 'tipo', value: (e) => TIPO_EVENTO_LABELS[e.tipo] ?? e.tipo },
    { key: 'status', header: 'Status', type: 'custom', priority: 1, sortField: 'status', value: (e) => STATUS_EVENTO_LABELS[e.status] ?? e.status },
    {
      key: 'ocorrencia',
      header: 'Ocorrência',
      type: 'date',
      sortField: 'dataOcorrencia',
      value: (e) => e.dataOcorrencia,
      display: (e) => new Date(e.dataOcorrencia).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
    },
    { key: 'atividade', header: 'Atividade', type: 'custom', value: (e) => e.atividadeNome },
    { key: 'local', header: 'Base / equipe', type: 'custom', value: (e) => e.baseNome },
    { key: 'gestor', header: 'Gestor', value: (e) => e.gestorNome },
    { key: 'lesao', header: 'Lesão', type: 'custom', value: (e) => (e.comLesao ? `${e.quantidadeAcidentados} acidentado(s)` : 'Sem lesão') }
  ];
  protected readonly rotulo = (e: EventoSstResumoResponse) => `Evento ${e.numeroRegistro}`;

  constructor() {
    inject(EventosLookupsService)
      .carregar()
      .subscribe({
        next: (l) => {
          this.lookups.set(l);
          this.equipes.setSeed(l.equipes);
          this.veiculos.setSeed(l.veiculos);
          this.rotulos.update((r) => ({
            ...r,
            ...Object.fromEntries([...l.equipes.map((e) => [e.id, e.codigo]), ...l.veiculos.map((v) => [v.id, v.placa])])
          }));
        },
        error: () => undefined
      });
  }

  protected opcoes(catalogo: Catalogo): CesSelectOption<string>[] {
    return ((this.lookups()?.[catalogo] ?? []) as { id: string; nome?: string; codigo?: string }[]).map((i) => ({ label: i.nome ?? i.codigo ?? i.id, value: i.id }));
  }

  private nome(catalogo: Catalogo, id: string): string {
    return this.opcoes(catalogo).find((o) => o.value === id)?.label ?? 'selecionado';
  }

  private rotulo_(id: string, padrao: string): string {
    return this.rotulos()[id] ?? this.colaboradores.items().find((o) => o.value === id)?.label ?? padrao;
  }

  /** Recorte aplicado no formato que `montarEventoFiltro` (e a exportação) espera. */
  private visao(): EventosFiltrosView {
    const f = this.lista.request().filters;
    return {
      tipo: [...f.tipo],
      status: [...f.status],
      idClassificacao: [...f.idClassificacao],
      idAtividade: [...f.idAtividade],
      idGestor: [...f.idGestor],
      idProcesso: [...f.idProcesso],
      idBaseOperacional: [...f.idBaseOperacional],
      idCentroCusto: [...f.idCentroCusto],
      idEquipe: [...f.idEquipe],
      idVeiculo: [...f.idVeiculo],
      idTecnico: [...f.idTecnico],
      idColaboradorEnvolvido: [...f.idColaboradorEnvolvido],
      idLocalLesao: [...f.idLocalLesao],
      telemetria: [...f.telemetria],
      filmagem: [...f.filmagem],
      possuiCat: f.possuiCat,
      comLesao: f.comLesao,
      dataOcorrenciaDe: dataLocal(f.ocorrencia.from),
      dataOcorrenciaAte: dataLocal(f.ocorrencia.to),
      dataComunicacaoDe: dataLocal(f.comunicacao.from),
      dataComunicacaoAte: dataLocal(f.comunicacao.to),
      nomeColaborador: f.nomeColaborador,
      nomeTerceiro: f.nomeTerceiro
    };
  }

  protected periodo(filtro: 'ocorrencia' | 'comunicacao', ponta: 'from' | 'to', data: Date | null): void {
    this.lista.filters[filtro].update((atual) => ({ ...atual, [ponta]: dataIso(data) }));
  }

  protected novo(): void {
    if (this.canWrite) void this.router.navigate(['/sst/eventos/novo']);
  }

  /** O evento abre em outra aba para não perder o recorte da lista. */
  protected abrir(item: EventoSstResumoResponse): void {
    window.open(`/sst/eventos/${item.id}`, '_blank');
  }

  /** Exporta exatamente o que a listagem devolveria — mesmos filtros, sem paginação. */
  protected exportar(): void {
    if (this.exportando()) return;
    this.exportando.set(true);
    this.sst.exportarEventos(montarEventoFiltroExportacao(this.visao(), this.lista.request().query)).subscribe({
      next: (blob) => {
        this.exportando.set(false);
        baixarCsv(blob, 'eventos_sst.csv');
        this.notifications.success('O download do CSV foi iniciado.');
      },
      error: (err) => {
        this.exportando.set(false);
        this.notifications.error(getErrorMessage(err), { title: 'Erro ao exportar' });
      }
    });
  }

  protected tipoLabel(value: TipoEventoSst): string {
    return TIPO_EVENTO_LABELS[value] ?? value;
  }

  protected tipoTone(value: TipoEventoSst): CesTone {
    return TOM_TIPO[value] ?? 'neutral';
  }

  protected statusLabel(value: StatusEventoSst): string {
    return STATUS_EVENTO_LABELS[value] ?? value;
  }

  protected statusTone(value: StatusEventoSst): CesTone {
    return TOM_STATUS[value] ?? 'neutral';
  }
}
