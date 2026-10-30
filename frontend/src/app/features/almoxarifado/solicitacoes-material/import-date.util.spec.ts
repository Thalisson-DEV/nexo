import { describe, expect, it } from 'vitest';
import { toLocalDate } from '../../../core/utils/local-date.util';
import { parseImportDate } from './import-date.util';

describe('parseImportDate', () => {
  it('converte datas do CSV e da API sem alterar o dia', () => {
    expect(toLocalDate(parseImportDate('23/09/2026'))).toBe('2026-09-23');
    expect(toLocalDate(parseImportDate('2026-09-23'))).toBe('2026-09-23');
  });

  it('rejeita datas inválidas e mantém referência estável para o seletor', () => {
    expect(parseImportDate('31/02/2026')).toBeNull();
    expect(parseImportDate('2026-09-23')).toBe(parseImportDate('2026-09-23'));
  });
});
