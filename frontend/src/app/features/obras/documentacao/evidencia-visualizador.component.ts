import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesConfirmationService,
  CesDialogShellComponent,
  CesIconButtonComponent,
  CesTextInputComponent,
  NotificationService
} from '@ces/ui';
import { take } from 'rxjs';
import { EvidenciaPonto } from '../../../core/models/obras-documentacao.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ObrasDocumentacaoService } from '../../../core/services/obras-documentacao.service';
import { ObrasService } from '../../../core/services/obras.service';

/** Foto do book em tamanho real, com legenda e navegação entre as fotos do ponto. */
@Component({
  selector: 'app-evidencia-visualizador',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, CesAsyncStateComponent, CesButtonComponent, CesDialogShellComponent, CesIconButtonComponent, CesTextInputComponent],
  host: {
    '(document:keydown.arrowleft)': 'teclaNavegar($event, -1)',
    '(document:keydown.arrowright)': 'teclaNavegar($event, 1)'
  },
  template: `
    <ces-dialog-shell [visible]="!!atual()" (visibleChange)="$event || fechar.emit()" [title]="titulo()" size="lg">
      @if (atual(); as e) {
        <div class="ev">
          <div class="ev-foto">
            @if (url(); as src) {
              <img [src]="src" [alt]="descricao()" />
            } @else if (erro()) {
              <ces-async-state state="error" [compact]="true" title="Não foi possível abrir a foto" [description]="erro()!" actionLabel="Tentar de novo" (action)="carregar()"></ces-async-state>
            } @else {
              <ces-async-state state="loading" [compact]="true" title="Abrindo a foto"></ces-async-state>
            }
          </div>
          <div class="ev-nav">
            <ces-icon-button icon="pi pi-chevron-left" label="Foto anterior" size="icon-sm" [disabled]="posicao() === 0" (pressed)="navegar.emit(posicao() - 1)"></ces-icon-button>
            <span aria-live="polite">Foto {{ posicao() + 1 }} de {{ evidencias().length }}</span>
            <ces-icon-button icon="pi pi-chevron-right" label="Próxima foto" size="icon-sm" [disabled]="posicao() === evidencias().length - 1" (pressed)="navegar.emit(posicao() + 1)"></ces-icon-button>
          </div>
          <p class="ev-autoria">Lançada em {{ e.data | date: 'dd/MM/yyyy HH:mm' }}@if (e.usuario) { por {{ e.usuario }} }</p>
          @if (podeEscrever()) {
            <div class="ev-legenda">
              <ces-text-input class="ev-legenda__campo" label="Legenda" [maxlength]="500" [(value)]="legenda"></ces-text-input>
              <ces-button variant="outline" label="Salvar legenda" [disabled]="legenda().trim() === (e.legenda ?? '')" [loading]="salvando()" (pressed)="salvarLegenda(e)"></ces-button>
            </div>
          } @else if (e.legenda) {
            <p class="ev-texto">{{ e.legenda }}</p>
          }
        </div>
      }
      <div dialog-footer class="ces-actions-row">
        @if (podeEscrever() && atual(); as e) {
          <ces-button variant="ghost" icon="pi pi-trash" label="Remover foto" (pressed)="remover(e)"></ces-button>
        }
        <ces-button variant="outline" icon="pi pi-download" label="Baixar original" [disabled]="!blob()" (pressed)="baixar()"></ces-button>
        <ces-button label="Fechar" (pressed)="fechar.emit()"></ces-button>
      </div>
    </ces-dialog-shell>
  `,
  styles: `
    .ev { display: flex; flex-direction: column; gap: 0.75rem; }
    .ev-foto { display: grid; place-items: center; min-height: 14rem; overflow: hidden; border-radius: var(--ces-radius-md); background: var(--ces-surface-subtle); }
    .ev-foto img { display: block; max-width: 100%; max-height: 60vh; object-fit: contain; }
    .ev-nav { display: flex; align-items: center; justify-content: center; gap: 0.75rem; color: var(--ces-foreground-muted); font-size: 0.8125rem; }
    .ev-autoria, .ev-texto { margin: 0; overflow-wrap: anywhere; font-size: 0.8125rem; }
    .ev-autoria { color: var(--ces-foreground-muted); }
    .ev-texto { color: var(--ces-foreground); }
    .ev-legenda { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0.75rem; }
    .ev-legenda__campo { flex: 1 1 16rem; min-width: 0; }
  `
})
export class EvidenciaVisualizadorComponent {
  private readonly servico = inject(ObrasDocumentacaoService);
  private readonly obras = inject(ObrasService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  readonly idObra = input.required<string>();
  readonly tituloPonto = input('');
  readonly evidencias = input<EvidenciaPonto[]>([]);
  readonly indice = input<number>(0);
  readonly aberto = input(false);
  readonly podeEscrever = input(false);
  readonly navegar = output<number>();
  readonly fechar = output<void>();
  readonly alterado = output<void>();

  /** Depois de remover a última foto da lista, fica na anterior. */
  protected readonly posicao = computed(() => Math.min(this.indice(), this.evidencias().length - 1));
  protected readonly atual = computed(() => (this.aberto() ? (this.evidencias()[this.posicao()] ?? null) : null));
  private readonly idAtual = computed(() => this.atual()?.id ?? null);
  protected readonly titulo = computed(() => {
    const e = this.atual();
    return e ? `${this.tituloPonto()} · ${e.tituloItem ?? 'Outras evidências'}` : '';
  });
  protected readonly descricao = computed(() => {
    const e = this.atual();
    return e?.legenda ? `${e.tituloItem ?? 'Outras evidências'}, ${e.legenda}` : (e?.tituloItem ?? 'Outras evidências');
  });

  protected readonly url = signal<string | null>(null);
  protected readonly blob = signal<Blob | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly legenda = signal('');
  protected readonly salvando = signal(false);

  constructor() {
    effect((onCleanup) => {
      const id = this.idAtual();
      untracked(() => {
        this.legenda.set(this.atual()?.legenda ?? '');
        if (id) this.carregar();
      });
      onCleanup(() => this.limpar());
    });
  }

  protected carregar(): void {
    const id = this.idAtual();
    if (!id) return;
    this.erro.set(null);
    this.servico.baixarFoto(this.idObra(), id).pipe(take(1)).subscribe({
      next: (blob) => {
        // A pessoa pode ter passado para outra foto enquanto esta vinha.
        if (this.idAtual() !== id) return;
        this.limpar();
        this.blob.set(blob);
        this.url.set(URL.createObjectURL(blob));
      },
      error: (e) => {
        if (this.idAtual() === id) this.erro.set(getErrorMessage(e));
      }
    });
  }

  protected teclaNavegar(evento: Event, direcao: number): void {
    const alvo = evento.target as HTMLElement | null;
    if (!this.atual() || alvo?.tagName === 'INPUT' || alvo?.tagName === 'TEXTAREA') return;
    const destino = this.posicao() + direcao;
    if (destino >= 0 && destino < this.evidencias().length) this.navegar.emit(destino);
  }

  protected salvarLegenda(evidencia: EvidenciaPonto): void {
    this.salvando.set(true);
    this.servico.alterarLegenda(this.idObra(), evidencia.id, this.legenda().trim() || null).pipe(take(1)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.notificacoes.success('Legenda salva.');
        this.alterado.emit();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async remover(evidencia: EvidenciaPonto): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: 'Remover esta foto?',
      message: 'Ela sai do book e das próximas versões do As Built. As versões já geradas continuam como estão.',
      confirmLabel: 'Remover'
    });
    if (!ok) return;
    this.servico.removerEvidencia(this.idObra(), evidencia.id).pipe(take(1)).subscribe({
      next: () => {
        this.notificacoes.success('Foto removida.');
        this.alterado.emit();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected baixar(): void {
    const blob = this.blob();
    const e = this.atual();
    if (blob && e) this.obras.baixarArquivoBlob(blob, e.nomeArquivo);
  }

  private limpar(): void {
    const atual = this.url();
    if (atual) URL.revokeObjectURL(atual);
    this.url.set(null);
    this.blob.set(null);
  }
}
