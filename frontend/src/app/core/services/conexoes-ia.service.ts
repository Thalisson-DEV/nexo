import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AutorizacaoIaPrevia,
  AutorizacaoIaRedirecionamento,
  AutorizarIaRequest,
  ChaveIaCriada,
  ConexaoIa,
  ConexaoIaInfo,
  CriarChaveIaRequest,
} from '../models/conexoes-ia.models';
import { PagedResponse } from '../models/shared.models';

/** Conexões do Nexo com IAs externas: endereço, chaves de acesso, consentimento e desconexão. */
@Injectable({ providedIn: 'root' })
export class ConexoesIaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/v1/assistente/conexoes`;

  info(): Observable<ConexaoIaInfo> {
    return this.http.get<ConexaoIaInfo>(`${this.url}/info`);
  }

  listar(): Observable<ConexaoIa[]> {
    return this.http.get<ConexaoIa[]>(this.url);
  }

  listarTodas(pagina = 0, tamanho = 200): Observable<PagedResponse<ConexaoIa>> {
    const params = new HttpParams().set('pagina', pagina).set('tamanho', tamanho);
    return this.http.get<PagedResponse<ConexaoIa>>(`${this.url}/todas`, { params });
  }

  criarChave(request: CriarChaveIaRequest): Observable<ChaveIaCriada> {
    return this.http.post<ChaveIaCriada>(`${this.url}/chaves`, request);
  }

  desconectar(id: string): Observable<ConexaoIa> {
    return this.http.post<ConexaoIa>(`${this.url}/${id}/desconectar`, {});
  }

  previa(pedido: AutorizarIaRequest): Observable<AutorizacaoIaPrevia> {
    return this.http.post<AutorizacaoIaPrevia>(`${this.url}/autorizacao/previa`, pedido);
  }

  permitir(pedido: AutorizarIaRequest): Observable<AutorizacaoIaRedirecionamento> {
    return this.http.post<AutorizacaoIaRedirecionamento>(`${this.url}/autorizacao/permitir`, pedido);
  }

  recusar(pedido: AutorizarIaRequest): Observable<AutorizacaoIaRedirecionamento> {
    return this.http.post<AutorizacaoIaRedirecionamento>(`${this.url}/autorizacao/recusar`, pedido);
  }
}
