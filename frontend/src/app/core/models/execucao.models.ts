import { PagedResponse } from './shared.models';
import { EquipeResponse, MunicipioResponse } from './rh.models';
import { AcaoResponse } from './contrato.models';
import { MaterialResponse } from './almoxarifado.models';

export type StatusExecucao = 'INICIADO' | 'CONCLUIDO' | 'EDITADO';
export type OperacaoMaterial = 'APLICADO' | 'RETIRADO';
/**
 * COMUM: nota com número da OS ou do talão. AVULSA: nota achada em campo, lançada com
 * número provisório; o número oficial é informado depois. OCORRENCIA: ocorrência
 * comercial. Os três contam faturamento e meta.
 */
export type TipoNota = 'COMUM' | 'AVULSA' | 'OCORRENCIA';
/** EQUIPE movimenta o saldo da equipe; CONCESSIONARIA (Concessionária) é só registro. */
export type OrigemMaterial = 'EQUIPE' | 'CONCESSIONARIA';

export const TIPOS_NOTA: Record<TipoNota, { rotulo: string; tom: 'neutral' | 'info' | 'warning' }> = {
  COMUM: { rotulo: 'Comum', tom: 'neutral' },
  AVULSA: { rotulo: 'Avulsa', tom: 'info' },
  OCORRENCIA: { rotulo: 'Ocorrência', tom: 'info' }
};

export const ORIGENS_MATERIAL: Record<OrigemMaterial, string> = {
  EQUIPE: 'Equipe',
  CONCESSIONARIA: 'Concessionária'
};

/** "Nota 123", "Nota avulsa AV-…" ou "Ocorrência OC-12". */
export function rotuloNota(e: Pick<ExecucaoComercialResponse, 'referenciaNota' | 'tipoNota'>): string {
  switch (e.tipoNota) {
    case 'AVULSA': return `Nota avulsa ${e.referenciaNota}`;
    case 'OCORRENCIA': return `Ocorrência ${e.referenciaNota}`;
    default: return `Nota ${e.referenciaNota}`;
  }
}

/**
 * Tipo de serviço da nota (Ligação nova, Corte, Religação...). O código é a
 * classificação da nota e se repete entre tipos; a descrição identifica o tipo.
 */
export interface TipoServicoResponse {
  id: string;
  codigo: string;
  descricao: string;
  ativo: boolean;
  dataCriacao?: string;
  dataUltimaAlteracao?: string;
}

export interface TipoServicoRequest {
  codigo: string;
  descricao: string;
}

export interface TipoServicoFilters {
  buscaGlobal?: string;
  codigo?: string;
  ativo?: boolean | null;
  page?: number;
  size?: number;
  sort?: string | string[];
}

/** Rótulo das notas lançadas sem tipo (antes do cadastro ou por versões antigas do app). */
export const TIPO_SERVICO_NAO_INFORMADO = 'Não informado';

export function rotuloTipoServico(tipo: Pick<TipoServicoResponse, 'codigo' | 'descricao'> | null | undefined): string {
  if (!tipo) return TIPO_SERVICO_NAO_INFORMADO;
  return tipo.codigo ? `${tipo.codigo} · ${tipo.descricao}` : tipo.descricao;
}

export interface ExecucaoAcaoResponse {
  id: string;
  acao: AcaoResponse;
}

export interface ExecucaoMaterialResponse {
  id: string;
  material: MaterialResponse;
  quantidade: number;
  operacao: OperacaoMaterial;
  numSerie?: string;
  origem: OrigemMaterial;
}

export interface ExecucaoComercialResponse {
  id: string;
  referenciaNota: string;
  idTurno: string;
  /** Nulo nas notas lançadas antes do cadastro de tipos de serviço. */
  tipoServico: TipoServicoResponse | null;
  equipe: EquipeResponse;
  /** Legado: lançamentos atuais registram apenas a localização. */
  municipio: MunicipioResponse | null;
  latitude?: number;
  longitude?: number;
  /** O lançamento é feito de uma só vez: esta é a única data da execução. */
  dataLancamento: string;
  statusExecucao: StatusExecucao;
  valorTotalFaturado: number;
  idUsuarioAlteracao?: string;
  dataAlteracao?: string;
  acoes: ExecucaoAcaoResponse[];
  materiais: ExecucaoMaterialResponse[];
  tipoNota: TipoNota;
  /** Número gerado pelo app para a nota avulsa; nulo nos demais tipos. */
  referenciaProvisoria: string | null;
  dataNumeroOficial: string | null;
  /** Nota avulsa que ainda está com o número provisório. */
  aguardandoNumeroOficial: boolean;
  /** Quantas vezes a nota foi alterada depois de sincronizada; 0 = como foi lançada. */
  totalAlteracoes: number;
  /** A nota já não é como foi lançada. */
  alterada: boolean;
}

/**
 * Como a nota deve ficar: a lista completa de serviços e materiais, e o porquê. Os
 * dados da nota vão só quando mudam (ausente = fica como está); tipo, tipo de serviço,
 * município e data são do escritório.
 */
export interface AlterarExecucaoRequest {
  acoes: string[];
  materiais: {
    idMaterial: string;
    quantidade: number;
    operacao: OperacaoMaterial;
    numSerie?: string | null;
    origem: OrigemMaterial;
  }[];
  motivo: string;
  referenciaNota?: string;
  tipoNota?: TipoNota;
  idTipoServico?: string;
  idMunicipio?: string;
  dataLancamento?: string;
}

/** Um dado da nota que mudou numa alteração, já em texto. */
export interface DadoAlterado {
  rotulo: string;
  anterior: string | null;
  novo: string | null;
}

export type TipoEventoNota = 'LANCADA' | 'ALTERADA' | 'NUMERO_OFICIAL';
export type OrigemAlteracaoNota = 'CAMPO' | 'ESCRITORIO';

export interface AcaoRegistrada {
  idAcao: string;
  codigo: string | null;
  nome: string;
}

export interface MaterialRegistrado {
  idMaterial: string;
  codigo: string | null;
  descricao: string;
  unidadeMedida: string | null;
  quantidade: number;
  operacao: OperacaoMaterial;
  numSerie: string | null;
  origem: OrigemMaterial;
}

/** Um ponto da linha do tempo da nota, com os nomes congelados no momento do evento. */
export interface EventoHistoricoNota {
  id: string | null;
  tipo: TipoEventoNota;
  origem: OrigemAlteracaoNota;
  autor: string;
  motivo: string | null;
  valorAnterior: number | null;
  valorNovo: number | null;
  detalhes: {
    acoesAdicionadas: AcaoRegistrada[];
    acoesRemovidas: AcaoRegistrada[];
    materiaisAdicionados: MaterialRegistrado[];
    materiaisRemovidos: MaterialRegistrado[];
    referenciaAnterior: string | null;
    referenciaNova: string | null;
    /** Número, tipo, serviço, município ou data que mudaram (ausente nos eventos antigos). */
    dadosAlterados?: DadoAlterado[];
  };
  ocorridoEm: string;
  registradoEm: string;
  /** Nota anterior ao histórico: o lançamento foi remontado desfazendo as alterações. */
  reconstruido: boolean;
}

export interface ExecucaoFilters {
  idTurno?: string;
  referenciaNota?: string;
  referencia?: string;
  idEquipe?: string;
  idMunicipio?: string;
  dataLancamentoDe?: string;
  dataLancamentoAte?: string;
  status?: StatusExecucao;
  idAcao?: string;
  idMaterial?: string;
  operacaoMaterial?: OperacaoMaterial;
  numSerieMaterial?: string;
  idTipoServico?: string;
  /** Apenas notas sem tipo de serviço informado. */
  semTipoServico?: boolean;
  tipoNota?: TipoNota;
  /** Apenas notas avulsas que ainda esperam o número oficial. */
  aguardandoNumeroOficial?: boolean;
  alterada?: boolean;
  apenasHoje?: boolean;
  buscaGlobal?: string;
  page?: number;
  size?: number;
  sort?: string | string[];
}
