import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ReportNavigatorRegistryService } from './report-navigator-registry.service';
import { FinanceiroService } from './financeiro.service';
import { AlmoxarifadoService } from './almoxarifado.service';
import { SolicitacaoMaterialTecnicaService } from './solicitacao-material-tecnica.service';
import { ExecucaoService } from './execucao.service';
import { DashboardService } from './dashboard.service';
import { RhDescontosService } from './rh-descontos.service';
import { SstService } from './sst.service';
import { ObrasService } from './obras.service';

describe('ReportNavigatorRegistryService and Data Sources', () => {
  let registry: ReportNavigatorRegistryService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ReportNavigatorRegistryService,
        {
          provide: FinanceiroService,
          useValue: {
            listarSolicitacoes: vi.fn(() => of({ content: [], page: { totalElements: 0 } }))
          }
        },
        {
          provide: AlmoxarifadoService,
          useValue: {
            listarSolicitacoes: vi.fn(() => of({ content: [], page: { totalElements: 0 } })),
            listarMovimentacoes: vi.fn(() => of({ content: [], page: { totalElements: 0 } })),
            listarEstoques: vi.fn(() => of({ content: [], page: { totalElements: 0 } }))
          }
        },
        {
          provide: SolicitacaoMaterialTecnicaService,
          useValue: {
            listar: vi.fn(() => of({ content: [], page: { totalElements: 0 } }))
          }
        },
        {
          provide: ExecucaoService,
          useValue: {
            listarExecucoes: vi.fn(() => of({ content: [], page: { totalElements: 0 } }))
          }
        },
        {
          provide: DashboardService,
          useValue: {
            gerarDashboardOperacional: vi.fn(() => of({ equipes: [] }))
          }
        },
        {
          provide: RhDescontosService,
          useValue: {
            buscarDescontos: vi.fn(() => of({ content: [], page: { totalElements: 0 } }))
          }
        },
        {
          provide: SstService,
          useValue: {
            listarInspecoes: vi.fn(() => of({ content: [], page: { totalElements: 0 } })),
            listarMinhasInspecoes: vi.fn(() => of({ content: [], page: { totalElements: 0 } }))
          }
        },
        {
          provide: ObrasService,
          useValue: {
            listarObras: vi.fn(() => of({ content: [], page: { totalElements: 0 } }))
          }
        }
      ]
    });

    registry = TestBed.inject(ReportNavigatorRegistryService);
  });

  const expectedSources = [
    'financeiro',
    'solicitacao-material',
    'solicitacao-tecnica',
    'movimentacao',
    'saldo',
    'execucao-comercial',
    'turno',
    'desconto',
    'inspecao-sst',
    'obra'
  ];

  for (const sourceKey of expectedSources) {
    it(`should resolve data source for "${sourceKey}"`, () => {
      const ds = registry.resolve(sourceKey);
      expect(ds).not.toBeNull();
      expect(ds?.config).toBeDefined();
      expect(typeof ds?.load).toBe('function');
      expect(typeof ds?.toItem).toBe('function');
      expect(typeof ds?.getRouteForItem).toBe('function');
      expect(typeof ds?.getListRoute).toBe('function');
    });
  }

  it('should return null for unknown source key', () => {
    expect(registry.resolve('unknown-key')).toBeNull();
  });
});
