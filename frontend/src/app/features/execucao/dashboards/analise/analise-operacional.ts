import { CesDataTableColumn } from '@ces/ui';
import {
  DashboardOperacionalFiltros,
  DashboardOperacionalResponse,
  EquipeOperacional,
  NotaDoRecorte,
  ProducaoPorTipoServico
} from '../../../../core/models/dashboard.models';
import { DashboardService } from '../../../../core/services/dashboard.service';
import {
  AlvoAnalise,
  DestaqueAnalise,
  GraficoAnalise,
  KpiAnalise,
  LinkAnalise,
  VistaAnalise,
  chaveDoAlvo
} from '../../../../shared/components/dashboard/dashboard-analise.models';
import {
  SITUACAO_META,
  dataLocal,
  diaMes,
  moeda,
  moedaCompacta,
  percentual,
  serieAcumulada,
  tomDaMeta,
  variacao
} from '../execucao-dashboards.utils';
import {
  SEM_TIPO,
  agrupar,
  diaDe,
  diaHora,
  fatia,
  graficosDasNotas,
  linkListagemNotas,
  origemNotas,
  plural
} from './analise-comum';

/**
 * Análises (drill-down) do dashboard operacional. Cada alvo vira uma `VistaAnalise`: o que o número
 * clicado significa, os gráficos que o explicam (clicáveis, aprofundando) e as notas que o compõem.
 */

export interface ContextoAnaliseOperacional {
  readonly d: DashboardOperacionalResponse;
  readonly filtros: DashboardOperacionalFiltros;
  readonly service: DashboardService;
  readonly nomeBase: string | null;
  readonly nomeProcesso: string | null;
}

const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];

const alvo = (dimension: string, value: string): AlvoAnalise => ({ dimension, value });
const navegar = (dimension: string, value: string) => ({ tipo: 'navegar' as const, alvo: alvo(dimension, value) });

export function chaveTipo(tipo: Pick<ProducaoPorTipoServico, 'idTipoServico'>): string {
  return tipo.idTipoServico ?? SEM_TIPO;
}

function diaPorExtenso(iso: string): string {
  return dataLocal(iso).toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' });
}

function recorteTexto(c: ContextoAnaliseOperacional): string {
  const partes = [`${diaMes(c.d.inicio)} a ${diaMes(c.d.fim)}`];
  partes.push(c.nomeBase ?? 'todas as bases');
  partes.push(c.nomeProcesso ?? 'todos os processos');
  return partes.join(' · ');
}

function filtrosNotas(c: ContextoAnaliseOperacional) {
  return {
    inicio: c.d.inicio,
    fim: c.d.fim,
    idBaseOperacional: c.filtros.idBaseOperacional,
    idProcesso: c.filtros.idProcesso
  };
}

/** A listagem de notas não filtra por base/processo: só oferece o atalho quando o recorte cabe nela. */
function verTudo(c: ContextoAnaliseOperacional, extra: { idEquipe?: string; inicio?: string; fim?: string; tipo?: string | null } = {}): LinkAnalise | undefined {
  if (!extra.idEquipe && (c.filtros.idBaseOperacional?.length || c.filtros.idProcesso?.length)) return undefined;
  return linkListagemNotas({ inicio: c.d.inicio, fim: c.d.fim, ...extra });
}

function alturaBarras(n: number): number {
  return Math.max(220, Math.min(n, 30) * 28 + 60);
}

function porProducao(equipes: readonly EquipeOperacional[]): EquipeOperacional[] {
  return [...equipes].sort((a, b) => b.producao - a.producao || a.codigo.localeCompare(b.codigo, 'pt-BR'));
}

/** Gráfico de equipes por produção (barras horizontais), clicável para a análise da equipe. */
function graficoEquipes(equipes: readonly EquipeOperacional[], titulo: string, descricao: string, comMeta: boolean): GraficoAnalise {
  const lista = porProducao(equipes).slice(0, 30);
  return {
    id: 'producao-por-equipe',
    titulo,
    descricao,
    tipo: 'bar-horizontal',
    rotulos: lista.map((e) => e.codigo),
    series: [
      { name: 'Produção', values: lista.map((e) => e.producao) },
      ...(comMeta && lista.some((e) => e.metaPeriodo != null) ? [{ name: 'Meta do período', values: lista.map((e) => e.metaPeriodo) }] : [])
    ],
    formato: 'currency',
    altura: alturaBarras(lista.length),
    aoSelecionar: (i) => navegar('equipe', lista[i].idEquipe),
    rotuloSelecao: 'Analisar equipe'
  };
}

function graficoTipos(tipos: readonly ProducaoPorTipoServico[], aoSelecionar: (t: ProducaoPorTipoServico) => ReturnType<NonNullable<GraficoAnalise['aoSelecionar']>>): GraficoAnalise {
  return {
    id: 'producao-por-tipo',
    titulo: 'Produção por tipo de serviço',
    descricao: 'Clique numa fatia para analisar o tipo.',
    tipo: 'doughnut',
    rotulos: tipos.map((t) => t.descricao),
    series: [{ name: 'Produção', values: tipos.map((t) => t.producao) }],
    formato: 'currency',
    altura: 240,
    aoSelecionar: (i) => aoSelecionar(tipos[i]),
    rotuloSelecao: 'Analisar tipo'
  };
}

const colunasEquipes: CesDataTableColumn<EquipeOperacional>[] = [
  { key: 'codigo', header: 'Equipe', priority: 1 },
  { key: 'baseOperacional', header: 'Base', priority: 2, display: (e) => e.baseOperacional ?? '—' },
  { key: 'situacao', header: 'Situação', priority: 1, value: (e) => SITUACAO_META[e.situacao].rotulo },
  { key: 'notas', header: 'Notas', type: 'integer', priority: 2 },
  { key: 'producao', header: 'Produção', type: 'currency', priority: 1 },
  { key: 'valorMedioNota', header: 'Por nota', type: 'currency', priority: 3 },
  { key: 'metaPeriodo', header: 'Meta do período', type: 'currency', priority: 2 },
  { key: 'percentualMeta', header: '% da meta', type: 'percent', priority: 1 },
  { key: 'diasComTurno', header: 'Dias com turno', type: 'integer', priority: 3 },
  { key: 'diasComProducao', header: 'Dias com produção', type: 'integer', priority: 3 }
];

function tabelaEquipes(equipes: readonly EquipeOperacional[], titulo = 'Equipes'): NonNullable<VistaAnalise['tabelas']>[number] {
  return {
    id: 'equipes',
    titulo,
    icone: 'pi pi-users',
    descricao: 'Clique numa equipe para abrir a análise dela.',
    colunas: colunasEquipes,
    linhas: porProducao(equipes),
    chaveLinha: (e: EquipeOperacional) => e.idEquipe,
    rotuloLinha: (e: EquipeOperacional) => `Equipe ${e.codigo}`,
    aoAtivar: (e: EquipeOperacional) => navegar('equipe', e.idEquipe),
    rotuloAcao: 'Analisar',
    nomeArquivo: 'equipes-producao'
  };
}

/** Resolve o alvo contra os dados em tela; alvo desconhecido (link antigo, recorte mudou) devolve `null`. */
export function vistaOperacional(a: AlvoAnalise, c: ContextoAnaliseOperacional): VistaAnalise | null {
  switch (a.dimension) {
    case 'kpi':
      switch (a.value) {
        case 'producao': return vistaProducao(a, c);
        case 'meta': return vistaMeta(a, c);
        case 'notas': return vistaNotas(a, c);
        case 'ritmo': return vistaRitmo(a, c);
        default: return null;
      }
    case 'dia': return vistaDia(a, c);
    case 'mes': return vistaMes(a, c);
    case 'equipe': return vistaEquipe(a, c);
    case 'tipo': return vistaTipo(a, c);
    default: return null;
  }
}

// ------------------------------------------------------------------ cartões

function vistaProducao(a: AlvoAnalise, c: ContextoAnaliseOperacional): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const produtoras = porProducao(d.equipes).filter((e) => e.producao > 0);
  const top = produtoras.slice(0, 3);
  const topFatia = fatia(top.reduce((t, e) => t + e.producao, 0), r.producao);
  const melhorDia = [...d.producaoDiaria].sort((x, y) => y.valor - x.valor)[0];
  const diasComProducao = d.producaoDiaria.filter((p) => p.notas > 0).length;
  const temMeta = d.producaoDiaria.some((p) => p.meta != null);

  const destaques: DestaqueAnalise[] = [];
  if (r.variacaoPercentual != null) {
    destaques.push({
      tom: r.variacaoPercentual >= 0 ? 'success' : 'warning',
      icone: r.variacaoPercentual >= 0 ? 'pi pi-arrow-up-right' : 'pi pi-arrow-down-right',
      texto: `${variacao(r.variacaoPercentual)} em relação ao período anterior de mesma duração (${moedaCompacta(r.producaoPeriodoAnterior)}).`
    });
  }
  if (top.length && r.producao > 0) {
    destaques.push({
      tom: 'info',
      icone: 'pi pi-chart-pie',
      texto: `${top.map((e) => e.codigo).join(', ')} ${top.length === 1 ? 'responde' : 'respondem'} por ${percentual(topFatia)} da produção.`
    });
  }
  if (melhorDia && melhorDia.valor > 0) {
    destaques.push({ tom: 'neutral', icone: 'pi pi-star', texto: `Melhor dia: ${diaPorExtenso(melhorDia.data)}, com ${moedaCompacta(melhorDia.valor)} em ${plural(melhorDia.notas, 'nota', 'notas')}.` });
  }
  const paradas = r.equipesAtivas - r.equipesComProducao;
  if (paradas > 0) {
    destaques.push({ tom: 'warning', icone: 'pi pi-exclamation-circle', texto: `${plural(paradas, 'equipe ativa não lançou', 'equipes ativas não lançaram')} nenhuma nota no período.` });
  }

  const bases = agruparEquipes(d.equipes, (e) => e.baseOperacional ?? 'Sem base');
  const graficos: GraficoAnalise[] = [
    {
      id: 'producao-por-dia',
      titulo: 'Produção por dia',
      descricao: 'Clique num dia para ver as equipes e as notas daquele dia.',
      tipo: 'bar',
      rotulos: d.producaoDiaria.map((p) => diaMes(p.data)),
      series: [
        { name: 'Produção', values: d.producaoDiaria.map((p) => p.valor) },
        ...(temMeta ? [{ name: 'Meta do dia', values: d.producaoDiaria.map((p) => p.meta) }] : [])
      ],
      formato: 'currency',
      altura: 260,
      inteiro: true,
      aoSelecionar: (i) => navegar('dia', d.producaoDiaria[i].data),
      rotuloSelecao: 'Analisar dia'
    },
    graficoEquipes(d.equipes, 'Produção por equipe', 'Da maior para a menor. Clique para analisar a equipe.', false),
    graficoTipos(d.tiposServico, (t) => navegar('tipo', chaveTipo(t)))
  ];
  if (bases.length > 1) {
    graficos.push({
      id: 'producao-por-base',
      titulo: 'Produção por base operacional',
      tipo: 'doughnut',
      rotulos: bases.map((b) => b.rotulo),
      series: [{ name: 'Produção', values: bases.map((b) => b.valor) }],
      formato: 'currency',
      altura: 240
    });
  }

  const kpis: KpiAnalise[] = [
    { rotulo: 'Produção', valor: moeda(r.producao), tom: 'brand', icone: 'pi pi-wallet', detalhe: r.variacaoPercentual != null ? `${variacao(r.variacaoPercentual)} vs. anterior` : undefined },
    { rotulo: 'Período anterior', valor: moedaCompacta(r.producaoPeriodoAnterior), icone: 'pi pi-history' },
    { rotulo: 'Notas', valor: r.notas.toLocaleString('pt-BR'), icone: 'pi pi-file-check', detalhe: `${moedaCompacta(r.valorMedioNota)} por nota` },
    { rotulo: 'Equipes que lançaram', valor: `${r.equipesComProducao} de ${r.equipesAtivas}`, icone: 'pi pi-users', tom: paradas > 0 ? 'warning' : 'success' },
    { rotulo: 'Média por dia com produção', valor: diasComProducao ? moedaCompacta(r.producao / diasComProducao) : '—', icone: 'pi pi-calendar', detalhe: plural(diasComProducao, 'dia com notas', 'dias com notas') }
  ];

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Produção no período',
    titulo: `${moeda(r.producao)} em ${plural(r.notas, 'nota', 'notas')}`,
    descricao: recorteTexto(c),
    kpis,
    destaques,
    graficos,
    tabelas: [tabelaEquipes(d.equipes, 'Ranking de equipes')],
    origem: origemNotas({
      service: c.service,
      filtros: filtrosNotas(c),
      esperado: { notas: r.notas, valor: r.producao },
      derivados: (notas) => graficosDasNotas(notas),
      verTudo: verTudo(c),
      nomeArquivo: `notas-${d.inicio}-a-${d.fim}`
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'producao', rotulo: 'Produção no período' },
      pergunta: `Analise a produção de ${diaMes(d.inicio)} a ${diaMes(d.fim)} (${moeda(r.producao)} em ${r.notas} notas, ${variacao(r.variacaoPercentual)} vs. o período anterior): quais equipes, dias e tipos de serviço explicam o resultado e onde está a maior oportunidade?`
    }
  };
}

function vistaMeta(a: AlvoAnalise, c: ContextoAnaliseOperacional): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const comMeta = d.equipes.filter((e) => e.metaPeriodo != null);
  const esperado = comMeta.reduce((t, e) => t + (e.metaEsperadaAteAgora ?? 0), 0);
  const contagem = (['META_ATINGIDA', 'NO_RITMO', 'ABAIXO_DO_RITMO', 'SEM_META'] as const).map((s) => ({
    situacao: s,
    total: d.equipes.filter((e) => e.situacao === s).length
  })).filter((s) => s.total > 0);
  const porPercentual = [...comMeta].sort((x, y) => (x.percentualMeta ?? 0) - (y.percentualMeta ?? 0));
  const acumulado = serieAcumulada(d.producaoDiaria);
  const pctProjecao = r.projecaoEquipesComMeta != null && r.metaPeriodo ? (r.projecaoEquipesComMeta / r.metaPeriodo) * 100 : null;

  const destaques: DestaqueAnalise[] = [];
  if (pctProjecao != null && d.diasUteisEncerrados < d.diasUteisPeriodo) {
    destaques.push({
      tom: pctProjecao >= 100 ? 'success' : 'warning',
      icone: 'pi pi-chart-line',
      texto: `No ritmo atual, as equipes com meta fecham o período em ${moedaCompacta(r.projecaoEquipesComMeta)} — ${percentual(pctProjecao)} da meta.`
    });
  }
  if (r.equipesAbaixoDoRitmo > 0) {
    destaques.push({ tom: 'warning', icone: 'pi pi-flag', texto: `${plural(r.equipesAbaixoDoRitmo, 'equipe está abaixo', 'equipes estão abaixo')} do ritmo esperado até ontem.` });
  }
  if (r.equipesComMeta < r.equipesAtivas) {
    destaques.push({ tom: 'neutral', icone: 'pi pi-info-circle', texto: `${plural(r.equipesAtivas - r.equipesComMeta, 'equipe não tem', 'equipes não têm')} meta cadastrada e fica fora do percentual.` });
  }

  const colunas: CesDataTableColumn<EquipeOperacional>[] = [
    { key: 'codigo', header: 'Equipe', priority: 1 },
    { key: 'situacao', header: 'Situação', priority: 1, value: (e) => SITUACAO_META[e.situacao].rotulo },
    { key: 'metaMensal', header: 'Meta do ciclo/mês', type: 'currency', priority: 3 },
    { key: 'metaPeriodo', header: 'Meta do período', type: 'currency', priority: 2 },
    { key: 'metaEsperadaAteAgora', header: 'Esperado até ontem', type: 'currency', priority: 2 },
    { key: 'producao', header: 'Produção', type: 'currency', priority: 1 },
    { key: 'percentualMeta', header: '% da meta', type: 'percent', priority: 1 },
    { key: 'falta', header: 'Falta', type: 'currency', priority: 2, value: (e) => Math.max(0, (e.metaPeriodo ?? 0) - e.producao) },
    { key: 'projecao', header: 'Projeção', type: 'currency', priority: 3 }
  ];

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Meta das equipes',
    titulo: r.percentualMeta != null ? `${percentual(r.percentualMeta)} da meta do período` : 'Sem metas cadastradas',
    descricao: recorteTexto(c),
    kpis: [
      { rotulo: 'Meta atingida', valor: percentual(r.percentualMeta), tom: tomDaMeta(r.percentualMeta), icone: 'pi pi-flag' },
      { rotulo: 'Meta do período', valor: moedaCompacta(r.metaPeriodo), icone: 'pi pi-bullseye', detalhe: `${d.diasUteisPeriodo} dias úteis` },
      { rotulo: 'Produção (com meta)', valor: moedaCompacta(r.producaoEquipesComMeta), icone: 'pi pi-wallet' },
      { rotulo: 'Esperado até ontem', valor: moedaCompacta(esperado), icone: 'pi pi-calendar-clock', detalhe: `${d.diasUteisEncerrados} de ${d.diasUteisPeriodo} dias úteis encerrados`, tom: r.producaoEquipesComMeta >= esperado ? 'success' : 'warning' },
      { rotulo: 'Projeção', valor: moedaCompacta(r.projecaoEquipesComMeta), icone: 'pi pi-chart-line', detalhe: pctProjecao != null ? `${percentual(pctProjecao)} da meta` : undefined },
      { rotulo: 'Equipes com meta', valor: `${r.equipesComMeta} de ${r.equipesAtivas}`, icone: 'pi pi-users' }
    ],
    destaques,
    graficos: [
      {
        id: 'situacao-meta',
        titulo: 'Situação das equipes',
        descricao: 'Clique em "Abaixo do ritmo" para ver quem precisa de atenção.',
        tipo: 'doughnut',
        rotulos: contagem.map((s) => SITUACAO_META[s.situacao].rotulo),
        series: [{ name: 'Equipes', values: contagem.map((s) => s.total) }],
        formato: 'integer',
        altura: 240,
        aoSelecionar: (i) => (contagem[i].situacao === 'ABAIXO_DO_RITMO' ? navegar('kpi', 'ritmo') : null),
        rotuloSelecao: 'Ver equipes'
      },
      {
        id: 'percentual-por-equipe',
        titulo: '% da meta por equipe',
        descricao: 'Da menor para a maior. Clique para analisar a equipe.',
        tipo: 'bar-horizontal',
        rotulos: porPercentual.map((e) => e.codigo),
        series: [{ name: '% da meta', values: porPercentual.map((e) => e.percentualMeta) }],
        formato: 'number',
        altura: alturaBarras(porPercentual.length),
        aoSelecionar: (i) => navegar('equipe', porPercentual[i].idEquipe),
        rotuloSelecao: 'Analisar equipe'
      },
      {
        id: 'acumulado',
        titulo: 'Produção acumulada × meta acumulada',
        descricao: 'A distância entre as linhas é o quanto falta (ou sobra). Clique num dia para analisá-lo.',
        tipo: 'line',
        rotulos: acumulado.rotulos,
        series: acumulado.series,
        formato: 'currency',
        altura: 260,
        inteiro: true,
        aoSelecionar: (i) => navegar('dia', d.producaoDiaria[i].data),
        rotuloSelecao: 'Analisar dia'
      }
    ],
    tabelas: [{
      id: 'equipes-meta',
      titulo: 'Metas por equipe',
      icone: 'pi pi-flag',
      descricao: 'Meta mensal do cadastro da equipe, proporcional aos dias úteis do período.',
      colunas,
      linhas: [...d.equipes].sort((x, y) => (x.percentualMeta ?? Infinity) - (y.percentualMeta ?? Infinity)),
      chaveLinha: (e: EquipeOperacional) => e.idEquipe,
      rotuloLinha: (e: EquipeOperacional) => `Equipe ${e.codigo}`,
      aoAtivar: (e: EquipeOperacional) => navegar('equipe', e.idEquipe),
      rotuloAcao: 'Analisar',
      nomeArquivo: 'metas-equipes'
    }],
    copiloto: {
      foco: { tipo: 'metrica', id: 'percentualMeta', rotulo: 'Meta das equipes' },
      pergunta: `As equipes com meta atingiram ${percentual(r.percentualMeta)} da meta do período e a projeção é ${moedaCompacta(r.projecaoEquipesComMeta)}. Quais equipes mais pesam no resultado e o que cada uma precisa fazer por dia útil para fechar a meta?`
    }
  };
}

function vistaNotas(a: AlvoAnalise, c: ContextoAnaliseOperacional): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const maiorDia = [...d.producaoDiaria].sort((x, y) => y.notas - x.notas)[0];
  const semTipo = d.tiposServico.find((t) => !t.idTipoServico);
  const comNotas = d.equipes.filter((e) => e.notas > 0).sort((x, y) => y.valorMedioNota - x.valorMedioNota);
  const tipos = [...d.tiposServico].sort((x, y) => y.notas - x.notas);

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Notas lançadas',
    titulo: `${plural(r.notas, 'nota concluída', 'notas concluídas')}`,
    descricao: recorteTexto(c),
    kpis: [
      { rotulo: 'Notas', valor: r.notas.toLocaleString('pt-BR'), tom: 'brand', icone: 'pi pi-file-check' },
      { rotulo: 'Valor médio por nota', valor: moeda(r.valorMedioNota), icone: 'pi pi-tag' },
      { rotulo: 'Notas por dia útil', valor: d.diasUteisEncerrados ? (r.notas / Math.max(1, d.diasUteisEncerrados)).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) : '—', icone: 'pi pi-calendar', detalhe: 'nos dias úteis encerrados' },
      { rotulo: 'Dia com mais notas', valor: maiorDia && maiorDia.notas ? diaMes(maiorDia.data) : '—', icone: 'pi pi-star', detalhe: maiorDia && maiorDia.notas ? plural(maiorDia.notas, 'nota', 'notas') : undefined },
      { rotulo: 'Sem tipo de serviço', valor: (semTipo?.notas ?? 0).toLocaleString('pt-BR'), icone: 'pi pi-question-circle', tom: semTipo?.notas ? 'warning' : 'success', detalhe: semTipo ? `${percentual(semTipo.percentualNotas)} das notas` : 'todas classificadas' }
    ],
    graficos: [
      {
        id: 'notas-por-dia',
        titulo: 'Notas por dia',
        descricao: 'Clique num dia para ver as notas dele.',
        tipo: 'bar',
        rotulos: d.producaoDiaria.map((p) => diaMes(p.data)),
        series: [{ name: 'Notas', values: d.producaoDiaria.map((p) => p.notas) }],
        formato: 'integer',
        altura: 240,
        inteiro: true,
        aoSelecionar: (i) => navegar('dia', d.producaoDiaria[i].data),
        rotuloSelecao: 'Analisar dia'
      },
      {
        id: 'valor-medio-por-equipe',
        titulo: 'Valor médio por nota, por equipe',
        descricao: 'Equipes com notas mais valiosas no topo.',
        tipo: 'bar-horizontal',
        rotulos: comNotas.map((e) => e.codigo),
        series: [{ name: 'Valor médio', values: comNotas.map((e) => e.valorMedioNota) }],
        formato: 'currency',
        altura: alturaBarras(comNotas.length),
        aoSelecionar: (i) => navegar('equipe', comNotas[i].idEquipe),
        rotuloSelecao: 'Analisar equipe'
      },
      {
        id: 'notas-por-tipo',
        titulo: 'Notas por tipo de serviço',
        tipo: 'bar-horizontal',
        rotulos: tipos.map((t) => t.descricao),
        series: [{ name: 'Notas', values: tipos.map((t) => t.notas) }],
        formato: 'integer',
        altura: alturaBarras(tipos.length),
        aoSelecionar: (i) => navegar('tipo', chaveTipo(tipos[i])),
        rotuloSelecao: 'Analisar tipo'
      }
    ],
    origem: origemNotas({
      service: c.service,
      filtros: filtrosNotas(c),
      esperado: { notas: r.notas, valor: r.producao },
      derivados: (notas) => graficosDasNotas(notas),
      verTudo: verTudo(c),
      nomeArquivo: `notas-${d.inicio}-a-${d.fim}`
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'notas', rotulo: 'Notas lançadas' },
      pergunta: `Foram ${r.notas} notas com valor médio de ${moeda(r.valorMedioNota)}. Como o volume e o valor médio por nota variam entre equipes e tipos de serviço, e há notas sem tipo que precisam de atenção?`
    }
  };
}

function vistaRitmo(a: AlvoAnalise, c: ContextoAnaliseOperacional): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const diasRestantes = Math.max(0, d.diasUteisPeriodo - d.diasUteisEncerrados);
  const comMeta = d.equipes.filter((e) => e.metaPeriodo != null);
  const distancia = comMeta
    .map((e) => ({ e, valor: e.producao - (e.metaEsperadaAteAgora ?? 0) }))
    .sort((x, y) => x.valor - y.valor);
  const atencao = d.equipes.filter((e) => e.situacao === 'ABAIXO_DO_RITMO' || e.notas === 0);
  const abaixo = d.equipes.filter((e) => e.situacao === 'ABAIXO_DO_RITMO');
  const deficit = abaixo.reduce((t, e) => t + Math.max(0, (e.metaEsperadaAteAgora ?? 0) - e.producao), 0);
  const necessarioPorDia = (e: EquipeOperacional) =>
    e.metaPeriodo != null && diasRestantes > 0 ? Math.max(0, e.metaPeriodo - e.producao) / diasRestantes : null;

  const destaques: DestaqueAnalise[] = distancia.slice(0, 3).filter((x) => x.valor < 0).map((x) => ({
    tom: 'warning' as const,
    icone: 'pi pi-arrow-down-right',
    texto: `${x.e.codigo} está ${moedaCompacta(-x.valor)} abaixo do esperado` +
      (necessarioPorDia(x.e) != null ? `; precisa de ${moedaCompacta(necessarioPorDia(x.e))} por dia útil restante para fechar a meta.` : '.')
  }));
  if (!destaques.length) destaques.push({ tom: 'success', icone: 'pi pi-check-circle', texto: 'Nenhuma equipe com meta está abaixo do ritmo esperado.' });

  const colunas: CesDataTableColumn<EquipeOperacional>[] = [
    { key: 'codigo', header: 'Equipe', priority: 1 },
    { key: 'situacao', header: 'Situação', priority: 1, value: (e) => (e.notas === 0 ? 'Sem lançamento' : SITUACAO_META[e.situacao].rotulo) },
    { key: 'producao', header: 'Produção', type: 'currency', priority: 1 },
    { key: 'metaEsperadaAteAgora', header: 'Esperado até ontem', type: 'currency', priority: 2 },
    { key: 'deficit', header: 'Abaixo do esperado', type: 'currency', priority: 1, value: (e) => Math.max(0, (e.metaEsperadaAteAgora ?? 0) - e.producao) },
    { key: 'necessario', header: 'Necessário por dia útil', type: 'currency', priority: 2, value: (e) => necessarioPorDia(e) },
    { key: 'diasComTurno', header: 'Dias com turno', type: 'integer', priority: 3 },
    { key: 'diasComProducao', header: 'Dias com produção', type: 'integer', priority: 3 },
    { key: 'ultimoLancamento', header: 'Último lançamento', priority: 3, value: (e) => e.ultimoLancamento ?? '', display: (e) => diaHora(e.ultimoLancamento) }
  ];

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Ritmo da meta',
    titulo: r.equipesComMeta ? `${r.equipesAbaixoDoRitmo} de ${r.equipesComMeta} equipes abaixo do ritmo` : 'Sem equipes com meta',
    descricao: `${recorteTexto(c)} · ${plural(diasRestantes, 'dia útil restante', 'dias úteis restantes')}`,
    kpis: [
      { rotulo: 'Abaixo do ritmo', valor: `${r.equipesAbaixoDoRitmo} de ${r.equipesComMeta}`, icone: 'pi pi-exclamation-triangle', tom: r.equipesAbaixoDoRitmo ? 'warning' : 'success' },
      { rotulo: 'Déficit somado', valor: moedaCompacta(deficit), icone: 'pi pi-arrow-down', detalhe: 'contra o esperado até ontem', tom: deficit > 0 ? 'warning' : 'success' },
      { rotulo: 'Sem lançamento', valor: (r.equipesAtivas - r.equipesComProducao).toLocaleString('pt-BR'), icone: 'pi pi-ban', detalhe: 'equipes sem nota no período' },
      { rotulo: 'Dias úteis', valor: `${d.diasUteisEncerrados} de ${d.diasUteisPeriodo}`, icone: 'pi pi-calendar', detalhe: 'encerrados' }
    ],
    destaques,
    graficos: [
      {
        id: 'distancia-esperado',
        titulo: 'Distância do esperado até ontem',
        descricao: 'Negativo: abaixo do ritmo. Clique para analisar a equipe.',
        tipo: 'bar-horizontal',
        rotulos: distancia.map((x) => x.e.codigo),
        series: [{ name: 'Produção − esperado', values: distancia.map((x) => Math.round(x.valor * 100) / 100) }],
        formato: 'currency',
        altura: alturaBarras(distancia.length),
        aoSelecionar: (i) => navegar('equipe', distancia[i].e.idEquipe),
        rotuloSelecao: 'Analisar equipe'
      },
      {
        id: 'dias-turno-producao',
        titulo: 'Dias com turno × dias com produção',
        descricao: 'Equipes de atenção: turno aberto sem nota lançada aparece como diferença entre as barras.',
        tipo: 'bar-horizontal',
        rotulos: atencao.map((e) => e.codigo),
        series: [
          { name: 'Dias com turno', values: atencao.map((e) => e.diasComTurno) },
          { name: 'Dias com produção', values: atencao.map((e) => e.diasComProducao) }
        ],
        formato: 'integer',
        altura: alturaBarras(atencao.length),
        aoSelecionar: (i) => navegar('equipe', atencao[i].idEquipe),
        rotuloSelecao: 'Analisar equipe'
      }
    ],
    tabelas: [{
      id: 'equipes-atencao',
      titulo: 'Equipes de atenção',
      icone: 'pi pi-exclamation-triangle',
      descricao: 'Abaixo do ritmo ou sem nenhuma nota no período.',
      colunas,
      linhas: atencao,
      chaveLinha: (e: EquipeOperacional) => e.idEquipe,
      rotuloLinha: (e: EquipeOperacional) => `Equipe ${e.codigo}`,
      aoAtivar: (e: EquipeOperacional) => navegar('equipe', e.idEquipe),
      rotuloAcao: 'Analisar',
      nomeArquivo: 'equipes-atencao',
      vazio: 'Nenhuma equipe abaixo do ritmo'
    }],
    copiloto: {
      foco: { tipo: 'metrica', id: 'equipesAbaixoDoRitmo', rotulo: 'Equipes abaixo do ritmo' },
      pergunta: `${r.equipesAbaixoDoRitmo} equipes estão abaixo do ritmo (déficit de ${moedaCompacta(deficit)} contra o esperado). Para cada uma, o que explica a diferença — dias sem turno, poucas notas por dia ou valor médio baixo — e quanto precisa produzir por dia útil restante?`
    }
  };
}

// ------------------------------------------------------------------ pontos de gráfico

function vistaDia(a: AlvoAnalise, c: ContextoAnaliseOperacional): VistaAnalise | null {
  const { d } = c;
  const indice = d.producaoDiaria.findIndex((p) => p.data === a.value);
  if (indice < 0) return null;
  const dia = d.producaoDiaria[indice];
  const doDia = d.equipes
    .map((e) => ({ e, p: e.producaoDiaria.find((p) => p.data === dia.data) }))
    .filter((x) => x.p && (x.p.notas > 0 || (x.p.meta ?? 0) > 0))
    .sort((x, y) => (y.p!.valor - x.p!.valor) || x.e.codigo.localeCompare(y.e.codigo, 'pt-BR'));
  const comProducao = d.producaoDiaria.filter((p) => p.notas > 0);
  const media = comProducao.length ? comProducao.reduce((t, p) => t + p.valor, 0) / comProducao.length : 0;
  const pctMeta = dia.meta ? (dia.valor / dia.meta) * 100 : null;
  const anterior = d.producaoDiaria[indice - 1];
  const proximo = d.producaoDiaria[indice + 1];
  const lancaram = doDia.filter((x) => x.p!.notas > 0).length;

  const destaques: DestaqueAnalise[] = [];
  if (media > 0 && dia.notas > 0) {
    const dif = ((dia.valor - media) / media) * 100;
    destaques.push({
      tom: dif >= 0 ? 'success' : 'warning',
      icone: dif >= 0 ? 'pi pi-arrow-up-right' : 'pi pi-arrow-down-right',
      texto: `${variacao(dif)} em relação à média dos dias com produção no período (${moedaCompacta(media)}).`
    });
  }
  if (dia.meta === 0 || dia.meta == null) {
    destaques.push({ tom: 'neutral', icone: 'pi pi-calendar-times', texto: dia.meta === 0 ? 'Dia não útil: não há meta diária.' : 'Nenhuma equipe do recorte tem meta cadastrada.' });
  }
  if (!dia.notas) destaques.push({ tom: 'warning', icone: 'pi pi-inbox', texto: 'Nenhuma nota concluída neste dia.' });

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Dia',
    titulo: diaPorExtenso(dia.data).replace(/^./, (l) => l.toLocaleUpperCase('pt-BR')),
    descricao: `${c.nomeBase ?? 'Todas as bases'} · ${c.nomeProcesso ?? 'todos os processos'}`,
    anterior: anterior ? { alvo: alvo('dia', anterior.data), rotulo: diaMes(anterior.data) } : undefined,
    proximo: proximo ? { alvo: alvo('dia', proximo.data), rotulo: diaMes(proximo.data) } : undefined,
    kpis: [
      { rotulo: 'Produção do dia', valor: moeda(dia.valor), tom: 'brand', icone: 'pi pi-wallet' },
      { rotulo: 'Meta do dia', valor: dia.meta ? moedaCompacta(dia.meta) : '—', icone: 'pi pi-flag', detalhe: pctMeta != null ? `${percentual(pctMeta)} atingido` : undefined, tom: pctMeta == null ? 'neutral' : tomDaMeta(pctMeta) },
      { rotulo: 'Notas', valor: dia.notas.toLocaleString('pt-BR'), icone: 'pi pi-file-check', detalhe: dia.notas ? `${moedaCompacta(dia.valor / dia.notas)} por nota` : undefined },
      { rotulo: 'Equipes que lançaram', valor: `${lancaram} de ${d.equipes.length}`, icone: 'pi pi-users' },
      { rotulo: 'Média dos dias', valor: moedaCompacta(media), icone: 'pi pi-chart-line', detalhe: 'dias com produção no período' }
    ],
    destaques,
    graficos: doDia.length ? [{
      id: 'equipes-no-dia',
      titulo: 'Produção por equipe no dia',
      descricao: 'Clique numa equipe para filtrar as notas dela neste dia.',
      tipo: 'bar-horizontal',
      rotulos: doDia.map((x) => x.e.codigo),
      series: [
        { name: 'Produção', values: doDia.map((x) => x.p!.valor) },
        ...(doDia.some((x) => (x.p!.meta ?? 0) > 0) ? [{ name: 'Meta do dia', values: doDia.map((x) => x.p!.meta) }] : [])
      ],
      formato: 'currency',
      altura: alturaBarras(doDia.length),
      inteiro: true,
      aoSelecionar: (i) => ({
        tipo: 'filtrar',
        filtro: { chave: `equipe-${doDia[i].e.idEquipe}`, rotulo: `Equipe ${doDia[i].e.codigo}`, aceita: (n: NotaDoRecorte) => n.idEquipe === doDia[i].e.idEquipe }
      }),
      rotuloSelecao: 'Ver notas da equipe'
    }] : [],
    origem: origemNotas({
      service: c.service,
      filtros: { ...filtrosNotas(c), inicio: dia.data, fim: dia.data },
      esperado: { notas: dia.notas, valor: dia.valor },
      derivados: (notas) => graficosDasNotas(notas),
      verTudo: verTudo(c, { inicio: dia.data, fim: dia.data }),
      nomeArquivo: `notas-${dia.data}`
    }),
    links: [{
      rotulo: 'Abrir no dashboard de turnos',
      icone: 'pi pi-clock',
      rota: '/execucao/dashboard-turnos',
      queryParams: { dia: dia.data, base: c.filtros.idBaseOperacional ?? null, processo: c.filtros.idProcesso ?? null }
    }],
    copiloto: {
      foco: { tipo: 'grafico', id: 'producaoDiaria', rotulo: `Produção de ${diaMes(dia.data)}` },
      pergunta: `Em ${diaPorExtenso(dia.data)} a produção foi ${moeda(dia.valor)} em ${dia.notas} notas${dia.meta ? ` (meta do dia ${moedaCompacta(dia.meta)})` : ''}. Quais equipes puxaram o resultado para cima ou para baixo e o que explica a diferença para a média do período?`
    }
  };
}

function vistaMes(a: AlvoAnalise, c: ContextoAnaliseOperacional): VistaAnalise | null {
  const { d } = c;
  const dias = d.producaoDiaria.filter((p) => p.data.startsWith(a.value));
  if (!dias.length) return null;
  const [ano, mes] = a.value.split('-').map(Number);
  const valor = dias.reduce((t, p) => t + p.valor, 0);
  const notas = dias.reduce((t, p) => t + p.notas, 0);
  const temMeta = dias.some((p) => p.meta != null);
  const meta = dias.reduce((t, p) => t + (p.meta ?? 0), 0);
  const equipes = d.equipes
    .map((e) => ({ e, valor: e.producaoDiaria.filter((p) => p.data.startsWith(a.value)).reduce((t, p) => t + p.valor, 0) }))
    .filter((x) => x.valor > 0)
    .sort((x, y) => y.valor - x.valor);
  const meses = [...new Set(d.producaoDiaria.map((p) => p.data.slice(0, 7)))].sort();
  const i = meses.indexOf(a.value);
  const nomeMes = (m: string) => `${MESES[Number(m.slice(5, 7)) - 1]}/${m.slice(2, 4)}`;

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Mês',
    titulo: `${MESES[mes - 1].replace(/^./, (l) => l.toLocaleUpperCase('pt-BR'))} de ${ano}`,
    descricao: `${diaMes(dias[0].data)} a ${diaMes(dias[dias.length - 1].data)} · ${c.nomeBase ?? 'todas as bases'} · ${c.nomeProcesso ?? 'todos os processos'}`,
    anterior: i > 0 ? { alvo: alvo('mes', meses[i - 1]), rotulo: nomeMes(meses[i - 1]) } : undefined,
    proximo: i < meses.length - 1 ? { alvo: alvo('mes', meses[i + 1]), rotulo: nomeMes(meses[i + 1]) } : undefined,
    kpis: [
      { rotulo: 'Produção', valor: moeda(valor), tom: 'brand', icone: 'pi pi-wallet' },
      { rotulo: 'Meta', valor: temMeta ? moedaCompacta(meta) : '—', icone: 'pi pi-flag', detalhe: temMeta && meta ? `${percentual((valor / meta) * 100)} atingido` : undefined, tom: temMeta && meta ? tomDaMeta((valor / meta) * 100) : 'neutral' },
      { rotulo: 'Notas', valor: notas.toLocaleString('pt-BR'), icone: 'pi pi-file-check', detalhe: notas ? `${moedaCompacta(valor / notas)} por nota` : undefined },
      { rotulo: 'Equipes que lançaram', valor: equipes.length.toLocaleString('pt-BR'), icone: 'pi pi-users' }
    ],
    graficos: [
      {
        id: 'dias-do-mes',
        titulo: 'Produção por dia do mês',
        descricao: 'Clique num dia para analisá-lo.',
        tipo: 'bar',
        rotulos: dias.map((p) => diaMes(p.data)),
        series: [
          { name: 'Produção', values: dias.map((p) => p.valor) },
          ...(temMeta ? [{ name: 'Meta do dia', values: dias.map((p) => p.meta) }] : [])
        ],
        formato: 'currency',
        altura: 260,
        inteiro: true,
        aoSelecionar: (k) => navegar('dia', dias[k].data),
        rotuloSelecao: 'Analisar dia'
      },
      {
        id: 'equipes-do-mes',
        titulo: 'Produção por equipe no mês',
        tipo: 'bar-horizontal',
        rotulos: equipes.map((x) => x.e.codigo),
        series: [{ name: 'Produção', values: equipes.map((x) => Math.round(x.valor * 100) / 100) }],
        formato: 'currency',
        altura: alturaBarras(equipes.length),
        inteiro: true,
        aoSelecionar: (k) => navegar('equipe', equipes[k].e.idEquipe),
        rotuloSelecao: 'Analisar equipe'
      }
    ],
    origem: origemNotas({
      service: c.service,
      filtros: { ...filtrosNotas(c), inicio: dias[0].data, fim: dias[dias.length - 1].data },
      esperado: { notas, valor },
      derivados: (n) => graficosDasNotas(n),
      verTudo: verTudo(c, { inicio: dias[0].data, fim: dias[dias.length - 1].data }),
      nomeArquivo: `notas-${a.value}`
    }),
    copiloto: {
      foco: { tipo: 'grafico', id: 'producaoDiaria', rotulo: `Produção de ${nomeMes(a.value)}` },
      pergunta: `Em ${MESES[mes - 1]} de ${ano} a produção foi ${moeda(valor)} em ${notas} notas. Como o mês evoluiu semana a semana e quais equipes mais contribuíram?`
    }
  };
}

function vistaEquipe(a: AlvoAnalise, c: ContextoAnaliseOperacional): VistaAnalise | null {
  const { d } = c;
  const ordem = [...d.equipes].sort((x, y) => x.codigo.localeCompare(y.codigo, 'pt-BR'));
  const i = ordem.findIndex((e) => e.idEquipe === a.value);
  if (i < 0) return null;
  const e = ordem[i];
  const diasRestantes = Math.max(0, d.diasUteisPeriodo - d.diasUteisEncerrados);
  const necessario = e.metaPeriodo != null && diasRestantes > 0 ? Math.max(0, e.metaPeriodo - e.producao) / diasRestantes : null;
  const acumulado = serieAcumulada(e.producaoDiaria);
  const temMeta = e.producaoDiaria.some((p) => p.meta != null);
  const situacao = SITUACAO_META[e.situacao];
  const filtroDia = (data: string) => ({
    tipo: 'filtrar' as const,
    filtro: { chave: `dia-${data}`, rotulo: `Lançadas em ${diaMes(data)}`, aceita: (n: NotaDoRecorte) => diaDe(n.dataLancamento) === data }
  });

  const destaques: DestaqueAnalise[] = [{ tom: situacao.tom, icone: situacao.icone, texto: `${situacao.rotulo}.` + (e.metaPeriodo != null ? ` ${moedaCompacta(e.producao)} de ${moedaCompacta(e.metaPeriodo)} (${percentual(e.percentualMeta)}).` : '') }];
  if (necessario != null && necessario > 0) {
    destaques.push({ tom: 'info', icone: 'pi pi-calculator', texto: `Para fechar a meta, precisa de ${moedaCompacta(necessario)} por dia útil nos ${diasRestantes} dias úteis restantes (média atual: ${moedaCompacta(e.producaoMediaPorDia)} por dia com produção).` });
  }
  if (e.diasComTurno > e.diasComProducao) {
    destaques.push({ tom: 'warning', icone: 'pi pi-clock', texto: `${plural(e.diasComTurno - e.diasComProducao, 'dia', 'dias')} com turno aberto e nenhuma nota concluída.` });
  }

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Equipe',
    titulo: e.codigo,
    descricao: [e.baseOperacional, e.processo, e.tipoEquipe, e.placa].filter(Boolean).join(' · ') + ` · ${diaMes(d.inicio)} a ${diaMes(d.fim)}`,
    anterior: i > 0 ? { alvo: alvo('equipe', ordem[i - 1].idEquipe), rotulo: ordem[i - 1].codigo } : undefined,
    proximo: i < ordem.length - 1 ? { alvo: alvo('equipe', ordem[i + 1].idEquipe), rotulo: ordem[i + 1].codigo } : undefined,
    kpis: [
      { rotulo: 'Produção', valor: moeda(e.producao), tom: 'brand', icone: 'pi pi-wallet' },
      { rotulo: 'Meta do período', valor: e.metaPeriodo != null ? percentual(e.percentualMeta) : 'Sem meta', icone: 'pi pi-flag', tom: tomDaMeta(e.percentualMeta), detalhe: e.metaPeriodo != null ? `de ${moedaCompacta(e.metaPeriodo)}` : undefined },
      { rotulo: 'Notas', valor: e.notas.toLocaleString('pt-BR'), icone: 'pi pi-file-check', detalhe: e.notas ? `${moedaCompacta(e.valorMedioNota)} por nota` : undefined },
      { rotulo: 'Dias com turno', valor: e.diasComTurno.toLocaleString('pt-BR'), icone: 'pi pi-calendar', detalhe: `${e.diasComProducao} com produção` },
      { rotulo: 'Média por dia', valor: e.diasComProducao ? moedaCompacta(e.producaoMediaPorDia) : '—', icone: 'pi pi-chart-bar', detalhe: 'dias com produção' },
      { rotulo: 'Projeção', valor: moedaCompacta(e.projecao), icone: 'pi pi-chart-line', detalhe: e.ultimoLancamento ? `último lançamento ${diaHora(e.ultimoLancamento)}` : 'sem lançamentos' }
    ],
    destaques,
    graficos: [
      {
        id: 'equipe-por-dia',
        titulo: 'Produção por dia × meta do dia',
        descricao: 'Clique num dia para ver as notas dele.',
        tipo: 'bar',
        rotulos: e.producaoDiaria.map((p) => diaMes(p.data)),
        series: [
          { name: 'Produção', values: e.producaoDiaria.map((p) => p.valor) },
          ...(temMeta ? [{ name: 'Meta do dia', values: e.producaoDiaria.map((p) => p.meta) }] : [])
        ],
        formato: 'currency',
        altura: 240,
        inteiro: true,
        aoSelecionar: (k) => filtroDia(e.producaoDiaria[k].data),
        rotuloSelecao: 'Ver notas do dia'
      },
      {
        id: 'equipe-acumulado',
        titulo: 'Acumulado × meta',
        tipo: 'line',
        rotulos: acumulado.rotulos,
        series: acumulado.series,
        formato: 'currency',
        altura: 240,
        aoSelecionar: (k) => filtroDia(e.producaoDiaria[k].data),
        rotuloSelecao: 'Ver notas do dia'
      },
      ...(e.tiposServico.length ? [{
        id: 'equipe-tipos',
        titulo: 'Notas por tipo de serviço',
        descricao: 'Clique numa fatia para filtrar as notas.',
        tipo: 'doughnut' as const,
        rotulos: e.tiposServico.map((t) => t.descricao),
        series: [{ name: 'Notas', values: e.tiposServico.map((t) => t.notas) }],
        formato: 'integer' as const,
        altura: 240,
        aoSelecionar: (k: number) => ({
          tipo: 'filtrar' as const,
          filtro: { chave: `tipo-${chaveTipo(e.tiposServico[k])}`, rotulo: e.tiposServico[k].descricao, aceita: (n: NotaDoRecorte) => (n.idTipoServico ?? SEM_TIPO) === chaveTipo(e.tiposServico[k]) }
        }),
        rotuloSelecao: 'Ver notas do tipo'
      }] : [])
    ],
    origem: origemNotas({
      service: c.service,
      filtros: { inicio: d.inicio, fim: d.fim, idEquipe: e.idEquipe },
      esperado: { notas: e.notas, valor: e.producao },
      derivados: (n) => graficosDasNotas(n),
      verTudo: verTudo(c, { idEquipe: e.idEquipe }),
      mostrarEquipe: false,
      nomeArquivo: `notas-${e.codigo}-${d.inicio}-a-${d.fim}`
    }),
    links: [{
      rotulo: 'Turnos da equipe',
      icone: 'pi pi-clock',
      rota: '/execucao/turnos',
      queryParams: { equipe: e.idEquipe, aberturaDe: d.inicio, aberturaAte: d.fim, res: '1' }
    }],
    copiloto: {
      foco: { tipo: 'secao', id: `equipe.${e.codigo}`, rotulo: `Equipe ${e.codigo}` },
      pergunta: `Analise a equipe ${e.codigo} de ${diaMes(d.inicio)} a ${diaMes(d.fim)}: ${moeda(e.producao)} em ${e.notas} notas, ${e.diasComTurno} dias com turno e ${e.diasComProducao} com produção${e.metaPeriodo != null ? `, ${percentual(e.percentualMeta)} da meta` : ''}. O que explica o desempenho e o que a equipe precisa mudar?`
    }
  };
}

function vistaTipo(a: AlvoAnalise, c: ContextoAnaliseOperacional): VistaAnalise | null {
  const { d } = c;
  const i = d.tiposServico.findIndex((t) => chaveTipo(t) === a.value);
  if (i < 0) return null;
  const t = d.tiposServico[i];
  const porEquipe = d.equipes
    .map((e) => ({ e, t: e.tiposServico.find((x) => chaveTipo(x) === a.value) }))
    .filter((x) => x.t && x.t.notas > 0)
    .sort((x, y) => y.t!.producao - x.t!.producao);
  const dif = d.resumo.valorMedioNota ? ((t.valorMedioNota - d.resumo.valorMedioNota) / d.resumo.valorMedioNota) * 100 : null;
  const anterior = d.tiposServico[i - 1];
  const proximo = d.tiposServico[i + 1];

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Tipo de serviço',
    titulo: t.codigo ? `${t.codigo} · ${t.descricao}` : t.descricao,
    descricao: recorteTexto(c),
    anterior: anterior ? { alvo: alvo('tipo', chaveTipo(anterior)), rotulo: anterior.descricao } : undefined,
    proximo: proximo ? { alvo: alvo('tipo', chaveTipo(proximo)), rotulo: proximo.descricao } : undefined,
    kpis: [
      { rotulo: 'Notas', valor: t.notas.toLocaleString('pt-BR'), tom: 'brand', icone: 'pi pi-file-check', detalhe: `${percentual(t.percentualNotas)} das notas` },
      { rotulo: 'Produção', valor: moeda(t.producao), icone: 'pi pi-wallet', detalhe: `${percentual(fatia(t.producao, d.resumo.producao))} da produção` },
      { rotulo: 'Valor médio por nota', valor: moeda(t.valorMedioNota), icone: 'pi pi-tag', detalhe: dif != null ? `${variacao(dif)} vs. média geral` : undefined, tom: dif == null ? 'neutral' : dif >= 0 ? 'success' : 'warning' },
      { rotulo: 'Equipes', valor: porEquipe.length.toLocaleString('pt-BR'), icone: 'pi pi-users', detalhe: 'lançaram este tipo' }
    ],
    destaques: !t.idTipoServico ? [{ tom: 'warning', icone: 'pi pi-info-circle', texto: 'Notas lançadas antes do cadastro de tipos de serviço ou por versões antigas do aplicativo.' }] : [],
    graficos: [{
      id: 'tipo-por-equipe',
      titulo: 'Por equipe',
      descricao: 'Clique numa equipe para filtrar as notas dela.',
      tipo: 'bar-horizontal',
      rotulos: porEquipe.map((x) => x.e.codigo),
      series: [{ name: 'Produção', values: porEquipe.map((x) => x.t!.producao) }],
      formato: 'currency',
      altura: alturaBarras(porEquipe.length),
      aoSelecionar: (k) => ({
        tipo: 'filtrar',
        filtro: { chave: `equipe-${porEquipe[k].e.idEquipe}`, rotulo: `Equipe ${porEquipe[k].e.codigo}`, aceita: (n: NotaDoRecorte) => n.idEquipe === porEquipe[k].e.idEquipe }
      }),
      rotuloSelecao: 'Ver notas da equipe'
    }],
    origem: origemNotas({
      service: c.service,
      filtros: { ...filtrosNotas(c), ...(t.idTipoServico ? { idTipoServico: t.idTipoServico } : { semTipoServico: true }) },
      esperado: { notas: t.notas, valor: t.producao },
      derivados: (notas) => [graficoNotasPorDia(notas), ...graficosDasNotas(notas, { faixas: false })],
      verTudo: verTudo(c, { tipo: t.idTipoServico ?? SEM_TIPO }),
      nomeArquivo: `notas-tipo-${t.codigo ?? 'nao-informado'}`
    }),
    copiloto: {
      foco: { tipo: 'tabela', id: 'tiposServico', rotulo: `Tipo de serviço ${t.descricao}` },
      pergunta: `O tipo de serviço "${t.descricao}" teve ${t.notas} notas e ${moeda(t.producao)} (${moeda(t.valorMedioNota)} por nota). Quais equipes mais executam esse tipo e como o valor médio delas se compara?`
    }
  };
}

function graficoNotasPorDia(notas: readonly NotaDoRecorte[]): GraficoAnalise {
  const dias = agrupar(notas, (n) => diaDe(n.dataLancamento), (n) => diaMes(diaDe(n.dataLancamento))).sort((x, y) => x.chave.localeCompare(y.chave));
  return {
    id: 'notas-por-dia-tipo',
    titulo: 'Notas por dia',
    descricao: 'Clique num dia para filtrar as notas.',
    tipo: 'bar',
    rotulos: dias.map((x) => x.rotulo),
    series: [{ name: 'Notas', values: dias.map((x) => x.notas) }],
    formato: 'integer',
    altura: 220,
    aoSelecionar: (k) => ({
      tipo: 'filtrar',
      filtro: { chave: `dia-${dias[k].chave}`, rotulo: `Lançadas em ${dias[k].rotulo}`, aceita: (n: NotaDoRecorte) => diaDe(n.dataLancamento) === dias[k].chave }
    }),
    rotuloSelecao: 'Ver notas do dia'
  };
}

function agruparEquipes(equipes: readonly EquipeOperacional[], chave: (e: EquipeOperacional) => string): { rotulo: string; valor: number }[] {
  const mapa = new Map<string, number>();
  for (const e of equipes) mapa.set(chave(e), (mapa.get(chave(e)) ?? 0) + e.producao);
  return [...mapa.entries()].map(([rotulo, valor]) => ({ rotulo, valor })).sort((x, y) => y.valor - x.valor);
}
