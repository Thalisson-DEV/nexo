import { CesTone } from '@ces/ui';
import { DesvioFilter } from '../../../../core/models/desvio.models';
import { DashboardDesviosResponse, DashboardSstFilter, GRAVIDADE_LABELS, GravidadeDesvio } from '../../../../core/models/sst.models';
import { ExposicaoNormalizadaItem, FaixaBacklog, GranularidadeDashboard, PendenciaAgrupadaResponse } from '../../../../core/models/sst-dashboard.models';
import { SstService } from '../../../../core/services/sst.service';
import { temSelecao } from '../../../../core/utils/http-params.util';
import {
  AlvoAnalise,
  DestaqueAnalise,
  GraficoAnalise,
  LinkAnalise,
  VistaAnalise,
  chaveDoAlvo
} from '../../../../shared/components/dashboard/dashboard-analise.models';
import { LeituraGerencial, VereditoGerencial } from '../../../../shared/components/dashboard/dashboard-visao-gerencial.component';
import { IntervaloDrill, filtrosListagemDesvios, filtrosListagemInspecoes, intervaloDoPonto } from '../drill/inspecoes-drill.utils';
import { toPlainDashboardLanguage } from '../sst-dashboard-insights';
import {
  LinhaDesvio,
  ORDEM_GRAVIDADE,
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
import { VisaoGerencialSst } from './visao-inspecoes';

/**
 * Análises (drill-down) do dashboard de desvios: cada indicador, ponto da tendência, gravidade, faixa
 * de idade das pendências, agrupamento (processo, base, equipe) e item do checklist vira uma vista com
 * os desvios que o compõem e os gráficos que explicam onde, quando e com que gravidade acontecem.
 */

export interface ContextoAnaliseDesvios {
  readonly d: DashboardDesviosResponse;
  readonly filtros: DashboardSstFilter;
  readonly sst: SstService;
}

export const GRUPOS_DESVIO = {
  processo: { rotulo: 'Processo da inspeção', chave: 'idProcesso', pendencias: 'pendenciasPorProcesso', exposicao: 'exposicaoPorProcesso' },
  processoInspetor: { rotulo: 'Processo do inspetor', chave: 'idProcessoInspetor', pendencias: 'pendenciasPorProcessoInspetor', exposicao: 'exposicaoPorProcessoInspetor' },
  base: { rotulo: 'Base operacional', chave: 'idBaseOperacional', pendencias: 'pendenciasPorBaseOperacional', exposicao: 'exposicaoPorBaseOperacional' },
  equipe: { rotulo: 'Equipe', chave: 'idEquipe', pendencias: 'pendenciasPorEquipe', exposicao: 'exposicaoPorEquipe' }
} as const;
export type GrupoDesvio = keyof typeof GRUPOS_DESVIO;

const alvo = (dimension: string, value: string): AlvoAnalise => ({ dimension, value });
const navegar = (dimension: string, value: string) => ({ tipo: 'navegar' as const, alvo: alvo(dimension, value) });
const DIMENSAO_GRANULARIDADE: Record<GranularidadeDashboard, string> = { DIA: 'dia', MES: 'mes', ANO: 'ano' };
const GRANULARIDADE_DIMENSAO: Record<string, GranularidadeDashboard> = { dia: 'DIA', mes: 'MES', ano: 'ANO' };
const TOM_GRAVIDADE: Record<GravidadeDesvio, CesTone> = { BAIXA: 'neutral', MEDIA: 'info', ALTA: 'warning', CRITICA: 'danger' };

function intervaloDoPainel(d: DashboardDesviosResponse): IntervaloDrill {
  const de = diaCivil(d.inicio);
  const ate = diaCivil(d.fim);
  return { de, ate, inicio: intervaloDoPonto(de, 'DIA')!.inicio, fim: intervaloDoPonto(ate, 'DIA')!.fim };
}

function rotuloIntervalo(i: IntervaloDrill): string {
  const f = (iso: string) => iso.split('-').reverse().join('/');
  return i.de === i.ate ? f(i.de) : `${f(i.de)} a ${f(i.ate)}`;
}

/** Recorte do painel → filtros da listagem de desvios, incluindo gravidade e status escolhidos no painel. */
function filtrosDesvio(c: ContextoAnaliseDesvios, intervalo: IntervaloDrill | null): DesvioFilter {
  const base = intervalo ? filtrosListagemDesvios(c.filtros, intervalo) : filtrosListagemDesvios(c.filtros, { de: '', ate: '', inicio: '', fim: '' });
  if (!intervalo) {
    delete base.dataInspecaoDe;
    delete base.dataInspecaoAte;
  }
  return {
    ...base,
    ...(temSelecao(c.filtros.gravidade as string[] | null) ? { gravidade: [...c.filtros.gravidade!] } : {}),
    ...(temSelecao(c.filtros.status as string[] | null) ? { status: [...c.filtros.status!] } : {})
  };
}

/** Pendências "até o fim do período": tudo que seguia aberto, inclusive identificado antes do período. */
function filtrosAteOFim(c: ContextoAnaliseDesvios): DesvioFilter {
  const intervalo = intervaloDoPainel(c.d);
  return { ...filtrosDesvio(c, null), dataInspecaoAte: intervalo.fim, apenasPendentes: true };
}

function listagemDesvios(rotulo = 'Ver na listagem de desvios'): LinkAnalise {
  return { rotulo, icone: 'pi pi-external-link', rota: '/sst/desvios' };
}

function graficoTendencia(c: ContextoAnaliseDesvios): GraficoAnalise {
  const { d } = c;
  return {
    id: 'tendencia',
    titulo: 'Desvios identificados ao longo do período',
    descricao: 'Clique num ponto para analisar aquele período.',
    tipo: 'bar',
    rotulos: d.tendenciaDesvios.map((t) => t.rotulo),
    series: [{ name: 'Desvios', values: d.tendenciaDesvios.map((t) => t.totalDesvios) }],
    formato: 'integer',
    altura: 240,
    inteiro: true,
    aoSelecionar: (i) => navegar(DIMENSAO_GRANULARIDADE[d.granularidade], d.tendenciaDesvios[i].inicio),
    rotuloSelecao: 'Analisar período'
  };
}

function graficoGravidade(d: DashboardDesviosResponse): GraficoAnalise {
  const volumes = d.volumePorGravidade.filter((v) => v.total > 0).sort((a, b) => ORDEM_GRAVIDADE.indexOf(b.gravidade) - ORDEM_GRAVIDADE.indexOf(a.gravidade));
  return {
    id: 'gravidade',
    titulo: 'Por gravidade',
    descricao: 'Clique numa fatia para analisar a gravidade.',
    tipo: 'doughnut',
    rotulos: volumes.map((v) => GRAVIDADE_LABELS[v.gravidade]),
    series: [{ name: 'Desvios', values: volumes.map((v) => v.total) }],
    formato: 'integer',
    altura: 230,
    aoSelecionar: (i) => navegar('gravidade', volumes[i].gravidade),
    rotuloSelecao: 'Analisar gravidade'
  };
}

function graficoExposicao(d: DashboardDesviosResponse, grupo: GrupoDesvio): GraficoAnalise {
  const itens = [...(d[GRUPOS_DESVIO[grupo].exposicao] ?? [])].sort((a, b) => b.desviosPor100Inspecoes - a.desviosPor100Inspecoes).slice(0, 15);
  return {
    id: `exposicao-${grupo}`,
    titulo: `Desvios a cada 100 inspeções · ${GRUPOS_DESVIO[grupo].rotulo.toLowerCase()}`,
    descricao: 'Compara áreas pelo volume inspecionado. Clique para analisar.',
    tipo: 'bar-horizontal',
    rotulos: itens.map((x) => nomeOu(x.nome, 'Não informado')),
    series: [{ name: 'A cada 100 inspeções', values: itens.map((x) => Math.round(x.desviosPor100Inspecoes * 10) / 10) }],
    formato: 'number',
    altura: alturaBarras(itens.length),
    aoSelecionar: (i) => navegar('grupo', `${grupo}~${itens[i].id}`),
    rotuloSelecao: 'Analisar'
  };
}

function graficoItens(d: DashboardDesviosResponse): GraficoAnalise {
  const itens = d.itensCriticosRecorrentes.slice(0, 12);
  return {
    id: 'itens',
    titulo: 'Itens com mais desvios',
    descricao: 'Clique num item para ver onde e com quem aconteceu.',
    tipo: 'bar-horizontal',
    rotulos: itens.map((x) => {
      const nome = nomeOu(x.item, 'Item sem nome');
      return nome.length > 46 ? `${nome.slice(0, 44)}…` : nome;
    }),
    series: [{ name: 'Desvios', values: itens.map((x) => x.reprovacoes) }],
    formato: 'integer',
    altura: alturaBarras(itens.length),
    inteiro: true,
    aoSelecionar: (i) => navegar('item', itens[i].idPergunta),
    rotuloSelecao: 'Analisar item'
  };
}

function graficoBacklog(d: DashboardDesviosResponse): GraficoAnalise {
  const faixas = d.backlogPorIdade;
  return {
    id: 'backlog-idade',
    titulo: 'Pendências por tempo em aberto',
    descricao: 'Barras empilhadas por gravidade. Clique numa faixa para ver as pendências dela.',
    tipo: 'bar-stacked',
    rotulos: faixas.map((f) => rotuloFaixa(f.faixa)),
    series: (['CRITICA', 'ALTA', 'MEDIA', 'BAIXA'] as GravidadeDesvio[]).map((g) => ({ name: GRAVIDADE_LABELS[g], values: faixas.map((f) => f.porGravidade[g] ?? 0) })),
    formato: 'integer',
    altura: 240,
    inteiro: true,
    aoSelecionar: (i) => navegar('faixa', faixas[i].faixa),
    rotuloSelecao: 'Ver pendências'
  };
}

export function rotuloFaixa(faixa: string): string {
  return ({ '0-7': 'Até 7 dias', '8-15': '8 a 15 dias', '16-30': '16 a 30 dias', '31-60': '31 a 60 dias', '>60': 'Mais de 60 dias' } as Record<string, string>)[faixa] ?? faixa;
}

/** Gráfico derivado: em quantos dias os desvios resolvidos foram fechados. */
function graficoTempoResolucao(linhas: readonly LinhaDesvio[]): GraficoAnalise[] {
  const resolvidos = linhas.filter((l) => l.dataResolucao);
  if (!resolvidos.length) return [];
  const faixas = [
    { rotulo: 'No mesmo dia', de: 0, ate: 0 },
    { rotulo: '1 a 7 dias', de: 1, ate: 7 },
    { rotulo: '8 a 15 dias', de: 8, ate: 15 },
    { rotulo: '16 a 30 dias', de: 16, ate: 30 },
    { rotulo: 'Mais de 30 dias', de: 31, ate: Infinity }
  ];
  return [{
    id: 'tempo-resolucao',
    titulo: 'Em quanto tempo foram resolvidos',
    descricao: 'Dias entre a inspeção e a resolução. Clique numa faixa para listar.',
    tipo: 'bar',
    rotulos: faixas.map((f) => f.rotulo),
    series: [{ name: 'Desvios', values: faixas.map((f) => resolvidos.filter((l) => l.idadeDias >= f.de && l.idadeDias <= f.ate).length) }],
    formato: 'integer',
    altura: 220,
    aoSelecionar: (i) => ({
      tipo: 'filtrar',
      filtro: { chave: `resolucao-${i}`, rotulo: `Resolvidos ${faixas[i].rotulo.toLowerCase()}`, aceita: (l: LinhaDesvio) => !!l.dataResolucao && l.idadeDias >= faixas[i].de && l.idadeDias <= faixas[i].ate },
      origem: 'desvios'
    }),
    rotuloSelecao: 'Ver desvios'
  }];
}

function aviso(c: ContextoAnaliseDesvios): DestaqueAnalise[] {
  return temSelecao(c.filtros.idTipoInspecao as string[] | null)
    ? [{ tom: 'info', icone: 'pi pi-filter', texto: 'A listagem de desvios não filtra por tipo de inspeção; a origem considera os demais filtros.' }]
    : [];
}

export function vistaDesvios(a: AlvoAnalise, c: ContextoAnaliseDesvios): VistaAnalise | null {
  switch (a.dimension) {
    case 'kpi':
      switch (a.value) {
        case 'total': return vistaTotal(a, c);
        case 'criticos': return vistaGravidade({ dimension: 'gravidade', value: 'CRITICA' }, c, a);
        case 'inspecoes': return vistaInspecoesComDesvio(a, c);
        case 'resolucao': return vistaResolucao(a, c);
        case 'backlog': return vistaPendentes(a, c, 'backlog');
        case 'vencidos': return vistaPendentes(a, c, 'vencidos');
        case 'pendentes-periodo': return vistaPendentes(a, c, 'pendentes-periodo');
        case 'vencidos-periodo': return vistaPendentes(a, c, 'vencidos-periodo');
        default: return null;
      }
    case 'dia':
    case 'mes':
    case 'ano':
      return vistaPeriodo(a, c);
    case 'gravidade': return vistaGravidade(a, c);
    case 'faixa': return vistaFaixa(a, c);
    case 'grupo': return vistaGrupo(a, c);
    case 'item': return vistaItem(a, c);
    default: return null;
  }
}

// ------------------------------------------------------------------ indicadores

function vistaTotal(a: AlvoAnalise, c: ContextoAnaliseDesvios): VistaAnalise {
  const { d } = c;
  const i = intervaloDoPainel(d);
  const ind = d.indicadores.totalDesvios;
  const pico = [...d.tendenciaDesvios].sort((x, y) => y.totalDesvios - x.totalDesvios)[0];
  const item = d.itensCriticosRecorrentes[0];
  const destaques: DestaqueAnalise[] = [...aviso(c)];
  if (ind.anterior) destaques.push({ tom: ind.variacaoPercentual <= 0 ? 'success' : 'warning', icone: ind.variacaoPercentual <= 0 ? 'pi pi-arrow-down-right' : 'pi pi-arrow-up-right', texto: `${ind.variacaoPercentual > 0 ? '+' : ''}${pct(ind.variacaoPercentual)} contra o período anterior (${ind.anterior}).` });
  if (pico && pico.totalDesvios) destaques.push({ tom: 'neutral', icone: 'pi pi-star', texto: `Maior volume em ${pico.rotulo}: ${plural(pico.totalDesvios, 'desvio', 'desvios')}.` });
  if (item) destaques.push({ tom: 'warning', icone: 'pi pi-list', texto: `Item que mais reprova: "${nomeOu(item.item)}" (${item.reprovacoes} desvios, ${pct(item.taxaReprovacao)} das respostas).` });
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Desvios identificados',
    titulo: `${plural(d.totalDesvios, 'desvio', 'desvios')} em ${rotuloIntervalo(i)}`,
    descricao: 'Quando, com que gravidade, onde e em quais itens os desvios aparecem.',
    kpis: [
      { rotulo: 'Desvios', valor: String(d.totalDesvios), tom: 'brand', icone: 'pi pi-exclamation-triangle' },
      { rotulo: 'Críticos', valor: String(d.totalCriticos), icone: 'pi pi-bolt', tom: d.totalCriticos ? 'danger' : 'success', detalhe: pct(d.percentualCriticos) },
      { rotulo: 'Inspeções com desvio', valor: String(d.totalInspecoesComDesvio), icone: 'pi pi-clipboard', detalhe: `${d.mediaDesviosPorInspecao.toLocaleString('pt-BR', { maximumFractionDigits: 2 })} por inspeção` },
      { rotulo: 'Resolvidos', valor: pct(d.taxaResolucao), icone: 'pi pi-check-circle' },
      { rotulo: 'Colaboradores envolvidos', valor: String(d.totalColaboradoresEnvolvidos), icone: 'pi pi-users', detalhe: `${d.totalColaboradoresReincidentes} reincidentes` }
    ],
    destaques,
    graficos: [graficoTendencia(c), graficoGravidade(d), graficoExposicao(d, 'processo'), graficoItens(d), graficoExposicao(d, 'equipe')],
    origem: origemDesvios({
      sst: c.sst,
      filtros: filtrosDesvio(c, i),
      quantidadeEsperada: temSelecao(c.filtros.idTipoInspecao as string[] | null) ? undefined : d.totalDesvios,
      derivados: (l) => graficosDosDesvios(l, 'desvios', { itens: false }),
      verTudo: listagemDesvios(),
      nomeArquivo: `desvios-${i.de}-a-${i.ate}`
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'totalDesvios', rotulo: 'Desvios identificados' },
      pergunta: `Foram ${d.totalDesvios} desvios no período (${d.totalCriticos} críticos). Onde e em quais itens eles se concentram, como evoluíram e o que priorizar?`
    }
  };
}

function vistaInspecoesComDesvio(a: AlvoAnalise, c: ContextoAnaliseDesvios): VistaAnalise {
  const { d } = c;
  const i = intervaloDoPainel(d);
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Inspeções com desvio',
    titulo: `${plural(d.totalInspecoesComDesvio, 'inspeção encontrou', 'inspeções encontraram')} desvio`,
    descricao: rotuloIntervalo(i),
    kpis: [
      { rotulo: 'Inspeções com desvio', valor: String(d.totalInspecoesComDesvio), tom: 'brand', icone: 'pi pi-clipboard' },
      { rotulo: 'Desvios por inspeção', valor: d.mediaDesviosPorInspecao.toLocaleString('pt-BR', { maximumFractionDigits: 2 }), icone: 'pi pi-percentage' },
      { rotulo: 'Desvios', valor: String(d.totalDesvios), icone: 'pi pi-exclamation-triangle' }
    ],
    graficos: [graficoExposicao(d, 'base'), graficoExposicao(d, 'processoInspetor')],
    origem: origemInspecoes({
      sst: c.sst,
      filtros: { ...filtrosListagemInspecoes(c.filtros, i), apenasComDesvios: true },
      titulo: 'Inspeções com desvio',
      quantidadeEsperada: d.totalInspecoesComDesvio,
      derivados: (l) => graficosDasInspecoes(l).filter((g) => g.id !== 'resultado-inspecoes'),
      verTudo: { rotulo: 'Ver na listagem de inspeções', icone: 'pi pi-external-link', rota: '/sst/inspecoes', queryParams: { desvios: 'true' } },
      nomeArquivo: `inspecoes-com-desvio-${i.de}`
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'inspecoesComDesvio', rotulo: 'Inspeções com desvio' },
      pergunta: `${d.totalInspecoesComDesvio} inspeções encontraram desvio no período. Quais formulários, inspetores e áreas concentram essas inspeções?`
    }
  };
}

function vistaResolucao(a: AlvoAnalise, c: ContextoAnaliseDesvios): VistaAnalise {
  const { d } = c;
  const i = intervaloDoPainel(d);
  const destaques: DestaqueAnalise[] = [];
  if (d.totalResolvidos) destaques.push({ tom: 'info', icone: 'pi pi-clock', texto: `Metade dos resolvidos fechou em até ${d.tempoMedianoResolucaoDias.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dias; 9 em cada 10, em até ${d.tempoP90ResolucaoDias.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dias.` });
  if (d.totalSemPrazo) destaques.push({ tom: 'warning', icone: 'pi pi-calendar', texto: `${plural(d.totalSemPrazo, 'desvio está', 'desvios estão')} sem prazo definido — sem prazo, não há como cobrar.` });
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Tratamento dos desvios',
    titulo: `${pct(d.taxaResolucao)} dos desvios resolvidos`,
    descricao: rotuloIntervalo(i),
    kpis: [
      { rotulo: 'Resolvidos', valor: String(d.totalResolvidos), tom: 'success', icone: 'pi pi-check-circle', detalhe: pct(d.taxaResolucao) },
      { rotulo: 'Em tratamento', valor: String(d.totalEmTratamento), icone: 'pi pi-sync' },
      { rotulo: 'Abertos', valor: String(d.totalAbertos), icone: 'pi pi-inbox' },
      { rotulo: 'Vencidos', valor: String(d.totalVencidos), icone: 'pi pi-calendar-times', tom: d.totalVencidos ? 'danger' : 'success' },
      { rotulo: 'Tempo mediano', valor: d.totalResolvidos ? `${d.tempoMedianoResolucaoDias.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dias` : '—', icone: 'pi pi-stopwatch' }
    ],
    destaques,
    graficos: [{
      id: 'situacao',
      titulo: 'Situação dos desvios do período',
      tipo: 'doughnut',
      rotulos: ['Resolvidos', 'Em tratamento', 'Abertos'],
      series: [{ name: 'Desvios', values: [d.totalResolvidos, d.totalEmTratamento, d.totalAbertos] }],
      formato: 'integer',
      altura: 230,
      aoSelecionar: (k) => ({
        tipo: 'filtrar',
        filtro: { chave: `status-${k}`, rotulo: ['Resolvidos', 'Em tratamento', 'Abertos'][k], aceita: (l: LinhaDesvio) => l.status === (['RESOLVIDO', 'EM_TRATAMENTO', 'ABERTO'] as const)[k] },
        origem: 'desvios'
      }),
      rotuloSelecao: 'Ver desvios'
    }],
    origem: origemDesvios({
      sst: c.sst,
      filtros: filtrosDesvio(c, i),
      quantidadeEsperada: temSelecao(c.filtros.idTipoInspecao as string[] | null) ? undefined : d.totalDesvios,
      derivados: (l) => [...graficoTempoResolucao(l), ...graficosDosDesvios(l, 'desvios', { itens: false }).filter((g) => g.id !== 'desvios-gravidade')],
      verTudo: listagemDesvios(),
      nomeArquivo: `tratamento-desvios-${i.de}`
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'taxaResolucao', rotulo: 'Desvios resolvidos' },
      pergunta: `${pct(d.taxaResolucao)} dos desvios foram resolvidos (mediana de ${d.tempoMedianoResolucaoDias} dias). Onde o tratamento demora mais e quais vencidos cobrar primeiro?`
    }
  };
}

type TipoPendencia = 'backlog' | 'vencidos' | 'pendentes-periodo' | 'vencidos-periodo';

function vistaPendentes(a: AlvoAnalise, c: ContextoAnaliseDesvios, tipo: TipoPendencia): VistaAnalise {
  const { d } = c;
  const i = intervaloDoPainel(d);
  const ateOFim = tipo === 'backlog' || tipo === 'vencidos';
  const vencidos = tipo === 'vencidos' || tipo === 'vencidos-periodo';
  const total = { backlog: d.indicadores.backlog.valor, vencidos: d.indicadores.vencidos.valor, 'pendentes-periodo': d.totalPendentes, 'vencidos-periodo': d.totalVencidos }[tipo];
  const titulo = {
    backlog: 'Total pendente até o fim do período',
    vencidos: 'Total vencido até o fim do período',
    'pendentes-periodo': 'Pendências identificadas no período',
    'vencidos-periodo': 'Pendências vencidas do período'
  }[tipo];
  const filtros: DesvioFilter = ateOFim
    ? { ...filtrosAteOFim(c), ...(vencidos ? { apenasVencidos: true } : {}) }
    : { ...filtrosDesvio(c, i), apenasPendentes: true, ...(vencidos ? { apenasVencidos: true } : {}) };
  const pendencias = [...d.pendenciasPorEquipe].sort((x, y) => (vencidos ? y.totalVencidos - x.totalVencidos : y.totalPendentes - x.totalPendentes)).filter((p) => (vencidos ? p.totalVencidos : p.totalPendentes) > 0).slice(0, 15);
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Operação pendente',
    titulo: `${titulo}: ${total}`,
    descricao: ateOFim
      ? `Tudo que seguia ${vencidos ? 'vencido' : 'pendente'} em ${i.ate.split('-').reverse().join('/')}, inclusive desvios identificados antes do período.`
      : rotuloIntervalo(i),
    kpis: [
      { rotulo: vencidos ? 'Vencidos' : 'Pendentes', valor: String(total), tom: vencidos ? 'danger' : 'warning', icone: vencidos ? 'pi pi-calendar-times' : 'pi pi-inbox' },
      { rotulo: 'Abertos', valor: String(d.totalAbertos), icone: 'pi pi-inbox', detalhe: 'identificados no período' },
      { rotulo: 'Em tratamento', valor: String(d.totalEmTratamento), icone: 'pi pi-sync', detalhe: 'identificados no período' },
      { rotulo: 'Sem prazo', valor: String(d.totalSemPrazo), icone: 'pi pi-calendar', tom: d.totalSemPrazo ? 'warning' : 'neutral' }
    ],
    destaques: [
      ...aviso(c),
      ...(d.indicadores[ateOFim ? (vencidos ? 'vencidos' : 'backlog') : 'backlog'].anterior
        ? [{ tom: 'neutral' as const, icone: 'pi pi-history', texto: `No fim do período anterior eram ${d.indicadores[vencidos ? 'vencidos' : 'backlog'].anterior}.` }]
        : [])
    ],
    graficos: [
      graficoBacklog(d),
      {
        id: 'pendencias-por-equipe',
        titulo: vencidos ? 'Vencidos por equipe' : 'Pendentes por equipe',
        descricao: 'Clique para analisar a equipe.',
        tipo: 'bar-horizontal',
        rotulos: pendencias.map((p) => nomeOu(p.nome, 'Não informado')),
        series: [{ name: vencidos ? 'Vencidos' : 'Pendentes', values: pendencias.map((p) => (vencidos ? p.totalVencidos : p.totalPendentes)) }],
        formato: 'integer',
        altura: alturaBarras(pendencias.length),
        aoSelecionar: (k) => navegar('grupo', `equipe~${pendencias[k].id}`),
        rotuloSelecao: 'Analisar equipe'
      }
    ],
    origem: origemDesvios({
      sst: c.sst,
      filtros,
      titulo: vencidos ? 'Desvios vencidos' : 'Desvios pendentes',
      quantidadeEsperada: temSelecao(c.filtros.idTipoInspecao as string[] | null) ? undefined : total,
      derivados: (l) => graficosDosDesvios(l, 'desvios').filter((g) => g.id !== 'desvios-tratamento'),
      verTudo: listagemDesvios(),
      nomeArquivo: `desvios-${tipo}`
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: ateOFim ? (vencidos ? 'vencidos' : 'backlog') : (vencidos ? 'totalVencidos' : 'totalPendentes'), rotulo: titulo },
      pergunta: `${titulo}: ${total}. Quais pendências são mais antigas e graves, quem são os responsáveis e o que cobrar primeiro?`
    }
  };
}

// ------------------------------------------------------------------ pontos, fatias e linhas

function vistaPeriodo(a: AlvoAnalise, c: ContextoAnaliseDesvios): VistaAnalise | null {
  const { d } = c;
  const intervalo = intervaloDoPonto(a.value, GRANULARIDADE_DIMENSAO[a.dimension]);
  if (!intervalo) return null;
  const idx = d.tendenciaDesvios.findIndex((t) => t.inicio === a.value);
  const ponto = d.tendenciaDesvios[idx];
  const anterior = idx > 0 ? d.tendenciaDesvios[idx - 1] : null;
  const proximo = idx >= 0 && idx < d.tendenciaDesvios.length - 1 ? d.tendenciaDesvios[idx + 1] : null;
  return {
    chave: chaveDoAlvo(a),
    eyebrow: a.dimension === 'dia' ? 'Dia' : a.dimension === 'mes' ? 'Mês' : 'Ano',
    titulo: ponto?.rotulo ?? rotuloIntervalo(intervalo),
    descricao: rotuloIntervalo(intervalo),
    anterior: anterior ? { alvo: alvo(a.dimension, anterior.inicio), rotulo: anterior.rotulo } : undefined,
    proximo: proximo ? { alvo: alvo(a.dimension, proximo.inicio), rotulo: proximo.rotulo } : undefined,
    kpis: ponto ? [{ rotulo: 'Desvios identificados', valor: String(ponto.totalDesvios), tom: 'brand', icone: 'pi pi-exclamation-triangle' }] : [],
    destaques: aviso(c),
    graficos: [],
    origem: origemDesvios({
      sst: c.sst,
      filtros: filtrosDesvio(c, intervalo),
      quantidadeEsperada: temSelecao(c.filtros.idTipoInspecao as string[] | null) ? undefined : ponto?.totalDesvios,
      derivados: (l) => graficosDosDesvios(l),
      verTudo: listagemDesvios(),
      nomeArquivo: `desvios-${intervalo.de}`
    }),
    copiloto: {
      foco: { tipo: 'grafico', id: 'tendenciaDesvios', rotulo: `Desvios em ${ponto?.rotulo ?? rotuloIntervalo(intervalo)}` },
      pergunta: `O que explica os desvios identificados em ${rotuloIntervalo(intervalo)}? Quais itens, equipes e gravidades predominam?`
    }
  };
}

function vistaGravidade(a: AlvoAnalise, c: ContextoAnaliseDesvios, origemAlvo?: AlvoAnalise): VistaAnalise | null {
  const { d } = c;
  const g = a.value as GravidadeDesvio;
  if (!ORDEM_GRAVIDADE.includes(g)) return null;
  const v = d.volumePorGravidade.find((x) => x.gravidade === g);
  const i = intervaloDoPainel(d);
  const ordem = ORDEM_GRAVIDADE.slice().reverse();
  const k = ordem.indexOf(g);
  return {
    chave: chaveDoAlvo(origemAlvo ?? a),
    eyebrow: 'Gravidade',
    titulo: `Desvios de gravidade ${GRAVIDADE_LABELS[g].toLowerCase()}`,
    descricao: rotuloIntervalo(i),
    anterior: k > 0 ? { alvo: alvo('gravidade', ordem[k - 1]), rotulo: GRAVIDADE_LABELS[ordem[k - 1]] } : undefined,
    proximo: k < ordem.length - 1 ? { alvo: alvo('gravidade', ordem[k + 1]), rotulo: GRAVIDADE_LABELS[ordem[k + 1]] } : undefined,
    kpis: v ? [
      { rotulo: 'Desvios', valor: String(v.total), tom: TOM_GRAVIDADE[g], icone: g === 'CRITICA' ? 'pi pi-bolt' : 'pi pi-exclamation-triangle' },
      { rotulo: 'Fatia do volume', valor: pct(v.percentual), icone: 'pi pi-chart-pie' },
      { rotulo: 'Período anterior', valor: String(v.anterior), icone: 'pi pi-history', detalhe: v.anterior ? `${v.variacaoPercentual > 0 ? '+' : ''}${pct(v.variacaoPercentual)}` : undefined, tom: v.total > v.anterior ? 'warning' : 'success' }
    ] : [],
    destaques: aviso(c),
    graficos: [],
    origem: origemDesvios({
      sst: c.sst,
      filtros: { ...filtrosDesvio(c, i), gravidade: [g] },
      quantidadeEsperada: temSelecao(c.filtros.idTipoInspecao as string[] | null) ? undefined : v?.total,
      derivados: (l) => graficosDosDesvios(l).filter((x) => x.id !== 'desvios-gravidade'),
      verTudo: listagemDesvios(),
      nomeArquivo: `desvios-${g.toLowerCase()}`
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: g === 'CRITICA' ? 'desviosCriticos' : `gravidade.${g}`, rotulo: `Desvios de gravidade ${GRAVIDADE_LABELS[g].toLowerCase()}` },
      pergunta: `Analise os desvios de gravidade ${GRAVIDADE_LABELS[g].toLowerCase()} do período (${v?.total ?? 0}): quais itens, onde, como está o tratamento e o que é urgente?`
    }
  };
}

function vistaFaixa(a: AlvoAnalise, c: ContextoAnaliseDesvios): VistaAnalise | null {
  const { d } = c;
  const idx = d.backlogPorIdade.findIndex((f) => f.faixa === a.value);
  if (idx < 0) return null;
  const f: FaixaBacklog = d.backlogPorIdade[idx];
  const fim = intervaloDoPainel(d).ate;
  // A idade conta até o fim do período: a faixa vira um intervalo de datas de inspeção.
  const dataMenos = (dias: number) => {
    const [ano, mes, dia] = fim.split('-').map(Number);
    const x = new Date(ano, mes - 1, dia - dias);
    return `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`;
  };
  const ate = intervaloDoPonto(dataMenos(f.idadeMinimaDias), 'DIA')!;
  const de = f.idadeMaximaDias != null ? intervaloDoPonto(dataMenos(f.idadeMaximaDias), 'DIA') : null;
  const vizinho = (k: number) => d.backlogPorIdade[k] ? { alvo: alvo('faixa', d.backlogPorIdade[k].faixa), rotulo: rotuloFaixa(d.backlogPorIdade[k].faixa) } : undefined;
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Pendências por tempo em aberto',
    titulo: `${rotuloFaixa(f.faixa)} em aberto`,
    descricao: `Pendências que, no fim do período, aguardavam tratamento há ${rotuloFaixa(f.faixa).toLowerCase()}.`,
    anterior: vizinho(idx - 1),
    proximo: vizinho(idx + 1),
    kpis: [
      { rotulo: 'Pendências', valor: String(f.total), tom: f.idadeMinimaDias >= 31 ? 'danger' : f.idadeMinimaDias >= 16 ? 'warning' : 'info', icone: 'pi pi-inbox' },
      ...(['CRITICA', 'ALTA', 'MEDIA', 'BAIXA'] as GravidadeDesvio[]).map((g) => ({ rotulo: GRAVIDADE_LABELS[g], valor: String(f.porGravidade[g] ?? 0), tom: (f.porGravidade[g] ? TOM_GRAVIDADE[g] : 'neutral') as CesTone }))
    ],
    destaques: (f.porGravidade.CRITICA || f.porGravidade.ALTA) && f.idadeMinimaDias >= 31
      ? [{ tom: 'danger', icone: 'pi pi-bolt', texto: 'Há pendências graves e antigas nesta faixa: são as primeiras a cobrar.' }]
      : [],
    graficos: [],
    origem: origemDesvios({
      sst: c.sst,
      filtros: { ...filtrosAteOFim(c), dataInspecaoAte: ate.fim, ...(de ? { dataInspecaoDe: de.inicio } : {}) },
      titulo: `Pendências ${rotuloFaixa(f.faixa).toLowerCase()} em aberto`,
      quantidadeEsperada: temSelecao(c.filtros.idTipoInspecao as string[] | null) ? undefined : f.total,
      derivados: (l) => graficosDosDesvios(l).filter((x) => x.id !== 'desvios-tratamento'),
      verTudo: listagemDesvios(),
      nomeArquivo: `pendencias-${f.faixa}`
    }),
    copiloto: {
      foco: { tipo: 'grafico', id: 'backlogPorIdade', rotulo: `Pendências ${rotuloFaixa(f.faixa).toLowerCase()}` },
      pergunta: `Há ${f.total} pendências em aberto há ${rotuloFaixa(f.faixa).toLowerCase()} (${f.porGravidade.CRITICA} críticas, ${f.porGravidade.ALTA} altas). Quem são os responsáveis e por que estão paradas?`
    }
  };
}

function vistaGrupo(a: AlvoAnalise, c: ContextoAnaliseDesvios): VistaAnalise | null {
  const { d } = c;
  const [dim, id] = a.value.split('~') as [GrupoDesvio, string];
  const grupo = GRUPOS_DESVIO[dim];
  if (!grupo || !id) return null;
  const pend = (d[grupo.pendencias] ?? []) as PendenciaAgrupadaResponse[];
  const expo = (d[grupo.exposicao] ?? []) as ExposicaoNormalizadaItem[];
  const p = pend.find((x) => x.id === id);
  const e = expo.find((x) => x.id === id);
  if (!p && !e) return null;
  const nome = nomeOu(p?.nome ?? e?.nome, 'Não informado');
  const i = intervaloDoPainel(d);
  const ordem = expo.length ? expo : pend;
  const k = ordem.findIndex((x) => x.id === id);
  const media = d.totalInspecoesComDesvio && expo.length ? expo.reduce((t, x) => t + x.desvios, 0) / Math.max(1, expo.reduce((t, x) => t + x.inspecoes, 0)) * 100 : null;
  const destaques: DestaqueAnalise[] = [...aviso(c)];
  if (e && media != null) destaques.push({ tom: e.desviosPor100Inspecoes > media ? 'warning' : 'success', icone: 'pi pi-percentage', texto: `${e.desviosPor100Inspecoes.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} desvios a cada 100 inspeções, contra ${media.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} no conjunto.` });
  return {
    chave: chaveDoAlvo(a),
    eyebrow: grupo.rotulo,
    titulo: nome,
    descricao: rotuloIntervalo(i),
    anterior: k > 0 ? { alvo: alvo('grupo', `${dim}~${ordem[k - 1].id}`), rotulo: nomeOu(ordem[k - 1].nome, 'Não informado') } : undefined,
    proximo: k >= 0 && k < ordem.length - 1 ? { alvo: alvo('grupo', `${dim}~${ordem[k + 1].id}`), rotulo: nomeOu(ordem[k + 1].nome, 'Não informado') } : undefined,
    kpis: [
      ...(e ? [
        { rotulo: 'Inspeções', valor: String(e.inspecoes), icone: 'pi pi-clipboard' },
        { rotulo: 'Desvios', valor: String(e.desvios), tom: 'brand' as CesTone, icone: 'pi pi-exclamation-triangle' },
        { rotulo: 'A cada 100 inspeções', valor: e.desviosPor100Inspecoes.toLocaleString('pt-BR', { maximumFractionDigits: 1 }), icone: 'pi pi-percentage' }
      ] : []),
      ...(p ? [
        { rotulo: 'Pendentes', valor: String(p.totalPendentes), icone: 'pi pi-inbox', tom: (p.totalPendentes ? 'warning' : 'success') as CesTone, detalhe: `${p.totalAbertos} abertos · ${p.totalEmTratamento} em tratamento` },
        { rotulo: 'Vencidos', valor: String(p.totalVencidos), icone: 'pi pi-calendar-times', tom: (p.totalVencidos ? 'danger' : 'neutral') as CesTone },
        { rotulo: 'Críticos pendentes', valor: String(p.totalCriticos), icone: 'pi pi-bolt', tom: (p.totalCriticos ? 'danger' : 'neutral') as CesTone }
      ] : [])
    ],
    destaques,
    graficos: [],
    origem: origemDesvios({
      sst: c.sst,
      filtros: { ...filtrosDesvio(c, i), [grupo.chave]: [id] },
      titulo: `Desvios identificados · ${nome}`,
      rotuloAba: 'Origem · desvios',
      quantidadeEsperada: e && !temSelecao(c.filtros.idTipoInspecao as string[] | null) ? e.desvios : undefined,
      derivados: (l) => graficosDosDesvios(l, 'desvios', { equipe: dim !== 'equipe' }),
      verTudo: listagemDesvios(),
      nomeArquivo: `desvios-${nome}`
    }),
    origensExtras: [origemDesvios({
      sst: c.sst,
      id: 'pendentes',
      filtros: { ...filtrosAteOFim(c), [grupo.chave]: [id] },
      titulo: `Pendências de ${nome} até o fim do período`,
      rotuloAba: 'Pendências',
      descricao: 'Tudo que seguia aberto ou em tratamento, inclusive desvios identificados antes do período.',
      nomeArquivo: `pendencias-${nome}`
    })],
    copiloto: {
      foco: { tipo: 'tabela', id: `pendencias.${dim}`, rotulo: `${grupo.rotulo}: ${nome}` },
      pergunta: `Analise os desvios de ${grupo.rotulo.toLowerCase()} "${nome}": ${e ? `${e.desvios} desvios em ${e.inspecoes} inspeções` : ''}${p ? `, ${p.totalPendentes} pendentes e ${p.totalVencidos} vencidos` : ''}. Quais itens se repetem e o que cobrar?`
    }
  };
}

function vistaItem(a: AlvoAnalise, c: ContextoAnaliseDesvios): VistaAnalise | null {
  const { d } = c;
  const k = d.itensCriticosRecorrentes.findIndex((x) => x.idPergunta === a.value);
  if (k < 0) return null;
  const x = d.itensCriticosRecorrentes[k];
  const nome = nomeOu(x.item, 'Item sem nome');
  const i = intervaloDoPainel(d);
  const lista = d.itensCriticosRecorrentes;
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Item do checklist',
    titulo: nome,
    descricao: rotuloIntervalo(i),
    anterior: k > 0 ? { alvo: alvo('item', lista[k - 1].idPergunta), rotulo: nomeOu(lista[k - 1].item) } : undefined,
    proximo: k < lista.length - 1 ? { alvo: alvo('item', lista[k + 1].idPergunta), rotulo: nomeOu(lista[k + 1].item) } : undefined,
    kpis: [
      { rotulo: 'Desvios', valor: String(x.reprovacoes), tom: 'brand', icone: 'pi pi-exclamation-triangle' },
      { rotulo: 'Respostas', valor: String(x.respostas), icone: 'pi pi-comments' },
      { rotulo: 'Com desvio', valor: pct(x.taxaReprovacao), icone: 'pi pi-percentage', tom: x.taxaReprovacao >= 30 ? 'danger' : x.taxaReprovacao >= 10 ? 'warning' : 'neutral' },
      { rotulo: 'Maior gravidade', valor: GRAVIDADE_LABELS[x.maiorGravidade], icone: 'pi pi-bolt', tom: TOM_GRAVIDADE[x.maiorGravidade] },
      { rotulo: 'Acumulado', valor: pct(x.percentualAcumulado), icone: 'pi pi-chart-line', detalhe: 'dos desvios até este item' }
    ],
    destaques: x.variacaoTaxaPercentual
      ? [{ tom: x.variacaoTaxaPercentual > 0 ? 'warning' : 'success', icone: x.variacaoTaxaPercentual > 0 ? 'pi pi-arrow-up-right' : 'pi pi-arrow-down-right', texto: `Taxa de desvio ${x.variacaoTaxaPercentual > 0 ? 'subiu' : 'caiu'} ${pct(Math.abs(x.variacaoTaxaPercentual))} contra o período anterior.` }]
      : [],
    graficos: [],
    origem: origemDesvios({
      sst: c.sst,
      filtros: { ...filtrosDesvio(c, i), idPergunta: [x.idPergunta] },
      titulo: `Desvios no item "${nome.length > 40 ? nome.slice(0, 38) + '…' : nome}"`,
      quantidadeEsperada: temSelecao(c.filtros.idTipoInspecao as string[] | null) ? undefined : x.reprovacoes,
      derivados: (l) => graficosDosDesvios(l, 'desvios', { itens: false }),
      verTudo: listagemDesvios(),
      nomeArquivo: 'desvios-item'
    }),
    copiloto: {
      foco: { tipo: 'tabela', id: 'itensCriticosRecorrentes', rotulo: `Item ${nome}` },
      pergunta: `O item "${nome}" gerou ${x.reprovacoes} desvios (${pct(x.taxaReprovacao)} das respostas). Onde e com quais equipes acontece, e que ação preventiva atacaria a causa?`
    }
  };
}

// ------------------------------------------------------------------ visão gerencial

export function visaoDesvios(d: DashboardDesviosResponse): VisaoGerencialSst {
  const backlog = d.indicadores.backlog;
  const vencidos = d.indicadores.vencidos;
  const graves = d.backlogPorIdade.filter((f) => f.idadeMinimaDias >= 31).reduce((t, f) => t + (f.porGravidade.CRITICA ?? 0) + (f.porGravidade.ALTA ?? 0), 0);
  let veredito: VereditoGerencial;
  if (!d.totalDesvios && !backlog.valor) {
    veredito = { tom: 'success', rotulo: 'Sem pendências', titulo: 'Nenhum desvio no período e nada pendente', texto: 'Confira no painel de inspeções se o volume inspecionado é suficiente para sustentar essa leitura.' };
  } else {
    const tom: CesTone = vencidos.valor > 0 && graves > 0 ? 'danger' : vencidos.valor > 0 || d.totalCriticos > 0 ? 'warning' : 'success';
    veredito = {
      tom,
      rotulo: tom === 'danger' ? 'Agir agora' : tom === 'warning' ? 'Atenção' : 'Sob controle',
      titulo: vencidos.valor ? `${plural(vencidos.valor, 'desvio vencido', 'desvios vencidos')} na fila` : backlog.valor ? `${plural(backlog.valor, 'pendência', 'pendências')} dentro do prazo` : 'Fila de tratamento zerada',
      texto: `${plural(d.totalDesvios, 'desvio identificado', 'desvios identificados')} no período (${d.totalCriticos} críticos), ${pct(d.taxaResolucao)} resolvidos; metade dos resolvidos fechou em até ${d.tempoMedianoResolucaoDias.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dias. ${backlog.valor} pendentes até o fim do período.`,
      progresso: d.totalDesvios ? { valor: d.taxaResolucao, rotulo: `${pct(d.taxaResolucao)} dos desvios do período resolvidos` } : undefined
    };
  }
  const leituras: LeituraGerencial[] = [];
  if (vencidos.valor) leituras.push({ id: 'vencidos', tom: 'danger', icone: 'pi pi-calendar-times', titulo: 'Vencidos até o fim do período', valor: String(vencidos.valor), texto: vencidos.anterior ? `Eram ${vencidos.anterior} no fim do período anterior.` : 'Desvios com prazo estourado aguardando tratamento.', acao: { rotulo: 'Ver os vencidos', alvo: { dimension: 'kpi', value: 'vencidos' } } });
  if (graves) leituras.push({ id: 'graves-antigos', tom: 'danger', icone: 'pi pi-bolt', titulo: 'Graves abertos há 30+ dias', valor: String(graves), texto: 'Críticos e altos que seguem pendentes há mais de um mês.', acao: { rotulo: 'Ver pendências antigas', alvo: { dimension: 'faixa', value: d.backlogPorIdade.find((f) => f.idadeMinimaDias >= 31 && (f.porGravidade.CRITICA || f.porGravidade.ALTA))?.faixa ?? '31-60' } } });
  if (d.totalCriticos) leituras.push({ id: 'criticos', tom: 'warning', icone: 'pi pi-exclamation-triangle', titulo: 'Críticos identificados', valor: String(d.totalCriticos), texto: `${pct(d.percentualCriticos)} dos desvios do período.`, acao: { rotulo: 'Analisar críticos', alvo: { dimension: 'kpi', value: 'criticos' } } });
  const item = d.itensCriticosRecorrentes[0];
  if (item) leituras.push({ id: 'item', tom: 'info', icone: 'pi pi-list', titulo: 'Item que mais reprova', valor: `${item.reprovacoes} desvios`, texto: `"${nomeOu(item.item)}" — ${pct(item.taxaReprovacao)} das respostas com desvio.`, acao: { rotulo: 'Ver onde acontece', alvo: { dimension: 'item', value: item.idPergunta } } });
  const area = [...d.exposicaoPorEquipe].filter((x) => x.inspecoes >= 3).sort((a, b) => b.desviosPor100Inspecoes - a.desviosPor100Inspecoes)[0];
  if (area) leituras.push({ id: 'area', tom: 'warning', icone: 'pi pi-map-marker', titulo: 'Equipe com mais desvios proporcionais', valor: nomeOu(area.nome), texto: `${area.desviosPor100Inspecoes.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} desvios a cada 100 inspeções (${area.inspecoes} inspeções).`, acao: { rotulo: 'Analisar a equipe', alvo: { dimension: 'grupo', value: `equipe~${area.id}` } } });
  if (d.totalSemPrazo) leituras.push({ id: 'sem-prazo', tom: 'warning', icone: 'pi pi-calendar', titulo: 'Sem prazo definido', valor: String(d.totalSemPrazo), texto: 'Desvios sem prazo não podem ser cobrados nem vencem.', acao: { rotulo: 'Ver pendências do período', alvo: { dimension: 'kpi', value: 'pendentes-periodo' } } });
  if (d.totalResolvidos) leituras.push({ id: 'tempo', tom: 'info', icone: 'pi pi-stopwatch', titulo: 'Tempo para resolver', valor: `${d.tempoMedianoResolucaoDias.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dias`, texto: `Mediana; 9 em cada 10 em até ${d.tempoP90ResolucaoDias.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dias.`, acao: { rotulo: 'Analisar tratamento', alvo: { dimension: 'kpi', value: 'resolucao' } } });
  for (const p of d.prioridades.slice(0, 2)) {
    leituras.push({ id: `prioridade-${p.tipo}`, tom: p.severidade === 'CRITICA' ? 'danger' : p.severidade === 'ALTA' ? 'warning' : 'info', icone: p.tipo === 'SEM_PRAZO' ? 'pi pi-calendar' : 'pi pi-exclamation-triangle', titulo: toPlainDashboardLanguage(p.titulo), texto: `${toPlainDashboardLanguage(p.evidencia)} ${toPlainDashboardLanguage(p.acaoSugerida)}`, acao: { rotulo: 'Ver pendências', alvo: { dimension: 'kpi', value: 'backlog' } } });
  }
  return {
    veredito,
    leituras,
    pergunta: `Faça a leitura gerencial dos desvios de segurança deste recorte: ${veredito.titulo.toLowerCase()} (${veredito.texto}) Quais as 3 ações prioritárias e quem cobrar?`
  };
}
