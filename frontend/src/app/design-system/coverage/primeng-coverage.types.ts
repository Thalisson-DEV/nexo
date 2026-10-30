export const PRIMENG_COVERAGE_DISPOSITIONS = [
  'wrapper',
  'pattern',
  'styled-direct',
  'infrastructure',
  'excluded'
] as const;

export type PrimeNgCoverageDisposition = (typeof PRIMENG_COVERAGE_DISPOSITIONS)[number];

export interface PrimeNgCoverageEntry {
  /** Canonical exact public package entrypoint, for example `primeng/button`. */
  readonly entrypoint: string;
  /** Intended design-system treatment for this entrypoint. */
  readonly disposition: PrimeNgCoverageDisposition;
  /** Stable explanation of the selected treatment. */
  readonly rationale: string;
}

export interface PrimeNgFilteredPackageExport {
  /** Export key as declared by PrimeNG's package.json, for example `./types/button`. */
  readonly exportKey: string;
  /** Why this exported package surface is accounted for as a filtered family. */
  readonly rationale: string;
}

export interface PrimeNgCoverageRegistry {
  readonly schemaVersion: 1;
  readonly primeNgVersion: string;
  readonly practicalEntrypointPolicy: 'exact-public-consumer-export-v2';
  readonly practicalEntrypointDefinition: string;
  readonly entries: readonly PrimeNgCoverageEntry[];
  readonly filteredPackageExports: readonly PrimeNgFilteredPackageExport[];
}
