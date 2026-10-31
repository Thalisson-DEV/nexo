import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
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
  CesNumberInputComponent,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';
import { catchError, map, of } from 'rxjs';

import { AcaoResponse } from '../../../core/models/contrato.models';
import { MaterialResponse } from '../../../core/models/almoxarifado.models';
import {
  CLASSES_TENSAO,
  ClasseTensao,
  ItemKitRequest,
  ItemKitResponse,
  KitConstrutivoRequest,
  KitConstrutivoResponse,
  OperacaoItemKit,
  TipoItemKit,
  rotuloClasseTensao
} from '../../../core/models/obras-kit.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { AuthService } from '../../../core/services/auth.service';
import { ContratoService } from '../../../core/services/contrato.service';
import { ObrasKitService } from '../../../core/services/obras-kit.service';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';
import { ImportacaoPlanilhaObrasComponent } from '../importacao/importacao-planilha-obras.component';
import { COLUNAS_KITS, fonteKits } from '../importacao/importacoes-obras';
import { formatarMoeda } from '../obras-ui';

/** Item em edição no drawer; `chave` identifica a linha mesmo antes de ser salva. */
interface ItemEmEdicao {
  chave: string;
  id?: string | null;
  tipo: TipoItemKit;
  idReferencia: string;
  codigo: string;
  descricao: string;
  operacao: OperacaoItemKit;
  unidade: string | null;
  quantidade: number;
  valorUnitario?: number | null;
  valorTotal?: number | null;
}

const TIPOS_ITEM: CesSelectOption<TipoItemKit>[] = [
  { label: 'Serviço', value: 'SERVICO' },
  { label: 'Material', value: 'MATERIAL' }
];
const OPERACOES: CesSelectOption<OperacaoItemKit>[] = [
  { label: 'Instalar', value: 'INSTALAR' },
  { label: 'Retirar', value: 'RETIRAR' }
];

@Component({
  selector: 'app-kits-construtivos',
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
    CesNumberInputComponent,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AuditDialogComponent,
    AjudaListagemComponent,
    ImportacaoPlanilhaObrasComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="podeEscrever" (newRequested)="novo()">
      <ces-page-header
        title="Kits construtivos"
        icon="pi pi-box"
        [breadcrumb]="[{ label: 'Obras' }, { label: 'Cadastros' }, { label: 'Kits construtivos' }]"
        description="Conjuntos de serviços e materiais por contrato, aplicados nos pontos do projeto. O valor do kit soma só os serviços, em R$."
        helpLabel="Ajuda sobre kits construtivos"
        (help)="ajuda.show()"
      >
        @if (podeImportar) {
          <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacaoAberta.set(true)"></ces-button>
        }
        @if (podeEscrever) {
          <ces-button actions icon="pi pi-plus" label="Novo kit" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel #painel storageKey="obras-kits" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Contrato" [options]="contratoOpcoes()" [(value)]="lista.filters.idContrato" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Grupo" [options]="grupoOpcoes()" [(value)]="lista.filters.grupo" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Classe de tensão" [options]="tensaoOpcoes" [(value)]="lista.filters.classeTensao" [clearable]="true" placeholder="Todas"></ces-select>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Kits"
        mode="server"
        storageKey="obras-kits"
        exportFileName="kits-construtivos"
        searchPlaceholder="Código ou descrição do kit"
        [rowActionLabel]="podeEscrever ? 'Editar' : 'Ver itens'"
        [rowActionIcon]="podeEscrever ? 'pi pi-pencil' : 'pi pi-eye'"
        [emptyActionLabel]="podeEscrever ? 'Novo kit' : undefined"
        noResultsTitle="Nenhum kit encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os kits' : 'Nenhum kit cadastrado'"
        [stateDescription]="resultado.error() ?? 'Cadastre um kit ou importe a planilha de kits do contrato.'"
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
        (rowActivate)="abrir($event)"
      >
        <ng-template cesCell="codigo" let-row><span class="ces-cell-strong">{{ row.codigo }}</span></ng-template>
        <ng-template cesCell="classesTensao" let-row>
          @if (row.classesTensao.length) {
            {{ tensoes(row.classesTensao) }}
          } @else {
            <span class="ces-cell-muted">Todas</span>
          }
        </ng-template>
        <ng-template cesCell="itens" let-row>
          @if (row.quantidadeItens) {
            {{ row.quantidadeServicos }} serv. · {{ row.quantidadeMateriais }} mat.
          } @else {
            <ces-status tone="warning" icon="pi-exclamation-triangle">Sem itens</ces-status>
          }
        </ng-template>
        <ng-template cesCell="valor" let-row>
          <span class="ces-cell-strong">{{ moeda(row.valor) }}</span>
          @if (row.servicosSemValor) {
            <span class="ces-cell-muted">{{ row.servicosSemValor }} sem valor vigente</span>
          }
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            @if (podeEscrever && row.ativo) {
              <ces-icon-button icon="pi pi-ban" [label]="'Desativar ' + row.codigo" size="icon-sm" (pressed)="desativar(row)"></ces-icon-button>
            }
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.codigo" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="titulo()" [description]="emEdicao()?.descricao"
      [dirty]="alterado()" [loading]="carregandoKit()" size="xl">
      <ces-form-section title="Kit" icon="pi pi-box">
        <div class="ces-form-grid">
          <ces-select label="Contrato" [required]="true" [options]="contratoOpcoes()" [(value)]="idContrato" [filter]="true"
            [disabled]="!!emEdicao()" [readonly]="somenteLeitura()" [error]="erro('idContrato')"
            [description]="emEdicao() ? 'O contrato não muda depois do cadastro.' : undefined"></ces-select>
          <ces-text-input label="Código" [required]="true" [value]="codigo()" (valueChange)="codigo.set($event.toUpperCase())" [maxlength]="40"
            placeholder="Ex.: LMI-0688" [disabled]="!!emEdicao()" [readonly]="somenteLeitura()" [error]="erro('codigo')"></ces-text-input>
        </div>
        <div class="ces-form-stack">
          <ces-text-input label="Descrição" [required]="true" [(value)]="descricao" [maxlength]="300" [readonly]="somenteLeitura()" [error]="erro('descricao')"></ces-text-input>
        </div>
        <div class="ces-form-grid">
          <ces-text-input label="Grupo" [(value)]="grupo" [maxlength]="60" placeholder="Ex.: Poste, Estrutura, Aterramento" [readonly]="somenteLeitura()"></ces-text-input>
          <ces-multiselect label="Classes de tensão" [options]="tensaoOpcoes" [(value)]="classes" [filter]="false" display="chip"
            description="Sem seleção, o kit vale para todas as tensões." [readonly]="somenteLeitura()"></ces-multiselect>
        </div>
      </ces-form-section>

      <ces-form-section title="Itens" icon="pi pi-list">
        @if (!somenteLeitura()) {
          <div class="ces-form-grid">
            <ces-select label="Tipo" [options]="tiposItem" [value]="novoTipo()" (valueChange)="trocarTipo($event)"></ces-select>
            @if (novoTipo() === 'SERVICO') {
              <ces-select label="Ação do contrato" [options]="acaoOpcoes()" [(value)]="novaReferencia" [filter]="true" placeholder="Busque por código ou nome"
                [loading]="acoes.loading()" (opened)="acoes.open()" (search)="acoes.search($event)" [error]="erroItem()"></ces-select>
            } @else {
              <ces-select label="Material" [options]="materialOpcoes()" [(value)]="novaReferencia" [filter]="true" placeholder="Busque por código ou descrição"
                [loading]="materiais.loading()" (opened)="materiais.open()" (search)="materiais.search($event)" [error]="erroItem()"></ces-select>
            }
            <ces-select label="Operação" [options]="operacoes" [(value)]="novaOperacao"></ces-select>
            <ces-number-input label="Quantidade por kit" [(value)]="novaQuantidade" [min]="0" [maxFractionDigits]="4"></ces-number-input>
          </div>
          <div class="ces-actions-row">
            @if (itemEmEdicao()) {
              <ces-button variant="ghost" size="sm" label="Cancelar edição do item" (pressed)="limparItem()"></ces-button>
            }
            <ces-button variant="outline" size="sm" [icon]="itemEmEdicao() ? 'pi pi-check' : 'pi pi-plus'"
              [label]="itemEmEdicao() ? 'Atualizar item' : 'Adicionar item'" (pressed)="adicionarItem()"></ces-button>
          </div>
        }
        @if (tentouSalvar() && !itens().length) {
          <ces-callout tone="warning" [dense]="true">O kit fica cadastrado sem itens, mas só pode ser aplicado nos pontos depois de ter ao menos um item.</ces-callout>
        }
        <ces-data-table caption="Itens do kit" [searchable]="itens().length > 8" searchPlaceholder="Código ou descrição"
          [columns]="colunasItens" [rows]="itens()" [rowKey]="chaveItem" [pageSizeOptions]="[25, 50, 100]" [pageSize]="25"
          [state]="itens().length ? 'ready' : 'empty'" stateTitle="Nenhum item no kit"
          [stateDescription]="somenteLeitura() ? 'Este kit ainda não tem itens.' : 'Adicione serviços e materiais acima.'"
          [rowActionLabel]="somenteLeitura() ? undefined : 'Editar item'" rowActionIcon="pi pi-pencil" [rowLabel]="rotuloItem"
          (rowActivate)="editarItem($event)">
          <ng-template cesCell="tipo" let-row>
            <ces-status [tone]="row.tipo === 'SERVICO' ? 'brand' : 'neutral'" [icon]="row.tipo === 'SERVICO' ? 'pi-wrench' : 'pi-box'">
              {{ row.tipo === 'SERVICO' ? 'Serviço' : 'Material' }}
            </ces-status>
          </ng-template>
          <ng-template cesCell="referencia" let-row>
            <span class="ces-cell-strong">{{ row.codigo }}</span>
            <span class="ces-cell-muted">{{ row.descricao }}</span>
          </ng-template>
          <ng-template cesCell="valorTotal" let-row>
            @if (row.tipo === 'MATERIAL') {
              <span class="ces-cell-muted">Não compõe o valor</span>
            } @else if (row.valorTotal != null) {
              {{ moeda(row.valorTotal) }}
            } @else if (row.id) {
              <ces-status tone="warning" icon="pi-exclamation-triangle">Sem valor vigente</ces-status>
            } @else {
              <span class="ces-cell-muted">Calculado ao salvar</span>
            }
          </ng-template>
          <ng-template cesRowActions let-row>
            @if (!somenteLeitura()) {
              <span class="ces-table-actions">
                <ces-icon-button icon="pi pi-trash" [label]="'Remover item ' + row.codigo" size="icon-sm" (pressed)="removerItem(row)"></ces-icon-button>
              </span>
            }
          </ng-template>
        </ces-data-table>
        @if (emEdicao(); as kit) {
          <p class="ces-cell-muted" aria-live="polite">
            Valor do kit: <strong>{{ moeda(kit.valor) }}</strong> pelos valores vigentes em {{ dataReferencia(kit.dataReferenciaValor) }}.
            @if (alterado()) { Salve para recalcular. }
          </p>
        }
      </ces-form-section>

      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" [label]="somenteLeitura() ? 'Fechar' : 'Cancelar'" (pressed)="formularioAberto.set(false)"></ces-button>
        @if (!somenteLeitura()) {
          <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar kit'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
        }
      </div>
    </ces-form-drawer>

    <app-importacao-planilha-obras [(visible)]="importacaoAberta" tipo="KITS" titulo="Importar kits"
      descricao="Uma linha por item; o kit é identificado pelo código. Reimportar um kit atualiza os dados e os itens informados."
      [colunas]="colunasImportacao" [fonte]="fonteImportacao" [exigeContrato]="true" [contratos]="contratoOpcoes()"
      [contratoInicial]="lista.filters.idContrato()" (processada)="lista.reload()"></app-importacao-planilha-obras>

    <app-audit-dialog #auditoria></app-audit-dialog>
    <app-ajuda-listagem #ajuda titulo="Kits construtivos" entidadePlural="kits" busca="código ou descrição" rotuloNovo="Novo kit" [importacao]="podeImportar"></app-ajuda-listagem>
  `
})
export class KitsConstrutivosComponent {
  private readonly kits = inject(ObrasKitService);
  private readonly contratos = inject(ContratoService);
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  private readonly permissoes = inject(AuthService).currentUser()?.permissoes ?? [];
  protected readonly podeEscrever = this.permissoes.includes('obras:kit:write');
  protected readonly podeImportar = this.permissoes.includes('obras:kit:import');

  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly tensaoOpcoes = CLASSES_TENSAO;
  protected readonly tiposItem = TIPOS_ITEM;
  protected readonly operacoes = OPERACOES;
  protected readonly moeda = formatarMoeda;

  protected readonly contratoOpcoes = toSignal(
    this.contratos.listarContratosAtivos().pipe(
      map((p) => p.content.map((c) => ({ label: `${c.numeroContrato} · ${c.cliente}`, value: c.id }))),
      catchError(() => of([] as CesSelectOption<string>[]))
    ),
    { initialValue: [] as CesSelectOption<string>[] }
  );
  protected readonly grupoOpcoes = toSignal(
    this.kits.listarGrupos().pipe(
      map((grupos) => grupos.map((g) => ({ label: g, value: g }))),
      catchError(() => of([] as CesSelectOption<string>[]))
    ),
    { initialValue: [] as CesSelectOption<string>[] }
  );

  protected readonly lista = cesListState({
    storageKey: 'obras-kits',
    filters: {
      idContrato: cesFilter.value<string | null>(null, { label: 'Contrato', param: 'contrato', options: () => this.contratoOpcoes() }),
      grupo: cesFilter.value<string | null>(null, { label: 'Grupo', param: 'grupo' }),
      classeTensao: cesFilter.value<ClasseTensao | null>(null, { label: 'Tensão', param: 'tensao', options: () => CLASSES_TENSAO }),
      ativo: cesFilter.value<boolean | null>(true, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'codigo', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<KitConstrutivoResponse>(
    (pedido) => this.kits.listarKits({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.kits.listarKits({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<KitConstrutivoResponse>[] = [
    { key: 'codigo', header: 'Código', type: 'custom', priority: 1, sortField: 'codigo', width: '9rem', value: (k) => k.codigo, display: (k) => k.codigo },
    { key: 'descricao', header: 'Descrição', priority: 1, sortField: 'descricao' },
    { key: 'grupo', header: 'Grupo', sortField: 'grupo', width: '9rem', display: (k) => k.grupo ?? '' },
    { key: 'numeroContrato', header: 'Contrato', width: '9rem', display: (k) => k.numeroContrato ?? '' },
    { key: 'classesTensao', header: 'Tensões', type: 'custom', width: '9rem', value: (k) => k.classesTensao.join(','), display: (k) => this.tensoes(k.classesTensao) || 'Todas' },
    { key: 'itens', header: 'Itens', type: 'custom', width: '9rem', value: (k) => k.quantidadeItens, display: (k) => `${k.quantidadeServicos} serviços, ${k.quantidadeMateriais} materiais` },
    { key: 'valor', header: 'Valor (R$)', type: 'custom', priority: 1, width: '9rem', value: (k) => k.valor, display: (k) => formatarMoeda(k.valor) },
    { key: 'ativo', header: 'Status', type: 'custom', sortField: 'ativo', width: '7rem', value: (k) => k.ativo, display: (k) => (k.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (k: KitConstrutivoResponse) => k.codigo;

  protected readonly colunasItens: CesDataTableColumn<ItemEmEdicao>[] = [
    { key: 'tipo', header: 'Tipo', type: 'custom', width: '8rem', value: (i) => i.tipo, display: (i) => (i.tipo === 'SERVICO' ? 'Serviço' : 'Material') },
    { key: 'referencia', header: 'Código / descrição', type: 'custom', priority: 1, value: (i) => `${i.codigo} ${i.descricao}`, display: (i) => `${i.codigo} ${i.descricao}` },
    { key: 'operacao', header: 'Operação', width: '7rem', display: (i) => (i.operacao === 'RETIRAR' ? 'Retirar' : 'Instalar') },
    { key: 'quantidade', header: 'Qtd.', priority: 1, width: '6rem', value: (i) => i.quantidade,
      display: (i) => `${i.quantidade.toLocaleString('pt-BR', { maximumFractionDigits: 4 })}${i.unidade ? ' ' + i.unidade : ''}` },
    { key: 'valorTotal', header: 'Valor (R$)', type: 'custom', width: '9rem', value: (i) => i.valorTotal ?? null, display: (i) => (i.valorTotal != null ? formatarMoeda(i.valorTotal) : '') }
  ];
  protected readonly chaveItem = (i: ItemEmEdicao) => i.chave;
  protected readonly rotuloItem = (i: ItemEmEdicao) => i.codigo;

  // Formulário do kit
  protected readonly formularioAberto = signal(false);
  protected readonly importacaoAberta = signal(false);
  protected readonly colunasImportacao = COLUNAS_KITS;
  protected readonly fonteImportacao = fonteKits(this.kits);
  protected readonly emEdicao = signal<KitConstrutivoResponse | null>(null);
  protected readonly carregandoKit = signal(false);
  protected readonly idContrato = signal<string | null>(null);
  protected readonly codigo = signal('');
  protected readonly descricao = signal('');
  protected readonly grupo = signal('');
  protected readonly classes = signal<ClasseTensao[]>([]);
  protected readonly itens = signal<ItemEmEdicao[]>([]);
  protected readonly salvando = signal(false);
  protected readonly tentouSalvar = signal(false);
  private itensOriginais = '';
  protected readonly somenteLeitura = computed(() => !this.podeEscrever || this.emEdicao()?.ativo === false);
  protected readonly titulo = computed(() => {
    const kit = this.emEdicao();
    if (!kit) return 'Novo kit';
    return this.somenteLeitura() ? `Kit ${kit.codigo}` : `Editar kit ${kit.codigo}`;
  });

  // Item em edição
  protected readonly novoTipo = signal<TipoItemKit>('SERVICO');
  protected readonly novaReferencia = signal<string | null>(null);
  protected readonly novaOperacao = signal<OperacaoItemKit>('INSTALAR');
  protected readonly novaQuantidade = signal<number | null>(1);
  protected readonly itemEmEdicao = signal<string | null>(null);
  protected readonly erroItem = signal<string | undefined>(undefined);

  protected readonly acoes = new RemoteLookup<AcaoResponse>(
    {
      identify: (a) => a.id,
      selectedKeys: () => (this.novoTipo() === 'SERVICO' && this.novaReferencia() ? [this.novaReferencia()!] : []),
      load: ({ query, page, size }) => this.contratos.listarAcoes({ ativo: true, buscaGlobal: query || undefined, page, size, sort: 'codigo,asc' })
    },
    this.destroyRef
  );
  protected readonly materiais = new RemoteLookup<MaterialResponse>(
    {
      identify: (m) => m.id,
      selectedKeys: () => (this.novoTipo() === 'MATERIAL' && this.novaReferencia() ? [this.novaReferencia()!] : []),
      load: ({ query, page, size }) => this.almoxarifado.listarMateriais({ ativo: true, buscaGlobal: query || undefined, page, size, sort: 'codigo,asc' })
    },
    this.destroyRef
  );
  protected readonly acaoOpcoes = computed(() => this.acoes.items().map((a) => ({ label: `${a.codigo} · ${a.nome}`, value: a.id })));
  protected readonly materialOpcoes = computed(() => this.materiais.items().map((m) => ({ label: `${m.codigo} · ${m.descricao}`, value: m.id })));

  protected readonly alterado = computed(() => {
    if (this.somenteLeitura()) return false;
    const kit = this.emEdicao();
    const itensAtuais = this.assinaturaItens(this.itens());
    if (!kit) {
      return !!(this.codigo() + this.descricao() + this.grupo()).trim() || !!this.idContrato() || this.itens().length > 0 || this.classes().length > 0;
    }
    return (
      this.descricao() !== kit.descricao ||
      this.grupo() !== (kit.grupo ?? '') ||
      [...this.classes()].sort().join() !== [...kit.classesTensao].sort().join() ||
      itensAtuais !== this.itensOriginais
    );
  });
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.idContrato()) e['idContrato'] = 'Selecione o contrato.';
    if (!this.codigo().trim()) e['codigo'] = 'Informe o código.';
    if (!this.descricao().trim()) e['descricao'] = 'Informe a descrição.';
    return e;
  });

  private filtro() {
    const { filters, query } = this.lista.request();
    return {
      idContrato: filters.idContrato ?? undefined,
      grupo: filters.grupo ?? undefined,
      classeTensao: filters.classeTensao ?? undefined,
      ativo: filters.ativo ?? undefined,
      buscaGlobal: query || undefined
    };
  }

  protected tensoes(classes: ClasseTensao[]): string {
    return classes.map(rotuloClasseTensao).join(', ');
  }

  protected dataReferencia(data?: string | null): string {
    if (!data) return 'hoje';
    const [ano, mes, dia] = data.split('-');
    return `${dia}/${mes}/${ano}`;
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    if (!this.podeEscrever) return;
    this.preencher(null);
    this.idContrato.set(this.lista.filters.idContrato());
    this.formularioAberto.set(true);
  }

  protected abrir(resumo: KitConstrutivoResponse): void {
    this.preencher(resumo);
    this.formularioAberto.set(true);
    this.carregandoKit.set(true);
    this.kits.buscarKit(resumo.id).subscribe({
      next: (kit) => {
        this.carregandoKit.set(false);
        this.preencher(kit);
      },
      error: (e) => {
        this.carregandoKit.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  private preencher(kit: KitConstrutivoResponse | null): void {
    this.emEdicao.set(kit);
    this.idContrato.set(kit?.idContrato ?? null);
    this.codigo.set(kit?.codigo ?? '');
    this.descricao.set(kit?.descricao ?? '');
    this.grupo.set(kit?.grupo ?? '');
    this.classes.set([...(kit?.classesTensao ?? [])]);
    this.itens.set((kit?.itens ?? []).map((i) => this.deResposta(i)));
    this.itensOriginais = this.assinaturaItens(this.itens());
    this.tentouSalvar.set(false);
    this.limparItem();
  }

  private deResposta(i: ItemKitResponse): ItemEmEdicao {
    return {
      chave: i.id,
      id: i.id,
      tipo: i.tipo,
      idReferencia: i.idReferencia,
      codigo: i.codigoReferencia ?? '',
      descricao: i.descricaoReferencia ?? '',
      operacao: i.operacao,
      unidade: i.unidade ?? null,
      quantidade: i.quantidade,
      valorUnitario: i.valorUnitario,
      valorTotal: i.valorTotal
    };
  }

  private assinaturaItens(itens: ItemEmEdicao[]): string {
    return itens.map((i) => `${i.id ?? ''}|${i.tipo}|${i.idReferencia}|${i.operacao}|${i.quantidade}`).join(';');
  }

  protected trocarTipo(tipo: TipoItemKit | null): void {
    this.novoTipo.set(tipo ?? 'SERVICO');
    this.novaReferencia.set(null);
    this.erroItem.set(undefined);
  }

  protected limparItem(): void {
    this.itemEmEdicao.set(null);
    this.novoTipo.set('SERVICO');
    this.novaReferencia.set(null);
    this.novaOperacao.set('INSTALAR');
    this.novaQuantidade.set(1);
    this.erroItem.set(undefined);
  }

  protected editarItem(item: ItemEmEdicao): void {
    if (this.somenteLeitura()) return;
    this.itemEmEdicao.set(item.chave);
    this.novoTipo.set(item.tipo);
    // A referência atual fica como opção mesmo fora da página carregada no select.
    const semente = { id: item.idReferencia, codigo: item.codigo, nome: item.descricao, descricao: item.descricao, ativo: true };
    if (item.tipo === 'SERVICO') {
      this.acoes.setSeed([semente as AcaoResponse]);
    } else {
      this.materiais.setSeed([semente as unknown as MaterialResponse]);
    }
    this.novaReferencia.set(item.idReferencia);
    this.novaOperacao.set(item.operacao);
    this.novaQuantidade.set(item.quantidade);
    this.erroItem.set(undefined);
  }

  protected adicionarItem(): void {
    const referencia = this.novaReferencia();
    const quantidade = this.novaQuantidade();
    const tipo = this.novoTipo();
    if (!referencia) {
      this.erroItem.set(tipo === 'SERVICO' ? 'Selecione a ação do contrato.' : 'Selecione o material.');
      return;
    }
    if (!quantidade || quantidade <= 0) {
      this.notificacoes.warning('Informe uma quantidade maior que zero.');
      return;
    }
    const chaveEditada = this.itemEmEdicao();
    const repetido = this.itens().find(
      (i) => i.chave !== chaveEditada && i.tipo === tipo && i.idReferencia === referencia && i.operacao === this.novaOperacao()
    );
    if (repetido) {
      this.erroItem.set('Este item já está no kit com a mesma ação. Edite a quantidade dele.');
      return;
    }
    const acao = tipo === 'SERVICO' ? this.acoes.items().find((a) => a.id === referencia) : undefined;
    const material = tipo === 'MATERIAL' ? this.materiais.items().find((m) => m.id === referencia) : undefined;
    const anterior = this.itens().find((i) => i.chave === chaveEditada);
    const item: ItemEmEdicao = {
      chave: anterior?.chave ?? `novo-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      id: anterior?.idReferencia === referencia ? anterior.id : null,
      tipo,
      idReferencia: referencia,
      codigo: acao?.codigo ?? material?.codigo ?? anterior?.codigo ?? '',
      descricao: acao?.nome ?? material?.descricao ?? anterior?.descricao ?? '',
      operacao: this.novaOperacao(),
      unidade: material?.unidadeMedida ?? anterior?.unidade ?? null,
      quantidade,
      // Valor recalculado pelo servidor ao salvar.
      valorUnitario: null,
      valorTotal: null
    };
    this.itens.update((lista) => (anterior ? lista.map((i) => (i.chave === anterior.chave ? item : i)) : [...lista, item]));
    this.limparItem();
  }

  protected removerItem(item: ItemEmEdicao): void {
    this.itens.update((lista) => lista.filter((i) => i.chave !== item.chave));
    if (this.itemEmEdicao() === item.chave) this.limparItem();
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const request: KitConstrutivoRequest = {
      idContrato: this.idContrato()!,
      codigo: this.codigo().trim(),
      descricao: this.descricao().trim(),
      grupo: this.grupo().trim() || null,
      classesTensao: this.classes(),
      itens: this.itens().map<ItemKitRequest>((i) => ({
        id: i.id ?? null,
        tipo: i.tipo,
        idReferencia: i.idReferencia,
        operacao: i.operacao,
        unidade: i.unidade,
        quantidade: i.quantidade
      }))
    };
    this.salvando.set(true);
    (atual ? this.kits.atualizarKit(atual.id, request) : this.kits.criarKit(request)).subscribe({
      next: (kit) => {
        this.salvando.set(false);
        this.notificacoes.success(atual ? `Kit ${kit.codigo} atualizado.` : `Kit ${kit.codigo} cadastrado.`);
        this.preencher(kit);
        this.formularioAberto.set(false);
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async desativar(kit: KitConstrutivoResponse): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: 'Desativar kit',
      message: `${kit.codigo} deixa de aparecer para aplicação em novos pontos. Os pontos onde ele já foi aplicado continuam com o kit e seus itens.`,
      confirmLabel: 'Desativar',
      variant: 'destructive'
    });
    if (!ok) return;
    this.kits.desativarKit(kit.id).subscribe({
      next: () => {
        this.notificacoes.success('Kit desativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(kit: KitConstrutivoResponse): void {
    this.auditoria().show(kit.id, kit.codigo, { codigo: 'Código', descricao: 'Descrição', grupo: 'Grupo', ativo: 'Status' });
  }
}
