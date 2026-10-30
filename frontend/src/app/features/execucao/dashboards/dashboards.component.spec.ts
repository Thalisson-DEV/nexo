import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { NotificationService } from '@ces/ui';
import {
  DashboardOperacionalFiltros,
  DashboardOperacionalResponse,
  DashboardTurnosResponse,
  EquipeOperacional,
  EquipeTurno
} from '../../../core/models/dashboard.models';
import { AssistenteService } from '../../../core/services/assistente.service';
import { AuthService } from '../../../core/services/auth.service';
import { DashboardService } from '../../../core/services/dashboard.service';
import { DashboardOperacionalComponent } from './operacional/dashboard-operacional.component';
import { RecorteDashboardService } from './recorte-dashboard.service';
import { DashboardTurnosComponent } from './turnos/dashboard-turnos.component';

const recorteFalso = {
  carregando: signal(false),
  opcoesBases: signal([{ label: 'Base Salvador', value: 'b1' }]),
  opcoesProcessos: signal([]),
  carregar: vi.fn(),
  nomeDaBase: () => null,
  nomeDoProcesso: () => null,
  resumoDasBases: () => null,
  resumoDosProcessos: () => null
};

function configurar(dashboard: Partial<DashboardService>) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: DashboardService, useValue: dashboard },
      { provide: RecorteDashboardService, useValue: recorteFalso },
      { provide: AuthService, useValue: { currentUser: signal({ id: 'u1', permissoes: [] }), token: () => null } },
      { provide: AssistenteService, useValue: {} },
      { provide: NotificationService, useValue: { show: vi.fn() } }
    ]
  });
}

function texto(el: HTMLElement): string {
  return (el.textContent ?? '').replace(/\s+/g, ' ').trim();
}

describe('Dashboard de turnos', () => {
  const equipe = (parcial: Partial<EquipeTurno>): EquipeTurno => ({
    idEquipe: parcial.codigo!,
    codigo: 'EQ',
    baseOperacional: 'Base Salvador',
    processo: 'Corte',
    situacao: 'EM_TURNO',
    idTurno: 't-' + parcial.codigo,
    quantidadeTurnos: 1,
    abertura: '2026-10-09T07:00:00-03:00',
    fechamento: null,
    minutosEmTurno: 480,
    abertoDesdeOutroDia: false,
    abertoPor: 'MARIA APARECIDA SOUZA',
    placa: 'ABC1D23',
    membros: ['JOAO LIMA', 'MARIA APARECIDA SOUZA'],
    notas: 0,
    producao: 0,
    valorMedioNota: 0,
    metaDiaria: null,
    percentualMetaDiaria: null,
    primeiroLancamento: null,
    ultimoLancamento: null,
    minutosSemLancamento: null,
    semLancamentoRecente: false,
    lancamentos: [],
    tiposServico: [],
    ...parcial
  });

  const resposta: DashboardTurnosResponse = {
    data: '2026-10-09',
    hoje: true,
    diaUtil: true,
    geradoEm: '2026-10-09T15:00:00-03:00',
    limiteSemLancamentoMinutos: 120,
    resumo: {
      equipesAtivas: 3, emTurno: 2, encerrados: 0, semTurno: 1, semLancamentoRecente: 1, turnosAbertosDeOutroDia: 0,
      notas: 2, producao: 300, equipesComMeta: 1, metaDiaria: 1000, producaoEquipesComMeta: 300, percentualMetaDiaria: 30
    },
    tiposServico: [
      { idTipoServico: 'ts-cs', codigo: 'CS', descricao: 'CORTE', notas: 2, producao: 300, valorMedioNota: 150, percentualNotas: 100 }
    ],
    equipes: [
      equipe({
        codigo: 'EQ-01', notas: 2, producao: 300, metaDiaria: 1000, percentualMetaDiaria: 30,
        ultimoLancamento: '2026-10-09T12:30:00-03:00',
        lancamentos: [
          { idExecucao: 'e1', dataLancamento: '2026-10-09T08:00:00-03:00', valor: 100, tipoServico: 'CORTE' },
          { idExecucao: 'e2', dataLancamento: '2026-10-09T12:30:00-03:00', valor: 200, tipoServico: 'CORTE' }
        ],
        tiposServico: [
          { idTipoServico: 'ts-cs', codigo: 'CS', descricao: 'CORTE', notas: 2, producao: 300, valorMedioNota: 150, percentualNotas: 100 }
        ]
      }),
      equipe({ codigo: 'EQ-02', minutosSemLancamento: 300, semLancamentoRecente: true }),
      equipe({ codigo: 'EQ-03', situacao: 'SEM_TURNO', idTurno: null, abertura: null, membros: [], abertoPor: null })
    ]
  };

  function montar() {
    const gerarDashboardTurnos = vi.fn(() => of(resposta));
    configurar({ gerarDashboardTurnos });
    const fixture = TestBed.createComponent(DashboardTurnosComponent);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement, gerarDashboardTurnos };
  }

  it('carrega o dia corrente e põe primeiro a equipe há mais tempo sem lançar', () => {
    const { el, gerarDashboardTurnos } = montar();

    expect(gerarDashboardTurnos).toHaveBeenCalledTimes(1);
    const cartoes = [...el.querySelectorAll('article.ed-team')] as HTMLElement[];
    expect(cartoes.map((c) => c.querySelector('h3')?.textContent)).toEqual(['EQ-02', 'EQ-01', 'EQ-03']);
    expect(texto(cartoes[0])).toContain('Sem lançar há 5h');
    expect(cartoes[0].getAttribute('data-atencao')).toBe('true');
    expect(texto(cartoes[2])).toContain('A equipe ainda não abriu turno hoje.');
  });

  it('mostra composição, meta diária e um ponto por nota na linha do tempo', () => {
    const { el } = montar();
    const eq01 = [...el.querySelectorAll('article.ed-team')].find((c) => c.querySelector('h3')?.textContent === 'EQ-01') as HTMLElement;

    expect(texto(eq01)).toContain('Maria Souza');
    expect(texto(eq01)).toContain('ABC1D23');
    expect(texto(eq01)).toContain('2 pessoas');
    expect(eq01.querySelector('ces-meter')).not.toBeNull();
    const marcas = eq01.querySelectorAll('a.ed-timeline__mark');
    expect(marcas).toHaveLength(2);
    expect(marcas[0].getAttribute('aria-label')).toContain('Nota de CORTE lançada às');
    expect(eq01.querySelector('a.ed-link')?.getAttribute('href')).toBe('/execucao/turnos/t-EQ-01/detalhamento');
  });

  it('filtra as equipes pelas abas', () => {
    const { fixture, el } = montar();
    const aba = [...el.querySelectorAll('.ed-section .dash-tab')].find((b) => texto(b as HTMLElement).startsWith('Sem turno')) as HTMLButtonElement;

    aba.click();
    fixture.detectChanges();

    const cartoes = el.querySelectorAll('article.ed-team');
    expect(cartoes).toHaveLength(1);
    expect(aba.getAttribute('aria-pressed')).toBe('true');
  });

  it('agrupa por base e mostra as equipes em miniatura', () => {
    const { fixture, el } = montar();
    const botao = (rotulo: string) =>
      [...el.querySelectorAll('.ed-equipes-toolbar .dash-tab')].find((b) => texto(b as HTMLElement) === rotulo) as HTMLButtonElement;

    botao('Base').click();
    botao('Miniaturas').click();
    fixture.detectChanges();

    expect(el.querySelectorAll('article.ed-team')).toHaveLength(0);
    expect(el.querySelectorAll('.ed-mini').length).toBeGreaterThan(0);
    expect(texto(el.querySelector('.ed-grupo__head h3') as HTMLElement)).toBe('Base Salvador');
    expect(el.querySelector('.ed-mini')?.getAttribute('aria-label')).toContain('Analisar turno');

    botao('Sem grupo').click();
    botao('Cartões').click();
  });
});

describe('Dashboard operacional', () => {
  const equipe = (parcial: Partial<EquipeOperacional>): EquipeOperacional => ({
    idEquipe: parcial.codigo!,
    codigo: 'EQ',
    baseOperacional: 'Base Salvador',
    processo: 'Corte',
    tipoEquipe: 'Leve',
    placa: 'ABC1D23',
    producao: 0,
    notas: 0,
    valorMedioNota: 0,
    diasComTurno: 0,
    diasComProducao: 0,
    producaoMediaPorDia: 0,
    metaMensal: null,
    metaPeriodo: null,
    metaEsperadaAteAgora: null,
    percentualMeta: null,
    projecao: null,
    situacao: 'SEM_META',
    ultimoLancamento: null,
    producaoDiaria: [
      { data: '2026-10-01', notas: 0, valor: 0, meta: null },
      { data: '2026-10-02', notas: 0, valor: 0, meta: null }
    ],
    tiposServico: [],
    ...parcial
  });

  const resposta: DashboardOperacionalResponse = {
    inicio: '2026-10-01',
    fim: '2026-10-09',
    geradoEm: '2026-10-09T15:00:00-03:00',
    diasUteisPeriodo: 7,
    diasUteisEncerrados: 6,
    resumo: {
      producao: 7600, producaoPeriodoAnterior: 8000, variacaoPercentual: -5, notas: 4, valorMedioNota: 1900,
      equipesAtivas: 2, equipesComProducao: 2, equipesComMeta: 2, metaPeriodo: 14000, producaoEquipesComMeta: 7600,
      percentualMeta: 54.3, projecaoEquipesComMeta: 7600, equipesAbaixoDoRitmo: 1
    },
    producaoDiaria: [],
    tiposServico: [
      { idTipoServico: 'ts-cl', codigo: 'CL', descricao: 'LIGAÇÃO NOVA', notas: 3, producao: 6600, valorMedioNota: 2200, percentualNotas: 75 },
      { idTipoServico: null, codigo: null, descricao: 'Não informado', notas: 1, producao: 1000, valorMedioNota: 1000, percentualNotas: 25 }
    ],
    equipes: [
      equipe({
        codigo: 'EQ-01', situacao: 'NO_RITMO', producao: 6600, notas: 3, metaPeriodo: 7000, percentualMeta: 94.3, metaMensal: 22000,
        tiposServico: [
          { idTipoServico: 'ts-cl', codigo: 'CL', descricao: 'LIGAÇÃO NOVA', notas: 3, producao: 6600, valorMedioNota: 2200, percentualNotas: 100 }
        ]
      }),
      equipe({ codigo: 'EQ-02', situacao: 'ABAIXO_DO_RITMO', producao: 1000, notas: 1, metaPeriodo: 7000, percentualMeta: 14.3, metaMensal: 22000 })
    ]
  };

  function montar() {
    const gerarDashboardOperacional = vi.fn((_filtros: DashboardOperacionalFiltros) => of(resposta));
    configurar({ gerarDashboardOperacional });
    const fixture = TestBed.createComponent(DashboardOperacionalComponent);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement, gerarDashboardOperacional };
  }

  it('consulta o mês corrente por padrão e resume a produção no topo', () => {
    const { el, gerarDashboardOperacional } = montar();

    const filtros = gerarDashboardOperacional.mock.calls[0][0];
    expect(filtros.inicio!).toMatch(/^\d{4}-\d{2}-01$/);
    expect(el.querySelectorAll('app-dashboard-metric-card')).toHaveLength(4);
    expect(texto(el.querySelector('.dash-metrics') as HTMLElement)).toContain('−5% vs. período anterior');
  });

  it('põe primeiro quem está abaixo do ritmo e mostra a situação em texto', () => {
    const { el } = montar();
    const cartoes = [...el.querySelectorAll('article.ed-team')] as HTMLElement[];

    expect(cartoes.map((c) => c.querySelector('h3')?.textContent)).toEqual(['EQ-02', 'EQ-01']);
    expect(texto(cartoes[0].querySelector('ces-status') as HTMLElement)).toBe('Abaixo do ritmo');
    expect(cartoes[0].querySelectorAll('.ed-bars li')).toHaveLength(2);
    expect(cartoes[0].querySelector('a.ed-link')?.getAttribute('href')).toContain('/execucao/comercial?equipe=EQ-02');
  });

  it('mostra as notas por tipo de serviço no período e no cartão da equipe', () => {
    const { el } = montar();
    const linhas = [...el.querySelectorAll('table.ed-tipos tbody tr')] as HTMLElement[];

    expect(linhas.map((l) => texto(l.querySelector('th') as HTMLElement))).toEqual(['CL LIGAÇÃO NOVA', 'Não informado']);
    expect(linhas[1].classList).toContain('ed-tipos__sem-tipo');
    const eq01 = [...el.querySelectorAll('article.ed-team')].find((c) => c.querySelector('h3')?.textContent === 'EQ-01')!;
    expect(texto(eq01.querySelector('.ed-team__tags[aria-label^="Notas por tipo"]') as HTMLElement)).toBe('LIGAÇÃO NOVA · 3');
  });
});
