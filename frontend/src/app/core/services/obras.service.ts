import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import {
  AgenteResponsavelFilter,
  AgenteResponsavelRequest,
  AgenteResponsavelResponse,
  AnexoObraResponse,
  ConfirmarImportacaoObraRequest,
  ImportacaoObraResponse,
  ObraFilter,
  ObraRequest,
  ObraResponse,
  ObraResumoResponse,
  StatusObraFilter,
  StatusObraRequest,
  StatusObraResponse,
  SubprojetoVinculoRequest,
  TipoAnexoObraFilter,
  TipoAnexoObraRequest,
  TipoAnexoObraResponse
} from '../models/obras.models';
import { PagedResponse } from '../models/shared.models';
import { toHttpParams } from '../utils/http-params.util';

@Injectable({
  providedIn: 'root'
})
export class ObrasService extends BaseService {
  private readonly apiUrl = environment.apiUrl;
  private readonly baseUrl = `${this.apiUrl}/v1/obras`;

  // Caches reativos para lookups frequentes
  private statusAtivos$?: Observable<StatusObraResponse[]>;
  private agentesAtivos$?: Observable<AgenteResponsavelResponse[]>;
  private tiposAnexosAtivos$?: Observable<TipoAnexoObraResponse[]>;

  // ═══════════════════════════════════════════════════════════════════════
  // Obras Principais (CRUD + Operações de Domínio)
  // ═══════════════════════════════════════════════════════════════════════

  listarObras(filter: ObraFilter = {}): Observable<PagedResponse<ObraResumoResponse>> {
    return this.getList<ObraResumoResponse>(this.baseUrl, filter);
  }

  buscarObraPorId(id: string): Observable<ObraResponse> {
    return this.getOne<ObraResponse>(this.baseUrl, id);
  }

  criarObra(request: ObraRequest): Observable<ObraResponse | null> {
    return this.createSync<ObraResponse>(this.baseUrl, request);
  }

  atualizarObra(id: string, request: ObraRequest): Observable<ObraResponse | null> {
    return this.updateSync<ObraResponse>(this.baseUrl, id, request);
  }

  alterarStatus(id: string, idNovoStatus: string, dataFimEfetiva?: string | null): Observable<ObraResponse> {
    let params = new HttpParams().set('idNovoStatus', idNovoStatus);
    if (dataFimEfetiva) {
      params = params.set('dataFimEfetiva', dataFimEfetiva);
    }
    return this.http.patch<ObraResponse>(`${this.baseUrl}/${id}/status`, null, { params });
  }

  desativarObra(id: string): Observable<void | null> {
    return this.deleteSync(this.baseUrl, id);
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Subprojetos (Obras Filhas)
  // ═══════════════════════════════════════════════════════════════════════

  vincularSubprojeto(idObraPai: string, request: SubprojetoVinculoRequest): Observable<ObraResponse> {
    return this.http.post<ObraResponse>(`${this.baseUrl}/${idObraPai}/subprojetos`, request);
  }

  desvincularSubprojeto(idObraPai: string, idFilho: string): Observable<ObraResponse> {
    return this.http.delete<ObraResponse>(`${this.baseUrl}/${idObraPai}/subprojetos/${idFilho}`);
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Anexos Técnicos da Obra
  // ═══════════════════════════════════════════════════════════════════════

  listarAnexos(idObra: string): Observable<AnexoObraResponse[]> {
    return this.http.get<AnexoObraResponse[]>(`${this.baseUrl}/${idObra}/anexos`);
  }

  anexarArquivo(
    idObra: string,
    idTipoAnexo: string,
    arquivo: File,
    observacoes?: string
  ): Observable<AnexoObraResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    formData.append('idTipoAnexo', idTipoAnexo);
    if (observacoes?.trim()) {
      formData.append('observacoes', observacoes.trim());
    }
    return this.http.post<AnexoObraResponse>(`${this.baseUrl}/${idObra}/anexos`, formData);
  }

  baixarAnexo(idObra: string, idAnexo: string, nomeArquivoFallback = 'anexo_obra'): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.baseUrl}/${idObra}/anexos/${idAnexo}/download`, {
      responseType: 'blob',
      observe: 'response'
    });
  }

  removerAnexo(idObra: string, idAnexo: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${idObra}/anexos/${idAnexo}`);
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Importação em Lote via CSV
  // ═══════════════════════════════════════════════════════════════════════

  baixarModeloCsv(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/importar/modelo`, {
      responseType: 'blob'
    });
  }

  validarImportacao(arquivo: File): Observable<ImportacaoObraResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoObraResponse>(`${this.baseUrl}/importar/validar`, formData);
  }

  confirmarImportacao(request: ConfirmarImportacaoObraRequest): Observable<ImportacaoObraResponse> {
    return this.http.post<ImportacaoObraResponse>(`${this.baseUrl}/importar/confirmar`, request);
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Exportação CSV (Streaming)
  // ═══════════════════════════════════════════════════════════════════════

  exportarResumo(filter: ObraFilter = {}): Observable<HttpResponse<Blob>> {
    const params = toHttpParams(filter as Record<string, unknown>, {
      ignorar: ['page', 'size']
    });
    return this.http.get(`${this.baseUrl}/exportar/resumo`, {
      params,
      responseType: 'blob',
      observe: 'response'
    });
  }

  exportarDetalhado(filter: ObraFilter = {}): Observable<HttpResponse<Blob>> {
    const params = toHttpParams(filter as Record<string, unknown>, {
      ignorar: ['page', 'size']
    });
    return this.http.get(`${this.baseUrl}/exportar/detalhado`, {
      params,
      responseType: 'blob',
      observe: 'response'
    });
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Cadastros Auxiliares: Status de Obras (/v1/obras/status)
  // ═══════════════════════════════════════════════════════════════════════

  listarStatusAtivos(forceRefresh = false): Observable<StatusObraResponse[]> {
    if (!this.statusAtivos$ || forceRefresh) {
      this.statusAtivos$ = this.http.get<StatusObraResponse[]>(`${this.baseUrl}/status/todos-ativos`).pipe(
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.statusAtivos$;
  }

  listarStatus(filter: StatusObraFilter = {}): Observable<PagedResponse<StatusObraResponse>> {
    return this.getList<StatusObraResponse>(`${this.baseUrl}/status`, filter);
  }

  buscarStatusPorId(id: string): Observable<StatusObraResponse> {
    return this.getOne<StatusObraResponse>(`${this.baseUrl}/status`, id);
  }

  criarStatus(request: StatusObraRequest): Observable<StatusObraResponse | null> {
    this.statusAtivos$ = undefined;
    return this.createSync<StatusObraResponse>(`${this.baseUrl}/status`, request);
  }

  atualizarStatus(id: string, request: StatusObraRequest): Observable<StatusObraResponse | null> {
    this.statusAtivos$ = undefined;
    return this.updateSync<StatusObraResponse>(`${this.baseUrl}/status`, id, request);
  }

  desativarStatus(id: string): Observable<void | null> {
    this.statusAtivos$ = undefined;
    return this.deleteSync(`${this.baseUrl}/status`, id);
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Cadastros Auxiliares: Agentes Responsáveis (/v1/obras/agentes)
  // ═══════════════════════════════════════════════════════════════════════

  listarAgentesAtivos(forceRefresh = false): Observable<AgenteResponsavelResponse[]> {
    if (!this.agentesAtivos$ || forceRefresh) {
      this.agentesAtivos$ = this.http.get<AgenteResponsavelResponse[]>(`${this.baseUrl}/agentes/todos-ativos`).pipe(
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.agentesAtivos$;
  }

  listarAgentes(filter: AgenteResponsavelFilter = {}): Observable<PagedResponse<AgenteResponsavelResponse>> {
    return this.getList<AgenteResponsavelResponse>(`${this.baseUrl}/agentes`, filter);
  }

  buscarAgentePorId(id: string): Observable<AgenteResponsavelResponse> {
    return this.getOne<AgenteResponsavelResponse>(`${this.baseUrl}/agentes`, id);
  }

  criarAgente(request: AgenteResponsavelRequest): Observable<AgenteResponsavelResponse | null> {
    this.agentesAtivos$ = undefined;
    return this.createSync<AgenteResponsavelResponse>(`${this.baseUrl}/agentes`, request);
  }

  atualizarAgente(id: string, request: AgenteResponsavelRequest): Observable<AgenteResponsavelResponse | null> {
    this.agentesAtivos$ = undefined;
    return this.updateSync<AgenteResponsavelResponse>(`${this.baseUrl}/agentes`, id, request);
  }

  desativarAgente(id: string): Observable<void | null> {
    this.agentesAtivos$ = undefined;
    return this.deleteSync(`${this.baseUrl}/agentes`, id);
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Cadastros Auxiliares: Tipos de Anexos (/v1/obras/tipos-anexos)
  // ═══════════════════════════════════════════════════════════════════════

  listarTiposAnexosAtivos(forceRefresh = false): Observable<TipoAnexoObraResponse[]> {
    if (!this.tiposAnexosAtivos$ || forceRefresh) {
      this.tiposAnexosAtivos$ = this.http.get<TipoAnexoObraResponse[]>(`${this.baseUrl}/tipos-anexos/todos-ativos`).pipe(
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.tiposAnexosAtivos$;
  }

  listarTiposAnexos(filter: TipoAnexoObraFilter = {}): Observable<PagedResponse<TipoAnexoObraResponse>> {
    return this.getList<TipoAnexoObraResponse>(`${this.baseUrl}/tipos-anexos`, filter);
  }

  buscarTipoAnexoPorId(id: string): Observable<TipoAnexoObraResponse> {
    return this.getOne<TipoAnexoObraResponse>(`${this.baseUrl}/tipos-anexos`, id);
  }

  criarTipoAnexo(request: TipoAnexoObraRequest): Observable<TipoAnexoObraResponse | null> {
    this.tiposAnexosAtivos$ = undefined;
    return this.createSync<TipoAnexoObraResponse>(`${this.baseUrl}/tipos-anexos`, request);
  }

  atualizarTipoAnexo(id: string, request: TipoAnexoObraRequest): Observable<TipoAnexoObraResponse | null> {
    this.tiposAnexosAtivos$ = undefined;
    return this.updateSync<TipoAnexoObraResponse>(`${this.baseUrl}/tipos-anexos`, id, request);
  }

  desativarTipoAnexo(id: string): Observable<void | null> {
    this.tiposAnexosAtivos$ = undefined;
    return this.deleteSync(`${this.baseUrl}/tipos-anexos`, id);
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Utilitário de Download no Navegador
  // ═══════════════════════════════════════════════════════════════════════

  baixarArquivoBlob(blob: Blob, nomeArquivo: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nomeArquivo;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }

  extrairNomeArquivoDeResponse(response: HttpResponse<Blob>, fallback: string): string {
    const contentDisposition = response.headers.get('content-disposition');
    if (contentDisposition) {
      const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
      if (utf8Match?.[1]) return decodeURIComponent(utf8Match[1]);
      const match = contentDisposition.match(/filename="?([^";]+)"?/i);
      if (match?.[1]) return match[1];
    }
    return fallback;
  }
}
