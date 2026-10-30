import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CesCalloutComponent } from '@ces/ui';
import { AuthService } from '../../../core/services/auth.service';
import { ConnectivityService } from '../../../core/services/connectivity.service';

/**
 * Aviso de que o servidor encerrou a sessão do aparelho.
 *
 * <p>Não bloqueia nada: a pessoa continua usando o app e lançando, e os lançamentos ficam
 * enfileirados no aparelho. O aviso existe para que ela entre de novo quando tiver internet,
 * e a fila volte a sincronizar. Sem rede, o botão some e o texto diz o que esperar.</p>
 */
@Component({
  selector: 'app-sessao-reautenticacao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonModule, CesCalloutComponent],
  template: `
    @if (auth.reautenticacaoNecessaria()) {
      <div role="status" aria-live="polite" class="px-4 pt-3 lg:px-6 print:hidden">
        <ces-callout tone="warning" title="Entre novamente para sincronizar" [dense]="true">
          <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <p>
              Seus lançamentos continuam salvos neste aparelho e ficam enfileirados para
              sincronização.
              @if (connectivity.isOnline()) {
                Entre de novo para enviá-los.
              } @else {
                Quando houver internet, entre de novo para enviá-los.
              }
            </p>
            @if (connectivity.isOnline()) {
              <p-button
                label="Entrar novamente"
                icon="pi pi-sign-in"
                size="small"
                (onClick)="entrar()"
              ></p-button>
            }
          </div>
        </ces-callout>
      </div>
    }
  `,
})
export class SessaoReautenticacaoComponent {
  protected readonly auth = inject(AuthService);
  protected readonly connectivity = inject(ConnectivityService);
  private readonly router = inject(Router);

  protected entrar(): void {
    this.router.navigate(['/login'], { queryParams: { retorno: this.router.url } });
  }
}
