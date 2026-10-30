import '@angular/compiler';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { routes } from '../../app.routes';
import { comModulosOcultos, removerModulosOcultos, rotaOculta } from './modulos-ocultos';

describe('módulos ocultos da distribuição white label', () => {
  it('tira do menu os grupos ocultos em qualquer nível', () => {
    const menu = removerModulosOcultos([
      { label: 'Comunicação', items: [{ label: 'Mural', routerLink: '/mural' }] },
      {
        label: 'Segurança',
        items: [
          { label: 'Gestão', items: [{ label: 'Inspeções', routerLink: '/sst/inspecoes' }] },
          { label: 'CIPA', items: [] },
          { label: 'Brigada de Incêndio', items: [] }
        ]
      },
      { label: 'Treinamento', items: [] }
    ]);

    expect(menu.map((item) => item.label)).toEqual(['Segurança']);
    expect(menu[0].items?.map((item) => item.label)).toEqual(['Gestão']);
  });

  it('bloqueia o endereço direto e as subrotas, sem pegar rotas vizinhas', () => {
    expect(rotaOculta('/sst/governanca/cipa/painel')).toBe(true);
    expect(rotaOculta('mural/123?origem=aviso')).toBe(true);
    expect(rotaOculta('/manuais')).toBe(true);
    expect(rotaOculta('/notificacoes/automacoes/abc/editar')).toBe(true);

    expect(rotaOculta('/sst/inspecoes')).toBe(false);
    expect(rotaOculta('/notificacoes')).toBe(false);
    expect(rotaOculta('/manuais-extra')).toBe(false);
  });

  it('o router aceita as rotas do app com o bloqueio aplicado', () => {
    // Rota com redirectTo não pode ter guard: o Angular recusa a configuração inteira e o
    // app abre em branco.
    TestBed.configureTestingModule({ providers: [provideRouter(comModulosOcultos(routes))] });

    expect(() => TestBed.inject(Router)).not.toThrow();
  });
});
