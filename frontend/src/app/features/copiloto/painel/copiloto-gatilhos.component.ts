import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { CesIconButtonComponent } from '@ces/ui';
import { FocoPainel } from '../../../core/models/assistente.models';
import { CopilotoPainelService } from './copiloto-painel.service';

/**
 * Ação do cabeçalho do dashboard que mostra/oculta o Copiloto contextual. Leva o avatar do Copiloto
 * para ser reconhecido como a mesma funcionalidade do workspace (não é um widget de suporte externo).
 */
@Component({
  selector: 'app-copiloto-gatilho',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (painel.disponivelParaUsuario()) {
    <button
      type="button"
      class="gatilho"
      [class.gatilho--aberto]="painel.aberto()"
      [attr.aria-expanded]="painel.aberto()"
      [attr.aria-controls]="controla()"
      (click)="painel.alternar()"
    >
      <img src="icons/copiloto-1.png" alt="" aria-hidden="true" />
      <span class="gatilho__texto">{{ painel.aberto() ? 'Ocultar Copiloto' : 'Perguntar ao Copiloto' }}</span>
      <i class="pi pi-sparkles" aria-hidden="true"></i>
    </button>
    }
  `,
  styles: `
    :host { display: inline-flex; }
    .gatilho {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      min-height: var(--ces-control-height-sm, 2rem);
      padding: 0.2rem 0.75rem 0.2rem 0.25rem;
      border: 1px solid color-mix(in srgb, var(--ces-brand) 35%, var(--ces-border));
      border-radius: 999px;
      background: var(--ces-surface);
      color: var(--ces-brand);
      font: inherit;
      font-size: var(--ces-font-body);
      font-weight: 650;
      cursor: pointer;
      box-shadow: var(--ces-shadow-sm);
      transition: background var(--ces-duration-fast) var(--ces-ease-standard),
        border-color var(--ces-duration-fast) var(--ces-ease-standard);
    }
    .gatilho img { width: 1.6rem; height: 1.6rem; border-radius: 50%; background: var(--ces-brand-soft); object-fit: contain; }
    .gatilho i { font-size: 0.75rem; }
    .gatilho:hover { background: var(--ces-brand-soft); border-color: var(--ces-brand); }
    .gatilho:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 2px; }
    .gatilho:active { transform: translateY(1px); }
    .gatilho--aberto { background: var(--ces-brand-soft); border-color: var(--ces-brand); }
    @media (max-width: 480px) { .gatilho__texto { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); } .gatilho { padding-right: 0.55rem; } }
  `,
})
export class CopilotoGatilhoComponent {
  protected readonly painel = inject(CopilotoPainelService);
  readonly controla = input<string | null>('copiloto-painel-contextual');
}

/**
 * Botão "Perguntar ao Copiloto" de um elemento do dashboard (gráfico, KPI, tabela): abre o painel já
 * focado nesse elemento; a pergunta leva `foco` no contexto (id, métrica, dimensão e dados já estão no
 * contexto do painel).
 */
@Component({
  selector: 'app-copiloto-perguntar',
  standalone: true,
  imports: [CesIconButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (painel.disponivelParaUsuario()) {
      <ces-icon-button
        icon="pi pi-sparkles"
        size="icon-sm"
        [label]="rotulo()"
        (pressed)="perguntar()"
      ></ces-icon-button>
    }
  `,
})
export class CopilotoPerguntarComponent {
  protected readonly painel = inject(CopilotoPainelService);
  readonly foco = input.required<FocoPainel>();
  /** Pergunta sugerida no compositor (o usuário pode editar antes de enviar). */
  readonly pergunta = input<string | undefined>();

  protected readonly rotulo = computed(() => `Perguntar ao Copiloto sobre ${this.foco().rotulo ?? 'este elemento'}`);

  protected perguntar(): void {
    const foco = this.foco();
    const nome = foco.rotulo ?? foco.id;
    const padrao = foco.tipo === 'grafico'
      ? `Explique o gráfico “${nome}”: o que ele mostra e o que explica esse resultado?`
      : foco.tipo === 'metrica'
        ? `O que explica o indicador “${nome}” neste recorte?`
        : `Analise “${nome}” neste recorte.`;
    this.painel.perguntarSobre(foco, this.pergunta() ?? padrao);
  }
}
