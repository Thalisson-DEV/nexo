import { map } from 'rxjs';
import { CesDataTableColumn, CesTone } from '@ces/ui';
import {
  DashboardEventosSstFilter,
  DashboardEventosSstResponse,
  DiasSemAcidenteItem,
  EventoSstFilter,
  EventoSstResumoResponse,
  GranularidadeDashboard,
  RankingEventoItem,
  STATUS_EVENTO_LABELS,
  StatusEventoSst,
  TIPO_EVENTO_LABELS,
  TipoEventoSst
} from '../../../../core/models/sst-evento.models';
import { SstService } from '../../../../core/services/sst.service';
import {
  AcaoAnalise,
  AlvoAnalise,
  DestaqueAnalise,
  DetalheRegistro,
  FiltroOrigem,
  GraficoAnalise,
  LinkAnalise,
  OrigemAnalise,
  VistaAnalise,
  chaveDoAlvo
} from '../../../../shared/components/dashboard/dashboard-analise.models';
import { LeituraGerencial, VereditoGerencial } from '../../../../shared/components/dashboard/dashboard-visao-gerencial.component';
import { intervaloDoPonto } from '../drill/inspecoes-drill.utils';
import { LIMITE_SST, alturaBarras, contar, diaCivil, diaHora, pct, plural } from './analise-sst-comum';
import { VisaoGerencialSst } from './visao-inspecoes';

/**
 * Análises (drill-down) do dashboard de eventos (acidentalidade): cada indicador, ponto da série, tipo,
 * faixa de tratativa em aberto e item de concentração vira uma vista com os eventos que o compõem.
 * Cancelados ficam fora, como no painel.
 */

export interface ContextoAnaliseEventos {
  readonly d: DashboardEventosSstResponse;
  readonly filtros: DashboardEventosSstFilter;
  readonly sst: SstService;
}

export const DIMENSOES_RANKING_EVENTO = {
  classificacao: { rotulo: 'Classificação', chave: 'idClassificacao', campo: 'porClassificacao' },
  atividade: { rotulo: 'Atividade', chave: 'idAtividade', campo: 'porAtividade' },
  gestor: { rotulo: 'Gestor', chave: 'idGestor', campo: 'porGestor' },
  base: { rotulo: 'Base operacional', chave: 'idBaseOperacional', campo: 'porBaseOperacional' },
  equipe: { rotulo: 'Equipe', chave: 'idEquipe', campo: 'porEquipe' },
  processo: { rotulo: 'Processo', chave: 'idProcesso', campo: 'porProcesso' },
  localLesao: { rotulo: 'Local da lesão', chave: 'idLocalLesao', campo: 'porLocalLesao' }
} as const;
export type DimensaoRankingEvento = keyof typeof DIMENSOES_RANKING_EVENTO;

const VALIDOS: StatusEventoSst[] = ['REGISTRADO', 'EM_TRATATIVA', 'CONCLUIDO'];
const ABERTOS: StatusEventoSst[] = ['REGISTRADO', 'EM_TRATATIVA'];
const alvo = (dimension: string, value: string): AlvoAnalise => ({ dimension, value });
const navegar = (dimension: string, value: string) => ({ tipo: 'navegar' as const, alvo: alvo(dimension, value) });
const DIMENSAO_GRANULARIDADE: Record<GranularidadeDashboard, string> = { DIA: 'dia', MES: 'mes', ANO: 'ano' };
const GRANULARIDADE_DIMENSAO: Record<string, GranularidadeDashboard> = { dia: 'DIA', mes: 'MES', ano: 'ANO' };

// ------------------------------------------------------------------ origem: eventos

export interface LinhaEvento {
  id: string;
  numero: string;
  ocorrencia: string;
  tipo: TipoEventoSst;
  status: StatusEventoSst;
  classificacao: string;
  atividade: string;
  gestor: string;
  base: string;
  equipe: string;
  veiculo: string;
  local: string;
  comLesao: boolean;
  acidentados: number;
  diasPerdidos: number;
  possuiCat: boolean;
  horasAteComunicacao: number;
  comunicacaoAtrasada: boolean;
}

function linhaEvento(e: EventoSstResumoResponse): LinhaEvento {
  return {
    id: e.id,
    numero: e.numeroRegistro,
    ocorrencia: e.dataOcorrencia,
    tipo: e.tipo,
    status: e.status,
    classificacao: e.classificacaoNome ?? '—',
    atividade: e.atividadeNome ?? '—',
    gestor: e.gestorNome ?? '—',
    base: e.baseNome ?? '—',
    equipe: e.equipeCodigo ?? '—',
    veiculo: e.veiculoPlaca ?? '—',
    local: e.localOcorrencia ?? '—',
    comLesao: e.comLesao,
    acidentados: e.quantidadeAcidentados ?? 0,
    diasPerdidos: e.diasPerdidos ?? 0,
    possuiCat: e.possuiCat,
    horasAteComunicacao: e.horasAteComunicacao ?? 0,
    comunicacaoAtrasada: e.comunicacaoAtrasada
  };
}

const COLUNAS_EVENTO: CesDataTableColumn<LinhaEvento>[] = [
  { key: 'ocorrencia', header: 'Ocorrência', priority: 1, width: '8rem', value: (l) => l.ocorrencia, display: (l) => diaHora(l.ocorrencia), searchable: false },
  { key: 'numero', header: 'Registro', priority: 1 },
  { key: 'tipo', header: 'Tipo', priority: 1, value: (l) => TIPO_EVENTO_LABELS[l.tipo] ?? l.tipo },
  { key: 'classificacao', header: 'Classificação', priority: 2 },
  { key: 'base', header: 'Base · equipe', priority: 2, value: (l) => `${l.base} · ${l.equipe}` },
  { key: 'status', header: 'Status', priority: 2, value: (l) => STATUS_EVENTO_LABELS[l.status] ?? l.status },
  { key: 'comLesao', header: 'Lesão', priority: 1, value: (l) => (l.comLesao ? 1 : 0), display: (l) => (l.comLesao ? `Sim (${l.acidentados})` : 'Não') },
  { key: 'diasPerdidos', header: 'Dias perdidos', type: 'integer', priority: 3 },
  { key: 'horasAteComunicacao', header: 'Comunicação', priority: 3, value: (l) => l.horasAteComunicacao, display: (l) => `${l.horasAteComunicacao.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} h${l.comunicacaoAtrasada ? ' · fora do prazo' : ''}` }
];

function detalheEvento(l: LinhaEvento): DetalheRegistro {
  return {
    titulo: `Evento ${l.numero}`,
    subtitulo: `${TIPO_EVENTO_LABELS[l.tipo] ?? l.tipo} · ${diaHora(l.ocorrencia)}`,
    campos: [
      { rotulo: 'Status', valor: STATUS_EVENTO_LABELS[l.status] ?? l.status, icone: 'pi pi-sync' },
      { rotulo: 'Classificação', valor: l.classificacao, icone: 'pi pi-tag' },
      { rotulo: 'Lesão', valor: l.comLesao ? `Sim — ${plural(l.acidentados, 'acidentado', 'acidentados')}` : 'Não', icone: 'pi pi-heart' },
      { rotulo: 'Dias perdidos', valor: String(l.diasPerdidos), icone: 'pi pi-calendar-minus' },
      { rotulo: 'CAT', valor: l.comLesao ? (l.possuiCat ? 'Emitida' : 'Pendente') : 'Não se aplica', icone: 'pi pi-file' },
      { rotulo: 'Comunicação ao SESMT', valor: `${l.horasAteComunicacao.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} h${l.comunicacaoAtrasada ? ' (fora do prazo)' : ' (no prazo)'}`, icone: 'pi pi-clock' },
      { rotulo: 'Atividade', valor: l.atividade, icone: 'pi pi-briefcase' },
      { rotulo: 'Gestor', valor: l.gestor, icone: 'pi pi-user' },
      { rotulo: 'Base · equipe', valor: `${l.base} · ${l.equipe}`, icone: 'pi pi-building' },
      { rotulo: 'Veículo', valor: l.veiculo, icone: 'pi pi-car' },
      { rotulo: 'Local', valor: l.local, icone: 'pi pi-map-marker' }
    ],
    links: [{ rotulo: 'Abrir o evento completo', icone: 'pi pi-file', rota: ['/sst/eventos', l.id] }]
  };
}

function filtrar(chave: string, rotulo: string, aceita: (l: LinhaEvento) => boolean): AcaoAnalise {
  return { tipo: 'filtrar', filtro: { chave, rotulo, aceita } as FiltroOrigem };
}

/** Gráficos a partir dos eventos carregados. */
function graficosDosEventos(linhas: readonly LinhaEvento[], opcoes: { tipo?: boolean; comunicacao?: boolean; classificacao?: boolean } = {}): GraficoAnalise[] {
  if (!linhas.length) return [];
  const graficos: GraficoAnalise[] = [];
  if (opcoes.tipo !== false) {
    const tipos = contar(linhas, (l) => l.tipo);
    graficos.push({
      id: 'eventos-tipo',
      titulo: 'Por tipo',
      descricao: 'Clique numa fatia para listar os eventos.',
      tipo: 'doughnut',
      rotulos: tipos.map((t) => TIPO_EVENTO_LABELS[t.rotulo as TipoEventoSst] ?? t.rotulo),
      series: [{ name: 'Eventos', values: tipos.map((t) => t.total) }],
      formato: 'integer',
      altura: 220,
      aoSelecionar: (i) => filtrar(`tipo-${tipos[i].rotulo}`, TIPO_EVENTO_LABELS[tipos[i].rotulo as TipoEventoSst] ?? tipos[i].rotulo, (l) => l.tipo === tipos[i].rotulo),
      rotuloSelecao: 'Ver eventos'
    });
  }
  if (opcoes.comunicacao !== false) {
    const atrasados = linhas.filter((l) => l.comunicacaoAtrasada).length;
    graficos.push({
      id: 'eventos-comunicacao',
      titulo: 'Comunicação ao SESMT',
      descricao: 'Prazo legal: 24 h para acidentes e doença ocupacional, 72 h para os demais.',
      tipo: 'doughnut',
      rotulos: ['No prazo', 'Fora do prazo'],
      series: [{ name: 'Eventos', values: [linhas.length - atrasados, atrasados] }],
      formato: 'integer',
      altura: 220,
      aoSelecionar: (i) => filtrar(`comunicacao-${i}`, i ? 'Comunicados fora do prazo' : 'Comunicados no prazo', (l) => l.comunicacaoAtrasada === (i === 1)),
      rotuloSelecao: 'Ver eventos'
    });
  }
  if (opcoes.classificacao !== false) {
    const classes = contar(linhas, (l) => l.classificacao, 10);
    graficos.push({
      id: 'eventos-classificacao',
      titulo: 'Por classificação',
      tipo: 'bar-horizontal',
      rotulos: classes.map((c) => c.rotulo),
      series: [{ name: 'Eventos', values: classes.map((c) => c.total) }],
      formato: 'integer',
      altura: alturaBarras(classes.length),
      aoSelecionar: (i) => filtrar(`classificacao-${i}`, classes[i].rotulo, (l) => l.classificacao === classes[i].rotulo),
      rotuloSelecao: 'Ver eventos'
    });
  }
  const bases = contar(linhas, (l) => l.base, 10);
  if (bases.length > 1) {
    graficos.push({
      id: 'eventos-base',
      titulo: 'Por base operacional',
      tipo: 'bar-horizontal',
      rotulos: bases.map((b) => b.rotulo),
      series: [{ name: 'Eventos', values: bases.map((b) => b.total) }],
      formato: 'integer',
      altura: alturaBarras(bases.length),
      aoSelecionar: (i) => filtrar(`base-${i}`, `Base ${bases[i].rotulo}`, (l) => l.base === bases[i].rotulo),
      rotuloSelecao: 'Ver eventos'
    });
  }
  return graficos;
}

function graficoDiasPerdidos(linhas: readonly LinhaEvento[]): GraficoAnalise[] {
  const top = [...linhas].filter((l) => l.diasPerdidos > 0).sort((a, b) => b.diasPerdidos - a.diasPerdidos).slice(0, 12);
  if (!top.length) return [];
  return [{
    id: 'eventos-dias-perdidos',
    titulo: 'Eventos com mais dias perdidos',
    descricao: 'Clique num evento para ver o detalhe.',
    tipo: 'bar-horizontal',
    rotulos: top.map((l) => `${l.numero} · ${TIPO_EVENTO_LABELS[l.tipo] ?? l.tipo}`),
    series: [{ name: 'Dias perdidos', values: top.map((l) => l.diasPerdidos) }],
    formato: 'integer',
    altura: alturaBarras(top.length),
    inteiro: true,
    aoSelecionar: (i) => ({ tipo: 'registro', id: top[i].id }),
    rotuloSelecao: 'Ver evento'
  }];
}

interface OpcoesOrigemEventos {
  readonly filtros: EventoSstFilter;
  readonly titulo?: string;
  readonly descricao?: string;
  readonly quantidadeEsperada?: number;
  readonly derivados?: (linhas: readonly LinhaEvento[]) => readonly GraficoAnalise[];
  readonly nomeArquivo: string;
}

function origemEventos(c: ContextoAnaliseEventos, o: OpcoesOrigemEventos): OrigemAnalise<LinhaEvento> {
  return {
    id: 'eventos',
    chave: `eventos:${JSON.stringify(o.filtros)}`,
    titulo: o.titulo ?? 'Eventos que compõem este número',
    rotuloAba: 'Origem · eventos',
    icone: 'pi pi-flag',
    descricao: o.descricao ?? 'Eventos do recorte (sem cancelados). Clique num evento para ver lesão, CAT, comunicação e responsáveis.',
    colunas: COLUNAS_EVENTO,
    carregar: () => c.sst.listarEventos({ ...o.filtros, page: 0, size: LIMITE_SST, sort: 'dataOcorrencia,desc' }).pipe(
      map((p) => ({ linhas: p.content.map(linhaEvento), total: p.page.totalElements }))
    ),
    chaveLinha: (l) => l.id,
    rotuloLinha: (l) => `Evento ${l.numero}`,
    conferencia: o.quantidadeEsperada != null ? { quantidadeEsperada: o.quantidadeEsperada, valorEsperado: null, somar: () => 0, formato: 'inteiro' } : undefined,
    derivados: o.derivados,
    detalhar: detalheEvento,
    verTudo: { rotulo: 'Ver na listagem de eventos', icone: 'pi pi-external-link', rota: '/sst/eventos' },
    nomeArquivo: o.nomeArquivo,
    vazio: 'Nenhum evento neste recorte'
  };
}

// ------------------------------------------------------------------ filtros

function periodo(c: ContextoAnaliseEventos): { inicio: string; fim: string; de: string; ate: string } {
  const de = diaCivil(c.d.inicio);
  const ate = diaCivil(c.d.fim);
  return { de, ate, inicio: c.filtros.inicio ?? intervaloDoPonto(de, 'DIA')!.inicio, fim: c.filtros.fim ?? intervaloDoPonto(ate, 'DIA')!.fim };
}

function rotuloPeriodo(c: ContextoAnaliseEventos): string {
  const p = periodo(c);
  const f = (iso: string) => iso.split('-').reverse().join('/');
  return p.de === p.ate ? f(p.de) : `${f(p.de)} a ${f(p.ate)}`;
}

/** Recorte do painel → filtros da listagem de eventos (sem o período). */
function filtrosBase(c: ContextoAnaliseEventos): EventoSstFilter {
  const filtro: EventoSstFilter = { status: [...VALIDOS] };
  (['tipo', 'idClassificacao', 'idAtividade', 'idGestor', 'idProcesso', 'idBaseOperacional', 'idCentroCusto', 'idEquipe'] as const).forEach((dim) => {
    const selecao = c.filtros[dim] as string[] | null | undefined;
    if (selecao?.length) (filtro as Record<string, unknown>)[dim] = [...selecao];
  });
  return filtro;
}

function filtrosPeriodo(c: ContextoAnaliseEventos, extra: EventoSstFilter = {}): EventoSstFilter {
  const p = periodo(c);
  return { ...filtrosBase(c), dataOcorrenciaDe: p.inicio, dataOcorrenciaAte: p.fim, ...extra };
}

function graficoSerie(c: ContextoAnaliseEventos, serie: 'total' | 'comLesao' | 'diasPerdidos' = 'total'): GraficoAnalise {
  const { d } = c;
  return {
    id: `serie-${serie}`,
    titulo: serie === 'total' ? 'Eventos ao longo do período' : serie === 'comLesao' ? 'Eventos com lesão ao longo do período' : 'Dias perdidos ao longo do período',
    descricao: 'Clique num ponto para analisar aquele período.',
    tipo: 'bar',
    rotulos: d.serie.map((s) => s.rotulo),
    series: serie === 'total'
      ? [{ name: 'Eventos', values: d.serie.map((s) => s.total) }, { name: 'Com lesão', values: d.serie.map((s) => s.comLesao) }]
      : [{ name: serie === 'comLesao' ? 'Com lesão' : 'Dias perdidos', values: d.serie.map((s) => s[serie]) }],
    formato: 'integer',
    altura: 240,
    inteiro: true,
    aoSelecionar: (i) => navegar(DIMENSAO_GRANULARIDADE[d.granularidade], diaCivil(d.serie[i].data)),
    rotuloSelecao: 'Analisar período'
  };
}

function graficoRanking(d: DashboardEventosSstResponse, dim: DimensaoRankingEvento, titulo?: string): GraficoAnalise {
  const itens = ((d[DIMENSOES_RANKING_EVENTO[dim].campo] ?? []) as RankingEventoItem[]).slice(0, 12);
  return {
    id: `ranking-${dim}`,
    titulo: titulo ?? `Por ${DIMENSOES_RANKING_EVENTO[dim].rotulo.toLowerCase()}`,
    descricao: 'Clique para analisar.',
    tipo: 'bar-horizontal',
    rotulos: itens.map((x) => x.nome),
    series: [{ name: 'Eventos', values: itens.map((x) => x.total) }],
    formato: 'integer',
    altura: alturaBarras(itens.length),
    aoSelecionar: (i) => navegar('ranking', `${dim}~${itens[i].id}`),
    rotuloSelecao: 'Analisar'
  };
}

function graficoTipos(d: DashboardEventosSstResponse): GraficoAnalise {
  const tipos = (Object.keys(d.resumo.porTipo ?? {}) as TipoEventoSst[]).filter((t) => d.resumo.porTipo[t] > 0);
  return {
    id: 'composicao-tipo',
    titulo: 'Composição por tipo',
    descricao: 'Clique num tipo para analisá-lo.',
    tipo: 'doughnut',
    rotulos: tipos.map((t) => TIPO_EVENTO_LABELS[t] ?? t),
    series: [{ name: 'Eventos', values: tipos.map((t) => d.resumo.porTipo[t]) }],
    formato: 'integer',
    altura: 230,
    aoSelecionar: (i) => navegar('tipo', tipos[i]),
    rotuloSelecao: 'Analisar tipo'
  };
}

// ------------------------------------------------------------------ vistas

export function vistaEventos(a: AlvoAnalise, c: ContextoAnaliseEventos): VistaAnalise | null {
  switch (a.dimension) {
    case 'kpi':
      switch (a.value) {
        case 'eventos': return vistaTotal(a, c);
        case 'dias-sem-eventos': return vistaDiasSem(a, c);
        case 'lesao': return vistaLesao(a, c);
        case 'dias-perdidos': return vistaDiasPerdidos(a, c);
        case 'comunicacao': return vistaComunicacao(a, c);
        case 'cat': return vistaCat(a, c);
        case 'abertos': return vistaAbertos(a, c);
        default: return null;
      }
    case 'dia':
    case 'mes':
    case 'ano':
      return vistaPeriodo(a, c);
    case 'tipo': return vistaTipo(a, c);
    case 'faixa': return vistaFaixa(a, c);
    case 'ranking': return vistaRanking(a, c);
    default: return null;
  }
}

function vistaTotal(a: AlvoAnalise, c: ContextoAnaliseEventos): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const destaques: DestaqueAnalise[] = [];
  if (r.totalAnterior) destaques.push({ tom: r.variacaoPercentual <= 0 ? 'success' : 'warning', icone: r.variacaoPercentual <= 0 ? 'pi pi-arrow-down-right' : 'pi pi-arrow-up-right', texto: `${r.variacaoPercentual > 0 ? '+' : ''}${pct(r.variacaoPercentual)} contra o período anterior (${r.totalAnterior}).` });
  if (r.comLesao) destaques.push({ tom: 'danger', icone: 'pi pi-heart', texto: `${plural(r.comLesao, 'evento teve', 'eventos tiveram')} lesão (${pct((r.comLesao / Math.max(1, r.total)) * 100)}).` });
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Eventos no período',
    titulo: `${plural(r.total, 'evento', 'eventos')} em ${rotuloPeriodo(c)}`,
    descricao: 'O que aconteceu, onde, com que gravidade e como foi comunicado.',
    kpis: [
      { rotulo: 'Eventos', valor: String(r.total), tom: 'brand', icone: 'pi pi-flag' },
      { rotulo: 'Com lesão', valor: String(r.comLesao), tom: r.comLesao ? 'danger' : 'success', icone: 'pi pi-heart' },
      { rotulo: 'Dias perdidos', valor: String(r.diasPerdidos), icone: 'pi pi-calendar-minus' },
      { rotulo: 'Fora do prazo', valor: String(r.comunicacoesAtrasadas), icone: 'pi pi-clock', detalhe: pct(r.percentualAtrasadas), tom: r.comunicacoesAtrasadas ? 'warning' : 'success' },
      { rotulo: 'Em aberto', valor: String(r.abertos), icone: 'pi pi-inbox', detalhe: `${r.concluidos} concluídos` }
    ],
    destaques,
    graficos: [graficoSerie(c), graficoTipos(d), graficoRanking(d, 'classificacao'), graficoRanking(d, 'base')],
    origem: origemEventos(c, {
      filtros: filtrosPeriodo(c),
      quantidadeEsperada: r.total,
      derivados: (l) => graficosDosEventos(l, { tipo: false, classificacao: false }),
      nomeArquivo: 'eventos-periodo'
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'total', rotulo: 'Eventos no período' },
      pergunta: `Foram ${r.total} eventos no período (${r.comLesao} com lesão). Que padrões aparecem por tipo, atividade, base e horário, e o que priorizar na prevenção?`
    }
  };
}

function vistaDiasSem(a: AlvoAnalise, c: ContextoAnaliseEventos): VistaAnalise {
  const { d } = c;
  const bases = [...d.diasSemAcidente].sort((x, y) => (x.diasSemAcidente ?? Infinity) - (y.diasSemAcidente ?? Infinity));
  const comHistorico = bases.filter((b) => b.diasSemAcidente != null);
  const colunas: CesDataTableColumn<DiasSemAcidenteItem>[] = [
    { key: 'baseNome', header: 'Base', priority: 1 },
    { key: 'diasSemAcidente', header: 'Dias sem lesão', priority: 1, value: (b) => b.diasSemAcidente ?? 99999, display: (b) => (b.diasSemAcidente == null ? 'Sem ocorrência' : `${b.diasSemAcidente} dias`) },
    { key: 'ultimaOcorrencia', header: 'Última ocorrência', priority: 2, value: (b) => b.ultimaOcorrencia ?? '', display: (b) => (b.ultimaOcorrencia ? new Date(b.ultimaOcorrencia).toLocaleDateString('pt-BR') : '—') },
    { key: 'comLesaoNoPeriodo', header: 'Com lesão no período', type: 'integer', priority: 2 }
  ];
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Dias sem eventos',
    titulo: d.resumo.diasSemEventos == null ? 'Sem evento válido no histórico' : `${plural(d.resumo.diasSemEventos, 'dia', 'dias')} sem eventos`,
    descricao: 'Métrica global, desde o último evento válido. A tabela por base ignora o período do filtro, mas respeita os demais recortes.',
    kpis: [
      { rotulo: 'Dias sem eventos', valor: d.resumo.diasSemEventos == null ? '—' : String(d.resumo.diasSemEventos), tom: 'success', icone: 'pi pi-calendar' },
      { rotulo: 'Bases com lesão no período', valor: String(bases.filter((b) => b.comLesaoNoPeriodo > 0).length), icone: 'pi pi-map-marker', tom: bases.some((b) => b.comLesaoNoPeriodo > 0) ? 'warning' : 'success' },
      { rotulo: 'Bases sem ocorrência', valor: String(bases.length - comHistorico.length), icone: 'pi pi-check-circle' }
    ],
    graficos: comHistorico.length ? [{
      id: 'dias-por-base',
      titulo: 'Dias sem evento com lesão, por base',
      descricao: 'Do menor intervalo para o maior. Clique para analisar a base.',
      tipo: 'bar-horizontal',
      rotulos: comHistorico.map((b) => b.baseNome),
      series: [{ name: 'Dias', values: comHistorico.map((b) => b.diasSemAcidente) }],
      formato: 'integer',
      altura: alturaBarras(comHistorico.length),
      inteiro: true,
      aoSelecionar: (i) => navegar('ranking', `base~${comHistorico[i].idBaseOperacional}`),
      rotuloSelecao: 'Analisar base'
    }] : [],
    tabelas: [{
      id: 'bases',
      titulo: 'Bases',
      icone: 'pi pi-building',
      colunas,
      linhas: bases,
      chaveLinha: (b: DiasSemAcidenteItem) => b.idBaseOperacional,
      rotuloLinha: (b: DiasSemAcidenteItem) => b.baseNome,
      aoAtivar: (b: DiasSemAcidenteItem) => navegar('ranking', `base~${b.idBaseOperacional}`),
      rotuloAcao: 'Analisar',
      nomeArquivo: 'dias-sem-acidente-por-base'
    }],
    origem: origemEventos(c, {
      filtros: { ...filtrosBase(c), comLesao: true },
      titulo: 'Eventos com lesão mais recentes',
      descricao: 'Os eventos com lesão do histórico (sem cancelados), do mais recente para o mais antigo.',
      derivados: () => [],
      nomeArquivo: 'eventos-com-lesao-historico'
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'diasSemEventos', rotulo: 'Dias sem eventos' },
      pergunta: 'Quais bases tiveram eventos com lesão mais recentemente e o que esses eventos têm em comum?'
    }
  };
}

function vistaLesao(a: AlvoAnalise, c: ContextoAnaliseEventos): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Eventos com lesão',
    titulo: `${plural(r.comLesao, 'evento com lesão', 'eventos com lesão')}`,
    descricao: rotuloPeriodo(c),
    kpis: [
      { rotulo: 'Com lesão', valor: String(r.comLesao), tom: r.comLesao ? 'danger' : 'success', icone: 'pi pi-heart', detalhe: r.comLesaoAnterior ? `${r.variacaoComLesao > 0 ? '+' : ''}${pct(r.variacaoComLesao)} vs. anterior` : 'sem base anterior' },
      { rotulo: 'Dias perdidos', valor: String(r.diasPerdidos), icone: 'pi pi-calendar-minus' },
      { rotulo: 'CAT emitida', valor: `${r.comCat} de ${r.comLesao}`, icone: 'pi pi-file', detalhe: pct(r.coberturaCat), tom: r.comLesao && r.coberturaCat < 100 ? 'danger' : 'success' },
      { rotulo: 'Desvios por lesão', valor: r.razaoDesviosPorLesao.toLocaleString('pt-BR', { maximumFractionDigits: 1 }), icone: 'pi pi-chart-line', detalhe: 'base preventiva da pirâmide' }
    ],
    destaques: r.comLesao && r.coberturaCat < 100 ? [{ tom: 'danger', icone: 'pi pi-exclamation-triangle', texto: `${plural(r.comLesao - r.comCat, 'evento com lesão está', 'eventos com lesão estão')} sem CAT registrada.` }] : [],
    graficos: [graficoSerie(c, 'comLesao'), graficoRanking(d, 'localLesao', 'Locais de lesão no corpo'), graficoRanking(d, 'atividade')],
    origem: origemEventos(c, {
      filtros: filtrosPeriodo(c, { comLesao: true }),
      titulo: 'Eventos com lesão',
      quantidadeEsperada: r.comLesao,
      derivados: (l) => [...graficoDiasPerdidos(l), ...graficosDosEventos(l, { comunicacao: false })],
      nomeArquivo: 'eventos-com-lesao'
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'comLesao', rotulo: 'Eventos com lesão' },
      pergunta: `Houve ${r.comLesao} eventos com lesão (${r.diasPerdidos} dias perdidos). Quais causas imediatas se repetem e quais planos de ação estão pendentes?`
    }
  };
}

function vistaDiasPerdidos(a: AlvoAnalise, c: ContextoAnaliseEventos): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Dias perdidos',
    titulo: `${plural(r.diasPerdidos, 'dia perdido', 'dias perdidos')}`,
    descricao: `${rotuloPeriodo(c)} · somados nos eventos com lesão`,
    kpis: [
      { rotulo: 'Dias perdidos', valor: String(r.diasPerdidos), tom: r.diasPerdidos ? 'warning' : 'success', icone: 'pi pi-calendar-minus' },
      { rotulo: 'Por evento com lesão', valor: r.comLesao ? (r.diasPerdidos / r.comLesao).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) : '—', icone: 'pi pi-percentage' },
      { rotulo: 'Eventos com lesão', valor: String(r.comLesao), icone: 'pi pi-heart' }
    ],
    graficos: [graficoSerie(c, 'diasPerdidos')],
    origem: origemEventos(c, {
      filtros: filtrosPeriodo(c, { comLesao: true }),
      titulo: 'Eventos com lesão e dias perdidos',
      quantidadeEsperada: r.comLesao,
      derivados: (l) => [...graficoDiasPerdidos(l), ...graficosDosEventos(l, { comunicacao: false, classificacao: false })],
      nomeArquivo: 'eventos-dias-perdidos'
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'diasPerdidos', rotulo: 'Dias perdidos' },
      pergunta: `Foram ${r.diasPerdidos} dias perdidos no período. Quais eventos concentram os afastamentos e que medidas reduziriam a gravidade?`
    }
  };
}

function vistaComunicacao(a: AlvoAnalise, c: ContextoAnaliseEventos): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Comunicação ao SESMT',
    titulo: `${plural(r.comunicacoesAtrasadas, 'comunicação fora do prazo', 'comunicações fora do prazo')}`,
    descricao: `${rotuloPeriodo(c)} · prazo legal de 24 h para acidentes e doença ocupacional, 72 h para os demais`,
    kpis: [
      { rotulo: 'Fora do prazo', valor: String(r.comunicacoesAtrasadas), tom: r.comunicacoesAtrasadas ? 'danger' : 'success', icone: 'pi pi-clock', detalhe: pct(r.percentualAtrasadas) },
      { rotulo: 'Tempo médio', valor: `${r.mediaHorasComunicacao.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} h`, icone: 'pi pi-stopwatch' },
      { rotulo: 'Eventos', valor: String(r.total), icone: 'pi pi-flag' }
    ],
    graficos: [],
    origem: origemEventos(c, {
      filtros: filtrosPeriodo(c),
      quantidadeEsperada: r.total,
      derivados: (l) => {
        const faixas = [
          { rotulo: 'Até 24 h', de: 0, ate: 24 },
          { rotulo: '24 a 72 h', de: 24, ate: 72 },
          { rotulo: '72 h a 7 dias', de: 72, ate: 168 },
          { rotulo: 'Mais de 7 dias', de: 168, ate: Infinity }
        ];
        const atrasadosPorBase = contar(l.filter((x) => x.comunicacaoAtrasada), (x) => x.base, 10);
        return [
          ...graficosDosEventos(l, { tipo: false, classificacao: false }).filter((g) => g.id === 'eventos-comunicacao'),
          {
            id: 'horas-comunicacao',
            titulo: 'Tempo até comunicar',
            descricao: 'Clique numa faixa para listar os eventos.',
            tipo: 'bar',
            rotulos: faixas.map((f) => f.rotulo),
            series: [{ name: 'Eventos', values: faixas.map((f) => l.filter((x) => x.horasAteComunicacao >= f.de && x.horasAteComunicacao < f.ate).length) }],
            formato: 'integer',
            altura: 220,
            aoSelecionar: (i) => filtrar(`horas-${i}`, `Comunicados em ${faixas[i].rotulo.toLowerCase()}`, (x) => x.horasAteComunicacao >= faixas[i].de && x.horasAteComunicacao < faixas[i].ate),
            rotuloSelecao: 'Ver eventos'
          },
          ...(atrasadosPorBase.length ? [{
            id: 'atrasados-por-base',
            titulo: 'Atrasos por base',
            tipo: 'bar-horizontal' as const,
            rotulos: atrasadosPorBase.map((b) => b.rotulo),
            series: [{ name: 'Fora do prazo', values: atrasadosPorBase.map((b) => b.total) }],
            formato: 'integer' as const,
            altura: alturaBarras(atrasadosPorBase.length),
            aoSelecionar: (i: number) => filtrar(`atraso-base-${i}`, `Atrasados · ${atrasadosPorBase[i].rotulo}`, (x) => x.comunicacaoAtrasada && x.base === atrasadosPorBase[i].rotulo),
            rotuloSelecao: 'Ver eventos'
          }] : [])
        ];
      },
      nomeArquivo: 'eventos-comunicacao'
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'comunicacoesAtrasadas', rotulo: 'Comunicações fora do prazo' },
      pergunta: `${r.comunicacoesAtrasadas} eventos foram comunicados fora do prazo (média de ${r.mediaHorasComunicacao.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} h). Onde o fluxo de notificação está falhando?`
    }
  };
}

function vistaCat(a: AlvoAnalise, c: ContextoAnaliseEventos): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Andamento e CAT',
    titulo: `${pct(r.coberturaCat)} de cobertura de CAT`,
    descricao: rotuloPeriodo(c),
    kpis: [
      { rotulo: 'CAT emitida', valor: `${r.comCat} de ${r.comLesao}`, tom: r.comLesao && r.coberturaCat < 100 ? 'danger' : 'success', icone: 'pi pi-file' },
      { rotulo: 'Em aberto', valor: String(r.abertos), icone: 'pi pi-inbox' },
      { rotulo: 'Concluídos', valor: String(r.concluidos), icone: 'pi pi-check-circle' },
      { rotulo: 'Sem lesão', valor: String(r.semLesao), icone: 'pi pi-shield' }
    ],
    graficos: [{
      id: 'andamento',
      titulo: 'Andamento dos eventos do período',
      tipo: 'doughnut',
      rotulos: ['Em aberto', 'Concluídos'],
      series: [{ name: 'Eventos', values: [r.abertos, r.concluidos] }],
      formato: 'integer',
      altura: 220,
      aoSelecionar: (i) => filtrar(`andamento-${i}`, i ? 'Concluídos' : 'Em aberto', (l) => (i ? l.status === 'CONCLUIDO' : l.status !== 'CONCLUIDO')),
      rotuloSelecao: 'Ver eventos'
    }],
    origem: origemEventos(c, {
      filtros: filtrosPeriodo(c),
      quantidadeEsperada: r.total,
      derivados: (l) => {
        const lesao = l.filter((x) => x.comLesao);
        return lesao.length ? [{
          id: 'cat',
          titulo: 'CAT nos eventos com lesão',
          descricao: 'Clique em "Pendente" para listar quem falta regularizar.',
          tipo: 'doughnut',
          rotulos: ['Emitida', 'Pendente'],
          series: [{ name: 'Eventos', values: [lesao.filter((x) => x.possuiCat).length, lesao.filter((x) => !x.possuiCat).length] }],
          formato: 'integer',
          altura: 220,
          aoSelecionar: (i: number) => filtrar(`cat-${i}`, i ? 'CAT pendente' : 'CAT emitida', (x) => x.comLesao && x.possuiCat === (i === 0)),
          rotuloSelecao: 'Ver eventos'
        }] : [];
      },
      nomeArquivo: 'eventos-andamento'
    }),
    copiloto: {
      foco: { tipo: 'secao', id: 'andamentoCobertura', rotulo: 'Andamento e cobertura de CAT' },
      pergunta: `A cobertura de CAT está em ${pct(r.coberturaCat)} e há ${r.abertos} eventos em aberto. Quais regularizar primeiro?`
    }
  };
}

function vistaAbertos(a: AlvoAnalise, c: ContextoAnaliseEventos): VistaAnalise {
  const { d } = c;
  const total = d.backlog.reduce((t, f) => t + f.total, 0);
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Tratativa em aberto',
    titulo: `${plural(total, 'evento em aberto', 'eventos em aberto')} hoje`,
    descricao: 'Tudo que ainda não teve desfecho, ocorrido quando for. A idade conta da ocorrência até hoje.',
    kpis: [
      { rotulo: 'Em aberto', valor: String(total), tom: total ? 'warning' : 'success', icone: 'pi pi-inbox' },
      { rotulo: 'Há mais de 15 dias', valor: String(d.backlog.filter((f) => f.idadeMinimaDias >= 16).reduce((t, f) => t + f.total, 0)), icone: 'pi pi-calendar-times', tom: 'danger' }
    ],
    graficos: [{
      id: 'backlog',
      titulo: 'Em aberto por tempo desde a ocorrência',
      descricao: 'Clique numa faixa para ver os eventos dela.',
      tipo: 'bar-stacked',
      rotulos: d.backlog.map((f) => f.faixa),
      series: (['REGISTRADO', 'EM_TRATATIVA'] as StatusEventoSst[]).map((s) => ({ name: STATUS_EVENTO_LABELS[s], values: d.backlog.map((f) => f.porStatus[s] ?? 0) })),
      formato: 'integer',
      altura: 240,
      inteiro: true,
      aoSelecionar: (i) => navegar('faixa', d.backlog[i].faixa),
      rotuloSelecao: 'Ver eventos'
    }],
    origem: origemEventos(c, {
      filtros: { ...filtrosBase(c), status: [...ABERTOS] },
      titulo: 'Eventos em aberto',
      quantidadeEsperada: total,
      derivados: (l) => graficosDosEventos(l, { comunicacao: false }),
      nomeArquivo: 'eventos-em-aberto'
    }),
    copiloto: {
      foco: { tipo: 'secao', id: 'backlog', rotulo: 'Eventos em aberto' },
      pergunta: `Há ${total} eventos sem desfecho. Quais estão parados há mais tempo, com quem e o que falta para concluir?`
    }
  };
}

function vistaFaixa(a: AlvoAnalise, c: ContextoAnaliseEventos): VistaAnalise | null {
  const { d } = c;
  const idx = d.backlog.findIndex((f) => f.faixa === a.value);
  if (idx < 0) return null;
  const f = d.backlog[idx];
  const hoje = new Date();
  const menos = (dias: number) => {
    const x = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate() - dias);
    return `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`;
  };
  const ate = intervaloDoPonto(menos(f.idadeMinimaDias), 'DIA')!.fim;
  const de = f.idadeMaximaDias != null ? intervaloDoPonto(menos(f.idadeMaximaDias), 'DIA')!.inicio : null;
  const vizinho = (k: number) => (d.backlog[k] ? { alvo: alvo('faixa', d.backlog[k].faixa), rotulo: d.backlog[k].faixa } : undefined);
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Tratativa em aberto',
    titulo: `Em aberto há ${f.faixa}`,
    descricao: 'Eventos sem desfecho nesta faixa de idade (da ocorrência até hoje).',
    anterior: vizinho(idx - 1),
    proximo: vizinho(idx + 1),
    kpis: [
      { rotulo: 'Em aberto', valor: String(f.total), tom: f.idadeMinimaDias >= 16 ? 'danger' : 'warning', icone: 'pi pi-inbox' },
      { rotulo: 'Registrados', valor: String(f.porStatus.REGISTRADO ?? 0), icone: 'pi pi-file' },
      { rotulo: 'Em tratativa', valor: String(f.porStatus.EM_TRATATIVA ?? 0), icone: 'pi pi-pencil' }
    ],
    graficos: [],
    origem: origemEventos(c, {
      filtros: { ...filtrosBase(c), status: [...ABERTOS], dataOcorrenciaAte: ate, ...(de ? { dataOcorrenciaDe: de } : {}) },
      titulo: `Eventos em aberto há ${f.faixa}`,
      quantidadeEsperada: f.total,
      derivados: (l) => graficosDosEventos(l, { comunicacao: false }),
      nomeArquivo: `eventos-abertos-${f.faixa}`
    }),
    copiloto: {
      foco: { tipo: 'secao', id: 'backlog', rotulo: `Eventos em aberto há ${f.faixa}` },
      pergunta: `Há ${f.total} eventos em aberto há ${f.faixa}. Quem são os responsáveis e o que falta para concluir cada um?`
    }
  };
}

function vistaPeriodo(a: AlvoAnalise, c: ContextoAnaliseEventos): VistaAnalise | null {
  const { d } = c;
  const intervalo = intervaloDoPonto(a.value, GRANULARIDADE_DIMENSAO[a.dimension]);
  if (!intervalo) return null;
  const idx = d.serie.findIndex((s) => diaCivil(s.data) === a.value);
  const ponto = d.serie[idx];
  const anterior = idx > 0 ? d.serie[idx - 1] : null;
  const proximo = idx >= 0 && idx < d.serie.length - 1 ? d.serie[idx + 1] : null;
  return {
    chave: chaveDoAlvo(a),
    eyebrow: a.dimension === 'dia' ? 'Dia' : a.dimension === 'mes' ? 'Mês' : 'Ano',
    titulo: ponto?.rotulo ?? intervalo.de.split('-').reverse().join('/'),
    descricao: intervalo.de === intervalo.ate ? intervalo.de.split('-').reverse().join('/') : `${intervalo.de.split('-').reverse().join('/')} a ${intervalo.ate.split('-').reverse().join('/')}`,
    anterior: anterior ? { alvo: alvo(a.dimension, diaCivil(anterior.data)), rotulo: anterior.rotulo } : undefined,
    proximo: proximo ? { alvo: alvo(a.dimension, diaCivil(proximo.data)), rotulo: proximo.rotulo } : undefined,
    kpis: ponto ? [
      { rotulo: 'Eventos', valor: String(ponto.total), tom: 'brand', icone: 'pi pi-flag' },
      { rotulo: 'Com lesão', valor: String(ponto.comLesao), tom: ponto.comLesao ? 'danger' : 'success', icone: 'pi pi-heart' },
      { rotulo: 'Dias perdidos', valor: String(ponto.diasPerdidos), icone: 'pi pi-calendar-minus' }
    ] : [],
    graficos: [],
    origem: origemEventos(c, {
      filtros: { ...filtrosBase(c), dataOcorrenciaDe: intervalo.inicio, dataOcorrenciaAte: intervalo.fim },
      quantidadeEsperada: ponto?.total,
      derivados: (l) => graficosDosEventos(l),
      nomeArquivo: `eventos-${intervalo.de}`
    }),
    copiloto: {
      foco: { tipo: 'grafico', id: 'serie', rotulo: `Eventos em ${ponto?.rotulo ?? intervalo.de}` },
      pergunta: `O que aconteceu nos eventos de ${ponto?.rotulo ?? intervalo.de}? Tipos, locais, lesões e se a comunicação foi no prazo.`
    }
  };
}

function vistaTipo(a: AlvoAnalise, c: ContextoAnaliseEventos): VistaAnalise | null {
  const { d } = c;
  const tipo = a.value as TipoEventoSst;
  if (!TIPO_EVENTO_LABELS[tipo]) return null;
  const total = d.resumo.porTipo?.[tipo] ?? 0;
  const tipos = (Object.keys(d.resumo.porTipo ?? {}) as TipoEventoSst[]).filter((t) => d.resumo.porTipo[t] > 0);
  const k = tipos.indexOf(tipo);
  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Tipo de evento',
    titulo: TIPO_EVENTO_LABELS[tipo],
    descricao: rotuloPeriodo(c),
    anterior: k > 0 ? { alvo: alvo('tipo', tipos[k - 1]), rotulo: TIPO_EVENTO_LABELS[tipos[k - 1]] } : undefined,
    proximo: k >= 0 && k < tipos.length - 1 ? { alvo: alvo('tipo', tipos[k + 1]), rotulo: TIPO_EVENTO_LABELS[tipos[k + 1]] } : undefined,
    kpis: [
      { rotulo: 'Eventos', valor: String(total), tom: 'brand', icone: 'pi pi-flag' },
      { rotulo: 'Fatia dos eventos', valor: pct(d.resumo.total ? (total / d.resumo.total) * 100 : 0), icone: 'pi pi-chart-pie' }
    ],
    graficos: [],
    origem: origemEventos(c, {
      filtros: filtrosPeriodo(c, { tipo: [tipo] }),
      quantidadeEsperada: total,
      derivados: (l) => [...graficoDiasPerdidos(l), ...graficosDosEventos(l, { tipo: false })],
      nomeArquivo: `eventos-${tipo.toLowerCase()}`
    }),
    copiloto: {
      foco: { tipo: 'tabela', id: 'composicaoPorTipo', rotulo: TIPO_EVENTO_LABELS[tipo] },
      pergunta: `Analise os eventos do tipo "${TIPO_EVENTO_LABELS[tipo]}" do período (${total}): onde, com quais atividades e o que se repete.`
    }
  };
}

function vistaRanking(a: AlvoAnalise, c: ContextoAnaliseEventos): VistaAnalise | null {
  const { d } = c;
  const [dim, id] = a.value.split('~') as [DimensaoRankingEvento, string];
  const def = DIMENSOES_RANKING_EVENTO[dim];
  if (!def || !id) return null;
  const itens = (d[def.campo] ?? []) as RankingEventoItem[];
  const k = itens.findIndex((x) => x.id === id);
  const item = itens[k];
  const base = dim === 'base' ? d.diasSemAcidente.find((b) => b.idBaseOperacional === id) : undefined;
  if (!item && !base) return null;
  const nome = item?.nome ?? base!.baseNome;
  const destaques: DestaqueAnalise[] = [];
  if (base) {
    destaques.push(base.diasSemAcidente == null
      ? { tom: 'success', icone: 'pi pi-check-circle', texto: 'Nenhum evento com lesão no histórico desta base.' }
      : { tom: base.diasSemAcidente < 30 ? 'danger' : 'info', icone: 'pi pi-calendar', texto: `${base.diasSemAcidente} dias sem evento com lesão (último em ${base.ultimaOcorrencia ? new Date(base.ultimaOcorrencia).toLocaleDateString('pt-BR') : '—'}).` });
  }
  return {
    chave: chaveDoAlvo(a),
    eyebrow: def.rotulo,
    titulo: nome,
    descricao: rotuloPeriodo(c),
    anterior: k > 0 ? { alvo: alvo('ranking', `${dim}~${itens[k - 1].id}`), rotulo: itens[k - 1].nome } : undefined,
    proximo: k >= 0 && k < itens.length - 1 ? { alvo: alvo('ranking', `${dim}~${itens[k + 1].id}`), rotulo: itens[k + 1].nome } : undefined,
    kpis: [
      { rotulo: 'Eventos', valor: String(item?.total ?? 0), tom: 'brand', icone: 'pi pi-flag' },
      { rotulo: 'Fatia do período', valor: pct(item?.percentual ?? 0), icone: 'pi pi-chart-pie' },
      ...(item ? [{ rotulo: 'Posição', valor: `${k + 1}º de ${itens.length}`, icone: 'pi pi-sort-amount-down' }] : []),
      ...(base ? [{ rotulo: 'Com lesão no período', valor: String(base.comLesaoNoPeriodo), icone: 'pi pi-heart', tom: (base.comLesaoNoPeriodo ? 'danger' : 'success') as CesTone }] : [])
    ],
    destaques,
    graficos: [],
    origem: origemEventos(c, {
      filtros: filtrosPeriodo(c, { [def.chave]: [id] }),
      quantidadeEsperada: item?.total,
      derivados: (l) => [...graficoDiasPerdidos(l), ...graficosDosEventos(l, { classificacao: dim !== 'classificacao' })],
      nomeArquivo: `eventos-${dim}`
    }),
    links: [{ rotulo: 'Ver na listagem de eventos', icone: 'pi pi-external-link', rota: '/sst/eventos', queryParams: { [def.chave]: id } } as LinkAnalise],
    copiloto: {
      foco: { tipo: 'tabela', id: `ranking.${dim}`, rotulo: `${def.rotulo}: ${nome}` },
      pergunta: `Analise os eventos de ${def.rotulo.toLowerCase()} "${nome}" no período: tipos, lesões, comunicação e o que se repete.`
    }
  };
}

// ------------------------------------------------------------------ visão gerencial

export function visaoEventos(d: DashboardEventosSstResponse): VisaoGerencialSst {
  const r = d.resumo;
  const emAberto = d.backlog.reduce((t, f) => t + f.total, 0);
  const antigos = d.backlog.filter((f) => f.idadeMinimaDias >= 16).reduce((t, f) => t + f.total, 0);
  const semCat = Math.max(0, r.comLesao - r.comCat);
  let veredito: VereditoGerencial;
  if (!r.total) {
    veredito = {
      tom: emAberto ? 'warning' : 'success',
      rotulo: emAberto ? 'Pendências antigas' : 'Sem eventos',
      titulo: 'Nenhum evento no período',
      texto: emAberto ? `Mas ${plural(emAberto, 'evento de outro período segue', 'eventos de outros períodos seguem')} sem desfecho.` : `${r.diasSemEventos != null ? `${r.diasSemEventos} dias sem eventos na operação. ` : ''}Mantenha as inspeções e o relato de desvios.`
    };
  } else {
    const tom: CesTone = r.comLesao > 0 && (semCat > 0 || r.variacaoComLesao > 0) ? 'danger' : r.comLesao > 0 || r.comunicacoesAtrasadas > 0 ? 'warning' : 'success';
    veredito = {
      tom,
      rotulo: tom === 'danger' ? 'Agir agora' : tom === 'warning' ? 'Atenção' : 'Sob controle',
      titulo: r.comLesao ? `${plural(r.comLesao, 'evento com lesão', 'eventos com lesão')} no período` : `${plural(r.total, 'evento', 'eventos')} sem lesão`,
      texto: `${plural(r.total, 'evento', 'eventos')} (${r.totalAnterior ? `${r.variacaoPercentual > 0 ? '+' : ''}${pct(r.variacaoPercentual)} vs. anterior` : 'sem base anterior'}), ${r.diasPerdidos} dias perdidos, ${pct(r.percentualAtrasadas)} comunicados fora do prazo e ${emAberto} sem desfecho.`,
      progresso: r.comLesao ? { valor: r.coberturaCat, rotulo: `${pct(r.coberturaCat)} de cobertura de CAT nos eventos com lesão` } : undefined
    };
  }
  const leituras: LeituraGerencial[] = [];
  if (r.comLesao) leituras.push({ id: 'lesao', tom: 'danger', icone: 'pi pi-heart', titulo: 'Eventos com lesão', valor: String(r.comLesao), texto: `${r.diasPerdidos} dias perdidos${r.comLesaoAnterior ? `; ${r.variacaoComLesao > 0 ? '+' : ''}${pct(r.variacaoComLesao)} vs. anterior` : ''}.`, acao: { rotulo: 'Ver os eventos', alvo: { dimension: 'kpi', value: 'lesao' } } });
  if (semCat) leituras.push({ id: 'cat', tom: 'danger', icone: 'pi pi-file', titulo: 'CAT pendente', valor: String(semCat), texto: 'Eventos com lesão sem número de CAT cadastrado.', acao: { rotulo: 'Ver quem falta', alvo: { dimension: 'kpi', value: 'cat' } } });
  if (r.total) leituras.push({ id: 'comunicacao', tom: r.comunicacoesAtrasadas ? (r.percentualAtrasadas > 25 ? 'danger' : 'warning') : 'success', icone: 'pi pi-clock', titulo: 'Comunicação fora do prazo', valor: r.comunicacoesAtrasadas ? pct(r.percentualAtrasadas) : 'Nenhuma', texto: `Média de ${r.mediaHorasComunicacao.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} h entre a ocorrência e a comunicação.`, acao: { rotulo: 'Analisar comunicação', alvo: { dimension: 'kpi', value: 'comunicacao' } } });
  if (emAberto) leituras.push({ id: 'abertos', tom: antigos ? 'warning' : 'info', icone: 'pi pi-inbox', titulo: 'Tratativas em aberto', valor: String(emAberto), texto: antigos ? `${antigos} há mais de 15 dias sem desfecho.` : 'Todas abertas há até 15 dias.', acao: { rotulo: 'Ver tratativas', alvo: { dimension: 'kpi', value: 'abertos' } } });
  const baseLesao = d.diasSemAcidente.filter((b) => b.comLesaoNoPeriodo > 0).sort((a, b) => b.comLesaoNoPeriodo - a.comLesaoNoPeriodo)[0];
  if (baseLesao) leituras.push({ id: 'base', tom: 'warning', icone: 'pi pi-map-marker', titulo: 'Base com mais lesões', valor: baseLesao.baseNome, texto: `${plural(baseLesao.comLesaoNoPeriodo, 'evento com lesão', 'eventos com lesão')} no período.`, acao: { rotulo: 'Analisar a base', alvo: { dimension: 'ranking', value: `base~${baseLesao.idBaseOperacional}` } } });
  else if (r.diasSemEventos) leituras.push({ id: 'dias', tom: 'success', icone: 'pi pi-calendar', titulo: 'Dias sem eventos', valor: String(r.diasSemEventos), texto: 'Dias consecutivos sem evento válido na operação.', acao: { rotulo: 'Ver por base', alvo: { dimension: 'kpi', value: 'dias-sem-eventos' } } });
  const classe = d.porClassificacao[0];
  if (classe && r.total > 1) leituras.push({ id: 'classe', tom: 'info', icone: 'pi pi-tag', titulo: 'Classificação mais frequente', valor: classe.nome, texto: `${plural(classe.total, 'evento', 'eventos')} (${pct(classe.percentual)} do período).`, acao: { rotulo: 'Analisar', alvo: { dimension: 'ranking', value: `classificacao~${classe.id}` } } });
  if (r.total && !r.desviosNoPeriodo) leituras.push({ id: 'subnotificacao', tom: 'warning', icone: 'pi pi-exclamation-triangle', titulo: 'Base preventiva vazia', valor: '0 desvios', texto: `Nenhum desvio de inspeção registrado para contrapor aos ${r.total} eventos — possível subnotificação.` });
  else if (r.comLesao && r.desviosNoPeriodo) leituras.push({ id: 'piramide', tom: 'info', icone: 'pi pi-chart-line', titulo: 'Desvios por evento com lesão', valor: r.razaoDesviosPorLesao.toLocaleString('pt-BR', { maximumFractionDigits: 1 }), texto: `${r.desviosNoPeriodo} desvios de inspeção sustentam a base da pirâmide.` });
  return {
    veredito,
    leituras,
    pergunta: `Faça a leitura gerencial da acidentalidade deste recorte: ${veredito.titulo.toLowerCase()} (${veredito.texto}) Quais as 3 ações prioritárias de prevenção e regularização?`
  };
}
