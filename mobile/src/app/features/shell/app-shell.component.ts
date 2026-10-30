import { AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { NavigationEnd, NavigationStart, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { OutboxService } from '../../core/sync/outbox.service';
import { SessaoService } from '../../core/sessao/sessao.service';
import { Aba, BarraAbasComponent } from '../../ui';
import { AvisoSessaoComponent } from '../../ui/aviso-sessao.component';

/** Telas de topo: mostram a barra de abas. Lançamentos e detalhes ocupam a tela toda. */
const TELAS_DE_TOPO = /^\/(hoje|campo|material|solicitacoes|solicitacoes\/estoque|transferencias|perfil|inspecoes|desvios|almox\/solicitacoes|almox\/movimentacoes)(\?|$)/;

/**
 * Casca do app: conteúdo rolável + barra de abas inferior.
 *
 * A rolagem de cada rota é guardada no estado de tela da sessão (durante a
 * rolagem e ao sair) e devolvida ao voltar — inclusive depois de o sistema
 * matar o app — esperando a lista ter altura suficiente.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, BarraAbasComponent, AvisoSessaoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="casca">
      <main #rolagem class="conteudo" (scroll)="aoRolar()">
        <ces-aviso-sessao />
        <router-outlet />
      </main>
      @if (mostrarAbas()) {
        <ces-barra-abas [abas]="abas()" />
      }
    </div>
  `,
  styles: [`
    :host { display: block; height: 100%; }
    .casca { display: flex; flex-direction: column; height: 100dvh; background: var(--ces-fundo); }
    .conteudo { flex: 1; min-height: 0; overflow-y: auto; overflow-x: hidden; overscroll-behavior: contain;
      display: flex; flex-direction: column; }
    .conteudo > :not(router-outlet) { flex: 1 0 auto; }
  `],
})
export class AppShellComponent implements AfterViewInit {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly outbox = inject(OutboxService);
  private readonly sessao = inject(SessaoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly rolagem = viewChild.required<ElementRef<HTMLElement>>('rolagem');

  private readonly url = signal(this.router.url);
  private timerRolagem: ReturnType<typeof setTimeout> | null = null;

  /** Destinos por perfil. Regras de acesso: SST não opera logística; campo não acessa inspeções. */
  readonly abas = computed<Aba[]>(() => {
    const pendentes = this.outbox.pendingCount();
    if (this.auth.isAdministrativo() && this.auth.podeAcessarAlmoxarifado()) {
      // Com almoxarifado são 5 áreas: Inspeções e Desvios viram uma aba (Segurança) com abas no topo.
      return [
        { rota: '/hoje', rotulo: 'Hoje', icone: 'hoje', marcador: pendentes || undefined },
        { rota: '/almox', rotulo: 'Almoxarifado', icone: 'almox', ativaEm: ['/almox'] },
        { rota: '/inspecoes', rotulo: 'Segurança', icone: 'escudo', ativaEm: ['/desvios'] },
        { rota: '/perfil', rotulo: 'Conta', icone: 'conta' },
      ];
    }
    if (this.auth.isAdministrativo()) {
      return [
        { rota: '/hoje', rotulo: 'Hoje', icone: 'hoje', marcador: pendentes || undefined },
        { rota: '/inspecoes', rotulo: 'Inspeções', icone: 'inspecao' },
        { rota: '/desvios', rotulo: 'Desvios', icone: 'desvio' },
        { rota: '/perfil', rotulo: 'Conta', icone: 'conta' },
      ];
    }
    return [
      { rota: '/hoje', rotulo: 'Hoje', icone: 'hoje', marcador: pendentes || undefined },
      { rota: '/campo', rotulo: 'Serviços', icone: 'servicos' },
      { rota: '/material', rotulo: 'Material', icone: 'material', ativaEm: ['/solicitacoes', '/transferencias'] },
      { rota: '/perfil', rotulo: 'Conta', icone: 'conta' },
    ];
  });
  readonly rotas = computed(() => this.abas().map(a => a.rota));
  readonly mostrarAbas = computed(() => TELAS_DE_TOPO.test(this.url()));

  constructor() {
    // Ouve desde já: a casca nasce DURANTE a primeira navegação, e o NavigationEnd
    // dela chega antes do ngAfterViewInit. Sem isto a rolagem iria para a rota errada.
    const sub = this.router.events
      .pipe(filter(e => e instanceof NavigationStart || e instanceof NavigationEnd))
      .subscribe(e => {
        if (e instanceof NavigationStart) {
          // Um temporizador de rolagem pendente da tela que está saindo não pode
          // disparar depois e gravar por cima da posição da próxima tela.
          if (this.timerRolagem) clearTimeout(this.timerRolagem);
          this.timerRolagem = null;
          if (this.vistaPronta) this.guardarRolagem();
        } else {
          const url = (e as NavigationEnd).urlAfterRedirects;
          this.url.set(url);
          if (this.vistaPronta) this.restaurarRolagem(url);
        }
      });
    this.destroyRef.onDestroy(() => sub.unsubscribe());
  }

  private vistaPronta = false;
  /** Até quando os eventos de rolagem vêm da própria restauração (e não do dedo). */
  private restaurandoAte = 0;

  ngAfterViewInit(): void {
    this.vistaPronta = true;
    this.url.set(this.router.url);
    this.restaurarRolagem(this.router.url);
  }

  aoRolar(): void {
    if (performance.now() < this.restaurandoAte) return;
    // Grava durante a rolagem (com folga): o app pode morrer sem evento de saída.
    if (this.timerRolagem) clearTimeout(this.timerRolagem);
    this.timerRolagem = setTimeout(() => this.guardarRolagem(), 250);
  }

  /**
   * Guarda a posição como "qual item estava no topo + quanto dele já tinha passado"
   * (como as listas nativas), além do pixel bruto como reserva. Pixel sozinho escorrega
   * quando linhas fora da tela ainda não têm a altura real (content-visibility).
   */
  private guardarRolagem(): void {
    const el = this.rolagem().nativeElement;
    const topo = el.getBoundingClientRect().top;
    let ancora: string | null = null;
    let deslocamento = 0;
    for (const item of Array.from(el.querySelectorAll<HTMLElement>('[data-ancora]'))) {
      const r = item.getBoundingClientRect();
      if (r.bottom > topo + 1) {
        ancora = item.dataset['ancora'] ?? null;
        deslocamento = Math.round(topo - r.top);
        break;
      }
    }
    this.sessao.definirEstadoTela({ rolagem: Math.round(el.scrollTop), ancora, deslocamento }, this.url());
  }

  /** Espera o conteúdo assíncrono aparecer e volta para o mesmo item (até 5 s, ou até o usuário mexer). */
  private restaurarRolagem(url: string): void {
    const salvo = this.sessao.estadoTela<{ rolagem?: number; ancora?: string | null; deslocamento?: number }>(url);
    const alvo = Number(salvo.rolagem ?? 0);
    const el = this.rolagem().nativeElement;
    const terminou = () => { this.restaurandoAte = performance.now() + 150; };
    if (!alvo) {
      el.scrollTop = 0;
      terminou();
      return;
    }
    const inicio = performance.now();
    const LIMITE_MS = 5000;
    // Tela ainda carregando (esqueleto com aria-busy): continua esperando, até este teto.
    const LIMITE_CARREGANDO_MS = 15000;
    this.restaurandoAte = inicio + LIMITE_CARREGANDO_MS + 200;
    // O dedo manda: se o usuário tocar ou rolar, a restauração para.
    let desistiu = false;
    const desistir = () => { desistiu = true; terminou(); };
    el.addEventListener('pointerdown', desistir, { once: true, passive: true });
    el.addEventListener('wheel', desistir, { once: true, passive: true });
    el.addEventListener('touchmove', desistir, { once: true, passive: true });
    const limpar = () => {
      el.removeEventListener('pointerdown', desistir);
      el.removeEventListener('wheel', desistir);
      el.removeEventListener('touchmove', desistir);
    };
    const posicionarNaAncora = (): boolean => {
      if (!salvo.ancora) return false;
      const item = el.querySelector<HTMLElement>(`[data-ancora="${CSS.escape(salvo.ancora)}"]`);
      if (!item) return false;
      el.scrollTop += item.getBoundingClientRect().top - el.getBoundingClientRect().top + Number(salvo.deslocamento ?? 0);
      return true;
    };
    const tentar = () => {
      if (this.url() !== url || desistiu) { limpar(); return; }
      if (posicionarNaAncora()) {
        limpar();
        // Um quadro depois as linhas vizinhas ganham altura real: corrige o resíduo.
        requestAnimationFrame(() => { if (this.url() === url) posicionarNaAncora(); terminou(); });
        return;
      }
      const cabe = el.scrollHeight - el.clientHeight >= alvo;
      const decorrido = performance.now() - inicio;
      const carregando = !!el.querySelector('[aria-busy="true"]');
      const esgotou = carregando ? decorrido > LIMITE_CARREGANDO_MS : decorrido > LIMITE_MS;
      if ((!salvo.ancora && cabe && !carregando) || esgotou) {
        limpar();
        el.scrollTop = alvo;
        terminou();
        return;
      }
      requestAnimationFrame(tentar);
    };
    requestAnimationFrame(tentar);
  }
}
