import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CheckboxModule } from 'primeng/checkbox';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
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
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';
import { CES_FORM_KIT } from '@ces/ui/forms';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup } from '../../../core/lookup/store/remote-lookup';
import {
  ESCOPO_META_LABELS,
  EscopoMeta,
  META_DESCRICAO_MAX,
  META_QUANTIDADE_MAX,
  META_QUANTIDADE_MIN,
  MetaInspecaoRequest,
  MetaInspecaoResponse,
  PERIODICIDADE_LABELS,
  PERIODICIDADE_OPTIONS,
  PeriodicidadeMeta,
  calcularJanela,
  descreverMeta,
  formatarDataBr,
  parseDataIso,
  toDataIso
} from '../../../core/models/meta-inspecao.models';
import { FuncaoResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { TipoInspecaoResponse } from '../../../core/models/sst.models';
import { MetasInspecaoService } from '../../../core/services/metas-inspecao.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { DateMaskDirective } from '../../../shared/directives/date-mask.directive';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { OPCOES_STATUS, dataIso, dataLocal } from '../../../shared/listagem/listagem';

const ESCOPOS: CesSelectOption<EscopoMeta | null>[] = [
  { label: 'Todos', value: null },
  { label: ESCOPO_META_LABELS.FUNCAO, value: 'FUNCAO' },
  { label: ESCOPO_META_LABELS.COLABORADOR, value: 'COLABORADOR' }
];
const PERIODICIDADES: CesSelectOption<PeriodicidadeMeta | null>[] = [{ label: 'Todas', value: null }, ...(PERIODICIDADE_OPTIONS as CesSelectOption<PeriodicidadeMeta>[])];

@Component({
  selector: 'app-sst-metas-inspecao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    CheckboxModule,
    DatePickerModule,
    InputNumberModule,
    InputTextModule,
    SelectModule,
    CesAutocompleteComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    ...CES_FORM_KIT,
    AjudaListagemComponent,
    DateMaskDirective
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="showAddDialog()">
      <ces-page-header
        title="Metas de inspeção"
        icon="pi pi-flag"
        [breadcrumb]="[{ label: 'SST' }, { label: 'Metas de inspeção' }]"
        description="Quantas inspeções cada função ou colaborador precisa realizar por período."
        helpLabel="Ajuda sobre metas de inspeção"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-chart-bar" label="Acompanhamento" (pressed)="abrirAcompanhamento()"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Nova meta" [keyShortcut]="atalhos.newShortcut()" (pressed)="showAddDialog()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="sst-metas" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Tipo de inspeção" [options]="tiposOpcoes()" [(value)]="lista.filters.idTipoInspecao" [clearable]="true" placeholder="Todos"></ces-select>
          <ces-select label="Alvo" [options]="escopos" [(value)]="lista.filters.escopo"></ces-select>
          <ces-select label="Periodicidade" [options]="periodicidades" [(value)]="lista.filters.periodicidade"></ces-select>
          <ces-datepicker label="Vigente em" [value]="dataLocal(lista.filters.vigenteEm())" [clearable]="true" (valueChange)="lista.filters.vigenteEm.set(dataIso($event))"></ces-datepicker>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Metas"
        mode="server"
        storageKey="sst-metas"
        searchPlaceholder="Descrição da meta"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Nova meta"
        noResultsTitle="Nenhuma meta encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as metas' : 'Nenhuma meta cadastrada'"
        [stateDescription]="resultado.error() ?? 'Defina quantas inspeções cada função ou colaborador precisa realizar por período.'"
        [filtered]="lista.filtered()"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="showAddDialog()"
        (rowActivate)="showEditDialog($event)"
      >
        <ng-template cesCell="alvo" let-row>
          <span class="ces-cell-strong">{{ nomeAlvo(row) }}</span>
          <span class="ces-cell-muted">{{ rotuloEscopo(row) }}{{ row.descricao ? ' · ' + row.descricao : '' }}</span>
        </ng-template>
        <ng-template cesCell="meta" let-row>
          <span class="ces-cell-strong">{{ row.quantidade }} {{ row.quantidade === 1 ? 'inspeção' : 'inspeções' }}</span>
          <span class="ces-cell-muted">{{ rotuloPeriodicidade(row) }}</span>
        </ng-template>
        <ng-template cesCell="janela" let-row>
          @if (row.janelaAtualInicio && row.janelaAtualFim) {
            {{ formatarData(row.janelaAtualInicio) }} a {{ formatarData(row.janelaAtualFim) }}
          } @else {
            <span class="ces-cell-muted">Fora da vigência</span>
          }
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativa' : 'Inativa' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar meta de ' : 'Reativar meta de ') + nomeAlvo(row)" size="icon-sm" (pressed)="toggleStatus(row)"></ces-icon-button>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer #drawer [(visible)]="formularioAberto" [title]="editingId() ? 'Editar meta' : 'Nova meta'" [dirty]="metaForm.dirty" size="md">
      <form [formGroup]="metaForm" (ngSubmit)="onSave()">
        <ces-form-section title="Alvo" icon="pi pi-users">
          @if (editingId()) {
            <ces-callout tone="info">O tipo e o alvo não podem ser alterados. Para mudá-los, cadastre outra meta.</ces-callout>
          }
          <ces-form-field label="Tipo de inspeção" size="lg">
            <p-select formControlName="idTipoInspecao" [options]="tiposInspecao()" optionLabel="nome" optionValue="id" placeholder="Selecione" appendTo="body" size="small"></p-select>
          </ces-form-field>
          <ces-form-field label="A meta vale para" size="lg">
            <p-select formControlName="escopo" [options]="escoposFormulario" optionLabel="label" optionValue="value" appendTo="body" size="small" (onChange)="onEscopoChange()"></p-select>
          </ces-form-field>
          @if (escopoAtual() === 'COLABORADOR') {
            <ces-autocomplete label="Colaborador" formControlName="idColaborador" [options]="colaboradores()" [loading]="colaboradoresLoading()"
              [status]="colaboradorLookupStatus()" placeholder="Digite o nome" description="Exceção individual: prevalece sobre a meta da função dele."
              (search)="searchColaboradores($event)" (loadMore)="colaboradorLookup.loadNext()" (retry)="colaboradorLookup.retry()"></ces-autocomplete>
          } @else {
            <ces-form-field label="Função" size="lg" hint="Vale para todos os colaboradores ativos nessa função.">
              <p-select formControlName="idFuncao" [options]="funcoes()" optionLabel="nome" optionValue="id" [filter]="true" filterBy="nome" placeholder="Selecione"
                emptyFilterMessage="Nenhuma função encontrada" appendTo="body" size="small"></p-select>
            </ces-form-field>
          }
          <ces-form-field label="Quantidade de inspeções" size="sm">
            <p-inputnumber formControlName="quantidade" [min]="minQuantidade" [max]="maxQuantidade" [showButtons]="true" buttonLayout="horizontal"
              incrementButtonIcon="pi pi-plus" decrementButtonIcon="pi pi-minus" size="small"></p-inputnumber>
          </ces-form-field>
        </ces-form-section>

        <ces-form-section title="Período" icon="pi pi-calendar">
          <ces-form-field label="Periodicidade" size="lg">
            <p-select formControlName="periodicidade" [options]="periodicidadesFormulario" optionLabel="label" optionValue="value" appendTo="body" size="small" (onChange)="onPeriodicidadeChange()"></p-select>
          </ces-form-field>
          <ces-form-field label="Início da vigência" size="md">
            <p-datepicker formControlName="vigenciaInicio" dateFormat="dd/mm/yy" dateMask [showIcon]="true" iconDisplay="input" [showButtonBar]="true" placeholder="dd/mm/aaaa" appendTo="body" size="small"></p-datepicker>
          </ces-form-field>
          @if (!semPrazo()) {
            <ces-form-field label="Fim da vigência" size="md">
              <p-datepicker formControlName="vigenciaFim" dateFormat="dd/mm/yy" dateMask [showIcon]="true" iconDisplay="input" [showButtonBar]="true" placeholder="dd/mm/aaaa" appendTo="body" size="small"></p-datepicker>
            </ces-form-field>
          }
          <ces-form-field label="Sem prazo definido" size="lg" [hint]="periodicidadeAtual() === 'PERSONALIZADO' ? 'Campanha pontual: a janela é única e a data de fim é obrigatória.' : undefined">
            <p-checkbox formControlName="semPrazo" [binary]="true" (onChange)="onSemPrazoChange()"></p-checkbox>
          </ces-form-field>
          <ces-form-field label="Descrição" size="lg">
            <input pInputText formControlName="descricao" [maxlength]="maxDescricao" placeholder="Ex.: Meta trimestral dos encarregados" pSize="small" />
          </ces-form-field>
          @if (previa(); as texto) {
            <ces-callout tone="info" title="Prévia">
              {{ texto }}
              @if (janelaPrevia(); as janela) {
                Janela atual: {{ formatarData(janela.inicio) }} a {{ formatarData(janela.fim) }}.
              } @else {
                Fora da vigência hoje: a primeira janela ainda vai começar ou já terminou.
              }
            </ces-callout>
          }
          @if (erroFormulario(); as erro) {
            <ces-callout tone="danger">{{ erro }}</ces-callout>
          }
          @if (conflito(); as detalhe) {
            <ces-callout tone="warning" title="Já existe meta para este alvo">
              {{ detalhe }}
              <ces-button size="sm" variant="link" icon="pi pi-arrow-right" iconPosition="right" label="Ver metas deste alvo" (pressed)="filtrarPorAlvoEmConflito()"></ces-button>
            </ces-callout>
          }
        </ces-form-section>
        <button type="submit" hidden aria-hidden="true" tabindex="-1"></button>
      </form>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="drawer.requestClose()"></ces-button>
        <ces-button icon="pi pi-check" [label]="editingId() ? 'Salvar alterações' : 'Cadastrar meta'" [loading]="saving()" (pressed)="onSave()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-ajuda-listagem #ajuda titulo="Metas de inspeção" artigoPlural="as" entidadePlural="metas" busca="descrição" rotuloNovo="Nova meta">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-users"></i> Função ou colaborador</h4>
        <p class="help-text-desc">A meta da função vale para todos os colaboradores ativos nela; a meta de um colaborador é exceção e prevalece sobre a da função. Duas metas ativas do mesmo alvo não podem se sobrepor: o formulário avisa e leva às metas existentes.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-chart-bar"></i> Acompanhamento</h4>
        <p class="help-text-desc">"Acompanhamento" mostra quanto cada pessoa já cumpriu na janela atual.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class MetasInspecaoComponent {
  private readonly metasService = inject(MetasInspecaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly lookupStore = inject(LookupStore);

  protected readonly escopos = ESCOPOS;
  protected readonly periodicidades = PERIODICIDADES;
  protected readonly escoposFormulario = ESCOPOS.filter((e) => e.value !== null);
  protected readonly periodicidadesFormulario = PERIODICIDADE_OPTIONS;
  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly minQuantidade = META_QUANTIDADE_MIN;
  protected readonly maxQuantidade = META_QUANTIDADE_MAX;
  protected readonly maxDescricao = META_DESCRICAO_MAX;
  protected readonly formatarData = formatarDataBr;
  protected readonly dataLocal = dataLocal;
  protected readonly dataIso = dataIso;

  protected readonly funcoes = signal<FuncaoResponse[]>([]);
  protected readonly tiposInspecao = signal<TipoInspecaoResponse[]>([]);
  protected readonly tiposOpcoes = computed<CesSelectOption<string>[]>(() => this.tiposInspecao().map((t) => ({ label: t.nome, value: t.id })));

  protected readonly metaForm = this.fb.group({
    idTipoInspecao: [null as string | null, Validators.required],
    escopo: ['FUNCAO' as EscopoMeta, Validators.required],
    idFuncao: [null as string | null],
    idColaborador: [null as string | null],
    quantidade: [1, [Validators.required, Validators.min(META_QUANTIDADE_MIN), Validators.max(META_QUANTIDADE_MAX)]],
    periodicidade: ['MENSAL' as PeriodicidadeMeta, Validators.required],
    vigenciaInicio: [null as Date | null, Validators.required],
    semPrazo: [true],
    vigenciaFim: [null as Date | null],
    descricao: [null as string | null]
  });
  /** Valores do formulário como signal (prévia, campos condicionais). */
  private readonly valores = toSignal(this.metaForm.valueChanges, { initialValue: this.metaForm.getRawValue() });
  protected readonly escopoAtual = computed(() => this.valores().escopo ?? this.metaForm.getRawValue().escopo);
  protected readonly periodicidadeAtual = computed(() => this.valores().periodicidade);
  protected readonly semPrazo = computed(() => !!this.valores().semPrazo);

  protected readonly colaboradorLookup = new RemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () => {
      const id = this.metaForm.getRawValue().idColaborador;
      return id ? [id] : [];
    },
    destroyRef: inject(DestroyRef)
  });
  protected readonly colaboradores = this.colaboradorLookup.items;
  protected readonly colaboradoresLoading = this.colaboradorLookup.loading;
  protected readonly colaboradorLookupStatus = this.colaboradorLookup.status;

  protected readonly lista = cesListState({
    storageKey: 'sst-metas',
    filters: {
      idTipoInspecao: cesFilter.value<string | null>(null, { label: 'Tipo', param: 'tipo', format: (id): string | null => (id ? (this.tiposInspecao().find((t) => t.id === id)?.nome ?? 'tipo selecionado') : null) }),
      escopo: cesFilter.value<EscopoMeta | null>(null, { label: 'Alvo', type: 'string', options: () => ESCOPOS }),
      idFuncao: cesFilter.value<string | null>(null, { label: 'Função', param: 'funcao', format: (id): string | null => (id ? (this.funcoes().find((f) => f.id === id)?.nome ?? 'função selecionada') : null) }),
      idColaborador: cesFilter.value<string | null>(null, {
        label: 'Colaborador',
        param: 'colaborador',
        format: (id): string | null => (id ? (this.colaboradores().find((c) => c.value === id)?.label ?? 'colaborador selecionado') : null)
      }),
      periodicidade: cesFilter.value<PeriodicidadeMeta | null>(null, { label: 'Periodicidade', type: 'string', options: () => PERIODICIDADES }),
      vigenteEm: cesFilter.date(null, { label: 'Vigente em', param: 'vigente' }),
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'vigencia', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<MetaInspecaoResponse>(
    (pedido) => {
      const { filters, query } = this.lista.request();
      return this.metasService.listarMetas({
        ...filters,
        buscaGlobal: query || null,
        page: pedido.page,
        size: pedido.size,
        sort: pedido.sort.map((r) => `${r.key},${r.direction}`)[0] ?? 'vigenciaInicio,desc'
      });
    },
    { errorMessage: getErrorMessage }
  );

  protected readonly colunas: CesDataTableColumn<MetaInspecaoResponse>[] = [
    { key: 'alvo', header: 'Alvo', type: 'custom', priority: 1, value: (m) => this.nomeAlvo(m) },
    { key: 'tipo', header: 'Tipo', value: (m) => m.nomeTipoInspecao, display: (m) => m.nomeTipoInspecao || 'Campo' },
    { key: 'meta', header: 'Meta', type: 'custom', priority: 1, value: (m) => m.quantidade, display: (m) => `${m.quantidade} / ${PERIODICIDADE_LABELS[m.periodicidade]}` },
    {
      key: 'vigencia',
      header: 'Vigência',
      sortField: 'vigenciaInicio',
      value: (m) => m.vigenciaInicio,
      display: (m) => `${formatarDataBr(m.vigenciaInicio)} → ${m.vigenciaFim ? formatarDataBr(m.vigenciaFim) : 'indeterminada'}`
    },
    { key: 'janela', header: 'Janela atual', type: 'custom', value: (m) => m.janelaAtualInicio },
    { key: 'ativo', header: 'Status', type: 'custom', sortField: 'ativo', value: (m) => m.ativo, display: (m) => (m.ativo ? 'Ativa' : 'Inativa') }
  ];
  protected readonly rotulo = (m: MetaInspecaoResponse) => this.nomeAlvo(m);

  protected readonly formularioAberto = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly erroFormulario = signal<string | null>(null);
  protected readonly conflito = signal<string | null>(null);

  constructor() {
    inject(RhService).listarFuncoesAtivas().subscribe({ next: (r) => this.funcoes.set(r.content), error: (e) => this.notificacoes.error(getErrorMessage(e)) });
    inject(SstService).listarOpcoesTiposInspecao().subscribe({ next: (r) => this.tiposInspecao.set(r), error: (e) => this.notificacoes.error(getErrorMessage(e)) });
  }

  protected searchColaboradores(query: string): void {
    this.colaboradorLookup.search(query);
  }

  // ---------------------------------------------------------- Formulário
  protected showAddDialog(): void {
    // Padrão: primeiro dia do mês seguinte — evita cobrar uma janela já em curso.
    const hoje = new Date();
    const tipos = this.tiposInspecao();
    this.editingId.set(null);
    this.metaForm.reset({
      idTipoInspecao: tipos.find((t) => t.nome === 'Campo')?.id ?? tipos[0]?.id ?? null,
      escopo: 'FUNCAO',
      idFuncao: null,
      idColaborador: null,
      quantidade: 1,
      periodicidade: 'MENSAL',
      vigenciaInicio: new Date(hoje.getFullYear(), hoje.getMonth() + 1, 1),
      semPrazo: true,
      vigenciaFim: null,
      descricao: null
    });
    this.alternarCamposDeAlvo(true);
    this.abrir();
  }

  protected showEditDialog(meta: MetaInspecaoResponse): void {
    this.editingId.set(meta.id);
    this.metaForm.reset({
      idTipoInspecao: meta.idTipoInspecao,
      escopo: meta.escopo,
      idFuncao: meta.idFuncao,
      idColaborador: meta.idColaborador,
      quantidade: meta.quantidade,
      periodicidade: meta.periodicidade,
      vigenciaInicio: parseDataIso(meta.vigenciaInicio),
      semPrazo: !meta.vigenciaFim,
      vigenciaFim: parseDataIso(meta.vigenciaFim),
      descricao: meta.descricao
    });
    // O tipo e o alvo são imutáveis: para trocá-los, cadastra-se outra meta.
    this.alternarCamposDeAlvo(false);
    if (meta.idColaborador && meta.nomeColaborador) this.colaboradorLookup.seedSelected({ value: meta.idColaborador, label: meta.nomeColaborador });
    this.abrir();
  }

  private alternarCamposDeAlvo(habilitado: boolean): void {
    (['idTipoInspecao', 'escopo', 'idFuncao', 'idColaborador'] as const).forEach((campo) => {
      const controle = this.metaForm.get(campo);
      if (habilitado) controle?.enable();
      else controle?.disable();
    });
  }

  private abrir(): void {
    this.erroFormulario.set(null);
    this.conflito.set(null);
    this.formularioAberto.set(true);
  }

  protected onEscopoChange(): void {
    // O alvo é excludente: trocar o tipo limpa o outro lado.
    this.metaForm.patchValue({ idFuncao: null, idColaborador: null });
    this.erroFormulario.set(null);
    this.conflito.set(null);
  }

  protected onPeriodicidadeChange(): void {
    if (this.metaForm.value.periodicidade === 'PERSONALIZADO') this.metaForm.patchValue({ semPrazo: false });
  }

  protected onSemPrazoChange(): void {
    if (this.metaForm.value.semPrazo) this.metaForm.patchValue({ vigenciaFim: null });
  }

  protected previa(): string | null {
    this.valores();
    const { quantidade, periodicidade, vigenciaInicio, semPrazo, vigenciaFim } = this.metaForm.getRawValue();
    return descreverMeta(quantidade, periodicidade, vigenciaInicio ? toDataIso(vigenciaInicio) : null, semPrazo || !vigenciaFim ? null : toDataIso(vigenciaFim));
  }

  protected janelaPrevia() {
    this.valores();
    const { periodicidade, vigenciaInicio, semPrazo, vigenciaFim } = this.metaForm.getRawValue();
    if (!vigenciaInicio || !periodicidade) return null;
    return calcularJanela(periodicidade, toDataIso(vigenciaInicio), semPrazo || !vigenciaFim ? null : toDataIso(vigenciaFim));
  }

  protected onSave(): void {
    const request = this.montarRequest();
    if (!request) return;
    this.saving.set(true);
    this.erroFormulario.set(null);
    this.conflito.set(null);
    const id = this.editingId();
    (id ? this.metasService.atualizarMeta(id, request) : this.metasService.criarMeta(request)).subscribe({
      next: () => {
        this.saving.set(false);
        this.metaForm.markAsPristine();
        this.formularioAberto.set(false);
        this.notificacoes.success(id ? 'Meta atualizada.' : 'Meta cadastrada.');
        this.lista.reload();
      },
      error: (erro) => {
        this.saving.set(false);
        const mensagem = getErrorMessage(erro);
        // 422 de sobreposição: mantém o formulário aberto e oferece o atalho.
        if (erro?.status === 422 && /sobrepo|vigência|vigencia/i.test(mensagem)) this.conflito.set(mensagem);
        else this.erroFormulario.set(mensagem);
      }
    });
  }

  /** Valida localmente o que o backend também barra (422/400) e monta o payload. */
  private montarRequest(): MetaInspecaoRequest | null {
    const v = this.metaForm.getRawValue();
    const alvoFuncao = v.escopo === 'FUNCAO';
    const idAlvo = alvoFuncao ? v.idFuncao : v.idColaborador;
    const erro = (mensagem: string) => {
      this.erroFormulario.set(mensagem);
      return null;
    };
    if (!v.idTipoInspecao) return erro('Selecione o tipo de inspeção da meta.');
    if (!idAlvo) return erro(alvoFuncao ? 'Selecione a função da meta.' : 'Selecione o colaborador da meta.');
    if (!v.quantidade || v.quantidade < META_QUANTIDADE_MIN || v.quantidade > META_QUANTIDADE_MAX) {
      return erro(`A quantidade deve estar entre ${META_QUANTIDADE_MIN} e ${META_QUANTIDADE_MAX}.`);
    }
    if (!v.vigenciaInicio || !v.periodicidade) return erro('Informe a periodicidade e a data de início da vigência.');
    const semPrazo = !!v.semPrazo;
    if (v.periodicidade === 'PERSONALIZADO' && (semPrazo || !v.vigenciaFim)) return erro('Metas personalizadas exigem uma data de fim.');
    if (!semPrazo && v.vigenciaFim && v.vigenciaFim < v.vigenciaInicio) return erro('A data de fim não pode ser anterior à data de início.');
    return {
      idTipoInspecao: v.idTipoInspecao,
      idFuncao: alvoFuncao ? idAlvo : null,
      idColaborador: alvoFuncao ? null : idAlvo,
      quantidade: v.quantidade,
      periodicidade: v.periodicidade,
      vigenciaInicio: toDataIso(v.vigenciaInicio),
      vigenciaFim: semPrazo || !v.vigenciaFim ? null : toDataIso(v.vigenciaFim),
      descricao: v.descricao?.trim() || null
    };
  }

  /** Atalho do 422 de sobreposição: lista as metas ativas do mesmo alvo. */
  protected filtrarPorAlvoEmConflito(): void {
    const { escopo, idFuncao, idColaborador, idTipoInspecao } = this.metaForm.getRawValue();
    this.lista.reset();
    this.lista.filters.idTipoInspecao.set(idTipoInspecao);
    this.lista.filters.escopo.set(escopo);
    this.lista.filters.idFuncao.set(escopo === 'FUNCAO' ? idFuncao : null);
    this.lista.filters.idColaborador.set(escopo === 'COLABORADOR' ? idColaborador : null);
    this.lista.filters.ativo.set(true);
    this.lista.apply();
    this.metaForm.markAsPristine();
    this.formularioAberto.set(false);
    this.notificacoes.info('A lista mostra as metas ativas do alvo em conflito.');
  }

  // ---------------------------------------------------------- Ações da lista
  protected abrirAcompanhamento(): void {
    void this.router.navigate(['/sst/metas-inspecao/acompanhamento']);
  }

  protected async toggleStatus(meta: MetaInspecaoResponse): Promise<void> {
    const desativar = meta.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar meta' : 'Reativar meta',
      message: desativar ? 'A meta sai das apurações, mas continua listável e pode ser reativada.' : 'A meta volta a ser cobrada nas apurações.',
      confirmLabel: desativar ? 'Desativar' : 'Reativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.metasService.desativarMeta(meta.id) : this.metasService.ativarMeta(meta.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Meta desativada.' : 'Meta reativada.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected nomeAlvo(meta: MetaInspecaoResponse): string {
    return (meta.escopo === 'FUNCAO' ? meta.nomeFuncao : meta.nomeColaborador) ?? 'Cadastro não disponível';
  }

  protected rotuloEscopo(meta: MetaInspecaoResponse): string {
    return ESCOPO_META_LABELS[meta.escopo];
  }

  protected rotuloPeriodicidade(meta: MetaInspecaoResponse): string {
    return PERIODICIDADE_LABELS[meta.periodicidade];
  }
}
