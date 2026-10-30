import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { AlmoxService } from '../../../core/almox/almox.service';
import { ItemSolicitacao, Motivo, Solicitacao } from '../../../core/almox/almox.models';
import { defStatus, idCurto, pode, prazoRelativo, saqueAtrasado } from '../../../core/almox/almox-status';
import { ApiError } from '../../../core/http/error.interceptor';
import { SessaoService } from '../../../core/sessao/sessao.service';
import { ConnectivityService } from '../../../core/sync/connectivity.service';
import { ToastService } from '../../../core/ui/toast.service';
import {
  BarraTopoComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, NomeIcone, OpcaoSeletor, QuantidadeComponent, SeletorComponent,
  VazioComponent, dataCurta, hora, numeroBr,
} from '../../../ui';
import { FolhaRejeicaoComponent } from './folha-rejeicao.component';

interface Etapa { rotulo: string; estado: 'feito' | 'atual' | 'futuro' | 'interrompido'; quando?: string | null; quem?: string | null; icone: NomeIcone }
interface ItemEstorno { id: string; descricao: string; codigo: string; unidade: string; entregue: number; quantidade: number; marcado: boolean }

const MINIMO = 5;

/** Detalhe de uma solicitação comercial com as ações de quem aprova e de quem entrega. */
@Component({
  selector: 'app-solicitacao-almox-detalhe',
  standalone: true,
  imports: [BarraTopoComponent, FolhaComponent, FolhaRejeicaoComponent, IconeComponent, IndicadorRedeComponent, QuantidadeComponent, SeletorComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-barra-topo [titulo]="'Solicitação #' + idCurto()" [subtitulo]="det() ? status().rotulo : 'Carregando…'" (voltar)="voltar()">
      <ces-indicador-rede />
      <button type="button" class="ces-btn-icone" (click)="carregar()" [disabled]="carregando()" aria-label="Atualizar"><ces-icone nome="atualizar" /></button>
    </ces-barra-topo>

    @if (carregando() && !det()) {
      <div class="ces-esqueletos" aria-busy="true" aria-label="Carregando a solicitação">
        <div class="ces-esqueleto" style="height: 132px"></div><div class="ces-esqueleto" style="height: 72px"></div><div class="ces-esqueleto" style="height: 220px"></div>
      </div>
    } @else if (erro()) {
      <ces-vazio icone="alerta" titulo="Não foi possível abrir a solicitação" [texto]="erro()">
        <button type="button" class="ces-btn ces-btn--tonal" (click)="carregar()">Tentar de novo</button>
      </ces-vazio>
    } @else if (det(); as d) {
      <div class="pagina" [class.atualizando]="carregando()">
        <section class="hero" [attr.data-tom]="status().tom" aria-label="Resumo">
          <div class="selos">
            <span [class]="'ces-etiqueta ces-etiqueta--' + status().tom"><ces-icone [nome]="status().icone" [tamanho]="14" />{{ status().rotulo }}</span>
            @if (atrasada()) { <span class="ces-etiqueta ces-etiqueta--erro"><ces-icone nome="alerta" [tamanho]="14" />Saque atrasado</span> }
            @if (d.foraPrazoSaque) { <span class="ces-etiqueta ces-etiqueta--atencao"><ces-icone nome="relogio" [tamanho]="14" />Fora das 48h</span> }
            @if (d.possuiPendencia) { <span class="ces-etiqueta ces-etiqueta--atencao"><ces-icone nome="bandeira" [tamanho]="14" />Com pendência</span> }
          </div>
          <ol class="rota" aria-label="Caminho do material">
            <li><span class="marco" aria-hidden="true"></span><span class="ponta"><span class="rotulo">Sai de</span><span class="nome">{{ d.estoqueOrigem.nomeIdentificador }}</span></span></li>
            <li><span class="marco marco--fim" aria-hidden="true"></span><span class="ponta"><span class="rotulo">Vai para</span><strong class="nome">{{ d.estoqueDestino.nomeIdentificador }}</strong></span></li>
          </ol>
          <p class="quem ces-num">Pedida por <strong>{{ nome(d.idRequisitante) }}</strong> em {{ dataHora(d.dataSolicitacao) }}</p>
        </section>

        <ol class="fluxo" aria-label="Andamento">
          @for (e of fluxo(); track e.rotulo) {
            <li [attr.data-estado]="e.estado">
              <span class="marca"><ces-icone [nome]="e.icone" [tamanho]="16" /></span>
              <span class="txt"><strong>{{ e.rotulo }}</strong>
                @if (e.quando) { <span class="ces-num">{{ dataHora(e.quando) }}</span> }
                @if (e.quem) { <span>{{ e.quem }}</span> }
              </span>
            </li>
          }
        </ol>

        <dl class="ces-dados">
          <div><dt>Saque previsto</dt>
            <dd>@if (d.dataPrevista) { <span [class.alerta]="atrasada()">{{ dataCurta(d.dataPrevista.substring(0, 10)) }}</span><span class="apoio"> · {{ relativo() }}</span> } @else { Não agendado }</dd></div>
          <div><dt>Quem retira</dt><dd>{{ d.idColaboradorSaque ? nome(d.idColaboradorSaque) : 'Definido na entrega' }}@if (d.saqueForaEquipe) { <span class="apoio"> · de fora da equipe</span> }</dd></div>
          <div><dt>Atendimento</dt>
            <dd>
              <span class="progresso" role="progressbar" [attr.aria-valuenow]="percentual()" aria-valuemin="0" aria-valuemax="100" [attr.aria-label]="percentual() + '% entregue'"><i [style.width.%]="percentual()"></i></span>
              <span class="apoio ces-num"><strong>{{ num(totalEntregue()) }}</strong> de {{ num(totalPedido()) }} entregues · {{ percentual() }}%</span>
            </dd></div>
        </dl>

        @if (d.encerrada && d.motivoEncerramento && d.status !== 'ENTREGUE') {
          <div class="aviso"><ces-icone nome="info" /><div><strong>Saldo pendente encerrado</strong><p>{{ d.motivoEncerramento }}</p></div></div>
        }
        @if (d.status === 'REJEITADO' && d.motivoRejeicao) {
          <div class="aviso aviso--erro"><ces-icone nome="fechar" /><div><strong>Rejeitada: {{ d.motivoRejeicao.nome }}</strong><p>{{ d.justificativaRejeicao || 'Sem justificativa registrada.' }}</p></div></div>
        }
        @if (d.motivoPendencia) {
          <div class="aviso aviso--atencao"><ces-icone nome="bandeira" />
            <div><strong>Pendência: {{ d.motivoPendencia.nome }}</strong><p>{{ d.justificativaPendencia || 'Sem justificativa informada.' }}</p>
              @if (podePendencia()) { <button type="button" class="ces-btn ces-btn--neutro ces-btn--compacto" (click)="resolver(null)" [disabled]="executando()">Resolver pendência</button> }
            </div></div>
        }
        @if (d.observacao) {
          <blockquote class="obs"><span>Observação de quem pediu</span>{{ d.observacao }}</blockquote>
        }

        <h2 class="ces-secao"><span>Materiais</span><span class="ces-num">{{ d.itens.length }}</span></h2>
        <ul class="ces-grupo" aria-label="Materiais pedidos">
          @for (it of d.itens; track it.id) {
            <li>
              <button type="button" class="ces-linha ces-linha--acao item" (click)="abrirItem(it)" [disabled]="!podePendencia() && !it.motivoPendencia">
                <span class="ces-linha__texto">
                  <span class="ces-linha__titulo">{{ it.material.descricao }}</span>
                  <span class="ces-linha__apoio ces-num">Cód. {{ it.material.codigo }} · {{ it.material.unidadeMedida || 'UN' }}{{ it.material.detalhaSerie ? ' · com nº de série' : '' }}</span>
                  <span class="estado">
                    @if (it.motivoPendencia) { <span class="ces-etiqueta ces-etiqueta--atencao"><ces-icone nome="bandeira" [tamanho]="13" />{{ it.motivoPendencia.nome }}</span> }
                    @else if (entregue(it) >= it.quantidade) { <span class="ces-etiqueta ces-etiqueta--ok"><ces-icone nome="check" [tamanho]="13" />Atendido</span> }
                    @else if (entregue(it) > 0) { <span class="ces-etiqueta ces-etiqueta--atencao">Em parte</span> }
                  </span>
                </span>
                <span class="numeros ces-num" [attr.aria-label]="'Pedido ' + num(it.quantidade) + ', entregue ' + num(entregue(it)) + ', falta ' + num(pendente(it))">
                  <span><small>Pedido</small>{{ num(it.quantidade) }}</span>
                  <span><small>Entregue</small>{{ num(entregue(it)) }}</span>
                  <span [class.falta]="pendente(it) > 0"><small>Falta</small>{{ num(pendente(it)) }}</span>
                </span>
              </button>
            </li>
          }
        </ul>
        @if (podePendencia() && !d.motivoPendencia) {
          <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto acao-secao" (click)="abrirPendencia(null)"><ces-icone nome="bandeira" [tamanho]="16" />Apontar pendência geral</button>
        }
        @if (podeGerir() && podeEstornar() && podeEntregar()) {
          <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto acao-secao" (click)="abrirEstorno()"><ces-icone nome="desfazer" [tamanho]="16" />Estornar o que já foi entregue</button>
        }

        @if (d.operacoes?.length) {
          <h2 class="ces-secao"><span>Histórico de entregas</span><span class="ces-num">{{ d.operacoes!.length }}</span></h2>
          <ul class="ces-grupo historico">
            @for (op of operacoes(); track op.id) {
              <li class="ces-linha op">
                <span class="ces-selo" [class.ces-selo--ok]="op.tipo === 'ENTREGA'"><ces-icone [nome]="op.tipo === 'ENTREGA' ? 'caminhao' : 'desfazer'" /></span>
                <span class="ces-linha__texto">
                  <span class="ces-linha__titulo">{{ op.tipo === 'ENTREGA' ? 'Entrega' : 'Estorno' }} · <span class="ces-num">{{ dataHora(op.realizadaEm) }}</span></span>
                  <span class="ces-linha__apoio">Por {{ nome(op.idUsuarioOperador) }}{{ op.idColaboradorSaque ? ' · retirou ' + nome(op.idColaboradorSaque) : '' }}</span>
                  <ul class="op-itens">
                    @for (i of op.itens; track i.id) {
                      <li class="ces-num">{{ num(i.quantidade) }} × {{ i.descricaoMaterial }}@if (i.numeroSerie) { <span class="serie">{{ i.numeroSerie }}</span> }</li>
                    }
                  </ul>
                </span>
              </li>
            }
          </ul>
        }
        <div class="respiro"></div>
      </div>

      @if (podeGerir() && (podeAprovar() || podeRejeitar() || podeEntregar() || podeEstornar())) {
        <footer class="ces-rodape-acoes">
          @if (podeEstornar() && !podeEntregar()) {
            <button type="button" class="ces-btn ces-btn--neutro" (click)="abrirEstorno()"><ces-icone nome="desfazer" [tamanho]="18" />Estornar</button>
          }
          @if (podeRejeitar()) { <button type="button" class="ces-btn ces-btn--neutro" (click)="rejeitando.set(d.id)" [disabled]="executando()">Rejeitar</button> }
          @if (podeAprovar()) {
            <button type="button" class="ces-btn ces-btn--principal principal" (click)="aprovar()" [disabled]="executando()">
              <ces-icone nome="check" [tamanho]="18" />{{ executando() ? 'Aprovando…' : 'Aprovar' }}</button>
          } @else if (podeEntregar()) {
            <button type="button" class="ces-btn ces-btn--principal principal" (click)="entregar()"><ces-icone nome="caminhao" [tamanho]="18" />Registrar entrega</button>
          }
        </footer>
      }
    }

    <app-folha-rejeicao [idSolicitacao]="rejeitando()" (fechar)="rejeitando.set(null)" (rejeitada)="rejeitando.set(null); carregar()" />

    <!-- Item: apontar, ver ou resolver pendência -->
    <ces-folha [aberta]="!!itemAberto() || pendenciaGeral()" (fechar)="fecharPendencia()" [titulo]="tituloPendencia()" [descricao]="descricaoPendencia()">
      @if (pendenciaAtual(); as p) {
        <div class="aviso aviso--atencao dentro"><ces-icone nome="bandeira" /><div><strong>{{ p.motivo.nome }}</strong><p>{{ p.justificativa || 'Sem justificativa.' }}</p></div></div>
      }
      @if (podePendencia()) {
        <div class="campos">
          <ces-seletor [rotulo]="pendenciaAtual() ? 'Trocar o motivo' : 'Motivo da pendência'" [obrigatorio]="true" [opcoes]="opcoesPendencia()" [valor]="motivoPendencia()"
            (valorChange)="motivoPendencia.set($event)" [invalido]="tentouPendencia() && !motivoPendencia()" />
          <label class="ces-campo">
            <span class="ces-campo__rotulo">Justificativa *</span>
            <textarea class="ces-input texto" rows="3" [value]="justificativaPendencia()" (input)="justificativaPendencia.set($any($event.target).value)"
              [attr.aria-invalid]="tentouPendencia() && justificativaPendencia().trim().length < minimo" placeholder="O que impede o atendimento"></textarea>
            @if (tentouPendencia() && justificativaPendencia().trim().length < minimo) { <span class="ces-campo__erro"><ces-icone nome="alerta" [tamanho]="16" />Escreva ao menos {{ minimo }} caracteres.</span> }
          </label>
        </div>
      }
      @if (podePendencia()) {
        <div rodape class="ces-folha-rodape">
          @if (pendenciaAtual()) {
            <button type="button" class="ces-btn ces-btn--neutro" (click)="resolver(itemAberto()?.id ?? null)" [disabled]="executando()">Resolver</button>
          } @else {
            <button type="button" class="ces-btn ces-btn--neutro" (click)="fecharPendencia()">Cancelar</button>
          }
          <button type="button" class="ces-btn ces-btn--principal" (click)="salvarPendencia()" [disabled]="executando()">{{ executando() ? 'Salvando…' : pendenciaAtual() ? 'Atualizar' : 'Apontar' }}</button>
        </div>
      }
    </ces-folha>

    <!-- Estorno parcial -->
    <ces-folha [aberta]="estornoAberto()" (fechar)="estornoAberto.set(false)" titulo="Estornar materiais" descricao="Os itens marcados voltam para o estoque de origem. Não dá para desfazer.">
      <ul class="ces-grupo lista-estorno">
        @for (i of estorno(); track i.id; let idx = $index) {
          <li class="ces-linha estorno-linha">
            <button type="button" role="checkbox" class="caixa" [attr.aria-checked]="i.marcado" (click)="marcarEstorno(idx)" [attr.aria-label]="'Estornar ' + i.descricao">
              <ces-icone nome="check" [tamanho]="14" /></button>
            <span class="ces-linha__texto"><span class="ces-linha__titulo">{{ i.descricao }}</span><span class="ces-linha__apoio ces-num">Entregue {{ num(i.entregue) }} {{ i.unidade }}</span></span>
            @if (i.marcado) {
              <ces-quantidade [valor]="i.quantidade" (valorChange)="qtdEstorno(idx, $event)" [minimo]="0.001" [unidade]="i.unidade" [rotulo]="'quantidade a estornar de ' + i.descricao" />
            }
          </li>
        }
      </ul>
      <div rodape class="ces-folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="estornoAberto.set(false)">Voltar</button>
        <button type="button" class="ces-btn ces-btn--perigo" (click)="confirmarEstorno()" [disabled]="executando() || !marcadosEstorno()">
          {{ executando() ? 'Estornando…' : confirmandoEstorno() ? 'Toque de novo para confirmar' : 'Estornar ' + marcadosEstorno() + (marcadosEstorno() === 1 ? ' item' : ' itens') }}</button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100%; background: var(--ces-fundo); }
    :host > * { flex-shrink: 0; }
    .pagina { transition: opacity var(--ces-m-entrar); }
    .pagina.atualizando { opacity: .6; }
    .hero { margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e4); border-radius: var(--ces-r-folha); background: var(--ces-superficie); display: grid; gap: var(--ces-e3); }
    .selos { display: flex; flex-wrap: wrap; gap: 6px; }
    .rota { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--ces-e3); }
    .rota li { position: relative; display: flex; gap: var(--ces-e3); align-items: flex-start; }
    .rota li:first-child::after { content: ''; position: absolute; left: 4px; top: 14px; bottom: calc(-1 * var(--ces-e3) - 2px); width: 2px; background: var(--ces-linha-forte); }
    .marco { width: 10px; height: 10px; margin-top: 4px; border-radius: 50%; box-shadow: inset 0 0 0 2px var(--ces-texto-3); flex: none; background: var(--ces-superficie); position: relative; z-index: 1; }
    .marco--fim { background: var(--ces-destaque); box-shadow: none; }
    .ponta { display: grid; gap: 1px; min-width: 0; }
    .ponta .rotulo { font: 500 .75rem/1.2 var(--ces-fonte); color: var(--ces-texto-2); }
    .ponta .nome { font: 500 .9375rem/1.3 var(--ces-fonte); overflow-wrap: break-word; word-break: break-word; }
    .ponta strong.nome { font-weight: 600; }
    .quem { margin: 0; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .quem strong { color: var(--ces-texto); font-weight: 600; }
    .fluxo { list-style: none; margin: var(--ces-e3) var(--ces-e3) 0; padding: 0; display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; gap: var(--ces-e2); }
    .fluxo li { position: relative; display: grid; justify-items: center; align-content: start; text-align: center; gap: 6px; }
    .fluxo li + li::before { content: ''; position: absolute; top: 15px; right: calc(50% + 20px); width: calc(100% - 32px); height: 2px; background: var(--ces-linha-forte); }
    .fluxo li[data-estado='feito'] + li::before, .fluxo li[data-estado='feito'] + li[data-estado='atual']::before { background: var(--ces-ok); }
    .marca { width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; background: var(--ces-superficie-2); color: var(--ces-texto-3); }
    [data-estado='feito'] .marca { background: var(--ces-ok-suave); color: var(--ces-ok); }
    [data-estado='atual'] .marca { background: var(--ces-destaque); color: var(--ces-destaque-texto); }
    [data-estado='interrompido'] .marca { background: var(--ces-erro-suave); color: var(--ces-erro); }
    .fluxo .txt { display: grid; gap: 1px; font: var(--ces-t-apoio); font-size: .75rem; color: var(--ces-texto-2); }
    .fluxo strong { font: 600 .8125rem/1.25 var(--ces-fonte); color: var(--ces-texto); }
    .ces-dados .alerta { color: var(--ces-erro); }
    .apoio { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .progresso { display: block; height: 8px; margin: 6px 0; border-radius: var(--ces-r-pilula); background: var(--ces-linha); overflow: hidden; }
    .progresso i { display: block; height: 100%; border-radius: inherit; background: var(--ces-ok); transition: width var(--ces-m-folha) var(--ces-curva); }
    .aviso { display: flex; gap: var(--ces-e3); margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e3) var(--ces-e4); border-radius: var(--ces-r-grupo);
      background: var(--ces-superficie-2); }
    .aviso > ces-icone { margin-top: 1px; }
    .aviso div { display: grid; gap: 4px; justify-items: start; min-width: 0; }
    .aviso strong { font: var(--ces-t-corpo-forte); }
    .aviso p { margin: 0; font: var(--ces-t-apoio); font-size: .875rem; overflow-wrap: anywhere; }
    .aviso--erro { background: var(--ces-erro-suave); } .aviso--erro > ces-icone { color: var(--ces-erro); }
    .aviso--atencao { background: var(--ces-atencao-suave); } .aviso--atencao > ces-icone { color: var(--ces-atencao); }
    .aviso.dentro { margin: 0 var(--ces-e4) var(--ces-e3); }
    .obs { margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e3) var(--ces-e4); border-left: 3px solid var(--ces-linha-forte); border-radius: 0 var(--ces-r-grupo) var(--ces-r-grupo) 0;
      background: var(--ces-superficie); font: var(--ces-t-corpo); display: grid; gap: 2px; }
    .obs span { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .item { align-items: flex-start; }
    .item:disabled { opacity: 1; cursor: default; }
    .estado { display: flex; gap: 4px; margin-top: 4px; }
    .estado:empty { display: none; }
    .numeros { display: grid; grid-template-columns: repeat(3, auto); gap: var(--ces-e3); text-align: right; font: 700 1rem/1.1 var(--ces-fonte); flex: none; }
    .numeros span { display: grid; gap: 2px; }
    .numeros small { font: 500 .6875rem/1 var(--ces-fonte); color: var(--ces-texto-2); }
    .numeros .falta { color: var(--ces-atencao); }
    .acao-secao { margin: var(--ces-e2) var(--ces-e3) 0; gap: 6px; }
    .op { align-items: flex-start; }
    .op-itens { list-style: none; margin: 6px 0 0; padding: 0; display: grid; gap: 2px; font: var(--ces-t-apoio); color: var(--ces-texto); }
    .serie { margin-left: 6px; padding: 1px 6px; border-radius: 6px; background: var(--ces-superficie-2); font: 600 .75rem/1.4 var(--ces-fonte-mono); }
    .respiro { height: 112px; }
    .ces-rodape-acoes .principal { flex: 1.6; }
    .campos { display: grid; gap: var(--ces-e3); padding: 0 var(--ces-e3) var(--ces-e2); }
    .campos ces-seletor { box-shadow: inset 0 0 0 1px var(--ces-linha); border-radius: var(--ces-r-grupo); }
    .texto { min-height: 96px; padding: var(--ces-e3) var(--ces-e4); resize: vertical; }
    .lista-estorno { background: var(--ces-fundo); }
    .estorno-linha { flex-wrap: wrap; }
    .caixa { width: 44px; height: 44px; margin: -10px -6px -10px -10px; border: 0; background: transparent; display: grid; place-items: center; cursor: pointer; color: transparent; }
    .caixa ces-icone { width: 22px; height: 22px; border-radius: 6px; border: 2px solid var(--ces-linha-forte); display: grid; place-items: center; box-sizing: border-box; }
    .caixa[aria-checked='true'] { color: var(--ces-destaque-texto); }
    .caixa[aria-checked='true'] ces-icone { background: var(--ces-destaque); border-color: var(--ces-destaque); }
    .campos .ces-campo__rotulo, .campos .ces-campo__ajuda, .campos .ces-campo__erro { padding-left: var(--ces-e4); }
  `],
})
export class SolicitacaoAlmoxDetalheComponent {
  private readonly almox = inject(AlmoxService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly sessao = inject(SessaoService);
  private readonly rede = inject(ConnectivityService);
  readonly id = signal(inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '');

  readonly minimo = MINIMO;
  readonly det = signal<Solicitacao | null>(null);
  readonly carregando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly executando = signal(false);
  readonly rejeitando = signal<string | null>(null);

  readonly itemAberto = signal<ItemSolicitacao | null>(null);
  readonly pendenciaGeral = signal(false);
  readonly motivos = signal<Motivo[]>([]);
  readonly motivoPendencia = signal<string | null>(null);
  readonly justificativaPendencia = signal('');
  readonly tentouPendencia = signal(false);

  readonly estornoAberto = signal(false);
  readonly estorno = signal<ItemEstorno[]>([]);
  readonly confirmandoEstorno = signal(false);

  readonly idCurto = computed(() => idCurto(this.id()));
  readonly status = computed(() => defStatus(this.det()?.status));
  readonly atrasada = computed(() => saqueAtrasado(this.det()));
  readonly relativo = computed(() => (this.det()?.dataPrevista ? prazoRelativo(this.det()!.dataPrevista!) : ''));
  readonly podeGerir = computed(() => this.auth.podeGerirSolicitacoesAlmox() && this.rede.isOnline());
  readonly podeAprovar = computed(() => pode(this.det(), 'APROVAR'));
  readonly podeRejeitar = computed(() => pode(this.det(), 'REJEITAR'));
  readonly podeEntregar = computed(() => pode(this.det(), 'ENTREGAR'));
  readonly podeEstornar = computed(() => {
    const d = this.det();
    return !!d && (d.status === 'ENTREGUE' || d.status === 'ENTREGUE_PARCIAL') && d.itens.some(i => this.entregue(i) > 0);
  });
  readonly podePendencia = computed(() => {
    const d = this.det();
    return this.podeGerir() && !!d && !d.encerrada && ['PENDENTE', 'APROVADO', 'ENTREGUE_PARCIAL'].includes(d.status);
  });
  readonly totalPedido = computed(() => (this.det()?.itens ?? []).reduce((a, i) => a + (Number(i.quantidade) || 0), 0));
  readonly totalEntregue = computed(() => (this.det()?.itens ?? []).reduce((a, i) => a + this.entregue(i), 0));
  readonly percentual = computed(() => (this.totalPedido() > 0 ? Math.min(100, Math.round((this.totalEntregue() / this.totalPedido()) * 100)) : 0));
  readonly operacoes = computed(() => [...(this.det()?.operacoes ?? [])].sort((a, b) => b.realizadaEm.localeCompare(a.realizadaEm)));
  readonly opcoesPendencia = computed<OpcaoSeletor[]>(() => this.motivos().map(m => ({ valor: m.id, rotulo: m.nome, apoio: m.descricao })));
  readonly marcadosEstorno = computed(() => this.estorno().filter(i => i.marcado && i.quantidade > 0).length);

  readonly pendenciaAtual = computed(() => {
    const it = this.itemAberto();
    const d = this.det();
    if (it?.motivoPendencia) return { motivo: it.motivoPendencia, justificativa: it.justificativaPendencia };
    if (this.pendenciaGeral() && d?.motivoPendencia) return { motivo: d.motivoPendencia, justificativa: d.justificativaPendencia };
    return null;
  });
  readonly tituloPendencia = computed(() => this.itemAberto()?.material.descricao ?? 'Pendência geral');
  readonly descricaoPendencia = computed(() => {
    const it = this.itemAberto();
    if (it) return `Cód. ${it.material.codigo} · falta ${numeroBr(this.pendente(it))} ${it.material.unidadeMedida || 'UN'}`;
    return 'Vale para a solicitação inteira. Quem pediu vê o motivo.';
  });

  readonly fluxo = computed<Etapa[]>(() => {
    const d = this.det();
    if (!d) return [];
    const rejeitada = d.status === 'REJEITADO';
    const aprovada = !!d.dataVerificacao && !rejeitada;
    const total = d.status === 'ENTREGUE';
    const iniciada = total || d.status === 'ENTREGUE_PARCIAL' || d.status === 'ESTORNADO';
    const etapas: Etapa[] = [
      { rotulo: 'Pedida', estado: 'feito', quando: d.dataSolicitacao, icone: 'documento' },
      rejeitada ? { rotulo: 'Rejeitada', estado: 'interrompido', quando: d.dataVerificacao, quem: d.idAprovador ? this.nome(d.idAprovador) : null, icone: 'fechar' }
        : aprovada ? { rotulo: 'Aprovada', estado: 'feito', quando: d.dataVerificacao, quem: d.idAprovador ? this.nome(d.idAprovador) : null, icone: 'check' }
          : { rotulo: 'Aprovação', estado: 'atual', icone: 'relogio' },
    ];
    if (!rejeitada) {
      etapas.push({
        rotulo: total ? 'Entregue' : d.status === 'ESTORNADO' ? 'Estornada' : iniciada ? 'Em parte' : 'Retirada',
        estado: total || d.status === 'ESTORNADO' ? 'feito' : aprovada ? 'atual' : 'futuro',
        quando: d.dataEntrega, icone: d.status === 'ESTORNADO' ? 'desfazer' : 'caminhao',
      });
    }
    return etapas;
  });

  constructor() {
    this.almox.carregarNomes();
    queueMicrotask(() => this.carregar());
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.almox.obterSolicitacao(this.id()).subscribe({
      next: d => { this.det.set(d); this.carregando.set(false); },
      error: (e: ApiError) => {
        this.carregando.set(false);
        this.erro.set(e.status === 404 ? 'Esta solicitação não existe mais.' : e.status === 0 ? 'Sem conexão com o servidor.' : e.detail || 'Tente de novo.');
      },
    });
  }

  voltar(): void {
    const anterior = this.sessao.rotaAnterior();
    void this.router.navigateByUrl(anterior?.startsWith('/almox') && !anterior.includes('/entrega') ? anterior : '/almox/solicitacoes');
  }

  nome(id?: string | null): string {
    return this.almox.nome(id);
  }

  num(n: number): string {
    return numeroBr(n);
  }

  dataHora(iso: string): string {
    return `${dataCurta(iso)} às ${hora(iso)}`;
  }

  protected readonly dataCurta = dataCurta;

  entregue(i: ItemSolicitacao): number {
    return Number(i.quantidadeEntregaLiquida ?? i.quantidadeEntregue ?? 0);
  }

  pendente(i: ItemSolicitacao): number {
    return Number(i.quantidadePendente ?? Math.max(0, i.quantidade - this.entregue(i)));
  }

  aprovar(): void {
    const d = this.det();
    if (!d || this.executando()) return;
    this.executando.set(true);
    this.almox.aprovar(d.id).subscribe({
      next: () => { this.executando.set(false); this.toast.success(`Solicitação #${this.idCurto()} aprovada. Agora aguarda a retirada.`); this.carregar(); },
      error: (e: ApiError) => { this.executando.set(false); this.falha(e, 'Não foi possível aprovar.'); },
    });
  }

  entregar(): void {
    void this.router.navigate(['/almox/solicitacoes', this.id(), 'entrega']);
  }

  // ---------- pendências ----------
  abrirItem(it: ItemSolicitacao): void {
    this.abrirPendencia(it);
  }

  abrirPendencia(it: ItemSolicitacao | null): void {
    this.itemAberto.set(it);
    this.pendenciaGeral.set(!it);
    const atual = it ? it.motivoPendencia : this.det()?.motivoPendencia;
    this.motivoPendencia.set(atual?.id ?? null);
    this.justificativaPendencia.set((it ? it.justificativaPendencia : this.det()?.justificativaPendencia) ?? '');
    this.tentouPendencia.set(false);
    if (!this.motivos().length) this.almox.motivosPendencia().subscribe(m => this.motivos.set(m));
  }

  fecharPendencia(): void {
    this.itemAberto.set(null);
    this.pendenciaGeral.set(false);
  }

  salvarPendencia(): void {
    const d = this.det();
    this.tentouPendencia.set(true);
    if (!d || !this.motivoPendencia() || this.justificativaPendencia().trim().length < MINIMO || this.executando()) return;
    this.executando.set(true);
    this.almox.registrarPendencia(d.id, {
      idMotivoPendencia: this.motivoPendencia()!, justificativa: this.justificativaPendencia().trim(), idItem: this.itemAberto()?.id ?? null,
    }).subscribe({
      next: () => { this.executando.set(false); this.fecharPendencia(); this.toast.success('Pendência registrada.'); this.carregar(); },
      error: (e: ApiError) => { this.executando.set(false); this.falha(e, 'Não foi possível registrar a pendência.'); },
    });
  }

  resolver(idItem: string | null): void {
    const d = this.det();
    if (!d || this.executando()) return;
    this.executando.set(true);
    this.almox.resolverPendencia(d.id, idItem).subscribe({
      next: () => { this.executando.set(false); this.fecharPendencia(); this.toast.success('Pendência resolvida.'); this.carregar(); },
      error: (e: ApiError) => { this.executando.set(false); this.falha(e, 'Não foi possível resolver a pendência.'); },
    });
  }

  // ---------- estorno ----------
  abrirEstorno(): void {
    const d = this.det();
    if (!d) return;
    this.estorno.set(d.itens.filter(i => this.entregue(i) > 0).map(i => ({
      id: i.id, descricao: i.material.descricao, codigo: i.material.codigo, unidade: i.material.unidadeMedida || 'UN',
      entregue: this.entregue(i), quantidade: this.entregue(i), marcado: true,
    })));
    this.confirmandoEstorno.set(false);
    this.estornoAberto.set(true);
  }

  marcarEstorno(idx: number): void {
    this.estorno.update(l => l.map((i, k) => (k === idx ? { ...i, marcado: !i.marcado } : i)));
    this.confirmandoEstorno.set(false);
  }

  qtdEstorno(idx: number, q: number): void {
    const item = this.estorno()[idx];
    if (q > item.entregue) { this.toast.warning(`No máximo ${numeroBr(item.entregue)} ${item.unidade} (o que foi entregue).`); return; }
    this.estorno.update(l => l.map((i, k) => (k === idx ? { ...i, quantidade: q } : i)));
    this.confirmandoEstorno.set(false);
  }

  confirmarEstorno(): void {
    const d = this.det();
    if (!d || this.executando()) return;
    if (!this.confirmandoEstorno()) { this.confirmandoEstorno.set(true); return; }
    const itens = this.estorno().filter(i => i.marcado && i.quantidade > 0).map(i => ({ idItemSolicitacao: i.id, quantidade: i.quantidade }));
    this.executando.set(true);
    this.almox.estornarParcial(d.id, { itens }).subscribe({
      next: () => { this.executando.set(false); this.estornoAberto.set(false); this.toast.success('Estorno feito. O material voltou para a origem.'); this.carregar(); },
      error: (e: ApiError) => { this.executando.set(false); this.confirmandoEstorno.set(false); this.falha(e, 'Não foi possível estornar.'); },
    });
  }

  private falha(e: ApiError, padrao: string): void {
    if (e.status === 409) {
      this.toast.warning('Outra pessoa alterou esta solicitação. Os dados foram atualizados.');
      this.carregar();
    } else {
      this.toast.error(e.detail || padrao);
    }
  }
}
