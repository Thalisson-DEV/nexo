import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, model, output } from '@angular/core';
import {
  CesCellDirective,
  CesRowActionsDirective,
  CesConfirmationService,
  CesContentState,
  CesDataTableColumn,
  CesDataTableComponent,
  CesIconButtonComponent,
  CesLazyLoadEvent,
  CesSortRule,
  CesStatusComponent,
  NotificationService,
  CesTone
} from '@ces/ui';
import { Observable } from 'rxjs';
import {
  DocumentoFrota,
  ROTULO_VENCIMENTO,
  TOM_VENCIMENTO,
  descreverPrazo,
  formatarPlaca
} from '../../../core/models/frota.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FrotaService } from '../../../core/services/frota.service';

/**
 * Tabela de documentos com download, edição e exclusão. Na ficha do veículo fica em modo cliente;
 * na tela de vencimentos, em modo servidor (`modo="server"`, com página, ordem e busca ligadas à lista).
 */
@Component({
  selector: 'app-frota-documentos-tabela',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, CesCellDirective, CesDataTableComponent, CesIconButtonComponent, CesStatusComponent],
  template: `
    <ces-data-table
      [title]="titulo()"
      [description]="descricao()"
      [mode]="modo()"
      [searchPlaceholder]="modo() === 'server' ? 'Número ou descrição do documento' : 'Filtrar nesta lista'"
      [storageKey]="storageKey()"
      [exportFileName]="exportar() ? 'frota-documentos' : undefined"
      [exportRows]="exportarLinhas()"
      [exportLimit]="limiteExportacao()"
      [columns]="mostrarTitular() ? colunasComTitular : colunas"
      [rows]="documentos()"
      [totalRecords]="total()"
      [rowKey]="chave"
      [rowLabel]="nomeDoc"
      [rowActionLabel]="podeEditar() ? 'Editar' : undefined"
      rowActionIcon="pi pi-pencil"
      [state]="estado()"
      [stateTitle]="estado() === 'error' ? 'Não foi possível carregar os documentos' : (tituloVazio() ?? 'Nenhum documento cadastrado')"
      [stateDescription]="estado() === 'error' ? (erro() ?? undefined) : vazio()"
      noResultsTitle="Nenhum documento encontrado com esse recorte."
      [filtered]="filtrado()"
      [(page)]="pagina"
      [(pageSize)]="tamanho"
      [(sort)]="ordem"
      [(query)]="busca"
      (lazyLoad)="carregar.emit($event)"
      (adjustFilters)="ajustarFiltros.emit()"
      (clearFilters)="limparFiltros.emit()"
      (rowActivate)="editar.emit($event)"
    >
      <ng-container table-filters><ng-content select="[filtros-rapidos]"></ng-content></ng-container>
      <ng-container table-actions><ng-content select="[acoes]"></ng-content></ng-container>
      <ng-template cesCell="titular" let-row>
        <span class="ces-cell-strong">{{ row.placa ? placa(row.placa) : row.implemento }}</span>
        <span class="ces-cell-muted">{{ row.placa ? 'Veículo' : 'Implemento' }}</span>
      </ng-template>
      <ng-template cesCell="tipo" let-row>
        <span class="ces-cell-strong">{{ row.tipoRotulo }}</span>
        @if (row.descricao) {
          <span class="ces-cell-muted">{{ row.descricao }}</span>
        }
      </ng-template>
      <ng-template cesCell="dataVencimento" let-row>
        @if (row.dataVencimento) {
          <span class="ces-cell-strong">{{ row.dataVencimento | date: 'dd/MM/yyyy' }}</span>
          @if (row.diasParaVencer !== null && row.situacaoVencimento !== 'VIGENTE') {
            <span class="ces-cell-muted">{{ prazo(row.diasParaVencer) }}</span>
          }
        } @else {
          <span class="ces-cell-muted">Sem vencimento</span>
        }
      </ng-template>
      <ng-template cesCell="situacaoVencimento" let-row>
        <ces-status [tone]="tom[row.situacaoVencimento]">{{ rotulo[row.situacaoVencimento] }}</ces-status>
      </ng-template>
      <ng-template cesRowActions let-row>
        <span class="ces-table-actions">
          @if (row.possuiArquivo) {
            <ces-icon-button icon="pi pi-download" [label]="'Baixar ' + (row.arquivoNome ?? 'arquivo de ' + nomeDoc(row))" tooltip="Baixar arquivo" size="icon-sm"
              (pressed)="baixar(row)"></ces-icon-button>
          }
          @if (podeEditar()) {
            <ces-icon-button icon="pi pi-trash" [label]="'Excluir ' + nomeDoc(row)" tooltip="Excluir documento" size="icon-sm"
              (pressed)="excluir(row)"></ces-icon-button>
          }
        </span>
      </ng-template>
    </ces-data-table>
  `
})
export class FrotaDocumentosTabelaComponent {
  private readonly frota = inject(FrotaService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly notificacoes = inject(NotificationService);

  readonly documentos = input<readonly DocumentoFrota[]>([]);
  readonly estado = input<CesContentState>('ready');
  readonly titulo = input('Documentos');
  readonly descricao = input<string | undefined>();
  readonly erro = input<string | null>(null);
  /** Título do vazio quando a lista abre num recorte padrão (ex.: "Nada vencido nem a vencer"). */
  readonly tituloVazio = input<string | undefined>();
  readonly vazio = input('Cadastre CRLV, seguro, licenciamento e os demais documentos com vencimento.');
  readonly storageKey = input('frota-documentos');
  readonly mostrarTitular = input(false);
  readonly exportar = input(false);
  readonly podeEditar = input(false);
  /** `server` na tela de vencimentos (paginada no backend). */
  readonly modo = input<'client' | 'server'>('client');
  readonly total = input<number | undefined>();
  readonly filtrado = input(false);
  readonly exportarLinhas = input<((evento: CesLazyLoadEvent, limite: number) => Observable<readonly DocumentoFrota[]>) | undefined>();
  readonly limiteExportacao = input(10000);
  readonly pagina = model(0);
  readonly tamanho = model(25);
  readonly ordem = model<CesSortRule[]>([]);
  readonly busca = model('');
  readonly editar = output<DocumentoFrota>();
  readonly alterado = output<void>();
  readonly carregar = output<CesLazyLoadEvent>();
  readonly ajustarFiltros = output<void>();
  readonly limparFiltros = output<void>();

  protected readonly tom: Record<string, CesTone> = TOM_VENCIMENTO;
  protected readonly rotulo: Record<string, string> = ROTULO_VENCIMENTO;
  protected readonly prazo = descreverPrazo;
  protected readonly placa = formatarPlaca;
  protected readonly chave = (d: DocumentoFrota) => d.id;
  protected readonly nomeDoc = (d: DocumentoFrota) => `${d.tipoRotulo}${d.numero ? ' nº ' + d.numero : ''}`;

  protected readonly colunas: CesDataTableColumn<DocumentoFrota>[] = [
    { key: 'tipo', header: 'Documento', type: 'custom', priority: 1, sortField: 'tipo', value: (d) => d.tipoRotulo, display: (d) => `${d.tipoRotulo} ${d.descricao ?? ''}` },
    { key: 'numero', header: 'Número', sortField: 'numero', display: (d) => d.numero ?? '—' },
    { key: 'dataVencimento', header: 'Vencimento', type: 'custom', sortField: 'dataVencimento', value: (d) => d.dataVencimento ?? '9999-12-31', display: (d) => d.dataVencimento ?? '' },
    { key: 'situacaoVencimento', header: 'Situação', type: 'custom', priority: 1, value: (d) => d.diasParaVencer ?? 99999, display: (d) => ROTULO_VENCIMENTO[d.situacaoVencimento] }
  ];

  protected readonly colunasComTitular: CesDataTableColumn<DocumentoFrota>[] = [
    { key: 'titular', header: 'Pertence a', type: 'custom', priority: 1, value: (d) => d.placa ?? d.implemento, display: (d) => d.placa ?? d.implemento ?? '' },
    ...this.colunas
  ];

  protected baixar(documento: DocumentoFrota): void {
    this.frota.baixarArquivoDocumento(documento.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = documento.arquivoNome ?? 'documento';
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: (erro) => this.notificacoes.error(getErrorMessage(erro))
    });
  }

  protected async excluir(documento: DocumentoFrota): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: 'Excluir documento',
      message: `Excluir ${documento.tipoRotulo}${documento.numero ? ' nº ' + documento.numero : ''}? Ele deixa de aparecer nas listas e nos avisos de vencimento.`,
      variant: 'destructive'
    });
    if (!ok) return;
    this.frota.excluirDocumento(documento.id).subscribe({
      next: () => {
        this.notificacoes.success('Documento excluído.');
        this.alterado.emit();
      },
      error: (erro) => this.notificacoes.error(getErrorMessage(erro))
    });
  }
}
