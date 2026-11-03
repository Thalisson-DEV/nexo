import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, inject, input, output, signal } from '@angular/core';
import { BarraGantt, LinhaGantt } from '../../../core/models/obras-programacao.models';
import { formatarDataParaApi, formatarMoeda, parseDataBrParaDate } from '../obras-ui';
import { ModoArraste, SITUACAO_PROGRAMACAO, datasArrastadas, diasDoPeriodo, formatarNumero, posicionarBarras, temConflito } from './programacao.logic';

const ALTURA_BARRA = { semana: 3, mes: 2 };
const FOLGA_LINHA_REM = 0.25;
/** Abaixo disso é clique, não arraste. */
const LIMIAR_ARRASTE_PX = 4;

/** Pedido de reprogramação vindo de um arraste. */
export interface ArrasteProgramacao {
  barra: BarraGantt;
  idEquipe: string;
  idEquipeOrigem: string;
  inicio: string; // dd/MM/yyyy
  fim: string; // dd/MM/yyyy
}

interface Arraste {
  barra: BarraGantt;
  idEquipeOrigem: string;
  modo: ModoArraste;
  xInicial: number;
  yInicial: number;
  larguraDia: number;
  ativo: boolean;
  dias: number;
  idEquipe: string;
}

/**
 * Programações em linha do tempo por equipe. Na semana cabe a obra escrita; no mês, só o número. Equipe sem nada aparece
 * como livre. Com permissão, a barra arrasta para outro dia ou equipe e estica pelas pontas; o mesmo se faz pelo detalhe,
 * que é o caminho de quem usa teclado ou toque.
 */
@Component({
  selector: 'app-programacao-linha-tempo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tempo" role="region" aria-label="Linha do tempo por equipe" tabindex="0">
      <div class="tempo__grade" [attr.data-modo]="modo()" [style.--dias]="dias().length">
        <div class="tempo__canto">Equipe</div>
        @for (dia of dias(); track dia.data.getTime()) {
          <div class="tempo__dia" [class.tempo__dia--fim]="dia.fimDeSemana" [class.tempo__dia--hoje]="dia.hoje" [attr.aria-current]="dia.hoje ? 'date' : null">
            <small>{{ dia.semana }}</small><span class="tempo__numero">{{ dia.dia }}</span>
          </div>
        }
        @for (linha of grade(); track linha.idEquipe) {
          <div class="tempo__equipe" [style.min-height.rem]="linha.altura">
            <span class="tempo__codigo"><strong>{{ linha.codigo }}</strong>
              @if (linha.conflito) {
                <span class="tempo__conflito" title="Duas programações nos mesmos dias e turnos"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i> Conflito</span>
              }
            </span>
            <small [title]="linha.tipoEquipe + ' · ' + linha.base">{{ linha.tipoEquipe }} · {{ linha.base }}</small>
            @if (linha.percentualMeta) {
              <span class="tempo__meta" [attr.data-acima]="linha.percentualMeta > 100">
                <span class="tempo__meta-barra" aria-hidden="true"><span [style.width.%]="largura(linha.percentualMeta)"></span></span>
                {{ numero(linha.percentualMeta) }}% da meta
              </span>
            } @else if (linha.valorProgramado > 0) {
              <small>{{ moeda(linha.valorProgramado) }}</small>
            }
          </div>
          <div class="tempo__trilho" [attr.data-equipe]="linha.idEquipe" [attr.data-alvo]="previa()?.idEquipe === linha.idEquipe" [style.min-height.rem]="linha.altura">
            @for (dia of dias(); track dia.data.getTime()) {
              <span class="tempo__coluna" [class.tempo__coluna--fim]="dia.fimDeSemana" [class.tempo__coluna--hoje]="dia.hoje"></span>
            }
            @if (!linha.barras.length) {
              <span class="tempo__livre"><i class="pi pi-check" aria-hidden="true"></i> Livre no período</span>
            }
            @if (previa(); as g) {
              @if (g.idEquipe === linha.idEquipe) {
                <span class="tempo__fantasma" aria-hidden="true" [style.left]="'calc(100% / var(--dias) * ' + g.coluna + ' + 3px)'" [style.width]="'calc(100% / var(--dias) * ' + g.largura + ' - 6px)'">
                  {{ g.inicio }} a {{ g.fim }}
                </span>
              }
            }
            @for (b of linha.barras; track b.barra.id) {
              <button type="button" class="tempo__barra" [attr.data-situacao]="b.barra.situacao"
                [class.tempo__barra--arrastavel]="podeArrastar(b.barra)" [class.tempo__barra--origem]="previa()?.id === b.barra.id"
                (pointerdown)="iniciar($event, b.barra, linha.idEquipe)"
                [class.tempo__barra--corte-inicio]="b.cortadaNoInicio" [class.tempo__barra--corte-fim]="b.cortadaNoFim"
                [style.left]="'calc(100% / var(--dias) * ' + b.coluna + ' + 3px)'"
                [style.width]="'calc(100% / var(--dias) * ' + b.largura + ' - 6px)'"
                [style.top]="'calc(100% / ' + linha.faixas + ' * ' + b.faixa + ' + 3px)'"
                [style.height]="'calc(100% / ' + linha.faixas + ' - 6px)'"
                [title]="descrever(b.barra)" [attr.aria-label]="descrever(b.barra)" (click)="clicar($event, b.barra.id)">
                @if (podeArrastar(b.barra)) {
                  <span class="tempo__ponta tempo__ponta--inicio" data-ponta="inicio" aria-hidden="true"></span>
                  <span class="tempo__ponta tempo__ponta--fim" data-ponta="fim" aria-hidden="true"></span>
                }
                <i [class]="situacoes[b.barra.situacao].icone" aria-hidden="true"></i>
                <span class="tempo__texto">
                  <strong>#{{ b.barra.numero }} · {{ b.barra.numeroObra }}</strong>
                  @if (modo() === 'semana') { <small>{{ b.barra.tituloObra }}</small> }
                </span>
              </button>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; min-width: 0; }
    .tempo { overflow-x: auto; }
    .tempo:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: -2px; }
    .tempo__grade { display: grid; grid-template-columns: 13rem repeat(var(--dias), minmax(2.5rem, 1fr)); min-width: max-content; }
    .tempo__grade[data-modo='semana'] { grid-template-columns: 13rem repeat(var(--dias), minmax(7.5rem, 1fr)); }
    .tempo__canto, .tempo__equipe { position: sticky; left: 0; z-index: 2; padding: 0.5rem 0.75rem; border-right: 1px solid var(--ces-border); border-bottom: 1px solid var(--ces-border); background: var(--ces-surface); }
    .tempo__canto { display: flex; align-items: flex-end; color: var(--ces-foreground-muted); font-size: var(--ces-font-caption); font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; background: var(--ces-surface-subtle); }
    .tempo__dia { display: flex; flex-direction: column; gap: 0.125rem; align-items: center; padding: 0.375rem 0; border-bottom: 1px solid var(--ces-border); background: var(--ces-surface-subtle); font-size: var(--ces-font-control); font-weight: 600; font-variant-numeric: tabular-nums; }
    .tempo__dia small { color: var(--ces-foreground-muted); font-size: 0.625rem; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; }
    .tempo__numero { display: inline-flex; align-items: center; justify-content: center; min-width: 1.5rem; height: 1.5rem; border-radius: 999px; }
    .tempo__dia--fim { background: var(--ces-surface-muted); color: var(--ces-foreground-muted); }
    .tempo__dia--hoje .tempo__numero { background: var(--ces-brand); color: var(--ces-brand-contrast, #fff); }
    .tempo__equipe { display: flex; flex-direction: column; gap: 0.125rem; justify-content: center; min-width: 0; font-size: var(--ces-font-control); }
    .tempo__codigo { display: flex; gap: 0.375rem; align-items: center; justify-content: space-between; }
    .tempo__conflito { display: inline-flex; gap: 0.25rem; align-items: center; color: var(--ces-warning-text); font-size: 0.6875rem; font-weight: 700; white-space: nowrap; }
    .tempo__conflito .pi { font-size: 0.625rem; }
    .tempo__equipe small { overflow: hidden; color: var(--ces-foreground-muted); font-size: var(--ces-font-caption); text-overflow: ellipsis; white-space: nowrap; }
    .tempo__meta { display: flex; gap: 0.375rem; align-items: center; color: var(--ces-foreground-muted); font-size: 0.6875rem; white-space: nowrap; }
    .tempo__meta-barra { flex: 1; overflow: hidden; height: 0.25rem; border-radius: 999px; background: var(--ces-surface-muted); }
    .tempo__meta-barra span { display: block; height: 100%; border-radius: inherit; background: var(--ces-brand); }
    .tempo__meta[data-acima='true'] { color: var(--ces-warning-text); font-weight: 600; }
    .tempo__meta[data-acima='true'] .tempo__meta-barra span { background: var(--ces-warning); }
    .tempo__trilho { position: relative; display: grid; grid-column: 2 / -1; grid-template-columns: subgrid; border-bottom: 1px solid var(--ces-border); }
    .tempo__coluna { border-right: 1px solid color-mix(in srgb, var(--ces-border) 45%, transparent); }
    .tempo__coluna--fim { background: color-mix(in srgb, var(--ces-surface-muted) 55%, transparent); }
    .tempo__coluna--hoje { background: color-mix(in srgb, var(--ces-brand) 8%, transparent); }
    .tempo__livre { position: absolute; top: 50%; left: 0.75rem; display: inline-flex; gap: 0.375rem; align-items: center; color: var(--ces-foreground-subtle, var(--ces-foreground-muted)); font-size: var(--ces-font-caption); transform: translateY(-50%); pointer-events: none; }
    .tempo__livre .pi { font-size: 0.625rem; }
    .tempo__barra { position: absolute; display: flex; gap: 0.375rem; align-items: center; overflow: hidden; padding: 0 0.5rem; border: 1px solid; border-left-width: 3px; border-radius: var(--ces-radius-sm); font: inherit; text-align: left; white-space: nowrap; cursor: pointer; transition: filter var(--ces-duration-fast) var(--ces-ease-standard); }
    [data-modo='mes'] .tempo__barra { padding: 0 0.375rem; }
    .tempo__barra:hover { filter: brightness(1.08); }
    .tempo__barra--arrastavel { cursor: grab; }
    .tempo__barra--origem { opacity: 0.35; }
    .tempo__ponta { position: absolute; top: 0; bottom: 0; z-index: 1; width: 0.5rem; cursor: ew-resize; }
    .tempo__ponta--inicio { left: 0; }
    .tempo__ponta--fim { right: 0; }
    .tempo__ponta--fim::after { content: ''; position: absolute; top: 30%; right: 2px; bottom: 30%; width: 2px; border-radius: 1px; background: currentColor; opacity: 0; }
    .tempo__barra--arrastavel:hover .tempo__ponta--fim::after { opacity: 0.6; }
    .tempo__trilho[data-alvo='true'] { background: color-mix(in srgb, var(--ces-brand) 6%, transparent); }
    .tempo__fantasma { position: absolute; top: 3px; bottom: 3px; z-index: 3; display: flex; align-items: center; justify-content: center; overflow: hidden; border: 2px dashed var(--ces-brand); border-radius: var(--ces-radius-sm); background: var(--ces-brand-soft, var(--ces-surface-muted)); color: var(--ces-foreground); font-size: 0.6875rem; font-weight: 700; white-space: nowrap; pointer-events: none; box-shadow: var(--ces-shadow-sm); }
    .tempo__barra:focus-visible { z-index: 1; outline: 2px solid var(--ces-focus-ring); outline-offset: 1px; }
    .tempo__barra .pi { flex: none; font-size: 0.75rem; }
    .tempo__texto { display: flex; flex-direction: column; min-width: 0; line-height: 1.2; }
    .tempo__texto strong { overflow: hidden; font-size: 0.75rem; font-variant-numeric: tabular-nums; text-overflow: ellipsis; }
    .tempo__texto small { overflow: hidden; font-size: 0.6875rem; text-overflow: ellipsis; }
    .tempo__barra--corte-inicio { border-left-style: dashed; }
    .tempo__barra--corte-fim { border-right-style: dashed; }
    .tempo__barra[data-situacao='PROGRAMADA'] { border-color: var(--ces-info); background: var(--ces-info-soft); color: var(--ces-info-text); }
    .tempo__barra[data-situacao='ATRASADA'] { border-color: var(--ces-danger); background: var(--ces-danger-soft); color: var(--ces-danger-text); }
    .tempo__barra[data-situacao='EM_ANDAMENTO'] { border-color: var(--ces-warning); background: var(--ces-warning-soft); color: var(--ces-warning-text); }
    .tempo__barra[data-situacao='CONCLUIDA'] { border-color: var(--ces-success); background: var(--ces-success-soft); color: var(--ces-success-text); }
    .tempo__barra[data-situacao='CANCELADA'] { border-color: var(--ces-border); border-style: dashed; background: var(--ces-surface-muted); color: var(--ces-foreground-muted); }
    .tempo__barra[data-situacao='CANCELADA'] strong { text-decoration: line-through; }
  `]
})
export class ProgramacaoLinhaTempoComponent {
  readonly linhas = input.required<readonly LinhaGantt[]>();
  readonly inicio = input.required<Date>();
  readonly fim = input.required<Date>();
  readonly modo = input.required<'semana' | 'mes'>();
  readonly podeEditar = input(false);
  readonly abrir = output<string>();
  readonly arrastada = output<ArrasteProgramacao>();

  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly arraste = signal<Arraste | null>(null);
  /** O navegador dispara um clique depois de soltar a barra: esse não abre o detalhe. */
  private ignorarClique = false;
  private soltar: (() => void) | null = null;

  protected readonly situacoes = SITUACAO_PROGRAMACAO;
  protected readonly moeda = formatarMoeda;
  protected readonly numero = formatarNumero;
  protected readonly alturaFaixa = computed(() => ALTURA_BARRA[this.modo()]);
  protected readonly dias = computed(() => diasDoPeriodo(this.inicio(), this.fim()));
  protected readonly grade = computed(() => this.linhas().map((linha) => {
    const barras = posicionarBarras(linha.programacoes, this.inicio(), this.fim());
    const faixas = barras.reduce((maior, b) => Math.max(maior, b.faixa + 1), 1);
    // A barra ocupa a altura real da linha, que cresce com os dados da equipe; com faixas, divide entre elas.
    return { ...linha, barras, faixas, conflito: temConflito(linha.programacoes), altura: barras.length ? Math.max(faixas * this.alturaFaixa() + FOLGA_LINHA_REM * 2, 3.75) : 2.75 };
  }));

  /** Onde a barra cairia se fosse solta agora, já recortado no período exibido. */
  protected readonly previa = computed(() => {
    const a = this.arraste();
    if (!a?.ativo) return null;
    const { inicio, fim } = datasArrastadas(parseDataBrParaDate(a.barra.inicio)!, parseDataBrParaDate(a.barra.fim)!, a.modo, a.dias);
    const textoInicio = formatarDataParaApi(inicio)!;
    const textoFim = formatarDataParaApi(fim)!;
    const [pos] = posicionarBarras([{ inicio: textoInicio, fim: textoFim }], this.inicio(), this.fim());
    return { id: a.barra.id, idEquipe: a.idEquipe, inicio: textoInicio, fim: textoFim, coluna: pos?.coluna ?? 0, largura: pos?.largura ?? 1 };
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.soltar?.());
  }

  protected podeArrastar(barra: BarraGantt): boolean {
    return this.podeEditar() && barra.situacao !== 'CANCELADA' && barra.situacao !== 'CONCLUIDA';
  }

  protected iniciar(evento: PointerEvent, barra: BarraGantt, idEquipe: string): void {
    // No toque, arrastar é rolar a tela: a reprogramação fica no detalhe.
    if (!this.podeArrastar(barra) || evento.button !== 0 || evento.pointerType === 'touch') return;
    const trilho = (evento.currentTarget as HTMLElement).closest<HTMLElement>('.tempo__trilho');
    if (!trilho) return;
    const ponta = (evento.target as HTMLElement).dataset['ponta'] as 'inicio' | 'fim' | undefined;
    this.arraste.set({
      barra, idEquipeOrigem: idEquipe, modo: ponta ?? 'mover', xInicial: evento.clientX, yInicial: evento.clientY,
      larguraDia: trilho.getBoundingClientRect().width / this.dias().length, ativo: false, dias: 0, idEquipe
    });
    const mover = (e: PointerEvent) => this.mover(e);
    const terminar = () => this.terminar();
    const cancelar = (e: KeyboardEvent) => { if (e.key === 'Escape') this.cancelar(); };
    document.addEventListener('pointermove', mover);
    document.addEventListener('pointerup', terminar);
    document.addEventListener('keydown', cancelar);
    this.soltar = () => {
      document.removeEventListener('pointermove', mover);
      document.removeEventListener('pointerup', terminar);
      document.removeEventListener('keydown', cancelar);
      this.soltar = null;
    };
  }

  private mover(evento: PointerEvent): void {
    const a = this.arraste();
    if (!a) return;
    const dx = evento.clientX - a.xInicial;
    const dy = evento.clientY - a.yInicial;
    if (!a.ativo && Math.hypot(dx, dy) < LIMIAR_ARRASTE_PX) return;
    evento.preventDefault();
    let idEquipe = a.idEquipe;
    if (a.modo === 'mover') {
      const trilhos = (this.host.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.tempo__trilho');
      for (const trilho of Array.from(trilhos)) {
        const caixa = trilho.getBoundingClientRect();
        if (evento.clientY >= caixa.top && evento.clientY < caixa.bottom) idEquipe = trilho.dataset['equipe']!;
      }
    }
    this.arraste.set({ ...a, ativo: true, dias: Math.round(dx / a.larguraDia), idEquipe });
  }

  private terminar(): void {
    const a = this.arraste();
    const previa = this.previa();
    this.cancelar();
    if (!a?.ativo || !previa) return;
    this.ignorarClique = true;
    setTimeout(() => (this.ignorarClique = false));
    if (previa.idEquipe === a.idEquipeOrigem && previa.inicio === a.barra.inicio && previa.fim === a.barra.fim) return;
    this.arrastada.emit({ barra: a.barra, idEquipe: previa.idEquipe, idEquipeOrigem: a.idEquipeOrigem, inicio: previa.inicio, fim: previa.fim });
  }

  private cancelar(): void {
    this.soltar?.();
    this.arraste.set(null);
  }

  protected clicar(evento: Event, id: string): void {
    if (this.ignorarClique) {
      evento.preventDefault();
      return;
    }
    this.abrir.emit(id);
  }

  protected largura(percentual: number): number {
    return Math.min(100, Math.max(0, percentual));
  }

  protected descrever(b: BarraGantt): string {
    return `Programação ${b.numero}, obra ${b.numeroObra}, ${b.tituloObra}. ${SITUACAO_PROGRAMACAO[b.situacao].rotulo}, de ${b.inicio} a ${b.fim}, ${formatarMoeda(b.valor)}, ${b.itens} ${b.itens === 1 ? 'item' : 'itens'}.`;
  }
}
