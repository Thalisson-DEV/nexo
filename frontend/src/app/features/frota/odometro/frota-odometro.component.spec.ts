import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LeituraOdometro } from '../../../core/models/frota.models';
import { abrirTelaFrota, chipsDoResumo, paginaDe } from '../shared/frota-teste';
import { FrotaOdometroComponent } from './frota-odometro.component';

const leitura = {
  id: 'l1',
  idVeiculo: 'v1',
  placa: 'ABC1234',
  km: 9000,
  lidoEm: '2026-10-08T10:00:00-03:00',
  origem: 'MANUAL',
  idOrigem: null,
  situacao: 'INCONSISTENTE',
  motivoSituacao: 'Menor que a anterior',
  kmAnterior: 12000,
  registradoPor: 'ANA',
  dataAnulacao: null,
  justificativaAnulacao: null
} as unknown as LeituraOdometro;

describe('FrotaOdometroComponent', () => {
  let frota: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/frota/odometro') => abrirTelaFrota(FrotaOdometroComponent, 'frota/odometro', url, frota, ['frota:veiculo:read']);

  beforeEach(() => {
    TestBed.resetTestingModule();
    frota = { listarLeituras: vi.fn(() => paginaDe([leitura])) };
  });

  afterEach(() => localStorage.clear());

  it('abre na fila de inconsistentes (padrão), mais recentes primeiro, sem busca textual', async () => {
    const { el } = await abrir();
    expect(frota['listarLeituras']).toHaveBeenCalledWith(expect.objectContaining({ situacoes: ['INCONSISTENTE'] }), {
      page: 0,
      size: 25,
      sort: ['lidoEm,desc']
    });
    expect(chipsDoResumo(el)).toEqual(['Situação: Inconsistente']);
    expect(el.querySelector('input[type="search"]')).toBeNull();
  });

  it('"Limpar" volta à fila de inconsistentes', async () => {
    const { componente } = await abrir('/frota/odometro?situacao=VALIDA,ANULADA');
    expect(frota['listarLeituras']).toHaveBeenLastCalledWith(expect.objectContaining({ situacoes: ['VALIDA', 'ANULADA'] }), expect.anything());
    (componente as unknown as { lista: { reset(): void } }).lista.reset();
    expect(frota['listarLeituras']).toHaveBeenLastCalledWith(expect.objectContaining({ situacoes: ['INCONSISTENTE'] }), expect.anything());
  });
});
