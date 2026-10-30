import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import {
  CesButtonComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFilterPanelComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';

import { TipoAnexoObraResponse } from '../../../../core/models/obras.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { AuthService } from '../../../../core/services/auth.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { AuditDialogComponent } from '../../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../../shared/listagem/listagem';

@Component({
  selector: 'app-tipos-anexos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent,
    AuditDialogComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="podeEscrever" (newRequested)="novo()">
      <ces-page-header
        title="Tipos de anexo"
        icon="pi pi-paperclip"
        [breadcrumb]="[{ label: 'Obras' }, { label: 'Cadastros' }, { label: 'Tipos de Anexos' }]"
        description="Tipos de documento anexados às obras (projeto, ART, licença) e a finalidade de cada um."
        helpLabel="Ajuda sobre tipos de anexo"
        (help)="ajuda.show()"
      >
        @if (podeEscrever) {
          <ces-button actions icon="pi pi-plus" label="Novo tipo" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel #painel storageKey="obras-tipos-anexos" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Tipos de anexo"
        mode="server"
        storageKey="obras-tipos-anexos"
        exportFileName="tipos-anexos-obra"
        searchPlaceholder="Nome do tipo"
        [rowActionLabel]="podeEscrever ? 'Editar' : undefined"
        rowActionIcon="pi pi-pencil"
        [emptyActionLabel]="podeEscrever ? 'Novo tipo' : undefined"
        noResultsTitle="Nenhum tipo encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os tipos de anexo' : 'Nenhum tipo de anexo cadastrado'"
        [stateDescription]="resultado.error() ?? 'Os tipos cadastrados aparecem aqui.'"
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
            @if (podeEscrever && row.ativo) {
              <ces-icon-button icon="pi pi-ban" [label]="'Desativar ' + row.nome" size="icon-sm" (pressed)="desativar(row)"></ces-icon-button>
            }
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.nome" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar tipo de anexo' : 'Novo tipo de anexo'"
      [description]="emEdicao()?.nome" [dirty]="alterado()" size="sm">
      <ces-form-section title="Dados do tipo" icon="pi pi-paperclip">
        <ces-text-input label="Nome" [required]="true" [(value)]="nome" [maxlength]="100" description="Gravado em letras maiúsculas." [error]="erro('nome')"></ces-text-input>
        <ces-textarea label="Descrição / finalidade" [(value)]="descricao" [rows]="3" [maxlength]="300" [showOptional]="true"></ces-textarea>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar tipo'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-audit-dialog #auditoria></app-audit-dialog>
    <app-ajuda-listagem #ajuda titulo="Tipos de anexo" entidadePlural="tipos de anexo" busca="nome do tipo"
      rotuloNovo="Novo agente" [podeCriar]="podeEscrever"></app-ajuda-listagem>
  `
})
export class TiposAnexosComponent {
  private readonly servico = inject(ObrasService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');
  protected readonly podeEscrever = inject(AuthService).currentUser()?.permissoes?.includes('obras:cadastros:write') ?? false;

  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly lista = cesListState({
    storageKey: 'obras-tipos-anexos',
    filters: {
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'nome', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<TipoAnexoObraResponse>(
    (pedido) => this.servico.listarTiposAnexos({ ...this.filtro(), ...(paginaPadrao(pedido) as { page: number; size: number; sort?: string }) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao(
    (pagina) => this.servico.listarTiposAnexos({ ...this.filtro(), ...(pagina as { page: number; size: number; sort?: string }) }),
    () => this.lista.request()
  );

  protected readonly colunas: CesDataTableColumn<TipoAnexoObraResponse>[] = [
    { key: 'nome', header: 'Nome', priority: 1, sortField: 'nome' },
    { key: 'descricao', header: 'Descrição / finalidade', sortField: 'descricao', display: (t) => t.descricao || '—' },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, sortField: 'ativo', value: (t) => t.ativo, display: (t) => (t.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (a: TipoAnexoObraResponse) => a.nome;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<TipoAnexoObraResponse | null>(null);
  protected readonly nome = signal('');
  protected readonly descricao = signal('');
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => {
    const t = this.emEdicao();
    return t ? this.nome() !== t.nome || this.descricao() !== (t.descricao ?? '') : !!(this.nome() + this.descricao()).trim();
  });
  private readonly erros = computed<Record<string, string>>(() => (this.nome().trim() ? ({} as Record<string, string>) : { nome: 'Informe o nome do tipo.' }));

  private filtro() {
    const { filters, query } = this.lista.request();
    return {
      ativo: filters.ativo ?? undefined,
      buscaGlobal: query || undefined
    };
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    if (!this.podeEscrever) return;
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(agente: TipoAnexoObraResponse): void {
    this.preencher(agente);
    this.formularioAberto.set(true);
  }

  private preencher(t: TipoAnexoObraResponse | null): void {
    this.emEdicao.set(t);
    this.nome.set(t?.nome ?? '');
    this.descricao.set(t?.descricao ?? '');
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const request = { nome: this.nome().trim().toUpperCase(), descricao: this.descricao().trim() || undefined };
    this.salvando.set(true);
    (atual ? this.servico.atualizarTipoAnexo(atual.id, request) : this.servico.criarTipoAnexo(request)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Tipo de anexo atualizado.' : 'Tipo de anexo cadastrado.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async desativar(agente: TipoAnexoObraResponse): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: 'Desativar tipo de anexo',
      message: `${agente.nome} deixa de aparecer na seleção de anexos. Os anexos já enviados continuam.`,
      confirmLabel: 'Desativar',
      variant: 'destructive'
    });
    if (!ok) return;
    this.servico.desativarTipoAnexo(agente.id).subscribe({
      next: () => {
        this.notificacoes.success('Tipo de anexo desativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(agente: TipoAnexoObraResponse): void {
    this.auditoria().show(agente.id, 'Tipo de Anexo de Obra', { nome: 'Nome do tipo', descricao: 'Descrição / finalidade' });
  }
}
