import {
  BaseOperacionalResponse,
  ColaboradorResponse,
  EquipeResponse,
  ProcessoNegocioResponse,
  VeiculoResponse
} from './rh.models';
import {
  ColaboradorInspecionadoItem,
  EquipeInspecaoItem,
  ExposicaoNormalizadaItem,
  FaixaBacklog,
  GranularidadeDashboard,
  IndicadorComparativo,
  InspetorMetaItem,
  ItemCriticoDashboard,
  PendenciaAgrupadaResponse,
  PeriodoDashboard,
  PrioridadeDashboard,
  QualidadeDadosDashboard,
  ResumoColaboradores,
  ResumoInspecoes,
  ResumoMeta,
  SerieDesviosItem,
  SerieMetaItem,
  TendenciaDesviosItem,
  VolumePorGravidade
} from './sst-dashboard.models';

// ============================================================
// Tipos de Inspeção (SST) — cadastro e parametrização
// ============================================================
export interface TipoInspecaoResponse {
  id: string;
  nome: string;
  descricao?: string | null;
  ordem?: number;
  ativo: boolean;
  totalInspecoes?: number;
  dataCriacao?: string;
  dataUltimaAlteracao?: string;
}

export interface TipoInspecaoRequest {
  nome: string;
  descricao?: string | null;
  ordem?: number;
}

export interface TipoInspecaoFilter {
  ativo?: boolean | null;
  buscaGlobal?: string | null;
  page?: number;
  size?: number;
  sort?: string;
}

// ============================================================
// Enums
// ============================================================
export type TipoResposta = 'MULTIPLA_ESCOLHA' | 'MULTIPLA_SELECAO' | 'TEXTO' | 'NUMERO' | 'DATA';
export type StatusInspecao = 'EM_ANDAMENTO' | 'CONCLUIDA' | 'CANCELADA';
export type GravidadeDesvio = 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA';
export type StatusDesvio = 'ABERTO' | 'EM_TRATAMENTO' | 'RESOLVIDO';

/** Contexto do anexo: evidência da não-conformidade ou da ação corretiva. */
export type ContextoAnexo = 'DESVIO' | 'ACAO_CORRETIVA';
/** Tipo do anexo, derivado pelo servidor a partir do MIME. */
export type TipoAnexo = 'FOTO' | 'DOCUMENTO';

/** Tipos objetivos possuem opções; tipos livres não. */
export const TIPOS_RESPOSTA_OBJETIVOS: TipoResposta[] = ['MULTIPLA_ESCOLHA', 'MULTIPLA_SELECAO'];

export function isTipoObjetivo(tipo: TipoResposta): boolean {
  return TIPOS_RESPOSTA_OBJETIVOS.includes(tipo);
}

// ============================================================
// Bloco 1 — Template (Formulário dinâmico) — Requests
// ============================================================
export interface OpcaoRespostaRequest {
  texto: string;
  geraDesvio: boolean;
  gravidade?: GravidadeDesvio | null;
  padrao?: boolean;
}

export interface PerguntaRequest {
  enunciado: string;
  tipoResposta: TipoResposta;
  obrigatoria: boolean;
  opcoes: OpcaoRespostaRequest[];
}

export interface GrupoPerguntaRequest {
  titulo: string;
  perguntas: PerguntaRequest[];
}

export interface CriarFormularioRequest {
  titulo: string;
  descricao?: string;
  grupos: GrupoPerguntaRequest[];
}

export interface AtualizarFormularioDadosRequest {
  titulo: string;
  descricao?: string;
}

export interface ReordenarRequest {
  idsNaOrdem: string[];
}

// ============================================================
// Bloco 1 — Template — Responses
// ============================================================
export interface OpcaoRespostaResponse {
  id: string;
  texto: string;
  geraDesvio: boolean;
  gravidade?: GravidadeDesvio | null;
  padrao: boolean;
  ordem: number;
}

export interface PerguntaResponse {
  id: string;
  enunciado: string;
  tipoResposta: TipoResposta;
  obrigatoria: boolean;
  ordem: number;
  opcoes: OpcaoRespostaResponse[];
}

export interface GrupoPerguntaResponse {
  id: string;
  titulo: string;
  ordem: number;
  perguntas: PerguntaResponse[];
}

export interface FormularioInspecaoResponse {
  id: string;
  titulo: string;
  descricao?: string;
  ativo: boolean;
  emUso: boolean;
  grupos: GrupoPerguntaResponse[];
}

export interface FormularioInspecaoResumoResponse {
  id: string;
  titulo: string;
  descricao?: string;
  ativo: boolean;
  totalPerguntas: number;
}

// ============================================================
// Bloco 2 — Execução (Inspeção lançada) — Requests
// ============================================================
export interface RespostaInspecaoRequest {
  idPergunta: string;
  opcoesSelecionadas: string[];
  valorTexto?: string | null;
  valorNumero?: number | null;
  valorData?: string | null;
}

export interface DesvioInspecaoRequest {
  idPergunta: string;
  idOpcaoResposta: string;
  descricao?: string;
  acaoCorretiva?: string;
  prazo?: string | null;
  colaboradores: string[];
  status?: StatusDesvio;
}

export interface RegistrarInspecaoRequest {
  idTipoInspecao: string;
  idFormulario: string;
  idProcesso: string;
  idVeiculo?: string | null;
  idEquipe?: string | null;
  idBaseOperacional: string;
  dataRealizacao: string;
  observacoes?: string;
  latitude?: number | null;
  longitude?: number | null;
  colaboradoresInspecionados: string[];
  respostas: RespostaInspecaoRequest[];
  desvios: DesvioInspecaoRequest[];
  /**
   * IDs devolvidos por `POST /v1/sst/inspecoes/anexos`. **≥ 1** — nenhuma inspeção
   * é aceita sem ao menos uma fotografia comprovando que aconteceu de fato.
   * Lista vazia ou ausente → 400.
   */
  evidencias: string[];
}

export interface TratarDesvioRequest {
  status: StatusDesvio;
  acaoCorretiva?: string;
  prazo?: string | null;
}

export interface AtualizarCabecalhoInspecaoRequest {
  idTipoInspecao: string;
  idProcesso: string;
  idBaseOperacional: string;
  idVeiculo?: string | null;
  idEquipe?: string | null;
  dataRealizacao: string;
  observacoes?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  colaboradoresInspecionados: string[];
  justificativa?: string | null;
}

export interface AtualizarInspecaoRequest {
  idFormulario?: string;
  idTipoInspecao: string;
  idProcesso: string;
  idBaseOperacional: string;
  idVeiculo?: string | null;
  idEquipe?: string | null;
  dataRealizacao: string;
  observacoes?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  colaboradoresInspecionados: string[];
  respostas: RespostaInspecaoRequest[];
  desvios: DesvioInspecaoRequest[];
  evidencias: string[];
  justificativa?: string | null;
}

export interface HistoricoInspecaoAlteracaoItem {
  caminho: string;
  valorAnterior?: unknown;
  valorNovo?: unknown;
}

export interface HistoricoInspecaoPayload {
  comando: string;
  alteracoes?: HistoricoInspecaoAlteracaoItem[];
  snapshots?: Record<string, unknown>;
}

export interface HistoricoInspecaoEvento {
  id: string;
  idInspecao: string;
  sequencia: number;
  tipo: string;
  autor?: { id?: string; nome?: string } | null;
  origem: string;
  justificativa?: string | null;
  ocorridoEm: string;
  payload?: HistoricoInspecaoPayload | null;
}

export interface IntegridadeCadeiaInspecao {
  escopo: string;
  idCadeia: string;
  status: 'INTEGRO' | 'VIOLADO' | 'NAO_VERIFICADO';
  sequenciaInicial?: number;
  sequenciaFinal?: number;
}

export interface HistoricoInspecaoResponse {
  eventos: HistoricoInspecaoEvento[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
  integridade: IntegridadeCadeiaInspecao[];
}

// ============================================================
// Bloco 2 — Execução — Responses
// ============================================================
export interface RespostaInspecaoResponse {
  id: string;
  idPergunta: string;
  opcoesSelecionadas: string[];
  valorTexto?: string | null;
  valorNumero?: number | null;
  valorData?: string | null;
}

/**
 * Metadados de uma evidência fotográfica da inspeção. O binário é servido pelo
 * backend sob o mesmo JWT (`GET /v1/sst/inspecoes/{idInspecao}/anexos/{idAnexo}`);
 * não existe URL pública.
 */
export interface EvidenciaInspecaoResponse {
  id: string;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  /** ISO 8601 com offset, instante do servidor. */
  dataCriacao: string;
}

/** Metadados de um anexo (evidência) de desvio. O binário é baixado sob demanda. */
export interface AnexoDesvioResponse {
  id: string;
  contexto: ContextoAnexo;
  tipo: TipoAnexo;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  dataCriacao: string;
}

export interface DesvioSegurancaResponse {
  id: string;
  idPergunta: string;
  idOpcaoResposta: string;
  gravidade: GravidadeDesvio;
  descricao?: string;
  acaoCorretiva?: string;
  prazo?: string | null;
  status: StatusDesvio;
  dataResolucao: string | null;
  colaboradores: ColaboradorResponse[];
  /** Sempre uma lista (vazia quando não há evidências). */
  anexos: AnexoDesvioResponse[];
}

export interface InspecaoSegurancaResponse {
  id: string;
  tipoInspecao?: TipoInspecaoResponse;
  formulario: FormularioInspecaoResumoResponse;
  processo: ProcessoNegocioResponse;
  processoInspetor?: ProcessoNegocioResponse | null;
  idInspetor: string;
  nomeInspetor: string;
  veiculo?: VeiculoResponse | null;
  equipe?: EquipeResponse | null;
  baseOperacional: BaseOperacionalResponse | null;
  dataRealizacao: string;
  status: StatusInspecao;
  observacoes?: string;
  latitude?: number | null;
  longitude?: number | null;
  colaboradoresInspecionados: ColaboradorResponse[];
  respostas: RespostaInspecaoResponse[];
  desvios: DesvioSegurancaResponse[];
  /**
   * Evidências fotográficas da inspeção. Sempre uma lista — inspeções lançadas
   * **antes** da exigência vêm com `[]`, e isso não é erro nem pendência.
   */
  evidencias: EvidenciaInspecaoResponse[];
}

// ============================================================
// Filtros
// ============================================================
/**
 * Todos os recortes por id aceitam múltiplos valores: dentro da mesma dimensão
 * a combinação é **OU**; entre dimensões diferentes, **E**. Seleção vazia não
 * restringe nada — o serializador simplesmente omite o parâmetro.
 */
export interface InspecaoFilters {
  idTipoInspecao?: string[] | null;
  idFormulario?: string[] | null;
  idProcesso?: string[] | null;
  idProcessoInspetor?: string[] | null;
  /** Não enviar em `/inspecoes/minhas` — o inspetor já é o usuário logado. */
  idInspetor?: string[] | null;
  idVeiculo?: string[] | null;
  idEquipe?: string[] | null;
  idBaseOperacional?: string[] | null;
  /** Regional comercial da base operacional (entidade Centro de Faturamento). */
  idCentroCusto?: string[] | null;
  idColaborador?: string[] | null;
  status?: StatusInspecao[] | null;
  gravidadeDesvio?: GravidadeDesvio[] | null;
  apenasComDesvios?: boolean | null;
  dataRealizacaoDe?: string | null;
  dataRealizacaoAte?: string | null;
  buscaGlobal?: string | null;
  apenasMinhas?: boolean | null;
  page?: number;
  size?: number;
  sort?: string;
}

export interface DashboardSstFilter {
  /** ISO 8601 com offset explícito (ex.: 2026-08-01T00:00:00-03:00). */
  inicio?: string | null;
  fim?: string | null;
  idTipoInspecao?: string[] | null;
  idProcesso?: string[] | null;
  idProcessoInspetor?: string[] | null;
  idEquipe?: string[] | null;
  idBaseOperacional?: string[] | null;
  /** Regional comercial da base operacional (entidade Centro de Faturamento). */
  idCentroCusto?: string[] | null;
  idInspetor?: string[] | null;
  idFormulario?: string[] | null;
  /** Omitido = o backend escolhe e devolve a granularidade efetivamente usada. */
  granularidade?: GranularidadeDashboard | null;
  gravidade?: GravidadeDesvio[] | null;
  status?: StatusDesvio[] | null;
}

/**
 * Filtros que restringem apenas o realizado, não a meta — com qualquer um deles
 * ativo o `%` atingido fica distorcido e o bloco de metas não é comparável.
 *
 * `idBaseOperacional` e `idCentroCusto` ficam de fora de propósito: eles
 * restringem também a meta rateada e o efetivo ativo, então o `%` continua
 * comparável com eles ativos.
 */
export const FILTROS_INCOMPATIVEIS_COM_META: (keyof DashboardSstFilter)[] = [
  'idProcesso',
  'idProcessoInspetor',
  'idEquipe',
  'idFormulario'
];

// ============================================================
// Dashboards
// ============================================================
export interface RankingItem {
  id: string;
  nome: string | null;
  total: number;
  percentual: number;
}

export interface SerieTemporalItem {
  data: string;
  total: number;
}

/** Linha da tabela de desempenho por colaborador (campos definidos pelo backend). */
export interface ColaboradorDesvioKpi {
  idColaborador: string;
  nome: string | null;
  totalDesvios: number;
  abertos: number;
  emTratamento: number;
  resolvidos: number;
  criticos: number;
}

/**
 * Contrato agrupado do dashboard de inspeções. Substituiu a versão anterior, que
 * tinha 30+ campos soltos na raiz (`taxaConformidade`, `rankingInspetores`,
 * `serieInspecoes`, `exposicao*`, `indicadores`, ...) — nenhum deles existe mais.
 */
export interface DashboardInspecoesResponse {
  inicio: string;
  fim: string;
  /** Granularidade efetivamente usada — rotule o eixo por ela, não pelo filtro enviado. */
  granularidade: GranularidadeDashboard;
  atualizadoEm: string;

  resumo: ResumoInspecoes;
  meta: ResumoMeta;
  colaboradores: ResumoColaboradores;

  serieTemporal: SerieMetaItem[];
  porInspetor: InspetorMetaItem[];
  porEquipe: EquipeInspecaoItem[];
  porColaborador: ColaboradorInspecionadoItem[];

  porTipoInspecao: RankingItem[];
  porProcesso: RankingItem[];
  porProcessoInspetor: RankingItem[];
  porBaseOperacional: RankingItem[];
  porFormulario: RankingItem[];
}

export interface DashboardDesviosResponse {
  inicio: string;
  fim: string;
  totalDesvios: number;
  totalPendentes: number;
  totalAbertos: number;
  totalEmTratamento: number;
  totalResolvidos: number;
  totalCriticos: number;
  totalVencidos: number;
  totalNoPrazo: number;
  totalSemPrazo: number;
  totalColaboradoresEnvolvidos: number;
  totalColaboradoresReincidentes: number;
  totalInspecoesComDesvio: number;
  totalPorGravidade: Record<string, number>;
  totalPorStatus: Record<string, number>;
  taxaResolucao: number;
  percentualCriticos: number;
  percentualVencidos: number;
  percentualReincidencia: number;
  mediaDesviosPorColaborador: number;
  mediaDesviosPorInspecao: number;
  tempoMedioResolucaoDias: number;
  rankingColaboradores: RankingItem[];
  rankingPerguntas: RankingItem[];
  rankingProcessos: RankingItem[];
  rankingProcessosInspetores: RankingItem[];
  rankingEquipes: RankingItem[];
  rankingBasesOperacionais: RankingItem[];
  rankingInspetores: RankingItem[];
  pendenciasPorProcesso: PendenciaAgrupadaResponse[];
  pendenciasPorProcessoInspetor: PendenciaAgrupadaResponse[];
  pendenciasPorBaseOperacional: PendenciaAgrupadaResponse[];
  pendenciasPorEquipe: PendenciaAgrupadaResponse[];
  desempenhoPorColaborador: ColaboradorDesvioKpi[];
  desviosPorDia: SerieTemporalItem[];
  periodo: PeriodoDashboard;
  atualizadoEm: string;
  qualidadeDados: QualidadeDadosDashboard;
  indicadores: Record<string, IndicadorComparativo> & {
    totalDesvios: IndicadorComparativo;
    desviosCriticos: IndicadorComparativo;
    inspecoesComDesvio: IndicadorComparativo;
    taxaResolucao: IndicadorComparativo;
    backlog: IndicadorComparativo;
    resolvidosNaCoorte: IndicadorComparativo;
    vencidos: IndicadorComparativo;
  };
  tempoMedianoResolucaoDias: number;
  tempoP90ResolucaoDias: number;
  backlogPorIdade: FaixaBacklog[];
  volumePorGravidade: VolumePorGravidade[];
  exposicaoPorProcesso: ExposicaoNormalizadaItem[];
  exposicaoPorProcessoInspetor: ExposicaoNormalizadaItem[];
  exposicaoPorEquipe: ExposicaoNormalizadaItem[];
  exposicaoPorBaseOperacional: ExposicaoNormalizadaItem[];
  itensCriticosRecorrentes: ItemCriticoDashboard[];
  prioridades: PrioridadeDashboard[];
  serieDesvios: SerieDesviosItem[];
  granularidade: GranularidadeDashboard;
  tendenciaDesvios: TendenciaDesviosItem[];
}

// ============================================================
// Rótulos / metadados de UI (cores e labels dos enums)
// ============================================================
export const TIPO_RESPOSTA_LABELS: Record<TipoResposta, string> = {
  MULTIPLA_ESCOLHA: 'Múltipla Escolha',
  MULTIPLA_SELECAO: 'Múltipla Seleção',
  TEXTO: 'Texto',
  NUMERO: 'Número',
  DATA: 'Data'
};

export const GRAVIDADE_LABELS: Record<GravidadeDesvio, string> = {
  BAIXA: 'Baixa',
  MEDIA: 'Média',
  ALTA: 'Alta',
  CRITICA: 'Crítica'
};

/** Classes Tailwind (bg/text) por gravidade — alinhadas ao padrão de badges das telas existentes. */
export const GRAVIDADE_BADGE: Record<GravidadeDesvio, string> = {
  BAIXA: 'bg-slate-50 text-slate-600 dark:bg-slate-800 dark:text-slate-300 dark:border dark:border-slate-700',
  MEDIA: 'bg-amber-50 text-amber-600 dark:bg-amber-950/60 dark:text-amber-300 dark:border dark:border-amber-800/60',
  ALTA: 'bg-orange-50 text-orange-600 dark:bg-orange-950/60 dark:text-orange-300 dark:border dark:border-orange-800/60',
  CRITICA: 'bg-red-50 text-red-600 dark:bg-red-950/60 dark:text-red-300 dark:border dark:border-red-800/60'
};

export const STATUS_DESVIO_LABELS: Record<StatusDesvio, string> = {
  ABERTO: 'Aberto',
  EM_TRATAMENTO: 'Em Tratamento',
  RESOLVIDO: 'Resolvido'
};

export const STATUS_DESVIO_BADGE: Record<StatusDesvio, string> = {
  ABERTO: 'bg-red-50 text-red-600 dark:bg-red-950/60 dark:text-red-300 dark:border dark:border-red-800/60',
  EM_TRATAMENTO: 'bg-amber-50 text-amber-600 dark:bg-amber-950/60 dark:text-amber-300 dark:border dark:border-amber-800/60',
  RESOLVIDO: 'bg-green-50 text-green-600 dark:bg-emerald-950/60 dark:text-emerald-300 dark:border dark:border-emerald-800/60'
};

export const STATUS_INSPECAO_LABELS: Record<StatusInspecao, string> = {
  EM_ANDAMENTO: 'Em Andamento',
  CONCLUIDA: 'Concluída',
  CANCELADA: 'Cancelada'
};

export const STATUS_INSPECAO_BADGE: Record<StatusInspecao, string> = {
  EM_ANDAMENTO: 'bg-amber-50 text-amber-600 dark:bg-amber-950/60 dark:text-amber-300 dark:border dark:border-amber-800/60',
  CONCLUIDA: 'bg-green-50 text-green-600 dark:bg-emerald-950/60 dark:text-emerald-300 dark:border dark:border-emerald-800/60',
  CANCELADA: 'bg-red-50 text-red-600 dark:bg-red-950/60 dark:text-red-300 dark:border dark:border-red-800/60'
};

// ============================================================
// Anexos / Evidências de desvios (validação local + rótulos)
// ============================================================
export const CONTEXTO_ANEXO_LABELS: Record<ContextoAnexo, string> = {
  DESVIO: 'Evidência da não-conformidade',
  ACAO_CORRETIVA: 'Evidência da ação corretiva'
};

/** Limite por arquivo (15 MB) — barrado no servidor com 413; validamos antes de enviar. */
export const ANEXO_TAMANHO_MAX_BYTES = 15 * 1024 * 1024;

/** MIME types aceitos pelo backend. */
export const ANEXO_MIME_ACEITOS: readonly string[] = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/heic',
  'image/heif',
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'text/plain',
  'text/csv'
];

/** Extensões aceitas (fallback quando o navegador não informa o MIME — ex.: HEIC). */
export const ANEXO_EXTENSOES_ACEITAS: readonly string[] = [
  'jpg',
  'jpeg',
  'png',
  'webp',
  'heic',
  'heif',
  'pdf',
  'doc',
  'docx',
  'xls',
  'xlsx',
  'txt',
  'csv'
];

/** Valor do atributo `accept` para o seletor de arquivos. Usar apenas extensões para imagens (.jpg, .png) sem MIME types image/* evita que o Android exiba a opção nativa de Câmera, mantendo apenas Galeria e Arquivos. */
export const ANEXO_ACCEPT =
  '.jpg,.jpeg,.png,.webp,.heic,.heif,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/plain,text/csv,.pdf,.doc,.docx,.xls,.xlsx,.txt,.csv';

/**
 * Valida um arquivo localmente antes do envio. Retorna a mensagem de erro
 * (alinhada ao backend) ou `null` quando o arquivo é válido.
 */
export function validarArquivoAnexo(file: File): string | null {
  if (!file || file.size === 0) return 'Envie um arquivo não vazio.';
  if (file.size > ANEXO_TAMANHO_MAX_BYTES) {
    return 'Arquivo acima do limite de 15 MB.';
  }
  const mimeOk = !!file.type && ANEXO_MIME_ACEITOS.includes(file.type.toLowerCase());
  const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
  const extOk = ANEXO_EXTENSOES_ACEITAS.includes(ext);
  if (!mimeOk && !extOk) {
    return 'Tipo de arquivo não suportado. Aceitos: imagens (PNG, JPG, WebP, etc.), PDF, Word, Excel ou Texto.';
  }
  return null;
}

// ============================================================
// Evidência fotográfica da inspeção (validação local + rótulos)
// ============================================================
/** Limite por arquivo (15 MB) — o backend recusa com 422. */
export const EVIDENCIA_TAMANHO_MAX_BYTES = 15 * 1024 * 1024;

/** **Somente imagens.** PDF e Word são recusados pelo backend (422). */
export const EVIDENCIA_MIME_ACEITOS: readonly string[] = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/heic',
  'image/heif'
];

/** Extensões aceitas (fallback quando o navegador não informa o MIME — ex.: HEIC). */
export const EVIDENCIA_EXTENSOES_ACEITAS: readonly string[] = [
  'jpg',
  'jpeg',
  'png',
  'webp',
  'heic',
  'heif'
];

/** Valor do atributo `accept` do seletor de arquivos de evidência. */
export const EVIDENCIA_ACCEPT =
  'image/jpeg,image/png,image/webp,image/heic,image/heif,.jpg,.jpeg,.png,.webp,.heic,.heif';

/**
 * Valida uma evidência localmente antes do envio — evita gastar upload em
 * arquivo que o backend vai recusar. Retorna a mensagem de erro (alinhada ao
 * backend) ou `null` quando o arquivo é válido.
 */
export function validarEvidenciaInspecao(file: File): string | null {
  if (!file || file.size === 0) return 'Envie um arquivo não vazio.';
  if (file.size > EVIDENCIA_TAMANHO_MAX_BYTES) {
    return 'Arquivo acima do limite de 15 MB.';
  }
  const mimeOk = !!file.type && EVIDENCIA_MIME_ACEITOS.includes(file.type.toLowerCase());
  const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
  const extOk = EVIDENCIA_EXTENSOES_ACEITAS.includes(ext);
  if (!mimeOk && !extOk) {
    return 'A evidência precisa ser uma fotografia (JPEG, PNG, WebP ou HEIC). PDF e Word não são aceitos.';
  }
  return null;
}

/**
 * HEIC/HEIF não renderizam em `<img>` na maioria dos navegadores desktop — para
 * essas, exiba um cartão com ícone e nome em vez de uma miniatura quebrada.
 */
export function evidenciaRenderizavelNoBrowser(contentType?: string | null, nomeArquivo?: string | null): boolean {
  const tipo = (contentType ?? '').toLowerCase();
  if (tipo) return /^image\/(jpe?g|png|webp|gif|avif)$/.test(tipo);
  const ext = (nomeArquivo ?? '').split('.').pop()?.toLowerCase() ?? '';
  return ['jpg', 'jpeg', 'png', 'webp', 'gif', 'avif'].includes(ext);
}

/** Maior dimensão (px) de uma foto após a compressão para upload. */
export const ANEXO_IMAGEM_MAX_DIMENSAO = 1920;
/** Qualidade JPEG (0–1) usada ao recomprimir fotos. */
export const ANEXO_IMAGEM_QUALIDADE = 0.8;
/** Abaixo deste tamanho a imagem já é considerada leve e não é recomprimida. */
const ANEXO_IMAGEM_LIMIAR_RECOMPRIME = 1_200_000;

/**
 * Reduz uma foto (redimensiona para {@link ANEXO_IMAGEM_MAX_DIMENSAO} e
 * recomprime em JPEG) antes do upload — fotos de câmera no mobile costumam ter
 * 5–12 MB, o que causa timeouts/uploads lentos. Formatos que o canvas não
 * decodifica com segurança (HEIC/HEIF) ou não-imagens são devolvidos intactos.
 * Qualquer falha na decodificação também devolve o arquivo original.
 */
export async function comprimirImagemAnexo(file: File): Promise<File> {
  const tipo = (file.type || '').toLowerCase();
  const ehCompativel = /^image\/(jpe?g|png|webp)$/.test(tipo);
  if (!ehCompativel) return file;
  if (typeof createImageBitmap !== 'function' || typeof document === 'undefined') return file;

  let bitmap: ImageBitmap | null = null;
  try {
    // `from-image` aplica a orientação EXIF ao decodificar — sem isso, fotos de
    // celular tiradas na vertical seriam recomprimidas deitadas.
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const { width, height } = bitmap;
    const escala = Math.min(1, ANEXO_IMAGEM_MAX_DIMENSAO / Math.max(width, height));
    // Já é pequena e leve: não vale recomprimir.
    if (escala === 1 && file.size <= ANEXO_IMAGEM_LIMIAR_RECOMPRIME) return file;

    const w = Math.max(1, Math.round(width * escala));
    const h = Math.max(1, Math.round(height * escala));
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d');
    if (!ctx) return file;
    ctx.drawImage(bitmap, 0, 0, w, h);

    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, 'image/jpeg', ANEXO_IMAGEM_QUALIDADE)
    );
    // Não melhorou (ex.: PNG pequeno já otimizado): mantém o original.
    if (!blob || blob.size >= file.size) return file;

    const nome = file.name.replace(/\.[^.]+$/, '') + '.jpg';
    return new File([blob], nome, { type: 'image/jpeg', lastModified: Date.now() });
  } catch {
    return file;
  } finally {
    bitmap?.close?.();
  }
}
