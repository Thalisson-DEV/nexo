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
  CesNumberInputComponent,
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

import { BaseOperacionalImportacaoDTO, BaseOperacionalResponse, CentroFaturamentoResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

@Component({
  selector: 'app-bases-operacionais',
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
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novo()">
      <ces-page-header
        title="Bases operacionais"
        icon="pi pi-building"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Cadastros' }, { label: 'Bases' }]"
        description="Bases de onde as equipes operam, com o centro de custo e a área de atuação (coordenadas e raio)."
        helpLabel="Ajuda sobre bases operacionais"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacao.aberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Nova base" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="rh-bases" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Centro de custo" [options]="centrosOpcoes()" [(value)]="lista.filters.idCentroFaturamento" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Bases operacionais"
        mode="server"
        storageKey="rh-bases"
        exportFileName="bases-operacionais"
        searchPlaceholder="Nome da base"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Nova base"
        noResultsTitle="Nenhuma base encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as bases' : 'Nenhuma base cadastrada'"
        [stateDescription]="resultado.error() ?? 'Cadastre uma base ou importe uma planilha.'"
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
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativa' : 'Inativa' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.nome" size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.nome" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar base' : 'Nova base'" [description]="emEdicao()?.nome"
      [dirty]="alterado()" size="md">
      <ces-form-section title="Dados da base" icon="pi pi-building">
        <ces-text-input label="Nome" [required]="true" [(value)]="nome" [maxlength]="120" placeholder="Ex.: Juazeiro" [error]="erro('nome')"></ces-text-input>
        <ces-select label="Centro de custo" [required]="true" [options]="centrosOpcoes()" [(value)]="idCentro" [filter]="true" [error]="erro('centro')"></ces-select>
        <ces-boolean-control label="Base ativa" kind="toggle" [(value)]="ativo"></ces-boolean-control>
      </ces-form-section>
      <ces-form-section title="Área de atuação" icon="pi pi-map-marker" description="Usada para conferir a localização dos apontamentos de campo.">
        <div class="ces-form-grid">
          <ces-number-input label="Latitude" [(value)]="latitude" [minFractionDigits]="6" [maxFractionDigits]="10" [showOptional]="true"></ces-number-input>
          <ces-number-input label="Longitude" [(value)]="longitude" [minFractionDigits]="6" [maxFractionDigits]="10" [showOptional]="true"></ces-number-input>
          <ces-number-input label="Raio (metros)" [(value)]="raio" [min]="0" [useGrouping]="false" [showOptional]="true"></ces-number-input>
        </div>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar base'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer [(visible)]="importacao.aberta" title="Importar bases operacionais" description="Planilha CSV com o nome da base e o centro de custo."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao.linhas()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="nome" let-row>
            @if (row.editing) {
              <ces-text-input label="Nome" [labelVisible]="false" [(value)]="row.nome"></ces-text-input>
            } @else {
              {{ row.nome }}
            }
          </ng-template>
          <ng-template cesCell="centro" let-row>
            @if (row.editing) {
              <ces-select label="Centro de custo" [labelVisible]="false" [options]="centrosOpcoes()" [value]="row.centroCustoId ?? null"
                (valueChange)="definirCentroImportado(row, $event)" [filter]="true"></ces-select>
            } @else {
              {{ nomeCentro(row.centroCustoId) || row.nomeCentroFaturamento || '—' }}
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
    <app-ajuda-listagem #ajuda titulo="Bases operacionais" entidadePlural="bases" artigoPlural="as" busca="nome da base"
      rotuloNovo="Nova base" [importacao]="true" video="https://www.youtube.com/embed/YzI79KE28OI"></app-ajuda-listagem>
  `
})
export class BasesOperacionaisComponent {
  private readonly rh = inject(RhService);
  private readonly importacaoService = inject(ImportacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly centros = signal<CentroFaturamentoResponse[]>([]);
  protected readonly centrosOpcoes = computed<CesSelectOption<string>[]>(() => this.centros().map((c) => ({ label: c.nome, value: c.id })));

  protected readonly lista = cesListState({
    storageKey: 'rh-bases',
    filters: {
      idCentroFaturamento: cesFilter.value<string | null>(null, { label: 'Centro de custo', param: 'centro', format: (id): string | null => (id ? this.nomeCentro(id) : null) }),
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'nome', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<BaseOperacionalResponse>(
    (pedido) => this.rh.listarBasesOperacionais({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.rh.listarBasesOperacionais({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<BaseOperacionalResponse>[] = [
    { key: 'nome', header: 'Nome', priority: 1, sortField: 'nome' },
    { key: 'centro', header: 'Centro de custo', value: (b) => b.centroFaturamento?.nome, display: (b) => b.centroFaturamento?.nome ?? '—' },
    { key: 'raio', header: 'Raio de atuação', value: (b) => b.raio, display: (b) => (b.raio != null ? `${b.raio} m` : '—') },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, sortField: 'ativo', value: (b) => b.ativo, display: (b) => (b.ativo ? 'Ativa' : 'Inativa') }
  ];
  protected readonly rotulo = (b: BaseOperacionalResponse) => b.nome;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<BaseOperacionalResponse | null>(null);
  protected readonly nome = signal('');
  protected readonly idCentro = signal<string | null>(null);
  protected readonly latitude = signal<number | null>(null);
  protected readonly longitude = signal<number | null>(null);
  protected readonly raio = signal<number | null>(null);
  protected readonly ativo = signal(true);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => {
    const b = this.emEdicao();
    if (!b) return !!this.nome().trim() || !!this.idCentro();
    return (
      this.nome() !== b.nome || this.idCentro() !== (b.centroFaturamento?.id ?? null) || this.latitude() !== (b.latitude ?? null) ||
      this.longitude() !== (b.longitude ?? null) || this.raio() !== (b.raio ?? null) || this.ativo() !== b.ativo
    );
  });
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.nome().trim()) e['nome'] = 'Informe o nome da base.';
    if (!this.idCentro()) e['centro'] = 'Escolha o centro de custo.';
    return e;
  });

  protected readonly importacao = importacaoListagem<BaseOperacionalImportacaoDTO>({
    enviar: (arquivo) => this.rh.importarBasesOperacionais(arquivo),
    confirmar: (bases) => this.rh.confirmarImportacaoBases({ bases }),
    modelo: () => this.importacaoService.baixarModeloCsv('/v1/bases-operacionais/importar/modelo', 'modelo-bases-operacionais.csv'),
    aoProcessar: () => this.lista.reload()
  });
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = CHAVE_LINHA_IMPORTACAO;
  protected readonly colunasImportacao: CesDataTableColumn<BaseOperacionalImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'nome', header: 'Nome', type: 'custom', priority: 1, value: (r) => r.nome },
    { key: 'centro', header: 'Centro de custo', type: 'custom', value: (r) => r.nomeCentroFaturamento },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  constructor() {
    this.rh.listarCentrosFaturamentoAtivos().subscribe({ next: (r) => this.centros.set(r.content), error: () => undefined });
  }

  private filtro() {
    const { filters, query } = this.lista.request();
    return { ...filters, buscaGlobal: query || undefined };
  }

  protected nomeCentro(id?: string | null): string {
    return (id && this.centros().find((c) => c.id === id)?.nome) || '';
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(base: BaseOperacionalResponse): void {
    this.preencher(base);
    this.formularioAberto.set(true);
  }

  private preencher(b: BaseOperacionalResponse | null): void {
    this.emEdicao.set(b);
    this.nome.set(b?.nome ?? '');
    this.idCentro.set(b?.centroFaturamento?.id ?? null);
    this.latitude.set(b?.latitude ?? null);
    this.longitude.set(b?.longitude ?? null);
    this.raio.set(b?.raio ?? null);
    this.ativo.set(b?.ativo ?? true);
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const request = {
      nome: this.nome().trim(),
      centroCustoId: this.idCentro()!,
      // `null` limpa o valor no servidor (mesmo comportamento do formulário anterior).
      latitude: this.latitude() as number | undefined,
      longitude: this.longitude() as number | undefined,
      raio: this.raio() as number | undefined
    };
    const ativo = this.ativo();
    this.salvando.set(true);
    // O status é alterado por ativar/desativar depois de gravar os dados.
    const salvar$: Observable<unknown> = atual
      ? this.rh.atualizarBaseOperacional(atual.id, request).pipe(
          switchMap(() => (atual.ativo === ativo ? of(null) : ativo ? this.rh.ativarBaseOperacional(atual.id) : this.rh.desativarBaseOperacional(atual.id)))
        )
      : this.rh.criarBaseOperacional(request);
    salvar$.subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Base atualizada.' : 'Base cadastrada.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(base: BaseOperacionalResponse): Promise<void> {
    const desativar = base.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar base' : 'Ativar base',
      message: desativar ? `${base.nome} deixa de aparecer nas seleções. O histórico é mantido.` : `${base.nome} volta a aparecer nas seleções.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.rh.desativarBaseOperacional(base.id) : this.rh.ativarBaseOperacional(base.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Base desativada.' : 'Base ativada.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(base: BaseOperacionalResponse): void {
    this.auditoria().show(base.id, base.nome, {
      nome: 'Nome da base',
      ativo: 'Status (ativo)',
      centroCustoId: 'Centro de custo',
      centroFaturamento: 'Centro de custo',
      dataDesativacao: 'Data de desativação'
    });
  }

  protected definirCentroImportado(linha: BaseOperacionalImportacaoDTO, id: string | null): void {
    linha.centroCustoId = id ?? undefined;
    linha.nomeCentroFaturamento = this.nomeCentro(id);
  }
}
