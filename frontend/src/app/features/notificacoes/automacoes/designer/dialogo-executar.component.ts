import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { CesDialogShellComponent, CesButtonComponent, CesCalloutComponent, CesTextareaComponent } from '@ces/ui';

import { Definicao, JsonValue, NoEspec, Opcao } from '../../../../core/models/automacao.models';
import { AutomacoesService } from '../../../../core/services/automacoes.service';
import { objetos, texto } from '../lib/arvore';

interface Pergunta {
  nome: string;
  rotulo: string;
  tipo: string;
  obrigatorio: boolean;
}

/** Executar agora ou testar: pede o que o gatilho precisa (informações do manual, dados de exemplo do evento). */
@Component({
  selector: 'aut-dialogo-executar',
  standalone: true,
  imports: [CesCalloutComponent, CesButtonComponent, CesDialogShellComponent, CesTextareaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-dialog-shell [(visible)]="aberto" [title]="teste() ? 'Testar a automação' : 'Executar agora'" size="md" [loading]="ocupado()" loadingLabel="Iniciando">
      <div class="aut" style="display:flex;flex-direction:column;gap:0.875rem">
        @if (teste()) {
          <ces-callout tone="info" [dense]="true">No teste os e-mails e avisos vão só para você, as esperas são puladas e as aprovações são aprovadas automaticamente.</ces-callout>
        }
        @if (temColaborador()) {
          <div class="aut-field" style="position:relative">
            <span class="ces-field__label">Colaborador do exemplo</span>
            <input class="p-inputtext ces-prime-control" placeholder="Busque um colaborador real (ex.: da base de Juazeiro)" (input)="buscarColaborador($any($event.target).value)" />
            @if (opcoes().length) {
              <div class="aut-rows" style="max-height:12rem;overflow:auto">
                @for (o of opcoes(); track o.valor) {
                  <button type="button" class="aut-dyn-item" style="padding-left:0.5rem" [attr.aria-pressed]="valores()['idColaborador'] === o.valor" (click)="escolherColaborador(o)">
                    <span style="display:flex;flex-direction:column;text-align:start"><span>{{ o.rotulo }}</span><small class="aut-muted">{{ o.detalhe }}</small></span>
                  </button>
                }
              </div>
            }
            @if (colaboradorEscolhido()) { <span class="aut-field-help">Escolhido: {{ colaboradorEscolhido() }}</span> }
          </div>
        }
        @for (p of perguntas(); track p.nome) {
          <label class="aut-field">
            <span class="ces-field__label">{{ p.rotulo }} @if (p.obrigatorio) { <span class="ces-field__required">*</span> }</span>
            @if (p.tipo === 'boolean') {
              <input type="checkbox" [checked]="valores()[p.nome] === true" (change)="definir(p.nome, $any($event.target).checked)" />
            } @else if (p.tipo === 'longtext') {
              <ces-textarea [label]="p.rotulo" [labelVisible]="false" [rows]="3" (valueChange)="definir(p.nome, $event)"></ces-textarea>
            } @else {
              <input class="p-inputtext ces-prime-control" [type]="p.tipo === 'number' ? 'number' : p.tipo === 'date' ? 'date' : 'text'" (input)="definir(p.nome, $any($event.target).value)" />
            }
          </label>
        }
        @if (!perguntas().length && !temColaborador()) {
          <p class="aut-muted" style="margin:0">Nada a informar: a execução começa com os dados do gatilho.</p>
        }
        @if (erro()) { <ces-callout tone="danger" [dense]="true">{{ erro() }}</ces-callout> }
      </div>
      <div dialog-footer>
        <ces-button label="Cancelar" variant="ghost" size="sm" (pressed)="aberto.set(false)"></ces-button>
        <ces-button [icon]="teste() ? 'pi pi-bolt' : 'pi pi-play'" [label]="teste() ? 'Testar' : 'Executar'" variant="primary" size="sm" [disabled]="ocupado()" (pressed)="confirmar()"></ces-button>
      </div>
    </ces-dialog-shell>
  `
})
export class DialogoExecutarComponent {
  readonly aberto = model(false);
  readonly teste = input(false);
  readonly definicao = input.required<Definicao>();
  readonly especs = input.required<Map<string, NoEspec>>();
  readonly ocupado = input(false);
  readonly erro = input<string | null>(null);
  readonly executar = output<Record<string, JsonValue>>();

  private readonly service = inject(AutomacoesService);
  protected readonly valores = signal<Record<string, JsonValue>>({});
  protected readonly opcoes = signal<Opcao[]>([]);
  protected readonly colaboradorEscolhido = signal<string | null>(null);

  private readonly espec = computed(() => this.especs().get(this.definicao().trigger.type));
  protected readonly temColaborador = computed(() => !!this.espec()?.saidas.some((s) => s.chave === 'colaborador'));

  protected readonly perguntas = computed<Pergunta[]>(() => {
    const d = this.definicao();
    const e = this.espec();
    if (!e) return [];
    if (e.tipo === 'trigger.manual') {
      return objetos(d.trigger.params['inputs']).map((f) => ({
        nome: texto(f['name']), rotulo: texto(f['label']) || texto(f['name']), tipo: texto(f['type']) || 'text', obrigatorio: f['required'] === true
      })).filter((p) => p.nome);
    }
    if (e.tipo.startsWith('threshold.')) return [{ nome: 'total', rotulo: 'Total de exemplo', tipo: 'number', obrigatorio: false }];
    if (!e.chaveEvento || !this.teste()) return [];
    return e.saidas
      .filter((s) => ['string', 'number', 'boolean'].includes(s.tipo) && !['data', 'hora', 'id_referencia', 'idColaborador'].includes(s.chave))
      .map((s) => ({ nome: s.chave, rotulo: `${s.rotulo} (exemplo)`, tipo: s.tipo === 'number' ? 'number' : s.tipo === 'boolean' ? 'boolean' : 'text', obrigatorio: false }));
  });

  constructor() {
    effect(() => {
      if (this.aberto()) {
        this.valores.set({});
        this.opcoes.set([]);
        this.colaboradorEscolhido.set(null);
      }
    });
  }

  protected definir(nome: string, v: JsonValue): void {
    this.valores.update((x) => ({ ...x, [nome]: v }));
  }

  protected buscarColaborador(termo: string): void {
    if (termo.trim().length < 2) {
      this.opcoes.set([]);
      return;
    }
    this.service.opcoes('colaborador', termo).subscribe({ next: (o) => this.opcoes.set(o), error: () => this.opcoes.set([]) });
  }

  protected escolherColaborador(o: Opcao): void {
    this.definir('idColaborador', o.valor);
    this.colaboradorEscolhido.set(`${o.rotulo}${o.detalhe ? ' — ' + o.detalhe : ''}`);
    this.opcoes.set([]);
  }

  protected confirmar(): void {
    this.executar.emit(this.valores());
  }
}
