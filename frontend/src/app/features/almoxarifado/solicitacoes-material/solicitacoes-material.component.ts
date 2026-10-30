import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import {
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterPanelComponent,
  CesFormDrawerComponent,
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
  CesTone,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import {
  EstoqueResponse,
  ItemSolicitacaoResponse,
  MaterialResponse,
  SolicitacaoStatus,
  SolicitacaoSummaryResponse
} from '../../../core/models/almoxarifado.models';
import { UsuarioResponse } from '../../../core/models/iam.models';
import { BaseOperacionalResponse } from '../../../core/models/rh.models';
import { generateReferenceOptions, getErrorMessage } from '../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { RhService } from '../../../core/services/rh.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import {
  CopilotoAreaComponent,
  CopilotoDashboardContext,
  CopilotoGatilhoComponent,
  CopilotoPainelService,
  filtroPainel,
  graficoPainel,
  metricaPainel,
  periodoPainel
} from '../../copiloto/painel';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { dataIso, dataLocal } from '../../../shared/listagem/listagem';
import { ExportarSolicitacoesDialogComponent } from './exportar-solicitacoes-dialog.component';
import { AbaDetalheComercial, SolicitacaoComercialDetalheComponent } from './solicitacao-comercial-detalhe.component';
import { prazoRelativo, saqueAtrasado, STATUS_COMERCIAIS, STATUS_EM_ABERTO, statusComercial } from './solicitacao-comercial-status';
import { SolicitacaoRejeicaoDialogComponent } from './solicitacao-rejeicao-dialog.component';
import { SolicitacoesComerciaisCalendarioComponent } from './solicitacoes-comerciais-calendario/solicitacoes-comerciais-calendario.component';
import { SolicitacoesComerciaisImportacaoComponent } from './solicitacoes-comerciais-importacao/solicitacoes-comerciais-importacao.component';
import { SolicitacoesComerciaisKanbanComponent } from './solicitacoes-comerciais-kanban';

type ItemEstorno = ItemSolicitacaoResponse & { quantidadeEstorno: number | null };
export type VisaoSolicitacoes = 'tabela' | 'kanban' | 'calendario';

const STATUS: CesSelectOption<SolicitacaoStatus>[] = STATUS_COMERCIAIS.map((s) => ({ label: s.label, value: s.value }));
const ORDEM_STATUS = STATUS_COMERCIAIS.map((s) => s.value);
const VISOES: { value: VisaoSolicitacoes; label: string; icon: string; rota: string }[] = [
  { value: 'tabela', label: 'Tabela', icon: 'pi pi-table', rota: '/almoxarifado/solicitacoes' },
  { value: 'kanban', label: 'Quadro', icon: 'pi pi-objects-column', rota: '/almoxarifado/solicitacoes/kanban' },
  { value: 'calendario', label: 'Calendário', icon: 'pi pi-calendar', rota: '/almoxarifado/solicitacoes/calendario' }
];
const ehDevolucao = (tipo?: string | null) => tipo === 'DEVOLUCAO_COMUM' || tipo === '222';
const dataHora = (iso?: string | null) => (iso ? new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—');
const mesmoConjunto = (a: readonly string[], b: readonly string[]) => a.length === b.length && a.every((v) => b.includes(v));

@Component({
  selector: 'app-solicitacoes-material',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
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
    ExportarSolicitacoesDialogComponent,
    SolicitacaoComercialDetalheComponent,
    SolicitacaoRejeicaoDialogComponent,
    SolicitacoesComerciaisCalendarioComponent,
    SolicitacoesComerciaisImportacaoComponent,
    SolicitacoesComerciaisKanbanComponent,
    CopilotoAreaComponent,
    CopilotoGatilhoComponent
  ],
  styles: `
    .sol-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; flex-wrap: wrap; }
    .sol-atalhos { flex: 1 1 auto; min-width: 0; }
    .sol-contagem {
      display: inline-flex; align-items: center; justify-content: center; min-width: 1.375rem; height: 1.25rem; padding: 0 0.375rem;
      border-radius: 999px; background: var(--ces-surface-muted); color: var(--ces-foreground-muted);
      font-size: 0.7rem; font-weight: 700; font-variant-numeric: tabular-nums;
    }
    .ces-quick-filter[aria-pressed='true'] .sol-contagem { background: var(--ces-surface); color: var(--ces-foreground); }
    .sol-contagem--alerta { background: var(--ces-warning-soft); color: var(--ces-warning-text); }
    .sol-divisor { width: 1px; align-self: stretch; margin: 0.25rem 0.125rem; background: var(--ces-border); }
    .sol-tom { width: 0.5rem; height: 0.5rem; border-radius: 999px; background: var(--sol-tom); flex: none; }
    [data-tone='warning'] { --sol-tom: var(--ces-warning); }
    [data-tone='info'] { --sol-tom: var(--ces-info); }
    [data-tone='success'] { --sol-tom: var(--ces-success); }
    [data-tone='danger'] { --sol-tom: var(--ces-danger); }
    [data-tone='neutral'] { --sol-tom: var(--ces-foreground-subtle); }
    .sol-visoes { display: inline-flex; flex: none; padding: 2px; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface-subtle); }
    .sol-visoes button {
      display: inline-flex; align-items: center; gap: 0.375rem; min-height: calc(var(--ces-control-height-sm) - 2px); padding: 0 0.75rem;
      border: 0; border-radius: calc(var(--ces-radius-md) - 2px); background: transparent; color: var(--ces-foreground-muted);
      font: inherit; font-size: var(--ces-font-control); font-weight: 600; cursor: pointer;
    }
    .sol-visoes button:hover { color: var(--ces-foreground); }
    .sol-visoes button[aria-current='page'] { background: var(--ces-surface); color: var(--ces-foreground); box-shadow: var(--ces-shadow-sm); }
    .sol-visoes button:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 1px; }
    .sol-visoes .pi { font-size: 0.8rem; }
    .sol-prazo { display: grid; gap: 0.125rem; justify-items: start; }
    @media (max-width: 40rem) {
      .sol-toolbar { display: grid; grid-template-columns: minmax(0, 1fr); gap: 0.625rem; }
      .sol-atalhos { flex-wrap: nowrap; overflow-x: auto; padding-bottom: 2px; scrollbar-width: none; scroll-snap-type: x proximity; }
      .sol-atalhos::-webkit-scrollbar { display: none; }
      .sol-atalhos .ces-quick-filter { flex: none; scroll-snap-align: start; }
      .sol-visoes { width: 100%; }
      .sol-visoes button { flex: 1; justify-content: center; }
    }
  `,
  template: `
    <app-copiloto-area [contexto]="contextoCopiloto()">
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novo()">
      <ces-page-header
        title="Solicitações comerciais"
        icon="pi pi-shopping-cart"
        [breadcrumb]="[{ label: 'Almoxarifado' }, { label: 'Solicitações comerciais' }]"
        description="Saques de material do estoque físico para os estoques de campo das equipes: aprovação, entrega e estorno."
        helpLabel="Ajuda sobre solicitações comerciais"
        (help)="ajuda.show()"
      >
        <app-copiloto-gatilho actions></app-copiloto-gatilho>
        <ces-button actions variant="outline" icon="pi pi-download" label="Exportar" (pressed)="exportacaoAberta.set(true)"></ces-button>
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacaoAberta.set(true)"></ces-button>
        <ces-button actions variant="outline" icon="pi pi-undo" label="Nova devolução" (pressed)="irParaDevolucaoComercial()"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Nova solicitação" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel [storageKey]="chave" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Estoque de origem" [options]="estoqueOptions()" [(value)]="lista.filters.idEstoqueOrigem" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Estoque de destino" [options]="estoqueOptions()" [(value)]="lista.filters.idEstoqueDestino" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Base de origem" [options]="baseOptions()" [(value)]="lista.filters.idBaseOrigem" [clearable]="true" [filter]="true" placeholder="Todas"
            [loading]="bases.loading()" (opened)="bases.open()" (search)="bases.search($event)"></ces-select>
          <ces-select label="Base de destino" [options]="baseOptions()" [(value)]="lista.filters.idBaseDestino" [clearable]="true" [filter]="true" placeholder="Todas"
            [loading]="bases.loading()" (opened)="bases.open()" (search)="bases.search($event)"></ces-select>
          <ces-select label="Requisitante" [options]="usuarioOptions()" [(value)]="lista.filters.idRequisitante" [clearable]="true" [filter]="true" placeholder="Todos"
            [loading]="usuarios.loading()" (opened)="usuarios.open()" (search)="usuarios.search($event)"></ces-select>
          <ces-multiselect label="Status" [options]="statusOpcoes" [(value)]="lista.filters.status" [filter]="false" placeholder="Todos"></ces-multiselect>
          <ces-select label="Referência" [options]="referencias" [(value)]="lista.filters.referencia" [clearable]="true" [filter]="true" placeholder="Mês/ano"></ces-select>
          <ces-text-input label="Número de série" [(value)]="lista.filters.numSerie" hint="Não fica salvo no endereço da página."></ces-text-input>
          <ces-datepicker label="Solicitadas a partir de" [value]="dataLocal(lista.filters.periodo().from)" [clearable]="true" (valueChange)="periodo('from', $event)"></ces-datepicker>
          <ces-datepicker label="Solicitadas até" [value]="dataLocal(lista.filters.periodo().to)" [clearable]="true" (valueChange)="periodo('to', $event)"></ces-datepicker>
        </div>
      </ces-filter-panel>

      <div class="sol-toolbar">
        <div class="ces-quick-filters sol-atalhos" role="group" aria-label="Mostrar só os status escolhidos">
          <button type="button" class="ces-quick-filter" [attr.aria-pressed]="!statusAplicados().length" (click)="definirStatus([])">
            Todas <span class="sol-contagem">{{ contagemTotal() ?? '–' }}</span>
          </button>
          <button type="button" class="ces-quick-filter" [attr.aria-pressed]="emAbertoAtivo()" (click)="definirStatus(emAberto)"
            title="Pendentes, aprovadas e entregues em parte: tudo o que ainda exige ação">
            <i class="pi pi-inbox" aria-hidden="true"></i> Em aberto <span class="sol-contagem" [class.sol-contagem--alerta]="(contagemAbertas() ?? 0) > 0">{{ contagemAbertas() ?? '–' }}</span>
          </button>
          <span class="sol-divisor" aria-hidden="true"></span>
          @for (s of atalhosStatus; track s.value) {
            <button type="button" class="ces-quick-filter" [attr.aria-pressed]="statusAplicados().includes(s.value)" [attr.data-tone]="s.tone" (click)="alternarStatus(s.value)"
              [attr.aria-label]="s.atalho + ': ' + (contagens()[s.value] ?? 'contagem indisponível')">
              <span class="sol-tom" aria-hidden="true"></span> {{ s.atalho }} <span class="sol-contagem">{{ contagens()[s.value] ?? '–' }}</span>
            </button>
          }
        </div>
        <nav class="sol-visoes" aria-label="Forma de visualizar">
          @for (v of visoes; track v.value) {
            <button type="button" [attr.aria-current]="visao === v.value ? 'page' : null" (click)="trocarVisao(v.value)">
              <i [class]="v.icon" aria-hidden="true"></i>{{ v.label }}
            </button>
          }
        </nav>
      </div>

      @switch (visao) {
        @case ('kanban') {
          <app-solicitacoes-comerciais-kanban #quadro [filtros]="filtroVisao()" (abrir)="abrirDetalhe($event.item, $event.aba)"
            (rejeitar)="rejeitarId.set($event.id); rejeitarPendente.set($event.status === 'PENDENTE')" (verNaTabela)="verNaTabela($event)" (alterado)="aoAlterar('kanban')" [copiloto]="copilotoDisponivel" (analisar)="analisarEtapa($event)"></app-solicitacoes-comerciais-kanban>
        }
        @case ('calendario') {
          <app-solicitacoes-comerciais-calendario #calendario [filtros]="filtroVisao()" (abrir)="abrirDetalhe($event)"></app-solicitacoes-comerciais-calendario>
        }
        @default {
          <ces-data-table
            title="Solicitações comerciais"
            mode="server"
            [storageKey]="chave"
            searchPlaceholder="Número de série dos itens"
            rowActionLabel="Ver detalhe"
            rowActionIcon="pi pi-arrow-right"
            emptyActionLabel="Nova solicitação"
            noResultsTitle="Nenhuma solicitação encontrada com esse recorte."
            [columns]="colunas"
            [rows]="resultado.rows()"
            [totalRecords]="resultado.total()"
            [rowLabel]="rotulo"
            [state]="resultado.state()"
            [stateTitle]="resultado.error() ? 'Não foi possível carregar as solicitações' : 'Nenhuma solicitação comercial'"
            [stateDescription]="resultado.error() ?? 'Crie uma solicitação ou importe uma planilha.'"
            [filtered]="lista.filtered()"
            [(page)]="lista.page"
            [(pageSize)]="lista.pageSize"
            [(sort)]="lista.sort"
            [(query)]="lista.query"
            (lazyLoad)="lista.load($event)"
            (adjustFilters)="painel.expandAndFocus()"
            (clearFilters)="lista.reset()"
            (emptyAction)="novo()"
            (rowActivate)="abrirDetalhe($event)"
          >
            <ng-template cesCell="id" let-row><span class="ces-cell-strong">#{{ row.id?.substring(0, 8) }}</span></ng-template>
            <ng-template cesCell="tipo" let-row>
              <ces-status [tone]="tipoTone(row.tipoTecnico)" [icon]="tipoIcon(row.tipoTecnico)">{{ tipoCodigo(row.tipoTecnico) }}</ces-status>
            </ng-template>
            <ng-template cesCell="status" let-row>
              <ces-status [tone]="statusTone(row.status)" [icon]="statusIcon(row.status)">{{ statusLabel(row.status) }}</ces-status>
              @if (row.encerrada && row.status === 'ENTREGUE_PARCIAL') { <ces-status tone="neutral" icon="pi-lock">Saldo encerrado</ces-status> }
              @if (row.possuiPendencia) { <ces-status tone="warning" icon="pi-flag">Com pendência</ces-status> }
            </ng-template>
            <ng-template cesCell="saque" let-row>
              <span class="sol-prazo">
                <span>{{ row.dataPrevista ? dataCivil(row.dataPrevista) : '—' }}</span>
                @if (atrasada(row)) {
                  <ces-status tone="danger" icon="pi-exclamation-triangle">Atrasada {{ relativo(row.dataPrevista) }}</ces-status>
                } @else if (row.foraPrazoSaque) {
                  <ces-status tone="warning" icon="pi-clock">Fora do prazo de 48h</ces-status>
                }
              </span>
            </ng-template>
            <ng-template cesRowActions let-row>
              <span class="ces-table-actions">
                <ces-icon-button icon="pi pi-print" [label]="'Imprimir relatório da solicitação #' + row.id?.substring(0, 8)" size="icon-sm" (pressed)="imprimir(row)"></ces-icon-button>
                @if (row.status === 'ENTREGUE' || row.status === 'ENTREGUE_PARCIAL') {
                  <ces-icon-button icon="pi pi-replay" [label]="'Estornar solicitação #' + row.id?.substring(0, 8)" size="icon-sm" (pressed)="confirmarEstorno(row)"></ces-icon-button>
                }
                <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações da solicitação #' + row.id?.substring(0, 8)" size="icon-sm" (pressed)="showLogs(row)"></ces-icon-button>
              </span>
            </ng-template>
          </ces-data-table>
        }
      }
    </div>
    </app-copiloto-area>

    <app-solicitacao-comercial-detalhe [(visible)]="detalheAberto" [solicitacaoId]="detalheId()" [abaInicial]="detalheAba()" [copiloto]="copilotoDisponivel" (alterado)="aoAlterar()" (analisar)="analisarSolicitacao($event)"></app-solicitacao-comercial-detalhe>
    <app-solicitacao-rejeicao-dialog [visible]="!!rejeitarId()" (visibleChange)="!$event && rejeitarId.set(null)" [idSolicitacao]="rejeitarId()" [reprovar]="rejeitarPendente()" (rejeitada)="aoAlterar()"></app-solicitacao-rejeicao-dialog>

    <ces-form-drawer [visible]="!!estornoId()" (visibleChange)="!$event && estornoId.set(null)" title="Estorno parcial de materiais"
      description="Os itens selecionados voltam ao estoque de origem." size="md">
      <ces-callout tone="danger" title="Ação irreversível">Os saldos são revertidos e o estorno não pode ser desfeito.</ces-callout>
      <ces-data-table caption="Itens entregues" [searchable]="false" [columns]="colunasEstorno" [rows]="estornoItens()" [rowKey]="chaveItem"
        [selectable]="true" [(selection)]="estornoSelecionados">
        <ng-template cesCell="material" let-item>
          <span class="ces-cell-strong">{{ item.material.descricao }}</span>
          <span class="ces-cell-muted">Cód. {{ item.material.codigo }}{{ item.numSerie ? ' · série ' + item.numSerie : '' }}</span>
        </ng-template>
        <ng-template cesCell="estorno" let-item>
          <ces-number-input [label]="'Quantidade a estornar de ' + item.material.codigo" [labelVisible]="false" [min]="0" [max]="item.quantidadeEntregue"
            [minFractionDigits]="3" [maxFractionDigits]="3" [disabled]="!selecionado(item)" [(value)]="item.quantidadeEstorno"></ces-number-input>
        </ng-template>
      </ces-data-table>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="estornoId.set(null)"></ces-button>
        <ces-button variant="destructive" icon="pi pi-replay" label="Estornar selecionados" [loading]="estornando()" (pressed)="confirmarEstornoParcial()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-solicitacoes-comerciais-importacao [(visible)]="importacaoAberta" [estoques]="estoques()" [materiais]="materiais()" (processado)="aoAlterar()"></app-solicitacoes-comerciais-importacao>

    <app-exportar-solicitacoes-dialog [visible]="exportacaoAberta()" (visibleChange)="exportacaoAberta.set($event)" contextoInicial="COMERCIAL"
      [filtrosAtivos]="filtrosExportacao()" [estoqueOptions]="estoques()" [baseOptions]="bases.items()"></app-exportar-solicitacoes-dialog>

    <app-audit-dialog #auditoria></app-audit-dialog>
    <app-ajuda-listagem #ajuda titulo="Solicitações comerciais" artigoPlural="as" entidadePlural="solicitações" busca="número de série dos itens"
      rotuloNovo="Nova solicitação" [importacao]="true">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-filter"></i> Atalhos de status</h4>
        <p class="help-text-desc">Os botões acima da lista mostram quantas solicitações há em cada status. Clique para ver só aquele status; clique em outros para somar. "Em aberto" junta tudo o que ainda precisa de aprovação ou entrega.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-objects-column"></i> Tabela, quadro e calendário</h4>
        <p class="help-text-desc">O mesmo recorte aparece nas três formas. No quadro, arraste um cartão para aprovar, entregar ou rejeitar (ou use os botões do cartão) e agrupe por situação, pendência ou prazo. O calendário mostra os saques previstos por dia.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-clock"></i> Prazo de saque</h4>
        <p class="help-text-desc">O saque previsto precisa respeitar a antecedência de 48h; as solicitações fora do prazo e as com saque vencido aparecem marcadas.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-replay"></i> Estorno</h4>
        <p class="help-text-desc">Solicitações entregues (total ou parcialmente) podem ter itens estornados: os saldos voltam ao estoque de origem.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class SolicitacoesMaterialComponent {
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly rh = inject(RhService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly router = inject(Router);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');
  private readonly quadro = viewChild<SolicitacoesComerciaisKanbanComponent>('quadro');
  private readonly calendario = viewChild<SolicitacoesComerciaisCalendarioComponent>('calendario');
  private readonly copiloto = inject(CopilotoPainelService);
  protected readonly copilotoDisponivel = this.copiloto.disponivelParaUsuario();

  protected readonly visao: VisaoSolicitacoes = lerVisao(inject(ActivatedRoute).snapshot.data['modo']);
  /** Uma chave só: o recorte e o painel acompanham a troca de visualização. */
  protected readonly chave = 'almox-solicitacoes';
  protected readonly visoes = VISOES;
  protected readonly statusOpcoes = STATUS;
  protected readonly atalhosStatus = STATUS_COMERCIAIS;
  protected readonly emAberto = STATUS_EM_ABERTO;
  protected readonly referencias: CesSelectOption<string>[] = generateReferenceOptions();
  protected readonly dataLocal = dataLocal;
  protected readonly importacaoAberta = signal(false);
  protected readonly exportacaoAberta = signal(false);

  protected readonly detalheAberto = signal(false);
  protected readonly detalheId = signal<string | null>(null);
  protected readonly detalheAba = signal<AbaDetalheComercial>('itens');
  protected readonly rejeitarId = signal<string | null>(null);
  protected readonly rejeitarPendente = signal(false);

  protected readonly estoques = signal<EstoqueResponse[]>([]);
  protected readonly materiais = signal<MaterialResponse[]>([]);
  protected readonly estoqueOptions = computed<CesSelectOption<string>[]>(() => this.estoques().map((e) => ({ label: e.nomeIdentificador, value: e.id })));
  protected readonly bases = new RemoteLookup<BaseOperacionalResponse>(
    {
      identify: (b) => b.id,
      selectedKeys: () => [this.lista.filters.idBaseOrigem(), this.lista.filters.idBaseDestino()].filter((id): id is string => !!id),
      load: ({ query, page, size }) => this.rh.listarBasesOperacionais({ ativo: true, buscaGlobal: query, page, size, sort: 'nome,asc' })
    },
    this.destroyRef
  );
  protected readonly baseOptions = computed<CesSelectOption<string>[]>(() => this.bases.items().map((b) => ({ label: b.nome, value: b.id })));
  protected readonly usuarios = new RemoteLookup<UsuarioResponse>(
    {
      identify: (u) => u.id,
      selectedKeys: () => [this.lista.filters.idRequisitante()].filter((id): id is string => !!id),
      load: ({ query, page, size }) => this.usuariosService.listarUsuarios({ ativo: true, buscaGlobal: query, page, size, sort: 'login,asc' })
    },
    this.destroyRef
  );
  protected readonly usuarioOptions = computed<CesSelectOption<string>[]>(() => this.usuarios.items().map((u) => ({ label: u.login, value: u.id })));

  protected readonly lista = cesListState({
    storageKey: this.chave,
    filters: {
      idEstoqueOrigem: cesFilter.value<string | null>(null, { label: 'Origem', param: 'estoqueOrigem', format: (id): string | null => (id ? this.nomeEstoque(id) : null) }),
      idEstoqueDestino: cesFilter.value<string | null>(null, { label: 'Destino', param: 'estoqueDestino', format: (id): string | null => (id ? this.nomeEstoque(id) : null) }),
      idBaseOrigem: cesFilter.value<string | null>(null, { label: 'Base de origem', param: 'baseOrigem', format: (id): string | null => (id ? this.nomeBase(id) : null) }),
      idBaseDestino: cesFilter.value<string | null>(null, { label: 'Base de destino', param: 'baseDestino', format: (id): string | null => (id ? this.nomeBase(id) : null) }),
      idRequisitante: cesFilter.value<string | null>(null, {
        label: 'Requisitante',
        param: 'requisitante',
        format: (id): string | null => (id ? (this.usuarioOptions().find((o) => o.value === id)?.label ?? 'selecionado') : null)
      }),
      status: cesFilter.list<SolicitacaoStatus>([], { label: 'Status', itemLabel: (s) => statusComercial(s).label }),
      referencia: cesFilter.value<string | null>(null, { label: 'Referência', param: 'ref', type: 'string' }),
      // Nº de série é dado sensível: fica fora da URL.
      numSerie: cesFilter.value<string>('', { label: 'Nº de série', url: false }),
      periodo: cesFilter.range({ from: null, to: null }, { label: 'Solicitação' })
    },
    sort: [{ key: 'solicitado', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<SolicitacaoSummaryResponse>(
    (pedido) =>
      this.visao !== 'tabela'
        ? of({ content: [], page: { size: 0, number: 0, totalElements: 0, totalPages: 0 } })
        : this.almoxarifado.listarSolicitacoes({
            ...this.filtro(),
            page: pedido.page,
            size: pedido.size,
            sort: pedido.sort.map((r) => `${r.key},${r.direction}`)[0] ?? 'dataSolicitacao,desc'
          }),
    { errorMessage: getErrorMessage }
  );

  protected readonly statusAplicados = computed(() => this.lista.applied().status);
  protected readonly emAbertoAtivo = computed(() => mesmoConjunto(this.statusAplicados(), STATUS_EM_ABERTO));

  /** Recorte aplicado para o quadro e o calendário (sem paginação). Só muda quando o recorte muda. */
  protected readonly filtroVisao = computed(
    () => {
      this.lista.applied();
      return this.filtro();
    },
    { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) }
  );
  protected readonly filtrosExportacao = computed(() => {
    this.lista.applied();
    const f = this.filtro();
    return { ...f, dataInicio: f['dataInicio'] ?? null, dataFim: f['dataFim'] ?? null, buscaGlobal: f['buscaGlobal'] ?? null };
  });

  /** Contagem de cada status no recorte atual, ignorando os atalhos (para o número não sumir ao filtrar). */
  protected readonly contagens = signal<Partial<Record<SolicitacaoStatus, number>>>({});
  private readonly versaoContagens = signal(0);
  protected readonly contagemTotal = computed(() => this.somar(ORDEM_STATUS));
  protected readonly contagemAbertas = computed(() => this.somar(STATUS_EM_ABERTO));

  /** O que o Copiloto recebe: o recorte em tela e a contagem de cada status. */
  protected readonly contextoCopiloto = computed<CopilotoDashboardContext | null>(() => {
    const f = this.lista.applied();
    const c = this.contagens();
    const status = STATUS_COMERCIAIS.filter((s) => c[s.value] !== undefined);
    return {
      id: 'almoxarifado.solicitacoes-comerciais',
      nome: 'Gestão de solicitações comerciais',
      modulo: 'Almoxarifado',
      descricao: 'Fila de solicitações comerciais de material: aprovação pelo almoxarifado, retirada pela equipe (saque previsto) e encerramento.',
      rota: VISOES.find((v) => v.value === this.visao)!.rota,
      periodo: periodoPainel(f.periodo.from ? dataLocal(f.periodo.from) : null, f.periodo.to ? dataLocal(f.periodo.to) : null),
      filtros: [
        filtroPainel('status', 'Status', f.status.map((s) => statusComercial(s).label), f.status),
        filtroPainel('estoqueOrigem', 'Estoque de origem', f.idEstoqueOrigem ? this.nomeEstoque(f.idEstoqueOrigem) : null, f.idEstoqueOrigem),
        filtroPainel('estoqueDestino', 'Estoque de destino', f.idEstoqueDestino ? this.nomeEstoque(f.idEstoqueDestino) : null, f.idEstoqueDestino),
        filtroPainel('baseOrigem', 'Base de origem', f.idBaseOrigem ? this.nomeBase(f.idBaseOrigem) : null, f.idBaseOrigem),
        filtroPainel('baseDestino', 'Base de destino', f.idBaseDestino ? this.nomeBase(f.idBaseDestino) : null, f.idBaseDestino),
        filtroPainel('referencia', 'Referência', f.referencia, f.referencia)
      ],
      metricas: [
        metricaPainel('emAberto', 'Em aberto (aguardando aprovação ou retirada)', this.contagemAbertas(), 'inteiro'),
        ...status.map((s) => metricaPainel(`status.${s.value}`, s.atalho, c[s.value], 'inteiro'))
      ],
      graficos: status.length
        ? [graficoPainel('porStatus', 'rosca', 'Solicitações por status', status.map((s) => s.label), status.map((s) => c[s.value] ?? 0), { metrica: 'solicitações', dimensao: 'status', formato: 'inteiro' })]
        : [],
      datasets: ['solicitacoes', 'itens_solicitacao', 'operacoes_solicitacao', 'historico_solicitacoes', 'materiais', 'estoques'],
      sugestoes: [
        { rotulo: 'Priorizar aprovações', icone: 'pi pi-clock', requer: ['dados'], pergunta: 'Quais solicitações comerciais deste recorte devo aprovar primeiro, considerando o saque previsto e as pendências?' },
        { rotulo: 'Retiradas atrasadas', icone: 'pi pi-truck', requer: ['dados'], pergunta: 'Quais retiradas estão atrasadas neste recorte, para quais equipes, e há saldo na origem para entregá-las?' },
        { rotulo: 'Pendências abertas', icone: 'pi pi-flag', requer: ['dados'], pergunta: 'Resuma as pendências abertas nas solicitações deste recorte, agrupadas por motivo.' }
      ]
    };
  });

  protected readonly colunas: CesDataTableColumn<SolicitacaoSummaryResponse>[] = [
    { key: 'id', header: 'Solicitação', type: 'custom', priority: 1, sortField: 'id', value: (s) => s.id },
    { key: 'tipo', header: 'Tipo', type: 'custom', value: (s) => this.tipoCodigo(s.tipoTecnico) },
    { key: 'origem', header: 'Origem', priority: 1, sortField: 'estoqueOrigem.nomeIdentificador', value: (s) => s.estoqueOrigemNome },
    { key: 'destino', header: 'Destino', priority: 1, sortField: 'estoqueDestino.nomeIdentificador', value: (s) => s.estoqueDestinoNome },
    { key: 'status', header: 'Status', type: 'custom', priority: 1, sortField: 'status', value: (s) => this.statusLabel(s.status) },
    { key: 'solicitado', header: 'Solicitado', type: 'date', sortField: 'dataSolicitacao', value: (s) => s.dataSolicitacao, display: (s) => dataHora(s.dataSolicitacao) },
    { key: 'saque', header: 'Saque previsto', type: 'custom', sortField: 'dataPrevistaRetirada', value: (s) => s.dataPrevista },
    { key: 'verificado', header: 'Verificado', type: 'date', sortField: 'dataVerificacao', value: (s) => s.dataVerificacao, display: (s) => dataHora(s.dataVerificacao) },
    { key: 'entregue', header: 'Entregue', type: 'date', sortField: 'dataEntrega', value: (s) => s.dataEntrega, display: (s) => dataHora(s.dataEntrega) },
    { key: 'itens', header: 'Itens', type: 'integer', value: (s) => s.totalItens }
  ];
  protected readonly rotulo = (s: SolicitacaoSummaryResponse) => `Solicitação #${s.id?.substring(0, 8)}`;

  // Estorno parcial
  protected readonly estornoId = signal<string | null>(null);
  protected readonly estornoItens = signal<ItemEstorno[]>([]);
  protected readonly estornoSelecionados = signal<ItemEstorno[]>([]);
  protected readonly estornando = signal(false);
  protected readonly chaveItem = (i: ItemEstorno) => i.id;
  protected readonly colunasEstorno: CesDataTableColumn<ItemEstorno>[] = [
    { key: 'material', header: 'Material', type: 'custom', priority: 1, value: (i) => i.material?.descricao },
    { key: 'entregue', header: 'Entregue', type: 'number', value: (i) => i.quantidadeEntregue },
    { key: 'estorno', header: 'Estornar', type: 'custom', priority: 1, value: (i) => i.quantidadeEstorno }
  ];

  constructor() {
    this.almoxarifado.listarEstoquesAtivos().subscribe({ next: (r) => this.estoques.set(r.content), error: () => this.estoques.set([]) });
    this.almoxarifado.listarMateriais({ page: 0, size: 1000, ativo: true }).subscribe({ next: (r) => this.materiais.set(r?.content || []), error: () => this.materiais.set([]) });

    const consultaContagens = computed(
      () => {
        const { statuses: _s, ...semStatus } = this.filtroVisao();
        return { filtro: semStatus, versao: this.versaoContagens() };
      },
      { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) }
    );
    toObservable(consultaContagens)
      .pipe(
        switchMap(({ filtro }) =>
          forkJoin(
            ORDEM_STATUS.map((status) =>
              this.almoxarifado.listarSolicitacoes({ ...filtro, statuses: [status], page: 0, size: 1 }).pipe(
                map((r) => [status, r.page?.totalElements ?? 0] as const),
                catchError(() => of([status, undefined] as const))
              )
            )
          )
        ),
        takeUntilDestroyed()
      )
      .subscribe((pares) => this.contagens.set(Object.fromEntries(pares.filter(([, n]) => n !== undefined))));
  }

  /** Recorte aplicado no formato do backend (lista, quadro, calendário e exportação). */
  private filtro(): Record<string, unknown> {
    const { filters: f, query } = this.lista.request();
    const params: Record<string, unknown> = { contexto: 'COMERCIAL' };
    if (f.idEstoqueOrigem) params['idEstoqueOrigem'] = f.idEstoqueOrigem;
    if (f.idEstoqueDestino) params['idEstoqueDestino'] = f.idEstoqueDestino;
    if (f.idBaseOrigem) params['idBaseOrigem'] = f.idBaseOrigem;
    if (f.idBaseDestino) params['idBaseDestino'] = f.idBaseDestino;
    if (f.idRequisitante) params['idRequisitante'] = f.idRequisitante;
    // `statuses` é a lista no backend; `status` aceita só um valor.
    if (f.status.length) params['statuses'] = [...f.status];
    if (f.numSerie.trim()) params['numSerie'] = f.numSerie.trim();
    if (f.referencia) params['referencia'] = f.referencia;
    if (f.periodo.from) params['dataInicio'] = dataLocal(f.periodo.from)!.toISOString();
    if (f.periodo.to) params['dataFim'] = dataLocal(f.periodo.to)!.toISOString();
    if (query) params['buscaGlobal'] = query;
    return params;
  }

  private somar(status: readonly SolicitacaoStatus[]): number | null {
    const c = this.contagens();
    if (!status.every((s) => c[s] !== undefined)) return null;
    return status.reduce((acc, s) => acc + (c[s] ?? 0), 0);
  }

  private nomeEstoque(id: string): string {
    return this.estoques().find((e) => e.id === id)?.nomeIdentificador ?? 'selecionado';
  }

  private nomeBase(id: string): string {
    return this.baseOptions().find((o) => o.value === id)?.label ?? 'selecionada';
  }

  protected definirStatus(status: readonly SolicitacaoStatus[]): void {
    this.lista.set('status', [...status]);
  }

  /** Atalho de status: liga/desliga o status, somando aos já escolhidos. */
  protected alternarStatus(status: SolicitacaoStatus): void {
    const atuais = this.statusAplicados();
    const proximos = atuais.includes(status) ? atuais.filter((s) => s !== status) : [...atuais, status];
    this.definirStatus(ORDEM_STATUS.filter((s) => proximos.includes(s)));
  }

  protected trocarVisao(visao: VisaoSolicitacoes): void {
    if (visao === this.visao) return;
    void this.router.navigate([VISOES.find((v) => v.value === visao)!.rota], { queryParams: this.lista.queryParams() });
  }

  protected verNaTabela(status: SolicitacaoStatus[]): void {
    void this.router.navigate(['/almoxarifado/solicitacoes'], { queryParams: { ...this.lista.queryParams(), status: status.join(',') } });
  }

  protected periodo(ponta: 'from' | 'to', data: Date | null): void {
    this.lista.filters.periodo.update((atual) => ({ ...atual, [ponta]: dataIso(data) }));
  }

  protected dataCivil(iso: string): string {
    return new Date(`${iso.substring(0, 10)}T00:00:00`).toLocaleDateString('pt-BR');
  }

  protected statusLabel(status: SolicitacaoStatus): string {
    return statusComercial(status).label;
  }

  protected statusTone(status: SolicitacaoStatus | undefined): CesTone {
    return statusComercial(status).tone;
  }

  protected statusIcon(status: SolicitacaoStatus | undefined): string {
    return statusComercial(status).icon;
  }

  protected atrasada(s: SolicitacaoSummaryResponse): boolean {
    return saqueAtrasado(s);
  }

  protected relativo(data?: string | null): string {
    return data ? prazoRelativo(data) : '';
  }

  protected tipoCodigo(tipo?: string | null): string {
    if (!tipo || tipo === 'ADICAO_COMUM' || tipo === '221') return '221';
    if (ehDevolucao(tipo)) return '222';
    return tipo;
  }

  protected tipoTone(tipo?: string | null): CesTone {
    return ehDevolucao(tipo) ? 'neutral' : 'info';
  }

  protected tipoIcon(tipo?: string | null): string {
    return ehDevolucao(tipo) ? 'pi-trash' : 'pi-shopping-cart';
  }

  protected novo(): void {
    void this.router.navigate(['/almoxarifado/solicitacoes/nova']);
  }

  protected irParaDevolucaoComercial(): void {
    void this.router.navigate(['/almoxarifado/devolucao-comercial']);
  }

  /** Detalhe rápido em painel lateral, igual nas três visualizações. */
  protected abrirDetalhe(solicitacao: { id: string } | null | undefined, aba: AbaDetalheComercial = 'itens'): void {
    if (!solicitacao?.id) return;
    // Abaixo de 1280px o Copiloto sobrepõe a tela: não deixa os dois painéis empilhados.
    if (typeof matchMedia === 'function' && !matchMedia('(min-width: 1280px)').matches) this.copiloto.fechar();
    this.detalheId.set(solicitacao.id);
    this.detalheAba.set(aba);
    this.detalheAberto.set(true);
  }

  /** "Analisar" de uma coluna do quadro: abre o Copiloto ao lado, com o recorte da tela. */
  protected analisarEtapa(etapa: { id: string; titulo: string; pergunta: string }): void {
    this.copiloto.perguntarSobre({ tipo: 'secao', id: `quadro.${etapa.id}`, rotulo: `Etapa ${etapa.titulo}` }, etapa.pergunta);
  }

  /** "Analisar" do detalhe: fecha o painel da solicitação e abre o Copiloto sobre ela. */
  protected analisarSolicitacao(pedido: { id: string; rotulo: string; pergunta: string }): void {
    this.detalheAberto.set(false);
    this.copiloto.perguntarSobre({ tipo: 'secao', id: `solicitacao.${pedido.id}`, rotulo: pedido.rotulo }, pedido.pergunta);
  }

  /** Algo mudou (aprovação, entrega, rejeição, pendência, importação): atualiza a visão e as contagens. */
  protected aoAlterar(origem?: VisaoSolicitacoes): void {
    this.versaoContagens.update((v) => v + 1);
    if (origem === this.visao) return;
    if (this.visao === 'kanban') this.quadro()?.recarregar();
    else if (this.visao === 'calendario') this.calendario()?.recarregar();
    else this.lista.reload();
  }

  protected imprimir(solicitacao: SolicitacaoSummaryResponse): void {
    const url = this.router.serializeUrl(this.router.createUrlTree(['/almoxarifado/solicitacoes', solicitacao.id], { queryParams: { mode: 'print' } }));
    window.open(url, '_blank', 'noopener');
  }

  protected showLogs(solicitacao: SolicitacaoSummaryResponse): void {
    this.auditoria().show(solicitacao.id, `Solicitação: ${solicitacao.id?.substring(0, 8)}`, {
      id: 'ID',
      idEstoqueOrigem: 'ID Estoque Origem',
      idEstoqueDestino: 'ID Estoque Destino',
      status: 'Status',
      dataSolicitacao: 'Data Solicitação',
      dataVerificacao: 'Data Verificação',
      dataEntrega: 'Data Entrega',
      idRequisitante: 'ID Requisitante',
      idAprovador: 'ID Aprovador',
      idEntregadorAlmox: 'ID Entregador',
      totalItens: 'Total de Itens'
    });
  }

  protected selecionado(item: ItemEstorno): boolean {
    return this.estornoSelecionados().some((i) => i.id === item.id);
  }

  /** Abre o estorno com todos os itens entregues selecionados, na quantidade entregue. */
  protected confirmarEstorno(solicitacao: SolicitacaoSummaryResponse): void {
    this.almoxarifado.buscarSolicitacaoPorId(solicitacao.id).subscribe({
      next: (res) => {
        const itens = res.itens.filter((i) => i.quantidadeEntregue > 0).map((i) => ({ ...i, quantidadeEstorno: i.quantidadeEntregue }));
        this.estornoItens.set(itens);
        this.estornoSelecionados.set([...itens]);
        this.estornoId.set(res.id);
      },
      error: (err) => this.notificacoes.error(getErrorMessage(err), { title: 'Erro ao carregar os itens' })
    });
  }

  protected async confirmarEstornoParcial(): Promise<void> {
    const id = this.estornoId();
    if (!id || this.estornando()) return;
    const selecionados = this.estornoItens().filter((i) => this.selecionado(i));
    if (!selecionados.length) return this.notificacoes.warning('Selecione ao menos um item para estornar.');
    if (selecionados.some((i) => (i.quantidadeEstorno || 0) <= 0)) return this.notificacoes.warning('A quantidade de estorno deve ser maior que zero.');
    if (selecionados.some((i) => (i.quantidadeEstorno || 0) > i.quantidadeEntregue))
      return this.notificacoes.warning('A quantidade de estorno não pode ser maior que a entregue.');

    const ok = await this.confirmacao.confirm({
      title: 'Confirmar estorno',
      message: `Estornar ${selecionados.length} ${selecionados.length === 1 ? 'item' : 'itens'}? Os saldos voltam ao estoque de origem e a ação não pode ser desfeita.`,
      confirmLabel: 'Estornar',
      variant: 'destructive'
    });
    if (!ok) return;
    this.estornando.set(true);
    this.almoxarifado.estornarParcial(id, { itens: selecionados.map((i) => ({ idItemSolicitacao: i.id, quantidade: i.quantidadeEstorno || 0 })) }).subscribe({
      next: () => {
        this.estornando.set(false);
        this.estornoId.set(null);
        this.notificacoes.success('Estorno realizado.');
        this.aoAlterar();
      },
      error: (err) => {
        this.estornando.set(false);
        this.notificacoes.error(getErrorMessage(err), { title: 'Erro no estorno' });
      }
    });
  }
}

function lerVisao(modo: unknown): VisaoSolicitacoes {
  return modo === 'kanban' || modo === 'calendario' ? modo : 'tabela';
}
