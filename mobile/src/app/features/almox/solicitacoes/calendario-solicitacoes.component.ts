import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, of, switchMap, tap } from 'rxjs';
import { AlmoxService } from '../../../core/almox/almox.service';
import { FiltroSolicitacoes, ResumoSolicitacao } from '../../../core/almox/almox.models';
import { EM_ABERTO, dataCivil, isoCivil, saqueAtrasado } from '../../../core/almox/almox-status';
import { IconeComponent, VazioComponent } from '../../../ui';
import { AcaoCartao, CartaoSolicitacaoComponent } from './cartao-solicitacao.component';

const LIMITE = 500;
const SEMANA = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
const SEMANA_EXTENSO = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado'];
const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];

type Marca = 'atraso' | 'aberta' | 'encerrada';

interface Dia {
  iso: string;
  numero: number;
  noMes: boolean;
  hoje: boolean;
  passado: boolean;
  itens: ResumoSolicitacao[];
  marcas: Marca[];
}

const marcaDe = (s: ResumoSolicitacao): Marca => (saqueAtrasado(s) ? 'atraso' : EM_ABERTO.includes(s.status) && !s.encerrada ? 'aberta' : 'encerrada');

/**
 * Agenda de saques previstos: o mês em grade (cada dia mostra pontos coloridos — em aberto,
 * atrasado, encerrado) e, embaixo, a lista do dia tocado. Dia vazio mostra os próximos saques,
 * para nunca ficar uma tela "morta". Trocar de mês é pelos botões, não por deslize acidental.
 */
@Component({
  selector: 'app-calendario-solicitacoes',
  standalone: true,
  imports: [CartaoSolicitacaoComponent, IconeComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mes" aria-label="Calendário de saques">
      <header class="cab">
        <div class="titulo" aria-live="polite">
          <h3>{{ nomeMes() }} <span>{{ mes().getFullYear() }}</span></h3>
          <p class="ces-num">
            @if (carregou()) {
              {{ resumoMes().total }} {{ resumoMes().total === 1 ? 'saque' : 'saques' }}
              @if (resumoMes().abertas) { · {{ resumoMes().abertas }} em aberto }
              @if (resumoMes().atrasadas) { · <span class="atraso">{{ resumoMes().atrasadas }} {{ resumoMes().atrasadas === 1 ? 'atrasado' : 'atrasados' }}</span> }
            } @else { Carregando… }
          </p>
        </div>
        @if (!mesAtual()) { <button type="button" class="ces-btn ces-btn--tonal ces-btn--compacto hoje-btn" (click)="irHoje()">Hoje</button> }
        <div class="nav">
          <button type="button" class="ces-btn-icone" (click)="mover(-1)" aria-label="Mês anterior"><ces-icone nome="voltar" /></button>
          <button type="button" class="ces-btn-icone" (click)="mover(1)" aria-label="Próximo mês"><ces-icone nome="avancar" /></button>
        </div>
      </header>
      <div class="semana" aria-hidden="true">@for (d of semana; track $index) { <span [class.fds]="$index === 0 || $index === 6">{{ d }}</span> }</div>
      <div class="grade" role="grid" [attr.aria-busy]="carregando()" [class.atualizando]="carregando()">
        @for (sem of semanas(); track $index) {
          <div class="linha" role="row">
            @for (d of sem; track d.iso) {
              <button type="button" role="gridcell" class="dia" [class.fora]="!d.noMes" [class.hoje]="d.hoje" [class.passado]="d.passado" [class.com]="d.itens.length > 0"
                [attr.aria-selected]="selecionado() === d.iso" (click)="selecionar(d.iso)" [attr.aria-label]="rotuloDia(d)">
                <span class="num ces-num">{{ d.numero }}</span>
                <span class="marcas" aria-hidden="true">
                  @for (m of d.marcas; track $index) { <i [attr.data-marca]="m"></i> }
                  @if (d.itens.length > 3) { <b class="ces-num">+{{ d.itens.length - 3 }}</b> }
                </span>
              </button>
            }
          </div>
        }
      </div>
      <p class="legenda">
        <span><i data-marca="aberta"></i>Em aberto</span>
        <span><i data-marca="atraso"></i>Atrasado</span>
        <span><i data-marca="encerrada"></i>Encerrado</span>
      </p>
    </section>

    @if (erro()) {
      <ces-vazio icone="alerta" titulo="Não foi possível carregar o calendário" [texto]="erro()">
        <button type="button" class="ces-btn ces-btn--tonal" (click)="recarregar()">Tentar de novo</button>
      </ces-vazio>
    } @else {
      @if (excedeu()) {
        <p class="aviso"><ces-icone nome="info" [tamanho]="18" />Há mais de {{ limite }} saques neste período; mostrando os {{ limite }} primeiros. Use os filtros para refinar.</p>
      }
      <header class="dia-cab">
        <span class="dia-cab__data" [class.hoje]="selecionadoEhHoje()">
          <span class="ces-num">{{ diaSelecionado().getDate() }}</span><small>{{ nomeMesCurto() }}</small>
        </span>
        <div>
          <h3>{{ selecionadoEhHoje() ? 'Hoje' : semanaExtenso() }}</h3>
          <p>{{ doDia().length ? doDia().length + (doDia().length === 1 ? ' saque previsto' : ' saques previstos') : 'Nenhum saque previsto' }}</p>
        </div>
      </header>
      @if (carregando() && !carregou()) {
        <div class="ces-esqueletos" aria-busy="true"><div class="ces-esqueleto" style="height: 96px"></div></div>
      } @else if (doDia().length) {
        <div class="cartoes">
          @for (s of doDia(); track s.id) {
            <app-cartao-solicitacao [item]="s" [semData]="true" [acoes]="acoes()" [ocupado]="ocupado()?.id === s.id ? ocupado()!.acao : null" (abrir)="abrir.emit($event)" (agir)="agir.emit($event)" />
          }
        </div>
      } @else if (proximos().length) {
        <h4 class="ces-secao">Próximos saques</h4>
        <ul class="ces-grupo proximos">
          @for (p of proximos(); track p.iso) {
            <li><button type="button" class="ces-linha ces-linha--acao" (click)="selecionar(p.iso)">
              <span class="mini-data"><span class="ces-num">{{ p.dia }}</span><small>{{ p.mes }}</small></span>
              <span class="ces-linha__texto"><span class="ces-linha__titulo">{{ p.titulo }}</span>
                <span class="ces-linha__apoio">{{ p.total }} {{ p.total === 1 ? 'saque' : 'saques' }}{{ p.atrasadas ? ' · ' + p.atrasadas + ' atrasado' + (p.atrasadas > 1 ? 's' : '') : '' }}</span></span>
              <ces-icone nome="avancar" [tamanho]="18" />
            </button></li>
          }
        </ul>
      }
    }
  `,
  styles: [`
    :host { display: block; }
    .mes { margin: var(--ces-e3) var(--ces-e3) 0; padding: var(--ces-e4) var(--ces-e3) var(--ces-e3); border-radius: var(--ces-r-folha); background: var(--ces-superficie);
      box-shadow: 0 1px 2px rgba(14, 23, 38, .05); }
    .cab { display: flex; align-items: center; gap: var(--ces-e2); padding: 0 var(--ces-e1) var(--ces-e3) var(--ces-e2); }
    .titulo { flex: 1; min-width: 0; }
    .titulo h3 { margin: 0; font: 600 1.0625rem/1.25 var(--ces-fonte); }
    .titulo h3 span { font-weight: 500; color: var(--ces-texto-3); }
    .titulo p { margin: 2px 0 0; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .titulo .atraso { color: var(--ces-erro); font-weight: 600; }
    .nav { display: flex; margin-right: -6px; }
    .hoje-btn { min-height: 36px; }
    .semana, .linha { display: grid; grid-template-columns: repeat(7, 1fr); }
    .semana span { text-align: center; font: 600 .6875rem/2 var(--ces-fonte); letter-spacing: .04em; text-transform: uppercase; color: var(--ces-texto-3); }
    .grade { display: grid; gap: 2px; transition: opacity var(--ces-m-entrar); }
    .grade.atualizando { opacity: .55; }
    .dia { display: grid; justify-items: center; align-content: start; gap: 3px; min-height: 46px; padding: 4px 0; border: 0; border-radius: 14px;
      background: transparent; color: var(--ces-texto); cursor: pointer; font: inherit; transition: background-color 100ms; }
    .dia:active { background: var(--ces-superficie-2); }
    .dia.fora { color: var(--ces-texto-3); }
    .dia.fora .marcas { opacity: .5; }
    .dia.passado:not(.com) .num { color: var(--ces-texto-3); }
    .num { width: 32px; height: 32px; display: grid; place-items: center; border-radius: 50%; font: 500 .9375rem/1 var(--ces-fonte);
      transition: background-color var(--ces-m-entrar), color var(--ces-m-entrar); }
    .dia.com .num { font-weight: 700; }
    .dia.hoje .num { color: var(--ces-destaque); box-shadow: inset 0 0 0 2px var(--ces-destaque); font-weight: 700; }
    .dia[aria-selected='true'] .num { background: var(--ces-destaque); color: var(--ces-destaque-texto); box-shadow: none; }
    .marcas { display: flex; align-items: center; justify-content: center; gap: 3px; height: 6px; }
    .marcas b { font: 700 .5625rem/1 var(--ces-fonte); color: var(--ces-texto-2); }
    i[data-marca] { display: block; width: 6px; height: 6px; border-radius: 50%; }
    i[data-marca='aberta'] { background: var(--ces-destaque); }
    i[data-marca='atraso'] { background: var(--ces-erro); }
    i[data-marca='encerrada'] { background: var(--ces-linha-forte); }
    .legenda { display: flex; justify-content: center; flex-wrap: wrap; gap: 4px var(--ces-e4); margin: var(--ces-e3) 0 0; padding-top: var(--ces-e3);
      border-top: 1px solid var(--ces-linha); font: var(--ces-t-apoio); font-size: .75rem; color: var(--ces-texto-2); }
    .legenda span { display: inline-flex; align-items: center; gap: 6px; }
    .aviso { display: flex; gap: var(--ces-e2); align-items: flex-start; margin: var(--ces-e3) var(--ces-e3) 0; padding: var(--ces-e3); border-radius: var(--ces-r-grupo);
      background: var(--ces-atencao-suave); font: var(--ces-t-apoio); }
    .dia-cab { display: flex; align-items: center; gap: var(--ces-e3); padding: var(--ces-e4) var(--ces-e4) var(--ces-e3); }
    .dia-cab h3 { margin: 0; font: 600 .9375rem/1.3 var(--ces-fonte); }
    .dia-cab p { margin: 0; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .dia-cab__data { width: 40px; height: 44px; border-radius: 12px; display: grid; place-content: center; justify-items: center; background: var(--ces-destaque); color: var(--ces-destaque-texto); }
    .dia-cab__data span { font: 700 1rem/1 var(--ces-fonte); }
    .dia-cab__data small { font: 700 .625rem/1.2 var(--ces-fonte); letter-spacing: .06em; text-transform: uppercase; opacity: .85; }
    .cartoes { display: grid; gap: var(--ces-e2); padding: 0 var(--ces-e3); }
    .proximos .ces-linha { min-height: 64px; }
    .mini-data { width: 40px; height: 44px; border-radius: 10px; display: grid; place-content: center; justify-items: center; background: var(--ces-superficie-2); flex: none; }
    .mini-data span { font: 700 1rem/1 var(--ces-fonte); }
    .mini-data small { font: 700 .5625rem/1.2 var(--ces-fonte); letter-spacing: .05em; text-transform: uppercase; color: var(--ces-texto-2); }
    .vazio-dia { margin: 0 var(--ces-e5); font: var(--ces-t-apoio); color: var(--ces-texto-2); }
  `],
})
export class CalendarioSolicitacoesComponent {
  private readonly almox = inject(AlmoxService);
  readonly filtro = input.required<FiltroSolicitacoes>();
  readonly acoes = input(false);
  readonly ocupado = input<{ id: string; acao: AcaoCartao } | null>(null);
  readonly abrir = output<ResumoSolicitacao>();
  readonly agir = output<{ acao: AcaoCartao; item: ResumoSolicitacao }>();

  readonly semana = SEMANA;
  readonly limite = LIMITE;
  private readonly hoje = new Date();
  private readonly hojeIso = isoCivil(this.hoje);
  readonly mes = signal(new Date(this.hoje.getFullYear(), this.hoje.getMonth(), 1));
  readonly selecionado = signal(this.hojeIso);
  readonly itens = signal<ResumoSolicitacao[]>([]);
  readonly excedeu = signal(false);
  readonly carregando = signal(false);
  readonly carregou = signal(false);
  readonly erro = signal<string | null>(null);
  private readonly versao = signal(0);

  private readonly inicioGrade = computed(() => {
    const m = this.mes();
    return new Date(m.getFullYear(), m.getMonth(), 1 - m.getDay());
  });
  private readonly fimGrade = computed(() => {
    const m = this.mes();
    const ultimo = new Date(m.getFullYear(), m.getMonth() + 1, 0);
    return new Date(ultimo.getFullYear(), ultimo.getMonth(), ultimo.getDate() + (6 - ultimo.getDay()));
  });

  readonly nomeMes = computed(() => MESES[this.mes().getMonth()]);
  readonly mesAtual = computed(() => this.mes().getMonth() === this.hoje.getMonth() && this.mes().getFullYear() === this.hoje.getFullYear());
  readonly diaSelecionado = computed(() => dataCivil(this.selecionado()));
  readonly selecionadoEhHoje = computed(() => this.selecionado() === this.hojeIso);
  readonly semanaExtenso = computed(() => `${SEMANA_EXTENSO[this.diaSelecionado().getDay()]}, ${this.diaSelecionado().getDate()} de ${MESES[this.diaSelecionado().getMonth()].toLowerCase()}`);
  readonly nomeMesCurto = computed(() => MESES[this.diaSelecionado().getMonth()].substring(0, 3));

  private readonly porDia = computed(() => {
    const mapa = new Map<string, ResumoSolicitacao[]>();
    for (const s of this.itens()) {
      const d = s.dataPrevista?.substring(0, 10);
      if (!d) continue;
      mapa.set(d, [...(mapa.get(d) ?? []), s]);
    }
    return mapa;
  });

  readonly resumoMes = computed(() => {
    const m = this.mes().getMonth();
    const doMes = this.itens().filter(s => s.dataPrevista && dataCivil(s.dataPrevista).getMonth() === m);
    return {
      total: doMes.length,
      abertas: doMes.filter(s => marcaDe(s) === 'aberta').length,
      atrasadas: doMes.filter(s => marcaDe(s) === 'atraso').length,
    };
  });

  readonly semanas = computed<Dia[][]>(() => {
    const fim = this.fimGrade();
    const mes = this.mes().getMonth();
    const semanas: Dia[][] = [];
    for (let d = new Date(this.inicioGrade()); d <= fim; d = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1)) {
      if (d.getDay() === 0) semanas.push([]);
      const iso = isoCivil(d);
      const itens = this.porDia().get(iso) ?? [];
      // Até 3 pontos, do mais urgente para o menos.
      const ordem: Marca[] = ['atraso', 'aberta', 'encerrada'];
      const marcas = itens.map(marcaDe).sort((a, b) => ordem.indexOf(a) - ordem.indexOf(b)).slice(0, 3);
      semanas[semanas.length - 1].push({ iso, numero: d.getDate(), noMes: d.getMonth() === mes, hoje: iso === this.hojeIso, passado: iso < this.hojeIso, itens, marcas });
    }
    return semanas;
  });

  readonly doDia = computed(() => {
    const lista = this.porDia().get(this.selecionado()) ?? [];
    return [...lista].sort((a, b) => Number(EM_ABERTO.includes(b.status)) - Number(EM_ABERTO.includes(a.status)));
  });

  /** Próximos dias com saque a partir do selecionado (dentro do período carregado). */
  readonly proximos = computed(() => [...this.porDia().entries()]
    .filter(([iso]) => iso > this.selecionado())
    .sort(([a], [b]) => a.localeCompare(b))
    .slice(0, 4)
    .map(([iso, lista]) => {
      const d = dataCivil(iso);
      return {
        iso, dia: d.getDate(), mes: MESES[d.getMonth()].substring(0, 3), total: lista.length,
        atrasadas: lista.filter(s => saqueAtrasado(s)).length,
        titulo: iso === isoCivil(new Date(this.hoje.getFullYear(), this.hoje.getMonth(), this.hoje.getDate() + 1)) ? 'Amanhã' : `${SEMANA_EXTENSO[d.getDay()]}, ${d.getDate()} de ${MESES[d.getMonth()].toLowerCase()}`,
      };
    }));

  constructor() {
    combineLatest([toObservable(this.filtro), toObservable(this.inicioGrade), toObservable(this.versao)]).pipe(
      tap(() => { this.carregando.set(true); this.erro.set(null); }),
      switchMap(([f]) => this.almox.listarSolicitacoes({
        ...f, previstaDe: isoCivil(this.inicioGrade()), previstaAte: isoCivil(this.fimGrade()), page: 0, size: LIMITE, sort: 'dataPrevistaRetirada,asc',
      }).pipe(catchError(e => { this.erro.set(e?.detail || 'Verifique a conexão e tente de novo.'); return of(null); }))),
      takeUntilDestroyed(),
    ).subscribe(r => {
      this.carregando.set(false);
      if (!r) return;
      this.itens.set(r.content ?? []);
      this.excedeu.set((r.page?.totalElements ?? 0) > LIMITE);
      this.carregou.set(true);
    });
  }

  recarregar(): void {
    this.versao.update(v => v + 1);
  }

  mover(delta: number): void {
    const m = this.mes();
    const novo = new Date(m.getFullYear(), m.getMonth() + delta, 1);
    this.mes.set(novo);
    const ehAtual = novo.getMonth() === this.hoje.getMonth() && novo.getFullYear() === this.hoje.getFullYear();
    this.selecionado.set(ehAtual ? this.hojeIso : isoCivil(novo));
  }

  irHoje(): void {
    this.mes.set(new Date(this.hoje.getFullYear(), this.hoje.getMonth(), 1));
    this.selecionado.set(this.hojeIso);
  }

  selecionar(iso: string): void {
    this.selecionado.set(iso);
    const d = dataCivil(iso);
    if (d.getMonth() !== this.mes().getMonth() || d.getFullYear() !== this.mes().getFullYear()) this.mes.set(new Date(d.getFullYear(), d.getMonth(), 1));
  }

  rotuloDia(d: Dia): string {
    const data = dataCivil(d.iso);
    const base = `${data.getDate()} de ${MESES[data.getMonth()].toLowerCase()}`;
    if (!d.itens.length) return `${base}, sem saques`;
    const atrasados = d.marcas.filter(m => m === 'atraso').length;
    return `${base}, ${d.itens.length} ${d.itens.length === 1 ? 'saque' : 'saques'}` + (atrasados ? `, com atraso` : '');
  }
}
