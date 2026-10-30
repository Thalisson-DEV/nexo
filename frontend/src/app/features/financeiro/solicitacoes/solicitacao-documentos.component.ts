import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CesButtonComponent,
  CesConfirmationService,
  CesDialogShellComponent,
  CesIconButtonComponent,
  CesNumberInputComponent,
  CesSelectComponent,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService
} from '@ces/ui';
import {
  DocumentoFinanceiro,
  DocumentoRequest,
  DocumentoResumo,
  ROTULO_TIPO_DOCUMENTO,
  SituacaoDocumento,
  SolicitacaoFinanceira,
  TipoDocumentoFinanceiro,
  TipoSolicitacao
} from '../../../core/models/financeiro.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AuthService } from '../../../core/services/auth.service';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { FileDownloadUtil } from '../../../core/utils/file-download.util';
import { novaChaveIdempotencia } from '../../../core/utils/idempotencia.util';

type DocumentoComIndicadores = DocumentoResumo & {
  chaveAcesso?: string | null;
  codigoPagamento?: string | null;
  possuiCodigoPagamento?: boolean;
  duplicado?: boolean;
  identificacaoPendente?: boolean;
  observacaoConferencia?: string | null;
  dataConferencia?: string | null;
  motivoPendencia?: string | null;
  acoes?: AcoesDocumento;
};

interface AcoesDocumento {
  baixar: boolean;
  editarMetadados: boolean;
  remover: boolean;
  conferir: boolean;
  apontarPendencia: boolean;
  arquivar: boolean;
  reabrir: boolean;
  desvincular: boolean;
}

interface EditorDocumento {
  id: string;
  tipo: TipoDocumentoFinanceiro | null;
  codigoPagamento: string;
  numeroDocumento: string;
  chaveAcesso: string;
  fornecedorId: string;
  emissorNome: string;
  dataEmissao: string;
  dataVencimento: string;
  competencia: string;
  valor: number | null;
  baseId: string;
  processoId: string;
  obraId: string;
  descricao: string;
  observacoes: string;
}

type AcaoDialog = 'PENDENCIA' | null;

const ACOES_FECHADAS: AcoesDocumento = {
  baixar: false,
  editarMetadados: false,
  remover: false,
  conferir: false,
  apontarPendencia: false,
  arquivar: false,
  reabrir: false,
  desvincular: false
};

@Component({
  selector: 'app-solicitacao-documentos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    CesButtonComponent,
    CesDialogShellComponent,
    CesIconButtonComponent,
    CesNumberInputComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent
  ],
  styles: `
    .step-circle {
      border-radius: 9999px;
      border-width: 2px;
      width: 2rem;
      height: 2rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      transition: all 0.3s;
    }
    .step-active {
      border-color: var(--p-primary-700, #1d4ed8);
      background-color: var(--p-primary-700, #1d4ed8);
      color: #ffffff;
    }
    .step-inactive {
      border-color: #cbd5e1;
      background-color: #ffffff;
      color: #94a3b8;
    }
    :host-context(.app-dark) .step-inactive,
    :host-context(.dark) .step-inactive {
      border-color: #334155 !important;
      background-color: #0f172a !important;
      color: #64748b !important;
    }
    .step-label {
      font-size: 10px;
      font-weight: 500;
      letter-spacing: 0.025em;
      text-transform: uppercase;
    }
  `,
  template: `
    <section
      [attr.aria-labelledby]="comTitulo() ? 'documentos-titulo' : null"
      [attr.aria-label]="comTitulo() ? null : 'Documentos e comprovantes'"
    >
      @if (comTitulo()) {
        <header class="border-l-2 border-primary-700 dark:border-primary-500 pl-3 mb-4">
          <h2 id="documentos-titulo" class="text-[11px] font-medium uppercase tracking-tight text-slate-800 dark:text-slate-100">
            Documentos
          </h2>
          <p class="text-[9px] text-slate-400 dark:text-slate-400 mt-0.5">
            Nota fiscal, boleto, comprovante, orçamento ou fotos de apoio.
          </p>
        </header>
      }

      <input
        #fileInput
        type="file"
        class="sr-only"
        multiple
        accept=".jpg,.jpeg,.png,.webp,.heic,.heif,.pdf,.xml,.doc,.docx,.xls,.xlsx"
        (change)="selecionarArquivos($event)"
      />

      @if (podeEnviar && arquivosPendentes().length === 0) {
        <button
          type="button"
          (click)="fileInput.click()"
          [ngClass]="recuo"
          class="w-full min-h-24 border border-dashed border-slate-300 dark:border-slate-700 rounded-xl px-5 py-5 bg-slate-50/40 dark:bg-slate-800/30 hover:bg-primary-50/30 dark:hover:bg-primary-950/30 hover:border-primary-300 dark:hover:border-primary-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600 transition-colors text-center"
        >
          <i class="pi pi-cloud-upload text-xl text-primary-700 dark:text-primary-400" aria-hidden="true"></i>
          <span class="block text-[11px] font-semibold text-slate-700 dark:text-slate-200 mt-2">Adicionar documentos</span>
          <span class="block text-[9px] text-slate-400 dark:text-slate-400 mt-1">
            Imagem, PDF, XML da nota, Word ou Excel · 15 MB por arquivo e 20 MB no total
          </span>
        </button>
      }

      @if (podeEnviar && arquivosPendentes().length > 0) {
        <div
          class="p-4 bg-primary-50/40 dark:bg-primary-950/30 border border-primary-100 dark:border-primary-900/50 rounded-xl mb-4"
          [ngClass]="recuo"
          aria-live="polite"
        >
          <div class="flex items-center justify-between gap-3 mb-3">
            <p class="text-[10px] font-semibold text-slate-700 dark:text-slate-200">
              {{ arquivosPendentes().length }}
              {{ arquivosPendentes().length === 1 ? 'arquivo selecionado' : 'arquivos selecionados' }}
            </p>
            <span class="text-[9px] text-slate-400 dark:text-slate-400">
              {{ tamanho(tamanhoTotalArquivos()) }} de 20 MB
            </span>
          </div>

          <ul class="space-y-2" aria-label="Arquivos selecionados">
            @for (arquivo of arquivosPendentes(); track $index) {
              <li class="flex items-center gap-2">
                <i class="pi pi-file text-primary-700 dark:text-primary-400" aria-hidden="true"></i>
                <span class="text-[11px] font-medium text-slate-700 dark:text-slate-200 truncate flex-1">
                  {{ arquivo.name }}
                </span>
                <span class="text-[9px] text-slate-400 dark:text-slate-400">
                  {{ tamanho(arquivo.size) }}
                </span>
                <ces-icon-button
                  icon="pi pi-times"
                  [label]="'Remover ' + arquivo.name + ' da seleção'"
                  size="icon-sm"
                  [disabled]="enviando()"
                  (pressed)="removerArquivoSelecionado($index)"
                />
              </li>
            }
          </ul>

          <div class="mt-3">
            <ces-button
              label="Adicionar mais arquivos"
              icon="pi pi-plus"
              variant="ghost"
              size="sm"
              [disabled]="enviando()"
              (pressed)="fileInput.click()"
            />
          </div>

          <div class="flex flex-col sm:flex-row sm:items-end gap-3 mt-3">
            <ces-select
              class="flex-1"
              label="Tipo do documento"
              [options]="tipos"
              [(value)]="tipoDocumento"
            />
            <div class="flex justify-end gap-2">
              <ces-button
                label="Cancelar"
                variant="ghost"
                size="sm"
                (pressed)="cancelarSelecao()"
              />
              <ces-button
                [label]="rotuloEnvio()"
                icon="pi pi-upload"
                size="sm"
                [loading]="enviando()"
                (pressed)="enviar()"
              />
            </div>
          </div>
        </div>
      }

      <div
        class="divide-y divide-slate-100 dark:divide-slate-800 border-y border-slate-100 dark:border-slate-800 mt-4"
        [ngClass]="recuo"
        [attr.aria-busy]="mutacaoEmAndamento()"
      >
        @for (documento of solicitacao().documentos; track documento.id) {
          <article class="py-4 px-2 flex flex-col gap-3" [attr.aria-labelledby]="'documento-' + documento.id">
            <div class="flex items-start gap-3">
              <span
                class="w-8 h-8 rounded-md bg-slate-50 dark:bg-slate-800 text-slate-400 dark:text-slate-400 flex items-center justify-center shrink-0"
                aria-hidden="true"
              >
                <i [class]="icone(documento)"></i>
              </span>
              <div class="text-left min-w-0 flex-1">
                <h3 [id]="'documento-' + documento.id" class="text-[10px] font-medium text-slate-700 dark:text-slate-200 break-words">
                  {{ documento.nomeArquivo }}
                </h3>
                <p class="text-[9px] text-slate-400 dark:text-slate-400 mt-0.5">
                  {{ documento.tipoRotulo || 'Documento' }} · {{ tamanho(documento.tamanhoBytes) }} · enviado em
                  {{ documento.dataCriacao | date: 'dd/MM/yyyy HH:mm' }}
                  @if (documento.usuarioCriacao) {
                    · {{ documento.usuarioCriacao }}
                  }
                </p>
              </div>
              <ces-status [tone]="tomSituacao(documento.situacao)"
                [icon]="documento.situacao === 'PENDENTE' ? 'pi pi-clock' : (documento.situacao === 'CONFERIDO' ? 'pi pi-check' : 'pi pi-exclamation-triangle')"
                [emphasis]="documento.situacao === 'PENDENTE' ? 'outline' : 'soft'"
                [class]="documento.situacao === 'PENDENTE' ? 'font-bold !border-amber-400 dark:!border-amber-500 !text-amber-700 dark:!text-amber-300 !bg-amber-50 dark:!bg-amber-950/40' : ''">
                {{ documento.situacaoRotulo || documento.situacao }}
              </ces-status>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 pl-11 text-[10px]">
              @if (documento.numeroDocumento) {
                <p>
                  <span class="text-slate-400 dark:text-slate-400">Número:</span>
                  <span class="font-medium text-slate-700 dark:text-slate-200 ml-1">{{ documento.numeroDocumento }}</span>
                </p>
              }
              @if (documento.valor != null) {
                <p>
                  <span class="text-slate-400 dark:text-slate-400">Valor:</span>
                  <span class="font-medium text-slate-700 dark:text-slate-200 ml-1">{{ documento.valor | currency: 'BRL' }}</span>
                </p>
              }
            </div>

            @if (documentoAsExtended(documento).codigoPagamento && documentoAsExtended(documento).possuiCodigoPagamento !== false) {
              <div class="ml-11 rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 p-3">
                <div class="flex items-center justify-between gap-2 mb-1">
                  <span class="text-[9px] font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                    Código de pagamento
                  </span>
                  <ces-icon-button
                    icon="pi pi-copy"
                    label="Copiar código de pagamento"
                    size="icon-sm"
                    [loading]="copiandoId() === documento.id"
                    (pressed)="copiarCodigo(documento)"
                  />
                </div>
                <code class="block text-[10px] text-slate-700 dark:text-slate-200 font-mono break-all whitespace-pre-wrap">{{ documentoAsExtended(documento).codigoPagamento }}</code>
              </div>
            }

            @if (documentoAsExtended(documento).duplicado) {
              <div class="ml-11 rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 px-3 py-2 text-[10px] text-slate-600 dark:text-slate-300" role="status">
                <i class="pi pi-copy mr-1" aria-hidden="true"></i>
                Este arquivo também está anexado em outro documento. A conferência continua disponível.
              </div>
            }

            @if (documentoAsExtended(documento).identificacaoPendente) {
              <div class="ml-11 rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 px-3 py-2 text-[10px] text-slate-600 dark:text-slate-300" role="status">
                <i class="pi pi-info-circle mr-1" aria-hidden="true"></i>
                Falta informar a identificação obrigatória deste documento.
              </div>
            }

            @if (documentoAsExtended(documento).motivoPendencia) {
              <div class="ml-11 rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50 px-3 py-2 text-[10px] text-slate-600 dark:text-slate-300">
                <span class="font-semibold">Motivo da pendência:</span>
                {{ documentoAsExtended(documento).motivoPendencia }}
              </div>
            }

            @if (documentoAsExtended(documento).observacaoConferencia || documentoAsExtended(documento).dataConferencia) {
              <div class="ml-11 text-[10px] text-slate-500 dark:text-slate-400">
                @if (documentoAsExtended(documento).observacaoConferencia) {
                  <p>
                    <span class="font-semibold">Observação:</span>
                    {{ documentoAsExtended(documento).observacaoConferencia }}
                  </p>
                }
                @if (documentoAsExtended(documento).dataConferencia) {
                  <p>
                    <span class="font-semibold">Conferido em:</span>
                    {{ documentoAsExtended(documento).dataConferencia | date: 'dd/MM/yyyy HH:mm' }}
                  </p>
                }
              </div>
            }

            <div class="ml-11 flex flex-wrap items-center gap-1.5" aria-label="Ações do documento">
              @if (acoes(documento).baixar && podeVisualizar(documento)) {
                <ces-icon-button
                  icon="pi pi-eye"
                  label="Visualizar anexo"
                  size="icon-sm"
                  [loading]="visualizandoId() === documento.id"
                  (pressed)="visualizar(documento)"
                />
              }
              @if (acoes(documento).baixar) {
                <ces-icon-button
                  icon="pi pi-download"
                  label="Baixar arquivo"
                  size="icon-sm"
                  [loading]="baixandoId() === documento.id"
                  (pressed)="baixar(documento)"
                />
              }
              @if (acoes(documento).editarMetadados) {
                <ces-icon-button
                  icon="pi pi-pencil"
                  label="Editar metadados"
                  size="icon-sm"
                  [loading]="editandoId() === documento.id"
                  (pressed)="abrirEdicao(documento)"
                />
              }
              @if (acoes(documento).apontarPendencia) {
                <ces-button
                  label="Apontar pendência"
                  icon="pi pi-flag"
                  variant="secondary"
                  size="sm"
                  [loading]="mutacaoId() === documento.id && mutacaoTipo() === 'PENDENCIA'"
                  (pressed)="abrirPendencia(documento)"
                />
              }
              @if (acoes(documento).arquivar) {
                <ces-icon-button
                  icon="pi pi-folder"
                  label="Arquivar documento"
                  size="icon-sm"
                  (pressed)="arquivar(documento)"
                />
              }
              @if (acoes(documento).reabrir) {
                <ces-icon-button
                  icon="pi pi-folder-open"
                  label="Reabrir documento"
                  size="icon-sm"
                  (pressed)="reabrir(documento)"
                />
              }
              @if (acoes(documento).desvincular) {
                <ces-icon-button
                  icon="pi pi-link"
                  label="Desvincular da solicitação"
                  size="icon-sm"
                  (pressed)="confirmarDesvinculacao(documento)"
                />
              }
              @if (acoes(documento).remover) {
                <ces-icon-button
                  icon="pi pi-trash"
                  label="Remover documento"
                  variant="destructive"
                  size="icon-sm"
                  [loading]="removendoId() === documento.id"
                  (pressed)="confirmarRemocao(documento)"
                />
              }
            </div>
          </article>
        } @empty {
          <div class="p-8 text-center" role="status">
            <i class="pi pi-folder-open text-xl text-slate-300" aria-hidden="true"></i>
            <p class="text-[10px] text-slate-500 mt-2">Nenhum documento anexado ainda.</p>
          </div>
        }
      </div>
    </section>

    <ces-dialog-shell
      [(visible)]="dialogVisivel"
      title="Apontar pendência"
      description="Informe o motivo para registrar a pendência."
      size="sm"
      [loading]="mutacaoEmAndamento()"
      loadingLabel="Salvando pendência"
    >
      <ces-textarea
        label="Motivo"
        [required]="true"
        [maxlength]="1000"
        [rows]="4"
        placeholder="Descreva o que precisa ser corrigido."
        [error]="motivoPendenciaErro()"
        [(value)]="textoPendencia"
      />
      <div dialog-footer>
        <ces-button
          label="Cancelar"
          variant="ghost"
          [disabled]="mutacaoEmAndamento()"
          (pressed)="fecharDialogo()"
        />
        <ces-button
          label="Registrar pendência"
          variant="primary"
          [loading]="mutacaoEmAndamento()"
          (pressed)="confirmarDialogo()"
        />
      </div>
    </ces-dialog-shell>

    <ces-dialog-shell
      [(visible)]="editorVisivel"
      title="Editar metadados do documento"
      description="Navegue pelas etapas e ajuste os metadados do documento."
      size="lg"
      [loading]="editandoId() !== null"
      loadingLabel="Carregando documento"
    >
      <div class="flex items-center justify-center gap-4 sm:gap-8 border-b border-slate-100 dark:border-slate-800 pb-4 mb-5">
        <button
          type="button"
          (click)="etapaEditor.set(1)"
          class="inline-flex flex-col items-center gap-1.5 focus-visible:outline-none transition-colors"
        >
          <span class="step-circle" [ngClass]="etapaEditor() >= 1 ? 'step-active' : 'step-inactive'">
            <i class="pi pi-file text-[10px]"></i>
          </span>
          <span class="step-label" [ngClass]="etapaEditor() === 1 ? 'text-primary-700 dark:text-primary-400 font-semibold' : 'text-slate-400 dark:text-slate-500'">
            Documento
          </span>
        </button>

        <div class="h-0.5 w-6 sm:w-12" [ngClass]="etapaEditor() >= 2 ? 'bg-primary-700' : 'bg-slate-200 dark:bg-slate-700'"></div>

        <button
          type="button"
          (click)="etapaEditor.set(2)"
          class="inline-flex flex-col items-center gap-1.5 focus-visible:outline-none transition-colors"
        >
          <span class="step-circle" [ngClass]="etapaEditor() >= 2 ? 'step-active' : 'step-inactive'">
            <i class="pi pi-dollar text-[10px]"></i>
          </span>
          <span class="step-label" [ngClass]="etapaEditor() === 2 ? 'text-primary-700 dark:text-primary-400 font-semibold' : 'text-slate-400 dark:text-slate-500'">
            Valores & Prazos
          </span>
        </button>

        <div class="h-0.5 w-6 sm:w-12" [ngClass]="etapaEditor() >= 3 ? 'bg-primary-700' : 'bg-slate-200 dark:bg-slate-700'"></div>

        <button
          type="button"
          (click)="etapaEditor.set(3)"
          class="inline-flex flex-col items-center gap-1.5 focus-visible:outline-none transition-colors"
        >
          <span class="step-circle" [ngClass]="etapaEditor() >= 3 ? 'step-active' : 'step-inactive'">
            <i class="pi pi-list text-[10px]"></i>
          </span>
          <span class="step-label" [ngClass]="etapaEditor() === 3 ? 'text-primary-700 dark:text-primary-400 font-semibold' : 'text-slate-400 dark:text-slate-500'">
            Classificação
          </span>
        </button>
      </div>

      @if (etapaEditor() === 1) {
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 animate-in fade-in duration-200">
          <ces-select
            class="sm:col-span-2"
            label="Tipo do documento"
            [options]="tipos"
            [(value)]="editor.tipo"
          />
          <ces-text-input
            label="Número do documento"
            [showOptional]="true"
            [(value)]="editor.numeroDocumento"
          />
          <ces-text-input
            label="Nome do emissor"
            [showOptional]="true"
            [(value)]="editor.emissorNome"
          />
          <ces-text-input
            class="sm:col-span-2"
            label="Chave de acesso"
            [showOptional]="true"
            [(value)]="editor.chaveAcesso"
          />
          <ces-text-input
            class="sm:col-span-2"
            label="Código de pagamento"
            [showOptional]="true"
            [maxlength]="512"
            [(value)]="editor.codigoPagamento"
          />
          <ces-text-input
            class="sm:col-span-2"
            label="Fornecedor"
            [showOptional]="true"
            [(value)]="editor.fornecedorId"
          />
        </div>
      }

      @if (etapaEditor() === 2) {
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 animate-in fade-in duration-200">
          <ces-number-input
            class="sm:col-span-2"
            label="Valor"
            mode="currency"
            locale="pt-BR"
            [(value)]="editor.valor"
          />
          <ces-text-input
            label="Data de emissão"
            description="Formato: AAAA-MM-DD"
            [showOptional]="true"
            [(value)]="editor.dataEmissao"
          />
          <ces-text-input
            label="Data de vencimento"
            description="Formato: AAAA-MM-DD"
            [showOptional]="true"
            [(value)]="editor.dataVencimento"
          />
          <ces-text-input
            class="sm:col-span-2"
            label="Mês contábil (competência)"
            description="Formato: AAAA-MM-DD"
            [showOptional]="true"
            [(value)]="editor.competencia"
          />
        </div>
      }

      @if (etapaEditor() === 3) {
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 animate-in fade-in duration-200">
          <ces-text-input
            label="Base"
            [showOptional]="true"
            [(value)]="editor.baseId"
          />
          <ces-text-input
            label="Processo"
            [showOptional]="true"
            [(value)]="editor.processoId"
          />
          <ces-text-input
            class="sm:col-span-2"
            label="Obra"
            [showOptional]="true"
            [(value)]="editor.obraId"
          />
          <ces-text-input
            class="sm:col-span-2"
            label="Descrição"
            [showOptional]="true"
            [(value)]="editor.descricao"
          />
          <ces-textarea
            class="sm:col-span-2"
            label="Observações"
            [showOptional]="true"
            [maxlength]="2000"
            [rows]="3"
            [(value)]="editor.observacoes"
          />
        </div>
      }

      <div dialog-footer class="flex items-center justify-between w-full">
        <div>
          @if (etapaEditor() === 1) {
            <ces-button
              label="Cancelar"
              variant="ghost"
              [disabled]="salvandoEdicao()"
              (pressed)="fecharEdicao()"
            />
          } @else {
            <ces-button
              label="Voltar"
              icon="pi pi-arrow-left"
              variant="ghost"
              [disabled]="salvandoEdicao()"
              (pressed)="etapaEditor.set(etapaEditor() - 1)"
            />
          }
        </div>
        <div class="flex items-center gap-2">
          @if (etapaEditor() < 3) {
            <ces-button
              label="Próximo"
              icon="pi pi-arrow-right"
              iconPosition="right"
              (pressed)="etapaEditor.set(etapaEditor() + 1)"
            />
          } @else {
            <ces-button
              label="Salvar metadados"
              icon="pi pi-check"
              [loading]="salvandoEdicao()"
              (pressed)="salvarEdicao()"
            />
          }
        </div>
      </div>
    </ces-dialog-shell>
  `
})
export class SolicitacaoDocumentosComponent {
  private readonly service = inject(FinanceiroService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly confirmations = inject(CesConfirmationService);

  readonly tipo = input.required<TipoSolicitacao>();
  readonly solicitacao = input.required<SolicitacaoFinanceira>();
  readonly comTitulo = input(true);
  readonly atualizada = output<SolicitacaoFinanceira>();

  protected readonly arquivosPendentes = signal<File[]>([]);
  protected readonly enviando = signal(false);
  protected readonly baixandoId = signal<string | null>(null);
  protected readonly visualizandoId = signal<string | null>(null);
  protected readonly removendoId = signal<string | null>(null);
  protected readonly copiandoId = signal<string | null>(null);
  protected readonly editandoId = signal<string | null>(null);
  protected readonly mutacaoId = signal<string | null>(null);
  protected readonly dialogDocumentoId = signal<string | null>(null);
  protected readonly mutacaoTipo = signal<AcaoDialog>(null);
  protected readonly editorVisivel = signal(false);
  protected readonly etapaEditor = signal<number>(1);
  protected readonly dialogVisivel = signal(false);
  protected readonly acaoDialog = signal<AcaoDialog>(null);
  protected readonly motivoPendenciaErro = signal<string | undefined>(undefined);
  protected readonly textoConferencia = signal('');
  protected readonly textoPendencia = signal('');
  protected readonly editor = this.novoEditor();
  protected readonly salvandoEdicao = signal(false);

  protected tipoDocumento: TipoDocumentoFinanceiro = 'OUTRO';
  protected readonly podeEnviar =
    this.auth.currentUser()?.permissoes?.includes('financeiro:documento:write') ?? false;

  protected readonly tipos = (
    [
      'NOTA_FISCAL',
      'BOLETO',
      'COMPROVANTE_PAGAMENTO',
      'RECIBO',
      'ORCAMENTO',
      'CONTRATO',
      'EXTRATO',
      'FOTO',
      'OUTRO'
    ] as TipoDocumentoFinanceiro[]
  ).map((value) => ({ label: ROTULO_TIPO_DOCUMENTO[value], value }));

  private readonly extensoesAceitas = [
    'jpg',
    'jpeg',
    'png',
    'webp',
    'heic',
    'heif',
    'pdf',
    'xml',
    'doc',
    'docx',
    'xls',
    'xlsx'
  ];
  private readonly limiteArquivoBytes = 15 * 1024 * 1024;
  private readonly limiteRequisicaoBytes = 20 * 1024 * 1024;

  protected get recuo(): string {
    return this.comTitulo() ? 'md:ml-5 md:w-[calc(100%-1.25rem)]' : '';
  }

  protected mutacaoEmAndamento(): boolean {
    return this.enviando() || this.mutacaoId() !== null || this.salvandoEdicao();
  }

  protected acoes(documento: DocumentoResumo): AcoesDocumento {
    const acoesBase = this.documentoAsExtended(documento).acoes ?? ACOES_FECHADAS;
    if (documento.situacao === 'CONFERIDO') {
      return {
        ...ACOES_FECHADAS,
        baixar: acoesBase.baixar
      };
    }
    return acoesBase;
  }

  protected documentoAsExtended(documento: DocumentoResumo): DocumentoComIndicadores {
    return documentoAsExtended(documento);
  }

  protected podeVisualizar(documento: DocumentoResumo): boolean {
    const ct = documento?.contentType?.toLowerCase() ?? '';
    return (
      ct.startsWith('image/') ||
      ct === 'application/pdf' ||
      ct.includes('image') ||
      ct.includes('pdf') ||
      ct.includes('xml') ||
      ct.includes('text')
    );
  }

  protected tamanhoTotalArquivos(): number {
    return this.arquivosPendentes().reduce((total, arquivo) => total + arquivo.size, 0);
  }

  protected rotuloEnvio(): string {
    const quantidade = this.arquivosPendentes().length;
    return quantidade === 1 ? 'Enviar arquivo' : `Enviar ${quantidade} arquivos`;
  }

  protected selecionarArquivos(event: Event): void {
    const input = event.target as HTMLInputElement;
    const selecionados = Array.from(input.files ?? []);
    input.value = '';
    if (selecionados.length === 0) return;

    const lote = [...this.arquivosPendentes(), ...selecionados];
    const erros = this.validarArquivos(lote);

    if (erros.length > 0) {
      this.notifications.warning(erros.join(' '), {
        title: 'Revise os arquivos selecionados'
      });
      return;
    }

    this.arquivosPendentes.set(lote);
  }

  protected removerArquivoSelecionado(indice: number): void {
    if (this.enviando()) return;
    this.arquivosPendentes.update((arquivos) =>
      arquivos.filter((_, indiceAtual) => indiceAtual !== indice)
    );
  }

  protected cancelarSelecao(): void {
    this.arquivosPendentes.set([]);
    this.tipoDocumento = 'OUTRO';
  }

  protected enviar(): void {
    const arquivos = this.arquivosPendentes();
    if (arquivos.length === 0 || this.enviando()) return;

    this.enviando.set(true);
    this.service
      .anexarDocumento(
        this.tipo(),
        this.solicitacao().id,
        arquivos,
        this.tipoDocumento,
        novaChaveIdempotencia()
      )
      .subscribe({
        next: (response) => {
          this.enviando.set(false);
          this.cancelarSelecao();
          this.atualizada.emit(response);
          this.notifications.success(
            arquivos.length === 1
              ? 'Anexo enviado com sucesso.'
              : `${arquivos.length} anexos enviados com sucesso.`
          );
        },
        error: (error) => this.erroUpload(error)
      });
  }

  protected visualizar(documento: DocumentoResumo): void {
    if (!this.acoes(documento).baixar || this.visualizandoId() || this.mutacaoEmAndamento()) return;
    this.visualizandoId.set(documento.id);
    this.service
      .baixarDocumentoDaSolicitacao(this.tipo(), this.solicitacao().id, documento.id)
      .subscribe({
        next: (blob) => {
          this.visualizandoId.set(null);
          const url = URL.createObjectURL(blob);
          window.open(url, '_blank', 'noopener,noreferrer');
          window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
        },
        error: (error) => {
          this.visualizandoId.set(null);
          this.erro(error);
        }
      });
  }

  protected baixar(documento: DocumentoResumo): void {
    if (!this.acoes(documento).baixar || this.baixandoId() || this.mutacaoEmAndamento()) return;
    this.baixandoId.set(documento.id);
    this.service
      .baixarDocumentoDaSolicitacao(this.tipo(), this.solicitacao().id, documento.id)
      .subscribe({
        next: (blob) => {
          this.baixandoId.set(null);
          FileDownloadUtil.downloadBlob(blob, documento.nomeArquivo);
        },
        error: (error) => this.erro(error)
      });
  }

  protected copiarCodigo(documento: DocumentoResumo): void {
    const codigo = documentoAsExtended(documento).codigoPagamento;
    if (!codigo || this.copiandoId()) return;

    this.copiandoId.set(documento.id);
    navigator.clipboard
      ?.writeText(codigo)
      .then(() => this.notifications.success('Código copiado integralmente.'))
      .catch(() => this.notifications.error('Não foi possível copiar o código.'))
      .finally(() => this.copiandoId.set(null));
  }

  protected confirmarRemocao(documento: DocumentoResumo): void {
    if (!this.acoes(documento).remover || this.mutacaoEmAndamento()) return;

    void this.confirmations
      .confirm({
        title: 'Remover documento',
        message: `O arquivo “${documento.nomeArquivo}” será removido da solicitação e do repositório.`,
        confirmLabel: 'Remover',
        cancelLabel: 'Voltar',
        variant: 'destructive'
      })
      .then((confirmed) => {
        if (confirmed) this.remover(documento);
      });
  }

  protected confirmarDesvinculacao(documento: DocumentoResumo): void {
    if (!this.acoes(documento).desvincular || this.mutacaoEmAndamento()) return;

    void this.confirmations
      .confirm({
        title: 'Desvincular documento',
        message: `Deseja desvincular o arquivo “${documento.nomeArquivo}” desta solicitação? O documento continuará salvo no repositório geral.`,
        confirmLabel: 'Desvincular',
        cancelLabel: 'Voltar',
        variant: 'destructive'
      })
      .then((confirmed) => {
        if (confirmed) this.desvincular(documento);
      });
  }

  protected abrirPendencia(documento: DocumentoResumo): void {
    if (this.acoes(documento).apontarPendencia && !this.mutacaoEmAndamento()) {
      this.acaoDialog.set('PENDENCIA');
      this.dialogDocumentoId.set(documento.id);
      this.mutacaoTipo.set('PENDENCIA');
      this.textoPendencia.set('');
      this.motivoPendenciaErro.set(undefined);
      this.dialogVisivel.set(true);
    }
  }

  protected fecharDialogo(): void {
    if (!this.mutacaoEmAndamento()) {
      this.dialogVisivel.set(false);
      this.resetMutacao();
    }
  }

  protected confirmarDialogo(): void {
    const documentoId = this.dialogDocumentoId();
    if (!documentoId || this.mutacaoId()) return;

    const motivo = this.textoPendencia().trim();
    if (!motivo) {
      this.motivoPendenciaErro.set('Informe o motivo da pendência.');
      return;
    }

    this.mutacaoId.set(documentoId);
    const chave = novaChaveIdempotencia();
    this.service.apontarPendenciaDocumento(documentoId, { motivo }, chave).subscribe({
      next: () => this.mutacaoConcluida('Pendência registrada.'),
      error: (error) => this.erroMutacao(error)
    });
  }

  protected arquivar(documento: DocumentoResumo): void {
    if (this.acoes(documento).arquivar) {
      this.mutacaoSimples(
        documento,
        this.service.arquivarDocumento(documento.id, novaChaveIdempotencia()),
        'Documento arquivado.'
      );
    }
  }

  protected reabrir(documento: DocumentoResumo): void {
    if (this.acoes(documento).reabrir) {
      this.mutacaoSimples(
        documento,
        this.service.reabrirDocumento(documento.id, novaChaveIdempotencia()),
        'Documento reaberto para conferência.'
      );
    }
  }

  protected desvincular(documento: DocumentoResumo): void {
    if (this.acoes(documento).desvincular) {
      this.mutacaoSimples(
        documento,
        this.service.desvincularDocumento(documento.id, novaChaveIdempotencia()),
        'Documento desvinculado da solicitação.'
      );
    }
  }

  protected abrirEdicao(documento: DocumentoResumo): void {
    if (!this.acoes(documento).editarMetadados || this.mutacaoEmAndamento()) return;

    this.editandoId.set(documento.id);
    this.service.buscarDocumento(documento.id).subscribe({
      next: (completo) => {
        this.preencherEditor(completo);
        this.etapaEditor.set(1);
        this.editandoId.set(null);
        this.editorVisivel.set(true);
      },
      error: (error) => {
        this.editandoId.set(null);
        this.erro(error);
      }
    });
  }

  protected fecharEdicao(): void {
    if (!this.salvandoEdicao()) this.editorVisivel.set(false);
  }

  protected salvarEdicao(): void {
    if (!this.editor.id || this.salvandoEdicao()) return;

    this.salvandoEdicao.set(true);
    const request: DocumentoRequest = {
      tipo: this.editor.tipo,
      numeroDocumento: this.editor.numeroDocumento || null,
      chaveAcesso: this.editor.chaveAcesso || null,
      codigoPagamento: this.editor.codigoPagamento || null,
      fornecedorId: this.editor.fornecedorId || null,
      emissorNome: this.editor.emissorNome || null,
      dataEmissao: this.editor.dataEmissao || null,
      dataVencimento: this.editor.dataVencimento || null,
      competencia: this.editor.competencia || null,
      valor: this.editor.valor,
      baseId: this.editor.baseId || null,
      processoId: this.editor.processoId || null,
      obraId: this.editor.obraId || null,
      descricao: this.editor.descricao || null,
      observacoes: this.editor.observacoes || null
    };

    this.service.atualizarDocumento(this.editor.id, request, novaChaveIdempotencia()).subscribe({
      next: () => {
        this.salvandoEdicao.set(false);
        this.editorVisivel.set(false);
        this.notifications.success('Metadados atualizados.');
        this.recarregarSolicitacao();
      },
      error: (error) => this.erroMutacao(error)
    });
  }

  protected icone(documento: DocumentoResumo): string {
    const type = documento.contentType.toLowerCase();
    if (type.includes('image')) return 'pi pi-image';
    if (type.includes('pdf')) return 'pi pi-file-pdf';
    if (type.includes('xml')) return 'pi pi-code';
    if (type.includes('sheet') || type.includes('excel')) return 'pi pi-file-excel';
    return 'pi pi-file';
  }

  protected tamanho(bytes: number): string {
    return bytes < 1024 * 1024
      ? `${Math.ceil(bytes / 1024)} KB`
      : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  protected tomSituacao(
    situacao: SituacaoDocumento
  ): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
    return situacao === 'CONFERIDO'
      ? 'success'
      : situacao === 'COM_PENDENCIA'
        ? 'danger'
        : situacao === 'PENDENTE'
          ? 'warning'
          : 'neutral';
  }

  private remover(documento: DocumentoResumo): void {
    this.removendoId.set(documento.id);
    this.service
      .removerDocumentoDaSolicitacao(
        this.tipo(),
        this.solicitacao().id,
        documento.id,
        novaChaveIdempotencia()
      )
      .subscribe({
        next: (response) => {
          this.removendoId.set(null);
          this.atualizada.emit(
            response ?? {
              ...this.solicitacao(),
              documentos: this.solicitacao().documentos.filter((item) => item.id !== documento.id)
            }
          );
          this.notifications.success('Documento removido.');
        },
        error: (error) => this.erroMutacao(error)
      });
  }

  private mutacaoSimples<T>(
    documento: DocumentoResumo,
    request: import('rxjs').Observable<T>,
    sucesso: string
  ): void {
    if (this.mutacaoEmAndamento()) return;
    this.mutacaoId.set(documento.id);
    request.subscribe({
      next: () => this.mutacaoConcluida(sucesso),
      error: (error) => this.erroMutacao(error)
    });
  }

  private mutacaoConcluida(sucesso: string): void {
    this.resetMutacao();
    this.dialogVisivel.set(false);
    this.notifications.success(sucesso);
    this.recarregarSolicitacao();
  }

  private recarregarSolicitacao(): void {
    this.service.buscarSolicitacao(this.tipo(), this.solicitacao().id).subscribe({
      next: (response) => this.atualizada.emit(response),
      error: (error) => this.erro(error)
    });
  }

  private erroMutacao(error: unknown): void {
    this.resetMutacao();
    this.salvandoEdicao.set(false);
    this.erro(error);
    this.recarregarSolicitacao();
  }

  private validarArquivos(arquivos: readonly File[]): string[] {
    const erros: string[] = [];

    for (const arquivo of arquivos) {
      const extensao = arquivo.name.split('.').pop()?.toLowerCase() ?? '';
      if (!this.extensoesAceitas.includes(extensao)) {
        erros.push(`${arquivo.name}: formato não aceito.`);
      }
      if (arquivo.size > this.limiteArquivoBytes) {
        erros.push(`${arquivo.name} ultrapassa 15 MB.`);
      }
    }

    const tamanhoTotal = arquivos.reduce((total, arquivo) => total + arquivo.size, 0);
    if (tamanhoTotal > this.limiteRequisicaoBytes) {
      erros.push('O conjunto de arquivos ultrapassa 20 MB.');
    }

    return erros;
  }

  private erroUpload(error: unknown): void {
    this.enviando.set(false);
    const status =
      typeof error === 'object' && error !== null && 'status' in error
        ? Number((error as { status?: unknown }).status)
        : undefined;

    if (status === 413) {
      this.notifications.error('Os arquivos ultrapassam o limite permitido.');
    } else if (status === 400 || status === 422) {
      this.notifications.error(
        'Um ou mais arquivos são inválidos. Revise o formato e o tamanho.'
      );
    } else if (status === 403) {
      this.notifications.error('Você não possui permissão para anexar documentos.');
    } else {
      this.notifications.error('Não foi possível enviar os anexos.');
    }
  }

  private erro(error: unknown): void {
    this.enviando.set(false);
    this.baixandoId.set(null);
    this.visualizandoId.set(null);
    this.removendoId.set(null);
    this.editandoId.set(null);
    this.notifications.error(getErrorMessage(error));
  }

  private resetMutacao(): void {
    this.mutacaoId.set(null);
    this.dialogDocumentoId.set(null);
    this.mutacaoTipo.set(null);
    this.acaoDialog.set(null);
  }

  private novoEditor(): EditorDocumento {
    return {
      id: '',
      tipo: 'OUTRO',
      numeroDocumento: '',
      chaveAcesso: '',
      codigoPagamento: '',
      fornecedorId: '',
      emissorNome: '',
      dataEmissao: '',
      dataVencimento: '',
      competencia: '',
      valor: null,
      baseId: '',
      processoId: '',
      obraId: '',
      descricao: '',
      observacoes: ''
    };
  }

  private preencherEditor(documento: DocumentoFinanceiro): void {
    Object.assign(this.editor, {
      id: documento.id,
      tipo: documento.tipo,
      numeroDocumento: documento.numeroDocumento ?? '',
      chaveAcesso: documento.chaveAcesso ?? '',
      codigoPagamento:
        (documento as DocumentoFinanceiro & { codigoPagamento?: string | null }).codigoPagamento ??
        '',
      fornecedorId: documento.fornecedorId ?? '',
      emissorNome: documento.emissorNome ?? '',
      dataEmissao: dateOnly(documento.dataEmissao) ?? '',
      dataVencimento: dateOnly(documento.dataVencimento) ?? '',
      competencia: dateOnly(documento.competencia) ?? '',
      valor: documento.valor ?? null,
      baseId: documento.baseId ?? '',
      processoId: documento.processoId ?? '',
      obraId: documento.obraId ?? '',
      descricao: documento.descricao ?? '',
      observacoes: documento.observacoes ?? ''
    });
  }
}

function documentoAsExtended(documento: DocumentoResumo): DocumentoComIndicadores {
  return documento as DocumentoComIndicadores;
}

function dateOnly(value?: string | null): string | null {
  return value ? value.slice(0, 10) : null;
}
