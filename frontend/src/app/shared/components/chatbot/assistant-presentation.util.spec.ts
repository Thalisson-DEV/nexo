import {
  AssistenteMensagem,
  AssistenteVisualizacao
} from '../../../core/models/assistente.models';
import {
  buildAssistantPresentation,
  formatVisualizationValue
} from './assistant-presentation.util';

describe('apresentação estruturada do assistente', () => {
  it('preserva os valores recebidos no bloco de barras quando existe evidência', () => {
    const visualization = barVisualization();
    const blocks = buildAssistantPresentation(messageWith(visualization));
    const visualBlock = blocks.find(block => block.kind === 'visualization');

    expect(visualBlock).toBeTruthy();
    if (visualBlock?.kind !== 'visualization') return;
    expect(visualBlock.visualization.tipo).toBe('BARRAS');
    expect(visualBlock.visualization.series[0].valores).toEqual([12, 8]);
    expect(visualBlock.visualization.alternativaTabular.linhas).toEqual([
      ['Alfa', '12'],
      ['Beta', '8']
    ]);
  });

  it('não renderiza visualização sem evidência ou em resposta indisponível', () => {
    const withoutEvidence = messageWith(barVisualization());
    withoutEvidence.evidencias = [];
    const unavailable = messageWith(barVisualization());
    unavailable.status = 'INDISPONIVEL';

    expect(buildAssistantPresentation(withoutEvidence).map(block => block.kind)).toEqual(['text']);
    expect(buildAssistantPresentation(unavailable).map(block => block.kind)).toEqual(['text']);
  });

  it('usa conteudo como fallback para versão desconhecida e bloco inválido', () => {
    const message = messageWith(barVisualization());
    if (!message.apresentacao) throw new Error('Apresentação esperada');
    message.apresentacao.versao = '2.0';

    expect(buildAssistantPresentation(message)).toEqual([{
      kind: 'text',
      id: 'conteudo-fallback',
      content: message.conteudo,
      format: 'MARKDOWN_CES_V1'
    }]);
  });

  it('formata unidades sem recalcular o valor', () => {
    expect(formatVisualizationValue(42.5, 'PERCENTUAL')).toBe('42,5%');
    expect(formatVisualizationValue(1234.5, 'MOEDA_BRL')).toContain('1.234,50');
    expect(formatVisualizationValue(12, 'CONTAGEM')).toBe('12');
  });

  function messageWith(visualization: AssistenteVisualizacao): AssistenteMensagem {
    return {
      id: 'message-id',
      papel: 'ASSISTENTE',
      status: 'CONCLUIDA',
      conteudo: 'A equipe **Alfa** ficou em primeiro lugar.',
      criadaEm: '2026-08-02T12:00:00-03:00',
      evidencias: [{
        fonte: 'sst_desvios',
        versao: '1',
        timezone: 'America/Sao_Paulo',
        amostra: 20,
        truncada: false,
        calculadaEm: '2026-08-02T12:00:00-03:00',
        dados: [{ equipe: 'Alfa', totalDesvios: 12 }, { equipe: 'Beta', totalDesvios: 8 }]
      }],
      apresentacao: {
        versao: '1.0',
        formatoConteudo: 'MARKDOWN_CES_V1',
        blocos: [
          { id: 'texto-principal', tipo: 'TEXTO', conteudo: 'A equipe **Alfa** ficou em primeiro lugar.', visualizacao: null },
          { id: 'ranking', tipo: 'VISUALIZACAO', conteudo: null, visualizacao: visualization }
        ]
      }
    };
  }

  function barVisualization(): AssistenteVisualizacao {
    return {
      id: 'ranking-equipes',
      tipo: 'BARRAS',
      titulo: 'Desvios por equipe',
      descricao: 'Comparação do período selecionado.',
      rotuloCategoria: 'Equipe',
      categorias: ['Alfa', 'Beta'],
      series: [{ nome: 'Total de desvios', unidade: 'CONTAGEM', valores: [12, 8] }],
      alternativaTabular: {
        colunas: ['Equipe', 'Total de desvios'],
        linhas: [['Alfa', '12'], ['Beta', '8']]
      }
    };
  }
});
