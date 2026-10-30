/** Opção mínima compartilhada por selects, autocompletes e filtros. */
export interface LookupOption<T = string> {
  value: T;
  label: string;
}
