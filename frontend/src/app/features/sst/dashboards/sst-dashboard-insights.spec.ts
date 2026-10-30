import { describe, expect, it } from 'vitest';
import { DashboardDesviosResponse, DashboardInspecoesResponse } from '../../../core/models/sst.models';
import { DashboardEventosSstResponse } from '../../../core/models/sst-evento.models';
import { visaoDesvios } from './analise/analise-desvios';
import { visaoEventos } from './analise/analise-eventos';
import { visaoInspecoes } from './analise/visao-inspecoes';
import { toPlainDashboardLanguage } from './sst-dashboard-insights';

describe('Visão gerencial dos dashboards de SST', () => {
  const inspectionData = (overrides: Record<string, unknown> = {}) => ({
    resumo: {
      totalInspecoes: 10,
      totalPeriodoAnterior: 0,
      variacaoPercentual: 0,
      inspecoesComDesvio: 2,
      inspecoesSemDesvio: 8,
      percentualSemDesvio: 80,
      totalDesvios: 3,
      mediaDesviosPorInspecao: 0.3,
      colaboradoresInspecionados: 6,
      mediaColaboradoresPorInspecao: 1.2,
      inspetoresAtivos: 2,
      mediaInspecoesPorInspetor: 5,
      mediaInspecoesPorDia: 1.4
    },
    meta: {
      possuiMeta: true,
      metaPeriodo: 12.4,
      realizadoPeriodo: 10,
      percentualAtingido: 80.6,
      saldoParaMeta: 2.4,
      colaboradoresComMeta: 4,
      inspetoresNaMeta: 2,
      percentualInspetoresNaMeta: 50,
      colaboradoresSemAcesso: 0
    },
    colaboradores: {
      colaboradoresInspecionados: 6,
      colaboradoresAtivos: 10,
      percentualCobertura: 60,
      colaboradoresSemInspecao: 4,
      mediaInspecoesPorColaborador: 1.6,
      colaboradoresComDesvio: 2,
      percentualComDesvio: 20,
      colaboradoresReincidentes: 1
    },
    porInspetor: [
      { idInspetor: 'i1', nomeInspetor: 'Ana', realizado: 6, meta: 6, percentualAtingido: 100, atingiuMeta: true, desvios: 1, percentualDoTotal: 60 },
      { idInspetor: 'i2', nomeInspetor: 'Bia', realizado: 4, meta: 6.4, percentualAtingido: 62.5, atingiuMeta: false, desvios: 2, percentualDoTotal: 40 }
    ],
    porEquipe: [
      { idEquipe: 'e1', nomeEquipe: 'EQ-01', inspecoes: 10, percentualDoTotal: 100, desvios: 3, desviosPorInspecao: 0.3, percentualSemDesvio: 80, colaboradoresInspecionados: 6, ultimaInspecao: '2026-10-01', diasSemInspecao: 45 }
    ],
    ...overrides
  }) as unknown as DashboardInspecoesResponse;

  const eventosData = (overrides: Record<string, unknown> = {}) => ({
    resumo: {
      total: 5,
      totalAnterior: 3,
      variacaoPercentual: 66.7,
      diasSemEventos: 12,
      porTipo: { ACIDENTE_TIPICO: 2, INCIDENTE_SEM_LESAO: 3 },
      comLesao: 2,
      comLesaoAnterior: 1,
      variacaoComLesao: 100,
      semLesao: 3,
      diasPerdidos: 10,
      comCat: 1,
      coberturaCat: 50,
      mediaHorasComunicacao: 36,
      comunicacoesAtrasadas: 2,
      percentualAtrasadas: 40,
      abertos: 2,
      concluidos: 3,
      desviosNoPeriodo: 20,
      razaoDesviosPorLesao: 10
    },
    diasSemAcidente: [
      { idBaseOperacional: 'b1', baseNome: 'Base Central', diasSemAcidente: 5, ultimaOcorrencia: '2026-09-01', comLesaoNoPeriodo: 2 }
    ],
    backlog: [
      { faixa: 'Até 7 dias', idadeMinimaDias: 0, idadeMaximaDias: 7, total: 1, porStatus: {} },
      { faixa: '16 a 30 dias', idadeMinimaDias: 16, idadeMaximaDias: 30, total: 1, porStatus: {} }
    ],
    porClassificacao: [{ id: 'c1', nome: 'Queda', total: 3, percentual: 60 }],
    ...overrides
  }) as unknown as DashboardEventosSstResponse;

  describe('inspeções', () => {
    it('veredito pela meta quando comparável, com leitura dos inspetores abaixo dela', () => {
      const visao = visaoInspecoes(inspectionData(), true);
      expect(visao.veredito.tom).toBe('warning');
      expect(visao.veredito.titulo).toContain('80,6%');
      const meta = visao.leituras.find((l) => l.id === 'meta')!;
      expect(meta.valor).toBe('1 de 2');
      expect(meta.acao!.alvo).toEqual({ dimension: 'kpi', value: 'meta' });
    });

    it('avisa quem tem meta mas não consegue lançar inspeções', () => {
      const data = inspectionData({
        meta: {
          possuiMeta: true, metaPeriodo: 12, realizadoPeriodo: 10, percentualAtingido: 83.3,
          saldoParaMeta: 2, colaboradoresComMeta: 4, inspetoresNaMeta: 2,
          percentualInspetoresNaMeta: 50, colaboradoresSemAcesso: 3
        }
      });
      expect(visaoInspecoes(data, true).leituras.find((l) => l.id === 'meta')!.texto).toContain('não têm usuário');
    });

    it('sem meta comparável, o veredito usa a cobertura do efetivo e explica o porquê', () => {
      const visao = visaoInspecoes(inspectionData(), false);
      expect(visao.veredito.rotulo).toBe('Meta não comparável');
      expect(visao.veredito.texto).toContain('restringem só o realizado');
      expect(visao.leituras.some((l) => l.id === 'meta')).toBe(false);
    });

    it('sem inspeções no período, o veredito é de falta de cobertura', () => {
      const visao = visaoInspecoes(inspectionData({ resumo: { ...inspectionData().resumo, totalInspecoes: 0 } }), true);
      expect(visao.veredito.tom).toBe('danger');
    });

    it('equipe há 30+ dias sem inspeção vira leitura que abre a análise da equipe', () => {
      const leitura = visaoInspecoes(inspectionData(), true).leituras.find((l) => l.id === 'equipes-sem-inspecao')!;
      expect(leitura.acao!.alvo).toEqual({ dimension: 'equipe', value: 'e1' });
    });
  });

  describe('desvios', () => {
    const desvios = (overrides: Record<string, unknown> = {}) => ({
      totalDesvios: 10, totalCriticos: 2, percentualCriticos: 20, taxaResolucao: 50, totalResolvidos: 5,
      tempoMedianoResolucaoDias: 4, tempoP90ResolucaoDias: 12, totalSemPrazo: 1,
      indicadores: { backlog: { valor: 6, anterior: 4 }, vencidos: { valor: 3, anterior: 1 } },
      backlogPorIdade: [{ faixa: '31-60', idadeMinimaDias: 31, idadeMaximaDias: 60, total: 2, porGravidade: { CRITICA: 1, ALTA: 0, MEDIA: 1, BAIXA: 0 } }],
      itensCriticosRecorrentes: [{ idPergunta: 'p1', item: 'Uso de EPI', respostas: 20, reprovacoes: 5, taxaReprovacao: 25, maiorGravidade: 'ALTA', variacaoTaxaPercentual: 0, percentualAcumulado: 50 }],
      exposicaoPorEquipe: [],
      prioridades: [
        { tipo: 'SLA_CRITICO', severidade: 'CRITICA', titulo: 'SLA crítico', evidencia: 'Backlog acima do esperado', acaoSugerida: 'Revisar o SLA', drilldown: {} }
      ],
      ...overrides
    }) as unknown as DashboardDesviosResponse;

    it('vencidos graves e antigos pedem ação imediata', () => {
      const visao = visaoDesvios(desvios());
      expect(visao.veredito.tom).toBe('danger');
      expect(visao.leituras[0].id).toBe('vencidos');
      expect(visao.leituras.find((l) => l.id === 'graves-antigos')!.acao!.alvo).toEqual({ dimension: 'faixa', value: '31-60' });
      expect(visao.leituras.find((l) => l.id === 'item')!.acao!.alvo).toEqual({ dimension: 'item', value: 'p1' });
    });

    it('traduz termos técnicos das prioridades do backend', () => {
      const prioridade = visaoDesvios(desvios()).leituras.find((l) => l.id.startsWith('prioridade'))!;
      expect(prioridade.titulo).toBe('Prazo crítico');
      expect(prioridade.texto).toContain('Pendências em aberto');
      expect(prioridade.texto).not.toContain('SLA');
    });
  });

  describe('eventos', () => {
    it('lesão com CAT pendente pede ação imediata, com leituras acionáveis', () => {
      const visao = visaoEventos(eventosData());
      expect(visao.veredito.tom).toBe('danger');
      expect(visao.leituras.find((l) => l.id === 'cat')!.valor).toBe('1');
      expect(visao.leituras.find((l) => l.id === 'base')!.acao!.alvo).toEqual({ dimension: 'ranking', value: 'base~b1' });
      expect(visao.leituras.find((l) => l.id === 'abertos')!.texto).toContain('1 há mais de 15 dias');
    });

    it('sem eventos e sem pendências, o veredito é positivo', () => {
      const visao = visaoEventos(eventosData({ resumo: { ...eventosData().resumo, total: 0 }, backlog: [] }));
      expect(visao.veredito.tom).toBe('success');
    });

    it('sem eventos mas com tratativas antigas, avisa', () => {
      const visao = visaoEventos(eventosData({ resumo: { ...eventosData().resumo, total: 0 }, backlog: [{ faixa: 'Mais de 30 dias', idadeMinimaDias: 31, idadeMaximaDias: null, total: 2, porStatus: {} }] }));
      expect(visao.veredito.tom).toBe('warning');
    });
  });

  it('remove termos técnicos das definições exibidas ao usuário', () => {
    const texto = toPlainDashboardLanguage('Taxa de resolução da coorte, backlog e exposição normalizada');
    expect(texto).toBe('Percentual de desvios resolvidos do período selecionado, pendências em aberto e comparação entre áreas');
    expect(texto).not.toMatch(/backlog|coorte|exposição normalizada/i);
  });
});
