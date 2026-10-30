import { DescontoStatus } from './desconto.models';

/**
 * Contrato do dashboard executivo de descontos
 * (`GET /v1/descontos/dashboards/executivo`).
 *
 * Espelha o backend campo a campo. Nenhum percentual, média ou variação é recalculado
 * no cliente: os denominadores do servidor consideram registros que a tela nem recebe
 * (o backlog anterior ao período, por exemplo), e refazer a conta aqui produziria dois
 * números diferentes para a mesma pergunta.
 */

export type GranularidadeDashboard = 'DIA' | 'MES' | 'ANO';

export type SeveridadePrioridade = 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA';

/** Sentido em que a variação do indicador é uma boa notícia. */
export type DirecaoDesejada = 'SUBIR' | 'DESCER' | 'NEUTRO';

export interface DescontoDashboardFilter {
  inicio?: string | null;
  fim?: string | null;
  inicioCriacao?: string | null;
  fimCriacao?: string | null;
  idMotivo?: string[] | null;
  idTipoEquipamento?: string[] | null;
  idColaborador?: string[] | null;
  idBaseOperacional?: string[] | null;
  idCentroCusto?: string[] | null;
  idFuncao?: string[] | null;
  idUsuarioSolicitante?: string[] | null;
  status?: DescontoStatus[] | null;
  granularidade?: GranularidadeDashboard | null;
}

export interface PeriodoDescontoDashboard {
  inicio: string;
  fim: string;
  timezone: string;
  comparacaoInicio: string;
  comparacaoFim: string;
  diasNoPeriodo: number;
}

export interface ResumoDescontos {
  totalDescontos: number;
  totalPeriodoAnterior: number;
  /** Pode ser negativa. */
  variacaoPercentual: number;
  solicitados: number;
  validados: number;
  baixados: number;
  rejeitados: number;
  /** SOLICITADO ou VALIDADO. */
  pendentes: number;
  percentualPendentes: number;
  /** BAIXADO ou REJEITADO. */
  finalizados: number;
  taxaAprovacao: number;
  taxaRejeicao: number;
  colaboradoresAtingidos: number;
  colaboradoresAtivos: number;
  percentualDoEfetivo: number;
  colaboradoresReincidentes: number;
  mediaDescontosPorColaborador: number;
  totalItens: number;
  mediaItensPorDesconto: number;
  mediaParcelas: number;
}

export interface ResumoFinanceiro {
  valorSolicitado: number;
  valorValidado: number;
  valorBaixado: number;
  valorBaixadoPeriodoAnterior: number;
  variacaoValorBaixado: number;
  valorRejeitado: number;
  valorEmAberto: number;
  /** Corte feito na validação. */
  glosaValidacao: number;
  percentualGlosaValidacao: number;
  /** Corte feito na baixa. */
  ajusteBaixa: number;
  percentualAjusteBaixa: number;
  /** Baixado sobre solicitado. */
  taxaRecuperacao: number;
  ticketMedioSolicitado: number;
  ticketMedioBaixado: number;
  maiorValorSolicitado: number;
  valorMedioParcela: number;
}

export interface CicloDesconto {
  mediaDiasAteValidacao: number;
  medianaDiasAteValidacao: number;
  p90DiasAteValidacao: number;
  mediaDiasAteBaixa: number;
  medianaDiasAteBaixa: number;
  p90DiasAteBaixa: number;
  mediaDiasCicloTotal: number;
  medianaDiasCicloTotal: number;
  p90DiasCicloTotal: number;
  mediaDiasAteRejeicao: number;
  idadeMediaPendentesDias: number;
  idadeMaximaPendenteDias: number;
  amostraValidacao: number;
  amostraBaixa: number;
}

export interface QualidadeDadosDesconto {
  ultimaCargaEm: string;
  pendentesSemAnexo: number;
  pendentesSemItens: number;
  pendentesSemObservacao: number;
  coberturaDocumentalPercentual: number;
  /** Termos cujo colaborador não foi encontrado no RH — ficam fora dos agrupamentos. */
  colaboradoresNaoIdentificados: number;
}

export interface IndicadorComparativoDesconto {
  valor: number;
  anterior: number;
  variacaoPercentual: number;
  direcaoDesejada: DirecaoDesejada;
  definicao: string;
}

export type ChaveIndicador = 'valorBaixado' | 'taxaAprovacao' | 'valorEmAberto' | 'tempoValidacao';

export interface FunilEtapaItem {
  etapa: 'SOLICITADOS' | 'VALIDADOS' | 'BAIXADOS' | 'REJEITADOS';
  rotulo: string;
  quantidade: number;
  valor: number;
  percentualDoTopo: number;
  /** `null` no topo do funil. */
  percentualDaEtapaAnterior: number | null;
}

export interface DistribuicaoStatusItem {
  status: DescontoStatus;
  rotulo: string;
  quantidade: number;
  valor: number;
  percentualQuantidade: number;
  percentualValor: number;
}

export interface SerieDescontoItem {
  /** '2026-08-03' | '2026-08' | '2026' */
  chave: string;
  /** '03/08' | 'ago/2026' | '2026' */
  rotulo: string;
  inicio: string;
  solicitados: number;
  valorSolicitado: number;
  validados: number;
  valorValidado: number;
  baixados: number;
  valorBaixado: number;
  rejeitados: number;
  valorRejeitado: number;
  pendentes: number;
  taxaRecuperacao: number;
}

export interface FaixaEnvelhecimentoItem {
  faixa: '0-7' | '8-15' | '16-30' | '31-60' | '>60' | string;
  idadeMinimaDias: number;
  /** `null` na última faixa, que é aberta. */
  idadeMaximaDias: number | null;
  quantidade: number;
  valor: number;
  porStatus: Partial<Record<DescontoStatus, number>>;
}

export interface RankingDescontoItem {
  id: string;
  /** `null` quando o cadastro de origem não foi encontrado. */
  nome: string | null;
  quantidade: number;
  percentualQuantidade: number;
  valorSolicitado: number;
  valorEfetivo: number;
  percentualValor: number;
  baixados: number;
  rejeitados: number;
  pendentes: number;
  taxaAprovacao: number;
  ticketMedio: number;
}

export interface TipoEquipamentoDescontoItem {
  id: string;
  nome: string | null;
  /** Itens lançados — não confundir com termos. */
  itens: number;
  descontos: number;
  valorItens: number;
  percentualValor: number;
  valorMedioPorItem: number;
  descontosBaixados: number;
  descontosRejeitados: number;
  taxaAprovacao: number;
}

export interface ColaboradorDescontoItem {
  idColaborador: string;
  nomeColaborador: string | null;
  funcao: string | null;
  baseOperacional: string | null;
  quantidade: number;
  valorSolicitado: number;
  valorEfetivo: number;
  baixados: number;
  rejeitados: number;
  pendentes: number;
  ultimaOcorrencia: string;
  diasDesdeUltimaOcorrencia: number;
  mediaParcelas: number;
}

export interface PrioridadeDescontoItem {
  tipo:
    | 'VALIDACAO_PARADA'
    | 'BAIXA_PARADA'
    | 'SEM_COMPROVACAO'
    | 'GLOSA_ELEVADA'
    | 'REINCIDENCIA'
    | string;
  severidade: SeveridadePrioridade;
  titulo: string;
  evidencia: string;
  acaoSugerida: string;
  amostraIds: string[];
  drilldown: Record<string, unknown>;
}

export interface DashboardDescontosResponse {
  inicio: string;
  fim: string;
  granularidade: GranularidadeDashboard;
  atualizadoEm: string;
  periodo: PeriodoDescontoDashboard;

  resumo: ResumoDescontos;
  financeiro: ResumoFinanceiro;
  ciclo: CicloDesconto;
  qualidade: QualidadeDadosDesconto;
  indicadores: Record<ChaveIndicador | string, IndicadorComparativoDesconto>;

  funil: FunilEtapaItem[];
  porStatus: DistribuicaoStatusItem[];
  serieTemporal: SerieDescontoItem[];
  serieTemporalCriacao: SerieDescontoItem[];
  envelhecimento: FaixaEnvelhecimentoItem[];

  porMotivo: RankingDescontoItem[];
  porTipoEquipamento: TipoEquipamentoDescontoItem[];
  porColaborador: ColaboradorDescontoItem[];
  porBaseOperacional: RankingDescontoItem[];
  porCentroCusto: RankingDescontoItem[];
  porFuncao: RankingDescontoItem[];
  porSolicitante: RankingDescontoItem[];

  prioridades: PrioridadeDescontoItem[];
}

export const STATUS_DESCONTO_LABELS: Record<DescontoStatus, string> = {
  SOLICITADO: 'Aguardando validação',
  VALIDADO: 'Aguardando baixa',
  BAIXADO: 'Baixado em folha',
  REJEITADO: 'Rejeitado'
};

/**
 * Dimensões de múltipla seleção do filtro, na ordem em que aparecem na URL. Fica aqui
 * porque a mesma lista é usada para restaurar o recorte de um link compartilhado,
 * para reescrevê-lo e para contar quantos filtros estão ativos — mantê-la em um lugar
 * só é o que impede as três leituras de divergirem.
 */
export const DIMENSOES_FILTRO_DESCONTO = [
  'idMotivo',
  'idTipoEquipamento',
  'idBaseOperacional',
  'idCentroCusto',
  'idFuncao',
  'status'
] as const satisfies readonly (keyof DescontoDashboardFilter)[];
