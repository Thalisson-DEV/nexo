import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ChartModule } from 'primeng/chart';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';

import {
  ChaveIndicador,
  DIMENSOES_FILTRO_DESCONTO,
  DashboardDescontosResponse,
  DescontoDashboardFilter,
  GranularidadeDashboard,
  IndicadorComparativoDesconto,
  PrioridadeDescontoItem,
  RankingDescontoItem,
  STATUS_DESCONTO_LABELS
} from '../../../../core/models/desconto-dashboard.models';
import { DescontoStatus } from '../../../../core/models/desconto.models';
import {
  BaseOperacionalResponse,
  CentroFaturamentoResponse,
  FuncaoResponse
} from '../../../../core/models/rh.models';
import { PagedResponse, getErrorMessage } from '../../../../core/models/shared.models';
import { CentrosCustoLookupService } from '../../../../core/services/centros-custo-lookup.service';
import { RhService } from '../../../../core/services/rh.service';
import { RhDescontosService } from '../../../../core/services/rh-descontos.service';
import {
  MotivoDescontoResponse,
  TipoEquipamentoResponse
} from '../../../../core/models/desconto.models';
import { normalizarSelecao } from '../../../../core/utils/http-params.util';
import {
  DashboardInsightPanelComponent,
  DashboardMetricCardComponent
} from '../../../../shared/components/dashboard/dashboard-ui.components';
import {
  CesButtonComponent,
  CesDataTableColumn,
  CesDatepickerComponent,
  CesDrillStateService,
  CesFilterChip,
  CesFilterPanelComponent,
  CesIconButtonComponent,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesSelectOption,
  NotificationService
} from '@ces/ui';
import { DashboardDrillComponent, DrillRecorte } from '../../../../shared/components/dashboard/dashboard-drill.component';
import { DescontoResponse, getValorEfetivo } from '../../../../core/models/desconto.models';

const ROTULO_STATUS_DESCONTO: Record<DescontoStatus, string> = {
  SOLICITADO: 'Solicitado', VALIDADO: 'Validado', BAIXADO: 'Baixado', REJEITADO: 'Rejeitado',
};

interface LinhaDescontoDrill {
  id: string;
  ocorrencia: string;
  motivo: string;
  status: string;
  parcelas: number;
  valor: number;
  itens: string;
}
import {
  CopilotoAreaComponent,
  CopilotoDashboardContext,
  CopilotoGatilhoComponent,
  filtroPainel,
  graficoPainel,
  metricaPainel,
  periodoPainel,
  rotuloDasOpcoes,
} from '../../../copiloto/painel';
import { toSaoPauloIso } from '../../../../shared/utils/dashboard-date.utils';
import {
  construirInsightsDeDescontos,
  formatarMoeda,
  formatarMoedaCompacta,
  formatarPercentual
} from './desconto-dashboard-insights';
import { atualizarPeriodicamente } from '../../../../shared/components/dashboard/atualizacao-automatica';

/**
 * Configuração de um eixo do Chart.js. Só o suficiente para que espalhar o bloco base
 * com `...` continue tipado — sem isso, `ticks` cai numa assinatura de índice e o
 * compilador exige acesso por colchetes em toda sobrescrita.
 */
interface EixoChart {
  ticks: Record<string, unknown>;
  [chave: string]: unknown;
}

/** Como a série e os rankings são lidos: em dinheiro ou em número de termos. */
type Unidade = 'valor' | 'quantidade';
type SerieModo = 'periodo' | 'acumulado';
type DimensaoOrganizacional = 'bases' | 'centros' | 'funcoes';

/** Acima disto, um ranking em barras vira ruído em vez de comparação. */
const MAX_BARRAS_NO_GRAFICO = 10;

/** Paleta única dos gráficos — as três telas de dashboard usam as mesmas famílias. */
const COR = {
  solicitado: '#93c5fd',
  validado: '#2563eb',
  baixado: '#0f766e',
  rejeitado: '#c2413b',
  pendente: '#c57a16',
  neutro: '#94a3b8',
  eixo: '#9fb3c8',
  rotulo: '#829ab1',
  grade: '#edf2f7',
  tooltip: '#102a43'
};

const CORES_CATEGORICAS = [
  '#2563eb', '#0f766e', '#c57a16', '#60a5fa', '#14b8a6',
  '#9f1239', '#7c3aed', '#475569', '#93c5fd', '#a16207'
];

@Component({
  selector: 'app-dashboard-descontos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, DecimalPipe, ChartModule, SkeletonModule, TableModule, TooltipModule,
    DashboardInsightPanelComponent, DashboardMetricCardComponent, CesButtonComponent, CopilotoAreaComponent, CopilotoGatilhoComponent,
    DashboardDrillComponent, CesIconButtonComponent, CesDatepickerComponent, CesFilterPanelComponent, CesMultiSelectComponent,
    CesPageHeaderComponent, CesSelectComponent
  ],
  providers: [CesDrillStateService],
  templateUrl: './dashboard-descontos.component.html'
})
export class DashboardDescontosComponent implements OnInit {
  private readonly descontos = inject(RhDescontosService);
  private readonly rh = inject(RhService);
  private readonly centrosCustoLookup = inject(CentrosCustoLookupService);
  private readonly notificacoes = inject(NotificationService);
  private readonly router = inject(Router);
  protected readonly drill = inject(CesDrillStateService);
  private readonly route = inject(ActivatedRoute);

  protected readonly loading = signal(false);
  protected readonly data = signal<DashboardDescontosResponse | null>(null);
  protected readonly atualizadoEm = signal<Date | null>(null);
  protected readonly showManagementView = signal(true);
  protected readonly periodoAtivo = signal<string>('');
  protected readonly tipoDataFiltro = signal<'ocorrencia' | 'criacao'>('ocorrencia');

  protected readonly unidadeSerie = signal<Unidade>('valor');
  protected readonly serieModo = signal<SerieModo>('periodo');
  protected readonly unidadeStatus = signal<Unidade>('valor');
  protected readonly dimensaoOrganizacional = signal<DimensaoOrganizacional>('bases');

  protected readonly motivos = signal<MotivoDescontoResponse[]>([]);
  protected readonly tiposEquipamento = signal<TipoEquipamentoResponse[]>([]);
  protected readonly basesOperacionais = signal<BaseOperacionalResponse[]>([]);
  protected readonly centrosCusto = signal<CentroFaturamentoResponse[]>([]);
  protected readonly funcoes = signal<FuncaoResponse[]>([]);

  /** Filtros que estavam ativos na consulta que produziu `data()`. */
  private readonly filtrosAplicados = signal<DescontoDashboardFilter>({});

  private readonly atualizacao = atualizarPeriodicamente(() => {
    this.buscar(this.filtrosAplicados(), true);
  }, { pode: () => !this.loading() && !!this.data() });

  private readonly chavesDeSelecao = DIMENSOES_FILTRO_DESCONTO;

  protected readonly opcoesMotivos = computed<CesSelectOption<string>[]>(() => this.motivos().map((m) => ({ label: m.descricao, value: m.id })));
  protected readonly opcoesTipos = computed<CesSelectOption<string>[]>(() => this.tiposEquipamento().map((t) => ({ label: t.descricao, value: t.id })));
  protected readonly opcoesCentros = computed<CesSelectOption<string>[]>(() => this.centrosCusto().map((c) => ({ label: c.nome, value: c.id })));
  protected readonly opcoesBases = computed<CesSelectOption<string>[]>(() => this.basesOperacionais().map((b) => ({ label: b.nome, value: b.id })));
  protected readonly opcoesFuncoes = computed<CesSelectOption<string>[]>(() => this.funcoes().map((f) => ({ label: f.nome, value: f.id })));

  /** Recorte aplicado como chips; o agrupamento do gráfico não recorta e fica fora. */
  protected readonly chips = computed<CesFilterChip[]>(() => {
    const f = this.filtrosAplicados();
    const chips: CesFilterChip[] = [];
    const criacao = !!(f.inicioCriacao || f.fimCriacao);
    const de = criacao ? f.inicioCriacao : f.inicio;
    const ate = criacao ? f.fimCriacao : f.fim;
    const dia = (iso: string) => new Date(iso).toLocaleDateString('pt-BR');
    if (de || ate) {
      const rotulo = criacao ? 'Lançamento' : 'Ocorrência';
      chips.push({ key: 'periodo', label: `${rotulo}: ${de && ate ? `${dia(de)} a ${dia(ate)}` : de ? `a partir de ${dia(de)}` : `até ${dia(ate!)}`}` });
    }
    const grupos: [keyof DescontoDashboardFilter, string, CesSelectOption<string>[]][] = [
      ['idMotivo', 'Motivo', this.opcoesMotivos()],
      ['idTipoEquipamento', 'Tipo de equipamento', this.opcoesTipos()],
      ['idCentroCusto', 'Centro de custo', this.opcoesCentros()],
      ['idBaseOperacional', 'Base', this.opcoesBases()],
      ['idFuncao', 'Função', this.opcoesFuncoes()],
      ['status', 'Status', this.statusOptions]
    ];
    for (const [chave, rotulo, opcoes] of grupos) {
      const ids = (f[chave] as string[] | null | undefined) ?? [];
      if (!ids.length) continue;
      chips.push({ key: chave, label: `${rotulo}: ${ids.length === 1 ? (opcoes.find((o) => o.value === ids[0])?.label ?? 'selecionado') : `${ids.length} selecionados`}` });
    }
    return chips;
  });

  protected removerFiltro(chave: string): void {
    if (chave === 'periodo') {
      this.dataInicio = null;
      this.dataFim = null;
    } else {
      this.filter = { ...this.filter, [chave]: [] };
    }
    this.carregar();
  }

  protected readonly insights = computed(() => {
    const atual = this.data();
    return atual ? construirInsightsDeDescontos(atual) : [];
  });

  protected readonly linhasOrganizacionais = computed<RankingDescontoItem[]>(() => {
    const atual = this.data();
    if (!atual) return [];
    return {
      bases: atual.porBaseOperacional,
      centros: atual.porCentroCusto,
      funcoes: atual.porFuncao
    }[this.dimensaoOrganizacional()] ?? [];
  });

  protected readonly rotuloOrganizacional = computed(
    () => this.abasOrganizacionais.find((aba) => aba.value === this.dimensaoOrganizacional())?.label ?? 'Agrupamento'
  );

  /**
   * Total de pendências do backlog histórico, que é o que o gráfico de envelhecimento
   * mostra. Não dá para usar `resumo.pendentes`: aquele número é só o do período
   * consultado, e o painel ficaria vazio num recorte sem termos novos mas com
   * pendências antigas em aberto — justamente o caso que mais precisa ser visto.
   */
  protected readonly totalPendenteHistorico = computed(() =>
    (this.data()?.envelhecimento ?? []).reduce((soma, faixa) => soma + faixa.quantidade, 0)
  );

  /**
   * Indicadores comparativos já formatados. Fica em um `computed` porque o template
   * precisaria de um `switch` por chave para escolher entre moeda, percentual e dias —
   * decisão que é de apresentação, não de marcação.
   */
  protected readonly kpisComparativos = computed(() => {
    const indicadores = this.data()?.indicadores;
    if (!indicadores) return [];

    const formatos: { chave: ChaveIndicador; rotulo: string; formatar: (valor: number) => string }[] = [
      {
        chave: 'valorEmAberto',
        rotulo: 'Valor em aberto (todo o backlog)',
        formatar: formatarMoedaCompacta
      },
      {
        chave: 'taxaAprovacao',
        rotulo: 'Aprovação dos finalizados',
        formatar: (valor) => formatarPercentual(valor)
      },
      {
        chave: 'tempoValidacao',
        rotulo: 'Dias até a validação',
        formatar: (valor) => `${valor.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dia(s)`
      }
    ];

    return formatos
      .filter(({ chave }) => !!indicadores[chave])
      .map(({ chave, rotulo, formatar }) => {
        const indicador = indicadores[chave];
        const variou = indicador.anterior !== 0 && indicador.variacaoPercentual !== 0;
        return {
          chave,
          rotulo,
          valor: formatar(indicador.valor),
          comparativo: this.comparativo(indicador),
          tom: this.tomDaVariacao(indicador),
          seta: variou
            ? indicador.variacaoPercentual > 0
              ? 'pi-arrow-up-right'
              : 'pi-arrow-down-right'
            : null,
          definicao: indicador.definicao
        };
      });
  });

  protected readonly abasOrganizacionais = [
    { label: 'Bases', value: 'bases' as const },
    { label: 'Centros de custo', value: 'centros' as const },
    { label: 'Funções', value: 'funcoes' as const }
  ];
  protected readonly abasUnidade = [
    { label: 'Em valor', value: 'valor' as const },
    { label: 'Em quantidade', value: 'quantidade' as const }
  ];
  protected readonly abasSerieModo = [
    { label: 'Por período', value: 'periodo' as const },
    { label: 'Acumulado', value: 'acumulado' as const }
  ];
  protected readonly periodosRapidos = [
    { label: '30 dias', value: 'mensal' as const },
    { label: '90 dias', value: 'trimestral' as const },
    { label: '1 ano', value: 'anual' as const }
  ];
  protected readonly tiposFiltroData = [
    { label: 'Data de Ocorrência', value: 'ocorrencia' as const },
    { label: 'Data de Lançamento', value: 'criacao' as const }
  ];

  protected readonly granularidadeOptions = [
    { label: 'Automático', value: null },
    { label: 'Por dia', value: 'DIA' as GranularidadeDashboard },
    { label: 'Por mês', value: 'MES' as GranularidadeDashboard },
    { label: 'Por ano', value: 'ANO' as GranularidadeDashboard }
  ];
  protected readonly granularidadeLabels: Record<GranularidadeDashboard, string> = {
    DIA: 'dia', MES: 'mês', ANO: 'ano'
  };
  protected readonly statusOptions = (Object.keys(STATUS_DESCONTO_LABELS) as DescontoStatus[])
    .map((value) => ({ label: STATUS_DESCONTO_LABELS[value], value }));

  /**
   * Contexto do dashboard para o Copiloto: o recorte aplicado e os agregados em tela. Não inclui a
   * lista de colaboradores (dado pessoal) — se a pergunta exigir, o agente consulta pelas ferramentas,
   * que aplicam as permissões do usuário.
   */
  protected readonly contextoCopiloto = computed<CopilotoDashboardContext | null>(() => {
    const d = this.data();
    const f = this.filtrosAplicados();
    const nomes = <T>(itens: readonly T[], ids: readonly string[] | null | undefined, id: (i: T) => string, nome: (i: T) => string) =>
      rotuloDasOpcoes(itens, ids ?? [], id, nome);
    const doRanking = (itens: readonly { id: string; nome: string | null }[] | undefined, ids: readonly string[] | null | undefined) =>
      nomes(itens ?? [], ids, (i) => i.id, (i) => i.nome ?? i.id);
    const direcao = (ind?: IndicadorComparativoDesconto) =>
      ind?.direcaoDesejada === 'SUBIR' ? 'maior' as const : ind?.direcaoDesejada === 'DESCER' ? 'menor' as const : undefined;
    const indicador = (chave: string, rotulo: string, formato: 'moeda' | 'percentual' | 'dias') => {
      const ind = d?.indicadores?.[chave];
      return metricaPainel(`indicador.${chave}`, rotulo, ind?.valor, formato, {
        descricao: ind?.definicao,
        comparacao: ind && ind.anterior
          ? { rotulo: 'vs. período anterior', valorAnterior: ind.anterior, variacaoPercentual: ind.variacaoPercentual, melhorQuando: direcao(ind) }
          : undefined,
      });
    };
    const serie = (this.tipoDataFiltro() === 'criacao' ? d?.serieTemporalCriacao : d?.serieTemporal) ?? [];
    const organizacional = this.linhasOrganizacionais();
    return {
      id: 'dp.descontos',
      nome: 'Descontos em folha',
      modulo: 'Departamento pessoal',
      descricao: 'Termos de desconto: valores solicitados, validados e baixados em folha, funil de aprovação, pendências por idade, tempo de ciclo e concentração por motivo, tipo de equipamento e estrutura.',
      rota: '/dp/descontos/dashboard',
      periodo: d ? periodoPainel(d.inicio, d.fim, undefined) : periodoPainel(this.dataInicio, this.dataFim),
      filtros: [
        filtroPainel('tipoData', 'Data considerada', this.tipoDataFiltro() === 'criacao' ? 'Data de criação do termo' : 'Data da ocorrência'),
        filtroPainel('motivo', 'Motivo', doRanking(d?.porMotivo, f.idMotivo), f.idMotivo ?? []),
        filtroPainel('tipoEquipamento', 'Tipo de equipamento', doRanking(d?.porTipoEquipamento, f.idTipoEquipamento), f.idTipoEquipamento ?? []),
        filtroPainel('baseOperacional', 'Base operacional', nomes(this.basesOperacionais(), f.idBaseOperacional, (i) => i.id, (i) => i.nome), f.idBaseOperacional ?? []),
        filtroPainel('centroCusto', 'Centro de custo', nomes(this.centrosCusto(), f.idCentroCusto, (i) => i.id, (i) => i.nome), f.idCentroCusto ?? []),
        filtroPainel('funcao', 'Função', doRanking(d?.porFuncao, f.idFuncao), f.idFuncao ?? []),
        filtroPainel('status', 'Status', (f.status ?? []).map((s) => STATUS_DESCONTO_LABELS[s] ?? s), f.status ?? []),
      ],
      metricas: d
        ? [
            metricaPainel('totalDescontos', 'Termos de desconto', d.resumo.totalDescontos, 'inteiro', {
              comparacao: d.resumo.totalPeriodoAnterior
                ? { rotulo: 'vs. período anterior', valorAnterior: d.resumo.totalPeriodoAnterior, variacaoPercentual: d.resumo.variacaoPercentual }
                : undefined,
            }),
            metricaPainel('valorSolicitado', 'Valor solicitado', d.financeiro.valorSolicitado, 'moeda'),
            metricaPainel('valorBaixado', 'Baixado em folha', d.financeiro.valorBaixado, 'moeda', {
              comparacao: d.financeiro.valorBaixadoPeriodoAnterior
                ? { rotulo: 'vs. período anterior', valorAnterior: d.financeiro.valorBaixadoPeriodoAnterior, variacaoPercentual: d.financeiro.variacaoValorBaixado }
                : undefined,
            }),
            metricaPainel('taxaRecuperacao', 'Recuperado (baixado ÷ solicitado)', d.financeiro.taxaRecuperacao, 'percentual'),
            metricaPainel('valorEmAberto', 'Em aberto', d.financeiro.valorEmAberto, 'moeda', {
              descricao: `${d.resumo.pendentes} termo(s) pendentes; o mais antigo há ${d.ciclo.idadeMaximaPendenteDias} dia(s)`,
            }),
            metricaPainel('glosaValidacao', 'Cortado na validação', d.financeiro.glosaValidacao, 'moeda', {
              descricao: `${d.financeiro.percentualGlosaValidacao.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}% do solicitado`,
            }),
            metricaPainel('valorRejeitado', 'Rejeitado', d.financeiro.valorRejeitado, 'moeda'),
            indicador('taxaAprovacao', 'Aprovação dos finalizados', 'percentual'),
            indicador('tempoValidacao', 'Dias até a validação', 'dias'),
            metricaPainel('colaboradoresAtingidos', 'Colaboradores atingidos', d.resumo.colaboradoresAtingidos, 'inteiro', {
              descricao: `${d.resumo.percentualDoEfetivo.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}% do efetivo; ${d.resumo.colaboradoresReincidentes} reincidentes`,
            }),
            metricaPainel('medianaDiasCicloTotal', 'Ciclo total (mediana)', d.ciclo.medianaDiasCicloTotal, 'dias', {
              descricao: `P90 ${d.ciclo.p90DiasCicloTotal.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dia(s)`,
            }),
          ]
        : [],
      graficos: d
        ? [
            graficoPainel('serie', 'barra', 'Solicitado × baixado ao longo do período', serie.map((i) => i.rotulo), [
              { nome: 'Solicitado (R$)', valores: serie.map((i) => i.valorSolicitado) },
              { nome: 'Baixado (R$)', valores: serie.map((i) => i.valorBaixado) },
            ], { metrica: 'valor', dimensao: `período (${this.granularidadeLabels[d.granularidade]})`, formato: 'moeda' }),
            graficoPainel('funil', 'funil', 'Funil do desconto', d.funil.map((e) => e.rotulo), d.funil.map((e) => e.valor), {
              metrica: 'valor', dimensao: 'etapa', formato: 'moeda',
            }),
            graficoPainel('porStatus', 'rosca', 'Distribuição por status', d.porStatus.map((s) => s.rotulo), d.porStatus.map((s) => s.valor), {
              metrica: 'valor', dimensao: 'status', formato: 'moeda',
            }),
            graficoPainel('envelhecimento', 'barra', 'Pendências por idade', d.envelhecimento.map((e) => `${e.faixa} dias`), d.envelhecimento.map((e) => e.quantidade), {
              metrica: 'termos pendentes', dimensao: 'idade', formato: 'inteiro',
            }),
            graficoPainel('porMotivo', 'ranking', 'Descontos por motivo', d.porMotivo.map((m) => m.nome ?? 'Cadastro não disponível'), d.porMotivo.map((m) => m.valorEfetivo), {
              metrica: 'valor efetivo', dimensao: 'motivo', formato: 'moeda',
            }),
            graficoPainel('porTipoEquipamento', 'ranking', 'Descontos por tipo de equipamento', d.porTipoEquipamento.map((m) => m.nome ?? 'Cadastro não disponível'),
              d.porTipoEquipamento.map((m) => m.valorItens), { metrica: 'valor dos itens', dimensao: 'tipo de equipamento', formato: 'moeda' }),
            graficoPainel(`organizacional.${this.dimensaoOrganizacional()}`, 'ranking', `Concentração por ${this.rotuloOrganizacional().toLowerCase()}`,
              organizacional.map((o) => o.nome ?? 'Cadastro não disponível'), organizacional.map((o) => o.valorEfetivo), {
                metrica: 'valor efetivo', dimensao: this.rotuloOrganizacional().toLowerCase(), formato: 'moeda', totalCategorias: organizacional.length,
              }),
          ]
        : [],
      datasets: ['descontos', 'descontos_itens', 'motivos_desconto', 'colaboradores', 'bases_operacionais'],
      sugestoes: [
        { rotulo: 'O que está parado?', icone: 'pi pi-hourglass', requer: ['dados'],
          pergunta: 'Quais termos de desconto deste recorte estão parados há mais tempo aguardando validação ou baixa, e quanto valor está em aberto?' },
        { rotulo: 'Por que o baixado variou?', icone: 'pi pi-arrow-right-arrow-left', requer: ['dados', 'analise'],
          pergunta: 'O que explica a variação do valor baixado em folha em relação ao período anterior? Detalhe por motivo e tipo de equipamento.' },
      ],
    };
  });

  protected dataInicio: Date | null = null;
  protected dataFim: Date | null = null;
  protected filter: DescontoDashboardFilter = this.filtroVazio();

  protected serieData: unknown;
  protected serieOptions: unknown;
  protected funilData: unknown;
  protected funilOptions: unknown;
  protected statusData: unknown;
  protected statusChartOptions: unknown;
  protected envelhecimentoData: unknown;
  protected envelhecimentoOptions: unknown;
  protected motivoData: unknown;
  protected motivoOptions: unknown;
  protected tipoEquipamentoData: unknown;
  protected tipoEquipamentoOptions: unknown;
  protected cicloData: unknown;
  protected cicloOptions: unknown;

  // ------------------------------------------------------------------ //
  //  Ciclo de vida
  // ------------------------------------------------------------------ //

  ngOnInit(): void {
    this.restaurarFiltrosDaUrl();
    this.carregarLookups();
    this.carregar();
  }

  protected aplicar(): void {
    if (!this.periodoValido()) return;
    this.periodoAtivo.set('');
    this.carregar();
  }

  protected limpar(): void {
    this.dataInicio = null;
    this.dataFim = null;
    this.filter = this.filtroVazio();
    this.periodoAtivo.set('');
    this.carregar();
  }

  protected setPeriodo(tipo: 'mensal' | 'trimestral' | 'anual'): void {
    const hoje = new Date();
    this.dataFim = new Date(hoje);
    this.dataInicio = new Date(hoje);
    if (tipo === 'mensal') this.dataInicio.setMonth(hoje.getMonth() - 1);
    if (tipo === 'trimestral') this.dataInicio.setMonth(hoje.getMonth() - 3);
    if (tipo === 'anual') this.dataInicio.setFullYear(hoje.getFullYear() - 1);
    this.periodoAtivo.set(tipo);
    this.carregar();
  }

  protected onGranularidadeChange(): void {
    this.carregar();
  }

  private filtroVazio(): DescontoDashboardFilter {
    return {
      idMotivo: [], idTipoEquipamento: [], idBaseOperacional: [], idCentroCusto: [],
      idFuncao: [], status: [], granularidade: null
    };
  }

  /** Recompõe o recorte compartilhado por link (`?idMotivo=a&idMotivo=b`). */
  private restaurarFiltrosDaUrl(): void {
    const query = this.route.snapshot.queryParamMap;
    this.chavesDeSelecao.forEach((chave) => {
      (this.filter[chave] as string[]) = normalizarSelecao(query.getAll(chave));
    });
    const granularidade = query.get('granularidade') as GranularidadeDashboard | null;
    if (granularidade && ['DIA', 'MES', 'ANO'].includes(granularidade)) {
      this.filter.granularidade = granularidade;
    }
    
    if (query.get('inicioCriacao') || query.get('fimCriacao')) {
      this.tipoDataFiltro.set('criacao');
      this.dataInicio = this.parsearData(query.get('inicioCriacao'));
      this.dataFim = this.parsearData(query.get('fimCriacao'));
    } else {
      this.tipoDataFiltro.set('ocorrencia');
      this.dataInicio = this.parsearData(query.get('inicio'));
      this.dataFim = this.parsearData(query.get('fim'));
    }
  }

  private parsearData(valor: string | null): Date | null {
    if (!valor) return null;
    const data = new Date(valor);
    return Number.isNaN(data.getTime()) ? null : data;
  }

  /** Deixa o recorte na URL para o gestor conseguir compartilhar a tela. */
  private sincronizarUrl(filter: DescontoDashboardFilter): void {
    const queryParams: Record<string, string[] | string | null> = {};
    this.chavesDeSelecao.forEach((chave) => {
      const selecao = (filter[chave] as string[] | null) ?? [];
      queryParams[chave] = selecao.length ? selecao : null;
    });
    queryParams['granularidade'] = filter.granularidade ?? null;
    
    if (this.tipoDataFiltro() === 'criacao') {
      queryParams['inicio'] = null;
      queryParams['fim'] = null;
      queryParams['inicioCriacao'] = filter.inicioCriacao ?? null;
      queryParams['fimCriacao'] = filter.fimCriacao ?? null;
    } else {
      queryParams['inicio'] = filter.inicio ?? null;
      queryParams['fim'] = filter.fim ?? null;
      queryParams['inicioCriacao'] = null;
      queryParams['fimCriacao'] = null;
    }
    
    // `merge` preserva o `?drill=` do painel de detalhe aberto.
    this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge', replaceUrl: true });
  }

  /**
   * Os lookups são independentes entre si e nenhum deles é essencial para a tela: uma
   * lista de opções que não carregou apenas deixa aquele filtro vazio, e derrubar o
   * dashboard inteiro por isso seria desproporcional.
   */
  private carregarLookups(): void {
    forkJoin({
      motivos: this.opcoes<MotivoDescontoResponse>(
        this.descontos.listarMotivos({ ativo: true, size: 200, sort: 'descricao,asc' })),
      tipos: this.opcoes<TipoEquipamentoResponse>(
        this.descontos.listarTiposEquipamento({ ativo: true, size: 200, sort: 'descricao,asc' })),
      bases: this.opcoes<BaseOperacionalResponse>(
        this.rh.listarBasesOperacionais({ ativo: true, page: 0, size: 200, sort: 'nome,asc' })),
      funcoes: this.opcoes<FuncaoResponse>(this.rh.listarFuncoesAtivas()),
      centrosCusto: this.centrosCustoLookup.listar().pipe(catchError(() => of([] as CentroFaturamentoResponse[])))
    }).subscribe((resposta) => {
      this.motivos.set(resposta.motivos);
      this.tiposEquipamento.set(resposta.tipos);
      this.basesOperacionais.set(resposta.bases);
      this.funcoes.set(resposta.funcoes);
      this.centrosCusto.set(resposta.centrosCusto);
    });
  }

  /** Extrai o conteúdo de uma página de lookup e degrada para lista vazia em falha. */
  private opcoes<T>(origem: Observable<PagedResponse<T>>): Observable<T[]> {
    return origem.pipe(
      map((resposta) => resposta.content ?? []),
      catchError(() => of([] as T[]))
    );
  }

  private carregar(): void {
    const filter: DescontoDashboardFilter = {
      ...this.filter,
    };
    
    const inicioStr = this.dataInicio ? this.toIso(this.dataInicio, false) : null;
    const fimStr = this.dataFim ? this.toIso(this.dataFim, true) : null;
    
    if (this.tipoDataFiltro() === 'criacao') {
      filter.inicioCriacao = inicioStr;
      filter.fimCriacao = fimStr;
      filter.inicio = null;
      filter.fim = null;
    } else {
      filter.inicio = inicioStr;
      filter.fim = fimStr;
      filter.inicioCriacao = null;
      filter.fimCriacao = null;
    }

    this.sincronizarUrl(filter);
    this.buscar(filter);
  }

  /** Busca um recorte; a atualização automática repete o aplicado sem mostrar carregamento nem avisar erro. */
  private buscar(filter: DescontoDashboardFilter, silencioso = false): void {
    if (!silencioso) this.loading.set(true);
    this.descontos.gerarDashboard(filter).subscribe({
      next: (resposta) => {
        this.data.set(resposta);
        this.filtrosAplicados.set(filter);
        this.atualizadoEm.set(new Date(resposta.atualizadoEm));
        this.prepararGraficos(resposta);
        this.loading.set(false);
      },
      error: (erro) => {
        if (silencioso) return;
        this.loading.set(false);
        this.notificacoes.error(getErrorMessage(erro), { title: 'Não foi possível carregar' });
      }
    });
  }

  private periodoValido(): boolean {
    if (!this.dataInicio || !this.dataFim || this.dataInicio <= this.dataFim) return true;
    this.notificacoes.warning('A data inicial deve ser anterior ou igual à data final.', { title: 'Período inválido' });
    return false;
  }

  private toIso(date: Date, endOfDay: boolean): string {
    const value = new Date(date);
    value.setHours(endOfDay ? 23 : 0, endOfDay ? 59 : 0, endOfDay ? 59 : 0, endOfDay ? 999 : 0);
    return toSaoPauloIso(value, endOfDay);
  }

  // ------------------------------------------------------------------ //
  //  Formatação e leitura
  // ------------------------------------------------------------------ //

  protected readonly moeda = formatarMoeda;
  protected readonly moedaCompacta = formatarMoedaCompacta;
  protected readonly percentual = formatarPercentual;

  protected nomeOuPlaceholder(nome: string | null): string {
    return nome ?? 'Cadastro não disponível';
  }

  /** A barra enche até 100%, mas o percentual em si pode passar disso. */
  protected barWidth(value: number): number {
    return Math.max(2, Math.min(100, value ?? 0));
  }

  protected variacaoDoVolume(resumo: { totalPeriodoAnterior: number; variacaoPercentual: number }): string {
    if (resumo.totalPeriodoAnterior === 0) return 'Sem período anterior para comparar';
    const valor = resumo.variacaoPercentual.toLocaleString('pt-BR', {
      maximumFractionDigits: 1, signDisplay: 'always'
    });
    return `${valor}% vs. período anterior (${resumo.totalPeriodoAnterior})`;
  }

  protected comparativo(indicador: IndicadorComparativoDesconto): string {
    if (indicador.anterior === 0) return 'Sem período anterior para comparar';
    const variacao = indicador.variacaoPercentual.toLocaleString('pt-BR', {
      maximumFractionDigits: 1, signDisplay: 'always'
    });
    return `${variacao}% vs. período anterior`;
  }

  /**
   * Cor da variação pela direção que o backend declara como desejável. Sem isso, uma
   * queda no valor em aberto sairia vermelha — e queda de backlog é boa notícia.
   */
  protected tomDaVariacao(indicador: IndicadorComparativoDesconto): 'positivo' | 'negativo' | 'neutro' {
    if (indicador.anterior === 0 || indicador.variacaoPercentual === 0) return 'neutro';
    if (indicador.direcaoDesejada === 'NEUTRO') return 'neutro';
    const subiu = indicador.variacaoPercentual > 0;
    const queriaSubir = indicador.direcaoDesejada === 'SUBIR';
    return subiu === queriaSubir ? 'positivo' : 'negativo';
  }

  protected indicador(chave: ChaveIndicador): IndicadorComparativoDesconto | null {
    return this.data()?.indicadores?.[chave] ?? null;
  }

  protected descricaoSerie(granularidade: GranularidadeDashboard): string {
    const intervalo = this.granularidadeLabels[granularidade];
    const perspectivaStr = this.tipoDataFiltro() === 'criacao' ? 'Data de Lançamento' : 'Data de Ocorrência';
    if (this.serieModo() === 'acumulado') {
      return `Soma corrida do que foi solicitado contra o que foi efetivamente descontado. A distância entre as duas curvas é o que se perdeu no caminho. (Agrupado por ${perspectivaStr})`;
    }
    return this.unidadeSerie() === 'valor'
      ? `Valor solicitado e valor baixado dos termos em cada ${intervalo}, por ${perspectivaStr}. A linha é o percentual recuperado.`
      : `Termos em cada ${intervalo}, agrupados por ${perspectivaStr}, pelo desfecho que têm hoje.`;
  }

  protected alertaDeIdade(dias: number): 'low' | 'medium' | 'high' {
    if (dias > 60) return 'high';
    return dias > 30 ? 'medium' : 'low';
  }

  /** Mesma cor que a etapa recebe no gráfico — legenda e barra têm que combinar. */
  protected corDaEtapa(etapa: string): string {
    return {
      SOLICITADOS: COR.solicitado,
      VALIDADOS: COR.validado,
      BAIXADOS: COR.baixado,
      REJEITADOS: COR.rejeitado
    }[etapa] ?? COR.neutro;
  }

  protected corDaFaixa(idadeMinimaDias: number): string {
    if (idadeMinimaDias > 30) return COR.rejeitado;
    return idadeMinimaDias > 15 ? COR.pendente : 'var(--p-primary-400)';
  }

  // ------------------------------------------------------------------ //
  //  Navegação
  // ------------------------------------------------------------------ //

  protected verListagem(): void {
    this.router.navigate(['/dp/descontos/lancamento']);
  }

  /** O painel de leitura devolve o índice do cartão clicado. */
  protected abrirPrioridadePorIndice(indice: number): void {
    const prioridade = this.data()?.prioridades?.[indice];
    // Os cartões que não vieram de uma prioridade (recuperação, tempo de ciclo) ficam
    // depois delas na lista e não têm para onde navegar.
    if (prioridade) {
      this.abrirPrioridade(prioridade);
    }
  }

  /** Leva o gestor do cartão de prioridade direto para os termos que o produziram. */
  protected abrirPrioridade(prioridade: PrioridadeDescontoItem): void {
    const statuses = prioridade.drilldown['status'];
    const queryParams: Record<string, string> = {};
    // A listagem aceita um status por vez; com mais de um, o filtro fica com o gestor.
    if (Array.isArray(statuses) && statuses.length === 1) {
      queryParams['status'] = String(statuses[0]);
    }
    this.router.navigate(['/dp/descontos/lancamento'], { queryParams });
  }

  /** Camada 2: termos do colaborador no período, sem sair do painel. */
  protected abrirColaborador(idColaborador: string): void {
    this.drill.open({ dimension: 'colaborador', value: idColaborador });
  }

  /** Camada 3: listagem de lançamentos do colaborador (navegação original). */
  protected verColaboradorNaListagem(idColaborador: string): void {
    this.router.navigate(['/dp/descontos/lancamento'], { queryParams: { colaboradorId: idColaborador } });
  }

  private readonly colunasDrill: CesDataTableColumn<LinhaDescontoDrill>[] = [
    { key: 'ocorrencia', header: 'Ocorrência', type: 'date', priority: 1 },
    { key: 'motivo', header: 'Motivo', priority: 1 },
    { key: 'itens', header: 'Itens', priority: 2 },
    { key: 'status', header: 'Status', priority: 1 },
    { key: 'parcelas', header: 'Parcelas', type: 'integer', priority: 2 },
    { key: 'valor', header: 'Valor efetivo', type: 'currency', priority: 1 },
  ];

  protected readonly recorteDrill = computed<DrillRecorte<LinhaDescontoDrill> | null>(() => {
    const alvo = this.drill.target();
    const dados = this.data();
    if (alvo?.dimension !== 'colaborador' || !dados) return null;
    const item = dados.porColaborador.find((linha) => linha.idColaborador === alvo.value);
    if (!item) return null;
    const filtros = this.filtrosAplicados();
    const status = filtros.status?.length === 1 ? filtros.status[0] : undefined;
    const outros = (['idMotivo', 'idTipoEquipamento', 'idBaseOperacional', 'idCentroCusto', 'idFuncao', 'idUsuarioSolicitante'] as const)
      .some((chave) => (filtros[chave] as unknown[] | null | undefined)?.length) || (filtros.status?.length ?? 0) > 1;
    const params = {
      colaboradorId: item.idColaborador,
      dataOcorrenciaInicio: filtros.inicio ?? dados.inicio,
      dataOcorrenciaFim: filtros.fim ?? dados.fim,
      ...(status ? { status } : {}),
      page: 0,
      size: 100,
      sort: 'dataOcorrencia,desc',
    };
    return {
      chave: `colaborador:${item.idColaborador}:${params.dataOcorrenciaInicio}:${params.dataOcorrenciaFim}`,
      eyebrow: 'Colaborador',
      titulo: this.nomeOuPlaceholder(item.nomeColaborador),
      descricao: [item.funcao, item.baseOperacional].filter(Boolean).join(' · ') || 'Termos de desconto no período do painel.',
      kpis: [
        { rotulo: 'Termos', valor: String(item.quantidade), detalhe: `${item.baixados} baixado(s) · ${item.rejeitados} rejeitado(s)` },
        { rotulo: 'Valor efetivo', valor: this.moeda(item.valorEfetivo) },
        { rotulo: 'Pendentes', valor: String(item.pendentes) },
      ],
      aviso: outros
        ? 'A lista considera colaborador, período e status (quando único); os demais filtros do painel não se aplicam a ela.'
        : undefined,
      colunas: this.colunasDrill,
      carregar: () => this.descontos.buscarDescontos(params).pipe(map((pagina) => ({
        total: pagina.page.totalElements,
        linhas: pagina.content.map((desconto: DescontoResponse) => ({
          id: desconto.id,
          ocorrencia: desconto.dataOcorrencia,
          motivo: desconto.motivo?.descricao ?? '—',
          status: ROTULO_STATUS_DESCONTO[desconto.status] ?? desconto.status,
          parcelas: desconto.quantidadeParcelas,
          valor: getValorEfetivo(desconto),
          itens: (desconto.itens ?? []).map((i) => i.descricaoMaterial).join(', ') || '—',
        })),
      }))),
      rotuloLinha: (linha) => `Termo de ${linha.motivo}`,
      abrirLinha: (linha) => this.router.navigate(['/dp/descontos', linha.id]),
      rotuloVerTudo: 'Ver lançamentos do colaborador',
      verTudo: () => this.verColaboradorNaListagem(item.idColaborador),
      vazio: 'Nenhum termo neste recorte',
      nomeArquivo: 'descontos-colaborador',
    };
  });

  // ------------------------------------------------------------------ //
  //  Gráficos
  // ------------------------------------------------------------------ //

  private prepararGraficos(dados: DashboardDescontosResponse): void {
    this.prepararSerie();
    this.prepararFunil(dados);
    this.prepararStatus();
    this.prepararEnvelhecimento(dados);
    this.prepararMotivos(dados);
    this.prepararTiposEquipamento(dados);
    this.prepararCiclo(dados);
  }

  protected onUnidadeSerieChange(unidade: Unidade): void {
    this.unidadeSerie.set(unidade);
    this.prepararSerie();
  }

  protected onSerieModoChange(modo: SerieModo): void {
    this.serieModo.set(modo);
    this.prepararSerie();
  }

  protected onUnidadeStatusChange(unidade: Unidade): void {
    this.unidadeStatus.set(unidade);
    this.prepararStatus();
  }

  protected onDimensaoOrganizacionalChange(dimensao: DimensaoOrganizacional): void {
    this.dimensaoOrganizacional.set(dimensao);
  }

  /**
   * Duas leituras da mesma série:
   * - `periodo`: barras de solicitado x baixado em cada intervalo, com o percentual
   *   recuperado como linha no eixo direito;
   * - `acumulado`: soma corrida das duas — é a leitura que mostra a lacuna crescendo
   *   ao longo do tempo, que a versão por intervalo dilui.
   */
  private prepararSerie(): void {
    const serie = this.tipoDataFiltro() === 'criacao' ? (this.data()?.serieTemporalCriacao ?? []) : (this.data()?.serieTemporal ?? []);
    const emValor = this.unidadeSerie() === 'valor';
    const acumulado = this.serieModo() === 'acumulado';

    const solicitado = serie.map((item) => (emValor ? item.valorSolicitado : item.solicitados));
    const baixado = serie.map((item) => (emValor ? item.valorBaixado : item.baixados));
    const primario = acumulado ? this.somaCorrida(solicitado) : solicitado;
    const secundario = acumulado ? this.somaCorrida(baixado) : baixado;

    const datasets: unknown[] = acumulado
      ? [
          {
            type: 'line', label: emValor ? 'Solicitado (acumulado)' : 'Solicitados (acumulado)',
            data: primario, borderColor: COR.validado, backgroundColor: 'rgba(37, 99, 235, 0.10)',
            borderWidth: 2.5, pointRadius: 0, pointHoverRadius: 4, pointHitRadius: 16,
            tension: 0.3, fill: true, order: 2
          },
          {
            type: 'line', label: emValor ? 'Baixado (acumulado)' : 'Baixados (acumulado)',
            data: secundario, borderColor: COR.baixado, backgroundColor: 'rgba(15, 118, 110, 0.16)',
            borderWidth: 2.5, pointRadius: 0, pointHoverRadius: 4, pointHitRadius: 16,
            tension: 0.3, fill: true, order: 1
          }
        ]
      : [
          {
            type: 'bar', label: emValor ? 'Solicitado' : 'Solicitados', data: primario,
            backgroundColor: COR.solicitado, hoverBackgroundColor: '#60a5fa',
            borderRadius: 6, maxBarThickness: 30, order: 3
          },
          {
            type: 'bar', label: emValor ? 'Baixado' : 'Baixados', data: secundario,
            backgroundColor: COR.baixado, hoverBackgroundColor: '#0d9488',
            borderRadius: 6, maxBarThickness: 30, order: 2
          },
          {
            type: 'line', label: '% recuperado', yAxisID: 'y1',
            data: serie.map((item) => item.taxaRecuperacao),
            borderColor: COR.pendente, backgroundColor: COR.pendente, borderDash: [6, 4],
            borderWidth: 2, pointRadius: 0, pointHoverRadius: 4, tension: 0.25, fill: false, order: 1
          }
        ];

    this.serieData = { labels: serie.map((item) => item.rotulo), datasets };
    this.serieOptions = {
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: this.legenda(),
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            label: (contexto: any) => {
              const valor = Number(contexto.parsed?.y ?? 0);
              const rotulo = contexto.dataset?.label ?? '';
              if (rotulo.startsWith('%')) return `${rotulo}: ${formatarPercentual(valor)}`;
              return `${rotulo}: ${emValor ? formatarMoeda(valor) : valor.toLocaleString('pt-BR')}`;
            }
          }
        }
      },
      scales: {
        x: this.eixoX(),
        y: {
          ...this.eixoY(),
          ticks: {
            ...this.eixoY().ticks,
            callback: (valor: number) =>
              emValor ? formatarMoedaCompacta(valor) : Number(valor).toLocaleString('pt-BR')
          }
        },
        ...(acumulado
          ? {}
          : {
              y1: {
                beginAtZero: true, position: 'right', border: { display: false },
                grid: { display: false },
                ticks: {
                  color: COR.pendente, maxTicksLimit: 4, padding: 6,
                  callback: (valor: number) => `${valor}%`
                }
              }
            })
      }
    };
  }

  private somaCorrida(valores: number[]): number[] {
    let total = 0;
    return valores.map((valor) => (total += valor));
  }

  /** Barras horizontais decrescentes — a forma de funil que se lê sem legenda. */
  private prepararFunil(dados: DashboardDescontosResponse): void {
    const etapas = dados.funil.filter((etapa) => etapa.etapa !== 'REJEITADOS');
    const rejeitados = dados.funil.find((etapa) => etapa.etapa === 'REJEITADOS');

    this.funilData = {
      labels: etapas.map((etapa) => etapa.rotulo),
      datasets: [
        {
          label: 'Valor', data: etapas.map((etapa) => etapa.valor),
          backgroundColor: [COR.solicitado, COR.validado, COR.baixado],
          borderRadius: 6, maxBarThickness: 42
        }
      ]
    };
    this.funilOptions = {
      indexAxis: 'y',
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            label: (contexto: any) => formatarMoeda(Number(contexto.parsed?.x ?? 0)),
            afterLabel: (contexto: any) => {
              const etapa = etapas[contexto.dataIndex];
              const linhas = [`${etapa.quantidade} termo(s) · ${formatarPercentual(etapa.percentualDoTopo)} do total`];
              if (etapa.percentualDaEtapaAnterior !== null && contexto.dataIndex > 0) {
                linhas.push(`${formatarPercentual(etapa.percentualDaEtapaAnterior)} da etapa anterior`);
              }
              if (etapa.etapa === 'SOLICITADOS' && rejeitados && rejeitados.quantidade > 0) {
                linhas.push(`${rejeitados.quantidade} rejeitado(s) — ${formatarMoeda(rejeitados.valor)}`);
              }
              return linhas;
            }
          }
        }
      },
      scales: {
        x: {
          beginAtZero: true, border: { display: false },
          grid: { color: COR.grade, drawTicks: false },
          ticks: { color: COR.eixo, maxTicksLimit: 5, padding: 6, callback: (v: number) => formatarMoedaCompacta(v) }
        },
        y: {
          grid: { display: false }, border: { display: false },
          ticks: { color: '#486581', font: { size: 11 }, crossAlign: 'far' }
        }
      }
    };
  }

  private prepararStatus(): void {
    const linhas = this.data()?.porStatus ?? [];
    const emValor = this.unidadeStatus() === 'valor';
    this.statusData = {
      labels: linhas.map((item) => item.rotulo),
      datasets: [
        {
          data: linhas.map((item) => (emValor ? item.valor : item.quantidade)),
          backgroundColor: [COR.pendente, COR.validado, COR.baixado, COR.rejeitado],
          borderWidth: 0, hoverOffset: 6
        }
      ]
    };
    this.statusChartOptions = {
      maintainAspectRatio: false,
      cutout: '62%',
      plugins: {
        legend: {
          position: 'right',
          labels: { boxWidth: 10, boxHeight: 10, usePointStyle: true, color: '#486581', font: { size: 11 }, padding: 12 }
        },
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            label: (contexto: any) => {
              const item = linhas[contexto.dataIndex];
              return emValor
                ? `${formatarMoeda(item.valor)} · ${formatarPercentual(item.percentualValor)}`
                : `${item.quantidade} termo(s) · ${formatarPercentual(item.percentualQuantidade)}`;
            }
          }
        }
      }
    };
  }

  /** Barras empilhadas por status dentro de cada faixa de idade. */
  private prepararEnvelhecimento(dados: DashboardDescontosResponse): void {
    const faixas = dados.envelhecimento;
    this.envelhecimentoData = {
      labels: faixas.map((faixa) => `${faixa.faixa} dias`),
      datasets: [
        {
          label: 'Aguardando validação', data: faixas.map((faixa) => faixa.porStatus.SOLICITADO ?? 0),
          backgroundColor: COR.pendente, borderRadius: 5, maxBarThickness: 44, stack: 'pendentes'
        },
        {
          label: 'Aguardando baixa', data: faixas.map((faixa) => faixa.porStatus.VALIDADO ?? 0),
          backgroundColor: COR.validado, borderRadius: 5, maxBarThickness: 44, stack: 'pendentes'
        }
      ]
    };
    this.envelhecimentoOptions = {
      maintainAspectRatio: false,
      plugins: {
        legend: this.legenda(),
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            footer: (itens: any[]) => {
              const faixa = faixas[itens?.[0]?.dataIndex ?? 0];
              return faixa ? `${formatarMoeda(faixa.valor)} em aberto nesta faixa` : '';
            }
          },
          footerColor: '#bfdbfe', footerFont: { size: 11, weight: '600' }
        }
      },
      scales: {
        x: { ...this.eixoX(), stacked: true },
        y: { ...this.eixoY(), stacked: true, ticks: { ...this.eixoY().ticks, precision: 0 } }
      }
    };
  }

  private prepararMotivos(dados: DashboardDescontosResponse): void {
    const linhas = dados.porMotivo.slice(0, MAX_BARRAS_NO_GRAFICO);
    this.motivoData = {
      labels: linhas.map((item) => this.nomeOuPlaceholder(item.nome)),
      datasets: [
        {
          label: 'Valor efetivo', data: linhas.map((item) => item.valorEfetivo),
          backgroundColor: COR.validado, borderRadius: 5, maxBarThickness: 18
        }
      ]
    };
    this.motivoOptions = this.opcoesBarraHorizontal(
      (indice) => {
        const item = linhas[indice];
        return [
          `${item.quantidade} termo(s) · ${formatarPercentual(item.percentualValor)} do valor`,
          `${item.baixados} baixado(s), ${item.rejeitados} rejeitado(s), ${item.pendentes} pendente(s)`,
          `Ticket médio: ${formatarMoeda(item.ticketMedio)}`
        ];
      }
    );
  }

  private prepararTiposEquipamento(dados: DashboardDescontosResponse): void {
    const linhas = dados.porTipoEquipamento.slice(0, MAX_BARRAS_NO_GRAFICO);
    this.tipoEquipamentoData = {
      labels: linhas.map((item) => this.nomeOuPlaceholder(item.nome)),
      datasets: [
        {
          label: 'Valor dos itens', data: linhas.map((item) => item.valorItens),
          backgroundColor: CORES_CATEGORICAS.slice(0, linhas.length),
          borderRadius: 5, maxBarThickness: 18
        }
      ]
    };
    this.tipoEquipamentoOptions = this.opcoesBarraHorizontal(
      (indice) => {
        const item = linhas[indice];
        return [
          `${item.itens} item(ns) em ${item.descontos} termo(s)`,
          `Valor médio por item: ${formatarMoeda(item.valorMedioPorItem)}`,
          `Aprovação: ${formatarPercentual(item.taxaAprovacao)}`
        ];
      }
    );
  }

  /**
   * Mediana ao lado do P90 em cada etapa. É a comparação que mostra se o processo é
   * previsível: barras próximas significam ritmo constante; um P90 muito acima da
   * mediana significa que existe um punhado de casos que trava.
   */
  private prepararCiclo(dados: DashboardDescontosResponse): void {
    const ciclo = dados.ciclo;
    this.cicloData = {
      labels: ['Até a validação', 'Da validação à baixa', 'Ciclo completo'],
      datasets: [
        {
          label: 'Metade dos casos (mediana)',
          data: [ciclo.medianaDiasAteValidacao, ciclo.medianaDiasAteBaixa, ciclo.medianaDiasCicloTotal],
          backgroundColor: COR.validado, borderRadius: 5, maxBarThickness: 26
        },
        {
          label: '9 em cada 10 casos (P90)',
          data: [ciclo.p90DiasAteValidacao, ciclo.p90DiasAteBaixa, ciclo.p90DiasCicloTotal],
          backgroundColor: COR.pendente, borderRadius: 5, maxBarThickness: 26
        }
      ]
    };
    this.cicloOptions = {
      maintainAspectRatio: false,
      plugins: {
        legend: this.legenda(),
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            label: (contexto: any) =>
              `${contexto.dataset?.label}: ${Number(contexto.parsed?.y ?? 0).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dia(s)`
          }
        }
      },
      scales: {
        x: this.eixoX(),
        y: { ...this.eixoY(), ticks: { ...this.eixoY().ticks, callback: (v: number) => `${v}d` } }
      }
    };
  }

  // ------------------------------------------------------------------ //
  //  Blocos de configuração reaproveitados pelos gráficos
  // ------------------------------------------------------------------ //

  private opcoesBarraHorizontal(detalhe: (indice: number) => string[]): unknown {
    return {
      indexAxis: 'y',
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            label: (contexto: any) => formatarMoeda(Number(contexto.parsed?.x ?? 0)),
            afterLabel: (contexto: any) => detalhe(contexto.dataIndex)
          }
        }
      },
      scales: {
        x: {
          beginAtZero: true, border: { display: false },
          grid: { color: COR.grade, drawTicks: false },
          ticks: { color: COR.eixo, maxTicksLimit: 5, padding: 6, callback: (v: number) => formatarMoedaCompacta(v) }
        },
        y: {
          grid: { display: false }, border: { display: false },
          ticks: { color: '#486581', font: { size: 11 }, crossAlign: 'far' }
        }
      }
    };
  }

  private legenda(): unknown {
    return {
      display: true, position: 'top', align: 'end',
      labels: { boxWidth: 10, boxHeight: 10, usePointStyle: true, color: '#627d98', font: { size: 11 } }
    };
  }

  private tooltip(): Record<string, unknown> {
    return {
      mode: 'index', intersect: false, backgroundColor: COR.tooltip,
      titleColor: '#d9e2ec', bodyColor: '#fff', padding: 11, cornerRadius: 8
    };
  }

  private eixoX(): EixoChart {
    return {
      grid: { display: false }, border: { display: false },
      ticks: { color: COR.rotulo, font: { size: 10 }, maxTicksLimit: 12, maxRotation: 0, padding: 8 }
    };
  }

  private eixoY(): EixoChart {
    return {
      beginAtZero: true, grace: '8%', border: { display: false },
      grid: { color: COR.grade, drawTicks: false },
      ticks: { color: COR.eixo, maxTicksLimit: 5, padding: 10 }
    };
  }
}
