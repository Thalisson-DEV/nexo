import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';

import {
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesDrawerComponent,
  CesIconButtonComponent,
  CesNumberInputComponent,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTabItem,
  CesTabPanelDirective,
  CesTabsComponent,
  NotificationService
} from '@ces/ui';

import { ConsumoAnteriorItem, EntregaSolicitacaoRequest, ItemSolicitacaoResponse, MotivoPendenciaResponse, SolicitacaoResponse } from '../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { RhService } from '../../../core/services/rh.service';
import { UsuarioLookupService } from '../../../core/services/usuario-lookup.service';
import { prazoRelativo, saqueAtrasado, statusComercial } from './solicitacao-comercial-status';
import { navegarSaindoDoDrawer } from '../../../shared/utils/drawer-navegacao.utils';
import { montarItensEntrega } from './series-selecao/entrega-series.util';
import { SeriesSelecaoComponent } from './series-selecao/series-selecao.component';
import { ConsumoAnteriorLinhaComponent } from './solicitacao-consumo-anterior/solicitacao-consumo-anterior.component';
import { ConsumoAnteriorOperacoesComponent } from './solicitacao-consumo-anterior/solicitacao-consumo-operacoes.component';
import { SolicitacaoOperacoesHistoricoComponent } from './solicitacao-operacoes-historico/solicitacao-operacoes-historico.component';
import { SolicitacaoPendenciaModalComponent } from './solicitacao-pendencia-modal.component';
import { SolicitacaoRejeicaoDialogComponent } from './solicitacao-rejeicao-dialog.component';
import { SolicitacaoTecnicaAssinaturaComponent } from './solicitacao-tecnica-assinatura/solicitacao-tecnica-assinatura.component';

const ehImagem = (valor: string) => valor.startsWith('data:image/');

export type AbaDetalheComercial = 'itens' | 'entrega' | 'historico';

export interface ItemEntregaDraft {
  idItemSolicitacao: string;
  codigo: string;
  descricao: string;
  unidade: string;
  detalhaSerie: boolean;
  quantidadeSolicitada: number;
  jaEntregue: number;
  quantidadePendente: number;
  quantidadeAEntregar: number | null;
  /** Séries que saem nesta entrega (material com série); começa com as reservadas. */
  series: string[];
  idMaterial: string;
}

interface EtapaFluxo {
  rotulo: string;
  estado: 'feito' | 'atual' | 'futuro' | 'interrompido';
  quando?: string | null;
  quem?: string | null;
  icone: string;
}

/**
 * Detalhe rápido de uma solicitação comercial, aberto a partir do quadro, da tabela ou do
 * calendário: fluxo, prazo, itens e as ações de quem aprova e de quem entrega, sem sair da tela.
 */
@Component({
  selector: 'app-solicitacao-comercial-detalhe',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    NgTemplateOutlet,
    CesBooleanControlComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesDrawerComponent,
    CesIconButtonComponent,
    CesNumberInputComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTabsComponent,
    CesTabPanelDirective,
    ConsumoAnteriorLinhaComponent,
    ConsumoAnteriorOperacoesComponent,
    SeriesSelecaoComponent,
    SolicitacaoOperacoesHistoricoComponent,
    SolicitacaoPendenciaModalComponent,
    SolicitacaoRejeicaoDialogComponent,
    SolicitacaoTecnicaAssinaturaComponent
  ],
  styleUrl: './solicitacao-comercial-detalhe.component.css',
  template: `
    <ces-drawer
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      eyebrow="Solicitação comercial"
      [title]="detalhe()?.estoqueDestino?.nomeIdentificador ?? 'Carregando…'"
      size="lg"
      (closed)="aoFechar()"
    >
      @if (carregando() && !detalhe()) {
        <div class="dd-skeleton" role="status" aria-label="Carregando a solicitação">
          <span></span><span></span><span></span><span></span>
        </div>
      } @else if (erro()) {
        <ces-callout tone="danger" title="Não foi possível carregar a solicitação">{{ erro() }}</ces-callout>
      } @else if (detalhe(); as det) {
        <div class="dd">
          <section class="dd-hero" [attr.data-tone]="status().tone" aria-label="Resumo">
            <div class="dd-hero__topo">
              <ces-status [tone]="status().tone" [icon]="'pi ' + status().icon" emphasis="solid">{{ status().label }}</ces-status>
              @if (atrasada()) {
                <ces-status tone="danger" icon="pi pi-exclamation-triangle">Saque atrasado</ces-status>
              }
              @if (det.foraPrazoSaque) {
                <ces-status tone="warning" icon="pi pi-clock">Fora do prazo de 48h</ces-status>
              }
              @if (det.possuiPendencia) {
                <ces-status tone="warning" icon="pi pi-flag">Com pendência</ces-status>
              }
            </div>
            <p class="dd-hero__rota">
              <i class="pi pi-building" aria-hidden="true"></i>
              <span>{{ det.estoqueOrigem.nomeIdentificador }}</span>
              <i class="pi pi-arrow-right dd-hero__seta" aria-hidden="true"></i>
              <strong>{{ det.estoqueDestino.nomeIdentificador }}</strong>
              @if (det.estoqueDestino.tipoEstoque.nome) {
                <span class="dd-hero__tipo">{{ det.estoqueDestino.tipoEstoque.nome }}</span>
              }
            </p>
            <p class="dd-hero__meta"><span class="dd-hero__id">#{{ idCurto() }}</span> · Pedida por <strong>{{ nome(det.idRequisitante) }}</strong> em {{ det.dataSolicitacao | date: "dd/MM/yyyy 'às' HH:mm" }}</p>
          </section>

          @if (aba() !== 'entrega') {
          <ol class="dd-fluxo" aria-label="Andamento">
            @for (etapa of fluxo(); track etapa.rotulo) {
              <li class="dd-fluxo__etapa" [attr.data-estado]="etapa.estado">
                <span class="dd-fluxo__marca"><i [class]="'pi ' + etapa.icone" aria-hidden="true"></i></span>
                <span class="dd-fluxo__texto">
                  <strong>{{ etapa.rotulo }}</strong>
                  @if (etapa.quando) { <span>{{ etapa.quando | date: 'dd/MM/yy HH:mm' }}</span> }
                  @if (etapa.quem) { <span class="dd-muted">{{ etapa.quem }}</span> }
                </span>
              </li>
            }
          </ol>

          <section class="dd-fatos" aria-label="Prazo e atendimento">
            <div class="dd-fato">
              <span class="dd-label">Saque previsto</span>
              @if (det.dataPrevista) {
                <strong [class.dd-texto-alerta]="atrasada()">{{ det.dataPrevista | date: 'dd/MM/yyyy' }}</strong>
                <span class="dd-muted">{{ relativo() }}</span>
              } @else {
                <strong>Não agendado</strong>
              }
            </div>
            <div class="dd-fato">
              <span class="dd-label">Responsável pelo saque</span>
              <strong>{{ det.idColaboradorSaque ? nome(det.idColaboradorSaque) : 'Definido na entrega' }}</strong>
              @if (det.saqueForaEquipe) { <span class="dd-muted">Fora da equipe de destino</span> }
            </div>
            <div class="dd-fato dd-fato--largo">
              <span class="dd-label">Atendimento</span>
              <div class="dd-progresso" role="progressbar" [attr.aria-valuenow]="percentual()" aria-valuemin="0" aria-valuemax="100" [attr.aria-label]="percentual() + '% entregue'">
                <span [style.width.%]="percentual()"></span>
              </div>
              <span class="dd-muted">
                <strong class="dd-forte">{{ totalEntregue() | number: '1.0-3' }}</strong> de {{ totalSolicitado() | number: '1.0-3' }} entregues · {{ percentual() }}%
              </span>
            </div>
          </section>
          }

          @if (det.encerrada && det.motivoEncerramento && det.status !== 'ENTREGUE') {
            <ces-callout tone="info" title="Saldo pendente encerrado">{{ det.motivoEncerramento }}</ces-callout>
          }
          @if (det.status === 'REJEITADO' && det.motivoRejeicao) {
            <ces-callout tone="danger" [title]="'Rejeitada: ' + det.motivoRejeicao.nome">{{ det.justificativaRejeicao || 'Sem justificativa registrada.' }}</ces-callout>
          }
          @if (det.motivoPendencia) {
            <ces-callout tone="warning" [title]="'Pendência: ' + det.motivoPendencia.nome">
              <div class="dd-callout-linha">
                <span>{{ det.justificativaPendencia || 'Sem justificativa informada.' }}</span>
                @if (podeRegistrarPendencia()) {
                  <ces-button label="Resolver pendência" icon="pi pi-check" variant="outline" size="sm" [loading]="executando()" (pressed)="resolverPendenciaGeral()" />
                }
              </div>
            </ces-callout>
          }
          @if (det.observacao) {
            <blockquote class="dd-obs"><span class="dd-label">Observação do requisitante</span>{{ det.observacao }}</blockquote>
          }

          @if (abas().length > 1) {
            <ces-tabs [(value)]="aba" [tabs]="abas()">
              <ng-template cesTabPanel="itens"><ng-container *ngTemplateOutlet="materiais" /></ng-template>

              @if (podeEntregar()) {
                <ng-template cesTabPanel="entrega">
                  <div class="dd-entrega">
                    <div class="dd-entrega__quem">
                      <ces-select
                        label="Sai de"
                        [options]="opcoesOrigem()"
                        [value]="origemEntrega().id"
                        (valueChange)="trocarOrigem($event)"
                        (opened)="carregarOrigens()"
                        [loading]="carregandoOrigens()"
                        [filter]="true"
                        [disabled]="!podeTrocarOrigem()"
                        [description]="podeTrocarOrigem() ? 'Almoxarifado de onde o material sai nesta entrega.' : 'Já saiu material daqui: a origem não muda mais, porque o estorno volta para ela.'"
                      />
                      @if (origemTrocada()) {
                        <ces-callout tone="warning" [title]="'Sai de ' + origemEntrega().nome + ' em vez de ' + det.estoqueOrigem.nomeIdentificador">
                          A solicitação passa a ser atendida por {{ origemEntrega().nome }}.@if (temSeriesReservadas()) { As séries definidas na origem anterior não valem aqui: escolha as séries de novo. }
                        </ces-callout>
                      }
                      <ces-boolean-control kind="toggle" label="Retirada por outra equipe"
                      description="Ligue quando quem retira não é da equipe de destino." [value]="saqueForaEquipe()" (valueChange)="alternarForaEquipe($event)" />
                      <div class="dd-grid-2">
                        <ces-select
                          label="Quem retira"
                          placeholder="Selecione"
                          [options]="saqueForaEquipe() ? colaboradoresGerais() : membrosEquipe()"
                          [(value)]="responsavelId"
                          [loading]="carregandoColaboradores()"
                          [filter]="true"
                          [required]="true"
                          [error]="tentouEntregar() && !responsavelId() ? 'Escolha quem retira o material.' : undefined"
                          [description]="!saqueForaEquipe() && !membrosEquipe().length && !carregandoColaboradores() ? 'A equipe de destino não tem membros ativos. Ligue a retirada por outra equipe.' : saqueForaEquipe() ? 'Qualquer colaborador ativo.' : 'Membros ativos da equipe de destino.'"
                        />
                        <ces-select label="Assinatura" [options]="opcoesAssinatura" [value]="assinatura()" (valueChange)="trocarAssinatura($event)" [required]="true" />
                      </div>
                      @if (assinatura() === 'DIGITAL') {
                        <div class="dd-assinaturas">
                          <app-solicitacao-tecnica-assinatura label="Almoxarifado (quem entrega)" [value]="assinaturaAlmox()" (valueChanged)="assinaturaAlmox.set($event)" />
                          <app-solicitacao-tecnica-assinatura label="Quem retira" [value]="assinaturaEquipe()" (valueChanged)="assinaturaEquipe.set($event)" />
                        </div>
                      }
                    </div>

                    <div class="dd-secao-topo">
                      <span class="dd-muted"><strong class="dd-forte">{{ itensMarcados() }}</strong> de {{ itensEntrega().length }} itens nesta entrega</span>
                      <span class="dd-linha-botoes">
                        <ces-button label="Entregar tudo o que falta" icon="pi pi-check-square" variant="ghost" size="sm" (pressed)="preencherTudo()" />
                        <ces-button label="Zerar" icon="pi pi-eraser" variant="ghost" size="sm" (pressed)="zerarTudo()" />
                      </span>
                    </div>

                    <ul class="dd-entrega__lista">
                      @for (item of itensEntrega(); track item.idItemSolicitacao; let i = $index) {
                        <li class="dd-entrega__item" [class.dd-entrega__item--ok]="item.quantidadePendente <= 0">
                          <div class="dd-material">
                            <strong>{{ item.descricao }}</strong>
                            <span class="dd-muted">{{ item.codigo }} · falta {{ item.quantidadePendente | number: '1.0-3' }} {{ item.unidade }} de {{ item.quantidadeSolicitada | number: '1.0-3' }}</span>
                          </div>
                          @if (item.quantidadePendente > 0) {
                            @if (item.detalhaSerie) {
                              <div class="dd-entrega__series">
                                <app-series-selecao
                                  [idSolicitacao]="det.id"
                                  [origem]="origemEntrega()"
                                  [material]="{ id: item.idMaterial, codigo: item.codigo, descricao: item.descricao }"
                                  [maximo]="item.quantidadePendente"
                                  [selecionadas]="item.series"
                                  (selecionadasChange)="definirSeries(i, $event)"
                                />
                              </div>
                            } @else {
                              <div class="dd-entrega__campos">
                                <ces-number-input
                                  label="Entregar agora"
                                  [suffix]="' ' + item.unidade"
                                  [min]="0"
                                  [max]="item.quantidadePendente"
                                  [maxFractionDigits]="3"
                                  [value]="item.quantidadeAEntregar"
                                  (valueChange)="definirQuantidade(i, $event)"
                                  [error]="(item.quantidadeAEntregar ?? 0) > item.quantidadePendente ? 'Máximo ' + item.quantidadePendente : undefined"
                                />
                              </div>
                            }
                          } @else {
                            <ces-status tone="success" icon="pi pi-check">Já entregue</ces-status>
                          }
                        </li>
                      }
                    </ul>
                  </div>
                </ng-template>
              }

              @if (det.operacoes?.length) {
                <ng-template cesTabPanel="historico">
                  <app-solicitacao-operacoes-historico [operacoes]="det.operacoes!" [equipeNome]="det.estoqueDestino.nomeIdentificador" [recolhivel]="false" />
                </ng-template>
              }
            </ces-tabs>
          } @else {
            <section aria-labelledby="dd-materiais-titulo">
              <h3 id="dd-materiais-titulo" class="dd-titulo-secao"><i class="pi pi-box" aria-hidden="true"></i>Materiais</h3>
              <ng-container *ngTemplateOutlet="materiais" />
            </section>
          }

          <ng-template #materiais>
              <div class="dd-secao-topo">
                <span class="dd-muted">{{ det.itens.length }} {{ det.itens.length === 1 ? 'material' : 'materiais' }}</span>
                @if (podeRegistrarPendencia() && !det.motivoPendencia) {
                  <ces-button label="Apontar pendência geral" icon="pi pi-flag" variant="ghost" size="sm" (pressed)="abrirPendenciaGeral()" />
                }
              </div>
              <div class="dd-tabela" role="table" aria-label="Materiais solicitados">
                <div class="dd-tabela__linha dd-tabela__cab" role="row">
                  <span role="columnheader">Material</span>
                  <span role="columnheader" class="dd-num">Pedido</span>
                  <span role="columnheader" class="dd-num">Entregue</span>
                  <span role="columnheader" class="dd-num">Falta</span>
                  <span role="columnheader"><span class="dd-sr">Ações</span></span>
                </div>
                @for (item of det.itens; track item.id) {
                  <div class="dd-tabela__linha" role="row">
                    <span role="cell" class="dd-material">
                      <strong>{{ item.material.descricao }}</strong>
                      <span class="dd-muted">
                        {{ item.material.codigo }} · {{ item.material.unidadeMedida || 'UN' }}
                        @if (item.material.detalhaSerie) { · com nº de série }
                      </span>
                      @if (item.motivoPendencia) {
                        <ces-status tone="warning" icon="pi pi-flag">{{ item.motivoPendencia.nome }}</ces-status>
                      } @else if (entregue(item) >= item.quantidade) {
                        <ces-status tone="success" icon="pi pi-check">Atendido</ces-status>
                      } @else if (entregue(item) > 0) {
                        <ces-status tone="warning" icon="pi pi-truck">Parcial</ces-status>
                      }
                      @if (podeDefinirSeries(item)) {
                        <span class="dd-reserva">
                          @if (item.seriesReservadas?.length) {
                            <ces-status tone="brand" icon="pi pi-lock">{{ item.seriesReservadas!.length }} de {{ pendente(item) | number: '1.0-0' }} {{ pendente(item) === 1 ? 'série definida' : 'séries definidas' }}</ces-status>
                          }
                          <button type="button" class="dd-reserva__botao" [attr.aria-expanded]="reservaAberta() === item.id" [attr.aria-controls]="'dd-reserva-' + item.id" (click)="alternarReserva(item)">
                            {{ reservaAberta() === item.id ? 'Fechar' : item.seriesReservadas?.length ? 'Alterar séries' : 'Definir séries' }}
                            <i class="pi" [class.pi-chevron-up]="reservaAberta() === item.id" [class.pi-chevron-down]="reservaAberta() !== item.id" aria-hidden="true"></i>
                          </button>
                        </span>
                      }
                      @if (!erroConsumo()) {
                        <app-consumo-anterior-linha
                          [consumo]="consumoPorMaterial()[item.material.id] ?? null"
                          [unidade]="item.material.unidadeMedida || 'UN'"
                          [carregando]="carregandoConsumo()"
                          [idControlado]="'dd-ops-' + item.id"
                          [aberto]="consumoAberto().has(item.material.id)"
                          (alternar)="alternarConsumo(item.material.id)"
                        />
                      }
                    </span>
                    <span role="cell" class="dd-num">{{ item.quantidade | number: '1.0-3' }}</span>
                    <span role="cell" class="dd-num">{{ entregue(item) | number: '1.0-3' }}</span>
                    <span role="cell" class="dd-num" [class.dd-texto-aviso]="pendente(item) > 0">{{ pendente(item) | number: '1.0-3' }}</span>
                    <span role="cell" class="dd-acao">
                      @if (podeRegistrarPendencia()) {
                        <ces-icon-button
                          [icon]="item.motivoPendencia ? 'pi pi-exclamation-triangle' : 'pi pi-flag'"
                          [label]="(item.motivoPendencia ? 'Ver ou resolver a pendência de ' : 'Apontar pendência em ') + item.material.codigo"
                          [tooltip]="item.motivoPendencia ? 'Ver ou resolver pendência' : 'Apontar pendência'"
                          size="icon-sm"
                          (pressed)="abrirPendenciaItem(item)"
                        />
                      }
                    </span>
                    @if (reservaAberta() === item.id) {
                      <div class="dd-consumo-ops dd-reserva__painel" role="cell" [id]="'dd-reserva-' + item.id">
                        <p class="dd-muted dd-reserva__texto">
                          As séries definidas ficam reservadas para esta solicitação: não saem de {{ det.estoqueOrigem.nomeIdentificador }} por outro caminho. Na entrega, é só confirmar.
                        </p>
                        <app-series-selecao
                          [idSolicitacao]="det.id"
                          [origem]="{ id: det.estoqueOrigem.id, nome: det.estoqueOrigem.nomeIdentificador }"
                          [material]="{ id: item.material.id, codigo: item.material.codigo, descricao: item.material.descricao }"
                          [maximo]="pendente(item)"
                          [(selecionadas)]="reservaRascunho"
                        />
                        <span class="dd-linha-botoes dd-reserva__acoes">
                          <ces-button label="Cancelar" variant="ghost" size="sm" (pressed)="reservaAberta.set(null)" />
                          <ces-button label="Salvar séries" icon="pi pi-lock" size="sm" [loading]="salvandoReserva()" (pressed)="salvarReserva(item)" />
                        </span>
                      </div>
                    }
                    @if (consumoAberto().has(item.material.id)) {
                      <div class="dd-consumo-ops" role="cell" [id]="'dd-ops-' + item.id">
                        <app-consumo-anterior-operacoes [idSolicitacao]="det.id" [idMaterial]="item.material.id" [unidade]="item.material.unidadeMedida || 'UN'" />
                      </div>
                    }
                  </div>
                }
              </div>
            </ng-template>
        </div>
      }

      <div drawer-footer class="dd-rodape">
        <span class="dd-linha-botoes">
          <ces-icon-button icon="pi pi-external-link" label="Abrir relatório completo" tooltip="Relatório completo" (pressed)="abrirRelatorio()" />
          <ces-icon-button icon="pi pi-print" label="Imprimir a solicitação" tooltip="Imprimir" (pressed)="imprimir()" />
          @if (copiloto()) {
            <ces-icon-button icon="pi pi-sparkles" label="Analisar esta solicitação com o Copiloto" tooltip="Analisar com o Copiloto" (pressed)="pedirAnalise()" />
          }
        </span>
        <span class="dd-linha-botoes">
          @if (aba() === 'entrega' && podeEntregar()) {
            <ces-button label="Voltar" variant="ghost" (pressed)="aba.set('itens')" />
            <ces-button
              [label]="'Confirmar entrega' + (itensMarcados() ? ' (' + itensMarcados() + ')' : '')"
              icon="pi pi-check-circle"
              [loading]="salvandoEntrega()"
              [disabled]="!itensMarcados()"
              (pressed)="confirmarEntrega()"
            />
          } @else {
            @if (podeRejeitar()) {
              <ces-button [label]="detalhe()?.status === 'PENDENTE' ? 'Reprovar' : 'Rejeitar'" icon="pi pi-times" variant="outline" [disabled]="executando()" (pressed)="rejeicaoAberta.set(true)" />
            }
            @if (podeAprovar()) {
              <ces-button label="Aprovar" icon="pi pi-check" [loading]="executando()" (pressed)="aprovar()" />
            } @else if (podeEntregar()) {
              <ces-button label="Registrar entrega" icon="pi pi-truck" (pressed)="irParaEntrega()" />
            } @else {
              <ces-button label="Fechar" variant="ghost" (pressed)="visible.set(false)" />
            }
          }
        </span>
      </div>
    </ces-drawer>

    <app-solicitacao-rejeicao-dialog [(visible)]="rejeicaoAberta" [idSolicitacao]="detalhe()?.id ?? null" [reprovar]="detalhe()?.status === 'PENDENTE'" (rejeitada)="aoAlterar()" />

    @if (detalhe(); as det) {
      <app-solicitacao-pendencia-modal
        [(visible)]="pendenciaAberta"
        [idSolicitacao]="det.id"
        [idItem]="pendenciaItemId()"
        [itemLabel]="pendenciaItemRotulo()"
        [motivoAtual]="pendenciaMotivo()"
        [justificativaAtual]="pendenciaJustificativa()"
        (pendenciaSalva)="aoAlterar()"
        (pendenciaResolvida)="aoAlterar()"
      />
    }
  `
})
export class SolicitacaoComercialDetalheComponent {
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly rh = inject(RhService);
  private readonly usuarios = inject(UsuarioLookupService);
  private readonly notificacoes = inject(NotificationService);
  private readonly router = inject(Router);

  readonly visible = model(false);
  readonly solicitacaoId = input<string | null>(null);
  readonly abaInicial = input<AbaDetalheComercial>('itens');
  /** Aprovou, rejeitou, entregou ou mexeu em pendência: quem abriu recarrega. */
  readonly alterado = output<void>();
  /** Mostra o atalho de análise quando o usuário pode usar o Copiloto. */
  readonly copiloto = input(false);
  /** "Analisar": a tela abre o Copiloto ao lado com o contexto desta solicitação. */
  readonly analisar = output<{ id: string; rotulo: string; pergunta: string }>();

  protected readonly detalhe = signal<SolicitacaoResponse | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly executando = signal(false);
  protected readonly aba = signal<string>('itens');

  protected readonly rejeicaoAberta = signal(false);
  protected readonly pendenciaAberta = signal(false);
  protected readonly pendenciaItemId = signal<string | null>(null);
  protected readonly pendenciaItemRotulo = signal<string | null>(null);
  protected readonly pendenciaMotivo = signal<MotivoPendenciaResponse | null>(null);
  protected readonly pendenciaJustificativa = signal<string | null>(null);

  /** Consumo de cada material desde a última solicitação do mesmo estoque (por id do material). */
  protected readonly consumoPorMaterial = signal<Record<string, ConsumoAnteriorItem>>({});
  protected readonly carregandoConsumo = signal(false);
  protected readonly erroConsumo = signal(false);
  /** Item com o painel de definir séries aberto e o rascunho das séries escolhidas. */
  protected readonly reservaAberta = signal<string | null>(null);
  protected readonly reservaRascunho = signal<string[]>([]);
  protected readonly salvandoReserva = signal(false);

  /** Material com série, ainda com saldo a entregar, em solicitação que aceita entrega. */
  protected podeDefinirSeries(item: ItemSolicitacaoResponse): boolean {
    return !!item.material.detalhaSerie && this.pendente(item) > 0 && this.podeEntregar();
  }

  protected alternarReserva(item: ItemSolicitacaoResponse): void {
    if (this.reservaAberta() === item.id) return this.reservaAberta.set(null);
    this.reservaRascunho.set([...(item.seriesReservadas ?? [])]);
    this.reservaAberta.set(item.id);
  }

  protected salvarReserva(item: ItemSolicitacaoResponse): void {
    const det = this.detalhe();
    if (!det || this.salvandoReserva()) return;
    this.salvandoReserva.set(true);
    this.almoxarifado.definirSeriesReservadas(det.id, item.id, this.reservaRascunho()).subscribe({
      next: (series) => {
        this.salvandoReserva.set(false);
        this.reservaAberta.set(null);
        this.notificacoes.success(
          series.length
            ? `${series.length} ${series.length === 1 ? 'série reservada' : 'séries reservadas'} para ${item.material.codigo}. Na entrega, é só confirmar.`
            : `Séries de ${item.material.codigo} liberadas.`
        );
        this.aoAlterar();
      },
      error: (err) => {
        this.salvandoReserva.set(false);
        this.notificacoes.error(getErrorMessage(err), { title: 'Não foi possível salvar as séries' });
      }
    });
  }

  /** Materiais com as operações do consumo abertas abaixo do item. */
  protected readonly consumoAberto = signal<ReadonlySet<string>>(new Set());

  protected readonly itensEntrega = signal<ItemEntregaDraft[]>([]);
  protected readonly responsavelId = signal<string | null>(null);
  protected readonly saqueForaEquipe = signal(false);
  protected readonly assinatura = signal<'FISICA' | 'DIGITAL'>('FISICA');
  protected readonly membrosEquipe = signal<CesSelectOption<string>[]>([]);
  protected readonly colaboradoresGerais = signal<CesSelectOption<string>[]>([]);
  protected readonly carregandoColaboradores = signal(false);
  protected readonly salvandoEntrega = signal(false);
  protected readonly tentouEntregar = signal(false);
  /** Assinaturas desenhadas no dispositivo (PNG em data URL), quando a entrega é digital. */
  protected readonly assinaturaAlmox = signal('');
  protected readonly assinaturaEquipe = signal('');

  // Quem entrega pode trocar o almoxarifado que atende, antes da primeira saída
  private readonly origensCarregadas = signal<CesSelectOption<string>[]>([]);
  protected readonly carregandoOrigens = signal(false);
  protected readonly idOrigemEntrega = signal<string | null>(null);
  protected readonly origemEntrega = computed(() => {
    const atual = this.detalhe()?.estoqueOrigem;
    const id = this.idOrigemEntrega() ?? atual?.id ?? '';
    if (id === atual?.id) return { id, nome: atual.nomeIdentificador };
    return { id, nome: this.origensCarregadas().find((o) => o.value === id)?.label ?? '' };
  });
  /** Antes de abrir a lista, o select mostra só a origem atual. */
  protected readonly opcoesOrigem = computed<CesSelectOption<string>[]>(() => {
    const atual = this.detalhe()?.estoqueOrigem;
    if (this.origensCarregadas().length) return this.origensCarregadas();
    return atual ? [{ label: atual.nomeIdentificador, value: atual.id }] : [];
  });
  protected readonly origemTrocada = computed(() => this.origemEntrega().id !== this.detalhe()?.estoqueOrigem?.id);
  /** O estorno devolve à origem da solicitação: depois da primeira saída ela não muda mais. */
  protected readonly podeTrocarOrigem = computed(() => !(this.detalhe()?.itens ?? []).some((i) => this.entregue(i) > 0));
  protected readonly temSeriesReservadas = computed(() => (this.detalhe()?.itens ?? []).some((i) => i.seriesReservadas?.length));

  protected carregarOrigens(): void {
    if (this.origensCarregadas().length || this.carregandoOrigens()) return;
    this.carregandoOrigens.set(true);
    this.almoxarifado.listarEstoquesAtivos().subscribe({
      next: (res) => {
        const idDestino = this.detalhe()?.estoqueDestino?.id;
        this.origensCarregadas.set(
          (res.content ?? [])
            .filter((e) => e.ativo && e.tipoEstoque?.nome?.toUpperCase() === 'FISICO' && e.id !== idDestino)
            .map((e) => ({ label: e.nomeIdentificador, value: e.id }))
        );
        this.carregandoOrigens.set(false);
      },
      error: () => this.carregandoOrigens.set(false)
    });
  }

  protected trocarOrigem(id: string | null): void {
    if (!id || id === this.origemEntrega().id) return;
    this.idOrigemEntrega.set(id);
    // As séries escolhidas eram da origem anterior.
    this.itensEntrega.update((itens) => itens.map((it) => (it.detalhaSerie && it.series.length ? { ...it, series: [], quantidadeAEntregar: 0 } : it)));
  }

  protected trocarAssinatura(tipo: 'FISICA' | 'DIGITAL' | null): void {
    this.assinatura.set(tipo ?? 'FISICA');
    this.assinaturaAlmox.set('');
    this.assinaturaEquipe.set('');
  }

  protected readonly opcoesAssinatura: CesSelectOption<'FISICA' | 'DIGITAL'>[] = [
    { label: 'Física — termo em papel', value: 'FISICA' },
    { label: 'Digital — no dispositivo', value: 'DIGITAL' }
  ];

  protected readonly idCurto = computed(() => (this.detalhe()?.id ?? this.solicitacaoId() ?? '').substring(0, 8));
  protected readonly status = computed(() => statusComercial(this.detalhe()?.status));
  protected readonly atrasada = computed(() => saqueAtrasado(this.detalhe()));
  protected readonly relativo = computed(() => {
    const prevista = this.detalhe()?.dataPrevista;
    return prevista ? prazoRelativo(prevista) : '';
  });

  protected readonly totalSolicitado = computed(() => (this.detalhe()?.itens ?? []).reduce((a, i) => a + (Number(i.quantidade) || 0), 0));
  protected readonly totalEntregue = computed(() => (this.detalhe()?.itens ?? []).reduce((a, i) => a + this.entregue(i), 0));
  protected readonly percentual = computed(() => {
    const total = this.totalSolicitado();
    return total > 0 ? Math.min(100, Math.round((this.totalEntregue() / total) * 100)) : 0;
  });
  protected readonly itensMarcados = computed(() => this.itensEntrega().filter((i) => (i.quantidadeAEntregar ?? 0) > 0).length);

  /** A política de ações do backend é a fonte de verdade; sem ela, cai na regra pelo status. */
  private pode(acao: string, status: string[]): boolean {
    const det = this.detalhe();
    if (!det) return false;
    if (det.acoesPermitidas) return det.acoesPermitidas.includes(acao);
    return !det.encerrada && status.includes(det.status);
  }
  protected readonly podeAprovar = computed(() => this.pode('APROVAR', ['PENDENTE']));
  protected readonly podeRejeitar = computed(() => this.pode('REJEITAR', ['PENDENTE', 'APROVADO']));
  protected readonly podeEntregar = computed(() => this.pode('ENTREGAR', ['APROVADO', 'ENTREGUE_PARCIAL']));
  protected readonly podeRegistrarPendencia = computed(() => {
    const det = this.detalhe();
    return !!det && !det.encerrada && ['PENDENTE', 'APROVADO', 'ENTREGUE_PARCIAL'].includes(det.status);
  });

  protected readonly abas = computed<readonly CesTabItem[]>(() => {
    const det = this.detalhe();
    const abas: CesTabItem[] = [{ value: 'itens', label: 'Materiais', icon: 'pi pi-box', badge: det?.itens.length || undefined }];
    if (this.podeEntregar()) abas.push({ value: 'entrega', label: 'Registrar entrega', icon: 'pi pi-truck' });
    if (det?.operacoes?.length) abas.push({ value: 'historico', label: 'Histórico', icon: 'pi pi-history', badge: det.operacoes.length });
    return abas;
  });

  protected readonly fluxo = computed<EtapaFluxo[]>(() => {
    const det = this.detalhe();
    if (!det) return [];
    const rejeitada = det.status === 'REJEITADO';
    const aprovada = !!det.dataVerificacao && !rejeitada;
    const entregueTotal = det.status === 'ENTREGUE';
    const entregaIniciada = entregueTotal || det.status === 'ENTREGUE_PARCIAL' || det.status === 'ESTORNADO';
    const etapas: EtapaFluxo[] = [
      { rotulo: 'Solicitada', estado: 'feito', quando: det.dataSolicitacao, quem: this.nome(det.idRequisitante), icone: 'pi-send' },
      rejeitada
        ? { rotulo: 'Rejeitada', estado: 'interrompido', quando: det.dataVerificacao, quem: det.idAprovador ? this.nome(det.idAprovador) : null, icone: 'pi-times' }
        : aprovada
          ? { rotulo: 'Aprovada', estado: 'feito', quando: det.dataVerificacao, quem: det.idAprovador ? this.nome(det.idAprovador) : null, icone: 'pi-check' }
          : { rotulo: 'Aguardando aprovação', estado: 'atual', icone: 'pi-clock' }
    ];
    if (!rejeitada) {
      etapas.push({
        rotulo: entregueTotal ? 'Entregue' : det.status === 'ESTORNADO' ? 'Estornada' : entregaIniciada ? 'Entregue em parte' : aprovada ? 'Aguardando retirada' : 'Entrega',
        estado: entregueTotal || det.status === 'ESTORNADO' ? 'feito' : aprovada ? 'atual' : 'futuro',
        quando: det.dataEntrega,
        quem: det.idEntregadorAlmox ? this.nome(det.idEntregadorAlmox) : null,
        icone: det.status === 'ESTORNADO' ? 'pi-replay' : 'pi-truck'
      });
    }
    return etapas;
  });

  protected readonly perguntaCopiloto = computed(() => {
    const det = this.detalhe();
    return det
      ? `Analisar status, disponibilidade e o consumo desde a última solicitação dos materiais da solicitação comercial #${this.idCurto()} de ${det.estoqueOrigem.nomeIdentificador} para ${det.estoqueDestino.nomeIdentificador}`
      : '';
  });

  constructor() {
    effect(() => {
      const id = this.solicitacaoId();
      if (!this.visible() || !id) return;
      const aba = this.abaInicial();
      untracked(() => {
        this.aba.set(aba);
        this.carregar(id);
      });
    });
  }

  private carregar(id: string): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.carregarConsumo(id);
    this.almoxarifado.buscarSolicitacaoPorId(id).subscribe({
      next: (det) => {
        this.detalhe.set(det);
        this.prepararEntrega(det);
        this.carregando.set(false);
        // A aba de entrega só existe enquanto há saldo a entregar.
        if (this.aba() === 'entrega' && !this.podeEntregar()) this.aba.set('itens');
      },
      error: (err) => {
        this.carregando.set(false);
        this.erro.set(getErrorMessage(err));
      }
    });
  }

  protected alternarConsumo(idMaterial: string): void {
    this.consumoAberto.update((atual) => {
      const proximo = new Set(atual);
      if (!proximo.delete(idMaterial)) proximo.add(idMaterial);
      return proximo;
    });
  }

  private carregarConsumo(id: string): void {
    this.consumoAberto.set(new Set());
    this.carregandoConsumo.set(true);
    this.erroConsumo.set(false);
    this.almoxarifado.buscarConsumoAnterior(id).subscribe({
      next: (r) => {
        this.consumoPorMaterial.set(Object.fromEntries((r.itens ?? []).map((i) => [i.idMaterial, i])));
        this.carregandoConsumo.set(false);
      },
      // Informação de apoio: sem ela o detalhe segue normal.
      error: () => {
        this.consumoPorMaterial.set({});
        this.erroConsumo.set(true);
        this.carregandoConsumo.set(false);
      }
    });
  }

  protected entregue(item: ItemSolicitacaoResponse): number {
    return item.quantidadeEntregaLiquida ?? item.quantidadeEntregue ?? 0;
  }

  protected pendente(item: ItemSolicitacaoResponse): number {
    return item.quantidadePendente ?? Math.max(0, item.quantidade - this.entregue(item));
  }

  protected nome(id?: string | null): string {
    return this.usuarios.resolverNome(id, 'Não informado');
  }

  private prepararEntrega(det: SolicitacaoResponse): void {
    this.tentouEntregar.set(false);
    this.idOrigemEntrega.set(null);
    this.trocarAssinatura('FISICA');
    this.itensEntrega.set(
      (det.itens ?? []).map((i) => ({
        idItemSolicitacao: i.id,
        codigo: i.material.codigo,
        descricao: i.material.descricao,
        unidade: i.material.unidadeMedida || 'UN',
        detalhaSerie: !!i.material.detalhaSerie,
        quantidadeSolicitada: i.quantidade,
        jaEntregue: this.entregue(i),
        quantidadePendente: this.pendente(i),
        // Com série: sai o que tiver série escolhida; as reservadas já vêm escolhidas, só confirmar.
        quantidadeAEntregar: i.material.detalhaSerie ? (i.seriesReservadas?.length ?? 0) : this.pendente(i),
        series: [...(i.seriesReservadas ?? [])],
        idMaterial: i.material.id
      }))
    );
    this.responsavelId.set(det.idColaboradorSaque ?? null);
    const equipe = det.idEquipe || det.estoqueDestino?.idEquipe;
    this.saqueForaEquipe.set(!!det.saqueForaEquipe || !equipe);
    if (this.saqueForaEquipe()) this.carregarColaboradoresGerais();
    else if (equipe) this.carregarMembros(equipe);
  }

  protected irParaEntrega(): void {
    this.aba.set('entrega');
  }

  protected definirQuantidade(indice: number, valor: number | null): void {
    this.itensEntrega.update((itens) => itens.map((it, i) => (i === indice ? { ...it, quantidadeAEntregar: valor } : it)));
  }

  protected definirSeries(indice: number, series: string[]): void {
    this.itensEntrega.update((itens) => itens.map((it, i) => (i === indice ? { ...it, series, quantidadeAEntregar: series.length } : it)));
  }

  /** Entrega todo o saldo do material comum; o material com série sai com as séries já escolhidas. */
  protected preencherTudo(): void {
    this.itensEntrega.update((itens) =>
      itens.map((it) => (it.quantidadePendente <= 0 || it.detalhaSerie ? it : { ...it, quantidadeAEntregar: it.quantidadePendente }))
    );
  }

  protected zerarTudo(): void {
    this.itensEntrega.update((itens) => itens.map((it) => ({ ...it, quantidadeAEntregar: 0, series: [] })));
  }

  protected alternarForaEquipe(fora: boolean): void {
    const det = this.detalhe();
    const equipe = det?.idEquipe || det?.estoqueDestino?.idEquipe;
    if (!fora && !equipe) {
      this.notificacoes.warning('O estoque de destino não tem equipe vinculada; o saque precisa ser por alguém de fora.');
      return;
    }
    this.saqueForaEquipe.set(fora);
    this.responsavelId.set(null);
    if (fora && !this.colaboradoresGerais().length) this.carregarColaboradoresGerais();
    if (!fora && equipe && !this.membrosEquipe().length) this.carregarMembros(equipe);
  }

  private carregarMembros(equipe: string): void {
    this.carregandoColaboradores.set(true);
    this.rh.listarMembrosAtivos(equipe).subscribe({
      next: (membros) => {
        const opcoes = (membros ?? []).map((m) => ({ label: m.nomeColaborador, value: m.idColaborador }));
        this.membrosEquipe.set(opcoes);
        if (!this.responsavelId() && opcoes.length === 1) this.responsavelId.set(opcoes[0].value);
        this.carregandoColaboradores.set(false);
      },
      error: () => {
        this.membrosEquipe.set([]);
        this.carregandoColaboradores.set(false);
      }
    });
  }

  private carregarColaboradoresGerais(): void {
    this.carregandoColaboradores.set(true);
    this.rh.listarColaboradores({ ativo: true, size: 200, sort: 'nomeCompleto,asc' }).subscribe({
      next: (res) => {
        this.colaboradoresGerais.set((res?.content ?? []).map((c) => ({ label: c.nomeCompleto, value: c.id })));
        this.carregandoColaboradores.set(false);
      },
      error: () => {
        this.colaboradoresGerais.set([]);
        this.carregandoColaboradores.set(false);
      }
    });
  }

  protected aprovar(): void {
    const det = this.detalhe();
    if (!det || this.executando()) return;
    this.executando.set(true);
    this.almoxarifado.aprovarSolicitacao(det.id, {}).subscribe({
      next: () => {
        this.executando.set(false);
        this.notificacoes.success(`Solicitação #${this.idCurto()} aprovada. Ela agora aguarda retirada.`);
        this.aoAlterar();
      },
      error: (err) => {
        this.executando.set(false);
        this.notificacoes.error(getErrorMessage(err), { title: 'Não foi possível aprovar' });
      }
    });
  }

  protected confirmarEntrega(): void {
    const det = this.detalhe();
    if (!det || this.salvandoEntrega()) return;
    this.tentouEntregar.set(true);
    const itens = this.itensEntrega().filter((i) => (i.quantidadeAEntregar ?? 0) > 0);
    if (!this.responsavelId()) return this.notificacoes.warning('Escolha quem retira o material.');
    if (!itens.length) return this.notificacoes.warning('Informe a quantidade de ao menos um item.');
    const excedido = itens.find((i) => (i.quantidadeAEntregar ?? 0) > i.quantidadePendente);
    if (excedido) return this.notificacoes.warning(`${excedido.codigo}: a quantidade passa do que falta entregar.`);

    const digital = this.assinatura() === 'DIGITAL';
    if (digital && !(ehImagem(this.assinaturaAlmox()) && ehImagem(this.assinaturaEquipe())))
      return this.notificacoes.warning('Colete as duas assinaturas digitais: almoxarifado e quem retira.');

    const pedido: EntregaSolicitacaoRequest = {
      assinaturaAlmox: digital ? this.assinaturaAlmox() : 'FISICA',
      assinaturaEquipe: digital ? this.assinaturaEquipe() : 'FISICA',
      idColaboradorSaque: this.responsavelId()!,
      saqueForaEquipe: this.saqueForaEquipe(),
      ...montarItensEntrega(
        itens.map((i) => ({
          idItemSolicitacao: i.idItemSolicitacao,
          idMaterial: i.idMaterial,
          detalhaSerie: i.detalhaSerie,
          quantidadeSolicitada: i.quantidadeSolicitada,
          jaEntregue: i.jaEntregue,
          quantidade: i.quantidadeAEntregar ?? 0,
          series: i.series
        }))
      ),
      ...(this.origemTrocada() ? { idEstoqueOrigem: this.origemEntrega().id } : {})
    };
    this.salvandoEntrega.set(true);
    this.almoxarifado.registrarEntrega(det.id, pedido).subscribe({
      next: () => {
        this.salvandoEntrega.set(false);
        const completa = itens.length === this.itensEntrega().filter((i) => i.quantidadePendente > 0).length && itens.every((i) => i.quantidadeAEntregar === i.quantidadePendente);
        this.notificacoes.success(completa ? 'Entrega registrada. A solicitação foi concluída.' : 'Entrega parcial registrada. O saldo continua aguardando retirada.');
        this.aba.set('itens');
        this.aoAlterar();
      },
      error: (err) => {
        this.salvandoEntrega.set(false);
        this.notificacoes.error(getErrorMessage(err), { title: 'Não foi possível registrar a entrega' });
      }
    });
  }

  protected abrirPendenciaGeral(): void {
    const det = this.detalhe();
    if (!det) return;
    this.pendenciaItemId.set(null);
    this.pendenciaItemRotulo.set(null);
    this.pendenciaMotivo.set(det.motivoPendencia ?? null);
    this.pendenciaJustificativa.set(det.justificativaPendencia ?? null);
    this.pendenciaAberta.set(true);
  }

  protected abrirPendenciaItem(item: ItemSolicitacaoResponse): void {
    this.pendenciaItemId.set(item.id);
    this.pendenciaItemRotulo.set(`${item.material.codigo} — ${item.material.descricao}`);
    this.pendenciaMotivo.set(item.motivoPendencia ?? null);
    this.pendenciaJustificativa.set(item.justificativaPendencia ?? null);
    this.pendenciaAberta.set(true);
  }

  protected resolverPendenciaGeral(): void {
    const det = this.detalhe();
    if (!det || this.executando()) return;
    this.executando.set(true);
    this.almoxarifado.removerPendencia(det.id).subscribe({
      next: () => {
        this.executando.set(false);
        this.notificacoes.success('Pendência resolvida.');
        this.aoAlterar();
      },
      error: (err) => {
        this.executando.set(false);
        this.notificacoes.error(getErrorMessage(err), { title: 'Não foi possível resolver a pendência' });
      }
    });
  }

  /** Recarrega o detalhe e avisa quem abriu para atualizar a lista/quadro/calendário. */
  protected aoAlterar(): void {
    const det = this.detalhe();
    if (det) this.carregar(det.id);
    this.alterado.emit();
  }

  protected pedirAnalise(): void {
    const det = this.detalhe();
    if (det) this.analisar.emit({ id: det.id, rotulo: `Solicitação #${this.idCurto()}`, pergunta: this.perguntaCopiloto() });
  }

  protected abrirRelatorio(): void {
    const id = this.detalhe()?.id ?? this.solicitacaoId();
    if (!id) return;
    this.visible.set(false);
    void navegarSaindoDoDrawer(() => this.router.navigate(['/almoxarifado/solicitacoes', id]));
  }

  protected imprimir(): void {
    const id = this.detalhe()?.id ?? this.solicitacaoId();
    if (!id) return;
    const url = this.router.serializeUrl(this.router.createUrlTree(['/almoxarifado/solicitacoes', id], { queryParams: { mode: 'print' } }));
    window.open(url, '_blank', 'noopener');
  }

  protected aoFechar(): void {
    this.detalhe.set(null);
    this.consumoPorMaterial.set({});
    this.consumoAberto.set(new Set());
    this.erro.set(null);
  }
}

