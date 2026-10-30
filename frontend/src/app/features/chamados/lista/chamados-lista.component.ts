import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  CesButtonComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterPanelComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  cesFilter,
  cesListState
} from '@ces/ui';
import { catchError, map, of } from 'rxjs';

import { Chamado, ChamadoFiltros, ChamadoPrioridade, ChamadoStatus, EquipeChamado, TopicoChamado } from '../../../core/models/chamado.models';
import { UsuarioResponse } from '../../../core/models/iam.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ChamadosService } from '../../../core/services/chamados.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, dataIso, dataLocal, paginaPadrao } from '../../../shared/listagem/listagem';
import {
  CHAMADO_PRIORIDADE_LABELS,
  CHAMADO_STATUS_LABELS,
  PRIORIDADE_OPTIONS,
  STATUS_OPTIONS,
  fimDoDiaIso,
  prazoIcon,
  prazoTom,
  prioridadeTom,
  statusTom
} from '../chamados-ui';

const SIM_NAO: CesSelectOption<boolean | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Sim', value: true },
  { label: 'Não', value: false }
];
const STATUS: CesSelectOption<ChamadoStatus | null>[] = [{ label: 'Todos', value: null }, ...STATUS_OPTIONS];
const PRIORIDADES: CesSelectOption<ChamadoPrioridade | null>[] = [{ label: 'Todas', value: null }, ...PRIORIDADE_OPTIONS];

@Component({
  selector: 'app-chamados-lista',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="!modoCentral" (newRequested)="novo()">
      <ces-page-header
        [title]="titulo"
        icon="pi pi-ticket"
        [breadcrumb]="[{ label: 'Chamados' }, { label: modoCentral ? 'Central de atendimento' : 'Meus chamados' }]"
        [description]="modoCentral ? 'Chamados das equipes de atendimento, do prazo de resolução mais próximo ao mais distante.' : 'Chamados que você abriu, da última interação para trás.'"
        helpLabel="Ajuda sobre chamados"
        (help)="ajuda.show()"
      >
        @if (!modoCentral) {
          <ces-button actions icon="pi pi-plus" label="Novo chamado" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel #painel [storageKey]="chave" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-text-input label="Protocolo" [value]="lista.filters.protocolo() ?? ''" (valueChange)="lista.filters.protocolo.set($event || null)" placeholder="CH-AAAAMMDD-..."></ces-text-input>
          <ces-select label="Tópico" [options]="topicosOpcoes()" [(value)]="lista.filters.topicoId" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          @if (modoCentral) {
            <ces-select label="Equipe" [options]="equipesOpcoes()" [(value)]="lista.filters.equipeId" [clearable]="true" [filter]="true" placeholder="Todas"
              [loading]="equipes.loading()" (opened)="equipes.open()" (search)="equipes.search($event)"></ces-select>
          }
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.status"></ces-select>
          <ces-select label="Prioridade" [options]="prioridadeOpcoes" [(value)]="lista.filters.prioridade"></ces-select>
          @if (modoCentral) {
            <ces-select label="Solicitante" [options]="usuariosOpcoes()" [(value)]="lista.filters.solicitanteId" [clearable]="true" [filter]="true" placeholder="Todos"
              [loading]="usuarios.loading()" (opened)="usuarios.open()" (search)="usuarios.search($event)"></ces-select>
            <ces-select label="Responsável" [options]="usuariosOpcoes()" [(value)]="lista.filters.responsavelId" [clearable]="true" [filter]="true" placeholder="Todos"
              [loading]="usuarios.loading()" (opened)="usuarios.open()" (search)="usuarios.search($event)"></ces-select>
          }
          <ces-datepicker label="Abertos a partir de" [value]="dataLocal(lista.filters.abertura().from)" [clearable]="true" (valueChange)="abertura('from', $event)"></ces-datepicker>
          <ces-datepicker label="Abertos até" [value]="dataLocal(lista.filters.abertura().to)" [clearable]="true" (valueChange)="abertura('to', $event)"></ces-datepicker>
          <ces-select label="1ª resposta vencida" [options]="simNao" [(value)]="lista.filters.primeiraRespostaVencida"></ces-select>
          <ces-select label="Resolução vencida" [options]="simNao" [(value)]="lista.filters.resolucaoVencida"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        [title]="titulo"
        mode="server"
        [storageKey]="chave"
        exportFileName="chamados"
        searchPlaceholder="Protocolo, assunto ou descrição"
        rowActionLabel="Abrir"
        rowActionIcon="pi pi-arrow-right"
        [emptyActionLabel]="modoCentral ? undefined : 'Novo chamado'"
        noResultsTitle="Nenhum chamado encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os chamados' : modoCentral ? 'Nenhum chamado na fila' : 'Você ainda não abriu chamados'"
        [stateDescription]="resultado.error() ?? (modoCentral ? 'Os chamados abertos para as suas equipes aparecem aqui.' : 'Abra um chamado para pedir ajuda a uma equipe de atendimento.')"
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
        (rowActivate)="abrir($event)"
      >
        <ng-template cesCell="protocolo" let-row><span class="ces-cell-strong">{{ row.protocolo }}</span></ng-template>
        <ng-template cesCell="assunto" let-row>
          <span>{{ row.assunto }}</span>
          <span class="ces-cell-muted">{{ row.topico?.nome }}</span>
        </ng-template>
        <ng-template cesCell="atendimento" let-row>
          <span>{{ row.equipeAtendimento?.nome }}</span>
          @if (modoCentral) {
            <span class="ces-cell-muted">{{ nomeUsuario(row.usuarioResponsavelId) }}</span>
          }
        </ng-template>
        <ng-template cesCell="prioridade" let-row>
          <ces-status [tone]="prioridadeTom(row.prioridade)">{{ prioridadeLabel(row.prioridade) }}</ces-status>
        </ng-template>
        <ng-template cesCell="status" let-row>
          <ces-status [tone]="statusTom(row.status)">{{ statusLabel(row.status) }}</ces-status>
        </ng-template>
        <ng-template cesCell="prazo" let-row>
          <ces-status [tone]="prazoTom(row)" [icon]="prazoIcone(row)" emphasis="outline">{{ row.dataLimiteResolucao | date: 'dd/MM/yy HH:mm' }}</ces-status>
        </ng-template>
      </ces-data-table>
    </div>

    <app-ajuda-listagem #ajuda [titulo]="titulo" entidadePlural="chamados" busca="protocolo, assunto ou descrição" rotuloNovo="Novo chamado" [podeCriar]="!modoCentral">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-clock"></i> Prazo (SLA)</h4>
        <p class="help-text-desc">A coluna Prazo mostra o limite de resolução: verde no prazo, amarelo a menos de 4 horas, vermelho vencido, cinza pausado ou cancelado. Os filtros "1ª resposta vencida" e "Resolução vencida" isolam os atrasados.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-arrow-right"></i> Detalhes</h4>
        <p class="help-text-desc">Abrir o chamado mostra a linha do tempo, os anexos e as ações disponíveis.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class ChamadosListaComponent {
  private readonly service = inject(ChamadosService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly router = inject(Router);

  protected readonly modoCentral = inject(ActivatedRoute).snapshot.data['modo'] === 'central';
  protected readonly titulo = this.modoCentral ? 'Fila de chamados' : 'Meus chamados';
  protected readonly chave = this.modoCentral ? 'chamados-fila' : 'chamados-meus';
  protected readonly statusOpcoes = STATUS;
  protected readonly prioridadeOpcoes = PRIORIDADES;
  protected readonly simNao = SIM_NAO;
  protected readonly dataLocal = dataLocal;
  protected readonly statusTom = statusTom;
  protected readonly prioridadeTom = prioridadeTom;
  protected readonly prazoTom = prazoTom;
  protected readonly statusLabel = (s: ChamadoStatus) => CHAMADO_STATUS_LABELS[s];
  protected readonly prioridadeLabel = (p: ChamadoPrioridade) => CHAMADO_PRIORIDADE_LABELS[p];

  protected readonly topicos = signal<TopicoChamado[]>([]);
  protected readonly topicosOpcoes = computed<CesSelectOption<string>[]>(() => this.topicos().map((t) => ({ label: t.nome, value: t.id })));
  /** Logins de usuários já conhecidos (filtros e coluna de responsável). */
  private readonly logins = signal<Record<string, string>>({});
  private readonly destroyRef = inject(DestroyRef);

  protected readonly equipes = new RemoteLookup<EquipeChamado>(
    {
      identify: (e) => e.id,
      selectedKeys: () => (this.lista.filters.equipeId() ? [this.lista.filters.equipeId()!] : []),
      load: ({ query, page, size }) => this.service.listarEquipes({ ativo: true, buscaGlobal: query || undefined, page, size, sort: 'nome,asc' })
    },
    this.destroyRef
  );
  protected readonly equipesOpcoes = computed<CesSelectOption<string>[]>(() => this.equipes.items().map((e) => ({ label: e.nome, value: e.id })));
  protected readonly usuarios = new RemoteLookup<UsuarioResponse>(
    {
      identify: (u) => u.id,
      selectedKeys: () => [this.lista.filters.solicitanteId(), this.lista.filters.responsavelId()].filter((id): id is string => !!id),
      load: ({ query, page, size }) => this.usuariosService.listarUsuarios({ ativo: true, buscaGlobal: query || undefined, page, size, sort: 'login,asc' })
    },
    this.destroyRef
  );
  protected readonly usuariosOpcoes = computed<CesSelectOption<string>[]>(() => this.usuarios.items().map((u) => ({ label: u.login, value: u.id })));

  protected readonly lista = cesListState({
    storageKey: this.chave,
    filters: {
      protocolo: cesFilter.value<string | null>(null, { label: 'Protocolo' }),
      topicoId: cesFilter.value<string | null>(null, {
        label: 'Tópico',
        param: 'topico',
        format: (id): string | null => (id ? (this.topicos().find((t) => t.id === id)?.nome ?? 'tópico selecionado') : null)
      }),
      equipeId: cesFilter.value<string | null>(null, {
        label: 'Equipe',
        param: 'equipe',
        format: (id): string | null => (id ? (this.equipes.items().find((e) => e.id === id)?.nome ?? 'equipe selecionada') : null)
      }),
      status: cesFilter.value<ChamadoStatus | null>(null, { label: 'Status', type: 'string', options: () => STATUS }),
      prioridade: cesFilter.value<ChamadoPrioridade | null>(null, { label: 'Prioridade', type: 'string', options: () => PRIORIDADES }),
      solicitanteId: cesFilter.value<string | null>(null, { label: 'Solicitante', param: 'solicitante', format: (id): string | null => (id ? this.nomeUsuario(id) : null) }),
      responsavelId: cesFilter.value<string | null>(null, { label: 'Responsável', param: 'responsavel', format: (id): string | null => (id ? this.nomeUsuario(id) : null) }),
      abertura: cesFilter.range({ from: null, to: null }, { label: 'Abertura' }),
      primeiraRespostaVencida: cesFilter.value<boolean | null>(null, { label: '1ª resposta vencida', param: 'primeiraResposta', options: () => SIM_NAO }),
      resolucaoVencida: cesFilter.value<boolean | null>(null, { label: 'Resolução vencida', param: 'resolucao', options: () => SIM_NAO })
    },
    sort: [this.modoCentral ? { key: 'prazo', direction: 'asc' } : { key: 'ultima', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<Chamado>((pedido) => this.listar(this.filtro(pedido)), { errorMessage: getErrorMessage });
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = (_evento: unknown, limite: number) =>
    this.listar({ ...this.filtro(this.lista.request()), page: 0, size: limite }).pipe(map((r) => r.content));

  protected readonly colunas: CesDataTableColumn<Chamado>[] = [
    { key: 'protocolo', header: 'Protocolo', type: 'custom', priority: 1, sortField: 'protocolo', value: (c) => c.protocolo, display: (c) => c.protocolo },
    { key: 'assunto', header: 'Chamado', type: 'custom', priority: 1, value: (c) => c.assunto, display: (c) => `${c.assunto} (${c.topico?.nome ?? ''})` },
    { key: 'atendimento', header: 'Atendimento', type: 'custom', value: (c) => c.equipeAtendimento?.nome, display: (c) => c.equipeAtendimento?.nome ?? '—' },
    { key: 'prioridade', header: 'Prioridade', type: 'custom', sortField: 'prioridade', value: (c) => c.prioridade, display: (c) => CHAMADO_PRIORIDADE_LABELS[c.prioridade] },
    { key: 'status', header: 'Status', type: 'custom', priority: 1, sortField: 'status', value: (c) => c.status, display: (c) => CHAMADO_STATUS_LABELS[c.status] },
    { key: 'prazo', header: 'Prazo de resolução', type: 'custom', sortField: 'dataLimiteResolucao', value: (c) => c.dataLimiteResolucao, display: (c) => new Date(c.dataLimiteResolucao).toLocaleString('pt-BR') },
    { key: 'ultima', header: 'Última interação', type: 'date', sortField: 'dataUltimaInteracao', value: (c) => c.dataUltimaInteracao }
  ];
  protected readonly rotulo = (c: Chamado) => c.protocolo;

  constructor() {
    this.service.listarTopicosAtivos().subscribe({ next: (t) => this.topicos.set(t), error: () => undefined });
    // Logins do solicitante/responsável da página e dos filtros vindos da URL.
    effect(() => {
      const ids = [
        ...this.resultado.rows().flatMap((c) => [c.usuarioSolicitanteId, c.usuarioResponsavelId]),
        this.lista.filters.solicitanteId(),
        this.lista.filters.responsavelId()
      ];
      untracked(() => this.carregarLogins(ids));
    });
  }

  private listar(filtros: ChamadoFiltros) {
    return this.modoCentral ? this.service.listarFila(filtros) : this.service.listarMeus(filtros);
  }

  private filtro(pedido: Parameters<typeof paginaPadrao>[0]): ChamadoFiltros {
    const { filters, query } = this.lista.request();
    const de = dataLocal(filters.abertura.from);
    const ate = dataLocal(filters.abertura.to);
    return {
      ...(paginaPadrao(pedido) as { page: number; size: number; sort?: string }),
      buscaGlobal: query || undefined,
      protocolo: filters.protocolo?.trim() || undefined,
      topicoId: filters.topicoId ?? undefined,
      equipeId: this.modoCentral ? (filters.equipeId ?? undefined) : undefined,
      prioridade: filters.prioridade ?? undefined,
      status: filters.status ?? undefined,
      usuarioSolicitanteId: this.modoCentral ? (filters.solicitanteId ?? undefined) : undefined,
      usuarioResponsavelId: this.modoCentral ? (filters.responsavelId ?? undefined) : undefined,
      dataAberturaInicio: de ? fimDoDiaIso(de, false) : undefined,
      dataAberturaFim: ate ? fimDoDiaIso(ate, true) : undefined,
      slaPrimeiraRespostaVencido: filters.primeiraRespostaVencida ?? undefined,
      slaResolucaoVencido: filters.resolucaoVencida ?? undefined,
      ativo: true
    };
  }

  private carregarLogins(ids: readonly (string | null | undefined)[]): void {
    const conhecidos = this.logins();
    new Set(ids.filter((id): id is string => !!id && !(id in conhecidos))).forEach((id) =>
      this.usuariosService
        .buscarUsuarioPorId(id)
        .pipe(catchError(() => of(null)))
        .subscribe((u) => {
          this.logins.update((atual) => ({ ...atual, [id]: u?.login ?? 'usuário removido' }));
          if (u) this.usuarios.setSeed([u]);
        })
    );
  }

  protected nomeUsuario(id?: string | null): string {
    if (!id) return 'Não atribuído';
    return this.logins()[id] ?? this.usuarios.items().find((u) => u.id === id)?.login ?? '…';
  }

  protected prazoIcone(c: Chamado): string {
    return prazoIcon(c).replace('pi ', '');
  }

  protected abertura(limite: 'from' | 'to', data: Date | null): void {
    this.lista.filters.abertura.update((atual) => ({ ...atual, [limite]: dataIso(data) }));
  }

  protected abrir(c: Chamado): void {
    void this.router.navigate([this.modoCentral ? '/chamados/fila' : '/chamados/meus', c.id]);
  }

  protected novo(): void {
    if (!this.modoCentral) void this.router.navigate(['/chamados/novo']);
  }
}
