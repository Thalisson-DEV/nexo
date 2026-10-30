import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesIconButtonComponent
} from '@ces/ui';
import { AutorizacaoIaPrevia, AutorizarIaRequest } from '../../../core/models/conexoes-ia.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AuthService } from '../../../core/services/auth.service';
import { ConexoesIaService } from '../../../core/services/conexoes-ia.service';
import { ThemeService } from '../../../core/services/theme.service';

/**
 * Tela de consentimento: um aplicativo de IA (Claude, ChatGPT, Cursor...) pede para usar o Nexo
 * em nome da pessoa. Sem sessão, vai ao login e volta para cá com o mesmo pedido.
 */
@Component({
  selector: 'app-autorizar-ia',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CesAsyncStateComponent, CesBooleanControlComponent, CesButtonComponent, CesCalloutComponent, CesIconButtonComponent],
  styleUrl: './autorizar-ia.component.css',
  templateUrl: './autorizar-ia.component.html'
})
export class AutorizarIaComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly service = inject(ConexoesIaService);
  private readonly tema = inject(ThemeService);

  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly previa = signal<AutorizacaoIaPrevia | null>(null);
  protected readonly escrever = signal(false);
  protected readonly enviando = signal(false);
  protected readonly usuario = computed(() => {
    const u = this.auth.currentUser();
    return u?.nome ? `${u.nome} (${u.login})` : (u?.login ?? '');
  });
  protected readonly logo = computed(() =>
    this.tema.isDarkMode() ? '/logo_vertical_dark.svg' : '/logo_vertical.svg'
  );
  protected readonly escuro = computed(() => this.tema.isDarkMode());
  protected readonly inicialUsuario = computed(
    () => (this.auth.currentUser()?.nome ?? this.auth.currentUser()?.login ?? '?').trim().charAt(0).toUpperCase() || '?'
  );

  private readonly pedido: Omit<AutorizarIaRequest, 'permiteEscrita'>;

  constructor() {
    const q = this.route.snapshot.queryParamMap;
    this.pedido = {
      responseType: q.get('response_type') ?? '',
      clientId: q.get('client_id') ?? '',
      redirectUri: q.get('redirect_uri') ?? '',
      codeChallenge: q.get('code_challenge'),
      codeChallengeMethod: q.get('code_challenge_method'),
      state: q.get('state'),
      scope: q.get('scope'),
      resource: q.get('resource')
    };

    if (!this.auth.isAuthenticated() || this.auth.currentUser()?.reautenticacaoNecessaria) {
      this.irParaLogin();
      return;
    }
    if (!this.auth.currentUser()?.permissoes?.includes('assistente:conexao:use')) {
      this.carregando.set(false);
      this.erro.set('O seu perfil não pode conectar aplicativos de IA ao Nexo.');
      return;
    }
    if (!this.pedido.clientId || !this.pedido.redirectUri) {
      this.carregando.set(false);
      this.erro.set('Faltam informações no pedido enviado pelo aplicativo.');
      return;
    }
    this.service.previa({ ...this.pedido, permiteEscrita: false }).subscribe({
      next: (previa) => {
        this.previa.set(previa);
        this.carregando.set(false);
      },
      error: (e) => {
        this.erro.set(getErrorMessage(e));
        this.carregando.set(false);
      }
    });
  }

  protected decidir(permitir: boolean): void {
    this.enviando.set(true);
    const pedido = { ...this.pedido, permiteEscrita: permitir && this.escrever() };
    (permitir ? this.service.permitir(pedido) : this.service.recusar(pedido)).subscribe({
      next: ({ redirectUrl }) => window.location.assign(redirectUrl),
      error: (e) => {
        this.enviando.set(false);
        this.erro.set(getErrorMessage(e));
      }
    });
  }

  protected trocarConta(): void {
    this.auth.logout();
    this.irParaLogin();
  }

  protected alternarTema(): void {
    this.tema.setDarkMode(!this.tema.isDarkMode());
  }

  protected irParaInicio(): void {
    this.router.navigateByUrl('/home');
  }

  private irParaLogin(): void {
    const retorno = this.router.serializeUrl(
      this.router.createUrlTree(['/conectar-ia/autorizar'], { queryParams: this.route.snapshot.queryParams })
    );
    void this.router.navigate(['/login'], { queryParams: { retorno } });
  }
}
