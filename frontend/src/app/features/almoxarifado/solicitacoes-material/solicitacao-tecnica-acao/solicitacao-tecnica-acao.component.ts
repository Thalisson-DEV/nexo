import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  ElementRef,
  OnChanges,
  OnInit,
  SimpleChanges,
  ViewChild,
  computed,
  inject,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { StepperModule } from 'primeng/stepper';
import { CesDialogShellComponent, CesDialogSize, CesStatusComponent, NotificationService } from '@ces/ui';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import {
  AvisoSaldoNegativoEquipeComponent,
  SaldoNegativoMaterial,
  agruparSaldosNegativos,
} from '../../shared/saldo-negativo-equipe';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { MotivoPendenciaResponse } from '../../../../core/models/almoxarifado.models';
import { SolicitacaoPendenciaModalComponent } from '../solicitacao-pendencia-modal.component';
import { ObraResponse } from '../../../../core/models/obras.models';
import { ColaboradorResponse, EquipeMembroResponse, EquipeResponse } from '../../../../core/models/rh.models';
import {
  ConfirmarDevolucaoTecnicaRequest,
  ConfirmarEntregaTecnicaRequest,
  EncerrarSaldoTecnicoRequest,
  EstornarOperacaoTecnicaRequest,
  ItemOperacaoTecnicaResponse,
  OperacaoSolicitacaoTecnicaResponse,
  RegistrarReservaTecnicaRequest,
  SolicitacaoMaterialDetalheResponse,
} from '../../../../core/models/solicitacao-material-tecnica.models';
import { novaChaveIdempotencia } from '../../../../core/utils/idempotencia.util';
import {
  SolicitacaoTecnicaProblem,
  ehConflitoDeVersao,
  ehFalhaDeTransporte,
  errosDeCampo,
  humanizarErrosDeCampo,
  problemaTecnico,
} from '../../../../core/utils/solicitacao-tecnica-error.util';
import { getErrorMessage } from '../../../../core/models/shared.models';
import {
  possuiMovimentacaoAtendimento,
  separacaoLabel,
  separacaoTagClass,
  separacaoTone,
  statusLabel,
  statusTagClass,
  statusTone,
  tipoTecnicoEhAdicao,
  tipoTecnicoIcon,
  tipoTecnicoLabel,
} from '../solicitacao-tecnica-presentation';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { RemoteLookup as CursorRemoteLookup } from '../../../../core/lookup/store/remote-lookup';
import { CesAutocompleteComponent } from '@ces/ui/forms';

export type TechnicalActionMode = 'reserve' | 'deliver' | 'return' | 'reverse' | 'close';

type TechnicalAllowedAction =
  | 'REGISTRAR_RESERVA'
  | 'ALTERAR_RESERVA'
  | 'ENTREGAR'
  | 'CONFIRMAR_DEVOLUCAO'
  | 'ESTORNAR'
  | 'ENCERRAR_SALDO';

interface ActionLine {
  uid: string;
  idItemSolicitacao?: string;
  idItemOperacaoOriginal?: string;
  idMaterial?: string;
  label: string;
  max: number;
  quantity: number | null;
  serialRequired: boolean;
  numeroSerie: string;
  condition: 'NOVO' | 'SUCATA';
  motivoPendencia?: MotivoPendenciaResponse | null;
  justificativaPendencia?: string | null;
}

type DeliveryMode = 'all' | 'partial';

type ActionRequest =
  | RegistrarReservaTecnicaRequest
  | ConfirmarEntregaTecnicaRequest
  | ConfirmarDevolucaoTecnicaRequest
  | EstornarOperacaoTecnicaRequest
  | EncerrarSaldoTecnicoRequest;

@Component({
  selector: 'app-solicitacao-tecnica-acao',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    SelectModule,
    InputNumberModule,
    TableModule,
    TooltipModule,
    InputTextModule,
    TextareaModule,
    DatePickerModule,
    DialogModule,
    StepperModule,
    CesDialogShellComponent,
    CesAutocompleteComponent,
    SolicitacaoPendenciaModalComponent,
    CesStatusComponent,
    AvisoSaldoNegativoEquipeComponent,
  ],
  templateUrl: './solicitacao-tecnica-acao.component.html',
  styleUrl: './solicitacao-tecnica-acao.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitacaoTecnicaAcaoComponent implements OnInit, OnChanges {
  private readonly service = inject(SolicitacaoMaterialTecnicaService);
  private readonly obrasService = inject(ObrasService);
  private readonly rhService = inject(RhService);
  private readonly almoxarifadoService = inject(AlmoxarifadoService);
  private readonly notifications = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly lookupStore = inject(LookupStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly withdrawalLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () => {
      const id = this.withdrawalCollaboratorId();
      return id ? [id] : [];
    },
    destroyRef: this.destroyRef,
  });

  readonly visible = model(false);
  readonly detail = input<SolicitacaoMaterialDetalheResponse | null>(null);
  readonly mode = input<TechnicalActionMode>('reserve');
  readonly completed = output<{ message: string; idOperation?: string }>();
  readonly conflict = output<{ problem: SolicitacaoTecnicaProblem; intent: string }>();

  protected readonly activeStep = signal<number>(1);
  protected readonly submitting = signal(false);
  protected readonly retryRequest = signal<ActionRequest | null>(null);
  protected readonly retryKey = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});
  protected readonly lines = signal<ActionLine[]>([]);
  protected readonly agents = signal<Array<{ label: string; value: string }>>([]);
  protected readonly agentsLoading = signal(false);
  protected readonly agentsLoadError = signal('');
  protected readonly obraInfo = signal<ObraResponse | null>(null);
  protected readonly reserveNumber = signal('');
  protected readonly reserveDate = signal<Date | null>(null);
  protected readonly agentId = signal<string | null>(null);
  protected readonly justification = signal('');
  protected readonly reason = signal('');
  protected readonly originalOperationId = signal<string | null>(null);
  protected readonly deliveryMode = signal<DeliveryMode>('partial');
  protected readonly signatureWarehouse = signal('FISICA');
  protected readonly signatureTeam = signal('FISICA');
  protected readonly signatureType = signal<'FISICA' | 'DIGITAL'>('FISICA');
  protected readonly displaySignatureTypeModal = signal(false);
  protected readonly displayDigitalSignatureDashboard = signal(false);
  protected readonly displaySignatureModal = signal(false);
  protected readonly currentSignatureField = signal<'almox' | 'equipe'>('almox');
  @ViewChild('signatureCanvas') signatureCanvas?: ElementRef<HTMLCanvasElement>;
  private signaturePadCtx: CanvasRenderingContext2D | null = null;
  private isDrawing = false;
  protected readonly showMaterialReview = signal(false);
  protected readonly withdrawalCollaboratorId = signal<string | null>(null);
  protected readonly withdrawalOutsideTeam = signal(false);
  protected readonly withdrawalCollaborators = signal<ColaboradorResponse[]>([]);
  protected readonly withdrawalTeamMembers = signal<EquipeMembroResponse[]>([]);
  protected readonly withdrawalOptionsRemote = this.withdrawalLookup.items;
  protected readonly withdrawalRemoteLoading = this.withdrawalLookup.loading;
  protected readonly withdrawalRemoteStatus = this.withdrawalLookup.status;
  protected readonly withdrawalRemoteError = computed(() => {
    const error = this.withdrawalLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });
  protected readonly withdrawalTeamLoading = signal(false);
  protected readonly withdrawalLookupLoading = computed(
    () => this.withdrawalTeamLoading() || this.withdrawalRemoteLoading(),
  );
  protected readonly withdrawalLookupError = signal('');
  protected readonly deliveryTeamId = signal<string | null>(null);
  /** Saldos negativos da equipe que recebe a entrega (apenas informativo). */
  protected readonly saldosNegativosEquipe = signal<Map<string, SaldoNegativoMaterial>>(new Map());
  protected readonly idsMateriaisEntrega = computed(() =>
    this.lines()
      .map((line) => line.idMaterial)
      .filter((id): id is string => !!id),
  );
  protected readonly teams = signal<EquipeResponse[]>([]);
  protected readonly teamsLoading = signal(false);

  protected readonly teamOptions = computed(() =>
    this.teams().map((team) => ({
      label: team.tipoEquipe ? `${team.codigo} · ${team.tipoEquipe.nome}` : team.codigo,
      value: team.id,
    })),
  );

  protected readonly selectedTeam = computed(() =>
    this.teams().find((t) => t.id === (this.deliveryTeamId() ?? this.technical().idEquipe)),
  );

  protected readonly selectedTeamName = computed(() => {
    const team = this.selectedTeam();
    if (!team) return 'Não definida';
    return team.tipoEquipe ? `${team.codigo} · ${team.tipoEquipe.nome}` : team.codigo;
  });

  private initialized = false;
  private lineUidCounter = 0;

  private generateLineUid(): string {
    return `action-line-${++this.lineUidCounter}`;
  }

  protected readonly trackByLineUid = (_index: number, line: ActionLine): string => {
    return line?.uid || line?.idItemSolicitacao || String(_index);
  };

  protected readonly technical = computed(() => this.detail()?.tecnico ?? ({} as any));
  protected readonly actionPermission = computed<TechnicalAllowedAction>(() => {
    const mode = this.mode();
    if (mode === 'reserve') {
      const allowed = this.technical().acoesPermitidas ?? this.detail()?.acoesPermitidas ?? [];
      return allowed.includes('ALTERAR_RESERVA') ? 'ALTERAR_RESERVA' : 'REGISTRAR_RESERVA';
    }
    if (mode === 'deliver') return 'ENTREGAR';
    if (mode === 'return') return 'CONFIRMAR_DEVOLUCAO';
    if (mode === 'reverse') return 'ESTORNAR';
    return 'ENCERRAR_SALDO';
  });
  protected readonly hasDeliveryMovement = computed(() =>
    possuiMovimentacaoAtendimento(this.detail()),
  );
  protected readonly hasSerialLines = computed(() =>
    this.lines().some((line) => line.serialRequired),
  );
  protected readonly hasPendingSerials = computed(() =>
    this.lines().some(
      (line) =>
        line.serialRequired &&
        (this.deliveryMode() === 'all' || (line.quantity ?? 0) > 0) &&
        !line.numeroSerie?.trim(),
    ),
  );
  protected readonly plannedWithdrawalCollaboratorId = computed(
    () => this.technical().idColaboradorSaque ?? null,
  );
  protected readonly plannedWithdrawalOutsideTeam = computed(() =>
    Boolean(this.technical().saqueForaEquipe),
  );
  protected readonly withdrawalOptions = computed(() => {
    if (this.withdrawalOutsideTeam()) {
      return this.withdrawalOptionsRemote();
    }

    return this.withdrawalTeamMembers().map((member) => ({
      label: `${member.nomeColaborador}${member.funcao === 'RESPONSAVEL' ? ' (Responsável da Equipe)' : ''}`,
      value: member.idColaborador,
    }));
  });
  protected readonly plannedWithdrawalName = computed(() =>
    this.withdrawalName(this.plannedWithdrawalCollaboratorId()),
  );
  protected readonly selectedWithdrawalName = computed(() =>
    this.withdrawalName(this.withdrawalCollaboratorId()),
  );

  protected readonly isOcOrOs = computed(() => {
    const tech = this.technical();
    if (!tech) return false;
    return (
      tech.tipoObra === 'CORRETIVA' ||
      tech.tipoObra === 'PREVENTIVA' ||
      Boolean(tech.ocorrencia?.trim()) ||
      Boolean(tech.idOcorrencia)
    );
  });

  protected readonly isReserveRequired = computed(() => !this.isOcOrOs());

  protected readonly actionAllowed = computed(() => {
    return (
      this.technical().acoesPermitidas?.includes(this.actionPermission()) ??
      this.detail()?.acoesPermitidas?.includes(this.actionPermission()) ??
      false
    );
  });
  protected readonly title = computed(
    () =>
      ({
        reserve: this.technical().reserva ? 'Alterar Reserva Externa' : 'Registrar Reserva Externa',
        deliver: 'Confirmar Entrega de Materiais',
        return: 'Confirmar Devolução de Materiais',
        reverse: 'Estornar Operação',
        close: 'Encerrar Saldo',
      })[this.mode()],
  );
  protected readonly description = computed(
    () =>
      ({
        reserve: this.technical().reserva
          ? 'Atualize os dados e a numeração da reserva externa vinculada à concessionária.'
          : 'Informe o número e a data da reserva emitida no sistema SAP / concessionária.',
        deliver: 'Formalize a entrega física dos materiais para a equipe de campo.',
        return: 'Registre o retorno de materiais novos ou sucata do estoque da equipe ao almoxarifado.',
        reverse: 'Cria uma nova operação compensatória para correção de lançamentos físicos.',
        close: 'Finaliza o saldo pendente restante desta solicitação.',
      })[this.mode()],
  );
  protected readonly dialogSize = computed<CesDialogSize>(() => {
    switch (this.mode()) {
      case 'deliver':
      case 'reserve':
        return 'xl';
      default:
        return 'lg';
    }
  });
  protected readonly reversibleOperations = computed(() => {
    const operations: OperacaoSolicitacaoTecnicaResponse[] = this.technical().operacoes ?? [];
    return operations.filter(
      (operation: OperacaoSolicitacaoTecnicaResponse) =>
        (operation.tipo === 'ENTREGA' || operation.tipo === 'DEVOLUCAO') &&
        (operation.itens ?? []).some(
          (item: ItemOperacaoTecnicaResponse) => this.remaining(operation, item.id) > 0,
        ),
    );
  });
  protected readonly operationOptions = computed(() =>
    this.reversibleOperations().map((operation: OperacaoSolicitacaoTecnicaResponse) => ({
      label: `${operation.tipo === 'ENTREGA' ? 'Entrega' : 'Devolução'} · ${new Date(operation.realizadaEm).toLocaleString('pt-BR')}`,
      value: operation.id,
    })),
  );
  protected readonly locked = computed(() => this.submitting() || this.retryRequest() !== null);

  protected readonly fixedType = computed(() => this.technical().tipoTecnico);
  protected readonly fixedTypeLabel = computed(() => tipoTecnicoLabel(this.fixedType()));
  protected readonly fixedTypeIcon = computed(() => tipoTecnicoIcon(this.fixedType()));
  protected readonly isAddition = computed(() => tipoTecnicoEhAdicao(this.fixedType()));

  protected readonly obraDisplay = computed(() => {
    const obra = this.obraInfo();
    if (obra) return `Obra ${obra.numero} — ${obra.titulo}`;
    return this.technical()?.tipoObra ? `Obra (${this.technical().tipoObra})` : 'Obra vinculada';
  });

  protected readonly selectedAgentName = computed(() => {
    const id = this.agentId();
    if (!id) return 'Não informado';
    const agent = this.agents().find((a) => a.value === id);
    return agent ? agent.label : 'Não informado';
  });

  protected readonly formattedReserveDate = computed(() => {
    const d = this.reserveDate();
    if (!d) return 'Não informada';
    return d.toLocaleDateString('pt-BR');
  });

  protected readonly statusLabel = statusLabel;
  protected readonly statusTone = statusTone;
  protected readonly statusTagClass = statusTagClass;
  protected readonly separacaoLabel = separacaoLabel;
  protected readonly separacaoTone = separacaoTone;
  protected readonly separacaoTagClass = separacaoTagClass;
  protected readonly tipoTecnicoIcon = tipoTecnicoIcon;
  protected readonly tipoTecnicoLabel = tipoTecnicoLabel;
  protected readonly tipoTecnicoEhAdicao = tipoTecnicoEhAdicao;

  ngOnInit(): void {
    this.initialized = true;
    this.resetFormForCurrentMode();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.initialized) return;
    if (changes['mode'] || changes['detail']) {
      this.resetFormForCurrentMode();
      return;
    }
    if (changes['visible']?.currentValue === true) {
      this.resetFormForCurrentMode(true);
    }
  }

  protected validateDeliverStep1(): boolean {
    const errors: Record<string, string> = { ...this.fieldErrors() };
    delete errors['itens'];
    this.syncSerialQuantities();
    const selected = this.lines().filter((line) => (line.quantity ?? 0) > 0);
    let valid = true;
    if (!selected.length) {
      errors['itens'] = 'Informe a quantidade de ao menos um item.';
      valid = false;
    } else if (selected.some((line) => line.quantity! > line.max)) {
      errors['itens'] = 'Uma quantidade excede o saldo permitido.';
      valid = false;
    } else {
      const itemTotals = new Map<string, { total: number; max: number }>();
      for (const line of selected) {
        const key = line.idItemSolicitacao || line.idItemOperacaoOriginal || '';
        const current = itemTotals.get(key) || { total: 0, max: line.max };
        current.total += line.quantity ?? 0;
        itemTotals.set(key, current);
      }
      for (const [, { total, max }] of itemTotals) {
        if (total > max) {
          errors['itens'] = 'A soma das quantidades excede o saldo permitido.';
          valid = false;
          break;
        }
      }

      if (valid && selected.some((line) => line.serialRequired && !line.numeroSerie.trim())) {
        errors['itens'] = 'Informe o número de série de cada material seriado.';
        valid = false;
      } else if (valid) {
        const serials = selected
          .filter((line) => line.serialRequired && line.numeroSerie.trim())
          .map((line) => line.numeroSerie.trim().toUpperCase());
        if (new Set(serials).size !== serials.length) {
          errors['itens'] = 'Não repita o mesmo número de série na requisição.';
          valid = false;
        }
      }
    }
    this.fieldErrors.set(errors);
    if (!valid) {
      this.notifications.warning(errors['itens'] || 'Revise os itens da entrega.');
    }
    return valid;
  }

  protected validateReserveStep1(): boolean {
    const errors: Record<string, string> = {};
    if (!/^\d{1,20}$/.test(this.reserveNumber())) {
      errors['numero'] = 'Informe até 20 dígitos numéricos para a reserva.';
    }
    if (!this.reserveDate()) {
      errors['dataCriacao'] = 'Informe a data de emissão da reserva.';
    }
    if (this.technical().reserva && !this.justification().trim()) {
      errors['justificativa'] = 'Informe a justificativa da alteração da reserva.';
    } else if (this.justification().trim().length > 500) {
      errors['justificativa'] = 'A justificativa deve ter no máximo 500 caracteres.';
    }

    this.fieldErrors.set(errors);
    if (Object.keys(errors).length > 0) {
      this.notifications.warning('Revise os campos obrigatórios antes de avançar.');
      return false;
    }
    return true;
  }

  protected validateDeliverStep2(): boolean {
    const errors: Record<string, string> = { ...this.fieldErrors() };
    delete errors['idEquipe'];
    delete errors['idColaboradorSaque'];
    delete errors['numeroReserva'];

    const teamId = this.deliveryTeamId() ?? this.technical()?.idEquipe;
    if (!teamId) {
      errors['idEquipe'] = 'A equipe responsável pelo saque é obrigatória.';
    }

    const collaboratorId =
      this.withdrawalCollaboratorId() ?? this.plannedWithdrawalCollaboratorId();
    if (!collaboratorId) {
      errors['idColaboradorSaque'] = this.withdrawalOutsideTeam()
        ? 'Selecione o colaborador responsável pelo saque fora da equipe.'
        : 'Selecione o colaborador responsável pelo saque.';
    }

    if (!this.technical()?.reserva) {
      const reserveNum = this.reserveNumber().trim();
      if (this.isReserveRequired()) {
        if (!reserveNum) {
          errors['numeroReserva'] = 'O número da reserva SAP é obrigatório para esta entrega.';
        } else if (!/^\d{1,20}$/.test(reserveNum)) {
          errors['numeroReserva'] = 'Informe até 20 dígitos numéricos para a reserva.';
        }
        if (!this.reserveDate()) {
          errors['dataReserva'] = 'A data de emissão da reserva é obrigatória.';
        }
      } else if (reserveNum && !/^\d{1,20}$/.test(reserveNum)) {
        errors['numeroReserva'] = 'Informe até 20 dígitos numéricos para a reserva.';
      }
    }

    this.fieldErrors.set(errors);
    const hasErrors = Boolean(
      errors['idEquipe'] || errors['idColaboradorSaque'] || errors['numeroReserva'] || errors['dataReserva'],
    );
    if (hasErrors) {
      if (errors['numeroReserva'] || errors['dataReserva']) {
        this.notifications.warning(
          'O número e a data da reserva SAP são obrigatórios para confirmar a entrega de obras que não possuem OC/OS.',
        );
      } else {
        this.notifications.warning('Preencha os campos obrigatórios da retirada.');
      }
      return false;
    }
    return true;
  }

  protected confirmDeliveryStep(): void {
    if (!this.validateDeliverStep2()) return;
    this.displaySignatureTypeModal.set(true);
    this.cdr.markForCheck();
  }

  protected goToStep(step: number): void {
    if (this.mode() === 'deliver') {
      if (step === 2 && this.activeStep() === 1) {
        if (!this.validateDeliverStep1()) return;
      }
    }
    if (this.mode() === 'reserve' && step === 2 && this.activeStep() === 1) {
      if (!this.validateReserveStep1()) return;
    }
    this.activeStep.set(step);
    this.cdr.markForCheck();
  }

  protected nextStep(): void {
    if (this.mode() === 'deliver') {
      if (this.activeStep() === 1) {
        if (this.validateDeliverStep1()) {
          this.activeStep.set(2);
          this.cdr.markForCheck();
        }
      }
    } else if (this.mode() === 'reserve' && this.activeStep() === 1) {
      if (this.validateReserveStep1()) {
        this.activeStep.set(2);
        this.cdr.markForCheck();
      }
    }
  }

  protected prevStep(): void {
    if (this.activeStep() > 1) {
      this.activeStep.update((s) => s - 1);
      this.cdr.markForCheck();
    }
  }

  protected selectSignatureType(type: 'FISICA' | 'DIGITAL'): void {
    this.signatureType.set(type);
    if (type === 'FISICA') {
      this.signatureWarehouse.set('FISICA');
      this.signatureTeam.set('FISICA');
      this.displaySignatureTypeModal.set(false);
      this.submit();
    } else {
      if (this.signatureWarehouse() === 'FISICA') this.signatureWarehouse.set('');
      if (this.signatureTeam() === 'FISICA') this.signatureTeam.set('');
      this.displaySignatureTypeModal.set(false);
      this.displayDigitalSignatureDashboard.set(true);
    }
    this.clearFieldError('assinaturaAlmox');
    this.clearFieldError('assinaturaEquipe');
    this.cdr.markForCheck();
  }

  protected openSignaturePad(field: 'almox' | 'equipe'): void {
    this.currentSignatureField.set(field);
    this.displayDigitalSignatureDashboard.set(false);
    this.displaySignatureModal.set(true);
    setTimeout(() => this.initSignaturePad(), 100);
  }

  protected closeSignaturePad(): void {
    this.displaySignatureModal.set(false);
    this.displayDigitalSignatureDashboard.set(true);
    this.clearSignaturePad();
    this.cdr.markForCheck();
  }

  protected initSignaturePad(): void {
    const canvas = this.signatureCanvas?.nativeElement;
    if (canvas) {
      const rect = canvas.getBoundingClientRect();
      canvas.width = rect.width;
      canvas.height = rect.height;
      this.signaturePadCtx = canvas.getContext('2d');
      if (this.signaturePadCtx) {
        this.signaturePadCtx.lineWidth = 3;
        this.signaturePadCtx.lineCap = 'round';
        this.signaturePadCtx.lineJoin = 'round';
        this.signaturePadCtx.strokeStyle = '#000';
        this.clearSignaturePad();
      }
    }
  }

  protected startDrawing(event: MouseEvent | TouchEvent): void {
    this.isDrawing = true;
    this.draw(event);
  }

  protected stopDrawing(): void {
    this.isDrawing = false;
    if (this.signaturePadCtx) {
      this.signaturePadCtx.beginPath();
    }
  }

  protected draw(event: MouseEvent | TouchEvent): void {
    if (!this.isDrawing || !this.signaturePadCtx || !this.signatureCanvas) return;
    event.preventDefault();

    const canvas = this.signatureCanvas.nativeElement;
    const rect = canvas.getBoundingClientRect();

    let clientX: number, clientY: number;
    if (event instanceof MouseEvent) {
      clientX = event.clientX;
      clientY = event.clientY;
    } else if (window.TouchEvent && event instanceof TouchEvent && event.touches.length > 0) {
      clientX = event.touches[0].clientX;
      clientY = event.touches[0].clientY;
    } else {
      return;
    }

    const x = clientX - rect.left;
    const y = clientY - rect.top;

    this.signaturePadCtx.lineTo(x, y);
    this.signaturePadCtx.stroke();
    this.signaturePadCtx.beginPath();
    this.signaturePadCtx.moveTo(x, y);
  }

  protected clearSignaturePad(): void {
    if (this.signaturePadCtx && this.signatureCanvas) {
      const canvas = this.signatureCanvas.nativeElement;
      this.signaturePadCtx.clearRect(0, 0, canvas.width, canvas.height);
      this.signaturePadCtx.beginPath();
    }
  }

  protected saveSignature(): void {
    const canvas = this.signatureCanvas?.nativeElement;
    if (canvas) {
      const base64 = canvas.toDataURL('image/png');
      const field = this.currentSignatureField();
      if (field === 'almox') {
        this.signatureWarehouse.set(base64);
        this.clearFieldError('assinaturaAlmox');
      } else if (field === 'equipe') {
        this.signatureTeam.set(base64);
        this.clearFieldError('assinaturaEquipe');
      }
      this.displaySignatureModal.set(false);
      this.displayDigitalSignatureDashboard.set(true);
      this.cdr.markForCheck();
    }
  }

  protected setPhysicalSignature(field: 'almox' | 'equipe'): void {
    if (field === 'almox') {
      this.signatureWarehouse.set('FISICA');
      this.clearFieldError('assinaturaAlmox');
    } else {
      this.signatureTeam.set('FISICA');
      this.clearFieldError('assinaturaEquipe');
    }
    this.cdr.markForCheck();
  }

  protected clearSignatureData(field: 'almox' | 'equipe'): void {
    if (field === 'almox') {
      this.signatureWarehouse.set('');
    } else {
      this.signatureTeam.set('');
    }
    this.cdr.markForCheck();
  }

  protected isBase64(str: string | undefined | null): boolean {
    if (!str) return false;
    return str.startsWith('data:image/');
  }

  protected selectOperation(id: string | null): void {
    this.originalOperationId.set(id);
    const operation = this.reversibleOperations().find(
      (item: OperacaoSolicitacaoTecnicaResponse) => item.id === id,
    );
    this.lines.set(
      operation?.itens
        ?.map((item: ItemOperacaoTecnicaResponse) => ({
          uid: this.generateLineUid(),
          idItemOperacaoOriginal: item.id,
          label: `${item.codigoMaterial} — ${item.descricaoMaterial}`,
          max: this.remaining(operation, item.id),
          quantity: null,
          serialRequired: Boolean(item.numeroSerie),
          numeroSerie: item.numeroSerie ?? '',
          condition: 'NOVO' as const,
        }))
        .filter((line: ActionLine) => line.max > 0) ?? [],
    );
  }

  protected updateLine(index: number, patch: Partial<ActionLine>): void {
    if (this.locked()) return;
    if (this.mode() === 'deliver' && 'quantity' in patch) this.deliveryMode.set('partial');
    this.lines.update((lines) =>
      lines.map((line, position) => {
        if (position !== index) return line;
        const updated = { ...line, ...patch };
        if (updated.serialRequired && patch.numeroSerie !== undefined) {
          const hasSerial = Boolean(patch.numeroSerie?.trim());
          if (hasSerial) {
            updated.quantity = 1;
          } else if (
            this.deliveryMode() === 'partial' &&
            !this.isItemFullySelected(line.idItemSolicitacao)
          ) {
            updated.quantity = null;
          }
        }
        return updated;
      }),
    );
  }

  protected onWithdrawalCollaboratorChange(id: string | null): void {
    if (this.locked()) return;
    this.withdrawalCollaboratorId.set(id);
    if (id && this.withdrawalOutsideTeam()) this.resolveWithdrawalCollaborator(id);
  }

  protected onWithdrawalOutsideTeamChange(value: boolean): void {
    if (this.locked()) return;
    this.withdrawalOutsideTeam.set(value);
    if (!value) {
      const memberIds = new Set(this.withdrawalTeamMembers().map((member) => member.idColaborador));
      const selectedId = this.withdrawalCollaboratorId();
      if (selectedId && !memberIds.has(selectedId)) this.withdrawalCollaboratorId.set(null);
    }
  }

  protected searchWithdrawalCollaborators(query: string): void {
    if (this.withdrawalOutsideTeam() && !this.locked()) this.withdrawalLookup.search(query);
  }

  protected loadMoreWithdrawalCollaborators(): void {
    if (this.withdrawalOutsideTeam() && !this.locked()) this.withdrawalLookup.loadNext();
  }

  protected retryWithdrawalCollaborators(): void {
    if (this.withdrawalOutsideTeam() && !this.locked()) this.withdrawalLookup.retry();
  }

  protected selectDeliveryMode(mode: DeliveryMode): void {
    if (this.locked()) return;
    this.deliveryMode.set(mode);
    if (mode === 'partial') {
      this.prepareRequestLines();
      this.cdr.markForCheck();
      return;
    }
    this.lines.set(
      this.lines().flatMap((line, index, lines) =>
        lines.findIndex((item) => item.idItemSolicitacao === line.idItemSolicitacao) === index
          ? this.completeDeliveryLines(line)
          : [],
      ),
    );
    const hasPendingSerials = this.lines().some((l) => l.serialRequired && !l.numeroSerie?.trim());
    if (!hasPendingSerials && this.validateDeliverStep1()) {
      this.activeStep.set(2);
    }
    this.cdr.markForCheck();
  }

  protected isFirstLineForItem(index: number): boolean {
    const lines = this.lines();
    return (
      lines.findIndex((line) => line.idItemSolicitacao === lines[index]?.idItemSolicitacao) ===
      index
    );
  }

  protected isItemFullySelected(itemId: string | undefined): boolean {
    if (!itemId) return false;
    const itemLines = this.lines().filter((line) => line.idItemSolicitacao === itemId);
    return (
      Boolean(itemLines.length) &&
      itemLines.reduce((sum, line) => sum + (line.quantity ?? 0), 0) === itemLines[0].max
    );
  }

  protected toggleItemFullySelected(index: number, selected: boolean): void {
    if (this.locked()) return;
    const selectedLine = this.lines()[index];
    if (!selectedLine?.idItemSolicitacao) return;
    const replacement = selected
      ? this.completeDeliveryLines(selectedLine)
      : [{ ...selectedLine, uid: selectedLine.uid || this.generateLineUid(), quantity: null, numeroSerie: '' }];
    this.lines.update((lines) =>
      lines.flatMap((line, position) => {
        if (position === index) return replacement;
        return line.idItemSolicitacao === selectedLine.idItemSolicitacao ? [] : [line];
      }),
    );
  }

  protected addSerialLine(line: ActionLine): void {
    const currentLines = this.lines().filter(
      (item) => item.idItemSolicitacao === line.idItemSolicitacao,
    );
    if (currentLines.length >= line.max) return;
    if (this.mode() === 'deliver') this.deliveryMode.set('partial');
    this.lines.update((lines) => {
      const lastIndex = lines
        .map((item) => item.idItemSolicitacao)
        .lastIndexOf(line.idItemSolicitacao);
      return [
        ...lines.slice(0, lastIndex + 1),
        { ...line, uid: this.generateLineUid(), quantity: 1, numeroSerie: '' },
        ...lines.slice(lastIndex + 1),
      ];
    });
  }

  protected removeLine(index: number): void {
    if (this.mode() === 'deliver') this.deliveryMode.set('partial');
    this.lines.update((lines) => lines.filter((_, i) => i !== index));
  }

  protected readonly selectedItemsCount = computed(
    () => this.lines().filter((line) => (line.quantity ?? 0) > 0).length,
  );

  protected canAddSerialUnit(line: ActionLine): boolean {
    if (!line.serialRequired) return false;
    const currentLines = this.lines().filter(
      (item) => item.idItemSolicitacao === line.idItemSolicitacao,
    );
    return currentLines.length < line.max;
  }

  protected canRemoveSerialUnit(line: ActionLine): boolean {
    if (!line.serialRequired) return false;
    return (
      this.lines().filter((item) => item.idItemSolicitacao === line.idItemSolicitacao).length > 1
    );
  }

  protected submit(): void {
    if (!this.actionAllowed()) {
      this.notifications.warning('Esta ação não está disponível para a solicitação atual.');
      return;
    }
    if (this.mode() === 'reserve' && this.activeStep() === 1 && !this.validateReserveStep1()) {
      return;
    }
    const request = this.retryRequest() ?? this.buildRequest();
    if (!request) {
      if (this.fieldErrors()['numeroReserva'] || this.fieldErrors()['dataReserva']) {
        this.notifications.warning(
          'O número e a data da reserva SAP são obrigatórios para confirmar a entrega de obras que não possuem OC/OS.',
        );
      }
      return;
    }
    const key = this.retryKey() ?? novaChaveIdempotencia();
    this.retryRequest.set(request);
    this.retryKey.set(key);
    this.submitting.set(true);
    this.fieldErrors.set({});
    this.execute(request, key).subscribe({
      next: (result) => {
        this.submitting.set(false);
        this.retryRequest.set(null);
        this.retryKey.set(null);
        this.clearSensitive();
        const idOperation = this.hasOperationId(result) ? result.idOperacao : undefined;
        this.notifications.success(this.successMessage());
        this.completed.emit({ message: this.successMessage(), idOperation });
        this.displaySignatureTypeModal.set(false);
        this.displayDigitalSignatureDashboard.set(false);
        this.displaySignatureModal.set(false);
        this.visible.set(false);
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.submitting.set(false);
        if (ehConflitoDeVersao(error)) {
          const problem = problemaTecnico(error)!;
          this.clearSensitive();
          this.retryRequest.set(null);
          this.retryKey.set(null);
          this.displaySignatureTypeModal.set(false);
          this.displayDigitalSignatureDashboard.set(false);
          this.displaySignatureModal.set(false);
          this.visible.set(false);
          this.conflict.emit({ problem, intent: this.intentSummary(request) });
          return;
        }
        if (!ehFalhaDeTransporte(error)) {
          this.retryRequest.set(null);
          this.retryKey.set(null);
        }
        this.fieldErrors.set(humanizarErrosDeCampo(errosDeCampo(error)));
        this.notifications.error(getErrorMessage(error), {
          title: ehFalhaDeTransporte(error)
            ? 'Resposta não confirmada'
            : 'Não foi possível concluir',
        });
        this.cdr.markForCheck();
      },
    });
  }

  protected discardRetry(): void {
    this.retryRequest.set(null);
    this.retryKey.set(null);
  }

  protected close(): void {
    if (!this.submitting()) {
      this.clearSensitive();
      this.activeStep.set(1);
      this.displaySignatureTypeModal.set(false);
      this.displayDigitalSignatureDashboard.set(false);
      this.displaySignatureModal.set(false);
      this.visible.set(false);
    }
  }

  protected fieldError(name: string): string | undefined {
    return this.fieldErrors()[name];
  }

  protected pendenciaModalVisible = signal(false);
  protected pendenciaModalItemId = signal<string | null>(null);
  protected pendenciaModalItemLabel = signal<string | null>(null);
  protected pendenciaModalMotivoAtual = signal<MotivoPendenciaResponse | null>(null);
  protected pendenciaModalJustificativaAtual = signal<string | null>(null);

  protected abrirModalPendencia(): void {
    const d = this.detail();
    this.pendenciaModalItemId.set(null);
    this.pendenciaModalItemLabel.set(null);
    this.pendenciaModalMotivoAtual.set(d?.motivoPendencia ?? null);
    this.pendenciaModalJustificativaAtual.set(d?.justificativaPendencia ?? null);
    this.pendenciaModalVisible.set(true);
  }

  protected openItemPendencia(line: ActionLine): void {
    if (!line.idItemSolicitacao) return;
    this.pendenciaModalItemId.set(line.idItemSolicitacao);
    this.pendenciaModalItemLabel.set(line.label);
    this.pendenciaModalMotivoAtual.set(line.motivoPendencia ?? null);
    this.pendenciaModalJustificativaAtual.set(line.justificativaPendencia ?? null);
    this.pendenciaModalVisible.set(true);
  }

  protected onPendenciaAtualizada(): void {
    const detailId = this.detail()?.id;
    if (!detailId) return;
    this.service.buscarPorId(detailId).subscribe({
      next: (fresh) => {
        const itensMap = new Map((fresh.itens ?? []).map((i) => [i.id, i]));
        this.lines.update((lines) =>
          lines.map((line) => {
            const item = line.idItemSolicitacao ? itensMap.get(line.idItemSolicitacao) : null;
            return {
              ...line,
              motivoPendencia: item?.motivoPendencia ?? null,
              justificativaPendencia: item?.justificativaPendencia ?? null,
            };
          })
        );
        this.cdr.markForCheck();
      },
    });
  }

  protected onDeliveryTeamChange(id: string | null): void {
    if (this.locked()) return;
    this.deliveryTeamId.set(id);
    this.withdrawalCollaboratorId.set(null);
    this.clearFieldError('idEquipe');
    this.clearFieldError('idColaboradorSaque');
    this.loadWithdrawalLookups();
    this.carregarSaldosNegativosEquipe();
  }

  protected saldoNegativoDoMaterial(idMaterial: string | null | undefined): SaldoNegativoMaterial | null {
    return idMaterial ? (this.saldosNegativosEquipe().get(idMaterial) ?? null) : null;
  }

  private carregarSaldosNegativosEquipe(): void {
    this.saldosNegativosEquipe.set(new Map());
    const idEquipe = this.mode() === 'deliver' ? (this.deliveryTeamId() ?? this.technical()?.idEquipe) : null;
    if (!idEquipe || typeof this.almoxarifadoService?.listarSaldosNegativos !== 'function') return;
    // Informativo: uma falha aqui não pode atrapalhar a entrega.
    this.almoxarifadoService.listarSaldosNegativos({ idEquipe }).subscribe({
      next: (saldos) => {
        if ((this.deliveryTeamId() ?? this.technical()?.idEquipe) !== idEquipe) return;
        this.saldosNegativosEquipe.set(agruparSaldosNegativos(saldos));
        this.cdr.markForCheck();
      },
      error: () => this.saldosNegativosEquipe.set(new Map()),
    });
  }

  protected clearFieldError(name: string): void {
    if (!this.fieldErrors()[name]) return;
    this.fieldErrors.update((errors) => {
      const next = { ...errors };
      delete next[name];
      return next;
    });
  }

  private resetFormForCurrentMode(preserveRetry = false): void {
    if (preserveRetry && this.retryRequest()) {
      this.cdr.markForCheck();
      return;
    }

    this.activeStep.set(1);
    this.fieldErrors.set({});
    this.retryRequest.set(null);
    this.retryKey.set(null);
    this.lines.set([]);
    this.reserveNumber.set('');
    this.reserveDate.set(null);
    this.agentId.set(null);
    this.justification.set('');
    this.reason.set('');
    this.originalOperationId.set(null);
    this.displaySignatureTypeModal.set(false);
    this.displayDigitalSignatureDashboard.set(false);
    this.displaySignatureModal.set(false);
    this.signatureType.set('FISICA');
    this.signatureWarehouse.set('FISICA');
    this.signatureTeam.set('FISICA');
    this.deliveryMode.set('partial');
    this.deliveryTeamId.set(null);
    this.withdrawalCollaboratorId.set(null);
    this.withdrawalOutsideTeam.set(false);
    this.clearSensitive();

    const technical = this.technical();
    if (technical?.idObra && typeof this.obrasService?.buscarObraPorId === 'function') {
      this.obrasService.buscarObraPorId(technical.idObra).subscribe({
        next: (obra) => {
          this.obraInfo.set(obra);
          this.cdr.markForCheck();
        },
        error: () => {
          this.obraInfo.set(null);
          this.cdr.markForCheck();
        },
      });
    }

    const reserve = technical?.reserva;
    if (this.mode() === 'reserve' && reserve) {
      this.reserveNumber.set(reserve.numero);
      this.reserveDate.set(this.parseDate(reserve.dataCriacao));
      this.agentId.set(reserve.idAgenteResponsavel ?? null);
    }
    if (this.mode() === 'deliver' || this.mode() === 'return') this.prepareRequestLines();
    if (this.mode() === 'deliver') {
      this.deliveryTeamId.set(technical?.idEquipe ?? null);
      this.withdrawalCollaboratorId.set(technical?.idColaboradorSaque ?? null);
      this.withdrawalOutsideTeam.set(Boolean(technical?.saqueForaEquipe));
      if (!technical?.reserva) {
        this.reserveNumber.set('');
        this.reserveDate.set(new Date());
        this.agentId.set(null);
      }
      this.loadTeamsIfNeeded();
      this.loadWithdrawalLookups();
      this.carregarSaldosNegativosEquipe();
    }
    if (this.mode() === 'reverse' && this.reversibleOperations()[0]) {
      this.selectOperation(this.reversibleOperations()[0].id);
    }
    this.loadAgentsIfNeeded();
    this.cdr.markForCheck();
  }

  private loadTeamsIfNeeded(): void {
    if (this.mode() !== 'deliver' || this.teamsLoading() || this.teams().length) return;
    if (typeof this.rhService?.listarEquipes !== 'function') return;
    this.teamsLoading.set(true);
    this.rhService.listarEquipes({ ativo: true, size: 1000, sort: 'codigo,asc' }).subscribe({
      next: (page) => {
        this.teams.set(page.content);
        this.teamsLoading.set(false);
        this.cdr.markForCheck();
      },
      error: () => {
        this.teamsLoading.set(false);
        this.cdr.markForCheck();
      },
    });
  }

  private loadAgentsIfNeeded(): void {
    if (
      (this.mode() !== 'reserve' && this.mode() !== 'deliver') ||
      this.agentsLoading() ||
      this.agents().length
    )
      return;

    this.agentsLoading.set(true);
    this.agentsLoadError.set('');
    if (typeof this.obrasService?.listarAgentesAtivos === 'function') {
      this.obrasService.listarAgentesAtivos().subscribe({
        next: (agents) => {
          this.agents.set(
            agents.map((agent) => ({
              label: `${agent.nome}${agent.empresaOrgao ? ' · ' + agent.empresaOrgao : ''}`,
              value: agent.id,
            })),
          );
          this.agentsLoading.set(false);
          this.cdr.markForCheck();
        },
        error: () => {
          this.agentsLoading.set(false);
          this.agentsLoadError.set(
            'Não foi possível carregar os fiscais e agentes. O campo continua opcional.',
          );
          this.cdr.markForCheck();
        },
      });
    }
  }

  private syncSerialQuantities(): void {
    this.lines.update((lines) =>
      lines.map((line) => {
        if (!line.serialRequired) return line;
        const hasSerial = Boolean(line.numeroSerie?.trim());
        if (hasSerial && (line.quantity ?? 0) !== 1) {
          return { ...line, quantity: 1 };
        }
        return line;
      }),
    );
  }

  private prepareRequestLines(): void {
    const detail = this.detail();
    if (!detail) return;
    this.lines.set(
      (detail.itens ?? [])
        .filter((item) => (item.quantidadePendente ?? 0) > 0)
        .map((item) => {
          const serialRequired = Boolean(item.material?.detalhaSerie || (item as any).numSerie);
          return {
            uid: this.generateLineUid(),
            idItemSolicitacao: item.id,
            idMaterial: item.material?.id,
            label: `${item.material.codigo} — ${item.material.descricao}`,
            max: item.quantidadePendente ?? 0,
            quantity: serialRequired && this.deliveryMode() === 'all' ? 1 : null,
            serialRequired,
            numeroSerie: '',
            condition: 'NOVO' as const,
            motivoPendencia: item.motivoPendencia ?? null,
            justificativaPendencia: item.justificativaPendencia ?? null,
          };
        }),
    );
  }

  private loadWithdrawalLookups(): void {
    const teamId = this.deliveryTeamId() ?? this.technical()?.idEquipe;
    this.withdrawalLookupError.set('');
    const selectedId = this.withdrawalCollaboratorId();
    if (selectedId && this.withdrawalOutsideTeam()) this.resolveWithdrawalCollaborator(selectedId);

    if (!teamId) {
      this.withdrawalTeamMembers.set([]);
      return;
    }

    this.withdrawalTeamLoading.set(true);
    this.rhService.listarMembrosAtivos(teamId).subscribe({
      next: (members) => {
        this.withdrawalTeamMembers.set(members);
        this.withdrawalTeamLoading.set(false);
        this.cdr.markForCheck();
      },
      error: () => {
        this.withdrawalTeamLoading.set(false);
        this.withdrawalLookupError.set(
          'Não foi possível carregar os membros da equipe para o saque.',
        );
        this.cdr.markForCheck();
      },
    });
  }

  private resolveWithdrawalCollaborator(id: string): void {
    this.rhService.buscarColaboradorPorId(id).subscribe({
      next: (collaborator) => {
        this.withdrawalCollaborators.update((items) => [
          ...items.filter((item) => item.id !== collaborator.id),
          collaborator,
        ]);
        this.withdrawalLookup.seedSelected({
          value: collaborator.id,
          label: this.collaboratorName(collaborator),
        });
        this.cdr.markForCheck();
      },
    });
  }

  private completeDeliveryLines(line: ActionLine): ActionLine[] {
    if (!line.serialRequired) return [{ ...line, quantity: line.max }];
    const existing = this.lines().filter(
      (item) => item.idItemSolicitacao === line.idItemSolicitacao,
    );
    return Array.from({ length: Math.max(0, Math.floor(line.max)) }, (_, index) => ({
      ...line,
      uid: existing[index]?.uid || this.generateLineUid(),
      quantity: 1,
      numeroSerie: existing[index]?.numeroSerie ?? '',
    }));
  }

  private buildRequest(): ActionRequest | null {
    const version = this.technical().versao;
    const errors: Record<string, string> = {};
    let request: ActionRequest | null = null;
    if (this.mode() === 'reserve') {
      if (!/^\d{1,20}$/.test(this.reserveNumber())) errors['numero'] = 'Informe até 20 dígitos.';
      if (!this.reserveDate()) errors['dataCriacao'] = 'Informe a data da reserva.';
      if (this.technical().reserva && !this.justification().trim())
        errors['justificativa'] = 'Justifique a alteração da reserva.';
      if (this.justification().trim().length > 500)
        errors['justificativa'] = 'A justificativa deve ter no máximo 500 caracteres.';
      request = {
        versaoEsperada: version,
        numero: this.reserveNumber(),
        dataCriacao: this.toCivilDate(this.reserveDate())!,
        justificativa: this.justification().trim() || null,
      };
      const agentId = this.agentId();
      if (agentId) request.idAgenteResponsavel = agentId;
    } else if (this.mode() === 'close') {
      if (!this.reason().trim()) errors['motivo'] = 'Informe o motivo do encerramento.';
      request = { versaoEsperada: version, motivo: this.reason().trim() };
    } else if (this.mode() === 'reverse') {
      if (!this.originalOperationId())
        errors['idOperacaoOriginal'] = 'Selecione a operação original.';
      if (!this.reason().trim()) errors['motivo'] = 'Informe o motivo do estorno.';
      const items = this.validLines(errors).map((line) => ({
        idItemOperacaoOriginal: line.idItemOperacaoOriginal!,
        quantidade: line.quantity!,
        numeroSerie: line.numeroSerie || null,
      }));
      request = {
        versaoEsperada: version,
        idOperacaoOriginal: this.originalOperationId()!,
        motivo: this.reason().trim(),
        itens: items,
      };
    } else if (this.mode() === 'deliver') {
      const teamId = this.deliveryTeamId() ?? this.technical().idEquipe;
      if (!teamId) {
        errors['idEquipe'] = 'A equipe responsável pelo saque é obrigatória.';
      }
      if (!this.technical().reserva) {
        const reserveNum = this.reserveNumber().trim();
        if (this.isReserveRequired()) {
          if (!reserveNum) {
            errors['numeroReserva'] = 'O número da reserva SAP é obrigatório para esta entrega.';
          } else if (!/^\d{1,20}$/.test(reserveNum)) {
            errors['numeroReserva'] = 'Informe até 20 dígitos numéricos para a reserva.';
          }
          if (!this.reserveDate()) {
            errors['dataReserva'] = 'A data de emissão da reserva é obrigatória.';
          }
        } else if (reserveNum && !/^\d{1,20}$/.test(reserveNum)) {
          errors['numeroReserva'] = 'Informe até 20 dígitos numéricos para a reserva.';
        }
      }
      if (!this.signatureWarehouse()) {
        errors['assinaturaAlmox'] = 'Registre a assinatura do almoxarife.';
      }
      if (!this.signatureTeam()) {
        errors['assinaturaEquipe'] = 'Registre a assinatura da equipe.';
      }

      const items = this.validLines(errors).map((line) => ({
        idItemSolicitacao: line.idItemSolicitacao!,
        quantidade: line.quantity!,
        numeroSerie: line.numeroSerie?.trim() || null,
      }));

      const override = this.buildWithdrawalOverride(errors);
      const hasReserve = !this.technical().reserva && Boolean(this.reserveNumber().trim());
      const resDate = this.reserveDate() ? this.toCivilDate(this.reserveDate()) : null;

      request = {
        versaoEsperada: version,
        idEquipe: teamId!,
        ...(override ?? {}),
        assinaturaAlmox: this.signatureWarehouse(),
        assinaturaEquipe: this.signatureTeam(),
        itens: items,
        ...(hasReserve
          ? {
              numeroReserva: this.reserveNumber().trim(),
              dataReserva: resDate ?? this.toCivilDate(new Date())!,
              idAgenteReserva: this.agentId() || undefined,
            }
          : {}),
      };
    } else {
      if (!this.technical().idEquipe)
        errors['idEquipe'] = 'A solicitação precisa definir uma equipe.';
      if (!this.technical().reserva) {
        const reserveNum = this.reserveNumber().trim();
        if (reserveNum && !/^\d{1,20}$/.test(reserveNum)) {
          errors['numeroReserva'] = 'Informe até 20 dígitos numéricos para a reserva.';
        }
      }
      const items = this.validLines(errors).map((line) => ({
        idItemSolicitacao: line.idItemSolicitacao!,
        quantidade: line.quantity!,
        numeroSerie: line.numeroSerie?.trim() || null,
        condicao: line.condition,
      }));
      const hasReserve = !this.technical().reserva && Boolean(this.reserveNumber().trim());
      const resDate = this.reserveDate() ? this.toCivilDate(this.reserveDate()) : null;
      request = {
        versaoEsperada: version,
        idEquipe: this.technical().idEquipe!,
        itens: items,
        ...(hasReserve
          ? {
              numeroReserva: this.reserveNumber().trim(),
              dataReserva: resDate ?? this.toCivilDate(new Date())!,
              idAgenteReserva: this.agentId() || undefined,
            }
          : {}),
      };
    }
    this.fieldErrors.set(errors);
    return Object.keys(errors).length ? null : request;
  }

  private validLines(errors: Record<string, string>): ActionLine[] {
    this.syncSerialQuantities();
    const selected = this.lines().filter((line) => (line.quantity ?? 0) > 0);
    if (!selected.length) {
      errors['itens'] = 'Informe a quantidade de ao menos um item.';
      return selected;
    }
    if (selected.some((line) => line.quantity! > line.max)) {
      errors['itens'] = 'Uma quantidade excede o saldo permitido.';
      return selected;
    }

    const itemTotals = new Map<string, { total: number; max: number }>();
    for (const line of selected) {
      const key = line.idItemSolicitacao || line.idItemOperacaoOriginal || '';
      const current = itemTotals.get(key) || { total: 0, max: line.max };
      current.total += line.quantity ?? 0;
      itemTotals.set(key, current);
    }
    for (const [, { total, max }] of itemTotals) {
      if (total > max) {
        errors['itens'] = 'A soma das quantidades excede o saldo permitido.';
        return selected;
      }
    }

    if (selected.some((line) => line.serialRequired && !line.numeroSerie.trim())) {
      errors['itens'] = 'Informe a série de cada material seriado.';
    } else if (selected.some((line) => line.serialRequired && line.quantity !== 1)) {
      errors['itens'] = 'Material seriado deve ser registrado unitariamente.';
    } else {
      const serials = selected
        .filter((line) => line.serialRequired && line.numeroSerie.trim())
        .map((line) => line.numeroSerie.trim().toUpperCase());
      if (new Set(serials).size !== serials.length) {
        errors['itens'] = 'Não repita o mesmo número de série na requisição.';
      }
    }
    return selected;
  }

  private buildWithdrawalOverride(
    errors: Record<string, string>,
  ): Pick<ConfirmarEntregaTecnicaRequest, 'idColaboradorSaque' | 'saqueForaEquipe'> | null {
    const collaboratorId = this.withdrawalCollaboratorId();
    const outsideTeam = this.withdrawalOutsideTeam();
    const plannedCollaboratorId = this.plannedWithdrawalCollaboratorId();
    const plannedOutsideTeam = this.plannedWithdrawalOutsideTeam();
    const changed = collaboratorId !== plannedCollaboratorId || outsideTeam !== plannedOutsideTeam;

    if (!changed) return null;
    if (!collaboratorId) {
      errors['idColaboradorSaque'] = outsideTeam
        ? 'Selecione o colaborador responsável pelo saque fora da equipe.'
        : 'Selecione o colaborador responsável pelo saque.';
      return null;
    }

    return {
      idColaboradorSaque: collaboratorId,
      saqueForaEquipe: outsideTeam,
    };
  }

  private withdrawalName(id: string | null): string {
    if (!id) return 'Não definido';
    const collaborator = this.withdrawalCollaborators().find((item) => item.id === id);
    if (collaborator) return this.collaboratorName(collaborator);
    const member = this.withdrawalTeamMembers().find((item) => item.idColaborador === id);
    return member?.nomeColaborador ?? 'Colaborador planejado';
  }

  private collaboratorName(collaborator: ColaboradorResponse): string {
    const name =
      collaborator.nomeCompleto || `${collaborator.primeiroNome} ${collaborator.ultimoNome}`.trim();
    return name;
  }

  private execute(request: ActionRequest, key: string): Observable<unknown> {
    const id = this.detail()?.id ?? '';
    if (this.mode() === 'reserve')
      return this.service.registrarReserva(id, request as RegistrarReservaTecnicaRequest, key);
    if (this.mode() === 'deliver')
      return this.service.entregar(id, request as ConfirmarEntregaTecnicaRequest, key);
    if (this.mode() === 'return')
      return this.service.devolver(id, request as ConfirmarDevolucaoTecnicaRequest, key);
    if (this.mode() === 'reverse')
      return this.service.estornar(id, request as EstornarOperacaoTecnicaRequest, key);
    return this.service.encerrar(id, request as EncerrarSaldoTecnicoRequest, key);
  }

  private remaining(operation: OperacaoSolicitacaoTecnicaResponse, itemId: string): number {
    const original =
      (operation.itens ?? []).find((item: ItemOperacaoTecnicaResponse) => item.id === itemId)
        ?.quantidade ?? 0;
    const operacoes: OperacaoSolicitacaoTecnicaResponse[] = this.technical()?.operacoes ?? [];
    const reversed = operacoes
      .filter(
        (item: OperacaoSolicitacaoTecnicaResponse) =>
          item.tipo === 'ESTORNO' && item.idOperacaoOriginal === operation.id,
      )
      .flatMap((item: OperacaoSolicitacaoTecnicaResponse) => item.itens ?? [])
      .filter((item: ItemOperacaoTecnicaResponse) => item.idItemOperacaoOriginal === itemId)
      .reduce((sum: number, item: ItemOperacaoTecnicaResponse) => sum + item.quantidade, 0);
    return Math.max(0, original - reversed);
  }

  private successMessage(): string {
    return {
      reserve: 'Reserva externa registrada/atualizada com sucesso.',
      deliver: 'Entrega técnica confirmada.',
      return: 'Devolução técnica confirmada.',
      reverse: 'Estorno técnico confirmado.',
      close: 'Saldo remanescente encerrado.',
    }[this.mode()];
  }

  private intentSummary(request: ActionRequest): string {
    if ('itens' in request) return `${this.title()}: ${request.itens.length} item(ns).`;
    return `${this.title()}.`;
  }
  private hasOperationId(value: unknown): value is { idOperacao: string } {
    return Boolean(value && typeof value === 'object' && 'idOperacao' in value);
  }
  private clearSensitive(): void {
    this.signatureWarehouse.set('FISICA');
    this.signatureTeam.set('FISICA');
  }
  private parseDate(value: string): Date {
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day);
  }
  private toCivilDate(date: Date | null): string | null {
    return date
      ? `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
      : null;
  }
}
