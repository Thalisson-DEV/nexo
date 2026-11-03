import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import {
  CES_FORM_KIT,
  CesAsyncStateComponent,
  CesAutocompleteComponent,
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesDatepickerComponent,
  CesIconButtonComponent,
  CesNumberInputComponent,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService,
  cesFocusFirstInvalid,
} from '@ces/ui';
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
  tipoTecnicoEhAdicao,
  tipoTecnicoIcon,
  tipoTecnicoLabel,
} from '../solicitacao-tecnica-presentation';
import { saidaDoDrawer } from '../../../../shared/utils/drawer-navegacao.utils';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { RemoteLookup as CursorRemoteLookup } from '../../../../core/lookup/store/remote-lookup';

export interface AddedMaterialItem extends ItemIntencaoSolicitacaoTecnica {
  codigo: string;
  descricao: string;
  tipoMovimentacao: TipoSolicitacaoTecnica;
  codigoOperacao: CodigoOperacaoTecnica;
}

const TIPOS: { value: TipoSolicitacaoTecnica; nome: string }[] = [
  { value: 'ADICAO_COMUM', nome: 'Adição comum' },
  { value: 'ADICAO_CONCRETO', nome: 'Adição concreto' },
];

/**
 * Solicitação técnica de material em drawer. Com `idObra`, a obra já vem definida (aberta de dentro
 * da obra ou da carteira). A tela que abre monta o componente só enquanto o drawer está aberto.
 */
@Component({
  selector: 'app-solicitacao-tecnica-form',
  standalone: true,
  imports: [
    ...CES_FORM_KIT,
    FormsModule,
    CesAsyncStateComponent,
    CesAutocompleteComponent,
    CesBooleanControlComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesDatepickerComponent,
    CesIconButtonComponent,
    CesNumberInputComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent,
  ],
  templateUrl: './solicitacao-tecnica-form.component.html',
  styleUrl: '../solicitacao-form-itens.css',
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

  /** Obra já definida por quem abriu. */
  readonly obraInicial = input<string | null>(null, { alias: 'idObra' });
  readonly criadas = output<SolicitacaoTecnicaCriada[]>();
  /** Depois de fechar, por qualquer meio. */
  readonly fechado = output<void>();

  protected readonly aberto = signal(true);
  private readonly aoSair = saidaDoDrawer(() => this.fechado.emit());
  protected readonly loadingLookups = signal(true);
  protected readonly lookupError = signal('');
  protected readonly submitting = signal(false);
  protected readonly fieldErrors = signal<Record<string, string>>({});
  protected readonly retryRequest = signal<CriarSolicitacaoTecnicaRequest | null>(null);
  protected readonly retryKey = signal<string | null>(null);

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
  protected readonly obraFixa = computed(() => !!this.obraInicial());
  protected readonly obraRotulo = computed(() => {
    const obra = this.obraDetalhe() ?? this.selectedObra();
    return obra ? `${obra.numero} · ${obra.titulo}` : undefined;
  });
  protected readonly tipoOpcoes = computed<CesSelectOption<TipoSolicitacaoTecnica>[]>(() =>
    TIPOS.map((t) => ({ value: t.value, label: `${tipoTecnicoLabel(t.value)} · ${t.nome}` })),
  );
  protected readonly alterado = computed(
    () =>
      this.itens().length > 0 ||
      !!(this.idAlmoxarifado() || this.idEquipe() || this.idColaboradorSaque() || this.dataPrevista() || this.idMaterial()) ||
      !!(this.observacao().trim() || this.ocorrencia().trim()) ||
      (!this.obraFixa() && !!this.idObra()),
  );

  ngOnInit(): void {
    this.loadLookups();
  }

  protected fechar(): void {
    this.aberto.set(false);
    this.aoSair();
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
    const obraInicial = this.obraInicial();
    forkJoin({
      // Com a obra definida não há o que escolher: evita trazer a carteira inteira.
      obras: obraInicial ? of({ content: [] as ObraResumoResponse[] }) : this.obrasService.listarObras({ ativo: true, size: 1000, sort: 'numero,asc' }),
      equipes: this.rhService.listarEquipes({ ativo: true, size: 1000, sort: 'codigo,asc' }),
    }).subscribe({
      next: ({ obras, equipes }) => {
        this.obras.set(obras.content);
        this.equipes.set(equipes.content);
        this.equipesElegiveis.set(equipes.content);
        this.loadingLookups.set(false);

        if (obraInicial) this.selectObra(obraInicial);
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

  protected updateQuantidade(index: number, quantidade: number | null): void {
    if (!quantidade || quantidade < 0.001) return;
    this.itens.update((items) => items.map((item, i) => (i === index ? { ...item, quantidade } : item)));
  }

  protected readonly tipoDescricao = tipoTecnicoLabel;

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
        const criadas = response.solicitacoesCriadas || [];
        const codigos = criadas.map((c) => c.codigo).join(', ');
        this.notifications.success(
          criadas.length > 1
            ? `${criadas.length} solicitações independentes criadas: ${codigos}.`
            : `Solicitação ${codigos} criada.`,
        );
        this.criadas.emit(criadas);
        this.fechar();
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

  protected discardRetry(): void {
    this.retryRequest.set(null);
    this.retryKey.set(null);
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
      this.notifications.warning('Preencha os campos destacados para enviar.');
      setTimeout(() => cesFocusFirstInvalid());
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
