import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { ChartModule } from 'primeng/chart';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';

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
import { createUrlFilters, qp } from '../../../core/navigation';
import { map as mapear } from 'rxjs';
import { DashboardDrillComponent, DrillRecorte } from '../../../shared/components/dashboard/dashboard-drill.component';
import { Chamado, ChamadoFiltros } from '../../../core/models/chamado.models';

interface LinhaChamadoDrill {
  id: string;
  protocolo: string;
  assunto: string;
  prioridade: string;
  status: string;
  abertura: string;
  sla: string;
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
} from '../../copiloto/painel';
import {
  DashboardInsightPanelComponent,
  DashboardMetricCardComponent
} from '../../../shared/components/dashboard/dashboard-ui.components';
import { toSaoPauloIso } from '../../../shared/utils/dashboard-date.utils';
import { ThemeService } from '../../../core/services/theme.service';
import { ChamadosService } from '../../../core/services/chamados.service';
import {
  ChamadoPrioridade,
  ChamadoStatus,
  EquipeChamado,
  TopicoChamado
} from '../../../core/models/chamado.models';
import {
  DashboardChamadosFilter,
  DashboardChamadosResponse,
  FaixaBacklogChamado,
  GranularidadeDashboard,
  IndicadorComparativoChamado
} from '../../../core/models/chamado-dashboard.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import {
  CHAMADO_PRIORIDADE_LABELS,
  CHAMADO_STATUS_LABELS,
  PRIORIDADE_OPTIONS,
  STATUS_OPTIONS
} from '../chamados-ui';
import {
  montarDadosTendenciaChamados,
  opcoesGraficoTendenciaChamados
} from './chamados-dashboard-chart.config';
import { buildChamadosInsights } from './chamados-dashboard-insights';
import { atualizarPeriodicamente } from '../../../shared/components/dashboard/atualizacao-automatica';

export type DimensaoDesempenho = 'topico' | 'equipe' | 'atendente';

@Component({
  selector: 'app-chamados-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    DecimalPipe,
    DatePipe,
    ChartModule,
    SkeletonModule,
    TableModule,
    TooltipModule,
    CesButtonComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesIconButtonComponent,
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesSelectComponent,
    DashboardMetricCardComponent,
    DashboardInsightPanelComponent,
    CopilotoAreaComponent,
    CopilotoGatilhoComponent,
    DashboardDrillComponent
  ],
  providers: [CesDrillStateService],
  templateUrl: './chamados-dashboard.component.html',
  styleUrl: './chamados-dashboard.component.css'
})
export class ChamadosDashboardComponent implements OnInit {
  private readonly service = inject(ChamadosService);
  private readonly notificacoes = inject(NotificationService);
  private readonly themeService = inject(ThemeService);
  private readonly router = inject(Router);
  protected readonly drill = inject(CesDrillStateService);

  protected readonly loading = signal(false);
  protected readonly data = signal<DashboardChamadosResponse | null>(null);
  protected readonly atualizadoEm = signal<Date | null>(null);
  /** Filtros da consulta que produziu data() — é o recorte em tela e o que o Copiloto recebe. */
  private readonly filtrosAplicados = signal<DashboardChamadosFilter>({});

  private readonly atualizacao = atualizarPeriodicamente(() => this.buscar(this.filtrosAplicados(), true), {
    pode: () => !this.loading() && !!this.data()
  });
  protected readonly showManagementView = signal(true);

  // Lookups para filtros
  protected readonly topicos = signal<TopicoChamado[]>([]);
  protected readonly equipes = signal<EquipeChamado[]>([]);

  // Filtros de formulário
  protected dataInicio: Date | null = null;
  protected dataFim: Date | null = null;
  protected filter: DashboardChamadosFilter = {
    topicoId: [],
    equipeId: [],
    prioridade: [],
    status: [],
    slaVencido: undefined
  };

  protected readonly slaOptions: CesSelectOption<boolean | null>[] = [
    { label: 'Todos os prazos', value: null },
    { label: 'Apenas SLA estourado', value: true },
    { label: 'Apenas dentro do prazo', value: false }
  ];

  // Abas de Dimensão
  protected readonly dimensionTab = signal<DimensaoDesempenho>('topico');
  protected readonly dimensionTabs = [
    { label: 'Tópicos de atendimento', value: 'topico' as const, icon: 'pi-tags' },
    { label: 'Equipes de atendimento', value: 'equipe' as const, icon: 'pi-users' },
    { label: 'Atendentes responsáveis', value: 'atendente' as const, icon: 'pi-user' }
  ];

  protected readonly prioridadeOptions = PRIORIDADE_OPTIONS;
  protected readonly statusOptions = STATUS_OPTIONS;

  protected readonly insights = computed(() => {
    const current = this.data();
    return current ? buildChamadosInsights(current) : [];
  });

  protected readonly trendChartData = computed(() => {
    const current = this.data();
    if (!current?.serieTemporal) return { labels: [], datasets: [] };
    return montarDadosTendenciaChamados(current.serieTemporal, this.themeService.primaryColor());
  });

  protected readonly trendChartOptions = computed(() => opcoesGraficoTendenciaChamados());

  private readonly filtrosUrl = createUrlFilters<{
    inicio: Date;
    fim: Date;
    topico: string[];
    equipe: string[];
    prioridade: string[];
    status: string[];
    sla: boolean;
  }>({
    inicio: qp.date('inicio'),
    fim: qp.date('fim'),
    topico: qp.list('topico'),
    equipe: qp.list('equipe'),
    prioridade: qp.list('prioridade'),
    status: qp.list('status'),
    sla: qp.boolean('sla')
  });
  /** Datas da consulta em tela (os chips mostram o recorte aplicado, não o que está sendo editado). */
  private readonly periodoAplicado = signal<{ inicio: Date | null; fim: Date | null }>({ inicio: null, fim: null });

  protected readonly topicoOpcoes = computed<CesSelectOption<string>[]>(() => this.topicos().map((t) => ({ label: t.nome, value: t.id })));
  protected readonly equipeOpcoes = computed<CesSelectOption<string>[]>(() => this.equipes().map((e) => ({ label: e.nome, value: e.id })));

  protected readonly chips = computed<CesFilterChip[]>(() => {
    const f = this.filtrosAplicados();
    const { inicio, fim } = this.periodoAplicado();
    const chips: CesFilterChip[] = [];
    const lista = (chave: string, rotulo: string, ids: readonly string[] | undefined, nome: (id: string) => string, plural: string) => {
      if (!ids?.length) return;
      chips.push({ key: chave, label: `${rotulo}: ${ids.length === 1 ? nome(ids[0]) : `${ids.length} ${plural}`}` });
    };
    if (inicio && fim) chips.push({ key: 'periodo', label: `Período: ${inicio.toLocaleDateString('pt-BR')} a ${fim.toLocaleDateString('pt-BR')}`, removable: false });
    lista('topico', 'Tópico', f.topicoId, (id) => this.topicoOpcoes().find((o) => o.value === id)?.label ?? 'selecionado', 'selecionados');
    lista('equipe', 'Equipe', f.equipeId, (id) => this.equipeOpcoes().find((o) => o.value === id)?.label ?? 'selecionada', 'selecionadas');
    lista('prioridade', 'Prioridade', f.prioridade, (v) => this.prioridadeOptions.find((o) => o.value === v)?.label ?? v, 'selecionadas');
    lista('status', 'Status', f.status, (v) => this.statusOptions.find((o) => o.value === v)?.label ?? v, 'selecionados');
    if (f.slaVencido !== undefined && f.slaVencido !== null) chips.push({ key: 'sla', label: f.slaVencido ? 'SLA estourado' : 'Dentro do prazo' });
    return chips;
  });

  constructor() {
    const hoje = new Date();
    const url = this.filtrosUrl.read();
    this.dataInicio = url.inicio ?? new Date(hoje.getFullYear(), hoje.getMonth(), 1);
    this.dataFim = url.fim ?? hoje;
    this.filter = {
      topicoId: url.topico ?? [],
      equipeId: url.equipe ?? [],
      prioridade: (url.prioridade ?? []) as DashboardChamadosFilter['prioridade'],
      status: (url.status ?? []) as DashboardChamadosFilter['status'],
      slaVencido: url.sla ?? undefined
    };
  }

  protected removerFiltro(chave: string): void {
    const campo = ({ topico: 'topicoId', equipe: 'equipeId', prioridade: 'prioridade', status: 'status' } as Record<string, keyof DashboardChamadosFilter>)[chave];
    if (campo) this.filter = { ...this.filter, [campo]: [] };
    if (chave === 'sla') this.filter = { ...this.filter, slaVencido: undefined };
    this.aplicar();
  }

  ngOnInit(): void {
    this.carregarLookups();
    this.aplicar();
  }

  protected carregarLookups(): void {
    forkJoin({
      topicos: this.service.listarTopicosAtivos().pipe(catchError(() => of([]))),
      equipes: this.service.listarEquipes({ ativo: true, size: 100 }).pipe(catchError(() => of({ content: [] })))
    }).subscribe({
      next: ({ topicos, equipes }) => {
        this.topicos.set(topicos);
        this.equipes.set(equipes.content ?? []);
      }
    });
  }

  protected aplicar(): void {
    this.filtrosUrl.write({
      inicio: this.dataInicio ?? undefined,
      fim: this.dataFim ?? undefined,
      topico: this.filter.topicoId,
      equipe: this.filter.equipeId,
      prioridade: this.filter.prioridade,
      status: this.filter.status,
      sla: this.filter.slaVencido
    });
    const periodo = { inicio: this.dataInicio, fim: this.dataFim };
    const payload: DashboardChamadosFilter = {
      ...this.filter,
      inicio: this.dataInicio ? toSaoPauloIso(this.dataInicio, false) : null,
      fim: this.dataFim ? toSaoPauloIso(this.dataFim, true) : null
    };

    this.periodoAplicado.set(periodo);
    this.buscar(payload);
  }

  /** Busca um recorte; a atualização automática repete o aplicado sem mostrar carregamento nem avisar erro. */
  private buscar(payload: DashboardChamadosFilter, silencioso = false): void {
    if (!silencioso) this.loading.set(true);
    this.service.obterDashboard(payload).pipe(
      catchError((error) => {
        if (!silencioso) this.notificacoes.error(getErrorMessage(error), { title: 'Erro ao carregar o painel' });
        return of(null);
      })
    ).subscribe({
      next: (res) => {
        if (silencioso && !res) return;
        this.data.set(res);
        this.filtrosAplicados.set(payload);
        this.atualizadoEm.set(new Date());
        this.loading.set(false);
      }
    });
  }

  protected limpar(): void {
    const hoje = new Date();
    this.dataInicio = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
    this.dataFim = hoje;
    this.filter = {
      topicoId: [],
      equipeId: [],
      prioridade: [],
      status: [],
      slaVencido: undefined
    };
    this.aplicar();
  }

  /** Contexto do dashboard para o Copiloto: o recorte aplicado e os números em tela. */
  protected readonly contextoCopiloto = computed<CopilotoDashboardContext | null>(() => {
    const d = this.data();
    const f = this.filtrosAplicados();
    const nomes = <T>(itens: readonly T[], ids: readonly string[] | null | undefined, id: (i: T) => string, nome: (i: T) => string) =>
      rotuloDasOpcoes(itens, ids ?? [], id, nome);
    const indicador = (chave: string, rotulo: string, formato: 'inteiro' | 'percentual', melhor: 'maior' | 'menor') => {
      const ind = d?.indicadores?.[chave];
      return metricaPainel(chave, rotulo, ind?.valor, formato, {
        descricao: ind?.definicao,
        comparacao: ind && ind.anterior
          ? { rotulo: 'vs. período anterior', valorAnterior: ind.anterior, variacaoPercentual: ind.variacaoPercentual, melhorQuando: melhor }
          : undefined,
      });
    };
    const dimensao = this.dimensionTab();
    const linhas: { nome: string; total: number; resolvidos: number; sla: number }[] = !d ? [] : dimensao === 'topico'
      ? d.desempenhoPorTopico.map((i) => ({ nome: i.nome, total: i.totalChamados, resolvidos: i.resolvidos, sla: i.taxaSlaCumprido }))
      : dimensao === 'equipe'
        ? d.desempenhoPorEquipe.map((i) => ({ nome: i.nome, total: i.totalChamados, resolvidos: i.resolvidos, sla: i.taxaSlaResolucao }))
        : d.desempenhoPorAtendente.map((i) => ({ nome: i.nome || 'Não atribuído', total: i.totalChamados, resolvidos: i.resolvidos, sla: i.taxaSlaResolucao }));
    const rotuloDimensao = this.dimensionTabs.find((tab) => tab.value === dimensao)?.label ?? 'Dimensão';
    const serie = d?.serieTemporal ?? [];
    return {
      id: 'chamados.gestao',
      nome: 'Desempenho operacional e SLA de chamados',
      modulo: 'Chamados',
      descricao: 'Volume de chamados, tempo de ciclo, cumprimento de SLA, fila em aberto por idade e prioridade e desempenho por tópico, equipe e atendente.',
      rota: '/chamados/dashboard',
      periodo: d ? periodoPainel(d.inicio, d.fim) : periodoPainel(this.dataInicio, this.dataFim),
      filtros: [
        filtroPainel('topico', 'Tópico', nomes(this.topicos(), f.topicoId, (i) => i.id, (i) => i.nome), f.topicoId ?? []),
        filtroPainel('equipe', 'Equipe', nomes(this.equipes(), f.equipeId, (i) => i.id, (i) => i.nome), f.equipeId ?? []),
        filtroPainel('prioridade', 'Prioridade', (f.prioridade ?? []).map((p) => this.rotuloPrioridade(p)), f.prioridade ?? []),
        filtroPainel('status', 'Status', (f.status ?? []).map((s) => this.rotuloStatus(s)), f.status ?? []),
        filtroPainel('sla', 'Prazo / SLA', f.slaVencido === true ? 'Apenas SLA estourado' : f.slaVencido === false ? 'Apenas dentro do prazo' : null),
      ],
      metricas: d
        ? [
            indicador('totalChamados', 'Chamados registrados', 'inteiro', 'menor'),
            indicador('chamadosResolvidos', 'Chamados resolvidos', 'inteiro', 'maior'),
            indicador('backlogTotal', 'Fila em aberto (backlog)', 'inteiro', 'menor'),
            indicador('taxaSlaResolucao', 'SLA de resolução', 'percentual', 'maior'),
            metricaPainel('taxaResolucao', 'Taxa de resolução', d.taxaResolucao, 'percentual'),
            metricaPainel('tempoMedianoResolucaoHoras', 'Tempo mediano de resolução', d.tempoMedianoResolucaoHoras, 'horas', {
              descricao: `P90 ${d.tempoP90ResolucaoHoras.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} h`,
            }),
            metricaPainel('tempoMedianoPrimeiraRespostaHoras', '1ª resposta (mediana)', d.tempoMedianoPrimeiraRespostaHoras, 'horas', {
              descricao: `SLA de 1ª resposta ${d.taxaSlaPrimeiraRespostaPercentual.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`,
            }),
            metricaPainel('taxaReabertura', 'Reaberturas pós-solução', d.taxaReabertura, 'percentual', { descricao: `${d.totalReabertos} reabertos` }),
            metricaPainel('totalCriticos', 'Chamados críticos', d.totalCriticos, 'inteiro'),
            metricaPainel('semResponsavel', 'Chamados sem responsável', d.qualidadeDados.chamadosSemResponsavel, 'inteiro', {
              descricao: `${d.qualidadeDados.chamadosEstagnadosSemInteracao} estagnados sem interação`,
            }),
          ]
        : [],
      graficos: d
        ? [
            graficoPainel('tendencia', 'linha', 'Evolução de abertura e resolução', serie.map((i) => i.rotulo), [
              { nome: 'Abertos', valores: serie.map((i) => i.totalAbertos) },
              { nome: 'Resolvidos', valores: serie.map((i) => i.totalResolvidos) },
              { nome: 'Vencidos', valores: serie.map((i) => i.totalVencidos) },
            ], { metrica: 'chamados', dimensao: `período (${this.rotuloGranularidade(d.granularidade)})`, formato: 'inteiro' }),
            graficoPainel('porPrioridade', 'barra', 'Distribuição por prioridade', d.volumePorPrioridade.map((v) => this.rotuloPrioridade(v.prioridade)), [
              { nome: 'Atual', valores: d.volumePorPrioridade.map((v) => v.total) },
              { nome: 'Período anterior', valores: d.volumePorPrioridade.map((v) => v.anterior) },
            ], { metrica: 'chamados', dimensao: 'prioridade', formato: 'inteiro' }),
            graficoPainel('backlogPorIdade', 'barra', 'Fila de pendências por tempo em aberto', d.backlogPorIdade.map((b) => this.rotuloFaixa(b.faixa, b.rotulo)),
              d.backlogPorIdade.map((b) => b.total), { metrica: 'chamados em aberto', dimensao: 'tempo em aberto', formato: 'inteiro' }),
            graficoPainel(`desempenho.${dimensao}`, 'tabela', `Desempenho por ${rotuloDimensao.toLowerCase()}`, linhas.map((l) => l.nome), [
              { nome: 'Chamados', valores: linhas.map((l) => l.total) },
              { nome: 'Resolvidos', valores: linhas.map((l) => l.resolvidos) },
              { nome: 'SLA (%)', valores: linhas.map((l) => l.sla) },
            ], { metrica: 'chamados', dimensao: rotuloDimensao.toLowerCase(), totalCategorias: linhas.length * 3 }),
          ]
        : [],
      datasets: ['chamados'],
      extras: d ? { 'Período de comparação': this.periodoComparativo(d).replace('Comparado ao período de ', '') } : undefined,
      sugestoes: [
        { rotulo: 'Chamados mais antigos', icone: 'pi pi-clock', requer: ['dados'],
          pergunta: 'Quais chamados deste recorte estão há mais tempo em aberto, com qual prioridade e quem é o responsável?' },
        { rotulo: 'Por que o SLA mudou?', icone: 'pi pi-arrow-right-arrow-left', requer: ['dados', 'analise'],
          pergunta: 'O que explica o cumprimento de SLA deste recorte em relação ao período anterior? Detalhe por tópico e equipe.' },
      ],
    };
  });

  protected irParaFila(): void {
    this.router.navigate(['/chamados/fila']);
  }

  /** Camada 2: chamados do tópico/equipe no período do painel, sem sair dele. */
  protected detalhar(dimensao: 'topico' | 'equipe', id: string): void {
    this.drill.open({ dimension: dimensao, value: id });
  }

  private readonly colunasDrill: CesDataTableColumn<LinhaChamadoDrill>[] = [
    { key: 'protocolo', header: 'Protocolo', priority: 1 },
    { key: 'assunto', header: 'Assunto', priority: 1 },
    { key: 'prioridade', header: 'Prioridade', priority: 2 },
    { key: 'status', header: 'Status', priority: 1 },
    { key: 'abertura', header: 'Abertura', type: 'date', priority: 2 },
    { key: 'sla', header: 'SLA', priority: 1 },
  ];

  protected readonly recorteDrill = computed<DrillRecorte<LinhaChamadoDrill> | null>(() => {
    const alvo = this.drill.target();
    const dados = this.data();
    if (!alvo || !dados || !['topico', 'equipe'].includes(alvo.dimension)) return null;
    const topico = alvo.dimension === 'topico';
    const item = topico
      ? dados.desempenhoPorTopico.find((linha) => linha.id === alvo.value)
      : dados.desempenhoPorEquipe.find((linha) => linha.id === alvo.value);
    if (!item) return null;
    const filtros = this.filtrosAplicados();
    const unico = <T>(lista: T[] | undefined) => (lista?.length === 1 ? lista[0] : undefined);
    const filtro: ChamadoFiltros = {
      dataAberturaInicio: filtros.inicio ?? undefined,
      dataAberturaFim: filtros.fim ?? undefined,
      ...(topico ? { topicoId: item.id } : { equipeId: item.id }),
      ...(!topico && unico(filtros.topicoId) ? { topicoId: unico(filtros.topicoId) } : {}),
      ...(topico && unico(filtros.equipeId) ? { equipeId: unico(filtros.equipeId) } : {}),
      ...(unico(filtros.prioridade) ? { prioridade: unico(filtros.prioridade) } : {}),
      ...(unico(filtros.status) ? { status: unico(filtros.status) } : {}),
      page: 0,
      size: 100,
      sort: 'dataAbertura,desc',
    };
    const multiplos = (['topicoId', 'equipeId', 'prioridade', 'status', 'usuarioResponsavelId', 'usuarioSolicitanteId'] as const)
      .some((chave) => (filtros[chave]?.length ?? 0) > 1 || (['usuarioResponsavelId', 'usuarioSolicitanteId'].includes(chave) && (filtros[chave]?.length ?? 0) > 0));
    return {
      chave: `${alvo.dimension}:${item.id}:${filtro.dataAberturaInicio}:${filtro.dataAberturaFim}`,
      eyebrow: topico ? 'Tópico' : 'Equipe de atendimento',
      titulo: item.nome,
      descricao: 'Chamados abertos no período do painel, com os filtros aplicados.',
      kpis: [
        { rotulo: 'Chamados', valor: String(item.totalChamados), detalhe: `${item.resolvidos} resolvido(s) · ${item.pendentes} pendente(s)` },
        { rotulo: 'Resolução mediana', valor: `${item.tempoMedianoResolucaoHoras.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} h` },
      ],
      aviso: multiplos || filtros.slaVencido
        ? 'A fila aceita um valor por filtro: filtros do painel com vários valores, responsável, solicitante e SLA vencido não foram aplicados a esta lista.'
        : undefined,
      colunas: this.colunasDrill,
      carregar: () => this.service.listarFila(filtro).pipe(mapear((pagina) => ({
        total: pagina.page.totalElements,
        linhas: pagina.content.map((chamado: Chamado) => ({
          id: chamado.id,
          protocolo: chamado.protocolo,
          assunto: chamado.assunto,
          prioridade: CHAMADO_PRIORIDADE_LABELS[chamado.prioridade] ?? chamado.prioridade,
          status: CHAMADO_STATUS_LABELS[chamado.status] ?? chamado.status,
          abertura: chamado.dataAbertura,
          sla: chamado.slaResolucaoVencido ? 'Resolução vencida' : chamado.slaPrimeiraRespostaVencido ? '1ª resposta vencida' : 'No prazo',
        })),
      }))),
      rotuloLinha: (linha) => `Chamado ${linha.protocolo}`,
      abrirLinha: (linha) => this.router.navigate(['/chamados/fila', linha.id]),
      rotuloVerTudo: 'Ver na fila',
      verTudo: () => this.abrirFilaComFiltro(topico ? { topicoId: [item.id] } : { equipeId: [item.id] }),
      vazio: 'Nenhum chamado neste recorte',
      nomeArquivo: `chamados-${alvo.dimension}`,
    };
  });

  protected abrirFilaComFiltro(queryParams?: Record<string, any>): void {
    this.router.navigate(['/chamados/fila'], { queryParams });
  }

  protected abrirPrioridade(index: number): void {
    const prioridades = this.data()?.prioridades;
    if (prioridades && prioridades[index]) {
      const item = prioridades[index];
      if (item.tipo === 'SLA_ESTOURADO') {
        this.abrirFilaComFiltro({ slaVencido: true });
      } else {
        this.irParaFila();
      }
    } else {
      this.irParaFila();
    }
  }

  protected comparison(indicador?: IndicadorComparativoChamado): string {
    if (!indicador) return '';
    const varPct = indicador.variacaoPercentual ?? 0;
    if (indicador.anterior === 0) {
      return 'Sem dados no período anterior';
    }
    const sinal = varPct > 0 ? '+' : '';
    return `${sinal}${varPct.toFixed(1)}% vs. período anterior`;
  }

  protected descricaoIndicador(indicador?: IndicadorComparativoChamado): string {
    if (!indicador?.definicao) return '';
    return `${indicador.definicao} · Período anterior: ${indicador.anterior}`;
  }

  protected periodoComparativo(d: DashboardChamadosResponse): string {
    if (!d.periodo?.comparacaoInicio || !d.periodo?.comparacaoFim) return '';
    const inicio = new Date(d.periodo.comparacaoInicio).toLocaleDateString('pt-BR');
    const fim = new Date(d.periodo.comparacaoFim).toLocaleDateString('pt-BR');
    return `Comparado ao período de ${inicio} a ${fim}`;
  }

  protected rotuloGranularidade(g?: GranularidadeDashboard): string {
    return {
      DIA: 'diária',
      MES: 'mensal',
      ANO: 'anual'
    }[g ?? 'DIA'] ?? 'diária';
  }

  protected rotuloPrioridade(p: ChamadoPrioridade): string {
    return CHAMADO_PRIORIDADE_LABELS[p] ?? p;
  }

  protected rotuloStatus(s: ChamadoStatus): string {
    return CHAMADO_STATUS_LABELS[s] ?? s;
  }

  protected tomPrioridade(p: ChamadoPrioridade): 'info' | 'success' | 'warning' | 'danger' {
    return {
      BAIXA: 'info' as const,
      NORMAL: 'success' as const,
      ALTA: 'warning' as const,
      CRITICA: 'danger' as const
    }[p] ?? 'info';
  }

  protected subtituloPrioridade(p: ChamadoPrioridade): string {
    return {
      BAIXA: 'Impacto leve',
      NORMAL: 'Rotina padrão',
      ALTA: 'Alto impacto',
      CRITICA: 'Urgência máxima'
    }[p] ?? '';
  }

  protected rotuloFaixa(faixa: string, rotuloPadrao?: string): string {
    if (rotuloPadrao) return rotuloPadrao;
    return {
      '<24h': 'Até 24 horas',
      '1-3d': '1 a 3 dias',
      '4-7d': '4 a 7 dias',
      '8-15d': '8 a 15 dias',
      '>15d': 'Mais de 15 dias'
    }[faixa] ?? faixa;
  }

  protected subtituloFaixa(faixa: string): string {
    return {
      '<24h': 'Recentes',
      '1-3d': 'Em andamento',
      '4-7d': 'Atenção',
      '8-15d': 'Gargalo',
      '>15d': 'Crítico / Crônico'
    }[faixa] ?? '';
  }

  protected tomFaixa(faixa: string): 'info' | 'success' | 'warning' | 'orange' | 'danger' {
    return {
      '<24h': 'info' as const,
      '1-3d': 'success' as const,
      '4-7d': 'warning' as const,
      '8-15d': 'orange' as const,
      '>15d': 'danger' as const
    }[faixa] ?? 'info';
  }

  protected classeVariacao(variacaoPercentual: number, anterior: number): string {
    if (anterior === 0) return 'dash-badge';
    if (variacaoPercentual < 0) return 'dash-badge dash-badge--success';
    if (variacaoPercentual > 0) return 'dash-badge dash-badge--warning';
    return 'dash-badge';
  }

  protected iconeVariacao(variacaoPercentual: number): string {
    if (variacaoPercentual < 0) return 'pi pi-arrow-down';
    if (variacaoPercentual > 0) return 'pi pi-arrow-up';
    return 'pi pi-minus';
  }

  protected formatarVariacao(variacaoPercentual: number, anterior: number): string {
    if (anterior === 0) return 'Novo';
    const sinal = variacaoPercentual > 0 ? '+' : '';
    return `${sinal}${variacaoPercentual.toFixed(1)}%`;
  }

  protected barWidth(pct: number): number {
    return Math.max(2, Math.min(100, pct ?? 0));
  }

  protected percentualPrioridade(total: number, valor: number): number {
    if (!total || total <= 0) return 0;
    return (valor / total) * 100;
  }

  protected totalBacklog(d: DashboardChamadosResponse): number {
    if (!d.backlogPorIdade) return 0;
    return d.backlogPorIdade.reduce((acc: number, curr: FaixaBacklogChamado) => acc + (curr.total ?? 0), 0);
  }

  protected hasDataQualityWarning(d: DashboardChamadosResponse): boolean {
    return !!d.qualidadeDados && (
      d.qualidadeDados.chamadosSemResponsavel > 0 ||
      d.qualidadeDados.chamadosEstagnadosSemInteracao > 0
    );
  }
}
