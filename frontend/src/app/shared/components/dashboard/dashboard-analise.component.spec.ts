import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { DashboardAnaliseComponent } from './dashboard-analise.component';
import { OrigemAnalise, VistaAnalise } from './dashboard-analise.models';

// O jsdom não tem ResizeObserver (usado pelas abas do PrimeNG).
globalThis.ResizeObserver ??= class {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
} as unknown as typeof ResizeObserver;

interface Nota { id: string; ref: string; valor: number; hora: number }

const NOTAS: Nota[] = [
  { id: 'a', ref: 'N-1', valor: 100, hora: 8 },
  { id: 'b', ref: 'N-2', valor: 250, hora: 9 },
  { id: 'c', ref: 'N-3', valor: 50, hora: 9 }
];

function origem(parcial: Partial<OrigemAnalise<Nota>> = {}): OrigemAnalise<Nota> {
  return {
    chave: 'dia:2026-10-08',
    titulo: 'Notas que compõem este número',
    rotuloAba: 'Origem · notas',
    colunas: [{ key: 'ref', header: 'Nota' }, { key: 'valor', header: 'Valor', type: 'currency' }],
    carregar: () => of({ linhas: NOTAS, total: 3 }),
    chaveLinha: (n) => n.id,
    conferencia: { quantidadeEsperada: 3, valorEsperado: 400, somar: (n) => n.valor, formato: 'moeda' },
    detalhar: (n) => ({ titulo: `Nota ${n.ref}`, campos: [{ rotulo: 'Valor', valor: String(n.valor) }] }),
    ...parcial
  };
}

function vista(parcial: Partial<VistaAnalise> = {}): VistaAnalise {
  return {
    chave: 'dia:2026-10-08',
    eyebrow: 'Dia',
    titulo: 'Quinta-feira, 08 de outubro',
    kpis: [{ rotulo: 'Produção do dia', valor: 'R$ 400,00' }],
    graficos: [{
      id: 'por-hora',
      titulo: 'Notas por hora',
      tipo: 'bar',
      rotulos: ['08h', '09h'],
      series: [{ name: 'Notas', values: [1, 2] }],
      aoSelecionar: (i) => ({ tipo: 'filtrar', filtro: { chave: `h${i}`, rotulo: `Hora ${8 + i}`, aceita: (n: Nota) => n.hora === 8 + i } })
    }],
    origem: origem(),
    copiloto: { foco: { tipo: 'secao', id: 'dia', rotulo: 'Dia' }, pergunta: 'O que houve?' },
    ...parcial
  };
}

async function montar(v: VistaAnalise, entradas: Record<string, unknown> = {}) {
  await TestBed.configureTestingModule({ imports: [DashboardAnaliseComponent], providers: [provideRouter([])] }).compileComponents();
  const fixture = TestBed.createComponent(DashboardAnaliseComponent);
  fixture.componentRef.setInput('vista', v);
  for (const [nome, valor] of Object.entries(entradas)) fixture.componentRef.setInput(nome, valor);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

type Interno = {
  aba: () => unknown;
  visiveis: () => Record<string, Nota[]>;
  conferencias: () => Record<string, { tom: string; texto: string } | null>;
  detalhes: () => Record<string, { titulo: string } | null>;
  selecionarPonto: (g: VistaAnalise['graficos'][number], s: { index: number; label: string; seriesIndex: number | null }) => void;
  selecionarRegistro: (o: OrigemAnalise<Nota>, n: Nota) => void;
};

describe('DashboardAnaliseComponent', () => {
  afterEach(() => document.body.querySelectorAll('.p-drawer').forEach((el) => el.remove()));

  it('carrega a origem, confere a soma com o painel e mostra trilha, KPIs e Copiloto', async () => {
    const carregar = vi.fn(() => of({ linhas: NOTAS, total: 3 }));
    const fixture = await montar(vista({ origem: origem({ carregar }) }), { trilha: ['Produção no período'], copiloto: true });
    const interno = fixture.componentInstance as unknown as Interno;
    const drawer = document.body.querySelector('.p-drawer') as HTMLElement;

    expect(carregar).toHaveBeenCalledOnce();
    expect(drawer.textContent).toContain('Quinta-feira, 08 de outubro');
    expect(drawer.textContent).toContain('Produção do dia');
    expect(drawer.querySelector('.an-trilha')!.textContent).toContain('Produção no período');
    expect(drawer.querySelector('.an-copiloto')).not.toBeNull();
    expect(interno.conferencias()['origem']).toMatchObject({ tom: 'success' });
    expect(interno.conferencias()['origem']!.texto).toContain('fecha com o painel');

    const voltar = vi.fn();
    fixture.componentInstance.voltar.subscribe(voltar);
    (drawer.querySelector('.an-trilha button') as HTMLButtonElement).click();
    expect(voltar).toHaveBeenCalledWith(1);
  });

  it('avisa quando a origem não fecha com o painel', async () => {
    const fixture = await montar(vista({ origem: origem({ conferencia: { quantidadeEsperada: 4, valorEsperado: 500, somar: (n) => n.valor, formato: 'moeda' } }) }));
    const interno = fixture.componentInstance as unknown as Interno;
    expect(interno.conferencias()['origem']).toMatchObject({ tom: 'warning' });
    expect(interno.conferencias()['origem']!.texto).toContain('o painel mostra 4');
  });

  it('clique num ponto pode filtrar a origem; clique numa linha abre o detalhe do registro', async () => {
    const fixture = await montar(vista());
    const interno = fixture.componentInstance as unknown as Interno;
    const grafico = fixture.componentInstance.vista()!.graficos[0];

    interno.selecionarPonto(grafico, { index: 1, label: '09h', seriesIndex: 0 });
    fixture.detectChanges();
    expect(interno.aba()).toBe('origem');
    expect(interno.visiveis()['origem'].map((n) => n.id)).toEqual(['b', 'c']);

    interno.selecionarRegistro(fixture.componentInstance.vista()!.origem!, NOTAS[1]);
    fixture.detectChanges();
    expect(interno.detalhes()['origem']!.titulo).toBe('Nota N-2');
  });

  it('clique que aprofunda emite o novo alvo; vizinhos trocam sem empilhar', async () => {
    const v = vista({
      graficos: [{ id: 'g', titulo: 'G', tipo: 'bar', rotulos: ['EQ'], series: [{ name: 'S', values: [1] }], aoSelecionar: () => ({ tipo: 'navegar', alvo: { dimension: 'equipe', value: 'e1' } }) }],
      proximo: { alvo: { dimension: 'dia', value: '2026-10-09' }, rotulo: '09/10' }
    });
    const fixture = await montar(v);
    const navegar = vi.fn();
    const trocar = vi.fn();
    fixture.componentInstance.navegar.subscribe(navegar);
    fixture.componentInstance.trocar.subscribe(trocar);
    (fixture.componentInstance as unknown as Interno).selecionarPonto(v.graficos[0], { index: 0, label: 'EQ', seriesIndex: 0 });
    expect(navegar).toHaveBeenCalledWith({ dimension: 'equipe', value: 'e1' });

    const proximo = document.body.querySelector('.an-passos ces-icon-button:last-child button') as HTMLButtonElement;
    proximo.click();
    expect(trocar).toHaveBeenCalledWith({ dimension: 'dia', value: '2026-10-09' });
  });

  it('várias origens carregam em paralelo; a ação escolhe a aba da origem', async () => {
    const desvios = origem({ id: 'desvios', chave: 'desvios:x', rotuloAba: 'Desvios', carregar: () => of({ linhas: [NOTAS[2]], total: 1 }), conferencia: undefined });
    const v = vista({
      origensExtras: [desvios],
      graficos: [{ id: 'g', titulo: 'G', tipo: 'bar', rotulos: ['x'], series: [{ name: 'S', values: [1] }], aoSelecionar: () => ({ tipo: 'registro', id: 'c', origem: 'desvios' }) }]
    });
    const fixture = await montar(v);
    const interno = fixture.componentInstance as unknown as Interno;
    expect(interno.visiveis()['origem'].length).toBe(3);
    expect(interno.visiveis()['desvios'].length).toBe(1);
    interno.selecionarPonto(v.graficos[0], { index: 0, label: 'x', seriesIndex: 0 });
    expect(interno.aba()).toBe('desvios');
    expect(interno.detalhes()['desvios']!.titulo).toBe('Nota N-3');
  });

  it('erro na origem mostra nova tentativa sem perder a análise', async () => {
    const carregar = vi.fn<() => ReturnType<OrigemAnalise<Nota>['carregar']>>(() => throwError(() => new Error('x')));
    const fixture = await montar(vista({ origem: origem({ carregar }) }));
    const interno = fixture.componentInstance as unknown as Interno;
    expect(interno.conferencias()['origem']).toBeNull();
    carregar.mockReturnValue(of({ linhas: NOTAS, total: 3 }));
    fixture.componentInstance.carregarOrigem(fixture.componentInstance.vista()!.origem!);
    expect(carregar).toHaveBeenCalledTimes(2);
    expect(interno.conferencias()['origem']).toMatchObject({ tom: 'success' });
  });
});
