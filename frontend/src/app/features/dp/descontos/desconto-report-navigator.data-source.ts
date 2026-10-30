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
  DescontoResponse,
  DescontoStatus,
  getValorEfetivo
} from '../../../core/models/desconto.models';
import { RhDescontosService } from '../../../core/services/rh-descontos.service';
import { CesTone } from '../../../design-system/core';

const STATUS_TONES: Record<DescontoStatus, CesTone> = {
  SOLICITADO: 'warning',
  VALIDADO: 'info',
  BAIXADO: 'success',
  REJEITADO: 'danger'
};

const STATUS_LABELS: Record<DescontoStatus, string> = {
  SOLICITADO: 'Lançado',
  VALIDADO: 'Validado',
  BAIXADO: 'Baixado',
  REJEITADO: 'Rejeitado'
};

@Injectable({ providedIn: 'root' })
export class DescontoReportNavigatorDataSource
  implements CesReportNavigatorDataSource<DescontoResponse>
{
  private readonly descontosService = inject(RhDescontosService);

  readonly initialCriteria: Partial<CesReportNavigatorCriteria> = {
    status: null,
    sort: 'dataCriacao,desc'
  };

  readonly config: CesReportNavigatorConfig = {
    title: 'Descontos em folha',
    icon: 'pi pi-money-bill',
    eyebrow: 'Departamento Pessoal',
    statusChips: [
      { label: 'Todos', value: null, icon: 'pi pi-list' },
      { label: 'Lançados', value: 'SOLICITADO', tone: 'warning', icon: 'pi pi-clock' },
      { label: 'Validados', value: 'VALIDADO', tone: 'info', icon: 'pi pi-check' },
      { label: 'Baixados', value: 'BAIXADO', tone: 'success', icon: 'pi pi-check-circle' },
      { label: 'Rejeitados', value: 'REJEITADO', tone: 'danger', icon: 'pi pi-times-circle' }
    ],
    sortOptions: [
      { label: 'Mais recentes', value: 'dataCriacao,desc' },
      { label: 'Mais antigos', value: 'dataCriacao,asc' },
      { label: 'Maior valor', value: 'valorSugerido,desc' }
    ],
    searchPlaceholder: 'Buscar desconto...',
    searchLabel: 'Buscar descontos em folha',
    clearFiltersLabel: 'Limpar',
    listAllLabel: 'Ver todos',
    listAllTooltip: 'Abrir lista de descontos',
    totalLabel: (total) => `${total} ${total === 1 ? 'desconto' : 'descontos'}`
  };

  load(criteria: CesReportNavigatorCriteria): Observable<CesReportNavigatorPage<DescontoResponse>> {
    return this.descontosService
      .buscarDescontos({
        buscaGlobal: criteria.search.trim() || undefined,
        status: (criteria.status as DescontoStatus) || undefined,
        sort: criteria.sort || 'dataCriacao,desc',
        page: 0,
        size: 50
      })
      .pipe(
        map((res) => ({
          items: res.content || [],
          totalElements: res.page?.totalElements ?? res.content.length
        }))
      );
  }

  toItem(raw: DescontoResponse): CesReportNavigatorItem {
    const valor = getValorEfetivo(raw);
    const motivoDescricao = raw.motivo?.descricao || 'Desconto';

    return {
      id: raw.id,
      number: `#${raw.id.substring(0, 8)}`,
      typePill: {
        label: motivoDescricao,
        variant: 'brand',
        icon: 'pi pi-tag'
      },
      value: valor !== undefined && valor !== null ? valor : null,
      title: `${motivoDescricao} · ${raw.quantidadeParcelas}x`,
      status: {
        label: STATUS_LABELS[raw.status] || raw.status,
        tone: STATUS_TONES[raw.status] || 'neutral'
      },
      metaAuthor: {
        label: 'Colaborador',
        icon: 'pi pi-user',
        title: `Colaborador: ${raw.colaboradorId}`
      },
      metaDate: {
        date: raw.dataOcorrencia || raw.dataCriacao,
        format: 'dd/MM/yy',
        title: `Ocorrência: ${raw.dataOcorrencia || raw.dataCriacao}`
      },
      raw
    };
  }

  getRouteForItem(item: CesReportNavigatorItem | DescontoResponse): string[] {
    const raw = (item as CesReportNavigatorItem).raw as DescontoResponse | undefined ?? (item as DescontoResponse);
    return ['/dp/descontos', String(raw.id || item.id)];
  }

  getListRoute(): string[] {
    return ['/dp/descontos', 'lancamento'];
  }
}
