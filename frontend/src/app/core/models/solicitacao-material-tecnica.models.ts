import { EstoqueResponse, MaterialResponse, MotivoRejeicaoResponse, MotivoPendenciaResponse } from './almoxarifado.models';
import { PageMetadata, PagedResponse } from './shared.models';

export type ContextoSolicitacaoMaterial = 'COMERCIAL' | 'TECNICO';
export type CodigoTipoTecnico = '221' | '921' | '222' | '922';
export type TipoSolicitacaoTecnica =
  | 'ADICAO_COMUM'
  | 'ADICAO_CONCRETO'
  | 'DEVOLUCAO_COMUM'
  | 'DEVOLUCAO_CONCRETO';
export type CodigoOperacaoTecnica = CodigoTipoTecnico;
export type TipoObraSolicitacao = 'OBRA' | 'CORRETIVA' | 'PREVENTIVA' | 'LPT';
export type StatusSeparacao =
  | 'AGUARDANDO_AGENDAMENTO'
  | 'PRONTO_PARA_SEPARACAO'
  | 'EM_SEPARACAO'
  | 'SEPARADO';
export type StatusSolicitacaoMaterial =
  | 'PENDENTE'
  | 'APROVADO'
  | 'REJEITADO'
  | 'ENTREGUE_PARCIAL'
  | 'ENTREGUE'
  | 'DEVOLVIDO_PARCIAL'
  | 'DEVOLVIDO'
  | 'ESTORNADO'
  | 'CANCELADO';
export type StatusSolicitacao = StatusSolicitacaoMaterial;
export type EtapaSolicitacaoTecnica =
  | 'AGUARDANDO_PLANEJAMENTO'
  | 'EM_SEPARACAO'
  | 'PENDENTE_ENTREGA';
export type EtapaOperacional =
  | 'AGUARDANDO_PLANEJAMENTO'
  | 'PRONTO_PARA_SEPARACAO'
  | 'EM_SEPARACAO'
  | 'PENDENTE_ENTREGA'
  | 'PENDENTE_DEVOLUCAO'
  | 'CONCLUIDA';
export type EtapaOperacionalTecnica = EtapaOperacional;
export type AcaoPermitidaSolicitacao =
  | 'EDITAR_PLANEJAMENTO'
  | 'REGISTRAR_RESERVA'
  | 'ALTERAR_RESERVA'
  | 'APROVAR'
  | 'REJEITAR'
  | 'INICIAR_SEPARACAO'
  | 'CONCLUIR_SEPARACAO'
  | 'PULAR_SEPARACAO'
  | 'ENTREGAR'
  | 'CONFIRMAR_DEVOLUCAO'
  | 'ENCERRAR_SALDO'
  | 'ESTORNAR'
  | 'VER_HISTORICO';

export const MAPA_TIPO_CODIGO_TECNICO: Record<TipoSolicitacaoTecnica, CodigoTipoTecnico> = {
  ADICAO_COMUM: '221',
  ADICAO_CONCRETO: '921',
  DEVOLUCAO_COMUM: '222',
  DEVOLUCAO_CONCRETO: '922',
};

export const MAPA_CODIGO_TIPO_TECNICO: Record<CodigoTipoTecnico, TipoSolicitacaoTecnica> = {
  '221': 'ADICAO_COMUM',
  '921': 'ADICAO_CONCRETO',
  '222': 'DEVOLUCAO_COMUM',
  '922': 'DEVOLUCAO_CONCRETO',
};

export function obterCodigoTecnico(
  tipo: TipoSolicitacaoTecnica | CodigoTipoTecnico,
): CodigoTipoTecnico {
  return tipo in MAPA_CODIGO_TIPO_TECNICO
    ? (tipo as CodigoTipoTecnico)
    : MAPA_TIPO_CODIGO_TECNICO[tipo as TipoSolicitacaoTecnica];
}

export interface OcorrenciaTecnica {
  id: string;
  codigo: string;
}

export interface ReservaTecnicaResponse {
  id: string;
  idSolicitacao: string;
  numero: string;
  dataCriacao: string;
  idAgenteResponsavel: string | null;
  nomeAgente: string | null;
  empresaAgente: string | null;
  registradaEm: string;
  idUsuarioRegistro: string;
  atualizadaEm: string | null;
  idUsuarioAtualizacao: string | null;
  versao: number;
}

export interface TotaisSolicitacaoTecnica {
  solicitada: number;
  entregaBruta: number;
  entregaEstornada: number;
  entregaLiquida: number;
  devolucaoBruta: number;
  devolucaoEstornada: number;
  devolucaoLiquida: number;
  pendente: number;
}

export interface ItemSolicitacaoTecnicaResponse {
  id: string;
  material: MaterialResponse;
  quantidade: number;
  quantidadeEntregue: number;
  numSerie?: string | null;
  tipoMovimentacao: TipoSolicitacaoTecnica;
  quantidadeEntregueBruta?: number;
  quantidadeEntregaEstornada?: number;
  quantidadeEntregaLiquida?: number;
  quantidadeDevolvidaBruta?: number;
  quantidadeDevolucaoEstornada?: number;
  quantidadeDevolucaoLiquida?: number;
  quantidadePendente?: number;
  motivoPendencia?: MotivoPendenciaResponse | null;
  justificativaPendencia?: string | null;
}

/**
 * Shape accepted at the HTTP boundary. In the atomic v2 detail the backend
 * may omit the item type because it is already defined by `tecnico.tipoTecnico`.
 */
export type ItemSolicitacaoTecnicaApiResponse = Omit<
  ItemSolicitacaoTecnicaResponse,
  'tipoMovimentacao'
> & {
  tipoMovimentacao?: TipoSolicitacaoTecnica;
};

export interface ItemOperacaoTecnicaResponse {
  id: string;
  idItemSolicitacao: string;
  idMaterial: string;
  codigoMaterial: string;
  descricaoMaterial: string;
  quantidade: number;
  numeroSerie?: string | null;
  condicao: 'NOVO' | 'SUCATA';
  idItemOperacaoOriginal?: string | null;
}

export type TipoOperacaoTecnica =
  | 'ENTREGA'
  | 'DEVOLUCAO'
  | 'ESTORNO'
  | 'ENCERRAMENTO'
  | 'ENCERRAMENTO_SALDO'
  | string;

export interface OperacaoSolicitacaoTecnicaResponse {
  id: string;
  tipo: TipoOperacaoTecnica;
  idEquipe: string;
  idColaboradorSaque?: string | null;
  saqueForaEquipe?: boolean;
  idUsuarioOperador: string;
  realizadaEm: string;
  motivo?: string | null;
  idOperacaoOriginal?: string | null;
  itens: ItemOperacaoTecnicaResponse[];
}

export interface DetalhesTecnicosSolicitacaoResponse {
  tipoTecnico: TipoSolicitacaoTecnica;
  tipoObra: TipoObraSolicitacao;
  idObra: string;
  ocorrencia: OcorrenciaTecnica | null;
  idAlmoxarifadoAtendimento: string;
  idEquipe: string | null;
  idColaboradorSaque?: string | null;
  saqueForaEquipe?: boolean;
  dataPrevista: string | null;
  observacao: string | null;
  statusSeparacao: StatusSeparacao | null;
  reserva: ReservaTecnicaResponse | null;
  totais: TotaisSolicitacaoTecnica;
  operacoes: OperacaoSolicitacaoTecnicaResponse[];
  versao: number;
  acoesPermitidas: AcaoPermitidaSolicitacao[];
  atrasada: boolean;
  foraPrazoSaque?: boolean;
  encerrada?: boolean;
  encerradaEm: string | null;
  idUsuarioEncerramento: string | null;
  motivoEncerramento: string | null;
  etapaOperacional?: EtapaOperacionalTecnica | null;
}

export interface SolicitacaoMaterialDetalheResponse {
  id: string;
  estoqueOrigem?: EstoqueResponse | null;
  estoqueDestino?: EstoqueResponse | null;
  idRequisitante?: string;
  idAprovador?: string | null;
  idEntregadorAlmox?: string | null;
  status: StatusSolicitacaoMaterial;
  dataSolicitacao?: string;
  dataVerificacao?: string | null;
  dataEntrega?: string | null;
  motivoRejeicao?: MotivoRejeicaoResponse | null;
  justificativaRejeicao?: string | null;
  motivoPendencia?: MotivoPendenciaResponse | null;
  justificativaPendencia?: string | null;
  possuiPendencia?: boolean;
  itens: ItemSolicitacaoTecnicaResponse[];
  tecnico: DetalhesTecnicosSolicitacaoResponse | null;
  etapaOperacional?: EtapaOperacional | null;
  contexto: ContextoSolicitacaoMaterial;
  versao: number;
  statusSeparacao: StatusSeparacao | null;
  acoesPermitidas: AcaoPermitidaSolicitacao[];
  encerrada?: boolean;
  encerradaEm?: string | null;
  motivoEncerramento?: string | null;
}

export type SolicitacaoMaterialDetalheApiResponse = Omit<
  SolicitacaoMaterialDetalheResponse,
  'itens'
> & {
  itens: ItemSolicitacaoTecnicaApiResponse[];
};

export type SolicitacaoTecnicaDetalhe = DetalhesTecnicosSolicitacaoResponse;

export interface SolicitacaoMaterialSummaryResponse {
  id: string;
  possuiPendencia?: boolean;
  tipo?: TipoSolicitacaoTecnica | null;
  tipoObra?: TipoObraSolicitacao | null;
  idObra?: string | null;
  ocorrencia?: OcorrenciaTecnica | null;
  contexto: ContextoSolicitacaoMaterial;
  dataSolicitacao: string;
  dataEntrega?: string | null;
  estoqueOrigemNome?: string | null;
  estoqueDestinoNome?: string | null;
  idRequisitante: string;
  idEquipe?: string | null;
  idColaboradorSaque?: string | null;
  saqueForaEquipe?: boolean;
  idAlmoxarifadoAtendimento?: string | null;
  almoxarifadoNome?: string | null;
  status: StatusSolicitacaoMaterial;
  statusSeparacao: StatusSeparacao | null;
  dataPrevista?: string | null;
  numeroReserva?: string | null;
  idAgenteReserva?: string | null;
  nomeAgenteReserva?: string | null;
  dataCriacaoReserva?: string | null;
  registradaEmReserva?: string | null;
  referencia?: string | null;
  numSerie?: string | null;
  quantidadeItens: number;
  quantidadeSolicitada?: number;
  quantidadeEntregaLiquida?: number;
  quantidadeDevolucaoLiquida?: number;
  quantidadePendente?: number;
  totalOperacoes?: number;
  atrasada?: boolean;
  encerrada?: boolean;
  encerradaEm?: string | null;
  motivoEncerramento?: string | null;
  etapaOperacional?: EtapaOperacionalTecnica | null;
  versao: number;
  acoesPermitidas: AcaoPermitidaSolicitacao[];
}

/**
 * Shape returned by the v2 list endpoint. The API keeps the technical
 * projection under `tecnico`; the service normalizes it to the row model
 * consumed by the table.
 */
export interface SolicitacaoMaterialListaTecnicaResponse {
  tipoTecnico: TipoSolicitacaoTecnica;
  tipoObra: TipoObraSolicitacao;
  idObra: string;
  ocorrencia?: OcorrenciaTecnica | null;
  idAlmoxarifadoAtendimento: string;
  idEquipe?: string | null;
  idColaboradorSaque?: string | null;
  saqueForaEquipe?: boolean;
  dataPrevista?: string | null;
  observacao?: string | null;
  statusSeparacao?: StatusSeparacao | null;
  reserva?: Partial<ReservaTecnicaResponse> | null;
  totais: TotaisSolicitacaoTecnica;
  operacoes: OperacaoSolicitacaoTecnicaResponse[];
  versao: number;
  acoesPermitidas: AcaoPermitidaSolicitacao[];
  atrasada: boolean;
  encerradaEm?: string | null;
  motivoEncerramento?: string | null;
  etapaOperacional?: EtapaOperacionalTecnica | null;
}

export interface SolicitacaoMaterialListaItemResponse {
  id: string;
  possuiPendencia?: boolean;
  contexto: ContextoSolicitacaoMaterial;
  dataSolicitacao: string;
  dataEntrega?: string | null;
  estoqueOrigemNome?: string | null;
  estoqueDestinoNome?: string | null;
  idRequisitante: string;
  status: StatusSolicitacaoMaterial;
  statusSeparacao?: StatusSeparacao | null;
  tecnico?: SolicitacaoMaterialListaTecnicaResponse | null;
  totalItens: number;
  versao: number;
  acoesPermitidas: AcaoPermitidaSolicitacao[];
  etapaOperacional?: EtapaOperacionalTecnica | null;
  referencia?: string | null;
  numSerie?: string | null;
}

export interface SolicitacaoResumoObra {
  id: string;
  tipo: TipoSolicitacaoTecnica;
  status: StatusSolicitacaoMaterial;
  statusSeparacao: StatusSeparacao | null;
  idEquipe: string | null;
  dataPrevista: string | null;
  quantidadeSolicitada: number;
  quantidadePendente: number;
  dataSolicitacao?: string | null;
  almoxarifadoNome?: string | null;
  itens?: ItemSolicitacaoResumoObra[];
}

export interface ItemSolicitacaoResumoObra {
  idMaterial: string;
  codigo: string;
  descricao: string;
  unidade?: string | null;
  quantidade: number;
  /** Entregue nas adições, devolvido nas devoluções. */
  quantidadeAtendida: number;
  quantidadePendente: number;
}

export interface MovimentacaoResumoObra {
  idReferencia: string;
  tipo: string;
  tipoTecnico?: TipoSolicitacaoTecnica | null;
  dataHora: string;
  origemNome?: string | null;
  destinoNome?: string | null;
  idSolicitacao?: string | null;
  itens: ItemMovimentacaoResumoObra[];
}

export interface ItemMovimentacaoResumoObra {
  codigo: string;
  descricao: string;
  unidade?: string | null;
  quantidade: number;
  condicao?: string | null;
  numSerie?: string | null;
}

export interface ItemSaldoObra {
  codigo: string;
  descricao: string;
  unidade?: string | null;
  condicao: string;
  quantidade: number;
  series: string[];
}

export interface SaldoObra {
  idEstoque?: string | null;
  itens: ItemSaldoObra[];
}

export interface ItemIntencaoSolicitacaoTecnica {
  idMaterial: string;
  tipoMovimentacao: TipoSolicitacaoTecnica;
  quantidade: number;
}

export interface CriarSolicitacaoTecnicaRequest {
  idObra: string;
  ocorrencia?: string | null;
  idAlmoxarifadoAtendimento: string;
  idEquipe?: string | null;
  idColaboradorSaque?: string | null;
  saqueForaEquipe?: boolean;
  dataPrevista?: string | null;
  observacao?: string | null;
  itens: ItemIntencaoSolicitacaoTecnica[];
}

export interface SolicitacaoTecnicaCriada {
  id: string;
  tipo: TipoSolicitacaoTecnica;
  codigo: CodigoTipoTecnico;
}

export interface CriacaoSolicitacaoTecnicaResponse {
  solicitacoesCriadas: SolicitacaoTecnicaCriada[];
}

export interface ItemPlanejamentoTecnico {
  idItem: string | null;
  idMaterial: string;
  tipoMovimentacao: TipoSolicitacaoTecnica;
  quantidade: number;
}

export interface PlanejarSolicitacaoTecnicaRequest {
  versaoEsperada: number;
  idObra: string;
  ocorrencia: string | null;
  idAlmoxarifadoAtendimento: string;
  idEquipe: string | null;
  idColaboradorSaque?: string | null;
  saqueForaEquipe?: boolean;
  dataPrevista: string | null;
  observacao: string | null;
  justificativa?: string | null;
  itens: ItemPlanejamentoTecnico[];
}

export interface PlanejamentoSolicitacaoTecnicaResponse {
  solicitacaoAtualizada: SolicitacaoMaterialDetalheResponse;
}

export interface PlanejamentoSolicitacaoTecnicaApiResponse {
  solicitacaoAtualizada: SolicitacaoMaterialDetalheApiResponse;
}

export interface RegistrarReservaTecnicaRequest {
  versaoEsperada: number;
  numero: string;
  dataCriacao: string;
  idAgenteResponsavel?: string | null;
  justificativa?: string | null;
}

export interface AlterarSeparacaoTecnicaRequest {
  versaoEsperada: number;
}

export interface ItemComandoTecnicoRequest {
  idItemSolicitacao: string;
  quantidade: number;
  numeroSerie?: string | null;
}

export interface ConfirmarEntregaTecnicaRequest {
  versaoEsperada: number;
  idEquipe: string;
  idColaboradorSaque?: string | null;
  saqueForaEquipe?: boolean;
  assinaturaAlmox: string;
  assinaturaEquipe: string;
  itens: ItemComandoTecnicoRequest[];
  numeroReserva?: string | null;
  dataReserva?: string | null;
  idAgenteReserva?: string | null;
}

export interface ConfirmarDevolucaoTecnicaRequest {
  versaoEsperada: number;
  idEquipe: string;
  numeroReserva?: string | null;
  dataReserva?: string | null;
  idAgenteReserva?: string | null;
  itens: Array<ItemComandoTecnicoRequest & { condicao: 'NOVO' | 'SUCATA' }>;
}

export interface EstornarOperacaoTecnicaRequest {
  versaoEsperada: number;
  idOperacaoOriginal: string;
  motivo: string;
  itens: Array<{
    idItemOperacaoOriginal: string;
    quantidade: number;
    numeroSerie?: string | null;
  }>;
}

export interface EncerrarSaldoTecnicoRequest {
  versaoEsperada: number;
  motivo: string;
}

export interface OperacaoTecnicaResponse {
  idOperacao: string;
}

export interface SolicitacaoMaterialFilter {
  contexto?: ContextoSolicitacaoMaterial | null;
  etapa?: EtapaSolicitacaoTecnica | null;
  tipoTecnico?: TipoSolicitacaoTecnica | null;
  tipoObra?: TipoObraSolicitacao | null;
  idObra?: string | null;
  ocorrencia?: string | null;
  idEquipe?: string | null;
  idAlmoxarifado?: string | null;
  numeroReserva?: string | null;
  idAgenteReserva?: string | null;
  statusSeparacao?: StatusSeparacao | null;
  status?: StatusSolicitacaoMaterial | null;
  statuses?: StatusSolicitacaoMaterial[];
  dataInicio?: string | null;
  dataFim?: string | null;
  previstaDe?: string | null;
  previstaAte?: string | null;
  referencia?: string | null;
  numSerie?: string | null;
  buscaGlobal?: string | null;
  idEstoqueOrigem?: string | null;
  idEstoqueDestino?: string | null;
  idRequisitante?: string | null;
  idBaseOrigem?: string | null;
  idBaseDestino?: string | null;
  page?: number;
  size?: number;
  sort?: string;
}

export interface MaterialHistorico {
  id: string;
  snapshot: string;
}

export type ValorMaterialHistorico = string | MaterialHistorico;

export interface SnapshotHistoricoItem {
  id: string;
  material?: ValorMaterialHistorico | null;
  quantidade?: string | null;
  tipoTecnico?: string | null;
  /** Compatibilidade com snapshots antigos que achatavam o nome do material. */
  snapshot?: string | null;
  [key: string]: unknown;
}

export interface SnapshotHistorico {
  dataPrevista?: string | null;
  observacao?: string | null;
  itens?: SnapshotHistoricoItem[];
  [key: string]: unknown;
}

/**
 * Os nomes valorAnterior/valorNovo são o contrato atual da API.
 * anterior/novo permanecem somente para leitura de eventos legados.
 */
export interface AlteracaoHistorico {
  caminho: string;
  valorAnterior?: unknown | null;
  valorNovo?: unknown | null;
  anterior?: unknown | null;
  novo?: unknown | null;
}

/** Alias mantido para os consumidores existentes do domínio. */
export type HistoricoAlteracaoItem = AlteracaoHistorico;

export interface PayloadHistorico {
  comando?: string;
  alteracoes?: AlteracaoHistorico[];
  snapshots?: SnapshotHistorico | Array<unknown> | null;
  correlacoes?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface EventoHistorico {
  id: string;
  escopo: 'SOLICITACAO';
  idSolicitacao: string;
  sequencia: number;
  tipo: string;
  tipoTecnico?: TipoSolicitacaoTecnica | null;
  tipoObra?: TipoObraSolicitacao | null;
  ocorrencia?: string | null;
  autor?: { id: string; nome: string } | null;
  origem: string;
  justificativa?: string | null;
  versao: number;
  ocorridoEm: string;
  hash?: string;
  hashAnterior?: string | null;
  payload?: PayloadHistorico | null;
}

/** Alias mantido para os consumidores existentes do domínio. */
export type HistoricoSolicitacaoEvento = EventoHistorico;

export interface IntegridadeHistorico {
  escopo: 'SOLICITACAO';
  idCadeia: string;
  status: 'INTEGRO' | 'VIOLADO' | 'NAO_VERIFICADO';
  sequenciaInicial?: number | null;
  sequenciaFinal?: number | null;
}

/** Alias mantido para os consumidores existentes do domínio. */
export type IntegridadeHistoricoSolicitacao = IntegridadeHistorico;

export interface HistoricoResponse {
  eventos: EventoHistorico[];
  page: PageMetadata;
  integridade?: IntegridadeHistorico[];
}

/** Alias mantido para os consumidores existentes do domínio. */
export type HistoricoSolicitacaoResponse = HistoricoResponse;

export interface MensagemImportacaoTecnica {
  linha?: number | null;
  campo?: string | null;
  codigo: string;
  severidade: 'ERRO' | 'AVISO';
  valorOriginal?: string | null;
  mensagem: string;
}

export interface ParChaveTipoImportacao {
  chave: string;
  tipo: CodigoTipoTecnico;
}

export interface SolicitacaoTecnicaImportRow {
  linha: number;
  chave?: string;
  numeroObra: string;
  idObra?: string;
  ocorrencia: string;
  almoxarifado: string;
  idAlmoxarifado?: string;
  codigoEquipe: string;
  idEquipe?: string;
  dataPrevista: string;
  observacao: string;
  tipoMovimentacao: CodigoTipoTecnico;
  codigoMaterial: string;
  idMaterial?: string;
  nomeMaterial?: string;
  quantidade: number;
  numeroReserva: string;
  dataCriacaoReserva: string;
  agenteResponsavel: string;
  idAgenteResponsavel?: string;
  status: 'AGUARDANDO_PROCESSAMENTO' | 'COM_ERRO' | 'EM_PROCESSAMENTO' | 'CONCLUIDO';
  campoErro?: string;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoSolicitacaoTecnicaResponse {
  token: string;
  hashArquivo: string;
  expiraEm: string;
  totalLinhas: number;
  totalConfirmaveis: number;
  totalErros: number;
  totalAvisos: number;
  errosPorLinha: MensagemImportacaoTecnica[];
  paresDisponiveis: ParChaveTipoImportacao[];
  processamento?: ProcessamentoImportacao;
}

export interface ProcessamentoImportacao {
  etapa: 'VALIDACAO' | 'CONFIRMACAO';
  estado: 'PRONTO_PARA_CONFIRMACAO' | 'COM_INCONSISTENCIAS' | 'CONCLUIDO' | 'PARCIAL' | 'FALHOU';
  mensagem: string;
}

export type ImportacaoValidacaoResponse = ImportacaoSolicitacaoTecnicaResponse;

export interface ConfirmarImportacaoSolicitacaoTecnicaRequest {
  token: string;
  combinacoes: ParChaveTipoImportacao[];
  reconhecerAvisos: boolean;
}

export type ConfirmarImportacaoTecnicaRequest = ConfirmarImportacaoSolicitacaoTecnicaRequest;

export interface ResultadoImportacaoTecnica {
  chave: string;
  tipo: CodigoTipoTecnico;
  solicitacoes?: SolicitacaoTecnicaCriada[];
  erro: string | null;
}

export interface ConfirmacaoImportacaoSolicitacaoTecnicaResponse {
  resultados: ResultadoImportacaoTecnica[];
  processamento?: ProcessamentoImportacao;
}

export type ConfirmacaoImportacaoTecnicaResponse = ConfirmacaoImportacaoSolicitacaoTecnicaResponse;

export interface OcorrenciaProjecaoObra {
  id: string;
  codigo: string;
}

export interface GrupoOcorrenciaSolicitacoes {
  id: string;
  codigo: string;
  solicitacoes: SolicitacaoResumoObra[];
}

export interface ProjecaoObraSolicitacoesResponse {
  idObra: string;
  tipoObra: TipoObraSolicitacao;
  ocorrencias: GrupoOcorrenciaSolicitacoes[];
  solicitacoes: SolicitacaoResumoObra[];
  movimentacoes?: MovimentacaoResumoObra[];
  movimentacoesTruncadas?: boolean;
}

export interface ProjetoRelacionadoSolicitacao {
  idRelacionamento: string;
  idProjeto: string;
  numero: string;
  titulo: string;
  tipoVinculo: 'KIT' | 'MEDIDOR';
  tipoObra: TipoObraSolicitacao;
  ativo: boolean;
}

export type PaginaSolicitacoesMaterial = PagedResponse<SolicitacaoMaterialSummaryResponse>;
export type PaginaSolicitacoesMaterialApi = PagedResponse<SolicitacaoMaterialListaItemResponse>;

export interface KanbanMetricasColuna {
  total: number;
  atrasadas: number;
  foraPrazo: number;
  diasMaisAntiga: number | null;
}

export type MacroEtapaKanban = 'PLANEJAMENTO' | 'SEPARACAO' | 'ENTREGA' | 'RECUSADO';

export type SubEtapaKanban =
  | 'PENDENTE_PLANEJAMENTO'
  | 'PLANEJADO'
  | 'PENDENTE_APROVACAO'
  | 'PRONTO_PARA_SEPARACAO'
  | 'EM_SEPARACAO'
  | 'SEPARADO'
  | 'PENDENTE_RETIRADA'
  | 'PENDENTE_DEVOLUCAO'
  | 'CONCLUIDO'
  | 'REJEITADO'
  | 'CANCELADO';

export interface KanbanSubColunaApiResponse {
  subEtapa: SubEtapaKanban;
  label: string;
  corPadrao: string;
  itens: SolicitacaoMaterialListaItemResponse[];
  total: number;
}

export interface KanbanColunaApiResponse {
  etapa: MacroEtapaKanban;
  label: string;
  icone: string;
  subColunas: KanbanSubColunaApiResponse[];
  metricas: KanbanMetricasColuna;
}

export interface KanbanSolicitacaoTecnicaApiResponse {
  colunas: KanbanColunaApiResponse[];
}

export interface KanbanSubColunaResponse {
  subEtapa: SubEtapaKanban;
  label: string;
  corPadrao: string;
  itens: SolicitacaoMaterialSummaryResponse[];
  total: number;
}

export interface KanbanColunaResponse {
  etapa: MacroEtapaKanban;
  label: string;
  icone: string;
  subColunas: KanbanSubColunaResponse[];
  metricas: KanbanMetricasColuna;
}

export interface KanbanSolicitacaoTecnicaResponse {
  colunas: KanbanColunaResponse[];
}

