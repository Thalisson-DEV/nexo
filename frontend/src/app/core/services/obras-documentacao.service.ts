import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DocumentacaoObra,
  DocumentoAsBuilt,
  EquipamentoRequest,
  ModeloChecklistFilter,
  ModeloChecklistRequest,
  ModeloChecklistResponse,
  RespostaInformada,
  VisitaPreviaRequest
} from '../models/obras-documentacao.models';
import { PagedResponse } from '../models/shared.models';
import { BaseService } from './base.service';

/** Modelos de checklist e documentação técnica da obra: visita prévia, fichas As Built, fotos do book e equipamentos. */
@Injectable({ providedIn: 'root' })
export class ObrasDocumentacaoService extends BaseService {
  private readonly obrasUrl = `${environment.apiUrl}/v1/obras`;
  private readonly modelosUrl = `${this.obrasUrl}/checklists`;

  listarModelos(filter: ModeloChecklistFilter = {}): Observable<PagedResponse<ModeloChecklistResponse>> {
    return this.getList<ModeloChecklistResponse>(this.modelosUrl, filter);
  }

  buscarModelo(id: string): Observable<ModeloChecklistResponse> {
    return this.http.get<ModeloChecklistResponse>(`${this.modelosUrl}/${id}`);
  }

  criarModelo(request: ModeloChecklistRequest): Observable<ModeloChecklistResponse> {
    return this.http.post<ModeloChecklistResponse>(this.modelosUrl, request);
  }

  atualizarModelo(id: string, request: ModeloChecklistRequest): Observable<ModeloChecklistResponse> {
    return this.http.put<ModeloChecklistResponse>(`${this.modelosUrl}/${id}`, request);
  }

  duplicarModelo(id: string): Observable<ModeloChecklistResponse> {
    return this.http.post<ModeloChecklistResponse>(`${this.modelosUrl}/${id}/duplicacao`, {});
  }

  desativarModelo(id: string): Observable<void> {
    return this.http.delete<void>(`${this.modelosUrl}/${id}`);
  }

  ativarModelo(id: string): Observable<void> {
    return this.http.put<void>(`${this.modelosUrl}/${id}/ativar`, {});
  }

  consultar(idObra: string): Observable<DocumentacaoObra> {
    return this.http.get<DocumentacaoObra>(this.url(idObra));
  }

  salvarVisita(idObra: string, idVisita: string | null, request: VisitaPreviaRequest): Observable<unknown> {
    return idVisita
      ? this.http.put(`${this.url(idObra)}/visitas/${idVisita}`, request)
      : this.http.post(`${this.url(idObra)}/visitas`, request);
  }

  removerVisita(idObra: string, idVisita: string): Observable<void> {
    return this.http.delete<void>(`${this.url(idObra)}/visitas/${idVisita}`);
  }

  baixarPdfDaVisita(idObra: string, idVisita: string): Observable<Blob> {
    return this.http.get(`${this.url(idObra)}/visitas/${idVisita}/pdf`, { responseType: 'blob' });
  }

  preencherFicha(idObra: string, idPonto: string, idModelo: string, respostas: RespostaInformada[]): Observable<void> {
    return this.http.put<void>(`${this.url(idObra)}/fichas/${idPonto}`, { idModelo, respostas });
  }

  enviarEvidencia(idObra: string, idPonto: string, arquivo: File, item: { idModelo: string; idItem: string } | null): Observable<string> {
    const corpo = new FormData();
    corpo.append('arquivo', arquivo);
    if (item) {
      corpo.append('idModelo', item.idModelo);
      corpo.append('idItem', item.idItem);
    }
    return this.http.post<string>(`${this.url(idObra)}/pontos/${idPonto}/evidencias`, corpo);
  }

  alterarLegenda(idObra: string, idEvidencia: string, legenda: string | null): Observable<void> {
    return this.http.put<void>(`${this.url(idObra)}/evidencias/${idEvidencia}/legenda`, { legenda });
  }

  removerEvidencia(idObra: string, idEvidencia: string): Observable<void> {
    return this.http.delete<void>(`${this.url(idObra)}/evidencias/${idEvidencia}`);
  }

  baixarMiniatura(idObra: string, idEvidencia: string): Observable<Blob> {
    return this.http.get(`${this.url(idObra)}/evidencias/${idEvidencia}/miniatura`, { responseType: 'blob' });
  }

  baixarFoto(idObra: string, idEvidencia: string): Observable<Blob> {
    return this.http.get(`${this.url(idObra)}/evidencias/${idEvidencia}/arquivo`, { responseType: 'blob' });
  }

  gerarAsBuilt(idObra: string): Observable<DocumentoAsBuilt> {
    return this.http.post<DocumentoAsBuilt>(`${this.url(idObra)}/as-built`, {});
  }

  definirEquipamento(idObra: string, idPonto: string, request: EquipamentoRequest): Observable<void> {
    return this.http.put<void>(`${this.url(idObra)}/equipamentos/${idPonto}`, request);
  }

  removerEquipamento(idObra: string, idPonto: string): Observable<void> {
    return this.http.delete<void>(`${this.url(idObra)}/equipamentos/${idPonto}`);
  }

  private url(idObra: string): string {
    return `${this.obrasUrl}/${idObra}/documentacao`;
  }
}
