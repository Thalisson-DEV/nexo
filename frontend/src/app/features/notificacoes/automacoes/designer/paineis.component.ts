import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, afterNextRender, computed, inject, input, output, signal } from '@angular/core';
import { CesButtonComponent, CesIconButtonComponent, CesTabItem, CesTabPanelDirective, CesTabsComponent, CesTextareaComponent } from '@ces/ui';

import {
  Catalogo, Definicao, FlowNode, NoEspec, Params, Problema, Rascunho, RetryPolicy, RunAfter, RUN_AFTER_ROTULO, TipoAutomacao, Variavel
} from '../../../../core/models/automacao.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { AutomacoesService } from '../../../../core/services/automacoes.service';
import { buscarNo, idUnico, todosIds, texto } from '../lib/arvore';
import { TIPO_DADO, icone } from '../lib/dinamico';
import { FormParametrosComponent } from './form-parametros.component';

// ------------------------------------------------------------------------------ configuração do passo

type Aba = 'params' | 'config' | 'saidas';

@Component({
  selector: 'aut-painel-config',
  standalone: true,
  imports: [NgTemplateOutlet, CesButtonComponent, CesIconButtonComponent, CesTabsComponent, CesTabPanelDirective, FormParametrosComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (espec(); as e) {
      <aside class="aut-panel" [attr.data-group]="ehGatilho() ? 'trigger' : e.grupo" [attr.aria-label]="e.rotulo">
        <header class="aut-panel-head">
          <span class="aut-node-icon"><i [class]="icone(e.icone)" aria-hidden="true"></i></span>
          <div style="display:flex;flex-direction:column;min-width:0;flex:1">
            @if (ehGatilho()) {
              <span class="aut-panel-title">{{ e.rotulo }}</span>
              <span class="aut-muted">Quando começa</span>
            } @else {
              <input class="aut-title-input" aria-label="Nome do passo" [value]="no()?.label ?? ''" [placeholder]="e.rotulo"
                     (input)="mudarNo({ label: $any($event.target).value })" />
              <span class="aut-muted">{{ e.rotulo }}</span>
            }
          </div>
          @if (ehGatilho()) {
            <ces-icon-button icon="pi pi-arrow-right-arrow-left" label="Trocar o gatilho" variant="ghost" size="icon-sm" (pressed)="trocarGatilho.emit()"></ces-icon-button>
          } @else if (!somenteLeitura()) {
            <ces-icon-button icon="pi pi-copy" label="Duplicar" variant="ghost" size="icon-sm" (pressed)="duplicar.emit(selecionado())"></ces-icon-button>
            <ces-icon-button icon="pi pi-trash" label="Excluir passo" variant="destructive" size="icon-sm" (pressed)="excluir.emit(selecionado())"></ces-icon-button>
          }
          <ces-icon-button icon="pi pi-times" label="Fechar painel" variant="ghost" size="icon-sm" (pressed)="fechar.emit()"></ces-icon-button>
        </header>
        <p class="aut-muted" style="margin:0">{{ e.descricao }}</p>
        <ces-tabs class="aut-panel-tabs" [tabs]="abasDs()" [value]="aba()" (valueChange)="aba.set($any($event))" ariaLabel="Seções do passo">
          @for (a of abas(); track a[0]) {
            <ng-template [cesTabPanel]="a[0]"><ng-container [ngTemplateOutlet]="corpo" /></ng-template>
          }
        </ces-tabs>
        <ng-template #corpo>
        <div class="aut-panel-body">
          @if (proprios().length && aba() !== 'saidas') {
            <ul class="aut-issues">
              @for (p of soltos(); track $index) { <li [attr.data-severity]="p.severidade">{{ p.mensagem }}</li> }
            </ul>
          }
          @switch (aba()) {
            @case ('params') {
              <aut-form-parametros [espec]="e" [noId]="selecionado()" [no]="no()" [params]="params()" [definicao]="definicao()"
                                   [catalogo]="catalogo()" [problemas]="proprios()" (mudou)="mudarParams($event)" />
              @if (no(); as n) {
                @if (n.type === 'control.switch') {
                  <div class="aut-section">
                    <span class="aut-section-title">Casos</span>
                    <p class="aut-muted">Cada caso é uma coluna no canvas. Num caso, separe vários valores com ;</p>
                    @for (c of n.cases ?? []; track c.id) {
                      <div style="display:flex;gap:0.5rem">
                        <input class="p-inputtext ces-prime-control" [value]="c.value" placeholder="Juazeiro" aria-label="Valor do caso"
                               (input)="mudarNo({ cases: trocarCaso(n, c.id, $any($event.target).value) })" />
                        <ces-icon-button icon="pi pi-times" label="Remover caso" variant="ghost" size="icon-sm" (pressed)="mudarNo({ cases: (n.cases ?? []).filter(x => x.id !== c.id) })"></ces-icon-button>
                      </div>
                    }
                    <div><ces-button icon="pi pi-plus" label="Caso" variant="secondary" size="sm" (pressed)="novoCaso(n)"></ces-button></div>
                  </div>
                }
                @if (n.type === 'control.parallel') {
                  <div class="aut-section">
                    <span class="aut-section-title">Ramos</span>
                    @for (b of n.branches ?? []; track b.id) {
                      <div style="display:flex;gap:0.5rem">
                        <input class="p-inputtext ces-prime-control" [value]="b.label" aria-label="Nome do ramo"
                               (input)="mudarNo({ branches: trocarRamo(n, b.id, $any($event.target).value) })" />
                        <ces-icon-button icon="pi pi-times" label="Remover ramo" variant="ghost" size="icon-sm" (pressed)="mudarNo({ branches: (n.branches ?? []).filter(x => x.id !== b.id) })"></ces-icon-button>
                      </div>
                    }
                    <div><ces-button icon="pi pi-plus" label="Ramo" variant="secondary" size="sm" (pressed)="novoRamo(n)"></ces-button></div>
                  </div>
                }
              }
            }
            @case ('config') {
              @if (no(); as n) {
                <div class="aut-section">
                  <label class="aut-field">
                    <span class="ces-field__label">Anotação</span>
                    <input class="p-inputtext ces-prime-control" [value]="n.description ?? ''" placeholder="Por que este passo existe" (input)="mudarNo({ description: $any($event.target).value })" />
                  </label>
                  <div class="aut-field">
                    <span class="ces-field__label">Executar depois que o passo anterior…</span>
                    <div class="aut-toggles" role="group">
                      @for (s of situacoes; track s[0]) {
                        <button type="button" class="aut-toggle" [attr.aria-pressed]="runAfter(n).includes(s[0])" (click)="alternarRunAfter(n, s[0])">{{ s[1] }}</button>
                      }
                    </div>
                    <span class="aut-field-help">Use "Falhou" num passo depois de um Escopo para tratar erros (tentar/tratar).</span>
                  </div>
                  @if (!e.conteiner && !['control.wait', 'approval.request', 'control.terminate'].includes(n.type)) {
                    <label class="aut-field">
                      <span class="ces-field__label">Se falhar por instabilidade, tentar de novo</span>
                      <select class="p-inputtext ces-prime-control aut-select" (change)="mudarRetry(n, { policy: $any($event.target).value })">
                        @for (o of politicas; track o[0]) { <option [value]="o[0]" [selected]="retry(n).policy === o[0]">{{ o[1] }}</option> }
                      </select>
                    </label>
                    @if (retry(n).policy !== 'none') {
                      <div class="aut-grid2">
                        <label class="aut-field"><span class="ces-field__label">Vezes</span>
                          <input class="p-inputtext ces-prime-control" type="number" min="1" max="10" [value]="retry(n).count" (input)="mudarRetry(n, { count: +$any($event.target).value || 1 })" /></label>
                        <label class="aut-field"><span class="ces-field__label">Intervalo (s)</span>
                          <input class="p-inputtext ces-prime-control" type="number" min="1" max="3600" [value]="retry(n).interval_seconds" (input)="mudarRetry(n, { interval_seconds: +$any($event.target).value || 30 })" /></label>
                      </div>
                    }
                    <label class="aut-field">
                      <span class="ces-field__label">Tempo limite (segundos)</span>
                      <input class="p-inputtext ces-prime-control" type="number" min="1" [value]="n.timeout_seconds ?? ''" placeholder="Padrão do passo"
                             (input)="mudarNo({ timeout_seconds: $any($event.target).value ? +$any($event.target).value : null })" />
                    </label>
                  }
                  <p class="aut-muted">Id técnico: <code>{{ n.id }}</code></p>
                </div>
              }
            }
            @case ('saidas') {
              @if (!e.saidas.length) { <p class="aut-muted">Este passo não produz dados para os próximos.</p> }
              @else {
                <p class="aut-muted">Os próximos passos podem usar estes dados pelo botão "Inserir dado" dos campos.</p>
                <ul class="aut-outputs">
                  @for (s of e.saidas; track s.chave) {
                    <li>
                      <span style="display:flex;flex-direction:column;gap:0.125rem;min-width:0">
                        <strong style="font-weight:600">{{ s.rotulo }}</strong>
                        @if (s.descricao) { <span class="aut-muted">{{ s.descricao }}</span> }
                        @if (s.campos.length) { <span class="aut-muted">{{ campos(s.campos) }}</span> }
                      </span>
                      <span class="aut-type">{{ tipoDado(s.tipo) }}</span>
                    </li>
                  }
                </ul>
              }
            }
          }
        </div>
        </ng-template>
      </aside>
    }
  `
})
export class PainelConfigComponent {
  readonly selecionado = input.required<string>();
  readonly definicao = input.required<Definicao>();
  readonly catalogo = input.required<Catalogo>();
  readonly especs = input.required<Map<string, NoEspec>>();
  readonly problemas = input<Problema[]>([]);
  readonly somenteLeitura = input(false);
  readonly mudouNo = output<FlowNode>();
  readonly mudouGatilho = output<Params>();
  readonly trocarGatilho = output<void>();
  readonly excluir = output<string>();
  readonly duplicar = output<string>();
  readonly fechar = output<void>();

  protected readonly aba = signal<Aba>('params');
  protected readonly icone = icone;
  protected readonly situacoes = Object.entries(RUN_AFTER_ROTULO) as [RunAfter, string][];
  protected readonly politicas: [RetryPolicy['policy'], string][] = [['none', 'Não'], ['fixed', 'Sim, em intervalos fixos'], ['exponential', 'Sim, esperando cada vez mais']];

  protected readonly ehGatilho = computed(() => this.selecionado() === 'trigger');
  protected readonly no = computed(() => (this.ehGatilho() ? null : buscarNo(this.definicao(), this.selecionado()) ?? null));
  protected readonly espec = computed(() => this.especs().get(this.ehGatilho() ? this.definicao().trigger.type : this.no()?.type ?? ''));
  protected readonly params = computed(() => (this.ehGatilho() ? this.definicao().trigger.params : this.no()?.params ?? {}));
  protected readonly proprios = computed(() => this.problemas().filter((p) => (p.noId ?? '') === this.selecionado()));
  protected readonly soltos = computed(() => (this.aba() === 'params' ? this.proprios().filter((p) => !p.param) : this.proprios()));
  protected readonly abasDs = computed<CesTabItem[]>(() => this.abas().map(([value, label]) => ({ value, label })));
  protected readonly abas = computed<[Aba, string][]>(() =>
    this.ehGatilho() ? [['params', 'Parâmetros'], ['saidas', 'Dados gerados']] : [['params', 'Parâmetros'], ['config', 'Configurações'], ['saidas', 'Dados gerados']]);

  protected tipoDado(t: string): string {
    return TIPO_DADO[t] ?? 'Dado';
  }

  protected campos(c: { rotulo: string }[]): string {
    return c.map((x) => x.rotulo).join(' · ');
  }

  protected mudarParams(p: Params): void {
    if (this.ehGatilho()) this.mudouGatilho.emit(p);
    else if (this.no()) this.mudouNo.emit({ ...this.no()!, params: p });
  }

  protected mudarNo(patch: Partial<FlowNode>): void {
    const n = this.no();
    if (n) this.mudouNo.emit({ ...n, ...patch });
  }

  protected runAfter(n: FlowNode): RunAfter[] {
    return n.run_after ?? ['succeeded'];
  }

  protected alternarRunAfter(n: FlowNode, s: RunAfter): void {
    const atual = this.runAfter(n);
    const prox = atual.includes(s) ? atual.filter((x) => x !== s) : [...atual, s];
    if (prox.length) this.mudarNo({ run_after: prox });
  }

  protected retry(n: FlowNode): RetryPolicy {
    return n.retry ?? this.espec()?.repeticaoPadrao ?? { policy: 'none', count: 0, interval_seconds: 30 };
  }

  protected mudarRetry(n: FlowNode, patch: Partial<RetryPolicy>): void {
    const atual = this.retry(n);
    const prox = { ...atual, ...patch };
    if (patch.policy) prox.count = patch.policy === 'none' ? 0 : Math.max(1, atual.count || 3);
    this.mudarNo({ retry: prox });
  }

  protected trocarCaso(n: FlowNode, id: string, valor: string) {
    return (n.cases ?? []).map((c) => (c.id === id ? { ...c, value: valor } : c));
  }

  protected novoCaso(n: FlowNode): void {
    this.mudarNo({ cases: [...(n.cases ?? []), { id: idUnico('caso', todosIds(this.definicao())), value: '', steps: [] }] });
  }

  protected trocarRamo(n: FlowNode, id: string, rotulo: string) {
    return (n.branches ?? []).map((b) => (b.id === id ? { ...b, label: rotulo } : b));
  }

  protected novoRamo(n: FlowNode): void {
    const ramos = n.branches ?? [];
    this.mudarNo({ branches: [...ramos, { id: idUnico('ramo', todosIds(this.definicao())), label: `Ramo ${ramos.length + 1}`, steps: [] }] });
  }
}

// ------------------------------------------------------------------------------ adicionar

@Component({
  selector: 'aut-painel-adicionar',
  standalone: true,
  imports: [CesIconButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="aut aut-add" [attr.aria-label]="modo() === 'gatilho' ? 'Escolher gatilho' : 'Adicionar ação'">
      <header class="aut-panel-head">
        <span class="aut-panel-title" style="flex:1">{{ modo() === 'gatilho' ? 'Quando a automação começa?' : 'Adicionar ação' }}</span>
        <ces-icon-button icon="pi pi-times" label="Fechar" variant="ghost" size="icon-sm" (pressed)="fechar.emit()"></ces-icon-button>
      </header>
      <input #q class="p-inputtext ces-prime-control" placeholder="Buscar (ex.: e-mail, desconto, condição)" (input)="termo.set(q.value)" aria-label="Buscar" />
      @if (modo() === 'acao') { <p class="aut-muted" style="margin:0">Clique para adicionar ou arraste até um + no canvas.</p> }
      <div class="aut-add-list">
        @for (g of grupos(); track g.chave) {
          <div class="aut-add-group">
            <span class="aut-add-group-title">{{ g.titulo }}</span>
            @for (n of g.nos; track n.tipo) {
              <button type="button" class="aut-add-item" [attr.data-group]="n.grupo" [draggable]="modo() === 'acao'"
                      (dragstart)="arrastar($event, n)" (dragend)="arrastando.emit(false)" (click)="escolher.emit(n)">
                <span class="aut-node-icon"><i [class]="icone(n.icone)" aria-hidden="true"></i></span>
                <span style="display:flex;flex-direction:column;min-width:0">
                  <span class="aut-add-label">{{ n.rotulo }} @if (n.ia) { <span class="aut-tag">IA</span> }</span>
                  <span class="aut-add-desc">{{ n.descricao }}</span>
                </span>
              </button>
            }
          </div>
        }
        @if (!grupos().length) { <p class="aut-muted">Nada encontrado.</p> }
      </div>
    </aside>
  `
})
export class PainelAdicionarComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly focoInicial = afterNextRender(() => this.host.nativeElement.querySelector('input')?.focus());
  readonly catalogo = input.required<Catalogo>();
  readonly modo = input<'acao' | 'gatilho'>('acao');
  readonly escolher = output<NoEspec>();
  readonly fechar = output<void>();
  readonly arrastando = output<boolean>();
  protected readonly termo = signal('');
  protected readonly icone = icone;

  protected readonly grupos = computed(() => {
    const t = this.termo().trim().toLowerCase();
    const gatilho = this.modo() === 'gatilho';
    const nos = this.catalogo().nos.filter((n) => n.gatilho === gatilho)
      .filter((n) => !t || `${n.rotulo} ${n.descricao} ${n.palavras.join(' ')} ${n.subgrupo ?? ''}`.toLowerCase().includes(t));
    const grupos = new Map<string, { chave: string; titulo: string; nos: NoEspec[] }>();
    for (const n of nos) {
      const chave = gatilho ? (n.subgrupo ?? 'Geral') : n.grupo;
      const titulo = gatilho ? (n.subgrupo ?? 'Geral') : this.catalogo().grupos[n.grupo] ?? n.grupo;
      if (!grupos.has(chave)) grupos.set(chave, { chave, titulo, nos: [] });
      grupos.get(chave)!.nos.push(n);
    }
    return [...grupos.values()];
  });

  protected arrastar(e: DragEvent, n: NoEspec): void {
    e.dataTransfer?.setData('application/ces-automacao', `novo:${n.tipo}`);
    if (e.dataTransfer) e.dataTransfer.effectAllowed = 'copy';
    this.arrastando.emit(true);
  }
}

// ------------------------------------------------------------------------------ pedir ao Copiloto

interface Mensagem {
  de: 'user' | 'ai' | 'error';
  texto: string;
  faltando?: string[];
}

@Component({
  selector: 'aut-painel-pedir',
  standalone: true,
  imports: [CesButtonComponent, CesIconButtonComponent, CesTextareaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="aut-panel" data-group="ai" aria-label="Pedir ao Copiloto">
      <header class="aut-panel-head">
        <span class="aut-node-icon"><i class="pi pi-sparkles" aria-hidden="true"></i></span>
        <span class="aut-panel-title" style="flex:1">Pedir ao Copiloto</span>
        <ces-icon-button icon="pi pi-times" label="Fechar" variant="ghost" size="icon-sm" (pressed)="fechar.emit()"></ces-icon-button>
      </header>
      <div class="aut-panel-body">
        @if (!mensagens().length) {
          <p class="aut-muted" style="margin:0">Descreva o que mudar. O Copiloto ajusta o fluxo e você confere no canvas (dá para desfazer).</p>
          @for (ex of exemplos; track ex) {
            <button type="button" class="aut-ask-example" (click)="pedido.set(ex)">{{ ex }}</button>
          }
        }
        <div class="aut-ask-thread" aria-live="polite">
          @for (m of mensagens(); track $index) {
            <div class="aut-bubble" [attr.data-from]="m.de">
              {{ m.texto }}
              @if (m.faltando?.length) {
                <strong>Falta informar:</strong>
                <ul style="margin:0;padding-left:1.125rem">
                  @for (f of m.faltando!; track f) { <li>{{ f }}</li> }
                </ul>
              }
            </div>
          }
          @if (ocupado()) { <div class="aut-bubble" data-from="ai" data-busy>Montando o fluxo…</div> }
        </div>
      </div>
      <form class="aut-composer" (submit)="$event.preventDefault(); enviar()">
        <ces-textarea label="O que mudar" [labelVisible]="false" [rows]="3" placeholder="Ex.: se a base for Petrolina, avise também a Ana"
                      [value]="pedido()" (valueChange)="pedido.set($event)" (keydown.control.enter)="enviar()"></ces-textarea>
        <ces-button label="Pedir" variant="primary" size="sm" type="submit" [disabled]="ocupado() || !pedido().trim()"></ces-button>
      </form>
    </aside>
  `
})
export class PainelPedirComponent {
  readonly automacaoId = input.required<string>();
  readonly aplicado = output<Rascunho>();
  readonly fechar = output<void>();
  private readonly service = inject(AutomacoesService);
  protected readonly pedido = signal('');
  protected readonly ocupado = signal(false);
  protected readonly mensagens = signal<Mensagem[]>([]);
  protected readonly exemplos = [
    'Se o colaborador for da base de Juazeiro, avise o RH de Juazeiro; se for de Petrolina, o RH de Petrolina; senão, todo o RH.',
    'Mande também um e-mail com uma tabela do que mudou para quem pediu.',
    'Antes de enviar, peça aprovação ao coordenador, com prazo de 24 horas.'
  ];

  protected enviar(): void {
    const texto = this.pedido().trim();
    if (!texto || this.ocupado()) return;
    this.mensagens.update((m) => [...m, { de: 'user', texto }]);
    this.pedido.set('');
    this.ocupado.set(true);
    this.service.rascunho(texto, this.automacaoId()).subscribe({
      next: (r) => {
        this.ocupado.set(false);
        this.mensagens.update((m) => [...m, { de: 'ai', texto: r.resumo || 'Pronto: ajustei o fluxo. Confira no canvas.', faltando: r.faltando }]);
        this.aplicado.emit(r);
      },
      error: (e) => {
        this.ocupado.set(false);
        this.mensagens.update((m) => [...m, { de: 'error', texto: getErrorMessage(e) }]);
      }
    });
  }
}

// ------------------------------------------------------------------------------ configurações

@Component({
  selector: 'aut-painel-configuracoes',
  standalone: true,
  imports: [CesButtonComponent, CesIconButtonComponent, CesTextareaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="aut-panel" aria-label="Configurações da automação">
      <header class="aut-panel-head">
        <span class="aut-panel-title" style="flex:1">Configurações</span>
        <ces-icon-button icon="pi pi-times" label="Fechar" variant="ghost" size="icon-sm" (pressed)="fechar.emit()"></ces-icon-button>
      </header>
      <div class="aut-panel-body">
        <div class="aut-section">
          <label class="aut-field">
            <span class="ces-field__label">Tipo</span>
            <select class="p-inputtext ces-prime-control aut-select" (change)="mudouTipo.emit($any($event.target).value)">
              @for (t of catalogo().tipos; track t.valor) { <option [value]="t.valor" [selected]="t.valor === tipo()">{{ t.rotulo }}</option> }
            </select>
            <span class="aut-field-help">{{ descricaoTipo() }}</span>
          </label>
          <ces-textarea label="Descrição" [rows]="2" [value]="descricao()" (valueChange)="mudouDescricao.emit($event)"></ces-textarea>
        </div>
        <div class="aut-section">
          <span class="aut-section-title">Valores guardados (variáveis)</span>
          <p class="aut-muted" style="margin:0">Valores que os passos leem e alteram durante a execução, como um contador ou uma lista que vai sendo montada.</p>
          @for (v of definicao().variables; track $index; let i = $index) {
            <div class="aut-row">
              <div class="aut-row-fields">
                <input class="p-inputtext ces-prime-control aut-row-cell" [value]="v.name" placeholder="nome_sem_espaco" aria-label="Nome"
                       (input)="mudarVar(i, { name: $any($event.target).value })" />
                <select class="p-inputtext ces-prime-control aut-select aut-row-cell" aria-label="Tipo" (change)="mudarVar(i, { type: $any($event.target).value })">
                  @for (t of tiposVar; track t[0]) { <option [value]="t[0]" [selected]="v.type === t[0]">{{ t[1] }}</option> }
                </select>
                <input class="p-inputtext ces-prime-control aut-row-wide" [value]="texto(v.value)" placeholder="Valor inicial" aria-label="Valor inicial"
                       (input)="mudarVar(i, { value: $any($event.target).value })" />
                <input class="p-inputtext ces-prime-control aut-row-wide" [value]="v.description ?? ''" placeholder="Para que serve" aria-label="Descrição"
                       (input)="mudarVar(i, { description: $any($event.target).value })" />
              </div>
              <ces-icon-button icon="pi pi-trash" label="Remover variável" variant="ghost" size="icon-sm" (pressed)="removerVar(i)"></ces-icon-button>
            </div>
          }
          <div><ces-button icon="pi pi-plus" label="Valor guardado" variant="secondary" size="sm" (pressed)="novaVar()"></ces-button></div>
        </div>
        <div class="aut-section">
          <label class="aut-field">
            <span class="ces-field__label">Avisar por e-mail quando falhar</span>
            <input class="p-inputtext ces-prime-control" [value]="definicao().settings.notify_on_failure.join(', ')" placeholder="ti@exemplo.com.br"
                   (change)="mudarAviso($any($event.target).value)" />
          </label>
          <label class="aut-field">
            <span class="ces-field__label">Tempo limite da execução (horas)</span>
            <input class="p-inputtext ces-prime-control" type="number" min="1" max="720" [value]="definicao().settings.timeout_hours"
                   (input)="mudouDefinicao.emit({ ...definicao(), settings: { ...definicao().settings, timeout_hours: +$any($event.target).value || 168 } })" />
            <span class="aut-field-help">Esperas e aprovações contam neste tempo.</span>
          </label>
        </div>
      </div>
    </aside>
  `
})
export class PainelConfiguracoesComponent {
  readonly definicao = input.required<Definicao>();
  readonly catalogo = input.required<Catalogo>();
  readonly tipo = input.required<TipoAutomacao>();
  readonly descricao = input('');
  readonly mudouDefinicao = output<Definicao>();
  readonly mudouTipo = output<TipoAutomacao>();
  readonly mudouDescricao = output<string>();
  readonly fechar = output<void>();
  protected readonly texto = texto;
  protected readonly tiposVar: [Variavel['type'], string][] = [['string', 'Texto'], ['number', 'Número'], ['boolean', 'Sim/Não'], ['array', 'Lista'], ['object', 'Grupo de dados']];
  protected readonly descricaoTipo = computed(() => this.catalogo().tipos.find((t) => t.valor === this.tipo())?.descricao ?? '');

  private vars(lista: Variavel[]): void {
    this.mudouDefinicao.emit({ ...this.definicao(), variables: lista });
  }

  protected mudarVar(i: number, patch: Partial<Variavel>): void {
    this.vars(this.definicao().variables.map((v, j) => (j === i ? { ...v, ...patch } : v)));
  }

  protected novaVar(): void {
    const usados = new Set(this.definicao().variables.map((v) => v.name));
    this.vars([...this.definicao().variables, { name: idUnico('valor', usados), type: 'string', value: '' }]);
  }

  protected removerVar(i: number): void {
    this.vars(this.definicao().variables.filter((_, j) => j !== i));
  }

  protected mudarAviso(valor: string): void {
    const emails = valor.split(/[,;\s]+/).map((e) => e.trim()).filter(Boolean);
    this.mudouDefinicao.emit({ ...this.definicao(), settings: { ...this.definicao().settings, notify_on_failure: emails } });
  }
}
