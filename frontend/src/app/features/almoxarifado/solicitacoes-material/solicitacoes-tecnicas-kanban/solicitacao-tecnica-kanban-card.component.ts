import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { DragDropModule } from 'primeng/dragdrop';
import { TooltipModule } from 'primeng/tooltip';
import { MenuModule } from 'primeng/menu';
import { MenuItem } from 'primeng/api';
import { CesTone } from '@ces/ui';
import {
  AcaoPermitidaSolicitacao,
  SolicitacaoMaterialSummaryResponse,
} from '../../../../core/models/solicitacao-material-tecnica.models';
import {
  statusLabel,
  statusTone,
  tipoTecnicoBadgeClass,
  tipoTecnicoColorClass,
  tipoTecnicoEhAdicao,
  tipoTecnicoIcon,
  tipoTecnicoLabel,
} from '../solicitacao-tecnica-presentation';

@Component({
  selector: 'app-solicitacao-tecnica-kanban-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, DragDropModule, TooltipModule, MenuModule],
  template: `
    <article
      class="group relative flex flex-col gap-2 p-3 rounded-xl
             border border-slate-200 dark:border-slate-800/80
             bg-white dark:bg-[#0e1726] hover:bg-slate-50 dark:hover:bg-[#131f33]
             hover:border-slate-300 dark:hover:border-slate-700
             hover:shadow-md
             cursor-grab active:cursor-grabbing transition-all
             focus-within:ring-2 focus-within:ring-sky-500"
      pDraggable="ces-kanban-card"
      (onDragStart)="onDragStart()"
      (onDragEnd)="onDragEnd()"
      (click)="onClick($event)"
      (keydown.enter)="onClick($event)"
      (keydown.space)="onClick($event)"
      tabindex="0"
      [attr.aria-label]="ariaLabel()"
      role="button"
    >
      <!-- Linha 1: Ícone do Tipo & Badge # & ID curto & Ações -->
      <div class="flex items-center justify-between gap-1.5">
        <div class="flex items-center gap-1.5 min-w-0">
          <i
            [class]="tipoIcon() + ' text-xs shrink-0 ' + tipoIconColorClass()"
            aria-hidden="true"
          ></i>
          <span
            class="inline-flex items-center px-1.5 py-0.5 rounded text-[11px] font-bold font-mono tracking-wide border"
            [class]="tipoBadgeClass()"
          >
            #{{ tipoCodigo() }}
          </span>
          <span class="text-xs font-mono font-bold text-slate-800 dark:text-slate-100 truncate">
            #{{ idCurto() }}
          </span>
        </div>

        <div class="flex items-center gap-1 shrink-0">
          @if (item().atrasada) {
            <span
              class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-bold
                     bg-rose-100 text-rose-700 dark:bg-rose-950/80 dark:text-rose-300 border border-rose-300 dark:border-rose-800"
              pTooltip="Solicitação atrasada em relação à data prevista"
              tooltipPosition="top"
            >
              <i class="pi pi-exclamation-triangle text-[9px]" aria-hidden="true"></i>
              Atrasada
            </span>
          }

          <!-- Botões de Ação Rápida no Card -->
          @if (podePlanejar() && !podeIniciarSeparacao() && !podeConcluirSeparacao() && !podeEntregar()) {
            <button
              type="button"
              class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-sky-600 hover:bg-sky-700 text-white shadow-2xs transition-transform active:scale-95 cursor-pointer"
              (click)="dispararAcao('PLANEJAR', $event)"
              title="Planejar esta solicitação"
            >
              <i class="pi pi-pencil text-[8px]" aria-hidden="true"></i>
              Planejar
            </button>
          }

          @if (podeIniciarSeparacao()) {
            <button
              type="button"
              class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500 hover:bg-amber-600 text-white shadow-2xs transition-transform active:scale-95 cursor-pointer"
              (click)="dispararAcao('INICIAR_SEPARACAO', $event)"
              title="Iniciar separação desta solicitação"
            >
              <i class="pi pi-play text-[8px]" aria-hidden="true"></i>
              Iniciar
            </button>
          }

          @if (podeConcluirSeparacao()) {
            <button
              type="button"
              class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-600 hover:bg-emerald-700 text-white shadow-2xs transition-transform active:scale-95 cursor-pointer"
              (click)="dispararAcao('CONCLUIR_SEPARACAO', $event)"
              title="Concluir separação desta solicitação"
            >
              <i class="pi pi-check text-[8px]" aria-hidden="true"></i>
              Concluir
            </button>
          }

          @if (podeEntregar()) {
            <button
              type="button"
              class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-teal-600 hover:bg-teal-700 text-white shadow-2xs transition-transform active:scale-95 cursor-pointer"
              (click)="dispararAcao('ENTREGAR', $event)"
              title="Registrar entrega desta solicitação"
            >
              <i class="pi pi-truck text-[8px]" aria-hidden="true"></i>
              Entregar
            </button>
          }

          <!-- Menu de Ações (3 pontinhos) -->
          <button
            type="button"
            class="w-6 h-6 inline-flex items-center justify-center rounded text-slate-400
                   hover:text-slate-700 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800
                   focus-visible:outline-2 focus-visible:outline-sky-500 transition-colors cursor-pointer"
            [attr.aria-label]="'Ações da solicitação #' + idCurto()"
            (click)="abrirMenu($event)"
          >
            <i class="pi pi-ellipsis-v text-xs" aria-hidden="true"></i>
          </button>
          <p-menu #menu [model]="menuAcoes()" [popup]="true" appendTo="body" />
        </div>
      </div>

      <!-- Linha 2: Obra / Local de Destino -->
      <div class="flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-300 font-medium truncate">
        <i class="pi pi-map-marker text-[11px] text-slate-400 shrink-0" aria-hidden="true"></i>
        <span class="truncate uppercase tracking-wide text-[11px]">
          {{ nomeObraDestino() }}
        </span>
      </div>

      <!-- Linha 3: Equipe e Reserva -->
      <div class="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400 gap-2">
        <div class="flex items-center gap-1.5 truncate">
          <i class="pi pi-users text-[11px] text-slate-400 shrink-0" aria-hidden="true"></i>
          <span class="truncate text-[11px]">
            {{ item().idEquipe ? 'Equipe vinculada' : 'Sem equipe' }}
          </span>
        </div>
        @if (item().numeroReserva) {
          <div class="flex items-center gap-1 shrink-0 font-mono text-[11px] text-slate-600 dark:text-slate-300 font-semibold">
            <span class="text-slate-400 text-[10px] font-normal">Res:</span>
            <span>{{ item().numeroReserva }}</span>
          </div>
        }
      </div>

      <!-- Linha 4: Rodapé: Itens e Data Prevista -->
      <div class="flex items-center justify-between pt-2 border-t border-slate-100 dark:border-slate-800/80 text-xs text-slate-500 dark:text-slate-400">
        <div class="flex items-center gap-1.5 font-medium">
          <i class="pi pi-box text-[11px] text-slate-400 shrink-0" aria-hidden="true"></i>
          <span class="text-[11px]">{{ item().quantidadeItens }} {{ item().quantidadeItens === 1 ? 'item' : 'itens' }}</span>
        </div>

        @if (item().dataPrevista) {
          <span
            class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium"
            [class]="item().atrasada
              ? 'bg-rose-100 text-rose-700 dark:bg-rose-950/70 dark:text-rose-300 font-bold border border-rose-300 dark:border-rose-800'
              : 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/50'"
          >
            <i class="pi pi-calendar text-[10px]" aria-hidden="true"></i>
            {{ item().dataPrevista | date: 'dd/MM/yyyy' }}
          </span>
        } @else {
          <span class="inline-flex items-center gap-1 text-[11px] text-slate-400 italic">
            <i class="pi pi-clock text-[10px]" aria-hidden="true"></i>
            Sem data prev.
          </span>
        }
      </div>
    </article>
  `,
})
export class SolicitacaoTecnicaKanbanCardComponent {
  item = input.required<SolicitacaoMaterialSummaryResponse>();
  columnId = input.required<string>();
  subGroupId = input<string>();

  cardClicked = output<SolicitacaoMaterialSummaryResponse>();
  dragStarted = output<{ item: SolicitacaoMaterialSummaryResponse; columnId: string; subGroupId?: string }>();
  dragEnded = output<void>();
  actionTriggered = output<{ item: SolicitacaoMaterialSummaryResponse; action: string }>();

  protected readonly podePlanejar = computed(() =>
    this.item().acoesPermitidas?.includes('EDITAR_PLANEJAMENTO') ?? false
  );

  protected readonly podeIniciarSeparacao = computed(() =>
    this.item().acoesPermitidas?.includes('INICIAR_SEPARACAO') ?? false
  );

  protected readonly podePularSeparacao = computed(() =>
    this.item().acoesPermitidas?.includes('PULAR_SEPARACAO') ?? false
  );

  protected readonly podeConcluirSeparacao = computed(() =>
    this.item().acoesPermitidas?.includes('CONCLUIR_SEPARACAO') ?? false
  );

  protected readonly podeEntregar = computed(() =>
    this.item().acoesPermitidas?.includes('ENTREGAR') ?? false
  );

  protected readonly idCurto = computed(() => this.item().id.slice(0, 8));

  protected readonly nomeObraDestino = computed(() => {
    const it = this.item();
    if (it.estoqueDestinoNome) return it.estoqueDestinoNome;
    if (it.idObra) return `OBRA ${it.idObra.slice(0, 8)}`;
    if (it.ocorrencia?.codigo) return `OC ${it.ocorrencia.codigo}`;
    return 'SOLICITAÇÃO DE MATERIAL';
  });

  protected readonly ariaLabel = computed(() => {
    const it = this.item();
    const oc = it.ocorrencia?.codigo ? ` Ocorrência ${it.ocorrencia.codigo}.` : '';
    const status = statusLabel(it.status);
    return `Solicitação ${this.idCurto()}.${oc} Status: ${status}. Total de ${it.quantidadeItens} itens.`;
  });

  protected readonly tipoCodigo = computed(() => {
    const tipo = this.item().tipo;
    if (tipo === 'ADICAO_COMUM') return '221';
    if (tipo === 'ADICAO_CONCRETO') return '921';
    if (tipo === 'DEVOLUCAO_COMUM') return '222';
    if (tipo === 'DEVOLUCAO_CONCRETO') return '922';
    return 'TEC';
  });

  protected readonly tipoIcon = computed(() => tipoTecnicoIcon(this.item().tipo));

  protected readonly tipoIconColorClass = computed(() => tipoTecnicoColorClass(this.item().tipo));

  protected readonly tipoBadgeClass = computed(() => tipoTecnicoBadgeClass(this.item().tipo));

  protected readonly menuAcoes = computed<MenuItem[]>(() => {
    const it = this.item();
    const acoes: MenuItem[] = [
      {
        label: 'Ver mini-detalhamento',
        icon: 'pi pi-eye',
        command: () => this.cardClicked.emit(it),
      },
    ];

    if (it.acoesPermitidas?.includes('EDITAR_PLANEJAMENTO')) {
      acoes.push({
        label: 'Planejar Solicitação',
        icon: 'pi pi-pencil',
        command: () => this.actionTriggered.emit({ item: it, action: 'PLANEJAR' }),
      });
    }

    if (it.acoesPermitidas?.includes('INICIAR_SEPARACAO')) {
      acoes.push({
        label: 'Iniciar Separação',
        icon: 'pi pi-play',
        command: () => this.actionTriggered.emit({ item: it, action: 'INICIAR_SEPARACAO' }),
      });
    }

    if (it.acoesPermitidas?.includes('PULAR_SEPARACAO')) {
      acoes.push({
        label: 'Pular Separação',
        icon: 'pi pi-forward',
        command: () => this.actionTriggered.emit({ item: it, action: 'PULAR_SEPARACAO' }),
      });
    }

    if (it.acoesPermitidas?.includes('CONCLUIR_SEPARACAO')) {
      acoes.push({
        label: 'Concluir Separação',
        icon: 'pi pi-check',
        command: () => this.actionTriggered.emit({ item: it, action: 'CONCLUIR_SEPARACAO' }),
      });
    }

    if (it.acoesPermitidas?.includes('ENTREGAR')) {
      acoes.push({
        label: 'Registrar Entrega',
        icon: 'pi pi-truck',
        command: () => this.actionTriggered.emit({ item: it, action: 'ENTREGAR' }),
      });
    }

    if (it.acoesPermitidas?.includes('CONFIRMAR_DEVOLUCAO')) {
      acoes.push({
        label: 'Confirmar Devolução',
        icon: 'pi pi-undo',
        command: () => this.actionTriggered.emit({ item: it, action: 'DEVOLVER' }),
      });
    }

    if (it.acoesPermitidas?.includes('ENCERRAR_SALDO')) {
      acoes.push({
        label: 'Encerrar Saldo',
        icon: 'pi pi-lock',
        command: () => this.actionTriggered.emit({ item: it, action: 'ENCERRAR_SALDO' }),
      });
    }

    if (it.acoesPermitidas?.includes('REJEITAR')) {
      acoes.push({
        label: 'Rejeitar Solicitação',
        icon: 'pi pi-times',
        command: () => this.actionTriggered.emit({ item: it, action: 'REJEITAR' }),
      });
    }

    return acoes;
  });

  protected dispararAcao(action: string, event: Event): void {
    event.stopPropagation();
    this.actionTriggered.emit({ item: this.item(), action });
  }

  protected onClick(event: Event): void {
    // Evita abrir modal quando o clique for no menu de contexto
    const target = event.target as HTMLElement;
    if (target.closest('p-menu') || target.closest('button')) {
      return;
    }
    this.cardClicked.emit(this.item());
  }

  protected abrirMenu(event: Event): void {
    event.stopPropagation();
  }

  protected onDragStart(): void {
    this.dragStarted.emit({ item: this.item(), columnId: this.columnId(), subGroupId: this.subGroupId() });
  }

  protected onDragEnd(): void {
    this.dragEnded.emit();
  }
}
