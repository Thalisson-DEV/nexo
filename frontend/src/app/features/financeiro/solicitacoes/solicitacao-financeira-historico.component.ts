import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { CesAsyncStateComponent, CesStatusComponent } from '@ces/ui';
import { EventoSolicitacao, TipoEventoSolicitacao } from '../../../core/models/financeiro.models';

type Tom = 'success' | 'warning' | 'info' | 'danger' | 'neutral';

const EVENTOS: Record<TipoEventoSolicitacao, { icone: string; tom: Tom; rotuloTexto: string }> = {
  CRIADA: { icone: 'pi pi-file-edit', tom: 'neutral', rotuloTexto: 'Observação' },
  SUBMETIDA: { icone: 'pi pi-send', tom: 'info', rotuloTexto: 'Observação' },
  APROVADA: { icone: 'pi pi-check', tom: 'success', rotuloTexto: 'Observação' },
  REJEITADA: { icone: 'pi pi-times', tom: 'danger', rotuloTexto: 'Motivo' },
  DECISAO_DESFEITA: { icone: 'pi pi-undo', tom: 'warning', rotuloTexto: 'Motivo' },
  CANCELADA: { icone: 'pi pi-ban', tom: 'neutral', rotuloTexto: 'Motivo' }
};

/**
 * Linha do tempo da solicitação: abertura, envio, decisões e decisões desfeitas,
 * com quem, quando e por quê. Mesmo desenho da linha do tempo da nota.
 */
@Component({
  selector: 'app-solicitacao-financeira-historico',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, CesAsyncStateComponent, CesStatusComponent],
  template: `
    @if (carregando()) {
      <ces-async-state state="loading" title="Carregando a linha do tempo..."></ces-async-state>
    } @else if (erro()) {
      <ces-async-state state="error" title="Linha do tempo indisponível" [description]="erro()!"
        actionLabel="Tentar novamente" (action)="recarregar.emit()"></ces-async-state>
    } @else if (!eventos().length) {
      <p class="apoio">Nenhuma movimentação registrada.</p>
    } @else {
      <ol class="linha" aria-label="Linha do tempo da solicitação">
        @for (ev of eventos(); track ev.id; let ultimo = $last) {
          <li class="item">
            <div class="trilho" aria-hidden="true">
              <span class="ponto" [attr.data-tom]="evento(ev.tipo).tom"><i [class]="evento(ev.tipo).icone"></i></span>
              @if (!ultimo) { <span class="conector"></span> }
            </div>
            <div class="conteudo">
              <div class="cabeca">
                <span class="titulo">{{ ev.rotulo }}</span>
                @if (ev.tipo === 'DECISAO_DESFEITA') {
                  <ces-status tone="warning">{{ ev.statusAnterior === 'APROVADO' ? 'Era aprovação' : 'Era rejeição' }}</ces-status>
                }
                <time class="quando" [attr.datetime]="ev.ocorridoEm">{{ ev.ocorridoEm | date: 'dd/MM/yyyy · HH:mm' }}</time>
              </div>
              <p class="meta"><i class="pi pi-user" aria-hidden="true"></i> {{ ev.usuario || 'Usuário não identificado' }}</p>

              @if (ev.valor != null) {
                <p class="mudanca">
                  @switch (ev.tipo) {
                    @case ('APROVADA') { Valor autorizado: <strong>{{ moeda(ev.valor) }}</strong> }
                    @case ('DECISAO_DESFEITA') { Deixou de valer a autorização de <span class="antes">{{ moeda(ev.valor) }}</span>; voltou a aguardar aprovação. }
                    @default { Valor solicitado: <strong>{{ moeda(ev.valor) }}</strong> }
                  }
                </p>
              } @else if (ev.tipo === 'DECISAO_DESFEITA') {
                <p class="mudanca">A rejeição deixou de valer; voltou a aguardar aprovação.</p>
              }

              @if (ev.texto) {
                <p class="motivo">
                  <i class="pi pi-comment" aria-hidden="true"></i>
                  <span><strong>{{ evento(ev.tipo).rotuloTexto }}:</strong> {{ ev.texto }}</span>
                </p>
              }
            </div>
          </li>
        }
      </ol>
    }
  `,
  styles: `
    :host { display: block; }
    .linha { list-style: none; margin: 0; padding: 0; display: grid; }
    .item { display: grid; grid-template-columns: 2.25rem minmax(0, 1fr); min-height: 3.5rem; break-inside: avoid; }
    .trilho { display: flex; flex-direction: column; align-items: center; padding-top: 0.1rem; }
    .ponto { width: 1.75rem; height: 1.75rem; border-radius: 50%; display: grid; place-items: center; font-size: 0.75rem;
      border: 2px solid var(--ces-border); background: var(--ces-surface); color: var(--ces-foreground-muted); }
    .ponto[data-tom='success'] { border-color: var(--ces-success); color: var(--ces-success); background: var(--ces-success-soft); }
    .ponto[data-tom='warning'] { border-color: var(--ces-warning); color: var(--ces-warning); background: var(--ces-warning-soft); }
    .ponto[data-tom='info'] { border-color: var(--ces-info); color: var(--ces-info); background: var(--ces-info-soft); }
    .ponto[data-tom='danger'] { border-color: var(--ces-danger); color: var(--ces-danger); background: var(--ces-danger-soft); }
    .conector { flex: 1; width: 2px; background: var(--ces-border); margin: 0.25rem 0; min-height: 1rem; }
    .conteudo { padding: 0 0 1.25rem 0.5rem; min-width: 0; display: grid; gap: 0.35rem; }
    .cabeca { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
    .titulo { font-weight: 700; font-size: 0.875rem; color: var(--ces-foreground); }
    .quando { margin-left: auto; font-size: 0.75rem; color: var(--ces-foreground-muted); font-variant-numeric: tabular-nums; }
    .meta, .apoio, .mudanca { margin: 0; font-size: 0.75rem; color: var(--ces-foreground-muted); }
    .mudanca { color: var(--ces-foreground); }
    .antes { text-decoration: line-through; color: var(--ces-foreground-muted); }
    .motivo { margin: 0; display: flex; gap: 0.5rem; align-items: flex-start; padding: 0.5rem 0.75rem; border-radius: var(--ces-radius-md);
      background: var(--ces-surface-muted); border: 1px solid var(--ces-border); font-size: 0.8125rem; color: var(--ces-foreground);
      white-space: pre-line; overflow-wrap: anywhere; }
    .motivo i { margin-top: 0.15rem; color: var(--ces-foreground-muted); }
    @media print {
      .titulo, .mudanca, .motivo { color: #0f172a !important; }
      .meta, .quando, .apoio { color: #475569 !important; }
      .motivo { background: #ffffff !important; border-color: #cbd5e1 !important; }
    }
  `
})
export class SolicitacaoFinanceiraHistoricoComponent {
  readonly eventos = input<EventoSolicitacao[]>([]);
  readonly carregando = input(false);
  readonly erro = input<string | null>(null);
  readonly recarregar = output<void>();

  protected evento(tipo: TipoEventoSolicitacao) {
    return EVENTOS[tipo];
  }

  /** O backend omite campos nulos: o valor pode chegar ausente, não só nulo. */
  protected moeda(valor: number | null | undefined): string {
    return (valor ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
