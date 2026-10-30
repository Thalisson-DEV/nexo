import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import {
  AnexoComunicadoResponse,
  ComunicadoMuralResponse
} from '../../../core/models/notificacao.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { MuralStore } from '../../../core/services/mural-store.service';
import { FileDownloadUtil } from '../../../core/utils/file-download.util';
import { formatarTamanhoArquivo } from '../../../features/notificacoes/notificacoes-ui';

/** Corpo de um comunicado: texto, vídeo externo e anexos. Reutilizado no modal, no banner e nos cards. */
@Component({
  selector: 'app-comunicado-conteudo',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ButtonModule],
  template: `
    <div class="flex flex-col gap-4">
      <!-- max-w-[72ch]: o diálogo é largo por causa do vídeo/anexos, mas texto corrido
           fica ilegível na largura cheia. -->
      <div
        class="comunicado-conteudo w-full text-[13.5px] leading-relaxed text-slate-700 dark:text-slate-200"
        [innerHTML]="conteudoHtml()"
      ></div>

      @if (comunicado().videoUrl; as video) {
        @if (videoEmbed()) {
          <div class="relative w-full overflow-hidden rounded-xl border border-slate-200 bg-slate-900 dark:border-slate-700" style="padding-top:56.25%">
            <iframe
              class="absolute inset-0 h-full w-full"
              [src]="videoEmbed()"
              title="Vídeo do comunicado"
              frameborder="0"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowfullscreen
            ></iframe>
          </div>
        } @else {
          <a
            [href]="video"
            target="_blank"
            rel="noopener noreferrer"
            class="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[11px] font-bold text-primary-600 hover:bg-white transition-colors dark:border-slate-700 dark:bg-slate-800 dark:text-primary-400 dark:hover:bg-slate-700"
          >
            <i class="pi pi-video"></i> Assistir ao vídeo
          </a>
        }
      }

      @if (comunicado().anexos.length > 0) {
        <div class="flex flex-col gap-2">
          <span class="text-[9px] font-bold uppercase tracking-widest text-slate-400 dark:text-slate-400">Anexos</span>
          @for (anexo of comunicado().anexos; track anexo.id) {
            <div class="flex items-center gap-3 rounded-xl border border-slate-100 bg-slate-50/60 p-2.5 dark:border-slate-800 dark:bg-slate-800/60">
              <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-white border border-slate-100 dark:bg-slate-700 dark:border-slate-600">
                <i [class]="iconeAnexo(anexo)" class="text-sm text-slate-400 dark:text-slate-300"></i>
              </div>
              <div class="flex min-w-0 flex-col">
                <span class="truncate text-[11px] font-medium text-slate-700 dark:text-white">{{ anexo.nomeArquivo }}</span>
                <span class="text-[9px] text-slate-400 dark:text-slate-400">{{ tamanho(anexo) }}</span>
              </div>
              <div class="ml-auto flex items-center gap-1">
                <p-button
                  icon="pi pi-download"
                  [text]="true"
                  [rounded]="true"
                  [loading]="baixando() === anexo.id"
                  (onClick)="baixar(anexo)"
                  styleClass="!w-8 !h-8 !p-0 !text-slate-500 dark:!text-slate-300"
                ></p-button>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      /* ─── Layout base ──────────────────────────────────────────────────── */
      :host ::ng-deep .comunicado-conteudo > :first-child { margin-top: 0; }
      :host ::ng-deep .comunicado-conteudo > :last-child  { margin-bottom: 0; }

      /* ─── Parágrafos ───────────────────────────────────────────────────── */
      :host ::ng-deep .comunicado-conteudo p {
        margin: 0 0 0.85rem;
        line-height: 1.7;
      }

      /* ─── Headings ─────────────────────────────────────────────────────── */
      :host ::ng-deep .comunicado-conteudo h1,
      :host ::ng-deep .comunicado-conteudo h2,
      :host ::ng-deep .comunicado-conteudo h3,
      :host ::ng-deep .comunicado-conteudo h4 {
        font-weight: 700;
        line-height: 1.3;
        color: #0f172a;
        margin: 1.4rem 0 0.5rem;
      }
      .app-dark :host ::ng-deep .comunicado-conteudo h1,
      .app-dark :host ::ng-deep .comunicado-conteudo h2,
      .app-dark :host ::ng-deep .comunicado-conteudo h3,
      .app-dark :host ::ng-deep .comunicado-conteudo h4,
      .dark :host ::ng-deep .comunicado-conteudo h1,
      .dark :host ::ng-deep .comunicado-conteudo h2,
      .dark :host ::ng-deep .comunicado-conteudo h3,
      .dark :host ::ng-deep .comunicado-conteudo h4 {
        color: #f8fafc;
      }
      /* Tamanhos proporcionais e legíveis */
      :host ::ng-deep .comunicado-conteudo h1 { font-size: 1.25rem; }
      :host ::ng-deep .comunicado-conteudo h2 { font-size: 1.1rem;  border-bottom: 1px solid #e2e8f0; padding-bottom: 0.3rem; }
      :host ::ng-deep .comunicado-conteudo h3 { font-size: 1rem; }
      :host ::ng-deep .comunicado-conteudo h4 { font-size: 0.9rem; }
      .app-dark :host ::ng-deep .comunicado-conteudo h2,
      .dark      :host ::ng-deep .comunicado-conteudo h2 {
        border-bottom-color: #334155;
      }

      /* ─── Listas ───────────────────────────────────────────────────────── */
      :host ::ng-deep .comunicado-conteudo ul,
      :host ::ng-deep .comunicado-conteudo ol {
        margin: 0 0 0.85rem;
        padding-left: 1.5rem;
      }
      :host ::ng-deep .comunicado-conteudo ul { list-style: disc; }
      :host ::ng-deep .comunicado-conteudo ol { list-style: decimal; }
      :host ::ng-deep .comunicado-conteudo li + li { margin-top: 0.3rem; }
      :host ::ng-deep .comunicado-conteudo li > p  { margin: 0.25rem 0; }

      /* ─── Negrito e itálico ────────────────────────────────────────────── */
      :host ::ng-deep .comunicado-conteudo strong { font-weight: 700; }
      :host ::ng-deep .comunicado-conteudo em      { font-style: italic; }

      /* ─── Links ────────────────────────────────────────────────────────── */
      :host ::ng-deep .comunicado-conteudo a {
        color: var(--p-primary-600);
        text-decoration: underline;
        text-underline-offset: 2px;
      }
      .app-dark :host ::ng-deep .comunicado-conteudo a,
      .dark      :host ::ng-deep .comunicado-conteudo a {
        color: var(--p-primary-400);
      }

      /* ─── Blockquote ───────────────────────────────────────────────────── */
      :host ::ng-deep .comunicado-conteudo blockquote {
        margin: 0 0 0.85rem;
        padding: 0.5rem 0.9rem;
        border-left: 3px solid var(--p-primary-400);
        background: #f8fafc;
        border-radius: 0 0.375rem 0.375rem 0;
        color: #475569;
      }
      .app-dark :host ::ng-deep .comunicado-conteudo blockquote,
      .dark      :host ::ng-deep .comunicado-conteudo blockquote {
        background: #1e293b;
        color: #94a3b8;
        border-left-color: var(--p-primary-500);
      }

      /* ─── Divisor horizontal ───────────────────────────────────────────── */
      :host ::ng-deep .comunicado-conteudo hr {
        border: 0;
        border-top: 1px solid #e2e8f0;
        margin: 1.25rem 0;
      }
      .app-dark :host ::ng-deep .comunicado-conteudo hr,
      .dark      :host ::ng-deep .comunicado-conteudo hr {
        border-top-color: #334155;
      }

      /* ─── Código inline ────────────────────────────────────────────────── */
      :host ::ng-deep .comunicado-conteudo code {
        font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
        font-size: 0.85em;
        background: #f1f5f9;
        border-radius: 0.25rem;
        padding: 0.1rem 0.3rem;
      }
      .app-dark :host ::ng-deep .comunicado-conteudo code,
      .dark      :host ::ng-deep .comunicado-conteudo code {
        background: #1e293b;
        color: #e2e8f0;
      }

      /* ─── Bloco de código ──────────────────────────────────────────────── */
      :host ::ng-deep .comunicado-conteudo pre {
        margin: 0 0 0.85rem;
        padding: 0.75rem 0.9rem;
        overflow-x: auto;
        border-radius: 0.5rem;
        background: #f1f5f9;
        border: 1px solid #e2e8f0;
      }
      :host ::ng-deep .comunicado-conteudo pre code {
        padding: 0;
        background: transparent;
        font-size: 0.82em;
      }
      .app-dark :host ::ng-deep .comunicado-conteudo pre,
      .dark      :host ::ng-deep .comunicado-conteudo pre {
        background: #0f172a;
        border-color: #1e293b;
      }

      /* ─── Imagens ──────────────────────────────────────────────────────── */
      :host ::ng-deep .comunicado-conteudo img {
        max-width: 100%;
        border-radius: 0.75rem;
        display: block;
        margin: 0.5rem 0;
      }

      /* ─── Tabelas ──────────────────────────────────────────────────────── */
      :host ::ng-deep .comunicado-conteudo table {
        width: 100%;
        border-collapse: collapse;
        margin: 0 0 0.85rem;
        font-size: 0.9em;
      }
      :host ::ng-deep .comunicado-conteudo th,
      :host ::ng-deep .comunicado-conteudo td {
        padding: 0.45rem 0.7rem;
        border: 1px solid #e2e8f0;
        text-align: left;
        vertical-align: top;
      }
      :host ::ng-deep .comunicado-conteudo thead th {
        background: #f8fafc;
        font-weight: 600;
      }
      .app-dark :host ::ng-deep .comunicado-conteudo th,
      .app-dark :host ::ng-deep .comunicado-conteudo td,
      .dark      :host ::ng-deep .comunicado-conteudo th,
      .dark      :host ::ng-deep .comunicado-conteudo td {
        border-color: #334155;
      }
      .app-dark :host ::ng-deep .comunicado-conteudo thead th,
      .dark      :host ::ng-deep .comunicado-conteudo thead th {
        background: #1e293b;
      }
    `
  ]
})
export class ComunicadoConteudoComponent {
  private readonly mural = inject(MuralStore);
  private readonly messages = inject(MessageService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly comunicado = input.required<ComunicadoMuralResponse>();

  protected readonly baixando = signal<string | null>(null);

  protected readonly conteudoHtml = computed(() => {
    const bruto = this.comunicado().conteudo || '';
    // breaks: false → respeita parágrafos duplos como blocos separados (padrão GFM correto).
    // breaks: true convertia cada \n em <br>, colapsando headings com o parágrafo seguinte.
    const convertido = marked.parse(bruto, { async: false, gfm: true, breaks: false }) as string;
    return DOMPurify.sanitize(convertido);
  });

  /** Converte links de YouTube/Vimeo em URL de embed; qualquer outra coisa vira link simples. */
  protected readonly videoEmbed = computed<SafeResourceUrl | null>(() => {
    const url = this.comunicado().videoUrl;
    if (!url) return null;

    const youtube = url.match(
      /(?:youtube\.com\/(?:watch\?v=|embed\/)|youtu\.be\/)([\w-]{6,})/
    );
    if (youtube) {
      return this.sanitizer.bypassSecurityTrustResourceUrl(
        `https://www.youtube.com/embed/${youtube[1]}`
      );
    }

    const vimeo = url.match(/vimeo\.com\/(?:video\/)?(\d+)/);
    if (vimeo) {
      return this.sanitizer.bypassSecurityTrustResourceUrl(
        `https://player.vimeo.com/video/${vimeo[1]}`
      );
    }

    return null;
  });

  protected tamanho(anexo: AnexoComunicadoResponse): string {
    return formatarTamanhoArquivo(anexo.tamanhoBytes);
  }

  protected iconeAnexo(anexo: AnexoComunicadoResponse): string {
    if (anexo.contentType?.startsWith('image/')) return 'pi pi-image';
    if (anexo.contentType === 'application/pdf') return 'pi pi-file-pdf';
    return 'pi pi-file';
  }

  /** O download é autenticado: precisa passar pelo HttpClient e virar blob. */
  protected baixar(anexo: AnexoComunicadoResponse): void {
    this.baixando.set(anexo.id);
    this.mural.baixarAnexo(this.comunicado().id, anexo.id).subscribe({
      next: (blob) => {
        FileDownloadUtil.downloadBlob(blob, anexo.nomeArquivo);
        this.baixando.set(null);
      },
      error: (erro) => {
        this.baixando.set(null);
        this.messages.add({
          severity: 'error',
          summary: 'Falha no download',
          detail: getErrorMessage(erro)
        });
      }
    });
  }
}
