import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { CesStatusComponent, NotificationService } from '@ces/ui';

/** Um número de série na grade; `indisponivel` marca a série que não está mais no estoque. */
export interface SerieGradeItem {
  readonly numSerie: string;
  readonly indisponivel?: boolean;
}

/**
 * Grade de números de série usada nas linhas expandidas dos relatórios de saldo e de
 * movimentação e no histórico do material. Cada série pode ser copiada com um clique.
 */
@Component({
  selector: 'app-series-grade',
  standalone: true,
  imports: [CesStatusComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './series-grade.component.css',
  template: `
    <ul class="grade" [attr.aria-label]="rotulo()">
      @for (serie of series(); track serie.numSerie) {
        <li class="grade__item" [class.grade__item--inativo]="serie.indisponivel">
          <span class="grade__numero" [title]="serie.numSerie">{{ serie.numSerie }}</span>
          @if (serie.indisponivel) {
            <ces-status tone="warning" icon="pi pi-ban">Indisponível</ces-status>
          }
          @if (copiavel()) {
            <button
              type="button"
              class="grade__copiar print:hidden"
              [attr.aria-label]="'Copiar número de série ' + serie.numSerie"
              [title]="copiada() === serie.numSerie ? 'Copiado' : 'Copiar'"
              (click)="copiar(serie.numSerie)"
            >
              <i class="pi" [class.pi-copy]="copiada() !== serie.numSerie" [class.pi-check]="copiada() === serie.numSerie" aria-hidden="true"></i>
            </button>
          }
        </li>
      }
    </ul>
  `,
})
export class SeriesGradeComponent {
  private readonly notifications = inject(NotificationService);

  readonly series = input.required<readonly SerieGradeItem[]>();
  readonly rotulo = input('Números de série');
  readonly copiavel = input(true);

  protected readonly copiada = signal<string | null>(null);

  protected copiar(numSerie: string): void {
    void navigator.clipboard?.writeText(numSerie).then(() => {
      this.copiada.set(numSerie);
      this.notifications.success(`Série ${numSerie} copiada.`);
      setTimeout(() => this.copiada.update((atual) => (atual === numSerie ? null : atual)), 2000);
    });
  }
}
