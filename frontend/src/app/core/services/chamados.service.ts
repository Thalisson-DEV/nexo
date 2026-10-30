import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AtualizarAtendimentoRequest,
  Chamado,
  ChamadoFiltros,
  CriarChamadoRequest,
  EquipeChamado,
  EquipeChamadoFiltros,
  EquipeChamadoRequest,
  EvidenciaChamado,
  InteracaoChamado,
  InteracaoChamadoRequest,
  PaginaChamados,
  PaginaEquipesChamado,
  PaginaTopicosChamado,
  TopicoChamado,
  TopicoChamadoFiltros,
  TopicoChamadoRequest
} from '../models/chamado.models';
import {
  DashboardChamadosFilter,
  DashboardChamadosResponse
} from '../models/chamado-dashboard.models';
import { toHttpParams } from '../utils/http-params.util';

@Injectable({ providedIn: 'root' })
export class ChamadosService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/v1/chamados`;

  obterDashboard(filtros: DashboardChamadosFilter): Observable<DashboardChamadosResponse> {
    return this.http.get<DashboardChamadosResponse>(`${this.apiUrl}/dashboards/gerencial`, {
      params: toHttpParams(filtros as Record<string, unknown>)
    });
  }

  listarMeus(filtros: ChamadoFiltros): Observable<PaginaChamados> {
    return this.http.get<PaginaChamados>(`${this.apiUrl}/meus`, {
      params: this.params(filtros)
    });
  }

  listarFila(filtros: ChamadoFiltros): Observable<PaginaChamados> {
    return this.http.get<PaginaChamados>(this.apiUrl, { params: this.params(filtros) });
  }

  buscar(id: string): Observable<Chamado> {
    return this.http.get<Chamado>(`${this.apiUrl}/${id}`);
  }

  criar(request: CriarChamadoRequest): Observable<Chamado> {
    return this.http.post<Chamado>(this.apiUrl, request);
  }

  listarInteracoes(id: string): Observable<InteracaoChamado[]> {
    return this.http.get<InteracaoChamado[]>(`${this.apiUrl}/${id}/interacoes`);
  }

  responderComoSolicitante(id: string, mensagem: string): Observable<InteracaoChamado> {
    return this.http.post<InteracaoChamado>(`${this.apiUrl}/meus/${id}/interacoes`, {
      mensagem,
      interno: false
    });
  }

  adicionarInteracao(
    id: string,
    request: InteracaoChamadoRequest
  ): Observable<InteracaoChamado> {
    return this.http.post<InteracaoChamado>(`${this.apiUrl}/${id}/interacoes`, request);
  }

  atualizarAtendimento(id: string, request: AtualizarAtendimentoRequest): Observable<Chamado> {
    return this.http.patch<Chamado>(`${this.apiUrl}/${id}/atendimento`, request);
  }

  cancelarMeu(id: string): Observable<Chamado> {
    return this.http.patch<Chamado>(`${this.apiUrl}/meus/${id}/cancelamento`, null);
  }

  reabrirMeu(id: string, motivo: string): Observable<Chamado> {
    return this.http.patch<Chamado>(`${this.apiUrl}/meus/${id}/reabertura`, { motivo });
  }

  anexar(id: string, arquivo: File): Observable<EvidenciaChamado> {
    const formData = new FormData();
    formData.append('arquivo', arquivo, arquivo.name);
    return this.http.post<EvidenciaChamado>(`${this.apiUrl}/${id}/anexos`, formData);
  }

  baixarAnexo(id: string, anexoId: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/anexos/${anexoId}/download`, {
      responseType: 'blob'
    });
  }

  listarTopicosAtivos(): Observable<TopicoChamado[]> {
    return this.http.get<TopicoChamado[]>(`${this.apiUrl}/topicos`);
  }

  listarTopicos(filtros: TopicoChamadoFiltros): Observable<PaginaTopicosChamado> {
    return this.http.get<PaginaTopicosChamado>(`${this.apiUrl}/topicos/administracao`, {
      params: this.params(filtros)
    });
  }

  criarTopico(request: TopicoChamadoRequest): Observable<TopicoChamado> {
    return this.http.post<TopicoChamado>(`${this.apiUrl}/topicos`, request);
  }

  atualizarTopico(id: string, request: TopicoChamadoRequest): Observable<TopicoChamado> {
    return this.http.put<TopicoChamado>(`${this.apiUrl}/topicos/${id}`, request);
  }

  alternarTopico(id: string, ativar: boolean): Observable<TopicoChamado> {
    const acao = ativar ? 'ativacao' : 'desativacao';
    return this.http.patch<TopicoChamado>(`${this.apiUrl}/topicos/${id}/${acao}`, null);
  }

  listarEquipes(filtros: EquipeChamadoFiltros): Observable<PaginaEquipesChamado> {
    return this.http.get<PaginaEquipesChamado>(`${this.apiUrl}/equipes`, {
      params: this.params(filtros)
    });
  }

  buscarEquipe(id: string): Observable<EquipeChamado> {
    return this.http.get<EquipeChamado>(`${this.apiUrl}/equipes/${id}`);
  }

  criarEquipe(request: EquipeChamadoRequest): Observable<EquipeChamado> {
    return this.http.post<EquipeChamado>(`${this.apiUrl}/equipes`, request);
  }

  atualizarEquipe(id: string, request: EquipeChamadoRequest): Observable<EquipeChamado> {
    return this.http.put<EquipeChamado>(`${this.apiUrl}/equipes/${id}`, request);
  }

  alternarEquipe(id: string, ativar: boolean): Observable<EquipeChamado> {
    const acao = ativar ? 'ativacao' : 'desativacao';
    return this.http.patch<EquipeChamado>(`${this.apiUrl}/equipes/${id}/${acao}`, null);
  }

  private params(source: object): HttpParams {
    let params = new HttpParams();
    Object.entries(source).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return params;
  }
}
