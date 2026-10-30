import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { providePrimeNG } from 'primeng/config';
import { Dialog } from 'primeng/dialog';

import { CesDialogShellComponent } from './ces-dialog-shell.component';

describe('CesDialogShellComponent', () => {
  it('impede fechamento enquanto uma operação está em andamento', async () => {
    await TestBed.configureTestingModule({
      imports: [CesDialogShellComponent],
      providers: [provideNoopAnimations(), providePrimeNG()]
    }).compileComponents();
    const fixture = TestBed.createComponent(CesDialogShellComponent);
    fixture.componentRef.setInput('title', 'Salvar alterações');
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('loading', true);
    fixture.componentRef.setInput('dismissableMask', true);
    fixture.detectChanges();

    const dialog = fixture.debugElement.query(By.directive(Dialog)).componentInstance as Dialog;
    expect(dialog.closable).toBe(false);
    expect(dialog.closeOnEscape).toBe(false);
    expect(dialog.dismissableMask).toBe(false);
    expect(document.body.textContent).toContain('Processando');
  });

  it('sincroniza o fechamento iniciado pelo PrimeNG', async () => {
    await TestBed.configureTestingModule({
      imports: [CesDialogShellComponent],
      providers: [provideNoopAnimations(), providePrimeNG()]
    }).compileComponents();
    const fixture = TestBed.createComponent(CesDialogShellComponent);
    const dialog = fixture.debugElement.query(By.directive(Dialog)).componentInstance as Dialog;
    dialog.visibleChange.emit(false);
    fixture.detectChanges();

    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('aplica as classes de estilo padrão ces-dialog e ces-dialog-shell', async () => {
    await TestBed.configureTestingModule({
      imports: [CesDialogShellComponent],
      providers: [provideNoopAnimations(), providePrimeNG()]
    }).compileComponents();
    const fixture = TestBed.createComponent(CesDialogShellComponent);
    fixture.componentRef.setInput('title', 'Diálogo de teste');
    fixture.componentRef.setInput('size', 'lg');
    fixture.componentRef.setInput('visible', true);
    fixture.detectChanges();

    const dialog = fixture.debugElement.query(By.directive(Dialog)).componentInstance as Dialog;
    expect(dialog.styleClass).toContain('ces-dialog');
    expect(dialog.styleClass).toContain('ces-dialog-shell');
    expect(dialog.styleClass).toContain('ces-dialog-shell--lg');
  });
});
