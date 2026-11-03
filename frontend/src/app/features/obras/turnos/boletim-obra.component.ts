import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesChartComponent,
  CesChartPointSelection,
  CesDatepickerComponent,
  CesDrillStateService,
  CesFilterChip,
  CesFilterPanelComponent,
  CesMeterComponent,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesSelectOption,
  CesStatusComponent
} from '@ces/ui';
import { take } from 'rxjs';
import { createUrlFilters, qp } from '../../../core/navigation';
import { getErrorMessage } from '../../../core/models/shared.models';
import { BoletimEquipes, EquipeBoletim } from '../../../core/models/obras-turnos.models';
import { ObrasTurnosService } from '../../../core/services/obras-turnos.service';
import { atualizarPeriodicamente } from '../../../shared/components/dashboard/atualizacao-automatica';
import { DashboardAnaliseComponent } from '../../../shared/components/dashboard/dashboard-analise.component';
import { AlvoAnalise, VistaAnalise } from '../../../shared/components/dashboard/dashboard-analise.models';
import { NavegacaoAnaliseService } from '../../../shared/components/dashboard/dashboard-analise.navegacao';
import { DashboardMetricCardComponent } from '../../../shared/components/dashboard/dashboard-ui.components';
import { DashboardVisaoGerencialComponent } from '../../../shared/components/dashboard/dashboard-visao-gerencial.component';
import {
  CopilotoAreaComponent,
  CopilotoDashboardContext,
  CopilotoGatilhoComponent,
  CopilotoPainelService,
  CopilotoPerguntarComponent,
  filtroPainel,
  graficoPainel,
  metricaPainel,
  periodoPainel
} from '../../copiloto/painel';
import { SITUACAO_TURNO, dataLocal, duracao, horaCurta, isoLocal, moedaCompacta, nomeCurto, percentual, tomDaMeta } from '../../execucao/dashboards/execucao-dashboards.utils';
import { ProgramacaoDetalheComponent } from '../programacao/programacao-detalhe.component';
import { AgrupamentoBoletim, FiltroBoletim, agendaDoBoletim, agruparEquipes, agendaEmAberto, atendeFiltro, ordenarEquipes, resumirBoletim, serieDoBoletim, temAprPendente, vistaDoBoletim, visaoDoBoletim } from './boletim-obra.logic';
import { LIBERACAO } from './turnos-obra.logic';

type VisaoEquipes = 'cartoes' | 'miniaturas';

const AGRUPAMENTOS: readonly { valor: AgrupamentoBoletim; rotulo: string }[] = [
  { valor: 'nenhum', rotulo: 'Sem grupo' },
  { valor: 'base', rotulo: 'Base' },
  { valor: 'tipo', rotulo: 'Tipo de equipe' },
  { valor: 'meta', rotulo: 'Meta do dia' }
];

/** Agrupamento e visão dos cartões são preferência de quem vê, guardadas neste navegador. */
const PREFERENCIA_AGRUPAMENTO = 'boletim-obras:agrupar';
const PREFERENCIA_VISAO = 'boletim-obras:visao';
const ATUALIZACAO_AUTOMATICA_MS = 2 * 60_000;

function lerPreferencia<T extends string>(chave: string, validos: readonly T[], padrao: T): T {
  try {
    const valor = localStorage.getItem(chave);
    return validos.includes(valor as T) ? (valor as T) : padrao;
  } catch {
    return padrao;
  }
}

function gravarPreferencia(chave: string, valor: string): void {
  try {
    localStorage.setItem(chave, valor);
  } catch {
    // Sem armazenamento (janela anônima, bloqueio): a escolha vale só nesta visita.
  }
}

/**
 * Boletim das equipes de Obras: o dashboard de turnos no contexto do técnico. Mesma estrutura e mesmas peças, com
 * programado e executado no lugar das notas e a situação da APR de cada obra do dia.
 */
@Component({
  selector: 'app-boletim-obra',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesChartComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesMeterComponent,
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesStatusComponent,
    CopilotoAreaComponent,
    CopilotoGatilhoComponent,
    CopilotoPerguntarComponent,
    DashboardAnaliseComponent,
    DashboardMetricCardComponent,
    DashboardVisaoGerencialComponent,
    ProgramacaoDetalheComponent
  ],
  providers: [CesDrillStateService, NavegacaoAnaliseService],
  styleUrls: ['../../execucao/dashboards/execucao-dashboards.css', './boletim-obra.component.css'],
  template: `
    <app-copiloto-area [contexto]="contextoCopiloto()">
    <main class="dash-page">
      <div class="dash-shell">
        <ces-page-header title="Boletim das equipes" icon="pi pi-th-large" [breadcrumb]="[{ label: 'Relatórios' }, { label: 'Boletim das equipes' }]"
          description="Quem está em campo, com quem, se a APR está em dia e quanto foi programado e executado.">
          <app-copiloto-gatilho actions></app-copiloto-gatilho>
          @if (b(); as d) {
            <span actions class="dash-updated" [title]="d.hoje ? 'Atualiza sozinho a cada 2 minutos' : ''">Atualizado às {{ d.geradoEm | date: 'HH:mm' }}</span>
          }
          <ces-button actions label="Hoje" icon="pi pi-calendar" variant="outline" (pressed)="hoje()"></ces-button>
          <ces-button actions label="Turnos" icon="pi pi-calendar-clock" variant="secondary" (pressed)="irParaTurnos()"></ces-button>
          <ces-button actions label="Atualizar" icon="pi pi-refresh" [loading]="carregando()" (pressed)="aplicar()"></ces-button>
        </ces-page-header>

        <ces-filter-panel storageKey="boletim-obras" [activeCount]="chips().length" [summary]="chips()" [loading]="carregando()"
          (applied)="aplicar()" (cleared)="limpar()" (chipRemoved)="removerFiltro($event)">
          <div class="ces-filter-grid">
            <ces-datepicker label="Dia" inputId="boletim-dia" [value]="dia()" (valueChange)="dia.set($any($event))" [maxDate]="hojeLocal"></ces-datepicker>
            <ces-multiselect label="Base operacional" inputId="boletim-base" placeholder="Todas" [clearable]="true" selectedItemsLabel="{0} bases" [maxSelectedLabels]="2"
              [options]="opcoesBases()" [value]="idsBase()" (valueChange)="idsBase.set($event)"></ces-multiselect>
            <ces-multiselect label="Tipo de equipe" inputId="boletim-tipo" placeholder="Todos" [clearable]="true" selectedItemsLabel="{0} tipos" [maxSelectedLabels]="2"
              [options]="opcoesTipos()" [value]="tipos()" (valueChange)="tipos.set($event)"></ces-multiselect>
          </div>
        </ces-filter-panel>

        @if (carregando() && !b()) {
          <ces-async-state state="loading" title="Montando o boletim" description="Turnos, composição, APR, programado e executado de cada equipe."></ces-async-state>
        } @else if (erro() && !b()) {
          <ces-async-state state="error" title="Não foi possível montar o boletim" [description]="erro()!" actionLabel="Tentar novamente" actionIcon="pi pi-refresh" (action)="aplicar()"></ces-async-state>
        } @else if (b(); as d) {
          @if (visaoGerencial(); as vg) {
            <app-dashboard-visao-gerencial [veredito]="vg.veredito" [leituras]="vg.leituras" [copiloto]="copilotoDisponivel"
              (abrir)="analise.abrir($event.dimension, $event.value)" (perguntar)="perguntarVisao(vg.pergunta)" />
          }

          @let r = resumo();
          <section class="dash-metrics" aria-label="Resumo do dia" [attr.aria-busy]="carregando()">
            <app-dashboard-metric-card [label]="d.hoje ? 'Em turno agora' : 'Equipes com turno'" icon="pi-users" tone="primary"
              [value]="(d.hoje ? r.emTurno : r.emTurno + r.encerrados) + ' de ' + r.equipes"
              [progress]="r.equipes ? ((r.emTurno + r.encerrados) / r.equipes) * 100 : null"
              [detail]="r.encerrados + (r.encerrados === 1 ? ' encerrado' : ' encerrados') + ' · ' + r.semTurno + ' sem turno'"
              actionLabel="Analisar turnos" (activate)="analise.abrir('kpi', 'turnos')">
              <app-copiloto-perguntar metric-action [foco]="{ tipo: 'metrica', id: 'emTurno', rotulo: 'Turnos do dia' }" />
            </app-dashboard-metric-card>

            <app-dashboard-metric-card label="Executado no dia" icon="pi-wallet" [tone]="r.programado && r.executado >= r.programado ? 'green' : 'primary'"
              [value]="moeda(r.executado)" [progress]="r.programado ? (r.executado / r.programado) * 100 : null"
              [detail]="'Programado: ' + moeda(r.programado) + (r.percentualMeta != null ? ' · ' + percentual(r.percentualMeta) + ' da meta' : '')"
              actionLabel="Analisar execução" (activate)="analise.abrir('kpi', 'execucao')">
              <app-copiloto-perguntar metric-action [foco]="{ tipo: 'metrica', id: 'executado', rotulo: 'Executado no dia' }" />
            </app-dashboard-metric-card>

            <app-dashboard-metric-card label="APR pendente" icon="pi-shield" [tone]="r.aprPendente ? 'amber' : 'green'" [value]="r.aprPendente"
              [detail]="r.aprPendente ? 'Em turno, sem APR concluída nem motivo registrado' : 'Obras do dia com APR ou motivo registrado'"
              actionLabel="Ver equipes" (activate)="analise.abrir('kpi', 'apr-pendente')">
              <app-copiloto-perguntar metric-action [foco]="{ tipo: 'metrica', id: 'aprPendente', rotulo: 'APR pendente' }" />
            </app-dashboard-metric-card>

            <app-dashboard-metric-card label="Agendamento sem turno" icon="pi-user-minus" [tone]="r.agendadasSemTurno ? 'red' : 'slate'" [value]="r.agendadasSemTurno"
              [detail]="r.abertosDeOutroDia ? r.abertosDeOutroDia + ' turno(s) aberto(s) desde outro dia' : 'Têm programação no dia e não abriram'"
              actionLabel="Ver equipes" (activate)="analise.abrir('kpi', 'agendadas')">
              <app-copiloto-perguntar metric-action [foco]="{ tipo: 'metrica', id: 'agendadasSemTurno', rotulo: 'Agendamento sem turno' }" />
            </app-dashboard-metric-card>
          </section>

          @if (serie().rotulos.length) {
            <section class="dash-grid" aria-label="Gráfico de execução do dia">
              <article class="dash-panel dash-col-12">
                <header class="dash-panel__header">
                  <div>
                    <h2><i class="pi pi-chart-bar ed-panel-icon" aria-hidden="true"></i> Executado × programado por equipe</h2>
                    <p>Equipes com turno ou programação no dia, da maior execução para a menor. Clique numa equipe para analisá-la.</p>
                  </div>
                  <app-copiloto-perguntar [foco]="{ tipo: 'grafico', id: 'execucaoDoDia', rotulo: 'Executado × programado por equipe' }" />
                </header>
                <ces-chart kind="bar" title="Executado, programado e meta por equipe" [showTitle]="false" [labels]="serie().rotulos" [series]="serie().series" valueFormat="currency" [height]="280"
                  [selectable]="true" selectLabel="Analisar equipe" (pointSelect)="selecionarEquipe($event)"></ces-chart>
              </article>
            </section>
          }

          <section class="dash-grid" aria-label="Programação do dia">
            <article class="dash-panel dash-col-12">
              <header class="dash-panel__header">
                <div>
                  <h2><i class="pi pi-calendar ed-panel-icon" aria-hidden="true"></i> Programação do dia</h2>
                  <p>O que cada equipe tem para hoje e a situação da APR. Clique numa programação para ver os itens.</p>
                </div>
                <app-copiloto-perguntar [foco]="{ tipo: 'tabela', id: 'agenda', rotulo: 'Programação do dia' }" />
              </header>
              @if (agenda().length) {
                <div class="dash-table-wrap">
                  <table class="ed-tipos">
                    <caption class="ed-sr-only">Programações do dia por equipe</caption>
                    <thead>
                      <tr>
                        <th scope="col">Programação</th>
                        <th scope="col">Equipe</th>
                        <th scope="col">Serviço</th>
                        <th scope="col">APR</th>
                        <th scope="col" class="ed-tipos__fatia">Itens concluídos</th>
                        <th scope="col" class="ed-num">Valor</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (a of agenda(); track a.idProgramacao) {
                        <tr>
                          <th scope="row">
                            <button type="button" class="ed-tipos__abrir" (click)="verProgramacao(a.idProgramacao)" [attr.aria-label]="'Ver a programação ' + a.numero">
                              <span class="ed-tipos__codigo">#{{ a.numero }}</span> {{ a.numeroObra }} <i class="pi pi-angle-right" aria-hidden="true"></i>
                            </button>
                          </th>
                          <td>{{ a.codigoEquipe }}</td>
                          <td>{{ a.descricao }}</td>
                          <td>
                            @if (!agendaEmAberto(a)) {
                              <ces-status tone="success" icon="pi-check">Itens concluídos</ces-status>
                            } @else if (a.situacaoEquipe === 'SEM_TURNO') {
                              <ces-status tone="neutral">Sem turno</ces-status>
                            } @else {
                              <ces-status [tone]="liberacao[a.liberacao].tom" [icon]="liberacao[a.liberacao].icone.replace('pi ', '')">{{ liberacao[a.liberacao].rotulo }}</ces-status>
                            }
                          </td>
                          <td class="ed-tipos__fatia">
                            <div class="dash-progress">
                              <div class="dash-progress__track" aria-hidden="true"><span [style.width.%]="a.itens ? (a.itensConcluidos / a.itens) * 100 : 0"></span></div>
                              <strong>{{ a.itensConcluidos }} de {{ a.itens }}</strong>
                            </div>
                          </td>
                          <td class="ed-num">{{ moeda(a.valor) }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              } @else {
                <p class="ed-empty"><i class="pi pi-inbox" aria-hidden="true"></i> Nenhuma programação neste dia.</p>
              }
            </article>
          </section>

          <section class="ed-section" aria-labelledby="boletim-equipes-titulo">
            <header class="ed-section__header">
              <div>
                <h2 id="boletim-equipes-titulo"><i class="pi pi-users ed-panel-icon" aria-hidden="true"></i> Equipes · {{ diaPorExtenso(d.data) }}</h2>
                <p>Composição do turno, ou a da equipe quando ela ainda não abriu. O que pede ação aparece primeiro.</p>
              </div>
              <div class="dash-tabs" role="group" aria-label="Mostrar equipes">
                @for (f of filtros(); track f.valor) {
                  <button type="button" class="dash-tab" [class.is-active]="filtro() === f.valor" [attr.aria-pressed]="filtro() === f.valor" (click)="filtro.set(f.valor)">{{ f.rotulo }} ({{ f.total }})</button>
                }
              </div>
            </header>

            <div class="ed-equipes-toolbar">
              <div class="ed-equipes-toolbar__grupo">
                <span class="ed-equipes-toolbar__rotulo" id="boletim-agrupar">Agrupar por</span>
                <div class="dash-tabs" role="group" aria-labelledby="boletim-agrupar">
                  @for (a of agrupamentos; track a.valor) {
                    <button type="button" class="dash-tab" [class.is-active]="agrupamento() === a.valor" [attr.aria-pressed]="agrupamento() === a.valor" (click)="agrupar(a.valor)">{{ a.rotulo }}</button>
                  }
                </div>
              </div>
              <div class="dash-tabs" role="group" aria-label="Visualização das equipes">
                <button type="button" class="dash-tab" [class.is-active]="visao() === 'cartoes'" [attr.aria-pressed]="visao() === 'cartoes'" (click)="mudarVisao('cartoes')"><i class="pi pi-id-card" aria-hidden="true"></i> Cartões</button>
                <button type="button" class="dash-tab" [class.is-active]="visao() === 'miniaturas'" [attr.aria-pressed]="visao() === 'miniaturas'" (click)="mudarVisao('miniaturas')"><i class="pi pi-th-large" aria-hidden="true"></i> Miniaturas</button>
              </div>
            </div>

            @for (grupo of grupos(); track grupo.chave) {
              <section class="ed-grupo" [attr.aria-label]="agrupamento() === 'nenhum' ? 'Equipes' : grupo.rotulo">
                @if (agrupamento() !== 'nenhum') {
                  <header class="ed-grupo__head">
                    <h3><i [class]="grupo.icone" aria-hidden="true"></i> {{ grupo.rotulo }}</h3>
                    <ul class="ed-grupo__numeros">
                      <li>{{ grupo.equipes.length }} {{ grupo.equipes.length === 1 ? 'equipe' : 'equipes' }}</li>
                      <li>{{ grupo.emTurno }} em turno</li>
                      <li><strong>{{ moeda(grupo.executado) }}</strong></li>
                      @if (grupo.percentualMeta !== null) {
                        <li [attr.data-tone]="tomDaMeta(grupo.percentualMeta)"><strong>{{ percentual(grupo.percentualMeta) }}</strong> da meta</li>
                      }
                    </ul>
                  </header>
                }
                @if (visao() === 'miniaturas') {
                  <ul class="ed-minis">
                    @for (e of grupo.equipes; track e.idEquipe) {
                      <li>
                        <button type="button" class="ed-mini" [attr.aria-label]="resumoMiniatura(e)" [attr.data-situacao]="e.situacao" [attr.data-atencao]="atencao(e)" (click)="analise.abrir('equipe', e.idEquipe)">
                          <span class="ed-mini__topo">
                            <span class="ed-team__avatar ed-mini__avatar" [attr.data-situacao]="e.situacao" aria-hidden="true"><i class="pi pi-users"></i></span>
                            <span class="ed-mini__titulo">
                              <strong class="ed-mini__codigo">{{ e.codigo }}</strong>
                              <span class="ed-mini__situacao"><i [class]="situacaoTurno[e.situacao].icone" aria-hidden="true"></i>{{ situacaoCurta(e) }}</span>
                            </span>
                            @if (atencao(e)) { <i class="pi pi-exclamation-triangle ed-mini__alerta" aria-hidden="true"></i> }
                          </span>
                          <span class="ed-mini__numeros">
                            <span class="ed-mini__valor">{{ moeda(e.noDia.executado) }}</span>
                            <span class="ed-mini__notas">de {{ moeda(e.noDia.programado) }}</span>
                          </span>
                          <span class="ed-mini__meta" [attr.data-tone]="tomDaMeta(e.percentualMeta)">
                            <span class="ed-mini__barra" aria-hidden="true"><span [style.width.%]="e.percentualMeta ?? 0"></span></span>
                            <span>{{ e.percentualMeta == null ? 'Sem meta' : percentual(e.percentualMeta) + ' da meta' }}</span>
                          </span>
                          <span class="ed-mini__integrantes" [title]="nomes(e)"><i class="pi pi-user" aria-hidden="true"></i><span>{{ e.componentes.length ? nomes(e) : 'Sem componentes' }}</span></span>
                        </button>
                      </li>
                    }
                  </ul>
                } @else {
                  <div class="ed-grid">
                    @for (e of grupo.equipes; track e.idEquipe) {
                      <article class="ed-team" [attr.aria-labelledby]="'bol-' + e.idEquipe" [attr.data-atencao]="atencao(e)" [attr.data-apagada]="e.situacao === 'SEM_TURNO' && !e.agendamentoSemTurno">
                        <header class="ed-team__head">
                          <span class="ed-team__avatar" [attr.data-situacao]="e.situacao" aria-hidden="true"><i class="pi pi-users"></i></span>
                          <div class="ed-team__title">
                            <span class="ed-team__eyebrow">{{ e.tipoEquipe ?? 'Equipe' }}</span>
                            <h3 [id]="'bol-' + e.idEquipe">{{ e.codigo }}</h3>
                          </div>
                          <ces-status [tone]="situacaoTurno[e.situacao].tom" [icon]="situacaoTurno[e.situacao].icone">{{ rotuloSituacao(e, d.hoje) }}</ces-status>
                        </header>

                        <ul class="ed-team__tags" aria-label="Dados da equipe">
                          @if (e.baseOperacional) { <li title="Base operacional"><i class="pi pi-building" aria-hidden="true"></i>{{ e.baseOperacional }}</li> }
                          @if (e.placa) { <li title="Veículo"><i class="pi pi-car" aria-hidden="true"></i>{{ e.placa }}</li> }
                        </ul>

                        @if (e.agendamentoSemTurno) {
                          <p class="ed-alert" role="status"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i><span>{{ resumoAgenda(e) }}, sem turno aberto.</span></p>
                        }
                        @if (temAprPendente(e)) {
                          <p class="ed-alert" role="status"><i class="pi pi-shield" aria-hidden="true"></i><span>APR pendente: sem ela, o serviço não pode ser apontado.</span></p>
                        }
                        @if (e.abertoDesdeOutroDia) {
                          <p class="ed-alert" role="status"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i><span>Turno aberto desde {{ e.abertura | date: 'dd/MM HH:mm' }} e ainda não fechado.</span></p>
                        }

                        @if (e.situacao === 'SEM_TURNO' && !e.agendamentoSemTurno) {
                          <p class="ed-team__note ed-team__note--icon"><i class="pi pi-moon" aria-hidden="true"></i><span>{{ d.hoje ? 'A equipe ainda não abriu turno hoje.' : 'A equipe não abriu turno neste dia.' }}</span></p>
                        }

                        @if (e.situacao !== 'SEM_TURNO') {
                          @if (e.metaDiaria != null) {
                            <section class="ed-goal" [attr.data-tone]="tomDaMeta(e.percentualMeta)" [attr.aria-label]="'Meta do dia da equipe ' + e.codigo">
                              <div class="ed-goal__head">
                                <span class="ed-goal__label"><i class="pi pi-flag" aria-hidden="true"></i> Meta do dia</span>
                                <strong class="ed-goal__pct">{{ percentual(e.percentualMeta) }}</strong>
                              </div>
                              <ces-meter [value]="e.percentualMeta ?? 0" [label]="'Meta diária da equipe ' + e.codigo" [detail]="moeda(e.noDia.executado) + ' de ' + moeda(e.metaDiaria)"></ces-meter>
                              <ul class="ed-goal__rows">
                                @if (e.metaDiaria - e.noDia.executado > 0) {
                                  <li><i class="pi pi-arrow-up" aria-hidden="true"></i>Faltam <strong>{{ moeda(e.metaDiaria - e.noDia.executado) }}</strong> {{ d.hoje ? 'hoje' : 'no dia' }}</li>
                                } @else {
                                  <li><i class="pi pi-check" aria-hidden="true"></i>Meta do dia batida</li>
                                }
                              </ul>
                            </section>
                          } @else {
                            <section class="ed-goal" data-tone="neutral">
                              <div class="ed-goal__head"><span class="ed-goal__label"><i class="pi pi-flag" aria-hidden="true"></i> Meta do dia</span></div>
                              <p class="ed-goal__empty">Sem meta cadastrada para a equipe neste dia.</p>
                            </section>
                          }
                        }

                        <div class="ed-figures">
                          <div class="ed-figure"><span class="ed-figure__label"><i class="pi pi-calendar" aria-hidden="true"></i> Programado</span><strong>{{ moeda(e.noDia.programado) }}</strong></div>
                          <div class="ed-figure"><span class="ed-figure__label"><i class="pi pi-wallet" aria-hidden="true"></i> Executado</span><strong>{{ moeda(e.noDia.executado) }}</strong></div>
                          <div class="ed-figure"><span class="ed-figure__label"><i class="pi pi-stopwatch" aria-hidden="true"></i> Em turno</span><strong>{{ e.situacao === 'SEM_TURNO' ? '—' : duracao(e.minutosEmTurno) }}</strong></div>
                        </div>

                        <dl class="ed-team__facts">
                          <div><dt><i class="pi pi-chart-line" aria-hidden="true"></i> Até o dia</dt><dd>{{ moeda(e.noCiclo.executado) }} de {{ moeda(e.noCiclo.programado) }}</dd></div>
                          <div><dt><i class="pi pi-history" aria-hidden="true"></i> Dia útil anterior ({{ d.diaAnterior.slice(0, 5) }})</dt><dd>{{ moeda(e.diaAnterior.executado) }} de {{ moeda(e.diaAnterior.programado) }}</dd></div>
                        </dl>

                        @if (e.agenda.length) {
                          <div>
                            <span class="ed-subtitle"><i class="pi pi-calendar" aria-hidden="true"></i> Programação do dia</span>
                            <ul class="bol-agenda" [attr.aria-label]="'Programação do dia da equipe ' + e.codigo">
                              @for (a of e.agenda; track a.idProgramacao) {
                                <li>
                                  <button type="button" class="bol-agenda__link" [title]="a.descricao" (click)="verProgramacao(a.idProgramacao)">#{{ a.numero }} · {{ a.numeroObra }}</button>
                                  @if (!agendaEmAberto(a)) {
                                    <ces-status tone="success" icon="pi-check">Itens concluídos</ces-status>
                                  } @else if (e.situacao !== 'SEM_TURNO') { <ces-status [tone]="liberacao[a.liberacao].tom" [icon]="liberacao[a.liberacao].icone.replace('pi ', '')">{{ liberacao[a.liberacao].rotulo }}</ces-status> }
                                </li>
                              }
                            </ul>
                          </div>
                        }

                        <div>
                          <span class="ed-subtitle"><i class="pi pi-users" aria-hidden="true"></i> Componentes</span>
                          <ul class="bol-pessoas" aria-label="Componentes">
                            @for (m of e.componentes; track m.idColaborador) {
                              <li>
                                <span class="bol-avatar" aria-hidden="true">{{ iniciais(m.nome) }}</span>
                                <span class="bol-pessoa"><strong>{{ m.nome }}</strong><small>{{ m.funcao }}</small></span>
                                @if (m.lider) { <ces-status tone="info" icon="pi-star">Líder</ces-status> }
                                @if (m.substituto) { <ces-status tone="warning">Substituto</ces-status> }
                              </li>
                            } @empty {
                              <li class="ed-team__note">Sem componentes na equipe.</li>
                            }
                          </ul>
                        </div>

                        <div class="ed-team__actions">
                          <button type="button" class="ed-analisar" (click)="analise.abrir('equipe', e.idEquipe)"><i class="pi pi-chart-line" aria-hidden="true"></i> Analisar equipe</button>
                          @if (e.idTurno) {
                            <a class="ed-link" [routerLink]="['/obras/turnos', e.idTurno]"><i class="pi pi-calendar-clock" aria-hidden="true"></i> Ver turno <i class="pi pi-arrow-right" aria-hidden="true"></i></a>
                          }
                        </div>
                      </article>
                    }
                  </div>
                }
              </section>
            } @empty {
              <p class="ed-empty"><i class="pi pi-inbox" aria-hidden="true"></i>
                @if (noRecorte().length) { Nenhuma equipe nesta visão. Escolha "Todas" para ver o recorte completo. } @else { Nenhuma equipe no recorte. Ajuste a base ou o tipo de equipe. }
              </p>
            }
          </section>
        }
      </div>
    </main>
    </app-copiloto-area>

    <app-dashboard-analise [vista]="vistaAnalise()" [trilha]="analise.trilha()" [copiloto]="copilotoDisponivel"
      (fechar)="analise.fechar()" (navegar)="aprofundar($event)" (trocar)="analise.trocar($event)"
      (voltar)="analise.voltar($event)" (perguntar)="perguntarAnalise($event)" />

    <app-programacao-detalhe [(aberto)]="detalheAberto" [idProgramacao]="idDetalhe()" (alterada)="recarregar(true)"></app-programacao-detalhe>
  `
})
export class BoletimObraComponent implements OnInit {
  private readonly api = inject(ObrasTurnosService);
  private readonly router = inject(Router);
  private readonly copiloto = inject(CopilotoPainelService);
  protected readonly analise = inject(NavegacaoAnaliseService);

  protected readonly copilotoDisponivel = this.copiloto.disponivelParaUsuario();
  protected readonly situacaoTurno = SITUACAO_TURNO;
  protected readonly liberacao = LIBERACAO;
  protected readonly agrupamentos = AGRUPAMENTOS;
  protected readonly moeda = moedaCompacta;
  protected readonly percentual = percentual;
  protected readonly duracao = duracao;
  protected readonly tomDaMeta = tomDaMeta;
  protected readonly temAprPendente = temAprPendente;
  protected readonly agendaEmAberto = agendaEmAberto;
  protected readonly hojeLocal = new Date();

  protected readonly b = signal<BoletimEquipes | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly dia = signal<Date | null>(new Date());
  protected readonly idsBase = signal<string[]>([]);
  protected readonly tipos = signal<string[]>([]);
  private readonly aplicado = signal<{ dia: string; idsBase: string[]; tipos: string[] }>({ dia: '', idsBase: [], tipos: [] });
  protected readonly filtro = signal<FiltroBoletim>('todas');
  protected readonly agrupamento = signal<AgrupamentoBoletim>(lerPreferencia(PREFERENCIA_AGRUPAMENTO, AGRUPAMENTOS.map((a) => a.valor), 'nenhum'));
  protected readonly visao = signal<VisaoEquipes>(lerPreferencia(PREFERENCIA_VISAO, ['cartoes', 'miniaturas'], 'cartoes'));
  protected readonly detalheAberto = signal(false);
  protected readonly idDetalhe = signal<string | null>(null);
  private readonly analiseNoCopiloto = signal<VistaAnalise | null>(null);

  private readonly filtrosUrl = createUrlFilters<{ data: Date; base: string[]; tipo: string[] }>({
    data: qp.date('dia'),
    base: qp.list('base'),
    tipo: qp.list('tipo')
  });

  private readonly atualizacao = atualizarPeriodicamente(() => this.recarregar(true), {
    intervaloMs: ATUALIZACAO_AUTOMATICA_MS,
    pode: () => !!this.b()?.hoje && !this.carregando()
  });

  protected readonly noRecorte = computed(() => {
    const { idsBase, tipos } = this.aplicado();
    return (this.b()?.equipes ?? []).filter((e) => (!idsBase.length || (!!e.idBase && idsBase.includes(e.idBase))) && (!tipos.length || (!!e.tipoEquipe && tipos.includes(e.tipoEquipe))));
  });
  protected readonly resumo = computed(() => resumirBoletim(this.noRecorte()));
  protected readonly grupos = computed(() => agruparEquipes(ordenarEquipes(this.noRecorte().filter((e) => atendeFiltro(e, this.filtro()))), this.agrupamento()));
  protected readonly serie = computed(() => serieDoBoletim(this.noRecorte()));
  protected readonly agenda = computed(() => agendaDoBoletim(this.noRecorte()));
  protected readonly visaoGerencial = computed(() => {
    const d = this.b();
    return d ? visaoDoBoletim(d, this.resumo()) : null;
  });
  protected readonly filtros = computed(() => {
    const r = this.resumo();
    const lista: { valor: FiltroBoletim; rotulo: string; total: number }[] = [
      { valor: 'todas', rotulo: 'Todas', total: r.equipes },
      { valor: 'em-turno', rotulo: 'Em turno', total: r.emTurno },
      { valor: 'apr-pendente', rotulo: 'APR pendente', total: r.aprPendente },
      { valor: 'agendadas', rotulo: 'Agendamento sem turno', total: r.agendadasSemTurno },
      { valor: 'encerradas', rotulo: 'Encerradas', total: r.encerrados },
      { valor: 'sem-turno', rotulo: 'Sem turno', total: r.semTurno }
    ];
    return lista;
  });
  protected readonly opcoesBases = computed<CesSelectOption<string>[]>(() => {
    const mapa = new Map<string, string>();
    for (const e of this.b()?.equipes ?? []) if (e.idBase && e.baseOperacional) mapa.set(e.idBase, e.baseOperacional);
    return [...mapa].map(([value, label]) => ({ value, label })).sort((a, b) => a.label.localeCompare(b.label, 'pt-BR'));
  });
  protected readonly opcoesTipos = computed<CesSelectOption<string>[]>(() =>
    [...new Set((this.b()?.equipes ?? []).map((e) => e.tipoEquipe).filter((t): t is string => !!t))].sort().map((t) => ({ value: t, label: t }))
  );
  protected readonly chips = computed<CesFilterChip[]>(() => {
    const f = this.aplicado();
    const chips: CesFilterChip[] = [];
    if (this.b()) chips.push({ key: 'dia', label: `Dia: ${this.b()!.data}`, removable: false });
    for (const id of f.idsBase) chips.push({ key: `base:${id}`, label: `Base: ${this.opcoesBases().find((o) => o.value === id)?.label ?? id}` });
    for (const t of f.tipos) chips.push({ key: `tipo:${t}`, label: `Tipo: ${t}` });
    return chips;
  });
  protected readonly vistaAnalise = computed<VistaAnalise | null>(() => {
    const alvo = this.analise.alvo();
    const d = this.b();
    return alvo && d ? vistaDoBoletim(alvo, this.noRecorte(), d.data) : null;
  });
  protected readonly contextoCopiloto = computed<CopilotoDashboardContext | null>(() => {
    const d = this.b();
    const r = this.resumo();
    const f = this.aplicado();
    const equipes = this.noRecorte();
    return {
      id: 'obras.boletim',
      nome: 'Boletim das equipes de Obras',
      modulo: 'Obras',
      descricao: 'Situação das equipes de Obras num dia: em turno, encerrado ou sem turno; componentes; APR de cada obra; programado e executado contra a meta diária.',
      rota: '/obras/boletim',
      periodo: periodoPainel(this.diaAplicado(), this.diaAplicado()),
      filtros: [
        filtroPainel('baseOperacional', 'Base operacional', f.idsBase.map((id) => this.opcoesBases().find((o) => o.value === id)?.label ?? id).join(', ') || null, f.idsBase.join(',') || null),
        filtroPainel('tipoEquipe', 'Tipo de equipe', f.tipos.join(', ') || null, f.tipos.join(',') || null)
      ],
      metricas: d
        ? [
            metricaPainel('emTurno', 'Equipes em turno', r.emTurno, 'inteiro', { descricao: `de ${r.equipes} equipes` }),
            metricaPainel('semTurno', 'Equipes sem turno', r.semTurno, 'inteiro'),
            metricaPainel('agendadasSemTurno', 'Equipes com programação e sem turno', r.agendadasSemTurno, 'inteiro'),
            metricaPainel('aprPendente', 'Equipes com APR pendente', r.aprPendente, 'inteiro'),
            metricaPainel('programado', 'Programado no dia', r.programado, 'moeda'),
            metricaPainel('executado', 'Executado no dia', r.executado, 'moeda'),
            metricaPainel('percentualMeta', 'Meta diária atingida (equipes com meta)', r.percentualMeta, 'percentual', { comparacao: { rotulo: 'meta', valorAnterior: 100, melhorQuando: 'maior' } })
          ]
        : [],
      graficos: d
        ? [
            graficoPainel('equipes', 'tabela', 'Boletim por equipe', equipes.map((e) => e.codigo), [
              { nome: 'Programado (R$)', valores: equipes.map((e) => e.noDia.programado) },
              { nome: 'Executado (R$)', valores: equipes.map((e) => e.noDia.executado) },
              { nome: 'Minutos em turno', valores: equipes.map((e) => e.minutosEmTurno) }
            ], { metrica: 'programado e executado', dimensao: 'equipe', totalCategorias: equipes.length * 3 })
          ]
        : [],
      datasets: ['turnos', 'equipes'],
      sugestoes: [
        { rotulo: 'APR pendente', icone: 'pi pi-shield', requer: ['dados'], pergunta: 'Quais equipes estão em turno com APR pendente neste dia?' },
        { rotulo: 'Quem não abriu turno', icone: 'pi pi-user-minus', requer: ['dados'], pergunta: 'Quais equipes têm programação neste dia e não abriram turno?' }
      ]
    };
  });

  constructor() {
    // Link de análise que não existe mais no recorte (outro dia, outra base): fecha o painel.
    effect(() => {
      if (this.analise.alvo() && this.b() && !this.carregando() && !this.vistaAnalise()) this.analise.fechar();
    });
  }

  ngOnInit(): void {
    const lidos = this.filtrosUrl.read();
    if (lidos.data) this.dia.set(lidos.data);
    this.idsBase.set(lidos.base ?? []);
    this.tipos.set(lidos.tipo ?? []);
    this.aplicar();
  }

  private diaAplicado(): Date | null {
    return this.aplicado().dia ? dataLocal(this.aplicado().dia) : null;
  }

  protected aplicar(): void {
    this.filtrosUrl.write({ data: this.dia() ?? undefined, base: this.idsBase().length ? this.idsBase() : undefined, tipo: this.tipos().length ? this.tipos() : undefined });
    this.aplicado.set({ dia: isoLocal(this.dia() ?? new Date()), idsBase: this.idsBase(), tipos: this.tipos() });
    this.recarregar(false);
  }

  /** A base e o tipo filtram na tela: o boletim do dia já vem inteiro e as opções dos filtros saem dele. */
  recarregar(silencioso: boolean): void {
    this.carregando.set(true);
    if (!silencioso) this.erro.set(null);
    this.api.boletim(this.aplicado().dia).pipe(take(1)).subscribe({
      next: (boletim) => {
        this.b.set(boletim);
        this.erro.set(null);
        this.carregando.set(false);
      },
      error: (e) => {
        this.carregando.set(false);
        // Falha numa atualização automática não apaga o que já está em tela.
        if (!silencioso) this.erro.set(getErrorMessage(e));
      }
    });
  }

  protected hoje(): void {
    this.dia.set(new Date());
    this.aplicar();
  }

  protected limpar(): void {
    this.dia.set(new Date());
    this.idsBase.set([]);
    this.tipos.set([]);
    this.aplicar();
  }

  protected removerFiltro(chave: string): void {
    const separador = chave.indexOf(':');
    const dimensao = chave.slice(0, separador);
    const valor = chave.slice(separador + 1);
    if (dimensao === 'base') this.idsBase.update((ids) => ids.filter((i) => i !== valor));
    if (dimensao === 'tipo') this.tipos.update((tipos) => tipos.filter((t) => t !== valor));
    this.aplicar();
  }

  protected agrupar(criterio: AgrupamentoBoletim): void {
    this.agrupamento.set(criterio);
    gravarPreferencia(PREFERENCIA_AGRUPAMENTO, criterio);
  }

  protected mudarVisao(visao: VisaoEquipes): void {
    this.visao.set(visao);
    gravarPreferencia(PREFERENCIA_VISAO, visao);
  }

  protected aprofundar(alvo: AlvoAnalise): void {
    this.analise.aprofundar(alvo, this.vistaAnalise()?.titulo ?? '');
  }

  protected selecionarEquipe(selecao: CesChartPointSelection): void {
    const codigo = this.serie().rotulos[selecao.index];
    const equipe = this.noRecorte().find((e) => e.codigo === codigo);
    if (equipe) this.analise.abrir('equipe', equipe.idEquipe);
  }

  protected perguntarVisao(pergunta: string): void {
    this.copiloto.perguntarSobre({ tipo: 'secao', id: 'visaoGerencial', rotulo: 'Visão gerencial' }, pergunta);
  }

  protected perguntarAnalise(vista: VistaAnalise): void {
    if (!vista.copiloto) return;
    this.analiseNoCopiloto.set(vista);
    this.analise.fechar();
    this.copiloto.perguntarSobre(vista.copiloto.foco, vista.copiloto.pergunta);
  }

  protected verProgramacao(id: string): void {
    this.idDetalhe.set(id);
    this.detalheAberto.set(true);
  }

  protected atencao(e: EquipeBoletim): boolean {
    return e.agendamentoSemTurno || e.abertoDesdeOutroDia || temAprPendente(e);
  }

  protected rotuloSituacao(e: EquipeBoletim, hoje: boolean): string {
    switch (e.situacao) {
      case 'EM_TURNO':
        return e.abertoDesdeOutroDia ? 'Em turno' : `Em turno desde ${horaCurta(e.abertura)}`;
      case 'ENCERRADO':
        return `Encerrado às ${horaCurta(e.fechamento)}`;
      default:
        return hoje ? 'Não abriu turno' : 'Sem turno';
    }
  }

  protected situacaoCurta(e: EquipeBoletim): string {
    switch (e.situacao) {
      case 'EM_TURNO':
        return 'Em turno';
      case 'ENCERRADO':
        return `Encerrou ${horaCurta(e.fechamento)}`;
      default:
        return 'Sem turno';
    }
  }

  protected resumoAgenda(e: EquipeBoletim): string {
    const abertas = e.agenda.filter(agendaEmAberto);
    const obras = abertas.map((a) => a.numeroObra).join(', ');
    return abertas.length === 1 ? `Programação #${abertas[0].numero} na obra ${obras}` : `${abertas.length} programações nas obras ${obras}`;
  }

  protected resumoMiniatura(e: EquipeBoletim): string {
    const partes = [`Equipe ${e.codigo}`, SITUACAO_TURNO[e.situacao].rotulo, `executou ${moedaCompacta(e.noDia.executado)} de ${moedaCompacta(e.noDia.programado)} programados`];
    if (e.percentualMeta != null) partes.push(`${percentual(e.percentualMeta)} da meta do dia`);
    if (e.agendamentoSemTurno) partes.push('agendamento sem turno aberto');
    if (temAprPendente(e)) partes.push('APR pendente');
    if (e.abertoDesdeOutroDia) partes.push('turno aberto desde outro dia');
    return `${partes.join(', ')}. Analisar equipe.`;
  }

  protected nomes(e: EquipeBoletim): string {
    return e.componentes.map((m) => nomeCurto(m.nome)).join(', ');
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    return (partes[0][0] + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
  }

  protected diaPorExtenso(data: string): string {
    const [d, m, a] = data.split('/').map(Number);
    return new Date(a, m - 1, d).toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: '2-digit' });
  }

  protected irParaTurnos(): void {
    void this.router.navigate(['/obras/turnos']);
  }
}
