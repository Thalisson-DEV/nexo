import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { DbService } from '../../core/db/db.service';
import { BaseOperacionalLocal, EquipeLocal, ProcessoLocal } from '../../core/db/models';
import { ApiError } from '../../core/http/error.interceptor';
import { ConnectivityService } from '../../core/sync/connectivity.service';
import { ToastService } from '../../core/ui/toast.service';
import { SessaoService } from '../../core/sessao/sessao.service';
import { MeuDesvioService } from './meu-desvio.service';
import { ChipsComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, OpcaoSeletor, SegmentadoComponent, SeletorComponent, TituloGrandeComponent, VazioComponent, dataCurta } from '../../ui';
import { AbasSecaoComponent } from '../../ui/abas-secao.component';
import { abasSeguranca } from '../shell/abas-seguranca';
import {
  GravidadeDesvio,
  MeuDesvio,
  MeuDesvioFilter,
  StatusDesvio,
  TratarDesvioRequest,
} from './meu-desvio.models';

type AbaDesvio = 'pendentes' | 'abertos' | 'em_tratamento' | 'resolvidos' | 'todos';
type GravidadeFilter = GravidadeDesvio | '';
type VencimentoFilter = 'todos' | 'vencidos';

const ABAS: readonly { value: AbaDesvio; label: string }[] = [
  { value: 'pendentes', label: 'Pendentes' },
  { value: 'abertos', label: 'Abertos' },
  { value: 'em_tratamento', label: 'Em tratamento' },
  { value: 'resolvidos', label: 'Resolvidos' },
  { value: 'todos', label: 'Todos' },
];

/**
 * Desvios atribuídos ao usuário (consulta online). Filtros e página ficam na URL,
 * que a sessão guarda; folhas abertas e o tratamento em edição ficam no estado de tela.
 */
@Component({
  selector: 'app-meus-desvios',
  standalone: true,
  imports: [AbasSecaoComponent, ChipsComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, SegmentadoComponent, SeletorComponent, TituloGrandeComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-titulo-grande titulo="Desvios" [subtitulo]="subtitulo()">
      <ces-indicador-rede />
      <button type="button" class="ces-btn ces-btn--neutro ces-btn--compacto filtro" (click)="abrirFiltros()" [attr.aria-label]="'Filtros' + (activeFiltersCount() ? ', ' + activeFiltersCount() + ' ativos' : '')">
        <ces-icone nome="filtro" [tamanho]="18" />@if (activeFiltersCount()) { <span class="ces-num">{{ activeFiltersCount() }}</span> } @else { Filtrar }
      </button>
    </ces-titulo-grande>
    @if (abasSeguranca().length) { <ces-abas-secao rotulo="Segurança" [abas]="abasSeguranca()" /> }

    @if (!connectivity.isOnline()) {
      <ces-vazio icone="nuvem" titulo="Desvios precisam de internet" texto="A lista vem direto do servidor. Conecte e ela aparece aqui." />
    } @else if (blockedMessage()) {
      <ces-vazio icone="alerta" titulo="Consulta indisponível" [texto]="blockedMessage()" />
    } @else {
      <div class="busca">
        <ces-icone nome="busca" [tamanho]="18" />
        <input type="search" placeholder="Pergunta, equipe, base…" enterkeyhint="search" aria-label="Buscar desvios"
          [value]="buscaInput()" (input)="onBuscaChange($any($event.target).value)">
      </div>
      <ces-chips rotulo="Situação" [opcoes]="abasChips" [valor]="aba()" (valorChange)="selecionarAba($any($event))" />

      @if (desvios().length && !isLoading()) {
        <div class="barra-sel">
          @if (modoSelecao()) {
            <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="toggleSelecionarPagina()">
              {{ paginaTodaSelecionada() ? 'Desmarcar página' : 'Marcar página' }}</button>
            <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="sairSelecao()">Cancelar</button>
          } @else {
            <span class="ces-num total">{{ page().totalElements }} {{ page().totalElements === 1 ? 'desvio' : 'desvios' }}</span>
            @if (desviosSelecionaveis().length > 1) {
              <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="modoSelecao.set(true)">Tratar vários</button>
            }
          }
        </div>
      }

      @if (isLoading()) {
        <div class="ces-esqueletos" aria-busy="true" aria-label="Carregando">
          @for (i of [1, 2, 3]; track i) { <div class="ces-esqueleto" style="height: 96px"></div> }
        </div>
      } @else if (desvios().length) {
        <ul class="ces-grupo" aria-label="Desvios">
          @for (d of desvios(); track d.id) {
            <li class="ces-linha-lista" [attr.data-ancora]="d.id">
              <button type="button" class="ces-linha ces-linha--acao desvio" [class.vencido]="d.vencido && d.status !== 'RESOLVIDO'"
                [attr.role]="modoSelecao() ? 'checkbox' : null" [attr.aria-checked]="modoSelecao() ? isSelecionado(d.id) : null"
                [disabled]="modoSelecao() && d.status === 'RESOLVIDO'" (click)="modoSelecao() ? toggleSelecao(d.id) : abrirTratamento(d)">
                @if (modoSelecao()) {
                  <span class="caixa" [class.marcada]="isSelecionado(d.id)" aria-hidden="true"><ces-icone nome="check" [tamanho]="16" /></span>
                }
                <span class="corpo">
                  <span class="topo">
                    <span class="ces-etiqueta" [class]="'ces-etiqueta ces-etiqueta--' + tomGravidade(d.gravidade)">{{ gravidadeLabel(d.gravidade) }}</span>
                    <span class="ces-etiqueta" [class]="'ces-etiqueta ces-etiqueta--' + tomStatus(d.status)">{{ statusLabel(d.status) }}</span>
                  </span>
                  <span class="ces-linha__titulo">{{ d.pergunta || 'Desvio' }}</span>
                  @if (d.descricao) { <span class="desc">{{ d.descricao }}</span> }
                  <span class="ces-linha__apoio ces-num">{{ equipeLabel(d) }}{{ d.baseOperacional?.nome ? ' · ' + d.baseOperacional?.nome : '' }} · inspeção {{ data(d.dataInspecao) }}</span>
                  @if (d.prazo && d.status !== 'RESOLVIDO') {
                    <span class="prazo ces-num" [class.atrasado]="d.vencido"><ces-icone nome="relogio" [tamanho]="14" />{{ d.vencido ? 'Venceu em ' : 'Prazo ' }}{{ data(d.prazo) }}</span>
                  }
                </span>
              </button>
            </li>
          }
        </ul>
        @if (page().totalPages > 1) {
          <nav class="paginas" aria-label="Páginas">
            <button type="button" class="ces-btn ces-btn--neutro ces-btn--compacto" (click)="paginaAnterior()" [disabled]="pageNumber() === 0">Anterior</button>
            <span class="ces-num">Página {{ pageNumber() + 1 }} de {{ page().totalPages }}</span>
            <button type="button" class="ces-btn ces-btn--neutro ces-btn--compacto" (click)="proximaPagina()" [disabled]="pageNumber() + 1 >= page().totalPages">Próxima</button>
          </nav>
        }
        <div class="ces-respiro"></div>
      } @else {
        <ces-vazio icone="desvio" [titulo]="aba() === 'pendentes' && !activeFiltersCount() && !busca() ? 'Nenhum desvio pendente' : 'Nada encontrado'"
          [texto]="activeFiltersCount() || busca() ? 'Mude a busca ou os filtros.' : 'Quando uma inspeção apontar um desvio para você, ele aparece aqui.'">
          @if (activeFiltersCount()) { <button type="button" class="ces-btn ces-btn--tonal" (click)="limparFiltros()">Limpar filtros</button> }
        </ces-vazio>
      }
    }

    @if (modoSelecao() && selectedCount()) {
      <footer class="ces-rodape-acoes">
        <button type="button" class="ces-btn ces-btn--principal" (click)="abrirTratamentoEmMassa()">Tratar {{ selectedCount() }} {{ selectedCount() === 1 ? 'desvio' : 'desvios' }}</button>
      </footer>
    }

    <ces-folha [aberta]="isFilterSheetOpen()" (fechar)="fecharFiltros()" titulo="Filtros">
      <div class="campos">
        <ces-segmentado rotulo="Gravidade" [opcoes]="opcoesGravidade" [valor]="gravidade()" (valorChange)="gravidade.set($any($event))" />
        <button type="button" role="switch" class="interruptor" [attr.aria-checked]="vencimento() === 'vencidos'" (click)="vencimento.set(vencimento() === 'vencidos' ? 'todos' : 'vencidos')">
          <span class="txt"><b>Só os vencidos</b><span>Prazo já passou</span></span><span class="trilho" aria-hidden="true"><span></span></span>
        </button>
        <ces-seletor rotulo="Processo" placeholder="Todos" [opcoes]="opcoesProcesso()" [valor]="idProcesso() || null" (valorChange)="idProcesso.set($event ?? '')" />
        <ces-seletor rotulo="Base" placeholder="Todas" [opcoes]="opcoesBase()" [valor]="idBase() || null" (valorChange)="idBase.set($event ?? '')" />
        <ces-seletor rotulo="Equipe" placeholder="Todas" [opcoes]="opcoesEquipe()" [valor]="idEquipe() || null" (valorChange)="idEquipe.set($event ?? '')" />
        <div class="datas">
          <div class="ces-campo"><label for="de">Inspeção de</label><input id="de" class="ces-input" type="date" [value]="dataDe()" (input)="dataDe.set($any($event.target).value)"></div>
          <div class="ces-campo"><label for="ate">até</label><input id="ate" class="ces-input" type="date" [value]="dataAte()" (input)="dataAte.set($any($event.target).value)"></div>
        </div>
        <ces-seletor rotulo="Ordem" [opcoes]="opcoesOrdem" [valor]="sort()" (valorChange)="sort.set($event ?? 'prazo,asc')" />
      </div>
      <div rodape class="ces-folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="limparFiltros()">Limpar</button>
        <button type="button" class="ces-btn ces-btn--principal" (click)="aplicarFiltros()">Mostrar</button>
      </div>
    </ces-folha>

    <ces-folha [aberta]="isTreatmentOpen()" (fechar)="fecharTratamento()" [titulo]="treatmentItemId() ? 'Tratar desvio' : 'Tratar ' + selectedCount() + ' desvios'"
      [descricao]="desvioEmTratamento()?.pergunta ?? null">
      <div class="campos">
        <ces-segmentado rotulo="Situação" [opcoes]="opcoesStatus" [valor]="treatmentStatus()" (valorChange)="definirTratamento({ status: $any($event) })" />
        <div class="ces-campo">
          <label for="acao">O que foi ou será feito</label>
          <textarea id="acao" class="ces-input" rows="3" [value]="treatmentAction()" (input)="definirTratamento({ acao: $any($event.target).value })"
            placeholder="Ex.: luva trocada e equipe orientada"></textarea>
        </div>
        <div class="ces-campo">
          <label for="prazo">Prazo</label>
          <input id="prazo" class="ces-input" type="date" [value]="treatmentDeadline()" (input)="definirTratamento({ prazo: $any($event.target).value })">
        </div>
      </div>
      <div rodape class="ces-folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="fecharTratamento()" [disabled]="isSubmitting()">Cancelar</button>
        <button type="button" class="ces-btn ces-btn--principal" (click)="confirmarTratamento()" [disabled]="isSubmitting() || !connectivity.isOnline()">
          {{ isSubmitting() ? 'Salvando…' : 'Salvar' }}</button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: block; position: relative; min-height: 100%; }
    .filtro { color: var(--ces-texto); gap: 4px; white-space: nowrap; flex: none; }
    .ces-rodape-acoes { bottom: calc(var(--ces-barra-abas) + var(--ces-safe-base)); padding-bottom: var(--ces-e3); }
    .busca { margin: var(--ces-e1) var(--ces-e3) 0; display: flex; align-items: center; gap: var(--ces-e2); padding: 0 var(--ces-e4); min-height: 48px;
      border-radius: var(--ces-r-pilula); background: var(--ces-superficie); color: var(--ces-texto-2); }
    .busca input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ces-texto); font: var(--ces-t-corpo); font-size: 1rem; }
    .busca input:focus { outline: none; }
    .barra-sel { display: flex; align-items: center; justify-content: space-between; padding: var(--ces-e2) var(--ces-e4) var(--ces-e1); }
    .total { font: var(--ces-t-corpo-forte); color: var(--ces-texto-2); }
    .desvio { align-items: flex-start; border-left: 4px solid transparent; }
    .desvio.vencido { border-left-color: var(--ces-erro); }
    .corpo { flex: 1; min-width: 0; display: grid; gap: 4px; }
    .topo { display: flex; gap: 6px; flex-wrap: wrap; }
    .desc { font: var(--ces-t-corpo); color: var(--ces-texto); overflow-wrap: anywhere; }
    .prazo { display: inline-flex; align-items: center; gap: 4px; font: 600 .8125rem/1.3 var(--ces-fonte); color: var(--ces-texto-2); }
    .prazo.atrasado { color: var(--ces-erro); }
    .caixa { width: 24px; height: 24px; margin-top: 2px; border-radius: 7px; border: 2px solid var(--ces-linha-forte); display: grid; place-items: center; color: transparent; flex: none; }
    .caixa.marcada { background: var(--ces-destaque); border-color: var(--ces-destaque); color: var(--ces-destaque-texto); }
    .paginas { display: flex; align-items: center; justify-content: space-between; gap: var(--ces-e3); padding: var(--ces-e4); font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .campos { display: grid; gap: var(--ces-e3); padding: 0 var(--ces-e4); }
    .datas { display: grid; grid-template-columns: 1fr 1fr; gap: var(--ces-e3); }
    textarea.ces-input { min-height: 96px; resize: vertical; font: var(--ces-t-corpo); font-size: 1rem; }
    .interruptor { display: flex; align-items: center; gap: var(--ces-e3); min-height: 64px; padding: var(--ces-e3) var(--ces-e4); border: 0;
      border-radius: var(--ces-r-grupo); background: var(--ces-superficie); color: var(--ces-texto); text-align: left; cursor: pointer; }
    .interruptor .txt { flex: 1; display: grid; gap: 2px; }
    .interruptor .txt span { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .trilho { width: 48px; height: 28px; border-radius: 14px; background: var(--ces-linha-forte); position: relative; flex: none; transition: background-color var(--ces-m-entrar); }
    .trilho span { position: absolute; top: 3px; left: 3px; width: 22px; height: 22px; border-radius: 50%; background: #fff; transition: transform var(--ces-m-entrar) var(--ces-curva); }
    [aria-checked='true'] .trilho { background: var(--ces-destaque); }
    [aria-checked='true'] .trilho span { transform: translateX(20px); }
  `],
})
export class MeusDesviosComponent implements OnInit, OnDestroy {
  readonly abasSeguranca = abasSeguranca();
  readonly authService = inject(AuthService);
  readonly connectivity = inject(ConnectivityService);
  private readonly service = inject(MeuDesvioService);
  private readonly dbService = inject(DbService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly sessao = inject(SessaoService);

  readonly abasChips = ABAS.map(a => ({ valor: a.value, rotulo: a.label }));
  readonly opcoesGravidade = [
    { valor: '', rotulo: 'Todas' }, { valor: 'BAIXA', rotulo: 'Baixa' }, { valor: 'MEDIA', rotulo: 'Média' },
    { valor: 'ALTA', rotulo: 'Alta' }, { valor: 'CRITICA', rotulo: 'Crítica' },
  ];
  readonly opcoesStatus = [
    { valor: 'ABERTO', rotulo: 'Aberto' }, { valor: 'EM_TRATAMENTO', rotulo: 'Tratando' }, { valor: 'RESOLVIDO', rotulo: 'Resolvido' },
  ];
  readonly opcoesOrdem: OpcaoSeletor[] = [
    { valor: 'prazo,asc', rotulo: 'Prazo mais próximo' },
    { valor: 'dataInspecao,desc', rotulo: 'Inspeções mais recentes' },
    { valor: 'gravidade,desc', rotulo: 'Maior gravidade' },
  ];
  readonly modoSelecao = signal(false);

  readonly tabs = ABAS;
  readonly gravidades: readonly { value: GravidadeFilter; label: string }[] = [
    { value: '', label: 'Todas' },
    { value: 'BAIXA', label: 'Baixa' },
    { value: 'MEDIA', label: 'Média' },
    { value: 'ALTA', label: 'Alta' },
    { value: 'CRITICA', label: 'Crítica' },
  ];

  readonly desvios = signal<MeuDesvio[]>([]);
  readonly processos = signal<ProcessoLocal[]>([]);
  readonly bases = signal<BaseOperacionalLocal[]>([]);
  readonly equipes = signal<EquipeLocal[]>([]);
  readonly page = signal({ size: 20, number: 0, totalElements: 0, totalPages: 0 });
  readonly isLoading = signal(true);
  readonly blockedMessage = signal('');

  readonly aba = signal<AbaDesvio>('pendentes');
  readonly busca = signal('');
  readonly buscaInput = signal('');
  readonly gravidade = signal<GravidadeFilter>('');
  readonly vencimento = signal<VencimentoFilter>('todos');
  readonly idProcesso = signal('');
  readonly idBase = signal('');
  readonly idEquipe = signal('');
  readonly dataDe = signal('');
  readonly dataAte = signal('');
  readonly sort = signal('prazo,asc');
  readonly pageNumber = signal(0);
  readonly isFilterSheetOpen = signal(false);

  readonly selectedIds = signal<Set<string>>(new Set());
  readonly selectedCount = computed(() => this.selectedIds().size);
  readonly desviosSelecionaveis = computed(() =>
    this.desvios().filter(item => item.status !== 'RESOLVIDO')
  );
  readonly paginaTodaSelecionada = computed(() =>
    this.desviosSelecionaveis().length > 0
      && this.desviosSelecionaveis().every(item => this.selectedIds().has(item.id))
  );

  readonly isTreatmentOpen = signal(false);
  readonly treatmentItemId = signal<string | null>(null);
  readonly treatmentStatus = signal<StatusDesvio>('EM_TRATAMENTO');
  readonly treatmentAction = signal('');
  readonly treatmentDeadline = signal('');
  readonly isSubmitting = signal(false);

  readonly activeFiltersCount = computed(() => {
    let total = 0;
    if (this.gravidade()) total++;
    if (this.vencimento() === 'vencidos') total++;
    if (this.idProcesso()) total++;
    if (this.idBase()) total++;
    if (this.idEquipe()) total++;
    if (this.dataDe()) total++;
    if (this.dataAte()) total++;
    if (this.sort() !== 'prazo,asc') total++;
    return total;
  });

  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private initialized = false;
  private lastOnline = this.connectivity.isOnline();

  constructor() {
    effect(() => {
      const online = this.connectivity.isOnline();
      if (this.initialized && online && !this.lastOnline) {
        void this.carregar();
      }
      this.lastOnline = online;
    });
  }

  async ngOnInit(): Promise<void> {
    this.restaurarUrl();
    await this.carregarCatalogos();
    this.initialized = true;
    await this.carregar();
    this.restaurarFolha();
  }

  ngOnDestroy(): void {
    if (this.searchTimer) clearTimeout(this.searchTimer);
  }

  onBuscaChange(value: string): void {
    this.buscaInput.set(value);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.busca.set(value.trim());
      this.pageNumber.set(0);
      this.selectedIds.set(new Set());
      this.atualizarUrl();
      void this.carregar();
    }, 400);
  }

  selecionarAba(aba: AbaDesvio): void {
    if (aba === this.aba()) return;
    this.aba.set(aba);
    this.pageNumber.set(0);
    this.selectedIds.set(new Set());
    this.atualizarUrl();
    void this.carregar();
  }

  aplicarFiltros(): void {
    this.isFilterSheetOpen.set(false);
    this.sessao.definirEstadoTela({ folha: null });
    this.pageNumber.set(0);
    this.selectedIds.set(new Set());
    this.atualizarUrl();
    void this.carregar();
  }

  limparFiltros(): void {
    this.gravidade.set('');
    this.vencimento.set('todos');
    this.idProcesso.set('');
    this.idBase.set('');
    this.idEquipe.set('');
    this.dataDe.set('');
    this.dataAte.set('');
    this.sort.set('prazo,asc');
    this.aplicarFiltros();
  }

  isSelecionado(id: string): boolean {
    return this.selectedIds().has(id);
  }

  toggleSelecao(id: string): void {
    const desvio = this.desvios().find(item => item.id === id);
    if (!desvio || desvio.status === 'RESOLVIDO') return;
    const next = new Set(this.selectedIds());
    next.has(id) ? next.delete(id) : next.add(id);
    this.selectedIds.set(next);
  }

  toggleSelecionarPagina(): void {
    const next = new Set(this.selectedIds());
    if (this.paginaTodaSelecionada()) {
      this.desviosSelecionaveis().forEach(item => next.delete(item.id));
    } else {
      this.desviosSelecionaveis().forEach(item => {
        if (next.size < 100) next.add(item.id);
      });
    }
    this.selectedIds.set(next);
  }

  abrirTratamento(desvio: MeuDesvio): void {
    if (desvio.status === 'RESOLVIDO') {
      this.toast.warning('Desvios resolvidos não podem receber novos tratamentos.');
      return;
    }
    this.treatmentItemId.set(desvio.id);
    this.treatmentStatus.set(desvio.status);
    this.treatmentAction.set(desvio.acaoCorretiva ?? '');
    this.treatmentDeadline.set(desvio.prazo ?? '');
    this.isTreatmentOpen.set(true);
    this.guardarTratamento();
  }

  abrirTratamentoEmMassa(): void {
    this.removerResolvidosDaSelecao();
    if (this.selectedCount() < 1 || this.selectedCount() > 100) {
      this.toast.warning('Selecione entre 1 e 100 desvios.');
      return;
    }
    this.treatmentItemId.set(null);
    this.treatmentStatus.set('EM_TRATAMENTO');
    this.treatmentAction.set('');
    this.treatmentDeadline.set('');
    this.isTreatmentOpen.set(true);
    this.guardarTratamento();
  }

  fecharTratamento(): void {
    if (this.isSubmitting()) return;
    this.isTreatmentOpen.set(false);
    this.sessao.definirEstadoTela({ folha: null, tratar: null });
  }

  async confirmarTratamento(): Promise<void> {
    if (this.isSubmitting()) return;

    const request = this.criarTratamentoRequest();
    this.isSubmitting.set(true);
    try {
      const itemId = this.treatmentItemId();
      if (itemId) {
        const desvio = this.desvios().find(item => item.id === itemId);
        if (!desvio || desvio.status === 'RESOLVIDO') {
          this.toast.warning('Este desvio já foi resolvido e não pode mais ser tratado.');
          this.isTreatmentOpen.set(false);
          return;
        }
        const atualizado = await firstValueFrom(this.service.tratar(itemId, request));
        this.aplicarAtualizacaoLocal(atualizado);
        this.toast.success('Tratamento salvo.');
      } else {
        this.removerResolvidosDaSelecao();
        const ids = [...this.selectedIds()];
        if (ids.length < 1 || ids.length > 100) {
          this.toast.warning('Selecione entre 1 e 100 desvios.');
          return;
        }
        const result = await firstValueFrom(
          this.service.tratarEmMassa({ idsDesvios: ids, ...request })
        );
        this.selectedIds.set(new Set());
        await this.carregar();
        this.toast.success(`${result.totalAtualizados} ${result.totalAtualizados === 1 ? 'desvio atualizado' : 'desvios atualizados'}.`);
        this.modoSelecao.set(false);
      }
      this.isTreatmentOpen.set(false);
      this.sessao.definirEstadoTela({ folha: null, tratar: null });
    } catch (error) {
      const status = (error as ApiError)?.status;
      if (status === 404 || status === 409) {
        this.selectedIds.set(new Set());
        await this.carregar();
        this.isTreatmentOpen.set(false);
      }
    } finally {
      this.isSubmitting.set(false);
    }
  }

  paginaAnterior(): void {
    if (this.pageNumber() === 0) return;
    this.pageNumber.update(value => value - 1);
    this.mudarPagina();
  }

  proximaPagina(): void {
    if (this.pageNumber() + 1 >= this.page().totalPages) return;
    this.pageNumber.update(value => value + 1);
    this.mudarPagina();
  }

  gravidadeLabel(gravidade: GravidadeDesvio): string {
    return ({ BAIXA: 'Gravidade baixa', MEDIA: 'Gravidade média', ALTA: 'Gravidade alta', CRITICA: 'Crítico' } as Record<GravidadeDesvio, string>)[gravidade];
  }

  statusLabel(status: StatusDesvio): string {
    const labels: Record<StatusDesvio, string> = {
      ABERTO: 'Aberto',
      EM_TRATAMENTO: 'Em tratamento',
      RESOLVIDO: 'Resolvido',
    };
    return labels[status];
  }

  equipeLabel(desvio: MeuDesvio): string {
    return desvio.equipe?.codigo || desvio.equipe?.nome || 'Não identificada';
  }

  formatarDataLocal(value: string): string {
    const [ano, mes, dia] = value.split('-');
    return ano && mes && dia ? `${dia}/${mes}/${ano}` : value;
  }

  readonly opcoesProcesso = computed<OpcaoSeletor[]>(() => this.processos().map(p => ({ valor: p.id, rotulo: p.nome })));
  readonly opcoesBase = computed<OpcaoSeletor[]>(() => this.bases().map(b => ({ valor: b.id, rotulo: b.nome })));
  readonly opcoesEquipe = computed<OpcaoSeletor[]>(() => this.equipes().map(e => ({ valor: e.id, rotulo: e.codigo })));
  readonly desvioEmTratamento = computed(() => this.desvios().find(d => d.id === this.treatmentItemId()) ?? null);
  readonly subtitulo = computed(() => {
    if (!this.connectivity.isOnline()) return 'Sem internet';
    const n = this.page().totalElements;
    const aba = this.aba() === 'todos' ? '' : ' · ' + (ABAS.find(a => a.value === this.aba())?.label.toLowerCase() ?? '');
    return `${n} ${n === 1 ? 'desvio' : 'desvios'}${aba}`;
  });

  data(v: string | null): string {
    return v ? dataCurta(v) : '';
  }

  tomGravidade(g: GravidadeDesvio): string {
    return g === 'CRITICA' || g === 'ALTA' ? 'erro' : g === 'MEDIA' ? 'atencao' : 'destaque';
  }

  tomStatus(s: StatusDesvio): string {
    return s === 'RESOLVIDO' ? 'ok' : s === 'EM_TRATAMENTO' ? 'destaque' : 'atencao';
  }

  abrirFiltros(): void {
    this.isFilterSheetOpen.set(true);
    this.sessao.definirEstadoTela({ folha: 'filtros' });
  }

  fecharFiltros(): void {
    this.isFilterSheetOpen.set(false);
    this.sessao.definirEstadoTela({ folha: null });
  }

  sairSelecao(): void {
    this.modoSelecao.set(false);
    this.selectedIds.set(new Set());
  }

  /** Mudança no formulário de tratamento: guarda já, para voltar igual se o app fechar. */
  definirTratamento(m: { status?: StatusDesvio; acao?: string; prazo?: string }): void {
    if (m.status) this.treatmentStatus.set(m.status);
    if (m.acao !== undefined) this.treatmentAction.set(m.acao);
    if (m.prazo !== undefined) this.treatmentDeadline.set(m.prazo);
    this.guardarTratamento();
  }

  private guardarTratamento(): void {
    this.sessao.definirEstadoTela({
      folha: 'tratar',
      tratar: { id: this.treatmentItemId(), ids: [...this.selectedIds()], status: this.treatmentStatus(), acao: this.treatmentAction(), prazo: this.treatmentDeadline() },
    });
  }

  /** Reabre a folha que estava aberta quando o app fechou. */
  private restaurarFolha(): void {
    const e = this.sessao.estadoTela<{ folha?: string | null; tratar?: { id: string | null; ids: string[]; status: StatusDesvio; acao: string; prazo: string } }>();
    if (e.folha === 'filtros') this.isFilterSheetOpen.set(true);
    if (e.folha === 'tratar' && e.tratar) {
      const t = e.tratar;
      if (t.id && !this.desvios().some(d => d.id === t.id)) return;
      if (!t.id) {
        this.selectedIds.set(new Set(t.ids));
        this.modoSelecao.set(true);
      }
      this.treatmentItemId.set(t.id);
      this.treatmentStatus.set(t.status);
      this.treatmentAction.set(t.acao);
      this.treatmentDeadline.set(t.prazo);
      this.isTreatmentOpen.set(true);
    }
  }

  private async carregarCatalogos(): Promise<void> {
    const [processos, bases, equipes] = await Promise.all([
      this.dbService.getProcessos(),
      this.dbService.getBasesOperacionaisAtivas(),
      this.dbService.getEquipes(),
    ]);
    this.processos.set(processos.filter(item => item.ativo));
    this.bases.set(bases);
    this.equipes.set(equipes);
  }

  private async carregar(): Promise<void> {
    if (!this.connectivity.isOnline()) {
      this.isLoading.set(false);
      return;
    }

    this.isLoading.set(true);
    this.blockedMessage.set('');
    try {
      const response = await firstValueFrom(this.service.listar(this.criarFiltro()));
      this.desvios.set(response.content.map(item => this.normalizarDataResolucao(item)));
      this.removerResolvidosDaSelecao();
      this.page.set(response.page);
      this.pageNumber.set(response.page.number);
    } catch (error) {
      const apiError = error as ApiError;
      if (apiError.status === 404) {
        this.blockedMessage.set(
          apiError.detail || 'Seu usuário precisa estar vinculado a um colaborador ativo para consultar desvios.'
        );
      }
      this.desvios.set([]);
      this.page.set({ size: 20, number: 0, totalElements: 0, totalPages: 0 });
    } finally {
      this.isLoading.set(false);
    }
  }

  private criarFiltro(): MeuDesvioFilter {
    const filter: MeuDesvioFilter = {
      page: this.pageNumber(),
      size: 20,
      sort: this.sort(),
    };

    const status = this.statusDaAba();
    if (this.aba() === 'pendentes') filter.apenasPendentes = true;
    if (status) filter.status = status;
    if (this.gravidade()) filter.gravidade = this.gravidade() as GravidadeDesvio;
    if (this.vencimento() === 'vencidos') filter.apenasVencidos = true;
    if (this.idProcesso()) filter.idProcesso = this.idProcesso();
    if (this.idBase()) filter.idBaseOperacional = this.idBase();
    if (this.idEquipe()) filter.idEquipe = this.idEquipe();
    if (this.dataDe()) filter.dataInspecaoDe = this.dataDe();
    if (this.dataAte()) filter.dataInspecaoAte = this.dataAte();
    if (this.busca()) filter.buscaGlobal = this.busca();
    return filter;
  }

  private statusDaAba(): StatusDesvio | undefined {
    const status: Partial<Record<AbaDesvio, StatusDesvio>> = {
      abertos: 'ABERTO',
      em_tratamento: 'EM_TRATAMENTO',
      resolvidos: 'RESOLVIDO',
    };
    return status[this.aba()];
  }

  private criarTratamentoRequest(): TratarDesvioRequest {
    const request: TratarDesvioRequest = { status: this.treatmentStatus() };
    const action = this.treatmentAction().trim();
    if (action) request.acaoCorretiva = action;
    if (this.treatmentDeadline()) request.prazo = this.treatmentDeadline();
    return request;
  }

  private aplicarAtualizacaoLocal(atualizado: MeuDesvio): void {
    atualizado = this.normalizarDataResolucao(atualizado);
    const selecionados = new Set(this.selectedIds());
    selecionados.delete(atualizado.id);
    this.selectedIds.set(selecionados);

    if (this.itemAtendeFiltros(atualizado)) {
      this.desvios.update(items =>
        items.map(item => item.id === atualizado.id ? atualizado : item)
      );
    } else {
      this.desvios.update(items => items.filter(item => item.id !== atualizado.id));
      this.page.update(page => ({
        ...page,
        totalElements: Math.max(0, page.totalElements - 1),
      }));
    }
  }

  private itemAtendeFiltros(item: MeuDesvio): boolean {
    const status = this.statusDaAba();
    if (this.aba() === 'pendentes' && item.status === 'RESOLVIDO') return false;
    if (status && item.status !== status) return false;
    if (this.gravidade() && item.gravidade !== this.gravidade()) return false;
    if (this.vencimento() === 'vencidos' && !item.vencido) return false;
    if (this.idProcesso() && item.processo?.id !== this.idProcesso()) return false;
    if (this.idBase() && item.baseOperacional?.id !== this.idBase()) return false;
    if (this.idEquipe() && item.equipe?.id !== this.idEquipe()) return false;

    const query = this.busca().toLocaleLowerCase('pt-BR');
    if (query) {
      const content = [
        item.pergunta,
        item.descricao,
        item.acaoCorretiva,
        item.processo?.nome,
        item.baseOperacional?.nome,
        item.equipe?.codigo,
        item.equipe?.nome,
      ].filter(Boolean).join(' ').toLocaleLowerCase('pt-BR');
      if (!content.includes(query)) return false;
    }
    return true;
  }

  private normalizarDataResolucao(item: MeuDesvio): MeuDesvio {
    return item.status === 'RESOLVIDO' ? item : { ...item, dataResolucao: null };
  }

  private removerResolvidosDaSelecao(): void {
    const resolvidos = new Set(
      this.desvios().filter(item => item.status === 'RESOLVIDO').map(item => item.id)
    );
    if (resolvidos.size === 0) return;
    this.selectedIds.update(ids => new Set([...ids].filter(id => !resolvidos.has(id))));
  }

  private mudarPagina(): void {
    this.atualizarUrl();
    void this.carregar();
  }

  private restaurarUrl(): void {
    const query = this.route.snapshot.queryParamMap;
    const aba = query.get('tab') as AbaDesvio | null;
    if (aba && ABAS.some(item => item.value === aba)) this.aba.set(aba);

    const busca = query.get('busca') ?? '';
    this.busca.set(busca);
    this.buscaInput.set(busca);

    const gravidade = query.get('gravidade') as GravidadeFilter | null;
    if (gravidade && ['BAIXA', 'MEDIA', 'ALTA', 'CRITICA'].includes(gravidade)) {
      this.gravidade.set(gravidade);
    }

    this.vencimento.set(query.get('vencidos') === 'true' ? 'vencidos' : 'todos');
    this.idProcesso.set(query.get('processo') ?? '');
    this.idBase.set(query.get('base') ?? '');
    this.idEquipe.set(query.get('equipe') ?? '');
    this.dataDe.set(query.get('de') ?? '');
    this.dataAte.set(query.get('ate') ?? '');
    this.sort.set(query.get('sort') ?? 'prazo,asc');

    const page = Number(query.get('page') ?? 0);
    this.pageNumber.set(Number.isInteger(page) && page >= 0 ? page : 0);
  }

  private atualizarUrl(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      replaceUrl: true,
      queryParams: {
        tab: this.aba() === 'pendentes' ? null : this.aba(),
        busca: this.busca() || null,
        gravidade: this.gravidade() || null,
        vencidos: this.vencimento() === 'vencidos' ? true : null,
        processo: this.idProcesso() || null,
        base: this.idBase() || null,
        equipe: this.idEquipe() || null,
        de: this.dataDe() || null,
        ate: this.dataAte() || null,
        sort: this.sort() === 'prazo,asc' ? null : this.sort(),
        page: this.pageNumber() || null,
      },
      queryParamsHandling: 'merge',
    });
  }
}
