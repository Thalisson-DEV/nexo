import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import {
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
  CesListShortcutsDirective,
  CesNumberInputComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';

import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { RhDescontosService } from '../../../core/services/rh-descontos.service';
import { MotivoDescontoResponse } from '../../../core/models/desconto.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

@Component({
  selector: 'app-motivos-desconto',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCellDirective,
    CesRowActionsDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesNumberInputComponent,
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
        title="Motivos de desconto"
        icon="pi pi-tags"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Recursos Humanos' }, { label: 'Motivos de Desconto' }]"
        description="Motivos usados no lançamento de descontos e o teto de parcelas de cada um."
        helpLabel="Ajuda sobre motivos de desconto"
        (help)="ajuda.show()"
      >
        <ces-button actions icon="pi pi-plus" label="Novo motivo" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="rh-motivos-desconto" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Motivos de desconto"
        mode="server"
        storageKey="rh-motivos-desconto"
        exportFileName="motivos-desconto"
        searchPlaceholder="Descrição do motivo"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo motivo"
        noResultsTitle="Nenhum motivo encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os motivos' : 'Nenhum motivo cadastrado'"
        [stateDescription]="resultado.error() ?? 'Cadastre os motivos que podem ser usados nos descontos.'"
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
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.descricao"
              size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.descricao" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar motivo' : 'Novo motivo'"
      [description]="emEdicao()?.descricao" [dirty]="alterado()" size="sm">
      <ces-form-section title="Dados do motivo" icon="pi pi-tags">
        <ces-text-input label="Descrição" [required]="true" [(value)]="descricao" [maxlength]="120" placeholder="Ex.: Mau uso" [error]="erro('descricao')"></ces-text-input>
        <ces-number-input label="Teto de parcelas" [required]="true" [(value)]="tetoParcelas" [min]="1" [useGrouping]="false"
          description="Número máximo de parcelas permitido para descontos com este motivo." [error]="erro('teto')"></ces-number-input>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar motivo'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-audit-dialog #auditoria></app-audit-dialog>
    <app-ajuda-listagem #ajuda titulo="Motivos de desconto" entidadePlural="motivos" busca="descrição do motivo" rotuloNovo="Novo motivo"></app-ajuda-listagem>
  `
})
export class MotivosDescontoComponent {
  private readonly service = inject(RhDescontosService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly lista = cesListState({
    storageKey: 'rh-motivos-desconto',
    filters: { ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS }) },
    sort: [{ key: 'descricao', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<MotivoDescontoResponse>(
    (pedido) => this.service.listarMotivos({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.service.listarMotivos({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<MotivoDescontoResponse>[] = [
    { key: 'descricao', header: 'Descrição', priority: 1, sortField: 'descricao' },
    { key: 'tetoParcelas', header: 'Teto de parcelas', type: 'integer', sortField: 'tetoParcelas' },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, sortField: 'ativo', value: (m) => m.ativo, display: (m) => (m.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (m: MotivoDescontoResponse) => m.descricao;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<MotivoDescontoResponse | null>(null);
  protected readonly descricao = signal('');
  protected readonly tetoParcelas = signal<number | null>(1);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => {
    const atual = this.emEdicao();
    return atual ? this.descricao() !== atual.descricao || this.tetoParcelas() !== atual.tetoParcelas : !!this.descricao().trim();
  });
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.descricao().trim()) e['descricao'] = 'Informe a descrição.';
    if (!this.tetoParcelas() || this.tetoParcelas()! < 1) e['teto'] = 'O teto é de pelo menos 1 parcela.';
    return e;
  });

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

  protected editar(motivo: MotivoDescontoResponse): void {
    this.preencher(motivo);
    this.formularioAberto.set(true);
  }

  private preencher(motivo: MotivoDescontoResponse | null): void {
    this.emEdicao.set(motivo);
    this.descricao.set(motivo?.descricao ?? '');
    this.tetoParcelas.set(motivo?.tetoParcelas ?? 1);
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const request = { descricao: this.descricao().trim(), tetoParcelas: this.tetoParcelas()! };
    this.salvando.set(true);
    (atual ? this.service.atualizarMotivo(atual.id, request) : this.service.criarMotivo(request)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Motivo atualizado.' : 'Motivo cadastrado.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(motivo: MotivoDescontoResponse): Promise<void> {
    const desativar = motivo.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar motivo' : 'Ativar motivo',
      message: desativar ? `"${motivo.descricao}" deixa de aparecer no lançamento de descontos.` : `"${motivo.descricao}" volta a aparecer no lançamento de descontos.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.service.desativarMotivo(motivo.id) : this.service.ativarMotivo(motivo.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Motivo desativado.' : 'Motivo ativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(motivo: MotivoDescontoResponse): void {
    this.auditoria().show(motivo.id, 'MotivoDesconto');
  }
}
