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
import { AccordionModule } from 'primeng/accordion';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { CesAsyncStateComponent, CesStatusComponent } from '@ces/ui';

import { NavigationHistoryService } from '../../../core/navigation/navigation-history.service';
import { AuthService } from '../../../core/services/auth.service';
import { ExecucaoService } from '../../../core/services/execucao.service';
import { DashboardService } from '../../../core/services/dashboard.service';
import { ExecucaoComercialResponse, ORIGENS_MATERIAL, TIPOS_NOTA, rotuloTipoServico } from '../../../core/models/execucao.models';
import { ContextoExecucaoResponse } from '../../../core/models/dashboard.models';
import { EquipeResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { duracao, horaCurta, moeda, percentual, tomDaMeta } from '../dashboards/execucao-dashboards.utils';
import { STATUS_EXECUCAO } from '../execucao-list/execucao-list.utils';
import { NumeroOficialDialogComponent } from '../execucao-list/numero-oficial-dialog.component';
import { AlterarNotaDrawerComponent } from './alterar-nota-drawer.component';
import { HistoricoNotaComponent } from './historico-nota.component';
import {
  distanciaTexto,
  insightsDaExecucao,
  ordinal,
  posicaoNoTurno,
  variacaoPercentual
} from '../relatorios/relatorios.utils';
import { desenharMapaDeLancamentos } from '../relatorios/mapa-lancamentos';
import { imprimirSempreNoTemaClaro } from '../relatorios/relatorio-impressao';

/** Relatório de uma nota: o serviço feito e o contexto da equipe e do turno em que foi lançada. */
@Component({
  selector: 'app-execucao-relatorio',
  standalone: true,
  imports: [
    CommonModule,
    AccordionModule,
    ButtonModule,
    TooltipModule,
    CesAsyncStateComponent,
    CesStatusComponent,
    NumeroOficialDialogComponent,
    AlterarNotaDrawerComponent,
    HistoricoNotaComponent
  ],
  templateUrl: './execucao-relatorio.component.html',
  styleUrl: '../relatorios/relatorio.css'
})
export class ExecucaoRelatorioComponent implements OnInit, AfterViewChecked {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);
  private readonly execucaoService = inject(ExecucaoService);
  private readonly dashboardService = inject(DashboardService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly duracao = duracao;
  protected readonly horaCurta = horaCurta;
  protected readonly moeda = moeda;
  protected readonly percentual = percentual;
  protected readonly tomDaMeta = tomDaMeta;
  protected readonly distanciaTexto = distanciaTexto;
  protected readonly ordinal = ordinal;
  protected readonly statusExecucao = STATUS_EXECUCAO;
  protected readonly tiposNota = TIPOS_NOTA;
  protected readonly origens = ORIGENS_MATERIAL;
  protected readonly numeroOficialAberto = signal(false);
  protected readonly alteracaoAberta = signal(false);
  /** Recarrega a linha do tempo depois de cada alteração. */
  protected readonly versaoHistorico = signal(0);
  /** Acordeão da linha do tempo, como no relatório da solicitação: só busca quando abre. */
  protected readonly historicoAberto = signal<string | number | string[] | number[] | null | undefined>(undefined);
  protected readonly historicoVisivel = computed(() => {
    const v = this.historicoAberto();
    return v === 'historico' || (Array.isArray(v) && (v as unknown[]).includes('historico'));
  });
  private readonly auth = inject(AuthService);
  protected readonly podeAlterar = computed(() => this.auth.currentUser()?.permissoes?.includes('execucao:comercial:write') ?? false);

  /** "NOTA", "NOTA AVULSA" ou "OCORRÊNCIA", conforme o tipo de lançamento. */
  protected readonly rotuloReferencia = computed(() => {
    const tipo = this.execucao()?.tipoNota;
    return tipo === 'AVULSA' ? 'Nota avulsa' : tipo === 'OCORRENCIA' ? 'Ocorrência' : 'Nota';
  });

  /** Na ocorrência os materiais mostram de onde vieram (Concessionária ou equipe). */
  protected readonly mostrarOrigem = computed(() => this.execucao()?.tipoNota === 'OCORRENCIA');

  protected readonly execucao = signal<ExecucaoComercialResponse | null>(null);
  protected readonly contexto = signal<ContextoExecucaoResponse | null>(null);
  protected readonly carregandoContexto = signal(false);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly emitidoEm = new Date();

  private readonly mapaRef = viewChild<ElementRef<HTMLElement>>('mapa');
  private mapa: any = null;
  private mapaDesenhado: string | null = null;
  private impressaoDisparada = false;
  protected readonly isPrintMode = signal(false);

  /** A equipe pode ter sido removida depois do lançamento. */
  protected readonly equipe = computed<EquipeResponse | null>(() => this.execucao()?.equipe ?? null);

  protected readonly tipoServico = computed(() => rotuloTipoServico(this.execucao()?.tipoServico));

  protected readonly status = computed(() => {
    const e = this.execucao();
    return e ? (STATUS_EXECUCAO[e.statusExecucao] ?? null) : null;
  });

  protected readonly insights = computed(() => {
    const e = this.execucao();
    return e ? insightsDaExecucao(e, this.contexto()) : [];
  });

  protected readonly posicao = computed(() => {
    const e = this.execucao();
    const c = this.contexto();
    return e && c ? posicaoNoTurno(c.lancamentosDoTurno, e.id) : null;
  });

  /** Participação desta nota na produção da equipe no dia. */
  protected readonly participacaoNoDia = computed(() => {
    const e = this.execucao();
    const dia = this.contexto()?.diaDaEquipe;
    return e && dia && dia.producao > 0 ? (e.valorTotalFaturado / dia.producao) * 100 : null;
  });

  protected readonly variacaoMedia = computed(() => {
    const e = this.execucao();
    return e ? variacaoPercentual(e.valorTotalFaturado, this.contexto()?.referenciaEquipe?.valorMedioNota) : null;
  });

  /** Ações com o valor de referência no contrato, quando houver. */
  protected readonly acoes = computed(() => {
    const e = this.execucao();
    const valores = new Map((this.contexto()?.valoresPorAcao ?? []).map((v) => [v.idAcao, v.valor]));
    return (e?.acoes ?? []).map((item) => ({
      id: item.id,
      codigo: item.acao?.codigo ?? '—',
      nome: item.acao?.nome ?? 'Ação removida',
      valor: item.acao ? (valores.get(item.acao.id) ?? null) : null
    }));
  });

  protected readonly somaReferencia = computed(() => {
    const valores = this.acoes().map((a) => a.valor);
    return valores.length && valores.every((v) => v !== null) ? valores.reduce((s, v) => s + (v ?? 0), 0) : null;
  });

  protected readonly materiaisAplicados = computed(
    () => (this.execucao()?.materiais ?? []).filter((m) => m.operacao === 'APLICADO').length
  );
  protected readonly materiaisRetirados = computed(
    () => (this.execucao()?.materiais ?? []).filter((m) => m.operacao === 'RETIRADO').length
  );

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
    });
    this.destroyRef.onDestroy(() => this.mapa?.remove());
  }

  /**
   * O mapa espera o contexto (as outras notas do turno entram como referência) e é
   * redesenhado uma vez por nota.
   */
  ngAfterViewChecked(): void {
    const e = this.execucao();
    const elemento = this.mapaRef()?.nativeElement;
    if (!e || !elemento || this.carregandoContexto() || this.mapaDesenhado === e.id) return;
    this.mapaDesenhado = e.id;
    this.mapa?.remove();

    const doTurno = this.contexto()?.lancamentosDoTurno ?? [];
    const pontos = doTurno.length
      ? doTurno
          .map((l, i) => ({ l, ordem: i + 1 }))
          .filter(({ l }) => l.latitude != null && l.longitude != null)
          .map(({ l, ordem }) => ({
            latitude: l.latitude!,
            longitude: l.longitude!,
            rotulo: String(ordem),
            destaque: l.idExecucao === e.id,
            descricao: `${ordem}. Nota ${l.referenciaNota} · ${horaCurta(l.dataLancamento)} · ${moeda(l.valor)}`
          }))
      : e.latitude != null && e.longitude != null
        ? [{ latitude: e.latitude, longitude: e.longitude, rotulo: '1', destaque: true, descricao: `Nota ${e.referenciaNota}` }]
        : [];
    this.mapa = desenharMapaDeLancamentos(elemento, pontos);

    if (this.isPrintMode() && !this.impressaoDisparada) {
      this.impressaoDisparada = true;
      setTimeout(() => window.print(), 1200);
    }
  }

  protected loadData(id = this.route.snapshot.paramMap.get('id')): void {
    if (!id) return;
    this.loading.set(true);
    this.error.set(null);
    this.mapaDesenhado = null;
    this.contexto.set(null);

    this.execucaoService.buscarPorId(id).subscribe({
      next: (res) => {
        this.execucao.set(res);
        this.loading.set(false);
        this.carregarContexto(id);
      },
      error: (err) => {
        this.error.set(getErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  /** O contexto enriquece o relatório, mas a nota é exibida mesmo se ele falhar. */
  private carregarContexto(id: string): void {
    this.carregandoContexto.set(true);
    this.dashboardService
      .contextualizarExecucao(id)
      .pipe(catchError(() => of(null)))
      .subscribe((contexto) => {
        this.contexto.set(contexto);
        this.carregandoContexto.set(false);
      });
  }

  protected abrirNota(id: string): void {
    void this.router.navigate(['/execucao/comercial', id]);
  }

  protected abrirTurno(): void {
    const id = this.execucao()?.idTurno;
    if (!id) return;
    const url = this.router.serializeUrl(this.router.createUrlTree(['/execucao/turnos', id, 'detalhamento']));
    window.open(url, '_blank');
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

  protected iniciais(nome: string | undefined): string {
    const partes = (nome ?? '').trim().split(/\s+/).filter(Boolean);
    return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase() || '?';
  }

  protected variacao(valor: number | null): string {
    if (valor === null) return '';
    const texto = Math.abs(valor).toLocaleString('pt-BR', { maximumFractionDigits: 0 });
    return `${valor >= 0 ? '+' : '−'}${texto}%`;
  }

  protected numeroInformado(atualizada: ExecucaoComercialResponse): void {
    this.execucao.set(atualizada);
    this.versaoHistorico.update((v) => v + 1);
  }

  /** Nota alterada: atualiza o relatório, o contexto (valor no turno) e a linha do tempo. */
  protected notaAlterada(atualizada: ExecucaoComercialResponse): void {
    this.execucao.set(atualizada);
    // Abre a linha do tempo para quem alterou ver a mudança registrada.
    this.historicoAberto.set('historico');
    this.versaoHistorico.update((v) => v + 1);
    this.mapaDesenhado = null;
    this.carregarContexto(atualizada.id);
  }

  protected imprimir(): void {
    window.print();
  }

  protected voltar(): void {
    this.navegacao.back();
  }
}
