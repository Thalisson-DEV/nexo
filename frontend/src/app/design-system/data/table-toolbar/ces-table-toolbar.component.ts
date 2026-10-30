import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';

@Component({
  selector: 'ces-table-toolbar',
  standalone: true,
  imports: [FormsModule, IconFieldModule, InputIconModule, InputTextModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'ces-table-toolbar' },
  styles: `
    :host {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      width: 100%;
    }

    .ces-table-toolbar__primary,
    .ces-table-toolbar__actions {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .ces-table-toolbar__primary {
      min-width: 0;
      flex: 1 1 auto;
    }

    .ces-table-toolbar__search {
      width: min(100%, 22rem);
    }

    .ces-table-toolbar__search input {
      padding-left: 2rem;
    }

    .ces-table-toolbar__actions {
      flex: 0 0 auto;
      justify-content: flex-end;
    }

    @media (max-width: 40rem) {
      :host {
        align-items: stretch;
        flex-direction: column;
      }

      .ces-table-toolbar__search,
      .ces-table-toolbar__actions {
        width: 100%;
      }

      .ces-table-toolbar__actions {
        flex-wrap: wrap;
        justify-content: flex-start;
      }
    }
  `,
  template: `
    <div class="ces-table-toolbar__primary">
      @if (showSearch()) {
        <p-iconfield iconPosition="left" class="ces-table-toolbar__search">
          <p-inputicon class="pi pi-search" aria-hidden="true"></p-inputicon>
          <input
            pInputText
            type="search"
            class="ces-prime-control"
            [placeholder]="searchPlaceholder()"
            [attr.aria-label]="searchLabel()"
            [attr.aria-keyshortcuts]="keyShortcut() || null"
            [title]="keyShortcut() ? searchLabel() + ' (' + keyShortcut() + ')' : ''"
            [(ngModel)]="query"
            (input)="searchChanged.emit(query())"
          />
        </p-iconfield>
      }
      <ng-content select="[table-filters]"></ng-content>
    </div>

    <div class="ces-table-toolbar__actions" role="group" [attr.aria-label]="actionsLabel()">
      <ng-content></ng-content>
    </div>
  `
})
export class CesTableToolbarComponent {
  readonly query = model('');
  readonly showSearch = input(true);
  readonly searchPlaceholder = input('Pesquisar registros');
  readonly searchLabel = input('Pesquisar registros');
  readonly actionsLabel = input('Ações da tabela');
  /** Atalho que leva o foco à busca, anunciado com `aria-keyshortcuts`. */
  readonly keyShortcut = input<string | undefined>();

  readonly searchChanged = output<string>();
}
