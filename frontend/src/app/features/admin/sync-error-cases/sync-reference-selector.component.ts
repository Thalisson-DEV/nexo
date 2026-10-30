import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, model, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { CesAutocompleteComponent } from '@ces/ui/forms';
import { RemoteLookup } from '../../../core/lookup/store/remote-lookup';
import { SyncReferenceCatalogService, SyncReferenceKind } from './sync-reference-catalog.service';

@Component({
  selector: 'app-sync-reference-selector',
  standalone: true,
  imports: [FormsModule, CesAutocompleteComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-autocomplete
      [label]="label()"
      [options]="lookup.options()"
      [ngModel]="value()"
      (ngModelChange)="value.set($event)"
      [loading]="lookup.loading()"
      [status]="lookup.status()"
      [error]="resolutionError() || (lookup.error() ? 'Não foi possível buscar os cadastros.' : undefined)"
      [description]="resolutionError() ? 'Selecione um cadastro válido para substituir a referência recebida.' : undefined"
      [showSearchInstruction]="false"
      [minLength]="2"
      [forceSelection]="true"
      [clearable]="true"
      placeholder="Pesquise por nome, código ou descrição"
      (search)="lookup.search($event)"
      (loadMore)="lookup.loadNext()"
      (retry)="lookup.retry()"
    />
  `
})
export class SyncReferenceSelectorComponent {
  private readonly catalog = inject(SyncReferenceCatalogService);
  private readonly destroyRef = inject(DestroyRef);
  readonly kind = input.required<SyncReferenceKind>();
  readonly label = input.required<string>();
  readonly value = model<string | null>(null);
  readonly lookup = new RemoteLookup<string>(request => this.catalog.search(this.kind(), request.query, request.cursor), {
    destroyRef: this.destroyRef
  });
  readonly resolutionError = signal<string | undefined>(undefined);

  constructor() {
    effect(() => {
      const id = this.value();
      const kind = this.kind();
      if (!id || this.lookup.options().some(option => option.value === id)) {
        this.resolutionError.set(undefined);
        return;
      }
      this.catalog.resolve(kind, id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: option => {
          if (this.value() === id) {
            this.lookup.seedSelected(option);
            this.resolutionError.set(undefined);
          }
        },
        error: () => {
          if (this.value() === id) this.resolutionError.set('O cadastro informado no envio não foi localizado.');
        }
      });
    });
  }
}
