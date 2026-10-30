import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { IconeComponent, NomeIcone } from './icone.component';

/** Seletor de quantidade: − valor +, com digitação direta pelo teclado decimal. */
@Component({
  selector: 'ces-quantidade',
  standalone: true,
  imports: [IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="caixa" [class.grande]="grande()" role="group" [attr.aria-label]="rotulo()">
      <button type="button" class="b" (click)="mudar(-passo())" [disabled]="valor() - passo() < minimo()" [attr.aria-label]="'Diminuir ' + rotulo()">
        <ces-icone nome="menos" [tamanho]="20" />
      </button>
      <span class="numero">
        <input #campo class="ces-num" type="text" inputmode="decimal" enterkeyhint="done" [value]="texto()" [style.width.ch]="largura()"
          (change)="digitar(campo.value); campo.value = texto()" (focus)="campo.select()" [attr.aria-label]="rotulo()">
        @if (unidade()) { <span class="un" aria-hidden="true">{{ unidade() }}</span> }
      </span>
      <button type="button" class="b" (click)="mudar(passo())" [attr.aria-label]="'Aumentar ' + rotulo()">
        <ces-icone nome="mais" [tamanho]="20" />
      </button>
    </div>
  `,
  styles: [`
    :host { display: inline-flex; }
    .caixa { display: inline-flex; align-items: center; border: 1px solid var(--ces-linha-forte); border-radius: var(--ces-r-pilula); }
    .b { width: 44px; height: 44px; border: 0; background: transparent; color: var(--ces-destaque); display: grid; place-items: center;
      border-radius: 50%; cursor: pointer; }
    .b:active:not(:disabled) { background: var(--ces-destaque-suave); }
    .b:disabled { color: var(--ces-texto-3); }
    .numero { display: inline-flex; align-items: baseline; justify-content: center; gap: 3px; min-width: 3.2em; }
    input { min-width: 2ch; border: 0; background: transparent; text-align: center; color: var(--ces-texto);
      font: 600 1rem/1 var(--ces-fonte); padding: 0; }
    input:focus { outline: none; }
    .un { font: 600 .8125rem/1 var(--ces-fonte); color: var(--ces-texto-2); }
    /* Versão para lançamentos: o número é a informação principal da linha. */
    .grande { border-width: 1.5px; }
    .grande .b { width: 52px; height: 52px; }
    .grande .numero { min-width: 4.5em; }
    .grande input { font: 700 1.5rem/1 var(--ces-fonte); }
    .grande .un { font-size: .9375rem; }
  `],
})
export class QuantidadeComponent {
  readonly valor = model.required<number>();
  readonly minimo = input(1);
  readonly passo = input(1);
  readonly rotulo = input('quantidade');
  readonly unidade = input<string | null>(null);
  readonly grande = input(false);
  /** Vírgula decimal, como o usuário escreve e lê. */
  readonly texto = computed(() => String(this.valor()).replace('.', ','));
  readonly largura = computed(() => Math.max(2, this.texto().length + 0.5));

  mudar(delta: number): void {
    const n = arredondar(this.valor() + delta);
    // Abaixo do mínimo o botão não faz nada (0,5 − 1 não vira 0,001).
    if (n < this.minimo()) return;
    this.valor.set(n);
  }

  digitar(texto: string): void {
    const n = Number(String(texto).replace(',', '.'));
    this.valor.set(Number.isFinite(n) ? Math.max(this.minimo(), arredondar(n)) : this.valor());
  }
}

function arredondar(n: number): number {
  return Math.round(n * 1000) / 1000;
}

export interface OpcaoSegmento<T extends string = string> {
  valor: T;
  rotulo: string;
  icone?: NomeIcone;
}

/** Escolha entre 2–3 opções exclusivas. */
@Component({
  selector: 'ces-segmentado',
  standalone: true,
  imports: [IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="trilho" role="radiogroup" [attr.aria-label]="rotulo()" [style.grid-template-columns]="colunas()">
      @for (o of opcoes(); track o.valor) {
        <button type="button" role="radio" [attr.aria-checked]="o.valor === valor()" (click)="valor.set(o.valor)">
          @if (o.icone) { <ces-icone [nome]="o.icone" [tamanho]="18" /> }
          {{ o.rotulo }}
        </button>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .trilho { display: grid; background: var(--ces-superficie-2); border-radius: var(--ces-r-pilula); padding: 3px; gap: 2px; }
    button { min-height: 40px; border: 0; border-radius: var(--ces-r-pilula); background: transparent; color: var(--ces-texto-2);
      font: 500 .875rem/1.1 var(--ces-fonte); display: inline-flex; align-items: center; justify-content: center; gap: 6px; cursor: pointer;
      transition: background-color var(--ces-m-entrar) var(--ces-curva), color var(--ces-m-entrar); }
    button[aria-checked='true'] { background: var(--ces-superficie); color: var(--ces-texto); font-weight: 600;
      box-shadow: 0 1px 2px rgba(0, 0, 0, .1); }
  `],
})
export class SegmentadoComponent<T extends string = string> {
  readonly opcoes = input.required<OpcaoSegmento<T>[]>();
  readonly valor = model.required<T>();
  readonly rotulo = input('');
  readonly colunas = computed(() => `repeat(${this.opcoes().length}, 1fr)`);
}

/** Estado vazio útil: diz o que aparece aqui e como começar. */
@Component({
  selector: 'ces-vazio',
  standalone: true,
  imports: [IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="vazio">
      <span class="ces-selo ces-selo--destaque"><ces-icone [nome]="icone()" /></span>
      <h2>{{ titulo() }}</h2>
      @if (texto()) { <p>{{ texto() }}</p> }
      <ng-content />
    </div>
  `,
  styles: [`
    :host { display: block; }
    .vazio { display: grid; justify-items: center; text-align: center; gap: var(--ces-e2); padding: var(--ces-e8) var(--ces-e6); }
    h2 { margin: var(--ces-e2) 0 0; font: var(--ces-t-barra); }
    p { margin: 0; font: var(--ces-t-corpo); color: var(--ces-texto-2); max-width: 32ch; }
  `],
})
export class VazioComponent {
  readonly icone = input<NomeIcone>('info');
  readonly titulo = input.required<string>();
  readonly texto = input<string | null>(null);
}
