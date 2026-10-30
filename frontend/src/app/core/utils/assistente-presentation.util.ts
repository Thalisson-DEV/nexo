import { AssistenteEvidencia } from '../models/assistente.models';

const SOURCE_LABELS: Record<string, string> = {
  sst_inspecoes: 'Inspeções de segurança',
  sst_desvios: 'Desvios de segurança',
  descontos: 'Descontos em folha',
  desconto: 'Descontos em folha'
};

export function getEvidencePeriod(evidence: AssistenteEvidencia): string {
  const filters = evidence.consultaEfetiva?.['filtros'];
  if (!Array.isArray(filters)) return 'Período conforme a consulta';
  const dateFilter = filters.find(filter => {
    if (!isRecord(filter)) return false;
    const field = String(filter['campo'] ?? '').toLocaleLowerCase('pt-BR');
    return field.includes('data') || field.includes('periodo');
  });
  if (!isRecord(dateFilter) || !Array.isArray(dateFilter['valores'])) {
    return 'Período conforme a consulta';
  }
  return (dateFilter['valores'] as unknown[]).map(value => formatAssistenteValue(value)).join(' a ');
}

export function getEvidenceQuerySummary(evidence: AssistenteEvidencia): string[] {
  const query = evidence.consultaEfetiva;
  if (!query) return [];
  const summary: string[] = [];
  addQueryList(summary, 'Dimensões', query['dimensoes']);
  addQueryList(summary, 'Medidas', query['medidas']);
  addQueryList(summary, 'Agrupamento', query['agruparPor']);
  if (typeof query['limite'] === 'number') summary.push(`Limite: ${query['limite']}`);
  return summary;
}

export function getEvidenceRows(evidence: AssistenteEvidencia): Record<string, unknown>[] {
  const data = evidence.dados;
  if (Array.isArray(data)) {
    return data.filter(item => isRecord(item)).slice(0, 5) as Record<string, unknown>[];
  }
  return isRecord(data) ? [data] : [];
}

export function getRowEntries(row: Record<string, unknown>): [string, unknown][] {
  return Object.entries(row).slice(0, 8);
}

export function formatAssistenteFieldName(field: string): string {
  return field
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/^./, value => value.toLocaleUpperCase('pt-BR'));
}

export function formatAssistenteSource(source: string): string {
  return SOURCE_LABELS[source.toLocaleLowerCase('pt-BR')] ?? formatAssistenteFieldName(source);
}

export function formatAssistenteValue(value: unknown, field = ''): string {
  if (value === null || value === undefined || value === '') return 'Não informado';
  if (typeof value === 'boolean') return value ? 'Sim' : 'Não';
  if (typeof value === 'number') {
    const isPercentage = /taxa|percentual|porcentagem/i.test(field);
    const isCurrency = /valor|preco|preço|custo|total monetario|total monetário/i.test(field);
    return new Intl.NumberFormat('pt-BR', {
      style: isPercentage ? 'percent' : isCurrency ? 'currency' : 'decimal',
      currency: isCurrency ? 'BRL' : undefined,
      maximumFractionDigits: 2
    }).format(isPercentage && value > 1 ? value / 100 : value);
  }
  if (Array.isArray(value)) return value.map(item => formatAssistenteValue(item, field)).join(', ');
  if (isRecord(value)) return 'Detalhamento estruturado';
  const text = String(value);
  const date = /^\d{4}-\d{2}-\d{2}(T.*)?$/.test(text) ? new Date(text) : null;
  if (date && !Number.isNaN(date.getTime())) {
    return new Intl.DateTimeFormat(
      'pt-BR',
      text.includes('T')
        ? { dateStyle: 'short', timeStyle: 'short' }
        : { dateStyle: 'short', timeZone: 'UTC' }
    ).format(date);
  }
  return text;
}

function addQueryList(summary: string[], label: string, value: unknown): void {
  if (Array.isArray(value) && value.length) summary.push(`${label}: ${value.join(', ')}`);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
