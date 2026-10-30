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
  CesListShortcutsDirective,
  CesNumberInputComponent,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';

import { StatusObraResponse } from '../../../../core/models/obras.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { AuthService } from '../../../../core/services/auth.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { AuditDialogComponent } from '../../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../../shared/listagem/listagem';

/** Opções do filtro "Tipo de status". */
const OPCOES_CONCLUSAO = [
  { label: 'Todos os tipos', value: null },
  { label: 'Em andamento', value: false },
  { label: 'Concluída / finalizada', value: true }
];

@Component({
  selector: 'app-status-obras',
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
    CesListShortcutsDirective,
    CesNumberInputComponent,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AuditDialogComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="podeEscrever" (newRequested)="novo()">
      <ces-page-header
        title="Status de obras"
        icon="pi pi-flag"
        [breadcrumb]="[{ label: 'Obras' }, { label: 'Cadastros' }, { label: 'Status de Obras' }]"
        description="Etapas pelas quais uma obra passa, na ordem de exibição do painel, e quais encerram a obra."
        helpLabel="Ajuda sobre status de obras"
        (help)="ajuda.show()"
      >
        @if (podeEscrever) {
          <ces-button actions icon="pi pi-plus" label="Novo status" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel #painel storageKey="obras-status" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Tipo de status" [options]="conclusaoOpcoes" [(value)]="lista.filters.concluida"></ces-select>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Status de obras"
        mode="server"
        storageKey="obras-status"
        exportFileName="status-obras"
        searchPlaceholder="Nome do status"
        [rowActionLabel]="podeEscrever ? 'Editar' : undefined"
        rowActionIcon="pi pi-pencil"
        [emptyActionLabel]="podeEscrever ? 'Novo status' : undefined"
        noResultsTitle="Nenhum status encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os status' : 'Nenhum status cadastrado'"
        [stateDescription]="resultado.error() ?? 'Os status cadastrados aparecem aqui.'"
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
        <ng-template cesCell="concluida" let-row>
          <ces-status [tone]="row.concluida ? 'success' : 'info'" [icon]="row.concluida ? 'pi-check-circle' : 'pi-spinner'">
            {{ row.concluida ? 'Concluída / finalizada' : 'Em andamento' }}
          </ces-status>
        </ng-template>
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

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar status' : 'Novo status'"
      [description]="emEdicao()?.nome" [dirty]="alterado()" size="sm">
      <ces-form-section title="Dados do status" icon="pi pi-flag">
        <ces-text-input label="Nome" [required]="true" [(value)]="nome" [maxlength]="100" description="Gravado em letras maiúsculas." [error]="erro('nome')"></ces-text-input>
        <ces-number-input label="Ordem de exibição" [(value)]="ordem" [min]="0" [useGrouping]="false" description="Posição do status no painel de obras."></ces-number-input>
        <ces-boolean-control label="Este status conclui a obra" [(value)]="concluida"
          description="Obras neste status contam como concluídas/finalizadas."></ces-boolean-control>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar status'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-audit-dialog #auditoria></app-audit-dialog>
    <app-ajuda-listagem #ajuda titulo="Status de obras" entidadePlural="status" busca="nome do status"
      rotuloNovo="Novo agente" [podeCriar]="podeEscrever"></app-ajuda-listagem>
  `
})
export class StatusObrasComponent {
  private readonly servico = inject(ObrasService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');
  protected readonly podeEscrever = inject(AuthService).currentUser()?.permissoes?.includes('obras:cadastros:write') ?? false;

  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly conclusaoOpcoes = OPCOES_CONCLUSAO;
  protected readonly lista = cesListState({
    storageKey: 'obras-status',
    filters: {
      concluida: cesFilter.value<boolean | null>(null, { label: 'Tipo', param: 'tipo', options: () => OPCOES_CONCLUSAO }),
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'ordem', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<StatusObraResponse>(
    (pedido) => this.servico.listarStatus({ ...this.filtro(), ...(paginaPadrao(pedido) as { page: number; size: number; sort?: string }) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao(
    (pagina) => this.servico.listarStatus({ ...this.filtro(), ...(pagina as { page: number; size: number; sort?: string }) }),
    () => this.lista.request()
  );

  protected readonly colunas: CesDataTableColumn<StatusObraResponse>[] = [
    { key: 'ordem', header: 'Ordem', type: 'integer', sortField: 'ordem', width: '6rem' },
    { key: 'nome', header: 'Nome', priority: 1, sortField: 'nome' },
    { key: 'concluida', header: 'Tipo de status', type: 'custom', sortField: 'concluida', value: (s) => s.concluida, display: (s) => (s.concluida ? 'Concluída / finalizada' : 'Em andamento') },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, sortField: 'ativo', value: (s) => s.ativo, display: (s) => (s.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (a: StatusObraResponse) => a.nome;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<StatusObraResponse | null>(null);
  protected readonly nome = signal('');
  protected readonly ordem = signal<number | null>(0);
  protected readonly concluida = signal(false);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => {
    const s = this.emEdicao();
    return s ? this.nome() !== s.nome || this.ordem() !== s.ordem || this.concluida() !== s.concluida : !!this.nome().trim();
  });
  private readonly erros = computed<Record<string, string>>(() => (this.nome().trim() ? ({} as Record<string, string>) : { nome: 'Informe o nome do status.' }));

  private filtro() {
    const { filters, query } = this.lista.request();
    return {
      concluida: filters.concluida ?? undefined,
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

  protected editar(agente: StatusObraResponse): void {
    this.preencher(agente);
    this.formularioAberto.set(true);
  }

  private preencher(s: StatusObraResponse | null): void {
    this.emEdicao.set(s);
    this.nome.set(s?.nome ?? '');
    this.ordem.set(s?.ordem ?? 0);
    this.concluida.set(s?.concluida ?? false);
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const request = { nome: this.nome().trim().toUpperCase(), ordem: this.ordem() ?? 0, concluida: this.concluida() };
    this.salvando.set(true);
    (atual ? this.servico.atualizarStatus(atual.id, request) : this.servico.criarStatus(request)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Status atualizado.' : 'Status cadastrado.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async desativar(agente: StatusObraResponse): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: 'Desativar status',
      message: `${agente.nome} deixa de aparecer na seleção de status das obras. As obras que já estão nele não mudam.`,
      confirmLabel: 'Desativar',
      variant: 'destructive'
    });
    if (!ok) return;
    this.servico.desativarStatus(agente.id).subscribe({
      next: () => {
        this.notificacoes.success('Status desativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(agente: StatusObraResponse): void {
    this.auditoria().show(agente.id, 'Status de Obra', { nome: 'Nome do status', ordem: 'Ordem de exibição', concluida: 'Obra concluída' });
  }
}
