import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ObraResponse, TipoVinculoSubprojeto } from '../../../core/models/obras.models';
import { AuthService } from '../../../core/services/auth.service';
import { FavoritosService } from '../../../core/services/favoritos.service';
import { ObrasService } from '../../../core/services/obras.service';
import { RhService } from '../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../core/services/solicitacao-material-tecnica.service';
import { ObraDetalheComponent } from './obra-detalhe.component';

describe('ObraDetalheComponent', () => {
  let component: ObraDetalheComponent;
  let fixture: ComponentFixture<ObraDetalheComponent>;
  let obrasService: any;
  let rhService: any;
  let authService: any;
  let favoritosService: any;

  const mockObra: ObraResponse = {
    id: 'obra-1',
    numero: '1228694',
    titulo: 'SUBESTAÇÃO CENTRAL - REFORMA ALIMENTADOR 02',
    valorProjeto: 185000.0,
    dataInicio: '01/03/2026',
    dataPrevisaoFim: '30/09/2026',
    dataFimEfetiva: null,
    statusId: 'st-1',
    statusNome: 'EM EXECUÇÃO',
    statusConcluida: false,
    supervisorId: 'sup-1',
    supervisorNome: 'CARLOS SILVA',
    coordenadorId: 'coord-1',
    coordenadorNome: 'MARCOS OLIVEIRA',
    processoNome: 'CONSTRUÇÃO E REFORMA',
    baseOperacionalNome: 'BASE JUAZEIRO',
    municipioNome: 'JUAZEIRO',
    centroCustoNome: 'CC-OBRAS-01',
    notaObra: 'NOTA-998811',
    agenteResponsavelNome: 'Eng. Roberto Fiscal',
    agenteResponsavelEmail: 'roberto@concessionaria.com.br',
    latitude: -9.41234,
    longitude: -40.50123,
    equipes: ['eq-1'],
    subprojetos: [
      {
        id: 'sub-1',
        numero: '1228695',
        titulo: 'KIT MEDIDOR 01',
        tipoVinculo: TipoVinculoSubprojeto.KIT,
        valorProjeto: 15000.0,
        dataInicio: '01/03/2026',
        dataPrevisaoFim: '15/04/2026',
        statusId: 'st-2',
        statusNome: 'PLANEJADA'
      }
    ],
    anexos: [
      {
        id: 'anx-1',
        idObra: 'obra-1',
        idTipoAnexo: 'tipo-1',
        tipoAnexoNome: 'PROJETO EXECUTIVO',
        nomeArquivo: 'planta_baixa.pdf',
        contentType: 'application/pdf',
        tamanhoBytes: 2048576,
        dataCriacao: '01/03/2026 10:00',
        usuarioCriacao: 'engenheiro'
      }
    ],
    ativo: true,
    dataCriacao: '2026-03-01T09:00:00-03:00'
  };

  beforeEach(async () => {
    const mockResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
    (globalThis as any).ResizeObserver = mockResizeObserver;
    if (typeof window !== 'undefined') {
      (window as any).ResizeObserver = mockResizeObserver;
    }

    obrasService = {
      buscarObraPorId: vi.fn().mockReturnValue(of(mockObra)),
      listarStatusAtivos: vi.fn().mockReturnValue(of([{ id: 'st-1', nome: 'EM EXECUÇÃO', concluida: false }])),
      listarTiposAnexosAtivos: vi.fn().mockReturnValue(of([{ id: 'tipo-1', nome: 'PROJETO EXECUTIVO' }])),
      listarObras: vi.fn().mockReturnValue(of({ content: [] })),
      alterarStatus: vi.fn().mockReturnValue(of(mockObra)),
      vincularSubprojeto: vi.fn().mockReturnValue(of(mockObra)),
      desvincularSubprojeto: vi.fn().mockReturnValue(of(mockObra)),
      anexarArquivo: vi.fn().mockReturnValue(of(mockObra.anexos[0])),
      baixarAnexo: vi.fn().mockReturnValue(of({ body: new Blob() })),
      removerAnexo: vi.fn().mockReturnValue(of(null)),
      extrairNomeArquivoDeResponse: vi.fn().mockReturnValue('planta_baixa.pdf'),
      baixarArquivoBlob: vi.fn()
    };

    rhService = {
      listarEquipes: vi.fn().mockReturnValue(
        of({
          content: [
            {
              id: 'eq-1',
              codigo: 'EQ-01',
              tipoEquipe: { id: 'te-1', nome: 'LINHA VIVA' },
              baseOperacional: { id: 'bo-1', nome: 'BASE JUAZEIRO' },
              supervisor: {
                id: 'sup-1',
                colaborador: { id: 'colab-sup-1', nomeCompleto: 'Supervisor Teste', matricula: 'SUP001', ativo: true },
                coordenador: { id: 'coord-1', colaborador: { id: 'colab-coord-1', nomeCompleto: 'Coord Teste', matricula: 'CRD001', ativo: true }, dataInicio: '2025-01-01', dataFim: null, ativo: true },
                dataInicio: '2025-01-01',
                dataFim: null,
                ativo: true
              },
              membros: ['colab-1', 'colab-2']
            }
          ]
        })
      )
    };

    authService = {
      currentUser: vi.fn().mockReturnValue({
        login: 'supervisor',
        permissoes: ['obras:obra:read', 'obras:obra:write', 'obras:anexos:read', 'obras:anexos:write']
      })
    };

    favoritosService = {
      isCurrentPageFavorite: vi.fn().mockReturnValue(false),
      toggleFavorite: vi.fn()
    };

    const solicitacaoService = {
      obterProjecaoObra: vi.fn().mockReturnValue(
        of({
          tipoObra: 'OBRA',
          idObra: 'obra-1',
          ocorrencias: [],
          solicitacoes: [
            {
              id: 'sol-001',
              status: 'APROVADO',
              tipo: 'ADICAO_COMUM',
              idEquipe: null,
              statusSeparacao: 'PRONTO_PARA_SEPARACAO',
              dataPrevista: '2026-03-01',
              quantidadeSolicitada: 5,
              quantidadePendente: 5
            }
          ]
        })
      )
    };

    await TestBed.configureTestingModule({
      imports: [ObraDetalheComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
        ConfirmationService,
        { provide: ObrasService, useValue: obrasService },
        { provide: RhService, useValue: rhService },
        { provide: AuthService, useValue: authService },
        { provide: FavoritosService, useValue: favoritosService },
        { provide: SolicitacaoMaterialTecnicaService, useValue: solicitacaoService },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'obra-1' } } }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ObraDetalheComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve criar o componente e carregar os dados completos do relatório de obra', () => {
    expect(component).toBeTruthy();
    expect(obrasService.buscarObraPorId).toHaveBeenCalledWith('obra-1');
    expect(component['obra']()?.numero).toBe('1228694');
    expect(component['obra']()?.supervisorNome).toBe('CARLOS SILVA');
    expect(component['obra']()?.coordenadorNome).toBe('MARCOS OLIVEIRA');
    expect(component['equipesDetalhadas']().length).toBe(1);
    expect(component['dataEmissao']).toBeTruthy();
  });

  it('deve renderizar as seções lineares unificadas no template de relatório', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('NOME DA EMPRESA');
    expect(compiled.textContent).toContain('Relatório Técnico da Obra');
    expect(compiled.textContent).toContain('Subprojetos Vinculados');
    expect(compiled.textContent).toContain('Equipes Operacionais Alocadas');
    expect(compiled.textContent).toContain('Solicitações de Material');
    expect(compiled.textContent).toContain('Custódia Documental & Anexos Técnicos');
    expect(compiled.textContent).toContain('Geolocalização');
  });

  it('consome a projeção v2 com ocorrências achatadas e conta solicitações por ID', () => {
    component['solicitacoesObra'].set({
      idObra: 'obra-1',
      tipoObra: 'CORRETIVA',
      ocorrencias: [
        {
          id: 'oc-1',
          codigo: 'OC-100',
          solicitacoes: [
            {
              id: 'sol-002',
              status: 'PENDENTE',
              tipo: 'ADICAO_COMUM',
              statusSeparacao: null,
              idEquipe: 'equipe-1',
              dataPrevista: '2026-03-01',
              quantidadeSolicitada: 1,
              quantidadePendente: 1
            }
          ]
        }
      ],
      solicitacoes: []
    });
    fixture.detectChanges();

    expect(component['totalSolicitacoesObra']()).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('OC-100');
    expect(fixture.nativeElement.querySelector('a[href*="sol-002"]')).toBeTruthy();
  });

  it('deve abrir o modal de alteração rápida de status', () => {
    component['abrirAlteracaoStatus']();
    expect(component['statusDialogVisible']()).toBe(true);
    expect(component['novoStatusId']).toBe('st-1');
  });

  it('deve abrir o modal de vincular subprojeto com tipo padrão KIT', () => {
    component['abrirModalVincularSubprojeto']();
    expect(component['vincularDialogVisible']()).toBe(true);
    expect(component['tipoVinculoSelecionado']).toBe(TipoVinculoSubprojeto.KIT);
  });

  it('deve abrir o modal de upload de anexo técnico', () => {
    component['abrirModalAnexo']();
    expect(component['anexoDialogVisible']()).toBe(true);
    expect(component['anexoTipoId']).toBe('tipo-1');
  });

  it('deve acionar a impressão do relatório via window.print', () => {
    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => {});
    component['imprimir']();
    expect(printSpy).toHaveBeenCalled();
    printSpy.mockRestore();
  });

  it('deve abrir o modal de pré-visualização ao visualizar anexo PDF', () => {
    const anexo = mockObra.anexos[0];
    component['visualizarAnexo'](anexo);
    expect(component['showPreviewModal']()).toBe(true);
    expect(component['previewNomeArquivo']()).toBe('planta_baixa.pdf');
    expect(component['previewTipo']()).toBe('pdf');
    expect(obrasService.baixarAnexo).toHaveBeenCalledWith('obra-1', 'anx-1', 'planta_baixa.pdf');
  });

  it('deve fechar o modal de pré-visualização e limpar URLs', () => {
    component['fecharPreviewModal']();
    expect(component['showPreviewModal']()).toBe(false);
    expect(component['previewRawUrl']()).toBeNull();
    expect(component['previewUrl']()).toBeNull();
  });
});
