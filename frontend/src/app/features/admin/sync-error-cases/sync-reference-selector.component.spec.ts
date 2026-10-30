import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { SyncReferenceCatalogService } from './sync-reference-catalog.service';
import { SyncReferenceSelectorComponent } from './sync-reference-selector.component';

describe('SyncReferenceSelectorComponent', () => {
  it('mostra o cadastro associado ao ID recebido antes de pesquisar', () => {
    const resolve = vi.fn(() => of({ value: 'team-1', label: 'Equipe FAF1234123' }));
    TestBed.configureTestingModule({
      imports: [SyncReferenceSelectorComponent],
      providers: [
        provideNoopAnimations(),
        { provide: SyncReferenceCatalogService, useValue: { resolve, search: () => of({ items: [], nextCursor: null, hasMore: false }) } }
      ]
    });
    const fixture = TestBed.createComponent(SyncReferenceSelectorComponent);
    fixture.componentRef.setInput('kind', 'team');
    fixture.componentRef.setInput('label', 'Equipe');
    fixture.componentRef.setInput('value', 'team-1');
    fixture.detectChanges();
    expect(resolve).toHaveBeenCalledWith('team', 'team-1');
    expect(fixture.componentInstance.lookup.options()).toContainEqual({ value: 'team-1', label: 'Equipe FAF1234123' });
  });
});
