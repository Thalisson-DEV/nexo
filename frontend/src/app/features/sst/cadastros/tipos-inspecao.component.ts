import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
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
  CesNumberInputComponent,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';

import { getErrorMessage } from '../../../core/models/shared.models';
import { TipoInspecaoResponse } from '../../../core/models/sst.models';
import { AuthService } from '../../../core/services/auth.service';
import { SstService } from '../../../core/services/sst.service';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

const SITUACOES: CesSelectOption<boolean | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Ativos', value: true },
  { label: 'Desativados', value: false }
];

/** Tipos de inspeção de SST (campo, veicular, alojamento) usados nas inspeções e nas metas. */
@Component({
  selector: 'app-sst-tipos-inspecao',
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
    CesNumberInputComponent,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="podeEscrever" (newRequested)="novo()">
      <ces-page-header
        title="Tipos de inspeção"
        icon="pi pi-list-check"
        [breadcrumb]="[{ label: 'SST' }, { label: 'Cadastros' }, { label: 'Tipos de Inspeção' }]"
        description="Tipos de inspeção usados nas inspeções e nas metas, na ordem de exibição."
        helpLabel="Ajuda sobre tipos de inspeção"
        (help)="ajuda.show()"
      >
        @if (podeEscrever) {
          <ces-button actions icon="pi pi-plus" label="Novo tipo" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel #painel storageKey="sst-tipos-inspecao" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Situação" [options]="situacoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Tipos de inspeção"
        mode="server"
        storageKey="sst-tipos-inspecao"
        exportFileName="tipos-inspecao"
        searchPlaceholder="Nome ou descrição"
        [rowActionLabel]="podeEscrever ? 'Editar' : undefined"
        rowActionIcon="pi pi-pencil"
        [emptyActionLabel]="podeEscrever ? 'Novo tipo' : undefined"
        noResultsTitle="Nenhum tipo de inspeção encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os tipos de inspeção' : 'Nenhum tipo de inspeção ativo'"
        [stateDescription]="resultado.error() ?? (lista.applied().ativo ? 'A lista mostra só os ativos; remova a situação nos filtros para ver os desativados.' : undefined)"
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
        <ng-template cesCell="nome" let-row>
          <span class="ces-cell-strong">{{ row.nome }}</span>
          <span class="ces-cell-muted">{{ row.descricao || '—' }}</span>
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          @if (podeEscrever) {
            <span class="ces-table-actions">
              <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.nome"
                size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            </span>
          }
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar tipo de inspeção' : 'Novo tipo de inspeção'"
      [description]="emEdicao()?.nome" [dirty]="alterado()" size="sm">
      <ces-form-section title="Dados do tipo" icon="pi pi-list-check">
        <ces-text-input label="Nome" [required]="true" [(value)]="nome" [maxlength]="120" placeholder="Ex.: Campo, Veicular, Alojamento"
          [error]="tentouSalvar() && !nome().trim() ? 'Informe o nome.' : undefined"></ces-text-input>
        <ces-textarea label="Descrição" [(value)]="descricao" [rows]="3" [maxlength]="500" [showOptional]="true"
          placeholder="Descreva o propósito ou escopo deste tipo"></ces-textarea>
        <ces-number-input label="Ordem de exibição" [(value)]="ordem" [min]="0" [useGrouping]="false" description="Menores números aparecem primeiro."></ces-number-input>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar tipo'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-ajuda-listagem #ajuda titulo="Tipos de inspeção" entidadePlural="tipos de inspeção" busca="nome ou descrição"
      rotuloNovo="Novo tipo" [podeCriar]="podeEscrever"></app-ajuda-listagem>
  `
})
export class TiposInspecaoComponent {
  private readonly sst = inject(SstService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  protected readonly podeEscrever = inject(AuthService).currentUser()?.permissoes?.includes('sst:catalogo:write') ?? false;

  protected readonly situacoes = SITUACOES;

  protected readonly lista = cesListState({
    storageKey: 'sst-tipos-inspecao',
    filters: { ativo: cesFilter.value<boolean | null>(true, { label: 'Status', param: 'status', options: () => SITUACOES }) },
    sort: [{ key: 'ordem', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<TipoInspecaoResponse>(
    (pedido) => this.sst.listarTiposInspecao({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.sst.listarTiposInspecao({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<TipoInspecaoResponse>[] = [
    { key: 'nome', header: 'Tipo de inspeção', type: 'custom', priority: 1, sortField: 'nome', value: (t) => t.nome, display: (t) => `${t.nome} ${t.descricao ?? ''}` },
    { key: 'ordem', header: 'Ordem', type: 'integer', sortField: 'ordem' },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, sortField: 'ativo', value: (t) => t.ativo, display: (t) => (t.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (c: TipoInspecaoResponse) => c.nome;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<TipoInspecaoResponse | null>(null);
  protected readonly nome = signal('');
  protected readonly descricao = signal('');
  protected readonly ordem = signal<number | null>(0);
  protected readonly salvando = signal(false);
  protected readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => {
    const c = this.emEdicao();
    return c
      ? this.nome() !== c.nome || this.descricao() !== (c.descricao ?? '') || this.ordem() !== (c.ordem ?? 0)
      : !!(this.nome() + this.descricao()).trim();
  });

  private filtro() {
    const { filters, query } = this.lista.request();
    return { ativo: filters.ativo, buscaGlobal: query || null };
  }

  protected novo(): void {
    if (!this.podeEscrever) return;
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(item: TipoInspecaoResponse): void {
    this.preencher(item);
    this.formularioAberto.set(true);
  }

  private preencher(c: TipoInspecaoResponse | null): void {
    this.emEdicao.set(c);
    this.nome.set(c?.nome ?? '');
    this.descricao.set(c?.descricao ?? '');
    // Novo tipo entra no fim da ordem.
    this.ordem.set(c ? (c.ordem ?? 0) : this.resultado.total() + 1);
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (this.salvando() || !this.nome().trim()) return;
    const atual = this.emEdicao();
    const request = { nome: this.nome().trim(), descricao: this.descricao().trim() || null, ordem: this.ordem() ?? 0 };
    this.salvando.set(true);
    (atual ? this.sst.atualizarTipoInspecao(atual.id, request) : this.sst.criarTipoInspecao(request)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Tipo de inspeção atualizado.' : 'Tipo de inspeção cadastrado.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(item: TipoInspecaoResponse): Promise<void> {
    const desativar = item.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar tipo de inspeção' : 'Ativar tipo de inspeção',
      message: desativar ? `${item.nome} sai das novas inspeções e metas. As existentes não mudam.` : `${item.nome} volta a aparecer nas seleções.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.sst.desativarTipoInspecao(item.id) : this.sst.ativarTipoInspecao(item.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Tipo de inspeção desativado.' : 'Tipo de inspeção ativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }
}
