export interface EquipeLocal {
  id: string;
  codigo: string;
  nomeTipoEquipe: string;
  nomeProcesso: string;
  idBaseOperacional?: string;
  nomeBaseOperacional: string;
  idVeiculo?: string;
  membrosIds?: string[];
  metaFaturamentoMensal?: number | null;
  metaFaturamentoDiaria?: number | null;
}

export interface BaseOperacionalLocal {
  id: string;
  nome: string;
  latitude: number | null;
  longitude: number | null;
  raio: number | null;
  ativo: boolean;
}

export interface ColaboradorLocal {
  id: string;
  idUsuario: string;
  nomeCompleto: string;
  funcao: string;
  ativo: boolean;
}

export interface MunicipioLocal {
  id: string;
  nome: string;
  idBaseOperacional: string;
  ativo: boolean;
}

export interface VeiculoLocal {
  id: string;
  placa: string;
  modelo: string;
  fabricante: string;
  ativo: boolean;
}

export interface MaterialLocal {
  id: string;
  codigo: string;
  descricao: string;
  unidadeMedida: string;
  detalhaSerie: boolean;
}

export interface EstoqueLocal {
  id: string;
  nomeIdentificador: string;
  tipoEstoque: string; // ex: 'CAMPO', 'MOVEL', 'DEPOSITO'
  idEquipe?: string;
  ativo?: boolean;
}

export interface SaldoLocal {
  id: string;
  idEstoque: string;
  idMaterial: string;
  quantidade: number;
  ativo: boolean;
  condicao: 'NOVO' | 'SUCATA';
  /** Última vez que a quantidade mudou (ISO): base do "parado há N dias". */
  ultimaMovimentacao?: string | null;
}

export interface SaldoSerieLocal {
  id: string;
  idEstoque: string;
  idMaterial: string;
  numSerie: string;
  ativo: boolean;
  condicao: 'NOVO' | 'SUCATA';
  /** Desde quando a série está neste estoque (ISO). */
  noSaldoDesde?: string | null;
}

export interface ProcessoLocal {
  id: string;
  nome: string;
  ativo: boolean;
}

export interface AcaoComercialLocal {
  id: string;
  codigo: string;
  nome: string;
  ativo: boolean;
  valor?: number;
}

/**
 * Tipo de serviço da nota (Ligação nova, Corte, Religação...). O código é a
 * classificação da nota e se repete entre tipos; quem identifica é a descrição.
 */
export interface TipoServicoLocal {
  id: string;
  codigo: string;
  descricao: string;
  ativo: boolean;
}

// Interfaces de Operação Offline
export interface ItemSolicitacaoLocal {
  idMaterial: string;
  quantidade: number;
}

export interface SolicitacaoLocal {
  id: string;
  idEstoqueOrigem: string;
  idEstoqueDestino: string;
  idEquipe?: string;
  dataPrevista?: string;
  idColaboradorSaque?: string | null;
  saqueForaEquipe?: boolean;
  idRequisitante: string;
  status: 'PENDENTE' | 'APROVADO' | 'REJEITADO' | 'ENTREGUE_PARCIAL' | 'ENTREGUE' | 'ESTORNADO';
  dataSolicitacao?: string;
  itens: ItemSolicitacaoLocal[];
  motivoRejeicao?: { nome: string; descricao: string };
  justificativaRejeicao?: string;
}

export interface TransferenciaLocal {
  id: string;
  idEstoqueOrigem: string;
  idEstoqueDestino: string;
  dataTransferencia?: string;
  itens: {
    idMaterial: string;
    quantidade: number;
    condicao: 'NOVO' | 'SUCATA';
    numSerie?: string | null;
  }[];
  assinaturaEntregador?: string;
  assinaturaRecebedor?: string;
  // Controle local para a lista histórica
  statusSync: 'PENDENTE' | 'SINCRONIZADO' | 'ERRO' | 'RESULTADO_INCERTO' | 'RECEBIDO_BACKOFFICE';
  erroDetalhes?: string;
}

/**
 * COMUM: nota com número da OS ou do talão. AVULSA: nota achada em campo, sem número
 * (o app gera um provisório e o escritório informa o oficial depois). OCORRENCIA:
 * ocorrência comercial, com número de letras e números. Os três contam meta.
 */
export type TipoNota = 'COMUM' | 'AVULSA' | 'OCORRENCIA';

/** EQUIPE mexe no saldo do caminhão; CONCESSIONARIA (Concessionária) é só registro, na ocorrência. */
export type OrigemMaterial = 'EQUIPE' | 'CONCESSIONARIA';

export interface ExecucaoMaterialLocal {
  idMaterial: string;
  quantidade: number;
  operacao: 'APLICADO' | 'RETIRADO';
  numSerie?: string;
  condicao: 'NOVO' | 'SUCATA';
  /** Ausente nos registros anteriores à ocorrência comercial: vale EQUIPE. */
  origem?: OrigemMaterial;
}

export interface ExecucaoCampoLocal {
  id: string;
  referenciaNota: string;
  /** Ausente nos registros anteriores à nota avulsa e à ocorrência: vale COMUM. */
  tipoNota?: TipoNota;
  /** Número gerado pelo app para a nota avulsa (continua aqui depois do número oficial). */
  referenciaProvisoria?: string | null;
  /** Nota avulsa que ainda está com o número provisório. */
  aguardandoNumeroOficial?: boolean;
  idTurno: string;
  idEquipe: string;
  /**
   * Tipo de serviço escolhido no lançamento, guardado inteiro para continuar legível
   * mesmo se o tipo for desativado depois. Nulo nas notas anteriores ao cadastro.
   */
  tipoServico?: Pick<TipoServicoLocal, 'id' | 'codigo' | 'descricao'> | null;
  /** Legado: execuções novas registram apenas a localização (GPS). */
  idMunicipio?: string | null;
  latitude?: number;
  longitude?: number;
  /** O lançamento é feito de uma só vez: esta é a única data da execução. */
  dataLancamento: string;
  statusExecucao: 'INICIADO' | 'CONCLUIDO' | 'EDITADO';
  acoes: string[]; // IDs das ações
  materiais: ExecucaoMaterialLocal[];
  // Controle local: 'LOCAL' = ainda não persistida no servidor; 'SINCRONIZADO' = já existe lá.
  statusSync: 'LOCAL' | 'SINCRONIZADO' | 'ERRO' | 'RESULTADO_INCERTO' | 'RECEBIDO_BACKOFFICE';
  erroDetalhes?: string;
  valorTotalFaturado?: number;
  /** Quantas vezes a nota foi alterada depois de sincronizada (vem do servidor). */
  totalAlteracoes?: number;
  /**
   * Alteração feita no aparelho numa nota já sincronizada e que ainda não chegou ao
   * servidor. Enquanto existir, `acoes` e `materiais` já mostram a nota corrigida.
   */
  alteracaoPendente?: AlteracaoPendenteLocal | null;
}

/** Correção de uma nota sincronizada, guardada no aparelho até o envio. */
export interface AlteracaoPendenteLocal {
  /** Vai ao servidor: o reenvio da mesma alteração não a aplica duas vezes. */
  idOperacao: string;
  motivo: string;
  /** Quando a pessoa alterou (pode ser bem antes do envio, se estava offline). */
  alteradoEm: string;
  /** Como a nota está no servidor: volta a valer se a alteração for desfeita. */
  anterior: { acoes: string[]; materiais: ExecucaoMaterialLocal[]; valorTotalFaturado?: number; referenciaNota?: string };
}

export interface VeiculoComposicaoLocal {
  id: string;
  placa: string;
  modelo?: string | null;
}

export interface MembroComposicaoLocal {
  idColaborador: string;
  nome: string;
  funcao?: string | null;
  responsavel: boolean;
}

export interface OpcaoColaboradorLocal {
  id: string;
  nome: string;
  funcao?: string | null;
  /** Código da equipe em que o colaborador está hoje (pode ser a própria). */
  equipeAtual?: string | null;
  /** Responsável de outra equipe não pode ser transferido pelo app. */
  responsavelEmOutraEquipe: boolean;
}

/**
 * Meta da janela vigente da equipe (ciclo comercial de 26 a 25, ou mês civil),
 * como o servidor apurou em `apuradoEm`. Datas no formato `AAAA-MM-DD`.
 */
export interface MetaDoCicloLocal {
  periodicidade: 'CICLO_COMERCIAL' | 'MENSAL';
  /** Mês de referência `AAAA-MM`: no ciclo, o mês em que ele termina. */
  competencia: string;
  inicio: string;
  fim: string;
  valor: number;
  metaDiaria: number | null;
  diasUteis: number;
  /** Faturamento que o servidor já tinha recebido na janela em `apuradoEm`. */
  realizado: number;
  apuradoEm: string;
}

/**
 * Retrato da equipe do encarregado para a conferência na abertura do turno.
 * Guardado em `config` para funcionar offline.
 */
export interface ComposicaoEquipeLocal {
  idEquipe: string;
  codigo: string;
  veiculo: VeiculoComposicaoLocal | null;
  membros: MembroComposicaoLocal[];
  metaFaturamentoMensal: number | null;
  metaFaturamentoDiaria: number | null;
  /** Meta do ciclo/mês vigente; nula sem meta cadastrada (ou em dado antigo). */
  metaDoCiclo?: MetaDoCicloLocal | null;
  usuarioResponsavel: boolean;
  opcoes: {
    colaboradores: OpcaoColaboradorLocal[];
    veiculos: VeiculoComposicaoLocal[];
  };
  atualizadoEm: string;
}

export interface TurnoLocal {
  id: string;
  idEquipe: string;
  idUsuarioAbertura: string;
  dataInicio: string;
  dataFim?: string;
  status: 'ABERTO' | 'FECHADO';
  idVeiculo?: string | null;
  idsColaboradores?: string[];
  /** A equipe esqueceu o turno aberto e o servidor o encerrou no horário de corte. */
  encerradoAutomaticamente?: boolean;
  motivoEncerramento?: string | null;
}

export interface OutboxItem {
  id: string; // UUID local
  endpoint: string;
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  payload: any;
  status: 'PENDENTE' | 'ERRO' | 'RESULTADO_INCERTO' | 'SINCRONIZADO';
  /** Estado do recebimento durável no servidor, independente do resultado do domínio. */
  receiptStatus?: 'LOCAL' | 'QUEUED' | 'RECEIVED' | 'RESOLVED' | 'CONFLICT';
  /** Identificador da operação no backoffice, quando o backend confirmar o recebimento. */
  serverOperationId?: string;
  /** Cópia imutável da intenção original, preservada para reconciliação e suporte. */
  originalPayload?: any;
  operationType?: string;
  erroDetalhes?: string;
  tentativas: number;
  criadoEm: Date;
  /**
   * Epoch (ms) antes do qual o item não é reenviado (espera crescente após 5xx).
   * Campo não indexado: entra sem mudar a versão do `ces-field-db` (decisão D5).
   */
  proximaTentativaEm?: number;
  /**
   * Epoch (ms) em que o sync reivindicou o item para enviar. A partir daí o servidor
   * pode ter gravado a operação, então o item não pode mais ser corrigido nem
   * substituído no aparelho. Campo não indexado, sem mudar a versão do banco.
   */
  envioIniciadoEm?: number;
}

export interface TipoInspecaoLocal {
  id: string;
  nome: string;
  descricao?: string | null;
  ordem: number;
  ativo: boolean;
  dataCriacao?: string;
  dataUltimaAlteracao?: string;
}

export interface InspecaoLocal {
  id: string;
  idFormulario: string;
  idTipoInspecao?: string;
  nomeTipoInspecao?: string;
  tipoInspecao?: TipoInspecaoLocal;
  idProcesso?: string;
  idEquipe?: string;
  idBaseOperacional?: string;
  nomeBaseOperacional?: string;
  idVeiculo?: string;
  idColaboradoresInspecionados?: string[];
  /** Identificador retornado pelo servidor; nunca faz parte do payload de lançamento. */
  idInspetor?: string;
  latitude?: number;
  longitude?: number;
  observacoes?: string;
  status: 'EM_ANDAMENTO' | 'CONCLUIDA' | 'CANCELADA';
  dataRealizacao: string;
  statusSync: 'PENDENTE' | 'SINCRONIZADO' | 'ERRO' | 'RESULTADO_INCERTO';
  erroDetalhes?: string;
  draftData?: string; // JSON with partial respostas and desvios
  formularioSnapshot?: FormularioInspecao;
  payload?: RegistrarInspecaoRequest;
  anexosPendentes?: AnexoPendenteLocal[];
  /**
   * Evidências obrigatórias com os `Blob`s retidos. Só são liberadas depois do
   * `201` do lançamento — antes disso o `idRemoto` pode ter expirado.
   */
  evidencias?: EvidenciaLocal[];
  /** Metadados das evidências já vinculadas, vindos da resposta canônica. */
  evidenciasRemotas?: EvidenciaInspecaoResponse[];
  idRemoto?: string | null;
}

export interface OpcaoRespostaLocal {
  id: string;
  texto: string;
  geraDesvio: boolean;
  gravidade: 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA' | null;
  padrao?: boolean;
  ordem: number;
}

export interface PerguntaLocal {
  id: string;
  enunciado: string;
  tipoResposta: 'MULTIPLA_ESCOLHA' | 'MULTIPLA_SELECAO' | 'TEXTO' | 'NUMERO' | 'DATA';
  obrigatoria: boolean;
  ordem: number;
  opcoes: OpcaoRespostaLocal[];
}

export interface GrupoPerguntaLocal {
  id: string;
  titulo: string;
  ordem: number;
  perguntas: PerguntaLocal[];
}

export interface FormularioLocal {
  id: string;
  titulo: string;
  descricao: string | null;
  ativo: boolean;
  emUso?: boolean;
  grupos: GrupoPerguntaLocal[];
}
import {
  AnexoPendenteLocal,
  EvidenciaInspecaoResponse,
  EvidenciaLocal,
  FormularioInspecao,
  RegistrarInspecaoRequest,
} from '../sst/inspecao.models';
