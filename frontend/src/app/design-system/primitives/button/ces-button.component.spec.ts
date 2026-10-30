import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Button } from 'primeng/button';

import { CesButtonComponent } from './ces-button.component';

const createButton = async () => {
  await TestBed.configureTestingModule({ imports: [CesButtonComponent] }).compileComponents();
  const fixture = TestBed.createComponent(CesButtonComponent);
  fixture.componentRef.setInput('label', 'Salvar alterações');
  return fixture;
};

describe('CesButtonComponent', () => {
  it('mapeia a intenção destrutiva para PrimeNG sem expor cor na API', async () => {
    const fixture = await createButton();
    fixture.componentRef.setInput('variant', 'destructive');
    fixture.detectChanges();

    const primeButton = fixture.debugElement.query(By.directive(Button)).componentInstance as Button;
    expect(primeButton.severity).toBe('danger');
  });

  it('bloqueia novas ativações enquanto carrega', async () => {
    const fixture = await createButton();
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.getAttribute('aria-label')).toBe('Salvar alterações');
  });

  it('não sai com autofocus (o p-button marcaria todo botão e o navegador focaria o primeiro)', async () => {
    const fixture = await createButton();
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('button') as HTMLButtonElement).hasAttribute('autofocus')).toBe(false);
  });
});
