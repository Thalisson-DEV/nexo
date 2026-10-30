import { describe, expect, it } from 'vitest';
import { displayLocalDate, parseLocalDate, toLocalDate } from './local-date.util';

describe('LocalDate sem conversao UTC', () => {
  it('preserva o dia na ida e volta', () => {
    expect(toLocalDate(parseLocalDate('2026-09-25'))).toBe('2026-09-25');
    expect(displayLocalDate('2026-09-25')).toBe('25/09/2026');
  });

  it('trata vazios e dias inexistentes sem Invalid Date', () => {
    expect(parseLocalDate(null)).toBeNull();
    expect(parseLocalDate('2026-02-31')).toBeNull();
    expect(toLocalDate(new Date(Number.NaN))).toBeNull();
    expect(displayLocalDate('2026-02-31')).toBe('—');
  });
});
