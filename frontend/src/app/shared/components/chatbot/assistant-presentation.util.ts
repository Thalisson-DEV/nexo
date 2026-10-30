import {
  AssistenteFormatoConteudo,
  AssistenteMensagem,
  AssistenteTipoVisualizacao,
  AssistenteUnidadeSerie,
  AssistenteVisualizacao
} from '../../../core/models/assistente.models';

export type AssistantRenderableBlock =
  | {
      kind: 'text';
      id: string;
      content: string;
      format: AssistenteFormatoConteudo;
    }
  | {
      kind: 'visualization';
      id: string;
      visualization: AssistenteVisualizacao;
    };

const VISUALIZATION_TYPES = new Set<AssistenteTipoVisualizacao>([
  'INDICADOR',
  'BARRAS',
  'LINHA',
  'ROSCA'
]);
const SERIES_UNITS = new Set<AssistenteUnidadeSerie>([
  'NUMERO',
  'CONTAGEM',
  'PERCENTUAL',
  'MOEDA_BRL'
]);

export function buildAssistantPresentation(message: AssistenteMensagem): AssistantRenderableBlock[] {
  const fallback = (): AssistantRenderableBlock[] => [{
    kind: 'text',
    id: 'conteudo-fallback',
    content: fallbackAssistantContent(message),
    format: 'MARKDOWN_CES_V1'
  }];
  const presentation = message.apresentacao;
  if (presentation?.versao !== '1.0' || !Array.isArray(presentation.blocos)) return fallback();
  if (presentation.formatoConteudo !== 'MARKDOWN_CES_V1' &&
      presentation.formatoConteudo !== 'TEXTO_SIMPLES') return fallback();

  const result: AssistantRenderableBlock[] = [];
  let visualizationCount = 0;
  for (const block of presentation.blocos) {
    if (block?.tipo === 'TEXTO' && typeof block.conteudo === 'string') {
      result.push({
        kind: 'text',
        id: block.id || `texto-${result.length}`,
        content: block.conteudo,
        format: presentation.formatoConteudo
      });
      continue;
    }
    if (block?.tipo !== 'VISUALIZACAO' || visualizationCount >= 3) continue;
    if (!canRenderVisualization(message) || !isValidVisualization(block.visualizacao)) continue;
    result.push({
      kind: 'visualization',
      id: block.id || block.visualizacao.id,
      visualization: block.visualizacao
    });
    visualizationCount += 1;
  }
  return result.length ? result : fallback();
}

export function fallbackAssistantContent(message: AssistenteMensagem): string {
  if (message.conteudo?.trim()) return message.conteudo;
  if (message.status === 'INDISPONIVEL') {
    return 'Não consegui buscar essa resposta agora. Sua pergunta continua aqui para você tentar novamente.';
  }
  if (message.status === 'DADOS_INSUFICIENTES') {
    return 'Não encontrei informações suficientes para chegar a uma resposta. Se quiser, tente incluir um período ou perguntar de outro jeito.';
  }
  return '';
}

export function formatVisualizationValue(value: number, unit: AssistenteUnidadeSerie): string {
  if (!Number.isFinite(value)) return '—';
  if (unit === 'MOEDA_BRL') {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
  }
  if (unit === 'PERCENTUAL') {
    return `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(value)}%`;
  }
  return new Intl.NumberFormat('pt-BR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: unit === 'CONTAGEM' ? 0 : 2
  }).format(value);
}

function canRenderVisualization(message: AssistenteMensagem): boolean {
  return message.status !== 'INDISPONIVEL' && message.status !== 'DADOS_INSUFICIENTES' &&
    (message.evidencias?.length ?? 0) > 0;
}

function isValidVisualization(
  visualization: AssistenteVisualizacao | null
): visualization is AssistenteVisualizacao {
  if (!visualization || !VISUALIZATION_TYPES.has(visualization.tipo as AssistenteTipoVisualizacao)) {
    return false;
  }
  if (typeof visualization.titulo !== 'string' || typeof visualization.descricao !== 'string' ||
      typeof visualization.rotuloCategoria !== 'string') return false;
  if (!Array.isArray(visualization.categorias) || visualization.categorias.length === 0 ||
      visualization.categorias.length > 20 ||
      !visualization.categorias.every(category => typeof category === 'string')) return false;
  if (!Array.isArray(visualization.series) || visualization.series.length === 0 ||
      visualization.series.length > 3) return false;
  const validSeries = visualization.series.every(series =>
    typeof series.nome === 'string' && SERIES_UNITS.has(series.unidade) &&
    Array.isArray(series.valores) && series.valores.length === visualization.categorias.length &&
    series.valores.every(value => typeof value === 'number' && Number.isFinite(value))
  );
  if (!validSeries) return false;
  const table = visualization.alternativaTabular;
  return !!table && Array.isArray(table.colunas) && table.colunas.length > 0 &&
    table.colunas.every(column => typeof column === 'string') && Array.isArray(table.linhas) &&
    table.linhas.every(row => Array.isArray(row) && row.length === table.colunas.length &&
      row.every(cell => typeof cell === 'string'));
}
