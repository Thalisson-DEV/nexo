import type { PrimeNgCoverageRegistry } from './primeng-coverage.types';

/**
 * O registro executável fica em `.mjs` para os scripts Node não dependerem de transpile.
 * O aplicativo só precisa do contrato tipado; o catálogo visual terá metadados próprios.
 */
export type TypedPrimeNgCoverageRegistry = PrimeNgCoverageRegistry;
