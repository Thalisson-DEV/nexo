import {
  AssistenteContexto,
  CapacidadesAssistente,
  ContextoPainel,
  EntidadePainel,
  FiltroPainel,
  FocoPainel,
  GraficoPainel,
  MetricaPainel,
  PeriodoPainel,
} from '../../../core/models/assistente.models';

/**
 * Dashboard Context Contract do Copiloto contextual.
 *
 * Todo dashboard do Nexo descreve o que está em tela com este contrato (um `computed()` que acompanha
 * filtros e dados). O contexto é **estruturado** e **enxuto**: identidade, período, filtros, KPIs com
 * comparação, agregações visíveis dos gráficos, seleção e datasets semânticos relacionados — nunca a base
 * completa. Detalhes vêm das ferramentas do agente. O contexto é pista, jamais autorização: o backend e as
 * ferramentas continuam aplicando as permissões do usuário.
 */
export interface CopilotoDashboardContext extends ContextoPainel {
  /** Rota do dashboard (ex.: `/almoxarifado/dashboard/solicitacoes`). */
  rota?: string;
  /** Ações rápidas específicas do painel; filtradas pelas capacidades reais do agente. */
  sugestoes?: SugestaoPainel[];
}

/** Capacidades reais do agente das quais uma ação rápida depende. */
export type CapacidadeSugestao =
  | 'dados'
  | 'analise'
  | 'artefato'
  | 'relatorio'
  | 'planilha'
  | 'python'
  | 'web'
  | 'arquivo';

export interface SugestaoPainel {
  rotulo: string;
  pergunta: string;
  icone: string;
  requer?: CapacidadeSugestao[];
}

export type FormatoMetricaPainel = 'numero' | 'inteiro' | 'moeda' | 'percentual' | 'horas' | 'dias';

const MAX_PONTOS_GRAFICO = 40;

// ---------------------------------------------------------------------------------- construtores

export function formatarMetricaPainel(valor: number | null | undefined, formato: FormatoMetricaPainel = 'numero'): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return 'sem dados';
  switch (formato) {
    case 'moeda':
      return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    case 'percentual':
      return `${valor.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`;
    case 'inteiro':
      return Math.round(valor).toLocaleString('pt-BR');
    case 'horas':
      return `${valor.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} h`;
    case 'dias':
      return `${valor.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dias`;
    default:
      return valor.toLocaleString('pt-BR', { maximumFractionDigits: 2 });
  }
}

export interface OpcoesMetricaPainel {
  descricao?: string;
  unidade?: string;
  comparacao?: MetricaPainel['comparacao'];
}

export function metricaPainel(
  id: string,
  rotulo: string,
  valor: number | null | undefined,
  formato: FormatoMetricaPainel = 'numero',
  opcoes: OpcoesMetricaPainel = {},
): MetricaPainel {
  const numero = valor === null || valor === undefined || !Number.isFinite(valor) ? null : valor;
  return {
    id,
    rotulo,
    valor: numero,
    valorFormatado: formatarMetricaPainel(numero, formato),
    ...(opcoes.unidade ? { unidade: opcoes.unidade } : {}),
    ...(opcoes.descricao ? { descricao: opcoes.descricao } : {}),
    ...(opcoes.comparacao ? { comparacao: opcoes.comparacao } : {}),
  };
}

export interface OpcoesGraficoPainel {
  metrica?: string;
  dimensao?: string;
  formato?: FormatoMetricaPainel;
  /** Total real de categorias quando o gráfico já recebe um recorte (ex.: top 10 de 57). */
  totalCategorias?: number;
}

/**
 * Agregações visíveis de um gráfico. `series` aceita uma série simples (números) ou várias séries
 * nomeadas; os pontos são limitados a 40 — o restante continua acessível pelas ferramentas.
 */
export function graficoPainel(
  id: string,
  tipo: string,
  titulo: string,
  rotulos: readonly string[],
  series: readonly (number | null | undefined)[] | readonly { nome: string; valores: readonly (number | null | undefined)[] }[],
  opcoes: OpcoesGraficoPainel = {},
): GraficoPainel {
  const nomeadas = series.length > 0 && typeof series[0] === 'object' && series[0] !== null
    ? (series as readonly { nome: string; valores: readonly (number | null | undefined)[] }[])
    : [{ nome: '', valores: series as readonly (number | null | undefined)[] }];
  const dados: GraficoPainel['dados'] = [];
  for (const serie of nomeadas) {
    rotulos.forEach((rotulo, indice) => {
      const valor = serie.valores[indice];
      dados.push({
        rotulo,
        valor: valor === null || valor === undefined || !Number.isFinite(valor) ? null : valor,
        ...(serie.nome ? { serie: serie.nome } : {}),
      });
    });
  }
  return {
    id,
    tipo,
    titulo,
    ...(opcoes.metrica ? { metrica: opcoes.metrica } : {}),
    ...(opcoes.dimensao ? { dimensao: opcoes.dimensao } : {}),
    ...(opcoes.formato ? { formato: opcoes.formato } : {}),
    dados: dados.slice(0, MAX_PONTOS_GRAFICO),
    totalCategorias: Math.max(opcoes.totalCategorias ?? 0, dados.length),
  };
}

export function filtroPainel(
  chave: string,
  rotulo: string,
  valor: string | readonly string[] | null | undefined,
  valorId?: string | readonly string[] | null,
): FiltroPainel {
  const texto = Array.isArray(valor) ? (valor.length ? valor.join(', ') : null) : ((valor as string | null | undefined) || null);
  const id = Array.isArray(valorId) ? (valorId.length ? valorId.join(',') : null) : ((valorId as string | null | undefined) ?? null);
  return { chave, rotulo, valor: texto, ...(id ? { valorId: id } : {}) };
}

/** Rótulo de uma opção selecionada (ou de várias) a partir da lista de opções exibida no filtro. */
export function rotuloDasOpcoes<T>(
  opcoes: readonly T[],
  selecionado: unknown,
  valor: (opcao: T) => unknown,
  rotulo: (opcao: T) => string,
): string | null {
  const alvos = Array.isArray(selecionado) ? selecionado : selecionado === null || selecionado === undefined || selecionado === '' ? [] : [selecionado];
  if (!alvos.length) return null;
  const nomes = alvos.map((alvo) => {
    const opcao = opcoes.find((item) => valor(item) === alvo);
    return opcao ? rotulo(opcao) : String(alvo);
  });
  return nomes.join(', ');
}

function dataIso(data: Date | string | null | undefined): string | undefined {
  if (!data) return undefined;
  const d = typeof data === 'string' ? new Date(data) : data;
  if (Number.isNaN(d.getTime())) return undefined;
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mes}-${dia}`;
}

function dataBr(iso: string): string {
  const [ano, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${ano}`;
}

export function periodoPainel(
  inicio: Date | string | null | undefined,
  fim: Date | string | null | undefined,
  rotulo?: string,
): PeriodoPainel | undefined {
  const i = dataIso(inicio);
  const f = dataIso(fim);
  if (!i && !f && !rotulo) return undefined;
  const intervalo = i && f ? (i === f ? dataBr(i) : `${dataBr(i)} a ${dataBr(f)}`) : i ? `desde ${dataBr(i)}` : f ? `até ${dataBr(f)}` : '';
  return {
    ...(i ? { inicio: i } : {}),
    ...(f ? { fim: f } : {}),
    rotulo: rotulo ?? (intervalo || 'todo o período'),
  };
}

export function entidadePainel(tipo: string, rotulo: string, id?: string | null): EntidadePainel {
  return { tipo, rotulo, ...(id ? { id } : {}) };
}

// ---------------------------------------------------------------------------------- uso pelo agente

/**
 * Converte o contexto do dashboard no `AssistenteContexto` enviado ao backend. Os campos planos
 * (página, rota, filtros) mantêm compatibilidade com o workspace; `painel` leva o contexto estruturado.
 */
export function paraContextoAssistente(contexto: CopilotoDashboardContext, foco?: FocoPainel | null): AssistenteContexto {
  const { rota, sugestoes: _sugestoes, ...painel } = contexto;
  const filtros: Record<string, string> = {};
  for (const filtro of contexto.filtros) {
    if (filtro.valor) filtros[filtro.rotulo] = filtro.valor;
  }
  if (contexto.periodo?.rotulo) filtros['Período'] = contexto.periodo.rotulo;
  return {
    pagina: contexto.nome,
    ...(rota ? { rota } : {}),
    entidadeTipo: 'DASHBOARD',
    entidadeId: contexto.id,
    rotulo: contexto.nome,
    ...(Object.keys(filtros).length ? { filtros } : {}),
    painel: {
      ...painel,
      graficos: (painel.graficos ?? []).map((grafico) => ({ ...grafico, dados: grafico.dados.slice(0, MAX_PONTOS_GRAFICO) })),
      ...(foco ? { foco } : {}),
    },
  };
}

/** Recorte legível (período + filtros ativos) para chips de contexto nas mensagens. */
export function resumoRecorte(painel: ContextoPainel | null | undefined, maxFiltros = 3): string {
  if (!painel) return '';
  const partes: string[] = [];
  if (painel.periodo?.rotulo) partes.push(painel.periodo.rotulo);
  const ativos = painel.filtros.filter((filtro) => filtro.valor);
  for (const filtro of ativos.slice(0, maxFiltros)) partes.push(`${filtro.rotulo}: ${filtro.valor}`);
  if (ativos.length > maxFiltros) partes.push(`+${ativos.length - maxFiltros} filtros`);
  if (!ativos.length) partes.push('sem filtros');
  if (painel.foco?.rotulo) partes.push(`foco: ${painel.foco.rotulo}`);
  return partes.join(' · ');
}

const TIPOS_POR_CAPACIDADE: Record<Exclude<CapacidadeSugestao, 'python' | 'web'>, readonly string[]> = {
  dados: ['CATALOG', 'DATA', 'SQL'],
  analise: ['ANALYSIS'],
  artefato: ['ARTIFACT'],
  relatorio: ['REPORT'],
  planilha: ['EXPORT'],
  arquivo: ['FILE'],
};

export function capacidadeDisponivel(capacidades: CapacidadesAssistente | null, capacidade: CapacidadeSugestao): boolean {
  if (!capacidades?.disponivel) return false;
  if (capacidade === 'python') return capacidades.python;
  if (capacidade === 'web') return capacidades.pesquisaWeb;
  const tipos = new Set((capacidades.ferramentas ?? []).map((ferramenta) => ferramenta.tipo));
  return TIPOS_POR_CAPACIDADE[capacidade].some((tipo) => tipos.has(tipo));
}

/** Ações rápidas genéricas derivadas do próprio contexto (valem para qualquer painel). */
export function sugestoesGenericas(contexto: CopilotoDashboardContext): SugestaoPainel[] {
  const sugestoes: SugestaoPainel[] = [
    { rotulo: 'Explicar este painel', icone: 'pi pi-compass', requer: ['dados'],
      pergunta: 'O que está acontecendo neste painel? Resuma os principais indicadores e o que merece atenção.' },
    { rotulo: 'Encontrar anomalias', icone: 'pi pi-exclamation-triangle', requer: ['dados', 'analise'],
      pergunta: 'Existe alguma anomalia ou ponto fora do padrão nos dados deste painel? Investigue nos dados detalhados.' },
  ];
  if (contexto.periodo?.inicio || contexto.periodo?.fim) {
    sugestoes.push({ rotulo: 'Comparar com o período anterior', icone: 'pi pi-arrow-right-arrow-left', requer: ['dados', 'analise'],
      pergunta: 'Compare os principais indicadores deste painel com o período anterior equivalente e explique as variações.' });
  }
  if ((contexto.graficos ?? []).length) {
    sugestoes.push({ rotulo: 'Visualizar comparação', icone: 'pi pi-chart-bar', requer: ['dados', 'artefato'],
      pergunta: 'Monte um gráfico comparando os principais recortes deste painel.' });
  }
  sugestoes.push({ rotulo: 'Gerar relatório', icone: 'pi pi-file-pdf', requer: ['dados', 'relatorio'],
    pergunta: 'Gere um relatório PDF deste painel com os indicadores, os destaques e recomendações, usando os dados reais.' });
  return sugestoes;
}

/** Ações rápidas do painel: primeiro as específicas, depois as genéricas; só as suportadas pelas capacidades. */
export function sugestoesDisponiveis(
  contexto: CopilotoDashboardContext | null,
  capacidades: CapacidadesAssistente | null,
  limite = 4,
): SugestaoPainel[] {
  if (!contexto || !capacidades?.disponivel) return [];
  const vistas = new Set<string>();
  return [...(contexto.sugestoes ?? []), ...sugestoesGenericas(contexto)]
    .filter((sugestao) => (sugestao.requer ?? ['dados']).every((capacidade) => capacidadeDisponivel(capacidades, capacidade)))
    .filter((sugestao) => !vistas.has(sugestao.rotulo) && vistas.add(sugestao.rotulo))
    .slice(0, limite);
}
