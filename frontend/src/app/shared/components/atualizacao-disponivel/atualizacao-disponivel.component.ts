import { Component, inject } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { VersaoAppService } from '../../../core/services/versao-app.service';

/**
 * O convite para adotar a versão nova do Nexo.
 *
 * <p><b>Por que um convite, e não um recarregamento automático.</b> Recarregar por conta
 * própria descartaria o formulário que o usuário está preenchendo — e este é justamente o
 * sistema em que alguém acabou lançando o mesmo desconto três vezes por não ter certeza do
 * que a tela tinha feito. O aviso diz o que houve, lembra de salvar antes, e deixa a hora
 * com quem está trabalhando.</p>
 *
 * <p>Fica no rodapé (largura inteira no celular, canto inferior direito no desktop) porque
 * é onde não cobre conteúdo nem o botão primário das telas de cadastro, que mora à direita
 * — daí o respiro extra na base no desktop.</p>
 *
 * <p><b>Sobre o tema escuro:</b> as variantes `dark:` do Tailwind seguem a preferência do
 * sistema operacional, que aqui não é quem manda — o tema do Nexo é o seletor `.app-dark`
 * controlado pelo `ThemeService`. Usá-las pintaria o card de escuro sobre uma tela clara.
 * Por isso o escuro vem de regras `.app-dark` explícitas, como no `texto-marcacoes`.</p>
 */
@Component({
  selector: 'app-atualizacao-disponivel',
  standalone: true,
  imports: [ButtonModule],
  template: `
    @if (versao.atualizacaoDisponivel()) {
      <div
        role="status"
        aria-live="polite"
        class="fixed inset-x-0 bottom-0 z-[60] p-4 print:hidden md:inset-x-auto md:right-6 md:bottom-24 md:p-0 md:w-[22rem]"
      >
        <div
          class="cartao-atualizacao flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl shadow-slate-900/10"
        >
          <div class="flex items-start gap-4">
            <div
              class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-primary-500 to-primary-700 text-white shadow-lg shadow-primary-500/30"
            >
              <i class="pi pi-sparkles text-lg" aria-hidden="true"></i>
            </div>

            <div class="flex min-w-0 flex-col gap-1">
              <h2 class="titulo-atualizacao text-sm font-bold tracking-tight text-slate-800">
                Nova versão disponível
              </h2>
              <p class="texto-atualizacao text-xs leading-relaxed text-slate-500">
                O Nexo foi atualizado com melhorias e correções. Atualize para continuar com
                tudo funcionando.
              </p>
            </div>
          </div>

          <!-- O lembrete é o ponto do componente: quem recarrega no meio de um lançamento
               perde o que digitou, e foi um lançamento perdido que gerou as duplicatas. -->
          <p
            class="aviso-salvar flex items-start gap-2 rounded-lg bg-amber-50 px-3 py-2 text-[11px] leading-relaxed text-amber-800"
          >
            <i class="pi pi-info-circle mt-0.5 shrink-0 text-[11px]" aria-hidden="true"></i>
            <span>Salve o que estiver preenchendo antes de atualizar.</span>
          </p>

          <div class="flex items-center justify-end gap-2">
            <p-button
              label="Agora não"
              [text]="true"
              severity="secondary"
              (onClick)="versao.adiar()"
              styleClass="!rounded-xl !font-medium !text-[11px] !px-4 !py-2.5"
            ></p-button>
            <p-button
              label="Atualizar agora"
              icon="pi pi-refresh"
              (onClick)="versao.atualizar()"
              styleClass="!rounded-xl !bg-primary-600 !border-primary-600 !text-white !font-medium !text-[11px] !tracking-wide !px-5 !py-2.5 shadow-lg shadow-primary-500/25"
            ></p-button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      @keyframes subir {
        from {
          opacity: 0;
          transform: translateY(1rem) scale(0.98);
        }
        to {
          opacity: 1;
          transform: translateY(0) scale(1);
        }
      }

      .cartao-atualizacao {
        animation: subir 0.35s cubic-bezier(0.16, 1, 0.3, 1) both;
      }

      /* Quem pediu menos movimento recebe o card já posicionado, sem a entrada. */
      @media (prefers-reduced-motion: reduce) {
        .cartao-atualizacao {
          animation: none;
        }
      }

      /* Tokens do preset (app.config.ts), para acompanhar o tema em vez de fixar um
         cinza próprio. Atenção: no colorScheme.dark a escala de surface é INVERTIDA —
         surface-0 é o slate.950 e surface-900 é o slate.50. Por isso o fundo do card
         usa um número baixo e o texto um número alto, ao contrário do que o nome sugere. */
      :host-context(.app-dark) .cartao-atualizacao {
        background-color: var(--p-surface-100);
        border-color: var(--p-surface-200);
      }

      /* O preset inverteu a escala de surface, mas não os tokens de botão: no escuro o
         secundário continua saindo em slate-600, o que deixaria o "Agora não" em 1.7:1
         sobre o card — abaixo do mínimo da WCAG. O alcance ao filho é deliberadamente
         estreito (só dentro deste card) para não virar um patch do tema global, que tem
         o mesmo problema em toda tela com botão secundário de texto. */
      :host-context(.app-dark) .cartao-atualizacao ::ng-deep .p-button-secondary {
        color: var(--p-surface-600);
      }

      :host-context(.app-dark) .titulo-atualizacao {
        color: var(--p-surface-900);
      }

      :host-context(.app-dark) .texto-atualizacao {
        color: var(--p-surface-600);
      }

      /* Âmbar literal: o preset não garante uma escala --p-amber-*, e um token
         inexistente derrubaria a regra deixando texto escuro sobre fundo escuro. */
      :host-context(.app-dark) .aviso-salvar {
        background-color: rgb(251 191 36 / 0.12);
        color: rgb(253 230 138);
      }
    `
  ]
})
export class AtualizacaoDisponivelComponent {
  protected readonly versao = inject(VersaoAppService);
}
