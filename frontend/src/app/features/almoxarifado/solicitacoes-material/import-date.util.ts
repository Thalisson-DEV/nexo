import { parseLocalDate } from '../../../core/utils/local-date.util';

const dateCache = new Map<string, Date | null>();

/** Aceita as duas formas de data presentes nas prévias de CSV sem deslocamento de fuso. */
export function parseImportDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const normalized = value.trim();
  if (dateCache.has(normalized)) return dateCache.get(normalized) ?? null;
  const br = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(normalized);
  const date = parseLocalDate(br ? `${br[3]}-${br[2]}-${br[1]}` : normalized);
  dateCache.set(normalized, date);
  return date;
}
