import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  CesReportNavigatorConfig,
  CesReportNavigatorCriteria,
  CesReportNavigatorDataSource,
  CesReportNavigatorItem,
  CesReportNavigatorPage
} from '../../../design-system/patterns/report-navigator/report-navigator.models';
import { EstoqueResponse } from '../../../core/models/almoxarifado.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';

@Injectable({ providedIn: 'root' })
export class SaldoEstoqueReportNavigatorDataSource
  implements CesReportNavigatorDataSource<EstoqueResponse>
{
  private readonly service = inject(AlmoxarifadoService);

  readonly initialCriteria: Partial<CesReportNavigatorCriteria> = {
    scope: 'TODOS',
    sort: 'nomeIdentificador,asc'
  };

  readonly config: CesReportNavigatorConfig = {
    title: 'Saldos de Estoque',
    icon: 'pi pi-chart-bar',
    eyebrow: 'Navegador',
    searchPlaceholder: 'Buscar estoque...',
    searchLabel: 'Buscar estoques',
    listAllLabel: 'Ver todos os estoques',
    sortOptions: [
      { label: 'Nome (A-Z)', value: 'nomeIdentificador,asc' },
      { label: 'Nome (Z-A)', value: 'nomeIdentificador,desc' }
    ],
    totalLabel: (total) => `${total} ${total === 1 ? 'estoque' : 'estoques'}`
  };

  load(
    criteria: CesReportNavigatorCriteria
  ): Observable<CesReportNavigatorPage<EstoqueResponse>> {
    const params: any = {
      page: 0,
      size: 100,
      sort: criteria.sort || 'nomeIdentificador,asc',
      busca: criteria.search.trim() || undefined,
      ativo: true
    };

    return this.service.listarEstoques(params).pipe(
      map((res) => ({
        items: res.content || [],
        totalElements: res.page?.totalElements ?? res.content.length
      }))
    );
  }

  toItem(raw: EstoqueResponse): CesReportNavigatorItem {
    const tipoNome = raw.tipoEstoque?.nome || 'Estoque';

    return {
      id: raw.id,
      number: `#${raw.id.substring(0, 8)}`,
      typePill: {
        label: tipoNome,
        icon: 'pi pi-box',
        variant: 'brand'
      },
      title: raw.nomeIdentificador,
      status: {
        label: raw.ativo ? 'Ativo' : 'Inativo',
        tone: raw.ativo ? 'success' : 'neutral'
      },
      metaAuthor: {
        label: tipoNome,
        icon: 'pi pi-tag',
        title: `Tipo: ${tipoNome}`
      },
      raw
    };
  }

  getRouteForItem(item: CesReportNavigatorItem | EstoqueResponse): string {
    const id = (item as any).id;
    return `/almoxarifado/saldos/relatorio?idEstoque=${id}`;
  }

  getListRoute(): any[] {
    return ['/almoxarifado', 'saldos'];
  }
}
