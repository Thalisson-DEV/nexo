import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesMeterComponent,
  CesPageHeaderComponent,
  CesStatusComponent,
  NotificationService
} from '@ces/ui';
import { take } from 'rxjs';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FotoTurno, ProgramacaoDoTurno, TurnoObraDetalhe } from '../../../core/models/obras-turnos.models';
import { ObrasTurnosService } from '../../../core/services/obras-turnos.service';
import { duracao } from '../../execucao/dashboards/execucao-dashboards.utils';
import { situacaoDoTurno } from '../../execucao/turno-list/turno-list.utils';
import { formatarMoeda } from '../obras-ui';
import { SITUACAO_PROGRAMACAO, formatarNumero } from '../programacao/programacao.logic';
import { ProgramacaoDetalheComponent } from '../programacao/programacao-detalhe.component';
import { AprDrawerComponent } from './apr-drawer.component';
import { LIBERACAO, TIPO_OCORRENCIA, cartoesDoTurno } from './turnos-obra.logic';

/**
 * O dia de uma equipe, na ordem em que ele acontece: para cada obra, primeiro a APR (ou o motivo de não executar),
 * depois o que foi executado. Equipe, ocorrências e relatório ficam ao lado. É tela de análise: tudo aqui é lançado
 * pela equipe em campo.
 */
@Component({
  selector: 'app-turno-obra-detalhe',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesMeterComponent,
    CesPageHeaderComponent,
    CesStatusComponent,
    AprDrawerComponent,
    ProgramacaoDetalheComponent
  ],
  template: `
    <div class="ces-page">
      @if (erro(); as mensagem) {
        <ces-page-header title="Turno" icon="pi pi-calendar-clock" [breadcrumb]="[{ label: 'Obras' }, { label: 'Turnos', route: '/obras/turnos' }, { label: 'Turno' }]"></ces-page-header>
        <ces-async-state state="error" title="Não foi possível abrir o turno" [description]="mensagem" actionLabel="Tentar novamente" actionIcon="pi pi-refresh" (action)="carregar()"></ces-async-state>
      } @else if (!t()) {
        <ces-page-header title="Turno" icon="pi pi-calendar-clock" [breadcrumb]="[{ label: 'Obras' }, { label: 'Turnos', route: '/obras/turnos' }, { label: 'Turno' }]"></ces-page-header>
        <ces-async-state state="loading" title="Abrindo o turno"></ces-async-state>
      } @else {
        @let turno = t()!;
        <ces-page-header [title]="'Turno de ' + turno.codigoEquipe" icon="pi pi-calendar-clock"
          [breadcrumb]="[{ label: 'Obras' }, { label: 'Turnos', route: '/obras/turnos' }, { label: turno.codigoEquipe }]" [favoritable]="false"
          [description]="subtitulo()">
          <ces-status status [tone]="situacao().tom" [icon]="situacao().icone.replace('pi ', '')">{{ situacao().rotulo }}</ces-status>
        </ces-page-header>

        @if (turno.encerradoAutomaticamente) {
          <ces-callout tone="warning" title="Encerrado pelo sistema" icon="pi pi-clock">{{ turno.motivoEncerramento }}</ces-callout>
        } @else if (turno.abertoDesdeOutroDia) {
          <ces-callout tone="warning" title="Aberto desde outro dia" icon="pi pi-exclamation-triangle">Quase sempre é turno esquecido pela equipe.</ces-callout>
        }

        <section class="td-resumo" aria-label="Resumo do dia">
          <div class="td-resumo__meta">
            <span class="td-rotulo">Realizado no turno</span>
            <strong class="td-resumo__valor">{{ moeda(turno.realizado) }}</strong>
            @if (turno.percentualMeta != null) {
              <ces-meter [value]="turno.percentualMeta" [label]="'Meta do dia de ' + turno.codigoEquipe" [detail]="turno.bateuMeta ? 'Meta de ' + moeda(turno.metaDiaria) + ' batida' : 'Faltam ' + moeda(turno.metaDiaria! - turno.realizado) + ' para ' + moeda(turno.metaDiaria)"></ces-meter>
            } @else {
              <span class="td-muted">Sem meta neste dia</span>
            }
          </div>
          <dl class="td-resumo__fatos">
            <div><dt><i class="pi pi-play" aria-hidden="true"></i> Início</dt><dd>{{ turno.dataInicio | date: 'dd/MM HH:mm' }}</dd></div>
            <div><dt><i class="pi pi-stop" aria-hidden="true"></i> Fim</dt><dd>{{ turno.dataFim ? (turno.dataFim | date: 'dd/MM HH:mm') : 'Em andamento' }}</dd></div>
            <div><dt><i class="pi pi-stopwatch" aria-hidden="true"></i> Duração</dt><dd>{{ duracao(turno.duracaoMinutos) }}</dd></div>
            <div><dt><i class="pi pi-check-square" aria-hidden="true"></i> Serviços</dt><dd>{{ servicos().length }}</dd></div>
          </dl>
        </section>

        <div class="td-grid">
          <section class="td-frentes" aria-labelledby="td-servicos">
            <header class="td-secao">
              <h2 id="td-servicos">Serviços do dia</h2>
              <p>O que estava programado, a APR feita pela equipe antes de começar e o que foi executado.</p>
            </header>

            @for (c of cartoes(); track c.chave) {
              <article class="td-card" [attr.aria-label]="'Obra ' + c.numeroObra">
                <header class="td-card__topo">
                  <div class="td-card__titulo">
                    @if (c.programacao; as p) {
                      <button type="button" class="td-link" (click)="verProgramacao(p)">#{{ p.numero }} · Obra {{ c.numeroObra }}</button>
                    } @else {
                      <a class="td-link" [routerLink]="['/obras', c.idObra]">Obra {{ c.numeroObra }}</a>
                    }
                    <span class="td-muted">{{ c.tituloObra }}</span>
                    @if (c.programacao?.descricao; as descricao) { <span>{{ descricao }}</span> }
                    @if (c.atividade?.observacao; as observacao) { <span class="td-muted">{{ observacao }}</span> }
                  </div>
                  @if (c.programacao; as p) {
                    <ces-status [tone]="situacaoProg(p).tom" [icon]="situacaoProg(p).icone.replace('pi ', '')">{{ situacaoProg(p).rotulo }}</ces-status>
                  } @else if (!c.atividade?.programada) {
                    <ces-status tone="warning">Não programada</ces-status>
                  }
                </header>

                <div class="td-gate" [attr.data-liberacao]="c.liberacao">
                  <i [class]="liberacao[c.liberacao].icone + ' td-gate__icone'" aria-hidden="true"></i>
                  <div class="td-gate__texto">
                    @switch (c.liberacao) {
                      @case ('PENDENTE') {
                        <strong>APR pendente</strong>
                        <span>A equipe ainda não preencheu a APR nem informou motivo para não executar.</span>
                      }
                      @case ('EM_PREENCHIMENTO') {
                        <strong>APR em preenchimento</strong>
                        <span>A equipe começou e ainda não concluiu.</span>
                      }
                      @case ('LIBERADA') {
                        <strong>APR concluída</strong>
                        <span>{{ c.aprConcluida?.atividade }} · {{ c.aprConcluida?.responsavel ?? 'Sem responsável' }}{{ c.aprConcluida?.dataConclusao ? ' · ' + (c.aprConcluida?.dataConclusao | date: 'dd/MM HH:mm') : '' }}</span>
                      }
                      @case ('IMPEDIDA') {
                        <strong>Sem condição de executar · {{ tipoOcorrencia[c.impedimento!.tipo].rotulo }}</strong>
                        <span>{{ c.impedimento?.descricao }}</span>
                      }
                    }
                  </div>
                  @if (c.aprs.length) {
                    <ces-button size="sm" variant="ghost" icon="pi pi-eye" label="Ver APR" (pressed)="apr().abrir(c.aprConcluida ?? c.aprs[0])"></ces-button>
                  }
                </div>

                @if (c.programacao; as p) {
                  <div class="td-progresso">
                    <span class="td-progresso__trilho" aria-hidden="true"><span [style.width.%]="p.itens ? (p.itensConcluidos / p.itens) * 100 : 0"></span></span>
                    <span class="td-muted">{{ p.itensConcluidos }} de {{ p.itens }} itens concluídos · {{ p.inicio }} a {{ p.fim }} · {{ moeda(p.valor) }}</span>
                  </div>
                }

                @if (c.apontamentos.length && !c.aprConcluida) {
                  <p class="td-aviso"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i> Execução apontada neste turno sem APR concluída da obra.</p>
                }

                @if (c.apontamentos.length) {
                  <div class="td-tabela-wrap">
                    <table class="td-tabela">
                      <caption class="td-sr">Itens executados na obra {{ c.numeroObra }}</caption>
                      <thead><tr><th scope="col">Executado no turno</th><th scope="col" class="td-num">Qtd.</th><th scope="col" class="td-num">Valor</th></tr></thead>
                      <tbody>
                        @for (a of c.apontamentos; track a.id) {
                          <tr>
                            <td><strong>{{ a.descricao }}</strong><small>{{ a.local }} · {{ a.usuario }}</small>
                              @if (a.excedente > 0) { <small class="td-excedente"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i> Passou {{ numero(a.excedente) }} {{ a.unidade }} do previsto: {{ a.justificativa }}</small> }
                            </td>
                            <td class="td-num">{{ numero(a.quantidade) }} {{ a.unidade }}</td>
                            <td class="td-num">{{ a.valor != null ? moeda(a.valor) : '—' }}</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                }
              </article>
            } @empty {
              <div class="td-card td-vazio-card">
                <i class="pi pi-calendar-times" aria-hidden="true"></i>
                <p>Nada programado para a equipe neste dia e nenhuma obra registrada no turno.</p>
              </div>
            }
          </section>

          <aside class="td-lateral">
            <section class="td-bloco" aria-labelledby="td-equipe">
              <h2 id="td-equipe"><i class="pi pi-users" aria-hidden="true"></i> Equipe</h2>
              @if (turno.composicao.length) {
                <ul class="td-pessoas">
                  @for (m of turno.composicao; track m.idColaborador) {
                    <li>
                      <span class="td-avatar" aria-hidden="true">{{ iniciais(m.nome) }}</span>
                      <span class="td-pessoa"><strong>{{ m.nome }}</strong><small>{{ m.funcao }}</small></span>
                      @if (m.lider) { <ces-status tone="info" icon="pi-star">Líder</ces-status> }
                      @if (m.substituto) { <ces-status tone="warning">Substituto</ces-status> }
                    </li>
                  }
                </ul>
              } @else {
                <p class="td-vazio">A composição não foi registrada neste turno.</p>
              }
              <p class="td-muted td-rodape">
                @if (turno.placa) { <span><i class="pi pi-car" aria-hidden="true"></i> {{ turno.placa }}</span> }
                @if (turno.abertoPor) { <span>Aberto por {{ turno.abertoPor }}</span> }
              </p>
            </section>

            <section class="td-bloco" aria-labelledby="td-ocorrencias">
              <h2 id="td-ocorrencias"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i> Ocorrências</h2>
              @if (turno.ocorrencias.length) {
                <ul class="td-lista">
                  @for (o of turno.ocorrencias; track o.id) {
                    <li class="td-linha">
                      <strong><i [class]="tipoOcorrencia[o.tipo].icone" aria-hidden="true"></i> {{ tipoOcorrencia[o.tipo].rotulo }}{{ o.numeroObra ? ' · ' + o.numeroObra : '' }}</strong>
                      <span>{{ o.descricao }}</span><small>{{ o.usuario }} · {{ o.data | date: 'dd/MM HH:mm' }}</small>
                    </li>
                  }
                </ul>
              } @else {
                <p class="td-vazio">Sem ocorrências.</p>
              }
            </section>

            <section class="td-bloco" aria-labelledby="td-relatorio">
              <h2 id="td-relatorio"><i class="pi pi-file-edit" aria-hidden="true"></i> Relatório diário</h2>
              @if (turno.relatorio; as relatorio) {
                <p class="td-texto">{{ relatorio.observacao }}</p>
                <p class="td-muted td-rodape">{{ relatorio.usuario }} · {{ relatorio.data | date: 'dd/MM/yyyy HH:mm' }}</p>
              } @else {
                <p class="td-vazio">A equipe não escreveu relatório neste turno.</p>
              }
              @if (turno.fotos.length) {
                <ul class="td-fotos" aria-label="Fotos do dia">
                  @for (f of turno.fotos; track f.id) {
                    <li>
                      <button type="button" class="td-foto" [attr.aria-label]="'Abrir ' + f.nomeArquivo" (click)="abrirArquivo(f)">
                        @if (urls()[f.id]; as url) { <img [src]="url" [alt]="f.nomeArquivo" loading="lazy" /> } @else { <i class="pi pi-image" aria-hidden="true"></i> }
                      </button>
                    </li>
                  }
                </ul>
              }
            </section>
          </aside>
        </div>
      }
    </div>

    @if (t(); as turno) {
      <app-apr-drawer #apr [turno]="turno"></app-apr-drawer>
    }
    <app-programacao-detalhe [(aberto)]="detalheAberto" [idProgramacao]="idDetalhe()" (alterada)="carregar()"></app-programacao-detalhe>
  `,
  styles: `
    .td-resumo { display: grid; grid-template-columns: minmax(16rem, 2fr) minmax(0, 3fr); gap: 1.5rem; align-items: center; margin-bottom: 1rem; padding: 1rem 1.25rem; background: var(--ces-surface); border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); }
    .td-resumo__meta { display: flex; flex-direction: column; gap: 0.25rem; min-width: 0; }
    .td-rotulo { color: var(--ces-foreground-muted); font-size: 0.8125rem; font-weight: 600; }
    .td-resumo__valor { font-size: 1.5rem; font-variant-numeric: tabular-nums; font-weight: 700; line-height: 1.2; }
    .td-resumo__fatos { display: grid; grid-template-columns: repeat(auto-fit, minmax(7rem, 1fr)); gap: 0.75rem; margin: 0; }
    .td-resumo__fatos dt { color: var(--ces-foreground-muted); font-size: 0.8125rem; }
    .td-resumo__fatos dt i { margin-right: 0.125rem; font-size: 0.75rem; }
    .td-resumo__fatos dd { margin: 0.125rem 0 0; font-variant-numeric: tabular-nums; font-weight: 600; }
    .td-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(19rem, 24rem); gap: 1rem; align-items: start; }
    .td-frentes, .td-lateral { display: flex; flex-direction: column; gap: 0.75rem; min-width: 0; }
    .td-secao h2 { margin: 0; font-size: 1.0625rem; font-weight: 700; }
    .td-secao p { margin: 0.125rem 0 0; color: var(--ces-foreground-muted); font-size: 0.875rem; }
    .td-card { display: flex; flex-direction: column; gap: 0.75rem; padding: 1rem; background: var(--ces-surface); border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); }
    .td-card__topo { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: flex-start; justify-content: space-between; }
    .td-card__titulo { display: flex; flex: 1 1 16rem; flex-direction: column; gap: 0.125rem; min-width: 0; overflow-wrap: anywhere; }
    .td-gate { display: flex; flex-wrap: wrap; gap: 0.5rem 0.75rem; align-items: center; padding: 0.625rem 0.75rem; background: var(--ces-surface-subtle); border-left: 3px solid var(--ces-border); border-radius: var(--ces-radius-sm); }
    .td-gate[data-liberacao='PENDENTE'], .td-gate[data-liberacao='EM_PREENCHIMENTO'] { background: var(--ces-warning-soft); border-left-color: var(--ces-warning-text); }
    .td-gate[data-liberacao='LIBERADA'] { background: var(--ces-success-soft); border-left-color: var(--ces-success-text); }
    .td-gate__icone { flex: none; }
    .td-gate[data-liberacao='PENDENTE'] .td-gate__icone, .td-gate[data-liberacao='EM_PREENCHIMENTO'] .td-gate__icone { color: var(--ces-warning-text); }
    .td-gate[data-liberacao='LIBERADA'] .td-gate__icone { color: var(--ces-success-text); }
    .td-gate[data-liberacao='IMPEDIDA'] .td-gate__icone { color: var(--ces-foreground-muted); }
    .td-gate__texto { display: flex; flex: 1 1 14rem; flex-direction: column; min-width: 0; font-size: 0.875rem; overflow-wrap: anywhere; }
    .td-progresso { display: flex; flex-direction: column; gap: 0.25rem; }
    .td-progresso__trilho { display: block; height: 0.375rem; overflow: hidden; background: var(--ces-surface-subtle); border-radius: 999px; }
    .td-progresso__trilho span { display: block; height: 100%; background: var(--ces-brand); border-radius: inherit; }
    .td-aviso { margin: 0; color: var(--ces-warning-text); font-size: 0.875rem; font-weight: 600; }
    .td-vazio-card { flex-direction: row; align-items: center; color: var(--ces-foreground-muted); font-size: 0.875rem; }
    .td-vazio-card p { margin: 0; }
    .td-bloco { padding: 1rem; background: var(--ces-surface); border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); }
    .td-bloco h2 { margin: 0 0 0.625rem; font-size: 0.9375rem; font-weight: 700; }
    .td-bloco h2 i { margin-right: 0.25rem; color: var(--ces-foreground-muted); }
    .td-pessoas { display: flex; flex-direction: column; gap: 0.5rem; margin: 0; padding: 0; list-style: none; }
    .td-pessoas li { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
    .td-avatar { display: inline-flex; flex: none; align-items: center; justify-content: center; width: 1.875rem; height: 1.875rem; color: var(--ces-foreground-muted); font-size: 0.6875rem; font-weight: 700; background: var(--ces-surface-subtle); border-radius: 50%; }
    .td-pessoa { display: flex; flex: 1 1 8rem; flex-direction: column; min-width: 0; font-size: 0.875rem; overflow-wrap: anywhere; }
    .td-pessoa strong { font-weight: 600; }
    .td-pessoa small { color: var(--ces-foreground-muted); }
    .td-lista { display: flex; flex-direction: column; gap: 0.625rem; margin: 0; padding: 0; list-style: none; }
    .td-linha { display: flex; flex-direction: column; min-width: 0; font-size: 0.875rem; overflow-wrap: anywhere; }
    .td-linha small { color: var(--ces-foreground-muted); }
    .td-link { padding: 0; color: var(--ces-brand); font-weight: 700; text-align: left; text-decoration: none; background: none; border: 0; cursor: pointer; }
    .td-link:hover { text-decoration: underline; }
    .td-link:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 2px; }
    .td-muted { color: var(--ces-foreground-muted); font-size: 0.875rem; }
    .td-rodape { display: flex; flex-wrap: wrap; gap: 0.25rem 0.75rem; margin: 0.625rem 0 0; font-size: 0.8125rem; }
    .td-rodape:empty { display: none; }
    .td-texto { margin: 0; font-size: 0.875rem; overflow-wrap: anywhere; white-space: pre-wrap; }
    .td-vazio { margin: 0; color: var(--ces-foreground-muted); font-size: 0.875rem; }
    .td-tabela-wrap { overflow-x: auto; }
    .td-tabela { width: 100%; font-size: 0.875rem; border-collapse: collapse; }
    .td-tabela th { padding: 0.375rem 0.5rem; color: var(--ces-foreground-muted); font-size: 0.8125rem; font-weight: 600; text-align: left; border-bottom: 1px solid var(--ces-border); }
    .td-tabela td { padding: 0.5rem; vertical-align: top; border-bottom: 1px solid var(--ces-border); }
    .td-tabela tr:last-child td { border-bottom: 0; }
    .td-tabela small { display: block; color: var(--ces-foreground-muted); }
    .td-tabela .td-num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    .td-tabela .td-excedente { color: var(--ces-warning-text); }
    .td-sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
    .td-fotos { display: grid; grid-template-columns: repeat(auto-fill, minmax(5.5rem, 1fr)); gap: 0.5rem; margin: 0.75rem 0 0; padding: 0; list-style: none; }
    .td-foto { display: flex; align-items: center; justify-content: center; width: 100%; aspect-ratio: 4 / 3; padding: 0; overflow: hidden; color: var(--ces-foreground-muted); background: var(--ces-surface-subtle); border: 1px solid var(--ces-border); border-radius: var(--ces-radius-sm); cursor: zoom-in; }
    .td-foto img { width: 100%; height: 100%; object-fit: cover; }
    .td-foto:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 2px; }
    @media (max-width: 1080px) { .td-grid, .td-resumo { grid-template-columns: minmax(0, 1fr); } }
  `
})
export class TurnoObraDetalheComponent {
  private readonly api = inject(ObrasTurnosService);
  private readonly notificacoes = inject(NotificationService);
  protected readonly apr = viewChild.required<AprDrawerComponent>('apr');

  private readonly rota = inject(ActivatedRoute);
  private id = this.rota.snapshot.paramMap.get('id')!;

  protected readonly moeda = formatarMoeda;
  protected readonly numero = formatarNumero;
  protected readonly duracao = duracao;
  protected readonly tipoOcorrencia = TIPO_OCORRENCIA;
  protected readonly liberacao = LIBERACAO;

  protected readonly t = signal<TurnoObraDetalhe | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly urls = signal<Record<string, string>>({});
  protected readonly detalheAberto = signal(false);
  protected readonly idDetalhe = signal<string | null>(null);

  protected readonly situacao = computed(() => situacaoDoTurno(this.t() ?? { status: 'ABERTO', abertoDesdeOutroDia: false, dataInicio: '', encerradoAutomaticamente: false }));
  protected readonly cartoes = computed(() => (this.t() ? cartoesDoTurno(this.t()!) : []));
  protected readonly servicos = computed(() => (this.t()?.apontamentos ?? []).filter((a) => a.tipo === 'SERVICO'));
  protected readonly subtitulo = computed(() => {
    const t = this.t();
    return t ? [t.tipoEquipe, t.baseOperacional].filter(Boolean).join(' · ') : '';
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    destroyRef.onDestroy(() => Object.values(this.urls()).forEach((url) => URL.revokeObjectURL(url)));
    this.rota.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.id = params.get('id')!;
      this.t.set(null);
      this.carregar();
    });
  }

  carregar(): void {
    this.erro.set(null);
    this.api.detalhar(this.id).pipe(take(1)).subscribe({
      next: (t) => this.aplicar(t),
      error: (e) => this.erro.set(getErrorMessage(e))
    });
  }

  private aplicar(t: TurnoObraDetalhe): void {
    this.t.set(t);
    for (const foto of t.fotos) {
      if (!this.urls()[foto.id]) {
        this.api.baixarAnexo(t.id, foto.id).pipe(take(1)).subscribe({
          next: (blob) => this.urls.update((atual) => ({ ...atual, [foto.id]: URL.createObjectURL(blob) })),
          error: () => undefined
        });
      }
    }
  }

  protected situacaoProg(p: ProgramacaoDoTurno) {
    return SITUACAO_PROGRAMACAO[p.situacao];
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    return (partes[0][0] + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
  }

  protected verProgramacao(p: ProgramacaoDoTurno): void {
    this.idDetalhe.set(p.id);
    this.detalheAberto.set(true);
  }

  protected abrirArquivo(f: FotoTurno): void {
    this.api.baixarAnexo(this.t()!.id, f.id).pipe(take(1)).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }
}
