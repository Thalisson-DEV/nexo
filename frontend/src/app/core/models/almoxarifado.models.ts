import { PageMetadata } from './shared.models';

// --- Avaliação Técnica de Material ---
export interface AvaliacaoMaterialRequest {
  nome: string;
}

export interface AvaliacaoMaterialResponse {
  id: string;
  nome: string;
  ativo: boolean;
}

export interface AvaliacaoMaterialImportacaoDTO {
  linha: number;
  nome?: string;
  status: 'AGUARDANDO_PROCESSAMENTO' | 'COM_ERRO' | 'EM_PROCESSAMENTO';
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoAvaliacaoMaterialResponse {
  registros: AvaliacaoMaterialImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoAvaliacaoMaterialRequest {
  avaliacoes: AvaliacaoMaterialImportacaoDTO[];
}

export interface PageResponseAvaliacaoMaterialResponse {
  content: AvaliacaoMaterialResponse[];
  page: PageMetadata;
}

// --- Centro de Investimento ---
export interface CentroInvestimentoRequest {
  codigo: string;
}

export interface CentroInvestimentoResponse {
  id: string;
  codigo: string;
  ativo: boolean;
}

export interface CentroInvestimentoImportacaoDTO {
  linha: number;
  codigo?: string;
  status: 'AGUARDANDO_PROCESSAMENTO' | 'COM_ERRO' | 'EM_PROCESSAMENTO';
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoCentroInvestimentoResponse {
  registros: CentroInvestimentoImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoCentroInvestimentoRequest {
  centros: CentroInvestimentoImportacaoDTO[];
}

export interface PageResponseCentroInvestimentoResponse {
  content: CentroInvestimentoResponse[];
  page: PageMetadata;
}

// --- Material ---
export interface MaterialRequest {
  codigo: string;
  descricao: string;
  unidadeMedida?: string;
  suprMatrFlag?: string;
  idAvaliacaoTecnica?: string;
  idCentroInvestimento?: string;
  detalhaSerie?: boolean;
}

export interface MaterialResponse {
  id: string;
  codigo: string;
  descricao: string;
  unidadeMedida?: string;
  suprMatrFlag?: string;
  processo?: { id: string; nome?: string; codigo?: string } | null;
  avaliacaoTecnica?: AvaliacaoMaterialResponse;
  centroInvestimento?: CentroInvestimentoResponse;
  classificacaoTecnica?: 'COMUM' | 'CONCRETO';
  detalhaSerie: boolean;
  ativo: boolean;
}

export interface MaterialImportacaoDTO {
  linha: number;
  codigo?: string;
  descricao?: string;
  unidadeMedida?: string;
  suprMatrFlag?: string;
  nomeAvaliacao?: string;
  codigoCentroInvestimento?: string;
  detalhaSerie?: boolean;
  status: 'AGUARDANDO_PROCESSAMENTO' | 'COM_ERRO' | 'EM_PROCESSAMENTO';
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoMaterialResponse {
  registros: MaterialImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoMaterialRequest {
  materiais: MaterialImportacaoDTO[];
}

export interface PageResponseMaterialResponse {
  content: MaterialResponse[];
  page: PageMetadata;
}

// --- Tipos de Estoque ---
export interface TipoEstoqueRequest {
  nome: string;
}

export interface TipoEstoqueResponse {
  id: string;
  nome: string;
  ativo: boolean;
}

export interface TipoEstoqueImportacaoDTO {
  linha: number;
  nome?: string;
  status: 'AGUARDANDO_PROCESSAMENTO' | 'COM_ERRO' | 'EM_PROCESSAMENTO';
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoTipoEstoqueResponse {
  registros: TipoEstoqueImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoTipoEstoqueRequest {
  tipos: TipoEstoqueImportacaoDTO[];
}

export interface PageResponseTipoEstoqueResponse {
  content: TipoEstoqueResponse[];
  page: PageMetadata;
}

// --- Estoques ---
export interface EstoqueRequest {
  nomeIdentificador: string;
  idTipoEstoque: string;
  idEquipe?: string;
  idBaseOperacional: string;
}

export interface EstoqueImportacaoDTO {
  linha: number;
  nomeIdentificador?: string;
  nomeTipoEstoque?: string;
  codigoEquipe?: string;
  status: 'AGUARDANDO_PROCESSAMENTO' | 'COM_ERRO' | 'EM_PROCESSAMENTO';
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoEstoqueResponse {
  registros: EstoqueImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoEstoqueRequest {
  estoques: EstoqueImportacaoDTO[];
}

export interface PageResponseEstoqueResponse {
  content: EstoqueResponse[];
  page: PageMetadata;
}

// --- Motivos de Rejeição ---
export interface MotivoRejeicaoRequest {
  nome: string;
  descricao: string;
}

export interface MotivoRejeicaoResponse {
  id: string;
  nome: string;
  descricao: string;
  ativo: boolean;
  dataCriacao: string;
  usuarioCriacao: string;
}

export interface PageResponseMotivoRejeicaoResponse {
  content: MotivoRejeicaoResponse[];
  page: PageMetadata;
}

export interface RejeitarSolicitacaoRequest {
  idMotivoRejeicao: string;
  justificativa: string;
}

export interface MotivoPendenciaRequest {
  nome: string;
  descricao: string;
}

export interface MotivoPendenciaResponse {
  id: string;
  nome: string;
  descricao: string;
  ativo: boolean;
}

export interface ItemPendenciaRequest {
  idItem: string;
  idMotivoPendencia: string;
  justificativa: string;
}

export interface RegistrarPendenciaRequest {
  idMotivoPendencia?: string;
  justificativa?: string;
  idItem?: string | null;
  idsItens?: string[] | null;
  itensPendencias?: ItemPendenciaRequest[] | null;
}

// --- Solicitações de Material ---
export type SolicitacaoStatus = 'PENDENTE' | 'APROVADO' | 'REJEITADO' | 'ENTREGUE_PARCIAL' | 'ENTREGUE' | 'ESTORNADO';

export interface SolicitacaoSummaryResponse {
  id: string;
  possuiPendencia?: boolean;
  /** Motivos de pendência abertos na solicitação e nos itens. */
  motivosPendencia?: string[];
  /** Saldo pendente encerrado (ex.: entrega parcial expirada): não aceita nova entrega. */
  encerrada?: boolean;
  estoqueOrigemNome?: string;
  estoqueDestinoNome?: string;
  estoqueOrigemNomeIdentificador: string;
  estoqueDestinoNomeIdentificador: string;
  status: SolicitacaoStatus;
  tipoTecnico?: string | null;
  dataSolicitacao: string;
  dataVerificacao?: string;
  dataEntrega?: string;
  dataPrevista?: string | null;
  foraPrazoSaque?: boolean;
  idRequisitante: string;
  idAprovador?: string;
  idEntregadorAlmox?: string;
  idEquipe?: string | null;
  idColaboradorSaque?: string | null;
  saqueForaEquipe?: boolean;
  totalItens: number;
  statusSeparacao?: 'AGUARDANDO_AGENDAMENTO' | 'PRONTO_PARA_SEPARACAO' | 'EM_SEPARACAO' | 'SEPARADO' | null;
  versao?: number;
  contexto?: 'COMERCIAL' | 'TECNICO' | null;
}

export interface ItemSolicitacaoResponse {
  id: string;
  material: MaterialResponse;
  quantidade: number;
  quantidadeEntregue: number;
  numSerie?: string;
  quantidadeEntregueBruta?: number;
  quantidadeEntregaEstornada?: number;
  quantidadeEntregaLiquida?: number;
  quantidadeDevolvidaBruta?: number;
  quantidadeDevolucaoEstornada?: number;
  quantidadeDevolucaoLiquida?: number;
  quantidadePendente?: number;
  motivoPendencia?: MotivoPendenciaResponse | null;
  justificativaPendencia?: string | null;
  /** Séries que o almoxarifado definiu para o item antes da entrega. */
  seriesReservadas?: string[];
}

/** Consumo de um material desde a última solicitação comercial do mesmo estoque. */
export interface ConsumoAnteriorItem {
  idMaterial: string;
  ultimaSolicitacao: {
    id: string;
    dataSolicitacao: string;
    /** Primeira entrega do material naquela solicitação: início da janela de consumo. */
    dataEntrega: string;
    quantidadeSolicitada: number;
    quantidadeRecebida: number;
  } | null;
  quantidadeAplicada: number | null;
  quantidadeDevolvida: number | null;
  /** Saldo NOVO logo antes da entrega da última solicitação; null quando não houve uma. */
  saldoAntes?: number | null;
  /** Saldo NOVO atual do estoque de destino. */
  saldoAtual: number;
}

/** Uma movimentação do estoque de destino no período do consumo; quantidade + entrou, − saiu. */
export interface ConsumoAnteriorMovimentacao {
  id: string;
  tipo: TipoMovimentacao;
  dataHora: string;
  quantidade: number;
  condicao: string;
  numSerie?: string | null;
  idReferenciaOperacao?: string | null;
  solicitacao?: { id: string; contexto: 'COMERCIAL' | 'TECNICO' } | null;
  /** O outro estoque da movimentação (almoxarifado, outra equipe). */
  contraparte?: { id: string; nomeIdentificador: string } | null;
  /** Nota em que o material foi aplicado ou retirado. */
  nota?: { idExecucao: string; referencia: string | null; tipoServico: string | null } | null;
}

/** As movimentações por trás do consumo de um material e a conta do saldo novo no período. */
export interface ConsumoAnteriorDetalheResponse {
  idMaterial: string;
  /** Entrega da última solicitação ou, sem ela, o começo dos últimos 90 dias. */
  inicio: string | null;
  fim: string;
  /** Sem solicitação anterior: a conta mostra como o saldo chegou ao que é nos últimos 90 dias. */
  semSolicitacaoAnterior?: boolean;
  saldoInicial: number | null;
  saldoFinal: number | null;
  saldoAtual: number;
  movimentacoes: ConsumoAnteriorMovimentacao[];
}

export interface ConsumoAnteriorSolicitacaoResponse {
  idSolicitacao: string;
  idEstoque: string;
  /** Fim da janela: primeira entrega desta solicitação ou o momento da consulta. */
  consultadoAte: string;
  itens: ConsumoAnteriorItem[];
}

export interface ItemOperacaoSolicitacaoComercialResponse {
  id: string;
  idItemSolicitacao?: string | null;
  idMaterial: string;
  codigoMaterial: string;
  descricaoMaterial: string;
  quantidade: number;
  numeroSerie?: string | null;
  idItemOperacaoOriginal?: string | null;
}

export interface OperacaoSolicitacaoComercialResponse {
  id: string;
  tipo: 'ENTREGA' | 'ESTORNO';
  idEquipe?: string | null;
  idColaboradorSaque?: string | null;
  saqueForaEquipe: boolean;
  idUsuarioOperador: string;
  realizadaEm: string;
  motivo?: string | null;
  idOperacaoOriginal?: string | null;
  itens: ItemOperacaoSolicitacaoComercialResponse[];
}

export interface EstoqueResponse {
  id: string;
  nomeIdentificador: string;
  tipoEstoque: TipoEstoqueResponse;
  idEquipe?: string;
  idObra?: string;
  idBaseOperacional?: string;
  ativo: boolean;
}

export interface SolicitacaoResponse {
  id: string;
  estoqueOrigem: EstoqueResponse;
  estoqueDestino: EstoqueResponse;
  tipoTecnico?: string | null;
  idRequisitante: string;
  idAprovador?: string;
  idEntregadorAlmox?: string;
  idEquipe?: string | null;
  idColaboradorSaque?: string | null;
  saqueForaEquipe?: boolean;
  assinaturaAlmox?: string;
  assinaturaEquipe?: string;
  status: SolicitacaoStatus;
  dataSolicitacao: string;
  dataVerificacao?: string;
  dataEntrega?: string;
  dataPrevista?: string | null;
  foraPrazoSaque?: boolean;
  motivoRejeicao?: MotivoRejeicaoResponse;
  justificativaRejeicao?: string;
  motivoPendencia?: MotivoPendenciaResponse | null;
  justificativaPendencia?: string | null;
  possuiPendencia?: boolean;
  itens: ItemSolicitacaoResponse[];
  operacoes?: OperacaoSolicitacaoComercialResponse[];
  encerrada?: boolean;
  encerradaEm?: string | null;
  motivoEncerramento?: string | null;
  statusSeparacao?: 'AGUARDANDO_AGENDAMENTO' | 'PRONTO_PARA_SEPARACAO' | 'EM_SEPARACAO' | 'SEPARADO' | null;
  versao?: number;
  acoesPermitidas?: string[];
  contexto?: 'COMERCIAL' | 'TECNICO' | null;
  observacao?: string | null;
}

export interface ItemRequest {
  idMaterial: string;
  quantidade: number;
  numSerie?: string;
}

export interface SolicitacaoRequest {
  idEstoqueOrigem: string;
  idEstoqueDestino?: string;
  dataPrevista: string;
  idEquipe: string;
  idColaboradorSaque?: string | null;
  saqueForaEquipe: boolean;
  itens: ItemRequest[];
}

export interface ItemEdicaoAprovacaoRequest {
  idItemSolicitacao: string;
  novaQuantidade: number;
}

export interface NovoItemAprovacaoRequest {
  idMaterial: string;
  quantidade: number;
}

export interface AprovarSolicitacaoRequest {
  itensEditados?: ItemEdicaoAprovacaoRequest[];
  itensAdicionados?: NovoItemAprovacaoRequest[];
  itensRemovidos?: string[];
  /** Ausente mantém a origem atual. */
  idEstoqueOrigem?: string;
  /** `yyyy-MM-dd`; ausente mantém a data atual. */
  dataPrevista?: string;
}

export interface ItemEstornoRequest {
  idItemSolicitacao: string;
  quantidade: number;
}

export interface EstornoParcialRequest {
  itens: ItemEstornoRequest[];
}

export interface ItemEntregaRequest {
  idItemSolicitacao: string;
  quantidade: number;
  novaQuantidadeSolicitada?: number;
  numSerie?: string;
}

export interface NovoItemEntregaRequest {
  idMaterial: string;
  quantidade: number;
  quantidadeSolicitada?: number;
  numSerie?: string;
}

export interface EntregaSolicitacaoRequest {
  assinaturaAlmox: string;
  assinaturaEquipe: string;
  idColaboradorSaque: string;
  saqueForaEquipe: boolean;
  itens: ItemEntregaRequest[];
  itensAdicionados?: NovoItemEntregaRequest[];
  itensRemovidos?: string[];
  /** Almoxarifado de onde sai o material, quando muda no balcão. */
  idEstoqueOrigem?: string;
}

export interface PageResponseSolicitacaoSummaryResponse {
  content: SolicitacaoSummaryResponse[];
  page: PageMetadata;
}

// --- Movimentações de Estoque ---
export type TipoMovimentacao =
  | 'ENTRADA_AVULSA'
  | 'BAIXA_AVULSA'
  | 'TRANSFERENCIA'
  | 'ENTREGA'
  | 'ESTORNO'
  | 'APLICACAO'
  | 'RETIRADA'
  | 'DEVOLUCAO';
export type CondicaoMaterial = 'NOVO' | 'USADO' | 'RECUPERADO' | 'SUCATA';
export interface Link {
  href: string;
  hreflang?: string;
  title?: string;
  type?: string;
  deprecation?: string;
  profile?: string;
  name?: string;
  templated?: boolean;
}

export interface Links {
  [key: string]: Link;
}

export interface ItemMovimentacaoRequest {
  idMaterial: string;
  quantidade: number;
  numSerie?: string;
  condicao?: string;
}

export interface MovimentacaoBulkRequest {
  idEstoqueOrigem?: string;
  idEstoqueDestino?: string;
  itens: ItemMovimentacaoRequest[];
  assinaturaEntregador?: string;
  assinaturaRecebedor?: string;
}

export interface ItemDevolucaoComercialRequest {
  idMaterial: string;
  quantidade: number;
  condicao?: string;
  numSerie?: string;
  /** Falso: o destino não guarda o material e ele é baixado na mesma operação. */
  guardarNoEstoque?: boolean;
}

export interface DevolucaoComercialRequest {
  idEquipe: string;
  idEstoqueDestino: string;
  itens: ItemDevolucaoComercialRequest[];
  observacao?: string;
}

export interface ItemDevolucaoTecnicaRequest {
  idMaterial: string;
  tipo: 'DEVOLUCAO_COMUM' | 'DEVOLUCAO_CONCRETO';
  quantidade: number;
  condicao: string;
  numSerie?: string;
  guardarNoEstoque: boolean;
}

export interface DevolucaoTecnicaRequest {
  idObra: string;
  ocorrencia?: string;
  idEquipe: string;
  idEstoqueDestino: string;
  itens: ItemDevolucaoTecnicaRequest[];
}

export interface MetadadosSolicitacaoMovimentacao {
  contexto?: 'COMERCIAL' | 'TECNICO' | null;
  idAgrupador?: string | null;
  idSolicitacao?: string | null;
  tipoTecnico?: 'ADICAO_COMUM' | 'ADICAO_CONCRETO' | 'DEVOLUCAO_COMUM' | 'DEVOLUCAO_CONCRETO' | null;
  tipoObra?: 'OBRA' | 'CORRETIVA' | 'PREVENTIVA' | 'LPT' | null;
  ocorrencia?: string | null;
  idReserva?: string | null;
  idOperacao?: string | null;
  idObra?: string | null;
  idEquipe?: string | null;
}

export interface MovimentacaoOperacaoSummaryResponse extends MetadadosSolicitacaoMovimentacao {
  idReferenciaOperacao: string;
  tipo: TipoMovimentacao;
  dataHora: string;
  operadorNome: string;
  estoqueOrigem?: EstoqueResponse;
  estoqueDestino?: EstoqueResponse;
}

export interface MovimentacaoItemResponse {
  id: string;
  material: MaterialResponse;
  quantidade: number;
  numSerie?: string;
  condicao?: string;
  idItemOperacao?: string | null;
  idItemSolicitacao?: string | null;
}

export interface MovimentacaoOperacaoResponse extends MetadadosSolicitacaoMovimentacao {
  idReferenciaOperacao: string;
  tipo: TipoMovimentacao;
  dataHora: string;
  operadorNome: string;
  estoqueOrigem?: EstoqueResponse;
  estoqueDestino?: EstoqueResponse;
  itens: MovimentacaoItemResponse[];
  assinaturaEntregador?: string;
  assinaturaRecebedor?: string;
  _links?: Links;
}

export interface PageResponseMovimentacaoOperacaoSummaryResponse {
  content: MovimentacaoOperacaoSummaryResponse[];
  page: PageMetadata;
}

export interface MovimentacaoEstoqueResponse extends MetadadosSolicitacaoMovimentacao {
  id: string;
  estoqueOrigem?: EstoqueResponse;
  estoqueDestino?: EstoqueResponse;
  material: MaterialResponse;
  quantidade: number;
  tipo: TipoMovimentacao;
  idUsuarioOperador: string;
  idReferenciaOperacao?: string;
  dataHora: string;
  condicao?: string | null;
  idItemOperacao?: string | null;
}

export interface MovimentacaoEstoqueFilter {
  idEstoque?: string | null;
  idBaseOrigem?: string | null;
  idBaseDestino?: string | null;
  idMaterial?: string | null;
  condicao?: string | null;
  tipo?: TipoMovimentacao | null;
  dataInicio?: string | null;
  dataFim?: string | null;
  referencia?: string | null;
  idUsuarioOperador?: string | null;
  numSerie?: string | null;
  contexto?: 'COMERCIAL' | 'TECNICO' | null;
  idAgrupador?: string | null;
  idSolicitacao?: string | null;
  tipoTecnico?: 'ADICAO_COMUM' | 'ADICAO_CONCRETO' | 'DEVOLUCAO_COMUM' | 'DEVOLUCAO_CONCRETO' | null;
  tipoObra?: 'OBRA' | 'CORRETIVA' | 'PREVENTIVA' | 'LPT' | null;
  idObra?: string | null;
  ocorrencia?: string | null;
  idEquipe?: string | null;
  idReserva?: string | null;
  numeroReserva?: string | null;
  idAgenteReserva?: string | null;
  statusSolicitacao?: string | null;
  statusSeparacao?: string | null;
  buscaGlobal?: string | null;
  page?: number;
  size?: number;
  sort?: string;
}

export interface PageResponseMovimentacaoSummaryResponse {
  content: MovimentacaoOperacaoSummaryResponse[];
  page: PageMetadata;
}

export interface PageResponseMovimentacaoOperacaoSummaryResponse {
  content: MovimentacaoOperacaoSummaryResponse[];
  page: PageMetadata;
}

// --- Saldos ---
export interface SaldoResponse {
  id: string;
  estoque: EstoqueResponse;
  material: MaterialResponse;
  quantidade: number;
  condicao?: string;
  negativo: boolean;
  ativo: boolean;
}

export interface PageResponseSaldoResponse {
  content: SaldoResponse[];
  page: PageMetadata;
}

// --- Dashboard de Solicitações ---
export interface TopMaterialItem {
  codigo: string;
  nome: string;
  totalSolicitado: number;
}

export interface TopEstoqueItem {
  nome: string;
  totalSolicitacoes: number;
}

export interface TopMotivoItem {
  nome: string;
  totalOcorrencias: number;
}

export interface DashboardSolicitacaoResponse {
  totalSolicitacoes: number;
  totalPendentes: number;
  totalAprovadas: number;
  totalRejeitadas: number;
  totalEntregues: number;
  totalEntreguesParciais: number;
  totalEstornadas: number;
  tempoMedioAtendimentoHoras: number;
  tempoMedioAprovacaoHoras: number;
  mediaSolicitacoesPorDia: number;
  percentualPendentes: number;
  percentualAprovadas: number;
  percentualRejeitadas: number;
  percentualEntregues: number;
  percentualEntreguesParciais: number;
  percentualEstornadas: number;
  indiceRejeicao: number;
  indiceSucessoEntrega: number;
  totalItensSolicitados: number;
  mediaItensPorSolicitacao: number;
  topMateriais: TopMaterialItem[];
  topEstoquesDestino: TopEstoqueItem[];
  topMotivosRejeicao: TopMotivoItem[];
}

export interface SaldoSerieResponse {
  id: string;
  estoque: EstoqueResponse;
  material: MaterialResponse;
  numSerie: string;
  condicao?: 'NOVO' | 'SUCATA';
  ativo: boolean;
  /** Solicitação para a qual a série está reservada; ausente quando está livre. */
  reservadaParaSolicitacao?: string | null;
}

export interface PageResponseSaldoSerieResponse {
  content: SaldoSerieResponse[];
  page: PageMetadata;
}

// --- Importação de Solicitações Comerciais de Material ---
export interface SolicitacaoComercialImportacaoDTO {
  linha: number;
  nomeEstoqueOrigem?: string;
  idEstoqueOrigem?: string;
  nomeEstoqueDestino?: string;
  idEstoqueDestino?: string;
  nomeRequisitante?: string;
  idRequisitante?: string;
  codigoMaterial?: string;
  idMaterial?: string;
  nomeMaterial?: string;
  quantidade?: number;
  dataPrevista?: string;
  observacao?: string;
  status: 'AGUARDANDO_PROCESSAMENTO' | 'COM_ERRO' | 'EM_PROCESSAMENTO' | 'CONCLUIDO';
  campoErro?: string;
  mensagemErro?: string;
}

export interface SolicitacaoComercialImportRow extends SolicitacaoComercialImportacaoDTO {
  editing?: boolean;
}

export interface ImportacaoSolicitacaoComercialResponse {
  registros: SolicitacaoComercialImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
  processamento?: {
    etapa: 'VALIDACAO' | 'CONFIRMACAO';
    estado: 'PRONTO_PARA_CONFIRMACAO' | 'COM_INCONSISTENCIAS' | 'CONCLUIDO' | 'PARCIAL' | 'FALHOU';
    mensagem: string;
  };
}

export interface ConfirmarImportacaoSolicitacaoComercialRequest {
  registros: SolicitacaoComercialImportacaoDTO[];
}

export interface MovimentacaoImportacaoDTO {
  linha?: number;
  tipo?: string;
  nomeEstoqueOrigem?: string;
  idEstoqueOrigem?: string;
  nomeEstoqueDestino?: string;
  idEstoqueDestino?: string;
  codigoMaterial?: string;
  idMaterial?: string;
  nomeMaterial?: string;
  detalhaSerie?: boolean;
  quantidade?: number;
  numSerie?: string;
  condicao?: CondicaoMaterial;
  observacao?: string;
  status?: 'AGUARDANDO_PROCESSAMENTO' | 'COM_ERRO' | 'EM_PROCESSAMENTO' | 'CONCLUIDO';
  campoErro?: string;
  mensagemErro?: string;
}

export interface MovimentacaoImportRow extends MovimentacaoImportacaoDTO {
  editing?: boolean;
}

export interface ImportacaoMovimentacaoResponse {
  registros: MovimentacaoImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
  processamento?: {
    etapa: 'VALIDACAO' | 'CONFIRMACAO';
    estado: 'PRONTO_PARA_CONFIRMACAO' | 'COM_INCONSISTENCIAS' | 'CONCLUIDO' | 'PARCIAL' | 'FALHOU';
    mensagem: string;
  };
}

export interface ConfirmarImportacaoMovimentacaoRequest {
  registros: MovimentacaoImportacaoDTO[];
}
