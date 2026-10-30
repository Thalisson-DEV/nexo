import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { CesTone } from '@ces/ui';
import { AlvoAnalise } from './dashboard-analise.models';

/** Veredito do recorte em uma frase: o que um gestor precisa saber antes de olhar qualquer gráfico. */
export interface VereditoGerencial {
  readonly tom: CesTone;
  readonly rotulo: string;
  readonly titulo: string;
  readonly texto: string;
  /** Barra de progresso opcional (0–100), ex.: meta atingida. */
  readonly progresso?: { readonly valor: number; readonly rotulo: string; readonly marcador?: number; readonly rotuloMarcador?: string };
}

/**
 * Uma leitura de gestão: o que foi encontrado (`titulo` + `valor`), por que importa (`texto`) e onde
 * aprofundar (`acao`, que abre a análise detalhada do elemento).
 */
export interface LeituraGerencial {
  readonly id: string;
  readonly tom: CesTone;
  readonly icone: string;
  readonly titulo: string;
  readonly valor?: string;
  readonly texto: string;
  readonly acao?: { readonly rotulo: string; readonly alvo: AlvoAnalise };
}

const CHAVE_RECOLHIDO = 'ces.dashboard.visao-gerencial.recolhida';

const ROTULO_TOM: Record<CesTone, string> = {
  danger: 'Agir agora',
  warning: 'Acompanhar',
  success: 'Sinal positivo',
  info: 'Contexto',
  brand: 'Contexto',
  neutral: 'Contexto'
};

/**
 * Visão gerencial de um dashboard: veredito do período e leituras acionáveis. Cada leitura com `acao`
 * abre a análise detalhada correspondente — a visão gerencial é a porta de entrada do drill-down.
 * Pode ser recolhida (preferência do dispositivo).
 */
@Component({
  selector: 'app-dashboard-visao-gerencial',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './dashboard-visao-gerencial.component.css',
  template: `
    <section class="vg" [attr.aria-labelledby]="id + '-titulo'" [attr.data-tone]="veredito().tom">
      <header class="vg__cabecalho">
        <div class="vg__veredito">
          <span class="vg__eyebrow"><i class="pi pi-compass" aria-hidden="true"></i> Visão gerencial</span>
          <div class="vg__titulo-linha">
            <span class="vg__selo" [attr.data-tone]="veredito().tom">{{ veredito().rotulo }}</span>
            <h2 [id]="id + '-titulo'">{{ veredito().titulo }}</h2>
          </div>
          <p>{{ veredito().texto }}</p>
          @if (veredito().progresso; as p) {
            <div class="vg__progresso" role="img" [attr.aria-label]="p.rotulo + (p.rotuloMarcador ? '; ' + p.rotuloMarcador : '')">
              <div class="vg__trilho">
                <span class="vg__barra" [style.width.%]="limitar(p.valor)"></span>
                @if (p.marcador !== undefined) {
                  <span class="vg__marcador" [style.left.%]="limitar(p.marcador)"></span>
                }
              </div>
              <div class="vg__legenda">
                <span>{{ p.rotulo }}</span>
                @if (p.rotuloMarcador) { <span class="vg__legenda-marcador">{{ p.rotuloMarcador }}</span> }
              </div>
            </div>
          }
        </div>
        <div class="vg__acoes">
          @if (copiloto()) {
            <button type="button" class="vg__copiloto" (click)="perguntar.emit()">
              <img src="icons/copiloto-1.png" alt="" aria-hidden="true" />
              <span>Leitura completa com o Copiloto</span>
            </button>
          }
          <button type="button" class="vg__recolher" [attr.aria-expanded]="!recolhida()" [attr.aria-controls]="id + '-leituras'"
            (click)="alternar()">
            <i class="pi" [class.pi-chevron-up]="!recolhida()" [class.pi-chevron-down]="recolhida()" aria-hidden="true"></i>
            {{ recolhida() ? 'Mostrar leituras (' + leituras().length + ')' : 'Recolher' }}
          </button>
        </div>
      </header>

      @if (!recolhida()) {
        <ul class="vg__leituras" [id]="id + '-leituras'" aria-label="Leituras do período">
          @for (l of ordenadas(); track l.id) {
            <li class="vg__leitura" [attr.data-tone]="l.tom">
              <div class="vg__leitura-topo">
                <span class="vg__icone" aria-hidden="true"><i [class]="l.icone"></i></span>
                <span class="vg__tipo">{{ rotuloTom[l.tom] }}</span>
              </div>
              <h3>{{ l.titulo }}</h3>
              @if (l.valor) { <strong class="vg__valor">{{ l.valor }}</strong> }
              <p>{{ l.texto }}</p>
              @if (l.acao; as a) {
                <button type="button" class="vg__abrir" (click)="abrir.emit(a.alvo)">
                  {{ a.rotulo }} <i class="pi pi-arrow-right" aria-hidden="true"></i>
                </button>
              }
            </li>
          }
        </ul>
      }
    </section>
  `
})
export class DashboardVisaoGerencialComponent {
  private static sequencia = 0;
  protected readonly id = `vg-${++DashboardVisaoGerencialComponent.sequencia}`;
  protected readonly rotuloTom = ROTULO_TOM;

  readonly veredito = input.required<VereditoGerencial>();
  readonly leituras = input.required<readonly LeituraGerencial[]>();
  readonly copiloto = input(false);

  readonly abrir = output<AlvoAnalise>();
  readonly perguntar = output<void>();

  protected readonly recolhida = signal(ler());

  /** O que pede ação vem primeiro. */
  protected readonly ordenadas = computed(() => {
    const peso: Record<CesTone, number> = { danger: 0, warning: 1, info: 2, brand: 2, neutral: 3, success: 4 };
    return [...this.leituras()].sort((a, b) => peso[a.tom] - peso[b.tom]);
  });

  protected alternar(): void {
    this.recolhida.update((r) => !r);
    try {
      localStorage.setItem(CHAVE_RECOLHIDO, this.recolhida() ? '1' : '0');
    } catch {
      // preferência só em memória
    }
  }

  protected limitar(valor: number): number {
    return Math.max(0, Math.min(100, valor));
  }
}

function ler(): boolean {
  try {
    return localStorage.getItem(CHAVE_RECOLHIDO) === '1';
  } catch {
    return false;
  }
}
