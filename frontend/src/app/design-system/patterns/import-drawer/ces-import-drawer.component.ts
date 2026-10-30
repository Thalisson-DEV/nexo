import { ChangeDetectionStrategy, Component, ElementRef, computed, input, model, output, viewChild } from '@angular/core';
import { CesTone } from '../../core';
import { CesFormDrawerComponent } from '../../forms/form-kit/ces-form-drawer.component';
import { CesButtonComponent } from '../../primitives/button';

/** Linha de importação no formato comum dos endpoints de pré-visualização do Nexo. */
export interface CesImportRow {
  readonly linha?: number;
  readonly status?: string;
  readonly mensagemErro?: string | null;
  editing?: boolean;
}

/** Selo de situação de uma linha importada (texto + tom; nunca só cor). */
export function cesImportStatus(status: string | undefined): { label: string; tone: CesTone; icon: string } {
  switch (status) {
    case 'AGUARDANDO_PROCESSAMENTO':
      return { label: 'Pronta', tone: 'success', icon: 'pi-check' };
    case 'COM_ERRO':
      return { label: 'Com erro', tone: 'danger', icon: 'pi-times' };
    case 'PROCESSADO':
    case 'SUCESSO':
      return { label: 'Processada', tone: 'success', icon: 'pi-check-circle' };
    default:
      return { label: 'Em processamento', tone: 'info', icon: 'pi-clock' };
  }
}

/**
 * Importação de planilha num drawer largo, sem trocar a lista principal: 1) baixar o modelo e
 * escolher o arquivo; 2) conferir a pré-visualização (a tela projeta um `ces-data-table` com as
 * colunas e a edição por linha); 3) processar. Fechar com linhas carregadas pede confirmação.
 */
@Component({
  selector: 'ces-import-drawer',
  standalone: true,
  imports: [CesFormDrawerComponent, CesButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .ces-import-drawer__steps {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 11rem), 1fr));
      gap: 0.5rem;
      margin: 0 0 1rem;
      padding: 0;
      list-style: none;
    }

    .ces-import-drawer__step {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-md);
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-caption);
      font-weight: 600;
    }

    .ces-import-drawer__step[data-current='true'] {
      border-color: var(--ces-brand);
      background: var(--ces-brand-soft);
      color: var(--ces-foreground);
    }

    .ces-import-drawer__step-number {
      display: inline-grid;
      width: 1.5rem;
      height: 1.5rem;
      place-items: center;
      border-radius: 999px;
      background: var(--ces-surface-muted);
      font-variant-numeric: tabular-nums;
    }

    .ces-import-drawer__step[data-current='true'] .ces-import-drawer__step-number {
      background: var(--ces-brand);
      color: var(--p-primary-contrast-color);
    }

    .ces-import-drawer__pick {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      margin-bottom: 1rem;
      padding: 1rem;
      border: 1px dashed var(--ces-border-strong);
      border-radius: var(--ces-radius-lg);
      background: var(--ces-surface-subtle);
    }

    .ces-import-drawer__pick p {
      margin: 0;
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-body);
    }

    .ces-import-drawer__pick-actions,
    .ces-import-drawer__summary {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }

    .ces-import-drawer__summary {
      margin-bottom: 0.75rem;
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-caption);
      font-weight: 600;
    }

    .ces-import-drawer__file {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
    }
  `,
  template: `
    <ces-form-drawer
      #drawer
      [(visible)]="visible"
      [title]="title()"
      [description]="description()"
      size="xl"
      [dirty]="rowCount() > 0"
      discardMessage="As linhas carregadas e as correções feitas serão descartadas. A lista principal continua como estava."
      (closed)="closed.emit()"
    >
      <ol class="ces-import-drawer__steps" aria-label="Etapas da importação">
        <li class="ces-import-drawer__step" [attr.data-current]="rowCount() === 0">
          <span class="ces-import-drawer__step-number" aria-hidden="true">1</span> Modelo e arquivo
        </li>
        <li class="ces-import-drawer__step" [attr.data-current]="rowCount() > 0">
          <span class="ces-import-drawer__step-number" aria-hidden="true">2</span> Conferir e corrigir
        </li>
        <li class="ces-import-drawer__step">
          <span class="ces-import-drawer__step-number" aria-hidden="true">3</span> Processar
        </li>
      </ol>

      <div class="ces-import-drawer__pick">
        <p>Use o modelo para montar a planilha ({{ accept() }}). As linhas com erro podem ser corrigidas aqui antes de processar.</p>
        <div class="ces-import-drawer__pick-actions">
          <ces-button variant="ghost" size="sm" icon="pi pi-download" label="Baixar modelo" (pressed)="templateRequested.emit()"></ces-button>
          <ces-button variant="outline" size="sm" icon="pi pi-upload" [label]="rowCount() ? 'Trocar arquivo' : 'Selecionar arquivo'"
            [loading]="uploading()" (pressed)="fileInput().nativeElement.click()"></ces-button>
          <input #arquivo type="file" class="ces-import-drawer__file" [accept]="accept()" aria-label="Arquivo para importar" (change)="onFile($event)" />
        </div>
      </div>

      @if (rowCount() > 0) {
        <p class="ces-import-drawer__summary" aria-live="polite">
          {{ rowCount() }} {{ rowCount() === 1 ? 'linha' : 'linhas' }} · {{ errorCount() }} com erro
          @if (blocked()) {
            · corrija a planilha ou remova as linhas com erro para processar
          }
        </p>
      }
      <ng-content></ng-content>

      <div drawer-footer class="ces-actions-row">
        @if (rowCount() > 0) {
          <ces-button variant="ghost" icon="pi pi-trash" label="Limpar linhas" [disabled]="processing()" (pressed)="cleared.emit()"></ces-button>
        }
        <ces-button variant="ghost" label="Fechar" (pressed)="drawer.requestClose()"></ces-button>
        <ces-button icon="pi pi-check" label="Processar importação" [disabled]="!rowCount() || processing() || blocked()"
          [loading]="processing()" (pressed)="processed.emit()"></ces-button>
      </div>
    </ces-form-drawer>
  `,
})
export class CesImportDrawerComponent {
  readonly visible = model(false);
  readonly title = input('Importar planilha');
  readonly description = input<string | undefined>();
  readonly accept = input('.csv');
  /** Linhas carregadas (para a contagem e a confirmação ao fechar). */
  readonly rows = input<readonly CesImportRow[]>([]);
  readonly uploading = input(false);
  readonly processing = input(false);
  /** `true` = só processa sem linhas com erro (importações tudo-ou-nada, ex.: usuários). */
  readonly blockOnErrors = input(false);

  readonly templateRequested = output<void>();
  readonly fileSelected = output<File>();
  readonly processed = output<void>();
  readonly cleared = output<void>();
  readonly closed = output<void>();

  protected readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('arquivo');
  protected readonly rowCount = computed(() => this.rows().length);
  protected readonly errorCount = computed(() => this.rows().filter((r) => r.status === 'COM_ERRO').length);
  protected readonly blocked = computed(() => this.blockOnErrors() && this.errorCount() > 0);

  protected onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    if (arquivo) this.fileSelected.emit(arquivo);
    input.value = '';
  }
}
