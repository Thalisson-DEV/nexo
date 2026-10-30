import { Component } from '@angular/core';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NavigationHistoryService } from './navigation-history.service';

@Component({ template: '', standalone: true })
class TelaComponent {}

const routes = [
  { path: 'lista', component: TelaComponent },
  { path: 'lista/:id', component: TelaComponent },
  {
    path: 'turnos/:id/detalhamento',
    component: TelaComponent,
    data: { backFallback: '/lista?turno=:id' },
  },
  { path: '**', component: TelaComponent },
];

/** O Router reage ao popstate num setTimeout: espera a navegação assentar. */
async function settle(): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await TestBed.inject(Router).navigated;
  }
}

const janelaOriginal = { opener: window.opener, closed: window.closed };

function definirJanela(valores: { opener?: unknown; closed?: boolean }): void {
  for (const [chave, valor] of Object.entries(valores)) {
    Object.defineProperty(window, chave, { value: valor, configurable: true, writable: true });
  }
}

describe('NavigationHistoryService', () => {
  let router: Router;
  let location: Location;
  let history: NavigationHistoryService;

  function setup(): void {
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideLocationMocks()] });
    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
    history = TestBed.inject(NavigationHistoryService);
    history.start();
    // Fora do bootstrap, é o initialNavigation que liga o Router ao popstate.
    router.initialNavigation();
  }

  beforeEach(() => {
    sessionStorage.clear();
    setup();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    sessionStorage.clear();
    vi.restoreAllMocks();
    definirJanela(janelaOriginal);
  });

  it('deep link: sem entrada anterior, back() usa a rota pai como fallback', async () => {
    await router.navigateByUrl('/lista/42');
    expect(history.canGoBack()).toBe(false);

    history.back();
    await settle();
    expect(location.path()).toBe('/lista');
  });

  it('com histórico interno, back() usa Location.back() e restaura a URL exata', async () => {
    await router.navigateByUrl('/lista?status=A&res=1&pag=3');
    await router.navigateByUrl('/lista/42');
    expect(history.canGoBack()).toBe(true);
    const back = vi.spyOn(location, 'back');

    history.back();
    await settle();

    expect(back).toHaveBeenCalledOnce();
    expect(location.path()).toBe('/lista?status=A&res=1&pag=3');
    expect(history.wasRestored()).toBe(true);
    expect(history.canGoBack()).toBe(false);
  });

  it('replaceUrl não empilha; push empilha; avançar volta à entrada seguinte', async () => {
    await router.navigateByUrl('/lista');
    const idLista = history.currentEntryId();
    await router.navigate(['/lista'], { queryParams: { pag: 2 }, replaceUrl: true });
    expect(history.currentEntryId()).toBe(idLista);
    expect(history.canGoBack()).toBe(false);

    await router.navigateByUrl('/lista/1');
    await router.navigateByUrl('/lista/2');
    expect(history.previousEntry()?.url).toBe('/lista/1');

    location.back();
    await settle();
    expect(location.path()).toBe('/lista/1');
    location.forward();
    await settle();
    expect(location.path()).toBe('/lista/2');
    expect(history.wasRestored()).toBe(true);
  });

  it('sub-relatórios desempilham um nível por vez', async () => {
    await router.navigateByUrl('/lista?res=1');
    await router.navigateByUrl('/lista/1');
    await router.navigateByUrl('/lista/2');

    history.back();
    await settle();
    expect(location.path()).toBe('/lista/1');
    history.back();
    await settle();
    expect(location.path()).toBe('/lista?res=1');
  });

  it('data.backFallback tem precedência e recebe os parâmetros da rota', async () => {
    await router.navigateByUrl('/turnos/7/detalhamento');
    expect(history.resolveFallback()).toBe('/lista?turno=7');
    history.back();
    await settle();
    expect(location.path()).toBe('/lista?turno=7');
  });

  it('fallback explícito do botão vence o da rota', async () => {
    await router.navigateByUrl('/turnos/7/detalhamento');
    history.back('/home');
    await settle();
    expect(location.path()).toBe('/home');
  });

  it('aba aberta pelo app (window.opener da mesma origem) fecha em vez de navegar', async () => {
    await router.navigateByUrl('/lista/42');
    definirJanela({ opener: { location: { origin: window.location.origin } }, closed: true });
    const close = vi.spyOn(window, 'close').mockImplementation(() => undefined);

    history.back();
    expect(close).toHaveBeenCalledOnce();
    expect(location.path()).toBe('/lista/42');
  });

  it('opener de outra origem não fecha a aba: usa o fallback', async () => {
    await router.navigateByUrl('/lista/42');
    definirJanela({
      opener: {
        get location(): Location {
          throw new DOMException('cross-origin', 'SecurityError');
        },
      },
    });
    const close = vi.spyOn(window, 'close').mockImplementation(() => undefined);

    history.back();
    await settle();
    expect(close).not.toHaveBeenCalled();
    expect(location.path()).toBe('/lista');
  });

  it('F5: com a entrada verificada no history.state, o voltar continua usando o histórico', async () => {
    await router.navigateByUrl('/lista?res=1');
    await router.navigateByUrl('/lista/42');
    const estadoDoNavegador = location.getState();
    TestBed.resetTestingModule();

    // Nova carga da página: mesma aba (sessionStorage), mesmo history.state.
    setup();
    vi.spyOn(location, 'getState').mockReturnValue(estadoDoNavegador);
    await router.navigateByUrl('/lista/42', { state: estadoDoNavegador as Record<string, unknown> });
    vi.mocked(location.getState).mockRestore();

    expect(history.canGoBack()).toBe(true);
    expect(history.previousEntry()?.url).toBe('/lista?res=1');
  });

  it('F5 sem entrada verificável (nova aba com storage copiado) cai no fallback', async () => {
    await router.navigateByUrl('/lista?res=1');
    await router.navigateByUrl('/lista/42');
    TestBed.resetTestingModule();

    setup();
    await router.navigateByUrl('/lista/42');
    expect(history.canGoBack()).toBe(false);
  });

  it('reset (logout/login) esquece a pilha', async () => {
    await router.navigateByUrl('/lista');
    await router.navigateByUrl('/lista/1');
    history.reset();
    await router.navigateByUrl('/home');
    expect(history.canGoBack()).toBe(false);
  });

  it('distingue mudança de caminho de mudança só de queryParams', async () => {
    await router.navigateByUrl('/lista');
    await router.navigate(['/lista'], { queryParams: { pag: 2 }, replaceUrl: true });
    expect(history.pathChanged()).toBe(false);
    await router.navigateByUrl('/lista/1');
    expect(history.pathChanged()).toBe(true);
  });
});
