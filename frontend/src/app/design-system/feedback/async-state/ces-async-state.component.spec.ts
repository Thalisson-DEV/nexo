import { TestBed } from '@angular/core/testing';

import { CesAsyncStateComponent } from './ces-async-state.component';

describe('CesAsyncStateComponent', () => {
  it('distingue dados offline de um estado vazio ou de sucesso', async () => {
    await TestBed.configureTestingModule({ imports: [CesAsyncStateComponent] }).compileComponents();
    const fixture = TestBed.createComponent(CesAsyncStateComponent);
    fixture.componentRef.setInput('state', 'offline-cached');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Dados disponíveis offline');
    expect(fixture.nativeElement.textContent).toContain('última versão salva');
  });

  it('emite intenção de tentar novamente no estado de erro', async () => {
    await TestBed.configureTestingModule({ imports: [CesAsyncStateComponent] }).compileComponents();
    const fixture = TestBed.createComponent(CesAsyncStateComponent);
    fixture.componentRef.setInput('state', 'error');
    fixture.componentRef.setInput('actionLabel', 'Tentar novamente');
    const action = vi.fn();
    fixture.componentInstance.action.subscribe(action);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    expect(action).toHaveBeenCalledOnce();
  });

  it('aceita ícone próprio e uma ação secundária (vazio por filtro)', async () => {
    await TestBed.configureTestingModule({ imports: [CesAsyncStateComponent] }).compileComponents();
    const fixture = TestBed.createComponent(CesAsyncStateComponent);
    fixture.componentRef.setInput('state', 'empty');
    fixture.componentRef.setInput('icon', 'pi-filter-slash');
    fixture.componentRef.setInput('actionLabel', 'Ajustar filtros');
    fixture.componentRef.setInput('secondaryActionLabel', 'Limpar filtros');
    const secundaria = vi.fn();
    fixture.componentInstance.secondaryAction.subscribe(secundaria);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.pi-filter-slash')).not.toBeNull();
    const botoes = fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>;
    expect(botoes).toHaveLength(2);
    botoes[1].click();
    expect(secundaria).toHaveBeenCalledOnce();
  });
});
