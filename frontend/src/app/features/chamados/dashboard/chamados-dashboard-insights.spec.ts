import { describe, expect, it } from 'vitest';
import { DashboardChamadosResponse } from '../../../core/models/chamado-dashboard.models';
import { buildChamadosInsights, toPlainChamadosLanguage } from './chamados-dashboard-insights';

describe('Chamados dashboard insights', () => {
  const baseResponse: DashboardChamadosResponse = {
    inicio: '2026-03-01T00:00:00Z',
    fim: '2026-03-11T23:59:59Z',
    atualizadoEm: '2026-03-11T12:00:00Z',
    totalChamados: 10,
    totalAbertos: 2,
    totalEmAtendimento: 1,
    totalAguardandoSolicitante: 0,
    totalResolvidos: 8,
    totalFechados: 8,
    totalCancelados: 0,
    totalPendentes: 2,
    totalCriticos: 1,
    totalReabertos: 0,
    taxaResolucao: 80,
    taxaSlaPrimeiraRespostaPercentual: 95,
    taxaSlaResolucaoPercentual: 90,
    taxaReabertura: 0,
    tempoMedianoPrimeiraRespostaHoras: 2,
    tempoP90PrimeiraRespostaHoras: 4,
    tempoMedianoResolucaoHoras: 12,
    tempoP90ResolucaoHoras: 24,
    tempoMedioResolucaoHoras: 15,
    totalPrimeiraRespostaNoPrazo: 9,
    totalPrimeiraRespostaVencida: 1,
    totalResolucaoNoPrazo: 8,
    totalResolucaoVencida: 0,
    totalPorStatus: {
      ABERTO: 1,
      EM_ATENDIMENTO: 1,
      AGUARDANDO_SOLICITANTE: 0,
      RESOLVIDO: 4,
      FECHADO: 4,
      CANCELADO: 0
    },
    totalPorPrioridade: {
      BAIXA: 2,
      NORMAL: 5,
      ALTA: 2,
      CRITICA: 1
    },
    granularidade: 'DIA',
    periodo: {
      inicio: '2026-03-01T00:00:00Z',
      fim: '2026-03-11T23:59:59Z',
      timezone: 'America/Sao_Paulo',
      comparacaoInicio: '2026-02-19T00:00:00Z',
      comparacaoFim: '2026-02-28T23:59:59Z'
    },
    qualidadeDados: {
      ultimaCargaEm: '2026-03-11T12:00:00Z',
      chamadosSemResponsavel: 0,
      chamadosComSlaPausado: 0,
      chamadosEstagnadosSemInteracao: 0,
      coberturaResponsavelPercentual: 100
    },
    indicadores: {
      totalChamados: { valor: 10, anterior: 5, variacaoPercentual: 100, status: 'REGULAR', definicao: 'Total de chamados' },
      chamadosResolvidos: { valor: 8, anterior: 4, variacaoPercentual: 100, status: 'REGULAR', definicao: 'Chamados resolvidos' },
      backlogTotal: { valor: 2, anterior: 1, variacaoPercentual: 100, status: 'REGULAR', definicao: 'Fila aberta' },
      taxaSlaResolucao: { valor: 90, anterior: 85, variacaoPercentual: 5.8, status: 'REGULAR', definicao: 'SLA de resolução' }
    },
    prioridades: [],
    volumePorPrioridade: [],
    backlogPorIdade: [],
    desempenhoPorTopico: [],
    desempenhoPorEquipe: [],
    desempenhoPorAtendente: [],
    rankingSolicitantes: [],
    serieTemporal: []
  };

  it('substitui jargões técnicos por linguagem de gestão clara', () => {
    const texto = 'Atenção ao SLA e ao backlog de chamados com P90 elevado';
    const traduzido = toPlainChamadosLanguage(texto);

    expect(traduzido).toContain('prazo contratual');
    expect(traduzido).toContain('fila de pendências em aberto');
    expect(traduzido).toContain('prazo de 9 em cada 10 chamados');
  });

  it('retorna insight de operação regular quando não houver prioridades críticas', () => {
    const insights = buildChamadosInsights(baseResponse);

    expect(insights).toHaveLength(1);
    expect(insights[0].tone).toBe('positive');
    expect(insights[0].title).toBe('Operação regular');
  });

  it('mapeia severidade CRITICA para tom critical com ícone pi-bolt', () => {
    const responseComCritica: DashboardChamadosResponse = {
      ...baseResponse,
      prioridades: [
        {
          severidade: 'CRITICA',
          tipo: 'SLA_ESTOURADO',
          titulo: 'Chamados com SLA estourado',
          evidencia: 'Existem 5 chamados com SLA vencido',
          acaoSugerida: 'Cobrar equipe responsável'
        }
      ]
    };

    const insights = buildChamadosInsights(responseComCritica);

    expect(insights).toHaveLength(1);
    expect(insights[0].tone).toBe('critical');
    expect(insights[0].icon).toBe('pi-bolt');
    expect(insights[0].title).toContain('Chamados com prazo contratual estourado');
  });

  it('mapeia severidade ALTA para tom warning com ícone pi-clock', () => {
    const responseComAlta: DashboardChamadosResponse = {
      ...baseResponse,
      prioridades: [
        {
          severidade: 'ALTA',
          tipo: 'REABERTURAS_ELEVADAS',
          titulo: 'Taxa de reabertura alta',
          evidencia: 'Taxa acima de 10%',
          acaoSugerida: 'Revisar qualidade dos atendimentos'
        }
      ]
    };

    const insights = buildChamadosInsights(responseComAlta);

    expect(insights).toHaveLength(1);
    expect(insights[0].tone).toBe('warning');
    expect(insights[0].icon).toBe('pi-clock');
  });
});
