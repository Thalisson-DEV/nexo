import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, combineLatest, debounceTime, map, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { AlmoxService } from '../../../core/almox/almox.service';
import { Estoque, FiltroSolicitacoes, ResumoSolicitacao, StatusSolicitacao } from '../../../core/almox/almox.models';
import { EM_ABERTO, ORDEM_STATUS, STATUS, dataCivil, idCurto, isoCivil, saqueAtrasado } from '../../../core/almox/almox-status';
import { ApiError } from '../../../core/http/error.interceptor';
import { SessaoService } from '../../../core/sessao/sessao.service';
import { ConnectivityService } from '../../../core/sync/connectivity.service';
import { ToastService } from '../../../core/ui/toast.service';
import { PeriodFilter, periodStartTime } from '../../../shared/components/filter-sheet/period-filter.util';
import {
  AbasSecaoComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, OpcaoSegmento, OpcaoSeletor, PERIODOS,
  SegmentadoComponent, SeletorComponent, TituloGrandeComponent, VazioComponent, agruparPorDia, tituloDia,
} from '../../../ui';
import { abasAlmox } from '../almox-abas';
import { CalendarioSolicitacoesComponent } from './calendario-solicitacoes.component';
import { AcaoCartao, CartaoSolicitacaoComponent } from './cartao-solicitacao.component';
import { FolhaRejeicaoComponent } from './folha-rejeicao.component';
import { QuadroSolicitacoesComponent } from './quadro-solicitacoes.component';

type Visao = 'lista' | 'quadro' | 'calendario';
type Ordem = 'recentes' | 'saque';

interface EstadoTela {
  visao?: Visao;
  statuses?: StatusSolicitacao[];
  origem?: string | null;
  destino?: string | null;
  serie?: string;
  periodo?: PeriodFilter;
  ordem?: Ordem;
  filtros?: boolean;
}

const VISOES: OpcaoSegmento<Visao>[] = [
  { valor: 'lista', rotulo: 'Lista', icone: 'lista' },
  { valor: 'quadro', rotulo: 'Quadro', icone: 'quadro' },
  { valor: 'calendario', rotulo: 'Calendário', icone: 'calendario' },
];
const ORDENS: OpcaoSegmento<Ordem>[] = [
  { valor: 'saque', rotulo: 'Por prazo de saque' },
  { valor: 'recentes', rotulo: 'Mais recentes' },
];
const TAMANHO_PAGINA = 30;
const CHAVE_VISAO = 'ces_almox_visao';

const mesmo = (a: readonly string[], b: readonly string[]) => a.length === b.length && a.every(v => b.includes(v));

/**
 * Gestão das solicitações comerciais no celular: o mesmo recorte em lista, quadro e
 * calendário, com atalhos de status (e a contagem de cada um), filtros em folha e as ações
 * de aprovar, rejeitar e entregar nos cartões. Abre em "Em aberto": o que pede ação.
 */
@Component({
  selector: 'app-solicitacoes-almox',
  standalone: true,
  imports: [
    AbasSecaoComponent, CalendarioSolicitacoesComponent, CartaoSolicitacaoComponent, FolhaComponent, FolhaRejeicaoComponent, IconeComponent,
    IndicadorRedeComponent, QuadroSolicitacoesComponent, SegmentadoComponent, SeletorComponent, TituloGrandeComponent, VazioComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-titulo-grande titulo="Almoxarifado" [subtitulo]="subtitulo()">
      <ces-indicador-rede />
      <button type="button" class="ces-btn ces-btn--neutro ces-btn--compacto filtro" (click)="definir({ filtros: true })"
        [attr.aria-label]="'Filtros' + (filtrosAtivos() ? ', ' + filtrosAtivos() + ' ativos' : '')">
        <ces-icone nome="filtro" [tamanho]="18" />@if (filtrosAtivos()) { <span class="ces-num">{{ filtrosAtivos() }}</span> } @else { Filtrar }
      </button>
    </ces-titulo-grande>
    @if (abas().length > 1) { <ces-abas-secao rotulo="Almoxarifado" [abas]="abas()" /> }

    @if (!rede.isOnline()) {
      <ces-vazio icone="sem_rede" titulo="O almoxarifado precisa de internet" texto="Aprovar e entregar dependem do saldo de agora no servidor. Conecte e a lista aparece aqui." />
    } @else {
      <div class="visoes">
        <ces-segmentado rotulo="Forma de ver" [opcoes]="visoes" [valor]="visao()" (valorChange)="definir({ visao: $event })" />
      </div>

      <div class="atalhos" role="group" aria-label="Mostrar só os status escolhidos">
        <button type="button" class="chip" [attr.aria-pressed]="emAbertoAtivo()" (click)="definir({ statuses: emAberto })">
          <ces-icone nome="relogio" [tamanho]="16" />Em aberto<span class="conta ces-num" [class.alerta]="(abertas() ?? 0) > 0">{{ abertas() ?? '–' }}</span>
        </button>
        <button type="button" class="chip" [attr.aria-pressed]="!statuses().length" (click)="definir({ statuses: [] })">
          Todas<span class="conta ces-num">{{ total() ?? '–' }}</span>
        </button>
        <span class="divisor" aria-hidden="true"></span>
        @for (s of status; track s.valor) {
          <button type="button" class="chip" [attr.data-tom]="s.tom" [attr.aria-pressed]="statuses().includes(s.valor)" (click)="alternarStatus(s.valor)"
            [attr.aria-label]="s.atalho + ': ' + (contagens()[s.valor] ?? 'contagem indisponível')">
            <span class="ponto" aria-hidden="true"></span>{{ s.atalho }}<span class="conta ces-num">{{ contagens()[s.valor] ?? '–' }}</span>
          </button>
        }
      </div>

      @if (resumoFiltros()) {
        <p class="recorte"><ces-icone nome="filtro" [tamanho]="16" /><span>{{ resumoFiltros() }}</span>
          <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="limparFiltros()">Limpar</button></p>
      }

      @switch (visao()) {
        @case ('quadro') {
          <app-quadro-solicitacoes #quadro [filtro]="filtro()" [acoes]="podeGerir()" [ocupado]="ocupado()" (abrir)="abrir($event)" (agir)="agir($event)" />
        }
        @case ('calendario') {
          <app-calendario-solicitacoes #calendario [filtro]="filtro()" [acoes]="podeGerir()" [ocupado]="ocupado()" (abrir)="abrir($event)" (agir)="agir($event)" />
        }
        @default {
          @if (erroLista()) {
            <ces-vazio icone="alerta" titulo="Não foi possível carregar as solicitações" [texto]="erroLista()">
              <button type="button" class="ces-btn ces-btn--tonal" (click)="recarregar()">Tentar de novo</button>
            </ces-vazio>
          } @else if (carregandoLista() && !itens().length) {
            <div class="ces-esqueletos" aria-busy="true" aria-label="Carregando">
              @for (i of [1, 2, 3, 4]; track i) { <div class="ces-esqueleto" style="height: 128px"></div> }
            </div>
          } @else if (itens().length) {
            <div class="grupos" [class.atualizando]="carregandoLista()">
              @for (g of grupos(); track g.titulo) {
                <h2 class="ces-secao grupo-titulo" [class.alerta]="g.alerta"><span>{{ g.titulo }}</span><span class="ces-num">{{ g.itens.length }}</span></h2>
                <div class="cartoes">
                  @for (s of g.itens; track s.id) {
                    <app-cartao-solicitacao [attr.data-ancora]="s.id" [item]="s" [acoes]="podeGerir()" [ocupado]="ocupado()?.id === s.id ? ocupado()!.acao : null"
                      (abrir)="abrir($event)" (agir)="agir($event)" />
                  }
                </div>
              }
            </div>
            @if (itens().length < totalLista()) {
              <button type="button" class="ces-btn ces-btn--neutro ces-btn--bloco mais" (click)="carregarMais()" [disabled]="carregandoLista()">
                {{ carregandoLista() ? 'Carregando…' : 'Mostrar mais (' + (totalLista() - itens().length) + ')' }}
              </button>
            } @else {
              <p class="fim ces-num">{{ totalLista() }} {{ totalLista() === 1 ? 'solicitação' : 'solicitações' }} neste recorte</p>
            }
          } @else {
            <ces-vazio icone="documento" [titulo]="emAbertoAtivo() && !filtrosAtivos() ? 'Nada aguardando o almoxarifado' : 'Nenhuma solicitação neste recorte'"
              [texto]="emAbertoAtivo() && !filtrosAtivos() ? 'Não há solicitação para aprovar nem retirada pendente.' : 'Tire algum filtro ou veja todas.'">
              @if (statuses().length || filtrosAtivos()) {
                <button type="button" class="ces-btn ces-btn--tonal" (click)="verTodas()">Ver todas</button>
              }
            </ces-vazio>
          }
        }
      }
      <div class="ces-respiro"></div>
    }

    <app-folha-rejeicao [idSolicitacao]="rejeitando()" (fechar)="rejeitando.set(null)" (rejeitada)="aoRejeitar()" />

    <ces-folha [aberta]="folhaFiltros()" (fechar)="definir({ filtros: false })" titulo="Filtrar solicitações">
      <div class="campos">
        <ces-seletor rotulo="Estoque de origem" placeholder="Todos" [opcoes]="opcoesEstoque()" [valor]="origem()" (valorChange)="definir({ origem: $event })" />
        <ces-seletor rotulo="Estoque de destino" placeholder="Todos" [opcoes]="opcoesEstoque()" [valor]="destino()" (valorChange)="definir({ destino: $event })" />
        <ces-seletor rotulo="Solicitadas em" [opcoes]="opcoesPeriodo" [valor]="periodo()" (valorChange)="definir({ periodo: $any($event) ?? 'todos' })" />
        <label class="ces-campo serie">
          <span class="ces-campo__rotulo">Número de série nos itens</span>
          <input class="ces-input" [value]="serie()" (input)="serieDigitada.set($any($event.target).value)" autocapitalize="characters" autocomplete="off"
            spellcheck="false" enterkeyhint="search" placeholder="Ex.: ABC12345">
        </label>
        @if (visao() === 'lista') {
          <div class="ces-campo"><span class="ces-campo__rotulo">Ordenar a lista</span>
            <ces-segmentado rotulo="Ordenar a lista" [opcoes]="ordens" [valor]="ordem()" (valorChange)="definir({ ordem: $event })" /></div>
        }
      </div>
      <div rodape class="ces-folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="limparFiltros()">Limpar</button>
        <button type="button" class="ces-btn ces-btn--principal" (click)="definir({ filtros: false })">Mostrar</button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: block; min-height: 100%; }
    .filtro { gap: 6px; }
    .visoes { padding: var(--ces-e3) var(--ces-e3) 0; }
    .atalhos { display: flex; gap: var(--ces-e2); overflow-x: auto; padding: var(--ces-e3) var(--ces-e3) var(--ces-e1); scrollbar-width: none; overscroll-behavior-x: contain;
      scroll-snap-type: x proximity; }
    .atalhos::-webkit-scrollbar { display: none; }
    .chip { flex: none; scroll-snap-align: start; display: inline-flex; align-items: center; gap: 6px; min-height: 40px; padding: 0 6px 0 14px; border-radius: var(--ces-r-pilula);
      border: 1px solid var(--ces-linha-forte); background: var(--ces-superficie); color: var(--ces-texto-2); font: 500 .875rem/1 var(--ces-fonte); cursor: pointer; white-space: nowrap; }
    .chip[aria-pressed='true'] { background: var(--ces-destaque-suave); border-color: transparent; color: var(--ces-destaque); font-weight: 600; }
    .chip:active { transform: scale(.97); }
    .conta { min-width: 24px; height: 26px; padding: 0 7px; box-sizing: border-box; border-radius: 13px; display: inline-grid; place-items: center;
      background: var(--ces-superficie-2); color: var(--ces-texto); font: 700 .75rem/1 var(--ces-fonte); }
    .chip[aria-pressed='true'] .conta { background: var(--ces-superficie); }
    .conta.alerta { background: var(--ces-atencao-suave); color: var(--ces-atencao); }
    .ponto { width: 8px; height: 8px; border-radius: 50%; background: var(--ces-texto-3); flex: none; }
    [data-tom='atencao'] .ponto { background: var(--ces-atencao); }
    [data-tom='destaque'] .ponto { background: var(--ces-destaque); }
    [data-tom='ok'] .ponto { background: var(--ces-ok); }
    [data-tom='erro'] .ponto { background: var(--ces-erro); }
    .divisor { flex: none; width: 1px; align-self: stretch; margin: 6px 2px; background: var(--ces-linha-forte); }
    .recorte { display: flex; align-items: center; gap: var(--ces-e2); margin: var(--ces-e2) var(--ces-e3) 0; padding: 0 0 0 var(--ces-e4); min-height: 44px;
      border-radius: var(--ces-r-grupo); background: var(--ces-superficie); font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .recorte span { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ces-texto); }
    .grupos { transition: opacity var(--ces-m-entrar); }
    .grupos.atualizando { opacity: .6; }
    .grupo-titulo { padding-top: var(--ces-e4); }
    .grupo-titulo.alerta { color: var(--ces-erro); }
    .cartoes { display: grid; gap: var(--ces-e2); padding: 0 var(--ces-e3); }
    .mais { margin: var(--ces-e3) var(--ces-e3) 0; width: calc(100% - 24px); }
    .fim { margin: var(--ces-e4) 0 0; text-align: center; font: var(--ces-t-apoio); color: var(--ces-texto-3); }
    .campos { display: grid; gap: var(--ces-e3); padding: 0 var(--ces-e3) var(--ces-e2); }
    .campos ces-seletor { box-shadow: inset 0 0 0 1px var(--ces-linha); border-radius: var(--ces-r-grupo); }
    .campos .ces-campo__rotulo, .campos .ces-campo__ajuda, .campos .ces-campo__erro { padding-left: var(--ces-e4); }
  `],
})
export class SolicitacoesAlmoxComponent {
  private readonly almox = inject(AlmoxService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly sessao = inject(SessaoService);
  private readonly toast = inject(ToastService);
  readonly rede = inject(ConnectivityService);
  private readonly quadro = viewChild<QuadroSolicitacoesComponent>('quadro');
  private readonly calendario = viewChild<CalendarioSolicitacoesComponent>('calendario');

  readonly visoes = VISOES;
  readonly ordens = ORDENS;
  readonly status = STATUS;
  readonly emAberto = [...EM_ABERTO];
  readonly opcoesPeriodo: OpcaoSeletor[] = PERIODOS.map(p => ({ valor: p.valor, rotulo: p.rotulo }));

  private readonly salvo = this.sessao.estadoTela<EstadoTela>();
  readonly visao = signal<Visao>(this.salvo.visao ?? lerVisao());
  readonly statuses = signal<StatusSolicitacao[]>(this.salvo.statuses ?? [...EM_ABERTO]);
  readonly origem = signal<string | null>(this.salvo.origem ?? null);
  readonly destino = signal<string | null>(this.salvo.destino ?? null);
  readonly serie = signal(this.salvo.serie ?? '');
  readonly serieDigitada = signal(this.serie());
  readonly periodo = signal<PeriodFilter>(this.salvo.periodo ?? 'todos');
  readonly ordem = signal<Ordem>(this.salvo.ordem ?? 'saque');
  readonly folhaFiltros = signal(!!this.salvo.filtros);

  readonly estoques = signal<Estoque[]>([]);
  readonly contagens = signal<Partial<Record<StatusSolicitacao, number>>>({});
  readonly itens = signal<ResumoSolicitacao[]>([]);
  readonly totalLista = signal(0);
  readonly carregandoLista = signal(false);
  readonly erroLista = signal<string | null>(null);
  readonly ocupado = signal<{ id: string; acao: AcaoCartao } | null>(null);
  readonly rejeitando = signal<string | null>(null);
  private readonly pagina = signal(0);
  private readonly versao = signal(0);

  readonly podeGerir = computed(() => this.auth.podeGerirSolicitacoesAlmox() && this.rede.isOnline());
  readonly abertas = computed(() => somar(this.contagens(), EM_ABERTO));
  readonly total = computed(() => somar(this.contagens(), ORDEM_STATUS));
  readonly abas = abasAlmox(() => this.abertas());
  readonly paraEntregar = computed(() => somar(this.contagens(), ['APROVADO', 'ENTREGUE_PARCIAL']));
  readonly atrasadas = signal(0);
  readonly subtitulo = computed(() => {
    const n = this.atrasadas();
    return n ? `${n} com saque atrasado` : 'Solicitações comerciais';
  });

  /** Lista em blocos: por dia do pedido (mais recentes) ou por prazo de saque. */
  readonly grupos = computed(() => {
    const lista = this.itens();
    if (this.ordem() === 'recentes') return agruparPorDia(lista, s => s.dataSolicitacao).map(g => ({ ...g, titulo: `Pedidas ${g.titulo.toLowerCase()}`, alerta: false }));
    const hoje = isoCivil(new Date());
    const blocos = new Map<string, { titulo: string; itens: ResumoSolicitacao[]; alerta: boolean }>();
    for (const s of lista) {
      const d = s.dataPrevista?.substring(0, 10);
      const chave = !d ? 'sem' : saqueAtrasado(s) ? 'atrasadas' : d;
      const titulo = chave === 'sem' ? 'Sem data de saque' : chave === 'atrasadas' ? 'Saque atrasado' : d === hoje ? 'Saque hoje' : `Saque ${tituloDia(dataCivil(d!)).toLowerCase()}`;
      if (!blocos.has(chave)) blocos.set(chave, { titulo, itens: [], alerta: chave === 'atrasadas' });
      blocos.get(chave)!.itens.push(s);
    }
    return [...blocos.values()];
  });
  readonly emAbertoAtivo = computed(() => mesmo(this.statuses(), EM_ABERTO));
  readonly opcoesEstoque = computed<OpcaoSeletor[]>(() => this.estoques().map(e => ({ valor: e.id, rotulo: e.nomeIdentificador, apoio: e.tipoEstoque?.nome })));

  readonly filtrosAtivos = computed(() => [this.origem(), this.destino(), this.serie().trim(), this.periodo() !== 'todos' ? 1 : null].filter(Boolean).length);
  readonly resumoFiltros = computed(() => {
    const nome = (id: string | null) => this.estoques().find(e => e.id === id)?.nomeIdentificador ?? 'selecionado';
    const partes = [
      this.origem() ? `De ${nome(this.origem())}` : '',
      this.destino() ? `Para ${nome(this.destino())}` : '',
      this.periodo() !== 'todos' ? PERIODOS.find(p => p.valor === this.periodo())?.rotulo ?? '' : '',
      this.serie().trim() ? `Série ${this.serie().trim().toUpperCase()}` : '',
    ].filter(Boolean);
    return partes.join(' · ');
  });


  /** Recorte no formato do backend, comum às três visões e às contagens. */
  readonly filtro = computed<FiltroSolicitacoes>(() => {
    const inicio = periodStartTime(this.periodo());
    return {
      statuses: this.statuses().length ? [...this.statuses()] : undefined,
      idEstoqueOrigem: this.origem(),
      idEstoqueDestino: this.destino(),
      numSerie: this.serie().trim().toUpperCase() || null,
      dataInicio: inicio !== null ? new Date(inicio).toISOString() : null,
    };
  }, { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) });

  constructor() {
    this.almox.estoques().subscribe(e => this.estoques.set(e));
    this.almox.carregarNomes();

    // Série digitada: aplica depois de uma pausa, sem consultar a cada letra.
    toObservable(this.serieDigitada).pipe(debounceTime(450), takeUntilDestroyed()).subscribe(v => {
      if (v.trim() !== this.serie().trim()) this.definir({ serie: v });
    });

    // Contagens por status (ignoram os atalhos, para o número não sumir ao filtrar).
    combineLatest([toObservable(this.filtro), toObservable(this.versao)]).pipe(
      switchMap(([f]) => (this.rede.isOnline() ? this.almox.contarPorStatus(f, ORDEM_STATUS) : of({}))),
      takeUntilDestroyed(),
    ).subscribe(c => this.contagens.set(c));
    combineLatest([toObservable(this.filtro), toObservable(this.versao)]).pipe(
      switchMap(([f]) => {
        if (!this.rede.isOnline()) return of(0);
        const ontem = new Date();
        ontem.setDate(ontem.getDate() - 1);
        return this.almox.listarSolicitacoes({ ...f, statuses: [...EM_ABERTO], previstaAte: isoCivil(ontem), page: 0, size: 1 })
          .pipe(map(r => r.page?.totalElements ?? 0), catchError(() => of(0)));
      }),
      takeUntilDestroyed(),
    ).subscribe(n => this.atrasadas.set(n));

    // Lista (só quando é a visão ativa).
    combineLatest([toObservable(this.filtro), toObservable(this.ordem), toObservable(this.visao), toObservable(this.versao), toObservable(this.pagina)]).pipe(
      tap(() => this.erroLista.set(null)),
      switchMap(([f, ordem, visao, , pagina]) => {
        if (visao !== 'lista' || !this.rede.isOnline()) return of(null);
        this.carregandoLista.set(true);
        return this.almox.listarSolicitacoes({
          ...f, page: pagina, size: TAMANHO_PAGINA, sort: ordem === 'saque' ? 'dataPrevistaRetirada,asc' : 'dataSolicitacao,desc',
        }).pipe(
          tap(r => {
            this.itens.set(pagina === 0 ? r.content ?? [] : [...this.itens(), ...(r.content ?? [])]);
            this.totalLista.set(r.page?.totalElements ?? r.content?.length ?? 0);
          }),
          catchError((e: ApiError) => { this.erroLista.set(e?.detail || 'Verifique a conexão e tente de novo.'); return of(null); }),
        );
      }),
      takeUntilDestroyed(),
    ).subscribe(() => this.carregandoLista.set(false));

    // Voltou a conexão: atualiza o que está na tela.
    let estavaOnline = this.rede.isOnline();
    effect(() => {
      const online = this.rede.isOnline();
      if (online && !estavaOnline) untracked(() => this.recarregar());
      estavaOnline = online;
    });
  }

  mesmoStatus(lista: StatusSolicitacao[]): boolean {
    return mesmo(this.statuses(), lista);
  }

  definir(m: Partial<EstadoTela>): void {
    const mudaRecorte = m.statuses !== undefined || m.origem !== undefined || m.destino !== undefined || m.serie !== undefined || m.periodo !== undefined || m.ordem !== undefined;
    if (m.visao !== undefined) {
      this.visao.set(m.visao);
      try { localStorage.setItem(CHAVE_VISAO, m.visao); } catch { /* preferência opcional */ }
    }
    if (m.statuses !== undefined) this.statuses.set(ORDEM_STATUS.filter(s => m.statuses!.includes(s)));
    if (m.origem !== undefined) this.origem.set(m.origem);
    if (m.destino !== undefined) this.destino.set(m.destino);
    if (m.serie !== undefined) { this.serie.set(m.serie); this.serieDigitada.set(m.serie); }
    if (m.periodo !== undefined) this.periodo.set(m.periodo);
    if (m.ordem !== undefined) this.ordem.set(m.ordem);
    if (m.filtros !== undefined) this.folhaFiltros.set(m.filtros);
    if (mudaRecorte) this.pagina.set(0);
    // Número de série é dado sensível: não vai para o estado de tela persistido.
    const { serie: _s, ...persistivel } = m;
    this.sessao.definirEstadoTela({ ...persistivel });
  }

  alternarStatus(s: StatusSolicitacao): void {
    const atuais = this.statuses();
    this.definir({ statuses: atuais.includes(s) ? atuais.filter(x => x !== s) : [...atuais, s] });
  }

  limparFiltros(): void {
    this.definir({ origem: null, destino: null, serie: '', periodo: 'todos' });
  }

  verTodas(): void {
    this.definir({ statuses: [], origem: null, destino: null, serie: '', periodo: 'todos' });
  }

  carregarMais(): void {
    this.pagina.update(p => p + 1);
  }

  recarregar(): void {
    this.pagina.set(0);
    this.versao.update(v => v + 1);
    this.quadro()?.recarregar();
    this.calendario()?.recarregar();
  }

  abrir(s: ResumoSolicitacao): void {
    void this.router.navigate(['/almox/solicitacoes', s.id]);
  }

  agir(e: { acao: AcaoCartao; item: ResumoSolicitacao }): void {
    if (e.acao === 'rejeitar') this.rejeitando.set(e.item.id);
    else if (e.acao === 'entregar') void this.router.navigate(['/almox/solicitacoes', e.item.id, 'entrega']);
    else this.aprovar(e.item);
  }

  aoRejeitar(): void {
    this.rejeitando.set(null);
    this.recarregar();
  }

  private aprovar(s: ResumoSolicitacao): void {
    if (this.ocupado()) return;
    this.ocupado.set({ id: s.id, acao: 'aprovar' });
    this.almox.aprovar(s.id).subscribe({
      next: () => {
        this.ocupado.set(null);
        this.toast.success(`Solicitação #${idCurto(s.id)} aprovada. Agora aguarda a retirada.`);
        this.recarregar();
      },
      error: (e: ApiError) => {
        this.ocupado.set(null);
        if (e.status === 409) {
          this.toast.warning('Outra pessoa alterou esta solicitação. A lista foi atualizada.');
          this.recarregar();
        } else {
          this.toast.error(e.detail || 'Não foi possível aprovar.');
        }
      },
    });
  }
}

function somar(c: Partial<Record<StatusSolicitacao, number>>, status: readonly StatusSolicitacao[]): number | null {
  if (!status.every(s => c[s] !== undefined)) return null;
  return status.reduce((a, s) => a + (c[s] ?? 0), 0);
}

function lerVisao(): Visao {
  try {
    const v = localStorage.getItem(CHAVE_VISAO);
    return v === 'quadro' || v === 'calendario' ? v : 'lista';
  } catch {
    return 'lista';
  }
}
