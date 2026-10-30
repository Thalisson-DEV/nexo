import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { DashboardDrillComponent, DrillRecorte } from './dashboard-drill.component';

interface Linha { id: string; nome: string; valor: number }

function recorte(parcial: Partial<DrillRecorte<Linha>> = {}): DrillRecorte<Linha> {
  return {
    chave: 'fornecedor:f1',
    eyebrow: 'Fornecedor',
    titulo: 'Fornecedor Alfa',
    kpis: [{ rotulo: 'Pedidos', valor: '12' }],
    colunas: [
      { key: 'nome', header: 'Pedido' },
      { key: 'valor', header: 'Valor', type: 'currency' },
    ],
    carregar: () => of({ linhas: [{ id: '1', nome: 'Pedido 1', valor: 10 }], total: 40 }),
    ...parcial,
  };
}

async function montar(r: DrillRecorte<Linha>) {
  await TestBed.configureTestingModule({ imports: [DashboardDrillComponent] }).compileComponents();
  const fixture = TestBed.createComponent(DashboardDrillComponent);
  fixture.componentRef.setInput('recorte', r);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

describe('DashboardDrillComponent', () => {
  it('carrega o recorte uma vez por chave, mostra KPIs, linhas e aviso de parcial', async () => {
    const carregar = vi.fn(() => of({ linhas: [{ id: '1', nome: 'Pedido 1', valor: 10 }], total: 40 }));
    const verTudo = vi.fn();
    const fixture = await montar(recorte({ carregar, verTudo }));
    const drawer = document.body.querySelector('.p-drawer') as HTMLElement;
    expect(carregar).toHaveBeenCalledOnce();
    expect(drawer.textContent).toContain('Fornecedor Alfa');
    expect(drawer.textContent).toContain('Pedidos');
    expect(drawer.textContent).toContain('Pedido 1');
    expect(drawer.textContent).toContain('Mostrando 1 de 40');

    fixture.componentRef.setInput('recorte', { ...recorte({ carregar, verTudo }), kpis: [] });
    fixture.detectChanges();
    expect(carregar).toHaveBeenCalledOnce();

    (Array.from(drawer.querySelectorAll('button')).find((b) => b.textContent?.includes('Ver todos na listagem')) as HTMLButtonElement).click();
    expect(verTudo).toHaveBeenCalledOnce();
    drawer.remove();
  });

  it('mostra erro com nova tentativa', async () => {
    const carregar = vi.fn<() => ReturnType<DrillRecorte<Linha>['carregar']>>(() => throwError(() => new Error('x')));
    await montar(recorte({ chave: 'outra', carregar }));
    const drawer = document.body.querySelector('.p-drawer') as HTMLElement;
    expect(drawer.textContent).toContain('Não foi possível carregar o recorte');
    carregar.mockReturnValue(of({ linhas: [], total: 0 }));
    (drawer.querySelector('ces-async-state button') as HTMLButtonElement).click();
    expect(carregar).toHaveBeenCalledTimes(2);
    drawer.remove();
  });
});
