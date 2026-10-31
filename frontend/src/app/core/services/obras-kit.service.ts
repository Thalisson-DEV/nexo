import { Injectable } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import {
  CarteiraImportacaoLinha,
  ConfirmarImportacaoPlanilhaRequest,
  KitConstrutivoFilter,
  KitConstrutivoRequest,
  KitConstrutivoResponse,
  KitImportacaoLinha,
  OrcamentoImportacaoLinha,
  PreviaImportacaoObraResponse,
  TipoImportacaoObra
} from '../models/obras-kit.models';
import { PagedResponse } from '../models/shared.models';

/**
 * Kits construtivos (catálogo por contrato) e as importações de planilha de Obras (kits, carteira e
 * orçamento previsto), no fluxo validação → conferência → confirmação.
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
  // Importações (validação com pré-visualização → confirmação processada no servidor)
  // ═══════════════════════════════════════════════════════════════════════

  validarImportacaoKits(idContrato: string, arquivo: File): Observable<PreviaImportacaoObraResponse<KitImportacaoLinha>> {
    const params = new HttpParams().set('idContrato', idContrato);
    return this.http.post<PreviaImportacaoObraResponse<KitImportacaoLinha>>(`${this.kitsUrl}/importacoes/validacao`, this.formulario(arquivo), { params });
  }

  revalidarImportacaoKits(request: ConfirmarImportacaoPlanilhaRequest<KitImportacaoLinha>): Observable<PreviaImportacaoObraResponse<KitImportacaoLinha>> {
    return this.http.post<PreviaImportacaoObraResponse<KitImportacaoLinha>>(`${this.kitsUrl}/importacoes/revalidacao`, request);
  }

  confirmarImportacaoKits(request: ConfirmarImportacaoPlanilhaRequest<KitImportacaoLinha>): Observable<PreviaImportacaoObraResponse<KitImportacaoLinha>> {
    return this.http.post<PreviaImportacaoObraResponse<KitImportacaoLinha>>(`${this.kitsUrl}/importacoes/confirmacao`, request);
  }

  validarImportacaoCarteira(idContrato: string, arquivo: File): Observable<PreviaImportacaoObraResponse<CarteiraImportacaoLinha>> {
    const params = new HttpParams().set('idContrato', idContrato);
    return this.http.post<PreviaImportacaoObraResponse<CarteiraImportacaoLinha>>(`${this.baseUrl}/importacoes/carteira/validacao`, this.formulario(arquivo), { params });
  }

  revalidarImportacaoCarteira(request: ConfirmarImportacaoPlanilhaRequest<CarteiraImportacaoLinha>): Observable<PreviaImportacaoObraResponse<CarteiraImportacaoLinha>> {
    return this.http.post<PreviaImportacaoObraResponse<CarteiraImportacaoLinha>>(`${this.baseUrl}/importacoes/carteira/revalidacao`, request);
  }

  confirmarImportacaoCarteira(request: ConfirmarImportacaoPlanilhaRequest<CarteiraImportacaoLinha>): Observable<PreviaImportacaoObraResponse<CarteiraImportacaoLinha>> {
    return this.http.post<PreviaImportacaoObraResponse<CarteiraImportacaoLinha>>(`${this.baseUrl}/importacoes/carteira/confirmacao`, request);
  }

  validarImportacaoOrcamento(arquivo: File): Observable<PreviaImportacaoObraResponse<OrcamentoImportacaoLinha>> {
    return this.http.post<PreviaImportacaoObraResponse<OrcamentoImportacaoLinha>>(`${this.baseUrl}/importacoes/orcamento/validacao`, this.formulario(arquivo));
  }

  revalidarImportacaoOrcamento(request: ConfirmarImportacaoPlanilhaRequest<OrcamentoImportacaoLinha>): Observable<PreviaImportacaoObraResponse<OrcamentoImportacaoLinha>> {
    return this.http.post<PreviaImportacaoObraResponse<OrcamentoImportacaoLinha>>(`${this.baseUrl}/importacoes/orcamento/revalidacao`, request);
  }

  confirmarImportacaoOrcamento(request: ConfirmarImportacaoPlanilhaRequest<OrcamentoImportacaoLinha>): Observable<PreviaImportacaoObraResponse<OrcamentoImportacaoLinha>> {
    return this.http.post<PreviaImportacaoObraResponse<OrcamentoImportacaoLinha>>(`${this.baseUrl}/importacoes/orcamento/confirmacao`, request);
  }

  /** Baixa o modelo XLSX e entrega o arquivo ao navegador. */
  baixarModelo(tipo: TipoImportacaoObra): Observable<void> {
    const url = tipo === 'KITS' ? `${this.kitsUrl}/importacoes/modelo` : `${this.baseUrl}/importacoes/${tipo.toLowerCase()}/modelo`;
    const nome = `modelo-importacao-${tipo === 'ORCAMENTO' ? 'orcamento' : tipo.toLowerCase()}.xlsx`;
    return this.http.get(url, { responseType: 'blob' }).pipe(
      map((blob) => {
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = nome;
        link.click();
        URL.revokeObjectURL(link.href);
      })
    );
  }

  private formulario(arquivo: File): FormData {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return formData;
  }
}
