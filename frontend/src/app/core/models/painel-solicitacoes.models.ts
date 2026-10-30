/**
 * Painel analítico de solicitações de material (`GET /v1/dashboard/solicitacoes/analitico`).
 * Uma linha por solicitação do recorte — mais as abertas criadas antes do período (backlog) — com a
 * etapa operacional (mesma regra do Kanban), os marcos de tempo e as quantidades atendidas.
 */

export type ProcessoSolicitacao = 'COMERCIAL' | 'TECNICO';

export type EtapaSolicitacao =
  | 'PENDENTE_APROVACAO'
  | 'PENDENTE_PLANEJAMENTO'
  | 'PLANEJADO'
  | 'PRONTO_PARA_SEPARACAO'
  | 'EM_SEPARACAO'
  | 'SEPARADO'
  | 'PENDENTE_RETIRADA'
  | 'PENDENTE_DEVOLUCAO'
  | 'CONCLUIDO'
  | 'REJEITADO'
  | 'CANCELADO';

export type MacroEtapaSolicitacao = 'PLANEJAMENTO' | 'SEPARACAO' | 'ENTREGA' | 'RECUSADO';

export interface SolicitacaoDoPainel {
  id: string;
  contexto: ProcessoSolicitacao;
  status: string;
  etapa: EtapaSolicitacao | null;
  macroEtapa: MacroEtapaSolicitacao | null;
  /** 221, 921, 222 ou 922 (só técnico). */
  tipoTecnico: string | null;
  tipoObra: string | null;
  ocorrencia: string | null;
  idObra: string | null;
  numeroObra: string | null;
  statusSeparacao: string | null;
  dataSolicitacao: string;
  dataVerificacao: string | null;
  dataEntrega: string | null;
  /** yyyy-MM-dd */
  dataPrevista: string | null;
  separacaoIniciadaEm: string | null;
  separacaoConcluidaEm: string | null;
  encerradaEm: string | null;
  estoqueOrigem: string | null;
  estoqueDestino: string | null;
  idAlmoxarifado: string | null;
  almoxarifado: string | null;
  idEquipe: string | null;
  equipe: string | null;
  baseOperacional: string | null;
  processo: string | null;
  motivoRejeicao: string | null;
  possuiPendencia: boolean;
  motivosPendencia: string | null;
  foraPrazoSaque: boolean;
  saqueForaEquipe: boolean;
  temReserva: boolean;
  itens: number;
  quantidadeSolicitada: number;
  quantidadeAtendida: number;
  /** Criada antes do período e ainda aberta. */
  anteriorAoPeriodo: boolean;
}

export interface MaterialDoPainel {
  contexto: ProcessoSolicitacao;
  sentido: 'SAIDA' | 'DEVOLUCAO';
  codigo: string;
  descricao: string;
  unidade: string | null;
  solicitacoes: number;
  quantidadeSolicitada: number;
  quantidadeAtendida: number;
}

export interface ResumoPeriodoAnterior {
  contexto: ProcessoSolicitacao;
  total: number;
  concluidas: number;
  rejeitadas: number;
  cicloMedioHoras: number | null;
  aprovacaoMediaHoras: number | null;
}

export interface PainelSolicitacoesResponse {
  geradoEm: string;
  inicio: string;
  fim: string;
  limite: number;
  truncado: boolean;
  solicitacoes: SolicitacaoDoPainel[];
  materiais: MaterialDoPainel[];
  periodoAnterior: ResumoPeriodoAnterior[];
}

export interface PainelSolicitacoesFiltros {
  dataInicio?: string;
  dataFim?: string;
  contexto?: ProcessoSolicitacao;
  idBaseOperacional?: string;
  idProcesso?: string;
  idEquipe?: string;
  idAlmoxarifado?: string;
}

/** O que exportar do painel (mesmo modelo de inspeções e desvios: conteúdo, nível e arquivo). */
export type ConteudoExportacaoPainel = 'SOLICITACOES' | 'MATERIAIS' | 'EQUIPES' | 'ALMOXARIFADOS';
export type NivelExportacaoPainel = 'RESUMO' | 'DETALHADO';
export type SituacaoExportacaoPainel = 'ABERTA' | 'ATRASADA' | 'CONCLUIDA' | 'RECUSADA';

export interface ExportacaoPainelSolicitacoes {
  conteudo: ConteudoExportacaoPainel;
  nivel: NivelExportacaoPainel;
  situacao?: SituacaoExportacaoPainel;
  incluirBacklog: boolean;
  formato: 'csv' | 'xlsx';
}
