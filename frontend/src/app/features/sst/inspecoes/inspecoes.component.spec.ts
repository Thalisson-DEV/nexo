import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { CentrosCustoLookupService } from '../../../core/services/centros-custo-lookup.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { queryParamsListagem } from '../dashboards/drill/inspecoes-drill.utils';
import { DIAS_PADRAO_INSPECOES, InspecoesComponent } from './inspecoes.component';

const inspecao = {
  id: 'i1',
  tipoInspecao: { id: 't1', nome: 'VEICULAR' },
  formulario: { id: 'f1', titulo: 'Checklist de veículo' },
  processo: { id: 'p1', nome: 'MANUTENÇÃO' },
  baseOperacional: { id: 'b1', nome: 'JUAZEIRO' },
  nomeInspetor: 'ANA LIMA',
  dataRealizacao: '2026-10-05T09:00:00Z',
  status: 'CONCLUIDA',
  desvios: [{ id: 'd1' }, { id: 'd2' }]
};

describe('InspecoesComponent (SST)', () => {
  let sst: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(InspecoesComponent, 'lista', url, [
      { provide: SstService, useValue: sst },
      {
        provide: RhService,
        useValue: {
          listarProcessosAtivos: () => paginaTeste([{ id: 'p1', nome: 'MANUTENÇÃO' }]),
          listarBasesOperacionais: () => paginaTeste([{ id: 'b1', nome: 'JUAZEIRO' }]),
          listarEquipes: () => paginaTeste([])
        }
      },
      { provide: CentrosCustoLookupService, useValue: { listar: () => of([]) } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    sst = {
      listarOpcoesTiposInspecao: vi.fn(() => of([{ id: 't1', nome: 'VEICULAR' }])),
      listarFormulariosAtivos: vi.fn(() => paginaTeste([{ id: 'f1', titulo: 'Checklist de veículo' }])),
      listarInspecoes: vi.fn(() => paginaTeste([inspecao])),
      listarMinhasInspecoes: vi.fn(() => paginaTeste([inspecao])),
      exportarInspecoesResumo: vi.fn(() => of(new Blob(['x']))),
      exportarDesviosDetalhado: vi.fn(() => of(new Blob(['x'])))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nos últimos 90 dias, mais recentes primeiro, com tipo e desvios', async () => {
    const { el } = await abrir();
    const filtro = sst['listarInspecoes'].mock.calls[0][0];
    const esperado = new Date();
    esperado.setDate(esperado.getDate() - DIAS_PADRAO_INSPECOES);
    expect(new Date(filtro.dataRealizacaoDe).toDateString()).toBe(esperado.toDateString());
    expect(filtro).toMatchObject({ page: 0, size: 25, sort: 'dataRealizacao,desc' });
    expect(el.textContent).toContain('VEICULAR');
    expect(chipsDoPainel(el)[0]).toMatch(/^Realização: a partir de /);
  });

  it('abre no recorte do drill do painel (inspetor, base e período)', async () => {
    const params = queryParamsListagem({ idBaseOperacional: ['b1'] }, { de: '2026-09-01', ate: '2026-09-30', inicio: '', fim: '' }, { idInspetor: 'u1', inspetor: 'ANA LIMA' });
    const url = '/lista?' + new URLSearchParams(params).toString();
    const { el } = await abrir(url);
    const filtro = sst['listarInspecoes'].mock.lastCall![0];
    expect(filtro).toMatchObject({ idBaseOperacional: ['b1'], idInspetor: ['u1'] });
    expect(new Date(filtro.dataRealizacaoDe).getDate()).toBe(1);
    expect(chipsDoPainel(el)).toEqual(['Base: JUAZEIRO', 'Inspetor: ANA LIMA', 'Realização: 01/09/2026 a 30/09/2026']);
  });

  it('"Só as minhas" usa o endpoint próprio sem enviar inspetor', async () => {
    const { el } = await abrir('/lista?realizacao=~&idInspetor=u1');
    const minhas = Array.from(el.querySelectorAll('.ces-quick-filter')).find((b) => b.textContent!.includes('Só as minhas')) as HTMLButtonElement;
    minhas.click();
    await new Promise((r) => setTimeout(r));
    expect(sst['listarMinhasInspecoes']).toHaveBeenCalled();
    expect(sst['listarMinhasInspecoes'].mock.lastCall![0]).not.toHaveProperty('idInspetor');
  });

  it('exporta o recorte no tipo, formato e arquivo escolhidos', async () => {
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();
    const { componente } = await abrir('/lista?idTipoInspecao=t1');
    const tela = componente as unknown as { exportTipo: { set(v: string): void }; exportFormato: { set(v: string): void }; exportar(): void };
    tela.exportTipo.set('desvios');
    tela.exportFormato.set('detalhado');
    tela.exportar();
    expect(sst['exportarDesviosDetalhado']).toHaveBeenCalledWith(expect.objectContaining({ idTipoInspecao: ['t1'] }), 'xlsx');
  });
});
