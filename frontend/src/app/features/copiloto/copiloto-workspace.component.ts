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
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { combineLatest } from 'rxjs';
import { CesButtonComponent, CesIconButtonComponent } from '@ces/ui';
import { CesAsyncStateComponent, CesCalloutComponent } from '@ces/ui/feedback';
import { AssistenteContexto } from '../../core/models/assistente.models';
import { CopilotoStore } from './copiloto.store';
import { CopilotoArtefatoComponent } from './componentes/copiloto-artefato.component';
import { CopilotoBoasVindasComponent } from './componentes/copiloto-boas-vindas.component';
import { CopilotoCompositorComponent } from './componentes/copiloto-compositor.component';
import { CopilotoConversasComponent } from './componentes/copiloto-conversas.component';
import { CopilotoMensagemComponent } from './componentes/copiloto-mensagem.component';
import { AuthService } from '../../core/services/auth.service';

/** Lê o contexto de origem enviado pelos atalhos "Analisar com Copiloto". */
export function contextoDaUrl(query: ParamMap): AssistenteContexto | null {
  const contexto: AssistenteContexto = {
    pagina: query.get('origem') ?? undefined,
    rota: query.get('rota') ?? undefined,
    entidadeTipo: query.get('tipo') ?? undefined,
    entidadeId: query.get('entidade') ?? undefined,
    rotulo: query.get('rotulo') ?? undefined,
  };
  const filtros: Record<string, string> = {};
  for (const chave of query.keys) {
    if (chave.startsWith('f_')) filtros[chave.slice(2)] = query.get(chave) ?? '';
  }
  if (Object.keys(filtros).length) contexto.filtros = filtros;
  return Object.values(contexto).some((valor) => valor !== undefined) ? contexto : null;
}

/**
 * Workspace do Copiloto: histórico de conversas, conversa com linha do tempo das ferramentas e painel
 * de artefatos. Tela própria do Nexo (sem widget flutuante), responsiva: no celular, histórico e artefatos
 * ocupam a área da conversa quando abertos.
 */
@Component({
  selector: 'app-copiloto-workspace',
  standalone: true,
  imports: [
    CesButtonComponent,
    CesIconButtonComponent,
    CesAsyncStateComponent,
    CesCalloutComponent,
    CopilotoArtefatoComponent,
    CopilotoBoasVindasComponent,
    CopilotoCompositorComponent,
    CopilotoConversasComponent,
    CopilotoMensagemComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './copiloto-workspace.component.html',
  styleUrl: './copiloto-workspace.component.css',
})
export class CopilotoWorkspaceComponent {
  protected readonly store = inject(CopilotoStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly titulo = inject(Title);
  private readonly auth = inject(AuthService);
  protected readonly podeConectarIa = computed(() => !!this.auth.currentUser()?.permissoes?.includes('assistente:conexao:use'));

  private readonly rolagem = viewChild<ElementRef<HTMLElement>>('rolagem');
  private readonly compositor = viewChild(CopilotoCompositorComponent);

  protected readonly rascunho = signal('');
  protected readonly desktop = signal(typeof window === 'undefined' || window.matchMedia?.('(min-width: 1024px)').matches !== false);
  protected readonly listaAberta = signal(this.desktop());
  protected readonly grudado = signal(true);
  protected readonly arrastando = signal(false);
  protected readonly painelAberto = computed(() => this.store.artefatoSelecionado() !== null);
  protected readonly listaVisivel = computed(() => this.listaAberta() && !this.painelAberto());

  constructor() {
    void this.store.iniciar();

    combineLatest([this.route.paramMap, this.route.queryParamMap])
      .pipe(takeUntilDestroyed())
      .subscribe(([params, query]) => this.aplicarRota(params.get('id'), query));

    // Primeira mensagem de uma conversa nova: a URL passa a apontar para a conversa criada.
    effect(() => {
      const id = this.store.conversaId();
      untracked(() => {
        if (id && this.route.snapshot.paramMap.get('id') !== id) {
          void this.router.navigate(['/copiloto', id], { replaceUrl: true });
        }
      });
    });

    effect(() => this.titulo.setTitle(`${this.store.titulo()} · Copiloto`));

    effect(() => {
      this.store.mensagens();
      untracked(() => {
        if (this.grudado()) requestAnimationFrame(() => this.irParaOFim(false));
      });
    });

    const midia = typeof window !== 'undefined' ? window.matchMedia?.('(min-width: 1024px)') : undefined;
    if (midia) {
      const aoMudar = (evento: MediaQueryListEvent) => {
        this.desktop.set(evento.matches);
        this.listaAberta.set(evento.matches);
      };
      midia.addEventListener('change', aoMudar);
      inject(DestroyRef).onDestroy(() => midia.removeEventListener('change', aoMudar));
    }
  }

  // ---------------------------------------------------------------------------------- navegação

  protected conectarIa(): void {
    void this.router.navigate(['/copiloto/conectar-ia']);
  }

  protected nova(): void {
    this.store.novaConversa();
    this.rascunho.set('');
    if (!this.desktop()) this.listaAberta.set(false);
    void this.router.navigate(['/copiloto']);
    setTimeout(() => this.compositor()?.focar());
  }

  protected abrirConversa(id: string): void {
    if (!this.desktop()) this.listaAberta.set(false);
    void this.router.navigate(['/copiloto', id]);
  }

  protected async excluir(id: string): Promise<void> {
    const eraAtual = this.store.conversaId() === id;
    const excluida = await this.store.excluir(id);
    if (excluida && eraAtual) void this.router.navigate(['/copiloto']);
  }

  protected alternarLista(): void {
    if (this.painelAberto() && this.desktop()) {
      this.store.abrirArtefato(null);
      this.listaAberta.set(true);
      return;
    }
    this.listaAberta.update((aberta) => !aberta);
  }

  // ---------------------------------------------------------------------------------- mensagens

  protected async enviar(texto: string): Promise<void> {
    this.rascunho.set('');
    this.grudado.set(true);
    const enviada = this.store.enviar(texto);
    setTimeout(() => this.compositor()?.focar());
    if (!(await enviada)) this.rascunho.set(texto);
  }

  protected usarSugestao(pergunta: string): void {
    this.rascunho.set(pergunta);
    setTimeout(() => this.compositor()?.focar());
  }

  protected abrirArtefato(codigo: string): void {
    this.store.abrirArtefato(codigo);
  }

  protected abrirUltimoArtefato(): void {
    const artefatos = this.store.artefatos();
    if (artefatos.length) this.store.abrirArtefato(artefatos[artefatos.length - 1].codigo);
  }

  // ---------------------------------------------------------------------------------- rolagem e arquivos

  protected aoRolar(): void {
    const elemento = this.rolagem()?.nativeElement;
    if (!elemento) return;
    const distancia = elemento.scrollHeight - elemento.scrollTop - elemento.clientHeight;
    this.grudado.set(distancia < 80);
  }

  protected irParaOFim(suave = true): void {
    const elemento = this.rolagem()?.nativeElement;
    if (!elemento) return;
    const reduzir = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    elemento.scrollTo({ top: elemento.scrollHeight, behavior: suave && !reduzir ? 'smooth' : 'auto' });
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

  private aplicarRota(id: string | null, query: ParamMap): void {
    if (id) {
      void this.store.abrirConversa(id);
      return;
    }
    const contexto = contextoDaUrl(query);
    if (this.store.conversaId() !== null || contexto) this.store.novaConversa(contexto);
    const pergunta = query.get('pergunta');
    if (pergunta) this.rascunho.set(pergunta);
  }
}
