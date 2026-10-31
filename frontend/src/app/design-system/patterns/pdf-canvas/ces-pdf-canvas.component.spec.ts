import { Component, signal, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { CesPdfCanvasComponent, CesPdfLoader } from './ces-pdf-canvas.component';
import {
  cesPdfDaTela,
  cesPdfOrdenarTexto,
  cesPdfParaTela,
  limitarZoom,
  normalizarRotacao
} from './ces-pdf-canvas.geometry';

describe('geometria do ces-pdf-canvas', () => {
  it('ida e volta entre página e tela preserva a posição em qualquer rotação', () => {
    for (const rotacao of [0, 90, 180, 270]) {
      const volta = cesPdfDaTela(cesPdfParaTela({ x: 0.2, y: 0.7 }, rotacao), rotacao);
      expect(volta.x).toBeCloseTo(0.2);
      expect(volta.y).toBeCloseTo(0.7);
    }
  });

  it('girar 90° à direita leva o canto superior esquerdo ao superior direito', () => {
    expect(cesPdfParaTela({ x: 0, y: 0 }, 90)).toEqual({ x: 1, y: 0 });
  });

  it('normaliza rotação e limita o zoom de 25% a 400%', () => {
    expect(normalizarRotacao(-90)).toBe(270);
    expect(normalizarRotacao(450)).toBe(90);
    expect(limitarZoom(10)).toBe(4);
    expect(limitarZoom(0.01)).toBe(0.25);
  });

  it('ordena o texto por linha e depois por coluna', () => {
    expect(cesPdfOrdenarTexto([
      { texto: 'B', x: 0.5, y: 0.1, altura: 0.02 },
      { texto: 'C', x: 0.1, y: 0.3, altura: 0.02 },
      { texto: 'A', x: 0.1, y: 0.105, altura: 0.02 },
      { texto: '  ', x: 0.9, y: 0.9, altura: 0.02 }
    ])).toBe('A B\nC');
  });
});

@Component({
  imports: [CesPdfCanvasComponent],
  template: `
    <ces-pdf-canvas [source]="fonte()" [(page)]="pagina" [(zoom)]="zoom" (pageCount)="paginas.set($event)" (loadError)="erro.set(true)">
      <div pdf-overlay class="sobre-a-folha">marcador</div>
    </ces-pdf-canvas>
  `
})
class HostComponent {
  readonly fonte = signal<ArrayBuffer | null>(null);
  readonly pagina = signal(1);
  readonly zoom = signal(1);
  readonly paginas = signal(0);
  readonly erro = signal(false);
  readonly canvas = viewChild.required(CesPdfCanvasComponent);
}

describe('CesPdfCanvasComponent', () => {
  function documentoFalso() {
    const viewport = { width: 800, height: 600, convertToViewportPoint: (x: number, y: number) => [x, y] };
    const pagina = {
      rotate: 0,
      getViewport: vi.fn(() => viewport),
      getTextContent: vi.fn(async () => ({
        items: [
          { str: 'K051159', transform: [1, 0, 0, 1, 400, 300], height: 10 },
          { str: 'FORA', transform: [1, 0, 0, 1, 10, 10], height: 10 }
        ]
      })),
      render: vi.fn(() => ({ promise: Promise.resolve(), cancel: vi.fn() }))
    };
    return { numPages: 3, getPage: vi.fn(async () => pagina), destroy: vi.fn() };
  }

  async function montar(abrir: () => Promise<unknown>): Promise<ComponentFixture<HostComponent>> {
    TestBed.configureTestingModule({ providers: [{ provide: CesPdfLoader, useValue: { abrir } }] });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.fonte.set(new ArrayBuffer(4));
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
    return fixture;
  }

  it('informa o total de páginas e projeta o conteúdo sobre a folha', async () => {
    const fixture = await montar(async () => documentoFalso());

    expect(fixture.componentInstance.paginas()).toBe(3);
    expect(fixture.nativeElement.querySelector('.ces-pdf-overlay .sobre-a-folha')).not.toBeNull();
  });

  it('extrai só o texto dentro da região', async () => {
    const fixture = await montar(async () => documentoFalso());

    const texto = await fixture.componentInstance.canvas().extractText({ x0: 0.4, y0: 0.4, x1: 0.6, y1: 0.6 });

    expect(texto).toBe('K051159');
  });

  it('avisa quando o PDF não abre', async () => {
    const fixture = await montar(async () => { throw new Error('corrompido'); });

    expect(fixture.componentInstance.erro()).toBe(true);
  });

  it('botões de zoom respeitam o limite máximo', async () => {
    const fixture = await montar(async () => documentoFalso());
    const canvas = fixture.componentInstance.canvas();

    for (let i = 0; i < 20; i++) canvas.zoomIn();

    expect(fixture.componentInstance.zoom()).toBe(4);
  });
});
