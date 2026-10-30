import { ChangeDetectionStrategy, Component, computed, input, model, output, signal } from '@angular/core';
import { FolhaComponent } from './folha.component';
import { IconeComponent } from './icone.component';

export interface OpcaoSeletor {
  valor: string;
  rotulo: string;
  apoio?: string | null;
  desabilitado?: boolean;
}

/**
 * Campo de escolha no padrão do celular: uma linha com o rótulo e o valor; o toque
 * abre uma folha inferior com busca. Substitui `<select>` e dropdowns caseiros.
 * Um valor (`valor`) ou vários (`valores`, com `multiplo`). `aberto` é um model para
 * a tela poder persistir a folha aberta no rascunho.
 */
@Component({
  selector: 'ces-seletor',
  standalone: true,
  imports: [FolhaComponent, IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="campo" [class.vazio]="!resumo()" [class.invalido]="invalido()" [disabled]="desabilitado()"
      (click)="abrir()" [attr.aria-haspopup]="'dialog'" [attr.aria-label]="rotulo() + ': ' + (resumo() || 'não escolhido')">
      <span class="txt">
        <span class="rotulo">{{ rotulo() }}@if (obrigatorio()) {<span aria-hidden="true"> *</span>}</span>
        <span class="valor">{{ resumo() || placeholder() }}</span>
      </span>
      <ces-icone nome="avancar" [tamanho]="18" />
    </button>

    <ces-folha [aberta]="aberto()" (fechar)="fechar()" [titulo]="rotulo()" [descricao]="multiplo() ? 'Marque quantos precisar.' : null">
      @if (opcoes().length > 6) {
        <div class="busca">
          <ces-icone nome="busca" [tamanho]="18" />
          <input type="search" placeholder="Buscar" enterkeyhint="search" [attr.aria-label]="'Buscar ' + rotulo()"
            [value]="busca()" (input)="busca.set($any($event.target).value)">
        </div>
      }
      <ul class="ces-grupo lista" [attr.role]="multiplo() ? 'group' : 'radiogroup'" [attr.aria-label]="rotulo()">
        @for (o of filtradas(); track o.valor) {
          <li>
            <button type="button" class="ces-linha ces-linha--acao" [attr.role]="multiplo() ? 'checkbox' : 'radio'"
              [attr.aria-checked]="marcado(o.valor)" [disabled]="o.desabilitado" (click)="escolher(o)">
              @if (multiplo()) {
                <span class="caixa" [class.marcada]="marcado(o.valor)" aria-hidden="true"><ces-icone nome="check" [tamanho]="16" /></span>
              }
              <span class="ces-linha__texto">
                <span class="ces-linha__titulo">{{ o.rotulo }}</span>
                @if (o.apoio) { <span class="ces-linha__apoio">{{ o.apoio }}</span> }
              </span>
              @if (!multiplo() && marcado(o.valor)) { <ces-icone nome="check" /> }
            </button>
          </li>
        } @empty {
          <li><div class="ces-linha"><span class="ces-linha__apoio">Nada encontrado</span></div></li>
        }
      </ul>
      @if (multiplo()) {
        <div rodape class="rodape"><button type="button" class="ces-btn ces-btn--principal ces-btn--bloco" (click)="fechar()">Pronto</button></div>
      }
    </ces-folha>
  `,
  styles: [`
    :host { display: block; }
    .campo { width: 100%; display: flex; align-items: center; gap: var(--ces-e3); min-height: 64px; padding: var(--ces-e3) var(--ces-e4);
      background: var(--ces-superficie); border: 0; border-radius: var(--ces-r-grupo); text-align: left; color: var(--ces-texto); cursor: pointer; }
    .campo:active:not(:disabled) { background: var(--ces-superficie-2); }
    .campo:disabled { cursor: default; opacity: .7; }
    .campo.invalido { box-shadow: inset 0 0 0 1.5px var(--ces-erro); }
    .txt { flex: 1; min-width: 0; display: grid; gap: 2px; }
    .rotulo { font: 500 .8125rem/1.3 var(--ces-fonte); color: var(--ces-texto-2); }
    .valor { font: var(--ces-t-corpo-forte); overflow-wrap: anywhere; }
    .vazio .valor { color: var(--ces-texto-3); font-weight: 400; }
    .busca { margin: 0 var(--ces-e3) var(--ces-e3); display: flex; align-items: center; gap: var(--ces-e2); padding: 0 var(--ces-e4); min-height: 48px;
      border-radius: var(--ces-r-pilula); background: var(--ces-superficie-2); color: var(--ces-texto-2); }
    .busca input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ces-texto); font: var(--ces-t-corpo); font-size: 1rem; }
    .busca input:focus { outline: none; }
    .lista { background: var(--ces-fundo); }
    .lista .ces-linha:disabled { opacity: .5; }
    .caixa { width: 24px; height: 24px; border-radius: 7px; border: 2px solid var(--ces-linha-forte); display: grid; place-items: center; color: transparent; flex: none; }
    .caixa.marcada { background: var(--ces-destaque); border-color: var(--ces-destaque); color: var(--ces-destaque-texto); }
    .rodape { padding: var(--ces-e3) var(--ces-e4) 0; }
  `],
})
export class SeletorComponent {
  readonly rotulo = input.required<string>();
  readonly opcoes = input.required<OpcaoSeletor[]>();
  readonly placeholder = input('Escolher');
  readonly obrigatorio = input(false);
  readonly multiplo = input(false);
  readonly desabilitado = input(false);
  readonly invalido = input(false);
  readonly valor = model<string | null>(null);
  readonly valores = model<string[]>([]);
  readonly aberto = model(false);
  readonly escolhido = output<OpcaoSeletor>();
  readonly busca = signal('');

  readonly filtradas = computed(() => {
    const q = normalizar(this.busca());
    return q ? this.opcoes().filter(o => normalizar(`${o.rotulo} ${o.apoio ?? ''}`).includes(q)) : this.opcoes();
  });

  readonly resumo = computed(() => {
    if (this.multiplo()) {
      const sel = this.opcoes().filter(o => this.valores().includes(o.valor));
      if (!sel.length) return '';
      return sel.length <= 2 ? sel.map(o => o.rotulo).join(', ') : `${sel[0].rotulo} e mais ${sel.length - 1}`;
    }
    return this.opcoes().find(o => o.valor === this.valor())?.rotulo ?? '';
  });

  marcado(v: string): boolean {
    return this.multiplo() ? this.valores().includes(v) : this.valor() === v;
  }

  abrir(): void {
    this.busca.set('');
    this.aberto.set(true);
  }

  fechar(): void {
    this.aberto.set(false);
  }

  escolher(o: OpcaoSeletor): void {
    if (o.desabilitado) return;
    if (this.multiplo()) {
      const atual = this.valores();
      this.valores.set(atual.includes(o.valor) ? atual.filter(v => v !== o.valor) : [...atual, o.valor]);
    } else {
      this.valor.set(o.valor);
      this.fechar();
    }
    this.escolhido.emit(o);
  }
}

function normalizar(v: string): string {
  return (v || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}
