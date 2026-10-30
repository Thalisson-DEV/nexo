import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, model, output, signal } from '@angular/core';
import {
  CesBlockingProgressDialogComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesIconButtonComponent,
  CesImportDrawerComponent,
  CesNumberInputComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  CesTone,
  NotificationService
} from '@ces/ui';

import { CondicaoMaterial, EstoqueResponse, MaterialResponse, MovimentacaoImportRow } from '../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { FileDownloadUtil } from '../../../core/utils/file-download.util';

/** Separa os números de série de uma célula (vírgula, ponto e vírgula, barra, quebra de linha ou espaço). */
export function extrairSeriais(texto?: string | null): string[] {
  if (!texto || !texto.trim()) return [];
  const t = texto.trim();
  const partes = /[,;\n\r\t|/\\]/.test(t) ? t.split(/[,;\n\r\t|/\\]+/) : t.includes(' ') ? t.split(/\s+/) : [t];
  return partes.map((s) => s.replace(/^["']+|["']+$/g, '').trim()).filter((s) => s.length > 0);
}

const TIPOS: CesSelectOption<string>[] = [
  { label: 'Entrada', value: 'ENTRADA' },
  { label: 'Saída', value: 'SAIDA' },
  { label: 'Transferência', value: 'TRANSFERENCIA' }
];
const CONDICOES: CesSelectOption<CondicaoMaterial>[] = [
  { label: 'Novo', value: 'NOVO' },
  { label: 'Usado', value: 'USADO' },
  { label: 'Recuperado', value: 'RECUPERADO' },
  { label: 'Sucata', value: 'SUCATA' }
];
const TOM_TIPO: Record<string, CesTone> = { ENTRADA: 'success', SAIDA: 'danger', TRANSFERENCIA: 'brand' };
const CAMPOS: Record<string, string> = {
  tipo: 'tipo',
  origem: 'estoque de origem',
  destino: 'estoque de destino',
  material: 'material',
  numserie: 'número de série',
  quantidade: 'quantidade',
  condicao: 'condição'
};

/**
 * Importação de movimentações de estoque (entrada, saída e transferência) por planilha no
 * `ces-import-drawer`. Materiais com vários números de série na mesma linha são desdobrados
 * pelo backend; a correção revalida as regras de tipo, estoques, série e quantidade.
 */
@Component({
  selector: 'app-movimentacoes-importacao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    CesBlockingProgressDialogComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesIconButtonComponent,
    CesImportDrawerComponent,
    CesNumberInputComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent
  ],
  template: `
    <ces-import-drawer [(visible)]="visible" title="Importar movimentações"
      description="Planilha CSV com tipo (entrada, saída ou transferência), estoques, material, quantidade, condição e números de série."
      [rows]="registros()" [uploading]="enviando()" [processing]="processando()"
      (templateRequested)="baixarModeloCsv()" (fileSelected)="onFileSelected($event)"
      (processed)="onProcessImport()" (cleared)="registros.set([])">
      @if (registros().length) {
        <p class="ces-cell-muted">
          {{ validos() }} {{ validos() === 1 ? 'linha válida' : 'linhas válidas' }} ({{ unidades() | number: '1.0-3' }} unidades).
          Materiais com vários números de série na mesma linha são desdobrados automaticamente; só as linhas sem erro são processadas.
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
          <ng-template cesCell="tipo" let-row>
            @if (row.editing) {
              <ces-select label="Tipo" [labelVisible]="false" [options]="tipos" [value]="row.tipo" (valueChange)="onTipoChange(row, $event)"></ces-select>
            } @else {
              <ces-status [tone]="tomTipo(row.tipo)">{{ rotuloTipo(row.tipo) }}</ces-status>
            }
          </ng-template>
          <ng-template cesCell="origem" let-row>
            @if (row.editing) {
              <ces-select label="Estoque de origem" [labelVisible]="false" [options]="opcoesEstoques()" [value]="row.nomeEstoqueOrigem || null"
                (valueChange)="onEstoqueOrigemChange(row, $event)" [filter]="true" [clearable]="true" [disabled]="row.tipo === 'ENTRADA'"></ces-select>
            } @else { {{ row.nomeEstoqueOrigem || '—' }} }
          </ng-template>
          <ng-template cesCell="destino" let-row>
            @if (row.editing) {
              <ces-select label="Estoque de destino" [labelVisible]="false" [options]="opcoesEstoques()" [value]="row.nomeEstoqueDestino || null"
                (valueChange)="onEstoqueDestinoChange(row, $event)" [filter]="true" [clearable]="true" [disabled]="row.tipo === 'SAIDA'"></ces-select>
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
            } @else { {{ row.quantidade != null ? (row.quantidade | number: '1.0-3') : '—' }} }
          </ng-template>
          <ng-template cesCell="condicao" let-row>
            @if (row.editing) {
              <ces-select label="Condição" [labelVisible]="false" [options]="condicoes" [(value)]="row.condicao" [disabled]="row.tipo === 'TRANSFERENCIA'"></ces-select>
            } @else { {{ rotuloCondicao(row.condicao) }} }
          </ng-template>
          <ng-template cesCell="series" let-row>
            @if (row.editing) {
              <ces-text-input label="Números de série" [labelVisible]="false" placeholder="Ex.: SN-01, SN-02" [(value)]="row.numSerie"
                [description]="row.numSerie ? seriais(row.numSerie).length + ' detectada(s)' : undefined"></ces-text-input>
            } @else if (seriais(row.numSerie).length) {
              <span [title]="seriais(row.numSerie).join(', ')">{{ seriais(row.numSerie).slice(0, 3).join(', ') }}</span>
              @if (seriais(row.numSerie).length > 3) { <span class="ces-cell-muted">+{{ seriais(row.numSerie).length - 3 }}</span> }
            } @else { — }
          </ng-template>
          <ng-template cesCell="situacao" let-row>
            @if (row.status === 'COM_ERRO') {
              <ces-status tone="danger" icon="pi-times-circle">Erro em {{ campoErro(row) }}</ces-status>
              <span class="ces-cell-muted">{{ row.mensagemErro }}</span>
            } @else if (row.status === 'CONCLUIDO') {
              <ces-status tone="success" icon="pi-check">Processada</ces-status>
            } @else if (row.mensagemErro) {
              <ces-status tone="warning" icon="pi-info-circle">Aviso</ces-status>
              <span class="ces-cell-muted">{{ row.mensagemErro }}</span>
            } @else {
              <ces-status tone="info" icon="pi-check-circle">Pronta</ces-status>
            }
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

    <ces-blocking-progress-dialog [visible]="processando()" title="Processando movimentações" statusTitle="Movimentando o estoque"
      [statusMessages]="['Validando as linhas…', 'Atualizando saldos…', 'Registrando as operações…']"></ces-blocking-progress-dialog>
  `
})
export class MovimentacoesImportacaoComponent {
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  readonly visible = model(false);
  readonly estoques = input<EstoqueResponse[]>([]);
  readonly materiais = input<MaterialResponse[]>([]);
  /** Ao menos uma movimentação foi feita (a lista recarrega). */
  readonly processado = output<void>();

  protected readonly tipos = TIPOS;
  protected readonly condicoes = CONDICOES;
  protected readonly seriais = extrairSeriais;
  protected readonly chaveLinha = (r: MovimentacaoImportRow, i: number) => String(r.linha ?? i);
  protected readonly enviando = signal(false);
  protected readonly processando = signal(false);
  protected readonly apenasErros = signal(false);
  /** Linhas editadas no lugar: `equal` falso para redesenhar após cada correção. */
  readonly registros = signal<MovimentacaoImportRow[]>([], { equal: () => false });
  protected readonly comErro = computed(() => this.registros().filter((r) => r.status === 'COM_ERRO').length);
  protected readonly validos = computed(() => this.registros().length - this.comErro());
  protected readonly unidades = computed(() =>
    this.registros()
      .filter((r) => r.status !== 'COM_ERRO')
      .reduce((total, r) => total + (Number(r.quantidade) || 0), 0)
  );
  protected readonly exibidos = computed(() => (this.apenasErros() ? this.registros().filter((r) => r.status === 'COM_ERRO') : this.registros()));
  private readonly ativos = computed(() => this.estoques().filter((e) => e.ativo !== false));
  protected readonly opcoesEstoques = computed<CesSelectOption<string>[]>(() => this.ativos().map((e) => ({ label: e.nomeIdentificador, value: e.nomeIdentificador })));
  protected readonly opcoesMateriais = computed<CesSelectOption<string>[]>(() => this.materiais().map((m) => ({ label: `${m.codigo} - ${m.descricao}`, value: m.codigo })));

  protected readonly colunas: CesDataTableColumn<MovimentacaoImportRow>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'tipo', header: 'Tipo', type: 'custom', priority: 1, value: (r) => r.tipo },
    { key: 'origem', header: 'Origem', type: 'custom', value: (r) => r.nomeEstoqueOrigem },
    { key: 'destino', header: 'Destino', type: 'custom', value: (r) => r.nomeEstoqueDestino },
    { key: 'material', header: 'Material', type: 'custom', priority: 1, value: (r) => r.codigoMaterial },
    { key: 'quantidade', header: 'Qtd.', type: 'custom', value: (r) => r.quantidade },
    { key: 'condicao', header: 'Condição', type: 'custom', value: (r) => r.condicao },
    { key: 'series', header: 'Números de série', type: 'custom', value: (r) => r.numSerie },
    { key: 'situacao', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  private readonly editSnapshots = new WeakMap<MovimentacaoImportRow, { values: string; previousStatus: string; previousError?: string }>();

  protected tomTipo(tipo?: string): CesTone {
    return (tipo && TOM_TIPO[tipo]) || 'neutral';
  }

  protected rotuloTipo(tipo?: string): string {
    return TIPOS.find((t) => t.value === tipo)?.label ?? tipo ?? '—';
  }

  protected rotuloCondicao(condicao?: string | null): string {
    return CONDICOES.find((c) => c.value === (condicao || 'NOVO'))?.label ?? String(condicao);
  }

  protected baixarModeloCsv(): void {
    this.almoxarifado.baixarModeloCsvMovimentacoes().subscribe({
      next: (blob) => FileDownloadUtil.downloadBlob(blob, 'modelo_importacao_movimentacoes_estoque.csv'),
      error: (err) => this.notificacoes.error(getErrorMessage(err), { title: 'Falha ao baixar modelo' })
    });
  }

  onFileSelected(arquivo: File): void {
    this.enviando.set(true);
    this.almoxarifado.validarImportacaoMovimentacoes(arquivo).subscribe({
      next: (res) => {
        this.enviando.set(false);
        this.apenasErros.set(false);
        const rows = (res.registros || []).map((r) => {
          const row: MovimentacaoImportRow = { ...r, editing: false };
          this.editSnapshots.set(row, { values: this.serializeImportRow(row), previousStatus: row.status || 'AGUARDANDO_PROCESSAMENTO', previousError: row.mensagemErro });
          return row;
        });
        this.registros.set(rows);
        if (res.totalFalha > 0) this.notificacoes.warning(`${res.totalFalha} de ${res.totalProcessados || rows.length} registro(s) têm inconsistências.`);
        else this.notificacoes.info(`${res.totalSucesso} registro(s) pronto(s) para importação.`);
      },
      error: (err) => {
        this.enviando.set(false);
        this.notificacoes.error(getErrorMessage(err), { title: 'Erro ao validar arquivo' });
      }
    });
  }

  startImportEdit(item: MovimentacaoImportRow): void {
    if (!this.editSnapshots.has(item)) {
      this.editSnapshots.set(item, { values: this.serializeImportRow(item), previousStatus: item.status || 'AGUARDANDO_PROCESSAMENTO', previousError: item.mensagemErro });
    }
    item.editing = true;
    this.registros.set(this.registros());
  }

  private serializeImportRow(item: MovimentacaoImportRow): string {
    return JSON.stringify({
      tipo: (item.tipo || '').trim().toUpperCase(),
      origem: (item.nomeEstoqueOrigem || '').trim().toLowerCase(),
      destino: (item.nomeEstoqueDestino || '').trim().toLowerCase(),
      mat: (item.codigoMaterial || '').trim().toLowerCase(),
      qtd: Number(item.quantidade) || 0,
      seriais: (item.numSerie || '').trim().toLowerCase(),
      cond: item.condicao || 'NOVO',
      obs: (item.observacao || '').trim()
    });
  }

  onTipoChange(item: MovimentacaoImportRow, tipo: string | null): void {
    item.tipo = tipo ?? '';
    if (tipo === 'ENTRADA') {
      item.nomeEstoqueOrigem = '';
      item.idEstoqueOrigem = undefined;
    } else if (tipo === 'SAIDA') {
      item.nomeEstoqueDestino = '';
      item.idEstoqueDestino = undefined;
    } else if (tipo === 'TRANSFERENCIA') {
      item.condicao = 'NOVO';
    }
  }

  onEstoqueOrigemChange(item: MovimentacaoImportRow, nome: string | null): void {
    const est = this.ativos().find((e) => e.nomeIdentificador === nome);
    item.idEstoqueOrigem = est?.id;
    item.nomeEstoqueOrigem = est?.nomeIdentificador || nome || '';
  }

  onEstoqueDestinoChange(item: MovimentacaoImportRow, nome: string | null): void {
    const est = this.ativos().find((e) => e.nomeIdentificador === nome);
    item.idEstoqueDestino = est?.id;
    item.nomeEstoqueDestino = est?.nomeIdentificador || nome || '';
  }

  onMaterialChange(item: MovimentacaoImportRow, codigo: string | null): void {
    const mat = this.materiais().find((m) => m.codigo === codigo);
    if (mat) {
      item.idMaterial = mat.id;
      item.codigoMaterial = mat.codigo;
      item.nomeMaterial = mat.descricao;
      item.detalhaSerie = mat.detalhaSerie;
    }
  }

  /** Normaliza, casa com os cadastros, alinha quantidade × séries e revalida a linha. */
  finishImportEdit(item: MovimentacaoImportRow): void {
    item.editing = false;
    item.tipo = (item.tipo || '').trim().toUpperCase();
    item.nomeEstoqueOrigem = item.nomeEstoqueOrigem?.trim() || '';
    item.nomeEstoqueDestino = item.nomeEstoqueDestino?.trim() || '';
    item.codigoMaterial = item.codigoMaterial?.trim() || '';
    item.numSerie = item.numSerie?.trim() || '';
    item.observacao = item.observacao?.trim() || undefined;
    item.condicao = item.condicao || 'NOVO';

    const ests = this.estoques();
    const mats = this.materiais();
    const mesmoNome = (a?: string | null, b?: string | null) => !!a && a.trim().toLowerCase() === (b || '').toLowerCase();

    const estOrigem = ests.find((e) => (item.idEstoqueOrigem && e.id === item.idEstoqueOrigem) || mesmoNome(e.nomeIdentificador, item.nomeEstoqueOrigem));
    if (estOrigem) {
      item.idEstoqueOrigem = estOrigem.id;
      item.nomeEstoqueOrigem = estOrigem.nomeIdentificador;
    } else if (ests.length > 0 && item.nomeEstoqueOrigem) {
      item.idEstoqueOrigem = undefined;
    }
    const estDestino = ests.find((e) => (item.idEstoqueDestino && e.id === item.idEstoqueDestino) || mesmoNome(e.nomeIdentificador, item.nomeEstoqueDestino));
    if (estDestino) {
      item.idEstoqueDestino = estDestino.id;
      item.nomeEstoqueDestino = estDestino.nomeIdentificador;
    } else if (ests.length > 0 && item.nomeEstoqueDestino) {
      item.idEstoqueDestino = undefined;
    }
    const mat = mats.find((m) => (item.idMaterial && m.id === item.idMaterial) || mesmoNome(m.codigo, item.codigoMaterial));
    if (mat) {
      item.idMaterial = mat.id;
      item.codigoMaterial = mat.codigo;
      item.nomeMaterial = mat.descricao;
      item.detalhaSerie = mat.detalhaSerie;
    } else if (mats.length > 0) {
      item.idMaterial = undefined;
    }

    // Material serializado: a quantidade acompanha a contagem de séries informadas.
    const seriais = extrairSeriais(item.numSerie);
    const qtd = Number(item.quantidade);
    if (item.detalhaSerie && seriais.length > 0) item.quantidade = !qtd || qtd <= 0 || qtd !== seriais.length ? seriais.length : qtd;
    else item.quantidade = !isNaN(qtd) ? qtd : item.quantidade;

    const snapshot = this.editSnapshots.get(item);
    if (snapshot && snapshot.values === this.serializeImportRow(item) && snapshot.previousStatus === 'COM_ERRO') {
      item.status = 'COM_ERRO';
      item.mensagemErro = snapshot.previousError || 'A linha permanece com dados inválidos após a edição.';
      this.registros.set(this.registros());
      return;
    }

    const [campoErro, erro] = this.validar(item, seriais, ests.length > 0, mats.length > 0);
    item.status = erro ? 'COM_ERRO' : 'AGUARDANDO_PROCESSAMENTO';
    item.campoErro = campoErro;
    item.mensagemErro = erro;
    this.editSnapshots.set(item, { values: this.serializeImportRow(item), previousStatus: item.status, previousError: item.mensagemErro });
    this.registros.set(this.registros());
  }

  private validar(item: MovimentacaoImportRow, seriais: string[], temEstoques: boolean, temMateriais: boolean): [string?, string?] {
    const { tipo } = item;
    if (!tipo || !['ENTRADA', 'SAIDA', 'TRANSFERENCIA'].includes(tipo)) return ['tipo', 'Tipo de movimentação: selecione Entrada, Saída ou Transferência.'];
    if (tipo === 'ENTRADA' && !item.nomeEstoqueDestino) return ['destino', 'Estoque de destino: campo obrigatório para entrada.'];
    if (tipo === 'ENTRADA' && temEstoques && !item.idEstoqueDestino) return ['destino', 'Estoque de destino: selecione um estoque válido de destino.'];
    if (tipo === 'SAIDA' && !item.nomeEstoqueOrigem) return ['origem', 'Estoque de origem: campo obrigatório para saída.'];
    if (tipo === 'SAIDA' && temEstoques && !item.idEstoqueOrigem) return ['origem', 'Estoque de origem: selecione um estoque válido de origem.'];
    if (tipo === 'TRANSFERENCIA' && !item.nomeEstoqueOrigem) return ['origem', 'Estoque de origem: campo obrigatório para transferência.'];
    if (tipo === 'TRANSFERENCIA' && !item.nomeEstoqueDestino) return ['destino', 'Estoque de destino: campo obrigatório para transferência.'];
    if (
      tipo === 'TRANSFERENCIA' &&
      ((item.idEstoqueOrigem && item.idEstoqueOrigem === item.idEstoqueDestino) || item.nomeEstoqueOrigem!.toLowerCase() === item.nomeEstoqueDestino!.toLowerCase())
    )
      return ['destino', 'Estoque de destino: deve ser diferente do estoque de origem.'];
    if (tipo === 'TRANSFERENCIA' && item.condicao !== 'NOVO') return ['condicao', 'Condição: transferências aceitam apenas materiais novos.'];
    if (!item.codigoMaterial) return ['material', 'Material: código do material é obrigatório.'];
    if (temMateriais && !item.idMaterial) return ['material', `Material: material '${item.codigoMaterial}' não encontrado no cadastro.`];
    if (item.detalhaSerie && seriais.length === 0) return ['numSerie', `Número de Série: o material '${item.codigoMaterial}' exige número(s) de série.`];
    if (item.detalhaSerie && new Set(seriais.map((s) => s.toLowerCase())).size !== seriais.length)
      return ['numSerie', 'Número de Série: contém números de série duplicados na mesma linha.'];
    if (item.detalhaSerie && item.quantidade && item.quantidade !== seriais.length)
      return ['quantidade', `Quantidade: informada (${item.quantidade}) diverge da contagem de seriais (${seriais.length}).`];
    if (!item.detalhaSerie && seriais.length > 0) return ['numSerie', `Número de Série: o material '${item.codigoMaterial}' não permite controle por número de série.`];
    if (item.quantidade === undefined || item.quantidade === null || isNaN(item.quantidade) || item.quantidade <= 0) return ['quantidade', 'Quantidade: deve ser maior que zero.'];
    return [undefined, undefined];
  }

  removeImportRecord(item: MovimentacaoImportRow): void {
    this.registros.set(this.registros().filter((r) => r !== item));
  }

  /** Campo com erro: o indicado pelo backend ou deduzido da mensagem. */
  protected campoErro(item: MovimentacaoImportRow): string {
    let campo = item.campoErro;
    if (!campo && item.mensagemErro) {
      const msg = item.mensagemErro.toLowerCase();
      campo = msg.includes('tipo') ? 'tipo' : msg.includes('origem') ? 'origem' : msg.includes('destino') ? 'destino'
        : /s[ée]rie|serial/.test(msg) ? 'numSerie' : msg.includes('material') ? 'material' : msg.includes('quantidade') ? 'quantidade'
        : /condi[çc]/.test(msg) ? 'condicao' : undefined;
    }
    return (campo && CAMPOS[campo.toLowerCase()]) || 'linha';
  }

  async onProcessImport(): Promise<void> {
    if (this.processando()) return;
    if (this.registros().some((r) => r.editing)) {
      this.notificacoes.warning('Conclua as correções em andamento antes de processar.');
      return;
    }
    const validos = this.registros().filter((r) => r.status !== 'COM_ERRO');
    if (validos.length === 0) {
      this.notificacoes.warning('Não há linhas válidas para processar. Corrija as inconsistências antes.');
      return;
    }
    const ok = await this.confirmacao.confirm({
      title: 'Processar importação',
      message: `Processar ${validos.length} ${validos.length === 1 ? 'movimentação válida' : 'movimentações válidas'} no estoque?`,
      confirmLabel: 'Processar'
    });
    if (!ok) return;

    this.processando.set(true);
    this.almoxarifado.confirmarImportacaoMovimentacoes({ registros: validos.map(({ editing, ...resto }) => resto) }).subscribe({
      next: (res) => {
        this.processando.set(false);
        if (res.totalSucesso > 0) this.processado.emit();
        if (res.processamento?.estado === 'FALHOU' || res.totalSucesso === 0) {
          this.registros.set(res.registros);
          this.notificacoes.error(res.processamento?.mensagem || 'Nenhum item foi processado. Revise as inconsistências exibidas.', { title: 'Importação não processada' });
        } else if (res.processamento?.estado === 'PARCIAL' || res.totalFalha > 0) {
          this.registros.set((res.registros || []).filter((item) => item.status === 'COM_ERRO'));
          this.notificacoes.warning(
            `${res.totalSucesso} ${res.totalSucesso === 1 ? 'movimentação realizada' : 'movimentações realizadas'}; ${res.totalFalha} com erro.`,
            { title: 'Importação parcial' }
          );
        } else {
          this.notificacoes.success(res.processamento?.mensagem || `${res.totalSucesso} ${res.totalSucesso === 1 ? 'movimentação realizada' : 'movimentações realizadas'}.`);
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
