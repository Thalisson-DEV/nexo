import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AnexoDesvioResponse,
  ContextoAnexo,
  DesvioSegurancaResponse,
  EvidenciaInspecaoResponse,
  FormularioInspecao,
  FormularioInspecaoResumo,
  InspecaoSegurancaFilter,
  InspecaoSegurancaResponse,
  PageResponse,
  RegistrarInspecaoRequest,
  TipoInspecao,
  TratarDesvioRequest,
  sanitizarRegistrarInspecao,
} from './inspecao.models';

@Injectable({ providedIn: 'root' })
export class InspecaoService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/sst`;

  listarTiposInspecao(): Observable<TipoInspecao[]> {
    return this.http.get<TipoInspecao[]>(`${this.baseUrl}/tipos-inspecao/opcoes`);
  }

  listarFormulariosAtivos(page = 0, size = 50): Observable<PageResponse<FormularioInspecaoResumo>> {
    return this.http.get<PageResponse<FormularioInspecaoResumo>>(
      `${this.baseUrl}/formularios-inspecao`,
      { params: { ativo: true, page, size, sort: 'titulo,asc' } }
    );
  }

  obterFormulario(idFormulario: string): Observable<FormularioInspecao> {
    return this.http.get<FormularioInspecao>(
      `${this.baseUrl}/formularios-inspecao/${encodeURIComponent(idFormulario)}`
    );
  }

  registrar(request: RegistrarInspecaoRequest): Observable<InspecaoSegurancaResponse> {
    return this.http.post<InspecaoSegurancaResponse>(
      `${this.baseUrl}/inspecoes`, sanitizarRegistrarInspecao(request)
    );
  }

  listarMinhas(filter: InspecaoSegurancaFilter = {}): Observable<PageResponse<InspecaoSegurancaResponse>> {
    return this.http.get<PageResponse<InspecaoSegurancaResponse>>(
      `${this.baseUrl}/inspecoes/minhas`, { params: this.params(filter) }
    );
  }

  obterInspecao(idInspecao: string): Observable<InspecaoSegurancaResponse> {
    return this.http.get<InspecaoSegurancaResponse>(
      `${this.baseUrl}/inspecoes/${encodeURIComponent(idInspecao)}`
    );
  }

  tratarDesvio(
    idInspecao: string,
    idDesvio: string,
    request: TratarDesvioRequest
  ): Observable<DesvioSegurancaResponse> {
    return this.http.put<DesvioSegurancaResponse>(
      `${this.baseUrl}/inspecoes/${encodeURIComponent(idInspecao)}`
        + `/desvios/${encodeURIComponent(idDesvio)}/tratamento`,
      request
    );
  }

  /**
   * Sobe uma evidência fotográfica **antes** do lançamento. A foto fica pendente
   * no servidor, pertence a quem a enviou e só pode ser consumida uma vez —
   * o expurgo a recolhe após 72 h sem uso.
   */
  enviarEvidenciaPendente(arquivo: Blob, nomeArquivo: string): Observable<EvidenciaInspecaoResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo, nomeArquivo);
    return this.http.post<EvidenciaInspecaoResponse>(`${this.urlEvidenciasPendentes()}`, formData);
  }

  /** Descarta uma foto pendente que ainda não foi vinculada a nenhuma inspeção. */
  descartarEvidenciaPendente(idEvidencia: string): Observable<void> {
    return this.http.delete<void>(
      `${this.urlEvidenciasPendentes()}/${encodeURIComponent(idEvidencia)}`
    );
  }

  /** Acrescenta uma evidência a uma inspeção já lançada. */
  adicionarEvidencia(
    idInspecao: string,
    arquivo: Blob,
    nomeArquivo: string
  ): Observable<EvidenciaInspecaoResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo, nomeArquivo);
    return this.http.post<EvidenciaInspecaoResponse>(this.urlEvidencias(idInspecao), formData);
  }

  /** A imagem é servida sob o mesmo JWT; não existe URL pública. */
  baixarEvidencia(idInspecao: string, idEvidencia: string): Observable<Blob> {
    return this.http.get(
      `${this.urlEvidencias(idInspecao)}/${encodeURIComponent(idEvidencia)}`,
      { responseType: 'blob' }
    );
  }

  /** O backend recusa (`422`) a remoção da última evidência da inspeção. */
  removerEvidencia(idInspecao: string, idEvidencia: string): Observable<void> {
    return this.http.delete<void>(
      `${this.urlEvidencias(idInspecao)}/${encodeURIComponent(idEvidencia)}`
    );
  }

  enviarAnexo(
    idInspecao: string,
    idDesvio: string,
    arquivo: Blob,
    nomeArquivo: string,
    contexto: ContextoAnexo
  ): Observable<AnexoDesvioResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo, nomeArquivo);
    return this.http.post<AnexoDesvioResponse>(
      this.urlAnexo(idInspecao, idDesvio), formData, { params: { contexto } }
    );
  }

  baixarAnexo(idInspecao: string, idDesvio: string, idAnexo: string): Observable<Blob> {
    return this.http.get(
      `${this.urlAnexo(idInspecao, idDesvio)}/${encodeURIComponent(idAnexo)}`,
      { responseType: 'blob' }
    );
  }

  removerAnexo(idInspecao: string, idDesvio: string, idAnexo: string): Observable<void> {
    return this.http.delete<void>(
      `${this.urlAnexo(idInspecao, idDesvio)}/${encodeURIComponent(idAnexo)}`
    );
  }

  private urlEvidenciasPendentes(): string {
    return `${this.baseUrl}/inspecoes/anexos`;
  }

  private urlEvidencias(idInspecao: string): string {
    return `${this.baseUrl}/inspecoes/${encodeURIComponent(idInspecao)}/anexos`;
  }

  private urlAnexo(idInspecao: string, idDesvio: string): string {
    return `${this.baseUrl}/inspecoes/${encodeURIComponent(idInspecao)}`
      + `/desvios/${encodeURIComponent(idDesvio)}/anexos`;
  }

  private params(filter: InspecaoSegurancaFilter): HttpParams {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filter)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return params;
  }
}
