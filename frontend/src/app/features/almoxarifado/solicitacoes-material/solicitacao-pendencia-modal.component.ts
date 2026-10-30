import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import {
  CesButtonComponent,
  CesDialogShellComponent,
  CesSelectComponent,
  CesStatusComponent,
  CesTextareaComponent,
  NotificationService,
} from '@ces/ui';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { ItemPendenciaRequest, MotivoPendenciaResponse } from '../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../core/models/shared.models';

export interface ItemPendenciaModal {
  id: string;
  codigo?: string;
  descricao?: string;
  quantidade?: number | null;
  material?: {
    codigo: string;
    descricao: string;
  };
  motivoPendencia?: MotivoPendenciaResponse | null;
  justificativaPendencia?: string | null;
}

export interface ItemPendenciaConfig {
  idMotivo: string | null;
  justificativa: string;
}

@Component({
  selector: 'app-solicitacao-pendencia-modal',
  standalone: true,
  imports: [
    CesDialogShellComponent,
    CesButtonComponent,
    CesSelectComponent,
    CesTextareaComponent,
    CesStatusComponent,
  ],
  template: `
    <ces-dialog-shell
      [title]="modalTitle()"
      [description]="modalDescription()"
      [size]="modalSize()"
      [(visible)]="visible"
    >
      <div class="flex flex-col gap-4 py-1">
        @if (motivoAtual(); as motivo) {
          <div class="rounded-xl border border-[var(--ces-warning)] bg-[var(--ces-warning-soft)] p-3 flex flex-col gap-2">
            <div class="flex items-center justify-between gap-2">
              <span class="text-xs font-bold text-[var(--ces-warning-text)] uppercase tracking-wider flex items-center gap-1.5">
                <i class="pi pi-exclamation-triangle text-[var(--ces-warning-text)]"></i>
                {{ idItem() ? 'Pendência Atual do Material' : 'Pendência Atual da Solicitação' }}
              </span>
              <ces-status tone="warning">{{ motivo.nome }}</ces-status>
            </div>
            @if (justificativaAtual()) {
              <p class="text-xs text-[var(--ces-foreground)] italic bg-[var(--ces-surface)] p-2 rounded-lg border border-[var(--ces-border)]">
                "{{ justificativaAtual() }}"
              </p>
            }
            @if (podeResolver()) {
              <div class="flex justify-end pt-1">
                <ces-button
                  [label]="idItem() ? 'Resolver pendência do material' : 'Resolver pendência geral'"
                  icon="pi pi-check"
                  variant="ghost"
                  size="sm"
                  [loading]="resolvendo()"
                  (pressed)="resolver()"
                />
              </div>
            }
          </div>
        }

        @if (idItem(); as itemId) {
          <!-- Modo Material Individual -->
          <div class="flex flex-col gap-3">
            <ces-select
              label="Motivo da pendência"
              placeholder="Selecione o motivo..."
              [options]="opcoesMotivos()"
              [value]="getItemConfig(itemId).idMotivo"
              (valueChange)="setItemMotivo(itemId, $event)"
              [loading]="carregandoMotivos()"
              [required]="true"
              [error]="getItemMotivoErro(itemId)"
            />

            <ces-textarea
              label="Descrição da pendência"
              placeholder="Descreva detalhadamente a pendência (mínimo de 5 caracteres)..."
              [rows]="3"
              [maxlength]="2000"
              [value]="getItemConfig(itemId).justificativa"
              (valueChange)="setItemJustificativa(itemId, $event)"
              [required]="true"
              [error]="getItemJustificativaErro(itemId)"
              description="Obrigatório, mínimo de 5 caracteres."
            />
          </div>
        } @else {
          <!-- Seletor de Escopo: Geral vs Por Material -->
          <div class="flex flex-col gap-1.5">
            <span class="text-xs font-semibold text-[var(--ces-foreground)]">
              Escopo da Pendência
            </span>
            <div class="grid grid-cols-2 gap-2">
              <button
                type="button"
                (click)="setEscopo('GERAL')"
                class="flex flex-col items-start gap-1 p-3 rounded-xl border text-left transition-all cursor-pointer"
                [class]="escopo() === 'GERAL'
                  ? 'border-[var(--ces-brand)] bg-[var(--ces-brand-soft)] text-[var(--ces-foreground)] shadow-2xs'
                  : 'border-[var(--ces-border)] bg-[var(--ces-surface)] hover:bg-[var(--ces-surface-subtle)] text-[var(--ces-foreground-muted)]'"
              >
                <div class="flex items-center gap-2">
                  <i class="pi pi-file text-sm" [class.text-[var(--ces-brand)]]="escopo() === 'GERAL'"></i>
                  <span class="text-xs font-bold">Geral</span>
                </div>
                <span class="text-[11px] text-[var(--ces-foreground-muted)]">
                  Para toda a solicitação
                </span>
              </button>

              <button
                type="button"
                (click)="setEscopo('MATERIAL')"
                class="flex flex-col items-start gap-1 p-3 rounded-xl border text-left transition-all cursor-pointer"
                [class]="escopo() === 'MATERIAL'
                  ? 'border-[var(--ces-brand)] bg-[var(--ces-brand-soft)] text-[var(--ces-foreground)] shadow-2xs'
                  : 'border-[var(--ces-border)] bg-[var(--ces-surface)] hover:bg-[var(--ces-surface-subtle)] text-[var(--ces-foreground-muted)]'"
              >
                <div class="flex items-center gap-2">
                  <i class="pi pi-box text-sm" [class.text-[var(--ces-brand)]]="escopo() === 'MATERIAL'"></i>
                  <span class="text-xs font-bold">Por Material</span>
                </div>
                <span class="text-[11px] text-[var(--ces-foreground-muted)]">
                  Configurar por item da solicitação
                </span>
              </button>
            </div>
          </div>

          <!-- Seção: Pendência Geral -->
          @if (escopo() === 'GERAL') {
            <div class="flex flex-col gap-3">
              <ces-select
                label="Motivo da pendência"
                placeholder="Selecione o motivo..."
                [options]="opcoesMotivos()"
                [(value)]="idMotivo"
                [loading]="carregandoMotivos()"
                [required]="true"
              />

              <ces-textarea
                label="Descrição da pendência"
                placeholder="Descreva detalhadamente a pendência (mínimo de 5 caracteres)..."
                [rows]="3"
                [maxlength]="2000"
                [(value)]="descricao"
                [required]="true"
                [error]="descricaoErro()"
                description="Obrigatório, mínimo de 5 caracteres."
              />
            </div>
          }

        <!-- Seção: Por Material (Itens com suas respectivas pendências) -->
        @if (escopo() === 'MATERIAL') {
          <div class="flex flex-col gap-3 p-3.5 rounded-xl border border-[var(--ces-border)] bg-[var(--ces-surface-subtle)]">
            <div class="flex items-center justify-between gap-2 flex-wrap">
              <span class="text-xs font-bold text-[var(--ces-foreground)]">
                Materiais da Solicitação ({{ itensSelecionados().size }} com pendência)
              </span>
              @if (listaItens().length > 0) {
                <ces-button
                  [label]="todosSelecionados() ? 'Desmarcar todos' : 'Selecionar todos'"
                  variant="ghost"
                  size="sm"
                  (pressed)="toggleTodos()"
                />
              }
            </div>

            <!-- Preenchimento em lote opcional -->
            @if (listaItens().length > 1) {
              <div class="p-3 rounded-lg bg-[var(--ces-surface)] border border-[var(--ces-border)] flex flex-col gap-2.5">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-xs font-semibold text-[var(--ces-foreground)] flex items-center gap-1.5">
                    <i class="pi pi-bolt text-[var(--ces-warning-text)]"></i>
                    Aplicar mesmo motivo aos selecionados (opcional)
                  </span>
                  @if (itensSelecionados().size > 0 && idMotivoLote()) {
                    <ces-button
                      label="Aplicar aos marcados"
                      icon="pi pi-arrow-down"
                      variant="ghost"
                      size="sm"
                      (pressed)="aplicarLoteAosSelecionados()"
                    />
                  }
                </div>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
                  <ces-select
                    label="Motivo para lote"
                    placeholder="Selecione para preencher selecionados..."
                    [options]="opcoesMotivos()"
                    [(value)]="idMotivoLote"
                  />
                  <ces-textarea
                    label="Justificativa para lote"
                    placeholder="Justificativa padrão para os selecionados..."
                    [rows]="1"
                    [maxlength]="2000"
                    [(value)]="descricaoLote"
                  />
                </div>
              </div>
            }

            @if (carregandoItens()) {
              <div class="py-4 text-center text-xs text-[var(--ces-foreground-muted)] flex items-center justify-center gap-2">
                <i class="pi pi-spin pi-spinner"></i>
                <span>Carregando materiais da solicitação...</span>
              </div>
            } @else if (listaItens().length === 0) {
              <div class="py-3 text-center text-xs text-[var(--ces-foreground-muted)]">
                Nenhum material encontrado nesta solicitação.
              </div>
            } @else {
              <div class="max-h-96 overflow-y-auto flex flex-col gap-2.5 pr-1">
                @for (item of listaItens(); track item.id) {
                  <div
                    class="p-3 rounded-xl border transition-colors flex flex-col gap-2"
                    [class]="itensSelecionados().has(item.id)
                      ? 'border-[var(--ces-brand)] bg-[var(--ces-surface)] shadow-2xs'
                      : 'border-[var(--ces-border)] bg-[var(--ces-surface)] opacity-90'"
                  >
                    <div class="flex items-center justify-between gap-2">
                      <label class="flex items-center gap-2.5 cursor-pointer min-w-0 flex-1">
                        <input
                          type="checkbox"
                          class="rounded border-[var(--ces-border-strong)] text-[var(--ces-brand)] focus:ring-0 cursor-pointer shrink-0"
                          [checked]="itensSelecionados().has(item.id)"
                          (change)="toggleItem(item.id)"
                        />
                        <div class="flex flex-col min-w-0 flex-1">
                          <div class="flex items-center gap-1.5 flex-wrap">
                            <span class="font-bold text-[var(--ces-foreground)] text-xs">{{ item.codigo }}</span>
                            <span class="text-[var(--ces-foreground-muted)] dark:text-[var(--ces-foreground-muted)] text-xs">—</span>
                            <span class="truncate text-[var(--ces-foreground)] text-xs font-medium">{{ item.descricao }}</span>
                            @if (item.quantidade !== null && item.quantidade !== undefined) {
                              <span class="text-[11px] text-[var(--ces-foreground-muted)]">({{ item.quantidade }} un)</span>
                            }
                          </div>
                        </div>
                      </label>

                      @if (item.motivoPendencia) {
                        <div class="flex items-center gap-1.5 shrink-0">
                          <ces-status tone="warning">{{ item.motivoPendencia.nome }}</ces-status>
                          @if (podeResolver()) {
                            <ces-button
                              label="Resolver"
                              variant="ghost"
                              size="sm"
                              [loading]="resolvendoItem() === item.id"
                              (pressed)="resolverItem(item.id)"
                            />
                          }
                        </div>
                      }
                    </div>

                    <!-- Campos individuais da pendência do material (visíveis quando selecionado) -->
                    @if (itensSelecionados().has(item.id)) {
                      <div class="pt-2.5 border-t border-[var(--ces-border)] grid grid-cols-1 md:grid-cols-2 gap-2">
                        <ces-select
                          label="Motivo deste item"
                          placeholder="Selecione o motivo..."
                          [options]="opcoesMotivos()"
                          [value]="getItemConfig(item.id).idMotivo"
                          (valueChange)="setItemMotivo(item.id, $event)"
                          [required]="true"
                          [error]="getItemMotivoErro(item.id)"
                        />
                        <ces-textarea
                          label="Justificativa deste item"
                          placeholder="Mínimo 5 caracteres..."
                          [rows]="1"
                          [maxlength]="2000"
                          [value]="getItemConfig(item.id).justificativa"
                          (valueChange)="setItemJustificativa(item.id, $event)"
                          [required]="true"
                          [error]="getItemJustificativaErro(item.id)"
                        />
                      </div>
                    }
                  </div>
                }
              </div>
            }

            @if (touched() && itensSelecionados().size === 0) {
              <span class="text-[11px] text-[var(--ces-danger-text)] font-medium">
                Selecione ao menos um material para registrar pendência.
              </span>
            }
          </div>
        }
      }
      </div>

      <div dialog-footer class="flex items-center justify-end gap-2 w-full">
        <ces-button
          label="Cancelar"
          variant="ghost"
          size="sm"
          (pressed)="fechar()"
        />
        <ces-button
          label="Salvar"
          icon="pi pi-check"
          variant="primary"
          size="sm"
          [loading]="salvando()"
          [disabled]="!formularioValido() || salvando()"
          (pressed)="salvar()"
        />
      </div>
    </ces-dialog-shell>
  `,
})
export class SolicitacaoPendenciaModalComponent {
  readonly visible = model<boolean>(false);
  readonly idSolicitacao = input<string>('');
  readonly idItem = input<string | null>(null);
  readonly itemLabel = input<string | null>(null);
  readonly motivoAtual = input<MotivoPendenciaResponse | null>(null);
  readonly justificativaAtual = input<string | null>(null);
  readonly podeResolver = input<boolean>(true);
  readonly itens = input<any[]>([]);

  readonly pendenciaSalva = output<void>();
  readonly pendenciaResolvida = output<void>();

  private readonly service = inject(AlmoxarifadoService);
  private readonly notifications = inject(NotificationService);

  readonly escopo = signal<'GERAL' | 'MATERIAL'>('GERAL');
  readonly itensSelecionados = signal<Set<string>>(new Set());
  readonly itensConfig = signal<Record<string, ItemPendenciaConfig>>({});
  readonly itensCarregados = signal<any[]>([]);
  readonly carregandoItens = signal(false);

  readonly motivos = signal<MotivoPendenciaResponse[]>([]);
  readonly carregandoMotivos = signal(false);
  readonly salvando = signal(false);
  readonly resolvendo = signal(false);
  readonly resolvendoItem = signal<string | null>(null);

  // Campos para Escopo Geral
  readonly idMotivo = signal<string | null>(null);
  readonly descricao = signal<string>('');
  readonly touched = signal(false);

  // Campos auxiliares para preenchimento em lote no Escopo Material
  readonly idMotivoLote = signal<string | null>(null);
  readonly descricaoLote = signal<string>('');

  readonly itensResolvidosLocais = signal<Set<string>>(new Set());

  readonly listaItens = computed(() => {
    const parentItens = this.itens();
    const source = parentItens && parentItens.length > 0 ? parentItens : this.itensCarregados();
    const resolvidos = this.itensResolvidosLocais();
    return source.map((item: any) => {
      const foiResolvido = resolvidos.has(item.id);
      return {
        id: item.id,
        codigo: item.material?.codigo ?? item.codigo ?? 'Item',
        descricao: item.material?.descricao ?? item.descricao ?? '',
        quantidade: item.quantidade ?? item.quantidadeSolicitada ?? null,
        motivoPendencia: foiResolvido ? null : (item.motivoPendencia ?? null),
        justificativaPendencia: foiResolvido ? null : (item.justificativaPendencia ?? null),
      };
    });
  });

  readonly todosSelecionados = computed(() => {
    const itens = this.listaItens();
    return itens.length > 0 && this.itensSelecionados().size === itens.length;
  });

  readonly modalTitle = computed(() => {
    if (this.itemLabel()) {
      return 'Registrar Pendência do Material';
    }
    return this.escopo() === 'MATERIAL' ? 'Registrar Pendência por Material' : 'Registrar Pendência da Solicitação';
  });

  readonly modalDescription = computed(() => {
    if (this.itemLabel()) {
      return `Material: ${this.itemLabel()}`;
    }
    return this.escopo() === 'MATERIAL'
      ? 'Selecione os materiais e configure as pendências individuais ou em lote.'
      : 'Informe o motivo e a descrição para registrar a pendência nesta solicitação.';
  });

  readonly modalSize = computed(() => (this.escopo() === 'MATERIAL' ? 'lg' : 'md'));

  readonly opcoesMotivos = computed(() =>
    this.motivos().map((m) => ({
      label: `${m.nome} — ${m.descricao}`,
      value: m.id,
    }))
  );

  readonly formularioValido = computed(() => {
    if (this.escopo() === 'GERAL') {
      return Boolean(this.idMotivo()) && this.descricao().trim().length >= 5;
    }
    const selecionados = this.itensSelecionados();
    if (selecionados.size === 0) return false;
    for (const id of selecionados) {
      const cfg = this.getItemConfig(id);
      if (!cfg.idMotivo || cfg.justificativa.trim().length < 5) {
        return false;
      }
    }
    return true;
  });

  readonly descricaoErro = computed(() => {
    const text = this.descricao().trim();
    if (!text && this.touched()) return 'A descrição é obrigatória.';
    if (text.length > 0 && text.length < 5) return 'A descrição deve ter no mínimo 5 caracteres.';
    return undefined;
  });

  constructor() {
    this.carregarMotivos();
    // Reage só à abertura e às entradas. O restante roda sem rastrear: ler `itensCarregados()` aqui e
    // depois recarregar gravava um array novo (mesmo vazio) e o efeito reiniciava sem fim.
    effect(() => {
      const aberto = this.visible();
      this.idItem();
      this.idSolicitacao();
      this.motivoAtual();
      this.justificativaAtual();
      if (aberto) untracked(() => this.prepararAbertura());
    });
  }

  private prepararAbertura(): void {
    this.touched.set(false);
    this.itensResolvidosLocais.set(new Set());
    const itemId = this.idItem();
    if (itemId) {
      this.escopo.set('MATERIAL');
      this.itensSelecionados.set(new Set([itemId]));
      this.itensConfig.update((map) => ({
        ...map,
        [itemId]: {
          idMotivo: this.motivoAtual()?.id ?? null,
          justificativa: this.justificativaAtual() ?? '',
        },
      }));
    } else {
      this.escopo.set('GERAL');
      this.itensSelecionados.set(new Set());
    }
    this.idMotivo.set(this.motivoAtual()?.id ?? null);
    this.descricao.set(this.justificativaAtual() ?? '');

    const id = this.idSolicitacao();
    if (id && (!this.itens() || this.itens().length === 0) && this.itensCarregados().length === 0) {
      this.carregarItensDaSolicitacao();
    }
  }

  getItemConfig(id: string): ItemPendenciaConfig {
    return this.itensConfig()[id] ?? { idMotivo: null, justificativa: '' };
  }

  setItemMotivo(id: string, idMotivo: any): void {
    this.itensConfig.update((map) => ({
      ...map,
      [id]: {
        idMotivo: typeof idMotivo === 'string' ? idMotivo : null,
        justificativa: map[id]?.justificativa ?? '',
      },
    }));
  }

  setItemJustificativa(id: string, justificativa: string): void {
    this.itensConfig.update((map) => ({
      ...map,
      [id]: {
        idMotivo: map[id]?.idMotivo ?? null,
        justificativa: justificativa ?? '',
      },
    }));
  }

  getItemMotivoErro(id: string): string | undefined {
    if (!this.touched()) return undefined;
    const cfg = this.getItemConfig(id);
    return !cfg.idMotivo ? 'Motivo obrigatório.' : undefined;
  }

  getItemJustificativaErro(id: string): string | undefined {
    if (!this.touched()) return undefined;
    const cfg = this.getItemConfig(id);
    const just = cfg.justificativa.trim();
    if (!just) return 'Justificativa obrigatória.';
    if (just.length < 5) return 'Mínimo 5 caracteres.';
    return undefined;
  }

  aplicarLoteAosSelecionados(): void {
    const motivoLote = this.idMotivoLote();
    const descLote = this.descricaoLote().trim();
    if (!motivoLote) return;
    this.itensConfig.update((map) => {
      const next = { ...map };
      for (const id of this.itensSelecionados()) {
        next[id] = {
          idMotivo: motivoLote,
          justificativa: descLote || next[id]?.justificativa || '',
        };
      }
      return next;
    });
    this.notifications.info('Motivo e justificativa aplicados aos itens selecionados.');
  }

  setEscopo(novoEscopo: 'GERAL' | 'MATERIAL'): void {
    this.escopo.set(novoEscopo);
    if (novoEscopo === 'MATERIAL' && (!this.itens() || this.itens().length === 0) && this.itensCarregados().length === 0) {
      this.carregarItensDaSolicitacao();
    }
  }

  toggleItem(id: string): void {
    this.itensSelecionados.update((set) => {
      const novo = new Set(set);
      if (novo.has(id)) {
        novo.delete(id);
      } else {
        novo.add(id);
        const existing = this.itensConfig()[id];
        if (!existing || !existing.idMotivo) {
          const itemObj = this.listaItens().find((i) => i.id === id);
          const idMotivo = itemObj?.motivoPendencia?.id ?? this.idMotivoLote() ?? null;
          const justificativa = itemObj?.justificativaPendencia ?? this.descricaoLote() ?? '';
          this.itensConfig.update((map) => ({
            ...map,
            [id]: { idMotivo, justificativa },
          }));
        }
      }
      return novo;
    });
  }

  toggleTodos(): void {
    if (this.todosSelecionados()) {
      this.itensSelecionados.set(new Set());
    } else {
      const todosIds = this.listaItens().map((i) => i.id);
      this.itensSelecionados.set(new Set(todosIds));
      this.itensConfig.update((map) => {
        const next = { ...map };
        for (const id of todosIds) {
          if (!next[id] || !next[id].idMotivo) {
            const itemObj = this.listaItens().find((i) => i.id === id);
            next[id] = {
              idMotivo: itemObj?.motivoPendencia?.id ?? this.idMotivoLote() ?? null,
              justificativa: itemObj?.justificativaPendencia ?? this.descricaoLote() ?? '',
            };
          }
        }
        return next;
      });
    }
  }

  carregarItensDaSolicitacao(): void {
    const id = this.idSolicitacao();
    if (!id || typeof this.service.buscarSolicitacaoPorId !== 'function') return;
    this.carregandoItens.set(true);
    this.service.buscarSolicitacaoPorId(id).subscribe({
      next: (sol) => {
        this.itensCarregados.set(sol?.itens ?? []);
        this.carregandoItens.set(false);
      },
      error: () => {
        this.carregandoItens.set(false);
      },
    });
  }

  carregarMotivos(): void {
    this.carregandoMotivos.set(true);
    this.service.listarMotivosPendencia(true).subscribe({
      next: (res) => {
        this.motivos.set(res);
        this.carregandoMotivos.set(false);
      },
      error: (err) => {
        this.carregandoMotivos.set(false);
        this.notifications.error(getErrorMessage(err));
      },
    });
  }

  salvar(): void {
    this.touched.set(true);
    if (!this.formularioValido()) return;

    const idSolicitacao = this.idSolicitacao();
    this.salvando.set(true);

    if (this.escopo() === 'MATERIAL') {
      const ids = Array.from(this.itensSelecionados());
      const itensPendencias: ItemPendenciaRequest[] = ids.map((id) => {
        const cfg = this.getItemConfig(id);
        return {
          idItem: id,
          idMotivoPendencia: cfg.idMotivo!,
          justificativa: cfg.justificativa.trim(),
        };
      });

      this.service
        .registrarPendencia(idSolicitacao, {
          idMotivoPendencia: itensPendencias[0]?.idMotivoPendencia,
          justificativa: itensPendencias[0]?.justificativa,
          idItem: ids.length === 1 ? ids[0] : undefined,
          idsItens: ids,
          itensPendencias,
        })
        .subscribe({
          next: () => {
            this.salvando.set(false);
            this.visible.set(false);
            this.notifications.success('Pendência(s) salva(s) com sucesso.');
            this.pendenciaSalva.emit();
          },
          error: (err) => {
            this.salvando.set(false);
            this.notifications.error(getErrorMessage(err));
          },
        });
    } else {
      const idMotivo = this.idMotivo()!;
      const justificativa = this.descricao().trim();
      this.service
        .registrarPendencia(idSolicitacao, {
          idMotivoPendencia: idMotivo,
          justificativa,
          idItem: undefined,
        })
        .subscribe({
          next: () => {
            this.salvando.set(false);
            this.visible.set(false);
            this.notifications.success('Pendência registrada com sucesso.');
            this.pendenciaSalva.emit();
          },
          error: (err) => {
            this.salvando.set(false);
            this.notifications.error(getErrorMessage(err));
          },
        });
    }
  }

  resolver(): void {
    const idSolicitacao = this.idSolicitacao();
    if (!idSolicitacao) return;

    const itemId = this.idItem();
    this.resolvendo.set(true);
    this.service.removerPendencia(idSolicitacao, itemId ?? undefined).subscribe({
      next: () => {
        this.resolvendo.set(false);
        this.visible.set(false);
        this.notifications.success(
          itemId ? 'Pendência do material resolvida com sucesso.' : 'Pendência geral resolvida com sucesso.'
        );
        this.pendenciaResolvida.emit();
      },
      error: (err) => {
        this.resolvendo.set(false);
        this.notifications.error(getErrorMessage(err));
      },
    });
  }

  resolverItem(idItem: string): void {
    const idSolicitacao = this.idSolicitacao();
    if (!idSolicitacao) return;

    this.resolvendoItem.set(idItem);
    this.service.removerPendencia(idSolicitacao, idItem).subscribe({
      next: () => {
        this.resolvendoItem.set(null);
        this.itensResolvidosLocais.update((set) => {
          const next = new Set(set);
          next.add(idItem);
          return next;
        });
        this.itensCarregados.update((itens) =>
          itens.map((it) => (it.id === idItem ? { ...it, motivoPendencia: null, justificativaPendencia: null } : it))
        );
        this.itensConfig.update((map) => {
          const next = { ...map };
          if (next[idItem]) {
            next[idItem] = { idMotivo: null, justificativa: '' };
          }
          return next;
        });
        const aindaTemItensComPendencia = this.listaItens().some((it) => Boolean(it.motivoPendencia));
        if (!aindaTemItensComPendencia) {
          this.notifications.success('Todas as pendências foram resolvidas. A pendência da solicitação foi removida.');
          this.visible.set(false);
        } else {
          this.notifications.success('Pendência do item removida.');
        }
        this.pendenciaResolvida.emit();
      },
      error: (err) => {
        this.resolvendoItem.set(null);
        this.notifications.error(getErrorMessage(err));
      },
    });
  }

  fechar(): void {
    this.visible.set(false);
  }
}
