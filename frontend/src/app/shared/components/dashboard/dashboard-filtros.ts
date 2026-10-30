import { CesFilterChip, CesSelectOption } from '@ces/ui';

/**
 * Auxiliares dos painéis (dashboards) no padrão de listagem: o `ces-filter-panel` mostra o
 * recorte aplicado como chips e cada chip remove o próprio filtro.
 */

/** Itens de um cadastro → opções do `ces-select`/`ces-multiselect`. */
export function opcoesDe<T>(itens: readonly T[] | null | undefined, rotulo: keyof T, valor: keyof T = 'id' as keyof T): CesSelectOption<string>[] {
  return (itens ?? []).map((item) => ({ label: String(item[rotulo] ?? ''), value: String(item[valor]) }));
}

export interface GrupoDeChip {
  /** Chave do filtro (o chip removido devolve essa chave). */
  readonly chave: string;
  readonly rotulo: string;
  readonly opcoes: readonly CesSelectOption<unknown>[];
}

/**
 * Chips do recorte aplicado: um por filtro com seleção ("Base: Salvador" ou "Base: 3 selecionados")
 * e, se houver, o período (`inicio`/`fim` em ISO).
 */
export function chipsDoRecorte(
  filtros: Readonly<Record<string, unknown>>,
  grupos: readonly GrupoDeChip[],
  periodo?: { readonly inicio?: string | null; readonly fim?: string | null; readonly rotulo?: string }
): CesFilterChip[] {
  const chips: CesFilterChip[] = [];
  if (periodo && (periodo.inicio || periodo.fim)) {
    const dia = (iso: string) => new Date(iso).toLocaleDateString('pt-BR');
    const { inicio, fim } = periodo;
    const texto = inicio && fim ? `${dia(inicio)} a ${dia(fim)}` : inicio ? `a partir de ${dia(inicio)}` : `até ${dia(fim!)}`;
    chips.push({ key: 'periodo', label: `${periodo.rotulo ?? 'Período'}: ${texto}` });
  }
  for (const grupo of grupos) {
    const valor = filtros[grupo.chave];
    const selecao = Array.isArray(valor) ? valor : valor === null || valor === undefined || valor === '' ? [] : [valor];
    if (!selecao.length) continue;
    const texto =
      selecao.length === 1 ? (grupo.opcoes.find((o) => o.value === selecao[0])?.label ?? 'selecionado') : `${selecao.length} selecionados`;
    chips.push({ key: grupo.chave, label: `${grupo.rotulo}: ${texto}` });
  }
  return chips;
}
