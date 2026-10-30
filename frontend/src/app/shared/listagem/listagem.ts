import { Observable, map } from 'rxjs';
import { CesListRequest, CesSelectOption } from '@ces/ui';

/**
 * Auxiliares das telas no padrão de listagem (docs/design-system/components/list-page.md) que
 * usam os serviços baseados em `BaseService` (parâmetros soltos `page`, `size`, `sort`).
 */

/** Página, tamanho e ordenação no formato do Spring (`sort=campo,asc`; vários critérios repetem o parâmetro). */
export function paginaPadrao(pedido: Pick<CesListRequest<unknown>, 'page' | 'size' | 'sort'>): {
  page: number;
  size: number;
  sort?: string | string[];
} {
  const sort = pedido.sort.map((regra) => `${regra.key},${regra.direction}`);
  return { page: pedido.page, size: pedido.size, sort: sort.length === 0 ? undefined : sort.length === 1 ? sort[0] : sort };
}

/** Filtro de status de cadastro (ativo/inativo). `null` = todos. */
export const OPCOES_STATUS: CesSelectOption<boolean | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Ativo', value: true },
  { label: 'Inativo', value: false }
];

/**
 * Limite da exportação CSV das listagens paginadas: o Spring Data aceita no máximo 2.000 registros
 * por página, então o CSV traz até esse número e a tabela avisa quando o recorte é maior.
 */
export const LIMITE_EXPORTACAO = 2000;

/**
 * Provedor `exportRows` do `ces-data-table` para serviços paginados: repete a consulta do recorte
 * atual pedindo a primeira página com `LIMITE_EXPORTACAO` linhas.
 */
export function exportacaoPadrao<T>(
  buscar: (pagina: { page: number; size: number; sort?: string | string[] }) => Observable<{ content: T[] }>,
  pedido: () => Pick<CesListRequest<unknown>, 'page' | 'size' | 'sort'>
): (evento: unknown, limite: number) => Observable<T[]> {
  return (_evento, limite) => buscar({ ...paginaPadrao(pedido()), page: 0, size: limite }).pipe(map((r) => r.content));
}

/** Data local (`Date`) → `yyyy-MM-dd`, sem deslocar pelo fuso (filtros e requests de data). */
export function dataIso(data: Date | null | undefined): string | null {
  if (!data) return null;
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

/** `yyyy-MM-dd` → `Date` local à meia-noite (para o `ces-datepicker`). */
export function dataLocal(valor: string | null | undefined): Date | null {
  if (!valor) return null;
  const [ano, mes, dia] = valor.substring(0, 10).split('-').map(Number);
  return new Date(ano, mes - 1, dia);
}
