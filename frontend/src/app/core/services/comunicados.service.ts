import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AdiarComunicadoRequest,
  AnexoComunicadoResponse,
  ComunicadoFiltros,
  ComunicadoMuralResponse,
  ComunicadoRequest,
  ComunicadoResponse,
  CorrigirTextoComunicadoRequest,
  PublicarComunicadoRequest,
  RelatorioConfirmacaoResponse
} from '../models/notificacao.models';
import { PagedResponse } from '../models/shared.models';

/**
 * Mural de comunicados: consumo (`/v1/mural`, qualquer autenticado) e administração
 * (`/v1/comunicados`, exige `notificacao:comunicado:manage`).
 */
@Injectable({ providedIn: 'root' })
export class ComunicadosService {
  private readonly http = inject(HttpClient);
  private readonly muralUrl = `${environment.apiUrl}/v1/mural`;
  private readonly adminUrl = `${environment.apiUrl}/v1/comunicados`;

  // -------------------------------------------------------------------------
  // Consumo — o servidor já decide o que este usuário deve ver
  // -------------------------------------------------------------------------

  /** Já vem ordenado por `fixado DESC, dataInicio DESC`. Preserve a ordem. */
  listarMural(): Observable<ComunicadoMuralResponse[]> {
    return this.http.get<ComunicadoMuralResponse[]>(this.muralUrl);
  }

  /** Registra a abertura. Não remove nada do mural. */
  registrarVisualizacao(id: string): Observable<void> {
    return this.http.patch<void>(`${this.muralUrl}/${id}/visualizacao`, null);
  }

  /** "Não exibir novamente" — some para sempre para este usuário. */
  dispensar(id: string): Observable<void> {
    return this.http.delete<void>(`${this.muralUrl}/${id}`);
  }

  /** Sem corpo o servidor aplica a própria política de adiamento. */
  adiar(id: string, request?: AdiarComunicadoRequest): Observable<void> {
    return this.http.post<void>(`${this.muralUrl}/${id}/adiamento`, request ?? null);
  }

  confirmar(id: string): Observable<void> {
    return this.http.post<void>(`${this.muralUrl}/${id}/confirmacao`, null);
  }

  /** Download autenticado — precisa do Bearer, não funciona como `<a href>` direto. */
  baixarAnexoMural(id: string, idAnexo: string): Observable<Blob> {
    return this.http.get(`${this.muralUrl}/${id}/anexos/${idAnexo}`, { responseType: 'blob' });
  }

  // -------------------------------------------------------------------------
  // Administração
  // -------------------------------------------------------------------------

  listar(filtros: ComunicadoFiltros = {}): Observable<PagedResponse<ComunicadoResponse>> {
    return this.http.get<PagedResponse<ComunicadoResponse>>(this.adminUrl, {
      params: this.params(filtros)
    });
  }

  buscar(id: string): Observable<ComunicadoResponse> {
    return this.http.get<ComunicadoResponse>(`${this.adminUrl}/${id}`);
  }

  criar(request: ComunicadoRequest): Observable<ComunicadoResponse> {
    return this.http.post<ComunicadoResponse>(this.adminUrl, request);
  }

  /** Só em RASCUNHO. Depois de publicado use `corrigirTexto`. */
  atualizar(id: string, request: ComunicadoRequest): Observable<ComunicadoResponse> {
    return this.http.put<ComunicadoResponse>(`${this.adminUrl}/${id}`, request);
  }

  /** Único caminho de edição de um comunicado publicado: título, resumo e conteúdo. */
  corrigirTexto(
    id: string,
    request: CorrigirTextoComunicadoRequest
  ): Observable<ComunicadoResponse> {
    return this.http.patch<ComunicadoResponse>(`${this.adminUrl}/${id}/texto`, request);
  }

  publicar(id: string, request: PublicarComunicadoRequest): Observable<ComunicadoResponse> {
    return this.http.post<ComunicadoResponse>(`${this.adminUrl}/${id}/publicacao`, request);
  }

  arquivar(id: string): Observable<ComunicadoResponse> {
    return this.http.patch<ComunicadoResponse>(`${this.adminUrl}/${id}/arquivamento`, null);
  }

  remover(id: string): Observable<void> {
    return this.http.delete<void>(`${this.adminUrl}/${id}`);
  }

  relatorioConfirmacao(id: string): Observable<RelatorioConfirmacaoResponse> {
    return this.http.get<RelatorioConfirmacaoResponse>(`${this.adminUrl}/${id}/confirmacoes`);
  }

  anexar(id: string, arquivo: File): Observable<AnexoComunicadoResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo, arquivo.name);
    return this.http.post<AnexoComunicadoResponse>(`${this.adminUrl}/${id}/anexos`, formData);
  }

  baixarAnexo(id: string, idAnexo: string): Observable<Blob> {
    return this.http.get(`${this.adminUrl}/${id}/anexos/${idAnexo}`, { responseType: 'blob' });
  }

  removerAnexo(id: string, idAnexo: string): Observable<void> {
    return this.http.delete<void>(`${this.adminUrl}/${id}/anexos/${idAnexo}`);
  }

  private params(source: object): HttpParams {
    let params = new HttpParams();
    Object.entries(source).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return params;
  }
}
