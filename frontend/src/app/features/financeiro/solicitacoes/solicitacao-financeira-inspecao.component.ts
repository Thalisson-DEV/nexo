import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesStatusComponent,
  CesTextInputComponent,
  CesTone
} from '@ces/ui';
import {
  InspecaoContexto,
  SolicitacaoFinanceira,
  SolicitacaoFinanceiraFilter,
  StatusSolicitacao,
  TipoSolicitacao
} from '../../../core/models/financeiro.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { nomeSolicitante } from './solicitacao-financeira.utils';

type OrdenacaoSolicitacao = 'RECENTES' | 'ANTIGAS';
type EscopoTipoSolicitacao = TipoSolicitacao | 'AMBOS';

interface StatusFilterChip {
  label: string;
  value: StatusSolicitacao | null;
  tone?: CesTone;
  icon?: string;
}

const STATUS_OPTIONS: readonly { label: string; value: StatusSolicitacao }[] = [
  { label: 'Aguardando aprovação', value: 'SOLICITADO' },
  { label: 'Rascunho', value: 'RASCUNHO' },
  { label: 'Aprovado', value: 'APROVADO' },
  { label: 'Rejeitado', value: 'REJEITADO' },
  { label: 'Cancelado', value: 'CANCELADO' }
];

const QUICK_STATUS_CHIPS: readonly StatusFilterChip[] = [
  { label: 'Todas', value: null, icon: 'pi pi-list' },
  { label: 'Aguardando', value: 'SOLICITADO', tone: 'warning', icon: 'pi pi-clock' },
  { label: 'Aprovadas', value: 'APROVADO', tone: 'success', icon: 'pi pi-check-circle' },
  { label: 'Rascunhos', value: 'RASCUNHO', tone: 'neutral', icon: 'pi pi-file-edit' },
  { label: 'Rejeitadas', value: 'REJEITADO', tone: 'danger', icon: 'pi pi-times-circle' }
];

@Component({
  selector: 'app-solicitacao-financeira-inspecao',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    DatePipe,
    CesAsyncStateComponent,
    CesStatusComponent,
    CesTextInputComponent
  ],
  styleUrl: './solicitacao-financeira-inspecao.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (aberta() && contexto(); as ctx) {
      <aside
        class="finance-inspect-sidebar"
        role="dialog"
        aria-modal="true"
        [attr.aria-label]="'Histórico de ' + ctx.label"
      >
        <div class="finance-inspect-container">
          <!-- ==================================================================
               CABEÇALHO DO CONTEXTO DE INSPEÇÃO
               ================================================================== -->
          <header class="finance-inspect-header">
            <div class="finance-inspect-header__title-group">
              <span class="finance-inspect-header__icon">
                <i [class]="ctx.icone" aria-hidden="true"></i>
              </span>
              <div class="finance-inspect-header__text">
                <span class="finance-inspect-header__eyebrow">{{ ctx.subtitulo ?? 'Histórico Cruzado' }}</span>
                <h2 [title]="ctx.label">{{ ctx.label }}</h2>
              </div>
            </div>

            <div class="finance-inspect-header__actions">
              <button
                type="button"
                class="finance-inspect-header__close-btn"
                (click)="fechar()"
                aria-label="Fechar painel de histórico (Esc)"
                title="Fechar (Esc)"
              >
                <i class="pi pi-times" aria-hidden="true"></i>
              </button>
            </div>
          </header>

          <!-- ==================================================================
               BARRA DE FILTROS DO CONTEXTO
               ================================================================== -->
          <div class="finance-inspect-filters">
            <!-- Seletor Segmentado de Tipo -->
            <div class="finance-inspect-type-selector" role="group" aria-label="Filtrar por tipo de solicitação">
              <button
                type="button"
                class="finance-inspect-type-btn"
                [class.finance-inspect-type-btn--active]="tipoSelecionado() === 'AMBOS'"
                (click)="alterarTipo('AMBOS')"
              >
                <span>Todas</span>
              </button>
              <button
                type="button"
                class="finance-inspect-type-btn"
                [class.finance-inspect-type-btn--active]="tipoSelecionado() === 'COMPRA'"
                (click)="alterarTipo('COMPRA')"
              >
                <i class="pi pi-shopping-cart" aria-hidden="true"></i>
                <span>Compras</span>
              </button>
              <button
                type="button"
                class="finance-inspect-type-btn"
                [class.finance-inspect-type-btn--active]="tipoSelecionado() === 'PAGAMENTO'"
                (click)="alterarTipo('PAGAMENTO')"
              >
                <i class="pi pi-credit-card" aria-hidden="true"></i>
                <span>Pagamentos</span>
              </button>
            </div>

            <!-- Busca Rápida -->
            <div class="finance-inspect-search">
              <ces-text-input
                label="Buscar neste histórico"
                inputId="solicitacao-inspecao-busca"
                placeholder="Busca rápida..."
                [value]="busca()"
                (valueChange)="alterarBusca($event)"
              ></ces-text-input>
            </div>

            <!-- Chips Rápidos de Status -->
            <div class="finance-inspect-chips" role="group" aria-label="Filtro rápido por status">
              @for (chip of quickStatusChips; track chip.label) {
                <button
                  type="button"
                  class="finance-inspect-chip"
                  [class.finance-inspect-chip--active]="status() === chip.value"
                  (click)="alterarStatus(chip.value)"
                >
                  @if (chip.icon) {
                    <i [class]="chip.icon" aria-hidden="true"></i>
                  }
                  <span>{{ chip.label }}</span>
                </button>
              }
            </div>

            <!-- Controles Secundários: Ordenação -->
            <div class="finance-inspect-controls-row">
              <div class="finance-inspect-sort-group" role="group" aria-label="Ordenação">
                <span class="finance-inspect-control-label" title="Ordenar por">
                  <i class="pi pi-sort-alt" aria-hidden="true"></i>
                </span>
                <button
                  type="button"
                  class="finance-inspect-control-btn"
                  [class.finance-inspect-control-btn--active]="ordenacao() === 'RECENTES'"
                  (click)="alterarOrdenacao('RECENTES')"
                >
                  Recentes
                </button>
                <button
                  type="button"
                  class="finance-inspect-control-btn"
                  [class.finance-inspect-control-btn--active]="ordenacao() === 'ANTIGAS'"
                  (click)="alterarOrdenacao('ANTIGAS')"
                >
                  Antigas
                </button>
              </div>

              <div class="finance-inspect-count">
                <i class="pi pi-list" aria-hidden="true"></i>
                <span>{{ totalLabel() }}</span>
              </div>
            </div>
          </div>

          <!-- ==================================================================
               LISTA DE RESULTADOS DO HISTÓRICO CONTEXTUAL
               ================================================================== -->
          <div class="finance-inspect-results" #scrollContainer tabIndex="-1">
            <!-- Skeletons durante carregamento inicial -->
            @if (carregando() && !carregado()) {
              <div class="finance-inspect-skeletons" aria-hidden="true">
                @for (i of [1, 2, 3, 4, 5]; track i) {
                  <div class="finance-inspect-skeleton-card">
                    <div class="flex justify-between items-center">
                      <div class="finance-inspect-skeleton-line w-28 h-3.5"></div>
                      <div class="finance-inspect-skeleton-line w-20 h-3.5"></div>
                    </div>
                    <div class="finance-inspect-skeleton-line w-full h-4 mt-2"></div>
                    <div class="flex justify-between items-center mt-2">
                      <div class="finance-inspect-skeleton-line w-32 h-3"></div>
                      <div class="finance-inspect-skeleton-line w-20 h-3"></div>
                    </div>
                  </div>
                }
              </div>
            } @else if (erro() && itensVisiveis().length === 0) {
              <div class="p-4">
                <ces-async-state
                  state="error"
                  [compact]="true"
                  title="Não foi possível carregar o histórico"
                  [description]="erro()!"
                  actionLabel="Tentar novamente"
                  actionIcon="pi pi-refresh"
                  (action)="carregar()"
                ></ces-async-state>
              </div>
            } @else if (itensVisiveis().length === 0) {
              <div class="p-4">
                <ces-async-state
                  state="empty"
                  [compact]="true"
                  title="Nenhuma solicitação encontrada"
                  description="Não há registros correspondentes aos filtros selecionados neste contexto."
                  actionLabel="Limpar filtros"
                  actionIcon="pi pi-filter-slash"
                  (action)="limparFiltros()"
                ></ces-async-state>
              </div>
            } @else {
              <!-- Indicador de Carregamento em Segundo Plano -->
              @if (carregando()) {
                <div class="finance-inspect-refresh-indicator" role="status">
                  <i class="pi pi-spinner pi-spin text-xs" aria-hidden="true"></i>
                  <span>Atualizando histórico...</span>
                </div>
              }

              <!-- Lista de Cards Compactos de 3 Linhas -->
              <ul class="finance-inspect-list" role="list">
                @for (item of itensVisiveis(); track item.id) {
                  <li>
                    <button
                      type="button"
                      class="finance-inspect-item"
                      [class.finance-inspect-item--current]="item.id === atual()?.id"
                      [attr.aria-current]="item.id === atual()?.id ? 'page' : null"
                      (click)="selecionar(item)"
                    >
                      <!-- Linha 1: Top (Número + Tipo Pill + Valor à direita) -->
                      <div class="finance-inspect-item__row-top">
                        <div class="flex items-center gap-2 min-w-0">
                          <span class="finance-inspect-item__number">#{{ item.numero }}</span>
                          <span
                            class="finance-inspect-item__type-pill"
                            [class.finance-inspect-item__type-pill--compra]="item.formulario.tipoSolicitacao === 'COMPRA'"
                            [class.finance-inspect-item__type-pill--pagamento]="item.formulario.tipoSolicitacao === 'PAGAMENTO'"
                          >
                            <i
                              [class]="item.formulario.tipoSolicitacao === 'COMPRA' ? 'pi pi-shopping-cart' : 'pi pi-credit-card'"
                              aria-hidden="true"
                            ></i>
                            <span>{{ item.formulario.tipoSolicitacao === 'COMPRA' ? 'Compra' : 'Pagamento' }}</span>
                          </span>
                        </div>

                        <span class="finance-inspect-item__value">
                          {{ item.valor | currency:'BRL' }}
                        </span>
                      </div>

                      <!-- Linha 2: Título / Resumo Descritivo -->
                      <div class="finance-inspect-item__title">
                        {{ tituloItem(item) }}
                      </div>

                      <!-- Linha 3: Bottom (Status + Solicitante + Data + Atual) -->
                      <div class="finance-inspect-item__row-bottom">
                        <ces-status [tone]="statusTone(item.status)" emphasis="soft">
                          {{ statusLabel(item.status) }}
                        </ces-status>

                        <div class="finance-inspect-item__meta-group">
                          <span class="finance-inspect-item__requester" [title]="'Solicitante: ' + nomeSolicitante(item)">
                            <i class="pi pi-user" aria-hidden="true"></i>
                            <span>{{ nomeSolicitante(item) }}</span>
                          </span>

                          <span class="finance-inspect-item__dot-sep" aria-hidden="true">·</span>

                          <time
                            class="finance-inspect-item__date"
                            [attr.datetime]="item.dataCriacao"
                            [title]="'Data: ' + (item.dataCriacao | date:'dd/MM/yyyy HH:mm')"
                          >
                            <i class="pi pi-calendar" aria-hidden="true"></i>
                            <span>{{ item.dataCriacao | date:'dd/MM/yy' }}</span>
                          </time>
                        </div>

                        @if (item.id === atual()?.id) {
                          <span class="finance-inspect-item__active-badge" title="Solicitação aberta atualmente">
                            <i class="pi pi-eye" aria-hidden="true"></i>
                            <span>Atual</span>
                          </span>
                        }
                      </div>
                    </button>
                  </li>
                }
              </ul>
            }
          </div>
        </div>
      </aside>
    }
  `
})
export class SolicitacaoFinanceiraInspecaoComponent {
  private readonly service = inject(FinanceiroService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly elementRef = inject(ElementRef);
  private searchTimer?: ReturnType<typeof setTimeout>;
  private requestId = 0;

  /** Contexto ativo de inspeção (ex: solicitante, base, veículo, fornecedor) */
  readonly contexto = model<InspecaoContexto | null>(null);

  /** Controle de visibilidade da sidebar direita */
  readonly aberta = model(false);

  /** Solicitação exibida atualmente na página de detalhe */
  readonly atual = input<SolicitacaoFinanceira | null>(null);

  /** Evento emitido quando o usuário clica em um item do histórico */
  readonly selecionada = output<SolicitacaoFinanceira>();

  // Estado dos filtros internos
  protected readonly tipoSelecionado = signal<EscopoTipoSolicitacao>('AMBOS');
  protected readonly busca = signal('');
  protected readonly status = signal<StatusSolicitacao | null>(null);
  protected readonly ordenacao = signal<OrdenacaoSolicitacao>('RECENTES');

  // Estado dos dados
  protected readonly itens = signal<SolicitacaoFinanceira[]>([]);
  protected readonly total = signal(0);
  protected readonly carregando = signal(false);
  protected readonly carregado = signal(false);
  protected readonly erro = signal<string | null>(null);

  // Utilitários de template
  protected readonly nomeSolicitante = nomeSolicitante;
  protected readonly statusOptions = STATUS_OPTIONS;
  protected readonly quickStatusChips = QUICK_STATUS_CHIPS;

  protected readonly itensVisiveis = computed(() => this.itens());

  readonly totalLabel = computed(() => {
    const qtd = this.total();
    return qtd === 1 ? '1 solicitação' : `${qtd} solicitações`;
  });

  constructor() {
    // Carrega quando a sidebar abre ou quando o contexto muda
    effect(() => {
      const isAberta = this.aberta();
      const ctx = this.contexto();
      if (isAberta && ctx) {
        this.carregar();
      } else if (!isAberta) {
        this.itens.set([]);
        this.total.set(0);
        this.carregado.set(false);
        this.erro.set(null);
      }
    });
  }

  @HostListener('document:keydown.escape', ['$event'])
  protected onEscape(event: Event): void {
    if (this.aberta()) {
      event.preventDefault();
      this.fechar();
    }
  }

  @HostListener('document:pointerdown', ['$event'])
  protected onDocumentClick(event: PointerEvent): void {
    if (!this.aberta()) return;
    const target = event.target as HTMLElement | null;
    if (!target) return;

    if (this.elementRef.nativeElement.contains(target)) return;

    if (
      target.closest(
        '.p-overlay, .p-select-overlay, .p-datepicker-panel, .p-dialog-mask, .p-tooltip, .cdk-overlay-container, .p-component-overlay'
      )
    ) {
      return;
    }

    this.fechar();
  }

  protected fechar(): void {
    this.aberta.set(false);
  }

  protected alterarTipo(tipo: EscopoTipoSolicitacao): void {
    if (this.tipoSelecionado() === tipo) return;
    this.tipoSelecionado.set(tipo);
    this.carregar();
  }

  protected alterarBusca(texto: string): void {
    this.busca.set(texto);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.carregar();
    }, 280);
  }

  protected alterarStatus(status: StatusSolicitacao | null): void {
    if (this.status() === status) return;
    this.status.set(status);
    this.carregar();
  }

  protected alterarOrdenacao(ord: OrdenacaoSolicitacao): void {
    if (this.ordenacao() === ord) return;
    this.ordenacao.set(ord);
    this.carregar();
  }

  protected limparFiltros(): void {
    this.busca.set('');
    this.status.set(null);
    this.tipoSelecionado.set('AMBOS');
    this.ordenacao.set('RECENTES');
    this.carregar();
  }

  protected carregar(): void {
    const ctx = this.contexto();
    if (!ctx) return;

    const currentRequest = ++this.requestId;
    this.carregando.set(true);
    this.erro.set(null);

    const filtros: SolicitacaoFinanceiraFilter = {
      ...ctx.filtros,
      buscaGlobal: this.busca().trim() || ctx.filtros.buscaGlobal || null,
      status: this.status(),
      page: 0,
      size: 40,
      sort: this.sortParam()
    };

    this.service
      .listarSolicitacoes(this.tipoSelecionado(), filtros)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          if (currentRequest !== this.requestId) return;
          this.itens.set(response.content);
          this.total.set(response.page.totalElements);
          this.carregado.set(true);
          this.carregando.set(false);
        },
        error: (error: unknown) => {
          if (currentRequest !== this.requestId) return;
          this.erro.set(getErrorMessage(error));
          this.carregado.set(true);
          this.carregando.set(false);
        }
      });
  }

  protected selecionar(item: SolicitacaoFinanceira): void {
    this.selecionada.emit(item);
  }

  protected tituloItem(item: SolicitacaoFinanceira): string {
    return item.valores['descricao']?.exibicao || item.formulario.titulo;
  }

  protected statusLabel(status: StatusSolicitacao): string {
    return STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status;
  }

  protected statusTone(status: StatusSolicitacao): CesTone {
    return {
      RASCUNHO: 'neutral',
      SOLICITADO: 'warning',
      APROVADO: 'success',
      REJEITADO: 'danger',
      CANCELADO: 'neutral'
    }[status] as CesTone;
  }

  private sortParam(): string {
    return {
      RECENTES: 'dataCriacao,desc',
      ANTIGAS: 'dataCriacao,asc'
    }[this.ordenacao()];
  }
}
