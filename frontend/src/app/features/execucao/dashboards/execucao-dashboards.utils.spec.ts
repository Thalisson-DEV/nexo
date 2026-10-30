import { describe, expect, it } from 'vitest';
import { EquipeOperacional, EquipeTurno } from '../../../core/models/dashboard.models';
import {
  agruparEquipesTurno,
  barrasProducao,
  duracao,
  isoLocal,
  janelaDoTurno,
  marcasDaLinhaDoTempo,
  moedaCompacta,
  nomeCurto,
  ordenarEquipesOperacional,
  ordenarEquipesTurno,
  serieAcumulada,
  serieMensal,
  seriePorEquipe,
  tiposEmDestaque,
  variacao
} from './execucao-dashboards.utils';

const t = (iso: string) => Date.parse(iso);

describe('utilitários dos dashboards de execução', () => {
  it('formata moeda de forma compacta', () => {
    expect(moedaCompacta(850)).toBe('R$ 850,00');
    expect(moedaCompacta(4200)).toBe('R$ 4.200');
    expect(moedaCompacta(12_340)).toBe('R$ 12,3 mil');
    expect(moedaCompacta(1_250_000)).toBe('R$ 1,25 mi');
    expect(moedaCompacta(null)).toBe('—');
  });

  it('formata duração e variação', () => {
    expect(duracao(45)).toBe('45 min');
    expect(duracao(120)).toBe('2h');
    expect(duracao(485)).toBe('8h 05min');
    expect(variacao(12.5)).toBe('+12,5%');
    expect(variacao(-3)).toBe('−3%');
    expect(variacao(null)).toBe('—');
  });

  it('normaliza as barras de produção pelo maior dia e mantém visível o dia pequeno', () => {
    const barras = barrasProducao([
      { data: '2026-10-01', notas: 2, valor: 1000, meta: null },
      { data: '2026-10-02', notas: 0, valor: 0, meta: null },
      { data: '2026-10-05', notas: 1, valor: 10, meta: null }
    ]);

    expect(barras.map((b) => b.altura)).toEqual([100, 0, 6]);
    expect(barras[1].vazio).toBe(true);
    expect(barras[1].rotulo).toBe('02/10: sem lançamentos');
    expect(barras[0].rotulo).toContain('2 notas');
  });

  it('posiciona os lançamentos na janela do turno aberto até agora', () => {
    const equipe = {
      abertura: '2026-10-09T07:00:00-03:00',
      fechamento: null,
      situacao: 'EM_TURNO' as const,
      lancamentos: [
        { idExecucao: 'a', dataLancamento: '2026-10-09T09:00:00-03:00', valor: 100, tipoServico: null },
        { idExecucao: 'b', dataLancamento: '2026-10-09T13:00:00-03:00', valor: 200, tipoServico: 'CORTE' }
      ]
    };
    const dia = { inicio: t('2026-10-09T00:00:00-03:00'), fim: t('2026-10-10T00:00:00-03:00') };

    const janela = janelaDoTurno(equipe, dia, t('2026-10-09T15:00:00-03:00'))!;
    const marcas = marcasDaLinhaDoTempo(equipe.lancamentos, janela);

    expect(janela.inicio).toBe(t('2026-10-09T07:00:00-03:00'));
    expect(janela.fim).toBe(t('2026-10-09T15:00:00-03:00'));
    expect(marcas.map((m) => m.posicao)).toEqual([25, 75]);
    expect(marcas[0].rotulo).toContain('Nota lançada às');
    expect(marcas[1].rotulo).toContain('Nota de CORTE lançada às');
  });

  it('resume os tipos de serviço da equipe e junta o excedente em "Outros"', () => {
    const tipo = (descricao: string, notas: number) => ({
      idTipoServico: descricao, codigo: 'C', descricao, notas, producao: notas * 100, valorMedioNota: 100, percentualNotas: 0
    });
    const resumo = tiposEmDestaque([tipo('CORTE', 5), tipo('RELIGAÇÃO', 3), tipo('VARREDURA', 2), tipo('ENLACE', 1), tipo('BAIXA ADM', 1)]);
    expect(resumo.map((r) => [r.rotulo, r.notas])).toEqual([['CORTE', 5], ['RELIGAÇÃO', 3], ['VARREDURA', 2], ['Outros', 2]]);
    expect(resumo[3].titulo).toBe('ENLACE: 1 · BAIXA ADM: 1');
    expect(tiposEmDestaque([tipo('CORTE', 1)])).toHaveLength(1);
  });

  it('limita ao dia um turno aberto desde o dia anterior e não cria janela sem dados', () => {
    const dia = { inicio: t('2026-10-09T00:00:00-03:00'), fim: t('2026-10-10T00:00:00-03:00') };
    const janela = janelaDoTurno(
      { abertura: '2026-10-08T20:00:00-03:00', fechamento: null, situacao: 'EM_TURNO', lancamentos: [] },
      dia,
      t('2026-10-09T15:00:00-03:00')
    );

    expect(janela?.inicio).toBe(dia.inicio);
    expect(janelaDoTurno({ abertura: null, fechamento: null, situacao: 'SEM_TURNO', lancamentos: [] }, dia, 0)).toBeNull();
  });

  it('ordena o operacional pondo primeiro quem está abaixo do ritmo', () => {
    const equipe = (codigo: string, situacao: EquipeOperacional['situacao'], percentualMeta: number | null, producao: number) =>
      ({ codigo, situacao, percentualMeta, producao }) as EquipeOperacional;
    const equipes = [
      equipe('EQ-03', 'SEM_META', null, 900),
      equipe('EQ-01', 'META_ATINGIDA', 120, 500),
      equipe('EQ-02', 'ABAIXO_DO_RITMO', 40, 100)
    ];

    expect(ordenarEquipesOperacional(equipes, 'meta').map((e) => e.codigo)).toEqual(['EQ-02', 'EQ-01', 'EQ-03']);
    expect(ordenarEquipesOperacional(equipes, 'producao').map((e) => e.codigo)).toEqual(['EQ-03', 'EQ-01', 'EQ-02']);
    expect(ordenarEquipesOperacional(equipes, 'equipe').map((e) => e.codigo)).toEqual(['EQ-01', 'EQ-02', 'EQ-03']);
  });

  it('ordena turnos pondo primeiro quem está há mais tempo sem lançar', () => {
    const equipe = (codigo: string, situacao: EquipeTurno['situacao'], semLancamentoRecente = false, minutos: number | null = null) =>
      ({ codigo, situacao, semLancamentoRecente, minutosSemLancamento: minutos }) as EquipeTurno;
    const ordem = ordenarEquipesTurno([
      equipe('EQ-04', 'SEM_TURNO'),
      equipe('EQ-03', 'ENCERRADO'),
      equipe('EQ-02', 'EM_TURNO', true, 130),
      equipe('EQ-01', 'EM_TURNO'),
      equipe('EQ-05', 'EM_TURNO', true, 300)
    ]);

    expect(ordem.map((e) => e.codigo)).toEqual(['EQ-05', 'EQ-02', 'EQ-01', 'EQ-03', 'EQ-04']);
  });

  it('agrupa as equipes de turno por base, processo e faixa da meta do dia', () => {
    const equipe = (codigo: string, base: string | null, producao: number, metaDiaria: number | null) =>
      ({
        codigo,
        baseOperacional: base,
        processo: 'CORTE',
        situacao: 'EM_TURNO',
        producao,
        metaDiaria,
        percentualMetaDiaria: metaDiaria ? (producao / metaDiaria) * 100 : null
      }) as EquipeTurno;
    const equipes = [equipe('EQ-01', 'Feira', 1200, 1000), equipe('EQ-02', null, 0, null), equipe('EQ-03', 'Aracaju', 850, 1000), equipe('EQ-04', 'Feira', 100, 1000)];

    const porBase = agruparEquipesTurno(equipes, 'base');
    expect(porBase.map((g) => [g.rotulo, g.equipes.map((e) => e.codigo)])).toEqual([
      ['Aracaju', ['EQ-03']],
      ['Feira', ['EQ-01', 'EQ-04']],
      ['Sem base operacional', ['EQ-02']]
    ]);
    expect(porBase[1].producao).toBe(1300);
    expect(porBase[1].percentualMeta).toBe(65);
    expect(porBase[2].percentualMeta).toBeNull();

    expect(agruparEquipesTurno(equipes, 'meta').map((g) => [g.chave, g.equipes.map((e) => e.codigo)])).toEqual([
      ['danger', ['EQ-04']],
      ['warning', ['EQ-03']],
      ['success', ['EQ-01']],
      ['neutral', ['EQ-02']]
    ]);
    expect(agruparEquipesTurno(equipes, 'processo')).toHaveLength(1);
    expect(agruparEquipesTurno(equipes, 'nenhum')[0].equipes).toHaveLength(4);
    expect(agruparEquipesTurno([], 'nenhum')).toEqual([]);
  });

  it('abrevia nomes e gera datas locais sem passar por UTC', () => {
    expect(nomeCurto('MARIA APARECIDA SOUZA')).toBe('Maria Souza');
    expect(nomeCurto('joão')).toBe('João');
    expect(isoLocal(new Date(2026, 9, 9, 23, 30))).toBe('2026-10-09');
  });

  it('monta as séries de produção × meta acumulada e mensal', () => {
    const dias = [
      { data: '2026-09-30', notas: 1, valor: 500, meta: 1000 },
      { data: '2026-10-01', notas: 2, valor: 1200, meta: 1000 },
      { data: '2026-10-03', notas: 0, valor: 0, meta: 0 }
    ];

    const acumulada = serieAcumulada(dias);
    expect(acumulada.rotulos).toEqual(['30/09', '01/10', '03/10']);
    expect(acumulada.series[0].values).toEqual([500, 1700, 1700]);
    expect(acumulada.series[1]).toEqual({ name: 'Meta acumulada', values: [1000, 2000, 2000] });

    const mensal = serieMensal(dias);
    expect(mensal.rotulos).toEqual(['set/26', 'out/26']);
    expect(mensal.series[0].values).toEqual([500, 1200]);
    expect(mensal.series[1].values).toEqual([1000, 1000]);
  });

  it('omite a série de meta quando ninguém tem meta e ordena equipes pela produção', () => {
    expect(serieAcumulada([{ data: '2026-10-01', notas: 1, valor: 10, meta: null }]).series).toHaveLength(1);

    const porEquipe = seriePorEquipe([
      { codigo: 'EQ-01', producao: 100, metaPeriodo: 500 },
      { codigo: 'EQ-02', producao: 900, metaPeriodo: null }
    ]);
    expect(porEquipe.rotulos).toEqual(['EQ-02', 'EQ-01']);
    expect(porEquipe.series[1].values).toEqual([null, 500]);
  });
});

