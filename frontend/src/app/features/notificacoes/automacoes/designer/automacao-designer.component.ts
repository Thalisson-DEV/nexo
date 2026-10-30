import {
  ChangeDetectionStrategy, Component, DestroyRef, HostListener, ViewEncapsulation, computed, effect, inject, signal
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, forkJoin, switchMap, catchError, of } from 'rxjs';
import { CesAsyncStateComponent, NotificationService, CesButtonComponent, CesIconButtonComponent, CesStatusComponent, CesCalloutComponent } from '@ces/ui';

import {
  AutomacaoDetalhe, Catalogo, Definicao, FlowNode, JsonValue, NoEspec, Params, Rascunho, STATUS_ROTULO, TipoAutomacao, Validacao
} from '../../../../core/models/automacao.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { AutomacoesService } from '../../../../core/services/automacoes.service';
import {
  InsertTarget, atualizarNo, buscarNo, duplicarNo, inserirNo, moverNo, normalizar, novoNo, parametrosPadrao, removerNo, todosIds
} from '../lib/arvore';
import { mapaEspecs } from '../lib/dinamico';
import { CanvasComponent } from './canvas.component';
import { DialogoExecutarComponent } from './dialogo-executar.component';
import {
  PainelAdicionarComponent, PainelConfigComponent, PainelConfiguracoesComponent, PainelPedirComponent
} from './paineis.component';

interface Estado {
  definicao: Definicao;
  nome: string;
  descricao: string;
  tipo: TipoAutomacao;
}

type Lado =
  | { tipo: 'config'; id: string }
  | { tipo: 'adicionar'; modo: 'acao' | 'gatilho'; alvo: InsertTarget | null }
  | { tipo: 'pedir' }
  | { tipo: 'configuracoes' }
  | null;

const LIMITE_HISTORICO = 80;

function iguais(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

/** Designer em tela cheia: o canvas no centro, painéis dos lados, como no TON. */
@Component({
  selector: 'app-automacao-designer',
  standalone: true,
  imports: [CesStatusComponent, CesCalloutComponent, CesButtonComponent, CesIconButtonComponent, CesAsyncStateComponent, CanvasComponent, PainelConfigComponent, PainelAdicionarComponent, PainelPedirComponent,
    PainelConfiguracoesComponent, DialogoExecutarComponent],
  styleUrls: ['../automacoes.css', '../automacoes-canvas.css', '../automacoes-campos.css', '../automacoes-email.css', '../automacoes-execucao.css'],
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!detalhe() || !catalogo()) {
      <div class="aut" style="padding:2rem">
        @if (erroCarga()) {
          <ces-async-state state="error" title="Não foi possível abrir a automação" [description]="erroCarga()!" actionLabel="Tentar de novo" (action)="carregar()" />
        } @else {
          <ces-async-state state="loading" title="Abrindo o editor" />
        }
      </div>
    } @else {
      <div class="aut aut-designer">
        <header class="aut-topbar">
          <ces-icon-button icon="pi pi-arrow-left" label="Voltar" variant="ghost" size="icon-sm" (pressed)="voltar()"></ces-icon-button>
          <div class="aut-topbar-title">
            <input class="aut-name" aria-label="Nome da automação" maxlength="120" [value]="estado().nome" (input)="commit({ nome: $any($event.target).value })" />
            <span class="aut-topbar-meta">
              <ces-status [tone]="$any(tomStatus()) ?? 'neutral'">{{ statusRotulo() }}</ces-status>
              <span class="aut-save-state" [attr.data-dirty]="sujo() || null">{{ ocupado() === 'salvar' ? 'Salvando…' : sujo() ? 'Alterações não salvas' : 'Salvo' }}</span>
            </span>
          </div>
          <span style="flex:1"></span>
          <ces-icon-button icon="pi pi-undo" label="Desfazer" variant="ghost" size="icon-sm" [disabled]="!passado().length" (pressed)="desfazer()"></ces-icon-button>
          <ces-icon-button icon="pi pi-refresh" label="Refazer" variant="ghost" size="icon-sm" [disabled]="!futuro().length" (pressed)="refazer()"></ces-icon-button>
          <span style="position:relative">
            <ces-button size="sm" aria-haspopup="dialog" [variant]="erros() ? 'destructive' : 'ghost'" [icon]="erros() || avisos() ? 'pi pi-exclamation-circle' : 'pi pi-check-circle'"
              [label]="erros() || avisos() ? (erros() ? erros() + ' erro(s)' : '') + (erros() && avisos() ? ' · ' : '') + (avisos() ? avisos() + ' aviso(s)' : '') : 'Fluxo verificado'"
              (pressed)="verificadorAberto.set(!verificadorAberto())"></ces-button>
            @if (verificadorAberto()) {
              <div class="aut-popover" style="right:0;top:calc(100% + 6px)" role="dialog" aria-label="Verificador do fluxo">
                <strong>Verificador do fluxo</strong>
                @if (validacao()?.erroEstrutura) { <div class="aut-checker-item" data-severity="error">{{ validacao()!.erroEstrutura }}</div> }
                @if (!(validacao()?.problemas?.length) && !problemasTipo().length) { <span class="aut-muted">Nenhum problema encontrado.</span> }
                @for (p of validacao()?.problemas ?? []; track $index) {
                  <button type="button" class="aut-checker-item" [attr.data-severity]="p.severidade" (click)="irPara(p.noId)">
                    <span class="aut-checker-node">{{ nomeNo(p.noId) }}</span>
                    <span>{{ p.mensagem }}</span>
                  </button>
                }
                @for (p of problemasTipo(); track p) { <div class="aut-checker-item" data-severity="error">{{ p }}</div> }
              </div>
            }
          </span>
          <ces-icon-button icon="pi pi-cog" label="Configurações" size="icon-sm" [variant]="lado()?.tipo === 'configuracoes' ? 'secondary' : 'ghost'" (pressed)="alternarLado({ tipo: 'configuracoes' })"></ces-icon-button>
          <ces-button icon="pi pi-sparkles" label="Pedir ao Copiloto" size="sm" [variant]="lado()?.tipo === 'pedir' ? 'secondary' : 'ghost'" (pressed)="alternarLado({ tipo: 'pedir' })"></ces-button>
          <span class="aut-topbar-sep" aria-hidden="true"></span>
          <ces-button icon="pi pi-play" label="Testar" variant="outline" size="sm" [title]="erros() ? 'Corrija os erros antes de testar' : 'E-mails e avisos só para você'" [disabled]="!!ocupado() || erros() > 0" (pressed)="abrirExecucao(true)"></ces-button>
          @if (detalhe()!.resumo.status !== 'ATIVA') {
            <ces-button icon="pi pi-check" label="Ativar" variant="outline" size="sm" [disabled]="!!ocupado() || erros() > 0 || problemasTipo().length > 0" (pressed)="mudarStatus('ATIVA')"></ces-button>
          } @else {
            <ces-button icon="pi pi-pause" label="Pausar" variant="outline" size="sm" [disabled]="!!ocupado()" (pressed)="mudarStatus('PAUSADA')"></ces-button>
          }
          <ces-button [label]="ocupado() === 'salvar' ? 'Salvando…' : 'Salvar'" variant="primary" size="sm" [disabled]="!!ocupado() || !sujo()" (pressed)="salvar()"></ces-button>
        </header>
        @if (erro()) { <ces-callout tone="danger" [dense]="true" role="alert">{{ erro() }} <button type="button" class="aut-link" (click)="erro.set(null)">Fechar</button></ces-callout> }
        <div class="aut-workspace">
          @if (lado(); as l) {
            @if (l.tipo === 'adicionar') {
              <aut-painel-adicionar [catalogo]="catalogo()!" [modo]="l.modo" (fechar)="lado.set(null)" (arrastando)="arrastandoPaleta.set($event)"
                                    (escolher)="escolherDoPainel($event)" />
            }
          }
          <aut-canvas [definicao]="estado().definicao" [catalogo]="catalogo()!" [especs]="especs()" [selecionado]="selecionado()"
                      [problemas]="problemasPorNo()" [arrastandoPaleta]="arrastandoPaleta()" [foco]="foco()"
                      (selecionar)="lado.set({ tipo: 'config', id: $event })"
                      (inserirEm)="lado.set({ tipo: 'adicionar', modo: 'acao', alvo: $event })"
                      (soltarNovo)="soltarNovo($event.alvo, $event.tipo)"
                      (mover)="definir(moverNo(estado().definicao, $event.id, $event.alvo))"
                      (posicionar)="posicionar($event.id, $event.x, $event.y)"
                      (reorganizar)="definir({ ...estado().definicao, layout: {} })" />
          @if (lado(); as l) {
            @switch (l.tipo) {
              @case ('config') {
                <aut-painel-config [selecionado]="l.id" [definicao]="estado().definicao" [catalogo]="catalogo()!" [especs]="especs()"
                                   [problemas]="validacao()?.problemas ?? []"
                                   (mudouNo)="mudarNo($event)"
                                   (mudouGatilho)="mudarGatilho($event)"
                                   (trocarGatilho)="lado.set({ tipo: 'adicionar', modo: 'gatilho', alvo: null })"
                                   (excluir)="excluir($event)" (duplicar)="duplicar($event)" (fechar)="lado.set(null)" />
              }
              @case ('pedir') {
                <aut-painel-pedir [automacaoId]="detalhe()!.resumo.id" (aplicado)="aplicarRascunho($event)" (fechar)="lado.set(null)" />
              }
              @case ('configuracoes') {
                <aut-painel-configuracoes [definicao]="estado().definicao" [catalogo]="catalogo()!" [tipo]="estado().tipo" [descricao]="estado().descricao"
                                          (mudouDefinicao)="definir($event)" (mudouTipo)="commit({ tipo: $event })" (mudouDescricao)="commit({ descricao: $event })"
                                          (fechar)="lado.set(null)" />
              }
            }
          }
        </div>
        <aut-dialogo-executar [(aberto)]="execucaoAberta" [teste]="execucaoTeste()" [definicao]="estado().definicao" [especs]="especs()"
                              [ocupado]="ocupado() === 'executar'" [erro]="erroExecucao()" (executar)="executar($event)" />
      </div>
    }
  `
})
export class AutomacaoDesignerComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected voltar(): void {
    void this.router.navigate(['/notificacoes/automacoes', this.detalhe()!.resumo.id]);
  }
  private readonly service = inject(AutomacoesService);
  private readonly notificacao = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly detalhe = signal<AutomacaoDetalhe | null>(null);
  protected readonly catalogo = signal<Catalogo | null>(null);
  protected readonly erroCarga = signal<string | null>(null);
  protected readonly estado = signal<Estado>({ definicao: normalizar({} as Definicao), nome: '', descricao: '', tipo: 'GERAL' });
  protected readonly passado = signal<Estado[]>([]);
  protected readonly futuro = signal<Estado[]>([]);
  private readonly salvo = signal<Estado | null>(null);
  protected readonly lado = signal<Lado>(null);
  protected readonly validacao = signal<Validacao | null>(null);
  protected readonly verificadorAberto = signal(false);
  protected readonly ocupado = signal<string | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly arrastandoPaleta = signal(false);
  protected readonly foco = signal<{ id: string; n: number } | null>(null);
  protected readonly execucaoAberta = signal(false);
  protected readonly execucaoTeste = signal(true);
  protected readonly erroExecucao = signal<string | null>(null);
  private readonly validar$ = new Subject<Estado>();

  protected readonly moverNo = moverNo;

  protected readonly especs = computed(() => (this.catalogo() ? mapaEspecs(this.catalogo()!) : new Map<string, NoEspec>()));
  protected readonly sujo = computed(() => !!this.salvo() && !iguais(this.estado(), this.salvo()));
  protected readonly selecionado = computed(() => { const l = this.lado(); return l?.tipo === 'config' ? l.id : null; });
  protected readonly erros = computed(() => (this.validacao()?.problemas ?? []).filter((p) => p.severidade === 'error').length + (this.validacao()?.erroEstrutura ? 1 : 0));
  protected readonly avisos = computed(() => (this.validacao()?.problemas ?? []).filter((p) => p.severidade === 'warning').length);
  protected readonly problemasTipo = computed(() => {
    const v = this.validacao();
    if (!v) return [];
    return v.ativacao.filter((a) => !v.problemas.some((p) => a.endsWith(p.mensagem)));
  });
  protected readonly problemasPorNo = computed(() => {
    const m = new Map<string, { erros: number; avisos: number }>();
    for (const p of this.validacao()?.problemas ?? []) {
      if (!p.noId) continue;
      const e = m.get(p.noId) ?? { erros: 0, avisos: 0 };
      if (p.severidade === 'error') e.erros++;
      else e.avisos++;
      m.set(p.noId, e);
    }
    return m;
  });
  protected readonly statusRotulo = computed(() => STATUS_ROTULO[this.detalhe()?.resumo.status ?? 'RASCUNHO']);
  protected readonly tomStatus = computed(() => ({ ATIVA: 'success', PAUSADA: 'warning', RASCUNHO: 'neutral', ARQUIVADA: 'neutral' } as const)[this.detalhe()?.resumo.status ?? 'RASCUNHO']);

  constructor() {
    this.validar$.pipe(
      debounceTime(400),
      switchMap((e) => this.service.validar(e.definicao, e.tipo).pipe(catchError(() => of(null)))),
      takeUntilDestroyed()
    ).subscribe((v) => v && this.validacao.set(v));
    effect(() => this.validar$.next(this.estado()));
    this.carregar();
  }

  protected carregar(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.erroCarga.set(null);
    forkJoin([this.service.buscar(id), this.service.catalogo()]).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ([d, c]) => {
        this.catalogo.set(c);
        this.aplicarDetalhe(d, true);
        if (!d.definicao.steps?.length) this.lado.set({ tipo: 'config', id: 'trigger' });
      },
      error: (e) => this.erroCarga.set(getErrorMessage(e))
    });
  }

  private aplicarDetalhe(d: AutomacaoDetalhe, resetar: boolean): void {
    this.detalhe.set(d);
    const e: Estado = { definicao: normalizar(d.definicao), nome: d.resumo.nome, descricao: d.resumo.descricao ?? '', tipo: d.resumo.tipo };
    if (resetar) {
      this.estado.set(e);
      this.passado.set([]);
      this.futuro.set([]);
    }
    this.salvo.set(resetar ? e : this.estado());
  }

  // ------------------------------------------------------------------------ histórico

  protected commit(patch: Partial<Estado>): void {
    const atual = this.estado();
    const prox = { ...atual, ...patch };
    if (iguais(prox, atual)) return;
    this.passado.update((p) => [...p.slice(-LIMITE_HISTORICO), atual]);
    this.futuro.set([]);
    this.estado.set(prox);
  }

  protected definir(d: Definicao): void {
    this.commit({ definicao: d });
  }

  protected desfazer(): void {
    const p = this.passado();
    if (!p.length) return;
    this.futuro.update((f) => [this.estado(), ...f]);
    this.estado.set(p[p.length - 1]!);
    this.passado.set(p.slice(0, -1));
  }

  protected refazer(): void {
    const f = this.futuro();
    if (!f.length) return;
    this.passado.update((p) => [...p, this.estado()]);
    this.estado.set(f[0]!);
    this.futuro.set(f.slice(1));
  }

  // ------------------------------------------------------------------------ edição

  protected alternarLado(l: Lado): void {
    this.lado.set(this.lado()?.tipo === l?.tipo ? null : l);
  }

  protected escolherDoPainel(espec: NoEspec): void {
    const l = this.lado();
    if (l?.tipo !== 'adicionar') return;
    if (l.modo === 'gatilho') {
      this.definir({ ...this.estado().definicao, trigger: { type: espec.tipo, params: parametrosPadrao(espec) } });
      this.lado.set({ tipo: 'config', id: 'trigger' });
      return;
    }
    this.inserir(l.alvo ?? { parentId: null, slot: 'steps', index: this.estado().definicao.steps.length }, espec);
  }

  protected soltarNovo(alvo: InsertTarget, tipo: string): void {
    this.arrastandoPaleta.set(false);
    const espec = this.especs().get(tipo);
    if (espec) this.inserir(alvo, espec);
  }

  private inserir(alvo: InsertTarget, espec: NoEspec): void {
    const no: FlowNode = novoNo(espec, todosIds(this.estado().definicao));
    this.definir(inserirNo(this.estado().definicao, alvo, no));
    this.lado.set({ tipo: 'config', id: no.id });
    this.foco.set({ id: no.id, n: Date.now() });
  }

  protected mudarNo(no: FlowNode): void {
    this.definir(atualizarNo(this.estado().definicao, no.id, () => no));
  }

  protected mudarGatilho(params: Params): void {
    const d = this.estado().definicao;
    this.definir({ ...d, trigger: { ...d.trigger, params } });
  }

  protected posicionar(id: string, x: number, y: number): void {
    const d = this.estado().definicao;
    this.definir({ ...d, layout: { ...(d.layout ?? {}), [id]: { x, y } } });
  }

  protected excluir(id: string): void {
    this.definir(removerNo(this.estado().definicao, id));
    this.lado.set(null);
  }

  protected duplicar(id: string): void {
    const r = duplicarNo(this.estado().definicao, id);
    this.definir(r.definicao);
    if (r.id) this.lado.set({ tipo: 'config', id: r.id });
  }

  protected irPara(id: string | null): void {
    if (!id) return;
    this.verificadorAberto.set(false);
    this.lado.set({ tipo: 'config', id });
    this.foco.set({ id, n: Date.now() });
  }

  protected nomeNo(id: string | null): string {
    if (!id || id === 'trigger') return 'Quando começa';
    const no = buscarNo(this.estado().definicao, id);
    return no?.label || this.especs().get(no?.type ?? '')?.rotulo || id;
  }

  protected aplicarRascunho(r: Rascunho): void {
    // O ajuste já foi salvo como nova versão: entra no canvas como uma alteração desfazível.
    const novo: Estado = { ...this.estado(), definicao: normalizar(r.definicao), nome: r.nome, tipo: r.tipo };
    this.commit(novo);
    this.salvo.set(novo);
    this.service.buscar(this.detalhe()!.resumo.id).subscribe({ next: (d) => this.detalhe.set(d), error: () => undefined });
  }

  // ------------------------------------------------------------------------ servidor

  protected salvar(): Promise<boolean> {
    const e = this.estado();
    this.ocupado.set('salvar');
    this.erro.set(null);
    return new Promise((ok) => {
      this.service.salvar(this.detalhe()!.resumo.id, { nome: e.nome.trim() || this.detalhe()!.resumo.nome, descricao: e.descricao || null, tipo: e.tipo, definicao: e.definicao })
        .subscribe({
          next: (d) => {
            this.ocupado.set(null);
            this.detalhe.set(d);
            this.salvo.set(e);
            ok(true);
          },
          error: (err) => {
            this.ocupado.set(null);
            this.erro.set(getErrorMessage(err));
            ok(false);
          }
        });
    });
  }

  protected async mudarStatus(status: 'ATIVA' | 'PAUSADA'): Promise<void> {
    if (this.sujo() && !(await this.salvar())) return;
    this.ocupado.set('status');
    this.service.alterarStatus(this.detalhe()!.resumo.id, status).subscribe({
      next: (d) => {
        this.ocupado.set(null);
        this.detalhe.set(d);
        this.notificacao.success(status === 'ATIVA' ? 'Automação ativada.' : 'Automação pausada.');
      },
      error: (e) => {
        this.ocupado.set(null);
        this.erro.set(getErrorMessage(e));
      }
    });
  }

  protected abrirExecucao(teste: boolean): void {
    this.execucaoTeste.set(teste);
    this.erroExecucao.set(null);
    this.execucaoAberta.set(true);
  }

  protected async executar(inputs: Record<string, JsonValue>): Promise<void> {
    if (this.sujo() && !(await this.salvar())) return;
    this.ocupado.set('executar');
    this.service.executar(this.detalhe()!.resumo.id, inputs, this.execucaoTeste()).subscribe({
      next: (r) => {
        this.ocupado.set(null);
        this.execucaoAberta.set(false);
        void this.router.navigate(['/notificacoes/automacoes', this.detalhe()!.resumo.id, 'execucoes', r.id]);
      },
      error: (e) => {
        this.ocupado.set(null);
        this.erroExecucao.set(getErrorMessage(e));
      }
    });
  }

  // ------------------------------------------------------------------------ teclado

  @HostListener('window:keydown', ['$event'])
  protected tecla(e: KeyboardEvent): void {
    const mod = e.ctrlKey || e.metaKey;
    if (mod && e.key.toLowerCase() === 's') {
      e.preventDefault();
      if (this.sujo()) void this.salvar();
      return;
    }
    const alvo = e.target as HTMLElement | null;
    if (alvo && (alvo.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(alvo.tagName))) return;
    if (mod && e.key.toLowerCase() === 'z' && !e.shiftKey) {
      e.preventDefault();
      this.desfazer();
    } else if (mod && (e.key.toLowerCase() === 'y' || (e.key.toLowerCase() === 'z' && e.shiftKey))) {
      e.preventDefault();
      this.refazer();
    } else if ((e.key === 'Delete') && this.selecionado() && this.selecionado() !== 'trigger') {
      e.preventDefault();
      this.excluir(this.selecionado()!);
    } else if (e.key === 'Escape') {
      this.lado.set(null);
      this.verificadorAberto.set(false);
    }
  }

  @HostListener('window:beforeunload', ['$event'])
  protected antesDeSair(e: BeforeUnloadEvent): void {
    if (this.sujo()) e.preventDefault();
  }
}
