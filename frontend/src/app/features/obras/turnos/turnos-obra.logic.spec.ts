import { describe, expect, it } from 'vitest';
import { TurnoObraDetalhe } from '../../../core/models/obras-turnos.models';
import { cartoesDoTurno } from './turnos-obra.logic';

describe('turnos-obra.logic', () => {
  it('frentes do turno separam programação e obra fora dela, e cada serviço tem a sua APR', () => {
    const apr = { id: 'a1', idObra: 'o1', idProgramacao: 'p1', status: 'CONCLUIDA' };
    const turno = {
      programacoes: [
        { id: 'p1', idObra: 'o1', numeroObra: '1', tituloObra: 'Um', liberacao: 'LIBERADA' },
        { id: 'p2', idObra: 'o1', numeroObra: '1', tituloObra: 'Um', liberacao: 'LIBERADA' }
      ],
      atividades: [
        { id: 'x1', idObra: 'o1', numeroObra: '1', tituloObra: 'Um', idProgramacao: 'p1', programada: true, liberacao: 'LIBERADA' },
        { id: 'x2', idObra: 'o2', numeroObra: '2', tituloObra: 'Dois', idProgramacao: null, programada: false, liberacao: 'IMPEDIDA' }
      ],
      apontamentos: [{ id: 's1', idProgramacao: 'p1', valor: 100 }, { id: 's2', idProgramacao: 'p1', valor: null }],
      aprs: [apr],
      ocorrencias: [{ id: 'i1', idObra: 'o2', impedeExecucao: true }, { id: 'i2', idObra: 'o1', impedeExecucao: false }]
    } as unknown as TurnoObraDetalhe;

    const [primeira, segunda, fora] = cartoesDoTurno(turno);

    expect(cartoesDoTurno(turno)).toHaveLength(3);
    expect(primeira.executado).toBe(100);
    expect(primeira.impedimento).toBeNull();
    expect(primeira.aprConcluida?.id).toBe('a1');
    expect(segunda.aprConcluida).toBeNull();
    expect(segunda.apontamentos).toEqual([]);
    expect(fora.programacao).toBeNull();
    expect(fora.impedimento?.id).toBe('i1');
  });
});
