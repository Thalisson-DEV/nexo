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
  InspecaoFilters,
  InspecaoSegurancaResponse,
  StatusInspecao
} from '../../../core/models/sst.models';
import { SstService } from '../../../core/services/sst.service';
import { CesTone } from '../../../design-system/core';

const STATUS_LABELS: Record<StatusInspecao, string> = {
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDA: 'Concluída',
  CANCELADA: 'Cancelada'
};

const STATUS_TONES: Record<StatusInspecao, CesTone> = {
  EM_ANDAMENTO: 'warning',
  CONCLUIDA: 'success',
  CANCELADA: 'danger'
};

@Injectable({ providedIn: 'root' })
export class InspecaoSstReportNavigatorDataSource
  implements CesReportNavigatorDataSource<InspecaoSegurancaResponse>
{
  private readonly service = inject(SstService);

  readonly initialCriteria: Partial<CesReportNavigatorCriteria> = {
    scope: 'TODAS',
    sort: 'dataRealizacao,desc'
  };

  readonly config: CesReportNavigatorConfig = {
    title: 'Inspeções de SST',
    icon: 'pi pi-shield',
    eyebrow: 'Navegador',
    searchPlaceholder: 'Buscar por formulário ou inspetor...',
    searchLabel: 'Buscar inspeções',
    listAllLabel: 'Ver todas as inspeções',
    scopeOptions: [
      { label: 'Todas', value: 'TODAS' },
      { label: 'Minhas', value: 'MINHAS' },
      { label: 'Concluídas', value: 'CONCLUIDA' }
    ],
    sortOptions: [
      { label: 'Mais recentes', value: 'dataRealizacao,desc' },
      { label: 'Mais antigas', value: 'dataRealizacao,asc' }
    ],
    totalLabel: (total) => `${total} ${total === 1 ? 'inspeção' : 'inspeções'}`
  };

  load(
    criteria: CesReportNavigatorCriteria
  ): Observable<CesReportNavigatorPage<InspecaoSegurancaResponse>> {
    const page = 0;
    const size = 50;

    const filtros: InspecaoFilters = {
      page,
      size,
      sort: criteria.sort || 'dataRealizacao,desc',
      buscaGlobal: criteria.search.trim() || undefined
    };

    if (criteria.scope === 'CONCLUIDA') {
      filtros.status = ['CONCLUIDA'];
    }

    const obs$ =
      criteria.scope === 'MINHAS'
        ? this.service.listarMinhasInspecoes(filtros)
        : this.service.listarInspecoes(filtros);

    return obs$.pipe(
      map((res) => ({
        items: res.content || [],
        totalElements: res.page?.totalElements ?? res.content.length
      }))
    );
  }

  toItem(raw: InspecaoSegurancaResponse): CesReportNavigatorItem {
    const status = raw.status;
    const titleEquipe =
      raw.equipe?.codigo || raw.veiculo?.placa || raw.processo?.nome || 'Inspeção';

    return {
      id: raw.id,
      number: `#${raw.id.substring(0, 8)}`,
      typePill: {
        label: raw.formulario?.titulo || 'SST',
        icon: 'pi pi-shield',
        variant: 'info'
      },
      title: `${titleEquipe}${raw.processo?.nome ? ' · ' + raw.processo.nome : ''}`,
      status: {
        label: STATUS_LABELS[status] || status,
        tone: STATUS_TONES[status] || 'neutral'
      },
      metaAuthor: {
        label: raw.nomeInspetor || 'Inspetor',
        icon: 'pi pi-user',
        title: `Inspetor: ${raw.nomeInspetor || '-'}`
      },
      metaDate: {
        date: raw.dataRealizacao,
        title: 'Data de realização'
      },
      raw
    };
  }

  getRouteForItem(item: CesReportNavigatorItem | InspecaoSegurancaResponse): any[] {
    const id = (item as any).id;
    return ['/sst', 'inspecoes', id];
  }

  getListRoute(): any[] {
    return ['/sst', 'inspecoes'];
  }
}
