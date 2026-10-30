import { DestroyRef, Signal, WritableSignal, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { Observable, Subject, catchError, map, of, switchMap, tap } from 'rxjs';
import { CesContentState } from '../../core';
import { CesLazyLoadEvent, CesSortRule } from '../../data/data-table/ces-data-table.utils';
import { CesFilterChip } from '../filter-panel/ces-filter-panel.component';

type Primitive = string | number | boolean;

/** Definição de um filtro da listagem: padrão, rótulo do chip e (de)serialização na URL. */
export interface CesFilterDef<V> {
  readonly default: V;
  readonly label: string;
  /** Nome do parâmetro na URL. Padrão: a chave do filtro. */
  readonly param?: string;
  /** `false` para dados sensíveis (CPF, CNH): o filtro não vai para a URL. Padrão `true`. */
  readonly url: boolean;
  /** Texto do valor no chip; `null` esconde o chip (ex.: "sem restrição"). */
  readonly format: (value: V) => string | null;
  readonly serialize: (value: V) => string | null;
  /** `undefined` = parâmetro inválido (ignorado). */
  readonly parse: (raw: string) => V | undefined;
  readonly equals: (a: V, b: V) => boolean;
}

interface CommonOptions<V> {
  readonly label: string;
  readonly param?: string;
  readonly url?: boolean;
  readonly format?: (value: V) => string | null;
}

interface ValueOptions<V> extends CommonOptions<V> {
  /** Tipo do valor na URL quando o padrão é `null`. Padrão: inferido do padrão, senão `string`. */
  readonly type?: 'string' | 'number' | 'boolean';
  /** Opções válidas: dão o texto do chip e validam o parâmetro da URL. */
  readonly options?: () => readonly { readonly label: string; readonly value: V }[];
}

interface ListOptions<V> extends CommonOptions<V[]> {
  readonly type?: 'string' | 'number';
  /** Texto de um item (chip com um só valor). */
  readonly itemLabel?: (value: V) => string;
  /** Texto com vários itens. Padrão: "<n> selecionados". */
  readonly countLabel?: (count: number) => string;
}

export interface CesDateRange {
  readonly from: string | null;
  readonly to: string | null;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function parsePrimitive(raw: string, type: 'string' | 'number' | 'boolean'): Primitive | undefined {
  if (type === 'boolean') return raw === 'true' ? true : raw === 'false' ? false : undefined;
  if (type === 'number') {
    const numero = Number(raw);
    return raw.trim() !== '' && Number.isFinite(numero) ? numero : undefined;
  }
  return raw;
}

function formatDate(value: string): string {
  const [ano, mes, dia] = value.split('-');
  return `${dia}/${mes}/${ano}`;
}

/** Fábricas de definições de filtro para `cesListState`. */
export const cesFilter = {
  /** Valor único (select, booleano). `null` = sem restrição. */
  value<V extends Primitive | null>(defaultValue: V, options: ValueOptions<V>): CesFilterDef<V> {
    // Tipo na URL: o informado, o do padrão ou o das opções (padrão `null`); por fim, texto.
    const tipo = (): 'string' | 'number' | 'boolean' => {
      if (options.type) return options.type;
      const amostra = defaultValue ?? options.options?.().find((opcao) => opcao.value !== null)?.value;
      const tipoAmostra = typeof amostra;
      return tipoAmostra === 'number' || tipoAmostra === 'boolean' ? tipoAmostra : 'string';
    };
    const rotuloDe = (value: V) => options.options?.().find((opcao) => opcao.value === value)?.label;
    return {
      default: defaultValue,
      label: options.label,
      param: options.param,
      url: options.url ?? true,
      format: options.format ?? ((value) => rotuloDe(value) ?? (value === null || value === '' ? null : String(value))),
      serialize: (value) => (value === null ? 'null' : String(value)),
      parse: (raw) => {
        if (raw === 'null') return null as V;
        const valor = parsePrimitive(raw, tipo()) as V | undefined;
        if (valor === undefined) return undefined;
        const opcoes = options.options?.();
        if (opcoes?.length && !opcoes.some((opcao) => opcao.value === valor)) return undefined;
        return valor;
      },
      equals: (a, b) => a === b,
    };
  },

  /** Seleção múltipla. Lista vazia = sem restrição. Na URL, valores separados por vírgula. */
  list<V extends string | number>(defaultValue: V[], options: ListOptions<V>): CesFilterDef<V[]> {
    const tipo = options.type ?? (typeof defaultValue[0] === 'number' ? 'number' : 'string');
    const contagem = options.countLabel ?? ((n: number) => `${n} selecionados`);
    return {
      default: defaultValue,
      label: options.label,
      param: options.param,
      url: options.url ?? true,
      format:
        options.format ??
        ((value) => {
          if (!value.length) return null;
          if (value.length === 1) return options.itemLabel?.(value[0]) ?? String(value[0]);
          return contagem(value.length);
        }),
      serialize: (value) => value.map(String).join(','),
      parse: (raw) => {
        if (raw === '') return [];
        const itens = raw.split(',').map((item) => parsePrimitive(item, tipo));
        return itens.some((item) => item === undefined) ? undefined : (itens as V[]);
      },
      equals: (a, b) => a.length === b.length && [...a].sort().every((item, i) => item === [...b].sort()[i]),
    };
  },

  /** Data (ISO `yyyy-mm-dd`). */
  date(defaultValue: string | null, options: CommonOptions<string | null>): CesFilterDef<string | null> {
    return {
      default: defaultValue,
      label: options.label,
      param: options.param,
      url: options.url ?? true,
      format: options.format ?? ((value) => (value ? formatDate(value) : null)),
      serialize: (value) => value ?? 'null',
      parse: (raw) => (raw === 'null' ? null : ISO_DATE.test(raw) ? raw : undefined),
      equals: (a, b) => a === b,
    };
  },

  /** Período (ISO `yyyy-mm-dd`). Na URL, `inicio~fim`. */
  range(defaultValue: CesDateRange, options: CommonOptions<CesDateRange>): CesFilterDef<CesDateRange> {
    return {
      default: defaultValue,
      label: options.label,
      param: options.param,
      url: options.url ?? true,
      format:
        options.format ??
        ((value) => {
          if (value.from && value.to) return `${formatDate(value.from)} a ${formatDate(value.to)}`;
          if (value.from) return `a partir de ${formatDate(value.from)}`;
          if (value.to) return `até ${formatDate(value.to)}`;
          return null;
        }),
      serialize: (value) => `${value.from ?? ''}~${value.to ?? ''}`,
      parse: (raw) => {
        const [from = '', to = ''] = raw.split('~');
        if ((from && !ISO_DATE.test(from)) || (to && !ISO_DATE.test(to))) return undefined;
        return { from: from || null, to: to || null };
      },
      equals: (a, b) => a.from === b.from && a.to === b.to,
    };
  },
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type CesFilterDefs = Record<string, CesFilterDef<any>>;
export type CesFilterValues<D extends CesFilterDefs> = { [K in keyof D]: D[K]['default'] };

export interface CesListStateConfig<D extends CesFilterDefs> {
  readonly filters: D;
  /** Chave da tabela (`ces.table.<chave>.rows`) para lembrar o tamanho de página. */
  readonly storageKey?: string;
  /** Padrão 25. */
  readonly pageSize?: number;
  readonly pageSizeOptions?: readonly number[];
  /** Ordenação padrão, pelas chaves das colunas (use a mesma chave do `sortField`). */
  readonly sort?: readonly CesSortRule[];
  /** Parâmetro da busca na URL (padrão `busca`); `false` quando a busca pode conter dado pessoal (CPF, CNH). */
  readonly queryParam?: string | false;
  /** `false` desliga a sincronização com a URL. */
  readonly url?: boolean;
}

/** O que a tela envia ao service: filtros aplicados + página, tamanho, ordenação e busca. */
export interface CesListRequest<V> {
  readonly filters: V;
  readonly page: number;
  readonly size: number;
  readonly sort: readonly CesSortRule[];
  readonly query: string;
}

/** Resposta aceita por `results()`: `PagedResponse<T>` ou lista simples. */
export type CesListResponse<T> = { readonly content: readonly T[]; readonly page?: { readonly totalElements: number } } | readonly T[];

export interface CesListResults<T> {
  readonly rows: Signal<readonly T[]>;
  readonly total: Signal<number>;
  readonly loading: Signal<boolean>;
  readonly error: Signal<string | null>;
  /** Estado para o `ces-data-table` (`loading`, `error` ou `ready`; o vazio é decidido pela tabela). */
  readonly state: Signal<CesContentState>;
}

export interface CesResultsOptions {
  /** Dispara a primeira carga já na criação (listas sem `ces-data-table` em modo servidor). */
  readonly immediate?: boolean;
  readonly errorMessage?: (error: unknown) => string;
}

function problemMessage(error: unknown): string {
  const corpo = (error as { error?: { detail?: string; title?: string; message?: string } } | null)?.error;
  return corpo?.detail || corpo?.title || corpo?.message || 'Não foi possível carregar os registros. Tente novamente.';
}

const SORT_PARAM = 'ordem';
const PAGE_PARAM = 'pagina';
const SIZE_PARAM = 'tamanho';

function readStoredPageSize(key: string | undefined, options: readonly number[]): number | null {
  if (!key) return null;
  try {
    const valor = Number(localStorage.getItem(`ces.table.${key}.rows`));
    return options.includes(valor) ? valor : null;
  } catch {
    return null;
  }
}

function serializeSort(rules: readonly CesSortRule[]): string {
  return rules.map((rule) => `${rule.key},${rule.direction}`).join(';');
}

function parseSort(raw: string): CesSortRule[] | undefined {
  if (!raw) return [];
  const regras = raw.split(';').map((parte) => {
    const [key, direction] = parte.split(',');
    return key && (direction === 'asc' || direction === 'desc') ? { key, direction } : null;
  });
  return regras.some((regra) => !regra) ? undefined : (regras as CesSortRule[]);
}

/**
 * Estado de uma listagem "tabela primeiro": filtros com padrões (rascunho no painel × aplicado na
 * consulta), busca, página, tamanho e ordenação, sincronizados com a URL (só o que difere do padrão,
 * com `replaceUrl`). Crie-o num inicializador de campo (contexto de injeção).
 *
 * ```ts
 * readonly lista = cesListState({
 *   storageKey: 'frota-veiculos',
 *   filters: { ativo: cesFilter.value<boolean | null>(true, { label: 'Cadastro' }) },
 *   sort: [{ key: 'placa', direction: 'asc' }],
 * });
 * readonly resultado = this.lista.results((req) => this.service.listar(req));
 * ```
 */
export class CesListState<D extends CesFilterDefs> {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly trigger$ = new Subject<void>();
  private readonly defs: D;
  private readonly keys: (keyof D & string)[];
  private readonly defaultPageSize: number;
  private readonly defaultSort: readonly CesSortRule[];
  private readonly queryParam: string | false;
  private readonly syncUrl: boolean;
  private readonly appliedState: WritableSignal<CesFilterValues<D>>;
  /** Ordenação já traduzida para o backend (vinda do último `lazyLoad`). */
  private backendSort: readonly CesSortRule[] | null = null;

  /** Rascunho editável pelos campos do painel. */
  readonly filters: { readonly [K in keyof D]: WritableSignal<D[K]['default']> };
  /** Filtros aplicados (o que está na consulta e na URL). */
  readonly applied: Signal<CesFilterValues<D>>;
  readonly query: WritableSignal<string>;
  readonly page: WritableSignal<number>;
  readonly pageSize: WritableSignal<number>;
  readonly sort: WritableSignal<CesSortRule[]>;

  /** Filtros aplicados diferentes do padrão. */
  readonly activeCount: Signal<number>;
  /** Chips do resumo: padrões aparecem como não removíveis. */
  readonly summary: Signal<CesFilterChip[]>;
  /** Há filtro diferente do padrão (para o vazio "Ajustar filtros"). */
  readonly filtered: Signal<boolean>;

  constructor(config: CesListStateConfig<D>) {
    this.defs = config.filters;
    this.keys = Object.keys(config.filters) as (keyof D & string)[];
    this.defaultPageSize = config.pageSize ?? 25;
    this.defaultSort = config.sort ?? [];
    this.queryParam = config.queryParam ?? 'busca';
    this.syncUrl = config.url ?? true;
    const opcoesTamanho = config.pageSizeOptions ?? [10, 25, 50, 100];
    const url = this.syncUrl ? this.route.snapshot.queryParamMap : null;

    const iniciais = {} as CesFilterValues<D>;
    for (const chave of this.keys) {
      const def = this.defs[chave];
      const bruto = def.url && url ? url.get(def.param ?? chave) : null;
      const lido = bruto === null ? undefined : def.parse(bruto);
      iniciais[chave] = lido === undefined ? def.default : lido;
    }
    this.appliedState = signal(iniciais, { equal: () => false });
    this.applied = this.appliedState.asReadonly();
    this.filters = Object.fromEntries(this.keys.map((chave) => [chave, signal(iniciais[chave])])) as never;

    const pagina = Number(url?.get(PAGE_PARAM));
    const tamanho = Number(url?.get(SIZE_PARAM));
    const ordem = url?.get(SORT_PARAM);
    this.query = signal((this.queryParam && url?.get(this.queryParam)) || '');
    this.page = signal(Number.isInteger(pagina) && pagina > 1 ? pagina - 1 : 0);
    this.pageSize = signal(
      opcoesTamanho.includes(tamanho)
        ? tamanho
        : (readStoredPageSize(config.storageKey, opcoesTamanho) ?? this.defaultPageSize),
    );
    this.sort = signal([...((ordem !== null && ordem !== undefined ? parseSort(ordem) : undefined) ?? this.defaultSort)]);

    this.activeCount = computed(() => {
      const aplicados = this.applied();
      return this.keys.filter((chave) => !this.defs[chave].equals(aplicados[chave], this.defs[chave].default)).length;
    });
    this.filtered = computed(() => this.activeCount() > 0);
    this.summary = computed(() => {
      const aplicados = this.applied();
      const chips: CesFilterChip[] = [];
      for (const chave of this.keys) {
        const def = this.defs[chave];
        const noPadrao = def.equals(aplicados[chave], def.default);
        const valor = aplicados[chave] as unknown;
        const semRestricao = valor === null || valor === '' || (Array.isArray(valor) && valor.length === 0);
        // Padrão sem restrição ("Todos") não vira chip: o resumo mostra só o que de fato recorta a lista.
        if (noPadrao && semRestricao) continue;
        // Sem restrição só some do resumo quando é o próprio padrão; fora dele, o chip diz "Todos".
        const texto = def.format(aplicados[chave]) ?? (noPadrao ? null : 'Todos');
        if (texto === null) continue;
        chips.push({ key: chave, label: `${def.label}: ${texto}`, removable: !noPadrao });
      }
      return chips;
    });
  }

  /** Aplica o rascunho do painel e recarrega da primeira página. */
  apply(): void {
    const valores = {} as CesFilterValues<D>;
    for (const chave of this.keys) valores[chave] = this.filters[chave]();
    this.appliedState.set(valores);
    this.restart();
  }

  /** "Limpar filtros": volta todos os filtros aos padrões da tela e recarrega. */
  reset(): void {
    for (const chave of this.keys) this.filters[chave].set(this.defs[chave].default);
    this.apply();
  }

  /** Chip removido: o filtro volta ao padrão e a lista recarrega. */
  remove(key: keyof D & string): void {
    this.set(key, this.defs[key].default);
  }

  /** Define e aplica um filtro (filtros rápidos, atalhos). */
  set<K extends keyof D & string>(key: K, value: D[K]['default']): void {
    this.filters[key].set(value);
    this.appliedState.update((atuais) => ({ ...atuais, [key]: value }));
    this.restart();
  }

  /** Volta o rascunho do painel aos valores aplicados (cancelar edição). */
  discardDraft(): void {
    const aplicados = this.applied();
    for (const chave of this.keys) this.filters[chave].set(aplicados[chave]);
  }

  /** Recebe o `lazyLoad` do `ces-data-table` e recarrega. */
  load(event: CesLazyLoadEvent): void {
    this.page.set(event.page);
    this.pageSize.set(event.size);
    this.query.set(event.query);
    this.backendSort = event.sort;
    this.trigger$.next();
  }

  /** Repete a consulta com o estado atual (depois de salvar, "Atualizar"). */
  reload(): void {
    this.trigger$.next();
  }

  /** Filtros aplicados + página, tamanho, ordenação e busca. */
  request(): CesListRequest<CesFilterValues<D>> {
    return {
      filters: this.applied(),
      page: this.page(),
      size: this.pageSize(),
      sort: this.backendSort ?? this.sort(),
      query: this.query().trim(),
    };
  }

  /** `queryParams` atuais da lista, para a trilha da ficha "voltar ao mesmo recorte". */
  queryParams(): Params {
    const params: Params = {};
    for (const [chave, valor] of Object.entries(this.urlParams())) if (valor !== null) params[chave] = valor;
    return params;
  }

  /**
   * Liga a consulta: a cada carga pedida (tabela, aplicar, limpar, chip, recarregar), chama `fn`
   * com o pedido atual e cancela a anterior (`switchMap`), de modo que resposta antiga não
   * sobrescreve a nova.
   */
  results<T>(fn: (request: CesListRequest<CesFilterValues<D>>) => Observable<CesListResponse<T>>, options: CesResultsOptions = {}): CesListResults<T> {
    const rows = signal<readonly T[]>([]);
    const total = signal(0);
    const loading = signal(false);
    const error = signal<string | null>(null);
    const mensagem = options.errorMessage ?? problemMessage;

    this.trigger$
      .pipe(
        map(() => this.request()),
        tap(() => {
          this.writeUrl();
          loading.set(true);
          error.set(null);
        }),
        switchMap((pedido) =>
          fn(pedido).pipe(
            map((resposta) => ({ ok: true as const, resposta })),
            catchError((erro: unknown) => of({ ok: false as const, erro })),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => {
        loading.set(false);
        if (!resultado.ok) {
          error.set(mensagem(resultado.erro));
          return;
        }
        const resposta = resultado.resposta;
        if (Array.isArray(resposta)) {
          rows.set(resposta);
          total.set(resposta.length);
        } else {
          const pagina = resposta as Exclude<CesListResponse<T>, readonly T[]>;
          rows.set(pagina.content);
          total.set(pagina.page?.totalElements ?? pagina.content.length);
        }
      });

    if (options.immediate) this.trigger$.next();

    return {
      rows: rows.asReadonly(),
      total: total.asReadonly(),
      loading: loading.asReadonly(),
      error: error.asReadonly(),
      state: computed<CesContentState>(() => (loading() ? 'loading' : error() ? 'error' : 'ready')),
    };
  }

  private restart(): void {
    this.page.set(0);
    this.trigger$.next();
  }

  private urlParams(): Record<string, string | null> {
    const params: Record<string, string | null> = {};
    const aplicados = this.applied();
    for (const chave of this.keys) {
      const def = this.defs[chave];
      if (!def.url) continue;
      params[def.param ?? chave] = def.equals(aplicados[chave], def.default) ? null : def.serialize(aplicados[chave]);
    }
    if (this.queryParam) params[this.queryParam] = this.query().trim() || null;
    params[PAGE_PARAM] = this.page() > 0 ? String(this.page() + 1) : null;
    params[SIZE_PARAM] = this.pageSize() !== this.defaultPageSize ? String(this.pageSize()) : null;
    const ordem = serializeSort(this.sort());
    params[SORT_PARAM] = ordem !== serializeSort(this.defaultSort) ? ordem : null;
    return params;
  }

  private writeUrl(): void {
    if (!this.syncUrl) return;
    const params = this.urlParams();
    const atuais = this.route.snapshot.queryParamMap;
    const mudou = Object.entries(params).some(([chave, valor]) => atuais.get(chave) !== valor);
    if (!mudou) return;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}

/** Cria o estado da listagem (chame num inicializador de campo). */
export function cesListState<D extends CesFilterDefs>(config: CesListStateConfig<D>): CesListState<D> {
  return new CesListState(config);
}

/**
 * Valor simples ligado a um parâmetro da URL (abas de ficha e de configurações: `?aba=`).
 * Lê na criação, acompanha a URL (links para a mesma tela) e grava com `replaceUrl`; o padrão e
 * valores fora de `values` não vão para a URL.
 */
export function cesUrlParam<T extends string>(options: {
  readonly param?: string;
  readonly values: readonly T[];
  readonly default: T;
}): WritableSignal<T> {
  const router = inject(Router);
  const route = inject(ActivatedRoute);
  const nome = options.param ?? 'aba';
  const valido = (bruto: string | null): T => (bruto && options.values.includes(bruto as T) ? (bruto as T) : options.default);
  const valor = signal<T>(valido(route.snapshot.queryParamMap.get(nome)));
  route.queryParamMap.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe((mapa) => valor.set(valido(mapa.get(nome))));
  effect(() => {
    const atual = valor();
    untracked(() => {
      const naUrl = route.snapshot.queryParamMap.get(nome);
      const desejado = atual === options.default ? null : atual;
      if (naUrl === desejado) return;
      void router.navigate([], { relativeTo: route, queryParams: { [nome]: desejado }, queryParamsHandling: 'merge', replaceUrl: true });
    });
  });
  return valor;
}
