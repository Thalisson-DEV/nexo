import { CommonModule, DatePipe } from '@angular/common';
import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked
} from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { ConfirmPopupModule } from 'primeng/confirmpopup';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { Observable } from 'rxjs';

import { SstService } from '../../../core/services/sst.service';
import {
  ContextoAnexo,
  EVIDENCIA_ACCEPT,
  EvidenciaInspecaoResponse,
  TipoAnexo,
  comprimirImagemAnexo,
  evidenciaRenderizavelNoBrowser,
  validarEvidenciaInspecao
} from '../../../core/models/sst.models';
import { getErrorMessage } from '../../../core/models/shared.models';

/**
 * Anexo de um desvio já resolvido com o contexto do checklist (enunciado da
 * pergunta e opção marcada) — montado pela tela de detalhe, que é quem tem o
 * formulário carregado.
 */
export interface AnexoDesvioGaleria {
  idDesvio: string;
  idAnexo: string;
  tipo: TipoAnexo;
  contexto: ContextoAnexo;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  dataCriacao: string;
  perguntaEnunciado: string;
  opcaoTexto: string;
}

/** Item normalizado da galeria — origem define rótulo, rodapé e permissões. */
interface ItemGaleria {
  chave: string;
  origem: 'INSPECAO' | 'DESVIO';
  id: string;
  idDesvio?: string;
  ehFoto: boolean;
  renderizavel: boolean;
  nomeArquivo: string;
  tamanhoBytes: number;
  dataCriacao: string;
  titulo: string;
  subtitulo: string;
}

/**
 * Seção **Evidências e Anexos** da tela de detalhe: uma galeria única com as
 * fotos que comprovam a realização da inspeção e os anexos dos desvios, cada
 * card marcado com a sua origem.
 *
 * As imagens são servidas pelo backend sob o mesmo JWT — não existe URL pública,
 * então cada arquivo é buscado com o interceptor de autenticação e exibido via
 * `ObjectURL` (revogado ao destruir o componente).
 *
 * Só as evidências da inspeção são editáveis aqui; os anexos de desvio são
 * gerenciados no card do próprio desvio. E a **última** evidência não pode ser
 * removida: o botão fica desabilitado quando resta uma só, e o `422` do backend
 * ainda é tratado (outro usuário pode ter removido uma foto no meio do caminho).
 *
 * Herda `MessageService` do componente pai (toasts no `<p-toast>` da tela).
 */
@Component({
  selector: 'app-evidencias-inspecao',
  standalone: true,
  imports: [CommonModule, DatePipe, ButtonModule, ConfirmPopupModule, DialogModule, TooltipModule],
  providers: [ConfirmationService],
  template: `
    <div class="mb-8 px-1">
      <div class="flex items-center justify-between gap-2 mb-4 flex-wrap">
        <div class="flex items-center gap-2 border-l-2 border-primary-700 dark:border-primary-500 pl-3">
          <span class="text-sm font-bold uppercase tracking-tight text-gray-800 dark:text-slate-100 print:text-xs">Evidências e Anexos</span>
          @if (itens().length) {
            <span class="text-xs font-semibold text-primary-700 bg-primary-50 dark:text-primary-300 dark:bg-primary-950/60 dark:border-primary-800/60 px-2.5 py-0.5 rounded-full border border-primary-100 print:border-slate-300">
              {{ itens().length }} {{ itens().length === 1 ? 'arquivo' : 'arquivos' }}
            </span>
          }
        </div>
        @if (canWrite()) {
          <button type="button" (click)="escolherArquivos()" [disabled]="enviando() > 0"
            class="inline-flex items-center gap-1.5 text-[11px] font-semibold text-primary-700 bg-primary-50 hover:bg-primary-100 dark:text-primary-300 dark:bg-primary-950/60 dark:hover:bg-primary-900/60 dark:border dark:border-primary-800/60 px-3 py-1.5 rounded-lg transition-colors disabled:opacity-40 print:hidden">
            @if (enviando() > 0) {
              <i class="pi pi-spin pi-spinner text-[11px]"></i> Enviando...
            } @else {
              <i class="pi pi-camera text-[11px]"></i> Acrescentar foto da inspeção
            }
          </button>
        }
      </div>

      @if (!evidencias().length) {
        <!-- Inspeção anterior à exigência: aviso discreto, não é erro nem pendência. -->
        <div class="flex items-center gap-2 rounded-lg border border-dashed border-slate-200 dark:border-slate-700 bg-slate-50/60 dark:bg-slate-900/40 px-4 py-3 mb-4">
          <i class="pi pi-info-circle text-slate-400 dark:text-slate-500 text-xs"></i>
          <span class="text-[11px] text-slate-500 dark:text-slate-400">Inspeção anterior à exigência de evidência fotográfica.</span>
        </div>
      }

      @if (itens().length) {
        <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 print:grid-cols-3">
          @for (item of itens(); track item.chave) {
            <div class="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 overflow-hidden shadow-sm flex flex-col break-inside-avoid print:border-slate-300 print:shadow-none">
              @if (item.ehFoto) {
                @if (item.renderizavel) {
                  <div (click)="abrir(item)" class="relative h-44 w-full bg-slate-100 dark:bg-slate-800 overflow-hidden cursor-pointer group print:cursor-default">
                    @if (thumbs()[item.chave]; as url) {
                      <img [src]="url" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300 print:scale-100" [alt]="item.nomeArquivo" />
                      <div class="absolute inset-0 bg-black/35 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center print:hidden">
                        <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/95 text-slate-900 text-xs font-semibold shadow">
                          <i class="pi pi-search-plus text-blue-600"></i> Visualizar
                        </span>
                      </div>
                    } @else {
                      <div class="w-full h-full flex flex-col items-center justify-center gap-2 bg-slate-50 dark:bg-slate-800">
                        <i class="pi pi-spin pi-spinner text-slate-400 text-xl"></i>
                        <span class="text-[10px] text-slate-400 font-medium">Carregando imagem...</span>
                      </div>
                    }
                  </div>
                } @else {
                  <!-- HEIC/HEIF não renderiza em <img> no desktop: cartão com ícone e nome. -->
                  <div class="h-44 w-full bg-slate-50 dark:bg-slate-800/60 flex flex-col items-center justify-center gap-2 px-3 text-center">
                    <i class="pi pi-image text-3xl text-slate-300 dark:text-slate-600"></i>
                    <span class="text-[11px] font-medium text-slate-600 dark:text-slate-300 break-all line-clamp-2">{{ item.nomeArquivo }}</span>
                    <button type="button" (click)="baixar(item)" class="text-[10px] font-semibold text-primary-700 dark:text-primary-400 hover:underline print:hidden">
                      Baixar para visualizar
                    </button>
                  </div>
                }
              } @else {
                <div class="h-44 w-full bg-slate-50 dark:bg-slate-800/60 flex flex-col items-center justify-center gap-2.5 px-3 text-center">
                  <div class="w-14 h-14 rounded-xl flex items-center justify-center shadow-sm"
                       [ngClass]="ehPdf(item) ? 'bg-red-50 text-red-600 border border-red-100 dark:bg-red-950/50 dark:text-red-400 dark:border-red-900/60' : 'bg-blue-50 text-blue-600 border border-blue-100 dark:bg-blue-950/50 dark:text-blue-400 dark:border-blue-900/60'">
                    <i [class]="iconeArquivo(item.nomeArquivo)" class="text-2xl"></i>
                  </div>
                  <button type="button" (click)="baixar(item)" class="inline-flex items-center gap-1.5 text-[10px] font-semibold text-primary-700 dark:text-primary-400 hover:underline print:hidden">
                    <i class="pi pi-download text-[10px]"></i> Abrir documento
                  </button>
                </div>
              }

              <div class="p-3.5 flex flex-col gap-1.5 flex-1 bg-white dark:bg-slate-900">
                <div class="flex items-center justify-between gap-1">
                  @if (item.origem === 'INSPECAO') {
                    <span class="inline-flex items-center gap-1 text-[9px] font-bold uppercase tracking-wider text-primary-700 bg-primary-50 dark:text-primary-300 dark:bg-primary-950/60 dark:border-primary-800/60 px-2 py-0.5 rounded border border-primary-100">
                      <i class="pi pi-camera text-[9px]"></i> Inspeção
                    </span>
                  } @else {
                    <span class="inline-flex items-center gap-1 text-[9px] font-bold uppercase tracking-wider text-amber-700 bg-amber-50 dark:text-amber-300 dark:bg-amber-950/60 dark:border-amber-800/60 px-2 py-0.5 rounded border border-amber-100">
                      <i class="pi pi-exclamation-triangle text-[9px]"></i> Desvio
                    </span>
                  }
                  <span class="text-[10px] text-slate-400 font-medium">{{ formatarTamanho(item.tamanhoBytes) }}</span>
                </div>
                <span class="text-xs font-semibold text-slate-800 dark:text-slate-100 line-clamp-2 leading-snug mt-1" [title]="item.titulo">{{ item.titulo }}</span>
                <span class="text-[11px] text-slate-500 dark:text-slate-400 line-clamp-1" [title]="item.subtitulo">{{ item.subtitulo }}</span>
                <div class="mt-auto pt-2 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between gap-2">
                  <span class="text-[10px] text-slate-400 truncate" [title]="item.nomeArquivo">
                    {{ item.origem === 'INSPECAO' ? (item.dataCriacao | date: 'dd/MM/yy HH:mm') : item.nomeArquivo }}
                  </span>
                  @if (canWrite() && item.origem === 'INSPECAO') {
                    <span [pTooltip]="ehUnica()
                            ? 'A última evidência não pode ser removida — toda inspeção precisa de ao menos uma foto. Para trocar, anexe a nova antes de apagar a antiga.'
                            : 'Remover evidência'"
                          tooltipPosition="top" class="print:hidden shrink-0">
                      <button type="button" (click)="confirmarRemocao($event, item)" [disabled]="ehUnica()"
                        class="inline-flex items-center gap-1 px-2 py-1 rounded-md text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:text-red-400 dark:hover:bg-red-950/50 transition-colors disabled:opacity-40 disabled:hover:text-slate-400 disabled:hover:bg-transparent disabled:cursor-not-allowed">
                        <i class="pi pi-trash text-[10px]"></i>
                      </button>
                    </span>
                  }
                </div>
              </div>
            </div>
          }
        </div>
      }

      <input #fileInput type="file" [accept]="acceptAttr" multiple class="hidden"
        (change)="onArquivosSelecionados($event)" />
    </div>

    <p-confirmpopup></p-confirmpopup>

    <p-dialog [(visible)]="ampliadaVisible" [modal]="true" appendTo="body" [dismissableMask]="true"
      header="Evidência Fotográfica" [style]="{ width: '90vw', maxWidth: '850px' }"
      styleClass="overflow-hidden rounded-2xl print:hidden">
      @if (ampliada(); as item) {
        <div class="flex flex-col gap-4">
          <div class="w-full max-h-[72vh] flex items-center justify-center bg-slate-900 rounded-xl overflow-hidden shadow-inner">
            <img [src]="thumbs()[item.chave] || ''" class="max-h-[72vh] w-auto object-contain" [alt]="item.nomeArquivo" />
          </div>
          <div class="flex flex-col gap-1.5 bg-slate-50 dark:bg-slate-900 p-4 rounded-xl border border-slate-200 dark:border-slate-800">
            <div class="flex items-center justify-between gap-3">
              <span class="text-xs font-bold text-slate-800 dark:text-white">{{ item.titulo }}</span>
              <span class="text-[11px] text-slate-400 font-medium shrink-0">{{ formatarTamanho(item.tamanhoBytes) }}</span>
            </div>
            <span class="text-xs text-slate-600 dark:text-slate-300">{{ item.subtitulo }}</span>
            <div class="flex items-center justify-between gap-3 pt-2 mt-1 border-t border-slate-200/60 dark:border-slate-800">
              <span class="text-[11px] text-slate-400 truncate">
                Arquivo: {{ item.nomeArquivo }} · {{ item.dataCriacao | date: 'dd/MM/yyyy HH:mm' }}
              </span>
              <button type="button" (click)="baixar(item)" class="inline-flex items-center gap-1.5 text-xs font-semibold text-primary-700 dark:text-primary-400 hover:text-primary-700 dark:hover:text-primary-300 shrink-0">
                <i class="pi pi-external-link text-xs"></i> Baixar arquivo original
              </button>
            </div>
          </div>
        </div>
      }
    </p-dialog>
  `
})
export class EvidenciasInspecaoComponent implements OnInit, OnDestroy {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  private sst = inject(SstService);
  private messageService = inject(MessageService);
  private confirm = inject(ConfirmationService);
  private cdr = inject(ChangeDetectorRef);

  readonly idInspecao = input.required<string>();
  readonly evidenciasIniciais = input<EvidenciaInspecaoResponse[]>([], { alias: 'evidencias' });
  /** Anexos dos desvios — somente leitura aqui; a edição é no card do desvio. */
  readonly anexosDesvios = input<AnexoDesvioGaleria[]>([]);
  readonly canWrite = input<boolean>(false);

  /** Emite a lista atualizada para o pai sincronizar a resposta canônica. */
  readonly evidenciasChange = output<EvidenciaInspecaoResponse[]>();

  protected readonly acceptAttr = EVIDENCIA_ACCEPT;
  protected readonly evidencias = signal<EvidenciaInspecaoResponse[]>([]);
  protected readonly thumbs = signal<Record<string, string>>({});
  protected readonly enviando = signal(0);
  protected readonly ampliada = signal<ItemGaleria | null>(null);
  protected ampliadaVisible = false;

  /** Regra de domínio: a única evidência de uma inspeção não pode ser removida. */
  protected readonly ehUnica = computed(() => this.evidencias().length === 1);

  /** Galeria única: evidências da inspeção primeiro, anexos dos desvios depois. */
  protected readonly itens = computed<ItemGaleria[]>(() => [
    ...this.evidencias().map((ev) => ({
      chave: `insp:${ev.id}`,
      origem: 'INSPECAO' as const,
      id: ev.id,
      ehFoto: true,
      renderizavel: evidenciaRenderizavelNoBrowser(ev.contentType, ev.nomeArquivo),
      nomeArquivo: ev.nomeArquivo,
      tamanhoBytes: ev.tamanhoBytes,
      dataCriacao: ev.dataCriacao,
      titulo: ev.nomeArquivo,
      subtitulo: 'Comprova a realização da inspeção em campo'
    })),
    ...this.anexosDesvios().map((a) => ({
      chave: `desvio:${a.idAnexo}`,
      origem: 'DESVIO' as const,
      id: a.idAnexo,
      idDesvio: a.idDesvio,
      ehFoto: a.tipo === 'FOTO',
      renderizavel: evidenciaRenderizavelNoBrowser(a.contentType, a.nomeArquivo),
      nomeArquivo: a.nomeArquivo,
      tamanhoBytes: a.tamanhoBytes,
      dataCriacao: a.dataCriacao,
      titulo: a.perguntaEnunciado,
      subtitulo:
        a.contexto === 'ACAO_CORRETIVA' ? `Ação corretiva · ${a.opcaoTexto}` : a.opcaoTexto
    }))
  ]);

  private carregandoThumb = new Set<string>();

  constructor() {
    // Ressincroniza quando o pai recarrega a inspeção (ex.: após tratar um desvio).
    effect(() => {
      const recebidas = this.evidenciasIniciais();
      untracked(() => {
        const atuais = this.evidencias();
        if (recebidas.map((e) => e.id).join('|') === atuais.map((e) => e.id).join('|')) return;
        this.evidencias.set([...recebidas]);
      });
    });

    // Carrega as miniaturas de tudo que entra na galeria, das duas origens.
    effect(() => {
      const itens = this.itens();
      if (itens.length) untracked(() => this.carregarThumbs());
    });
  }

  ngOnInit() {
    this.evidencias.set([...this.evidenciasIniciais()]);
  }

  ngOnDestroy() {
    Object.values(this.thumbs()).forEach((url) => URL.revokeObjectURL(url));
  }

  /** Cada origem tem o seu endpoint de download — ambos autenticados. */
  private baixarBinario(item: ItemGaleria): Observable<Blob> {
    return item.origem === 'INSPECAO'
      ? this.sst.baixarEvidenciaInspecao(this.idInspecao(), item.id)
      : this.sst.baixarAnexo(this.idInspecao(), item.idDesvio!, item.id);
  }

  private carregarThumbs() {
    for (const item of this.itens()) {
      if (!item.ehFoto || !item.renderizavel) continue;
      if (this.thumbs()[item.chave] || this.carregandoThumb.has(item.chave)) continue;
      this.carregandoThumb.add(item.chave);
      this.baixarBinario(item).subscribe({
        next: (blob) => {
          this.carregandoThumb.delete(item.chave);
          this.thumbs.update((m) => ({ ...m, [item.chave]: URL.createObjectURL(blob) }));
          this.cdr.detectChanges();
        },
        error: () => {
          this.carregandoThumb.delete(item.chave);
          this.cdr.detectChanges();
        }
      });
    }
  }

  protected abrir(item: ItemGaleria) {
    this.ampliada.set(item);
    this.ampliadaVisible = true;
  }

  protected baixar(item: ItemGaleria) {
    this.baixarBinario(item).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
      },
      error: () =>
        this.messageService.add({
          severity: 'error',
          summary: 'Falha ao baixar',
          detail: 'Não foi possível recuperar o arquivo do armazenamento.'
        })
    });
  }

  protected escolherArquivos() {
    this.fileInput.nativeElement.value = '';
    this.fileInput.nativeElement.click();
  }

  protected async onArquivosSelecionados(event: Event) {
    const input = event.target as HTMLInputElement;
    const arquivos = Array.from(input.files ?? []);
    input.value = '';
    for (const original of arquivos) {
      const erro = validarEvidenciaInspecao(original);
      if (erro) {
        this.messageService.add({
          severity: 'warn',
          summary: 'Arquivo recusado',
          detail: `${original.name}: ${erro}`
        });
        continue;
      }
      const arquivo = await comprimirImagemAnexo(original);
      this.enviar(arquivo);
    }
  }

  private enviar(arquivo: File) {
    this.enviando.update((n) => n + 1);
    this.sst.adicionarEvidenciaInspecao(this.idInspecao(), arquivo).subscribe({
      next: (ev) => {
        this.enviando.update((n) => n - 1);
        this.thumbs.update((m) => ({ ...m, [`insp:${ev.id}`]: URL.createObjectURL(arquivo) }));
        this.evidencias.update((l) => [...l, ev]);
        this.evidenciasChange.emit(this.evidencias());
        this.cdr.detectChanges();
        this.messageService.add({
          severity: 'success',
          summary: 'Evidência anexada',
          detail: ev.nomeArquivo
        });
      },
      error: (err) => {
        this.enviando.update((n) => n - 1);
        this.cdr.detectChanges();
        this.messageService.add({
          severity: 'error',
          summary: 'Falha ao anexar',
          detail: getErrorMessage(err)
        });
      }
    });
  }

  protected confirmarRemocao(event: Event, item: ItemGaleria) {
    this.confirm.confirm({
      target: event.currentTarget as EventTarget,
      message: 'Remover esta evidência?',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Remover',
      rejectLabel: 'Cancelar',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-text p-button-sm',
      accept: () => this.remover(item)
    });
  }

  private remover(item: ItemGaleria) {
    this.sst.removerEvidenciaInspecao(this.idInspecao(), item.id).subscribe({
      next: () => {
        this.evidencias.update((l) => l.filter((e) => e.id !== item.id));
        const url = this.thumbs()[item.chave];
        if (url) {
          URL.revokeObjectURL(url);
          this.thumbs.update((m) => {
            const copia = { ...m };
            delete copia[item.chave];
            return copia;
          });
        }
        this.evidenciasChange.emit(this.evidencias());
        this.cdr.detectChanges();
        this.messageService.add({ severity: 'success', summary: 'Removida', detail: 'Evidência removida.' });
      },
      error: (err) =>
        // 422 quando é a última — a UI já bloqueia, mas outro usuário pode ter
        // removido uma foto entre o carregamento da tela e este clique.
        this.messageService.add({
          severity: 'error',
          summary: 'Falha ao remover',
          detail: getErrorMessage(err)
        })
    });
  }

  protected ehPdf(item: ItemGaleria): boolean {
    return item.nomeArquivo.toLowerCase().endsWith('.pdf');
  }

  protected iconeArquivo(nome: string): string {
    const lower = (nome || '').toLowerCase();
    if (lower.endsWith('.pdf')) return 'pi pi-file-pdf';
    if (lower.endsWith('.doc') || lower.endsWith('.docx')) return 'pi pi-file-word';
    return 'pi pi-file';
  }

  protected formatarTamanho(bytes: number): string {
    if (!bytes) return '0 B';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}
