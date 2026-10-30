import { CapacidadesAssistente } from '../../../core/models/assistente.models';
import {
  CopilotoDashboardContext,
  filtroPainel,
  graficoPainel,
  metricaPainel,
  paraContextoAssistente,
  periodoPainel,
  resumoRecorte,
  rotuloDasOpcoes,
  sugestoesDisponiveis,
} from './copiloto-contexto';

const capacidades = (tipos: string[], disponivel = true): CapacidadesAssistente => ({
  disponivel,
  python: false,
  pesquisaWeb: false,
  leituraWeb: false,
  ferramentas: tipos.map((tipo) => ({ nome: tipo.toLowerCase(), titulo: tipo, tipo })),
  competencias: [],
  sugestoes: [],
  limites: { arquivoMaxBytes: 1, anexosPorMensagem: 1, formatosAceitos: [] },
});

function contexto(base: string | null): CopilotoDashboardContext {
  return {
    id: 'almoxarifado.solicitacoes',
    nome: 'Solicitações de material',
    rota: '/almoxarifado/dashboard/solicitacoes',
    periodo: periodoPainel(new Date(2026, 8, 1), new Date(2026, 8, 30)),
    filtros: [filtroPainel('base', 'Base operacional', base, base ? `id-${base}` : null), filtroPainel('processo', 'Processo', null)],
    metricas: [metricaPainel('total', 'Solicitações', 4281, 'inteiro', { comparacao: { rotulo: 'vs. período anterior', variacaoPercentual: 18.2 } })],
    graficos: [graficoPainel('porStatus', 'rosca', 'Por status', ['Pendentes', 'Entregues'], [12, 30])],
    datasets: ['solicitacoes'],
    sugestoes: [{ rotulo: 'Identificar atrasos', pergunta: 'Quais atrasos?', icone: 'pi pi-clock', requer: ['dados'] }],
  };
}

describe('contrato de contexto do dashboard', () => {
  it('formata métricas e período em pt-BR sem perder o valor numérico', () => {
    const metrica = metricaPainel('valor', 'Autorizado', 1234.5, 'moeda');
    expect(metrica.valor).toBe(1234.5);
    expect(metrica.valorFormatado).toContain('1.234,50');
    expect(metricaPainel('x', 'X', Number.NaN).valor).toBeNull();
    expect(periodoPainel(new Date(2026, 8, 1), new Date(2026, 8, 30))).toEqual({
      inicio: '2026-09-01', fim: '2026-09-30', rotulo: '01/09/2026 a 30/09/2026',
    });
    expect(periodoPainel(null, null)).toBeUndefined();
  });

  it('limita os pontos dos gráficos às agregações visíveis e informa o total', () => {
    const rotulos = Array.from({ length: 60 }, (_, i) => `item ${i}`);
    const grafico = graficoPainel('g', 'barra', 'G', rotulos, rotulos.map((_, i) => i));
    expect(grafico.dados).toHaveLength(40);
    expect(grafico.totalCategorias).toBe(60);
    const series = graficoPainel('s', 'barra', 'S', ['a', 'b'], [{ nome: 'Pedido', valores: [1, 2] }, { nome: 'Autorizado', valores: [3, null] }]);
    expect(series.dados).toEqual([
      { rotulo: 'a', valor: 1, serie: 'Pedido' }, { rotulo: 'b', valor: 2, serie: 'Pedido' },
      { rotulo: 'a', valor: 3, serie: 'Autorizado' }, { rotulo: 'b', valor: null, serie: 'Autorizado' },
    ]);
  });

  it('resolve rótulos de seleções múltiplas e mantém o id quando a opção não foi carregada', () => {
    const opcoes = [{ id: '1', nome: 'Norte' }, { id: '2', nome: 'Sul' }];
    expect(rotuloDasOpcoes(opcoes, ['1', '9'], (o) => o.id, (o) => o.nome)).toBe('Norte, 9');
    expect(rotuloDasOpcoes(opcoes, [], (o) => o.id, (o) => o.nome)).toBeNull();
    expect(filtroPainel('base', 'Base', ['Norte', 'Sul'], ['1', '2'])).toEqual({ chave: 'base', rotulo: 'Base', valor: 'Norte, Sul', valorId: '1,2' });
  });

  it('converte para o contrato do backend com painel estruturado e sem as sugestões de UI', () => {
    const enviado = paraContextoAssistente(contexto('Norte'), { tipo: 'grafico', id: 'porStatus', rotulo: 'Por status' });
    expect(enviado.entidadeTipo).toBe('DASHBOARD');
    expect(enviado.entidadeId).toBe('almoxarifado.solicitacoes');
    expect(enviado.filtros).toEqual({ 'Base operacional': 'Norte', 'Período': '01/09/2026 a 30/09/2026' });
    expect(enviado.painel?.foco?.id).toBe('porStatus');
    expect(enviado.painel?.metricas?.[0].comparacao?.variacaoPercentual).toBe(18.2);
    expect(enviado.painel as unknown as Record<string, unknown>).not.toHaveProperty('sugestoes');
    expect(enviado.painel as unknown as Record<string, unknown>).not.toHaveProperty('rota');
  });

  it('resume o recorte ativo para o chip das mensagens', () => {
    expect(resumoRecorte(contexto('Norte'))).toBe('01/09/2026 a 30/09/2026 · Base operacional: Norte');
    expect(resumoRecorte(contexto(null))).toBe('01/09/2026 a 30/09/2026 · sem filtros');
  });

  it('oferece só ações rápidas suportadas pelas capacidades reais do agente', () => {
    expect(sugestoesDisponiveis(contexto(null), capacidades(['DATA'], false))).toEqual([]);
    const soDados = sugestoesDisponiveis(contexto(null), capacidades(['DATA']), 10).map((s) => s.rotulo);
    expect(soDados).toContain('Identificar atrasos');
    expect(soDados).toContain('Explicar este painel');
    expect(soDados).not.toContain('Gerar relatório');
    expect(soDados).not.toContain('Visualizar comparação');
    const completo = sugestoesDisponiveis(contexto(null), capacidades(['DATA', 'ANALYSIS', 'ARTIFACT', 'REPORT']), 10).map((s) => s.rotulo);
    expect(completo).toEqual(expect.arrayContaining(['Gerar relatório', 'Visualizar comparação', 'Comparar com o período anterior']));
    expect(completo[0]).toBe('Identificar atrasos');
  });
});
