import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, ViewEncapsulation, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CesAsyncStateComponent, CesPageHeaderComponent, NotificationService, CesButtonComponent, CesTextareaComponent } from '@ces/ui';

import { Aprovacao } from '../../../core/models/automacao.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AutomacoesService } from '../../../core/services/automacoes.service';

/** Um pedido de aprovação aberto por uma automação: ler os detalhes e decidir. */
@Component({
  selector: 'app-aprovacao-automacao',
  standalone: true,
  imports: [DatePipe, RouterLink, CesButtonComponent, CesTextareaComponent, CesPageHeaderComponent, CesAsyncStateComponent],
  styleUrls: ['./automacoes.css', './automacoes-campos.css'],
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="aut aut-page" style="max-width:48rem">
      @if (!aprovacao()) {
        @if (erro()) { <ces-async-state state="error" title="Aprovação não encontrada" [description]="erro()!" /> }
        @else { <ces-async-state state="loading" title="Carregando" /> }
      } @else {
        @let a = aprovacao()!;
        <ces-page-header [title]="a.titulo" icon="pi pi-check-square" [description]="'Pedido pela automação ' + a.automacaoNome" />
        <section class="aut-card" style="padding:1.25rem;display:flex;flex-direction:column;gap:1rem">
          <dl class="aut-datalist">
            <div><dt>Pedido em</dt><dd>{{ a.criadaEm | date: 'dd/MM/yyyy HH:mm' }}</dd></div>
            @if (a.expiraEm) { <div><dt>Prazo</dt><dd>{{ a.expiraEm | date: 'dd/MM/yyyy HH:mm' }}</dd></div> }
            <div><dt>Situação</dt><dd>{{ a.status === 'PENDENTE' ? 'Aguardando decisão' : a.status === 'DECIDIDA' ? a.resposta + ' por ' + a.respondidaPor : a.status }}</dd></div>
          </dl>
          @if (a.detalhes) { <div class="aut-json" style="font-family:inherit;font-size:var(--ces-font-body);white-space:pre-line">{{ a.detalhes }}</div> }
          @if (a.podeResponder) {
            <ces-textarea label="Comentário" [showOptional]="true" [rows]="3" [value]="comentario()" (valueChange)="comentario.set($event)"></ces-textarea>
            <div style="display:flex;gap:0.5rem;flex-wrap:wrap">
              @for (o of a.opcoes; track o; let i = $index) {
                <ces-button [label]="o" [variant]="i === 0 ? 'primary' : 'outline'" size="sm" [disabled]="enviando()" (pressed)="responder(o)"></ces-button>
              }
            </div>
          } @else if (a.status === 'PENDENTE') {
            <p class="aut-muted">Você não está entre os aprovadores deste pedido.</p>
          }
          <a class="aut-link" [routerLink]="['/notificacoes/automacoes', a.automacaoId, 'execucoes', a.execucaoId]">Ver a execução</a>
        </section>
      }
    </div>
  `
})
export class AprovacaoAutomacaoComponent {
  private readonly service = inject(AutomacoesService);
  private readonly notificacao = inject(NotificationService);
  private readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id')!;
  protected readonly aprovacao = signal<Aprovacao | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly comentario = signal('');
  protected readonly enviando = signal(false);

  constructor() {
    this.service.aprovacao(this.id).subscribe({ next: (a) => this.aprovacao.set(a), error: (e) => this.erro.set(getErrorMessage(e)) });
  }

  protected responder(opcao: string): void {
    this.enviando.set(true);
    this.service.responder(this.id, opcao, this.comentario()).subscribe({
      next: (a) => {
        this.enviando.set(false);
        this.aprovacao.set(a);
        this.notificacao.success('Resposta registrada. A automação continua.');
      },
      error: (e) => {
        this.enviando.set(false);
        this.notificacao.error(getErrorMessage(e));
      }
    });
  }
}
