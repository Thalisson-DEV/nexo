import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { DashboardService } from '../../../core/services/dashboard.service';
import { dataIso } from '../../../shared/listagem/listagem';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { RecorteDashboardService } from '../dashboards/recorte-dashboard.service';
import { DIAS_PADRAO_TURNOS, TurnoListComponent } from './turno-list.component';

const turno = {
  id: 't1',
  idEquipe: 'q1',
  codigoEquipe: 'EQ-01',
  baseOperacional: 'JUAZEIRO',
  processo: 'MANUTENÇÃO',
  status: 'ABERTO',
  dataInicio: '2026-10-09T07:00:00',
  dataFim: null,
  duracaoMinutos: 600,
  abertoDesdeOutroDia: true,
  abertoPor: 'USR000001',
  placa: 'ABC1234',
  membros: 3,
  notas: 4,
  producao: 1500,
  valorMedioNota: 375,
  metaDiaria: 2000,
  percentualMetaDiaria: 75,
  primeiroLancamento: null,
  ultimoLancamento: null
};

describe('TurnoListComponent', () => {
  let dashboard: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') =>
    abrirListagem(TurnoListComponent, 'lista', url, [
      { provide: DashboardService, useValue: dashboard },
      {
        provide: RecorteDashboardService,
        useValue: {
          carregar: vi.fn(),
          carregarEquipes: vi.fn(),
          carregando: signal(false),
          carregandoEquipes: signal(false),
          opcoesEquipes: signal([{ label: 'EQ-01', value: 'q1' }]),
          opcoesBases: signal([]),
          opcoesProcessos: signal([])
        }
      }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    dashboard = { consultarTurnos: vi.fn(() => paginaTeste([turno])) };
  });

  afterEach(() => localStorage.clear());

  it('abre nos últimos 30 dias (chip), mais recentes primeiro', async () => {
    const { el } = await abrir();
    const inicio = new Date();
    inicio.setDate(inicio.getDate() - DIAS_PADRAO_TURNOS);
    expect(dashboard['consultarTurnos']).toHaveBeenCalledWith(expect.objectContaining({ aberturaDe: dataIso(inicio), sort: ['dataInicio,desc'], page: 0, size: 25 }));
    expect(chipsDoPainel(el)[0]).toMatch(/^Abertura: a partir de /);
    expect(el.textContent).toContain('EQ-01');
    expect(el.textContent).toContain('Aberto desde');
  });

  it('atalho "Em turno" e equipe da URL', async () => {
    const { el } = await abrir('/lista?equipe=q1&abertura=~');
    expect(dashboard['consultarTurnos']).toHaveBeenLastCalledWith(expect.objectContaining({ idEquipe: 'q1', aberturaDe: undefined }));
    const emTurno = Array.from(el.querySelectorAll('.ces-quick-filter')).find((b) => b.textContent!.includes('Em turno')) as HTMLButtonElement;
    emTurno.click();
    await new Promise((r) => setTimeout(r));
    expect(dashboard['consultarTurnos']).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'ABERTO', idEquipe: 'q1' }));
  });
});
