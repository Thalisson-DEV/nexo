import { of } from 'rxjs';
import {
  DashboardOperacionalResponse,
  DashboardTurnosResponse,
  EquipeOperacional,
  EquipeTurno,
  NotaDoRecorte
} from '../../../../core/models/dashboard.models';
import { DashboardService } from '../../../../core/services/dashboard.service';
import { detalheNota, faixasDeValor, horaDe, porHora } from './analise-comum';
import { vistaOperacional } from './analise-operacional';
import { graficosDaAnalise } from './analise-copiloto';
import { vistaTurnos } from './analise-turnos';

const service = { consultarNotas: vi.fn(() => of({ content: [], page: { totalElements: 0, totalPages: 0, number: 0, size: 500 } })) } as unknown as DashboardService;

function nota(parcial: Partial<NotaDoRecorte>): NotaDoRecorte {
  return {
    id: 'n1',
    referenciaNota: '100',
    idTurno: 't1',
    idEquipe: 'e1',
    codigoEquipe: 'EQ-01',
    baseOperacional: 'Base Salvador',
    processo: 'Corte',
    idTipoServico: null,
    codigoTipoServico: null,
    tipoServico: null,
    dataLancamento: '2026-10-08T09:10:00-03:00',
    valor: 100,
    acoes: [],
    materiais: 0,
    latitude: null,
    longitude: null,
    ...parcial
  };
}

describe('Análise · peças comuns', () => {
  it('agrupa notas por hora de São Paulo, preenchendo as horas vazias', () => {
    const r = porHora([
      nota({ dataLancamento: '2026-10-08T09:10:00-03:00', valor: 100 }),
      nota({ dataLancamento: '2026-10-08T11:59:00-03:00', valor: 50 }),
      nota({ dataLancamento: '2026-10-08T14:05:00Z', valor: 25 }) // 11h em São Paulo
    ]);
    expect(r.horas).toEqual([9, 10, 11]);
    expect(r.notas).toEqual([1, 0, 2]);
    expect(r.valor).toEqual([100, 0, 75]);
    expect(horaDe('2026-10-08T02:30:00Z')).toBe(23);
  });

  it('distribui valores nas faixas sem perder nenhum', () => {
    const f = faixasDeValor([0, 99, 100, 300, 999, 5000]);
    expect(f.contagem.reduce((a, b) => a + b, 0)).toBe(6);
    expect(f.contagem[0]).toBe(2);
    expect(f.contagem.at(-1)).toBe(1);
  });

  it('detalhe da nota mede o intervalo desde a nota anterior da mesma equipe', () => {
    const a = nota({ id: 'a', dataLancamento: '2026-10-08T09:00:00-03:00' });
    const b = nota({ id: 'b', dataLancamento: '2026-10-08T10:30:00-03:00', idTurno: 't9', acoes: ['A1 · Corte'] });
    const outraEquipe = nota({ id: 'c', idEquipe: 'e2', dataLancamento: '2026-10-08T10:00:00-03:00' });
    const detalhe = detalheNota(b, [a, b, outraEquipe]);
    expect(detalhe.campos.find((c) => c.rotulo === 'Desde a nota anterior')!.valor).toContain('1h 30min');
    expect(detalhe.listas![0].itens).toEqual(['A1 · Corte']);
    expect(detalhe.links!.map((l) => l.rotulo)).toEqual(['Abrir nota completa', 'Relatório do turno']);
  });
});

describe('Análise · dashboard operacional', () => {
  const obra = { idObra: 'o1', numero: 'OB-100', titulo: 'Rede', equipes: 1, servicos: 5, diasComExecucao: 2, producao: 900, percentualProducao: 100, ultimoApontamento: '2026-10-02' };
  const equipe = (parcial: Partial<EquipeOperacional>): EquipeOperacional => ({
    idEquipe: 'e1', codigo: 'EQ-01', baseOperacional: 'Base Salvador', processo: 'Obras', tipoEquipe: null, placa: null,
    producao: 0, obrasAtendidas: 0, valorMedioObra: 0, servicos: 0, diasComTurno: 0, diasComProducao: 0, producaoMediaPorDia: 0,
    metaMensal: null, metaPeriodo: null, metaEsperadaAteAgora: null, percentualMeta: null, projecao: null,
    situacao: 'SEM_META', ultimoApontamento: null, producaoDiaria: [], obras: [], ...parcial
  });
  const d: DashboardOperacionalResponse = {
    inicio: '2026-10-01', fim: '2026-10-03', geradoEm: '2026-10-03T12:00:00-03:00', diasUteisPeriodo: 3, diasUteisEncerrados: 2,
    resumo: {
      producao: 900, producaoPeriodoAnterior: 600, variacaoPercentual: 50, obrasAtendidas: 1, valorMedioObra: 900, servicos: 5,
      equipesAtivas: 2, equipesComProducao: 1, equipesComMeta: 1, metaPeriodo: 1500, producaoEquipesComMeta: 900, percentualMeta: 60,
      projecaoEquipesComMeta: 1350, equipesAbaixoDoRitmo: 1
    },
    producaoDiaria: [
      { data: '2026-10-01', obras: 1, servicos: 2, valor: 300, meta: 500 },
      { data: '2026-10-02', obras: 1, servicos: 3, valor: 600, meta: 500 },
      { data: '2026-10-03', obras: 0, servicos: 0, valor: 0, meta: 500 }
    ],
    obras: [obra],
    equipes: [
      equipe({
        idEquipe: 'e1', codigo: 'EQ-01', producao: 900, obrasAtendidas: 1, valorMedioObra: 900, servicos: 5, metaPeriodo: 1500,
        metaEsperadaAteAgora: 1000, percentualMeta: 60, situacao: 'ABAIXO_DO_RITMO', diasComTurno: 3, diasComProducao: 2,
        producaoDiaria: [
          { data: '2026-10-01', obras: 1, servicos: 2, valor: 300, meta: 500 },
          { data: '2026-10-02', obras: 1, servicos: 3, valor: 600, meta: 500 },
          { data: '2026-10-03', obras: 0, servicos: 0, valor: 0, meta: 500 }
        ],
        obras: [obra]
      }),
      equipe({ idEquipe: 'e2', codigo: 'LV-02' })
    ]
  };
  const ctx = { d, filtros: { inicio: d.inicio, fim: d.fim }, service, nomeBase: null, nomeProcesso: null };

  it('resolve cada cartão numa vista com as obras de origem conferidas contra o painel', () => {
    for (const kpi of ['producao', 'meta', 'obras', 'ritmo']) {
      expect(vistaOperacional({ dimension: 'kpi', value: kpi }, ctx)).not.toBeNull();
    }
    const producao = vistaOperacional({ dimension: 'kpi', value: 'producao' }, ctx)!;
    expect(producao.origem!.conferencia).toMatchObject({ quantidadeEsperada: 1, valorEsperado: 900 });
    expect(producao.copiloto!.pergunta).toContain('R$');
  });

  it('o gráfico por dia aprofunda para a análise do dia; o dia navega para os vizinhos e para as equipes', () => {
    const producao = vistaOperacional({ dimension: 'kpi', value: 'producao' }, ctx)!;
    const porDia = producao.graficos.find((g) => g.id === 'producao-por-dia')!;
    expect(porDia.aoSelecionar!(1)).toEqual({ tipo: 'navegar', alvo: { dimension: 'dia', value: '2026-10-02' } });

    const dia = vistaOperacional({ dimension: 'dia', value: '2026-10-02' }, ctx)!;
    expect(dia.anterior!.alvo.value).toBe('2026-10-01');
    expect(dia.proximo!.alvo.value).toBe('2026-10-03');
    expect(dia.graficos[0].aoSelecionar!(0)).toEqual({ tipo: 'navegar', alvo: { dimension: 'equipe', value: 'e1' } });
    expect(dia.links![0].rota).toBe('/obras/boletim');
  });

  it('a análise da obra mostra as equipes que executaram nela', () => {
    const vista = vistaOperacional({ dimension: 'obra', value: 'o1' }, ctx)!;
    expect(vista.titulo).toBe('OB-100 · Rede');
    expect(vista.graficos[0].rotulos).toEqual(['EQ-01']);
    expect(vista.links!.map((l) => l.rotulo)).toEqual(['Abrir a obra', 'Turnos da obra']);
  });

  it('alvo inexistente (link antigo) não abre análise', () => {
    expect(vistaOperacional({ dimension: 'dia', value: '2025-01-01' }, ctx)).toBeNull();
    expect(vistaOperacional({ dimension: 'equipe', value: 'x' }, ctx)).toBeNull();
    expect(vistaOperacional({ dimension: 'obra', value: 'x' }, ctx)).toBeNull();
    expect(vistaOperacional({ dimension: 'kpi', value: 'notas' }, ctx)).toBeNull();
  });

  it('a análise de ritmo calcula o necessário por dia útil restante', () => {
    const ritmo = vistaOperacional({ dimension: 'kpi', value: 'ritmo' }, ctx)!;
    expect(ritmo.destaques![0].texto).toContain('EQ-01');
    expect(ritmo.destaques![0].texto).toContain('por dia útil restante');
  });

  it('leva indicadores e gráficos da análise ao contexto do Copiloto', () => {
    const vista = vistaOperacional({ dimension: 'equipe', value: 'e1' }, ctx)!;
    const graficos = graficosDaAnalise(vista);
    expect(graficos[0].id).toBe('analise.equipe:e1.indicadores');
    expect(graficos.length).toBe(1 + vista.graficos.length);
  });
});

describe('Análise · dashboard de turnos', () => {
  const equipe = (parcial: Partial<EquipeTurno>): EquipeTurno => ({
    idEquipe: 'e1', codigo: 'EQ-01', baseOperacional: 'Base Salvador', processo: 'Corte', situacao: 'ENCERRADO', idTurno: 't1',
    quantidadeTurnos: 1, abertura: '2026-10-08T07:00:00-03:00', fechamento: '2026-10-08T16:00:00-03:00', minutosEmTurno: 540,
    abertoDesdeOutroDia: false, abertoPor: 'MARIA SOUZA', placa: 'ABC1D23', membros: ['MARIA SOUZA', 'JOAO LIMA'],
    notas: 2, producao: 300, valorMedioNota: 150, metaDiaria: 500, percentualMetaDiaria: 60,
    primeiroLancamento: null, ultimoLancamento: null, minutosSemLancamento: null, semLancamentoRecente: false,
    lancamentos: [
      { idExecucao: 'x1', dataLancamento: '2026-10-08T08:00:00-03:00', valor: 100, tipoServico: null },
      { idExecucao: 'x2', dataLancamento: '2026-10-08T11:30:00-03:00', valor: 200, tipoServico: null }
    ],
    tiposServico: [],
    ...parcial
  });
  const d: DashboardTurnosResponse = {
    data: '2026-10-08', hoje: false, diaUtil: true, geradoEm: '2026-10-08T23:00:00-03:00', limiteSemLancamentoMinutos: 120,
    resumo: {
      equipesAtivas: 2, emTurno: 0, encerrados: 1, semTurno: 1, semLancamentoRecente: 0, turnosAbertosDeOutroDia: 0,
      notas: 2, producao: 300, equipesComMeta: 1, metaDiaria: 500, producaoEquipesComMeta: 300, percentualMetaDiaria: 60
    },
    tiposServico: [{ idTipoServico: null, codigo: null, descricao: 'Não informado', notas: 2, producao: 300, valorMedioNota: 150, percentualNotas: 100 }],
    equipes: [
      equipe({}),
      equipe({ idEquipe: 'e2', codigo: 'LV-02', situacao: 'SEM_TURNO', idTurno: null, abertura: null, fechamento: null, notas: 0, producao: 0, lancamentos: [], membros: [] })
    ]
  };
  const ctx = { d, filtros: { data: d.data }, service, nomeBase: null, nomeProcesso: null };

  it('cada ponto do acumulado do turno abre o detalhe daquela nota', () => {
    const vista = vistaTurnos({ dimension: 'equipe', value: 'e1' }, ctx)!;
    const acumulado = vista.graficos.find((g) => g.id === 'acumulado-turno')!;
    expect(acumulado.series[0].values).toEqual([100, 300]);
    expect(acumulado.aoSelecionar!(1)).toEqual({ tipo: 'registro', id: 'x2' });
    // Intervalos: abertura→1ª nota e entre notas, em minutos.
    expect(vista.graficos.find((g) => g.id === 'intervalos')!.series[0].values).toEqual([60, 210]);
  });

  it('turno aberto em outro dia não vira intervalo de dias', () => {
    const esquecido = equipe({ abertura: '2026-09-25T10:01:00-03:00', abertoDesdeOutroDia: true });
    const vista = vistaTurnos({ dimension: 'equipe', value: 'e1' }, { ...ctx, d: { ...d, equipes: [esquecido] } })!;
    expect(vista.graficos.find((g) => g.id === 'intervalos')!.series[0].values[0]).toBe(8 * 60);
  });

  it('a produção do dia aprofunda por hora, e a hora filtra as notas carregadas', () => {
    const producao = vistaTurnos({ dimension: 'kpi', value: 'producao' }, ctx)!;
    const acumulado = producao.graficos.find((g) => g.id === 'producao-acumulada-hora')!;
    expect(acumulado.rotulos).toEqual(['08h', '09h', '10h', '11h']);
    expect(acumulado.aoSelecionar!(3)).toEqual({ tipo: 'navegar', alvo: { dimension: 'hora', value: '11' } });

    const hora = vistaTurnos({ dimension: 'hora', value: '11' }, ctx)!;
    expect(hora.origem!.filtroInicial!.aceita(nota({ dataLancamento: '2026-10-08T11:30:00-03:00' }))).toBe(true);
    expect(hora.origem!.filtroInicial!.aceita(nota({ dataLancamento: '2026-10-08T08:00:00-03:00' }))).toBe(false);
    expect(hora.anterior!.alvo.value).toBe('8');
    expect(hora.origem!.conferencia).toMatchObject({ quantidadeEsperada: 1, valorEsperado: 200 });
  });

  it('equipe sem turno abre sem gráficos nem origem', () => {
    const vista = vistaTurnos({ dimension: 'equipe', value: 'e2' }, ctx)!;
    expect(vista.graficos).toEqual([]);
    expect(vista.origem).toBeUndefined();
    expect(vistaTurnos({ dimension: 'kpi', value: 'sem-turno' }, ctx)!.tabelas![0].linhas.length).toBe(1);
  });
});
