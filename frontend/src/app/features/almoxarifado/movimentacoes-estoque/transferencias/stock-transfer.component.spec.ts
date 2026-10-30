import '@angular/compiler';
import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MessageService, ConfirmationService } from 'primeng/api';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { StockTransferComponent } from './stock-transfer.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('StockTransferComponent', () => {
  let fixture: ComponentFixture<StockTransferComponent>;
  let component: StockTransferComponent;
  let router: Router;

  const almoxServiceMock = {
    listarEstoquesAtivos: vi.fn(() =>
      of({
        content: [
          { id: 'est-origem', nomeIdentificador: 'Almox Central', ativo: true },
          { id: 'est-destino', nomeIdentificador: 'Base Sul', ativo: true },
        ],
      })
    ),
    listarSaldos: vi.fn(() =>
      of({
        content: [
          {
            id: 'saldo-1',
            material: { id: 'mat-1', codigo: '1001', descricao: 'Poste Concreto', detalhaSerie: false, unidadeMedida: 'UN' },
            condicao: 'NOVO',
            quantidade: 20,
          },
        ],
      })
    ),
    listarSeriais: vi.fn(() => of({ content: [] })),
    registrarTransferenciaLote: vi.fn(() => of('op-transf-999')),
    executarMovimentacaoBulk: vi.fn(() =>
      of({
        idReferenciaOperacao: 'op-transf-999',
        tipo: 'TRANSFERENCIA',
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
      imports: [StockTransferComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA],
      providers: [
        provideRouter([]),
        { provide: AlmoxarifadoService, useValue: almoxServiceMock },
        { provide: MessageService, useValue: { add: vi.fn() } },
        { provide: ConfirmationService, useValue: { confirm: vi.fn() } },
      ],
    });

    TestBed.overrideComponent(StockTransferComponent, {
      set: {
        imports: [],
        template: '<div>Transferência entre Estoques</div>',
      },
    });

    await TestBed.compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(StockTransferComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve inicializar e carregar estoques ativos', () => {
    expect(component).toBeTruthy();
    expect(almoxServiceMock.listarEstoquesAtivos).toHaveBeenCalled();
    expect(component.estoques().length).toBe(2);
  });

  it('deve validar integridade do cabeçalho da transferência', () => {
    expect(component.isHeaderValid()).toBe(false);

    component.headerForm.patchValue({
      idEstoqueOrigem: 'est-origem',
      idEstoqueDestino: 'est-origem',
    });
    expect(component.isHeaderValid()).toBe(false); // Origem e destino iguais

    component.headerForm.patchValue({
      idEstoqueDestino: 'est-destino',
    });
    expect(component.isHeaderValid()).toBe(true);
  });

  it('deve avançar para a Etapa 2 ao salvar cabeçalho válido', () => {
    component.headerForm.patchValue({
      idEstoqueOrigem: 'est-origem',
      idEstoqueDestino: 'est-destino',
      data: new Date(),
    });

    component.saveHeader();
    expect(almoxServiceMock.listarSaldos).toHaveBeenCalledWith(expect.objectContaining({ idEstoque: 'est-origem' }));
    expect(component.activeStep()).toBe(2);
  });

  it('deve adicionar item à lista de transferência', () => {
    const saldoMock = {
      id: 'saldo-1',
      material: { id: 'mat-1', codigo: '1001', descricao: 'Poste Concreto', detalhaSerie: false, unidadeMedida: 'UN' },
      condicao: 'NOVO',
      quantidade: 20,
    };
    component.selectedMaterial.set(saldoMock as any);
    component.itemForm.patchValue({
      idMaterial: 'saldo-1',
      quantidade: 5,
    });

    component.addItem();
    expect(component.items().length).toBe(1);
    expect(component.items()[0].quantidade).toBe(5);
    expect(component.items()[0].codigo).toBe('1001');
  });

  it('deve remover item da lista', () => {
    component.items.set([
      {
        idMaterial: 'mat-1',
        codigo: '1001',
        descricao: 'Poste Concreto',
        quantidade: 5,
        detalhaSerie: false,
        condicao: 'NOVO',
      },
    ]);

    component.removeItem(0);
    expect(component.items().length).toBe(0);
  });

  it('deve abrir modal de escolha de tipo de assinatura ao confirmar transferência', () => {
    component.items.set([
      {
        idMaterial: 'mat-1',
        codigo: '1001',
        descricao: 'Poste Concreto',
        quantidade: 5,
        detalhaSerie: false,
        condicao: 'NOVO',
      },
    ]);

    component.confirmTransfer();
    expect(component.displaySignatureTypeModal()).toBe(true);
  });

  it('deve executar transferência com assinatura física diretamente', () => {
    component.headerForm.patchValue({
      idEstoqueOrigem: 'est-origem',
      idEstoqueDestino: 'est-destino',
      data: new Date(),
    });
    component.items.set([
      {
        idMaterial: 'mat-1',
        codigo: '1001',
        descricao: 'Poste Concreto',
        quantidade: 5,
        detalhaSerie: false,
        condicao: 'NOVO',
      },
    ]);

    component.selectSignatureType('FISICA');

    expect(almoxServiceMock.registrarTransferenciaLote).toHaveBeenCalledWith(
      expect.objectContaining({
        idEstoqueOrigem: 'est-origem',
        idEstoqueDestino: 'est-destino',
        assinaturaEntregador: 'FISICA',
        assinaturaRecebedor: 'FISICA',
      }),
    );
  });

  it('deve abrir painel de assinaturas digitais ao escolher tipo digital', () => {
    component.selectSignatureType('DIGITAL');
    expect(component.displayDigitalSignatureDashboard()).toBe(true);
    expect(component.displaySignatureTypeModal()).toBe(false);
  });
});
