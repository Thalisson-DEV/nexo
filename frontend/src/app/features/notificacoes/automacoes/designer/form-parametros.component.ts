import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { CesButtonComponent, CesCalloutComponent } from '@ces/ui';
import { catchError, of } from 'rxjs';

import { Catalogo, Definicao, FlowNode, JsonValue, NoEspec, ParamEspec, Params, Problema } from '../../../../core/models/automacao.models';
import { AutomacoesService } from '../../../../core/services/automacoes.service';
import { lista, texto } from '../lib/arvore';
import { legivel } from '../lib/resumo';
import { CampoExpressaoComponent } from './campo-expressao.component';
import { DestinatariosComponent } from './destinatarios.component';
import { CondicaoComponent, FichasComponent, LinhasComponent, RotasComponent } from './editores.component';
import { EditorEmailComponent } from './editor-email.component';
import { ContextoDinamico } from './seletor-dinamico.component';

const PARAMS_POR_ITEM = new Set(['data.filter:condition', 'data.select:mapping', 'data.table:columns']);
const DIAS: [string, string][] = [['0', 'Seg'], ['1', 'Ter'], ['2', 'Qua'], ['3', 'Qui'], ['4', 'Sex'], ['5', 'Sáb'], ['6', 'Dom']];

/** Os campos de um nó, desenhados a partir do catálogo (o mesmo que o servidor valida). */
@Component({
  selector: 'aut-form-parametros',
  standalone: true,
  imports: [CesCalloutComponent, CesButtonComponent, NgTemplateOutlet, CampoExpressaoComponent, DestinatariosComponent, CondicaoComponent, FichasComponent, LinhasComponent, RotasComponent, EditorEmailComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="aut-section">
      @for (p of basicos(); track p.chave) {
        <ng-container *ngTemplateOutlet="campo; context: { $implicit: p }" />
      }
      @if (avancados().length) {
        <ces-button [icon]="mostrarAvancado() ? 'pi pi-chevron-down' : 'pi pi-chevron-right'" label="Opções avançadas" variant="ghost" size="sm" style="align-self:flex-start" (pressed)="mostrarAvancado.set(!mostrarAvancado())" [attr.aria-expanded]="mostrarAvancado()"></ces-button>
        @if (mostrarAvancado()) {
          @for (p of avancados(); track p.chave) {
            <ng-container *ngTemplateOutlet="campo; context: { $implicit: p }" />
          }
        }
      }
      @if (espec().tipo === 'email.send' && !catalogo().emailPronto) {
        <ces-callout tone="warning" [dense]="true">O envio de e-mail está desligado neste ambiente: a execução monta o e-mail e o guarda, sem enviar.</ces-callout>
      }
      @if (espec().ia && !catalogo().iaPronta) {
        <ces-callout tone="warning" [dense]="true">O provedor de IA não está configurado: este passo vai falhar até ele ser configurado.</ces-callout>
      }
    </div>

    <ng-template #campo let-p>
      <div class="aut-field">
        <span class="ces-field__label">{{ p.rotulo }} @if (p.obrigatorio) { <span class="ces-field__required" aria-label="obrigatório">*</span> }</span>
        @switch (p.tipo) {
          @case ('boolean') {
            <label class="aut-check"><input type="checkbox" [checked]="valor(p) === true" (change)="alterar(p, $any($event.target).checked)" /> Sim</label>
          }
          @case ('select') {
            <select class="p-inputtext ces-prime-control aut-select" [attr.aria-label]="p.rotulo" (change)="alterar(p, $any($event.target).value)">
              @if (!texto(valor(p))) { <option value="" selected>Selecione</option> }
              @for (o of opcoesDe(p); track o[0]) { <option [value]="o[0]" [selected]="texto(valor(p)) === o[0]">{{ o[1] }}</option> }
            </select>
          }
          @case ('source') {
            <select class="p-inputtext ces-prime-control aut-select" [attr.aria-label]="p.rotulo" (change)="alterar(p, $any($event.target).value)">
              <option value="" [selected]="!texto(valor(p))">Selecione a fonte</option>
              @for (f of fontes(); track f.valor) { <option [value]="f.valor" [selected]="texto(valor(p)) === f.valor">{{ f.rotulo }}</option> }
            </select>
          }
          @case ('weekdays') {
            <div class="aut-toggles" role="group" [attr.aria-label]="p.rotulo">
              @for (d of dias; track d[0]) {
                <button type="button" class="aut-toggle" [attr.aria-pressed]="lista(valor(p)).includes(d[0])" (click)="alternarDia(p, d[0])">{{ d[1] }}</button>
              }
            </div>
          }
          @case ('automations') {
            <div class="aut-toggles" role="group">
              @for (a of catalogo().automacoes; track a.id) {
                <button type="button" class="aut-toggle" [attr.aria-pressed]="lista(valor(p)).includes(a.id)" (click)="alternarItem(p, a.id)">{{ a.nome }}</button>
              }
            </div>
          }
          @case ('time') {
            <input class="p-inputtext ces-prime-control" type="time" [value]="texto(valor(p))" (change)="alterar(p, $any($event.target).value)" [attr.aria-label]="p.rotulo" />
          }
          @case ('recipients') {
            <aut-destinatarios [valor]="valor(p)" [contexto]="contexto()" (mudou)="alterar(p, $event)" />
          }
          @case ('routes') {
            <aut-rotas [valor]="valor(p)" [contexto]="contexto()" (mudou)="alterar(p, $event)" />
          }
          @case ('condition') {
            <aut-condicao [valor]="valor(p)" [contexto]="ctxParam(p)" [operadores]="catalogo().operadores" (mudou)="alterar(p, $event)" />
          }
          @case ('list') {
            <aut-fichas [valor]="valor(p)" [contexto]="contexto()" [rotulo]="p.rotulo" [placeholder]="p.placeholder" (mudou)="alterar(p, $event)" />
          }
          @case ('emails') {
            <aut-fichas [valor]="valor(p)" [contexto]="contexto()" [rotulo]="p.rotulo" placeholder="nome@exemplo.com.br" (mudou)="alterar(p, $event)" />
          }
          @case ('fields') {
            <aut-linhas [valor]="valor(p)" [campos]="p.campos" [contexto]="contexto()" (mudou)="alterar(p, $event)" />
          }
          @case ('mapping') {
            <aut-linhas [valor]="valor(p)" [campos]="p.campos" [contexto]="contexto()" [fonteItem]="fonteItem(p)" (mudou)="alterar(p, $event)" />
          }
          @case ('columns') {
            <aut-linhas [valor]="valor(p)" [campos]="p.campos" [contexto]="contexto()" [fonteItem]="fonteItem(p)" (mudou)="alterar(p, $event)" />
          }
          @case ('keyvalue') {
            <aut-linhas [valor]="valor(p)" [campos]="p.campos" [contexto]="contexto()" (mudou)="alterar(p, $event)" />
          }
          @case ('html') {
            <div class="aut-html-preview">
              <span>{{ previa(p) || 'Mensagem ainda vazia.' }}</span>
              <ces-button icon="pi pi-envelope" label="Abrir o editor de e-mail" variant="outline" size="sm" (pressed)="emailAberto.set(true)"></ces-button>
            </div>
            <aut-editor-email [(aberto)]="emailAberto" [params]="params()" [contexto]="contexto()" (mudou)="alterarVarios($event)" />
          }
          @case ('textarea') {
            <aut-campo-expressao [valor]="texto(valor(p))" [contexto]="contexto()" [rotulo]="p.rotulo" [placeholder]="p.placeholder"
                                 [multilinha]="true" [dinamico]="p.dinamico" [invalido]="!!erro(p)" (mudou)="alterar(p, $event)" />
          }
          @case ('number') {
            @if (p.dinamico) {
              <aut-campo-expressao [valor]="texto(valor(p))" [contexto]="contexto()" [rotulo]="p.rotulo" [placeholder]="p.placeholder"
                                   [invalido]="!!erro(p)" (mudou)="alterarNumero(p, $event)" />
            } @else {
              <input class="p-inputtext ces-prime-control" type="number" [attr.min]="p.minimo" [attr.max]="p.maximo" [value]="texto(valor(p))"
                     [attr.aria-label]="p.rotulo" (input)="alterarNumero(p, $any($event.target).value)" />
            }
          }
          @default {
            @if (p.dinamico) {
              <aut-campo-expressao [valor]="texto(valor(p))" [contexto]="ctxParam(p)" [rotulo]="p.rotulo" [placeholder]="p.placeholder"
                                   [invalido]="!!erro(p)" (mudou)="alterar(p, $event)" />
            } @else {
              <input class="p-inputtext ces-prime-control" [value]="texto(valor(p))" [placeholder]="p.placeholder ?? ''" [attr.aria-label]="p.rotulo"
                     (input)="alterar(p, $any($event.target).value)" />
            }
          }
        }
        @if (erro(p); as e) { <span class="aut-field-error">{{ e }}</span> }
        @else if (p.ajuda) { <span class="aut-field-help">{{ p.ajuda }}</span> }
      </div>
    </ng-template>
  `
})
export class FormParametrosComponent {
  readonly espec = input.required<NoEspec>();
  readonly noId = input.required<string>();
  readonly no = input<FlowNode | null>(null);
  readonly params = input.required<Params>();
  readonly definicao = input.required<Definicao>();
  readonly catalogo = input.required<Catalogo>();
  readonly problemas = input<Problema[]>([]);
  readonly mudou = output<Params>();

  private readonly service = inject(AutomacoesService);
  protected readonly mostrarAvancado = signal(false);
  protected readonly emailAberto = signal(false);
  protected readonly dias = DIAS;
  protected readonly texto = texto;
  protected readonly lista = lista;

  protected readonly fontes = toSignal(this.service.opcoes('fonte', '').pipe(catchError(() => of([]))), { initialValue: [] });

  protected readonly contexto = computed<ContextoDinamico>(() => ({ definicao: this.definicao(), catalogo: this.catalogo(), noId: this.noId() }));

  private readonly visiveis = computed(() => this.espec().params.filter((p) => this.visivel(p)));
  protected readonly basicos = computed(() => this.visiveis().filter((p) => !p.avancado));
  protected readonly avancados = computed(() => this.visiveis().filter((p) => p.avancado));

  private visivel(p: ParamEspec): boolean {
    if (!p.mostrarSe || !p.mostrarSe.length) return true;
    const [outro, ...valores] = p.mostrarSe;
    const padrao = this.espec().params.find((x) => x.chave === outro)?.padrao;
    const atual = this.params()[outro!] ?? padrao;
    return valores.includes(texto(atual ?? undefined));
  }

  protected valor(p: ParamEspec): JsonValue | undefined {
    const v = this.params()[p.chave];
    return v === undefined ? p.padrao : v;
  }

  protected erro(p: ParamEspec): string | null {
    return this.problemas().find((x) => x.param === p.chave && x.severidade === 'error')?.mensagem ?? null;
  }

  protected opcoesDe(p: ParamEspec): [string, string][] {
    if (p.chave === 'name' && this.espec().tipo.startsWith('variable.')) {
      return this.definicao().variables.map((v) => [v.name, v.description || v.name]);
    }
    return p.opcoes;
  }

  protected fonteItem(p: ParamEspec): string | undefined {
    return PARAMS_POR_ITEM.has(`${this.espec().tipo}:${p.chave}`) ? texto(this.params()['items']) : undefined;
  }

  protected ctxParam(p: ParamEspec): ContextoDinamico {
    const f = this.fonteItem(p);
    return f !== undefined ? { ...this.contexto(), fonteItem: f } : this.contexto();
  }

  protected previa(p: ParamEspec): string {
    const html = texto(this.valor(p));
    const t = html
      .replace(/<span[^>]*data-expr="([^"]*)"[^>]*>[\s\S]*?<\/span>/g, (_m, e: string) => ` {{ ${e.replace(/&amp;/g, '&')} }} `)
      .replace(/<div[^>]*data-block="([^"]+)"[^>]*>[\s\S]*?<\/div>/g, (_m, b: string) => ` [Bloco: ${this.catalogo().blocos[b] ?? b}] `)
      .replace(/<\/(p|div|li|h\d)>/g, ' ')
      .replace(/<[^>]+>/g, ' ')
      .replace(/&nbsp;/g, ' ');
    const legivelTexto = legivel(t, this.definicao(), this.catalogo());
    return legivelTexto.length > 240 ? `${legivelTexto.slice(0, 238)}…` : legivelTexto;
  }

  protected alterar(p: ParamEspec, v: JsonValue): void {
    this.mudou.emit({ ...this.params(), [p.chave]: v });
  }

  /** O editor de e-mail muda assunto, área, destaque, botão e corpo de uma vez. */
  protected alterarVarios(campos: Partial<Params>): void {
    this.mudou.emit({ ...this.params(), ...(campos as Params) });
  }

  protected alterarNumero(p: ParamEspec, v: string): void {
    const t = (v ?? '').trim();
    this.alterar(p, /^-?\d+([.,]\d+)?$/.test(t) ? Number(t.replace(',', '.')) : t === '' ? null : t);
  }

  protected alternarDia(p: ParamEspec, dia: string): void {
    const atuais = lista(this.valor(p));
    const novos = atuais.includes(dia) ? atuais.filter((d) => d !== dia) : [...atuais, dia];
    this.alterar(p, novos.map(Number).sort((a, b) => a - b));
  }

  protected alternarItem(p: ParamEspec, item: string): void {
    const atuais = lista(this.valor(p));
    this.alterar(p, atuais.includes(item) ? atuais.filter((d) => d !== item) : [...atuais, item]);
  }
}
