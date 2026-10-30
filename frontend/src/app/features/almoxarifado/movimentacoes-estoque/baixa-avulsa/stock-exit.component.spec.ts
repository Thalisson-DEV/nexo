import '@angular/compiler';
import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MessageService, ConfirmationService } from 'primeng/api';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { StockExitComponent } from './stock-exit.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('StockExitComponent', () => {
  let fixture: ComponentFixture<StockExitComponent>;
  let component: StockExitComponent;
  let router: Router;

  const almoxServiceMock = {
    listarEstoquesAtivos: vi.fn(() =>
      of({
        content: [
          { id: 'est-origem', nomeIdentificador: 'Almox Central', ativo: true },
        ],
      })
    ),
    listarSaldos: vi.fn(() =>
      of({
        content: [
          {
            id: 'saldo-1',
            material: { id: 'mat-1', codigo: '3001', descricao: 'Chave Fusível', detalhaSerie: false, unidadeMedida: 'UN' },
            condicao: 'USADO',
            quantidade: 10,
          },
        ],
      })
    ),
    listarSeriais: vi.fn(() => of({ content: [] })),
    executarMovimentacaoBulk: vi.fn(() =>
      of({
        idReferenciaOperacao: 'op-baixa-555',
        tipo: 'BAIXA_AVULSA',
        dataHora: '2026-09-23T11:00:00Z',
        operadorNome: 'Admin',
        itens: [],
      })
    ),
  };

  beforeEach(async () => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [StockExitComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA],
      providers: [
        provideRouter([]),
        { provide: AlmoxarifadoService, useValue: almoxServiceMock },
        { provide: MessageService, useValue: { add: vi.fn() } },
        { provide: ConfirmationService, useValue: { confirm: vi.fn() } },
      ],
    });

    TestBed.overrideComponent(StockExitComponent, {
      set: {
        imports: [],
        template: '<div>Baixa Avulsa</div>',
      },
    });

    await TestBed.compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(StockExitComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve inicializar e carregar estoques ativos', () => {
    expect(component).toBeTruthy();
    expect(almoxServiceMock.listarEstoquesAtivos).toHaveBeenCalled();
    expect(component.estoques().length).toBe(1);
  });

  it('não deve permitir avançar Etapa 1 sem selecionar origem e justificativa', () => {
    expect(component.isHeaderValid()).toBe(false);

    component.headerForm.patchValue({
      idEstoque: 'est-origem',
      data: new Date(),
      motivo: 'PERDA_AVARIA',
      observacoes: 'Material quebrado durante manuseio',
    });
    expect(component.isHeaderValid()).toBe(true);
  });

  it('deve buscar saldos da origem ao salvar cabeçalho', () => {
    component.headerForm.patchValue({
      idEstoque: 'est-origem',
      data: new Date(),
      motivo: 'PERDA_AVARIA',
      observacoes: 'Material avariado',
    });

    component.saveHeader();
    expect(almoxServiceMock.listarSaldos).toHaveBeenCalledWith(expect.objectContaining({ idEstoque: 'est-origem' }));
    expect(component.activeStep()).toBe(2);
  });

  it('deve adicionar item de baixa', () => {
    const saldoMock = {
      id: 'saldo-1',
      material: { id: 'mat-1', codigo: '3001', descricao: 'Chave Fusível', detalhaSerie: false, unidadeMedida: 'UN' },
      condicao: 'USADO',
      quantidade: 10,
    };
    component.selectedMaterial.set(saldoMock as any);
    component.itemForm.patchValue({
      idMaterial: 'saldo-1',
      quantidade: 3,
    });

    component.addItem();
    expect(component.items().length).toBe(1);
    expect(component.items()[0].quantidade).toBe(3);
    expect(component.items()[0].codigo).toBe('3001');
  });

  it('deve remover item da lista', () => {
    component.items.set([
      {
        idMaterial: 'mat-1',
        codigo: '3001',
        descricao: 'Chave Fusível',
        quantidade: 3,
        detalhaSerie: false,
        condicao: 'USADO',
      },
    ]);

    component.removeItem(0);
    expect(component.items().length).toBe(0);
  });
});
