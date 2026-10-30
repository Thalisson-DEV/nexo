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
export type StatusImportacaoObra = 'PROCESSANDO' | 'CONCLUIDA' | 'FALHOU';

export interface ErroLinhaImportacao {
  linha: number;
  referencia?: string | null;
  motivo: string;
}

export interface HistoricoImportacaoObraResponse {
  id: string;
  tipo: TipoImportacaoObra;
  idContrato?: string | null;
  nomeArquivo: string;
  status: StatusImportacaoObra;
  totalLinhas: number;
  inseridos: number;
  atualizados: number;
  erros: number;
  detalheErros: ErroLinhaImportacao[];
  mensagem?: string | null;
  usuarioCriacao?: string | null;
  dataCriacao?: string | null;
  dataConclusao?: string | null;
}
