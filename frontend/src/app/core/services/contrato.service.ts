import { Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import {
  ContratoResponse,
  ContratoRequest,
  ImportacaoContratoResponse,
  ConfirmarImportacaoContratoRequest,
  AcaoResponse,
  AcaoRequest,
  ImportacaoAcaoResponse,
  ConfirmarImportacaoAcaoRequest,
  AcaoValorResponse,
  AcaoValorRequest,
  ImportacaoAcaoValorResponse,
  ConfirmarImportacaoAcaoValorRequest
} from '../models/contrato.models';
import { PagedResponse } from '../models/shared.models';

@Injectable({
  providedIn: 'root'
})
export class ContratoService extends BaseService {
  private apiUrl = environment.apiUrl;

  private contratosAtivos$?: Observable<PagedResponse<ContratoResponse>>;
  private acoesAtivas$?: Observable<PagedResponse<AcaoResponse>>;

  // Contratos
  listarContratos(params: any): Observable<PagedResponse<ContratoResponse>> {
    return this.getList<ContratoResponse>(`${this.apiUrl}/v1/contratos`, params);
  }

  buscarContratoPorId(id: string): Observable<ContratoResponse> {
    return this.getOne<ContratoResponse>(`${this.apiUrl}/v1/contratos`, id);
  }

  criarContrato(request: ContratoRequest): Observable<ContratoResponse | null> {
    this.contratosAtivos$ = undefined;
    return this.createSync<ContratoResponse>(`${this.apiUrl}/v1/contratos`, request);
  }

  atualizarContrato(id: string, request: ContratoRequest): Observable<ContratoResponse | null> {
    this.contratosAtivos$ = undefined;
    return this.updateSync<ContratoResponse>(`${this.apiUrl}/v1/contratos`, id, request);
  }

  desativarContrato(id: string): Observable<void | null> {
    this.contratosAtivos$ = undefined;
    return this.deleteSync(`${this.apiUrl}/v1/contratos`, id);
  }

  ativarContrato(id: string): Observable<void | null> {
    this.contratosAtivos$ = undefined;
    return this.updateSync<void>(`${this.apiUrl}/v1/contratos`, `${id}/ativar`, {});
  }

  importarContratos(arquivo: File): Observable<ImportacaoContratoResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoContratoResponse>(`${this.apiUrl}/v1/contratos/importar`, formData);
  }

  confirmarImportacaoContratos(request: ConfirmarImportacaoContratoRequest): Observable<ImportacaoContratoResponse> {
    this.contratosAtivos$ = undefined;
    return this.http.post<ImportacaoContratoResponse>(`${this.apiUrl}/v1/contratos/confirmar-importacao`, request);
  }

  listarContratosAtivos(forceRefresh = false): Observable<PagedResponse<ContratoResponse>> {
    if (!this.contratosAtivos$ || forceRefresh) {
      this.contratosAtivos$ = this.getList<ContratoResponse>(`${this.apiUrl}/v1/contratos`, { ativo: true, size: 1000 }).pipe(
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.contratosAtivos$;
  }

  // Ações
  listarAcoes(params: any): Observable<PagedResponse<AcaoResponse>> {
    return this.getList<AcaoResponse>(`${this.apiUrl}/v1/contratos/acoes`, params);
  }

  buscarAcaoPorId(id: string): Observable<AcaoResponse> {
    return this.getOne<AcaoResponse>(`${this.apiUrl}/v1/contratos/acoes`, id);
  }

  criarAcao(request: AcaoRequest): Observable<AcaoResponse | null> {
    this.acoesAtivas$ = undefined;
    return this.createSync<AcaoResponse>(`${this.apiUrl}/v1/contratos/acoes`, request);
  }

  atualizarAcao(id: string, request: AcaoRequest): Observable<AcaoResponse | null> {
    this.acoesAtivas$ = undefined;
    return this.updateSync<AcaoResponse>(`${this.apiUrl}/v1/contratos/acoes`, id, request);
  }

  desativarAcao(id: string): Observable<void | null> {
    this.acoesAtivas$ = undefined;
    return this.deleteSync(`${this.apiUrl}/v1/contratos/acoes`, id);
  }

  ativarAcao(id: string): Observable<void | null> {
    this.acoesAtivas$ = undefined;
    return this.updateSync<void>(`${this.apiUrl}/v1/contratos/acoes`, `${id}/ativar`, {});
  }

  importarAcoes(arquivo: File): Observable<ImportacaoAcaoResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoAcaoResponse>(`${this.apiUrl}/v1/contratos/acoes/importar`, formData);
  }

  confirmarImportacaoAcoes(request: ConfirmarImportacaoAcaoRequest): Observable<ImportacaoAcaoResponse> {
    this.acoesAtivas$ = undefined;
    return this.http.post<ImportacaoAcaoResponse>(`${this.apiUrl}/v1/contratos/acoes/confirmar-importacao`, request);
  }

  listarAcoesAtivas(forceRefresh = false): Observable<PagedResponse<AcaoResponse>> {
    if (!this.acoesAtivas$ || forceRefresh) {
      this.acoesAtivas$ = this.getList<AcaoResponse>(`${this.apiUrl}/v1/contratos/acoes`, { ativo: true, size: 1000 }).pipe(
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.acoesAtivas$;
  }

  // Ações Valores
  listarAcoesValores(params: any): Observable<PagedResponse<AcaoValorResponse>> {
    return this.getList<AcaoValorResponse>(`${this.apiUrl}/v1/contratos/acoes-valores`, params);
  }

  buscarAcaoValorPorId(id: string): Observable<AcaoValorResponse> {
    return this.getOne<AcaoValorResponse>(`${this.apiUrl}/v1/contratos/acoes-valores`, id);
  }

  criarAcaoValor(request: AcaoValorRequest): Observable<AcaoValorResponse | null> {
    return this.createSync<AcaoValorResponse>(`${this.apiUrl}/v1/contratos/acoes-valores`, request);
  }

  atualizarAcaoValor(id: string, request: AcaoValorRequest): Observable<AcaoValorResponse | null> {
    return this.updateSync<AcaoValorResponse>(`${this.apiUrl}/v1/contratos/acoes-valores`, id, request);
  }

  desativarAcaoValor(id: string): Observable<void | null> {
    return this.deleteSync(`${this.apiUrl}/v1/contratos/acoes-valores`, id);
  }

  ativarAcaoValor(id: string): Observable<void | null> {
    return this.updateSync<void>(`${this.apiUrl}/v1/contratos/acoes-valores`, `${id}/ativar`, {});
  }

  importarAcoesValores(arquivo: File): Observable<ImportacaoAcaoValorResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoAcaoValorResponse>(`${this.apiUrl}/v1/contratos/acoes-valores/importar`, formData);
  }

  confirmarImportacaoAcoesValores(request: ConfirmarImportacaoAcaoValorRequest): Observable<ImportacaoAcaoValorResponse> {
    return this.http.post<ImportacaoAcaoValorResponse>(`${this.apiUrl}/v1/contratos/acoes-valores/confirmar-importacao`, request);
  }
}
