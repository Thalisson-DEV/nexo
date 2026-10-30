import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { AlmoxService } from '../../../core/almox/almox.service';
import { Estoque, ResumoMovimentacao, TipoMovimentacao } from '../../../core/almox/almox.models';
import { defTipoMov } from '../../../core/almox/almox-status';
import { ApiError } from '../../../core/http/error.interceptor';
import { RascunhoStore, ResumoRascunho } from '../../../core/sessao/rascunho.store';
import { SessaoService } from '../../../core/sessao/sessao.service';
import { TokenService } from '../../../core/auth/token.service';
import { ConnectivityService } from '../../../core/sync/connectivity.service';
import { PeriodFilter, periodStartTime } from '../../../shared/components/filter-sheet/period-filter.util';
import {
  AbasSecaoComponent, ChipsComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, NomeIcone, OpcaoChip, OpcaoSeletor, PERIODOS,
  SeletorComponent, TituloGrandeComponent, VazioComponent, agruparPorDia, hora,
} from '../../../ui';
import { abasAlmox } from '../almox-abas';

type Filtro = 'todas' | 'ENTRADA_AVULSA' | 'BAIXA_AVULSA' | 'TRANSFERENCIA' | 'ENTREGA' | 'ESTORNO';
interface EstadoTela { tipo?: Filtro; estoque?: string | null; periodo?: PeriodFilter; filtros?: boolean; novo?: boolean }

const FILTROS: OpcaoChip<Filtro>[] = [
  { valor: 'todas', rotulo: 'Todas' },
  { valor: 'ENTRADA_AVULSA', rotulo: 'Entradas' },
  { valor: 'BAIXA_AVULSA', rotulo: 'Saídas' },
  { valor: 'TRANSFERENCIA', rotulo: 'Transferências' },
  { valor: 'ENTREGA', rotulo: 'Entregas' },
  { valor: 'ESTORNO', rotulo: 'Estornos' },
];
const NOVAS: { tipo: 'entrada' | 'saida' | 'transferencia'; titulo: string; texto: string; icone: NomeIcone; tom: string }[] = [
  { tipo: 'entrada', titulo: 'Entrada', texto: 'Material que chega a um estoque (compra, devolução, ajuste).', icone: 'entrada', tom: 'ok' },
  { tipo: 'saida', titulo: 'Saída', texto: 'Baixa definitiva: perda, avaria, sucateamento, ajuste.', icone: 'saida', tom: 'erro' },
  { tipo: 'transferencia', titulo: 'Transferir', texto: 'De um estoque para outro, com as duas assinaturas.', icone: 'transferencia', tom: 'destaque' },
];
const TAMANHO = 30;

/** Movimentações de estoque por dia, com filtro por tipo e estoque, e os atalhos para movimentar. */
@Component({
  selector: 'app-movimentacoes-almox',
  standalone: true,
  imports: [AbasSecaoComponent, ChipsComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, RouterLink, SeletorComponent, TituloGrandeComponent, VazioComponent],
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
      <ces-vazio icone="sem_rede" titulo="Movimentações precisam de internet" texto="O saldo de cada estoque está no servidor. Conecte e a lista aparece aqui." />
    } @else {
      @if (podeMovimentar()) {
        <div class="atalhos" role="group" aria-label="Movimentar estoque">
          @for (n of novas; track n.tipo) {
            <a class="atalho" [routerLink]="['/almox/movimentacoes/nova', n.tipo]" [attr.data-tom]="n.tom">
              <ces-icone [nome]="n.icone" [tamanho]="18" />{{ n.titulo }}
            </a>
          }
        </div>
        @for (r of rascunhos(); track r.chave) {
          <button type="button" class="retomar" (click)="continuar(r)">
            <span class="ces-selo ces-selo--destaque"><ces-icone nome="retomar" /></span>
            <span class="txt"><strong>{{ r.titulo || 'Movimentação pela metade' }}</strong><span>Continue de onde parou</span></span>
            <span class="ces-btn ces-btn--tonal ces-btn--compacto" aria-hidden="true">Continuar</span>
          </button>
        }
      }

      <ces-chips rotulo="Tipo de movimentação" [opcoes]="filtrosTipo" [valor]="tipo()" (valorChange)="definir({ tipo: $any($event) })" />

      @if (erro()) {
        <ces-vazio icone="alerta" titulo="Não foi possível carregar as movimentações" [texto]="erro()">
          <button type="button" class="ces-btn ces-btn--tonal" (click)="recarregar()">Tentar de novo</button>
        </ces-vazio>
      } @else if (carregando() && !itens().length) {
        <div class="ces-esqueletos" aria-busy="true" aria-label="Carregando">@for (i of [1, 2, 3, 4]; track i) { <div class="ces-esqueleto" style="height: 72px"></div> }</div>
      } @else if (grupos().length) {
        <div [class.atualizando]="carregando()" class="lista">
          @for (g of grupos(); track g.titulo) {
            <h2 class="ces-secao">{{ g.titulo }}</h2>
            <ul class="ces-grupo">
              @for (m of g.itens; track m.idReferenciaOperacao) {
                <li class="ces-linha-lista" [attr.data-ancora]="m.idReferenciaOperacao">
                  <a class="ces-linha ces-linha--acao" [routerLink]="['/almox/movimentacoes', m.idReferenciaOperacao]">
                    <span class="ces-selo" [class]="'ces-selo ces-selo--' + tipoDe(m).tom"><ces-icone [nome]="tipoDe(m).icone" /></span>
                    <span class="ces-linha__texto">
                      <span class="ces-linha__titulo">{{ tipoDe(m).rotulo }}</span>
                      <span class="ces-linha__resumo">{{ rota(m) }}</span>
                      <span class="ces-linha__apoio ces-num">{{ hora(m.dataHora) }} · {{ m.operadorNome || 'Sistema' }}</span>
                    </span>
                    <ces-icone nome="avancar" [tamanho]="18" />
                  </a>
                </li>
              }
            </ul>
          }
          @if (itens().length < total()) {
            <button type="button" class="ces-btn ces-btn--neutro ces-btn--bloco mais" (click)="pagina.set(pagina() + 1)" [disabled]="carregando()">
              {{ carregando() ? 'Carregando…' : 'Mostrar mais (' + (total() - itens().length) + ')' }}</button>
          }
        </div>
      } @else {
        <ces-vazio icone="transferencia" titulo="Nenhuma movimentação neste recorte" [texto]="filtrosAtivos() || tipo() !== 'todas' ? 'Tire algum filtro para ver mais.' : 'As entradas, saídas e transferências aparecem aqui.'">
          @if (filtrosAtivos() || tipo() !== 'todas') { <button type="button" class="ces-btn ces-btn--tonal" (click)="limpar()">Ver todas</button> }
        </ces-vazio>
      }
      <div class="ces-respiro"></div>
    }

    <ces-folha [aberta]="folhaFiltros()" (fechar)="definir({ filtros: false })" titulo="Filtrar movimentações">
      <div class="campos">
        <ces-seletor rotulo="Estoque (origem ou destino)" placeholder="Todos" [opcoes]="opcoesEstoque()" [valor]="estoque()" (valorChange)="definir({ estoque: $event })" />
        <ces-seletor rotulo="Período" [opcoes]="opcoesPeriodo" [valor]="periodo()" (valorChange)="definir({ periodo: $any($event) ?? '30dias' })" />
        <label class="ces-campo serie"><span class="ces-campo__rotulo">Número de série</span>
          <input class="ces-input" [value]="serieDigitada()" (input)="serieDigitada.set($any($event.target).value)" autocapitalize="characters" autocomplete="off"
            spellcheck="false" enterkeyhint="search" placeholder="Ex.: ABC12345"></label>
      </div>
      <div rodape class="ces-folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="limpar()">Limpar</button>
        <button type="button" class="ces-btn ces-btn--principal" (click)="definir({ filtros: false })">Mostrar</button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: block; min-height: 100%; }
    .filtro { gap: 6px; }
    .atalhos { display: flex; gap: var(--ces-e2); padding: var(--ces-e3) var(--ces-e3) 0; }
    .atalho { flex: 1; display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 44px; padding: 0 var(--ces-e2);
      border-radius: var(--ces-r-pilula); background: var(--ces-superficie); color: var(--ces-texto); text-decoration: none; font: 600 .8125rem/1 var(--ces-fonte);
      box-shadow: inset 0 0 0 1px var(--ces-linha); transition: transform 100ms; }
    .atalho:active { transform: scale(.97); background: var(--ces-superficie-2); }
    .atalho[data-tom='ok'] ces-icone { color: var(--ces-ok); }
    .atalho[data-tom='erro'] ces-icone { color: var(--ces-erro); }
    .atalho[data-tom='destaque'] ces-icone { color: var(--ces-destaque); }
    .retomar { display: flex; align-items: center; gap: var(--ces-e3); width: calc(100% - 24px); margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e3) var(--ces-e4);
      border: 0; border-radius: var(--ces-r-grupo); background: var(--ces-destaque-suave); color: var(--ces-texto); text-align: left; font: inherit; cursor: pointer; }
    .retomar .txt { flex: 1; min-width: 0; display: grid; }
    .retomar .txt span { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .lista { transition: opacity var(--ces-m-entrar); }
    .lista.atualizando { opacity: .6; }
    .mais { margin: var(--ces-e3) var(--ces-e3) 0; width: calc(100% - 24px); }
    .campos { display: grid; gap: var(--ces-e3); padding: 0 var(--ces-e3) var(--ces-e2); }
    .campos ces-seletor { box-shadow: inset 0 0 0 1px var(--ces-linha); border-radius: var(--ces-r-grupo); }
    .campos .ces-campo__rotulo, .campos .ces-campo__ajuda, .campos .ces-campo__erro { padding-left: var(--ces-e4); }
  `],
})
export class MovimentacoesAlmoxComponent {
  private readonly almox = inject(AlmoxService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly sessao = inject(SessaoService);
  private readonly rascunhoStore = inject(RascunhoStore);
  private readonly tokens = inject(TokenService);
  readonly rede = inject(ConnectivityService);

  readonly filtrosTipo = FILTROS;
  readonly novas = NOVAS;
  readonly hora = hora;
  readonly opcoesPeriodo: OpcaoSeletor[] = PERIODOS.map(p => ({ valor: p.valor, rotulo: p.rotulo }));
  private readonly salvo = this.sessao.estadoTela<EstadoTela>();
  readonly tipo = signal<Filtro>(this.salvo.tipo ?? 'todas');
  readonly estoque = signal<string | null>(this.salvo.estoque ?? null);
  readonly periodo = signal<PeriodFilter>(this.salvo.periodo ?? '30dias');
  readonly serie = signal('');
  readonly serieDigitada = signal('');
  readonly folhaFiltros = signal(!!this.salvo.filtros);
  readonly estoques = signal<Estoque[]>([]);
  readonly itens = signal<ResumoMovimentacao[]>([]);
  readonly total = signal(0);
  readonly carregando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly pagina = signal(0);
  readonly rascunhos = signal<ResumoRascunho[]>([]);
  private readonly versao = signal(0);

  readonly abas = abasAlmox();
  readonly podeMovimentar = this.auth.podeMovimentarEstoque;
  readonly opcoesEstoque = computed<OpcaoSeletor[]>(() => this.estoques().map(e => ({ valor: e.id, rotulo: e.nomeIdentificador, apoio: e.tipoEstoque?.nome })));
  readonly filtrosAtivos = computed(() => [this.estoque(), this.serie().trim(), this.periodo() !== '30dias' ? 1 : null].filter(Boolean).length);
  readonly grupos = computed(() => agruparPorDia(this.itens(), m => m.dataHora));
  readonly subtitulo = computed(() => {
    const p = PERIODOS.find(x => x.valor === this.periodo())?.rotulo.toLowerCase() ?? '';
    return `${this.total()} · ${p}`;
  });

  constructor() {
    this.almox.estoques().subscribe(e => this.estoques.set(e));
    void this.carregarRascunhos();
    toObservable(this.serieDigitada).pipe(debounceTime(450), takeUntilDestroyed()).subscribe(v => {
      if (v.trim() !== this.serie()) { this.serie.set(v.trim()); this.pagina.set(0); }
    });

    combineLatest([toObservable(this.tipo), toObservable(this.estoque), toObservable(this.periodo), toObservable(this.serie), toObservable(this.pagina), toObservable(this.versao)]).pipe(
      tap(() => this.erro.set(null)),
      switchMap(([tipo, estoque, periodo, serie, pagina]) => {
        if (!this.rede.isOnline()) return of(null);
        this.carregando.set(true);
        const inicio = periodStartTime(periodo);
        return this.almox.listarMovimentacoes({
          tipo: tipo === 'todas' ? null : (tipo as TipoMovimentacao), idEstoque: estoque, numSerie: serie.toUpperCase() || null,
          dataInicio: inicio !== null ? new Date(inicio).toISOString() : null, page: pagina, size: TAMANHO, sort: 'dataHora,desc',
        }).pipe(
          tap(r => { this.itens.set(pagina === 0 ? r.content ?? [] : [...this.itens(), ...(r.content ?? [])]); this.total.set(r.page?.totalElements ?? 0); }),
          catchError((e: ApiError) => { this.erro.set(e?.detail || 'Verifique a conexão e tente de novo.'); return of(null); }),
        );
      }),
      takeUntilDestroyed(),
    ).subscribe(() => this.carregando.set(false));

    let estavaOnline = this.rede.isOnline();
    effect(() => {
      const online = this.rede.isOnline();
      if (online && !estavaOnline) untracked(() => this.recarregar());
      estavaOnline = online;
    });
  }

  definir(m: Partial<EstadoTela>): void {
    if (m.tipo !== undefined) this.tipo.set(m.tipo);
    if (m.estoque !== undefined) this.estoque.set(m.estoque);
    if (m.periodo !== undefined) this.periodo.set(m.periodo);
    if (m.filtros !== undefined) this.folhaFiltros.set(m.filtros);
    if (m.tipo !== undefined || m.estoque !== undefined || m.periodo !== undefined) this.pagina.set(0);
    this.sessao.definirEstadoTela({ ...m });
  }

  limpar(): void {
    this.serieDigitada.set('');
    this.serie.set('');
    this.definir({ tipo: 'todas', estoque: null, periodo: '30dias' });
  }

  recarregar(): void {
    this.pagina.set(0);
    this.versao.update(v => v + 1);
  }

  tipoDe(m: ResumoMovimentacao) {
    return defTipoMov(m.tipo);
  }

  rota(m: ResumoMovimentacao): string {
    const o = m.estoqueOrigem?.nomeIdentificador;
    const d = m.estoqueDestino?.nomeIdentificador;
    if (o && d) return `${o} → ${d}`;
    if (d) return `Para ${d}`;
    if (o) return `De ${o}`;
    return 'Estoque não informado';
  }

  continuar(r: ResumoRascunho): void {
    if (r.rota) void this.router.navigateByUrl(r.rota);
  }

  private async carregarRascunhos(): Promise<void> {
    const dono = this.tokens.ownerId();
    if (!dono) return;
    const lista = await this.rascunhoStore.listar(dono);
    this.rascunhos.set(lista.filter(r => r.chave.startsWith('almox:mov:') && !r.chave.includes('#conflito-') && !!r.rota));
  }
}
