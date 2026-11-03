import { DestroyRef, Signal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Params, Router } from '@angular/router';
import { NavigationHistoryService } from './navigation-history.service';

export type DrawerUrlMode = 'novo' | 'editar' | 'ver';

export interface DrawerUrlState {
  /** `novo`, `editar`, `ver` ou `null` (fechado). */
  readonly mode: Signal<DrawerUrlMode | null>;
  /** Id do registro em edição/visualização. */
  readonly id: Signal<string | null>;
  readonly open: Signal<boolean>;
  /** Começa a ouvir a URL. `onChange` é chamado a cada abertura/troca/fechamento vindo da URL. */
  connect(onChange: (mode: DrawerUrlMode | null, id: string | null) => void): void;
  /** `extras` vão para a URL junto (ex.: `{ modo: 'devolucao' }`); declare-os em `extraParams`. */
  openNew(extras?: Params): void;
  openEdit(id: string): void;
  openView(id: string): void;
  /** Fecha: desfaz a entrada do histórico criada ao abrir (ou troca a URL no lugar, se veio por link). */
  close(): void;
}

const PARAMS: Record<DrawerUrlMode, string> = { novo: 'novo', editar: 'editar', ver: 'ver' };

/**
 * Drawer de cadastro sincronizado com a URL: `?novo=1`, `?editar=<id>`, `?ver=<id>`.
 *
 * - abrir = PUSH (o voltar do navegador fecha o drawer e mantém a lista com filtros/página);
 * - F5 ou link com `?editar=<id>` reabre o drawer;
 * - os demais parâmetros (filtros da lista) são preservados.
 *
 * ```ts
 * private readonly drawer = bindDrawerToUrl();
 * ngOnInit() {
 *   this.drawer.connect((modo, id) => modo === 'editar' ? this.carregar(id!) : modo === 'novo' ? this.novo() : this.fechar());
 * }
 * ```
 */
export function bindDrawerToUrl(options: { extraParams?: readonly string[] } = {}): DrawerUrlState {
  const router = inject(Router);
  const route = inject(ActivatedRoute);
  const history = inject(NavigationHistoryService);
  const destroyRef = inject(DestroyRef);

  const mode = signal<DrawerUrlMode | null>(null);
  const id = signal<string | null>(null);
  let pushedByUs = false;

  function read(params: ParamMap): [DrawerUrlMode | null, string | null] {
    if (params.get(PARAMS.editar)) return ['editar', params.get(PARAMS.editar)];
    if (params.get(PARAMS.ver)) return ['ver', params.get(PARAMS.ver)];
    if (params.get(PARAMS.novo) === '1') return ['novo', null];
    return [null, null];
  }

  function navigate(next: DrawerUrlMode | null, nextId: string | null, replaceUrl: boolean, extras: Params = {}) {
    // Sem isto, fechar um drawer aberto por link deixaria os extras para a próxima abertura.
    const limpos = Object.fromEntries((options.extraParams ?? []).map((nome) => [nome, null]));
    return router.navigate([], {
      relativeTo: route,
      queryParams: {
        [PARAMS.novo]: next === 'novo' ? '1' : null,
        [PARAMS.editar]: next === 'editar' ? nextId : null,
        [PARAMS.ver]: next === 'ver' ? nextId : null,
        ...limpos,
        ...extras,
      },
      queryParamsHandling: 'merge',
      replaceUrl,
    });
  }

  function open(next: DrawerUrlMode, nextId: string | null, extras?: Params) {
    // Trocar de registro com o drawer aberto não empilha outra entrada.
    const replace = mode() !== null;
    if (!replace) pushedByUs = true;
    void navigate(next, nextId, replace, extras);
  }

  return {
    mode: mode.asReadonly(),
    id: id.asReadonly(),
    open: computed(() => mode() !== null),

    connect(onChange) {
      route.queryParamMap.pipe(takeUntilDestroyed(destroyRef)).subscribe((params) => {
        const [nextMode, nextId] = read(params);
        if (nextMode === mode() && nextId === id()) return;
        if (nextMode === null) pushedByUs = false;
        mode.set(nextMode);
        id.set(nextId);
        onChange(nextMode, nextId);
      });
    },
    openNew: (extras) => open('novo', null, extras),
    openEdit: (registro) => open('editar', registro),
    openView: (registro) => open('ver', registro),
    close() {
      if (mode() === null) return;
      if (pushedByUs && history.canGoBack()) {
        pushedByUs = false;
        history.back();
        return;
      }
      void navigate(null, null, true);
    },
  };
}
