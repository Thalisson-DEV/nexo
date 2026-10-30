import { Component, OnInit } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';
import { DrawerUrlMode, bindDrawerToUrl } from './drawer-state';
import { NavigationHistoryService } from './navigation-history.service';
import { openRoute, settleNavigation } from './testing';

@Component({ standalone: true, template: '' })
class ListaComDrawer implements OnInit {
  readonly drawer = bindDrawerToUrl();
  readonly eventos: [DrawerUrlMode | null, string | null][] = [];
  ngOnInit() {
    this.drawer.connect((modo, id) => this.eventos.push([modo, id]));
  }
}

describe('bindDrawerToUrl', () => {
  const abrir = async (url: string) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', component: ListaComDrawer }]), provideLocationMocks()],
    });
    const fixture = await openRoute(url, ListaComDrawer);
    return fixture.componentInstance;
  };

  afterEach(() => TestBed.resetTestingModule());

  it('abrir empilha a URL preservando os filtros; o voltar do navegador fecha o drawer', async () => {
    const tela = await abrir('/contratos?res=1&pag=2');
    const router = TestBed.inject(Router);

    tela.drawer.openEdit('c-9');
    await settleNavigation();
    expect(router.url).toBe('/contratos?res=1&pag=2&editar=c-9');
    expect(tela.drawer.mode()).toBe('editar');

    TestBed.inject(NavigationHistoryService).back();
    await settleNavigation();
    expect(router.url).toBe('/contratos?res=1&pag=2');
    expect(tela.drawer.open()).toBe(false);
    expect(tela.eventos.at(-1)).toEqual([null, null]);
  });

  it('link direto (ou F5) com ?editar reabre; fechar troca a URL no lugar', async () => {
    const tela = await abrir('/contratos?editar=c-1');
    expect(tela.eventos).toEqual([['editar', 'c-1']]);

    tela.drawer.close();
    await settleNavigation();
    expect(TestBed.inject(Router).url).toBe('/contratos');
    expect(TestBed.inject(NavigationHistoryService).canGoBack()).toBe(false);
  });

  it('novo usa ?novo=1', async () => {
    const tela = await abrir('/contratos');
    tela.drawer.openNew();
    await settleNavigation();
    expect(TestBed.inject(Router).url).toBe('/contratos?novo=1');
    expect(tela.eventos.at(-1)).toEqual(['novo', null]);
  });
});
