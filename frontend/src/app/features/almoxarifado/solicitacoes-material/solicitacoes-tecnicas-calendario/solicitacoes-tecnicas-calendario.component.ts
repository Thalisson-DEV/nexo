import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CesAsyncStateComponent, CesButtonComponent, CesCalloutComponent, CesIconButtonComponent, CesStatusComponent } from '@ces/ui';
import { catchError, of, switchMap, tap } from 'rxjs';

import { SolicitacaoMaterialSummaryResponse, StatusSolicitacaoMaterial } from '../../../../core/models/solicitacao-material-tecnica.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { statusLabel, statusTone, tipoTecnicoLabel } from '../solicitacao-tecnica-presentation';

/** Limite de solicitações por mês exibido; acima disso o calendário pede para refinar o recorte. */
export const LIMITE_CALENDARIO = 500;
const VISIVEIS_POR_DIA = 3;
const DIAS_SEMANA = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];

const ICONES: Partial<Record<StatusSolicitacaoMaterial, string>> = {
  PENDENTE: 'pi-clock',
  APROVADO: 'pi-check',
  REJEITADO: 'pi-times',
  ENTREGUE_PARCIAL: 'pi-box',
  ENTREGUE: 'pi-check-circle',
  DEVOLVIDO_PARCIAL: 'pi-undo',
  DEVOLVIDO: 'pi-undo',
  ESTORNADO: 'pi-replay',
  CANCELADO: 'pi-ban',
};

const LEGENDA: StatusSolicitacaoMaterial[] = ['PENDENTE', 'APROVADO', 'ENTREGUE_PARCIAL', 'ENTREGUE', 'REJEITADO'];

interface DiaCalendario {
  iso: string;
  numero: number;
  noMes: boolean;
  hoje: boolean;
  fimDeSemana: boolean;
  itens: SolicitacaoMaterialSummaryResponse[];
}

/**
 * Agenda das solicitações técnicas: grade mensal com as solicitações na data prevista de atendimento.
 * O dia selecionado lista tudo o que está previsto para ele. Solicitação sem data prevista não aparece.
 */
@Component({
  selector: 'app-solicitacoes-tecnicas-calendario',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CesAsyncStateComponent, CesButtonComponent, CesCalloutComponent, CesIconButtonComponent, CesStatusComponent],
  styles: `
    :host { display: block; }
    .cal-shell { display: grid; gap: 1rem; grid-template-columns: minmax(0, 1fr) 20rem; align-items: start; }
    .cal-card {
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-xl);
      background: var(--ces-surface);
      box-shadow: var(--ces-shadow-sm);
      overflow: hidden;
    }
    .cal-head {
      display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; flex-wrap: wrap;
      padding: 0.75rem 1rem; border-bottom: 1px solid var(--ces-border);
    }
    .cal-nav { display: flex; align-items: center; gap: 0.25rem; }
    .cal-title { margin: 0 0.5rem; font-size: var(--ces-font-title); font-weight: 700; color: var(--ces-foreground); min-width: 11rem; }
    .cal-resumo { display: flex; gap: 0.5rem; flex-wrap: wrap; align-items: center; font-size: var(--ces-font-caption); color: var(--ces-foreground-muted); }
    .cal-weekdays, .cal-week { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); }
    .cal-weekdays { background: var(--ces-surface-subtle); border-bottom: 1px solid var(--ces-border); }
    .cal-weekdays span {
      padding: 0.5rem; font-size: var(--ces-font-caption); font-weight: 700; text-transform: uppercase;
      letter-spacing: 0.04em; color: var(--ces-foreground-muted); text-align: left;
    }
    .cal-week + .cal-week { border-top: 1px solid var(--ces-border); }
    .cal-day {
      position: relative; display: flex; flex-direction: column; gap: 0.25rem; min-height: 7.25rem; min-width: 0;
      padding: 0.375rem; border-left: 1px solid var(--ces-border); cursor: pointer;
      transition: background var(--ces-duration-fast) var(--ces-ease-standard);
    }
    .cal-day:first-child { border-left: 0; }
    .cal-day:hover { background: var(--ces-surface-subtle); }
    .cal-day--fds { background: color-mix(in srgb, var(--ces-surface-muted) 45%, transparent); }
    .cal-day--fora { background: var(--ces-surface-subtle); }
    .cal-day--fora .cal-num { color: var(--ces-foreground-subtle); }
    .cal-day--sel { background: var(--ces-brand-soft); box-shadow: inset 0 0 0 2px var(--ces-brand); }
    .cal-num {
      display: inline-flex; align-items: center; justify-content: center; align-self: flex-start;
      min-width: 1.75rem; height: 1.75rem; padding: 0 0.375rem; border: 0; border-radius: 999px;
      background: transparent; color: var(--ces-foreground); font: inherit; font-size: var(--ces-font-control); font-weight: 600; cursor: pointer;
    }
    .cal-num:focus-visible, .cal-pill:focus-visible, .cal-mais:focus-visible, .cal-agenda-item:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 2px; }
    .cal-day--hoje .cal-num { background: var(--ces-brand); color: #fff; }
    .cal-pill {
      display: flex; align-items: center; gap: 0.375rem; width: 100%; min-width: 0; padding: 0.2rem 0.4rem;
      border: 1px solid var(--ces-status-border); border-radius: var(--ces-radius-sm);
      background: var(--ces-status-soft); color: var(--ces-status-ink);
      font: inherit; font-size: 0.72rem; font-weight: 600; text-align: left; cursor: pointer;
    }
    .cal-pill:hover { filter: brightness(0.97); border-color: var(--ces-status-color); }
    .cal-pill .pi { font-size: 0.65rem; flex: none; }
    .cal-pill__txt { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .cal-pill--atrasada { box-shadow: inset 3px 0 0 var(--ces-danger); }
    .cal-mais {
      align-self: flex-start; padding: 0.1rem 0.3rem; border: 0; border-radius: var(--ces-radius-sm); background: transparent;
      color: var(--ces-brand); font: inherit; font-size: 0.72rem; font-weight: 700; cursor: pointer;
    }
    .cal-mais:hover { background: var(--ces-brand-soft); }
    .cal-dots { display: none; }

    [data-tone='warning'] { --ces-status-color: var(--ces-warning); --ces-status-soft: var(--ces-warning-soft); --ces-status-ink: var(--ces-warning-text); --ces-status-border: color-mix(in srgb, var(--ces-warning) 35%, transparent); }
    [data-tone='info'] { --ces-status-color: var(--ces-info); --ces-status-soft: var(--ces-info-soft); --ces-status-ink: var(--ces-info-text); --ces-status-border: color-mix(in srgb, var(--ces-info) 35%, transparent); }
    [data-tone='success'] { --ces-status-color: var(--ces-success); --ces-status-soft: var(--ces-success-soft); --ces-status-ink: var(--ces-success-text); --ces-status-border: color-mix(in srgb, var(--ces-success) 35%, transparent); }
    [data-tone='danger'] { --ces-status-color: var(--ces-danger); --ces-status-soft: var(--ces-danger-soft); --ces-status-ink: var(--ces-danger-text); --ces-status-border: color-mix(in srgb, var(--ces-danger) 35%, transparent); }
    [data-tone='neutral'] { --ces-status-color: var(--ces-foreground-muted); --ces-status-soft: var(--ces-surface-muted); --ces-status-ink: var(--ces-foreground); --ces-status-border: var(--ces-border); }

    .cal-legenda { display: flex; flex-wrap: wrap; gap: 0.75rem; padding: 0.625rem 1rem; border-top: 1px solid var(--ces-border); font-size: var(--ces-font-caption); color: var(--ces-foreground-muted); }
    .cal-legenda span { display: inline-flex; align-items: center; gap: 0.3rem; }
    .cal-legenda .pi { font-size: 0.7rem; color: var(--ces-status-color); }

    .cal-agenda { position: sticky; top: 1rem; display: flex; flex-direction: column; max-height: calc(100vh - 8rem); }
    .cal-agenda__head { padding: 0.875rem 1rem; border-bottom: 1px solid var(--ces-border); }
    .cal-agenda__eyebrow { margin: 0; font-size: var(--ces-font-caption); font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--ces-foreground-muted); }
    .cal-agenda__title { margin: 0.125rem 0 0; font-size: var(--ces-font-title); font-weight: 700; color: var(--ces-foreground); }
    .cal-agenda__list { display: flex; flex-direction: column; gap: 0.5rem; padding: 0.75rem; overflow-y: auto; }
    .cal-agenda__vazio { padding: 2rem 1rem; text-align: center; color: var(--ces-foreground-muted); font-size: var(--ces-font-control); }
    .cal-agenda__vazio .pi { display: block; margin-bottom: 0.5rem; font-size: 1.5rem; color: var(--ces-foreground-subtle); }
    .cal-agenda-item {
      display: grid; gap: 0.3rem; width: 100%; padding: 0.625rem 0.75rem; border: 1px solid var(--ces-border);
      border-left: 3px solid var(--ces-status-color); border-radius: var(--ces-radius-md);
      background: var(--ces-surface); color: var(--ces-foreground); font: inherit; text-align: left; cursor: pointer;
    }
    .cal-agenda-item:hover { background: var(--ces-surface-subtle); border-color: var(--ces-border-strong); border-left-color: var(--ces-status-color); }
    .cal-agenda-item__top { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; }
    .cal-agenda-item__id { font-weight: 700; font-size: var(--ces-font-control); font-variant-numeric: tabular-nums; }
    .cal-agenda-item__rota { font-size: var(--ces-font-caption); color: var(--ces-foreground-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .cal-agenda-item__rota strong { color: var(--ces-foreground); font-weight: 600; }
    .cal-agenda-item__meta { display: flex; gap: 0.75rem; font-size: var(--ces-font-caption); color: var(--ces-foreground-muted); }
    .cal-agenda-item__meta .pi { font-size: 0.7rem; margin-right: 0.2rem; }

    @media (max-width: 64rem) {
      .cal-shell { grid-template-columns: minmax(0, 1fr); }
      .cal-agenda { position: static; max-height: none; }
    }
    @media (max-width: 40rem) {
      .cal-day { min-height: 3.25rem; padding: 0.25rem; align-items: center; }
      .cal-num { align-self: center; }
      .cal-pill, .cal-mais { display: none; }
      .cal-dots { display: flex; gap: 2px; flex-wrap: wrap; justify-content: center; }
      .cal-dots i { width: 6px; height: 6px; border-radius: 999px; background: var(--ces-status-color); }
      .cal-weekdays span { text-align: center; padding: 0.375rem 0; }
      .cal-title { min-width: 0; }
    }
    @media (prefers-reduced-motion: reduce) { .cal-day { transition: none; } }
  `,
  template: `
    @if (erro()) {
      <ces-async-state type="error" title="Não foi possível carregar o calendário" [description]="erro()!" actionLabel="Tentar novamente" (actionPressed)="recarregar()" />
    } @else {
      @if (truncado()) {
        <ces-callout tone="warning" title="Mês com muitas solicitações">
          Mostrando {{ limite }} de {{ total() }}. Use os filtros ou os atalhos de status para reduzir o recorte.
        </ces-callout>
      }
      <div class="cal-shell">
        <section class="cal-card" aria-labelledby="cal-titulo">
          <header class="cal-head">
            <div class="cal-nav">
              <ces-icon-button icon="pi pi-chevron-left" label="Mês anterior" size="icon-sm" (pressed)="mudarMes(-1)" />
              <h2 id="cal-titulo" class="cal-title" aria-live="polite">{{ tituloMes() }}</h2>
              <ces-icon-button icon="pi pi-chevron-right" label="Próximo mês" size="icon-sm" (pressed)="mudarMes(1)" />
              <ces-button label="Hoje" variant="outline" size="sm" [disabled]="ehMesAtual()" (pressed)="irParaHoje()" />
            </div>
            <div class="cal-resumo" aria-live="polite">
              @if (carregando()) {
                <span><i class="pi pi-spin pi-spinner" aria-hidden="true"></i> Carregando…</span>
              } @else {
                <span>{{ totalNoMes() }} {{ totalNoMes() === 1 ? 'solicitação prevista' : 'solicitações previstas' }} no mês</span>
                @if (atrasadasNoMes() > 0) {
                  <ces-status tone="danger" icon="pi pi-exclamation-triangle">{{ atrasadasNoMes() }} {{ atrasadasNoMes() === 1 ? 'atrasada' : 'atrasadas' }}</ces-status>
                }
              }
            </div>
          </header>

          <div role="grid" [attr.aria-label]="'Solicitações previstas em ' + tituloMes()" [attr.aria-busy]="carregando()">
            <div class="cal-weekdays" role="row">
              @for (d of diasSemana; track d) { <span role="columnheader">{{ d }}</span> }
            </div>
            @for (semana of semanas(); track $index) {
              <div class="cal-week" role="row">
                @for (dia of semana; track dia.iso) {
                  <div
                    role="gridcell"
                    class="cal-day"
                    [class.cal-day--fora]="!dia.noMes"
                    [class.cal-day--fds]="dia.fimDeSemana && dia.noMes"
                    [class.cal-day--hoje]="dia.hoje"
                    [class.cal-day--sel]="dia.iso === selecionado()"
                    [attr.aria-selected]="dia.iso === selecionado()"
                    (click)="selecionado.set(dia.iso)"
                  >
                    <button type="button" class="cal-num" [attr.aria-label]="rotuloDia(dia)" (click)="selecionar(dia.iso, $event)">{{ dia.numero }}</button>
                    @for (s of dia.itens.slice(0, visiveis); track s.id) {
                      <button
                        type="button"
                        class="cal-pill"
                        [class.cal-pill--atrasada]="atrasada(s)"
                        [attr.data-tone]="status(s.status).tone"
                        [attr.aria-label]="rotuloSolicitacao(s)"
                        (click)="abrirSolicitacao(s, $event)"
                      >
                        <i [class]="'pi ' + status(s.status).icon" aria-hidden="true"></i>
                        <span class="cal-pill__txt">{{ obra(s) }}</span>
                      </button>
                    }
                    @if (dia.itens.length > visiveis) {
                      <button type="button" class="cal-mais" (click)="selecionar(dia.iso, $event)">+{{ dia.itens.length - visiveis }} mais</button>
                    }
                    @if (dia.itens.length) {
                      <span class="cal-dots" aria-hidden="true">
                        @for (s of dia.itens.slice(0, 4); track s.id) { <i [attr.data-tone]="status(s.status).tone"></i> }
                      </span>
                    }
                  </div>
                }
              </div>
            }
          </div>

          <footer class="cal-legenda" aria-label="Legenda">
            @for (s of legenda; track s) {
              <span [attr.data-tone]="status(s).tone"><i [class]="'pi ' + status(s).icon" aria-hidden="true"></i>{{ status(s).label }}</span>
            }
            <span>Solicitações sem data prevista não aparecem aqui.</span>
          </footer>
        </section>

        <aside class="cal-card cal-agenda" aria-labelledby="cal-agenda-titulo">
          <div class="cal-agenda__head">
            <p class="cal-agenda__eyebrow">{{ agendaSelecionada().length }} {{ agendaSelecionada().length === 1 ? 'solicitação prevista' : 'solicitações previstas' }}</p>
            <h3 id="cal-agenda-titulo" class="cal-agenda__title">{{ tituloDiaSelecionado() }}</h3>
          </div>
          <div class="cal-agenda__list">
            @for (s of agendaSelecionada(); track s.id) {
              <button type="button" class="cal-agenda-item" [attr.data-tone]="status(s.status).tone" (click)="abrir.emit(s)" [attr.aria-label]="rotuloSolicitacao(s)">
                <span class="cal-agenda-item__top">
                  <span class="cal-agenda-item__id">{{ tipo(s.tipo) }} · {{ s.id.substring(0, 8) }}</span>
                  <ces-status [tone]="status(s.status).tone" [icon]="'pi ' + status(s.status).icon">{{ status(s.status).label }}</ces-status>
                </span>
                <span class="cal-agenda-item__rota">{{ s.almoxarifadoNome || s.estoqueOrigemNome }} → <strong>{{ obra(s) }}</strong></span>
                <span class="cal-agenda-item__meta">
                  <span><i class="pi pi-box" aria-hidden="true"></i>{{ s.quantidadeItens }} {{ s.quantidadeItens === 1 ? 'item' : 'itens' }}</span>
                  @if (atrasada(s)) {
                    <span><i class="pi pi-exclamation-triangle" aria-hidden="true"></i>Atrasada {{ relativo(s) }}</span>
                  }
                  @if (s.possuiPendencia) {
                    <span><i class="pi pi-flag" aria-hidden="true"></i>Com pendência</span>
                  }
                </span>
              </button>
            } @empty {
              <p class="cal-agenda__vazio"><i class="pi pi-calendar" aria-hidden="true"></i>Nenhuma solicitação prevista neste dia.</p>
            }
          </div>
        </aside>
      </div>
    }
  `
})
export class SolicitacoesTecnicasCalendarioComponent {
  private readonly service = inject(SolicitacaoMaterialTecnicaService);

  /** Recorte da tela (já no formato do backend), sem paginação. */
  readonly filtros = input<Record<string, unknown>>({});
  /** Como a tela nomeia a obra; a listagem só traz o id. */
  readonly rotuloObra = input<(idObra?: string | null) => string>((id) => (id ? `Obra ${id.substring(0, 8)}` : 'Obra não informada'));
  readonly abrir = output<SolicitacaoMaterialSummaryResponse>();

  protected readonly diasSemana = DIAS_SEMANA;
  protected readonly visiveis = VISIVEIS_POR_DIA;
  protected readonly limite = LIMITE_CALENDARIO;
  protected readonly legenda = LEGENDA;
  protected readonly tipo = tipoTecnicoLabel;

  private readonly hoje = isoCivil(new Date());
  protected readonly mes = signal(primeiroDoMes(new Date()));
  protected readonly selecionado = signal<string>(this.hoje);
  private readonly versao = signal(0);

  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly itens = signal<SolicitacaoMaterialSummaryResponse[]>([]);
  protected readonly total = signal(0);
  protected readonly truncado = computed(() => this.total() > this.itens().length);

  /** Domingo antes do dia 1 até completar 6 semanas. */
  private readonly inicioGrade = computed(() => {
    const m = this.mes();
    return new Date(m.getFullYear(), m.getMonth(), 1 - m.getDay());
  });
  private readonly fimGrade = computed(() => {
    const i = this.inicioGrade();
    return new Date(i.getFullYear(), i.getMonth(), i.getDate() + 41);
  });

  private readonly porDia = computed(() => {
    const mapa = new Map<string, SolicitacaoMaterialSummaryResponse[]>();
    for (const s of this.itens()) {
      if (!s.dataPrevista) continue;
      const dia = s.dataPrevista.substring(0, 10);
      mapa.set(dia, [...(mapa.get(dia) ?? []), s]);
    }
    return mapa;
  });

  protected readonly semanas = computed<DiaCalendario[][]>(() => {
    const inicio = this.inicioGrade();
    const mes = this.mes().getMonth();
    const porDia = this.porDia();
    const semanas: DiaCalendario[][] = [];
    for (let w = 0; w < 6; w++) {
      const semana: DiaCalendario[] = [];
      for (let d = 0; d < 7; d++) {
        const data = new Date(inicio.getFullYear(), inicio.getMonth(), inicio.getDate() + w * 7 + d);
        const iso = isoCivil(data);
        semana.push({ iso, numero: data.getDate(), noMes: data.getMonth() === mes, hoje: iso === this.hoje, fimDeSemana: d === 0 || d === 6, itens: porDia.get(iso) ?? [] });
      }
      semanas.push(semana);
    }
    return semanas;
  });

  private readonly doMes = computed(() => {
    const m = this.mes();
    const prefixo = isoCivil(m).substring(0, 7);
    return this.itens().filter((s) => s.dataPrevista?.startsWith(prefixo));
  });
  protected readonly totalNoMes = computed(() => this.doMes().length);
  protected readonly atrasadasNoMes = computed(() => this.doMes().filter((s) => this.atrasada(s)).length);
  protected readonly agendaSelecionada = computed(() => this.porDia().get(this.selecionado()) ?? []);

  protected readonly tituloMes = computed(() => inicial(this.mes().toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })));
  protected readonly ehMesAtual = computed(() => isoCivil(this.mes()).substring(0, 7) === this.hoje.substring(0, 7));
  protected readonly tituloDiaSelecionado = computed(() =>
    inicial(dataCivil(this.selecionado()).toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' }))
  );

  private readonly consulta = computed(() => ({ filtros: this.filtros(), inicio: isoCivil(this.inicioGrade()), fim: isoCivil(this.fimGrade()), versao: this.versao() }));

  constructor() {
    toObservable(this.consulta)
      .pipe(
        tap(() => {
          this.carregando.set(true);
          this.erro.set(null);
        }),
        switchMap(({ filtros, inicio, fim }) =>
          this.service
            .listar({ ...filtros, previstaDe: inicio, previstaAte: fim, page: 0, size: LIMITE_CALENDARIO, sort: 'dataPrevistaRetirada,asc' })
            .pipe(
              catchError((err) => {
                this.erro.set(getErrorMessage(err));
                return of(null);
              })
            )
        ),
        takeUntilDestroyed()
      )
      .subscribe((res) => {
        this.carregando.set(false);
        if (!res) return;
        this.itens.set(res.content ?? []);
        this.total.set(res.page?.totalElements ?? res.content?.length ?? 0);
      });
  }

  recarregar(): void {
    this.versao.update((v) => v + 1);
  }

  protected mudarMes(delta: number): void {
    const m = this.mes();
    const novo = new Date(m.getFullYear(), m.getMonth() + delta, 1);
    this.mes.set(novo);
    this.selecionado.set(this.ehMesDe(this.hoje, novo) ? this.hoje : isoCivil(novo));
  }

  protected irParaHoje(): void {
    this.mes.set(primeiroDoMes(new Date()));
    this.selecionado.set(this.hoje);
  }

  protected selecionar(iso: string, evento: Event): void {
    evento.stopPropagation();
    this.selecionado.set(iso);
  }

  protected abrirSolicitacao(s: SolicitacaoMaterialSummaryResponse, evento: Event): void {
    evento.stopPropagation();
    this.selecionado.set(s.dataPrevista!.substring(0, 10));
    this.abrir.emit(s);
  }

  protected status(valor: string) {
    return { label: statusLabel(valor), tone: statusTone(valor), icon: ICONES[valor as StatusSolicitacaoMaterial] ?? 'pi-circle' };
  }

  protected obra(s: SolicitacaoMaterialSummaryResponse): string {
    return this.rotuloObra()(s.idObra);
  }

  protected atrasada(s: SolicitacaoMaterialSummaryResponse): boolean {
    return !!s.atrasada;
  }

  protected relativo(s: SolicitacaoMaterialSummaryResponse): string {
    return s.dataPrevista ? prazoRelativo(s.dataPrevista) : '';
  }

  protected rotuloDia(dia: DiaCalendario): string {
    const data = dataCivil(dia.iso).toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
    const n = dia.itens.length;
    return `${data}: ${n === 0 ? 'nenhuma solicitação prevista' : n === 1 ? '1 solicitação prevista' : n + ' solicitações previstas'}`;
  }

  protected rotuloSolicitacao(s: SolicitacaoMaterialSummaryResponse): string {
    const partes = [`Solicitação #${s.id.substring(0, 8)}`, `para ${this.obra(s)}`, statusLabel(s.status)];
    if (this.atrasada(s)) partes.push('atrasada');
    return partes.join(', ');
  }

  private ehMesDe(iso: string, mes: Date): boolean {
    return iso.substring(0, 7) === isoCivil(mes).substring(0, 7);
  }
}

function isoCivil(data: Date): string {
  const m = String(data.getMonth() + 1).padStart(2, '0');
  const d = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${m}-${d}`;
}

function dataCivil(iso: string): Date {
  const [a, m, d] = iso.substring(0, 10).split('-').map(Number);
  return new Date(a, m - 1, d);
}

/** "hoje", "ontem", "há 2 dias" para a data prevista. */
function prazoRelativo(dataPrevista: string, hoje = new Date()): string {
  const base = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());
  const dias = Math.round((dataCivil(dataPrevista).getTime() - base.getTime()) / 86_400_000);
  if (dias === 0) return 'hoje';
  if (dias === 1) return 'amanhã';
  if (dias === -1) return 'ontem';
  return dias > 0 ? `em ${dias} dias` : `há ${-dias} dias`;
}

/** "outubro de 2026" → "Outubro de 2026". */
function inicial(texto: string): string {
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

function primeiroDoMes(data: Date): Date {
  return new Date(data.getFullYear(), data.getMonth(), 1);
}
