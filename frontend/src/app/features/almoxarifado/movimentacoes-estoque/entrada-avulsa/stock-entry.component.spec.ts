import '@angular/compiler';
import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MessageService, ConfirmationService } from 'primeng/api';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { StockEntryComponent } from './stock-entry.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('StockEntryComponent', () => {
  let fixture: ComponentFixture<StockEntryComponent>;
  let component: StockEntryComponent;
  let router: Router;

  const almoxServiceMock = {
    listarEstoquesAtivos: vi.fn(() =>
      of({
        content: [
          { id: 'est-central', nomeIdentificador: 'Almox Central', ativo: true },
        ],
      })
    ),
    buscarMaterialPorId: vi.fn((id: string) =>
      of({
        id,
        codigo: '2001',
        descricao: 'Cabo 10mm',
        unidadeMedida: 'M',
        detalhaSerie: false,
        ativo: true,
      })
    ),
    registrarEntradaLote: vi.fn(() =>
      of({
        idReferenciaOperacao: 'op-entrada-777',
        tipo: 'ENTRADA_AVULSA',
        dataHora: '2026-09-23T11:00:00Z',
        operadorNome: 'Admin',
        itens: [],
      })
    ),
    executarMovimentacaoBulk: vi.fn(() =>
      of({
        idReferenciaOperacao: 'op-entrada-777',
        tipo: 'ENTRADA_AVULSA',
        dataHora: '2026-09-23T11:00:00Z',
        operadorNome: 'Admin',
        itens: [],
      })
    ),
  };

  const lookupStoreMock = {
    searchMaterials: vi.fn(() =>
      of({
        items: [
          {
            value: 'mat-1',
            label: 'Cabo 10mm',
            code: '2001',
            description: 'Cabo 10mm',
            unit: 'M',
            hasSerial: false,
          },
        ],
        nextCursor: null,
      })
    ),
  };

  beforeEach(async () => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [StockEntryComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA],
      providers: [
        provideRouter([]),
        { provide: AlmoxarifadoService, useValue: almoxServiceMock },
        { provide: LookupStore, useValue: lookupStoreMock },
        { provide: MessageService, useValue: { add: vi.fn() } },
        { provide: ConfirmationService, useValue: { confirm: vi.fn() } },
      ],
    });

    TestBed.overrideComponent(StockEntryComponent, {
      set: {
        imports: [],
        template: '<div>Entrada Avulsa</div>',
      },
    });

    await TestBed.compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(StockEntryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve inicializar e carregar estoques ativos', () => {
    expect(component).toBeTruthy();
    expect(almoxServiceMock.listarEstoquesAtivos).toHaveBeenCalled();
    expect(component.estoques().length).toBe(1);
  });

  it('não deve permitir avançar Etapa 1 sem selecionar estoque de destino', () => {
    expect(component.isHeaderValid()).toBe(false);

    component.headerForm.patchValue({
      idEstoqueDestino: 'est-central',
      data: new Date(),
      motivo: 'AJUSTE_INVENTARIO',
    });
    expect(component.isHeaderValid()).toBe(true);
  });

  it('deve adicionar material à lista de entrada e limpar a seleção', () => {
    component.selectedMaterialId.set('mat-1');
    component.selectedMaterial.set({
      id: 'mat-1',
      codigo: '2001',
      descricao: 'Cabo 10mm',
      detalhaSerie: false,
      unidadeMedida: 'M',
      ativo: true,
    });
    component.itemForm.patchValue({
      condicao: 'NOVO',
      quantidade: 100,
    });

    component.addItem();
    expect(component.items().length).toBe(1);
    expect(component.items()[0].quantidade).toBe(100);
    expect(component.items()[0].codigo).toBe('2001');
    expect(component.selectedMaterial()).toBeNull();
    expect(component.selectedMaterialId()).toBeNull();
  });

  it('deve buscar e preencher dados do material ao chamar onMaterialChange', () => {
    component.onMaterialChange('mat-1');

    expect(almoxServiceMock.buscarMaterialPorId).toHaveBeenCalledWith('mat-1');
    expect(component.selectedMaterialId()).toBe('mat-1');
    expect(component.selectedMaterial()).toEqual({
      id: 'mat-1',
      codigo: '2001',
      descricao: 'Cabo 10mm',
      unidadeMedida: 'M',
      detalhaSerie: false,
      ativo: true,
    });
  });

  it('deve limpar material selecionado quando onMaterialChange recebe null', () => {
    component.selectedMaterialId.set('mat-1');
    component.selectedMaterial.set({
      id: 'mat-1',
      codigo: '2001',
      descricao: 'Cabo 10mm',
      detalhaSerie: false,
      unidadeMedida: 'M',
      ativo: true,
    });

    component.onMaterialChange(null);

    expect(component.selectedMaterialId()).toBeNull();
    expect(component.selectedMaterial()).toBeNull();
  });

  it('deve remover material da lista', () => {
    component.items.set([
      {
        idMaterial: 'mat-1',
        codigo: '2001',
        descricao: 'Cabo 10mm',
        quantidade: 100,
        condicao: 'NOVO',
        detalhaSerie: false,
      },
    ]);

    component.removeItem(0);
    expect(component.items().length).toBe(0);
  });
});
