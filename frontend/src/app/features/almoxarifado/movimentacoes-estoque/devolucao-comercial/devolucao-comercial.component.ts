import { CommonModule, DecimalPipe } from '@angular/common';
import {
  Component,
  inject,
  signal,
  computed,
  ViewChild,
  ChangeDetectionStrategy,
  OnInit,
  WritableSignal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators, FormGroup } from '@angular/forms';
import { Router } from '@angular/router';
import { MenuItem, MessageService, ConfirmationService } from 'primeng/api';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { StepperModule } from 'primeng/stepper';

import { DateMaskDirective } from '../../../../shared/directives/date-mask.directive';
import { SaldoEstoqueDialogComponent } from '../../../../shared/components/saldo-estoque-dialog/saldo-estoque-dialog.component';
import { HelpDialogComponent } from '../../../../shared/components/help-dialog/help-dialog.component';
import { CesButtonComponent, CesStatusComponent } from '@ces/ui';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { RhService } from '../../../../core/services/rh.service';
import {
  EstoqueResponse,
  SaldoResponse,
  DevolucaoComercialRequest,
  ItemDevolucaoComercialRequest,
  PageResponseSaldoSerieResponse,
} from '../../../../core/models/almoxarifado.models';
import { EquipeResponse } from '../../../../core/models/rh.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { NavigationHistoryService, bindStepToUrl, createFormDraft } from '../../../../core/navigation';
import { DevolucaoSeriesPickerComponent } from './devolucao-series-picker.component';

export type CondicaoDevolucao = 'NOVO' | 'SUCATA';

export interface DevolucaoItem {
  idMaterial: string;
  codigo: string;
  descricao: string;
  quantidade: number;
  unidadeMedida?: string;
  numSeries?: string[];
  detalhaSerie: boolean;
  condicao: CondicaoDevolucao;
  /** Falso: o almoxarifado destino não guarda o material e ele é baixado na mesma operação. */
  guardarNoEstoque: boolean;
  /** Saldo da equipe nesta condição quando o item foi incluído; limita a edição da quantidade. */
  saldoDisponivel?: number;
  temporaryQuantidade?: number;
}

function condicaoDoSaldo(saldo: SaldoResponse): CondicaoDevolucao {
  return saldo.condicao === 'SUCATA' ? 'SUCATA' : 'NOVO';
}

/** Sucata devolvida normalmente vai para descarte; material novo volta para a prateleira. */
function guardarPorPadrao(condicao: CondicaoDevolucao): boolean {
  return condicao === 'NOVO';
}

@Component({
  selector: 'app-devolucao-comercial',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    SelectModule,
    InputNumberModule,
    InputTextModule,
    TextareaModule,
    DatePickerModule,
    TableModule,
    ButtonModule,
    TooltipModule,
    DialogModule,
    BreadcrumbModule,
    StepperModule,
    SaldoEstoqueDialogComponent,
    HelpDialogComponent,
    DateMaskDirective,
    CesButtonComponent,
    CesStatusComponent,
    DevolucaoSeriesPickerComponent,
    DecimalPipe,
  ],
  templateUrl: './devolucao-comercial.component.html',
  styleUrl: './devolucao-comercial.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DevolucaoComercialComponent implements OnInit {
  protected readonly Math = Math;
  private fb = inject(FormBuilder);
  private almoxService = inject(AlmoxarifadoService);
  private rhService = inject(RhService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  private router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);

  @ViewChild('saldoDialog') saldoDialog!: SaldoEstoqueDialogComponent;
  @ViewChild('helpDialog') helpDialog!: HelpDialogComponent;

  breadcrumb: MenuItem[] = [
    { label: 'Almoxarifado' },
    { label: 'Movimentações', routerLink: '/almoxarifado/movimentacoes' },
    { label: 'Nova Devolução' },
  ];

  activeStep = signal<1 | 2 | 3>(1);
  loading = signal<boolean>(false);

  // Lookups
  equipes = signal<EquipeResponse[]>([]);
  stocks = signal<EstoqueResponse[]>([]);
  saldos = signal<SaldoResponse[]>([]);

  // Formulários
  headerForm: FormGroup = this.fb.group({
    idEquipe: ['', Validators.required],
    idEstoqueDestino: ['', Validators.required],
    data: [new Date(), Validators.required],
    justificativa: [''],
  });

  selectedEquipeId = toSignal(
    this.headerForm.get('idEquipe')!.valueChanges,
    { initialValue: this.headerForm.get('idEquipe')?.value || '' }
  );

  selectedEstoqueDestinoId = toSignal(
    this.headerForm.get('idEstoqueDestino')!.valueChanges,
    { initialValue: this.headerForm.get('idEstoqueDestino')?.value || '' }
  );

  // Inclusão de material: a condição filtra os saldos e a condição do item vem sempre do saldo escolhido.
  condicaoFiltro = signal<CondicaoDevolucao>('NOVO');
  idSaldoSelecionado = signal<string | null>(null);
  quantidade = signal<number | null>(null);
  guardarNoEstoque = signal<boolean>(guardarPorPadrao('NOVO'));
  seriesDisponiveis = signal<string[]>([]);
  seriesSelecionadas = signal<string[]>([]);
  loadingSerials = signal<boolean>(false);

  // Itens Selecionados
  items = signal<DevolucaoItem[]>([]);

  // Edição em Linha
  editingIndex = signal<number | null>(null);

  // Edição das séries de um item já incluído
  serialDialogVisible = false;
  editingSerialIndex = signal<number | null>(null);
  seriesDialogDisponiveis = signal<string[]>([]);
  seriesDialogSelecionadas = signal<string[]>([]);
  loadingSerialsDialog = signal<boolean>(false);

  /** Evita que a resposta de uma busca de séries antiga sobrescreva a da seleção atual. */
  private buscaSeriesAtual = 0;

  // Computeds
  equipeOptions = computed(() =>
    this.equipes().map((e) => ({
      label: `${e.codigo} — ${e.tipoEquipe?.nome || 'Equipe'}`,
      value: e.id,
    })),
  );

  physicalStocks = computed(() =>
    this.stocks().filter((s) => Boolean(s.ativo) && s.tipoEstoque?.nome?.trim().toUpperCase() === 'FISICO'),
  );

  physicalStockOptions = computed(() =>
    this.physicalStocks().map((s) => ({
      label: s.nomeIdentificador,
      value: s.id,
    })),
  );

  estoqueCampoEquipe = computed(() => {
    const idEquipe = this.selectedEquipeId() || this.headerForm.get('idEquipe')?.value;
    if (!idEquipe) return null;
    const cleanId = String(idEquipe).toLowerCase().trim();
    return (
      this.stocks().find(
        (s) =>
          Boolean(s.ativo) &&
          s.idEquipe &&
          String(s.idEquipe).toLowerCase().trim() === cleanId &&
          s.tipoEstoque?.nome?.trim().toUpperCase() === 'CAMPO',
      ) || null
    );
  });

  nomeEquipeOrigem = computed(() => {
    const idEquipe = this.selectedEquipeId() || this.headerForm.get('idEquipe')?.value;
    const cleanId = String(idEquipe).toLowerCase().trim();
    const eq = this.equipes().find((e) => String(e.id).toLowerCase().trim() === cleanId);
    return eq ? `${eq.codigo} — ${eq.tipoEquipe?.nome || 'Equipe'}` : 'Equipe Selecionada';
  });

  nomeEstoqueDestino = computed(() => {
    const idEstoque = this.selectedEstoqueDestinoId() || this.headerForm.get('idEstoqueDestino')?.value;
    const cleanId = String(idEstoque).toLowerCase().trim();
    const st = this.stocks().find((s) => String(s.id).toLowerCase().trim() === cleanId);
    return st ? st.nomeIdentificador : 'Almoxarifado Destino';
  });

  /** Saldos com quantidade que ainda não estão na lista, em qualquer condição. */
  private saldosLivres = computed(() => {
    const incluidos = new Set(this.items().map((i) => `${i.idMaterial}|${i.condicao}`));
    return this.saldos().filter(
      (s) => (s.quantidade || 0) > 0 && !incluidos.has(`${s.material.id}|${condicaoDoSaldo(s)}`),
    );
  });

  contagemPorCondicao = computed(() => {
    const livres = this.saldosLivres();
    return {
      NOVO: livres.filter((s) => condicaoDoSaldo(s) === 'NOVO').length,
      SUCATA: livres.filter((s) => condicaoDoSaldo(s) === 'SUCATA').length,
    };
  });

  materialOptions = computed(() =>
    this.saldosLivres()
      .filter((s) => condicaoDoSaldo(s) === this.condicaoFiltro())
      .map((s) => ({
        label: `${s.material.codigo} — ${s.material.descricao} (${s.quantidade} ${s.material.unidadeMedida})`,
        value: s.id,
      })),
  );

  selectedMaterial = computed(
    () => this.saldos().find((s) => s.id === this.idSaldoSelecionado()) ?? null,
  );

  selectedSaldoDisponivel = computed(() => this.selectedMaterial()?.quantidade ?? 0);

  podeIncluir = computed(() => {
    const saldo = this.selectedMaterial();
    if (!saldo) return false;
    if (saldo.material.detalhaSerie) return this.seriesSelecionadas().length > 0;
    const qtd = this.quantidade();
    return qtd !== null && qtd > 0 && qtd <= this.selectedSaldoDisponivel();
  });

  materialPlaceholder = computed(() => {
    if (this.materialOptions().length) return 'Selecione o material...';
    return this.condicaoFiltro() === 'SUCATA'
      ? 'Nenhuma sucata em saldo para incluir'
      : 'Nenhum material novo em saldo para incluir';
  });

  incluirLabel = computed(() => {
    const series = this.seriesSelecionadas().length;
    return this.selectedMaterial()?.material.detalhaSerie && series
      ? `Incluir ${series} série(s)`
      : 'Incluir na devolução';
  });

  totalUnidades = computed(() =>
    this.items().reduce((acc, curr) => acc + (curr.quantidade || 0), 0),
  );

  itensBaixados = computed(() => this.items().filter((i) => !i.guardarNoEstoque).length);

  private readonly passoUrl = bindStepToUrl(this.activeStep);
  /** Rascunho do wizard. Só em memória: os itens podem trazer números de série (dado sensível). */
  private readonly rascunho = createFormDraft<{ cabecalho: Record<string, unknown>; itens: DevolucaoItem[] }>({
    persist: false,
    read: () => ({ cabecalho: this.headerForm.getRawValue(), itens: this.items() }),
    write: (valor) => {
      // Cabeçalho primeiro: trocar o estoque/equipe limpa os itens (valueChanges).
      this.headerForm.patchValue(valor.cabecalho);
      this.items.set(valor.itens ?? []);
    },
  });

  ngOnInit(): void {
    this.loadLookups();
    this.rascunho.restore();
    // O passo 2 depende dos saldos carregados ao avançar: reabre no passo 1, com os dados.
    this.passoUrl.restore(() => false);
  }

  loadLookups(): void {
    this.loading.set(true);
    this.almoxService.listarEstoquesAtivos().subscribe({
      next: (res) => {
        this.stocks.set(res.content || []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });

    this.rhService.listarEquipes({ ativo: true, size: 1000, sort: 'codigo,asc' }).subscribe({
      next: (res) => {
        this.equipes.set(res.content || []);
      },
      error: () => {},
    });
  }

  onEquipeChange(): void {
    this.saldos.set([]);
    this.items.set([]);
    this.limparInclusao();
  }

  isHeaderValid(): boolean {
    return this.headerForm.valid && !!this.estoqueCampoEquipe();
  }

  fieldError(fieldName: string): string | null {
    const control = this.headerForm.get(fieldName);
    if (control?.invalid && (control.dirty || control.touched)) {
      return 'Campo obrigatório';
    }
    return null;
  }

  goToStep(step: 1 | 2 | 3): void {
    if (step === 2 && !this.isHeaderValid()) return;
    if (step === 3 && (this.items().length === 0 || this.hasMissingSerials())) return;
    this.activeStep.set(step);
  }

  advanceToStep2(): void {
    if (!this.isHeaderValid()) {
      this.headerForm.markAllAsTouched();
      return;
    }

    const campoStock = this.estoqueCampoEquipe();
    if (!campoStock) {
      this.messageService.add({
        severity: 'error',
        summary: 'Erro',
        detail: 'A equipe selecionada não possui estoque de campo ativo.',
      });
      return;
    }

    this.loading.set(true);
    this.almoxService.listarSaldos({ idEstoque: campoStock.id, size: 1000 }).subscribe({
      next: (res) => {
        this.saldos.set(res.content || []);
        this.loading.set(false);
        this.activeStep.set(2);
      },
      error: () => {
        this.loading.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Falha ao carregar saldos do estoque da equipe.',
        });
      },
    });
  }

  selecionarCondicao(condicao: CondicaoDevolucao): void {
    if (this.condicaoFiltro() === condicao) return;
    this.condicaoFiltro.set(condicao);
    this.limparInclusao();
  }

  onMaterialChange(idSaldo: string | null): void {
    this.buscaSeriesAtual++;
    this.idSaldoSelecionado.set(idSaldo);
    this.quantidade.set(null);
    this.seriesSelecionadas.set([]);
    this.seriesDisponiveis.set([]);
    this.loadingSerials.set(false);

    const saldo = this.selectedMaterial();
    if (saldo?.material.detalhaSerie) {
      this.buscarSeries(
        saldo.material.id,
        condicaoDoSaldo(saldo),
        (series) => this.seriesDisponiveis.set(series),
        this.loadingSerials,
      );
    }
  }

  addItem(): void {
    const saldo = this.selectedMaterial();
    if (!saldo || !this.podeIncluir()) return;

    const series = saldo.material.detalhaSerie ? [...this.seriesSelecionadas()] : undefined;

    const newItem: DevolucaoItem = {
      idMaterial: saldo.material.id,
      codigo: saldo.material.codigo,
      descricao: saldo.material.descricao,
      quantidade: series ? series.length : (this.quantidade() as number),
      unidadeMedida: saldo.material.unidadeMedida,
      numSeries: series,
      detalhaSerie: saldo.material.detalhaSerie,
      condicao: condicaoDoSaldo(saldo),
      guardarNoEstoque: this.guardarNoEstoque(),
      saldoDisponivel: saldo.quantidade,
    };

    this.items.update((prev) => [...prev, newItem]);
    this.limparInclusao();
  }

  removeItem(index: number): void {
    this.items.update((prev) => prev.filter((_, i) => i !== index));
    if (this.editingIndex() === index) {
      this.editingIndex.set(null);
    }
  }

  alternarDestino(index: number): void {
    this.items.update((prev) =>
      prev.map((item, i) => (i === index ? { ...item, guardarNoEstoque: !item.guardarNoEstoque } : item)),
    );
  }

  startEdit(index: number): void {
    this.items.update((prev) => {
      const next = [...prev];
      if (next[index]) {
        next[index] = {
          ...next[index],
          temporaryQuantidade: next[index].quantidade,
        };
      }
      return next;
    });
    this.editingIndex.set(index);
  }

  saveEdit(index: number): void {
    const item = this.items()[index];
    if (!item) return;
    const tempQty = item.temporaryQuantidade;

    if (tempQty === undefined || tempQty === null || tempQty <= 0) {
      this.cancelEdit();
      return;
    }

    if (item.saldoDisponivel !== undefined && tempQty > item.saldoDisponivel) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: `A equipe tem ${item.saldoDisponivel} ${item.unidadeMedida ?? ''} deste material como ${item.condicao === 'SUCATA' ? 'sucata' : 'novo'}.`,
      });
      return;
    }

    this.items.update((prev) => {
      const next = [...prev];
      next[index] = { ...item, quantidade: tempQty, temporaryQuantidade: undefined };
      return next;
    });

    this.editingIndex.set(null);
  }

  cancelEdit(): void {
    const curr = this.editingIndex();
    if (curr !== null) {
      this.items.update((prev) => {
        if (!prev[curr]) return prev;
        const next = [...prev];
        next[curr] = {
          ...next[curr],
          temporaryQuantidade: undefined,
        };
        return next;
      });
    }
    this.editingIndex.set(null);
  }

  openSerialManager(index: number): void {
    const item = this.items()[index];
    if (!item) return;
    this.editingSerialIndex.set(index);
    this.seriesDialogSelecionadas.set([...(item.numSeries || [])]);
    this.seriesDialogDisponiveis.set([]);
    this.serialDialogVisible = true;
    this.buscarSeries(
      item.idMaterial,
      item.condicao,
      (series) => this.seriesDialogDisponiveis.set(series),
      this.loadingSerialsDialog,
    );
  }

  selectedItemForSerials(): DevolucaoItem | null {
    const index = this.editingSerialIndex();
    return index === null ? null : (this.items()[index] ?? null);
  }

  saveSerials(): void {
    const index = this.editingSerialIndex();
    const series = this.seriesDialogSelecionadas();
    if (index !== null && series.length > 0) {
      this.items.update((prev) => {
        const next = [...prev];
        next[index] = { ...next[index], numSeries: [...series], quantidade: series.length };
        return next;
      });
    }
    this.serialDialogVisible = false;
  }

  hasMissingSerials(): boolean {
    return this.items().some(
      (item) => item.detalhaSerie && (item.numSeries?.length || 0) !== Math.floor(item.quantidade),
    );
  }

  confirmDevolucao(): void {
    if (this.items().length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'Adicione ao menos um material para devolução.',
      });
      return;
    }

    if (this.hasMissingSerials()) {
      this.messageService.add({
        severity: 'error',
        summary: 'Atenção',
        detail: 'Selecione todos os números de série pendentes.',
      });
      return;
    }

    const baixados = this.itensBaixados();
    const avisoBaixa = baixados
      ? ` ${baixados} item(ns) não ficarão no almoxarifado e serão baixados na mesma operação.`
      : '';

    this.confirmationService.confirm({
      message: `Confirma a devolução de ${this.items().length} material(is) da equipe ${this.nomeEquipeOrigem()} para o almoxarifado ${this.nomeEstoqueDestino()}?${avisoBaixa}`,
      header: 'Confirmar Devolução Comercial',
      icon: 'pi pi-question-circle',
      acceptLabel: 'Confirmar Devolução',
      rejectLabel: 'Cancelar',
      acceptButtonStyleClass: '!bg-green-600 !border-green-600',
      rejectButtonStyleClass: '!bg-red-600 !border-red-600',
      accept: () => this.executeDevolucao(),
    });
  }

  private executeDevolucao(): void {
    this.loading.set(true);
    const header = this.headerForm.value;

    const flatItens: ItemDevolucaoComercialRequest[] = [];
    this.items().forEach((i) => {
      if (i.detalhaSerie && i.numSeries && i.numSeries.length > 0) {
        i.numSeries.forEach((sn) => {
          flatItens.push({
            idMaterial: i.idMaterial,
            quantidade: 1.0,
            condicao: i.condicao,
            numSerie: sn,
            guardarNoEstoque: i.guardarNoEstoque,
          });
        });
      } else {
        flatItens.push({
          idMaterial: i.idMaterial,
          quantidade: i.quantidade,
          condicao: i.condicao,
          numSerie: undefined,
          guardarNoEstoque: i.guardarNoEstoque,
        });
      }
    });

    const request: DevolucaoComercialRequest = {
      idEquipe: header.idEquipe,
      idEstoqueDestino: header.idEstoqueDestino,
      observacao: header.justificativa?.trim() || undefined,
      itens: flatItens,
    };

    this.almoxService.registrarDevolucaoComercial(request).subscribe({
      next: () => {
        this.loading.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Devolução comercial realizada com sucesso!',
        });
        this.rascunho.clear();
        setTimeout(() => {
          void this.router.navigate(['/almoxarifado/movimentacoes']);
        }, 1200);
      },
      error: (err) => {
        this.loading.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Erro na Devolução',
          detail: getErrorMessage(err),
        });
      },
    });
  }

  abrirSaldo(idEstoque?: string): void {
    if (!idEstoque) return;
    const nome = this.stocks().find((s) => s.id === idEstoque)?.nomeIdentificador || idEstoque;
    this.saldoDialog?.show(idEstoque, nome);
  }

  cancel(): void {
    this.rascunho.clear();
    this.navegacao.back(['/almoxarifado/movimentacoes']);
  }

  protected getCondicaoBadgeClass(condicao: CondicaoDevolucao): string {
    return condicao === 'SUCATA'
      ? 'bg-rose-50 dark:bg-rose-950/60 text-rose-700 dark:text-rose-400 border border-rose-200 dark:border-rose-800'
      : 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800';
  }

  private limparInclusao(): void {
    this.buscaSeriesAtual++;
    this.idSaldoSelecionado.set(null);
    this.quantidade.set(null);
    this.seriesDisponiveis.set([]);
    this.seriesSelecionadas.set([]);
    this.loadingSerials.set(false);
    this.guardarNoEstoque.set(guardarPorPadrao(this.condicaoFiltro()));
  }

  /** Séries ativas do material no estoque de campo da equipe, somente na condição pedida. */
  private buscarSeries(
    idMaterial: string,
    condicao: CondicaoDevolucao,
    aplicar: (series: string[]) => void,
    carregando: WritableSignal<boolean>,
  ): void {
    const campoStock = this.estoqueCampoEquipe();
    if (!campoStock) return;

    const busca = ++this.buscaSeriesAtual;
    carregando.set(true);
    this.almoxService
      .listarSeriais({ idEstoque: campoStock.id, idMaterial, condicao, ativo: true, size: 1000 })
      .subscribe({
        next: (res: PageResponseSaldoSerieResponse) => {
          if (busca !== this.buscaSeriesAtual) return;
          aplicar(
            (res.content || [])
              .filter((s) => !s.condicao || s.condicao === condicao)
              .map((s) => s.numSerie)
              .sort((a, b) => a.localeCompare(b, 'pt-BR', { numeric: true })),
          );
          carregando.set(false);
        },
        error: () => {
          if (busca !== this.buscaSeriesAtual) return;
          carregando.set(false);
          this.messageService.add({
            severity: 'error',
            summary: 'Erro',
            detail: 'Falha ao carregar números de série do estoque da equipe.',
          });
        },
      });
  }
}
