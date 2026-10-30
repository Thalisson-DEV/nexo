import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { CesButtonComponent, CesCalloutComponent, CesDialogShellComponent, CesSelectComponent, CesSelectOption, CesTextareaComponent, NotificationService } from '@ces/ui';

import { MotivoRejeicaoResponse } from '../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';

const MINIMO_JUSTIFICATIVA = 5;

/** Rejeição de uma solicitação comercial: motivo cadastrado + justificativa, num só lugar. */
@Component({
  selector: 'app-solicitacao-rejeicao-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CesButtonComponent, CesCalloutComponent, CesDialogShellComponent, CesSelectComponent, CesTextareaComponent],
  template: `
    <ces-dialog-shell
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [title]="(reprovar() ? 'Reprovar' : 'Rejeitar') + ' solicitação #' + idCurto()"
      description="A solicitação sai da fila e o requisitante vê o motivo no histórico."
      size="sm"
    >
      <div class="rej-form">
        <ces-callout tone="danger" title="Encerra o atendimento">Depois de {{ reprovar() ? 'reprovada' : 'rejeitada' }}, a solicitação não pode ser aprovada nem entregue.</ces-callout>
        <ces-select
          label="Motivo"
          placeholder="Selecione o motivo"
          [options]="opcoes()"
          [(value)]="idMotivo"
          [loading]="carregandoMotivos()"
          [filter]="opcoes().length > 8"
          [required]="true"
          [error]="tentou() && !idMotivo() ? 'Escolha um motivo.' : undefined"
        />
        <ces-textarea
          label="Justificativa"
          placeholder="Explique ao requisitante o que impede o atendimento"
          [rows]="4"
          [(value)]="justificativa"
          [required]="true"
          [description]="'Mínimo de ' + minimo + ' caracteres.'"
          [error]="tentou() && !justificativaValida() ? 'Escreva ao menos ' + minimo + ' caracteres.' : undefined"
        />
      </div>
      <div dialog-footer class="ces-actions-row">
        <ces-button label="Cancelar" variant="ghost" (pressed)="visible.set(false)" />
        <ces-button [label]="(reprovar() ? 'Reprovar' : 'Rejeitar') + ' solicitação'" icon="pi pi-times" variant="destructive" [loading]="salvando()" (pressed)="confirmar()" />
      </div>
    </ces-dialog-shell>
  `,
  styles: `
    .rej-form { display: grid; gap: 1rem; }
  `
})
export class SolicitacaoRejeicaoDialogComponent {
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly notificacoes = inject(NotificationService);

  readonly visible = model(false);
  readonly idSolicitacao = input<string | null>(null);
  /** Pendente: quem aprova "reprova". Aprovada: o almoxarifado "rejeita" (não consegue atender). */
  readonly reprovar = input(false);
  readonly rejeitada = output<string>();

  protected readonly minimo = MINIMO_JUSTIFICATIVA;
  protected readonly motivos = signal<MotivoRejeicaoResponse[]>([]);
  protected readonly carregandoMotivos = signal(false);
  protected readonly idMotivo = signal<string | null>(null);
  protected readonly justificativa = signal('');
  protected readonly tentou = signal(false);
  protected readonly salvando = signal(false);

  protected readonly idCurto = computed(() => (this.idSolicitacao() ?? '').substring(0, 8));
  protected readonly opcoes = computed<CesSelectOption<string>[]>(() =>
    this.motivos().map((m) => ({ label: m.descricao ? `${m.nome} — ${m.descricao}` : m.nome, value: m.id }))
  );
  protected readonly justificativaValida = computed(() => this.justificativa().trim().length >= MINIMO_JUSTIFICATIVA);

  constructor() {
    effect(() => {
      if (!this.visible()) return;
      untracked(() => {
        this.idMotivo.set(null);
        this.justificativa.set('');
        this.tentou.set(false);
        if (!this.motivos().length) this.carregarMotivos();
      });
    });
  }

  private carregarMotivos(): void {
    this.carregandoMotivos.set(true);
    this.almoxarifado.listarMotivosRejeicaoAtivos().subscribe({
      next: (res) => {
        this.motivos.set(res.content ?? []);
        this.carregandoMotivos.set(false);
      },
      error: (err) => {
        this.carregandoMotivos.set(false);
        this.notificacoes.error(getErrorMessage(err), { title: 'Não foi possível carregar os motivos de rejeição' });
      }
    });
  }

  protected confirmar(): void {
    const id = this.idSolicitacao();
    this.tentou.set(true);
    if (!id || !this.idMotivo() || !this.justificativaValida() || this.salvando()) return;
    this.salvando.set(true);
    this.almoxarifado.rejeitarSolicitacao(id, { idMotivoRejeicao: this.idMotivo()!, justificativa: this.justificativa().trim() }).subscribe({
      next: () => {
        this.salvando.set(false);
        this.visible.set(false);
        this.notificacoes.success(`Solicitação #${id.substring(0, 8)} rejeitada.`);
        this.rejeitada.emit(id);
      },
      error: (err) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(err), { title: 'Não foi possível rejeitar' });
      }
    });
  }
}
