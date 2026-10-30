import {
  Component,
  DestroyRef,
  OnInit,
  signal,
  inject,
  ViewChild,
  AfterViewInit,
  OnDestroy,
  ElementRef,
  computed,
} from '@angular/core';
import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { CheckboxModule } from 'primeng/checkbox';
import { AccordionModule } from 'primeng/accordion';
import { MessageService, ConfirmationService } from 'primeng/api';
import { catchError, forkJoin, map, of } from 'rxjs';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesIconButtonComponent,
  CesNumberInputComponent,
  CesStatusComponent,
  CesTone,
} from '@ces/ui';
import { CesAutocompleteComponent, CesDatepickerComponent, CesSelectComponent, CesSelectOption } from '@ces/ui/forms';
import { CesCellDirective, CesDataTableColumn, CesDataTableComponent, CesRowDetailDirective } from '@ces/ui/data';

import { AuthService } from '../../../../core/services/auth.service';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import {
  SolicitacaoResponse,
  SolicitacaoStatus,
  ItemSolicitacaoResponse,
  AprovarSolicitacaoRequest,
  EntregaSolicitacaoRequest,
  MaterialResponse,
  MotivoRejeicaoResponse,
  MotivoPendenciaResponse,
  RejeitarSolicitacaoRequest,
  EstornoParcialRequest,
} from '../../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { dataIso, dataLocal } from '../../../../shared/listagem/listagem';
import { SaldoEstoqueDialogComponent } from '../../../../shared/components/saldo-estoque-dialog/saldo-estoque-dialog.component';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { RemoteLookup as CursorRemoteLookup } from '../../../../core/lookup/store/remote-lookup';
import { SolicitacaoTecnicaHistoricoComponent } from '../solicitacao-tecnica-historico/solicitacao-tecnica-historico.component';
import { SolicitacaoOperacoesHistoricoComponent } from '../solicitacao-operacoes-historico/solicitacao-operacoes-historico.component';
import { ConsumoAnteriorTabelaComponent } from '../solicitacao-consumo-anterior/solicitacao-consumo-anterior.component';
import { SolicitacaoPendenciasComponent } from '../solicitacao-pendencias.component';
import { SolicitacaoPendenciaModalComponent } from '../solicitacao-pendencia-modal.component';
import { RhService } from '../../../../core/services/rh.service';
import { EquipeMembroResponse } from '../../../../core/models/rh.models';
import { NavigationHistoryService } from '../../../../core/navigation/navigation-history.service';
import {
  AvisoSaldoNegativoEquipeComponent,
  SaldoNegativoMaterial,
  agruparSaldosNegativos,
} from '../../shared/saldo-negativo-equipe';
import { SeriesSelecaoComponent } from '../series-selecao/series-selecao.component';
import { SeriesGradeComponent, SerieGradeItem } from '../../../../shared/components/series-grade/series-grade.component';
import {
  GrupoItemSolicitacao,
  ItemEntregaRascunho,
  SITUACOES,
  agruparItensSolicitacao,
  montarEntregaDoRelatorio,
  quantidadeDaLinha,
  rascunhoDaEntrega,
  situacaoDoGrupo,
} from './itens-solicitacao.util';

/** A faixa abaixo do título: a situação da solicitação em uma frase. */
interface FaixaSituacao {
  readonly tom: CesTone | 'brand';
  readonly icone: string;
  readonly titulo: string;
  readonly texto: string;
}

@Component({
  selector: 'app-relatorio-solicitacao-material',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    TooltipModule,
    ToastModule,
    DecimalPipe,
    DatePipe,
    ConfirmDialogModule,
    DialogModule,
    SelectModule,
    SeriesSelecaoComponent,
    InputNumberModule,
    InputTextModule,
    TextareaModule,
    CheckboxModule,
    AccordionModule,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesIconButtonComponent,
    CesNumberInputComponent,
    CesStatusComponent,
    CesAutocompleteComponent,
    CesSelectComponent,
    CesDatepickerComponent,
    CesDataTableComponent,
    CesCellDirective,
    CesRowDetailDirective,
    SeriesGradeComponent,
    SaldoEstoqueDialogComponent,
    SolicitacaoTecnicaHistoricoComponent,
    SolicitacaoOperacoesHistoricoComponent,
    ConsumoAnteriorTabelaComponent,
    SolicitacaoPendenciasComponent,
    SolicitacaoPendenciaModalComponent,
    AvisoSaldoNegativoEquipeComponent,
  ],
  providers: [MessageService, ConfirmationService],
  templateUrl: './relatorio-solicitacao-material.component.html',
  styleUrls: ['./relatorio-solicitacao-material.component.css'],
})
export class RelatorioSolicitacaoMaterialComponent implements OnInit, AfterViewInit, OnDestroy {
  protected readonly Math = Math;
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);
  private almoxarifadoService = inject(AlmoxarifadoService);
  private lookupStore = inject(LookupStore);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  private destroyRef = inject(DestroyRef);
  private rhService = inject(RhService);
  private auth = inject(AuthService);

  private readonly materialLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchMaterials({ query, cursor }),
    selectedValues: () => {
      const ids = this.itemsDraft()
        .filter((item) => !item.removed)
        .map((item) => item.material.id);
      const selected = this.selectedMaterial()?.id;
      return selected ? [...ids, selected] : ids;
    },
    destroyRef: this.destroyRef,
  });

  private readonly withdrawalLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () => {
      const id = this.withdrawalCollaboratorId();
      return id ? [id] : [];
    },
    destroyRef: this.destroyRef,
  });

  protected solicitacao = signal<SolicitacaoResponse | null>(null);
  protected loading = signal(true);
  protected error = signal<string | null>(null);
  protected isPrintMode = signal(false);
  protected usersMap = new Map<string, string>();
  protected readonly assinaturaAlmoxUrl = signal<string | null>(null);
  protected readonly assinaturaEquipeUrl = signal<string | null>(null);
  protected readonly responsavelSaqueNome = signal('Responsável pelo Saque');
  protected readonly equipeSaqueName = signal('—');

  // Linha do tempo / Histórico
  protected readonly timelineValue = signal<string | number | string[] | number[] | null | undefined>(undefined);
  protected readonly isTimelineOpen = computed(() => {
    const val = this.timelineValue();
    if (!val) return false;
    return val === 'timeline' || (Array.isArray(val) && (val as any[]).includes('timeline'));
  });

  // Estados de Fluxo
  protected isEditMode = signal(false);
  protected isDeliveryMode = signal(false);
  /** Saldos negativos do estoque da equipe que recebe a entrega (apenas informativo). */
  protected saldosNegativosEquipe = signal<Map<string, SaldoNegativoMaterial>>(new Map());
  protected readonly idsMateriaisEntrega = computed(() =>
    this.entregaRascunho()
      .filter((l) => !l.removido)
      .map((l) => l.material.id),
  );

  /** Um material por linha, com as séries entregues e as definidas para a entrega. */
  protected readonly gruposItens = computed(() => agruparItensSolicitacao(this.solicitacao()?.itens));
  protected readonly resumoSeries = computed(() => {
    const grupos = this.gruposItens();
    return {
      entregues: grupos.reduce((t, g) => t + g.seriesEntregues.length, 0),
      definidas: grupos.filter((g) => g.pendente > 0).reduce((t, g) => t + g.seriesReservadas.length, 0),
      materiais: grupos.filter((g) => g.material.detalhaSerie).length,
    };
  });
  protected readonly materiaisPendentes = computed(() => this.gruposItens().filter((g) => g.pendente > 0).length);

  protected readonly faixaSituacao = computed<FaixaSituacao>(() => {
    const sol = this.solicitacao();
    const totais = this.totaisOperacionais();
    const fmt = (n: number) => n.toLocaleString('pt-BR', { maximumFractionDigits: 3 });
    const destino = sol?.estoqueDestino?.nomeIdentificador ?? 'o estoque';
    const pendentes = this.materiaisPendentes();
    const definidas = this.resumoSeries().definidas;
    switch (sol?.status) {
      case 'PENDENTE':
        return { tom: 'info', icone: 'pi pi-clock', titulo: 'Aguardando aprovação', texto: `Pedido de ${destino} com ${this.gruposItens().length} materiais; nada sai do almoxarifado antes da aprovação.` };
      case 'APROVADO':
        return {
          tom: 'brand',
          icone: 'pi pi-box',
          titulo: 'Aprovada, aguardando retirada',
          texto: `${pendentes} ${pendentes === 1 ? 'material' : 'materiais'} a entregar para ${destino}` + (definidas ? ` · ${definidas} ${definidas === 1 ? 'série definida' : 'séries definidas'} para a entrega.` : '.'),
        };
      case 'ENTREGUE_PARCIAL':
        return { tom: 'warning', icone: 'pi pi-truck', titulo: 'Entrega parcial', texto: `Saíram ${fmt(totais.entregaLiquida)} de ${fmt(totais.solicitada)}; faltam ${fmt(totais.pendente)} em ${pendentes} ${pendentes === 1 ? 'material' : 'materiais'}.` };
      case 'ENTREGUE':
        return { tom: 'success', icone: 'pi pi-check-circle', titulo: 'Solicitação entregue', texto: `${fmt(totais.entregaLiquida)} entregues para ${destino}${sol.dataEntrega ? ' · última entrega em ' + new Date(sol.dataEntrega).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : ''}.` };
      case 'REJEITADO':
        return { tom: 'danger', icone: 'pi pi-times-circle', titulo: 'Solicitação rejeitada', texto: sol.motivoRejeicao?.nome ?? 'Nada foi entregue.' };
      case 'ESTORNADO':
        return { tom: 'neutral', icone: 'pi pi-replay', titulo: 'Entrega estornada', texto: `O material voltou para ${sol.estoqueOrigem?.nomeIdentificador ?? 'a origem'}.` };
      default:
        return { tom: 'neutral', icone: 'pi pi-file', titulo: 'Solicitação de material', texto: '' };
    }
  });

  protected readonly colunasItens: CesDataTableColumn<GrupoItemSolicitacao>[] = [
    { key: 'codigo', header: 'Código', priority: 1, width: '7rem', value: (g) => g.material.codigo },
    {
      key: 'descricao',
      header: 'Material / Descrição',
      type: 'custom',
      priority: 1,
      searchable: true,
      value: (g) => g.material.descricao,
      // A busca e o CSV também encontram o material pelo número de série.
      display: (g) => [g.material.descricao, ...g.seriesEntregues, ...g.seriesReservadas].join(' '),
    },
    { key: 'unidade', header: 'Un.', priority: 2, width: '4rem', value: (g) => g.material.unidadeMedida },
    { key: 'solicitada', header: 'Solicitado', type: 'number', priority: 1, width: '7rem', value: (g) => g.solicitada },
    { key: 'entregue', header: 'Entregue', type: 'number', priority: 1, width: '7rem', value: (g) => g.entregue },
    { key: 'pendente', header: 'Pendente', type: 'number', priority: 1, width: '7rem', value: (g) => g.pendente },
    { key: 'situacao', header: 'Situação', type: 'custom', priority: 2, width: '11rem', value: (g) => SITUACOES[situacaoDoGrupo(g)].rotulo },
  ];
  protected readonly chaveGrupo = (g: GrupoItemSolicitacao) => g.chave;
  protected readonly rotuloGrupo = (g: GrupoItemSolicitacao) => `${g.material.codigo} ${g.material.descricao}`;
  protected readonly grupoExpansivel = (g: GrupoItemSolicitacao) =>
    g.seriesEntregues.length > 0 || (g.seriesReservadas.length > 0 && g.pendente > 0) || this.podeDefinirSeries(g);
  protected readonly quantidadeDaLinha = quantidadeDaLinha;

  protected situacao(g: GrupoItemSolicitacao) {
    return SITUACOES[situacaoDoGrupo(g)];
  }

  protected grade(series: readonly string[]): SerieGradeItem[] {
    return series.map((numSerie) => ({ numSerie }));
  }

  // Séries definidas antes da entrega (reserva)
  protected readonly reservaAberta = signal<string | null>(null);
  protected readonly reservaRascunho = signal<string[]>([]);
  protected readonly salvandoReserva = signal(false);

  /** Material com série, com saldo a entregar, em solicitação que aceita entrega. */
  protected podeDefinirSeries(g: GrupoItemSolicitacao): boolean {
    return !!g.material.detalhaSerie && g.pendente > 0 && !!g.itemPendente && this.pode('ENTREGAR');
  }

  protected abrirReserva(g: GrupoItemSolicitacao): void {
    this.reservaRascunho.set([...(g.itemPendente?.seriesReservadas ?? [])]);
    this.reservaAberta.set(g.chave);
  }

  protected salvarReserva(g: GrupoItemSolicitacao): void {
    const sol = this.solicitacao();
    const item = g.itemPendente;
    if (!sol || !item || this.salvandoReserva()) return;
    this.salvandoReserva.set(true);
    this.almoxarifadoService.definirSeriesReservadas(sol.id, item.id, this.reservaRascunho()).subscribe({
      next: (series) => {
        this.salvandoReserva.set(false);
        this.reservaAberta.set(null);
        this.messageService.add({
          severity: 'success',
          summary: 'Séries definidas',
          detail: series.length
            ? `${series.length} ${series.length === 1 ? 'série reservada' : 'séries reservadas'} para ${g.material.codigo}. Na entrega, já vêm escolhidas.`
            : `Séries de ${g.material.codigo} liberadas.`,
        });
        this.loadData();
      },
      error: (err) => {
        this.salvandoReserva.set(false);
        this.messageService.add({ severity: 'error', summary: 'Não foi possível salvar as séries', detail: getErrorMessage(err) });
      },
    });
  }

  // Entrega: o que sai de cada material agora
  protected readonly entregaRascunho = signal<ItemEntregaRascunho[]>([]);
  protected readonly linhasNaEntrega = computed(() => this.entregaRascunho().filter((l) => quantidadeDaLinha(l) > 0).length);
  /** Saldo novo de cada material na origem; `undefined` enquanto consulta. */
  protected readonly saldoOrigem = signal<Record<string, number | null | undefined>>({});
  /** Saldos do material escolhido para adicionar: na origem e no destino. */
  protected readonly saldosDoSelecionado = signal<{ origem: number; destino: number } | null>(null);

  // Atendimento: quem aprova troca origem e data do saque; quem entrega, só a origem
  protected readonly opcoesOrigem = signal<CesSelectOption<string>[]>([]);
  protected readonly carregandoOrigens = signal(false);
  protected readonly idOrigemEscolhida = signal<string | null>(null);
  protected readonly dataPrevistaEscolhida = signal<Date | null>(null);
  protected readonly origemAtual = computed(() => {
    const atual = this.solicitacao()?.estoqueOrigem;
    const id = this.idOrigemEscolhida();
    if (!id || id === atual?.id) return atual ? { id: atual.id, nome: atual.nomeIdentificador } : null;
    return { id, nome: this.opcoesOrigem().find((o) => o.value === id)?.label ?? '' };
  });
  protected readonly origemTrocada = computed(() => {
    const id = this.idOrigemEscolhida();
    return !!id && id !== this.solicitacao()?.estoqueOrigem?.id;
  });
  /** O estorno devolve à origem da solicitação: depois da primeira saída ela não muda mais. */
  protected readonly podeTrocarOrigem = computed(() => !(this.solicitacao()?.itens ?? []).some((i) => (i.quantidadeEntregaLiquida ?? 0) > 0));
  protected readonly dataPrevistaAtual = computed(() => this.dataPrevistaEscolhida() ?? dataLocal(this.solicitacao()?.dataPrevista));

  protected itemsDraft = signal<
    (ItemSolicitacaoResponse & {
      isNew?: boolean;
      removed?: boolean;
      jaEntregueOriginal?: number;
      quantidadeOriginal?: number;
      readonly?: boolean;
    })[]
  >([]);
  protected readonly materiaisOptions = this.materialLookup.items;
  protected readonly materiaisLoading = this.materialLookup.loading;
  protected readonly materiaisStatus = this.materialLookup.status;
  protected readonly materiaisError = computed(() => {
    const error = this.materialLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });
  protected selectedMaterial = signal<MaterialResponse | null>(null);
  protected selectedMaterialId = signal<string | null>(null);
  protected itemQuantity = signal<number | null>(null);
  protected saving = signal(false);

  // Estados de Estorno Parcial
  protected displayEstornoParcialModal = signal(false);
  protected estornoItems = signal<
    (ItemSolicitacaoResponse & { selected?: boolean; quantidadeEstorno?: number })[]
  >([]);

  protected isAllEstornoSelected = computed(() => {
    const items = this.estornoItems();
    return items.length > 0 && items.every((i) => i.selected);
  });

  protected readonly totaisOperacionais = computed(() => {
    const itens = this.solicitacao()?.itens ?? [];
    return itens.reduce(
      (totais, item) => ({
        solicitada: totais.solicitada + (item.quantidade ?? 0),
        entregaBruta:
          totais.entregaBruta + (item.quantidadeEntregueBruta ?? item.quantidadeEntregue ?? 0),
        entregaEstornada: totais.entregaEstornada + (item.quantidadeEntregaEstornada ?? 0),
        entregaLiquida:
          totais.entregaLiquida + (item.quantidadeEntregaLiquida ?? item.quantidadeEntregue ?? 0),
        pendente:
          totais.pendente +
          (item.quantidadePendente ??
            Math.max(
              0,
              item.quantidade - (item.quantidadeEntregaLiquida ?? item.quantidadeEntregue ?? 0),
            )),
      }),
      { solicitada: 0, entregaBruta: 0, entregaEstornada: 0, entregaLiquida: 0, pendente: 0 },
    );
  });

  protected toggleSelectAllEstorno(selected: boolean) {
    this.estornoItems.update((items) => items.map((i) => ({ ...i, selected })));
  }

  // Estados de Rejeição
  protected displayRejectModal = signal(false);
  protected motivosRejeicaoOptions = signal<MotivoRejeicaoResponse[]>([]);
  protected rejectionData = {
    idMotivoRejeicao: '',
    justificativa: '',
  };

  // Dados de Entrega
  protected deliveryData = {
    assinaturaAlmox: '',
    assinaturaEquipe: '',
  };
  protected readonly withdrawalCollaboratorId = signal<string | null>(null);
  protected readonly withdrawalOutsideTeam = signal(false);
  protected readonly withdrawalTeamMembers = signal<EquipeMembroResponse[]>([]);
  protected readonly withdrawalTeamLoading = signal(false);
  protected readonly withdrawalOptionsRemote = this.withdrawalLookup.items;
  protected readonly withdrawalRemoteLoading = this.withdrawalLookup.loading;
  protected readonly withdrawalRemoteStatus = this.withdrawalLookup.status;
  protected readonly withdrawalRemoteError = computed(() => {
    const error = this.withdrawalLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });
  protected readonly withdrawalOptions = computed(() =>
    this.withdrawalOutsideTeam()
      ? this.withdrawalOptionsRemote()
      : this.withdrawalTeamMembers().map((member) => ({
          label: `${member.nomeColaborador}${member.funcao === 'RESPONSAVEL' ? ' (Responsável da Equipe)' : ''}`,
          value: member.idColaborador,
        })),
  );

  // Assinatura Digital
  protected displaySignatureModal = signal(false);
  protected currentSignatureField = signal<'almox' | 'equipe' | null>(null);

  @ViewChild('signatureCanvas') signatureCanvas!: ElementRef<HTMLCanvasElement>;
  private signaturePadCtx: CanvasRenderingContext2D | null = null;
  private isDrawing = false;

  @ViewChild('saldoDialog') saldoDialog!: SaldoEstoqueDialogComponent;

  // Pendências
  protected pendenciaModalVisible = signal(false);
  protected pendenciaModalItemId = signal<string | null>(null);
  protected pendenciaModalItemLabel = signal<string | null>(null);
  protected pendenciaModalMotivoAtual = signal<MotivoPendenciaResponse | null>(null);
  protected pendenciaModalJustificativaAtual = signal<string | null>(null);

  /** Pendências são do almoxarifado (mesma regra do backend: write ou plan). */
  protected podeRegistrarPendencia(): boolean {
    const sol = this.solicitacao();
    if (!sol) return false;
    const permissoes = this.auth.currentUser()?.permissoes ?? [];
    return (
      (permissoes.includes('estoque:solicitacao:write') || permissoes.includes('estoque:solicitacao:plan')) &&
      (sol.status === 'PENDENTE' || sol.status === 'APROVADO' || sol.status === 'ENTREGUE_PARCIAL') &&
      !sol.encerrada
    );
  }

  /** A política de ações do backend decide os botões; sem ela, cai na regra pelo status. */
  protected pode(acao: 'APROVAR' | 'REJEITAR' | 'ENTREGAR' | 'ESTORNAR'): boolean {
    const sol = this.solicitacao();
    if (!sol) return false;
    if (sol.acoesPermitidas) return sol.acoesPermitidas.includes(acao);
    switch (acao) {
      case 'APROVAR':
        return sol.status === 'PENDENTE';
      case 'REJEITAR':
        return sol.status === 'PENDENTE' || sol.status === 'APROVADO';
      case 'ENTREGAR':
        return sol.status === 'APROVADO' || sol.status === 'ENTREGUE_PARCIAL' || (sol.status === 'ESTORNADO' && this.totaisOperacionais().pendente > 0);
      case 'ESTORNAR':
        return sol.status === 'ENTREGUE' || sol.status === 'ENTREGUE_PARCIAL';
    }
  }

  /** Na pendente quem decide "reprova"; na aprovada o almoxarifado "rejeita" (não consegue atender). */
  protected rotuloRejeicao(): string {
    return this.solicitacao()?.status === 'PENDENTE' ? 'Reprovar' : 'Rejeitar';
  }

  protected abrirModalPendenciaSolicitacao(): void {
    const sol = this.solicitacao();
    if (!sol) return;
    this.pendenciaModalItemId.set(null);
    this.pendenciaModalItemLabel.set(null);
    this.pendenciaModalMotivoAtual.set(sol.motivoPendencia ?? null);
    this.pendenciaModalJustificativaAtual.set(sol.justificativaPendencia ?? null);
    this.pendenciaModalVisible.set(true);
  }

  protected abrirModalPendenciaItem(item: any): void {
    const sol = this.solicitacao();
    if (!sol || !item?.id) return;
    this.pendenciaModalItemId.set(item.id);
    this.pendenciaModalItemLabel.set(`${item.material.codigo} — ${item.material.descricao}`);
    this.pendenciaModalMotivoAtual.set(item.motivoPendencia ?? null);
    this.pendenciaModalJustificativaAtual.set(item.justificativaPendencia ?? null);
    this.pendenciaModalVisible.set(true);
  }

  protected abrirSaldo(estoqueId: string | undefined, nome: string | undefined) {
    if (estoqueId && nome) {
      this.saldoDialog.show(estoqueId, nome);
    }
  }

  private shouldAutoOpenDelivery = false;

  ngOnInit() {
    this.route.queryParams?.subscribe((params) => {
      this.isPrintMode.set(params['mode'] === 'print');
      if (params['action'] === 'deliver') {
        this.shouldAutoOpenDelivery = true;
      }
    });
    this.loadData();
    this.route.params?.subscribe((params) => {
      if (params['id']) {
        this.loadData(params['id']);
      }
    });
  }

  ngAfterViewInit() {
    if (this.isPrintMode()) {
      setTimeout(() => window.print(), 1500);
    }
  }

  protected loadData(targetId?: string) {
    const id = targetId || this.route.snapshot.params['id'];
    if (!id) return;

    // Recarregar a mesma solicitação (depois de salvar algo) mantém a tela e a rolagem no lugar.
    if (this.solicitacao()?.id !== id) this.loading.set(true);
    forkJoin({
      solicitacao: this.almoxarifadoService.buscarSolicitacaoPorId(id),
      usuarios: this.almoxarifadoService.listarUsuariosAtivos(),
    }).subscribe({
      next: (res) => {
        res.usuarios.content.forEach((u) => this.usersMap.set(u.id, u.login));
        this.solicitacao.set(res.solicitacao);
        if (this.shouldAutoOpenDelivery && !this.isDeliveryMode() && (res.solicitacao.status === 'APROVADO' || res.solicitacao.status === 'ENTREGUE_PARCIAL')) {
          this.shouldAutoOpenDelivery = false;
          setTimeout(() => this.startDelivery(), 50);
        }
        // Durante a aprovação ou a entrega o rascunho fica como está (uma pendência salva recarrega a tela).
        if (!this.isEditMode()) this.itemsDraft.set(JSON.parse(JSON.stringify(res.solicitacao.itens)));
        this.loadReportSignatures(res.solicitacao.id);
        this.resolveWithdrawalResponsible(res.solicitacao);
        this.resolveEquipeSaque(res.solicitacao);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(getErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    this.revokeSignatureUrls();
  }

  protected voltar(): void {
    this.navegacao.back(['/almoxarifado/solicitacoes']);
  }

  protected getUserName(id?: string): string {
    if (!id) return '-';
    return this.usersMap.get(id) || id?.substring(0, 8);
  }

  protected getStatusTone(status: SolicitacaoStatus | undefined): CesTone {
    if (!status) return 'neutral';
    switch (status) {
      case 'ENTREGUE':
        return 'success';
      case 'APROVADO':
        return 'info';
      case 'PENDENTE':
      case 'ENTREGUE_PARCIAL':
        return 'warning';
      case 'REJEITADO':
        return 'danger';
      case 'ESTORNADO':
      default:
        return 'neutral';
    }
  }

  protected getStatusLabel(status: SolicitacaoStatus | undefined): string {
    if (!status) return '';
    switch (status) {
      case 'PENDENTE':
        return 'Pendente';
      case 'APROVADO':
        return 'Aprovada';
      case 'REJEITADO':
        return 'Rejeitada';
      case 'ENTREGUE_PARCIAL':
        return 'Entrega Parcial';
      case 'ENTREGUE':
        return 'Entregue';
      case 'ESTORNADO':
        return 'Estornada';
      default:
        return status;
    }
  }

  protected tipoLabel(tipo?: string | null): string {
    if (tipo === 'DEVOLUCAO_COMUM' || tipo === '222') return '222 — Devolução';
    return '221 — Saída Direta / Obra';
  }

  protected tipoTone(tipo?: string | null): CesTone {
    if (tipo === 'DEVOLUCAO_COMUM' || tipo === '222') return 'neutral';
    return 'info';
  }

  protected tipoIcon(tipo?: string | null): string {
    if (tipo === 'DEVOLUCAO_COMUM' || tipo === '222') return 'pi pi-trash';
    return 'pi pi-shopping-cart';
  }

  protected calculateTotal(itens: (ItemSolicitacaoResponse & { removed?: boolean })[]): number {
    return itens.filter((i) => !i.removed).reduce((acc, item) => acc + (item.quantidade || 0), 0);
  }

  protected printReport() {
    window.print();
  }

  protected confirmarEstorno() {
    this.openEstornoParcialModal();
  }

  protected openEstornoParcialModal() {
    const items =
      this.solicitacao()?.itens.filter(
        (i) => (i.quantidadeEntregaLiquida ?? i.quantidadeEntregue) > 0,
      ) || [];
    this.estornoItems.set(
      items.map((i) => ({
        ...i,
        selected: true,
        quantidadeEstorno: i.quantidadeEntregaLiquida ?? i.quantidadeEntregue,
      })),
    );
    this.displayEstornoParcialModal.set(true);
  }

  protected confirmarEstornoParcial() {
    const selected = this.estornoItems().filter((i) => i.selected);
    if (selected.length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'Selecione ao menos um item para estornar',
      });
      return;
    }

    if (selected.some((i) => (i.quantidadeEstorno || 0) <= 0)) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'A quantidade de estorno deve ser maior que zero',
      });
      return;
    }

    if (
      selected.some(
        (i) => (i.quantidadeEstorno || 0) > (i.quantidadeEntregaLiquida ?? i.quantidadeEntregue),
      )
    ) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'A quantidade de estorno não pode ser maior que a quantidade entregue',
      });
      return;
    }

    this.confirmationService.confirm({
      message:
        'Atenção! Esta ação é IRREVERSÍVEL. Deseja realmente estornar os itens selecionados? Os saldos retornarão ao estoque de origem.',
      header: 'Confirmar Estorno Parcial',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'SIM, ESTORNAR',
      rejectLabel: 'CANCELAR',
      acceptButtonStyleClass: 'p-button-danger !rounded-lg !text-[10px] !font-medium !px-6',
      rejectButtonStyleClass:
        'p-button-text p-button-secondary !rounded-lg !text-[10px] !font-medium !px-6',
      accept: () => {
        const request: EstornoParcialRequest = {
          itens: selected.map((i) => ({
            idItemSolicitacao: i.id,
            quantidade: i.quantidadeEstorno || 0,
          })),
        };

        this.saving.set(true);
        this.almoxarifadoService.estornarParcial(this.solicitacao()!.id, request).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Sucesso',
              detail: 'Estorno realizado com sucesso!',
            });
            this.displayEstornoParcialModal.set(false);
            this.loadData();
            this.saving.set(false);
          },
          error: (err) => {
            this.messageService.add({
              severity: 'error',
              summary: 'Erro no Estorno',
              detail: getErrorMessage(err),
            });
            this.saving.set(false);
          },
        });
      },
    });
  }

  // --- Fluxo de Aprovação ---
  protected startApproval() {
    this.itemsDraft.set(
      JSON.parse(JSON.stringify(this.solicitacao()!.itens)).map((i: ItemSolicitacaoResponse) => ({ ...i, quantidadeOriginal: i.quantidade })),
    );
    this.prepararAtendimento();
    this.isEditMode.set(true);
  }

  private prepararAtendimento() {
    this.idOrigemEscolhida.set(null);
    this.dataPrevistaEscolhida.set(null);
    if (this.opcoesOrigem().length || this.carregandoOrigens()) return;
    this.carregandoOrigens.set(true);
    this.almoxarifadoService.listarEstoquesAtivos().subscribe({
      next: (res) => {
        const idDestino = this.solicitacao()?.estoqueDestino?.id;
        this.opcoesOrigem.set(
          (res.content ?? [])
            .filter((e) => e.ativo && e.tipoEstoque?.nome?.toUpperCase() === 'FISICO' && e.id !== idDestino)
            .map((e) => ({ label: e.nomeIdentificador, value: e.id })),
        );
        this.carregandoOrigens.set(false);
      },
      error: () => this.carregandoOrigens.set(false),
    });
  }

  protected trocarOrigem(id: string | null) {
    if (!id || id === this.origemAtual()?.id) return;
    this.idOrigemEscolhida.set(id);
    // Saldos e séries são da origem: tudo que veio da anterior é refeito.
    this.saldoOrigem.set({});
    if (this.isDeliveryMode()) {
      this.entregaRascunho.update((linhas) => linhas.map((l) => (l.series.length ? { ...l, series: [] } : l)));
      this.carregarSaldosOrigem(this.entregaRascunho().map((l) => l.material.id));
    }
    if (this.selectedMaterialId()) this.onMaterialSelect(this.selectedMaterialId());
  }

  /** Só manda o que mudou: ausente, o backend mantém origem e data. */
  private alteracoesDoAtendimento(comData: boolean): { idEstoqueOrigem?: string; dataPrevista?: string } {
    const sol = this.solicitacao();
    const alteracoes: { idEstoqueOrigem?: string; dataPrevista?: string } = {};
    if (this.origemTrocada()) alteracoes.idEstoqueOrigem = this.idOrigemEscolhida()!;
    const data = dataIso(this.dataPrevistaEscolhida());
    if (comData && data && data !== sol?.dataPrevista?.substring(0, 10)) alteracoes.dataPrevista = data;
    return alteracoes;
  }

  protected definirQuantidadeAprovada(indice: number, valor: number | null) {
    this.itemsDraft.update((itens) => itens.map((it, i) => (i === indice ? { ...it, quantidade: valor ?? 0 } : it)));
  }

  protected cancelApproval() {
    this.isEditMode.set(false);
    this.loadData();
  }

  protected confirmApproval() {
    const items = this.itemsDraft().filter((i) => !i.removed);
    if (items.length === 0) {
      this.messageService.add({
        severity: 'error',
        summary: 'Erro',
        detail: 'A solicitação deve ter ao menos um item',
      });
      return;
    }

    this.confirmationService.confirm({
      message: 'Deseja confirmar a aprovação com as alterações realizadas?',
      header: 'Confirmar Aprovação',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'SIM, APROVAR',
      rejectLabel: 'CANCELAR',
      acceptButtonStyleClass: '!bg-primary-600 !border-primary-600',
      rejectButtonStyleClass: '!bg-red-600 !border-red-600',
      accept: () => {
        const currentItens = this.itemsDraft();
        const request: AprovarSolicitacaoRequest = {
          itensEditados: currentItens
            .filter((i) => !i.isNew && !i.removed)
            .map((i) => ({ idItemSolicitacao: i.id, novaQuantidade: i.quantidade })),
          itensAdicionados: currentItens
            .filter((i) => i.isNew)
            .map((i) => ({ idMaterial: i.material.id, quantidade: i.quantidade })),
          itensRemovidos: currentItens.filter((i) => i.removed && !i.isNew).map((i) => i.id),
          ...this.alteracoesDoAtendimento(true),
        };

        this.saving.set(true);
        this.almoxarifadoService.aprovarSolicitacao(this.solicitacao()!.id, request).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Sucesso',
              detail: 'Solicitação aprovada!',
            });
            this.isEditMode.set(false);
            this.loadData();
            this.saving.set(false);
          },
          error: (err) => {
            this.messageService.add({
              severity: 'error',
              summary: 'Erro na Aprovação',
              detail: getErrorMessage(err),
            });
            this.saving.set(false);
          },
        });
      },
    });
  }

  // --- Fluxo de Entrega ---
  protected startDelivery() {
    this.prepararAtendimento();
    this.isDeliveryMode.set(true);
    const rascunho = rascunhoDaEntrega(this.solicitacao()!.itens);
    this.entregaRascunho.set(rascunho);
    this.saldoOrigem.set({});
    this.carregarSaldosOrigem(rascunho.map((l) => l.material.id));
    this.prepareWithdrawalSelection();
    this.carregarSaldosNegativosEquipe();
  }

  private saldoNovo(idEstoque: string, idMaterial: string) {
    return this.almoxarifadoService.listarSaldos({ idEstoque, idMaterial, condicao: 'NOVO', ativo: true, size: 1 }).pipe(
      map((res) => Number(res.content?.[0]?.quantidade ?? 0)),
      catchError(() => of(null)),
    );
  }

  /** Saldo de cada material na origem, para o almoxarife ver se dá para atender antes de confirmar. */
  private carregarSaldosOrigem(idsMateriais: string[]) {
    const idOrigem = this.origemAtual()?.id;
    const novos = [...new Set(idsMateriais)].filter((id) => !(id in this.saldoOrigem()));
    if (!idOrigem || !novos.length) return;
    this.saldoOrigem.update((atual) => ({ ...atual, ...Object.fromEntries(novos.map((id) => [id, undefined])) }));
    for (const id of novos) {
      this.saldoNovo(idOrigem, id).subscribe((saldo) => {
        // Resposta de uma origem já trocada não sobrescreve a atual.
        if (this.origemAtual()?.id === idOrigem) this.saldoOrigem.update((atual) => ({ ...atual, [id]: saldo }));
      });
    }
  }

  protected definirQuantidadeEntrega(indice: number, valor: number | null) {
    this.entregaRascunho.update((linhas) =>
      linhas.map((l, i) => (i !== indice ? l : { ...l, quantidade: valor, ...(l.idItem ? {} : { solicitada: valor ?? 0, pendente: valor ?? 0 }) })),
    );
  }

  protected definirSeriesEntrega(indice: number, series: string[]) {
    this.entregaRascunho.update((linhas) => linhas.map((l, i) => (i === indice ? { ...l, series } : l)));
  }

  /** Só sai da solicitação o material que ainda não teve entrega nenhuma. */
  protected podeTirarDaSolicitacao(l: ItemEntregaRascunho): boolean {
    return !!l.idItem && l.jaEntregue <= 0;
  }

  protected alternarRemocaoEntrega(indice: number) {
    const linha = this.entregaRascunho()[indice];
    if (!linha) return;
    if (!linha.idItem) {
      this.entregaRascunho.update((linhas) => linhas.filter((_, i) => i !== indice));
      return;
    }
    this.entregaRascunho.update((linhas) => linhas.map((l, i) => (i === indice ? { ...l, removido: !l.removido } : l)));
  }

  /** Entrega todo o saldo do material comum; o com série sai pelas séries escolhidas. */
  protected preencherEntrega() {
    this.entregaRascunho.update((linhas) => linhas.map((l) => (l.material.detalhaSerie || l.removido || !l.idItem ? l : { ...l, quantidade: l.pendente })));
  }

  protected zerarEntrega() {
    this.entregaRascunho.update((linhas) => linhas.map((l) => (l.idItem ? { ...l, quantidade: 0, series: [] } : l)));
  }

  protected saldoNegativoDoMaterial(idMaterial: string | null | undefined): SaldoNegativoMaterial | null {
    return idMaterial ? (this.saldosNegativosEquipe().get(idMaterial) ?? null) : null;
  }

  private carregarSaldosNegativosEquipe() {
    this.saldosNegativosEquipe.set(new Map());
    const idEstoque = this.solicitacao()?.estoqueDestino?.id;
    if (!idEstoque) return;
    // Informativo: uma falha aqui não pode atrapalhar a entrega.
    this.almoxarifadoService.listarSaldosNegativos({ idEstoque }).subscribe({
      next: (saldos) => this.saldosNegativosEquipe.set(agruparSaldosNegativos(saldos)),
      error: () => this.saldosNegativosEquipe.set(new Map()),
    });
  }

  protected cancelDelivery() {
    this.isDeliveryMode.set(false);
    this.entregaRascunho.set([]);
    this.loadData();
  }

  protected displaySignatureTypeModal = signal(false);
  protected displayDigitalSignatureDashboard = signal(false);

  protected confirmDelivery() {
    if (!this.withdrawalCollaboratorId()) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Responsável obrigatório',
        detail: 'Selecione o colaborador responsável pelo saque.',
      });
      return;
    }
    const linhas = this.entregaRascunho();
    if (!linhas.some((l) => quantidadeDaLinha(l) > 0)) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Nada para entregar',
        detail: 'Informe a quantidade de ao menos um material ou escolha as séries do material com série.',
      });
      return;
    }
    const excedido = linhas.find((l) => l.idItem && quantidadeDaLinha(l) > l.pendente);
    if (excedido) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Quantidade acima do pedido',
        detail: `${excedido.material.codigo}: sai mais do que falta entregar (${excedido.pendente}).`,
      });
      return;
    }

    this.displaySignatureTypeModal.set(true);
  }

  public selectSignatureType(type: 'FISICA' | 'DIGITAL') {
    if (type === 'FISICA') {
      this.deliveryData.assinaturaAlmox = 'FISICA';
      this.deliveryData.assinaturaEquipe = 'FISICA';
      this.displaySignatureTypeModal.set(false);
      this.executeDelivery();
    } else {
      this.displaySignatureTypeModal.set(false);
      this.displayDigitalSignatureDashboard.set(true);
    }
  }

  protected executeDelivery() {
    if (!this.deliveryData.assinaturaAlmox || !this.deliveryData.assinaturaEquipe) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'As assinaturas são obrigatórias',
      });
      return;
    }

    const linhas = this.entregaRascunho();

    this.confirmationService.confirm({
      message: 'Deseja finalizar a entrega dos materiais?',
      header: 'Confirmar Entrega',
      icon: 'pi pi-box',
      acceptLabel: 'SIM, FINALIZAR ENTREGA',
      rejectLabel: 'CANCELAR',
      acceptButtonStyleClass: '!bg-green-600 !border-green-600',
      rejectButtonStyleClass: '!bg-red-600 !border-red-600',
      accept: () => {
        const request: EntregaSolicitacaoRequest = {
          assinaturaAlmox: this.deliveryData.assinaturaAlmox,
          assinaturaEquipe: this.deliveryData.assinaturaEquipe,
          idColaboradorSaque: this.withdrawalCollaboratorId()!,
          saqueForaEquipe: this.withdrawalOutsideTeam(),
          ...montarEntregaDoRelatorio(linhas),
          ...this.alteracoesDoAtendimento(false),
        };

        this.saving.set(true);
        this.almoxarifadoService.registrarEntrega(this.solicitacao()!.id, request).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Sucesso',
              detail: 'Entrega realizada com sucesso!',
            });
            this.isDeliveryMode.set(false);
            this.displayDigitalSignatureDashboard.set(false);
            this.entregaRascunho.set([]);
            this.loadData();
            this.saving.set(false);
          },
          error: (err) => {
            this.messageService.add({
              severity: 'error',
              summary: 'Erro na Entrega',
              detail: getErrorMessage(err),
            });
            this.saving.set(false);
          },
        });
      },
    });
  }

  // --- Ações Comuns ---
  protected canRemoveItem(item: ReturnType<typeof this.itemsDraft>[number]): boolean {
    if (item.isNew) return true;
    const entregaBruta = item.quantidadeEntregueBruta ?? 0;
    const entregaAnterior =
      item.jaEntregueOriginal ?? item.quantidadeEntregaLiquida ?? item.quantidadeEntregue ?? 0;
    return entregaBruta <= 0 && entregaAnterior <= 0;
  }

  protected removeItem(index: number) {
    const items = [...this.itemsDraft()];
    const item = items[index];
    if (item.isNew) {
      items.splice(index, 1);
    } else if (item.removed) {
      item.removed = false;
    } else if (!this.canRemoveItem(item)) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Remoção não permitida',
        detail: 'Materiais que já possuem entrega registrada não podem ser removidos da solicitação.',
      });
      return;
    } else {
      item.removed = true;
    }
    this.itemsDraft.set(items);
  }

  protected onMaterialSelect(id: string | null) {
    this.selectedMaterialId.set(id);
    this.selectedMaterial.set(null);
    this.saldosDoSelecionado.set(null);
    const sol = this.solicitacao();
    if (id && sol) {
      forkJoin({ origem: this.saldoNovo(this.origemAtual()?.id ?? sol.estoqueOrigem.id, id), destino: this.saldoNovo(sol.estoqueDestino.id, id) }).subscribe(
        ({ origem, destino }) => {
          if (this.selectedMaterialId() === id) this.saldosDoSelecionado.set({ origem: origem ?? 0, destino: destino ?? 0 });
        },
      );
    }
    if (id) {
      this.almoxarifadoService.buscarMaterialPorId(id).subscribe({
        next: (material) => {
          if (this.selectedMaterialId() === id) this.selectedMaterial.set(material);
        },
        error: () => {
          if (this.selectedMaterialId() === id) this.selectedMaterial.set(null);
        },
      });
    }
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

  protected addItem() {
    const material = this.selectedMaterial();
    const quantity = this.itemQuantity();
    if (!material || quantity === null || quantity < 0.001) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Quantidade obrigatória',
        detail: 'Escolha um material e informe uma quantidade maior ou igual a 0,001.',
      });
      return;
    }

    const jaNaLista = this.isDeliveryMode()
      ? this.entregaRascunho().some((l) => l.material.id === material.id && !l.removido)
      : this.itemsDraft().some((i) => i.material.id === material.id && !i.removed);
    if (jaNaLista) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Aviso',
        detail: `${material.codigo} já está na lista: ajuste a quantidade na linha dele.`,
      });
      this.limparAdicao();
      return;
    }

    if (this.isDeliveryMode()) {
      this.entregaRascunho.update((linhas) => [
        ...linhas,
        {
          chave: `novo-${material.id}`,
          idItem: null,
          material,
          solicitada: quantity,
          jaEntregue: 0,
          pendente: quantity,
          quantidade: quantity,
          series: [],
        },
      ]);
      this.carregarSaldosOrigem([material.id]);
    } else {
      this.itemsDraft.update((itens) => [...itens, { id: '', material, quantidade: quantity, quantidadeEntregue: 0, isNew: true }]);
    }
    this.limparAdicao();
  }

  private limparAdicao() {
    this.selectedMaterial.set(null);
    this.selectedMaterialId.set(null);
    this.itemQuantity.set(null);
    this.saldosDoSelecionado.set(null);
  }

  protected rejectSolicitacao() {
    this.rejectionData = { idMotivoRejeicao: '', justificativa: '' };
    this.almoxarifadoService.listarMotivosRejeicaoAtivos().subscribe({
      next: (res) => {
        this.motivosRejeicaoOptions.set(res.content);
        this.displayRejectModal.set(true);
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Erro ao carregar motivos de rejeição',
        });
      },
    });
  }

  protected confirmRejection() {
    if (
      !this.rejectionData.idMotivoRejeicao ||
      !this.rejectionData.justificativa ||
      this.rejectionData.justificativa.trim().length < 5
    ) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'Motivo e justificativa (mínimo de 5 caracteres) são obrigatórios',
      });
      return;
    }

    const request: RejeitarSolicitacaoRequest = {
      idMotivoRejeicao: this.rejectionData.idMotivoRejeicao,
      justificativa: this.rejectionData.justificativa.trim(),
    };

    this.saving.set(true);
    this.almoxarifadoService.rejeitarSolicitacao(this.solicitacao()!.id, request).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Solicitação rejeitada com sucesso',
        });
        this.displayRejectModal.set(false);
        this.loadData();
        this.saving.set(false);
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: getErrorMessage(err),
        });
        this.saving.set(false);
      },
    });
  }

  // --- Assinatura Digital e Física ---
  public openSignaturePad(field: 'almox' | 'equipe') {
    this.currentSignatureField.set(field);
    this.displayDigitalSignatureDashboard.set(false);
    this.displaySignatureModal.set(true);
    setTimeout(() => this.initSignaturePad(), 100);
  }

  private initSignaturePad() {
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

  public startDrawing(event: MouseEvent | TouchEvent) {
    this.isDrawing = true;
    this.draw(event);
  }

  public stopDrawing() {
    this.isDrawing = false;
    if (this.signaturePadCtx) {
      this.signaturePadCtx.beginPath();
    }
  }

  public draw(event: MouseEvent | TouchEvent) {
    if (!this.isDrawing || !this.signaturePadCtx || !this.signatureCanvas) return;
    event.preventDefault();

    const canvas = this.signatureCanvas.nativeElement;
    const rect = canvas.getBoundingClientRect();

    let clientX, clientY;
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

  public clearSignaturePad() {
    if (this.signaturePadCtx && this.signatureCanvas) {
      const canvas = this.signatureCanvas.nativeElement;
      this.signaturePadCtx.clearRect(0, 0, canvas.width, canvas.height);
      this.signaturePadCtx.beginPath();
    }
  }

  public saveSignature() {
    const canvas = this.signatureCanvas?.nativeElement;
    if (canvas) {
      const base64 = canvas.toDataURL('image/png');
      const field = this.currentSignatureField();
      if (field === 'almox') {
        this.deliveryData.assinaturaAlmox = base64;
      } else if (field === 'equipe') {
        this.deliveryData.assinaturaEquipe = base64;
      }
      this.displaySignatureModal.set(false);
      this.displayDigitalSignatureDashboard.set(true);
    }
  }

  public setPhysicalSignature(field: 'almox' | 'equipe') {
    if (field === 'almox') {
      this.deliveryData.assinaturaAlmox = 'FISICA';
    } else {
      this.deliveryData.assinaturaEquipe = 'FISICA';
    }
  }

  public clearSignatureData(field: 'almox' | 'equipe') {
    if (field === 'almox') {
      this.deliveryData.assinaturaAlmox = '';
    } else {
      this.deliveryData.assinaturaEquipe = '';
    }
  }

  public isBase64(str: string | undefined | null): boolean {
    if (!str) return false;
    return str.startsWith('data:image/');
  }

  protected onWithdrawalOutsideTeamChange(value: boolean): void {
    this.withdrawalOutsideTeam.set(value);
    if (!value) {
      const memberIds = new Set(this.withdrawalTeamMembers().map((item) => item.idColaborador));
      if (!memberIds.has(this.withdrawalCollaboratorId() ?? '')) {
        this.withdrawalCollaboratorId.set(null);
      }
    }
  }

  protected searchWithdrawalCollaborators(query: string): void {
    if (this.withdrawalOutsideTeam()) this.withdrawalLookup.search(query);
  }

  protected loadMoreWithdrawalCollaborators(): void {
    if (this.withdrawalOutsideTeam()) this.withdrawalLookup.loadNext();
  }

  protected retryWithdrawalCollaborators(): void {
    if (this.withdrawalOutsideTeam()) this.withdrawalLookup.retry();
  }

  private prepareWithdrawalSelection(): void {
    const solicitacao = this.solicitacao();
    const teamId = solicitacao?.idEquipe ?? solicitacao?.estoqueDestino?.idEquipe;
    const collaboratorId = solicitacao?.idColaboradorSaque ?? null;
    const outsideTeam = solicitacao?.saqueForaEquipe ?? !teamId;
    this.withdrawalCollaboratorId.set(collaboratorId);
    this.withdrawalOutsideTeam.set(outsideTeam);
    this.withdrawalTeamMembers.set([]);
    if (outsideTeam && collaboratorId) {
      this.rhService.buscarColaboradorPorId(collaboratorId).subscribe({
        next: (colaborador) =>
          this.withdrawalLookup.seedSelected({
            value: colaborador.id,
            label: colaborador.nomeCompleto,
          }),
      });
    }
    if (!teamId || outsideTeam) return;
    this.withdrawalTeamLoading.set(true);
    this.rhService.listarMembrosAtivos(teamId).subscribe({
      next: (members) => {
        this.withdrawalTeamMembers.set(members);
        this.withdrawalTeamLoading.set(false);
      },
      error: () => this.withdrawalTeamLoading.set(false),
    });
  }

  private resolveWithdrawalResponsible(solicitacao: SolicitacaoResponse): void {
    const operacao = [...(solicitacao.operacoes ?? [])]
      .reverse()
      .find((item) => item.tipo === 'ENTREGA' && item.idColaboradorSaque);
    const id = operacao?.idColaboradorSaque;
    if (!id) {
      this.responsavelSaqueNome.set(this.getUserName(solicitacao.idRequisitante) || 'Responsável pelo Saque');
      return;
    }
    if (typeof (this.rhService as any).buscarColaboradorPorId !== 'function') {
      this.responsavelSaqueNome.set(id.slice(0, 8));
      return;
    }
    this.rhService.buscarColaboradorPorId(id).subscribe({
      next: (colaborador) => this.responsavelSaqueNome.set(colaborador.nomeCompleto),
      error: () => this.responsavelSaqueNome.set(id.slice(0, 8)),
    });
  }

  private resolveEquipeSaque(solicitacao: SolicitacaoResponse): void {
    const idEquipe = solicitacao.idEquipe ?? null;
    if (!idEquipe) {
      this.equipeSaqueName.set('—');
      return;
    }
    this.rhService.buscarEquipePorId(idEquipe).subscribe({
      next: (equipe) => {
        const nome = equipe.tipoEquipe?.nome
          ? `${equipe.codigo} · ${equipe.tipoEquipe.nome}`
          : equipe.codigo ?? idEquipe.slice(0, 8);
        this.equipeSaqueName.set(nome);
      },
      error: () => this.equipeSaqueName.set(idEquipe.slice(0, 8)),
    });
  }

  private loadReportSignatures(id: string): void {
    this.revokeSignatureUrls();
    if (typeof (this.almoxarifadoService as any).buscarAssinaturaSolicitacao !== 'function') return;
    this.loadSignature(id, 'ALMOX', this.assinaturaAlmoxUrl);
    this.loadSignature(id, 'EQUIPE', this.assinaturaEquipeUrl);
  }

  private loadSignature(
    id: string,
    parte: 'ALMOX' | 'EQUIPE',
    target: ReturnType<typeof signal<string | null>>,
  ): void {
    this.almoxarifadoService.buscarAssinaturaSolicitacao(id, parte).subscribe({
      next: (blob) => {
        if (blob.size > 0) target.set(URL.createObjectURL(blob));
      },
      error: () => target.set(null),
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
