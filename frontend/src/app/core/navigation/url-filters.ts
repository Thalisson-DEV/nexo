import { inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { QuerySchema, parseQueryState, sameParams, toQueryParams } from './query-state';

export interface UrlFilters<F> {
  /** Filtros presentes na URL atual (o resto fica com o default da tela). */
  read(): Partial<F>;
  /** Grava os filtros aplicados na URL, no lugar (replace), preservando outros parâmetros. */
  write(filters: Partial<F>): void;
}

/**
 * Para telas sem tabela paginada (dashboards, painéis, relatórios com filtro próprio):
 * filtros aplicados vão para a URL com replace e são lidos no init.
 *
 * ```ts
 * private readonly filtrosUrl = createUrlFilters<FiltrosPainel>({ inicio: qp.date('inicio'), ... });
 * ngOnInit() { Object.assign(this, this.filtrosUrl.read()); this.carregar(); }
 * carregar() { this.filtrosUrl.write({ inicio: this.dataInicio, ... }); ... }
 * ```
 */
export function createUrlFilters<F>(schema: QuerySchema<F>): UrlFilters<F> {
  const router = inject(Router);
  const route = inject(ActivatedRoute);
  return {
    read: () => parseQueryState(schema, route.snapshot.queryParamMap),
    write: (filters) => {
      const queryParams = toQueryParams(schema, filters);
      const atuais: Record<string, string | string[]> = {};
      for (const key of Object.keys(queryParams)) {
        const values = route.snapshot.queryParamMap.getAll(key);
        if (values.length) atuais[key] = values.length === 1 ? values[0] : values;
      }
      if (sameParams(queryParams, atuais)) return;
      void router.navigate([], { relativeTo: route, queryParams, queryParamsHandling: 'merge', replaceUrl: true });
    },
  };
}
