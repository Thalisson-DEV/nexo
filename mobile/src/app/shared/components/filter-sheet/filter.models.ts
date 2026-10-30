/**
 * Contrato compartilhado dos filtros das listagens (Inspeções, Campo,
 * Solicitações e Transferências).
 *
 * As classes de estilo (`activeClass` / `inactiveClass`) são as definidas em
 * `src/styles.scss`, na camada `@layer components`.
 */
export interface FilterOption<T extends string = string> {
  /** Valor associado à opção — deve pertencer ao union do filtro do domínio. */
  readonly value: T;
  /** Texto exibido no chip. */
  readonly label: string;
  /** Classe aplicada quando a opção está selecionada. */
  readonly activeClass: string;
  /** Classe aplicada quando a opção não está selecionada. Padrão: `filter-bw-inactive`. */
  readonly inactiveClass?: string;
}

/** Classe padrão dos chips não selecionados. */
export const FILTER_INACTIVE_CLASS = 'filter-bw-inactive';
