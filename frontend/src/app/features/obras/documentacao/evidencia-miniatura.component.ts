import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, afterNextRender, inject, input, output, signal } from '@angular/core';
import { take } from 'rxjs';
import { EvidenciaPonto } from '../../../core/models/obras-documentacao.models';
import { ObrasDocumentacaoService } from '../../../core/services/obras-documentacao.service';

/** Miniatura de uma foto do book. A foto exige login, por isso vem como blob, e só é pedida quando aparece na tela. */
@Component({
  selector: 'app-evidencia-miniatura',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="em" [attr.aria-label]="'Ver a foto: ' + descricao()" [title]="descricao()" (click)="abrir.emit()">
      @if (url(); as src) {
        <img [src]="src" alt="" />
      } @else if (falhou()) {
        <i class="pi pi-image" aria-hidden="true"></i>
      }
    </button>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .em { display: grid; place-items: center; width: 100%; aspect-ratio: 1; overflow: hidden; padding: 0; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface-subtle); color: var(--ces-foreground-muted); cursor: zoom-in; }
    .em:hover { border-color: var(--ces-brand); }
    .em:focus-visible { outline: 3px solid var(--ces-focus-ring); outline-offset: 2px; }
    .em img { display: block; width: 100%; height: 100%; object-fit: cover; }
  `
})
export class EvidenciaMiniaturaComponent {
  private readonly servico = inject(ObrasDocumentacaoService);
  private readonly elemento = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);

  readonly idObra = input.required<string>();
  readonly evidencia = input.required<EvidenciaPonto>();
  readonly abrir = output<void>();

  protected readonly url = signal<string | null>(null);
  protected readonly falhou = signal(false);

  protected descricao(): string {
    const e = this.evidencia();
    const item = e.tituloItem ?? 'Outras evidências';
    return e.legenda ? `${item}, ${e.legenda}` : item;
  }

  constructor() {
    afterNextRender(() => {
      if (typeof IntersectionObserver === 'undefined') {
        this.carregar();
        return;
      }
      const observador = new IntersectionObserver((entradas) => {
        if (entradas.some((entrada) => entrada.isIntersecting)) {
          observador.disconnect();
          this.carregar();
        }
      }, { rootMargin: '200px' });
      observador.observe(this.elemento.nativeElement);
      this.destroyRef.onDestroy(() => observador.disconnect());
    });
    this.destroyRef.onDestroy(() => {
      const atual = this.url();
      if (atual) URL.revokeObjectURL(atual);
    });
  }

  private carregar(): void {
    this.servico.baixarMiniatura(this.idObra(), this.evidencia().id).pipe(take(1)).subscribe({
      next: (blob) => this.url.set(URL.createObjectURL(blob)),
      error: () => this.falhou.set(true)
    });
  }
}
