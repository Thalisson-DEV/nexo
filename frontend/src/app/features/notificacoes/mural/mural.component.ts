import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { MenuItem, MessageService } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { TooltipModule } from 'primeng/tooltip';
import { ComunicadoMuralResponse } from '../../../core/models/notificacao.models';
import { MuralStore } from '../../../core/services/mural-store.service';
import { ComunicadoDetalheDialogComponent } from '../../../shared/components/comunicado-detalhe-dialog/comunicado-detalhe-dialog.component';
import { HelpDialogComponent } from '../../../shared/components/help-dialog/help-dialog.component';
import {
  TIPO_COMUNICADO_CLASSES,
  TIPO_COMUNICADO_ICONES,
  formatarDataHora
} from '../notificacoes-ui';

/** Página do mural: mostra tudo o que está no ar para este usuário, na ordem do servidor. */
@Component({
  selector: 'app-mural',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    BreadcrumbModule,
    ButtonModule,
    SkeletonModule,
    TooltipModule,
    ComunicadoDetalheDialogComponent,
    HelpDialogComponent
  ],
  template: `
    <div class="flex flex-col gap-4 pb-12">
      <div class="flex items-center justify-between">
        <p-breadcrumb [model]="breadcrumb" styleClass="!bg-transparent !border-0 !p-0"></p-breadcrumb>
        <div class="flex items-center gap-1.5">
          <p-button
            icon="pi pi-refresh"
            [text]="true"
            severity="secondary"
            pTooltip="Atualizar"
            [loading]="mural.carregando()"
            (onClick)="mural.carregar(true)"
            styleClass="!text-slate-400"
          ></p-button>
          <p-button
            icon="pi pi-question-circle"
            [text]="true"
            severity="secondary"
            pTooltip="Ajuda"
            (onClick)="helpDialog.show()"
            styleClass="!text-slate-400"
          ></p-button>
        </div>
      </div>

      <div class="flex flex-wrap items-end justify-between gap-3">
        <div class="flex flex-col">
          <h1 class="text-base font-bold text-slate-800">Mural de comunicados</h1>
          <p class="text-[11px] text-slate-500">
            Avisos, novidades e orientações publicados pela empresa para o seu acesso.
          </p>
        </div>
        @if (mural.pendentesConfirmacao() > 0) {
          <span class="rounded-full bg-amber-50 px-3 py-1 text-[10px] font-bold text-amber-700">
            {{ mural.pendentesConfirmacao() }} aguardando sua confirmação
          </span>
        }
      </div>

      @if (mural.carregando() && mural.itens().length === 0) {
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          @for (item of [1, 2, 3]; track item) {
            <p-skeleton height="200px" borderRadius="16px"></p-skeleton>
          }
        </div>
      } @else if (mural.itens().length > 0) {
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          @for (item of mural.itens(); track item.id) {
            <article
              class="flex flex-col overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm transition-all hover:border-primary-200 hover:shadow-md"
            >
              <div class="flex items-center gap-2 border-b px-4 py-2.5" [ngClass]="classesTipo(item)">
                <i [class]="iconeTipo(item)" class="text-xs"></i>
                <span class="text-[9px] font-bold uppercase tracking-widest">{{ item.tipoRotulo }}</span>
                <div class="ml-auto flex items-center gap-2">
                  @if (item.fixado) {
                    <i class="pi pi-bookmark-fill text-[10px]" pTooltip="Fixado no topo"></i>
                  }
                  @if (item.exigeConfirmacao) {
                    <i
                      class="pi text-[10px]"
                      [ngClass]="item.confirmado ? 'pi-check-circle' : 'pi-exclamation-circle'"
                      [pTooltip]="item.confirmado ? 'Leitura confirmada' : 'Exige confirmação de leitura'"
                    ></i>
                  }
                </div>
              </div>

              <div class="flex flex-grow flex-col gap-2 p-4">
                <h2 class="text-xs font-bold leading-snug text-slate-800">{{ item.titulo }}</h2>
                @if (item.resumo) {
                  <p class="line-clamp-3 text-[11px] leading-relaxed text-slate-500">{{ item.resumo }}</p>
                }
                <span class="mt-auto pt-2 text-[9px] text-slate-400">
                  Publicado em {{ quando(item.dataInicio) }}
                </span>
              </div>

              <div class="flex items-center justify-between gap-1 border-t border-slate-50 bg-slate-50/50 p-2">
                <p-button
                  label="Abrir"
                  icon="pi pi-book"
                  [text]="true"
                  (onClick)="abrir(item)"
                  styleClass="!text-[10px] !font-bold !py-2 !text-primary-700"
                ></p-button>
                <div class="flex items-center gap-1">
                  @if (item.exigeConfirmacao && !item.confirmado) {
                    <p-button
                      label="Li e entendi"
                      icon="pi pi-check"
                      [text]="true"
                      (onClick)="mural.confirmar(item)"
                      styleClass="!text-[10px] !font-bold !py-2 !text-green-600"
                    ></p-button>
                  } @else {
                    <p-button
                      icon="pi pi-eye-slash"
                      [text]="true"
                      [rounded]="true"
                      pTooltip="Não exibir novamente"
                      (onClick)="mural.dispensar(item)"
                      styleClass="!w-8 !h-8 !p-0 !text-slate-400"
                    ></p-button>
                  }
                </div>
              </div>
            </article>
          }
        </div>
      } @else {
        <div
          class="flex flex-col items-center justify-center gap-3 rounded-2xl border border-slate-100 bg-white py-20 text-center shadow-sm"
        >
          <div class="flex h-16 w-16 items-center justify-center rounded-full bg-slate-50">
            <i class="pi pi-megaphone text-2xl text-slate-300"></i>
          </div>
          <span class="text-sm font-medium text-slate-700 dark:text-slate-200">Nenhum comunicado no ar</span>
          <span class="max-w-sm text-[11px] leading-relaxed text-slate-400">
            Quando a empresa publicar um aviso direcionado ao seu acesso, ele aparece aqui.
          </span>
        </div>
      }
    </div>

    <app-comunicado-detalhe-dialog [(comunicado)]="selecionado"></app-comunicado-detalhe-dialog>

    <app-help-dialog #helpDialog title="Guia da tela: Mural de comunicados">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-megaphone"></i> Como o mural funciona</h4>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-book"></i></div>
          <div class="help-text">
            <span class="help-text-title">Abrir</span>
            <span class="help-text-desc">
              Mostra o comunicado completo, com vídeo e anexos quando houver. A abertura fica
              registrada para a empresa.
            </span>
          </div>
        </div>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-check-circle"></i></div>
          <div class="help-text">
            <span class="help-text-title">Li e entendi</span>
            <span class="help-text-desc">
              Alguns comunicados exigem confirmação de leitura e só saem do seu caminho depois
              dela — é a prova de comunicação usada em auditorias.
            </span>
          </div>
        </div>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-eye-slash"></i></div>
          <div class="help-text">
            <span class="help-text-title">Não exibir novamente</span>
            <span class="help-text-desc">
              Remove o comunicado do seu mural em definitivo. Os demais colegas continuam vendo.
            </span>
          </div>
        </div>
      </div>
    </app-help-dialog>
  `
})
export class MuralComponent {
  protected readonly mural = inject(MuralStore);
  private readonly route = inject(ActivatedRoute);
  private readonly messages = inject(MessageService);

  protected readonly breadcrumb: MenuItem[] = [{ label: 'Comunicação' }, { label: 'Mural' }];
  protected readonly selecionado = signal<ComunicadoMuralResponse | null>(null);

  /** `/mural/{id}` é o destino que o backend renderiza nos deeplinks de comunicado. */
  private readonly idSolicitado = signal<string | null>(null);
  private idJaTratado: string | null = null;

  constructor() {
    this.mural.carregar();

    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((parametros) => {
      this.idJaTratado = null;
      this.idSolicitado.set(parametros.get('id'));
    });

    // Só dá para abrir depois que o mural chegou — o deeplink pode ser aberto de fora do app.
    effect(() => {
      const id = this.idSolicitado();
      const carregado = this.mural.carregado();
      const itens = this.mural.itens();
      if (!id || !carregado || this.idJaTratado === id) return;

      untracked(() => {
        this.idJaTratado = id;
        const item = itens.find((comunicado) => comunicado.id === id);
        if (item) {
          this.abrir(item);
          return;
        }
        this.messages.add({
          severity: 'info',
          summary: 'Comunicado indisponível',
          detail: 'Este comunicado não está mais no seu mural.'
        });
      });
    });
  }

  protected abrir(item: ComunicadoMuralResponse): void {
    // Comunicado de apresentação MODAL já é aberto pelo mural-host no shell. Abrir o diálogo
    // daqui empilharia um segundo diálogo do mesmo comunicado sobre o primeiro.
    if (this.mural.modais().some((modal) => modal.id === item.id)) return;
    this.mural.registrarVisualizacao(item);
    this.selecionado.set(item);
  }

  protected quando(valor: string | null): string {
    return formatarDataHora(valor);
  }

  protected iconeTipo(item: ComunicadoMuralResponse): string {
    return TIPO_COMUNICADO_ICONES[item.tipo] ?? 'pi pi-megaphone';
  }

  protected classesTipo(item: ComunicadoMuralResponse): string {
    return TIPO_COMUNICADO_CLASSES[item.tipo] ?? 'bg-slate-100 text-slate-600 border-slate-200';
  }
}
