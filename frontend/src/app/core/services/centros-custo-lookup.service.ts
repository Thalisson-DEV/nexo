import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';

import { CentroFaturamentoResponse } from '../models/rh.models';
import { RhService } from './rh.service';

/**
 * Opções do filtro de centro de custo (a regional comercial à qual cada base
 * operacional está vinculada — no backend, a entidade Centro de Faturamento).
 *
 * Lista curta e estável: é buscada uma vez por sessão e compartilhada entre as
 * telas de SST. Traz também os centros **inativos**, porque uma base desativada
 * continua tendo inspeções históricas e o recorte por centro não exclui bases
 * inativas.
 */
@Injectable({ providedIn: 'root' })
export class CentrosCustoLookupService {
  private readonly rh = inject(RhService);
  private cache$?: Observable<CentroFaturamentoResponse[]>;

  listar(): Observable<CentroFaturamentoResponse[]> {
    this.cache$ ??= this.rh
      .listarCentrosFaturamento({ size: 200, sort: 'nome,asc' })
      .pipe(
        map((resposta) => resposta.content ?? []),
        // Sem opções o filtro apenas não aparece preenchido; não derruba a tela.
        catchError(() => of([] as CentroFaturamentoResponse[])),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    return this.cache$;
  }

  /** Descarta o cache — use após cadastrar ou editar um centro de faturamento. */
  invalidar(): void {
    this.cache$ = undefined;
  }
}
