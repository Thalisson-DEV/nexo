export interface PendenciaAgrupadaResponse {
  id: string;
  nome: string | null;
  totalPendentes: number;
  totalAbertos: number;
  totalEmTratamento: number;
  totalVencidos: number;
  totalCriticos: number;
}

export interface PeriodoDashboard {
  inicio: string;
  fim: string;
  timezone: 'America/Sao_Paulo' | string;
  comparacaoInicio: string;
  comparacaoFim: string;
}

export interface QualidadeDadosDashboard {
  ultimaCargaEm: string;
  registrosSemPrazo: number;
  registrosSemResponsavel: number;
  coberturaCadastroPercentual: number;
}

export type StatusIndicador = 'SEM_META' | 'DENTRO_META' | 'FORA_META';

export interface IndicadorComparativo {
  valor: number;
  anterior: number;
  variacaoPercentual: number;
  meta: number | null;
  status: StatusIndicador;
  definicao: string;
}

export interface ExposicaoNormalizadaItem {
  id: string;
  nome: string | null;
  inspecoes: number;
  desvios: number;
  desviosPor100Inspecoes: number;
  criticosAbertos: number;
  vencidos: number;
  variacaoTaxaPercentual: number;
}

export interface ItemCriticoDashboard {
  idPergunta: string;
  item: string | null;
  respostas: number;
  reprovacoes: number;
  taxaReprovacao: number;
  maiorGravidade: 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA';
  variacaoTaxaPercentual: number;
  percentualAcumulado: number;
}

// ============================================================
// Dashboard de inspeções — blocos do contrato agrupado
// ============================================================
export type GranularidadeDashboard = 'DIA' | 'MES' | 'ANO';

export interface TendenciaDesviosItem {
  /** Identidade estável do balde: YYYY-MM-DD, YYYY-MM ou YYYY. */
  chave: string;
  /** Texto do eixo já localizado pelo backend. */
  rotulo: string;
  /** Data civil YYYY-MM-DD; não converter para UTC para exibição. */
  inicio: string;
  totalDesvios: number;
}

export interface ResumoInspecoes {
  totalInspecoes: number;
  totalPeriodoAnterior: number;
  /** Pode ser negativo. */
  variacaoPercentual: number;
  inspecoesComDesvio: number;
  inspecoesSemDesvio: number;
  /** Taxa de conformidade — sucessor do antigo `taxaConformidade`. */
  percentualSemDesvio: number;
  totalDesvios: number;
  mediaDesviosPorInspecao: number;
  colaboradoresInspecionados: number;
  mediaColaboradoresPorInspecao: number;
  inspetoresAtivos: number;
  mediaInspecoesPorInspetor: number;
  mediaInspecoesPorDia: number;
}

export interface ResumoMeta {
  /** `false` = nenhuma meta vigente no período consultado. */
  possuiMeta: boolean;
  /** Rateada pelos dias do recorte — pode ser fracionária. Não recalcular no cliente. */
  metaPeriodo: number;
  realizadoPeriodo: number;
  percentualAtingido: number;
  saldoParaMeta: number;
  colaboradoresComMeta: number;
  inspetoresNaMeta: number;
  percentualInspetoresNaMeta: number;
  /** Têm meta mas não conseguem lançar inspeções (sem usuário vinculado). */
  colaboradoresSemAcesso: number;
}

export interface ResumoColaboradores {
  colaboradoresInspecionados: number;
  /** Efetivo ativo (respeita o filtro de base operacional). */
  colaboradoresAtivos: number;
  percentualCobertura: number;
  colaboradoresSemInspecao: number;
  mediaInspecoesPorColaborador: number;
  colaboradoresComDesvio: number;
  percentualComDesvio: number;
  /** 2+ desvios no período. */
  colaboradoresReincidentes: number;
}

export interface SerieMetaItem {
  /** '2026-08-03' | '2026-08' | '2026' */
  chave: string;
  /** '03/08' | 'ago/2026' | '2026' */
  rotulo: string;
  /** 'YYYY-MM-DD' — primeiro dia do intervalo. */
  inicio: string;
  realizado: number;
  /** Rateada no intervalo. */
  meta: number;
  percentualAtingido: number;
  desvios: number;
}

export interface InspetorMetaItem {
  idInspetor: string;
  nomeInspetor: string | null;
  realizado: number;
  /** 0 quando o inspetor não tem meta. */
  meta: number;
  /** `null` quando o inspetor não tem meta. */
  percentualAtingido: number | null;
  atingiuMeta: boolean;
  desvios: number;
  percentualDoTotal: number;
}

export interface EquipeInspecaoItem {
  idEquipe: string;
  nomeEquipe: string | null;
  inspecoes: number;
  percentualDoTotal: number;
  desvios: number;
  desviosPorInspecao: number;
  percentualSemDesvio: number;
  colaboradoresInspecionados: number;
  ultimaInspecao: string;
  /** Até o fim do período consultado. */
  diasSemInspecao: number;
}

export interface ColaboradorInspecionadoItem {
  idColaborador: string;
  nomeColaborador: string | null;
  vezesInspecionado: number;
  desviosRecebidos: number;
  ultimaInspecao: string;
  diasDesdeUltimaInspecao: number;
}

export interface FaixaBacklog {
  faixa: '0-7' | '8-15' | '16-30' | '31-60' | '>60' | string;
  idadeMinimaDias: number;
  idadeMaximaDias: number | null;
  total: number;
  porGravidade: Record<'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA', number>;
}

export interface VolumePorGravidade {
  gravidade: 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA';
  total: number;
  percentual: number;
  anterior: number;
  variacaoPercentual: number;
}

export interface PrioridadeDashboard {
  tipo: 'SLA_CRITICO' | 'SEM_PRAZO' | 'RISCO_ESTOURO_SLA' | string;
  severidade: 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA';
  titulo: string;
  evidencia: string;
  acaoSugerida: string;
  drilldown: Record<string, unknown>;
}

export interface SerieDesviosItem {
  data: string;
  abertos: number;
  resolvidos: number;
  backlogFimDoDia: number;
  vencidosFimDoDia: number;
}
