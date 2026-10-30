import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { HttpParams } from '@angular/common/http';
import { BaseService } from './base.service';
import { PagedResponse } from '../models/shared.models';
import { HEADER_IDEMPOTENCIA } from '../utils/idempotencia.util';
import { toHttpParams } from '../utils/http-params.util';
import {
  DashboardDescontosResponse,
  DescontoDashboardFilter
} from '../models/desconto-dashboard.models';
import {
  MotivoDescontoRequest,
  MotivoDescontoResponse,
  TipoEquipamentoRequest,
  TipoEquipamentoResponse,
  DescontoRequest,
  DescontoItemRequest,
  DescontoResponse,
  DescontoValidacaoRequest,
  DescontoBaixaRequest,
  DescontoRejeicaoRequest
} from '../models/desconto.models';

@Injectable({ providedIn: 'root' })
export class RhDescontosService extends BaseService {
  private apiUrl = environment.apiUrl;

  // Tipos de Equipamento
  listarTiposEquipamento(params: any): Observable<PagedResponse<TipoEquipamentoResponse>> {
    return this.getList<TipoEquipamentoResponse>(`${this.apiUrl}/v1/descontos/tipos-equipamento`, params);
  }

  buscarTipoEquipamentoPorId(id: string): Observable<TipoEquipamentoResponse> {
    return this.getOne<TipoEquipamentoResponse>(`${this.apiUrl}/v1/descontos/tipos-equipamento`, id);
  }

  criarTipoEquipamento(request: TipoEquipamentoRequest): Observable<TipoEquipamentoResponse | null> {
    return this.createSync<TipoEquipamentoResponse>(`${this.apiUrl}/v1/descontos/tipos-equipamento`, request);
  }

  atualizarTipoEquipamento(id: string, request: TipoEquipamentoRequest): Observable<TipoEquipamentoResponse | null> {
    return this.updateSync<TipoEquipamentoResponse>(`${this.apiUrl}/v1/descontos/tipos-equipamento`, id, request);
  }

  desativarTipoEquipamento(id: string): Observable<void | null> {
    return this.deleteSync(`${this.apiUrl}/v1/descontos/tipos-equipamento`, id);
  }
  
  ativarTipoEquipamento(id: string): Observable<void | null> {
    return this.updateSync<void>(`${this.apiUrl}/v1/descontos/tipos-equipamento`, `${id}/ativar`, {});
  }

  // Motivos de Desconto
  listarMotivos(params: any): Observable<PagedResponse<MotivoDescontoResponse>> {
    return this.getList<MotivoDescontoResponse>(`${this.apiUrl}/v1/descontos/motivos`, params);
  }

  buscarMotivoPorId(id: string): Observable<MotivoDescontoResponse> {
    return this.getOne<MotivoDescontoResponse>(`${this.apiUrl}/v1/descontos/motivos`, id);
  }

  criarMotivo(request: MotivoDescontoRequest): Observable<MotivoDescontoResponse | null> {
    return this.createSync<MotivoDescontoResponse>(`${this.apiUrl}/v1/descontos/motivos`, request);
  }

  atualizarMotivo(id: string, request: MotivoDescontoRequest): Observable<MotivoDescontoResponse | null> {
    return this.updateSync<MotivoDescontoResponse>(`${this.apiUrl}/v1/descontos/motivos`, id, request);
  }

  desativarMotivo(id: string): Observable<void | null> {
    return this.deleteSync(`${this.apiUrl}/v1/descontos/motivos`, id);
  }
  
  ativarMotivo(id: string): Observable<void | null> {
    return this.updateSync<void>(`${this.apiUrl}/v1/descontos/motivos`, `${id}/ativar`, {});
  }

  // ----------------------------------------------------
  // Descontos em Folha
  // ----------------------------------------------------

  buscarDescontos(params: any): Observable<PagedResponse<DescontoResponse>> {
    return this.getList<DescontoResponse>(`${this.apiUrl}/v1/descontos`, params);
  }

  obterDescontoPorId(id: string): Observable<DescontoResponse> {
    return this.getOne<DescontoResponse>(`${this.apiUrl}/v1/descontos`, id);
  }

  /**
   * @param chaveIdempotencia chave ESTÁVEL da tentativa de salvamento. O formulário a
   * mantém entre retentativas para que apertar "Salvar" de novo depois de um erro no meio
   * do fluxo não abra um segundo desconto — foi assim que um lançamento virou três.
   */
  iniciarSolicitacao(request: DescontoRequest, chaveIdempotencia?: string): Observable<DescontoResponse | null> {
    return this.createSync<DescontoResponse>(`${this.apiUrl}/v1/descontos`, request, undefined, chaveIdempotencia);
  }

  atualizarDesconto(id: string, request: Partial<DescontoRequest>): Observable<DescontoResponse | null> {
    return this.updateSync<DescontoResponse>(`${this.apiUrl}/v1/descontos`, id, request);
  }

  /** Transição para VALIDADO. O `valorValidado` é obrigatório e pode divergir do sugerido. */
  validarDesconto(id: string, request: DescontoValidacaoRequest): Observable<DescontoResponse | null> {
    return this.patchSync<DescontoResponse>(`${this.apiUrl}/v1/descontos`, `${id}/validar`, request);
  }

  /** Transição para BAIXADO. O `valorBaixado` é obrigatório e é o valor que vale no fim. */
  baixarDesconto(id: string, request: DescontoBaixaRequest): Observable<DescontoResponse | null> {
    return this.patchSync<DescontoResponse>(`${this.apiUrl}/v1/descontos`, `${id}/baixar`, request);
  }

  rejeitarDesconto(id: string, request: DescontoRejeicaoRequest): Observable<DescontoResponse | null> {
    return this.patchSync<DescontoResponse>(`${this.apiUrl}/v1/descontos`, `${id}/rejeitar`, request);
  }

  /** @param chaveIdempotencia estável por item; ver {@link iniciarSolicitacao}. */
  adicionarItem(
    descontoId: string,
    item: DescontoItemRequest,
    chaveIdempotencia?: string
  ): Observable<DescontoResponse | null> {
    return this.createSync<DescontoResponse>(
      `${this.apiUrl}/v1/descontos/${descontoId}/itens`,
      item,
      undefined,
      chaveIdempotencia
    );
  }

  atualizarItem(descontoId: string, itemId: string, item: DescontoItemRequest): Observable<DescontoResponse | null> {
    return this.updateSync<DescontoResponse>(`${this.apiUrl}/v1/descontos/${descontoId}/itens`, itemId, item);
  }

  removerItem(descontoId: string, itemId: string): Observable<void | null> {
    return this.deleteSync(`${this.apiUrl}/v1/descontos/${descontoId}/itens`, itemId);
  }

  /**
   * Upload direto (sem passar pela `syncQueue`, que só serializa JSON).
   *
   * @param chaveIdempotencia estável por anexo; ver {@link iniciarSolicitacao}. Sem ela,
   * um upload que chegou ao servidor mas cuja resposta se perdeu na volta seria anexado
   * de novo a cada retentativa.
   */
  enviarAnexo(descontoId: string, file: File, chaveIdempotencia?: string): Observable<DescontoResponse | null> {
    const formData = new FormData();
    formData.append('arquivo', file);
    return this.http.post<DescontoResponse>(
      `${this.apiUrl}/v1/descontos/${descontoId}/anexos`,
      formData,
      chaveIdempotencia ? { headers: { [HEADER_IDEMPOTENCIA]: chaveIdempotencia } } : {}
    );
  }

  removerAnexo(descontoId: string, anexoId: string): Observable<void | null> {
    return this.deleteSync(`${this.apiUrl}/v1/descontos/${descontoId}/anexos`, anexoId);
  }

  baixarAnexoArquivo(descontoId: string, anexoId: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/v1/descontos/${descontoId}/anexos/${anexoId}/download`, { responseType: 'blob' });
  }

  // ----------------------------------------------------
  // Dashboard executivo
  // ----------------------------------------------------

  /**
   * Não passa pelo `BaseService`: o dashboard é uma leitura agregada de um período,
   * e servir uma versão em cache dele depois de uma queda de rede daria ao gestor um
   * número desatualizado com a mesma cara de um número atual.
   */
  gerarDashboard(filter: DescontoDashboardFilter): Observable<DashboardDescontosResponse> {
    return this.http.get<DashboardDescontosResponse>(`${this.apiUrl}/v1/descontos/dashboards/executivo`, {
      params: toHttpParams(filter as Record<string, unknown>)
    });
  }

  // ----------------------------------------------------
  // Exportação (CSV)
  // ----------------------------------------------------
  exportarDetalhado(filtro: any): Observable<Blob> {
    const params = this.buildExportParams(filtro);
    return this.http.get(`${this.apiUrl}/v1/descontos/exportar/detalhado`, {
      params,
      responseType: 'blob'
    });
  }

  exportarResumo(filtro: any): Observable<Blob> {
    const params = this.buildExportParams(filtro);
    return this.http.get(`${this.apiUrl}/v1/descontos/exportar/resumo`, {
      params,
      responseType: 'blob'
    });
  }

  private buildExportParams(filtro: any): HttpParams {
    let params = new HttpParams();
    Object.keys(filtro).forEach((key) => {
      const value = filtro[key];
      // Ignora paginação
      if (key === 'page' || key === 'size' || key === 'sort') return;
      
      if (value !== null && value !== undefined && value !== '') {
        if (key === 'dataCriacaoInicio' || key === 'dataCriacaoFim') {
          // Garante formato ISO 8601 completo (com timezone Z ou offset)
          params = params.set(key, new Date(value).toISOString());
        } else if (Array.isArray(value)) {
          params = params.set(key, value.join(','));
        } else {
          params = params.set(key, String(value));
        }
      }
    });
    return params;
  }
}
