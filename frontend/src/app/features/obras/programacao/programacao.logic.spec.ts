import { describe, expect, it } from 'vitest';
import { atendeFiltrosBarra, atendeSituacao, temFiltroBarra, temConflito, datasArrastadas, deslocarVisao, diasDoPeriodo, formatarMinutos, mesDe, posicionarBarras, proximoDiaDeSemana, semanaDe, sugerirDescricao, tituloDoPeriodo, turnosCruzam } from './programacao.logic';

const INICIO = new Date(2026, 10, 2);
const FIM = new Date(2026, 10, 8);

describe('programacao.logic', () => {
  it('formata minutos, inclusive capacidade negativa', () => {
    expect(formatarMinutos(330)).toBe('5h30');
    expect(formatarMinutos(-106)).toBe('−1h46');
    expect(formatarMinutos(null)).toBe('—');
  });

  it('lista os dias do período e marca fim de semana e hoje', () => {
    const dias = diasDoPeriodo(INICIO, FIM, new Date(2026, 10, 4));

    expect(dias).toHaveLength(7);
    expect(dias[0].semana).toBe('seg');
    expect(dias[5].fimDeSemana).toBe(true);
    expect(dias.findIndex((d) => d.hoje)).toBe(2);
  });

  it('atravessa a virada do horário e do mês sem pular dia', () => {
    expect(diasDoPeriodo(new Date(2026, 9, 30), new Date(2026, 10, 2))).toHaveLength(4);
  });

  it('coloca programações que se cruzam em faixas diferentes', () => {
    const barras = posicionarBarras(
      [
        { inicio: '02/11/2026', fim: '04/11/2026' },
        { inicio: '03/11/2026', fim: '05/11/2026' },
        { inicio: '05/11/2026', fim: '06/11/2026' }
      ],
      INICIO,
      FIM
    );

    expect(barras.map((b) => b.faixa)).toEqual([0, 1, 0]);
    expect(barras[1].coluna).toBe(1);
    expect(barras[1].largura).toBe(3);
  });

  it('recorta o que passa do período e descarta o que está fora', () => {
    const barras = posicionarBarras(
      [
        { inicio: '30/10/2026', fim: '03/11/2026' },
        { inicio: '07/11/2026', fim: '15/11/2026' },
        { inicio: '20/11/2026', fim: '21/11/2026' }
      ],
      INICIO,
      FIM
    );

    expect(barras).toHaveLength(2);
    expect(barras[0]).toMatchObject({ coluna: 0, largura: 2, cortadaNoInicio: true, cortadaNoFim: false });
    expect(barras[1]).toMatchObject({ coluna: 5, largura: 2, cortadaNoInicio: false, cortadaNoFim: true });
  });

  it('conta a semana de segunda a domingo, mesmo a partir do domingo', () => {
    const semana = semanaDe(new Date(2026, 10, 1));

    expect(semana.inicio).toEqual(new Date(2026, 9, 26));
    expect(semana.fim).toEqual(new Date(2026, 10, 1));
  });

  it('navega de semana em semana e de mês em mês', () => {
    expect(deslocarVisao('semana', INICIO, 1)).toEqual(new Date(2026, 10, 9));
    expect(deslocarVisao('mes', new Date(2026, 0, 31), 1)).toEqual(new Date(2026, 1, 1));
  });

  it('escreve o período por extenso', () => {
    expect(tituloDoPeriodo('mes', mesDe(INICIO))).toBe('novembro de 2026');
    expect(tituloDoPeriodo('semana', semanaDe(INICIO))).toBe('2 a 8 de novembro de 2026');
    expect(tituloDoPeriodo('semana', semanaDe(new Date(2026, 9, 30)))).toBe('26 de outubro a 1 de novembro de 2026');
  });

  it('pula o fim de semana ao sugerir a data', () => {
    expect(proximoDiaDeSemana(new Date(2026, 10, 7))).toEqual(new Date(2026, 10, 9));
    expect(proximoDiaDeSemana(new Date(2026, 10, 3))).toEqual(new Date(2026, 10, 3));
  });

  it('só vê conflito quando os turnos cruzam', () => {
    expect(turnosCruzam('MANHA', 'TARDE')).toBe(false);
    expect(turnosCruzam('MANHA', 'DIA_TODO')).toBe(true);
    expect(turnosCruzam('TARDE', 'TARDE')).toBe(true);
    expect(turnosCruzam('MANHA', null)).toBe(true);
  });

  it('sugere a descrição pela seleção', () => {
    expect(sugerirDescricao(['P1', 'P2'], ['KIT-1', 'KIT-1'])).toBe('P1, P2 — KIT-1');
    expect(sugerirDescricao(['P1', 'P2', 'P3', 'P4', 'P5', 'P6'], [])).toBe('P1, P2, P3, P4 e mais 2');
  });

  it('arrasta mantendo a duração e impede período invertido pelas pontas', () => {
    expect(datasArrastadas(INICIO, FIM, 'mover', 3)).toEqual({ inicio: new Date(2026, 10, 5), fim: new Date(2026, 10, 11) });
    expect(datasArrastadas(INICIO, FIM, 'fim', -2).fim).toEqual(new Date(2026, 10, 6));
    expect(datasArrastadas(INICIO, FIM, 'fim', -30)).toEqual({ inicio: INICIO, fim: INICIO });
    expect(datasArrastadas(INICIO, FIM, 'inicio', 30)).toEqual({ inicio: FIM, fim: FIM });
  });

  it('acusa conflito só entre programações abertas em turnos que cruzam', () => {
    const base = { inicio: '03/11/2026', fim: '05/11/2026', situacao: 'PROGRAMADA' as const };

    expect(temConflito([{ ...base, turno: 'DIA_TODO' }, { ...base, turno: 'MANHA' }])).toBe(true);
    expect(temConflito([{ ...base, turno: 'MANHA' }, { ...base, turno: 'TARDE' }])).toBe(false);
    expect(temConflito([{ ...base, turno: 'DIA_TODO' }, { ...base, turno: 'DIA_TODO', situacao: 'CANCELADA' }])).toBe(false);
    expect(temConflito([{ ...base, turno: 'DIA_TODO' }, { inicio: '06/11/2026', fim: '07/11/2026', situacao: 'PROGRAMADA', turno: 'DIA_TODO' }])).toBe(false);
  });

  it('filtra a programação pelos dados dela', () => {
    const barra = { idObra: 'o1', municipio: 'JUAZEIRO', prioridade: 'ALTA', turno: 'MANHA', linhaViva: false, desligamento: true, finalizaObra: false, situacaoIntervencao: 'CRIADO' } as never;

    expect(atendeFiltrosBarra(barra, {})).toBe(true);
    expect(atendeFiltrosBarra(barra, { municipio: 'JUAZEIRO', turno: 'MANHA', desligamento: true, linhaViva: false })).toBe(true);
    expect(atendeFiltrosBarra(barra, { linhaViva: true })).toBe(false);
    expect(atendeFiltrosBarra(barra, { idObra: 'o2' })).toBe(false);
    expect(temFiltroBarra({ linhaViva: false })).toBe(true);
    expect(temFiltroBarra({ linhaViva: null, idObra: null })).toBe(false);
  });

  it('sem situação escolhida deixa de fora só as canceladas', () => {
    expect(atendeSituacao('CONCLUIDA', [])).toBe(true);
    expect(atendeSituacao('CANCELADA', [])).toBe(false);
    expect(atendeSituacao('CANCELADA', ['CANCELADA'])).toBe(true);
    expect(atendeSituacao('ATRASADA', ['PROGRAMADA'])).toBe(false);
  });

  it('calcula o mês de uma data', () => {
    const mes = mesDe(new Date(2026, 1, 10));

    expect(mes.inicio.getDate()).toBe(1);
    expect(mes.fim.getDate()).toBe(28);
  });
});
