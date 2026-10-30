import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { TipoCorpoGovernanca } from '../../../core/models/governanca-sst.models';
import { AuthService } from '../../../core/services/auth.service';
import { GOVERNANCA_ABAS, GovernancaSecao } from './governanca-state';

@Component({
  selector: 'app-governanca-contexto',
  standalone: true,
  imports: [],
  templateUrl: './governanca-contexto.component.html',
  styleUrl: './governanca-contexto.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GovernancaContextoComponent {
  private readonly auth = inject(AuthService);
  readonly tipo = input.required<TipoCorpoGovernanca>();
  readonly secao = input.required<GovernancaSecao>();
  readonly tipoChange = output<TipoCorpoGovernanca>();
  protected readonly abas = GOVERNANCA_ABAS;

  protected podeAcessar(aba: GovernancaSecao): boolean {
    return aba !== 'configuracoes' ||
      (this.auth.currentUser()?.permissoes?.includes('sst:governanca:write') ?? false);
  }
}
