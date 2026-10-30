import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { CesButtonComponent, CesIconButtonComponent } from '@ces/ui';
import { CesAsyncStateComponent } from '@ces/ui/feedback';
import { CesTextInputComponent } from '@ces/ui/forms';
import { CesConfirmationService } from '@ces/ui/overlays';
import { ConversaResumo } from '../../../core/models/assistente.models';
import { GrupoConversas } from '../copiloto.store';

@Component({
  selector: 'app-copiloto-conversas',
  standalone: true,
  imports: [CesButtonComponent, CesIconButtonComponent, CesAsyncStateComponent, CesTextInputComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="conversas" aria-label="Conversas do Copiloto">
      @if (mobile()) {
        <div class="conversas__cabecalho">
          <strong>Conversas</strong>
          <ces-icon-button icon="pi pi-times" label="Voltar à conversa" size="icon-sm" (pressed)="fechar.emit()"></ces-icon-button>
        </div>
      }
      <div class="conversas__topo">
        <ces-button icon="pi pi-plus" label="Nova conversa" [fluid]="true" (pressed)="nova.emit()"></ces-button>
        <ces-text-input
          label="Buscar conversas"
          inputId="copiloto-busca"
          placeholder="Buscar no histórico"
          [value]="busca()"
          (valueChange)="aoBuscar($event)"
        ></ces-text-input>
      </div>

      <div class="conversas__lista">
        @if (carregando() && !grupos().length) {
          <ces-async-state state="loading" [compact]="true" title="Carregando conversas"></ces-async-state>
        } @else if (erro()) {
          <ces-async-state
            state="error"
            [compact]="true"
            title="Não foi possível carregar o histórico"
            [description]="erro() || undefined"
            actionLabel="Tentar novamente"
            actionIcon="pi pi-refresh"
            (action)="recarregar.emit()"
          ></ces-async-state>
        } @else if (!grupos().length) {
          <ces-async-state
            state="empty"
            [compact]="true"
            [title]="busca() ? 'Nenhuma conversa encontrada' : 'Nenhuma conversa ainda'"
            [description]="busca() ? 'Tente outros termos.' : 'Suas conversas aparecerão aqui.'"
          ></ces-async-state>
        } @else {
          @for (grupo of grupos(); track grupo.rotulo) {
            <section class="grupo" [attr.aria-labelledby]="'grupo-' + $index">
              <h2 class="grupo__titulo" [id]="'grupo-' + $index">{{ grupo.rotulo }}</h2>
              <ul>
                @for (conversa of grupo.conversas; track conversa.id) {
                  <li class="item" [class.item--ativa]="conversa.id === ativa()">
                    @if (editando() === conversa.id) {
                      <form class="item__edicao" (submit)="$event.preventDefault(); confirmarRenomear(conversa)">
                        <label class="ces-sr-only" [for]="'renomear-' + conversa.id">Novo título da conversa</label>
                        <input
                          #campoTitulo
                          class="item__campo"
                          [id]="'renomear-' + conversa.id"
                          maxlength="160"
                          [value]="rascunhoTitulo()"
                          (input)="rascunhoTitulo.set($any($event.target).value)"
                          (keydown.escape)="cancelarEdicao()"
                          (blur)="confirmarRenomear(conversa)"
                        />
                      </form>
                    } @else {
                      <button
                        type="button"
                        class="item__abrir"
                        [attr.aria-current]="conversa.id === ativa() ? 'page' : null"
                        (click)="abrir.emit(conversa.id)"
                      >
                        @if (conversa.emAndamento) {
                          <i class="pi pi-spinner item__giro" aria-hidden="true"></i>
                          <span class="ces-sr-only">Resposta em andamento:</span>
                        }
                        @if (conversa.origem === 'DASHBOARD') {
                          <i class="pi pi-objects-column item__origem" aria-hidden="true" title="Iniciada em um dashboard"></i>
                          <span class="ces-sr-only">Iniciada em um dashboard:</span>
                        }
                        <span class="item__titulo">{{ conversa.titulo }}</span>
                      </button>
                      <span class="item__acoes">
                        <ces-icon-button
                          icon="pi pi-pencil"
                          size="icon-sm"
                          [label]="'Renomear ' + conversa.titulo"
                          tooltip="Renomear"
                          (pressed)="iniciarEdicao(conversa)"
                        ></ces-icon-button>
                        <ces-icon-button
                          icon="pi pi-trash"
                          size="icon-sm"
                          variant="ghost"
                          [label]="'Excluir ' + conversa.titulo"
                          tooltip="Excluir"
                          (pressed)="confirmarExclusao(conversa)"
                        ></ces-icon-button>
                      </span>
                    }
                  </li>
                }
              </ul>
            </section>
          }
        }
      </div>
    </nav>
  `,
  styles: `
    :host { display: flex; min-width: 0; min-height: 0; height: 100%; }
    .conversas {
      display: flex;
      flex-direction: column;
      width: 100%;
      min-width: 0;
      min-height: 0;
      overflow: hidden;
      background: var(--ces-surface);
    }
    .conversas__topo {
      display: flex;
      flex-direction: column;
      gap: 0.6rem;
      padding: 0.85rem 0.8rem 0.6rem;
      border-bottom: 1px solid var(--ces-border);
    }
    .conversas__cabecalho {
      display: flex;
      align-items: center;
      justify-content: space-between;
      min-height: 3.25rem;
      padding: 0.5rem 0.85rem;
      border-bottom: 1px solid var(--ces-border);
      font-size: var(--ces-font-body);
      color: var(--ces-foreground);
    }
    .conversas__lista {
      flex: 1;
      min-width: 0;
      min-height: 0;
      overflow-y: auto;
      padding: 0.4rem 0.45rem 1rem;
    }
    .grupo + .grupo { margin-top: 0.6rem; }
    .grupo__titulo {
      margin: 0.4rem 0.45rem 0.25rem;
      font-size: var(--ces-font-caption);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--ces-foreground-subtle);
    }
    ul { list-style: none; margin: 0; padding: 0; }
    .item {
      position: relative;
      display: flex;
      align-items: center;
      width: 100%;
      min-width: 0;
      overflow: hidden;
      border-radius: var(--ces-radius-sm);
    }
    .item:hover { background: var(--ces-surface-muted); }
    .item--ativa { background: var(--ces-brand-soft); }
    .item--ativa .item__titulo { color: var(--ces-brand); font-weight: 600; }
    .item__abrir {
      flex: 1;
      min-width: 0;
      display: flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.5rem 0.5rem;
      border: 0;
      background: transparent;
      color: var(--ces-foreground);
      font: inherit;
      font-size: var(--ces-font-body);
      text-align: left;
      cursor: pointer;
      border-radius: var(--ces-radius-sm);
    }
    .item__abrir:focus-visible, .item__campo:focus-visible {
      outline: 2px solid var(--ces-focus-ring);
      outline-offset: -2px;
    }
    .item__titulo { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .item__giro { color: var(--ces-brand); font-size: 0.75rem; animation: item-giro 0.9s linear infinite; }
    @keyframes item-giro { to { transform: rotate(360deg); } }
    .item__origem { flex: 0 0 auto; color: var(--ces-foreground-subtle); font-size: 0.7rem; }
    .item__acoes { display: none; flex: 0 0 auto; padding-right: 0.15rem; }
    .item:hover .item__acoes, .item:focus-within .item__acoes, .item--ativa .item__acoes { display: inline-flex; }
    .item__edicao { flex: 1; padding: 0.25rem; }
    .item__campo {
      width: 100%;
      padding: 0.35rem 0.5rem;
      border: 1px solid var(--ces-brand);
      border-radius: var(--ces-radius-sm);
      background: var(--ces-surface);
      color: var(--ces-foreground);
      font: inherit;
      font-size: var(--ces-font-body);
    }
    @media (hover: none) { .item__acoes { display: inline-flex; } }
    @media (prefers-reduced-motion: reduce) { .item__giro { animation: none; } }
  `,
})
export class CopilotoConversasComponent {
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly campoTitulo = viewChild<ElementRef<HTMLInputElement>>('campoTitulo');

  readonly grupos = input.required<readonly GrupoConversas[]>();
  readonly ativa = input<string | null>(null);
  readonly carregando = input(false);
  readonly erro = input<string | null>(null);
  readonly busca = input('');
  readonly mobile = input(false);

  readonly nova = output<void>();
  readonly fechar = output<void>();
  readonly abrir = output<string>();
  readonly buscar = output<string>();
  readonly recarregar = output<void>();
  readonly renomear = output<{ id: string; titulo: string }>();
  readonly excluir = output<string>();

  protected readonly editando = signal<string | null>(null);
  protected readonly rascunhoTitulo = signal('');
  private temporizador: ReturnType<typeof setTimeout> | undefined;

  protected aoBuscar(termo: string): void {
    clearTimeout(this.temporizador);
    this.temporizador = setTimeout(() => this.buscar.emit(termo), 300);
  }

  protected iniciarEdicao(conversa: ConversaResumo): void {
    this.rascunhoTitulo.set(conversa.titulo);
    this.editando.set(conversa.id);
    setTimeout(() => {
      const campo = this.campoTitulo()?.nativeElement;
      campo?.focus();
      campo?.select();
    });
  }

  protected cancelarEdicao(): void {
    this.editando.set(null);
  }

  protected confirmarRenomear(conversa: ConversaResumo): void {
    if (this.editando() !== conversa.id) return;
    const titulo = this.rascunhoTitulo().trim();
    this.editando.set(null);
    if (titulo && titulo !== conversa.titulo) this.renomear.emit({ id: conversa.id, titulo });
  }

  protected async confirmarExclusao(conversa: ConversaResumo): Promise<void> {
    const confirmado = await this.confirmacao.confirm({
      title: 'Excluir conversa?',
      message: `"${conversa.titulo}" sairá do seu histórico. Arquivos e artefatos gerados nela deixam de ficar acessíveis.`,
      variant: 'destructive',
    });
    if (confirmado) this.excluir.emit(conversa.id);
  }
}
