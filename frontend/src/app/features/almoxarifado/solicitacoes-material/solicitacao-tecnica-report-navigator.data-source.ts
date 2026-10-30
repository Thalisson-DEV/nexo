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
  SolicitacaoMaterialFilter,
  SolicitacaoMaterialSummaryResponse,
  StatusSolicitacaoMaterial
} from '../../../core/models/solicitacao-material-tecnica.models';
import { SolicitacaoMaterialTecnicaService } from '../../../core/services/solicitacao-material-tecnica.service';
import { CesTone } from '../../../design-system/core';

const STATUS_LABELS: Record<StatusSolicitacaoMaterial, string> = {
  PENDENTE: 'Pendente',
  APROVADO: 'Aprovado',
  REJEITADO: 'Rejeitado',
  ENTREGUE_PARCIAL: 'Entregue parcial',
  ENTREGUE: 'Entregue',
  DEVOLVIDO_PARCIAL: 'Devolvido parcial',
  DEVOLVIDO: 'Devolvido',
  ESTORNADO: 'Estornado',
  CANCELADO: 'Cancelado'
};

const STATUS_TONES: Record<StatusSolicitacaoMaterial, CesTone> = {
  PENDENTE: 'warning',
  APROVADO: 'info',
  REJEITADO: 'danger',
  ENTREGUE_PARCIAL: 'warning',
  ENTREGUE: 'success',
  DEVOLVIDO_PARCIAL: 'warning',
  DEVOLVIDO: 'success',
  ESTORNADO: 'neutral',
  CANCELADO: 'neutral'
};

@Injectable({ providedIn: 'root' })
export class SolicitacaoTecnicaReportNavigatorDataSource
  implements CesReportNavigatorDataSource<SolicitacaoMaterialSummaryResponse>
{
  private readonly service = inject(SolicitacaoMaterialTecnicaService);

  readonly initialCriteria: Partial<CesReportNavigatorCriteria> = {
    scope: 'TODAS',
    sort: 'dataSolicitacao,desc'
  };

  readonly config: CesReportNavigatorConfig = {
    title: 'Solicitações Técnicas',
    icon: 'pi pi-wrench',
    eyebrow: 'Navegador',
    searchPlaceholder: 'Buscar por reserva ou destino...',
    searchLabel: 'Buscar solicitações técnicas',
    listAllLabel: 'Ver todas as solicitações técnicas',
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
  ): Observable<CesReportNavigatorPage<SolicitacaoMaterialSummaryResponse>> {
    const filtros: SolicitacaoMaterialFilter = {
      page: 0,
      size: 50,
      sort: criteria.sort || 'dataSolicitacao,desc',
      buscaGlobal: criteria.search.trim() || undefined,
      status: criteria.status ? (criteria.status as StatusSolicitacaoMaterial) : undefined
    };

    return this.service.listar(filtros).pipe(
      map((res) => ({
        items: res.content || [],
        totalElements: res.page?.totalElements ?? res.content.length
      }))
    );
  }

  toItem(raw: SolicitacaoMaterialSummaryResponse): CesReportNavigatorItem {
    const status = raw.status;

    return {
      id: raw.id,
      number: raw.numeroReserva ? `#${raw.numeroReserva}` : `#${raw.id.substring(0, 8)}`,
      typePill: {
        label: raw.tipo || 'Técnica',
        icon: 'pi pi-wrench',
        variant: raw.tipo?.includes('DEVOLUCAO') ? 'devolucao' : 'info'
      },
      title: `${raw.estoqueOrigemNome || 'Origem'} → ${raw.estoqueDestinoNome || 'Destino'}`,
      status: {
        label: STATUS_LABELS[status] || status,
        tone: STATUS_TONES[status] || 'neutral'
      },
      metaAuthor: {
        label: raw.nomeAgenteReserva || 'Agente',
        icon: 'pi pi-user',
        title: `Agente / Responsável: ${raw.nomeAgenteReserva || '-'}`
      },
      metaDate: {
        date: raw.dataSolicitacao,
        title: 'Data da solicitação'
      },
      raw
    };
  }

  getRouteForItem(
    item: CesReportNavigatorItem | SolicitacaoMaterialSummaryResponse
  ): any[] {
    const id = (item as any).id;
    return ['/almoxarifado', 'solicitacoes-tecnicas', id];
  }

  getListRoute(): any[] {
    return ['/almoxarifado', 'solicitacoes-tecnicas'];
  }
}
