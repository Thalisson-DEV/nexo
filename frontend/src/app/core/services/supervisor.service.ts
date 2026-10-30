import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PagedResponse } from '../models/shared.models';
import {
  SupervisorResponse,
  SupervisorRequest,
  SupervisorFilter,
  SupervisorMinResponse,
  ImportacaoSupervisorResponse,
  ConfirmarImportacaoSupervisorRequest
} from '../models/rh.models';

@Injectable({
  providedIn: 'root'
})
export class SupervisorService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/v1/rh/supervisores`;

  listar(
    filter?: SupervisorFilter,
    page: number = 0,
    size: number = 20,
    sort?: string
  ): Observable<PagedResponse<SupervisorResponse>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (sort) params = params.set('sort', sort);
    if (filter) {
      if (filter.nomeColaborador) params = params.set('nomeColaborador', filter.nomeColaborador);
      if (filter.idColaborador) params = params.set('idColaborador', filter.idColaborador);
      if (filter.nomeCoordenador) params = params.set('nomeCoordenador', filter.nomeCoordenador);
      if (filter.idCoordenador) params = params.set('idCoordenador', filter.idCoordenador);
      if (filter.dataInicio) params = params.set('dataInicio', filter.dataInicio);
      if (filter.dataFim) params = params.set('dataFim', filter.dataFim);
      if (filter.ativo !== undefined && filter.ativo !== null) params = params.set('ativo', filter.ativo.toString());
      if (filter.buscaGlobal) params = params.set('buscaGlobal', filter.buscaGlobal);
    }

    return this.http.get<PagedResponse<SupervisorResponse>>(this.baseUrl, { params });
  }

  listarAtivosMin(): Observable<SupervisorMinResponse[]> {
    return this.http.get<SupervisorMinResponse[]>(`${this.baseUrl}/ativos-min`).pipe(
      catchError(() => this.http.get<SupervisorMinResponse[]>(`${this.baseUrl}/min`))
    );
  }

  buscarPorId(id: string): Observable<SupervisorResponse> {
    return this.http.get<SupervisorResponse>(`${this.baseUrl}/${id}`);
  }

  criar(request: SupervisorRequest): Observable<SupervisorResponse> {
    return this.http.post<SupervisorResponse>(this.baseUrl, request);
  }

  atualizar(id: string, request: SupervisorRequest): Observable<SupervisorResponse> {
    return this.http.put<SupervisorResponse>(`${this.baseUrl}/${id}`, request);
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

  validarImportacao(arquivo: File): Observable<ImportacaoSupervisorResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoSupervisorResponse>(`${this.baseUrl}/importar`, formData);
  }

  confirmarImportacao(request: ConfirmarImportacaoSupervisorRequest): Observable<ImportacaoSupervisorResponse> {
    return this.http.post<ImportacaoSupervisorResponse>(`${this.baseUrl}/confirmar-importacao`, request);
  }
}
