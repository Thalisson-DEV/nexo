import '@angular/compiler';
import { describe, expect, it } from 'vitest';
import { EquipeBoletim } from '../../../core/models/obras-turnos.models';
import { agruparEquipes, atendeFiltro, ordenarEquipes, resumirBoletim, serieDoBoletim, temAprPendente, vistaDoBoletim, visaoDoBoletim } from './boletim-obra.logic';

function equipe(parte: Partial<EquipeBoletim>): EquipeBoletim {
  return {
    idEquipe: parte.codigo ?? 'e', codigo: 'E', tipoEquipe: 'CONSTRUCAO', idBase: 'b', baseOperacional: 'BASE', situacao: 'SEM_TURNO', idTurno: null, abertura: null, fechamento: null,
    minutosEmTurno: 0, abertoDesdeOutroDia: false, placa: null, lider: null, componentes: [], noCiclo: { programado: 0, executado: 0 }, noDia: { programado: 0, executado: 0 },
    diaAnterior: { programado: 0, executado: 0 }, metaDiaria: null, percentualMeta: null, agendamentoSemTurno: false, agenda: [], ...parte
  } as EquipeBoletim;
}

const agenda = (liberacao: EquipeBoletim['agenda'][number]['liberacao']) => ({ idProgramacao: 'p', numero: 1, idObra: 'o', numeroObra: '1', tituloObra: 'T', descricao: 'D', valor: 10, itens: 1, itensConcluidos: 0, liberacao });

describe('boletim-obra.logic', () => {
  it('APR pendente só conta para quem tem turno e ainda não decidiu', () => {
    expect(temAprPendente(equipe({ situacao: 'EM_TURNO', agenda: [agenda('PENDENTE')] }))).toBe(true);
    expect(temAprPendente(equipe({ situacao: 'ENCERRADO', agenda: [agenda('EM_PREENCHIMENTO')] }))).toBe(true);
    expect(temAprPendente(equipe({ situacao: 'SEM_TURNO', agenda: [agenda('PENDENTE')] }))).toBe(false);
    expect(temAprPendente(equipe({ situacao: 'EM_TURNO', agenda: [agenda('LIBERADA'), agenda('IMPEDIDA')] }))).toBe(false);
    expect(temAprPendente(equipe({ situacao: 'EM_TURNO', agenda: [{ ...agenda('PENDENTE'), itensConcluidos: 1 }] }))).toBe(false);
  });

  it('resumo recalcula pelo recorte e só compara a meta com quem tem meta', () => {
    const r = resumirBoletim([
      equipe({ situacao: 'EM_TURNO', noDia: { programado: 100, executado: 50 }, metaDiaria: 200 }),
      equipe({ situacao: 'ENCERRADO', noDia: { programado: 10, executado: 30 } }),
      equipe({ agendamentoSemTurno: true })
    ]);

    expect(r).toMatchObject({ equipes: 3, emTurno: 1, encerrados: 1, semTurno: 1, agendadasSemTurno: 1, programado: 110, executado: 80, meta: 200, percentualMeta: 25 });
    expect(resumirBoletim([equipe({})]).percentualMeta).toBeNull();
  });

  it('o que pede ação vem primeiro', () => {
    const lista = ordenarEquipes([
      equipe({ codigo: 'SEM' }),
      equipe({ codigo: 'ENC', situacao: 'ENCERRADO' }),
      equipe({ codigo: 'OK', situacao: 'EM_TURNO' }),
      equipe({ codigo: 'APR', situacao: 'EM_TURNO', agenda: [agenda('PENDENTE')] }),
      equipe({ codigo: 'AGE', agendamentoSemTurno: true, agenda: [agenda('PENDENTE')] })
    ]);

    expect(lista.map((e) => e.codigo)).toEqual(['AGE', 'APR', 'OK', 'ENC', 'SEM']);
  });

  it('filtros e agrupamento separam as equipes', () => {
    const emTurno = equipe({ codigo: 'A', situacao: 'EM_TURNO', baseOperacional: 'NORTE', percentualMeta: 120 });
    const semBase = equipe({ codigo: 'B', baseOperacional: null, percentualMeta: 10 });

    expect(atendeFiltro(emTurno, 'em-turno')).toBe(true);
    expect(atendeFiltro(semBase, 'em-turno')).toBe(false);
    expect(atendeFiltro(semBase, 'sem-turno')).toBe(true);
    expect(agruparEquipes([emTurno, semBase], 'base').map((g) => g.rotulo)).toEqual(['NORTE', 'Sem base operacional']);
    expect(agruparEquipes([emTurno, semBase], 'meta').map((g) => g.chave)).toEqual(['danger', 'success']);
    expect(agruparEquipes([], 'nenhum')).toEqual([]);
  });

  it('gráfico deixa de fora quem não tinha nada no dia e ordena pela execução', () => {
    const s = serieDoBoletim([
      equipe({ codigo: 'PARADA' }),
      equipe({ codigo: 'MENOR', situacao: 'EM_TURNO', noDia: { programado: 9, executado: 1 } }),
      equipe({ codigo: 'MAIOR', noDia: { programado: 0, executado: 5 }, metaDiaria: 7 })
    ]);

    expect(s.rotulos).toEqual(['MAIOR', 'MENOR']);
    expect(s.series.map((x) => x.name)).toEqual(['Executado', 'Programado', 'Meta diária']);
  });

  it('veredito prioriza programação sem turno, depois APR pendente', () => {
    const agendada = resumirBoletim([equipe({ agendamentoSemTurno: true }), equipe({ situacao: 'EM_TURNO', agenda: [agenda('PENDENTE')] })]);
    const soApr = resumirBoletim([equipe({ situacao: 'EM_TURNO', agenda: [agenda('PENDENTE')] })]);
    const emDia = resumirBoletim([equipe({ situacao: 'EM_TURNO', agenda: [agenda('LIBERADA')] })]);

    expect(visaoDoBoletim({ hoje: true }, agendada).veredito.tom).toBe('danger');
    expect(visaoDoBoletim({ hoje: true }, soApr).veredito.titulo).toBe('Serviço sem APR concluída');
    expect(visaoDoBoletim({ hoje: true }, emDia).veredito.tom).toBe('success');
  });

  it('análise abre só para alvos conhecidos e equipes do recorte', () => {
    const lista = [equipe({ codigo: 'A', idEquipe: 'a', idTurno: 't', situacao: 'EM_TURNO' })];

    expect(vistaDoBoletim({ dimension: 'equipe', value: 'a' }, lista, '30/10')?.links?.[0].rota).toEqual(['/obras/turnos', 't']);
    expect(vistaDoBoletim({ dimension: 'equipe', value: 'x' }, lista, '30/10')).toBeNull();
    expect(vistaDoBoletim({ dimension: 'kpi', value: 'turnos' }, lista, '30/10')?.tabelas?.[0].linhas).toHaveLength(1);
    expect(vistaDoBoletim({ dimension: 'kpi', value: 'nada' }, lista, '30/10')).toBeNull();
  });
});
