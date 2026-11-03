import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PagedResponse } from '../models/shared.models';
import {
  CondicoesProgramacao,
  EventoProgramacao,
  GanttProgramacao,
  ObraAProgramar,
  PreviaProgramacao,
  ProgramacaoDetalhe,
  ProgramarRequest,
  ResultadoProgramacao,
  ResumoPlanejamentoObra,
  TipoEventoProgramacao
} from '../models/obras-programacao.models';
import { BaseService } from './base.service';

export interface FiltroGantt {
  inicio: string; // yyyy-MM-dd
  fim: string; // yyyy-MM-dd
  idBase?: string | null;
  idTipoEquipe?: string | null;
}

export interface FiltroHistorico {
  tipos?: TipoEventoProgramacao[];
  obra?: string | null;
  equipe?: string | null;
  usuario?: string | null;
  mudou?: 'EQUIPE' | 'PERIODO' | null;
  de?: string | null; // yyyy-MM-dd
  ate?: string | null; // yyyy-MM-dd
  q?: string | null;
  page: number;
  size: number;
  sort?: string | string[];
}

/** Programação das obras: prévia, comandos, Gantt e histórico. */
@Injectable({ providedIn: 'root' })
export class ObrasProgramacaoService extends BaseService {
  private readonly base = `${environment.apiUrl}/v1/obras`;

  previa(idObra: string, pedido: { idsItens?: string[]; inicio: string; fim: string; idProgramacao?: string }): Observable<PreviaProgramacao> {
    return this.http.post<PreviaProgramacao>(`${this.base}/${idObra}/programacoes/previa`, pedido);
  }

  programar(idObra: string, request: ProgramarRequest): Observable<ResultadoProgramacao> {
    return this.http.post<ResultadoProgramacao>(`${this.base}/${idObra}/programacoes`, request);
  }

  buscar(id: string): Observable<ProgramacaoDetalhe> {
    return this.http.get<ProgramacaoDetalhe>(`${this.base}/programacoes/${id}`);
  }

  editar(id: string, condicoes: CondicoesProgramacao, versao: number): Observable<ProgramacaoDetalhe> {
    return this.http.put<ProgramacaoDetalhe>(`${this.base}/programacoes/${id}`, { ...condicoes, versao });
  }

  reprogramar(id: string, pedido: { idEquipe: string; inicio: string; fim: string; observacao: string; versao: number }): Observable<ProgramacaoDetalhe> {
    return this.http.post<ProgramacaoDetalhe>(`${this.base}/programacoes/${id}/reprogramacao`, pedido);
  }

  cancelar(id: string, observacao: string, versao: number): Observable<ProgramacaoDetalhe> {
    return this.http.post<ProgramacaoDetalhe>(`${this.base}/programacoes/${id}/cancelamento`, { observacao, versao });
  }

  excluir(id: string, observacao: string | null, versao: number): Observable<void> {
    return this.http.post<void>(`${this.base}/programacoes/${id}/exclusao`, { observacao, versao });
  }

  /** Devolve `null` quando era o último item e a programação foi excluída. */
  retirarItem(id: string, idItem: string): Observable<ProgramacaoDetalhe | null> {
    return this.http.delete<ProgramacaoDetalhe | null>(`${this.base}/programacoes/${id}/itens/${idItem}`);
  }

  gantt(filtro: FiltroGantt): Observable<GanttProgramacao> {
    let params = new HttpParams().set('inicio', filtro.inicio).set('fim', filtro.fim);
    if (filtro.idBase) params = params.set('idBase', filtro.idBase);
    if (filtro.idTipoEquipe) params = params.set('idTipoEquipe', filtro.idTipoEquipe);
    return this.http.get<GanttProgramacao>(`${this.base}/programacoes/gantt`, { params });
  }

  obrasAProgramar(idBase?: string | null): Observable<ObraAProgramar[]> {
    const params = idBase ? new HttpParams().set('idBase', idBase) : undefined;
    return this.http.get<ObraAProgramar[]>(`${this.base}/programacoes/a-programar`, { params });
  }

  resumos(ids: readonly string[]): Observable<ResumoPlanejamentoObra[]> {
    return this.http.get<ResumoPlanejamentoObra[]>(`${this.base}/planejamento/resumos`, { params: new HttpParams().set('ids', ids.join(',')) });
  }

  listarDaObra(idObra: string): Observable<ProgramacaoDetalhe[]> {
    return this.http.get<ProgramacaoDetalhe[]>(`${this.base}/${idObra}/programacoes`);
  }

  eventosDa(idProgramacao: string): Observable<PagedResponse<EventoProgramacao>> {
    const params = new HttpParams().set('idProgramacao', idProgramacao).set('size', 50).set('sort', 'dataCriacao,desc');
    return this.http.get<PagedResponse<EventoProgramacao>>(`${this.base}/programacoes/historico`, { params });
  }

  historico(filtro: FiltroHistorico): Observable<PagedResponse<EventoProgramacao>> {
    let params = new HttpParams().set('page', filtro.page).set('size', filtro.size);
    for (const tipo of filtro.tipos ?? []) params = params.append('tipos', tipo);
    for (const campo of ['obra', 'equipe', 'usuario', 'mudou', 'de', 'ate', 'q'] as const) {
      const valor = filtro[campo];
      if (valor) params = params.set(campo, valor);
    }
    for (const regra of [filtro.sort ?? []].flat()) params = params.append('sort', regra);
    return this.http.get<PagedResponse<EventoProgramacao>>(`${this.base}/programacoes/historico`, { params });
  }
}
