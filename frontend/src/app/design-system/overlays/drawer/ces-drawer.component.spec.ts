import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CesDrawerComponent } from './ces-drawer.component';

@Component({
  standalone: true,
  imports: [CesDrawerComponent],
  template: `
    <ces-drawer [(visible)]="aberto" title="Inspeções em 14/09/2026" eyebrow="Detalhe do dia" description="Recorte com os filtros do painel">
      <p class="conteudo">Lista</p>
      <button drawer-footer class="rodape">Ver tudo</button>
    </ces-drawer>
  `,
})
class HostComponent {
  readonly aberto = signal(true);
}

function mockMatchMedia(matches: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches,
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }));
}

async function montar() {
  await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  const fixture = TestBed.createComponent(HostComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

describe('CesDrawerComponent', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.body.querySelectorAll('.p-drawer').forEach((el) => el.remove());
  });

  it('abre à direita no desktop com título rotulando o conteúdo e rodapé projetado', async () => {
    mockMatchMedia(false);
    await montar();
    const drawer = document.body.querySelector('.p-drawer') as HTMLElement;
    expect(drawer).not.toBeNull();
    expect(drawer.className).toContain('p-drawer-right');
    const titulo = drawer.querySelector('h2.ces-drawer__title') as HTMLElement;
    expect(titulo.textContent).toContain('Inspeções em 14/09/2026');
    const corpo = drawer.querySelector('.ces-drawer__body')!;
    expect(corpo.getAttribute('aria-labelledby')).toBe(titulo.id);
    expect(drawer.querySelector('.conteudo')).not.toBeNull();
    expect(drawer.querySelector('.ces-drawer__footer .rodape')).not.toBeNull();
    expect(drawer.querySelector('.ces-drawer__handle')).toBeNull();
    // Superfície pelos tokens Nexo (o overlay do preset fica claro no tema escuro).
    expect(drawer.getAttribute('style')).toContain('var(--ces-surface)');
    expect(drawer.getAttribute('style')).toContain('var(--ces-foreground)');
  });

  it('vira folha inferior com alça em telas compactas', async () => {
    mockMatchMedia(true);
    await montar();
    const drawer = document.body.querySelector('.p-drawer') as HTMLElement;
    expect(drawer.className).toContain('p-drawer-bottom');
    expect(drawer.querySelector('.ces-drawer__handle')).not.toBeNull();
  });
});
