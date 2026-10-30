import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, model } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { ComunicadoMuralResponse } from '../../../core/models/notificacao.models';
import { MuralStore } from '../../../core/services/mural-store.service';
import {
  TIPO_COMUNICADO_CLASSES,
  TIPO_COMUNICADO_ICONES
} from '../../../features/notificacoes/notificacoes-ui';
import { ComunicadoConteudoComponent } from '../comunicado-conteudo/comunicado-conteudo.component';

/**
 * Leitura de um comunicado fora do fluxo de modal-ao-entrar (banner expandido, card do mural).
 * Fechar aqui não dispensa nada: a dispensa é sempre uma ação rotulada.
 */
@Component({
  selector: 'app-comunicado-detalhe-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ButtonModule, DialogModule, ComunicadoConteudoComponent],
  template: `
    @if (comunicado(); as item) {
      <p-dialog
        [visible]="true"
        (visibleChange)="comunicado.set(null)"
        [modal]="true"
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
              </div>
            </div>
          </div>
        </ng-template>

        <div class="flex flex-col gap-4 pt-2">
          @if (item.resumo) {
            <p class="text-[13px] font-medium leading-relaxed text-slate-700 dark:text-slate-300">{{ item.resumo }}</p>
          }
          <app-comunicado-conteudo [comunicado]="item"></app-comunicado-conteudo>

          @if (item.exigeConfirmacao && item.confirmado) {
            <div class="flex items-center gap-2 rounded-xl border border-green-200 bg-green-50 p-3">
              <i class="pi pi-check-circle text-xs text-green-600"></i>
              <span class="text-[10px] font-medium text-green-700">
                Você já confirmou a leitura deste comunicado.
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
            @if (item.exigeConfirmacao && !item.confirmado) {
              <p-button
                label="LI E ENTENDI"
                icon="pi pi-check"
                (onClick)="confirmar(item)"
                styleClass="!rounded-lg !bg-primary-600 !border-primary-600 !py-2.5 !px-8 !font-bold !text-[10px]"
              ></p-button>
            } @else {
              <p-button
                label="FECHAR"
                [text]="true"
                (onClick)="comunicado.set(null)"
                styleClass="!text-primary-600 !font-bold !text-[10px]"
              ></p-button>
            }
          </div>
        </ng-template>
      </p-dialog>
    }
  `
})
export class ComunicadoDetalheDialogComponent {
  private readonly mural = inject(MuralStore);
  private readonly router = inject(Router);

  readonly comunicado = model<ComunicadoMuralResponse | null>(null);

  protected confirmar(item: ComunicadoMuralResponse): void {
    this.mural.confirmar(item);
    this.comunicado.set(null);
  }

  protected executarAcao(item: ComunicadoMuralResponse): void {
    if (!item.acaoDeeplink) return;
    this.comunicado.set(null);
    this.router.navigateByUrl(item.acaoDeeplink);
  }

  protected iconeTipo(item: ComunicadoMuralResponse): string {
    return TIPO_COMUNICADO_ICONES[item.tipo] ?? 'pi pi-megaphone';
  }

  protected classesTipo(item: ComunicadoMuralResponse): string {
    return TIPO_COMUNICADO_CLASSES[item.tipo] ?? 'bg-slate-100 text-slate-600 border-slate-200';
  }
}
