import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  MeuDesvio,
  MeuDesvioFilter,
  PageResponse,
  TratarDesvioRequest,
  TratarDesviosEmMassaRequest,
  TratamentoDesviosEmMassaResponse,
} from './meu-desvio.models';

@Injectable({ providedIn: 'root' })
export class MeuDesvioService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${environment.apiBaseUrl}/v1/sst/desvios/meus`;

  listar(filter: MeuDesvioFilter): Observable<PageResponse<MeuDesvio>> {
    let params = new HttpParams();

    for (const [key, value] of Object.entries(filter)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }

    return this.http.get<PageResponse<MeuDesvio>>(this.endpoint, { params });
  }

  tratar(idDesvio: string, request: TratarDesvioRequest): Observable<MeuDesvio> {
    return this.http.put<MeuDesvio>(
      `${this.endpoint}/${encodeURIComponent(idDesvio)}/tratamento`,
      request
    );
  }

  tratarEmMassa(
    request: TratarDesviosEmMassaRequest
  ): Observable<TratamentoDesviosEmMassaResponse> {
    return this.http.put<TratamentoDesviosEmMassaResponse>(
      `${this.endpoint}/tratamento-em-massa`,
      request
    );
  }
}
