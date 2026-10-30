import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { CesMeterComponent, cesAttainmentBand } from './ces-meter.component';

describe('CesMeterComponent', () => {
  it('classifica faixas de atingimento', () => {
    expect(cesAttainmentBand(100)).toBe('success');
    expect(cesAttainmentBand(80)).toBe('warning');
    expect(cesAttainmentBand(79.9)).toBe('danger');
  });

  it('expõe meter acessível com texto da faixa e satura a barra em 100%', async () => {
    await TestBed.configureTestingModule({ imports: [CesMeterComponent] }).compileComponents();
    const fixture = TestBed.createComponent(CesMeterComponent);
    fixture.componentRef.setInput('value', 125);
    fixture.componentRef.setInput('detail', '50/40');
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.getAttribute('data-tone')).toBe('success');
    const meter = el.querySelector('[role="meter"]')!;
    expect(meter.getAttribute('aria-valuenow')).toBe('100');
    expect(meter.getAttribute('aria-valuetext')).toBe('125% — meta atingida');
    expect(el.textContent).toContain('50/40');

    fixture.componentRef.setInput('value', 42);
    fixture.detectChanges();
    expect(el.getAttribute('data-tone')).toBe('danger');
    expect(el.querySelector('.pi-times-circle')).not.toBeNull();
  });
});
