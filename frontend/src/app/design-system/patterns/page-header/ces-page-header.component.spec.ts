import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';

import { CES_PAGE_FAVORITES, CesPageHeaderComponent } from './ces-page-header.component';

@Component({
  standalone: true,
  imports: [CesPageHeaderComponent],
  template: `
    <ces-page-header
      title="Veículos"
      icon="pi pi-car"
      description="Situação de cada placa."
      helpLabel="Ajuda sobre veículos"
      [breadcrumb]="[{ label: 'Frota', route: '/frota/veiculos', queryParams: { base: 'b1' } }, { label: 'Veículos' }]"
      (help)="ajudas = ajudas + 1"
    >
      <span status class="selo">3 vencidos</span>
      <button actions type="button" class="novo">Novo veículo</button>
    </ces-page-header>
  `,
})
class HostComponent {
  ajudas = 0;
}

describe('CesPageHeaderComponent', () => {
  it('renderiza trilha, título, selos, ações, ajuda e favorito', async () => {
    const favorito = signal(false);
    const toggle = vi.fn(() => favorito.set(!favorito()));
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideRouter([]), { provide: CES_PAGE_FAVORITES, useValue: { isCurrentPageFavorite: favorito, toggle } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('h1')!.textContent).toBe('Veículos');
    const link = el.querySelector('nav a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/frota/veiculos?base=b1');
    expect(el.querySelector('[aria-current="page"]')!.textContent).toBe('Veículos');
    expect(el.querySelector('.selo')).not.toBeNull();
    expect(el.querySelector('.ces-page-header__actions .novo')).not.toBeNull();

    (el.querySelector('button[aria-label="Ajuda sobre veículos"]') as HTMLButtonElement).click();
    expect(fixture.componentInstance.ajudas).toBe(1);

    (el.querySelector('button[aria-label="Adicionar aos favoritos"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(toggle).toHaveBeenCalledWith('Veículos', 'pi pi-car');
    expect(el.querySelector('button[aria-label="Remover dos favoritos"]')).not.toBeNull();
  });

  it('sem provedor de favoritos e sem rótulo de ajuda, não mostra os botões', async () => {
    await TestBed.configureTestingModule({ imports: [CesPageHeaderComponent], providers: [provideRouter([])] }).compileComponents();
    const fixture = TestBed.createComponent(CesPageHeaderComponent);
    fixture.componentRef.setInput('title', 'Municípios');
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('button')).toBeNull();
    expect(el.querySelector('nav')).toBeNull();
  });
});
