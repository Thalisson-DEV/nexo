import { CommonModule } from '@angular/common';
import { Component, HostListener, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ViewStateStore } from '../../../core/navigation/view-state.store';
import { MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { FavoritosService } from '../../../core/services/favoritos.service';

/** Uma tela alcançável pelo menu, já achatada para busca. */
interface TelaBuscavel {
  label: string;
  rota: string;
  icone: string;
  /** Trilha até ela — "Financeiro › Cadastros" —, que é o que desempata homônimos. */
  caminho: string;
  externa: boolean;
}

/**
 * Busca rápida de telas (Ctrl+K).
 *
 * A fonte é o próprio menu já filtrado por permissão: o que a pessoa não pode ver
 * no menu também não aparece aqui. Assim a busca não vira uma porta paralela para
 * telas restritas nem oferece caminho que termina em 403.
 */
@Component({
  selector: 'app-busca-telas',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, DialogModule],
  template: `
    <!-- Gatilho: barra no desktop, só a lupa quando não há largura para ela -->
    <button type="button" (click)="abrir()" aria-label="Buscar telas"
      class="hidden md:flex items-center gap-2 h-10 pl-3 pr-2 rounded-xl border border-[var(--ces-border)]
        bg-[var(--ces-surface-subtle)] hover:bg-[var(--ces-surface)] hover:border-primary-400 text-[var(--ces-foreground-muted)] hover:text-primary-500
        transition-all">
      <i class="pi pi-search text-xs"></i>
      <span class="text-[11px] font-medium">Buscar telas</span>
      <span class="ml-3 text-[9px] font-bold text-[var(--ces-foreground-subtle)] bg-[var(--ces-surface)] border border-[var(--ces-border)]
        rounded-md px-1.5 py-0.5 tracking-wider">CTRL K</span>
    </button>
    <p-button icon="pi pi-search" [text]="true" (onClick)="abrir()"
      styleClass="md:!hidden !text-[var(--ces-foreground-muted)] hover:!bg-[var(--ces-brand-soft)] hover:!text-[var(--ces-brand)] !w-10 !h-10 !p-0 !rounded-xl transition-all" />

    <p-dialog [visible]="aberto()" (visibleChange)="aberto.set($event)" [modal]="true"
      [showHeader]="false" [dismissableMask]="true" [draggable]="false" [resizable]="false"
      position="top" [style]="{ width: '92vw', maxWidth: '560px', marginTop: '8vh' }"
      styleClass="busca-telas-dialog" appendTo="body" (onShow)="campo.focus()">

      <div class="flex flex-col bg-[var(--ces-surface)] text-[var(--ces-foreground)] border border-[var(--ces-border)] rounded-2xl overflow-hidden shadow-2xl
        animate-in zoom-in-95 duration-200">

        <div class="flex items-center gap-3 px-4 h-14 border-b border-[var(--ces-border)] bg-[var(--ces-surface-subtle)]">
          <i class="pi pi-search text-[var(--ces-foreground-subtle)] text-sm"></i>
          <input #campo type="text" [ngModel]="termo()" (ngModelChange)="digitar($event)"
            placeholder="Buscar telas do sistema..." autocomplete="off"
            class="flex-1 bg-transparent border-0 outline-none text-sm text-[var(--ces-foreground)]
              placeholder:text-[var(--ces-foreground-subtle)]" />
          <span class="text-[9px] font-bold text-[var(--ces-foreground-subtle)] bg-[var(--ces-surface)] border border-[var(--ces-border)]
            rounded-md px-1.5 py-0.5 tracking-wider">ESC</span>
        </div>

        <div class="max-h-[380px] overflow-y-auto custom-scrollbar">
          @if (lista().length) {
            <span class="block px-4 pt-3 pb-1 text-[10px] font-bold text-[var(--ces-foreground-subtle)]
              uppercase tracking-widest">{{ tituloDaLista() }}</span>

            <div class="flex flex-col p-1">
              @for (tela of lista(); track tela.rota; let i = $index) {
                <button type="button" (click)="ir(tela)" (mouseenter)="indiceAtivo.set(i)"
                  class="flex items-center justify-between gap-3 p-3 rounded-xl text-left
                    transition-all group"
                  [class.!bg-[var(--ces-brand-soft)]]="i === indiceAtivo()"
                  [class.hover:bg-[var(--ces-surface-muted)]]="true">
                  <div class="flex items-center gap-3 overflow-hidden">
                    <div class="w-8 h-8 rounded-lg bg-[var(--ces-surface-subtle)] border border-[var(--ces-border)] flex items-center justify-center
                      shrink-0 group-hover:bg-[var(--ces-surface)] group-hover:shadow-sm transition-all">
                      <i [class]="tela.icone" class="text-[var(--ces-foreground-subtle)] group-hover:text-primary-500 text-sm"></i>
                    </div>
                    <div class="flex flex-col overflow-hidden">
                      <span class="text-xs font-medium text-[var(--ces-foreground)] truncate">{{ tela.label }}</span>
                      <span class="text-[10px] text-[var(--ces-foreground-subtle)] truncate">{{ tela.caminho }}</span>
                    </div>
                  </div>
                  @if (i === indiceAtivo()) {
                    <i class="pi pi-arrow-right text-[10px] text-primary-500 shrink-0"></i>
                  }
                </button>
              }
            </div>
          } @else {
            <div class="flex flex-col items-center justify-center py-12 px-6 text-center">
              <div class="w-12 h-12 bg-[var(--ces-surface-subtle)] rounded-full flex items-center justify-center mb-4 border border-[var(--ces-border)]">
                <i class="pi pi-search-minus text-[var(--ces-foreground-subtle)] text-2xl"></i>
              </div>
              <span class="text-sm font-medium text-[var(--ces-foreground-muted)]">Nenhuma tela encontrada</span>
              <p class="text-[11px] text-[var(--ces-foreground-subtle)] mt-1 leading-relaxed">
                Tente outro termo, ou o nome do módulo.
              </p>
            </div>
          }
        </div>

        <div class="flex items-center gap-4 px-4 py-2.5 border-t border-[var(--ces-border)] bg-[var(--ces-surface-subtle)]">
          <span class="flex items-center gap-1.5 text-[10px] text-[var(--ces-foreground-subtle)]">
            <i class="pi pi-arrows-v text-[9px]"></i> navegar
          </span>
          <span class="flex items-center gap-1.5 text-[10px] text-[var(--ces-foreground-subtle)]">
            <i class="pi pi-arrow-down-left text-[9px]"></i> abrir
          </span>
        </div>
      </div>
    </p-dialog>
  `,
  styles: [`
    /* O diálogo vai para o body, fora do alcance de :host ::ng-deep — o seletor é
       global de propósito, ancorado numa classe que só este componente usa. */
    ::ng-deep .busca-telas-dialog.p-dialog { border-radius: 1rem !important; overflow: hidden; }
    ::ng-deep .busca-telas-dialog .p-dialog-content {
      padding: 0 !important; border-radius: 1rem !important; overflow: hidden;
    }
  `]
})
export class BuscaTelasComponent {
  private readonly router = inject(Router);
  private readonly viewState = inject(ViewStateStore);
  private readonly favs = inject(FavoritosService);

  /** O menu já filtrado por permissão, vindo do App. */
  readonly menuItems = input<MenuItem[]>([]);

  protected readonly aberto = signal(false);
  protected readonly termo = signal('');
  protected readonly indiceAtivo = signal(0);

  /** Sem termo, o que se oferece de cara — o que mais se abre no dia a dia. */
  private readonly rotasSugeridas = [
    '/mural',
    '/notificacoes',
    '/chamados/novo',
    '/financeiro/compras',
    '/sst/inspecoes/nova',
    '/almoxarifado/solicitacoes-tecnicas'
  ];

  private readonly telas = computed(() => this.achatar(this.menuItems()));

  protected readonly lista = computed<TelaBuscavel[]>(() => {
    if (this.termo().trim()) return this.resultados();

    const favoritos: TelaBuscavel[] = this.favs.favoritos().slice(0, 4).map((favorito) => ({
      label: favorito.titulo,
      rota: favorito.rota,
      icone: favorito.icone || 'pi pi-star',
      caminho: 'Favorito',
      externa: false
    }));

    const sugestoes = this.sugestoes().filter(
      (sugestao) => !favoritos.some((favorito) => favorito.rota === sugestao.rota)
    );
    return [...favoritos, ...sugestoes].slice(0, 7);
  });

  protected readonly tituloDaLista = computed(() =>
    this.termo().trim() ? 'Resultados' : 'Favoritos e sugestões'
  );

  private readonly sugestoes = computed<TelaBuscavel[]>(() => {
    const telas = this.telas();
    const preferidas = this.rotasSugeridas
      .map((rota) => telas.find((tela) => tela.rota === rota))
      .filter((tela): tela is TelaBuscavel => !!tela);
    return preferidas.length ? preferidas : telas.slice(0, 5);
  });

  private readonly resultados = computed<TelaBuscavel[]>(() => {
    const termo = this.normalizar(this.termo());
    if (!termo) return [];
    return this.telas()
      .map((tela) => ({ tela, peso: this.peso(tela, termo) }))
      .filter((candidato) => candidato.peso > 0)
      .sort((a, b) => b.peso - a.peso || a.tela.label.localeCompare(b.tela.label))
      .slice(0, 8)
      .map((candidato) => candidato.tela);
  });

  @HostListener('window:keydown', ['$event'])
  protected atalho(evento: KeyboardEvent): void {
    if ((evento.ctrlKey || evento.metaKey) && evento.key.toLowerCase() === 'k') {
      evento.preventDefault();
      this.aberto() ? this.aberto.set(false) : this.abrir();
      return;
    }
    if (!this.aberto()) return;

    if (evento.key === 'ArrowDown') {
      evento.preventDefault();
      this.mover(1);
    } else if (evento.key === 'ArrowUp') {
      evento.preventDefault();
      this.mover(-1);
    } else if (evento.key === 'Enter') {
      evento.preventDefault();
      const tela = this.lista()[this.indiceAtivo()];
      if (tela) this.ir(tela);
    }
  }

  protected abrir(): void {
    this.termo.set('');
    this.indiceAtivo.set(0);
    this.aberto.set(true);
  }

  protected digitar(valor: string): void {
    this.termo.set(valor);
    this.indiceAtivo.set(0);
  }

  protected ir(tela: TelaBuscavel): void {
    this.aberto.set(false);
    if (tela.externa) {
      window.open(tela.rota, '_blank');
      return;
    }
    // Navegação intencional: abre a tela limpa.
    this.viewState.clearRoute(tela.rota);
    this.router.navigateByUrl(tela.rota);
  }

  private mover(passo: number): void {
    const total = this.lista().length;
    if (!total) return;
    this.indiceAtivo.update((atual) => (atual + passo + total) % total);
  }

  /** Percorre o menu e devolve só as folhas navegáveis, com a trilha até elas. */
  private achatar(itens: MenuItem[], trilha: string[] = []): TelaBuscavel[] {
    return itens.flatMap((item) => {
      if (item.visible === false) return [];
      if (item.items?.length) {
        return this.achatar(item.items, [...trilha, item.label ?? '']);
      }
      const destino = typeof item.routerLink === 'string' ? item.routerLink : item.url;
      if (!destino) return [];
      return [{
        label: item.label ?? '',
        rota: destino,
        icone: item.icon ?? 'pi pi-file',
        caminho: trilha.join(' › '),
        externa: item.target === '_blank'
      }];
    });
  }

  /** Sem acento e sem caixa: "solicitacao" acha "Solicitações". */
  private normalizar(valor: string): string {
    return valor.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
  }

  private peso(tela: TelaBuscavel, termo: string): number {
    const label = this.normalizar(tela.label);
    if (label.startsWith(termo)) return 3;
    if (label.includes(termo)) return 2;
    if (this.normalizar(tela.caminho).includes(termo)) return 1;
    return 0;
  }
}
