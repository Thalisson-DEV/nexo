import { Injectable } from '@angular/core';

interface CacheEntry<T> {
  value: T;
  expiresAt: number;
}

type ResourceEntries = Map<string, CacheEntry<unknown>>;

/** Número máximo de consultas mantidas para cada resource. */
export const LOOKUP_CACHE_MAX_ENTRIES_PER_RESOURCE = 100;

/**
 * Cache de lookups em memória, separado por resource.
 *
 * A chave é recebida pronta pelo consumidor porque pode conter mais que uma
 * consulta (por exemplo, query e cursor). O consumidor deve normalizar textos
 * com `normalizeLookupQuery` antes de compor a chave.
 */
@Injectable({
  providedIn: 'root',
})
export class LookupCacheService {
  private readonly resources = new Map<string, ResourceEntries>();

  get<T>(resource: string, key: string): T | null {
    const entries = this.resources.get(resource);
    const entry = entries?.get(key);
    if (!entries || !entry) return null;

    const now = Date.now();
    if (now >= entry.expiresAt) {
      entries.delete(key);
      this.removeResourceWhenEmpty(resource, entries);
      return null;
    }

    // Re-inserir promove a entrada ao fim do Map, que representa o item mais
    // recentemente utilizado para a política LRU.
    entries.delete(key);
    entries.set(key, entry);
    return entry.value as T;
  }

  set<T>(resource: string, key: string, value: T, ttlMs: number): void {
    const now = Date.now();
    const entries = this.resources.get(resource) ?? new Map<string, CacheEntry<unknown>>();

    this.removeExpired(entries, now);
    if (ttlMs <= 0 || !Number.isFinite(ttlMs)) {
      entries.delete(key);
      this.removeResourceWhenEmpty(resource, entries);
      return;
    }

    // Atualização também conta como uso recente.
    entries.delete(key);
    entries.set(key, { value, expiresAt: now + ttlMs });
    this.resources.set(resource, entries);

    while (entries.size > LOOKUP_CACHE_MAX_ENTRIES_PER_RESOURCE) {
      const leastRecentlyUsed = entries.keys().next().value as string | undefined;
      if (leastRecentlyUsed === undefined) break;
      entries.delete(leastRecentlyUsed);
    }
  }

  delete(resource: string, key: string): void {
    const entries = this.resources.get(resource);
    if (!entries) return;

    entries.delete(key);
    this.removeResourceWhenEmpty(resource, entries);
  }

  clear(resource?: string): void {
    if (resource === undefined) {
      this.resources.clear();
      return;
    }

    this.resources.delete(resource);
  }

  private removeExpired(entries: ResourceEntries, now: number): void {
    for (const [key, entry] of entries) {
      if (now >= entry.expiresAt) entries.delete(key);
    }
  }

  private removeResourceWhenEmpty(resource: string, entries: ResourceEntries): void {
    if (entries.size === 0) this.resources.delete(resource);
  }
}
