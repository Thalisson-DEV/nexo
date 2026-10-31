import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import {
  CesBlockingProgressDialogComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesIconButtonComponent,
  CesImportDrawerComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesImportStatus
} from '@ces/ui';
import { Observable } from 'rxjs';

import { PreviaImportacaoObraResponse, SituacaoLinhaImportacao, TipoImportacaoObra } from '../../../core/models/obras-kit.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ObrasKitService } from '../../../core/services/obras-kit.service';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';

/** Linha da pré-visualização: campos em texto, situação e mensagem vindas da validação no servidor. */
export type LinhaPlanilha = Record<string, unknown> & {
  linha?: number;
  status?: SituacaoLinhaImportacao;
  mensagemErro?: string | null;
  editing?: boolean;
};

/** Coluna da pré-visualização. Com `opcoes`, a correção usa um select; sem, um campo de texto. */
export interface ColunaPlanilha {
  key: string;
  header: string;
  priority?: 1 | 2 | 3;
  editavel?: boolean;
  opcoes?: readonly CesSelectOption<string>[];
  /** Texto exibido no lugar do valor (ex.: "Nova" / "Existente"). */
  exibir?: (linha: LinhaPlanilha) => string;
}

/** Fonte de dados da importação: valida o arquivo e confirma as linhas conferidas. */
export interface FonteImportacao {
  validar: (arquivo: File, idContrato: string | null) => Observable<PreviaImportacaoObraResponse<LinhaPlanilha>>;
  /** Revalida as linhas já carregadas (depois de uma correção), sem gravar. */
  revalidar: (registros: LinhaPlanilha[], idContrato: string | null) => Observable<PreviaImportacaoObraResponse<LinhaPlanilha>>;
  confirmar: (registros: LinhaPlanilha[], idContrato: string | null, nomeArquivo: string | null) =>
    Observable<PreviaImportacaoObraResponse<LinhaPlanilha>>;
}

/**
 * Importação de planilha das telas de Obras no padrão de importação do Nexo (`ces-import-drawer`):
 * parâmetros (ex.: contrato) → modelo e arquivo → pré-visualização validada no servidor, com correção por
 * linha e filtro das linhas com erro → processar. As linhas válidas são processadas em segundo plano.
 */
@Component({
  selector: 'app-importacao-planilha-obras',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesBlockingProgressDialogComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesIconButtonComponent,
    CesImportDrawerComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent
  ],
  template: `
    <ces-import-drawer [(visible)]="visible" [title]="titulo()" [description]="descricao()" accept=".xlsx,.csv"
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="enviar($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (exigeContrato()) {
        <ces-select import-params label="Contrato" [required]="true" [options]="contratos()" [value]="idContrato()"
          (valueChange)="trocarContrato($event)" [filter]="true" placeholder="Selecione o contrato" [error]="erroContrato()"
          [description]="importacao.linhas().length ? 'Trocar o contrato descarta as linhas carregadas.' : 'Escolha o contrato antes do arquivo.'"></ces-select>
      }
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" searchPlaceholder="Buscar nas linhas" [columns]="colunasTabela()"
          [rows]="exibidas()" [rowKey]="chave" [pageSizeOptions]="[25, 50, 100]">
          <div table-filters class="ces-quick-filters">
            @if (comErro() > 0) {
              <button type="button" class="ces-quick-filter" [attr.aria-pressed]="apenasErros()" (click)="apenasErros.set(!apenasErros())">
                <i class="pi pi-filter" aria-hidden="true"></i> Só as {{ comErro() }} com erro
              </button>
            }
          </div>
          @for (coluna of colunas(); track coluna.key) {
            <ng-template [cesCell]="coluna.key" let-row>
              @if (row.editing && coluna.editavel !== false) {
                @if (coluna.opcoes) {
                  <ces-select [label]="coluna.header" [labelVisible]="false" [options]="coluna.opcoes" [value]="$any(row[coluna.key]) ?? null"
                    (valueChange)="row[coluna.key] = $event" [clearable]="true"></ces-select>
                } @else {
                  <ces-text-input [label]="coluna.header" [labelVisible]="false" [value]="$any(row[coluna.key]) ?? ''"
                    (valueChange)="row[coluna.key] = $event"></ces-text-input>
                }
              } @else if (coluna.exibir) {
                {{ coluna.exibir(row) }}
              } @else {
                {{ row[coluna.key] || '—' }}
              }
            </ng-template>
          }
          <ng-template cesCell="situacao" let-row>
            <ces-status [tone]="situacao(row.status).tone" [icon]="situacao(row.status).icon">{{ situacao(row.status).label }}</ces-status>
            @if (row.mensagemErro) {
              <span class="ces-cell-muted" [title]="row.mensagemErro">{{ row.mensagemErro }}</span>
            }
          </ng-template>
          <ng-template cesRowActions let-row>
            <span class="ces-table-actions">
              @if (row.editing) {
                <ces-icon-button icon="pi pi-check" [label]="'Revalidar a linha ' + row.linha" size="icon-sm" (pressed)="revalidar(row)"></ces-icon-button>
              } @else if (row.status !== 'EM_PROCESSAMENTO') {
                <ces-icon-button icon="pi pi-pencil" [label]="'Corrigir linha ' + row.linha" size="icon-sm" (pressed)="row.editing = true"></ces-icon-button>
                <ces-icon-button icon="pi pi-trash" [label]="'Remover linha ' + row.linha" size="icon-sm" (pressed)="importacao.remover(row)"></ces-icon-button>
              }
            </span>
          </ng-template>
        </ces-data-table>
      }
    </ces-import-drawer>

    <ces-blocking-progress-dialog [visible]="importacao.processando()" [title]="'Processando ' + titulo().toLowerCase()"
      statusTitle="Enviando as linhas conferidas" [statusMessages]="['Revalidando as linhas…', 'Registrando a importação…', 'Iniciando o processamento…']">
    </ces-blocking-progress-dialog>
  `
})
export class ImportacaoPlanilhaObrasComponent {
  private readonly servico = inject(ObrasKitService);
  private readonly notificacoes = inject(NotificationService);

  readonly visible = model(false);
  readonly tipo = input.required<TipoImportacaoObra>();
  readonly titulo = input('Importar planilha');
  readonly descricao = input<string | undefined>();
  readonly colunas = input.required<readonly ColunaPlanilha[]>();
  readonly fonte = input.required<FonteImportacao>();
  readonly exigeContrato = input(false);
  readonly contratos = input<readonly CesSelectOption<string>[]>([]);
  /** Contrato pré-selecionado (ex.: o filtro atual da listagem). */
  readonly contratoInicial = input<string | null | undefined>(null);

  /** Depois de enviar as linhas válidas para processamento (ex.: recarregar a listagem). */
  readonly processada = output<void>();

  protected readonly idContrato = signal<string | null>(null);
  private readonly nomeArquivo = signal<string | null>(null);
  private readonly tentouSemContrato = signal(false);
  protected readonly apenasErros = signal(false);
  protected readonly situacao = cesImportStatus;
  protected readonly chave = CHAVE_LINHA_IMPORTACAO;

  protected readonly importacao = importacaoListagem<LinhaPlanilha>({
    enviar: (arquivo) => this.fonte().validar(arquivo, this.idContrato()),
    confirmar: (registros) => this.fonte().confirmar(registros, this.idContrato(), this.nomeArquivo()),
    modelo: () => this.servico.baixarModelo(this.tipo()).subscribe({ error: (e) => this.notificacoes.error(getErrorMessage(e)) }),
    aoProcessar: () => this.processada.emit()
  });

  protected readonly comErro = computed(() => this.importacao.linhas().filter((l) => l.status === 'COM_ERRO').length);
  protected readonly exibidas = computed(() =>
    this.apenasErros() && this.comErro() ? this.importacao.linhas().filter((l) => l.status === 'COM_ERRO') : this.importacao.linhas()
  );
  protected readonly erroContrato = computed(() => (this.tentouSemContrato() && !this.idContrato() ? 'Selecione o contrato.' : undefined));
  protected readonly colunasTabela = computed<CesDataTableColumn<LinhaPlanilha>[]>(() => [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    ...this.colunas().map((c): CesDataTableColumn<LinhaPlanilha> => ({
      key: c.key,
      header: c.header,
      type: 'custom',
      priority: c.priority,
      value: (r) => r[c.key],
      display: (r) => (c.exibir ? c.exibir(r) : String(r[c.key] ?? ''))
    })),
    { key: 'situacao', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status, display: (r) => cesImportStatus(r.status).label }
  ]);

  constructor() {
    effect(() => {
      if (this.visible() && !this.idContrato()) this.idContrato.set(this.contratoInicial() ?? null);
    });
  }

  protected trocarContrato(id: string | null): void {
    if (id === this.idContrato()) return;
    this.idContrato.set(id);
    this.importacao.limpar();
  }

  protected enviar(arquivo: File): void {
    if (this.exigeContrato() && !this.idContrato()) {
      this.tentouSemContrato.set(true);
      this.notificacoes.warning('Selecione o contrato antes de escolher a planilha.');
      return;
    }
    this.nomeArquivo.set(arquivo.name);
    this.apenasErros.set(false);
    this.importacao.enviarArquivo(arquivo);
  }

  /** Concluir a correção: o servidor revalida as linhas (sem gravar) e a pré-visualização é atualizada. */
  protected revalidar(linha: LinhaPlanilha): void {
    linha.editing = false;
    const registros = this.importacao.linhas().map(({ editing, ...resto }) => resto as LinhaPlanilha);
    this.fonte()
      .revalidar(registros, this.idContrato())
      .subscribe({
        next: (r) => this.importacao.linhas.set(r.registros),
        error: (e) => {
          linha.editing = true;
          this.notificacoes.error(getErrorMessage(e));
        }
      });
  }
}
