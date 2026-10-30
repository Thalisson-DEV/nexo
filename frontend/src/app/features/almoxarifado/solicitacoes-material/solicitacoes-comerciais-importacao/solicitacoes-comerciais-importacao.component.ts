import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, model, output, signal } from '@angular/core';
import {
  CesBlockingProgressDialogComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesIconButtonComponent,
  CesImportDrawerComponent,
  CesNumberInputComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesImportStatus
} from '@ces/ui';

import {
  EstoqueResponse,
  ImportacaoSolicitacaoComercialResponse,
  MaterialResponse,
  SolicitacaoComercialImportRow
} from '../../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { FileDownloadUtil } from '../../../../core/utils/file-download.util';
import { toLocalDate } from '../../../../core/utils/local-date.util';
import { parseImportDate } from '../import-date.util';
import { MENSAGENS_PROCESSAMENTO_IMPORTACAO } from '../importacao-progress-messages';
import { contarSolicitacoesComerciaisPrevistas } from '../solicitacao-comercial-importacao-contagem.util';

/**
 * Importação de solicitações comerciais (tipo 221, estoque físico → estoque de campo) por
 * planilha, no `ces-import-drawer`: correção por linha e processamento só das linhas válidas.
 */
@Component({
  selector: 'app-solicitacoes-comerciais-importacao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    CesBlockingProgressDialogComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesIconButtonComponent,
    CesImportDrawerComponent,
    CesNumberInputComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent
  ],
  template: `
    <ces-import-drawer [(visible)]="visible" title="Importar solicitações comerciais"
      description="Planilha CSV com estoque de origem (físico), estoque de destino (de campo), material, quantidade, data prevista do saque e observação. Quem solicita é você."
      [rows]="registros()" [uploading]="enviando()" [processing]="processando()"
      (templateRequested)="baixarModeloCsv()" (fileSelected)="onFileSelected($event)"
      (processed)="onProcessImport()" (cleared)="limpar()">
      @if (registros().length) {
        <p class="ces-cell-muted">
          {{ validos() }} {{ validos() === 1 ? 'item válido formará' : 'itens válidos formarão' }} {{ solicitacoesPrevistas() }}
          {{ solicitacoesPrevistas() === 1 ? 'solicitação' : 'solicitações' }}. Só as linhas sem erro são processadas.
        </p>
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunas"
          [rows]="exibidos()" [rowKey]="chaveLinha" [pageSizeOptions]="[25, 50, 100]">
          <div table-filters class="ces-quick-filters">
            @if (comErro() > 0) {
              <button type="button" class="ces-quick-filter" [attr.aria-pressed]="apenasErros()" (click)="apenasErros.set(!apenasErros())">
                <i class="pi pi-filter" aria-hidden="true"></i> Só as {{ comErro() }} com erro
              </button>
            }
          </div>
          <ng-template cesCell="origem" let-row>
            @if (row.editing) {
              <ces-select label="Estoque de origem" [labelVisible]="false" [options]="opcoesOrigem()" [value]="row.nomeEstoqueOrigem || null"
                (valueChange)="onEstoqueOrigemChange(row, $event)" [filter]="true" [clearable]="true" placeholder="Estoque físico"></ces-select>
            } @else { {{ row.nomeEstoqueOrigem || '—' }} }
          </ng-template>
          <ng-template cesCell="destino" let-row>
            @if (row.editing) {
              <ces-select label="Estoque de destino" [labelVisible]="false" [options]="opcoesDestino()" [value]="row.nomeEstoqueDestino || null"
                (valueChange)="onEstoqueDestinoChange(row, $event)" [filter]="true" [clearable]="true" placeholder="Estoque de campo"></ces-select>
            } @else { {{ row.nomeEstoqueDestino || '—' }} }
          </ng-template>
          <ng-template cesCell="material" let-row>
            @if (row.editing) {
              <ces-select label="Material" [labelVisible]="false" [options]="opcoesMateriais()" [value]="row.codigoMaterial || null"
                (valueChange)="onMaterialChange(row, $event)" [filter]="true" [clearable]="true"></ces-select>
            } @else {
              <span class="ces-cell-strong">{{ row.codigoMaterial || '—' }}</span>
              @if (row.nomeMaterial) { <span class="ces-cell-muted">{{ row.nomeMaterial }}</span> }
            }
          </ng-template>
          <ng-template cesCell="quantidade" let-row>
            @if (row.editing) {
              <ces-number-input label="Quantidade" [labelVisible]="false" [min]="0.001" [maxFractionDigits]="3" [(value)]="row.quantidade"></ces-number-input>
            } @else { {{ row.quantidade != null ? (row.quantidade | number: '1.3-3') : '—' }} }
          </ng-template>
          <ng-template cesCell="dataPrevista" let-row>
            @if (row.editing) {
              <ces-datepicker label="Data prevista do saque" [labelVisible]="false" [value]="parseImportDate(row.dataPrevista)"
                (valueChange)="row.dataPrevista = toLocalDate($event) ?? undefined"></ces-datepicker>
            } @else { {{ row.dataPrevista || '—' }} }
          </ng-template>
          <ng-template cesCell="observacao" let-row>
            @if (row.editing) {
              <ces-text-input label="Observação" [labelVisible]="false" [(value)]="row.observacao"></ces-text-input>
            } @else { {{ row.observacao || '—' }} }
          </ng-template>
          <ng-template cesCell="situacao" let-row>
            <ces-status [tone]="situacao(row.status).tone" [icon]="situacao(row.status).icon">{{ situacao(row.status).label }}</ces-status>
            @if (row.mensagemErro) { <span class="ces-cell-muted">{{ row.mensagemErro }}</span> }
          </ng-template>
          <ng-template cesRowActions let-row>
            <span class="ces-table-actions">
              @if (row.editing) {
                <ces-icon-button icon="pi pi-check" [label]="'Concluir correção da linha ' + row.linha" size="icon-sm" (pressed)="finishImportEdit(row)"></ces-icon-button>
              } @else {
                <ces-icon-button icon="pi pi-pencil" [label]="'Corrigir linha ' + row.linha" size="icon-sm" (pressed)="startImportEdit(row)"></ces-icon-button>
                <ces-icon-button icon="pi pi-trash" [label]="'Remover linha ' + row.linha" size="icon-sm" (pressed)="removeImportRecord(row)"></ces-icon-button>
              }
            </span>
          </ng-template>
        </ces-data-table>
      }
    </ces-import-drawer>

    <ces-blocking-progress-dialog [visible]="processando()" title="Processando solicitações comerciais" statusTitle="Criando solicitações"
      [statusMessages]="mensagensProcessamento"></ces-blocking-progress-dialog>
  `
})
export class SolicitacoesComerciaisImportacaoComponent {
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  readonly visible = model(false);
  readonly estoques = input<EstoqueResponse[]>([]);
  readonly materiais = input<MaterialResponse[]>([]);
  /** Ao menos um item foi criado (a lista recarrega). */
  readonly processado = output<void>();

  protected readonly parseImportDate = parseImportDate;
  protected readonly toLocalDate = toLocalDate;
  protected readonly mensagensProcessamento = MENSAGENS_PROCESSAMENTO_IMPORTACAO;
  protected readonly situacao = cesImportStatus;
  protected readonly chaveLinha = (r: SolicitacaoComercialImportRow, i: number) => String(r.linha ?? i);

  protected readonly enviando = signal(false);
  protected readonly processando = signal(false);
  protected readonly apenasErros = signal(false);
  /** Linhas editadas no lugar: `equal` falso para redesenhar após cada correção. */
  protected readonly registros = signal<SolicitacaoComercialImportRow[]>([], { equal: () => false });
  protected readonly comErro = computed(() => this.registros().filter((r) => r.status === 'COM_ERRO').length);
  protected readonly validos = computed(() => this.registros().length - this.comErro());
  protected readonly solicitacoesPrevistas = computed(() => contarSolicitacoesComerciaisPrevistas(this.registros()));
  protected readonly exibidos = computed(() => (this.apenasErros() ? this.registros().filter((r) => r.status === 'COM_ERRO') : this.registros()));

  private readonly estoquesOrigem = computed(() => this.estoques().filter((e) => e.ativo && e.tipoEstoque?.nome?.toUpperCase() === 'FISICO'));
  private readonly estoquesDestino = computed(() => this.estoques().filter((e) => e.ativo && e.tipoEstoque?.nome?.toUpperCase() === 'CAMPO' && !!e.idEquipe));
  protected readonly opcoesOrigem = computed<CesSelectOption<string>[]>(() => this.estoquesOrigem().map((e) => ({ label: e.nomeIdentificador, value: e.nomeIdentificador })));
  protected readonly opcoesDestino = computed<CesSelectOption<string>[]>(() => this.estoquesDestino().map((e) => ({ label: e.nomeIdentificador, value: e.nomeIdentificador })));
  protected readonly opcoesMateriais = computed<CesSelectOption<string>[]>(() => this.materiais().map((m) => ({ label: `${m.codigo} - ${m.descricao}`, value: m.codigo })));

  protected readonly colunas: CesDataTableColumn<SolicitacaoComercialImportRow>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'origem', header: 'Origem', type: 'custom', priority: 1, value: (r) => r.nomeEstoqueOrigem },
    { key: 'destino', header: 'Destino', type: 'custom', priority: 1, value: (r) => r.nomeEstoqueDestino },
    { key: 'material', header: 'Material', type: 'custom', priority: 1, value: (r) => r.codigoMaterial },
    { key: 'quantidade', header: 'Qtd.', type: 'custom', value: (r) => r.quantidade },
    { key: 'dataPrevista', header: 'Saque previsto', type: 'custom', value: (r) => r.dataPrevista },
    { key: 'observacao', header: 'Observação', type: 'custom', value: (r) => r.observacao },
    { key: 'situacao', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  private readonly editSnapshots = new WeakMap<SolicitacaoComercialImportRow, { values: string; previousStatus: string; previousError?: string }>();

  protected baixarModeloCsv(): void {
    this.almoxarifado.baixarModeloCsvSolicitacoesComerciais().subscribe({
      next: (blob) => FileDownloadUtil.downloadBlob(blob, 'modelo_importacao_solicitacoes_comerciais.csv'),
      error: (err) => this.notificacoes.error(getErrorMessage(err), { title: 'Falha ao baixar modelo' })
    });
  }

  protected onFileSelected(arquivo: File): void {
    this.enviando.set(true);
    this.almoxarifado.validarImportacaoSolicitacoesComerciais(arquivo).subscribe({
      next: (res) => {
        this.enviando.set(false);
        this.apenasErros.set(false);
        const rows = (res.registros || []).map((r) => {
          const row: SolicitacaoComercialImportRow = { ...r, editing: false };
          this.editSnapshots.set(row, { values: this.serializeComercialRow(row), previousStatus: row.status || 'AGUARDANDO_PROCESSAMENTO', previousError: row.mensagemErro });
          return row;
        });
        this.registros.set(rows);
        if (res.totalFalha > 0) {
          this.notificacoes.warning(`${res.totalFalha} de ${res.totalProcessados || rows.length} registro(s) têm inconsistências.`);
        } else {
          this.notificacoes.info(`${res.totalSucesso} registro(s) pronto(s) para importação.`);
        }
      },
      error: (err) => {
        this.enviando.set(false);
        this.notificacoes.error(getErrorMessage(err), { title: 'Erro ao validar arquivo' });
      }
    });
  }

  startImportEdit(item: SolicitacaoComercialImportRow): void {
    if (!this.editSnapshots.has(item)) {
      this.editSnapshots.set(item, { values: this.serializeComercialRow(item), previousStatus: item.status || 'AGUARDANDO_PROCESSAMENTO', previousError: item.mensagemErro });
    }
    item.editing = true;
    this.registros.set(this.registros());
  }

  private serializeComercialRow(item: SolicitacaoComercialImportRow): string {
    return JSON.stringify({
      origem: (item.nomeEstoqueOrigem || '').trim().toLowerCase(),
      destino: (item.nomeEstoqueDestino || '').trim().toLowerCase(),
      mat: (item.codigoMaterial || '').trim().toLowerCase(),
      qtd: Number(item.quantidade) || 0,
      dataPrev: (item.dataPrevista || '').trim(),
      obs: (item.observacao || '').trim()
    });
  }

  onEstoqueOrigemChange(item: SolicitacaoComercialImportRow, nomeEstoque: string | null): void {
    const est = this.estoquesOrigem().find((e) => e.nomeIdentificador === nomeEstoque);
    item.idEstoqueOrigem = est?.id;
    item.nomeEstoqueOrigem = est?.nomeIdentificador || nomeEstoque || '';
  }

  onEstoqueDestinoChange(item: SolicitacaoComercialImportRow, nomeEstoque: string | null): void {
    const est = this.estoquesDestino().find((e) => e.nomeIdentificador === nomeEstoque);
    item.idEstoqueDestino = est?.id;
    item.nomeEstoqueDestino = est?.nomeIdentificador || nomeEstoque || '';
  }

  onMaterialChange(item: SolicitacaoComercialImportRow, codigoMaterial: string | null): void {
    const mat = this.materiais().find((m) => m.codigo === codigoMaterial);
    item.idMaterial = mat?.id;
    item.codigoMaterial = mat?.codigo ?? codigoMaterial ?? '';
    item.nomeMaterial = mat?.descricao ?? '';
  }

  /** Normaliza, casa com os cadastros e revalida a linha (mesmas regras do backend). */
  finishImportEdit(item: SolicitacaoComercialImportRow): void {
    item.editing = false;
    item.nomeEstoqueOrigem = item.nomeEstoqueOrigem?.trim() || '';
    item.nomeEstoqueDestino = item.nomeEstoqueDestino?.trim() || '';
    item.codigoMaterial = item.codigoMaterial?.trim() || '';
    item.dataPrevista = item.dataPrevista?.trim() || undefined;
    if (item.dataPrevista && /^\d{2}\/\d{2}\/\d{4}$/.test(item.dataPrevista)) {
      const [dia, mes, ano] = item.dataPrevista.split('/');
      item.dataPrevista = `${ano}-${mes}-${dia}`;
    }
    item.observacao = item.observacao?.trim() || undefined;
    const qtdNum = Number(item.quantidade);
    item.quantidade = !isNaN(qtdNum) ? qtdNum : item.quantidade;

    const ests = this.estoques();
    const mats = this.materiais();
    const mesmoNome = (a?: string | null, b?: string | null) => !!a && a.trim().toLowerCase() === (b || '').toLowerCase();

    const estOrigem = this.estoquesOrigem().find((e) => (item.idEstoqueOrigem && e.id === item.idEstoqueOrigem) || mesmoNome(e.nomeIdentificador, item.nomeEstoqueOrigem));
    if (estOrigem) {
      item.idEstoqueOrigem = estOrigem.id;
      item.nomeEstoqueOrigem = estOrigem.nomeIdentificador;
    } else if (ests.length > 0) {
      item.idEstoqueOrigem = undefined;
    }
    const estDestino = this.estoquesDestino().find((e) => (item.idEstoqueDestino && e.id === item.idEstoqueDestino) || mesmoNome(e.nomeIdentificador, item.nomeEstoqueDestino));
    if (estDestino) {
      item.idEstoqueDestino = estDestino.id;
      item.nomeEstoqueDestino = estDestino.nomeIdentificador;
    } else if (ests.length > 0) {
      item.idEstoqueDestino = undefined;
    }
    const mat = mats.find((m) => (item.idMaterial && m.id === item.idMaterial) || mesmoNome(m.codigo, item.codigoMaterial));
    if (mat) {
      item.idMaterial = mat.id;
      item.codigoMaterial = mat.codigo;
      item.nomeMaterial = mat.descricao;
    } else if (mats.length > 0) {
      item.idMaterial = undefined;
    }

    // Sem mudança numa linha que já tinha erro: continua com o erro original.
    const snapshot = this.editSnapshots.get(item);
    if (snapshot && snapshot.values === this.serializeComercialRow(item) && snapshot.previousStatus === 'COM_ERRO') {
      item.status = 'COM_ERRO';
      item.mensagemErro = snapshot.previousError || 'A linha permanece com dados inválidos após a edição.';
      this.registros.set(this.registros());
      return;
    }

    let erro: string | undefined;
    if (!item.nomeEstoqueOrigem) erro = 'Estoque de origem é obrigatório.';
    else if (ests.length > 0 && !item.idEstoqueOrigem) erro = 'Selecione um estoque físico de origem.';
    else if (!item.nomeEstoqueDestino) erro = 'Estoque de destino é obrigatório.';
    else if (ests.length > 0 && !item.idEstoqueDestino) erro = 'Selecione um estoque de campo vinculado a uma equipe para o destino.';
    else if ((item.idEstoqueOrigem && item.idEstoqueOrigem === item.idEstoqueDestino) || mesmoNome(item.nomeEstoqueOrigem, item.nomeEstoqueDestino))
      erro = 'O estoque de destino deve ser diferente do estoque de origem.';
    else if (!item.codigoMaterial) erro = 'Código do material é obrigatório.';
    else if (mats.length > 0 && !item.idMaterial) erro = `Material '${item.codigoMaterial}' não encontrado no cadastro.`;
    else if (item.quantidade === undefined || item.quantidade === null || isNaN(item.quantidade) || item.quantidade <= 0) erro = 'Quantidade deve ser maior que zero.';
    else if (!item.dataPrevista) erro = 'Informe a data prevista para o saque.';

    item.status = erro ? 'COM_ERRO' : 'AGUARDANDO_PROCESSAMENTO';
    item.mensagemErro = erro;
    this.registros.set(this.registros());
  }

  removeImportRecord(item: SolicitacaoComercialImportRow): void {
    this.registros.set(this.registros().filter((r) => r !== item));
  }

  protected limpar(): void {
    this.registros.set([]);
  }

  async onProcessImport(): Promise<void> {
    if (this.processando()) return;
    const validos = this.registros().filter((r) => r.status !== 'COM_ERRO');
    if (validos.length === 0) {
      this.notificacoes.warning('Não há itens válidos para criar solicitações.');
      return;
    }
    const previstas = this.solicitacoesPrevistas();
    const ok = await this.confirmacao.confirm({
      title: 'Processar importação',
      message: `Criar ${previstas} ${previstas === 1 ? 'solicitação' : 'solicitações'} com os ${validos.length} itens válidos?`,
      confirmLabel: 'Processar'
    });
    if (!ok) return;

    this.processando.set(true);
    this.almoxarifado.confirmarImportacaoSolicitacoesComerciais({ registros: validos.map(({ editing, ...resto }) => resto) }).subscribe({
      next: (res: ImportacaoSolicitacaoComercialResponse) => {
        this.processando.set(false);
        if (res.totalSucesso > 0) this.processado.emit();
        if (res.processamento?.estado === 'FALHOU' || res.totalSucesso === 0) {
          this.registros.set(res.registros);
          this.notificacoes.error(res.processamento?.mensagem || 'Nenhum item foi processado. Revise as inconsistências exibidas.', { title: 'Importação não processada' });
        } else if (res.processamento?.estado === 'PARCIAL' || res.totalFalha > 0) {
          this.registros.set(res.registros.filter((item) => item.status === 'COM_ERRO'));
          this.notificacoes.warning(
            `${res.totalSucesso} ${res.totalSucesso === 1 ? 'item criado' : 'itens criados'}; ${res.totalFalha} com erro. Revise os erros exibidos.`,
            { title: 'Importação parcial' }
          );
        } else {
          this.notificacoes.success(res.processamento?.mensagem || `${res.totalSucesso} ${res.totalSucesso === 1 ? 'item criado' : 'itens criados'}.`);
          this.registros.set([]);
          this.visible.set(false);
        }
      },
      error: (err) => {
        this.processando.set(false);
        this.notificacoes.error(getErrorMessage(err), { title: 'Erro ao confirmar importação' });
      }
    });
  }
}
