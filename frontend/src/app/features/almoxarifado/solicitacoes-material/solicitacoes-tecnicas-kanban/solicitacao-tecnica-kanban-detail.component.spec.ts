import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { provideRouter } from '@angular/router';
import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA } from '@angular/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

import { NotificationService } from '@ces/ui';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';
import { SolicitacaoTecnicaKanbanDetailComponent } from './solicitacao-tecnica-kanban-detail.component';
import {
  SolicitacaoMaterialDetalheResponse,
} from '../../../../core/models/solicitacao-material-tecnica.models';

describe('SolicitacaoTecnicaKanbanDetailComponent', () => {
  let component: SolicitacaoTecnicaKanbanDetailComponent;
  let fixture: ComponentFixture<SolicitacaoTecnicaKanbanDetailComponent>;

  const mockDetalheAdicao: SolicitacaoMaterialDetalheResponse = {
    id: 'sol-tec-12345678',
    status: 'APROVADO',
    contexto: 'TECNICO',
    dataSolicitacao: '2026-10-01T10:00:00Z',
    idRequisitante: 'usr-1',
    acoesPermitidas: ['APROVAR', 'EDITAR_PLANEJAMENTO', 'INICIAR_SEPARACAO', 'CONCLUIR_SEPARACAO', 'PULAR_SEPARACAO', 'ENTREGAR', 'ENCERRAR_SALDO', 'REJEITAR'],
    statusSeparacao: 'PRONTO_PARA_SEPARACAO',
    versao: 1,
    estoqueOrigem: {
      id: 'est-1',
      nomeIdentificador: 'ALMOX CENTRAL',
      ativo: true,
      tipoEstoque: { id: 't-1', nome: 'FISICO', ativo: true },
    },
    estoqueDestino: {
      id: 'est-2',
      nomeIdentificador: 'EQUIPE LINHA VIVA',
      ativo: true,
      idEquipe: 'eq-1',
      tipoEstoque: { id: 't-2', nome: 'CAMPO', ativo: true },
    },
    tecnico: {
      tipoTecnico: 'ADICAO_COMUM',
      tipoObra: 'OBRA',
      idObra: 'obra-1',
      ocorrencia: { id: 'oc-1', codigo: 'OC-9876' },
      idAlmoxarifadoAtendimento: 'est-1',
      idEquipe: 'eq-1',
      idColaboradorSaque: 'colab-1',
      saqueForaEquipe: false,
      dataPrevista: '2026-10-03T18:00:00Z',
      observacao: 'Separação emergencial para posteamento',
      statusSeparacao: 'PRONTO_PARA_SEPARACAO',
      reserva: {
        id: 'res-1',
        idSolicitacao: 'sol-tec-12345678',
        numero: '900123456',
        dataCriacao: '2026-10-01T10:00:00Z',
        idAgenteResponsavel: null,
        nomeAgente: 'Fiscal Carlos',
        empresaAgente: 'Empresa',
        registradaEm: '2026-10-01T10:00:00Z',
        idUsuarioRegistro: 'usr-1',
        atualizadaEm: null,
        idUsuarioAtualizacao: null,
        versao: 1,
      },
      totais: {
        solicitada: 50,
        entregaBruta: 20,
        entregaEstornada: 0,
        entregaLiquida: 20,
        devolucaoBruta: 0,
        devolucaoEstornada: 0,
        devolucaoLiquida: 0,
        pendente: 30,
      },
      operacoes: [
        {
          id: 'op-1',
          tipo: 'ENTREGA',
          idEquipe: 'eq-1',
          idColaboradorSaque: 'colab-1',
          saqueForaEquipe: false,
          idUsuarioOperador: 'usr-almox',
          realizadaEm: '2026-10-02T14:30:00Z',
          itens: [
            {
              id: 'op-item-1',
              idItemSolicitacao: 'item-1',
              idMaterial: 'mat-1',
              codigoMaterial: 'ISOL-15KV',
              descricaoMaterial: 'Isolador Polimérico 15kV',
              quantidade: 20,
              condicao: 'NOVO',
            },
          ],
        },
      ],
      versao: 1,
      acoesPermitidas: ['ENTREGAR'],
      atrasada: false,
      encerradaEm: null,
      idUsuarioEncerramento: null,
      motivoEncerramento: null,
    },
    itens: [
      {
        id: 'item-1',
        tipoMovimentacao: 'ADICAO_COMUM',
        material: {
          id: 'mat-1',
          codigo: 'ISOL-15KV',
          descricao: 'Isolador Polimérico 15kV',
          unidadeMedida: 'UN',
          detalhaSerie: false,
          ativo: true,
        },
        quantidade: 50,
        quantidadeEntregue: 20,
        quantidadeEntregaLiquida: 20,
        quantidadePendente: 30,
      },
    ],
  };

  const mockDetalheDevolucao: SolicitacaoMaterialDetalheResponse = {
    ...mockDetalheAdicao,
    id: 'sol-tec-dev-8888',
    tecnico: {
      ...mockDetalheAdicao.tecnico!,
      tipoTecnico: 'DEVOLUCAO_COMUM',
      totais: {
        solicitada: 10,
        entregaBruta: 0,
        entregaEstornada: 0,
        entregaLiquida: 0,
        devolucaoBruta: 4,
        devolucaoEstornada: 0,
        devolucaoLiquida: 4,
        pendente: 6,
      },
      operacoes: [],
      acoesPermitidas: ['CONFIRMAR_DEVOLUCAO'],
    },
    acoesPermitidas: ['CONFIRMAR_DEVOLUCAO', 'REJEITAR'],
    itens: [
      {
        id: 'item-dev-1',
        tipoMovimentacao: 'DEVOLUCAO_COMUM',
        material: {
          id: 'mat-transf',
          codigo: 'TRANSF-45',
          descricao: 'Transformador 45kVA',
          unidadeMedida: 'UN',
          detalhaSerie: true,
          ativo: true,
        },
        quantidade: 10,
        quantidadeEntregue: 0,
        quantidadeDevolvidaBruta: 4,
        quantidadeDevolucaoLiquida: 4,
        quantidadePendente: 6,
      },
    ],
  };

  const mockTecnicaService = {
    buscarPorId: vi.fn(() => of(mockDetalheAdicao)),
  };

  const mockAlmoxarifadoService = {
    listarEstoquesAtivos: vi.fn(() => of({ content: [] })),
    removerPendencia: vi.fn(() => of(undefined)),
  };

  const mockRhService = {
    buscarEquipePorId: vi.fn(() =>
      of({
        id: 'eq-1',
        codigo: 'LV-01',
        tipoEquipe: { id: 'te-1', nome: 'Linha Viva' },
      })
    ),
  };

  const mockObrasService = {
    buscarObraPorId: vi.fn(() =>
      of({
        id: 'obra-1',
        numero: 'OBR-2026-001',
        titulo: 'Alimentador Subestação Norte',
      })
    ),
  };

  const mockUserLookup = {
    resolverNome: vi.fn((id?: string | null, fallback = 'Usuário') => {
      if (id === 'usr-1') return 'Maria Oliveira';
      if (id === 'colab-1') return 'João Eletricista';
      if (id === 'usr-almox') return 'Roberto Almoxarife';
      return fallback;
    }),
  };

  const mockNotificationService = {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [SolicitacaoTecnicaKanbanDetailComponent],
      providers: [
        provideRouter([]),
        { provide: SolicitacaoMaterialTecnicaService, useValue: mockTecnicaService },
        { provide: AlmoxarifadoService, useValue: mockAlmoxarifadoService },
        { provide: RhService, useValue: mockRhService },
        { provide: ObrasService, useValue: mockObrasService },
        { provide: UsuarioLookupService, useValue: mockUserLookup },
        { provide: NotificationService, useValue: mockNotificationService },
      ],
      schemas: [CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(SolicitacaoTecnicaKanbanDetailComponent);
    component = fixture.componentInstance;
  });

  it('deve ser instanciado corretamente', () => {
    expect(component).toBeTruthy();
  });

  it('deve carregar dados da solicitação técnica através do carregarDetalhe', () => {
    component['carregarDetalhe']('sol-tec-12345678');

    expect(mockTecnicaService.buscarPorId).toHaveBeenCalledWith('sol-tec-12345678');
    expect(mockRhService.buscarEquipePorId).toHaveBeenCalledWith('eq-1');
    expect(mockObrasService.buscarObraPorId).toHaveBeenCalledWith('obra-1');
    expect(component['detalhe']()?.id).toBe('sol-tec-12345678');
    expect(component['equipeNome']()).toBe('LV-01 · Linha Viva');
    expect(component['responsavelSaqueNome']()).toBe('João Eletricista');
    expect(component['requisitanteNome']()).toBe('Maria Oliveira');
  });

  it('deve calcular corretamente os totais operacionais de adição', () => {
    component['detalhe'].set(mockDetalheAdicao);

    expect(component['totalItens']()).toBe(1);
    expect(component['totalSolicitado']()).toBe(50);
    expect(component['totalAtendido']()).toBe(20);
    expect(component['totalPendente']()).toBe(30);
    expect(component['percentualAtendido']()).toBe(40);
    expect(component['tipoEhAdicao']()).toBe(true);
  });

  it('deve calcular corretamente os totais de devolução quando for devolução comum (222)', () => {
    component['detalhe'].set(mockDetalheDevolucao);

    expect(component['tipoEhAdicao']()).toBe(false);
    expect(component['totalItens']()).toBe(1);
    expect(component['totalSolicitado']()).toBe(10);
    expect(component['totalAtendido']()).toBe(4);
    expect(component['totalPendente']()).toBe(6);
    expect(component['percentualAtendido']()).toBe(40);
  });

  it('deve emitir actionRequested ao executar uma ação do rodapé', () => {
    const actionSpy = vi.fn();
    component.actionRequested.subscribe(actionSpy);
    component['detalhe'].set(mockDetalheAdicao);

    component['executarAcao']('PLANEJAR');
    expect(actionSpy).toHaveBeenCalledWith({ id: 'sol-tec-12345678', action: 'PLANEJAR' });

    component['executarAcao']('ENTREGAR');
    expect(actionSpy).toHaveBeenCalledWith({ id: 'sol-tec-12345678', action: 'ENTREGAR' });

    component['executarAcao']('REJEITAR');
    expect(actionSpy).toHaveBeenCalledWith({ id: 'sol-tec-12345678', action: 'REJEITAR' });
  });

  it('deve abrir modal de pendência para um item específico', () => {
    component['detalhe'].set(mockDetalheAdicao);

    const item = mockDetalheAdicao.itens[0];
    component['abrirPendenciaItem'](item);

    expect(component['modalPendenciaVisivel']()).toBe(true);
    expect(component['pendenciaItemId']()).toBe('item-1');
    expect(component['pendenciaItemLabel']()).toContain('ISOL-15KV');
  });

  it('deve expandir e recolher todas as operações no histórico', () => {
    component['detalhe'].set(mockDetalheAdicao);

    expect(component['isOperacaoExpandida']('op-1')).toBe(false);

    component['expandirTodasOperacoes']();
    expect(component['isOperacaoExpandida']('op-1')).toBe(true);

    component['recolherTodasOperacoes']();
    expect(component['isOperacaoExpandida']('op-1')).toBe(false);
  });

  it('deve resolver pendência geral com sucesso e recarregar detalhe', () => {
    component['detalhe'].set({
      ...mockDetalheAdicao,
      motivoPendencia: { id: 'p-1', nome: 'Falta de Documentação' } as any,
    });

    component['resolverPendenciaGeral']();

    expect(mockAlmoxarifadoService.removerPendencia).toHaveBeenCalledWith('sol-tec-12345678');
    expect(mockNotificationService.success).toHaveBeenCalledWith('Pendência geral resolvida com sucesso.');
    expect(mockTecnicaService.buscarPorId).toHaveBeenCalledWith('sol-tec-12345678');
  });
});
