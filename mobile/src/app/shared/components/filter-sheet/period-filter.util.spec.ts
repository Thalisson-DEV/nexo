import { isWithinPeriod, periodStartTime } from './period-filter.util';

/** Data local deslocada em `days` dias a partir de agora, no formato ISO. */
function isoDaysAgo(days: number, hours = 12): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  date.setHours(hours, 0, 0, 0);
  return date.toISOString();
}

describe('period-filter.util', () => {
  describe('periodStartTime', () => {
    it('retorna null para "todos" (sem recorte de data)', () => {
      expect(periodStartTime('todos')).toBeNull();
    });

    it('retorna o início do dia local para "hoje"', () => {
      const start = new Date(periodStartTime('hoje')!);
      const hoje = new Date();

      expect(start.getHours()).toBe(0);
      expect(start.getMinutes()).toBe(0);
      expect(start.getDate()).toBe(hoje.getDate());
    });

    it('inclui o dia atual na contagem da janela', () => {
      const start = new Date(periodStartTime('7dias')!);
      const esperado = new Date();
      esperado.setHours(0, 0, 0, 0);
      esperado.setDate(esperado.getDate() - 6);

      expect(start.getTime()).toBe(esperado.getTime());
    });
  });

  describe('isWithinPeriod', () => {
    it('aceita qualquer data quando o período é "todos"', () => {
      expect(isWithinPeriod(isoDaysAgo(900), 'todos')).toBeTrue();
      expect(isWithinPeriod(undefined, 'todos')).toBeTrue();
      expect(isWithinPeriod('data-invalida', 'todos')).toBeTrue();
    });

    it('mantém registros de hoje no filtro "hoje"', () => {
      expect(isWithinPeriod(isoDaysAgo(0, 0), 'hoje')).toBeTrue();
      expect(isWithinPeriod(isoDaysAgo(0, 23), 'hoje')).toBeTrue();
    });

    it('remove registros de ontem no filtro "hoje"', () => {
      expect(isWithinPeriod(isoDaysAgo(1), 'hoje')).toBeFalse();
    });

    it('respeita os limites de 7 e 30 dias', () => {
      expect(isWithinPeriod(isoDaysAgo(6), '7dias')).toBeTrue();
      expect(isWithinPeriod(isoDaysAgo(7), '7dias')).toBeFalse();
      expect(isWithinPeriod(isoDaysAgo(29), '30dias')).toBeTrue();
      expect(isWithinPeriod(isoDaysAgo(30), '30dias')).toBeFalse();
    });

    it('mantém visível um registro com data futura (relógio adiantado)', () => {
      expect(isWithinPeriod(isoDaysAgo(-1), 'hoje')).toBeTrue();
    });

    it('descarta registros sem data ou com data inválida em períodos limitados', () => {
      expect(isWithinPeriod(undefined, '30dias')).toBeFalse();
      expect(isWithinPeriod(null, '30dias')).toBeFalse();
      expect(isWithinPeriod('', '30dias')).toBeFalse();
      expect(isWithinPeriod('nao-e-data', '30dias')).toBeFalse();
    });

    it('aceita objetos Date além de strings ISO', () => {
      const ontem = new Date();
      ontem.setDate(ontem.getDate() - 1);
      expect(isWithinPeriod(ontem, '7dias')).toBeTrue();
      expect(isWithinPeriod(ontem, 'hoje')).toBeFalse();
    });

    it('reaproveita o startTime informado, evitando recalcular por item', () => {
      const start = periodStartTime('7dias');
      expect(isWithinPeriod(isoDaysAgo(3), '7dias', start)).toBeTrue();
      expect(isWithinPeriod(isoDaysAgo(10), '7dias', start)).toBeFalse();
    });
  });
});
