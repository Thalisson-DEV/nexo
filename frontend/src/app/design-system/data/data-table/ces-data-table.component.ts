import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  InjectionToken,
  Injector,
  OnInit,
  computed,
  contentChild,
  contentChildren,
  effect,
  inject,
  input,
  model,
  output,
  signal,
  untracked,
} from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { Observable } from 'rxjs';
import { CesContentState, CesDensity } from '../../core';
import { CES_COMPACT_VIEWPORT_QUERY, cesMediaQuery } from '../../core/media';
import { CES_LIST_SHORTCUTS } from '../../core/shortcuts';
import { NotificationService } from '../../feedback/notification';
import { CesAsyncStateComponent } from '../../feedback/async-state';
import { CesConfirmationService } from '../../overlays/confirmation';
import { CesButtonComponent } from '../../primitives/button';
import { CesTableShellComponent } from '../table-shell';
import { CesTableToolbarComponent } from '../table-toolbar';
import { CesCellDirective, CesRowActionsDirective, CesRowDetailDirective } from './ces-data-table.directives';
import {
  CES_PAGE_SIZE_OPTIONS,
  CesColumnFilter,
  CesDataTableColumn,
  CesLazyLoadEvent,
  CesSortRule,
  columnValue,
  filterLabel,
  filterRows,
  formatCell,
  isSortable,
  nextSortRules,
  searchRows,
  sortRows,
  toCsv,
} from './ces-data-table.utils';

/**
 * Ajuste do texto às colunas: cada coluna tem largura mínima e o texto quebra em no máximo
 * `maxLines` linhas (o restante fica no `title`). Quando a área é estreita, a tabela rola na
 * horizontal em vez de espremer as colunas.
 */
export interface CesDataTableTextFit {
  /** Largura mínima das colunas de texto (as numéricas usam a metade; `column.width` prevalece). */
  readonly minColumnWidth: string;
  readonly maxLines: number;
}

/** Padrão de ajuste para as tabelas de um contêiner (ex.: o drawer de importação). */
export const CES_DATA_TABLE_TEXT_FIT = new InjectionToken<CesDataTableTextFit>('CES_DATA_TABLE_TEXT_FIT');

let tableSequence = 0;
const SEARCH_DEBOUNCE_MS = 300;
const inteiro = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 });

function readStoredPageSize(key: string | undefined, options: readonly number[]): number | null {
  if (!key) return null;
  try {
    const valor = Number(localStorage.getItem(`ces.table.${key}.rows`));
    return options.includes(valor) ? valor : null;
  } catch {
    return null;
  }
}

/**
 * Tabela de dados do Nexo.
 *
 * - `mode="client"` (padrão): conjunto já carregado; ordenação multicoluna (Shift+clique), busca
 *   rápida sem acento, paginação 10/25/50/100 lembrada por tabela, exportação CSV da visão.
 * - `mode="server"`: listagens paginadas no backend. A tabela não ordena nem busca localmente;
 *   emite `lazyLoad` (na entrada e a cada mudança de página, tamanho, ordenação, busca com espera
 *   de 300 ms ou filtro por coluna) e mostra as linhas recebidas com `totalRecords`.
 *
 * Nos dois modos: linha expansível, ação por linha e, em telas compactas, cards com prioridade de
 * colunas, seletor "Ordenar por" e "Carregar mais".
 */
/** Elementos que têm clique próprio: não ativam a linha. */
const CONTROLES_INTERATIVOS =
  'a, button, input, select, textarea, label, summary, [role="button"], [role="checkbox"], [role="link"], [role="menuitem"], [contenteditable="true"], .p-checkbox, .p-radiobutton, .p-toggleswitch, .p-select, .p-multiselect';

@Component({
  selector: 'ces-data-table',
  standalone: true,
  imports: [NgTemplateOutlet, CesAsyncStateComponent, CesButtonComponent, CesTableShellComponent, CesTableToolbarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ces-data-table',
    '[attr.data-layout]': "compact() ? 'cards' : 'table'",
    '[attr.data-busy]': 'refreshing() || null',
    '[attr.data-fit]': "fit() ? '' : null",
    '[style.--ces-data-table-lines]': 'fit()?.maxLines ?? null',
  },
  styleUrl: './ces-data-table.component.css',
  templateUrl: './ces-data-table.component.html',
})
export class CesDataTableComponent<T = unknown> implements OnInit {
  private readonly document = inject(DOCUMENT);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly shortcuts = inject(CES_LIST_SHORTCUTS, { optional: true });
  private readonly textFitPadrao = inject(CES_DATA_TABLE_TEXT_FIT, { optional: true });
  protected readonly id = `ces-data-table-${++tableSequence}`;

  readonly columns = input.required<readonly CesDataTableColumn<T>[]>();
  readonly rows = input<readonly T[]>([]);
  /** `client` (padrão) para conjuntos carregados; `server` para listagens paginadas no backend. */
  readonly mode = input<'client' | 'server'>('client');
  /** Modo servidor: total de registros do recorte (paginação e rótulo de intervalo). */
  readonly totalRecords = input<number | undefined>();
  /** Identidade estável da linha (expansão e `track`). Padrão: `row.id`, depois o índice. */
  readonly rowKey = input<(row: T, index: number) => string>(
    (row, index) => String((row as { id?: unknown } | null)?.id ?? index),
  );
  /** Nome curto da linha para rótulos acessíveis. Padrão: valor da primeira coluna. */
  readonly rowLabel = input<((row: T) => string) | undefined>();
  readonly title = input<string | undefined>();
  readonly description = input<string | undefined>();
  readonly caption = input<string | undefined>();
  readonly state = input<CesContentState>('ready');
  readonly density = input<CesDensity>('compact');
  /** Largura mínima e limite de linhas do texto das colunas; rola na horizontal em vez de espremer. */
  readonly textFit = input<CesDataTableTextFit | undefined>();
  readonly stateTitle = input<string | undefined>();
  readonly stateDescription = input<string | undefined>();
  /** Texto do vazio por busca/filtro. Padrão: "Nenhum registro corresponde …". */
  readonly noResultsTitle = input<string | undefined>();
  /** Vazio sem filtros: ação oferecida (ex.: "Novo veículo"), que emite `emptyAction`. */
  readonly emptyActionLabel = input<string | undefined>();
  readonly emptyActionIcon = input('pi pi-plus');
  /** A página tem filtros diferentes do padrão: o vazio oferece "Ajustar filtros" e "Limpar filtros". */
  readonly filtered = input(false);
  readonly searchable = input(true);
  readonly searchPlaceholder = input('Pesquisar');
  readonly pageSizeOptions = input<readonly number[]>(CES_PAGE_SIZE_OPTIONS);
  /** Chave para lembrar o tamanho de página neste dispositivo (`localStorage`). */
  readonly storageKey = input<string | undefined>();
  /** Quando definido, mostra "Exportar CSV" com este nome de arquivo (sem extensão). */
  readonly exportFileName = input<string | undefined>();
  /** Modo servidor: busca todas as linhas do recorte (até `exportLimit`) para o CSV. */
  readonly exportRows = input<((event: CesLazyLoadEvent, limit: number) => Observable<readonly T[]>) | undefined>();
  readonly exportLimit = input(10000);
  /** Quando definido, cada linha ganha este botão e emite `rowActivate`. */
  readonly rowActionLabel = input<string | undefined>();
  readonly rowActionIcon = input('pi pi-angle-right');
  /**
   * Clique na linha (ou no cartão, no mobile) emite `rowActivate` — abre o detalhe ou a edição.
   * Padrão: ligado quando há ação principal (`rowActionLabel`). Ao ligar sem ela, a tela mantém
   * um botão equivalente nas ações da linha: o clique é atalho de mouse, não o único caminho.
   */
  readonly rowClickable = input<boolean | undefined>();
  /**
   * Quais linhas expandem com o `cesRowDetail` (ex.: só materiais seriados). Sem a função, todas
   * expandem. As demais mantêm a coluna do expansor vazia para o alinhamento não mudar.
   */
  readonly rowExpandable = input<((row: T) => boolean) | undefined>();

  readonly query = model('');
  readonly sort = model<CesSortRule[]>([]);
  readonly pageSize = model(10);
  /** Página atual (base 0). No modo servidor, a tela pode restaurá-la da URL. */
  readonly page = model(0);
  /** Filtros por coluna (E), exibidos como chips removíveis acima da tabela. */
  readonly filters = model<CesColumnFilter[]>([]);
  /** Seleção de linhas por caixa de marcação (ações em lote). A identidade vem de `rowKey`. */
  readonly selectable = input(false);
  /** Linhas selecionadas; sobrevive à troca de página no modo servidor. */
  readonly selection = model<readonly T[]>([]);
  /** Máximo de linhas selecionáveis (ex.: limite da ação em lote no servidor). */
  readonly selectionLimit = input<number | undefined>();

  readonly rowActivate = output<T>();
  readonly lazyLoad = output<CesLazyLoadEvent>();
  /** "Ajustar filtros" no vazio por filtro: a página expande o painel e foca o primeiro campo. */
  readonly adjustFilters = output<void>();
  /** "Limpar filtros" no vazio por filtro: a página volta os filtros ao padrão. */
  readonly clearFilters = output<void>();
  readonly emptyAction = output<void>();

  private readonly cellTemplates = contentChildren(CesCellDirective);
  protected readonly detailTemplate = contentChild(CesRowDetailDirective);
  protected readonly actionsTemplate = contentChild(CesRowActionsDirective);
  /** Há coluna de ações (principal e/ou secundárias). */
  protected readonly hasActions = computed(() => !!this.rowActionLabel() || !!this.actionsTemplate());
  protected readonly clickable = computed(() => this.rowClickable() ?? !!this.rowActionLabel());

  protected readonly compact = cesMediaQuery(CES_COMPACT_VIEWPORT_QUERY);
  protected readonly fit = computed(() => this.textFit() ?? this.textFitPadrao ?? undefined);
  protected readonly server = computed(() => this.mode() === 'server');
  /** Cards no mobile (modo cliente): quantas páginas já foram carregadas por "Carregar mais". */
  protected readonly loadedPages = signal(1);
  /** Cards no mobile (modo servidor): páginas recebidas acumuladas. */
  protected readonly cardRows = signal<readonly T[]>([]);
  protected readonly expanded = signal<ReadonlySet<string>>(new Set());
  protected readonly exporting = signal(false);
  /** Já houve uma carga concluída: recargas mantêm as linhas visíveis em vez do skeleton. */
  private readonly loadedOnce = signal(false);
  private appendNext = false;
  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  /** Largura mínima do cabeçalho com `textFit`: `column.width` prevalece; numéricas usam a metade. */
  protected minWidth(column: CesDataTableColumn<T>): string | null {
    const fit = this.fit();
    if (!fit || column.width) return null;
    return this.isNumeric(column) ? `calc(${fit.minColumnWidth} / 2)` : fit.minColumnWidth;
  }

  protected readonly searchShortcut = computed(() => (this.shortcuts?.enabled() ? '/' : undefined));

  protected readonly templates = computed(() => {
    const mapa = new Map<string, CesCellDirective<T>['template']>();
    for (const diretiva of this.cellTemplates()) mapa.set(diretiva.cesCell(), diretiva.template as never);
    return mapa;
  });

  protected readonly sortableColumns = computed(() =>
    this.columns().filter((column) =>
      this.server() ? !!column.sortField && column.sortable !== false : isSortable(column as CesDataTableColumn<unknown>),
    ),
  );
  protected readonly cardPrimary = computed(() =>
    this.columns().filter((column, index) => (column.priority ?? (index === 0 ? 1 : 2)) === 1),
  );
  protected readonly cardSecondary = computed(() =>
    this.columns().filter((column, index) => (column.priority ?? (index === 0 ? 1 : 2)) === 2),
  );

  /** A visão: filtros + busca + ordenação, todas as páginas (o que o CSV exporta). No servidor, a página recebida. */
  readonly view = computed(() => {
    if (this.server()) return this.rows();
    const columns = this.columns();
    const filtradas = filterRows(this.rows(), this.filters(), columns);
    return sortRows(searchRows(filtradas, this.query(), columns), this.sort(), columns);
  });
  protected readonly filterChips = computed(() =>
    this.filters().map((filter, index) => ({ index, label: filterLabel(filter, this.columns()) })),
  );
  protected readonly total = computed(() =>
    this.server() ? (this.totalRecords() ?? this.rows().length) : this.view().length,
  );
  protected readonly pageCount = computed(() => Math.max(1, Math.ceil(this.total() / this.pageSize())));
  protected readonly safePageIndex = computed(() =>
    this.server() ? this.page() : Math.min(this.page(), this.pageCount() - 1),
  );
  protected readonly pageRows = computed(() => {
    const tamanho = this.pageSize();
    if (this.server()) return this.compact() ? this.cardRows() : this.rows();
    if (this.compact()) return this.view().slice(0, tamanho * this.loadedPages());
    const inicio = this.safePageIndex() * tamanho;
    return this.view().slice(inicio, inicio + tamanho);
  });
  protected readonly rangeLabel = computed(() => {
    const total = this.total();
    if (!total) return '0 registros';
    if (this.compact()) return `${inteiro.format(this.pageRows().length)} de ${inteiro.format(total)}`;
    const inicio = this.safePageIndex() * this.pageSize() + 1;
    const fim = this.server()
      ? Math.min(total, inicio + this.rows().length - 1)
      : Math.min(total, inicio + this.pageSize() - 1);
    return `${inteiro.format(inicio)}–${inteiro.format(fim)} de ${inteiro.format(total)}`;
  });
  /**
   * Páginas numeradas do paginador (base 0): primeira, última e até duas vizinhas da atual;
   * `null` marca o salto ("…").
   */
  protected readonly pageNumbers = computed<(number | null)[]>(() => {
    const total = this.pageCount();
    const atual = this.safePageIndex();
    const paginas = new Set([0, total - 1]);
    for (let p = atual - 2; p <= atual + 2; p++) if (p >= 0 && p < total) paginas.add(p);
    const ordenadas = [...paginas].sort((a, b) => a - b);
    const itens: (number | null)[] = [];
    ordenadas.forEach((p, i) => {
      if (i > 0 && p - ordenadas[i - 1] > 1) itens.push(null);
      itens.push(p);
    });
    return itens;
  });
  protected readonly hasMore = computed(() => this.pageRows().length < this.total());

  /** Há busca, filtro por coluna ou filtro da página aplicados. */
  protected readonly narrowed = computed(
    () => !!this.query().trim() || this.filters().length > 0 || this.filtered(),
  );
  /** Recarga no servidor com linhas anteriores ainda visíveis. */
  protected readonly refreshing = computed(
    () => this.server() && this.state() === 'loading' && this.loadedOnce() && this.rows().length > 0,
  );
  protected readonly shellState = computed<CesContentState>(() => {
    const estado = this.state();
    if (this.refreshing()) return 'ready';
    if (estado !== 'ready') return estado;
    return this.view().length || this.narrowed() ? 'ready' : 'empty';
  });
  protected readonly noResults = computed(
    () => this.shellState() === 'ready' && !this.refreshing() && this.view().length === 0 && this.narrowed(),
  );
  /** Texto de apoio do vazio por busca/filtro: diz o que está restringindo o resultado. */
  protected readonly noResultsDescription = computed(() => {
    const termo = this.query().trim();
    const comFiltros = this.filters().length > 0 || this.filtered();
    if (termo) return `Nada corresponde à busca “${termo}”${comFiltros ? ' com os filtros aplicados' : ''}. Confira a grafia ou tente outro termo.`;
    return 'Nada corresponde aos filtros aplicados. Ajuste ou limpe os filtros para ver mais registros.';
  });
  /** Limpa o que a própria página consegue limpar: busca/filtros da tabela e, se houver, os do painel. */
  protected readonly noResultsClearLabel = computed(() => {
    const local = !!this.query().trim() || this.filters().length > 0;
    if (local && this.filtered()) return 'Limpar busca e filtros';
    if (this.filtered()) return 'Limpar filtros';
    return this.filters().length ? 'Limpar busca e filtros da tabela' : 'Limpar busca';
  });
  protected readonly shellActionLabel = computed(() => {
    const estado = this.shellState();
    if (estado === 'error' && this.server()) return 'Tentar novamente';
    if (estado === 'empty') return this.emptyActionLabel();
    return undefined;
  });
  protected readonly shellActionIcon = computed(() =>
    this.shellState() === 'error' ? 'pi pi-refresh' : this.emptyActionIcon(),
  );
  protected readonly sortSelectValue = computed(() => {
    const regra = this.sort()[0];
    return regra ? `${regra.key}:${regra.direction}` : '';
  });
  protected readonly hasHeading = computed(() => Boolean(this.title() || this.description()));
  protected readonly hasToolbar = computed(
    () =>
      this.searchable() ||
      !!this.exportFileName() ||
      this.filters().length > 0 ||
      (this.compact() && this.sortableColumns().length > 0),
  );
  protected readonly exportLabel = computed(() =>
    this.server() && !this.exportRows() ? 'Exportar página' : 'Exportar CSV',
  );
  protected readonly exportAriaLabel = computed(() =>
    this.server() && !this.exportRows()
      ? `Exportar os ${this.rows().length} registros desta página em CSV`
      : `Exportar ${this.total()} registros em CSV`,
  );
  protected readonly columnCount = computed(
    () => this.columns().length + (this.detailTemplate() ? 1 : 0) + (this.hasActions() ? 1 : 0) + (this.selectable() ? 1 : 0),
  );

  constructor() {
    // Restaura o tamanho de página lembrado (modo cliente; no servidor quem restaura é a tela).
    // Sem lembrança, um tamanho fora de `pageSizeOptions` cai na primeira opção.
    effect(() => {
      if (this.server()) return;
      const opcoes = this.pageSizeOptions();
      const lembrado = readStoredPageSize(this.storageKey(), opcoes);
      untracked(() => {
        if (lembrado) this.pageSize.set(lembrado);
        else if (opcoes.length && !opcoes.includes(this.pageSize())) this.pageSize.set(opcoes[0]);
      });
    });
    // Modo cliente: mudou o recorte, volta para o início.
    effect(() => {
      this.query();
      this.sort();
      this.filters();
      this.rows();
      untracked(() => {
        if (this.server()) return;
        this.page.set(0);
        this.loadedPages.set(1);
      });
    });
    // Modo servidor: cards acumulam as páginas pedidas por "Carregar mais".
    effect(() => {
      const linhas = this.rows();
      untracked(() => {
        if (!this.server()) return;
        if (this.appendNext) {
          this.appendNext = false;
          this.cardRows.update((atuais) => [...atuais, ...linhas]);
        } else {
          this.cardRows.set(linhas);
        }
      });
    });
    effect(() => {
      const estado = this.state();
      if (estado === 'ready' || estado === 'empty') untracked(() => this.loadedOnce.set(true));
    });
    inject(DestroyRef).onDestroy(() => clearTimeout(this.searchTimer));
  }

  ngOnInit(): void {
    // Primeira emissão única, já com o estado restaurado pela tela (página, ordenação, busca).
    if (this.server()) this.emitLoad();
  }

  /** Leva o foco à busca da tabela (atalho `/`). */
  focusSearch(): void {
    this.host.nativeElement.querySelector<HTMLInputElement>('input[type="search"]')?.focus();
  }

  /** Repete a última carga (modo servidor). */
  reload(): void {
    if (this.server()) this.emitLoad();
  }

  protected key(row: T, index: number): string {
    return this.rowKey()(row, index);
  }

  protected readonly selectedKeys = computed(() => new Set(this.selection().map((row) => this.rowKey()(row, -1))));
  protected readonly pageSelection = computed(() => {
    const chaves = this.selectedKeys();
    const linhas = this.pageRows();
    const marcadas = linhas.filter((row, i) => chaves.has(this.key(row, i))).length;
    return { all: linhas.length > 0 && marcadas === linhas.length, some: marcadas > 0 && marcadas < linhas.length };
  });
  protected readonly selectionFull = computed(() => {
    const limite = this.selectionLimit();
    return limite !== undefined && this.selection().length >= limite;
  });

  protected isSelected(row: T, index: number): boolean {
    return this.selectedKeys().has(this.key(row, index));
  }

  /** Clique fora de controles interativos e sem texto selecionado ativa a linha. */
  protected activateFromClick(event: MouseEvent, row: T): void {
    if (!this.clickable()) return;
    const alvo = event.target as Element | null;
    if (alvo?.closest(CONTROLES_INTERATIVOS)) return;
    if (globalThis.getSelection?.()?.toString()) return;
    this.rowActivate.emit(row);
  }

  protected toggleRow(row: T, index: number): void {
    const chave = this.key(row, index);
    if (this.selectedKeys().has(chave)) {
      this.selection.set(this.selection().filter((r) => this.rowKey()(r, -1) !== chave));
    } else if (!this.selectionFull()) {
      this.selection.set([...this.selection(), row]);
    }
  }

  protected togglePage(): void {
    const linhas = this.pageRows();
    if (this.pageSelection().all) {
      const daPagina = new Set(linhas.map((row, i) => this.key(row, i)));
      this.selection.set(this.selection().filter((r) => !daPagina.has(this.rowKey()(r, -1))));
      return;
    }
    const chaves = this.selectedKeys();
    const novas = linhas.filter((row, i) => !chaves.has(this.key(row, i)));
    const limite = this.selectionLimit();
    const cabem = limite === undefined ? novas : novas.slice(0, Math.max(0, limite - this.selection().length));
    this.selection.set([...this.selection(), ...cabem]);
  }

  clearSelection(): void {
    this.selection.set([]);
  }

  protected labelOf(row: T): string {
    const custom = this.rowLabel();
    if (custom) return custom(row);
    const primeira = this.columns()[0];
    return primeira ? formatCell(columnValue(primeira, row), primeira.type) : '';
  }

  protected text(column: CesDataTableColumn<T>, row: T): string {
    if (column.display) return column.display(row);
    return formatCell(columnValue(column, row), column.type);
  }

  protected isNumeric(column: CesDataTableColumn<T>): boolean {
    return ['number', 'integer', 'percent', 'currency'].includes(column.type ?? 'text');
  }

  protected sortState(key: string): { direction: 'asc' | 'desc'; order: number } | null {
    const regras = this.sort();
    const indice = regras.findIndex((regra) => regra.key === key);
    return indice < 0 ? null : { direction: regras[indice].direction, order: indice + 1 };
  }

  protected ariaSort(key: string): 'ascending' | 'descending' | 'none' {
    const estado = this.sortState(key);
    return !estado ? 'none' : estado.direction === 'asc' ? 'ascending' : 'descending';
  }

  protected toggleSort(key: string, event: MouseEvent | KeyboardEvent): void {
    this.sort.set(nextSortRules(this.sort(), key, event.shiftKey));
    this.restart();
  }

  protected onSortSelect(value: string): void {
    if (!value) {
      this.sort.set([]);
    } else {
      const [key, direction] = value.split(':') as [string, 'asc' | 'desc'];
      this.sort.set([{ key, direction }]);
    }
    this.restart();
  }

  protected onSearchChanged(): void {
    if (!this.server()) return;
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.restart(), SEARCH_DEBOUNCE_MS);
  }

  protected setPageSize(value: string | number): void {
    const tamanho = Number(value);
    if (!this.pageSizeOptions().includes(tamanho)) return;
    this.pageSize.set(tamanho);
    this.page.set(0);
    this.loadedPages.set(1);
    const chave = this.storageKey();
    if (chave) {
      try {
        localStorage.setItem(`ces.table.${chave}.rows`, String(tamanho));
      } catch {
        // preferência apenas em memória
      }
    }
    if (this.server()) this.emitLoad();
  }

  protected goTo(page: number): void {
    this.page.set(Math.max(0, Math.min(page, this.pageCount() - 1)));
    if (this.server()) this.emitLoad();
  }

  protected loadMore(): void {
    if (this.server()) {
      this.appendNext = true;
      this.page.update((pagina) => pagina + 1);
      this.emitLoad();
      return;
    }
    this.loadedPages.update((paginas) => paginas + 1);
  }

  protected canExpand(row: T): boolean {
    if (!this.detailTemplate()) return false;
    const expansivel = this.rowExpandable();
    return expansivel ? expansivel(row) : true;
  }

  protected isExpanded(row: T, index: number): boolean {
    return this.expanded().has(this.key(row, index));
  }

  protected toggleExpanded(row: T, index: number): void {
    const chave = this.key(row, index);
    this.expanded.update((atual) => {
      const proximo = new Set(atual);
      if (proximo.has(chave)) proximo.delete(chave);
      else proximo.add(chave);
      return proximo;
    });
  }

  protected clearSearch(): void {
    this.query.set('');
    this.filters.set([]);
    this.restart();
  }

  protected removeFilter(index: number): void {
    this.filters.update((atuais) => atuais.filter((_, i) => i !== index));
    this.restart();
  }

  protected clearNarrowing(): void {
    const local = !!this.query().trim() || this.filters().length > 0;
    if (this.filtered()) this.clearFilters.emit();
    if (local) this.clearSearch();
  }

  protected onShellAction(): void {
    if (this.shellState() === 'error') this.reload();
    else if (this.shellState() === 'empty') this.emptyAction.emit();
  }

  /** Baixa a visão atual em CSV para Excel pt-BR. No servidor, usa `exportRows` ou a página exibida. */
  async exportCsv(fileName = this.exportFileName() ?? 'dados'): Promise<void> {
    if (!this.server()) {
      this.download(this.view(), fileName);
      return;
    }
    const provedor = this.exportRows();
    if (!provedor) {
      this.download(this.rows(), fileName);
      return;
    }
    const limite = this.exportLimit();
    const total = this.total();
    if (total > limite) {
      const continuar = await this.injector.get(CesConfirmationService).confirm({
        title: 'Exportação limitada',
        message: `O recorte tem ${inteiro.format(total)} registros e só os ${inteiro.format(limite)} primeiros serão exportados. Refine os filtros para exportar tudo ou continue.`,
        confirmLabel: `Exportar ${inteiro.format(limite)}`,
        cancelLabel: 'Refinar filtros',
        icon: 'pi pi-download',
      });
      if (!continuar) return;
    }
    this.exporting.set(true);
    provedor(this.currentEvent(), limite).subscribe({
      next: (linhas) => this.download(linhas.slice(0, limite), fileName),
      error: () => {
        this.exporting.set(false);
        this.injector.get(NotificationService).error('Não foi possível gerar o CSV. Tente novamente.');
      },
      complete: () => this.exporting.set(false),
    });
  }

  private download(rows: readonly T[], fileName: string): void {
    const view = this.document.defaultView;
    if (!view?.URL?.createObjectURL) return;
    const blob = new Blob([toCsv(rows, this.columns())], { type: 'text/csv;charset=utf-8' });
    const url = view.URL.createObjectURL(blob);
    const link = this.document.createElement('a');
    link.href = url;
    link.download = `${fileName}.csv`;
    link.click();
    view.URL.revokeObjectURL(url);
  }

  /** Mudou ordenação, busca ou filtro: volta à primeira página e (no servidor) recarrega. */
  private restart(): void {
    clearTimeout(this.searchTimer);
    this.page.set(0);
    this.loadedPages.set(1);
    if (this.server()) this.emitLoad();
  }

  private currentEvent(): CesLazyLoadEvent {
    const colunas = this.columns();
    return {
      page: this.page(),
      size: this.pageSize(),
      sort: this.sort()
        .map((regra) => {
          const campo = colunas.find((coluna) => coluna.key === regra.key)?.sortField;
          return campo ? { key: campo, direction: regra.direction } : null;
        })
        .filter((regra): regra is CesSortRule => !!regra),
      query: this.query().trim(),
      filters: this.filters(),
    };
  }

  private emitLoad(): void {
    this.lazyLoad.emit(this.currentEvent());
  }
}
