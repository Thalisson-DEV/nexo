import { PageMetadata } from './shared.models';

export type TipoSolicitacao = 'COMPRA' | 'PAGAMENTO';
export type StatusSolicitacao =
  | 'RASCUNHO'
  | 'SOLICITADO'
  | 'APROVADO'
  | 'REJEITADO'
  | 'CANCELADO';
export type PrioridadeSolicitacao = 'BAIXA' | 'NORMAL' | 'ALTA' | 'URGENTE';
export type LocalAquisicao = 'LOCAL' | 'CENTRAL';
export type FormaPagamento =
  | 'A_VISTA_ESPECIE'
  | 'PIX_TRANSFERENCIA'
  | 'BOLETO'
  | 'CARTAO_CREDITO'
  | 'CARTAO_DEBITO'
  | 'OUTRA';
export type RegimeVeiculo = 'PROPRIO' | 'LOCADO';
export type NaturezaServico =
  | 'COMPRA_E_REFORMA_DE_PNEUS'
  | 'SUSPENSAO_FREIOS'
  | 'ARREFECIMENTO'
  | 'TRANSMISSAO'
  | 'ELETRICA'
  | 'CHAPARIA_E_PINTURA'
  | 'HIDRAULICA'
  | 'TAPECARIA'
  | 'LAVAGEM_E_LUBRIFICACAO'
  | 'ACESSORIOS'
  | 'BATERIA'
  | 'BORRACHARIA'
  | 'MECANICA'
  | 'PNEUMATICA'
  | 'POSTO_DE_MOLAS'
  | 'REFRIGERACAO_E_CLIMATIZACAO'
  | 'TACOGRAFO'
  | 'TORNEADORA';
export type MotivoServico =
  | 'DESGASTE_NATURAL'
  | 'ADEQUACAO_DE_MELHORIA'
  | 'PROBLEMA_MECANICO'
  | 'MAU_USO_DESCONTO'
  | 'OUTRA';
export type TipoDespesaSolicitacao =
  | 'MATERIAL'
  | 'SERVICO'
  | 'COMBUSTIVEL'
  | 'LOCACAO'
  | 'UTILIDADES'
  | 'TRIBUTOS'
  | 'MANUTENCAO'
  | 'OUTRA';
export type MotivoSemCompra =
  | 'COMBUSTIVEL_FORNECEDOR_CADASTRADO'
  | 'DESPESA_MENSAL_RECORRENTE'
  | 'OUTRA';

/**
 * Query aceita pelas listagens de compra e pagamento. Datas de vencimento usam
 * `YYYY-MM-DD`; as demais usam ISO 8601 com offset. Todos os campos são escalares:
 * o backend combina filtros diferentes com AND e não aceita arrays de enum.
 */
export interface SolicitacaoFinanceiraFilter {
  buscaGlobal?: string | null;
  numero?: number | null;
  status?: StatusSolicitacao | null;
  formularioId?: string | null;
  baseId?: string | null;
  processoId?: string | null;
  obraId?: string | null;
  fornecedorId?: string | null;
  veiculoId?: string | null;
  solicitanteColaboradorId?: string | null;
  prioridade?: PrioridadeSolicitacao | null;
  localAquisicao?: LocalAquisicao | null;
  formaPagamento?: FormaPagamento | null;
  regimeVeiculo?: RegimeVeiculo | null;
  naturezaServico?: NaturezaServico | null;
  motivoServico?: MotivoServico | null;
  tipoDespesa?: TipoDespesaSolicitacao | null;
  usuarioSolicitanteId?: string | null;
  apenasMinhas?: boolean | null;
  valorMinimo?: number | null;
  valorMaximo?: number | null;
  quantidadeMinima?: number | null;
  quantidadeMaxima?: number | null;
  parcelasMinimas?: number | null;
  parcelasMaximas?: number | null;
  vencimentoInicio?: string | null;
  vencimentoFim?: string | null;
  temNf?: boolean | null;
  numeroNf?: string | null;
  valorAprovadoMinimo?: number | null;
  valorAprovadoMaximo?: number | null;
  usuarioDecisaoId?: string | null;
  usuarioCancelamentoId?: string | null;
  dataSubmissaoInicio?: string | null;
  dataSubmissaoFim?: string | null;
  dataDecisaoInicio?: string | null;
  dataDecisaoFim?: string | null;
  dataCancelamentoInicio?: string | null;
  dataCancelamentoFim?: string | null;
  dataUltimaAlteracaoInicio?: string | null;
  dataUltimaAlteracaoFim?: string | null;
  dataDesativacaoInicio?: string | null;
  dataDesativacaoFim?: string | null;
  possuiDocumentos?: boolean | null;
  dataCriacaoInicio?: string | null;
  dataCriacaoFim?: string | null;
  ativo?: boolean | null;
  solicitacaoCompraId?: string | null;
  possuiCompraPrevia?: boolean | null;
  motivoSemCompra?: MotivoSemCompra | null;
  page?: number;
  size?: number;
  sort?: string;
}
export type TipoCampoFinanceiro =
  | 'TEXTO'
  | 'TEXTO_LONGO'
  | 'INTEIRO'
  | 'DECIMAL'
  | 'MOEDA'
  | 'DATA'
  | 'BOOLEANO'
  | 'SELECAO_UNICA'
  | 'REFERENCIA';
/**
 * Assunto a que um campo pertence. É propriedade do campo no catálogo do sistema,
 * não algo que se cadastre: os blocos do formulário se formam sozinhos.
 */
export type GrupoCampo =
  | 'ORIGEM'
  | 'IDENTIFICACAO'
  | 'FROTA'
  | 'FORNECEDOR'
  | 'VALORES'
  | 'NOTA_FISCAL'
  | 'COMPLEMENTO';

/** Ícone de cada bloco, para o cabeçalho da seção. */
export const ICONE_GRUPO: Record<GrupoCampo, string> = {
  ORIGEM: 'pi pi-link',
  IDENTIFICACAO: 'pi pi-id-card',
  FROTA: 'pi pi-car',
  FORNECEDOR: 'pi pi-building',
  VALORES: 'pi pi-wallet',
  NOTA_FISCAL: 'pi pi-receipt',
  COMPLEMENTO: 'pi pi-align-left'
};

/** Natureza de um documento do repositório financeiro. */
export type TipoDocumentoFinanceiro =
  | 'NOTA_FISCAL'
  | 'BOLETO'
  | 'RECIBO'
  | 'COMPROVANTE_PAGAMENTO'
  | 'ORCAMENTO'
  | 'CONTRATO'
  | 'EXTRATO'
  | 'FOTO'
  | 'OUTRO';

/** Etapa da conferência contábil de um documento. */
export type SituacaoDocumento = 'PENDENTE' | 'CONFERIDO' | 'COM_PENDENCIA' | 'ARQUIVADO';

/**
 * Rótulos em linguagem de quem usa. O servidor manda `tipoRotulo`/`situacaoRotulo`
 * prontos e eles têm precedência; estes aqui atendem os lugares onde só existe a
 * constante — filtros, legendas de gráfico e o seletor do envio.
 */
export const ROTULO_TIPO_DOCUMENTO: Record<TipoDocumentoFinanceiro, string> = {
  NOTA_FISCAL: 'Nota fiscal',
  BOLETO: 'Boleto',
  RECIBO: 'Recibo',
  COMPROVANTE_PAGAMENTO: 'Comprovante de pagamento',
  ORCAMENTO: 'Orçamento',
  CONTRATO: 'Contrato',
  EXTRATO: 'Extrato bancário',
  FOTO: 'Foto',
  OUTRO: 'Outro'
};

export const ROTULO_SITUACAO_DOCUMENTO: Record<SituacaoDocumento, string> = {
  PENDENTE: 'Aguardando conferência',
  CONFERIDO: 'Conferido',
  COM_PENDENCIA: 'Com pendência',
  ARQUIVADO: 'Arquivado'
};

export interface FinanceiroPage<T> {
  content: T[];
  page: PageMetadata;
}

export interface FormularioResumo {
  id: string;
  tipoSolicitacao: TipoSolicitacao;
  codigo: string;
  titulo: string;
  descricao?: string | null;
  padrao: boolean;
  ativo: boolean;
}

export interface OpcaoFormulario {
  valor: string;
  rotulo: string;
}

export interface RestricoesCampo {
  tamanhoMaximo?: number | null;
  valorMinimo?: number | null;
  valorMaximo?: number | null;
}

export interface ReferenciaCampo {
  fonte: string;
  endpointOpcoes: string;
  descricao: string;
}

export interface CondicaoCampo {
  campoChave: string;
  valores: string[];
}

/**
 * Um campo publicado no schema.
 *
 * Tudo aqui — tipo, opções, limites, condição — vem do catálogo do sistema
 * (`campoCanonico`) e não da configuração. A variante decide apenas rótulo,
 * ajuda, placeholder, ordem e obrigatoriedade.
 */
export interface CampoFormulario {
  id: string;
  chave: string;
  rotulo: string;
  tipoCampo: TipoCampoFinanceiro;
  campoCanonico: string;
  obrigatorio: boolean;
  ordem: number;
  /** Bloco sob o qual o campo aparece; vem do catálogo, não da configuração. */
  grupo: GrupoCampo;
  grupoRotulo: string;
  ajuda?: string | null;
  placeholder?: string | null;
  restricoes?: RestricoesCampo | null;
  opcoes: OpcaoFormulario[];
  referencia?: ReferenciaCampo | null;
  exibirQuando?: CondicaoCampo | null;
}

/** O schema é plano: uma lista de campos na ordem de exibição. */
export interface FormularioSchema extends FormularioResumo {
  campos: CampoFormulario[];
}

/**
 * Corpo de criação/atualização de um campo: só apresentação.
 *
 * O placeholder não está aqui de propósito — ele descreve o que o campo espera
 * receber, e isso é propriedade do catálogo, não da variante.
 */
export interface CampoRequest {
  campoCanonico?: string | null;
  rotulo?: string | null;
  ajuda?: string | null;
  obrigatorio: boolean;
}

export interface CampoCanonico {
  campoCanonico: string;
  chave: string;
  rotuloPadrao: string;
  tipoCampo: TipoCampoFinanceiro;
  grupo: GrupoCampo;
  grupoRotulo: string;
  fonteReferencia?: string | null;
  jaUtilizado: boolean;
}

export interface ValorSolicitacao {
  valor: unknown;
  exibicao?: string | null;
}

/**
 * Ações que o **estado** da solicitação permite. Não refletem permissão: para
 * decidir, combine `podeDecidir` com a authority `financeiro:*:aprovar` do token.
 */
export interface AcoesSolicitacao {
  podeEditar: boolean;
  podeSubmeter: boolean;
  podeDecidir: boolean;
  podeCancelar: boolean;
  /** Aprovada ou rejeitada: quem tem alçada pode devolvê-la à fila de aprovação. */
  podeDesfazerDecisao: boolean;
}

export type AcaoDecisaoEmail = 'APROVAR' | 'REJEITAR';

export type SituacaoDecisaoEmail =
  | 'DISPONIVEL'
  | 'DECIDIDA_POR_ESTE_LINK'
  | 'JA_DECIDIDA'
  | 'EXPIRADO'
  | 'SEM_PERMISSAO';

/** A página do link de decisão enviado por e-mail (sem login). */
export interface DecisaoEmail {
  situacao: SituacaoDecisaoEmail;
  mensagem: string;
  titulo: string;
  situacaoSolicitacao: string;
  valorSolicitado?: number | null;
  /** Quem recebeu o link; a decisão fica em nome dele. */
  decisor?: string | null;
  expiraEm: string;
  acaoTomada?: AcaoDecisaoEmail | null;
  ficha: { rotulo: string; valor: string }[];
}

export interface DecisaoEmailRequest {
  acao: AcaoDecisaoEmail;
  valorAprovado?: number | null;
  texto?: string | null;
}

export type TipoEventoSolicitacao =
  | 'CRIADA'
  | 'SUBMETIDA'
  | 'APROVADA'
  | 'REJEITADA'
  | 'DECISAO_DESFEITA'
  | 'CANCELADA';

/** Um ponto do histórico da solicitação, em ordem cronológica. */
export interface EventoSolicitacao {
  id: string;
  tipo: TipoEventoSolicitacao;
  rotulo: string;
  statusAnterior?: StatusSolicitacao | null;
  statusNovo: StatusSolicitacao;
  usuarioId?: string | null;
  usuario?: string | null;
  /**
   * Solicitado na abertura e no envio; autorizado na aprovação e no desfazer de uma
   * aprovação. Ausente quando não se aplica: a API omite campos nulos.
   */
  valor?: number | null;
  /** Observação da aprovação, ou motivo da rejeição, do cancelamento ou do desfazer. */
  texto?: string | null;
  ocorridoEm: string;
}

export interface DesfechoSolicitacao {
  usuarioDecisaoId?: string | null;
  usuarioDecisao?: string | null;
  dataDecisao?: string | null;
  valorAprovado?: number | null;
  observacaoDecisao?: string | null;
  motivoRejeicao?: string | null;
  usuarioCancelamentoId?: string | null;
  dataCancelamento?: string | null;
  motivoCancelamento?: string | null;
}

/** Fornecedor resolvido pelo backend para ser exibido em uma solicitação. */
export interface FornecedorSolicitacaoResponse {
  id: string | null;
  nome: string;
  razaoSocial: string | null;
  cadastrado: boolean;
}

/** Ações documentais autorizadas para o usuário atual. */
export interface AcoesDocumentoResponse {
  baixar: boolean;
  editarMetadados: boolean;
  remover: boolean;
  conferir: boolean;
  apontarPendencia: boolean;
  arquivar: boolean;
  reabrir: boolean;
  desvincular: boolean;
}

/**
 * Documento visto de dentro da solicitação: o bastante para reconhecer e baixar,
 * sem o aparato de conferência que só interessa ao repositório.
 */
export interface DocumentoResumoResponse {
  id: string;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  tipo: TipoDocumentoFinanceiro;
  tipoRotulo: string;
  situacao: SituacaoDocumento;
  situacaoRotulo: string;
  numeroDocumento: string | null;
  chaveAcesso: string | null;
  codigoPagamento: string | null;
  possuiCodigoPagamento: boolean;
  duplicado: boolean;
  identificacaoPendente: boolean;
  valor: number | null;
  usuarioConferenciaId: string | null;
  dataConferencia: string | null;
  observacaoConferencia: string | null;
  motivoPendencia: string | null;
  acoes: AcoesDocumentoResponse;
  dataCriacao: string;
  usuarioCriacao: string | null;
}

/**
 * Projeção compatível com respostas antigas. O backend novo preenche todos os
 * campos de `DocumentoResumoResponse`; versões intermediárias podem omitir os
 * indicadores, que a tela deve tratar como ações desabilitadas.
 */
export type DocumentoResumo = Omit<
  DocumentoResumoResponse,
  | 'numeroDocumento'
  | 'chaveAcesso'
  | 'codigoPagamento'
  | 'possuiCodigoPagamento'
  | 'duplicado'
  | 'identificacaoPendente'
  | 'valor'
  | 'usuarioConferenciaId'
  | 'dataConferencia'
  | 'observacaoConferencia'
  | 'motivoPendencia'
  | 'acoes'
> &
  Partial<
    Pick<
      DocumentoResumoResponse,
      | 'numeroDocumento'
      | 'chaveAcesso'
      | 'codigoPagamento'
      | 'possuiCodigoPagamento'
      | 'duplicado'
      | 'identificacaoPendente'
      | 'valor'
      | 'usuarioConferenciaId'
      | 'dataConferencia'
      | 'observacaoConferencia'
      | 'motivoPendencia'
      | 'acoes'
    >
  >;

/** Um documento completo, como o repositório o entrega. */
export interface DocumentoFinanceiro {
  id: string;

  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  hashConteudo?: string | null;
  /** Existe outro documento ativo com conteúdo idêntico. */
  duplicado: boolean;

  tipo: TipoDocumentoFinanceiro;
  tipoRotulo: string;
  situacao: SituacaoDocumento;
  situacaoRotulo: string;
  numeroDocumento?: string | null;
  chaveAcesso?: string | null;
  codigoPagamento?: string | null;
  possuiCodigoPagamento?: boolean;
  /** Nota ou boleto sem número — não serve como comprovação. */
  identificacaoPendente: boolean;

  fornecedorId?: string | null;
  emissorNome?: string | null;

  dataEmissao?: string | null;
  dataVencimento?: string | null;
  /** Mês contábil, sempre no dia 1. */
  competencia?: string | null;
  vencido: boolean;
  diasNaSituacao: number;
  valor?: number | null;

  baseId?: string | null;
  baseNome?: string | null;
  processoId?: string | null;
  processoNome?: string | null;
  obraId?: string | null;
  obraNome?: string | null;

  descricao?: string | null;
  observacoes?: string | null;

  origemTipo?: TipoSolicitacao | null;
  origemSolicitacaoId?: string | null;
  origemNumero?: number | null;

  usuarioEnvioId: string;
  usuarioEnvio?: string | null;
  usuarioConferenciaId?: string | null;
  usuarioConferencia?: string | null;
  dataConferencia?: string | null;
  observacaoConferencia?: string | null;
  motivoPendencia?: string | null;
  acoes?: AcoesDocumentoResponse;

  ativo: boolean;
  dataCriacao: string;
  dataUltimaAlteracao?: string | null;
  usuarioUltimaAlteracao?: string | null;
}

/**
 * Metadados enviados na criação e em cada correção.
 *
 * Quase tudo é opcional de propósito: o envio precisa ser barato, e a
 * classificação acontece depois, na conferência.
 */
export interface DocumentoRequest {
  tipo?: TipoDocumentoFinanceiro | null;
  numeroDocumento?: string | null;
  chaveAcesso?: string | null;
  /** Código integral para pagamento (PIX, boleto ou equivalente), até 512 caracteres. */
  codigoPagamento?: string | null;
  fornecedorId?: string | null;
  emissorNome?: string | null;
  dataEmissao?: string | null;
  dataVencimento?: string | null;
  competencia?: string | null;
  valor?: number | null;
  baseId?: string | null;
  processoId?: string | null;
  obraId?: string | null;
  descricao?: string | null;
  observacoes?: string | null;
}

/** Filtros da busca no repositório. */
export interface DocumentoFinanceiroFilter {
  buscaGlobal?: string | null;
  tipo?: TipoDocumentoFinanceiro[] | null;
  situacao?: SituacaoDocumento[] | null;
  fornecedorId?: string[] | null;
  baseId?: string | null;
  processoId?: string | null;
  obraId?: string | null;
  numeroDocumento?: string | null;
  chaveAcesso?: string | null;
  origemTipo?: TipoSolicitacao | null;
  origemSolicitacaoId?: string | null;
  origemNumero?: number | null;
  apenasAvulsos?: boolean | null;
  competenciaInicio?: string | null;
  competenciaFim?: string | null;
  emissaoInicio?: string | null;
  emissaoFim?: string | null;
  vencimentoInicio?: string | null;
  vencimentoFim?: string | null;
  apenasVencidos?: boolean | null;
  apenasSemIdentificacao?: boolean | null;
  valorMinimo?: number | null;
  valorMaximo?: number | null;
}

/** Identificação da compra que originou um pagamento, quando houver. */
export interface CompraVinculada {
  id: string;
  numero: number;
  status: StatusSolicitacao;
  valorAprovado?: number | null;
  fornecedor?: string | null;
  descricao?: string | null;
}

export interface SolicitacaoFinanceira {
  id: string;
  numero: number;
  status: StatusSolicitacao;
  formulario: FormularioResumo;
  valores: Record<string, ValorSolicitacao>;
  valor: number;
  /** Só em pagamento: a compra de origem, se o pagamento apontar para uma. */
  compraVinculada?: CompraVinculada | null;
  /** Fornecedor resolvido na própria resposta; não faça uma consulta por linha. */
  fornecedor?: FornecedorSolicitacaoResponse | null;
  usuarioSolicitanteId: string;
  usuarioSolicitante: string | null;
  nomeColaboradorSolicitante: string | null;
  dataSubmissao?: string | null;
  desfecho: DesfechoSolicitacao;
  /**
   * Documentos do repositório que apontam para esta solicitação. Diferente dos
   * antigos anexos, eles sobrevivem ao ciclo dela: a nota fiscal e o boleto podem
   * (e costumam) chegar depois da aprovação.
   */
  documentos: DocumentoResumo[];
  acoes: AcoesSolicitacao;
  ativo: boolean;
  dataCriacao: string;
  dataUltimaAlteracao?: string;
  usuarioUltimaAlteracao?: string;
}

/** Resposta completa das duas famílias de solicitação (compra e pagamento). */
export interface SolicitacaoFinanceiraResponse extends SolicitacaoFinanceira {
  fornecedor: FornecedorSolicitacaoResponse | null;
}

export type SolicitacaoCompraResponse = SolicitacaoFinanceiraResponse;
export type SolicitacaoPagamentoResponse = SolicitacaoFinanceiraResponse;

export interface SolicitacaoRequest {
  formularioId: string;
  valores: Record<string, unknown>;
}

/** Payloads das transições documentais executadas dentro do detalhe. */
export interface ConferirDocumentoRequest {
  observacao?: string | null;
}

export interface ApontarPendenciaDocumentoRequest {
  motivo: string;
}

export type ConferirDocumentoPayload = ConferirDocumentoRequest;
export type ApontarPendenciaDocumentoPayload = ApontarPendenciaDocumentoRequest;

export interface PrePreenchimentoPagamento {
  compra: CompraVinculada;
  valores: Record<string, ValorSolicitacao>;
  /** Campos copiados da compra; o `valor` vem como sugestão e não entra aqui. */
  camposHerdados: string[];
}

export interface Fornecedor {
  id: string;
  nomeFantasia: string;
  razaoSocial?: string | null;
  documento?: string | null;
  dadosBancarios?: string | null;
  observacoes?: string | null;
  status: 'PENDENTE' | 'HOMOLOGADO' | 'BLOQUEADO';
  motivoBloqueio?: string | null;
  utilizavel: boolean;
  ativo: boolean;
  dataCriacao?: string;
}

export interface FornecedorRequest {
  nomeFantasia: string;
  razaoSocial?: string | null;
  documento?: string | null;
  dadosBancarios?: string | null;
  observacoes?: string | null;
}

export interface ReferenciaOpcao {
  id: string;
  label: string;
  raw: Record<string, unknown>;
}

export interface FinanceiroProblemDetails {
  title?: string;
  detail?: string;
  status?: number;
  erros?: Record<string, string>;
  errors?: Record<string, string[]>;
}

/** Natureza do contexto de inspeção e cruzamento histórico */
export type TipoContextoInspecao =
  | 'SOLICITANTE'
  | 'BASE'
  | 'VEICULO'
  | 'FORNECEDOR'
  | 'PROCESSO'
  | 'OBRA'
  | 'CENTRO_CUSTO'
  | 'DECISOR';

/** Contexto ativo para a sidebar de inspeção e histórico à direita */
export interface InspecaoContexto {
  tipo: TipoContextoInspecao;
  id?: string | null;
  label: string;
  subtitulo?: string;
  icone: string;
  filtros: SolicitacaoFinanceiraFilter;
}

