import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PagedResponse } from '../models/shared.models';
import {
  CoordenadorResponse,
  CoordenadorRequest,
  CoordenadorFilter,
  CoordenadorMinResponse,
  ImportacaoCoordenadorResponse,
  ConfirmarImportacaoCoordenadorRequest
} from '../models/rh.models';

@Injectable({
  providedIn: 'root'
})
export class CoordenadorService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/v1/rh/coordenadores`;

  listar(
    filter?: CoordenadorFilter,
    page: number = 0,
    size: number = 20,
    sort?: string
  ): Observable<PagedResponse<CoordenadorResponse>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (sort) params = params.set('sort', sort);
    if (filter) {
      if (filter.nomeColaborador) params = params.set('nomeColaborador', filter.nomeColaborador);
      if (filter.idColaborador) params = params.set('idColaborador', filter.idColaborador);
      if (filter.dataInicio) params = params.set('dataInicio', filter.dataInicio);
      if (filter.dataFim) params = params.set('dataFim', filter.dataFim);
      if (filter.ativo !== undefined && filter.ativo !== null) params = params.set('ativo', filter.ativo.toString());
      if (filter.buscaGlobal) params = params.set('buscaGlobal', filter.buscaGlobal);
    }

    return this.http.get<PagedResponse<CoordenadorResponse>>(this.baseUrl, { params });
  }

  listarAtivosMin(): Observable<CoordenadorMinResponse[]> {
    return this.http.get<CoordenadorMinResponse[]>(`${this.baseUrl}/ativos-min`).pipe(
      catchError(() => this.http.get<CoordenadorMinResponse[]>(`${this.baseUrl}/min`))
    );
  }

  buscarPorId(id: string): Observable<CoordenadorResponse> {
    return this.http.get<CoordenadorResponse>(`${this.baseUrl}/${id}`);
  }

  criar(request: CoordenadorRequest): Observable<CoordenadorResponse> {
    return this.http.post<CoordenadorResponse>(this.baseUrl, request);
  }

  atualizar(id: string, request: CoordenadorRequest): Observable<CoordenadorResponse> {
    return this.http.put<CoordenadorResponse>(`${this.baseUrl}/${id}`, request);
  }

  ativar(id: string): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/${id}/ativar`, {});
  }

  desativar(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  baixarModeloCsv(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/importar/modelo`, { responseType: 'blob' });
  }

  validarImportacao(arquivo: File): Observable<ImportacaoCoordenadorResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoCoordenadorResponse>(`${this.baseUrl}/importar`, formData);
  }

  confirmarImportacao(request: ConfirmarImportacaoCoordenadorRequest): Observable<ImportacaoCoordenadorResponse> {
    return this.http.post<ImportacaoCoordenadorResponse>(`${this.baseUrl}/confirmar-importacao`, request);
  }
}
