import { describe, expect, it } from 'vitest';
import { toSaoPauloIso } from './dashboard-date.utils';

describe('datas dos dashboards', () => {
  it('sends the selected calendar date with an explicit São Paulo offset', () => {
    const value = toSaoPauloIso(new Date(2026, 6, 1), false);
    expect(value).toMatch(/^2026-07-01T00:00:00\.000-03:00$/);
    expect(value.endsWith('Z')).toBe(false);
  });

  it('uses the inclusive end of day', () => {
    expect(toSaoPauloIso(new Date(2026, 6, 31), true)).toContain('T23:59:59.999-03:00');
  });
});
