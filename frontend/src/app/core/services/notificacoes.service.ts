import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  GatilhoResponse,
  NotificacaoFiltros,
  NotificacaoResponse,
  PoliticaRoteamentoRequest,
  PoliticaRoteamentoResponse,
  PreferenciaNotificacaoResponse,
  PreferenciasNotificacaoRequest,
  RegraNotificacaoFiltros,
  RegraNotificacaoRequest,
  RegraNotificacaoResponse,
  ResumoNotificacoesResponse
} from '../models/notificacao.models';
import { PagedResponse } from '../models/shared.models';

/**
 * Caixa de entrada pessoal, preferências e motor de regras (`/v1/notificacoes`).
 *
 * O escopo da caixa de entrada é sempre o próprio usuário, resolvido no servidor — não existe
 * (nem deve ser inventado) parâmetro de "usuário" no filtro.
 */
@Injectable({ providedIn: 'root' })
export class NotificacoesService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/v1/notificacoes`;

  // -------------------------------------------------------------------------
  // Caixa de entrada
  // -------------------------------------------------------------------------

  listar(filtros: NotificacaoFiltros = {}): Observable<PagedResponse<NotificacaoResponse>> {
    return this.http.get<PagedResponse<NotificacaoResponse>>(this.apiUrl, {
      params: this.params(filtros)
    });
  }

  resumo(): Observable<ResumoNotificacoesResponse> {
    return this.http.get<ResumoNotificacoesResponse>(`${this.apiUrl}/resumo`);
  }

  marcarLida(id: string): Observable<NotificacaoResponse> {
    return this.http.patch<NotificacaoResponse>(`${this.apiUrl}/${id}/leitura`, null);
  }

  marcarNaoLida(id: string): Observable<NotificacaoResponse> {
    return this.http.delete<NotificacaoResponse>(`${this.apiUrl}/${id}/leitura`);
  }

  /** Já devolve o resumo atualizado — dispensa uma segunda chamada ao `/resumo`. */
  marcarTodasLidas(): Observable<ResumoNotificacoesResponse> {
    return this.http.patch<ResumoNotificacoesResponse>(`${this.apiUrl}/leitura`, null);
  }

  arquivar(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Já devolve o resumo atualizado. */
  arquivarLidas(): Observable<ResumoNotificacoesResponse> {
    return this.http.delete<ResumoNotificacoesResponse>(`${this.apiUrl}/lidas`);
  }

  // -------------------------------------------------------------------------
  // Preferências
  // -------------------------------------------------------------------------

  /** Devolve todas as categorias existentes, já com o padrão das que o usuário nunca tocou. */
  listarPreferencias(): Observable<PreferenciaNotificacaoResponse[]> {
    return this.http.get<PreferenciaNotificacaoResponse[]>(`${this.apiUrl}/preferencias`);
  }

  /** Atualização parcial: só as categorias enviadas mudam. */
  salvarPreferencias(
    request: PreferenciasNotificacaoRequest
  ): Observable<PreferenciaNotificacaoResponse[]> {
    return this.http.put<PreferenciaNotificacaoResponse[]>(`${this.apiUrl}/preferencias`, request);
  }

  // -------------------------------------------------------------------------
  // Motor de regras
  // -------------------------------------------------------------------------

  /** Catálogo que dirige o formulário de regras. Nunca replicar no front. */
  listarGatilhos(): Observable<GatilhoResponse[]> {
    return this.http.get<GatilhoResponse[]>(`${this.apiUrl}/regras/gatilhos`);
  }

  listarRegras(
    filtros: RegraNotificacaoFiltros = {}
  ): Observable<PagedResponse<RegraNotificacaoResponse>> {
    return this.http.get<PagedResponse<RegraNotificacaoResponse>>(`${this.apiUrl}/regras`, {
      params: this.params(filtros)
    });
  }

  buscarRegra(id: string): Observable<RegraNotificacaoResponse> {
    return this.http.get<RegraNotificacaoResponse>(`${this.apiUrl}/regras/${id}`);
  }

  criarRegra(request: RegraNotificacaoRequest): Observable<RegraNotificacaoResponse> {
    return this.http.post<RegraNotificacaoResponse>(`${this.apiUrl}/regras`, request);
  }

  atualizarRegra(
    id: string,
    request: RegraNotificacaoRequest
  ): Observable<RegraNotificacaoResponse> {
    return this.http.put<RegraNotificacaoResponse>(`${this.apiUrl}/regras/${id}`, request);
  }

  alternarRegra(id: string, ativar: boolean): Observable<RegraNotificacaoResponse> {
    const acao = ativar ? 'ativacao' : 'desativacao';
    return this.http.patch<RegraNotificacaoResponse>(`${this.apiUrl}/regras/${id}/${acao}`, null);
  }

  removerRegra(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/regras/${id}`);
  }

  // -------------------------------------------------------------------------
  // Políticas de roteamento
  // -------------------------------------------------------------------------

  listarPoliticas(): Observable<PoliticaRoteamentoResponse[]> {
    return this.http.get<PoliticaRoteamentoResponse[]>(`${this.apiUrl}/politicas-roteamento`);
  }

  buscarPolitica(id: string): Observable<PoliticaRoteamentoResponse> {
    return this.http.get<PoliticaRoteamentoResponse>(`${this.apiUrl}/politicas-roteamento/${id}`);
  }

  criarPolitica(request: PoliticaRoteamentoRequest): Observable<PoliticaRoteamentoResponse> {
    return this.http.post<PoliticaRoteamentoResponse>(
      `${this.apiUrl}/politicas-roteamento`,
      request
    );
  }

  atualizarPolitica(
    id: string,
    request: PoliticaRoteamentoRequest
  ): Observable<PoliticaRoteamentoResponse> {
    return this.http.put<PoliticaRoteamentoResponse>(
      `${this.apiUrl}/politicas-roteamento/${id}`,
      request
    );
  }

  alternarPolitica(id: string, ativar: boolean): Observable<PoliticaRoteamentoResponse> {
    const acao = ativar ? 'ativacao' : 'desativacao';
    return this.http.patch<PoliticaRoteamentoResponse>(
      `${this.apiUrl}/politicas-roteamento/${id}/${acao}`,
      null
    );
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
