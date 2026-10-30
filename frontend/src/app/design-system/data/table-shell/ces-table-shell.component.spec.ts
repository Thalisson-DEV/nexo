import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { CesTableShellComponent } from './ces-table-shell.component';

@Component({
  standalone: true,
  imports: [CesTableShellComponent],
  template: `
    <ces-table-shell [state]="state" [customEmpty]="true">
      <div class="rows">Linhas da tabela</div>
      <p table-empty class="custom-empty">Cadastre o primeiro material.</p>
    </ces-table-shell>
  `
})
class HostComponent {
  state: 'ready' | 'empty' = 'ready';
}

describe('CesTableShellComponent', () => {
  it('preserva o p-table projetado sem encapsulá-lo', async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.rows')?.textContent).toContain('Linhas da tabela');
  });

  it('troca o conteúdo pelo slot vazio source-owned', async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.state = 'empty';
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.rows')).toBeNull();
    expect(fixture.nativeElement.querySelector('.custom-empty')?.textContent).toContain(
      'Cadastre o primeiro material.'
    );
  });
});
