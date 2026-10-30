import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { CesAsyncStateComponent, CesConfirmationService, CesPageHeaderComponent, NotificationService, CesButtonComponent, CesIconButtonComponent, CesStatusComponent, CesCalloutComponent } from '@ces/ui';

import {
  AutomacaoDetalhe, Catalogo, ExecucaoResumo, JsonValue, MODO_ROTULO, ModoExecucao, NoEspec, STATUS_EXECUCAO_ROTULO, STATUS_ROTULO,
  StatusExecucao, TIPO_ROTULO
} from '../../../core/models/automacao.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AutomacoesService } from '../../../core/services/automacoes.service';
import { CanvasComponent, formatarDuracao } from './designer/canvas.component';
import { DialogoExecutarComponent } from './designer/dialogo-executar.component';
import { normalizar } from './lib/arvore';
import { mapaEspecs } from './lib/dinamico';

@Component({
  selector: 'app-automacao-detalhe',
  standalone: true,
  imports: [CesStatusComponent, CesCalloutComponent, CesButtonComponent, CesIconButtonComponent, DatePipe, CesPageHeaderComponent, CesAsyncStateComponent, CanvasComponent, DialogoExecutarComponent],
  styleUrls: ['./automacoes.css', './automacoes-canvas.css', './automacoes-campos.css', './automacoes-email.css', './automacoes-execucao.css'],
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="aut aut-page">
      @if (!detalhe() || !catalogo()) {
        @if (erro()) {
          <ces-async-state state="error" title="Não foi possível carregar" [description]="erro()!" actionLabel="Tentar de novo" (action)="carregar()" />
        } @else { <ces-async-state state="loading" title="Carregando automação" /> }
      } @else {
        @let a = detalhe()!.resumo;
        <ces-page-header [title]="a.nome" icon="pi pi-sitemap" [description]="a.descricao ?? undefined"
                         [breadcrumb]="[{ label: 'Automações', route: '/notificacoes/automacoes' }, { label: a.nome }]">
          <div status style="display:flex;gap:0.375rem;flex-wrap:wrap">
            <ces-status [tone]="$any(a.status === 'ATIVA' ? 'success' : a.status === 'PAUSADA' ? 'warning' : null) ?? 'neutral'">{{ statusRotulo(a.status) }}</ces-status>
            <ces-status tone="neutral">{{ tipoRotulo(a.tipo) }}</ces-status>
            @if (a.origem === 'REGRA_CONVERTIDA') { <ces-status tone="info">Convertida de regra de notificação</ces-status> }
            @if (a.origem === 'COPILOTO') { <ces-status tone="brand">Montada pelo Copiloto</ces-status> }
          </div>
          <div actions style="display:flex;gap:0.5rem;flex-wrap:wrap">
            <ces-button icon="pi pi-pencil" label="Abrir no editor" variant="primary" size="sm" (pressed)="ir(['/notificacoes/automacoes', a.id, 'editar'])"></ces-button>
            <ces-button icon="pi pi-bolt" label="Testar" variant="outline" size="sm" (pressed)="abrir(true)"></ces-button>
            @if (podeExecutar()) {
              <ces-button icon="pi pi-play" label="Executar agora" variant="outline" size="sm" (pressed)="abrir(false)"></ces-button>
            }
            @if (a.status === 'ATIVA') {
              <ces-button icon="pi pi-pause" label="Pausar" variant="outline" size="sm" [disabled]="ocupado()" (pressed)="status('PAUSADA')"></ces-button>
            } @else {
              <ces-button icon="pi pi-check" label="Ativar" variant="outline" size="sm" [disabled]="ocupado() || detalhe()!.problemasAtivacao.length > 0" (pressed)="status('ATIVA')"></ces-button>
            }
            <ces-icon-button icon="pi pi-copy" label="Duplicar" variant="ghost" size="icon-sm" (pressed)="duplicar()"></ces-icon-button>
            <ces-icon-button icon="pi pi-trash" label="Arquivar" variant="destructive" size="icon-sm" (pressed)="arquivar()"></ces-icon-button>
          </div>
        </ces-page-header>

        @if (detalhe()!.problemasAtivacao.length && a.status !== 'ATIVA') {
          <ces-callout tone="warning" [dense]="true"><span>Para ativar, corrija: {{ detalhe()!.problemasAtivacao.slice(0, 3).join('; ') }}{{ detalhe()!.problemasAtivacao.length > 3 ? '…' : '' }}</span>
          </ces-callout>
        }

        <div class="aut-kpis">
          <div class="aut-kpi"><span>Quando</span><strong>{{ a.gatilhoRotulo }}</strong><span>{{ a.gatilhoResumo }}</span></div>
          <div class="aut-kpi"><span>Próxima execução</span><strong>{{ a.proximaExecucao ? (a.proximaExecucao | date: 'dd/MM/yyyy HH:mm') : '—' }}</strong></div>
          <div class="aut-kpi"><span>Execuções em 30 dias</span><strong>{{ a.execucoes30Dias }}</strong><span>{{ a.falhas30Dias }} com falha</span></div>
          <div class="aut-kpi"><span>Responsável</span><strong>{{ a.donoNome || '—' }}</strong><span>Lê dados com as permissões desta pessoa</span></div>
          <div class="aut-kpi"><span>Versão</span><strong>{{ a.numeroVersao }}</strong><span>{{ a.dataUltimaAlteracao | date: 'dd/MM/yyyy HH:mm' }}</span></div>
        </div>

        <div class="aut-card" style="height:26rem;display:flex">
          <aut-canvas [definicao]="definicao()" [catalogo]="catalogo()!" [especs]="especs()" [somenteLeitura]="true"
                      (selecionar)="irEditor()" />
        </div>

        <section class="aut-card">
          <div style="display:flex;align-items:center;padding:0.875rem 1rem;border-bottom:1px solid var(--ces-border)">
            <strong style="flex:1">Histórico de execuções</strong>
            <ces-button icon="pi pi-refresh" label="Atualizar" variant="ghost" size="sm" (pressed)="carregarExecucoes()"></ces-button>
          </div>
          @if (!execucoes().length) {
            <p class="aut-muted" style="padding:1rem;margin:0">Ainda não rodou. Use "Testar" para ver o fluxo funcionando sem incomodar ninguém.</p>
          } @else {
            <div style="overflow-x:auto">
              <table class="aut-table">
                <thead><tr><th>Quando</th><th>Situação</th><th>Modo</th><th>Duração</th><th>Passos</th><th>Detalhe</th></tr></thead>
                <tbody>
                  @for (e of execucoes(); track e.id) {
                    <tr data-clickable tabindex="0" (click)="abrirExecucao(e)" (keydown.enter)="abrirExecucao(e)">
                      <td>{{ e.criadaEm | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                      <td><span class="aut-list-run" [attr.data-status]="e.status"><span class="aut-dot"></span>{{ rotuloExecucao(e.status) }}</span></td>
                      <td>{{ rotuloModo(e.modo) }}{{ e.iniciadaPor ? ' · ' + e.iniciadaPor : '' }}</td>
                      <td>{{ duracao(e.duracaoMs) }}</td>
                      <td>{{ e.passos }}</td>
                      <td class="aut-muted" style="max-width:24rem">{{ e.erro || e.mensagem || (e.status === 'AGUARDANDO' ? 'Aguardando ' + (e.aguardando === 'aprovacao' ? 'aprovação' : e.aguardando ?? '') : '') }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>

        <section class="aut-card" style="padding:1rem">
          <strong>Versões</strong>
          <ul class="aut-outputs" style="margin-top:0.75rem">
            @for (v of detalhe()!.versoes; track v.numero) {
              <li><span><strong style="font-weight:600">Versão {{ v.numero }}</strong> · {{ v.data | date: 'dd/MM/yyyy HH:mm' }} · {{ v.autor }}</span><span class="aut-muted">{{ v.nota }}</span></li>
            }
          </ul>
        </section>

        <aut-dialogo-executar [(aberto)]="dialogo" [teste]="teste()" [definicao]="definicao()" [especs]="especs()" [ocupado]="ocupado()"
                              [erro]="erroExecucao()" (executar)="executar($event)" />
      }
    </div>
  `
})
export class AutomacaoDetalheComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected ir(destino: unknown[]): void {
    void this.router.navigate(destino);
  }
  private readonly service = inject(AutomacoesService);
  private readonly notificacao = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly detalhe = signal<AutomacaoDetalhe | null>(null);
  protected readonly catalogo = signal<Catalogo | null>(null);
  protected readonly execucoes = signal<ExecucaoResumo[]>([]);
  protected readonly erro = signal<string | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly dialogo = signal(false);
  protected readonly teste = signal(true);
  protected readonly erroExecucao = signal<string | null>(null);
  protected readonly duracao = formatarDuracao;

  protected readonly definicao = computed(() => normalizar(this.detalhe()!.definicao));
  protected readonly especs = computed(() => (this.catalogo() ? mapaEspecs(this.catalogo()!) : new Map<string, NoEspec>()));
  protected readonly podeExecutar = computed(() => {
    const d = this.detalhe();
    return !!d && (d.definicao.trigger.type === 'trigger.manual' || d.resumo.status === 'ATIVA');
  });

  private get id(): string {
    return this.route.snapshot.paramMap.get('id')!;
  }

  constructor() {
    this.carregar();
  }

  protected carregar(): void {
    this.erro.set(null);
    forkJoin([this.service.buscar(this.id), this.service.catalogo()]).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ([d, c]) => {
        this.catalogo.set(c);
        this.detalhe.set(d);
        this.carregarExecucoes();
      },
      error: (e) => this.erro.set(getErrorMessage(e))
    });
  }

  protected carregarExecucoes(): void {
    this.service.execucoes(this.id).subscribe({ next: (p) => this.execucoes.set(p.content), error: () => undefined });
  }

  protected statusRotulo(s: AutomacaoDetalhe['resumo']['status']): string { return STATUS_ROTULO[s]; }
  protected tipoRotulo(t: AutomacaoDetalhe['resumo']['tipo']): string { return TIPO_ROTULO[t]; }
  protected rotuloExecucao(s: StatusExecucao): string { return STATUS_EXECUCAO_ROTULO[s]; }
  protected rotuloModo(m: ModoExecucao): string { return MODO_ROTULO[m]; }

  protected irEditor(): void {
    void this.router.navigate(['/notificacoes/automacoes', this.id, 'editar']);
  }

  protected abrirExecucao(e: ExecucaoResumo): void {
    void this.router.navigate(['/notificacoes/automacoes', this.id, 'execucoes', e.id]);
  }

  protected abrir(teste: boolean): void {
    this.teste.set(teste);
    this.erroExecucao.set(null);
    this.dialogo.set(true);
  }

  protected executar(inputs: Record<string, JsonValue>): void {
    this.ocupado.set(true);
    this.service.executar(this.id, inputs, this.teste()).subscribe({
      next: (r) => {
        this.ocupado.set(false);
        this.dialogo.set(false);
        void this.router.navigate(['/notificacoes/automacoes', this.id, 'execucoes', r.id]);
      },
      error: (e) => {
        this.ocupado.set(false);
        this.erroExecucao.set(getErrorMessage(e));
      }
    });
  }

  protected status(s: 'ATIVA' | 'PAUSADA'): void {
    this.ocupado.set(true);
    this.service.alterarStatus(this.id, s).subscribe({
      next: (d) => {
        this.ocupado.set(false);
        this.detalhe.set(d);
        this.notificacao.success(s === 'ATIVA' ? 'Automação ativada.' : 'Automação pausada.');
      },
      error: (e) => {
        this.ocupado.set(false);
        this.notificacao.error(getErrorMessage(e));
      }
    });
  }

  protected duplicar(): void {
    this.service.duplicar(this.id).subscribe({
      next: (d) => void this.router.navigate(['/notificacoes/automacoes', d.resumo.id, 'editar']),
      error: (e) => this.notificacao.error(getErrorMessage(e))
    });
  }

  protected async arquivar(): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: 'Arquivar automação',
      message: 'Ela para de rodar e sai da lista. O histórico de execuções é mantido.',
      confirmLabel: 'Arquivar',
      variant: 'destructive'
    });
    if (!ok) return;
    this.service.arquivar(this.id).subscribe({
      next: () => {
        this.notificacao.success('Automação arquivada.');
        void this.router.navigate(['/notificacoes/automacoes']);
      },
      error: (e) => this.notificacao.error(getErrorMessage(e))
    });
  }
}
