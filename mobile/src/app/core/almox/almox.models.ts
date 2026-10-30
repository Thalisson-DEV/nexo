// Contratos do almoxarifado consumidos pelo perfil administrativo (gestão de solicitações
// comerciais e movimentações de estoque). Espelham os DTOs do backend (`/v1/solicitacoes-material`,
// `/v1/movimentacoes-estoque`, `/v1/saldos`) — só os campos que o app usa.

export interface Pagina<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export type StatusSolicitacao = 'PENDENTE' | 'APROVADO' | 'REJEITADO' | 'ENTREGUE_PARCIAL' | 'ENTREGUE' | 'ESTORNADO';
export type AcaoSolicitacao = 'APROVAR' | 'REJEITAR' | 'ENTREGAR' | string;
export type Condicao = 'NOVO' | 'USADO' | 'RECUPERADO' | 'SUCATA';

export interface TipoEstoque { id?: string; nome?: string }

export interface Estoque {
  id: string;
  nomeIdentificador: string;
  tipoEstoque?: TipoEstoque | null;
  idEquipe?: string | null;
  idBaseOperacional?: string | null;
  ativo: boolean;
}

export interface Material {
  id: string;
  codigo: string;
  descricao: string;
  unidadeMedida?: string | null;
  detalhaSerie: boolean;
  ativo?: boolean;
}

export interface Motivo { id: string; nome: string; descricao?: string | null; ativo?: boolean }

export interface ResumoSolicitacao {
  id: string;
  status: StatusSolicitacao;
  possuiPendencia?: boolean;
  motivosPendencia?: string[];
  encerrada?: boolean;
  estoqueOrigemNome?: string;
  estoqueDestinoNome?: string;
  estoqueOrigemNomeIdentificador?: string;
  estoqueDestinoNomeIdentificador?: string;
  tipoTecnico?: string | null;
  dataSolicitacao: string;
  dataVerificacao?: string | null;
  dataEntrega?: string | null;
  dataPrevista?: string | null;
  foraPrazoSaque?: boolean;
  idRequisitante?: string;
  totalItens: number;
  acoesPermitidas?: AcaoSolicitacao[];
}

export interface ItemSolicitacao {
  id: string;
  material: Material;
  quantidade: number;
  quantidadeEntregue: number;
  quantidadeEntregaLiquida?: number;
  quantidadePendente?: number;
  numSerie?: string | null;
  motivoPendencia?: Motivo | null;
  justificativaPendencia?: string | null;
}

export interface ItemOperacao {
  id: string;
  idItemSolicitacao?: string | null;
  codigoMaterial: string;
  descricaoMaterial: string;
  quantidade: number;
  numeroSerie?: string | null;
}

export interface OperacaoSolicitacao {
  id: string;
  tipo: 'ENTREGA' | 'ESTORNO';
  idColaboradorSaque?: string | null;
  saqueForaEquipe: boolean;
  idUsuarioOperador: string;
  realizadaEm: string;
  motivo?: string | null;
  itens: ItemOperacao[];
}

export interface Solicitacao {
  id: string;
  estoqueOrigem: Estoque;
  estoqueDestino: Estoque;
  tipoTecnico?: string | null;
  idRequisitante: string;
  idAprovador?: string | null;
  idEntregadorAlmox?: string | null;
  idEquipe?: string | null;
  idColaboradorSaque?: string | null;
  saqueForaEquipe?: boolean;
  status: StatusSolicitacao;
  dataSolicitacao: string;
  dataVerificacao?: string | null;
  dataEntrega?: string | null;
  dataPrevista?: string | null;
  foraPrazoSaque?: boolean;
  observacao?: string | null;
  motivoRejeicao?: Motivo | null;
  justificativaRejeicao?: string | null;
  motivoPendencia?: Motivo | null;
  justificativaPendencia?: string | null;
  possuiPendencia?: boolean;
  itens: ItemSolicitacao[];
  operacoes?: OperacaoSolicitacao[];
  encerrada?: boolean;
  motivoEncerramento?: string | null;
  acoesPermitidas?: AcaoSolicitacao[];
}

export interface FiltroSolicitacoes {
  statuses?: StatusSolicitacao[];
  idEstoqueOrigem?: string | null;
  idEstoqueDestino?: string | null;
  numSerie?: string | null;
  dataInicio?: string | null;
  dataFim?: string | null;
  previstaDe?: string | null;
  previstaAte?: string | null;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ItemEntregaRequest {
  idItemSolicitacao: string;
  quantidade: number;
  novaQuantidadeSolicitada: number;
  numSerie?: string;
}

export interface EntregaRequest {
  assinaturaAlmox: string;
  assinaturaEquipe: string;
  idColaboradorSaque: string;
  saqueForaEquipe: boolean;
  itens: ItemEntregaRequest[];
}

export interface PendenciaRequest {
  idMotivoPendencia: string;
  justificativa: string;
  idItem?: string | null;
}

export interface EstornoParcialRequest {
  itens: { idItemSolicitacao: string; quantidade: number }[];
}

export interface Saldo {
  id: string;
  estoque: Estoque;
  material: Material;
  quantidade: number;
  condicao?: Condicao | null;
}

export interface SaldoSerie { id: string; numSerie: string; material: Material; condicao?: Condicao | null; estoque?: Pick<Estoque, 'id' | 'nomeIdentificador'> | null }

export interface MembroEquipe { idColaborador: string; nomeColaborador: string; vinculoAtivo?: boolean }

export interface Colaborador { id: string; nomeCompleto: string; idUsuario?: string | null }

export interface Usuario { id: string; login: string; idColaborador?: string | null; colaborador?: { nomeCompleto?: string } | null }

export type TipoMovimentacao =
  | 'ENTRADA_AVULSA' | 'BAIXA_AVULSA' | 'TRANSFERENCIA' | 'ENTREGA' | 'ESTORNO' | 'APLICACAO' | 'RETIRADA' | 'DEVOLUCAO';

export interface ResumoMovimentacao {
  idReferenciaOperacao: string;
  tipo: TipoMovimentacao;
  dataHora: string;
  operadorNome: string;
  estoqueOrigem?: Estoque | null;
  estoqueDestino?: Estoque | null;
  idSolicitacao?: string | null;
}

export interface ItemMovimentado {
  id: string;
  material: Material;
  quantidade: number;
  numSerie?: string | null;
  condicao?: Condicao | null;
}

export interface Movimentacao extends ResumoMovimentacao {
  itens: ItemMovimentado[];
  assinaturaEntregador?: string | null;
  assinaturaRecebedor?: string | null;
}

export interface FiltroMovimentacoes {
  tipo?: TipoMovimentacao | null;
  idEstoque?: string | null;
  numSerie?: string | null;
  dataInicio?: string | null;
  dataFim?: string | null;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ItemMovimentacaoRequest {
  idMaterial: string;
  quantidade: number;
  numSerie?: string;
  condicao: Condicao;
}

export interface MovimentacaoRequest {
  idEstoqueOrigem?: string;
  idEstoqueDestino?: string;
  itens: ItemMovimentacaoRequest[];
  assinaturaEntregador?: string;
  assinaturaRecebedor?: string;
}
