import '@angular/compiler';
import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '@ces/ui';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { RhService } from '../../../../core/services/rh.service';
import { SolicitacaoMaterialFormComponent } from './solicitacao-material-form.component';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('SolicitacaoMaterialFormComponent (Comercial)', () => {
  let fixture: ComponentFixture<SolicitacaoMaterialFormComponent>;
  let component: SolicitacaoMaterialFormComponent;

  const criarSolicitacaoMock = vi.fn(() => of('sol-com-1'));
  const criarSolicitacaoDiretaMock = vi.fn(() => of('sol-com-direta-1'));

  const notificationsMock = {
    error: vi.fn(),
    warning: vi.fn(),
    success: vi.fn(),
  };

  beforeEach(async () => {
    TestBed.resetTestingModule();
    criarSolicitacaoMock.mockClear();
    criarSolicitacaoDiretaMock.mockClear();
    notificationsMock.error.mockClear();
    notificationsMock.warning.mockClear();
    notificationsMock.success.mockClear();

    await TestBed.configureTestingModule({
      imports: [SolicitacaoMaterialFormComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA],
      providers: [
        provideRouter([]),
        {
          provide: AlmoxarifadoService,
          useValue: {
            listarEstoquesAtivos: () =>
              of({
                content: [
                  {
                    id: 'stock-origem',
                    nomeIdentificador: 'Almoxarifado Central',
                    ativo: true,
                    tipoEstoque: { id: 'tipo-fisico', nome: 'FISICO', ativo: true },
                  },
                  {
                    id: 'stock-destino',
                    nomeIdentificador: 'EQ-01',
                    ativo: true,
                    idEquipe: 'equipe-1',
                    tipoEstoque: { id: 'tipo-campo', nome: 'CAMPO', ativo: true },
                  },
                ],
              }),
            buscarMaterialPorId: (id: string) =>
              of({
                id,
                codigo: 'MAT01',
                descricao: 'Cabo 10mm',
                unidadeMedida: 'M',
              }),
            criarSolicitacao: criarSolicitacaoMock,
            criarSolicitacaoDireta: criarSolicitacaoDiretaMock,
          },
        },
        {
          provide: RhService,
          useValue: {
            listarEquipes: () =>
              of({
                content: [
                  {
                    id: 'equipe-1',
                    codigo: 'EQ-01',
                    ativo: true,
                    tipoEquipe: { id: 'tipo-1', nome: 'Comercial' },
                    membros: [
                      {
                        idColaborador: 'colab-1',
                        nomeColaborador: 'Colaborador Responsavel',
                        funcao: 'RESPONSAVEL',
                        vinculoAtivo: true,
                      },
                    ],
                  },
                  {
                    id: 'equipe-sem-estoque',
                    codigo: 'EQ-02',
                    ativo: true,
                    tipoEquipe: { id: 'tipo-1', nome: 'Comercial' },
                    membros: [],
                  },
                ],
              }),
            buscarColaboradorPorId: (id: string) =>
              of({
                id,
                idUsuario: 'user-colab-1',
                nomeCompleto: 'Colaborador Responsavel',
              }),
          },
        },
        {
          provide: NotificationService,
          useValue: notificationsMock,
        },
        {
          provide: LookupStore,
          useValue: {
            searchCollaborators: () =>
              of({
                items: [{ value: 'colab-1', label: 'Colaborador Responsavel' }],
                nextCursor: null,
                hasMore: false,
              }),
            searchMaterials: () => of({ items: [], nextCursor: null, hasMore: false }),
          },
        },
      ],
    });

    TestBed.overrideComponent(SolicitacaoMaterialFormComponent, {
      set: {
        imports: [],
        template: '',
      },
    });

    await TestBed.compileComponents();

    fixture = TestBed.createComponent(SolicitacaoMaterialFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function adicionarMaterial(): void {
    component['onMaterialChange']('mat-1');
    component['quantidade'].set(5);
    component['addItem']();
  }

  it('oferece só estoques físicos como origem', () => {
    expect(component['stocks']()).toHaveLength(2);
    expect(component['stockOptions']()).toEqual([
      { label: 'Almoxarifado Central', value: 'stock-origem' },
    ]);
    expect(component['alterado']()).toBe(false);
  });

  it('não envia sem origem e equipe', () => {
    component['onSave']();
    expect(criarSolicitacaoDiretaMock).not.toHaveBeenCalled();
    expect(component['fieldErrors']()['idEstoqueOrigem']).toBeTruthy();
    expect(component['fieldErrors']()['idEquipe']).toBeTruthy();
  });

  it('deriva o estoque CAMPO de destino pela equipe selecionada', () => {
    component['idEstoqueOrigem'].set('stock-origem');
    component['onEquipeChange']('equipe-1');

    expect(component['selectedEstoqueDestino']()?.id).toBe('stock-destino');
    expect(component['selectedEstoqueDestino']()?.tipoEstoque.nome).toBe('CAMPO');
  });

  it('exige a data prevista, mas não bloqueia antecedência menor que 48 horas', () => {
    component['idEstoqueOrigem'].set('stock-origem');
    component['idEquipe'].set('equipe-1');
    adicionarMaterial();

    component['onSave']();
    expect(criarSolicitacaoDiretaMock).not.toHaveBeenCalled();
    expect(component['fieldErrors']()['dataPrevista']).toBeTruthy();

    component['dataPrevista'].set(new Date());
    component['onSave']();
    expect(criarSolicitacaoDiretaMock).toHaveBeenCalledTimes(1);
  });

  it('exige equipe e permite deixar o colaborador para a entrega', () => {
    component['idEstoqueOrigem'].set('stock-origem');
    component['dataPrevista'].set(new Date(2026, 8, 25));
    adicionarMaterial();

    component['onSave']();
    expect(criarSolicitacaoDiretaMock).not.toHaveBeenCalled();
    expect(component['fieldErrors']()['idEquipe']).toBeTruthy();

    component['idEquipe'].set('equipe-1');
    component['onSave']();
    expect(criarSolicitacaoDiretaMock).toHaveBeenCalledTimes(1);
    expect(component['idColaboradorSaque']()).toBeNull();
  });

  it('bloqueia equipe sem estoque de campo ativo', () => {
    component['idEstoqueOrigem'].set('stock-origem');
    component['idEquipe'].set('equipe-sem-estoque');
    component['dataPrevista'].set(new Date(2026, 8, 25));
    adicionarMaterial();

    component['onSave']();

    expect(criarSolicitacaoDiretaMock).not.toHaveBeenCalled();
    expect(component['fieldErrors']()['idEquipe']).toContain('não possui estoque de campo ativo');
    expect(component['destinoRotulo']()).toBe('Equipe sem estoque de campo');
  });

  it('adiciona material, ajusta quantidade e remove da lista', () => {
    component['idEstoqueOrigem'].set('stock-origem');
    component['idEquipe'].set('equipe-1');
    component['dataPrevista'].set(new Date(2026, 8, 25));

    // Adicionar item
    component['onMaterialChange']('mat-1');
    component['quantidade'].set(15.5);
    component['addItem']();

    expect(component['items']()).toHaveLength(1);
    expect(component['items']()[0].idMaterial).toBe('mat-1');
    expect(component['items']()[0].quantidade).toBe(15.5);

    // Atualizar quantidade
    component['updateItemQuantidade'](0, 20);
    expect(component['items']()[0].quantidade).toBe(20);

    // Remover item
    component['removeItem'](0);
    expect(component['items']()).toHaveLength(0);
  });

  it('submete solicitação comercial com equipe e responsável externo opcional', () => {
    component['idEquipe'].set('equipe-1');
    component['saqueForaEquipe'].set(true);
    component['idColaboradorSaque'].set('colab-fora-1');
    component['idEstoqueOrigem'].set('stock-origem');
    component['dataPrevista'].set(new Date(2026, 8, 25));
    component['onMaterialChange']('mat-1');
    component['quantidade'].set(5);
    component['addItem']();

    component['onSave']();

    expect(criarSolicitacaoDiretaMock).toHaveBeenCalledTimes(1);
    expect(criarSolicitacaoDiretaMock).toHaveBeenCalledWith({
      idEstoqueOrigem: 'stock-origem',
      dataPrevista: '2026-09-25',
      idEquipe: 'equipe-1',
      idColaboradorSaque: 'colab-fora-1',
      saqueForaEquipe: true,
      itens: [{ idMaterial: 'mat-1', quantidade: 5 }],
    });
    expect(notificationsMock.success).toHaveBeenCalled();
    expect(component['aberto']()).toBe(false);
  });
});
