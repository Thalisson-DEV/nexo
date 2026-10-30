import { DesvioFilter } from '../../../../core/models/desvio.models';
import { DashboardSstFilter, InspecaoFilters } from '../../../../core/models/sst.models';
import { GranularidadeDashboard } from '../../../../core/models/sst-dashboard.models';
import { temSelecao } from '../../../../core/utils/http-params.util';
import { toSaoPauloIso } from '../../../../shared/utils/dashboard-date.utils';

/** Dimensões do painel que restringem também as listagens de inspeções. */
const DIMENSOES_INSPECAO = [
  'idTipoInspecao', 'idProcesso', 'idEquipe', 'idBaseOperacional', 'idCentroCusto', 'idFormulario', 'idInspetor'
] as const;

/** A listagem de desvios não filtra por tipo de inspeção. */
const DIMENSOES_DESVIO = [
  'idProcesso', 'idEquipe', 'idBaseOperacional', 'idCentroCusto', 'idFormulario', 'idInspetor'
] as const;

export interface IntervaloDrill {
  /** `AAAA-MM-DD` do primeiro e do último dia (inclusive). */
  readonly de: string;
  readonly ate: string;
  /** ISO com o offset de São Paulo, prontos para a API. */
  readonly inicio: string;
  readonly fim: string;
}

function dataLocal(iso: string): Date | null {
  const partes = iso.match(/^(\d{4})-(\d{2})(?:-(\d{2}))?$/);
  if (!partes) return null;
  return new Date(Number(partes[1]), Number(partes[2]) - 1, Number(partes[3] ?? '1'));
}

function aaaaMmDd(data: Date): string {
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}

/**
 * Intervalo de um ponto da série. `inicio` é o primeiro dia do intervalo (`AAAA-MM-DD`); o fim
 * depende da granularidade: o próprio dia, o último dia do mês ou 31/12.
 */
export function intervaloDoPonto(inicio: string, granularidade: GranularidadeDashboard): IntervaloDrill | null {
  const primeiro = dataLocal(inicio);
  if (!primeiro) return null;
  const ultimo =
    granularidade === 'DIA'
      ? new Date(primeiro)
      : granularidade === 'MES'
        ? new Date(primeiro.getFullYear(), primeiro.getMonth() + 1, 0)
        : new Date(primeiro.getFullYear(), 11, 31);
  return {
    de: aaaaMmDd(primeiro),
    ate: aaaaMmDd(ultimo),
    inicio: toSaoPauloIso(primeiro, false),
    fim: toSaoPauloIso(ultimo, true),
  };
}

/** Granularidade de um nível abaixo (ano → mês → dia); `null` quando já é o dia. */
export function granularidadeAbaixo(granularidade: GranularidadeDashboard): GranularidadeDashboard | null {
  return granularidade === 'ANO' ? 'MES' : granularidade === 'MES' ? 'DIA' : null;
}

/** `2026-09-14` → `14/09/2026`. */
export function rotuloDia(iso: string): string {
  const data = dataLocal(iso);
  return data ? data.toLocaleDateString('pt-BR') : iso;
}

function copiarDimensoes<T extends object>(
  filtros: DashboardSstFilter,
  dimensoes: readonly (keyof DashboardSstFilter & keyof T)[],
  destino: T,
): T {
  for (const chave of dimensoes) {
    const selecao = filtros[chave] as string[] | null | undefined;
    if (temSelecao(selecao)) (destino as Record<string, unknown>)[chave] = [...selecao!];
  }
  return destino;
}

/** Recorte do painel + intervalo → filtros de `GET /sst/inspecoes`. */
export function filtrosListagemInspecoes(filtros: DashboardSstFilter, intervalo: IntervaloDrill): InspecaoFilters {
  return copiarDimensoes(filtros, DIMENSOES_INSPECAO, {
    dataRealizacaoDe: intervalo.inicio,
    dataRealizacaoAte: intervalo.fim,
  } as InspecaoFilters);
}

/** Recorte do painel + intervalo → filtros de `GET /sst/desvios` (por data da inspeção). */
export function filtrosListagemDesvios(filtros: DashboardSstFilter, intervalo: IntervaloDrill): DesvioFilter {
  return copiarDimensoes(filtros, DIMENSOES_DESVIO, {
    dataInspecaoDe: intervalo.inicio,
    dataInspecaoAte: intervalo.fim,
  } as DesvioFilter);
}

/** `true` quando algum filtro do painel não pode ser aplicado à listagem de desvios. */
export function desviosIgnoramAlgumFiltro(filtros: DashboardSstFilter): boolean {
  return temSelecao(filtros.idTipoInspecao as string[] | null);
}

/** Query params da listagem completa ("Ver todas"), já filtrada pelo recorte. */
export function queryParamsListagem(
  filtros: DashboardSstFilter,
  intervalo: IntervaloDrill,
  extra: Record<string, string> = {},
): Record<string, string> {
  // Formato do estado da listagem: período `de~ate` e seleções separadas por vírgula.
  const params: Record<string, string> = { ...extra };
  if (intervalo.de || intervalo.ate) params['realizacao'] = `${intervalo.de ?? ''}~${intervalo.ate ?? ''}`;
  for (const chave of DIMENSOES_INSPECAO) {
    const selecao = filtros[chave] as string[] | null | undefined;
    if (temSelecao(selecao)) params[chave] = selecao!.join(',');
  }
  return params;
}
