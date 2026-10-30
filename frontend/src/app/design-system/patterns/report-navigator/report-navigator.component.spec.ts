import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CesReportNavigatorComponent } from './report-navigator.component';
import {
  CesReportNavigatorCriteria,
  CesReportNavigatorDataSource,
  CesReportNavigatorItem,
  CesReportNavigatorPage
} from './report-navigator.models';

interface TestRecord {
  id: string;
  code: string;
  name: string;
  status: string;
  category: string;
  amount: number;
}

class MockDataSource implements CesReportNavigatorDataSource<TestRecord> {
  readonly config = {
    title: 'Registros de Teste',
    icon: 'pi pi-folder',
    scopeOptions: [
      { label: 'Todos', value: 'ALL' },
      { label: 'Cat A', value: 'A' }
    ],
    statusChips: [
      { label: 'Ativo', value: 'ACTIVE', tone: 'success' as const },
      { label: 'Pendente', value: 'PENDING', tone: 'warning' as const }
    ],
    sortOptions: [
      { label: 'Recentes', value: 'recent' },
      { label: 'Antigos', value: 'oldest' }
    ],
    showMineToggle: true,
    searchPlaceholder: 'Buscar registros...'
  };

  mockPage: CesReportNavigatorPage<TestRecord> = {
    items: [
      { id: '1', code: '#001', name: 'Item Um', status: 'ACTIVE', category: 'A', amount: 100 },
      { id: '2', code: '#002', name: 'Item Dois', status: 'PENDING', category: 'A', amount: 250 }
    ],
    totalElements: 2
  };

  loadFn = vi.fn((_c: CesReportNavigatorCriteria) => of(this.mockPage));

  load(criteria: CesReportNavigatorCriteria) {
    return this.loadFn(criteria);
  }

  toItem(raw: TestRecord): CesReportNavigatorItem {
    return {
      id: raw.id,
      number: raw.code,
      title: raw.name,
      value: raw.amount,
      status: { label: raw.status, tone: raw.status === 'ACTIVE' ? 'success' : 'warning' },
      metaAuthor: { label: 'Admin', icon: 'pi pi-user' },
      metaDate: { date: '2026-01-15T10:00:00Z' },
      raw
    };
  }

  getRouteForItem(item: CesReportNavigatorItem | TestRecord) {
    return ['/test', (item as any).id];
  }

  getListRoute() {
    return ['/test/list'];
  }
}

describe('CesReportNavigatorComponent', () => {
  let component: CesReportNavigatorComponent;
  let fixture: ComponentFixture<CesReportNavigatorComponent>;
  let dataSource: MockDataSource;
  let mockRouter: { navigate: ReturnType<typeof vi.fn>; navigateByUrl: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dataSource = new MockDataSource();
    mockRouter = {
      navigate: vi.fn(),
      navigateByUrl: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [CesReportNavigatorComponent],
      providers: [
        provideNoopAnimations(),
        providePrimeNG(),
        { provide: Router, useValue: mockRouter }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CesReportNavigatorComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('dataSource', dataSource);
    fixture.detectChanges();
  });

  it('deve inicializar em estado recolhido por padrão', () => {
    expect(component).toBeTruthy();
    expect(component.opened()).toBe(false);
    const rail = fixture.nativeElement.querySelector('.ces-report-nav-rail');
    expect(rail).not.toBeNull();
  });

  it('deve expandir ao clicar no rail e carregar itens', () => {
    const rail = fixture.nativeElement.querySelector('.ces-report-nav-rail') as HTMLButtonElement;
    rail.click();
    fixture.detectChanges();

    expect(component.opened()).toBe(true);
    expect(dataSource.loadFn).toHaveBeenCalled();

    const items = fixture.nativeElement.querySelectorAll('.ces-report-nav-item');
    expect(items.length).toBe(2);
    expect(fixture.nativeElement.querySelector('h2')?.textContent).toContain('Registros de Teste');
  });

  it('deve alternar com atalho Ctrl + K e fechar com Escape', () => {
    expect(component.opened()).toBe(false);

    const ctrlKEvent = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, cancelable: true });
    window.dispatchEvent(ctrlKEvent);
    fixture.detectChanges();
    expect(component.opened()).toBe(true);

    const escEvent = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true });
    window.dispatchEvent(escEvent);
    fixture.detectChanges();
    expect(component.opened()).toBe(false);
  });

  it('deve alterar status e recarregar', () => {
    component.open();
    fixture.detectChanges();
    dataSource.loadFn.mockClear();

    component.changeStatus('ACTIVE');
    fixture.detectChanges();

    expect(dataSource.loadFn).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'ACTIVE' })
    );
  });

  it('deve alterar escopo e recarregar', () => {
    component.open();
    fixture.detectChanges();
    dataSource.loadFn.mockClear();

    component.changeScope('A');
    fixture.detectChanges();

    expect(dataSource.loadFn).toHaveBeenCalledWith(
      expect.objectContaining({ scope: 'A' })
    );
  });

  it('deve alternar toggle de Minhas e recarregar', () => {
    component.open();
    fixture.detectChanges();
    dataSource.loadFn.mockClear();

    component.toggleMine();
    fixture.detectChanges();

    expect(dataSource.loadFn).toHaveBeenCalledWith(
      expect.objectContaining({ mine: true })
    );
  });

  it('deve emitir itemSelected e navegar ao selecionar um item', () => {
    component.open();
    fixture.detectChanges();

    let itemEmitido: CesReportNavigatorItem | undefined;
    component.itemSelected.subscribe((i) => (itemEmitido = i));

    const item = dataSource.toItem(dataSource.mockPage.items[0]);
    component.select(item);

    expect(itemEmitido?.id).toBe('1');
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/test', '1']);
  });

  it('deve navegar para a listagem completa ao chamar navigateToListAll', () => {
    component.open();
    fixture.detectChanges();

    component.navigateToListAll();
    expect(component.opened()).toBe(false);
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/test/list']);
  });

  it('deve exibir estado de erro em falha de carregamento', () => {
    dataSource.loadFn = vi.fn(() => throwError(() => new Error('Falha na comunicação')));
    component.open();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Falha na comunicação');
  });

  it('deve incluir item atual no topo se não estiver na página carregada', () => {
    const currentItem: CesReportNavigatorItem = {
      id: '99',
      number: '#099',
      title: 'Item Atual Injetado'
    };

    fixture.componentRef.setInput('currentId', '99');
    fixture.componentRef.setInput('currentItem', currentItem);
    component.open();
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('.ces-report-nav-item');
    expect(items.length).toBe(3);
    expect(fixture.nativeElement.querySelector('[aria-current="page"]')?.textContent).toContain('#099');
  });
});
