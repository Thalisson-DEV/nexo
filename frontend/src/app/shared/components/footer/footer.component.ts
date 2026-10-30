import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ConnectivityService } from '../../../core/services/connectivity.service';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule],
  template: `
    <footer class="fixed bottom-0 left-0 w-full bg-[var(--ces-surface)]/90 backdrop-blur-md border-t border-[var(--ces-border)] py-1 px-3 flex justify-between items-center text-[10px] text-[var(--ces-foreground-subtle)] z-50">
      <div class="flex gap-5 items-center">
        <span class="font-medium text-[var(--ces-foreground-muted)]">Nexo | Gestão de Ativos e Eficiência Operacional</span>
        <span class="font-medium text-[var(--ces-foreground-subtle)]"> | </span>
        <span class="font-medium text-[var(--ces-foreground-muted)] tracking-tight">v1.0</span>
      </div>

      <div class="flex items-center gap-3">
        <div class="flex items-center gap-2 px-3 py-1 cursor-default select-none" [title]="rotuloTooltip()">
          <div [class]="'w-2 h-2 rounded-full animate-pulse ' + statusDotClass()"></div>
          <span [class]="'font-bold ' + statusTextClass()">
            {{ rotuloStatus() }}
          </span>
        </div>
      </div>
    </footer>
  `,
  styles: [`
    :host { display: block; }
  `]
})
export class FooterComponent {
  protected readonly connectivity = inject(ConnectivityService);

  protected readonly rotuloStatus = computed(() => {
    if (this.connectivity.forcarOffline()) return 'Offline (Manual)';
    if (this.connectivity.isBloqueadoPorDadosMoveis()) return 'Offline (Dados Móveis)';
    if (!this.connectivity.hardwareOnline()) return 'Offline (Sem Rede)';
    return this.connectivity.isOnline() ? 'Online' : 'Offline';
  });

  protected readonly rotuloTooltip = computed(() => {
    if (this.connectivity.forcarOffline()) return 'Modo offline manual ativado pelo usuário.';
    if (this.connectivity.isBloqueadoPorDadosMoveis()) return 'Sincronização em redes móveis desativada nas preferências de perfil.';
    if (!this.connectivity.hardwareOnline()) return 'Dispositivo sem conexão de rede.';
    return this.connectivity.isOnline() ? 'Conectado e pronto para sincronizar.' : 'Modo offline ativo.';
  });

  protected readonly statusDotClass = computed(() => {
    if (this.connectivity.isOnline()) {
      return 'bg-green-500 shadow-[0_0_5px_rgba(34,197,94,0.5)]';
    }
    if (this.connectivity.isBloqueadoPorDadosMoveis()) {
      return 'bg-amber-500 shadow-[0_0_5px_rgba(245,158,11,0.5)]';
    }
    return 'bg-red-500 shadow-[0_0_5px_rgba(239,68,68,0.5)]';
  });

  protected readonly statusTextClass = computed(() => {
    if (this.connectivity.isOnline()) {
      return 'text-green-600 dark:text-green-400';
    }
    if (this.connectivity.isBloqueadoPorDadosMoveis()) {
      return 'text-amber-600 dark:text-amber-400';
    }
    return 'text-red-600 dark:text-red-400';
  });
}
