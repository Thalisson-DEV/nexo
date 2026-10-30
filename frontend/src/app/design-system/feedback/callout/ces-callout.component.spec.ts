import { TestBed } from '@angular/core/testing';

import { CesCalloutComponent } from './ces-callout.component';

describe('CesCalloutComponent', () => {
  const criar = async () => {
    await TestBed.configureTestingModule({ imports: [CesCalloutComponent] }).compileComponents();
    return TestBed.createComponent(CesCalloutComponent);
  };

  it('anuncia o tom por rótulo textual, não apenas por cor', async () => {
    const fixture = await criar();
    fixture.componentRef.setInput('tone', 'warning');
    fixture.componentRef.setInput('title', 'Troca de senha obrigatória');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.getAttribute('data-tone')).toBe('warning');
    expect(host.textContent).toContain('Atenção');
    expect(host.textContent).toContain('Troca de senha obrigatória');
  });

  it('mantém o ícone decorativo fora da leitura assistiva', async () => {
    const fixture = await criar();
    fixture.detectChanges();

    const icone = (fixture.nativeElement as HTMLElement).querySelector('.ces-callout__icon');
    expect(icone?.getAttribute('aria-hidden')).toBe('true');
    expect(icone?.querySelector('i')?.className).toContain('pi-info-circle');
  });

  it('permite rótulo e ícone próprios sem perder o tom semântico', async () => {
    const fixture = await criar();
    fixture.componentRef.setInput('tone', 'brand');
    fixture.componentRef.setInput('label', 'Regra de ouro');
    fixture.componentRef.setInput('icon', 'pi pi-star');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.getAttribute('data-tone')).toBe('brand');
    expect(host.textContent).toContain('Regra de ouro');
    expect(host.querySelector('.ces-callout__icon i')?.className).toContain('pi-star');
  });
});
