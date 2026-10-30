import { Component } from '@angular/core';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { NavigationHistoryService } from '../../core/navigation/navigation-history.service';
import { BackDirective } from './back.directive';

@Component({
  template: `
    <button id="padrao" appBack>Voltar</button>
    <button id="explicito" appBack="/home">Voltar</button>
  `,
  standalone: true,
  imports: [BackDirective],
})
class RelatorioHost {}

@Component({ template: '', standalone: true })
class Tela {}

async function settle(): Promise<void> {
  for (let i = 0; i < 3; i++) await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('BackDirective', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    sessionStorage.clear();
  });

  async function setup(): Promise<RouterTestingHarness> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'lista', component: Tela },
          { path: 'lista/:id', component: RelatorioHost },
          { path: '**', component: Tela },
        ]),
        provideLocationMocks(),
      ],
    });
    TestBed.inject(NavigationHistoryService).start();
    TestBed.inject(Router).initialNavigation();
    return RouterTestingHarness.create();
  }

  it('com histórico interno volta para a entrada anterior exata', async () => {
    const harness = await setup();
    await harness.navigateByUrl('/lista?nota=1&res=1&pag=2');
    await harness.navigateByUrl('/lista/9');
    (harness.routeNativeElement!.querySelector('#explicito') as HTMLButtonElement).click();
    await settle();
    expect(TestBed.inject(Location).path()).toBe('/lista?nota=1&res=1&pag=2');
  });

  it('sem histórico usa o fallback explícito, ou a rota pai', async () => {
    const harness = await setup();
    await harness.navigateByUrl('/lista/9');
    (harness.routeNativeElement!.querySelector('#explicito') as HTMLButtonElement).click();
    await settle();
    expect(TestBed.inject(Location).path()).toBe('/home');

    await harness.navigateByUrl('/lista/9');
    TestBed.inject(NavigationHistoryService).reset();
    await harness.navigateByUrl('/lista/9?x=1');
    (harness.routeNativeElement!.querySelector('#padrao') as HTMLButtonElement).click();
    await settle();
    expect(TestBed.inject(Location).path()).toBe('/lista');
  });
});
