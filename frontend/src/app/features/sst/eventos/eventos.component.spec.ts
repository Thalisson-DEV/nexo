import { TestBed } from '@angular/core/testing';
import { CesConfirmationService } from '@ces/ui';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { EventoSstImportacaoDTO, EventoSstResumoResponse } from '../../../core/models/sst-evento.models';
import { AuthService } from '../../../core/services/auth.service';
import { RhService } from '../../../core/services/rh.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { SstService } from '../../../core/services/sst.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { EventosComponent } from './eventos.component';
import { EventosImportacaoComponent, dataDaPlanilha, dataParaPlanilha } from './eventos-importacao.component';
import { EventosLookupsService } from './eventos-lookups.service';

const evento = {
  id: 'ev-1',
  numeroRegistro: 'EV-2026-0042',
  tipo: 'ACIDENTE_TIPICO',
  status: 'REGISTRADO',
  dataOcorrencia: '2026-09-10T13:30:00Z',
  dataComunicacao: '2026-09-11T13:30:00Z',
  horasAteComunicacao: 30,
  comunicacaoAtrasada: true,
  atividadeNome: 'PODA',
  gestorNome: 'Carlos',
  baseNome: 'BASE JUAZEIRO',
  equipeCodigo: 'EQ-01',
  veiculoPlaca: 'ABC1D23',
  idClassificacao: 'c1',
  classificacaoNome: 'GRAVE',
  comLesao: true,
  quantidadeAcidentados: 1,
  diasPerdidos: 3,
  possuiCat: true,
  localOcorrencia: null,
  ativo: true
} as unknown as EventoSstResumoResponse;

const lookups = {
  atividades: [{ id: 'a1', nome: 'PODA' }],
  classificacoes: [{ id: 'c1', nome: 'GRAVE' }],
  locaisLesao: [],
  naturezasLesao: [],
  agentesCausadores: [],
  processos: [],
  bases: [{ id: 'b1', nome: 'BASE JUAZEIRO' }],
  equipes: [{ id: 'eq-1', codigo: 'EQ-01' }],
  veiculos: [],
  centrosCusto: []
};

describe('EventosComponent', () => {
  let sst: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const providers = () => [
    { provide: SstService, useValue: sst },
    { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } },
    { provide: EventosLookupsService, useValue: { carregar: () => of(lookups) } },
    { provide: RhService, useValue: { listarEquipes: () => paginaTeste([]), listarVeiculos: () => paginaTeste([]) } },
    { provide: LookupStore, useValue: { searchCollaborators: () => of({ items: [], nextCursor: null, hasMore: false }) } }
  ];
  const abrir = (url = '/lista') => abrirListagem(EventosComponent, 'lista', url, providers());
  const ultimaConsulta = () => sst['listarEventos'].mock.lastCall![0];

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['sst:evento:write'];
    sst = {
      listarEventos: vi.fn(() => paginaTeste([evento])),
      exportarEventos: vi.fn(() => of(new Blob(['csv']))),
      importarEventos: vi.fn(() => of({ registros: [], totalFalha: 0, totalProcessados: 0, totalSucesso: 0 })),
      confirmarImportacaoEventos: vi.fn()
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, ocorrências mais recentes primeiro, com prazo de comunicação e lesão', async () => {
    const { el } = await abrir();
    expect(ultimaConsulta()).toMatchObject({ page: 0, size: 25, sort: 'dataOcorrencia,desc', buscaGlobal: null });
    expect(el.textContent).toContain('EV-2026-0042');
    expect(el.textContent).toContain('30h para comunicar');
    expect(el.textContent).toContain('1 acidentado(s) · 3d perdidos');
    expect(el.textContent).toContain('CAT emitida');
  });

  it('drill do painel (?idAtividade=) e períodos da URL viram o recorte, com chips nomeados', async () => {
    const { el } = await abrir('/lista?idAtividade=a1&tipo=ACIDENTE_TIPICO&ocorrencia=2026-09-01~2026-09-30');
    const consulta = ultimaConsulta();
    expect(consulta).toMatchObject({ idAtividade: ['a1'], tipo: ['ACIDENTE_TIPICO'] });
    expect(new Date(consulta.dataOcorrenciaDe).getDate()).toBe(1);
    expect(new Date(consulta.dataOcorrenciaAte).getHours()).toBe(23);
    expect(chipsDoPainel(el)).toEqual(expect.arrayContaining(['Atividade: PODA', 'Ocorrência: 01/09/2026 a 30/09/2026']));
  });

  it('exporta o recorte aplicado; sem permissão não há registro nem importação', async () => {
    const { componente } = await abrir('/lista?status=REGISTRADO&busca=poda');
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    (componente as unknown as { exportar(): void }).exportar();
    expect(sst['exportarEventos']).toHaveBeenCalledWith(expect.objectContaining({ status: ['REGISTRADO'], buscaGlobal: 'poda' }));
    expect(sst['exportarEventos'].mock.lastCall![0]).not.toHaveProperty('page');

    TestBed.resetTestingModule();
    permissoes = [];
    const { el } = await abrir();
    expect(el.textContent).not.toContain('Registrar evento');
    expect(el.textContent).not.toContain('Importar');
  });
});

describe('EventosImportacaoComponent', () => {
  const linha = (): EventoSstImportacaoDTO =>
    ({
      linha: 2,
      tipo: 'Acidente típico',
      telemetria: '',
      filmagem: '',
      tipoCat: '',
      tecnicos: 'Ana, Bruno',
      envolvidos: 'Colaborador~Acidentado~Maria|Terceiro~Testemunha~José~Motorista~Transp X',
      status: 'COM_ERRO',
      mensagemErro: 'Atividade não encontrada'
    }) as unknown as EventoSstImportacaoDTO;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: SstService, useValue: {} },
        { provide: RhService, useValue: {} },
        { provide: ImportacaoService, useValue: {} },
        MessageService,
        { provide: LookupStore, useValue: { searchCollaborators: () => of({ items: [], nextCursor: null, hasMore: false }) } },
        { provide: CesConfirmationService, useValue: { confirm: () => Promise.resolve(true) } }
      ]
    });
  });

  it('datas da planilha ida e volta', () => {
    expect(dataParaPlanilha(dataDaPlanilha('10/09/2026 13:45'), true)).toBe('10/09/2026 13:45');
    expect(dataParaPlanilha(dataDaPlanilha('10/09/2026'), false)).toBe('10/09/2026');
    expect(dataDaPlanilha('2026-09-10')).toBeNull();
  });

  it('correção converte rótulos em listas editáveis e volta ao texto da planilha', () => {
    const tela = TestBed.createComponent(EventosImportacaoComponent).componentInstance;
    const row = linha();
    tela.startImportEdit(row);
    expect(row.tipo).not.toBe('Acidente típico');
    expect(row._tecnicosArray).toEqual(['Ana', 'Bruno']);
    expect(row._envolvidosArray).toEqual([
      expect.objectContaining({ tipo: 'COLABORADOR', identificacao: 'Maria' }),
      expect.objectContaining({ tipo: 'TERCEIRO', identificacao: 'José', funcao: 'Motorista', empresa: 'Transp X' })
    ]);
    row._tecnicosArray = ['Ana'];
    tela.finishImportEdit(row);
    expect(row.tipo).toBe('Acidente típico');
    expect(row.tecnicos).toBe('Ana');
    expect(row.envolvidos.split('|')[1]).toMatch(/~José~Motorista~Transp X$/);
  });
});
