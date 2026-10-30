import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  OnChanges,
  OnInit,
  SimpleChanges,
  computed,
  inject,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { StepperModule } from 'primeng/stepper';
import {
  CesDialogShellComponent,
  NotificationService,
} from '@ces/ui';
import { CesAutocompleteComponent } from '@ces/ui/forms';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { MaterialResponse } from '../../../../core/models/almoxarifado.models';
import { ObraResponse } from '../../../../core/models/obras.models';
import {
  ColaboradorResponse,
  EquipeMembroResponse,
  EquipeResponse,
} from '../../../../core/models/rh.models';
import {
  DetalhesTecnicosSolicitacaoResponse,
  PlanejamentoSolicitacaoTecnicaResponse,
  PlanejarSolicitacaoTecnicaRequest,
  SolicitacaoMaterialDetalheResponse,
  TipoSolicitacaoTecnica,
} from '../../../../core/models/solicitacao-material-tecnica.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { RemoteLookup as CursorRemoteLookup } from '../../../../core/lookup/store/remote-lookup';
import { novaChaveIdempotencia } from '../../../../core/utils/idempotencia.util';
import {
  errosDeCampo,
  ehConflitoDeVersao,
  humanizarErrosDeCampo,
  validarOcorrencia,
} from '../../../../core/utils/solicitacao-tecnica-error.util';
import {
  possuiMovimentacaoAtendimento,
  separacaoLabel,
  separacaoTagClass,
  separacaoTone,
  statusLabel,
  statusTagClass,
  statusTone,
  tipoTecnicoBadgeClass,
  tipoTecnicoColorClass,
  tipoTecnicoContainerClass,
  tipoTecnicoDescricaoCompleta,
  tipoTecnicoEhAdicao,
  tipoTecnicoIcon,
  tipoTecnicoLabel,
} from '../solicitacao-tecnica-presentation';
import { CesButtonComponent } from '@ces/ui';

export interface PlanningLine {
  idItem: string | null;
  idMaterial: string;
  codigoMaterial: string;
  descricaoMaterial: string;
  tipoMovimentacao: TipoSolicitacaoTecnica;
  quantidade: number;
}

@Component({
  selector: 'app-solicitacao-tecnica-planejamento',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    SelectModule,
    InputNumberModule,
    InputTextModule,
    TextareaModule,
    DatePickerModule,
    TableModule,
    TooltipModule,
    ProgressSpinnerModule,
    StepperModule,
    CesDialogShellComponent,
    CesAutocompleteComponent,
    CesButtonComponent,
  ],
  templateUrl: './solicitacao-tecnica-planejamento.component.html',
  styleUrl: './solicitacao-tecnica-planejamento.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitacaoTecnicaPlanejamentoComponent implements OnInit, OnChanges {
  private readonly service = inject(SolicitacaoMaterialTecnicaService);
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly obras = inject(ObrasService);
  private readonly rh = inject(RhService);
  private readonly notifications = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly lookupStore = inject(LookupStore);
  private readonly destroyRef = inject(DestroyRef);

  readonly visible = model(false);
  readonly detail = input<SolicitacaoMaterialDetalheResponse | null>(null);
  readonly completed = output<PlanejamentoSolicitacaoTecnicaResponse>();
  readonly conflict = output<{ intent: string; currentVersion?: number }>();

  protected readonly activeStep = signal<number>(1);
  protected readonly teams = signal<EquipeResponse[]>([]);
  protected readonly teamMembers = signal<EquipeMembroResponse[]>([]);
  protected readonly obraInfo = signal<ObraResponse | null>(null);
  protected readonly loadingLookups = signal(true);
  protected readonly loadingTeamMembers = signal(false);
  protected readonly lookupError = signal('');
  protected readonly teamMembersError = signal('');
  protected readonly submitting = signal(false);
  protected readonly errors = signal<Record<string, string>>({});

  protected readonly warehouseId = signal<string | null>(null);
  protected readonly teamId = signal<string | null>(null);
  protected readonly saqueForaEquipe = signal(false);
  protected readonly idColaboradorSaque = signal<string | null>(null);
  protected readonly expectedDate = signal<Date | null>(null);
  protected readonly occurrence = signal('');
  protected readonly observation = signal('');
  protected readonly justification = signal('');
  protected readonly reserveNumber = signal('');
  protected readonly reserveDate = signal<Date | null>(null);

  protected readonly selectedMaterialId = signal<string | null>(null);
  protected readonly selectedMaterial = signal<MaterialResponse | null>(null);
  private readonly materialLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchMaterials({ query, cursor }),
    selectedValues: () => {
      const id = this.selectedMaterialId();
      return id ? [id] : [];
    },
    destroyRef: this.destroyRef,
  });
  private readonly stockLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) =>
      this.lookupStore.searchStocks({ query, cursor, types: ['OBRA', 'FISICO'] }),
    selectedValues: () => {
      const id = this.warehouseId();
      return id ? [id] : [];
    },
    destroyRef: this.destroyRef,
  });
  protected readonly warehouseOptions = this.stockLookup.items;
  protected readonly warehousesLoading = this.stockLookup.loading;
  protected readonly warehousesStatus = this.stockLookup.status;
  protected readonly warehousesError = computed(() => {
    const error = this.stockLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });
  protected readonly materials = this.materialLookup.items;
  protected readonly materialsLoading = this.materialLookup.loading;
  protected readonly materialsStatus = this.materialLookup.status;
  protected readonly materialsError = computed(() => {
    const error = this.materialLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });
  private readonly externalCollaboratorLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () => {
      const id = this.idColaboradorSaque();
      return this.saqueForaEquipe() && id ? [id] : [];
    },
    destroyRef: this.destroyRef,
  });
  protected readonly externalCollaborators = this.externalCollaboratorLookup.items;
  protected readonly externalCollaboratorsLoading = this.externalCollaboratorLookup.loading;
  protected readonly externalCollaboratorsStatus = this.externalCollaboratorLookup.status;
  protected readonly externalCollaboratorsError = computed(() => {
    const error = this.externalCollaboratorLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });
  protected readonly externalCollaborator = signal<ColaboradorResponse | null>(null);
  protected readonly itemQuantity = signal<number | null>(null);
  protected readonly lines = signal<PlanningLine[]>([]);

  protected readonly technical = computed<DetalhesTecnicosSolicitacaoResponse>(
    () => this.detail()?.tecnico ?? ({} as any),
  );

  protected readonly isInitialPlanning = computed(() => {
    const technical = this.technical();
    return technical.etapaOperacional
      ? technical.etapaOperacional === 'AGUARDANDO_PLANEJAMENTO'
      : !technical.dataPrevista || technical.statusSeparacao === 'AGUARDANDO_AGENDAMENTO';
  });

  protected readonly hasDeliveryMovement = computed(() =>
    possuiMovimentacaoAtendimento(this.detail()),
  );

  protected readonly dialogTitle = computed(() =>
    this.isInitialPlanning()
      ? 'Planejamento da Solicitação Técnica'
      : 'Replanejamento da Solicitação Técnica',
  );

  protected readonly fixedType = computed(() => this.technical().tipoTecnico);
  protected readonly fixedTypeLabel = computed(() => tipoTecnicoLabel(this.fixedType()));
  protected readonly fixedTypeIcon = computed(() => tipoTecnicoIcon(this.fixedType()));
  protected readonly isAddition = computed(() => tipoTecnicoEhAdicao(this.fixedType()));
  protected readonly occurrenceRequired = computed(() =>
    ['CORRETIVA', 'PREVENTIVA'].includes(this.technical().tipoObra),
  );

  protected readonly teamOptions = computed(() =>
    this.teams().map((team) => ({
      label: team.tipoEquipe ? `${team.codigo} · ${team.tipoEquipe.nome}` : team.codigo,
      value: team.id,
    })),
  );

  protected readonly collaboratorSaqueOptions = computed(() => {
    if (this.saqueForaEquipe()) {
      return this.externalCollaborators();
    }

    if (!this.teamId()) return [];

    return this.teamMembers()
      .filter((member) => member.vinculoAtivo)
      .map((member) => ({
        label: `${member.nomeColaborador}${member.funcao === 'RESPONSAVEL' ? ' (Responsável da equipe)' : ''}`,
        value: member.idColaborador,
      }));
  });

  protected readonly saqueResponsavelRequired = computed(
    () => this.saqueForaEquipe(),
  );

  protected readonly saqueResponsavelDescription = computed(() => {
    if (this.saqueForaEquipe()) return 'Selecione qualquer colaborador ativo.';
    if (this.teamId()) return 'Membro da equipe selecionada (opcional nesta etapa).';
    return 'Selecione uma equipe para listar os membros ou ative o saque fora da equipe.';
  });

  protected readonly selectedWarehouseName = computed(
    () =>
      this.warehouseOptions().find((warehouse) => warehouse.value === this.warehouseId())?.label ??
      'Não selecionado',
  );

  protected readonly selectedTeam = computed(() =>
    this.teams().find((t) => t.id === this.teamId()),
  );

  protected readonly selectedTeamName = computed(() => {
    const team = this.selectedTeam();
    if (!team) return 'Não definida';
    return team.tipoEquipe ? `${team.codigo} · ${team.tipoEquipe.nome}` : team.codigo;
  });

  protected readonly selectedColaboradorSaqueName = computed(() => {
    const id = this.idColaboradorSaque();
    if (!id) return 'Não selecionado';
    return (
      this.collaboratorSaqueOptions().find((option) => option.value === id)?.label ??
      'Não disponível'
    );
  });

  protected readonly obraDisplay = computed(() => {
    const obra = this.obraInfo();
    if (obra) return `Obra ${obra.numero} — ${obra.titulo}`;
    return this.technical()?.tipoObra ? `Obra (${this.technical().tipoObra})` : 'Obra vinculada';
  });

  protected readonly formattedExpectedDate = computed(() => {
    const value = this.expectedDate();
    return value ? value.toLocaleDateString('pt-BR') : 'Não informada';
  });

  protected readonly additionCount = computed(
    () => this.lines().filter((line) => tipoTecnicoEhAdicao(line.tipoMovimentacao)).length,
  );
  protected readonly returnCount = computed(
    () => this.lines().filter((line) => !tipoTecnicoEhAdicao(line.tipoMovimentacao)).length,
  );

  protected readonly showAdditionalInfo = signal(false);
  protected readonly statusLabel = statusLabel;
  protected readonly statusTone = statusTone;
  protected readonly statusTagClass = statusTagClass;
  protected readonly separacaoLabel = separacaoLabel;
  protected readonly separacaoTone = separacaoTone;
  protected readonly separacaoTagClass = separacaoTagClass;
  protected readonly tipoTecnicoIcon = tipoTecnicoIcon;
  protected readonly tipoTecnicoLabel = tipoTecnicoLabel;
  protected readonly tipoTecnicoBadgeClass = tipoTecnicoBadgeClass;
  protected readonly tipoTecnicoColorClass = tipoTecnicoColorClass;
  protected readonly tipoTecnicoContainerClass = tipoTecnicoContainerClass;
  protected readonly tipoTecnicoDescricaoCompleta = tipoTecnicoDescricaoCompleta;
  protected readonly tipoTecnicoEhAdicao = tipoTecnicoEhAdicao;

  protected readonly totalMaterialQuantity = computed(() =>
    this.lines().reduce((acc, line) => acc + (line.quantidade || 0), 0),
  );

  ngOnInit(): void {
    this.loadLookups(true);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['detail'] && !changes['detail'].firstChange) {
      this.resetSnapshot();
    }
  }

  protected retryLookups(): void {
    this.loadLookups(false);
  }

  protected searchMaterials(query: string): void {
    this.materialLookup.search(query);
  }

  protected loadMoreMaterials(): void {
    this.materialLookup.loadNext();
  }

  protected retryMaterials(): void {
    this.materialLookup.retry();
  }

  protected searchWarehouses(query: string): void {
    this.stockLookup.search(query);
  }

  protected loadMoreWarehouses(): void {
    this.stockLookup.loadNext();
  }

  protected retryWarehouses(): void {
    this.stockLookup.retry();
  }

  protected onMaterialChange(id: string | null): void {
    this.selectedMaterialId.set(id);
    this.selectedMaterial.set(null);
    if (!id) return;

    this.almoxarifado.buscarMaterialPorId(id).subscribe({
      next: (material) => {
        if (this.selectedMaterialId() === id) this.selectedMaterial.set(material);
      },
      error: () => {
        if (this.selectedMaterialId() === id) this.selectedMaterial.set(null);
      },
    });
  }

  protected searchExternalCollaborators(query: string): void {
    this.externalCollaboratorLookup.search(query);
  }

  protected loadMoreExternalCollaborators(): void {
    this.externalCollaboratorLookup.loadNext();
  }

  protected retryExternalCollaborators(): void {
    this.externalCollaboratorLookup.retry();
  }

  protected onTeamChange(id: string | null): void {
    this.teamMembersRequestId++;
    this.teamId.set(id);
    this.teamMembers.set([]);
    this.teamMembersError.set('');
    this.loadingTeamMembers.set(false);
    this.idColaboradorSaque.set(null);
    this.clearFieldError('idColaboradorSaque');

    if (!this.saqueForaEquipe() && id) this.loadTeamMembers(id);
  }

  protected onSaqueForaEquipeChange(value: boolean): void {
    this.saqueForaEquipe.set(value);
    this.clearFieldError('idColaboradorSaque');

    if (value) {
      this.teamMembersRequestId++;
      this.loadingTeamMembers.set(false);
      this.teamMembers.set([]);
      this.teamMembersError.set('');
      if (this.idColaboradorSaque()) this.hydrateExternalCollaborator(this.idColaboradorSaque()!);
      return;
    }

    if (this.teamId()) {
      this.loadTeamMembers(this.teamId()!);
    } else {
      this.idColaboradorSaque.set(null);
    }
  }

  protected onColaboradorSaqueChange(id: string | null): void {
    this.idColaboradorSaque.set(id);
    if (this.saqueForaEquipe()) this.hydrateExternalCollaborator(id);
    this.clearFieldError('idColaboradorSaque');
  }

  protected setQuickDate(days: number): void {
    const date = new Date();
    date.setDate(date.getDate() + days);
    date.setHours(0, 0, 0, 0);
    this.expectedDate.set(date);
  }

  protected goToStep(step: number): void {
    if (step > this.activeStep()) {
      if (this.activeStep() === 1 && !this.validateStep1()) return;
      if (this.activeStep() === 2 && !this.validateStep2()) return;
    }
    this.activeStep.set(step);
  }

  protected nextStep(): void {
    if (this.activeStep() === 1) {
      if (this.validateStep1()) this.activeStep.set(2);
    } else if (this.activeStep() === 2) {
      if (this.validateStep2()) this.activeStep.set(3);
    }
  }

  protected previousStep(): void {
    if (this.activeStep() > 1) {
      this.activeStep.update((s) => s - 1);
    }
  }

  protected addLine(): void {
    const materialId = this.selectedMaterialId();
    const quantity = this.itemQuantity();

    if (!materialId || quantity === null || quantity < 0.001) {
      this.notifications.warning(
        'Escolha um material e informe uma quantidade maior ou igual a 0,001.',
      );
      return;
    }

    if (this.lines().some((line) => line.idMaterial === materialId)) {
      this.notifications.warning(
        'Este material já está na lista. Ajuste a quantidade na própria linha.',
      );
      return;
    }

    const material = this.selectedMaterial();
    if (!material || material.id !== materialId) {
      this.notifications.warning('Aguarde o carregamento do material selecionado.');
      return;
    }

    this.lines.update((lines) => [
      ...lines,
      {
        idItem: null,
        idMaterial: material.id,
        codigoMaterial: material.codigo,
        descricaoMaterial: material.descricao,
        tipoMovimentacao: this.fixedType(),
        quantidade: quantity,
      },
    ]);
    this.selectedMaterialId.set(null);
    this.selectedMaterial.set(null);
    this.itemQuantity.set(null);
    this.clearFieldError('itens');
  }

  protected updateQuantity(index: number, quantity: number | null): void {
    this.lines.update((lines) =>
      lines.map((line, position) =>
        position === index ? { ...line, quantidade: quantity ?? 0 } : line,
      ),
    );
  }

  protected lineQuantityError(line: PlanningLine): string | undefined {
    return line.quantidade < 0.001 ? 'Informe ao menos 0,001.' : undefined;
  }

  protected removeLine(index: number): void {
    this.lines.update((lines) => lines.filter((_, position) => position !== index));
  }

  protected validateStep1(): boolean {
    const technical = this.technical();
    const errors: Record<string, string> = { ...this.errors() };
    delete errors['idAlmoxarifadoAtendimento'];
    delete errors['ocorrencia'];
    delete errors['observacao'];
    delete errors['justificativa'];
    delete errors['idColaboradorSaque'];
    delete errors['reservaNumero'];

    const occurrenceValidation = validarOcorrencia(this.occurrence(), technical.tipoObra);

    if (!this.warehouseId()) {
      errors['idAlmoxarifadoAtendimento'] = 'Selecione o almoxarifado de atendimento.';
    }
    if (!occurrenceValidation.valida && occurrenceValidation.erro) {
      errors['ocorrencia'] = occurrenceValidation.erro;
    }
    if (this.reserveNumber().trim() && !/^\d{1,20}$/.test(this.reserveNumber().trim())) {
      errors['reservaNumero'] = 'O número da reserva deve conter até 20 dígitos numéricos.';
    }
    if (this.observation().trim().length > 1000) {
      errors['observacao'] = 'A observação deve ter no máximo 1.000 caracteres.';
    }
    this.validateSaqueResponsavel(errors);
    if (!this.isInitialPlanning() && !this.justification().trim()) {
      errors['justificativa'] = 'Informe a justificativa do replanejamento.';
    } else if (this.justification().trim().length > 500) {
      errors['justificativa'] = 'A justificativa deve ter no máximo 500 caracteres.';
    }

    this.errors.set(errors);
    if (
      errors['idAlmoxarifadoAtendimento'] ||
      errors['ocorrencia'] ||
      errors['reservaNumero'] ||
      errors['observacao'] ||
      errors['justificativa'] ||
      errors['idColaboradorSaque']
    ) {
      this.notifications.warning('Revise os campos obrigatórios antes de avançar.');
      return false;
    }
    return true;
  }

  protected validateStep2(): boolean {
    const errors: Record<string, string> = { ...this.errors() };
    delete errors['itens'];
    if (!this.lines().length) {
      errors['itens'] = 'Adicione ao menos um material ao planejamento.';
    } else if (this.lines().some((line) => line.quantidade < 0.001)) {
      errors['itens'] = 'Todas as quantidades devem ser maiores ou iguais a 0,001.';
    }
    this.errors.set(errors);
    if (errors['itens']) {
      this.notifications.warning(errors['itens']);
      return false;
    }
    return true;
  }

  protected submit(): void {
    if (this.hasDeliveryMovement()) {
      this.notifications.warning(
        'Replanejamento bloqueado: a solicitação já possui movimentação de entrega registrada.',
      );
      return;
    }
    if (!this.validateStep1() || !this.validateStep2()) return;
    const request = this.buildRequest();
    if (!request || this.submitting()) return;

    this.submitting.set(true);
    const id = this.detail()?.id ?? '';
    this.service.substituirPlanejamento(id, request).subscribe({
      next: (response) => {
        if (!this.technical().reserva && this.reserveNumber().trim()) {
          const num = this.reserveNumber().trim();
          const dt = this.toCivilDate(this.reserveDate()) ?? this.toCivilDate(new Date())!;
          this.service
            .registrarReserva(
              id,
              {
                versaoEsperada: response.solicitacaoAtualizada.versao,
                numero: num,
                dataCriacao: dt,
              },
              novaChaveIdempotencia(),
            )
            .subscribe({
              next: () => {
                this.service.buscarPorId(id).subscribe({
                  next: (fresco) => {
                    this.submitting.set(false);
                    this.notifications.success(
                      this.isInitialPlanning()
                        ? 'Planejamento e reserva registrados com sucesso.'
                        : 'Replanejamento e reserva registrados com sucesso.',
                    );
                    this.completed.emit({
                      ...response,
                      solicitacaoAtualizada: fresco,
                    });
                    this.visible.set(false);
                    this.cdr.markForCheck();
                  },
                  error: () => {
                    this.submitting.set(false);
                    this.notifications.success(
                      this.isInitialPlanning()
                        ? 'Planejamento e reserva registrados com sucesso.'
                        : 'Replanejamento e reserva registrados com sucesso.',
                    );
                    this.completed.emit(response);
                    this.visible.set(false);
                    this.cdr.markForCheck();
                  },
                });
              },
              error: (resError) => {
                this.submitting.set(false);
                this.notifications.warning(
                  'Planejamento salvo, mas houve falha ao registrar a reserva: ' +
                    getErrorMessage(resError),
                );
                this.completed.emit(response);
                this.visible.set(false);
                this.cdr.markForCheck();
              },
            });
        } else {
          this.submitting.set(false);
          this.notifications.success(
            this.isInitialPlanning()
              ? 'Planejamento salvo com sucesso.'
              : 'Replanejamento salvo com sucesso.',
          );
          this.completed.emit(response);
          this.visible.set(false);
          this.cdr.markForCheck();
        }
      },
      error: (error) => {
        this.submitting.set(false);
        if (ehConflitoDeVersao(error)) {
          this.visible.set(false);
          this.conflict.emit({
            intent: this.intentSummary(),
            currentVersion: error?.error?.versaoAtual,
          });
          this.cdr.markForCheck();
          return;
        }

        this.errors.set(humanizarErrosDeCampo(errosDeCampo(error)));
        this.notifications.error(getErrorMessage(error), {
          title: 'Não foi possível salvar o planejamento',
        });
        this.cdr.markForCheck();
      },
    });
  }

  protected close(): void {
    if (!this.submitting()) {
      this.activeStep.set(1);
      this.visible.set(false);
    }
  }

  protected fieldError(name: string): string | undefined {
    return this.errors()[name];
  }

  private loadLookups(resetSnapshot: boolean): void {
    this.loadingLookups.set(true);
    this.lookupError.set('');
    forkJoin({
      teams: this.rh.listarEquipes({ ativo: true, size: 1000, sort: 'codigo,asc' }),
    }).subscribe({
      next: ({ teams }) => {
        this.teams.set(teams.content);
        this.loadingLookups.set(false);
        if (resetSnapshot) this.resetSnapshot();
        else if (this.teamId() && !this.saqueForaEquipe()) this.loadTeamMembers(this.teamId()!);
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.loadingLookups.set(false);
        this.lookupError.set(getErrorMessage(error));
        this.notifications.error(getErrorMessage(error), {
          title: 'Dados do planejamento indisponíveis',
        });
        this.cdr.markForCheck();
      },
    });
  }

  private clearFieldError(name: string): void {
    if (!this.errors()[name]) return;
    this.errors.update((errors) => {
      const next = { ...errors };
      delete next[name];
      return next;
    });
  }

  private resetSnapshot(): void {
    const technical = this.technical();
    this.activeStep.set(1);
    this.errors.set({});
    this.occurrence.set(technical.ocorrencia?.codigo ?? '');

    const defaultWarehouseId =
      technical.idAlmoxarifadoAtendimento ??
      this.detail()?.estoqueDestino?.id ??
      this.detail()?.estoqueOrigem?.id ??
      null;
    this.warehouseId.set(defaultWarehouseId);
    if (defaultWarehouseId) {
      const existingStock =
        this.detail()?.estoqueDestino?.id === defaultWarehouseId
          ? this.detail()?.estoqueDestino
          : this.detail()?.estoqueOrigem?.id === defaultWarehouseId
            ? this.detail()?.estoqueOrigem
            : null;
      if (existingStock?.nomeIdentificador) {
        this.stockLookup.seedSelected({
          value: existingStock.id,
          label: existingStock.nomeIdentificador,
        });
      } else {
        this.almoxarifado.buscarEstoquePorId(defaultWarehouseId).subscribe({
          next: (stock) => {
            this.stockLookup.seedSelected({ value: stock.id, label: stock.nomeIdentificador });
            this.cdr.markForCheck();
          },
        });
      }
    }

    this.teamId.set(technical.idEquipe ?? null);
    this.saqueForaEquipe.set(technical.saqueForaEquipe ?? false);
    this.idColaboradorSaque.set(technical.idColaboradorSaque ?? null);
    this.teamMembers.set([]);
    this.teamMembersError.set('');
    this.expectedDate.set(this.parseCivilDate(technical.dataPrevista));
    this.reserveNumber.set(technical.reserva?.numero ?? '');
    this.reserveDate.set(this.parseCivilDate(technical.reserva?.dataCriacao));
    this.observation.set(technical.observacao ?? '');
    this.showAdditionalInfo.set(!!(technical.reserva?.numero || technical.observacao));
    this.justification.set('');
    this.selectedMaterialId.set(null);
    this.selectedMaterial.set(null);
    this.itemQuantity.set(null);
    this.lines.set(
      (this.detail()?.itens ?? []).map((item) => ({
        idItem: item.id,
        idMaterial: item.material.id,
        codigoMaterial: item.material.codigo,
        descricaoMaterial: item.material.descricao,
        tipoMovimentacao: item.tipoMovimentacao,
        quantidade: item.quantidade,
      })),
    );

    if (technical.idObra) {
      this.obras.buscarObraPorId(technical.idObra).subscribe({
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

    if (this.teamId() && !this.saqueForaEquipe()) this.loadTeamMembers(this.teamId()!);
    if (this.saqueForaEquipe() && this.idColaboradorSaque()) {
      this.hydrateExternalCollaborator(this.idColaboradorSaque()!);
    }
  }

  private hydrateExternalCollaborator(id: string | null): void {
    this.externalCollaborator.set(null);
    if (!id) return;
    this.rh.buscarColaboradorPorId(id).subscribe({
      next: (collaborator) => {
        this.externalCollaborator.set(collaborator);
        const label =
          collaborator.nomeCompleto ||
          `${collaborator.primeiroNome ?? ''} ${collaborator.ultimoNome ?? ''}`.trim();
        this.externalCollaboratorLookup.seedSelected({ value: collaborator.id, label });
        this.cdr.markForCheck();
      },
      error: () => this.externalCollaborator.set(null),
    });
  }

  private buildRequest(): PlanejarSolicitacaoTecnicaRequest | null {
    const technical = this.technical();
    const occurrenceValidation = validarOcorrencia(this.occurrence(), technical.tipoObra);

    if (!this.warehouseId()) return null;
    if (!occurrenceValidation.valida && occurrenceValidation.erro) return null;
    if (!this.isInitialPlanning() && !this.justification().trim()) return null;
    if (!this.lines().length) return null;

    return {
      versaoEsperada: technical.versao,
      idObra: technical.idObra,
      ocorrencia: occurrenceValidation.normalizada,
      idAlmoxarifadoAtendimento: this.warehouseId()!,
      idEquipe: this.teamId(),
      idColaboradorSaque: this.idColaboradorSaque(),
      saqueForaEquipe: this.saqueForaEquipe(),
      dataPrevista: this.toCivilDate(this.expectedDate()),
      observacao: this.observation().trim() || null,
      justificativa: this.justification().trim() || null,
      itens: this.lines().map(({ idItem, idMaterial, tipoMovimentacao, quantidade }) => ({
        idItem,
        idMaterial,
        tipoMovimentacao,
        quantidade,
      })),
    };
  }

  private intentSummary(): string {
    return `${this.dialogTitle()} (${this.lines().length} material(is), versão ${this.detail()?.versao ?? 1}).`;
  }

  private validateSaqueResponsavel(errors: Record<string, string>): void {
    const teamId = this.teamId();
    const collaboratorId = this.idColaboradorSaque();

    if (!this.saqueForaEquipe() && teamId) {
      if (this.loadingTeamMembers()) {
        errors['idColaboradorSaque'] = 'Aguarde o carregamento dos membros ativos da equipe.';
        return;
      }
      if (this.teamMembersError()) {
        errors['idColaboradorSaque'] = this.teamMembersError();
        return;
      }
    }

    const valid = this.collaboratorSaqueOptions().some((option) => option.value === collaboratorId);
    if (this.saqueResponsavelRequired() && !collaboratorId) {
      errors['idColaboradorSaque'] = 'Selecione o responsável pelo saque.';
    } else if (collaboratorId && !valid) {
      errors['idColaboradorSaque'] = this.saqueForaEquipe()
        ? 'Selecione um colaborador ativo.'
        : 'Selecione um membro ativo da equipe.';
    }
  }

  private loadTeamMembers(teamId: string): void {
    const requestId = ++this.teamMembersRequestId;
    this.loadingTeamMembers.set(true);
    this.teamMembersError.set('');

    this.rh.listarMembrosAtivos(teamId).subscribe({
      next: (members) => {
        if (requestId !== this.teamMembersRequestId || this.teamId() !== teamId) return;
        this.teamMembers.set(members.filter((member) => member.vinculoAtivo));
        this.loadingTeamMembers.set(false);
        if (
          !this.collaboratorSaqueOptions().some(
            (option) => option.value === this.idColaboradorSaque(),
          )
        ) {
          this.idColaboradorSaque.set(null);
        }
        this.clearFieldError('idColaboradorSaque');
        this.cdr.markForCheck();
      },
      error: (error) => {
        if (requestId !== this.teamMembersRequestId || this.teamId() !== teamId) return;
        this.loadingTeamMembers.set(false);
        this.teamMembersError.set(getErrorMessage(error));
        this.teamMembers.set([]);
        this.idColaboradorSaque.set(null);
        this.cdr.markForCheck();
      },
    });
  }

  private teamMembersRequestId = 0;

  private parseCivilDate(value?: string | null): Date | null {
    if (!value) return null;
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day);
  }

  private toCivilDate(value: Date | null): string | null {
    return value
      ? `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
      : null;
  }
}
