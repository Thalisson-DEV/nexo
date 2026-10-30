import { StatusSolicitacao, TipoSolicitacao } from './financeiro.models';

/**
 * Contrato do dashboard executivo do financeiro
 * (`GET /v1/financeiro/dashboards/executivo`).
 *
 * Espelha o backend campo a campo. Nenhum percentual, média ou variação é
 * recalculado no cliente: os denominadores do servidor consideram registros que a
 * tela nem recebe (o backlog anterior ao período, por exemplo), e refazer a conta
 * aqui produziria dois números diferentes para a mesma pergunta.
 */

export type GranularidadeDashboard = 'DIA' | 'MES' | 'ANO';

export type SeveridadePrioridade = 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA';

/** Sentido em que a variação do indicador é boa notícia. */
export type DirecaoDesejada = 'SUBIR' | 'DESCER' | 'NEUTRO';

export type GrauPrioridade = 'BAIXA' | 'NORMAL' | 'ALTA' | 'URGENTE';

export type TipoDespesa =
  | 'MATERIAL'
  | 'SERVICO'
  | 'COMBUSTIVEL'
  | 'LOCACAO'
  | 'UTILIDADES'
  | 'TRIBUTOS'
  | 'MANUTENCAO'
  | 'OUTRA';

/** Chaves de `indicadores`, na ordem em que o backend as monta. */
export type ChaveIndicadorFinanceiro =
  | 'valorAprovado'
  | 'taxaAprovacao'
  | 'tempoDecisao'
  | 'backlogEmAberto'
  | 'coberturaFiscal';

export interface DashboardFinanceiroFilter {
  inicio?: string | null;
  fim?: string | null;
  tipo?: TipoSolicitacao | null;
  idFormulario?: string[] | null;
  idBase?: string[] | null;
  idProcesso?: string[] | null;
  idObra?: string[] | null;
  idFornecedor?: string[] | null;
  idSolicitante?: string[] | null;
  status?: StatusSolicitacao[] | null;
  tipoDespesa?: TipoDespesa[] | null;
  prioridade?: GrauPrioridade[] | null;
  granularidade?: GranularidadeDashboard | null;
}

/** Dimensões que contam para o "N filtros ativos" da barra. */
export const DIMENSOES_FILTRO_FINANCEIRO = [
  'idFormulario',
  'idBase',
  'idProcesso',
  'idObra',
  'idFornecedor',
  'idSolicitante',
  'status',
  'tipoDespesa',
  'prioridade'
] as const satisfies readonly (keyof DashboardFinanceiroFilter)[];

export interface PeriodoFinanceiroDashboard {
  inicio: string;
  fim: string;
  timezone: string;
  comparacaoInicio: string;
  comparacaoFim: string;
  diasNoPeriodo: number;
}

export interface ResumoSolicitacoesDashboard {
  total: number;
  totalPeriodoAnterior: number;
  /** Pode ser negativa. */
  variacaoPercentual: number;

  compras: number;
  pagamentos: number;

  rascunhos: number;
  /** Submetidas e aguardando decisão. */
  aguardandoDecisao: number;
  aprovadas: number;
  rejeitadas: number;
  canceladas: number;

  /** Rascunho ou aguardando decisão — ainda sem desfecho. */
  pendentes: number;
  percentualPendentes: number;

  /** Aprovadas mais rejeitadas: as que passaram por decisão. */
  decididas: number;
  taxaAprovacao: number;
  taxaRejeicao: number;
  taxaCancelamento: number;

  solicitantesDistintos: number;
  fornecedoresDistintos: number;
  pagamentosComCompraPrevia: number;
  percentualComCompraPrevia: number;
}

export interface ResumoValoresDashboard {
  valorSolicitado: number;
  valorAprovado: number;
  valorAprovadoPeriodoAnterior: number;
  variacaoValorAprovado: number;

  valorRejeitado: number;
  valorCancelado: number;
  /** Pedido em rascunho ou na fila — dinheiro ainda indefinido. */
  valorEmAnalise: number;

  /** Solicitado menos aprovado, nas que foram aprovadas. */
  corteNaAprovacao: number;
  percentualCorte: number;
  taxaAutorizacao: number;

  ticketMedioSolicitado: number;
  ticketMedioAprovado: number;
  maiorValorSolicitado: number;

  valorAprovadoCompras: number;
  valorAprovadoPagamentos: number;

  valorVencido: number;
  quantidadeVencida: number;
  valorAVencer: number;
  quantidadeAVencer: number;
}

export interface CicloSolicitacaoDashboard {
  mediaDiasAteSubmissao: number;
  medianaDiasAteSubmissao: number;
  p90DiasAteSubmissao: number;

  mediaDiasAteDecisao: number;
  medianaDiasAteDecisao: number;
  p90DiasAteDecisao: number;

  mediaDiasCicloTotal: number;
  medianaDiasCicloTotal: number;
  p90DiasCicloTotal: number;

  mediaDiasAteRejeicao: number;

  idadeMediaFilaDias: number;
  idadeMaximaFilaDias: number;
  idadeMediaRascunhosDias: number;

  amostraSubmissao: number;
  amostraDecisao: number;
  /** Aprovações e rejeições desfeitas nos pedidos do período: retrabalho de decisão. */
  decisoesDesfeitas: number;
}

export interface ConformidadeDocumentalDashboard {
  documentosRecebidos: number;
  documentosPeriodoAnterior: number;
  variacaoDocumentos: number;
  valorDocumentado: number;

  aguardandoConferencia: number;
  comPendencia: number;
  conferidos: number;
  arquivados: number;
  taxaConferencia: number;
  idadeMediaFilaDias: number;
  idadeMaximaFilaDias: number;

  aprovadasComDocumentoFiscal: number;
  aprovadasSemDocumentoFiscal: number;
  coberturaFiscal: number;
  valorSemDocumentoFiscal: number;

  semIdentificacao: number;
  duplicados: number;
  vencidosEmAberto: number;
  valorVencidoEmAberto: number;
  avulsosSemVinculo: number;
}

export interface QualidadeDadosFinanceiroDashboard {
  ultimaMovimentacaoEm: string;
  semFornecedorIdentificado: number;
  semDescricao: number;
  semCentroCusto: number;
  semBase: number;
  fornecedoresNaoHomologados: number;
  solicitacoesComFornecedorNaoHomologado: number;
  aprovadasSemNenhumDocumento: number;
  percentualPreenchimento: number;
}

export interface IndicadorComparativoFinanceiro {
  valor: number;
  anterior: number;
  variacaoPercentual: number;
  direcaoDesejada: DirecaoDesejada;
  definicao: string;
}

export interface FunilFinanceiroItem {
  /** ABERTAS, SUBMETIDAS, APROVADAS, REJEITADAS ou CANCELADAS. */
  etapa: string;
  rotulo: string;
  quantidade: number;
  valor: number;
  percentualDoTopo: number;
  percentualDaEtapaAnterior: number | null;
  /** Perda fora da cascata: rejeitadas e canceladas. */
  vazamento: boolean;
}

export interface DistribuicaoStatusFinanceiroItem {
  status: StatusSolicitacao;
  rotulo: string;
  quantidade: number;
  valor: number;
  percentualQuantidade: number;
  percentualValor: number;
}

export interface SerieFinanceiroItem {
  chave: string;
  rotulo: string;
  inicio: string;
  abertas: number;
  valorSolicitado: number;
  aprovadas: number;
  valorAprovado: number;
  rejeitadas: number;
  valorRejeitado: number;
  canceladas: number;
  pendentes: number;
  taxaAutorizacao: number;
}

export interface FaixaEnvelhecimentoFinanceiroItem {
  faixa: string;
  idadeMinimaDias: number;
  idadeMaximaDias: number | null;
  quantidade: number;
  valor: number;
  porTipo: Record<TipoSolicitacao, number>;
}

export interface FaixaVencimentoItem {
  /** VENCIDO, 0-7, 8-15, 16-30 ou >30. */
  faixa: string;
  rotulo: string;
  quantidade: number;
  valor: number;
  percentualValor: number;
  /** Dias até o vencimento mais próximo; na faixa VENCIDO, o maior atraso. */
  diasExtremo: number;
}

export interface RankingFinanceiroItem {
  /** O que o drilldown devolve ao filtro; nunca nulo. */
  chave: string;
  /** Só nas dimensões que apontam para um cadastro. */
  id: string | null;
  nome: string | null;

  quantidade: number;
  percentualQuantidade: number;

  valorSolicitado: number;
  valorAprovado: number;
  percentualValor: number;

  aprovadas: number;
  rejeitadas: number;
  pendentes: number;

  taxaAprovacao: number;
  ticketMedio: number;

  documentos: number;
  percentualComDocumentoFiscal: number;
}

export interface DistribuicaoDocumentoItem {
  chave: string;
  rotulo: string;
  quantidade: number;
  percentualQuantidade: number;
  valor: number;
  emAberto: number;
}

export interface PrioridadeFinanceiraItem {
  tipo: string;
  severidade: SeveridadePrioridade;
  titulo: string;
  evidencia: string;
  acaoSugerida: string;
  /** Listagem que o drilldown abre: SOLICITACOES ou DOCUMENTOS. */
  destino: 'SOLICITACOES' | 'DOCUMENTOS';
  amostraIds: string[];
  drilldown: Record<string, unknown>;
}

export interface DashboardFinanceiroResponse {
  inicio: string;
  fim: string;
  granularidade: GranularidadeDashboard;
  atualizadoEm: string;
  periodo: PeriodoFinanceiroDashboard;

  resumo: ResumoSolicitacoesDashboard;
  valores: ResumoValoresDashboard;
  ciclo: CicloSolicitacaoDashboard;
  conformidade: ConformidadeDocumentalDashboard;
  qualidade: QualidadeDadosFinanceiroDashboard;

  indicadores: Record<ChaveIndicadorFinanceiro, IndicadorComparativoFinanceiro>;

  funil: FunilFinanceiroItem[];
  porStatus: DistribuicaoStatusFinanceiroItem[];
  serieTemporal: SerieFinanceiroItem[];
  envelhecimento: FaixaEnvelhecimentoFinanceiroItem[];
  vencimentos: FaixaVencimentoItem[];

  porBase: RankingFinanceiroItem[];
  porCentroCusto: RankingFinanceiroItem[];
  porObra: RankingFinanceiroItem[];
  porFornecedor: RankingFinanceiroItem[];
  porTipoDespesa: RankingFinanceiroItem[];
  porFormulario: RankingFinanceiroItem[];
  porSolicitante: RankingFinanceiroItem[];

  documentosPorTipo: DistribuicaoDocumentoItem[];
  documentosPorSituacao: DistribuicaoDocumentoItem[];

  prioridades: PrioridadeFinanceiraItem[];
}

/** Rótulos em linguagem de quem usa, para filtros e legendas. */
export const ROTULO_STATUS_SOLICITACAO: Record<StatusSolicitacao, string> = {
  RASCUNHO: 'Em preenchimento',
  SOLICITADO: 'Aguardando decisão',
  APROVADO: 'Aprovada',
  REJEITADO: 'Rejeitada',
  CANCELADO: 'Cancelada'
};

export const ROTULO_TIPO_DESPESA: Record<TipoDespesa, string> = {
  MATERIAL: 'Material',
  SERVICO: 'Serviço',
  COMBUSTIVEL: 'Combustível',
  LOCACAO: 'Locação',
  UTILIDADES: 'Água, energia, telefone e internet',
  TRIBUTOS: 'Impostos e taxas',
  MANUTENCAO: 'Manutenção',
  OUTRA: 'Outra'
};

export const ROTULO_PRIORIDADE: Record<GrauPrioridade, string> = {
  BAIXA: 'Baixa',
  NORMAL: 'Normal',
  ALTA: 'Alta',
  URGENTE: 'Urgente'
};
