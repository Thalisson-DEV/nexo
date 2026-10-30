import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CesButtonComponent, CesIconButtonComponent, CesStatusComponent } from '@ces/ui';
import { CesAsyncStateComponent, CesCalloutComponent } from '@ces/ui/feedback';
import { ConversaResumo, FocoPainel } from '../../../core/models/assistente.models';
import { AssistenteService } from '../../../core/services/assistente.service';
import { CopilotoStore } from '../copiloto.store';
import { CopilotoArtefatoComponent } from '../componentes/copiloto-artefato.component';
import { CopilotoCompositorComponent } from '../componentes/copiloto-compositor.component';
import { CopilotoMensagemComponent } from '../componentes/copiloto-mensagem.component';
import {
  CopilotoDashboardContext,
  paraContextoAssistente,
  resumoRecorte,
  sugestoesDisponiveis,
} from '../contexto/copiloto-contexto';
import { CopilotoPainelService } from './copiloto-painel.service';

/**
 * Copiloto contextual: o **mesmo** agente do workspace em uma superfície compacta ao lado do dashboard.
 *
 * Reaproveita integralmente o núcleo do workspace — `CopilotoStore` (conversas, streaming NDJSON com
 * retomada, parada, anexos, artefatos), `AssistenteService`, e os componentes de mensagem (Markdown,
 * linha do tempo das ferramentas, arquivos, atalhos), compositor e artefato. A diferença é só a superfície
 * e o contexto: cada pergunta leva o recorte **atual** do dashboard (período, filtros, KPIs, gráficos),
 * que o backend guarda como snapshot da mensagem.
 *
 * O store é uma instância própria do painel (provider do componente): conversas de dashboards diferentes
 * não se misturam e não interferem na conversa aberta no workspace. A conversa é real e aparece no
 * histórico geral — "Abrir no Copiloto" continua exatamente a mesma conversa em `/copiloto/:id`.
 */
@Component({
  selector: 'app-copiloto-painel',
  standalone: true,
  imports: [
    CesButtonComponent,
    CesIconButtonComponent,
    CesStatusComponent,
    CesAsyncStateComponent,
    CesCalloutComponent,
    CopilotoArtefatoComponent,
    CopilotoCompositorComponent,
    CopilotoMensagemComponent,
  ],
  providers: [CopilotoStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './copiloto-painel.component.html',
  styleUrl: './copiloto-painel.component.css',
  host: { class: 'copiloto-painel' },
})
export class CopilotoPainelComponent {
  protected readonly store = inject(CopilotoStore);
  protected readonly painel = inject(CopilotoPainelService);
  private readonly api = inject(AssistenteService);
  private readonly router = inject(Router);

  /** Contexto estruturado do dashboard em tela (um `computed()` do dashboard). */
  readonly contexto = input.required<CopilotoDashboardContext | null>();

  private readonly rolagem = viewChild<ElementRef<HTMLElement>>('rolagem');
  private readonly compositor = viewChild(CopilotoCompositorComponent);

  protected readonly rascunho = signal('');
  protected readonly foco = signal<FocoPainel | null>(null);
  protected readonly grudado = signal(true);
  protected readonly arrastando = signal(false);
  protected readonly anterior = signal<ConversaResumo | null>(null);

  protected readonly recorteAtual = computed(() => resumoRecorte(this.contexto()));
  protected readonly filtrosAtivos = computed(() => (this.contexto()?.filtros ?? []).filter((filtro) => filtro.valor));
  protected readonly sugestoes = computed(() => sugestoesDisponiveis(this.contexto(), this.store.capacidades()));
  protected readonly titulo = computed(() => {
    const titulo = this.store.titulo();
    const nome = this.contexto()?.nome;
    return titulo && titulo !== 'Nova conversa' ? titulo : nome ? `Análise — ${nome}` : 'Nova análise';
  });

  /**
   * Recorte da última pergunta enviada. Se o usuário mudou filtros depois dela, o painel avisa que a próxima
   * pergunta usará o recorte atual — o contexto antigo nunca fica ativo em silêncio.
   */
  protected readonly recorteMudou = computed(() => {
    const ultimaPergunta = [...this.store.mensagens()].reverse().find((mensagem) => mensagem.papel === 'USUARIO');
    const anterior = ultimaPergunta?.contexto?.painel;
    if (!anterior || !this.contexto()) return false;
    return resumoRecorte({ ...anterior, foco: undefined }) !== resumoRecorte({ ...this.contexto()!, foco: undefined });
  });

  private painelAtual: string | null = null;

  constructor() {
    void this.store.carregarCapacidades();
    this.store.definirFonteContexto(() => {
      const contexto = this.contexto();
      return contexto ? paraContextoAssistente(contexto, this.foco()) : null;
    });
    inject(DestroyRef).onDestroy(() => this.store.encerrar());

    // Cada dashboard tem a sua conversa (isolamento entre painéis); ao voltar, a conversa é reaberta.
    effect(() => {
      const id = this.contexto()?.id ?? null;
      if (!id || id === this.painelAtual) return;
      this.painelAtual = id;
      untracked(() => void this.restaurar(id));
    });

    effect(() => {
      const conversa = this.store.conversaId();
      const painel = this.painelAtual;
      if (painel && conversa) untracked(() => this.painel.lembrarConversa(painel, conversa));
    });

    // Pedido "Perguntar ao Copiloto" vindo de um gráfico/KPI do dashboard.
    effect(() => {
      const pedido = this.painel.pedido();
      if (!pedido) return;
      untracked(() => {
        this.foco.set(pedido.foco);
        if (pedido.pergunta) this.rascunho.set(pedido.pergunta);
        this.painel.consumirPedido(pedido.sequencia);
        setTimeout(() => this.compositor()?.focar());
      });
    });

    effect(() => {
      this.store.mensagens();
      untracked(() => {
        if (this.grudado()) requestAnimationFrame(() => this.irParaOFim(false));
      });
    });
  }

  // ---------------------------------------------------------------------------------- conversa

  protected async enviar(texto: string): Promise<void> {
    this.rascunho.set('');
    this.grudado.set(true);
    const enviada = this.store.enviar(texto);
    // O foco vale para a pergunta que o pediu (o snapshot guarda o foco); as seguintes voltam ao painel todo.
    this.foco.set(null);
    setTimeout(() => this.compositor()?.focar());
    if (!(await enviada)) this.rascunho.set(texto);
  }

  protected usarSugestao(pergunta: string): void {
    void this.enviar(pergunta);
  }

  protected nova(): void {
    this.store.novaConversa();
    if (this.painelAtual) this.painel.lembrarConversa(this.painelAtual, null);
    this.rascunho.set('');
    this.foco.set(null);
    void this.buscarAnterior();
    setTimeout(() => this.compositor()?.focar());
  }

  protected continuarAnterior(): void {
    const conversa = this.anterior();
    if (conversa) void this.store.abrirConversa(conversa.id);
  }

  /**
   * Continua a mesma conversa no workspace completo (mensagens, artefatos, arquivos e ferramentas). Antes
   * da primeira pergunta ainda não há conversa: abre o workspace com o contexto de origem do dashboard
   * (mesmo formato dos atalhos "Analisar com Copiloto") e o rascunho já digitado.
   */
  protected abrirNoWorkspace(): void {
    const id = this.store.conversaId();
    if (id) {
      void this.router.navigate(['/copiloto', id]);
      return;
    }
    const contexto = this.contexto();
    const queryParams: Record<string, string> = {};
    if (contexto) {
      queryParams['origem'] = contexto.nome;
      queryParams['tipo'] = 'DASHBOARD';
      queryParams['entidade'] = contexto.id;
      queryParams['rotulo'] = contexto.nome;
      if (contexto.rota) queryParams['rota'] = contexto.rota;
      if (contexto.periodo?.rotulo) queryParams['f_Período'] = contexto.periodo.rotulo;
      for (const filtro of contexto.filtros) {
        if (filtro.valor) queryParams[`f_${filtro.rotulo}`] = filtro.valor;
      }
    }
    const rascunho = this.rascunho().trim();
    if (rascunho) queryParams['pergunta'] = rascunho;
    void this.router.navigate(['/copiloto'], { queryParams });
  }

  protected abrirArtefato(codigo: string): void {
    this.store.abrirArtefato(codigo);
  }

  protected removerFoco(): void {
    this.foco.set(null);
  }

  // ---------------------------------------------------------------------------------- rolagem e arquivos

  protected aoRolar(): void {
    const elemento = this.rolagem()?.nativeElement;
    if (!elemento) return;
    this.grudado.set(elemento.scrollHeight - elemento.scrollTop - elemento.clientHeight < 80);
  }

  protected irParaOFim(suave = true): void {
    const elemento = this.rolagem()?.nativeElement;
    if (!elemento) return;
    const reduzir = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    elemento.scrollTo?.({ top: elemento.scrollHeight, behavior: suave && !reduzir ? 'smooth' : 'auto' });
    this.grudado.set(true);
  }

  protected aoArrastar(evento: DragEvent): void {
    if (!evento.dataTransfer?.types?.includes('Files')) return;
    evento.preventDefault();
    this.arrastando.set(true);
  }

  protected aoSairArrasto(evento: DragEvent): void {
    const atual = evento.currentTarget as HTMLElement | null;
    const destino = evento.relatedTarget as Node | null;
    if (!atual || !destino || !atual.contains(destino)) this.arrastando.set(false);
  }

  protected aoSoltar(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastando.set(false);
    const arquivos = [...(evento.dataTransfer?.files ?? [])];
    if (arquivos.length && this.store.disponivel()) this.store.anexar(arquivos);
  }

  // ---------------------------------------------------------------------------------- interno

  private async restaurar(painelId: string): Promise<void> {
    const salva = this.painel.conversaDoPainel(painelId);
    if (salva) {
      await this.store.abrirConversa(salva);
      if (!this.store.erroConversa()) return;
      this.painel.lembrarConversa(painelId, null);
    }
    this.store.novaConversa();
    await this.buscarAnterior();
  }

  /** Última conversa deste painel (de outra sessão), oferecida como "Continuar análise anterior". */
  private async buscarAnterior(): Promise<void> {
    const painelId = this.painelAtual;
    if (!painelId) return;
    try {
      const pagina = await firstValueFrom(this.api.listarConversas(0, 1, undefined, painelId));
      const ultima = pagina.content[0] ?? null;
      this.anterior.set(ultima && ultima.id !== this.store.conversaId() ? ultima : null);
    } catch {
      this.anterior.set(null);
    }
  }
}
