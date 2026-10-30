import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  Aprovacao,
  AutomacaoDetalhe,
  AutomacaoRequest,
  AutomacaoResumo,
  Catalogo,
  Definicao,
  ExecucaoDetalhe,
  ExecucaoResumo,
  JsonValue,
  Modelo,
  Opcao,
  Rascunho,
  StatusAutomacao,
  TipoAutomacao,
  Validacao
} from '../models/automacao.models';
import { PagedResponse } from '../models/shared.models';

/**
 * Motor de automações (`/v1/automacoes`): cadastro com versões, verificação, execução,
 * histórico, aprovações e o rascunho feito pelo Copiloto. Tela administrativa e online:
 * não passa pela fila offline.
 */
@Injectable({ providedIn: 'root' })
export class AutomacoesService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/v1/automacoes`;
  private catalogoCache$?: Observable<Catalogo>;

  catalogo(recarregar = false): Observable<Catalogo> {
    if (!this.catalogoCache$ || recarregar) {
      this.catalogoCache$ = this.http.get<Catalogo>(`${this.apiUrl}/catalogo`).pipe(shareReplay(1));
    }
    return this.catalogoCache$;
  }

  modelos(): Observable<Modelo[]> {
    return this.http.get<Modelo[]>(`${this.apiUrl}/modelos`);
  }

  listar(filtro: { busca?: string; tipo?: TipoAutomacao | null; status?: StatusAutomacao | null; page?: number; size?: number }):
    Observable<PagedResponse<AutomacaoResumo>> {
    let params = new HttpParams().set('page', filtro.page ?? 0).set('size', filtro.size ?? 100);
    if (filtro.busca) params = params.set('busca', filtro.busca);
    if (filtro.tipo) params = params.set('tipo', filtro.tipo);
    if (filtro.status) params = params.set('status', filtro.status);
    return this.http.get<PagedResponse<AutomacaoResumo>>(this.apiUrl, { params });
  }

  buscar(id: string): Observable<AutomacaoDetalhe> {
    return this.http.get<AutomacaoDetalhe>(`${this.apiUrl}/${id}`);
  }

  criar(request: AutomacaoRequest): Observable<AutomacaoDetalhe> {
    return this.http.post<AutomacaoDetalhe>(this.apiUrl, request);
  }

  salvar(id: string, request: AutomacaoRequest): Observable<AutomacaoDetalhe> {
    return this.http.put<AutomacaoDetalhe>(`${this.apiUrl}/${id}`, request);
  }

  alterarStatus(id: string, status: 'ATIVA' | 'PAUSADA'): Observable<AutomacaoDetalhe> {
    return this.http.patch<AutomacaoDetalhe>(`${this.apiUrl}/${id}/status`, { status });
  }

  arquivar(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  duplicar(id: string): Observable<AutomacaoDetalhe> {
    return this.http.post<AutomacaoDetalhe>(`${this.apiUrl}/${id}/duplicar`, null);
  }

  transferir(id: string, donoId: string): Observable<AutomacaoDetalhe> {
    return this.http.patch<AutomacaoDetalhe>(`${this.apiUrl}/${id}/responsavel`, { donoId });
  }

  versao(id: string, numero: number): Observable<Definicao> {
    return this.http.get<Definicao>(`${this.apiUrl}/${id}/versoes/${numero}`);
  }

  validar(definicao: Definicao, tipo: TipoAutomacao | null): Observable<Validacao> {
    return this.http.post<Validacao>(`${this.apiUrl}/validar`, { definicao, tipo });
  }

  /** O e-mail como vai chegar: o layout institucional real, montado no servidor. */
  previaEmail(params: Record<string, JsonValue>, rotulos: Record<string, string>): Observable<{ assunto: string; html: string }> {
    return this.http.post<{ assunto: string; html: string }>(`${this.apiUrl}/previa-email`, { params, rotulos });
  }

  executar(id: string, inputs: Record<string, JsonValue>, teste: boolean): Observable<ExecucaoResumo> {
    return this.http.post<ExecucaoResumo>(`${this.apiUrl}/${id}/executar`, { inputs, teste });
  }

  execucoes(id: string, page = 0, size = 30): Observable<PagedResponse<ExecucaoResumo>> {
    return this.http.get<PagedResponse<ExecucaoResumo>>(`${this.apiUrl}/${id}/execucoes`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  execucao(id: string, execucaoId: string): Observable<ExecucaoDetalhe> {
    return this.http.get<ExecucaoDetalhe>(`${this.apiUrl}/${id}/execucoes/${execucaoId}`);
  }

  cancelar(id: string, execucaoId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/execucoes/${execucaoId}/cancelar`, null);
  }

  reenviar(id: string, execucaoId: string): Observable<ExecucaoResumo> {
    return this.http.post<ExecucaoResumo>(`${this.apiUrl}/${id}/execucoes/${execucaoId}/reenviar`, null);
  }

  urlArquivo(arquivoId: string): string {
    return `${this.apiUrl}/arquivos/${arquivoId}`;
  }

  baixarArquivo(arquivoId: string): Observable<Blob> {
    return this.http.get(this.urlArquivo(arquivoId), { responseType: 'blob' });
  }

  rascunho(pedido: string, automacaoId?: string | null): Observable<Rascunho> {
    return this.http.post<Rascunho>(`${this.apiUrl}/rascunho`, { pedido, automacaoId: automacaoId ?? null });
  }

  opcoes(tipo: string, busca: string): Observable<Opcao[]> {
    return this.http.get<Opcao[]>(`${this.apiUrl}/opcoes/${tipo}`, { params: new HttpParams().set('busca', busca) });
  }

  rotulos(tipo: string, valores: string[]): Observable<Opcao[]> {
    let params = new HttpParams();
    for (const v of valores) params = params.append('valores', v);
    return this.http.get<Opcao[]>(`${this.apiUrl}/rotulos/${tipo}`, { params });
  }

  minhasAprovacoes(): Observable<Aprovacao[]> {
    return this.http.get<Aprovacao[]>(`${this.apiUrl}/aprovacoes/minhas`);
  }

  aprovacao(id: string): Observable<Aprovacao> {
    return this.http.get<Aprovacao>(`${this.apiUrl}/aprovacoes/${id}`);
  }

  responder(id: string, opcao: string, comentario: string): Observable<Aprovacao> {
    return this.http.post<Aprovacao>(`${this.apiUrl}/aprovacoes/${id}/resposta`, { opcao, comentario });
  }
}
