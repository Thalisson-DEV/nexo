import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { LookupBootstrap } from '../models/lookup-bootstrap';
import { LookupOption } from '../models/lookup-option';
import { LookupPage } from '../models/lookup-page';

export interface LookupSearchRequest {
  query: string;
  limit?: number;
  cursor?: string | null;
  types?: string[];
}

/**
 * Adaptador HTTP dos endpoints de lookup. Cache, estado e regras de interacao
 * pertencem ao store e aos componentes consumidores.
 */
@Injectable({ providedIn: 'root' })
export class LookupService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  getBootstrap(): Observable<LookupBootstrap> {
    return this.http.get<LookupBootstrap>(`${this.apiUrl}/lookups/bootstrap`);
  }

  searchCollaborators(request: LookupSearchRequest): Observable<LookupPage<LookupOption<string>>> {
    return this.search(`${this.apiUrl}/v1/rh/colaboradores/options`, request);
  }

  searchMaterials(request: LookupSearchRequest): Observable<LookupPage<LookupOption<string>>> {
    return this.search(`${this.apiUrl}/v1/materiais/options`, request);
  }

  searchStocks(request: LookupSearchRequest): Observable<LookupPage<LookupOption<string>>> {
    return this.search(`${this.apiUrl}/v1/estoques/options`, request);
  }

  private search(
    url: string,
    request: LookupSearchRequest,
  ): Observable<LookupPage<LookupOption<string>>> {
    let params = new HttpParams().set('q', request.query).set('limit', request.limit ?? 50);

    if (request.cursor) params = params.set('cursor', request.cursor);
    for (const type of request.types ?? []) params = params.append('tipos', type);

    return this.http
      .get<unknown>(url, { params })
      .pipe(map((response) => this.normalizeLookupPage(response)));
  }

  private normalizeLookupPage(response: unknown): LookupPage<LookupOption<string>> {
    if (!response || typeof response !== 'object') throw this.invalidResponseError();

    const candidate = response as {
      items?: unknown;
      nextCursor?: unknown;
      hasMore?: unknown;
    };
    if (!Array.isArray(candidate.items)) throw this.invalidResponseError();

    const items = candidate.items.flatMap((item): LookupOption<string>[] => {
      if (!item || typeof item !== 'object') return [];
      const option = item as { value?: unknown; label?: unknown };
      if (typeof option.value !== 'string' || !option.value.trim()) return [];

      const value = option.value.trim();
      const rawLabel = typeof option.label === 'string' ? option.label.trim() : '';
      const invalidLabel = /^(?:(?:null|undefined)\s*,?\s*)+$/i.test(rawLabel);
      const fallbackLabel = `Cadastro sem descrição (${value.slice(0, 8)})`;
      return [{ value, label: rawLabel && !invalidLabel ? rawLabel : fallbackLabel }];
    });
    const nextCursor =
      typeof candidate.nextCursor === 'string' && candidate.nextCursor
        ? candidate.nextCursor
        : null;

    return {
      items,
      nextCursor,
      hasMore: candidate.hasMore === true && nextCursor !== null,
    };
  }

  private invalidResponseError(): Error {
    return new Error('A busca retornou dados em um formato inválido. Tente novamente.');
  }
}
