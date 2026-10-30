import { PagedResponse } from './shared.models';

export type TipoCorpoGovernanca = 'CIPA' | 'BRIGADA';
export type RegimeComposicaoGovernanca = 'CIPA_DIMENSIONADA' | 'REPRESENTANTE_NOMEADO';
export type StatusCorpoGovernanca = 'EM_PREPARACAO' | 'ATIVO' | 'ENCERRADO';
export type PapelMembroGovernanca =
  | 'PRESIDENTE'
  | 'VICE_PRESIDENTE'
  | 'MEMBRO'
  | 'REPRESENTANTE_NOMEADO'
  | 'COORDENADOR_GERAL'
  | 'CHEFE'
  | 'LIDER'
  | 'BRIGADISTA';
export type RepresentacaoCipa = 'EMPREGADOR' | 'EMPREGADOS';
export type TitularidadeCipa = 'TITULAR' | 'SUPLENTE';
export type OrigemMembroCipa = 'DESIGNADO' | 'ELEITO';
export type StatusReuniaoGovernanca =
  | 'AGENDADA'
  | 'EM_REALIZACAO'
  | 'AGUARDANDO_ATA'
  | 'AGUARDANDO_ASSINATURAS'
  | 'FINALIZADA'
  | 'CANCELADA';
export type StatusDocumentoGovernanca = 'SEM_VALIDADE' | 'VIGENTE' | 'A_VENCER' | 'VENCIDO';
export type StatusMatrizTreinamento =
  | 'REGULAR'
  | 'A_VENCER'
  | 'VENCIDO'
  | 'AUSENTE'
  | 'INCOMPATIVEL'
  | 'CONTEXTO_PENDENTE';
export type StatusAcaoGovernanca = 'ABERTA' | 'EM_ANDAMENTO' | 'CONCLUIDA' | 'CANCELADA';

export interface GovernancaPage<T> extends PagedResponse<T> {}

export interface CorpoGovernancaResumo {
  id: string;
  tipo: TipoCorpoGovernanca;
  regimeComposicao?: RegimeComposicaoGovernanca | null;
  status: StatusCorpoGovernanca;
  nome: string;
  idBaseOperacional: string;
  baseNome: string;
  unidadeGovernanca: string;
  uf: string;
  inicioPeriodo: string;
  fimPeriodo: string;
  quantidadeMembrosAtivos: number;
  grauRisco?: number | null;
  idPerfilNormativo?: string | null;
  hipoteseRepresentanteNomeadoConfirmada: boolean;
  conformidadeNormativaValidada: boolean;
  codigoAvaliacaoNormativa?: string | null;
  perfilCodigoSnapshot?: string | null;
  perfilEdicaoSnapshot?: string | null;
  perfilUrlSnapshot?: string | null;
  dataAtivacao?: string | null;
  dataEncerramento?: string | null;
  ativo?: boolean;
  versao: number;
}

export interface MembroCorpoGovernanca {
  id: string;
  idCorpo: string;
  idColaborador: string;
  nomeSnapshot: string;
  idFuncaoSnapshot?: string | null;
  funcaoSnapshot?: string | null;
  idBaseSnapshot?: string | null;
  baseSnapshot?: string | null;
  papel: PapelMembroGovernanca;
  representacao?: RepresentacaoCipa | null;
  titularidade?: TitularidadeCipa | null;
  origem?: OrigemMembroCipa | null;
  cicloEleitoral?: string | null;
  classificacaoEleitoral?: number | null;
  atoDesignacao?: string | null;
  reeleito?: boolean;
  turnoSnapshot?: string | null;
  inicioParticipacao: string;
  fimParticipacao?: string | null;
  motivoSaida?: string | null;
  dataRegistroSaida?: string | null;
  idMembroSubstituido?: string | null;
  idMembroSubstituto?: string | null;
  vacanciaPendente: boolean;
  conformidadeNormativa: boolean;
  codigoDivergenciaNormativa?: string | null;
  versao: number;
}

export interface CorpoGovernancaDetalhe {
  corpo: CorpoGovernancaResumo;
  membros: MembroCorpoGovernanca[];
  basesAbrangidas: Array<{ id: string; idBaseOperacional: string; baseNome: string }>;
}

export interface CriarCorpoGovernancaRequest {
  tipo: TipoCorpoGovernanca;
  regimeComposicao: RegimeComposicaoGovernanca | null;
  nome: string;
  idBaseOperacional: string;
  unidadeGovernanca: string;
  uf: string;
  inicioPeriodo: string;
  fimPeriodo: string;
  grauRisco: number;
  idPerfilNormativo: string | null;
  hipoteseRepresentanteNomeadoConfirmada: boolean;
  idsBasesAbrangidas: string[];
}

export interface AtualizarCorpoGovernancaRequest {
  versao: number;
  nome: string;
  unidadeGovernanca: string;
  uf: string;
  inicioPeriodo: string;
  fimPeriodo: string;
  grauRisco: number;
  idPerfilNormativo: string | null;
  hipoteseRepresentanteNomeadoConfirmada: boolean;
}

export interface DadosMembroCorpoGovernanca {
  idColaborador: string;
  papel: PapelMembroGovernanca;
  representacao: RepresentacaoCipa | null;
  titularidade: TitularidadeCipa | null;
  origem: OrigemMembroCipa | null;
  cicloEleitoral: string | null;
  classificacaoEleitoral: number | null;
  atoDesignacao: string | null;
  reeleito: boolean;
  inicioParticipacao: string;
  turnoDisponivel: string | null;
}

export interface InclusaoMembroCorpoGovernancaRequest {
  versao: number;
  membro: DadosMembroCorpoGovernanca;
}

export interface SaidaMembroCorpoGovernancaRequest {
  versao: number;
  dataEfeito: string;
  motivo: string;
}

export interface SubstituicaoMembroCorpoGovernancaRequest {
  versao: number;
  dataEfeito: string;
  motivoSaida: string;
  fundamentoProcedimental: string;
  sucessor: DadosMembroCorpoGovernanca;
}

export interface PerfilNormativoGovernanca {
  id: string;
  versao: number;
  codigo: string;
  versaoPerfil: number;
  escopo: 'NACIONAL' | 'ESTADUAL' | 'INTERNO';
  tipoCorpo: TipoCorpoGovernanca;
  jurisdicao?: string | null;
  orgaoEmissor?: string | null;
  atoNormativo?: string | null;
  edicao: string;
  inicioVigencia: string;
  fimVigencia?: string | null;
  urlOficial?: string | null;
  confirmado: boolean;
  ativo: boolean;
}

export interface ReuniaoGovernancaResumo {
  id: string;
  idCorpo: string;
  idBaseOperacional: string;
  tipoCorpo: TipoCorpoGovernanca;
  tipo: 'ORDINARIA' | 'EXTRAORDINARIA' | 'SIMULADO' | 'OUTRO';
  status: StatusReuniaoGovernanca;
  dataHora: string;
  duracaoMinutos: number;
  localOuMeio: string;
  pauta: string;
  totalParticipantes?: number;
  totalPresentes?: number;
  possuiAtaAprovada?: boolean;
  versao: number;
}

export interface ParticipacaoReuniaoGovernanca {
  id: string;
  idColaborador: string;
  nome: string;
  funcao?: string;
  papel: string;
  presenca: 'PRESENTE' | 'AUSENTE_JUSTIFICADA' | 'AUSENTE_NAO_JUSTIFICADA' | 'NAO_REGISTRADA';
  justificativaAusencia?: string;
}

export interface VersaoAtaGovernanca {
  id: string;
  numero: number;
  origem: 'MANUAL' | 'IA';
  status: 'RASCUNHO' | 'APROVADA';
  conteudo: string;
  idVersaoBase?: string;
  aprovadaEm?: string;
  dataCriacao: string;
}

export interface NormalizacaoAtaGovernanca {
  id: string;
  idRevisaoTranscricao?: string;
  numero: number;
  status: 'EM_PROCESSAMENTO' | 'RASCUNHO' | 'CONFIRMADA' | 'FALHA';
  conteudo?: {
    topicos: Array<{ texto: string; trechoOrigem: string; requerConfirmacao?: boolean }>;
    decisoesCandidatas: Array<{ texto: string; trechoOrigem: string; requerConfirmacao?: boolean }>;
    acoesCandidatas: Array<{ texto: string; trechoOrigem: string; requerConfirmacao?: boolean }>;
    incertezas: Array<{ texto: string; trechoOrigem: string; requerConfirmacao?: boolean }>;
  };
  codigoFalha?: string;
}

export interface ResumoDocumentoGovernanca {
  total: number;
  vencidos: number;
  aVencer: number;
  pendentes: number;
}

export interface SecaoModeloAta {
  id?: string;
  codigo?: string;
  titulo: string;
  orientacao?: string;
  obrigatoria: boolean;
  protegida?: boolean;
  ordem: number;
}

export interface ModeloAtaGovernanca {
  id: string;
  nome: string;
  tipoCorpo: TipoCorpoGovernanca;
  idBaseAplicavel?: string;
  revisao: number;
  status: 'RASCUNHO' | 'PUBLICADO' | 'RETIRADO';
  orientacoesGerais?: string;
  modeloInicial: boolean;
  idArquivoDocx?: string;
  hashArquivoDocx?: string;
  nomeArquivoDocx?: string;
  secoes?: SecaoModeloAta[];
  versao: number;
}

export interface EvidenciaGovernanca {
  id: string;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  hashConteudo?: string;
  legenda?: string;
  dataCriacao: string;
  enviadaEm?: string;
}

export interface CategoriaDocumentoGovernanca {
  id: string;
  codigo: string;
  nome: string;
  contexto: TipoCorpoGovernanca | 'AMBOS';
  natureza: string;
  escopoTitular: 'BASE' | 'CORPO' | 'REUNIAO' | 'MEMBRO' | 'COLABORADOR';
  confirmada: boolean;
  exigeDataValidade: boolean;
  semValidade: boolean;
  ativo: boolean;
}

export interface DocumentoGovernancaResumo {
  id: string;
  categoriaCodigo: string;
  categoriaNome: string;
  contexto: TipoCorpoGovernanca;
  idBase: string;
  baseNome?: string;
  nomeArquivo: string;
  numeroDocumento?: string;
  emissor?: string;
  dataEmissao?: string;
  dataValidade?: string;
  statusValidade: StatusDocumentoGovernanca;
  situacaoConferencia: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';
  atual: boolean;
  versao: number;
}

export interface TreinamentoGovernanca {
  id: string;
  codigo: string;
  nome: string;
  descricao?: string;
  aplicaCipa: boolean;
  aplicaBrigada: boolean;
  ativo: boolean;
  versao: number;
}

export interface CelulaMatrizTreinamento {
  /** Chave opcional para compatibilidade com a infraestrutura paginada; a matriz usa chave composta. */
  id?: string;
  idColaborador: string;
  nomeColaborador: string;
  funcaoNome?: string;
  papelCorpo?: string;
  idTreinamento: string;
  treinamentoNome: string;
  status: StatusMatrizTreinamento;
  dataValidadeEfetiva?: string;
  motivos: string[];
  idDocumentoSelecionado?: string;
}

export interface AcaoGovernancaResumo {
  id: string;
  idCorpo: string;
  idBaseOperacional: string;
  titulo: string;
  descricao: string;
  tipo: 'CORRETIVA' | 'PREVENTIVA' | 'INSPECAO' | 'OUTRA';
  prioridade: 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA';
  idResponsavel: string;
  responsavelNome?: string;
  prazo: string;
  status: StatusAcaoGovernanca;
  statusPrazo: 'NO_PRAZO' | 'VENCE_HOJE' | 'VENCIDA' | 'ENCERRADA';
  versao: number;
}

export interface ModeloChecklistGovernanca {
  id: string;
  idFormulario: string;
  formulario?: string;
  formularioTitulo?: string;
  tipoCorpo: TipoCorpoGovernanca;
  nome: string;
  orientacao?: string;
  referenciaNormativa?: string;
  ativo: boolean;
  utilizado?: boolean;
  emUso?: boolean;
  versao: number;
}

export interface ItemAtencaoGovernanca {
  tipo: string;
  severidade: 'VENCIDO' | 'URGENTE' | 'PENDENTE' | 'INFORMATIVO';
  idRegistro: string;
  titulo: string;
  prazo?: string;
  destino: string;
}

export interface DashboardGovernanca {
  calculadoEm: string;
  periodo: { inicio: string; fim: string };
  indicadores: {
    corposAtivos: number;
    reunioesPendentesAta: number;
    documentosVencidos: number;
    documentosAVencer: number;
    documentosPendentesConferencia: number;
    treinamentosIrregulares: number;
    inspecoesPendentes: number;
    acoesAbertas: number;
    acoesVencidas: number;
  };
  definicoes: Record<string, string>;
  atencao: ItemAtencaoGovernanca[];
}
