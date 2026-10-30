import { ChangeDetectionStrategy, Component, Injector, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, finalize, firstValueFrom, of, switchMap, tap } from 'rxjs';
import { AlmoxService } from '../../../core/almox/almox.service';
import { Condicao, Estoque, Material, MovimentacaoRequest, Saldo, SaldoSerie } from '../../../core/almox/almox.models';
import { CONDICOES, rotuloCondicao } from '../../../core/almox/almox-status';
import { contarSeries, extrairSerie } from '../../../core/almox/series';
import { ApiError } from '../../../core/http/error.interceptor';
import { RascunhoService, VinculoRascunho } from '../../../core/sessao/rascunho.service';
import { ConnectivityService } from '../../../core/sync/connectivity.service';
import { ToastService } from '../../../core/ui/toast.service';
import {
  AssinaturaComponent, BarraTopoComponent, CapturaSeriesComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, LeitorCodigoComponent,
  NomeIcone, OpcaoSeletor, QuantidadeComponent, RetornoLeitura, SeletorComponent, VazioComponent, VerificadorSeries, leitorPorCameraDisponivel, numeroBr,
} from '../../../ui';

type Tipo = 'entrada' | 'saida' | 'transferencia';

interface ItemMov {
  chave: string;
  idMaterial: string;
  codigo: string;
  descricao: string;
  unidade: string;
  detalhaSerie: boolean;
  condicao: Condicao;
  quantidade: number;
  series: string[];
  /** Saldo na origem (saída e transferência); null na entrada. */
  saldo: number | null;
}

interface RascunhoMov { passo: number; origem: string | null; destino: string | null; itens: ItemMov[] }

interface DefTipo { titulo: string; verbo: string; icone: NomeIcone; passos: string[]; botao: string; sucesso: string; intro: string }

const TIPOS: Record<Tipo, DefTipo> = {
  entrada: { titulo: 'Entrada de material', verbo: 'entrada', icone: 'entrada', passos: ['Estoque', 'Materiais', 'Conferir'], botao: 'Confirmar entrada', sucesso: 'Entrada registrada. O saldo do estoque já foi atualizado.', intro: 'Escolha o estoque que recebe o material. Depois você busca os materiais no catálogo.' },
  saida: { titulo: 'Saída de material', verbo: 'saída', icone: 'saida', passos: ['Estoque', 'Materiais', 'Conferir'], botao: 'Confirmar baixa', sucesso: 'Saída registrada. O saldo foi debitado.', intro: 'Escolha o estoque de onde o material sai. Só aparece o que tem saldo nele.' },
  transferencia: { titulo: 'Transferência', verbo: 'transferência', icone: 'transferencia', passos: ['Estoques', 'Materiais', 'Conferir', 'Assinaturas'], botao: 'Confirmar transferência', sucesso: 'Transferência registrada. O material já está no destino.', intro: 'Escolha de onde sai e para onde vai. Quem entrega e quem recebe assinam no fim.' },
};

const chaveDe = (idMaterial: string, condicao: string) => `${idMaterial}|${condicao}`;

/**
 * Entrada, saída (baixa) e transferência entre estoques, no mesmo padrão do web e no jeito
 * do celular: um passo por tela, busca no saldo ou no catálogo, números de série em massa
 * (câmera, leitor, colar, sequência) e — na saída e na transferência — leitura direta: a série
 * lida encontra sozinha o material no estoque de origem. Transferência exige as duas assinaturas.
 */
@Component({
  selector: 'app-nova-movimentacao',
  standalone: true,
  imports: [AssinaturaComponent, BarraTopoComponent, CapturaSeriesComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, LeitorCodigoComponent, QuantidadeComponent, SeletorComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tela">
      <ces-barra-topo [titulo]="def.titulo" [subtitulo]="def.passos[passo() - 1] + ' · ' + passo() + ' de ' + def.passos.length" navegacao="fechar" (voltar)="fechar()">
        <ces-indicador-rede />
      </ces-barra-topo>
      <div class="passos" [style.grid-template-columns]="'repeat(' + def.passos.length + ', 1fr)'" aria-hidden="true">
        @for (p of def.passos; track $index) { <i [class.feito]="$index < passo()"></i> }
      </div>

      <section class="conteudo" [attr.aria-label]="def.passos[passo() - 1]">
        @switch (passo()) {
          @case (1) {
            <p class="intro">{{ def.intro }}</p>
            <div class="campos">
              @if (tipo !== 'entrada') {
                <ces-seletor [rotulo]="tipo === 'saida' ? 'Estoque de onde sai' : 'Sai de'" [obrigatorio]="true" [opcoes]="opcoesEstoque()" [valor]="origem()"
                  (valorChange)="trocarOrigem($event)" [invalido]="tentou() && !origem()" [placeholder]="estoques().length ? 'Escolher' : 'Carregando…'" />
              }
              @if (tipo !== 'saida') {
                <ces-seletor [rotulo]="tipo === 'entrada' ? 'Estoque onde entra' : 'Vai para'" [obrigatorio]="true" [opcoes]="opcoesDestino()" [valor]="destino()"
                  (valorChange)="destino.set($event)" [invalido]="tentou() && !destino()" [placeholder]="estoques().length ? 'Escolher' : 'Carregando…'" />
              }
              @if (tipo === 'transferencia' && origem() && destino()) {
                <p class="resumo-rota"><ces-icone nome="transferencia" [tamanho]="18" />{{ nomeEstoque(origem()) }} → {{ nomeEstoque(destino()) }}</p>
              }
              @if (itens().length) { <p class="ajuda">Trocar o estoque de origem tira os {{ itens().length }} itens já escolhidos.</p> }
            </div>
          }
          @case (2) {
            @if (itens().length || tipo !== 'entrada') {
            <div class="totais ces-num"><span><strong>{{ itens().length }}</strong> {{ itens().length === 1 ? 'material' : 'materiais' }}</span>
              @if (totalSeries()) { <span>· {{ contar(totalSeries()) }}</span> }
              @if (tipo !== 'entrada') { <span class="de">de {{ nomeEstoque(origem()) }}</span> }</div>
            }

            @if (itens().length) {
              <h2 class="ces-secao"><span>Nesta {{ def.verbo }}</span>
                <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="tirarTudo()">{{ confirmandoTirar() ? 'Toque de novo' : 'Tirar todos' }}</button></h2>
              <ul class="ces-grupo escolhidos">
                @for (it of itens(); track it.chave; let i = $index) {
                  <li class="ces-linha item">
                    <span class="ces-linha__texto">
                      <span class="ces-linha__titulo">{{ it.descricao }}</span>
                      <span class="ces-linha__apoio ces-num">Cód. {{ it.codigo }}
                        @if (tipo === 'entrada') {
                          · <button type="button" class="cond" (click)="condicaoDe.set(i)" [attr.aria-label]="'Condição: ' + rotuloCondicao(it.condicao) + '. Trocar'">{{ rotuloCondicao(it.condicao) }} ▾</button>
                        } @else {
                          · <span class="ces-cond" [class.ces-cond--sucata]="it.condicao !== 'NOVO'">{{ rotuloCondicao(it.condicao) }}</span> · saldo {{ num(it.saldo ?? 0) }}
                        }
                      </span>
                    </span>
                    @if (it.detalhaSerie) {
                      <button type="button" class="series" [class.vazia]="!it.series.length" (click)="seriesDe.set(it.chave)">
                        <ces-icone nome="codigo_barras" [tamanho]="18" />{{ it.series.length ? contar(it.series.length) : 'Informar séries' }}</button>
                    } @else {
                      <ces-quantidade [valor]="it.quantidade" (valorChange)="definirQuantidade(i, $event)" [minimo]="0.001" [grande]="true" [unidade]="it.unidade"
                        [rotulo]="'quantidade de ' + it.descricao" />
                    }
                    <button type="button" class="ces-btn-icone tirar" (click)="remover(i)" [attr.aria-label]="'Tirar ' + it.descricao"><ces-icone nome="lixeira" [tamanho]="20" /></button>
                  </li>
                }
              </ul>
            }

            <h2 class="ces-secao"><span>{{ tipo === 'entrada' ? 'Catálogo de materiais' : 'Saldo do estoque' }}</span>
              @if (tipo !== 'entrada' && saldosFiltrados().length) { <span class="ces-num">{{ saldosFiltrados().length }}</span> }</h2>
            <div class="ferramentas">
              <div class="busca"><ces-icone nome="busca" [tamanho]="18" />
                <input type="search" placeholder="Nome ou código" enterkeyhint="search" [attr.aria-label]="tipo === 'entrada' ? 'Buscar no catálogo' : 'Buscar no saldo'"
                  [value]="busca()" (input)="busca.set($any($event.target).value)"></div>
              @if (tipo !== 'entrada') {
                <button type="button" class="ces-btn ces-btn--tonal ler" (click)="leitorAberto.set(true)" aria-label="Ler série pela câmera ou leitor"><ces-icone nome="codigo_barras" /></button>
              }
            </div>
            @if (tipo === 'transferencia' && saldos().length && !busca()) {
              <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto tudo" (click)="adicionarTudo()"><ces-icone nome="mais" [tamanho]="16" />Transferir todo o saldo sem série</button>
            }

            @if (tipo === 'entrada') {
              @if (buscando() && !catalogo().length) {
                <div class="ces-esqueletos">@for (i of [1, 2, 3]; track i) { <div class="ces-esqueleto" style="height: 60px"></div> }</div>
              } @else if (catalogo().length) {
                <ul class="ces-grupo" [class.atualizando]="buscando()">
                  @for (m of catalogo(); track m.id) {
                    <li><button type="button" class="ces-linha ces-linha--acao" (click)="adicionarMaterial(m)">
                      <span class="ces-linha__texto"><span class="ces-linha__titulo">{{ m.descricao }}</span>
                        <span class="ces-linha__apoio ces-num">Cód. {{ m.codigo }} · {{ m.unidadeMedida || 'UN' }}{{ m.detalhaSerie ? ' · com nº de série' : '' }}</span></span>
                      @if (temMaterial(m.id)) { <span class="ces-etiqueta ces-etiqueta--ok"><ces-icone nome="check" [tamanho]="13" />Na lista</span> }
                      @else { <span class="ces-btn ces-btn--tonal ces-btn--compacto" aria-hidden="true">Adicionar</span> }
                    </button></li>
                  }
                </ul>
                @if (temMaisCatalogo()) {
                  <button type="button" class="ces-btn ces-btn--neutro ces-btn--bloco mais" (click)="maisCatalogo()" [disabled]="buscando()">{{ buscando() ? 'Carregando…' : 'Mostrar mais' }}</button>
                }
              } @else {
                <ces-vazio icone="busca" [titulo]="busca() ? 'Nenhum material encontrado' : 'Busque o material'" [texto]="busca() ? 'Confira o nome ou o código.' : 'Digite o nome ou o código para ver o catálogo.'" />
              }
            } @else {
              @if (carregandoSaldos()) {
                <div class="ces-esqueletos">@for (i of [1, 2, 3]; track i) { <div class="ces-esqueleto" style="height: 64px"></div> }</div>
              } @else if (saldosFiltrados().length) {
                <ul class="ces-grupo">
                  @for (s of saldosFiltrados(); track s.id) {
                    @let escolhido = temChave(chave(s));
                    <li class="ces-linha-lista"><button type="button" class="ces-linha ces-linha--acao" (click)="adicionarSaldo(s)" [disabled]="escolhido && !s.material.detalhaSerie">
                      <span class="ces-linha__texto"><span class="ces-linha__titulo">{{ s.material.descricao }}</span>
                        <span class="ces-linha__apoio ces-num">Cód. {{ s.material.codigo }} · <span class="ces-cond" [class.ces-cond--sucata]="(s.condicao || 'NOVO') !== 'NOVO'">{{ rotuloCondicao(s.condicao) }}</span>{{ s.material.detalhaSerie ? ' · com série' : '' }}</span></span>
                      <span class="saldo ces-num"><strong>{{ num(s.quantidade) }}</strong><small>{{ s.material.unidadeMedida || 'UN' }}</small></span>
                      @if (escolhido) { <ces-icone nome="check" class="ok" /> }
                    </button></li>
                  }
                </ul>
              } @else {
                <ces-vazio icone="estoque" [titulo]="busca() ? 'Nada encontrado no saldo' : 'Estoque sem saldo'" [texto]="busca() ? 'Confira o nome ou o código.' : 'Não há material com saldo para movimentar.'" />
              }
            }
          }
          @case (3) {
            <div class="rota-final">
              @if (tipo !== 'entrada') { <span><small>Sai de</small>{{ nomeEstoque(origem()) }}</span> }
              @if (tipo !== 'saida') { <span><small>{{ tipo === 'entrada' ? 'Entra em' : 'Vai para' }}</small><strong>{{ nomeEstoque(destino()) }}</strong></span> }
            </div>
            @if (tipo === 'saida') {
              <div class="perigo"><ces-icone nome="alerta" /><p><strong>Baixa definitiva.</strong> O saldo é debitado na hora e a saída não pode ser desfeita.</p></div>
            }
            <h2 class="ces-secao"><span>Materiais</span><span class="ces-num">{{ itens().length }}</span></h2>
            <ul class="ces-grupo">
              @for (it of itens(); track it.chave) {
                <li class="ces-linha conferir">
                  <span class="ces-linha__texto"><span class="ces-linha__titulo">{{ it.descricao }}</span>
                    <span class="ces-linha__apoio ces-num">Cód. {{ it.codigo }} · {{ rotuloCondicao(it.condicao) }}</span>
                    @if (it.series.length) { <span class="lista-series">@for (s of it.series.slice(0, 12); track s) { <span>{{ s }}</span> }@if (it.series.length > 12) { <span>+{{ it.series.length - 12 }}</span> }</span> }
                  </span>
                  <span class="ces-qtd">{{ num(qtd(it)) }}<small>{{ it.unidade }}</small></span>
                </li>
              }
            </ul>
          }
          @case (4) {
            <p class="intro">Quem entrega e quem recebe assinam no aparelho. As duas assinaturas são obrigatórias.</p>
            <div class="assinaturas">
              <ces-assinatura rotulo="Quem entrega" [quem]="nomeEstoque(origem())" [valor]="assinaturaEntregador()" (valorChange)="assinaturaEntregador.set($event)" [invalida]="tentou()" />
              <ces-assinatura rotulo="Quem recebe" [quem]="nomeEstoque(destino())" [valor]="assinaturaRecebedor()" (valorChange)="assinaturaRecebedor.set($event)" [invalida]="tentou()" />
            </div>
          }
        }
      </section>
    </div>

    <footer class="rodape">
      @if (passo() > 1) { <button type="button" class="ces-btn ces-btn--neutro" (click)="passo.set(passo() - 1)" [disabled]="enviando()">Voltar</button> }
      <button type="button" class="ces-btn principal" [class.ces-btn--perigo]="ultimo() && tipo === 'saida'" [class.ces-btn--principal]="!(ultimo() && tipo === 'saida')"
        (click)="avancar()" [disabled]="enviando() || conferindoSeries() || (ultimo() && !rede.isOnline())">
        @if (ultimo()) { {{ enviando() ? 'Registrando…' : rede.isOnline() ? def.botao : 'Sem internet' }} }
        @else if (conferindoSeries()) { Conferindo séries… }
        @else { Continuar <ces-icone nome="seta" [tamanho]="18" /> }
      </button>
    </footer>

    <ces-captura-series [aberta]="!!seriesDe()" (abertaChange)="!$event && seriesDe.set(null)" [titulo]="itemSeries()?.descricao ?? ''"
      [disponiveis]="tipo === 'entrada' ? null : seriesDisponiveis()" [nomeEstoque]="nomeEstoque(origem())"
      [series]="itemSeries()?.series ?? []" (seriesChange)="definirSeries($event)" [verificar]="tipo === 'entrada' ? verificarSeries : null" />

    <ces-folha [aberta]="condicaoDe() !== null" (fechar)="condicaoDe.set(null)" titulo="Condição do material">
      <ul class="ces-grupo lista" role="radiogroup" aria-label="Condição">
        @for (c of condicoes; track c.valor) {
          <li><button type="button" role="radio" class="ces-linha ces-linha--acao" [attr.aria-checked]="itemCondicao()?.condicao === c.valor" (click)="definirCondicao(c.valor)">
            <span class="ces-linha__texto"><span class="ces-linha__titulo">{{ c.rotulo }}</span></span>
            @if (itemCondicao()?.condicao === c.valor) { <ces-icone nome="check" /> }</button></li>
        }
      </ul>
    </ces-folha>

    <ces-folha [aberta]="leitorAberto()" (fechar)="leitorAberto.set(false)" titulo="Ler séries" [descricao]="'Cada série lida entra no material certo, a partir do saldo de ' + nomeEstoque(origem()) + '.'">
      <div class="leitor">
        @if (temCamera) {
          <ces-leitor-codigo [ativo]="leitorAberto()" [retorno]="retorno()" (lido)="lerSerie($event)" />
        } @else {
          <p class="ajuda">Este navegador não lê pela câmera. Use um leitor de código conectado: ele digita aqui e confirma sozinho.</p>
        }
        <form class="digitar" (submit)="$event.preventDefault(); lerSerie(digitada()); digitada.set('')">
          <input class="ces-input mono" [value]="digitada()" (input)="digitada.set($any($event.target).value)" placeholder="Ou digite a série" aria-label="Série"
            autocapitalize="characters" autocomplete="off" spellcheck="false" enterkeyhint="send">
          <button type="submit" class="ces-btn ces-btn--principal" [disabled]="!digitada().trim() || localizando()" aria-label="Adicionar série"><ces-icone nome="mais" /></button>
        </form>
        @if (lidas().length) {
          <ul class="lidas" aria-label="Últimas leituras">
            @for (l of lidas(); track l.serie + l.em) { <li [attr.data-tom]="l.tom"><span class="mono">{{ l.serie }}</span><span>{{ l.texto }}</span></li> }
          </ul>
        }
      </div>
      <div rodape class="ces-folha-rodape"><button type="button" class="ces-btn ces-btn--principal" (click)="leitorAberto.set(false)">Pronto · {{ contar(totalSeries()) }}</button></div>
    </ces-folha>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100%; background: var(--ces-fundo); }
    :host > * { flex-shrink: 0; }
    .tela { flex: 1; padding-bottom: var(--ces-e5); }
    .passos { display: grid; gap: 4px; padding: 0 var(--ces-e5) var(--ces-e3); }
    .passos i { height: 4px; border-radius: var(--ces-r-pilula); background: var(--ces-linha); transition: background var(--ces-m-entrar); }
    .passos i.feito { background: var(--ces-destaque); }
    .conteudo { animation: entrar var(--ces-m-entrar) var(--ces-curva); }
    @keyframes entrar { from { opacity: 0; transform: translateY(6px); } }
    .campos { display: grid; gap: var(--ces-e2); padding: var(--ces-e2) var(--ces-e3); }
    .resumo-rota { display: flex; align-items: center; gap: var(--ces-e2); margin: var(--ces-e2) var(--ces-e2) 0; font: var(--ces-t-corpo-forte); color: var(--ces-texto-2); }
    .ajuda { margin: var(--ces-e1) var(--ces-e2) 0; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .totais { display: flex; flex-wrap: wrap; gap: 4px; padding: var(--ces-e2) var(--ces-e5) 0; font: var(--ces-t-apoio); font-size: .875rem; color: var(--ces-texto-2); }
    .totais strong { color: var(--ces-texto); }
    .totais .de { flex-basis: 100%; }
    .ces-secao .ces-btn { margin: -10px -8px -10px 0; color: var(--ces-erro); }
    .item { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: start; row-gap: var(--ces-e2); column-gap: var(--ces-e2); }
    .item .ces-linha__texto { grid-column: 1; grid-row: 1; }
    .item .tirar { grid-column: 2; grid-row: 1; margin: -8px -10px -8px 0; color: var(--ces-texto-3); }
    .item > ces-quantidade, .item > .series { grid-column: 1 / -1; grid-row: 2; justify-self: start; }
    .cond { border: 0; padding: 2px 6px; margin: -2px 0; border-radius: 6px; background: var(--ces-superficie-2); color: var(--ces-texto); font: 600 .8125rem/1.2 var(--ces-fonte); cursor: pointer; }
    .series { display: inline-flex; align-items: center; gap: 6px; min-height: 44px; padding: 0 14px; border-radius: var(--ces-r-pilula); border: 1.5px solid var(--ces-ok);
      background: transparent; color: var(--ces-texto); font: 600 .875rem/1 var(--ces-fonte); cursor: pointer; }
    .series ces-icone { color: var(--ces-ok); }
    .series.vazia { border-style: dashed; border-color: var(--ces-linha-forte); color: var(--ces-destaque); }
    .series.vazia ces-icone { color: var(--ces-destaque); }
    .ferramentas { display: flex; gap: var(--ces-e2); padding: 0 var(--ces-e3) var(--ces-e2); }
    .busca { flex: 1; display: flex; align-items: center; gap: var(--ces-e2); padding: 0 var(--ces-e4); min-height: 48px; border-radius: var(--ces-r-pilula);
      background: var(--ces-superficie); color: var(--ces-texto-2); }
    .busca input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ces-texto); font: var(--ces-t-corpo); font-size: 1rem; }
    .busca input:focus { outline: none; }
    .ler { width: 48px; padding: 0; border-radius: 50%; }
    .tudo { margin: 0 var(--ces-e3) var(--ces-e2); gap: 4px; }
    .atualizando { opacity: .6; }
    .mais { margin: var(--ces-e3) var(--ces-e3) 0; width: calc(100% - 24px); }
    .saldo { display: grid; justify-items: end; font: 700 1.125rem/1.1 var(--ces-fonte); }
    .saldo small { font: 500 .75rem/1 var(--ces-fonte); color: var(--ces-texto-2); }
    .ok { color: var(--ces-ok); }
    button.ces-linha:disabled { opacity: .6; }
    .rota-final { display: grid; gap: var(--ces-e2); margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e4); border-radius: var(--ces-r-grupo); background: var(--ces-superficie); }
    .rota-final span { display: grid; font: var(--ces-t-corpo); }
    .rota-final small { font: 500 .75rem/1.3 var(--ces-fonte); color: var(--ces-texto-2); }
    .perigo { display: flex; gap: var(--ces-e3); margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e3) var(--ces-e4); border-radius: var(--ces-r-grupo); background: var(--ces-erro-suave); }
    .perigo > ces-icone { color: var(--ces-erro); }
    .perigo p { margin: 0; font: var(--ces-t-apoio); font-size: .875rem; }
    .conferir { align-items: flex-start; }
    .lista-series { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }
    .lista-series span { padding: 2px 6px; border-radius: 6px; background: var(--ces-superficie-2); font: 600 .6875rem/1.4 var(--ces-fonte-mono); }
    .intro { margin: var(--ces-e2) var(--ces-e5) var(--ces-e3); font: var(--ces-t-apoio); font-size: .875rem; color: var(--ces-texto-2); }
    .assinaturas { display: grid; gap: var(--ces-e3); padding: 0 var(--ces-e3); }
    .rodape { position: sticky; bottom: 0; z-index: 5; display: flex; gap: var(--ces-e3); padding: var(--ces-e3) var(--ces-e4) calc(var(--ces-e3) + var(--ces-safe-base));
      background: var(--ces-fundo); border-top: 1px solid var(--ces-linha); }
    .rodape .ces-btn { min-height: var(--ces-alvo-principal); }
    .rodape .principal { flex: 1; font-size: 1rem; }
    .lista { background: var(--ces-fundo); }
    .mono { font-family: var(--ces-fonte-mono); }
    .leitor { display: grid; gap: var(--ces-e3); padding: 0 var(--ces-e4); }
    .digitar { display: flex; gap: var(--ces-e2); }
    .digitar .ces-input { flex: 1; min-width: 0; }
    .digitar .ces-btn { width: 54px; padding: 0; }
    .lidas { list-style: none; margin: 0; padding: 0; display: grid; gap: 4px; }
    .lidas li { display: flex; justify-content: space-between; gap: var(--ces-e2); padding: var(--ces-e2) var(--ces-e3); border-radius: var(--ces-r-campo); background: var(--ces-superficie-2);
      font: var(--ces-t-apoio); animation: entrar 160ms var(--ces-curva); }
    .lidas li span:last-child { text-align: right; color: var(--ces-texto-2); }
    .lidas li[data-tom='ok'] { background: var(--ces-ok-suave); }
    .lidas li[data-tom='erro'] { background: var(--ces-erro-suave); }
    .lidas li[data-tom='atencao'] { background: var(--ces-atencao-suave); }
  `],
})
export class NovaMovimentacaoComponent {
  private readonly almox = inject(AlmoxService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly rascunhos = inject(RascunhoService);
  private readonly injector = inject(Injector);
  readonly rede = inject(ConnectivityService);

  readonly tipo: Tipo = lerTipo(inject(ActivatedRoute).snapshot.paramMap.get('tipo'));
  readonly def = TIPOS[this.tipo];
  readonly condicoes = CONDICOES;
  readonly rotuloCondicao = rotuloCondicao;
  readonly temCamera = leitorPorCameraDisponivel();

  readonly passo = signal(1);
  readonly tentou = signal(false);
  readonly enviando = signal(false);
  readonly estoques = signal<Estoque[]>([]);
  readonly origem = signal<string | null>(null);
  readonly destino = signal<string | null>(null);
  readonly itens = signal<ItemMov[]>([]);
  readonly busca = signal('');
  readonly confirmandoTirar = signal(false);

  readonly saldos = signal<Saldo[]>([]);
  readonly carregandoSaldos = signal(false);
  private saldosDe: string | null = null;
  private readonly seriesPorChave = signal<Record<string, string[]>>({});

  readonly catalogo = signal<Material[]>([]);
  readonly buscando = signal(false);
  readonly temMaisCatalogo = signal(false);
  private readonly paginaCatalogo = signal(0);

  readonly seriesDe = signal<string | null>(null);
  readonly condicaoDe = signal<number | null>(null);
  readonly leitorAberto = signal(false);
  readonly localizando = signal(false);
  /** Leituras que chegaram enquanto a anterior era localizada: nenhuma se perde. */
  private filaLeitura: string[] = [];
  readonly conferindoSeries = signal(false);

  /** Entrada: séries que já estão ativas em algum estoque (o servidor recusaria a entrada inteira). */
  readonly verificarSeries: VerificadorSeries = async series => {
    const achadas = await firstValueFrom(this.almox.verificarSeries(series).pipe(catchError(() => of([] as SaldoSerie[]))));
    return new Map(achadas.map(s => [s.numSerie.toUpperCase(), `Já está cadastrada${s.estoque?.nomeIdentificador ? ' em ' + s.estoque.nomeIdentificador : ' no sistema'}.`]));
  };
  readonly digitada = signal('');
  readonly retorno = signal<RetornoLeitura | null>(null);
  readonly lidas = signal<{ serie: string; texto: string; tom: string; em: number }[]>([]);

  readonly assinaturaEntregador = signal<string | null>(null);
  readonly assinaturaRecebedor = signal<string | null>(null);
  private rascunho: VinculoRascunho | null = null;

  readonly ultimo = computed(() => this.passo() === this.def.passos.length);
  readonly opcoesEstoque = computed<OpcaoSeletor[]>(() => this.estoques().map(e => ({ valor: e.id, rotulo: e.nomeIdentificador, apoio: e.tipoEstoque?.nome })));
  readonly opcoesDestino = computed<OpcaoSeletor[]>(() => this.opcoesEstoque().filter(o => o.valor !== this.origem()));
  readonly totalSeries = computed(() => this.itens().reduce((a, i) => a + i.series.length, 0));
  readonly itemSeries = computed(() => this.itens().find(i => i.chave === this.seriesDe()) ?? null);
  readonly itemCondicao = computed(() => (this.condicaoDe() !== null ? this.itens()[this.condicaoDe()!] : null));
  readonly seriesDisponiveis = computed(() => (this.seriesDe() ? this.seriesPorChave()[this.seriesDe()!] ?? [] : []));
  readonly saldosFiltrados = computed(() => {
    const q = normalizar(this.busca());
    return q ? this.saldos().filter(s => normalizar(`${s.material.descricao} ${s.material.codigo}`).includes(q)) : this.saldos();
  });

  constructor() {
    this.almox.estoques().subscribe(e => this.estoques.set(e));
    if (this.tipo === 'entrada') {
      toObservable(this.busca).pipe(
        debounceTime(300), distinctUntilChanged(),
        tap(() => { this.paginaCatalogo.set(0); this.buscando.set(true); }),
        switchMap(q => this.almox.buscarMateriais(q, 0).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      ).subscribe(r => {
        this.buscando.set(false);
        this.catalogo.set(r?.content ?? []);
        this.temMaisCatalogo.set(!!r && (r.page?.number ?? 0) + 1 < (r.page?.totalPages ?? 0));
      });
    }
    void this.vincularRascunho();
  }

  private async vincularRascunho(): Promise<void> {
    this.rascunho = this.rascunhos.vincular<RascunhoMov>({
      chave: `almox:mov:${this.tipo}`,
      injector: this.injector,
      // Assinaturas ficam fora do rascunho: se o app fechar, colhe de novo.
      ler: () => ({ passo: Math.min(this.passo(), 3), origem: this.origem(), destino: this.destino(), itens: this.itens() }),
      aplicar: r => {
        this.origem.set(r.origem);
        this.destino.set(r.destino);
        this.itens.set(r.itens ?? []);
        this.passo.set(Math.min(Math.max(1, r.passo || 1), 3));
        if (this.passo() >= 2) this.carregarSaldos();
      },
      titulo: () => `${this.def.titulo} · ${this.itens().length} ${this.itens().length === 1 ? 'material' : 'materiais'}`,
      rota: () => `/almox/movimentacoes/nova/${this.tipo}`,
      vazio: r => !r.itens.length && !r.origem && !r.destino,
    });
    await this.rascunho.restaurado;
  }

  nomeEstoque(id: string | null): string {
    return this.estoques().find(e => e.id === id)?.nomeIdentificador ?? '—';
  }

  num(n: number): string {
    return numeroBr(n);
  }

  contar(n: number): string {
    return contarSeries(n);
  }

  chave(s: Saldo): string {
    return chaveDe(s.material.id, s.condicao || 'NOVO');
  }

  temChave(c: string): boolean {
    return this.itens().some(i => i.chave === c);
  }

  temMaterial(id: string): boolean {
    return this.itens().some(i => i.idMaterial === id);
  }

  qtd(it: ItemMov): number {
    return it.detalhaSerie ? it.series.length : it.quantidade;
  }

  trocarOrigem(id: string | null): void {
    if (id === this.origem()) return;
    this.origem.set(id);
    if (this.destino() === id) this.destino.set(null);
    if (this.itens().length) { this.itens.set([]); this.toast.info('Os itens escolhidos saíram: o estoque de origem mudou.'); }
    this.saldos.set([]);
    this.saldosDe = null;
    this.seriesPorChave.set({});
  }

  // ---------- itens ----------
  private carregarSaldos(): void {
    if (this.tipo === 'entrada' || !this.origem() || this.saldosDe === this.origem()) return;
    const origem = this.origem()!;
    this.saldosDe = origem;
    this.carregandoSaldos.set(true);
    this.almox.listarSaldos(origem).subscribe({
      next: s => {
        this.saldos.set([...s].sort((a, b) => a.material.descricao.localeCompare(b.material.descricao, 'pt-BR')));
        this.carregandoSaldos.set(false);
        // Saldo pode ter mudado desde o rascunho: ajusta o limite de cada item.
        this.itens.update(l => l.map(i => ({ ...i, saldo: this.saldoDe(i.chave) })));
      },
      error: () => { this.carregandoSaldos.set(false); this.saldosDe = null; this.toast.error('Não foi possível carregar o saldo do estoque.'); },
    });
  }

  private saldoDe(chave: string): number {
    return Number(this.saldos().find(s => this.chave(s) === chave)?.quantidade ?? 0);
  }

  adicionarSaldo(s: Saldo): void {
    const c = this.chave(s);
    if (!this.temChave(c)) {
      this.itens.update(l => [...l, {
        chave: c, idMaterial: s.material.id, codigo: s.material.codigo, descricao: s.material.descricao, unidade: s.material.unidadeMedida || 'UN',
        detalhaSerie: !!s.material.detalhaSerie, condicao: (s.condicao || 'NOVO') as Condicao, quantidade: s.material.detalhaSerie ? 0 : Math.min(1, Number(s.quantidade)),
        series: [], saldo: Number(s.quantidade),
      }]);
    }
    if (s.material.detalhaSerie) this.abrirSeries(c);
  }

  adicionarTudo(): void {
    const novos = this.saldos().filter(s => !s.material.detalhaSerie && !this.temChave(this.chave(s)));
    if (!novos.length) { this.toast.info('Todo o saldo sem série já está na lista.'); return; }
    this.itens.update(l => [...l, ...novos.map(s => ({
      chave: this.chave(s), idMaterial: s.material.id, codigo: s.material.codigo, descricao: s.material.descricao, unidade: s.material.unidadeMedida || 'UN',
      detalhaSerie: false, condicao: (s.condicao || 'NOVO') as Condicao, quantidade: Number(s.quantidade), series: [], saldo: Number(s.quantidade),
    }))]);
    const comSerie = this.saldos().filter(s => s.material.detalhaSerie).length;
    this.toast.success(`${novos.length} ${novos.length === 1 ? 'material entrou' : 'materiais entraram'} com todo o saldo.${comSerie ? ' Os com série escolha ou leia à parte.' : ''}`);
  }

  adicionarMaterial(m: Material): void {
    const c = chaveDe(m.id, 'NOVO');
    if (!this.temChave(c)) {
      this.itens.update(l => [...l, {
        chave: c, idMaterial: m.id, codigo: m.codigo, descricao: m.descricao, unidade: m.unidadeMedida || 'UN', detalhaSerie: !!m.detalhaSerie,
        condicao: 'NOVO', quantidade: m.detalhaSerie ? 0 : 1, series: [], saldo: null,
      }]);
    }
    if (m.detalhaSerie) this.abrirSeries(c);
  }

  maisCatalogo(): void {
    const p = this.paginaCatalogo() + 1;
    this.buscando.set(true);
    this.almox.buscarMateriais(this.busca(), p).pipe(catchError(() => of(null))).subscribe(r => {
      this.buscando.set(false);
      if (!r) return;
      this.paginaCatalogo.set(p);
      this.catalogo.update(l => [...l, ...(r.content ?? [])]);
      this.temMaisCatalogo.set((r.page?.number ?? 0) + 1 < (r.page?.totalPages ?? 0));
    });
  }

  private abrirSeries(chave: string): void {
    this.seriesDe.set(chave);
    if (this.tipo === 'entrada' || this.seriesPorChave()[chave]) return;
    const it = this.itens().find(i => i.chave === chave);
    if (!it || !this.origem()) return;
    this.almox.listarSeries(this.origem()!, it.idMaterial, it.condicao).pipe(catchError(() => of([]))).subscribe(s => {
      this.seriesPorChave.update(m => ({ ...m, [chave]: s.map(x => x.numSerie) }));
    });
  }

  definirSeries(series: string[]): void {
    const c = this.seriesDe();
    if (!c) return;
    this.itens.update(l => l.map(i => (i.chave === c ? { ...i, series, quantidade: series.length } : i)));
  }

  definirQuantidade(i: number, q: number): void {
    const it = this.itens()[i];
    if (it.saldo !== null && q > it.saldo) { this.toast.warning(`O saldo é ${numeroBr(it.saldo)} ${it.unidade}.`); return; }
    this.itens.update(l => l.map((x, k) => (k === i ? { ...x, quantidade: q } : x)));
  }

  definirCondicao(c: Condicao): void {
    const i = this.condicaoDe();
    if (i === null) return;
    const it = this.itens()[i];
    const nova = chaveDe(it.idMaterial, c);
    if (nova !== it.chave && this.temChave(nova)) { this.toast.warning(`${it.descricao} já está na lista como ${rotuloCondicao(c)}.`); return; }
    this.itens.update(l => l.map((x, k) => (k === i ? { ...x, condicao: c, chave: nova } : x)));
    this.condicaoDe.set(null);
  }

  remover(i: number): void {
    this.itens.update(l => l.filter((_, k) => k !== i));
  }

  tirarTudo(): void {
    if (!this.confirmandoTirar()) { this.confirmandoTirar.set(true); setTimeout(() => this.confirmandoTirar.set(false), 3000); return; }
    this.itens.set([]);
    this.confirmandoTirar.set(false);
  }

  /** Leitura direta (saída e transferência): a série lida encontra o material no saldo da origem. */
  lerSerie(bruta: string): void {
    const s = extrairSerie(bruta);
    const origem = this.origem();
    if (!s || !origem) return;
    if (this.localizando()) {
      if (!this.filaLeitura.includes(s)) this.filaLeitura.push(s);
      return;
    }
    const registrar = (texto: string, tom: 'ok' | 'erro' | 'atencao') => {
      this.retorno.set({ texto: `${s}: ${texto}`, tom });
      this.lidas.update(l => [{ serie: s, texto, tom, em: Date.now() }, ...l].slice(0, 8));
    };
    if (this.itens().some(i => i.series.includes(s))) { registrar('já está na lista', 'atencao'); return; }
    this.localizando.set(true);
    this.almox.localizarSerie(origem, s).pipe(catchError(() => of(null)), finalize(() => {
      this.localizando.set(false);
      const proxima = this.filaLeitura.shift();
      if (proxima) this.lerSerie(proxima);
    })).subscribe(serie => {
      if (!serie) { registrar(`não está em ${this.nomeEstoque(origem)}`, 'erro'); return; }
      const c = chaveDe(serie.material.id, serie.condicao || 'NOVO');
      const saldo = this.saldos().find(x => this.chave(x) === c);
      if (!this.temChave(c)) {
        this.itens.update(l => [...l, {
          chave: c, idMaterial: serie.material.id, codigo: serie.material.codigo, descricao: serie.material.descricao, unidade: serie.material.unidadeMedida || 'UN',
          detalhaSerie: true, condicao: (serie.condicao || 'NOVO') as Condicao, quantidade: 0, series: [], saldo: Number(saldo?.quantidade ?? 0),
        }]);
      }
      this.itens.update(l => l.map(i => (i.chave === c ? { ...i, series: [...i.series, s], quantidade: i.series.length + 1 } : i)));
      registrar(serie.material.descricao, 'ok');
    });
  }

  // ---------- fluxo ----------
  avancar(): void {
    this.tentou.set(true);
    const p = this.passo();
    if (p === 1) {
      if ((this.tipo !== 'entrada' && !this.origem()) || (this.tipo !== 'saida' && !this.destino())) { this.toast.warning('Escolha o estoque para continuar.'); return; }
      this.carregarSaldos();
    }
    if (p === 2) {
      if (!this.itens().length) { this.toast.warning('Adicione ao menos um material.'); return; }
      const semSerie = this.itens().find(i => i.detalhaSerie && !i.series.length);
      if (semSerie) { this.toast.warning(`Informe as séries de ${semSerie.descricao}.`); this.abrirSeries(semSerie.chave); return; }
      const excedido = this.itens().find(i => i.saldo !== null && this.qtd(i) > i.saldo);
      if (excedido) { this.toast.warning(`${excedido.descricao}: passa do saldo (${numeroBr(excedido.saldo!)} ${excedido.unidade}).`); return; }
      const zerado = this.itens().find(i => this.qtd(i) <= 0);
      if (zerado) { this.toast.warning(`Informe a quantidade de ${zerado.descricao}.`); return; }
      if (this.tipo === 'entrada' && this.itens().some(i => i.series.length)) { void this.conferirSeriesEAvancar(); return; }
    }
    if (p === 4 && !(this.assinaturaEntregador() && this.assinaturaRecebedor())) { this.toast.warning('Colha as duas assinaturas para confirmar.'); return; }
    if (this.ultimo()) { this.confirmar(); return; }
    this.tentou.set(false);
    this.passo.set(p + 1);
  }

  /** Antes de seguir na entrada: série que já está no sistema faria o servidor recusar tudo no fim. */
  private async conferirSeriesEAvancar(): Promise<void> {
    if (this.conferindoSeries()) return;
    this.conferindoSeries.set(true);
    const todas = this.itens().flatMap(i => i.series);
    const achadas = await this.verificarSeries(todas);
    this.conferindoSeries.set(false);
    const item = this.itens().find(i => i.series.some(s => achadas.has(s)));
    if (item) {
      const repetidas = item.series.filter(s => achadas.has(s));
      this.toast.warning(`${repetidas.length === 1 ? `A série ${repetidas[0]} já está` : `${repetidas.length} séries de ${item.descricao} já estão`} no sistema. Confira a lista.`);
      this.abrirSeries(item.chave);
      return;
    }
    this.tentou.set(false);
    this.passo.update(p => p + 1);
  }

  private confirmar(): void {
    if (this.enviando()) return;
    const itens = this.itens().flatMap(i => (i.detalhaSerie
      ? i.series.map(numSerie => ({ idMaterial: i.idMaterial, quantidade: 1, numSerie, condicao: i.condicao }))
      : [{ idMaterial: i.idMaterial, quantidade: i.quantidade, condicao: i.condicao }]));
    const pedido: MovimentacaoRequest = {
      idEstoqueOrigem: this.tipo !== 'entrada' ? this.origem()! : undefined,
      idEstoqueDestino: this.tipo !== 'saida' ? this.destino()! : undefined,
      itens,
      assinaturaEntregador: this.tipo === 'transferencia' ? this.assinaturaEntregador()! : undefined,
      assinaturaRecebedor: this.tipo === 'transferencia' ? this.assinaturaRecebedor()! : undefined,
    };
    const chamada = this.tipo === 'entrada' ? this.almox.registrarEntrada(pedido) : this.tipo === 'saida' ? this.almox.registrarSaida(pedido) : this.almox.registrarTransferencia(pedido);
    this.enviando.set(true);
    chamada.subscribe({
      next: async () => {
        this.enviando.set(false);
        await this.rascunho?.concluir();
        this.toast.success(this.def.sucesso);
        void this.router.navigate(['/almox/movimentacoes'], { replaceUrl: true });
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        this.toast.error(e.detail || `Não foi possível registrar a ${this.def.verbo}. Os dados continuam aqui.`);
        // Saldo pode ter mudado: recarrega para a próxima tentativa.
        this.saldosDe = null;
        this.carregarSaldos();
      },
    });
  }

  fechar(): void {
    void this.rascunho?.gravarEstadoAtual();
    if (this.itens().length) this.toast.info('Rascunho guardado. Continue por Movimentações.');
    void this.router.navigate(['/almox/movimentacoes'], { replaceUrl: true });
  }
}

function lerTipo(v: string | null): Tipo {
  return v === 'saida' || v === 'transferencia' ? v : 'entrada';
}

function normalizar(v: string): string {
  return (v || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}
