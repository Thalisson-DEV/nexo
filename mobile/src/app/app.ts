import { Component, DestroyRef, effect, inject, untracked } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastComponent } from './shared/components/toast/toast.component';
import { AppUpdateService } from './core/sync/app-update.service';
import { SessaoService } from './core/sessao/sessao.service';
import { PlataformaService } from './core/plataforma/plataforma.service';
import { TelemetriaService } from './core/sessao/telemetria.service';
import { RascunhoStore } from './core/sessao/rascunho.store';
import { TelemetriaEnvioService } from './core/sessao/telemetria-envio.service';
import { ConnectivityService } from './core/sync/connectivity.service';
import { ConviteInstalacaoComponent } from './features/instalar/convite-instalacao.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastComponent, ConviteInstalacaoComponent],
  template: `
    <router-outlet />
    <app-toast />
    <app-convite-instalacao />
  `,
  styles: [`
    :host { display: block; height: 100%; }
  `]
})
export class App {
  constructor() {
    // Sem splash: a primeira tela útil aparece assim que o app carrega.
    inject(SessaoService).iniciar(inject(DestroyRef));
    inject(PlataformaService).iniciar();
    inject(AppUpdateService).init();

    const inicio = performance.now();
    void inject(TelemetriaService).registrar('app.abertura', {
      ms: Math.round(inicio),
      instalado: inject(PlataformaService).instalado(),
      online: typeof navigator === 'undefined' || navigator.onLine,
    });
    // Limpeza documentada (DECISOES P2): só anexos órfãos de gravação interrompida.
    void inject(RascunhoStore).limparOrfaos().catch(() => 0);

    // Telemetria sobe em lote quando há rede liberada; nunca disputa a tela.
    const rede = inject(ConnectivityService);
    const envio = inject(TelemetriaEnvioService);
    effect(() => {
      if (rede.isOnline()) untracked(() => setTimeout(() => void envio.enviarPendentes(), 5_000));
    });
  }
}
