import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { AuthService } from '../../../core/services/auth.service';
import { ThemeService } from '../../../core/services/theme.service';
import { LoginRequest } from '../../../core/models/auth.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    InputTextModule,
    PasswordModule,
    ButtonModule,
    CardModule,
    ToastModule,
    IconFieldModule,
    InputIconModule
  ],
  providers: [MessageService],
  template: `
    <p-toast></p-toast>

    <!-- Fundo da Tela -->
    <div
      class="min-h-screen flex flex-col items-center justify-center p-4 pb-12 overflow-hidden relative transition-colors duration-200"
      [class.bg-[#020617]]="themeService.isDarkMode()"
      [class.bg-slate-50]="!themeService.isDarkMode()"
    >

      <!-- Botão de Alternância de Tema -->
      <button
        type="button"
        (click)="themeService.setDarkMode(!themeService.isDarkMode())"
        class="absolute top-4 right-4 p-2.5 rounded-xl border backdrop-blur transition-all shadow-sm flex items-center justify-center cursor-pointer z-10"
        [class.bg-slate-800\/90]="themeService.isDarkMode()"
        [class.border-slate-700]="themeService.isDarkMode()"
        [class.text-slate-200]="themeService.isDarkMode()"
        [class.hover:bg-slate-700]="themeService.isDarkMode()"
        [class.bg-white\/90]="!themeService.isDarkMode()"
        [class.border-slate-200]="!themeService.isDarkMode()"
        [class.text-slate-600]="!themeService.isDarkMode()"
        [class.hover:bg-slate-100]="!themeService.isDarkMode()"
        [title]="themeService.isDarkMode() ? 'Modo Escuro (clique para mudar para Modo Claro)' : 'Modo Claro (clique para mudar para Modo Escuro)'"
        aria-label="Alternar tema"
      >
        <i [class]="themeService.isDarkMode() ? 'pi pi-moon text-blue-400 text-sm' : 'pi pi-sun text-amber-500 text-sm'"></i>
      </button>

      <!-- Elementos Decorativos Suaves -->
      <div
        class="absolute top-[-10%] right-[-10%] w-[40%] h-[40%] rounded-full blur-[100px] -z-10 transition-all duration-300"
        [class.bg-blue-950\/30]="themeService.isDarkMode()"
        [class.opacity-20]="themeService.isDarkMode()"
        [class.bg-blue-100]="!themeService.isDarkMode()"
        [class.opacity-60]="!themeService.isDarkMode()"
      ></div>
      <div
        class="absolute bottom-[-10%] left-[-10%] w-[30%] h-[30%] rounded-full blur-[80px] -z-10 transition-all duration-300"
        [class.bg-slate-900\/40]="themeService.isDarkMode()"
        [class.opacity-20]="themeService.isDarkMode()"
        [class.bg-blue-50]="!themeService.isDarkMode()"
        [class.opacity-40]="!themeService.isDarkMode()"
      ></div>

      <!-- Card de Login -->
      <p-card
        class="w-full max-w-[350px] overflow-hidden !rounded-2xl transition-all duration-200"
        [class.login-card-dark]="themeService.isDarkMode()"
        [class.login-card-light]="!themeService.isDarkMode()"
        [class.shadow-black\/60]="themeService.isDarkMode()"
      >

        <!-- Logo Container -->
        <div class="flex flex-col items-center mb-8 pt-4">
          <div class="w-28 h-28 mb-4 flex items-center justify-center">
            @if (themeService.isDarkMode()) {
              <!-- Versão Escuro (Linhas Brancas + Texto Vermelho) -->
              <img src="/logo_vertical_dark.svg" alt="Nexo" class="w-full h-full object-contain">
            } @else {
              <!-- Versão Claro (Linhas Azuis + Texto Vermelho Original) -->
              <img src="/logo_vertical.svg" alt="Nexo" class="w-full h-full object-contain">
            }
          </div>
          <p
            class="text-[10px] font-bold uppercase tracking-[0.25em]"
            [class.text-slate-400]="themeService.isDarkMode()"
            [class.text-slate-500]="!themeService.isDarkMode()"
          >Gestão Operacional</p>
        </div>

        @if (reautenticando) {
          <p role="status" class="mb-4 px-1 text-xs leading-relaxed" [class.text-slate-300]="themeService.isDarkMode()" [class.text-slate-600]="!themeService.isDarkMode()">
            Sua sessão terminou no servidor. Entre de novo para enviar os lançamentos que estão
            salvos neste aparelho.
          </p>
        }

        <form [formGroup]="loginForm" (ngSubmit)="onSubmit()" class="flex flex-col gap-5 px-1">

          <!-- Campo Usuário -->
          <div class="flex flex-col gap-2">
            <label
              for="login"
              class="filter-label !text-[11px] ml-1"
              [class.!text-slate-300]="themeService.isDarkMode()"
              [class.!text-slate-600]="!themeService.isDarkMode()"
            >Usuário</label>
            <p-iconfield iconPosition="left">
              <p-inputicon class="pi pi-user !text-xs !text-blue-500"></p-inputicon>
              <input
                id="login"
                type="text"
                pInputText
                formControlName="login"
                placeholder="Ex: USR123456"
                class="login-input w-full !rounded-xl !py-2.5 !text-sm !font-medium !uppercase focus:!border-blue-400 focus:!ring-0 transition-all"
                [class.login-input-dark]="themeService.isDarkMode()"
                [class.login-input-light]="!themeService.isDarkMode()"
                autocomplete="username"
                (input)="onLoginInput($event)"
              />
            </p-iconfield>
            <small *ngIf="loginForm.get('login')?.invalid && loginForm.get('login')?.touched" class="text-red-500 text-[9px] font-medium ml-1">
              Formato inválido (USR + 6 números)
            </small>
          </div>

          <!-- Campo Senha -->
          <div class="flex flex-col gap-2">
            <label
              for="password"
              class="filter-label !text-[11px] ml-1"
              [class.!text-slate-300]="themeService.isDarkMode()"
              [class.!text-slate-600]="!themeService.isDarkMode()"
            >Senha</label>
            <p-iconfield iconPosition="left">
              <p-inputicon class="pi pi-lock !text-xs !text-blue-500 z-10"></p-inputicon>
              <p-password
                id="password"
                formControlName="password"
                [toggleMask]="true"
                [feedback]="false"
                placeholder="••••••••"
                class="w-full"
                [inputStyleClass]="themeService.isDarkMode() ? 'login-input login-input-dark w-full !rounded-xl !py-2.5 !text-sm focus:!border-blue-400 focus:!ring-0 transition-all' : 'login-input login-input-light w-full !rounded-xl !py-2.5 !text-sm focus:!border-blue-400 focus:!ring-0 transition-all'"
                autocomplete="current-password"
              ></p-password>
            </p-iconfield>
          </div>

          <!-- Botão Entrar -->
          <div class="pt-4 flex flex-col gap-3">
            <p-button
              label="ENTRAR NO SISTEMA"
              type="submit"
              class="w-full"
              styleClass="w-full !rounded-xl !py-4 !text-xs !font-black !tracking-widest !bg-blue-600 !border-blue-600 hover:!bg-blue-700 !shadow-lg transition-all active:scale-[0.98]"
              [class.!shadow-blue-950\/40]="themeService.isDarkMode()"
              [class.!shadow-blue-200\/50]="!themeService.isDarkMode()"
              [disabled]="loginForm.invalid"
              [loading]="isLoading()"
              icon="pi pi-sign-in"
              iconPos="right"
            ></p-button>

            @if (statusPageConfigured) {
              <p-button
                label="STATUS DO SISTEMA"
                type="button"
                class="w-full"
                styleClass="w-full !rounded-xl !py-3 !text-[10px] !font-bold !tracking-widest !bg-transparent transition-all"
                [class.login-status-dark]="themeService.isDarkMode()"
                [class.login-status-light]="!themeService.isDarkMode()"
                icon="pi pi-server"
                iconPos="left"
                (onClick)="openStatusPage()"
              ></p-button>
            }
          </div>

        </form>
      </p-card>

      <!-- Rodapé do Login -->
      <div class="mt-8 text-center">
        <span
          class="text-[9px] uppercase tracking-widest"
          [class.text-slate-500]="themeService.isDarkMode()"
          [class.text-slate-400]="!themeService.isDarkMode()"
        >Nexo | Gestão de Ativos e Eficiência Operacional © 2026</span>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
    }
    :host ::ng-deep .p-card-body {
      padding: 1.5rem;
    }

    /* Card Claro */
    :host ::ng-deep .login-card-light.p-card,
    :host .login-card-light.p-card {
      background-color: #ffffff !important;
      border: 1px solid #f1f5f9 !important; /* slate-100 */
    }

    /* Card Escuro */
    :host ::ng-deep .login-card-dark.p-card,
    :host .login-card-dark.p-card {
      background-color: #1e293b !important; /* slate-800 */
      border: 1px solid #334155 !important; /* slate-700 */
    }

    /* Input Claro */
    .login-input-light,
    :host ::ng-deep .login-input-light.p-inputtext,
    :host ::ng-deep input.login-input-light {
      background-color: #f8fafc !important; /* slate-50 */
      color: #0f172a !important; /* slate-900 */
      border: 1px solid #f1f5f9 !important; /* slate-100 */
    }
    .login-input-light::placeholder,
    :host ::ng-deep .login-input-light::placeholder {
      font-size: 0.75rem;
      color: #94a3b8 !important; /* slate-400 */
    }

    /* Input Escuro */
    .login-input-dark,
    :host ::ng-deep .login-input-dark.p-inputtext,
    :host ::ng-deep input.login-input-dark {
      background-color: #1e293b !important; /* slate-800 */
      color: #ffffff !important; /* branco puro */
      border: 1px solid #334155 !important; /* slate-700 */
    }
    .login-input-dark::placeholder,
    :host ::ng-deep .login-input-dark::placeholder {
      font-size: 0.75rem;
      color: #64748b !important; /* slate-500 */
    }

    /* Botão Status Claro */
    :host ::ng-deep .login-status-light .p-button {
      border: 1px solid #e2e8f0 !important; /* slate-200 */
      color: #64748b !important; /* slate-500 */
    }
    :host ::ng-deep .login-status-light .p-button:hover {
      background-color: #f8fafc !important; /* slate-50 */
      border-color: #cbd5e1 !important; /* slate-300 */
    }

    /* Botão Status Escuro */
    :host ::ng-deep .login-status-dark .p-button {
      border: 1px solid #334155 !important; /* slate-700 */
      color: #cbd5e1 !important; /* slate-300 */
    }
    :host ::ng-deep .login-status-dark .p-button:hover {
      background-color: rgba(51, 65, 85, 0.5) !important; /* slate-700/50 */
    }
  `]
})
export class LoginComponent {
  protected readonly themeService = inject(ThemeService);
  private readonly authService = inject(AuthService);
  private readonly messageService = inject(MessageService);

  protected readonly isLoading = signal(false);
  protected readonly reautenticando = this.authService.reautenticacaoNecessaria();
  protected readonly statusPageConfigured = environment.statusPageUrl.length > 0;

  protected readonly loginForm = new FormGroup({
    login: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(6)]
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(4)]
    })
  });

  constructor() {
    const login = this.authService.currentUser()?.login;
    if (this.reautenticando && login) {
      this.loginForm.controls.login.setValue(login);
    }
  }

  protected onLoginInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    input.value = input.value.toUpperCase();
    this.loginForm.controls.login.setValue(input.value, { emitEvent: false });
  }

  protected openStatusPage(): void {
    if (environment.statusPageUrl) {
      window.open(environment.statusPageUrl, '_blank', 'noopener,noreferrer');
    }
  }

  protected onSubmit(): void {
    if (this.loginForm.valid) {
      this.isLoading.set(true);

      const credentials: LoginRequest = {
        login: this.loginForm.controls.login.value,
        senha: this.loginForm.controls.password.value
      };

      this.authService.login(credentials).subscribe({
        next: () => {
          this.isLoading.set(false);
        },
        error: (err) => {
          this.isLoading.set(false);
          this.messageService.add({
            severity: 'error',
            summary: 'Acesso Negado',
            detail:
              err?.status === 0
                ? 'Sem conexão com o servidor. O login precisa de internet; tente quando houver sinal.'
                : getErrorMessage(err),
            life: 3000
          });
        }
      });
    }
  }
}
