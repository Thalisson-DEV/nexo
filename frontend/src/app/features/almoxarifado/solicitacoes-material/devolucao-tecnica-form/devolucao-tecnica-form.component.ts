import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  ViewChild,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import {
  CES_FORM_KIT,
  CesAsyncStateComponent,
  CesAutocompleteComponent,
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesConfirmationService,
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
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { RemoteLookup as CursorRemoteLookup } from '../../../../core/lookup/store/remote-lookup';
import {
  DevolucaoTecnicaRequest,
  EstoqueResponse,
  ItemDevolucaoTecnicaRequest,
  MaterialResponse,
  SaldoResponse,
} from '../../../../core/models/almoxarifado.models';
import { ObraResponse, ObraResumoResponse } from '../../../../core/models/obras.models';
import { EquipeResponse } from '../../../../core/models/rh.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { TipoObraSolicitacao, TipoSolicitacaoTecnica } from '../../../../core/models/solicitacao-material-tecnica.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { normalizeOcorrencia, validarOcorrencia } from '../../../../core/utils/solicitacao-tecnica-error.util';
import { SaldoEstoqueDialogComponent } from '../../../../shared/components/saldo-estoque-dialog/saldo-estoque-dialog.component';
import { saidaDoDrawer } from '../../../../shared/utils/drawer-navegacao.utils';
import { tipoTecnicoLabel } from '../solicitacao-tecnica-presentation';

export type CondicaoDevolucao = 'NOVO' | 'SUCATA';
type TipoDevolucao = Extract<TipoSolicitacaoTecnica, 'DEVOLUCAO_COMUM' | 'DEVOLUCAO_CONCRETO'>;

export interface ItemDevolucao {
  idMaterial: string;
  codigo: string;
  descricao: string;
  tipo: TipoDevolucao;
  condicao: CondicaoDevolucao;
  quantidade: number;
  numSeries?: string[];
  /** Falso: o almoxarifado não guarda o material e ele é baixado na mesma operação. */
  guardarNoEstoque: boolean;
  /** Quanto a obra tinha deste material nesta condição quando o item foi incluído. */
  saldoNaObra: number;
}

const TIPOS: CesSelectOption<TipoDevolucao>[] = [
  { value: 'DEVOLUCAO_COMUM', label: '222 · Comum' },
  { value: 'DEVOLUCAO_CONCRETO', label: '922 · Concreto' },
];

const CONDICOES: CesSelectOption<CondicaoDevolucao>[] = [
  { value: 'NOVO', label: 'Novo' },
  { value: 'SUCATA', label: 'Sucata' },
];

/**
 * Devolução técnica em drawer: registra na hora a saída do material da obra para o almoxarifado,
 * sem passar pelo fluxo de solicitação. Aceita material que não consta no estoque da obra; o backend
 * lança a diferença como entrada avulsa na obra. Com `idObra`, a obra já vem definida.
 */
@Component({
  selector: 'app-devolucao-tecnica-form',
  standalone: true,
  imports: [
    ...CES_FORM_KIT,
    FormsModule,
    CesAsyncStateComponent,
    CesAutocompleteComponent,
    CesBooleanControlComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesIconButtonComponent,
    CesNumberInputComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent,
    SaldoEstoqueDialogComponent,
  ],
  templateUrl: './devolucao-tecnica-form.component.html',
  styleUrl: '../solicitacao-form-itens.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DevolucaoTecnicaFormComponent implements OnInit {
  @ViewChild('saldoDialog') saldoDialog!: SaldoEstoqueDialogComponent;

  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly obrasService = inject(ObrasService);
  private readonly rhService = inject(RhService);
  private readonly lookupStore = inject(LookupStore);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** Obra já definida por quem abriu. */
  readonly obraInicial = input<string | null>(null, { alias: 'idObra' });
  readonly registrada = output<void>();
  /** Depois de fechar, por qualquer meio. */
  readonly fechado = output<void>();

  protected readonly aberto = signal(true);
  private readonly aoSair = saidaDoDrawer(() => this.fechado.emit());
  protected readonly carregando = signal(true);
  protected readonly erroCatalogos = signal('');
  protected readonly salvando = signal(false);
  private readonly tentouEnviar = signal(false);

  private readonly obras = signal<ObraResumoResponse[]>([]);
  private readonly equipes = signal<EquipeResponse[]>([]);
  private readonly estoques = signal<EstoqueResponse[]>([]);
  private readonly saldosObra = signal<SaldoResponse[]>([]);
  private readonly obraDetalhe = signal<ObraResponse | null>(null);

  protected readonly idObra = signal<string | null>(null);
  protected readonly ocorrencia = signal('');
  protected readonly idEquipe = signal<string | null>(null);
  protected readonly idEstoqueDestino = signal<string | null>(null);

  protected readonly tipos = TIPOS;
  protected readonly condicoes = CONDICOES;
  protected readonly tipo = signal<TipoDevolucao>('DEVOLUCAO_COMUM');
  protected readonly condicao = signal<CondicaoDevolucao>('NOVO');
  protected readonly guardar = signal(true);
  protected readonly idMaterial = signal<string | null>(null);
  protected readonly material = signal<MaterialResponse | null>(null);
  protected readonly quantidade = signal<number | null>(null);
  protected readonly seriesTexto = signal('');
  protected readonly seriesDaObra = signal<string[]>([]);
  protected readonly itens = signal<ItemDevolucao[]>([]);

  private readonly materialLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchMaterials({ query, cursor }),
    selectedValues: () => {
      const id = this.idMaterial();
      return id ? [id] : [];
    },
    destroyRef: this.destroyRef,
  });
  protected readonly materiais = this.materialLookup.items;
  protected readonly materiaisCarregando = this.materialLookup.loading;
  protected readonly materiaisStatus = this.materialLookup.status;
  protected readonly materiaisErro = computed(() => {
    const erro = this.materialLookup.error();
    return erro ? getErrorMessage(erro) : undefined;
  });

  protected readonly obraFixa = computed(() => !!this.obraInicial());
  protected readonly obraOpcoes = computed(() => this.obras().map((o) => ({ label: `${o.numero} — ${o.titulo}`, value: o.id })));
  protected readonly obraRotulo = computed(() => {
    const obra = this.obraDetalhe();
    return obra ? `${obra.numero} · ${obra.titulo}` : undefined;
  });
  protected readonly equipeOpcoes = computed(() =>
    this.equipes().map((e) => ({ label: `${e.codigo} — ${e.tipoEquipe?.nome || 'Equipe'}`, value: e.id })),
  );
  protected readonly destinoOpcoes = computed(() =>
    this.estoques()
      .filter((e) => e.ativo && e.tipoEstoque?.nome?.trim().toUpperCase() === 'FISICO')
      .map((e) => ({ label: e.nomeIdentificador, value: e.id })),
  );
  private readonly tipoObra = computed<TipoObraSolicitacao | undefined>(() => this.obraDetalhe()?.tipoObra);
  protected readonly ocorrenciaObrigatoria = computed(() => this.tipoObra() === 'CORRETIVA' || this.tipoObra() === 'PREVENTIVA');
  /** Estoque patrimonial da obra; só existe depois da primeira movimentação dela. */
  protected readonly estoqueObra = computed(() => this.estoques().find((e) => e.idObra === this.idObra()) ?? null);

  protected readonly controlaSerie = computed(() => !!this.material()?.detalhaSerie);
  protected readonly seriesInformadas = computed(() =>
    Array.from(new Set(this.seriesTexto().split(/[\n,;]+/).map((s) => s.trim()).filter(Boolean))),
  );
  protected readonly saldoSelecionado = computed(() => {
    const material = this.material();
    return material ? this.saldoDe(material.id, this.condicao()) : 0;
  });
  protected readonly quantidadeIncluida = computed(() => (this.controlaSerie() ? this.seriesInformadas().length : (this.quantidade() ?? 0)));
  protected readonly excede = computed(() => !!this.material() && this.quantidadeIncluida() > this.saldoSelecionado());
  protected readonly podeIncluir = computed(() => !!this.material() && this.quantidadeIncluida() > 0);

  protected readonly itensForaDoEstoque = computed(() => this.itens().filter((i) => i.quantidade > i.saldoNaObra));
  protected readonly itensBaixados = computed(() => this.itens().filter((i) => !i.guardarNoEstoque));

  protected readonly alterado = computed(
    () => this.itens().length > 0 || !!(this.idEquipe() || this.idEstoqueDestino() || this.idMaterial() || this.ocorrencia().trim()) || (!this.obraFixa() && !!this.idObra()),
  );

  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.idObra()) e['idObra'] = 'Selecione a obra.';
    if (!this.idEquipe()) e['idEquipe'] = 'Selecione a equipe.';
    if (!this.idEstoqueDestino()) e['idEstoqueDestino'] = 'Selecione o almoxarifado.';
    const ocorrencia = validarOcorrencia(this.ocorrencia(), this.tipoObra());
    if (!ocorrencia.valida && ocorrencia.erro) e['ocorrencia'] = ocorrencia.erro;
    if (!this.itens().length) e['itens'] = 'Adicione pelo menos um material à devolução.';
    return e;
  });

  ngOnInit(): void {
    this.carregarCatalogos();
  }

  protected carregarCatalogos(): void {
    this.carregando.set(true);
    this.erroCatalogos.set('');
    const obraInicial = this.obraInicial();
    forkJoin({
      // Com a obra definida não há o que escolher: evita trazer a carteira inteira.
      obras: obraInicial ? of({ content: [] as ObraResumoResponse[] }) : this.obrasService.listarObras({ ativo: true, size: 1000, sort: 'numero,asc' }),
      equipes: this.rhService.listarEquipes({ ativo: true, size: 1000, sort: 'codigo,asc' }),
      estoques: this.almoxarifado.listarEstoquesAtivos(),
    }).subscribe({
      next: ({ obras, equipes, estoques }) => {
        this.obras.set(obras.content ?? []);
        this.equipes.set(equipes.content ?? []);
        this.estoques.set(estoques.content ?? []);
        this.carregando.set(false);
        if (obraInicial) this.selecionarObra(obraInicial);
      },
      error: (falha) => {
        this.carregando.set(false);
        this.erroCatalogos.set(getErrorMessage(falha));
      },
    });
  }

  protected selecionarObra(id: string | null): void {
    this.idObra.set(id);
    this.ocorrencia.set('');
    this.obraDetalhe.set(null);
    this.saldosObra.set([]);
    // O saldo informado em cada item é o da obra anterior: a lista não vale para a nova.
    this.itens.set([]);
    this.limparInclusao();
    if (!id) return;
    this.obrasService.buscarObraPorId(id).subscribe({ next: (obra) => this.obraDetalhe.set(obra), error: () => undefined });
    const estoque = this.estoqueObra();
    if (estoque) {
      this.almoxarifado.listarSaldos({ idEstoque: estoque.id, size: 1000 }).subscribe({
        next: (r) => this.saldosObra.set(r.content ?? []),
        error: () => undefined,
      });
    }
  }

  protected buscarMateriais(termo: string): void {
    this.materialLookup.search(termo);
  }

  protected carregarMaisMateriais(): void {
    this.materialLookup.loadNext();
  }

  protected tentarMateriaisDeNovo(): void {
    this.materialLookup.retry();
  }

  protected aoEscolherMaterial(id: string | null): void {
    this.idMaterial.set(id);
    this.material.set(null);
    this.quantidade.set(null);
    this.seriesTexto.set('');
    this.seriesDaObra.set([]);
    if (!id) return;
    this.almoxarifado.buscarMaterialPorId(id).subscribe({
      next: (material) => {
        if (this.idMaterial() !== id) return;
        this.material.set(material);
        if (material.detalhaSerie) this.buscarSeries();
      },
      error: () => undefined,
    });
  }

  protected aoMudarCondicao(condicao: CondicaoDevolucao): void {
    this.condicao.set(condicao);
    // Sucata normalmente vai para descarte; material novo volta para a prateleira.
    this.guardar.set(condicao === 'NOVO');
    if (this.controlaSerie()) this.buscarSeries();
  }

  protected adicionarSerie(serie: string): void {
    if (this.seriesInformadas().includes(serie)) return;
    this.seriesTexto.update((texto) => (texto.trim() ? `${texto.trim()}\n${serie}` : serie));
  }

  protected adicionarItem(): void {
    const material = this.material();
    if (!material || !this.podeIncluir()) return;
    const repetido = this.itens().some((i) => i.idMaterial === material.id && i.condicao === this.condicao() && i.tipo === this.tipo());
    if (repetido) {
      this.notificacoes.warning('Este material já está na lista com o mesmo tipo e condição. Ajuste a linha existente.');
      return;
    }
    const series = material.detalhaSerie ? this.seriesInformadas() : undefined;
    this.itens.update((lista) => [
      ...lista,
      {
        idMaterial: material.id,
        codigo: material.codigo,
        descricao: material.descricao,
        tipo: this.tipo(),
        condicao: this.condicao(),
        quantidade: this.quantidadeIncluida(),
        numSeries: series,
        guardarNoEstoque: this.guardar(),
        saldoNaObra: this.saldoSelecionado(),
      },
    ]);
    this.limparInclusao();
  }

  protected removerItem(indice: number): void {
    this.itens.update((lista) => lista.filter((_, i) => i !== indice));
  }

  protected ajustarQuantidade(indice: number, quantidade: number | null): void {
    if (!quantidade || quantidade <= 0) return;
    this.itens.update((lista) => lista.map((item, i) => (i === indice ? { ...item, quantidade } : item)));
  }

  protected alternarDestino(indice: number): void {
    this.itens.update((lista) => lista.map((item, i) => (i === indice ? { ...item, guardarNoEstoque: !item.guardarNoEstoque } : item)));
  }

  protected rotuloTipo(tipo: TipoDevolucao): string {
    return tipoTecnicoLabel(tipo);
  }

  protected erro(campo: string): string | undefined {
    return this.tentouEnviar() ? this.erros()[campo] : undefined;
  }

  protected fechar(): void {
    this.aberto.set(false);
    this.aoSair();
  }

  protected abrirSaldoObra(): void {
    const estoque = this.estoqueObra();
    if (estoque) this.saldoDialog.show(estoque.id, estoque.nomeIdentificador);
  }

  protected async enviar(): Promise<void> {
    this.tentouEnviar.set(true);
    if (Object.keys(this.erros()).length) {
      this.notificacoes.warning('Preencha os campos destacados para registrar a devolução.');
      setTimeout(() => cesFocusFirstInvalid());
      return;
    }
    const n = this.itens().length;
    const avisos = [
      this.itensForaDoEstoque().length ? `${this.itensForaDoEstoque().length} fora do saldo da obra: a diferença será lançada como entrada na obra.` : '',
      this.itensBaixados().length ? `${this.itensBaixados().length} não ficará(ão) no almoxarifado e será(ão) baixado(s).` : '',
    ].filter(Boolean);
    const confirmou = await this.confirmacao.confirm({
      title: 'Registrar devolução?',
      message: [`O saldo é movimentado na hora: ${n} ${n === 1 ? 'material' : 'materiais'} da obra para o almoxarifado.`, ...avisos].join(' '),
      confirmLabel: 'Registrar devolução',
      cancelLabel: 'Continuar editando',
    });
    if (confirmou) this.registrar();
  }

  private registrar(): void {
    const itens: ItemDevolucaoTecnicaRequest[] = this.itens().flatMap((item) => {
      const base = { idMaterial: item.idMaterial, tipo: item.tipo, condicao: item.condicao, guardarNoEstoque: item.guardarNoEstoque };
      return item.numSeries?.length
        ? item.numSeries.map((numSerie) => ({ ...base, quantidade: 1, numSerie }))
        : [{ ...base, quantidade: item.quantidade }];
    });
    const ocorrencia = validarOcorrencia(this.ocorrencia(), this.tipoObra()).normalizada;
    const request: DevolucaoTecnicaRequest = {
      idObra: this.idObra()!,
      ocorrencia: ocorrencia ? normalizeOcorrencia(ocorrencia) : undefined,
      idEquipe: this.idEquipe()!,
      idEstoqueDestino: this.idEstoqueDestino()!,
      itens,
    };
    this.salvando.set(true);
    this.almoxarifado.registrarDevolucaoTecnica(request).subscribe({
      next: () => {
        this.salvando.set(false);
        this.notificacoes.success('Devolução registrada e saldo movimentado.', {
          action: { label: 'Ver movimentações', run: () => void this.router.navigate(['/almoxarifado/movimentacoes']) },
        });
        this.registrada.emit();
        this.fechar();
      },
      error: (falha) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(falha), { title: 'Não foi possível registrar a devolução' });
      },
    });
  }

  private saldoDe(idMaterial: string, condicao: CondicaoDevolucao): number {
    const saldo = this.saldosObra().find((s) => s.material.id === idMaterial && (s.condicao === 'SUCATA' ? 'SUCATA' : 'NOVO') === condicao);
    return Math.max(saldo?.quantidade ?? 0, 0);
  }

  private buscarSeries(): void {
    this.seriesDaObra.set([]);
    const estoque = this.estoqueObra();
    const material = this.material();
    if (!estoque || !material) return;
    const condicao = this.condicao();
    this.almoxarifado.listarSeriais({ idEstoque: estoque.id, idMaterial: material.id, condicao, ativo: true, size: 200 }).subscribe({
      next: (r) => {
        if (this.material()?.id === material.id && this.condicao() === condicao) {
          this.seriesDaObra.set((r.content ?? []).map((s) => s.numSerie));
        }
      },
      error: () => undefined,
    });
  }

  private limparInclusao(): void {
    this.idMaterial.set(null);
    this.material.set(null);
    this.quantidade.set(null);
    this.seriesTexto.set('');
    this.seriesDaObra.set([]);
  }
}
