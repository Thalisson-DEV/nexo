export type CesActionVariant =
  | 'primary'
  | 'secondary'
  | 'outline'
  | 'ghost'
  | 'destructive'
  | 'success'
  | 'link';

export type CesActionSize = 'sm' | 'md' | 'lg' | 'icon-sm' | 'icon-md' | 'icon-lg';

export type CesTone = 'neutral' | 'brand' | 'info' | 'success' | 'warning' | 'danger';

export type CesSurfaceVariant = 'plain' | 'raised' | 'outlined' | 'subtle' | 'interactive';

export type CesContentState =
  | 'idle'
  | 'loading'
  | 'ready'
  | 'empty'
  | 'refreshing'
  | 'error'
  | 'offline-cached'
  | 'stale';

export type CesMutationState =
  | 'idle'
  | 'submitting'
  | 'committed'
  | 'queued'
  | 'synchronizing'
  | 'failed'
  | 'cancelled';

export type CesDensity = 'compact' | 'comfortable';
