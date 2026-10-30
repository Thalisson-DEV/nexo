/** Converte LocalDate da API sem passar por UTC ou por parsing de timestamp. */
export function parseLocalDate(value: string | null | undefined): Date | null {
  const parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '');
  if (!parts) return null;
  const year = Number(parts[1]);
  const month = Number(parts[2]);
  const day = Number(parts[3]);
  const date = new Date(year, month - 1, day, 12);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
    ? date : null;
}

export function toLocalDate(value: Date | null | undefined): string | null {
  if (!(value instanceof Date) || Number.isNaN(value.getTime())) return null;
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function displayLocalDate(value: string | null | undefined): string {
  const date = parseLocalDate(value);
  return date ? `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')}/${date.getFullYear()}` : '—';
}
