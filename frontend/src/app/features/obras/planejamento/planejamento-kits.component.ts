import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CesButtonComponent, CesIconButtonComponent, CesNumberInputComponent, CesStatusComponent, CesTone } from '@ces/ui';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CLASSES_TENSAO, ClasseTensao, KitConstrutivoResponse, OperacaoItemKit } from '../../../core/models/obras-kit.models';
import { ItemPlanejado, KitAplicado, StatusItemPlanejado } from '../../../core/models/obras-planejamento.models';
import { ObrasKitService } from '../../../core/services/obras-kit.service';
import { ROTULO_STATUS_ITEM, formatarQuantidade, kitBloqueado } from './planejamento.logic';

const TOM_STATUS: Record<StatusItemPlanejado, CesTone> = {
  PENDENTE: 'neutral',
  PROGRAMADO: 'info',
  EM_ANDAMENTO: 'warning',
  CONCLUIDO: 'success',
  MEDIDO: 'success'
};

export interface AplicacaoKit {
  idKit: string;
  operacao: OperacaoItemKit;
}

/** Busca de kits com filtro pela tensão da obra e, opcionalmente, a lista dos kits já aplicados no local. */
@Component({
  selector: 'app-planejamento-kits',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CesButtonComponent, CesIconButtonComponent, CesNumberInputComponent, CesStatusComponent],
  template: `
    @if (podeEditar()) {
      <div class="busca">
        <div class="busca__linha">
          <span class="busca__campo">
            <i class="pi pi-search" aria-hidden="true"></i>
            <input type="search" placeholder="Buscar kit por código ou descrição" aria-label="Buscar kit"
              [value]="termo()" (input)="digitar($any($event.target).value)" />
          </span>
          <div class="segmento" role="group" aria-label="Ação do kit">
            <button type="button" [attr.aria-pressed]="operacao() === 'INSTALAR'" (click)="operacao.set('INSTALAR')">Instalar</button>
            <button type="button" [attr.aria-pressed]="operacao() === 'RETIRAR'" (click)="operacao.set('RETIRAR')">Retirar</button>
          </div>
        </div>
        @if (rotuloTensao()) {
          <label class="busca__tensao">
            <input type="checkbox" [checked]="verTodos()" (change)="alternarTodos($any($event.target).checked)" />
            Ver kits de todas as tensões (a obra é {{ rotuloTensao() }})
          </label>
        }
        @if (!idContrato()) {
          <p class="aviso">A obra não tem contrato na carteira: a busca mostra os kits de todos os contratos.</p>
        }
        @if (termo().trim()) {
          <ul class="resultados">
            @for (kit of resultados(); track kit.id) {
              <li>
                <button type="button" (click)="escolher(kit)" [disabled]="jaAplicado(kit.id)">
                  <code>{{ kit.codigo }}</code>
                  <span>{{ kit.descricao }}</span>
                  @if (jaAplicado(kit.id)) { <small>já aplicado</small> } @else { <i class="pi pi-plus" aria-hidden="true"></i> }
                </button>
              </li>
            } @empty {
              <li class="resultados__vazio">
                @if (buscando()) {
                  Buscando…
                } @else if (rotuloTensao() && !verTodos()) {
                  Nenhum kit para {{ rotuloTensao() }}.
                  <button type="button" class="resultados__todos" (click)="alternarTodos(true)">Ver de todas as tensões</button>
                } @else {
                  Nenhum kit encontrado.
                }
              </li>
            }
          </ul>
        } @else if (usadosNaObra().length) {
          <p class="busca__titulo">Usados nesta obra</p>
          <ul class="resultados">
            @for (kit of usadosNaObra(); track kit.idKit) {
              <li>
                <button type="button" (click)="aplicar.emit({ idKit: kit.idKit, operacao: operacao() })" [disabled]="jaAplicado(kit.idKit)">
                  <code>{{ kit.codigo }}</code>
                  <span>{{ kit.descricao }}</span>
                  @if (jaAplicado(kit.idKit)) { <small>já aplicado</small> } @else { <i class="pi pi-plus" aria-hidden="true"></i> }
                </button>
              </li>
            }
          </ul>
        }
      </div>
    }

    @if (mostrarAplicados()) {
      <div class="kits">
        @for (kit of kits(); track kit.id) {
          <article class="kit" [class.kit--bloqueado]="bloqueado(kit)">
            <header class="kit__topo">
              <button type="button" class="kit__abrir" [attr.aria-expanded]="abertos().has(kit.id)" (click)="alternar(kit.id)">
                <i class="pi" [class.pi-chevron-down]="abertos().has(kit.id)" [class.pi-chevron-right]="!abertos().has(kit.id)" aria-hidden="true"></i>
                <code>{{ kit.codigo }}</code>
                <span class="kit__descricao" [title]="kit.descricao">{{ kit.descricao }}</span>
              </button>
              @if (kit.operacao === 'RETIRAR') { <ces-status tone="warning">Retirar</ces-status> }
              @if (kit.executadoAntes) { <ces-status tone="success">Já executado</ces-status> }
              @if (bloqueado(kit)) { <i class="pi pi-lock kit__cadeado" title="Tem item programado ou executado: não pode ser alterado nem removido"></i> }
              @if (podeEditar() && !bloqueado(kit) && !kit.executadoAntes) {
                <ces-icon-button icon="pi pi-trash" variant="ghost" size="icon-sm" label="Remover kit" tooltip="Remover kit" (pressed)="remover.emit(kit)"></ces-icon-button>
              }
            </header>

            @if (abertos().has(kit.id)) {
              @if (podeEditar() && !bloqueado(kit) && !kit.executadoAntes) {
                <div class="kit__quantidade">
                  <ces-number-input [label]="emVao() ? 'Metros' : 'Qtd. de kits'" [labelVisible]="true" [min]="0.0001" [maxFractionDigits]="4"
                    [description]="emVao() ? 'Só os itens medidos em metro acompanham a metragem.' : undefined"
                    [value]="kit.multiplicador" (valueChange)="rascunho.set({ id: kit.id, valor: $event })"></ces-number-input>
                  <ces-button variant="outline" size="sm" label="Aplicar" [disabled]="!podeAplicarQuantidade(kit)"
                    (pressed)="alterarQuantidade.emit({ kit, multiplicador: rascunho()!.valor! })"></ces-button>
                </div>
              }
              <ul class="itens">
                @for (item of ordenados(kit); track item.id) {
                  <li class="item">
                    <span class="item__tipo" [attr.data-material]="item.tipo === 'MATERIAL'">{{ item.tipo === 'MATERIAL' ? 'M' : 'S' }}</span>
                    <span class="item__nome"><code>{{ item.codigo }}</code> {{ item.descricao }}</span>
                    @if (editando()?.id === item.id) {
                      <span class="item__edicao">
                        <ces-number-input label="Quantidade" [labelVisible]="false" [min]="0.0001" [maxFractionDigits]="4"
                          [value]="editando()!.valor" (valueChange)="editando.set({ id: item.id, valor: $event })"></ces-number-input>
                        <ces-icon-button icon="pi pi-check" variant="ghost" size="icon-sm" label="Salvar quantidade" [disabled]="!editando()!.valor"
                          (pressed)="salvarItem(kit, item)"></ces-icon-button>
                        <ces-icon-button icon="pi pi-times" variant="ghost" size="icon-sm" label="Cancelar" (pressed)="editando.set(null)"></ces-icon-button>
                      </span>
                    } @else {
                      <span class="item__qtd" title="Previsto e executado">
                        {{ qtd(item.quantidadePrevista) }} {{ item.unidade }}
                        @if (item.tipo === 'SERVICO') { <small>exec. {{ qtd(item.quantidadeExecutada) }}</small> }
                      </span>
                      <ces-status [tone]="tom[item.status]">{{ rotulo[item.status] }}</ces-status>
                      @if (podeEditar() && item.status === 'PENDENTE' && !kit.executadoAntes) {
                        <ces-icon-button icon="pi pi-pencil" variant="ghost" size="icon-sm" label="Editar quantidade" (pressed)="editando.set({ id: item.id, valor: item.quantidadePrevista })"></ces-icon-button>
                        <ces-icon-button icon="pi pi-times" variant="ghost" size="icon-sm" label="Remover item" (pressed)="removerItem.emit({ kit, item })"></ces-icon-button>
                      }
                    }
                  </li>
                }
              </ul>
              @if (podeEditar() && !bloqueado(kit)) {
                <button type="button" class="kit__antes" (click)="executadoAntes.emit({ kit, executado: !kit.executadoAntes })">
                  <i class="pi pi-history" aria-hidden="true"></i>
                  {{ kit.executadoAntes ? 'Desfazer: voltar para pendente' : 'Marcar como já executado antes do sistema' }}
                </button>
              }
            }
          </article>
        } @empty {
          <p class="kits__vazio">Nenhum kit ainda. Busque acima para aplicar.</p>
        }
      </div>
    }
  `,
  styles: [`
    :host { display: flex; flex-direction: column; gap: 0.625rem; min-width: 0; font-size: 0.8125rem; color: var(--ces-foreground); }
    code { color: var(--ces-brand); font-size: 0.75rem; font-weight: 600; }
    .busca { display: flex; flex-direction: column; gap: 0.375rem; }
    .busca__linha { display: flex; flex-wrap: wrap; gap: 0.375rem; }
    .busca__campo { display: flex; flex: 1 1 10rem; gap: 0.375rem; align-items: center; min-width: 0; padding: 0.375rem 0.5rem; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface); color: var(--ces-foreground-muted); }
    .busca__campo:focus-within { border-color: var(--ces-brand); }
    .busca__campo input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--ces-foreground); }
    .segmento { display: inline-flex; flex: none; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); overflow: hidden; }
    .segmento button { padding: 0.25rem 0.5rem; border: 0; background: transparent; color: var(--ces-foreground-muted); font-size: 0.75rem; cursor: pointer; }
    .segmento button[aria-pressed='true'] { background: var(--ces-brand-soft); color: var(--ces-brand); font-weight: 600; }
    .busca__tensao { display: flex; gap: 0.375rem; align-items: center; color: var(--ces-foreground-muted); font-size: 0.75rem; cursor: pointer; }
    .aviso { margin: 0; color: var(--ces-warning-text); font-size: 0.75rem; }
    .busca__titulo { margin: 0.25rem 0 0; color: var(--ces-foreground-muted); font-size: 0.6875rem; font-weight: 700; letter-spacing: 0.03em; text-transform: uppercase; }
    .resultados { max-height: 14rem; margin: 0; padding: 0; overflow-y: auto; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); list-style: none; }
    .resultados li + li { border-top: 1px solid var(--ces-border); }
    .resultados button { display: flex; gap: 0.5rem; align-items: center; width: 100%; padding: 0.375rem 0.5rem; border: 0; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; }
    .resultados button:hover:not(:disabled) { background: var(--ces-surface-muted); }
    .resultados button:disabled { color: var(--ces-foreground-muted); cursor: default; }
    .resultados span { flex: 1; min-width: 0; }
    .resultados__vazio { padding: 0.5rem; color: var(--ces-foreground-muted); }
    .resultados button.resultados__todos { display: inline; width: auto; padding: 0; color: var(--ces-brand); font-weight: 600; text-decoration: underline; }
    .kits { display: flex; flex-direction: column; gap: 0.375rem; }
    .kits__vazio { margin: 0; padding: 0.625rem; border: 1px dashed var(--ces-border); border-radius: var(--ces-radius-md); color: var(--ces-foreground-muted); font-size: 0.75rem; text-align: center; }
    .kit { border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface); }
    .kit__topo { display: flex; gap: 0.25rem; align-items: center; padding: 0.25rem 0.375rem; }
    .kit__abrir { display: flex; flex: 1; gap: 0.375rem; align-items: center; min-width: 0; padding: 0.125rem; border: 0; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; }
    .kit__abrir i { color: var(--ces-foreground-muted); font-size: 0.625rem; }
    .kit__descricao { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .kit__cadeado { color: var(--ces-foreground-muted); font-size: 0.75rem; }
    .kit__quantidade { display: flex; gap: 0.5rem; align-items: flex-end; padding: 0.25rem 0.5rem 0.5rem; border-top: 1px solid var(--ces-border); }
    .kit__quantidade ces-number-input { flex: 1; min-width: 0; }
    .itens { margin: 0; padding: 0; border-top: 1px solid var(--ces-border); list-style: none; }
    .item { display: flex; flex-wrap: wrap; gap: 0.375rem; align-items: center; padding: 0.3125rem 0.5rem; }
    .item + .item { border-top: 1px solid var(--ces-border); }
    .item__tipo { flex: none; width: 1rem; border-radius: var(--ces-radius-sm); background: var(--ces-success-soft); color: var(--ces-success-text); font-size: 0.625rem; font-weight: 700; line-height: 1rem; text-align: center; }
    .item__tipo[data-material='true'] { background: var(--ces-info-soft); color: var(--ces-info-text); }
    .item__nome { flex: 1 1 9rem; min-width: 0; font-size: 0.75rem; }
    .item__qtd { display: inline-flex; flex-direction: column; font-size: 0.75rem; font-variant-numeric: tabular-nums; text-align: right; }
    .item__qtd small { color: var(--ces-foreground-muted); font-size: 0.6875rem; }
    .item__edicao { display: inline-flex; gap: 0.125rem; align-items: center; width: 11rem; }
    .item__edicao ces-number-input { flex: 1; min-width: 0; }
    .kit__antes { display: flex; gap: 0.375rem; align-items: center; width: 100%; padding: 0.375rem 0.5rem; border: 0; border-top: 1px solid var(--ces-border); background: transparent; color: var(--ces-foreground-muted); font-size: 0.75rem; text-align: left; cursor: pointer; }
    .kit__antes:hover { color: var(--ces-foreground); background: var(--ces-surface-muted); }
    button:focus-visible { outline: 2px solid var(--ces-brand); outline-offset: 1px; }
  `]
})
export class PlanejamentoKitsComponent {
  private readonly kitsService = inject(ObrasKitService);

  /** Kits já usados na obra, a um clique quando a busca está vazia. */
  readonly usadosNaObra = input<readonly { idKit: string; codigo: string; descricao: string }[]>([]);
  readonly kits = input<readonly KitAplicado[]>([]);
  readonly idContrato = input<string | null | undefined>(null);
  readonly classeTensao = input<ClasseTensao | null | undefined>(null);
  readonly emVao = input(false);
  readonly podeEditar = input(true);
  readonly mostrarAplicados = input(true);

  readonly aplicar = output<AplicacaoKit>();
  readonly remover = output<KitAplicado>();
  readonly alterarQuantidade = output<{ kit: KitAplicado; multiplicador: number }>();
  readonly executadoAntes = output<{ kit: KitAplicado; executado: boolean }>();
  readonly alterarItem = output<{ kit: KitAplicado; item: ItemPlanejado; quantidade: number }>();
  readonly removerItem = output<{ kit: KitAplicado; item: ItemPlanejado }>();

  protected readonly tom = TOM_STATUS;
  protected readonly rotulo = ROTULO_STATUS_ITEM;
  protected readonly qtd = formatarQuantidade;

  protected readonly termo = signal('');
  protected readonly operacao = signal<OperacaoItemKit>('INSTALAR');
  protected readonly verTodos = signal(false);
  protected readonly resultados = signal<KitConstrutivoResponse[]>([]);
  protected readonly buscando = signal(false);
  protected readonly abertos = signal<ReadonlySet<string>>(new Set());
  protected readonly rascunho = signal<{ id: string; valor: number | null } | null>(null);
  protected readonly editando = signal<{ id: string; valor: number | null } | null>(null);

  protected readonly rotuloTensao = computed(() => CLASSES_TENSAO.find((c) => c.value === this.classeTensao())?.label ?? null);

  private readonly buscas = new Subject<void>();

  constructor() {
    this.buscas.pipe(
      debounceTime(250),
      switchMap(() => {
        const termo = this.termo().trim();
        if (!termo) return of(null);
        this.buscando.set(true);
        return this.kitsService.listarKits({
          buscaGlobal: termo,
          idContrato: this.idContrato() ?? undefined,
          classeTensao: this.verTodos() ? undefined : this.classeTensao() ?? undefined,
          ativo: true,
          size: 20
        }).pipe(catchError(() => of(null)));
      }),
      takeUntilDestroyed()
    ).subscribe((pagina) => {
      this.buscando.set(false);
      this.resultados.set(pagina?.content ?? []);
    });

    // Volta ao estado inicial ao trocar de ponto ou vão.
    effect(() => {
      this.kits();
      this.rascunho.set(null);
      this.editando.set(null);
    });
  }

  protected digitar(termo: string): void {
    this.termo.set(termo);
    this.buscas.next();
  }

  protected alternarTodos(todos: boolean): void {
    this.verTodos.set(todos);
    this.buscas.next();
  }

  protected escolher(kit: KitConstrutivoResponse): void {
    this.aplicar.emit({ idKit: kit.id, operacao: this.operacao() });
    this.termo.set('');
    this.resultados.set([]);
  }

  protected jaAplicado(idKit: string): boolean {
    return this.kits().some((k) => k.idKit === idKit && k.operacao === this.operacao());
  }

  protected bloqueado(kit: KitAplicado): boolean {
    return kitBloqueado(kit);
  }

  protected alternar(id: string): void {
    const abertos = new Set(this.abertos());
    if (!abertos.delete(id)) abertos.add(id);
    this.abertos.set(abertos);
  }

  protected podeAplicarQuantidade(kit: KitAplicado): boolean {
    const rascunho = this.rascunho();
    return rascunho?.id === kit.id && !!rascunho.valor && rascunho.valor !== kit.multiplicador;
  }

  protected ordenados(kit: KitAplicado): ItemPlanejado[] {
    return [...kit.itens].sort((a, b) => Number(a.tipo === 'SERVICO') - Number(b.tipo === 'SERVICO'));
  }

  protected salvarItem(kit: KitAplicado, item: ItemPlanejado): void {
    const valor = this.editando()?.valor;
    if (valor) this.alterarItem.emit({ kit, item, quantidade: valor });
    this.editando.set(null);
  }
}
