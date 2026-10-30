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
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';
import { map } from 'rxjs';

import { PermissaoResponse } from '../../../core/models/iam.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { PermissoesService } from '../../../core/services/permissoes.service';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

/** Permissão com o identificador `MODULO:RECURSO:ACAO` separado para exibição. */
interface PermissaoLinha extends PermissaoResponse {
  modulo: string;
  recurso: string;
  acao: string;
}

const MODULOS: CesSelectOption<string>[] = ['COMERCIAL', 'CONTRATO', 'ESTOQUE', 'EXECUCAO', 'IAM', 'LOGISTICA', 'RH'].map((m) => ({ label: m, value: m }));
const ACOES: CesSelectOption<string>[] = [
  { label: 'Leitura (READ)', value: 'READ' },
  { label: 'Escrita (WRITE)', value: 'WRITE' },
  { label: 'Execução (EXECUTE)', value: 'EXECUTE' },
  { label: 'Aprovação (APPROVE)', value: 'APPROVE' }
];
const RECURSO = /^[A-Z-]+$/;

function separar(p: PermissaoResponse): PermissaoLinha {
  const partes = p.nome.split(':');
  return {
    ...p,
    modulo: partes[0]?.toUpperCase() || '—',
    recurso: partes.slice(1, -1).join(':').toUpperCase() || '—',
    acao: partes[partes.length - 1]?.toUpperCase() || '—'
  };
}

@Component({
  selector: 'app-permissoes',
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
    AuditDialogComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novo()">
      <ces-page-header
        title="Permissões"
        icon="pi pi-lock"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'IAM' }, { label: 'Permissões' }]"
        description="Permissões no formato MÓDULO:RECURSO:AÇÃO, atribuídas aos perfis de acesso."
        helpLabel="Ajuda sobre permissões"
        (help)="ajuda.show()"
      >
        <ces-button actions icon="pi pi-plus" label="Nova permissão" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="iam-permissoes" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Permissões"
        mode="server"
        storageKey="iam-permissoes"
        exportFileName="permissoes"
        searchPlaceholder="Identificador (ex.: CONTRATO:ACAO:READ)"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Nova permissão"
        noResultsTitle="Nenhuma permissão encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as permissões' : 'Nenhuma permissão cadastrada'"
        [stateDescription]="resultado.error() ?? undefined"
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
          <code class="ces-cell-strong">{{ row.nome }}</code>
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo !== false ? 'success' : 'neutral'" [emphasis]="row.ativo !== false ? 'soft' : 'outline'">
            {{ row.ativo !== false ? 'Ativa' : 'Inativa' }}
          </ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo !== false ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo !== false ? 'Desativar ' : 'Ativar ') + row.nome"
              size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.nome" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar permissão' : 'Nova permissão'"
      [description]="emEdicao()?.nome" [dirty]="alterado()" size="sm">
      <ces-form-section title="Composição" icon="pi pi-lock">
        <div class="ces-form-grid">
          <ces-select label="Módulo" [required]="true" [options]="modulos" [(value)]="modulo" [error]="erro('modulo')"></ces-select>
          <ces-select label="Ação" [required]="true" [options]="acoes" [(value)]="acao" [error]="erro('acao')"></ces-select>
        </div>
        <ces-text-input label="Recurso" [required]="true" [value]="recurso()" (valueChange)="recurso.set(normalizar($event))"
          placeholder="Ex.: MATERIAL-SOLICITACAO" description="Só letras e hífen (ex.: BASE-OPERACIONAL)." [error]="erro('recurso')"></ces-text-input>
        <ces-text-input label="Identificador final" [value]="nome()" [readonly]="true"
          description="Montado a partir de módulo, recurso e ação."></ces-text-input>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar permissão'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-audit-dialog #auditoria></app-audit-dialog>
    <app-ajuda-listagem #ajuda titulo="Permissões" entidadePlural="permissões" artigoPlural="as" busca="identificador" rotuloNovo="Nova permissão">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-info-circle"></i> Identificador</h4>
        <p class="help-text-desc">O identificador é MÓDULO:RECURSO:AÇÃO (ex.: CONTRATO:ACAO:READ) e é o que os perfis de acesso e as rotas conferem.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class PermissoesComponent {
  private readonly servico = inject(PermissoesService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly modulos = MODULOS;
  protected readonly acoes = ACOES;
  protected readonly lista = cesListState({
    storageKey: 'iam-permissoes',
    filters: { ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS }) },
    sort: [{ key: 'nome', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<PermissaoLinha>(
    (pedido) => this.servico.listar({ ...this.filtro(), ...paginaPadrao(pedido) }).pipe(map((r) => ({ ...r, content: r.content.map(separar) }))),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao(
    (pagina) => this.servico.listar({ ...this.filtro(), ...pagina }).pipe(map((r) => ({ content: r.content.map(separar) }))),
    () => this.lista.request()
  );

  protected readonly colunas: CesDataTableColumn<PermissaoLinha>[] = [
    { key: 'modulo', header: 'Módulo', priority: 1 },
    { key: 'recurso', header: 'Recurso' },
    { key: 'acao', header: 'Ação' },
    { key: 'nome', header: 'Identificador', type: 'custom', priority: 1, sortField: 'nome', value: (p) => p.nome, display: (p) => p.nome },
    { key: 'ativo', header: 'Status', type: 'custom', value: (p) => p.ativo !== false, display: (p) => (p.ativo !== false ? 'Ativa' : 'Inativa') }
  ];
  protected readonly rotulo = (p: PermissaoLinha) => p.nome;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<PermissaoLinha | null>(null);
  protected readonly modulo = signal<string | null>(null);
  protected readonly recurso = signal('');
  protected readonly acao = signal<string | null>(null);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly nome = computed(() =>
    this.modulo() && this.recurso() && this.acao() ? `${this.modulo()}:${this.recurso()}:${this.acao()}` : this.emEdicao()?.nome ?? ''
  );
  protected readonly alterado = computed(() => (this.emEdicao() ? this.nome() !== this.emEdicao()!.nome : !!(this.modulo() || this.recurso() || this.acao())));
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.modulo()) e['modulo'] = 'Escolha o módulo.';
    if (!this.acao()) e['acao'] = 'Escolha a ação.';
    if (!RECURSO.test(this.recurso())) e['recurso'] = 'Use só letras e hífen.';
    return e;
  });

  private filtro() {
    const { filters, query } = this.lista.request();
    return { ...filters, buscaGlobal: query || undefined };
  }

  protected normalizar(valor: string): string {
    return valor.toUpperCase().replace(/\s+/g, '-');
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    this.emEdicao.set(null);
    this.modulo.set(null);
    this.recurso.set('');
    this.acao.set(null);
    this.tentouSalvar.set(false);
    this.formularioAberto.set(true);
  }

  protected editar(p: PermissaoLinha): void {
    this.emEdicao.set(p);
    const valido = p.nome.split(':').length >= 3;
    this.modulo.set(valido ? p.modulo : null);
    this.recurso.set(valido ? p.recurso : '');
    this.acao.set(valido ? p.acao : null);
    this.tentouSalvar.set(false);
    this.formularioAberto.set(true);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const request = { nome: this.nome() };
    this.salvando.set(true);
    (atual ? this.servico.atualizar(atual.id, request) : this.servico.criar(request)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Permissão atualizada.' : 'Permissão cadastrada.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(p: PermissaoLinha): Promise<void> {
    const ativar = p.ativo === false;
    const ok = await this.confirmacao.confirm({
      title: ativar ? 'Ativar permissão' : 'Desativar permissão',
      message: ativar ? `${p.nome} volta a valer para os perfis que a têm.` : `${p.nome} deixa de valer para os perfis que a têm.`,
      confirmLabel: ativar ? 'Ativar' : 'Desativar',
      variant: ativar ? 'default' : 'destructive'
    });
    if (!ok) return;
    this.servico.alterarStatus(p.id, ativar).subscribe({
      next: () => {
        this.notificacoes.success(ativar ? 'Permissão ativada.' : 'Permissão desativada.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(p: PermissaoLinha): void {
    this.auditoria().show(p.id, p.nome, { nome: 'Nome da permissão', ativo: 'Status (ativo)' });
  }
}
