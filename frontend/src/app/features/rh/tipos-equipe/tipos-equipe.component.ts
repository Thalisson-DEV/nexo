import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import {
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCellDirective,
  CesRowActionsDirective,
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
  CesSelectComponent,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesFilter,
  cesImportStatus,
  cesListState
} from '@ces/ui';
import { Observable } from 'rxjs';

import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { RhService } from '../../../core/services/rh.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { TipoEquipeImportacaoDTO, TipoEquipeResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

@Component({
  selector: 'app-tipos-equipe',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesBooleanControlComponent,
    CesButtonComponent,
    CesCellDirective,
    CesRowActionsDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesImportDrawerComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
      AuditDialogComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novo()">
      <ces-page-header
        title="Tipos de equipe"
        icon="pi pi-users"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Cadastros' }, { label: 'Tipos de Equipe' }]"
        description="Classificação das equipes (ex.: linha viva, construção, emergência)."
        helpLabel="Ajuda sobre tipos de equipe"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacao.aberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Novo tipo" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="rh-tipos-equipe" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Tipos de equipe"
        mode="server"
        storageKey="rh-tipos-equipe"
        exportFileName="tipos-equipe"
        searchPlaceholder="Nome do tipo"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo tipo"
        noResultsTitle="Nenhum tipo de equipe encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os tipos de equipe' : 'Nenhum tipo de equipe cadastrado'"
        [stateDescription]="resultado.error() ?? 'Cadastre um tipo ou importe uma planilha.'"
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
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.nome"
              size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.nome" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar tipo de equipe' : 'Novo tipo de equipe'"
      [description]="emEdicao()?.nome" [dirty]="alterado()" size="sm">
      <ces-form-section title="Dados do tipo" icon="pi pi-users">
        <ces-text-input label="Nome" [required]="true" [(value)]="nome" [maxlength]="50" placeholder="Ex.: EQUIPE LINHA VIVA" description="Gravado em letras maiúsculas." [error]="erro('nome')"></ces-text-input>
        <ces-boolean-control label="Tipo ativo" kind="toggle" [(value)]="ativo"></ces-boolean-control>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar tipo'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer [(visible)]="importacao.aberta" title="Importar tipos de equipe" description="Planilha CSV com o nome de cada tipo de equipe."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao.linhas()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="nome" let-row>
            @if (row.editing) {
              <ces-text-input label="Nome" [labelVisible]="false" [(value)]="row.nome"></ces-text-input>
            } @else {
              {{ row.nome }}
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
    <app-ajuda-listagem #ajuda titulo="Tipos de equipe" entidadePlural="tipos de equipe" busca="nome do tipo"
      rotuloNovo="Novo tipo" [importacao]="true" video="https://www.youtube.com/embed/ppE-efK-jcw"></app-ajuda-listagem>
  `
})
export class TiposEquipeComponent {
  private readonly rh = inject(RhService);
  private readonly importacaoService = inject(ImportacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly lista = cesListState({
    storageKey: 'rh-tipos-equipe',
    filters: { ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS }) },
    sort: [{ key: 'nome', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<TipoEquipeResponse>(
    (pedido) => this.rh.listarTiposEquipe({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.rh.listarTiposEquipe({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<TipoEquipeResponse>[] = [
    { key: 'nome', header: 'Nome', priority: 1, sortField: 'nome' },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, sortField: 'ativo', value: (f) => f.ativo, display: (f) => (f.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (f: TipoEquipeResponse) => f.nome;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<TipoEquipeResponse | null>(null);
  protected readonly nome = signal('');
  protected readonly ativo = signal(true);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => {
    const atual = this.emEdicao();
    return atual
      ? this.nome() !== atual.nome || this.ativo() !== atual.ativo
      : !!this.nome().trim();
  });

  protected readonly importacao = importacaoListagem<TipoEquipeImportacaoDTO>({
    enviar: (arquivo) => this.rh.importarTiposEquipe(arquivo),
    confirmar: (tiposEquipe) => this.rh.confirmarImportacaoTiposEquipe({ tiposEquipe }),
    modelo: () => this.importacaoService.baixarModeloCsv('/v1/tipos-equipe/importar/modelo', 'modelo-tipos-equipe.csv'),
    aoProcessar: () => this.lista.reload()
  });
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = CHAVE_LINHA_IMPORTACAO;
  protected readonly colunasImportacao: CesDataTableColumn<TipoEquipeImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'nome', header: 'Nome', type: 'custom', priority: 1, value: (r) => r.nome },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  private filtro() {
    const { filters, query } = this.lista.request();
    return { ...filters, buscaGlobal: query || undefined };
  }

  protected erro(campo: 'nome'): string | undefined {
    return this.tentouSalvar() && campo === 'nome' && !this.nome().trim() ? 'Informe o nome do tipo.' : undefined;
  }

  protected novo(): void {
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(funcao: TipoEquipeResponse): void {
    this.preencher(funcao);
    this.formularioAberto.set(true);
  }

  private preencher(funcao: TipoEquipeResponse | null): void {
    this.emEdicao.set(funcao);
    this.nome.set(funcao?.nome ?? '');
    this.ativo.set(funcao?.ativo ?? true);
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (!this.nome().trim()) return;
    const atual = this.emEdicao();
    // O cadastro de tipo de equipe já recebe o status.
    const request = { nome: this.nome().trim().toUpperCase(), ativo: this.ativo() };
    this.salvando.set(true);
    const salvar$: Observable<unknown> = atual ? this.rh.atualizarTipoEquipe(atual.id, request) : this.rh.criarTipoEquipe(request);
    salvar$.subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Tipo de equipe atualizado.' : 'Tipo de equipe cadastrado.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(funcao: TipoEquipeResponse): Promise<void> {
    const desativar = funcao.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar tipo de equipe' : 'Ativar tipo de equipe',
      message: desativar ? `${funcao.nome} deixa de aparecer nas seleções. O histórico é mantido.` : `${funcao.nome} volta a aparecer nas seleções.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.rh.desativarTipoEquipe(funcao.id) : this.rh.ativarTipoEquipe(funcao.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Tipo de equipe desativado.' : 'Tipo de equipe ativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(funcao: TipoEquipeResponse): void {
    this.auditoria().show(funcao.id, funcao.nome, { nome: 'Nome do tipo', ativo: 'Status (ativo)', dataDesativacao: 'Data de desativação' });
  }
}
