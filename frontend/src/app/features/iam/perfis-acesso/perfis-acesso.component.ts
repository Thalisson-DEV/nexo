import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
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
import { Observable, map, of, switchMap } from 'rxjs';

import { PerfilAcessoImportacaoDTO, PerfilAcessoResponse, PermissaoResponse } from '../../../core/models/iam.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { PerfisAcessoService } from '../../../core/services/perfis-acesso.service';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';
import { PermissoesPerfilEditorComponent } from './permissoes-perfil-editor.component';

@Component({
  selector: 'app-perfis-acesso',
  standalone: true,
  styles: `
    .perfil-copiar {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      gap: 0.75rem;
      margin: 0.25rem 0 1rem;
    }
    .perfil-copiar ces-select {
      flex: 1 1 16rem;
    }
  `,
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
    CesImportDrawerComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AuditDialogComponent,
    AjudaListagemComponent,
    PermissoesPerfilEditorComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novo()">
      <ces-page-header
        title="Perfis de acesso"
        icon="pi pi-shield"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Cadastros' }, { label: 'Perfis de acesso' }]"
        description="Conjuntos de permissões atribuídos aos usuários."
        helpLabel="Ajuda sobre perfis de acesso"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacao.aberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Novo perfil" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="iam-perfis" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Perfis de acesso"
        mode="server"
        storageKey="iam-perfis"
        exportFileName="perfis-de-acesso"
        searchPlaceholder="Nome do perfil"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo perfil"
        noResultsTitle="Nenhum perfil encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os perfis' : 'Nenhum perfil cadastrado'"
        [stateDescription]="resultado.error() ?? 'Cadastre um perfil ou importe uma planilha.'"
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
        <ng-template cesCell="nome" let-row><span class="ces-cell-strong">{{ row.nome }}</span></ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.nome" size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.nome" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar perfil' : 'Novo perfil'" [description]="emEdicao()?.nome"
      [dirty]="alterado()" size="xl">
      <ces-form-section title="Perfil" icon="pi pi-shield">
        <ces-text-input label="Nome" [required]="true" [(value)]="nome" [maxlength]="50" placeholder="Ex.: ADMIN" [error]="erro('nome')"></ces-text-input>
        @if (emEdicao()) {
          <ces-boolean-control label="Perfil ativo" kind="toggle" [(value)]="ativo"></ces-boolean-control>
        }
      </ces-form-section>
      <ces-form-section title="Permissões" icon="pi pi-lock" description="O que quem tem este perfil pode ver e fazer em cada módulo.">
        @if (emEdicao()) {
          <ces-callout tone="warning" title="Vale no próximo acesso">
            As permissões mudam na hora no servidor, mas quem já está usando o sistema com este perfil precisa
            <strong>sair e entrar de novo</strong> para ver os menus e botões atualizados.
          </ces-callout>
        }
        <div class="perfil-copiar">
          <ces-select
            label="Começar a partir de outro perfil"
            description="Copia as permissões de um perfil existente; depois é só ajustar."
            placeholder="Escolha um perfil"
            [options]="opcoesCopia()"
            [(value)]="perfilParaCopiar"
            [filter]="opcoesCopia().length > 8"
          ></ces-select>
          <ces-button label="Copiar permissões" icon="pi pi-copy" variant="outline" [disabled]="!perfilParaCopiar()" [loading]="copiando()" (pressed)="copiarPermissoes()"></ces-button>
        </div>
        <app-permissoes-perfil-editor
          [permissoes]="todasPermissoes()"
          [(selecionadas)]="selecionadas"
          [iniciais]="iniciais()"
          [carregando]="carregandoPermissoes()"
        />
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar perfil'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer [(visible)]="importacao.aberta" title="Importar perfis" description="Planilha CSV com o nome de cada perfil. As permissões são marcadas depois, na edição."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao.linhas()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
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
    <app-ajuda-listagem #ajuda titulo="Perfis de acesso" entidadePlural="perfis" busca="nome do perfil" rotuloNovo="Novo perfil" [importacao]="true">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-lock"></i> Permissões</h4>
        <p class="help-text-desc">Na edição, as permissões ficam agrupadas por módulo. Em cada tela, "Ver" deixa consultar e "Editar" deixa cadastrar e alterar; as outras opções são ações específicas, como aprovar ou entregar.</p>
        <p class="help-text-desc">Para começar rápido, copie as permissões de um perfil parecido e ajuste. Os atalhos "Só consultar", "Tudo" e "Nenhuma" valem para o módulo inteiro.</p>
        <p class="help-text-desc">Depois de salvar, quem usa o perfil precisa sair e entrar de novo no sistema para ver os menus atualizados.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class PerfisAcessoComponent {
  private readonly perfis = inject(PerfisAcessoService);
  private readonly importacaoService = inject(ImportacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly statusOpcoes = OPCOES_STATUS;

  protected readonly lista = cesListState({
    storageKey: 'iam-perfis',
    filters: {
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'nome', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<PerfilAcessoResponse>(
    (pedido) => this.perfis.listarPerfis({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.perfis.listarPerfis({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<PerfilAcessoResponse>[] = [
    { key: 'nome', header: 'Nome', type: 'custom', priority: 1, sortField: 'nome', value: (p) => p.nome, display: (p) => p.nome },
    { key: 'ativo', header: 'Status', type: 'custom', sortField: 'ativo', value: (p) => p.ativo, display: (p) => (p.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (p: PerfilAcessoResponse) => p.nome;

  protected readonly todasPermissoes = signal<PermissaoResponse[]>([]);
  /** Perfis que servem de ponto de partida (todos menos o que está em edição). */
  private readonly perfisDisponiveis = signal<PerfilAcessoResponse[]>([]);
  protected readonly opcoesCopia = computed(() =>
    this.perfisDisponiveis()
      .filter((p) => p.id !== this.emEdicao()?.id)
      .map((p) => ({ label: p.ativo ? p.nome : `${p.nome} (inativo)`, value: p.id }))
  );
  protected readonly perfilParaCopiar = signal<string | null>(null);
  protected readonly copiando = signal(false);

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<PerfilAcessoResponse | null>(null);
  protected readonly nome = signal('');
  protected readonly ativo = signal(true);
  protected readonly selecionadas = signal<ReadonlySet<string>>(new Set());
  protected readonly iniciais = signal<ReadonlySet<string>>(new Set());
  protected readonly carregandoPermissoes = signal(false);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);

  protected readonly alterado = computed(() => {
    const p = this.emEdicao();
    const permissoes = !mesmoConjunto(this.selecionadas(), this.iniciais());
    if (!p) return permissoes || !!this.nome().trim();
    return permissoes || this.nome() !== p.nome || this.ativo() !== p.ativo;
  });
  private readonly erros = computed<Record<string, string>>(() => (this.nome().trim() ? {} : ({ nome: 'Informe o nome do perfil.' } as Record<string, string>)));

  protected readonly importacao = importacaoListagem<PerfilAcessoImportacaoDTO>({
    enviar: (arquivo) => this.perfis.importarPerfis(arquivo),
    confirmar: (perfis) => this.perfis.confirmarImportacao({ perfis }),
    modelo: () => this.importacaoService.baixarModeloCsv('/v1/iam/perfis/importar/modelo', 'modelo-perfis-acesso.csv'),
    aoProcessar: () => this.lista.reload()
  });
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = CHAVE_LINHA_IMPORTACAO;
  protected readonly colunasImportacao: CesDataTableColumn<PerfilAcessoImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'nome', header: 'Nome do perfil', type: 'custom', priority: 1, value: (r) => r.nome },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  constructor() {
    this.perfis.listarTodasPermissoes().subscribe({ next: (r) => this.todasPermissoes.set(r.content ?? []), error: () => undefined });
  }

  private carregarPerfisParaCopia(): void {
    this.perfis.listarPerfis({ page: 0, size: 200, sort: 'nome,asc' }).subscribe({
      next: (r) => this.perfisDisponiveis.set(r.content ?? []),
      error: () => undefined
    });
  }

  protected copiarPermissoes(): void {
    const id = this.perfilParaCopiar();
    if (!id) return;
    const origem = this.perfisDisponiveis().find((p) => p.id === id)?.nome ?? 'o perfil escolhido';
    this.copiando.set(true);
    this.perfis.listarPermissoesPorPerfil(id).subscribe({
      next: (permissoes) => {
        this.selecionadas.set(new Set(permissoes.map((x) => x.id)));
        this.copiando.set(false);
        this.notificacoes.info(`Permissões de ${origem} copiadas. Revise e salve para aplicar.`);
      },
      error: (e) => {
        this.copiando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  private filtro() {
    const { filters, query } = this.lista.request();
    return { ativo: filters.ativo, buscaGlobal: query || undefined };
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    this.preencher(null);
    this.carregarPerfisParaCopia();
    this.formularioAberto.set(true);
  }

  protected editar(p: PerfilAcessoResponse): void {
    this.preencher(p);
    this.carregarPerfisParaCopia();
    this.formularioAberto.set(true);
    this.carregandoPermissoes.set(true);
    this.perfis.listarPermissoesPorPerfil(p.id).subscribe({
      next: (permissoes) => {
        const ids = new Set(permissoes.map((x) => x.id));
        this.selecionadas.set(ids);
        this.iniciais.set(ids);
        this.carregandoPermissoes.set(false);
      },
      error: (e) => {
        this.carregandoPermissoes.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  private preencher(p: PerfilAcessoResponse | null): void {
    this.emEdicao.set(p);
    this.nome.set(p?.nome ?? '');
    this.ativo.set(p?.ativo ?? true);
    this.selecionadas.set(new Set());
    this.iniciais.set(new Set());
    this.perfilParaCopiar.set(null);
    this.tentouSalvar.set(false);
  }

  /** Uma chamada só com o conjunto inteiro, e só quando algo mudou. */
  private gravarPermissoes(idPerfil: string): Observable<unknown> {
    return mesmoConjunto(this.selecionadas(), this.iniciais())
      ? of(null)
      : this.perfis.definirPermissoes(idPerfil, [...this.selecionadas()]);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const ativo = this.ativo();
    const request = { nome: this.nome().trim() };
    const mudouPermissoes = !mesmoConjunto(this.selecionadas(), this.iniciais());
    this.salvando.set(true);
    // Em sequência: nome, permissões e situação alteram o mesmo perfil.
    const salvar$: Observable<unknown> = atual
      ? (request.nome === atual.nome ? of(null) : this.perfis.atualizarPerfil(atual.id, request)).pipe(
          switchMap(() => this.gravarPermissoes(atual.id)),
          switchMap(() => (ativo === atual.ativo ? of(null) : ativo ? this.perfis.ativarPerfil(atual.id) : this.perfis.desativarPerfil(atual.id)))
        )
      : this.perfis.criarPerfil(request).pipe(switchMap((criado) => (criado ? this.gravarPermissoes(criado.id) : of(null)).pipe(map(() => criado))));
    salvar$.subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Perfil atualizado.' : 'Perfil cadastrado.');
        if (atual && mudouPermissoes) {
          this.notificacoes.info('Quem usa este perfil precisa sair e entrar de novo para ver os menus atualizados.');
        }
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e), { title: 'Não foi possível salvar o perfil' });
      }
    });
  }

  protected async alternar(p: PerfilAcessoResponse): Promise<void> {
    const desativar = p.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar perfil' : 'Ativar perfil',
      message: desativar ? `${p.nome} deixa de poder ser atribuído a usuários.` : `${p.nome} volta a poder ser atribuído a usuários.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.perfis.desativarPerfil(p.id) : this.perfis.ativarPerfil(p.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Perfil desativado.' : 'Perfil ativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(p: PerfilAcessoResponse): void {
    this.auditoria().show(p.id, p.nome, { nome: 'Nome do perfil', ativo: 'Status (ativo)', dataDesativacao: 'Data de desativação' });
  }
}

function mesmoConjunto(a: ReadonlySet<string>, b: ReadonlySet<string>): boolean {
  return a.size === b.size && [...a].every((x) => b.has(x));
}
