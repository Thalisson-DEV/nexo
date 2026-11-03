// ═══════════════════════════════════════════════════════════════════════
// Kits construtivos e importações assíncronas do processo de Obras
// ═══════════════════════════════════════════════════════════════════════

export type ClasseTensao = 'BT' | 'MT_15' | 'MT_25' | 'MT_36' | 'AT_72' | 'AT_138';
export type TipoItemKit = 'SERVICO' | 'MATERIAL';
export type OperacaoItemKit = 'INSTALAR' | 'RETIRAR';

export const CLASSES_TENSAO: { label: string; value: ClasseTensao }[] = [
  { label: 'BT', value: 'BT' },
  { label: '15 kV', value: 'MT_15' },
  { label: '25 kV', value: 'MT_25' },
  { label: '36,2 kV', value: 'MT_36' },
  { label: '72,5 kV', value: 'AT_72' },
  { label: '138 kV', value: 'AT_138' }
];

export function rotuloClasseTensao(classe?: ClasseTensao | null): string {
  return CLASSES_TENSAO.find((c) => c.value === classe)?.label ?? '';
}

export interface ItemKitResponse {
  id: string;
  tipo: TipoItemKit;
  idReferencia: string;
  codigoReferencia?: string | null;
  descricaoReferencia?: string | null;
  operacao: OperacaoItemKit;
  unidade?: string | null;
  quantidade: number;
  valorUnitario?: number | null;
  valorTotal?: number | null;
}

export interface KitConstrutivoResponse {
  id: string;
  idContrato: string;
  numeroContrato?: string | null;
  codigo: string;
  descricao: string;
  grupo?: string | null;
  classesTensao: ClasseTensao[];
  quantidadeItens: number;
  quantidadeServicos: number;
  quantidadeMateriais: number;
  valor: number;
  servicosSemValor: number;
  dataReferenciaValor?: string | null;
  /** Vem preenchido só no detalhe; a listagem traz só as contagens. */
  itens: ItemKitResponse[];
  ativo: boolean;
  versao?: number;
  dataCriacao?: string;
  dataUltimaAlteracao?: string;
}

export interface ItemKitRequest {
  id?: string | null;
  tipo: TipoItemKit;
  idReferencia: string;
  operacao: OperacaoItemKit;
  unidade?: string | null;
  quantidade: number;
}

export interface KitConstrutivoRequest {
  idContrato: string;
  codigo: string;
  descricao: string;
  grupo?: string | null;
  classesTensao: ClasseTensao[];
  itens: ItemKitRequest[];
}

export interface KitConstrutivoFilter {
  buscaGlobal?: string;
  idContrato?: string;
  grupo?: string;
  classeTensao?: ClasseTensao;
  ativo?: boolean;
  page?: number;
  size?: number;
  sort?: string | string[];
}

export type TipoImportacaoObra = 'KITS' | 'CARTEIRA' | 'ORCAMENTO';
export type SituacaoLinhaImportacao = 'AGUARDANDO_PROCESSAMENTO' | 'COM_ERRO' | 'EM_PROCESSAMENTO' | 'CONCLUIDO';

/** Campos comuns de toda linha da pré-visualização (formato de `CesImportRow`). */
interface LinhaImportacaoBase {
  linha?: number;
  status?: SituacaoLinhaImportacao;
  mensagemErro?: string | null;
  editing?: boolean;
}

/** Uma linha da planilha de kits: um item de um kit. */
export interface KitImportacaoLinha extends LinhaImportacaoBase {
  codigoKit?: string | null;
  descricaoKit?: string | null;
  grupo?: string | null;
  tensoes?: string | null;
  tipoItem?: string | null;
  codigoItem?: string | null;
  operacao?: string | null;
  quantidade?: string | null;
  unidade?: string | null;
}

/** Uma linha da carteira: uma obra (existente ou nova). */
export interface CarteiraImportacaoLinha extends LinhaImportacaoBase {
  numero?: string | null;
  titulo?: string | null;
  tensao?: string | null;
  prazo?: string | null;
  prazoRegulado?: string | null;
  prioridade?: string | null;
  lotePrevisto?: string | null;
  valorProjeto?: string | null;
  valorMaterialPrevisto?: string | null;
  valorServicoPrevisto?: string | null;
  latitude?: string | null;
  longitude?: string | null;
  processoNegocio?: string | null;
  baseOperacional?: string | null;
  municipio?: string | null;
  centroCusto?: string | null;
  supervisor?: string | null;
  coordenador?: string | null;
  statusObra?: string | null;
  dataInicio?: string | null;
  tipoObra?: string | null;
  /** Preenchido pela validação: a obra ainda não existe e será criada. */
  obraNova?: boolean | null;
}

/** Uma linha do orçamento previsto: um serviço de uma obra. */
export interface OrcamentoImportacaoLinha extends LinhaImportacaoBase {
  numeroObra?: string | null;
  codigoServico?: string | null;
  descricao?: string | null;
  unidade?: string | null;
  quantidade?: string | null;
  valorUnitario?: string | null;
  valorTotal?: string | null;
  valorMaterialPrevisto?: string | null;
  valorServicoPrevisto?: string | null;
}

export interface PreviaImportacaoObraResponse<T> {
  registros: T[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
  idImportacao?: string | null;
}

export interface ConfirmarImportacaoPlanilhaRequest<T> {
  idContrato?: string | null;
  nomeArquivo?: string | null;
  registros: T[];
}
