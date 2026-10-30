import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import { PagedResponse } from '../models/shared.models';
import {
  AcompanhamentoMetaFilter,
  AcompanhamentoMetasResponse,
  MetaInspecaoFilter,
  MetaInspecaoRequest,
  MetaInspecaoResponse
} from '../models/meta-inspecao.models';
import { toHttpParams } from '../utils/http-params.util';

/**
 * Metas de inspeção (SST): cadastro por função ou colaborador e apuração da
 * janela vigente. Leitura exige `sst:meta:read`; escrita, `sst:meta:write`.
 */
@Injectable({ providedIn: 'root' })
export class MetasInspecaoService extends BaseService {
  private readonly metasUrl = `${environment.apiUrl}/v1/sst/metas-inspecao`;

  listarMetas(filtros: MetaInspecaoFilter): Observable<PagedResponse<MetaInspecaoResponse>> {
    return this.getList<MetaInspecaoResponse>(this.metasUrl, this.limparFiltros(filtros));
  }

  buscarMetaPorId(id: string): Observable<MetaInspecaoResponse> {
    return this.getOne<MetaInspecaoResponse>(this.metasUrl, id);
  }

  criarMeta(request: MetaInspecaoRequest): Observable<MetaInspecaoResponse | null> {
    return this.createSync<MetaInspecaoResponse>(this.metasUrl, request);
  }

  atualizarMeta(id: string, request: MetaInspecaoRequest): Observable<MetaInspecaoResponse | null> {
    return this.updateSync<MetaInspecaoResponse>(this.metasUrl, id, request);
  }

  /** Soft delete: a meta sai das apurações mas continua listável com `ativo=false`. */
  desativarMeta(id: string): Observable<void | null> {
    return this.deleteSync(this.metasUrl, id);
  }

  ativarMeta(id: string): Observable<void | null> {
    return this.updateSync<void>(this.metasUrl, `${id}/ativar`, {});
  }

  /**
   * Apuração da janela vigente na data de referência (default do backend: hoje).
   * Os recortes múltiplos viajam como parâmetro repetido; omitir
   * `ordenarPor`/`direcao` mantém a ordem padrão (`PERCENTUAL ASC`).
   */
  acompanhamento(filtros: AcompanhamentoMetaFilter): Observable<AcompanhamentoMetasResponse> {
    const params = toHttpParams(filtros as Record<string, unknown>);
    return this.http.get<AcompanhamentoMetasResponse>(`${this.metasUrl}/acompanhamento`, { params });
  }

  private limparFiltros<T extends object>(filtros: T): Record<string, unknown> {
    const limpos: Record<string, unknown> = {};
    Object.entries(filtros).forEach(([chave, valor]) => {
      if (valor !== null && valor !== undefined && valor !== '') {
        limpos[chave] = valor;
      }
    });
    return limpos;
  }
}
