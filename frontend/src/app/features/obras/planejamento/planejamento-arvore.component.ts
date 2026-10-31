import { ChangeDetectionStrategy, Component, ElementRef, computed, input, output, signal, viewChild } from '@angular/core';
import { CesIconButtonComponent } from '@ces/ui';
import { PlanejamentoObra } from '../../../core/models/obras-planejamento.models';
import { LinhaArvore, ROTULO_ESTADO, ROTULO_STATUS_ITEM, janelaVisivel, montarLinhas } from './planejamento.logic';

const ALTURA_LINHA = 36;

/** Árvore do planejamento: Página → Ponto → Kit → itens e vãos. Desenha só as linhas visíveis. */
@Component({
  selector: 'app-planejamento-arvore',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CesIconButtonComponent],
  template: `
    <div class="arvore-busca">
      <span class="arvore-busca__campo">
        <i class="pi pi-search" aria-hidden="true"></i>
        <input type="search" placeholder="Buscar ponto, material ou serviço" aria-label="Buscar no planejamento"
          [value]="busca()" (input)="busca.set($any($event.target).value)" />
      </span>
      <ces-icon-button [icon]="soServicos() ? 'pi pi-wrench' : 'pi pi-box'" variant="ghost" size="icon-sm"
        [label]="soServicos() ? 'Mostrando só serviços. Mostrar também materiais' : 'Mostrando serviços e materiais. Mostrar só serviços'"
        [tooltip]="soServicos() ? 'Só serviços' : 'Serviços e materiais'" (pressed)="soServicos.set(!soServicos())"></ces-icon-button>
    </div>

    @if (plano().indicadores.pontosSemKit || plano().indicadores.vaosSemKit || soSemKit()) {
      <button type="button" class="arvore-resumo" [attr.aria-pressed]="soSemKit()" (click)="soSemKit.set(!soSemKit())">
        <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
        <span>Sem kit: {{ plano().indicadores.pontosSemKit }} {{ plano().indicadores.pontosSemKit === 1 ? 'ponto' : 'pontos' }}
        e {{ plano().indicadores.vaosSemKit }} {{ plano().indicadores.vaosSemKit === 1 ? 'vão' : 'vãos' }}</span>
        <small>{{ soSemKit() ? 'Mostrar tudo' : 'Ver só estes' }}</small>
      </button>
    }

    <div #rolagem class="arvore-lista" role="tree" aria-label="Pontos, kits e vãos" (scroll)="topo.set($any($event.target).scrollTop)">
      @if (!linhas().length) {
        <p class="arvore-vazio">{{ busca() ? 'Nada encontrado para esta busca.' : 'Todos os pontos e vãos têm kit.' }}</p>
      }
      <div [style.height.px]="linhas().length * alturaLinha" class="arvore-trilho">
        @for (linha of visiveis(); track linha.chave) {
          <div class="linha" role="treeitem"
            [attr.data-tipo]="linha.tipo"
            [attr.data-nivel]="linha.nivel"
            [class.linha--ativa]="linha.idLocal === ativo() && !linha.idItem && linha.tipo !== 'kit'"
            [style.top.px]="linha.indice * alturaLinha"
            [style.height.px]="alturaLinha">
            @if (linha.semKit) {
              <span class="linha__seta" aria-hidden="true"></span>
            } @else if (linha.tipo === 'ponto' || linha.tipo === 'vao' || linha.tipo === 'kit') {
              <button type="button" class="linha__seta" [attr.aria-label]="linha.recolhido ? 'Abrir' : 'Recolher'" [attr.aria-expanded]="!linha.recolhido"
                (click)="alternar.emit(linha.chave)">
                <i class="pi" [class.pi-chevron-right]="linha.recolhido" [class.pi-chevron-down]="!linha.recolhido" aria-hidden="true"></i>
              </button>
            }
            @if (linha.selecionavel) {
              <input type="checkbox" class="linha__marca" [attr.aria-label]="'Selecionar ' + linha.titulo"
                [checked]="linha.idItem ? itensSelecionados().has(linha.idItem) : locaisSelecionados().has(linha.idLocal!)"
                (change)="linha.idItem ? marcarItem.emit(linha.idItem) : marcarLocal.emit(linha.idLocal!)" />
            }
            @switch (linha.tipo) {
              @case ('pagina') {
                <button type="button" class="linha__texto" (click)="irPagina.emit(linha.pagina!)">
                  <i class="pi pi-file" aria-hidden="true"></i><strong>{{ linha.titulo }}</strong>
                </button>
              }
              @case ('secao-vaos') {
                <span class="linha__texto"><i class="pi pi-share-alt" aria-hidden="true"></i><strong>{{ linha.titulo }}</strong></span>
              }
              @case ('kit') {
                <button type="button" class="linha__texto" [title]="linha.detalhe" (click)="abrirLocal.emit(linha.idLocal!)">
                  <code>{{ linha.titulo }}</code><span class="linha__detalhe">{{ linha.detalhe }}</span>
                </button>
              }
              @case ('item') {
                <span class="linha__texto" [title]="linha.titulo + ' · ' + rotuloStatus[linha.statusItem!]">
                  <span class="linha__tipo" [attr.data-material]="linha.ehMaterial">{{ linha.ehMaterial ? 'M' : 'S' }}</span>
                  <span class="linha__detalhe">{{ linha.titulo }}</span>
                </span>
              }
              @default {
                <button type="button" class="linha__texto" (click)="abrirLocal.emit(linha.idLocal!)">
                  <span class="linha__estado" [attr.data-estado]="linha.estado" [title]="rotuloEstado[linha.estado!]"></span>
                  <strong>{{ linha.titulo }}</strong><span class="linha__detalhe">{{ linha.detalhe }}</span>
                </button>
              }
            }
            @if (linha.etiqueta) {
              <span class="linha__etiqueta" [class.linha__etiqueta--programado]="linha.statusItem && linha.statusItem !== 'PENDENTE'">{{ linha.etiqueta }}</span>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 0; flex: 1; }
    .arvore-busca { display: flex; gap: 0.25rem; align-items: center; padding: 0.5rem; border-bottom: 1px solid var(--ces-border); }
    .arvore-busca__campo { display: flex; flex: 1; align-items: center; gap: 0.375rem; min-width: 0; padding: 0.3125rem 0.5rem; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); color: var(--ces-foreground-muted); background: var(--ces-surface); }
    .arvore-busca__campo:focus-within { border-color: var(--ces-brand); }
    .arvore-busca input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--ces-foreground); font-size: 0.8125rem; }
    .arvore-resumo { display: flex; flex: none; gap: 0.375rem; align-items: center; width: 100%; padding: 0.375rem 0.75rem; border: 0; border-bottom: 1px solid var(--ces-border); background: var(--ces-warning-soft); color: var(--ces-warning-text); font-size: 0.75rem; text-align: left; cursor: pointer; }
    .arvore-resumo span { flex: 1; }
    .arvore-resumo small { font-size: 0.6875rem; font-weight: 600; text-decoration: underline; }
    .arvore-resumo:focus-visible { outline: 2px solid var(--ces-brand); outline-offset: -2px; }
    .arvore-lista { flex: 1; min-height: 0; overflow-y: auto; contain: strict; }
    .arvore-trilho { position: relative; }
    .arvore-vazio { margin: 0; padding: 1.5rem 1rem; color: var(--ces-foreground-muted); font-size: 0.8125rem; text-align: center; }
    .linha { position: absolute; right: 0; left: 0; display: flex; gap: 0.25rem; align-items: center; padding: 0 0.5rem; color: var(--ces-foreground); font-size: 0.8125rem; }
    .linha[data-nivel='1'] { padding-left: 1.25rem; }
    .linha[data-nivel='2'] { padding-left: 2.375rem; }
    .linha:hover { background: var(--ces-surface-muted); }
    .linha--ativa { box-shadow: inset 2px 0 0 var(--ces-brand); background: var(--ces-brand-soft, var(--ces-surface-muted)); }
    .linha[data-tipo='pagina'], .linha[data-tipo='secao-vaos'] { background: var(--ces-surface-muted); color: var(--ces-foreground-muted); text-transform: uppercase; letter-spacing: 0.02em; font-size: 0.6875rem; }
    .linha__seta { display: inline-grid; flex: none; width: 1.25rem; height: 1.25rem; place-items: center; padding: 0; border: 0; border-radius: var(--ces-radius-sm); background: transparent; color: var(--ces-foreground-muted); cursor: pointer; }
    .linha__seta i { font-size: 0.625rem; }
    .linha__marca { flex: none; margin: 0 0.125rem; accent-color: var(--ces-brand); }
    .linha__texto { display: flex; flex: 1; gap: 0.375rem; align-items: center; min-width: 0; padding: 0; border: 0; background: transparent; color: inherit; font: inherit; text-align: left; }
    button.linha__texto { cursor: pointer; }
    .linha__seta:focus-visible, button.linha__texto:focus-visible { outline: 2px solid var(--ces-brand); outline-offset: 1px; }
    .linha__detalhe { overflow: hidden; color: var(--ces-foreground-muted); text-overflow: ellipsis; white-space: nowrap; }
    .linha[data-tipo='item'] .linha__detalhe { color: var(--ces-foreground); }
    code { flex: none; color: var(--ces-brand); font-size: 0.6875rem; font-weight: 600; }
    .linha__tipo { flex: none; width: 1rem; border-radius: var(--ces-radius-sm); background: var(--ces-success-soft); color: var(--ces-success-text); font-size: 0.625rem; font-weight: 700; line-height: 1rem; text-align: center; }
    .linha__tipo[data-material='true'] { background: var(--ces-info-soft); color: var(--ces-info-text); }
    .linha__etiqueta { flex: none; padding: 0 0.375rem; border-radius: 999px; background: var(--ces-surface-muted); color: var(--ces-foreground-muted); font-size: 0.6875rem; line-height: 1.125rem; }
    .linha__etiqueta--programado { background: var(--ces-info-soft); color: var(--ces-info-text); }
    .linha__estado { flex: none; width: 0.625rem; height: 0.625rem; border: 2px solid var(--ces-foreground-muted); border-radius: 50%; }
    .linha__estado[data-estado='sem-kit'] { border-style: dashed; border-color: var(--ces-danger); }
    .linha__estado[data-estado='pendente'] { border-color: var(--ces-brand); }
    .linha__estado[data-estado='programado'] { border-color: var(--ces-warning); border-radius: 2px; transform: rotate(45deg); }
    .linha__estado[data-estado='andamento'] { border-color: var(--ces-info); border-radius: 2px; background: var(--ces-info); }
    .linha__estado[data-estado='concluido'] { border-color: var(--ces-success); background: var(--ces-success); }
  `]
})
export class PlanejamentoArvoreComponent {
  readonly plano = input.required<PlanejamentoObra>();
  readonly recolhidos = input.required<ReadonlySet<string>>();
  readonly locaisSelecionados = input.required<ReadonlySet<string>>();
  readonly itensSelecionados = input.required<ReadonlySet<string>>();
  readonly ativo = input<string | null>(null);

  readonly alternar = output<string>();
  readonly marcarLocal = output<string>();
  readonly marcarItem = output<string>();
  readonly abrirLocal = output<string>();
  readonly irPagina = output<number>();

  protected readonly rolagem = viewChild<ElementRef<HTMLElement>>('rolagem');
  protected readonly alturaLinha = ALTURA_LINHA;
  protected readonly rotuloEstado = ROTULO_ESTADO;
  protected readonly rotuloStatus = ROTULO_STATUS_ITEM;

  protected readonly busca = signal('');
  protected readonly soServicos = signal(true);
  protected readonly soSemKit = signal(false);
  protected readonly topo = signal(0);

  protected readonly linhas = computed<LinhaArvore[]>(() =>
    montarLinhas(this.plano(), { busca: this.busca(), soServicos: this.soServicos(), soSemKit: this.soSemKit(), recolhidos: this.recolhidos() })
  );

  protected readonly visiveis = computed(() => {
    const todas = this.linhas();
    const altura = this.rolagem()?.nativeElement.clientHeight || 800;
    const { inicio, fim } = janelaVisivel(todas.length, ALTURA_LINHA, this.topo(), altura);
    return todas.slice(inicio, fim).map((linha, i) => ({ ...linha, indice: inicio + i }));
  });
}
