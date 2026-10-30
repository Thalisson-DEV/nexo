import { Component } from '@angular/core';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, RouterOutlet } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';
import { NavigationHistoryService } from '../../core/navigation/navigation-history.service';
import { ScrollMemoryDirective } from './scroll-memory.directive';

@Component({ template: '', standalone: true })
class Tela {}

@Component({
  template: `<main appScrollMemory="main"><router-outlet /></main>`,
  standalone: true,
  imports: [RouterOutlet, ScrollMemoryDirective],
})
class Shell {}

/** jsdom não calcula layout: simulamos um container de 2000px com janela de 500px. */
function simularLayout(element: HTMLElement): void {
  Object.defineProperty(element, 'scrollHeight', { configurable: true, get: () => 2000 });
  Object.defineProperty(element, 'clientHeight', { configurable: true, get: () => 500 });
}

async function settle(): Promise<void> {
  for (let i = 0; i < 5; i++) await new Promise((resolve) => setTimeout(resolve, 20));
}

describe('ScrollMemoryDirective', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    sessionStorage.clear();
  });

  it('restaura a rolagem do <main> ao voltar e começa do topo em navegação nova', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', component: Tela }]),
        provideLocationMocks(),
      ],
    });
    TestBed.inject(NavigationHistoryService).start();
    const router = TestBed.inject(Router);
    router.initialNavigation();
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    const main = fixture.nativeElement.querySelector('main') as HTMLElement;
    simularLayout(main);

    await router.navigateByUrl('/lista?res=1');
    main.scrollTop = 840;

    await router.navigateByUrl('/lista/1');
    await settle();
    expect(main.scrollTop).toBe(0);

    // Paginação (só queryParams) mantém a posição.
    main.scrollTop = 120;
    await router.navigate(['/lista/1'], { queryParams: { pag: 2 }, replaceUrl: true });
    await settle();
    expect(main.scrollTop).toBe(120);

    TestBed.inject(Location).back();
    await settle();
    expect(main.scrollTop).toBe(840);
  });
});
