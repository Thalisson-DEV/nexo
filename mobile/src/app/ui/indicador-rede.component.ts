import { ChangeDetectionStrategy, Component, computed, inject, output } from '@angular/core';
import { ConnectivityService } from '../core/sync/connectivity.service';
import { IconeComponent } from './icone.component';

/** Pílula discreta de conexão. Sem rede não é erro: o app segue salvando no aparelho. */
@Component({
  selector: 'ces-indicador-rede',
  standalone: true,
  imports: [IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="pilula" [class.off]="!online()" (click)="tocar.emit()"
      [attr.aria-label]="'Conexão: ' + texto()">
      @if (online()) { <span class="ponto" aria-hidden="true"></span> } @else { <ces-icone nome="sem_rede" [tamanho]="16" /> }
      {{ texto() }}
    </button>
  `,
  styles: [`
    :host { display: inline-flex; }
    .pilula { display: inline-flex; align-items: center; gap: 6px; min-height: 36px; padding: 0 12px; border-radius: var(--ces-r-pilula);
      border: 1px solid var(--ces-linha); background: var(--ces-superficie); color: var(--ces-texto-2);
      font: 500 .8125rem/1 var(--ces-fonte); white-space: nowrap; cursor: pointer; }
    .pilula.off { color: var(--ces-atencao); border-color: transparent; background: var(--ces-atencao-suave); }
    .ponto { width: 8px; height: 8px; border-radius: 50%; background: var(--ces-ok); }
  `],
})
export class IndicadorRedeComponent {
  private readonly rede = inject(ConnectivityService);
  readonly tocar = output<void>();
  readonly online = computed(() => this.rede.isOnline());
  readonly texto = computed(() => {
    switch (this.rede.statusConexao()) {
      case 'online': return 'Online';
      case 'dados_moveis_bloqueados': return 'Só Wi-Fi';
      case 'offline_manual': return 'Modo offline';
      default: return 'Sem internet';
    }
  });
}
