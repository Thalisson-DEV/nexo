import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { MessageService } from 'primeng/api';
import { TooltipModule } from 'primeng/tooltip';

import { SstService } from '../../../core/services/sst.service';
import {
  EVIDENCIA_ACCEPT,
  comprimirImagemAnexo,
  evidenciaRenderizavelNoBrowser,
  validarEvidenciaInspecao
} from '../../../core/models/sst.models';
import { getErrorMessage } from '../../../core/models/shared.models';

/** Uma foto na área de upload da tela de lançamento. */
interface EvidenciaUploadItem {
  idLocal: string;
  arquivo: File;
  nome: string;
  tamanhoBytes: number;
  /** `null` para HEIC/HEIF — o browser desktop não renderiza em `<img>`. */
  previewUrl: string | null;
  progresso: number;
  estado: 'ENVIANDO' | 'ENVIADA' | 'ERRO';
  /** Preenchido no 201 do upload; é o que vai em `evidencias[]` do lançamento. */
  idRemoto: string | null;
  isExistente?: boolean;
  erro?: string;
}

let seqEvidencia = 0;

/**
 * Área de upload das evidências fotográficas obrigatórias da inspeção.
 *
 * Cada foto sobe **antes** do lançamento, em requisição própria
 * (`POST /v1/sst/inspecoes/anexos`), e devolve um `id`. O lançamento carrega
 * apenas os IDs — assim uma queda no meio de um upload não derruba a inspeção
 * inteira e cada foto tem retentativa própria.
 *
 * Herda `MessageService` do componente pai (toasts no `<p-toast>` da tela).
 */
@Component({
  selector: 'app-evidencias-inspecao-upload',
  standalone: true,
  imports: [CommonModule, TooltipModule],
  template: `
    <div class="rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden">
      <!-- Cabeçalho do painel (mesmo padrão do bloco de inspecionados) -->
      <div class="flex items-center justify-between gap-2 bg-slate-50/80 dark:bg-slate-900/60 border-b border-slate-200 dark:border-slate-800 px-4 md:px-5 py-2.5">
        <div class="flex items-center gap-2 min-w-0">
          <i class="pi pi-camera text-primary-700 dark:text-primary-400"></i>
          <span class="text-[11px] font-medium text-slate-700 dark:text-slate-200 uppercase tracking-widest">
            Evidências Fotográficas <span class="text-red-500">*</span>
          </span>
        </div>
        <div class="flex items-center gap-2 shrink-0">
          @if (!temEvidencia()) {
            <span class="hidden sm:inline text-[10px] font-medium text-amber-600 dark:text-amber-400">Ao menos 1 foto é obrigatória</span>
          }
          <span class="inline-flex items-center justify-center min-w-6 h-6 px-2 rounded-full text-[11px] font-medium border border-transparent"
                [ngClass]="temEvidencia() ? 'bg-primary-100 dark:bg-primary-950/60 text-primary-700 dark:text-primary-400 dark:border-primary-800/40' : 'bg-amber-100 dark:bg-amber-950/60 text-amber-700 dark:text-amber-400 dark:border-amber-800/40'">
            {{ enviadas().length }}
          </span>
        </div>
      </div>

      <div class="p-4 md:p-5 flex flex-col gap-4 bg-white dark:bg-slate-900">
        <!-- Seleção e arrastar-e-soltar (mesmo empty-state tracejado das demais listas) -->
        <div
          (click)="escolherArquivos()"
          (dragover)="onDragOver($event)"
          (dragleave)="onDragLeave($event)"
          (drop)="onDrop($event)"
          class="flex flex-col items-center justify-center gap-2 py-8 text-center rounded-lg border border-dashed cursor-pointer transition-colors"
          [ngClass]="arrastando()
            ? 'border-primary-400 dark:border-primary-500 bg-primary-50/60 dark:bg-primary-950/40'
            : 'border-slate-200 dark:border-slate-700 bg-slate-50/50 dark:bg-slate-800/30 hover:bg-slate-50 dark:hover:bg-slate-800/60'">
          <i class="pi pi-cloud-upload text-2xl text-slate-300 dark:text-slate-600"></i>
          <span class="text-[11px] font-medium text-slate-500 dark:text-slate-400">Arraste as fotos aqui ou clique para selecionar</span>
          <span class="text-[10px] text-slate-400 dark:text-slate-500">JPEG, PNG, WebP ou HEIC · até 15 MB por arquivo</span>
        </div>

        <span class="text-[10px] text-slate-400 dark:text-slate-500 flex items-center gap-1">
          <i class="pi pi-info-circle"></i> A foto comprova que a inspeção aconteceu em campo.
        </span>

        <!-- Miniaturas (mesmo padrão das evidências de desvio) -->
        @if (itens().length) {
          <div class="flex items-center gap-2.5 flex-wrap pt-1 border-t border-slate-100 dark:border-slate-800">
            @for (item of itens(); track item.idLocal) {
              <div class="relative group w-16 mt-3">
                <div class="relative w-16 h-16 rounded-lg overflow-hidden border shadow-sm bg-white dark:bg-slate-800"
                     [ngClass]="item.estado === 'ERRO' ? 'border-red-400 dark:border-red-600' : 'border-slate-300 dark:border-slate-700'"
                     [pTooltip]="item.estado === 'ERRO' ? (item.erro || 'Falha no envio') : item.nome"
                     tooltipPosition="top">
                  @if (item.previewUrl) {
                    <img [src]="item.previewUrl" class="w-full h-full object-cover" [alt]="item.nome" />
                  } @else {
                    <div class="w-full h-full flex flex-col items-center justify-center gap-0.5 text-slate-400 dark:text-slate-500">
                      <i class="pi pi-image text-base"></i>
                      <span class="text-[8px] font-medium uppercase tracking-tight">HEIC</span>
                    </div>
                  }

                  @if (item.estado === 'ENVIANDO') {
                    <div class="absolute inset-0 bg-slate-900/50 flex items-center justify-center">
                      <span class="text-[10px] font-semibold text-white">{{ item.progresso }}%</span>
                    </div>
                  } @else if (item.estado === 'ERRO') {
                    <button type="button" (click)="reenviar(item)"
                      class="absolute inset-0 bg-red-900/50 flex flex-col items-center justify-center gap-0.5 text-white">
                      <i class="pi pi-refresh text-xs"></i>
                      <span class="text-[8px] font-semibold uppercase tracking-tight">Tentar</span>
                    </button>
                  }

                  <button type="button" (click)="remover(item); $event.stopPropagation()" pTooltip="Remover evidência"
                    tooltipPosition="top"
                    class="absolute top-1 right-1 w-6 h-6 rounded-full bg-red-600 text-white flex items-center justify-center ring-1 ring-white/70 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity shadow">
                    <i class="pi pi-trash text-[10px]"></i>
                  </button>
                </div>

                <!-- Barra de progresso individual -->
                <div class="h-1 w-full rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden mt-1">
                  <div class="h-full rounded-full transition-[width] duration-200"
                       [ngClass]="item.estado === 'ERRO' ? 'bg-red-500' : (item.estado === 'ENVIADA' ? 'bg-emerald-500' : 'bg-primary-700 dark:bg-primary-500')"
                       [style.width.%]="item.estado === 'ERRO' ? 100 : item.progresso"></div>
                </div>
                <span class="block text-[9px] text-slate-400 dark:text-slate-500 truncate mt-0.5" [title]="item.nome">
                  {{ formatarTamanho(item.tamanhoBytes) }}
                </span>
              </div>
            }
          </div>
        }
      </div>

      <input #fileInput type="file" [accept]="acceptAttr" multiple class="hidden"
        (change)="onArquivosSelecionados($event)" />
    </div>
  `
})
export class EvidenciasInspecaoUploadComponent implements OnDestroy {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  private sst = inject(SstService);
  private messageService = inject(MessageService);
  private cdr = inject(ChangeDetectorRef);

  protected readonly acceptAttr = EVIDENCIA_ACCEPT;
  protected readonly itens = signal<EvidenciaUploadItem[]>([]);
  protected readonly arrastando = signal(false);

  /** Fotos com `idRemoto` — as únicas que podem entrar no lançamento. */
  readonly enviadas = computed(() => this.itens().filter((i) => i.estado === 'ENVIADA' && i.idRemoto));
  /** Há upload em andamento (o botão de lançar espera terminar). */
  readonly enviando = computed(() => this.itens().some((i) => i.estado === 'ENVIANDO'));
  /** Requisito mínimo da regra: ao menos uma foto já no servidor. */
  readonly temEvidencia = computed(() => this.enviadas().length > 0);

  /** IDs para `RegistrarInspecaoRequest.evidencias`. */
  ids(): string[] {
    return this.enviadas().map((i) => i.idRemoto!);
  }

  /**
   * Reenvia todos os blobs e devolve os novos IDs. Usado quando o lançamento
   * responde 404: a foto foi expurgada (mais de 72 h pendente) ou descartada, e
   * os arquivos originais continuam em memória.
   */
  async reenviarTodas(): Promise<string[]> {
    const alvos = this.itens().filter((i) => i.estado !== 'ENVIANDO');
    alvos.forEach((item) => this.atualizar(item.idLocal, { idRemoto: null }));
    await Promise.all(alvos.map((item) => this.enviar(item)));
    return this.ids();
  }

  /** Carrega evidências já existentes de uma inspeção em modo de edição */
  carregarExistentes(evidencias: { id: string; nomeArquivo: string; tamanhoBytes: number }[], idInspecao: string) {
    const novos: EvidenciaUploadItem[] = evidencias.map((ev) => ({
      idLocal: `existente-${ev.id}`,
      arquivo: new File([], ev.nomeArquivo),
      nome: ev.nomeArquivo,
      tamanhoBytes: ev.tamanhoBytes,
      previewUrl: null,
      progresso: 100,
      estado: 'ENVIADA',
      idRemoto: ev.id,
      isExistente: true
    }));
    this.itens.set(novos);

    // Carregar previews das fotos existentes assincronamente via blob autenticado
    novos.forEach((item) => {
      this.sst.baixarEvidenciaInspecao(idInspecao, item.idRemoto!).subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          this.atualizar(item.idLocal, { previewUrl: url });
          this.cdr.detectChanges();
        },
        error: () => {}
      });
    });
  }

  ngOnDestroy() {
    for (const item of this.itens()) {
      if (item.previewUrl) URL.revokeObjectURL(item.previewUrl);
    }
  }

  protected escolherArquivos() {
    this.fileInput.nativeElement.value = '';
    this.fileInput.nativeElement.click();
  }

  protected onDragOver(event: DragEvent) {
    event.preventDefault();
    this.arrastando.set(true);
  }

  protected onDragLeave(event: DragEvent) {
    event.preventDefault();
    this.arrastando.set(false);
  }

  protected onDrop(event: DragEvent) {
    event.preventDefault();
    this.arrastando.set(false);
    const arquivos = Array.from(event.dataTransfer?.files ?? []);
    if (arquivos.length) void this.adicionar(arquivos);
  }

  protected onArquivosSelecionados(event: Event) {
    const input = event.target as HTMLInputElement;
    const arquivos = Array.from(input.files ?? []);
    input.value = '';
    if (arquivos.length) void this.adicionar(arquivos);
  }

  private async adicionar(arquivos: File[]) {
    for (const original of arquivos) {
      // Valida antes de gastar upload — o backend recusaria com 422 de qualquer forma.
      const erro = validarEvidenciaInspecao(original);
      if (erro) {
        this.messageService.add({
          severity: 'warn',
          summary: 'Arquivo recusado',
          detail: `${original.name}: ${erro}`
        });
        continue;
      }

      // Fotos de celular passam fácil de 10 MB; recomprime mantendo a orientação EXIF.
      const arquivo = await comprimirImagemAnexo(original);
      const renderizavel = evidenciaRenderizavelNoBrowser(arquivo.type, arquivo.name);
      const item: EvidenciaUploadItem = {
        idLocal: `ev_${++seqEvidencia}`,
        arquivo,
        nome: arquivo.name,
        tamanhoBytes: arquivo.size,
        previewUrl: renderizavel ? URL.createObjectURL(arquivo) : null,
        progresso: 0,
        estado: 'ENVIANDO',
        idRemoto: null
      };
      this.itens.update((l) => [...l, item]);
      this.cdr.detectChanges();
      void this.enviar(item);
    }
  }

  /** Um `POST /anexos` por arquivo, em paralelo, com progresso individual. */
  private enviar(item: EvidenciaUploadItem): Promise<void> {
    this.atualizar(item.idLocal, { estado: 'ENVIANDO', progresso: 0, erro: undefined });
    return new Promise((resolve) => {
      this.sst.enviarEvidenciaPendente(item.arquivo).subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            this.atualizar(item.idLocal, {
              progresso: Math.min(99, Math.round((event.loaded / event.total) * 100))
            });
            this.cdr.detectChanges();
          } else if (event.type === HttpEventType.Response && event.body) {
            this.atualizar(item.idLocal, {
              estado: 'ENVIADA',
              progresso: 100,
              idRemoto: event.body.id
            });
            this.cdr.detectChanges();
          }
        },
        error: (err) => {
          this.atualizar(item.idLocal, { estado: 'ERRO', erro: getErrorMessage(err) });
          this.cdr.detectChanges();
          this.messageService.add({
            severity: 'error',
            summary: 'Falha ao enviar evidência',
            detail: `${item.nome}: ${getErrorMessage(err)}`
          });
          resolve();
        },
        complete: () => resolve()
      });
    });
  }

  protected reenviar(item: EvidenciaUploadItem) {
    void this.enviar(item);
  }

  /** Remover antes do lançamento/salvamento = tirar da lista local + descartar no backend se for foto nova pendente. */
  protected remover(item: EvidenciaUploadItem) {
    this.itens.update((l) => l.filter((i) => i.idLocal !== item.idLocal));
    if (item.previewUrl) URL.revokeObjectURL(item.previewUrl);
    if (item.idRemoto && !item.isExistente) {
      this.sst.descartarEvidenciaPendente(item.idRemoto).subscribe({
        error: (err) =>
          this.messageService.add({
            severity: 'warn',
            summary: 'Foto removida da lista',
            detail: `Não foi possível descartá-la no servidor: ${getErrorMessage(err)}`
          })
      });
    }
    this.cdr.detectChanges();
  }

  private atualizar(idLocal: string, patch: Partial<EvidenciaUploadItem>) {
    this.itens.update((l) => l.map((i) => (i.idLocal === idLocal ? { ...i, ...patch } : i)));
  }

  protected formatarTamanho(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}
