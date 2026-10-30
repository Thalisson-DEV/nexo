import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, input } from '@angular/core';
import { CES_FORM_SECTION, CesFormRegistry } from './form-kit.registry';

let sectionSequence = 0;

/**
 * Seção do formulário: título (sem numeração manual e sem caixa alta forçada), descrição opcional e
 * grade de 12 colunas onde os `ces-form-field` se distribuem pelo `size`. Em telas estreitas a grade
 * vira uma coluna. Dentro de `ces-form-page` com índice, cada seção vira uma âncora.
 *
 * ```html
 * <ces-form-section title="Identificação" icon="pi pi-id-card" description="Como o contrato aparece nos relatórios">
 *   <ces-form-field label="Número" size="md">…</ces-form-field>
 * </ces-form-section>
 * ```
 */
@Component({
  selector: 'ces-form-section',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: CES_FORM_SECTION,
      useFactory: () => {
        const section = inject(CesFormSectionComponent);
        return { title: () => section.title(), icon: () => section.icon() };
      },
    },
  ],
  host: {
    class: 'ces-form-section',
    '[attr.id]': 'anchor()',
    '[attr.aria-labelledby]': 'titleId',
    role: 'group',
  },
  template: `
    @if (title()) {
      <header class="ces-form-section__header">
        @if (icon()) {
          <span class="ces-form-section__icon" aria-hidden="true"><i [class]="icon()"></i></span>
        }
        <div class="ces-form-section__heading">
          <h2 class="ces-form-section__title" [id]="titleId">{{ title() }}</h2>
          @if (description()) {
            <p class="ces-form-section__description">{{ description() }}</p>
          }
        </div>
        <div class="ces-form-section__actions"><ng-content select="[section-actions]"></ng-content></div>
      </header>
    }
    <div class="ces-form-section__grid">
      <ng-content></ng-content>
    </div>
  `,
})
export class CesFormSectionComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly title = input<string>('');
  readonly description = input<string | undefined>();
  /** Ícone da seção (`pi pi-...`), em caixa ao lado do título. Ajuda a escanear formulários longos. */
  readonly icon = input<string | undefined>();
  /** Id da âncora (`#materiais`). Default: gerado. */
  readonly sectionId = input<string | undefined>(undefined, { alias: 'id' });

  private readonly generatedId = `secao-${++sectionSequence}`;
  protected readonly titleId = `${this.generatedId}-titulo`;
  readonly anchor = () => this.sectionId() || this.generatedId;

  constructor() {
    const registry = inject(CesFormRegistry, { optional: true });
    if (registry) {
      const anchor = () => this.anchor();
      const unregister = registry.addSection({
        get id() {
          return anchor();
        },
        title: this.title,
        icon: this.icon,
        element: () => this.host.nativeElement,
      });
      inject(DestroyRef).onDestroy(unregister);
    }
  }
}
