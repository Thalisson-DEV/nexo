import { CommonModule, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { SkeletonModule } from 'primeng/skeleton';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { CesStatusComponent, CesTone, NotificationService } from '@ces/ui';

import { getErrorMessage } from '../../../core/models/shared.models';
import {
  AnexoEventoResponse,
  EventoSstResponse,
  PAPEL_ENVOLVIDO_BADGE,
  PAPEL_ENVOLVIDO_LABELS,
  PRAZO_COMUNICACAO_HORAS,
  REGISTRO_EVIDENCIA_LABELS,
  STATUS_EVENTO_BADGE,
  STATUS_EVENTO_LABELS,
  StatusEventoSst,
  TIPO_CAT_LABELS,
  TIPO_ENVOLVIDO_LABELS,
  TIPO_EVENTO_BADGE,
  TIPO_EVENTO_LABELS,
  TipoEventoSst,
  eventoEditavel
} from '../../../core/models/sst-evento.models';
import { AuthService } from '../../../core/services/auth.service';
import { SstService } from '../../../core/services/sst.service';
import { EventoAnexosComponent } from './evento-anexos.component';

type AcaoCicloVida = 'tratativa' | 'conclusao' | 'cancelamento' | 'reabertura';
type EtapaCiclo = 'registro' | 'tratativa' | 'desfecho';

@Component({
  selector: 'app-sst-evento-detalhe',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    DatePipe,
    FormsModule,
    BreadcrumbModule,
    ButtonModule,
    DialogModule,
    ProgressSpinnerModule,
    SkeletonModule,
    TextareaModule,
    TooltipModule,
    EventoAnexosComponent,
    CesStatusComponent
  ],
  templateUrl: './evento-detalhe.component.html',
  styleUrl: './evento-detalhe.component.css'
})
export class EventoDetalheComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly sst = inject(SstService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  protected readonly evento = signal<EventoSstResponse | null>(null);
  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly canWrite = signal(false);

  protected dialogAberto = false;
  protected acao: AcaoCicloVida = 'tratativa';
  protected textoAcao = '';

  private idEvento!: string;

  protected readonly breadcrumb = computed<MenuItem[]>(() => [
    { label: 'Segurança (SST)' },
    { label: 'Eventos de segurança', routerLink: '/sst/eventos' },
    { label: this.evento()?.numeroRegistro ?? 'Detalhe' }
  ]);

  ngOnInit(): void {
    this.idEvento = this.route.snapshot.paramMap.get('id')!;
    this.canWrite.set((this.auth.currentUser()?.permissoes ?? []).includes('sst:evento:write'));
    this.carregar();
  }

  protected editavel(evento: EventoSstResponse): boolean {
    return eventoEditavel(evento.status);
  }

  protected estadoEtapa(evento: EventoSstResponse, etapa: EtapaCiclo): 'done' | 'active' | 'pending' | 'cancelled' {
    if (evento.status === 'CANCELADO') return etapa === 'desfecho' ? 'cancelled' : 'done';
    if (etapa === 'registro') return evento.status === 'REGISTRADO' ? 'active' : 'done';
    if (etapa === 'tratativa') {
      if (evento.status === 'REGISTRADO') return 'pending';
      return evento.status === 'EM_TRATATIVA' ? 'active' : 'done';
    }
    return evento.status === 'CONCLUIDO' ? 'done' : 'pending';
  }

  protected voltar(): void {
    if (window.opener || window.history.length <= 1) {
      window.close();
    } else {
      this.router.navigate(['/sst/eventos']);
    }
  }

  protected print(): void {
    window.print();
  }

  protected statusBadgeClass(status: StatusEventoSst): string {
    switch (status) {
      case 'REGISTRADO':
        return 'bg-orange-100 text-orange-800 border-orange-200 dark:bg-orange-950/60 dark:text-orange-300 dark:border-orange-800';
      case 'EM_TRATATIVA':
        return 'bg-blue-100 text-blue-800 border-blue-200 dark:bg-blue-950/60 dark:text-blue-300 dark:border-blue-800';
      case 'CONCLUIDO':
        return 'bg-emerald-100 text-emerald-800 border-emerald-200 dark:bg-emerald-950/60 dark:text-emerald-300 dark:border-emerald-800';
      case 'CANCELADO':
        return 'bg-slate-100 text-slate-700 border-slate-300 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700';
      default:
        return 'bg-slate-100 text-slate-700 border-slate-300 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700';
    }
  }

  protected editar(): void {
    this.router.navigate(['/sst/eventos', this.idEvento, 'editar']);
  }

  protected abrir(acao: AcaoCicloVida): void {
    this.acao = acao;
    this.textoAcao = acao === 'conclusao' ? this.evento()?.tratativa ?? '' : '';
    this.dialogAberto = true;
  }

  protected campoObrigatorio(): boolean {
    return this.acao !== 'tratativa';
  }

  protected tituloAcao(): string {
    return {
      tratativa: 'Assumir a tratativa',
      conclusao: 'Concluir o evento',
      cancelamento: 'Cancelar o registro',
      reabertura: 'Reabrir o evento'
    }[this.acao];
  }

  protected tituloDialog(): string {
    return this.tituloAcao();
  }

  protected descricaoAcao(): string {
    return {
      tratativa: 'O evento passa para Em tratativa. Você já pode registrar o que está sendo feito.',
      conclusao: 'Descreva o que foi feito antes de concluir. O evento ficará somente para leitura até ser reaberto.',
      cancelamento: 'Use para um registro indevido ou duplicado. O evento continua legível e sai dos indicadores.',
      reabertura: 'O evento volta para Em tratativa e poderá ser editado novamente.'
    }[this.acao];
  }

  protected descricaoDialog(): string {
    return this.descricaoAcao();
  }

  protected rotuloCampo(): string {
    return this.acao === 'tratativa' || this.acao === 'conclusao' ? 'Tratativa' : 'Motivo';
  }

  protected labelCampo(): string {
    return this.rotuloCampo();
  }

  protected placeholderCampo(): string {
    return this.acao === 'cancelamento'
      ? 'Ex.: registro duplicado do EVT-2026-000012'
      : this.acao === 'reabertura'
        ? 'Ex.: a classificação informada precisa ser corrigida'
        : 'Descreva o que foi feito a respeito da ocorrência';
  }

  protected rotuloConfirmar(): string {
    return {
      tratativa: 'Assumir tratativa',
      conclusao: 'Concluir evento',
      cancelamento: 'Cancelar registro',
      reabertura: 'Reabrir evento'
    }[this.acao];
  }

  protected confirmar(): void {
    if (this.salvando()) return;
    const texto = this.textoAcao.trim();
    this.salvando.set(true);
    const operacao = {
      tratativa: () => this.sst.iniciarTratativaEvento(this.idEvento, { tratativa: texto || null }),
      conclusao: () => this.sst.concluirEvento(this.idEvento, { tratativa: texto || null }),
      cancelamento: () => this.sst.cancelarEvento(this.idEvento, { motivo: texto }),
      reabertura: () => this.sst.reabrirEvento(this.idEvento, { motivo: texto })
    }[this.acao]();

    operacao.subscribe({
      next: (evento) => {
        this.salvando.set(false);
        this.dialogAberto = false;
        this.evento.set(evento);
        this.notifications.success(
          `${evento.numeroRegistro} agora está ${STATUS_EVENTO_LABELS[evento.status].toLowerCase()}.`,
          { title: 'Evento atualizado' }
        );
      },
      error: (error) => {
        this.salvando.set(false);
        this.notifications.error(getErrorMessage(error), {
          title: 'Não foi possível concluir a ação'
        });
      }
    });
  }

  protected onAnexosAlterados(anexos: AnexoEventoResponse[]): void {
    this.evento.update((atual) => (atual ? { ...atual, anexos } : atual));
  }

  protected tipoLabel(evento: EventoSstResponse): string {
    return TIPO_EVENTO_LABELS[evento.tipo];
  }

  protected tipoTone(tipo: TipoEventoSst): CesTone {
    switch (tipo) {
      case 'ACIDENTE_TIPICO':
        return 'danger';
      case 'ACIDENTE_TRAJETO':
        return 'warning';
      case 'INCIDENTE_SEM_LESAO':
        return 'neutral';
      case 'DANO_MATERIAL':
        return 'info';
      case 'DOENCA_OCUPACIONAL':
        return 'danger';
      default:
        return 'neutral';
    }
  }

  protected tipoBadge(evento: EventoSstResponse): string {
    return TIPO_EVENTO_BADGE[evento.tipo];
  }

  protected statusLabel(evento: EventoSstResponse): string {
    return STATUS_EVENTO_LABELS[evento.status];
  }

  protected statusTone(status: StatusEventoSst): CesTone {
    switch (status) {
      case 'REGISTRADO':
        return 'danger';
      case 'EM_TRATATIVA':
        return 'warning';
      case 'CONCLUIDO':
        return 'success';
      case 'CANCELADO':
        return 'neutral';
      default:
        return 'neutral';
    }
  }

  protected statusBadge(evento: EventoSstResponse): string {
    return STATUS_EVENTO_BADGE[evento.status];
  }

  protected papelTone(papel: string): CesTone {
    switch (papel) {
      case 'ACIDENTADO':
        return 'danger';
      case 'CONDUTOR':
        return 'warning';
      case 'TESTEMUNHA':
        return 'info';
      case 'PASSAGEIRO':
        return 'neutral';
      default:
        return 'neutral';
    }
  }

  protected evidenciaTone(valor: string): CesTone {
    switch (valor) {
      case 'DISPONIVEL':
        return 'success';
      case 'INDISPONIVEL':
        return 'danger';
      default:
        return 'neutral';
    }
  }

  protected prazoHoras(evento: EventoSstResponse): number {
    return PRAZO_COMUNICACAO_HORAS[evento.tipo];
  }

  protected catLabel(evento: EventoSstResponse): string {
    return evento.tipoCat ? TIPO_CAT_LABELS[evento.tipoCat] : '—';
  }

  protected evidenciaLabel(valor: EventoSstResponse['telemetria']): string {
    return REGISTRO_EVIDENCIA_LABELS[valor];
  }

  protected papelLabel(envolvido: EventoSstResponse['envolvidos'][number]): string {
    return PAPEL_ENVOLVIDO_LABELS[envolvido.papel];
  }

  protected papelBadge(envolvido: EventoSstResponse['envolvidos'][number]): string {
    return PAPEL_ENVOLVIDO_BADGE[envolvido.papel];
  }

  protected tipoEnvolvidoLabel(envolvido: EventoSstResponse['envolvidos'][number]): string {
    return TIPO_ENVOLVIDO_LABELS[envolvido.tipo];
  }

  protected acidentadosCount(evento: EventoSstResponse): number {
    return evento.envolvidos?.filter((e) => e.papel === 'ACIDENTADO').length ?? 0;
  }

  private carregar(): void {
    this.carregando.set(true);
    this.sst.buscarEventoPorId(this.idEvento).subscribe({
      next: (evento) => {
        this.evento.set(evento);
        this.carregando.set(false);
      },
      error: (error) => {
        this.carregando.set(false);
        this.notifications.error(getErrorMessage(error), {
          title: 'Não foi possível abrir'
        });
      }
    });
  }
}
