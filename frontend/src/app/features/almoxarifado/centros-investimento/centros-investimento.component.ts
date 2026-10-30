import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import {
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFilterPanelComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesImportDrawerComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesFilter,
  cesImportStatus,
  cesListState
} from '@ces/ui';
import { Observable, of, switchMap } from 'rxjs';

import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { CentroInvestimentoResponse, CentroInvestimentoImportacaoDTO } from '../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { OPCOES_STATUS, LIMITE_EXPORTACAO, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

@Component({
  selector: 'app-centros-investimento',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesBooleanControlComponent,
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesImportDrawerComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AuditDialogComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novo()">
      <ces-page-header
        title="Centros de investimento"
        icon="pi pi-chart-line"
        [breadcrumb]="[{ label: 'Almoxarifado' }, { label: 'Centros de Investimento' }]"
        description="Códigos de centro de investimento usados na classificação do material."
        helpLabel="Ajuda sobre centros de investimento"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacao.aberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Novo centro" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="almox-centros-investimento" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Centros de investimento"
        mode="server"
        storageKey="almox-centros-investimento"
        exportFileName="centros-investimento"
        searchPlaceholder="Código do centro"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo centro"
        noResultsTitle="Nenhum centro de investimento encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os centros de investimento' : 'Nenhum centro de investimento cadastrado'"
        [stateDescription]="resultado.error() ?? 'Cadastre um centro ou importe uma planilha.'"
        [filtered]="lista.filtered()"
        [totalRecords]="resultado.total()"
        [exportRows]="exportar"
        [exportLimit]="limiteExportacao"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        (lazyLoad)="lista.load($event)"
        [(query)]="lista.query"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="novo()"
        (rowActivate)="editar($event)"
      >
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.codigo"
              size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.codigo" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar centro de investimento' : 'Novo centro de investimento'"
      [description]="emEdicao()?.codigo" [dirty]="alterado()" size="sm">
      <ces-form-section title="Dados do centro de investimento" icon="pi pi-chart-line">
        <ces-text-input label="Código" [required]="true" [(value)]="principal" [maxlength]="10" placeholder="Ex.: 670" [error]="erro('principal')"></ces-text-input>
        <ces-boolean-control label="Centro de investimento ativo" kind="toggle" [(value)]="ativo"></ces-boolean-control>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar centro de investimento'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer [(visible)]="importacao.aberta" title="Importar centros de investimento" description="Planilha CSV com o código de cada centro de investimento."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao.linhas()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="codigo" let-row>
            @if (row.editing) {
              <ces-text-input label="Código" [labelVisible]="false" [value]="row.codigo ?? ''" (valueChange)="row.codigo = $event"></ces-text-input>
            } @else {
              {{ row.codigo || '—' }}
            }
          </ng-template>
          <ng-template cesCell="status" let-row>
            <ces-status [tone]="situacao(row.status).tone" [icon]="situacao(row.status).icon">{{ situacao(row.status).label }}</ces-status>
            @if (row.mensagemErro) {
              <span class="ces-cell-muted">{{ row.mensagemErro }}</span>
            }
          </ng-template>
          <ng-template cesRowActions let-row>
            <span class="ces-table-actions">
              @if (row.editing) {
                <ces-icon-button icon="pi pi-check" [label]="'Concluir correção da linha ' + row.linha" size="icon-sm" (pressed)="importacao.concluirCorrecao(row)"></ces-icon-button>
              } @else {
                <ces-icon-button icon="pi pi-pencil" [label]="'Corrigir linha ' + row.linha" size="icon-sm" (pressed)="row.editing = true"></ces-icon-button>
                <ces-icon-button icon="pi pi-trash" [label]="'Remover linha ' + row.linha" size="icon-sm" (pressed)="importacao.remover(row)"></ces-icon-button>
              }
            </span>
          </ng-template>
        </ces-data-table>
      }
    </ces-import-drawer>

    <app-audit-dialog #auditoria></app-audit-dialog>
    <app-ajuda-listagem #ajuda titulo="Centros de investimento" entidadePlural="centros de investimento" artigoPlural="os" busca="código do centro"
      rotuloNovo="Novo centro" [importacao]="true"
      video="https://www.youtube.com/embed/V38kro0yePk"></app-ajuda-listagem>
  `
})
export class CentrosInvestimentoComponent {
  private readonly servico = inject(AlmoxarifadoService);
  private readonly importacaoService = inject(ImportacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly lista = cesListState({
    storageKey: 'almox-centros-investimento',
    filters: { ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS }) },
    sort: [{ key: 'codigo', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<CentroInvestimentoResponse>(
    (pedido) => this.servico.listarCentrosInvestimento({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.servico.listarCentrosInvestimento({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<CentroInvestimentoResponse>[] = [
    { key: 'codigo', header: 'Código', priority: 1, sortField: 'codigo' },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, sortField: 'ativo', value: (r) => r.ativo, display: (r) => (r.ativo ? 'Ativo' : 'Inativo') },
  ];
  protected readonly rotulo = (r: CentroInvestimentoResponse) => r.codigo;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<CentroInvestimentoResponse | null>(null);
  protected readonly principal = signal('');
  protected readonly ativo = signal(true);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => {
    const atual = this.emEdicao();
    return atual ? this.principal() !== atual.codigo || this.ativo() !== atual.ativo : !!this.principal().trim();
  });
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.principal().trim()) e['principal'] = 'Informe o código.';
    return e;
  });

  protected readonly importacao = importacaoListagem<CentroInvestimentoImportacaoDTO>({
    enviar: (arquivo) => this.servico.importarCentrosInvestimento(arquivo),
    confirmar: (registros) => this.servico.confirmarImportacaoCentrosInvestimento({ centros: registros }),
    modelo: () => this.importacaoService.baixarModeloCsv('/v1/centros-investimento/importar/modelo', 'modelo-centros-investimento.csv'),
    aoProcessar: () => this.lista.reload()
  });
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = CHAVE_LINHA_IMPORTACAO;
  protected readonly colunasImportacao: CesDataTableColumn<CentroInvestimentoImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'codigo', header: 'Código', type: 'custom', priority: 1, value: (r) => r.codigo },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  private filtro() {
    const { filters, query } = this.lista.request();
    return { ...filters, buscaGlobal: query || undefined };
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(registro: CentroInvestimentoResponse): void {
    this.preencher(registro);
    this.formularioAberto.set(true);
  }

  private preencher(registro: CentroInvestimentoResponse | null): void {
    this.emEdicao.set(registro);
    this.principal.set(registro?.codigo ?? '');
    this.ativo.set(registro?.ativo ?? true);
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const request = { codigo: this.principal().trim() };
    this.salvando.set(true);
    const ativo = this.ativo();
    // O status é alterado por ativar/desativar depois de gravar os dados.
    const salvar$: Observable<unknown> = atual
      ? this.servico.atualizarCentroInvestimento(atual.id, request).pipe(
          switchMap(() => (atual.ativo === ativo ? of(null) : ativo ? this.servico.ativarCentroInvestimento(atual.id) : this.servico.desativarCentroInvestimento(atual.id)))
        )
      : this.servico.criarCentroInvestimento(request).pipe(switchMap((criado) => (criado && !ativo ? this.servico.desativarCentroInvestimento(criado.id) : of(null))));
    salvar$.subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Centro de investimento atualizado.' : 'Centro de investimento cadastrado.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(registro: CentroInvestimentoResponse): Promise<void> {
    const desativar = registro.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar centro de investimento' : 'Ativar centro de investimento',
      message: desativar ? `${registro.codigo} deixa de aparecer nas seleções. O histórico é mantido.` : `${registro.codigo} volta a aparecer nas seleções.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.servico.desativarCentroInvestimento(registro.id) : this.servico.ativarCentroInvestimento(registro.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Centro de investimento desativado.' : 'Centro de investimento ativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(registro: CentroInvestimentoResponse): void {
    this.auditoria().show(registro.id, registro.codigo, { codigo: 'Código', ativo: 'Status' });
  }
}
