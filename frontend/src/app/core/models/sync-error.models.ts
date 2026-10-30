import { PagedResponse } from './shared.models';

export type SyncCaseStatus = 'ABERTO' | 'EM_ANALISE' | 'AGUARDANDO_REPROCESSAMENTO' | 'RESOLVIDO' | 'REJEITADO';
export type SyncCaseStep = 'ANALYSIS' | 'CORRECTION' | 'REVIEW';
/** Próximo passo recomendado pelo servidor para quem trata o caso. */
export type SyncAcaoRecomendada = 'REENVIAR' | 'CORRIGIR' | 'CONFERIR' | 'AGUARDAR' | 'DESCARTAR';

export interface SyncDiagnostico {
  codigo: string;
  titulo: string;
  explicacao: string;
  orientacao: string;
  acao: SyncAcaoRecomendada;
}

export interface SyncErrorCaseSummary {
  id: string;
  operationId: string;
  localUuid: string;
  operationType: string;
  status: SyncCaseStatus;
  step: SyncCaseStep;
  sourceUserName?: string;
  sourceDeviceId?: string;
  errorTitle: string;
  errorDetail: string;
  attempts: number;
  receivedAt: string;
  updatedAt: string;
  assignedToName?: string;
  motivo?: string;
  justificativaUltima?: string;
  tipo?: string;
  /** Leitura do último erro: o que aconteceu e o que fazer. */
  diagnostico?: SyncDiagnostico;
  originalPayload?: unknown;
}

export interface SyncErrorCaseDetail extends SyncErrorCaseSummary {
  sourceUserId?: string;
  originalPayload: unknown;
  attachments: SyncErrorAttachment[];
  correction?: SyncErrorCorrection;
  history: SyncErrorHistoryEntry[];
  ultimaVersao?: unknown;
  versoes?: Array<{ numero: number; payload: unknown; justificativa: string; hash: string; idUsuario: string; criadaEm?: string }>;
  /** Nome de cada cadastro citado no registro, pela referência recebida do aparelho. */
  referencias?: Record<string, string>;
  resolvedAt?: string;
}

export interface SyncErrorAttachment {
  id: string;
  fileName: string;
  contentType: string;
  size: number;
  sha256: string;
  receivedAt: string;
}

export interface SyncErrorCorrection {
  version: number;
  payload: unknown;
  justification: string;
  createdByName: string;
  createdAt: string;
}

export interface SyncErrorHistoryEntry {
  id: string;
  action: string;
  actorName: string;
  detail?: string;
  createdAt: string;
}

export interface SyncErrorCaseFilters {
  query?: string;
  status?: SyncCaseStatus;
  operationType?: string;
  assignedTo?: string;
  receivedFrom?: string;
  receivedTo?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface SyncErrorCorrectionRequest {
  payload: unknown;
  justification: string;
}

export interface SyncErrorReviewRequest {
  approved: boolean;
  justification: string;
}

export type SyncErrorPage = PagedResponse<SyncErrorCaseSummary>;
