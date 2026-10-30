import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, finalize, of, timeout } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  PublicStatus,
  PublicStatusComponent,
  PublicStatusResponse,
  StatusSourceState,
} from '../models/status.models';
import { PublicStatusService } from './public-status.service';

const POLL_MS = 15_000;
const MAX_BACKOFF_MS = 60_000;
const REQUEST_TIMEOUT_MS = 3_000;
const MAX_PROJECTION_AGE_MS = 60_000;

export type StatusSourceError = 'offline' | 'unavailable' | 'stale' | null;

/** Estado compartilhado do status público. Não depende de sessão, API ou cache do Nexo. */
@Injectable({ providedIn: 'root' })
export class PublicStatusStore {
  private readonly service = inject(PublicStatusService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _projection = signal<PublicStatusResponse | null>(null);
  private readonly _sourceState = signal<StatusSourceState>('idle');
  private readonly _sourceError = signal<StatusSourceError>(null);
  private readonly _lastEvidenceAt = signal<string | null>(null);
  private readonly _expanded = signal(false);
  private readonly _refreshing = signal(false);
  private readonly _visible = signal(document.visibilityState === 'visible');

  readonly projection = this._projection.asReadonly();
  readonly sourceState = this._sourceState.asReadonly();
  readonly sourceError = this._sourceError.asReadonly();
  readonly lastEvidenceAt = this._lastEvidenceAt.asReadonly();
  readonly expanded = this._expanded.asReadonly();
  readonly refreshing = this._refreshing.asReadonly();
  readonly isVisible = this._visible.asReadonly();
  readonly statusPageConfigured = environment.statusPageUrl.length > 0;

  readonly componentsWithIssues = computed(() =>
    (this._projection()?.components ?? []).filter(
      (component) => component.status === 'DOWN' || component.status === 'DEGRADED',
    ),
  );

  readonly hasBanner = computed(() => {
    const source = this._sourceState();
    const overall = this._projection()?.overallStatus;
    return (
      source === 'offline' ||
      source === 'unavailable' ||
      source === 'stale' ||
      overall === 'DOWN' ||
      overall === 'DEGRADED' ||
      overall === 'UNKNOWN'
    );
  });

  private timer: ReturnType<typeof setTimeout> | undefined;
  private inFlight = false;
  private failureCount = 0;

  constructor() {
    const refreshOnVisible = () => {
      this._visible.set(document.visibilityState === 'visible');
      if (document.visibilityState === 'visible') {
        this.refresh();
      }
    };
    const refreshWhenOnline = () => this.refresh();
    const markOffline = () => this.markFailure('offline');

    document.addEventListener('visibilitychange', refreshOnVisible);
    window.addEventListener('focus', refreshOnVisible);
    window.addEventListener('online', refreshWhenOnline);
    window.addEventListener('offline', markOffline);
    this.destroyRef.onDestroy(() => {
      if (this.timer) clearTimeout(this.timer);
      document.removeEventListener('visibilitychange', refreshOnVisible);
      window.removeEventListener('focus', refreshOnVisible);
      window.removeEventListener('online', refreshWhenOnline);
      window.removeEventListener('offline', markOffline);
    });

    this.refresh();
  }

  refresh(): void {
    if (this.inFlight || !this.isVisible()) return;
    if (!navigator.onLine) {
      this.markFailure('offline');
      this.scheduleNext();
      return;
    }

    if (this.timer) clearTimeout(this.timer);
    this.inFlight = true;
    this._refreshing.set(true);
    this._sourceState.set('loading');

    this.service
      .get()
      .pipe(
        timeout(REQUEST_TIMEOUT_MS),
        catchError(() => {
          this.markFailure('unavailable');
          return of(null);
        }),
        finalize(() => {
          this.inFlight = false;
          this._refreshing.set(false);
          this.scheduleNext();
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((projection) => {
        if (projection) this.accept(projection);
      });
  }

  toggleExpanded(): void {
    this._expanded.update((expanded) => !expanded);
  }

  closeDetails(): void {
    this._expanded.set(false);
  }

  openStatusPage(): void {
    if (environment.statusPageUrl) {
      window.open(environment.statusPageUrl, '_blank', 'noopener,noreferrer');
    }
  }

  private accept(projection: PublicStatusResponse): void {
    if (!isValidProjection(projection)) {
      this.markFailure('unavailable');
      return;
    }

    const generatedAtMs = Date.parse(projection.generatedAt);
    if (Date.now() - generatedAtMs > MAX_PROJECTION_AGE_MS) {
      this._sourceState.set('stale');
      this._sourceError.set('stale');
      this._lastEvidenceAt.set(projection.generatedAt);
      return;
    }

    this.failureCount = 0;
    this._projection.set(projection);
    this._lastEvidenceAt.set(projection.generatedAt);
    this._sourceState.set('ready');
    this._sourceError.set(null);
  }

  private markFailure(error: Exclude<StatusSourceError, null>): void {
    this.failureCount += 1;
    this._sourceState.set(error);
    this._sourceError.set(error);
  }

  private scheduleNext(): void {
    if (!this.isVisible()) return;
    const delay = Math.min(POLL_MS * 2 ** Math.max(0, this.failureCount - 1), MAX_BACKOFF_MS);
    this.timer = setTimeout(() => this.refresh(), delay);
  }
}

const VALID_STATUSES = new Set<PublicStatus>(['UP', 'DOWN', 'DEGRADED', 'UNKNOWN']);

function isValidProjection(value: PublicStatusResponse): boolean {
  if (!value || value.schemaVersion !== '1' || !isIsoDate(value.generatedAt)) return false;
  if (!VALID_STATUSES.has(value.overallStatus)) return false;
  if (!Array.isArray(value.components)) return false;
  return value.components.every(isValidComponent);
}

function isValidComponent(value: PublicStatusComponent): boolean {
  return (
    !!value &&
    typeof value.key === 'string' &&
    typeof value.label === 'string' &&
    value.label.trim().length > 0 &&
    VALID_STATUSES.has(value.status) &&
    isIsoDate(value.observedAt) &&
    typeof value.message === 'string'
  );
}

function isIsoDate(value: string): boolean {
  return typeof value === 'string' && Number.isFinite(Date.parse(value));
}
