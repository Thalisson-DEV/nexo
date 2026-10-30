import { Injectable, signal, computed, inject, effect } from '@angular/core';
import { AuthService } from './auth.service';

export type StatusConexao = 'online' | 'offline_sem_rede' | 'dados_moveis_bloqueados' | 'offline_manual';

@Injectable({
  providedIn: 'root'
})
export class ConnectivityService {
  private readonly auth = inject(AuthService, { optional: true });

  private readonly STORAGE_REDES_MOVEIS = 'ces_usar_redes_moveis';

  /** Conexão física de rede informada pelo navegador/sistema operacional */
  readonly hardwareOnline = signal(typeof navigator !== 'undefined' ? navigator.onLine : true);

  /** Tipo de conexão física ('cellular', 'wifi', 'ethernet', 'unknown', etc.) */
  readonly connectionType = signal<string>('unknown');

  /** Flag indicando se a conexão detectada é de rede móvel (celular) */
  readonly isCellular = signal<boolean>(false);

  /** Preferência do usuário sobre permitir ou não sincronização em redes móveis */
  readonly usarRedesMoveis = signal<boolean>(this.carregarPreferenciaRedesMoveis());

  /** Alternância manual para forçar o app a operar em modo offline */
  readonly forcarOffline = signal<boolean>(false);

  /** Signal principal de conectividade efetiva da aplicação */
  readonly isOnline = signal(true);

  /** Indica se o app está offline especificamente devido ao bloqueio de rede móvel */
  readonly isBloqueadoPorDadosMoveis = computed(() => {
    return this.hardwareOnline() && this.isCellular() && !this.usarRedesMoveis() && !this.forcarOffline();
  });

  /** Diagnóstico textual do estado atual de conexão */
  readonly statusConexao = computed<StatusConexao>(() => {
    if (this.forcarOffline()) return 'offline_manual';
    if (!this.hardwareOnline()) return 'offline_sem_rede';
    if (this.isCellular() && !this.usarRedesMoveis()) return 'dados_moveis_bloqueados';
    return 'online';
  });

  constructor() {
    this.detectarConexaoInicial();
    this.registrarListeners();
    this.atualizarStatus();

    // Reage a mudanças de usuário para aplicar o padrão correto (CAMPO vs ADMINISTRATIVO)
    if (this.auth) {
      effect(() => {
        const user = this.auth?.currentUser();
        const salvo = typeof localStorage !== 'undefined' ? localStorage.getItem(this.STORAGE_REDES_MOVEIS) : null;
        if (salvo === null && user) {
          const padrao = user.tipoUsuario !== 'CAMPO';
          this.usarRedesMoveis.set(padrao);
          if (typeof localStorage !== 'undefined') {
            localStorage.setItem(this.STORAGE_REDES_MOVEIS, String(padrao));
          }
          this.atualizarStatus();
        }
      });
    }
  }

  private carregarPreferenciaRedesMoveis(): boolean {
    if (typeof localStorage !== 'undefined') {
      const salvo = localStorage.getItem(this.STORAGE_REDES_MOVEIS);
      if (salvo !== null) {
        return salvo === 'true';
      }
    }
    const user = this.auth?.currentUser?.();
    if (user?.tipoUsuario === 'CAMPO') {
      return false;
    }
    return true;
  }

  private detectarConexaoInicial(): void {
    if (typeof navigator === 'undefined') return;

    const navConn = (navigator as any).connection || (navigator as any).mozConnection || (navigator as any).webkitConnection;
    if (navConn) {
      const type = (navConn.type || 'unknown').toLowerCase();
      this.connectionType.set(type);
      this.isCellular.set(type === 'cellular');
    }
  }

  private registrarListeners(): void {
    if (typeof window === 'undefined') return;

    window.addEventListener('online', () => {
      this.hardwareOnline.set(true);
      this.atualizarStatus();
    });

    window.addEventListener('offline', () => {
      this.hardwareOnline.set(false);
      this.atualizarStatus();
    });

    const navConn = (navigator as any)?.connection || (navigator as any)?.mozConnection || (navigator as any)?.webkitConnection;
    if (navConn && typeof navConn.addEventListener === 'function') {
      navConn.addEventListener('change', () => {
        const type = (navConn.type || 'unknown').toLowerCase();
        this.connectionType.set(type);
        this.isCellular.set(type === 'cellular');
        this.atualizarStatus();
      });
    }
  }

  setUsarRedesMoveis(habilitar: boolean): void {
    this.usarRedesMoveis.set(habilitar);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(this.STORAGE_REDES_MOVEIS, String(habilitar));
    }
    if (this.auth?.currentUser()) {
      this.auth.updateUserSession({ usarRedesMoveis: habilitar });
    }
    this.atualizarStatus();
  }

  setForcarOffline(forcar: boolean): void {
    this.forcarOffline.set(forcar);
    this.atualizarStatus();
  }

  setSimulatedCellular(cellular: boolean): void {
    this.isCellular.set(cellular);
    this.connectionType.set(cellular ? 'cellular' : 'wifi');
    this.atualizarStatus();
  }

  setSimulatedHardwareOnline(online: boolean): void {
    this.hardwareOnline.set(online);
    this.atualizarStatus();
  }

  atualizarStatus(): void {
    const online = !this.forcarOffline() && this.hardwareOnline() && !(this.isCellular() && !this.usarRedesMoveis());
    this.isOnline.set(online);
  }
}
