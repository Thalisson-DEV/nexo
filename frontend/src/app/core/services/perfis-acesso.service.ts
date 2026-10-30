import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import {
  PerfilAcessoRequest,
  PerfilAcessoResponse,
  ImportacaoPerfilAcessoResponse,
  ConfirmarImportacaoPerfilRequest,
  PermissaoResponse
} from '../models/iam.models';
import { PagedResponse } from '../models/shared.models';

@Injectable({
  providedIn: 'root'
})
export class PerfisAcessoService extends BaseService {
  private apiUrl = environment.apiUrl;

  listarPerfis(params: any): Observable<PagedResponse<PerfilAcessoResponse>> {
    return this.getList<PerfilAcessoResponse>(`${this.apiUrl}/v1/iam/perfis`, params);
  }

  buscarPerfilPorId(id: string): Observable<PerfilAcessoResponse> {
    return this.getOne<PerfilAcessoResponse>(`${this.apiUrl}/v1/iam/perfis`, id);
  }

  criarPerfil(request: PerfilAcessoRequest): Observable<PerfilAcessoResponse | null> {
    return this.createSync<PerfilAcessoResponse>(`${this.apiUrl}/v1/iam/perfis`, request);
  }

  atualizarPerfil(id: string, request: PerfilAcessoRequest): Observable<PerfilAcessoResponse | null> {
    return this.updateSync<PerfilAcessoResponse>(`${this.apiUrl}/v1/iam/perfis`, id, request);
  }

  desativarPerfil(id: string): Observable<void | null> {
    return this.deleteSync(`${this.apiUrl}/v1/iam/perfis`, id);
  }

  ativarPerfil(id: string): Observable<void | null> {
    return this.updateSync<void>(`${this.apiUrl}/v1/iam/perfis`, `${id}/ativar`, {});
  }

  // Permissões
  listarTodasPermissoes(params?: any): Observable<PagedResponse<PermissaoResponse>> {
    return this.getList<PermissaoResponse>(`${this.apiUrl}/v1/iam/permissoes`, { ...params, size: 1000 });
  }

  listarPermissoesPorPerfil(idPerfil: string): Observable<PermissaoResponse[]> {
    return this.http.get<PermissaoResponse[]>(`${this.apiUrl}/v1/iam/perfis/${idPerfil}/permissoes`);
  }

  /**
   * Grava todas as permissões do perfil numa chamada só (o que não vier é removido). Substitui o
   * vincular/remover por permissão, que em lote disputava a versão do perfil no servidor.
   */
  definirPermissoes(idPerfil: string, idsPermissoes: readonly string[]): Observable<PermissaoResponse[]> {
    return this.http.put<PermissaoResponse[]>(`${this.apiUrl}/v1/iam/perfis/${idPerfil}/permissoes`, { idsPermissoes });
  }

  vincularPermissao(idPerfil: string, idPermissao: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/v1/iam/perfis/${idPerfil}/permissoes/${idPermissao}`, {});
  }

  removerPermissao(idPerfil: string, idPermissao: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/v1/iam/perfis/${idPerfil}/permissoes/${idPermissao}`);
  }

  importarPerfis(arquivo: File): Observable<ImportacaoPerfilAcessoResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoPerfilAcessoResponse>(`${this.apiUrl}/v1/iam/perfis/importar`, formData);
  }

  confirmarImportacao(request: ConfirmarImportacaoPerfilRequest): Observable<ImportacaoPerfilAcessoResponse> {
    return this.http.post<ImportacaoPerfilAcessoResponse>(`${this.apiUrl}/v1/iam/perfis/importar/confirmar`, request);
  }
}
