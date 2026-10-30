import { Injectable } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import {
  HistoricoImportacaoObraResponse,
  KitConstrutivoFilter,
  KitConstrutivoRequest,
  KitConstrutivoResponse,
  TipoImportacaoObra
} from '../models/obras-kit.models';
import { PagedResponse } from '../models/shared.models';

/**
 * Kits construtivos (catálogo por contrato) e o histórico das importações assíncronas de Obras
 * (kits, carteira e orçamento previsto).
 */
@Injectable({
  providedIn: 'root'
})
export class ObrasKitService extends BaseService {
  private readonly baseUrl = `${environment.apiUrl}/v1/obras`;
  private readonly kitsUrl = `${this.baseUrl}/kits`;

  listarKits(filter: KitConstrutivoFilter = {}): Observable<PagedResponse<KitConstrutivoResponse>> {
    return this.getList<KitConstrutivoResponse>(this.kitsUrl, filter);
  }

  buscarKit(id: string): Observable<KitConstrutivoResponse> {
    return this.getOne<KitConstrutivoResponse>(this.kitsUrl, id);
  }

  criarKit(request: KitConstrutivoRequest): Observable<KitConstrutivoResponse> {
    return this.http.post<KitConstrutivoResponse>(this.kitsUrl, request);
  }

  atualizarKit(id: string, request: KitConstrutivoRequest): Observable<KitConstrutivoResponse> {
    return this.http.put<KitConstrutivoResponse>(`${this.kitsUrl}/${id}`, request);
  }

  desativarKit(id: string): Observable<void> {
    return this.http.delete<void>(`${this.kitsUrl}/${id}`);
  }

  listarGrupos(idContrato?: string | null): Observable<string[]> {
    const params = idContrato ? new HttpParams().set('idContrato', idContrato) : undefined;
    return this.http.get<string[]>(`${this.kitsUrl}/grupos`, { params });
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Importações assíncronas (kits, carteira, orçamento previsto)
  // ═══════════════════════════════════════════════════════════════════════

  importarKits(idContrato: string, arquivo: File): Observable<HistoricoImportacaoObraResponse> {
    const params = new HttpParams().set('idContrato', idContrato);
    return this.http.post<HistoricoImportacaoObraResponse>(`${this.kitsUrl}/importacoes`, this.formulario(arquivo), { params });
  }

  importarCarteira(idContrato: string, arquivo: File): Observable<HistoricoImportacaoObraResponse> {
    const params = new HttpParams().set('idContrato', idContrato);
    return this.http.post<HistoricoImportacaoObraResponse>(`${this.baseUrl}/importacoes/carteira`, this.formulario(arquivo), { params });
  }

  importarOrcamento(arquivo: File): Observable<HistoricoImportacaoObraResponse> {
    return this.http.post<HistoricoImportacaoObraResponse>(`${this.baseUrl}/importacoes/orcamento`, this.formulario(arquivo));
  }

  baixarModelo(tipo: TipoImportacaoObra): Observable<Blob> {
    const url = tipo === 'KITS' ? `${this.kitsUrl}/importacoes/modelo` : `${this.baseUrl}/importacoes/${tipo.toLowerCase()}/modelo`;
    return this.http.get(url, { responseType: 'blob' });
  }

  listarImportacoes(tipo: TipoImportacaoObra, page = 0, size = 10): Observable<PagedResponse<HistoricoImportacaoObraResponse>> {
    const params = new HttpParams().set('tipo', tipo).set('page', page).set('size', size);
    return this.http.get<PagedResponse<HistoricoImportacaoObraResponse>>(`${this.baseUrl}/importacoes`, { params });
  }

  buscarImportacao(id: string): Observable<HistoricoImportacaoObraResponse> {
    return this.http.get<HistoricoImportacaoObraResponse>(`${this.baseUrl}/importacoes/${id}`);
  }

  private formulario(arquivo: File): FormData {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return formData;
  }
}
