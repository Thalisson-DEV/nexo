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
import { Observable, of, switchMap } from 'rxjs';

import { AvaliacaoMaterialResponse, CentroInvestimentoResponse, MaterialImportacaoDTO, MaterialRequest, MaterialResponse } from '../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

const OPCOES_SERIE: CesSelectOption<boolean | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Sim', value: true },
  { label: 'Não', value: false }
];

@Component({
  selector: 'app-materiais',
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
        title="Materiais"
        icon="pi pi-box"
        [breadcrumb]="[{ label: 'Almoxarifado' }, { label: 'Materiais' }]"
        description="Catálogo de materiais: unidade, avaliação técnica, centro de investimento e controle de número de série."
        helpLabel="Ajuda sobre materiais"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacao.aberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Novo material" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="almox-materiais" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Avaliação técnica" [options]="avaliacoesOpcoes()" [(value)]="lista.filters.idAvaliacaoTecnica" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Controla nº de série" [options]="opcoesSerie" [(value)]="lista.filters.detalhaSerie"></ces-select>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Materiais"
        mode="server"
        storageKey="almox-materiais"
        exportFileName="materiais"
        searchPlaceholder="Código ou descrição"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo material"
        noResultsTitle="Nenhum material encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os materiais' : 'Nenhum material cadastrado'"
        [stateDescription]="resultado.error() ?? 'Cadastre um material ou importe uma planilha.'"
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
        <ng-template cesCell="codigo" let-row><span class="ces-cell-strong">{{ row.codigo }}</span></ng-template>
        <ng-template cesCell="serie" let-row>
          @if (row.detalhaSerie) {
            <ces-status tone="info" icon="pi-hashtag">Controla</ces-status>
          } @else {
            <span class="ces-cell-muted">Não</span>
          }
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.codigo" size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.codigo" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar material' : 'Novo material'" [description]="emEdicao()?.codigo"
      [dirty]="alterado()" size="md">
      <ces-form-section title="Material" icon="pi pi-box">
        <div class="ces-form-grid">
          <ces-text-input label="Código" [required]="true" [value]="codigo()" (valueChange)="codigo.set($event.toUpperCase())" [maxlength]="40" placeholder="Ex.: MAT001" [error]="erro('codigo')"></ces-text-input>
          <ces-text-input label="Unidade de medida" [(value)]="unidade" [maxlength]="10" placeholder="Ex.: MT" [showOptional]="true"></ces-text-input>
        </div>
        <ces-text-input label="Descrição" [required]="true" [(value)]="descricao" [maxlength]="255" placeholder="Ex.: Cabo de cobre 16mm" [error]="erro('descricao')"></ces-text-input>
        <ces-text-input label="SUPR/MATR" [(value)]="suprMatr" [maxlength]="10" placeholder="Ex.: SUPR" [showOptional]="true"></ces-text-input>
      </ces-form-section>
      <ces-form-section title="Classificação" icon="pi pi-sliders-h">
        <div class="ces-form-grid">
          <ces-select label="Avaliação técnica" [options]="avaliacoesOpcoes()" [(value)]="idAvaliacao" [filter]="true" [clearable]="true" [showOptional]="true"></ces-select>
          <ces-select label="Centro de investimento" [options]="centrosOpcoes()" [(value)]="idCentro" [filter]="true" [clearable]="true" [showOptional]="true"></ces-select>
        </div>
        <ces-boolean-control label="Controla número de série" kind="toggle" [(value)]="detalhaSerie"
          description="Exige informar o número de série em cada movimentação."></ces-boolean-control>
        <ces-boolean-control [label]="emEdicao() ? 'Material ativo' : 'Cadastrar já ativo'" kind="toggle" [(value)]="ativo"></ces-boolean-control>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar material'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer [(visible)]="importacao.aberta" title="Importar materiais"
      description="Planilha CSV com código, descrição, unidade, SUPR/MATR, avaliação, centro de investimento e se controla nº de série."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao.linhas()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="codigo" let-row>
            @if (row.editing) { <ces-text-input label="Código" [labelVisible]="false" [(value)]="row.codigo"></ces-text-input> } @else { {{ row.codigo }} }
          </ng-template>
          <ng-template cesCell="descricao" let-row>
            @if (row.editing) { <ces-text-input label="Descrição" [labelVisible]="false" [(value)]="row.descricao"></ces-text-input> } @else { {{ row.descricao }} }
          </ng-template>
          <ng-template cesCell="unidade" let-row>
            @if (row.editing) { <ces-text-input label="Unidade" [labelVisible]="false" [(value)]="row.unidadeMedida"></ces-text-input> } @else { {{ row.unidadeMedida || '—' }} }
          </ng-template>
          <ng-template cesCell="serie" let-row>
            @if (row.editing) {
              <ces-boolean-control label="Controla nº de série" [inline]="true" [(value)]="row.detalhaSerie"></ces-boolean-control>
            } @else {
              {{ row.detalhaSerie ? 'Sim' : 'Não' }}
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
    <app-ajuda-listagem #ajuda titulo="Materiais" entidadePlural="materiais" busca="código ou descrição" rotuloNovo="Novo material" [importacao]="true">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-hashtag"></i> Número de série</h4>
        <p class="help-text-desc">Materiais que controlam nº de série exigem o número em cada entrada, saída e transferência.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class MateriaisComponent {
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly importacaoService = inject(ImportacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly opcoesSerie = OPCOES_SERIE;
  protected readonly avaliacoes = signal<AvaliacaoMaterialResponse[]>([]);
  protected readonly centros = signal<CentroInvestimentoResponse[]>([]);
  protected readonly avaliacoesOpcoes = computed<CesSelectOption<string>[]>(() => this.avaliacoes().map((a) => ({ label: a.nome, value: a.id })));
  protected readonly centrosOpcoes = computed<CesSelectOption<string>[]>(() => this.centros().map((c) => ({ label: c.codigo, value: c.id })));

  protected readonly lista = cesListState({
    storageKey: 'almox-materiais',
    filters: {
      idAvaliacaoTecnica: cesFilter.value<string | null>(null, {
        label: 'Avaliação',
        param: 'avaliacao',
        format: (id): string | null => (id ? (this.avaliacoes().find((a) => a.id === id)?.nome ?? 'avaliação selecionada') : null)
      }),
      detalhaSerie: cesFilter.value<boolean | null>(null, { label: 'Nº de série', param: 'serie', options: () => OPCOES_SERIE }),
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'codigo', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<MaterialResponse>(
    (pedido) => this.almoxarifado.listarMateriais({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.almoxarifado.listarMateriais({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<MaterialResponse>[] = [
    { key: 'codigo', header: 'Código', type: 'custom', priority: 1, sortField: 'codigo', width: '9rem', value: (m) => m.codigo, display: (m) => m.codigo },
    { key: 'descricao', header: 'Descrição', priority: 1, sortField: 'descricao' },
    { key: 'unidade', header: 'Unidade', sortField: 'unidadeMedida', width: '7rem', value: (m) => m.unidadeMedida, display: (m) => m.unidadeMedida || '—' },
    { key: 'avaliacao', header: 'Avaliação', sortField: 'avaliacaoTecnica.nome', value: (m) => m.avaliacaoTecnica?.nome, display: (m) => m.avaliacaoTecnica?.nome ?? '—' },
    { key: 'serie', header: 'Nº de série', type: 'custom', sortField: 'detalhaSerie', value: (m) => m.detalhaSerie, display: (m) => (m.detalhaSerie ? 'Sim' : 'Não') },
    { key: 'ativo', header: 'Status', type: 'custom', sortField: 'ativo', value: (m) => m.ativo, display: (m) => (m.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (m: MaterialResponse) => m.codigo;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<MaterialResponse | null>(null);
  protected readonly codigo = signal('');
  protected readonly descricao = signal('');
  protected readonly unidade = signal('');
  protected readonly suprMatr = signal('');
  protected readonly idAvaliacao = signal<string | null>(null);
  protected readonly idCentro = signal<string | null>(null);
  protected readonly detalhaSerie = signal(false);
  protected readonly ativo = signal(true);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => {
    const m = this.emEdicao();
    const atual = [this.codigo(), this.descricao(), this.unidade(), this.suprMatr(), this.idAvaliacao(), this.idCentro(), this.detalhaSerie(), this.ativo()];
    const original = m
      ? [m.codigo, m.descricao, m.unidadeMedida ?? '', m.suprMatrFlag ?? '', m.avaliacaoTecnica?.id ?? null, m.centroInvestimento?.id ?? null, m.detalhaSerie, m.ativo]
      : ['', '', '', '', null, null, false, true];
    return atual.some((v, i) => v !== original[i]);
  });
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.codigo().trim()) e['codigo'] = 'Informe o código.';
    if (!this.descricao().trim()) e['descricao'] = 'Informe a descrição.';
    return e;
  });

  protected readonly importacao = importacaoListagem<MaterialImportacaoDTO>({
    enviar: (arquivo) => this.almoxarifado.importarMateriais(arquivo),
    confirmar: (materiais) => this.almoxarifado.confirmarImportacaoMateriais({ materiais }),
    modelo: () => this.importacaoService.baixarModeloCsv('/v1/materiais/importar/modelo', 'modelo-materiais.csv'),
    aoProcessar: () => this.lista.reload()
  });
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = CHAVE_LINHA_IMPORTACAO;
  protected readonly colunasImportacao: CesDataTableColumn<MaterialImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'codigo', header: 'Código', type: 'custom', priority: 1, value: (r) => r.codigo },
    { key: 'descricao', header: 'Descrição', type: 'custom', value: (r) => r.descricao },
    { key: 'unidade', header: 'Unidade', type: 'custom', value: (r) => r.unidadeMedida },
    { key: 'avaliacao', header: 'Avaliação', value: (r) => r.nomeAvaliacao, display: (r) => r.nomeAvaliacao || '—' },
    { key: 'centro', header: 'Centro de investimento', value: (r) => r.codigoCentroInvestimento, display: (r) => r.codigoCentroInvestimento || '—' },
    { key: 'serie', header: 'Nº de série', type: 'custom', value: (r) => r.detalhaSerie },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  constructor() {
    this.almoxarifado.listarAvaliacoesAtivas().subscribe({ next: (r) => this.avaliacoes.set(r.content ?? []), error: () => undefined });
    this.almoxarifado.listarCentrosInvestimentoAtivos().subscribe({ next: (r) => this.centros.set(r.content ?? []), error: () => undefined });
  }

  private filtro() {
    const { filters, query } = this.lista.request();
    return {
      idAvaliacaoTecnica: filters.idAvaliacaoTecnica ?? undefined,
      detalhaSerie: filters.detalhaSerie ?? undefined,
      ativo: filters.ativo ?? undefined,
      buscaGlobal: query || undefined
    };
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(m: MaterialResponse): void {
    this.preencher(m);
    this.formularioAberto.set(true);
  }

  private preencher(m: MaterialResponse | null): void {
    this.emEdicao.set(m);
    this.codigo.set(m?.codigo ?? '');
    this.descricao.set(m?.descricao ?? '');
    this.unidade.set(m?.unidadeMedida ?? '');
    this.suprMatr.set(m?.suprMatrFlag ?? '');
    this.idAvaliacao.set(m?.avaliacaoTecnica?.id ?? null);
    this.idCentro.set(m?.centroInvestimento?.id ?? null);
    this.detalhaSerie.set(m?.detalhaSerie ?? false);
    this.ativo.set(m?.ativo ?? true);
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const ativo = this.ativo();
    const request: MaterialRequest = {
      codigo: this.codigo().trim(),
      descricao: this.descricao().trim(),
      unidadeMedida: this.unidade().trim() || undefined,
      suprMatrFlag: this.suprMatr().trim() || undefined,
      idAvaliacaoTecnica: this.idAvaliacao() ?? undefined,
      idCentroInvestimento: this.idCentro() ?? undefined,
      detalhaSerie: this.detalhaSerie()
    };
    this.salvando.set(true);
    const alternar = (id: string, era: boolean): Observable<unknown> =>
      era === ativo ? of(null) : ativo ? this.almoxarifado.ativarMaterial(id) : this.almoxarifado.desativarMaterial(id);
    const salvar$: Observable<unknown> = atual
      ? this.almoxarifado.atualizarMaterial(atual.id, request).pipe(switchMap(() => alternar(atual.id, atual.ativo)))
      : this.almoxarifado.criarMaterial(request).pipe(switchMap((criado) => (criado ? alternar(criado.id, true) : of(null))));
    salvar$.subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Material atualizado.' : 'Material cadastrado.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(m: MaterialResponse): Promise<void> {
    const desativar = m.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar material' : 'Ativar material',
      message: desativar ? `${m.codigo} deixa de aparecer nas seleções. O saldo e o histórico são mantidos.` : `${m.codigo} volta a aparecer nas seleções.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.almoxarifado.desativarMaterial(m.id) : this.almoxarifado.ativarMaterial(m.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Material desativado.' : 'Material ativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(m: MaterialResponse): void {
    this.auditoria().show(m.id, m.codigo, {
      codigo: 'Código',
      descricao: 'Descrição',
      unidadeMedida: 'Unidade de medida',
      suprMatrFlag: 'SUPR/MATR',
      detalhaSerie: 'Controla nº de série',
      ativo: 'Status'
    });
  }
}
