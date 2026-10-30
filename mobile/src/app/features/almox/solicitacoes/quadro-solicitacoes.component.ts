import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { toObservable, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, catchError, combineLatest, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { AlmoxService } from '../../../core/almox/almox.service';
import { FiltroSolicitacoes, ResumoSolicitacao, StatusSolicitacao } from '../../../core/almox/almox.models';
import { EM_ABERTO, Tom, defStatus, isoCivil, saqueAtrasado } from '../../../core/almox/almox-status';
import { FolhaComponent, IconeComponent, NomeIcone, VazioComponent } from '../../../ui';
import { AcaoCartao, CartaoSolicitacaoComponent } from './cartao-solicitacao.component';

type Agrupamento = 'situacao' | 'pendencia' | 'prazo';
type IdColuna = 'APROVACAO' | 'ENTREGA' | 'ENCERRADAS';

interface DefColuna { id: IdColuna; titulo: string; subtitulo: string; icone: NomeIcone; tom: Tom; statuses: StatusSolicitacao[]; vazio: string }
interface Faixa { status: StatusSolicitacao; itens: ResumoSolicitacao[]; total: number }
interface Grupo { id: string; rotulo: string; tom: Tom; itens: ResumoSolicitacao[]; total: number }
interface Coluna { def: DefColuna; grupos: Grupo[]; total: number; atrasadas: number; pendencias: number; varios: boolean }

/** Colunas pela próxima ação do almoxarifado (igual ao web): aprovar, entregar ou nada. */
const COLUNAS: readonly DefColuna[] = [
  { id: 'APROVACAO', titulo: 'Aprovação', subtitulo: 'Aguardam a sua decisão', icone: 'relogio', tom: 'atencao', statuses: ['PENDENTE'], vazio: 'Nada para aprovar neste recorte.' },
  { id: 'ENTREGA', titulo: 'Entrega', subtitulo: 'Aprovadas, esperando a retirada', icone: 'caminhao', tom: 'destaque', statuses: ['APROVADO', 'ENTREGUE_PARCIAL'], vazio: 'Nenhuma retirada pendente.' },
  { id: 'ENCERRADAS', titulo: 'Encerradas', subtitulo: 'Entregues, rejeitadas ou estornadas', icone: 'check', tom: 'neutro', statuses: ['ENTREGUE', 'REJEITADO', 'ESTORNADO'], vazio: 'Nada encerrado ainda.' },
];
const LIMITE_ABERTAS = 200;
const LIMITE_ENCERRADAS = 15;
const CHAVE_AGRUPAMENTO = 'ces_almox_quadro_agrupamento';
const AGRUPAMENTOS: { valor: Agrupamento; rotulo: string; apoio: string }[] = [
  { valor: 'situacao', rotulo: 'Situação', apoio: 'Aguardando retirada, entregue em parte…' },
  { valor: 'pendencia', rotulo: 'Pendência', apoio: 'Pelo motivo da pendência aberta' },
  { valor: 'prazo', rotulo: 'Prazo de saque', apoio: 'Atrasadas, hoje, próximos 7 dias…' },
];

/**
 * Quadro no celular: uma coluna por vez, com rolagem lateral que encaixa e atalhos no topo
 * para pular de coluna. Arrastar não existe no toque — os botões do cartão fazem as mesmas
 * ações do arrastar do web (aprovar, rejeitar, entregar).
 */
@Component({
  selector: 'app-quadro-solicitacoes',
  standalone: true,
  imports: [CartaoSolicitacaoComponent, FolhaComponent, IconeComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="barra">
      <nav class="atalhos" aria-label="Ir para a coluna">
      @for (c of colunas(); track c.def.id; let i = $index) {
        <button type="button" class="atalho" [attr.data-tom]="c.def.tom" [attr.aria-current]="visivel() === i ? 'true' : null" (click)="irPara(i)">
          {{ c.def.titulo }}<strong class="ces-num">{{ faixas() ? c.total : '–' }}</strong>
        </button>
      }
      </nav>
      <button type="button" class="ces-btn-icone agrupar" (click)="folhaAgrupar.set(true)" [attr.aria-label]="'Agrupar por ' + rotuloAgrupamento()"><ces-icone nome="filtro" [tamanho]="20" /></button>
    </div>
    <p class="agrupado">Agrupado por {{ rotuloAgrupamento().toLowerCase() }}</p>

    @if (erro()) {
      <ces-vazio icone="alerta" titulo="Não foi possível carregar o quadro" [texto]="erro()">
        <button type="button" class="ces-btn ces-btn--tonal" (click)="recarregar()">Tentar de novo</button>
      </ces-vazio>
    } @else if (!faixas()) {
      <div class="trilho" aria-busy="true" aria-label="Carregando o quadro">
        @for (c of [1, 2]; track c) {
          <div class="coluna"><div class="ces-esqueleto" style="height: 76px; border-radius: 16px"></div><div class="ces-esqueleto" style="height: 104px"></div><div class="ces-esqueleto" style="height: 104px"></div></div>
        }
      </div>
    } @else {
      <div #trilho class="trilho" [class.atualizando]="carregando()" (scroll)="aoRolar()" role="region" aria-label="Quadro de solicitações">
        @for (c of colunas(); track c.def.id) {
          <section class="coluna" [attr.data-tom]="c.def.tom" [attr.aria-labelledby]="'qd-' + c.def.id">
            <header class="cab">
              <span class="cab__icone"><ces-icone [nome]="c.def.icone" [tamanho]="20" /></span>
              <div class="cab__txt">
                <h3 [id]="'qd-' + c.def.id">{{ c.def.titulo }}</h3>
                <p>{{ c.def.subtitulo }}</p>
              </div>
              <span class="cab__total ces-num">{{ c.total }}</span>
            </header>
            @if (c.atrasadas || c.pendencias) {
              <div class="alertas">
                @if (c.atrasadas) { <span class="alerta alerta--erro"><ces-icone nome="alerta" [tamanho]="14" />{{ c.atrasadas }} {{ c.atrasadas === 1 ? 'atrasada' : 'atrasadas' }}</span> }
                @if (c.pendencias) { <span class="alerta alerta--atencao"><ces-icone nome="bandeira" [tamanho]="14" />{{ c.pendencias }} com pendência</span> }
              </div>
            }
            @if (c.total === 0) {
              <div class="vazio">
                <span class="vazio__icone"><ces-icone nome="check" [tamanho]="22" /></span>
                <strong>Tudo em dia</strong>
                <span>{{ c.def.vazio }}</span>
              </div>
            } @else {
              @let varios = c.varios;
              @for (g of c.grupos; track g.id) {
                @if (g.total > 0) {
                  @let fechado = recolhido(g.id);
                  <div class="grupo">
                    @if (varios) {
                    <button type="button" class="grupo__cab" [attr.aria-expanded]="!fechado" (click)="alternar(g.id)" [attr.data-tom]="g.tom">
                      <span class="grupo__ponto" aria-hidden="true"></span>
                      <span class="grupo__rotulo">{{ g.rotulo }}</span><span class="ces-num conta">{{ g.total }}</span>
                      <ces-icone nome="avancar" [tamanho]="16" class="seta" [class.aberta]="!fechado" />
                    </button>
                    }
                    @if (!fechado || !varios) {
                      <div class="cartoes">
                        @for (s of g.itens; track s.id) {
                          <app-cartao-solicitacao [item]="s" [compacto]="true" [acoes]="acoes()" [ocupado]="ocupado()?.id === s.id ? ocupado()!.acao : null"
                            (abrir)="abrir.emit($event)" (agir)="agir.emit($event)" />
                        }
                        @if (g.total > g.itens.length) {
                          <p class="mais">Mostrando {{ g.itens.length }} de {{ g.total }}. Veja todas na lista.</p>
                        }
                      </div>
                    }
                  </div>
                }
              }
            }
          </section>
        }
      </div>
    }

    <ces-folha [aberta]="folhaAgrupar()" (fechar)="folhaAgrupar.set(false)" titulo="Agrupar o quadro" descricao="Como os cartões se organizam dentro de cada coluna.">
      <ul class="ces-grupo opcoes" role="radiogroup" aria-label="Agrupar por">
        @for (a of agrupamentos; track a.valor) {
          <li><button type="button" role="radio" class="ces-linha ces-linha--acao" [attr.aria-checked]="agrupamento() === a.valor" (click)="agrupar(a.valor); folhaAgrupar.set(false)">
            <span class="ces-linha__texto"><span class="ces-linha__titulo">{{ a.rotulo }}</span><span class="ces-linha__apoio">{{ a.apoio }}</span></span>
            @if (agrupamento() === a.valor) { <ces-icone nome="check" /> }
          </button></li>
        }
      </ul>
    </ces-folha>
  `,
  styles: [`
    :host { display: block; }
    .barra { display: flex; align-items: center; gap: var(--ces-e1); padding: var(--ces-e3) var(--ces-e2) 0 var(--ces-e3); }
    .agrupar { flex: none; color: var(--ces-texto-2); }
    .agrupado { margin: 6px var(--ces-e5) 0; font: var(--ces-t-apoio); font-size: .75rem; color: var(--ces-texto-3); }
    .opcoes { background: var(--ces-fundo); }
    .atalhos { flex: 1; min-width: 0; display: flex; gap: 2px; padding: 3px; border-radius: var(--ces-r-pilula); background: var(--ces-superficie-2); }
    .atalho { flex: 1; display: inline-flex; align-items: center; justify-content: center; gap: 5px; min-height: 36px; padding: 0 6px; border: 0;
      border-radius: var(--ces-r-pilula); background: transparent; color: var(--ces-texto-2); font: 600 .8125rem/1 var(--ces-fonte); cursor: pointer;
      transition: background-color var(--ces-m-entrar), color var(--ces-m-entrar); }
    .atalho strong { min-width: 20px; height: 20px; padding: 0 6px; box-sizing: border-box; border-radius: 10px; display: inline-grid; place-items: center;
      font: 700 .6875rem/1 var(--ces-fonte); background: var(--ces-superficie); color: var(--ces-texto); }
    .atalho[aria-current='true'] { background: var(--ces-superficie); color: var(--ces-texto); box-shadow: 0 1px 2px rgba(0, 0, 0, .1); }
    .atalho[aria-current='true'][data-tom='atencao'] strong { background: var(--ces-atencao-suave); color: var(--ces-atencao); }
    .atalho[aria-current='true'][data-tom='destaque'] strong { background: var(--ces-destaque-suave); color: var(--ces-destaque); }
    .atalho[aria-current='true'] strong { background: var(--ces-superficie-2); }
    .trilho { display: grid; grid-auto-flow: column; grid-auto-columns: 100%; gap: var(--ces-e3); overflow-x: auto; overscroll-behavior-x: contain;
      align-items: start; scroll-snap-type: x mandatory; scroll-padding: 0 var(--ces-e3); padding: var(--ces-e3) var(--ces-e3) var(--ces-e2); scrollbar-width: none; transition: opacity var(--ces-m-entrar); }
    .trilho::-webkit-scrollbar { display: none; }
    .trilho.atualizando { opacity: .6; }
    .coluna { scroll-snap-align: start; display: grid; align-content: start; gap: var(--ces-e3); min-width: 0; padding: var(--ces-e2);
      border-radius: var(--ces-r-folha); background: var(--ces-superficie-2); }
    .coluna[data-tom='atencao'] { --cor: var(--ces-atencao); --suave: var(--ces-atencao-suave); }
    .coluna[data-tom='destaque'] { --cor: var(--ces-destaque); --suave: var(--ces-destaque-suave); }
    .coluna[data-tom='neutro'] { --cor: var(--ces-texto-2); --suave: var(--ces-superficie-2); }
    .cab { display: flex; align-items: center; gap: var(--ces-e3); padding: var(--ces-e2) var(--ces-e2) var(--ces-e1); }
    .cab__icone { width: 32px; height: 32px; border-radius: 10px; display: grid; place-items: center; background: var(--suave); color: var(--cor); flex: none; }
    .cab__txt { flex: 1; min-width: 0; }
    .cab h3 { margin: 0; font: 600 .9375rem/1.25 var(--ces-fonte); }
    .cab p { margin: 0; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .cab__total { min-width: 26px; height: 24px; padding: 0 8px; box-sizing: border-box; border-radius: 12px; display: grid; place-items: center; background: var(--ces-superficie); font: 700 .8125rem/1 var(--ces-fonte); color: var(--cor); }
    .alertas { display: flex; gap: 6px; flex-wrap: wrap; padding: 0 var(--ces-e1); }
    .alerta { display: inline-flex; align-items: center; gap: 4px; padding: 4px 9px; border-radius: var(--ces-r-pilula); font: 600 .75rem/1.3 var(--ces-fonte); }
    .alerta--erro { background: var(--ces-erro-suave); color: var(--ces-erro); }
    .alerta--atencao { background: var(--ces-atencao-suave); color: var(--ces-atencao); }
    .vazio { display: grid; justify-items: center; gap: 4px; padding: var(--ces-e5) var(--ces-e4); margin: 0 var(--ces-e1); border-radius: var(--ces-r-grupo); background: var(--ces-superficie); text-align: center; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .vazio strong { font: var(--ces-t-corpo-forte); color: var(--ces-texto); }
    .vazio__icone { width: 40px; height: 40px; border-radius: 50%; display: grid; place-items: center; margin-bottom: 4px; background: var(--ces-ok-suave); color: var(--ces-ok); }
    .grupo { display: grid; gap: var(--ces-e2); }
    .grupo__cab { display: flex; align-items: center; gap: var(--ces-e2); min-height: 36px; padding: 0 var(--ces-e2); border: 0; background: transparent; color: var(--ces-texto);
      font: 600 .8125rem/1.2 var(--ces-fonte); cursor: pointer; text-align: left; border-radius: var(--ces-r-campo); }
    .grupo__cab:active { background: var(--ces-linha); }
    .grupo__ponto { width: 8px; height: 8px; border-radius: 50%; background: var(--ces-texto-3); flex: none; }
    .grupo__cab[data-tom='erro'] .grupo__ponto { background: var(--ces-erro); }
    .grupo__cab[data-tom='atencao'] .grupo__ponto { background: var(--ces-atencao); }
    .grupo__cab[data-tom='destaque'] .grupo__ponto { background: var(--ces-destaque); }
    .grupo__cab[data-tom='ok'] .grupo__ponto { background: var(--ces-ok); }
    .grupo__rotulo { flex: 1; }
    .conta { font: 700 .75rem/1 var(--ces-fonte); color: var(--ces-texto-2); }
    .seta { transition: transform var(--ces-m-entrar) var(--ces-curva); color: var(--ces-texto-3); }
    .seta.aberta { transform: rotate(90deg); }
    .cartoes { display: grid; gap: var(--ces-e2); }
    .mais { margin: 0; text-align: center; font: var(--ces-t-apoio); color: var(--ces-texto-2); padding: var(--ces-e2); }
  `],
})
export class QuadroSolicitacoesComponent {
  private readonly almox = inject(AlmoxService);
  readonly filtro = input.required<FiltroSolicitacoes>();
  readonly acoes = input(false);
  readonly ocupado = input<{ id: string; acao: AcaoCartao } | null>(null);
  readonly abrir = output<ResumoSolicitacao>();
  readonly agir = output<{ acao: AcaoCartao; item: ResumoSolicitacao }>();

  readonly agrupamentos = AGRUPAMENTOS;
  readonly agrupamento = signal<Agrupamento>(lerAgrupamento());
  readonly folhaAgrupar = signal(false);
  readonly rotuloAgrupamento = computed(() => AGRUPAMENTOS.find(a => a.valor === this.agrupamento())?.rotulo ?? '');
  readonly faixas = signal<Faixa[] | null>(null);
  readonly carregando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly visivel = signal(0);
  private readonly recolhidos = signal<Record<string, boolean>>({});
  private readonly versao = signal(0);
  /** Coluna para onde o quadro pula assim que o trilho aparece na tela. */
  private readonly alvoInicial = signal(-1);
  private readonly trilho = viewChild<ElementRef<HTMLElement>>('trilho');

  readonly colunas = computed<Coluna[]>(() => {
    const ag = this.agrupamento();
    const brutas = this.faixas() ?? [];
    // Parcial com saldo encerrado não tem o que entregar: sai de Entrega e vai para Encerradas.
    const parcial = brutas.find(f => f.status === 'ENTREGUE_PARCIAL');
    const encerradas = parcial?.itens.filter(i => i.encerrada) ?? [];
    const faixas = brutas.map(f => (f === parcial && encerradas.length ? { ...f, itens: f.itens.filter(i => !i.encerrada), total: Math.max(0, f.total - encerradas.length) } : f));
    return COLUNAS.map(def => {
      const minhas = def.statuses.map(s => faixas.find(f => f.status === s) ?? { status: s, itens: [], total: 0 });
      const itens = minhas.flatMap(f => f.itens);
      let grupos = ag === 'situacao' || def.id === 'ENCERRADAS' ? porSituacao(minhas) : ag === 'pendencia' ? porPendencia(itens) : porPrazo(itens);
      if (def.id === 'ENCERRADAS' && encerradas.length) {
        grupos = [grupos[0], { id: 'PARCIAL_ENCERRADA', rotulo: 'Em parte · saldo encerrado', tom: 'neutro', itens: encerradas, total: encerradas.length }, ...grupos.slice(1)];
      }
      return {
        def, grupos, varios: grupos.filter(g => g.total > 0).length > 1,
        total: minhas.reduce((a, f) => a + f.total, 0) + (def.id === 'ENCERRADAS' ? encerradas.length : 0),
        atrasadas: def.id === 'ENCERRADAS' ? 0 : itens.filter(s => saqueAtrasado(s)).length,
        pendencias: def.id === 'ENCERRADAS' ? 0 : itens.filter(s => s.possuiPendencia).length,
      };
    });
  });

  constructor() {
    effect(() => {
      const el = this.trilho();
      const alvo = this.alvoInicial();
      if (!el || alvo < 0) return;
      untracked(() => {
        this.alvoInicial.set(-1);
        requestAnimationFrame(() => this.irPara(alvo, false));
      });
    });
    combineLatest([toObservable(this.filtro), toObservable(this.versao)]).pipe(
      tap(() => { this.carregando.set(true); this.erro.set(null); }),
      switchMap(([f]) => this.buscar(f).pipe(catchError(e => { this.erro.set(e?.detail || 'Verifique a conexão e tente de novo.'); return of(null); }))),
      takeUntilDestroyed(),
    ).subscribe(faixas => {
      this.carregando.set(false);
      if (!faixas) return;
      const primeira = !this.faixas();
      this.faixas.set(faixas);
      // Abre na primeira coluna que tem trabalho (ex.: nada para aprovar → Entrega).
      if (primeira) {
        const alvo = this.colunas().findIndex(c => c.total > 0 && c.def.id !== 'ENCERRADAS');
        if (alvo > 0) this.alvoInicial.set(alvo);
      }
    });
  }

  recarregar(): void {
    this.versao.update(v => v + 1);
  }

  private buscar(filtro: FiltroSolicitacoes): Observable<Faixa[]> {
    const { statuses, ...resto } = filtro;
    const pedidos = COLUNAS.flatMap(c => c.statuses).filter(s => !statuses?.length || statuses.includes(s));
    if (!pedidos.length) return of([]);
    return forkJoin(pedidos.map(status => {
      const aberto = EM_ABERTO.includes(status);
      return this.almox.listarSolicitacoes({
        ...resto, statuses: [status], page: 0, size: aberto ? LIMITE_ABERTAS : LIMITE_ENCERRADAS,
        sort: aberto ? 'dataPrevistaRetirada,asc' : 'dataSolicitacao,desc',
      }).pipe(map(r => ({ status, itens: r.content ?? [], total: r.page?.totalElements ?? r.content?.length ?? 0 })));
    }));
  }

  agrupar(a: Agrupamento): void {
    this.agrupamento.set(a);
    try { localStorage.setItem(CHAVE_AGRUPAMENTO, a); } catch { /* preferência opcional */ }
  }

  recolhido(id: string): boolean {
    return this.recolhidos()[`${this.agrupamento()}:${id}`] ?? false;
  }

  alternar(id: string): void {
    const chave = `${this.agrupamento()}:${id}`;
    this.recolhidos.update(r => ({ ...r, [chave]: !r[chave] }));
  }

  aoRolar(): void {
    const el = this.trilho()?.nativeElement;
    const col = el?.querySelector<HTMLElement>('.coluna');
    if (!el || !col) return;
    this.visivel.set(Math.max(0, Math.min(COLUNAS.length - 1, Math.round(el.scrollLeft / (col.offsetWidth + 12)))));
  }

  irPara(i: number, animar = true): void {
    const el = this.trilho()?.nativeElement;
    const col = el?.querySelectorAll<HTMLElement>('.coluna')[i];
    if (!el || !col) return;
    const reduzir = !animar || matchMedia('(prefers-reduced-motion: reduce)').matches;
    el.scrollTo({ left: el.scrollLeft + col.getBoundingClientRect().left - el.getBoundingClientRect().left - 12, behavior: reduzir ? 'auto' : 'smooth' });
    this.visivel.set(i);
  }
}

function porSituacao(faixas: Faixa[]): Grupo[] {
  return faixas.map(f => ({ id: f.status, rotulo: defStatus(f.status).atalho, tom: defStatus(f.status).tom, itens: f.itens, total: f.total }));
}

function porPendencia(itens: ResumoSolicitacao[]): Grupo[] {
  const grupos = new Map<string, ResumoSolicitacao[]>();
  const sem: ResumoSolicitacao[] = [];
  for (const it of itens) {
    const motivos = it.motivosPendencia ?? [];
    if (!it.possuiPendencia && !motivos.length) { sem.push(it); continue; }
    const chave = motivos.length === 1 ? motivos[0] : motivos.length > 1 ? 'Várias pendências' : 'Pendência sem motivo';
    grupos.set(chave, [...(grupos.get(chave) ?? []), it]);
  }
  const com = [...grupos.entries()].sort((a, b) => b[1].length - a[1].length || a[0].localeCompare(b[0], 'pt-BR'))
    .map(([m, l]): Grupo => ({ id: `P:${m}`, rotulo: m, tom: 'atencao', itens: l, total: l.length }));
  return [...com, { id: 'P:-', rotulo: 'Sem pendência', tom: 'ok', itens: sem, total: sem.length }];
}

function porPrazo(itens: ResumoSolicitacao[]): Grupo[] {
  const hoje = new Date();
  const h = isoCivil(hoje);
  const semana = isoCivil(new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate() + 7));
  const dia = (s: ResumoSolicitacao) => s.dataPrevista?.substring(0, 10) ?? '';
  const faixas: { id: string; rotulo: string; tom: Tom; teste: (s: ResumoSolicitacao) => boolean }[] = [
    { id: 'ATRASADAS', rotulo: 'Atrasadas', tom: 'erro', teste: s => saqueAtrasado(s, hoje) },
    { id: 'HOJE', rotulo: 'Saque hoje', tom: 'atencao', teste: s => dia(s) === h },
    { id: 'SEMANA', rotulo: 'Próximos 7 dias', tom: 'destaque', teste: s => !!dia(s) && dia(s) > h && dia(s) <= semana },
    { id: 'DEPOIS', rotulo: 'Mais adiante', tom: 'neutro', teste: s => !!dia(s) && dia(s) > semana },
    { id: 'SEM', rotulo: 'Sem data de saque', tom: 'neutro', teste: () => true },
  ];
  const restantes = new Set(itens);
  return faixas.map(f => {
    const lista = [...restantes].filter(f.teste);
    lista.forEach(s => restantes.delete(s));
    return { id: f.id, rotulo: f.rotulo, tom: f.tom, itens: lista, total: lista.length };
  });
}

function lerAgrupamento(): Agrupamento {
  try {
    const v = localStorage.getItem(CHAVE_AGRUPAMENTO);
    return v === 'pendencia' || v === 'prazo' ? v : 'situacao';
  } catch {
    return 'situacao';
  }
}
