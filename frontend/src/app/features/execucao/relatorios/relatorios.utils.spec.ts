import { describe, expect, it } from 'vitest';
import { HistoricoEquipeTurno, IndicadoresTurno, LancamentoDoTurno } from '../../../core/models/dashboard.models';
import { ExecucaoComercialResponse } from '../../../core/models/execucao.models';
import {
  acoesMaisExecutadas,
  distanciaKm,
  distanciaTexto,
  insightsDaExecucao,
  insightsDoTurno,
  materiaisMovimentados,
  notasComIntervalo,
  posicaoNoTurno,
  serieDoTurno,
  tiposServicoDasNotas,
  variacaoPercentual
} from './relatorios.utils';

function indicadores(parcial: Partial<IndicadoresTurno> = {}): IndicadoresTurno {
  return {
    calculadoEm: '2026-10-09T18:00:00Z',
    emAndamento: false,
    abertoDesdeOutroDia: false,
    duracaoMinutos: 480,
    notas: 6,
    producao: 1200,
    valorMedioNota: 200,
    producaoPorHora: 150,
    metaDiaria: 1000,
    percentualMetaDiaria: 120,
    minutosAtePrimeiroLancamento: 20,
    minutosAposUltimoLancamento: 30,
    maiorIntervaloSemLancamentoMinutos: 60,
    lancamentosSemLocalizacao: 0,
    ...parcial
  };
}

const historico = (media: number | null, turnos = 10): HistoricoEquipeTurno => ({
  dias: 30,
  turnos,
  mediaNotasPorTurno: 5,
  mediaProducaoPorTurno: media,
  mediaDuracaoMinutos: 470,
  valorMedioNota: 190
});

function lancamento(id: string, hora: string, lat: number | null = -12.97, lng: number | null = -38.5): LancamentoDoTurno {
  return {
    idExecucao: id,
    referenciaNota: `N-${id}`,
    dataLancamento: `2026-10-09T${hora}:00-03:00`,
    status: 'CONCLUIDO',
    valor: 100,
    latitude: lat,
    longitude: lng
  };
}

function execucao(parcial: Partial<ExecucaoComercialResponse> = {}): ExecucaoComercialResponse {
  return {
    id: 'e2',
    referenciaNota: 'N-e2',
    idTurno: 't1',
    tipoServico: null,
    equipe: { id: 'eq', codigo: 'EQ-01' } as ExecucaoComercialResponse['equipe'],
    municipio: null,
    latitude: -12.97,
    longitude: -38.5,
    dataLancamento: '2026-10-09T11:00:00-03:00',
    statusExecucao: 'CONCLUIDO',
    valorTotalFaturado: 500,
    acoes: [{ id: 'x', acao: { id: 'a1', codigo: 'A1', nome: 'Religação' } as never }],
    materiais: [],
    tipoNota: 'COMUM',
    referenciaProvisoria: null,
    dataNumeroOficial: null,
    aguardandoNumeroOficial: false,
    totalAlteracoes: 0,
    alterada: false,
    ...parcial
  };
}

describe('insightsDoTurno', () => {
  it('turno bom: meta batida e produção acima da média aparecem como positivos, sem alertas', () => {
    const insights = insightsDoTurno(indicadores(), historico(900));
    expect(insights.map((i) => i.tom)).toEqual(['success', 'success', 'info']);
    expect(insights[0].titulo).toBe('Meta do dia atingida');
    expect(insights[1].titulo).toBe('Produção 33% acima da média da equipe');
  });

  it('turno ruim: alertas vêm primeiro, do mais grave ao menos grave', () => {
    const insights = insightsDoTurno(
      indicadores({
        producao: 500,
        percentualMetaDiaria: 50,
        minutosAtePrimeiroLancamento: 120,
        maiorIntervaloSemLancamentoMinutos: 180,
        minutosAposUltimoLancamento: 100,
        lancamentosSemLocalizacao: 2
      }),
      historico(1000)
    );
    const titulos = insights.map((i) => i.titulo);
    expect(insights[0]).toMatchObject({ tom: 'danger', titulo: 'Meta do dia não atingida' });
    expect(titulos).toContain('Produção 50% abaixo da média da equipe');
    expect(titulos).toContain('3h sem lançar');
    expect(titulos).toContain('Primeira nota demorou');
    expect(titulos).toContain('Fim de turno sem produção');
    expect(titulos).toContain('2 notas sem localização');
  });

  it('turno esquecido aberto e sem notas é o primeiro destaque', () => {
    const insights = insightsDoTurno(
      indicadores({
        emAndamento: true,
        abertoDesdeOutroDia: true,
        notas: 0,
        producao: 0,
        percentualMetaDiaria: 0,
        minutosAtePrimeiroLancamento: null,
        minutosAposUltimoLancamento: null,
        maiorIntervaloSemLancamentoMinutos: null,
        producaoPorHora: 0,
        duracaoMinutos: 1300
      }),
      historico(null, 0)
    );
    expect(insights[0].titulo).toBe('Turno aberto desde outro dia');
    expect(insights[1]).toMatchObject({ tom: 'warning', titulo: 'Nenhuma nota lançada' });
    // Em andamento: meta parcial não é cobrada ainda.
    expect(insights.some((i) => i.titulo.startsWith('Meta'))).toBe(false);
  });
});

describe('posição e distância', () => {
  it('situa a nota entre as do turno, com intervalo e distância da anterior', () => {
    const lancamentos = [lancamento('e1', '08:00', -12.97, -38.5), lancamento('e2', '10:30', -12.98, -38.5)];
    const posicao = posicaoNoTurno(lancamentos, 'e2')!;
    expect(posicao.posicao).toBe(2);
    expect(posicao.total).toBe(2);
    expect(posicao.anterior?.idExecucao).toBe('e1');
    expect(posicao.proxima).toBeNull();
    expect(posicao.minutosDesdeAnterior).toBe(150);
    expect(posicao.distanciaDaAnteriorKm).toBeCloseTo(1.11, 1);
    expect(posicaoNoTurno(lancamentos, 'outra')).toBeNull();
  });

  it('distância nula sem coordenadas; texto em metros abaixo de 1 km', () => {
    expect(distanciaKm({ latitude: null, longitude: 1 }, { latitude: 1, longitude: 1 })).toBeNull();
    expect(distanciaTexto(0.42)).toBe('420 m');
    expect(distanciaTexto(12.345)).toBe('12,3 km');
  });

  it('variação percentual exige referência positiva', () => {
    expect(variacaoPercentual(150, 100)).toBe(50);
    expect(variacaoPercentual(150, 0)).toBeNull();
    expect(variacaoPercentual(150, null)).toBeNull();
  });
});

describe('insightsDaExecucao', () => {
  it('nota muito acima da média e lançada fora do horário do turno', () => {
    const insights = insightsDaExecucao(execucao(), {
      idExecucao: 'e2',
      turno: { id: 't1', status: 'FECHADO', dataInicio: '2026-10-09T07:00:00-03:00', dataFim: '2026-10-09T10:00:00-03:00' },
      lancamentosDoTurno: [lancamento('e1', '07:30'), lancamento('e2', '11:00')],
      diaDaEquipe: null,
      referenciaEquipe: { dias: 30, notas: 40, valorMedioNota: 200 },
      valoresPorAcao: []
    });
    const titulos = insights.map((i) => i.titulo);
    expect(titulos).toContain('Valor 150% acima da média da equipe');
    expect(titulos).toContain('Lançada fora do horário do turno');
    expect(titulos).toContain('3h 30min desde a nota anterior');
  });

  it('sem contexto ainda avisa nota sem localização e sem ações', () => {
    const insights = insightsDaExecucao(execucao({ latitude: undefined, longitude: undefined, acoes: [] }), null);
    expect(insights.map((i) => i.titulo)).toEqual(['Lançada sem localização', 'Nota sem ações']);
  });
});

describe('agregações das notas do turno', () => {
  const notas = [
    execucao({
      id: 'e1',
      dataLancamento: '2026-10-09T08:00:00-03:00',
      valorTotalFaturado: 100,
      acoes: [
        { id: '1', acao: { id: 'a1', codigo: 'A1', nome: 'Religação' } as never },
        { id: '2', acao: { id: 'a2', codigo: 'A2', nome: 'Corte' } as never }
      ],
      materiais: [
        { id: 'm1', material: { id: 'mat', codigo: 'M1', descricao: 'Cabo', unidadeMedida: 'M' } as never, quantidade: 2, operacao: 'APLICADO', origem: 'EQUIPE' },
        { id: 'm2', material: { id: 'mat', codigo: 'M1', descricao: 'Cabo', unidadeMedida: 'M' } as never, quantidade: 1, operacao: 'RETIRADO', origem: 'EQUIPE' }
      ]
    }),
    execucao({
      id: 'e2',
      dataLancamento: '2026-10-09T07:30:00-03:00',
      valorTotalFaturado: 300,
      acoes: [{ id: '3', acao: { id: 'a1', codigo: 'A1', nome: 'Religação' } as never }]
    }),
    execucao({ id: 'e3', dataLancamento: '2026-10-09T09:00:00-03:00', statusExecucao: 'EDITADO', valorTotalFaturado: 999 })
  ];

  it('ações por frequência, com a fatia das notas', () => {
    const acoes = acoesMaisExecutadas(notas);
    expect(acoes[0]).toMatchObject({ codigo: 'A1', quantidade: 3, percentualNotas: 100 });
    expect(acoes[1]).toMatchObject({ codigo: 'A2', quantidade: 1 });
  });

  it('tipos de serviço: mais notas primeiro, produção só das concluídas e "Não informado" por último', () => {
    const corte = { id: 'ts-cs', codigo: 'CS', descricao: 'CORTE', ativo: true };
    const ligacao = { id: 'ts-cl', codigo: 'CL', descricao: 'LIGAÇÃO NOVA', ativo: true };
    const tipos = tiposServicoDasNotas([
      execucao({ id: 'a', tipoServico: null, valorTotalFaturado: 50 }),
      execucao({ id: 'b', tipoServico: corte, valorTotalFaturado: 100 }),
      execucao({ id: 'c', tipoServico: corte, valorTotalFaturado: 999, statusExecucao: 'EDITADO' }),
      execucao({ id: 'd', tipoServico: ligacao, valorTotalFaturado: 300 })
    ]);
    expect(tipos.map((t) => [t.descricao, t.notas, t.producao, t.percentualNotas])).toEqual([
      ['CORTE', 2, 100, 50],
      ['LIGAÇÃO NOVA', 1, 300, 25],
      ['Não informado', 1, 50, 25]
    ]);
    expect(tipos[2]).toMatchObject({ id: null, codigo: null });
    expect(tiposServicoDasNotas([])).toEqual([]);
  });

  it('materiais somados por operação', () => {
    expect(materiaisMovimentados(notas)).toEqual([
      { codigo: 'M1', descricao: 'Cabo', unidade: 'M', aplicado: 2, retirado: 1 }
    ]);
  });

  it('notas em ordem com o intervalo desde a anterior (a primeira, desde a abertura)', () => {
    const ordenadas = notasComIntervalo(notas, '2026-10-09T07:00:00-03:00');
    expect(ordenadas.map((n) => n.id)).toEqual(['e2', 'e1', 'e3']);
    expect(ordenadas.map((n) => n.minutosDesdeAnterior)).toEqual([30, 30, 60]);
  });

  it('série acumulada só com concluídas, e a meta como linha constante', () => {
    const serie = serieDoTurno(notas, '2026-10-09T07:00:00-03:00', 1000);
    expect(serie.rotulos).toHaveLength(3);
    expect(serie.series[0].values).toEqual([0, 300, 400]);
    expect(serie.series[1]).toEqual({ name: 'Meta do dia', values: [1000, 1000, 1000] });
    expect(serieDoTurno(notas, '2026-10-09T07:00:00-03:00', null).series).toHaveLength(1);
  });
});
