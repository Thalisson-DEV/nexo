import { ChangeDetectionStrategy, Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AlmoxService } from '../../../core/almox/almox.service';
import { Movimentacao } from '../../../core/almox/almox.models';
import { defTipoMov, idCurto, rotuloCondicao } from '../../../core/almox/almox-status';
import { contarSeries } from '../../../core/almox/series';
import { ApiError } from '../../../core/http/error.interceptor';
import { silentContext } from '../../../core/http/http-context';
import { SessaoService } from '../../../core/sessao/sessao.service';
import { BarraTopoComponent, IconeComponent, VazioComponent, dataCurta, hora, numeroBr } from '../../../ui';

interface GrupoMaterial { chave: string; descricao: string; codigo: string; unidade: string; condicao: string; quantidade: number; series: string[] }

/** Uma movimentação (operação) com os itens agrupados por material e as séries de cada um. */
@Component({
  selector: 'app-movimentacao-almox-detalhe',
  standalone: true,
  imports: [BarraTopoComponent, IconeComponent, RouterLink, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-barra-topo [titulo]="mov() ? tipo().rotulo : 'Movimentação'" [subtitulo]="mov() ? quando() : 'Carregando…'" (voltar)="voltar()" />

    @if (carregando()) {
      <div class="ces-esqueletos" aria-busy="true"><div class="ces-esqueleto" style="height: 96px"></div><div class="ces-esqueleto" style="height: 200px"></div></div>
    } @else if (erro()) {
      <ces-vazio icone="alerta" titulo="Não foi possível abrir a movimentação" [texto]="erro()">
        <button type="button" class="ces-btn ces-btn--tonal" (click)="carregar()">Tentar de novo</button>
      </ces-vazio>
    } @else if (mov(); as m) {
      <section class="hero">
        <span class="ces-selo" [class]="'ces-selo ces-selo--' + tipo().tom"><ces-icone [nome]="tipo().icone" /></span>
        <div class="rota">
          @if (m.estoqueOrigem) { <span><small>Saiu de</small>{{ m.estoqueOrigem.nomeIdentificador }}</span> }
          @if (m.estoqueDestino) { <span><small>Entrou em</small><strong>{{ m.estoqueDestino.nomeIdentificador }}</strong></span> }
        </div>
      </section>
      <dl class="ces-dados">
        <div><dt>Quem registrou</dt><dd>{{ m.operadorNome || 'Sistema' }}</dd></div>
        <div><dt>Quando</dt><dd class="ces-num">{{ quando() }}</dd></div>
        <div><dt>Total</dt><dd class="ces-num">{{ grupos().length }} {{ grupos().length === 1 ? 'material' : 'materiais' }}{{ totalSeries() ? ' · ' + contar(totalSeries()) : '' }}</dd></div>
        @if (m.idSolicitacao) {
          <div><dt>Solicitação</dt><dd><a class="link" [routerLink]="['/almox/solicitacoes', m.idSolicitacao]">#{{ idCurto(m.idSolicitacao) }} <ces-icone nome="avancar" [tamanho]="16" /></a></dd></div>
        }
      </dl>

      <h2 class="ces-secao"><span>Materiais</span><span class="ces-num">{{ grupos().length }}</span></h2>
      <ul class="ces-grupo">
        @for (g of grupos(); track g.chave) {
          <li class="ces-linha material">
            <span class="ces-linha__texto">
              <span class="ces-linha__titulo">{{ g.descricao }}</span>
              <span class="ces-linha__apoio ces-num">Cód. {{ g.codigo }} · <span class="ces-cond" [class.ces-cond--sucata]="g.condicao !== 'NOVO'">{{ rotuloCondicao(g.condicao) }}</span></span>
              @if (g.series.length) {
                <button type="button" class="ver-series" [attr.aria-expanded]="abertos()[g.chave] ?? g.series.length <= 6" (click)="alternar(g)">
                  <ces-icone nome="codigo_barras" [tamanho]="16" />{{ contar(g.series.length) }}</button>
                @if (abertos()[g.chave] ?? g.series.length <= 6) {
                  <span class="series">@for (s of g.series; track s) { <span>{{ s }}</span> }</span>
                }
              }
            </span>
            <span class="ces-qtd">{{ num(g.quantidade) }}<small>{{ g.unidade }}</small></span>
          </li>
        }
      </ul>

      @if (assinaturas().entregador || assinaturas().recebedor) {
        <h2 class="ces-secao">Assinaturas</h2>
        <div class="assinaturas">
          @if (assinaturas().entregador; as a) { <figure><img [src]="a" alt="Assinatura de quem entregou"><figcaption>Quem entregou</figcaption></figure> }
          @if (assinaturas().recebedor; as b) { <figure><img [src]="b" alt="Assinatura de quem recebeu"><figcaption>Quem recebeu</figcaption></figure> }
        </div>
      }
      <div class="ces-respiro"></div>
    }
  `,
  styles: [`
    :host { display: block; min-height: 100%; background: var(--ces-fundo); }
    .hero { display: flex; gap: var(--ces-e3); align-items: center; margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e4); border-radius: var(--ces-r-folha); background: var(--ces-superficie); }
    .rota { display: grid; gap: var(--ces-e2); min-width: 0; }
    .rota span { display: grid; font: var(--ces-t-corpo); overflow-wrap: anywhere; }
    .rota small { font: 500 .75rem/1.3 var(--ces-fonte); color: var(--ces-texto-2); }
    .link { display: inline-flex; align-items: center; gap: 2px; color: var(--ces-destaque); font-weight: 600; text-decoration: none; font-family: var(--ces-fonte-mono); }
    .material { align-items: flex-start; }
    .ver-series { display: inline-flex; align-items: center; gap: 6px; justify-self: start; margin-top: 6px; min-height: 32px; padding: 0 10px; border: 0; border-radius: var(--ces-r-pilula);
      background: var(--ces-superficie-2); color: var(--ces-texto); font: 600 .75rem/1 var(--ces-fonte); cursor: pointer; }
    .series { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }
    .series span { padding: 3px 8px; border-radius: 6px; background: var(--ces-superficie-2); font: 600 .75rem/1.3 var(--ces-fonte-mono); }
    .assinaturas { display: grid; grid-template-columns: 1fr 1fr; gap: var(--ces-e2); padding: 0 var(--ces-e3); }
    figure { margin: 0; padding: var(--ces-e2); border-radius: var(--ces-r-grupo); background: var(--ces-superficie); display: grid; gap: 4px; }
    figure img { width: 100%; height: 72px; object-fit: contain; background: #fff; border-radius: 8px; }
    figcaption { font: var(--ces-t-apoio); font-size: .75rem; color: var(--ces-texto-2); text-align: center; }
  `],
})
export class MovimentacaoAlmoxDetalheComponent implements OnDestroy {
  private readonly almox = inject(AlmoxService);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly sessao = inject(SessaoService);
  readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
  readonly mov = signal<Movimentacao | null>(null);
  readonly carregando = signal(true);
  readonly erro = signal<string | null>(null);
  readonly abertos = signal<Partial<Record<string, boolean>>>({});
  readonly assinaturas = signal<{ entregador?: string; recebedor?: string }>({});
  private urls: string[] = [];

  readonly tipo = computed(() => defTipoMov(this.mov()?.tipo));
  readonly quando = computed(() => (this.mov() ? `${dataCurta(this.mov()!.dataHora)} às ${hora(this.mov()!.dataHora)}` : ''));
  readonly grupos = computed<GrupoMaterial[]>(() => {
    const mapa = new Map<string, GrupoMaterial>();
    for (const i of this.mov()?.itens ?? []) {
      const condicao = i.condicao || 'NOVO';
      const chave = `${i.material.id}|${condicao}`;
      const g = mapa.get(chave) ?? { chave, descricao: i.material.descricao, codigo: i.material.codigo, unidade: i.material.unidadeMedida || 'UN', condicao, quantidade: 0, series: [] };
      g.quantidade += Number(i.quantidade) || 0;
      if (i.numSerie) g.series.push(i.numSerie);
      mapa.set(chave, g);
    }
    return [...mapa.values()];
  });
  readonly totalSeries = computed(() => this.grupos().reduce((a, g) => a + g.series.length, 0));

  readonly idCurto = idCurto;
  readonly rotuloCondicao = rotuloCondicao;

  constructor() {
    this.carregar();
  }

  ngOnDestroy(): void {
    this.urls.forEach(u => URL.revokeObjectURL(u));
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.almox.obterMovimentacao(this.id).subscribe({
      next: m => {
        this.mov.set(m);
        this.carregando.set(false);
        if (m.tipo === 'TRANSFERENCIA') this.carregarAssinaturas();
      },
      error: (e: ApiError) => { this.carregando.set(false); this.erro.set(e.status === 404 ? 'Esta movimentação não existe.' : e.detail || 'Tente de novo.'); },
    });
  }

  private carregarAssinaturas(): void {
    for (const parte of ['ENTREGADOR', 'RECEBEDOR'] as const) {
      this.http.get(`${environment.apiBaseUrl}/v1/movimentacoes-estoque/operacao/${encodeURIComponent(this.id)}/assinaturas/${parte}`, { responseType: 'blob', context: silentContext() })
        .pipe(catchError(() => of(null)))
        .subscribe(blob => {
          if (!blob || !blob.size || !blob.type.startsWith('image/')) return;
          const url = URL.createObjectURL(blob);
          this.urls.push(url);
          this.assinaturas.update(a => ({ ...a, [parte === 'ENTREGADOR' ? 'entregador' : 'recebedor']: url }));
        });
    }
  }

  alternar(g: GrupoMaterial): void {
    const atual = this.abertos()[g.chave] ?? g.series.length <= 6;
    this.abertos.update(a => ({ ...a, [g.chave]: !atual }));
  }

  contar(n: number): string {
    return contarSeries(n);
  }

  num(n: number): string {
    return numeroBr(n);
  }

  voltar(): void {
    const anterior = this.sessao.rotaAnterior();
    void this.router.navigateByUrl(anterior?.startsWith('/almox') && !anterior.includes('/nova') ? anterior : '/almox/movimentacoes');
  }
}
