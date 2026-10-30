/**
 * Metas de faturamento das equipes (RH). A meta vale por janela: o ciclo comercial
 * (dia 26 de um mês ao dia 25 do seguinte, nomeado pelo mês em que termina) para as
 * equipes do processo comercial, ou o mês civil para as demais. A vigência é contada
 * em janelas inteiras.
 */
export type PeriodicidadeMetaFaturamento = 'CICLO_COMERCIAL' | 'MENSAL';

export const DIA_INICIO_CICLO = 26;

export const PERIODICIDADE_FATURAMENTO_LABELS: Record<PeriodicidadeMetaFaturamento, string> = {
  CICLO_COMERCIAL: 'Ciclo comercial (26 a 25)',
  MENSAL: 'Mês civil'
};

export interface MetaFaturamentoResponse {
  id: string;
  idEquipe: string;
  codigoEquipe: string | null;
  processo: string | null;
  baseOperacional: string | null;
  valor: number;
  periodicidade: PeriodicidadeMetaFaturamento;
  vigenciaInicio: string;
  vigenciaFim: string | null;
  descricao: string | null;
  ativo: boolean;
  janelaAtualInicio: string | null;
  janelaAtualFim: string | null;
  diasUteisJanelaAtual: number | null;
  metaDiariaAtual: number | null;
}

export interface MetaFaturamentoRequest {
  idsEquipes: string[];
  valor: number;
  periodicidade: PeriodicidadeMetaFaturamento;
  vigenciaInicio: string;
  vigenciaFim: string | null;
  descricao: string | null;
  /** Encerra a meta sem prazo da equipe no fim da janela anterior (reajuste). */
  substituirVigentes: boolean;
}

export interface MetaFaturamentoUpdateRequest {
  valor: number;
  periodicidade: PeriodicidadeMetaFaturamento;
  vigenciaInicio: string;
  vigenciaFim: string | null;
  descricao: string | null;
}

export interface MetaFaturamentoFilter {
  idEquipe?: string | null;
  idBaseOperacional?: string | null;
  idProcesso?: string | null;
  periodicidade?: PeriodicidadeMetaFaturamento | null;
  vigenteEm?: string | null;
  ativo?: boolean | null;
  buscaGlobal?: string | null;
  page?: number;
  size?: number;
  sort?: string | string[];
}

/** Janela de apuração: datas `AAAA-MM-DD` e competência `AAAA-MM`. */
export interface JanelaFaturamento {
  inicio: string;
  fim: string;
  competencia: string;
}

const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];

function iso(ano: number, mes: number, dia: number): string {
  const d = new Date(ano, mes - 1, dia);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Competência `AAAA-MM` a partir de ano e mês (1 a 12), normalizando estouros. */
export function competencia(ano: number, mes: number): string {
  const d = new Date(ano, mes - 1, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/** Janela de uma competência: o ciclo que termina nela ou o próprio mês civil. */
export function janelaDaCompetencia(periodicidade: PeriodicidadeMetaFaturamento, comp: string): JanelaFaturamento {
  const [ano, mes] = comp.split('-').map(Number);
  if (periodicidade === 'CICLO_COMERCIAL') {
    return { inicio: iso(ano, mes - 1, DIA_INICIO_CICLO), fim: iso(ano, mes, DIA_INICIO_CICLO - 1), competencia: comp };
  }
  return { inicio: iso(ano, mes, 1), fim: iso(ano, mes + 1, 0), competencia: comp };
}

/** Competência da janela que contém a data (`Date` local). */
export function competenciaDe(periodicidade: PeriodicidadeMetaFaturamento, data: Date): string {
  const virou = periodicidade === 'CICLO_COMERCIAL' && data.getDate() >= DIA_INICIO_CICLO;
  return competencia(data.getFullYear(), data.getMonth() + 1 + (virou ? 1 : 0));
}

/** Competência da janela que começa (ou termina) na data `AAAA-MM-DD`. */
export function competenciaDaData(periodicidade: PeriodicidadeMetaFaturamento, data: string): string {
  const [ano, mes, dia] = data.split('-').map(Number);
  return competenciaDe(periodicidade, new Date(ano, mes - 1, dia));
}

export function nomeCompetencia(periodicidade: PeriodicidadeMetaFaturamento, comp: string): string {
  const [ano, mes] = comp.split('-').map(Number);
  const nome = `${MESES[mes - 1]}/${ano}`;
  return periodicidade === 'CICLO_COMERCIAL' ? `Ciclo de ${nome}` : nome.charAt(0).toUpperCase() + nome.slice(1);
}

export function formatarDataCurta(data: string | null | undefined): string {
  if (!data) return '';
  const [ano, mes, dia] = data.slice(0, 10).split('-');
  return `${dia}/${mes}/${ano}`;
}

/** Feriados nacionais de data fixa, em `MM-DD` (mesma lista do backend). */
const FERIADOS_FIXOS = new Set(['01-01', '04-21', '05-01', '09-07', '10-12', '11-02', '11-15', '11-20', '12-25']);

/** Domingo de Páscoa (algoritmo de Meeus/Jones/Butcher). */
function pascoa(ano: number): Date {
  const a = ano % 19, b = Math.floor(ano / 100), c = ano % 100;
  const d = Math.floor(b / 4), e = b % 4, f = Math.floor((b + 8) / 25), g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4), k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const n = h + l - 7 * m + 114;
  return new Date(ano, Math.floor(n / 31) - 1, (n % 31) + 1);
}

/** Feriado nacional: datas fixas e Sexta-feira Santa (Carnaval e Corpus Christi são pontos facultativos). */
export function feriadoNacional(d: Date): boolean {
  const mmdd = `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  if (FERIADOS_FIXOS.has(mmdd)) return true;
  const p = pascoa(d.getFullYear());
  const sextaSanta = new Date(p.getFullYear(), p.getMonth(), p.getDate() - 2);
  return d.getMonth() === sextaSanta.getMonth() && d.getDate() === sextaSanta.getDate();
}

/** Dias úteis (segunda a sexta, menos feriados nacionais) da janela — mesma regra do backend. */
export function diasUteisDaJanela(janela: JanelaFaturamento): number {
  const [ai, mi, di] = janela.inicio.split('-').map(Number);
  const [af, mf, df] = janela.fim.split('-').map(Number);
  const fim = new Date(af, mf - 1, df);
  let total = 0;
  for (let d = new Date(ai, mi - 1, di); d <= fim; d = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1)) {
    if (d.getDay() !== 0 && d.getDay() !== 6 && !feriadoNacional(d)) total++;
  }
  return total;
}

/** Processos cujas equipes apuram a meta pelo ciclo comercial. */
export function processoUsaCiclo(nomeProcesso: string | null | undefined): boolean {
  return !!nomeProcesso && nomeProcesso.toUpperCase().includes('COMERCIAL');
}
