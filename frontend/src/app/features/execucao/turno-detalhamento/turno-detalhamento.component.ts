import { CommonModule } from '@angular/common';
import {
  AfterViewChecked,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { CesAsyncStateComponent, CesChartComponent, CesStatusComponent } from '@ces/ui';

import { NavigationHistoryService } from '../../../core/navigation/navigation-history.service';
import { DashboardService } from '../../../core/services/dashboard.service';
import { RhService } from '../../../core/services/rh.service';
import { DetalhamentoTurnoResponse } from '../../../core/models/dashboard.models';
import { EquipeResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { duracao, horaCurta, moeda, percentual, tomDaMeta } from '../dashboards/execucao-dashboards.utils';
import { situacaoDoTurno } from '../turno-list/turno-list.utils';
import { STATUS_EXECUCAO } from '../execucao-list/execucao-list.utils';
import {
  acoesMaisExecutadas,
  insightsDoTurno,
  materiaisMovimentados,
  notasComIntervalo,
  serieDoTurno,
  tiposServicoDasNotas,
  variacaoPercentual
} from '../relatorios/relatorios.utils';
import { desenharMapaDeLancamentos } from '../relatorios/mapa-lancamentos';
import { imprimirSempreNoTemaClaro } from '../relatorios/relatorio-impressao';

/** Relatório de um turno: o que a equipe fez, em que ritmo e como foi frente à meta e ao seu histórico. */
@Component({
  selector: 'app-turno-detalhamento',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    CesAsyncStateComponent,
    CesChartComponent,
    CesStatusComponent
  ],
  templateUrl: './turno-detalhamento.component.html',
  styleUrl: '../relatorios/relatorio.css'
})
export class TurnoDetalhamentoComponent implements OnInit, AfterViewChecked {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);
  private readonly dashboardService = inject(DashboardService);
  private readonly rhService = inject(RhService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly duracao = duracao;
  protected readonly horaCurta = horaCurta;
  protected readonly moeda = moeda;
  protected readonly percentual = percentual;
  protected readonly tomDaMeta = tomDaMeta;
  protected readonly statusExecucao = STATUS_EXECUCAO;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<DetalhamentoTurnoResponse | null>(null);
  protected readonly equipe = signal<EquipeResponse | null>(null);
  protected readonly emitidoEm = new Date();

  private readonly mapaRef = viewChild<ElementRef<HTMLElement>>('mapa');
  private mapa: any = null;
  private mapaDoTurno: string | null = null;
  private impressaoDisparada = false;
  protected readonly isPrintMode = signal(false);

  protected readonly situacao = computed(() => {
    const d = this.data();
    return d
      ? situacaoDoTurno({
          status: d.turno.status,
          abertoDesdeOutroDia: d.indicadores.abertoDesdeOutroDia,
          dataInicio: d.turno.dataInicio,
          encerradoAutomaticamente: d.turno.encerradoAutomaticamente
        })
      : null;
  });

  protected readonly insights = computed(() => {
    const d = this.data();
    if (!d) return [];
    const insights = insightsDoTurno(d.indicadores, d.historicoEquipe);
    return d.turno.encerradoAutomaticamente
      ? [
          {
            tom: 'warning' as const,
            icone: 'pi pi-clock',
            titulo: 'Turno encerrado pelo sistema',
            texto: d.turno.motivoEncerramento ?? 'A equipe não encerrou o turno e ele foi fechado no horário de corte.'
          },
          ...insights
        ]
      : insights;
  });

  protected readonly notas = computed(() => {
    const d = this.data();
    return d ? notasComIntervalo(d.execucoes, d.turno.dataInicio) : [];
  });

  protected readonly tiposServico = computed(() => tiposServicoDasNotas(this.data()?.execucoes ?? []));
  protected readonly maiorTipo = computed(() => this.tiposServico()[0]?.notas ?? 1);
  protected readonly acoes = computed(() => acoesMaisExecutadas(this.data()?.execucoes ?? []));
  protected readonly materiais = computed(() => materiaisMovimentados(this.data()?.execucoes ?? []));
  protected readonly maiorFrequencia = computed(() => this.acoes()[0]?.quantidade ?? 1);

  protected readonly serie = computed(() => {
    const d = this.data();
    return d ? serieDoTurno(d.execucoes, d.turno.dataInicio, d.indicadores.metaDiaria) : null;
  });

  /** Fim da janela do turno: fechamento ou, aberto, o momento do cálculo. */
  private readonly fimDoTurno = computed(() => {
    const d = this.data();
    return d ? (d.turno.dataFim ?? d.indicadores.calculadoEm) : null;
  });

  /** Posição (0–100%) de cada nota na linha do tempo do turno. */
  protected readonly pontosNaLinha = computed(() => {
    const d = this.data();
    const fim = this.fimDoTurno();
    if (!d || !fim) return [];
    const inicio = new Date(d.turno.dataInicio).getTime();
    const janela = Math.max(1, new Date(fim).getTime() - inicio);
    return this.notas().map((nota, i) => ({
      id: nota.id,
      ordem: i + 1,
      referencia: nota.referenciaNota,
      hora: horaCurta(nota.dataLancamento),
      valor: nota.valorTotalFaturado,
      posicao: Math.min(100, Math.max(0, ((new Date(nota.dataLancamento).getTime() - inicio) / janela) * 100))
    }));
  });

  protected readonly comLocalizacao = computed(
    () => this.notas().filter((n) => n.latitude != null && n.longitude != null).length
  );

  /** Comparações com a média da equipe, para os subtítulos dos indicadores. */
  protected readonly comparacao = computed(() => {
    const d = this.data();
    if (!d || d.historicoEquipe.turnos === 0) return null;
    return {
      producao: variacaoPercentual(d.indicadores.producao, d.historicoEquipe.mediaProducaoPorTurno),
      notas: variacaoPercentual(d.indicadores.notas, d.historicoEquipe.mediaNotasPorTurno)
    };
  });

  constructor() {
    imprimirSempreNoTemaClaro();
  }

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.isPrintMode.set(params.get('mode') === 'print');
    });
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('id');
      if (id) this.loadData(id);
      else {
        this.error.set('ID do turno não fornecido.');
        this.loading.set(false);
      }
    });
    this.destroyRef.onDestroy(() => this.mapa?.remove());
  }

  /** O mapa só existe depois que o bloco com dados é renderizado. */
  ngAfterViewChecked(): void {
    const d = this.data();
    const elemento = this.mapaRef()?.nativeElement;
    if (!d || !elemento || this.mapaDoTurno === d.turno.id) return;
    this.mapaDoTurno = d.turno.id;
    this.mapa?.remove();
    this.mapa = desenharMapaDeLancamentos(
      elemento,
      this.notas()
        .map((nota, i) => ({ nota, ordem: i + 1 }))
        .filter(({ nota }) => nota.latitude != null && nota.longitude != null)
        .map(({ nota, ordem }) => ({
          latitude: nota.latitude!,
          longitude: nota.longitude!,
          rotulo: String(ordem),
          descricao: `${ordem}. Nota ${nota.referenciaNota} · ${horaCurta(nota.dataLancamento)} · ${moeda(nota.valorTotalFaturado)}`
        }))
    );
    if (this.isPrintMode() && !this.impressaoDisparada) {
      this.impressaoDisparada = true;
      setTimeout(() => window.print(), 1200);
    }
  }

  protected loadData(id = this.route.snapshot.paramMap.get('id')): void {
    if (!id) return;
    this.loading.set(true);
    this.error.set(null);
    this.mapaDoTurno = null;

    this.dashboardService.detalharTurno(id).subscribe({
      next: (res) => {
        this.data.set(res);
        this.loading.set(false);
        // A equipe completa (tipo, supervisor, coordenador) é complementar: o relatório não espera por ela.
        this.rhService
          .buscarEquipePorId(res.turno.idEquipe)
          .pipe(catchError(() => of(null)))
          .subscribe((equipe) => this.equipe.set(equipe));
      },
      error: (err) => {
        this.error.set(getErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  protected abrirNota(id: string): void {
    const url = this.router.serializeUrl(this.router.createUrlTree(['/execucao/comercial', id]));
    window.open(url, '_blank');
  }

  protected verNotasNaConsulta(): void {
    const id = this.data()?.turno.id;
    if (id) void this.router.navigate(['/execucao/comercial'], { queryParams: { idTurno: id } });
  }

  protected imprimir(): void {
    window.print();
  }

  protected voltar(): void {
    this.navegacao.back();
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
  }

  protected nomeColaborador(colaborador: unknown): string {
    const c = colaborador as
      | { colaborador?: { nomeCompleto?: string }; nomeCompleto?: string; nomeColaborador?: string; primeiroNome?: string; ultimoNome?: string }
      | null
      | undefined;
    if (!c) return '—';
    return (
      c.colaborador?.nomeCompleto ||
      c.nomeCompleto ||
      c.nomeColaborador ||
      [c.primeiroNome, c.ultimoNome].filter(Boolean).join(' ') ||
      '—'
    );
  }

  /** "+12%" / "−8%" para comparação com a média. */
  protected variacao(valor: number | null | undefined): string {
    if (valor === null || valor === undefined) return '';
    const texto = Math.abs(valor).toLocaleString('pt-BR', { maximumFractionDigits: 0 });
    return `${valor >= 0 ? '+' : '−'}${texto}%`;
  }
}
