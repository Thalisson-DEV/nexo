import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCalloutComponent,
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
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';

import { FormularioResumo, TipoSolicitacao } from '../../../core/models/financeiro.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

const TIPOS: CesSelectOption<TipoSolicitacao>[] = [
  { label: 'Compra', value: 'COMPRA' },
  { label: 'Pagamento', value: 'PAGAMENTO' }
];
const SITUACOES: CesSelectOption<boolean | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Ativos', value: true },
  { label: 'Inativos', value: false }
];
const CODIGO = /^[A-Z][A-Z0-9_]*$/;

@Component({
  selector: 'app-formularios-financeiros',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesBooleanControlComponent,
    CesButtonComponent,
    CesCalloutComponent,
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
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="abrir()">
      <ces-page-header
        title="Formulários financeiros"
        icon="pi pi-file-edit"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Financeiro' }, { label: 'Formulários' }]"
        description="Modelos de formulário das solicitações de compra e de pagamento. Clique em um formulário para abrir o construtor."
        helpLabel="Ajuda sobre formulários financeiros"
        (help)="ajuda.show()"
      >
        <ces-button actions icon="pi pi-plus" label="Novo formulário" [keyShortcut]="atalhos.newShortcut()" (pressed)="abrir()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="financeiro-formularios" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Tipo" [options]="tipos" [(value)]="lista.filters.tipo" [clearable]="true" placeholder="Todos"></ces-select>
          <ces-select label="Status" [options]="situacoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Formulários"
        mode="server"
        storageKey="financeiro-formularios"
        exportFileName="formularios-financeiros"
        searchPlaceholder="Código, título ou descrição"
        rowActionLabel="Abrir construtor"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo formulário"
        noResultsTitle="Nenhum formulário encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os formulários' : 'Nenhum formulário ativo'"
        [stateDescription]="resultado.error() ?? 'Crie o primeiro formulário ou remova o status nos filtros para ver os inativos.'"
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
        (emptyAction)="abrir()"
        (rowActivate)="construtor($event)"
      >
        <ng-template cesCell="codigo" let-row><code class="ces-cell-muted">{{ row.codigo }}</code></ng-template>
        <ng-template cesCell="padrao" let-row>
          @if (row.padrao) {
            <ces-status tone="info" icon="pi-star">Padrão</ces-status>
          } @else {
            <span class="ces-cell-muted">Não</span>
          }
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button icon="pi pi-copy" [label]="'Duplicar ' + row.titulo" size="icon-sm" (pressed)="abrir(row)"></ces-icon-button>
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.titulo"
              size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="origem() ? 'Duplicar formulário' : 'Novo formulário'"
      [description]="origem() ? 'Cópia de ' + origem()!.titulo : 'Depois de criar, o construtor abre para configurar os campos.'"
      [dirty]="alterado()" size="sm">
      @if (origem()) {
        <ces-callout tone="info" [dense]="true">A estrutura completa é copiada para uma nova variante editável.</ces-callout>
      }
      <ces-form-section title="Identificação" icon="pi pi-file-edit">
        <ces-select label="Tipo" [required]="true" [options]="tipos" [(value)]="tipo"></ces-select>
        <ces-text-input label="Código" [required]="true" [value]="codigo()" (valueChange)="codigo.set($event.toUpperCase())" placeholder="Ex.: SERVICOS"
          description="Letras maiúsculas, números e sublinhado, começando por letra." [error]="erro('codigo')"></ces-text-input>
        <ces-text-input label="Título" [required]="true" [(value)]="titulo" [error]="erro('titulo')"></ces-text-input>
        <ces-textarea label="Descrição" [(value)]="descricao" [rows]="3" [showOptional]="true"></ces-textarea>
        <ces-boolean-control label="Formulário padrão do tipo" [(value)]="padrao"></ces-boolean-control>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="origem() ? 'Duplicar' : 'Criar e configurar'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-ajuda-listagem #ajuda titulo="Formulários financeiros" entidadePlural="formulários" busca="código, título ou descrição"
      rotuloNovo="Novo formulário">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-copy"></i> Construtor e cópias</h4>
        <p class="help-text-desc">Clique na linha para abrir o construtor do formulário. O ícone de cópia cria uma variante com a mesma estrutura.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class FormulariosFinanceirosComponent {
  private readonly servico = inject(FinanceiroService);
  private readonly router = inject(Router);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  protected readonly tipos = TIPOS;
  protected readonly situacoes = SITUACOES;
  // Os parâmetros `tipo` e `ativo` são os mesmos da versão anterior: links antigos continuam valendo.
  protected readonly lista = cesListState({
    storageKey: 'financeiro-formularios',
    filters: {
      tipo: cesFilter.value<TipoSolicitacao | null>(null, { label: 'Tipo', options: () => TIPOS }),
      ativo: cesFilter.value<boolean | null>(true, { label: 'Status', options: () => SITUACOES })
    },
    sort: [{ key: 'tipoSolicitacao', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<FormularioResumo>(
    (pedido) => this.servico.listarFormularios({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.servico.listarFormularios({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<FormularioResumo>[] = [
    { key: 'tipoSolicitacao', header: 'Tipo', sortField: 'tipoSolicitacao', display: (f) => TIPOS.find((t) => t.value === f.tipoSolicitacao)?.label ?? f.tipoSolicitacao },
    { key: 'codigo', header: 'Código', type: 'custom', sortField: 'codigo', value: (f) => f.codigo, display: (f) => f.codigo },
    { key: 'titulo', header: 'Título', priority: 1, sortField: 'titulo' },
    { key: 'descricao', header: 'Descrição', display: (f) => f.descricao || '—' },
    { key: 'padrao', header: 'Padrão', type: 'custom', value: (f) => f.padrao, display: (f) => (f.padrao ? 'Sim' : 'Não') },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, value: (f) => f.ativo, display: (f) => (f.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (f: FormularioResumo) => f.titulo;

  protected readonly formularioAberto = signal(false);
  protected readonly origem = signal<FormularioResumo | null>(null);
  protected readonly tipo = signal<TipoSolicitacao | null>('COMPRA');
  protected readonly codigo = signal('');
  protected readonly titulo = signal('');
  protected readonly descricao = signal('');
  protected readonly padrao = signal(false);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => !!(this.codigo() + this.titulo() + this.descricao()).trim());
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!CODIGO.test(this.codigo().trim())) e['codigo'] = 'Código inválido.';
    if (!this.titulo().trim()) e['titulo'] = 'Informe o título.';
    return e;
  });

  private filtro() {
    const { filters, query } = this.lista.request();
    return { buscaGlobal: query, tipoSolicitacao: filters.tipo, ativo: filters.ativo };
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  /** Novo formulário ou, com `item`, duplicar um existente. */
  protected abrir(item?: FormularioResumo): void {
    this.origem.set(item ?? null);
    this.tipo.set(item?.tipoSolicitacao ?? 'COMPRA');
    this.codigo.set(item ? `${item.codigo}_COPIA` : '');
    this.titulo.set(item ? `${item.titulo} - Cópia` : '');
    this.descricao.set(item?.descricao ?? '');
    this.padrao.set(false);
    this.tentouSalvar.set(false);
    this.formularioAberto.set(true);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length || !this.tipo()) return;
    const request = {
      tipoSolicitacao: this.tipo()!,
      codigo: this.codigo().trim().toUpperCase(),
      titulo: this.titulo().trim(),
      descricao: this.descricao().trim() || null,
      padrao: this.padrao()
    };
    const origem = this.origem();
    this.salvando.set(true);
    (origem ? this.servico.duplicarFormulario(origem.id, request) : this.servico.criarFormulario(request)).subscribe({
      next: (schema) => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        void this.router.navigate(['/financeiro/admin/formularios', schema.id]);
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected construtor(item: FormularioResumo): void {
    void this.router.navigate(['/financeiro/admin/formularios', item.id]);
  }

  protected async alternar(item: FormularioResumo): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: item.ativo ? 'Desativar formulário' : 'Ativar formulário',
      message: item.ativo ? `"${item.titulo}" deixa de ser oferecido nas novas solicitações.` : `"${item.titulo}" volta a ser oferecido nas novas solicitações.`,
      confirmLabel: item.ativo ? 'Desativar' : 'Ativar',
      variant: item.ativo ? 'destructive' : 'default'
    });
    if (!ok) return;
    this.servico.acaoFormulario(item.id, !item.ativo).subscribe({
      next: () => {
        this.notificacoes.success(item.ativo ? 'Formulário desativado.' : 'Formulário ativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }
}
