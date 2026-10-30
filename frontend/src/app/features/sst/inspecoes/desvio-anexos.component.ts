import { CommonModule } from '@angular/common';
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
import { ImageModule } from 'primeng/image';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TooltipModule } from 'primeng/tooltip';
import { retry, throwError, timer } from 'rxjs';

import { SstService } from '../../../core/services/sst.service';
import { AnexoSyncService } from '../../../core/services/anexo-sync.service';
import { PendingAnexo } from '../../../core/services/database.service';
import {
  ANEXO_ACCEPT,
  AnexoDesvioResponse,
  ContextoAnexo,
  comprimirImagemAnexo,
  validarArquivoAnexo
} from '../../../core/models/sst.models';
import { getErrorMessage } from '../../../core/models/shared.models';

interface SecaoAnexo {
  contexto: ContextoAnexo;
  label: string;
  icon: string;
}

/**
 * Galeria de evidências de um desvio: lista os anexos agrupados por contexto
 * (DESVIO / ACAO_CORRETIVA), permite anexar fotos/documentos (com validação
 * local + fila offline), baixar com autenticação e remover.
 *
 * Herda `MessageService` do componente pai (toasts no `<p-toast>` da tela).
 */
@Component({
  selector: 'app-desvio-anexos',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    ImageModule,
    TooltipModule,
    ProgressSpinnerModule,
    ConfirmPopupModule
  ],
  providers: [ConfirmationService],
  template: `
    <div class="mt-1 print:break-inside-avoid">
      <div class="flex items-center gap-2 mb-2">
        <i class="pi pi-paperclip text-[10px] text-slate-400 dark:text-slate-500"></i>
        <span class="text-[9px] font-medium text-gray-400 dark:text-slate-400 uppercase tracking-widest">Evidências</span>
        @if (lista().length) {
          <span class="text-[9px] text-gray-400 dark:text-slate-400 font-medium">({{ lista().length }})</span>
        }
        @if (pendentes() > 0) {
          <span class="inline-flex items-center gap-1 text-[9px] font-medium text-amber-600 bg-amber-50 dark:bg-amber-950/50 dark:text-amber-300 dark:border dark:border-amber-800/60 px-1.5 py-0.5 rounded print:hidden">
            <i class="pi pi-sync text-[8px]"></i> {{ pendentes() }} aguardando envio
          </span>
        }
      </div>

      <div class="flex flex-col gap-3">
        @for (secao of secoes; track secao.contexto) {
          @if (canWrite() || fotosDe(secao.contexto).length || docsDe(secao.contexto).length) {
            <div class="rounded-lg border border-slate-100 dark:border-slate-800 bg-slate-50/40 dark:bg-slate-900/60 px-3 py-2.5">
              <div class="flex items-center justify-between gap-2 mb-2">
                <span class="inline-flex items-center gap-1.5 text-[9px] font-medium uppercase tracking-tight text-slate-500 dark:text-slate-300">
                  <i class="pi" [ngClass]="secao.icon"></i> {{ secao.label }}
                </span>
                @if (canWrite()) {
                  <div class="flex items-center gap-1 print:hidden">
                    <button type="button" (click)="escolherArquivo(secao.contexto)" [disabled]="enviando() > 0"
                      class="inline-flex items-center gap-1.5 text-[10px] font-semibold text-primary-700 bg-primary-50 hover:bg-primary-100 dark:text-primary-300 dark:bg-primary-950/60 dark:hover:bg-primary-900/60 dark:border dark:border-primary-800/60 px-2.5 py-1 rounded transition-colors disabled:opacity-40">
                      <i class="pi pi-paperclip text-[11px]"></i> Anexar Evidência
                    </button>
                  </div>
                }
              </div>

              @if (!fotosDe(secao.contexto).length && !docsDe(secao.contexto).length) {
                <span class="text-[10px] text-slate-400 dark:text-slate-500">Nenhuma evidência anexada.</span>
              }

              <!-- Fotos -->
              @if (fotosDe(secao.contexto).length) {
                <div class="flex flex-wrap gap-2 mb-2">
                  @for (foto of fotosDe(secao.contexto); track foto.id) {
                    <div class="relative group w-16 h-16 rounded-md overflow-hidden border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800">
                      @if (thumbs()[foto.id]; as url) {
                        <p-image [src]="url" [preview]="true" appendTo="body" alt="Evidência" width="64"
                          [imageStyle]="{ width: '64px', height: '64px', objectFit: 'cover' }"></p-image>
                      } @else {
                        <div class="w-full h-full flex items-center justify-center">
                          <i class="pi pi-spin pi-spinner text-slate-300 dark:text-slate-600 text-sm"></i>
                        </div>
                      }
                      @if (canWrite()) {
                        <button type="button" (click)="confirmarRemocao($event, foto)" pTooltip="Remover"
                          class="absolute top-0.5 right-0.5 w-6 h-6 rounded-full bg-red-600 text-white flex items-center justify-center shadow-sm ring-1 ring-white/70 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity print:hidden">
                          <i class="pi pi-times text-[10px]"></i>
                        </button>
                      }
                    </div>
                  }
                </div>
              }

              <!-- Documentos -->
              @if (docsDe(secao.contexto).length) {
                <div class="flex flex-col gap-1.5">
                  @for (doc of docsDe(secao.contexto); track doc.id) {
                    <div class="flex items-center gap-2 bg-white dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700 rounded-md px-2.5 py-1.5">
                      <i class="pi pi-file-pdf text-red-500 dark:text-red-400 text-sm shrink-0"></i>
                      <button type="button" (click)="abrir(doc)" class="flex-1 min-w-0 text-left">
                        <span class="block text-[11px] font-medium text-slate-700 dark:text-slate-200 truncate hover:text-primary-700 dark:hover:text-primary-400 transition-colors">{{ doc.nomeArquivo }}</span>
                        <span class="block text-[9px] text-slate-400 dark:text-slate-400">{{ formatarTamanho(doc.tamanhoBytes) }}</span>
                      </button>
                      <button type="button" (click)="abrir(doc)" pTooltip="Baixar"
                        class="w-6 h-6 rounded flex items-center justify-center text-slate-400 hover:text-primary-700 hover:bg-primary-50 dark:hover:text-primary-400 dark:hover:bg-slate-700 transition-colors shrink-0 print:hidden">
                        <i class="pi pi-download text-[10px]"></i>
                      </button>
                      @if (canWrite()) {
                        <button type="button" (click)="confirmarRemocao($event, doc)" pTooltip="Remover"
                          class="w-6 h-6 rounded flex items-center justify-center text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:text-red-400 dark:hover:bg-red-950/50 transition-colors shrink-0 print:hidden">
                          <i class="pi pi-times text-[10px]"></i>
                        </button>
                      }
                    </div>
                  }
                </div>
              }
            </div>
          }
        }
      </div>

      <input #fileInput type="file" [accept]="acceptAttr" multiple class="hidden"
        (change)="onArquivosSelecionados($event)" />
    </div>

    <p-confirmpopup></p-confirmpopup>
  `
})
export class DesvioAnexosComponent implements OnInit, OnDestroy {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  private sst = inject(SstService);
  private anexoSync = inject(AnexoSyncService);
  private messageService = inject(MessageService);
  private confirm = inject(ConfirmationService);
  private cdr = inject(ChangeDetectorRef);

  readonly idInspecao = input.required<string>();
  readonly idDesvio = input.required<string>();
  readonly anexosIniciais = input<AnexoDesvioResponse[]>([], { alias: 'anexos' });
  readonly canWrite = input<boolean>(false);

  /** Emite a lista atualizada quando algo é adicionado/removido (para o pai sincronizar contagens). */
  readonly anexosChange = output<AnexoDesvioResponse[]>();

  protected readonly acceptAttr = ANEXO_ACCEPT;
  protected readonly secoes: SecaoAnexo[] = [
    { contexto: 'DESVIO', label: 'Não-conformidade', icon: 'pi-exclamation-circle' },
    { contexto: 'ACAO_CORRETIVA', label: 'Ação corretiva', icon: 'pi-wrench' }
  ];

  protected lista = signal<AnexoDesvioResponse[]>([]);
  protected thumbs = signal<Record<string, string>>({});
  protected enviando = signal(0);
  protected pendentes = computed(() => this.anexoSync.pendentesDoDesvio(this.idDesvio()));

  private alvoContexto: ContextoAnexo = 'DESVIO';
  private carregandoThumb = new Set<string>();

  constructor() {
    // Reconcilia a lista visível com a fila offline (AnexoSyncService):
    //  - exibe os anexos pendentes (mesmo após um reload — hidrata do IndexedDB);
    //  - remove o item temporário e injeta o anexo real quando a fila sincroniza.
    // Sem isto, uma evidência enfileirada (ex.: foto da câmera no mobile) sumia
    // ao recarregar a página, dando a impressão de que não foi salva.
    effect(() => {
      const idDesvio = this.idDesvio();
      const pendentes = this.anexoSync.pendentes().filter((p) => p.idDesvio === idDesvio);
      const sincronizados = this.anexoSync
        .sincronizados()
        .filter((s) => s.idDesvio === idDesvio)
        .map((s) => s.anexo);

      untracked(() => this.reconciliarPendentes(pendentes, sincronizados));
    });
  }

  ngOnInit() {
    this.lista.set([...this.anexosIniciais()]);
    this.carregarThumbs();
  }

  /**
   * Sincroniza `lista`/`thumbs` com o estado da fila offline. Idempotente:
   * só aplica mudanças quando o conjunto de itens realmente muda.
   */
  private reconciliarPendentes(pendentes: PendingAnexo[], sincronizados: AnexoDesvioResponse[]) {
    const atual = this.lista();
    const idsPendentes = new Set(pendentes.map((p) => 'local_q' + p.id));

    // 1) Mantém itens do servidor e temporários ainda na fila; descarta os que saíram.
    const nova = atual.filter((a) => !a.id.startsWith('local_q') || idsPendentes.has(a.id));
    const idsPresentes = new Set(nova.map((a) => a.id));
    const novosThumbs: Record<string, string> = {};

    // 2) Adiciona pendentes ainda não exibidos (inclui hidratação pós-reload).
    for (const p of pendentes) {
      const tempId = 'local_q' + p.id;
      if (idsPresentes.has(tempId)) continue;
      const isImage =
        (p.contentType || '').startsWith('image/') ||
        /\.(jpg|jpeg|png|webp|heic|heif)$/i.test(p.nomeArquivo);
      nova.push({
        id: tempId,
        contexto: p.contexto,
        nomeArquivo: p.nomeArquivo,
        contentType: p.contentType || 'application/octet-stream',
        tamanhoBytes: p.blob.size,
        tipo: isImage ? 'FOTO' : 'DOCUMENTO',
        dataCriacao: new Date(p.timestamp).toISOString()
      });
      idsPresentes.add(tempId);
      if (isImage && !this.thumbs()[tempId]) {
        novosThumbs[tempId] = URL.createObjectURL(p.blob);
      }
    }

    // 3) Injeta os anexos reais recém-sincronizados (dedupe por id).
    for (const anexo of sincronizados) {
      if (idsPresentes.has(anexo.id)) continue;
      nova.push(anexo);
      idsPresentes.add(anexo.id);
    }

    // Nada mudou? Evita re-render e libera as URLs de blob recém-criadas.
    const inalterado = nova.map((a) => a.id).join('|') === atual.map((a) => a.id).join('|');
    if (inalterado) {
      Object.values(novosThumbs).forEach((url) => URL.revokeObjectURL(url));
      return;
    }

    // Revoga thumbs de temporários que saíram da lista.
    const removidos = atual.filter((a) => a.id.startsWith('local_q') && !idsPendentes.has(a.id));
    if (Object.keys(novosThumbs).length) {
      this.thumbs.update((m) => ({ ...m, ...novosThumbs }));
    }
    if (removidos.length) {
      this.thumbs.update((m) => {
        const copia = { ...m };
        for (const a of removidos) {
          const url = copia[a.id];
          if (url) URL.revokeObjectURL(url);
          delete copia[a.id];
        }
        return copia;
      });
    }

    // `lista`/`thumbs` são signals: a renderização é agendada automaticamente.
    // NÃO chamar detectChanges() aqui — este método roda dentro de um effect,
    // durante o change detection, e chamá-lo/emitir output de forma síncrona
    // quebra o ciclo (a evidência "piscava" e sumia).
    this.lista.set(nova);
    this.carregarThumbs();

    // Notifica o pai só com anexos reais (sem temporários) e fora do ciclo de
    // CD atual, para não escrever no signal do pai durante a detecção.
    const reaisNova = nova.filter((a) => !a.id.startsWith('local_q'));
    const reaisAtual = atual.filter((a) => !a.id.startsWith('local_q'));
    if (reaisNova.map((a) => a.id).join('|') !== reaisAtual.map((a) => a.id).join('|')) {
      queueMicrotask(() => this.anexosChange.emit(reaisNova));
    }
  }

  ngOnDestroy() {
    Object.values(this.thumbs()).forEach((url) => URL.revokeObjectURL(url));
  }

  protected fotosDe(contexto: ContextoAnexo): AnexoDesvioResponse[] {
    return this.lista().filter((a) => a.contexto === contexto && a.tipo === 'FOTO');
  }

  protected docsDe(contexto: ContextoAnexo): AnexoDesvioResponse[] {
    return this.lista().filter((a) => a.contexto === contexto && a.tipo === 'DOCUMENTO');
  }

  protected escolherArquivo(contexto: ContextoAnexo) {
    this.alvoContexto = contexto;
    this.fileInput.nativeElement.value = '';
    this.fileInput.nativeElement.click();
  }

  protected async onArquivosSelecionados(event: Event) {
    const input = event.target as HTMLInputElement;
    const arquivos = Array.from(input.files ?? []);
    const contexto = this.alvoContexto;
    input.value = '';
    for (const file of arquivos) {
      // Comprime fotos (câmera/galeria) antes de enviar; demais arquivos passam intactos.
      const arquivo = await comprimirImagemAnexo(file);
      this.enviar(arquivo, contexto);
    }
  }

  private enviar(file: File, contexto: ContextoAnexo) {
    const erro = validarArquivoAnexo(file);
    if (erro) {
      this.messageService.add({ severity: 'warn', summary: 'Arquivo inválido', detail: erro });
      return;
    }

    this.enviando.update((n) => n + 1);
    this.messageService.add({ severity: 'info', summary: 'Enviando evidência', detail: file.name });
    this.sst.enviarAnexo(this.idInspecao(), this.idDesvio(), contexto, file)
      .pipe(
        retry({
          count: 3,
          delay: (error) => {
            const status = error?.status ?? 0;
            // No mobile, ao retornar da câmera nativa para o navegador, a conexão de socket/rede
            // pode levar 1 a 2 segundos para restabelecer. Aguarda 1.5s antes de tentar novamente.
            if (status === 0) {
              return timer(1500);
            }
            return throwError(() => error);
          }
        })
      )
      .subscribe({
      next: (anexo) => {
        this.enviando.update((n) => n - 1);
        if (anexo.tipo === 'FOTO' || file.type.startsWith('image/')) {
          anexo.tipo = 'FOTO';
          this.thumbs.update((m) => ({ ...m, [anexo.id]: URL.createObjectURL(file) }));
        }
        this.lista.update((l) => [...l, anexo]);
        if (anexo.tipo === 'FOTO') this.carregarThumbs();
        this.anexosChange.emit(this.lista().filter((a) => !a.id.startsWith('local_q')));
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.enviando.update((n) => n - 1);
        this.cdr.detectChanges();
        const status = err?.status ?? 0;
        if (status === 0 || status >= 500) {
          this.enfileirar(file, contexto);
        } else if (status === 413) {
          this.messageService.add({
            severity: 'error',
            summary: 'Arquivo muito grande',
            detail: 'O arquivo excede o limite de 15 MB.'
          });
        } else {
          this.messageService.add({ severity: 'error', summary: 'Falha ao anexar', detail: getErrorMessage(err) });
        }
      }
    });
  }

  private enfileirar(file: File, contexto: ContextoAnexo) {
    // Persiste na fila offline; o effect de reconciliação exibe o item pendente
    // (e o mantém visível após reload, hidratando do IndexedDB).
    this.anexoSync
      .enqueue({
        idInspecao: this.idInspecao(),
        idDesvio: this.idDesvio(),
        contexto,
        nomeArquivo: file.name,
        contentType: file.type || 'application/octet-stream',
        blob: file
      })
      .then(() =>
        this.messageService.add({
          severity: 'info',
          summary: 'Salvo em segundo plano',
          detail: 'A evidência está disponível na tela e será sincronizada automaticamente.'
        })
      );
  }

  private carregarThumbs() {
    for (const foto of this.lista().filter((a) => a.tipo === 'FOTO')) {
      // Itens locais (fila offline) usam blob-url próprio; não existem no servidor.
      if (foto.id.startsWith('local')) continue;
      if (this.thumbs()[foto.id] || this.carregandoThumb.has(foto.id)) continue;
      this.carregandoThumb.add(foto.id);
      this.sst.baixarAnexo(this.idInspecao(), this.idDesvio(), foto.id).subscribe({
        next: (blob) => {
          this.carregandoThumb.delete(foto.id);
          this.thumbs.update((m) => ({ ...m, [foto.id]: URL.createObjectURL(blob) }));
          this.cdr.detectChanges();
        },
        error: () => {
          this.carregandoThumb.delete(foto.id);
          this.cdr.detectChanges();
        }
      });
    }
  }

  protected abrir(anexo: AnexoDesvioResponse) {
    if (anexo.id.startsWith('local_')) {
      const url = this.thumbs()[anexo.id];
      if (url) window.open(url, '_blank');
      return;
    }
    this.sst.baixarAnexo(this.idInspecao(), this.idDesvio(), anexo.id).subscribe({
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

  protected confirmarRemocao(event: Event, anexo: AnexoDesvioResponse) {
    this.confirm.confirm({
      target: event.currentTarget as EventTarget,
      message: 'Remover esta evidência?',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Remover',
      rejectLabel: 'Cancelar',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-text p-button-sm',
      accept: () => this.remover(anexo)
    });
  }

  private remover(anexo: AnexoDesvioResponse) {
    if (anexo.id.startsWith('local_')) {
      // Item pendente: remove também da fila offline, senão o effect o re-exibe
      // e ele acabaria sincronizado mesmo após o usuário cancelar.
      const qId = Number(anexo.id.replace('local_q', ''));
      if (anexo.id.startsWith('local_q') && !Number.isNaN(qId)) {
        this.anexoSync.remover(qId);
      }
      this.lista.update((l) => l.filter((a) => a.id !== anexo.id));
      const url = this.thumbs()[anexo.id];
      if (url) {
        URL.revokeObjectURL(url);
        this.thumbs.update((m) => {
          const copia = { ...m };
          delete copia[anexo.id];
          return copia;
        });
      }
      this.anexosChange.emit(this.lista());
      this.cdr.detectChanges();
      this.messageService.add({ severity: 'success', summary: 'Removida', detail: 'Evidência removida.' });
      return;
    }

    this.sst.removerAnexo(this.idInspecao(), this.idDesvio(), anexo.id).subscribe({
      next: () => {
        this.lista.update((l) => l.filter((a) => a.id !== anexo.id));
        const url = this.thumbs()[anexo.id];
        if (url) {
          URL.revokeObjectURL(url);
          this.thumbs.update((m) => {
            const copia = { ...m };
            delete copia[anexo.id];
            return copia;
          });
        }
        this.anexosChange.emit(this.lista());
        this.messageService.add({ severity: 'success', summary: 'Removida', detail: 'Evidência removida.' });
      },
      error: (err) =>
        this.messageService.add({ severity: 'error', summary: 'Falha ao remover', detail: getErrorMessage(err) })
    });
  }

  protected formatarTamanho(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}
