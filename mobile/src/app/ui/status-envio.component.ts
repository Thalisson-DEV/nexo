import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconeComponent, NomeIcone } from './icone.component';

/**
 * Estado de envio de um registro, no vocabulário normativo do Nexo:
 * committed = salvo no aparelho · queued = na fila · syncing = enviando ·
 * synced = enviado · conflict = conflito · error = não enviado (precisa de ação).
 * Nunca mostrar "enviado" para algo que só está na fila.
 */
export type EstadoEnvio = 'committed' | 'queued' | 'syncing' | 'synced' | 'conflict' | 'error';

const ROTULOS: Record<EstadoEnvio, { texto: string; icone: NomeIcone | null; classe: string }> = {
  committed: { texto: 'Salvo no aparelho', icone: 'aparelho', classe: 'neutro' },
  queued: { texto: 'Na fila', icone: 'nuvem', classe: 'fila' },
  syncing: { texto: 'Enviando', icone: null, classe: 'fila' },
  synced: { texto: 'Enviado', icone: 'check', classe: 'ok' },
  conflict: { texto: 'Conflito', icone: 'alerta', classe: 'erro' },
  error: { texto: 'Não enviado', icone: 'alerta', classe: 'atencao' },
};

@Component({
  selector: 'ces-status-envio',
  standalone: true,
  imports: [IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="chip" [class]="'chip ' + info().classe" role="status">
      @if (estado() === 'syncing') {
        <span class="giro" aria-hidden="true"></span>
      } @else if (info().icone) {
        <ces-icone [nome]="info().icone!" [tamanho]="15" />
      }
      {{ rotulo() ?? info().texto }}
    </span>
  `,
  styles: [`
    :host { display: inline-flex; flex: none; }
    .chip { display: inline-flex; align-items: center; gap: 5px; padding: 4px 9px; border-radius: var(--ces-r-pilula);
      font: 600 .75rem/1.2 var(--ces-fonte); white-space: nowrap; }
    .neutro { background: var(--ces-superficie-2); color: var(--ces-texto-2); }
    .fila { background: var(--ces-destaque-suave); color: var(--ces-destaque); }
    .ok { background: var(--ces-ok-suave); color: var(--ces-ok); }
    .atencao { background: var(--ces-atencao-suave); color: var(--ces-atencao); }
    .erro { background: var(--ces-erro-suave); color: var(--ces-erro); }
    .giro { width: 11px; height: 11px; border-radius: 50%; border: 2px solid currentColor; border-right-color: transparent;
      animation: giro .8s linear infinite; }
    @keyframes giro { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) { .giro { animation: none; border-right-color: currentColor; opacity: .6; } }
  `],
})
export class StatusEnvioComponent {
  readonly estado = input.required<EstadoEnvio>();
  /** Sobrescreve o texto padrão (ex.: "Na fila · 3"). */
  readonly rotulo = input<string | undefined>(undefined);
  readonly info = computed(() => ROTULOS[this.estado()]);
}
