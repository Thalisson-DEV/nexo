import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  output,
  signal,
  ViewChild,
} from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { StepperModule } from 'primeng/stepper';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { DatePickerModule } from 'primeng/datepicker';
import { forkJoin } from 'rxjs';

import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { RhService } from '../../../../core/services/rh.service';
import { NotificationService } from '../../../../design-system/feedback/notification/notification.service';
import {
  EstoqueResponse,
  MaterialResponse,
  SolicitacaoRequest,
  ItemRequest,
} from '../../../../core/models/almoxarifado.models';
import { ColaboradorResponse, EquipeResponse } from '../../../../core/models/rh.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { SaldoEstoqueDialogComponent } from '../../../../shared/components/saldo-estoque-dialog/saldo-estoque-dialog.component';
import { HelpDialogComponent } from '../../../../shared/components/help-dialog/help-dialog.component';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { RemoteLookup as CursorRemoteLookup } from '../../../../core/lookup/store/remote-lookup';
import { CesAutocompleteComponent } from '../../../../design-system/forms/controls/ces-autocomplete.component';
import { NavigationHistoryService, bindStepToUrl, createFormDraft } from '../../../../core/navigation';
import { LookupOption } from '../../../../core/lookup/models/lookup-option';

/** Rascunho do wizard (ver docs/state-restoration/GUIA.md). */
interface RascunhoSolicitacaoComercial {
  tipo: TipoSolicitacaoComercial;
  idEstoqueOrigem: string | null;
  idEquipe: string | null;
  idColaboradorSaque: string | null;
  colaboradorSaque: LookupOption<string>[];
  saqueForaEquipe: boolean;
  dataPrevista: Date | null;
  items: AddedCommercialItem[];
}

export interface AddedCommercialItem extends ItemRequest {
  material: MaterialResponse;
}

export type TipoSolicitacaoComercial = 'DIRETA';

@Component({
  selector: 'app-solicitacao-material-form',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    BreadcrumbModule,
    StepperModule,
    ButtonModule,
    SelectModule,
    InputNumberModule,
    TableModule,
    TooltipModule,
    InputTextModule,
    TextareaModule,
    ProgressSpinnerModule,
    DatePickerModule,
    DecimalPipe,
    SaldoEstoqueDialogComponent,
    HelpDialogComponent,
    CesAutocompleteComponent,
  ],
  templateUrl: './solicitacao-material-form.component.html',
  styleUrl: './solicitacao-material-form.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitacaoMaterialFormComponent implements OnInit {
  @ViewChild('helpDialog') helpDialog!: HelpDialogComponent;
  @ViewChild('saldoDialog') saldoDialog!: SaldoEstoqueDialogComponent;

  private readonly router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);
  private readonly almoxarifadoService = inject(AlmoxarifadoService);
  private readonly rhService = inject(RhService);
  private readonly lookupStore = inject(LookupStore);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly onSuccess = output<void>();
  readonly onCancel = output<void>();

  protected readonly breadcrumb: MenuItem[] = [
    { label: 'Almoxarifado' },
    { label: 'Solicitações Comerciais', routerLink: '/almoxarifado/solicitacoes' },
    { label: 'Nova Solicitação' },
  ];

  // State
  protected readonly activeStep = signal<1 | 2 | 3>(1);
  protected readonly loadingLookups = signal(true);
  protected readonly lookupError = signal('');
  protected readonly saving = signal(false);
  protected readonly fieldErrors = signal<Record<string, string>>({});

  // Form Fields (Step 1)
  protected readonly tipo = signal<TipoSolicitacaoComercial>('DIRETA');
  protected readonly idEstoqueOrigem = signal<string | null>(null);
  protected readonly idEquipe = signal<string | null>(null);
  protected readonly idColaboradorSaque = signal<string | null>(null);
  protected readonly saqueForaEquipe = signal(false);
  protected readonly dataPrevista = signal<Date | null>(null);

  // Lookups & Data
  protected readonly stocks = signal<EstoqueResponse[]>([]);
  protected readonly equipes = signal<EquipeResponse[]>([]);
  private readonly colaboradores = signal<ColaboradorResponse[]>([]);

  // Item Builder (Step 2)
  protected readonly selectedMaterialId = signal<string | null>(null);
  protected readonly selectedMaterial = signal<MaterialResponse | null>(null);
  protected readonly quantidade = signal<number | null>(null);
  protected readonly items = signal<AddedCommercialItem[]>([]);

  // Autocomplete Lookups
  private readonly materialLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchMaterials({ query, cursor }),
    selectedValues: () => {
      const id = this.selectedMaterialId();
      return id ? [id] : [];
    },
    destroyRef: this.destroyRef,
  });

  private readonly collaboratorLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () => {
      const id = this.idColaboradorSaque();
      return id ? [id] : [];
    },
    destroyRef: this.destroyRef,
  });

  protected readonly materials = this.materialLookup.items;
  protected readonly materialsLoading = this.materialLookup.loading;
  protected readonly materialsStatus = this.materialLookup.status;
  protected readonly materialsError = computed(() => {
    const error = this.materialLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });

  protected readonly collaboratorOptionsRemote = this.collaboratorLookup.items;
  protected readonly collaboratorsLoading = this.collaboratorLookup.loading;
  protected readonly collaboratorsStatus = this.collaboratorLookup.status;
  protected readonly collaboratorsError = computed(() => {
    const error = this.collaboratorLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });

  // Computed
  protected readonly physicalStocks = computed(() =>
    this.stocks().filter(
      (stock) => stock.ativo && stock.tipoEstoque?.nome?.toUpperCase() === 'FISICO',
    ),
  );

  protected readonly stockOptions = computed(() =>
    this.physicalStocks().map((s) => ({
      label: s.nomeIdentificador,
      value: s.id,
    })),
  );

  protected readonly equipeOptions = computed(() =>
    this.equipes().map((equipe) => ({
      label: `${equipe.codigo} — ${equipe.tipoEquipe?.nome || 'Equipe'}`,
      value: equipe.id,
    })),
  );

  protected readonly selectedEquipe = computed(() =>
    this.equipes().find((equipe) => equipe.id === this.idEquipe()),
  );

  protected readonly colaboradorSaqueOptions = computed(() => {
    if (this.saqueForaEquipe()) return this.collaboratorOptionsRemote();
    return (this.selectedEquipe()?.membros ?? [])
      .filter((membro) => membro.vinculoAtivo)
      .map((membro) => ({
        label: `${membro.nomeColaborador}${membro.funcao === 'RESPONSAVEL' ? ' (Responsável da Equipe)' : ''}`,
        value: membro.idColaborador,
      }));
  });

  protected readonly selectedEstoqueOrigem = computed(() =>
    this.physicalStocks().find((s) => s.id === this.idEstoqueOrigem()),
  );

  protected readonly selectedEstoqueDestino = computed(() =>
    this.stocks().find(
      (stock) =>
        stock.ativo &&
        stock.idEquipe === this.idEquipe() &&
        stock.tipoEstoque?.nome?.toUpperCase() === 'CAMPO',
    ),
  );

  protected readonly selectedColaboradorSaqueName = computed(() => {
    const id = this.idColaboradorSaque();
    if (!id) return null;
    const option = this.colaboradorSaqueOptions().find((item) => item.value === id);
    if (option) return option.label;
    const detail = this.colaboradores().find((item) => item.id === id);
    if (detail) return detail.nomeCompleto;
    return id.slice(0, 8);
  });

  protected readonly totalQuantidadeItens = computed(() =>
    this.items().reduce((acc, item) => acc + (item.quantidade || 0), 0),
  );

  private readonly passoUrl = bindStepToUrl(this.activeStep);
  // Só em memória: o rascunho carrega o nome do colaborador do saque (dado pessoal).
  private readonly rascunho = createFormDraft<RascunhoSolicitacaoComercial>({
    persist: false,
    read: () => ({
      tipo: this.tipo(),
      idEstoqueOrigem: this.idEstoqueOrigem(),
      idEquipe: this.idEquipe(),
      idColaboradorSaque: this.idColaboradorSaque(),
      colaboradorSaque: this.collaboratorOptionsRemote().filter(
        (opcao) => opcao.value === this.idColaboradorSaque(),
      ),
      saqueForaEquipe: this.saqueForaEquipe(),
      dataPrevista: this.dataPrevista(),
      items: this.items(),
    }),
    write: (valor) => {
      this.tipo.set(valor.tipo);
      this.idEstoqueOrigem.set(valor.idEstoqueOrigem);
      this.idEquipe.set(valor.idEquipe);
      if (valor.colaboradorSaque?.length) this.collaboratorLookup.seedSelected(valor.colaboradorSaque);
      this.idColaboradorSaque.set(valor.idColaboradorSaque);
      this.saqueForaEquipe.set(valor.saqueForaEquipe);
      this.dataPrevista.set(valor.dataPrevista);
      this.items.set(valor.items ?? []);
    },
  });

  ngOnInit(): void {
    this.loadLookups();
    const restaurado = this.rascunho.restore();
    this.passoUrl.restore(() => restaurado);
  }

  protected loadLookups(): void {
    this.loadingLookups.set(true);
    this.lookupError.set('');
    forkJoin({
      estoques: this.almoxarifadoService.listarEstoquesAtivos(),
      equipes: this.rhService.listarEquipes({ ativo: true, size: 1000, sort: 'codigo,asc' }),
    }).subscribe({
      next: ({ estoques, equipes }) => {
        this.stocks.set(estoques.content || []);
        this.equipes.set(equipes.content || []);
        this.loadingLookups.set(false);
      },
      error: (error) => {
        this.loadingLookups.set(false);
        const msg = getErrorMessage(error);
        this.lookupError.set(msg);
        this.notifications.error(msg, { title: 'Dados indisponíveis' });
      },
    });
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

  protected onMaterialChange(id: string | null): void {
    this.selectedMaterialId.set(id);
    if (!id) {
      this.selectedMaterial.set(null);
      return;
    }
    this.almoxarifadoService.buscarMaterialPorId(id).subscribe({
      next: (material) => {
        if (this.selectedMaterialId() === id) this.selectedMaterial.set(material);
      },
      error: () => {
        if (this.selectedMaterialId() === id) this.selectedMaterial.set(null);
      },
    });
  }

  protected onEquipeChange(id: string | null): void {
    this.idEquipe.set(id);
    if (!this.saqueForaEquipe()) {
      const ids = new Set(this.colaboradorSaqueOptions().map((item) => item.value));
      if (!ids.has(this.idColaboradorSaque() ?? '')) this.idColaboradorSaque.set(null);
    }
  }

  protected onSaqueForaEquipeChange(value: boolean): void {
    this.saqueForaEquipe.set(value);
    this.idColaboradorSaque.set(null);
  }

  protected searchColaboradores(query: string): void {
    if (this.saqueForaEquipe()) this.collaboratorLookup.search(query);
  }

  protected loadMoreColaboradores(): void {
    if (this.saqueForaEquipe()) this.collaboratorLookup.loadNext();
  }

  protected retryColaboradores(): void {
    if (this.saqueForaEquipe()) this.collaboratorLookup.retry();
  }

  protected onColaboradorSaqueChange(id: string | null): void {
    this.idColaboradorSaque.set(id);
    if (!id || !this.saqueForaEquipe()) return;
    this.rhService.buscarColaboradorPorId(id).subscribe({
      next: (colaborador) => {
        this.colaboradores.update((items) => [
          ...items.filter((item) => item.id !== colaborador.id),
          colaborador,
        ]);
        this.collaboratorLookup.seedSelected({
          value: colaborador.id,
          label: colaborador.nomeCompleto,
        });
      },
    });
  }

  protected addItem(): void {
    const material = this.selectedMaterial();
    const idMaterial = this.selectedMaterialId();
    const qtd = this.quantidade() || 1;

    if (!idMaterial || !material) {
      this.notifications.warning('Selecione um material válido para incluir.');
      return;
    }

    if (qtd <= 0) {
      this.notifications.warning('A quantidade deve ser maior que zero.');
      return;
    }

    if (this.items().some((i) => i.idMaterial === idMaterial)) {
      this.notifications.warning('O material já está na lista. Ajuste a quantidade na tabela.');
      this.selectedMaterial.set(null);
      this.selectedMaterialId.set(null);
      this.quantidade.set(null);
      return;
    }

    this.items.update((prev) => [
      ...prev,
      {
        idMaterial,
        quantidade: qtd,
        material,
      },
    ]);

    this.selectedMaterial.set(null);
    this.selectedMaterialId.set(null);
    this.quantidade.set(null);
    this.fieldErrors.update((errs) => {
      const { itens: _i, ...rest } = errs;
      return rest;
    });
  }

  protected removeItem(index: number): void {
    this.items.update((prev) => prev.filter((_, i) => i !== index));
  }

  protected updateItemQuantidade(index: number, novaQtd: number | null): void {
    if (novaQtd === null || novaQtd <= 0) return;
    this.items.update((prev) =>
      prev.map((item, i) => (i === index ? { ...item, quantidade: novaQtd } : item)),
    );
  }

  protected abrirSaldo(estoqueId: string | null): void {
    if (!estoqueId) return;
    const nome = this.getStockName(estoqueId);
    this.saldoDialog.show(estoqueId, nome);
  }

  protected getStockName(id?: string | null): string {
    if (!id) return '—';
    return this.stocks().find((s) => s.id === id)?.nomeIdentificador || id;
  }

  protected onHelp(): void {
    this.helpDialog.show();
  }

  protected fieldError(field: string): string | undefined {
    return this.fieldErrors()[field];
  }

  protected goToStep(step: 1 | 2 | 3): void {
    if (this.saving() || step === this.activeStep()) return;
    if (step < this.activeStep()) {
      this.activeStep.set(step);
      return;
    }
    if (step >= 2 && !this.validateStep1()) return;
    if (step >= 3 && !this.validateStep2()) return;
    this.activeStep.set(step);
  }

  protected nextStep(): void {
    if (this.activeStep() === 1) {
      if (this.validateStep1()) this.activeStep.set(2);
      return;
    }
    if (this.activeStep() === 2) {
      if (this.validateStep2()) this.activeStep.set(3);
    }
  }

  protected previousStep(): void {
    if (this.activeStep() === 3) this.activeStep.set(2);
    else if (this.activeStep() === 2) this.activeStep.set(1);
  }

  private validateStep1(): boolean {
    const errors: Record<string, string> = {};
    if (!this.idEstoqueOrigem()) {
      errors['idEstoqueOrigem'] = 'Selecione o estoque de origem.';
    }
    if (!this.idEquipe()) {
      errors['idEquipe'] = 'Selecione a equipe responsável pelo saque.';
    } else if (!this.selectedEstoqueDestino()) {
      errors['idEquipe'] =
        'A equipe selecionada não possui estoque de campo ativo. Regularize o cadastro da equipe.';
    }
    if (!this.dataPrevista()) {
      errors['dataPrevista'] = 'Informe a data prevista para o saque.';
    }

    this.fieldErrors.set(errors);
    if (Object.keys(errors).length > 0) {
      this.notifications.warning('Preencha os campos obrigatórios para avançar.');
      return false;
    }
    return true;
  }

  private validateStep2(): boolean {
    if (this.items().length === 0) {
      this.fieldErrors.set({ itens: 'Adicione pelo menos um material à solicitação.' });
      this.notifications.warning('Adicione pelo menos um material à solicitação.');
      return false;
    }
    this.fieldErrors.set({});
    return true;
  }

  protected onSave(): void {
    if (!this.validateStep1() || !this.validateStep2()) return;

    this.saving.set(true);
    const request: SolicitacaoRequest = {
      idEstoqueOrigem: this.idEstoqueOrigem()!,
      dataPrevista: this.formatCivilDate(this.dataPrevista()!),
      idEquipe: this.idEquipe()!,
      idColaboradorSaque: this.idColaboradorSaque(),
      saqueForaEquipe: this.saqueForaEquipe(),
      itens: this.items().map((i) => ({ idMaterial: i.idMaterial, quantidade: i.quantidade })),
    };

    const obs = this.almoxarifadoService.criarSolicitacaoDireta(request);

    obs.subscribe({
      next: () => {
        this.saving.set(false);
        this.notifications.success('Solicitação comercial criada com sucesso.');
        this.rascunho.clear();
        this.onSuccess.emit();
        void this.router.navigate(['/almoxarifado/solicitacoes']);
      },
      error: (err) => {
        this.saving.set(false);
        const message = getErrorMessage(err);
        this.notifications.error(message, { title: 'Não foi possível salvar a solicitação' });
      },
    });
  }

  protected cancel(): void {
    this.rascunho.clear();
    this.onCancel.emit();
    this.navegacao.back(['/almoxarifado/solicitacoes']);
  }

  private formatCivilDate(data: Date): string {
    const ano = data.getFullYear();
    const mes = String(data.getMonth() + 1).padStart(2, '0');
    const dia = String(data.getDate()).padStart(2, '0');
    return `${ano}-${mes}-${dia}`;
  }
}
