import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CesAutocompleteComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
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
import { map } from 'rxjs';

import { ColaboradorResponse, CoordenadorMinResponse, SupervisorImportacaoDTO, SupervisorRequest, SupervisorResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { CoordenadorService } from '../../../core/services/coordenador.service';
import { SupervisorService } from '../../../core/services/supervisor.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, dataIso, dataLocal, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';
import { formatarCpf } from '../colaboradores/colaboradores.component';
import { buscaColaboradores, resumoColaborador, rotuloColaborador, situacaoVigencia } from '../shared/vigencia';

@Component({
  selector: 'app-supervisores',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    CesAutocompleteComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
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
        title="Supervisores"
        icon="pi pi-users"
        [breadcrumb]="[{ label: 'Departamento Pessoal' }, { label: 'Cadastros' }, { label: 'Supervisores' }]"
        description="Colaboradores designados como supervisores, o coordenador a que respondem e o período de vigência."
        helpLabel="Ajuda sobre supervisores"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacao.aberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Novo supervisor" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="rh-supervisores" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Coordenador" [options]="coordenadoresOpcoes()" [(value)]="lista.filters.idCoordenador" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-datepicker label="Início a partir de" [value]="dataLocal(lista.filters.inicio())" [clearable]="true"
            (valueChange)="lista.filters.inicio.set(dataIso($event))"></ces-datepicker>
          <ces-datepicker label="Fim até" [value]="dataLocal(lista.filters.fim())" [clearable]="true"
            (valueChange)="lista.filters.fim.set(dataIso($event))"></ces-datepicker>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Supervisores"
        mode="server"
        storageKey="rh-supervisores"
        exportFileName="supervisores"
        searchPlaceholder="Nome do supervisor"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo supervisor"
        noResultsTitle="Nenhum supervisor encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os supervisores' : 'Nenhum supervisor cadastrado'"
        [stateDescription]="resultado.error() ?? 'Designe um colaborador como supervisor ou importe uma planilha.'"
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
          <span class="ces-cell-strong">{{ row.colaborador?.nomeCompleto }}</span>
          <span class="ces-cell-muted">{{ formatarCpf(row.colaborador?.cpf) }}</span>
        </ng-template>
        <ng-template cesCell="situacao" let-row>
          <ces-status [tone]="vigencia(row).tone">{{ vigencia(row).label }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.colaborador?.nomeCompleto" size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.colaborador?.nomeCompleto" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar supervisor' : 'Novo supervisor'"
      [description]="emEdicao()?.colaborador?.nomeCompleto" [dirty]="alterado()" size="md">
      <ces-form-section title="Colaborador" icon="pi pi-user">
        <ces-autocomplete label="Colaborador" [required]="true" placeholder="Digite o nome" [options]="colaboradoresLookup.items()"
          [ngModel]="idColaborador()" (ngModelChange)="selecionar($event)" [loading]="colaboradoresLookup.loading()"
          [status]="colaboradoresLookup.status()" [clearable]="true" [error]="erro('colaborador')"
          (search)="colaboradoresLookup.search($event)" (loadMore)="colaboradoresLookup.loadNext()" (retry)="colaboradoresLookup.retry()"></ces-autocomplete>
        @if (colaborador(); as c) {
          <ces-callout tone="info" [title]="c.nomeCompleto">
            {{ resumoColaborador(c) }}
          </ces-callout>
        }
      </ces-form-section>
      <ces-form-section title="Coordenação" icon="pi pi-sitemap">
        <ces-select label="Coordenador" [required]="true" [options]="coordenadoresOpcoes()" [(value)]="idCoordenador" [filter]="true"
          description="A quem este supervisor responde." [error]="erro('coordenador')"></ces-select>
      </ces-form-section>
      <ces-form-section title="Vigência" icon="pi pi-calendar">
        <div class="ces-form-grid">
          <ces-datepicker label="Início" [required]="true" [(value)]="inicio" [error]="erro('inicio')"></ces-datepicker>
          <ces-datepicker label="Fim" [(value)]="fim" [clearable]="true" [showOptional]="true" [minDate]="inicio()"
            description="Em branco = vigente sem data de término." [error]="erro('fim')"></ces-datepicker>
        </div>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar supervisor'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer [(visible)]="importacao.aberta" title="Importar supervisores"
      description="Planilha CSV com CPF ou nome do supervisor e do coordenador, início e fim da vigência."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao.linhas()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="cpf" let-row>
            @if (row.editing) { <ces-text-input label="CPF" [labelVisible]="false" [(value)]="row.cpfColaborador"></ces-text-input> } @else { {{ row.cpfColaborador || '—' }} }
          </ng-template>
          <ng-template cesCell="nome" let-row>
            @if (row.editing) { <ces-text-input label="Nome" [labelVisible]="false" [(value)]="row.nomeColaborador"></ces-text-input> } @else { {{ row.nomeColaborador || '—' }} }
          </ng-template>
          <ng-template cesCell="cpfCoordenador" let-row>
            @if (row.editing) { <ces-text-input label="CPF do coordenador" [labelVisible]="false" [(value)]="row.cpfCoordenador"></ces-text-input> } @else { {{ row.cpfCoordenador || '—' }} }
          </ng-template>
          <ng-template cesCell="nomeCoordenador" let-row>
            @if (row.editing) { <ces-text-input label="Coordenador" [labelVisible]="false" [(value)]="row.nomeCoordenador"></ces-text-input> } @else { {{ row.nomeCoordenador || '—' }} }
          </ng-template>
          <ng-template cesCell="inicio" let-row>
            @if (row.editing) { <ces-text-input label="Início" [labelVisible]="false" [(value)]="row.dataInicio" placeholder="dd/mm/aaaa"></ces-text-input> } @else { {{ row.dataInicio }} }
          </ng-template>
          <ng-template cesCell="fim" let-row>
            @if (row.editing) { <ces-text-input label="Fim" [labelVisible]="false" [(value)]="row.dataFim" placeholder="Opcional"></ces-text-input> } @else { {{ row.dataFim || '—' }} }
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
    <app-ajuda-listagem #ajuda titulo="Supervisores" entidadePlural="supervisores" busca="nome do supervisor" rotuloNovo="Novo supervisor" [importacao]="true">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-calendar"></i> Vigência</h4>
        <p class="help-text-desc">Sem data de fim, a designação fica vigente. Com data de fim no passado, aparece como encerrada; o histórico é mantido.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class SupervisoresComponent {
  private readonly supervisores = inject(SupervisorService);
  private readonly coordenadoresService = inject(CoordenadorService);
  private readonly rh = inject(RhService);
  private readonly importacaoService = inject(ImportacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly formatarCpf = formatarCpf;
  protected readonly vigencia = situacaoVigencia;
  protected readonly resumoColaborador = resumoColaborador;
  protected readonly dataLocal = dataLocal;
  protected readonly dataIso = dataIso;
  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly coordenadoresAtivos = signal<CoordenadorMinResponse[]>([]);
  protected readonly coordenadoresOpcoes = computed<CesSelectOption<string>[]>(() => this.coordenadoresAtivos().map((c) => ({ label: c.nomeColaborador, value: c.id })));

  protected readonly lista = cesListState({
    storageKey: 'rh-supervisores',
    filters: {
      idCoordenador: cesFilter.value<string | null>(null, { label: 'Coordenador', param: 'coordenador', format: (id): string | null => (id ? this.nomeCoordenador(id) : null) }),
      inicio: cesFilter.date(null, { label: 'Início a partir de' }),
      fim: cesFilter.date(null, { label: 'Fim até' }),
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'inicio', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<SupervisorResponse>(
    (pedido) => {
      const { page, size, sort } = paginaPadrao(pedido);
      return this.supervisores.listar(this.filtro(), page, size, sort as string | undefined);
    },
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao(
    ({ page, size, sort }) => this.supervisores.listar(this.filtro(), page, size, sort as string | undefined),
    () => this.lista.request()
  );

  protected readonly colunas: CesDataTableColumn<SupervisorResponse>[] = [
    { key: 'nome', header: 'Supervisor', type: 'custom', priority: 1, sortField: 'colaborador.nomeCompleto', value: (c) => c.colaborador?.nomeCompleto, display: (c) => c.colaborador?.nomeCompleto ?? '' },
    { key: 'coordenador', header: 'Coordenador', priority: 1, sortField: 'coordenador.colaborador.nomeCompleto', value: (c) => c.coordenador?.colaborador?.nomeCompleto, display: (c) => c.coordenador?.colaborador?.nomeCompleto ?? '—' },
    { key: 'base', header: 'Base', value: (c) => c.colaborador?.baseOperacional?.nome, display: (c) => c.colaborador?.baseOperacional?.nome ?? '—' },
    { key: 'funcao', header: 'Função', value: (c) => c.colaborador?.funcao?.nome, display: (c) => c.colaborador?.funcao?.nome ?? '—' },
    { key: 'inicio', header: 'Início', type: 'date', sortField: 'dataInicio', value: (c) => c.dataInicio },
    { key: 'fim', header: 'Fim', type: 'date', sortField: 'dataFim', value: (c) => c.dataFim },
    { key: 'situacao', header: 'Situação', type: 'custom', priority: 1, sortField: 'ativo', value: (c) => c.ativo, display: (c) => situacaoVigencia(c).label }
  ];
  protected readonly rotulo = (c: SupervisorResponse) => c.colaborador?.nomeCompleto ?? 'supervisor';

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<SupervisorResponse | null>(null);
  protected readonly idColaborador = signal<string | null>(null);
  protected readonly idCoordenador = signal<string | null>(null);
  protected readonly colaborador = signal<ColaboradorResponse | null>(null);
  protected readonly inicio = signal<Date | null>(null);
  protected readonly fim = signal<Date | null>(null);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly colaboradoresLookup = buscaColaboradores(() => this.idColaborador());

  protected readonly alterado = computed(() => {
    const c = this.emEdicao();
    if (!c) return !!this.idColaborador() || !!this.idCoordenador() || !!this.fim();
    return this.idColaborador() !== c.colaborador?.id || this.idCoordenador() !== (c.coordenador?.id ?? null) || dataIso(this.inicio()) !== c.dataInicio || dataIso(this.fim()) !== (c.dataFim ?? null);
  });
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.idColaborador()) e['colaborador'] = 'Escolha o colaborador.';
    if (!this.idCoordenador()) e['coordenador'] = 'Escolha o coordenador.';
    if (!this.inicio()) e['inicio'] = 'Informe o início da vigência.';
    const inicio = this.inicio();
    const fim = this.fim();
    if (inicio && fim && fim < inicio) e['fim'] = 'O fim não pode ser antes do início.';
    return e;
  });

  protected readonly importacao = importacaoListagem<SupervisorImportacaoDTO>({
    enviar: (arquivo) => this.supervisores.validarImportacao(arquivo).pipe(map((r) => ({ registros: r.registros, totalFalha: r.totalFalhas }))),
    confirmar: (supervisores) => this.supervisores.confirmarImportacao({ supervisores }).pipe(map((r) => ({ registros: r.registros, totalFalha: r.totalFalhas }))),
    modelo: () => this.importacaoService.baixarModeloCsv('/v1/rh/supervisores/importar/modelo', 'modelo-supervisores.csv'),
    aoProcessar: () => this.lista.reload()
  });
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = CHAVE_LINHA_IMPORTACAO;
  protected readonly colunasImportacao: CesDataTableColumn<SupervisorImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'cpf', header: 'CPF', type: 'custom', value: (r) => r.cpfColaborador },
    { key: 'nome', header: 'Supervisor', type: 'custom', priority: 1, value: (r) => r.nomeColaborador },
    { key: 'cpfCoordenador', header: 'CPF do coordenador', type: 'custom', value: (r) => r.cpfCoordenador },
    { key: 'nomeCoordenador', header: 'Coordenador', type: 'custom', value: (r) => r.nomeCoordenador },
    { key: 'inicio', header: 'Início', type: 'custom', value: (r) => r.dataInicio },
    { key: 'fim', header: 'Fim', type: 'custom', value: (r) => r.dataFim },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  private filtro() {
    const { filters, query } = this.lista.request();
    return { idCoordenador: filters.idCoordenador ?? undefined, dataInicio: filters.inicio ?? undefined, dataFim: filters.fim ?? undefined, ativo: filters.ativo, buscaGlobal: query || undefined };
  }

  constructor() {
    this.coordenadoresService.listarAtivosMin().subscribe({ next: (c) => this.coordenadoresAtivos.set(c), error: () => undefined });
  }

  private nomeCoordenador(id: string): string {
    return this.coordenadoresAtivos().find((c) => c.id === id)?.nomeColaborador ?? 'coordenador selecionado';
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected selecionar(id: string | null): void {
    this.idColaborador.set(id);
    this.colaborador.set(null);
    if (!id) return;
    this.rh.buscarColaboradorPorId(id).subscribe({
      next: (c) => {
        if (this.idColaborador() === c.id) this.colaborador.set(c);
      },
      error: () => undefined
    });
  }

  protected novo(): void {
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(c: SupervisorResponse): void {
    this.preencher(c);
    this.formularioAberto.set(true);
  }

  private preencher(c: SupervisorResponse | null): void {
    this.emEdicao.set(c);
    if (c?.colaborador) this.colaboradoresLookup.seedSelected(rotuloColaborador(c.colaborador));
    this.idColaborador.set(c?.colaborador?.id ?? null);
    this.colaborador.set(c?.colaborador ?? null);
    this.idCoordenador.set(c?.coordenador?.id ?? null);
    this.inicio.set(c ? dataLocal(c.dataInicio) : new Date(new Date().setHours(0, 0, 0, 0)));
    this.fim.set(dataLocal(c?.dataFim));
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const request: SupervisorRequest = { idColaborador: this.idColaborador()!, idCoordenador: this.idCoordenador()!, dataInicio: dataIso(this.inicio())!, dataFim: dataIso(this.fim()) };
    this.salvando.set(true);
    (atual ? this.supervisores.atualizar(atual.id, request) : this.supervisores.criar(request)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(atual ? 'Supervisor atualizado.' : 'Supervisor cadastrado.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(c: SupervisorResponse): Promise<void> {
    const desativar = c.ativo;
    const nome = c.colaborador?.nomeCompleto ?? 'O supervisor';
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar supervisor' : 'Ativar supervisor',
      message: desativar ? `${nome} deixa de aparecer nas seleções. O histórico é mantido.` : `${nome} volta a aparecer nas seleções.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.supervisores.desativar(c.id) : this.supervisores.ativar(c.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Supervisor desativado.' : 'Supervisor ativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(c: SupervisorResponse): void {
    this.auditoria().show(c.id, c.colaborador?.nomeCompleto ?? 'Supervisor', {
      colaborador: 'Colaborador',
      coordenador: 'Coordenador',
      dataInicio: 'Início da vigência',
      dataFim: 'Fim da vigência',
      ativo: 'Status (ativo)',
      dataDesativacao: 'Data de desativação'
    });
  }
}
