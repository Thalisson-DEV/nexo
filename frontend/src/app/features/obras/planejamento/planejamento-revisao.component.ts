import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { CesButtonComponent, CesCalloutComponent } from '@ces/ui';
import {
  AceitarProposta,
  PropostaPlanejamento,
  TIPOS_PONTO,
  TipoPontoPlanejamento
} from '../../../core/models/obras-planejamento.models';

/** Sugestão de ponto em revisão: o usuário pode corrigir número e posição antes de aceitar. */
export interface PontoEmRevisao {
  referencia: string;
  numero: string;
  tipo: TipoPontoPlanejamento;
  x: number;
  y: number;
  barramento: string | null;
  textosLidos: string[];
  confianca: number;
  aceito: boolean;
  kits: { idKit: string; codigo: string; descricao: string; textoLido: string; marcado: boolean }[];
}

export interface VaoEmRevisao {
  referenciaA: string;
  referenciaB: string;
  metragem: number | null;
  textosLidos: string[];
  confianca: number;
  aceito: boolean;
  kits: { idKit: string; codigo: string; descricao: string; textoLido: string; marcado: boolean }[];
}

const CONFIANCA_BAIXA = 0.6;

export function paraRevisao(proposta: PropostaPlanejamento, numerosExistentes: ReadonlySet<string>): { pontos: PontoEmRevisao[]; vaos: VaoEmRevisao[] } {
  const pontos = proposta.pontos.map((p) => ({
    referencia: p.referencia,
    numero: p.numero,
    tipo: p.tipo,
    x: p.x,
    y: p.y,
    barramento: p.barramento ?? null,
    textosLidos: p.textosLidos ?? [],
    confianca: p.confianca,
    aceito: !p.conflito && !numerosExistentes.has(p.numero) && p.confianca >= CONFIANCA_BAIXA,
    kits: (p.kits ?? []).map((k) => ({ ...k }))
  }));
  const vaos = proposta.vaos.map((v) => ({
    referenciaA: v.referenciaA,
    referenciaB: v.referenciaB,
    metragem: v.metragem ?? null,
    textosLidos: v.textosLidos ?? [],
    confianca: v.confianca,
    aceito: v.confianca >= CONFIANCA_BAIXA,
    kits: (v.kits ?? []).map((k) => ({ ...k }))
  }));
  return { pontos, vaos };
}

/** Monta o aceite: só o que ficou marcado, e vão só quando os dois pontos existem ou foram aceitos. */
export function montarAceite(pontos: readonly PontoEmRevisao[], vaos: readonly VaoEmRevisao[], numerosExistentes: ReadonlySet<string>): AceitarProposta {
  const aceitos = pontos.filter((p) => p.aceito);
  const referencias = new Set(aceitos.map((p) => p.referencia));
  const resolvido = (ref: string) => referencias.has(ref) || numerosExistentes.has(ref);
  return {
    pontos: aceitos.map((p) => ({
      referencia: p.referencia,
      numero: p.numero.trim(),
      tipo: p.tipo,
      x: p.x,
      y: p.y,
      barramento: p.barramento,
      idsKits: p.kits.filter((k) => k.marcado).map((k) => k.idKit)
    })),
    vaos: vaos
      .filter((v) => v.aceito && resolvido(v.referenciaA) && resolvido(v.referenciaB))
      .map((v) => ({
        referenciaA: v.referenciaA,
        referenciaB: v.referenciaB,
        metragem: v.metragem,
        idsKits: v.metragem ? v.kits.filter((k) => k.marcado).map((k) => k.idKit) : []
      }))
  };
}

/** Painel de revisão da leitura do Copiloto: nada é gravado até o usuário aceitar. */
@Component({
  selector: 'app-planejamento-revisao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CesButtonComponent, CesCalloutComponent],
  template: `
    <header class="topo">
      <div>
        <h2><i class="pi pi-sparkles" aria-hidden="true"></i> Revisar leitura</h2>
        <p>Página {{ pagina() }}. Arraste os marcadores tracejados na folha para ajustar a posição.</p>
      </div>
    </header>

    <div class="corpo">
      @for (aviso of avisos(); track aviso) {
        <ces-callout tone="warning" [dense]="true">{{ aviso }}</ces-callout>
      }

      @if (!pontos().length && !vaos().length) {
        <p class="vazio">O Copiloto não encontrou pontos numerados nesta página. Marque manualmente ou tente outra página.</p>
      }

      @if (pontos().length) {
        <div class="secao">
          <label class="secao__marca">
            <input type="checkbox" [checked]="todosPontos()" (change)="marcarTodosPontos($any($event.target).checked)" />
            <strong>Pontos</strong> <span>{{ aceitosPontos() }} de {{ pontos().length }}</span>
          </label>
          @for (ponto of pontos(); track ponto.referencia; let i = $index) {
            <article class="cartao" [class.cartao--ativo]="destaque() === ponto.referencia" (click)="destacar.emit(ponto.referencia)">
              <div class="cartao__linha">
                <input type="checkbox" [checked]="ponto.aceito" [disabled]="conflito(ponto)" [attr.aria-label]="'Aceitar ponto ' + ponto.numero"
                  (change)="alterarPonto(i, { aceito: $any($event.target).checked })" (click)="$event.stopPropagation()" />
                <span class="prefixo">P</span>
                <input class="cartao__numero" type="text" maxlength="20" aria-label="Número do ponto" [value]="ponto.numero"
                  (change)="alterarPonto(i, { numero: $any($event.target).value, aceito: true })" (click)="$event.stopPropagation()" />
                <select aria-label="Tipo do ponto" [value]="ponto.tipo" (change)="alterarPonto(i, { tipo: $any($event.target).value })" (click)="$event.stopPropagation()">
                  @for (tipo of tipos; track tipo.value) {
                    <option [value]="tipo.value" [selected]="tipo.value === ponto.tipo">{{ tipo.label }}</option>
                  }
                </select>
                <span class="confianca" [class.confianca--baixa]="ponto.confianca < baixa" [title]="'Confiança da leitura: ' + porcento(ponto.confianca)">{{ porcento(ponto.confianca) }}</span>
              </div>
              @if (conflito(ponto)) {
                <p class="cartao__erro"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i> Já existe P{{ ponto.numero }} nesta obra: informe outro número.</p>
              }
              @if (ponto.barramento || ponto.textosLidos.length) {
                <p class="cartao__lido">Lido na folha: {{ lido(ponto.barramento, ponto.textosLidos) }}</p>
              }
              @for (kit of ponto.kits; track kit.idKit; let k = $index) {
                <label class="kit" (click)="$event.stopPropagation()">
                  <input type="checkbox" [checked]="kit.marcado" (change)="marcarKitPonto(i, k, $any($event.target).checked)" />
                  <code>{{ kit.codigo }}</code> <span>{{ kit.descricao }}</span>
                </label>
              }
            </article>
          }
        </div>
      }

      @if (vaos().length) {
        <div class="secao">
          <label class="secao__marca">
            <input type="checkbox" [checked]="todosVaos()" (change)="marcarTodosVaos($any($event.target).checked)" />
            <strong>Vãos</strong> <span>{{ aceitosVaos() }} de {{ vaos().length }}</span>
          </label>
          @for (vao of vaos(); track vao.referenciaA + vao.referenciaB; let i = $index) {
            <article class="cartao">
              <div class="cartao__linha">
                <input type="checkbox" [checked]="vao.aceito && dependenciasOk(vao)" [disabled]="!dependenciasOk(vao)" [attr.aria-label]="'Aceitar vão P' + vao.referenciaA + ' a P' + vao.referenciaB"
                  (change)="alterarVao(i, { aceito: $any($event.target).checked })" />
                <strong>P{{ vao.referenciaA }} — P{{ vao.referenciaB }}</strong>
                <input class="cartao__metros" type="number" min="0" step="0.01" aria-label="Metragem" placeholder="metros" [value]="vao.metragem ?? ''"
                  (change)="alterarVao(i, { metragem: $any($event.target).valueAsNumber || null })" />
                <span class="unidade">m</span>
                <span class="confianca" [class.confianca--baixa]="vao.confianca < baixa" [title]="'Confiança da leitura: ' + porcento(vao.confianca)">{{ porcento(vao.confianca) }}</span>
              </div>
              @if (!dependenciasOk(vao)) {
                <p class="cartao__lido">Aceite os dois pontos para incluir este vão.</p>
              }
              @if (vao.textosLidos.length) {
                <p class="cartao__lido">Lido na folha: {{ vao.textosLidos.join(', ') }}</p>
              }
              @for (kit of vao.kits; track kit.idKit; let k = $index) {
                <label class="kit">
                  <input type="checkbox" [checked]="kit.marcado" [disabled]="!vao.metragem" (change)="marcarKitVao(i, k, $any($event.target).checked)" />
                  <code>{{ kit.codigo }}</code> <span>{{ kit.descricao }}</span>
                </label>
              }
              @if (vao.kits.length && !vao.metragem) {
                <p class="cartao__lido">Informe a metragem para aplicar o kit de cabo.</p>
              }
            </article>
          }
        </div>
      }
    </div>

    <footer class="rodape">
      <ces-button variant="ghost" size="sm" label="Descartar" (pressed)="descartar.emit()"></ces-button>
      <ces-button size="sm" icon="pi pi-check" [label]="rotuloAceite()" [loading]="salvando()" [disabled]="!totalAceito()" (pressed)="aceitar.emit(aceite())"></ces-button>
    </footer>
  `,
  styles: [`
    :host { display: flex; flex: 1; flex-direction: column; min-height: 0; color: var(--ces-foreground); font-size: 0.8125rem; }
    .topo { padding: 0.625rem 0.75rem; border-bottom: 1px solid var(--ces-border); }
    h2 { display: flex; gap: 0.375rem; align-items: center; margin: 0; font-size: 0.9375rem; font-weight: 600; }
    h2 i { color: var(--ces-brand); }
    .topo p { margin: 0.125rem 0 0; color: var(--ces-foreground-muted); font-size: 0.75rem; }
    .corpo { display: flex; flex: 1; flex-direction: column; gap: 0.625rem; min-height: 0; padding: 0.625rem; overflow-y: auto; }
    .vazio { margin: 0; padding: 1rem 0.25rem; color: var(--ces-foreground-muted); text-align: center; }
    .secao { display: flex; flex-direction: column; gap: 0.375rem; }
    .secao__marca { display: flex; gap: 0.375rem; align-items: center; cursor: pointer; }
    .secao__marca span { color: var(--ces-foreground-muted); font-size: 0.75rem; }
    .cartao { display: flex; flex-direction: column; gap: 0.25rem; padding: 0.375rem 0.5rem; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface); }
    .cartao--ativo { border-color: var(--ces-brand); box-shadow: 0 0 0 1px var(--ces-brand); }
    .cartao__linha { display: flex; gap: 0.375rem; align-items: center; }
    .cartao input[type='text'], .cartao input[type='number'], .cartao select { min-width: 0; padding: 0.125rem 0.25rem; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-sm); background: var(--ces-surface); color: var(--ces-foreground); font-size: 0.75rem; }
    .prefixo, .unidade { color: var(--ces-foreground-muted); font-size: 0.75rem; }
    .prefixo { margin-right: -0.25rem; }
    .cartao__numero { width: 3.25rem; }
    .cartao select { flex: 1; }
    .cartao__metros { width: 4.5rem; margin-left: auto; }
    .confianca { flex: none; margin-left: auto; color: var(--ces-foreground-muted); font-size: 0.6875rem; font-variant-numeric: tabular-nums; }
    .cartao__metros + .unidade + .confianca { margin-left: 0; }
    .confianca--baixa { color: var(--ces-warning-text); font-weight: 600; }
    .cartao__erro { display: flex; gap: 0.25rem; margin: 0; color: var(--ces-danger-text); font-size: 0.75rem; }
    .cartao__lido { margin: 0; color: var(--ces-foreground-muted); font-size: 0.6875rem; }
    .kit { display: flex; gap: 0.375rem; align-items: baseline; font-size: 0.75rem; cursor: pointer; }
    .kit code { flex: none; color: var(--ces-brand); font-size: 0.6875rem; font-weight: 600; }
    input[type='checkbox'] { flex: none; accent-color: var(--ces-brand); }
    .rodape { display: flex; gap: 0.5rem; justify-content: flex-end; padding: 0.5rem 0.75rem; border-top: 1px solid var(--ces-border); }
  `]
})
export class PlanejamentoRevisaoComponent {
  readonly proposta = input.required<PropostaPlanejamento>();
  readonly pagina = input.required<number>();
  readonly numerosExistentes = input.required<ReadonlySet<string>>();
  readonly destaque = input<string | null>(null);
  readonly salvando = input(false);
  /** Posição ajustada na folha, vinda do componente pai. */
  readonly ajustes = input<ReadonlyMap<string, { x: number; y: number }>>(new Map());

  readonly aceitar = output<AceitarProposta>();
  readonly descartar = output<void>();
  readonly destacar = output<string>();
  readonly pontosAlterados = output<PontoEmRevisao[]>();

  protected readonly tipos = TIPOS_PONTO;
  protected readonly baixa = CONFIANCA_BAIXA;
  protected readonly pontos = signal<PontoEmRevisao[]>([]);
  protected readonly vaos = signal<VaoEmRevisao[]>([]);
  protected readonly avisos = computed(() => this.proposta().avisos ?? []);

  protected readonly aceitosPontos = computed(() => this.pontos().filter((p) => p.aceito).length);
  protected readonly aceitosVaos = computed(() => this.vaos().filter((v) => v.aceito && this.dependenciasOk(v)).length);
  protected readonly todosPontos = computed(() => this.pontos().every((p) => p.aceito || this.conflito(p)));
  protected readonly todosVaos = computed(() => this.vaos().every((v) => v.aceito || !this.dependenciasOk(v)));
  protected readonly aceite = computed(() => montarAceite(this.aplicarAjustes(), this.vaos(), this.numerosExistentes()));
  protected readonly totalAceito = computed(() => this.aceite().pontos.length + this.aceite().vaos.length);
  protected readonly rotuloAceite = computed(() => {
    const { pontos, vaos } = this.aceite();
    return `Aceitar ${pontos.length} ${pontos.length === 1 ? 'ponto' : 'pontos'} e ${vaos.length} ${vaos.length === 1 ? 'vão' : 'vãos'}`;
  });

  constructor() {
    effect(() => {
      const revisao = paraRevisao(this.proposta(), this.numerosExistentes());
      this.pontos.set(revisao.pontos);
      this.vaos.set(revisao.vaos);
      this.pontosAlterados.emit(revisao.pontos);
    });
  }

  private aplicarAjustes(): PontoEmRevisao[] {
    const ajustes = this.ajustes();
    return this.pontos().map((p) => ({ ...p, ...(ajustes.get(p.referencia) ?? {}) }));
  }

  protected conflito(ponto: PontoEmRevisao): boolean {
    return this.numerosExistentes().has(ponto.numero.trim().toUpperCase());
  }

  protected dependenciasOk(vao: VaoEmRevisao): boolean {
    const ok = (ref: string) => this.numerosExistentes().has(ref) || this.pontos().some((p) => p.referencia === ref && p.aceito);
    return ok(vao.referenciaA) && ok(vao.referenciaB);
  }

  protected alterarPonto(indice: number, parcial: Partial<PontoEmRevisao>): void {
    const pontos = this.pontos().map((p, i) => (i === indice ? { ...p, ...parcial } : p));
    const alterado = pontos[indice];
    pontos[indice] = { ...alterado, aceito: alterado.aceito && !this.conflito(alterado) };
    this.pontos.set(pontos);
    this.pontosAlterados.emit(pontos);
  }

  protected alterarVao(indice: number, parcial: Partial<VaoEmRevisao>): void {
    this.vaos.update((vaos) => vaos.map((v, i) => (i === indice ? { ...v, ...parcial } : v)));
  }

  protected marcarTodosPontos(aceito: boolean): void {
    const pontos = this.pontos().map((p) => ({ ...p, aceito: aceito && !this.conflito(p) }));
    this.pontos.set(pontos);
    this.pontosAlterados.emit(pontos);
  }

  protected marcarTodosVaos(aceito: boolean): void {
    this.vaos.update((vaos) => vaos.map((v) => ({ ...v, aceito })));
  }

  protected marcarKitPonto(ponto: number, kit: number, marcado: boolean): void {
    this.pontos.update((pontos) => pontos.map((p, i) => i !== ponto ? p : { ...p, kits: p.kits.map((k, j) => (j === kit ? { ...k, marcado } : k)) }));
  }

  protected marcarKitVao(vao: number, kit: number, marcado: boolean): void {
    this.vaos.update((vaos) => vaos.map((v, i) => i !== vao ? v : { ...v, kits: v.kits.map((k, j) => (j === kit ? { ...k, marcado } : k)) }));
  }

  protected porcento(valor: number): string {
    return `${Math.round(valor * 100)}%`;
  }

  protected lido(barramento: string | null, textos: readonly string[]): string {
    return [barramento, ...textos].filter(Boolean).join(', ');
  }
}
