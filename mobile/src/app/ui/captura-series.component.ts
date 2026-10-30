import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, input, model, signal, untracked, viewChild } from '@angular/core';
import { acumular, aprenderPadrao, contarSeries, extrairSerie, foraDoPadrao, gerarSequencia, normalizarSerie, ResultadoAdicao, separarLote } from '../core/almox/series';
import { FolhaComponent } from './folha.component';
import { IconeComponent, NomeIcone } from './icone.component';
import { LeitorCodigoComponent, RetornoLeitura, TomRetorno, leitorPorCameraDisponivel } from './leitor-codigo.component';

type Modo = 'ler' | 'digitar' | 'colar' | 'sequencia' | 'escolher';
/** De onde veio a série: leitura e digitação passam pela conferência de padrão; lote e sequência são deliberados. */
type Origem = 'leitura' | 'digitada' | 'lote';

interface DefModo { valor: Modo; rotulo: string; icone: NomeIcone }
interface Conferir { serie: string; motivo: string; tipo: 'suspeita' | 'recusada' }

/**
 * Confere séries no sistema (ex.: a entrada recusa série que já está ativa em algum estoque).
 * Devolve série → motivo; falha ou sem rede resolve vazio e o servidor confere no envio.
 */
export type VerificadorSeries = (series: string[]) => Promise<Map<string, string>>;

/** Teclas de um leitor USB/Bluetooth chegam com menos que isso entre si; digitação humana não. */
const INTERVALO_TECLADO_LEITOR_MS = 60;
const ESPERA_VERIFICAR_MS = 450;
const MAX_CONFERIR = 30;

/**
 * Captura de números de série em massa, para um material:
 * - **Ler**: câmera em leitura contínua — cada código lido entra na lista (bipe + vibração);
 * - **Digitar**: um por vez com Enter ou Tab (leitores USB/Bluetooth digitam e confirmam sozinhos);
 * - **Colar**: lista vinda de planilha ou e-mail (linha, vírgula ou ponto e vírgula);
 * - **Sequência**: da primeira à última série (ABC0001 → ABC0100);
 * - **Escolher**: marcar entre as séries do saldo (saída e transferência).
 *
 * Robustez: tira a série de etiquetas GS1/QR, segura para conferência a leitura que destoa das
 * outras (ex.: o código do produto lido no lugar da série), aceita leitor por teclado em qualquer
 * modo, desfaz a última adição e, com `verificar`, tira da lista as séries que já estão no sistema.
 */
@Component({
  selector: 'ces-captura-series',
  standalone: true,
  imports: [NgTemplateOutlet, FolhaComponent, IconeComponent, LeitorCodigoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'teclaGlobal($event)' },
  template: `
    <ces-folha [aberta]="aberta()" (fechar)="aberta.set(false)" [titulo]="titulo()" [descricao]="descricao()">
      <div class="modos" role="tablist" aria-label="Como adicionar as séries">
        @for (m of modos(); track m.valor) {
          <button type="button" role="tab" class="modo" [attr.aria-selected]="modo() === m.valor" (click)="trocarModo(m.valor)">
            <ces-icone [nome]="m.icone" [tamanho]="18" />{{ m.rotulo }}
          </button>
        }
      </div>

      <section class="area" role="tabpanel">
        @switch (modo()) {
          @case ('ler') {
            @if (temCamera) {
              <ces-leitor-codigo [ativo]="aberta() && modo() === 'ler'" [pausado]="completo()" motivoPausa="Quantidade completa" [retorno]="retorno()"
                [contador]="contador()" (lido)="adicionar([$event], 'leitura')" />
              <p class="dica">Centralize um código por vez na linha vermelha: cada bip entra na lista. Em "Gatilho", só lê com o botão apertado. Leitor conectado também funciona aqui.</p>
            } @else {
              <div class="sem-camera">
                <ces-icone nome="info" />
                <p>Este navegador não lê códigos pela câmera. Use um <strong>leitor de código</strong> conectado ao aparelho — ele digita aqui e confirma sozinho.</p>
              </div>
              <ng-container [ngTemplateOutlet]="digitar" />
            }
          }
          @case ('digitar') { <ng-container [ngTemplateOutlet]="digitar" /> }
          @case ('colar') {
            <label class="ces-campo">
              <span class="ces-campo__rotulo">Cole as séries (uma por linha, ou separadas por vírgula)</span>
              <textarea class="ces-input texto" rows="5" [value]="textoLote()" (input)="textoLote.set($any($event.target).value)"
                autocapitalize="characters" autocomplete="off" spellcheck="false" placeholder="ABC0001&#10;ABC0002&#10;ABC0003"></textarea>
            </label>
            <button type="button" class="ces-btn ces-btn--tonal ces-btn--bloco" [disabled]="!lote().length" (click)="adicionarLote()">
              <ces-icone nome="mais" [tamanho]="18" />{{ lote().length ? 'Adicionar ' + contar(lote().length) : 'Adicionar' }}
            </button>
          }
          @case ('sequencia') {
            <div class="duas">
              <label class="ces-campo"><span class="ces-campo__rotulo">Primeira série</span>
                <input class="ces-input mono" [value]="seqInicio()" (input)="seqInicio.set($any($event.target).value)" autocapitalize="characters" autocomplete="off" spellcheck="false" placeholder="ABC0001"></label>
              <label class="ces-campo"><span class="ces-campo__rotulo">Última série</span>
                <input class="ces-input mono" [value]="seqFim()" (input)="seqFim.set($any($event.target).value)" autocapitalize="characters" autocomplete="off" spellcheck="false" placeholder="ABC0100"></label>
            </div>
            @if (seqInicio() && seqFim()) {
              @if (sequencia().erro; as e) { <p class="aviso aviso--erro"><ces-icone nome="alerta" [tamanho]="18" />{{ e }}</p> }
              @else { <p class="aviso"><ces-icone nome="sequencia" [tamanho]="18" />Gera {{ contar(sequencia().series.length) }}: {{ sequencia().series[0] }} … {{ sequencia().series[sequencia().series.length - 1] }}</p> }
            }
            <button type="button" class="ces-btn ces-btn--tonal ces-btn--bloco" [disabled]="!sequencia().series.length" (click)="adicionar(sequencia().series, 'lote')">
              <ces-icone nome="mais" [tamanho]="18" />{{ sequencia().series.length ? 'Adicionar ' + contar(sequencia().series.length) : 'Adicionar sequência' }}
            </button>
          }
          @case ('escolher') {
            <div class="busca">
              <ces-icone nome="busca" [tamanho]="18" />
              <input type="search" placeholder="Buscar série" enterkeyhint="search" aria-label="Buscar série no estoque" [value]="buscaDisponivel()" (input)="buscaDisponivel.set($any($event.target).value)">
            </div>
            @if (faltam() > 0 && livres().length) {
              <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="adicionar(livres().slice(0, faltam()), 'lote')">
                Marcar {{ contar(Math.min(faltam(), livres().length)) }} {{ faltam() < livres().length ? 'primeiras' : '' }}
              </button>
            }
            <ul class="ces-grupo escolher" aria-label="Séries no estoque">
              @for (s of disponiveisFiltradas(); track s) {
                <li><button type="button" role="checkbox" class="ces-linha ces-linha--acao" [attr.aria-checked]="selecionada(s)" (click)="alternar(s)"
                  [disabled]="!selecionada(s) && completo()">
                  <span class="caixa" aria-hidden="true"><ces-icone nome="check" [tamanho]="14" /></span>
                  <span class="ces-linha__texto"><span class="ces-linha__titulo mono">{{ s }}</span></span>
                </button></li>
              } @empty {
                <li><div class="ces-linha"><span class="ces-linha__apoio">{{ buscaDisponivel() ? 'Nenhuma série com esse trecho' : 'Nenhuma série neste estoque' }}</span></div></li>
              }
            </ul>
          }
        }
        @if (ultimo(); as u) {
          <div class="resultado" [attr.data-tom]="u.tom" aria-live="polite">
            <span>{{ u.texto }}</span>
            @if (ultimaAdicao().length) {
              <button type="button" class="desfazer" (click)="desfazer()" [attr.aria-label]="'Desfazer: tirar ' + contar(ultimaAdicao().length) + ' da lista'">
                <ces-icone nome="desfazer" [tamanho]="16" />Desfazer</button>
            }
          </div>
        }
        @if (verificando()) {
          <p class="verificando" role="status"><span class="giro" aria-hidden="true"></span>Conferindo {{ contar(verificando()) }} no sistema…</p>
        }
      </section>

      <ng-template #digitar>
        <form class="digitar" (submit)="$event.preventDefault(); adicionarDigitada()">
          <label class="ces-sr-only" for="serie-digitada">Número de série</label>
          <input #campo id="serie-digitada" class="ces-input mono" [value]="digitada()" (input)="digitada.set($any($event.target).value)"
            (keydown.tab)="tabNoCampo($event)" (paste)="colarNoCampo($event)"
            autocapitalize="characters" autocomplete="off" spellcheck="false" enterkeyhint="send" placeholder="Número de série" [disabled]="completo()">
          <button type="submit" class="ces-btn ces-btn--principal" [disabled]="!digitada().trim() || completo()" aria-label="Adicionar série"><ces-icone nome="mais" /></button>
        </form>
        <p class="dica">Confirme com Enter: o campo fica pronto para a próxima. Colar várias linhas adiciona todas.</p>
      </ng-template>

      @if (conferir().length) {
        <section class="conferir" aria-labelledby="conferir-titulo">
          <h3 id="conferir-titulo" class="ces-secao topo-lista">
            <span>Para conferir</span>
            <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="conferir.set([])">Limpar</button>
          </h3>
          <ul class="ces-grupo">
            @for (c of conferir(); track c.serie) {
              <li class="ces-linha conferir__item" [attr.data-tipo]="c.tipo">
                <ces-icone [nome]="c.tipo === 'suspeita' ? 'alerta' : 'fechar'" [tamanho]="18" />
                <span class="ces-linha__texto"><span class="ces-linha__titulo mono">{{ c.serie }}</span><span class="ces-linha__apoio">{{ c.motivo }}</span></span>
                @if (c.tipo === 'suspeita') {
                  <span class="conferir__acoes">
                    <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="ignorar(c.serie)" [attr.aria-label]="'Ignorar ' + c.serie + ' nas próximas leituras'">Ignorar</button>
                    <button type="button" class="ces-btn ces-btn--tonal ces-btn--compacto" (click)="aceitarSuspeita(c.serie)" [attr.aria-label]="'Adicionar ' + c.serie + ' mesmo assim'">Adicionar</button>
                  </span>
                }
              </li>
            }
          </ul>
        </section>
      }

      <section class="lista" aria-label="Séries adicionadas">
        <h3 class="ces-secao topo-lista">
          <span>Adicionadas</span>
          <span class="ces-num conta" [class.completo]="completo()">{{ series().length }}@if (limite() != null) { de {{ limite() }} }</span>
        </h3>
        @if (series().length) {
          <ul class="chips">
            @for (s of seriesRecentes(); track s) {
              <li class="chip" [class.nova]="ultimaAdicao().includes(s)"><span class="mono">{{ s }}</span>
                <button type="button" class="tirar" (click)="remover(s)" [attr.aria-label]="'Tirar a série ' + s"><ces-icone nome="fechar" [tamanho]="16" /></button></li>
            }
          </ul>
          <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto limpar" (click)="confirmandoLimpar() ? limpar() : confirmandoLimpar.set(true)">
            <ces-icone nome="lixeira" [tamanho]="16" />{{ confirmandoLimpar() ? 'Toque de novo para tirar todas' : 'Tirar todas' }}
          </button>
        } @else {
          <p class="vazio">Nenhuma série ainda.</p>
        }
      </section>

      <div rodape class="ces-folha-rodape">
        <button type="button" class="ces-btn ces-btn--principal" (click)="aberta.set(false)">
          {{ limite() != null && !completo() && series().length ? 'Pronto · faltam ' + faltam() : 'Pronto' }}
        </button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: contents; }
    .modos { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; gap: 2px; margin: 0 var(--ces-e4) var(--ces-e3); padding: 3px;
      border-radius: var(--ces-r-grupo); background: var(--ces-superficie-2); }
    .modo { display: grid; justify-items: center; align-content: center; gap: 3px; min-height: 52px; padding: 4px 2px; border: 0; border-radius: 11px;
      background: transparent; color: var(--ces-texto-2); font: 500 .6875rem/1.1 var(--ces-fonte); cursor: pointer; white-space: nowrap;
      transition: background-color var(--ces-m-entrar), color var(--ces-m-entrar); }
    .modo[aria-selected='true'] { background: var(--ces-superficie); color: var(--ces-destaque); font-weight: 600; box-shadow: 0 1px 2px rgba(0, 0, 0, .1); }
    .area { display: grid; gap: var(--ces-e3); padding: 0 var(--ces-e4); }
    .dica { margin: 0; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .sem-camera { display: flex; gap: var(--ces-e3); align-items: flex-start; padding: var(--ces-e3) var(--ces-e4); border-radius: var(--ces-r-grupo);
      background: var(--ces-superficie-2); color: var(--ces-texto-2); }
    .sem-camera p { margin: 0; font: var(--ces-t-apoio); font-size: .875rem; color: var(--ces-texto); }
    .digitar { display: flex; gap: var(--ces-e2); }
    .digitar .ces-input { flex: 1; min-width: 0; }
    .digitar .ces-btn { width: 54px; padding: 0; flex: none; }
    .mono { font-family: var(--ces-fonte-mono); letter-spacing: .02em; }
    .texto { min-height: 132px; padding: var(--ces-e3) var(--ces-e4); font-family: var(--ces-fonte-mono); resize: vertical; }
    .duas { display: grid; grid-template-columns: 1fr 1fr; gap: var(--ces-e2); }
    .aviso { display: flex; gap: var(--ces-e2); align-items: center; margin: 0; padding: var(--ces-e2) var(--ces-e3); border-radius: var(--ces-r-campo);
      background: var(--ces-superficie-2); font: var(--ces-t-apoio); color: var(--ces-texto); overflow-wrap: anywhere; }
    .aviso--erro { background: var(--ces-erro-suave); color: var(--ces-erro); }
    .busca { display: flex; align-items: center; gap: var(--ces-e2); padding: 0 var(--ces-e4); min-height: 48px; border-radius: var(--ces-r-pilula);
      background: var(--ces-superficie-2); color: var(--ces-texto-2); }
    .busca input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ces-texto); font: var(--ces-t-corpo); font-size: 1rem; }
    .busca input:focus { outline: none; }
    .escolher { margin: 0; max-height: 34dvh; overflow-y: auto; background: var(--ces-fundo); }
    .escolher .ces-linha { min-height: 52px; }
    .caixa { width: 22px; height: 22px; border-radius: 6px; border: 2px solid var(--ces-linha-forte); display: grid; place-items: center; color: transparent; flex: none; }
    [aria-checked='true'] > .caixa { background: var(--ces-destaque); border-color: var(--ces-destaque); color: var(--ces-destaque-texto); }
    .resultado { display: flex; align-items: center; gap: var(--ces-e2); margin: 0; padding: var(--ces-e2) var(--ces-e3); border-radius: var(--ces-r-campo);
      font: 600 .8125rem/1.35 var(--ces-fonte); background: var(--ces-superficie-2); overflow-wrap: anywhere; }
    .resultado > span { flex: 1; min-width: 0; }
    .resultado[data-tom='ok'] { background: var(--ces-ok-suave); color: var(--ces-ok); }
    .resultado[data-tom='atencao'] { background: var(--ces-atencao-suave); color: var(--ces-atencao); }
    .resultado[data-tom='erro'] { background: var(--ces-erro-suave); color: var(--ces-erro); }
    .desfazer { flex: none; display: inline-flex; align-items: center; gap: 4px; min-height: 40px; padding: 0 12px; border: 0; border-radius: var(--ces-r-pilula);
      background: var(--ces-superficie); color: var(--ces-texto); font: 600 .8125rem/1 var(--ces-fonte); cursor: pointer; }
    .desfazer:active { transform: scale(.97); }
    .verificando { display: flex; align-items: center; gap: var(--ces-e2); margin: 0; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .giro { width: 14px; height: 14px; border-radius: 50%; border: 2px solid currentColor; border-right-color: transparent; animation: giro .8s linear infinite; }
    @keyframes giro { to { transform: rotate(360deg); } }
    .conferir .ces-secao button { margin-left: auto; }
    .conferir__item { gap: var(--ces-e3); }
    .conferir__item[data-tipo='suspeita'] > ces-icone { color: var(--ces-atencao); }
    .conferir__item[data-tipo='recusada'] > ces-icone { color: var(--ces-erro); }
    .conferir__acoes { display: flex; gap: 4px; flex: none; }
    .lista { padding-bottom: var(--ces-e2); }
    .topo-lista { padding-top: var(--ces-e4); }
    .conta { font-weight: 700; color: var(--ces-texto); }
    .conta.completo { color: var(--ces-ok); }
    .chips { list-style: none; margin: 0; padding: 0 var(--ces-e4); display: flex; flex-wrap: wrap; gap: 6px; }
    .chip { display: inline-flex; align-items: center; gap: 2px; padding-left: 12px; min-height: 36px; border-radius: var(--ces-r-pilula);
      background: var(--ces-superficie-2); font: 600 .8125rem/1 var(--ces-fonte); animation: entrar 160ms var(--ces-curva); }
    .chip.nova { background: var(--ces-ok-suave); }
    @keyframes entrar { from { opacity: 0; transform: scale(.94); } }
    .tirar { width: 36px; height: 36px; border: 0; border-radius: 50%; background: transparent; color: var(--ces-texto-2); display: grid; place-items: center; cursor: pointer; }
    .tirar:active { background: var(--ces-linha); }
    .limpar { margin: var(--ces-e2) var(--ces-e3) 0; color: var(--ces-erro); gap: 4px; }
    .vazio { margin: 0; padding: 0 var(--ces-e5); font: var(--ces-t-apoio); color: var(--ces-texto-3); }
    .ces-folha-rodape .ces-btn { flex: 1; }
    @media (prefers-reduced-motion: reduce) { .chip, .giro { animation: none; } }
  `],
})
export class CapturaSeriesComponent {
  protected readonly Math = Math;
  readonly aberta = model(false);
  readonly titulo = input('Números de série');
  /** Séries do saldo da origem. Com elas, só entram séries que estão no estoque e o modo "Escolher" aparece. */
  readonly disponiveis = input<readonly string[] | null>(null);
  /** Quantidade esperada (null = livre: a quantidade é o total de séries). */
  readonly limite = input<number | null>(null);
  readonly nomeEstoque = input('estoque de origem');
  /** Conferência no sistema (ex.: série já cadastrada, na entrada). */
  readonly verificar = input<VerificadorSeries | null>(null);
  readonly series = model<string[]>([]);

  readonly temCamera = leitorPorCameraDisponivel();
  readonly modo = signal<Modo>(this.temCamera ? 'ler' : 'digitar');
  readonly digitada = signal('');
  readonly textoLote = signal('');
  readonly seqInicio = signal('');
  readonly seqFim = signal('');
  readonly buscaDisponivel = signal('');
  readonly confirmandoLimpar = signal(false);
  readonly retorno = signal<RetornoLeitura | null>(null);
  readonly ultimo = signal<{ texto: string; tom: TomRetorno } | null>(null);
  /** Séries da última adição, para desfazer de uma vez. */
  readonly ultimaAdicao = signal<string[]>([]);
  readonly conferir = signal<Conferir[]>([]);
  readonly verificando = signal(0);
  /** Códigos que a pessoa mandou ignorar (ex.: o EAN do produto, que está em toda etiqueta). */
  private readonly ignoradas = new Set<string>();
  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');
  private readonly filaVerificar = new Set<string>();
  private timerVerificar: ReturnType<typeof setTimeout> | null = null;
  /** Muda a cada abertura: resposta de conferência atrasada não mexe na lista de outro material. */
  private sessao = 0;
  private teclado = { texto: '', em: 0 };

  readonly modos = computed<DefModo[]>(() => {
    const m: DefModo[] = [
      { valor: 'ler', rotulo: 'Ler', icone: 'codigo_barras' },
      { valor: 'digitar', rotulo: 'Digitar', icone: 'teclado' },
      { valor: 'colar', rotulo: 'Colar', icone: 'colar' },
      { valor: 'sequencia', rotulo: 'Sequência', icone: 'sequencia' },
    ];
    if (this.disponiveis()) m.push({ valor: 'escolher', rotulo: 'Do estoque', icone: 'lista' });
    return m;
  });

  readonly lote = computed(() => [...new Set(separarLote(this.textoLote()))]);
  readonly sequencia = computed(() => gerarSequencia(this.seqInicio(), this.seqFim()));
  readonly completo = computed(() => this.limite() != null && this.series().length >= this.limite()!);
  readonly faltam = computed(() => Math.max(0, (this.limite() ?? 0) - this.series().length));
  readonly seriesRecentes = computed(() => [...this.series()].reverse());
  readonly contador = computed(() => (this.limite() != null ? `${this.series().length} de ${this.limite()}` : `${this.series().length}`));
  private readonly conjuntoDisponivel = computed(() => (this.disponiveis() ? new Set(this.disponiveis()) : null));
  readonly livres = computed(() => (this.disponiveis() ?? []).filter(s => !this.series().includes(s)));
  readonly disponiveisFiltradas = computed(() => {
    const q = normalizarSerie(this.buscaDisponivel());
    const lista = this.disponiveis() ?? [];
    return q ? lista.filter(s => s.includes(q)) : lista;
  });
  readonly descricao = computed(() => {
    const n = this.series().length;
    if (this.limite() != null) return this.completo() ? `Todas as ${this.limite()} séries informadas.` : `${n} de ${this.limite()} · faltam ${this.faltam()}.`;
    return n ? `${contarSeries(n)} — a quantidade é o total de séries.` : 'Cada série é uma unidade do material.';
  });

  constructor() {
    effect(() => {
      const aberta = this.aberta();
      untracked(() => {
        this.sessao++;
        this.filaVerificar.clear();
        if (this.timerVerificar) clearTimeout(this.timerVerificar);
        this.verificando.set(0);
        if (!aberta) return;
        this.ultimo.set(null);
        this.ultimaAdicao.set([]);
        this.conferir.set([]);
        this.confirmandoLimpar.set(false);
        this.focarCampo();
        // Reaberta (ou aberta pelo envio): confere de novo tudo o que já está na lista.
        if (this.series().length) this.agendarVerificacao(this.series());
      });
    });
  }

  contar(n: number): string {
    return contarSeries(n);
  }

  trocarModo(m: Modo): void {
    this.modo.set(m);
    this.ultimo.set(null);
    this.focarCampo();
  }

  selecionada(s: string): boolean {
    return this.series().includes(s);
  }

  alternar(s: string): void {
    if (this.selecionada(s)) this.remover(s);
    else this.adicionar([s], 'lote');
  }

  adicionarDigitada(): void {
    const s = this.digitada();
    if (!s.trim()) return;
    const r = this.adicionar([s], 'digitada');
    // Mantém o texto quando recusada: a pessoa corrige sem redigitar. Suspeita vai para a conferência.
    if (r.novas.length || r.repetidas.length || this.conferir().some(c => c.serie === extrairSerie(s))) this.digitada.set('');
    this.focarCampo();
  }

  /** Leitor configurado para mandar Tab no fim: confirma como o Enter, sem pular de campo. */
  tabNoCampo(evento: Event): void {
    if (!this.digitada().trim()) return;
    evento.preventDefault();
    this.adicionarDigitada();
  }

  /** Colar várias linhas no campo de digitar adiciona todas de uma vez. */
  colarNoCampo(evento: ClipboardEvent): void {
    const partes = separarLote(evento.clipboardData?.getData('text') ?? '');
    if (partes.length < 2) return;
    evento.preventDefault();
    this.adicionar(partes, 'lote');
    this.digitada.set('');
  }

  adicionarLote(): void {
    const r = this.adicionar(this.lote(), 'lote');
    if (r.novas.length) this.textoLote.set(r.recusadas.map(x => x.serie).join('\n'));
  }

  adicionar(entrada: readonly string[], origem: Origem = 'lote'): ResultadoAdicao {
    const conferida = origem !== 'lote';
    const sobreCamera = origem === 'leitura';
    let candidatas = conferida ? entrada.map(extrairSerie).filter(Boolean) : [...entrada];
    const nada: ResultadoAdicao = { series: this.series(), novas: [], repetidas: [], recusadas: [] };

    if (conferida && candidatas.length === 1 && this.ignoradas.has(candidatas[0])) {
      this.avisar({ texto: `${candidatas[0]} ignorada`, tom: 'neutro' }, sobreCamera);
      return nada;
    }
    // Leitura que destoa das anteriores fica para conferir em vez de entrar calada na lista.
    if (conferida) {
      const padrao = aprenderPadrao(this.series());
      const suspeitas = candidatas
        .filter(s => !this.series().includes(s))
        .map(s => ({ serie: s, motivo: foraDoPadrao(s, padrao) }))
        .filter((x): x is { serie: string; motivo: string } => !!x.motivo);
      if (suspeitas.length) {
        for (const x of suspeitas) this.anotar({ serie: x.serie, motivo: `Parece diferente: ${x.motivo}.`, tipo: 'suspeita' });
        const fora = new Set(suspeitas.map(x => x.serie));
        candidatas = candidatas.filter(s => !fora.has(s));
        if (!candidatas.length) {
          this.avisar({ texto: `${suspeitas[0].serie} parece diferente — confira abaixo`, tom: 'atencao' }, sobreCamera);
          return nada;
        }
      }
    }
    return this.aplicar(candidatas, sobreCamera);
  }

  private aplicar(entrada: readonly string[], sobreCamera: boolean): ResultadoAdicao {
    const disponivel = this.conjuntoDisponivel();
    const r = acumular(this.series(), entrada, {
      limite: this.limite(),
      validar: disponivel ? s => (disponivel.has(s) ? null : `não está no ${this.nomeEstoque()}`) : undefined,
    });
    if (r.novas.length) {
      this.series.set(r.series);
      this.ultimaAdicao.set(r.novas);
      this.conferir.update(l => l.filter(c => !r.novas.includes(c.serie)));
      this.agendarVerificacao(r.novas);
    }
    for (const x of r.recusadas) {
      if (x.motivo !== 'passa da quantidade') this.anotar({ serie: x.serie, motivo: primeiraMaiuscula(x.motivo) + '.', tipo: 'recusada' });
    }
    this.confirmandoLimpar.set(false);
    this.ultimo.set(resumo(r));
    if (sobreCamera) {
      const unica = r.novas[0] ?? r.repetidas[0] ?? r.recusadas[0]?.serie ?? '';
      this.retorno.set({
        texto: r.novas.length ? `${unica} adicionada` : r.repetidas.length ? `${unica} já está na lista` : `${unica}: ${r.recusadas[0]?.motivo}`,
        tom: r.novas.length ? 'ok' : r.repetidas.length ? 'atencao' : 'erro',
      });
    }
    return r;
  }

  aceitarSuspeita(serie: string): void {
    this.conferir.update(l => l.filter(c => c.serie !== serie));
    this.aplicar([serie], false);
  }

  ignorar(serie: string): void {
    this.ignoradas.add(serie);
    this.conferir.update(l => l.filter(c => c.serie !== serie));
    this.ultimaAdicao.set([]);
    this.ultimo.set({ texto: `${serie} vai ser ignorada nas próximas leituras.`, tom: 'neutro' });
  }

  desfazer(): void {
    const tirar = new Set(this.ultimaAdicao());
    if (!tirar.size) return;
    this.series.set(this.series().filter(s => !tirar.has(s)));
    this.ultimaAdicao.set([]);
    this.ultimo.set({ texto: `${contarSeries(tirar.size)} ${tirar.size === 1 ? 'saiu' : 'saíram'} da lista.`, tom: 'neutro' });
    this.focarCampo();
  }

  remover(s: string): void {
    this.series.set(this.series().filter(x => x !== s));
    this.ultimaAdicao.set([]);
    this.ultimo.set({ texto: `${s} saiu da lista.`, tom: 'neutro' });
  }

  limpar(): void {
    const n = this.series().length;
    this.series.set([]);
    this.ultimaAdicao.set([]);
    this.confirmandoLimpar.set(false);
    this.ultimo.set({ texto: `${contarSeries(n)} ${n === 1 ? 'saiu' : 'saíram'} da lista.`, tom: 'neutro' });
  }

  /**
   * Leitor USB/Bluetooth em qualquer modo: ele "digita" muito rápido e termina com Enter ou Tab.
   * Fora de um campo de texto, junta as teclas e trata como uma leitura.
   */
  teclaGlobal(e: KeyboardEvent): void {
    if (!this.aberta() || e.ctrlKey || e.altKey || e.metaKey) return;
    const alvo = e.target as HTMLElement | null;
    if (alvo && (alvo.tagName === 'INPUT' || alvo.tagName === 'TEXTAREA' || alvo.isContentEditable)) return;
    const agora = performance.now();
    if (agora - this.teclado.em > INTERVALO_TECLADO_LEITOR_MS * 3) this.teclado.texto = '';
    this.teclado.em = agora;
    if (e.key === 'Enter' || e.key === 'Tab') {
      const lido = this.teclado.texto;
      this.teclado.texto = '';
      if (lido.length >= 3) {
        e.preventDefault();
        this.adicionar([lido], 'leitura');
      }
      return;
    }
    if (e.key.length === 1) this.teclado.texto += e.key;
  }

  private agendarVerificacao(series: readonly string[]): void {
    if (!this.verificar() || !series.length) return;
    for (const s of series) this.filaVerificar.add(s);
    this.verificando.set(this.filaVerificar.size);
    if (this.timerVerificar) clearTimeout(this.timerVerificar);
    this.timerVerificar = setTimeout(() => void this.verificarFila(), ESPERA_VERIFICAR_MS);
  }

  private async verificarFila(): Promise<void> {
    const verificar = this.verificar();
    const lote = [...this.filaVerificar];
    this.filaVerificar.clear();
    if (!verificar || !lote.length) { this.verificando.set(0); return; }
    const sessao = this.sessao;
    let encontradas = new Map<string, string>();
    try {
      encontradas = await verificar(lote);
    } catch {
      /* sem rede: o servidor confere no envio */
    }
    if (sessao !== this.sessao) return;
    this.verificando.set(this.filaVerificar.size);
    if (!encontradas.size) return;
    this.series.set(this.series().filter(s => !encontradas.has(s)));
    this.ultimaAdicao.set([]);
    for (const [serie, motivo] of encontradas) this.anotar({ serie, motivo, tipo: 'recusada' });
    const [primeira, motivo] = [...encontradas][0];
    const texto = encontradas.size === 1 ? `${primeira} saiu da lista: ${motivo.charAt(0).toLowerCase()}${motivo.slice(1)}`
      : `${encontradas.size} séries saíram da lista: já estão no sistema. Veja abaixo.`;
    this.ultimo.set({ texto, tom: 'erro' });
    this.retorno.set({ texto: encontradas.size === 1 ? `${primeira} já está no sistema` : `${encontradas.size} já estão no sistema`, tom: 'erro' });
  }

  private anotar(c: Conferir): void {
    this.conferir.update(l => [c, ...l.filter(x => x.serie !== c.serie)].slice(0, MAX_CONFERIR));
  }

  private avisar(msg: { texto: string; tom: TomRetorno }, sobreCamera: boolean): void {
    // "Desfazer" só acompanha a mensagem de uma adição.
    this.ultimaAdicao.set([]);
    this.ultimo.set(msg);
    if (sobreCamera) this.retorno.set(msg);
  }

  private focarCampo(): void {
    if (this.modo() !== 'digitar' && (this.modo() !== 'ler' || this.temCamera)) return;
    setTimeout(() => this.campo()?.nativeElement.focus({ preventScroll: true }), 320);
  }
}

function primeiraMaiuscula(s: string): string {
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
}

function resumo(r: ResultadoAdicao): { texto: string; tom: TomRetorno } {
  const partes: string[] = [];
  if (r.novas.length) partes.push(`${contarSeries(r.novas.length)} ${r.novas.length === 1 ? 'adicionada' : 'adicionadas'}`);
  if (r.repetidas.length) partes.push(`${r.repetidas.length} já ${r.repetidas.length === 1 ? 'estava' : 'estavam'} na lista`);
  if (r.recusadas.length) {
    const motivos = [...new Set(r.recusadas.map(x => x.motivo))];
    const exemplo = r.recusadas.slice(0, 3).map(x => x.serie).join(', ');
    partes.push(`${r.recusadas.length} ${r.recusadas.length === 1 ? 'recusada' : 'recusadas'} (${motivos.join('; ')}): ${exemplo}${r.recusadas.length > 3 ? '…' : ''}`);
  }
  const tom: TomRetorno = r.recusadas.length ? (r.novas.length ? 'atencao' : 'erro') : r.novas.length ? 'ok' : 'atencao';
  return { texto: partes.join(' · ') || 'Nada para adicionar.', tom };
}
