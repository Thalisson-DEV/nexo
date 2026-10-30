/**
 * Há quanto tempo um material está parado no saldo da equipe: para séries, desde que chegaram
 * ao caminhão; para materiais por quantidade, desde a última movimentação. Regras puras para a
 * tela e os testes.
 */

/** A partir daqui o material conta como parado (aviso). */
export const DIAS_PARADO = 30;
/** A partir daqui o material está parado há tempo demais (alerta). */
export const DIAS_PARADO_CRITICO = 60;

export type TomTempoSaldo = 'neutro' | 'atencao' | 'erro';

/** Dias de calendário inteiros entre a data e agora (fuso do aparelho). Null sem data válida. */
export function diasDesde(iso: string | null | undefined, agora: Date = new Date()): number | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const inicio = Date.UTC(d.getFullYear(), d.getMonth(), d.getDate());
  const hoje = Date.UTC(agora.getFullYear(), agora.getMonth(), agora.getDate());
  return Math.max(0, Math.round((hoje - inicio) / 86_400_000));
}

export function tomTempoSaldo(dias: number | null): TomTempoSaldo {
  if (dias == null || dias < DIAS_PARADO) return 'neutro';
  return dias >= DIAS_PARADO_CRITICO ? 'erro' : 'atencao';
}

/** "hoje", "há 1 dia", "há 12 dias". */
export function haDias(dias: number): string {
  return dias === 0 ? 'hoje' : `há ${dias} ${dias === 1 ? 'dia' : 'dias'}`;
}

/** Série: "Chegou hoje" / "Com você há 12 dias". */
export function rotuloSerieNoSaldo(dias: number | null): string {
  if (dias == null) return 'No caminhão';
  return dias === 0 ? 'Chegou hoje' : `Com você ${haDias(dias)}`;
}

/** Material por quantidade: "Movimentado hoje" / "Parado há 12 dias". */
export function rotuloMaterialParado(dias: number | null): string {
  if (dias == null) return '';
  return dias === 0 ? 'Movimentado hoje' : `Parado ${haDias(dias)}`;
}

/** Mais antiga primeiro (sem data por último); empate pelo número de série. */
export function ordenarMaisAntigas<T extends { numSerie: string; noSaldoDesde?: string | null }>(series: readonly T[]): T[] {
  return [...series].sort((a, b) => {
    const da = a.noSaldoDesde ? Date.parse(a.noSaldoDesde) : Number.POSITIVE_INFINITY;
    const db = b.noSaldoDesde ? Date.parse(b.noSaldoDesde) : Number.POSITIVE_INFINITY;
    return da === db ? a.numSerie.localeCompare(b.numSerie) : da - db;
  });
}

/** A data mais antiga da lista (a série há mais tempo no caminhão). */
export function maisAntiga(datas: readonly (string | null | undefined)[]): string | null {
  let menor: string | null = null;
  for (const d of datas) if (d && (!menor || Date.parse(d) < Date.parse(menor))) menor = d;
  return menor;
}
