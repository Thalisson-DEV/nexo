import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesBooleanControlComponent,
  CesButtonComponent,
  CesChartComponent,
  CesChartPointSelection,
  CesDatepickerComponent,
  CesFilterChip,
  CesFilterPanelComponent,
  CesFormDrawerComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesSelectOption,
  CesDrillStateService,
  CesStatusComponent,
  NotificationService
} from '@ces/ui';

import { createUrlFilters, qp } from '../../../../core/navigation';
import { getErrorMessage } from '../../../../core/models/shared.models';
import {
  ConteudoExportacaoPainel,
  ExportacaoPainelSolicitacoes,
  NivelExportacaoPainel,
  PainelSolicitacoesFiltros,
  PainelSolicitacoesResponse,
  ProcessoSolicitacao,
  SituacaoExportacaoPainel,
  SolicitacaoDoPainel
} from '../../../../core/models/painel-solicitacoes.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { DashboardMetricCardComponent, MetricTone } from '../../../../shared/components/dashboard/dashboard-ui.components';
import { DashboardAnaliseComponent } from '../../../../shared/components/dashboard/dashboard-analise.component';
import { AlvoAnalise, VistaAnalise } from '../../../../shared/components/dashboard/dashboard-analise.models';
import { NavegacaoAnaliseService } from '../../../../shared/components/dashboard/dashboard-analise.navegacao';
import { DashboardVisaoGerencialComponent } from '../../../../shared/components/dashboard/dashboard-visao-gerencial.component';
import { RecorteDashboardService } from '../../../execucao/dashboards/recorte-dashboard.service';
import { graficosDaAnalise } from '../../../execucao/dashboards/analise/analise-copiloto';
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
} from '../../../copiloto/painel';
import {
  ETAPAS,
  FAIXAS_CICLO,
  LinhaRanking,
  ModoPainel,
  ROTULO_PROCESSO,
  SEM_CHAVE,
  TIPOS_OBRA,
  TIPOS_TECNICOS,
  atrasada,
  contar,
  diaHora,
  diaMes,
  diasDeAtraso,
  diasDoPeriodo,
  distribuicaoDoCiclo,
  fatia,
  filaDeAtencao,
  fluxoDasAbertas,
  idadeDasAbertas,
  idadeDias,
  indicadores,
  inteiro,
  isoLocal,
  materiais,
  motivosDePendencia,
  percentual,
  plural,
  quantidade,
  ranking,
  recortar,
  serieDiaria,
  rotaDaSolicitacao,
  rotuloEtapa,
  rotuloTipo,
  situacao,
  tempo,
  variacao,
  variacaoSobre
} from './solicitacoes-painel.analise';
import { visaoGerencial, vistaPainel } from './solicitacoes-painel.visao';
import { atualizarPeriodicamente } from '../../../../shared/components/dashboard/atualizacao-automatica';

type PeriodoRapido = '7-dias' | 'mes' | 'mes-anterior' | '30-dias' | '90-dias';
type OrdemRanking = 'volume' | 'atrasos' | 'ciclo';
type VisaoSerie = 'situacao' | 'processo';

interface ConfigModo {
  readonly titulo: string;
  readonly icone: string;
  readonly descricao: string;
  readonly rota: string;
  readonly copilotoId: string;
}

const MODOS: Record<ModoPainel, ConfigModo> = {
  GERAL: {
    titulo: 'Painel de solicitações',
    icone: 'pi pi-chart-pie',
    descricao: 'Comercial e técnico juntos: volume, fila em aberto, atrasos, tempo de atendimento e onde estão os gargalos.',
    rota: '/almoxarifado/dashboard/solicitacoes',
    copilotoId: 'almoxarifado.solicitacoes'
  },
  COMERCIAL: {
    titulo: 'Solicitações comerciais',
    icone: 'pi pi-shopping-cart',
    descricao: 'Pedidos de material das equipes comerciais: aprovação, retirada, atendimento das quantidades e prazos de saque.',
    rota: '/almoxarifado/dashboard/solicitacoes/comercial',
    copilotoId: 'almoxarifado.solicitacoes.comercial'
  },
  TECNICO: {
    titulo: 'Solicitações técnicas',
    icone: 'pi pi-wrench',
    descricao: 'Adições e devoluções das obras (221/921/222/922): planejamento, separação, retirada e devoluções pendentes.',
    rota: '/almoxarifado/dashboard/solicitacoes/tecnico',
    copilotoId: 'almoxarifado.solicitacoes.tecnico'
  }
};

const ABAS: { modo: ModoPainel; rotulo: string; icone: string }[] = [
  { modo: 'GERAL', rotulo: 'Visão geral', icone: 'pi pi-chart-pie' },
  { modo: 'COMERCIAL', rotulo: 'Comercial', icone: 'pi pi-shopping-cart' },
  { modo: 'TECNICO', rotulo: 'Técnico', icone: 'pi pi-wrench' }
];

/**
 * Painel de solicitações de material em três lentes — geral, comercial e técnico — sobre a mesma
 * base analítica: cada número abre a análise detalhada até as solicitações que o compõem, a visão
 * gerencial aponta o que agir primeiro e a planilha leva o recorte inteiro para o Excel.
 */
@Component({
  selector: 'app-solicitacoes-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    CesAsyncStateComponent,
    CesBooleanControlComponent,
    CesButtonComponent,
    CesChartComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesPageHeaderComponent,
    CesSelectComponent,
    CesStatusComponent,
    DashboardMetricCardComponent,
    DashboardAnaliseComponent,
    DashboardVisaoGerencialComponent,
    CopilotoAreaComponent,
    CopilotoGatilhoComponent,
    CopilotoPerguntarComponent
  ],
  providers: [CesDrillStateService, NavegacaoAnaliseService],
  templateUrl: './solicitacoes-dashboard.component.html',
  styleUrl: './solicitacoes-dashboard.component.css'
})
export class SolicitacoesDashboardComponent implements OnInit {
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly copiloto = inject(CopilotoPainelService);
  protected readonly recorte = inject(RecorteDashboardService);
  protected readonly analise = inject(NavegacaoAnaliseService);
  protected readonly copilotoDisponivel = this.copiloto.disponivelParaUsuario();

  protected readonly modo: ModoPainel = (inject(ActivatedRoute).snapshot.data['modoPainel'] as ModoPainel) ?? 'GERAL';
  protected readonly config = MODOS[this.modo];
  protected readonly abas = ABAS.map((a) => ({ ...a, rota: MODOS[a.modo].rota }));

  protected readonly etapas = ETAPAS;
  protected readonly tiposTecnicos = TIPOS_TECNICOS;
  protected readonly tiposObra = TIPOS_OBRA;
  protected readonly rotuloProcesso = ROTULO_PROCESSO;
  protected readonly inteiro = inteiro;
  protected readonly percentual = percentual;
  protected readonly quantidade = quantidade;
  protected readonly tempo = tempo;
  protected readonly variacao = variacao;
  protected readonly diaMes = diaMes;
  protected readonly diaHora = diaHora;
  protected readonly plural = plural;
  protected readonly rotuloEtapa = rotuloEtapa;
  protected readonly rotuloTipo = rotuloTipo;
  protected readonly rotaDaSolicitacao = rotaDaSolicitacao;
  protected readonly semChave = SEM_CHAVE;

  // ------------------------------------------------------------------ estado e filtros

  protected readonly loading = signal(false);
  protected readonly exportando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly data = signal<PainelSolicitacoesResponse | null>(null);
  private readonly aplicado = signal<PainelSolicitacoesFiltros>({});

  private readonly atualizacao = atualizarPeriodicamente(() => this.buscar(this.aplicado(), true), {
    pode: () => !this.loading() && !!this.data()
  });

  protected readonly hojeLocal = new Date();
  protected readonly inicio = signal<Date | null>(null);
  protected readonly fim = signal<Date | null>(null);
  protected readonly idBase = signal<string | null>(null);
  protected readonly idProcesso = signal<string | null>(null);
  protected readonly idEquipe = signal<string | null>(null);
  protected readonly idAlmoxarifado = signal<string | null>(null);
  private readonly almoxarifados = signal<CesSelectOption<string>[]>([]);

  protected readonly visaoSerie = signal<VisaoSerie>('situacao');
  protected readonly ordemEquipes = signal<OrdemRanking>('volume');
  protected readonly sentidoMateriais = signal<'SAIDA' | 'DEVOLUCAO'>('SAIDA');

  protected readonly periodosRapidos: { valor: PeriodoRapido; rotulo: string }[] = [
    { valor: '7-dias', rotulo: '7 dias' },
    { valor: 'mes', rotulo: 'Este mês' },
    { valor: 'mes-anterior', rotulo: 'Mês anterior' },
    { valor: '30-dias', rotulo: '30 dias' },
    { valor: '90-dias', rotulo: '90 dias' }
  ];
  protected readonly ordensRanking: { valor: OrdemRanking; rotulo: string }[] = [
    { valor: 'volume', rotulo: 'Volume' },
    { valor: 'atrasos', rotulo: 'Atrasos' },
    { valor: 'ciclo', rotulo: 'Ciclo' }
  ];

  private readonly filtrosUrl = createUrlFilters<{
    inicio: Date;
    fim: Date;
    idBaseOperacional: string;
    idProcesso: string;
    idEquipe: string;
    idAlmoxarifado: string;
  }>({
    inicio: qp.date('inicio'),
    fim: qp.date('fim'),
    idBaseOperacional: qp.text('base'),
    idProcesso: qp.text('processo'),
    idEquipe: qp.text('equipe'),
    idAlmoxarifado: qp.text('almoxarifado')
  });

  protected readonly opcoesAlmoxarifados = computed(() => this.almoxarifados());

  protected readonly periodoAtivo = computed<PeriodoRapido | null>(() => {
    const i = this.inicio();
    const f = this.fim();
    if (!i || !f) return null;
    for (const p of this.periodosRapidos) {
      const [de, ate] = intervaloRapido(p.valor);
      if (isoLocal(de) === isoLocal(i) && isoLocal(ate) === isoLocal(f)) return p.valor;
    }
    return null;
  });

  // ------------------------------------------------------------------ dados derivados

  protected readonly r = computed(() => {
    const d = this.data();
    return d ? recortar(d, this.modo) : null;
  });

  protected readonly dias = computed(() => {
    const d = this.data();
    return d ? diasDoPeriodo(d.inicio, d.fim) : 1;
  });

  protected readonly ind = computed(() => indicadores(this.r()?.periodo ?? [], this.dias()));

  protected readonly atrasadas = computed(() => {
    const r = this.r();
    return r ? r.abertas.filter((l) => atrasada(l, r.hoje)) : [];
  });

  protected readonly serie = computed(() => {
    const d = this.data();
    const r = this.r();
    if (!d || !r) return null;
    const s = serieDiaria(r.periodo, d.inicio, d.fim);
    const series = this.visaoSerie() === 'processo' && this.modo === 'GERAL'
      ? [
          { name: 'Comercial', values: s.comercial },
          { name: 'Técnico', values: s.tecnico }
        ]
      : [
          { name: 'Concluídas', values: s.concluidas },
          { name: 'Em aberto', values: s.abertas },
          { name: 'Recusadas', values: s.recusadas }
        ];
    return { ...s, series };
  });

  protected readonly fluxo = computed(() => {
    const r = this.r();
    return r ? fluxoDasAbertas(r.abertas, this.modo, r.hoje, r.agora) : [];
  });
  protected readonly maiorEtapa = computed(() => Math.max(1, ...this.fluxo().map((e) => e.total)));

  protected readonly idade = computed(() => {
    const r = this.r();
    return r ? idadeDasAbertas(r.abertas, r.hoje, r.agora) : [];
  });
  protected readonly maiorIdade = computed(() => Math.max(1, ...this.idade().map((f) => f.total)));

  protected readonly ciclo = computed(() => ({
    rotulos: FAIXAS_CICLO.map((f) => f.rotulo),
    series: [{ name: 'Concluídas', values: distribuicaoDoCiclo(this.r()?.periodo ?? []) }]
  }));

  protected readonly atencao = computed(() => {
    const r = this.r();
    return r ? filaDeAtencao(r.abertas, r.hoje, r.agora, 8) : [];
  });

  protected readonly equipes = computed(() => {
    const r = this.r();
    if (!r) return [];
    const linhas = ranking(r.periodo, r.backlog, r.hoje, (l) => l.idEquipe, (l) => l.equipe,
      (l) => [l.baseOperacional, l.processo].filter(Boolean).join(' · ') || null, 'Sem equipe');
    return ordenarRanking(linhas, this.ordemEquipes()).slice(0, 12);
  });
  protected readonly totalEquipes = computed(() => {
    const r = this.r();
    return r ? new Set([...r.periodo, ...r.backlog].map((l) => l.idEquipe ?? SEM_CHAVE)).size : 0;
  });

  protected readonly almoxRanking = computed(() => {
    const r = this.r();
    return r ? ranking(r.periodo, r.backlog, r.hoje, (l) => l.idAlmoxarifado, (l) => l.almoxarifado).slice(0, 8) : [];
  });

  protected readonly listaMateriais = computed(() => materiais(this.r()?.materiais ?? [], this.sentidoMateriais()).slice(0, 12));
  protected readonly temDevolucoes = computed(() => (this.r()?.materiais ?? []).some((m) => m.sentido === 'DEVOLUCAO'));

  protected readonly motivosRejeicao = computed(() =>
    contar((this.r()?.periodo ?? []).filter((l) => situacao(l) === 'recusada').map((l) => l.motivoRejeicao), 'Sem motivo informado').slice(0, 6)
  );
  protected readonly motivosPendencia = computed(() => {
    const r = this.r();
    return r ? motivosDePendencia([...r.periodo, ...r.backlog]).slice(0, 6) : [];
  });

  /** Técnico: por tipo 221/921/222/922. */
  protected readonly porTipo = computed(() => {
    const r = this.r();
    if (!r || this.modo === 'COMERCIAL') return [];
    return ranking(r.periodo.filter((l) => l.contexto === 'TECNICO'), r.backlog.filter((l) => l.contexto === 'TECNICO'), r.hoje,
      (l) => l.tipoTecnico, (l) => rotuloTipo(l.tipoTecnico)).sort((a, b) => a.chave.localeCompare(b.chave));
  });

  protected readonly porTipoObra = computed(() => {
    const r = this.r();
    if (!r || this.modo === 'COMERCIAL') return [];
    return ranking(r.periodo.filter((l) => l.contexto === 'TECNICO'), r.backlog.filter((l) => l.contexto === 'TECNICO'), r.hoje,
      (l) => l.tipoObra, (l) => (l.tipoObra ? TIPOS_OBRA[l.tipoObra] ?? l.tipoObra : null));
  });

  protected readonly obras = computed(() => {
    const r = this.r();
    if (!r || this.modo === 'COMERCIAL') return [];
    return ranking(r.periodo.filter((l) => l.contexto === 'TECNICO'), r.backlog.filter((l) => l.contexto === 'TECNICO'), r.hoje,
      (l) => l.idObra, (l) => (l.numeroObra ? `Obra ${l.numeroObra}` : 'Obra'),
      (l) => (l.tipoObra ? TIPOS_OBRA[l.tipoObra] ?? l.tipoObra : null))
      .filter((o) => o.chave !== SEM_CHAVE)
      .sort((a, b) => b.abertas - a.abertas || b.total - a.total)
      .slice(0, 8);
  });

  /** Geral: os dois processos lado a lado. */
  protected readonly processos = computed(() => {
    const r = this.r();
    if (!r || this.modo !== 'GERAL') return [];
    return (['COMERCIAL', 'TECNICO'] as const).map((contexto) => {
      const periodo = r.periodo.filter((l) => l.contexto === contexto);
      const abertas = r.abertas.filter((l) => l.contexto === contexto);
      const ind = indicadores(periodo, this.dias());
      return {
        contexto,
        rotulo: ROTULO_PROCESSO[contexto],
        icone: MODOS[contexto].icone,
        rota: MODOS[contexto].rota,
        ind,
        abertas: abertas.length,
        atrasadas: abertas.filter((l) => atrasada(l, r.hoje)).length,
        fatia: fatia(periodo.length, r.periodo.length)
      };
    });
  });

  protected readonly visao = computed(() => {
    const r = this.r();
    return r ? visaoGerencial(r, this.modo) : null;
  });

  protected readonly vistaAnalise = computed<VistaAnalise | null>(() => {
    const alvo = this.analise.alvo();
    const d = this.data();
    const r = this.r();
    if (!alvo || !d || !r) return null;
    return vistaPainel(alvo, { r, modo: this.modo, inicio: d.inicio, fim: d.fim, recorteTexto: this.recorteTexto() });
  });

  private readonly analiseNoCopiloto = signal<VistaAnalise | null>(null);

  protected readonly chips = computed<CesFilterChip[]>(() => {
    const f = this.aplicado();
    const chips: CesFilterChip[] = [];
    if (f.dataInicio && f.dataFim) {
      chips.push({ key: 'periodo', label: `Período: ${dataCurta(f.dataInicio)} a ${dataCurta(f.dataFim)}`, removable: false });
    }
    if (f.idBaseOperacional) chips.push({ key: 'base', label: `Base: ${this.recorte.nomeDaBase(f.idBaseOperacional) ?? 'selecionada'}` });
    if (f.idProcesso) chips.push({ key: 'processo', label: `Processo: ${this.recorte.nomeDoProcesso(f.idProcesso) ?? 'selecionado'}` });
    if (f.idEquipe) chips.push({ key: 'equipe', label: `Equipe: ${this.nomeDaEquipe(f.idEquipe)}` });
    if (f.idAlmoxarifado) chips.push({ key: 'almoxarifado', label: `Almoxarifado: ${this.nomeDoAlmoxarifado(f.idAlmoxarifado)}` });
    return chips;
  });

  protected readonly recorteTexto = computed(() => {
    const f = this.aplicado();
    const partes = [
      f.dataInicio && f.dataFim ? `${dataCurta(f.dataInicio)} a ${dataCurta(f.dataFim)}` : null,
      f.idBaseOperacional ? this.recorte.nomeDaBase(f.idBaseOperacional) : null,
      f.idProcesso ? this.recorte.nomeDoProcesso(f.idProcesso) : null,
      f.idEquipe ? this.nomeDaEquipe(f.idEquipe) : null,
      f.idAlmoxarifado ? this.nomeDoAlmoxarifado(f.idAlmoxarifado) : null
    ];
    return partes.filter(Boolean).join(' · ') || 'todo o histórico recente';
  });

  // ------------------------------------------------------------------ Copiloto

  protected readonly contextoCopiloto = computed<CopilotoDashboardContext | null>(() => {
    const d = this.data();
    const r = this.r();
    const f = this.aplicado();
    const ind = this.ind();
    const fluxo = this.fluxo();
    const equipes = this.equipes();
    const materiaisTop = this.listaMateriais();
    return {
      id: this.config.copilotoId,
      nome: this.config.titulo,
      modulo: 'Almoxarifado',
      descricao: `${this.config.descricao} Indicadores do período (criadas no período), fila em aberto (incluindo backlog anterior), atrasos pela data prevista, ciclo até a entrega, atendimento das quantidades, rankings de equipes, almoxarifados e materiais.`,
      rota: this.config.rota,
      periodo: periodoPainel(f.dataInicio ? new Date(f.dataInicio) : null, f.dataFim ? new Date(f.dataFim) : null),
      filtros: [
        filtroPainel('contexto', 'Processo da solicitação', this.modo === 'GERAL' ? null : ROTULO_PROCESSO[this.modo], this.modo === 'GERAL' ? null : this.modo),
        filtroPainel('baseOperacional', 'Base operacional', this.recorte.nomeDaBase(f.idBaseOperacional), f.idBaseOperacional ?? null),
        filtroPainel('processo', 'Processo de negócio', this.recorte.nomeDoProcesso(f.idProcesso), f.idProcesso ?? null),
        filtroPainel('equipe', 'Equipe', f.idEquipe ? this.nomeDaEquipe(f.idEquipe) : null, f.idEquipe ?? null),
        filtroPainel('almoxarifado', 'Almoxarifado', f.idAlmoxarifado ? this.nomeDoAlmoxarifado(f.idAlmoxarifado) : null, f.idAlmoxarifado ?? null)
      ],
      metricas: d && r
        ? [
            metricaPainel('total', 'Solicitações criadas no período', ind.total, 'inteiro', r.anterior
              ? { comparacao: { rotulo: 'período anterior', valorAnterior: r.anterior.total, melhorQuando: 'menor' } }
              : undefined),
            metricaPainel('taxaConclusao', 'Concluídas (sem recusadas)', ind.taxaConclusao, 'percentual'),
            metricaPainel('abertas', 'Em aberto agora (com backlog)', r.abertas.length, 'inteiro'),
            metricaPainel('atrasadas', 'Atrasadas na data prevista', this.atrasadas().length, 'inteiro'),
            metricaPainel('backlog', 'Abertas criadas antes do período', r.backlog.length, 'inteiro'),
            metricaPainel('cicloMediana', 'Ciclo mediano até a entrega', ind.cicloMediana, 'horas'),
            metricaPainel('cicloMedia', 'Ciclo médio até a entrega', ind.cicloMedia, 'horas', r.anterior?.cicloMedioHoras != null
              ? { comparacao: { rotulo: 'período anterior', valorAnterior: r.anterior.cicloMedioHoras, melhorQuando: 'menor' } }
              : undefined),
            metricaPainel('aprovacaoMediana', 'Aprovação mediana', ind.aprovacaoMediana, 'horas'),
            metricaPainel('separacaoMediana', 'Separação mediana (técnico)', ind.separacaoMediana, 'horas'),
            metricaPainel('atendimento', 'Atendimento das quantidades', ind.atendimento, 'percentual'),
            metricaPainel('taxaRecusa', 'Recusas (rejeitadas + canceladas)', ind.taxaRecusa, 'percentual')
          ]
        : [],
      graficos: d && r
        ? [
            graficoPainel('fluxo', 'barra_horizontal', 'Abertas por etapa', fluxo.map((e) => e.rotulo), [
              { nome: 'Abertas', valores: fluxo.map((e) => e.total) },
              { nome: 'Atrasadas', valores: fluxo.map((e) => e.atrasadas) }
            ], { metrica: 'solicitações', dimensao: 'etapa', formato: 'inteiro' }),
            graficoPainel('idade', 'barra', 'Idade das abertas', this.idade().map((f) => f.rotulo), this.idade().map((f) => f.total),
              { metrica: 'solicitações abertas', dimensao: 'faixa de idade', formato: 'inteiro' }),
            graficoPainel('equipes', 'tabela', 'Ranking de equipes', equipes.map((e) => e.rotulo), [
              { nome: 'Solicitações', valores: equipes.map((e) => e.total) },
              { nome: 'Abertas', valores: equipes.map((e) => e.abertas) },
              { nome: 'Atrasadas', valores: equipes.map((e) => e.atrasadas) },
              { nome: 'Ciclo mediano (h)', valores: equipes.map((e) => e.cicloMediana) }
            ], { metrica: 'solicitações', dimensao: 'equipe' }),
            graficoPainel('materiais', 'tabela', 'Materiais mais solicitados', materiaisTop.map((m) => `${m.codigo} ${m.descricao}`), [
              { nome: 'Solicitações', valores: materiaisTop.map((m) => m.solicitacoes) },
              { nome: 'Qtd. solicitada', valores: materiaisTop.map((m) => m.quantidadeSolicitada) },
              { nome: '% atendido', valores: materiaisTop.map((m) => m.atendimento) }
            ], { metrica: 'demanda', dimensao: 'material' }),
            ...graficosDaAnalise(this.analiseNoCopiloto())
          ]
        : [],
      datasets: ['solicitacoes', 'itens_solicitacao', 'operacoes_solicitacao', 'historico_solicitacoes', 'materiais', 'estoques', 'reservas'],
      sugestoes: [
        { rotulo: 'Atrasos e gargalos', icone: 'pi pi-exclamation-triangle', requer: ['dados'],
          pergunta: 'Quais solicitações estão atrasadas neste recorte, em que etapa estão paradas e quem precisa agir em cada uma?' },
        { rotulo: 'Comparar equipes', icone: 'pi pi-users', requer: ['dados', 'analise'],
          pergunta: 'Compare as equipes deste recorte em volume, atrasos e ciclo de atendimento. Quais estão fora do padrão e por quê?' },
        { rotulo: 'Demanda de materiais', icone: 'pi pi-box', requer: ['dados'],
          pergunta: 'Quais materiais concentram a demanda e o menor atendimento neste recorte, e há saldo em estoque para atendê-los?' }
      ]
    };
  });

  constructor() {
    // Link de análise que não existe neste recorte: fecha o painel.
    effect(() => {
      if (this.analise.alvo() && this.data() && !this.loading() && !this.vistaAnalise()) this.analise.fechar();
    });
  }

  ngOnInit(): void {
    this.recorte.carregar(this.destroyRef);
    this.recorte.carregarEquipes(this.destroyRef);
    this.carregarAlmoxarifados();
    const lidos = this.filtrosUrl.read();
    const [inicioPadrao, fimPadrao] = intervaloRapido('mes');
    this.inicio.set(lidos.inicio ?? inicioPadrao);
    this.fim.set(lidos.fim ?? fimPadrao);
    this.idBase.set(lidos.idBaseOperacional ?? null);
    this.idProcesso.set(lidos.idProcesso ?? null);
    this.idEquipe.set(lidos.idEquipe ?? null);
    this.idAlmoxarifado.set(lidos.idAlmoxarifado ?? null);
    this.aplicar();
  }

  protected aplicar(): void {
    const filtros = this.filtrosAtuais();
    this.filtrosUrl.write({
      inicio: this.inicio() ?? undefined,
      fim: this.fim() ?? undefined,
      idBaseOperacional: filtros.idBaseOperacional,
      idProcesso: filtros.idProcesso,
      idEquipe: filtros.idEquipe,
      idAlmoxarifado: filtros.idAlmoxarifado
    });
    this.buscar(filtros);
  }

  /** Busca um recorte; a atualização automática repete o aplicado sem mostrar carregamento nem avisar erro. */
  private buscar(filtros: PainelSolicitacoesFiltros, silencioso = false): void {
    if (!silencioso) {
      this.loading.set(true);
      this.erro.set(null);
    }
    this.almoxarifado
      .obterPainelSolicitacoes(filtros)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.data.set(res);
          this.aplicado.set(filtros);
          this.loading.set(false);
        },
        error: (err) => {
          if (silencioso) return;
          const mensagem = getErrorMessage(err);
          this.loading.set(false);
          this.erro.set(mensagem);
          this.notificacoes.show({ variant: 'error', title: 'Não foi possível carregar o painel', message: mensagem });
        }
      });
  }

  private filtrosAtuais(): PainelSolicitacoesFiltros {
    const inicio = this.inicio();
    const fim = this.fim();
    return {
      dataInicio: inicio ? inicioDoDia(inicio) : undefined,
      dataFim: fim ? fimDoDia(fim) : undefined,
      contexto: this.modo === 'GERAL' ? undefined : this.modo,
      idBaseOperacional: this.idBase() ?? undefined,
      idProcesso: this.idProcesso() ?? undefined,
      idEquipe: this.idEquipe() ?? undefined,
      idAlmoxarifado: this.idAlmoxarifado() ?? undefined
    };
  }

  protected usarPeriodo(periodo: PeriodoRapido): void {
    const [de, ate] = intervaloRapido(periodo);
    this.inicio.set(de);
    this.fim.set(ate);
    this.aplicar();
  }

  protected removerFiltro(chave: string): void {
    if (chave === 'base') this.idBase.set(null);
    if (chave === 'processo') this.idProcesso.set(null);
    if (chave === 'equipe') this.idEquipe.set(null);
    if (chave === 'almoxarifado') this.idAlmoxarifado.set(null);
    this.aplicar();
  }

  protected limpar(): void {
    const [de, ate] = intervaloRapido('mes');
    this.inicio.set(de);
    this.fim.set(ate);
    this.idBase.set(null);
    this.idProcesso.set(null);
    this.idEquipe.set(null);
    this.idAlmoxarifado.set(null);
    this.aplicar();
  }

  // ------------------------------------------------------------------ exportação (padrão de inspeções e desvios)

  protected readonly exportacaoAberta = signal(false);
  protected readonly expConteudo = signal<ConteudoExportacaoPainel>('SOLICITACOES');
  protected readonly expNivel = signal<NivelExportacaoPainel>('RESUMO');
  protected readonly expArquivo = signal<'xlsx' | 'csv'>('xlsx');
  protected readonly expSituacao = signal<SituacaoExportacaoPainel | null>(null);
  protected readonly expProcesso = signal<ProcessoSolicitacao | null>(null);
  protected readonly expBacklog = signal(true);
  protected readonly expInicio = signal<Date | null>(null);
  protected readonly expFim = signal<Date | null>(null);
  protected readonly expBase = signal<string | null>(null);
  protected readonly expProcessoNegocio = signal<string | null>(null);
  protected readonly expEquipe = signal<string | null>(null);
  protected readonly expAlmoxarifado = signal<string | null>(null);

  protected readonly opcoesConteudo: CesSelectOption<ConteudoExportacaoPainel>[] = [
    { label: 'Solicitações', value: 'SOLICITACOES' },
    { label: 'Materiais', value: 'MATERIAIS' },
    { label: 'Equipes (consolidado)', value: 'EQUIPES' },
    { label: 'Almoxarifados (consolidado)', value: 'ALMOXARIFADOS' }
  ];
  protected readonly opcoesArquivo: CesSelectOption<'xlsx' | 'csv'>[] = [
    { label: 'Excel (.xlsx)', value: 'xlsx' },
    { label: 'CSV (.csv)', value: 'csv' }
  ];
  protected readonly opcoesSituacao: CesSelectOption<SituacaoExportacaoPainel>[] = [
    { label: 'Em aberto', value: 'ABERTA' },
    { label: 'Atrasadas', value: 'ATRASADA' },
    { label: 'Concluídas', value: 'CONCLUIDA' },
    { label: 'Recusadas', value: 'RECUSADA' }
  ];
  protected readonly opcoesProcessoSolicitacao: CesSelectOption<ProcessoSolicitacao>[] = [
    { label: 'Comercial', value: 'COMERCIAL' },
    { label: 'Técnico', value: 'TECNICO' }
  ];
  protected readonly temNivel = computed(() => this.expConteudo() === 'SOLICITACOES' || this.expConteudo() === 'MATERIAIS');
  protected readonly opcoesNivel = computed<CesSelectOption<NivelExportacaoPainel>[]>(() =>
    this.expConteudo() === 'MATERIAIS'
      ? [
          { label: 'Resumido — um material por linha', value: 'RESUMO' },
          { label: 'Detalhado — por processo e sentido', value: 'DETALHADO' }
        ]
      : [
          { label: 'Resumido — uma solicitação por linha', value: 'RESUMO' },
          { label: 'Detalhado — um item (material) por linha', value: 'DETALHADO' }
        ]
  );

  /** Abre a exportação já com o recorte em tela; dá para ajustar antes de baixar. */
  protected abrirExportacao(): void {
    this.expInicio.set(this.inicio());
    this.expFim.set(this.fim());
    this.expProcesso.set(this.modo === 'GERAL' ? null : this.modo);
    this.expBase.set(this.idBase());
    this.expProcessoNegocio.set(this.idProcesso());
    this.expEquipe.set(this.idEquipe());
    this.expAlmoxarifado.set(this.idAlmoxarifado());
    this.exportacaoAberta.set(true);
  }

  protected executarExportacao(): void {
    if (this.exportando()) return;
    const inicio = this.expInicio();
    const fim = this.expFim();
    const filtros: PainelSolicitacoesFiltros = {
      dataInicio: inicio ? inicioDoDia(inicio) : undefined,
      dataFim: fim ? fimDoDia(fim) : undefined,
      contexto: this.expProcesso() ?? undefined,
      idBaseOperacional: this.expBase() ?? undefined,
      idProcesso: this.expProcessoNegocio() ?? undefined,
      idEquipe: this.expEquipe() ?? undefined,
      idAlmoxarifado: this.expAlmoxarifado() ?? undefined
    };
    const opcoes: ExportacaoPainelSolicitacoes = {
      conteudo: this.expConteudo(),
      nivel: this.temNivel() ? this.expNivel() : 'RESUMO',
      situacao: this.expSituacao() ?? undefined,
      incluirBacklog: this.expBacklog(),
      formato: this.expArquivo()
    };
    this.exportando.set(true);
    this.almoxarifado
      .exportarPainelSolicitacoes(filtros, opcoes)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = `solicitacoes_${opcoes.conteudo.toLowerCase()}_${opcoes.nivel.toLowerCase()}_${isoLocal(new Date())}.${opcoes.formato}`;
          link.click();
          URL.revokeObjectURL(url);
          this.exportando.set(false);
          this.exportacaoAberta.set(false);
          this.notificacoes.success('O arquivo foi baixado.', { title: 'Exportação concluída' });
        },
        error: (err) => {
          this.exportando.set(false);
          this.notificacoes.error(getErrorMessage(err), { title: 'Falha na exportação' });
        }
      });
  }

  /** PDF executivo: o Copiloto gera o relatório com os dados reais do recorte. */
  protected relatorioComCopiloto(): void {
    this.exportacaoAberta.set(false);
    this.copiloto.perguntarSobre(
      { tipo: 'secao', id: 'relatorio', rotulo: 'Relatório do painel' },
      `Gere um relatório PDF executivo deste painel de ${this.config.titulo.toLowerCase()} com os indicadores, a fila em aberto e os atrasos por etapa, equipes e almoxarifados fora do padrão, materiais com menor atendimento e recomendações, usando os dados reais deste recorte.`
    );
  }

  // ------------------------------------------------------------------ análise detalhada

  protected analisar(dimension: string, value: string): void {
    this.analise.abrir(dimension, value);
  }

  protected aprofundar(alvo: AlvoAnalise): void {
    this.analise.aprofundar(alvo, this.vistaAnalise()?.titulo ?? '');
  }

  protected selecionarDia(selecao: CesChartPointSelection): void {
    const dia = this.serie()?.dias[selecao.index];
    if (dia) this.analisar('dia', dia);
  }

  protected selecionarCiclo(): void {
    this.analisar('kpi', 'ciclo');
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

  // ------------------------------------------------------------------ apresentação

  protected detalheVolume(): string {
    const r = this.r();
    const ind = this.ind();
    const porDia = `${ind.porDia.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} por dia`;
    const v = variacaoSobre(ind.total, r?.anterior?.total ?? null);
    return v === null ? porDia : `${porDia} · ${variacao(v)} vs. período anterior`;
  }

  protected detalheConclusao(): string {
    const ind = this.ind();
    return `${ind.concluidas} de ${ind.total - ind.recusadas} concluídas · ${plural(ind.recusadas, 'recusada', 'recusadas')}`;
  }

  protected detalheAbertas(): string {
    const r = this.r();
    if (!r) return '';
    const partes = [this.atrasadas().length ? plural(this.atrasadas().length, 'atrasada', 'atrasadas') : 'nenhuma atrasada'];
    if (r.backlog.length) partes.push(`${r.backlog.length} de antes do período`);
    return partes.join(' · ');
  }

  protected detalheCiclo(): string {
    const r = this.r();
    const ind = this.ind();
    if (ind.cicloMediana === null) return 'Nenhuma concluída no período';
    const v = variacaoSobre(ind.cicloMedia, r?.anterior?.cicloMedioHoras ?? null);
    const p90 = `90% em até ${tempo(ind.cicloP90)}`;
    return v === null ? p90 : `${p90} · média ${variacao(v)} vs. anterior`;
  }

  protected tomConclusao(): MetricTone {
    const t = this.ind().taxaConclusao;
    return t === null ? 'slate' : t >= 80 ? 'green' : t >= 50 ? 'amber' : 'red';
  }

  protected tomCiclo(): MetricTone {
    const r = this.r();
    const v = variacaoSobre(this.ind().cicloMedia, r?.anterior?.cicloMedioHoras ?? null);
    return v === null ? 'slate' : v <= 0 ? 'green' : 'amber';
  }

  protected tomAtendimento(): MetricTone {
    const a = this.ind().atendimento;
    return a === null ? 'slate' : a >= 95 ? 'green' : a >= 80 ? 'amber' : 'red';
  }

  protected idadeTexto(l: SolicitacaoDoPainel): string {
    const r = this.r();
    return r ? tempo(idadeDias(l, r.agora) * 24) : '—';
  }

  protected atrasoTexto(l: SolicitacaoDoPainel): string | null {
    const r = this.r();
    if (!r || !atrasada(l, r.hoje)) return null;
    return `Atrasada há ${plural(diasDeAtraso(l, r.hoje), 'dia', 'dias')}`;
  }

  protected tomEtapa(l: SolicitacaoDoPainel): 'warning' | 'danger' | 'info' {
    const r = this.r();
    if (r && atrasada(l, r.hoje)) return 'danger';
    return l.possuiPendencia ? 'warning' : 'info';
  }

  protected largura(parte: number, total: number): number {
    return total > 0 ? Math.max(parte > 0 ? 3 : 0, (parte / total) * 100) : 0;
  }

  private nomeDaEquipe(id: string): string {
    return this.recorte.opcoesEquipes().find((o) => o.value === id)?.label
      ?? this.data()?.solicitacoes.find((l) => l.idEquipe === id)?.equipe
      ?? 'selecionada';
  }

  private nomeDoAlmoxarifado(id: string): string {
    return this.almoxarifados().find((o) => o.value === id)?.label ?? 'selecionado';
  }

  private carregarAlmoxarifados(): void {
    this.almoxarifado
      .listarEstoquesAtivos()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        // Estoques sem equipe são os almoxarifados/depósitos que atendem as solicitações.
        next: (res) => this.almoxarifados.set(res.content.filter((e) => !e.idEquipe).map((e) => ({ label: e.nomeIdentificador, value: e.id }))),
        error: () => this.almoxarifados.set([])
      });
  }
}

// ------------------------------------------------------------------ utilitários locais

function ordenarRanking(linhas: LinhaRanking[], ordem: OrdemRanking): LinhaRanking[] {
  const copia = [...linhas];
  switch (ordem) {
    case 'atrasos':
      return copia.sort((a, b) => b.atrasadas - a.atrasadas || b.abertas - a.abertas);
    case 'ciclo':
      return copia.sort((a, b) => (b.cicloMediana ?? -1) - (a.cicloMediana ?? -1));
    default:
      return copia;
  }
}

function intervaloRapido(periodo: PeriodoRapido): [Date, Date] {
  const hoje = new Date();
  hoje.setHours(0, 0, 0, 0);
  const dias = (n: number) => new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate() - n);
  switch (periodo) {
    case '7-dias':
      return [dias(6), hoje];
    case '30-dias':
      return [dias(29), hoje];
    case '90-dias':
      return [dias(89), hoje];
    case 'mes-anterior':
      return [new Date(hoje.getFullYear(), hoje.getMonth() - 1, 1), new Date(hoje.getFullYear(), hoje.getMonth(), 0)];
    default:
      return [new Date(hoje.getFullYear(), hoje.getMonth(), 1), hoje];
  }
}

function inicioDoDia(d: Date): string {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 0, 0, 0).toISOString();
}

function fimDoDia(d: Date): string {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 23, 59, 59, 999).toISOString();
}

function dataCurta(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR');
}
