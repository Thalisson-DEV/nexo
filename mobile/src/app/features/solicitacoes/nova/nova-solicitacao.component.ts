import { ChangeDetectionStrategy, Component, Injector, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DbService } from '../../../core/db/db.service';
import { CorrecaoRecusadaError, OutboxService } from '../../../core/sync/outbox.service';
import { SyncService } from '../../../core/sync/sync.service';
import { ConnectivityService } from '../../../core/sync/connectivity.service';
import { TokenService } from '../../../core/auth/token.service';
import { TurnoService } from '../../../core/turno/turno.service';
import { ToastService } from '../../../core/ui/toast.service';
import { RascunhoService, VinculoRascunho } from '../../../core/sessao/rascunho.service';
import { SessaoService } from '../../../core/sessao/sessao.service';
import { ColaboradorLocal, EstoqueLocal, MaterialLocal, SolicitacaoLocal } from '../../../core/db/models';
import {
  BarraTopoComponent, IconeComponent, IndicadorRedeComponent, OpcaoSeletor, QuantidadeComponent, SeletorComponent, VazioComponent,
} from '../../../ui';

interface SelectedMaterial extends MaterialLocal {
  quantidade: number;
}

interface RascunhoPedido {
  passo: number;
  idEstoqueOrigem: string | null;
  idColaboradorSaque: string | null;
  saqueForaEquipe: boolean;
  dataPrevista: string;
  itens: { id: string; quantidade: number }[];
  seletorAberto: string | null;
}

const TITULOS = ['Origem e destino', 'Materiais', 'Conferir'];

/**
 * Pedido de material do almoxarifado para o estoque de campo da equipe.
 * Regras mantidas: turno aberto, origem só estoque físico, destino é o estoque de
 * campo da equipe, quantidade positiva com até 3 casas decimais, edição só antes do envio.
 */
@Component({
  selector: 'app-nova-solicitacao',
  standalone: true,
  imports: [BarraTopoComponent, IconeComponent, IndicadorRedeComponent, QuantidadeComponent, SeletorComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tela">
      <ces-barra-topo [titulo]="editId() ? 'Corrigir pedido' : 'Pedir material'" [subtitulo]="titulos[passo() - 1] + ' · ' + passo() + ' de 3'"
        navegacao="fechar" (voltar)="fechar()">
        <ces-indicador-rede />
      </ces-barra-topo>
      <div class="passos" aria-hidden="true">@for (s of [1, 2, 3]; track s) { <i [class.feito]="s <= passo()"></i> }</div>

      @if (carregando()) {
        <div class="esqueletos" aria-busy="true" aria-label="Carregando">
          @for (i of [1, 2, 3]; track i) { <div class="ces-esqueleto" style="height: 64px"></div> }
        </div>
      } @else {
        <section class="conteudo" [attr.aria-label]="titulos[passo() - 1]">
          @if (passo() === 1) {
            <div class="campos">
              <ces-seletor rotulo="Retirar de" [obrigatorio]="true" [opcoes]="opcoesOrigem()" [valor]="idEstoqueOrigem()"
                (valorChange)="idEstoqueOrigem.set($event)" [invalido]="tentou() && !origemValida()"
                [aberto]="seletorAberto() === 'origem'" (abertoChange)="seletorAberto.set($event ? 'origem' : null)" />
              <div class="fixo"><span>Vai para</span><strong>{{ nomeEstoqueDestino() }}</strong></div>
              @if (!estoqueDestino()) {
                <p class="aviso"><ces-icone nome="alerta" [tamanho]="18" />O estoque de campo da equipe ainda não está no aparelho. Sincronize em Conta.</p>
              }
              <button type="button" role="switch" class="interruptor" [attr.aria-checked]="saqueForaEquipe()" (click)="definirForaEquipe(!saqueForaEquipe())">
                <span class="txt"><b>Quem retira é de fora da equipe</b><span>O almoxarifado define na entrega.</span></span>
                <span class="trilho" aria-hidden="true"><span></span></span>
              </button>
              @if (!saqueForaEquipe()) {
                <ces-seletor rotulo="Quem vai retirar" placeholder="Definir na entrega" [opcoes]="opcoesMembros()" [valor]="idColaboradorSaque()"
                  (valorChange)="idColaboradorSaque.set($event)"
                  [aberto]="seletorAberto() === 'saque'" (abertoChange)="seletorAberto.set($event ? 'saque' : null)" />
              }
              <div class="ces-campo data">
                <label for="dataPrevista">Data prevista de retirada *</label>
                <input id="dataPrevista" class="ces-input" type="date" [min]="hoje" [value]="dataPrevista()" (input)="dataPrevista.set($any($event.target).value)"
                  [attr.aria-invalid]="tentou() && !dataValida() ? 'true' : null">
              </div>
            </div>
          }

          @if (passo() === 2) {
            @if (selecionados().length) {
              <h2 class="ces-secao"><span>No pedido</span><span class="ces-num">{{ selecionados().length }}</span></h2>
              <ul class="ces-grupo">
                @for (m of selecionados(); track m.id) {
                  <li>
                    <div class="ces-linha item">
                      <span class="ces-linha__texto">
                        <span class="ces-linha__titulo">{{ m.descricao }}</span>
                        <span class="ces-linha__apoio ces-num">Cód. {{ m.codigo }}</span>
                      </span>
                      <ces-quantidade [grande]="true" [unidade]="m.unidadeMedida" [valor]="m.quantidade" [minimo]="0.001" (valorChange)="definirQuantidade(m.id, $event)" [rotulo]="'quantidade de ' + m.descricao" />
                      <span class="un"></span>
                      <button type="button" class="ces-btn-icone" (click)="remover(m.id)" [attr.aria-label]="'Tirar ' + m.descricao + ' do pedido'">
                        <ces-icone nome="lixeira" [tamanho]="20" />
                      </button>
                    </div>
                  </li>
                }
              </ul>
            }
            <h2 class="ces-secao">Adicionar</h2>
            <div class="busca">
              <ces-icone nome="busca" [tamanho]="18" />
              <input type="search" placeholder="Nome ou código" enterkeyhint="search" aria-label="Buscar material"
                [value]="busca()" (input)="busca.set($any($event.target).value)">
            </div>
            @if (catalogoFiltrado().length) {
              <ul class="ces-grupo">
                @for (m of catalogoFiltrado(); track m.id) {
                  <li class="linha-lista">
                    <div class="ces-linha">
                      <span class="ces-linha__texto">
                        <span class="ces-linha__titulo">{{ m.descricao }}</span>
                        <span class="ces-linha__apoio ces-num">Cód. {{ m.codigo }} · em {{ m.unidadeMedida }}</span>
                      </span>
                      <button type="button" class="ces-btn ces-btn--tonal ces-btn--compacto" (click)="adicionar(m)" [disabled]="!!selecao(m.id)"
                        [attr.aria-label]="'Adicionar ' + m.descricao">{{ selecao(m.id) ? 'No pedido' : 'Adicionar' }}</button>
                    </div>
                  </li>
                }
              </ul>
            } @else {
              <ces-vazio icone="busca" titulo="Nada encontrado" texto="Confira o nome ou o código." />
            }
          }

          @if (passo() === 3) {
            <dl class="resumo">
              <div><dt>Retirar de</dt><dd>{{ nomeEstoqueOrigem() }}</dd></div>
              <div><dt>Vai para</dt><dd>{{ nomeEstoqueDestino() }}</dd></div>
              <div><dt>Quem retira</dt><dd>{{ nomeResponsavelSaque() }}</dd></div>
              <div><dt>Data prevista</dt><dd class="ces-num">{{ dataFormatada() }}</dd></div>
            </dl>
            <h2 class="ces-secao"><span>Materiais pedidos</span><span class="ces-num">{{ selecionados().length }}</span></h2>
            <ul class="ces-grupo resumo-lista" aria-label="Materiais pedidos">
              @for (m of selecionados(); track m.id) {
                <li><div class="ces-linha"><span class="ces-linha__texto"><span class="ces-linha__titulo">{{ m.descricao }}</span>
                  <span class="ces-linha__apoio ces-num">Cód. {{ m.codigo }}</span></span>
                  <span class="qtd-fixa ces-num">{{ num(m.quantidade) }} <small>{{ m.unidadeMedida }}</small></span></div></li>
              }
            </ul>
            <p class="dica"><ces-icone nome="nuvem" [tamanho]="18" />{{ rede.isOnline() ? 'Envia agora. Se o sinal cair, fica na fila.' : 'Sem internet: fica salvo no aparelho e sai quando o sinal voltar.' }}</p>
          }
        </section>
      }
    </div>

    <footer class="rodape">
      @if (passo() > 1) { <button type="button" class="ces-btn ces-btn--neutro" (click)="passo.set(passo() - 1)">Voltar</button> }
      <button type="button" class="ces-btn ces-btn--principal principal" (click)="avancar()" [disabled]="enviando() || carregando()">
        @if (passo() === 3) { {{ enviando() ? 'Salvando…' : (editId() ? 'Salvar correção' : 'Enviar pedido') }} }
        @else { Continuar <ces-icone nome="seta" [tamanho]="18" /> }
      </button>
    </footer>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100%; background: var(--ces-fundo); }
    /* Nada encolhe: com conteúdo maior que a tela, a página rola (grupos com overflow:hidden eram cortados). */
    :host > * { flex-shrink: 0; }
    .tela { flex: 1; padding-bottom: var(--ces-e5); }
    .passos { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; padding: 0 var(--ces-e5) var(--ces-e3); }
    .passos i { height: 4px; border-radius: var(--ces-r-pilula); background: var(--ces-linha); }
    .passos i.feito { background: var(--ces-destaque); }
    .esqueletos { display: grid; gap: var(--ces-e2); padding: var(--ces-e4); }
    .conteudo { animation: entrar var(--ces-m-entrar) var(--ces-curva); }
    @keyframes entrar { from { opacity: 0; transform: translateY(6px); } }
    .campos { display: grid; gap: var(--ces-e2); padding: var(--ces-e2) var(--ces-e3); }
    .fixo { display: grid; gap: 2px; padding: var(--ces-e3) var(--ces-e4); background: var(--ces-superficie); border-radius: var(--ces-r-grupo); }
    .fixo span { font: 500 .8125rem/1.3 var(--ces-fonte); color: var(--ces-texto-2); }
    .aviso, .dica { display: flex; gap: var(--ces-e2); align-items: flex-start; font: var(--ces-t-apoio); font-size: .875rem; margin: 0; }
    .aviso { padding: var(--ces-e3) var(--ces-e4); border-radius: var(--ces-r-grupo); background: var(--ces-atencao-suave); }
    .dica { margin: var(--ces-e4) var(--ces-e5) 0; color: var(--ces-texto-2); }
    .interruptor { display: flex; align-items: center; gap: var(--ces-e3); min-height: 64px; padding: var(--ces-e3) var(--ces-e4); border: 0;
      border-radius: var(--ces-r-grupo); background: var(--ces-superficie); color: var(--ces-texto); text-align: left; cursor: pointer; }
    .interruptor .txt { flex: 1; display: grid; gap: 2px; }
    .interruptor .txt span { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .trilho { width: 48px; height: 28px; border-radius: 14px; background: var(--ces-linha-forte); position: relative; flex: none; transition: background-color var(--ces-m-entrar); }
    .trilho span { position: absolute; top: 3px; left: 3px; width: 22px; height: 22px; border-radius: 50%; background: #fff; transition: transform var(--ces-m-entrar) var(--ces-curva); }
    [aria-checked='true'] .trilho { background: var(--ces-destaque); }
    [aria-checked='true'] .trilho span { transform: translateX(20px); }
    .data { padding: var(--ces-e2) var(--ces-e1) 0; }
    .item { flex-wrap: wrap; }
    .item .ces-linha__texto { flex-basis: 100%; }
    .un { font: var(--ces-t-apoio); color: var(--ces-texto-2); flex: 1; }
    .busca { margin: 0 var(--ces-e3) var(--ces-e2); display: flex; align-items: center; gap: var(--ces-e2); padding: 0 var(--ces-e4); min-height: 48px;
      border-radius: var(--ces-r-pilula); background: var(--ces-superficie); color: var(--ces-texto-2); }
    .busca input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ces-texto); font: var(--ces-t-corpo); font-size: 1rem; }
    .busca input:focus { outline: none; }
    .linha-lista { content-visibility: auto; contain-intrinsic-size: auto 64px; }
    .resumo { margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e2) var(--ces-e4); background: var(--ces-superficie); border-radius: var(--ces-r-grupo); }
    .resumo > div { padding: var(--ces-e3) 0; border-bottom: 1px solid var(--ces-linha); }
    .resumo > div:last-child { border-bottom: 0; }
    dt { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    dd { margin: 2px 0 0; font: var(--ces-t-corpo-forte); overflow-wrap: anywhere; }
    .qtd-fixa { font: 700 1.375rem/1 var(--ces-fonte); white-space: nowrap; }
    .qtd-fixa small { font: 600 .875rem/1 var(--ces-fonte); color: var(--ces-texto-2); }
    .resumo-lista .ces-linha { min-height: 56px; }
    .rodape { position: sticky; bottom: 0; z-index: 5; display: flex; gap: var(--ces-e3); padding: var(--ces-e3) var(--ces-e4) calc(var(--ces-e3) + var(--ces-safe-base));
      background: var(--ces-fundo); border-top: 1px solid var(--ces-linha); }
    .rodape .principal { flex: 1; min-height: var(--ces-alvo-principal); font-size: 1rem; }
  `],
})
export class NovaSolicitacaoComponent implements OnInit {
  private readonly dbService = inject(DbService);
  private readonly outboxService = inject(OutboxService);
  private readonly syncService = inject(SyncService);
  readonly rede = inject(ConnectivityService);
  private readonly tokenService = inject(TokenService);
  private readonly turnoService = inject(TurnoService);
  private readonly toast = inject(ToastService);
  private readonly rascunhos = inject(RascunhoService);
  private readonly sessao = inject(SessaoService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly injector = inject(Injector);

  readonly titulos = TITULOS;
  readonly hoje = dataLocal(new Date());
  readonly editId = signal<string | null>(null);
  readonly passo = signal(1);
  readonly carregando = signal(true);
  readonly enviando = signal(false);
  readonly tentou = signal(false);
  readonly seletorAberto = signal<string | null>(null);

  readonly estoques = signal<EstoqueLocal[]>([]);
  readonly materiais = signal<MaterialLocal[]>([]);
  readonly membrosEquipe = signal<ColaboradorLocal[]>([]);
  readonly idEstoqueOrigem = signal<string | null>(null);
  readonly idColaboradorSaque = signal<string | null>(null);
  readonly saqueForaEquipe = signal(false);
  readonly dataPrevista = signal(dataSugerida());
  readonly selecionados = signal<SelectedMaterial[]>([]);
  readonly busca = signal('');
  private rascunho: VinculoRascunho | null = null;

  readonly estoquesDisponiveis = computed(() => this.estoques().filter(e => e.ativo !== false && e.tipoEstoque?.toUpperCase() === 'FISICO'));
  readonly estoqueDestino = computed(() => {
    const campo = this.estoques().filter(e => e.ativo !== false && e.tipoEstoque?.toUpperCase() === 'CAMPO');
    return campo.find(e => e.idEquipe === this.tokenService.equipeId()) || campo.find(e => e.id === this.tokenService.estoqueId());
  });
  readonly nomeEstoqueDestino = computed(() => this.estoqueDestino()?.nomeIdentificador || 'Estoque de campo indisponível');
  readonly nomeEstoqueOrigem = computed(() => this.estoques().find(e => e.id === this.idEstoqueOrigem())?.nomeIdentificador || 'Não escolhido');
  readonly nomeResponsavelSaque = computed(() => this.saqueForaEquipe()
    ? 'Fora da equipe (definido na entrega)'
    : this.membrosEquipe().find(c => c.id === this.idColaboradorSaque())?.nomeCompleto || 'Definido na entrega');
  readonly opcoesOrigem = computed<OpcaoSeletor[]>(() => this.estoquesDisponiveis().map(e => ({ valor: e.id, rotulo: e.nomeIdentificador })));
  readonly opcoesMembros = computed<OpcaoSeletor[]>(() => this.membrosEquipe().map(c => ({ valor: c.id, rotulo: c.nomeCompleto, apoio: c.funcao })));
  readonly catalogoFiltrado = computed(() => {
    const q = this.busca().toLowerCase().trim();
    const lista = this.materiais();
    return (q ? lista.filter(m => m.descricao.toLowerCase().includes(q) || m.codigo.toLowerCase().includes(q)) : lista).slice(0, 80);
  });
  readonly origemValida = computed(() => this.estoquesDisponiveis().some(e => e.id === this.idEstoqueOrigem()));
  readonly dataValida = computed(() => /^\d{4}-\d{2}-\d{2}$/.test(this.dataPrevista()));
  readonly dataFormatada = computed(() => {
    const [a, m, d] = this.dataPrevista().split('-');
    return d ? `${d}/${m}/${a}` : '';
  });

  async ngOnInit(): Promise<void> {
    await this.turnoService.loadTurnoAtivo();
    if (!this.turnoService.turnoAtivo()) {
      this.toast.warning('Abra o turno antes de pedir material.');
      void this.router.navigate(['/solicitacoes']);
      return;
    }
    const editId = this.route.snapshot.queryParamMap.get('editId');
    if (editId) this.editId.set(editId);
    await this.carregar();
    if (editId && !(await this.carregarEdicao(editId))) return;

    this.rascunho = this.rascunhos.vincular<RascunhoPedido>({
      chave: editId ? `pedido:editar:${editId}` : 'pedido:novo',
      injector: this.injector,
      ler: () => ({
        passo: this.passo(), idEstoqueOrigem: this.idEstoqueOrigem(), idColaboradorSaque: this.idColaboradorSaque(),
        saqueForaEquipe: this.saqueForaEquipe(), dataPrevista: this.dataPrevista(),
        itens: this.selecionados().map(m => ({ id: m.id, quantidade: m.quantidade })), seletorAberto: this.seletorAberto(),
      }),
      aplicar: r => this.aplicarRascunho(r),
      titulo: () => `Pedido de material · ${this.selecionados().length} ${this.selecionados().length === 1 ? 'item' : 'itens'}`,
      rota: () => (editId ? `/solicitacoes/nova?editId=${encodeURIComponent(editId)}` : '/solicitacoes/nova'),
      vazio: r => r.passo === 1 && !r.itens.length && r.seletorAberto === null,
    });
    await this.rascunho.restaurado;
  }

  private async carregar(): Promise<void> {
    this.carregando.set(true);
    try {
      const [estoques, materiais, equipe, colaboradores] = await Promise.all([
        this.dbService.getEstoques(), this.dbService.getMateriais(), this.dbService.getEquipe(), this.dbService.getColaboradoresAtivos(),
      ]);
      this.estoques.set(estoques);
      this.materiais.set(materiais);
      const membros = colaboradores.filter(c => !equipe?.membrosIds?.length || equipe.membrosIds.includes(c.id));
      this.membrosEquipe.set(membros);
      this.idColaboradorSaque.set(membros.find(c => c.idUsuario === this.tokenService.usuarioId())?.id || null);
      const origens = this.estoquesDisponiveis();
      if (origens.length === 1) this.idEstoqueOrigem.set(origens[0].id);
    } finally {
      this.carregando.set(false);
    }
  }

  private async carregarEdicao(id: string): Promise<boolean> {
    const fila = await this.dbService.getOutboxItemBySolicitacaoId(id);
    if (fila && !this.outboxService.canEdit(fila)) {
      this.toast.error(fila.envioIniciadoEm && fila.status === 'PENDENTE'
        ? new CorrecaoRecusadaError().message
        : 'Este pedido foi preservado e aguarda tratamento administrativo.');
      void this.router.navigate(['/solicitacoes']);
      return false;
    }
    const sol = await this.dbService.getSolicitacaoById(id);
    if (!sol) return true;
    this.idEstoqueOrigem.set(sol.idEstoqueOrigem);
    this.dataPrevista.set(sol.dataPrevista || dataSugerida());
    this.saqueForaEquipe.set(!!sol.saqueForaEquipe);
    this.idColaboradorSaque.set(sol.idColaboradorSaque || null);
    const mapa = new Map(this.materiais().map(m => [m.id, m]));
    this.selecionados.set(sol.itens.map(item => ({
      ...(mapa.get(item.idMaterial) || { id: item.idMaterial, codigo: '?', descricao: 'Material', unidadeMedida: 'UN', detalhaSerie: false }),
      quantidade: item.quantidade,
    })));
    return true;
  }

  private aplicarRascunho(r: RascunhoPedido): void {
    this.passo.set(Math.min(Math.max(1, r.passo || 1), 3));
    this.idEstoqueOrigem.set(r.idEstoqueOrigem);
    this.saqueForaEquipe.set(!!r.saqueForaEquipe);
    this.idColaboradorSaque.set(r.idColaboradorSaque);
    if (r.dataPrevista) this.dataPrevista.set(r.dataPrevista);
    const mapa = new Map(this.materiais().map(m => [m.id, m]));
    this.selecionados.set((r.itens ?? []).filter(i => mapa.has(i.id)).map(i => ({ ...mapa.get(i.id)!, quantidade: i.quantidade })));
    this.seletorAberto.set(r.seletorAberto ?? null);
  }

  /** Número no formato brasileiro (vírgula decimal). */
  num(n: number): string {
    return String(n).replace('.', ',');
  }

  fechar(): void {
    void this.rascunho?.gravarEstadoAtual();
    if (this.selecionados().length) this.toast.info('Rascunho guardado. Continue pela tela Hoje.');
    const anterior = this.sessao.rotaAnterior();
    void this.router.navigateByUrl(anterior && !anterior.startsWith('/solicitacoes/nova') ? anterior : '/solicitacoes');
  }

  definirForaEquipe(valor: boolean): void {
    this.saqueForaEquipe.set(valor);
    this.idColaboradorSaque.set(null);
  }

  selecao(id: string): SelectedMaterial | undefined {
    return this.selecionados().find(m => m.id === id);
  }

  adicionar(m: MaterialLocal): void {
    if (!this.selecao(m.id)) this.selecionados.update(l => [...l, { ...m, quantidade: 1 }]);
  }

  remover(id: string): void {
    this.selecionados.update(l => l.filter(m => m.id !== id));
  }

  definirQuantidade(id: string, qtd: number): void {
    if (!quantidadeValida(qtd)) return;
    this.selecionados.update(l => l.map(m => (m.id === id ? { ...m, quantidade: qtd } : m)));
  }

  private podeAvancar(): boolean {
    if (this.passo() === 1) return !!this.tokenService.equipeId() && this.origemValida() && !!this.estoqueDestino() && this.dataValida();
    return this.selecionados().length > 0 && this.selecionados().every(m => quantidadeValida(m.quantidade));
  }

  avancar(): void {
    this.tentou.set(true);
    if (!this.podeAvancar()) {
      this.toast.warning(this.passo() === 1 ? 'Escolha de onde retirar e a data prevista.' : 'Adicione ao menos um material com quantidade.');
      return;
    }
    if (this.passo() === 3) {
      void this.enviar();
      return;
    }
    this.tentou.set(false);
    this.passo.update(p => p + 1);
  }

  private async enviar(): Promise<void> {
    if (this.enviando()) return;
    if (!this.turnoService.turnoAtivo()) {
      this.toast.error('Seu turno foi fechado. O pedido continua salvo aqui.');
      return;
    }
    const id = crypto.randomUUID();
    const payload = {
      idEstoqueOrigem: this.idEstoqueOrigem(),
      idEquipe: this.tokenService.equipeId(),
      dataPrevista: this.dataPrevista(),
      idColaboradorSaque: this.idColaboradorSaque(),
      saqueForaEquipe: this.saqueForaEquipe(),
      itens: this.selecionados().map(m => ({ idMaterial: m.id, quantidade: m.quantidade })),
    };
    const solicitacao: SolicitacaoLocal = {
      id,
      idEstoqueOrigem: payload.idEstoqueOrigem!,
      idEstoqueDestino: this.estoqueDestino()!.id,
      idEquipe: payload.idEquipe,
      dataPrevista: payload.dataPrevista,
      idColaboradorSaque: payload.idColaboradorSaque,
      saqueForaEquipe: payload.saqueForaEquipe,
      idRequisitante: this.tokenService.usuarioId() || '',
      status: 'PENDENTE',
      dataSolicitacao: new Date().toISOString(),
      itens: payload.itens,
    };
    try {
      this.enviando.set(true);
      const antigo = this.editId();
      const filaAntiga = antigo ? await this.dbService.getOutboxItemBySolicitacaoId(antigo) : undefined;
      // Registro e intenção de envio na mesma transação local.
      await this.outboxService.enqueueSolicitacao(
        { endpoint: '/v1/solicitacoes-material', method: 'POST', payload: { ...payload, tempId: id } },
        solicitacao, antigo ?? undefined, filaAntiga?.id,
      );
      await this.rascunho?.concluir();
      if (this.rede.isOnline()) void this.syncService.syncOutbox();
      this.toast.success(this.rede.isOnline() ? 'Pedido enviado.' : 'Pedido salvo no aparelho. Sai quando o sinal voltar.');
      void this.router.navigate(['/solicitacoes'], { replaceUrl: true });
    } catch (error) {
      console.error('Erro ao salvar solicitação:', error);
      this.toast.error(error instanceof CorrecaoRecusadaError
        ? error.message
        : 'Não foi possível salvar o pedido. Ele continua aqui.');
    } finally {
      this.enviando.set(false);
    }
  }
}

function quantidadeValida(q: number): boolean {
  return Number.isFinite(q) && q > 0 && Math.abs(Math.round(q * 1000) - q * 1000) < 0.000001;
}

function dataLocal(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function dataSugerida(): string {
  const d = new Date();
  d.setDate(d.getDate() + 2);
  return dataLocal(d);
}
