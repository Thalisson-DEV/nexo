import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { MenuModule } from 'primeng/menu';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesDialogShellComponent,
  CesFileInputComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesNumberInputComponent,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  CesTone,
  NotificationService
} from '@ces/ui';
import { Subject, catchError, debounceTime, map, of, switchMap } from 'rxjs';

import { CLASSES_TENSAO, ClasseTensao } from '../../../core/models/obras-kit.models';
import {
  AnexoObraResponse,
  CarteiraObraRequest,
  HistoricoStatusObraResponse,
  ItemOrcamentoObraResponse,
  ObraProximaResponse,
  ObraResponse,
  ObraResumoResponse,
  OrcamentoObraResponse,
  PRIORIDADES_OBRA,
  PrioridadeObra,
  StatusObraResponse,
  SubprojetoResponse,
  TipoVinculoSubprojeto
} from '../../../core/models/obras.models';
import { EquipeResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ProjecaoObraSolicitacoesResponse, SolicitacaoResumoObra } from '../../../core/models/solicitacao-material-tecnica.models';
import { AuthService } from '../../../core/services/auth.service';
import { ContratoService } from '../../../core/services/contrato.service';
import { ObrasService } from '../../../core/services/obras.service';
import { RhService } from '../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../core/services/solicitacao-material-tecnica.service';
import { ThemeService } from '../../../core/services/theme.service';
import { AnalisarComCopilotoComponent } from '../../../shared/components/analisar-com-copiloto/analisar-com-copiloto.component';
import {
  statusLabel as statusSolicitacaoLabel,
  statusTone as statusSolicitacaoTone,
  tipoTecnicoEhAdicao,
  tipoTecnicoIcon,
  tipoTecnicoLabel
} from '../../almoxarifado/solicitacoes-material/solicitacao-tecnica-presentation';
import {
  TIPO_VINCULO_OPTIONS,
  formatarBytes,
  formatarDataHora,
  formatarDataParaApi,
  formatarMoeda,
  parseDataBrParaDate,
  statusIcon,
  statusTone
} from '../obras-ui';
import { AcaoInsightObra, insightsDaObra } from './obra-insights';

declare var L: any;

type Carga<T> = { estado: 'loading' } | { estado: 'error'; mensagem: string } | { estado: 'ready'; dados: T };

const RAIOS: CesSelectOption<number>[] = [
  { label: '5 km', value: 5 },
  { label: '10 km', value: 10 },
  { label: '20 km', value: 20 },
  { label: '50 km', value: 50 }
];
const DIA_MS = 86_400_000;
const INSIGHTS_VISIVEIS = 6;
/** Escurece os tiles claros do mapa no tema escuro. */
const FILTRO_TILES_ESCURO = 'invert(1) hue-rotate(180deg) brightness(0.95) contrast(0.9)';

/** Marco da linha do tempo do prazo, em % do intervalo exibido. */
interface MarcoPrazo {
  rotulo: string;
  data: string;
  posicao: number;
  tipo: 'inicio' | 'hoje' | 'previsao' | 'regulado' | 'conclusao';
}

/**
 * Relatório da obra: tudo o que se sabe sobre ela numa página de aplicação (não A4). Cabeçalho com situação e
 * prazo, indicadores financeiros e de deslocamento, dados de cadastro e carteira, solicitações de material,
 * orçamento previsto, anexos, subprojetos, mapa com obras próximas, histórico do status e equipes.
 */
@Component({
  selector: 'app-obra-detalhe',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    MenuModule,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesDialogShellComponent,
    CesFileInputComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesNumberInputComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent,
    AnalisarComCopilotoComponent
  ],
  templateUrl: './obra-detalhe.component.html',
  styleUrl: './obra-detalhe.component.css'
})
export class ObraDetalheComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(ObrasService);
  private readonly contratos = inject(ContratoService);
  private readonly rhService = inject(RhService);
  private readonly solicitacaoService = inject(SolicitacaoMaterialTecnicaService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly tema = inject(ThemeService);
  private readonly mapEl = viewChild<ElementRef<HTMLDivElement>>('mapa');
  private leafletMap: any = null;

  private readonly permissoes = inject(AuthService).currentUser()?.permissoes ?? [];
  protected readonly podeEscrever = this.permissoes.includes('obras:obra:write');
  protected readonly podeAnexar = this.permissoes.includes('obras:anexos:write');

  // Helpers de template
  protected readonly moeda = formatarMoeda;
  protected readonly bytes = formatarBytes;
  protected readonly dataHora = formatarDataHora;
  protected readonly statusTone = statusTone;
  protected readonly statusIcon = statusIcon;
  protected readonly statusSolicitacaoLabel = statusSolicitacaoLabel;
  protected readonly statusSolicitacaoTone = statusSolicitacaoTone;
  protected readonly tipoTecnicoLabel = tipoTecnicoLabel;
  protected readonly tipoTecnicoIcon = tipoTecnicoIcon;
  protected readonly tipoTecnicoEhAdicao = tipoTecnicoEhAdicao;
  protected readonly raios = RAIOS;
  protected readonly tensoes = CLASSES_TENSAO;
  protected readonly prioridades = PRIORIDADES_OBRA;
  protected readonly tiposVinculo: CesSelectOption<TipoVinculoSubprojeto>[] = TIPO_VINCULO_OPTIONS.map((t) => ({ label: t.label, value: t.value }));
  protected readonly emissao = new Date();

  // ═══════════════════════════════════════════════════════════════════════
  // Estado principal
  // ═══════════════════════════════════════════════════════════════════════

  protected readonly obra = signal<ObraResponse | null>(null);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly equipes = signal<EquipeResponse[]>([]);
  protected readonly statusList = signal<StatusObraResponse[]>([]);
  protected readonly tiposAnexo = signal<CesSelectOption<string>[]>([]);
  protected readonly solicitacoes = signal<Carga<ProjecaoObraSolicitacoesResponse | null>>({ estado: 'loading' });
  protected readonly historico = signal<Carga<HistoricoStatusObraResponse[]>>({ estado: 'loading' });
  protected readonly proximas = signal<Carga<ObraProximaResponse[]>>({ estado: 'loading' });
  protected readonly orcamento = signal<Carga<OrcamentoObraResponse>>({ estado: 'loading' });
  protected readonly raio = signal(10);
  protected readonly recalculando = signal(false);

  protected readonly contratoOpcoes = toSignal(
    this.contratos.listarContratosAtivos().pipe(
      map((p) => p.content.map((c) => ({ label: `${c.numeroContrato} · ${c.cliente}`, value: c.id }))),
      catchError(() => of([] as CesSelectOption<string>[]))
    ),
    { initialValue: [] as CesSelectOption<string>[] }
  );

  protected readonly carteira = computed(() => this.obra()?.carteira ?? null);
  protected readonly temCoordenadas = computed(() => this.obra()?.latitude != null && this.obra()?.longitude != null);
  protected readonly nomeContrato = computed(() => {
    const id = this.carteira()?.idContrato;
    if (!id) return null;
    return this.contratoOpcoes().find((o) => o.value === id)?.label ?? 'Contrato inativo';
  });
  protected readonly rotuloPrioridade = computed(() => PRIORIDADES_OBRA.find((p) => p.value === this.carteira()?.prioridade)?.label ?? null);
  protected readonly tomPrioridade = computed<CesTone>(() => {
    const p = this.carteira()?.prioridade;
    return p === 'URGENTE' ? 'danger' : p === 'ALTA' ? 'warning' : p === 'NORMAL' ? 'info' : 'neutral';
  });

  /** Solicitações de material num formato plano (obras corretivas/preventivas agrupam por ocorrência). */
  protected readonly listaSolicitacoes = computed<(SolicitacaoResumoObra & { ocorrencia?: string | null })[]>(() => {
    const carga = this.solicitacoes();
    if (carga.estado !== 'ready' || !carga.dados) return [];
    const proj = carga.dados;
    if (proj.ocorrencias?.length) {
      return proj.ocorrencias.flatMap((oc) => oc.solicitacoes.map((s) => ({ ...s, ocorrencia: oc.codigo || 'Geral' })));
    }
    return proj.solicitacoes ?? [];
  });

  /** Linha do tempo do prazo: início, hoje, previsão de fim, prazo regulado e conclusão. */
  protected readonly prazo = computed(() => {
    const o = this.obra();
    if (!o) return null;
    const inicio = parseDataBrParaDate(o.dataInicio);
    const previsao = parseDataBrParaDate(o.dataPrevisaoFim);
    if (!inicio || !previsao) return null;
    const regulado = parseDataBrParaDate(this.carteira()?.prazoRegulado);
    const conclusao = parseDataBrParaDate(o.dataFimEfetiva);
    const hoje = new Date();
    hoje.setHours(0, 0, 0, 0);
    const datas = [inicio, previsao, regulado, conclusao, hoje].filter((d): d is Date => !!d).map((d) => d.getTime());
    const min = Math.min(...datas);
    const max = Math.max(...datas);
    const pos = (d: Date) => (max === min ? 0 : ((d.getTime() - min) / (max - min)) * 100);
    const marcos: MarcoPrazo[] = [
      { rotulo: 'Início', data: o.dataInicio, posicao: pos(inicio), tipo: 'inicio' },
      { rotulo: 'Previsão', data: o.dataPrevisaoFim, posicao: pos(previsao), tipo: 'previsao' }
    ];
    if (regulado) marcos.push({ rotulo: 'Prazo regulado', data: this.carteira()!.prazoRegulado!, posicao: pos(regulado), tipo: 'regulado' });
    if (conclusao) marcos.push({ rotulo: 'Conclusão', data: o.dataFimEfetiva!, posicao: pos(conclusao), tipo: 'conclusao' });
    else marcos.push({ rotulo: 'Hoje', data: hoje.toLocaleDateString('pt-BR'), posicao: pos(hoje), tipo: 'hoje' });

    const limite = regulado && regulado < previsao ? regulado : previsao;
    const dias = Math.round((limite.getTime() - hoje.getTime()) / DIA_MS);
    const total = Math.max(DIA_MS, previsao.getTime() - inicio.getTime());
    const decorrido = Math.min(100, Math.max(0, ((hoje.getTime() - inicio.getTime()) / total) * 100));
    let situacao: { texto: string; tom: CesTone; icone: string };
    if (conclusao) situacao = { texto: `Concluída em ${o.dataFimEfetiva}`, tom: 'success', icone: 'pi-check-circle' };
    else if (dias < 0) situacao = { texto: `${Math.abs(dias)} ${Math.abs(dias) === 1 ? 'dia' : 'dias'} de atraso`, tom: 'danger', icone: 'pi-exclamation-circle' };
    else if (dias <= 15) situacao = { texto: `Faltam ${dias} ${dias === 1 ? 'dia' : 'dias'}`, tom: 'warning', icone: 'pi-clock' };
    else situacao = { texto: `Faltam ${dias} dias`, tom: 'info', icone: 'pi-calendar' };
    return { marcos, situacao, decorrido: Math.round(decorrido), preenchimento: conclusao ? pos(conclusao) : pos(hoje) };
  });

  /** Pontos de atenção entre os indicadores e os dados; cargas em andamento não geram insight. */
  protected readonly insights = computed(() => {
    const obra = this.obra();
    if (!obra) return [];
    return insightsDaObra({
      obra,
      hoje: this.hoje,
      orcamento: this.dados(this.orcamento()),
      totalSolicitacoes: this.solicitacoes().estado === 'ready' ? this.listaSolicitacoes().length : null,
      totalProximas: this.dados(this.proximas())?.length ?? null,
      raioKm: this.raio(),
      dataUltimoStatus: this.dados(this.historico())?.[0]?.data ?? null
    }).map((i) => (i.acao && !this.podeAcionar(i.acao.tipo) ? { ...i, acao: undefined } : i));
  });
  protected readonly todosInsights = signal(false);
  protected readonly insightsExibidos = computed(() => (this.todosInsights() ? this.insights() : this.insights().slice(0, INSIGHTS_VISIVEIS)));
  protected readonly insightsOcultos = computed(() => this.insights().length - INSIGHTS_VISIVEIS);
  protected readonly resumoInsights = computed(() => {
    const lista = this.insights();
    const graves = lista.filter((i) => i.tom === 'danger').length;
    const avisos = lista.filter((i) => i.tom === 'warning').length;
    return [graves && `${graves} ${graves === 1 ? 'crítico' : 'críticos'}`, avisos && `${avisos} ${avisos === 1 ? 'aviso' : 'avisos'}`]
      .filter(Boolean)
      .join(' · ');
  });

  /** O que dá para atualizar na obra, conforme as permissões. Fica num só botão para não lotar a barra. */
  protected readonly acoesAtualizar = computed<MenuItem[]>(() => {
    const o = this.obra();
    const itens: MenuItem[] = [];
    if (this.podeEscrever) {
      itens.push(
        { label: 'Registrar status', icon: 'pi pi-sync', command: () => this.abrirStatus() },
        { label: 'Editar carteira', icon: 'pi pi-briefcase', command: () => this.abrirCarteira() },
        { label: 'Editar cadastro e equipes', icon: 'pi pi-pencil', command: () => this.editar() }
      );
    }
    if (this.podeAnexar) itens.push({ label: 'Adicionar anexo', icon: 'pi pi-paperclip', command: () => this.abrirAnexo() });
    if (this.podeEscrever && !o?.obraPaiId && !o?.statusConcluida) {
      itens.push({ label: 'Vincular subprojeto', icon: 'pi pi-sitemap', command: () => this.abrirVinculo() });
    }
    return itens;
  });

  protected readonly colunasOrcamento: CesDataTableColumn<ItemOrcamentoObraResponse>[] = [
    { key: 'codigo', header: 'Serviço', type: 'custom', priority: 1, value: (i) => i.codigo, display: (i) => `${i.codigo} ${i.descricao ?? ''}` },
    { key: 'quantidade', header: 'Qtd.', width: '7rem', value: (i) => i.quantidade, display: (i) => `${i.quantidade.toLocaleString('pt-BR')} ${i.unidade ?? ''}` },
    { key: 'valorUnitario', header: 'Unitário', type: 'currency', width: '8rem' },
    { key: 'valorTotal', header: 'Total', type: 'currency', priority: 1, width: '9rem' }
  ];
  protected readonly chave = (r: { id: string }) => r.id;

  // ═══════════════════════════════════════════════════════════════════════
  // Drawers
  // ═══════════════════════════════════════════════════════════════════════

  // Status operacional
  protected readonly statusAberto = signal(false);
  protected readonly salvandoStatus = signal(false);
  protected readonly novoStatusId = signal<string | null>(null);
  protected readonly novaDataFim = signal<Date | null>(null);
  protected readonly observacaoStatus = signal('');
  protected readonly opcoesStatus = computed(() => this.statusList().map((s) => ({ label: s.nome, value: s.id })));
  protected readonly statusConcluido = computed(() => this.statusList().find((s) => s.id === this.novoStatusId())?.concluida ?? false);
  protected readonly hoje = new Date();

  // Carteira
  protected readonly carteiraAberta = signal(false);
  protected readonly salvandoCarteira = signal(false);
  protected readonly cIdContrato = signal<string | null>(null);
  protected readonly cTensao = signal<ClasseTensao | null>(null);
  protected readonly cPrioridade = signal<PrioridadeObra | null>(null);
  protected readonly cPrazoRegulado = signal<Date | null>(null);
  protected readonly cLote = signal('');
  protected readonly cSituacao = signal('');
  protected readonly cValorProjeto = signal<number | null>(null);
  protected readonly cMaterial = signal<number | null>(null);
  protected readonly cServico = signal<number | null>(null);
  private carteiraOriginal = '';
  protected readonly carteiraAlterada = computed(() => this.assinaturaCarteira() !== this.carteiraOriginal);

  // Anexo
  protected readonly anexoAberto = signal(false);
  protected readonly enviandoAnexo = signal(false);
  protected readonly anexoTipo = signal<string | null>(null);
  protected readonly anexoArquivo = signal<File | null>(null);
  protected readonly anexoObservacao = signal('');

  // Subprojeto
  protected readonly vinculoAberto = signal(false);
  protected readonly vinculando = signal(false);
  protected readonly obraFilha = signal<string | null>(null);
  protected readonly tipoVinculo = signal<TipoVinculoSubprojeto>(TipoVinculoSubprojeto.KIT);
  protected readonly obrasParaVinculo = signal<ObraResumoResponse[]>([]);
  protected readonly buscandoObras = signal(false);
  protected readonly opcoesObrasVinculo = computed(() => this.obrasParaVinculo().map((o) => ({ label: `${o.numero} · ${o.titulo}`, value: o.id })));
  private readonly buscaObras$ = new Subject<string>();

  // Pré-visualização de anexo
  protected readonly previewAberto = signal(false);
  protected readonly previewNome = signal('');
  protected readonly previewTipo = signal<'pdf' | 'image' | 'other'>('other');
  protected readonly carregandoPreview = signal(false);
  private readonly previewBruto = signal<string | null>(null);
  protected readonly previewUrl = signal<SafeResourceUrl | null>(null);

  constructor() {
    this.carregarLookups();
    this.route.paramMap.subscribe((params) => {
      const id = params.get('id');
      if (id === 'novo' || id === 'nova') {
        void this.router.navigate(['/obras/nova']);
      } else if (id) {
        this.carregarObra(id);
      } else {
        void this.router.navigate(['/obras']);
      }
    });
    // Recarrega as obras próximas ao trocar o raio.
    effect(() => {
      const raio = this.raio();
      untracked(() => {
        const o = this.obra();
        if (o && this.temCoordenadas() && this.proximas().estado !== 'loading') this.carregarProximas(o.id, raio);
      });
    });
    this.buscaObras$
      .pipe(
        debounceTime(300),
        switchMap((termo) => {
          this.buscandoObras.set(true);
          return this.service.listarObras({ buscaGlobal: termo || undefined, apenasPrincipais: true, size: 20 }).pipe(catchError(() => of(null)));
        })
      )
      .subscribe((res) => {
        this.buscandoObras.set(false);
        const o = this.obra();
        const jaVinculadas = new Set((o?.subprojetos ?? []).map((s) => s.id));
        this.obrasParaVinculo.set((res?.content ?? []).filter((x) => x.id !== o?.id && !jaVinculadas.has(x.id)));
      });
    // O mapa acompanha o tema: tiles e cores dos marcadores são definidos ao desenhar.
    effect(() => {
      this.tema.isDarkMode();
      untracked(() => {
        if (this.leafletMap) setTimeout(() => this.renderizarMapa(), 0);
      });
    });
    inject(DestroyRef).onDestroy(() => {
      this.fecharPreview();
      this.destruirMapa();
    });
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Carga
  // ═══════════════════════════════════════════════════════════════════════

  protected carregarObra(id: string, silencioso = false): void {
    if (!silencioso) this.carregando.set(true);
    this.erro.set(null);
    this.service.buscarObraPorId(id).subscribe({
      next: (dados) => {
        this.obra.set(dados);
        this.carregando.set(false);
        this.carregarEquipes(dados.equipes ?? []);
        this.carregarSolicitacoes(dados.id);
        this.carregarHistorico(dados.id);
        this.carregarOrcamento(dados.id);
        if (dados.latitude != null && dados.longitude != null) {
          this.carregarProximas(dados.id, this.raio());
        } else {
          this.proximas.set({ estado: 'ready', dados: [] });
          this.destruirMapa();
        }
      },
      error: (e) => {
        this.carregando.set(false);
        this.erro.set(getErrorMessage(e));
      }
    });
  }

  protected recarregar(): void {
    const id = this.obra()?.id;
    if (id) this.carregarObra(id, true);
  }

  protected carregarSolicitacoes(id = this.obra()!.id): void {
    this.solicitacoes.set({ estado: 'loading' });
    this.solicitacaoService.obterProjecaoObra(id).subscribe({
      next: (dados) => this.solicitacoes.set({ estado: 'ready', dados }),
      error: (e) => this.solicitacoes.set({ estado: 'error', mensagem: getErrorMessage(e) })
    });
  }

  protected carregarHistorico(id = this.obra()!.id): void {
    this.historico.set({ estado: 'loading' });
    this.service.listarHistoricoStatus(id).subscribe({
      next: (dados) => this.historico.set({ estado: 'ready', dados }),
      error: (e) => this.historico.set({ estado: 'error', mensagem: getErrorMessage(e) })
    });
  }

  protected carregarOrcamento(id = this.obra()!.id): void {
    this.orcamento.set({ estado: 'loading' });
    this.service.buscarOrcamento(id).subscribe({
      next: (dados) => this.orcamento.set({ estado: 'ready', dados }),
      error: (e) => this.orcamento.set({ estado: 'error', mensagem: getErrorMessage(e) })
    });
  }

  protected carregarProximas(id = this.obra()!.id, raio = this.raio()): void {
    this.proximas.set({ estado: 'loading' });
    this.service.listarObrasProximas(id, raio).subscribe({
      next: (dados) => {
        this.proximas.set({ estado: 'ready', dados });
        setTimeout(() => this.renderizarMapa(), 0);
      },
      error: (e) => {
        this.proximas.set({ estado: 'error', mensagem: getErrorMessage(e) });
        setTimeout(() => this.renderizarMapa(), 0);
      }
    });
  }

  private carregarEquipes(ids: string[]): void {
    if (!ids.length) {
      this.equipes.set([]);
      return;
    }
    this.rhService.listarEquipes({ ativo: true, size: 200 }).subscribe({
      next: (res) => {
        const set = new Set(ids);
        this.equipes.set((res.content ?? []).filter((e) => set.has(e.id)));
      }
    });
  }

  private carregarLookups(): void {
    this.service.listarStatusAtivos().subscribe({ next: (r) => this.statusList.set(r) });
    this.service.listarTiposAnexosAtivos().subscribe({ next: (r) => this.tiposAnexo.set(r.map((t) => ({ label: t.nome, value: t.id }))) });
  }

  protected dados<T>(carga: Carga<T>): T | null {
    return carga.estado === 'ready' ? carga.dados : null;
  }

  protected mensagem(carga: Carga<unknown>): string | undefined {
    return carga.estado === 'error' ? carga.mensagem : undefined;
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Formatação
  // ═══════════════════════════════════════════════════════════════════════

  protected km(valor?: number | null): string {
    return valor == null ? '—' : `${valor.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} km`;
  }

  protected duracao(minutos?: number | null): string {
    if (minutos == null) return '—';
    const h = Math.floor(minutos / 60);
    return h ? `${h} h ${String(minutos % 60).padStart(2, '0')} min` : `${minutos} min`;
  }

  protected iconeArquivo(nome: string): string {
    const n = nome.toLowerCase();
    if (n.endsWith('.pdf')) return 'pi-file-pdf';
    if (/\.(png|jpe?g|webp|gif|svg)$/.test(n)) return 'pi-image';
    if (/\.(xlsx?|csv)$/.test(n)) return 'pi-file-excel';
    if (/\.(docx?)$/.test(n)) return 'pi-file-word';
    return 'pi-file';
  }

  protected iniciais(texto?: string | null): string {
    return (texto ?? '?').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase();
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Ações gerais
  // ═══════════════════════════════════════════════════════════════════════

  protected editar(): void {
    const id = this.obra()?.id;
    if (id) void this.router.navigate(['/obras', id, 'editar']);
  }

  protected voltar(): void {
    void this.router.navigate(['/obras']);
  }

  protected solicitarMateriais(): void {
    const id = this.obra()?.id;
    if (id) void this.router.navigate(['/almoxarifado/solicitacoes-tecnicas/nova'], { queryParams: { idObra: id } });
  }

  protected imprimir(): void {
    window.print();
  }

  private podeAcionar(tipo: AcaoInsightObra): boolean {
    if (tipo === 'anexo') return this.podeAnexar;
    return tipo === 'solicitar' || this.podeEscrever;
  }

  protected acionarInsight(tipo: AcaoInsightObra): void {
    switch (tipo) {
      case 'carteira':
        return this.abrirCarteira();
      case 'cadastro':
        return this.editar();
      case 'anexo':
        return this.abrirAnexo();
      case 'solicitar':
        return this.solicitarMateriais();
      case 'status':
        return this.abrirStatus();
    }
  }

  protected recalcularDeslocamento(): void {
    const id = this.obra()?.id;
    if (!id) return;
    this.recalculando.set(true);
    this.service.recalcularDeslocamento(id).subscribe({
      next: (c) => {
        this.recalculando.set(false);
        this.notificacoes.success(c.deslocamentoEstimado ? 'Deslocamento estimado (provedor de rotas indisponível).' : 'Deslocamento recalculado.');
        this.recarregar();
      },
      error: (e) => {
        this.recalculando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Status operacional
  // ═══════════════════════════════════════════════════════════════════════

  protected abrirStatus(): void {
    const o = this.obra();
    if (!o) return;
    this.novoStatusId.set(o.statusId);
    this.novaDataFim.set(parseDataBrParaDate(o.dataFimEfetiva));
    this.observacaoStatus.set('');
    this.statusAberto.set(true);
  }

  protected salvarStatus(): void {
    const o = this.obra();
    const idStatus = this.novoStatusId();
    if (!o || !idStatus) return;
    this.salvandoStatus.set(true);
    this.service
      .informarStatusOperacional(o.id, {
        idStatusObra: idStatus,
        observacao: this.observacaoStatus().trim() || null,
        dataFimEfetiva: (this.statusConcluido() && formatarDataParaApi(this.novaDataFim())) || null
      })
      .subscribe({
        next: () => {
          this.salvandoStatus.set(false);
          this.statusAberto.set(false);
          const nome = this.statusList().find((s) => s.id === idStatus)?.nome ?? o.statusNome;
          this.notificacoes.success(`Status registrado como "${nome}".`);
          this.recarregar();
        },
        error: (e) => {
          this.salvandoStatus.set(false);
          this.notificacoes.error(getErrorMessage(e));
        }
      });
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Carteira
  // ═══════════════════════════════════════════════════════════════════════

  protected abrirCarteira(): void {
    const o = this.obra();
    if (!o) return;
    const c = o.carteira;
    this.cIdContrato.set(c?.idContrato ?? null);
    this.cTensao.set(c?.classeTensao ?? null);
    this.cPrioridade.set(c?.prioridade ?? null);
    this.cPrazoRegulado.set(parseDataBrParaDate(c?.prazoRegulado));
    this.cLote.set(c?.lotePrevisto ?? '');
    this.cSituacao.set(c?.situacaoCarteira ?? '');
    this.cValorProjeto.set(o.valorProjeto ?? null);
    this.cMaterial.set(c?.valorMaterialPrevisto ?? null);
    this.cServico.set(c?.valorServicoPrevisto ?? null);
    this.carteiraOriginal = this.assinaturaCarteira();
    this.carteiraAberta.set(true);
  }

  private assinaturaCarteira(): string {
    return JSON.stringify([
      this.cIdContrato(), this.cTensao(), this.cPrioridade(), formatarDataParaApi(this.cPrazoRegulado()) ?? null,
      this.cLote(), this.cSituacao(), this.cValorProjeto(), this.cMaterial(), this.cServico()
    ]);
  }

  protected salvarCarteira(): void {
    const id = this.obra()?.id;
    if (!id) return;
    const request: CarteiraObraRequest = {
      idContrato: this.cIdContrato(),
      classeTensao: this.cTensao(),
      prioridade: this.cPrioridade(),
      prazoRegulado: formatarDataParaApi(this.cPrazoRegulado()) ?? null,
      lotePrevisto: this.cLote().trim() || null,
      situacaoCarteira: this.cSituacao().trim() || null,
      valorProjeto: this.cValorProjeto(),
      valorMaterialPrevisto: this.cMaterial(),
      valorServicoPrevisto: this.cServico()
    };
    this.salvandoCarteira.set(true);
    this.service.informarCarteira(id, request).subscribe({
      next: () => {
        this.salvandoCarteira.set(false);
        this.carteiraAberta.set(false);
        this.notificacoes.success('Carteira da obra atualizada.');
        this.recarregar();
      },
      error: (e) => {
        this.salvandoCarteira.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Subprojetos
  // ═══════════════════════════════════════════════════════════════════════

  protected abrirVinculo(): void {
    this.obraFilha.set(null);
    this.tipoVinculo.set(TipoVinculoSubprojeto.KIT);
    this.obrasParaVinculo.set([]);
    this.vinculoAberto.set(true);
    this.buscarObrasVinculo('');
  }

  protected buscarObrasVinculo(termo: string): void {
    this.buscaObras$.next(termo.trim());
  }

  protected vincular(): void {
    const paiId = this.obra()?.id;
    const filhaId = this.obraFilha();
    if (!paiId || !filhaId) return;
    const filha = this.obrasParaVinculo().find((o) => o.id === filhaId);
    this.vinculando.set(true);
    this.service.vincularSubprojeto(paiId, { idObraFilha: filhaId, tipoVinculo: this.tipoVinculo() }).subscribe({
      next: () => {
        this.vinculando.set(false);
        this.vinculoAberto.set(false);
        this.notificacoes.success(`Obra ${filha?.numero ?? ''} vinculada como subprojeto ${this.tipoVinculo()}.`);
        this.recarregar();
      },
      error: (e) => {
        this.vinculando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async desvincular(sub: SubprojetoResponse): Promise<void> {
    const paiId = this.obra()?.id;
    if (!paiId) return;
    const ok = await this.confirmacao.confirm({
      title: 'Desvincular subprojeto',
      message: `${sub.numero} deixa de ser subprojeto desta obra. A obra continua cadastrada.`,
      confirmLabel: 'Desvincular',
      variant: 'destructive'
    });
    if (!ok) return;
    this.service.desvincularSubprojeto(paiId, sub.id).subscribe({
      next: () => {
        this.notificacoes.success(`Subprojeto ${sub.numero} desvinculado.`);
        this.recarregar();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Anexos
  // ═══════════════════════════════════════════════════════════════════════

  protected abrirAnexo(): void {
    this.anexoArquivo.set(null);
    this.anexoTipo.set(this.tiposAnexo()[0]?.value ?? null);
    this.anexoObservacao.set('');
    this.anexoAberto.set(true);
  }

  protected salvarAnexo(): void {
    const id = this.obra()?.id;
    const arquivo = this.anexoArquivo();
    const tipo = this.anexoTipo();
    if (!id || !arquivo || !tipo) return;
    this.enviandoAnexo.set(true);
    this.service.anexarArquivo(id, tipo, arquivo, this.anexoObservacao()).subscribe({
      next: () => {
        this.enviandoAnexo.set(false);
        this.anexoAberto.set(false);
        this.notificacoes.success(`${arquivo.name} anexado.`);
        this.recarregar();
      },
      error: (e) => {
        this.enviandoAnexo.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected visualizarAnexo(anexo: AnexoObraResponse): void {
    const id = this.obra()?.id;
    if (!id) return;
    const nome = anexo.nomeArquivo.toLowerCase();
    const pdf = nome.endsWith('.pdf');
    const imagem = /\.(png|jpe?g|webp|svg|gif)$/.test(nome);
    this.previewNome.set(anexo.nomeArquivo);
    this.previewTipo.set(pdf ? 'pdf' : imagem ? 'image' : 'other');
    this.carregandoPreview.set(true);
    this.previewAberto.set(true);
    this.service.baixarAnexo(id, anexo.id, anexo.nomeArquivo).subscribe({
      next: (resposta) => {
        if (resposta.body) {
          let tipo = resposta.body.type;
          if (!tipo || tipo === 'application/octet-stream') {
            tipo = pdf ? 'application/pdf' : imagem ? (nome.endsWith('.png') ? 'image/png' : 'image/jpeg') : tipo;
          }
          const url = URL.createObjectURL(new Blob([resposta.body], { type: tipo }));
          this.previewBruto.set(url);
          this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(url));
        }
        this.carregandoPreview.set(false);
      },
      error: (e) => {
        this.carregandoPreview.set(false);
        this.previewAberto.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected fecharPreview(): void {
    this.previewAberto.set(false);
    const url = this.previewBruto();
    if (url) URL.revokeObjectURL(url);
    this.previewBruto.set(null);
    this.previewUrl.set(null);
  }

  protected abrirPreviewNovaAba(): void {
    const url = this.previewBruto();
    if (url) window.open(url, '_blank');
  }

  protected baixarAnexo(anexo: AnexoObraResponse): void {
    const id = this.obra()?.id;
    if (!id) return;
    this.service.baixarAnexo(id, anexo.id, anexo.nomeArquivo).subscribe({
      next: (resposta) => {
        if (resposta.body) {
          this.service.baixarArquivoBlob(resposta.body, this.service.extrairNomeArquivoDeResponse(resposta, anexo.nomeArquivo));
        }
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected async removerAnexo(anexo: AnexoObraResponse): Promise<void> {
    const id = this.obra()?.id;
    if (!id) return;
    const ok = await this.confirmacao.confirm({
      title: 'Remover anexo',
      message: `${anexo.nomeArquivo} será removido da obra.`,
      confirmLabel: 'Remover',
      variant: 'destructive'
    });
    if (!ok) return;
    this.service.removerAnexo(id, anexo.id).subscribe({
      next: () => {
        this.notificacoes.success('Anexo removido.');
        this.recarregar();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Mapa: a obra em destaque e as obras próximas
  // ═══════════════════════════════════════════════════════════════════════

  private renderizarMapa(): void {
    const el = this.mapEl()?.nativeElement;
    const o = this.obra();
    if (!el || !o || o.latitude == null || o.longitude == null || typeof L === 'undefined') return;
    this.destruirMapa();
    const estilo = getComputedStyle(document.documentElement);
    const marca = estilo.getPropertyValue('--ces-brand').trim() || 'currentColor';
    const neutro = estilo.getPropertyValue('--ces-foreground-muted').trim() || 'gray';
    const superficie = estilo.getPropertyValue('--ces-surface').trim() || 'white';
    try {
      this.leafletMap = L.map(el, { zoomControl: true, attributionControl: false, scrollWheelZoom: false });
      const tiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(this.leafletMap);
      const pontos: [number, number][] = [[o.latitude, o.longitude]];
      for (const p of this.dados(this.proximas()) ?? []) {
        pontos.push([p.latitude, p.longitude]);
        L.circleMarker([p.latitude, p.longitude], { radius: 6, fillColor: neutro, color: superficie, weight: 2, fillOpacity: 0.85 })
          .addTo(this.leafletMap)
          .bindTooltip(`${p.numero} · ${this.km(p.distanciaKm)}`);
      }
      L.circleMarker([o.latitude, o.longitude], { radius: 10, fillColor: marca, color: superficie, weight: 3, fillOpacity: 1 })
        .addTo(this.leafletMap)
        .bindTooltip(`Obra ${o.numero}`);
      if (pontos.length > 1) this.leafletMap.fitBounds(pontos, { padding: [24, 24], maxZoom: 15 });
      else this.leafletMap.setView([o.latitude, o.longitude], 15);
      // O contêiner dos tiles só existe depois que o mapa tem posição.
      if (this.tema.isDarkMode()) tiles.getContainer().style.filter = FILTRO_TILES_ESCURO;
      // Os controles do Leaflet ficam fora do encapsulamento do componente: recebem os tokens aqui.
      for (const botao of Array.from<HTMLElement>(this.leafletMap.zoomControl.getContainer().querySelectorAll('a'))) {
        botao.style.background = superficie;
        botao.style.color = estilo.getPropertyValue('--ces-foreground').trim();
        botao.style.borderColor = estilo.getPropertyValue('--ces-border').trim();
      }
      setTimeout(() => this.leafletMap?.invalidateSize(), 200);
    } catch {
      // Sem Leaflet carregado: o card mostra as coordenadas.
    }
  }

  private destruirMapa(): void {
    this.leafletMap?.remove();
    this.leafletMap = null;
  }
}
