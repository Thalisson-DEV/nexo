/**
 * Normaliza texto digitado antes de comparar consultas ou compor chaves de cache.
 *
 * A normalização é deliberadamente limitada ao contrato do lookup: espaços nas
 * extremidades e diferenças de caixa não devem criar consultas distintas.
 */
export function normalizeLookupQuery(value: string): string {
  return value.trim().toLocaleLowerCase('pt-BR');
}
