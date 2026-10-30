import { Injectable } from '@angular/core';

const STORAGE_PREFIX = 'ces.vs.';

interface Entry {
  value: unknown;
  /** Epoch (ms) a partir do qual a entrada é descartada. */
  expiresAt?: number;
}

export interface ViewStateWriteOptions {
  /** Tempo de vida em milissegundos. Sem TTL, a entrada vive até a limpeza. */
  ttlMs?: number;
  /**
   * Espelha em `sessionStorage` para sobreviver ao F5. Desligue para dado pessoal
   * (CPF, nome, dados de vítima etc.): ele fica só em memória e some ao recarregar.
   */
  persist?: boolean;
}

/**
 * Estado de tela que não deve (ou não cabe) ir para a URL: rascunhos de formulário,
 * posições de scroll, seleções grandes.
 *
 * Chave = `<rota>::<identificador>`. Memória com espelho opcional em `sessionStorage`.
 * Limpo no logout/login e quando o usuário navega intencionalmente (menu, breadcrumb, início).
 */
@Injectable({ providedIn: 'root' })
export class ViewStateStore {
  private readonly memory = new Map<string, Entry>();

  static key(route: string, id = 'default'): string {
    return `${normalizeRoute(route)}::${id}`;
  }

  get<T>(key: string): T | undefined {
    const entry = this.memory.get(key) ?? this.readStorage(key);
    if (!entry) return undefined;
    if (entry.expiresAt !== undefined && entry.expiresAt <= Date.now()) {
      this.remove(key);
      return undefined;
    }
    if (!this.memory.has(key)) this.memory.set(key, entry);
    return entry.value as T;
  }

  set<T>(key: string, value: T, options: ViewStateWriteOptions = {}): void {
    const entry: Entry = {
      value,
      expiresAt: options.ttlMs !== undefined ? Date.now() + options.ttlMs : undefined,
    };
    this.memory.set(key, entry);
    if (options.persist ?? true) {
      this.writeStorage(key, entry);
    } else {
      this.removeStorage(key);
    }
  }

  remove(key: string): void {
    this.memory.delete(key);
    this.removeStorage(key);
  }

  /** Remove tudo que pertence à rota (e às sub-rotas dela). */
  clearRoute(route: string): void {
    const base = normalizeRoute(route);
    const matches = (key: string) => key.startsWith(`${base}::`) || key.startsWith(`${base}/`);
    for (const key of [...this.memory.keys()]) if (matches(key)) this.memory.delete(key);
    for (const key of this.storageKeys()) if (matches(key)) this.removeStorage(key);
  }

  /** Logout/login: nada do usuário anterior pode ser restaurado. */
  clear(): void {
    this.memory.clear();
    for (const key of this.storageKeys()) this.removeStorage(key);
  }

  private storageKeys(): string[] {
    const keys: string[] = [];
    try {
      for (let i = 0; i < sessionStorage.length; i++) {
        const raw = sessionStorage.key(i);
        if (raw?.startsWith(STORAGE_PREFIX)) keys.push(raw.slice(STORAGE_PREFIX.length));
      }
    } catch {
      // sessionStorage indisponível (modo privado restrito): segue só em memória.
    }
    return keys;
  }

  private readStorage(key: string): Entry | undefined {
    try {
      const raw = sessionStorage.getItem(STORAGE_PREFIX + key);
      return raw ? (JSON.parse(raw, reviveDates) as Entry) : undefined;
    } catch {
      return undefined;
    }
  }

  private writeStorage(key: string, entry: Entry): void {
    try {
      sessionStorage.setItem(STORAGE_PREFIX + key, JSON.stringify(entry));
    } catch {
      // Cota excedida ou storage bloqueado: a cópia em memória continua valendo.
    }
  }

  private removeStorage(key: string): void {
    try {
      sessionStorage.removeItem(STORAGE_PREFIX + key);
    } catch {
      // idem
    }
  }
}

/** `routerLink` de MenuItem (texto ou comandos) → URL. */
export function linkToUrl(link: unknown): string {
  if (Array.isArray(link)) return '/' + link.map(String).join('/').replace(/^\/+/, '');
  return String(link ?? '/');
}

/** Caminho sem query/fragmento e sem barra final. */
export function normalizeRoute(route: string): string {
  const path = route.split(/[?#]/)[0] || '/';
  return path.length > 1 ? path.replace(/\/+$/, '') : path;
}

const ISO_DATE_TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/;

/** Rascunhos guardam `Date` (datepickers): o JSON as devolve como Date, não como texto. */
function reviveDates(_key: string, value: unknown): unknown {
  return typeof value === 'string' && ISO_DATE_TIME.test(value) ? new Date(value) : value;
}
