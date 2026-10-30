import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Cabeçalho das abas principais: título grande à esquerda, ação discreta à direita. */
@Component({
  selector: 'ces-titulo-grande',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="cab">
      <div class="txt">
        <h1>{{ titulo() }}</h1>
        @if (subtitulo()) { <p>{{ subtitulo() }}</p> }
      </div>
      <ng-content />
    </header>
  `,
  styles: [`
    :host { display: block; }
    .cab { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--ces-e3);
      padding: calc(var(--ces-safe-topo) + var(--ces-e5)) var(--ces-e5) var(--ces-e1); }
    .txt { min-width: 0; }
    h1 { margin: 0; font: var(--ces-t-titulo); letter-spacing: -.02em; text-wrap: balance; }
    p { margin: 2px 0 0; font: var(--ces-t-apoio); font-size: .875rem; color: var(--ces-texto-2); }
  `],
})
export class TituloGrandeComponent {
  readonly titulo = input.required<string>();
  readonly subtitulo = input<string | null | undefined>(null);
}
