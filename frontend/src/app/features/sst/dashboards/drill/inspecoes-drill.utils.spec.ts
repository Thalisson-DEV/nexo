import { describe, expect, it } from 'vitest';
import {
  desviosIgnoramAlgumFiltro,
  filtrosListagemDesvios,
  filtrosListagemInspecoes,
  granularidadeAbaixo,
  intervaloDoPonto,
  queryParamsListagem,
  rotuloDia,
} from './inspecoes-drill.utils';

describe('inspecoes-drill utils', () => {
  it('calcula o intervalo de dia, mês e ano com offset explícito', () => {
    const dia = intervaloDoPonto('2026-09-14', 'DIA')!;
    expect(dia.de).toBe('2026-09-14');
    expect(dia.ate).toBe('2026-09-14');
    expect(dia.inicio).toMatch(/^2026-09-14T00:00:00\.000[+-]\d{2}:\d{2}$/);
    expect(dia.fim).toMatch(/^2026-09-14T23:59:59\.999[+-]\d{2}:\d{2}$/);

    expect(intervaloDoPonto('2026-02-01', 'MES')!.ate).toBe('2026-02-28');
    expect(intervaloDoPonto('2024-02', 'MES')!.ate).toBe('2024-02-29');
    expect(intervaloDoPonto('2026-01-01', 'ANO')!.ate).toBe('2026-12-31');
    expect(intervaloDoPonto('lixo', 'DIA')).toBeNull();
  });

  it('desce um nível de granularidade', () => {
    expect(granularidadeAbaixo('ANO')).toBe('MES');
    expect(granularidadeAbaixo('MES')).toBe('DIA');
    expect(granularidadeAbaixo('DIA')).toBeNull();
  });

  it('leva todos os filtros do painel para as listagens', () => {
    const intervalo = intervaloDoPonto('2026-09-14', 'DIA')!;
    const filtros = { idTipoInspecao: ['t1'], idEquipe: ['e1', 'e2'], idProcesso: [], granularidade: 'DIA' as const };
    const inspecoes = filtrosListagemInspecoes(filtros, intervalo);
    expect(inspecoes).toEqual({
      dataRealizacaoDe: intervalo.inicio,
      dataRealizacaoAte: intervalo.fim,
      idTipoInspecao: ['t1'],
      idEquipe: ['e1', 'e2'],
    });
    const desvios = filtrosListagemDesvios(filtros, intervalo);
    expect(desvios).toEqual({ dataInspecaoDe: intervalo.inicio, dataInspecaoAte: intervalo.fim, idEquipe: ['e1', 'e2'] });
    expect(desviosIgnoramAlgumFiltro(filtros)).toBe(true);
    expect(desviosIgnoramAlgumFiltro({ idEquipe: ['e1'] })).toBe(false);
  });

  it('monta os query params da listagem completa', () => {
    const intervalo = intervaloDoPonto('2026-09-01', 'MES')!;
    expect(queryParamsListagem({ idBaseOperacional: ['b1'] }, intervalo, { inspetor: 'Ana' })).toEqual({
      realizacao: '2026-09-01~2026-09-30',
      inspetor: 'Ana',
      idBaseOperacional: 'b1',
    });
  });

  it('formata o dia em pt-BR', () => {
    expect(rotuloDia('2026-09-14')).toBe('14/09/2026');
  });
});
