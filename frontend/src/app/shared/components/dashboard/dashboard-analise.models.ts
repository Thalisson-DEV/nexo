import { Observable } from 'rxjs';
import { CesChartKind, CesChartSeries, CesChartValueFormat, CesDataTableColumn, CesTone } from '@ces/ui';

/**
 * Contrato declarativo da análise detalhada (drill-down) dos dashboards. O dashboard descreve o que
 * mostrar para o elemento clicado — indicadores, gráficos secundários, tabelas e a origem dos dados —
 * e o `app-dashboard-analise` cuida de painel, navegação, estados, carga, conferência e exportação.
 */

/** Alvo de uma análise, no mesmo formato do `?drill=` (`dia:2026-10-05`, `equipe:<id>`). */
export interface AlvoAnalise {
  readonly dimension: string;
  readonly value: string;
}

/** O que acontece ao clicar numa categoria de gráfico ou numa linha de tabela da análise. */
export type AcaoAnalise =
  /** Abre outra análise (empilhada: "Voltar" retorna a esta). */
  | { readonly tipo: 'navegar'; readonly alvo: AlvoAnalise }
  /** Filtra uma origem (ex.: só as notas das 14h) e abre a aba dela. Sem `origem`, a principal. */
  | { readonly tipo: 'filtrar'; readonly filtro: FiltroOrigem; readonly origem?: string }
  /** Destaca um registro de uma origem (ex.: uma nota) com o detalhe completo. */
  | { readonly tipo: 'registro'; readonly id: string; readonly origem?: string };

export interface KpiAnalise {
  readonly rotulo: string;
  readonly valor: string;
  readonly detalhe?: string;
  readonly tom?: CesTone;
  readonly icone?: string;
}

/** Leitura em linguagem de gestão sobre o recorte ("3 equipes concentram 62% da produção"). */
export interface DestaqueAnalise {
  readonly texto: string;
  readonly icone: string;
  readonly tom: CesTone;
}

export interface GraficoAnalise {
  readonly id: string;
  readonly titulo: string;
  readonly descricao?: string;
  readonly tipo: CesChartKind;
  readonly rotulos: readonly string[];
  readonly series: readonly CesChartSeries[];
  readonly formato?: CesChartValueFormat;
  readonly altura?: number;
  readonly nota?: string;
  /** Ocupa a largura toda do painel (padrão: metade em telas largas). */
  readonly inteiro?: boolean;
  /** Clique numa categoria. Sem ele, o gráfico não é selecionável. */
  readonly aoSelecionar?: (indice: number) => AcaoAnalise | null;
  /** Verbo da linha na tabela de dados do gráfico (caminho por teclado). */
  readonly rotuloSelecao?: string;
}

export interface TabelaAnalise<T = any> {
  readonly id: string;
  readonly titulo: string;
  readonly descricao?: string;
  readonly icone?: string;
  readonly colunas: readonly CesDataTableColumn<T>[];
  readonly linhas: readonly T[];
  readonly chaveLinha: (linha: T, indice: number) => string;
  readonly rotuloLinha?: (linha: T) => string;
  readonly aoAtivar?: (linha: T) => AcaoAnalise | null;
  readonly rotuloAcao?: string;
  readonly nomeArquivo?: string;
  readonly vazio?: string;
}

export interface FiltroOrigem<T = any> {
  readonly chave: string;
  readonly rotulo: string;
  readonly aceita: (linha: T) => boolean;
}

export interface CampoDetalhe {
  readonly rotulo: string;
  readonly valor: string;
  readonly icone?: string;
}

export interface LinkAnalise {
  readonly rotulo: string;
  readonly icone: string;
  readonly rota: string | readonly unknown[];
  /** Lista vira parâmetro repetido na URL (filtros de seleção múltipla). */
  readonly queryParams?: Record<string, string | readonly string[] | null | undefined>;
}

/** Detalhe de um registro de origem (uma nota, um turno), aberto dentro da própria análise. */
export interface DetalheRegistro {
  readonly titulo: string;
  readonly subtitulo?: string;
  readonly campos: readonly CampoDetalhe[];
  readonly listas?: readonly { readonly rotulo: string; readonly itens: readonly string[] }[];
  readonly links?: readonly LinkAnalise[];
}

/** Conferência entre a origem e o número do painel: a soma das linhas precisa fechar com o cartão. */
export interface ConferenciaOrigem<T = any> {
  readonly quantidadeEsperada: number;
  readonly valorEsperado: number | null;
  readonly somar: (linha: T) => number;
  readonly formato: 'moeda' | 'inteiro';
}

/** Registros que compõem o número analisado, vindos do backend com o mesmo escopo do dashboard. */
export interface OrigemAnalise<T = any> {
  /** Aba da origem (padrão `origem`); ações `filtrar`/`registro` a referenciam. */
  readonly id?: string;
  /** Identidade da carga: mudar a chave recarrega. */
  readonly chave: string;
  readonly titulo: string;
  readonly rotuloAba: string;
  readonly descricao?: string;
  readonly icone?: string;
  readonly colunas: readonly CesDataTableColumn<T>[];
  readonly carregar: () => Observable<{ linhas: T[]; total: number }>;
  readonly chaveLinha: (linha: T) => string;
  readonly rotuloLinha?: (linha: T) => string;
  /** Filtro aplicado ao abrir (ex.: análise de uma hora filtra as notas do dia carregadas). */
  readonly filtroInicial?: FiltroOrigem<T>;
  readonly conferencia?: ConferenciaOrigem<T>;
  /** Gráficos que só existem com os registros em mãos (ex.: notas por hora). */
  readonly derivados?: (linhas: readonly T[]) => readonly GraficoAnalise[];
  readonly detalhar?: (linha: T, linhas: readonly T[]) => DetalheRegistro;
  readonly verTudo?: LinkAnalise;
  readonly nomeArquivo?: string;
  readonly vazio?: string;
}

export interface VizinhoAnalise {
  readonly alvo: AlvoAnalise;
  readonly rotulo: string;
}

export interface CopilotoAnalise {
  readonly foco: { readonly tipo: 'grafico' | 'metrica' | 'tabela' | 'secao'; readonly id: string; readonly rotulo: string };
  readonly pergunta: string;
}

export interface VistaAnalise {
  /** Alvo serializado: identidade da vista. */
  readonly chave: string;
  readonly eyebrow: string;
  readonly titulo: string;
  readonly descricao?: string;
  readonly icone?: string;
  readonly kpis: readonly KpiAnalise[];
  readonly destaques?: readonly DestaqueAnalise[];
  /** Navegação lateral na mesma dimensão (dia anterior/seguinte, hora anterior/seguinte). */
  readonly anterior?: VizinhoAnalise;
  readonly proximo?: VizinhoAnalise;
  readonly graficos: readonly GraficoAnalise[];
  readonly tabelas?: readonly TabelaAnalise[];
  /** Origem principal: os registros que compõem o número analisado. */
  readonly origem?: OrigemAnalise;
  /** Outras fontes do recorte (ex.: desvios críticos do dia), cada uma com sua aba. */
  readonly origensExtras?: readonly OrigemAnalise[];
  readonly links?: readonly LinkAnalise[];
  readonly copiloto?: CopilotoAnalise;
}

export function chaveDoAlvo(alvo: AlvoAnalise): string {
  return `${alvo.dimension}:${alvo.value}`;
}
