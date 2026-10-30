import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { StepperModule } from 'primeng/stepper';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { NotificationService } from '../../../../design-system/feedback/notification/notification.service';
import { CesAutocompleteComponent } from '../../../../design-system/forms/controls/ces-autocomplete.component';
import { CesBooleanControlComponent } from '../../../../design-system/forms/controls/ces-boolean-control.component';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { MaterialResponse } from '../../../../core/models/almoxarifado.models';
import { ObraResponse, ObraResumoResponse } from '../../../../core/models/obras.models';
import { ColaboradorResponse, EquipeResponse } from '../../../../core/models/rh.models';
import {
  CodigoOperacaoTecnica,
  CodigoTipoTecnico,
  CriarSolicitacaoTecnicaRequest,
  ItemIntencaoSolicitacaoTecnica,
  OcorrenciaTecnica,
  ProjetoRelacionadoSolicitacao,
  SolicitacaoTecnicaCriada,
  TipoObraSolicitacao,
  TipoSolicitacaoTecnica,
  obterCodigoTecnico,
} from '../../../../core/models/solicitacao-material-tecnica.models';
import { novaChaveIdempotencia } from '../../../../core/utils/idempotencia.util';
import {
  ehFalhaDeTransporte,
  errosDeCampo,
  mensagemErroSolicitacao,
  normalizeOcorrencia,
  validarOcorrencia,
} from '../../../../core/utils/solicitacao-tecnica-error.util';
import { getErrorMessage } from '../../../../core/models/shared.models';
import {
  tipoTecnicoDescricaoCompleta,
  tipoTecnicoEhAdicao,
  tipoTecnicoIcon,
  tipoTecnicoLabel,
  tipoTecnicoNome,
} from '../solicitacao-tecnica-presentation';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { RemoteLookup as CursorRemoteLookup } from '../../../../core/lookup/store/remote-lookup';
import { LookupOption } from '../../../../core/lookup/models/lookup-option';
import { NavigationHistoryService, bindStepToUrl, createFormDraft } from '../../../../core/navigation';

/** Rascunho do wizard (ver docs/state-restoration/GUIA.md). */
interface RascunhoSolicitacaoTecnica {
  idObra: string | null;
  ocorrencia: string;
  idAlmoxarifado: string | null;
  almoxarifado: LookupOption<string>[];
  idEquipe: string | null;
  saqueForaEquipe: boolean;
  idColaboradorSaque: string | null;
  colaboradorSaque: LookupOption<string>[];
  dataPrevista: Date | null;
  observacao: string;
  tipoMovimentacao: TipoSolicitacaoTecnica;
  itens: AddedMaterialItem[];
}

export interface AddedMaterialItem extends ItemIntencaoSolicitacaoTecnica {
  codigo: string;
  descricao: string;
  tipoMovimentacao: TipoSolicitacaoTecnica;
  codigoOperacao: CodigoOperacaoTecnica;
}

@Component({
  selector: 'app-solicitacao-tecnica-form',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    BreadcrumbModule,
    ButtonModule,
    SelectModule,
    InputNumberModule,
    TableModule,
    TooltipModule,
    InputTextModule,
    TextareaModule,
    DatePickerModule,
    StepperModule,
    ProgressSpinnerModule,
    CesBooleanControlComponent,
    CesAutocompleteComponent,
  ],
  templateUrl: './solicitacao-tecnica-form.component.html',
  styleUrl: './solicitacao-tecnica-form.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitacaoTecnicaFormComponent implements OnInit {
  private readonly service = inject(SolicitacaoMaterialTecnicaService);
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly obrasService = inject(ObrasService);
  private readonly rhService = inject(RhService);
  private readonly notifications = inject(NotificationService);
  private readonly lookupStore = inject(LookupStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);
  private readonly route = inject(ActivatedRoute);
  private readonly collaboratorLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () => {
      const id = this.idColaboradorSaque();
      return id ? [id] : [];
    },
    destroyRef: this.destroyRef,
  });
  private readonly stockLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) =>
      this.lookupStore.searchStocks({ query, cursor, types: ['OBRA', 'FISICO'] }),
    selectedValues: () => {
      const id = this.idAlmoxarifado();
      return id ? [id] : [];
    },
    destroyRef: this.destroyRef,
  });

  protected readonly modo = signal<'adicao' | 'devolucao'>('adicao');
  protected readonly breadcrumb = computed<MenuItem[]>(() => [
    { label: 'Almoxarifado' },
    { label: 'Solicitações Técnicas', routerLink: '/almoxarifado/solicitacoes-tecnicas' },
    { label: this.modo() === 'devolucao' ? 'Nova Devolução' : 'Nova Solicitação' },
  ]);

  protected readonly loadingLookups = signal(true);
  protected readonly activeStep = signal<1 | 2 | 3 | 4>(1);
  protected readonly lookupError = signal('');
  protected readonly submitting = signal(false);
  protected readonly fieldErrors = signal<Record<string, string>>({});
  protected readonly retryRequest = signal<CriarSolicitacaoTecnicaRequest | null>(null);
  protected readonly retryKey = signal<string | null>(null);
  protected readonly solicitacoesCriadas = signal<SolicitacaoTecnicaCriada[]>([]);

  // Lookups data
  protected readonly obras = signal<ObraResumoResponse[]>([]);
  protected readonly equipes = signal<EquipeResponse[]>([]);
  protected readonly equipesElegiveis = signal<EquipeResponse[]>([]);
  protected readonly colaboradores = signal<ColaboradorResponse[]>([]);
  protected readonly projetosRelacionados = signal<ProjetoRelacionadoSolicitacao[]>([]);
  protected readonly ocorrencias = signal<OcorrenciaTecnica[]>([]);
  protected readonly obraDetalhe = signal<ObraResponse | null>(null);

  // Form Fields (Step 1: Obra e Atendimento)
  protected readonly idObra = signal<string | null>(null);
  protected readonly ocorrencia = signal('');
  protected readonly idAlmoxarifado = signal<string | null>(null);
  protected readonly idEquipe = signal<string | null>(null);
  protected readonly saqueForaEquipe = signal<boolean>(false);
  protected readonly idColaboradorSaque = signal<string | null>(null);
  protected readonly dataPrevista = signal<Date | null>(null);
  protected readonly observacao = signal('');

  // Item Builder (Step 2: Materiais)
  protected readonly idMaterial = signal<string | null>(null);
  protected readonly selectedMaterial = signal<MaterialResponse | null>(null);
  private readonly materialLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchMaterials({ query, cursor }),
    selectedValues: () => {
      const id = this.idMaterial();
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
  protected readonly collaboratorOptions = this.collaboratorLookup.items;
  protected readonly collaboratorLoading = this.collaboratorLookup.loading;
  protected readonly collaboratorStatus = this.collaboratorLookup.status;
  protected readonly collaboratorError = computed(() => {
    const error = this.collaboratorLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });
  protected readonly stockOptions = this.stockLookup.items;
  protected readonly stockLoading = this.stockLookup.loading;
  protected readonly stockStatus = this.stockLookup.status;
  protected readonly stockError = computed(() => {
    const error = this.stockLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });
  protected readonly tipoMovimentacao = signal<TipoSolicitacaoTecnica>('ADICAO_COMUM');
  protected readonly quantidade = signal<number | null>(null);
  protected readonly itens = signal<AddedMaterialItem[]>([]);

  // Computed properties
  protected readonly selectedObra = computed(() =>
    this.obras().find((obra) => obra.id === this.idObra()),
  );
  protected readonly selectedAlmoxarifado = computed(() =>
    this.stockOptions().find((estoque) => estoque.value === this.idAlmoxarifado()),
  );
  protected readonly selectedEquipe = computed(() =>
    this.equipes().find((equipe) => equipe.id === this.idEquipe()),
  );
  protected readonly tipoObra = computed<TipoObraSolicitacao | undefined>(
    () => this.obraDetalhe()?.tipoObra ?? this.selectedObra()?.tipoObra,
  );
  protected readonly ocorrenciaObrigatoria = computed(
    () => this.tipoObra() === 'CORRETIVA' || this.tipoObra() === 'PREVENTIVA',
  );

  protected readonly tiposPresentes = computed(() => {
    const tipos = new Set<TipoSolicitacaoTecnica>();
    for (const item of this.itens()) {
      tipos.add(item.tipoMovimentacao);
    }
    return Array.from(tipos);
  });

  protected readonly contagemPorTipo = computed(() => {
    const contagem: Record<string, number> = {};
    for (const item of this.itens()) {
      const codigo = item.codigoOperacao;
      contagem[codigo] = (contagem[codigo] || 0) + 1;
    }
    return contagem;
  });

  protected readonly additionItems = computed(
    () => this.itens().filter((item) => item.tipoMovimentacao.startsWith('ADICAO')).length,
  );
  protected readonly returnItems = computed(
    () => this.itens().filter((item) => item.tipoMovimentacao.startsWith('DEVOLUCAO')).length,
  );

  protected readonly obraOptions = computed(() =>
    this.obras().map((obra) => ({ label: `${obra.numero} — ${obra.titulo}`, value: obra.id })),
  );
  protected readonly equipeOptions = computed(() =>
    this.equipesElegiveis().map((equipe) => ({
      label: `${equipe.codigo} — ${equipe.tipoEquipe?.nome || 'Equipe'}`,
      value: equipe.id,
    })),
  );
  protected readonly colaboradorSaqueOptions = computed(() => {
    if (this.saqueForaEquipe()) {
      return this.collaboratorOptions();
    }

    const equipe = this.selectedEquipe();
    if (!equipe) return [];

    const membros = (equipe.membros ?? []).filter((m) => m.vinculoAtivo);
    return membros.map((m) => ({
      label: `${m.nomeColaborador}${m.funcao === 'RESPONSAVEL' ? ' (Responsável da Equipe)' : ''}`,
      value: m.idColaborador,
    }));
  });

  protected readonly selectedColaboradorSaqueName = computed(() => {
    const id = this.idColaboradorSaque();
    if (!id) return null;
    const opt = this.colaboradorSaqueOptions().find((o) => o.value === id);
    if (opt) return opt.label;
    const colab = this.colaboradores().find((c) => c.id === id);
    if (colab)
      return colab.nomeCompleto || `${colab.primeiroNome || ''} ${colab.ultimoNome || ''}`.trim();
    return id.slice(0, 8);
  });

  protected readonly formLocked = computed(() => this.submitting() || this.retryRequest() !== null);
  protected readonly formErrorMessages = computed(() =>
    Array.from(new Set(Object.values(this.fieldErrors()))),
  );

  private readonly passoUrl = bindStepToUrl(this.activeStep);
  /** Rascunho restaurado, aplicado quando os catálogos chegam (a obra recarrega dependências). */
  private rascunhoPendente: RascunhoSolicitacaoTecnica | null = null;
  // Só em memória: o rascunho carrega o nome do colaborador do saque (dado pessoal).
  private readonly rascunho = createFormDraft<RascunhoSolicitacaoTecnica>({
    persist: false,
    read: () =>
      this.rascunhoPendente ?? {
        idObra: this.idObra(),
        ocorrencia: this.ocorrencia(),
        idAlmoxarifado: this.idAlmoxarifado(),
        almoxarifado: this.stockLookup.items().filter((opcao) => opcao.value === this.idAlmoxarifado()),
        idEquipe: this.idEquipe(),
        saqueForaEquipe: this.saqueForaEquipe(),
        idColaboradorSaque: this.idColaboradorSaque(),
        colaboradorSaque: this.collaboratorLookup
          .items()
          .filter((opcao) => opcao.value === this.idColaboradorSaque()),
        dataPrevista: this.dataPrevista(),
        observacao: this.observacao(),
        tipoMovimentacao: this.tipoMovimentacao(),
        itens: this.itens(),
      },
    write: (valor) => (this.rascunhoPendente = valor),
  });

  ngOnInit(): void {
    const modoData =
      this.route.snapshot.data?.['modo'] || this.route.snapshot.queryParamMap.get('modo');
    if (modoData === 'devolucao') {
      this.modo.set('devolucao');
      this.tipoMovimentacao.set('DEVOLUCAO_COMUM');
    }
    const restaurado = this.rascunho.restore();
    const idObraParam = this.route.snapshot.queryParamMap.get('idObra');
    if (restaurado && idObraParam && this.rascunhoPendente?.idObra !== idObraParam) {
      // Aberto a partir de outra obra: o rascunho antigo não vale para esta.
      this.rascunhoPendente = null;
      this.rascunho.clear();
    }
    this.passoUrl.restore((passo) => !!this.rascunhoPendente && passo < 4);
    this.loadLookups();
  }

  private aplicarRascunho(valor: RascunhoSolicitacaoTecnica): void {
    this.rascunhoPendente = null;
    this.selectObra(valor.idObra);
    this.ocorrencia.set(valor.ocorrencia);
    if (valor.almoxarifado?.length) this.stockLookup.seedSelected(valor.almoxarifado);
    this.idAlmoxarifado.set(valor.idAlmoxarifado);
    this.idEquipe.set(valor.idEquipe);
    this.saqueForaEquipe.set(valor.saqueForaEquipe);
    if (valor.colaboradorSaque?.length) this.collaboratorLookup.seedSelected(valor.colaboradorSaque);
    this.idColaboradorSaque.set(valor.idColaboradorSaque);
    this.dataPrevista.set(valor.dataPrevista);
    this.observacao.set(valor.observacao);
    this.tipoMovimentacao.set(valor.tipoMovimentacao);
    this.itens.set(valor.itens ?? []);
  }

  protected onEquipeChange(id: string | null): void {
    this.idEquipe.set(id);
    if (!this.saqueForaEquipe()) {
      const options = this.colaboradorSaqueOptions();
      if (!options.some((opt) => opt.value === this.idColaboradorSaque())) {
        this.idColaboradorSaque.set(null);
      }
    }
  }

  protected onSaqueForaEquipeChange(value: boolean): void {
    this.saqueForaEquipe.set(value);
    const options = this.colaboradorSaqueOptions();
    if (!options.some((opt) => opt.value === this.idColaboradorSaque())) {
      this.idColaboradorSaque.set(null);
    }
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
          label:
            colaborador.nomeCompleto ||
            `${colaborador.primeiroNome || ''} ${colaborador.ultimoNome || ''}`.trim(),
        });
      },
    });
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

  protected searchStocks(query: string): void {
    this.stockLookup.search(query);
  }

  protected loadMoreStocks(): void {
    this.stockLookup.loadNext();
  }

  protected retryStocks(): void {
    this.stockLookup.retry();
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
    this.idMaterial.set(id);
    this.selectedMaterial.set(null);
    if (!id) {
      return;
    }
    this.almoxarifado.buscarMaterialPorId(id).subscribe({
      next: (material) => {
        if (this.idMaterial() === id) this.selectedMaterial.set(material);
      },
      error: () => {
        if (this.idMaterial() === id) this.selectedMaterial.set(null);
      },
    });
  }

  protected loadLookups(): void {
    this.loadingLookups.set(true);
    this.lookupError.set('');
    forkJoin({
      obras: this.obrasService.listarObras({ ativo: true, size: 1000, sort: 'numero,asc' }),
      equipes: this.rhService.listarEquipes({ ativo: true, size: 1000, sort: 'codigo,asc' }),
    }).subscribe({
      next: ({ obras, equipes }) => {
        this.obras.set(obras.content);
        this.equipes.set(equipes.content);
        this.equipesElegiveis.set(equipes.content);
        this.loadingLookups.set(false);

        const idObraParam = this.route.snapshot.queryParamMap.get('idObra');
        if (this.rascunhoPendente) {
          this.aplicarRascunho(this.rascunhoPendente);
        } else if (idObraParam) {
          this.selectObra(idObraParam);
        }
      },
      error: (error) => {
        this.loadingLookups.set(false);
        const message = getErrorMessage(error);
        this.lookupError.set(message);
        this.notifications.error(message, { title: 'Dados do formulário indisponíveis' });
      },
    });
  }

  protected selectObra(id: string | null): void {
    this.idObra.set(id);
    this.idEquipe.set(null);
    this.idColaboradorSaque.set(null);
    this.ocorrencia.set('');
    this.ocorrencias.set([]);
    this.projetosRelacionados.set([]);
    this.obraDetalhe.set(null);
    if (!id) return;

    forkJoin({
      detalhe: this.obrasService.buscarObraPorId(id),
      relacionados: this.service.projetosRelacionados(id),
    }).subscribe({
      next: ({ detalhe, relacionados }) => {
        this.obraDetalhe.set(detalhe);
        this.projetosRelacionados.set(relacionados.filter((item) => item.ativo));
        this.equipesElegiveis.set(this.equipes());
      },
      error: () => this.equipesElegiveis.set(this.equipes()),
    });

    this.service.obterProjecaoObra(id).subscribe({
      next: (projection) => {
        const unique = new Map(
          projection.ocorrencias.map((group) => [group.id, { id: group.id, codigo: group.codigo }]),
        );
        this.ocorrencias.set(Array.from(unique.values()));
      },
      error: () => this.ocorrencias.set([]),
    });
  }

  protected selectRelated(project: ProjetoRelacionadoSolicitacao): void {
    const candidate = this.obras().find((obra) => obra.id === project.idProjeto);
    if (!candidate) {
      this.obras.update((items) => [
        ...items,
        {
          id: project.idProjeto,
          numero: project.numero,
          titulo: project.titulo,
          tipoObra: project.tipoObra,
          dataInicio: '',
          dataPrevisaoFim: '',
          statusId: '',
          statusNome: '',
          statusConcluida: false,
          totalEquipes: 0,
          totalSubprojetos: 0,
          totalAnexos: 0,
          ativo: true,
        },
      ]);
    }
    this.selectObra(project.idProjeto);
  }

  protected selecionarSugestaoOcorrencia(sugestao: string): void {
    this.ocorrencia.set(normalizeOcorrencia(sugestao));
    this.fieldErrors.update((errors) => {
      const { ocorrencia: _o, ...rest } = errors;
      return rest;
    });
  }

  protected addItem(): void {
    const idMaterial = this.idMaterial();
    const tipoMovimentacao = this.tipoMovimentacao();
    const quantidade = this.quantidade();
    if (!idMaterial || !tipoMovimentacao || !quantidade || quantidade < 0.001) {
      this.notifications.warning('Selecione material, tipo e quantidade maior ou igual a 0,001.');
      return;
    }

    const mat = this.selectedMaterial();
    if (!mat || mat.id !== idMaterial) {
      this.notifications.warning('Aguarde o carregamento do material selecionado.');
      return;
    }
    const codigoOperacao = obterCodigoTecnico(tipoMovimentacao);

    const duplicate = this.itens().some(
      (item) => item.idMaterial === idMaterial && item.tipoMovimentacao === tipoMovimentacao,
    );
    if (duplicate) {
      this.notifications.warning(
        'O mesmo material e tipo técnico já estão na lista. Ajuste a linha existente.',
      );
      return;
    }

    this.itens.update((items) => [
      ...items,
      {
        idMaterial,
        tipoMovimentacao,
        codigoOperacao,
        quantidade,
        codigo: mat?.codigo || '',
        descricao: mat?.descricao || '',
      },
    ]);

    this.fieldErrors.update((errors) => {
      const { itens: _itens, ...remaining } = errors;
      return remaining;
    });
    this.idMaterial.set(null);
    this.selectedMaterial.set(null);
    this.quantidade.set(null);
  }

  protected removeItem(index: number): void {
    this.itens.update((items) => items.filter((_, i) => i !== index));
  }

  protected readonly tipoDescricao = tipoTecnicoLabel;
  protected readonly tipoDescricaoCompleta = tipoTecnicoDescricaoCompleta;
  protected readonly tipoNome = tipoTecnicoNome;

  protected goToStep(step: 1 | 2 | 3 | 4): void {
    if (this.formLocked() || step === this.activeStep()) return;
    if (step === 4 && this.solicitacoesCriadas().length === 0) return;
    if (step < this.activeStep()) {
      this.activeStep.set(step);
      return;
    }
    if (!this.validateContext()) return;
    if (step >= 3 && !this.validateItems()) return;
    this.activeStep.set(step);
  }

  protected nextStep(): void {
    if (this.activeStep() === 1) {
      if (this.validateContext()) this.activeStep.set(2);
      return;
    }
    if (this.activeStep() === 2 && this.validateItems()) this.activeStep.set(3);
  }

  protected previousStep(): void {
    if (this.activeStep() === 4) return;
    if (this.activeStep() === 3) this.activeStep.set(2);
    else if (this.activeStep() === 2) this.activeStep.set(1);
  }

  protected submit(): void {
    const request = this.retryRequest() ?? this.buildRequest();
    if (!request) return;
    const key = this.retryKey() ?? novaChaveIdempotencia();
    this.retryKey.set(key);
    this.submitting.set(true);
    this.fieldErrors.set({});

    this.service.criar(request, key).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.retryRequest.set(null);
        this.retryKey.set(null);
        this.rascunho.clear();
        const criadas = response.solicitacoesCriadas || [];
        this.solicitacoesCriadas.set(criadas);

        if (criadas.length === 1) {
          const unica = criadas[0];
          this.notifications.success(
            `Solicitação técnica (${unica.codigo} · ${unica.tipo}) criada com sucesso.`,
          );
          void this.router.navigate(['/almoxarifado/solicitacoes-tecnicas', unica.id]);
        } else if (criadas.length > 1) {
          this.notifications.success(
            `${criadas.length} solicitações técnicas independentes foram criadas com sucesso.`,
          );
          this.activeStep.set(4);
        } else {
          this.notifications.success('Solicitação criada com sucesso.');
          void this.router.navigate(['/almoxarifado/solicitacoes-tecnicas']);
        }
      },
      error: (error) => {
        this.submitting.set(false);
        if (ehFalhaDeTransporte(error)) {
          this.retryRequest.set(request);
          this.notifications.warning(
            'Falha de conexão. A mesma chave será usada na próxima tentativa.',
          );
          return;
        }
        this.retryRequest.set(null);
        this.retryKey.set(null);
        const problems = errosDeCampo(error);
        const errorStrings: Record<string, string> = Object.fromEntries(
          Object.entries(problems).map(([k, v]) => [k, v.message]),
        );
        this.fieldErrors.set(errorStrings);
        this.notifications.error(mensagemErroSolicitacao(error), {
          title: 'Não foi possível salvar',
        });
      },
    });
  }

  protected navegarParaSolicitacao(id: string): void {
    void this.router.navigate(['/almoxarifado/solicitacoes-tecnicas', id]);
  }

  protected novaSolicitacao(): void {
    this.solicitacoesCriadas.set([]);
    this.itens.set([]);
    this.idMaterial.set(null);
    this.selectedMaterial.set(null);
    this.quantidade.set(null);
    this.observacao.set('');
    this.ocorrencia.set('');
    this.idObra.set(null);
    this.idEquipe.set(null);
    this.idColaboradorSaque.set(null);
    this.saqueForaEquipe.set(false);
    this.idAlmoxarifado.set(null);
    this.activeStep.set(1);
  }

  protected discardRetry(): void {
    this.retryRequest.set(null);
    this.retryKey.set(null);
  }

  protected close(): void {
    this.rascunho.clear();
    this.navegacao.back(['/almoxarifado/solicitacoes-tecnicas']);
  }

  protected fieldError(field: string): string | undefined {
    return this.fieldErrors()[field];
  }

  private validateContext(): boolean {
    const errors: Record<string, string> = {};
    if (!this.idObra()) errors['idObra'] = 'Selecione a obra de destino.';
    if (!this.idAlmoxarifado())
      errors['idAlmoxarifadoAtendimento'] = 'Selecione o almoxarifado de atendimento.';

    const validacaoOcorrencia = validarOcorrencia(this.ocorrencia(), this.tipoObra());
    if (!validacaoOcorrencia.valida && validacaoOcorrencia.erro) {
      errors['ocorrencia'] = validacaoOcorrencia.erro;
    }
    if (this.observacao().trim().length > 1000) {
      errors['observacao'] = 'A observação deve ter no máximo 1.000 caracteres.';
    }

    this.fieldErrors.set(errors);
    if (Object.keys(errors).length) {
      this.notifications.warning('Preencha os campos obrigatórios para continuar.');
      return false;
    }
    return true;
  }

  private validateItems(): boolean {
    if (this.itens().length === 0) {
      this.fieldErrors.set({ itens: 'Adicione pelo menos um material à solicitação.' });
      this.notifications.warning('Adicione pelo menos um material à solicitação.');
      return false;
    }
    this.fieldErrors.set({});
    return true;
  }

  private buildRequest(): CriarSolicitacaoTecnicaRequest | null {
    if (!this.validateContext() || !this.validateItems()) return null;
    const validacaoOcorrencia = validarOcorrencia(this.ocorrencia(), this.tipoObra());

    const request: CriarSolicitacaoTecnicaRequest = {
      idObra: this.idObra()!,
      idAlmoxarifadoAtendimento: this.idAlmoxarifado()!,
      idEquipe: this.idEquipe(),
      idColaboradorSaque: this.idColaboradorSaque(),
      saqueForaEquipe: this.saqueForaEquipe(),
      dataPrevista: this.dataPrevista() ? this.formatCivilDate(this.dataPrevista()!) : null,
      observacao: this.observacao().trim() || null,
      itens: this.itens().map((item) => ({
        idMaterial: item.idMaterial,
        tipoMovimentacao: item.tipoMovimentacao,
        quantidade: item.quantidade,
      })),
    };
    if (validacaoOcorrencia.normalizada !== null) {
      request.ocorrencia = validacaoOcorrencia.normalizada;
    }
    return request;
  }

  protected isAddition(tipo: TipoSolicitacaoTecnica | CodigoTipoTecnico | string): boolean {
    return tipoTecnicoEhAdicao(tipo);
  }

  /** Ícone do tipo de movimentação, sempre resolvido pela fonte única de apresentação. */
  protected readonly typeIcon = tipoTecnicoIcon;

  private formatCivilDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
