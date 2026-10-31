import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  signal,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesConfirmationService,
  CesDialogShellComponent,
  CesFileInputComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesNumberInputComponent,
  CesPdfCanvasComponent,
  CesPdfRegiao,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTabPanelDirective,
  CesTabsComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService,
  cesPdfDaTela,
  cesPdfParaTela,
  normalizarRotacao
} from '@ces/ui';
import { Observable, filter, switchMap, take, takeWhile, timer } from 'rxjs';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AnexoObraResponse, OrcamentoObraResponse } from '../../../core/models/obras.models';
import {
  AceitarProposta,
  AnalisePlanejamento,
  AnotacaoPlanejamento,
  ItemPlanejado,
  KitAplicado,
  PlanejamentoObra,
  PontoPlanejamento,
  TIPOS_PONTO,
  TipoExecucaoPonto,
  TipoPontoPlanejamento,
  VaoPlanejamento
} from '../../../core/models/obras-planejamento.models';
import { AuthService } from '../../../core/services/auth.service';
import { ObrasPlanejamentoService } from '../../../core/services/obras-planejamento.service';
import { ObrasService } from '../../../core/services/obras.service';
import { formatarMoeda } from '../obras-ui';
import { PlanejamentoArvoreComponent } from './planejamento-arvore.component';
import { AplicacaoKit, PlanejamentoKitsComponent } from './planejamento-kits.component';
import { PlanejamentoRevisaoComponent, PontoEmRevisao } from './planejamento-revisao.component';
import { EstadoLocal, ROTULO_ESTADO, estadoDoLocal, kitsDoLocal, proximoNumero, rotuloVao } from './planejamento.logic';

type Ferramenta = 'mao' | 'ponto' | 'vao' | 'selecionar' | 'area' | 'texto' | 'anotar';
type ItemAtivo = { tipo: 'ponto' | 'vao' | 'anotacao'; id: string };

const INTERVALO_ANALISE_MS = 3000;

/** Editor do planejamento: o PDF do projeto no centro, a árvore à direita e cada ação gravada por um comando pequeno. */
@Component({
  selector: 'app-obra-planejamento',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesDialogShellComponent,
    CesFileInputComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesNumberInputComponent,
    CesPdfCanvasComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTabPanelDirective,
    CesTabsComponent,
    CesTextInputComponent,
    CesTextareaComponent,
    PlanejamentoArvoreComponent,
    PlanejamentoKitsComponent,
    PlanejamentoRevisaoComponent
  ],
  templateUrl: './obra-planejamento.component.html',
  styleUrl: './obra-planejamento.component.css'
})
export class ObraPlanejamentoComponent {
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly api = inject(ObrasPlanejamentoService);
  private readonly obras = inject(ObrasService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  private readonly canvas = viewChild(CesPdfCanvasComponent);
  private readonly overlay = viewChild<ElementRef<HTMLElement>>('overlay');
  private readonly numeroNovo = viewChild('numeroNovo', { read: ElementRef });

  protected readonly idObra = this.rota.snapshot.paramMap.get('id') ?? '';
  protected readonly podeEditar = (inject(AuthService).currentUser()?.permissoes ?? []).includes('obras:planejamento:write');
  protected readonly tiposPonto: CesSelectOption<TipoPontoPlanejamento>[] = TIPOS_PONTO;
  protected readonly moeda = formatarMoeda;
  protected readonly rotuloEstado = ROTULO_ESTADO;
  protected readonly estadosLegenda: EstadoLocal[] = ['sem-kit', 'pendente', 'programado', 'andamento', 'concluido'];

  // ---- Dados ----
  protected readonly plano = signal<PlanejamentoObra | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly pdf = signal<ArrayBuffer | null>(null);
  protected readonly carregandoPdf = signal(false);
  protected readonly erroPdf = signal(false);
  private idAnexoCarregado: string | null = null;

  // ---- Folha ----
  protected readonly pagina = signal(1);
  protected readonly zoom = signal(1);
  protected readonly totalPaginas = signal(1);
  protected readonly ferramenta = signal<Ferramenta>('mao');
  protected readonly esmaecido = signal(0);
  protected readonly camadasOcultas = signal(false);
  protected readonly painelOculto = signal(false);
  protected readonly legendaAberta = signal(false);

  // ---- Seleção e item aberto ----
  protected readonly ativo = signal<ItemAtivo | null>(null);
  protected readonly locaisSelecionados = signal<ReadonlySet<string>>(new Set());
  protected readonly itensSelecionados = signal<ReadonlySet<string>>(new Set());
  protected readonly recolhidos = signal<ReadonlySet<string>>(new Set());

  // ---- Ações em andamento na folha ----
  protected readonly origemVao = signal<string | null>(null);
  protected readonly novoPonto = signal<{ x: number; y: number } | null>(null);
  protected readonly novoPontoNumero = signal('');
  protected readonly novoPontoTipo = signal<TipoPontoPlanejamento>('POSTE');
  protected readonly novaAnotacao = signal<{ x: number; y: number } | null>(null);
  protected readonly novaAnotacaoTexto = signal('');
  protected readonly movendo = signal<{ id: string; x: number; y: number } | null>(null);
  protected readonly modoMover = signal(false);
  protected readonly retangulo = signal<{ x0: number; y0: number; x1: number; y1: number } | null>(null);
  protected readonly salvando = signal(false);

  // ---- Rascunhos do item aberto ----
  protected readonly rascunhoNumero = signal('');
  protected readonly rascunhoBarramento = signal('');
  protected readonly rascunhoMetragem = signal<number | null>(null);
  protected readonly rascunhoAnotacao = signal('');

  // ---- Diálogos ----
  protected readonly loteAberto = signal(false);
  protected readonly textoAberto = signal(false);
  protected readonly textoExtraido = signal('');
  protected readonly detalheAberto = signal(false);
  protected readonly abaDetalhe = signal<string>('kits');
  protected readonly abasDetalhe = [
    { value: 'kits', label: 'Kits construtivos', icon: 'pi pi-briefcase' },
    { value: 'orcamento', label: 'Orçamento', icon: 'pi pi-file' }
  ];
  protected readonly orcamento = signal<OrcamentoObraResponse | null>(null);
  protected readonly avancoAberto = signal(false);

  // ---- Projeto mestre ----
  protected readonly projetoAberto = signal(false);
  protected readonly anexosPdf = signal<AnexoObraResponse[]>([]);
  protected readonly tiposAnexo = signal<CesSelectOption<string>[]>([]);
  protected readonly projetoTipo = signal<string | null>(null);
  protected readonly projetoArquivo = signal<File | null>(null);
  protected readonly enviandoProjeto = signal(false);

  // ---- Copiloto ----
  protected readonly leituraDisponivel = signal(false);
  protected readonly analisando = signal(false);
  protected readonly analise = signal<AnalisePlanejamento | null>(null);
  protected readonly ultimaLeitura = signal<AnalisePlanejamento | null>(null);
  protected readonly sugestoes = signal<PontoEmRevisao[]>([]);
  protected readonly ajustes = signal<ReadonlyMap<string, { x: number; y: number }>>(new Map());
  protected readonly destaque = signal<string | null>(null);
  protected readonly aceitando = signal(false);
  protected readonly arrastandoSugestao = signal<string | null>(null);

  // ---- Derivados ----
  protected readonly rotacao = computed(() => this.plano()?.rotacoes[String(this.pagina())] ?? 0);
  protected readonly pontosPorId = computed(() => new Map((this.plano()?.pontos ?? []).map((p) => [p.id, p])));
  protected readonly numerosExistentes = computed<ReadonlySet<string>>(() => new Set((this.plano()?.pontos ?? []).map((p) => p.numero)));
  protected readonly revisando = computed(() => this.analise()?.status === 'CONCLUIDA' && !!this.analise()?.proposta);

  protected readonly pontosDaPagina = computed(() => {
    const plano = this.plano();
    if (!plano) return [];
    const rotacao = this.rotacao();
    const movendo = this.movendo();
    return plano.pontos
      .filter((p) => p.pagina === this.pagina())
      .map((ponto) => {
        const base = movendo?.id === ponto.id ? movendo : ponto;
        const tela = cesPdfParaTela({ x: Number(base.x), y: Number(base.y) }, rotacao);
        const estado = estadoDoLocal(kitsDoLocal(plano, ponto.id));
        return { ponto, left: tela.x * 100, top: tela.y * 100, estado };
      });
  });

  protected readonly vaosDaPagina = computed(() => {
    const plano = this.plano();
    if (!plano) return [];
    const pontos = this.pontosPorId();
    const rotacao = this.rotacao();
    const pagina = this.pagina();
    const movendo = this.movendo();
    const lista: { vao: VaoPlanejamento; x1: number; y1: number; x2: number; y2: number; estado: string; rotulo: string; mx: number; my: number }[] = [];
    for (const vao of plano.vaos) {
      const a = pontos.get(vao.idPontoA);
      const b = pontos.get(vao.idPontoB);
      if (!a || !b || a.pagina !== pagina || b.pagina !== pagina) continue;
      const pa = cesPdfParaTela(movendo?.id === a.id ? movendo : { x: Number(a.x), y: Number(a.y) }, rotacao);
      const pb = cesPdfParaTela(movendo?.id === b.id ? movendo : { x: Number(b.x), y: Number(b.y) }, rotacao);
      lista.push({
        vao, x1: pa.x, y1: pa.y, x2: pb.x, y2: pb.y,
        mx: ((pa.x + pb.x) / 2) * 100, my: ((pa.y + pb.y) / 2) * 100,
        estado: estadoDoLocal(kitsDoLocal(plano, vao.id)),
        rotulo: rotuloVao(vao, pontos)
      });
    }
    return lista;
  });

  /** Vãos que saem desta página: aparecem como etiqueta ao lado do ponto e levam à outra folha. */
  protected readonly conexoesEntreFolhas = computed(() => {
    const plano = this.plano();
    if (!plano) return [];
    const pontos = this.pontosPorId();
    const rotacao = this.rotacao();
    const pagina = this.pagina();
    return plano.vaos.flatMap((vao) => {
      const a = pontos.get(vao.idPontoA);
      const b = pontos.get(vao.idPontoB);
      if (!a || !b || a.pagina === b.pagina) return [];
      const aqui = a.pagina === pagina ? a : b.pagina === pagina ? b : null;
      if (!aqui) return [];
      const outro = aqui === a ? b : a;
      const tela = cesPdfParaTela({ x: Number(aqui.x), y: Number(aqui.y) }, rotacao);
      return [{ vao, left: tela.x * 100, top: tela.y * 100, destino: outro }];
    });
  });

  protected readonly anotacoesDaPagina = computed(() => {
    const rotacao = this.rotacao();
    return (this.plano()?.anotacoes ?? [])
      .filter((a) => a.pagina === this.pagina())
      .map((anotacao) => {
        const tela = cesPdfParaTela({ x: Number(anotacao.x), y: Number(anotacao.y) }, rotacao);
        return { anotacao, left: tela.x * 100, top: tela.y * 100 };
      });
  });

  protected readonly sugestoesDaPagina = computed(() => {
    if (!this.revisando() || this.analise()?.pagina !== this.pagina()) return [];
    const rotacao = this.rotacao();
    const ajustes = this.ajustes();
    return this.sugestoes().map((sugestao) => {
      const posicao = ajustes.get(sugestao.referencia) ?? sugestao;
      const tela = cesPdfParaTela({ x: Number(posicao.x), y: Number(posicao.y) }, rotacao);
      return { sugestao, left: tela.x * 100, top: tela.y * 100 };
    });
  });

  protected readonly pontoAtivo = computed<PontoPlanejamento | null>(() => {
    const ativo = this.ativo();
    return ativo?.tipo === 'ponto' ? this.pontosPorId().get(ativo.id) ?? null : null;
  });

  protected readonly vaoAtivo = computed<VaoPlanejamento | null>(() => {
    const ativo = this.ativo();
    return ativo?.tipo === 'vao' ? this.plano()?.vaos.find((v) => v.id === ativo.id) ?? null : null;
  });

  protected readonly anotacaoAtiva = computed<AnotacaoPlanejamento | null>(() => {
    const ativo = this.ativo();
    return ativo?.tipo === 'anotacao' ? this.plano()?.anotacoes.find((a) => a.id === ativo.id) ?? null : null;
  });

  protected readonly kitsDoAtivo = computed<KitAplicado[]>(() => {
    const plano = this.plano();
    const id = this.pontoAtivo()?.id ?? this.vaoAtivo()?.id;
    return plano && id ? kitsDoLocal(plano, id) : [];
  });

  protected readonly tituloAtivo = computed(() => {
    const vao = this.vaoAtivo();
    if (vao) return `Vão ${rotuloVao(vao, this.pontosPorId())}`;
    const ponto = this.pontoAtivo();
    return ponto ? `P${ponto.numero}` : '';
  });

  protected readonly contagemSelecao = computed(() => {
    const plano = this.plano();
    const locais = this.locaisSelecionados();
    if (!plano) return { pontos: 0, vaos: 0, itens: 0 };
    const pontos = plano.pontos.filter((p) => locais.has(p.id)).length;
    const vaos = plano.vaos.filter((v) => locais.has(v.id)).length;
    const itens = new Set(this.itensSelecionados());
    for (const kit of plano.kits) {
      if ((kit.idPonto && locais.has(kit.idPonto)) || (kit.idVao && locais.has(kit.idVao))) {
        kit.itens.filter((i) => i.status === 'PENDENTE' && !kit.executadoAntes).forEach((i) => itens.add(i.id));
      }
    }
    return { pontos, vaos, itens: itens.size };
  });

  protected readonly temSelecao = computed(() => {
    const contagem = this.contagemSelecao();
    return contagem.pontos + contagem.vaos + contagem.itens > 0;
  });

  protected readonly barra = computed(() => {
    const ind = this.plano()?.indicadores;
    const total = ind?.valorTotal || 0;
    const parte = (valor: number) => (total ? (valor / total) * 100 : 0);
    return { executado: parte(ind?.valorExecutado ?? 0), programado: parte(ind?.valorProgramado ?? 0) };
  });

  protected readonly dica = computed(() => {
    if (this.modoMover()) return 'Arraste o ponto até a nova posição. Esc cancela.';
    switch (this.ferramenta()) {
      case 'ponto': return 'Clique na folha onde está o poste ou a estrutura. A ferramenta continua ativa para o próximo. Esc termina.';
      case 'vao': {
        const origem = this.origemVao();
        return origem
          ? `Origem: P${this.pontosPorId().get(origem)?.numero}. Clique no ponto de destino, nesta ou em outra página.`
          : 'Clique no ponto de origem do vão.';
      }
      case 'selecionar': return 'Clique nos pontos e vãos para incluir ou tirar da seleção.';
      case 'area': return 'Desenhe um retângulo sobre os pontos e vãos que quer selecionar.';
      case 'texto': return 'Desenhe um retângulo sobre o texto do projeto.';
      case 'anotar': return 'Clique na folha onde quer deixar a anotação.';
      default: return '';
    }
  });

  /** O cartão fica do lado oposto ao ponto em foco, para não cobrir o que está sendo editado. */
  protected readonly cartaoNaDireita = computed(() => {
    const rotacao = this.rotacao();
    const posicao = this.novoPonto() ?? this.novaAnotacao();
    if (posicao) return cesPdfParaTela(posicao, rotacao).x < 0.5;
    const ativo = this.pontoAtivo() ?? this.anotacaoAtiva();
    if (ativo) return cesPdfParaTela({ x: Number(ativo.x), y: Number(ativo.y) }, rotacao).x < 0.5;
    const vao = this.vaosDaPagina().find((t) => t.vao.id === this.vaoAtivo()?.id);
    return !!vao && vao.mx < 50;
  });

  protected readonly capturaAtiva = computed(() => ['ponto', 'area', 'texto', 'anotar'].includes(this.ferramenta()));

  constructor() {
    this.carregar();

    effect(() => {
      const idAnexo = this.plano()?.projetoMestre?.idAnexo ?? null;
      if (idAnexo !== this.idAnexoCarregado) {
        this.idAnexoCarregado = idAnexo;
        this.baixarPdf(idAnexo);
      }
    });

    // Quem levanta em sequência confirma o número sugerido com Enter, sem tirar a mão do teclado.
    effect(() => {
      const campo = this.numeroNovo()?.nativeElement.querySelector('input') as HTMLInputElement | undefined;
      if (this.novoPonto() && campo) {
        queueMicrotask(() => {
          campo.focus();
          campo.select();
        });
      }
    });

    // Rascunhos acompanham o item aberto e a versão gravada.
    effect(() => {
      const ponto = this.pontoAtivo();
      this.rascunhoNumero.set(ponto?.numero ?? '');
      this.rascunhoBarramento.set(ponto?.barramento ?? '');
    });
    effect(() => this.rascunhoMetragem.set(this.vaoAtivo()?.metragem ?? null));
    effect(() => this.rascunhoAnotacao.set(this.anotacaoAtiva()?.texto ?? ''));

    effect(() => {
      const pagina = this.pagina();
      if (this.podeEditar && this.plano()?.projetoMestre) this.consultarLeitura(pagina);
    });
  }

  // ---- Carga ----

  protected carregar(): void {
    this.erro.set(null);
    this.api.buscar(this.idObra).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (plano) => this.plano.set(plano),
      error: (e) => this.erro.set(getErrorMessage(e))
    });
  }

  private baixarPdf(idAnexo: string | null): void {
    this.pdf.set(null);
    this.erroPdf.set(false);
    if (!idAnexo) return;
    this.carregandoPdf.set(true);
    this.obras.baixarAnexo(this.idObra, idAnexo).pipe(take(1)).subscribe({
      next: async (resposta) => {
        this.carregandoPdf.set(false);
        if (resposta.body) this.pdf.set(await resposta.body.arrayBuffer());
      },
      error: () => {
        this.carregandoPdf.set(false);
        this.erroPdf.set(true);
      }
    });
  }

  protected aoFalharPdf(): void {
    this.erroPdf.set(true);
  }

  /** Executa um comando: mantém a tela em dia e, se outra pessoa alterou o mesmo registro, recarrega e avisa. */
  private executar(comando: Observable<PlanejamentoObra>, depois?: (plano: PlanejamentoObra) => void): void {
    this.salvando.set(true);
    comando.pipe(take(1)).subscribe({
      next: (plano) => {
        this.salvando.set(false);
        this.plano.set(plano);
        depois?.(plano);
      },
      error: (e) => this.tratarErro(e)
    });
  }

  private tratarErro(e: { status?: number }): void {
    this.salvando.set(false);
    if (e?.status === 409) {
      this.notificacoes.warning('Outra pessoa alterou este registro. Atualizei a tela com o valor atual: confira e tente de novo.');
      this.carregar();
    } else {
      this.notificacoes.error(getErrorMessage(e));
    }
  }

  // ---- Barra de ferramentas ----

  protected escolherFerramenta(ferramenta: Ferramenta): void {
    this.cancelarAcoes();
    this.ferramenta.set(this.ferramenta() === ferramenta ? 'mao' : ferramenta);
    if (this.ferramenta() === 'ponto') this.ativo.set(null);
  }

  private cancelarAcoes(): void {
    this.origemVao.set(null);
    this.novoPonto.set(null);
    this.novaAnotacao.set(null);
    this.retangulo.set(null);
    this.modoMover.set(false);
    this.movendo.set(null);
  }

  protected zoomMais(): void {
    this.canvas()?.zoomIn();
  }

  protected zoomMenos(): void {
    this.canvas()?.zoomOut();
  }

  protected ajustarLargura(): void {
    this.canvas()?.fitWidth();
  }

  protected girar(graus: number): void {
    const nova = normalizarRotacao(this.rotacao() + graus);
    this.executar(this.api.girarPagina(this.idObra, this.pagina(), nova));
  }

  protected irParaPagina(pagina: number): void {
    if (pagina < 1 || pagina > this.totalPaginas()) return;
    this.novoPonto.set(null);
    this.novaAnotacao.set(null);
    this.pagina.set(pagina);
  }

  protected voltar(): void {
    void this.router.navigate(['/obras', this.idObra]);
  }

  @HostListener('document:keydown', ['$event'])
  protected aoTeclar(evento: KeyboardEvent): void {
    const alvo = evento.target as HTMLElement;
    const digitando = alvo?.matches?.('input, textarea, select, [contenteditable="true"]');
    if (evento.key === 'Escape') {
      if (this.loteAberto() || this.textoAberto() || this.detalheAberto() || this.projetoAberto()) return;
      if (this.novoPonto() || this.novaAnotacao() || this.modoMover() || this.origemVao()) {
        this.cancelarAcoes();
      } else if (this.ferramenta() !== 'mao') {
        this.ferramenta.set('mao');
      } else if (this.temSelecao()) {
        this.limparSelecao();
      } else {
        this.ativo.set(null);
      }
      return;
    }
    if (digitando || evento.ctrlKey || evento.metaKey || evento.altKey) return;
    const atalhos: Record<string, Ferramenta> = { m: 'mao', p: 'ponto', v: 'vao', s: 'selecionar', a: 'area', t: 'texto', n: 'anotar' };
    const ferramenta = atalhos[evento.key.toLowerCase()];
    if (ferramenta && (this.podeEditar || ['mao', 'selecionar', 'area', 'texto'].includes(ferramenta))) {
      this.escolherFerramenta(ferramenta);
    } else if (evento.key === '+') {
      this.zoomMais();
    } else if (evento.key === '-') {
      this.zoomMenos();
    }
  }

  // ---- Interação com a folha ----

  private posicaoNaFolha(evento: PointerEvent | MouseEvent): { x: number; y: number } {
    const caixa = this.overlay()!.nativeElement.getBoundingClientRect();
    const naTela = {
      x: Math.min(1, Math.max(0, (evento.clientX - caixa.left) / caixa.width)),
      y: Math.min(1, Math.max(0, (evento.clientY - caixa.top) / caixa.height))
    };
    return cesPdfDaTela(naTela, this.rotacao());
  }

  private posicaoNaTela(evento: PointerEvent): { x: number; y: number } {
    const caixa = this.overlay()!.nativeElement.getBoundingClientRect();
    return {
      x: Math.min(1, Math.max(0, (evento.clientX - caixa.left) / caixa.width)),
      y: Math.min(1, Math.max(0, (evento.clientY - caixa.top) / caixa.height))
    };
  }

  protected aoApertarCaptura(evento: PointerEvent): void {
    if (evento.button !== 0) return;
    const ferramenta = this.ferramenta();
    if (ferramenta === 'ponto') {
      const posicao = this.posicaoNaFolha(evento);
      this.novoPontoNumero.set(proximoNumero(this.plano()?.pontos ?? []));
      this.novoPonto.set(posicao);
      return;
    }
    if (ferramenta === 'anotar') {
      this.novaAnotacaoTexto.set('');
      this.novaAnotacao.set(this.posicaoNaFolha(evento));
      return;
    }
    (evento.target as HTMLElement).setPointerCapture?.(evento.pointerId);
    const tela = this.posicaoNaTela(evento);
    this.retangulo.set({ x0: tela.x, y0: tela.y, x1: tela.x, y1: tela.y });
  }

  protected aoMoverCaptura(evento: PointerEvent): void {
    const atual = this.retangulo();
    if (!atual) return;
    const tela = this.posicaoNaTela(evento);
    this.retangulo.set({ ...atual, x1: tela.x, y1: tela.y });
  }

  protected aoSoltarCaptura(): void {
    const atual = this.retangulo();
    this.retangulo.set(null);
    if (!atual || (Math.abs(atual.x1 - atual.x0) < 0.002 && Math.abs(atual.y1 - atual.y0) < 0.002)) return;
    const a = cesPdfDaTela({ x: atual.x0, y: atual.y0 }, this.rotacao());
    const b = cesPdfDaTela({ x: atual.x1, y: atual.y1 }, this.rotacao());
    const regiao: CesPdfRegiao = {
      x0: Math.min(a.x, b.x), y0: Math.min(a.y, b.y), x1: Math.max(a.x, b.x), y1: Math.max(a.y, b.y)
    };
    if (this.ferramenta() === 'area') this.selecionarRegiao(regiao);
    else if (this.ferramenta() === 'texto') void this.extrairTexto(regiao);
  }

  private selecionarRegiao(regiao: CesPdfRegiao): void {
    const dentro = (x: number, y: number) => x >= regiao.x0 && x <= regiao.x1 && y >= regiao.y0 && y <= regiao.y1;
    const locais = new Set(this.locaisSelecionados());
    const pontos = this.pontosPorId();
    for (const { ponto } of this.pontosDaPagina()) {
      if (dentro(Number(ponto.x), Number(ponto.y))) locais.add(ponto.id);
    }
    for (const { vao } of this.vaosDaPagina()) {
      const a = pontos.get(vao.idPontoA)!;
      const b = pontos.get(vao.idPontoB)!;
      if (dentro((Number(a.x) + Number(b.x)) / 2, (Number(a.y) + Number(b.y)) / 2)) locais.add(vao.id);
    }
    this.locaisSelecionados.set(locais);
  }

  private async extrairTexto(regiao: CesPdfRegiao): Promise<void> {
    const texto = (await this.canvas()?.extractText(regiao)) ?? '';
    this.textoExtraido.set(texto);
    this.textoAberto.set(true);
  }

  protected preencherBarramento(): void {
    const ponto = this.pontoAtivo();
    const texto = this.textoExtraido().replace(/\s+/g, ' ').trim();
    if (!ponto || !texto) return;
    this.textoAberto.set(false);
    this.executar(this.api.editarPonto(this.idObra, ponto.id, this.edicao(ponto, { barramento: texto.slice(0, 60) })));
  }

  protected copiarTexto(): void {
    void navigator.clipboard?.writeText(this.textoExtraido());
    this.notificacoes.success('Texto copiado.');
  }

  protected rotuloRetangulo(): { left: number; top: number; width: number; height: number } | null {
    const r = this.retangulo();
    if (!r) return null;
    return {
      left: Math.min(r.x0, r.x1) * 100,
      top: Math.min(r.y0, r.y1) * 100,
      width: Math.abs(r.x1 - r.x0) * 100,
      height: Math.abs(r.y1 - r.y0) * 100
    };
  }

  // ---- Pontos ----

  protected aoClicarPonto(ponto: PontoPlanejamento): void {
    if (this.modoMover()) return;
    switch (this.ferramenta()) {
      case 'vao':
        this.escolherPontaVao(ponto);
        break;
      case 'selecionar':
        this.alternarLocal(ponto.id);
        break;
      default:
        this.abrir({ tipo: 'ponto', id: ponto.id });
    }
  }

  protected abrir(item: ItemAtivo): void {
    this.cancelarAcoes();
    this.ativo.set(item);
  }

  /** Na árvore: abre o item e leva a folha até ele, trocando de página quando preciso. */
  protected abrirDaArvore(id: string): void {
    const ponto = this.pontosPorId().get(id);
    if (ponto) {
      this.irParaPagina(ponto.pagina);
      this.abrir({ tipo: 'ponto', id });
      const tela = cesPdfParaTela({ x: Number(ponto.x), y: Number(ponto.y) }, this.plano()?.rotacoes[String(ponto.pagina)] ?? 0);
      this.canvas()?.centralizar(tela.x, tela.y);
      return;
    }
    const vao = this.plano()?.vaos.find((v) => v.id === id);
    if (vao) {
      const origem = this.pontosPorId().get(vao.idPontoA);
      if (origem) this.irParaPagina(origem.pagina);
      this.abrir({ tipo: 'vao', id });
    }
  }

  protected salvarNovoPonto(): void {
    const posicao = this.novoPonto();
    const numero = this.novoPontoNumero().trim();
    if (!posicao || !numero) return;
    this.executar(
      this.api.adicionarPonto(this.idObra, { numero, pagina: this.pagina(), x: posicao.x, y: posicao.y, tipo: this.novoPontoTipo() }),
      () => this.novoPonto.set(null)
    );
  }

  private edicao(ponto: PontoPlanejamento, parcial: Partial<{ numero: string; barramento: string | null; tipo: TipoPontoPlanejamento; tipoExecucao: TipoExecucaoPonto; temPoda: boolean; temAterramento: boolean }>) {
    return {
      numero: ponto.numero,
      barramento: ponto.barramento,
      tipo: ponto.tipo,
      tipoExecucao: ponto.tipoExecucao,
      temPoda: ponto.temPoda,
      temAterramento: ponto.temAterramento,
      versao: ponto.versao,
      ...parcial
    };
  }

  protected alterarPonto(parcial: Partial<{ tipo: TipoPontoPlanejamento; tipoExecucao: TipoExecucaoPonto; temPoda: boolean; temAterramento: boolean }>): void {
    const ponto = this.pontoAtivo();
    if (ponto) this.executar(this.api.editarPonto(this.idObra, ponto.id, this.edicao(ponto, parcial)));
  }

  /** Número e barramento só gravam ao sair do campo, e só se mudaram. */
  protected salvarCamposPonto(): void {
    const ponto = this.pontoAtivo();
    if (!ponto || !this.podeEditar) return;
    const numero = this.rascunhoNumero().trim();
    const barramento = this.rascunhoBarramento().trim();
    if (!numero) {
      this.rascunhoNumero.set(ponto.numero);
      return;
    }
    if (numero.toUpperCase() === ponto.numero && barramento === (ponto.barramento ?? '')) return;
    this.executar(this.api.editarPonto(this.idObra, ponto.id, this.edicao(ponto, { numero, barramento: barramento || null })));
  }

  protected iniciarMover(): void {
    this.modoMover.set(true);
    this.ferramenta.set('mao');
  }

  protected aoApertarMarcador(evento: PointerEvent, ponto: PontoPlanejamento): void {
    if (evento.button !== 0) return;
    evento.stopPropagation();
    if (this.modoMover() && this.ativo()?.id === ponto.id) {
      (evento.target as HTMLElement).setPointerCapture?.(evento.pointerId);
      this.movendo.set({ id: ponto.id, ...this.posicaoNaFolha(evento) });
    }
  }

  protected aoMoverMarcador(evento: PointerEvent): void {
    const movendo = this.movendo();
    if (movendo) this.movendo.set({ id: movendo.id, ...this.posicaoNaFolha(evento) });
  }

  protected aoSoltarMarcador(): void {
    const movendo = this.movendo();
    if (!movendo) return;
    const ponto = this.pontosPorId().get(movendo.id);
    this.modoMover.set(false);
    if (!ponto) {
      this.movendo.set(null);
      return;
    }
    this.api.moverPonto(this.idObra, ponto.id, { pagina: ponto.pagina, x: movendo.x, y: movendo.y, versao: ponto.versao }).pipe(take(1)).subscribe({
      next: (atualizado) => {
        this.movendo.set(null);
        this.plano.update((plano) => plano && { ...plano, pontos: plano.pontos.map((p) => (p.id === atualizado.id ? atualizado : p)) });
      },
      error: (e) => {
        this.movendo.set(null);
        this.tratarErro(e);
      }
    });
  }

  protected async excluirPonto(ponto: PontoPlanejamento): Promise<void> {
    const vaos = this.plano()?.vaos.filter((v) => v.idPontoA === ponto.id || v.idPontoB === ponto.id).length ?? 0;
    const ok = await this.confirmacao.confirm({
      title: `Excluir P${ponto.numero}?`,
      message: vaos
        ? `O ponto, seus kits e ${vaos} ${vaos === 1 ? 'vão ligado' : 'vãos ligados'} a ele serão excluídos.`
        : 'O ponto e seus kits serão excluídos.',
      variant: 'destructive'
    });
    if (ok) this.executar(this.api.excluirPonto(this.idObra, ponto.id), () => this.ativo.set(null));
  }

  // ---- Vãos ----

  protected iniciarConexao(ponto: PontoPlanejamento): void {
    this.cancelarAcoes();
    this.ferramenta.set('vao');
    this.origemVao.set(ponto.id);
  }

  private escolherPontaVao(ponto: PontoPlanejamento): void {
    const origem = this.origemVao();
    if (!origem) {
      this.origemVao.set(ponto.id);
      return;
    }
    if (origem === ponto.id) {
      this.origemVao.set(null);
      return;
    }
    this.executar(this.api.adicionarVao(this.idObra, origem, ponto.id), (plano) => {
      this.origemVao.set(ponto.id);
      const criado = plano.vaos.find((v) => (v.idPontoA === origem && v.idPontoB === ponto.id));
      if (criado) this.ativo.set({ tipo: 'vao', id: criado.id });
    });
  }

  protected aoClicarVao(vao: VaoPlanejamento): void {
    if (this.ferramenta() === 'selecionar') this.alternarLocal(vao.id);
    else if (this.ferramenta() !== 'vao') this.abrir({ tipo: 'vao', id: vao.id });
  }

  protected salvarMetragem(): void {
    const vao = this.vaoAtivo();
    const metragem = this.rascunhoMetragem();
    if (!vao || !this.podeEditar || (metragem ?? null) === (vao.metragem ?? null)) return;
    this.executar(this.api.editarVao(this.idObra, vao.id, metragem || null, vao.versao));
  }

  protected async excluirVao(vao: VaoPlanejamento): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: `Excluir o vão ${rotuloVao(vao, this.pontosPorId())}?`,
      message: 'O vão e seus kits serão excluídos.',
      variant: 'destructive'
    });
    if (ok) this.executar(this.api.excluirVao(this.idObra, vao.id), () => this.ativo.set(null));
  }

  // ---- Kits ----

  protected aplicarKit(aplicacao: AplicacaoKit): void {
    const ponto = this.pontoAtivo();
    const vao = this.vaoAtivo();
    if (!ponto && !vao) return;
    this.executarKit({
      idKit: aplicacao.idKit,
      operacao: aplicacao.operacao,
      idsPontos: ponto ? [ponto.id] : [],
      idsVaos: vao ? [vao.id] : []
    });
  }

  protected aplicarEmLote(aplicacao: AplicacaoKit): void {
    const plano = this.plano();
    if (!plano) return;
    const locais = this.locaisSelecionados();
    this.executarKit({
      idKit: aplicacao.idKit,
      operacao: aplicacao.operacao,
      idsPontos: plano.pontos.filter((p) => locais.has(p.id)).map((p) => p.id),
      idsVaos: plano.vaos.filter((v) => locais.has(v.id)).map((v) => v.id)
    }, () => this.loteAberto.set(false));
  }

  private executarKit(comando: Parameters<ObrasPlanejamentoService['aplicarKit']>[1], depois?: () => void): void {
    this.salvando.set(true);
    this.api.aplicarKit(this.idObra, comando).pipe(take(1)).subscribe({
      next: ({ planejamento, jaTinham }) => {
        this.salvando.set(false);
        this.plano.set(planejamento);
        if (jaTinham.length) {
          this.notificacoes.info(`Já tinham este kit e ficaram como estavam: ${jaTinham.join(', ')}.`);
        }
        depois?.();
      },
      error: (e) => this.tratarErro(e)
    });
  }

  protected async removerKit(kit: KitAplicado): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: `Remover o kit ${kit.codigo}?`,
      message: 'Os itens previstos deste kit saem do planejamento.',
      variant: 'destructive'
    });
    if (ok) this.executar(this.api.removerKit(this.idObra, kit.id));
  }

  protected alterarQuantidadeKit(evento: { kit: KitAplicado; multiplicador: number }): void {
    this.executar(this.api.alterarMultiplicador(this.idObra, evento.kit.id, evento.multiplicador, evento.kit.versao));
  }

  protected marcarExecutadoAntes(evento: { kit: KitAplicado; executado: boolean }): void {
    this.executar(this.api.marcarExecutadoAntes(this.idObra, evento.kit.id, evento.executado, evento.kit.versao));
  }

  protected alterarItem(evento: { kit: KitAplicado; item: ItemPlanejado; quantidade: number }): void {
    this.executar(this.api.alterarItem(this.idObra, evento.kit.id, evento.item.id, evento.quantidade, evento.item.versao));
  }

  protected removerItem(evento: { kit: KitAplicado; item: ItemPlanejado }): void {
    this.executar(this.api.removerItem(this.idObra, evento.kit.id, evento.item.id));
  }

  protected abrirDetalhe(): void {
    this.abaDetalhe.set('kits');
    this.detalheAberto.set(true);
    if (!this.orcamento()) {
      this.obras.buscarOrcamento(this.idObra).pipe(take(1)).subscribe({
        next: (orcamento) => this.orcamento.set(orcamento),
        error: () => this.orcamento.set(null)
      });
    }
  }

  // ---- Seleção ----

  protected alternarLocal(id: string): void {
    const locais = new Set(this.locaisSelecionados());
    if (!locais.delete(id)) locais.add(id);
    this.locaisSelecionados.set(locais);
  }

  protected alternarItem(id: string): void {
    const itens = new Set(this.itensSelecionados());
    if (!itens.delete(id)) itens.add(id);
    this.itensSelecionados.set(itens);
  }

  protected alternarRecolhido(chave: string): void {
    const recolhidos = new Set(this.recolhidos());
    if (!recolhidos.delete(chave)) recolhidos.add(chave);
    this.recolhidos.set(recolhidos);
  }

  protected limparSelecao(): void {
    this.locaisSelecionados.set(new Set());
    this.itensSelecionados.set(new Set());
  }

  // ---- Anotações ----

  protected salvarNovaAnotacao(): void {
    const posicao = this.novaAnotacao();
    const texto = this.novaAnotacaoTexto().trim();
    if (!posicao || !texto) return;
    this.executar(this.api.adicionarAnotacao(this.idObra, { pagina: this.pagina(), x: posicao.x, y: posicao.y, texto }), () => {
      this.novaAnotacao.set(null);
      this.ferramenta.set('mao');
    });
  }

  protected salvarAnotacao(): void {
    const anotacao = this.anotacaoAtiva();
    const texto = this.rascunhoAnotacao().trim();
    if (anotacao && texto && texto !== anotacao.texto) {
      this.executar(this.api.editarAnotacao(this.idObra, anotacao.id, texto, anotacao.versao));
    }
  }

  protected excluirAnotacao(anotacao: AnotacaoPlanejamento): void {
    this.executar(this.api.excluirAnotacao(this.idObra, anotacao.id), () => this.ativo.set(null));
  }

  // ---- Projeto mestre ----

  protected abrirProjeto(): void {
    this.projetoArquivo.set(null);
    this.projetoAberto.set(true);
    this.obras.listarAnexos(this.idObra).pipe(take(1)).subscribe({
      next: (anexos) => this.anexosPdf.set(anexos.filter((a) => a.contentType === 'application/pdf' || a.nomeArquivo.toLowerCase().endsWith('.pdf'))),
      error: () => this.anexosPdf.set([])
    });
    this.obras.listarTiposAnexosAtivos().pipe(take(1)).subscribe({
      next: (tipos) => this.tiposAnexo.set(tipos.map((t) => ({ value: t.id, label: t.nome }))),
      error: () => this.tiposAnexo.set([])
    });
  }

  protected usarAnexo(anexo: AnexoObraResponse): void {
    this.executar(this.api.definirProjetoMestre(this.idObra, anexo.id), () => this.projetoAberto.set(false));
  }

  protected enviarProjeto(): void {
    const arquivo = this.projetoArquivo();
    const tipo = this.projetoTipo();
    if (!arquivo || !tipo) return;
    this.enviandoProjeto.set(true);
    this.api.enviarProjetoMestre(this.idObra, tipo, arquivo).pipe(take(1)).subscribe({
      next: (plano) => {
        this.enviandoProjeto.set(false);
        this.plano.set(plano);
        this.projetoAberto.set(false);
      },
      error: (e) => {
        this.enviandoProjeto.set(false);
        this.tratarErro(e);
      }
    });
  }

  // ---- Copiloto ----

  private consultarLeitura(pagina: number): void {
    this.api.disponibilidadeAnalise(this.idObra, pagina).pipe(take(1)).subscribe({
      next: (resposta) => {
        this.leituraDisponivel.set(resposta.disponivel);
        this.ultimaLeitura.set(resposta.ultima?.status === 'CONCLUIDA' ? resposta.ultima : null);
      },
      error: () => this.leituraDisponivel.set(false)
    });
  }

  protected lerComCopiloto(): void {
    this.cancelarAcoes();
    this.ferramenta.set('mao');
    this.analise.set(null);
    this.analisando.set(true);
    this.api.iniciarAnalise(this.idObra, this.pagina()).pipe(
      take(1),
      switchMap((inicio) => timer(INTERVALO_ANALISE_MS, INTERVALO_ANALISE_MS).pipe(
        switchMap(() => this.api.buscarAnalise(this.idObra, inicio.id)),
        takeWhile((analise) => analise.status === 'PROCESSANDO', true),
        filter((analise) => analise.status !== 'PROCESSANDO')
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (analise) => {
        this.analisando.set(false);
        if (analise.status === 'FALHOU') {
          this.notificacoes.error(analise.mensagem ?? 'Não foi possível ler esta página do projeto.');
          return;
        }
        this.abrirRevisao(analise);
      },
      error: (e) => {
        this.analisando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected abrirRevisao(analise: AnalisePlanejamento): void {
    this.ajustes.set(new Map());
    this.destaque.set(null);
    this.painelOculto.set(false);
    this.irParaPagina(analise.pagina);
    this.analise.set(analise);
  }

  protected descartarRevisao(): void {
    this.analise.set(null);
    this.sugestoes.set([]);
    this.ajustes.set(new Map());
  }

  protected aceitarProposta(aceite: AceitarProposta): void {
    const analise = this.analise();
    if (!analise) return;
    this.aceitando.set(true);
    this.api.aceitarAnalise(this.idObra, analise.id, aceite).pipe(take(1)).subscribe({
      next: (plano) => {
        this.aceitando.set(false);
        this.plano.set(plano);
        this.descartarRevisao();
        this.ultimaLeitura.set(null);
        this.notificacoes.success(`Planejamento atualizado: ${aceite.pontos.length} pontos e ${aceite.vaos.length} vãos da leitura.`);
      },
      error: (e) => {
        this.aceitando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected aoApertarSugestao(evento: PointerEvent, referencia: string): void {
    if (evento.button !== 0) return;
    evento.stopPropagation();
    (evento.target as HTMLElement).setPointerCapture?.(evento.pointerId);
    this.arrastandoSugestao.set(referencia);
    this.destaque.set(referencia);
  }

  protected aoMoverSugestao(evento: PointerEvent): void {
    const referencia = this.arrastandoSugestao();
    if (!referencia) return;
    const mapa = new Map(this.ajustes());
    mapa.set(referencia, this.posicaoNaFolha(evento));
    this.ajustes.set(mapa);
  }

  protected aoSoltarSugestao(): void {
    this.arrastandoSugestao.set(null);
  }
}
