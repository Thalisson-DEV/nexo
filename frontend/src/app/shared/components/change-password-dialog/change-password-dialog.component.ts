import { Component, inject, signal, output, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { AuthService } from '../../../core/services/auth.service';
import { getErrorMessage } from '../../../core/models/shared.models';

@Component({
  selector: 'app-change-password-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    DialogModule,
    InputTextModule,
    PasswordModule,
    ButtonModule,
    ToastModule
  ],
  providers: [MessageService],
  template: `
    <p-toast position="top-right"></p-toast>
    <p-dialog
      [(visible)]="visible"
      [modal]="true"
      [header]="'Alterar Minha Senha'"
      [style]="{ width: '95vw', maxWidth: '400px' }"
      [draggable]="false"
      [resizable]="false"
      [showHeader]="false"
      styleClass="custom-dialog !border-0 !bg-transparent"
      contentStyleClass="!p-0 !rounded-2xl overflow-hidden"
      appendTo="body"
      (onHide)="onClose()"
    >
      <div class="bg-[var(--ces-surface)] text-[var(--ces-foreground)] border border-[var(--ces-border)] rounded-2xl overflow-hidden flex flex-col relative shadow-2xl">
        <!-- Close Button -->
        <p-button
          icon="pi pi-times"
          [text]="true"
          [rounded]="true"
          (onClick)="visible = false"
          styleClass="!absolute top-4 right-4 !text-[var(--ces-foreground-subtle)] !z-50 hover:!bg-[var(--ces-surface-muted)]"
        ></p-button>

        <!-- Header Hero Section (Estilo Stepper/Profile) -->
        <div class="pt-10 pb-6 px-6 bg-[var(--ces-surface-subtle)] border-b border-[var(--ces-border)] flex flex-col items-center text-center">
          <div class="relative mb-4">
            <div class="w-13 h-13 rounded-full bg-primary-600 flex items-center justify-center shadow-md shadow-primary-600/20">
              <i class="pi pi-lock text-white text-2xl"></i>
            </div>
          </div>

          <h2 class="font-bold text-[var(--ces-foreground)] tracking-tight uppercase text-sm">Segurança da Conta</h2>

          @if (user(); as u) {
            <div class="mt-2 flex flex-col items-center">
              <span class="text-[11px] font-semibold text-primary-600 dark:text-primary-400 bg-primary-50 dark:bg-primary-950/40 border border-primary-100 dark:border-primary-800/40 px-3 py-1 rounded-full uppercase tracking-wider">
                {{ u.login }}
              </span>
              @if (u.nome) {
                <span class="text-[10px] text-[var(--ces-foreground-muted)] font-medium mt-1 uppercase tracking-tight">{{ u.nome }}</span>
              }
            </div>
          }
        </div>

        <div class="p-6">
          <form [formGroup]="passwordForm" (ngSubmit)="onSubmit()" class="flex flex-col gap-5">
            <div class="flex flex-col gap-1.5">
              <label class="filter-label !text-[var(--ces-foreground-muted)] !text-[11px] ml-1 font-semibold">Senha Atual</label>
              <p-password
                formControlName="senhaAntiga"
                [toggleMask]="true"
                [feedback]="false"
                class="w-full"
                inputStyleClass="w-full !rounded-xl !py-3 !text-sm !bg-[var(--ces-surface-subtle)] !border-[var(--ces-border)] !text-[var(--ces-foreground)] focus:!border-primary-400 focus:!ring-0 transition-all"
                placeholder="Digite sua senha atual"
                size="small"
              ></p-password>
            </div>

            <div class="flex flex-col gap-1.5">
              <label class="filter-label !text-[var(--ces-foreground-muted)] !text-[11px] ml-1 font-semibold">Nova Senha</label>
              <p-password
                formControlName="novaSenha"
                [toggleMask]="true"
                class="w-full"
                inputStyleClass="w-full !rounded-xl !py-3 !text-sm !bg-[var(--ces-surface-subtle)] !border-[var(--ces-border)] !text-[var(--ces-foreground)] focus:!border-primary-400 focus:!ring-0 transition-all"
                placeholder="Digite a nova senha"
                promptLabel="Escolha uma senha forte"
                weakLabel="Fraca"
                mediumLabel="Média"
                strongLabel="Forte"
                size="small"
              ></p-password>
            </div>

            <div class="flex flex-col gap-1.5">
              <label class="filter-label !text-[var(--ces-foreground-muted)] !text-[11px] ml-1 font-semibold">Confirmar Nova Senha</label>
              <p-password
                formControlName="confirmarSenha"
                [toggleMask]="true"
                [feedback]="false"
                class="w-full"
                inputStyleClass="w-full !rounded-xl !py-3 !text-sm !bg-[var(--ces-surface-subtle)] !border-[var(--ces-border)] !text-[var(--ces-foreground)] focus:!border-primary-400 focus:!ring-0 transition-all"
                placeholder="Repita a nova senha"
                size="small"
              ></p-password>
              @if (passwordForm.errors?.['mismatch'] && passwordForm.get('confirmarSenha')?.touched) {
                <small class="text-red-500 text-[10px] ml-1 mt-1 font-medium italic">
                  <i class="pi pi-exclamation-triangle mr-1"></i>As senhas não conferem
                </small>
              }
            </div>

            <div class="flex flex-col gap-2 mt-4">
              <p-button
                type="submit"
                label="CONFIRMAR ALTERAÇÃO"
                [loading]="loading()"
                [disabled]="passwordForm.invalid"
                styleClass="w-full !bg-primary-600 !border-primary-600 !py-3 !font-bold !text-[10px] !rounded-xl !tracking-widest shadow-lg shadow-primary-500/20 active:scale-[0.98] transition-all"
                icon="pi pi-check"
                iconPos="right"
              ></p-button>

              <p-button
                label="CANCELAR"
                [text]="true"
                (onClick)="visible = false"
                styleClass="w-full !text-[var(--ces-foreground-subtle)] !font-medium !text-[10px] !py-3 hover:!bg-[var(--ces-surface-muted)] hover:!text-[var(--ces-foreground)] !rounded-xl transition-all mt-1.5"
              ></p-button>
            </div>
          </form>
        </div>
      </div>
    </p-dialog>
  `,
  styles: [`
    :host ::ng-deep {
      .p-password-input {
        width: 100% !important;
      }
    }
  `]
})
export class ChangePasswordDialogComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private messageService = inject(MessageService);

  visible = false;
  loading = signal(false);
  onVisibleChange = output<boolean>();

  protected readonly user = computed(() => this.authService.currentUser());

  passwordForm: FormGroup = this.fb.group({
    senhaAntiga: ['', [Validators.required]],
    novaSenha: ['', [Validators.required, Validators.minLength(6)]],
    confirmarSenha: ['', [Validators.required]]
  }, { validators: this.passwordMatchValidator });

  passwordMatchValidator(g: FormGroup) {
    return g.get('novaSenha')?.value === g.get('confirmarSenha')?.value
      ? null : { mismatch: true };
  }

  show() {
    this.passwordForm.reset();
    this.visible = true;
  }

  onClose() {
    this.visible = false;
    this.messageService.clear();
    this.onVisibleChange.emit(false);
  }

  onSubmit() {
    if (this.passwordForm.valid) {
      this.loading.set(true);
      const { senhaAntiga, novaSenha } = this.passwordForm.value;

      this.authService.changePassword({ senhaAntiga, novaSenha }).subscribe({
        next: () => {
          this.loading.set(false);
          this.messageService.clear(); // Limpa erros anteriores
          this.messageService.add({
            severity: 'success',
            summary: 'Sucesso',
            detail: 'Senha alterada com sucesso!'
          });

          this.passwordForm.reset();

          setTimeout(() => {
            this.visible = false;
            this.messageService.clear(); // Limpa o toast de sucesso ao fechar
          }, 2000);
        },
        error: (err) => {

          this.loading.set(true); // Manter carregamento por um breve momento para feedback
          setTimeout(() => this.loading.set(false), 500);

          this.messageService.add({
            severity: 'error',
            summary: 'Erro na Alteração',
            detail: getErrorMessage(err),
            life: 5000
          });
        }
      });
    }
  }
}
