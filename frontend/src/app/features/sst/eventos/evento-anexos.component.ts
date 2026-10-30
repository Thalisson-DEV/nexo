import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, OnInit, ViewChild, inject, input, output, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { ImageModule } from 'primeng/image';
import { TooltipModule } from 'primeng/tooltip';
import { CesConfirmationService, NotificationService } from '@ces/ui';

import { getErrorMessage } from '../../../core/models/shared.models';
import {
  AnexoEventoResponse,
  CONTEXTO_ANEXO_EVENTO_LABELS,
  ContextoAnexoEvento
} from '../../../core/models/sst-evento.models';
import { SstService } from '../../../core/services/sst.service';

interface SecaoAnexo {
  contexto: ContextoAnexoEvento;
  label: string;
  icon: string;
  descricao: string;
}

/**
 * Evidências do evento, agrupadas pelos três contextos.
 *
 * O envio é **pós-registro** — diferente da evidência de inspeção, que sobe antes por
 * nascer offline em campo. Aqui o acidente é registrado da base, no desktop, depois do
 * fato: não há o que ganhar com pré-upload, e haveria arquivo órfão a expurgar.
 *
 * Evento concluído ou cancelado não aceita anexo nem remoção; a tela desabilita antes
 * de o backend recusar.
 */
@Component({
  selector: 'app-evento-anexos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ButtonModule, ImageModule, TooltipModule],
  template: `
    <div class="flex flex-col gap-3">
      @for (secao of secoes; track secao.contexto) {
        @if (canWrite() || anexosDe(secao.contexto).length) {
          <div class="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/60 p-3.5">
            <div class="flex items-center justify-between gap-2 mb-2.5">
              <div class="flex flex-col">
                <span class="inline-flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                  <i class="pi text-xs text-primary-700 dark:text-primary-400" [ngClass]="secao.icon"></i> {{ secao.label }}
                </span>
                <span class="text-[10px] text-slate-400 dark:text-slate-500">{{ secao.descricao }}</span>
              </div>
              @if (canWrite()) {
                <button type="button" (click)="escolherArquivo(secao.contexto)" [disabled]="enviando()"
                  class="inline-flex items-center gap-1.5 text-[10px] font-semibold text-primary-700 dark:text-primary-300 bg-primary-50 dark:bg-primary-950/60 hover:bg-primary-100 dark:hover:bg-primary-900/60 border border-primary-200 dark:border-primary-800/60 px-3 py-1.5 rounded-lg transition-colors disabled:opacity-40 shadow-2xs">
                  <i class="pi pi-paperclip text-[11px]"></i> Anexar
                </button>
              }
            </div>

            @if (!anexosDe(secao.contexto).length) {
              <div class="py-2 text-[10px] text-slate-400 dark:text-slate-500 italic">
                Nenhuma evidência anexada nesta seção.
              </div>
            }

            @if (fotosDe(secao.contexto).length) {
              <div class="flex flex-wrap gap-2.5 mb-2.5">
                @for (foto of fotosDe(secao.contexto); track foto.id) {
                  <div class="relative group w-16 h-16 rounded-lg overflow-hidden border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 shadow-2xs">
                    @if (thumbs()[foto.id]; as url) {
                      <p-image [src]="url" [preview]="true" appendTo="body" alt="Evidência" width="64"
                        [imageStyle]="{ width: '64px', height: '64px', objectFit: 'cover' }" />
                    } @else {
                      <div class="w-full h-full flex items-center justify-center">
                        <i class="pi pi-spin pi-spinner text-slate-400 dark:text-slate-500 text-sm"></i>
                      </div>
                    }
                    @if (canWrite()) {
                      <button type="button" (click)="confirmarRemocao(foto)" pTooltip="Remover"
                        class="absolute top-1 right-1 w-5 h-5 rounded-full bg-red-600 text-white flex items-center justify-center shadow-sm ring-1 ring-white/70 dark:ring-slate-900 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                        <i class="pi pi-times text-[9px]"></i>
                      </button>
                    }
                  </div>
                }
              </div>
            }

            @if (documentosDe(secao.contexto).length) {
              <div class="flex flex-col gap-1.5">
                @for (doc of documentosDe(secao.contexto); track doc.id) {
                  <div class="flex items-center gap-2.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 shadow-2xs">
                    <i class="pi pi-file text-slate-400 dark:text-slate-500 text-sm shrink-0"></i>
                    <button type="button" (click)="baixar(doc)" class="flex-1 min-w-0 text-left">
                      <span class="block text-[11px] font-medium text-slate-700 dark:text-slate-200 truncate hover:text-primary-700 dark:hover:text-primary-400 transition-colors">{{ doc.nomeArquivo }}</span>
                      <span class="block text-[9px] text-slate-400 dark:text-slate-500">{{ formatarTamanho(doc.tamanhoBytes) }}</span>
                    </button>
                    <button type="button" (click)="baixar(doc)" pTooltip="Baixar"
                      class="w-7 h-7 rounded-md flex items-center justify-center text-slate-500 dark:text-slate-400 hover:text-primary-700 dark:hover:text-primary-400 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors shrink-0">
                      <i class="pi pi-download text-xs"></i>
                    </button>
                    @if (canWrite()) {
                      <button type="button" (click)="confirmarRemocao(doc)" pTooltip="Remover"
                        class="w-7 h-7 rounded-md flex items-center justify-center text-slate-500 dark:text-slate-400 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 transition-colors shrink-0">
                        <i class="pi pi-trash text-xs"></i>
                      </button>
                    }
                  </div>
                }
              </div>
            }
          </div>
        }
      }

      <input #fileInput type="file" [accept]="accept" class="hidden" (change)="onArquivoSelecionado($event)" />
    </div>
  `
})
export class EventoAnexosComponent implements OnInit {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  private readonly sst = inject(SstService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(CesConfirmationService);

  readonly idEvento = input.required<string>();
  readonly canWrite = input(false);
  readonly anexosChange = output<AnexoEventoResponse[]>();

  protected readonly lista = signal<AnexoEventoResponse[]>([]);
  protected readonly thumbs = signal<Record<string, string>>({});
  protected readonly enviando = signal(false);

  protected readonly accept = 'image/jpeg,image/png,image/webp,image/heic,image/heif,application/pdf,.doc,.docx';

  protected readonly secoes: SecaoAnexo[] = [
    { contexto: 'EVENTO', label: 'Evento', icon: 'pi-camera', descricao: 'Fotos do local, boletim de ocorrência, croqui' },
    { contexto: 'CAT', label: 'CAT', icon: 'pi-file-check', descricao: 'Comunicação de Acidente de Trabalho emitida' },
    { contexto: 'TRATATIVA', label: 'Tratativa', icon: 'pi-wrench', descricao: 'Comprovação do que foi feito depois' }
  ];

  private contextoAlvo: ContextoAnexoEvento = 'EVENTO';

  ngOnInit(): void {
    this.sst.listarAnexosEvento(this.idEvento()).subscribe({
      next: (anexos) => {
        this.lista.set(anexos);
        this.carregarThumbs();
      },
      error: () => undefined
    });
  }

  protected anexosDe(contexto: ContextoAnexoEvento): AnexoEventoResponse[] {
    return this.lista().filter((anexo) => anexo.contexto === contexto);
  }

  protected fotosDe(contexto: ContextoAnexoEvento): AnexoEventoResponse[] {
    return this.anexosDe(contexto).filter((anexo) => anexo.tipo === 'FOTO');
  }

  protected documentosDe(contexto: ContextoAnexoEvento): AnexoEventoResponse[] {
    return this.anexosDe(contexto).filter((anexo) => anexo.tipo !== 'FOTO');
  }

  protected escolherArquivo(contexto: ContextoAnexoEvento): void {
    this.contextoAlvo = contexto;
    this.fileInput.nativeElement.value = '';
    this.fileInput.nativeElement.click();
  }

  protected onArquivoSelecionado(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    if (!arquivo) return;

    if (arquivo.size > 15 * 1024 * 1024) {
      this.notifications.warning('O limite por arquivo é de 15 MB.', {
        title: 'Arquivo muito grande'
      });
      return;
    }

    this.enviando.set(true);
    this.sst.anexarEvidenciaEvento(this.idEvento(), this.contextoAlvo, arquivo).subscribe({
      next: (anexo) => {
        this.enviando.set(false);
        this.lista.update((atual) => [...atual, anexo]);
        this.anexosChange.emit(this.lista());
        this.carregarThumbs();
        this.notifications.success(
          `${anexo.nomeArquivo} anexado em ${CONTEXTO_ANEXO_EVENTO_LABELS[anexo.contexto]}.`,
          { title: 'Evidência anexada' }
        );
      },
      error: (err) => {
        this.enviando.set(false);
        this.notifications.error(getErrorMessage(err), { title: 'Não foi possível anexar' });
      }
    });
  }

  protected async confirmarRemocao(anexo: AnexoEventoResponse): Promise<void> {
    const confirmed = await this.confirm.confirm({
      title: 'Remover evidência',
      message: `Deseja remover a evidência "${anexo.nomeArquivo}"?`,
      confirmLabel: 'Remover',
      cancelLabel: 'Cancelar',
      variant: 'destructive'
    });
    if (confirmed) {
      this.remover(anexo);
    }
  }

  private remover(anexo: AnexoEventoResponse): void {
    this.sst.removerAnexoEvento(this.idEvento(), anexo.id).subscribe({
      next: () => {
        this.lista.update((atual) => atual.filter((item) => item.id !== anexo.id));
        this.anexosChange.emit(this.lista());
        this.notifications.success(`Evidência "${anexo.nomeArquivo}" removida com sucesso.`);
      },
      error: (err) =>
        this.notifications.error(getErrorMessage(err), { title: 'Não foi possível remover' })
    });
  }

  protected baixar(anexo: AnexoEventoResponse): void {
    this.sst.baixarAnexoEvento(this.idEvento(), anexo.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (err) =>
        this.notifications.error(getErrorMessage(err), { title: 'Não foi possível baixar' })
    });
  }

  /** As miniaturas passam pelo backend porque o objeto no R2 não tem URL pública. */
  private carregarThumbs(): void {
    this.lista()
      .filter((anexo) => anexo.tipo === 'FOTO' && !this.thumbs()[anexo.id])
      .forEach((anexo) => {
        this.sst.baixarAnexoEvento(this.idEvento(), anexo.id).subscribe({
          next: (blob) =>
            this.thumbs.update((atual) => ({ ...atual, [anexo.id]: URL.createObjectURL(blob) })),
          error: () => undefined
        });
      });
  }

  protected formatarTamanho(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}
