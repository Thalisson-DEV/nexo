import { ChangeDetectionStrategy, Component, ElementRef, computed, input, model, output, viewChild } from '@angular/core';
import { CesButtonComponent, CesIconButtonComponent, CesStatusComponent } from '@ces/ui';
import { CesTextareaComponent } from '@ces/ui/forms';
import { AssistenteContexto } from '../../../core/models/assistente.models';
import { AnexoPendente, formatarTamanho } from '../copiloto.store';

const ACEITOS = '.pdf,.xlsx,.xlsm,.xls,.csv,.tsv,.json,.txt,.md,.log,.xml,.yml,.yaml,.sql,.html,.htm,.docx,.png,.jpg,.jpeg,.gif,.webp';

/**
 * Compositor de mensagens: texto com Enter para enviar (Shift+Enter quebra linha), anexos por botão,
 * colar ou arrastar, e botão de interromper durante a geração.
 */
@Component({
  selector: 'app-copiloto-compositor',
  standalone: true,
  imports: [CesButtonComponent, CesIconButtonComponent, CesStatusComponent, CesTextareaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="compositor" (submit)="$event.preventDefault(); enviarAgora()" [class.compositor--desabilitado]="!disponivel()">
      @if (contexto(); as ctx) {
        <div class="compositor__contexto">
          <ces-status tone="brand" icon="pi-map-marker">
            Contexto: {{ ctx.rotulo || ctx.pagina || ctx.entidadeTipo }}
          </ces-status>
        </div>
      }
      @if (anexos().length) {
        <ul class="compositor__anexos" aria-label="Anexos desta mensagem">
          @for (anexo of anexos(); track anexo.idLocal) {
            <li class="anexo" [attr.data-estado]="anexo.estado">
              <i
                class="pi"
                [class.pi-spinner]="anexo.estado === 'enviando'"
                [class.anexo__giro]="anexo.estado === 'enviando'"
                [class.pi-paperclip]="anexo.estado === 'pronto' && !anexo.erro"
                [class.pi-exclamation-triangle]="anexo.estado === 'erro' || !!anexo.erro"
                aria-hidden="true"
              ></i>
              <span class="anexo__nome">{{ anexo.nome }}</span>
              <span class="anexo__meta">
                @switch (anexo.estado) {
                  @case ('enviando') { Enviando {{ anexo.progresso }}% }
                  @case ('erro') { {{ anexo.erro || 'Falha no envio' }} }
                  @default { {{ anexo.erro ? 'Enviado; leitura automática falhou' : formatarTamanho(anexo.tamanho) }} }
                }
              </span>
              <ces-icon-button
                icon="pi pi-times"
                size="icon-sm"
                [label]="'Remover ' + anexo.nome"
                (pressed)="removerAnexo.emit(anexo.idLocal)"
              ></ces-icon-button>
            </li>
          }
        </ul>
      }
      <div class="compositor__campo" (keydown)="aoTeclar($event)" (paste)="aoColar($event)">
        <ces-textarea
          #campo
          label="Mensagem para o Copiloto"
          [labelVisible]="false"
          inputId="copiloto-mensagem"
          [(value)]="valor"
          [rows]="2"
          [autoResize]="false"
          [maxlength]="20000"
          [placeholder]="placeholder()"
          [disabled]="!disponivel()"
        ></ces-textarea>
      </div>
      <div class="compositor__barra">
        <input
          #seletor
          type="file"
          multiple
          class="ces-sr-only"
          tabindex="-1"
          aria-hidden="true"
          [accept]="aceitos"
          (change)="aoSelecionar($event)"
        />
        <ces-icon-button
          icon="pi pi-paperclip"
          label="Anexar arquivos (PDF, planilhas, CSV, JSON, texto, DOCX ou imagens)"
          [disabled]="!disponivel()"
          (pressed)="seletor.click()"
        ></ces-icon-button>
        <span class="compositor__dica">Enter envia · Shift+Enter quebra linha</span>
        @if (transmitindo()) {
          <ces-button
            variant="secondary"
            icon="pi pi-stop-circle"
            label="Parar"
            ariaLabel="Parar a geração da resposta"
            [loading]="parando()"
            (pressed)="parar.emit()"
          ></ces-button>
        } @else {
          <ces-button
            type="submit"
            icon="pi pi-send"
            label="Enviar"
            [disabled]="!podeEnviar()"
            ariaLabel="Enviar mensagem"
          ></ces-button>
        }
      </div>
    </form>
  `,
  styles: `
    :host { display: block; }
    .compositor {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      padding: 0.6rem 0.7rem 0.55rem;
      border: 1px solid var(--ces-border-strong);
      border-radius: var(--ces-radius-lg);
      background: var(--ces-surface);
      box-shadow: var(--ces-shadow-md);
    }
    .compositor:focus-within { border-color: var(--ces-brand); }
    .compositor--desabilitado { opacity: 0.7; }
    .compositor__contexto { display: flex; }
    .compositor__campo { min-width: 0; }
    .compositor__barra { display: flex; align-items: center; gap: 0.5rem; }
    .compositor__dica {
      flex: 1;
      font-size: var(--ces-font-caption);
      color: var(--ces-foreground-subtle);
    }
    .compositor__anexos {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem;
    }
    .anexo {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      max-width: 100%;
      padding: 0.15rem 0.2rem 0.15rem 0.6rem;
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-sm);
      background: var(--ces-surface-subtle);
      font-size: var(--ces-font-caption);
    }
    .anexo[data-estado='erro'] { border-color: var(--ces-danger-text); color: var(--ces-danger-text); }
    .anexo__nome { max-width: 14rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ces-foreground); }
    .anexo__meta { color: var(--ces-foreground-subtle); }
    .anexo__giro { color: var(--ces-brand); animation: anexo-giro 0.9s linear infinite; }
    @keyframes anexo-giro { to { transform: rotate(360deg); } }
    @media (max-width: 640px) { .compositor__dica { visibility: hidden; } }
    @media (prefers-reduced-motion: reduce) { .anexo__giro { animation: none; } }
  `,
})
export class CopilotoCompositorComponent {
  private readonly campo = viewChild<CesTextareaComponent>('campo');
  private readonly seletor = viewChild<ElementRef<HTMLInputElement>>('seletor');

  readonly valor = model('');
  readonly anexos = input<readonly AnexoPendente[]>([]);
  readonly contexto = input<AssistenteContexto | null>(null);
  readonly transmitindo = input(false);
  readonly parando = input(false);
  readonly disponivel = input(true);
  readonly enviandoAnexos = input(false);

  readonly enviar = output<string>();
  readonly parar = output<void>();
  readonly anexar = output<File[]>();
  readonly removerAnexo = output<string>();

  protected readonly aceitos = ACEITOS;
  protected readonly formatarTamanho = formatarTamanho;
  protected readonly podeEnviar = computed(
    () => this.disponivel() && !this.transmitindo() && !this.enviandoAnexos() && this.valor().trim().length > 0,
  );
  protected readonly placeholder = computed(() =>
    this.disponivel()
      ? 'Pergunte ao Copiloto…'
      : 'O Copiloto está indisponível no momento',
  );

  focar(): void {
    this.campo()?.focus();
  }

  protected enviarAgora(): void {
    if (!this.podeEnviar()) return;
    this.enviar.emit(this.valor().trim());
  }

  protected aoTeclar(evento: KeyboardEvent): void {
    if (evento.key === 'Enter' && !evento.shiftKey && !evento.isComposing && !evento.ctrlKey && !evento.altKey) {
      evento.preventDefault();
      this.enviarAgora();
    }
  }

  protected aoColar(evento: ClipboardEvent): void {
    const arquivos = [...(evento.clipboardData?.files ?? [])];
    if (arquivos.length) {
      evento.preventDefault();
      this.anexar.emit(arquivos);
    }
  }

  protected aoSelecionar(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const arquivos = [...(entrada.files ?? [])];
    if (arquivos.length) this.anexar.emit(arquivos);
    entrada.value = '';
    this.seletor()?.nativeElement.blur();
  }
}
