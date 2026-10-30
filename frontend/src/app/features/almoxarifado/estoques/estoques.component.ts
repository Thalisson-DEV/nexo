import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
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
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesFilter,
  cesImportStatus,
  cesListState
} from '@ces/ui';
import { Observable, catchError, of, switchMap } from 'rxjs';

import { EstoqueImportacaoDTO, EstoqueRequest, EstoqueResponse, TipoEstoqueResponse } from '../../../core/models/almoxarifado.models';
import { BaseOperacionalResponse, EquipeResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

@Component({
  selector: 'app-estoques',
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
        title="Estoques"
        icon="pi pi-database"
        [breadcrumb]="[{ label: 'Almoxarifado' }, { label: 'Estoques' }]"
        description="Locais de estoque (almoxarifados e estoques de equipe), com tipo, base e equipe responsável."
        helpLabel="Ajuda sobre estoques"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacao.aberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Novo estoque" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="almox-estoques" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Tipo de estoque" [options]="tiposOpcoes()" [(value)]="lista.filters.idTipoEstoque" [clearable]="true" placeholder="Todos"></ces-select>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Estoques"
        mode="server"
        storageKey="almox-estoques"
        exportFileName="estoques"
        searchPlaceholder="Identificador do estoque"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo estoque"
        noResultsTitle="Nenhum estoque encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os estoques' : 'Nenhum estoque cadastrado'"
        [stateDescription]="resultado.error() ?? 'Cadastre um estoque ou importe uma planilha.'"
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
        <ng-template cesCell="nome" let-row><span class="ces-cell-strong">{{ row.nomeIdentificador }}</span></ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.nomeIdentificador" size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.nomeIdentificador" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar estoque' : 'Novo estoque'" [description]="emEdicao()?.nomeIdentificador"
      [dirty]="alterado()" size="md">
      <ces-form-section title="Estoque" icon="pi pi-database">
        <ces-text-input label="Identificador" [required]="true" [(value)]="identificador" [maxlength]="100" placeholder="Ex.: Almoxarifado Central" [error]="erro('nome')"></ces-text-input>
        <div class="ces-form-grid">
          <ces-select label="Tipo de estoque" [required]="true" [options]="tiposOpcoes()" [(value)]="idTipo" [error]="erro('tipo')"></ces-select>
          <ces-select label="Base operacional" [required]="true" [options]="basesOpcoes()" [(value)]="idBase" [filter]="true" [error]="erro('base')"></ces-select>
          <ces-select label="Equipe" [options]="equipesOpcoes()" [(value)]="idEquipe" [filter]="true" [clearable]="true" [showOptional]="true"
            [loading]="equipes.loading()" (opened)="equipes.open()" (search)="equipes.search($event)" placeholder="Pesquise pelo código"
            description="Só para estoques de equipe."></ces-select>
        </div>
        @if (emEdicao()) {
          <ces-boolean-control label="Estoque ativo" kind="toggle" [(value)]="ativo"></ces-boolean-control>
        }
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar estoque'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer [(visible)]="importacao.aberta" title="Importar estoques" description="Planilha CSV com identificador, tipo de estoque e código da equipe."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao.linhas()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="nome" let-row>
            @if (row.editing) { <ces-text-input label="Identificador" [labelVisible]="false" [(value)]="row.nomeIdentificador"></ces-text-input> } @else { {{ row.nomeIdentificador }} }
          </ng-template>
          <ng-template cesCell="tipo" let-row>
            @if (row.editing) {
              <ces-select label="Tipo" [labelVisible]="false" [options]="tiposPorNome()" [(value)]="row.nomeTipoEstoque"></ces-select>
            } @else {
              {{ row.nomeTipoEstoque || '—' }}
            }
          </ng-template>
          <ng-template cesCell="equipe" let-row>
            @if (row.editing) { <ces-text-input label="Equipe" [labelVisible]="false" [(value)]="row.codigoEquipe"></ces-text-input> } @else { {{ row.codigoEquipe || '—' }} }
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
    <app-ajuda-listagem #ajuda titulo="Estoques" entidadePlural="estoques" busca="identificador do estoque" rotuloNovo="Novo estoque" [importacao]="true"></app-ajuda-listagem>
  `
})
export class EstoquesComponent {
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly rh = inject(RhService);
  private readonly importacaoService = inject(ImportacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly tipos = signal<TipoEstoqueResponse[]>([]);
  protected readonly bases = signal<BaseOperacionalResponse[]>([]);
  /** Códigos de equipe e nomes de base já conhecidos (a resposta traz só os IDs). */
  private readonly nomes = signal<Record<string, string>>({});
  protected readonly tiposOpcoes = computed<CesSelectOption<string>[]>(() => this.tipos().map((t) => ({ label: t.nome, value: t.id })));
  protected readonly tiposPorNome = computed<CesSelectOption<string>[]>(() => this.tipos().map((t) => ({ label: t.nome, value: t.nome })));
  protected readonly basesOpcoes = computed<CesSelectOption<string>[]>(() => this.bases().map((b) => ({ label: b.nome, value: b.id })));

  protected readonly equipes = new RemoteLookup<EquipeResponse>(
    {
      identify: (e) => e.id,
      selectedKeys: () => (this.idEquipe() ? [this.idEquipe()!] : []),
      load: ({ query, page, size }) => this.rh.listarEquipes({ ativo: true, buscaGlobal: query || undefined, page, size, sort: 'codigo,asc' })
    },
    inject(DestroyRef)
  );
  protected readonly equipesOpcoes = computed<CesSelectOption<string>[]>(() => this.equipes.items().map((e) => ({ label: e.codigo, value: e.id })));

  protected readonly lista = cesListState({
    storageKey: 'almox-estoques',
    filters: {
      idTipoEstoque: cesFilter.value<string | null>(null, {
        label: 'Tipo',
        param: 'tipo',
        format: (id): string | null => (id ? (this.tipos().find((t) => t.id === id)?.nome ?? 'tipo selecionado') : null)
      }),
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'nome', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<EstoqueResponse>(
    (pedido) => this.almoxarifado.listarEstoques({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.almoxarifado.listarEstoques({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<EstoqueResponse>[] = [
    { key: 'nome', header: 'Identificador', type: 'custom', priority: 1, sortField: 'nomeIdentificador', value: (e) => e.nomeIdentificador, display: (e) => e.nomeIdentificador },
    { key: 'tipo', header: 'Tipo', priority: 1, sortField: 'tipoEstoque.nome', value: (e) => e.tipoEstoque?.nome, display: (e) => e.tipoEstoque?.nome ?? '—' },
    { key: 'equipe', header: 'Equipe', value: (e) => this.nomeDe(e.idEquipe), display: (e) => this.nomeDe(e.idEquipe) },
    { key: 'base', header: 'Base', value: (e) => this.nomeDe(e.idBaseOperacional), display: (e) => this.nomeDe(e.idBaseOperacional) },
    { key: 'ativo', header: 'Status', type: 'custom', sortField: 'ativo', value: (e) => e.ativo, display: (e) => (e.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (e: EstoqueResponse) => e.nomeIdentificador;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<EstoqueResponse | null>(null);
  protected readonly identificador = signal('');
  protected readonly nomeDe = (id: string | null | undefined): string => (id ? (this.nomes()[id] ?? '…') : '—');
  protected readonly idTipo = signal<string | null>(null);
  protected readonly idBase = signal<string | null>(null);
  protected readonly idEquipe = signal<string | null>(null);
  protected readonly ativo = signal(true);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => {
    const e = this.emEdicao();
    if (!e) return !!this.identificador().trim() || !!this.idTipo() || !!this.idBase() || !!this.idEquipe();
    return (
      this.identificador() !== e.nomeIdentificador ||
      this.idTipo() !== (e.tipoEstoque?.id ?? null) ||
      this.idBase() !== (e.idBaseOperacional ?? null) ||
      this.idEquipe() !== (e.idEquipe ?? null) ||
      this.ativo() !== e.ativo
    );
  });
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.identificador().trim()) e['nome'] = 'Informe o identificador.';
    if (!this.idTipo()) e['tipo'] = 'Escolha o tipo de estoque.';
    if (!this.idBase()) e['base'] = 'Escolha a base.';
    return e;
  });

  protected readonly importacao = importacaoListagem<EstoqueImportacaoDTO>({
    enviar: (arquivo) => this.almoxarifado.importarEstoques(arquivo),
    confirmar: (estoques) => this.almoxarifado.confirmarImportacaoEstoques({ estoques }),
    modelo: () => this.importacaoService.baixarModeloCsv('/v1/estoques/importar/modelo', 'modelo-estoques.csv'),
    aoProcessar: () => this.lista.reload()
  });
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = CHAVE_LINHA_IMPORTACAO;
  protected readonly colunasImportacao: CesDataTableColumn<EstoqueImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'nome', header: 'Identificador', type: 'custom', priority: 1, value: (r) => r.nomeIdentificador },
    { key: 'tipo', header: 'Tipo', type: 'custom', value: (r) => r.nomeTipoEstoque },
    { key: 'equipe', header: 'Equipe', type: 'custom', value: (r) => r.codigoEquipe },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  constructor() {
    this.almoxarifado.listarTiposEstoqueAtivos().subscribe({ next: (r) => this.tipos.set(r.content ?? []), error: () => undefined });
    this.rh.listarBasesOperacionaisAtivas().subscribe({
      next: (r) => {
        this.bases.set(r.content ?? []);
        this.lembrar(Object.fromEntries((r.content ?? []).map((b) => [b.id, b.nome])));
      },
      error: () => undefined
    });
    // Nomes de equipe e base da página carregada que ainda não conhecemos.
    effect(() => {
      const linhas = this.resultado.rows();
      untracked(() => this.hidratar(linhas));
    });
  }

  private lembrar(novos: Record<string, string>): void {
    this.nomes.update((atual) => ({ ...atual, ...novos }));
  }

  private hidratar(estoques: readonly EstoqueResponse[]): void {
    const conhecidos = this.nomes();
    const equipes = new Set(estoques.map((e) => e.idEquipe).filter((id): id is string => !!id && !(id in conhecidos)));
    const bases = new Set(estoques.map((e) => e.idBaseOperacional).filter((id): id is string => !!id && !(id in conhecidos)));
    equipes.forEach((id) =>
      this.rh.buscarEquipePorId(id).pipe(catchError(() => of(null))).subscribe((e) => this.lembrar({ [id]: e?.codigo ?? 'Equipe removida' }))
    );
    bases.forEach((id) =>
      this.rh.buscarBaseOperacionalPorId(id).pipe(catchError(() => of(null))).subscribe((b) => this.lembrar({ [id]: b?.nome ?? 'Base removida' }))
    );
  }

  private filtro() {
    const { filters, query } = this.lista.request();
    return { idTipoEstoque: filters.idTipoEstoque ?? undefined, ativo: filters.ativo ?? undefined, buscaGlobal: query || undefined };
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(e: EstoqueResponse): void {
    this.preencher(e);
    this.formularioAberto.set(true);
  }

  private preencher(e: EstoqueResponse | null): void {
    this.emEdicao.set(e);
    if (e?.idEquipe) {
      this.rh.buscarEquipePorId(e.idEquipe).pipe(catchError(() => of(null))).subscribe((equipe) => equipe && this.equipes.setSeed([equipe]));
    }
    this.identificador.set(e?.nomeIdentificador ?? '');
    this.idTipo.set(e?.tipoEstoque?.id ?? null);
    this.idBase.set(e?.idBaseOperacional ?? null);
    this.idEquipe.set(e?.idEquipe ?? null);
    this.ativo.set(e?.ativo ?? true);
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const ativo = this.ativo();
    const request: EstoqueRequest = {
      nomeIdentificador: this.identificador().trim(),
      idTipoEstoque: this.idTipo()!,
      idBaseOperacional: this.idBase()!,
      idEquipe: this.idEquipe() ?? undefined
    };
    this.salvando.set(true);
    const salvar$: Observable<unknown> = atual
      ? this.almoxarifado
          .atualizarEstoque(atual.id, request)
          .pipe(switchMap(() => (ativo === atual.ativo ? of(null) : ativo ? this.almoxarifado.ativarEstoque(atual.id) : this.almoxarifado.desativarEstoque(atual.id))))
      : this.almoxarifado.criarEstoque(request);
    salvar$.subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Estoque atualizado.' : 'Estoque cadastrado.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(e: EstoqueResponse): Promise<void> {
    const desativar = e.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar estoque' : 'Ativar estoque',
      message: desativar ? `${e.nomeIdentificador} deixa de receber movimentações. O saldo e o histórico são mantidos.` : `${e.nomeIdentificador} volta a receber movimentações.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.almoxarifado.desativarEstoque(e.id) : this.almoxarifado.ativarEstoque(e.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Estoque desativado.' : 'Estoque ativado.');
        this.lista.reload();
      },
      error: (erro) => this.notificacoes.error(getErrorMessage(erro))
    });
  }

  protected logs(e: EstoqueResponse): void {
    this.auditoria().show(e.id, e.nomeIdentificador, {
      nomeIdentificador: 'Identificador',
      idTipoEstoque: 'Tipo de estoque',
      idEquipe: 'Equipe',
      ativo: 'Status'
    });
  }
}
