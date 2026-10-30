import { ChangeDetectionStrategy, Component, ElementRef, Injector, OnInit, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { DbService } from '../../../core/db/db.service';
import { TokenService } from '../../../core/auth/token.service';
import { ToastService } from '../../../core/ui/toast.service';
import { OutboxService } from '../../../core/sync/outbox.service';
import { SyncService } from '../../../core/sync/sync.service';
import { ConnectivityService } from '../../../core/sync/connectivity.service';
import { TurnoService } from '../../../core/turno/turno.service';
import { RascunhoService, VinculoRascunho } from '../../../core/sessao/rascunho.service';
import { SessaoService } from '../../../core/sessao/sessao.service';
import { EstoqueLocal, SaldoSerieLocal } from '../../../core/db/models';
import {
  BarraTopoComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, OpcaoSeletor, QuantidadeComponent, SeletorComponent, VazioComponent,
} from '../../../ui';

interface SelectedItem {
  idMaterial: string;
  codigo: string;
  descricao: string;
  unidadeMedida: string;
  quantidade: number;
  condicao: 'NOVO' | 'SUCATA';
  detalhaSerie: boolean;
  numSerie?: string | null;
  saldoDisponivel: number;
}

interface SaldoComMaterial {
  idMaterial: string;
  codigo: string;
  descricao: string;
  unidadeMedida: string;
  quantidade: number;
  condicao: 'NOVO' | 'SUCATA';
  detalhaSerie: boolean;
}

type Parte = 'entregador' | 'recebedor';

/** Transferência em andamento — inclusive as assinaturas já colhidas — gravada no aparelho. */
interface RascunhoTransferencia {
  passo: number;
  idDestino: string | null;
  itens: SelectedItem[];
  assinaturaEntregador: string | null;
  assinaturaRecebedor: string | null;
  assinando: Parte | null;
  seriesDe: string | null;
  seletorAberto: boolean;
}

const TITULOS = ['Estoque de destino', 'Seleção de itens', 'Revisão dos itens', 'Assinatura digital'];

/**
 * Transferência do estoque do caminhão para outro estoque, com assinatura de quem
 * entrega e de quem recebe. Regras mantidas: turno aberto, não passar do saldo,
 * séries do saldo, as duas assinaturas obrigatórias. Cada assinatura confirmada vai
 * para o rascunho na hora: o app morrer entre uma e outra não obriga a colher de novo.
 */
@Component({
  selector: 'app-nova-transferencia',
  standalone: true,
  imports: [BarraTopoComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, QuantidadeComponent, SeletorComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tela">
      <ces-barra-topo titulo="Transferir material" [subtitulo]="stepTitle() + ' · ' + currentStep() + ' de 4'" navegacao="fechar" (voltar)="fechar()">
        <ces-indicador-rede />
      </ces-barra-topo>
      <div class="passos" aria-hidden="true">@for (s of [1, 2, 3, 4]; track s) { <i [class.feito]="s <= currentStep()"></i> }</div>

      <section class="conteudo" [attr.aria-label]="stepTitle()">
        @if (currentStep() === 1) {
          <div class="campos">
            <div class="fixo"><span>Sai de</span><strong>{{ nomeEstoqueOrigem() }}</strong></div>
            <ces-seletor rotulo="Vai para" [obrigatorio]="true" [opcoes]="opcoesDestino()" [valor]="idEstoqueDestino()"
              (valorChange)="idEstoqueDestino.set($event)" [invalido]="tentou() && !idEstoqueDestino()"
              [aberto]="seletorAberto()" (abertoChange)="seletorAberto.set($event)" />
          </div>
        }

        @if (currentStep() === 2) {
          @if (selectedItens().length) {
            <p class="contagem ces-num">{{ selectedItens().length }} {{ selectedItens().length === 1 ? 'item escolhido' : 'itens escolhidos' }}</p>
          }
          <div class="busca">
            <ces-icone nome="busca" [tamanho]="18" />
            <input type="search" placeholder="Nome ou código" enterkeyhint="search" aria-label="Buscar no saldo"
              [value]="busca()" (input)="busca.set($any($event.target).value)">
          </div>
          @if (saldosFiltrados().length) {
            <ul class="ces-grupo">
              @for (s of saldosFiltrados(); track s.idMaterial + s.condicao) {
                <li class="linha-lista">
                  <div class="ces-linha item">
                    <span class="ces-linha__texto">
                      <span class="ces-linha__titulo">{{ s.descricao }}</span>
                      <span class="ces-linha__apoio ces-num">Cód. {{ s.codigo }} · <span class="cond" [class.sucata]="s.condicao !== 'NOVO'">{{ s.condicao === 'NOVO' ? 'Novo' : 'Sucata' }}</span></span>
                    </span>
                    <span class="saldo ces-num"><span><strong>{{ num(s.quantidade) }}</strong> {{ s.unidadeMedida }}</span><small>no caminhão</small></span>
                    @if (s.detalhaSerie) {
                      <button type="button" class="ces-btn ces-btn--tonal ces-btn--compacto" (click)="openSerialPicker(s)">
                        {{ getSelectedSeriais(s.idMaterial, s.condicao).length ? getSelectedSeriais(s.idMaterial, s.condicao).length + ' série(s)' : 'Escolher séries' }}
                      </button>
                    } @else if (getSelection(s.idMaterial, s.condicao); as sel) {
                      <ces-quantidade [grande]="true" [unidade]="s.unidadeMedida" [valor]="sel.quantidade" [minimo]="1" (valorChange)="setQty(s, $event)" [rotulo]="'quantidade de ' + s.descricao" />
                      <button type="button" class="ces-btn-icone" (click)="removerItem(s)" [attr.aria-label]="'Tirar ' + s.descricao"><ces-icone nome="lixeira" [tamanho]="20" /></button>
                    } @else {
                      <button type="button" class="ces-btn ces-btn--tonal ces-btn--compacto" (click)="addMaterial(s)" [attr.aria-label]="'Adicionar ' + s.descricao">Adicionar</button>
                    }
                  </div>
                </li>
              }
            </ul>
          } @else {
            <ces-vazio icone="caminhao" [titulo]="busca() ? 'Nada encontrado' : 'Caminhão sem saldo'" [texto]="busca() ? 'Confira o nome ou o código.' : 'Não há material para transferir.'" />
          }
        }

        @if (currentStep() === 3) {
          <div class="fixo resumo-topo"><span>De {{ nomeEstoqueOrigem() }} para</span><strong>{{ nomeEstoqueDestino() }}</strong></div>
          <h2 class="ces-secao"><span>Itens</span><span class="ces-num">{{ selectedItens().length }}</span></h2>
          <ul class="ces-grupo resumo-lista" aria-label="Itens da transferência">
            @for (i of selectedItens(); track i.idMaterial + i.condicao + (i.numSerie ?? '')) {
              <li><div class="ces-linha"><span class="ces-linha__texto">
                <span class="ces-linha__titulo">{{ i.descricao }}</span>
                <span class="ces-linha__apoio ces-num">{{ i.numSerie ? 'Série ' + i.numSerie : 'Cód. ' + i.codigo }} · <span class="cond" [class.sucata]="i.condicao !== 'NOVO'">{{ i.condicao === 'NOVO' ? 'Novo' : 'Sucata' }}</span></span>
              </span>
              <span class="qtd-fixa ces-num">{{ num(i.quantidade) }} <small>{{ i.unidadeMedida }}</small></span></div></li>
            }
          </ul>
        }

        @if (currentStep() === 4) {
          <p class="intro">As duas assinaturas são obrigatórias. Cada uma fica guardada no aparelho assim que é confirmada.</p>
          <div class="assinaturas">
            @for (p of partes; track p) {
              <button type="button" class="assinatura" [class.feita]="!!assinatura(p)" (click)="openSignaturePad(p)">
                <span class="rotulo">{{ p === 'entregador' ? 'Quem entrega' : 'Quem recebe' }}</span>
                @if (assinatura(p); as img) { <img [src]="img" [alt]="'Assinatura de ' + (p === 'entregador' ? 'quem entrega' : 'quem recebe')"> }
                @else { <span class="vazia"><ces-icone nome="editar" />Toque para assinar</span> }
              </button>
            }
          </div>
        }
      </section>
    </div>

    <footer class="rodape">
      @if (currentStep() > 1) { <button type="button" class="ces-btn ces-btn--neutro" (click)="prevStep()">Voltar</button> }
      <button type="button" class="ces-btn ces-btn--principal principal" (click)="nextStep()" [disabled]="isSubmitting()">
        @if (currentStep() === 4) { {{ isSubmitting() ? 'Salvando…' : 'Confirmar transferência' }} }
        @else { Continuar <ces-icone nome="seta" [tamanho]="18" /> }
      </button>
    </footer>

    <ces-folha [aberta]="!!seriesDe()" (fechar)="fecharSeries()" [titulo]="seriesDe()?.descricao ?? ''" descricao="Marque as séries que vão.">
      <ul class="ces-grupo lista">
        @for (sr of availableSeriais(); track sr.id) {
          <li><button type="button" role="checkbox" class="ces-linha ces-linha--acao" [attr.aria-checked]="isSerialSelected(sr.numSerie)" (click)="toggleSerialSelection(sr.numSerie)">
            <span class="caixa" aria-hidden="true"><ces-icone nome="check" [tamanho]="14" /></span>
            <span class="ces-linha__texto"><span class="ces-linha__titulo ces-num">{{ sr.numSerie }}</span></span>
          </button></li>
        } @empty { <li><div class="ces-linha"><span class="ces-linha__apoio">Nenhuma série no saldo</span></div></li> }
      </ul>
      <div rodape class="folha-rodape"><button type="button" class="ces-btn ces-btn--principal ces-btn--bloco" (click)="fecharSeries()">Pronto</button></div>
    </ces-folha>

    <ces-folha [aberta]="activeSignatureModal() !== null" (fechar)="closeSignaturePad()"
      [titulo]="activeSignatureModal() === 'entregador' ? 'Assinatura de quem entrega' : 'Assinatura de quem recebe'" descricao="Assine com o dedo no quadro.">
      <div class="quadro">
        <canvas #quadro aria-label="Quadro de assinatura" (pointerdown)="iniciarTraco($event)" (pointermove)="tracar($event)"
          (pointerup)="terminarTraco()" (pointercancel)="terminarTraco()" (pointerleave)="terminarTraco()"></canvas>
      </div>
      <div rodape class="folha-rodape duas">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="limparQuadro()">Limpar</button>
        <button type="button" class="ces-btn ces-btn--principal" (click)="saveSignature()" [disabled]="!hasDrawnInModal()">Confirmar assinatura</button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100%; background: var(--ces-fundo); }
    /* Nada encolhe: com conteúdo maior que a tela, a página rola (grupos com overflow:hidden eram cortados). */
    :host > * { flex-shrink: 0; }
    .tela { flex: 1; padding-bottom: var(--ces-e5); }
    .passos { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; padding: 0 var(--ces-e5) var(--ces-e3); }
    .passos i { height: 4px; border-radius: var(--ces-r-pilula); background: var(--ces-linha); }
    .passos i.feito { background: var(--ces-destaque); }
    .conteudo { animation: entrar var(--ces-m-entrar) var(--ces-curva); }
    @keyframes entrar { from { opacity: 0; transform: translateY(6px); } }
    .campos { display: grid; gap: var(--ces-e2); padding: var(--ces-e2) var(--ces-e3); }
    .fixo { display: grid; gap: 2px; padding: var(--ces-e3) var(--ces-e4); background: var(--ces-superficie); border-radius: var(--ces-r-grupo); }
    .fixo span { font: 500 .8125rem/1.3 var(--ces-fonte); color: var(--ces-texto-2); }
    .resumo-topo { margin: var(--ces-e2) var(--ces-e3) var(--ces-e3); }
    .contagem { margin: var(--ces-e2) var(--ces-e5) 0; font: var(--ces-t-corpo-forte); color: var(--ces-texto-2); }
    .busca { margin: var(--ces-e2) var(--ces-e3); display: flex; align-items: center; gap: var(--ces-e2); padding: 0 var(--ces-e4); min-height: 48px;
      border-radius: var(--ces-r-pilula); background: var(--ces-superficie); color: var(--ces-texto-2); }
    .busca input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ces-texto); font: var(--ces-t-corpo); font-size: 1rem; }
    .busca input:focus { outline: none; }
    .item { flex-wrap: wrap; }
    .linha-lista { content-visibility: auto; contain-intrinsic-size: auto 96px; }
    .qtd-fixa { font: 700 1.375rem/1 var(--ces-fonte); white-space: nowrap; }
    .qtd-fixa small { font: 600 .875rem/1 var(--ces-fonte); color: var(--ces-texto-2); }
    .resumo-lista .ces-linha { min-height: 56px; }
    .saldo { display: grid; justify-items: end; font: 600 .875rem/1.2 var(--ces-fonte); color: var(--ces-texto-2); white-space: nowrap; }
    .saldo strong { font: 700 1.25rem/1.1 var(--ces-fonte); color: var(--ces-texto); }
    .saldo small { font: var(--ces-t-apoio); font-size: .75rem; }
    .item .ces-linha__texto { flex: 1 1 55%; }
    .cond { font-weight: 600; color: var(--ces-ok); }
    .cond.sucata { color: var(--ces-atencao); }
    .intro { margin: var(--ces-e2) var(--ces-e5) var(--ces-e3); color: var(--ces-texto-2); font: var(--ces-t-apoio); font-size: .875rem; }
    .assinaturas { display: grid; gap: var(--ces-e3); padding: 0 var(--ces-e3); }
    .assinatura { display: grid; gap: var(--ces-e2); padding: var(--ces-e3) var(--ces-e4); min-height: 140px; border: 1.5px dashed var(--ces-linha-forte);
      border-radius: var(--ces-r-grupo); background: var(--ces-superficie); text-align: left; color: var(--ces-texto); cursor: pointer; }
    .assinatura.feita { border-style: solid; border-color: var(--ces-ok); }
    .assinatura .rotulo { font: 500 .8125rem/1.3 var(--ces-fonte); color: var(--ces-texto-2); }
    .assinatura img { max-width: 100%; height: 90px; object-fit: contain; background: #fff; border-radius: 8px; }
    .vazia { display: flex; align-items: center; justify-content: center; gap: var(--ces-e2); color: var(--ces-destaque); font-weight: 600; min-height: 90px; }
    .lista { background: var(--ces-fundo); }
    .caixa { width: 22px; height: 22px; border-radius: 6px; border: 2px solid var(--ces-linha-forte); display: grid; place-items: center; color: transparent; flex: none; }
    [aria-checked='true'] > .caixa { background: var(--ces-destaque); border-color: var(--ces-destaque); color: var(--ces-destaque-texto); }
    .quadro { padding: 0 var(--ces-e4); }
    .quadro canvas { width: 100%; height: 240px; border-radius: var(--ces-r-grupo); background: #fff; border: 1px solid var(--ces-linha-forte);
      touch-action: none; display: block; }
    .folha-rodape { padding: var(--ces-e3) var(--ces-e4) 0; }
    .folha-rodape.duas { display: flex; gap: var(--ces-e3); }
    .folha-rodape.duas .ces-btn { flex: 1; }
    .rodape { position: sticky; bottom: 0; z-index: 5; display: flex; gap: var(--ces-e3); padding: var(--ces-e3) var(--ces-e4) calc(var(--ces-e3) + var(--ces-safe-base));
      background: var(--ces-fundo); border-top: 1px solid var(--ces-linha); }
    .rodape .principal { flex: 1; min-height: var(--ces-alvo-principal); font-size: 1rem; }
  `],
})
export class NovaTransferenciaComponent implements OnInit {
  private readonly dbService = inject(DbService);
  private readonly tokenService = inject(TokenService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);
  private readonly syncService = inject(SyncService);
  private readonly connectivityService = inject(ConnectivityService);
  private readonly turnoService = inject(TurnoService);
  private readonly rascunhos = inject(RascunhoService);
  private readonly sessao = inject(SessaoService, { optional: true });
  private readonly injector = inject(Injector);

  readonly partes: Parte[] = ['entregador', 'recebedor'];
  readonly currentStep = signal(1);
  readonly isSubmitting = signal(false);
  readonly tentou = signal(false);
  readonly seletorAberto = signal(false);
  readonly idEstoqueOrigem = signal<string>(this.tokenService.estoqueId() || '');
  readonly idEstoqueDestino = signal<string | null>(null);
  readonly allEstoques = signal<EstoqueLocal[]>([]);
  readonly saldosLocais = signal<SaldoComMaterial[]>([]);
  readonly selectedItens = signal<SelectedItem[]>([]);
  readonly busca = signal('');

  readonly assinaturaEntregador = signal<string | null>(null);
  readonly assinaturaRecebedor = signal<string | null>(null);
  readonly activeSignatureModal = signal<Parte | null>(null);
  readonly hasDrawnInModal = signal(false);
  private readonly quadro = viewChild<ElementRef<HTMLCanvasElement>>('quadro');
  private ctx: CanvasRenderingContext2D | null = null;
  private desenhando = false;

  readonly seriesDe = signal<SaldoComMaterial | null>(null);
  readonly availableSeriais = signal<SaldoSerieLocal[]>([]);
  private rascunho: VinculoRascunho | null = null;

  readonly opcoesDestino = computed<OpcaoSeletor[]>(() => this.allEstoques()
    .filter(e => e.id !== this.idEstoqueOrigem() && e.ativo !== false)
    .map(e => ({ valor: e.id, rotulo: e.nomeIdentificador })));
  readonly nomeEstoqueOrigem = computed(() => this.allEstoques().find(e => e.id === this.idEstoqueOrigem())?.nomeIdentificador || 'Estoque da equipe');
  readonly nomeEstoqueDestino = computed(() => this.allEstoques().find(e => e.id === this.idEstoqueDestino())?.nomeIdentificador || 'Não selecionado');
  readonly saldosFiltrados = computed(() => {
    const q = this.busca().toLowerCase().trim();
    const lista = this.saldosLocais();
    return q ? lista.filter(s => s.descricao.toLowerCase().includes(q) || s.codigo.toLowerCase().includes(q)) : lista;
  });

  readonly canAdvance = computed(() => {
    switch (this.currentStep()) {
      case 1: return !!this.idEstoqueDestino();
      case 2: return this.selectedItens().length > 0;
      case 4: return !!this.assinaturaEntregador() && !!this.assinaturaRecebedor();
      default: return true;
    }
  });
  readonly stepTitle = computed(() => TITULOS[this.currentStep() - 1] ?? '');

  constructor() {
    // Ao abrir o quadro (inclusive restaurado após o app reabrir), prepara a tela de desenho.
    effect(() => {
      const parte = this.activeSignatureModal();
      const canvas = this.quadro();
      if (parte && canvas) untracked(() => requestAnimationFrame(() => this.prepararQuadro()));
    });
  }

  async ngOnInit(): Promise<void> {
    if (!this.idEstoqueOrigem()) {
      this.toastService.error('O estoque da sua equipe não foi identificado.');
      void this.router.navigate(['/transferencias']);
      return;
    }
    // Carrega antes de checar: ao reabrir o app direto nesta tela, ninguém carregou o turno ainda.
    await this.turnoService.loadTurnoAtivo();
    if (!this.turnoService.turnoAtivo()) {
      this.toastService.warning('Abra o turno para transferir material.');
      void this.router.navigate(['/transferencias']);
      return;
    }
    await this.loadData();
    this.rascunho = this.rascunhos.vincular<RascunhoTransferencia>({
      chave: 'transferencia:nova',
      injector: this.injector,
      ler: () => ({
        passo: this.currentStep(), idDestino: this.idEstoqueDestino(), itens: this.selectedItens(),
        assinaturaEntregador: this.assinaturaEntregador(), assinaturaRecebedor: this.assinaturaRecebedor(),
        assinando: this.activeSignatureModal(), seriesDe: this.seriesDe() ? `${this.seriesDe()!.idMaterial}|${this.seriesDe()!.condicao}` : null,
        seletorAberto: this.seletorAberto(),
      }),
      aplicar: r => this.aplicarRascunho(r),
      titulo: () => `Transferência · ${this.stepTitle().toLowerCase()}`,
      rota: () => '/transferencias/nova',
      vazio: r => r.passo === 1 && !r.idDestino && !r.itens.length && !r.seletorAberto,
    });
    await this.rascunho.restaurado;
  }

  async loadData(): Promise<void> {
    try {
      const [estoques, saldos] = await Promise.all([
        this.dbService.getEstoques(),
        this.dbService.getSaldosComMateriais(this.idEstoqueOrigem()),
      ]);
      this.allEstoques.set(estoques);
      this.saldosLocais.set(saldos as SaldoComMaterial[]);
    } catch (error) {
      console.error('Erro ao carregar dados:', error);
    }
  }

  private async aplicarRascunho(r: RascunhoTransferencia): Promise<void> {
    this.currentStep.set(Math.min(Math.max(1, r.passo || 1), 4));
    this.idEstoqueDestino.set(r.idDestino);
    this.selectedItens.set(r.itens ?? []);
    this.assinaturaEntregador.set(r.assinaturaEntregador);
    this.assinaturaRecebedor.set(r.assinaturaRecebedor);
    this.seletorAberto.set(!!r.seletorAberto);
    if (r.seriesDe) {
      const [id, condicao] = r.seriesDe.split('|');
      const s = this.saldosLocais().find(x => x.idMaterial === id && x.condicao === condicao);
      if (s) await this.openSerialPicker(s);
    }
    if (r.assinando) this.openSignaturePad(r.assinando);
  }

  fechar(): void {
    void this.rascunho?.gravarEstadoAtual();
    if (this.selectedItens().length || this.assinaturaEntregador()) this.toastService.info('Rascunho guardado. Continue pela tela Hoje.');
    const anterior = this.sessao?.rotaAnterior();
    void this.router.navigateByUrl(anterior && !anterior.startsWith('/transferencias/nova') ? anterior : '/transferencias');
  }

  /** Número no formato brasileiro (vírgula decimal). */
  num(n: number): string {
    return String(n).replace('.', ',');
  }

  assinatura(p: Parte): string | null {
    return p === 'entregador' ? this.assinaturaEntregador() : this.assinaturaRecebedor();
  }

  // ---------- itens ----------
  addMaterial(s: SaldoComMaterial): void {
    this.selectedItens.update(l => [...l, {
      idMaterial: s.idMaterial, codigo: s.codigo, descricao: s.descricao, unidadeMedida: s.unidadeMedida, quantidade: 1,
      condicao: s.condicao, detalhaSerie: s.detalhaSerie, saldoDisponivel: s.quantidade,
    }]);
  }

  getSelection(idMaterial: string, condicao: string): SelectedItem | undefined {
    return this.selectedItens().find(i => i.idMaterial === idMaterial && i.condicao === condicao && !i.numSerie);
  }

  setQty(s: SaldoComMaterial, qty: number): void {
    if (qty < 1) return;
    if (qty > s.quantidade) {
      this.toastService.warning('Mais do que o saldo do caminhão.');
      return;
    }
    this.selectedItens.update(l => l.map(i => (i.idMaterial === s.idMaterial && i.condicao === s.condicao && !i.numSerie ? { ...i, quantidade: qty } : i)));
  }

  removerItem(s: SaldoComMaterial): void {
    this.selectedItens.update(l => l.filter(i => !(i.idMaterial === s.idMaterial && i.condicao === s.condicao && !i.numSerie)));
  }

  async openSerialPicker(s: SaldoComMaterial): Promise<void> {
    try {
      this.availableSeriais.set(await this.dbService.getSeriaisPorMaterial(this.idEstoqueOrigem(), s.idMaterial, s.condicao));
      this.seriesDe.set(s);
    } catch (error) {
      console.error('Erro ao buscar séries:', error);
    }
  }

  fecharSeries(): void {
    this.seriesDe.set(null);
  }

  isSerialSelected(numSerie: string): boolean {
    const s = this.seriesDe();
    return !!s && this.selectedItens().some(i => i.idMaterial === s.idMaterial && i.condicao === s.condicao && i.numSerie === numSerie);
  }

  toggleSerialSelection(numSerie: string): void {
    const s = this.seriesDe();
    if (!s) return;
    if (this.isSerialSelected(numSerie)) {
      this.selectedItens.update(l => l.filter(i => !(i.idMaterial === s.idMaterial && i.condicao === s.condicao && i.numSerie === numSerie)));
    } else {
      this.selectedItens.update(l => [...l, {
        idMaterial: s.idMaterial, codigo: s.codigo, descricao: s.descricao, unidadeMedida: s.unidadeMedida, quantidade: 1,
        condicao: s.condicao, detalhaSerie: true, numSerie, saldoDisponivel: 1,
      }]);
    }
  }

  getSelectedSeriais(idMaterial: string, condicao: string): string[] {
    return this.selectedItens().filter(i => i.idMaterial === idMaterial && i.condicao === condicao && i.numSerie).map(i => i.numSerie!);
  }

  // ---------- assinatura ----------
  openSignaturePad(parte: Parte): void {
    this.activeSignatureModal.set(parte);
    this.hasDrawnInModal.set(false);
  }

  closeSignaturePad(): void {
    this.activeSignatureModal.set(null);
  }

  private prepararQuadro(): void {
    const canvas = this.quadro()?.nativeElement;
    if (!canvas) return;
    const r = canvas.getBoundingClientRect();
    const escala = window.devicePixelRatio || 1;
    canvas.width = r.width * escala;
    canvas.height = r.height * escala;
    this.ctx = canvas.getContext('2d');
    if (!this.ctx) return;
    this.ctx.scale(escala, escala);
    this.ctx.fillStyle = '#ffffff';
    this.ctx.fillRect(0, 0, r.width, r.height);
    this.ctx.lineWidth = 3;
    this.ctx.lineCap = 'round';
    this.ctx.lineJoin = 'round';
    this.ctx.strokeStyle = '#0f172a';
    this.hasDrawnInModal.set(false);
  }

  private ponto(e: PointerEvent): { x: number; y: number } | null {
    const canvas = this.quadro()?.nativeElement;
    if (!canvas) return null;
    const r = canvas.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  }

  iniciarTraco(e: PointerEvent): void {
    e.preventDefault();
    (e.target as Element).setPointerCapture?.(e.pointerId);
    const p = this.ponto(e);
    if (!this.ctx || !p) return;
    this.desenhando = true;
    this.ctx.beginPath();
    this.ctx.moveTo(p.x, p.y);
  }

  tracar(e: PointerEvent): void {
    if (!this.desenhando || !this.ctx) return;
    const p = this.ponto(e);
    if (!p) return;
    this.ctx.lineTo(p.x, p.y);
    this.ctx.stroke();
    this.ctx.beginPath();
    this.ctx.moveTo(p.x, p.y);
    this.hasDrawnInModal.set(true);
  }

  terminarTraco(): void {
    this.desenhando = false;
    this.ctx?.beginPath();
  }

  limparQuadro(): void {
    this.prepararQuadro();
  }

  saveSignature(): void {
    if (!this.hasDrawnInModal()) {
      this.toastService.warning('Assine no quadro antes de confirmar.');
      return;
    }
    const canvas = this.quadro()?.nativeElement;
    if (!canvas) return;
    const imagem = canvas.toDataURL('image/png');
    if (this.activeSignatureModal() === 'entregador') this.assinaturaEntregador.set(imagem);
    else if (this.activeSignatureModal() === 'recebedor') this.assinaturaRecebedor.set(imagem);
    this.activeSignatureModal.set(null);
    // Assinatura colhida não pode depender do debounce: grava já.
    void this.rascunho?.gravarEstadoAtual();
  }

  // ---------- fluxo ----------
  nextStep(): void {
    this.tentou.set(true);
    if (!this.canAdvance()) {
      const msg = ['Escolha para onde vai o material.', 'Adicione ao menos um item.', '', 'Colha as duas assinaturas para confirmar.'][this.currentStep() - 1];
      if (msg) this.toastService.warning(msg);
      return;
    }
    if (this.currentStep() === 4) {
      void this.confirm();
      return;
    }
    this.tentou.set(false);
    this.currentStep.update(s => s + 1);
  }

  prevStep(): void {
    if (this.currentStep() > 1) this.currentStep.update(s => s - 1);
  }

  async confirm(): Promise<void> {
    if (!this.canAdvance() || this.isSubmitting()) return;
    try {
      this.isSubmitting.set(true);
      const idDestino = this.idEstoqueDestino();
      if (!idDestino) return;
      const itens = this.selectedItens().map(i => ({ idMaterial: i.idMaterial, quantidade: i.quantidade, condicao: i.condicao, numSerie: i.numSerie || null }));
      await this.dbService.registrarTransferenciaOffline(
        this.idEstoqueOrigem(), idDestino, itens, this.assinaturaEntregador() || undefined, this.assinaturaRecebedor() || undefined,
      );
      await this.rascunho?.concluir();
      if (this.connectivityService.isOnline()) this.syncService.syncOutbox();
      this.toastService.success('Transferência registrada. Sai quando houver internet.');
      this.router.navigate(['/transferencias']);
    } catch (error) {
      console.error('Erro ao confirmar transferência:', error);
      this.toastService.error('Não foi possível registrar. Os dados continuam aqui.');
    } finally {
      this.isSubmitting.set(false);
    }
  }
}
