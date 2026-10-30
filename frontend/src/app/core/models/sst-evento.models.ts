/**
 * Governança de acidentalidade do SST — a metade reativa do módulo.
 *
 * O modelo tem duas regras opostas de propósito, e a interface precisa deixá-las
 * visíveis: pessoas e unidades organizacionais são **copiadas** no registro (atividade,
 * gestor, técnicos, envolvidos, base, equipe, veículo) e não mudam quando o cadastro
 * muda; catálogos são **referenciados** (classificação, lesão, agente) e acompanham
 * qualquer correção de nome.
 */

// ==========================================================
// Enumerações
// ==========================================================

export type TipoEventoSst =
  | 'ACIDENTE_TIPICO'
  | 'ACIDENTE_TRAJETO'
  | 'INCIDENTE_SEM_LESAO'
  | 'DANO_MATERIAL'
  | 'DOENCA_OCUPACIONAL';

export type StatusEventoSst = 'REGISTRADO' | 'EM_TRATATIVA' | 'CONCLUIDO' | 'CANCELADO';

export type TipoCat = 'INICIAL' | 'REABERTURA' | 'COMUNICACAO_OBITO';

export type RegistroEvidencia = 'SIM' | 'NAO' | 'NAO_APLICAVEL';

export type PapelEnvolvido = 'ACIDENTADO' | 'TESTEMUNHA' | 'ENVOLVIDO';

export type TipoEnvolvido = 'COLABORADOR' | 'TERCEIRO';

export type ContextoAnexoEvento = 'EVENTO' | 'CAT' | 'TRATATIVA';

export type TipoCatalogoSst =
  | 'CLASSIFICACAO_EVENTO'
  | 'LOCAL_LESAO'
  | 'NATUREZA_LESAO'
  | 'AGENTE_CAUSADOR';

// ==========================================================
// Cadastros de apoio
// ==========================================================

export interface CatalogoSstResponse {
  id: string;
  tipo: TipoCatalogoSst;
  nome: string;
  descricao: string | null;
  codigo: string | null;
  ordem: number;
  ativo: boolean;
}

export interface CatalogoSstRequest {
  tipo: TipoCatalogoSst;
  nome: string;
  descricao?: string | null;
  codigo?: string | null;
  ordem?: number | null;
}

export interface AtividadeSstResponse {
  id: string;
  nome: string;
  descricao: string | null;
  idGestor: string | null;
  nomeGestor: string | null;
  funcaoGestor: string | null;
  idProcesso: string | null;
  nomeProcesso: string | null;
  ativo: boolean;
}

export interface AtividadeSstRequest {
  nome: string;
  descricao?: string | null;
  idGestor?: string | null;
  idProcesso?: string | null;
}

// ==========================================================
// Evento — escrita
// ==========================================================

export interface EnvolvidoEventoRequest {
  tipo: TipoEnvolvido;
  papel: PapelEnvolvido;
  /** Obrigatório para COLABORADOR; proibido para TERCEIRO. */
  idColaborador?: string | null;
  /** Obrigatório para TERCEIRO; ignorado para COLABORADOR (o retrato vem do RH). */
  nome?: string | null;
  funcao?: string | null;
  /** Obrigatória para TERCEIRO: sem cadastro, é ela que identifica a pessoa. */
  empresa?: string | null;
}

export interface EventoSstRequest {
  tipo: TipoEventoSst;
  dataOcorrencia: string;
  dataComunicacao: string;

  idAtividade: string;
  /** Omitido herda o gestor da atividade; informado e diferente, marca substituto. */
  idGestor?: string | null;
  idBaseOperacional: string;
  idEquipe?: string | null;
  idVeiculo?: string | null;

  idClassificacao: string;
  idProcesso?: string | null;

  telemetria?: RegistroEvidencia | null;
  filmagem?: RegistroEvidencia | null;
  localOcorrencia?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  descricao: string;

  idLocalLesao?: string | null;
  idNaturezaLesao?: string | null;
  idAgenteCausador?: string | null;
  diasPerdidos?: number | null;

  possuiCat: boolean;
  tipoCat?: TipoCat | null;
  numeroCat?: string | null;
  dataEmissaoCat?: string | null;

  idsTecnicos: string[];
  envolvidos: EnvolvidoEventoRequest[];
}

export interface TratativaEventoRequest {
  tratativa?: string | null;
}

export interface MotivoEventoRequest {
  motivo: string;
}

// ==========================================================
// Importação CSV de eventos
// ==========================================================

export type ImportacaoEventoSstStatus =
  | 'AGUARDANDO_PROCESSAMENTO'
  | 'COM_ERRO'
  | 'EM_PROCESSAMENTO';

export interface EventoSstImportacaoDTO {
  linha: number;
  tipo: string;
  dataOcorrencia: string;
  dataComunicacao: string;
  atividade: string;
  gestor: string;
  baseOperacional: string;
  equipe: string;
  veiculo: string;
  classificacao: string;
  processo: string;
  telemetria: string;
  filmagem: string;
  localOcorrencia: string;
  latitude: string;
  longitude: string;
  descricao: string;
  localLesao: string;
  naturezaLesao: string;
  agenteCausador: string;
  diasPerdidos: string;
  possuiCat: string;
  tipoCat: string;
  numeroCat: string;
  dataEmissaoCat: string;
  tecnicos: string;
  envolvidos: string;
  status: ImportacaoEventoSstStatus;
  mensagemErro: string | null;
  editing?: boolean;
  _tecnicosArray?: string[];
  _envolvidosArray?: any[];
}

export interface ImportacaoEventoSstResponse {
  registros: EventoSstImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoEventoSstRequest {
  eventos: EventoSstImportacaoDTO[];
}

// ==========================================================
// Evento — leitura
// ==========================================================

export interface TecnicoEventoResponse {
  id: string;
  idColaborador: string;
  nome: string;
  funcao: string | null;
}

export interface EnvolvidoEventoResponse {
  id: string;
  tipo: TipoEnvolvido;
  papel: PapelEnvolvido;
  idColaborador: string | null;
  nome: string;
  funcao: string | null;
  baseOperacional: string | null;
  empresa: string | null;
}

export interface AnexoEventoResponse {
  id: string;
  contexto: ContextoAnexoEvento;
  tipo: 'FOTO' | 'DOCUMENTO';
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  dataCriacao: string;
}

export interface EventoSstResponse {
  id: string;
  numeroRegistro: string;
  tipo: TipoEventoSst;
  status: StatusEventoSst;

  dataOcorrencia: string;
  dataComunicacao: string;
  horasAteComunicacao: number;
  comunicacaoAtrasada: boolean;

  idAtividade: string;
  atividadeNome: string;
  idGestor: string;
  gestorNome: string;
  gestorFuncao: string | null;
  gestorSubstituto: boolean;

  idBaseOperacional: string;
  baseNome: string;
  idEquipe: string | null;
  equipeCodigo: string | null;
  idVeiculo: string | null;
  veiculoPlaca: string | null;

  idClassificacao: string;
  classificacaoNome: string | null;
  idProcesso: string | null;
  processoNome: string | null;

  telemetria: RegistroEvidencia;
  filmagem: RegistroEvidencia;
  localOcorrencia: string | null;
  latitude: number | null;
  longitude: number | null;
  descricao: string;

  comLesao: boolean;
  idLocalLesao: string | null;
  localLesaoNome: string | null;
  idNaturezaLesao: string | null;
  naturezaLesaoNome: string | null;
  idAgenteCausador: string | null;
  agenteCausadorNome: string | null;
  diasPerdidos: number | null;

  possuiCat: boolean;
  tipoCat: TipoCat | null;
  numeroCat: string | null;
  dataEmissaoCat: string | null;

  tratativa: string | null;
  dataConclusao: string | null;
  dataCancelamento: string | null;
  motivoCancelamento: string | null;
  motivoReabertura: string | null;

  tecnicos: TecnicoEventoResponse[];
  envolvidos: EnvolvidoEventoResponse[];
  anexos: AnexoEventoResponse[];

  ativo: boolean;
  dataCriacao: string;
  usuarioCriacao: string | null;
}

export interface EventoSstResumoResponse {
  id: string;
  numeroRegistro: string;
  tipo: TipoEventoSst;
  status: StatusEventoSst;

  dataOcorrencia: string;
  dataComunicacao: string;
  horasAteComunicacao: number;
  comunicacaoAtrasada: boolean;

  atividadeNome: string;
  gestorNome: string;
  baseNome: string;
  equipeCodigo: string | null;
  veiculoPlaca: string | null;

  idClassificacao: string;
  classificacaoNome: string | null;

  comLesao: boolean;
  quantidadeAcidentados: number;
  diasPerdidos: number | null;
  possuiCat: boolean;
  localOcorrencia: string | null;
  ativo: boolean;
}

// ==========================================================
// Filtros
// ==========================================================

export interface EventoSstFilter {
  tipo?: TipoEventoSst[] | null;
  status?: StatusEventoSst[] | null;
  idClassificacao?: string[] | null;
  idAtividade?: string[] | null;
  idGestor?: string[] | null;
  idProcesso?: string[] | null;
  idBaseOperacional?: string[] | null;
  idCentroCusto?: string[] | null;
  idEquipe?: string[] | null;
  idVeiculo?: string[] | null;
  idTecnico?: string[] | null;
  idColaboradorEnvolvido?: string[] | null;
  idLocalLesao?: string[] | null;
  telemetria?: RegistroEvidencia[] | null;
  filmagem?: RegistroEvidencia[] | null;
  possuiCat?: boolean | null;
  comLesao?: boolean | null;
  dataOcorrenciaDe?: string | null;
  dataOcorrenciaAte?: string | null;
  dataComunicacaoDe?: string | null;
  dataComunicacaoAte?: string | null;
  /** Casa PARCIALMENTE — o trecho é resolvido em ids dentro do módulo de RH. */
  nomeColaborador?: string | null;
  /** Casa EXATAMENTE — o nome do terceiro é comparado por blind index. */
  nomeTerceiro?: string | null;
  buscaGlobal?: string | null;
  page?: number;
  size?: number;
  sort?: string;
}

// ==========================================================
// Dashboard de acidentalidade
// ==========================================================

export type GranularidadeDashboard = 'DIA' | 'MES' | 'ANO';

export interface DashboardEventosSstFilter {
  inicio?: string | null;
  fim?: string | null;
  granularidade?: GranularidadeDashboard | null;
  tipo?: TipoEventoSst[] | null;
  idClassificacao?: string[] | null;
  idAtividade?: string[] | null;
  idGestor?: string[] | null;
  idProcesso?: string[] | null;
  idBaseOperacional?: string[] | null;
  idCentroCusto?: string[] | null;
  idEquipe?: string[] | null;
}

export interface ResumoEventosDashboard {
  total: number;
  totalAnterior: number;
  variacaoPercentual: number;
  /** Métrica global: ignora período e filtros; nulo quando não há histórico. */
  diasSemEventos: number | null;
  porTipo: Record<TipoEventoSst, number>;
  comLesao: number;
  comLesaoAnterior: number;
  variacaoComLesao: number;
  semLesao: number;
  diasPerdidos: number;
  comCat: number;
  coberturaCat: number;
  mediaHorasComunicacao: number;
  comunicacoesAtrasadas: number;
  percentualAtrasadas: number;
  abertos: number;
  concluidos: number;
  desviosNoPeriodo: number;
  razaoDesviosPorLesao: number;
}

export interface SerieEventosItem {
  data: string;
  rotulo: string;
  total: number;
  comLesao: number;
  diasPerdidos: number;
}

/** Degrau da pirâmide de Bird, agregado por gravidade e consequência. */
export interface PiramideAcidentalidadeItem {
  /** Uma das cinco chaves semânticas da pirâmide, do topo para a base. */
  chave: string;
  rotulo: string;
  total: number;
  participacaoPercentual: number;
  razaoPorEventoComLesao: number;
}

export const CHAVE_DEGRAU_DESVIO = 'DESVIO_SEGURANCA';

export interface DiasSemAcidenteItem {
  idBaseOperacional: string;
  baseNome: string;
  ultimaOcorrencia: string | null;
  diasSemAcidente: number | null;
  comLesaoNoPeriodo: number;
}

export interface FaixaBacklogEventos {
  faixa: string;
  idadeMinimaDias: number;
  idadeMaximaDias: number | null;
  total: number;
  porStatus: Partial<Record<StatusEventoSst, number>>;
}

export interface RankingEventoItem {
  id: string;
  nome: string;
  total: number;
  percentual: number;
}

export interface DashboardEventosSstResponse {
  inicio: string;
  fim: string;
  granularidade: GranularidadeDashboard;
  geradoEm: string;
  resumo: ResumoEventosDashboard;
  serie: SerieEventosItem[];
  piramide: PiramideAcidentalidadeItem[];
  diasSemAcidente: DiasSemAcidenteItem[];
  backlog: FaixaBacklogEventos[];
  porClassificacao: RankingEventoItem[];
  porAtividade: RankingEventoItem[];
  porGestor: RankingEventoItem[];
  porBaseOperacional: RankingEventoItem[];
  porEquipe: RankingEventoItem[];
  porProcesso: RankingEventoItem[];
  porLocalLesao: RankingEventoItem[];
}

// ==========================================================
// Rótulos, cores e regras espelhadas do domínio
// ==========================================================

export const TIPO_EVENTO_LABELS: Record<TipoEventoSst, string> = {
  ACIDENTE_TIPICO: 'Acidente típico',
  ACIDENTE_TRAJETO: 'Acidente de trajeto',
  INCIDENTE_SEM_LESAO: 'Incidente sem lesão',
  DANO_MATERIAL: 'Dano material',
  DOENCA_OCUPACIONAL: 'Doença ocupacional'
};

export const TIPO_EVENTO_BADGE: Record<TipoEventoSst, string> = {
  ACIDENTE_TIPICO: 'bg-red-50 text-red-600',
  ACIDENTE_TRAJETO: 'bg-orange-50 text-orange-600',
  INCIDENTE_SEM_LESAO: 'bg-slate-50 text-slate-600',
  DANO_MATERIAL: 'bg-violet-50 text-violet-600',
  DOENCA_OCUPACIONAL: 'bg-rose-50 text-rose-600'
};

/**
 * Tipos que pressupõem alguém lesionado. É o que decide, no formulário, se os campos
 * de lesão aparecem — e o backend recusa o contrário com 422.
 */
export const TIPO_EVENTO_IMPLICA_LESAO: Record<TipoEventoSst, boolean> = {
  ACIDENTE_TIPICO: true,
  ACIDENTE_TRAJETO: true,
  INCIDENTE_SEM_LESAO: false,
  DANO_MATERIAL: false,
  DOENCA_OCUPACIONAL: true
};

/** Prazo legal de comunicação, em horas, por tipo. Constante da lei, não do cadastro. */
export const PRAZO_COMUNICACAO_HORAS: Record<TipoEventoSst, number> = {
  ACIDENTE_TIPICO: 24,
  ACIDENTE_TRAJETO: 24,
  INCIDENTE_SEM_LESAO: 72,
  DANO_MATERIAL: 72,
  DOENCA_OCUPACIONAL: 24
};

export const STATUS_EVENTO_LABELS: Record<StatusEventoSst, string> = {
  REGISTRADO: 'Registrado',
  EM_TRATATIVA: 'Em tratativa',
  CONCLUIDO: 'Concluído',
  CANCELADO: 'Cancelado'
};

export const STATUS_EVENTO_BADGE: Record<StatusEventoSst, string> = {
  REGISTRADO: 'bg-red-50 text-red-600',
  EM_TRATATIVA: 'bg-amber-50 text-amber-600',
  CONCLUIDO: 'bg-green-50 text-green-600',
  CANCELADO: 'bg-slate-100 text-slate-500'
};

/** Concluído e cancelado são imutáveis até a reabertura — a tela desabilita antes do 422. */
export function eventoEditavel(status: StatusEventoSst): boolean {
  return status === 'REGISTRADO' || status === 'EM_TRATATIVA';
}

export const TIPO_CAT_LABELS: Record<TipoCat, string> = {
  INICIAL: 'Inicial',
  REABERTURA: 'Reabertura',
  COMUNICACAO_OBITO: 'Comunicação de óbito'
};

export const REGISTRO_EVIDENCIA_LABELS: Record<RegistroEvidencia, string> = {
  SIM: 'Sim',
  NAO: 'Não',
  NAO_APLICAVEL: 'Não aplicável'
};

export const PAPEL_ENVOLVIDO_LABELS: Record<PapelEnvolvido, string> = {
  ACIDENTADO: 'Acidentado',
  TESTEMUNHA: 'Testemunha',
  ENVOLVIDO: 'Envolvido'
};

export const PAPEL_ENVOLVIDO_BADGE: Record<PapelEnvolvido, string> = {
  ACIDENTADO: 'bg-red-50 text-red-600',
  TESTEMUNHA: 'bg-slate-50 text-slate-600',
  ENVOLVIDO: 'bg-blue-50 text-blue-600'
};

export const TIPO_ENVOLVIDO_LABELS: Record<TipoEnvolvido, string> = {
  COLABORADOR: 'Colaborador',
  TERCEIRO: 'Terceiro / visitante'
};

export const CONTEXTO_ANEXO_EVENTO_LABELS: Record<ContextoAnexoEvento, string> = {
  EVENTO: 'Evento',
  CAT: 'CAT',
  TRATATIVA: 'Tratativa'
};

export const TIPO_CATALOGO_LABELS: Record<TipoCatalogoSst, string> = {
  CLASSIFICACAO_EVENTO: 'Classificação do evento',
  LOCAL_LESAO: 'Local da lesão',
  NATUREZA_LESAO: 'Natureza da lesão',
  AGENTE_CAUSADOR: 'Agente causador'
};

export function opcoesDe<T extends string>(labels: Record<T, string>): { label: string; value: T }[] {
  return (Object.keys(labels) as T[]).map((value) => ({ label: labels[value], value }));
}
