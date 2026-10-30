/** Contrato sanitizado publicado pelo plano de observabilidade independente. */
export type PublicStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN';

export interface PublicStatusComponent {
  readonly key: string;
  readonly label: string;
  readonly status: PublicStatus;
  readonly observedAt: string;
  readonly incidentId?: string;
  readonly message: string;
}

export interface PublicStatusResponse {
  readonly schemaVersion: string;
  readonly generatedAt: string;
  readonly overallStatus: PublicStatus;
  readonly components: readonly PublicStatusComponent[];
}

export type StatusSourceState = 'idle' | 'loading' | 'ready' | 'offline' | 'unavailable' | 'stale';
