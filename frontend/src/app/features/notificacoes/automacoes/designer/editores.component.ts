import { ChangeDetectionStrategy, Component, computed, forwardRef, input, output, signal } from '@angular/core';
import { CesButtonComponent, CesIconButtonComponent } from '@ces/ui';

import { CampoEspec, JsonValue } from '../../../../core/models/automacao.models';
import { objetos, texto } from '../lib/arvore';
import { descreverExpressao, segmentos } from '../lib/dinamico';
import { CampoExpressaoComponent } from './campo-expressao.component';
import { DestinatariosComponent } from './destinatarios.component';
import { ContextoDinamico, SeletorDinamicoComponent } from './seletor-dinamico.component';

type Linha = Record<string, JsonValue>;

/** Tabela "quando for X → estes destinatários" do passo "Escolher destinatários por regra". */
@Component({
  selector: 'aut-rotas',
  standalone: true,
  imports: [CesButtonComponent, CesIconButtonComponent, DestinatariosComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="aut-rows">
      @for (r of linhas(); track $index; let i = $index) {
        <div class="aut-row">
          <div class="aut-row-fields">
            <label class="aut-row-cell aut-row-wide aut-route-when">
              <span style="white-space:nowrap">Quando for</span>
              <input class="p-inputtext ces-prime-control" [value]="texto(r['when'])" placeholder="Juazeiro; Juazeiro II"
                     (input)="alterar(i, 'when', $any($event.target).value)" [attr.aria-label]="'Valor da regra ' + (i + 1)" />
            </label>
            <div class="aut-row-cell aut-row-wide">
              <aut-destinatarios [valor]="r['recipients']" [contexto]="contexto()" vazio="Quem recebe nesta regra"
                                 (mudou)="alterar(i, 'recipients', $event)" />
            </div>
          </div>
          <ces-icon-button icon="pi pi-trash" label="Remover regra" variant="ghost" size="icon-sm" (pressed)="remover(i)"></ces-icon-button>
        </div>
      }
      <div><ces-button icon="pi pi-plus" label="Adicionar regra" variant="secondary" size="sm" (pressed)="adicionar()"></ces-button></div>
    </div>
  `
})
export class RotasComponent {
  readonly valor = input<JsonValue | undefined>(undefined);
  readonly contexto = input.required<ContextoDinamico>();
  readonly mudou = output<JsonValue>();
  protected readonly linhas = computed(() => objetos(this.valor()));
  protected readonly texto = texto;

  protected alterar(i: number, chave: string, v: JsonValue): void {
    this.mudou.emit(this.linhas().map((r, j) => (j === i ? { ...r, [chave]: v } : r)));
  }

  protected adicionar(): void {
    this.mudou.emit([...this.linhas(), { when: '', recipients: [] }]);
  }

  protected remover(i: number): void {
    this.mudou.emit(this.linhas().filter((_, j) => j !== i));
  }
}

/** Editor de parâmetros em lista: campos da IA, mapeamento, colunas, cabeçalhos. */
@Component({
  selector: 'aut-linhas',
  standalone: true,
  imports: [CesButtonComponent, CesIconButtonComponent, CampoExpressaoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="aut-rows">
      @for (r of linhas(); track $index; let i = $index) {
        <div class="aut-row">
          <div class="aut-row-fields">
            @for (c of campos(); track c.chave) {
              @switch (c.tipo) {
                @case ('select') {
                  <select class="p-inputtext ces-prime-control aut-select aut-row-cell" [attr.aria-label]="c.rotulo" [value]="texto(r[c.chave]) || c.opcoes[0]?.[0]"
                          (change)="alterar(i, c.chave, $any($event.target).value)">
                    @for (o of c.opcoes; track o[0]) { <option [value]="o[0]" [selected]="(texto(r[c.chave]) || c.opcoes[0]?.[0]) === o[0]">{{ o[1] }}</option> }
                  </select>
                }
                @case ('boolean') {
                  <label class="aut-check aut-row-cell"><input type="checkbox" [checked]="r[c.chave] === true"
                         (change)="alterar(i, c.chave, $any($event.target).checked)" /> {{ c.rotulo }}</label>
                }
                @case ('expression') {
                  <div class="aut-row-cell aut-row-wide">
                    <aut-campo-expressao [valor]="texto(r[c.chave])" [contexto]="ctxItem()" [rotulo]="c.rotulo"
                                         [placeholder]="c.placeholder ?? c.rotulo" (mudou)="alterar(i, c.chave, $event)" />
                  </div>
                }
                @default {
                  <input class="p-inputtext ces-prime-control aut-row-cell" [attr.aria-label]="c.rotulo" [placeholder]="c.placeholder ?? c.rotulo"
                         [value]="texto(r[c.chave])" (input)="alterar(i, c.chave, $any($event.target).value)" />
                }
              }
            }
          </div>
          <ces-icon-button icon="pi pi-trash" label="Remover linha" variant="ghost" size="icon-sm" (pressed)="remover(i)"></ces-icon-button>
        </div>
      }
      <div><ces-button icon="pi pi-plus" label="Adicionar" variant="secondary" size="sm" (pressed)="adicionar()"></ces-button></div>
    </div>
  `
})
export class LinhasComponent {
  readonly valor = input<JsonValue | undefined>(undefined);
  readonly campos = input<CampoEspec[]>([]);
  readonly contexto = input.required<ContextoDinamico>();
  readonly fonteItem = input<string | undefined>(undefined);
  readonly mudou = output<JsonValue>();
  protected readonly linhas = computed(() => objetos(this.valor()));
  protected readonly ctxItem = computed(() => ({ ...this.contexto(), fonteItem: this.fonteItem() ?? this.contexto().fonteItem }));
  protected readonly texto = texto;

  protected alterar(i: number, chave: string, v: JsonValue): void {
    this.mudou.emit(this.linhas().map((r, j) => (j === i ? { ...r, [chave]: v } : r)));
  }

  protected adicionar(): void {
    const nova: Linha = {};
    for (const c of this.campos()) nova[c.chave] = c.tipo === 'boolean' ? false : c.tipo === 'select' ? c.opcoes[0]?.[0] ?? '' : '';
    this.mudou.emit([...this.linhas(), nova]);
  }

  protected remover(i: number): void {
    this.mudou.emit(this.linhas().filter((_, j) => j !== i));
  }
}

/** Fichas de texto (categorias, colunas, e-mails); cada ficha pode ser um dado dinâmico. */
@Component({
  selector: 'aut-fichas',
  standalone: true,
  imports: [CesIconButtonComponent, SeletorDinamicoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="aut-chips">
      @for (v of valores(); track $index) {
        <span class="aut-chip" [class.aut-chip-dyn]="!!expressao(v)">
          {{ rotuloDe(v) }}
          <button type="button" [attr.aria-label]="'Remover ' + v" (click)="remover($index)"><i class="pi pi-times" aria-hidden="true"></i></button>
        </span>
      }
      <input #entrada class="aut-chips-input" [placeholder]="valores().length ? '' : (placeholder() ?? '')" [attr.aria-label]="rotulo()"
             (keydown)="tecla($event, entrada)" (blur)="confirmar(entrada)" />
      @if (contexto().noId !== 'trigger') {
        <ces-icon-button icon="pi pi-bolt" label="Inserir dado" variant="ghost" size="icon-sm" data-dyn-trigger (pressed)="aberto.set(!aberto())"></ces-icon-button>
      }
      @if (aberto()) {
        <aut-seletor-dinamico [contexto]="contexto()" (escolher)="inserir($event)" (fechar)="aberto.set(false)" />
      }
    </div>
  `
})
export class FichasComponent {
  readonly valor = input<JsonValue | undefined>(undefined);
  readonly contexto = input.required<ContextoDinamico>();
  readonly rotulo = input('');
  readonly placeholder = input<string | null>(null);
  readonly mudou = output<JsonValue>();
  protected readonly aberto = signal(false);
  protected readonly valores = computed(() => (Array.isArray(this.valor()) ? (this.valor() as JsonValue[]).map(texto) : texto(this.valor()) ? [texto(this.valor())] : []));

  protected expressao(v: string): string | undefined {
    return segmentos(v).find((s) => s.expressao !== undefined)?.expressao;
  }

  protected rotuloDe(v: string): string {
    const e = this.expressao(v);
    const c = this.contexto();
    return e ? descreverExpressao(e, c.definicao, c.catalogo) : v;
  }

  protected tecla(e: KeyboardEvent, el: HTMLInputElement): void {
    if ((e.key === 'Enter' || e.key === ',' || e.key === ';') && el.value.trim()) {
      e.preventDefault();
      this.confirmar(el);
    } else if (e.key === 'Backspace' && !el.value && this.valores().length) {
      this.mudou.emit(this.valores().slice(0, -1));
    }
  }

  protected confirmar(el: HTMLInputElement): void {
    const partes = el.value.split(/[,;\n]+/).map((p) => p.trim()).filter(Boolean);
    if (!partes.length) return;
    this.mudou.emit([...this.valores(), ...partes.filter((p) => !this.valores().includes(p))]);
    el.value = '';
  }

  protected inserir(expr: string): void {
    this.aberto.set(false);
    this.mudou.emit([...this.valores(), `{{ ${expr} }}`]);
  }

  protected remover(i: number): void {
    this.mudou.emit(this.valores().filter((_, j) => j !== i));
  }
}

interface Regra {
  left: string;
  operator: string;
  right: string;
}

interface Grupo {
  op: 'and' | 'or';
  rules: (Regra | Grupo)[];
}

const UNARIOS = new Set(['empty', 'not_empty', 'is_true', 'is_false']);

function eGrupo(r: Regra | Grupo): r is Grupo {
  return 'rules' in r;
}

export function paraGrupo(bruto: JsonValue | undefined): Grupo {
  if (bruto && typeof bruto === 'object' && !Array.isArray(bruto)) {
    const regras = Array.isArray(bruto['rules']) ? bruto['rules'] : [];
    return {
      op: bruto['op'] === 'or' ? 'or' : 'and',
      rules: regras.map((r) => {
        const o = (r && typeof r === 'object' && !Array.isArray(r) ? r : {}) as Record<string, JsonValue>;
        return 'rules' in o ? paraGrupo(o) : { left: texto(o['left']), operator: texto(o['operator']) || 'eq', right: texto(o['right']) };
      })
    };
  }
  return { op: 'and', rules: [] };
}

function deGrupo(g: Grupo): JsonValue {
  return { op: g.op, rules: g.rules.map((r) => (eGrupo(r) ? deGrupo(r) : { left: r.left, operator: r.operator, right: r.right })) };
}

/** Regras com "e"/"ou", em grupos aninhados — como o editor de condições do Power Automate. */
@Component({
  selector: 'aut-condicao',
  standalone: true,
  imports: [CesButtonComponent, CesIconButtonComponent, CampoExpressaoComponent, forwardRef(() => CondicaoComponent)],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="aut-cond-group" [attr.data-depth]="nivel()">
      @if (grupo().rules.length > 1) {
        <div class="aut-cond-op" role="group" aria-label="Como juntar as regras">
          <button type="button" [attr.aria-pressed]="grupo().op === 'and'" (click)="emitir({ op: 'and', rules: grupo().rules })">Todas (e)</button>
          <button type="button" [attr.aria-pressed]="grupo().op === 'or'" (click)="emitir({ op: 'or', rules: grupo().rules })">Qualquer uma (ou)</button>
        </div>
      }
      @for (r of grupo().rules; track $index; let i = $index) {
        @if (eGrupo(r)) {
          <aut-condicao [valor]="paraJson(r)" [contexto]="contexto()" [operadores]="operadores()" [nivel]="nivel() + 1"
                        (mudou)="trocar(i, $event)" (remover)="remover(i)" />
        } @else {
          <div class="aut-cond-rule">
            <span class="aut-cond-joiner">{{ i === 0 ? 'Se' : grupo().op === 'and' ? 'e' : 'ou' }}</span>
            <div class="aut-cond-fields">
              <aut-campo-expressao [valor]="r.left" [contexto]="contexto()" rotulo="Valor" placeholder="Escolha o dado"
                                   (mudou)="trocarRegra(i, { left: $event })" />
              <select class="p-inputtext ces-prime-control aut-select" aria-label="Comparação" [value]="r.operator" (change)="trocarRegra(i, { operator: $any($event.target).value })">
                @for (o of opcoes(); track o[0]) { <option [value]="o[0]" [selected]="o[0] === r.operator">{{ o[1] }}</option> }
              </select>
              @if (!unario(r.operator)) {
                <aut-campo-expressao [valor]="r.right" [contexto]="contexto()" rotulo="Comparar com" placeholder="Juazeiro"
                                     (mudou)="trocarRegra(i, { right: $event })" />
              }
            </div>
            <ces-icon-button icon="pi pi-trash" label="Remover regra" variant="ghost" size="icon-sm" (pressed)="remover(i)"></ces-icon-button>
          </div>
        }
      }
      <div style="display:flex;flex-wrap:wrap;gap:0.25rem">
        <ces-button icon="pi pi-plus" label="Regra" variant="secondary" size="sm" (pressed)="adicionarRegra()"></ces-button>
        @if (nivel() < 3) {
          <ces-button icon="pi pi-plus" label="Grupo" variant="ghost" size="sm" (pressed)="adicionarGrupo()"></ces-button>
        }
        @if (nivel() > 0) {
          <ces-button label="Remover grupo" variant="destructive" size="sm" (pressed)="removerGrupo.emit()"></ces-button>
        }
      </div>
    </div>
  `
})
export class CondicaoComponent {
  readonly valor = input<JsonValue | undefined>(undefined);
  readonly contexto = input.required<ContextoDinamico>();
  readonly operadores = input<Record<string, string>>({});
  readonly nivel = input(0);
  readonly mudou = output<JsonValue>();
  readonly removerGrupo = output<void>({ alias: 'remover' });

  protected readonly grupo = computed(() => paraGrupo(this.valor()));
  protected readonly opcoes = computed(() => Object.entries(this.operadores()));
  protected readonly eGrupo = eGrupo;

  protected paraJson(g: Grupo): JsonValue {
    return deGrupo(g);
  }

  protected unario(op: string): boolean {
    return UNARIOS.has(op);
  }

  protected emitir(g: Grupo): void {
    this.mudou.emit(deGrupo(g));
  }

  protected trocar(i: number, v: JsonValue): void {
    const g = this.grupo();
    this.emitir({ ...g, rules: g.rules.map((r, j) => (j === i ? paraGrupo(v) : r)) });
  }

  protected trocarRegra(i: number, patch: Partial<Regra>): void {
    const g = this.grupo();
    this.emitir({ ...g, rules: g.rules.map((r, j) => (j === i && !eGrupo(r) ? { ...r, ...patch } : r)) });
  }

  protected remover(i: number): void {
    const g = this.grupo();
    this.emitir({ ...g, rules: g.rules.filter((_, j) => j !== i) });
  }

  protected adicionarRegra(): void {
    const g = this.grupo();
    this.emitir({ ...g, rules: [...g.rules, { left: '', operator: 'eq', right: '' }] });
  }

  protected adicionarGrupo(): void {
    const g = this.grupo();
    this.emitir({ ...g, rules: [...g.rules, { op: 'or', rules: [{ left: '', operator: 'eq', right: '' }] }] });
  }
}
