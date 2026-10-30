import { TurnoResponse } from './turno.models';
import { ExecucaoComercialResponse } from './execucao.models';

/** Recorte de base operacional e processo, comum aos dois dashboards de execução. Lista vazia não restringe. */
export interface DashboardRecorte {
  idBaseOperacional?: string[];
  idProcesso?: string[];
}

/** Datas no formato yyyy-MM-dd (dia civil de São Paulo, inclusivo). */
export interface DashboardOperacionalFiltros extends DashboardRecorte {
  inicio?: string;
  fim?: string;
}

export interface DashboardTurnosFiltros extends DashboardRecorte {
  data?: string;
}

export type SituacaoMeta = 'META_ATINGIDA' | 'NO_RITMO' | 'ABAIXO_DO_RITMO' | 'SEM_META';
export type SituacaoTurno = 'EM_TURNO' | 'ENCERRADO' | 'SEM_TURNO';

export interface ProducaoDia {
  data: string;
  notas: number;
  valor: number;
  /** Parte da meta mensal que cabe ao dia (zero em dia não útil); nula sem meta cadastrada. */
  meta: number | null;
}

/**
 * Notas e produção de um tipo de serviço no recorte. As notas sem tipo vêm agrupadas
 * com `idTipoServico` nulo e descrição "Não informado".
 */
export interface ProducaoPorTipoServico {
  idTipoServico: string | null;
  codigo: string | null;
  descricao: string;
  notas: number;
  producao: number;
  valorMedioNota: number;
  /** Fatia das notas do recorte (0–100). */
  percentualNotas: number;
}

export interface ResumoOperacional {
  producao: number;
  producaoPeriodoAnterior: number;
  variacaoPercentual: number | null;
  notas: number;
  valorMedioNota: number;
  equipesAtivas: number;
  equipesComProducao: number;
  equipesComMeta: number;
  metaPeriodo: number | null;
  producaoEquipesComMeta: number;
  percentualMeta: number | null;
  projecaoEquipesComMeta: number | null;
  equipesAbaixoDoRitmo: number;
}

export interface EquipeOperacional {
  idEquipe: string;
  codigo: string;
  baseOperacional: string | null;
  processo: string | null;
  tipoEquipe: string | null;
  placa: string | null;
  producao: number;
  notas: number;
  valorMedioNota: number;
  diasComTurno: number;
  diasComProducao: number;
  producaoMediaPorDia: number;
  metaMensal: number | null;
  metaPeriodo: number | null;
  metaEsperadaAteAgora: number | null;
  percentualMeta: number | null;
  projecao: number | null;
  situacao: SituacaoMeta;
  ultimoLancamento: string | null;
  producaoDiaria: ProducaoDia[];
  tiposServico: ProducaoPorTipoServico[];
}

export interface DashboardOperacionalResponse {
  inicio: string;
  fim: string;
  geradoEm: string;
  diasUteisPeriodo: number;
  diasUteisEncerrados: number;
  resumo: ResumoOperacional;
  producaoDiaria: ProducaoDia[];
  tiposServico: ProducaoPorTipoServico[];
  equipes: EquipeOperacional[];
}

export interface ResumoTurnos {
  equipesAtivas: number;
  emTurno: number;
  encerrados: number;
  semTurno: number;
  semLancamentoRecente: number;
  turnosAbertosDeOutroDia: number;
  notas: number;
  producao: number;
  equipesComMeta: number;
  metaDiaria: number | null;
  producaoEquipesComMeta: number;
  percentualMetaDiaria: number | null;
}

export interface LancamentoTurno {
  idExecucao: string;
  dataLancamento: string;
  valor: number | null;
  /** Descrição do tipo de serviço; nula quando não informado. */
  tipoServico: string | null;
}

export interface EquipeTurno {
  idEquipe: string;
  codigo: string;
  baseOperacional: string | null;
  processo: string | null;
  situacao: SituacaoTurno;
  idTurno: string | null;
  quantidadeTurnos: number;
  abertura: string | null;
  fechamento: string | null;
  minutosEmTurno: number;
  abertoDesdeOutroDia: boolean;
  abertoPor: string | null;
  placa: string | null;
  membros: string[];
  notas: number;
  producao: number;
  valorMedioNota: number;
  metaDiaria: number | null;
  percentualMetaDiaria: number | null;
  primeiroLancamento: string | null;
  ultimoLancamento: string | null;
  minutosSemLancamento: number | null;
  semLancamentoRecente: boolean;
  lancamentos: LancamentoTurno[];
  tiposServico: ProducaoPorTipoServico[];
}

export interface DashboardTurnosResponse {
  data: string;
  hoje: boolean;
  diaUtil: boolean;
  geradoEm: string;
  limiteSemLancamentoMinutos: number;
  resumo: ResumoTurnos;
  tiposServico: ProducaoPorTipoServico[];
  equipes: EquipeTurno[];
}

/** Composição confirmada na abertura do turno (não a composição atual da equipe). */
export interface ComposicaoTurno {
  abertoPor: string | null;
  placa: string | null;
  membros: string[];
}

/**
 * Números do turno. Produção e notas contam só lançamentos concluídos; os tempos vão
 * até o fechamento ou, com o turno aberto, até `calculadoEm`.
 */
export interface IndicadoresTurno {
  calculadoEm: string;
  emAndamento: boolean;
  /** Aberto e iniciado antes de hoje: provável turno esquecido. */
  abertoDesdeOutroDia: boolean;
  duracaoMinutos: number;
  notas: number;
  producao: number;
  valorMedioNota: number;
  producaoPorHora: number | null;
  /** Meta da equipe no dia da abertura; nula sem meta ou em dia não útil. */
  metaDiaria: number | null;
  percentualMetaDiaria: number | null;
  minutosAtePrimeiroLancamento: number | null;
  minutosAposUltimoLancamento: number | null;
  maiorIntervaloSemLancamentoMinutos: number | null;
  lancamentosSemLocalizacao: number;
}

/** Médias dos turnos encerrados da equipe nos `dias` anteriores à abertura. */
export interface HistoricoEquipeTurno {
  dias: number;
  turnos: number;
  mediaNotasPorTurno: number | null;
  mediaProducaoPorTurno: number | null;
  mediaDuracaoMinutos: number | null;
  valorMedioNota: number | null;
}

export interface DetalhamentoTurnoResponse {
  turno: TurnoResponse;
  composicao: ComposicaoTurno;
  execucoes: ExecucaoComercialResponse[];
  indicadores: IndicadoresTurno;
  historicoEquipe: HistoricoEquipeTurno;
}

// ---------- Consulta de turnos ----------

export type StatusTurno = 'ABERTO' | 'FECHADO';

export interface ConsultaTurnosFiltros {
  idEquipe?: string;
  idBaseOperacional?: string[];
  idProcesso?: string[];
  status?: StatusTurno;
  /** Dia civil (yyyy-MM-dd). */
  aberturaDe?: string;
  /** Dia civil (yyyy-MM-dd), inclusive. */
  aberturaAte?: string;
  apenasHoje?: boolean;
  buscaGlobal?: string;
  page?: number;
  size?: number;
  sort?: string[];
}

/** Linha da consulta de turnos: o turno, a equipe e o que ela produziu nele. */
export interface TurnoConsulta {
  id: string;
  idEquipe: string;
  codigoEquipe: string | null;
  baseOperacional: string | null;
  processo: string | null;
  status: StatusTurno;
  dataInicio: string;
  dataFim: string | null;
  duracaoMinutos: number;
  abertoDesdeOutroDia: boolean;
  abertoPor: string | null;
  placa: string | null;
  membros: number;
  notas: number;
  producao: number;
  valorMedioNota: number;
  metaDiaria: number | null;
  percentualMetaDiaria: number | null;
  primeiroLancamento: string | null;
  ultimoLancamento: string | null;
  /** A equipe esqueceu o turno aberto e o servidor o encerrou no horário de corte. */
  encerradoAutomaticamente?: boolean;
  motivoEncerramento?: string | null;
}

// ---------- Notas que compõem os dashboards (drill-down) ----------

/** Mesmo escopo dos dashboards: equipes ativas do recorte, notas concluídas, dias civis de São Paulo. */
export interface NotasDoRecorteFiltros extends DashboardRecorte {
  inicio?: string;
  fim?: string;
  idEquipe?: string;
  idTipoServico?: string;
  semTipoServico?: boolean;
  page?: number;
  /** Até 500. */
  size?: number;
  /** `dataLancamento` ou `valorTotalFaturado`, com `,asc`/`,desc`. */
  sort?: string[];
}

export interface NotaDoRecorte {
  id: string;
  referenciaNota: string;
  idTurno: string;
  idEquipe: string;
  codigoEquipe: string | null;
  baseOperacional: string | null;
  processo: string | null;
  idTipoServico: string | null;
  codigoTipoServico: string | null;
  tipoServico: string | null;
  dataLancamento: string;
  valor: number | null;
  /** "Código · nome" de cada ação executada. */
  acoes: string[];
  materiais: number;
  latitude: number | null;
  longitude: number | null;
}

// ---------- Contexto do relatório de execução ----------

export interface LancamentoDoTurno {
  idExecucao: string;
  referenciaNota: string;
  dataLancamento: string;
  status: 'INICIADO' | 'CONCLUIDO' | 'EDITADO';
  valor: number | null;
  latitude: number | null;
  longitude: number | null;
}

export interface ContextoExecucaoResponse {
  idExecucao: string;
  turno: { id: string; status: StatusTurno; dataInicio: string; dataFim: string | null } | null;
  /** Todas as notas do turno, em ordem de lançamento, incluindo esta. */
  lancamentosDoTurno: LancamentoDoTurno[];
  diaDaEquipe: {
    data: string;
    notas: number;
    producao: number;
    metaDiaria: number | null;
    percentualMetaDiaria: number | null;
  } | null;
  referenciaEquipe: { dias: number; notas: number; valorMedioNota: number | null } | null;
  /** Valor de referência de cada ação da nota no contrato da equipe, na data do lançamento. */
  valoresPorAcao: { idAcao: string; valor: number }[];
}
