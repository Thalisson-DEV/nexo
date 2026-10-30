import { CesDataTableColumn, CesTone } from '@ces/ui';
import { DashboardInspecoesResponse, DashboardSstFilter, RankingItem } from '../../../../core/models/sst.models';
import { ColaboradorInspecionadoItem, EquipeInspecaoItem, GranularidadeDashboard, InspetorMetaItem } from '../../../../core/models/sst-dashboard.models';
import { SstService } from '../../../../core/services/sst.service';
import {
  AlvoAnalise,
  DestaqueAnalise,
  GraficoAnalise,
  LinkAnalise,
  VistaAnalise,
  chaveDoAlvo
} from '../../../../shared/components/dashboard/dashboard-analise.models';
import {
  IntervaloDrill,
  filtrosListagemDesvios,
  filtrosListagemInspecoes,
  intervaloDoPonto,
  queryParamsListagem
} from '../drill/inspecoes-drill.utils';
import {
  alturaBarras,
  diaCivil,
  graficosDasInspecoes,
  graficosDosDesvios,
  nomeOu,
  origemDesvios,
  origemInspecoes,
  pct,
  plural
} from './analise-sst-comum';

/**
 * Análises (drill-down) do dashboard de inspeções. Cada cartão, ponto da série, inspetor, equipe,
 * colaborador e fatia da distribuição vira uma vista com o próprio contexto: o que o número significa,
 * os gráficos que o explicam e as inspeções/desvios que o compõem.
 */

export interface ContextoAnaliseInspecoes {
  readonly d: DashboardInspecoesResponse;
  readonly filtros: DashboardSstFilter;
  readonly sst: SstService;
  readonly metaComparavel: boolean;
}

/** Dimensões da distribuição "Onde as inspeções aconteceram" e o filtro de listagem de cada uma. */
export const GRUPOS_INSPECAO = {
  tipo: { rotulo: 'Tipo de inspeção', chave: 'idTipoInspecao', campo: 'porTipoInspecao', desvios: false },
  processo: { rotulo: 'Processo da inspeção', chave: 'idProcesso', campo: 'porProcesso', desvios: true },
  processoInspetor: { rotulo: 'Processo do inspetor', chave: 'idProcessoInspetor', campo: 'porProcessoInspetor', desvios: true },
  base: { rotulo: 'Base operacional', chave: 'idBaseOperacional', campo: 'porBaseOperacional', desvios: true },
  formulario: { rotulo: 'Formulário', chave: 'idFormulario', campo: 'porFormulario', desvios: true }
} as const;
export type GrupoInspecao = keyof typeof GRUPOS_INSPECAO;

const alvo = (dimension: string, value: string): AlvoAnalise => ({ dimension, value });
const navegar = (dimension: string, value: string) => ({ tipo: 'navegar' as const, alvo: alvo(dimension, value) });
const DIMENSAO_GRANULARIDADE: Record<GranularidadeDashboard, string> = { DIA: 'dia', MES: 'mes', ANO: 'ano' };
const GRANULARIDADE_DIMENSAO: Record<string, GranularidadeDashboard> = { dia: 'DIA', mes: 'MES', ano: 'ANO' };

function tomPercentual(valor: number | null | undefined): CesTone {
  if (valor == null) return 'neutral';
  return valor >= 100 ? 'success' : valor >= 80 ? 'warning' : 'danger';
}

function intervaloDoPainel(d: DashboardInspecoesResponse): IntervaloDrill {
  const de = diaCivil(d.inicio);
  const ate = diaCivil(d.fim);
  const inicio = intervaloDoPonto(de, 'DIA')!;
  const fim = intervaloDoPonto(ate, 'DIA')!;
  return { de, ate, inicio: inicio.inicio, fim: fim.fim };
}

function rotuloIntervalo(i: IntervaloDrill): string {
  const f = (iso: string) => iso.split('-').reverse().join('/');
  return i.de === i.ate ? f(i.de) : `${f(i.de)} a ${f(i.ate)}`;
}

function listagem(c: ContextoAnaliseInspecoes, intervalo: IntervaloDrill, extra: Record<string, string> = {}): LinkAnalise {
  return { rotulo: 'Ver na listagem de inspeções', icone: 'pi pi-external-link', rota: '/sst/inspecoes', queryParams: queryParamsListagem(c.filtros, intervalo, extra) };
}

/** Série do painel (realizado, meta comparável e desvios), clicável para o período. */
function graficoSerie(c: ContextoAnaliseInspecoes, opcoes: { desvios?: boolean; acumulado?: boolean } = {}): GraficoAnalise {
  const { d } = c;
  const comMeta = c.metaComparavel && d.meta.possuiMeta;
  let realizado = 0;
  let meta = 0;
  const series = opcoes.acumulado
    ? [
        { name: 'Realizado acumulado', values: d.serieTemporal.map((p) => (realizado += p.realizado)) },
        ...(comMeta ? [{ name: 'Meta acumulada', values: d.serieTemporal.map((p) => Math.round((meta += p.meta) * 10) / 10) }] : [])
      ]
    : [
        // A meta rateada por intervalo vira degrau minúsculo em barras; ela aparece na leitura acumulada.
        { name: 'Realizado', values: d.serieTemporal.map((p) => p.realizado) },
        ...(opcoes.desvios ? [{ name: 'Desvios', values: d.serieTemporal.map((p) => p.desvios) }] : [])
      ];
  return {
    id: opcoes.acumulado ? 'serie-acumulada' : 'serie',
    titulo: opcoes.acumulado ? 'Realizado × meta acumulados' : 'Inspeções ao longo do período',
    descricao: 'Clique num ponto para analisar aquele período.',
    tipo: opcoes.acumulado ? 'line' : 'bar',
    rotulos: d.serieTemporal.map((p) => p.rotulo),
    series,
    formato: 'integer',
    altura: 250,
    inteiro: true,
    aoSelecionar: (i) => navegar(DIMENSAO_GRANULARIDADE[d.granularidade], d.serieTemporal[i].inicio),
    rotuloSelecao: 'Analisar período'
  };
}

function graficoRanking(id: string, titulo: string, itens: readonly RankingItem[], grupo: GrupoInspecao, tipo: 'doughnut' | 'bar-horizontal' = 'bar-horizontal'): GraficoAnalise {
  const lista = itens.slice(0, 15);
  return {
    id,
    titulo,
    descricao: 'Clique para analisar o grupo.',
    tipo,
    rotulos: lista.map((x) => nomeOu(x.nome, 'Não informado')),
    series: [{ name: 'Inspeções', values: lista.map((x) => x.total) }],
    formato: 'integer',
    altura: tipo === 'doughnut' ? 240 : alturaBarras(lista.length),
    aoSelecionar: (i) => navegar('grupo', `${grupo}~${lista[i].id}`),
    rotuloSelecao: 'Analisar'
  };
}

const colunasInspetores: CesDataTableColumn<InspetorMetaItem>[] = [
  { key: 'nomeInspetor', header: 'Inspetor', priority: 1, value: (i) => nomeOu(i.nomeInspetor) },
  { key: 'realizado', header: 'Realizadas', type: 'integer', priority: 1 },
  { key: 'meta', header: 'Meta', type: 'number', priority: 2 },
  { key: 'percentualAtingido', header: '% da meta', type: 'percent', priority: 1 },
  { key: 'desvios', header: 'Desvios apontados', type: 'integer', priority: 2 },
  { key: 'percentualDoTotal', header: '% do total', type: 'percent', priority: 3 }
];

function tabelaInspetores(d: DashboardInspecoesResponse) {
  return {
    id: 'inspetores',
    titulo: 'Inspetores',
    icone: 'pi pi-users',
    descricao: 'Clique num inspetor para ver o histórico dele no recorte.',
    colunas: colunasInspetores,
    linhas: d.porInspetor,
    chaveLinha: (i: InspetorMetaItem) => i.idInspetor,
    rotuloLinha: (i: InspetorMetaItem) => nomeOu(i.nomeInspetor),
    aoAtivar: (i: InspetorMetaItem) => navegar('inspetor', i.idInspetor),
    rotuloAcao: 'Analisar',
    nomeArquivo: 'inspetores-recorte'
  };
}

const colunasEquipes: CesDataTableColumn<EquipeInspecaoItem>[] = [
  { key: 'nomeEquipe', header: 'Equipe', priority: 1, value: (e) => nomeOu(e.nomeEquipe) },
  { key: 'inspecoes', header: 'Inspeções', type: 'integer', priority: 1 },
  { key: 'desvios', header: 'Desvios', type: 'integer', priority: 2 },
  { key: 'percentualSemDesvio', header: '% sem desvio', type: 'percent', priority: 1 },
  { key: 'diasSemInspecao', header: 'Dias sem inspeção', type: 'integer', priority: 2 }
];

const colunasColaboradores: CesDataTableColumn<ColaboradorInspecionadoItem>[] = [
  { key: 'nomeColaborador', header: 'Colaborador', priority: 1, value: (c) => nomeOu(c.nomeColaborador) },
  { key: 'vezesInspecionado', header: 'Inspeções', type: 'integer', priority: 1 },
  { key: 'desviosRecebidos', header: 'Desvios', type: 'integer', priority: 1 },
  { key: 'ultimaInspecao', header: 'Última inspeção', priority: 2, value: (c) => c.ultimaInspecao, display: (c) => (c.ultimaInspecao ? new Date(c.ultimaInspecao).toLocaleDateString('pt-BR') : '—') },
  { key: 'diasDesdeUltimaInspecao', header: 'Dias desde a última', type: 'integer', priority: 2 }
];

export function vistaInspecoes(a: AlvoAnalise, c: ContextoAnaliseInspecoes): VistaAnalise | null {
  switch (a.dimension) {
    case 'kpi':
      switch (a.value) {
        case 'inspecoes': return vistaVolume(a, c);
        case 'conformidade': return vistaConformidade(a, c);
        case 'desvios': return vistaDesvios(a, c);
        case 'meta': return vistaMeta(a, c);
        case 'inspetores': return vistaInspetores(a, c);
        case 'cobertura': return vistaCobertura(a, c);
        default: return null;
      }
    case 'dia':
    case 'mes':
    case 'ano':
      return vistaPeriodo(a, c);
    case 'inspetor': return vistaInspetor(a, c);
    case 'equipe': return vistaEquipe(a, c);
    case 'colaborador': return vistaColaborador(a, c);
    case 'grupo': return vistaGrupo(a, c);
    default: return null;
  }
}

// ------------------------------------------------------------------ cartões

function vistaVolume(a: AlvoAnalise, c: ContextoAnaliseInspecoes): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const intervalo = intervaloDoPainel(d);
  const pico = [...d.serieTemporal].sort((x, y) => y.realizado - x.realizado)[0];
  const esquecidas = d.porEquipe.filter((e) => e.diasSemInspecao > 30).length;
  const destaques: DestaqueAnalise[] = [];
  if (r.totalPeriodoAnterior) {
    destaques.push({ tom: r.variacaoPercentual >= 0 ? 'success' : 'warning', icone: r.variacaoPercentual >= 0 ? 'pi pi-arrow-up-right' : 'pi pi-arrow-down-right', texto: `${pct(r.variacaoPercentual)} contra o período anterior (${r.totalPeriodoAnterior} inspeções).` });
  }
  if (pico && pico.realizado) destaques.push({ tom: 'neutral', icone: 'pi pi-star', texto: `Pico em ${pico.rotulo}, com ${plural(pico.realizado, 'inspeção', 'inspeções')}.` });
  if (esquecidas) destaques.push({ tom: 'warning', icone: 'pi pi-eye-slash', texto: `${plural(esquecidas, 'equipe está', 'equipes estão')} há mais de 30 dias sem inspeção — "sem desvio" nelas pode ser só falta de amostra.` });

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Inspeções realizadas',
    titulo: `${plural(r.totalInspecoes, 'inspeção', 'inspeções')} em ${rotuloIntervalo(intervalo)}`,
    descricao: 'Volume, quem executou, onde e com qual resultado.',
    kpis: [
      { rotulo: 'Inspeções', valor: r.totalInspecoes.toLocaleString('pt-BR'), tom: 'brand', icone: 'pi pi-clipboard', detalhe: r.totalPeriodoAnterior ? `${pct(r.variacaoPercentual)} vs. anterior` : 'sem período anterior' },
      { rotulo: 'Por dia', valor: r.mediaInspecoesPorDia.toLocaleString('pt-BR', { maximumFractionDigits: 1 }), icone: 'pi pi-calendar' },
      { rotulo: 'Inspetores ativos', valor: String(r.inspetoresAtivos), icone: 'pi pi-users', detalhe: `${r.mediaInspecoesPorInspetor.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} por inspetor` },
      { rotulo: 'Com desvio', valor: pct(100 - r.percentualSemDesvio), icone: 'pi pi-exclamation-triangle', tom: r.inspecoesComDesvio ? 'warning' : 'success', detalhe: plural(r.inspecoesComDesvio, 'inspeção', 'inspeções') },
      { rotulo: 'Colaboradores alcançados', valor: String(r.colaboradoresInspecionados), icone: 'pi pi-id-card' }
    ],
    destaques,
    graficos: [
      graficoSerie(c, { desvios: true }),
      graficoRanking('por-tipo', 'Por tipo de inspeção', d.porTipoInspecao, 'tipo'),
      graficoRanking('por-base', 'Por base operacional', d.porBaseOperacional, 'base', 'doughnut'),
      graficoRanking('por-formulario', 'Por formulário', d.porFormulario, 'formulario')
    ],
    tabelas: [tabelaInspetores(d)],
    origem: origemInspecoes({
      sst: c.sst,
      filtros: filtrosListagemInspecoes(c.filtros, intervalo),
      quantidadeEsperada: r.totalInspecoes,
      derivados: (l) => graficosDasInspecoes(l, { dia: false }),
      verTudo: listagem(c, intervalo),
      nomeArquivo: `inspecoes-${intervalo.de}-a-${intervalo.ate}`
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'totalInspecoes', rotulo: 'Inspeções realizadas' },
      pergunta: `Foram ${r.totalInspecoes} inspeções em ${rotuloIntervalo(intervalo)} (${pct(r.variacaoPercentual)} vs. período anterior). O volume está bem distribuído entre inspetores, bases e tipos? Onde a cobertura está fraca?`
    }
  };
}

function vistaConformidade(a: AlvoAnalise, c: ContextoAnaliseInspecoes): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const intervalo = intervaloDoPainel(d);
  const equipes = [...d.porEquipe].filter((e) => e.inspecoes > 0).sort((x, y) => x.percentualSemDesvio - y.percentualSemDesvio);
  const pior = equipes[0];
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Inspeções sem desvio',
    titulo: `${pct(r.percentualSemDesvio)} das inspeções sem desvio`,
    descricao: `${rotuloIntervalo(intervalo)} · ${r.inspecoesSemDesvio} de ${r.totalInspecoes} inspeções`,
    kpis: [
      { rotulo: 'Sem desvio', valor: pct(r.percentualSemDesvio), tom: r.percentualSemDesvio >= 90 ? 'success' : r.percentualSemDesvio >= 70 ? 'warning' : 'danger', icone: 'pi pi-shield' },
      { rotulo: 'Inspeções com desvio', valor: String(r.inspecoesComDesvio), icone: 'pi pi-exclamation-triangle' },
      { rotulo: 'Desvios', valor: String(r.totalDesvios), icone: 'pi pi-flag', detalhe: `${r.mediaDesviosPorInspecao.toLocaleString('pt-BR', { maximumFractionDigits: 2 })} por inspeção` },
      { rotulo: 'Colaboradores com desvio', valor: String(d.colaboradores.colaboradoresComDesvio), icone: 'pi pi-user-minus', detalhe: `${d.colaboradores.colaboradoresReincidentes} reincidentes` }
    ],
    destaques: pior && pior.percentualSemDesvio < r.percentualSemDesvio
      ? [{ tom: 'warning', icone: 'pi pi-arrow-down-right', texto: `${nomeOu(pior.nomeEquipe)} tem o pior resultado: ${pct(pior.percentualSemDesvio)} sem desvio em ${plural(pior.inspecoes, 'inspeção', 'inspeções')}.` }]
      : [],
    graficos: [
      {
        id: 'conformidade-por-equipe',
        titulo: '% sem desvio por equipe',
        descricao: 'Da pior para a melhor. Clique para analisar a equipe.',
        tipo: 'bar-horizontal',
        rotulos: equipes.map((e) => nomeOu(e.nomeEquipe)),
        series: [{ name: '% sem desvio', values: equipes.map((e) => e.percentualSemDesvio) }],
        formato: 'number',
        altura: alturaBarras(equipes.length),
        aoSelecionar: (i) => navegar('equipe', equipes[i].idEquipe),
        rotuloSelecao: 'Analisar equipe'
      },
      graficoSerie(c, { desvios: true })
    ],
    origem: origemInspecoes({
      sst: c.sst,
      filtros: { ...filtrosListagemInspecoes(c.filtros, intervalo), apenasComDesvios: true },
      titulo: 'Inspeções com desvio',
      quantidadeEsperada: r.inspecoesComDesvio,
      derivados: (l) => graficosDasInspecoes(l, { dia: false }).filter((g) => g.id !== 'resultado-inspecoes'),
      verTudo: listagem(c, intervalo),
      nomeArquivo: `inspecoes-com-desvio-${intervalo.de}`
    }),
    origensExtras: [origemDesvios({
      sst: c.sst,
      filtros: filtrosListagemDesvios(c.filtros, intervalo),
      quantidadeEsperada: r.totalDesvios,
      derivados: (l) => graficosDosDesvios(l, 'desvios', { equipe: false }),
      nomeArquivo: `desvios-${intervalo.de}`
    })],
    copiloto: {
      foco: { tipo: 'metrica', id: 'percentualSemDesvio', rotulo: 'Inspeções sem desvio' },
      pergunta: `${pct(r.percentualSemDesvio)} das inspeções não tiveram desvio. Quais equipes, formulários e itens concentram os desvios e o que isso indica sobre o risco?`
    }
  };
}

function vistaDesvios(a: AlvoAnalise, c: ContextoAnaliseInspecoes): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const intervalo = intervaloDoPainel(d);
  const equipes = [...d.porEquipe].filter((e) => e.desvios > 0).sort((x, y) => y.desvios - x.desvios);
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Desvios registrados',
    titulo: `${plural(r.totalDesvios, 'desvio', 'desvios')} em ${plural(r.inspecoesComDesvio, 'inspeção', 'inspeções')}`,
    descricao: rotuloIntervalo(intervalo),
    kpis: [
      { rotulo: 'Desvios', valor: String(r.totalDesvios), tom: r.totalDesvios ? 'warning' : 'success', icone: 'pi pi-exclamation-triangle' },
      { rotulo: 'Por inspeção', valor: r.mediaDesviosPorInspecao.toLocaleString('pt-BR', { maximumFractionDigits: 2 }), icone: 'pi pi-percentage' },
      { rotulo: 'Colaboradores com desvio', valor: String(d.colaboradores.colaboradoresComDesvio), icone: 'pi pi-users', detalhe: pct(d.colaboradores.percentualComDesvio) + ' do efetivo inspecionado' },
      { rotulo: 'Reincidentes', valor: String(d.colaboradores.colaboradoresReincidentes), icone: 'pi pi-replay', tom: d.colaboradores.colaboradoresReincidentes ? 'danger' : 'neutral', detalhe: '2 ou mais desvios' }
    ],
    destaques: c.filtros.idTipoInspecao?.length
      ? [{ tom: 'info', icone: 'pi pi-filter', texto: 'A listagem de desvios não filtra por tipo de inspeção; a origem considera os demais filtros.' }]
      : [],
    graficos: [
      { ...graficoSerie(c), id: 'desvios-periodo', titulo: 'Desvios ao longo do período', series: [{ name: 'Desvios', values: d.serieTemporal.map((p) => p.desvios) }] },
      {
        id: 'desvios-por-equipe',
        titulo: 'Desvios por equipe',
        descricao: 'Clique para analisar a equipe.',
        tipo: 'bar-horizontal',
        rotulos: equipes.map((e) => nomeOu(e.nomeEquipe)),
        series: [{ name: 'Desvios', values: equipes.map((e) => e.desvios) }],
        formato: 'integer',
        altura: alturaBarras(equipes.length),
        aoSelecionar: (i) => navegar('equipe', equipes[i].idEquipe),
        rotuloSelecao: 'Analisar equipe'
      }
    ],
    origem: origemDesvios({
      sst: c.sst,
      filtros: filtrosListagemDesvios(c.filtros, intervalo),
      quantidadeEsperada: c.filtros.idTipoInspecao?.length ? undefined : r.totalDesvios,
      derivados: (l) => graficosDosDesvios(l),
      verTudo: { rotulo: 'Ver na listagem de desvios', icone: 'pi pi-external-link', rota: '/sst/desvios' },
      nomeArquivo: `desvios-${intervalo.de}-a-${intervalo.ate}`
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'totalDesvios', rotulo: 'Desvios registrados' },
      pergunta: `Foram ${r.totalDesvios} desvios no período. Quais itens e gravidades predominam, onde se concentram e quais precisam de ação imediata?`
    }
  };
}

function vistaMeta(a: AlvoAnalise, c: ContextoAnaliseInspecoes): VistaAnalise {
  const { d } = c;
  const m = d.meta;
  const comMeta = d.porInspetor.filter((i) => i.meta > 0).sort((x, y) => (x.percentualAtingido ?? 0) - (y.percentualAtingido ?? 0));
  const naMeta = d.porInspetor.filter((i) => i.meta > 0 && i.atingiuMeta).length;
  const situacoes = [
    { rotulo: 'Na meta', total: naMeta },
    { rotulo: 'Abaixo da meta', total: comMeta.length - naMeta },
    { rotulo: 'Sem meta', total: d.porInspetor.length - comMeta.length }
  ].filter((s) => s.total > 0);
  const destaques: DestaqueAnalise[] = [];
  if (!c.metaComparavel) destaques.push({ tom: 'info', icone: 'pi pi-filter', texto: 'Processo, equipe ou formulário filtram só o realizado; o percentual de meta não é comparável neste recorte.' });
  else if (!m.possuiMeta) destaques.push({ tom: 'warning', icone: 'pi pi-flag', texto: 'Nenhuma meta vigente cobre o período consultado.' });
  if (m.colaboradoresSemAcesso) destaques.push({ tom: 'danger', icone: 'pi pi-user-minus', texto: `${plural(m.colaboradoresSemAcesso, 'colaborador com meta não tem', 'colaboradores com meta não têm')} usuário para lançar inspeções — o realizado deles fica em zero.` });
  const pior = comMeta[0];
  if (pior && c.metaComparavel) destaques.push({ tom: 'warning', icone: 'pi pi-arrow-down-right', texto: `${nomeOu(pior.nomeInspetor)} está mais longe da meta: ${pior.realizado} de ${pior.meta.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} (${pct(pior.percentualAtingido)}).` });

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Meta de inspeções',
    titulo: c.metaComparavel && m.possuiMeta ? `${pct(m.percentualAtingido)} da meta do período` : 'Meta não comparável neste recorte',
    descricao: rotuloIntervalo(intervaloDoPainel(d)),
    kpis: [
      { rotulo: 'Atingido', valor: c.metaComparavel && m.possuiMeta ? pct(m.percentualAtingido) : '—', tom: c.metaComparavel && m.possuiMeta ? tomPercentual(m.percentualAtingido) : 'neutral', icone: 'pi pi-flag' },
      { rotulo: 'Realizado', valor: String(m.realizadoPeriodo), icone: 'pi pi-check' },
      { rotulo: 'Meta do período', valor: m.metaPeriodo.toLocaleString('pt-BR', { maximumFractionDigits: 1 }), icone: 'pi pi-bullseye' },
      { rotulo: 'Falta', valor: m.saldoParaMeta.toLocaleString('pt-BR', { maximumFractionDigits: 1 }), icone: 'pi pi-arrow-up' },
      { rotulo: 'Colaboradores na meta', valor: `${m.inspetoresNaMeta} de ${m.colaboradoresComMeta}`, icone: 'pi pi-users', detalhe: pct(m.percentualInspetoresNaMeta) }
    ],
    destaques,
    graficos: [
      {
        id: 'meta-por-inspetor',
        titulo: 'Realizado × meta por inspetor',
        descricao: 'Do mais distante da meta para o mais próximo. Clique para analisar.',
        tipo: 'bar-horizontal',
        rotulos: comMeta.map((i) => nomeOu(i.nomeInspetor)),
        series: [
          { name: 'Realizado', values: comMeta.map((i) => i.realizado) },
          { name: 'Meta', values: comMeta.map((i) => Math.round(i.meta * 10) / 10) }
        ],
        formato: 'number',
        altura: alturaBarras(comMeta.length),
        inteiro: true,
        aoSelecionar: (i) => navegar('inspetor', comMeta[i].idInspetor),
        rotuloSelecao: 'Analisar inspetor'
      },
      {
        id: 'situacao-meta',
        titulo: 'Situação dos inspetores',
        tipo: 'doughnut',
        rotulos: situacoes.map((s) => s.rotulo),
        series: [{ name: 'Inspetores', values: situacoes.map((s) => s.total) }],
        formato: 'integer',
        altura: 230
      },
      graficoSerie(c, { acumulado: true })
    ],
    tabelas: [tabelaInspetores(d)],
    links: [{ rotulo: 'Acompanhamento de metas', icone: 'pi pi-flag', rota: '/sst/metas-inspecao/acompanhamento' }],
    copiloto: {
      foco: { tipo: 'metrica', id: 'metaPercentualAtingido', rotulo: 'Meta de inspeções atingida' },
      pergunta: `A meta de inspeções está em ${pct(m.percentualAtingido)} (${m.realizadoPeriodo} de ${m.metaPeriodo.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}). Quais inspetores estão abaixo e quanto cada um precisa fazer para fechar a meta?`
    }
  };
}

function vistaInspetores(a: AlvoAnalise, c: ContextoAnaliseInspecoes): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const porVolume = [...d.porInspetor].sort((x, y) => y.realizado - x.realizado);
  const porDesvio = [...d.porInspetor].filter((i) => i.desvios > 0).sort((x, y) => y.desvios - x.desvios);
  const top = porVolume[0];
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Inspetores ativos',
    titulo: `${plural(r.inspetoresAtivos, 'inspetor lançou', 'inspetores lançaram')} inspeções`,
    descricao: rotuloIntervalo(intervaloDoPainel(d)),
    kpis: [
      { rotulo: 'Inspetores ativos', valor: String(r.inspetoresAtivos), tom: 'brand', icone: 'pi pi-users' },
      { rotulo: 'Por inspetor', valor: r.mediaInspecoesPorInspetor.toLocaleString('pt-BR', { maximumFractionDigits: 1 }), icone: 'pi pi-chart-bar' },
      { rotulo: 'Mais ativo', valor: top ? nomeOu(top.nomeInspetor) : '—', icone: 'pi pi-star', detalhe: top ? `${top.realizado} inspeções (${pct(top.percentualDoTotal)})` : undefined }
    ],
    destaques: top && top.percentualDoTotal >= 40 && d.porInspetor.length > 2
      ? [{ tom: 'warning', icone: 'pi pi-exclamation-circle', texto: `${nomeOu(top.nomeInspetor)} sozinho faz ${pct(top.percentualDoTotal)} das inspeções — o plano depende de uma pessoa.` }]
      : [],
    graficos: [
      {
        id: 'volume-por-inspetor',
        titulo: 'Inspeções por inspetor',
        descricao: 'Clique para ver o histórico do inspetor.',
        tipo: 'bar-horizontal',
        rotulos: porVolume.map((i) => nomeOu(i.nomeInspetor)),
        series: [{ name: 'Realizadas', values: porVolume.map((i) => i.realizado) }],
        formato: 'integer',
        altura: alturaBarras(porVolume.length),
        aoSelecionar: (i) => navegar('inspetor', porVolume[i].idInspetor),
        rotuloSelecao: 'Analisar inspetor'
      },
      {
        id: 'desvios-por-inspetor',
        titulo: 'Desvios apontados por inspetor',
        descricao: 'Quem aponta muito ou nada pode indicar rigor diferente entre inspetores.',
        tipo: 'bar-horizontal',
        rotulos: porDesvio.map((i) => nomeOu(i.nomeInspetor)),
        series: [{ name: 'Desvios', values: porDesvio.map((i) => i.desvios) }],
        formato: 'integer',
        altura: alturaBarras(porDesvio.length),
        aoSelecionar: (i) => navegar('inspetor', porDesvio[i].idInspetor),
        rotuloSelecao: 'Analisar inspetor'
      }
    ],
    tabelas: [tabelaInspetores(d)],
    copiloto: {
      foco: { tipo: 'tabela', id: 'inspetores', rotulo: 'Produtividade · Inspetores' },
      pergunta: 'Compare os inspetores deste recorte: volume, atingimento de meta e desvios apontados. Há diferença de rigor entre eles?'
    }
  };
}

function vistaCobertura(a: AlvoAnalise, c: ContextoAnaliseInspecoes): VistaAnalise {
  const { d } = c;
  const col = d.colaboradores;
  const faixas = [
    { rotulo: 'Até 7 dias', de: 0, ate: 7 },
    { rotulo: '8 a 15 dias', de: 8, ate: 15 },
    { rotulo: '16 a 30 dias', de: 16, ate: 30 },
    { rotulo: 'Mais de 30 dias', de: 31, ate: Infinity }
  ];
  const maisInspecionados = [...d.porColaborador].sort((x, y) => y.vezesInspecionado - x.vezesInspecionado).slice(0, 15);
  const comDesvio = [...d.porColaborador].filter((x) => x.desviosRecebidos > 0).sort((x, y) => y.desviosRecebidos - x.desviosRecebidos).slice(0, 15);
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Cobertura do efetivo',
    titulo: `${pct(col.percentualCobertura)} do efetivo inspecionado`,
    descricao: `${col.colaboradoresInspecionados} de ${col.colaboradoresAtivos} colaboradores ativos · ${rotuloIntervalo(intervaloDoPainel(d))}`,
    kpis: [
      { rotulo: 'Cobertura', valor: pct(col.percentualCobertura), tom: col.percentualCobertura >= 80 ? 'success' : col.percentualCobertura >= 50 ? 'warning' : 'danger', icone: 'pi pi-users' },
      { rotulo: 'Sem nenhuma inspeção', valor: String(col.colaboradoresSemInspecao), icone: 'pi pi-user-minus', tom: col.colaboradoresSemInspecao ? 'warning' : 'success' },
      { rotulo: 'Com desvio', valor: String(col.colaboradoresComDesvio), icone: 'pi pi-exclamation-triangle', detalhe: pct(col.percentualComDesvio) },
      { rotulo: 'Reincidentes', valor: String(col.colaboradoresReincidentes), icone: 'pi pi-replay', tom: col.colaboradoresReincidentes ? 'danger' : 'neutral' },
      { rotulo: 'Inspeções por colaborador', valor: col.mediaInspecoesPorColaborador.toLocaleString('pt-BR', { maximumFractionDigits: 2 }), icone: 'pi pi-chart-bar' }
    ],
    destaques: col.colaboradoresSemInspecao
      ? [{ tom: 'warning', icone: 'pi pi-eye-slash', texto: `${plural(col.colaboradoresSemInspecao, 'colaborador ativo não foi', 'colaboradores ativos não foram')} inspecionados no período.` }]
      : [{ tom: 'success', icone: 'pi pi-check-circle', texto: 'Todo o efetivo ativo do recorte foi inspecionado ao menos uma vez.' }],
    graficos: [
      {
        id: 'recencia',
        titulo: 'Há quanto tempo foram inspecionados',
        descricao: 'Dias desde a última inspeção de cada colaborador alcançado.',
        tipo: 'bar',
        rotulos: faixas.map((f) => f.rotulo),
        series: [{ name: 'Colaboradores', values: faixas.map((f) => d.porColaborador.filter((x) => x.diasDesdeUltimaInspecao >= f.de && x.diasDesdeUltimaInspecao <= f.ate).length) }],
        formato: 'integer',
        altura: 230
      },
      {
        id: 'mais-desvios',
        titulo: 'Colaboradores com mais desvios',
        descricao: 'Clique para ver as inspeções e desvios do colaborador.',
        tipo: 'bar-horizontal',
        rotulos: comDesvio.map((x) => nomeOu(x.nomeColaborador)),
        series: [{ name: 'Desvios', values: comDesvio.map((x) => x.desviosRecebidos) }],
        formato: 'integer',
        altura: alturaBarras(comDesvio.length),
        aoSelecionar: (i) => navegar('colaborador', comDesvio[i].idColaborador),
        rotuloSelecao: 'Analisar colaborador'
      },
      {
        id: 'mais-inspecionados',
        titulo: 'Mais inspecionados',
        tipo: 'bar-horizontal',
        rotulos: maisInspecionados.map((x) => nomeOu(x.nomeColaborador)),
        series: [{ name: 'Inspeções', values: maisInspecionados.map((x) => x.vezesInspecionado) }],
        formato: 'integer',
        altura: alturaBarras(maisInspecionados.length),
        aoSelecionar: (i) => navegar('colaborador', maisInspecionados[i].idColaborador),
        rotuloSelecao: 'Analisar colaborador'
      }
    ],
    tabelas: [{
      id: 'colaboradores',
      titulo: 'Colaboradores inspecionados',
      icone: 'pi pi-id-card',
      colunas: colunasColaboradores,
      linhas: d.porColaborador,
      chaveLinha: (x: ColaboradorInspecionadoItem) => x.idColaborador,
      rotuloLinha: (x: ColaboradorInspecionadoItem) => nomeOu(x.nomeColaborador),
      aoAtivar: (x: ColaboradorInspecionadoItem) => navegar('colaborador', x.idColaborador),
      rotuloAcao: 'Analisar',
      nomeArquivo: 'colaboradores-inspecionados'
    }],
    copiloto: {
      foco: { tipo: 'secao', id: 'coberturaEfetivo', rotulo: 'Cobertura do efetivo' },
      pergunta: `A cobertura do efetivo está em ${pct(col.percentualCobertura)}, com ${col.colaboradoresSemInspecao} colaboradores sem inspeção e ${col.colaboradoresReincidentes} reincidentes. Onde priorizar as próximas inspeções?`
    }
  };
}

// ------------------------------------------------------------------ pontos e linhas

function vistaPeriodo(a: AlvoAnalise, c: ContextoAnaliseInspecoes): VistaAnalise | null {
  const { d } = c;
  const granularidade = GRANULARIDADE_DIMENSAO[a.dimension];
  const indice = d.serieTemporal.findIndex((p) => p.inicio === a.value);
  const ponto = d.serieTemporal[indice];
  // Link antigo de um dia fora da série atual: abre o dia mesmo assim, sem números do painel.
  const intervalo = intervaloDoPonto(a.value, granularidade);
  if (!intervalo) return null;
  const comMeta = c.metaComparavel && d.meta.possuiMeta && ponto;
  const anterior = indice > 0 ? d.serieTemporal[indice - 1] : null;
  const proximo = indice >= 0 && indice < d.serieTemporal.length - 1 ? d.serieTemporal[indice + 1] : null;
  const eyebrow = granularidade === 'DIA' ? 'Dia' : granularidade === 'MES' ? 'Mês' : 'Ano';
  const destaques: DestaqueAnalise[] = [];
  if (comMeta && granularidade === 'DIA') destaques.push({ tom: 'neutral', icone: 'pi pi-info-circle', texto: 'A meta do dia é a meta do período rateada pelos dias — uma referência proporcional, não um alvo diário.' });
  if (ponto && !ponto.realizado) destaques.push({ tom: 'warning', icone: 'pi pi-inbox', texto: 'Nenhuma inspeção neste período.' });

  return {
    chave: chaveDoAlvo(a),
    eyebrow,
    titulo: ponto?.rotulo ?? rotuloIntervalo(intervalo),
    descricao: rotuloIntervalo(intervalo),
    anterior: anterior ? { alvo: alvo(a.dimension, anterior.inicio), rotulo: anterior.rotulo } : undefined,
    proximo: proximo ? { alvo: alvo(a.dimension, proximo.inicio), rotulo: proximo.rotulo } : undefined,
    kpis: ponto ? [
      { rotulo: 'Inspeções', valor: String(ponto.realizado), tom: 'brand', icone: 'pi pi-clipboard' },
      { rotulo: 'Meta', valor: comMeta ? ponto.meta.toLocaleString('pt-BR', { maximumFractionDigits: 1 }) : '—', icone: 'pi pi-flag', detalhe: comMeta ? `${pct(ponto.percentualAtingido)} atingido` : c.metaComparavel ? 'sem meta vigente' : 'não comparável', tom: comMeta ? tomPercentual(ponto.percentualAtingido) : 'neutral' },
      { rotulo: 'Desvios', valor: String(ponto.desvios), icone: 'pi pi-exclamation-triangle', tom: ponto.desvios ? 'warning' : 'success', detalhe: ponto.realizado ? `${(ponto.desvios / ponto.realizado).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} por inspeção` : undefined }
    ] : [],
    destaques,
    graficos: [],
    origem: origemInspecoes({
      sst: c.sst,
      filtros: filtrosListagemInspecoes(c.filtros, intervalo),
      quantidadeEsperada: ponto?.realizado,
      derivados: (l) => graficosDasInspecoes(l, { dia: granularidade !== 'DIA' }),
      verTudo: listagem(c, intervalo),
      nomeArquivo: `inspecoes-${intervalo.de}`
    }),
    origensExtras: [origemDesvios({
      sst: c.sst,
      filtros: filtrosListagemDesvios(c.filtros, intervalo),
      quantidadeEsperada: c.filtros.idTipoInspecao?.length ? undefined : ponto?.desvios,
      derivados: (l) => graficosDosDesvios(l, 'desvios', { equipe: false }),
      nomeArquivo: `desvios-${intervalo.de}`
    })],
    copiloto: {
      foco: { tipo: 'grafico', id: 'realizadoMeta', rotulo: `Inspeções em ${ponto?.rotulo ?? rotuloIntervalo(intervalo)}` },
      pergunta: `Explique o que aconteceu nas inspeções de ${rotuloIntervalo(intervalo)}: volume frente à meta, quem lançou, desvios críticos e o que merece atenção.`
    }
  };
}

function vistaInspetor(a: AlvoAnalise, c: ContextoAnaliseInspecoes): VistaAnalise | null {
  const { d } = c;
  const i = d.porInspetor.findIndex((x) => x.idInspetor === a.value);
  if (i < 0) return null;
  const x = d.porInspetor[i];
  const intervalo = intervaloDoPainel(d);
  const nome = nomeOu(x.nomeInspetor);
  const temMeta = x.meta > 0 && c.metaComparavel;
  const anterior = d.porInspetor[i - 1];
  const proximo = d.porInspetor[i + 1];
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Inspetor',
    titulo: nome,
    descricao: `${rotuloIntervalo(intervalo)} · ${i + 1}º no ranking de realização`,
    anterior: anterior ? { alvo: alvo('inspetor', anterior.idInspetor), rotulo: nomeOu(anterior.nomeInspetor) } : undefined,
    proximo: proximo ? { alvo: alvo('inspetor', proximo.idInspetor), rotulo: nomeOu(proximo.nomeInspetor) } : undefined,
    kpis: [
      { rotulo: 'Realizadas', valor: String(x.realizado), tom: 'brand', icone: 'pi pi-clipboard', detalhe: `${pct(x.percentualDoTotal)} do total` },
      { rotulo: 'Meta', valor: temMeta ? x.meta.toLocaleString('pt-BR', { maximumFractionDigits: 1 }) : x.meta > 0 ? 'Não comparável' : 'Sem meta', icone: 'pi pi-flag', tom: temMeta ? tomPercentual(x.percentualAtingido) : 'neutral', detalhe: temMeta ? `${pct(x.percentualAtingido)} atingido` : undefined },
      { rotulo: 'Desvios apontados', valor: String(x.desvios), icone: 'pi pi-exclamation-triangle', detalhe: x.realizado ? `${(x.desvios / x.realizado).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} por inspeção` : undefined }
    ],
    destaques: temMeta && !x.atingiuMeta
      ? [{ tom: 'warning', icone: 'pi pi-arrow-up', texto: `Faltam ${Math.max(0, x.meta - x.realizado).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} inspeções para a meta do período.` }]
      : [],
    graficos: [],
    origem: origemInspecoes({
      sst: c.sst,
      filtros: { ...filtrosListagemInspecoes(c.filtros, intervalo), idInspetor: [x.idInspetor] },
      titulo: `Inspeções de ${nome}`,
      quantidadeEsperada: x.realizado,
      derivados: (l) => graficosDasInspecoes(l, { inspetor: false }),
      verTudo: listagem(c, intervalo, { idInspetor: x.idInspetor, inspetor: nome }),
      nomeArquivo: `inspecoes-${nome}`
    }),
    origensExtras: [origemDesvios({
      sst: c.sst,
      filtros: { ...filtrosListagemDesvios(c.filtros, intervalo), idInspetor: [x.idInspetor] },
      titulo: `Desvios apontados por ${nome}`,
      rotuloAba: 'Desvios apontados',
      derivados: (l) => graficosDosDesvios(l, 'desvios', { equipe: false }),
      nomeArquivo: `desvios-${nome}`
    })],
    copiloto: {
      foco: { tipo: 'tabela', id: 'inspetores', rotulo: `Inspetor ${nome}` },
      pergunta: `Explique o desempenho de ${nome} neste recorte: ${x.realizado} inspeções${temMeta ? ` (${pct(x.percentualAtingido)} da meta)` : ''}, ${x.desvios} desvios apontados. Tendência, locais e o que pode explicar o resultado.`
    }
  };
}

function vistaEquipe(a: AlvoAnalise, c: ContextoAnaliseInspecoes): VistaAnalise | null {
  const { d } = c;
  const ordem = [...d.porEquipe].sort((x, y) => y.inspecoes - x.inspecoes);
  const i = ordem.findIndex((x) => x.idEquipe === a.value);
  if (i < 0) return null;
  const e = ordem[i];
  const nome = nomeOu(e.nomeEquipe);
  const intervalo = intervaloDoPainel(d);
  const destaques: DestaqueAnalise[] = [];
  if (e.diasSemInspecao > 30) destaques.push({ tom: 'warning', icone: 'pi pi-eye-slash', texto: `${e.diasSemInspecao} dias sem inspeção: o resultado pode refletir falta de amostra.` });
  if (e.percentualSemDesvio < d.resumo.percentualSemDesvio) destaques.push({ tom: 'warning', icone: 'pi pi-arrow-down-right', texto: `Abaixo da média do recorte (${pct(d.resumo.percentualSemDesvio)} sem desvio).` });
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Equipe',
    titulo: nome,
    descricao: rotuloIntervalo(intervalo),
    anterior: i > 0 ? { alvo: alvo('equipe', ordem[i - 1].idEquipe), rotulo: nomeOu(ordem[i - 1].nomeEquipe) } : undefined,
    proximo: i < ordem.length - 1 ? { alvo: alvo('equipe', ordem[i + 1].idEquipe), rotulo: nomeOu(ordem[i + 1].nomeEquipe) } : undefined,
    kpis: [
      { rotulo: 'Inspeções', valor: String(e.inspecoes), tom: 'brand', icone: 'pi pi-clipboard', detalhe: `${pct(e.percentualDoTotal)} do total` },
      { rotulo: 'Sem desvio', valor: pct(e.percentualSemDesvio), icone: 'pi pi-shield', tom: e.percentualSemDesvio >= d.resumo.percentualSemDesvio ? 'success' : 'warning' },
      { rotulo: 'Desvios', valor: String(e.desvios), icone: 'pi pi-exclamation-triangle', detalhe: `${e.desviosPorInspecao.toLocaleString('pt-BR', { maximumFractionDigits: 2 })} por inspeção` },
      { rotulo: 'Colaboradores', valor: String(e.colaboradoresInspecionados), icone: 'pi pi-users' },
      { rotulo: 'Dias sem inspeção', valor: String(e.diasSemInspecao), icone: 'pi pi-calendar-times', tom: e.diasSemInspecao > 30 ? 'danger' : e.diasSemInspecao > 14 ? 'warning' : 'success' }
    ],
    destaques,
    graficos: [],
    origem: origemInspecoes({
      sst: c.sst,
      filtros: { ...filtrosListagemInspecoes(c.filtros, intervalo), idEquipe: [e.idEquipe] },
      titulo: `Inspeções da equipe ${nome}`,
      quantidadeEsperada: e.inspecoes,
      derivados: (l) => graficosDasInspecoes(l),
      verTudo: listagem(c, intervalo, { idEquipe: e.idEquipe }),
      nomeArquivo: `inspecoes-equipe-${nome}`
    }),
    origensExtras: [origemDesvios({
      sst: c.sst,
      filtros: { ...filtrosListagemDesvios(c.filtros, intervalo), idEquipe: [e.idEquipe] },
      quantidadeEsperada: c.filtros.idTipoInspecao?.length ? undefined : e.desvios,
      derivados: (l) => graficosDosDesvios(l, 'desvios', { equipe: false }),
      nomeArquivo: `desvios-equipe-${nome}`
    })],
    copiloto: {
      foco: { tipo: 'secao', id: `equipe.${nome}`, rotulo: `Equipe ${nome}` },
      pergunta: `Analise a equipe ${nome}: ${e.inspecoes} inspeções, ${pct(e.percentualSemDesvio)} sem desvio e ${e.diasSemInspecao} dias sem inspeção. Quais itens geram desvio e o que priorizar com ela?`
    }
  };
}

function vistaColaborador(a: AlvoAnalise, c: ContextoAnaliseInspecoes): VistaAnalise | null {
  const { d } = c;
  const x = d.porColaborador.find((p) => p.idColaborador === a.value);
  if (!x) return null;
  const nome = nomeOu(x.nomeColaborador);
  const intervalo = intervaloDoPainel(d);
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Colaborador inspecionado',
    titulo: nome,
    descricao: rotuloIntervalo(intervalo),
    kpis: [
      { rotulo: 'Vezes inspecionado', valor: String(x.vezesInspecionado), tom: 'brand', icone: 'pi pi-clipboard' },
      { rotulo: 'Desvios recebidos', valor: String(x.desviosRecebidos), icone: 'pi pi-exclamation-triangle', tom: x.desviosRecebidos >= 2 ? 'danger' : x.desviosRecebidos ? 'warning' : 'success', detalhe: x.desviosRecebidos >= 2 ? 'reincidente' : undefined },
      { rotulo: 'Última inspeção', valor: x.ultimaInspecao ? new Date(x.ultimaInspecao).toLocaleDateString('pt-BR') : '—', icone: 'pi pi-calendar', detalhe: `há ${plural(x.diasDesdeUltimaInspecao, 'dia', 'dias')}` }
    ],
    graficos: [],
    origem: origemInspecoes({
      sst: c.sst,
      filtros: { ...filtrosListagemInspecoes(c.filtros, intervalo), idColaborador: [x.idColaborador] },
      titulo: `Inspeções em que ${nome} foi inspecionado`,
      quantidadeEsperada: x.vezesInspecionado,
      derivados: (l) => graficosDasInspecoes(l, { dia: false }),
      nomeArquivo: `inspecoes-${nome}`
    }),
    origensExtras: [origemDesvios({
      sst: c.sst,
      filtros: { ...filtrosListagemDesvios(c.filtros, intervalo), idColaborador: [x.idColaborador] },
      titulo: `Desvios envolvendo ${nome}`,
      quantidadeEsperada: c.filtros.idTipoInspecao?.length ? undefined : x.desviosRecebidos,
      derivados: (l) => graficosDosDesvios(l, 'desvios', { equipe: false }),
      nomeArquivo: `desvios-${nome}`
    })],
    copiloto: {
      foco: { tipo: 'secao', id: `colaborador.${x.idColaborador}`, rotulo: `Colaborador ${nome}` },
      pergunta: `${nome} foi inspecionado ${x.vezesInspecionado} vezes e recebeu ${x.desviosRecebidos} desvios no período. Há padrão nos desvios e que ação de orientação faz sentido?`
    }
  };
}

function vistaGrupo(a: AlvoAnalise, c: ContextoAnaliseInspecoes): VistaAnalise | null {
  const { d } = c;
  const [dim, id] = a.value.split('~') as [GrupoInspecao, string];
  const grupo = GRUPOS_INSPECAO[dim];
  if (!grupo || !id) return null;
  const itens = d[grupo.campo] as RankingItem[];
  const i = itens.findIndex((x) => x.id === id);
  if (i < 0) return null;
  const item = itens[i];
  const nome = nomeOu(item.nome, 'Não informado');
  const intervalo = intervaloDoPainel(d);
  const filtrosInspecao = { ...filtrosListagemInspecoes(c.filtros, intervalo), [grupo.chave]: [id] };
  return {
    chave: chaveDoAlvo(a),
    eyebrow: grupo.rotulo,
    titulo: nome,
    descricao: rotuloIntervalo(intervalo),
    anterior: i > 0 ? { alvo: alvo('grupo', `${dim}~${itens[i - 1].id}`), rotulo: nomeOu(itens[i - 1].nome, 'Não informado') } : undefined,
    proximo: i < itens.length - 1 ? { alvo: alvo('grupo', `${dim}~${itens[i + 1].id}`), rotulo: nomeOu(itens[i + 1].nome, 'Não informado') } : undefined,
    kpis: [
      { rotulo: 'Inspeções', valor: String(item.total), tom: 'brand', icone: 'pi pi-clipboard' },
      { rotulo: 'Fatia do total', valor: pct(item.percentual), icone: 'pi pi-chart-pie' },
      { rotulo: 'Posição', valor: `${i + 1}º de ${itens.length}`, icone: 'pi pi-sort-amount-down' }
    ],
    graficos: [],
    origem: origemInspecoes({
      sst: c.sst,
      filtros: filtrosInspecao,
      quantidadeEsperada: item.total,
      derivados: (l) => graficosDasInspecoes(l),
      verTudo: listagem(c, intervalo, { [grupo.chave]: id }),
      nomeArquivo: `inspecoes-${nome}`
    }),
    origensExtras: grupo.desvios ? [origemDesvios({
      sst: c.sst,
      filtros: { ...filtrosListagemDesvios(c.filtros, intervalo), [grupo.chave]: [id] },
      derivados: (l) => graficosDosDesvios(l),
      nomeArquivo: `desvios-${nome}`
    })] : [],
    copiloto: {
      foco: { tipo: 'grafico', id: `distribuicao.${grupo.campo}`, rotulo: `${grupo.rotulo}: ${nome}` },
      pergunta: `${grupo.rotulo} "${nome}" teve ${item.total} inspeções (${pct(item.percentual)} do total). Como é o resultado dessas inspeções e o que se destaca nos desvios?`
    }
  };
}
