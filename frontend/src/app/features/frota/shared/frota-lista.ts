import { computed, inject } from '@angular/core';
import { CesListRequest, CesSelectOption } from '@ces/ui';
import { Observable, map } from 'rxjs';
import { LookupStore } from '../../../core/lookup';
import { PagedResponse } from '../../../core/models/shared.models';
import { PaginaFrota } from '../../../core/services/frota.service';

/**
 * Limite da exportação CSV das listagens da frota: o backend aceita no máximo 2.000 registros por
 * página (padrão do Spring Data), então o CSV traz até esse número e avisa quando o recorte é maior.
 */
export const LIMITE_EXPORTACAO_FROTA = 2000;

/** Busca o recorte inteiro (até o limite) para o CSV do `ces-data-table` em modo servidor. */
export function exportacaoFrota<T>(
  buscar: (pagina: PaginaFrota) => Observable<PagedResponse<T>>,
  pedido: () => Pick<CesListRequest<unknown>, 'page' | 'size' | 'sort'>
): (evento: unknown, limite: number) => Observable<T[]> {
  return (_evento, limite) => buscar({ ...paginaFrota(pedido()), page: 0, size: limite }).pipe(map((pagina) => pagina.content));
}

/** Página, tamanho e ordenação do pedido da listagem no formato dos endpoints da frota. */
export function paginaFrota(pedido: Pick<CesListRequest<unknown>, 'page' | 'size' | 'sort'>): PaginaFrota {
  return {
    page: pedido.page,
    size: pedido.size,
    sort: pedido.sort.length ? pedido.sort.map((regra) => `${regra.key},${regra.direction}`) : undefined
  };
}

/** Bases operacionais para filtros e o nome de uma base para o chip do resumo. */
export function basesFrota() {
  const lookups = inject(LookupStore);
  lookups.loadBootstrap().subscribe({ error: () => undefined });
  const opcoes = computed<CesSelectOption<string>[]>(() =>
    lookups.operationalBases().map((b) => ({ label: b.label, value: b.value }))
  );
  return {
    opcoes,
    nome: (id: string) => opcoes().find((b) => b.value === id)?.label ?? 'base selecionada'
  };
}

/** Opções do filtro de cadastro (ativo) usado em veículos, implementos e condutores. */
export const OPCOES_CADASTRO: CesSelectOption<boolean | null>[] = [
  { label: 'Somente ativos', value: true },
  { label: 'Somente desativados', value: false },
  { label: 'Todos', value: null }
];
