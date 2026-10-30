import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { IconeComponent, NomeIcone } from './icone.component';

export interface Aba {
  rota: string;
  rotulo: string;
  icone: NomeIcone;
  /** Número pequeno sobre o ícone (ex.: pendências). */
  marcador?: number;
  /** Outras rotas que pertencem a esta aba (ex.: Material → /solicitacoes, /transferencias). */
  ativaEm?: string[];
}

/** Navegação principal: 4 destinos, rótulo sempre visível, pílula no ativo (Material 3). */
@Component({
  selector: 'ces-barra-abas',
  standalone: true,
  imports: [RouterLink, IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="barra" aria-label="Navegação principal">
      @for (aba of abas(); track aba.rota) {
        <a class="aba" [routerLink]="aba.rota" [class.ativa]="ativa(aba)" [attr.aria-current]="ativa(aba) ? 'page' : null">
          <span class="pilula">
            <ces-icone [nome]="aba.icone" />
            @if (aba.marcador) {
              <span class="marcador ces-num" aria-hidden="true">{{ aba.marcador > 99 ? '99+' : aba.marcador }}</span>
              <span class="ces-sr-only">, {{ aba.marcador }} aguardando envio</span>
            }
          </span>
          <span class="rotulo">{{ aba.rotulo }}</span>
        </a>
      }
    </nav>
  `,
  styles: [`
    :host { display: block; flex: none; }
    .barra { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; background: var(--ces-superficie);
      border-top: 1px solid var(--ces-linha); padding: 6px 4px calc(6px + var(--ces-safe-base)); }
    .aba { display: grid; justify-items: center; gap: 2px; min-height: 56px; padding-top: 4px; text-decoration: none;
      color: var(--ces-texto-2); font: 500 .75rem/1.2 var(--ces-fonte); }
    .pilula { position: relative; width: 60px; height: 32px; border-radius: var(--ces-r-pilula); display: grid; place-items: center;
      transition: background-color var(--ces-m-entrar) var(--ces-curva); }
    .aba.ativa { color: var(--ces-texto); font-weight: 600; }
    .aba.ativa .pilula { background: var(--ces-destaque-suave); color: var(--ces-destaque); }
    .aba:active .pilula { background: var(--ces-superficie-2); }
    .marcador { position: absolute; top: -2px; left: 34px; min-width: 18px; height: 18px; padding: 0 5px; box-sizing: border-box;
      border-radius: 9px; background: var(--ces-destaque); color: var(--ces-destaque-texto); font: 700 .6875rem/18px var(--ces-fonte);
      text-align: center; }
  `],
})
export class BarraAbasComponent {
  private readonly router = inject(Router);
  readonly abas = input.required<Aba[]>();
  private readonly url = signal(this.router.url);

  constructor() {
    const sub = this.router.events.pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(e => this.url.set((e as NavigationEnd).urlAfterRedirects));
    inject(DestroyRef).onDestroy(() => sub.unsubscribe());
  }

  ativa(aba: Aba): boolean {
    const caminho = this.url().split('?')[0];
    return [aba.rota, ...(aba.ativaEm ?? [])].some(r => caminho === r || caminho.startsWith(r + '/'));
  }
}
