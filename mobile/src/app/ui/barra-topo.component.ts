import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconeComponent } from './icone.component';

/**
 * Barra superior de telas empilhadas (detalhe, lançamento). Abas usam
 * `ces-titulo-grande`. O botão da esquerda é "voltar" ou "fechar" (fluxo
 * que guarda rascunho), sempre com 48 px de alvo.
 */
@Component({
  selector: 'ces-barra-topo',
  standalone: true,
  imports: [IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barra">
      @if (navegacao() !== 'nenhuma') {
        <button type="button" class="ces-btn-icone" (click)="voltar.emit()"
          [attr.aria-label]="navegacao() === 'fechar' ? rotuloFechar() : 'Voltar'">
          <ces-icone [nome]="navegacao() === 'fechar' ? 'fechar' : 'voltar'" />
        </button>
      }
      <div class="titulos">
        <h1>{{ titulo() }}</h1>
        @if (subtitulo()) { <p class="ces-num">{{ subtitulo() }}</p> }
      </div>
      <div class="acoes"><ng-content /></div>
    </header>
  `,
  styles: [`
    :host { display: block; flex: none; background: var(--ces-fundo); }
    .barra { display: flex; align-items: center; gap: var(--ces-e1); min-height: var(--ces-barra-topo);
      padding: calc(var(--ces-safe-topo) + 4px) var(--ces-e2) 4px var(--ces-e1); }
    .titulos { flex: 1; min-width: 0; padding-left: var(--ces-e1); }
    h1 { margin: 0; font: var(--ces-t-barra); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    p { margin: 0; font: var(--ces-t-apoio); color: var(--ces-texto-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .acoes { display: flex; align-items: center; gap: var(--ces-e1); }
  `],
})
export class BarraTopoComponent {
  readonly titulo = input.required<string>();
  readonly subtitulo = input<string | null | undefined>(null);
  readonly navegacao = input<'voltar' | 'fechar' | 'nenhuma'>('voltar');
  readonly rotuloFechar = input('Fechar e guardar rascunho');
  readonly voltar = output<void>();
}
