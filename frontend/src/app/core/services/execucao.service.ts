import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import {
  AlterarExecucaoRequest,
  EventoHistoricoNota,
  ExecucaoComercialResponse,
  ExecucaoFilters,
  TipoServicoFilters,
  TipoServicoRequest,
  TipoServicoResponse
} from '../models/execucao.models';
import { PagedResponse } from '../models/shared.models';

@Injectable({
  providedIn: 'root'
})
export class ExecucaoService extends BaseService {
  private apiUrl = `${environment.apiUrl}/v1/execucoes-comerciais`;
  private tiposServicoUrl = `${environment.apiUrl}/v1/tipos-servico`;

  listarExecucoes(params: ExecucaoFilters): Observable<PagedResponse<ExecucaoComercialResponse>> {
    return this.getList<ExecucaoComercialResponse>(this.apiUrl, params);
  }

  buscarPorId(id: string): Observable<ExecucaoComercialResponse> {
    return this.getOne<ExecucaoComercialResponse>(this.apiUrl, id);
  }

  /** Troca o número provisório da nota avulsa pelo número oficial. */
  informarNumeroOficial(id: string, referenciaNota: string): Observable<ExecucaoComercialResponse> {
    return this.http.patch<ExecucaoComercialResponse>(`${this.apiUrl}/${id}/numero-oficial`, { referenciaNota });
  }

  /** Corrige serviços e materiais de uma nota já sincronizada; o saldo da equipe é ajustado no servidor. */
  alterarLancamentos(id: string, request: AlterarExecucaoRequest): Observable<ExecucaoComercialResponse> {
    return this.http.put<ExecucaoComercialResponse>(`${this.apiUrl}/${id}/lancamentos`, request);
  }

  /** Linha do tempo da nota: lançamento, alterações e número oficial. */
  historico(id: string): Observable<EventoHistoricoNota[]> {
    return this.http.get<EventoHistoricoNota[]>(`${this.apiUrl}/${id}/historico`);
  }

  listarMinhaEquipe(params: ExecucaoFilters): Observable<PagedResponse<ExecucaoComercialResponse>> {
    return this.getList<ExecucaoComercialResponse>(`${this.apiUrl}/minha-equipe`, params);
  }

  // Tipos de serviço
  listarTiposServico(params: TipoServicoFilters): Observable<PagedResponse<TipoServicoResponse>> {
    return this.getList<TipoServicoResponse>(this.tiposServicoUrl, params as Record<string, unknown>);
  }

  /** Tipos ativos, sem paginação — abastece filtros e seletores. */
  listarOpcoesTiposServico(): Observable<TipoServicoResponse[]> {
    return this.http.get<TipoServicoResponse[]>(`${this.tiposServicoUrl}/opcoes`);
  }

  criarTipoServico(request: TipoServicoRequest): Observable<TipoServicoResponse> {
    return this.http.post<TipoServicoResponse>(this.tiposServicoUrl, request);
  }

  atualizarTipoServico(id: string, request: TipoServicoRequest): Observable<TipoServicoResponse> {
    return this.http.put<TipoServicoResponse>(`${this.tiposServicoUrl}/${id}`, request);
  }

  /** Ativa ou desativa, conforme o estado atual. */
  alternarStatusTipoServico(id: string): Observable<void> {
    return this.http.patch<void>(`${this.tiposServicoUrl}/${id}/status`, {});
  }
}
