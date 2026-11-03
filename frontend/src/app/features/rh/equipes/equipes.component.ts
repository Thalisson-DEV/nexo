import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CesAutocompleteComponent,
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

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup as BuscaColaboradores } from '../../../core/lookup/store/remote-lookup';
import { ContratoResponse } from '../../../core/models/contrato.models';
import {
  BaseOperacionalResponse,
  CoordenadorMinResponse,
  EquipeImportacaoDTO,
  EquipeRequest,
  EquipeResponse,
  FuncaoMembro,
  ProcessoNegocioResponse,
  SupervisorMinResponse,
  TipoEquipeResponse,
  VeiculoResponse
} from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ContratoService } from '../../../core/services/contrato.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

export interface MembroEquipe {
  readonly idColaborador: string;
  readonly nome: string;
  readonly funcao: FuncaoMembro;
}

const FUNCOES_MEMBRO: CesSelectOption<FuncaoMembro>[] = [
  { label: 'Responsável', value: 'RESPONSAVEL' },
  { label: 'Parceiro', value: 'PARCEIRO' }
];

@Component({
  selector: 'app-equipes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    CesAutocompleteComponent,
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
        title="Equipes"
        icon="pi pi-users"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Cadastros' }, { label: 'Equipes' }]"
        description="Equipes de campo com base, veículo, hierarquia (coordenador e supervisor), contrato e membros."
        helpLabel="Ajuda sobre equipes"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacao.aberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Nova equipe" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="rh-equipes" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Base operacional" [options]="basesOpcoes()" [(value)]="lista.filters.idBaseOperacional" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Tipo de equipe" [options]="tiposOpcoes()" [(value)]="lista.filters.idTipoEquipe" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Processo" [options]="processosOpcoes()" [(value)]="lista.filters.idProcesso" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Veículo" [options]="veiculosOpcoes()" [value]="lista.filters.idVeiculo()" (valueChange)="filtrarVeiculo($event)" [clearable]="true"
            [filter]="true" [loading]="veiculos.loading()" placeholder="Todos" (opened)="veiculos.open()" (search)="veiculos.search($event)"></ces-select>
          <ces-select label="Coordenador" [options]="coordenadoresOpcoes()" [value]="lista.filters.idCoordenador()" (valueChange)="filtrarCoordenador($event)"
            [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Supervisor" [options]="supervisoresDoFiltro()" [(value)]="lista.filters.idSupervisor" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-autocomplete label="Responsável" placeholder="Digite o nome" [options]="colaboradores.items()" [ngModel]="lista.filters.idResponsavel()"
            (ngModelChange)="filtrarColaborador('idResponsavel', $event)" [loading]="colaboradores.loading()" [status]="colaboradores.status()" [clearable]="true"
            (search)="colaboradores.search($event)" (loadMore)="colaboradores.loadNext()" (retry)="colaboradores.retry()"></ces-autocomplete>
          <ces-autocomplete label="Membro" placeholder="Digite o nome" [options]="colaboradores.items()" [ngModel]="lista.filters.idMembro()"
            (ngModelChange)="filtrarColaborador('idMembro', $event)" [loading]="colaboradores.loading()" [status]="colaboradores.status()" [clearable]="true"
            (search)="colaboradores.search($event)" (loadMore)="colaboradores.loadNext()" (retry)="colaboradores.retry()"></ces-autocomplete>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Equipes"
        mode="server"
        storageKey="rh-equipes"
        exportFileName="equipes"
        searchPlaceholder="Código, base, placa ou pessoas"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Nova equipe"
        noResultsTitle="Nenhuma equipe encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as equipes' : 'Nenhuma equipe cadastrada'"
        [stateDescription]="resultado.error() ?? 'Cadastre uma equipe ou importe uma planilha.'"
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
        <ng-template cesCell="codigo" let-row>
          <span class="ces-cell-strong">{{ row.codigo }}</span>
          @if (responsavelDe(row); as r) {
            <span class="ces-cell-muted">{{ r }}</span>
          }
        </ng-template>
        <ng-template cesCell="contrato" let-row>
          @if (row.idContrato) {
            {{ numeroContrato(row.idContrato) }}
          } @else {
            <ces-status tone="neutral" emphasis="outline">Sem contrato</ces-status>
          }
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativa' : 'Inativa' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.codigo" size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.codigo" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar equipe' : 'Nova equipe'" [description]="emEdicao()?.codigo"
      [dirty]="alterado()" size="lg">
      <ces-form-section title="Identificação" icon="pi pi-tag">
        <div class="ces-form-grid">
          <ces-text-input label="Código" [required]="true" [value]="codigo()" (valueChange)="codigo.set($event.toUpperCase())" [maxlength]="30" placeholder="Ex.: EQ-01" [error]="erro('codigo')"></ces-text-input>
          <ces-select label="Tipo de equipe" [required]="true" [options]="tiposOpcoes()" [(value)]="idTipo" [filter]="true" [error]="erro('tipo')"></ces-select>
        </div>
        @if (emEdicao()) {
          <ces-boolean-control label="Equipe ativa" kind="toggle" [(value)]="ativo"></ces-boolean-control>
        }
      </ces-form-section>

      <ces-form-section title="Operação" icon="pi pi-briefcase">
        <div class="ces-form-grid">
          <ces-select label="Base operacional" [required]="true" [options]="basesOpcoes()" [(value)]="idBase" [filter]="true" [error]="erro('base')"></ces-select>
          <ces-select label="Processo" [required]="true" [options]="processosOpcoes()" [(value)]="idProcesso" [filter]="true" [error]="erro('processo')"></ces-select>
          <ces-select label="Veículo" [required]="true" [options]="veiculosOpcoes()" [(value)]="idVeiculo" [filter]="true" [loading]="veiculos.loading()"
            (opened)="veiculos.open()" (search)="veiculos.search($event)" placeholder="Pesquise pela placa" [error]="erro('veiculo')"></ces-select>
          <ces-select label="Contrato" [options]="contratosOpcoes()" [(value)]="idContrato" [filter]="true" [clearable]="true" [showOptional]="true"
            description="Só contratos vigentes. Limpe o campo para desvincular."></ces-select>
          <ces-number-input label="Jornada" suffix=" min" [min]="1" [max]="1440" [showOptional]="true" [(value)]="jornada" description="Em branco, usa a do tipo de equipe."></ces-number-input>
          <ces-number-input label="Tempo de plataforma" suffix=" min" [min]="0" [showOptional]="true" [(value)]="plataforma" description="Em branco, usa o do tipo de equipe."></ces-number-input>
        </div>
      </ces-form-section>

      <ces-form-section title="Hierarquia" icon="pi pi-sitemap" description="O supervisor precisa responder ao coordenador da equipe.">
        <div class="ces-form-grid">
          <ces-select label="Coordenador" [required]="true" [options]="coordenadoresOpcoes()" [value]="idCoordenador()" (valueChange)="escolherCoordenador($event)"
            [filter]="true" [error]="erro('coordenador')"></ces-select>
          <ces-select label="Supervisor" [required]="true" [options]="supervisoresDisponiveis()" [(value)]="idSupervisor" [filter]="true"
            [disabled]="!idCoordenador()" [placeholder]="idCoordenador() ? 'Selecione' : 'Escolha primeiro o coordenador'"
            [description]="idCoordenador() && !supervisoresDisponiveis().length ? 'Nenhum supervisor ativo responde a este coordenador.' : undefined"
            [error]="erro('supervisor')"></ces-select>
        </div>
      </ces-form-section>

      <ces-form-section title="Membros" icon="pi pi-users" description="A equipe precisa de um responsável. Coordenador e supervisor não entram como membros.">
        <div class="ces-form-grid">
          <ces-autocomplete label="Colaborador" placeholder="Digite o nome" [options]="colaboradoresParaMembro()" [ngModel]="novoMembro()"
            (ngModelChange)="novoMembro.set($event)" [loading]="colaboradores.loading()" [status]="colaboradores.status()" [clearable]="true"
            (search)="colaboradores.search($event)" (loadMore)="colaboradores.loadNext()" (retry)="colaboradores.retry()"></ces-autocomplete>
          <ces-select label="Função na equipe" [options]="funcoesMembro" [(value)]="novaFuncao"></ces-select>
        </div>
        <div class="ces-actions-row">
          <ces-button variant="outline" icon="pi pi-plus" label="Adicionar membro" [disabled]="!novoMembro() || !novaFuncao()" (pressed)="adicionarMembro()"></ces-button>
        </div>
        @if (erro('membros'); as mensagem) {
          <ces-callout tone="danger">{{ mensagem }}</ces-callout>
        }
        <ces-data-table caption="Membros da equipe" [searchable]="false" [columns]="colunasMembros" [rows]="membros()" [rowKey]="chaveMembro"
          [state]="membros().length ? 'ready' : 'empty'" stateTitle="Nenhum membro adicionado" stateDescription="Adicione o responsável e os parceiros.">
          <ng-template cesCell="funcao" let-row>
            <ces-status [tone]="row.funcao === 'RESPONSAVEL' ? 'brand' : 'neutral'" [icon]="row.funcao === 'RESPONSAVEL' ? 'pi pi-star' : undefined">
              {{ row.funcao === 'RESPONSAVEL' ? 'Responsável' : 'Parceiro' }}
            </ces-status>
          </ng-template>
          <ng-template cesRowActions let-row>
            <span class="ces-table-actions">
              @if (row.funcao !== 'RESPONSAVEL') {
                <ces-icon-button icon="pi pi-star" [label]="'Tornar ' + row.nome + ' responsável'" size="icon-sm" (pressed)="tornarResponsavel(row)"></ces-icon-button>
              }
              <ces-icon-button icon="pi pi-trash" [label]="'Remover ' + row.nome" size="icon-sm" (pressed)="removerMembro(row)"></ces-icon-button>
            </span>
          </ng-template>
        </ces-data-table>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar equipe'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer [(visible)]="importacao.aberta" title="Importar equipes"
      description="Planilha CSV com código, tipo, base, placa, coordenador e supervisor (pelos nomes cadastrados)."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao.linhas()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="codigo" let-row>
            @if (row.editing) { <ces-text-input label="Código" [labelVisible]="false" [(value)]="row.codigo"></ces-text-input> } @else { {{ row.codigo }} }
          </ng-template>
          <ng-template cesCell="tipo" let-row>
            @if (row.editing) {
              <ces-select label="Tipo" [labelVisible]="false" [options]="tiposPorNome()" [(value)]="row.nomeTipoEquipe" [filter]="true"></ces-select>
            } @else {
              {{ row.nomeTipoEquipe || '—' }}
            }
          </ng-template>
          <ng-template cesCell="base" let-row>
            @if (row.editing) {
              <ces-select label="Base" [labelVisible]="false" [options]="basesPorNome()" [(value)]="row.nomeBase" [filter]="true"></ces-select>
            } @else {
              {{ row.nomeBase || row.nomeBaseOperacional || '—' }}
            }
          </ng-template>
          <ng-template cesCell="placa" let-row>
            @if (row.editing) { <ces-text-input label="Placa" [labelVisible]="false" [value]="row.placaVeiculo ?? ''" (valueChange)="row.placaVeiculo = $event.toUpperCase()"></ces-text-input> } @else { {{ row.placaVeiculo || '—' }} }
          </ng-template>
          <ng-template cesCell="coordenador" let-row>
            @if (row.editing) {
              <ces-select label="Coordenador" [labelVisible]="false" [options]="coordenadoresPorNome()" [(value)]="row.nomeCoordenador" [filter]="true"></ces-select>
            } @else {
              {{ row.nomeCoordenador || '—' }}
            }
          </ng-template>
          <ng-template cesCell="supervisor" let-row>
            @if (row.editing) {
              <ces-select label="Supervisor" [labelVisible]="false" [options]="supervisoresPorNome()" [(value)]="row.nomeSupervisor" [filter]="true"></ces-select>
            } @else {
              {{ row.nomeSupervisor || '—' }}
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
    <app-ajuda-listagem #ajuda titulo="Equipes" entidadePlural="equipes" busca="código, base, placa ou nome de pessoas" rotuloNovo="Nova equipe" [importacao]="true">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-sitemap"></i> Hierarquia</h4>
        <p class="help-text-desc">Escolha primeiro o coordenador: a lista de supervisores mostra só quem responde a ele.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-star"></i> Responsável</h4>
        <p class="help-text-desc">Toda equipe tem exatamente um responsável. Ao escolher outro, o anterior passa a parceiro.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class EquipesComponent {
  private readonly rh = inject(RhService);
  private readonly contratosService = inject(ContratoService);
  private readonly importacaoService = inject(ImportacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly lookups = inject(LookupStore);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly funcoesMembro = FUNCOES_MEMBRO;

  protected readonly bases = signal<BaseOperacionalResponse[]>([]);
  protected readonly tipos = signal<TipoEquipeResponse[]>([]);
  protected readonly processos = signal<ProcessoNegocioResponse[]>([]);
  protected readonly coordenadores = signal<CoordenadorMinResponse[]>([]);
  protected readonly supervisores = signal<SupervisorMinResponse[]>([]);
  protected readonly contratos = signal<ContratoResponse[]>([]);
  /** Rótulos de veículos e colaboradores escolhidos (chips e seleção fora da página carregada). */
  private readonly rotulos = signal<Record<string, string>>({});

  protected readonly basesOpcoes = computed(() => this.bases().map((b) => ({ label: b.nome, value: b.id })));
  protected readonly tiposOpcoes = computed(() => this.tipos().map((t) => ({ label: t.nome, value: t.id })));
  protected readonly processosOpcoes = computed(() => this.processos().map((p) => ({ label: p.nome, value: p.id })));
  protected readonly coordenadoresOpcoes = computed(() => this.coordenadores().map((c) => ({ label: c.nomeColaborador, value: c.id })));
  protected readonly basesPorNome = computed(() => this.bases().map((b) => ({ label: b.nome, value: b.nome })));
  protected readonly tiposPorNome = computed(() => this.tipos().map((t) => ({ label: t.nome, value: t.nome })));
  protected readonly coordenadoresPorNome = computed(() => this.coordenadores().map((c) => ({ label: c.nomeColaborador, value: c.nomeColaborador })));
  protected readonly supervisoresPorNome = computed(() => this.supervisores().map((s) => ({ label: s.nomeColaborador, value: s.nomeColaborador })));
  protected readonly supervisoresDoFiltro = computed(() => supervisoresDe(this.supervisores(), this.lista.filters.idCoordenador()));
  protected readonly contratosOpcoes = computed<CesSelectOption<string>[]>(() => {
    const atual = this.emEdicao()?.idContrato;
    return this.contratos()
      .filter((c) => c.id === atual || vigente(c))
      .map((c) => ({ label: c.cliente ? `${c.numeroContrato} · ${c.cliente}` : c.numeroContrato, value: c.id }));
  });

  protected readonly veiculos = new RemoteLookup<VeiculoResponse>(
    {
      identify: (v) => v.id,
      selectedKeys: () => [this.lista.filters.idVeiculo(), this.idVeiculo()].filter((id): id is string => !!id),
      load: ({ query, page, size }) => this.rh.listarVeiculos({ ativo: true, buscaGlobal: query || undefined, page, size, sort: 'placa,asc' })
    },
    inject(DestroyRef)
  );
  protected readonly veiculosOpcoes = computed<CesSelectOption<string>[]>(() =>
    this.veiculos.items().map((v) => ({ label: [v.placa, v.modelo].filter(Boolean).join(' · '), value: v.id }))
  );
  protected readonly colaboradores = new BuscaColaboradores<string>({
    loader: ({ query, cursor }) => this.lookups.searchCollaborators({ query, cursor }),
    selectedValues: () =>
      [this.lista.filters.idResponsavel(), this.lista.filters.idMembro(), this.novoMembro()].filter((id): id is string => !!id),
    destroyRef: inject(DestroyRef)
  });

  protected readonly lista = cesListState({
    storageKey: 'rh-equipes',
    filters: {
      idBaseOperacional: cesFilter.value<string | null>(null, { label: 'Base', param: 'base', format: (id): string | null => (id ? nomeEm(this.bases(), id) : null) }),
      idTipoEquipe: cesFilter.value<string | null>(null, { label: 'Tipo', param: 'tipo', format: (id): string | null => (id ? nomeEm(this.tipos(), id) : null) }),
      idProcesso: cesFilter.value<string | null>(null, { label: 'Processo', param: 'processo', format: (id): string | null => (id ? nomeEm(this.processos(), id) : null) }),
      idVeiculo: cesFilter.value<string | null>(null, { label: 'Veículo', param: 'veiculo', format: (id): string | null => (id ? this.rotulo_(id, 'veículo selecionado') : null) }),
      idCoordenador: cesFilter.value<string | null>(null, {
        label: 'Coordenador',
        param: 'coordenador',
        format: (id): string | null => (id ? (this.coordenadores().find((c) => c.id === id)?.nomeColaborador ?? 'coordenador selecionado') : null)
      }),
      idSupervisor: cesFilter.value<string | null>(null, {
        label: 'Supervisor',
        param: 'supervisor',
        format: (id): string | null => (id ? (this.supervisores().find((s) => s.id === id)?.nomeColaborador ?? 'supervisor selecionado') : null)
      }),
      idResponsavel: cesFilter.value<string | null>(null, { label: 'Responsável', param: 'responsavel', format: (id): string | null => (id ? this.rotulo_(id, 'colaborador selecionado') : null) }),
      idMembro: cesFilter.value<string | null>(null, { label: 'Membro', param: 'membro', format: (id): string | null => (id ? this.rotulo_(id, 'colaborador selecionado') : null) }),
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'codigo', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<EquipeResponse>(
    (pedido) => this.rh.listarEquipes({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.rh.listarEquipes({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<EquipeResponse>[] = [
    { key: 'codigo', header: 'Equipe', type: 'custom', priority: 1, sortField: 'codigo', value: (e) => e.codigo, display: (e) => e.codigo },
    { key: 'tipo', header: 'Tipo', sortField: 'tipoEquipe.nome', value: (e) => e.tipoEquipe?.nome, display: (e) => e.tipoEquipe?.nome ?? '—' },
    { key: 'processo', header: 'Processo', sortField: 'processo.nome', value: (e) => e.processo?.nome, display: (e) => e.processo?.nome ?? '—' },
    { key: 'base', header: 'Base', priority: 1, sortField: 'baseOperacional.nome', value: (e) => e.baseOperacional?.nome, display: (e) => e.baseOperacional?.nome ?? '—' },
    { key: 'veiculo', header: 'Veículo', sortField: 'veiculo.placa', value: (e) => e.veiculo?.placa, display: (e) => e.veiculo?.placa ?? '—' },
    { key: 'coordenador', header: 'Coordenador', sortField: 'coordenador.colaborador.nomeCompleto', value: (e) => e.coordenador?.colaborador?.nomeCompleto, display: (e) => e.coordenador?.colaborador?.nomeCompleto ?? '—' },
    { key: 'supervisor', header: 'Supervisor', sortField: 'supervisor.colaborador.nomeCompleto', value: (e) => e.supervisor?.colaborador?.nomeCompleto, display: (e) => e.supervisor?.colaborador?.nomeCompleto ?? '—' },
    { key: 'contrato', header: 'Contrato', type: 'custom', value: (e) => e.idContrato, display: (e) => (e.idContrato ? this.numeroContrato(e.idContrato) : 'Sem contrato') },
    { key: 'ativo', header: 'Status', type: 'custom', sortField: 'ativo', value: (e) => e.ativo, display: (e) => (e.ativo ? 'Ativa' : 'Inativa') }
  ];
  protected readonly rotulo = (e: EquipeResponse) => e.codigo;

  // Formulário
  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<EquipeResponse | null>(null);
  protected readonly codigo = signal('');
  protected readonly idTipo = signal<string | null>(null);
  protected readonly idBase = signal<string | null>(null);
  protected readonly idProcesso = signal<string | null>(null);
  protected readonly idVeiculo = signal<string | null>(null);
  protected readonly idContrato = signal<string | null>(null);
  protected readonly jornada = signal<number | null>(null);
  protected readonly plataforma = signal<number | null>(null);
  protected readonly idCoordenador = signal<string | null>(null);
  protected readonly idSupervisor = signal<string | null>(null);
  protected readonly ativo = signal(true);
  protected readonly membros = signal<MembroEquipe[]>([]);
  protected readonly novoMembro = signal<string | null>(null);
  protected readonly novaFuncao = signal<FuncaoMembro | null>('PARCEIRO');
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  private readonly fotografia = signal('');

  protected readonly supervisoresDisponiveis = computed(() => supervisoresDe(this.supervisores(), this.idCoordenador()));
  /** Coordenador e supervisor da equipe, e quem já é membro, não aparecem para adicionar. */
  protected readonly colaboradoresParaMembro = computed(() => {
    const fora = new Set(this.membros().map((m) => m.idColaborador));
    const coordenador = this.coordenadores().find((c) => c.id === this.idCoordenador())?.idColaborador;
    const supervisor = this.supervisores().find((s) => s.id === this.idSupervisor())?.idColaborador;
    if (coordenador) fora.add(coordenador);
    if (supervisor) fora.add(supervisor);
    return this.colaboradores.items().filter((c) => !fora.has(c.value));
  });
  protected readonly colunasMembros: CesDataTableColumn<MembroEquipe>[] = [
    { key: 'nome', header: 'Colaborador', priority: 1 },
    { key: 'funcao', header: 'Função', type: 'custom', value: (m) => m.funcao }
  ];
  protected readonly chaveMembro = (m: MembroEquipe) => m.idColaborador;

  protected readonly alterado = computed(() => this.formularioAberto() && this.estadoFormulario() !== this.fotografia());
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.codigo().trim()) e['codigo'] = 'Informe o código.';
    if (!this.idTipo()) e['tipo'] = 'Escolha o tipo de equipe.';
    if (!this.idBase()) e['base'] = 'Escolha a base.';
    if (!this.idProcesso()) e['processo'] = 'Escolha o processo.';
    if (!this.idVeiculo()) e['veiculo'] = 'Escolha o veículo.';
    if (!this.idCoordenador()) e['coordenador'] = 'Escolha o coordenador.';
    if (!this.idSupervisor()) e['supervisor'] = 'Escolha o supervisor.';
    if (!this.membros().some((m) => m.funcao === 'RESPONSAVEL')) e['membros'] = 'Adicione o responsável da equipe.';
    return e;
  });

  protected readonly importacao = importacaoListagem<EquipeImportacaoDTO>({
    enviar: (arquivo) => this.rh.importarEquipes(arquivo),
    confirmar: (equipes) => this.rh.confirmarImportacaoEquipes({ equipes }),
    modelo: () => this.importacaoService.baixarModeloCsv('/v1/equipes/importar/modelo', 'modelo-equipes.csv'),
    aoProcessar: () => this.lista.reload()
  });
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = CHAVE_LINHA_IMPORTACAO;
  protected readonly colunasImportacao: CesDataTableColumn<EquipeImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'codigo', header: 'Código', type: 'custom', priority: 1, value: (r) => r.codigo },
    { key: 'tipo', header: 'Tipo', type: 'custom', value: (r) => r.nomeTipoEquipe },
    { key: 'base', header: 'Base', type: 'custom', value: (r) => r.nomeBase ?? r.nomeBaseOperacional },
    { key: 'placa', header: 'Placa', type: 'custom', value: (r) => r.placaVeiculo },
    { key: 'coordenador', header: 'Coordenador', type: 'custom', value: (r) => r.nomeCoordenador },
    { key: 'supervisor', header: 'Supervisor', type: 'custom', value: (r) => r.nomeSupervisor },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  constructor() {
    const ignorar = () => undefined;
    this.rh.listarBasesOperacionaisAtivas().subscribe({ next: (r) => this.bases.set(r.content), error: ignorar });
    this.rh.listarTiposEquipeAtivos().subscribe({ next: (r) => this.tipos.set(r.content), error: ignorar });
    this.rh.listarProcessosAtivos().subscribe({ next: (r) => this.processos.set(r.content ?? []), error: ignorar });
    this.rh.listarCoordenadoresAtivosMin().subscribe({ next: (r) => this.coordenadores.set(r), error: ignorar });
    this.rh.listarSupervisoresAtivosMin().subscribe({ next: (r) => this.supervisores.set(r), error: ignorar });
    this.contratosService.listarContratosAtivos().subscribe({ next: (r) => this.contratos.set(r.content), error: ignorar });
    // Recorte vindo da URL: busca os nomes para os chips e as seleções.
    const { filters } = this.lista.request();
    if (filters.idVeiculo) this.rh.buscarVeiculoPorId(filters.idVeiculo).subscribe({ next: (v) => this.lembrarVeiculo(v), error: ignorar });
    for (const id of [filters.idResponsavel, filters.idMembro]) {
      if (id) this.rh.buscarColaboradorPorId(id).subscribe({ next: (c) => this.lembrarColaborador(c.id, c.nomeCompleto), error: ignorar });
    }
  }

  private filtro() {
    const { filters, query } = this.lista.request();
    return {
      idBaseOperacional: filters.idBaseOperacional ?? undefined,
      idTipoEquipe: filters.idTipoEquipe ?? undefined,
      idProcesso: filters.idProcesso ?? undefined,
      idVeiculo: filters.idVeiculo ?? undefined,
      idCoordenador: filters.idCoordenador ?? undefined,
      idSupervisor: filters.idSupervisor ?? undefined,
      idResponsavel: filters.idResponsavel ?? undefined,
      idMembro: filters.idMembro ?? undefined,
      ativo: filters.ativo,
      buscaGlobal: query || undefined
    };
  }

  private rotulo_(id: string, padrao: string): string {
    return this.rotulos()[id] ?? padrao;
  }

  private lembrarVeiculo(v: VeiculoResponse): void {
    this.veiculos.setSeed([v]);
    this.rotulos.update((r) => ({ ...r, [v.id]: v.placa }));
  }

  private lembrarColaborador(id: string, nome: string): void {
    this.colaboradores.seedSelected({ value: id, label: nome });
    this.rotulos.update((r) => ({ ...r, [id]: nome }));
  }

  protected filtrarVeiculo(id: string | null): void {
    const v = this.veiculos.items().find((item) => item.id === id);
    if (v) this.rotulos.update((r) => ({ ...r, [v.id]: v.placa }));
    this.lista.filters.idVeiculo.set(id);
  }

  protected filtrarCoordenador(id: string | null): void {
    this.lista.filters.idCoordenador.set(id);
    const supervisor = this.lista.filters.idSupervisor();
    if (id && supervisor && !supervisoresDe(this.supervisores(), id).some((s) => s.value === supervisor)) this.lista.filters.idSupervisor.set(null);
  }

  protected filtrarColaborador(filtro: 'idResponsavel' | 'idMembro', id: string | null): void {
    const opcao = this.colaboradores.items().find((c) => c.value === id);
    if (opcao) this.rotulos.update((r) => ({ ...r, [opcao.value]: opcao.label }));
    this.lista.filters[filtro].set(id);
  }

  protected numeroContrato(id: string): string {
    return this.contratos().find((c) => c.id === id)?.numeroContrato ?? 'Contrato vinculado';
  }

  protected responsavelDe(e: EquipeResponse): string | null {
    const r = e.membros?.find((m) => m.vinculoAtivo && m.funcao === 'RESPONSAVEL');
    return r ? `Responsável: ${r.nomeColaborador}` : null;
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected escolherCoordenador(id: string | null): void {
    this.idCoordenador.set(id);
    if (!supervisoresDe(this.supervisores(), id).some((s) => s.value === this.idSupervisor())) this.idSupervisor.set(null);
  }

  protected async adicionarMembro(): Promise<void> {
    const id = this.novoMembro();
    const funcao = this.novaFuncao();
    if (!id || !funcao || this.membros().some((m) => m.idColaborador === id)) return;
    const nome = this.colaboradores.items().find((c) => c.value === id)?.label ?? 'Colaborador';
    const responsavelAtual = this.membros().find((m) => m.funcao === 'RESPONSAVEL');
    if (funcao === 'RESPONSAVEL' && responsavelAtual) {
      const ok = await this.confirmacao.confirm({
        title: 'Trocar o responsável',
        message: `${responsavelAtual.nome} passa a parceiro e ${nome} vira o responsável da equipe.`,
        confirmLabel: 'Trocar responsável'
      });
      if (!ok) return;
    }
    this.membros.update((lista) => [
      ...(funcao === 'RESPONSAVEL' ? lista.map((m) => ({ ...m, funcao: 'PARCEIRO' as FuncaoMembro })) : lista),
      { idColaborador: id, nome, funcao }
    ]);
    this.novoMembro.set(null);
    this.novaFuncao.set(this.membros().some((m) => m.funcao === 'RESPONSAVEL') ? 'PARCEIRO' : 'RESPONSAVEL');
  }

  protected async tornarResponsavel(membro: MembroEquipe): Promise<void> {
    const atual = this.membros().find((m) => m.funcao === 'RESPONSAVEL');
    if (atual) {
      const ok = await this.confirmacao.confirm({
        title: 'Trocar o responsável',
        message: `${atual.nome} passa a parceiro e ${membro.nome} vira o responsável da equipe.`,
        confirmLabel: 'Trocar responsável'
      });
      if (!ok) return;
    }
    this.membros.update((lista) =>
      lista.map((m) => ({ ...m, funcao: (m.idColaborador === membro.idColaborador ? 'RESPONSAVEL' : 'PARCEIRO') as FuncaoMembro }))
    );
  }

  protected removerMembro(membro: MembroEquipe): void {
    this.membros.update((lista) => lista.filter((m) => m.idColaborador !== membro.idColaborador));
  }

  protected novo(): void {
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(e: EquipeResponse): void {
    this.preencher(e);
    this.formularioAberto.set(true);
  }

  private preencher(e: EquipeResponse | null): void {
    this.emEdicao.set(e);
    if (e?.veiculo) this.veiculos.setSeed([e.veiculo]);
    const ativos = (e?.membros ?? []).filter((m) => m.vinculoAtivo);
    this.colaboradores.seedSelected(ativos.map((m) => ({ value: m.idColaborador, label: m.nomeColaborador })));
    this.codigo.set(e?.codigo ?? '');
    this.idTipo.set(e?.tipoEquipe?.id ?? null);
    this.idBase.set(e?.baseOperacional?.id ?? null);
    this.idProcesso.set(e?.processo?.id ?? null);
    this.idVeiculo.set(e?.veiculo?.id ?? null);
    this.idContrato.set(e?.idContrato ?? null);
    this.jornada.set(e?.jornadaMinutos ?? null);
    this.plataforma.set(e?.plataformaMinutos ?? null);
    this.idCoordenador.set(e?.coordenador?.id ?? null);
    this.idSupervisor.set(e?.supervisor?.id ?? null);
    this.ativo.set(e?.ativo ?? true);
    this.membros.set(ativos.map((m) => ({ idColaborador: m.idColaborador, nome: m.nomeColaborador, funcao: m.funcao === 'RESPONSAVEL' ? 'RESPONSAVEL' : 'PARCEIRO' })));
    this.novoMembro.set(null);
    this.novaFuncao.set(ativos.some((m) => m.funcao === 'RESPONSAVEL') ? 'PARCEIRO' : 'RESPONSAVEL');
    this.tentouSalvar.set(false);
    this.fotografia.set(this.estadoFormulario());
  }

  private estadoFormulario(): string {
    return JSON.stringify([
      this.codigo(),
      this.idTipo(),
      this.idBase(),
      this.idProcesso(),
      this.idVeiculo(),
      this.idContrato(),
      this.jornada(),
      this.plataforma(),
      this.idCoordenador(),
      this.idSupervisor(),
      this.ativo(),
      this.membros()
    ]);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const ativo = this.ativo();
    const request: EquipeRequest = {
      codigo: this.codigo().trim(),
      idTipoEquipe: this.idTipo()!,
      idBaseOperacional: this.idBase()!,
      idVeiculo: this.idVeiculo()!,
      idCoordenador: this.idCoordenador()!,
      idSupervisor: this.idSupervisor()!,
      idProcesso: this.idProcesso()!,
      idContrato: this.idContrato(),
      jornadaMinutos: this.jornada(),
      plataformaMinutos: this.plataforma(),
      membros: this.membros().map((m) => ({ idColaborador: m.idColaborador, funcao: m.funcao }))
    };
    this.salvando.set(true);
    (atual ? this.rh.atualizarEquipe(atual.id, request) : this.rh.criarEquipe(request)).subscribe({
      next: (salva) => {
        if (atual && salva && salva.ativo !== ativo) {
          (ativo ? this.rh.ativarEquipe(atual.id) : this.rh.desativarEquipe(atual.id)).subscribe({ error: (e) => this.notificacoes.error(getErrorMessage(e)) });
        }
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(
          salva ? (atual ? 'Equipe atualizada.' : 'Equipe cadastrada.') : 'Sem conexão: a equipe será enviada quando a internet voltar.'
        );
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(e: EquipeResponse): Promise<void> {
    const desativar = e.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar equipe' : 'Ativar equipe',
      message: desativar ? `${e.codigo} deixa de aparecer nas seleções. O histórico é mantido.` : `${e.codigo} volta a aparecer nas seleções.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.rh.desativarEquipe(e.id) : this.rh.ativarEquipe(e.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Equipe desativada.' : 'Equipe ativada.');
        this.lista.reload();
      },
      error: (erro) => this.notificacoes.error(getErrorMessage(erro))
    });
  }

  protected logs(e: EquipeResponse): void {
    this.auditoria().show(e.id, e.codigo, {
      codigo: 'Código',
      idTipoEquipe: 'Tipo',
      idBaseOperacional: 'Base',
      idVeiculo: 'Veículo',
      idCoordenador: 'Coordenador',
      idSupervisor: 'Supervisor',
      idProcesso: 'Processo',
      idContrato: 'Contrato',
      ativo: 'Status'
    });
  }
}

function nomeEm(itens: readonly { id: string; nome: string }[], id: string): string {
  return itens.find((i) => i.id === id)?.nome ?? 'selecionado';
}

function supervisoresDe(supervisores: readonly SupervisorMinResponse[], idCoordenador: string | null): CesSelectOption<string>[] {
  return supervisores.filter((s) => !idCoordenador || s.idCoordenador === idCoordenador).map((s) => ({ label: s.nomeColaborador, value: s.id }));
}

function vigente(c: ContratoResponse): boolean {
  if (!c.ativo) return false;
  const hoje = new Date();
  hoje.setHours(0, 0, 0, 0);
  const inicio = c.dataVigencia ? new Date(c.dataVigencia) : null;
  const fim = c.dataEncerramento ? new Date(c.dataEncerramento) : null;
  return !(inicio && inicio > hoje) && !(fim && fim < hoje);
}
