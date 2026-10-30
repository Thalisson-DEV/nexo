import { Component, OnInit, signal, inject, ViewChild, AfterViewInit, OnDestroy, computed } from '@angular/core';
import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';

import { CesAsyncStateComponent, CesStatusComponent, CesTone } from '@ces/ui';
import { CesCellDirective, CesDataTableColumn, CesDataTableComponent, CesRowDetailDirective } from '@ces/ui/data';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { RhService } from '../../../../core/services/rh.service';
import { ObrasService } from '../../../../core/services/obras.service';
import {
  MovimentacaoOperacaoResponse,
  MovimentacaoItemResponse,
  TipoMovimentacao,
} from '../../../../core/models/almoxarifado.models';
import { BaseOperacionalResponse, EquipeResponse } from '../../../../core/models/rh.models';
import { ObraResponse } from '../../../../core/models/obras.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { SaldoEstoqueDialogComponent } from '../../../../shared/components/saldo-estoque-dialog/saldo-estoque-dialog.component';
import { NavigationHistoryService } from '../../../../core/navigation/navigation-history.service';
import { SeriesGradeComponent, SerieGradeItem } from '../../../../shared/components/series-grade/series-grade.component';
import {
  ItemMovimentacaoAgrupado,
  agruparItensPorMaterial,
  iconeTipoMovimentacao,
  rotuloCondicao,
  rotuloTipoMovimentacao,
  tomCondicao,
  tomTipoMovimentacao,
} from '../../../../shared/utils/movimentacao.utils';

/** Material da operação com a grade de séries pronta para a linha expandida. */
type GrupoItens = ItemMovimentacaoAgrupado & { readonly grade: readonly SerieGradeItem[] };

@Component({
  selector: 'app-relatorio-movimentacao-estoque',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    TooltipModule,
    DecimalPipe,
    DatePipe,
    CesStatusComponent,
    CesAsyncStateComponent,
    SaldoEstoqueDialogComponent,
    CesDataTableComponent,
    CesCellDirective,
    CesRowDetailDirective,
    SeriesGradeComponent,
  ],
  templateUrl: './relatorio-movimentacao-estoque.component.html',
  styleUrl: './relatorio-movimentacao-estoque.component.css',
})
export class RelatorioMovimentacaoEstoqueComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('saldoDialog') saldoDialog!: SaldoEstoqueDialogComponent;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);
  private almoxarifadoService = inject(AlmoxarifadoService);
  private rhService = inject(RhService);
  private obrasService = inject(ObrasService);

  protected movimentacao = signal<MovimentacaoOperacaoResponse | null>(null);
  protected loading = signal(true);
  protected error = signal<string | null>(null);
  protected isPrintMode = signal(false);

  // Informações complementares enriquecidas
  protected equipe = signal<EquipeResponse | null>(null);
  protected obra = signal<ObraResponse | null>(null);
  protected baseOrigem = signal<BaseOperacionalResponse | null>(null);
  protected baseDestino = signal<BaseOperacionalResponse | null>(null);

  // Assinaturas para transferências
  protected readonly assinaturaEntregadorUrl = signal<string | null>(null);
  protected readonly assinaturaRecebedorUrl = signal<string | null>(null);

  // Estado de cópia do identificador
  protected idCopiado = signal(false);

  protected readonly rotuloCondicao = rotuloCondicao;
  protected readonly tomCondicao = tomCondicao;

  /** Um material por linha (o backend devolve uma linha por série). */
  protected readonly itensAgrupados = computed<GrupoItens[]>(() =>
    agruparItensPorMaterial(this.movimentacao()?.itens).map((g) => ({
      ...g,
      grade: g.series.map((numSerie) => ({ numSerie })),
    })),
  );
  protected readonly totalSeries = computed(() => this.itensAgrupados().reduce((total, g) => total + g.series.length, 0));
  protected readonly materiaisSeriados = computed(() => this.itensAgrupados().filter((g) => g.series.length).length);

  protected readonly chaveGrupo = (g: GrupoItens) => g.chave;
  protected readonly rotuloGrupo = (g: GrupoItens) => `${g.material.codigo} ${g.material.descricao}`;
  protected readonly temSeries = (g: GrupoItens) => g.series.length > 0;

  protected readonly colunasItens: CesDataTableColumn<GrupoItens>[] = [
    { key: 'codigo', header: 'Código', priority: 1, width: '7rem', value: (g) => g.material.codigo },
    {
      key: 'descricao',
      header: 'Material / Descrição',
      type: 'custom',
      priority: 1,
      searchable: true,
      value: (g) => g.material.descricao,
      // A busca e o CSV também encontram o material pelo número de série.
      display: (g) => [g.material.descricao, ...g.series].join(' '),
    },
    { key: 'condicao', header: 'Condição', type: 'custom', priority: 2, value: (g) => rotuloCondicao(g.condicao) },
    { key: 'unidade', header: 'Un.', priority: 2, width: '4rem', value: (g) => g.material.unidadeMedida },
    { key: 'quantidade', header: 'Quantidade', type: 'number', priority: 1, width: '8rem', value: (g) => g.quantidade },
  ];

  ngOnInit() {
    this.route.queryParams?.subscribe((params) => {
      this.isPrintMode.set(params['mode'] === 'print');
    });
    this.loadData();
    this.route.params?.subscribe((params) => {
      if (params['id']) {
        this.loadData(params['id']);
      }
    });
  }

  ngAfterViewInit() {
    if (this.isPrintMode()) {
      setTimeout(() => window.print(), 1000);
    }
  }

  protected loadData(targetId?: string) {
    const id = targetId || this.route.snapshot.params['id'];
    if (!id) return;

    this.loading.set(true);
    this.error.set(null);

    this.almoxarifadoService.buscarOperacaoPorIdReferencia(id).subscribe({
      next: (res) => {
        this.movimentacao.set(res);
        this.loading.set(false);
        this.carregarInformacoesAdicionais(res);
        if (res.tipo === 'TRANSFERENCIA') {
          this.carregarAssinaturas(res.idReferenciaOperacao);
        } else {
          this.revogarUrlsAssinaturas();
        }
      },
      error: (err) => {
        this.error.set(getErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  private carregarAssinaturas(idReferencia: string): void {
    this.revogarUrlsAssinaturas();
    this.carregarAssinatura(idReferencia, 'ENTREGADOR', this.assinaturaEntregadorUrl);
    this.carregarAssinatura(idReferencia, 'RECEBEDOR', this.assinaturaRecebedorUrl);
  }

  private carregarAssinatura(
    idReferencia: string,
    parte: 'ENTREGADOR' | 'RECEBEDOR',
    target: ReturnType<typeof signal<string | null>>,
  ): void {
    this.almoxarifadoService.buscarAssinaturaTransferencia(idReferencia, parte).subscribe({
      next: (blob) => {
        if (blob && blob.size > 0) {
          target.set(URL.createObjectURL(blob));
        } else {
          target.set(null);
        }
      },
      error: () => target.set(null),
    });
  }

  private revogarUrlsAssinaturas(): void {
    for (const url of [this.assinaturaEntregadorUrl(), this.assinaturaRecebedorUrl()]) {
      if (url) {
        URL.revokeObjectURL(url);
      }
    }
    this.assinaturaEntregadorUrl.set(null);
    this.assinaturaRecebedorUrl.set(null);
  }

  ngOnDestroy(): void {
    this.revogarUrlsAssinaturas();
  }

  private carregarInformacoesAdicionais(mov: MovimentacaoOperacaoResponse) {
    // 1. Resolução da Equipe
    const idEquipe = mov.idEquipe || mov.estoqueDestino?.idEquipe || mov.estoqueOrigem?.idEquipe;
    if (idEquipe) {
      this.rhService.buscarEquipePorId(idEquipe).subscribe({
        next: (eq) => this.equipe.set(eq),
        error: () => this.equipe.set(null),
      });
    } else {
      this.equipe.set(null);
    }

    // 2. Resolução da Obra
    if (mov.idObra) {
      this.obrasService.buscarObraPorId(mov.idObra).subscribe({
        next: (ob) => this.obra.set(ob),
        error: () => this.obra.set(null),
      });
    } else {
      this.obra.set(null);
    }

    // 3. Bases Operacionais dos estoques
    if (mov.estoqueOrigem?.idBaseOperacional) {
      this.rhService.buscarBaseOperacionalPorId(mov.estoqueOrigem.idBaseOperacional).subscribe({
        next: (b) => this.baseOrigem.set(b),
        error: () => this.baseOrigem.set(null),
      });
    } else {
      this.baseOrigem.set(null);
    }

    if (mov.estoqueDestino?.idBaseOperacional) {
      this.rhService.buscarBaseOperacionalPorId(mov.estoqueDestino.idBaseOperacional).subscribe({
        next: (b) => this.baseDestino.set(b),
        error: () => this.baseDestino.set(null),
      });
    } else {
      this.baseDestino.set(null);
    }
  }

  protected abrirSaldo(estoqueId: string | undefined, nome: string | undefined) {
    if (estoqueId && nome) {
      this.saldoDialog.show(estoqueId, nome);
    }
  }

  protected voltar() {
    this.navegacao.back(['/almoxarifado/movimentacoes']);
  }

  protected abrirSolicitacao(idSolicitacao: string, contexto?: string | null) {
    if (!idSolicitacao) return;
    if (contexto === 'TECNICO') {
      void this.router.navigate(['/almoxarifado/solicitacoes-tecnicas', idSolicitacao]);
    } else {
      void this.router.navigate(['/almoxarifado/solicitacoes', idSolicitacao]);
    }
  }

  protected abrirObra(idObra: string) {
    if (!idObra) return;
    void this.router.navigate(['/obras', idObra]);
  }

  protected copiarId(id: string) {
    if (!id) return;
    navigator.clipboard?.writeText(id).then(() => {
      this.idCopiado.set(true);
      setTimeout(() => this.idCopiado.set(false), 2000);
    });
  }

  protected getTipoTone(tipo?: TipoMovimentacao): CesTone {
    return tomTipoMovimentacao(tipo);
  }

  protected getTipoLabel(tipo?: TipoMovimentacao): string {
    return rotuloTipoMovimentacao(tipo);
  }

  protected getTipoIcon(tipo?: TipoMovimentacao): string {
    return iconeTipoMovimentacao(tipo);
  }

  protected getTipoBannerTitle(tipo?: TipoMovimentacao): string {
    switch (tipo) {
      case 'ENTRADA_AVULSA':
        return 'Entrada Avulsa de Materiais';
      case 'BAIXA_AVULSA':
        return 'Baixa Avulsa de Estoque';
      case 'TRANSFERENCIA':
        return 'Transferência entre Estoques';
      case 'ENTREGA':
        return 'Entrega de Materiais';
      case 'DEVOLUCAO':
        return 'Devolução de Materiais';
      case 'ESTORNO':
        return 'Estorno de Movimentação';
      case 'RETIRADA':
        return 'Retirada de Materiais';
      case 'APLICACAO':
        return 'Aplicação de Materiais';
      default:
        return 'Operação de Movimentação';
    }
  }

  protected getTipoBannerSubtitle(mov: MovimentacaoOperacaoResponse): string {
    if (mov.tipo === 'DEVOLUCAO') {
      const orig = mov.estoqueOrigem?.nomeIdentificador || 'Equipe';
      const dest = mov.estoqueDestino?.nomeIdentificador || 'Almoxarifado';
      return `Devolução da equipe (${orig}) para o almoxarifado (${dest})`;
    }
    if (mov.tipo === 'TRANSFERENCIA') {
      const orig = mov.estoqueOrigem?.nomeIdentificador || 'Origem';
      const dest = mov.estoqueDestino?.nomeIdentificador || 'Destino';
      return `Transferência de saldo de ${orig} para ${dest}`;
    }
    if (mov.tipo === 'ENTRADA_AVULSA') {
      return `Recebido e integrado ao estoque: ${mov.estoqueDestino?.nomeIdentificador || 'Almoxarifado'}`;
    }
    if (mov.tipo === 'BAIXA_AVULSA') {
      return `Baixado e debitado do estoque: ${mov.estoqueOrigem?.nomeIdentificador || 'Almoxarifado'}`;
    }
    return `Operação processada no almoxarifado em ${new Date(mov.dataHora).toLocaleDateString('pt-BR')}`;
  }

  protected getTipoTecnicoLabel(tipo?: string | null): string {
    switch (tipo) {
      case 'ADICAO_COMUM':
        return 'Adição de Material Comum';
      case 'ADICAO_CONCRETO':
        return 'Adição de Poste / Concreto';
      case 'DEVOLUCAO_COMUM':
        return 'Devolução de Material Comum';
      case 'DEVOLUCAO_CONCRETO':
        return 'Devolução de Poste / Concreto';
      default:
        return tipo ? tipo.replace('_', ' ') : '—';
    }
  }

  protected getTipoObraLabel(tipo?: string | null): string {
    switch (tipo) {
      case 'OBRA':
        return 'Obra';
      case 'CORRETIVA':
        return 'Manutenção Corretiva';
      case 'PREVENTIVA':
        return 'Manutenção Preventiva';
      case 'LPT':
        return 'Linha Viva / LPT';
      default:
        return tipo || '—';
    }
  }

  protected calculateTotal(itens: MovimentacaoItemResponse[]): number {
    return (itens || []).reduce((acc, item) => acc + (item.quantidade || 0), 0);
  }

  protected printReport() {
    window.print();
  }
}
