import '@angular/compiler';
import { describe, expect, it } from 'vitest';
import { removerModulosOcultos, rotaOculta } from './modulos-ocultos';

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
});
