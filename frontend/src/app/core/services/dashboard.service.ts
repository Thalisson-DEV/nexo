import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import { PagedResponse } from '../models/shared.models';
import {
  ConsultaTurnosFiltros,
  ContextoExecucaoResponse,
  DashboardOperacionalFiltros,
  DashboardOperacionalResponse,
  DashboardTurnosFiltros,
  DashboardTurnosResponse,
  DetalhamentoTurnoResponse,
  NotaDoRecorte,
  NotasDoRecorteFiltros,
  TurnoConsulta
} from '../models/dashboard.models';

@Injectable({
  providedIn: 'root'
})
export class DashboardService extends BaseService {
  private readonly apiUrl = `${environment.apiUrl}/v1/dashboards`;

  /** Produção das equipes no período, comparada à meta proporcional e ao ritmo. */
  gerarDashboardOperacional(filtros: DashboardOperacionalFiltros): Observable<DashboardOperacionalResponse> {
    return this.http.get<DashboardOperacionalResponse>(`${this.apiUrl}/operacional`, { params: semVazios(filtros) });
  }

  /** Situação dos turnos das equipes em um dia (padrão: hoje). */
  gerarDashboardTurnos(filtros: DashboardTurnosFiltros): Observable<DashboardTurnosResponse> {
    return this.http.get<DashboardTurnosResponse>(`${this.apiUrl}/turnos`, { params: semVazios(filtros) });
  }

  /** Relatório de um turno: composição, notas, indicadores e histórico da equipe. */
  detalharTurno(idTurno: string): Observable<DetalhamentoTurnoResponse> {
    return this.http.get<DetalhamentoTurnoResponse>(`${this.apiUrl}/turnos/${idTurno}/detalhamento`);
  }

  /** Consulta paginada de turnos; ordena só por dataInicio, dataFim e status. */
  consultarTurnos(filtros: ConsultaTurnosFiltros): Observable<PagedResponse<TurnoConsulta>> {
    return this.getList<TurnoConsulta>(`${this.apiUrl}/turnos/consulta`, filtros);
  }

  /** Notas concluídas que compõem os números dos dashboards (mesmo escopo, para o drill-down fechar a conta). */
  consultarNotas(filtros: NotasDoRecorteFiltros): Observable<PagedResponse<NotaDoRecorte>> {
    return this.getList<NotaDoRecorte>(`${this.apiUrl}/notas`, filtros);
  }

  /** Turno, dia da equipe, média da equipe e valor das ações de uma nota. */
  contextualizarExecucao(idExecucao: string): Observable<ContextoExecucaoResponse> {
    return this.http.get<ContextoExecucaoResponse>(`${this.apiUrl}/execucoes/${idExecucao}/contexto`);
  }
}

/** Tira os filtros vazios; listas viram parâmetro repetido (`?idProcesso=a&idProcesso=b`). */
function semVazios(filtros: object): Record<string, string | string[]> {
  return Object.fromEntries(
    Object.entries(filtros).filter(
      ([, valor]) => valor !== undefined && valor !== null && valor !== '' && !(Array.isArray(valor) && !valor.length)
    )
  ) as Record<string, string | string[]>;
}
