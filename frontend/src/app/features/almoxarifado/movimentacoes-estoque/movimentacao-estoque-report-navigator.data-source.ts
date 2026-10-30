import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  CesReportNavigatorConfig,
  CesReportNavigatorCriteria,
  CesReportNavigatorDataSource,
  CesReportNavigatorItem,
  CesReportNavigatorPage
} from '../../../design-system/patterns/report-navigator/report-navigator.models';
import {
  MovimentacaoOperacaoSummaryResponse,
  TipoMovimentacao
} from '../../../core/models/almoxarifado.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { CesTone } from '../../../design-system/core';

const TIPO_LABELS: Record<TipoMovimentacao, string> = {
  ENTRADA_AVULSA: 'Entrada',
  BAIXA_AVULSA: 'Baixa',
  TRANSFERENCIA: 'Transferência',
  ENTREGA: 'Entrega',
  DEVOLUCAO: 'Devolução',
  ESTORNO: 'Estorno',
  RETIRADA: 'Retirada',
  APLICACAO: 'Aplicação',
};

const TIPO_TONES: Record<TipoMovimentacao, CesTone> = {
  ENTRADA_AVULSA: 'success',
  BAIXA_AVULSA: 'warning',
  TRANSFERENCIA: 'info',
  ENTREGA: 'success',
  DEVOLUCAO: 'brand',
  ESTORNO: 'danger',
  RETIRADA: 'info',
  APLICACAO: 'neutral',
};

@Injectable({ providedIn: 'root' })
export class MovimentacaoEstoqueReportNavigatorDataSource
  implements CesReportNavigatorDataSource<MovimentacaoOperacaoSummaryResponse>
{
  private readonly service = inject(AlmoxarifadoService);

  readonly initialCriteria: Partial<CesReportNavigatorCriteria> = {
    scope: 'TODAS',
    sort: 'dataHora,desc'
  };

  readonly config: CesReportNavigatorConfig = {
    title: 'Movimentações de Estoque',
    icon: 'pi pi-arrow-right-arrow-left',
    eyebrow: 'Navegador',
    searchPlaceholder: 'Buscar por estoque ou operador...',
    searchLabel: 'Buscar movimentações',
    listAllLabel: 'Ver todas as movimentações',
    scopeOptions: [
      { label: 'Todas', value: 'TODAS' },
      { label: 'Entradas', value: 'ENTRADA_AVULSA' },
      { label: 'Baixas', value: 'BAIXA_AVULSA' },
      { label: 'Transferências', value: 'TRANSFERENCIA' },
      { label: 'Entregas', value: 'ENTREGA' },
      { label: 'Devoluções', value: 'DEVOLUCAO' },
      { label: 'Estornos', value: 'ESTORNO' },
      { label: 'Retiradas', value: 'RETIRADA' }
    ],
    sortOptions: [
      { label: 'Mais recentes', value: 'dataHora,desc' },
      { label: 'Mais antigas', value: 'dataHora,asc' }
    ],
    totalLabel: (total) => `${total} ${total === 1 ? 'movimentação' : 'movimentações'}`
  };

  load(
    criteria: CesReportNavigatorCriteria
  ): Observable<CesReportNavigatorPage<MovimentacaoOperacaoSummaryResponse>> {
    const params: any = {
      page: 0,
      size: 50,
      sort: criteria.sort || 'dataHora,desc',
      tipo:
        criteria.scope && criteria.scope !== 'TODAS'
          ? (criteria.scope as TipoMovimentacao)
          : undefined,
      busca: criteria.search.trim() || undefined
    };

    return this.service.listarMovimentacoes(params).pipe(
      map((res) => ({
        items: res.content || [],
        totalElements: res.page?.totalElements ?? res.content.length
      }))
    );
  }

  toItem(raw: MovimentacaoOperacaoSummaryResponse): CesReportNavigatorItem {
    const tipo = raw.tipo;
    const origem = raw.estoqueOrigem?.nomeIdentificador;
    const destino = raw.estoqueDestino?.nomeIdentificador;
    const rota = origem && destino ? `${origem} → ${destino}` : origem || destino || 'Operação de estoque';

    return {
      id: raw.idReferenciaOperacao,
      number: `#${raw.idReferenciaOperacao.substring(0, 8)}`,
      typePill: {
        label: TIPO_LABELS[tipo] || tipo,
        icon: 'pi pi-arrow-right-arrow-left',
        variant: tipo === 'DEVOLUCAO' ? 'devolucao' : (TIPO_TONES[tipo] || 'neutral')
      },
      title: rota,
      status: {
        label: TIPO_LABELS[tipo] || tipo,
        tone: TIPO_TONES[tipo] || 'neutral'
      },
      metaAuthor: {
        label: raw.operadorNome || 'Operador',
        icon: 'pi pi-user',
        title: `Operador: ${raw.operadorNome || '-'}`
      },
      metaDate: {
        date: raw.dataHora,
        title: 'Data da operação'
      },
      raw
    };
  }

  getRouteForItem(
    item: CesReportNavigatorItem | MovimentacaoOperacaoSummaryResponse
  ): any[] {
    const raw = item as any;
    const refId = raw.idReferenciaOperacao || raw.id;
    return ['/almoxarifado', 'movimentacoes', refId];
  }

  getListRoute(): any[] {
    return ['/almoxarifado', 'movimentacoes'];
  }
}
