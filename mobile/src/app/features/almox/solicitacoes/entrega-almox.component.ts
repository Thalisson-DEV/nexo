import { ChangeDetectionStrategy, Component, Injector, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, forkJoin, map, of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { TokenService } from '../../../core/auth/token.service';
import { AlmoxService } from '../../../core/almox/almox.service';
import { Solicitacao } from '../../../core/almox/almox.models';
import { idCurto, pode } from '../../../core/almox/almox-status';
import { extrairSerie, normalizarSerie } from '../../../core/almox/series';
import { ApiError } from '../../../core/http/error.interceptor';
import { RascunhoService, VinculoRascunho } from '../../../core/sessao/rascunho.service';
import { ConnectivityService } from '../../../core/sync/connectivity.service';
import { ToastService } from '../../../core/ui/toast.service';
import {
  AssinaturaComponent, BarraTopoComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, LeitorCodigoComponent, OpcaoSeletor,
  QuantidadeComponent, RetornoLeitura, SeletorComponent, VazioComponent, leitorPorCameraDisponivel, numeroBr,
} from '../../../ui';

interface ItemEntrega {
  id: string;
  idMaterial: string;
  codigo: string;
  descricao: string;
  unidade: string;
  detalhaSerie: boolean;
  pedido: number;
  pendente: number;
  /** Quanto sai nesta entrega (com série: 0 ou 1). */
  quantidade: number;
  serie: string;
}

interface RascunhoEntrega {
  passo: number;
  itens: { id: string; quantidade: number; serie: string }[];
  foraEquipe: boolean;
  responsavel: string | null;
}

const PASSOS = ['Materiais', 'Quem retira', 'Assinaturas', 'Conferir'];

/**
 * Registrar a entrega (total ou parcial) de uma solicitação aprovada. A assinatura é sempre
 * digital: quem entrega (almoxarifado) e quem retira assinam no aparelho. Itens com número de
 * série saem uma unidade por entrega (regra do backend); a série pode ser lida pela câmera e cai
 * sozinha no item certo.
 */
@Component({
  selector: 'app-entrega-almox',
  standalone: true,
  imports: [AssinaturaComponent, BarraTopoComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, LeitorCodigoComponent, QuantidadeComponent, SeletorComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tela">
      <ces-barra-topo titulo="Registrar entrega" [subtitulo]="'#' + idCurto() + ' · ' + PASSOS[passo() - 1] + ' · ' + passo() + ' de 4'" navegacao="fechar"
        rotuloFechar="Fechar a entrega" (voltar)="fechar()">
        <ces-indicador-rede />
      </ces-barra-topo>
      <div class="passos" aria-hidden="true">@for (s of [1, 2, 3, 4]; track s) { <i [class.feito]="s <= passo()"></i> }</div>

      @if (carregando()) {
        <div class="ces-esqueletos" aria-busy="true"><div class="ces-esqueleto" style="height: 64px"></div><div class="ces-esqueleto" style="height: 220px"></div></div>
      } @else if (erro()) {
        <ces-vazio icone="alerta" titulo="Não foi possível preparar a entrega" [texto]="erro()">
          <button type="button" class="ces-btn ces-btn--tonal" (click)="carregar()">Tentar de novo</button>
        </ces-vazio>
      } @else if (det(); as d) {
        <section class="conteudo" [attr.aria-label]="PASSOS[passo() - 1]">
          @switch (passo()) {
            @case (1) {
              <div class="rota"><span class="origem">{{ d.estoqueOrigem.nomeIdentificador }}</span><ces-icone nome="seta" [tamanho]="16" /><strong class="destino">{{ d.estoqueDestino.nomeIdentificador }}</strong></div>
              <div class="barra-itens">
                <span class="ces-num"><strong>{{ marcados() }}</strong> de {{ itens().length }} {{ itens().length === 1 ? 'material' : 'materiais' }} nesta entrega</span>
                <span class="botoes">
                  <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="preencherTudo()">Tudo o que falta</button>
                  <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto" (click)="zerar()" [disabled]="!marcados()">Zerar</button>
                </span>
              </div>
              @if (temSerie()) {
                <button type="button" class="ces-btn ces-btn--tonal ces-btn--bloco ler" (click)="leitorAberto.set(true)">
                  <ces-icone nome="codigo_barras" />Ler as séries pela câmera
                </button>
              }
              <ul class="ces-grupo itens">
                @for (it of itens(); track it.id; let i = $index) {
                  <li class="ces-linha item" [class.zerado]="!it.quantidade">
                    <span class="ces-linha__texto">
                      <span class="ces-linha__titulo">{{ it.descricao }}</span>
                      <span class="ces-linha__apoio ces-num">Cód. {{ it.codigo }} · falta {{ num(it.pendente) }} de {{ num(it.pedido) }} {{ it.unidade }}</span>
                    </span>
                    @if (it.detalhaSerie) {
                      <button type="button" class="serie" [class.escolhida]="!!it.serie" (click)="escolherSerie(i)" [attr.aria-label]="it.serie ? 'Série ' + it.serie + ', trocar' : 'Escolher a série de ' + it.descricao">
                        <ces-icone [nome]="it.serie ? 'check' : 'codigo_barras'" [tamanho]="18" />
                        <span class="ces-num">{{ it.serie || 'Escolher série' }}</span>
                      </button>
                      <span class="nota">Sai 1 unidade por entrega · {{ (series()[it.idMaterial] ?? []).length }} na origem</span>
                    } @else {
                      <ces-quantidade [valor]="it.quantidade" (valorChange)="definirQuantidade(i, $event)" [minimo]="0" [grande]="true" [unidade]="it.unidade"
                        [rotulo]="'quantidade de ' + it.descricao + ' nesta entrega'" />
                    }
                  </li>
                }
              </ul>
            }
            @case (2) {
              <div class="campos">
                <button type="button" class="alternar" role="switch" [attr.aria-checked]="foraEquipe()" (click)="alternarForaEquipe()" [disabled]="!idEquipe()">
                  <span class="txt"><strong>Retirada por alguém de fora da equipe</strong>
                    <span>{{ idEquipe() ? 'Ligue quando quem retira não é da equipe de destino.' : 'O estoque de destino não tem equipe: escolha qualquer colaborador.' }}</span></span>
                  <span class="trilho" aria-hidden="true"><i></i></span>
                </button>
                @if (carregandoPessoas()) {
                  <div class="ces-esqueleto" style="height: 64px"></div>
                } @else if (!foraEquipe() && membros().length && membros().length <= 8) {
                  <h3 class="ces-secao">Membros da equipe</h3>
                  <ul class="ces-grupo" role="radiogroup" aria-label="Quem retira">
                    @for (m of membros(); track m.valor) {
                      <li><button type="button" role="radio" class="ces-linha ces-linha--acao" [attr.aria-checked]="responsavel() === m.valor" (click)="responsavel.set(m.valor)">
                        <span class="ces-selo" [class.ces-selo--destaque]="responsavel() === m.valor"><ces-icone nome="conta" /></span>
                        <span class="ces-linha__texto"><span class="ces-linha__titulo">{{ m.rotulo }}</span></span>
                        @if (responsavel() === m.valor) { <ces-icone nome="check" /> }
                      </button></li>
                    }
                  </ul>
                } @else {
                  <ces-seletor rotulo="Quem retira" [obrigatorio]="true" [opcoes]="foraEquipe() ? colaboradores() : membros()" [valor]="responsavel()" (valorChange)="responsavel.set($event)"
                    [invalido]="tentou() && !responsavel()" [placeholder]="(foraEquipe() ? colaboradores() : membros()).length ? 'Escolher' : 'Ninguém encontrado'" />
                  @if (!foraEquipe() && !membros().length) { <p class="ajuda">A equipe de destino não tem membros ativos. Ligue a retirada por alguém de fora.</p> }
                }
              </div>
            }
            @case (3) {
              <p class="intro">As duas assinaturas são obrigatórias e ficam guardadas com a entrega.</p>
              <div class="assinaturas">
                <ces-assinatura rotulo="Almoxarifado — quem entrega" [quem]="meuNome()" [valor]="assinaturaAlmox()" (valorChange)="assinaturaAlmox.set($event)" [invalida]="tentou()" />
                <ces-assinatura rotulo="Quem retira" [quem]="nomeResponsavel()" [valor]="assinaturaEquipe()" (valorChange)="assinaturaEquipe.set($event)" [invalida]="tentou()" />
              </div>
            }
            @case (4) {
              <div class="rota"><span class="origem">{{ d.estoqueOrigem.nomeIdentificador }}</span><ces-icone nome="seta" [tamanho]="16" /><strong class="destino">{{ d.estoqueDestino.nomeIdentificador }}</strong></div>
              <dl class="ces-dados">
                <div><dt>Quem retira</dt><dd>{{ nomeResponsavel() }}{{ foraEquipe() ? ' · de fora da equipe' : '' }}</dd></div>
                <div><dt>Resultado</dt><dd>{{ completa() ? 'Entrega total: a solicitação será concluída' : 'Entrega parcial: o saldo continua aguardando retirada' }}</dd></div>
              </dl>
              <h3 class="ces-secao"><span>Nesta entrega</span><span class="ces-num">{{ marcados() }}</span></h3>
              <ul class="ces-grupo">
                @for (it of itensMarcados(); track it.id) {
                  <li class="ces-linha"><span class="ces-linha__texto"><span class="ces-linha__titulo">{{ it.descricao }}</span>
                    <span class="ces-linha__apoio ces-num">{{ it.serie ? 'Série ' + it.serie : 'Cód. ' + it.codigo }}{{ it.quantidade < it.pendente ? ' · fica faltando ' + num(it.pendente - it.quantidade) : '' }}</span></span>
                    <span class="ces-qtd">{{ num(it.quantidade) }}<small>{{ it.unidade }}</small></span></li>
                }
              </ul>
              <div class="assinadas">
                @if (assinaturaAlmox(); as a) { <figure><img [src]="a" alt="Assinatura do almoxarifado"><figcaption>Almoxarifado</figcaption></figure> }
                @if (assinaturaEquipe(); as b) { <figure><img [src]="b" alt="Assinatura de quem retira"><figcaption>{{ nomeResponsavel() }}</figcaption></figure> }
              </div>
            }
          }
        </section>
      }
    </div>

    @if (det()) {
      <footer class="rodape">
        @if (passo() > 1) { <button type="button" class="ces-btn ces-btn--neutro" (click)="passo.set(passo() - 1)" [disabled]="enviando()">Voltar</button> }
        <button type="button" class="ces-btn ces-btn--principal principal" (click)="avancar()" [disabled]="enviando() || (passo() === 4 && !rede.isOnline())">
          @if (passo() === 4) { {{ enviando() ? 'Registrando…' : rede.isOnline() ? 'Confirmar entrega' : 'Sem internet' }} }
          @else { Continuar <ces-icone nome="seta" [tamanho]="18" /> }
        </button>
      </footer>
    }

    <!-- Série de um item -->
    <ces-folha [aberta]="serieDe() !== null" (fechar)="serieDe.set(null)" [titulo]="itemSerie()?.descricao ?? ''" descricao="Séries novas no estoque de origem. Sai uma por entrega.">
      @if (itemSerie(); as it) {
        <div class="busca"><ces-icone nome="busca" [tamanho]="18" />
          <input type="search" placeholder="Buscar ou digitar a série" enterkeyhint="search" aria-label="Buscar série" [value]="buscaSerie()" (input)="buscaSerie.set($any($event.target).value)"></div>
        <ul class="ces-grupo lista" role="radiogroup" [attr.aria-label]="'Séries de ' + it.descricao">
          @if (it.serie) {
            <li><button type="button" class="ces-linha ces-linha--acao" (click)="definirSerie(serieDe()!, '')"><span class="ces-selo"><ces-icone nome="fechar" /></span>
              <span class="ces-linha__texto"><span class="ces-linha__titulo">Não entregar agora</span></span></button></li>
          }
          @for (s of seriesFiltradas(); track s) {
            <li><button type="button" role="radio" class="ces-linha ces-linha--acao" [attr.aria-checked]="it.serie === s" (click)="definirSerie(serieDe()!, s)">
              <span class="ces-linha__texto"><span class="ces-linha__titulo mono">{{ s }}</span></span>
              @if (it.serie === s) { <ces-icone nome="check" /> }
            </button></li>
          } @empty {
            <li><div class="ces-linha"><span class="ces-linha__apoio">{{ buscaSerie() ? 'Nenhuma série com esse trecho na origem' : 'Nenhuma série nova disponível na origem' }}</span></div></li>
          }
        </ul>
      }
    </ces-folha>

    <!-- Leitura das séries -->
    <ces-folha [aberta]="leitorAberto()" (fechar)="leitorAberto.set(false)" titulo="Ler séries" [descricao]="descricaoLeitor()">
      <div class="leitor">
        @if (temCamera) {
          <ces-leitor-codigo [ativo]="leitorAberto()" [pausado]="!faltamSeries()" motivoPausa="Todos os itens com série têm série" [retorno]="retorno()" (lido)="lerSerie($event)" />
        } @else {
          <p class="ajuda">Este navegador não lê pela câmera. Use um leitor de código conectado: ele digita aqui e confirma sozinho.</p>
        }
        <form class="digitar" (submit)="$event.preventDefault(); lerSerie(digitada()); digitada.set('')">
          <input class="ces-input mono" [value]="digitada()" (input)="digitada.set($any($event.target).value)" placeholder="Ou digite a série" aria-label="Série"
            autocapitalize="characters" autocomplete="off" spellcheck="false" enterkeyhint="send">
          <button type="submit" class="ces-btn ces-btn--principal" [disabled]="!digitada().trim()" aria-label="Usar esta série"><ces-icone nome="mais" /></button>
        </form>
        <ul class="ces-grupo lidas">
          @for (it of itensComSerie(); track it.id) {
            <li class="ces-linha"><span class="ces-selo" [class.ces-selo--ok]="!!it.serie"><ces-icone [nome]="it.serie ? 'check' : 'codigo_barras'" /></span>
              <span class="ces-linha__texto"><span class="ces-linha__titulo">{{ it.descricao }}</span><span class="ces-linha__apoio mono">{{ it.serie || 'Aguardando leitura' }}</span></span></li>
          }
        </ul>
      </div>
      <div rodape class="ces-folha-rodape"><button type="button" class="ces-btn ces-btn--principal" (click)="leitorAberto.set(false)">Pronto</button></div>
    </ces-folha>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100%; background: var(--ces-fundo); }
    :host > * { flex-shrink: 0; }
    .tela { flex: 1; padding-bottom: var(--ces-e5); }
    .passos { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; padding: 0 var(--ces-e5) var(--ces-e3); }
    .passos i { height: 4px; border-radius: var(--ces-r-pilula); background: var(--ces-linha); transition: background var(--ces-m-entrar); }
    .passos i.feito { background: var(--ces-destaque); }
    .conteudo { animation: entrar var(--ces-m-entrar) var(--ces-curva); }
    @keyframes entrar { from { opacity: 0; transform: translateY(6px); } }
    .rota { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 8px; margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e3) var(--ces-e4);
      border-radius: var(--ces-r-grupo); background: var(--ces-superficie); font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .rota ces-icone { color: var(--ces-texto-3); }
    .rota .destino { font: 600 .9375rem/1.3 var(--ces-fonte); color: var(--ces-texto); }
    .barra-itens { display: flex; align-items: center; justify-content: space-between; gap: var(--ces-e2); flex-wrap: wrap; padding: var(--ces-e3) var(--ces-e3) var(--ces-e2) var(--ces-e5);
      font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .barra-itens strong { color: var(--ces-texto); }
    .botoes { display: flex; }
    .ler { margin: 0 var(--ces-e3) var(--ces-e2); width: calc(100% - 24px); }
    .item { flex-wrap: wrap; row-gap: var(--ces-e2); }
    .item .ces-linha__texto { flex: 1 1 55%; }
    .item.zerado .ces-linha__titulo { color: var(--ces-texto-2); }
    .serie { display: inline-flex; align-items: center; gap: 6px; min-height: 44px; padding: 0 14px; border-radius: var(--ces-r-pilula); border: 1.5px dashed var(--ces-linha-forte);
      background: transparent; color: var(--ces-destaque); font: 600 .875rem/1 var(--ces-fonte); cursor: pointer; }
    .serie.escolhida { border-style: solid; border-color: var(--ces-ok); color: var(--ces-texto); font-family: var(--ces-fonte-mono); }
    .serie.escolhida ces-icone { color: var(--ces-ok); }
    .nota { flex-basis: 100%; font: var(--ces-t-apoio); font-size: .75rem; color: var(--ces-texto-2); }
    .campos { display: grid; gap: var(--ces-e3); padding: var(--ces-e2) var(--ces-e3); }
    .alternar { display: flex; align-items: center; gap: var(--ces-e3); width: 100%; padding: var(--ces-e3) var(--ces-e4); min-height: 72px; border: 0; border-radius: var(--ces-r-grupo);
      background: var(--ces-superficie); color: var(--ces-texto); text-align: left; font: inherit; cursor: pointer; }
    .alternar:disabled { opacity: .75; cursor: default; }
    .alternar .txt { flex: 1; display: grid; gap: 2px; }
    .alternar .txt span { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .trilho { width: 48px; height: 28px; border-radius: 14px; background: var(--ces-linha-forte); position: relative; flex: none; transition: background var(--ces-m-entrar); }
    .trilho i { position: absolute; top: 3px; left: 3px; width: 22px; height: 22px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,.25);
      transition: transform var(--ces-m-entrar) var(--ces-curva); }
    [aria-checked='true'] .trilho { background: var(--ces-destaque); }
    [aria-checked='true'] .trilho i { transform: translateX(20px); }
    .campos .ces-secao { padding-left: var(--ces-e2); }
    .campos .ces-grupo { margin: 0; }
    .ajuda { margin: 0 var(--ces-e2); font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .intro { margin: var(--ces-e2) var(--ces-e5) var(--ces-e3); font: var(--ces-t-apoio); font-size: .875rem; color: var(--ces-texto-2); }
    .assinaturas { display: grid; gap: var(--ces-e3); padding: 0 var(--ces-e3); }
    .assinadas { display: grid; grid-template-columns: 1fr 1fr; gap: var(--ces-e2); margin: var(--ces-e3) var(--ces-e3) 0; }
    figure { margin: 0; padding: var(--ces-e2); border-radius: var(--ces-r-grupo); background: var(--ces-superficie); display: grid; gap: 4px; }
    figure img { width: 100%; height: 64px; object-fit: contain; background: #fff; border-radius: 8px; }
    figcaption { font: var(--ces-t-apoio); font-size: .75rem; color: var(--ces-texto-2); text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .rodape { position: sticky; bottom: 0; z-index: 5; display: flex; gap: var(--ces-e3); padding: var(--ces-e3) var(--ces-e4) calc(var(--ces-e3) + var(--ces-safe-base));
      background: var(--ces-fundo); border-top: 1px solid var(--ces-linha); }
    .rodape .ces-btn { min-height: var(--ces-alvo-principal); }
    .rodape .principal { flex: 1; font-size: 1rem; }
    .busca { margin: 0 var(--ces-e3) var(--ces-e3); display: flex; align-items: center; gap: var(--ces-e2); padding: 0 var(--ces-e4); min-height: 48px;
      border-radius: var(--ces-r-pilula); background: var(--ces-superficie-2); color: var(--ces-texto-2); }
    .busca input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ces-texto); font: var(--ces-t-corpo); font-size: 1rem; }
    .busca input:focus { outline: none; }
    .lista { background: var(--ces-fundo); }
    .mono { font-family: var(--ces-fonte-mono); }
    .leitor { display: grid; gap: var(--ces-e3); padding: 0 var(--ces-e4); }
    .digitar { display: flex; gap: var(--ces-e2); }
    .digitar .ces-input { flex: 1; min-width: 0; }
    .digitar .ces-btn { width: 54px; padding: 0; }
    .lidas { margin: 0; background: var(--ces-fundo); }
  `],
})
export class EntregaAlmoxComponent {
  protected readonly PASSOS = PASSOS;
  private readonly almox = inject(AlmoxService);
  private readonly auth = inject(AuthService);
  private readonly tokens = inject(TokenService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly rascunhos = inject(RascunhoService);
  private readonly injector = inject(Injector);
  readonly rede = inject(ConnectivityService);
  readonly id = signal(inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '');

  readonly temCamera = leitorPorCameraDisponivel();
  readonly passo = signal(1);
  readonly det = signal<Solicitacao | null>(null);
  readonly carregando = signal(true);
  readonly erro = signal<string | null>(null);
  readonly itens = signal<ItemEntrega[]>([]);
  readonly series = signal<Partial<Record<string, string[]>>>({});
  readonly foraEquipe = signal(false);
  readonly responsavel = signal<string | null>(null);
  readonly membros = signal<OpcaoSeletor[]>([]);
  readonly colaboradores = signal<OpcaoSeletor[]>([]);
  readonly carregandoPessoas = signal(false);
  readonly assinaturaAlmox = signal<string | null>(null);
  readonly assinaturaEquipe = signal<string | null>(null);
  readonly tentou = signal(false);
  readonly enviando = signal(false);
  readonly serieDe = signal<number | null>(null);
  readonly buscaSerie = signal('');
  readonly leitorAberto = signal(false);
  readonly retorno = signal<RetornoLeitura | null>(null);
  readonly digitada = signal('');
  private rascunho: VinculoRascunho | null = null;

  readonly idCurto = computed(() => idCurto(this.id()));
  readonly idEquipe = computed(() => this.det()?.idEquipe || this.det()?.estoqueDestino?.idEquipe || null);
  readonly marcados = computed(() => this.itens().filter(i => i.quantidade > 0).length);
  readonly itensMarcados = computed(() => this.itens().filter(i => i.quantidade > 0));
  readonly temSerie = computed(() => this.itens().some(i => i.detalhaSerie));
  readonly itensComSerie = computed(() => this.itens().filter(i => i.detalhaSerie));
  readonly faltamSeries = computed(() => this.itensComSerie().some(i => !i.serie));
  readonly itemSerie = computed(() => (this.serieDe() !== null ? this.itens()[this.serieDe()!] : null));
  readonly completa = computed(() => this.itens().every(i => i.quantidade >= i.pendente));
  readonly meuNome = computed(() => this.almox.nome(this.tokens.usuarioId(), this.tokens.nomeUsuario() ?? 'Você'));
  readonly nomeResponsavel = computed(() => {
    const id = this.responsavel();
    return [...this.membros(), ...this.colaboradores()].find(o => o.valor === id)?.rotulo ?? (id ? this.almox.nome(id) : 'Não escolhido');
  });
  readonly descricaoLeitor = computed(() => {
    const n = this.itensComSerie().filter(i => i.serie).length;
    return `${n} de ${this.itensComSerie().length} itens com série. Cada série lida vai para o item do mesmo material.`;
  });
  /** Séries da origem para o item, sem as já usadas em outra linha. */
  readonly seriesFiltradas = computed(() => {
    const it = this.itemSerie();
    if (!it) return [];
    const usadas = new Set(this.itens().filter(x => x.id !== it.id && x.serie).map(x => x.serie));
    const q = normalizarSerie(this.buscaSerie());
    return (this.series()[it.idMaterial] ?? []).filter(s => !usadas.has(s) && (!q || s.includes(q)));
  });

  constructor() {
    this.almox.carregarNomes();
    queueMicrotask(() => this.carregar());
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.almox.obterSolicitacao(this.id()).subscribe({
      next: d => {
        if (!pode(d, 'ENTREGAR')) {
          this.toast.info(`A solicitação #${this.idCurto()} não aceita entrega agora (${d.status === 'PENDENTE' ? 'falta aprovar' : 'sem saldo a entregar'}).`);
          void this.router.navigate(['/almox/solicitacoes', d.id], { replaceUrl: true });
          return;
        }
        this.det.set(d);
        this.itens.set(d.itens.map(i => {
          const entregue = Number(i.quantidadeEntregaLiquida ?? i.quantidadeEntregue ?? 0);
          const pendente = Number(i.quantidadePendente ?? Math.max(0, i.quantidade - entregue));
          return {
            id: i.id, idMaterial: i.material.id, codigo: i.material.codigo, descricao: i.material.descricao, unidade: i.material.unidadeMedida || 'UN',
            detalhaSerie: !!i.material.detalhaSerie, pedido: Number(i.quantidade), pendente,
            quantidade: i.material.detalhaSerie ? 0 : pendente, serie: '',
          };
        }).filter(i => i.pendente > 0));
        this.carregarSeries(d);
        this.prepararPessoas(d);
        this.carregando.set(false);
        void this.vincularRascunho();
      },
      error: (e: ApiError) => { this.carregando.set(false); this.erro.set(e.status === 0 ? 'Sem conexão com o servidor.' : e.detail || 'Tente de novo.'); },
    });
  }

  private async vincularRascunho(): Promise<void> {
    if (this.rascunho) return;
    this.rascunho = this.rascunhos.vincular<RascunhoEntrega>({
      chave: `almox:entrega:${this.id()}`,
      injector: this.injector,
      // Assinaturas não entram no rascunho: são colhidas de novo se o app fechar.
      ler: () => ({ passo: Math.min(this.passo(), 2), itens: this.itens().map(i => ({ id: i.id, quantidade: i.quantidade, serie: i.serie })), foraEquipe: this.foraEquipe(), responsavel: this.responsavel() }),
      aplicar: r => {
        const porId = new Map(r.itens.map(i => [i.id, i]));
        this.itens.update(l => l.map(i => {
          const s = porId.get(i.id);
          if (!s) return i;
          return i.detalhaSerie ? { ...i, serie: s.serie, quantidade: s.serie ? 1 : 0 } : { ...i, quantidade: Math.min(i.pendente, Math.max(0, s.quantidade)) };
        }));
        if (r.foraEquipe !== this.foraEquipe()) this.alternarForaEquipe();
        this.responsavel.set(r.responsavel);
        this.passo.set(Math.min(Math.max(1, r.passo || 1), 2));
      },
      titulo: () => `Entrega da solicitação #${this.idCurto()}`,
      rota: () => `/almox/solicitacoes/${this.id()}/entrega`,
      vazio: r => r.passo === 1 && !r.responsavel,
    });
    await this.rascunho.restaurado;
  }

  private carregarSeries(d: Solicitacao): void {
    const materiais = [...new Set(this.itens().filter(i => i.detalhaSerie).map(i => i.idMaterial))];
    if (!materiais.length) return;
    forkJoin(materiais.map(m => this.almox.listarSeries(d.estoqueOrigem.id, m, 'NOVO').pipe(
      map(r => [m, r.map(s => s.numSerie)] as const), catchError(() => of([m, [] as string[]] as const)),
    ))).subscribe(pares => this.series.set(Object.fromEntries(pares)));
  }

  private prepararPessoas(d: Solicitacao): void {
    this.responsavel.set(d.idColaboradorSaque ?? null);
    const fora = !!d.saqueForaEquipe || !this.idEquipe();
    this.foraEquipe.set(fora);
    if (fora) this.carregarColaboradores();
    else this.carregarMembros();
  }

  private carregarMembros(): void {
    const equipe = this.idEquipe();
    if (!equipe) return;
    this.carregandoPessoas.set(true);
    this.almox.membrosEquipe(equipe).subscribe({
      next: m => {
        const opcoes = m.map(x => ({ valor: x.idColaborador, rotulo: x.nomeColaborador }));
        this.membros.set(opcoes);
        if (!this.responsavel() && opcoes.length === 1) this.responsavel.set(opcoes[0].valor);
        this.carregandoPessoas.set(false);
      },
      error: () => { this.membros.set([]); this.carregandoPessoas.set(false); },
    });
  }

  private carregarColaboradores(): void {
    if (this.colaboradores().length) return;
    this.carregandoPessoas.set(true);
    this.almox.colaboradores().subscribe({
      next: c => { this.colaboradores.set(c.map(x => ({ valor: x.id, rotulo: x.nomeCompleto }))); this.carregandoPessoas.set(false); },
      error: () => { this.colaboradores.set([]); this.carregandoPessoas.set(false); },
    });
  }

  alternarForaEquipe(): void {
    if (!this.idEquipe()) return;
    const fora = !this.foraEquipe();
    this.foraEquipe.set(fora);
    this.responsavel.set(null);
    if (fora) this.carregarColaboradores();
    else if (!this.membros().length) this.carregarMembros();
  }

  num(n: number): string {
    return numeroBr(n);
  }

  definirQuantidade(i: number, q: number): void {
    const it = this.itens()[i];
    if (q > it.pendente) { this.toast.warning(`No máximo ${numeroBr(it.pendente)} ${it.unidade}: é o que falta entregar.`); return; }
    this.itens.update(l => l.map((x, k) => (k === i ? { ...x, quantidade: Math.max(0, q) } : x)));
  }

  escolherSerie(i: number): void {
    this.buscaSerie.set('');
    this.serieDe.set(i);
  }

  definirSerie(i: number, s: string): void {
    this.itens.update(l => l.map((x, k) => (k === i ? { ...x, serie: s, quantidade: s ? 1 : 0 } : x)));
    this.serieDe.set(null);
  }

  /** Série lida/digitada: vai para o primeiro item sem série do mesmo material que a tem na origem. */
  lerSerie(bruta: string): void {
    const s = extrairSerie(bruta);
    if (!s) return;
    if (this.itens().some(i => i.serie === s)) { this.retorno.set({ texto: `${s} já está na entrega`, tom: 'atencao' }); return; }
    const idx = this.itens().findIndex(i => i.detalhaSerie && !i.serie && (this.series()[i.idMaterial] ?? []).includes(s));
    if (idx < 0) {
      const existe = this.itens().some(i => i.detalhaSerie && (this.series()[i.idMaterial] ?? []).includes(s));
      this.retorno.set({ texto: existe ? `${s}: o item desse material já tem série` : `${s} não está na origem para estes materiais`, tom: 'erro' });
      return;
    }
    this.itens.update(l => l.map((x, k) => (k === idx ? { ...x, serie: s, quantidade: 1 } : x)));
    this.retorno.set({ texto: `${s} → ${this.itens()[idx].descricao}`, tom: 'ok' });
  }

  preencherTudo(): void {
    const usadas = new Set<string>();
    this.itens.update(l => l.map(it => {
      if (!it.detalhaSerie) return { ...it, quantidade: it.pendente };
      const s = it.serie || (this.series()[it.idMaterial] ?? []).find(n => !usadas.has(n)) || '';
      if (s) usadas.add(s);
      return { ...it, serie: s, quantidade: s ? 1 : 0 };
    }));
  }

  zerar(): void {
    this.itens.update(l => l.map(i => ({ ...i, quantidade: 0, serie: '' })));
  }

  avancar(): void {
    this.tentou.set(true);
    const p = this.passo();
    if (p === 1 && !this.marcados()) { this.toast.warning('Informe ao menos um material para entregar.'); return; }
    if (p === 2 && !this.responsavel()) { this.toast.warning('Escolha quem retira o material.'); return; }
    if (p === 3 && !(this.assinaturaAlmox() && this.assinaturaEquipe())) { this.toast.warning('Colha as duas assinaturas para continuar.'); return; }
    if (p === 4) { this.confirmar(); return; }
    this.tentou.set(false);
    this.passo.set(p + 1);
  }

  private confirmar(): void {
    const d = this.det();
    if (!d || this.enviando()) return;
    const itens = this.itensMarcados();
    this.enviando.set(true);
    this.almox.entregar(d.id, {
      assinaturaAlmox: this.assinaturaAlmox()!,
      assinaturaEquipe: this.assinaturaEquipe()!,
      idColaboradorSaque: this.responsavel()!,
      saqueForaEquipe: this.foraEquipe(),
      itens: itens.map(i => ({ idItemSolicitacao: i.id, quantidade: i.quantidade, novaQuantidadeSolicitada: i.pedido, numSerie: i.serie || undefined })),
    }).subscribe({
      next: async () => {
        this.enviando.set(false);
        await this.rascunho?.concluir();
        this.toast.success(this.completa() ? 'Entrega registrada. A solicitação foi concluída.' : 'Entrega parcial registrada. O saldo continua aguardando retirada.');
        void this.router.navigate(['/almox/solicitacoes', d.id], { replaceUrl: true });
      },
      error: (e: ApiError) => {
        this.enviando.set(false);
        this.toast.error(e.status === 409 ? 'Outra pessoa alterou esta solicitação. Revise e confirme de novo.' : e.detail || 'Não foi possível registrar a entrega.');
        if (e.status === 409) this.carregar();
      },
    });
  }

  fechar(): void {
    void this.rascunho?.gravarEstadoAtual();
    void this.router.navigate(['/almox/solicitacoes', this.id()], { replaceUrl: true });
  }

  protected readonly podeGerir = this.auth.podeGerirSolicitacoesAlmox;
}
