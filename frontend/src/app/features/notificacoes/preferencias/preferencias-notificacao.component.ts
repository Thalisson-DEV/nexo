import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MenuItem, MessageService } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import {
  CategoriaNotificacao,
  PreferenciaNotificacaoResponse
} from '../../../core/models/notificacao.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { NotificacoesService } from '../../../core/services/notificacoes.service';
import { HelpDialogComponent } from '../../../shared/components/help-dialog/help-dialog.component';
import { CATEGORIA_ICONES } from '../notificacoes-ui';

interface LinhaPreferencia extends PreferenciaNotificacaoResponse {
  icone: string;
}

@Component({
  selector: 'app-preferencias-notificacao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    BreadcrumbModule,
    ButtonModule,
    HelpDialogComponent,
    SkeletonModule,
    ToggleSwitchModule,
    TooltipModule
  ],
  template: `
    <div class="flex flex-col gap-4 pb-12">
      <div class="flex items-center justify-between">
        <p-breadcrumb [model]="breadcrumb" styleClass="!bg-transparent !border-0 !p-0"></p-breadcrumb>
        <p-button
          icon="pi pi-question-circle"
          [text]="true"
          severity="secondary"
          pTooltip="Ajuda"
          (onClick)="helpDialog.show()"
          styleClass="!text-slate-400"
        ></p-button>
      </div>

      <div class="flex flex-wrap items-end justify-between gap-3">
        <div class="flex flex-col">
          <h1 class="text-base font-bold text-slate-800 app-dark:text-white dark:text-white">Preferências de notificação</h1>
          <p class="text-[11px] text-slate-500 app-dark:text-slate-400 dark:text-slate-400">
            Escolha por onde quer ser avisado em cada assunto do sistema.
          </p>
        </div>
        <div class="flex items-center gap-2">
          @if (alterado()) {
            <p-button
              label="DESFAZER"
              icon="pi pi-undo"
              [text]="true"
              (onClick)="desfazer()"
              styleClass="!text-slate-500 app-dark:!text-slate-300 dark:!text-slate-300 !font-bold !text-[10px]"
            ></p-button>
          }
          <p-button
            label="SALVAR"
            icon="pi pi-check"
            [disabled]="!alterado()"
            [loading]="salvando()"
            (onClick)="salvar()"
            styleClass="!rounded-lg !bg-primary-700 !border-primary-700 !py-2.5 !px-8 !font-bold !text-[10px]"
          ></p-button>
        </div>
      </div>

      <!-- Container 1: Informações de E-mail -->
      <div
        class="flex items-center gap-3.5 rounded-2xl p-4 transition-all duration-200 border bg-[#f0f7ff] border-[#bfdbfe] dark:bg-[#081b36] dark:border-[#1d4ed8]/40"
      >
        <div
          class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#2563eb] text-white shadow-sm dark:bg-[#1e40af]/60 dark:text-[#93c5fd]"
        >
          <i class="pi pi-envelope text-base"></i>
        </div>
        <span
          class="text-xs leading-relaxed text-[#1e3a8a] dark:text-[#e0edff] font-medium"
        >
          Você recebe por e-mail os avisos das categorias marcadas. Desmarque o que preferir acompanhar apenas pelo sininho.
        </span>
      </div>

      <!-- Container 2: Aviso de Notificações Críticas -->
      <div
        class="flex items-center gap-3.5 rounded-2xl p-4 transition-all duration-200 border bg-[#fffbeb] border-[#fde68a] dark:bg-[#2c1806] dark:border-[#b45309]/40"
      >
        <div
          class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#d97706] text-white shadow-sm dark:bg-[#92400e]/60 dark:text-[#fde68a]"
        >
          <i class="pi pi-exclamation-triangle text-base"></i>
        </div>
        <span
          class="text-xs leading-relaxed text-[#78350f] dark:text-[#fef3c7] font-medium"
        >
          Avisos de prioridade <strong class="font-bold text-[#b45309] dark:text-[#fcd34d]">crítica</strong> são sempre entregues, mesmo em categorias desligadas aqui. É proposital: são os avisos obrigatórios.
        </span>
      </div>

      <div class="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm dark:!border-slate-800 dark:bg-slate-900">
        <div class="hidden items-center gap-4 border-b border-slate-100 bg-slate-50/50 px-5 py-3 md:flex dark:!border-slate-800 dark:!bg-slate-800/50">
          <span class="flex-grow text-[9px] font-bold uppercase tracking-widest text-slate-400 dark:text-slate-400">Assunto</span>
          <span class="w-24 text-center text-[9px] font-bold uppercase tracking-widest text-slate-400 dark:text-slate-400">No sistema</span>
          <span class="w-24 text-center text-[9px] font-bold uppercase tracking-widest text-slate-400 dark:text-slate-400">Por e-mail</span>
        </div>

        @if (carregando()) {
          <div class="flex flex-col gap-2 p-4">
            @for (item of [1, 2, 3, 4, 5, 6]; track item) {
              <p-skeleton height="52px" borderRadius="12px"></p-skeleton>
            }
          </div>
        } @else {
          <div class="flex flex-col divide-y divide-slate-50 dark:divide-slate-800">
            @for (linha of linhas(); track linha.categoria) {
              <div class="flex flex-wrap items-center gap-4 px-5 py-4">
                <div class="flex min-w-0 flex-grow items-center gap-3">
                  <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-slate-50 border border-slate-100 dark:!bg-slate-800 dark:border-slate-700">
                    <i [class]="linha.icone" class="text-sm text-slate-400 dark:!text-slate-300"></i>
                  </div>
                  <span class="truncate text-xs font-medium text-slate-700 dark:text-white">{{ linha.categoriaRotulo }}</span>
                </div>

                <div class="flex w-24 items-center justify-center gap-2">
                  <span class="text-[9px] uppercase tracking-wider text-slate-400 md:hidden">Sistema</span>
                  <p-toggleswitch
                    [(ngModel)]="linha.inApp"
                    (ngModelChange)="marcarAlteracao()"
                    [ariaLabel]="'Receber ' + linha.categoriaRotulo + ' no sistema'"
                  />
                </div>

                <div class="flex w-24 items-center justify-center gap-2">
                  <span class="text-[9px] uppercase tracking-wider text-slate-400 md:hidden">E-mail</span>
                  <span
                    [pTooltip]="linha.permiteEmail ? '' : 'Marcações são avisos internos da aplicação.'"
                    tooltipPosition="left"
                  >
                    <p-toggleswitch
                      [(ngModel)]="linha.email"
                      (ngModelChange)="marcarAlteracao()"
                      [disabled]="!linha.permiteEmail"
                      [ariaLabel]="'Receber ' + linha.categoriaRotulo + ' por e-mail'"
                    />
                  </span>
                </div>
              </div>
            }
          </div>
        }
      </div>
    </div>

    <app-help-dialog #helpDialog title="Guia da tela: Preferências de notificação">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-sliders-h"></i> Como isso funciona</h4>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-bell"></i></div>
          <div class="help-text">
            <span class="help-text-title">No sistema</span>
            <span class="help-text-desc">
              Controla o que chega no sininho e na sua caixa de entrada dentro do Nexo.
            </span>
          </div>
        </div>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-envelope"></i></div>
          <div class="help-text">
            <span class="help-text-title">Por e-mail</span>
            <span class="help-text-desc">
              Envia o mesmo aviso para o e-mail cadastrado no seu usuário. Sem e-mail cadastrado,
              nada é enviado por este canal — procure o administrador para cadastrar o seu.
            </span>
          </div>
        </div>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-at"></i></div>
          <div class="help-text">
            <span class="help-text-title">Marcações</span>
            <span class="help-text-desc">
              Quando alguém marca você num texto, o aviso existe só dentro da aplicação. Por isso a
              coluna de e-mail dessa linha fica travada.
            </span>
          </div>
        </div>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-exclamation-circle"></i></div>
          <div class="help-text">
            <span class="help-text-title">Exceção crítica</span>
            <span class="help-text-desc">
              Avisos críticos ignoram estas escolhas e são sempre entregues.
            </span>
          </div>
        </div>
      </div>
    </app-help-dialog>
  `
})
export class PreferenciasNotificacaoComponent {
  private readonly service = inject(NotificacoesService);
  private readonly messages = inject(MessageService);

  protected readonly breadcrumb: MenuItem[] = [
    { label: 'Comunicação' },
    { label: 'Preferências de notificação' }
  ];

  protected readonly linhas = signal<LinhaPreferencia[]>([]);
  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  private readonly versao = signal(0);

  /** Snapshot do que veio do servidor, para diferenciar o que o usuário mexeu. */
  private original = new Map<CategoriaNotificacao, PreferenciaNotificacaoResponse>();

  protected readonly alterado = computed(() => {
    this.versao();
    return this.alteracoes().length > 0;
  });

  constructor() {
    this.carregar();
  }

  private carregar(): void {
    this.carregando.set(true);
    this.service.listarPreferencias().subscribe({
      next: (preferencias) => this.aplicar(preferencias),
      error: (erro) => {
        this.carregando.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Não foi possível carregar',
          detail: getErrorMessage(erro)
        });
      }
    });
  }

  /** A resposta traz todas as categorias existentes: monte a tela direto dela. */
  private aplicar(preferencias: PreferenciaNotificacaoResponse[]): void {
    // `permiteEmail` ausente significa servidor anterior ao canal de e-mail. Ler isso como "não
    // permite" desabilitaria a coluna inteira; o padrão seguro é permitir e deixar o servidor
    // recusar o que não aceita.
    const normalizadas = preferencias.map((item) => ({
      ...item,
      permiteEmail: item.permiteEmail ?? true,
      email: (item.permiteEmail ?? true) && item.email
    }));

    this.original = new Map(normalizadas.map((item) => [item.categoria, { ...item }]));
    this.linhas.set(
      normalizadas.map((item) => ({
        ...item,
        icone: CATEGORIA_ICONES[item.categoria] ?? 'pi pi-bell'
      }))
    );
    this.carregando.set(false);
    this.salvando.set(false);
    this.versao.update((valor) => valor + 1);
  }

  protected marcarAlteracao(): void {
    this.versao.update((valor) => valor + 1);
  }

  protected desfazer(): void {
    this.linhas.update((linhas) =>
      linhas.map((linha) => {
        const base = this.original.get(linha.categoria);
        return base ? { ...linha, inApp: base.inApp, email: base.email } : linha;
      })
    );
    this.marcarAlteracao();
  }

  /** O PUT é parcial: envia só as linhas que o usuário mexeu. */
  protected salvar(): void {
    const preferencias = this.alteracoes();
    if (preferencias.length === 0) return;

    this.salvando.set(true);
    this.service.salvarPreferencias({ preferencias }).subscribe({
      next: (atualizadas) => {
        this.aplicar(atualizadas);
        this.messages.add({
          severity: 'success',
          summary: 'Preferências salvas',
          detail: 'Os próximos avisos já seguem estas escolhas.'
        });
      },
      error: (erro) => {
        this.salvando.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Não foi possível salvar',
          detail: getErrorMessage(erro)
        });
      }
    });
  }

  private alteracoes() {
    return this.linhas()
      .filter((linha) => {
        const base = this.original.get(linha.categoria);
        return !base || base.inApp !== linha.inApp || base.email !== linha.email;
      })
      .map((linha) => ({
        categoria: linha.categoria,
        inApp: linha.inApp,
        email: linha.email
      }));
  }
}
