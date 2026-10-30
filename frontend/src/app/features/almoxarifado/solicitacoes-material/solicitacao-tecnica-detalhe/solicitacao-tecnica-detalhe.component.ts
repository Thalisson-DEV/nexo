import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { Accordion, AccordionPanel, AccordionHeader, AccordionContent } from 'primeng/accordion';
import { CesAsyncStateComponent, CesButtonComponent, CesConfirmationService, CesDialogShellComponent, CesStatusComponent, CesTone, NotificationService } from '@ces/ui';
import { EstoqueResponse, MotivoRejeicaoResponse, RejeitarSolicitacaoRequest } from '../../../../core/models/almoxarifado.models';
import { ObraResponse } from '../../../../core/models/obras.models';
import { EquipeResponse } from '../../../../core/models/rh.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { AcaoPermitidaSolicitacao, AlterarSeparacaoTecnicaRequest, PlanejamentoSolicitacaoTecnicaResponse, SolicitacaoMaterialDetalheResponse } from '../../../../core/models/solicitacao-material-tecnica.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { ConnectivityService } from '../../../../core/services/connectivity.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';
import { catchError, finalize, forkJoin, of } from 'rxjs';
import { novaChaveIdempotencia } from '../../../../core/utils/idempotencia.util';
import { ehConflitoDeVersao, ehFalhaDeTransporte, problemaTecnico } from '../../../../core/utils/solicitacao-tecnica-error.util';
import { possuiMovimentacaoAtendimento, possuiSaldoPendenteDevolucao, possuiSaldoPendenteEntrega, separacaoLabel, separacaoTone, statusLabel, statusTone, tipoTecnicoEhAdicao, tipoTecnicoIcon, tipoTecnicoLabel } from '../solicitacao-tecnica-presentation';
import { SolicitacaoTecnicaAcaoComponent, TechnicalActionMode } from '../solicitacao-tecnica-acao/solicitacao-tecnica-acao.component';
import { SolicitacaoTecnicaEquipeComponent } from '../solicitacao-tecnica-equipe/solicitacao-tecnica-equipe.component';
import { SolicitacaoTecnicaHistoricoComponent } from '../solicitacao-tecnica-historico/solicitacao-tecnica-historico.component';
import { SolicitacaoTecnicaPlanejamentoComponent } from '../solicitacao-tecnica-planejamento/solicitacao-tecnica-planejamento.component';
import { NavigationHistoryService } from '../../../../core/navigation/navigation-history.service';
import { SolicitacaoPendenciasComponent } from '../solicitacao-pendencias.component';
import { SolicitacaoPendenciaModalComponent } from '../solicitacao-pendencia-modal.component';
import { MotivoPendenciaResponse } from '../../../../core/models/almoxarifado.models';

export interface WorkflowStage {
  number: number;
  label: string;
  description: string;
  icon: string;
  state: 'completed' | 'current' | 'waiting';
  badge?: string;
  tone?: CesTone;
}

interface PendingSeparation { kind: 'start' | 'finish' | 'skip'; request: AlterarSeparacaoTecnicaRequest; key: string; }

@Component({ selector: 'app-solicitacao-tecnica-detalhe', standalone: true, imports: [CommonModule, FormsModule, RouterLink, ButtonModule, TooltipModule, SelectModule, TextareaModule, Accordion, AccordionPanel, AccordionHeader, AccordionContent, CesAsyncStateComponent, CesButtonComponent, CesDialogShellComponent, CesStatusComponent, SolicitacaoTecnicaAcaoComponent, SolicitacaoTecnicaEquipeComponent, SolicitacaoTecnicaHistoricoComponent, SolicitacaoTecnicaPlanejamentoComponent, SolicitacaoPendenciasComponent, SolicitacaoPendenciaModalComponent], templateUrl: './solicitacao-tecnica-detalhe.component.html', styleUrl: './solicitacao-tecnica-detalhe.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class SolicitacaoTecnicaDetalheComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute); private readonly router = inject(Router); private readonly navegacao = inject(NavigationHistoryService); private readonly service = inject(SolicitacaoMaterialTecnicaService); private readonly obrasService = inject(ObrasService); private readonly almoxarifadoService = inject(AlmoxarifadoService); private readonly rhService = inject(RhService); private readonly connectivity = inject(ConnectivityService); private readonly notifications = inject(NotificationService); private readonly confirmations = inject(CesConfirmationService); private readonly userLookup = inject(UsuarioLookupService); private readonly cdr = inject(ChangeDetectorRef);
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading'); protected readonly isOnline = this.connectivity.isOnline; protected readonly detail = signal<SolicitacaoMaterialDetalheResponse | null>(null); protected readonly obraInfo = signal<ObraResponse | null>(null); protected readonly warehouses = signal<EstoqueResponse[]>([]); protected readonly teams = signal<EquipeResponse[]>([]); protected readonly dataEmissao = new Date(); protected readonly selectedAction = signal<TechnicalActionMode | null>(null); protected readonly actionVisible = signal(false); protected readonly planningVisible = signal(false); protected readonly teamVisible = signal(false); protected readonly pendingSeparation = signal<PendingSeparation | null>(null); protected readonly commandLoading = signal(false);  protected readonly conflictVisible = signal(false);
  protected readonly displayRejectModal = signal(false);
  protected readonly motivosRejeicaoOptions = signal<MotivoRejeicaoResponse[]>([]);
  protected readonly rejectionData = { idMotivoRejeicao: '', justificativa: '' };
  protected readonly rejectLoading = signal(false);
  protected readonly conflictIntent = signal('');
  protected readonly conflictVersions = signal('');
  protected readonly timelineValue = signal<string | number | string[] | number[] | null | undefined>(undefined);
  protected readonly operationsOpen = signal(false);
  protected readonly expandedOperations = signal<string[]>([]);
  private printScheduled = false;
  protected readonly assinaturaAlmoxUrl = signal<string | null>(null);
  protected readonly assinaturaEquipeUrl = signal<string | null>(null);
  protected readonly responsavelSaqueNome = signal('Responsável pelo Saque');

  protected readonly isTimelineOpen = computed(() => {
    const val = this.timelineValue();
    if (!val) return false;
    return val === 'timeline' || (Array.isArray(val) && (val as any[]).includes('timeline'));
  });
  protected readonly technical = computed(() => this.detail()?.tecnico as any);
  protected readonly hasDeliveryMovement = computed(() => possuiMovimentacaoAtendimento(this.detail()));
  protected readonly hasPendingDelivery = computed(() => possuiSaldoPendenteEntrega(this.detail()));
  protected readonly hasPendingReturn = computed(() => possuiSaldoPendenteDevolucao(this.detail()));
  protected readonly isInitialPlanning = computed(() => {
    const tech = this.technical();
    if (!tech) return false;
    return tech.etapaOperacional
      ? tech.etapaOperacional === 'AGUARDANDO_PLANEJAMENTO'
      : !tech.dataPrevista || tech.statusSeparacao === 'AGUARDANDO_AGENDAMENTO';
  });
  protected readonly canPlanning = computed(() => {
    return this.can('EDITAR_PLANEJAMENTO') && !this.hasDeliveryMovement();
  });
  protected readonly canQuickAssignTeam = computed(() => {
    return this.canPlanning();
  });
  protected readonly warehouseName = computed(() => {
    const id = this.technical()?.idAlmoxarifadoAtendimento;
    if (!id) return 'Não informado';
    return this.warehouses().find((w) => w.id === id)?.nomeIdentificador ?? id.slice(0, 8);
  });
  protected readonly teamName = computed(() => {
    const id = this.technical()?.idEquipe;
    if (!id) return 'Não definida';
    const found = this.teams().find((team) => team.id === id);
    return found ? (found.tipoEquipe ? `${found.codigo} · ${found.tipoEquipe.nome}` : found.codigo) : id.slice(0, 8);
  });
  protected readonly plannedWithdrawalName = computed(() => {
    const id = this.technical()?.idColaboradorSaque;
    return id ? this.formatUser(id) : 'Não definido';
  });
  protected readonly obraDisplay = computed(() => {
    const obra = this.obraInfo();
    if (obra) return `${obra.numero} — ${obra.titulo}`;
    const id = this.technical()?.idObra;
    return id ? `${id.slice(0, 8)}` : 'Obra não informada';
  });

  protected readonly operationsSummary = computed(() => {
    const ops = this.technical()?.operacoes ?? [];
    if (!ops.length) return 'Nenhuma movimentação realizada ainda';
    const entregas = ops.filter((o: any) => o.tipo === 'ENTREGA').length;
    const devolucoes = ops.filter((o: any) => o.tipo === 'DEVOLUCAO').length;
    const estornos = ops.filter((o: any) => o.tipo === 'ESTORNO').length;
    const parts: string[] = [];
    if (entregas) parts.push(`${entregas} ${entregas > 1 ? 'entregas realizadas' : 'entrega realizada'}`);
    if (devolucoes) parts.push(`${devolucoes} ${devolucoes > 1 ? 'devoluções ao estoque' : 'devolução ao estoque'}`);
    if (estornos) parts.push(`${estornos} ${estornos > 1 ? 'correções / estornos' : 'correção / estorno'}`);
    return parts.join(' · ');
  });

  protected readonly fiscalName = computed(() => {
    const tech = this.technical();
    if (tech?.reserva?.nomeAgente) return tech.reserva.nomeAgente;
    const obra = this.obraInfo();
    if (obra?.agenteResponsavelNome) return obra.agenteResponsavelNome;
    return 'Fiscal Responsável';
  });

  protected readonly delivererName = computed(() => {
    const tech = this.technical();
    const ops = tech?.operacoes ?? [];
    const entrega = [...ops].reverse().find((operation: any) => operation.tipo === 'ENTREGA');
    if (entrega?.idUsuarioOperador) {
      return this.formatUser(entrega.idUsuarioOperador);
    }
    const detail = this.detail();
    if (detail?.idEntregadorAlmox) {
      return this.formatUser(detail.idEntregadorAlmox);
    }
    if (detail?.idRequisitante) {
      return this.formatUser(detail.idRequisitante);
    }
    return 'Almoxarife Responsável';
  });

  protected readonly dataEntrega = computed(() => {
    const detail = this.detail();
    if (detail?.dataEntrega) return detail.dataEntrega;
    const ops = this.technical()?.operacoes ?? [];
    const entrega = [...ops].reverse().find((operation: any) => operation.tipo === 'ENTREGA');
    return entrega?.realizadaEm ?? null;
  });

  protected toggleOperations(): void {
    this.operationsOpen.update((open) => !open);
  }

  protected toggleOperation(opId: string): void {
    this.expandedOperations.update((list) =>
      list.includes(opId) ? list.filter((id) => id !== opId) : [...list, opId],
    );
  }

  protected isOperationExpanded(opId: string): boolean {
    return this.expandedOperations().includes(opId);
  }

  protected expandAllOperations(): void {
    const ops = this.technical()?.operacoes ?? [];
    this.expandedOperations.set(ops.map((o: any) => o.id));
  }

  protected collapseAllOperations(): void {
    this.expandedOperations.set([]);
  }

  protected isEncerramentoOp(tipo?: string | null): boolean {
    const t = tipo?.toUpperCase() || '';
    return t.includes('ENCERRA') || t.includes('CANCEL');
  }

  protected operationFriendlyTitle(tipo: string): string {
    const t = tipo?.toUpperCase() || '';
    if (t === 'ENTREGA') return 'Entrega de Materiais';
    if (t === 'DEVOLUCAO') return 'Devolução de Materiais ao Almoxarifado';
    if (t === 'ESTORNO') return 'Correção / Estorno de Movimentação';
    if (t.includes('ENCERRA')) return 'Encerramento de Saldo Remanescente';
    if (t.includes('CANCEL')) return 'Cancelamento da Solicitação';
    return tipo;
  }

  protected operationIcon(tipo: string): string {
    const t = tipo?.toUpperCase() || '';
    if (t === 'ENTREGA') return 'pi pi-box';
    if (t === 'DEVOLUCAO') return 'pi pi-inbox';
    if (t === 'ESTORNO') return 'pi pi-replay';
    if (t.includes('ENCERRA')) return 'pi pi-lock';
    if (t.includes('CANCEL')) return 'pi pi-times-circle';
    return 'pi pi-info-circle';
  }

  protected getTeamName(idEquipe?: string | null): string {
    if (!idEquipe) return 'Não definida';
    const found = this.teams().find((t) => t.id === idEquipe);
    return found ? (found.tipoEquipe ? `${found.codigo} · ${found.tipoEquipe.nome}` : found.codigo) : idEquipe.slice(0, 8);
  }
  ngOnInit(): void {
    this.printScheduled = this.route.snapshot?.queryParamMap?.get('mode') === 'print';
    this.loadLookups();
    const initialId = this.route.snapshot?.paramMap?.get('id') ?? '';
    if (initialId) {
      this.load(initialId);
    }
    this.route.paramMap?.subscribe((params) => {
      const id = params.get('id');
      if (id && id !== initialId) {
        this.load(id);
      }
    });
  }
  ngOnDestroy(): void { this.revokeSignatureUrls(); }
  protected reload(): void { this.load(this.detail()?.id ?? this.route.snapshot.paramMap.get('id') ?? ''); }
  protected print(): void { window.print(); }
  protected voltar(): void { this.navegacao.back(['/almoxarifado/solicitacoes-tecnicas']); }
  protected can(action: AcaoPermitidaSolicitacao): boolean {
    const allowedActions = this.technical()?.acoesPermitidas ?? this.detail()?.acoesPermitidas ?? [];
    return allowedActions.includes(action);
  }
  protected openAction(mode: TechnicalActionMode): void {
    const action = mode === 'reserve'
      ? (this.can('ALTERAR_RESERVA') ? 'ALTERAR_RESERVA' : 'REGISTRAR_RESERVA')
      : mode === 'deliver' ? 'ENTREGAR'
        : mode === 'return' ? 'CONFIRMAR_DEVOLUCAO'
          : mode === 'reverse' ? 'ESTORNAR' : 'ENCERRAR_SALDO';
    if (this.isOnline() && this.can(action)) {
      const currentDetail = this.detail();
      if (currentDetail?.id) {
        this.service.buscarPorId(currentDetail.id).subscribe({
          next: (fresh) => {
            this.detail.set(fresh);
            this.selectedAction.set(mode);
            this.actionVisible.set(true);
            this.cdr.markForCheck();
          },
          error: () => {
            this.selectedAction.set(mode);
            this.actionVisible.set(true);
            this.cdr.markForCheck();
          },
        });
        return;
      }
      this.selectedAction.set(mode);
      this.actionVisible.set(true);
      this.cdr.markForCheck();
    }
  }
  protected openPlanning(): void { if (this.isOnline() && this.canPlanning()) { this.planningVisible.set(true); this.cdr.markForCheck(); } }
  protected openTeamAssignment(): void { if (this.isOnline() && this.canQuickAssignTeam()) { this.teamVisible.set(true); this.cdr.markForCheck(); } }
  protected canRegistrarPendencia(): boolean {
    const detail = this.detail();
    if (!detail) return false;
    return (
      this.isOnline() &&
      !detail.encerrada &&
      detail.status !== 'REJEITADO' &&
      detail.status !== 'CANCELADO' &&
      detail.status !== 'ENTREGUE'
    );
  }

  protected pendenciaModalVisible = signal(false);
  protected pendenciaModalItemId = signal<string | null>(null);
  protected pendenciaModalItemLabel = signal<string | null>(null);
  protected pendenciaModalMotivoAtual = signal<MotivoPendenciaResponse | null>(null);
  protected pendenciaModalJustificativaAtual = signal<string | null>(null);

  protected abrirModalPendenciaSolicitacao(): void {
    const detail = this.detail();
    if (!detail) return;
    this.pendenciaModalItemId.set(null);
    this.pendenciaModalItemLabel.set(null);
    this.pendenciaModalMotivoAtual.set(detail.motivoPendencia ?? null);
    this.pendenciaModalJustificativaAtual.set(detail.justificativaPendencia ?? null);
    this.pendenciaModalVisible.set(true);
  }

  protected abrirModalPendenciaItem(item: any): void {
    const detail = this.detail();
    if (!detail || !item?.id) return;
    this.pendenciaModalItemId.set(item.id);
    this.pendenciaModalItemLabel.set(`${item.material?.codigo ?? item.codigo} — ${item.material?.descricao ?? item.descricao}`);
    this.pendenciaModalMotivoAtual.set(item.motivoPendencia ?? null);
    this.pendenciaModalJustificativaAtual.set(item.justificativaPendencia ?? null);
    this.pendenciaModalVisible.set(true);
  }

  protected rejectSolicitacao(): void {
    this.rejectionData.idMotivoRejeicao = '';
    this.rejectionData.justificativa = '';
    this.almoxarifadoService.listarMotivosRejeicaoAtivos().subscribe({
      next: (res) => {
        this.motivosRejeicaoOptions.set(res.content ?? []);
        this.displayRejectModal.set(true);
        this.cdr.markForCheck();
      },
      error: () => {
        this.notifications.error('Não foi possível carregar os motivos de rejeição.');
      },
    });
  }
  protected confirmRejection(): void {
    if (
      !this.rejectionData.idMotivoRejeicao ||
      !this.rejectionData.justificativa ||
      this.rejectionData.justificativa.trim().length < 5
    ) {
      this.notifications.warning('Motivo e descrição/justificativa (mínimo de 5 caracteres) são obrigatórios.');
      return;
    }
    const currentDetail = this.detail();
    if (!currentDetail?.id) return;
    const request: RejeitarSolicitacaoRequest = {
      idMotivoRejeicao: this.rejectionData.idMotivoRejeicao,
      justificativa: this.rejectionData.justificativa.trim(),
    };
    this.rejectLoading.set(true);
    this.almoxarifadoService.rejeitarSolicitacao(currentDetail.id, request).subscribe({
      next: () => {
        this.notifications.success('Solicitação técnica rejeitada com sucesso.');
        this.displayRejectModal.set(false);
        this.rejectLoading.set(false);
        this.reload();
      },
      error: (err) => {
        this.rejectLoading.set(false);
        this.notifications.error(getErrorMessage(err), { title: 'Erro ao rejeitar' });
        this.cdr.markForCheck();
      },
    });
  }
  protected readonly stages = computed<WorkflowStage[]>(() => {
    const detail = this.detail();
    const tech = this.technical();
    if (!detail || !tech) return [];

    const isAdicao = tech.tipoTecnico?.startsWith('ADICAO');
    const isDevolucao = tech.tipoTecnico?.startsWith('DEVOLUCAO');
    const hasData = !!tech.dataPrevista;
    const hasEquipe = !!tech.idEquipe;
    const hasColab = !!tech.idColaboradorSaque;
    const sepStatus = tech.statusSeparacao;
    const solStatus = detail.status;
    const isEncerrada = !!tech.encerradaEm;

    // Etapa 1: Criação
    const stage1: WorkflowStage = {
      number: 1,
      label: 'Criação',
      description: 'Registrada no sistema',
      icon: 'pi pi-file-plus',
      state: 'completed',
      badge: 'Concluída',
      tone: 'success',
    };

    // Etapa 2: Planejamento
    let stage2State: WorkflowStage['state'] = 'completed';
    let stage2Desc = 'Planejamento com data de saque';
    let stage2Badge = 'Planejado';
    let stage2Tone: CesTone = 'success';

    if (isDevolucao) {
      stage2State = 'completed';
      stage2Desc = 'Dispensado para devoluções';
      stage2Badge = 'Dispensado';
      stage2Tone = 'neutral';
    } else if (!hasData) {
      stage2State = 'current';
      stage2Desc = 'Aguardando data prevista de saque';
      stage2Badge = 'Pendente';
      stage2Tone = 'warning';
    } else if (!hasEquipe || !hasColab) {
      stage2Desc = 'Data agendada (saque a definir)';
      stage2Badge = 'Parcial';
      stage2Tone = 'info';
    }

    const stage2: WorkflowStage = {
      number: 2,
      label: 'Planejamento',
      description: stage2Desc,
      icon: 'pi pi-calendar-clock',
      state: stage2State,
      badge: stage2Badge,
      tone: stage2Tone,
    };

    // Etapa 3: Separação
    let stage3State: WorkflowStage['state'] = 'waiting';
    let stage3Label = isAdicao ? 'Separação' : 'Triagem';
    let stage3Desc = 'Aguardando agendamento';
    let stage3Badge = 'Aguardando';
    let stage3Tone: CesTone = 'neutral';

    if (isAdicao) {
      if (!hasData) {
        stage3State = 'waiting';
        stage3Desc = 'Necessita da data prevista de saque';
      } else if (sepStatus === 'PRONTO_PARA_SEPARACAO') {
        stage3State = 'current';
        stage3Desc = 'Pronto para separar no depósito';
        stage3Badge = 'Pronto';
        stage3Tone = 'brand';
      } else if (sepStatus === 'EM_SEPARACAO') {
        stage3State = 'current';
        stage3Desc = 'Conferência em andamento';
        stage3Badge = 'Em separação';
        stage3Tone = 'warning';
      } else if (sepStatus === 'SEPARADO') {
        stage3State = 'completed';
        stage3Desc = 'Separado / liberado para saque';
        stage3Badge = 'Separado';
        stage3Tone = 'success';
      }
    } else {
      stage3State = 'completed';
      stage3Desc = 'Devolução direta ao depósito';
      stage3Badge = 'Dispensada';
      stage3Tone = 'neutral';
    }

    const stage3: WorkflowStage = {
      number: 3,
      label: stage3Label,
      description: stage3Desc,
      icon: 'pi pi-box',
      state: stage3State,
      badge: stage3Badge,
      tone: stage3Tone,
    };

    // Etapa 4: Entrega / Recebimento
    let stage4Label = isAdicao ? 'Entrega' : 'Recebimento';
    let stage4State: WorkflowStage['state'] = 'waiting';
    let stage4Desc = 'Aguardando separação';
    let stage4Badge = 'Aguardando';
    let stage4Tone: CesTone = 'neutral';

    if (solStatus === 'REJEITADO') {
      stage4State = 'completed';
      stage4Label = 'Rejeição';
      stage4Desc = 'Solicitação técnica rejeitada';
      stage4Badge = 'Rejeitada';
      stage4Tone = 'danger';
    } else if (isEncerrada) {
      stage4State = 'completed';
      stage4Desc = 'Saldo pendente encerrado';
      stage4Badge = 'Encerrado';
      stage4Tone = 'neutral';
    } else if (solStatus === 'ENTREGUE' || solStatus === 'DEVOLVIDO') {
      stage4State = 'completed';
      stage4Desc = isAdicao ? 'Materiais 100% entregues' : 'Materiais 100% recebidos';
      stage4Badge = 'Concluído';
      stage4Tone = 'success';
    } else if (solStatus === 'ENTREGUE_PARCIAL' || solStatus === 'DEVOLVIDO_PARCIAL') {
      stage4State = 'current';
      stage4Desc = 'Movimentação parcial com saldo pendente';
      stage4Badge = 'Parcial';
      stage4Tone = 'warning';
    } else if (isAdicao && sepStatus === 'SEPARADO') {
      stage4State = 'current';
      stage4Desc = 'Pronto para confirmação de retirada';
      stage4Badge = 'Pronto';
      stage4Tone = 'brand';
    } else if (isDevolucao) {
      stage4State = 'current';
      stage4Desc = 'Pronto para recebimento físico';
      stage4Badge = 'Pronto';
      stage4Tone = 'brand';
    }

    const stage4: WorkflowStage = {
      number: 4,
      label: stage4Label,
      description: stage4Desc,
      icon: solStatus === 'REJEITADO' ? 'pi pi-times-circle' : isAdicao ? 'pi pi-truck' : 'pi pi-inbox',
      state: stage4State,
      badge: stage4Badge,
      tone: stage4Tone,
    };

    return [stage1, stage2, stage3, stage4];
  });

  protected async separation(kind: 'start' | 'finish' | 'skip', retry = false): Promise<void> {
    const detail = this.detail();
    const tech = this.technical();
    if (!detail || !tech || !tech.tipoTecnico.startsWith('ADICAO') || !this.isOnline()) return;
    if (kind === 'start' && !this.can('INICIAR_SEPARACAO')) return;
    if (kind === 'finish' && !this.can('CONCLUIR_SEPARACAO')) return;
    if (kind === 'skip' && !this.can('PULAR_SEPARACAO')) return;

    const pending = retry ? this.pendingSeparation() : null;
    if (!retry) {
      let title = 'Iniciar separação no almoxarifado?';
      let message = 'Deseja iniciar a separação dos materiais? O status passará para "Em separação" para conferência no depósito.';
      let confirmLabel = 'Iniciar separação';

      if (kind === 'finish') {
        title = 'Concluir separação de materiais?';
        message = 'Confirma que os materiais foram separados no depósito? O status passará para "Separado", liberando a entrega.';
        confirmLabel = 'Concluir separação';
      } else if (kind === 'skip') {
        title = 'Pular separação física?';
        message = 'Deseja dispensar a separação prévia no depósito? A solicitação avançará direto para "Separado", liberando a entrega imediata à equipe.';
        confirmLabel = 'Pular para entrega';
      }

      const confirmed = await this.confirmations.confirm({
        title,
        message,
        confirmLabel,
      });
      if (!confirmed) return;
    }

    const command = pending ?? {
      kind,
      request: { versaoEsperada: tech.versao },
      key: novaChaveIdempotencia(),
    };
    this.pendingSeparation.set(command);
    this.commandLoading.set(true);

    const call =
      kind === 'start'
        ? this.service.iniciarSeparacao(detail.id, command.request, command.key)
        : kind === 'finish'
          ? this.service.concluirSeparacao(detail.id, command.request, command.key)
          : this.service.pularSeparacao(detail.id, command.request, command.key);

    call.subscribe({
      next: () => {
        this.commandLoading.set(false);
        this.pendingSeparation.set(null);
        const msg =
          kind === 'start'
            ? 'Separação iniciada com sucesso.'
            : kind === 'finish'
              ? 'Separação concluída com sucesso.'
              : 'Separação dispensada. Solicitação pronta para entrega.';
        this.notifications.success(msg);
        this.reload();
      },
      error: (error) => {
        this.commandLoading.set(false);
        if (ehConflitoDeVersao(error)) {
          this.pendingSeparation.set(null);
          this.openConflict(
            kind === 'start' ? 'Iniciar separação' : kind === 'finish' ? 'Concluir separação' : 'Pular separação',
            problemaTecnico(error),
          );
          return;
        }
        if (!ehFalhaDeTransporte(error)) this.pendingSeparation.set(null);
        this.notifications.error(getErrorMessage(error), {
          title: ehFalhaDeTransporte(error) ? 'Resposta não confirmada' : 'Separação não atualizada',
        });
      },
    });
  }
  protected discardSeparationRetry(): void { this.pendingSeparation.set(null); }
  protected actionCompleted(event: { message: string; idOperation?: string }): void { this.notifications.success(event.idOperation ? `${event.message} Operação ${event.idOperation.slice(0, 8)}.` : event.message); this.reload(); }
  protected actionConflict(event: { problem: ReturnType<typeof problemaTecnico>; intent: string }): void { this.openConflict(event.intent, event.problem); }
  protected planningCompleted(response: PlanejamentoSolicitacaoTecnicaResponse): void { this.detail.set(response.solicitacaoAtualizada); this.planningVisible.set(false); this.reload(); }
  protected teamCompleted(response: PlanejamentoSolicitacaoTecnicaResponse): void { this.detail.set(response.solicitacaoAtualizada); this.teamVisible.set(false); this.reload(); }
  protected planningConflict(event: { intent: string; currentVersion?: number }): void { this.openConflict(event.intent, event.currentVersion === undefined ? null : { versaoAtual: event.currentVersion }); }
  protected reconcile(): void { this.conflictVisible.set(false); this.reload(); }
  protected statusLabel = statusLabel; protected statusTone = statusTone; protected separacaoLabel = separacaoLabel; protected separacaoTone = separacaoTone; protected typeLabel = tipoTecnicoLabel; protected typeIcon = tipoTecnicoIcon; protected typeIsAdicao = tipoTecnicoEhAdicao;
  protected formatUser(idOuAutor?: string | { id?: string; nome?: string } | null): string { return this.userLookup.resolverNome(idOuAutor, 'Não informado'); }
  protected toneForOperation(type: string): CesTone { return type === 'ESTORNO' ? 'warning' : type === 'ENTREGA' ? 'success' : 'info'; }
  protected number(value: number | undefined | null): string { return (value ?? 0).toLocaleString('pt-BR', { minimumFractionDigits: 3, maximumFractionDigits: 3 }); }
  private load(id: string): void { if (!id) { this.state.set('error'); this.cdr.markForCheck(); return; } this.state.set('loading'); this.cdr.markForCheck(); this.service.buscarPorId(id).subscribe({ next: (detail) => { const technical = detail.tecnico; if (!technical) { this.state.set('error'); this.cdr.markForCheck(); return; } this.detail.set(detail); this.obrasService.buscarObraPorId(technical.idObra).subscribe({ next: (obra) => { this.obraInfo.set(obra); this.cdr.markForCheck(); }, error: () => { this.obraInfo.set(null); this.cdr.markForCheck(); } }); this.resolveWithdrawalResponsible(); this.loadReportSignatures(id); this.state.set('ready'); this.cdr.markForCheck(); }, error: (error) => { this.state.set('error'); this.cdr.markForCheck(); this.notifications.error(getErrorMessage(error)); } }); }
  private loadLookups(): void { this.almoxarifadoService.listarEstoquesAtivos().subscribe({ next: (res) => { this.warehouses.set(res.content ?? []); this.cdr.markForCheck(); }, error: () => {} }); this.rhService.listarEquipes({ ativo: true, size: 500 }).subscribe({ next: (res) => { this.teams.set(res.content ?? []); this.cdr.markForCheck(); }, error: () => {} }); }
  private openConflict(intent: string, problem: { versaoEsperada?: number; versaoAtual?: number } | null): void { this.conflictIntent.set(intent); this.conflictVersions.set(problem?.versaoAtual === undefined ? '' : `Versão esperada: ${problem.versaoEsperada ?? '—'} · Versão atual no servidor: ${problem.versaoAtual}.`); this.conflictVisible.set(true); }
  private printWhenReady(): void { if (!this.printScheduled) return; this.printScheduled = false; window.setTimeout(() => window.print(), 300); }

  private resolveWithdrawalResponsible(): void {
    const tech = this.technical();
    const entrega = [...(tech?.operacoes ?? [])]
      .reverse()
      .find((operation: any) => operation.tipo === 'ENTREGA' && operation.idColaboradorSaque);
    const id = entrega?.idColaboradorSaque ?? tech?.idColaboradorSaque;
    if (!id) {
      this.responsavelSaqueNome.set('Responsável pelo Saque');
      return;
    }
    if (typeof (this.rhService as any).buscarColaboradorPorId !== 'function') {
      this.responsavelSaqueNome.set(id.slice(0, 8));
      return;
    }
    this.rhService.buscarColaboradorPorId(id).subscribe({
      next: (colaborador) => {
        this.responsavelSaqueNome.set(colaborador.nomeCompleto);
        this.cdr.markForCheck();
      },
      error: () => this.responsavelSaqueNome.set(id.slice(0, 8)),
    });
  }

  private loadReportSignatures(id: string): void {
    this.revokeSignatureUrls();
    if (typeof (this.almoxarifadoService as any).buscarAssinaturaSolicitacao !== 'function') {
      this.printWhenReady();
      return;
    }
    forkJoin({
      almox: this.almoxarifadoService.buscarAssinaturaSolicitacao(id, 'ALMOX').pipe(catchError(() => of(null))),
      equipe: this.almoxarifadoService.buscarAssinaturaSolicitacao(id, 'EQUIPE').pipe(catchError(() => of(null))),
    }).pipe(finalize(() => this.printWhenReady())).subscribe(({ almox, equipe }) => {
      if (almox?.size) this.assinaturaAlmoxUrl.set(URL.createObjectURL(almox));
      if (equipe?.size) this.assinaturaEquipeUrl.set(URL.createObjectURL(equipe));
      this.cdr.markForCheck();
    });
  }

  private revokeSignatureUrls(): void {
    for (const url of [this.assinaturaAlmoxUrl(), this.assinaturaEquipeUrl()]) {
      if (url) URL.revokeObjectURL(url);
    }
    this.assinaturaAlmoxUrl.set(null);
    this.assinaturaEquipeUrl.set(null);
  }
}
