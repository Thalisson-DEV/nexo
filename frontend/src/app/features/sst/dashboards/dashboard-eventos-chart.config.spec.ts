import { describe, expect, it, vi } from 'vitest';
import {
  PALETAS_GRADIENTE_PRIMARIA,
  calcularEventosSemLesao,
  criarGradienteBarra,
  montarDadosGraficoEvolucao,
  obterPaletaPrimaria
} from './dashboard-eventos-chart.config';

describe('montarDadosGraficoEvolucao', () => {
  const serie = [
    { data: '2026-08-01', rotulo: '01/08', total: 2, comLesao: 2, diasPerdidos: 0 },
    { data: '2026-08-02', rotulo: '02/08', total: 3, comLesao: 1, diasPerdidos: 0 }
  ];

  it('usa barras para o total e linha com pontos para lesões', () => {
    const dados = montarDadosGraficoEvolucao(serie);
    const total = dados.datasets[0];
    const comLesao = dados.datasets[1];

    expect(total.label).toBe('Total de eventos');
    expect(total.type).toBe('bar');
    expect(total.data).toEqual([2, 3]);
    expect(comLesao.label).toBe('Com lesão');
    expect(comLesao.type).toBe('line');
    expect(comLesao.data).toEqual([2, 1]);
    expect(comLesao['pointRadius']).toBeGreaterThan(0);
    expect(comLesao.order).toBeLessThan(total.order);
  });

  it('mantém as duas representações distinguíveis mesmo quando os valores coincidem', () => {
    const dados = montarDadosGraficoEvolucao([serie[0]]);

    expect(dados.datasets[0].data).toEqual(dados.datasets[1].data);
    expect(dados.datasets[0].type).not.toBe(dados.datasets[1].type);
    expect(dados.datasets[0]['backgroundColor']).not.toBe(
      dados.datasets[1]['backgroundColor']
    );
  });

  it('calcula no tooltip a parcela sem lesão sem permitir valor negativo', () => {
    expect(calcularEventosSemLesao(3, 1)).toBe(2);
    expect(calcularEventosSemLesao(1, 1)).toBe(0);
    expect(calcularEventosSemLesao(1, 2)).toBe(0);
  });

  it('aplica a cor primária selecionada pelo usuário no dataset de barras', () => {
    const dadosEmerald = montarDadosGraficoEvolucao(serie, 'emerald');
    const totalEmerald = dadosEmerald.datasets[0];
    expect(totalEmerald['borderColor']).toBe(PALETAS_GRADIENTE_PRIMARIA.emerald.borda);

    const dadosViolet = montarDadosGraficoEvolucao(serie, 'violet');
    const totalViolet = dadosViolet.datasets[0];
    expect(totalViolet['borderColor']).toBe(PALETAS_GRADIENTE_PRIMARIA.violet.borda);

    const dadosAmber = montarDadosGraficoEvolucao(serie, 'amber');
    const totalAmber = dadosAmber.datasets[0];
    expect(totalAmber['borderColor']).toBe(PALETAS_GRADIENTE_PRIMARIA.amber.borda);
  });

  it('retorna fallback sólido forte quando o contexto de canvas ainda não está pronto', () => {
    const dados = montarDadosGraficoEvolucao(serie, 'blue');
    const total = dados.datasets[0];
    const bgFn = total['backgroundColor'] as (context: unknown) => unknown;

    expect(typeof bgFn).toBe('function');
    expect(bgFn(null)).toBe(PALETAS_GRADIENTE_PRIMARIA.blue.forte);
    expect(bgFn({})).toBe(PALETAS_GRADIENTE_PRIMARIA.blue.forte);
    expect(bgFn({ chart: null })).toBe(PALETAS_GRADIENTE_PRIMARIA.blue.forte);
  });

  it('cria gradiente linear vertical vindo do claro ao forte quando contexto e área estão disponíveis', () => {
    const addColorStop = vi.fn();
    const fakeGradient = { addColorStop };
    const createLinearGradient = vi.fn().mockReturnValue(fakeGradient);
    const mockCtx = { createLinearGradient } as unknown as CanvasRenderingContext2D;

    const dados = montarDadosGraficoEvolucao(serie, 'emerald');
    const total = dados.datasets[0];
    const bgFn = total['backgroundColor'] as (context: unknown) => unknown;

    const result = bgFn({
      chart: {
        ctx: mockCtx,
        chartArea: { top: 10, bottom: 200 }
      },
      element: {
        y: 50,
        base: 200
      }
    });

    expect(result).toBe(fakeGradient);
    expect(createLinearGradient).toHaveBeenCalledWith(0, 50, 0, 200);
    expect(addColorStop).toHaveBeenCalledWith(0, PALETAS_GRADIENTE_PRIMARIA.emerald.claro);
    expect(addColorStop).toHaveBeenCalledWith(1, PALETAS_GRADIENTE_PRIMARIA.emerald.forte);
  });
});

describe('obterPaletaPrimaria e criarGradienteBarra', () => {
  it('retorna a paleta correta para cada cor suportada', () => {
    expect(obterPaletaPrimaria('blue')).toEqual(PALETAS_GRADIENTE_PRIMARIA.blue);
    expect(obterPaletaPrimaria('emerald')).toEqual(PALETAS_GRADIENTE_PRIMARIA.emerald);
    expect(obterPaletaPrimaria('violet')).toEqual(PALETAS_GRADIENTE_PRIMARIA.violet);
    expect(obterPaletaPrimaria('amber')).toEqual(PALETAS_GRADIENTE_PRIMARIA.amber);
    expect(obterPaletaPrimaria('rose')).toEqual(PALETAS_GRADIENTE_PRIMARIA.rose);
    expect(obterPaletaPrimaria('slate')).toEqual(PALETAS_GRADIENTE_PRIMARIA.slate);
  });

  it('retorna a paleta azul como padrão para valores nulos, vazios ou inválidos', () => {
    expect(obterPaletaPrimaria(null)).toEqual(PALETAS_GRADIENTE_PRIMARIA.blue);
    expect(obterPaletaPrimaria(undefined)).toEqual(PALETAS_GRADIENTE_PRIMARIA.blue);
    expect(obterPaletaPrimaria('invalido' as any)).toEqual(PALETAS_GRADIENTE_PRIMARIA.blue);
  });

  it('cria gradiente com paradas de cor 0 (claro) e 1 (forte)', () => {
    const addColorStop = vi.fn();
    const mockGradient = { addColorStop };
    const mockCtx = {
      createLinearGradient: vi.fn().mockReturnValue(mockGradient)
    } as unknown as CanvasRenderingContext2D;

    const grad = criarGradienteBarra(mockCtx, 30, 150, '#60a5fa', '#1d4ed8');
    expect(grad).toBe(mockGradient);
    expect(mockCtx.createLinearGradient).toHaveBeenCalledWith(0, 30, 0, 150);
    expect(addColorStop).toHaveBeenCalledWith(0, '#60a5fa');
    expect(addColorStop).toHaveBeenCalledWith(1, '#1d4ed8');
  });
});
