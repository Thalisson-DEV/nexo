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

import { AcaoImportacaoDTO, AcaoResponse } from '../../../core/models/contrato.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ContratoService } from '../../../core/services/contrato.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

@Component({
  selector: 'app-acoes',
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
        title="Ações"
        icon="pi pi-bolt"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Cadastros' }, { label: 'Ações' }]"
        description="Catálogo de serviços (ações) executados em campo. Os valores por contrato ficam em Valores de ações."
        helpLabel="Ajuda sobre ações"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacao.aberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Nova ação" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="acoes" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Ações"
        mode="server"
        storageKey="acoes"
        exportFileName="acoes"
        searchPlaceholder="Código ou nome da ação"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Nova ação"
        noResultsTitle="Nenhuma ação encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as ações' : 'Nenhuma ação cadastrada'"
        [stateDescription]="resultado.error() ?? 'Cadastre uma ação ou importe uma planilha.'"
        [filtered]="lista.filtered()"
        [exportRows]="exportar"
        [exportLimit]="limiteExportacao"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="novo()"
        (rowActivate)="editar($event)"
      >
        <ng-template cesCell="codigo" let-row><span class="ces-cell-strong">{{ row.codigo }}</span></ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativa' : 'Inativa' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.codigo" size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.codigo" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar ação' : 'Nova ação'" [description]="emEdicao()?.codigo"
      [dirty]="alterado()" size="md">
      <ces-form-section title="Ação" icon="pi pi-bolt">
        <div class="ces-form-grid">
          <ces-text-input label="Código" [required]="true" [value]="codigo()" (valueChange)="codigo.set($event.toUpperCase())" [maxlength]="20" placeholder="Ex.: SERV01" [error]="erro('codigo')"></ces-text-input>
          <ces-text-input label="Nome" [required]="true" [(value)]="nome" [maxlength]="255" [error]="erro('nome')"></ces-text-input>
        </div>
        <ces-boolean-control [label]="emEdicao() ? 'Ação ativa' : 'Cadastrar já ativa'" kind="toggle" [(value)]="ativo"></ces-boolean-control>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar ação'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer [(visible)]="importacao.aberta" title="Importar ações" description="Planilha CSV com código e nome de cada ação."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao.linhas()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="codigo" let-row>
            @if (row.editing) { <ces-text-input label="Código" [labelVisible]="false" [(value)]="row.codigo"></ces-text-input> } @else { {{ row.codigo }} }
          </ng-template>
          <ng-template cesCell="nome" let-row>
            @if (row.editing) { <ces-text-input label="Nome" [labelVisible]="false" [(value)]="row.nome"></ces-text-input> } @else { {{ row.nome }} }
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
    <app-ajuda-listagem #ajuda titulo="Ações" entidadePlural="ações" busca="código ou nome" rotuloNovo="Nova ação" [importacao]="true"></app-ajuda-listagem>
  `
})
export class AcoesComponent {
  private readonly contratos = inject(ContratoService);
  private readonly importacaoService = inject(ImportacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly statusOpcoes = OPCOES_STATUS;

  protected readonly lista = cesListState({
    storageKey: 'acoes',
    filters: {
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'codigo', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<AcaoResponse>(
    (pedido) => this.contratos.listarAcoes({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.contratos.listarAcoes({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<AcaoResponse>[] = [
    { key: 'codigo', header: 'Código', type: 'custom', priority: 1, sortField: 'codigo', width: '10rem', value: (a) => a.codigo, display: (a) => a.codigo },
    { key: 'nome', header: 'Nome', priority: 1, sortField: 'nome' },
    { key: 'ativo', header: 'Status', type: 'custom', sortField: 'ativo', value: (a) => a.ativo, display: (a) => (a.ativo ? 'Ativa' : 'Inativa') }
  ];
  protected readonly rotulo = (a: AcaoResponse) => a.codigo;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<AcaoResponse | null>(null);
  protected readonly codigo = signal('');
  protected readonly nome = signal('');
  protected readonly ativo = signal(true);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => {
    const a = this.emEdicao();
    if (!a) return !!(this.codigo() + this.nome()).trim() || !this.ativo();
    return this.codigo() !== a.codigo || this.nome() !== a.nome || this.ativo() !== a.ativo;
  });
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.codigo().trim()) e['codigo'] = 'Informe o código.';
    if (!this.nome().trim()) e['nome'] = 'Informe o nome.';
    return e;
  });

  protected readonly importacao = importacaoListagem<AcaoImportacaoDTO>({
    enviar: (arquivo) => this.contratos.importarAcoes(arquivo),
    confirmar: (acoes) => this.contratos.confirmarImportacaoAcoes({ acoes }),
    modelo: () => this.importacaoService.baixarModeloCsv('/v1/contratos/acoes/importar/modelo', 'modelo-acoes.csv'),
    aoProcessar: () => this.lista.reload()
  });
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = CHAVE_LINHA_IMPORTACAO;
  protected readonly colunasImportacao: CesDataTableColumn<AcaoImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'codigo', header: 'Código', type: 'custom', priority: 1, value: (r) => r.codigo },
    { key: 'nome', header: 'Nome', type: 'custom', value: (r) => r.nome },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  private filtro() {
    const { filters, query } = this.lista.request();
    return { ativo: filters.ativo, buscaGlobal: query || undefined };
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(a: AcaoResponse): void {
    this.preencher(a);
    this.formularioAberto.set(true);
  }

  private preencher(a: AcaoResponse | null): void {
    this.emEdicao.set(a);
    this.codigo.set(a?.codigo ?? '');
    this.nome.set(a?.nome ?? '');
    this.ativo.set(a?.ativo ?? true);
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const ativo = this.ativo();
    const request = { codigo: this.codigo().trim(), nome: this.nome().trim() };
    this.salvando.set(true);
    const alternar = (id: string, era: boolean): Observable<unknown> =>
      era === ativo ? of(null) : ativo ? this.contratos.ativarAcao(id) : this.contratos.desativarAcao(id);
    const salvar$: Observable<unknown> = atual
      ? this.contratos.atualizarAcao(atual.id, request).pipe(switchMap(() => alternar(atual.id, atual.ativo)))
      : this.contratos.criarAcao(request).pipe(switchMap((criada) => (criada ? alternar(criada.id, true) : of(null))));
    salvar$.subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Ação atualizada.' : 'Ação cadastrada.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(a: AcaoResponse): Promise<void> {
    const desativar = a.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar ação' : 'Ativar ação',
      message: desativar ? `${a.codigo} deixa de aparecer nas seleções. O histórico é mantido.` : `${a.codigo} volta a aparecer nas seleções.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.contratos.desativarAcao(a.id) : this.contratos.ativarAcao(a.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Ação desativada.' : 'Ação ativada.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(a: AcaoResponse): void {
    this.auditoria().show(a.id, a.codigo, { codigo: 'Código', nome: 'Nome', ativo: 'Status' });
  }
}
