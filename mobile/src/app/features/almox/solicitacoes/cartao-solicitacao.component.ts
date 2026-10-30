import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ResumoSolicitacao } from '../../../core/almox/almox.models';
import { dataCivil, defStatus, idCurto, isoCivil, nomeDestino, nomeOrigem, pode, prazoRelativo, saqueAtrasado } from '../../../core/almox/almox-status';
import { IconeComponent } from '../../../ui';

export type AcaoCartao = 'aprovar' | 'rejeitar' | 'entregar';

const MESES = ['JAN', 'FEV', 'MAR', 'ABR', 'MAI', 'JUN', 'JUL', 'AGO', 'SET', 'OUT', 'NOV', 'DEZ'];

/**
 * Uma solicitação comercial em lista, quadro e calendário. À esquerda, o dia do saque como
 * numa agenda (vermelho quando venceu, âmbar quando é hoje); no centro, para onde vai e a
 * situação; embaixo, para quem gere, a próxima ação.
 */
@Component({
  selector: 'app-cartao-solicitacao',
  standalone: true,
  imports: [IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="cartao" [class.compacto]="compacto()">
      <button type="button" class="corpo" (click)="abrir.emit(item())" [attr.aria-label]="rotuloAcessivel()">
        @if (!semData()) {
          <span class="data" [attr.data-prazo]="prazo()" aria-hidden="true">
            @if (item().dataPrevista) {
              <span class="dia ces-num">{{ dia() }}</span><span class="mes">{{ mes() }}</span>
            } @else {
              <ces-icone nome="calendario" [tamanho]="18" /><span class="mes">sem data</span>
            }
          </span>
        }
        <span class="txt">
          <span class="topo"><span class="destino">{{ destino() }}</span><span class="id ces-num">#{{ id() }}</span></span>
          <span class="origem">de {{ origem() }}</span>
          <span class="linha-status">
            <span class="status" [attr.data-tom]="status().tom"><i aria-hidden="true"></i>{{ status().rotulo }}</span>
            <span class="sep" aria-hidden="true">·</span>
            <span class="ces-num">{{ item().totalItens }} {{ item().totalItens === 1 ? 'item' : 'itens' }}</span>
          </span>
          @if (sinais().length) {
            <span class="sinais">
              @for (s of sinais(); track s.texto) {
                <span class="sinal" [attr.data-tom]="s.tom"><ces-icone [nome]="s.icone" [tamanho]="13" />{{ s.texto }}</span>
              }
            </span>
          }
        </span>
      </button>
      @if (acoes() && (podeAprovar() || podeRejeitar() || podeEntregar())) {
        <div class="acoes">
          @if (podeRejeitar()) {
            <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto rejeitar" [disabled]="!!ocupado()" (click)="agir.emit({ acao: 'rejeitar', item: item() })">Rejeitar</button>
          }
          @if (podeAprovar()) {
            <button type="button" class="ces-btn ces-btn--principal ces-btn--compacto acao" [disabled]="!!ocupado()" (click)="agir.emit({ acao: 'aprovar', item: item() })">
              @if (ocupado() === 'aprovar') { <span class="giro" aria-hidden="true"></span>Aprovando… } @else { <ces-icone nome="check" [tamanho]="18" />Aprovar }
            </button>
          } @else if (podeEntregar()) {
            <button type="button" class="ces-btn ces-btn--tonal ces-btn--compacto acao" [disabled]="!!ocupado()" (click)="agir.emit({ acao: 'entregar', item: item() })">
              <ces-icone nome="caminhao" [tamanho]="18" />Registrar entrega
            </button>
          }
        </div>
      }
    </article>
  `,
  styles: [`
    :host { display: block; }
    .cartao { background: var(--ces-superficie); border-radius: var(--ces-r-grupo); overflow: hidden; box-shadow: 0 1px 2px rgba(14, 23, 38, .05); }
    .corpo { display: flex; align-items: flex-start; gap: var(--ces-e3); width: 100%; padding: var(--ces-e3) var(--ces-e3) var(--ces-e3) var(--ces-e3);
      border: 0; background: transparent; color: inherit; text-align: left; font: inherit; cursor: pointer; transition: background-color 100ms; }
    .corpo:active { background: var(--ces-superficie-2); }
    .data { flex: none; width: 44px; height: 48px; border-radius: 12px; display: grid; place-content: center; justify-items: center; gap: 1px;
      background: var(--ces-superficie-2); color: var(--ces-texto); }
    .data .dia { font: 700 1.125rem/1 var(--ces-fonte); letter-spacing: -.02em; }
    .data .mes { font: 700 .625rem/1 var(--ces-fonte); letter-spacing: .06em; color: var(--ces-texto-2); text-transform: uppercase; }
    .data[data-prazo='atrasado'] { background: var(--ces-erro-suave); color: var(--ces-erro); }
    .data[data-prazo='atrasado'] .mes { color: var(--ces-erro); }
    .data[data-prazo='hoje'] { background: var(--ces-atencao-suave); color: var(--ces-atencao); }
    .data[data-prazo='hoje'] .mes { color: var(--ces-atencao); }
    .data[data-prazo='sem'] { background: transparent; box-shadow: inset 0 0 0 1.5px var(--ces-linha); color: var(--ces-texto-3); }
    .data[data-prazo='sem'] .mes { font-size: .5625rem; letter-spacing: .02em; color: var(--ces-texto-3); }
    .data[data-prazo='encerrada'] { opacity: .7; }
    .txt { flex: 1; min-width: 0; display: grid; gap: 2px; padding-top: 1px; }
    .topo { display: flex; align-items: baseline; gap: var(--ces-e2); }
    .destino { flex: 1; min-width: 0; font: 600 .9375rem/1.3 var(--ces-fonte); overflow-wrap: anywhere; }
    .origem { font: var(--ces-t-apoio); color: var(--ces-texto-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .linha-status { display: flex; align-items: center; flex-wrap: wrap; gap: 2px 6px; margin-top: 4px; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .status { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; color: var(--ces-texto); }
    .status i { width: 8px; height: 8px; border-radius: 50%; background: var(--ces-texto-3); }
    .status[data-tom='atencao'] i { background: var(--ces-atencao); }
    .status[data-tom='destaque'] i { background: var(--ces-destaque); }
    .status[data-tom='ok'] i { background: var(--ces-ok); }
    .status[data-tom='erro'] i { background: var(--ces-erro); }
    .sep { color: var(--ces-texto-3); }
    .id { flex: none; font: 500 .6875rem/1 var(--ces-fonte-mono); color: var(--ces-texto-3); }
    .sinais { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
    .sinal { display: inline-flex; align-items: center; gap: 4px; padding: 3px 8px; border-radius: var(--ces-r-pilula); font: 600 .75rem/1.3 var(--ces-fonte);
      background: var(--ces-superficie-2); color: var(--ces-texto-2); }
    .sinal[data-tom='erro'] { background: var(--ces-erro-suave); color: var(--ces-erro); }
    .sinal[data-tom='atencao'] { background: var(--ces-atencao-suave); color: var(--ces-atencao); }
    .acoes { display: flex; justify-content: flex-end; align-items: center; gap: var(--ces-e2); padding: 6px var(--ces-e3); border-top: 1px solid var(--ces-linha); }
    .acoes .acao { min-height: 38px; padding: 0 var(--ces-e4); border-radius: var(--ces-r-pilula); }
    .rejeitar { color: var(--ces-texto-2); }
    .compacto .corpo { padding: var(--ces-e3); }
    .compacto .data { width: 40px; height: 44px; }
    .compacto .data .dia { font-size: 1rem; }
    .giro { width: 14px; height: 14px; border-radius: 50%; border: 2px solid currentColor; border-right-color: transparent; animation: giro .8s linear infinite; }
    @keyframes giro { to { transform: rotate(360deg); } }
  `],
})
export class CartaoSolicitacaoComponent {
  readonly item = input.required<ResumoSolicitacao>();
  /** Mostra os botões de aprovar, rejeitar e entregar (quem pode gerir). */
  readonly acoes = input(false);
  /** Ação em andamento neste cartão. */
  readonly ocupado = input<AcaoCartao | null>(null);
  /** Versão menor (colunas do quadro). */
  readonly compacto = input(false);
  /** Esconde o bloco de data (no calendário o dia já está escolhido). */
  readonly semData = input(false);
  readonly abrir = output<ResumoSolicitacao>();
  readonly agir = output<{ acao: AcaoCartao; item: ResumoSolicitacao }>();

  readonly status = computed(() => defStatus(this.item().status));
  readonly id = computed(() => idCurto(this.item().id));
  readonly origem = computed(() => nomeOrigem(this.item()));
  readonly destino = computed(() => nomeDestino(this.item()));
  readonly atrasada = computed(() => saqueAtrasado(this.item()));
  readonly relativo = computed(() => (this.item().dataPrevista ? prazoRelativo(this.item().dataPrevista!) : ''));
  readonly dia = computed(() => (this.item().dataPrevista ? dataCivil(this.item().dataPrevista!).getDate() : ''));
  readonly mes = computed(() => (this.item().dataPrevista ? MESES[dataCivil(this.item().dataPrevista!).getMonth()] : ''));
  readonly prazo = computed(() => {
    const s = this.item();
    if (!s.dataPrevista) return 'sem';
    if (this.atrasada()) return 'atrasado';
    const aberta = ['PENDENTE', 'APROVADO', 'ENTREGUE_PARCIAL'].includes(s.status) && !s.encerrada;
    if (!aberta) return 'encerrada';
    return s.dataPrevista.substring(0, 10) === isoCivil(new Date()) ? 'hoje' : 'futuro';
  });
  readonly podeAprovar = computed(() => pode(this.item(), 'APROVAR'));
  readonly podeRejeitar = computed(() => pode(this.item(), 'REJEITAR'));
  readonly podeEntregar = computed(() => pode(this.item(), 'ENTREGAR'));

  /** No máximo dois sinais, do mais grave para o menos. */
  readonly sinais = computed(() => {
    const s = this.item();
    const lista: { texto: string; tom: string; icone: 'alerta' | 'relogio' | 'bandeira' | 'info' }[] = [];
    if (this.atrasada()) lista.push({ texto: `Atrasado ${this.relativo()}`, tom: 'erro', icone: 'alerta' });
    else if (this.prazo() === 'hoje') lista.push({ texto: 'Saque hoje', tom: 'atencao', icone: 'relogio' });
    else if (s.foraPrazoSaque) lista.push({ texto: 'Fora das 48h', tom: 'atencao', icone: 'relogio' });
    if (s.possuiPendencia) {
      const m = s.motivosPendencia ?? [];
      lista.push({ texto: m.length === 1 ? m[0] : m.length > 1 ? `${m.length} pendências` : 'Com pendência', tom: 'atencao', icone: 'bandeira' });
    }
    if (s.encerrada && s.status === 'ENTREGUE_PARCIAL') lista.push({ texto: 'Saldo encerrado', tom: 'neutro', icone: 'info' });
    return lista.slice(0, 2);
  });

  readonly rotuloAcessivel = computed(() => {
    const s = this.item();
    return `Solicitação ${this.id()}, ${this.status().rotulo}, para ${this.destino()}, de ${this.origem()}`
      + (s.dataPrevista ? `, saque ${this.relativo()}` : ', sem data de saque') + (this.atrasada() ? ', atrasada' : '') + '. Abrir detalhe.';
  });
}
