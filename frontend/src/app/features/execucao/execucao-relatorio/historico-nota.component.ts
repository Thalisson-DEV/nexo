import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { CesAsyncStateComponent, CesStatusComponent } from '@ces/ui';

import { EventoHistoricoNota, MaterialRegistrado, TipoEventoNota } from '../../../core/models/execucao.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ExecucaoService } from '../../../core/services/execucao.service';
import { moeda } from '../dashboards/execucao-dashboards.utils';
import { quantidade } from './alterar-nota.utils';

const EVENTOS: Record<TipoEventoNota, { rotulo: string; icone: string; tom: 'success' | 'warning' | 'info' }> = {
  LANCADA: { rotulo: 'Nota lançada', icone: 'pi pi-send', tom: 'success' },
  ALTERADA: { rotulo: 'Nota alterada', icone: 'pi pi-pencil', tom: 'warning' },
  NUMERO_OFICIAL: { rotulo: 'Número oficial informado', icone: 'pi pi-hashtag', tom: 'info' }
};

/** Linha do tempo da nota: como foi lançada e cada alteração, com quem, de onde e por quê. */
@Component({
  selector: 'app-historico-nota',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, CesAsyncStateComponent, CesStatusComponent],
  template: `
    @if (carregando()) {
      <ces-async-state state="loading" title="Carregando a linha do tempo..."></ces-async-state>
    } @else if (erro()) {
      <ces-async-state state="error" title="Linha do tempo indisponível" [description]="erro()!" actionLabel="Tentar novamente" (action)="carregar()"></ces-async-state>
    } @else {
      <ol class="linha" aria-label="Linha do tempo da nota">
        @for (ev of eventos(); track $index; let ultimo = $last) {
          <li class="item">
            <div class="trilho" aria-hidden="true">
              <span class="ponto" [attr.data-tom]="evento(ev.tipo).tom"><i [class]="evento(ev.tipo).icone"></i></span>
              @if (!ultimo) { <span class="conector"></span> }
            </div>
            <div class="conteudo">
              <div class="cabeca">
                <span class="titulo">{{ evento(ev.tipo).rotulo }}</span>
                <ces-status [tone]="ev.origem === 'CAMPO' ? 'neutral' : 'brand'" [icon]="ev.origem === 'CAMPO' ? 'pi-mobile' : 'pi-desktop'">
                  {{ ev.origem === 'CAMPO' ? 'App de campo' : 'Escritório' }}
                </ces-status>
                <time class="quando" [attr.datetime]="ev.ocorridoEm">{{ ev.ocorridoEm | date: 'dd/MM/yyyy · HH:mm' }}</time>
              </div>
              <p class="meta">
                <i class="pi pi-user" aria-hidden="true"></i> {{ ev.autor }}
                @if (chegouDepois(ev)) {
                  <span class="apoio">· feita offline, chegou {{ ev.registradoEm | date: 'dd/MM HH:mm' }}</span>
                }
              </p>

              @if (ev.motivo) {
                <p class="motivo"><i class="pi pi-comment" aria-hidden="true"></i><span>{{ ev.motivo }}</span></p>
              }

              @if (ev.tipo === 'NUMERO_OFICIAL') {
                <p class="mudanca">De <span class="antes">{{ ev.detalhes.referenciaAnterior }}</span> para <strong>{{ ev.detalhes.referenciaNova }}</strong></p>
              } @else {
                @let d = ev.detalhes;
                @if (d.dadosAlterados?.length) {
                  <ul class="itens" aria-label="Dados da nota que mudaram">
                    @for (dado of d.dadosAlterados; track $index) {
                      <li><span class="sinal" aria-hidden="true">•</span>
                        <span>{{ dado.rotulo }}: <span class="antes">{{ dado.anterior || 'sem' }}</span> → <strong>{{ dado.novo || 'sem' }}</strong></span></li>
                    }
                  </ul>
                }
                @if (ev.tipo === 'LANCADA') {
                  <p class="mudanca">
                    {{ d.acoesAdicionadas.length }} {{ d.acoesAdicionadas.length === 1 ? 'serviço' : 'serviços' }} ·
                    {{ d.materiaisAdicionados.length }} {{ d.materiaisAdicionados.length === 1 ? 'material' : 'materiais' }}
                    @if (ev.valorNovo !== null) { · {{ moeda(ev.valorNovo) }} }
                  </p>
                  @if (ev.reconstruido && temAlteracoes()) {
                    <p class="apoio">Nota anterior ao histórico: o lançamento original foi remontado desfazendo as alterações abaixo.</p>
                  }
                }
                @if (d.acoesAdicionadas.length || d.acoesRemovidas.length || d.materiaisAdicionados.length || d.materiaisRemovidos.length) {
                  <ul class="itens" [attr.aria-label]="ev.tipo === 'LANCADA' ? 'O que foi lançado' : 'O que mudou'">
                    @for (a of d.acoesAdicionadas; track $index) {
                      <li [class.entrou]="ev.tipo === 'ALTERADA'"><span class="sinal" aria-hidden="true">{{ ev.tipo === 'ALTERADA' ? '+' : '•' }}</span>
                        <span class="sr-only">{{ ev.tipo === 'ALTERADA' ? 'Entrou' : 'Lançado' }}:</span> Serviço {{ a.codigo ? a.codigo + ' · ' : '' }}{{ a.nome }}</li>
                    }
                    @for (a of d.acoesRemovidas; track $index) {
                      <li class="saiu"><span class="sinal" aria-hidden="true">−</span><span class="sr-only">Saiu:</span> Serviço {{ a.codigo ? a.codigo + ' · ' : '' }}{{ a.nome }}</li>
                    }
                    @for (m of d.materiaisAdicionados; track $index) {
                      <li [class.entrou]="ev.tipo === 'ALTERADA'"><span class="sinal" aria-hidden="true">{{ ev.tipo === 'ALTERADA' ? '+' : '•' }}</span>
                        <span class="sr-only">{{ ev.tipo === 'ALTERADA' ? 'Entrou' : 'Lançado' }}:</span> {{ material(m) }}</li>
                    }
                    @for (m of d.materiaisRemovidos; track $index) {
                      <li class="saiu"><span class="sinal" aria-hidden="true">−</span><span class="sr-only">Saiu:</span> {{ material(m) }}</li>
                    }
                  </ul>
                }
                @if (ev.tipo === 'ALTERADA' && ev.valorAnterior !== null && ev.valorNovo !== null && ev.valorAnterior !== ev.valorNovo) {
                  <p class="mudanca">Valor faturado: <span class="antes">{{ moeda(ev.valorAnterior) }}</span> → <strong>{{ moeda(ev.valorNovo) }}</strong></p>
                }
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
    .conector { flex: 1; width: 2px; background: var(--ces-border); margin: 0.25rem 0; min-height: 1rem; }
    .conteudo { padding: 0 0 1.25rem 0.5rem; min-width: 0; display: grid; gap: 0.35rem; }
    .cabeca { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
    .titulo { font-weight: 700; font-size: 0.875rem; color: var(--ces-foreground); }
    .quando { margin-left: auto; font-size: 0.75rem; color: var(--ces-foreground-muted); font-variant-numeric: tabular-nums; }
    .meta, .apoio, .mudanca { margin: 0; font-size: 0.75rem; color: var(--ces-foreground-muted); }
    .mudanca { color: var(--ces-foreground); }
    .antes { text-decoration: line-through; color: var(--ces-foreground-muted); }
    .motivo { margin: 0; display: flex; gap: 0.5rem; align-items: flex-start; padding: 0.5rem 0.75rem; border-radius: var(--ces-radius-md);
      background: var(--ces-surface-muted); border: 1px solid var(--ces-border); font-size: 0.8125rem; color: var(--ces-foreground); }
    .motivo i { margin-top: 0.15rem; color: var(--ces-foreground-muted); }
    .itens { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.2rem; font-size: 0.8125rem; color: var(--ces-foreground); }
    .itens li { display: flex; gap: 0.4rem; align-items: baseline; }
    .sinal { width: 1rem; flex-shrink: 0; text-align: center; font-weight: 700; color: var(--ces-foreground-muted); }
    .entrou .sinal { color: var(--ces-success); }
    .saiu { color: var(--ces-foreground-muted); }
    .saiu .sinal { color: var(--ces-danger); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
  `
})
export class HistoricoNotaComponent {
  private readonly execucoes = inject(ExecucaoService);

  readonly idExecucao = input.required<string>();
  /** Muda depois de cada alteração para recarregar a linha do tempo. */
  readonly versao = input<number>(0);

  protected readonly eventos = signal<EventoHistoricoNota[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly moeda = moeda;
  protected readonly temAlteracoes = computed(() => this.eventos().some((e) => e.tipo === 'ALTERADA'));

  constructor() {
    effect(() => {
      this.idExecucao();
      this.versao();
      untracked(() => this.carregar());
    });
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.execucoes.historico(this.idExecucao()).subscribe({
      next: (eventos) => {
        this.eventos.set(eventos);
        this.carregando.set(false);
      },
      error: (err) => {
        this.erro.set(getErrorMessage(err));
        this.carregando.set(false);
      }
    });
  }

  protected evento(tipo: TipoEventoNota): (typeof EVENTOS)[TipoEventoNota] {
    return EVENTOS[tipo];
  }

  protected material(m: MaterialRegistrado): string {
    const nome = m.codigo ? `${m.codigo} · ${m.descricao}` : m.descricao;
    const qtd = `${quantidade(Number(m.quantidade))}${m.unidadeMedida ? ' ' + m.unidadeMedida : ''}`;
    const serie = m.numSerie ? ` · série ${m.numSerie}` : '';
    const origem = m.origem === 'CONCESSIONARIA' ? ' · Concessionária' : '';
    return `${nome} · ${qtd} ${m.operacao === 'APLICADO' ? 'aplicado' : 'retirado'}${serie}${origem}`;
  }

  /** A alteração foi feita no aparelho sem sinal e só chegou ao servidor bem depois. */
  protected chegouDepois(ev: EventoHistoricoNota): boolean {
    return ev.tipo === 'ALTERADA' && new Date(ev.registradoEm).getTime() - new Date(ev.ocorridoEm).getTime() > 5 * 60_000;
  }
}
