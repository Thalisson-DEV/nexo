import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { TokenService } from '../../core/auth/token.service';
import { SyncService } from '../../core/sync/sync.service';
import { ConnectivityService } from '../../core/sync/connectivity.service';
import { rotaInternaSegura } from '../../core/sessao/snapshot';
import { ROTA_INICIO } from '../../core/sessao/retomada.guard';
import { IconeComponent } from '../../ui';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="tela">
      <div class="marca">
        <img src="logo_vertical.svg" alt="Nexo" width="96" height="96">
        <h1>Nexo Campo</h1>
        @if (volta) {
          <p>Sua sessão expirou. Entre de novo para continuar de onde parou.</p>
        } @else if (authService.hasLocalIdentity()) {
          <p>Entre com a mesma conta para enviar o que ficou no aparelho.</p>
        } @else {
          <p>Entre com o login da sua equipe.</p>
        }
      </div>

      <form [formGroup]="form" (ngSubmit)="entrar()" class="form" novalidate>
        <div class="ces-campo">
          <label for="login">Login</label>
          <input id="login" class="ces-input" type="text" formControlName="login" autocomplete="username"
            autocapitalize="none" autocorrect="off" spellcheck="false" enterkeyhint="next">
        </div>

        <div class="ces-campo">
          <label for="senha">Senha</label>
          <div class="senha">
            <input id="senha" class="ces-input" [type]="mostrarSenha() ? 'text' : 'password'" formControlName="senha"
              autocomplete="current-password" enterkeyhint="go" [attr.aria-invalid]="erro() ? 'true' : null"
              [attr.aria-describedby]="erro() ? 'erro-login' : null">
            <button type="button" class="ver" (click)="mostrarSenha.set(!mostrarSenha())"
              [attr.aria-label]="mostrarSenha() ? 'Esconder senha' : 'Mostrar senha'" [attr.aria-pressed]="mostrarSenha()">
              {{ mostrarSenha() ? 'Esconder' : 'Mostrar' }}
            </button>
          </div>
        </div>

        @if (erro()) {
          <p id="erro-login" class="erro" role="alert"><ces-icone nome="alerta" [tamanho]="18" />{{ erro() }}</p>
        }
        @if (!rede.isOnline()) {
          <p class="aviso"><ces-icone nome="sem_rede" [tamanho]="18" />Sem internet. Entrar precisa de conexão.</p>
        }

        <button type="submit" class="ces-btn ces-btn--principal ces-btn--bloco" [disabled]="carregando()">
          @if (carregando()) { <span class="giro" aria-hidden="true"></span> Entrando… } @else { Entrar }
        </button>
      </form>
    </main>
  `,
  styles: [`
    :host { display: block; min-height: 100dvh; background: var(--ces-fundo); }
    .tela { min-height: 100dvh; box-sizing: border-box; display: flex; flex-direction: column; justify-content: center; gap: var(--ces-e8);
      padding: calc(var(--ces-safe-topo) + var(--ces-e6)) var(--ces-e6) calc(var(--ces-safe-base) + var(--ces-e6)); max-width: 440px; margin: 0 auto; }
    .marca { display: grid; gap: var(--ces-e2); }
    .marca img { width: 64px; height: auto; }
    h1 { margin: var(--ces-e3) 0 0; font: var(--ces-t-titulo); letter-spacing: -.02em; }
    .marca p { margin: 0; color: var(--ces-texto-2); }
    .form { display: grid; gap: var(--ces-e5); }
    .senha { position: relative; }
    .senha .ces-input { padding-right: 96px; }
    .ver { position: absolute; right: 4px; top: 50%; transform: translateY(-50%); min-height: 44px; padding: 0 12px; border: 0;
      background: transparent; color: var(--ces-destaque); font: 600 .875rem/1 var(--ces-fonte); border-radius: var(--ces-r-campo); cursor: pointer; }
    .erro, .aviso { margin: 0; display: flex; gap: var(--ces-e2); align-items: flex-start; font: var(--ces-t-corpo); }
    .erro { color: var(--ces-erro); }
    .aviso { color: var(--ces-atencao); }
    .giro { width: 18px; height: 18px; border-radius: 50%; border: 2.5px solid currentColor; border-right-color: transparent; animation: g .8s linear infinite; }
    @keyframes g { to { transform: rotate(360deg); } }
  `],
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  readonly authService = inject(AuthService);
  private readonly tokens = inject(TokenService);
  private readonly sync = inject(SyncService);
  readonly rede = inject(ConnectivityService);
  private readonly router = inject(Router);

  /** Para onde voltar depois de entrar (sessão expirada no meio do trabalho). */
  readonly volta = rotaInternaSegura(inject(ActivatedRoute).snapshot.queryParamMap.get('volta'));

  readonly carregando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly mostrarSenha = signal(false);

  readonly form = this.fb.nonNullable.group({
    login: ['', [Validators.required]],
    senha: ['', [Validators.required]],
  });

  entrar(): void {
    if (this.form.invalid) {
      this.erro.set('Preencha login e senha.');
      return;
    }
    this.carregando.set(true);
    this.erro.set(null);
    const donoAnterior = this.tokens.ownerId();

    this.authService.login(this.form.getRawValue()).subscribe({
      next: async () => {
        const mesmaConta = !!donoAnterior && donoAnterior === this.tokens.ownerId();
        const temDados = mesmaConta && !!this.sync.lastSyncAt();
        if (temDados) {
          // Aparelho já tem os dados desta conta: volta direto e atualiza em segundo plano.
          void this.sync.syncTotal().catch(() => {});
          await this.router.navigateByUrl(this.volta ?? ROTA_INICIO, { replaceUrl: true });
        } else {
          await this.router.navigate(['/sync'], { replaceUrl: true, queryParams: this.volta ? { volta: this.volta } : {} });
        }
      },
      error: (err) => {
        this.carregando.set(false);
        this.erro.set(err?.status === 401
          ? 'Login ou senha não conferem.'
          : err?.detail || err?.message || 'Não foi possível entrar. Tente de novo.');
      },
    });
  }
}
