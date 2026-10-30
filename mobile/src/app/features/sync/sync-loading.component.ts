import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { rotaInternaSegura } from '../../core/sessao/snapshot';
import { ROTA_INICIO } from '../../core/sessao/retomada.guard';
import { SyncService } from '../../core/sync/sync.service';
import { ToastService } from '../../core/ui/toast.service';
import { IconeComponent } from '../../ui';

/**
 * Primeira carga depois do login: baixa o que o app precisa para funcionar sem internet.
 * Mostra a etapa atual e segue assim que termina — sem animação de espera.
 */
@Component({
  selector: 'app-sync-loading',
  standalone: true,
  imports: [IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="tela" [attr.aria-busy]="!error()">
      <img src="logo_vertical.svg" alt="Nexo" width="88" height="88">
      @if (!error()) {
        <h1>Preparando o aparelho</h1>
        <p class="sub">Baixando o que você precisa para trabalhar mesmo sem internet.</p>
        <div class="barra" role="progressbar" aria-label="Carregando dados"><span></span></div>
        <p class="etapa" aria-live="polite">{{ etapa() }}</p>
      } @else {
        <div class="erro" role="alert">
          <span class="ces-selo ces-selo--erro"><ces-icone nome="alerta" /></span>
          <h1>Não deu para carregar tudo</h1>
          <p class="sub">{{ error() }}</p>
          <div class="acoes">
            <button type="button" class="ces-btn ces-btn--principal ces-btn--bloco" (click)="startSync()">Tentar de novo</button>
            <button type="button" class="ces-btn ces-btn--neutro ces-btn--bloco" (click)="continueOffline()">Seguir com o que já tem</button>
          </div>
        </div>
      }
    </main>
  `,
  styles: [`
    :host { display: block; min-height: 100dvh; background: var(--ces-fundo); }
    .tela { min-height: 100dvh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--ces-e3);
      padding: calc(var(--ces-e6, 32px) + env(safe-area-inset-top, 0px)) var(--ces-e5) calc(var(--ces-e6, 32px) + var(--ces-safe-base)); text-align: center; }
    img { margin-bottom: var(--ces-e3); }
    h1 { margin: 0; font: 700 1.375rem/1.25 var(--ces-fonte); text-wrap: balance; }
    .sub { margin: 0; max-width: 32ch; font: var(--ces-t-corpo); color: var(--ces-texto-2); }
    .barra { width: min(280px, 80vw); height: 6px; margin-top: var(--ces-e4); border-radius: var(--ces-r-pilula); background: var(--ces-superficie-2); overflow: hidden; }
    .barra span { display: block; width: 40%; height: 100%; border-radius: inherit; background: var(--ces-destaque); animation: correr 1.2s ease-in-out infinite; }
    @keyframes correr { from { transform: translateX(-100%); } to { transform: translateX(250%); } }
    @media (prefers-reduced-motion: reduce) { .barra span { animation: none; width: 100%; opacity: .5; } }
    .etapa { margin: 0; min-height: 1.4em; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .erro { display: grid; justify-items: center; gap: var(--ces-e3); width: min(360px, 100%); }
    .acoes { display: grid; gap: var(--ces-e2); width: 100%; margin-top: var(--ces-e3); }
  `],
})
export class SyncLoadingComponent implements OnInit {
  /** Etapa em palavras simples (sem reticências técnicas). */
  readonly etapa = computed(() => (this.syncService.currentStep().label || 'Conectando…').replace(/\.\.\.$/, '…'));
  readonly syncService = inject(SyncService);
  private readonly router = inject(Router);
  private readonly toastService = inject(ToastService);
  /** Volta ao ponto exato depois da carga (sessão expirada no meio do trabalho). */
  private readonly destino = rotaInternaSegura(inject(ActivatedRoute).snapshot.queryParamMap.get('volta')) ?? ROTA_INICIO;

  readonly error = signal<string | null>(null);
  readonly isSyncComplete = signal(false);

  ngOnInit(): void {
    this.startSync();
  }

  async startSync(): Promise<void> {
    this.error.set(null);
    this.isSyncComplete.set(false);

    try {
      const resultado = await this.syncService.initialSync();

      // Etapas negadas por permissão (403) são esperadas para perfis restritos:
      // o app segue utilizável com o que foi baixado, mas o usuário é avisado
      // do que ficou de fora em vez de encarar uma tela de falha total.
      if (resultado.falhas.length > 0) {
        const areas = resultado.falhas.map(f => f.etapa).join(', ');
        this.toastService.warning(`Alguns dados não foram carregados: ${areas}.`, 7000);
      }

      this.isSyncComplete.set(true);
      void this.router.navigateByUrl(this.destino, { replaceUrl: true });

    } catch (err: unknown) {
      this.error.set(this.mensagemDeFalha(err));
      console.error(err);
    }
  }

  /**
   * Só cai aqui quando o sync falha por inteiro (offline, sessão inválida ou
   * usuário sem equipe vinculada). Mostrar o motivo real evita mandar o
   * encarregado checar a rede quando o problema é de cadastro.
   */
  private mensagemDeFalha(err: unknown): string {
    const e = err as { status?: number, detail?: string, message?: string } | null;

    if (e?.status === 0 || e?.status === 408 || e?.status === 504) {
      return 'Não foi possível conectar ao servidor. Verifique sua conexão de rede para baixar os dados.';
    }

    if (e?.status === 403) {
      return 'Seu perfil de acesso não permite baixar os dados operacionais. Fale com o administrador do sistema.';
    }

    const detalhe = e?.detail || e?.message;
    return detalhe || 'Não foi possível conectar ao servidor. Verifique sua conexão de rede para baixar os dados.';
  }

  continueOffline(): void {
    this.router.navigateByUrl(this.destino, { replaceUrl: true });
  }
}
