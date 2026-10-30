import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs/operators';
import { RascunhoService } from '../sessao/rascunho.service';
import { SessaoService } from '../sessao/sessao.service';
import { TelemetriaService } from '../sessao/telemetria.service';

/** Depois disso sem `change`, consideramos a câmera/seletor abandonados. */
const CAPTURA_EXPIRA_MS = 5 * 60 * 1000;

/**
 * Atualização do app sem derrubar ninguém no meio do trabalho.
 *
 * A versão nova baixa em segundo plano (ngsw). Quando fica pronta, NÃO interrompe a
 * tela: é aplicada na próxima vez que o app for para segundo plano, depois de gravar
 * sessão e rascunhos — ao voltar, a retomada põe o usuário no mesmo ponto, já na
 * versão nova. Exceção: com câmera ou seletor de arquivo aberto (o app também fica
 * oculto), a troca espera, senão a foto que está voltando se perderia.
 */
@Injectable({ providedIn: 'root' })
export class AppUpdateService {
  private readonly swUpdate = inject(SwUpdate);
  private readonly rascunhos = inject(RascunhoService);
  private readonly sessao = inject(SessaoService);
  private readonly telemetria = inject(TelemetriaService);

  /** Há versão nova esperando o momento seguro. A tela Conta mostra "Atualizar agora". */
  readonly atualizacaoPronta = signal(false);
  private capturaDesde = 0;
  private readonly ouvintes = new AbortController();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.ouvintes.abort());
  }

  init(): void {
    if (!this.swUpdate.isEnabled) return;
    const { signal: sinal } = this.ouvintes;

    this.swUpdate.versionUpdates
      .pipe(filter((evt): evt is VersionReadyEvent => evt.type === 'VERSION_READY'))
      .subscribe(evt => {
        this.atualizacaoPronta.set(true);
        void this.telemetria.registrar('sw.atualizacao', { etapa: 'pronta', versao: evt.latestVersion.hash.slice(0, 8) });
      });

    // Cache corrompido / versão apagada do servidor: só recarregar resolve. Grava antes.
    this.swUpdate.unrecoverable.subscribe(evt => {
      void this.telemetria.registrar('sw.atualizacao', { etapa: 'irrecuperavel', motivo: evt.reason.slice(0, 60) });
      void this.aplicar();
    });

    document.addEventListener('click', ev => {
      const alvo = ev.target as Element | null;
      // O toque pode cair no ícone dentro do <label> que abre a câmera.
      const rotulo = alvo?.closest('label');
      const controle = rotulo instanceof HTMLLabelElement ? rotulo.control : null;
      if (alvo?.closest('input[type=file]') || (controle instanceof HTMLInputElement && controle.type === 'file')) {
        this.capturaDesde = Date.now();
      }
    }, { capture: true, signal: sinal });
    document.addEventListener('change', ev => {
      if ((ev.target as HTMLInputElement | null)?.type === 'file') this.capturaDesde = 0;
    }, { capture: true, signal: sinal });

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden' && this.atualizacaoPronta() && !this.capturando()) {
        void this.aplicar();
      }
    }, { signal: sinal });
  }

  /** Câmera ou seletor de arquivo aberto agora. */
  capturando(): boolean {
    return this.capturaDesde > 0 && Date.now() - this.capturaDesde < CAPTURA_EXPIRA_MS;
  }

  /** Grava tudo, ativa a versão nova e recarrega na mesma rota. */
  async aplicar(): Promise<void> {
    await Promise.allSettled([this.rascunhos.gravarTodos(), this.sessao.gravarAgora()]);
    void this.telemetria.registrar('sw.atualizacao', { etapa: 'aplicada' });
    try {
      await this.swUpdate.activateUpdate();
    } finally {
      document.location.reload();
    }
  }

  /**
   * Verificação manual acionada pelo usuário (Conta → Procurar atualização).
   * Se houver versão nova, ela fica pronta e o chamador decide quando aplicar.
   */
  async checkForUpdateManual(): Promise<'updated' | 'current' | 'disabled' | 'error'> {
    if (!this.swUpdate.isEnabled) return 'disabled';
    try {
      const hasUpdate = await this.swUpdate.checkForUpdate();
      if (hasUpdate) {
        this.atualizacaoPronta.set(true);
        return 'updated';
      }
      return 'current';
    } catch {
      return 'error';
    }
  }

  /**
   * Reset agressivo para aparelhos com SW/cache travado numa versão antiga.
   * Apaga só os caches do service worker — nunca o IndexedDB (fila, rascunhos).
   */
  async hardReset(): Promise<void> {
    await Promise.allSettled([this.rascunhos.gravarTodos(), this.sessao.gravarAgora()]);
    try {
      if ('caches' in window) {
        const keys = await caches.keys();
        await Promise.all(keys.map((k) => caches.delete(k)));
      }
      if ('serviceWorker' in navigator) {
        const regs = await navigator.serviceWorker.getRegistrations();
        await Promise.all(regs.map((r) => r.update()));
      }
    } finally {
      document.location.reload();
    }
  }
}
