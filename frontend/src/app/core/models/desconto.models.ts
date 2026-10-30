// Modelos Baseados no FRONTEND_DESCONTOS_GUIDE.md para Admin
export interface TipoEquipamentoResponse {
  id: string; // UUID
  descricao: string;
  ativo: boolean;
}

export interface TipoEquipamentoRequest {
  descricao: string;
}

export interface MotivoDescontoResponse {
  id: string; // UUID
  descricao: string;
  tetoParcelas: number;
  ativo: boolean;
}

export interface MotivoDescontoRequest {
  descricao: string;
  tetoParcelas: number;
}

// ----------------------------------------------------
// DTOs e Contratos para Lançamento e Gestão de Descontos
// ----------------------------------------------------

export type DescontoStatus = 'SOLICITADO' | 'VALIDADO' | 'BAIXADO' | 'REJEITADO';

export interface DescontoRequest {
  colaboradorId: string;
  motivoId: string;
  observacoes?: string;
  quantidadeParcelas: number;
  dataOcorrencia: string;
}

export interface DescontoItemRequest {
  descricaoMaterial: string;
  tipoEquipamentoId: string;
  valor: number;
}

/** Corpo obrigatório do PATCH /descontos/{id}/validar */
export interface DescontoValidacaoRequest {
  valorValidado: number;
}

/** Corpo obrigatório do PATCH /descontos/{id}/baixar */
export interface DescontoBaixaRequest {
  valorBaixado: number;
}

/** Corpo obrigatório do PATCH /descontos/{id}/rejeitar */
export interface DescontoRejeicaoRequest {
  motivoRejeicao: string;
}

export interface DescontoFilter {
  page?: number;
  size?: number;
  buscaGlobal?: string;
  colaboradorId?: string;
  motivoId?: string;
  status?: DescontoStatus;
  usuarioSolicitanteId?: string;
  dataCriacaoInicio?: string;
  dataCriacaoFim?: string;
  dataOcorrenciaInicio?: string;
  dataOcorrenciaFim?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  }
}

export interface DescontoItemResponse {
  id: string;
  descricaoMaterial: string;
  tipoEquipamento: TipoEquipamentoResponse;
  valor: number;
}

export interface DescontoAnexoResponse {
  id: string;
  nomeArquivo: string;
  /** URL interna do storage — use GET /{id}/anexos/{anexoId}/download para obter o binário. */
  url: string;
  contentType?: string;
  tamanhoBytes?: number;
}

export interface DescontoResponse {
  id: string;
  colaboradorId: string;
  motivo: MotivoDescontoResponse;
  observacoes?: string;
  quantidadeParcelas: number;
  status: DescontoStatus;
  dataOcorrencia: string;

  // Valores por etapa — o que vale no fim é o valorBaixado
  /** Somatório automático dos itens, calculado pelo backend. Nunca enviado pelo front. */
  valorSugerido: number;
  /** Informado no PATCH /validar. null enquanto não validado. */
  valorValidado?: number | null;
  /** Informado no PATCH /baixar. null enquanto não baixado. */
  valorBaixado?: number | null;

  // Auditoria
  usuarioSolicitanteId: string;
  usuarioValidadorId?: string;
  usuarioBaixaId?: string;
  usuarioRejeicaoId?: string;
  dataCriacao: string;
  dataValidacao?: string;
  dataBaixa?: string;
  dataRejeicao?: string;
  motivoRejeicao?: string;
  usuarioUltimaAlteracao?: string;
  dataUltimaAlteracao?: string;

  // Coleções
  itens: DescontoItemResponse[];
  anexos: DescontoAnexoResponse[];
}

/**
 * Valor que vale para o desconto no momento atual do ciclo:
 * baixado > validado > sugerido.
 */
export function getValorEfetivo(desconto: Pick<DescontoResponse, 'valorSugerido' | 'valorValidado' | 'valorBaixado'>): number {
  if (desconto.valorBaixado !== null && desconto.valorBaixado !== undefined) return desconto.valorBaixado;
  if (desconto.valorValidado !== null && desconto.valorValidado !== undefined) return desconto.valorValidado;
  return desconto.valorSugerido ?? 0;
}

/** Rótulo da etapa que originou o valor efetivo (para exibição). */
export function getEtapaValorEfetivo(desconto: Pick<DescontoResponse, 'valorSugerido' | 'valorValidado' | 'valorBaixado'>): string {
  if (desconto.valorBaixado !== null && desconto.valorBaixado !== undefined) return 'Baixado';
  if (desconto.valorValidado !== null && desconto.valorValidado !== undefined) return 'Validado';
  return 'Sugerido';
}

export * from '../utils/desconto-anexo.util';
