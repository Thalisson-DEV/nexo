import { ChangeDetectionStrategy, Component, ElementRef, HostListener, afterNextRender, computed, inject, input, output, signal } from '@angular/core';
import { CesButtonComponent, CesTextareaComponent } from '@ces/ui';

import { Catalogo, Definicao } from '../../../../core/models/automacao.models';
import { GrupoDinamico, TIPO_DADO, gruposDinamicos } from '../lib/dinamico';

export interface ContextoDinamico {
  definicao: Definicao;
  catalogo: Catalogo;
  noId: string;
  /** Lista cujos itens viram {{ item }} (regras de "Filtrar lista", colunas de tabela). */
  fonteItem?: string;
}

/** "Inserir dado": escolhe um dado de um passo anterior, ou escreve uma expressão com funções. */
@Component({
  selector: 'aut-seletor-dinamico',
  standalone: true,
  imports: [CesTextareaComponent, CesButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="aut-dyn" role="dialog" aria-label="Escolher dado">
      <div class="aut-dyn-top">
        <span>{{ aba() === 'dados' ? 'Dados dos passos anteriores' : 'Expressão' }}</span>
        <button type="button" class="aut-link" (click)="aba.set(aba() === 'dados' ? 'expressao' : 'dados')">
          {{ aba() === 'dados' ? 'Expressão' : 'Dados' }}
        </button>
      </div>
      @if (aba() === 'dados') {
        <input #busca class="p-inputtext ces-prime-control" placeholder="Buscar dado" [value]="termo()" (input)="termo.set(busca.value)" aria-label="Buscar dado" />
        <div class="aut-dyn-list">
          @if (!filtrados().length) {
            <p class="aut-muted">{{ contexto().noId === 'trigger' ? 'O gatilho não lê dados de outros passos.' : 'Nenhum dado disponível antes deste passo.' }}</p>
          }
          @for (g of filtrados(); track g.chave) {
            <div [attr.data-group]="g.grupo">
              <button type="button" class="aut-dyn-head" [attr.aria-expanded]="!fechados().has(g.chave)" (click)="alternar(g.chave)">
                <span class="aut-dyn-icon"><i [class]="g.icone" aria-hidden="true"></i></span>
                <span class="aut-dyn-title">{{ g.titulo }}</span>
                <i [class]="fechados().has(g.chave) ? 'pi pi-chevron-right' : 'pi pi-chevron-down'" aria-hidden="true"></i>
              </button>
              @if (!fechados().has(g.chave)) {
                @for (item of g.itens; track item.expressao) {
                  <button type="button" class="aut-dyn-item" [attr.data-level]="item.nivel" [title]="item.descricao || item.expressao"
                          (click)="escolher.emit(item.expressao)">
                    <span class="aut-dyn-label">{{ item.rotulo }}</span>
                    <span class="aut-type">{{ tipo(item.tipo) }}</span>
                  </button>
                }
              }
            </div>
          }
        </div>
      } @else {
        <p class="aut-muted">Combine dados e funções. Ex.: <code>format_money(trigger.outputs.valorValidado)</code></p>
        <ces-textarea label="Expressão" [labelVisible]="false" [rows]="3" [value]="expressao()" (valueChange)="expressao.set($event)"
                      placeholder="upper(trigger.outputs.colaborador.base)"></ces-textarea>
        <div style="display:flex;justify-content:flex-end">
          <ces-button label="Inserir" variant="primary" size="sm" [disabled]="!expressao().trim()" (pressed)="escolher.emit(expressao().trim())"></ces-button>
        </div>
        <span class="aut-section-title">Funções</span>
        <div class="aut-dyn-functions">
          @for (f of funcoes(); track f[0]) {
            <button type="button" (click)="expressao.set(expressao() + f[0] + '(')">
              <code>{{ f[0] }}</code><span>{{ f[1] }}</span>
            </button>
          }
        </div>
      }
    </div>
  `
})
export class SeletorDinamicoComponent {
  readonly contexto = input.required<ContextoDinamico>();
  readonly escolher = output<string>();
  readonly fechar = output<void>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly focoInicial = afterNextRender(() => this.host.nativeElement.querySelector<HTMLInputElement>('input')?.focus());
  protected readonly aba = signal<'dados' | 'expressao'>('dados');
  protected readonly termo = signal('');
  protected readonly expressao = signal('');
  protected readonly fechados = signal(new Set<string>());

  protected readonly grupos = computed<GrupoDinamico[]>(() => {
    const c = this.contexto();
    return gruposDinamicos(c.definicao, c.noId, c.catalogo, c.fonteItem);
  });

  protected readonly filtrados = computed(() => {
    const t = this.termo().trim().toLowerCase();
    if (!t) return this.grupos();
    return this.grupos()
      .map((g) => ({ ...g, itens: g.itens.filter((i) => `${i.rotulo} ${g.titulo}`.toLowerCase().includes(t)) }))
      .filter((g) => g.itens.length);
  });

  protected readonly funcoes = computed(() =>
    Object.entries(this.contexto().catalogo.funcoes).map(([nome, ajuda]) => [nome, ajuda.split('—')[1]?.trim() ?? ajuda] as const)
  );

  protected tipo(t: string): string {
    return TIPO_DADO[t] ?? 'Dado';
  }

  protected alternar(chave: string): void {
    const s = new Set(this.fechados());
    if (s.has(chave)) s.delete(chave);
    else s.add(chave);
    this.fechados.set(s);
  }

  @HostListener('document:mousedown', ['$event'])
  protected foraDoSeletor(e: MouseEvent): void {
    const alvo = e.target as Node | null;
    if (alvo && !this.host.nativeElement.contains(alvo) && !(alvo as HTMLElement).closest?.('[data-dyn-trigger]')) this.fechar.emit();
  }

  @HostListener('document:keydown.escape')
  protected esc(): void {
    this.fechar.emit();
  }
}
