import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { NotificacaoResponse } from '../../../core/models/notificacao.models';
import { NotificacoesStore } from '../../../core/services/notificacoes-store.service';
import {
  CATEGORIA_ICONES,
  PRIORIDADE_PONTO,
  tempoRelativo
} from '../../../features/notificacoes/notificacoes-ui';

/** Conteúdo do painel do sininho. O popover em si fica no header. */
@Component({
  selector: 'app-notificacoes-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ButtonModule, TooltipModule],
  template: `
    <!--
      Largura acompanha a viewport (o corpo das notificações é texto livre e pode ser longo) e a
      altura da lista usa a tela disponível em vez de um teto fixo.
    -->
    <div
      class="flex flex-col w-[92vw] max-w-[30rem] sm:w-[26rem] lg:w-[30rem] bg-[var(--ces-surface)] text-[var(--ces-foreground)] border border-[var(--ces-border)] rounded-2xl shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200"
    >
      <div class="p-4 border-b border-[var(--ces-border)] bg-[var(--ces-surface-subtle)] flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span class="text-sm font-bold text-[var(--ces-foreground)]">Notificações</span>
          @if (!store.conectado()) {
            <i
              class="pi pi-wifi text-[10px] text-[var(--ces-foreground-subtle)]"
              pTooltip="Tempo real indisponível — a lista é atualizada ao abrir o painel."
            ></i>
          }
        </div>
        @if (store.temNaoLidas()) {
          <span class="text-[10px] bg-primary-500/20 text-primary-600 dark:text-primary-400 px-2 py-0.5 rounded-full font-bold">
            {{ store.badge() }} não lidas
          </span>
        }
      </div>

      <div class="max-h-[min(65vh,34rem)] overflow-y-auto custom-scrollbar">
        @if (store.carregando() && store.recentes().length === 0) {
          <div class="flex items-center justify-center py-12 text-[var(--ces-foreground-subtle)]">
            <i class="pi pi-spin pi-spinner text-xl"></i>
          </div>
        } @else if (store.recentes().length > 0) {
          <div class="flex flex-col">
            @for (item of store.recentes(); track item.id) {
              <div
                class="p-4 border-b border-[var(--ces-border)] transition-colors group flex gap-3"
                [class.hover:bg-[var(--ces-brand-soft)]]="!!item.deeplink"
                [class.cursor-pointer]="!!item.deeplink"
                [class.bg-red-500/10]="item.prioridade === 'CRITICA' && !item.lida"
                (click)="abrir(item)"
              >
                <div class="flex flex-col items-center gap-2 pt-1 shrink-0">
                  <span class="w-2 h-2 rounded-full" [ngClass]="item.lida ? 'bg-slate-300 dark:bg-slate-700' : ponto(item)"></span>
                </div>

                <div class="flex flex-col gap-1 min-w-0 flex-grow">
                  <div class="flex items-center gap-1.5">
                    <i [class]="icone(item)" class="text-[10px] text-[var(--ces-foreground-subtle)]"></i>
                    <span class="text-[9px] uppercase tracking-wider font-bold text-[var(--ces-foreground-subtle)] truncate">
                      {{ item.categoriaRotulo }}
                    </span>
                    @if (item.prioridade === 'CRITICA') {
                      <span class="text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded bg-red-500/20 text-red-600 dark:text-red-400">
                        {{ item.prioridadeRotulo }}
                      </span>
                    }
                  </div>
                  <span
                    class="text-xs text-[var(--ces-foreground)] leading-snug"
                    [ngClass]="item.lida ? 'font-medium' : 'font-bold'"
                    >{{ item.titulo }}</span
                  >
                  <span class="text-[11px] text-[var(--ces-foreground-muted)] leading-relaxed line-clamp-3">{{ item.corpo }}</span>
                  <span class="text-[9px] text-[var(--ces-foreground-subtle)] mt-1">{{ quando(item) }}</span>
                </div>

                <div class="flex flex-col gap-0.5 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
                  <p-button
                    [icon]="item.lida ? 'pi pi-envelope' : 'pi pi-check'"
                    [text]="true"
                    [rounded]="true"
                    [pTooltip]="item.lida ? 'Marcar como não lida' : 'Marcar como lida'"
                    (onClick)="$event.stopPropagation(); alternarLeitura(item)"
                    styleClass="!w-7 !h-7 !p-0 !text-[var(--ces-foreground-subtle)]"
                  ></p-button>
                  <p-button
                    icon="pi pi-times"
                    [text]="true"
                    [rounded]="true"
                    pTooltip="Arquivar"
                    (onClick)="$event.stopPropagation(); store.arquivar(item.id)"
                    styleClass="!w-7 !h-7 !p-0 !text-[var(--ces-foreground-subtle)]"
                  ></p-button>
                </div>
              </div>
            }
          </div>
        } @else {
          <div class="flex flex-col items-center justify-center py-12 px-6 text-center">
            <div class="w-16 h-16 bg-[var(--ces-surface-subtle)] border border-[var(--ces-border)] rounded-full flex items-center justify-center mb-4">
              <i class="pi pi-bell-slash text-[var(--ces-foreground-subtle)] text-2xl"></i>
            </div>
            <span class="text-sm font-medium text-[var(--ces-foreground-muted)]">Tudo limpo por aqui!</span>
            <p class="text-[11px] text-[var(--ces-foreground-subtle)] mt-1 leading-relaxed">
              Você não tem nenhuma notificação no momento.
            </p>
          </div>
        }
      </div>

      <div class="p-2 border-t border-[var(--ces-border)] bg-[var(--ces-surface-subtle)] flex items-center justify-between gap-1">
        <p-button
          label="Ver todas"
          icon="pi pi-list"
          [text]="true"
          (onClick)="navegar('/notificacoes')"
          styleClass="!text-[10px] !py-2 !text-[var(--ces-foreground-muted)] hover:!bg-[var(--ces-surface-muted)]"
        ></p-button>
        <div class="flex items-center gap-1">
          @if (store.temNaoLidas()) {
            <p-button
              label="Marcar todas"
              icon="pi pi-check-circle"
              [text]="true"
              (onClick)="store.marcarTodasLidas()"
              styleClass="!text-[10px] !py-2 !text-primary-600 dark:!text-primary-400 hover:!bg-[var(--ces-surface-muted)]"
            ></p-button>
          }
          <p-button
            icon="pi pi-cog"
            [text]="true"
            [rounded]="true"
            pTooltip="Preferências de notificação"
            (onClick)="navegar('/notificacoes/preferencias')"
            styleClass="!w-8 !h-8 !p-0 !text-[var(--ces-foreground-subtle)]"
          ></p-button>
        </div>
      </div>
    </div>
  `
})
export class NotificacoesPanelComponent {
  protected readonly store = inject(NotificacoesStore);
  private readonly router = inject(Router);

  readonly fechar = output<void>();

  protected abrir(item: NotificacaoResponse): void {
    // `deeplink` já vem renderizado pelo servidor. Sem ele o item não é clicável — não se
    // inventa destino a partir de `origem` + `idReferencia`.
    if (!item.deeplink) return;
    this.store.marcarLida(item);
    this.fechar.emit();
    this.router.navigateByUrl(item.deeplink);
  }

  protected alternarLeitura(item: NotificacaoResponse): void {
    if (item.lida) this.store.marcarNaoLida(item);
    else this.store.marcarLida(item);
  }

  protected navegar(rota: string): void {
    this.fechar.emit();
    this.router.navigateByUrl(rota);
  }

  protected icone(item: NotificacaoResponse): string {
    return CATEGORIA_ICONES[item.categoria] ?? 'pi pi-bell';
  }

  protected ponto(item: NotificacaoResponse): string {
    return PRIORIDADE_PONTO[item.prioridade] ?? 'bg-primary-500';
  }

  protected quando(item: NotificacaoResponse): string {
    return tempoRelativo(item.dataCriacao);
  }
}
