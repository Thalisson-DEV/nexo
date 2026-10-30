import { CommonModule, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, map } from 'rxjs';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { ChartModule } from 'primeng/chart';

import {
  BaseOperacionalResponse,
  CentroFaturamentoResponse,
  FuncaoResponse,
  ProcessoNegocioResponse,
} from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { TipoInspecaoResponse } from '../../../core/models/sst.models';
import {
  AcompanhamentoMetaFilter,
  AcompanhamentoMetaItem,
  AcompanhamentoMetasResponse,
  DirecaoOrdenacao,
  ESCOPO_META_LABELS,
  ORDENACAO_META_DIRECAO_INICIAL,
  ORDENACAO_META_LABELS,
  OrdenacaoAcompanhamentoMeta,
  PERIODICIDADE_LABELS,
  STATUS_META_LABELS,
  StatusMeta,
  formatarDataBr,
  toDataIso,
} from '../../../core/models/meta-inspecao.models';
import { CentrosCustoLookupService } from '../../../core/services/centros-custo-lookup.service';
import { MetasInspecaoService } from '../../../core/services/metas-inspecao.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { temSelecao } from '../../../core/utils/http-params.util';
import { DashboardMetricCardComponent } from '../../../shared/components/dashboard/dashboard-ui.components';
import {
  CesButtonComponent,
  CesDatepickerComponent,
  CesFilterChip,
  CesFilterPanelComponent,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  NotificationService
} from '@ces/ui';
import { createUrlFilters, qp } from '../../../core/navigation';
import { chipsDoRecorte, opcoesDe } from '../../../shared/components/dashboard/dashboard-filtros';
import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup } from '../../../core/lookup/store/remote-lookup';

@Component({
  selector: 'app-sst-acompanhamento-metas',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    DecimalPipe,
    SkeletonModule,
    TableModule,
    TooltipModule,
    DashboardMetricCardComponent,
    ChartModule,
    CesButtonComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesSelectComponent,
  ],
  template: `
    <main class="dash-page">
      <div class="dash-shell">
        <ces-page-header title="Acompanhamento das metas de inspeção" icon="pi pi-flag"
          [breadcrumb]="[{ label: 'Segurança' }, { label: 'Metas de inspeção' }, { label: 'Acompanhamento' }]"
          description="Quem está dentro e quem está fora da meta na janela vigente. O realizado conta as inspeções lançadas pela própria pessoa como inspetor.">
          @if (data(); as d) {
            <span actions class="dash-updated">Referência {{ formatarData(d.dataReferencia) }}</span>
          }
          <ces-button actions label="Cadastro de metas" icon="pi pi-cog" variant="secondary" (pressed)="irParaCadastro()"></ces-button>
          <ces-button actions label="Atualizar" icon="pi pi-refresh" [loading]="loading()" (pressed)="carregar()"></ces-button>
        </ces-page-header>

        <ces-filter-panel storageKey="dash-metas-inspecao" [activeCount]="chips().length" [summary]="chips()" [loading]="loading()"
          (applied)="carregar()" (cleared)="limpar()" (chipRemoved)="removerFiltro($event)">
          <div class="ces-filter-grid">
            <ces-datepicker label="Data de referência" placeholder="Hoje" [(value)]="dataReferencia" [clearable]="true"></ces-datepicker>
            <ces-multiselect label="Tipo de inspeção" [options]="opcoes.tipos()" [value]="$any(filtros.idTipoInspecao ?? [])" (valueChange)="filtros.idTipoInspecao = $event" placeholder="Todos"></ces-multiselect>
            <ces-multiselect label="Função" [options]="opcoes.funcoes()" [value]="$any(filtros.idFuncao ?? [])" (valueChange)="filtros.idFuncao = $event" placeholder="Todas"></ces-multiselect>
            <ces-multiselect label="Colaborador" [options]="colaboradores()" [value]="$any(filtros.idColaborador ?? [])" (valueChange)="filtros.idColaborador = $event"
              placeholder="Todos" [loading]="colaboradoresLoading()" (search)="searchColaboradores({ filter: $event })" description="Digite ao menos 2 letras do nome."></ces-multiselect>
            <ces-multiselect label="Centro de custo" [options]="opcoes.centros()" [value]="$any(filtros.idCentroCusto ?? [])" (valueChange)="filtros.idCentroCusto = $event" placeholder="Todos"></ces-multiselect>
            <ces-multiselect label="Base operacional" [options]="opcoes.bases()" [value]="$any(filtros.idBaseOperacional ?? [])" (valueChange)="filtros.idBaseOperacional = $event" placeholder="Todas"></ces-multiselect>
            <ces-multiselect label="Processo" [options]="opcoes.processos()" [value]="$any(filtros.idProcesso ?? [])" (valueChange)="filtros.idProcesso = $event" placeholder="Todos"></ces-multiselect>
            <ces-multiselect label="Situação" [options]="statusOptions" [value]="$any(filtros.status ?? [])" (valueChange)="filtros.status = $event" [filter]="false" placeholder="Todas"></ces-multiselect>
            <ces-select label="Ordenar por" [options]="ordenacaoOptions" [value]="ordenarPor" (valueChange)="aoTrocarOrdenacao($any($event))"></ces-select>
            <ces-button variant="outline" [icon]="direcao === 'ASC' ? 'pi pi-sort-amount-up' : 'pi pi-sort-amount-down'"
              [label]="direcao === 'ASC' ? 'Crescente' : 'Decrescente'" (pressed)="inverterDirecao()"></ces-button>
          </div>
        </ces-filter-panel>

        @if (loading()) {
          <div class="dash-metrics">
            @for (item of [1, 2, 3, 4]; track item) {
              <p-skeleton height="156px" borderRadius="16px" />
            }
          </div>
          <p-skeleton height="420px" borderRadius="16px" />
        } @else if (data(); as d) {
          <!--
            Os cartões usam a resposta SEM o filtro de situação: os totais são
            calculados só sobre o que passou pelos filtros, então com "Atingidas"
            aplicado os outros dois zerariam e o topo da tela perderia o sentido.
          -->
          @if (resumo(); as r) {
            <section class="dash-metrics" aria-label="Situação dos colaboradores">
              <app-dashboard-metric-card
                label="Não atingiram"
                [value]="r.naoAtingiram"
                icon="pi-times-circle"
                detail="Janela encerrada sem cumprir a meta"
                tone="red"
              />
              <app-dashboard-metric-card
                label="Em andamento"
                [value]="r.emAndamento"
                icon="pi-clock"
                detail="Janela aberta, ainda falta quantidade"
                tone="amber"
              />
              <app-dashboard-metric-card
                label="Atingiram"
                [value]="r.atingiram"
                icon="pi-check-circle"
                [detail]="'De ' + r.totalColaboradores + ' colaborador(es) com meta'"
                tone="primary"
                [progress]="r.percentualColaboradoresNaMeta"
              />
              <app-dashboard-metric-card
                label="Execução global"
                [value]="(r.percentualExecucaoGlobal | number: '1.0-1') + '%'"
                icon="pi-chart-bar"
                [detail]="r.somaRealizado + ' de ' + r.somaMetas + ' inspeções previstas'"
                tone="primary"
                [progress]="r.percentualExecucaoGlobal"
              />
            </section>
          }

          @if (filtrandoPorSituacao()) {
            <div class="dash-quality" role="status">
              <i class="pi pi-filter"></i>
              <div>
                <strong>Os cartões ignoram o filtro de situação</strong>
                <span
                  >Eles mostram o quadro completo do recorte atual; a tabela abaixo traz apenas
                  {{ situacoesSelecionadas() }}.</span
                >
              </div>
            </div>
          }

          @if (semAcesso() > 0) {
            <div class="dash-quality" role="status">
              <i class="pi pi-user-minus"></i>
              <div>
                <strong>{{ semAcesso() }} colaborador(es) sem usuário vinculado</strong>
                <span
                  >Eles não conseguem lançar inspeções: o zero em “Progresso” é falta de acesso, não
                  falta de trabalho.</span
                >
              </div>
            </div>
          }

          <section class="dash-panel">
            <header
              class="dash-panel__header flex-col md:flex-row gap-4 items-start md:items-center justify-between"
            >
              <div>
                <span class="dash-eyebrow">Cobrança</span>
                <h2>Metas por colaborador</h2>
                <p>
                  {{ descricaoOrdenacao() }} Cada colaborador aparece uma única vez, com a
                  precedência de meta já resolvida.
                </p>
              </div>
              <div class="dash-tabs" role="group" aria-label="Forma de exibição">
                @for (item of viewOptions; track item.value) {
                  <button type="button" class="dash-tab" [class.is-active]="selectedView === item.value"
                    [attr.aria-pressed]="selectedView === item.value" (click)="selectedView = item.value">
                    <i [class]="item.icon" aria-hidden="true"></i> {{ item.label }}
                  </button>
                }
              </div>
            </header>

            @if (selectedView === 'tabela') {
              <div class="dash-table-wrap">
                <p-table
                  [value]="d.itens"
                  [paginator]="d.itens.length > 12"
                  [rows]="12"
                  styleClass="dash-table meta-table"
                  [rowHover]="true"
                >
                  <ng-template pTemplate="header">
                    <tr>
                      <!--
                      A ordenação é do backend (ordenarPor + direcao): o endpoint
                      devolve a lista inteira já ordenada, então não usamos o sort do p-table.
                    -->
                      @for (coluna of colunas; track coluna.campo) {
                        <th [style.min-width.px]="coluna.largura">
                          @if (coluna.ordenacao; as campo) {
                            <button
                              type="button"
                              class="meta-sort"
                              [attr.aria-sort]="ariaSort(campo)"
                              (click)="ordenarPorColuna(campo)"
                            >
                              {{ coluna.titulo }}
                              <i [class]="iconeOrdenacao(campo)"></i>
                            </button>
                          } @else {
                            {{ coluna.titulo }}
                          }
                        </th>
                      }
                    </tr>
                  </ng-template>
                  <ng-template pTemplate="body" let-item>
                    <tr>
                      <td>
                        <div class="dash-table-name">
                          <div class="max-w-[180px] md:max-w-[250px]">
                            <strong
                              class="block truncate"
                              [pTooltip]="
                                item.nomeColaborador?.length > 22 ? item.nomeColaborador : undefined
                              "
                            >
                              {{ item.nomeColaborador || 'Cadastro não disponível' }}
                              @if (estaSemAcesso(item)) {
                                <i
                                  class="pi pi-user-minus meta-sem-acesso"
                                  pTooltip="Sem usuário vinculado — não consegue lançar inspeções"
                                ></i>
                              }
                            </strong>
                            <span
                              class="block truncate"
                              [pTooltip]="
                                item.nomeFuncao?.length + item.nomeBaseOperacional?.length > 30
                                  ? item.nomeFuncao + ' · ' + item.nomeBaseOperacional
                                  : undefined
                              "
                            >
                              {{ item.nomeFuncao || 'Função não informada' }} ·
                              {{ item.nomeBaseOperacional || 'Base não informada' }}
                            </span>
                          </div>
                        </div>
                      </td>
                      <td>
                        <div class="meta-progress">
                          <div class="meta-progress__top">
                            <strong
                              >{{ item.realizado }} <span>de {{ item.meta }}</span></strong
                            >
                            <span class="meta-progress__pct" [attr.data-status]="item.status"
                              >({{ item.percentualAtingimento | number: '1.0-0' }}%)</span
                            >
                          </div>
                          <div class="dash-progress__track">
                            <span
                              [style.width.%]="larguraBarra(item)"
                              [attr.data-status]="item.status"
                            ></span>
                          </div>
                          <span class="meta-progress__foot">
                            @if (item.saldo > 0) {
                              Faltam {{ item.saldo }} inspeç{{ item.saldo === 1 ? 'ão' : 'ões' }}
                            } @else {
                              Meta cumprida
                            }
                          </span>
                        </div>
                      </td>
                      <td>
                        <span class="meta-status" [attr.data-status]="item.status">{{
                          rotuloStatus(item)
                        }}</span>
                      </td>
                      <td class="text-[11px] text-slate-600">
                        {{ item.nomeCentroCusto || 'Sem centro de custo' }}
                      </td>
                      <td class="text-[11px] text-slate-600">
                        {{ item.nomeProcesso || 'Sem processo' }}
                      </td>
                      <td>
                        <div class="meta-prazo">
                          @if (item.diasRestantes > 0) {
                            <strong>{{ item.diasRestantes }} dia(s)</strong>
                          } @else {
                            <strong class="meta-prazo--encerrado">Janela encerrada</strong>
                          }
                          <span
                            >{{ formatarData(item.janelaInicio) }} a
                            {{ formatarData(item.janelaFim) }} ·
                            {{ rotuloPeriodicidade(item) }}
                            @if (item.nomeTipoInspecao) {
                              · {{ item.nomeTipoInspecao }}
                            }
                          </span>
                        </div>
                      </td>
                      <td>
                        <span class="meta-badge" [attr.data-origem]="item.origem">{{
                          rotuloOrigem(item)
                        }}</span>
                      </td>
                    </tr>
                  </ng-template>
                  <ng-template pTemplate="emptymessage">
                    <tr>
                      <td [attr.colspan]="colunas.length">
                        <div class="dash-empty">
                          <i class="pi pi-flag"></i>
                          Nenhuma meta vigente para os filtros selecionados.
                          <div class="pt-4">
                            <ces-button label="Cadastrar metas" icon="pi pi-plus" size="sm" (pressed)="irParaCadastro()"></ces-button>
                          </div>
                        </div>
                      </td>
                    </tr>
                  </ng-template>
                </p-table>
              </div>
            } @else {
              <div class="dash-chart-wrap p-4 w-full overflow-x-auto">
                <div
                  [style.min-width.px]="calculaLarguraGrafico(d.itens.length)"
                  [style.height.px]="400"
                >
                  <p-chart
                    type="bar"
                    [data]="chartData()"
                    [options]="chartOptions()"
                    height="100%"
                    width="100%"
                  ></p-chart>
                </div>
              </div>
            }
          </section>
        } @else {
          <div class="dash-panel dash-empty">
            <i class="pi pi-inbox"></i>Não há dados disponíveis para os filtros selecionados.
          </div>
        }
      </div>
    </main>
  `,
  styles: [
    `
      :host ::ng-deep {
        .meta-progress {
          display: grid;
          gap: 0.35rem;
        }
        .meta-progress__top {
          display: flex;
          align-items: baseline;
          justify-content: space-between;
          gap: 0.75rem;
        }
        .meta-progress__top strong {
          color: #102a43;
          font-size: 0.85rem;
          font-variant-numeric: tabular-nums;
        }
        .meta-progress__top strong span {
          color: #829ab1;
          font-size: 0.72rem;
          font-weight: 400;
        }
        .meta-progress__pct {
          font-size: 0.72rem;
          font-weight: 700;
          font-variant-numeric: tabular-nums;
        }
        .meta-progress__pct[data-status='ATINGIDA'] {
          color: #15803d;
        }
        .meta-progress__pct[data-status='EM_ANDAMENTO'] {
          color: #b45309;
        }
        .meta-progress__pct[data-status='NAO_ATINGIDA'] {
          color: #b91c1c;
        }
        .meta-progress__foot {
          color: #94a3b8;
          font-size: 0.66rem;
        }
        .meta-progress .dash-progress__track {
          width: 100%;
          height: 0.4rem;
          overflow: hidden;
          border-radius: 99px;
          background: #e9eff4;
        }
        .meta-progress .dash-progress__track span {
          display: block;
          height: 100%;
          border-radius: inherit;
          background: var(--p-primary-700);
        }
        .dash-progress__track span[data-status='ATINGIDA'] {
          background: #38a169;
        }
        .dash-progress__track span[data-status='EM_ANDAMENTO'] {
          background: #c57a16;
        }
        .dash-progress__track span[data-status='NAO_ATINGIDA'] {
          background: #c2413b;
        }

        .meta-prazo strong {
          display: block;
          color: #243b53;
          font-size: 0.78rem;
        }
        .meta-prazo strong.meta-prazo--encerrado {
          color: #94a3b8;
          font-weight: 600;
        }
        .meta-prazo span {
          display: block;
          margin-top: 0.15rem;
          color: #94a3b8;
          font-size: 0.65rem;
          line-height: 1.35;
        }

        .meta-badge {
          display: inline-block;
          padding: 0.2rem 0.55rem;
          border-radius: 0.4rem;
          font-size: 0.62rem;
          font-weight: 700;
          letter-spacing: 0.04em;
          text-transform: uppercase;
        }
        .meta-badge[data-origem='FUNCAO'] {
          background: #f1f5f9;
          color: #475569;
        }
        .meta-badge[data-origem='COLABORADOR'] {
          background: var(--p-primary-50);
          color: var(--p-primary-700);
        }
        .meta-status {
          display: inline-block;
          padding: 0.25rem 0.6rem;
          border-radius: 0.45rem;
          font-size: 0.65rem;
          font-weight: 700;
          letter-spacing: 0.03em;
          text-transform: uppercase;
        }
        .meta-status[data-status='ATINGIDA'] {
          background: #f0fdf4;
          color: #15803d;
        }
        .meta-status[data-status='EM_ANDAMENTO'] {
          background: #fffbeb;
          color: #b45309;
        }
        .meta-status[data-status='NAO_ATINGIDA'] {
          background: #fef2f2;
          color: #b91c1c;
        }
        .meta-sem-acesso {
          margin-left: 0.4rem;
          color: #b45309;
          font-size: 0.7rem;
        }

        .meta-sort {
          display: inline-flex;
          align-items: center;
          gap: 0.35rem;
          background: none;
          border: 0;
          padding: 0;
          font: inherit;
          color: inherit;
          cursor: pointer;
        }
        .meta-sort:hover {
          color: var(--p-primary-700);
        }
        .meta-sort i {
          font-size: 0.6rem;
          color: #cbd5e1;
        }
        .meta-sort i.meta-sort__ativo {
          color: var(--p-primary-700);
        }
        .meta-sort:focus-visible {
          outline: 2px solid var(--p-primary-400);
          outline-offset: 2px;
          border-radius: 0.25rem;
        }

        .dash-page .meta-table .p-datatable-table {
          min-width: 1180px;
        }
      }
    `,
  ],
})
export class AcompanhamentoMetasComponent implements OnInit {
  private readonly metasService = inject(MetasInspecaoService);
  private readonly rh = inject(RhService);
  private readonly sstService = inject(SstService);
  private readonly lookupStore = inject(LookupStore);
  private readonly centrosCustoLookup = inject(CentrosCustoLookupService);
  private readonly notificacoes = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly colaboradorLookup = new RemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () => this.filtros.idColaborador ?? [],
    destroyRef: this.destroyRef,
  });

  protected readonly loading = signal(false);
  /** Resposta filtrada — alimenta a tabela e o gráfico. */
  protected readonly data = signal<AcompanhamentoMetasResponse | null>(null);
  /** Resposta sem o filtro de situação — alimenta os cartões do topo. */
  protected readonly resumo = signal<AcompanhamentoMetasResponse | null>(null);
  protected readonly tiposInspecao = signal<TipoInspecaoResponse[]>([]);
  protected readonly funcoes = signal<FuncaoResponse[]>([]);
  protected readonly colaboradores = this.colaboradorLookup.items;
  protected readonly colaboradoresLoading = this.colaboradorLookup.loading;
  protected readonly bases = signal<BaseOperacionalResponse[]>([]);
  protected readonly centrosCusto = signal<CentroFaturamentoResponse[]>([]);
  protected readonly processos = signal<ProcessoNegocioResponse[]>([]);

  protected readonly formatarData = formatarDataBr;

  protected readonly statusOptions = [
    { label: STATUS_META_LABELS.NAO_ATINGIDA, value: 'NAO_ATINGIDA' as StatusMeta },
    { label: STATUS_META_LABELS.EM_ANDAMENTO, value: 'EM_ANDAMENTO' as StatusMeta },
    { label: STATUS_META_LABELS.ATINGIDA, value: 'ATINGIDA' as StatusMeta },
  ];

  protected readonly ordenacaoOptions = (
    Object.keys(ORDENACAO_META_LABELS) as OrdenacaoAcompanhamentoMeta[]
  ).map((value) => ({ label: ORDENACAO_META_LABELS[value], value }));

  /** Colunas da tabela; `ordenacao` ausente = coluna não clicável. */
  protected readonly colunas: {
    campo: string;
    titulo: string;
    largura: number;
    ordenacao?: OrdenacaoAcompanhamentoMeta;
  }[] = [
    { campo: 'colaborador', titulo: 'Colaborador', largura: 250, ordenacao: 'COLABORADOR' },
    { campo: 'progresso', titulo: 'Progresso na janela', largura: 250, ordenacao: 'PERCENTUAL' },
    { campo: 'situacao', titulo: 'Situação', largura: 130, ordenacao: 'STATUS' },
    { campo: 'centroCusto', titulo: 'Centro de custo', largura: 150, ordenacao: 'CENTRO_CUSTO' },
    { campo: 'processo', titulo: 'Processo', largura: 150, ordenacao: 'PROCESSO' },
    { campo: 'prazo', titulo: 'Prazo', largura: 160, ordenacao: 'DIAS_RESTANTES' },
    { campo: 'origem', titulo: 'Origem', largura: 110 },
  ];

  protected selectedView = 'tabela';
  protected viewOptions = [
    { label: 'Tabela', value: 'tabela', icon: 'pi pi-table' },
    { label: 'Gráfico', value: 'grafico', icon: 'pi pi-chart-bar' },
  ];

  protected dataReferencia: Date | null = null;
  protected filtros: AcompanhamentoMetaFilter = this.filtroVazio();

  /** Recorte na URL (link compartilhável e voltar do navegador). */
  private readonly filtrosUrl = createUrlFilters<{
    referencia: Date;
    tipo: string[];
    funcao: string[];
    colaborador: string[];
    centro: string[];
    base: string[];
    processo: string[];
    situacao: string[];
  }>({
    referencia: qp.date('referencia'),
    tipo: qp.list('tipo'),
    funcao: qp.list('funcao'),
    colaborador: qp.list('colaborador'),
    centro: qp.list('centro'),
    base: qp.list('base'),
    processo: qp.list('processo'),
    situacao: qp.list('situacao'),
  });
  /** Filtros da consulta que produziu `data()` — os chips mostram esse recorte. */
  private readonly filtrosAplicados = signal<AcompanhamentoMetaFilter>({});

  protected readonly opcoes = {
    tipos: computed(() => opcoesDe(this.tiposInspecao(), 'nome')),
    funcoes: computed(() => opcoesDe(this.funcoes(), 'nome')),
    centros: computed(() => opcoesDe(this.centrosCusto(), 'nome')),
    bases: computed(() => opcoesDe(this.bases(), 'nome')),
    processos: computed(() => opcoesDe(this.processos(), 'nome')),
  };

  protected readonly chips = computed<CesFilterChip[]>(() => {
    const f = this.filtrosAplicados();
    const chips = chipsDoRecorte(f as Record<string, unknown>, [
      { chave: 'idTipoInspecao', rotulo: 'Tipo', opcoes: this.opcoes.tipos() },
      { chave: 'idFuncao', rotulo: 'Função', opcoes: this.opcoes.funcoes() },
      { chave: 'idColaborador', rotulo: 'Colaborador', opcoes: this.colaboradores() },
      { chave: 'idCentroCusto', rotulo: 'Centro de custo', opcoes: this.opcoes.centros() },
      { chave: 'idBaseOperacional', rotulo: 'Base', opcoes: this.opcoes.bases() },
      { chave: 'idProcesso', rotulo: 'Processo', opcoes: this.opcoes.processos() },
      { chave: 'status', rotulo: 'Situação', opcoes: this.statusOptions },
    ]);
    if (f.dataReferencia) chips.unshift({ key: 'referencia', label: `Referência: ${formatarDataBr(f.dataReferencia)}` });
    return chips;
  });
  protected ordenarPor: OrdenacaoAcompanhamentoMeta = 'PERCENTUAL';
  protected direcao: DirecaoOrdenacao = 'ASC';

  /** Ordenação da consulta que produziu `data()` — é o que os cabeçalhos indicam. */
  private readonly ordenacaoAplicada = signal<{
    campo: OrdenacaoAcompanhamentoMeta;
    direcao: DirecaoOrdenacao;
  }>({ campo: 'PERCENTUAL', direcao: 'ASC' });
  private readonly statusAplicado = signal<StatusMeta[]>([]);

  protected readonly semAcesso = computed(
    () => (this.data()?.itens ?? []).filter((item) => !item.possuiUsuario).length,
  );

  protected readonly filtrandoPorSituacao = computed(() => this.statusAplicado().length > 0);

  protected readonly situacoesSelecionadas = computed(() =>
    this.statusAplicado()
      .map((status) => STATUS_META_LABELS[status].toLowerCase())
      .join(', '),
  );

  protected readonly descricaoOrdenacao = computed(() => {
    const { campo, direcao } = this.ordenacaoAplicada();
    const sentido = direcao === 'ASC' ? 'crescente' : 'decrescente';
    return `Ordenado por ${ORDENACAO_META_LABELS[campo].toLowerCase()} em ordem ${sentido}; o desempate é sempre o nome do colaborador.`;
  });

  protected readonly chartData = computed(() => {
    const dataInfo = this.data();
    if (!dataInfo || !dataInfo.itens) return null;

    const labels = dataInfo.itens.map((item) => this.formatarNomeParaGrafico(item.nomeColaborador));
    const realizados = dataInfo.itens.map((item) => item.realizado);
    const metas = dataInfo.itens.map((item) => item.meta);

    return {
      labels,
      datasets: [
        {
          type: 'bar',
          label: 'Realizado',
          data: realizados,
          backgroundColor: '#3b82f6', // Cor Primária
          borderRadius: 4,
          barPercentage: 0.5,
          categoryPercentage: 0.7,
        },
        {
          type: 'line',
          label: 'Meta',
          data: metas,
          borderColor: '#475569', // Slate 600
          backgroundColor: '#475569',
          borderWidth: 2,
          borderDash: [5, 5],
          pointRadius: 4,
          pointBackgroundColor: '#fff',
          pointBorderWidth: 2,
          tension: 0, // Linhas retas
          fill: false,
        },
      ],
    };
  });

  protected readonly chartOptions = computed(() => {
    return {
      maintainAspectRatio: false,
      indexAxis: 'x',
      plugins: {
        legend: {
          position: 'bottom',
          labels: {
            color: '#64748b',
            font: { family: 'Inter, sans-serif', size: 11, weight: '500' },
          },
        },
        tooltip: {
          mode: 'index',
          intersect: false,
          callbacks: {
            label: (context: any) => {
              let label = context.dataset.label || '';
              if (label) {
                label += ': ';
              }
              if (context.parsed.y !== null) {
                label += context.parsed.y;
              }
              return label;
            },
          },
        },
      },
      scales: {
        x: {
          grid: { display: false, drawBorder: false },
          ticks: {
            color: '#475569',
            font: { size: 10, weight: '500' },
            maxRotation: 0,
            minRotation: 0,
          },
        },
        y: {
          beginAtZero: true,
          grid: { color: '#f1f5f9', drawBorder: false },
          ticks: { color: '#64748b', font: { size: 10 } },
        },
      },
    };
  });

  ngOnInit(): void {
    const url = this.filtrosUrl.read();
    this.dataReferencia = url.referencia ?? null;
    this.filtros = {
      idTipoInspecao: url.tipo ?? [],
      idFuncao: url.funcao ?? [],
      idColaborador: url.colaborador ?? [],
      idCentroCusto: url.centro ?? [],
      idBaseOperacional: url.base ?? [],
      idProcesso: url.processo ?? [],
      status: (url.situacao ?? []) as StatusMeta[],
    };
    this.carregarLookups();
    this.carregar();
  }

  protected removerFiltro(chave: string): void {
    if (chave === 'referencia') this.dataReferencia = null;
    else this.filtros = { ...this.filtros, [chave]: [] };
    this.carregar();
  }

  protected carregar(): void {
    this.loading.set(true);
    const filtros: AcompanhamentoMetaFilter = {
      ...this.filtros,
      dataReferencia: this.dataReferencia ? toDataIso(this.dataReferencia) : null,
      ordenarPor: this.ordenarPor,
      direcao: this.direcao,
    };
    const status = (filtros.status ?? []) as StatusMeta[];
    this.filtrosUrl.write({
      referencia: this.dataReferencia ?? undefined,
      tipo: this.filtros.idTipoInspecao ?? [],
      funcao: this.filtros.idFuncao ?? [],
      colaborador: this.filtros.idColaborador ?? [],
      centro: this.filtros.idCentroCusto ?? [],
      base: this.filtros.idBaseOperacional ?? [],
      processo: this.filtros.idProcesso ?? [],
      situacao: status,
    });

    // Os totais consolidados só somam o que passou pelos filtros. Com uma
    // situação selecionada, os cartões precisam de uma segunda consulta sem esse
    // recorte — do contrário os outros dois cartões zerariam.
    const consulta$ = temSelecao(status)
      ? forkJoin({
          tabela: this.metasService.acompanhamento(filtros),
          cartoes: this.metasService.acompanhamento({ ...filtros, status: null }),
        })
      : this.metasService
          .acompanhamento(filtros)
          .pipe(map((resposta) => ({ tabela: resposta, cartoes: resposta })));

    consulta$.subscribe({
      next: ({ tabela, cartoes }) => {
        this.data.set(tabela);
        this.resumo.set(cartoes);
        this.statusAplicado.set(status);
        this.filtrosAplicados.set(filtros);
        this.ordenacaoAplicada.set({ campo: this.ordenarPor, direcao: this.direcao });
        this.loading.set(false);
      },
      error: (erro) => {
        this.loading.set(false);
        this.notificacoes.error(getErrorMessage(erro), { title: 'Não foi possível carregar' });
      },
    });
  }

  protected searchColaboradores(event: { filter?: string }): void {
    this.colaboradorLookup.search(event.filter ?? '');
  }

  protected loadMoreColaboradores(): void {
    this.colaboradorLookup.loadNext();
  }

  protected limpar(): void {
    this.dataReferencia = null;
    this.filtros = this.filtroVazio();
    this.ordenarPor = 'PERCENTUAL';
    this.direcao = 'ASC';
    this.carregar();
  }

  private filtroVazio(): AcompanhamentoMetaFilter {
    return {
      idTipoInspecao: [],
      idFuncao: [],
      idColaborador: [],
      idBaseOperacional: [],
      idCentroCusto: [],
      idProcesso: [],
      status: [],
    };
  }

  /**
   * Clique no cabeçalho: na primeira vez aplica a direção que faz sentido para o
   * campo (cobrança primeiro); nos cliques seguintes só inverte.
   */
  protected ordenarPorColuna(campo: OrdenacaoAcompanhamentoMeta): void {
    if (this.ordenarPor === campo) {
      this.direcao = this.direcao === 'ASC' ? 'DESC' : 'ASC';
    } else {
      this.ordenarPor = campo;
      this.direcao = ORDENACAO_META_DIRECAO_INICIAL[campo];
    }
    this.carregar();
  }

  protected aoTrocarOrdenacao(campo: OrdenacaoAcompanhamentoMeta): void {
    this.direcao = ORDENACAO_META_DIRECAO_INICIAL[campo];
    this.carregar();
  }

  protected inverterDirecao(): void {
    this.direcao = this.direcao === 'ASC' ? 'DESC' : 'ASC';
    this.carregar();
  }

  protected iconeOrdenacao(campo: OrdenacaoAcompanhamentoMeta): string {
    const atual = this.ordenacaoAplicada();
    if (atual.campo !== campo) return 'pi pi-sort-alt';
    return atual.direcao === 'ASC'
      ? 'pi pi-sort-amount-up meta-sort__ativo'
      : 'pi pi-sort-amount-down meta-sort__ativo';
  }

  protected ariaSort(campo: OrdenacaoAcompanhamentoMeta): 'ascending' | 'descending' | 'none' {
    const atual = this.ordenacaoAplicada();
    if (atual.campo !== campo) return 'none';
    return atual.direcao === 'ASC' ? 'ascending' : 'descending';
  }

  protected estaSemAcesso(item: AcompanhamentoMetaItem): boolean {
    return !item.possuiUsuario;
  }

  protected rotuloOrigem(item: AcompanhamentoMetaItem): string {
    return ESCOPO_META_LABELS[item.origem];
  }

  protected rotuloPeriodicidade(item: AcompanhamentoMetaItem): string {
    return PERIODICIDADE_LABELS[item.periodicidade];
  }

  protected rotuloStatus(item: AcompanhamentoMetaItem): string {
    return STATUS_META_LABELS[item.status];
  }

  /** A barra enche até 100%, mas o percentual exibido pode ultrapassar. */
  protected larguraBarra(item: AcompanhamentoMetaItem): number {
    return Math.max(2, Math.min(100, item.percentualAtingimento ?? 0));
  }

  protected truncarNome(nome: string | null | undefined, max: number): string {
    if (!nome) return 'Cadastro não disponível';
    return nome.length > max ? nome.substring(0, max) + '...' : nome;
  }

  protected formatarNomeParaGrafico(nome: string | null | undefined): string[] {
    if (!nome) return ['Cadastro', 'não disponível'];
    // Pega o primeiro e último nome, ou quebra em duas linhas para não ocupar muito espaço lateral
    const partes = nome.trim().split(' ');
    if (partes.length === 1) return [this.truncarNome(partes[0], 15)];
    return [this.truncarNome(partes[0], 12), this.truncarNome(partes[partes.length - 1], 12)];
  }

  protected calculaLarguraGrafico(qtd: number): number {
    return Math.max(600, qtd * 65); // 65px por barra para garantir o scroll horizontal sem encavalar
  }

  protected irParaCadastro(): void {
    this.router.navigate(['/sst/metas-inspecao']);
  }

  private carregarLookups(): void {
    forkJoin({
      tiposInspecao: this.sstService.listarOpcoesTiposInspecao(),
      funcoes: this.rh.listarFuncoesAtivas(),
      bases: this.rh.listarBasesOperacionais({ ativo: true, page: 0, size: 100, sort: 'nome,asc' }),
      centrosCusto: this.centrosCustoLookup.listar(),
      processos: this.rh.listarProcessosAtivos(),
    }).subscribe({
      next: (resposta) => {
        this.tiposInspecao.set(resposta.tiposInspecao);
        this.funcoes.set(resposta.funcoes.content);
        this.bases.set(resposta.bases.content);
        this.centrosCusto.set(resposta.centrosCusto);
        this.processos.set(resposta.processos.content);
      },
    });
  }
}
