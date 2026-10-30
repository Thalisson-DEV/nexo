import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesChartComponent,
  CesChartPointSelection,
  CesDataTableComponent,
  CesDrawerComponent,
  CesIconButtonComponent,
  CesTabItem,
  CesTabPanelDirective,
  CesTabsComponent,
  CesTabValue
} from '@ces/ui';
import {
  AcaoAnalise,
  AlvoAnalise,
  DetalheRegistro,
  FiltroOrigem,
  GraficoAnalise,
  LinkAnalise,
  OrigemAnalise,
  TabelaAnalise,
  VistaAnalise
} from './dashboard-analise.models';

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const ORIGEM_PADRAO = 'origem';

interface EstadoOrigem {
  readonly carregando: boolean;
  readonly erro: boolean;
  readonly linhas: readonly unknown[];
  readonly total: number;
}

interface Conferencia {
  readonly tom: string;
  readonly icone: string;
  readonly texto: string;
}

const VAZIO: EstadoOrigem = { carregando: false, erro: false, linhas: [], total: 0 };

/**
 * Análise detalhada de um elemento do dashboard (cartão, ponto de gráfico, linha de tabela): painel
 * largo com indicadores do recorte, gráficos secundários clicáveis (que aprofundam ou filtram), tabelas
 * analíticas e a **origem dos dados** — os registros que compõem o número, com conferência contra o
 * painel, detalhe de cada registro e exportação. Uma vista pode ter mais de uma origem (ex.: inspeções e
 * desvios críticos do dia), cada uma em sua aba.
 *
 * O dashboard só descreve a vista (`VistaAnalise`); navegação empilhada ("Voltar"), vizinhos na mesma
 * dimensão, estados de carga/erro/vazio e o atalho do Copiloto ficam aqui.
 */
@Component({
  selector: 'app-dashboard-analise',
  standalone: true,
  imports: [
    RouterLink,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesChartComponent,
    CesDataTableComponent,
    CesDrawerComponent,
    CesIconButtonComponent,
    CesTabPanelDirective,
    CesTabsComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './dashboard-analise.component.css',
  template: `
    @if (vista(); as v) {
      <ces-drawer [visible]="true" (visibleChange)="!$event && fechar.emit()" size="xl"
        [eyebrow]="v.eyebrow" [title]="v.titulo" [description]="v.descricao" closeLabel="Fechar análise">

        <div class="an-topo">
          @if (trilha().length) {
            <nav class="an-trilha" aria-label="Caminho da análise">
              <ol>
                @for (passo of trilha(); track $index) {
                  <li>
                    <button type="button" (click)="voltar.emit(trilha().length - $index)">
                      @if ($first) { <i class="pi pi-arrow-left" aria-hidden="true"></i> }
                      {{ passo }}
                    </button>
                  </li>
                }
                <li aria-current="page"><span>{{ v.titulo }}</span></li>
              </ol>
            </nav>
          }
          <div class="an-topo__acoes">
            @if (v.anterior || v.proximo) {
              <div class="an-passos" role="group" [attr.aria-label]="'Navegar entre ' + v.eyebrow.toLowerCase()">
                <ces-icon-button icon="pi pi-chevron-left" size="icon-sm" variant="outline"
                  [label]="v.anterior ? 'Ir para ' + v.anterior.rotulo : 'Não há anterior'" [tooltip]="v.anterior?.rotulo"
                  [disabled]="!v.anterior" (pressed)="v.anterior && trocar.emit(v.anterior.alvo)"></ces-icon-button>
                <ces-icon-button icon="pi pi-chevron-right" size="icon-sm" variant="outline"
                  [label]="v.proximo ? 'Ir para ' + v.proximo.rotulo : 'Não há seguinte'" [tooltip]="v.proximo?.rotulo"
                  [disabled]="!v.proximo" (pressed)="v.proximo && trocar.emit(v.proximo.alvo)"></ces-icon-button>
              </div>
            }
            @if (copiloto() && v.copiloto) {
              <button type="button" class="an-copiloto" (click)="perguntar.emit(v)"
                [attr.aria-label]="'Analisar ' + v.titulo + ' com o Copiloto'">
                <img src="icons/copiloto-1.png" alt="" aria-hidden="true" />
                <span>Analisar com o Copiloto</span>
                <i class="pi pi-sparkles" aria-hidden="true"></i>
              </button>
            }
          </div>
        </div>

        @if (v.destaques?.length) {
          <ul class="an-destaques" aria-label="Leituras do recorte">
            @for (d of v.destaques; track d.texto) {
              <li [attr.data-tone]="d.tom"><i [class]="d.icone" aria-hidden="true"></i><span>{{ d.texto }}</span></li>
            }
          </ul>
        }

        @if (v.kpis.length) {
          <section class="an-kpis" aria-label="Indicadores do recorte">
            @for (k of v.kpis; track k.rotulo) {
              <div class="an-kpi" [attr.data-tone]="k.tom ?? 'neutral'">
                <span class="an-kpi__rotulo">
                  @if (k.icone) { <i [class]="k.icone" aria-hidden="true"></i> }
                  {{ k.rotulo }}
                </span>
                <strong>{{ k.valor }}</strong>
                @if (k.detalhe) { <small>{{ k.detalhe }}</small> }
              </div>
            }
          </section>
        }

        <ces-tabs [tabs]="abas()" [value]="aba()" (valueChange)="aba.set($event ?? 'visao')" ariaLabel="Seções da análise">
          <ng-template cesTabPanel="visao">
            @if (graficos().length || carregandoDerivados()) {
              <div class="an-graficos">
                @for (g of graficos(); track g.id) {
                  <article class="an-grafico" [class.an-grafico--inteiro]="g.inteiro">
                    <header>
                      <h3>{{ g.titulo }}</h3>
                      @if (g.descricao) { <p>{{ g.descricao }}</p> }
                    </header>
                    <ces-chart [kind]="g.tipo" [title]="g.titulo" [showTitle]="false" [labels]="g.rotulos" [series]="g.series"
                      [valueFormat]="g.formato ?? 'number'" [height]="g.altura ?? 240" [note]="g.nota"
                      [selectable]="!!g.aoSelecionar" [selectLabel]="g.rotuloSelecao ?? 'Detalhar'"
                      (pointSelect)="selecionarPonto(g, $event)"></ces-chart>
                  </article>
                }
                @if (carregandoDerivados()) {
                  <div class="an-grafico an-grafico--esqueleto" aria-hidden="true"></div>
                }
              </div>
            } @else {
              <p class="an-vazio"><i class="pi pi-chart-bar" aria-hidden="true"></i> Sem gráficos para este recorte.</p>
            }
          </ng-template>

          @for (t of v.tabelas ?? []; track t.id) {
            <ng-template [cesTabPanel]="t.id">
              <div class="an-tabela">
                @if (t.descricao) { <p class="an-nota">{{ t.descricao }}</p> }
                <ces-data-table [caption]="t.titulo" [columns]="$any(t.colunas)" [rows]="t.linhas"
                  [rowKey]="$any(t.chaveLinha)" [rowLabel]="$any(t.rotuloLinha)"
                  [rowActionLabel]="t.aoAtivar ? (t.rotuloAcao ?? 'Analisar') : undefined"
                  [stateTitle]="t.vazio ?? 'Nada neste recorte'" [exportFileName]="t.nomeArquivo"
                  (rowActivate)="ativarLinha(t, $event)"></ces-data-table>
              </div>
            </ng-template>
          }

          @for (o of origens(); track idDe(o)) {
            <ng-template [cesTabPanel]="idDe(o)">
              <div class="an-origem">
                <header class="an-origem__cabecalho">
                  <div>
                    <h3>@if (o.icone) { <i [class]="o.icone" aria-hidden="true"></i> } {{ o.titulo }}</h3>
                    @if (o.descricao) { <p>{{ o.descricao }}</p> }
                  </div>
                  @if (conferencias()[idDe(o)]; as c) {
                    <p class="an-conferencia" [attr.data-tone]="c.tom" role="status">
                      <i [class]="c.icone" aria-hidden="true"></i><span>{{ c.texto }}</span>
                    </p>
                  }
                </header>

                @if (o.filtroInicial || filtros()[idDe(o)]) {
                  <div class="an-filtros" aria-label="Filtros da origem">
                    @if (o.filtroInicial; as fi) {
                      <span class="an-chip an-chip--fixo"><i class="pi pi-filter" aria-hidden="true"></i>{{ fi.rotulo }}</span>
                    }
                    @if (filtros()[idDe(o)]; as f) {
                      <span class="an-chip">
                        <i class="pi pi-filter" aria-hidden="true"></i>{{ f.rotulo }}
                        <button type="button" [attr.aria-label]="'Remover filtro ' + f.rotulo" (click)="definirFiltro(idDe(o), null)"><i class="pi pi-times" aria-hidden="true"></i></button>
                      </span>
                    }
                  </div>
                }

                @if (detalhes()[idDe(o)]; as dt) {
                  <section class="an-detalhe" #detalheRef tabindex="-1" [attr.aria-label]="'Detalhe: ' + dt.titulo">
                    <header>
                      <div>
                        <span class="an-detalhe__eyebrow">Registro selecionado</span>
                        <h4>{{ dt.titulo }}</h4>
                        @if (dt.subtitulo) { <p>{{ dt.subtitulo }}</p> }
                      </div>
                      <ces-icon-button icon="pi pi-times" size="icon-sm" label="Fechar detalhe" (pressed)="definirSelecionado(idDe(o), null)"></ces-icon-button>
                    </header>
                    <dl>
                      @for (c of dt.campos; track c.rotulo) {
                        <div><dt>@if (c.icone) { <i [class]="c.icone" aria-hidden="true"></i> } {{ c.rotulo }}</dt><dd>{{ c.valor }}</dd></div>
                      }
                    </dl>
                    @for (l of dt.listas ?? []; track l.rotulo) {
                      <div class="an-detalhe__lista">
                        <span>{{ l.rotulo }}</span>
                        @if (l.itens.length) {
                          <ul>@for (item of l.itens; track $index) { <li>{{ item }}</li> }</ul>
                        } @else {
                          <em>Nenhum</em>
                        }
                      </div>
                    }
                    @if (dt.links?.length) {
                      <div class="an-detalhe__links">
                        @for (lk of dt.links; track lk.rotulo) {
                          <a class="an-link" [routerLink]="$any(lk.rota)" [queryParams]="lk.queryParams ?? null">
                            <i [class]="lk.icone" aria-hidden="true"></i> {{ lk.rotulo }} <i class="pi pi-arrow-right" aria-hidden="true"></i>
                          </a>
                        }
                      </div>
                    }
                  </section>
                }

                @if (estadoDe(o).erro) {
                  <ces-async-state state="error" title="Não foi possível carregar a origem dos dados"
                    description="Os números do painel continuam válidos; tente carregar os registros novamente."
                    actionLabel="Tentar novamente" actionIcon="pi pi-refresh" (action)="carregarOrigem(o)"></ces-async-state>
                } @else {
                  <ces-data-table [caption]="o.titulo" [columns]="$any(o.colunas)" [rows]="visiveis()[idDe(o)] ?? []"
                    [state]="estadoDe(o).carregando ? 'loading' : 'ready'" [stateTitle]="o.vazio ?? 'Nenhum registro neste recorte'"
                    [rowKey]="$any(o.chaveLinha)" [rowLabel]="$any(o.rotuloLinha)"
                    [rowActionLabel]="o.detalhar ? 'Ver detalhe' : undefined" rowActionIcon="pi pi-eye"
                    [exportFileName]="o.nomeArquivo" (rowActivate)="selecionarRegistro(o, $event)"></ces-data-table>
                  @if (!estadoDe(o).carregando && !o.filtroInicial && estadoDe(o).total > estadoDe(o).linhas.length) {
                    <p class="an-nota">Mostrando {{ estadoDe(o).linhas.length.toLocaleString('pt-BR') }} de {{ estadoDe(o).total.toLocaleString('pt-BR') }} registros.
                      @if (o.verTudo) { Use “{{ o.verTudo.rotulo }}” para ver todos. }</p>
                  }
                }
              </div>
            </ng-template>
          }
        </ces-tabs>

        @for (lk of rodape(); track lk.rotulo) {
          <a drawer-footer class="an-link an-link--rodape" [routerLink]="$any(lk.rota)" [queryParams]="lk.queryParams ?? null">
            <i [class]="lk.icone" aria-hidden="true"></i> {{ lk.rotulo }}
          </a>
        }
        <ces-button drawer-footer label="Fechar" variant="secondary" (pressed)="fechar.emit()"></ces-button>
      </ces-drawer>
    }
  `
})
export class DashboardAnaliseComponent {
  private readonly assinaturas = new Map<string, Subscription>();
  private readonly detalheRef = viewChild<ElementRef<HTMLElement>>('detalheRef');

  readonly vista = input<VistaAnalise | null>(null);
  /** Títulos das análises anteriores (a última é a que "Voltar" reabre). */
  readonly trilha = input<readonly string[]>([]);
  /** O usuário pode usar o Copiloto (a tela decide pela permissão). */
  readonly copiloto = input(false);

  readonly fechar = output<void>();
  /** Aprofundar: abre outra análise empilhada sobre esta. */
  readonly navegar = output<AlvoAnalise>();
  /** Vizinho na mesma dimensão (dia anterior/seguinte): troca sem empilhar. */
  readonly trocar = output<AlvoAnalise>();
  /** Volta `n` passos na trilha. */
  readonly voltar = output<number>();
  readonly perguntar = output<VistaAnalise>();

  protected readonly aba = signal<CesTabValue>('visao');
  private readonly estados = signal<Readonly<Record<string, EstadoOrigem>>>({});
  protected readonly filtros = signal<Readonly<Record<string, FiltroOrigem | null>>>({});
  private readonly selecionados = signal<Readonly<Record<string, string | null>>>({});

  protected readonly idDe = (o: OrigemAnalise) => o.id ?? ORIGEM_PADRAO;

  protected readonly origens = computed<readonly OrigemAnalise[]>(() => {
    const v = this.vista();
    return v ? [...(v.origem ? [v.origem] : []), ...(v.origensExtras ?? [])] : [];
  });

  protected estadoDe(o: OrigemAnalise): EstadoOrigem {
    return this.estados()[this.idDe(o)] ?? VAZIO;
  }

  /** Registros de cada origem já com o filtro fixo da vista (ex.: a hora analisada). */
  private readonly bases = computed<Record<string, readonly unknown[]>>(() => {
    const mapa: Record<string, readonly unknown[]> = {};
    for (const o of this.origens()) {
      const linhas = this.estadoDe(o).linhas;
      mapa[this.idDe(o)] = o.filtroInicial ? linhas.filter((l) => o.filtroInicial!.aceita(l)) : linhas;
    }
    return mapa;
  });

  protected readonly visiveis = computed<Partial<Record<string, readonly unknown[]>>>(() => {
    const mapa: Partial<Record<string, readonly unknown[]>> = {};
    for (const [id, linhas] of Object.entries(this.bases())) {
      const f = this.filtros()[id];
      mapa[id] = f ? linhas.filter((l) => f.aceita(l)) : linhas;
    }
    return mapa;
  });

  protected readonly carregandoDerivados = computed(() => this.origens().some((o) => !!o.derivados && this.estadoDe(o).carregando));

  protected readonly graficos = computed<readonly GraficoAnalise[]>(() => {
    const v = this.vista();
    if (!v) return [];
    const derivados = this.origens().flatMap((o) => {
      const e = this.estadoDe(o);
      return o.derivados && !e.carregando && !e.erro ? o.derivados(this.bases()[this.idDe(o)] ?? []) : [];
    });
    return [...v.graficos, ...derivados];
  });

  protected readonly abas = computed<CesTabItem[]>(() => {
    const v = this.vista();
    if (!v) return [];
    const abas: CesTabItem[] = [{ value: 'visao', label: 'Visão geral', icon: 'pi pi-chart-bar' }];
    for (const t of v.tabelas ?? []) abas.push({ value: t.id, label: t.titulo, icon: t.icone, badge: t.linhas.length });
    for (const o of this.origens()) {
      const e = this.estadoDe(o);
      abas.push({
        value: this.idDe(o),
        label: o.rotuloAba,
        icon: o.icone ?? 'pi pi-database',
        badge: e.carregando ? '…' : e.erro ? '!' : o.filtroInicial ? (this.bases()[this.idDe(o)]?.length ?? 0) : e.total
      });
    }
    return abas;
  });

  protected readonly detalhes = computed<Record<string, DetalheRegistro | null>>(() => {
    const mapa: Record<string, DetalheRegistro | null> = {};
    for (const o of this.origens()) {
      const id = this.selecionados()[this.idDe(o)];
      const linhas = this.bases()[this.idDe(o)] ?? [];
      const linha = id && o.detalhar ? linhas.find((l) => o.chaveLinha(l) === id) : undefined;
      mapa[this.idDe(o)] = linha ? o.detalhar!(linha, linhas) : null;
    }
    return mapa;
  });

  /** A soma dos registros de cada origem fecha com o número do painel? */
  protected readonly conferencias = computed<Record<string, Conferencia | null>>(() => {
    const mapa: Record<string, Conferencia | null> = {};
    for (const o of this.origens()) mapa[this.idDe(o)] = this.conferir(o);
    return mapa;
  });

  protected readonly rodape = computed<readonly LinkAnalise[]>(() => {
    const v = this.vista();
    return [...(v?.links ?? []), ...this.origens().flatMap((o) => (o.verTudo ? [o.verTudo] : []))];
  });

  private ultimaVista: string | null = null;
  private cargas: Record<string, string> = {};

  constructor() {
    effect(() => {
      const v = this.vista();
      const origens = this.origens();
      untracked(() => {
        const chave = v?.chave ?? null;
        if (chave !== this.ultimaVista) {
          this.ultimaVista = chave;
          this.aba.set('visao');
          this.filtros.set({});
          this.selecionados.set({});
        }
        const ativas = new Set(origens.map((o) => this.idDe(o)));
        for (const id of Object.keys(this.cargas)) {
          if (!ativas.has(id)) this.descartar(id);
        }
        for (const o of origens) {
          if (this.cargas[this.idDe(o)] !== o.chave) this.carregarOrigem(o);
        }
      });
    });
    inject(DestroyRef).onDestroy(() => this.assinaturas.forEach((a) => a.unsubscribe()));
  }

  carregarOrigem(o: OrigemAnalise): void {
    const id = this.idDe(o);
    this.assinaturas.get(id)?.unsubscribe();
    this.cargas = { ...this.cargas, [id]: o.chave };
    this.definirEstado(id, { carregando: true, erro: false, linhas: [], total: 0 });
    this.assinaturas.set(id, o.carregar().subscribe({
      next: ({ linhas, total }) => this.definirEstado(id, { carregando: false, erro: false, linhas, total }),
      error: () => this.definirEstado(id, { carregando: false, erro: true, linhas: [], total: 0 })
    }));
  }

  private descartar(id: string): void {
    this.assinaturas.get(id)?.unsubscribe();
    this.assinaturas.delete(id);
    const { [id]: _c, ...cargas } = this.cargas;
    this.cargas = cargas;
    this.estados.update(({ [id]: _e, ...resto }) => resto);
  }

  private definirEstado(id: string, estado: EstadoOrigem): void {
    this.estados.update((e) => ({ ...e, [id]: estado }));
  }

  protected definirFiltro(id: string, filtro: FiltroOrigem | null): void {
    this.filtros.update((f) => ({ ...f, [id]: filtro }));
  }

  protected definirSelecionado(id: string, registro: string | null): void {
    this.selecionados.update((s) => ({ ...s, [id]: registro }));
  }

  private conferir(o: OrigemAnalise): Conferencia | null {
    const c = o.conferencia;
    const e = this.estadoDe(o);
    if (!c || e.carregando || e.erro) return null;
    const linhas = this.bases()[this.idDe(o)] ?? [];
    const completo = !!o.filtroInicial || e.total <= e.linhas.length;
    if (!completo) {
      return { tom: 'info', icone: 'pi pi-info-circle', texto: `Mostrando os ${linhas.length} registros mais recentes de ${e.total.toLocaleString('pt-BR')}.` };
    }
    const soma = linhas.reduce<number>((t, l) => t + (c.somar(l) || 0), 0);
    const quantidadeConfere = linhas.length === c.quantidadeEsperada;
    const valorConfere = c.valorEsperado == null || Math.abs(soma - c.valorEsperado) < 0.01;
    const resumo = `${linhas.length.toLocaleString('pt-BR')} ${linhas.length === 1 ? 'registro' : 'registros'}`
      + (c.formato === 'moeda' ? ` · ${MOEDA.format(soma)}` : '');
    if (quantidadeConfere && valorConfere) {
      return { tom: 'success', icone: 'pi pi-check-circle', texto: `${resumo} — fecha com o painel` };
    }
    return {
      tom: 'warning',
      icone: 'pi pi-exclamation-triangle',
      texto: `${resumo} — o painel mostra ${c.quantidadeEsperada.toLocaleString('pt-BR')}`
        + (c.formato === 'moeda' && c.valorEsperado != null ? ` (${MOEDA.format(c.valorEsperado)})` : '')
        + '. Houve lançamentos depois da última atualização; atualize o painel.'
    };
  }

  protected selecionarPonto(grafico: GraficoAnalise, selecao: CesChartPointSelection): void {
    this.executar(grafico.aoSelecionar?.(selecao.index) ?? null);
  }

  protected ativarLinha(tabela: TabelaAnalise, linha: unknown): void {
    this.executar(tabela.aoAtivar?.(linha) ?? null);
  }

  protected selecionarRegistro(o: OrigemAnalise, linha: unknown): void {
    this.executar({ tipo: 'registro', id: o.chaveLinha(linha), origem: this.idDe(o) });
  }

  private executar(acao: AcaoAnalise | null): void {
    if (!acao) return;
    if (acao.tipo === 'navegar') {
      this.navegar.emit(acao.alvo);
      return;
    }
    const id = acao.origem ?? (this.origens()[0] ? this.idDe(this.origens()[0]) : ORIGEM_PADRAO);
    if (acao.tipo === 'filtrar') {
      this.definirSelecionado(id, null);
      this.definirFiltro(id, acao.filtro);
      this.aba.set(id);
      return;
    }
    this.definirSelecionado(id, acao.id);
    this.aba.set(id);
    // O detalhe aparece acima da tabela: leva o foco até ele (teclado e leitor de tela).
    setTimeout(() => {
      const alvo = this.detalheRef()?.nativeElement;
      const reduzir = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
      alvo?.focus({ preventScroll: true });
      alvo?.scrollIntoView({ block: 'nearest', behavior: reduzir ? 'auto' : 'smooth' });
    });
  }
}
