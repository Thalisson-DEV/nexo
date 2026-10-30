import '@angular/compiler';
import { ElementRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SolicitacaoTecnicaAssinaturaComponent } from './solicitacao-tecnica-assinatura.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('SolicitacaoTecnicaAssinaturaComponent', () => {
  let component: SolicitacaoTecnicaAssinaturaComponent;
  let canvas: HTMLCanvasElement;
  let context: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    TestBed.resetTestingModule();
    context = {
      beginPath: vi.fn(),
      moveTo: vi.fn(),
      lineTo: vi.fn(),
      stroke: vi.fn(),
      closePath: vi.fn(),
      clearRect: vi.fn(),
      setTransform: vi.fn(),
    };
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(context as any);
    vi.spyOn(HTMLCanvasElement.prototype, 'toDataURL').mockReturnValue(
      'data:image/png;base64,assinatura',
    );

    TestBed.configureTestingModule({});
    component = TestBed.runInInjectionContext(
      () => new SolicitacaoTecnicaAssinaturaComponent(),
    );
    canvas = document.createElement('canvas');
    (component as any).label = signal('Almoxarife');
    (component as any).canvas = signal(new ElementRef(canvas));
    component.ngAfterViewInit();
  });

  afterEach(() => vi.restoreAllMocks());

  it('inicia como assinatura física registrada', () => {
    expect(component['physical']()).toBe(true);
    expect(component['captured']()).toBe(true);
  });

  it('redimensiona o canvas visível e emite a assinatura digital desenhada', () => {
    vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({
      width: 320,
      height: 144,
      top: 0,
      right: 320,
      bottom: 144,
      left: 0,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    });
    const emitted: string[] = [];
    component.valueChanged.subscribe((value) => emitted.push(value));
    component['useDigital']();

    const event = {
      pointerId: 1,
      clientX: 20,
      clientY: 30,
    } as PointerEvent;
    Object.defineProperty(canvas, 'setPointerCapture', { value: vi.fn() });
    component['pointerDown'](event);
    component['pointerMove']({ ...event, clientX: 50, clientY: 60 } as PointerEvent);
    component['pointerUp']();

    expect(canvas.width).toBe(Math.floor(320 * Math.max(1, window.devicePixelRatio || 1)));
    expect(context['stroke']).toHaveBeenCalled();
    expect(emitted.at(-1)).toBe('data:image/png;base64,assinatura');
    expect(component['captured']()).toBe(true);
  });
});
