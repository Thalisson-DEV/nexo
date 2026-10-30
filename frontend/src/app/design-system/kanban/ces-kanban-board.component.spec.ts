import { TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { providePrimeNG } from 'primeng/config';
import { CesKanbanBoardComponent } from './ces-kanban-board.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('CesKanbanBoardComponent', () => {
  it('instancia o painel kanban com valores padrão', async () => {
    await TestBed.configureTestingModule({
      imports: [CesKanbanBoardComponent],
      providers: [provideNoopAnimations(), providePrimeNG()],
    }).compileComponents();

    const fixture = TestBed.createComponent(CesKanbanBoardComponent);
    expect(fixture.componentInstance).toBeTruthy();
    expect(fixture.componentInstance.columns()).toEqual([]);
    expect(fixture.componentInstance.ariaLabel()).toBe('Painel Kanban');
  });

  it('gerencia início e término de drag', async () => {
    await TestBed.configureTestingModule({
      imports: [CesKanbanBoardComponent],
      providers: [provideNoopAnimations(), providePrimeNG()],
    }).compileComponents();

    const fixture = TestBed.createComponent(CesKanbanBoardComponent);
    const item = { id: 'item-1' };
    fixture.componentInstance.startDrag(item, 'col-1');
    expect(fixture.componentInstance['_dragItem']()).toBe(item);
    expect(fixture.componentInstance['_dragColumnId']()).toBe('col-1');

    fixture.componentInstance.endDrag();
    expect(fixture.componentInstance['_dragItem']()).toBeNull();
    expect(fixture.componentInstance['_dragColumnId']()).toBeNull();
  });

  it('gerencia drag com sub-grupos e emite itemMoved no drop', async () => {
    await TestBed.configureTestingModule({
      imports: [CesKanbanBoardComponent],
      providers: [provideNoopAnimations(), providePrimeNG()],
    }).compileComponents();

    const fixture = TestBed.createComponent(CesKanbanBoardComponent);
    const item = { id: 'card-99' };
    let movedEvent: any = null;
    fixture.componentInstance.itemMoved.subscribe((ev) => (movedEvent = ev));

    fixture.componentInstance.startDrag(item, 'PLANEJAMENTO', 'PENDENTE_PLANEJAMENTO');
    expect(fixture.componentInstance['_dragSubGroupId']()).toBe('PENDENTE_PLANEJAMENTO');

    const targetCol = {
      id: 'SEPARACAO',
      label: 'Separação',
      icon: 'pi pi-box',
      tone: 'info' as const,
      items: [],
      totalCount: 0,
    };

    fixture.componentInstance['onDrop'](targetCol, 'PRONTO_PARA_SEPARACAO');

    expect(movedEvent).toEqual({
      item,
      fromColumnId: 'PLANEJAMENTO',
      toColumnId: 'SEPARACAO',
      fromSubGroupId: 'PENDENTE_PLANEJAMENTO',
      toSubGroupId: 'PRONTO_PARA_SEPARACAO',
    });
  });
});
