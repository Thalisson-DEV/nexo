import { ChangeDetectionStrategy, Component, Injector, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';

import { DbService } from '../../../core/db/db.service';
import { TokenService } from '../../../core/auth/token.service';
import { TurnoService } from '../../../core/turno/turno.service';
import { ExecucoesService } from '../../../core/campo/execucoes.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ConnectivityService } from '../../../core/sync/connectivity.service';
import { SyncService } from '../../../core/sync/sync.service';
import { CorrecaoRecusadaError, OutboxService } from '../../../core/sync/outbox.service';
import { RascunhoService, VinculoRascunho } from '../../../core/sessao/rascunho.service';
import { SessaoService } from '../../../core/sessao/sessao.service';
import {
  AcaoComercialLocal, ExecucaoCampoLocal, ExecucaoMaterialLocal, MaterialLocal, OrigemMaterial, SaldoSerieLocal, TipoNota, TipoServicoLocal,
} from '../../../core/db/models';
import {
  FaltaSaldo,
  PRAZO_ALTERACAO_CAMPO_DIAS,
  SaldoEquipeSnapshot,
  chaveSerie,
  diferencaDaNota,
  linhaComSerie,
  podeAlterarNoCampo,
  preverFaltasDeSaldo,
  rotuloNota
} from '../../../core/campo/campo-regras';
import { diasDesde, rotuloSerieNoSaldo, tomTempoSaldo } from '../../../core/saldo/tempo-saldo';
import {
  BarraTopoComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, QuantidadeComponent, SegmentadoComponent, SeletorComponent, VazioComponent, OpcaoSeletor,
} from '../../../ui';

type Operacao = 'APLICADO' | 'RETIRADO';
type EstadoGps = 'capturando' | 'ok' | 'falhou' | 'indisponivel';

interface MaterialCatalogo {
  idMaterial: string;
  codigo: string;
  descricao: string;
  unidadeMedida: string;
  detalhaSerie: boolean;
  /** Saldo NOVO da equipe no aparelho (0 quando fora do saldo). */
  saldo: number;
}

interface SelectedMaterial {
  idMaterial: string;
  codigo: string;
  descricao: string;
  unidadeMedida: string;
  quantidade: number;
  operacao: Operacao;
  condicao: 'NOVO' | 'SUCATA';
  numSeries: string[];
  detalhaSerie: boolean;
  /** Só na ocorrência pode ser CONCESSIONARIA (Concessionária); ausente em rascunhos antigos vale EQUIPE. */
  origem?: OrigemMaterial;
}

/** Tudo que o encarregado já fez neste lançamento — gravado no aparelho a cada toque. */
export interface RascunhoExecucao {
  passo: number;
  nota: string;
  /** Ausente em rascunhos de versões anteriores: nota comum. */
  tipoNota?: TipoNota;
  /** Número provisório já gerado para a nota avulsa. */
  numeroAvulsa?: string;
  /** Id do tipo de serviço escolhido (ausente em rascunhos de versões anteriores). */
  tipoServico?: string | null;
  latitude?: number;
  longitude?: number;
  precisao?: number;
  gpsPulado: boolean;
  acoes: string[];
  materiais: SelectedMaterial[];
  abaMaterial: 'saldo' | 'outros';
  /** Folha de séries aberta (`idMaterial|operacao`) — volta aberta se o app reabrir. */
  folhaSerie: string | null;
  /** Motivo da alteração de uma nota já sincronizada. */
  motivo?: string;
}

export const TOTAL_ETAPAS = 4;
const TITULOS = ['Nota, tipo e local', 'Serviços', 'Materiais', 'Conferir'];

function origemDe(m: Pick<SelectedMaterial, 'origem'>): OrigemMaterial {
  return m.origem ?? 'EQUIPE';
}

/** Mesma linha de material: mesmo material, mesma operação e mesma origem. */
function mesmaLinha(a: SelectedMaterial, b: Pick<SelectedMaterial, 'idMaterial' | 'operacao' | 'origem'>): boolean {
  return a.idMaterial === b.idMaterial && a.operacao === b.operacao && origemDe(a) === origemDe(b);
}

function chaveLinha(m: Pick<SelectedMaterial, 'idMaterial' | 'operacao' | 'origem'>): string {
  return `${m.idMaterial}|${m.operacao}|${origemDe(m)}`;
}

/**
 * Lançamento de execução de campo em 4 passos curtos, com o botão principal sempre
 * no mesmo lugar. Nada se perde: cada toque vai para o rascunho no aparelho; sair,
 * bloquear a tela ou o sistema matar o app traz de volta ao mesmo passo.
 * O GPS funciona sem internet; materiais fora do saldo são aceitos (o servidor
 * gera as pendências).
 */
@Component({
  selector: 'app-nova-execucao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CurrencyPipe, BarraTopoComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, QuantidadeComponent,
    SegmentadoComponent, SeletorComponent, VazioComponent,
  ],
  template: `
    <div class="tela">
      <ces-barra-topo [titulo]="alterarId() ? 'Alterar ' + rotuloAlteracao() : editId() ? 'Corrigir execução' : 'Nova execução'"
        [subtitulo]="titulos[passo() - 1] + ' · ' + passo() + ' de ' + total" navegacao="fechar" (voltar)="fechar()">
        <ces-indicador-rede />
      </ces-barra-topo>
      <div class="passos" aria-hidden="true">
        @for (s of etapas; track s) { <i [class.feito]="s <= passo()"></i> }
      </div>

      @if (carregando()) {
        <div class="esqueletos" aria-busy="true" aria-label="Carregando">
          <div class="ces-esqueleto" style="height: 84px"></div>
          <div class="ces-esqueleto" style="height: 68px"></div>
        </div>
      } @else {
        <section class="conteudo" [attr.aria-label]="titulos[passo() - 1]">
          @switch (passo()) {
            @case (1) {
              <div class="ces-campo bloco tipos">
                <span class="tipos__titulo">Tipo de lançamento</span>
                <ces-segmentado [opcoes]="opcoesTipoNota" [valor]="tipoNota()" (valorChange)="escolherTipoNota($event)" rotulo="Tipo de lançamento" />
                <span class="ces-campo__ajuda">{{ ajudaTipoNota() }}</span>
              </div>

              @if (tipoNota() === 'AVULSA') {
                <div class="ces-campo bloco tipos">
                  <span class="tipos__titulo">Número provisório</span>
                  <div class="ces-grupo provisorio" aria-live="polite">
                    <div class="ces-linha">
                      <span class="ces-selo ces-selo--destaque"><ces-icone nome="documento" /></span>
                      <span class="ces-linha__texto">
                        <span class="ces-linha__titulo ces-num provisorio__num">{{ numeroAvulsa() || 'Gerando…' }}</span>
                        <span class="ces-linha__apoio">Gerado pelo app, funciona sem internet</span>
                      </span>
                    </div>
                  </div>
                  <span class="ces-campo__ajuda">Anote no talão. O número oficial é informado depois, sem refazer o lançamento.</span>
                </div>
              } @else {
                <div class="ces-campo bloco">
                  <label for="nota">{{ tipoNota() === 'OCORRENCIA' ? 'Número da ocorrência' : 'Número da nota' }}</label>
                  <input id="nota" class="ces-input ces-input--grande" type="text" enterkeyhint="next"
                    [attr.inputmode]="tipoNota() === 'OCORRENCIA' ? 'text' : 'numeric'"
                    [attr.autocapitalize]="tipoNota() === 'OCORRENCIA' ? 'characters' : null"
                    autocomplete="off" [value]="nota()" (input)="nota.set($any($event.target).value)" (keydown.enter)="avancar()"
                    [attr.aria-invalid]="tentouAvancar() && !nota().trim() ? 'true' : null" aria-describedby="nota-ajuda">
                  @if (tentouAvancar() && !nota().trim()) {
                    <span id="nota-ajuda" class="ces-campo__erro"><ces-icone nome="alerta" [tamanho]="16" />{{ tipoNota() === 'OCORRENCIA' ? 'Digite o número da ocorrência.' : 'Digite o número da nota.' }}</span>
                  } @else {
                    <span id="nota-ajuda" class="ces-campo__ajuda">{{ tipoNota() === 'OCORRENCIA' ? 'Letras e números, como está na ocorrência.' : 'Como está na OS ou no talão.' }}</span>
                  }
                </div>
              }

              <div class="ces-campo bloco tipos" id="tipo-servico">
                @if (tiposDisponiveis().length) {
                  <ces-seletor rotulo="Tipo de serviço" placeholder="Escolher o tipo" [obrigatorio]="true" [opcoes]="opcoesTipo()"
                    [valor]="tipoServico()" (valorChange)="escolherTipo($event)" [invalido]="faltaTipo()" />
                  @if (faltaTipo()) {
                    <span class="ces-campo__erro" role="alert"><ces-icone nome="alerta" [tamanho]="16" />Escolha o tipo de serviço da nota.</span>
                  } @else {
                    <span class="ces-campo__ajuda">O que está sendo feito nesta nota.</span>
                  }
                } @else {
                  <span class="tipos__titulo">Tipo de serviço</span>
                  <span class="ces-campo__erro"><ces-icone nome="alerta" [tamanho]="16" />Os tipos de serviço ainda não estão no aparelho. Sincronize em Conta com internet para continuar.</span>
                }
              </div>

              <div class="ces-grupo gps" aria-live="polite">
                @switch (estadoGps()) {
                  @case ('ok') {
                    <div class="ces-linha">
                      <span class="ces-selo ces-selo--ok"><ces-icone nome="local" /></span>
                      <span class="ces-linha__texto">
                        <span class="ces-linha__titulo">Local registrado</span>
                        <span class="ces-linha__apoio ces-num">{{ coords() }}{{ precisao() ? ' · ±' + precisao() + ' m' : '' }}</span>
                      </span>
                      <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="capturarGps()">Atualizar</button>
                    </div>
                  }
                  @case ('capturando') {
                    <div class="ces-linha">
                      <span class="ces-selo ces-selo--destaque"><ces-icone nome="local_busca" /></span>
                      <span class="ces-linha__texto">
                        <span class="ces-linha__titulo">Buscando o local…</span>
                        <span class="ces-linha__apoio">O GPS funciona sem internet</span>
                      </span>
                      <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="pularGps()">Seguir sem</button>
                    </div>
                  }
                  @default {
                    <div class="ces-linha">
                      <span class="ces-selo ces-selo--atencao"><ces-icone nome="local" /></span>
                      <span class="ces-linha__texto">
                        <span class="ces-linha__titulo">{{ estadoGps() === 'indisponivel' ? 'GPS indisponível' : 'Sem local' }}</span>
                        <span class="ces-linha__apoio">{{ gpsPulado() ? 'Lançamento segue sem coordenadas' : 'Em campo aberto o sinal melhora' }}</span>
                      </span>
                      @if (estadoGps() !== 'indisponivel') {
                        <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="capturarGps()">Tentar de novo</button>
                      }
                    </div>
                  }
                }
              </div>
            }

            @case (2) {
              <div class="tipo tipo--servico">
                <span class="tipo__selo" aria-hidden="true"><ces-icone nome="servicos" /></span>
                <span class="tipo__txt"><strong>Serviços</strong><span>O trabalho feito na nota: ligação, corte, religação…</span></span>
                <span class="tipo__num ces-num" aria-live="polite" [attr.aria-label]="acoes().length + ' marcados'">{{ acoes().length }}</span>
              </div>
              @if (valorEstimado()) {
                <p class="valor ces-num">Valor estimado <strong>{{ valorEstimado() | currency:'BRL':'symbol':'1.2-2':'pt-BR' }}</strong></p>
              }
              <div class="busca">
                <ces-icone nome="busca" [tamanho]="18" />
                <input type="search" placeholder="Buscar serviço" enterkeyhint="search" aria-label="Buscar serviço"
                  [value]="buscaAcao()" (input)="buscaAcao.set($any($event.target).value)">
              </div>
              @if (acoesFiltradas().length) {
                <ul class="ces-grupo">
                  @for (a of acoesFiltradas(); track a.id) {
                    <li>
                      <button type="button" class="ces-linha ces-linha--acao" [attr.aria-pressed]="marcada(a.id)" (click)="alternarAcao(a.id)">
                        <span class="caixa" [class.marcada]="marcada(a.id)" aria-hidden="true"><ces-icone nome="check" [tamanho]="16" /></span>
                        <span class="ces-linha__texto">
                          <span class="ces-linha__titulo">{{ a.nome }}</span>
                          <span class="ces-linha__apoio ces-num">{{ a.codigo }}{{ a.valor != null ? ' · ' + (a.valor | currency:'BRL':'symbol':'1.2-2':'pt-BR') : '' }}</span>
                        </span>
                      </button>
                    </li>
                  }
                </ul>
              } @else {
                @if (buscaAcao()) {
                  <ces-vazio icone="busca" titulo="Nenhum serviço encontrado" texto="Confira o nome ou o código." />
                } @else {
                  <ces-vazio icone="servicos" titulo="Nenhum serviço para esta equipe" texto="Sincronize em Conta. Se continuar vazio, peça ao escritório para cadastrar os serviços. Dá para seguir só com material." />
                }
              }
            }

            @case (3) {
              <div class="tipo tipo--material">
                <span class="tipo__selo" aria-hidden="true"><ces-icone nome="material" /></span>
                <span class="tipo__txt"><strong>Materiais</strong><span>Peças usadas no cliente ou tiradas dele.</span></span>
                <span class="tipo__num ces-num" [attr.aria-label]="materiais().length + ' materiais'">{{ materiais().length }}</span>
              </div>

              @for (g of grupos(); track g.operacao) {
                @if (g.itens.length) {
                  <h2 class="ces-secao grupo-tit" [class.retirado]="g.operacao === 'RETIRADO'">
                    <span class="marca" aria-hidden="true"><ces-icone [nome]="g.operacao === 'APLICADO' ? 'baixar' : 'subir'" [tamanho]="16" /></span>
                    <span>{{ g.titulo }}</span><span class="ces-num cont">{{ g.itens.length }}</span>
                  </h2>
                  <p class="grupo-ajuda">{{ g.ajuda }}</p>
                  <ul class="ces-grupo">
                    @for (m of g.itens; track chave(m)) {
                      <li>
                        <div class="ces-linha mat" [class.mat--retirado]="m.operacao === 'RETIRADO'">
                          <div class="mat__topo">
                            <span class="ces-linha__texto">
                              <span class="ces-linha__titulo">{{ m.descricao }}</span>
                              <span class="ces-linha__apoio ces-num">Cód. {{ m.codigo }}</span>
                            </span>
                            <button type="button" class="ces-btn-icone" (click)="inverterOperacao(m)"
                              [attr.aria-label]="'Trocar ' + m.descricao + ' para ' + (m.operacao === 'APLICADO' ? 'retirado' : 'aplicado')">
                              <ces-icone nome="transferencia" [tamanho]="20" />
                            </button>
                            <button type="button" class="ces-btn-icone" (click)="removerMaterial(m)" [attr.aria-label]="'Tirar ' + m.descricao">
                              <ces-icone nome="lixeira" [tamanho]="20" />
                            </button>
                          </div>
                          @if (ehOcorrencia()) {
                            <ces-segmentado [opcoes]="opcoesOrigem" [valor]="origem(m)" (valorChange)="definirOrigem(m, $event)"
                              [rotulo]="'De onde veio ' + m.descricao" />
                          }
                          <div class="mat__acoes">
                            @if (m.detalhaSerie) {
                              <span class="qtd-fixa ces-num">{{ m.numSeries.length }} <small>{{ m.unidadeMedida }}</small></span>
                              <button type="button" class="ces-btn ces-btn--tonal ces-btn--compacto" (click)="abrirSeries(m)">
                                {{ m.numSeries.length ? 'Séries' : 'Informar série' }}
                                <ces-icone nome="avancar" [tamanho]="16" />
                              </button>
                            } @else {
                              <ces-quantidade [grande]="true" [unidade]="m.unidadeMedida" [valor]="m.quantidade" (valorChange)="definirQuantidade(m, $event)" [rotulo]="'quantidade de ' + m.descricao" />
                            }
                          </div>
                          @if (m.detalhaSerie && m.numSeries.length) {
                            <p class="series-txt ces-num">{{ m.numSeries.join(' · ') }}</p>
                          }
                          @if (avisoSaldo(m); as aviso) {
                            <p class="aviso"><ces-icone nome="alerta" [tamanho]="16" />{{ aviso }}</p>
                          }
                        </div>
                      </li>
                    }
                  </ul>
                }
              }

              <h2 class="ces-secao">Adicionar</h2>
              <div class="filtros">
                <ces-segmentado [opcoes]="opcoesAba" [valor]="abaMaterial()" (valorChange)="abaMaterial.set($event)" rotulo="Origem do material" />
                <div class="busca">
                  <ces-icone nome="busca" [tamanho]="18" />
                  <input type="search" placeholder="Nome ou código" enterkeyhint="search" aria-label="Buscar material"
                    [value]="buscaMaterial()" (input)="buscaMaterial.set($any($event.target).value)">
                </div>
              </div>
              @if (catalogoFiltrado().length) {
                <ul class="ces-grupo">
                  @for (c of catalogoFiltrado(); track c.idMaterial) {
                    <li>
                      <div class="ces-linha cat">
                        <span class="ces-linha__texto">
                          <span class="ces-linha__titulo">{{ c.descricao }}</span>
                          <span class="ces-linha__apoio ces-num">Cód. {{ c.codigo }}</span>
                        </span>
                        <span class="saldo ces-num" [class.saldo--zero]="c.saldo <= 0">
                          @if (c.saldo > 0) { <span><strong>{{ num(c.saldo) }}</strong> {{ c.unidadeMedida }}</span><small>no caminhão</small> }
                          @else { <small>fora do saldo</small> }
                        </span>
                        <div class="cat__botoes">
                          <button type="button" class="ces-btn ces-btn--tonal ces-btn--compacto op" (click)="adicionar(c, 'APLICADO')"
                            [disabled]="selecionado(c.idMaterial, 'APLICADO')" [attr.aria-label]="'Aplicar ' + c.descricao">
                            <ces-icone nome="baixar" [tamanho]="16" />{{ selecionado(c.idMaterial, 'APLICADO') ? 'Aplicado' : 'Aplicar' }}</button>
                          <button type="button" class="ces-btn ces-btn--neutro ces-btn--compacto op op--retirar" (click)="adicionar(c, 'RETIRADO')"
                            [disabled]="selecionado(c.idMaterial, 'RETIRADO')" [attr.aria-label]="'Retirar ' + c.descricao">
                            <ces-icone nome="subir" [tamanho]="16" />{{ selecionado(c.idMaterial, 'RETIRADO') ? 'Retirado' : 'Retirar' }}</button>
                        </div>
                      </div>
                    </li>
                  }
                </ul>
              } @else {
                <ces-vazio icone="material" [titulo]="buscaMaterial() ? 'Nada encontrado' : 'Sem material no caminhão'"
                  [texto]="buscaMaterial() ? 'Confira o nome ou o código.' : 'Veja em Todos para lançar material fora do saldo.'" />
              }
            }

            @case (4) {
              <dl class="conferir">
                <div><dt>{{ rotuloTipoNota() }}</dt><dd class="ces-num grande">{{ referencia() }}</dd>
                  @if (tipoNota() === 'AVULSA') { <dd class="apoio">Número provisório. O oficial é informado depois.</dd> }</div>
                <div><dt>Tipo de serviço</dt><dd>{{ tipoEscolhido()?.descricao ?? '—' }}@if (tipoEscolhido()?.codigo) { <span class="ces-num cod"> · {{ tipoEscolhido()!.codigo }}</span> }</dd></div>
                <div><dt>Local</dt><dd class="ces-num">{{ temLocal() ? coords() : 'Sem coordenadas' }}</dd></div>
              </dl>

              <h2 class="ces-secao grupo-tit servico"><span class="marca" aria-hidden="true"><ces-icone nome="servicos" [tamanho]="16" /></span>
                <span>Serviços</span><span class="ces-num cont">{{ acoes().length }}</span></h2>
              <ul class="ces-grupo resumo">
                @for (a of acoesEscolhidas(); track a.id) {
                  <li><div class="ces-linha"><span class="ces-linha__texto"><span class="ces-linha__titulo">{{ a.nome }}</span>
                    <span class="ces-linha__apoio ces-num">Cód. {{ a.codigo }}</span></span>
                    @if (a.valor != null) { <span class="ces-num direita">{{ a.valor | currency:'BRL':'symbol':'1.2-2':'pt-BR' }}</span> }
                  </div></li>
                } @empty { <li><div class="ces-linha"><span class="ces-linha__apoio">Nenhum serviço</span></div></li> }
              </ul>
              @if (valorEstimado()) { <p class="valor ces-num">Valor estimado <strong>{{ valorEstimado() | currency:'BRL':'symbol':'1.2-2':'pt-BR' }}</strong></p> }

              @for (g of grupos(); track g.operacao) {
                <h2 class="ces-secao grupo-tit" [class.retirado]="g.operacao === 'RETIRADO'">
                  <span class="marca" aria-hidden="true"><ces-icone [nome]="g.operacao === 'APLICADO' ? 'baixar' : 'subir'" [tamanho]="16" /></span>
                  <span>{{ g.titulo }}</span><span class="ces-num cont">{{ g.itens.length }}</span></h2>
                <ul class="ces-grupo resumo">
                  @for (m of g.itens; track chave(m)) {
                    <li><div class="ces-linha"><span class="ces-linha__texto"><span class="ces-linha__titulo">{{ m.descricao }}</span>
                      <span class="ces-linha__apoio ces-num">@if (ehOcorrencia()) {<span class="ces-cond" [class.ces-cond--sucata]="origem(m) === 'CONCESSIONARIA'">{{ origem(m) === 'CONCESSIONARIA' ? 'Concessionária' : 'Equipe' }}</span> · }{{ m.detalhaSerie ? m.numSeries.join(' · ') : 'Cód. ' + m.codigo }}</span></span>
                      <span class="qtd-fixa ces-num">{{ m.detalhaSerie ? m.numSeries.length : num(m.quantidade) }} <small>{{ m.unidadeMedida }}</small></span>
                    </div></li>
                  } @empty { <li><div class="ces-linha"><span class="ces-linha__apoio">{{ g.operacao === 'APLICADO' ? 'Nenhum material aplicado' : 'Nenhum material retirado' }}</span></div></li> }
                </ul>
              }
              @if (alterarId()) {
                <label class="ces-campo bloco motivo">
                  <span class="ces-campo__rotulo">{{ tipoNota() === 'OCORRENCIA' ? 'Número da ocorrência' : 'Número da nota' }} *</span>
                  <input id="numero-alteracao" class="ces-input" type="text" autocomplete="off" maxlength="100"
                    [attr.inputmode]="tipoNota() === 'OCORRENCIA' ? 'text' : 'numeric'"
                    [attr.autocapitalize]="tipoNota() === 'OCORRENCIA' ? 'characters' : null"
                    [value]="numeroAlteracao()" (input)="numeroAlteracao.set($any($event.target).value)"
                    [attr.aria-invalid]="faltaNumero() ? 'true' : null">
                  @if (faltaNumero()) { <span class="ces-campo__erro"><ces-icone nome="alerta" [tamanho]="16" />Digite o número da nota.</span> }
                  @else { <span class="ces-campo__ajuda">{{ tipoNota() === 'AVULSA' ? 'Trocar o provisório pelo número oficial também vale aqui.' : 'Corrija se foi digitado errado.' }}</span> }
                </label>
                <h2 class="ces-secao grupo-tit"><span class="marca" aria-hidden="true"><ces-icone nome="editar" [tamanho]="16" /></span>
                  <span>O que muda</span><span class="ces-num cont">{{ mudancas().length }}</span></h2>
                <ul class="ces-grupo resumo" aria-label="O que muda na nota">
                  @for (m of mudancas(); track $index) {
                    <li><div class="ces-linha"><span class="sinal" [class.sinal--sai]="m.sai" aria-hidden="true">{{ m.sai ? '−' : '+' }}</span>
                      <span class="ces-linha__texto"><span class="ces-linha__titulo">{{ m.texto }}</span>
                      <span class="ces-linha__apoio">{{ m.sai ? 'Sai da nota' : 'Entra na nota' }}</span></span></div></li>
                  } @empty { <li><div class="ces-linha"><span class="ces-linha__apoio">Nada mudou ainda. Corrija o número ou volte e ajuste os serviços ou os materiais.</span></div></li> }
                </ul>
                <label class="ces-campo bloco motivo">
                  <span class="ces-campo__rotulo">Motivo da alteração *</span>
                  <textarea id="motivo" class="ces-input texto" rows="3" maxlength="500" [value]="motivo()" (input)="motivo.set($any($event.target).value)"
                    [attr.aria-invalid]="faltaMotivo()" placeholder="Ex.: lancei o medidor errado, o certo é o trifásico"></textarea>
                  @if (faltaMotivo()) { <span class="ces-campo__erro"><ces-icone nome="alerta" [tamanho]="16" />Escreva por que a nota está sendo alterada.</span> }
                  @else { <span class="ces-campo__ajuda">O escritório vê o motivo no histórico da nota.</span> }
                </label>
              }
              <p class="dica"><ces-icone nome="nuvem" [tamanho]="18" />
                {{ rede.isOnline() ? 'Envia agora. Se o sinal cair, fica na fila e sai sozinho.' : 'Sem internet: fica salvo no aparelho e sai sozinho quando o sinal voltar.' }}
              </p>
              @if (erroEnvio()) {
                <p class="erro" role="alert"><ces-icone nome="alerta" [tamanho]="18" />{{ erroEnvio() }}</p>
              }
            }
          }
        </section>
      }
    </div>

    <footer class="rodape">
      @if (passo() > passoInicial()) {
        <button type="button" class="ces-btn ces-btn--neutro" (click)="voltarPasso()">Voltar</button>
      }
      <button type="button" class="ces-btn ces-btn--principal principal" (click)="avancar()" [disabled]="enviando() || carregando()">
        @if (passo() === total) {
          {{ enviando() ? 'Salvando…' : (alterarId() ? 'Salvar alteração' : editId() ? 'Salvar correção' : 'Lançar execução') }}
        } @else {
          Continuar <ces-icone nome="seta" [tamanho]="18" />
        }
      </button>
    </footer>

    <ces-folha [aberta]="!!materialDaFolha()" (fechar)="fecharSeries()" [titulo]="materialDaFolha()?.descricao ?? ''"
      [descricao]="materialDaFolha()?.operacao === 'APLICADO' && materialDaFolha()?.origem !== 'CONCESSIONARIA' ? 'Marque a série aplicada ou digite.' : (materialDaFolha()?.operacao === 'APLICADO' ? 'Digite a série aplicada.' : 'Digite a série retirada do cliente.')">
      @if (materialDaFolha(); as m) {
        <div class="series">
          @if (m.operacao === 'APLICADO' && origem(m) === 'EQUIPE' && seriaisDisponiveis(m.idMaterial).length) {
            <ul class="ces-grupo serie-lista">
              @for (s of seriaisDisponiveis(m.idMaterial); track s.id) {
                <li>
                  <button type="button" class="ces-linha ces-linha--acao" [attr.aria-pressed]="m.numSeries.includes(s.numSerie)" (click)="alternarSerie(m, s.numSerie)">
                    <span class="caixa" [class.marcada]="m.numSeries.includes(s.numSerie)" aria-hidden="true"><ces-icone nome="check" [tamanho]="16" /></span>
                    <span class="ces-linha__texto"><span class="ces-linha__titulo ces-num">{{ s.numSerie }}</span><span class="ces-linha__apoio" [attr.data-tom]="tomSerie(s)">{{ rotuloSerie(s) }}</span></span>
                  </button>
                </li>
              }
            </ul>
          }
          <form class="digitar" (submit)="$event.preventDefault(); adicionarSerieDigitada(m)">
            <input class="ces-input" type="text" autocapitalize="characters" autocomplete="off" enterkeyhint="done"
              placeholder="Digitar série" aria-label="Digitar número de série" [value]="serieDigitada()" (input)="serieDigitada.set($any($event.target).value)">
            <button type="submit" class="ces-btn ces-btn--tonal" [disabled]="!serieDigitada().trim()">Incluir</button>
          </form>
          @if (seriesForaDoSaldo(m).length) {
            <ul class="chips" aria-label="Séries digitadas">
              @for (s of seriesForaDoSaldo(m); track s) {
                <li><span class="ces-num">{{ s }}</span>
                  <button type="button" class="ces-btn-icone" (click)="alternarSerie(m, s)" [attr.aria-label]="'Tirar série ' + s"><ces-icone nome="fechar" [tamanho]="16" /></button></li>
              }
            </ul>
          }
        </div>
      }
      <div rodape class="folha-rodape"><button type="button" class="ces-btn ces-btn--principal ces-btn--bloco" (click)="fecharSeries()">Pronto</button></div>
    </ces-folha>

    <ces-folha [aberta]="confirmarSemGps()" (fechar)="confirmarSemGps.set(false)" titulo="Seguir sem o local?"
      descricao="O GPS não respondeu. Em campo aberto o registro fica mais confiável.">
      <div rodape class="folha-rodape duas">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="confirmarSemGps.set(false); capturarGps()">Tentar de novo</button>
        <button type="button" class="ces-btn ces-btn--principal" (click)="seguirSemGps()">Seguir sem</button>
      </div>
    </ces-folha>

    <ces-folha [aberta]="confirmarSemItens()" (fechar)="confirmarSemItens.set(false)" titulo="Lançar sem serviço e sem material?"
      descricao="Você não marcou nenhum serviço nem material nesta nota.">
      <div rodape class="folha-rodape duas">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="confirmarSemItens.set(false); irPara(2)">Marcar agora</button>
        <button type="button" class="ces-btn ces-btn--principal" (click)="lancarMesmoAssim()">Sim, lançar</button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100%; background: var(--ces-fundo); }
    /* Nada encolhe: com conteúdo maior que a tela, a página rola (grupos com overflow:hidden eram cortados). */
    :host > * { flex-shrink: 0; }
    .tela { flex: 1; padding-bottom: var(--ces-e4); }
    .passos { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; padding: 0 var(--ces-e5) var(--ces-e3); }
    .passos i { height: 4px; border-radius: var(--ces-r-pilula); background: var(--ces-linha); transition: background-color var(--ces-m-entrar) var(--ces-curva); }
    .passos i.feito { background: var(--ces-destaque); }
    .esqueletos { display: grid; gap: var(--ces-e3); padding: var(--ces-e4); }
    .conteudo { animation: entrar var(--ces-m-entrar) var(--ces-curva); }
    @keyframes entrar { from { opacity: 0; transform: translateY(6px); } }
    .bloco { margin: var(--ces-e2) var(--ces-e4) var(--ces-e4); }
    .tipos { display: grid; gap: var(--ces-e2); }
    .tipos__titulo { font: var(--ces-t-corpo-forte); color: var(--ces-texto); }
    .provisorio { margin: 0; }
    .provisorio__num { font: 700 1.375rem/1.2 var(--ces-fonte); overflow-wrap: anywhere; }
    dd.apoio { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .conferir .cod { color: var(--ces-texto-2); }
    .resumo-sel { padding: var(--ces-e2) var(--ces-e5) var(--ces-e1); font: var(--ces-t-corpo-forte); color: var(--ces-texto-2); }
    .busca { margin: var(--ces-e2) var(--ces-e3); display: flex; align-items: center; gap: var(--ces-e2); padding: 0 var(--ces-e4);
      min-height: 48px; border-radius: var(--ces-r-pilula); background: var(--ces-superficie); color: var(--ces-texto-2); }
    .busca input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ces-texto); font: var(--ces-t-corpo); font-size: 1rem; }
    .busca input:focus { outline: none; }
    .filtros { display: grid; gap: var(--ces-e1); padding: 0 var(--ces-e3); }
    .filtros .busca { margin: var(--ces-e1) 0 var(--ces-e2); }
    .caixa { width: 24px; height: 24px; border-radius: 7px; border: 2px solid var(--ces-linha-forte); display: grid; place-items: center;
      color: transparent; flex: none; transition: background-color var(--ces-m-entrar), border-color var(--ces-m-entrar); }
    .caixa.marcada { background: var(--ces-destaque); border-color: var(--ces-destaque); color: var(--ces-destaque-texto); }
    .tipo { display: flex; align-items: center; gap: var(--ces-e3); margin: var(--ces-e2) var(--ces-e3) var(--ces-e1); padding: var(--ces-e3) var(--ces-e4);
      border-radius: var(--ces-r-grupo); background: var(--ces-superficie); }
    .tipo__selo { width: 44px; height: 44px; border-radius: 12px; display: grid; place-items: center; flex: none; }
    .tipo--servico .tipo__selo { background: var(--ces-destaque-suave); color: var(--ces-destaque); }
    .tipo--material .tipo__selo { background: var(--ces-ok-suave); color: var(--ces-ok); }
    .tipo__txt { flex: 1; min-width: 0; display: grid; gap: 2px; }
    .tipo__txt strong { font: 700 1.0625rem/1.25 var(--ces-fonte); }
    .tipo__txt span { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .tipo__num { font: 700 1.75rem/1 var(--ces-fonte); min-width: 1.5ch; text-align: right; }
    .valor { margin: var(--ces-e1) var(--ces-e5) var(--ces-e2); font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .valor strong { font: 700 1.0625rem/1.3 var(--ces-fonte); color: var(--ces-texto); margin-left: 4px; }
    .grupo-tit { display: flex; align-items: center; gap: var(--ces-e2); }
    .grupo-tit .marca { width: 24px; height: 24px; border-radius: 50%; display: grid; place-items: center; background: var(--ces-ok-suave); color: var(--ces-ok); }
    .grupo-tit.retirado .marca { background: var(--ces-atencao-suave); color: var(--ces-atencao); }
    .grupo-tit.servico .marca { background: var(--ces-destaque-suave); color: var(--ces-destaque); }
    .grupo-tit .cont { margin-left: auto; font-weight: 700; color: var(--ces-texto); }
    .grupo-ajuda { margin: -4px var(--ces-e5) var(--ces-e2); font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .mat { border-left: 4px solid var(--ces-ok); }
    .mat--retirado { border-left-color: var(--ces-atencao); }
    .qtd-fixa { font: 700 1.5rem/1 var(--ces-fonte); white-space: nowrap; }
    .qtd-fixa small { font: 600 .875rem/1 var(--ces-fonte); color: var(--ces-texto-2); }
    .series-txt { margin: 0; font: var(--ces-t-apoio); color: var(--ces-texto-2); overflow-wrap: anywhere; }
    .saldo { display: grid; justify-items: end; font: 600 .875rem/1.2 var(--ces-fonte); color: var(--ces-texto-2); white-space: nowrap; }
    .saldo strong { font: 700 1.25rem/1.1 var(--ces-fonte); color: var(--ces-texto); }
    .saldo small { font: var(--ces-t-apoio); font-size: .75rem; }
    .saldo--zero small { color: var(--ces-atencao); font-weight: 600; }
    .op { gap: 4px; }
    .op--retirar { color: var(--ces-atencao); }
    .direita { font: var(--ces-t-corpo-forte); white-space: nowrap; }
    dd.grande { font: 700 1.375rem/1.2 var(--ces-fonte); }
    .resumo .ces-linha { min-height: 56px; }
    .mat { flex-direction: column; align-items: stretch; gap: var(--ces-e2); }
    .mat__topo { display: flex; align-items: flex-start; gap: var(--ces-e2); }
    .mat__topo .ces-btn-icone { margin: -8px -12px 0 0; color: var(--ces-texto-2); }
    .mat__acoes { display: flex; align-items: center; gap: var(--ces-e2); }
    .un { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .aviso { margin: 0; display: flex; gap: 6px; align-items: flex-start; font: var(--ces-t-apoio); color: var(--ces-atencao); }
    .cat { flex-wrap: wrap; row-gap: var(--ces-e2); }
    .cat .ces-linha__texto { flex: 1 1 55%; }
    .cat__botoes { display: flex; gap: var(--ces-e2); flex-basis: 100%; }
    .cat__botoes .ces-btn { flex: 1; min-height: 44px; }
    .conferir { margin: var(--ces-e2) var(--ces-e3) var(--ces-e2); padding: var(--ces-e2) var(--ces-e4); background: var(--ces-superficie); border-radius: var(--ces-r-grupo); }
    .conferir > div { padding: var(--ces-e3) 0; border-bottom: 1px solid var(--ces-linha); }
    .conferir > div:last-child { border-bottom: 0; }
    dt { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    dd { margin: 2px 0 0; font: var(--ces-t-corpo-forte); overflow-wrap: anywhere; }
    .mat-linha { display: block; }
    .dica, .erro { margin: var(--ces-e4) var(--ces-e5) 0; display: flex; gap: var(--ces-e2); align-items: flex-start; font: var(--ces-t-apoio); font-size: .875rem; }
    .dica { color: var(--ces-texto-2); }
    .motivo { margin: var(--ces-e4) var(--ces-e3) 0; }
    .motivo .texto { min-height: 96px; padding: var(--ces-e3) var(--ces-e4); resize: vertical; }
    .sinal { width: 24px; flex-shrink: 0; text-align: center; font: var(--ces-t-corpo-forte); color: var(--ces-ok); }
    .sinal--sai { color: var(--ces-erro); }
    .erro { color: var(--ces-erro); }
    .rodape { position: sticky; bottom: 0; z-index: 5; display: flex; gap: var(--ces-e3); padding: var(--ces-e3) var(--ces-e4) calc(var(--ces-e3) + var(--ces-safe-base));
      background: var(--ces-fundo); border-top: 1px solid var(--ces-linha); }
    .rodape .principal { flex: 1; min-height: var(--ces-alvo-principal); font-size: 1rem; }
    .series { display: grid; gap: var(--ces-e3); padding-bottom: var(--ces-e2); }
    .serie-lista { background: var(--ces-fundo); }
    .serie-lista [data-tom='atencao'] { color: var(--ces-atencao); font-weight: 600; }
    .serie-lista [data-tom='erro'] { color: var(--ces-erro); font-weight: 600; }
    .digitar { display: flex; gap: var(--ces-e2); padding: 0 var(--ces-e4); }
    .digitar .ces-input { flex: 1; min-height: var(--ces-alvo); }
    .chips { list-style: none; margin: 0; padding: 0 var(--ces-e4); display: flex; flex-wrap: wrap; gap: var(--ces-e2); }
    .chips li { display: inline-flex; align-items: center; gap: 2px; padding-left: 12px; border-radius: var(--ces-r-pilula); background: var(--ces-superficie-2); font: var(--ces-t-corpo-forte); }
    .chips .ces-btn-icone { width: 40px; height: 40px; }
    .folha-rodape { padding: var(--ces-e3) var(--ces-e4) 0; }
    .folha-rodape.duas { display: flex; gap: var(--ces-e3); }
    .folha-rodape.duas .ces-btn { flex: 1; }
  `],
})
export class NovaExecucaoComponent implements OnInit, OnDestroy {
  private readonly dbService = inject(DbService);
  private readonly tokenService = inject(TokenService);
  private readonly turnoService = inject(TurnoService);
  private readonly execucoesService = inject(ExecucoesService);
  private readonly toastService = inject(ToastService);
  readonly rede = inject(ConnectivityService);
  private readonly syncService = inject(SyncService);
  private readonly outboxService = inject(OutboxService);
  private readonly rascunhos = inject(RascunhoService);
  private readonly sessao = inject(SessaoService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly injector = inject(Injector);

  readonly total = TOTAL_ETAPAS;
  readonly etapas = [1, 2, 3, 4];
  readonly titulos = TITULOS;
  readonly opcoesAba = [
    { valor: 'saldo' as const, rotulo: 'No caminhão' },
    { valor: 'outros' as const, rotulo: 'Todos' },
  ];
  readonly opcoesTipoNota: { valor: TipoNota; rotulo: string }[] = [
    { valor: 'COMUM', rotulo: 'Comum' },
    { valor: 'AVULSA', rotulo: 'Avulsa' },
    { valor: 'OCORRENCIA', rotulo: 'Ocorrência' },
  ];
  readonly opcoesOrigem: { valor: OrigemMaterial; rotulo: string }[] = [
    { valor: 'CONCESSIONARIA', rotulo: 'Concessionária' },
    { valor: 'EQUIPE', rotulo: 'Equipe' },
  ];

  readonly editId = signal<string | null>(null);
  /** Nota já sincronizada sendo alterada: número, tipo e local não mudam. */
  readonly alterarId = signal<string | null>(null);
  readonly rotuloAlteracao = signal('nota');
  readonly motivo = signal('');
  /** Número da nota no modo alteração; o do servidor fica em {@link numeroServidor}. */
  readonly numeroAlteracao = signal('');
  private readonly numeroServidor = signal('');
  readonly faltaNumero = signal(false);
  /** Como a nota está no servidor, para mostrar o que a alteração muda. */
  private readonly versaoServidor = signal<{ acoes: string[]; materiais: ExecucaoMaterialLocal[] } | null>(null);
  readonly passoInicial = computed(() => (this.alterarId() ? 2 : 1));
  readonly faltaMotivo = computed(() => this.tentouAvancar() && !!this.alterarId() && this.passo() === TOTAL_ETAPAS && !this.motivo().trim());
  readonly carregando = signal(true);
  readonly enviando = signal(false);
  readonly erroEnvio = signal<string | null>(null);
  readonly tentouAvancar = signal(false);
  readonly confirmarSemGps = signal(false);
  readonly confirmarSemItens = signal(false);
  private semItensConfirmado = false;

  // Catálogos
  readonly acoesDisponiveis = signal<AcaoComercialLocal[]>([]);
  readonly tiposDisponiveis = signal<TipoServicoLocal[]>([]);
  readonly catalogo = signal<MaterialCatalogo[]>([]);
  readonly saldoSnapshot = signal<SaldoEquipeSnapshot | null>(null);
  private readonly seriaisPorMaterial = signal<Map<string, SaldoSerieLocal[]>>(new Map());

  // Estado do lançamento (vai inteiro para o rascunho)
  readonly passo = signal(1);
  readonly tipoNota = signal<TipoNota>('COMUM');
  readonly nota = signal('');
  readonly numeroAvulsa = signal('');
  readonly tipoServico = signal<string | null>(null);
  readonly latitude = signal<number | undefined>(undefined);
  readonly longitude = signal<number | undefined>(undefined);
  readonly precisao = signal<number | undefined>(undefined);
  readonly gpsPulado = signal(false);
  readonly acoes = signal<string[]>([]);
  readonly materiais = signal<SelectedMaterial[]>([]);
  readonly abaMaterial = signal<'saldo' | 'outros'>('saldo');
  readonly folhaSerie = signal<string | null>(null);

  // Estado efêmero de tela
  readonly estadoGps = signal<EstadoGps>('capturando');
  readonly buscaAcao = signal('');
  readonly buscaMaterial = signal('');
  readonly serieDigitada = signal('');
  private watchId: number | null = null;
  private rascunho: VinculoRascunho | null = null;

  readonly ehOcorrencia = computed(() => this.tipoNota() === 'OCORRENCIA');
  /** Número que vai no lançamento: o digitado, ou o provisório na nota avulsa. */
  readonly referencia = computed(() => (this.tipoNota() === 'AVULSA' ? this.numeroAvulsa() : this.nota().trim()));
  readonly rotuloTipoNota = computed(() => ({ COMUM: 'Nota', AVULSA: 'Nota avulsa', OCORRENCIA: 'Ocorrência' })[this.tipoNota()]);
  readonly ajudaTipoNota = computed(() => ({
    COMUM: 'Nota com número da OS ou do talão.',
    AVULSA: 'Nota achada em campo, ainda sem número.',
    OCORRENCIA: 'Ocorrência comercial. Material da Concessionária fica só no registro.',
  })[this.tipoNota()]);

  readonly opcoesTipo = computed<OpcaoSeletor[]>(() =>
    this.tiposDisponiveis().map(t => ({ valor: t.id, rotulo: t.descricao, apoio: `Código ${t.codigo}` })));
  readonly tipoEscolhido = computed(() => this.tiposDisponiveis().find(t => t.id === this.tipoServico()) ?? null);
  readonly faltaTipo = computed(() => this.tentouAvancar() && this.passo() === 1 && !this.tipoEscolhido());

  readonly temLocal = computed(() => this.latitude() !== undefined && this.longitude() !== undefined);
  readonly coords = computed(() => this.temLocal()
    ? `${this.latitude()!.toFixed(5).replace('.', ',')}, ${this.longitude()!.toFixed(5).replace('.', ',')}`
    : '');

  readonly acoesFiltradas = computed(() => {
    const q = this.buscaAcao().toLowerCase().trim();
    const lista = this.acoesDisponiveis();
    return q ? lista.filter(a => a.nome.toLowerCase().includes(q) || a.codigo.toLowerCase().includes(q)) : lista;
  });

  readonly valorEstimado = computed(() => {
    const mapa = new Map(this.acoesDisponiveis().map(a => [a.id, a]));
    const total = this.acoes().reduce((t, id) => t + (Number(mapa.get(id)?.valor) || 0), 0);
    return Math.round(total * 100) / 100;
  });

  readonly grupos = computed(() => {
    const ocorrencia = this.ehOcorrencia();
    return [
      { operacao: 'APLICADO' as const, titulo: 'Aplicado no cliente',
        ajuda: ocorrencia ? 'Concessionária fica só no registro. Equipe sai do saldo do caminhão.' : 'Instalado ou usado na nota. Sai do saldo do caminhão.',
        itens: this.materiais().filter(m => m.operacao === 'APLICADO') },
      { operacao: 'RETIRADO' as const, titulo: 'Retirado do cliente',
        ajuda: ocorrencia ? 'Concessionária fica só no registro. Equipe volta no caminhão como sucata.' : 'Tirado na nota. Volta no caminhão como sucata.',
        itens: this.materiais().filter(m => m.operacao === 'RETIRADO') },
    ];
  });
  readonly acoesEscolhidas = computed(() => {
    const mapa = new Map(this.acoesDisponiveis().map(a => [a.id, a]));
    return this.acoes().map(id => mapa.get(id)).filter((a): a is NonNullable<typeof a> => !!a);
  });

  /** Número no formato brasileiro (vírgula decimal). */
  num(n: number): string {
    return String(n).replace('.', ',');
  }

  readonly nomesAcoes = computed(() => {
    const mapa = new Map(this.acoesDisponiveis().map(a => [a.id, a.nome]));
    return this.acoes().map(id => mapa.get(id) ?? '—').join(', ');
  });

  readonly catalogoFiltrado = computed(() => {
    const q = this.buscaMaterial().toLowerCase().trim();
    const todos = this.catalogo();
    if (q) {
      return todos
        .filter(m => (m.descricao || '').toLowerCase().includes(q) || (m.codigo || '').toLowerCase().includes(q))
        .sort((a, b) => (b.saldo > 0 ? 1 : 0) - (a.saldo > 0 ? 1 : 0));
    }
    return this.abaMaterial() === 'saldo' ? todos.filter(m => m.saldo > 0) : todos.slice(0, 50);
  });

  readonly materiaisPayload = computed<ExecucaoMaterialLocal[]>(() => this.materiais().flatMap(m => m.detalhaSerie
    ? m.numSeries.map(numSerie => ({ idMaterial: m.idMaterial, quantidade: 1, operacao: m.operacao, condicao: m.condicao, numSerie, origem: origemDe(m) }))
    : [{ idMaterial: m.idMaterial, quantidade: m.quantidade, operacao: m.operacao, condicao: m.condicao, origem: origemDe(m) }]));

  /** Linhas da caixa "O que muda", com os nomes do catálogo do aparelho. */
  readonly mudancas = computed<{ texto: string; sai: boolean }[]>(() => {
    const servidor = this.versaoServidor();
    if (!servidor) return [];
    const d = diferencaDaNota(servidor, { acoes: this.acoes(), materiais: this.materiaisPayload() });
    const acoes = new Map(this.acoesDisponiveis().map(a => [a.id, a.nome]));
    const mats = new Map(this.catalogo().map(m => [m.idMaterial, m]));
    const material = (m: ExecucaoMaterialLocal) => {
      const c = mats.get(m.idMaterial);
      const qtd = String(m.quantidade).replace('.', ',');
      return `${c?.descricao ?? 'Material'} · ${m.numSerie ? 'série ' + m.numSerie : qtd + ' ' + (c?.unidadeMedida ?? 'UN')} ${m.operacao === 'APLICADO' ? 'aplicado' : 'retirado'}`;
    };
    const numero = this.numeroAlteracao().trim();
    const numeroMudou = !!numero && numero !== this.numeroServidor();
    return [
      ...(numeroMudou ? [{ texto: `Número ${numero}`, sai: false }, { texto: `Número ${this.numeroServidor()}`, sai: true }] : []),
      ...d.acoesAdicionadas.map(id => ({ texto: `Serviço ${acoes.get(id) ?? ''}`.trim(), sai: false })),
      ...d.acoesRemovidas.map(id => ({ texto: `Serviço ${acoes.get(id) ?? ''}`.trim(), sai: true })),
      ...d.materiaisAdicionados.map(m => ({ texto: material(m), sai: false })),
      ...d.materiaisRemovidos.map(m => ({ texto: material(m), sai: true })),
    ];
  });

  readonly faltasDeSaldo = computed<FaltaSaldo[]>(() => {
    const snapshot = this.saldoSnapshot();
    return snapshot ? preverFaltasDeSaldo(this.materiaisPayload(), snapshot) : [];
  });

  readonly materialDaFolha = computed(() => {
    const chave = this.folhaSerie();
    return chave ? this.materiais().find(m => chaveLinha(m) === chave) ?? null : null;
  });

  async ngOnInit(): Promise<void> {
    const alterarParam = this.route.snapshot.queryParamMap.get('alterarId');
    // Alterar nota já sincronizada não depende de turno aberto: a nota já tem o dela.
    await this.turnoService.loadTurnoAtivo();
    if (!alterarParam && !this.turnoService.turnoAtivo()) {
      this.toastService.warning('Abra o turno antes de lançar uma execução.');
      void this.router.navigate(['/turno/abrir']);
      return;
    }
    await this.carregarCatalogos();

    const editParam = this.route.snapshot.queryParamMap.get('editId');
    if (alterarParam && !(await this.carregarParaAlteracao(alterarParam))) return;
    if (!alterarParam && editParam && !(await this.carregarParaEdicao(editParam))) return;

    // O rascunho vence a versão gravada: é o que o usuário tinha na tela por último.
    this.rascunho = this.rascunhos.vincular<RascunhoExecucao>({
      chave: alterarParam ? `execucao:alterar:${alterarParam}` : editParam ? `execucao:editar:${editParam}` : 'execucao:nova',
      injector: this.injector,
      ler: () => this.estadoAtual(),
      aplicar: v => this.aplicarRascunho(v),
      titulo: () => (this.referencia()
        ? `${rotuloNota({ referenciaNota: this.referencia(), tipoNota: this.tipoNota() })} · passo ${this.passo()} de ${TOTAL_ETAPAS}`
        : `Passo ${this.passo()} de ${TOTAL_ETAPAS}`),
      rota: () => (alterarParam ? `/campo/nova?alterarId=${encodeURIComponent(alterarParam)}`
        : editParam ? `/campo/nova?editId=${encodeURIComponent(editParam)}` : '/campo/nova'),
      vazio: v => v.passo === 1 && (v.tipoNota ?? 'COMUM') === 'COMUM' && !v.nota.trim() && !v.tipoServico && !v.acoes.length && !v.materiais.length,
    });
    await this.rascunho.restaurado;

    for (const m of this.materiais()) if (m.detalhaSerie && m.operacao === 'APLICADO') void this.carregarSeriais(m.idMaterial);
    if (!this.temLocal() && !this.gpsPulado()) this.capturarGps();
    else if (this.temLocal()) this.estadoGps.set('ok');
    else this.estadoGps.set('falhou');
    this.carregando.set(false);
  }

  ngOnDestroy(): void {
    this.pararGps();
  }

  // ---------- rascunho ----------
  private estadoAtual(): RascunhoExecucao {
    return {
      passo: this.passo(), tipoNota: this.tipoNota(), nota: this.nota(), numeroAvulsa: this.numeroAvulsa(), tipoServico: this.tipoServico(), latitude: this.latitude(), longitude: this.longitude(), precisao: this.precisao(),
      gpsPulado: this.gpsPulado(), acoes: this.acoes(), materiais: this.materiais(), abaMaterial: this.abaMaterial(),
      folhaSerie: this.folhaSerie(), motivo: this.motivo(),
    };
  }

  private aplicarRascunho(v: RascunhoExecucao): void {
    this.passo.set(Math.min(Math.max(this.passoInicial(), v.passo || 1), TOTAL_ETAPAS));
    this.motivo.set(v.motivo ?? '');
    this.tipoNota.set(v.tipoNota === 'AVULSA' || v.tipoNota === 'OCORRENCIA' ? v.tipoNota : 'COMUM');
    this.nota.set(v.nota ?? '');
    this.numeroAvulsa.set(v.numeroAvulsa ?? '');
    // Só reaplica o tipo se ele ainda estiver disponível no aparelho.
    const tipo = v.tipoServico ?? null;
    if (tipo && this.tiposDisponiveis().some(t => t.id === tipo)) this.tipoServico.set(tipo);
    this.latitude.set(v.latitude);
    this.longitude.set(v.longitude);
    this.precisao.set(v.precisao);
    this.gpsPulado.set(!!v.gpsPulado);
    // Só reaplica o que ainda existe nos catálogos do aparelho.
    const acoesValidas = new Set(this.acoesDisponiveis().map(a => a.id));
    this.acoes.set((v.acoes ?? []).filter(id => acoesValidas.has(id)));
    this.materiais.set(v.materiais ?? []);
    this.abaMaterial.set(v.abaMaterial === 'outros' ? 'outros' : 'saldo');
    this.folhaSerie.set(v.folhaSerie ?? null);
  }

  fechar(): void {
    void this.rascunho?.gravarAgora();
    const temConteudo = !!(this.nota().trim() || this.tipoNota() !== 'COMUM' || this.tipoServico() || this.acoes().length || this.materiais().length);
    if (temConteudo) this.toastService.info('Rascunho guardado. Continue pela tela Hoje.');
    void this.router.navigateByUrl(this.sessao.rotaAnteriorFora('/campo/nova') ?? '/campo');
  }

  // ---------- catálogos ----------
  private async carregarCatalogos(): Promise<void> {
    const [acoes, tipos, materiais, snapshot] = await Promise.all([
      this.dbService.getAcoesAtivas(),
      this.dbService.getTiposServicoAtivos(),
      this.dbService.getMateriais(),
      this.execucoesService.carregarSaldoSnapshot(),
    ]);
    this.acoesDisponiveis.set(acoes);
    this.tiposDisponiveis.set(tipos);
    this.saldoSnapshot.set(snapshot);
    this.catalogo.set(materiais
      .map((m: MaterialLocal) => ({
        idMaterial: m.id, codigo: m.codigo, descricao: m.descricao, unidadeMedida: m.unidadeMedida || 'UN',
        detalhaSerie: !!m.detalhaSerie, saldo: snapshot.novoPorMaterial.get(m.id) ?? 0,
      }))
      .sort((a, b) => (a.descricao || '').localeCompare(b.descricao || '')));
  }

  /** Carrega uma execução local para correção. Falso quando ela já não pode ser editada. */
  private async carregarParaEdicao(id: string): Promise<boolean> {
    const exec = await this.dbService.getExecucaoById(id);
    if (!exec) {
      this.toastService.warning('Esta execução não está mais no aparelho.');
      void this.router.navigate(['/campo']);
      return false;
    }
    const fila = await this.dbService.getOutboxItemByExecucaoId(id);
    if (fila && !this.outboxService.canEdit(fila)) {
      this.toastService.error(fila.envioIniciadoEm && fila.status === 'PENDENTE'
        ? new CorrecaoRecusadaError().message
        : 'Este registro foi preservado e aguarda tratamento administrativo.');
      void this.router.navigate(['/campo']);
      return false;
    }
    this.editId.set(id);
    this.tipoNota.set(exec.tipoNota ?? 'COMUM');
    if (exec.tipoNota === 'AVULSA') this.numeroAvulsa.set(exec.referenciaProvisoria ?? exec.referenciaNota);
    else this.nota.set(exec.referenciaNota);
    // Notas antigas (sem tipo) ou com tipo desativado pedem a escolha de novo.
    const tipoDaNota = exec.tipoServico?.id ?? null;
    this.tipoServico.set(tipoDaNota && this.tiposDisponiveis().some(t => t.id === tipoDaNota) ? tipoDaNota : null);
    if (exec.latitude !== undefined && exec.longitude !== undefined) {
      this.latitude.set(exec.latitude);
      this.longitude.set(exec.longitude);
    }
    this.preencherItens(exec);
    return true;
  }

  /**
   * Carrega uma nota já sincronizada para alteração. Começa nos serviços: número, tipo
   * e local continuam os da nota. Falso quando ela não pode ser alterada pelo campo.
   */
  private async carregarParaAlteracao(id: string): Promise<boolean> {
    const exec = await this.dbService.getExecucaoById(id);
    if (!exec) {
      this.toastService.warning('Esta nota não está mais no aparelho. Sincronize e tente de novo.');
      void this.router.navigate(['/campo']);
      return false;
    }
    if (!podeAlterarNoCampo(exec, this.tokenService.equipeId())) {
      this.toastService.error(`Notas com mais de ${PRAZO_ALTERACAO_CAMPO_DIAS} dias só podem ser alteradas pelo escritório.`);
      void this.router.navigate(['/campo', id]);
      return false;
    }
    const fila = await this.dbService.getOutboxAlteracaoByExecucaoId(id);
    if (fila && !this.outboxService.canEdit(fila)) {
      this.toastService.error('A alteração anterior já está saindo. Espere o envio terminar para alterar de novo.');
      void this.router.navigate(['/campo', id]);
      return false;
    }
    this.alterarId.set(id);
    this.rotuloAlteracao.set(rotuloNota(exec).replace(/^\S+/, p => p.toLowerCase()));
    this.tipoNota.set(exec.tipoNota ?? 'COMUM');
    if (exec.tipoNota === 'AVULSA') this.numeroAvulsa.set(exec.referenciaProvisoria ?? exec.referenciaNota);
    this.nota.set(exec.referenciaNota);
    this.numeroAlteracao.set(exec.referenciaNota);
    this.numeroServidor.set(exec.alteracaoPendente?.anterior.referenciaNota ?? exec.referenciaNota);
    this.tipoServico.set(exec.tipoServico?.id ?? null);
    if (exec.latitude !== undefined && exec.longitude !== undefined) {
      this.latitude.set(exec.latitude);
      this.longitude.set(exec.longitude);
    }
    this.gpsPulado.set(true);
    const servidor = exec.alteracaoPendente?.anterior ?? { acoes: exec.acoes, materiais: exec.materiais };
    this.versaoServidor.set({ acoes: [...servidor.acoes], materiais: structuredClone(servidor.materiais) });
    this.motivo.set(exec.alteracaoPendente?.motivo ?? '');
    this.preencherItens(exec);
    this.passo.set(2);
    return true;
  }

  /** Serviços e materiais da nota no formato do formulário (séries agrupadas por linha). */
  private preencherItens(exec: ExecucaoCampoLocal): void {
    this.acoes.set([...exec.acoes]);
    const catalogo = new Map(this.catalogo().map(m => [m.idMaterial, m]));
    const grupos = new Map<string, SelectedMaterial>();
    for (const em of exec.materiais) {
      const cat = catalogo.get(em.idMaterial);
      const detalhaSerie = cat?.detalhaSerie || false;
      const chave = chaveLinha(em);
      const existente = grupos.get(chave);
      if (existente) {
        if (em.numSerie) existente.numSeries.push(em.numSerie);
        existente.quantidade = detalhaSerie ? existente.numSeries.length : existente.quantidade + em.quantidade;
      } else {
        grupos.set(chave, {
          idMaterial: em.idMaterial, codigo: cat?.codigo || 'N/A', descricao: cat?.descricao || 'Material',
          unidadeMedida: cat?.unidadeMedida || 'UN', detalhaSerie,
          quantidade: detalhaSerie ? (em.numSerie ? 1 : 0) : em.quantidade,
          operacao: em.operacao, condicao: em.condicao, numSeries: em.numSerie ? [em.numSerie] : [], origem: em.origem ?? 'EQUIPE',
        });
      }
    }
    this.materiais.set([...grupos.values()]);
  }

  escolherTipo(id: string | null): void {
    this.tipoServico.set(id);
  }

  escolherTipoNota(tipo: TipoNota): void {
    const anterior = this.tipoNota();
    if (tipo === anterior) return;
    this.tipoNota.set(tipo);
    if (tipo === 'AVULSA' && !this.numeroAvulsa()) void this.gerarNumeroAvulsa();
    // Material da Concessionária só existe na ocorrência: fora dela, volta a ser da equipe.
    if (anterior === 'OCORRENCIA') {
      for (const m of this.materiais()) if (origemDe(m) === 'CONCESSIONARIA') this.definirOrigem(m, 'EQUIPE');
    }
    // Na ocorrência o material costuma não estar no caminhão.
    if (tipo === 'OCORRENCIA') this.abaMaterial.set('outros');
  }

  private async gerarNumeroAvulsa(): Promise<void> {
    try {
      this.numeroAvulsa.set(await this.execucoesService.proximoNumeroAvulsa());
    } catch (err) {
      console.error('Falha ao gerar o número da nota avulsa:', err);
      this.toastService.error('Não foi possível gerar o número provisório. Tente de novo.');
    }
  }

  // ---------- navegação entre passos ----------
  irPara(passo: number): void {
    this.passo.set(passo);
  }

  voltarPasso(): void {
    if (this.passo() > this.passoInicial()) this.passo.update(p => p - 1);
  }

  avancar(): void {
    this.tentouAvancar.set(true);
    const p = this.passo();
    if (p === 1) {
      if (!this.referencia()) {
        if (this.tipoNota() === 'AVULSA') void this.gerarNumeroAvulsa();
        else (document.getElementById('nota') as HTMLInputElement | null)?.focus();
        return;
      }
      if (!this.tipoEscolhido()) {
        (document.querySelector('#tipo-servico button') as HTMLButtonElement | null)?.focus();
        return;
      }
      if (!this.temLocal() && !this.gpsPulado()) {
        this.confirmarSemGps.set(true);
        return;
      }
    }
    if (p === 3) {
      const semSerie = this.materiais().find(m => m.detalhaSerie && m.numSeries.length === 0);
      if (semSerie) {
        this.toastService.warning(`Informe a série de ${semSerie.descricao}.`);
        this.abrirSeries(semSerie);
        return;
      }
    }
    if (p === TOTAL_ETAPAS) {
      void this.enviar();
      return;
    }
    this.tentouAvancar.set(false);
    this.passo.set(p + 1);
  }

  // ---------- GPS (funciona offline) ----------
  capturarGps(): void {
    this.pararGps();
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      this.estadoGps.set('indisponivel');
      return;
    }
    this.gpsPulado.set(false);
    this.estadoGps.set(this.temLocal() ? 'ok' : 'capturando');
    this.watchId = navigator.geolocation.watchPosition(
      pos => {
        const atual = this.precisao();
        if (atual === undefined || pos.coords.accuracy <= atual || this.estadoGps() !== 'ok') {
          this.latitude.set(pos.coords.latitude);
          this.longitude.set(pos.coords.longitude);
          this.precisao.set(Math.round(pos.coords.accuracy));
        }
        this.estadoGps.set('ok');
      },
      () => {
        if (!this.temLocal()) this.estadoGps.set('falhou');
        this.pararGps();
      },
      { enableHighAccuracy: true, maximumAge: 30_000, timeout: 20_000 },
    );
  }

  pularGps(): void {
    this.pararGps();
    this.gpsPulado.set(true);
    if (!this.temLocal()) this.estadoGps.set('falhou');
  }

  seguirSemGps(): void {
    this.confirmarSemGps.set(false);
    this.pularGps();
    this.avancar();
  }

  private pararGps(): void {
    if (this.watchId !== null && typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.clearWatch(this.watchId);
    }
    this.watchId = null;
  }

  // ---------- serviços ----------
  marcada(id: string): boolean {
    return this.acoes().includes(id);
  }

  alternarAcao(id: string): void {
    this.acoes.update(l => (l.includes(id) ? l.filter(x => x !== id) : [...l, id]));
  }

  // ---------- materiais ----------
  /** Origem com que o material entra: na ocorrência, Concessionária; nos demais, a equipe. */
  private origemPadrao(): OrigemMaterial {
    return this.ehOcorrencia() ? 'CONCESSIONARIA' : 'EQUIPE';
  }

  origem(m: SelectedMaterial): OrigemMaterial {
    return origemDe(m);
  }

  chave(m: SelectedMaterial): string {
    return chaveLinha(m);
  }

  selecionado(id: string, op: Operacao): boolean {
    return this.materiais().some(m => mesmaLinha(m, { idMaterial: id, operacao: op, origem: this.origemPadrao() }));
  }

  /** Troca a origem; se já existe a mesma linha com a outra origem, junta as duas. */
  definirOrigem(m: SelectedMaterial, origem: OrigemMaterial): void {
    if (origemDe(m) === origem) return;
    this.materiais.update(l => {
      const destino = l.find(x => mesmaLinha(x, { ...m, origem }));
      if (!destino) return l.map(x => (mesmaLinha(x, m) ? { ...x, origem } : x));
      const numSeries = [...new Set([...destino.numSeries, ...m.numSeries])];
      const quantidade = destino.detalhaSerie ? numSeries.length : destino.quantidade + m.quantidade;
      return l.filter(x => !mesmaLinha(x, m)).map(x => (x === destino ? { ...x, numSeries, quantidade } : x));
    });
  }

  adicionar(c: MaterialCatalogo, operacao: Operacao): void {
    if (this.selecionado(c.idMaterial, operacao)) return;
    const novo: SelectedMaterial = {
      idMaterial: c.idMaterial, codigo: c.codigo, descricao: c.descricao, unidadeMedida: c.unidadeMedida, detalhaSerie: c.detalhaSerie,
      quantidade: c.detalhaSerie ? 0 : 1, operacao, condicao: operacao === 'APLICADO' ? 'NOVO' : 'SUCATA', numSeries: [],
      origem: this.origemPadrao(),
    };
    this.materiais.update(l => [...l, novo]);
    if (c.detalhaSerie) {
      if (operacao === 'APLICADO') void this.carregarSeriais(c.idMaterial);
      this.abrirSeries(novo);
    }
  }

  /**
   * Aplicado vira retirado e vice-versa, num toque, mantendo quantidade e séries. Se já
   * houver a outra linha do mesmo material, as duas se juntam. Série que passa a ser
   * aplicada e não está no caminhão segue valendo, com o aviso de fora do saldo.
   */
  inverterOperacao(m: SelectedMaterial): void {
    const operacao: Operacao = m.operacao === 'APLICADO' ? 'RETIRADO' : 'APLICADO';
    const alvo = { ...m, operacao };
    this.materiais.update(l => {
      const existente = l.find(x => mesmaLinha(x, alvo));
      const semAntiga = l.filter(x => !mesmaLinha(x, m));
      if (!existente) {
        return semAntiga.concat([{ ...m, operacao, condicao: operacao === 'APLICADO' ? 'NOVO' : 'SUCATA' }]);
      }
      return semAntiga.map(x => (mesmaLinha(x, alvo)
        ? (x.detalhaSerie
          ? { ...x, numSeries: [...new Set([...x.numSeries, ...m.numSeries])], quantidade: new Set([...x.numSeries, ...m.numSeries]).size }
          : { ...x, quantidade: x.quantidade + m.quantidade })
        : x));
    });
    if (operacao === 'APLICADO' && m.detalhaSerie) void this.carregarSeriais(m.idMaterial);
  }

  removerMaterial(m: SelectedMaterial): void {
    this.materiais.update(l => l.filter(x => !mesmaLinha(x, m)));
  }

  definirQuantidade(m: SelectedMaterial, qtd: number): void {
    if (!Number.isFinite(qtd) || qtd <= 0) return;
    this.materiais.update(l => l.map(x => (mesmaLinha(x, m) && !x.detalhaSerie ? { ...x, quantidade: qtd } : x)));
  }

  avisoSaldo(m: SelectedMaterial): string | null {
    if (m.operacao !== 'APLICADO' || origemDe(m) !== 'EQUIPE') return null;
    const doMaterial = this.faltasDeSaldo().filter(p => p.idMaterial === m.idMaterial);
    if (!doMaterial.length) return null;
    if (m.detalhaSerie) {
      return `Série fora do seu saldo (${doMaterial.map(p => p.numSerie).filter(Boolean).join(', ')}). O saldo fica negativo: avise seu gestor.`;
    }
    const falta = doMaterial.reduce((t, p) => t + p.quantidade, 0);
    return `Faltam ${String(falta).replace(".", ",")} ${m.unidadeMedida} no caminhão. Pode lançar: o saldo fica negativo. Avise o gestor.`;
  }

  // ---------- séries ----------
  abrirSeries(m: SelectedMaterial): void {
    this.serieDigitada.set('');
    this.folhaSerie.set(chaveLinha(m));
  }

  fecharSeries(): void {
    this.folhaSerie.set(null);
  }

  /** Há quanto tempo a série está no caminhão: a mais antiga aparece primeiro e deve sair antes. */
  rotuloSerie(s: SaldoSerieLocal): string {
    return rotuloSerieNoSaldo(diasDesde(s.noSaldoDesde));
  }

  tomSerie(s: SaldoSerieLocal): string {
    return tomTempoSaldo(diasDesde(s.noSaldoDesde));
  }

  seriaisDisponiveis(idMaterial: string): SaldoSerieLocal[] {
    return this.seriaisPorMaterial().get(idMaterial) || [];
  }

  seriesForaDoSaldo(m: SelectedMaterial): string[] {
    const doSaldo = new Set(this.seriaisDisponiveis(m.idMaterial).map(s => s.numSerie));
    return m.operacao === 'APLICADO' && origemDe(m) === 'EQUIPE' ? m.numSeries.filter(s => !doSaldo.has(s)) : m.numSeries;
  }

  alternarSerie(m: SelectedMaterial, numSerie: string): void {
    const atual = this.materiais().find(x => mesmaLinha(x, m));
    if (!atual?.numSeries.includes(numSerie)) {
      const outra = linhaComSerie(this.materiais().filter(x => !mesmaLinha(x, m)), numSerie);
      if (outra) {
        const como = outra.operacao === 'RETIRADO' ? 'retirada' : 'aplicada';
        this.toastService.warning(`A série ${numSerie} já está na nota como ${como} (${outra.descricao}). Cada série entra uma vez só.`);
        return;
      }
    }
    this.materiais.update(l => l.map(x => {
      if (!mesmaLinha(x, m)) return x;
      const numSeries = x.numSeries.includes(numSerie) ? x.numSeries.filter(s => s !== numSerie) : [...x.numSeries, numSerie];
      return { ...x, numSeries, quantidade: numSeries.length };
    }));
  }

  adicionarSerieDigitada(m: SelectedMaterial): void {
    const valor = this.serieDigitada().trim().toUpperCase();
    if (!valor) return;
    const doSaldo = this.seriaisDisponiveis(m.idMaterial).find(s => chaveSerie(m.idMaterial, s.numSerie) === chaveSerie(m.idMaterial, valor));
    const numSerie = doSaldo?.numSerie ?? valor;
    const atual = this.materiais().find(x => mesmaLinha(x, m));
    if (atual && !atual.numSeries.includes(numSerie)) this.alternarSerie(m, numSerie);
    this.serieDigitada.set('');
  }

  private async carregarSeriais(idMaterial: string): Promise<void> {
    if (this.seriaisPorMaterial().has(idMaterial)) return;
    const estoqueId = this.tokenService.estoqueId();
    if (!estoqueId) return;
    try {
      const seriais = await this.dbService.getSeriaisPorMaterial(estoqueId, idMaterial, 'NOVO');
      this.seriaisPorMaterial.update(map => new Map(map).set(idMaterial, seriais));
    } catch (err) {
      console.error('Falha ao carregar seriais do material:', err);
    }
  }

  // ---------- envio ----------
  /** Salva a alteração no aparelho e enfileira o envio; funciona sem internet. */
  private async enviarAlteracao(): Promise<void> {
    this.faltaNumero.set(!this.numeroAlteracao().trim());
    if (this.faltaNumero()) {
      (document.getElementById('numero-alteracao') as HTMLInputElement | null)?.focus();
      return;
    }
    if (!this.motivo().trim()) {
      (document.getElementById('motivo') as HTMLTextAreaElement | null)?.focus();
      return;
    }
    if (!this.mudancas().length) {
      this.erroEnvio.set('Nada mudou na nota. Corrija o número ou volte e ajuste os serviços ou os materiais.');
      return;
    }
    this.enviando.set(true);
    this.erroEnvio.set(null);
    try {
      await this.execucoesService.alterarExecucao(this.alterarId()!, {
        acoes: this.acoes(),
        materiais: this.materiaisPayload(),
        motivo: this.motivo(),
        referenciaNota: this.numeroAlteracao().trim(),
      });
      await this.rascunho?.concluir();
      const online = this.rede.isOnline();
      if (online) void this.syncService.syncOutbox();
      this.toastService.success(online ? 'Alteração salva. Enviando…' : 'Alteração salva no aparelho. Sai sozinha quando o sinal voltar.');
      void this.router.navigate(['/campo', this.alterarId()], { replaceUrl: true });
    } catch (err: any) {
      this.erroEnvio.set(err instanceof CorrecaoRecusadaError
        ? 'A alteração anterior já está saindo. Espere o envio terminar e altere de novo.'
        : err?.message || 'Não foi possível salvar a alteração. Seus dados continuam aqui.');
    } finally {
      this.enviando.set(false);
    }
  }

  lancarMesmoAssim(): void {
    this.confirmarSemItens.set(false);
    this.semItensConfirmado = true;
    void this.enviar();
  }

  private async enviar(): Promise<void> {
    if (this.enviando()) return;
    if (this.alterarId()) {
      await this.enviarAlteracao();
      return;
    }
    const tipo = this.tipoEscolhido();
    if (!tipo) {
      this.passo.set(1);
      this.tentouAvancar.set(true);
      return;
    }
    if (!this.acoes().length && !this.materiais().length && !this.semItensConfirmado) {
      this.confirmarSemItens.set(true);
      return;
    }
    this.enviando.set(true);
    this.erroEnvio.set(null);
    try {
      this.pararGps();
      await this.execucoesService.salvarExecucao(
        {
          referenciaNota: this.referencia(),
          tipoNota: this.tipoNota(),
          tipoServico: { id: tipo.id, codigo: tipo.codigo, descricao: tipo.descricao },
          acoes: this.acoes(),
          materiais: this.materiaisPayload(),
          latitude: this.latitude(),
          longitude: this.longitude(),
        },
        { editId: this.editId() },
      );
      // A execução e a intenção de envio já estão gravadas: o rascunho pode sair.
      await this.rascunho?.concluir();
      const online = this.rede.isOnline();
      if (online) void this.syncService.syncOutbox();
      this.toastService.success(online ? 'Execução lançada. Enviando…' : 'Salva no aparelho. Sai sozinha quando o sinal voltar.');
      void this.router.navigate(['/campo'], { replaceUrl: true });
    } catch (err: any) {
      const msg = err?.message || 'Não foi possível salvar a execução. Seus dados continuam aqui.';
      this.erroEnvio.set(msg);
    } finally {
      this.enviando.set(false);
    }
  }
}
