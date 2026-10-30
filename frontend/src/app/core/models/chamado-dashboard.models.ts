import { ChamadoPrioridade, ChamadoStatus } from './chamado.models';

export type GranularidadeDashboard = 'DIA' | 'MES' | 'ANO';

export interface IndicadorComparativoChamado {
  valor: number;
  anterior: number;
  variacaoPercentual: number;
  meta?: number | null;
  status: string;
  definicao: string;
}

export interface PeriodoChamadoDashboard {
  inicio: string;
  fim: string;
  timezone: string;
  comparacaoInicio: string;
  comparacaoFim: string;
}

export interface QualidadeDadosChamadoDashboard {
  ultimaCargaEm: string;
  chamadosSemResponsavel: number;
  chamadosComSlaPausado: number;
  chamadosEstagnadosSemInteracao: number;
  coberturaResponsavelPercentual: number;
}

export interface PrioridadeChamadoDashboard {
  tipo: string;
  severidade: 'CRITICA' | 'ALTA' | 'MEDIA' | 'NORMAL' | 'BAIXA' | string;
  titulo: string;
  evidencia: string;
  acaoSugerida: string;
  drilldown?: Record<string, unknown>;
}

export interface FaixaBacklogChamado {
  faixa: string;
  rotulo: string;
  total: number;
  percentual: number;
  porPrioridade: Record<ChamadoPrioridade, number>;
}

export interface VolumePrioridadeChamadoItem {
  prioridade: ChamadoPrioridade;
  total: number;
  anterior: number;
  variacaoPercentual: number;
  percentual: number;
}

export interface TopicoDesempenhoItem {
  id: string;
  codigo: string;
  nome: string;
  totalChamados: number;
  resolvidos: number;
  pendentes: number;
  taxaSlaCumprido: number;
  tempoMedianoResolucaoHoras: number;
  reabertos: number;
  participacaoPercentual: number;
}

export interface EquipeDesempenhoItem {
  id: string;
  codigo: string;
  nome: string;
  totalChamados: number;
  resolvidos: number;
  pendentes: number;
  taxaSlaPrimeiraResposta: number;
  taxaSlaResolucao: number;
  tempoMedianoResolucaoHoras: number;
  participacaoPercentual: number;
}

export interface AtendenteDesempenhoItem {
  id: string;
  nome: string;
  equipe: string;
  totalChamados: number;
  resolvidos: number;
  pendentes: number;
  taxaSlaResolucao: number;
  tempoMedianoResolucaoHoras: number;
}

export interface RankingSolicitanteItem {
  id: string;
  nome: string;
  totalChamados: number;
  participacaoPercentual: number;
}

export interface SerieTemporalChamadoItem {
  chave: string;
  rotulo: string;
  data: string;
  totalAbertos: number;
  totalResolvidos: number;
  totalVencidos: number;
}

export interface DashboardChamadosResponse {
  inicio: string;
  fim: string;
  atualizadoEm: string;
  periodo: PeriodoChamadoDashboard;
  qualidadeDados: QualidadeDadosChamadoDashboard;
  indicadores: Record<string, IndicadorComparativoChamado>;

  totalChamados: number;
  totalAbertos: number;
  totalEmAtendimento: number;
  totalAguardandoSolicitante: number;
  totalResolvidos: number;
  totalFechados: number;
  totalCancelados: number;
  totalPendentes: number;
  totalCriticos: number;
  totalReabertos: number;

  taxaReabertura: number;
  taxaResolucao: number;
  taxaSlaPrimeiraRespostaPercentual: number;
  taxaSlaResolucaoPercentual: number;

  tempoMedianoPrimeiraRespostaHoras: number;
  tempoP90PrimeiraRespostaHoras: number;
  tempoMedianoResolucaoHoras: number;
  tempoP90ResolucaoHoras: number;
  tempoMedioResolucaoHoras: number;

  totalPrimeiraRespostaNoPrazo: number;
  totalPrimeiraRespostaVencida: number;
  totalResolucaoNoPrazo: number;
  totalResolucaoVencida: number;

  totalPorStatus: Record<ChamadoStatus, number>;
  totalPorPrioridade: Record<ChamadoPrioridade, number>;
  volumePorPrioridade: VolumePrioridadeChamadoItem[];
  backlogPorIdade: FaixaBacklogChamado[];

  desempenhoPorTopico: TopicoDesempenhoItem[];
  desempenhoPorEquipe: EquipeDesempenhoItem[];
  desempenhoPorAtendente: AtendenteDesempenhoItem[];
  rankingSolicitantes: RankingSolicitanteItem[];

  prioridades: PrioridadeChamadoDashboard[];
  granularidade: GranularidadeDashboard;
  serieTemporal: SerieTemporalChamadoItem[];
}

export interface DashboardChamadosFilter {
  inicio?: string | null;
  fim?: string | null;
  topicoId?: string[];
  equipeId?: string[];
  prioridade?: ChamadoPrioridade[];
  status?: ChamadoStatus[];
  usuarioResponsavelId?: string[];
  usuarioSolicitanteId?: string[];
  slaVencido?: boolean;
  granularidade?: GranularidadeDashboard;
}
