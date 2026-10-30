import { describe, expect, it } from 'vitest';
import {
  criarFiltrosSolicitacao,
  formatarDataLocal,
  formatarLimiteDataHora,
  normalizarFiltrosSolicitacao
} from './solicitacao-financeira-filtros';

const query = (valores: Record<string, string>) => ({
  get: (chave: string) => valores[chave] ?? null
});

describe('filtros das solicitações financeiras', () => {
  it('hidrata valores escalares válidos da URL e preserva ativos como padrão', () => {
    const filtros = criarFiltrosSolicitacao(
      'PAGAMENTO',
      'todas',
      query({
        status: 'APROVADO',
        numero: '1042',
        temNf: 'false',
        valorMinimo: '1500.50',
        dataCriacaoInicio: '2026-09-01T00:00:00-03:00'
      })
    );

    expect(filtros.status).toBe('APROVADO');
    expect(filtros.numero).toBe(1042);
    expect(filtros.temNf).toBe(false);
    expect(filtros.valorMinimo).toBe(1500.5);
    expect(filtros.ativo).toBe(true);
    expect(filtros.dataCriacaoInicio).toEqual(new Date(2026, 8, 1));
  });

  it('descarta status, booleano, número e data inválidos vindos da URL', () => {
    const filtros = criarFiltrosSolicitacao(
      'COMPRA',
      'todas',
      query({ status: 'TODOS', temNf: 'sim', valorMinimo: 'R$ 10,00', dataCriacaoInicio: '01/09/2026' })
    );

    expect(filtros.status).toBeNull();
    expect(filtros.temNf).toBeUndefined();
    expect(filtros.valorMinimo).toBeUndefined();
    expect(filtros.dataCriacaoInicio).toBeNull();
  });

  it('envia LocalDate e limites inclusivos de OffsetDateTime no formato do backend', () => {
    const filtros = criarFiltrosSolicitacao('COMPRA', 'todas', query({}));
    filtros.vencimentoInicio = new Date(2026, 8, 1);
    filtros.dataCriacaoInicio = new Date(2026, 8, 1, 14, 30);
    filtros.dataCriacaoFim = new Date(2026, 8, 30, 8, 15);

    const normalizados = normalizarFiltrosSolicitacao(filtros);

    expect(normalizados.vencimentoInicio).toBe('2026-09-01');
    expect(normalizados.dataCriacaoInicio).toMatch(
      /^2026-09-01T00:00:00\.000[+-]\d{2}:\d{2}$/
    );
    expect(normalizados.dataCriacaoFim).toMatch(
      /^2026-09-30T23:59:59\.999[+-]\d{2}:\d{2}$/
    );
  });

  it('formata datas locais sem conversão UTC e mantém o offset do navegador', () => {
    const data = new Date(2026, 8, 1, 12);

    expect(formatarDataLocal(data)).toBe('2026-09-01');
    expect(formatarLimiteDataHora(data, false)).toContain('T00:00:00.000');
    expect(formatarLimiteDataHora(data, true)).toContain('T23:59:59.999');
  });
});
