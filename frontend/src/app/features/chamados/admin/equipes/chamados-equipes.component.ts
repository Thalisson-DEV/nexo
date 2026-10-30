import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import {
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
  CesMultiSelectComponent,
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
import { catchError, forkJoin, of, tap } from 'rxjs';

import { EquipeChamado, EquipeChamadoRequest } from '../../../../core/models/chamado.models';
import { UsuarioResponse } from '../../../../core/models/iam.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { ChamadosService } from '../../../../core/services/chamados.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { RemoteLookup } from '../../../../core/utils/remote-lookup.util';
import { AjudaListagemComponent } from '../../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, exportacaoPadrao, paginaPadrao } from '../../../../shared/listagem/listagem';

const SITUACOES: CesSelectOption<boolean | null>[] = [
  { label: 'Todas', value: null },
  { label: 'Ativas', value: true },
  { label: 'Inativas', value: false }
];

@Component({
  selector: 'app-chamados-equipes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesMultiSelectComponent,
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
        title="Equipes de chamados"
        icon="pi pi-users"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Chamados' }, { label: 'Equipes' }]"
        description="Equipes que atendem os chamados. Com membros definidos, só eles podem ser responsáveis; sem membros, a atribuição é livre."
        helpLabel="Ajuda sobre equipes de chamados"
        (help)="ajuda.show()"
      >
        <ces-button actions icon="pi pi-plus" label="Nova equipe" [keyShortcut]="atalhos.newShortcut()" (pressed)="abrir()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="chamados-equipes" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-text-input label="Código" [value]="lista.filters.codigo() ?? ''" (valueChange)="lista.filters.codigo.set($event || null)" placeholder="Ex.: INFRAESTRUTURA"></ces-text-input>
          <ces-select label="Status" [options]="situacoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Equipes"
        mode="server"
        storageKey="chamados-equipes"
        exportFileName="equipes-chamados"
        searchPlaceholder="Nome, código ou descrição"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Nova equipe"
        noResultsTitle="Nenhuma equipe encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as equipes' : 'Nenhuma equipe ativa'"
        [stateDescription]="resultado.error() ?? 'Cadastre a primeira equipe ou remova o status nos filtros para ver as inativas.'"
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
        (rowActivate)="abrir($event)"
      >
        <ng-template cesCell="nome" let-row>
          <span class="ces-cell-strong">{{ row.nome }}</span>
          <code class="ces-cell-muted">{{ row.codigo }}</code>
        </ng-template>
        <ng-template cesCell="membros" let-row>
          @if (row.membros.length) {
            <span class="ces-cell-chips">
              @for (membro of row.membros.slice(0, 3); track membro) {
                <ces-status tone="neutral" emphasis="outline">{{ nomeMembro(membro) }}</ces-status>
              }
              @if (row.membros.length > 3) {
                <span class="ces-cell-muted">+{{ row.membros.length - 3 }}</span>
              }
            </span>
          } @else {
            <span class="ces-cell-muted">Atribuição livre</span>
          }
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativa' : 'Inativa' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.nome"
              size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar equipe' : 'Nova equipe'" [description]="emEdicao()?.nome"
      [dirty]="alterado()" [loading]="carregandoDetalhe()" size="md">
      <ces-form-section title="Identificação" icon="pi pi-users">
        <div class="ces-form-grid">
          <ces-text-input label="Código" [required]="true" [value]="codigo()" (valueChange)="codigo.set($event.toUpperCase())" [maxlength]="80"
            placeholder="INFRAESTRUTURA" [error]="erro('codigo')"></ces-text-input>
          <ces-text-input label="Nome" [required]="true" [(value)]="nome" [maxlength]="150" placeholder="Infraestrutura" [error]="erro('nome')"></ces-text-input>
        </div>
        <ces-textarea label="Descrição" [(value)]="descricao" [rows]="3" [maxlength]="1000" [showOptional]="true"></ces-textarea>
      </ces-form-section>
      <ces-form-section title="Membros" icon="pi pi-user-plus">
        <ces-multiselect label="Membros" [options]="usuariosOpcoes()" [(value)]="membros" display="chip" [loading]="usuarioLookup.loading()"
          placeholder="Selecione os usuários da equipe" [showOptional]="true" (opened)="usuarioLookup.open()" (search)="usuarioLookup.search($event)"></ces-multiselect>
        <ces-callout tone="info" [dense]="true">Sem membros, a equipe aceita atribuição livre. Com membros, só eles podem ser responsáveis.</ces-callout>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar equipe'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-ajuda-listagem #ajuda titulo="Equipes de chamados" entidadePlural="equipes" artigoPlural="as" busca="nome, código ou descrição" rotuloNovo="Nova equipe">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-info-circle"></i> Desativar</h4>
        <p class="help-text-desc">A desativação só é permitida se a equipe não estiver em tópicos ativos nem em chamados pendentes.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class ChamadosEquipesComponent {
  private readonly servico = inject(ChamadosService);
  private readonly usuarios = inject(UsuariosService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  protected readonly situacoes = SITUACOES;
  protected readonly lista = cesListState({
    storageKey: 'chamados-equipes',
    filters: {
      codigo: cesFilter.value<string | null>(null, { label: 'Código' }),
      ativo: cesFilter.value<boolean | null>(true, { label: 'Status', options: () => SITUACOES })
    },
    sort: [{ key: 'nome', direction: 'asc' }]
  });

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<EquipeChamado | null>(null);
  protected readonly carregandoDetalhe = signal(false);
  protected readonly codigo = signal('');
  protected readonly nome = signal('');
  protected readonly descricao = signal('');
  protected readonly membros = signal<string[]>([]);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);

  protected readonly usuarioLookup = new RemoteLookup<UsuarioResponse>(
    {
      identify: (usuario) => usuario.id,
      selectedKeys: () => this.membros(),
      load: ({ query, page, size }) => this.usuarios.listarUsuarios({ ativo: true, buscaGlobal: query, page, size, sort: 'login,asc' })
    },
    inject(DestroyRef)
  );
  protected readonly usuariosOpcoes = computed<CesSelectOption<string>[]>(() =>
    this.usuarioLookup.items().map((u) => ({ label: u.login, value: u.id }))
  );

  protected readonly resultado = this.lista.results<EquipeChamado>(
    (pedido) =>
      this.servico
        .listarEquipes({ ...this.filtro(), ...(paginaPadrao(pedido) as { page: number; size: number; sort?: string }) })
        .pipe(tap((r) => this.carregarUsuarios(r.content.flatMap((e) => e.membros)))),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao(
    (pagina) => this.servico.listarEquipes({ ...this.filtro(), ...(pagina as { page: number; size: number; sort?: string }) }),
    () => this.lista.request()
  );

  protected readonly colunas: CesDataTableColumn<EquipeChamado>[] = [
    { key: 'nome', header: 'Equipe', type: 'custom', priority: 1, sortField: 'nome', value: (e) => e.nome, display: (e) => `${e.nome} (${e.codigo})` },
    { key: 'descricao', header: 'Descrição', display: (e) => e.descricao || '—' },
    { key: 'membros', header: 'Membros', type: 'custom', value: (e) => e.membros.length, display: (e) => (e.membros.length ? e.membros.map((m) => this.nomeMembro(m)).join(', ') : 'Atribuição livre') },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, value: (e) => e.ativo, display: (e) => (e.ativo ? 'Ativa' : 'Inativa') }
  ];
  protected readonly rotulo = (e: EquipeChamado) => e.nome;
  protected readonly alterado = computed(() => {
    const e = this.emEdicao();
    return e
      ? this.codigo() !== e.codigo || this.nome() !== e.nome || this.descricao() !== (e.descricao ?? '') || this.membros().join() !== e.membros.join()
      : !!(this.codigo() + this.nome() + this.descricao()).trim() || this.membros().length > 0;
  });
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.codigo().trim()) e['codigo'] = 'Informe o código.';
    if (!this.nome().trim()) e['nome'] = 'Informe o nome.';
    return e;
  });

  private filtro() {
    const { filters, query } = this.lista.request();
    return { buscaGlobal: query || undefined, codigo: filters.codigo?.trim() || undefined, ativo: filters.ativo ?? undefined };
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected nomeMembro(id: string): string {
    return this.usuarioLookup.items().find((u) => u.id === id)?.login ?? id;
  }

  protected abrir(item?: EquipeChamado): void {
    this.preencher(item ?? null);
    this.formularioAberto.set(true);
    if (!item) return;
    // O resumo da lista pode não trazer tudo: a edição parte do detalhe.
    this.carregandoDetalhe.set(true);
    this.servico.buscarEquipe(item.id).subscribe({
      next: (detalhe) => {
        this.carregandoDetalhe.set(false);
        this.preencher(detalhe);
        this.carregarUsuarios(detalhe.membros);
      },
      error: (e) => {
        this.carregandoDetalhe.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  private preencher(e: EquipeChamado | null): void {
    this.emEdicao.set(e);
    this.codigo.set(e?.codigo ?? '');
    this.nome.set(e?.nome ?? '');
    this.descricao.set(e?.descricao ?? '');
    this.membros.set([...(e?.membros ?? [])]);
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const request: EquipeChamadoRequest = {
      codigo: this.codigo().trim().toUpperCase(),
      nome: this.nome().trim(),
      descricao: this.descricao().trim(),
      membros: this.membros()
    };
    this.salvando.set(true);
    (atual ? this.servico.atualizarEquipe(atual.id, request) : this.servico.criarEquipe(request)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success('Equipe salva.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(item: EquipeChamado): Promise<void> {
    const ativar = !item.ativo;
    const ok = await this.confirmacao.confirm({
      title: ativar ? 'Ativar equipe' : 'Desativar equipe',
      message: ativar
        ? `A equipe ${item.nome} voltará a receber chamados.`
        : 'A desativação só é permitida se a equipe não estiver em tópicos ativos nem em chamados pendentes.',
      confirmLabel: ativar ? 'Ativar' : 'Desativar',
      variant: ativar ? 'default' : 'destructive'
    });
    if (!ok) return;
    this.servico.alternarEquipe(item.id, ativar).subscribe({
      next: () => {
        this.notificacoes.success(ativar ? 'Equipe ativada.' : 'Equipe desativada.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  /** Busca os nomes dos membros ainda não carregados, para mostrar o login em vez do id. */
  private carregarUsuarios(ids: readonly string[]): void {
    const carregados = new Set(this.usuarioLookup.items().map((u) => u.id));
    const faltantes = [...new Set(ids)].filter((id) => id && !carregados.has(id));
    if (!faltantes.length) return;
    forkJoin(faltantes.map((id) => this.usuarios.buscarUsuarioPorId(id).pipe(catchError(() => of(null))))).subscribe((lista) =>
      this.usuarioLookup.setSeed(lista.filter((u): u is UsuarioResponse => u !== null))
    );
  }
}
