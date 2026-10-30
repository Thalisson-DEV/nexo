import { PagedResponse } from './shared.models';

export type ChamadoPrioridade = 'BAIXA' | 'NORMAL' | 'ALTA' | 'CRITICA';
export type ChamadoStatus =
  | 'ABERTO'
  | 'EM_ATENDIMENTO'
  | 'AGUARDANDO_SOLICITANTE'
  | 'RESOLVIDO'
  | 'FECHADO'
  | 'CANCELADO';

export type InteracaoChamadoTipo =
  | 'ABERTURA'
  | 'MENSAGEM_PUBLICA'
  | 'COMENTARIO_INTERNO'
  | 'ALTERACAO_STATUS'
  | 'ALTERACAO_PRIORIDADE'
  | 'ATRIBUICAO'
  | 'ENCAMINHAMENTO'
  | 'REABERTURA'
  | 'CANCELAMENTO'
  | 'EVIDENCIA_ADICIONADA';

export interface EquipeChamadoResumo {
  id: string;
  codigo: string;
  nome: string;
}

export interface TopicoChamado {
  id: string;
  codigo: string;
  nome: string;
  descricao?: string | null;
  prioridadePadrao: ChamadoPrioridade;
  equipePadrao: EquipeChamadoResumo;
  slaPrimeiraRespostaHoras: number;
  slaResolucaoHoras: number;
  pausarSlaAguardandoSolicitante: boolean;
  prazoReaberturaHoras: number;
  ordemExibicao: number;
  ativo: boolean;
}

export interface EvidenciaChamado {
  id: string;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  dataCriacao: string;
}

export interface Chamado {
  id: string;
  protocolo: string;
  topico: TopicoChamado;
  equipeAtendimento: EquipeChamadoResumo;
  assunto: string;
  descricao: string;
  prioridade: ChamadoPrioridade;
  status: ChamadoStatus;
  usuarioSolicitanteId: string;
  usuarioResponsavelId?: string | null;
  solucao?: string | null;
  dataAbertura: string;
  dataLimitePrimeiraResposta: string;
  dataLimiteResolucao: string;
  pausarSlaAguardandoSolicitante: boolean;
  prazoReaberturaHoras: number;
  dataPrimeiraResposta?: string | null;
  dataInicioAtendimento?: string | null;
  dataInicioPausaSla?: string | null;
  totalSegundosPausaSla: number;
  dataResolucao?: string | null;
  dataFechamento?: string | null;
  dataUltimaInteracao: string;
  slaPrimeiraRespostaVencido: boolean;
  slaResolucaoVencido: boolean;
  slaPausado: boolean;
  anexos: EvidenciaChamado[];
  dataCriacao: string;
  dataUltimaAlteracao?: string | null;
}

export interface InteracaoChamado {
  id: string;
  tipo: InteracaoChamadoTipo;
  mensagem?: string | null;
  interno: boolean;
  usuarioAutorId: string;
  statusAnterior?: ChamadoStatus;
  statusNovo?: ChamadoStatus;
  equipeAnteriorId?: string;
  equipeNovaId?: string;
  responsavelAnteriorId?: string;
  responsavelNovoId?: string;
  dataEvento: string;
}

export interface CriarChamadoRequest {
  topicoId: string;
  assunto: string;
  descricao: string;
  prioridade?: ChamadoPrioridade;
}

export interface InteracaoChamadoRequest {
  mensagem: string;
  interno: boolean;
}

export interface AtualizarAtendimentoRequest {
  status?: ChamadoStatus;
  equipeId?: string;
  prioridade?: ChamadoPrioridade;
  responsavelId?: string;
  solucao?: string | null;
}

export interface ChamadoFiltros {
  buscaGlobal?: string;
  protocolo?: string;
  topicoId?: string;
  equipeId?: string;
  prioridade?: ChamadoPrioridade;
  status?: ChamadoStatus;
  usuarioSolicitanteId?: string;
  usuarioResponsavelId?: string;
  dataAberturaInicio?: string;
  dataAberturaFim?: string;
  slaPrimeiraRespostaVencido?: boolean;
  slaResolucaoVencido?: boolean;
  ativo?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export interface EquipeChamado {
  id: string;
  codigo: string;
  nome: string;
  descricao?: string | null;
  membros: string[];
  ativo: boolean;
  dataCriacao: string;
  dataUltimaAlteracao?: string | null;
}

export interface EquipeChamadoRequest {
  codigo: string;
  nome: string;
  descricao?: string;
  membros: string[];
}

export interface EquipeChamadoFiltros {
  buscaGlobal?: string;
  codigo?: string;
  ativo?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export interface TopicoChamadoRequest {
  codigo: string;
  nome: string;
  descricao?: string;
  prioridadePadrao: ChamadoPrioridade;
  equipePadraoId: string;
  slaPrimeiraRespostaHoras: number;
  slaResolucaoHoras: number;
  pausarSlaAguardandoSolicitante: boolean;
  prazoReaberturaHoras: number;
  ordemExibicao: number;
}

export interface TopicoChamadoFiltros {
  buscaGlobal?: string;
  codigo?: string;
  equipePadraoId?: string;
  ativo?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export type PaginaChamados = PagedResponse<Chamado>;
export type PaginaTopicosChamado = PagedResponse<TopicoChamado>;
export type PaginaEquipesChamado = PagedResponse<EquipeChamado>;
