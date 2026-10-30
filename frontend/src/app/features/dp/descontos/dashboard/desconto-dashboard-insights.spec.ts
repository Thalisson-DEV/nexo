import { describe, expect, it } from 'vitest';

import {
  DashboardDescontosResponse,
  PrioridadeDescontoItem
} from '../../../../core/models/desconto-dashboard.models';
import {
  construirInsightsDeDescontos,
  formatarMoeda,
  formatarMoedaCompacta,
  formatarPercentual
} from './desconto-dashboard-insights';

function respostaMinima(parcial: Partial<DashboardDescontosResponse> = {}): DashboardDescontosResponse {
  return {
    inicio: '2026-08-01T00:00:00-03:00',
    fim: '2026-08-31T23:59:59-03:00',
    granularidade: 'DIA',
    atualizadoEm: '2026-08-31T18:00:00-03:00',
    periodo: {
      inicio: '2026-08-01T00:00:00-03:00',
      fim: '2026-08-31T23:59:59-03:00',
      timezone: 'America/Sao_Paulo',
      comparacaoInicio: '2026-07-01T00:00:00-03:00',
      comparacaoFim: '2026-07-31T23:59:59-03:00',
      diasNoPeriodo: 31
    },
    resumo: {
      totalDescontos: 0, totalPeriodoAnterior: 0, variacaoPercentual: 0,
      solicitados: 0, validados: 0, baixados: 0, rejeitados: 0,
      pendentes: 0, percentualPendentes: 0, finalizados: 0,
      taxaAprovacao: 0, taxaRejeicao: 0,
      colaboradoresAtingidos: 0, colaboradoresAtivos: 0, percentualDoEfetivo: 0,
      colaboradoresReincidentes: 0, mediaDescontosPorColaborador: 0,
      totalItens: 0, mediaItensPorDesconto: 0, mediaParcelas: 0
    },
    financeiro: {
      valorSolicitado: 0, valorValidado: 0, valorBaixado: 0,
      valorBaixadoPeriodoAnterior: 0, variacaoValorBaixado: 0,
      valorRejeitado: 0, valorEmAberto: 0,
      glosaValidacao: 0, percentualGlosaValidacao: 0,
      ajusteBaixa: 0, percentualAjusteBaixa: 0,
      taxaRecuperacao: 0, ticketMedioSolicitado: 0, ticketMedioBaixado: 0,
      maiorValorSolicitado: 0, valorMedioParcela: 0
    },
    ciclo: {
      mediaDiasAteValidacao: 0, medianaDiasAteValidacao: 0, p90DiasAteValidacao: 0,
      mediaDiasAteBaixa: 0, medianaDiasAteBaixa: 0, p90DiasAteBaixa: 0,
      mediaDiasCicloTotal: 0, medianaDiasCicloTotal: 0, p90DiasCicloTotal: 0,
      mediaDiasAteRejeicao: 0, idadeMediaPendentesDias: 0, idadeMaximaPendenteDias: 0,
      amostraValidacao: 0, amostraBaixa: 0
    },
    qualidade: {
      ultimaCargaEm: '2026-08-31T18:00:00-03:00',
      pendentesSemAnexo: 0, pendentesSemItens: 0, pendentesSemObservacao: 0,
      coberturaDocumentalPercentual: 0, colaboradoresNaoIdentificados: 0
    },
    indicadores: {},
    funil: [], porStatus: [], serieTemporal: [], serieTemporalCriacao: [], envelhecimento: [],
    porMotivo: [], porTipoEquipamento: [], porColaborador: [],
    porBaseOperacional: [], porCentroCusto: [], porFuncao: [], porSolicitante: [],
    prioridades: [],
    ...parcial
  };
}

const prioridade: PrioridadeDescontoItem = {
  tipo: 'VALIDACAO_PARADA',
  severidade: 'CRITICA',
  titulo: '4 termo(s) há mais de 15 dias aguardando validação',
  evidencia: '2 passaram de 30 dias',
  acaoSugerida: 'Revisar e validar ou rejeitar',
  amostraIds: ['a', 'b'],
  drilldown: { status: ['SOLICITADO'] }
};

describe('formatação do dashboard de descontos', () => {
  it('abrevia valores grandes sem perder a moeda', () => {
    expect(formatarMoedaCompacta(1_500_000)).toBe('R$ 1,5 mi');
    expect(formatarMoedaCompacta(2_400)).toBe('R$ 2,4 mil');
    expect(formatarMoedaCompacta(87.5)).toContain('87,50');
  });

  it('trata nulo como zero em vez de quebrar a célula', () => {
    expect(formatarMoeda(null)).toContain('0,00');
    expect(formatarPercentual(undefined)).toBe('0%');
  });
});

describe('leitura gerencial do dashboard de descontos', () => {
  it('coloca as prioridades do backend antes das leituras de contexto', () => {
    const insights = construirInsightsDeDescontos(
      respostaMinima({
        prioridades: [prioridade],
        resumo: { ...respostaMinima().resumo, totalDescontos: 10 },
        financeiro: {
          ...respostaMinima().financeiro,
          valorSolicitado: 1000, valorBaixado: 700, taxaRecuperacao: 70, glosaValidacao: 200, ajusteBaixa: 100
        }
      })
    );

    expect(insights[0].title).toBe(prioridade.titulo);
    expect(insights[0].tone).toBe('critical');
    expect(insights[0].icon).toBe('pi-hourglass');
    expect(insights[1].title).toBe('Recuperação do valor solicitado');
    expect(insights[1].description).toContain('70%');
  });

  it('sem prioridade e sem movimento, diz que não há nada a tratar', () => {
    const insights = construirInsightsDeDescontos(respostaMinima());

    expect(insights).toHaveLength(1);
    expect(insights[0].tone).toBe('positive');
  });

  it('marca o tempo de ciclo como alerta quando o P90 estoura um mês', () => {
    const insights = construirInsightsDeDescontos(
      respostaMinima({
        ciclo: {
          ...respostaMinima().ciclo,
          amostraValidacao: 12, medianaDiasAteValidacao: 4, p90DiasAteValidacao: 45,
          idadeMaximaPendenteDias: 60
        }
      })
    );

    const ciclo = insights.find((insight) => insight.title === 'Tempo do ciclo');
    expect(ciclo?.tone).toBe('warning');
    expect(ciclo?.action).toContain('60 dia(s)');
  });

  it('usa um ícone genérico para um tipo de prioridade que o backend venha a criar', () => {
    const insights = construirInsightsDeDescontos(
      respostaMinima({ prioridades: [{ ...prioridade, tipo: 'TIPO_FUTURO', severidade: 'MEDIA' }] })
    );

    expect(insights[0].icon).toBe('pi-exclamation-triangle');
    expect(insights[0].tone).toBe('neutral');
  });
});
