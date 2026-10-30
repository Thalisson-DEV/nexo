import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  CesReportNavigatorConfig,
  CesReportNavigatorCriteria,
  CesReportNavigatorDataSource,
  CesReportNavigatorItem,
  CesReportNavigatorPage
} from '../../design-system/patterns/report-navigator/report-navigator.models';
import { StatusTurno, TurnoConsulta } from '../../core/models/dashboard.models';
import { DashboardService } from '../../core/services/dashboard.service';
import { situacaoDoTurno } from './turno-list/turno-list.utils';

/** Navega entre os relatórios de turno, pela mesma consulta da tela de turnos. */
@Injectable({ providedIn: 'root' })
export class TurnoReportNavigatorDataSource implements CesReportNavigatorDataSource<TurnoConsulta> {
  private readonly dashboardService = inject(DashboardService);

  readonly initialCriteria: Partial<CesReportNavigatorCriteria> = {
    scope: 'TODOS',
    sort: 'dataInicio,desc'
  };

  readonly config: CesReportNavigatorConfig = {
    title: 'Turnos',
    icon: 'pi pi-calendar-clock',
    eyebrow: 'Navegador',
    searchPlaceholder: 'Buscar pelo código da equipe...',
    searchLabel: 'Buscar turnos',
    listAllLabel: 'Ver todos os turnos',
    scopeOptions: [
      { label: 'Todos', value: 'TODOS' },
      { label: 'Em turno', value: 'ABERTO' },
      { label: 'Encerrados', value: 'FECHADO' }
    ],
    sortOptions: [
      { label: 'Mais recentes', value: 'dataInicio,desc' },
      { label: 'Mais antigos', value: 'dataInicio,asc' }
    ],
    totalLabel: (total) => `${total} ${total === 1 ? 'turno' : 'turnos'}`
  };

  load(criteria: CesReportNavigatorCriteria): Observable<CesReportNavigatorPage<TurnoConsulta>> {
    const status =
      criteria.scope === 'ABERTO' || criteria.scope === 'FECHADO' ? (criteria.scope as StatusTurno) : undefined;
    return this.dashboardService
      .consultarTurnos({
        page: 0,
        size: 50,
        sort: [criteria.sort || 'dataInicio,desc'],
        status,
        buscaGlobal: criteria.search.trim() || undefined
      })
      .pipe(
        map((res) => ({
          items: res.content,
          totalElements: res.page?.totalElements ?? res.content.length
        }))
      );
  }

  toItem(raw: TurnoConsulta): CesReportNavigatorItem {
    const situacao = situacaoDoTurno(raw);
    const equipe = raw.codigoEquipe || 'Equipe';
    return {
      id: raw.id,
      number: equipe,
      typePill: {
        label: 'Turno',
        icon: 'pi pi-calendar-clock',
        variant: 'info'
      },
      title: `${equipe} · ${raw.notas} ${raw.notas === 1 ? 'nota' : 'notas'}${raw.placa ? ' · ' + raw.placa : ''}`,
      value: raw.producao,
      status: { label: situacao.rotulo, tone: situacao.tom },
      metaAuthor: {
        label: raw.abertoPor || equipe,
        icon: 'pi pi-user',
        title: raw.abertoPor ? `Aberto por ${raw.abertoPor}` : `Equipe: ${equipe}`
      },
      metaDate: { date: raw.dataInicio, title: 'Abertura do turno', format: 'dd/MM HH:mm' },
      raw
    };
  }

  getRouteForItem(item: CesReportNavigatorItem | TurnoConsulta): any[] {
    return ['/execucao', 'turnos', (item as { id: string }).id, 'detalhamento'];
  }

  getListRoute(): any[] {
    return ['/execucao', 'turnos'];
  }
}
