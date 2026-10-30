import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import {
  CHAVE_DEGRAU_DESVIO,
  PiramideAcidentalidadeItem
} from '../../../core/models/sst-evento.models';

interface NivelPiramide extends PiramideAcidentalidadeItem {
  cor: string;
  clipPath: string;
  topo: boolean;
}

const CORES: Record<string, string> = {
  FATALIDADE: '#b91c1c',
  ACIDENTE_COM_AFASTAMENTO: '#dc2626',
  ACIDENTE_SEM_AFASTAMENTO: '#ea580c',
  INCIDENTE: '#7c3aed',
  [CHAVE_DEGRAU_DESVIO]: '#2563eb'
};

const COR_PADRAO = '#64748b';

@Component({
  selector: 'app-piramide-bird',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    @if (niveis().length) {
      <figure class="bird" aria-labelledby="bird-caption">
        <div class="bird__visual" role="img" [attr.aria-label]="descricaoAcessivel()">
          <div class="bird__shape" aria-hidden="true">
            @for (nivel of niveis(); track nivel.chave) {
              <div
                class="bird__tier"
                [class.bird__tier--zero]="nivel.total === 0"
                [style.--tier-color]="nivel.cor"
                [style.clip-path]="nivel.clipPath"
              >
                <strong>{{ nivel.total }}</strong>
              </div>
            }
          </div>

          <div class="bird__legend">
            @for (nivel of niveis(); track nivel.chave) {
              <div class="bird__legend-row">
                <i [style.background]="nivel.cor"></i>
                <div class="bird__legend-text">
                  <strong>{{ nivel.rotulo }}</strong>
                  <span>{{ nivel.total }} · {{ nivel.participacaoPercentual | number:'1.0-1' }}%</span>
                </div>
              </div>
            }
          </div>
        </div>

        <figcaption id="bird-caption" class="bird__caption">
          <span class="bird__ratio">{{ razaoBase() | number:'1.0-1' }} : 1</span>
          <span>desvios para cada evento com lesão</span>
        </figcaption>
      </figure>
    } @else {
      <div class="dash-empty"><i class="pi pi-chart-bar"></i>Sem dados para montar a pirâmide.</div>
    }
  `,
  styles: [`
    :host { container-type: inline-size; display: block; }

    .bird { display: flex; flex-direction: column; gap: .85rem; margin: 0; }

    .bird__visual {
      display: grid;
      grid-template-columns: minmax(10rem, 1.2fr) minmax(9rem, 1fr);
      gap: clamp(.5rem, 3cqw, 1.5rem);
      align-items: center;
    }

    /* --- Pirâmide: degraus encaixados sem gap --- */
    .bird__shape {
      display: flex;
      flex-direction: column;
      /* sem gap — os degraus se encaixam */
    }

    .bird__tier {
      display: grid;
      place-items: center;
      min-height: clamp(2.4rem, 5cqw, 3.2rem);
      background: var(--tier-color);
      transition: filter 160ms ease;
    }

    .bird__tier:hover { filter: brightness(1.08); }
    .bird__tier--zero { opacity: .3; }

    .bird__tier strong {
      color: #fff;
      font-size: clamp(.82rem, 2.5cqw, 1.25rem);
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      line-height: 1;
      text-shadow: 0 1px 2px rgb(0 0 0 / 25%);
    }

    /* --- Legenda ao lado --- */
    .bird__legend {
      display: flex;
      flex-direction: column;
      gap: .4rem;
    }

    .bird__legend-row {
      display: flex;
      align-items: center;
      gap: .5rem;
      padding: .4rem .55rem;
      border-radius: .55rem;
      transition: background 150ms ease;
    }

    .bird__legend-row:hover { background: #f1f5f9; }

    .bird__legend-row > i {
      flex: none;
      width: .6rem;
      height: .6rem;
      border-radius: .2rem;
    }

    .bird__legend-text {
      display: flex;
      flex-direction: column;
      gap: .05rem;
      min-width: 0;
    }

    .bird__legend-text strong {
      color: #1e293b;
      font-size: clamp(.62rem, 1.6cqw, .76rem);
      font-weight: 650;
      line-height: 1.25;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .bird__legend-text span {
      color: #94a3b8;
      font-size: clamp(.5rem, 1.2cqw, .6rem);
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }

    /* --- Rodapé --- */
    .bird__caption {
      display: flex;
      align-items: baseline;
      justify-content: center;
      gap: .45rem;
      padding-top: .7rem;
      border-top: 1px solid #e5edf4;
      color: #829ab1;
      font-size: .66rem;
      text-align: center;
    }

    .bird__ratio {
      color: #102a43;
      font-size: 1rem;
      font-weight: 800;
      font-variant-numeric: tabular-nums;
    }

    /* --- Responsivo --- */
    @container (max-width: 420px) {
      .bird__visual { grid-template-columns: 1fr; }
      .bird__legend { flex-direction: row; flex-wrap: wrap; gap: .25rem; }
      .bird__legend-row { padding: .3rem .4rem; }
      .bird__legend-text span { display: none; }
    }

    /* --- Dark mode --- */
    :host-context(.app-dark), :host-context(.dark) {
      .bird__legend-row:hover { background: var(--ces-surface-subtle, rgba(255,255,255,.05)); }
      .bird__legend-text strong { color: #f1f5f9; }
      .bird__legend-text span { color: #64748b; }
      .bird__caption { border-color: var(--ces-border, #334155); }
      .bird__ratio { color: #f8fafc; }
    }
  `]
})
export class PiramideBirdComponent {
  readonly itens = input.required<PiramideAcidentalidadeItem[]>();

  /**
   * Gera degraus trapezoidais que se encaixam formando uma pirâmide contínua.
   *
   * Para N degraus, a largura superior do degrau i é (i/N * 100)% e a largura
   * inferior é ((i+1)/N * 100)%. O clip-path de cada degrau faz o recorte de
   * modo que o fundo de um se alinhe perfeitamente ao topo do próximo.
   */
  protected readonly niveis = computed<NivelPiramide[]>(() => {
    const itens = this.itens();
    const n = itens.length;

    return itens.map((item, i) => {
      const topPct = (i / n) * 100;
      const bottomPct = ((i + 1) / n) * 100;
      const topInset = (100 - topPct) / 2;
      const bottomInset = (100 - bottomPct) / 2;

      let clipPath: string;
      if (i === 0) {
        // Triângulo no topo
        clipPath = `polygon(50% 0%, ${100 - bottomInset}% 100%, ${bottomInset}% 100%)`;
      } else {
        clipPath = `polygon(${topInset}% 0%, ${100 - topInset}% 0%, ${100 - bottomInset}% 100%, ${bottomInset}% 100%)`;
      }

      return {
        ...item,
        cor: CORES[item.chave] ?? COR_PADRAO,
        clipPath,
        topo: i === 0
      };
    });
  });

  protected readonly razaoBase = computed(() =>
    this.itens().find((item) => item.chave === CHAVE_DEGRAU_DESVIO)?.razaoPorEventoComLesao ?? 0
  );

  protected descricaoAcessivel(): string {
    return this.niveis().map((nivel) => `${nivel.rotulo}: ${nivel.total}`).join('; ');
  }
}
