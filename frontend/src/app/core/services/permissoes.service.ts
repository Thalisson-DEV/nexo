import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import {
  PermissaoRequest,
  PermissaoResponse
} from '../models/iam.models';
import { PagedResponse } from '../models/shared.models';

@Injectable({
  providedIn: 'root'
})
export class PermissoesService extends BaseService {
  private apiUrl = `${environment.apiUrl}/v1/iam/permissoes`;

  listar(params: any): Observable<PagedResponse<PermissaoResponse>> {
    return this.getList<PermissaoResponse>(this.apiUrl, params);
  }

  buscarPorId(id: string): Observable<PermissaoResponse> {
    return this.getOne<PermissaoResponse>(this.apiUrl, id);
  }

  criar(request: PermissaoRequest): Observable<PermissaoResponse | null> {
    return this.createSync<PermissaoResponse>(this.apiUrl, request);
  }

  atualizar(id: string, request: PermissaoRequest): Observable<PermissaoResponse | null> {
    return this.updateSync<PermissaoResponse>(this.apiUrl, id, request);
  }

  alterarStatus(id: string, ativo: boolean): Observable<void | null> {
    return this.http.patch<void>(`${this.apiUrl}/${id}/status`, null, { params: { ativo } });
  }
}
