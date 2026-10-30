import { DatePipe, JsonPipe, NgTemplateOutlet } from '@angular/common';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ChangeDetectionStrategy, Component, DestroyRef, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { CesAsyncStateComponent, NotificationService, CesButtonComponent, CesIconButtonComponent, CesStatusComponent, CesCalloutComponent } from '@ces/ui';

import {
  Catalogo, Etapa, ExecucaoDetalhe, JsonValue, MODO_ROTULO, NoEspec, STATUS_ETAPA_ROTULO, STATUS_EXECUCAO_ROTULO
} from '../../../core/models/automacao.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AutomacoesService } from '../../../core/services/automacoes.service';
import { CanvasComponent, EstadoExecucaoNo, formatarDuracao } from './designer/canvas.component';
import { buscarNo, normalizar } from './lib/arvore';
import { icone, mapaEspecs, nomeBonito } from './lib/dinamico';

/** Chaves que aparecem fora do catálogo (contexto do gatilho, papéis). */
const ROTULOS_COMUNS: Record<string, string> = {
  ano: 'Ano', mes: 'Mês', data: 'Data', hora: 'Hora', semana: 'Semana', dia_semana: 'Dia da semana', papeis: 'Pessoas do evento',
  id_referencia: 'Id de referência'
};

@Component({
  selector: 'app-automacao-execucao',
  standalone: true,
  imports: [CesStatusComponent, CesCalloutComponent, CesButtonComponent, CesIconButtonComponent, RouterLink, DatePipe, JsonPipe, NgTemplateOutlet, CesAsyncStateComponent, CanvasComponent],
  styleUrls: ['./automacoes.css', './automacoes-canvas.css', './automacoes-campos.css', './automacoes-email.css', './automacoes-execucao.css'],
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!execucao() || !catalogo()) {
      <div class="aut" style="padding:2rem">
        @if (erro()) { <ces-async-state state="error" title="Não foi possível carregar a execução" [description]="erro()!" actionLabel="Tentar de novo" (action)="carregar()" /> }
        @else { <ces-async-state state="loading" title="Carregando execução" /> }
      </div>
    } @else {
      @let e = execucao()!;
      <div class="aut aut-designer">
        <header class="aut-topbar">
          <ces-icon-button icon="pi pi-arrow-left" label="Voltar" variant="ghost" size="icon-sm" (pressed)="ir(['/notificacoes/automacoes', e.resumo.automacaoId])"></ces-icon-button>
          <div class="aut-topbar-title">
            <strong style="padding-left:0.375rem">{{ e.automacaoNome }}</strong>
            <span class="aut-topbar-meta">
              <span class="aut-list-run" [attr.data-status]="e.resumo.status"><span class="aut-dot"></span>{{ rotuloExecucao(e.resumo.status) }}</span>
              <span class="aut-muted">{{ rotuloModo(e.resumo.modo) }} · {{ e.resumo.criadaEm | date: 'dd/MM/yyyy HH:mm:ss' }} · {{ duracao(e.resumo.duracaoMs) }} · versão {{ e.resumo.numeroVersao }}</span>
            </span>
          </div>
          <span style="flex:1"></span>
          @if (ativa()) { <ces-status tone="warning" icon="pi pi-spin pi-spinner">Atualizando</ces-status> }
          @if (!final()) {
            <ces-button icon="pi pi-stop-circle" label="Cancelar" variant="destructive" size="sm" (pressed)="cancelar()"></ces-button>
          }
          <ces-button icon="pi pi-replay" label="Reenviar" variant="outline" size="sm" (pressed)="reenviar()" title="Roda de novo com os mesmos dados do gatilho, na versão atual"></ces-button>
          <ces-button icon="pi pi-pencil" label="Editor" variant="primary" size="sm" (pressed)="ir(['/notificacoes/automacoes', e.resumo.automacaoId, 'editar'])"></ces-button>
        </header>
        @if (e.resumo.erro) { <ces-callout tone="danger" [dense]="true" role="alert">{{ e.resumo.erro }}</ces-callout> }
        @else if (e.resumo.status === 'AGUARDANDO') {
          <ces-callout tone="warning" [dense]="true">{{ e.resumo.aguardando === 'aprovacao' ? 'Aguardando a resposta de uma aprovação.' : 'Aguardando para continuar' }}
            @if (e.resumo.retomarEm) { — retoma em {{ e.resumo.retomarEm | date: 'dd/MM HH:mm' }} }
          </ces-callout>
        } @else if (e.resumo.status === 'SUCESSO') {
          <ces-callout tone="success" [dense]="true">Execução concluída. Clique num passo para ver o que entrou e o que saiu.</ces-callout>
        }
        <div class="aut-workspace">
          <aut-canvas [definicao]="definicao()" [catalogo]="catalogo()!" [especs]="especs()" [somenteLeitura]="true"
                      [execucao]="estados()" [selecionado]="selecionado()" (selecionar)="selecionado.set($event)" />
          <aside class="aut-panel" aria-label="Detalhes do passo">
            @if (selecionado() === 'trigger' || !selecionado()) {
              <header class="aut-panel-head" data-group="trigger">
                <span class="aut-node-icon"><i class="pi pi-bolt" aria-hidden="true"></i></span>
                <span class="aut-panel-title" style="flex:1">Dados do início</span>
              </header>
              <div class="aut-panel-body">
                <p class="aut-muted" style="margin:0">O que o gatilho entregou aos passos (fica congelado: reenviar usa os mesmos dados).</p>
                <ng-container *ngTemplateOutlet="dados; context: { $implicit: e.saidaGatilho, modo: 'gatilho' }" />
                @if (e.arquivos.length) {
                  <span class="aut-section-title">Arquivos gerados</span>
                  @for (f of e.arquivos; track f.id) {
                    <ces-button icon="pi pi-file-pdf" [label]="f.nome" variant="outline" size="sm" style="justify-content:flex-start" (pressed)="baixar(f.id, f.nome)"></ces-button>
                  }
                }
              </div>
            } @else {
              @let n = noSelecionado();
              <header class="aut-panel-head" [attr.data-group]="espec()?.grupo">
                <span class="aut-node-icon"><i [class]="icone(espec()?.icone)" aria-hidden="true"></i></span>
                <div style="display:flex;flex-direction:column;flex:1;min-width:0">
                  <span class="aut-panel-title">{{ n?.label || espec()?.rotulo }}</span>
                  <span class="aut-muted">{{ espec()?.rotulo }}</span>
                </div>
                <ces-icon-button icon="pi pi-times" label="Fechar" variant="ghost" size="icon-sm" (pressed)="selecionado.set(null)"></ces-icon-button>
              </header>
              <div class="aut-panel-body">
                @if (!etapasSelecionadas().length) { <p class="aut-muted">Este passo não rodou nesta execução.</p> }
                @if (etapasSelecionadas().length > 1) {
                  <div class="aut-toggles" role="group" aria-label="Iteração">
                    @for (et of etapasSelecionadas(); track et.iteracao; let i = $index) {
                      <button type="button" class="aut-toggle" [attr.aria-pressed]="iteracao() === i" (click)="iteracao.set(i)">{{ i + 1 }}</button>
                    }
                  </div>
                }
                @if (etapaAtual(); as et) {
                  <dl class="aut-datalist">
                    <div><dt>Situação</dt><dd>{{ rotuloEtapa(et.status) }}{{ et.tentativa > 1 ? ' (' + et.tentativa + ' tentativas)' : '' }}</dd></div>
                    <div><dt>Início</dt><dd>{{ et.inicio | date: 'dd/MM HH:mm:ss' }}</dd></div>
                    <div><dt>Duração</dt><dd>{{ duracaoEtapa(et) }}</dd></div>
                    @if (et.proximaEm) { <div><dt>Retoma em</dt><dd>{{ et.proximaEm | date: 'dd/MM HH:mm' }}</dd></div> }
                  </dl>
                  @if (et.erro) { <ces-callout tone="danger" [dense]="true" style="margin:0">{{ et.erro }}</ces-callout> }
                  @if (aprovacao(); as ap) {
                    <ces-callout tone="info" [dense]="true">
                      <strong>Aprovação: {{ ap.titulo }}</strong>
                      <span>{{ ap.status === 'PENDENTE' ? 'Aguardando resposta' : ap.status === 'DECIDIDA' ? ap.resposta + ' por ' + ap.respondidaPor : ap.status }}</span>
                      @if (ap.podeResponder) { <a class="aut-link" [routerLink]="['/notificacoes/automacoes/aprovacoes', ap.id]">Responder</a> }
                    </ces-callout>
                  }
                  @if (htmlEmail(et); as h) {
                    <span class="aut-section-title">E-mail montado</span>
                    <iframe [srcdoc]="h" title="Prévia do e-mail" sandbox="" style="width:100%;height:24rem;border:1px solid var(--ces-border);border-radius:10px;background:#fff"></iframe>
                  }
                  <span class="aut-section-title">Entradas</span>
                  <ng-container *ngTemplateOutlet="dados; context: { $implicit: et.entradas, modo: 'entrada' }" />
                  <span class="aut-section-title">Saídas</span>
                  <ng-container *ngTemplateOutlet="dados; context: { $implicit: semHtml(et.saidas), modo: 'saida' }" />
                }
              </div>
            }
          </aside>
        </div>
      </div>
    }

    <ng-template #dados let-valor let-modo="modo">
      @if (!valor || !chaves(valor).length) { <p class="aut-muted" style="margin:0">—</p> }
      @else {
        <dl class="aut-datalist">
          @for (k of chaves(valor); track k) {
            <div><dt>{{ rotulo(k, modo) }}</dt><dd>{{ simples(valor[k]) }}</dd></div>
          }
        </dl>
        <details><summary class="aut-link">Ver dados técnicos</summary><pre class="aut-json">{{ valor | json }}</pre></details>
      }
    </ng-template>
  `
})
export class AutomacaoExecucaoComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected ir(destino: unknown[]): void {
    void this.router.navigate(destino);
  }
  private readonly service = inject(AutomacoesService);
  private readonly notificacao = inject(NotificationService);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly execucao = signal<ExecucaoDetalhe | null>(null);
  protected readonly catalogo = signal<Catalogo | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly selecionado = signal<string | null>(null);
  protected readonly iteracao = signal(0);
  protected readonly duracao = formatarDuracao;
  protected readonly icone = icone;
  private temporizador: ReturnType<typeof setTimeout> | null = null;

  protected readonly definicao = computed(() => normalizar(this.execucao()!.definicao));
  protected readonly especs = computed(() => (this.catalogo() ? mapaEspecs(this.catalogo()!) : new Map<string, NoEspec>()));
  protected readonly final = computed(() => ['SUCESSO', 'FALHA', 'CANCELADA', 'TEMPO_ESGOTADO'].includes(this.execucao()?.resumo.status ?? ''));
  protected readonly ativa = computed(() => ['NA_FILA', 'EXECUTANDO'].includes(this.execucao()?.resumo.status ?? ''));

  protected readonly estados = computed(() => {
    const m = new Map<string, EstadoExecucaoNo>();
    for (const et of this.execucao()?.etapas ?? []) {
      const atual = m.get(et.noId);
      const ms = et.inicio && et.fim ? new Date(et.fim).getTime() - new Date(et.inicio).getTime() : null;
      if (!atual) {
        m.set(et.noId, { status: et.status, duracaoMs: ms, iteracoes: 1 });
      } else {
        atual.iteracoes++;
        if (et.status === 'FALHA' || et.status === 'TEMPO_ESGOTADO') atual.status = et.status;
        atual.duracaoMs = (atual.duracaoMs ?? 0) + (ms ?? 0);
      }
    }
    return m;
  });

  protected readonly noSelecionado = computed(() => (this.selecionado() ? buscarNo(this.definicao(), this.selecionado()!) : undefined));
  protected readonly espec = computed(() => this.especs().get(this.noSelecionado()?.type ?? ''));
  protected readonly etapasSelecionadas = computed(() => (this.execucao()?.etapas ?? []).filter((e) => e.noId === this.selecionado()));
  protected readonly etapaAtual = computed(() => this.etapasSelecionadas()[Math.min(this.iteracao(), this.etapasSelecionadas().length - 1)] ?? null);
  protected readonly aprovacao = computed(() =>
    this.noSelecionado()?.type === 'approval.request' ? (this.execucao()?.aprovacoes ?? [])[0] ?? null : null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.temporizador && clearTimeout(this.temporizador));
    this.carregar();
  }

  protected carregar(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    const execucaoId = this.route.snapshot.paramMap.get('execucaoId')!;
    forkJoin([this.service.execucao(id, execucaoId), this.service.catalogo()]).subscribe({
      next: ([e, c]) => {
        this.catalogo.set(c);
        this.execucao.set(e);
        if (!this.final()) this.temporizador = setTimeout(() => this.carregar(), this.ativa() ? 1500 : 6000);
      },
      error: (err) => this.erro.set(getErrorMessage(err))
    });
  }

  protected rotuloExecucao(s: keyof typeof STATUS_EXECUCAO_ROTULO): string { return STATUS_EXECUCAO_ROTULO[s]; }
  protected rotuloModo(m: keyof typeof MODO_ROTULO): string { return MODO_ROTULO[m]; }
  protected rotuloEtapa(s: keyof typeof STATUS_ETAPA_ROTULO): string { return STATUS_ETAPA_ROTULO[s]; }
  /** Rótulo do catálogo (o mesmo do editor); sem ele, a chave em forma legível. */
  protected rotulo(k: string, modo: 'gatilho' | 'entrada' | 'saida'): string {
    if (modo === 'entrada') {
      const p = this.espec()?.params.find((x) => x.chave === k);
      if (p) return p.rotulo;
    } else {
      const espec = modo === 'gatilho' ? this.especs().get(this.definicao().trigger.type) : this.espec();
      const saida = espec?.saidas.find((x) => x.chave === k);
      if (saida) return saida.rotulo;
    }
    return ROTULOS_COMUNS[k] ?? nomeBonito(k);
  }

  protected duracaoEtapa(et: Etapa): string {
    if (!et.inicio || !et.fim) return '—';
    return formatarDuracao(new Date(et.fim).getTime() - new Date(et.inicio).getTime());
  }

  protected chaves(v: Record<string, JsonValue> | null): string[] {
    return v ? Object.keys(v).filter((k) => k !== 'decision') : [];
  }

  protected simples(v: JsonValue): string {
    if (v === null || v === undefined) return '—';
    if (typeof v === 'boolean') return v ? 'Sim' : 'Não';
    if (typeof v === 'string') return v.length > 600 ? `${v.slice(0, 600)}…` : v;
    if (typeof v === 'number') return String(v);
    if (Array.isArray(v)) return `${v.length} item(ns)`;
    return Object.entries(v).slice(0, 6).map(([k, x]) => `${nomeBonito(k)}: ${typeof x === 'object' && x !== null ? '…' : x}`).join(' · ');
  }

  /** O HTML vem do servidor (montado pelo próprio Nexo) e abre num iframe sem scripts. */
  protected htmlEmail(et: Etapa): SafeHtml | null {
    const h = et.saidas?.['html'];
    return et.tipo === 'email.send' && typeof h === 'string' ? this.sanitizer.bypassSecurityTrustHtml(h) : null;
  }

  protected semHtml(v: Record<string, JsonValue> | null): Record<string, JsonValue> | null {
    if (!v) return v;
    const { html, ...resto } = v;
    return html !== undefined && typeof html === 'string' && html.length > 300 ? resto : v;
  }

  protected baixar(id: string, nome: string): void {
    this.service.baixarArquivo(id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = nome;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => this.notificacao.error(getErrorMessage(e))
    });
  }

  protected cancelar(): void {
    const e = this.execucao()!;
    this.service.cancelar(e.resumo.automacaoId, e.resumo.id).subscribe({
      next: () => {
        this.notificacao.info('Cancelamento pedido.');
        this.carregar();
      },
      error: (err) => this.notificacao.error(getErrorMessage(err))
    });
  }

  protected reenviar(): void {
    const e = this.execucao()!;
    this.service.reenviar(e.resumo.automacaoId, e.resumo.id).subscribe({
      next: (r) => {
        if (this.temporizador) clearTimeout(this.temporizador);
        void this.router.navigate(['/notificacoes/automacoes', e.resumo.automacaoId, 'execucoes', r.id]).then(() => this.carregar());
      },
      error: (err) => this.notificacao.error(getErrorMessage(err))
    });
  }
}
