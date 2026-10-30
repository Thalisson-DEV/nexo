const MANAGEMENT_TIMEZONE = 'America/Sao_Paulo';

function offsetFor(date: Date): string {
  const part = new Intl.DateTimeFormat('en-US', {
    timeZone: MANAGEMENT_TIMEZONE,
    timeZoneName: 'longOffset',
    year: 'numeric'
  }).formatToParts(date).find((item) => item.type === 'timeZoneName')?.value;
  const match = part?.match(/GMT([+-]\d{2}:\d{2})/);
  return match?.[1] ?? '-03:00';
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

export function toSaoPauloIso(date: Date, endOfDay: boolean): string {
  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  const time = endOfDay ? '23:59:59.999' : '00:00:00.000';
  return `${year}-${month}-${day}T${time}${offsetFor(date)}`;
}
