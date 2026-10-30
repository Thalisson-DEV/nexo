import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { NavigationHistoryService } from './navigation-history.service';

/**
 * Utilitários para specs de telas cujo estado vive na URL.
 *
 * Use com `provideRouter([{ path: '**', component: Tela }])` e `provideLocationMocks()`.
 */
export interface UrlStateFixture<T> {
  componentInstance: T;
  nativeElement: HTMLElement;
  harness: RouterTestingHarness;
  detectChanges(): void;
}

/** Abre a tela por uma URL real (o estado vem da URL, como no app). */
export async function openRoute<T>(url: string, component: Type<T>): Promise<UrlStateFixture<T>> {
  TestBed.inject(NavigationHistoryService).start();
  TestBed.inject(Router).initialNavigation();
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(url, component);
  await settleNavigation();
  return {
    harness,
    get componentInstance() {
      return harness.routeDebugElement!.componentInstance as T;
    },
    get nativeElement() {
      return harness.routeNativeElement as HTMLElement;
    },
    detectChanges: () => harness.detectChanges(),
  };
}

/** Espera navegações (inclusive as encadeadas por "Pesquisar") e efeitos assentarem. */
export async function settleNavigation(): Promise<void> {
  for (let i = 0; i < 5; i++) {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
}
