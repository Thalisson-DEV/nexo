import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  model,
  signal,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { TextareaModule } from 'primeng/textarea';
import { OrigemMencao, UsuarioMencionavel } from '../../../core/models/mencao.models';
import { MencoesService } from '../../../core/services/mencoes.service';
import {
  LIMITE_MARCACOES_POR_MENSAGEM,
  MarcacaoEmDigitacao,
  extrairMarcacoes,
  inserirMarcacao,
  localizarMarcacaoEmDigitacao
} from './mencoes.util';

/** A partir de quantos caracteres depois do `@` vale consultar o servidor. */
const MINIMO_PARA_BUSCAR = 2;

/**
 * Caixa de texto que reconhece `@` e sugere quem marcar.
 *
 * Insere sempre o **login** devolvido pelo servidor, nunca um nome de exibição: escolher pela
 * aparência e gravar outro texto é o jeito mais fácil de marcar a pessoa errada.
 *
 * Em composer de comentário interno, passe `visibilidadeRestrita = true`. A lista já vem sem quem
 * não poderia ler o texto — é a defesa que evita o 422 do envio, que desfaz a mensagem inteira.
 */
@Component({
  selector: 'app-mencao-textarea',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TextareaModule],
  template: `
    <div class="relative">
      <textarea
        #campo
        pTextarea
        class="w-full !text-[11px]"
        [rows]="rows()"
        [attr.maxlength]="maxlength()"
        [attr.placeholder]="placeholder()"
        [attr.aria-expanded]="aberto()"
        [disabled]="disabled()"
        [value]="texto()"
        (input)="aoDigitar($event)"
        (keydown)="aoTeclar($event)"
        (click)="reavaliar()"
        (blur)="fechar()"
      ></textarea>

      @if (aberto()) {
        <div
          class="absolute left-0 right-0 z-30 mt-1 overflow-hidden rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-lg"
          role="listbox"
          (mousedown)="$event.preventDefault()"
        >
          @if (carregando()) {
            <div class="flex items-center gap-2 px-3 py-2.5 text-[10px] text-slate-400">
              <i class="pi pi-spin pi-spinner text-[10px]"></i>
              <span>Procurando pessoas...</span>
            </div>
          } @else if (sugestoes().length) {
            @for (usuario of sugestoes(); track usuario.id; let i = $index) {
              <button
                type="button"
                role="option"
                [attr.aria-selected]="i === indiceAtivo()"
                class="flex w-full items-center gap-2 px-3 py-2 text-left text-[11px] transition-colors"
                [class.bg-primary-50]="i === indiceAtivo()"
                [class.dark:bg-primary-950/40]="i === indiceAtivo()"
                [class.text-primary-700]="i === indiceAtivo()"
                [class.dark:text-primary-300]="i === indiceAtivo()"
                [class.text-slate-600]="i !== indiceAtivo()"
                [class.dark:text-slate-200]="i !== indiceAtivo()"
                (mouseenter)="indiceAtivo.set(i)"
                (click)="escolher(usuario)"
              >
                <i class="pi pi-at text-[10px] text-primary-500 dark:text-primary-400"></i>
                <span class="font-medium">{{ usuario.login }}</span>
              </button>
            }
          } @else {
            <div class="px-3 py-2.5 text-[10px] text-slate-400">
              Ninguém encontrado com esse trecho.
            </div>
          }
        </div>
      }
    </div>

    <div class="mt-1.5 flex flex-wrap items-center justify-between gap-2">
      <span class="text-[9px] text-slate-400">
        <i class="pi pi-at mr-1 text-[9px]"></i>
        Digite <strong>&#64;</strong> para marcar alguém e avisá-la desta mensagem.
      </span>
      @if (excedeuLimite()) {
        <span class="text-[9px] font-medium text-red-600">
          Marque no máximo {{ limiteMarcacoes }} pessoas por mensagem.
        </span>
      }
    </div>
  `
})
export class MencaoTextareaComponent {
  private readonly service = inject(MencoesService);
  private readonly campo = viewChild.required<ElementRef<HTMLTextAreaElement>>('campo');

  readonly texto = model<string>('');
  readonly rows = input(5);
  readonly maxlength = input<number | null>(5000);
  readonly placeholder = input('');
  readonly disabled = input(false);
  readonly origem = input<OrigemMencao>('CHAMADO_INTERACAO');
  /** `true` quando o texto tem circulação restrita (comentário interno). */
  readonly visibilidadeRestrita = input(false);

  protected readonly limiteMarcacoes = LIMITE_MARCACOES_POR_MENSAGEM;
  protected readonly sugestoes = signal<UsuarioMencionavel[]>([]);
  protected readonly carregando = signal(false);
  protected readonly aberto = signal(false);
  protected readonly indiceAtivo = signal(0);

  protected readonly excedeuLimite = computed(
    () => extrairMarcacoes(this.texto()).length > LIMITE_MARCACOES_POR_MENSAGEM
  );

  /** Trecho `@algo` sob o cursor no momento — é o que a escolha substitui. */
  private alvo: MarcacaoEmDigitacao | null = null;
  private readonly termos = new Subject<string>();

  constructor() {
    this.termos
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        // switchMap cancela a busca anterior: só a do trecho atual interessa.
        switchMap((termo) =>
          this.service
            .buscarUsuarios({
              termo,
              origem: this.origem(),
              visibilidadeRestrita: this.visibilidadeRestrita()
            })
            .pipe(catchError(() => of<UsuarioMencionavel[]>([])))
        ),
        takeUntilDestroyed()
      )
      .subscribe((usuarios) => {
        this.sugestoes.set(usuarios);
        this.indiceAtivo.set(0);
        this.carregando.set(false);
      });

    // Trocar entre público e interno muda quem pode ser marcado: a lista anterior não vale mais.
    effect(() => {
      this.visibilidadeRestrita();
      this.fechar();
      this.sugestoes.set([]);
    });
  }

  protected aoDigitar(evento: Event): void {
    const campo = evento.target as HTMLTextAreaElement;
    this.texto.set(campo.value);
    this.avaliarContexto(campo);
  }

  protected reavaliar(): void {
    this.avaliarContexto(this.campo().nativeElement);
  }

  protected fechar(): void {
    this.aberto.set(false);
    this.carregando.set(false);
    this.alvo = null;
  }

  protected aoTeclar(evento: KeyboardEvent): void {
    if (!this.aberto()) return;

    switch (evento.key) {
      case 'ArrowDown':
        evento.preventDefault();
        this.mover(1);
        return;
      case 'ArrowUp':
        evento.preventDefault();
        this.mover(-1);
        return;
      case 'Enter':
      case 'Tab': {
        const escolhido = this.sugestoes()[this.indiceAtivo()];
        if (!escolhido) return;
        evento.preventDefault();
        this.escolher(escolhido);
        return;
      }
      case 'Escape':
        evento.preventDefault();
        this.fechar();
        return;
      default:
        return;
    }
  }

  protected escolher(usuario: UsuarioMencionavel): void {
    const alvo = this.alvo;
    if (!alvo) return;

    const resultado = inserirMarcacao(this.texto(), alvo, usuario.login);
    this.texto.set(resultado.texto);
    this.fechar();

    // O valor do textarea é ligado por `[value]`; devolver foco e cursor precisa esperar o render.
    queueMicrotask(() => {
      const campo = this.campo().nativeElement;
      campo.value = resultado.texto;
      campo.focus();
      campo.setSelectionRange(resultado.cursor, resultado.cursor);
    });
  }

  /** Decide, a partir do cursor, se há um `@algo` em digitação e se vale consultar o servidor. */
  private avaliarContexto(campo: HTMLTextAreaElement): void {
    const alvo = localizarMarcacaoEmDigitacao(campo.value, campo.selectionStart ?? 0);
    this.alvo = alvo;

    if (!alvo || alvo.termo.length < MINIMO_PARA_BUSCAR) {
      this.aberto.set(false);
      this.carregando.set(false);
      return;
    }

    this.aberto.set(true);
    this.carregando.set(true);
    this.termos.next(alvo.termo);
  }

  private mover(passo: number): void {
    const total = this.sugestoes().length;
    if (!total) return;
    this.indiceAtivo.update((indice) => (indice + passo + total) % total);
  }
}
