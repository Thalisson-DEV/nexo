import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  SyncErrorCaseDetail,
  SyncErrorCaseFilters,
  SyncErrorCorrectionRequest,
  SyncErrorPage,
  SyncDiagnostico
} from '../models/sync-error.models';

interface BackendCaseResponse {
  id: string;
  idOperacaoLocal: string;
  tipo: string;
  status: string;
  motivo: string;
  justificativaUltima?: string;
  idResponsavel?: string;
  idUsuarioOrigem?: string;
  recebidoEm?: string;
  atualizadoEm?: string;
  tentativas?: number;
  anexos?: Array<{ id: string; idLocal: string; nome: string; contentType: string; sha256: string; tamanhoBytes: number }>;
  original: unknown;
  ultimaVersao?: unknown;
  versoes?: Array<{ numero: number; payload: unknown; justificativa: string; hash: string; idUsuario: string; criadaEm?: string }>;
  diagnostico?: SyncDiagnostico;
  referencias?: Record<string, string>;
  resolvidoEm?: string;
}

@Injectable({ providedIn: 'root' })
export class SyncErrorCasesService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/v1/sincronizacao/casos`;

  list(filters: SyncErrorCaseFilters = {}): Observable<SyncErrorPage> {
    let params = new HttpParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        const apiKey = key === 'query' ? 'busca' : key === 'operationType' ? 'tipo' : key;
        params = params.set(apiKey, String(value));
      }
    });
    return this.http.get<{ content: BackendCaseResponse[]; page: SyncErrorPage['page'] }>(this.apiUrl, { params }).pipe(
      map(page => ({ ...page, content: page.content.map(item => this.mapCase(item)) }))
    );
  }

  get(id: string): Observable<SyncErrorCaseDetail> {
    return this.http.get<BackendCaseResponse>(`${this.apiUrl}/${id}`).pipe(map(item => this.mapCase(item)));
  }

  assign(id: string, assignedTo: string | null): Observable<SyncErrorCaseDetail> {
    return this.http.post<BackendCaseResponse>(`${this.apiUrl}/${id}/atribuicao`, { idResponsavel: assignedTo }).pipe(map(item => this.mapCase(item)));
  }

  saveCorrection(id: string, request: SyncErrorCorrectionRequest): Observable<SyncErrorCaseDetail> {
    return this.http.post<BackendCaseResponse>(`${this.apiUrl}/${id}/correcoes`, { payload: request.payload, justificativa: request.justification }).pipe(map(item => this.mapCase(item)));
  }

  reprocess(id: string, justification: string): Observable<SyncErrorCaseDetail> {
    return this.http.post<BackendCaseResponse>(`${this.apiUrl}/${id}/reprocessamento`, { justificativa: justification }).pipe(map(item => this.mapCase(item)));
  }

  discard(id: string, justification: string): Observable<SyncErrorCaseDetail> {
    return this.http.post<BackendCaseResponse>(`${this.apiUrl}/${id}/descarte`, { justificativa: justification }).pipe(map(item => this.mapCase(item)));
  }

  downloadAttachment(caseId: string, attachmentId: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${caseId}/anexos/${attachmentId}`, { responseType: 'blob' });
  }

  private mapCase(item: BackendCaseResponse): SyncErrorCaseDetail {
    const step = item.status === 'ABERTO' ? 'ANALYSIS' : item.status === 'AGUARDANDO_REPROCESSAMENTO' ? 'REVIEW' : 'CORRECTION';
    const latest = item.ultimaVersao ?? item.original;
    return {
      id: item.id, operationId: item.idOperacaoLocal, localUuid: item.idOperacaoLocal,
      operationType: item.tipo, tipo: item.tipo, status: item.status as SyncErrorCaseDetail['status'], step,
      errorTitle: item.motivo, errorDetail: item.motivo, motivo: item.motivo, justificativaUltima: item.justificativaUltima, attempts: item.tentativas ?? 0,
      receivedAt: item.recebidoEm ?? '', updatedAt: item.atualizadoEm ?? '', sourceUserId: item.idUsuarioOrigem,
      sourceUserName: item.idUsuarioOrigem, assignedToName: item.idResponsavel,
      originalPayload: item.original,
      attachments: (item.anexos ?? []).map(anexo => ({ id: anexo.id, fileName: anexo.nome, contentType: anexo.contentType, size: anexo.tamanhoBytes, sha256: anexo.sha256, receivedAt: item.recebidoEm ?? '' })),
      history: (item.versoes ?? []).map(versao => ({ id: `${item.id}:${versao.numero}`, action: `Correção ${versao.numero}`, actorName: versao.idUsuario, detail: versao.justificativa, createdAt: versao.criadaEm ?? '' })),
      correction: item.ultimaVersao ? { version: item.versoes?.at(-1)?.numero ?? 1, payload: latest, justification: item.versoes?.at(-1)?.justificativa ?? '', createdByName: item.versoes?.at(-1)?.idUsuario ?? '', createdAt: item.versoes?.at(-1)?.criadaEm ?? '' } : undefined,
      ultimaVersao: item.ultimaVersao, versoes: item.versoes,
      diagnostico: item.diagnostico, referencias: item.referencias ?? {}, resolvedAt: item.resolvidoEm
    };
  }
}
