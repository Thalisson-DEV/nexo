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
  SolicitacaoStatus,
  SolicitacaoSummaryResponse
} from '../../../core/models/almoxarifado.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { CesTone } from '../../../design-system/core';

const STATUS_LABELS: Record<SolicitacaoStatus, string> = {
  PENDENTE: 'Pendente',
  APROVADO: 'Aprovado',
  REJEITADO: 'Rejeitado',
  ENTREGUE_PARCIAL: 'Entregue parcial',
  ENTREGUE: 'Entregue',
  ESTORNADO: 'Estornado'
};

const STATUS_TONES: Record<SolicitacaoStatus, CesTone> = {
  PENDENTE: 'warning',
  APROVADO: 'info',
  REJEITADO: 'danger',
  ENTREGUE_PARCIAL: 'warning',
  ENTREGUE: 'success',
  ESTORNADO: 'neutral'
};

@Injectable({ providedIn: 'root' })
export class SolicitacaoMaterialReportNavigatorDataSource
  implements CesReportNavigatorDataSource<SolicitacaoSummaryResponse>
{
  private readonly service = inject(AlmoxarifadoService);

  readonly initialCriteria: Partial<CesReportNavigatorCriteria> = {
    scope: 'TODAS',
    sort: 'dataSolicitacao,desc'
  };

  readonly config: CesReportNavigatorConfig = {
    title: 'Solicitações de Material',
    icon: 'pi pi-box',
    eyebrow: 'Navegador',
    searchPlaceholder: 'Buscar por destino ou requisitante...',
    searchLabel: 'Buscar solicitações de material',
    listAllLabel: 'Ver todas as solicitações',
    statusChips: [
      { label: 'Todas', value: null, icon: 'pi pi-list' },
      { label: 'Pendentes', value: 'PENDENTE', tone: 'warning', icon: 'pi pi-clock' },
      { label: 'Aprovadas', value: 'APROVADO', tone: 'info', icon: 'pi pi-check' },
      { label: 'Entregues', value: 'ENTREGUE', tone: 'success', icon: 'pi pi-check-circle' },
      { label: 'Rejeitadas', value: 'REJEITADO', tone: 'danger', icon: 'pi pi-times-circle' }
    ],
    sortOptions: [
      { label: 'Mais recentes', value: 'dataSolicitacao,desc' },
      { label: 'Mais antigas', value: 'dataSolicitacao,asc' }
    ],
    totalLabel: (total) => `${total} ${total === 1 ? 'solicitação' : 'solicitações'}`
  };

  load(
    criteria: CesReportNavigatorCriteria
  ): Observable<CesReportNavigatorPage<SolicitacaoSummaryResponse>> {
    const params: any = {
      page: 0,
      size: 50,
      sort: criteria.sort || 'dataSolicitacao,desc',
      status: criteria.status ? (criteria.status as SolicitacaoStatus) : undefined,
      busca: criteria.search.trim() || undefined
    };

    return this.service.listarSolicitacoes(params).pipe(
      map((res) => ({
        items: res.content || [],
        totalElements: res.page?.totalElements ?? res.content.length
      }))
    );
  }

  toItem(raw: SolicitacaoSummaryResponse): CesReportNavigatorItem {
    const status = raw.status;

    return {
      id: raw.id,
      number: `#${raw.id.substring(0, 8)}`,
      typePill: {
        label: 'Material',
        icon: 'pi pi-box',
        variant: 'brand'
      },
      title: `${raw.estoqueOrigemNomeIdentificador || 'Origem'} → ${raw.estoqueDestinoNomeIdentificador || 'Destino'}`,
      status: {
        label: STATUS_LABELS[status] || status,
        tone: STATUS_TONES[status] || 'neutral'
      },
      metaAuthor: {
        label: 'Requisitante',
        icon: 'pi pi-user',
        title: `Requisitante ID: ${raw.idRequisitante}`
      },
      metaDate: {
        date: raw.dataSolicitacao,
        title: 'Data da solicitação'
      },
      raw
    };
  }

  getRouteForItem(item: CesReportNavigatorItem | SolicitacaoSummaryResponse): any[] {
    const id = (item as any).id;
    return ['/almoxarifado', 'solicitacoes', id];
  }

  getListRoute(): any[] {
    return ['/almoxarifado', 'solicitacoes'];
  }
}
