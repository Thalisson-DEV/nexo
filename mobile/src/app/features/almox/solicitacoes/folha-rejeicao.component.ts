import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { ApiError } from '../../../core/http/error.interceptor';
import { AlmoxService } from '../../../core/almox/almox.service';
import { Motivo } from '../../../core/almox/almox.models';
import { idCurto } from '../../../core/almox/almox-status';
import { ToastService } from '../../../core/ui/toast.service';
import { FolhaComponent, IconeComponent, OpcaoSeletor, SeletorComponent } from '../../../ui';

const MINIMO = 5;

/** Rejeição de uma solicitação comercial: motivo cadastrado + justificativa (mesma regra do web). */
@Component({
  selector: 'app-folha-rejeicao',
  standalone: true,
  imports: [FolhaComponent, IconeComponent, SeletorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-folha [aberta]="!!idSolicitacao()" (fechar)="fechar.emit()" [titulo]="'Rejeitar a solicitação #' + id()"
      descricao="Quem pediu vê o motivo e a justificativa. A rejeição encerra a solicitação.">
      <div class="campos">
        <ces-seletor rotulo="Motivo" [obrigatorio]="true" [opcoes]="opcoes()" [valor]="idMotivo()" (valorChange)="idMotivo.set($event)"
          [invalido]="tentou() && !idMotivo()" [placeholder]="carregando() ? 'Carregando motivos…' : 'Escolher'" [desabilitado]="carregando()" />
        <label class="ces-campo">
          <span class="ces-campo__rotulo">Justificativa *</span>
          <textarea class="ces-input texto" rows="3" [value]="justificativa()" (input)="justificativa.set($any($event.target).value)"
            [attr.aria-invalid]="tentou() && !valida()" placeholder="Explique o que impede o atendimento"></textarea>
          @if (tentou() && !valida()) { <span class="ces-campo__erro"><ces-icone nome="alerta" [tamanho]="16" />Escreva ao menos {{ minimo }} caracteres.</span> }
          @else { <span class="ces-campo__ajuda">Mínimo de {{ minimo }} caracteres.</span> }
        </label>
      </div>
      <div rodape class="ces-folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="fechar.emit()" [disabled]="enviando()">Voltar</button>
        <button type="button" class="ces-btn ces-btn--perigo" (click)="rejeitar()" [disabled]="enviando()">{{ enviando() ? 'Rejeitando…' : 'Rejeitar' }}</button>
      </div>
    </ces-folha>
  `,
  styles: [`
    .campos { display: grid; gap: var(--ces-e3); padding: 0 var(--ces-e3) var(--ces-e2); }
    .campos ces-seletor { box-shadow: inset 0 0 0 1px var(--ces-linha); border-radius: var(--ces-r-grupo); }
    .texto { min-height: 96px; padding: var(--ces-e3) var(--ces-e4); resize: vertical; }
    .campos .ces-campo__rotulo, .campos .ces-campo__ajuda, .campos .ces-campo__erro { padding-left: var(--ces-e4); }
  `],
})
export class FolhaRejeicaoComponent {
  private readonly almox = inject(AlmoxService);
  private readonly toast = inject(ToastService);
  readonly idSolicitacao = input<string | null>(null);
  readonly fechar = output<void>();
  readonly rejeitada = output<string>();

  readonly minimo = MINIMO;
  readonly motivos = signal<Motivo[]>([]);
  readonly carregando = signal(false);
  readonly idMotivo = signal<string | null>(null);
  readonly justificativa = signal('');
  readonly tentou = signal(false);
  readonly enviando = signal(false);
  readonly id = computed(() => idCurto(this.idSolicitacao()));
  readonly opcoes = computed<OpcaoSeletor[]>(() => this.motivos().map(m => ({ valor: m.id, rotulo: m.nome, apoio: m.descricao })));
  readonly valida = computed(() => this.justificativa().trim().length >= MINIMO);

  constructor() {
    effect(() => {
      if (!this.idSolicitacao()) return;
      untracked(() => {
        this.idMotivo.set(null);
        this.justificativa.set('');
        this.tentou.set(false);
        if (this.motivos().length) return;
        this.carregando.set(true);
        this.almox.motivosRejeicao().subscribe(m => { this.motivos.set(m); this.carregando.set(false); });
      });
    });
  }

  rejeitar(): void {
    const id = this.idSolicitacao();
    this.tentou.set(true);
    if (!id || !this.idMotivo() || !this.valida() || this.enviando()) return;
    this.enviando.set(true);
    this.almox.rejeitar(id, this.idMotivo()!, this.justificativa().trim()).subscribe({
      next: () => {
        this.enviando.set(false);
        this.toast.success(`Solicitação #${this.id()} rejeitada.`);
        this.rejeitada.emit(id);
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        this.toast.error(e.status === 409 ? 'Outra pessoa alterou esta solicitação. Atualize e tente de novo.' : e.detail || 'Não foi possível rejeitar.');
      },
    });
  }
}
