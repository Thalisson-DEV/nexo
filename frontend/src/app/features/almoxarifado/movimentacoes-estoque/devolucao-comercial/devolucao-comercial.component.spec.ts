import '@angular/compiler';
import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MessageService, ConfirmationService } from 'primeng/api';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { RhService } from '../../../../core/services/rh.service';
import { DevolucaoComercialComponent } from './devolucao-comercial.component';

describe('DevolucaoComercialComponent', () => {
  let fixture: ComponentFixture<DevolucaoComercialComponent>;
  let component: DevolucaoComercialComponent;
  let router: Router;

  const almoxServiceMock = {
    listarEstoquesAtivos: vi.fn(() =>
      of({
        content: [
          {
            id: 'est-campo-1',
            nomeIdentificador: 'CAMPO - EQ-01',
            ativo: true,
            idEquipe: 'eq-1',
            tipoEstoque: { nome: 'CAMPO' },
          },
          {
            id: 'est-fisico-1',
            nomeIdentificador: 'ALMOXARIFADO CENTRAL',
            ativo: true,
            tipoEstoque: { nome: 'FISICO' },
          },
        ],
      }),
    ),
    listarSaldos: vi.fn(() =>
      of({
        content: [
          {
            id: 'saldo-1',
            material: {
              id: 'mat-1',
              codigo: '3001',
              descricao: 'Cabo de Cobre',
              detalhaSerie: false,
              unidadeMedida: 'M',
            },
            condicao: 'NOVO',
            quantidade: 50,
          },
          {
            id: 'saldo-2',
            material: {
              id: 'mat-2',
              codigo: '4001',
              descricao: 'Medidor Monofásico',
              detalhaSerie: true,
              unidadeMedida: 'UN',
            },
            condicao: 'NOVO',
            quantidade: 5,
          },
          {
            id: 'saldo-3',
            material: {
              id: 'mat-1',
              codigo: '3001',
              descricao: 'Cabo de Cobre',
              detalhaSerie: false,
              unidadeMedida: 'M',
            },
            condicao: 'SUCATA',
            quantidade: 6,
          },
          {
            id: 'saldo-4',
            material: { id: 'mat-9', codigo: '9001', descricao: 'Zerado', detalhaSerie: false, unidadeMedida: 'UN' },
            condicao: 'SUCATA',
            quantidade: 0,
          },
        ],
      }),
    ),
    listarSeriais: vi.fn(() => of({ content: [{ numSerie: 'SN-001' }, { numSerie: 'SN-002' }] })),
    registrarDevolucaoComercial: vi.fn(() => of('uuid-movimentacao-222')),
  };

  const rhServiceMock = {
    listarEquipes: vi.fn(() =>
      of({
        content: [
          { id: 'eq-1', codigo: 'EQ-01', tipoEquipe: { nome: 'Comercial Leve' }, ativo: true },
        ],
      }),
    ),
  };

  const confirmationServiceMock = {
    confirm: vi.fn((opts: any) => opts.accept?.()),
  };

  beforeEach(async () => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [DevolucaoComercialComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA],
      providers: [
        provideRouter([]),
        { provide: AlmoxarifadoService, useValue: almoxServiceMock },
        { provide: RhService, useValue: rhServiceMock },
        { provide: MessageService, useValue: { add: vi.fn() } },
        { provide: ConfirmationService, useValue: confirmationServiceMock },
      ],
    });

    TestBed.overrideComponent(DevolucaoComercialComponent, {
      set: {
        imports: [],
        template: '<div>Devolucao Comercial</div>',
      },
    });

    await TestBed.compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(DevolucaoComercialComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve inicializar e carregar estoques e equipes', () => {
    expect(component).toBeTruthy();
    expect(almoxServiceMock.listarEstoquesAtivos).toHaveBeenCalled();
    expect(rhServiceMock.listarEquipes).toHaveBeenCalled();
    expect(component.equipes().length).toBe(1);
    expect(component.physicalStocks().length).toBe(1);
  });

  it('deve validar cabeçalho apenas quando equipe possui estoque de campo e destino físico for selecionado', () => {
    expect(component.isHeaderValid()).toBe(false);

    // Seleciona equipe vinculada ao estoque de campo
    component.headerForm.patchValue({
      idEquipe: 'eq-1',
      idEstoqueDestino: 'est-fisico-1',
      data: new Date(),
    });

    expect(component.estoqueCampoEquipe()?.id).toBe('est-campo-1');
    expect(component.isHeaderValid()).toBe(true);
  });

  it('deve buscar saldos do estoque de campo da equipe ao avançar para a Etapa 2', () => {
    component.headerForm.patchValue({
      idEquipe: 'eq-1',
      idEstoqueDestino: 'est-fisico-1',
      data: new Date(),
    });

    component.advanceToStep2();
    expect(almoxServiceMock.listarSaldos).toHaveBeenCalledWith(
      expect.objectContaining({ idEstoque: 'est-campo-1' }),
    );
    expect(component.activeStep()).toBe(2);
    expect(component.saldos().length).toBe(4);
  });

  function irParaMateriais() {
    component.headerForm.patchValue({
      idEquipe: 'eq-1',
      idEstoqueDestino: 'est-fisico-1',
      data: new Date(),
    });
    component.advanceToStep2();
  }

  it('lista só os saldos da condição escolhida', () => {
    irParaMateriais();

    expect(component.materialOptions().map((o) => o.value)).toEqual(['saldo-1', 'saldo-2']);
    expect(component.contagemPorCondicao()).toEqual({ NOVO: 2, SUCATA: 1 });

    component.selecionarCondicao('SUCATA');
    expect(component.materialOptions().map((o) => o.value)).toEqual(['saldo-3']);
  });

  it('a condição do item vem do saldo, e sucata sai do destino por padrão', () => {
    irParaMateriais();
    component.selecionarCondicao('SUCATA');
    component.onMaterialChange('saldo-3');
    component.quantidade.set(4);

    expect(component.guardarNoEstoque()).toBe(false);
    component.addItem();

    expect(component.items()[0]).toEqual(
      expect.objectContaining({ idMaterial: 'mat-1', condicao: 'SUCATA', quantidade: 4, guardarNoEstoque: false }),
    );
    // O mesmo material e condição não aparece de novo na lista.
    expect(component.materialOptions()).toEqual([]);
  });

  it('não deixa incluir mais do que o saldo da condição', () => {
    irParaMateriais();
    component.selecionarCondicao('SUCATA');
    component.onMaterialChange('saldo-3');
    component.quantidade.set(8);

    expect(component.podeIncluir()).toBe(false);
  });

  it('busca as séries filtrando pela condição e inclui as marcadas', () => {
    irParaMateriais();
    component.onMaterialChange('saldo-2');

    expect(almoxServiceMock.listarSeriais).toHaveBeenCalledWith(
      expect.objectContaining({ idEstoque: 'est-campo-1', idMaterial: 'mat-2', condicao: 'NOVO', ativo: true }),
    );
    expect(component.seriesDisponiveis()).toEqual(['SN-001', 'SN-002']);

    component.seriesSelecionadas.set(['SN-001', 'SN-002']);
    component.addItem();

    expect(component.items()[0]).toEqual(
      expect.objectContaining({ numSeries: ['SN-001', 'SN-002'], quantidade: 2, guardarNoEstoque: true }),
    );
    expect(component.hasMissingSerials()).toBe(false);
  });

  it('editar as séries de um item ajusta a quantidade', () => {
    irParaMateriais();
    component.onMaterialChange('saldo-2');
    component.seriesSelecionadas.set(['SN-001', 'SN-002']);
    component.addItem();

    component.openSerialManager(0);
    component.seriesDialogSelecionadas.set(['SN-002']);
    component.saveSerials();

    expect(component.items()[0]).toEqual(expect.objectContaining({ numSeries: ['SN-002'], quantidade: 1 }));
  });

  it('deve detectar séries pendentes para materiais que detalham série', () => {
    component.items.set([
      {
        idMaterial: 'mat-2',
        codigo: '4001',
        descricao: 'Medidor Monofásico',
        quantidade: 2,
        detalhaSerie: true,
        condicao: 'NOVO',
        guardarNoEstoque: true,
        numSeries: ['SN-001'],
      },
    ]);

    expect(component.hasMissingSerials()).toBe(true);
  });

  it('deve confirmar e submeter a devolução comercial tipo 222 com sucesso', () => {
    component.headerForm.patchValue({
      idEquipe: 'eq-1',
      idEstoqueDestino: 'est-fisico-1',
      data: new Date(),
      justificativa: 'Devolução de sobras comerciais',
    });

    component.items.set([
      {
        idMaterial: 'mat-1',
        codigo: '3001',
        descricao: 'Cabo de Cobre',
        quantidade: 10,
        detalhaSerie: false,
        condicao: 'NOVO',
        guardarNoEstoque: true,
      },
      {
        idMaterial: 'mat-2',
        codigo: '4001',
        descricao: 'Medidor Monofásico',
        quantidade: 2,
        detalhaSerie: true,
        condicao: 'SUCATA',
        guardarNoEstoque: false,
        numSeries: ['SN-001', 'SN-002'],
      },
    ]);

    component.confirmDevolucao();

    expect(confirmationServiceMock.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        message: expect.stringContaining('1 item(ns) não ficarão no almoxarifado'),
        acceptButtonStyleClass: '!bg-green-600 !border-green-600',
        rejectButtonStyleClass: '!bg-red-600 !border-red-600',
      }),
    );

    expect(almoxServiceMock.registrarDevolucaoComercial).toHaveBeenCalledWith(
      expect.objectContaining({
        idEquipe: 'eq-1',
        idEstoqueDestino: 'est-fisico-1',
        observacao: 'Devolução de sobras comerciais',
        itens: [
          {
            idMaterial: 'mat-1',
            quantidade: 10,
            condicao: 'NOVO',
            numSerie: undefined,
            guardarNoEstoque: true,
          },
          { idMaterial: 'mat-2', quantidade: 1, condicao: 'SUCATA', numSerie: 'SN-001', guardarNoEstoque: false },
          { idMaterial: 'mat-2', quantidade: 1, condicao: 'SUCATA', numSerie: 'SN-002', guardarNoEstoque: false },
        ],
      }),
    );
  });
});
