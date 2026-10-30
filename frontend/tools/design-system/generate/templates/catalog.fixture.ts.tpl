export interface {{CLASS_NAME}}CatalogFixture {
  readonly id: string;
  readonly name: string;
  readonly group: string;
  readonly status: 'experimental' | 'stable' | 'deprecated';
  readonly selector: string;
  readonly entrypoint: string;
  readonly objective: string;
  readonly documentationPath: string;
  readonly scenarios: readonly string[];
  readonly tests: readonly string[];
}

/**
 * Deterministic metadata stub for the live catalog. Move or register it in the catalog's
 * fixture registry when the rendered example is implemented.
 */
export const {{CLASS_NAME}}CatalogFixture: {{CLASS_NAME}}CatalogFixture = {
  id: '{{NAME}}',
  name: '{{CLASS_NAME}}',
  group: '{{CATALOG_GROUP}}',
  status: 'experimental',
  selector: 'ces-{{NAME}}',
  entrypoint: '{{PUBLIC_ENTRYPOINT}}',
  objective: 'TODO: describe the user outcome this {{KIND}} standardizes.',
  documentationPath: 'docs/design-system/components/{{NAME}}.md',
  scenarios: [
    'rest',
    'focus-visible',
    'disabled or read-only when applicable',
    'loading, empty, error, and success when applicable',
    'mobile and desktop',
    'light and dark themes',
    'reduced motion',
    'committed, queued, syncing, synced, and conflict when applicable'
  ],
  tests: [
    'unit contract and events',
    'keyboard and focus behavior',
    'automated accessibility',
    'catalog visual regression'
  ]
};
