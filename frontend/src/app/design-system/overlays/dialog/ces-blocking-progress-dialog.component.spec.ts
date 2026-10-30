import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { providePrimeNG } from 'primeng/config';
import { Dialog } from 'primeng/dialog';
import { vi } from 'vitest';

import { CesBlockingProgressDialogComponent } from './ces-blocking-progress-dialog.component';

describe('CesBlockingProgressDialogComponent', () => {
  it('mostra a operação em um diálogo modal que não pode ser dispensado', async () => {
    await TestBed.configureTestingModule({
      imports: [CesBlockingProgressDialogComponent],
      providers: [provideNoopAnimations(), providePrimeNG()]
    }).compileComponents();

    const fixture = TestBed.createComponent(CesBlockingProgressDialogComponent);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('title', 'Processando importação');
    fixture.componentRef.setInput('statusTitle', 'Confirmando solicitações');
    fixture.detectChanges();

    const dialog = fixture.debugElement.query(By.directive(Dialog)).componentInstance as Dialog;
    expect(dialog.modal).toBe(true);
    expect(dialog.closable).toBe(false);
    expect(dialog.closeOnEscape).toBe(false);
    expect(dialog.visible).toBe(true);
    expect(document.body.textContent).toContain('Confirmando solicitações');
    expect(document.body.textContent).not.toContain('Aguarde um instante.');
  });

  it('mostra mensagens de espera em sequência e reinicia ao abrir novamente', async () => {
    await TestBed.configureTestingModule({
      imports: [CesBlockingProgressDialogComponent],
      providers: [provideNoopAnimations(), providePrimeNG()]
    }).compileComponents();

    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    try {
      const fixture = TestBed.createComponent(CesBlockingProgressDialogComponent);
      fixture.componentRef.setInput('statusMessages', ['Pode demorar um pouco.', 'Processando registros...', 'Obrigado por esperar.']);
      fixture.componentRef.setInput('visible', true);
      fixture.detectChanges();
      expect((fixture.componentInstance as any).currentDescription()).toBe('Pode demorar um pouco.');

      vi.advanceTimersByTime(10000);
      fixture.detectChanges();
      expect((fixture.componentInstance as any).currentDescription()).toBe('Processando registros...');

      fixture.componentRef.setInput('visible', false);
      fixture.detectChanges();
      fixture.componentRef.setInput('visible', true);
      fixture.detectChanges();
      expect((fixture.componentInstance as any).currentDescription()).toBe('Pode demorar um pouco.');
      fixture.destroy();
    } finally {
      vi.useRealTimers();
    }
  });

  it('fecha imediatamente em tempo real quando visible volta a ser false', async () => {
    await TestBed.configureTestingModule({
      imports: [CesBlockingProgressDialogComponent],
      providers: [provideNoopAnimations(), providePrimeNG()]
    }).compileComponents();

    const fixture = TestBed.createComponent(CesBlockingProgressDialogComponent);
    const dialog = fixture.componentInstance as any;
    fixture.componentRef.setInput('visible', true);
    fixture.detectChanges();
    expect(dialog.renderedVisible()).toBe(true);
    expect(dialog.currentTitle()).toBe('Estamos cuidando disso');

    fixture.componentRef.setInput('visible', false);
    fixture.detectChanges();
    expect(dialog.renderedVisible()).toBe(false);
    fixture.destroy();
  });
});
