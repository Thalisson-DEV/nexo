import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { DesvioDetalhadoResponse } from '../../../../core/models/desvio.models';
import { DashboardDesviosResponse, DashboardInspecoesResponse } from '../../../../core/models/sst.models';
import { DashboardEventosSstResponse } from '../../../../core/models/sst-evento.models';
import { SstService } from '../../../../core/services/sst.service';
import { vistaDesvios } from './analise-desvios';
import { vistaEventos } from './analise-eventos';
import { vistaInspecoes } from './analise-inspecoes';
import { graficosDosDesvios, linhaDesvio } from './analise-sst-comum';

const pagina = { content: [], page: { totalElements: 0, totalPages: 0, number: 0, size: 200 } };
const sst = {
  listarInspecoes: vi.fn(() => of(pagina)),
  listarDesvios: vi.fn(() => of(pagina)),
  listarEventos: vi.fn(() => of(pagina))
} as unknown as SstService;

describe('Análises dos dashboards de SST', () => {
  describe('inspeções', () => {
    const d = {
      inicio: '2026-10-01', fim: '2026-10-03', granularidade: 'DIA',
      resumo: { totalInspecoes: 5, totalPeriodoAnterior: 0, variacaoPercentual: 0, inspecoesComDesvio: 1, inspecoesSemDesvio: 4, percentualSemDesvio: 80, totalDesvios: 2, mediaDesviosPorInspecao: 0.4, colaboradoresInspecionados: 3, mediaColaboradoresPorInspecao: 1, inspetoresAtivos: 1, mediaInspecoesPorInspetor: 5, mediaInspecoesPorDia: 1.7 },
      meta: { possuiMeta: true, metaPeriodo: 6, realizadoPeriodo: 5, percentualAtingido: 83, saldoParaMeta: 1, colaboradoresComMeta: 1, inspetoresNaMeta: 0, percentualInspetoresNaMeta: 0, colaboradoresSemAcesso: 0 },
      colaboradores: { colaboradoresInspecionados: 3, colaboradoresAtivos: 4, percentualCobertura: 75, colaboradoresSemInspecao: 1, mediaInspecoesPorColaborador: 1, colaboradoresComDesvio: 1, percentualComDesvio: 33, colaboradoresReincidentes: 0 },
      serieTemporal: [
        { chave: '2026-10-01', rotulo: '01/10', inicio: '2026-10-01', realizado: 2, meta: 2, percentualAtingido: 100, desvios: 1 },
        { chave: '2026-10-02', rotulo: '02/10', inicio: '2026-10-02', realizado: 3, meta: 2, percentualAtingido: 150, desvios: 1 }
      ],
      porInspetor: [{ idInspetor: 'i1', nomeInspetor: 'Ana', realizado: 5, meta: 6, percentualAtingido: 83, atingiuMeta: false, desvios: 2, percentualDoTotal: 100 }],
      porEquipe: [], porColaborador: [], porTipoInspecao: [], porProcesso: [{ id: 'p1', nome: 'Corte', total: 5, percentual: 100 }],
      porProcessoInspetor: [], porBaseOperacional: [], porFormulario: []
    } as unknown as DashboardInspecoesResponse;
    const ctx = { d, filtros: { idEquipe: ['e1'] }, sst, metaComparavel: true };

    it('ponto da série abre o dia com inspeções e desvios do intervalo, mantendo os filtros do painel', () => {
      const dia = vistaInspecoes({ dimension: 'dia', value: '2026-10-02' }, ctx)!;
      expect(dia.titulo).toBe('02/10');
      expect(dia.anterior!.alvo.value).toBe('2026-10-01');
      expect(dia.origem!.conferencia!.quantidadeEsperada).toBe(3);
      expect(dia.origem!.chave).toContain('"idEquipe":["e1"]');
      expect(dia.origem!.chave).toContain('2026-10-02T00:00:00');
      expect(dia.origensExtras![0].id).toBe('desvios');
    });

    it('o gráfico da série aprofunda para o período e o grupo da distribuição filtra a listagem', () => {
      const volume = vistaInspecoes({ dimension: 'kpi', value: 'inspecoes' }, ctx)!;
      expect(volume.graficos[0].aoSelecionar!(0)).toEqual({ tipo: 'navegar', alvo: { dimension: 'dia', value: '2026-10-01' } });
      const grupo = vistaInspecoes({ dimension: 'grupo', value: 'processo~p1' }, ctx)!;
      expect(grupo.origem!.chave).toContain('"idProcesso":["p1"]');
    });

    it('inspetor fora do recorte não abre análise', () => {
      expect(vistaInspecoes({ dimension: 'inspetor', value: 'x' }, ctx)).toBeNull();
      expect(vistaInspecoes({ dimension: 'grupo', value: 'inexistente~1' }, ctx)).toBeNull();
    });
  });

  describe('desvios', () => {
    const d = {
      inicio: '2026-10-01', fim: '2026-10-31', granularidade: 'MES',
      totalDesvios: 4, totalCriticos: 1, percentualCriticos: 25, taxaResolucao: 50, totalResolvidos: 2, totalAbertos: 1, totalEmTratamento: 1,
      totalPendentes: 2, totalVencidos: 1, totalNoPrazo: 1, totalSemPrazo: 0, totalInspecoesComDesvio: 3, mediaDesviosPorInspecao: 1.3,
      totalColaboradoresEnvolvidos: 2, totalColaboradoresReincidentes: 0, tempoMedianoResolucaoDias: 3, tempoP90ResolucaoDias: 6,
      indicadores: { totalDesvios: { valor: 4, anterior: 2, variacaoPercentual: 100 }, backlog: { valor: 5, anterior: 3 }, vencidos: { valor: 2, anterior: 1 } },
      tendenciaDesvios: [{ chave: '2026-10', rotulo: 'out/2026', inicio: '2026-10-01', totalDesvios: 4 }],
      volumePorGravidade: [{ gravidade: 'CRITICA', total: 1, percentual: 25, anterior: 0, variacaoPercentual: 0 }],
      backlogPorIdade: [
        { faixa: '0-7', idadeMinimaDias: 0, idadeMaximaDias: 7, total: 3, porGravidade: { CRITICA: 0, ALTA: 1, MEDIA: 2, BAIXA: 0 } },
        { faixa: '>60', idadeMinimaDias: 61, idadeMaximaDias: null, total: 2, porGravidade: { CRITICA: 1, ALTA: 0, MEDIA: 1, BAIXA: 0 } }
      ],
      itensCriticosRecorrentes: [], pendenciasPorEquipe: [{ id: 'e1', nome: 'EQ-01', totalPendentes: 2, totalAbertos: 1, totalEmTratamento: 1, totalVencidos: 1, totalCriticos: 0 }],
      pendenciasPorProcesso: [], pendenciasPorBaseOperacional: [], exposicaoPorProcesso: [], exposicaoPorBaseOperacional: [], exposicaoPorEquipe: [], exposicaoPorProcessoInspetor: []
    } as unknown as DashboardDesviosResponse;
    const ctx = { d, filtros: { gravidade: ['ALTA', 'CRITICA'] }, sst } as never;

    it('a faixa de idade vira um intervalo de datas de inspeção até o fim do período', () => {
      const faixa = vistaDesvios({ dimension: 'faixa', value: '>60' }, ctx)!;
      // 31/10 − 61 dias = 31/08; sem idade máxima não há limite inferior.
      expect(faixa.origem!.chave).toContain('"dataInspecaoAte":"2026-08-31T23:59:59');
      expect(faixa.origem!.chave).not.toContain('dataInspecaoDe');
      expect(faixa.origem!.chave).toContain('"apenasPendentes":true');
      expect(faixa.origem!.chave).toContain('"gravidade":["ALTA","CRITICA"]');
      expect(faixa.anterior!.alvo.value).toBe('0-7');
    });

    it('grupo traz desvios do período e, em outra aba, as pendências até o fim do período', () => {
      const grupo = vistaDesvios({ dimension: 'grupo', value: 'equipe~e1' }, ctx)!;
      expect(grupo.origem!.chave).toContain('"idEquipe":["e1"]');
      expect(grupo.origensExtras![0].id).toBe('pendentes');
      expect(grupo.origensExtras![0].chave).toContain('"apenasPendentes":true');
    });

    it('cartão de críticos reutiliza a análise da gravidade', () => {
      const criticos = vistaDesvios({ dimension: 'kpi', value: 'criticos' }, ctx)!;
      expect(criticos.chave).toBe('kpi:criticos');
      expect(criticos.origem!.chave).toContain('"gravidade":["CRITICA"]');
    });

    it('gráficos dos desvios carregados filtram a origem por gravidade e vencidos', () => {
      const desvio = (g: string, vencido: boolean) => linhaDesvio({ id: g + vencido, idInspecao: 'i', dataInspecao: '2026-10-01T10:00:00-03:00', gravidade: g, status: 'ABERTO', vencido, colaboradores: [], anexos: [] } as unknown as DesvioDetalhadoResponse);
      const graficos = graficosDosDesvios([desvio('ALTA', true), desvio('BAIXA', false)]);
      const tratamento = graficos.find((g) => g.id === 'desvios-tratamento')!;
      const acao = tratamento.aoSelecionar!(tratamento.rotulos.length - 1);
      expect(acao!.tipo).toBe('filtrar');
    });
  });

  describe('eventos', () => {
    const d = {
      inicio: '2026-10-01', fim: '2026-10-31', granularidade: 'MES',
      resumo: { total: 3, totalAnterior: 0, variacaoPercentual: 0, diasSemEventos: 4, porTipo: { ACIDENTE_TIPICO: 1, INCIDENTE_SEM_LESAO: 2 }, comLesao: 1, comLesaoAnterior: 0, variacaoComLesao: 0, semLesao: 2, diasPerdidos: 5, comCat: 0, coberturaCat: 0, mediaHorasComunicacao: 30, comunicacoesAtrasadas: 1, percentualAtrasadas: 33, abertos: 1, concluidos: 2, desviosNoPeriodo: 4, razaoDesviosPorLesao: 4 },
      serie: [{ data: '2026-10-01', rotulo: 'out/2026', total: 3, comLesao: 1, diasPerdidos: 5 }],
      piramide: [], diasSemAcidente: [{ idBaseOperacional: 'b1', baseNome: 'Base Central', ultimaOcorrencia: '2026-10-08', diasSemAcidente: 4, comLesaoNoPeriodo: 1 }],
      backlog: [{ faixa: '0-7 dias', idadeMinimaDias: 0, idadeMaximaDias: 7, total: 1, porStatus: { REGISTRADO: 1 } }],
      porClassificacao: [{ id: 'c1', nome: 'Queda', total: 2, percentual: 66 }], porAtividade: [], porGestor: [], porBaseOperacional: [], porEquipe: [], porProcesso: [], porLocalLesao: []
    } as unknown as DashboardEventosSstResponse;
    const ctx = { d, filtros: { tipo: ['ACIDENTE_TIPICO'] }, sst } as never;

    it('cartão de lesão lista só eventos com lesão do período, sem cancelados', () => {
      const lesao = vistaEventos({ dimension: 'kpi', value: 'lesao' }, ctx)!;
      expect(lesao.origem!.chave).toContain('"comLesao":true');
      expect(lesao.origem!.chave).toContain('"status":["REGISTRADO","EM_TRATATIVA","CONCLUIDO"]');
      expect(lesao.origem!.chave).toContain('"tipo":["ACIDENTE_TIPICO"]');
    });

    it('base da tabela de dias sem acidente abre a análise da base mesmo fora do ranking', () => {
      const base = vistaEventos({ dimension: 'ranking', value: 'base~b1' }, ctx)!;
      expect(base.titulo).toBe('Base Central');
      expect(base.destaques![0].texto).toContain('4 dias sem evento com lesão');
    });

    it('eventos em aberto ignoram o período e o ponto da série aprofunda para o mês', () => {
      const abertos = vistaEventos({ dimension: 'kpi', value: 'abertos' }, ctx)!;
      expect(abertos.origem!.chave).not.toContain('dataOcorrenciaDe');
      const total = vistaEventos({ dimension: 'kpi', value: 'eventos' }, ctx)!;
      expect(total.graficos[0].aoSelecionar!(0)).toEqual({ tipo: 'navegar', alvo: { dimension: 'mes', value: '2026-10-01' } });
    });
  });
});
