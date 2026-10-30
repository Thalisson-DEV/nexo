import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { ComunicadoMuralResponse, TipoComunicado } from '../../../core/models/notificacao.models';
import { MuralStore } from '../../../core/services/mural-store.service';
import {
  TIPO_COMUNICADO_CLASSES,
  TIPO_COMUNICADO_ICONES
} from '../../../features/notificacoes/notificacoes-ui';
import { ComunicadoConteudoComponent } from '../comunicado-conteudo/comunicado-conteudo.component';
import { ComunicadoDetalheDialogComponent } from '../comunicado-detalhe-dialog/comunicado-detalhe-dialog.component';

/** Cada tipo troca só a paleta da faixa — o layout é o mesmo (ver bloco `styles`). */
const FAIXA_MODIFICADORES: Record<TipoComunicado, string> = {
  DICA: 'faixa--dica',
  CHANGELOG: 'faixa--changelog',
  AVISO: 'faixa--aviso',
  MANUTENCAO: 'faixa--manutencao',
  URGENTE: 'faixa--urgente'
};

/**
 * Apresentação do mural dentro do shell: os comunicados `MODAL` abrem sobre a aplicação (um por
 * vez, na ordem recebida) e os `BANNER` viram faixa fixa no topo. Os `CARD` só aparecem na
 * página do mural.
 */
@Component({
  selector: 'app-mural-host',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ButtonModule,
    DialogModule,
    TooltipModule,
    ComunicadoConteudoComponent,
    ComunicadoDetalheDialogComponent
  ],
  template: `
    @if (mural.banners().length > 0) {
      <div class="shrink-0" role="status" aria-live="polite">
        @for (item of mural.banners(); track item.id) {
          <div class="faixa" [ngClass]="faixaTipo(item)">
            <i [class]="iconeTipo(item)" class="faixa__icone" aria-hidden="true"></i>

            <span class="faixa__etiqueta">{{ item.tipoRotulo }}</span>

            <p class="faixa__texto">
              <span class="faixa__titulo">{{ item.titulo }}</span>
              @if (item.resumo) {
                <span class="faixa__divisor" aria-hidden="true"></span>
                <span class="faixa__resumo">{{ item.resumo }}</span>
              }
            </p>

            <div class="faixa__acoes">
              @if (item.acaoRotulo && item.acaoDeeplink) {
                <p-button
                  [label]="item.acaoRotulo"
                  [text]="true"
                  (onClick)="executarAcao(item)"
                  styleClass="!text-[10px] !font-bold !py-0.5 !px-2 !gap-1"
                ></p-button>
              }
              <p-button
                label="Saiba mais"
                icon="pi pi-arrow-right"
                iconPos="right"
                [text]="true"
                (onClick)="abrirDetalhe(item)"
                styleClass="!text-[10px] !font-bold !py-0.5 !px-2 !gap-1"
              ></p-button>
              @if (!item.exigeConfirmacao) {
                <p-button
                  icon="pi pi-times"
                  [text]="true"
                  [rounded]="true"
                  pTooltip="Não exibir novamente"
                  (onClick)="mural.dispensar(item)"
                  styleClass="!w-6 !h-6 !p-0 !text-[10px]"
                ></p-button>
              }
            </div>
          </div>
        }
      </div>
    }

    <!-- Modal ao entrar: um por vez, na ordem recebida -->
    @if (modalAtual(); as item) {
      <p-dialog
        [visible]="true"
        [modal]="true"
        [closable]="false"
        [draggable]="false"
        [resizable]="false"
        [style]="{ width: '92vw', maxWidth: '880px' }"
        styleClass="custom-dialog"
        appendTo="body"
      >
        <ng-template pTemplate="header">
          <div class="flex w-full items-center gap-3">
            <div class="flex h-9 w-9 items-center justify-center rounded-xl border" [ngClass]="classesTipo(item)">
              <i [class]="iconeTipo(item)" class="text-sm"></i>
            </div>
            <div class="flex min-w-0 flex-col">
              <span class="truncate text-sm font-bold text-slate-800">{{ item.titulo }}</span>
              <div class="flex items-center gap-2">
                <span class="text-[9px] font-bold uppercase tracking-widest text-slate-400">
                  {{ item.tipoRotulo }}
                </span>
                @if (item.fixado) {
                  <span class="text-[9px] font-bold uppercase tracking-widest text-amber-500">
                    <i class="pi pi-bookmark-fill text-[8px]"></i> Fixado
                  </span>
                }
                @if (mural.modais().length > 1) {
                  <span class="text-[9px] text-slate-400">1 de {{ mural.modais().length }}</span>
                }
              </div>
            </div>
          </div>
        </ng-template>

        <div class="flex flex-col gap-4 pt-2">
          @if (item.resumo) {
            <p class="max-w-[72ch] text-[12px] font-medium leading-relaxed text-slate-700">{{ item.resumo }}</p>
          }
          <app-comunicado-conteudo [comunicado]="item"></app-comunicado-conteudo>

          @if (item.exigeConfirmacao) {
            <div class="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 p-3">
              <i class="pi pi-exclamation-circle mt-0.5 text-xs text-amber-600"></i>
              <span class="text-[10px] leading-relaxed text-amber-700">
                Este comunicado exige confirmação de leitura. Ele continuará aparecendo até você
                confirmar que leu e entendeu.
              </span>
            </div>
          }
        </div>

        <ng-template pTemplate="footer">
          <div class="flex w-full flex-wrap items-center justify-end gap-2">
            @if (item.acaoRotulo && item.acaoDeeplink) {
              <p-button
                [label]="item.acaoRotulo"
                icon="pi pi-arrow-up-right"
                [text]="true"
                (onClick)="executarAcao(item)"
                styleClass="!text-primary-600 !font-bold !text-[10px]"
              ></p-button>
            }
            @if (item.exigeConfirmacao) {
              <p-button
                label="LI E ENTENDI"
                icon="pi pi-check"
                (onClick)="mural.confirmar(item)"
                styleClass="!rounded-lg !bg-primary-600 !border-primary-600 !py-2.5 !px-8 !font-bold !text-[10px]"
              ></p-button>
            } @else {
              @if (item.permiteAdiar) {
                <p-button
                  label="VER DEPOIS"
                  icon="pi pi-clock"
                  [text]="true"
                  (onClick)="mural.adiar(item)"
                  styleClass="!text-slate-500 !font-bold !text-[10px]"
                ></p-button>
              }
              <p-button
                label="NÃO EXIBIR NOVAMENTE"
                icon="pi pi-check"
                (onClick)="mural.dispensar(item)"
                styleClass="!rounded-lg !bg-primary-600 !border-primary-600 !py-2.5 !px-6 !font-bold !text-[10px]"
              ></p-button>
            }
          </div>
        </ng-template>
      </p-dialog>
    }

    <!-- Detalhe de um banner -->
    <app-comunicado-detalhe-dialog [(comunicado)]="detalhe"></app-comunicado-detalhe-dialog>
  `,
  styles: [
    `
      :host {
        display: block;
        flex-shrink: 0;
      }

      /*
       * Faixa de aviso: barra de status sólida, uma linha só, ~32px de altura. O fundo cheio na
       * cor do tipo é o que garante que ela não passe despercebida. Cada modificador de tipo
       * troca apenas a variável --faixa-fundo; o layout é escrito uma vez.
       *
       * Os tons de fundo foram escolhidos para dar ao menos 4.5:1 com texto branco (WCAG AA em
       * texto pequeno) — daí DICA usar amber-700 em vez do amber-500 da paleta de badges.
       */
      .faixa {
        position: relative;
        display: flex;
        align-items: center;
        gap: 0.5rem;
        min-height: 32px;
        padding: 0.125rem 0.5rem 0.125rem 0.875rem;
        background-color: var(--faixa-fundo);
        color: #fff;
        box-shadow: inset 0 -1px 0 0 rgb(0 0 0 / 0.15);
      }

      .faixa + .faixa {
        border-top: 1px solid rgb(255 255 255 / 0.25);
      }

      .faixa__icone {
        flex-shrink: 0;
        font-size: 0.8125rem;
      }

      .faixa__etiqueta {
        display: none;
        flex-shrink: 0;
        border-radius: 3px;
        background-color: rgb(255 255 255 / 0.22);
        padding: 0.0625rem 0.375rem;
        font-size: 8px;
        font-weight: 700;
        line-height: 1.4;
        letter-spacing: 0.08em;
        text-transform: uppercase;
      }

      @media (min-width: 640px) {
        .faixa__etiqueta {
          display: inline-block;
        }
      }

      .faixa__texto {
        display: flex;
        min-width: 0;
        flex-grow: 1;
        align-items: baseline;
        gap: 0.5rem;
        margin: 0;
        overflow: hidden;
        white-space: nowrap;
      }

      .faixa__titulo {
        flex-shrink: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        font-size: 11px;
        font-weight: 700;
        line-height: 1.3;
      }

      .faixa__divisor {
        flex-shrink: 0;
        width: 1px;
        align-self: center;
        height: 11px;
        background-color: rgb(255 255 255 / 0.4);
      }

      .faixa__resumo {
        overflow: hidden;
        text-overflow: ellipsis;
        font-size: 10.5px;
        line-height: 1.3;
        color: rgb(255 255 255 / 0.9);
      }

      .faixa__acoes {
        display: flex;
        flex-shrink: 0;
        align-items: center;
        gap: 0.125rem;
      }

      /* Os botões do PrimeNG assumem fundo claro; sobre a barra sólida eles precisam ser brancos. */
      :host ::ng-deep .faixa .p-button {
        color: #fff;
        border-radius: 6px;
      }

      :host ::ng-deep .faixa .p-button:not(:disabled):hover {
        color: #fff;
        background: rgb(255 255 255 / 0.18);
      }

      :host ::ng-deep .faixa .p-button:focus-visible {
        outline: 2px solid #fff;
        outline-offset: -2px;
        box-shadow: none;
      }

      /* URGENTE: pulso suave no ícone. Sinaliza urgência sem piscar a barra inteira. */
      .faixa--urgente .faixa__icone {
        animation: faixa-pulso 1.6s ease-in-out infinite;
      }

      @keyframes faixa-pulso {
        50% {
          opacity: 0.35;
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .faixa--urgente .faixa__icone {
          animation: none;
        }
      }

      .faixa--dica {
        --faixa-fundo: var(--p-amber-700, #b45309);
      }

      .faixa--changelog {
        --faixa-fundo: var(--p-violet-600, #7c3aed);
      }

      .faixa--aviso {
        --faixa-fundo: var(--p-primary-600, #2563eb);
      }

      .faixa--manutencao {
        --faixa-fundo: var(--p-slate-700, #334155);
      }

      .faixa--urgente {
        --faixa-fundo: var(--p-red-600, #dc2626);
      }
    `
  ]
})
export class MuralHostComponent {
  protected readonly mural = inject(MuralStore);
  private readonly router = inject(Router);

  protected readonly detalhe = signal<ComunicadoMuralResponse | null>(null);
  protected readonly modalAtual = computed(() => this.mural.modais()[0] ?? null);

  constructor() {
    // Abrir o modal já conta como visualização.
    effect(() => {
      const item = this.modalAtual();
      if (item) untracked(() => this.mural.registrarVisualizacao(item));
    });
  }

  protected abrirDetalhe(item: ComunicadoMuralResponse): void {
    this.mural.registrarVisualizacao(item);
    this.detalhe.set(item);
  }

  protected executarAcao(item: ComunicadoMuralResponse): void {
    if (!item.acaoDeeplink) return;
    this.detalhe.set(null);
    this.router.navigateByUrl(item.acaoDeeplink);
  }

  protected iconeTipo(item: ComunicadoMuralResponse): string {
    return TIPO_COMUNICADO_ICONES[item.tipo] ?? 'pi pi-megaphone';
  }

  protected classesTipo(item: ComunicadoMuralResponse): string {
    return TIPO_COMUNICADO_CLASSES[item.tipo] ?? 'bg-slate-100 text-slate-600 border-slate-200';
  }

  /** Modificador de paleta da faixa. Tipo desconhecido cai no visual de aviso. */
  protected faixaTipo(item: ComunicadoMuralResponse): string {
    return FAIXA_MODIFICADORES[item.tipo] ?? 'faixa--aviso';
  }
}
