import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { MetasInspecaoService } from '../../../core/services/metas-inspecao.service';
import { CentrosCustoLookupService } from '../../../core/services/centros-custo-lookup.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { AcompanhamentoMetasResponse } from '../../../core/models/meta-inspecao.models';
import { TipoInspecaoResponse } from '../../../core/models/sst.models';
import { AcompanhamentoMetasComponent } from './acompanhamento-metas.component';

const mockResponse: AcompanhamentoMetasResponse = {
  dataReferencia: '2026-09-14',
  totalColaboradores: 1,
  atingiram: 1,
  emAndamento: 0,
  naoAtingiram: 0,
  somaMetas: 2,
  somaRealizado: 2,
  percentualColaboradoresNaMeta: 100,
  percentualExecucaoGlobal: 100,
  itens: [
    {
      idMeta: 'meta-1',
      idTipoInspecao: 'tipo-campo',
      nomeTipoInspecao: 'Campo',
      origem: 'COLABORADOR',
      idColaborador: 'colab-1',
      nomeColaborador: 'João da Silva',
      possuiUsuario: true,
      idFuncao: 'funcao-1',
      nomeFuncao: 'Eletricista',
      idBaseOperacional: 'base-1',
      nomeBaseOperacional: 'Base Central',
      idCentroCusto: 'cc-1',
      nomeCentroCusto: 'Regional Leste',
      idProcesso: 'proc-1',
      nomeProcesso: 'Operação',
      periodicidade: 'MENSAL',
      janelaInicio: '2026-09-01',
      janelaFim: '2026-09-30',
      diasRestantes: 16,
      meta: 2,
      realizado: 2,
      saldo: 0,
      percentualAtingimento: 100,
      status: 'ATINGIDA',
    },
  ],
};

const mockTipos: TipoInspecaoResponse[] = [
  {
    id: 'tipo-campo',
    nome: 'Campo',
    descricao: 'Inspeção de campo',
    ordem: 1,
    ativo: true,
    dataCriacao: '2026-09-14T00:00:00Z',
  },
  {
    id: 'tipo-veicular',
    nome: 'Veicular',
    descricao: 'Inspeção veicular',
    ordem: 2,
    ativo: true,
    dataCriacao: '2026-09-14T00:00:00Z',
  },
];

describe('AcompanhamentoMetasComponent', { timeout: 15000 }, () => {
  let metasService: { acompanhamento: ReturnType<typeof vi.fn> };
  let sstService: { listarOpcoesTiposInspecao: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    metasService = {
      acompanhamento: vi.fn(() => of(mockResponse)),
    };
    sstService = {
      listarOpcoesTiposInspecao: vi.fn(() => of(mockTipos)),
    };

    await TestBed.configureTestingModule({
      imports: [AcompanhamentoMetasComponent],
      providers: [
        MessageService,
        provideRouter([]),
        { provide: MetasInspecaoService, useValue: metasService },
        { provide: SstService, useValue: sstService },
        {
          provide: RhService,
          useValue: {
            listarFuncoesAtivas: vi.fn(() => of({ content: [] })),
            listarBasesOperacionais: vi.fn(() => of({ content: [] })),
            listarProcessosAtivos: vi.fn(() => of({ content: [] })),
          },
        },
        {
          provide: CentrosCustoLookupService,
          useValue: { listar: vi.fn(() => of([])) },
        },
        {
          provide: LookupStore,
          useValue: { searchCollaborators: vi.fn(() => of({ items: [], nextCursor: null, hasMore: false })) },
        },
      ],
    }).compileComponents();
  });

  it('carrega opções de tipos de inspeção na inicialização', () => {
    const fixture = TestBed.createComponent(AcompanhamentoMetasComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    expect(sstService.listarOpcoesTiposInspecao).toHaveBeenCalled();
    expect(component['tiposInspecao']()).toEqual(mockTipos);
  });

  it('envia o filtro idTipoInspecao ao consultar o acompanhamento', () => {
    const fixture = TestBed.createComponent(AcompanhamentoMetasComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component['filtros'].idTipoInspecao = ['tipo-veicular'];
    component['carregar']();

    expect(metasService.acompanhamento).toHaveBeenCalledWith(
      expect.objectContaining({
        idTipoInspecao: ['tipo-veicular'],
      }),
    );
  });

  it('limpa os filtros resetando idTipoInspecao para array vazio', () => {
    const fixture = TestBed.createComponent(AcompanhamentoMetasComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component['filtros'].idTipoInspecao = ['tipo-veicular'];
    component['limpar']();

    expect(component['filtros'].idTipoInspecao).toEqual([]);
  });
});
