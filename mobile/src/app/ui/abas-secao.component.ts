import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { IconeComponent, NomeIcone } from './icone.component';

export interface AbaSecao {
  rota: string;
  rotulo: string;
  icone?: NomeIcone;
  /** Número discreto ao lado do rótulo (ex.: aguardando aprovação). */
  contagem?: number | null;
}

/**
 * Abas no topo de um destino da barra inferior (ex.: Segurança → Inspeções | Desvios;
 * Almoxarifado → Solicitações | Movimentações). Trocar de aba troca a rota, sem empilhar
 * histórico (`replaceUrl`): o voltar do Android sai do destino, como nas abas nativas.
 */
@Component({
  selector: 'ces-abas-secao',
  standalone: true,
  imports: [RouterLink, IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="abas" [attr.aria-label]="rotulo()">
      @for (a of abas(); track a.rota) {
        <a class="aba" [routerLink]="a.rota" [replaceUrl]="true" [class.ativa]="ativa(a)" [attr.aria-current]="ativa(a) ? 'page' : null">
          @if (a.icone) { <ces-icone [nome]="a.icone" [tamanho]="18" /> }
          <span>{{ a.rotulo }}</span>
          @if (a.contagem) { <span class="conta ces-num">{{ a.contagem > 99 ? '99+' : a.contagem }}</span> }
        </a>
      }
    </nav>
  `,
  styles: [`
    :host { display: block; position: sticky; top: 0; z-index: 4; background: var(--ces-fundo); }
    .abas { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; padding: 0 var(--ces-e3); border-bottom: 1px solid var(--ces-linha); }
    .aba { position: relative; display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 48px; padding: 0 var(--ces-e2);
      text-decoration: none; color: var(--ces-texto-2); font: 600 .9375rem/1.2 var(--ces-fonte); }
    .aba::after { content: ''; position: absolute; left: 18%; right: 18%; bottom: -1px; height: 3px; border-radius: 3px 3px 0 0;
      background: var(--ces-destaque); transform: scaleX(0); transition: transform var(--ces-m-entrar) var(--ces-curva); }
    .aba.ativa { color: var(--ces-destaque); }
    .aba.ativa::after { transform: scaleX(1); }
    .aba:active { background: var(--ces-superficie-2); }
    .conta { min-width: 20px; height: 20px; padding: 0 6px; box-sizing: border-box; border-radius: 10px; display: inline-grid; place-items: center;
      background: var(--ces-atencao-suave); color: var(--ces-atencao); font: 700 .75rem/1 var(--ces-fonte); }
  `],
})
export class AbasSecaoComponent {
  private readonly router = inject(Router);
  readonly abas = input.required<AbaSecao[]>();
  readonly rotulo = input('Seções');
  private readonly url = signal(this.router.url);

  constructor() {
    const sub = this.router.events.pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(e => this.url.set((e as NavigationEnd).urlAfterRedirects));
    inject(DestroyRef).onDestroy(() => sub.unsubscribe());
  }

  ativa(a: AbaSecao): boolean {
    const caminho = this.url().split('?')[0];
    // A aba mais específica vence (/almox/movimentacoes não ativa /almox).
    const candidatas = this.abas().filter(x => caminho === x.rota || caminho.startsWith(x.rota + '/'));
    const melhor = candidatas.sort((x, y) => y.rota.length - x.rota.length)[0];
    return melhor?.rota === a.rota;
  }
}
