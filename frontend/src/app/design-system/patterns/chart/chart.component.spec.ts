import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { UIChart } from 'primeng/chart';
import { CesChartComponent, formatCesChartValue } from './chart.component';

async function criar(inputs: Record<string, unknown>) {
  await TestBed.configureTestingModule({ imports: [CesChartComponent] }).compileComponents();
  const fixture = TestBed.createComponent(CesChartComponent);
  for (const [nome, valor] of Object.entries(inputs)) fixture.componentRef.setInput(nome, valor);
  fixture.detectChanges();
  return fixture;
}

describe('CesChartComponent', () => {
  it('expõe resumo textual acessível com maior e menor valor em pt-BR', async () => {
    const fixture = await criar({
      kind: 'bar',
      title: 'Saídas por obra',
      labels: ['Obra A', 'Obra B', 'Obra C'],
      series: [{ name: 'Quantidade', values: [1200, 80, 45.5] }],
      valueFormat: 'number',
    });
    const canvas = fixture.nativeElement.querySelector('.ces-chart__canvas canvas') as HTMLElement;
    expect(canvas.getAttribute('role')).toBe('img');
    expect(canvas.getAttribute('aria-label')).toBe(
      'Gráfico de barras com 3 categorias; maior valor: Obra A (1.200); menor: Obra C (45,5).',
    );
    expect(fixture.nativeElement.querySelector('figcaption').textContent).toContain('Saídas por obra');
  });

  it('usa os tokens de série e configura barras horizontais e empilhadas', async () => {
    document.documentElement.style.setProperty('--ces-chart-1', '#112233');
    const fixture = await criar({
      kind: 'bar-horizontal',
      title: 'Ranking',
      labels: ['Equipe Norte', 'Equipe Sul'],
      series: [{ name: 'Desvios', values: [7, 3] }],
    });
    const chart = fixture.debugElement.query(By.directive(UIChart)).componentInstance as UIChart;
    expect(chart.type).toBe('bar');
    expect((chart.options as { indexAxis: string }).indexAxis).toBe('y');
    const dataset = (chart.data as { datasets: { backgroundColor: string }[] }).datasets[0];
    expect(dataset.backgroundColor).toBe('#112233');

    fixture.componentRef.setInput('kind', 'bar-stacked');
    fixture.detectChanges();
    const empilhado = fixture.debugElement.query(By.directive(UIChart)).componentInstance as UIChart;
    expect((empilhado.options as { scales: { y: { stacked: boolean } } }).scales.y.stacked).toBe(true);
    document.documentElement.style.removeProperty('--ces-chart-1');
  });

  it('alterna para a tabela de dados equivalente e volta ao gráfico', async () => {
    const fixture = await criar({
      kind: 'line',
      title: 'Tendência',
      labels: ['01/09', '02/09'],
      series: [
        { name: 'Entradas', values: [10, null] },
        { name: 'Saídas', values: [5, 8] },
      ],
      valueFormat: 'integer',
      xAxisLabel: 'Dia',
    });
    const botao = fixture.nativeElement.querySelector('.ces-chart__toggle button') as HTMLButtonElement;
    botao.click();
    fixture.detectChanges();

    const tabela = fixture.nativeElement.querySelector('table') as HTMLTableElement;
    expect(tabela).not.toBeNull();
    expect([...tabela.querySelectorAll('thead th')].map((th) => th.textContent?.trim())).toEqual([
      'Dia',
      'Entradas',
      'Saídas',
    ]);
    expect(tabela.querySelector('tbody tr:nth-child(2) td')?.textContent?.trim()).toBe('—');
    expect(fixture.componentInstance.dataTableVisible()).toBe(true);

    (fixture.nativeElement.querySelector('.ces-chart__toggle button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('mostra estado vazio sem canvas quando não há dados', async () => {
    const fixture = await criar({ kind: 'pie', title: 'Composição', labels: [], series: [] });
    expect(fixture.nativeElement.querySelector('.ces-chart__empty')?.getAttribute('role')).toBe('status');
    expect(fixture.nativeElement.querySelector('p-chart')).toBeNull();
    expect(fixture.nativeElement.querySelector('.ces-chart__toggle')).toBeNull();
  });

  it('formata moeda, percentual e valores compactos no padrão brasileiro', () => {
    expect(formatCesChartValue(1234.5, 'currency')).toBe('R$ 1.234,50'.replace(' ', '\u00a0'));
    expect(formatCesChartValue(0.125, 'percent')).toBe('12,5%');
    expect(formatCesChartValue(2_500_000, 'number', true)).toMatch(/^2,5\s?mi/);
    expect(formatCesChartValue(Number.NaN, 'number')).toBe('—');
  });
  it('emite a seleção pelo clique no gráfico e pela linha da tabela de dados quando selectable', async () => {
    const fixture = await criar({
      kind: 'bar',
      title: 'Inspeções por dia',
      labels: ['01/09', '02/09'],
      series: [{ name: 'Realizado', values: [3, 5] }],
      selectable: true,
    });
    const selecoes: unknown[] = [];
    fixture.componentInstance.pointSelect.subscribe((selecao) => selecoes.push(selecao));

    const chart = fixture.debugElement.query(By.directive(UIChart)).componentInstance as UIChart;
    const onClick = (chart.options as { onClick: (e: unknown, el: unknown[]) => void }).onClick;
    onClick({}, [{ index: 1, datasetIndex: 0 }]);
    onClick({}, []);
    expect(selecoes).toEqual([{ index: 1, label: '02/09', seriesIndex: 0 }]);
    // A categoria inteira é área de clique, não só a barra/ponto.
    expect((chart.options as { interaction?: unknown }).interaction).toEqual({ mode: 'index', intersect: false, axis: 'x' });

    fixture.componentInstance.dataTableVisible.set(true);
    fixture.detectChanges();
    const botoes = fixture.nativeElement.querySelectorAll('.ces-chart__row-action button');
    expect(botoes.length).toBe(2);
    expect(botoes[0].getAttribute('aria-label')).toBe('Detalhar 01/09');
    botoes[0].click();
    expect(selecoes.at(-1)).toEqual({ index: 0, label: '01/09', seriesIndex: null });
  });

  it('usa passo inteiro no eixo de valores quando o formato é integer', async () => {
    const fixture = await criar({
      kind: 'bar',
      title: 'Notas por dia',
      labels: ['01/10', '02/10'],
      series: [{ name: 'Notas', values: [0, 2] }],
      valueFormat: 'integer',
    });
    const chart = fixture.debugElement.query(By.directive(UIChart)).componentInstance as UIChart;
    const escalas = (chart.options as { scales: { y: { ticks: { precision?: number } } } }).scales;
    expect(escalas.y.ticks.precision).toBe(0);
  });

  it('não expõe clique nem ações de linha quando não é selectable', async () => {
    const fixture = await criar({
      kind: 'bar',
      title: 'Estático',
      labels: ['A'],
      series: [{ name: 'S', values: [1] }],
    });
    const chart = fixture.debugElement.query(By.directive(UIChart)).componentInstance as UIChart;
    expect((chart.options as { onClick?: unknown }).onClick).toBeUndefined();
    expect((chart.options as { interaction?: unknown }).interaction).toBeUndefined();
    fixture.componentInstance.dataTableVisible.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.ces-chart__row-action')).toBeNull();
  });
});
