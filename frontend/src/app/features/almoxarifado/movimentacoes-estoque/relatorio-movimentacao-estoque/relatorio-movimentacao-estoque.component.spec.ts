import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, beforeEach, it, expect, vi } from 'vitest';

import { CesStatusComponent } from '@ces/ui';
import { RelatorioMovimentacaoEstoqueComponent } from './relatorio-movimentacao-estoque.component';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { RhService } from '../../../../core/services/rh.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { MovimentacaoOperacaoResponse } from '../../../../core/models/almoxarifado.models';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

const mockMovimentacao: MovimentacaoOperacaoResponse = {
  idReferenciaOperacao: '6fdfb235-7dfb-447b-b056-7fb1fa6f2cf0',
  tipo: 'TRANSFERENCIA',
  dataHora: '2026-09-23T14:30:00Z',
  operadorNome: 'Carlos Almoxarife',
  estoqueOrigem: {
    id: 'est-origem-1',
    nomeIdentificador: 'Almox Central Salvador',
    tipoEstoque: { id: 'tipo-1', nome: 'CENTRAL', ativo: true },
    idBaseOperacional: 'base-1',
    ativo: true,
  },
  estoqueDestino: {
    id: 'est-destino-2',
    nomeIdentificador: 'Equipe LV-01',
    tipoEstoque: { id: 'tipo-2', nome: 'MOVEL', ativo: true },
    idEquipe: 'eq-1',
    ativo: true,
  },
  contexto: 'COMERCIAL',
  idSolicitacao: 'sol-12345678-abcd',
  idObra: 'obra-98765432-ef01',
  idEquipe: 'eq-1',
  tipoTecnico: null,
  tipoObra: 'OBRA',
  ocorrencia: 'OCO-2026-99',
  idReserva: 'res-456',
  idOperacao: 'op-789',
  itens: [
    {
      id: 'item-1',
      material: {
        id: 'mat-1',
        codigo: 'CAB-01',
        descricao: 'Cabo Alumínio 50mm',
        unidadeMedida: 'M',
        detalhaSerie: false,
        suprMatrFlag: 'MATR',
        ativo: true,
      } as any,
      quantidade: 150.5,
      condicao: 'NOVO',
      numSerie: undefined,
    },
    {
      id: 'item-2',
      material: {
        id: 'mat-2',
        codigo: 'TRAFO-01',
        descricao: 'Transformador Monofásico 15kVA',
        unidadeMedida: 'UN',
        detalhaSerie: true,
        suprMatrFlag: 'MATR',
        ativo: true,
      } as any,
      quantidade: 1,
      condicao: 'RECUPERADO',
      numSerie: 'TRF-2026-887',
    },
    {
      id: 'item-3',
      material: {
        id: 'mat-2',
        codigo: 'TRAFO-01',
        descricao: 'Transformador Monofásico 15kVA',
        unidadeMedida: 'UN',
        detalhaSerie: true,
        suprMatrFlag: 'MATR',
        ativo: true,
      } as any,
      quantidade: 1,
      condicao: 'RECUPERADO',
      numSerie: 'TRF-2026-101',
    },
  ],
};

describe('RelatorioMovimentacaoEstoqueComponent', () => {
  let component: RelatorioMovimentacaoEstoqueComponent;
  let fixture: ComponentFixture<RelatorioMovimentacaoEstoqueComponent>;
  let almoxarifadoServiceMock: any;
  let rhServiceMock: any;
  let obrasServiceMock: any;
  let router: Router;

  beforeEach(async () => {
    almoxarifadoServiceMock = {
      buscarOperacaoPorIdReferencia: vi.fn().mockReturnValue(of(mockMovimentacao)),
      buscarAssinaturaTransferencia: vi.fn().mockReturnValue(of(new Blob(['fake-img'], { type: 'image/png' }))),
    };

    rhServiceMock = {
      buscarEquipePorId: vi.fn().mockReturnValue(
        of({
          id: 'eq-1',
          codigo: 'LV-01',
          tipoEquipe: { id: 'tipo-eq-1', nome: 'Linha Viva' },
          baseOperacional: { id: 'base-1', nome: 'Base Salvador Central' },
        }),
      ),
      buscarBaseOperacionalPorId: vi.fn().mockReturnValue(
        of({
          id: 'base-1',
          nome: 'Base Salvador Central',
        }),
      ),
    };

    obrasServiceMock = {
      buscarObraPorId: vi.fn().mockReturnValue(
        of({
          id: 'obra-98765432-ef01',
          numero: 'OBR-2026-0042',
          descricao: 'Extensão de Rede MT',
        }),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [RelatorioMovimentacaoEstoqueComponent, CesStatusComponent],
      providers: [
        provideRouter([]),
        { provide: AlmoxarifadoService, useValue: almoxarifadoServiceMock },
        { provide: RhService, useValue: rhServiceMock },
        { provide: ObrasService, useValue: obrasServiceMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { params: { id: '6fdfb235-7dfb-447b-b056-7fb1fa6f2cf0' }, queryParams: {} },
            params: of({ id: '6fdfb235-7dfb-447b-b056-7fb1fa6f2cf0' }),
            queryParams: of({}),
          },
        },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(RelatorioMovimentacaoEstoqueComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve criar o componente', () => {
    expect(component).toBeTruthy();
  });

  it('deve carregar dados da movimentação pelo ID da rota', () => {
    expect(almoxarifadoServiceMock.buscarOperacaoPorIdReferencia).toHaveBeenCalledWith(
      '6fdfb235-7dfb-447b-b056-7fb1fa6f2cf0',
    );
    expect(component['movimentacao']()).toEqual(mockMovimentacao);
    expect(component['loading']()).toBe(false);
  });

  it('deve carregar assinaturas para transferências entre estoques', () => {
    expect(almoxarifadoServiceMock.buscarAssinaturaTransferencia).toHaveBeenCalledWith(
      '6fdfb235-7dfb-447b-b056-7fb1fa6f2cf0',
      'ENTREGADOR',
    );
    expect(almoxarifadoServiceMock.buscarAssinaturaTransferencia).toHaveBeenCalledWith(
      '6fdfb235-7dfb-447b-b056-7fb1fa6f2cf0',
      'RECEBEDOR',
    );
  });

  it('deve carregar informações adicionais de equipe, obra e base operacional', () => {
    expect(rhServiceMock.buscarEquipePorId).toHaveBeenCalledWith('eq-1');
    expect(obrasServiceMock.buscarObraPorId).toHaveBeenCalledWith('obra-98765432-ef01');
    expect(rhServiceMock.buscarBaseOperacionalPorId).toHaveBeenCalledWith('base-1');

    expect(component['equipe']()?.codigo).toBe('LV-01');
    expect(component['obra']()?.numero).toBe('OBR-2026-0042');
    expect(component['baseOrigem']()?.nome).toBe('Base Salvador Central');
  });

  it('deve tratar erro na carga da movimentação', () => {
    almoxarifadoServiceMock.buscarOperacaoPorIdReferencia.mockReturnValue(
      throwError(() => ({ message: 'Operação não encontrada' })),
    );

    component['loadData']('id-inexistente');
    expect(component['loading']()).toBe(false);
    expect(component['error']()).toBeTruthy();
  });

  it('deve calcular totais corretamente', () => {
    const total = component['calculateTotal'](mockMovimentacao.itens);
    expect(total).toBe(152.5);
    expect(component['totalSeries']()).toBe(2);
    expect(component['materiaisSeriados']()).toBe(1);
  });

  it('deve agrupar as linhas por série num único material com as séries ordenadas', () => {
    const grupos = component['itensAgrupados']();
    expect(grupos).toHaveLength(2);
    const trafo = grupos.find((g: any) => g.material.id === 'mat-2')!;
    expect(trafo.quantidade).toBe(2);
    expect(trafo.series).toEqual(['TRF-2026-101', 'TRF-2026-887']);
    expect(component['temSeries'](trafo)).toBe(true);
    expect(component['temSeries'](grupos.find((g: any) => g.material.id === 'mat-1')!)).toBe(false);
  });

  it('deve retornar tones e labels semânticos corretos para tipos de movimentação', () => {
    expect(component['getTipoTone']('ENTRADA_AVULSA')).toBe('success');
    expect(component['getTipoTone']('BAIXA_AVULSA')).toBe('danger');
    expect(component['getTipoTone']('TRANSFERENCIA')).toBe('brand');
    expect(component['getTipoTone']('ENTREGA')).toBe('info');
    expect(component['getTipoTone']('ESTORNO')).toBe('warning');

    expect(component['getTipoLabel']('TRANSFERENCIA')).toBe('Transferência');
    expect(component['getTipoLabel']('ENTRADA_AVULSA')).toBe('Entrada avulsa');
    expect(component['getTipoLabel']('BAIXA_AVULSA')).toBe('Baixa avulsa');
  });

  it('deve usar tons semânticos para as condições de material', () => {
    expect(component['tomCondicao']('NOVO')).toBe('neutral');
    expect(component['tomCondicao']('RECUPERADO')).toBe('brand');
    expect(component['tomCondicao']('USADO')).toBe('info');
    expect(component['tomCondicao']('SUCATA')).toBe('warning');
  });

  it('deve navegar de volta para /almoxarifado/movimentacoes ao chamar voltar()', () => {
    component['voltar']();
    expect(router.navigate).toHaveBeenCalledWith(['/almoxarifado/movimentacoes']);
  });

  it('deve navegar para a solicitação técnica ou comercial ao chamar abrirSolicitacao()', () => {
    component['abrirSolicitacao']('sol-1', 'TECNICO');
    expect(router.navigate).toHaveBeenCalledWith(['/almoxarifado/solicitacoes-tecnicas', 'sol-1']);

    component['abrirSolicitacao']('sol-2', 'COMERCIAL');
    expect(router.navigate).toHaveBeenCalledWith(['/almoxarifado/solicitacoes', 'sol-2']);
  });

  it('deve navegar para a obra ao chamar abrirObra()', () => {
    component['abrirObra']('obra-1');
    expect(router.navigate).toHaveBeenCalledWith(['/obras', 'obra-1']);
  });

  it('deve abrir o diálogo de saldo de estoque', () => {
    component.saldoDialog = { show: vi.fn() } as any;
    component['abrirSaldo']('est-1', 'Almox Central');
    expect(component.saldoDialog.show).toHaveBeenCalledWith('est-1', 'Almox Central');
  });

  it('deve copiar ID da operação para o clipboard', async () => {
    const writeTextSpy = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: { writeText: writeTextSpy },
    });

    component['copiarId']('6fdfb235-7dfb-447b-b056-7fb1fa6f2cf0');
    await Promise.resolve();
    expect(writeTextSpy).toHaveBeenCalledWith('6fdfb235-7dfb-447b-b056-7fb1fa6f2cf0');
    expect(component['idCopiado']()).toBe(true);
  });
});
