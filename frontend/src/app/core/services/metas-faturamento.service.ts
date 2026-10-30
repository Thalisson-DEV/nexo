import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import { PagedResponse } from '../models/shared.models';
import {
  MetaFaturamentoFilter,
  MetaFaturamentoRequest,
  MetaFaturamentoResponse,
  MetaFaturamentoUpdateRequest
} from '../models/meta-faturamento.models';

/**
 * Metas de faturamento das equipes por ciclo comercial ou mês civil. Leitura exige
 * `rh:meta-faturamento:read`; escrita, `rh:meta-faturamento:write`.
 */
@Injectable({ providedIn: 'root' })
export class MetasFaturamentoService extends BaseService {
  private readonly url = `${environment.apiUrl}/v1/metas-faturamento`;

  listar(filtros: MetaFaturamentoFilter): Observable<PagedResponse<MetaFaturamentoResponse>> {
    const limpos: Record<string, unknown> = {};
    Object.entries(filtros).forEach(([chave, valor]) => {
      if (valor !== null && valor !== undefined && valor !== '') limpos[chave] = valor;
    });
    return this.getList<MetaFaturamentoResponse>(this.url, limpos);
  }

  /** Cria uma meta por equipe informada (tudo ou nada). */
  criar(request: MetaFaturamentoRequest): Observable<MetaFaturamentoResponse[] | null> {
    return this.createSync<MetaFaturamentoResponse[]>(this.url, request);
  }

  atualizar(id: string, request: MetaFaturamentoUpdateRequest): Observable<MetaFaturamentoResponse | null> {
    return this.updateSync<MetaFaturamentoResponse>(this.url, id, request);
  }

  desativar(id: string): Observable<void | null> {
    return this.deleteSync(this.url, id);
  }

  ativar(id: string): Observable<void | null> {
    return this.updateSync<void>(this.url, `${id}/ativar`, {});
  }
}
