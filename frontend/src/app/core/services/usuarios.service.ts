import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import { DatabaseService } from './database.service';
import {
  ConfirmarImportacaoUsuarioRequest,
  ImportacaoUsuarioResponse,
  PreferenciasRequest,
  UsuarioRequest,
  UsuarioResponse,
  UsuarioUpdateRequest
} from '../models/iam.models';
import { PerfilAcessoResponse } from '../models/rh.models';
import { PagedResponse } from '../models/shared.models';

@Injectable({
  providedIn: 'root'
})
export class UsuariosService extends BaseService {
  private db = inject(DatabaseService);
  private apiUrl = environment.apiUrl;

  listarUsuarios(params: any): Observable<PagedResponse<UsuarioResponse>> {
    return this.getList<UsuarioResponse>(`${this.apiUrl}/v1/iam/usuarios`, params);
  }

  buscarUsuarioPorId(id: string): Observable<UsuarioResponse> {
    return this.getOne<UsuarioResponse>(`${this.apiUrl}/v1/iam/usuarios`, id);
  }

  criarUsuario(request: UsuarioRequest): Observable<UsuarioResponse | null> {
    return this.createSync<UsuarioResponse>(`${this.apiUrl}/v1/iam/usuarios`, request);
  }

  atualizarUsuario(id: string, request: UsuarioUpdateRequest): Observable<UsuarioResponse | null> {
    return this.updateSync<UsuarioResponse>(`${this.apiUrl}/v1/iam/usuarios`, id, request);
  }

  desativarUsuario(id: string): Observable<void | null> {
    return this.deleteSync(`${this.apiUrl}/v1/iam/usuarios`, id);
  }

  ativarUsuario(id: string): Observable<void | null> {
    return this.updateSync<void>(`${this.apiUrl}/v1/iam/usuarios`, `${id}/ativar`, {});
  }

  atualizarPreferencias(request: PreferenciasRequest): Observable<void | null> {
    return this.http.patch<void>(`${this.apiUrl}/v1/iam/usuarios/me/preferencias`, request);
  }

  /** O endpoint responde paginado; quem chama só quer a lista de perfis. */
  listarPerfisAtivos(): Observable<PerfilAcessoResponse[]> {
    return this.http
      .get<PagedResponse<PerfilAcessoResponse> | PerfilAcessoResponse[]>(
        `${this.apiUrl}/v1/iam/perfis`
      )
      .pipe(map((resposta) => (Array.isArray(resposta) ? resposta : resposta.content ?? [])));
  }

  validarImportacao(arquivo: File): Observable<ImportacaoUsuarioResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);

    return this.http.post<ImportacaoUsuarioResponse>(
      `${this.apiUrl}/v1/iam/usuarios/importar`,
      formData
    );
  }

  confirmarImportacao(
    request: ConfirmarImportacaoUsuarioRequest
  ): Observable<ImportacaoUsuarioResponse> {
    return this.http.post<ImportacaoUsuarioResponse>(
      `${this.apiUrl}/v1/iam/usuarios/confirmar-importacao`,
      request
    );
  }
}
