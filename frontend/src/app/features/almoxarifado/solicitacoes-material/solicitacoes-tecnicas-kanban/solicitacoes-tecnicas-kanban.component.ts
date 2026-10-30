import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesDialogShellComponent,
  CesKanbanBoardComponent,
  CesKanbanColumn,
  CesKanbanItemMovedEvent,
  CesKanbanMetrica,
  CesKanbanSubGroup,
  CesKanbanSubGroupToggleEvent,
  CesTone,
  getSubGroupTheme,
  KanbanCopilotoContext,
  NotificationService,
} from '@ces/ui';
import {
  KanbanColunaResponse,
  MacroEtapaKanban,
  PlanejamentoSolicitacaoTecnicaResponse,
  SolicitacaoMaterialDetalheResponse,
  SolicitacaoMaterialFilter,
  SolicitacaoMaterialSummaryResponse,
} from '../../../../core/models/solicitacao-material-tecnica.models';
import { MotivoRejeicaoResponse } from '../../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { tipoTecnicoLabel } from '../solicitacao-tecnica-presentation';
import { SolicitacaoTecnicaKanbanCardComponent } from './solicitacao-tecnica-kanban-card.component';
import { SolicitacaoTecnicaKanbanDetailComponent } from './solicitacao-tecnica-kanban-detail.component';
import { SolicitacaoTecnicaPlanejamentoComponent } from '../solicitacao-tecnica-planejamento/solicitacao-tecnica-planejamento.component';
import {
  SolicitacaoTecnicaAcaoComponent,
  TechnicalActionMode,
} from '../solicitacao-tecnica-acao/solicitacao-tecnica-acao.component';

interface MacroColunaDef {
  etapa: MacroEtapaKanban;
  label: string;
  icon: string;
  tone: CesTone;
}

const MACRO_COLUNAS_KANBAN: MacroColunaDef[] = [
  {
    etapa: 'PLANEJAMENTO',
    label: 'Planejamento',
    icon: 'pi pi-calendar',
    tone: 'info',
  },
  {
    etapa: 'SEPARACAO',
    label: 'Separação',
    icon: 'pi pi-box',
    tone: 'warning',
  },
  {
    etapa: 'ENTREGA',
    label: 'Entrega & Campo',
    icon: 'pi pi-truck',
    tone: 'success',
  },
  {
    etapa: 'RECUSADO',
    label: 'Recusado',
    icon: 'pi pi-times-circle',
    tone: 'danger',
  },
];

const STORAGE_KEY_COLLAPSED = 'ces_kanban_substatus_collapsed';

@Component({
  selector: 'app-solicitacoes-tecnicas-kanban',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    SelectModule,
    TextareaModule,
    CesKanbanBoardComponent,
    CesButtonComponent,
    CesAsyncStateComponent,
    CesDialogShellComponent,
    SolicitacaoTecnicaKanbanCardComponent,
    SolicitacaoTecnicaKanbanDetailComponent,
    SolicitacaoTecnicaPlanejamentoComponent,
    SolicitacaoTecnicaAcaoComponent,
  ],
  template: `
    <div class="flex flex-col gap-3 w-full">
      <!-- Barra de Controle do Kanban: Ações rápidas & Status de atualização -->
      <div class="flex items-center justify-between gap-3 px-1 py-0.5">
        <div class="flex items-center gap-2">
          <span class="text-xs font-semibold text-[var(--ces-fg-muted)]">
            Visão Geral das Fases Operacionais
          </span>
          @if (totalGeral() > 0) {
            <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-[var(--ces-surface-subtle)] text-[var(--ces-fg-muted)] border border-[var(--ces-border)]">
              {{ totalGeral() }} solicitações
            </span>
          }
        </div>

        <div class="flex items-center gap-2">
          <ces-button
            icon="pi pi-refresh"
            label="Atualizar Quadro"
            variant="ghost"
            size="sm"
            [loading]="loading()"
            (pressed)="recarregar()"
          />
        </div>
      </div>

      <!-- ESTADO: CARREGANDO -->
      @if (loading()) {
        <div class="flex items-center justify-center p-12">
          <ces-async-state
            state="loading"
            title="Carregando quadro Kanban..."
            description="Consultando solicitações agrupadas por macro-etapa e sub-status..."
          />
        </div>
      }

      <!-- ESTADO: ERRO -->
      @else if (erro()) {
        <div class="flex items-center justify-center p-8">
          <ces-async-state
            state="error"
            title="Erro ao carregar o Kanban"
            [description]="erro()!"
            actionLabel="Tentar novamente"
            (action)="recarregar()"
          />
        </div>
      }

      <!-- ESTADO: SUCESSO / QUADRO KANBAN -->
      @else {
        <ces-kanban-board
          #board
          [columns]="colunas()"
          [cardTemplate]="cardTpl"
          ariaLabel="Quadro Kanban de Solicitações Técnicas"
          [copilotoEnabled]="true"
          (itemMoved)="onItemMoved($event)"
          (subGroupToggled)="onSubGroupToggled($event)"
          (loadMore)="onLoadMore($event)"
          (copilotoClicked)="onCopilotoClicked($event)"
        />

        <ng-template #cardTpl let-item let-column="column" let-subGroup="subGroup">
          <app-solicitacao-tecnica-kanban-card
            [item]="item"
            [columnId]="column.id"
            [subGroupId]="subGroup?.id"
            (cardClicked)="abrirMiniDetalhe($event)"
            (dragStarted)="board.startDrag($event.item, $event.columnId, $event.subGroupId)"
            (dragEnded)="board.endDrag()"
            (actionTriggered)="onCardAction($event)"
          />
        </ng-template>
      }

      <!-- Dialog de Mini-Detalhamento com padrão de relatório oficial -->
      <app-solicitacao-tecnica-kanban-detail
        [(visible)]="detailVisible"
        [item]="selectedItem()"
        (actionRequested)="onDetailActionRequested($event)"
      />

      <!-- MODAL OFICIAL DE PLANEJAMENTO IN-PLACE -->
      @if (planningVisible()) {
        <app-solicitacao-tecnica-planejamento
          [visible]="planningVisible()"
          (visibleChange)="planningVisible.set($event)"
          [detail]="planningDetail()"
          (completed)="onPlanningCompleted($event)"
          (conflict)="onConflictOccurred($event)"
        />
      }

      <!-- MODAL OFICIAL DE AÇÃO IN-PLACE (ENTREGA, DEVOLUÇÃO, ENCERRAMENTO) -->
      @if (actionMode(); as mode) {
        <app-solicitacao-tecnica-acao
          [visible]="actionVisible()"
          (visibleChange)="actionVisible.set($event)"
          [detail]="actionDetail()"
          [mode]="mode"
          (completed)="onActionCompleted($event)"
          (conflict)="onConflictOccurred($event)"
        />
      }

      <!-- MODAL OFICIAL DE REJEIÇÃO IN-PLACE -->
      <ces-dialog-shell
        [(visible)]="rejectModalVisible"
        title="Rejeitar Solicitação Técnica"
        description="A rejeição cancelará permanentemente a solicitação técnica de material."
        size="md"
      >
        <div class="flex flex-col gap-4 py-1">
          <div class="bg-rose-50/70 dark:bg-rose-950/40 p-3.5 rounded-xl border border-rose-200 dark:border-rose-900/60 flex items-start gap-3">
            <i class="pi pi-exclamation-circle text-rose-600 dark:text-rose-400 mt-0.5 text-base shrink-0"></i>
            <div class="flex flex-col gap-0.5">
              <span class="text-xs font-bold text-rose-800 dark:text-rose-300 uppercase tracking-wider">Atenção</span>
              <p class="text-xs text-rose-700 dark:text-rose-300 leading-relaxed">
                A rejeição é irreversível e impedirá separações ou entregas físicas para esta solicitação.
              </p>
            </div>
          </div>

          @if (rejectItem(); as item) {
            <div class="p-3 bg-slate-50 dark:bg-slate-800/60 rounded-xl border border-slate-200 dark:border-slate-700 text-xs flex flex-col gap-2">
              <div class="flex items-center justify-between border-b border-slate-200 dark:border-slate-700 pb-1.5">
                <span class="font-semibold text-slate-700 dark:text-slate-300">Solicitação:</span>
                <span class="font-mono text-slate-600 dark:text-slate-400 font-bold">#{{ item.id.slice(0, 8) }}</span>
              </div>
              <div class="grid grid-cols-2 gap-2 text-[11px]">
                <div>
                  <span class="text-slate-500 dark:text-slate-400">Tipo:</span>
                  <span class="font-medium text-slate-800 dark:text-slate-200 ml-1">{{ tipoTecnicoLabel(item.tipo) }}</span>
                </div>
                <div>
                  <span class="text-slate-500 dark:text-slate-400">Ocorrência:</span>
                  <span class="font-mono font-medium text-slate-800 dark:text-slate-200 ml-1">{{ item.ocorrencia?.codigo || '—' }}</span>
                </div>
              </div>
            </div>
          }

          <div class="flex flex-col gap-3.5">
            <div class="flex flex-col gap-1.5">
              <label class="text-xs font-semibold text-slate-700 dark:text-slate-300">
                Motivo da Rejeição <span class="text-rose-500">*</span>
              </label>
              <p-select
                [(ngModel)]="selectedMotivoRejeicao"
                [options]="motivosRejeicaoOptions()"
                optionLabel="nome"
                size="small"
                optionValue="id"
                placeholder="Selecione o motivo..."
                class="w-full text-xs"
                appendTo="body"
              >
                <ng-template let-motivo pTemplate="item">
                  <div class="flex flex-col gap-0.5">
                    <span class="text-xs font-semibold text-slate-800 dark:text-slate-200">{{ motivo.nome }}</span>
                    <span class="text-[11px] text-slate-500 dark:text-slate-400 leading-tight">{{ motivo.descricao }}</span>
                  </div>
                </ng-template>
              </p-select>
            </div>

            <div class="flex flex-col gap-1.5">
              <label class="text-xs font-semibold text-slate-700 dark:text-slate-300">
                Descrição / Justificativa da Rejeição <span class="text-rose-500">*</span>
              </label>
              <textarea
                pTextarea
                [(ngModel)]="justificativaRejeicao"
                rows="4"
                placeholder="Descreva detalhadamente o porquê desta rejeição..."
                class="w-full text-xs rounded-xl border-slate-300 dark:border-slate-700 focus:border-rose-500 bg-white dark:bg-slate-900"
              ></textarea>
              <span class="text-[10px] text-slate-500 dark:text-slate-400 italic">* Mínimo de 5 caracteres.</span>
            </div>
          </div>
        </div>

        <div dialog-footer class="flex items-center justify-end gap-2">
          <ces-button label="Cancelar" variant="ghost" size="sm" (pressed)="rejectModalVisible.set(false)" />
          <ces-button
            label="Confirmar Rejeição"
            icon="pi pi-times-circle"
            variant="destructive"
            size="sm"
            [loading]="rejectLoading()"
            [disabled]="!selectedMotivoRejeicao() || !justificativaRejeicao() || justificativaRejeicao().trim().length < 5"
            (pressed)="confirmarRejeicao()"
          />
        </div>
      </ces-dialog-shell>

      <!-- MODAL DE CONFLITO DE VERSÃO (409) -->
      <ces-dialog-shell
        [(visible)]="conflictVisible"
        title="Conflito de versão"
        description="Esta solicitação foi atualizada recentemente por outro usuário ou processo."
        size="sm"
      >
        <div class="flex flex-col gap-3 py-1">
          <div class="flex items-center gap-2.5 p-2.5 rounded-lg bg-amber-50/70 dark:bg-amber-950/30 border border-amber-100 dark:border-amber-900/40 text-amber-800 dark:text-amber-300">
            <i class="pi pi-exclamation-triangle text-amber-600 text-base shrink-0"></i>
            <div class="flex flex-col gap-0.5 text-xs">
              <span class="font-medium">Atualização de segurança</span>
              <span class="text-[11px] text-amber-700 dark:text-amber-300">Os dados mudaram para evitar sobrescrita acidental.</span>
            </div>
          </div>
          <div class="text-xs text-slate-600 dark:text-slate-400 flex flex-col gap-1.5">
            <p><span class="font-medium">Ação em execução:</span> {{ conflictIntent() }}</p>
            @if (conflictVersions()) {
              <p class="text-slate-500 text-[11px] bg-slate-100 dark:bg-slate-800 p-2 rounded">
                {{ conflictVersions() }}
              </p>
            }
            <p class="mt-0.5 text-[11px]">
              Clique em <span class="font-medium">Recarregar</span> para atualizar o quadro Kanban com os dados mais recentes.
            </p>
          </div>
        </div>
        <div dialog-footer class="flex items-center justify-end gap-2">
          <ces-button label="Fechar" variant="ghost" size="sm" (pressed)="conflictVisible.set(false)" />
          <ces-button label="Recarregar e atualizar" icon="pi pi-refresh" variant="primary" size="sm" (pressed)="reconciliarConflito()" />
        </div>
      </ces-dialog-shell>
    </div>
  `,
})
export class SolicitacoesTecnicasKanbanComponent {
  private readonly service = inject(SolicitacaoMaterialTecnicaService);
  private readonly almoxarifadoService = inject(AlmoxarifadoService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);

  readonly filtros = input<Partial<SolicitacaoMaterialFilter>>({});

  /** Notifica o componente pai para alternar para a lista tabular filtrada por etapa */
  readonly navegarParaEtapa = output<string>();

  protected readonly board = viewChild<CesKanbanBoardComponent<SolicitacaoMaterialSummaryResponse>>('board');

  protected readonly loading = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly colunas = signal<CesKanbanColumn<SolicitacaoMaterialSummaryResponse>[]>([]);
  protected readonly totalGeral = signal(0);

  protected readonly selectedItem = signal<SolicitacaoMaterialSummaryResponse | null>(null);
  protected readonly detailVisible = signal(false);

  // Estados dos Diálogos In-Place
  protected readonly planningVisible = signal(false);
  protected readonly planningDetail = signal<SolicitacaoMaterialDetalheResponse | null>(null);

  protected readonly actionVisible = signal(false);
  protected readonly actionMode = signal<TechnicalActionMode | null>(null);
  protected readonly actionDetail = signal<SolicitacaoMaterialDetalheResponse | null>(null);

  protected readonly rejectModalVisible = signal(false);
  protected readonly rejectItem = signal<SolicitacaoMaterialSummaryResponse | null>(null);
  protected readonly motivosRejeicaoOptions = signal<MotivoRejeicaoResponse[]>([]);
  protected readonly selectedMotivoRejeicao = signal('');
  protected readonly justificativaRejeicao = signal('');
  protected readonly rejectLoading = signal(false);

  protected readonly conflictVisible = signal(false);
  protected readonly conflictIntent = signal('');
  protected readonly conflictVersions = signal('');

  protected readonly tipoTecnicoLabel = tipoTecnicoLabel;

  constructor() {
    effect(() => {
      const f = this.filtros();
      this.carregarKanban(f);
    });
  }

  carregarKanban(filtros: Partial<SolicitacaoMaterialFilter> = this.filtros()): void {
    this.loading.set(true);
    this.erro.set(null);

    this.service.obterKanban(filtros).subscribe({
      next: (resposta) => {
        let total = 0;
        const colunasMap = new Map<string, KanbanColunaResponse>();
        for (const col of resposta.colunas) {
          colunasMap.set(col.etapa, col);
          total += col.metricas?.total ?? 0;
        }

        const colapsosSalvos = this.carregarPreferenciasColapso();

        const colunasFormatadas: CesKanbanColumn<SolicitacaoMaterialSummaryResponse>[] =
          MACRO_COLUNAS_KANBAN.map((def) => {
            const data = colunasMap.get(def.etapa);
            const m = data?.metricas;
            const totalCount = m?.total ?? 0;

            const metricas: CesKanbanMetrica[] = [
              { label: 'Total', valor: totalCount },
              {
                label: 'Atrasadas',
                valor: m?.atrasadas ?? 0,
                alerta: (m?.atrasadas ?? 0) > 0,
              },
              {
                label: 'Fora do prazo',
                valor: m?.foraPrazo ?? 0,
                alerta: (m?.foraPrazo ?? 0) > 0,
              },
            ];

            if (m?.diasMaisAntiga !== null && m?.diasMaisAntiga !== undefined) {
              metricas.push({
                label: 'Mais antiga',
                valor: m.diasMaisAntiga,
                formato: 'dias',
              });
            }

            const subGroups: CesKanbanSubGroup<SolicitacaoMaterialSummaryResponse>[] =
              (data?.subColunas ?? []).map((sub) => {
                const totalCards = sub.total ?? sub.itens?.length ?? 0;
                let isCollapsed: boolean;
                if (totalCards === 0) {
                  isCollapsed = true;
                } else if (colapsosSalvos[sub.subEtapa] !== undefined) {
                  isCollapsed = colapsosSalvos[sub.subEtapa];
                } else {
                  isCollapsed = sub.subEtapa === 'CONCLUIDO' || sub.subEtapa === 'CANCELADO';
                }

                return {
                  id: sub.subEtapa,
                  label: sub.label,
                  items: sub.itens ?? [],
                  totalCount: totalCards,
                  collapsed: isCollapsed,
                  style: getSubGroupTheme(sub.subEtapa),
                };
              });

            return {
              id: def.etapa,
              label: def.label,
              icon: def.icon,
              tone: def.tone,
              items: [],
              totalCount,
              metricas,
              subGroups,
            };
          });

        this.colunas.set(colunasFormatadas);
        this.totalGeral.set(total);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.erro.set('Não foi possível carregar as informações do Kanban. Verifique a conexão.');
      },
    });
  }

  recarregar(): void {
    this.carregarKanban(this.filtros());
  }

  protected abrirMiniDetalhe(item: SolicitacaoMaterialSummaryResponse): void {
    this.selectedItem.set(item);
    this.detailVisible.set(true);
  }

  /**
   * Abre o diálogo oficial de planejamento in-place, consultando o detalhe atualizado.
   */
  protected abrirPlanejamento(idOuItem: string | { id: string }): void {
    const id = typeof idOuItem === 'string' ? idOuItem : idOuItem.id;
    this.service.buscarPorId(id).subscribe({
      next: (detalhe) => {
        this.planningDetail.set(detalhe);
        this.planningVisible.set(true);
      },
      error: (err) => {
        this.notifications.error(getErrorMessage(err) || 'Erro ao carregar dados da solicitação para planejamento.');
      },
    });
  }

  protected onPlanningCompleted(_response: PlanejamentoSolicitacaoTecnicaResponse): void {
    this.planningVisible.set(false);
    this.planningDetail.set(null);
    this.notifications.success('Solicitação técnica planejada com sucesso.');
    this.recarregar();
  }

  /**
   * Abre o diálogo oficial de ação in-place (entrega, devolução, encerramento de saldo).
   */
  protected abrirAcao(idOuItem: string | { id: string }, mode: TechnicalActionMode): void {
    const id = typeof idOuItem === 'string' ? idOuItem : idOuItem.id;
    this.service.buscarPorId(id).subscribe({
      next: (detalhe) => {
        this.actionDetail.set(detalhe);
        this.actionMode.set(mode);
        this.actionVisible.set(true);
      },
      error: (err) => {
        this.notifications.error(getErrorMessage(err) || 'Erro ao carregar dados da solicitação para a operação.');
      },
    });
  }

  protected onActionCompleted(_response?: unknown): void {
    this.actionVisible.set(false);
    this.actionMode.set(null);
    this.actionDetail.set(null);
    this.notifications.success('Operação registrada com sucesso.');
    this.recarregar();
  }

  /**
   * Abre o modal oficial de rejeição in-place com motivos ativos.
   */
  protected abrirRejeicao(item: SolicitacaoMaterialSummaryResponse): void {
    this.rejectItem.set(item);
    this.selectedMotivoRejeicao.set('');
    this.justificativaRejeicao.set('');
    this.almoxarifadoService.listarMotivosRejeicaoAtivos().subscribe({
      next: (res) => {
        this.motivosRejeicaoOptions.set(res.content ?? []);
        this.rejectModalVisible.set(true);
      },
      error: () => {
        this.notifications.error('Não foi possível carregar os motivos de rejeição.');
      },
    });
  }

  protected confirmarRejeicao(): void {
    const item = this.rejectItem();
    const idMotivo = this.selectedMotivoRejeicao();
    const justificativa = this.justificativaRejeicao().trim();

    if (!item || !idMotivo || justificativa.length < 5) {
      this.notifications.warning('Selecione o motivo e informe a justificativa (mínimo de 5 caracteres).');
      return;
    }

    this.rejectLoading.set(true);
    this.almoxarifadoService.rejeitarSolicitacao(item.id, { idMotivoRejeicao: idMotivo, justificativa }).subscribe({
      next: () => {
        this.rejectLoading.set(false);
        this.rejectModalVisible.set(false);
        this.rejectItem.set(null);
        this.notifications.success(`Solicitação #${item.id.slice(0, 8)} rejeitada com sucesso.`);
        this.recarregar();
      },
      error: (err) => {
        this.rejectLoading.set(false);
        this.notifications.error(getErrorMessage(err) || 'Erro ao rejeitar a solicitação.');
      },
    });
  }

  protected onConflictOccurred(event: { intent: string; currentVersion?: number }): void {
    this.conflictIntent.set(event.intent || 'Operação concorrente');
    this.conflictVersions.set(
      event.currentVersion !== undefined ? `Versão atual no servidor: ${event.currentVersion}` : ''
    );
    this.conflictVisible.set(true);
  }

  protected reconciliarConflito(): void {
    this.conflictVisible.set(false);
    this.planningVisible.set(false);
    this.actionVisible.set(false);
    this.recarregar();
  }

  /**
   * Salva a preferência de colapso de cada sub-status no navegador do usuário.
   */
  protected onSubGroupToggled(event: CesKanbanSubGroupToggleEvent): void {
    const colapsos = this.carregarPreferenciasColapso();
    colapsos[event.subGroupId] = event.collapsed;
    try {
      localStorage.setItem(STORAGE_KEY_COLLAPSED, JSON.stringify(colapsos));
    } catch {}

    this.colunas.update((cols) =>
      cols.map((col) => {
        if (col.id !== event.columnId || !col.subGroups) {
          return col;
        }
        return {
          ...col,
          subGroups: col.subGroups.map((sub) =>
            sub.id === event.subGroupId ? { ...sub, collapsed: event.collapsed } : sub
          ),
        };
      })
    );
  }

  private readonly ETAPA_ORDEM: Record<string, number> = {
    PLANEJAMENTO: 10,
    PENDENTE_PLANEJAMENTO: 10,
    PLANEJADO: 20,
    SEPARACAO: 30,
    PRONTO_PARA_SEPARACAO: 30,
    EM_SEPARACAO: 40,
    SEPARADO: 50,
    ENTREGA: 60,
    PENDENTE_RETIRADA: 60,
    PENDENTE_DEVOLUCAO: 60,
    CONCLUIDO: 70,
    RECUSADO: 90,
    REJEITADO: 90,
    CANCELADO: 90,
  };

  /**
   * Trata o evento de arrastar um card entre colunas ou sub-etapas.
   * Executa atalhos rápidos e inteligentes, acionando diálogos oficiais in-place ou mutações diretas.
   */
  protected onItemMoved(event: CesKanbanItemMovedEvent<SolicitacaoMaterialSummaryResponse>): void {
    const { item, fromColumnId, toColumnId, fromSubGroupId, toSubGroupId } = event;
    const origem = fromSubGroupId ?? fromColumnId;
    const destino = toSubGroupId ?? toColumnId;

    if (origem === destino) {
      return;
    }

    // 1. Bloqueia movimentações a partir de estados finais/terminais
    if (origem === 'CONCLUIDO' || origem === 'REJEITADO' || origem === 'CANCELADO') {
      this.notifications.warning('Solicitações finalizadas, rejeitadas ou canceladas não podem ser movidas.');
      return;
    }

    // 2. Bloqueia retrocessos (arrastar para etapas ou sub-etapas anteriores)
    if (this.isRetrocesso(origem, destino)) {
      this.notifications.warning('Não é permitido retroceder solicitações para etapas anteriores.');
      return;
    }

    // 3. Arrastar para Planejado ou coluna Planejamento (Pendente Planejamento -> Planejado)
    if (destino === 'PLANEJADO' || (toColumnId === 'PLANEJAMENTO' && !toSubGroupId)) {
      if (item.acoesPermitidas?.includes('EDITAR_PLANEJAMENTO')) {
        this.abrirPlanejamento(item);
      } else {
        this.notifications.warning('Esta solicitação não permite edição de planejamento no momento.');
      }
      return;
    }

    // 4. Transição para "Em Separação"
    if (destino === 'EM_SEPARACAO') {
      if (item.acoesPermitidas?.includes('INICIAR_SEPARACAO')) {
        this.executarIniciarSeparacao(item);
      } else if (origem === 'PENDENTE_PLANEJAMENTO') {
        this.notifications.warning('A solicitação precisa ser planejada antes de iniciar a separação.');
        this.abrirPlanejamento(item);
      } else {
        this.notifications.warning('Esta solicitação não possui permissão para iniciar separação no momento.');
      }
      return;
    }

    // 5. Transição para "Separado" (Pular Separação ou Concluir Separação)
    if (destino === 'SEPARADO') {
      // Se veio de Pronto para Separação -> Pular separação!
      if (origem === 'PRONTO_PARA_SEPARACAO' && item.acoesPermitidas?.includes('PULAR_SEPARACAO')) {
        this.executarPularSeparacao(item);
        return;
      }
      // Se veio de Em Separação -> Concluir separação!
      if (origem === 'EM_SEPARACAO' && item.acoesPermitidas?.includes('CONCLUIR_SEPARACAO')) {
        this.executarConcluirSeparacao(item);
        return;
      }
      if (item.acoesPermitidas?.includes('CONCLUIR_SEPARACAO')) {
        this.executarConcluirSeparacao(item);
        return;
      }
      if (item.acoesPermitidas?.includes('PULAR_SEPARACAO')) {
        this.executarPularSeparacao(item);
        return;
      }
      this.notifications.warning('A separação desta solicitação ainda não pode ser concluída.');
      return;
    }

    // 6. Arrastar para a macro-coluna Separação (sem sub-grupo especificado)
    if (toColumnId === 'SEPARACAO' && !toSubGroupId) {
      if (item.acoesPermitidas?.includes('INICIAR_SEPARACAO')) {
        this.executarIniciarSeparacao(item);
        return;
      }
      if (origem === 'PENDENTE_PLANEJAMENTO') {
        this.notifications.warning('A solicitação precisa ser planejada antes da separação.');
        this.abrirPlanejamento(item);
        return;
      }
    }

    // 7. Arrastar para Entrega / Campo
    if (toColumnId === 'ENTREGA' || destino === 'PENDENTE_RETIRADA' || destino === 'PENDENTE_DEVOLUCAO') {
      if (item.acoesPermitidas?.includes('ENTREGAR')) {
        this.abrirAcao(item, 'deliver');
      } else if (item.acoesPermitidas?.includes('CONFIRMAR_DEVOLUCAO')) {
        this.abrirAcao(item, 'return');
      } else {
        this.notifications.warning('Conclua a separação de materiais antes de prosseguir com a entrega.');
      }
      return;
    }

    // 8. Arrastar para Concluído
    if (destino === 'CONCLUIDO') {
      if (item.acoesPermitidas?.includes('ENCERRAR_SALDO')) {
        this.abrirAcao(item, 'close');
      } else {
        this.notifications.warning('A conclusão ocorre automaticamente após a entrega de todos os itens, ou por encerramento de saldo.');
      }
      return;
    }

    // 9. Arrastar para Recusado (Rejeitado ou Cancelado)
    if (toColumnId === 'RECUSADO' || destino === 'REJEITADO' || destino === 'CANCELADO') {
      if (item.acoesPermitidas?.includes('REJEITAR')) {
        this.abrirRejeicao(item);
      } else {
        this.notifications.warning('Esta solicitação não pode ser rejeitada ou cancelada no estágio atual.');
      }
      return;
    }

    // Padrão amigável para outras combinações válidas
    const destinoNome = this.obterNomeSubEtapa(destino);
    this.notifications.info(`Movendo solicitação #${item.id.slice(0, 8)} para ${destinoNome}.`);
    this.abrirMiniDetalhe(item);
  }

  protected onLoadMore(columnId: string): void {
    this.navegarParaEtapa.emit(columnId);
  }

  protected onCopilotoClicked(context: KanbanCopilotoContext): void {
    const queryParams: Record<string, string> = {
      origem: 'almoxarifado.kanban',
      tipo: 'ETAPA_OPERACIONAL',
      entidade: context.columnId,
      rotulo: context.columnLabel,
      pergunta: `Analise as solicitações na macro-etapa "${context.columnLabel}" do Almoxarifado, apontando potenciais gargalos, atrasos e recomendações de ação para a equipe.`,
    };

    void this.router.navigate(['/copiloto'], { queryParams });
  }

  /**
   * Trata ações disparadas a partir dos cards do Kanban (botões rápidos ou menu de 3 pontinhos).
   * Aciona os diálogos oficiais in-place sem desviar a rota.
   */
  protected onCardAction(event: { item: SolicitacaoMaterialSummaryResponse; action: string }): void {
    if (event.action === 'INICIAR_SEPARACAO') {
      this.executarIniciarSeparacao(event.item);
      return;
    }
    if (event.action === 'CONCLUIR_SEPARACAO') {
      this.executarConcluirSeparacao(event.item);
      return;
    }
    if (event.action === 'PULAR_SEPARACAO') {
      this.executarPularSeparacao(event.item);
      return;
    }
    if (event.action === 'PLANEJAR') {
      this.abrirPlanejamento(event.item);
      return;
    }
    if (event.action === 'ENTREGAR') {
      this.abrirAcao(event.item, 'deliver');
      return;
    }
    if (event.action === 'DEVOLVER') {
      this.abrirAcao(event.item, 'return');
      return;
    }
    if (event.action === 'ENCERRAR_SALDO') {
      this.abrirAcao(event.item, 'close');
      return;
    }
    if (event.action === 'REJEITAR') {
      this.abrirRejeicao(event.item);
      return;
    }
    this.abrirMiniDetalhe(event.item);
  }

  /**
   * Trata ações solicitadas a partir do mini-detalhamento.
   * Fecha o mini-detalhe e abre o modal oficial correspondente in-place.
   */
  protected onDetailActionRequested(event: { id: string; action: string }): void {
    this.detailVisible.set(false);
    const item = this.selectedItem();

    if (event.action === 'INICIAR_SEPARACAO' && item && item.id === event.id) {
      this.executarIniciarSeparacao(item);
      return;
    }
    if (event.action === 'CONCLUIR_SEPARACAO' && item && item.id === event.id) {
      this.executarConcluirSeparacao(item);
      return;
    }
    if (event.action === 'PULAR_SEPARACAO' && item && item.id === event.id) {
      this.executarPularSeparacao(item);
      return;
    }
    if (event.action === 'PLANEJAR') {
      this.abrirPlanejamento(event.id);
      return;
    }
    if (event.action === 'ENTREGAR') {
      this.abrirAcao(event.id, 'deliver');
      return;
    }
    if (event.action === 'DEVOLVER') {
      this.abrirAcao(event.id, 'return');
      return;
    }
    if (event.action === 'ENCERRAR_SALDO') {
      this.abrirAcao(event.id, 'close');
      return;
    }
    if (event.action === 'REJEITAR') {
      if (item && item.id === event.id) {
        this.abrirRejeicao(item);
      } else {
        this.service.buscarPorId(event.id).subscribe({
          next: (fresh) => {
            const resumo = this.service['normalizarResumo']
              ? this.service['normalizarResumo'](fresh as any)
              : (fresh as any);
            this.abrirRejeicao(resumo);
          },
          error: () => this.notifications.error('Erro ao carregar dados da solicitação para rejeição.'),
        });
      }
      return;
    }

    void this.router.navigate(['/almoxarifado/solicitacoes-tecnicas', event.id]);
  }

  private executarIniciarSeparacao(item: SolicitacaoMaterialSummaryResponse): void {
    const versaoEsperada = item.versao ?? 1;
    this.service.iniciarSeparacao(item.id, { versaoEsperada }).subscribe({
      next: () => {
        this.notifications.success(`Separação da solicitação #${item.id.slice(0, 8)} iniciada com sucesso.`);
        this.recarregar();
      },
      error: (err) => {
        const msg = err?.error?.message || 'Erro ao iniciar separação. O registro pode ter sido modificado por outro usuário.';
        this.notifications.error(msg);
      },
    });
  }

  private executarConcluirSeparacao(item: SolicitacaoMaterialSummaryResponse): void {
    const versaoEsperada = item.versao ?? 1;
    this.service.concluirSeparacao(item.id, { versaoEsperada }).subscribe({
      next: () => {
        this.notifications.success(`Separação da solicitação #${item.id.slice(0, 8)} concluída com sucesso.`);
        this.recarregar();
      },
      error: (err) => {
        const msg = err?.error?.message || 'Erro ao concluir separação. O registro pode ter sido modificado por outro usuário.';
        this.notifications.error(msg);
      },
    });
  }

  private executarPularSeparacao(item: SolicitacaoMaterialSummaryResponse): void {
    const versaoEsperada = item.versao ?? 1;
    this.service.pularSeparacao(item.id, { versaoEsperada }).subscribe({
      next: () => {
        this.notifications.success(`Separação da solicitação #${item.id.slice(0, 8)} pulada com sucesso.`);
        this.recarregar();
      },
      error: (err) => {
        const msg = err?.error?.message || 'Erro ao pular separação. O registro pode ter sido modificado por outro usuário.';
        this.notifications.error(msg);
      },
    });
  }

  private isRetrocesso(origem: string, destino: string): boolean {
    const pesoOrigem = this.ETAPA_ORDEM[origem] ?? 0;
    const pesoDestino = this.ETAPA_ORDEM[destino] ?? 0;
    return pesoDestino < pesoOrigem;
  }

  private carregarPreferenciasColapso(): Record<string, boolean> {
    try {
      const salvo = localStorage.getItem(STORAGE_KEY_COLLAPSED);
      if (salvo) {
        return JSON.parse(salvo);
      }
    } catch {}
    return {
      CONCLUIDO: true,
      CANCELADO: true,
    };
  }

  private obterNomeMacroColuna(etapaId: string): string {
    const col = MACRO_COLUNAS_KANBAN.find((c) => c.etapa === etapaId);
    return col ? col.label : etapaId;
  }

  private obterNomeSubEtapa(subEtapaId: string): string {
    const map: Record<string, string> = {
      PENDENTE_PLANEJAMENTO: 'Pendente de Planejamento',
      PLANEJADO: 'Planejado',
      PRONTO_PARA_SEPARACAO: 'Pronto para Separação',
      EM_SEPARACAO: 'Em Separação',
      SEPARADO: 'Separado',
      PENDENTE_RETIRADA: 'Pendente de Retirada',
      PENDENTE_DEVOLUCAO: 'Pendente de Devolução',
      CONCLUIDO: 'Concluído',
      REJEITADO: 'Rejeitado',
      CANCELADO: 'Cancelado',
    };
    return map[subEtapaId] ?? subEtapaId;
  }
}
