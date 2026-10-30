import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import {
  CesButtonComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterPanelComponent,
  CesFormDrawerComponent,
  CesIconButtonComponent,
  CesImportDrawerComponent,
  CesListShortcutsDirective,
  CesMultiSelectComponent,
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

import { ImportacaoStatus, ObraFilter, ObraImportacaoDTO, ObraResumoResponse, StatusObraResponse } from '../../../core/models/obras.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AuthService } from '../../../core/services/auth.service';
import { ObrasService } from '../../../core/services/obras.service';
import { RhService } from '../../../core/services/rh.service';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { dataIso, dataLocal } from '../../../shared/listagem/listagem';
import { formatarDataParaApi, parseDataBrParaDate, sanitizarNumeroObra, sanitizarTituloObra, statusIcon, statusTone } from '../obras-ui';

export type ObraImportRow = ObraImportacaoDTO & { editing?: boolean };

type Lookup = 'status' | 'processos' | 'bases' | 'municipios' | 'centros' | 'supervisores' | 'coordenadores' | 'agentes';
type CampoImportacao = 'nomeStatus' | 'nomeProcesso' | 'nomeBaseOperacional' | 'nomeMunicipio' | 'nomeCentroCusto' | 'nomeSupervisor' | 'nomeCoordenador' | 'nomeAgenteResponsavel';

/** Colunas da pré-visualização que se corrigem escolhendo um cadastro (o backend casa pelo nome). */
const SELECOES_IMPORTACAO: { key: string; campo: CampoImportacao; header: string; lookup: Lookup }[] = [
  { key: 'processo', campo: 'nomeProcesso', header: 'Processo', lookup: 'processos' },
  { key: 'base', campo: 'nomeBaseOperacional', header: 'Base', lookup: 'bases' },
  { key: 'municipio', campo: 'nomeMunicipio', header: 'Município', lookup: 'municipios' },
  { key: 'centro', campo: 'nomeCentroCusto', header: 'Centro de custo', lookup: 'centros' },
  { key: 'supervisor', campo: 'nomeSupervisor', header: 'Supervisor', lookup: 'supervisores' },
  { key: 'coordenador', campo: 'nomeCoordenador', header: 'Coordenador', lookup: 'coordenadores' },
  { key: 'statusObra', campo: 'nomeStatus', header: 'Status da obra', lookup: 'status' },
  { key: 'agente', campo: 'nomeAgenteResponsavel', header: 'Agente', lookup: 'agentes' }
];
const PRINCIPAIS: CesSelectOption<boolean | null>[] = [
  { label: 'Obras e subprojetos', value: null },
  { label: 'Só obras principais', value: true }
];
const FORMATOS: CesSelectOption<'resumo' | 'detalhado'>[] = [
  { label: 'Listagem resumida', value: 'resumo' },
  { label: 'Relatório completo', value: 'detalhado' }
];
const ISO_PARA_BR = (iso: string | null) => formatarDataParaApi(dataLocal(iso));

@Component({
  selector: 'app-obras-lista',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesIconButtonComponent,
    CesImportDrawerComponent,
    CesListShortcutsDirective,
    CesMultiSelectComponent,
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
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="podeEscrever" (newRequested)="novaObra()">
      <ces-page-header
        title="Obras e projetos"
        icon="pi pi-building"
        [breadcrumb]="[{ label: 'Obras' }, { label: 'Gestão de obras e projetos' }]"
        description="Carteira de obras: status, responsáveis, prazos e valor do projeto. Clique na linha para abrir a ficha da obra."
        helpLabel="Ajuda sobre obras"
        (help)="ajuda.show()"
      >
        @if (podeExportar) {
          <ces-button actions variant="outline" icon="pi pi-download" label="Exportar CSV" (pressed)="exportacaoAberta.set(true)"></ces-button>
        }
        @if (podeImportar) {
          <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacao.aberta.set(true)"></ces-button>
        }
        @if (podeEscrever) {
          <ces-button actions icon="pi pi-plus" label="Nova obra" [keyShortcut]="atalhos.newShortcut()" (pressed)="novaObra()"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel #painel storageKey="obras-lista" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-text-input label="Número" [(value)]="lista.filters.numero" placeholder="Ex.: 1228694"></ces-text-input>
          <ces-text-input label="Título" [(value)]="lista.filters.titulo"></ces-text-input>
          <ces-text-input label="Nota da obra" [(value)]="lista.filters.notaObra"></ces-text-input>
          <ces-multiselect label="Status" [options]="opcoes('status')" [(value)]="lista.filters.status" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Processo" [options]="opcoes('processos')" [(value)]="lista.filters.processo" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Base operacional" [options]="opcoes('bases')" [(value)]="lista.filters.base" placeholder="Todas"></ces-multiselect>
          <ces-multiselect label="Município" [options]="opcoes('municipios')" [(value)]="lista.filters.municipio" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Centro de custo" [options]="opcoes('centros')" [(value)]="lista.filters.centroCusto" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Supervisor" [options]="opcoes('supervisores')" [(value)]="lista.filters.supervisor" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Coordenador" [options]="opcoes('coordenadores')" [(value)]="lista.filters.coordenador" placeholder="Todos"></ces-multiselect>
          <ces-select label="Agente responsável" [options]="opcoes('agentes')" [(value)]="lista.filters.agente" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Hierarquia" [options]="principais" [(value)]="lista.filters.apenasPrincipais"></ces-select>
          <ces-datepicker label="Início a partir de" [value]="dataLocal(lista.filters.inicio().from)" [clearable]="true" (valueChange)="periodo('inicio', 'from', $event)"></ces-datepicker>
          <ces-datepicker label="Início até" [value]="dataLocal(lista.filters.inicio().to)" [clearable]="true" (valueChange)="periodo('inicio', 'to', $event)"></ces-datepicker>
          <ces-datepicker label="Previsão de fim a partir de" [value]="dataLocal(lista.filters.previsaoFim().from)" [clearable]="true" (valueChange)="periodo('previsaoFim', 'from', $event)"></ces-datepicker>
          <ces-datepicker label="Previsão de fim até" [value]="dataLocal(lista.filters.previsaoFim().to)" [clearable]="true" (valueChange)="periodo('previsaoFim', 'to', $event)"></ces-datepicker>
          <ces-number-input label="Valor mínimo" mode="currency" [min]="0" [(value)]="lista.filters.valorMin"></ces-number-input>
          <ces-number-input label="Valor máximo" mode="currency" [min]="0" [(value)]="lista.filters.valorMax"></ces-number-input>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Obras"
        mode="server"
        storageKey="obras-lista"
        searchPlaceholder="Número, título ou nota"
        rowActionLabel="Abrir ficha"
        rowActionIcon="pi pi-external-link"
        [emptyActionLabel]="podeEscrever ? 'Nova obra' : undefined"
        noResultsTitle="Nenhuma obra encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as obras' : 'Nenhuma obra cadastrada'"
        [stateDescription]="resultado.error() ?? 'Cadastre uma obra ou importe uma planilha.'"
        [filtered]="lista.filtered()"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="novaObra()"
        (rowActivate)="visualizarObra($event)"
      >
        <ng-template cesCell="numero" let-row>
          <span class="ces-cell-strong">{{ row.numero }}</span>
          @if (row.obraPaiNumero) {
            <span class="ces-cell-muted"><i class="pi pi-link" aria-hidden="true"></i> subprojeto de {{ row.obraPaiNumero }}</span>
          }
        </ng-template>
        <ng-template cesCell="titulo" let-row><span [title]="row.titulo">{{ row.titulo }}</span></ng-template>
        <ng-template cesCell="status" let-row>
          <ces-status [tone]="statusTone(row.statusNome, row.statusConcluida)" [icon]="statusIcon(row.statusNome)">{{ row.statusNome }}</ces-status>
        </ng-template>
        <ng-template cesCell="prazo" let-row>
          <span>{{ row.dataInicio }} → {{ row.dataPrevisaoFim }}</span>
          @if (row.dataFimEfetiva) {
            <span class="ces-cell-muted">Concluída em {{ row.dataFimEfetiva }}</span>
          }
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button icon="pi pi-cart-plus" [label]="'Solicitar materiais para ' + row.numero" size="icon-sm" (pressed)="solicitarMateriais(row)"></ces-icon-button>
            @if (podeEscrever) {
              <ces-icon-button icon="pi pi-pencil" [label]="'Editar obra ' + row.numero" size="icon-sm" (pressed)="editarObra(row)"></ces-icon-button>
              <ces-icon-button icon="pi pi-sync" [label]="'Alterar status da obra ' + row.numero" size="icon-sm" (pressed)="abrirAlteracaoStatus(row)"></ces-icon-button>
              <ces-icon-button icon="pi pi-ban" [label]="'Desativar obra ' + row.numero" size="icon-sm" (pressed)="desativarObra(row)"></ces-icon-button>
            }
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações da obra ' + row.numero" size="icon-sm" (pressed)="showLogs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [visible]="!!obraStatus()" (visibleChange)="!$event && obraStatus.set(null)" title="Alterar status da obra"
      [description]="obraStatus() ? obraStatus()!.numero + ' · ' + obraStatus()!.titulo : undefined" size="sm">
      <ces-select label="Novo status" [required]="true" [options]="opcoes('status')" [(value)]="novoStatusId"></ces-select>
      @if (statusSelecionadoEhConcluido()) {
        <ces-datepicker label="Data de conclusão efetiva" [(value)]="novaDataFimEfetiva" [maxDate]="hoje" [clearable]="true"
          hint="Se ficar em branco, o sistema usa a data de hoje."></ces-datepicker>
      }
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="obraStatus.set(null)"></ces-button>
        <ces-button icon="pi pi-check" label="Salvar status" [disabled]="!novoStatusId()" [loading]="salvandoStatus()" (pressed)="salvarAlteracaoStatus()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-form-drawer [(visible)]="exportacaoAberta" title="Exportar obras (CSV)" description="Exporta o recorte atual da lista, inclusive a busca." size="sm">
      <ces-select label="Formato" [options]="formatos" [(value)]="exportFormato"></ces-select>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="exportacaoAberta.set(false)"></ces-button>
        <ces-button icon="pi pi-download" label="Exportar" [loading]="exportando()" (pressed)="executarExportacao()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer [(visible)]="importacao.aberta" title="Importar obras"
      description="Planilha CSV com número, título, datas, processo, base, município, centro de custo, supervisor e coordenador."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao.linhas()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="numero" let-row>
            @if (row.editing) { <ces-text-input label="Número" [labelVisible]="false" [(value)]="row.numero" [maxlength]="7"></ces-text-input> } @else { {{ row.numero || '—' }} }
          </ng-template>
          <ng-template cesCell="titulo" let-row>
            @if (row.editing) { <ces-text-input label="Título" [labelVisible]="false" [(value)]="row.titulo"></ces-text-input> } @else { {{ row.titulo || '—' }} }
          </ng-template>
          @for (sel of selecoesImportacao; track sel.key) {
            <ng-template [cesCell]="sel.key" let-row>
              @if (row.editing) {
                <ces-select [label]="sel.header" [labelVisible]="false" [options]="nomes(sel.lookup)" [value]="row[sel.campo] ?? null"
                  (valueChange)="row[sel.campo] = $event ?? undefined" [clearable]="true" [filter]="true"></ces-select>
              } @else {
                {{ row[sel.campo] || '—' }}
              }
            </ng-template>
          }
          <ng-template cesCell="situacao" let-row>
            <ces-status [tone]="situacao(row.status).tone" [icon]="situacao(row.status).icon">{{ situacao(row.status).label }}</ces-status>
            @if (row.mensagemErro) {
              <span class="ces-cell-muted">{{ row.mensagemErro }}</span>
            }
          </ng-template>
          <ng-template cesRowActions let-row>
            <span class="ces-table-actions">
              @if (row.editing) {
                <ces-icon-button icon="pi pi-check" [label]="'Concluir correção da linha ' + row.linha" size="icon-sm" (pressed)="finishImportEdit(row)"></ces-icon-button>
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
    <app-ajuda-listagem #ajuda titulo="Obras e projetos" artigoPlural="as" entidadePlural="obras" busca="número, título ou nota da obra"
      rotuloNovo="Nova obra" [podeCriar]="podeEscrever" [importacao]="podeImportar">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-sync"></i> Status</h4>
        <p class="help-text-desc">"Alterar status" muda a etapa da obra; ao concluir, informe a data efetiva (em branco, vale a de hoje).</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-cart-plus"></i> Materiais</h4>
        <p class="help-text-desc">"Solicitar materiais" abre uma solicitação técnica já vinculada à obra.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class ObrasListComponent {
  private readonly service = inject(ObrasService);
  private readonly rh = inject(RhService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  private readonly permissoes = this.auth.currentUser()?.permissoes ?? [];
  protected readonly podeEscrever = this.permissoes.includes('obras:obra:write');
  protected readonly podeImportar = this.permissoes.includes('obras:obra:import');
  protected readonly podeExportar = this.permissoes.includes('obras:obra:export');

  protected readonly statusTone = statusTone;
  protected readonly statusIcon = statusIcon;
  protected readonly dataLocal = dataLocal;
  protected readonly principais = PRINCIPAIS;
  protected readonly formatos = FORMATOS;
  protected readonly hoje = new Date();

  /** Cadastros dos filtros e da correção da importação, como `{ id, nome }`. */
  private readonly cadastros = signal<Record<Lookup, { id: string; nome: string }[]>>({
    status: [], processos: [], bases: [], municipios: [], centros: [], supervisores: [], coordenadores: [], agentes: []
  });
  private readonly statusObras = signal<StatusObraResponse[]>([]);

  protected readonly lista = cesListState({
    storageKey: 'obras-lista',
    filters: {
      numero: cesFilter.value<string>('', { label: 'Número' }),
      titulo: cesFilter.value<string>('', { label: 'Título' }),
      notaObra: cesFilter.value<string>('', { label: 'Nota' }),
      status: cesFilter.list<string>([], { label: 'Status', itemLabel: (id) => this.nome('status', id) }),
      processo: cesFilter.list<string>([], { label: 'Processo', itemLabel: (id) => this.nome('processos', id) }),
      base: cesFilter.list<string>([], { label: 'Base', itemLabel: (id) => this.nome('bases', id), countLabel: (n) => `${n} selecionadas` }),
      municipio: cesFilter.list<string>([], { label: 'Município', itemLabel: (id) => this.nome('municipios', id) }),
      centroCusto: cesFilter.list<string>([], { label: 'Centro de custo', itemLabel: (id) => this.nome('centros', id) }),
      supervisor: cesFilter.list<string>([], { label: 'Supervisor', itemLabel: (id) => this.nome('supervisores', id) }),
      coordenador: cesFilter.list<string>([], { label: 'Coordenador', itemLabel: (id) => this.nome('coordenadores', id) }),
      agente: cesFilter.value<string | null>(null, { label: 'Agente', format: (id): string | null => (id ? this.nome('agentes', id) : null) }),
      apenasPrincipais: cesFilter.value<boolean | null>(null, { label: 'Hierarquia', param: 'principais', options: () => PRINCIPAIS }),
      inicio: cesFilter.range({ from: null, to: null }, { label: 'Início' }),
      previsaoFim: cesFilter.range({ from: null, to: null }, { label: 'Previsão de fim' }),
      valorMin: cesFilter.value<number | null>(null, { label: 'Valor mínimo', type: 'number' }),
      valorMax: cesFilter.value<number | null>(null, { label: 'Valor máximo', type: 'number' })
    },
    sort: [{ key: 'prazo', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<ObraResumoResponse>(
    (pedido) => this.service.listarObras({ ...this.filtro(), page: pedido.page, size: pedido.size, sort: pedido.sort.map((r) => `${r.key},${r.direction}`)[0] }),
    { errorMessage: getErrorMessage }
  );

  protected readonly colunas: CesDataTableColumn<ObraResumoResponse>[] = [
    { key: 'numero', header: 'Número', type: 'custom', priority: 1, sortField: 'numero', width: '9rem', value: (o) => o.numero, display: (o) => o.numero },
    { key: 'titulo', header: 'Título', type: 'custom', priority: 1, sortField: 'titulo', value: (o) => o.titulo, display: (o) => o.titulo },
    { key: 'nota', header: 'Nota', sortField: 'notaObra', value: (o) => o.notaObra, display: (o) => o.notaObra || '—' },
    { key: 'status', header: 'Status', type: 'custom', priority: 1, sortField: 'status.nome', value: (o) => o.statusNome, display: (o) => o.statusNome },
    { key: 'processo', header: 'Processo', sortField: 'processo.nome', value: (o) => o.processoNome, display: (o) => o.processoNome || '—' },
    { key: 'base', header: 'Base', sortField: 'baseOperacional.nome', value: (o) => o.baseOperacionalNome, display: (o) => o.baseOperacionalNome || '—' },
    { key: 'municipio', header: 'Município', sortField: 'municipio.nome', value: (o) => o.municipioNome, display: (o) => o.municipioNome || '—' },
    { key: 'centro', header: 'Centro de custo', sortField: 'centroCusto.nome', value: (o) => o.centroCustoNome, display: (o) => o.centroCustoNome || '—' },
    { key: 'supervisor', header: 'Supervisor', sortField: 'supervisor', value: (o) => o.supervisorNome, display: (o) => o.supervisorNome || '—' },
    { key: 'coordenador', header: 'Coordenador', sortField: 'coordenador', value: (o) => o.coordenadorNome, display: (o) => o.coordenadorNome || '—' },
    { key: 'prazo', header: 'Início → previsão', type: 'custom', sortField: 'dataInicio', value: (o) => o.dataInicio, display: (o) => `${o.dataInicio} → ${o.dataPrevisaoFim}` },
    { key: 'valor', header: 'Valor do projeto', type: 'currency', sortField: 'valorProjeto', value: (o) => o.valorProjeto }
  ];
  protected readonly rotulo = (o: ObraResumoResponse) => `Obra ${o.numero}`;

  // Alteração rápida de status
  protected readonly obraStatus = signal<ObraResumoResponse | null>(null);
  protected readonly novoStatusId = signal<string | null>(null);
  protected readonly novaDataFimEfetiva = signal<Date | null>(null);
  protected readonly salvandoStatus = signal(false);
  protected readonly statusSelecionadoEhConcluido = computed(() => this.statusObras().find((s) => s.id === this.novoStatusId())?.concluida ?? false);

  // Exportação
  protected readonly exportacaoAberta = signal(false);
  protected readonly exportFormato = signal<'resumo' | 'detalhado'>('resumo');
  protected readonly exportando = signal(false);

  // Importação
  protected readonly importacao = importacaoListagem<ObraImportRow>({
    enviar: (arquivo) => this.service.validarImportacao(arquivo),
    confirmar: (registros) => this.service.confirmarImportacao({ registros }),
    modelo: () =>
      this.service.baixarModeloCsv().subscribe({
        next: (blob) => this.service.baixarArquivoBlob(blob, 'modelo_importacao_obras.csv'),
        error: () => this.notificacoes.error('Não foi possível baixar o modelo CSV de importação.')
      }),
    aoProcessar: () => this.lista.reload()
  });
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = CHAVE_LINHA_IMPORTACAO;
  protected readonly selecoesImportacao = SELECOES_IMPORTACAO;
  protected readonly colunasImportacao: CesDataTableColumn<ObraImportRow>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'numero', header: 'Número', type: 'custom', priority: 1, value: (r) => r.numero },
    { key: 'titulo', header: 'Título', type: 'custom', priority: 1, value: (r) => r.titulo },
    { key: 'datas', header: 'Início → previsão', value: (r) => r.dataInicio, display: (r) => `${r.dataInicio || '—'} → ${r.dataPrevisaoFim || '—'}` },
    ...SELECOES_IMPORTACAO.map((s): CesDataTableColumn<ObraImportRow> => ({ key: s.key, header: s.header, type: 'custom', value: (r) => r[s.campo] })),
    { key: 'situacao', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  constructor() {
    const definir = (chave: Lookup, itens: { id: string; nome: string }[]) => this.cadastros.update((c) => ({ ...c, [chave]: itens }));
    const aviso = (nome: string) => () => this.notificacoes.warning(`Não foi possível carregar ${nome} para os filtros.`);
    this.service.listarStatusAtivos().subscribe({
      next: (r) => {
        this.statusObras.set(r);
        definir('status', r);
      },
      error: aviso('os status de obra')
    });
    this.service.listarAgentesAtivos().subscribe({ next: (r) => definir('agentes', r), error: aviso('os agentes responsáveis') });
    this.rh.listarBasesOperacionaisAtivas().subscribe({ next: (r) => definir('bases', r.content ?? []), error: aviso('as bases operacionais') });
    this.rh.listarProcessosAtivos().subscribe({ next: (r) => definir('processos', r.content ?? []), error: aviso('os processos') });
    this.rh.listarMunicipios({ ativo: true, size: 500, sort: 'nome,asc' }).subscribe({ next: (r) => definir('municipios', r.content ?? []), error: aviso('os municípios') });
    this.rh.listarCentrosFaturamentoAtivos().subscribe({ next: (r) => definir('centros', r.content ?? []), error: aviso('os centros de custo') });
    this.rh.listarSupervisoresAtivosMin().subscribe({
      next: (r) => definir('supervisores', r.map((s) => ({ id: s.id, nome: s.nomeColaborador }))),
      error: aviso('os supervisores')
    });
    this.rh.listarCoordenadoresAtivosMin().subscribe({
      next: (r) => definir('coordenadores', r.map((c) => ({ id: c.id, nome: c.nomeColaborador }))),
      error: aviso('os coordenadores')
    });
  }

  private nome(chave: Lookup, id: string): string {
    return this.cadastros()[chave].find((i) => i.id === id)?.nome ?? 'selecionado';
  }

  protected opcoes(chave: Lookup): CesSelectOption<string>[] {
    return this.cadastros()[chave].map((i) => ({ label: i.nome, value: i.id }));
  }

  /** Na importação o backend casa os cadastros pelo nome. */
  protected nomes(chave: Lookup): CesSelectOption<string>[] {
    return this.cadastros()[chave].map((i) => ({ label: i.nome, value: i.nome }));
  }

  protected periodo(filtro: 'inicio' | 'previsaoFim', ponta: 'from' | 'to', data: Date | null): void {
    const atual = this.lista.filters[filtro]();
    this.lista.filters[filtro].set({ ...atual, [ponta]: dataIso(data) });
  }

  /** Recorte aplicado no formato do backend (também usado na exportação). */
  private filtro(): ObraFilter {
    const { filters: f, query } = this.lista.request();
    const lista = (v: string[]) => (v.length ? v : undefined);
    return {
      buscaGlobal: query || undefined,
      numero: f.numero.trim() || undefined,
      titulo: f.titulo.trim() || undefined,
      notaObra: f.notaObra.trim() || undefined,
      statusIds: lista(f.status),
      processoIds: lista(f.processo),
      baseOperacionalIds: lista(f.base),
      municipioIds: lista(f.municipio),
      centroCustoIds: lista(f.centroCusto),
      supervisorIds: lista(f.supervisor),
      coordenadorIds: lista(f.coordenador),
      agenteResponsavelId: f.agente ?? undefined,
      apenasPrincipais: f.apenasPrincipais ?? false,
      dataInicioDe: ISO_PARA_BR(f.inicio.from),
      dataInicioAte: ISO_PARA_BR(f.inicio.to),
      dataPrevisaoFimDe: ISO_PARA_BR(f.previsaoFim.from),
      dataPrevisaoFimAte: ISO_PARA_BR(f.previsaoFim.to),
      valorProjetoMin: f.valorMin ?? undefined,
      valorProjetoMax: f.valorMax ?? undefined,
      ativo: true
    };
  }

  protected novaObra(): void {
    if (this.podeEscrever) void this.router.navigate(['/obras/nova']);
  }

  /** A ficha abre em outra aba para não perder o recorte da carteira. */
  protected visualizarObra(obra: ObraResumoResponse): void {
    window.open(this.router.serializeUrl(this.router.createUrlTree(['/obras', obra.id])), '_blank');
  }

  protected solicitarMateriais(obra: ObraResumoResponse): void {
    void this.router.navigate(['/almoxarifado/solicitacoes-tecnicas/nova'], { queryParams: { idObra: obra.id } });
  }

  protected editarObra(obra: ObraResumoResponse): void {
    void this.router.navigate(['/obras', obra.id, 'editar']);
  }

  protected showLogs(obra: ObraResumoResponse): void {
    this.auditoria().show(obra.id, 'Obra / Projeto', {
      numero: 'Número da Obra',
      titulo: 'Título',
      notaObra: 'Nota da Obra',
      statusNome: 'Status',
      valorProjeto: 'Valor do Projeto',
      dataInicio: 'Data de Início',
      dataPrevisaoFim: 'Previsão de Término'
    });
  }

  protected abrirAlteracaoStatus(obra: ObraResumoResponse): void {
    this.novoStatusId.set(obra.statusId);
    this.novaDataFimEfetiva.set(obra.dataFimEfetiva ? parseDataBrParaDate(obra.dataFimEfetiva) : null);
    this.obraStatus.set(obra);
  }

  protected salvarAlteracaoStatus(): void {
    const obra = this.obraStatus();
    const status = this.novoStatusId();
    if (!obra || !status || this.salvandoStatus()) return;
    this.salvandoStatus.set(true);
    this.service.alterarStatus(obra.id, status, formatarDataParaApi(this.novaDataFimEfetiva())).subscribe({
      next: (atualizada) => {
        this.salvandoStatus.set(false);
        this.obraStatus.set(null);
        this.notificacoes.success(`A obra ${atualizada.numero} agora está "${atualizada.statusNome}".`);
        this.lista.reload();
      },
      error: (erro) => {
        this.salvandoStatus.set(false);
        this.notificacoes.error(getErrorMessage(erro));
      }
    });
  }

  protected async desativarObra(obra: ObraResumoResponse): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: 'Desativar obra',
      message: `${obra.numero} (${obra.titulo}) deixa de aparecer na carteira. O histórico é mantido.`,
      confirmLabel: 'Desativar',
      variant: 'destructive'
    });
    if (!ok) return;
    this.service.desativarObra(obra.id).subscribe({
      next: () => {
        this.notificacoes.success(`Obra ${obra.numero} desativada.`);
        this.lista.reload();
      },
      error: (erro) => this.notificacoes.error(getErrorMessage(erro))
    });
  }

  protected executarExportacao(): void {
    if (this.exportando()) return;
    const formato = this.exportFormato();
    this.exportando.set(true);
    (formato === 'detalhado' ? this.service.exportarDetalhado(this.filtro()) : this.service.exportarResumo(this.filtro())).subscribe({
      next: (resposta) => {
        this.exportando.set(false);
        this.exportacaoAberta.set(false);
        if (!resposta.body) return;
        this.service.baixarArquivoBlob(resposta.body, `obras_${formato}_${dataIso(new Date())}.csv`);
        this.notificacoes.success(`CSV de obras (${formato}) gerado.`);
      },
      error: (erro) => {
        this.exportando.set(false);
        this.notificacoes.error(getErrorMessage(erro));
      }
    });
  }

  /** Concluir a correção: normaliza número e título e libera a linha se os obrigatórios estão preenchidos. */
  protected finishImportEdit(linha: ObraImportRow): void {
    linha.numero = sanitizarNumeroObra(linha.numero);
    linha.titulo = sanitizarTituloObra(linha.titulo);
    const completa = [linha.numero, linha.titulo, linha.dataInicio, linha.dataPrevisaoFim, linha.nomeProcesso, linha.nomeBaseOperacional,
      linha.nomeMunicipio, linha.nomeCentroCusto, linha.nomeSupervisor, linha.nomeCoordenador].every((v) => !!v);
    if (completa) {
      linha.status = ImportacaoStatus.AGUARDANDO_PROCESSAMENTO;
      linha.mensagemErro = undefined;
    }
    this.importacao.concluirCorrecao(linha);
  }

}
