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
  CesImportDrawerComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesFilter,
  cesImportStatus,
  cesListState
} from '@ces/ui';

import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { HelpDialogComponent } from '../../../shared/components/help-dialog/help-dialog.component';
import { RhService } from '../../../core/services/rh.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { BaseOperacionalResponse, MunicipioImportacaoDTO, MunicipioResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

@Component({
  selector: 'app-municipios',
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
    CesImportDrawerComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AuditDialogComponent,
    HelpDialogComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novo()">
      <ces-page-header
        title="Municípios"
        icon="pi pi-map"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Cadastros' }, { label: 'Municípios' }]"
        description="Municípios atendidos e a base operacional responsável por cada um."
        helpLabel="Ajuda sobre municípios"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacaoAberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Novo município" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel
        #painel
        storageKey="rh-municipios"
        [activeCount]="lista.activeCount()"
        [summary]="lista.summary()"
        [loading]="resultado.loading()"
        (applied)="lista.apply()"
        (cleared)="lista.reset()"
        (chipRemoved)="lista.remove($any($event))"
      >
        <div class="ces-filter-grid">
          <ces-select label="Base operacional" [options]="basesOpcoes()" [(value)]="lista.filters.idBaseOperacional" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Municípios"
        mode="server"
        storageKey="rh-municipios"
        exportFileName="municipios"
        searchPlaceholder="Nome do município"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo município"
        noResultsTitle="Nenhum município encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os municípios' : 'Nenhum município cadastrado'"
        [stateDescription]="resultado.error() ?? 'Cadastre um município ou importe uma planilha.'"
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
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.nome"
              size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.nome" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar município' : 'Novo município'"
      [description]="emEdicao()?.nome" [dirty]="alterado()" size="sm">
      <ces-form-section title="Dados do município" icon="pi pi-map">
        <ces-text-input label="Nome" [required]="true" [(value)]="nome" [maxlength]="120" placeholder="Ex.: Salvador" [error]="erro('nome')"></ces-text-input>
        <ces-select label="Base operacional" [required]="true" [options]="basesOpcoes()" [(value)]="idBase" [filter]="true" [error]="erro('base')"></ces-select>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar município'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer
      [(visible)]="importacaoAberta"
      title="Importar municípios"
      description="Planilha CSV com o nome do município e a base operacional."
      [rows]="importacao()"
      [uploading]="enviando()"
      [processing]="processando()"
      (templateRequested)="baixarModelo()"
      (fileSelected)="enviarArquivo($event)"
      (processed)="processarImportacao()"
      (cleared)="importacao.set([])"
    >
      @if (importacao().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="nome" let-row>
            @if (row.editing) {
              <ces-text-input label="Nome" [labelVisible]="false" [(value)]="row.nome"></ces-text-input>
            } @else {
              {{ row.nome }}
            }
          </ng-template>
          <ng-template cesCell="base" let-row>
            @if (row.editing) {
              <ces-select label="Base operacional" [labelVisible]="false" [options]="basesOpcoes()" [value]="row.idBaseOperacional ?? null"
                (valueChange)="definirBaseImportada(row, $event)" [filter]="true"></ces-select>
            } @else {
              {{ nomeBase(row.idBaseOperacional) || row.nomeBaseOperacional || '—' }}
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
                <ces-icon-button icon="pi pi-check" [label]="'Concluir correção da linha ' + row.linha" size="icon-sm" (pressed)="concluirCorrecao(row)"></ces-icon-button>
              } @else {
                <ces-icon-button icon="pi pi-pencil" [label]="'Corrigir linha ' + row.linha" size="icon-sm" (pressed)="row.editing = true"></ces-icon-button>
                <ces-icon-button icon="pi pi-trash" [label]="'Remover linha ' + row.linha" size="icon-sm" (pressed)="removerLinha(row)"></ces-icon-button>
              }
            </span>
          </ng-template>
        </ces-data-table>
      }
    </ces-import-drawer>

    <app-audit-dialog #auditoria></app-audit-dialog>
    <app-help-dialog #ajuda title="Ajuda: Municípios">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-search"></i> Encontrar um município</h4>
        <p class="help-text-desc">A lista já abre com todos os municípios. Digite parte do nome na busca da tabela; para filtrar por base ou status, abra <strong>Filtros</strong> (ou tecle F). Os filtros aplicados aparecem como etiquetas e podem ser removidos no "x".</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-plus-circle"></i> Cadastrar e editar</h4>
        <p class="help-text-desc"><strong>Novo município</strong> (ou tecla N) abre o formulário ao lado. Clique em <strong>Editar</strong> na linha para alterar. Nome e base operacional são obrigatórios.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-table"></i> Ações da linha</h4>
        <p class="help-text-desc">Desativar/ativar pede confirmação. O relógio mostra o histórico de alterações. <strong>Exportar CSV</strong> baixa o recorte atual.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-upload"></i> Importar planilha</h4>
        <p class="help-text-desc"><strong>Importar</strong> abre o painel de importação: baixe o modelo, escolha o arquivo, corrija as linhas com erro e processe. A lista continua visível por trás.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-video"></i> Vídeo tutorial</h4>
        <iframe width="100%" height="215" src="https://www.youtube.com/embed/LMtuqa4SVzE" title="Vídeo tutorial de municípios" frameborder="0"
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
          referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>
      </div>
    </app-help-dialog>
  `
})
export class MunicipiosComponent {
  private readonly rh = inject(RhService);
  private readonly importacaoService = inject(ImportacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly bases = signal<BaseOperacionalResponse[]>([]);
  protected readonly basesOpcoes = computed<CesSelectOption<string>[]>(() => this.bases().map((b) => ({ label: b.nome, value: b.id })));
  protected readonly statusOpcoes = OPCOES_STATUS;

  protected readonly lista = cesListState({
    storageKey: 'rh-municipios',
    filters: {
      idBaseOperacional: cesFilter.value<string | null>(null, { label: 'Base', param: 'base', format: (id) => (id ? this.nomeBase(id) : null) }),
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'nome', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<MunicipioResponse>(
    (pedido) => this.rh.listarMunicipios({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.rh.listarMunicipios({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<MunicipioResponse>[] = [
    { key: 'nome', header: 'Nome', priority: 1, sortField: 'nome' },
    { key: 'base', header: 'Base operacional', sortField: 'baseOperacional.nome', value: (m) => m.baseOperacional?.nome, display: (m) => m.baseOperacional?.nome ?? '—' },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, sortField: 'ativo', value: (m) => m.ativo, display: (m) => (m.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (m: MunicipioResponse) => m.nome;

  // Formulário
  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<MunicipioResponse | null>(null);
  protected readonly nome = signal('');
  protected readonly idBase = signal<string | null>(null);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() =>
    this.emEdicao() ? this.nome() !== this.emEdicao()!.nome || this.idBase() !== this.emEdicao()!.baseOperacional?.id : !!this.nome().trim() || !!this.idBase()
  );
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.nome().trim()) e['nome'] = 'Informe o nome do município.';
    if (!this.idBase()) e['base'] = 'Escolha a base operacional.';
    return e;
  });

  // Importação
  protected readonly importacaoAberta = signal(false);
  protected readonly importacao = signal<MunicipioImportacaoDTO[]>([]);
  protected readonly enviando = signal(false);
  protected readonly processando = signal(false);
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = (r: MunicipioImportacaoDTO, i: number) => String(r.linha ?? i);
  protected readonly colunasImportacao: CesDataTableColumn<MunicipioImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'nome', header: 'Nome', type: 'custom', priority: 1, value: (r) => r.nome },
    { key: 'base', header: 'Base operacional', type: 'custom', value: (r) => r.nomeBaseOperacional },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  constructor() {
    this.rh.listarBasesOperacionaisAtivas().subscribe({ next: (r) => this.bases.set(r.content), error: () => undefined });
  }

  private filtro() {
    const { filters, query } = this.lista.request();
    return { ...filters, buscaGlobal: query || undefined };
  }

  protected nomeBase(id?: string | null): string {
    return (id && this.bases().find((b) => b.id === id)?.nome) || '';
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    this.emEdicao.set(null);
    this.nome.set('');
    this.idBase.set(null);
    this.tentouSalvar.set(false);
    this.formularioAberto.set(true);
  }

  protected editar(municipio: MunicipioResponse): void {
    this.emEdicao.set(municipio);
    this.nome.set(municipio.nome);
    this.idBase.set(municipio.baseOperacional?.id ?? null);
    this.tentouSalvar.set(false);
    this.formularioAberto.set(true);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const request = { nome: this.nome().trim(), idBaseOperacional: this.idBase()! };
    const id = this.emEdicao()?.id;
    this.salvando.set(true);
    (id ? this.rh.atualizarMunicipio(id, request) : this.rh.criarMunicipio(request)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(id ? 'Município atualizado.' : 'Município cadastrado.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(municipio: MunicipioResponse): Promise<void> {
    const desativar = municipio.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar município' : 'Ativar município',
      message: desativar ? `${municipio.nome} deixa de aparecer nas seleções. O histórico é mantido.` : `${municipio.nome} volta a aparecer nas seleções.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.rh.desativarMunicipio(municipio.id) : this.rh.ativarMunicipio(municipio.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Município desativado.' : 'Município ativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(municipio: MunicipioResponse): void {
    this.auditoria().show(municipio.id, municipio.nome, {
      nome: 'Nome do município',
      ativo: 'Status (ativo)',
      idBaseOperacional: 'Base operacional',
      dataDesativacao: 'Data de desativação'
    });
  }

  protected baixarModelo(): void {
    this.importacaoService.baixarModeloCsv('/v1/municipios/importar/modelo', 'modelo-municipios.csv');
  }

  protected enviarArquivo(arquivo: File): void {
    this.enviando.set(true);
    this.rh.importarMunicipios(arquivo).subscribe({
      next: (r) => {
        this.enviando.set(false);
        this.importacao.set(r.registros);
        if (r.totalFalha > 0) this.notificacoes.warning(`${r.totalFalha} linhas têm erro e precisam de correção.`);
      },
      error: (e) => {
        this.enviando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async processarImportacao(): Promise<void> {
    const registros = this.importacao().map(({ editing, ...resto }) => resto);
    if (!registros.length) return;
    const ok = await this.confirmacao.confirm({
      title: 'Processar importação',
      message: `Processar ${registros.length} linhas conferidas?`,
      confirmLabel: 'Processar'
    });
    if (!ok) return;
    this.processando.set(true);
    this.rh.confirmarImportacaoMunicipios({ municipios: registros }).subscribe({
      next: (r) => {
        this.processando.set(false);
        this.importacao.set(r.registros ?? []);
        if (r.totalFalha > 0) this.notificacoes.warning(`${r.totalFalha} linhas ainda têm erro.`);
        else this.notificacoes.success('Linhas enviadas para processamento.');
        this.lista.reload();
      },
      error: (e) => {
        this.processando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected definirBaseImportada(linha: MunicipioImportacaoDTO, id: string | null): void {
    linha.idBaseOperacional = id ?? undefined;
    linha.nomeBaseOperacional = this.nomeBase(id);
  }

  protected concluirCorrecao(linha: MunicipioImportacaoDTO): void {
    linha.editing = false;
    this.importacao.set([...this.importacao()]);
  }

  protected removerLinha(linha: MunicipioImportacaoDTO): void {
    this.importacao.set(this.importacao().filter((r) => r !== linha));
  }
}
