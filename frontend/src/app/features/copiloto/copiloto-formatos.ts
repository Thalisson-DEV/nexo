import { CesChartKind, CesChartValueFormat, formatCesChartValue } from '@ces/ui/patterns';
import { FormatoValor, TipoArtefato, TipoGraficoArtefato } from '../../core/models/assistente.models';

export const FORMATO_GRAFICO: Record<FormatoValor, CesChartValueFormat> = {
  numero: 'number',
  inteiro: 'integer',
  moeda: 'currency',
  percentual: 'percent',
};

export const TIPO_GRAFICO: Record<TipoGraficoArtefato, CesChartKind> = {
  barra: 'bar',
  barra_horizontal: 'bar-horizontal',
  barra_empilhada: 'bar-stacked',
  linha: 'line',
  area: 'area',
  pizza: 'pie',
  rosca: 'doughnut',
  dispersao: 'scatter',
};

/** Ícone e rótulo de cada categoria de ferramenta (ToolKind do backend). */
export const TIPO_ETAPA: Record<string, { icone: string; rotulo: string }> = {
  CATALOG: { icone: 'pi pi-book', rotulo: 'Catálogo' },
  DATA: { icone: 'pi pi-database', rotulo: 'Dados do Nexo' },
  SQL: { icone: 'pi pi-code', rotulo: 'SQL analítico' },
  ANALYSIS: { icone: 'pi pi-chart-line', rotulo: 'Análise' },
  PYTHON: { icone: 'pi pi-microchip-ai', rotulo: 'Python' },
  WEB_SEARCH: { icone: 'pi pi-globe', rotulo: 'Pesquisa web' },
  OPEN_URL: { icone: 'pi pi-external-link', rotulo: 'Leitura de página' },
  FILE: { icone: 'pi pi-paperclip', rotulo: 'Arquivo' },
  ARTIFACT: { icone: 'pi pi-objects-column', rotulo: 'Artefato' },
  REPORT: { icone: 'pi pi-file-pdf', rotulo: 'Relatório' },
  EXPORT: { icone: 'pi pi-file-excel', rotulo: 'Exportação' },
  SKILL: { icone: 'pi pi-bolt', rotulo: 'Roteiro' },
  NAVIGATION: { icone: 'pi pi-compass', rotulo: 'Navegação' },
  WRITE: { icone: 'pi pi-pencil', rotulo: 'Alteração' },
};

export const ICONE_ARTEFATO: Record<TipoArtefato, string> = {
  DASHBOARD: 'pi pi-th-large',
  GRAFICO: 'pi pi-chart-bar',
  TABELA: 'pi pi-table',
  RELATORIO: 'pi pi-file-pdf',
  DOCUMENTO: 'pi pi-file',
  PLANILHA: 'pi pi-file-excel',
  IMAGEM: 'pi pi-image',
  CODIGO: 'pi pi-code',
};

export const ROTULO_ARTEFATO: Record<TipoArtefato, string> = {
  DASHBOARD: 'Dashboard',
  GRAFICO: 'Gráfico',
  TABELA: 'Tabela',
  RELATORIO: 'Relatório PDF',
  DOCUMENTO: 'Documento',
  PLANILHA: 'Planilha',
  IMAGEM: 'Imagem',
  CODIGO: 'Código',
};

export const NATUREZA: Record<string, string> = {
  INTERNO: 'Dados do Nexo',
  EXTERNO: 'Fonte externa',
  ARQUIVO: 'Arquivo enviado',
  CALCULO: 'Cálculo',
};

export function formatarValor(valor: number | null | undefined, formato: FormatoValor = 'numero'): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return '—';
  return formatCesChartValue(valor, FORMATO_GRAFICO[formato] ?? 'number');
}

export function formatarDuracao(ms: number | undefined): string {
  if (ms === undefined || ms === null) return '';
  if (ms < 1000) return `${ms} ms`;
  const segundos = ms / 1000;
  if (segundos < 60) return `${segundos.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} s`;
  const minutos = Math.floor(segundos / 60);
  return `${minutos} min ${Math.round(segundos % 60)} s`;
}

/** Célula de tabela de artefato: números pt-BR, datas dd/mm/aaaa, booleanos em português. */
export function formatarCelula(valor: unknown, tipo: string | undefined, coluna = ''): string {
  if (valor === null || valor === undefined || valor === '') return '';
  if (typeof valor === 'boolean') return valor ? 'Sim' : 'Não';
  if (typeof valor === 'number') {
    const nome = coluna.toLowerCase();
    if (/(valor|custo|preco|preço|montante)/.test(nome)) return formatCesChartValue(valor, 'currency');
    return formatCesChartValue(valor, tipo === 'inteiro' ? 'integer' : 'number');
  }
  const texto = String(valor);
  if ((tipo === 'data' || /^\d{4}-\d{2}-\d{2}$/.test(texto)) && /^\d{4}-\d{2}-\d{2}/.test(texto)) {
    const [data, hora] = texto.split('T');
    const [ano, mes, dia] = data.split('-');
    return hora && tipo === 'data_hora' ? `${dia}/${mes}/${ano} ${hora.slice(0, 5)}` : `${dia}/${mes}/${ano}`;
  }
  if (tipo === 'data_hora' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(texto)) {
    const [data, hora] = texto.split('T');
    const [ano, mes, dia] = data.split('-');
    return `${dia}/${mes}/${ano} ${hora.slice(0, 5)}`;
  }
  return texto;
}

export function rotuloColuna(nome: string): string {
  const texto = nome.replace(/_/g, ' ').trim();
  return texto ? texto.charAt(0).toUpperCase() + texto.slice(1) : '';
}
