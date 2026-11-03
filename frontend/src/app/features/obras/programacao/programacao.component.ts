import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFilterPanelComponent,
  CesIconButtonComponent,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';
import { catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';
import { getErrorMessage } from '../../../core/models/shared.models';
import { BarraGantt, GanttProgramacao, LinhaGantt, ObraAProgramar, SituacaoIntervencao, SituacaoProgramacao, TurnoProgramacao } from '../../../core/models/obras-programacao.models';
import { AuthService } from '../../../core/services/auth.service';
import { ObrasProgramacaoService } from '../../../core/services/obras-programacao.service';
import { RhService } from '../../../core/services/rh.service';
import { dataIso } from '../../../shared/listagem/listagem';
import { formatarMoeda, parseDataBrParaDate } from '../obras-ui';
import { ProgramacaoDetalheComponent } from './programacao-detalhe.component';
import { ArrasteProgramacao, ProgramacaoLinhaTempoComponent } from './programacao-linha-tempo.component';
import {
  FiltrosBarra,
  ROTULO_TURNO,
  SIM_NAO,
  SITUACAO_PROGRAMACAO,
  SITUACOES_EM_ABERTO,
  SITUACOES_INTERVENCAO,
  TURNOS,
  VisaoProgramacao,
  atendeFiltrosBarra,
  atendeSituacao,
  deslocarVisao,
  formatarNumero,
  periodoDaVisao,
  temFiltroBarra,
  tituloDoPeriodo
} from './programacao.logic';

const VISOES: { value: VisaoProgramacao; label: string; icon: string; rota: string }[] = [
  { value: 'tabela', label: 'Tabela', icon: 'pi pi-table', rota: '/obras/programacao' },
  { value: 'semana', label: 'Semana', icon: 'pi pi-calendar-minus', rota: '/obras/programacao/semana' },
  { value: 'mes', label: 'Mês', icon: 'pi pi-calendar', rota: '/obras/programacao/mes' }
];

type FiltroEquipes = 'COM_PROGRAMACAO' | 'LIVRES';
const OPCOES_EQUIPES: CesSelectOption<FiltroEquipes | null>[] = [
  { label: 'Todas as equipes', value: null },
  { label: 'Com programação', value: 'COM_PROGRAMACAO' },
  { label: 'Livres no período', value: 'LIVRES' }
];
const SITUACOES: SituacaoProgramacao[] = ['PROGRAMADA', 'EM_ANDAMENTO', 'ATRASADA', 'CONCLUIDA', 'CANCELADA'];
const OPCOES_SITUACAO: CesSelectOption<SituacaoProgramacao>[] = SITUACOES.map((value) => ({ value, label: SITUACAO_PROGRAMACAO[value].rotulo }));

const OPCOES_PRIORIDADE: CesSelectOption<string | null>[] = [
  { label: 'Todas', value: null },
  { label: 'Urgente', value: 'URGENTE' },
  { label: 'Alta', value: 'ALTA' },
  { label: 'Normal', value: 'NORMAL' },
  { label: 'Baixa', value: 'BAIXA' }
];
const OPCOES_TURNO: CesSelectOption<TurnoProgramacao | null>[] = [{ label: 'Todos', value: null }, ...TURNOS];
const OPCOES_SIM_NAO: CesSelectOption<boolean | null>[] = [{ label: 'Todas', value: null }, ...SIM_NAO];
const OPCOES_INTERVENCAO: CesSelectOption<SituacaoIntervencao | null>[] = [{ label: 'Todas', value: null }, ...SITUACOES_INTERVENCAO];

function opcoesDe(valores: readonly (string | null | undefined)[]): CesSelectOption<string>[] {
  return [...new Set(valores.filter((v): v is string => !!v))].sort((a, b) => a.localeCompare(b, 'pt-BR')).map((value) => ({ value, label: value }));
}

interface LinhaTabela extends BarraGantt {
  codigoEquipe: string;
  base?: string | null;
}

function dataDaUrl(valor: string | null): Date | null {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor ?? '');
  return partes ? new Date(+partes[1], +partes[2] - 1, +partes[3]) : null;
}

/**
 * Programação das obras em três visões do mesmo recorte: tabela, semana e mês por equipe. Filtros, visão e data ficam
 * na URL, então um link abre a tela igual. Sem situação escolhida, as canceladas ficam de fora, para não poluir o dia a dia.
 */
@Component({
  selector: 'app-programacao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesIconButtonComponent,
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    ProgramacaoDetalheComponent,
    ProgramacaoLinhaTempoComponent
  ],
  template: `
    <div class="ces-page">
      <ces-page-header title="Programação" icon="pi pi-calendar" [breadcrumb]="[{ label: 'Obras' }, { label: 'Programação' }]"
        description="Quem faz o quê e quando. Novas programações nascem da seleção de itens no planejamento da obra.">
        <ces-button actions variant="outline" icon="pi pi-history" label="Histórico" (pressed)="abrirHistorico()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel [storageKey]="'obras-programacao'" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="estado().carregando" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Obra" [options]="opcoesObra()" [(value)]="lista.filters.idObra" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Município" [options]="opcoesMunicipio()" [(value)]="lista.filters.municipio" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Prioridade da obra" [options]="opcoesPrioridade" [(value)]="lista.filters.prioridade"></ces-select>
          <ces-select label="Base operacional" [options]="bases()" [(value)]="lista.filters.idBase" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Tipo de equipe" [options]="tipos()" [(value)]="lista.filters.idTipoEquipe" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Equipe" [options]="opcoesEquipe()" [(value)]="lista.filters.idEquipe" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Supervisor" [options]="opcoesSupervisor()" [(value)]="lista.filters.supervisor" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Coordenador" [options]="opcoesCoordenador()" [(value)]="lista.filters.coordenador" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Disponibilidade" [options]="opcoesEquipes" [(value)]="lista.filters.equipes"></ces-select>
          <ces-multiselect label="Situação" [options]="opcoesSituacao" [(value)]="lista.filters.situacoes" [filter]="false" placeholder="Ativas (sem canceladas)"></ces-multiselect>
          <ces-select label="Turno" [options]="opcoesTurno" [(value)]="lista.filters.turno"></ces-select>
          <ces-select label="Linha viva" [options]="opcoesSimNao" [(value)]="lista.filters.linhaViva"></ces-select>
          <ces-select label="Desligamento" [options]="opcoesSimNao" [(value)]="lista.filters.desligamento"></ces-select>
          <ces-select label="Finaliza obra" [options]="opcoesSimNao" [(value)]="lista.filters.finalizaObra"></ces-select>
          <ces-select label="Situação da intervenção" [options]="opcoesIntervencao" [(value)]="lista.filters.intervencao"></ces-select>
        </div>
      </ces-filter-panel>

      <section class="ces-kpis prog-kpis" aria-label="Indicadores do período" aria-live="polite">
        <div class="ces-kpi" data-tone="info">
          <span class="ces-kpi__icon"><i class="pi pi-calendar" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Programações no período</span>
            <span class="ces-kpi__value">{{ indicadores().programacoes }}</span>
            <span class="ces-kpi__detail">{{ moeda(indicadores().valor) }} em serviços</span></span>
        </div>
        <button type="button" class="ces-kpi" [attr.data-tone]="indicadores().atrasadas ? 'danger' : 'neutral'" [attr.aria-pressed]="soAtrasadas()" (click)="alternarAtrasadas()">
          <span class="ces-kpi__icon"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Atrasadas</span>
            <span class="ces-kpi__value">{{ indicadores().atrasadas }}</span>
            <span class="ces-kpi__detail">{{ soAtrasadas() ? 'Mostrar todas' : 'Passaram do fim sem concluir' }}</span></span>
        </button>
        <div class="ces-kpi" [attr.data-tone]="indicadores().livres ? 'success' : 'neutral'">
          <span class="ces-kpi__icon"><i class="pi pi-users" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Equipes livres</span>
            <span class="ces-kpi__value">{{ indicadores().livres }} de {{ indicadores().equipes }}</span>
            <span class="ces-kpi__detail">Sem programação no período</span></span>
        </div>
        <a class="ces-kpi" href="#obras-a-programar" (click)="irParaObras($event)" [attr.data-tone]="obras().length ? 'warning' : 'neutral'">
          <span class="ces-kpi__icon"><i class="pi pi-building" aria-hidden="true"></i></span>
          <span class="ces-kpi__body"><span class="ces-kpi__label">Obras a programar</span>
            <span class="ces-kpi__value">{{ obras().length }}</span>
            <span class="ces-kpi__detail">{{ moeda(pendenteTotal()) }} pendentes</span></span>
        </a>
      </section>

      @if (atrasadasAntes().length) {
        <section class="prog-atrasadas" aria-label="Atrasadas de períodos anteriores">
          <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
          <span>{{ atrasadasAntes().length === 1 ? 'Uma programação de antes deste período segue atrasada:' : atrasadasAntes().length + ' programações de antes deste período seguem atrasadas:' }}</span>
          @for (a of atrasadasAntes(); track a.id) {
            <button type="button" [title]="a.tituloObra" (click)="abrir(a.id)">#{{ a.numero }} · {{ a.numeroObra }} · até {{ a.fim }}</button>
          }
        </section>
      }

      <div class="ces-quick-filters prog-atalhos" role="group" aria-label="Mostrar só as situações escolhidas">
        <button type="button" class="ces-quick-filter" [attr.aria-pressed]="!situacoesAplicadas().length" (click)="definirSituacoes([])" title="Tudo, menos as canceladas">
          Ativas <span class="prog-contagem">{{ contagemAtivas() }}</span>
        </button>
        <button type="button" class="ces-quick-filter" [attr.aria-pressed]="emAbertoAtivo()" (click)="definirSituacoes(emAberto)" title="Programadas, em execução e atrasadas: o que ainda pede ação">
          <i class="pi pi-inbox" aria-hidden="true"></i> Em aberto <span class="prog-contagem" [class.prog-contagem--alerta]="contagemAberto() > 0">{{ contagemAberto() }}</span>
        </button>
        <span class="prog-divisor" aria-hidden="true"></span>
        @for (s of legenda; track s) {
          <button type="button" class="ces-quick-filter" [attr.aria-pressed]="situacoesAplicadas().includes(s)" [attr.data-tone]="situacoes[s].tom" (click)="alternarSituacao(s)"
            [attr.aria-label]="situacoes[s].rotulo + ': ' + contagens()[s]">
            <i [class]="situacoes[s].icone" aria-hidden="true"></i> {{ situacoes[s].rotulo }} <span class="prog-contagem">{{ contagens()[s] }}</span>
          </button>
        }
      </div>

      <div class="prog-barra">
        <nav class="prog-visoes" aria-label="Forma de visualizar">
          @for (v of visoes; track v.value) {
            <button type="button" [attr.aria-current]="visao === v.value ? 'page' : null" (click)="trocarVisao(v.value)">
              <i [class]="v.icon" aria-hidden="true"></i>{{ v.label }}
            </button>
          }
        </nav>
        <div class="prog-periodo">
          <ces-icon-button icon="pi pi-chevron-left" [label]="visao === 'semana' ? 'Semana anterior' : 'Mês anterior'" variant="ghost" (pressed)="navegar(-1)"></ces-icon-button>
          <h2 class="prog-periodo__titulo" aria-live="polite">{{ titulo() }}</h2>
          <ces-icon-button icon="pi pi-chevron-right" [label]="visao === 'semana' ? 'Próxima semana' : 'Próximo mês'" variant="ghost" (pressed)="navegar(1)"></ces-icon-button>
          <ces-button variant="outline" size="sm" label="Hoje" (pressed)="irParaHoje()"></ces-button>
        </div>
        @if (visao !== 'tabela') {
          <ces-text-input class="prog-busca" label="Buscar" [labelVisible]="false" placeholder="Buscar obra ou equipe" [(value)]="busca"></ces-text-input>
        }
      </div>

      @if (estado().erro; as mensagem) {
        <ces-async-state state="error" title="Não foi possível carregar a programação" [description]="mensagem"></ces-async-state>
      } @else if (visao === 'tabela') {
        <ces-data-table
          title="Programações"
          storageKey="obras-programacao"
          exportFileName="programacoes"
          searchPlaceholder="Obra, equipe ou número"
          rowActionLabel="Detalhes"
          rowActionIcon="pi pi-arrow-right"
          noResultsTitle="Nenhuma programação com esse recorte."
          [columns]="colunas"
          [rows]="linhasTabela()"
          [rowKey]="chave"
          [rowLabel]="rotulo"
          [state]="estado().carregando && !estado().gantt ? 'loading' : linhasTabela().length ? 'ready' : 'empty'"
          stateTitle="Nenhuma programação neste período"
          stateDescription="Selecione itens no planejamento de uma obra para programar, ou veja outro período."
          [filtered]="lista.filtered()"
          [(query)]="lista.query"
          (adjustFilters)="painel.expandAndFocus()"
          (clearFilters)="lista.reset()"
          (rowActivate)="abrir($event.id)"
        >
          <ng-template cesCell="situacao" let-row>
            <ces-status [tone]="situacaoDe(row).tom" [icon]="situacaoDe(row).icone">{{ situacaoDe(row).rotulo }}</ces-status>
          </ng-template>
          <ng-template cesCell="obra" let-row>
            <strong>{{ row.numeroObra }}</strong>
            <span class="ces-cell-muted">{{ row.tituloObra }}</span>
          </ng-template>
        </ces-data-table>
      } @else {
        <section class="prog-card">
          @if (estado().carregando && !estado().gantt) {
            <ces-async-state state="loading" title="Carregando a programação"></ces-async-state>
          } @else if (!linhasTempo().length) {
            <ces-async-state state="empty" icon="pi pi-calendar" title="Nenhuma equipe com esse recorte"
              description="Mude a busca ou os filtros para ver as equipes."></ces-async-state>
          } @else {
            <app-programacao-linha-tempo [linhas]="linhasTempo()" [inicio]="periodo().inicio" [fim]="periodo().fim" [modo]="$any(visao)" [podeEditar]="podeEditar" (abrir)="abrir($event)" (arrastada)="reprogramarArrastando($event)"></app-programacao-linha-tempo>
          }
          @if (podeEditar) {
            <p class="prog-dica"><i class="pi pi-arrows-alt" aria-hidden="true"></i> Arraste a barra para mudar o dia ou a equipe, e estique pelas pontas para mudar a duração. Esc cancela.</p>
          }
        </section>
      }

      <section id="obras-a-programar" class="prog-card prog-obras" aria-labelledby="obras-titulo">
        <header class="prog-obras__topo">
          <h2 id="obras-titulo">Obras a programar</h2>
          <p>As de prazo mais curto primeiro. Abra o planejamento, selecione os itens e programe.</p>
        </header>
        @if (!obras().length) {
          <ces-async-state state="empty" icon="pi pi-check-circle" title="Nenhuma obra com serviço por programar" [compact]="true"></ces-async-state>
        } @else {
          <ul class="prog-obras__lista">
            @for (o of obras(); track o.idObra) {
              <li>
                <a [routerLink]="['/obras', o.idObra, 'planejamento']" class="prog-obra">
                  <span class="prog-obra__nome"><strong>{{ o.numero }}</strong> <span>{{ o.titulo }}</span></span>
                  <span class="prog-obra__prazo" [attr.data-vencido]="vencida(o)">
                    <i class="pi" [class.pi-exclamation-triangle]="vencida(o)" [class.pi-clock]="!vencida(o)" aria-hidden="true"></i>
                    {{ prazoDe(o) ? (vencida(o) ? 'Venceu em ' : 'Até ') + prazoDe(o) : 'Sem prazo' }}
                  </span>
                  <span class="prog-obra__dado">{{ moeda(o.valorPendente) }}<small>{{ o.servicosPendentes }} {{ o.servicosPendentes === 1 ? 'serviço' : 'serviços' }}</small></span>
                  <span class="prog-obra__dado">{{ numero(o.percentualProgramado) }}%<small>programado</small></span>
                  @if (o.pontosSemKit) {
                    <span class="prog-obra__aviso"><i class="pi pi-exclamation-circle" aria-hidden="true"></i> {{ o.pontosSemKit }} sem kit</span>
                  } @else {
                    <span class="prog-obra__aviso prog-obra__aviso--vazio"></span>
                  }
                  <i class="pi pi-arrow-right prog-obra__seta" aria-hidden="true"></i>
                </a>
              </li>
            }
          </ul>
        }
      </section>
    </div>

    <app-programacao-detalhe [(aberto)]="detalheAberto" [idProgramacao]="idDetalhe()" (alterada)="recarregar()"></app-programacao-detalhe>
  `,
  styles: [`
    .prog-kpis { margin-bottom: 1rem; }
    button.ces-kpi { width: 100%; font: inherit; text-align: left; color: inherit; }
    button.ces-kpi[aria-pressed='true'] { box-shadow: inset 0 0 0 2px var(--ces-danger); }
    .prog-atrasadas { display: flex; flex-wrap: wrap; gap: 0.375rem 0.5rem; align-items: center; margin-bottom: 0.75rem; padding: 0.5rem 0.75rem; border: 1px solid var(--ces-danger); border-radius: var(--ces-radius-md); background: var(--ces-danger-soft); color: var(--ces-danger-text); font-size: var(--ces-font-control); }
    .prog-atrasadas button { padding: 0.125rem 0.5rem; border: 1px solid currentColor; border-radius: 999px; background: transparent; color: inherit; font: inherit; font-size: var(--ces-font-caption); font-weight: 600; cursor: pointer; }
    .prog-atrasadas button:hover { background: color-mix(in srgb, currentColor 12%, transparent); }
    .prog-atrasadas button:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 1px; }
    .prog-barra { display: flex; flex-wrap: wrap; gap: 0.5rem 1rem; align-items: center; margin-bottom: 0.75rem; }
    .prog-visoes { display: inline-flex; flex: none; padding: 2px; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface-muted); }
    .prog-visoes button { display: inline-flex; gap: 0.375rem; align-items: center; min-height: calc(var(--ces-control-height-sm) - 2px); padding: 0 0.75rem; border: 0; border-radius: calc(var(--ces-radius-md) - 2px); background: transparent; color: var(--ces-foreground-muted); font: inherit; font-size: var(--ces-font-control); font-weight: 600; cursor: pointer; }
    .prog-visoes button:hover { color: var(--ces-foreground); }
    .prog-visoes button[aria-current='page'] { background: var(--ces-surface); color: var(--ces-foreground); box-shadow: var(--ces-shadow-sm); }
    .prog-visoes button:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 1px; }
    .prog-visoes .pi { font-size: 0.8rem; }
    .prog-periodo { display: flex; gap: 0.25rem; align-items: center; }
    .prog-periodo__titulo { min-width: 13rem; margin: 0; color: var(--ces-foreground); font-size: var(--ces-font-title); font-weight: 700; text-align: center; }
    .prog-periodo ces-button { margin-left: 0.5rem; }
    .prog-busca { width: 16rem; margin-left: auto; }
    .prog-card { overflow: hidden; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-xl); background: var(--ces-surface); box-shadow: var(--ces-shadow-sm); }
    .prog-dica { display: flex; gap: 0.375rem; align-items: center; margin: 0; padding: 0.5rem 0.75rem 0; color: var(--ces-foreground-muted); font-size: var(--ces-font-caption); }
    .prog-atalhos { margin-bottom: 0.75rem; }
    .prog-contagem { display: inline-flex; align-items: center; justify-content: center; min-width: 1.375rem; height: 1.25rem; padding: 0 0.375rem; border-radius: 999px; background: var(--ces-surface-muted); color: var(--ces-foreground-muted); font-size: 0.7rem; font-weight: 700; font-variant-numeric: tabular-nums; }
    .ces-quick-filter[aria-pressed='true'] .prog-contagem { background: var(--ces-surface); color: var(--ces-foreground); }
    .prog-contagem--alerta { background: var(--ces-warning-soft); color: var(--ces-warning-text); }
    .prog-divisor { width: 1px; align-self: stretch; margin: 0.25rem 0.125rem; background: var(--ces-border); }
    .prog-atalhos [data-tone='info'] .pi { color: var(--ces-info); }
    .prog-atalhos [data-tone='warning'] .pi { color: var(--ces-warning); }
    .prog-atalhos [data-tone='danger'] .pi { color: var(--ces-danger); }
    .prog-atalhos [data-tone='success'] .pi { color: var(--ces-success); }
    .prog-atalhos .pi { font-size: 0.75rem; }
    .prog-dica { padding-bottom: 0.5rem; }
    .prog-obras { margin-top: 1rem; scroll-margin-top: 1rem; }
    .prog-obras__topo { padding: 0.875rem 1rem; border-bottom: 1px solid var(--ces-border); }
    .prog-obras__topo h2 { margin: 0; color: var(--ces-foreground); font-size: var(--ces-font-title); font-weight: 700; }
    .prog-obras__topo p { margin: 0.125rem 0 0; color: var(--ces-foreground-muted); font-size: var(--ces-font-caption); }
    .prog-obras__lista { margin: 0; padding: 0; list-style: none; }
    .prog-obras__lista li + li { border-top: 1px solid var(--ces-border); }
    .prog-obra { display: grid; grid-template-columns: minmax(14rem, 1fr) 11rem 8rem 6rem 7rem 1rem; gap: 0.75rem; align-items: center; padding: 0.625rem 1rem; color: var(--ces-foreground); font-size: var(--ces-font-control); text-decoration: none; }
    .prog-obra:hover { background: var(--ces-surface-subtle); }
    .prog-obra:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: -2px; }
    .prog-obra__nome strong { margin-right: 0.5rem; }
    .prog-obra__nome { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .prog-obra__nome span { color: var(--ces-foreground-muted); }
    .prog-obra__prazo { display: inline-flex; gap: 0.375rem; align-items: center; color: var(--ces-foreground-muted); font-size: var(--ces-font-caption); }
    .prog-obra__prazo[data-vencido='true'] { color: var(--ces-danger-text); font-weight: 600; }
    .prog-obra__dado { display: flex; flex-direction: column; font-variant-numeric: tabular-nums; font-weight: 600; }
    .prog-obra__dado small { color: var(--ces-foreground-muted); font-size: 0.6875rem; font-weight: 400; }
    .prog-obra__aviso { display: inline-flex; gap: 0.25rem; align-items: center; color: var(--ces-warning-text); font-size: var(--ces-font-caption); }
    .prog-obra__seta { color: var(--ces-foreground-muted); font-size: 0.75rem; }
    @media (max-width: 60rem) {
      .prog-obra { grid-template-columns: 1fr auto; }
      .prog-obra__aviso--vazio, .prog-obra__seta { display: none; }
      .prog-busca { width: 100%; margin-left: 0; }
    }
    @media (max-width: 40rem) {
      .prog-visoes { width: 100%; }
      .prog-visoes button { flex: 1; justify-content: center; }
      .prog-periodo { width: 100%; justify-content: space-between; }
      .prog-periodo__titulo { min-width: 0; font-size: var(--ces-font-control); }
    }
  `]
})
export class ProgramacaoComponent {
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ObrasProgramacaoService);
  private readonly rh = inject(RhService);
  private readonly notificacoes = inject(NotificationService);
  protected readonly podeEditar = (inject(AuthService).currentUser()?.permissoes ?? []).includes('obras:programacao:write');

  protected readonly visao: VisaoProgramacao = this.rota.snapshot.data['modo'] === 'semana' || this.rota.snapshot.data['modo'] === 'mes' ? this.rota.snapshot.data['modo'] : 'tabela';
  protected readonly visoes = VISOES;
  protected readonly situacoes = SITUACAO_PROGRAMACAO;
  protected readonly legenda = SITUACOES;
  protected readonly opcoesSituacao = OPCOES_SITUACAO;
  protected readonly opcoesEquipes = OPCOES_EQUIPES;
  protected readonly moeda = formatarMoeda;
  protected readonly numero = formatarNumero;

  // Antes da lista: ela lê as opções ao interpretar os filtros da URL, já na criação.
  protected readonly bases = signal<CesSelectOption<string>[]>([]);
  protected readonly tipos = signal<CesSelectOption<string>[]>([]);
  protected readonly estado = signal<{ carregando: boolean; erro: string | null; gantt: GanttProgramacao | null }>({ carregando: true, erro: null, gantt: null });
  private readonly linhasDoPeriodo = computed(() => this.estado().gantt?.equipes ?? []);
  private readonly barrasDoPeriodo = computed(() => [...this.linhasDoPeriodo().flatMap((l) => l.programacoes), ...(this.estado().gantt?.atrasadasAntes ?? [])]);
  protected readonly opcoesEquipe = computed<CesSelectOption<string>[]>(() => this.linhasDoPeriodo().map((l) => ({ value: l.idEquipe, label: l.codigo })));
  protected readonly opcoesSupervisor = computed(() => opcoesDe(this.linhasDoPeriodo().map((l) => l.supervisor)));
  protected readonly opcoesCoordenador = computed(() => opcoesDe(this.linhasDoPeriodo().map((l) => l.coordenador)));
  protected readonly opcoesMunicipio = computed(() => opcoesDe(this.barrasDoPeriodo().map((b) => b.municipio)));
  protected readonly opcoesObra = computed<CesSelectOption<string>[]>(() => {
    const obras = new Map(this.barrasDoPeriodo().map((b) => [b.idObra, `${b.numeroObra} · ${b.tituloObra}`]));
    return [...obras].map(([value, label]) => ({ value, label })).sort((a, b) => a.label.localeCompare(b.label, 'pt-BR'));
  });
  protected readonly opcoesPrioridade = OPCOES_PRIORIDADE;
  protected readonly opcoesTurno = OPCOES_TURNO;
  protected readonly opcoesSimNao = OPCOES_SIM_NAO;
  protected readonly opcoesIntervencao = OPCOES_INTERVENCAO;
  protected readonly emAberto = SITUACOES_EM_ABERTO;

  protected readonly lista = cesListState({
    storageKey: 'obras-programacao',
    filters: {
      idBase: cesFilter.value<string | null>(null, { label: 'Base', param: 'base', options: () => this.bases() }),
      idTipoEquipe: cesFilter.value<string | null>(null, { label: 'Tipo de equipe', param: 'tipo', options: () => this.tipos() }),
      situacoes: cesFilter.list<SituacaoProgramacao>([], { label: 'Situação', param: 'situacao', itemLabel: (s) => SITUACAO_PROGRAMACAO[s].rotulo }),
      equipes: cesFilter.value<FiltroEquipes | null>(null, { label: 'Disponibilidade', param: 'equipes', options: () => OPCOES_EQUIPES }),
      idEquipe: cesFilter.value<string | null>(null, { label: 'Equipe', param: 'equipe', options: () => this.opcoesEquipe() }),
      supervisor: cesFilter.value<string | null>(null, { label: 'Supervisor', param: 'supervisor' }),
      coordenador: cesFilter.value<string | null>(null, { label: 'Coordenador', param: 'coordenador' }),
      idObra: cesFilter.value<string | null>(null, { label: 'Obra', param: 'obra', options: () => this.opcoesObra() }),
      municipio: cesFilter.value<string | null>(null, { label: 'Município', param: 'municipio' }),
      prioridade: cesFilter.value<string | null>(null, { label: 'Prioridade', param: 'prioridade', options: () => OPCOES_PRIORIDADE }),
      turno: cesFilter.value<TurnoProgramacao | null>(null, { label: 'Turno', param: 'turno', options: () => OPCOES_TURNO }),
      linhaViva: cesFilter.value<boolean | null>(null, { label: 'Linha viva', param: 'linhaViva', options: () => OPCOES_SIM_NAO }),
      desligamento: cesFilter.value<boolean | null>(null, { label: 'Desligamento', param: 'desligamento', options: () => OPCOES_SIM_NAO }),
      finalizaObra: cesFilter.value<boolean | null>(null, { label: 'Finaliza obra', param: 'finaliza', options: () => OPCOES_SIM_NAO }),
      intervencao: cesFilter.value<SituacaoIntervencao | null>(null, { label: 'Intervenção', param: 'intervencao', options: () => OPCOES_INTERVENCAO })
    }
  });

  // Os dados vêm do período, não da lista: isto só liga a gravação do recorte na URL a cada filtro aplicado.
  private readonly gravaRecorteNaUrl = this.lista.results<never>(() => of([]));
  protected readonly busca = signal('');
  protected readonly data = signal<Date>(dataDaUrl(this.rota.snapshot.queryParamMap.get('data')) ?? new Date());
  protected readonly periodo = computed(() => periodoDaVisao(this.visao, this.data()));
  protected readonly titulo = computed(() => tituloDoPeriodo(this.visao, this.periodo()));

  private readonly recarga = signal(0);
  protected readonly obras = signal<ObraAProgramar[]>([]);
  protected readonly pendenteTotal = computed(() => this.obras().reduce((soma, o) => soma + o.valorPendente, 0));

  protected readonly detalheAberto = signal(false);
  protected readonly idDetalhe = signal<string | null>(null);
  protected readonly situacoesAplicadas = computed(() => this.lista.applied().situacoes);
  protected readonly soAtrasadas = computed(() => {
    const s = this.situacoesAplicadas();
    return s.length === 1 && s[0] === 'ATRASADA';
  });
  protected readonly emAbertoAtivo = computed(() => {
    const s = this.situacoesAplicadas();
    return s.length === SITUACOES_EM_ABERTO.length && SITUACOES_EM_ABERTO.every((x) => s.includes(x));
  });

  private readonly consulta = computed(() => ({
    inicio: dataIso(this.periodo().inicio)!,
    fim: dataIso(this.periodo().fim)!,
    idBase: this.lista.applied().idBase,
    idTipoEquipe: this.lista.applied().idTipoEquipe,
    recarga: this.recarga()
  }));

  private readonly filtrosBarra = computed<FiltrosBarra>(() => {
    const f = this.lista.applied();
    return { idObra: f.idObra, municipio: f.municipio, prioridade: f.prioridade, turno: f.turno, linhaViva: f.linhaViva, desligamento: f.desligamento, finalizaObra: f.finalizaObra, intervencao: f.intervencao };
  });

  private atendeEquipe(idEquipe: string, supervisor?: string | null, coordenador?: string | null): boolean {
    const f = this.lista.applied();
    return (!f.idEquipe || f.idEquipe === idEquipe) && (!f.supervisor || f.supervisor === supervisor) && (!f.coordenador || f.coordenador === coordenador);
  }

  /** O recorte sem a situação: é dele que saem as contagens dos atalhos, para o número não sumir ao filtrar. */
  private readonly linhasDoRecorte = computed<LinhaGantt[]>(() =>
    this.linhasDoPeriodo()
      .filter((linha) => this.atendeEquipe(linha.idEquipe, linha.supervisor, linha.coordenador))
      .map((linha) => ({ ...linha, programacoes: linha.programacoes.filter((p) => atendeFiltrosBarra(p, this.filtrosBarra())) }))
  );

  protected readonly atrasadasAntes = computed(() => {
    const f = this.lista.applied();
    const linhas = new Map(this.linhasDoPeriodo().map((l) => [l.idEquipe, l]));
    return (this.estado().gantt?.atrasadasAntes ?? []).filter((p) => {
      const linha = linhas.get(p.idEquipe);
      return atendeFiltrosBarra(p, this.filtrosBarra()) && this.atendeEquipe(p.idEquipe, linha?.supervisor, linha?.coordenador)
        && (!f.situacoes.length || f.situacoes.includes('ATRASADA'));
    });
  });

  protected readonly contagens = computed<Record<SituacaoProgramacao, number>>(() => {
    const contagem: Record<SituacaoProgramacao, number> = { PROGRAMADA: 0, ATRASADA: 0, EM_ANDAMENTO: 0, CONCLUIDA: 0, CANCELADA: 0 };
    for (const linha of this.linhasDoRecorte()) for (const p of linha.programacoes) contagem[p.situacao]++;
    contagem.ATRASADA += (this.estado().gantt?.atrasadasAntes ?? []).filter((p) => atendeFiltrosBarra(p, this.filtrosBarra())).length;
    return contagem;
  });
  protected readonly contagemAberto = computed(() => SITUACOES_EM_ABERTO.reduce((soma, s) => soma + this.contagens()[s], 0));
  protected readonly contagemAtivas = computed(() => this.contagemAberto() + this.contagens().CONCLUIDA);

  /** Linhas com as barras que os filtros deixam ver. Filtrando a programação, equipe sem barra sai: não é "livre", é fora do recorte. */
  private readonly linhasFiltradas = computed<LinhaGantt[]>(() => {
    const { situacoes, equipes } = this.lista.applied();
    const filtrandoProgramacao = situacoes.length > 0 || temFiltroBarra(this.filtrosBarra());
    return this.linhasDoRecorte()
      .map((linha) => ({ ...linha, programacoes: linha.programacoes.filter((p) => atendeSituacao(p.situacao, situacoes)) }))
      .filter((linha) => !equipes || (equipes === 'LIVRES') === !linha.programacoes.length)
      .filter((linha) => !filtrandoProgramacao || linha.programacoes.length > 0);
  });

  protected readonly linhasTempo = computed(() => {
    const termo = this.busca().trim().toLowerCase();
    if (!termo) return this.linhasFiltradas();
    return this.linhasFiltradas()
      .map((linha) => {
        if (linha.codigo.toLowerCase().includes(termo)) return linha;
        return { ...linha, programacoes: linha.programacoes.filter((p) => `${p.numero} ${p.numeroObra} ${p.tituloObra}`.toLowerCase().includes(termo)) };
      })
      .filter((linha) => linha.programacoes.length || linha.codigo.toLowerCase().includes(termo));
  });

  protected readonly linhasTabela = computed<LinhaTabela[]>(() =>
    [
      ...this.linhasFiltradas().flatMap((linha) => linha.programacoes.map((p) => ({ ...p, codigoEquipe: linha.codigo, base: linha.base }))),
      ...this.atrasadasAntes().map((p) => ({ ...p, codigoEquipe: p.codigoEquipe ?? '—', base: this.linhasDoPeriodo().find((l) => l.idEquipe === p.idEquipe)?.base }))
    ]
      .sort((a, b) => parseDataBrParaDate(a.inicio)!.getTime() - parseDataBrParaDate(b.inicio)!.getTime() || a.numero - b.numero)
  );

  protected readonly indicadores = computed(() => {
    const linhas = this.linhasDoRecorte();
    const linhasDaEquipe = this.linhasDoPeriodo().filter((l) => this.atendeEquipe(l.idEquipe, l.supervisor, l.coordenador));
    const abertas = linhas.flatMap((l) => l.programacoes).filter((p) => p.situacao !== 'CANCELADA');
    return {
      programacoes: abertas.length,
      valor: abertas.reduce((soma, p) => soma + p.valor, 0),
      atrasadas: this.contagens().ATRASADA,
      // Livre é quem não tem nada no período: os filtros da programação não mudam isso, só os da equipe.
      equipes: linhasDaEquipe.length,
      livres: linhasDaEquipe.filter((l) => !l.programacoes.some((p) => p.situacao !== 'CANCELADA')).length
    };
  });

  protected readonly colunas: CesDataTableColumn<LinhaTabela>[] = [
    { key: 'numero', header: 'Nº', type: 'integer', width: '5rem', priority: 1, value: (p) => p.numero, display: (p) => `#${p.numero}` },
    { key: 'obra', header: 'Obra', type: 'custom', priority: 1, value: (p) => `${p.numeroObra} ${p.tituloObra}` },
    { key: 'codigoEquipe', header: 'Equipe', priority: 1, value: (p) => p.codigoEquipe },
    { key: 'base', header: 'Base', value: (p) => p.base },
    { key: 'inicio', header: 'Início', priority: 1, value: (p) => parseDataBrParaDate(p.inicio)?.getTime(), display: (p) => p.inicio },
    { key: 'fim', header: 'Fim', value: (p) => parseDataBrParaDate(p.fim)?.getTime(), display: (p) => p.fim },
    { key: 'turno', header: 'Turno', value: (p) => ROTULO_TURNO[p.turno] },
    { key: 'situacao', header: 'Situação', type: 'custom', priority: 1, value: (p) => SITUACAO_PROGRAMACAO[p.situacao].rotulo },
    { key: 'municipio', header: 'Município', value: (p) => p.municipio },
    { key: 'itens', header: 'Itens', type: 'integer', width: '5rem', value: (p) => p.itens },
    { key: 'valor', header: 'Valor', type: 'currency', value: (p) => p.valor }
  ];
  protected readonly chave = (p: LinhaTabela): string => p.id;
  protected readonly rotulo = (p: LinhaTabela): string => `Programação ${p.numero}, obra ${p.numeroObra}`;

  constructor() {
    combineLatest([toObservable(this.consulta)])
      .pipe(
        takeUntilDestroyed(),
        switchMap(([{ recarga: _recarga, ...filtro }]) => this.api.gantt(filtro).pipe(
          map((gantt) => ({ carregando: false, erro: null as string | null, gantt })),
          catchError((e) => of({ carregando: false, erro: getErrorMessage(e), gantt: null })),
          startWith({ carregando: true, erro: null as string | null, gantt: untracked(() => this.estado().gantt) })
        ))
      )
      .subscribe((estado) => this.estado.set(estado));

    effect(() => {
      const idBase = this.lista.applied().idBase;
      this.recarga();
      untracked(() => this.api.obrasAProgramar(idBase).pipe(catchError(() => of([]))).subscribe((obras) => this.obras.set(obras)));
    });

    this.rh.listarBasesOperacionaisAtivas().pipe(takeUntilDestroyed(), catchError(() => of({ content: [] }))).subscribe((r) =>
      this.bases.set((r?.content ?? []).map((b: { id: string; nome: string }) => ({ value: b.id, label: b.nome })))
    );
    this.rh.listarTiposEquipeAtivos().pipe(takeUntilDestroyed(), catchError(() => of({ content: [] }))).subscribe((r) =>
      this.tipos.set((r?.content ?? []).map((t: { id: string; nome: string }) => ({ value: t.id, label: t.nome })))
    );
  }

  protected situacaoDe(linha: BarraGantt) {
    return SITUACAO_PROGRAMACAO[linha.situacao];
  }

  private irParaData(data: Date): void {
    this.data.set(data);
    void this.router.navigate([], { relativeTo: this.rota, queryParams: { data: dataIso(data) }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  protected navegar(sentido: 1 | -1): void {
    this.irParaData(deslocarVisao(this.visao, this.data(), sentido));
  }

  protected irParaHoje(): void {
    this.irParaData(new Date());
  }

  protected trocarVisao(visao: VisaoProgramacao): void {
    if (visao === this.visao) return;
    void this.router.navigate([VISOES.find((v) => v.value === visao)!.rota], { queryParams: { ...this.lista.queryParams(), data: dataIso(this.data()) } });
  }

  protected abrirHistorico(): void {
    void this.router.navigate(['/obras/programacao/historico']);
  }

  protected alternarAtrasadas(): void {
    this.definirSituacoes(this.soAtrasadas() ? [] : ['ATRASADA']);
  }

  protected definirSituacoes(situacoes: readonly SituacaoProgramacao[]): void {
    this.lista.set('situacoes', [...situacoes]);
  }

  /** Atalho: liga ou desliga a situação, somando às já escolhidas. */
  protected alternarSituacao(situacao: SituacaoProgramacao): void {
    const atuais = this.situacoesAplicadas();
    const proximas = atuais.includes(situacao) ? atuais.filter((s) => s !== situacao) : [...atuais, situacao];
    this.definirSituacoes(SITUACOES.filter((s) => proximas.includes(s)));
  }

  protected irParaObras(evento: Event): void {
    evento.preventDefault();
    document.getElementById('obras-a-programar')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  protected abrir(id: string): void {
    this.idDetalhe.set(id);
    this.detalheAberto.set(true);
  }

  protected recarregar(): void {
    this.recarga.update((n) => n + 1);
  }

  /**
   * Arrastar grava na hora, com "Desfazer" na notificação: abrir um formulário a cada ajuste de agenda tiraria o sentido.
   * O histórico guarda quem, quando, a equipe e o período de antes e de depois.
   */
  protected reprogramarArrastando(a: ArrasteProgramacao): void {
    const { barra } = a;
    this.api.reprogramar(barra.id, { idEquipe: a.idEquipe, inicio: a.inicio, fim: a.fim, observacao: 'Ajuste arrastando na linha do tempo', versao: barra.versao }).subscribe({
      next: (atualizada) => {
        this.recarregar();
        const equipe = this.estado().gantt?.equipes.find((l) => l.idEquipe === a.idEquipe);
        const cruzamentos = (equipe?.programacoes ?? []).filter((p) => p.id !== barra.id && p.situacao !== 'CANCELADA' && p.inicio !== undefined
          && parseDataBrParaDate(p.inicio)! <= parseDataBrParaDate(a.fim)! && parseDataBrParaDate(p.fim)! >= parseDataBrParaDate(a.inicio)!);
        const desfazer = {
          label: 'Desfazer',
          run: () => this.api.reprogramar(barra.id, { idEquipe: a.idEquipeOrigem, inicio: barra.inicio, fim: barra.fim, observacao: 'Desfeito o ajuste arrastando', versao: atualizada.versao }).subscribe({
            next: () => this.recarregar(),
            error: (e) => this.notificacoes.error(getErrorMessage(e))
          })
        };
        const texto = `#${barra.numero}: ${atualizada.codigoEquipe}, ${a.inicio} a ${a.fim}.`;
        if (cruzamentos.length) {
          this.notificacoes.warning(`${texto} A equipe fica com outra programação nesses dias (${cruzamentos.map((p) => `#${p.numero}`).join(', ')}).`, { action: desfazer, life: 15000 });
        } else {
          this.notificacoes.success(texto, { action: desfazer, life: 15000 });
        }
      },
      error: (e) => {
        this.recarregar();
        this.notificacoes.error(e?.status === 409 ? 'Outra pessoa alterou esta programação. Atualizei a tela: confira e tente de novo.' : getErrorMessage(e));
      }
    });
  }

  protected prazoDe(obra: ObraAProgramar): string | null {
    return obra.prazoRegulado ?? obra.prazoPrevisto ?? null;
  }

  protected vencida(obra: ObraAProgramar): boolean {
    const prazo = parseDataBrParaDate(this.prazoDe(obra));
    return !!prazo && prazo < new Date(new Date().setHours(0, 0, 0, 0));
  }
}
