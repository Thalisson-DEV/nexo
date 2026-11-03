import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AnaliseOrcadoFaturado,
  CicloMedicao,
  Fechamento,
  FiltroMedicoes,
  GerarMedicaoRequest,
  LinhaPreFechamento,
  MedicaoDaObra,
  MedicaoDetalhe,
  MedicaoResumo,
  ObraAMedir,
  OpcoesMedicoes,
  PedidoRelatorioDetalhado,
  PreFechamento
} from '../models/obras-medicoes.models';
import { PagedResponse } from '../models/shared.models';
import { BaseService } from './base.service';

/** Pré-fechamento do levantamento, medições (parcial e por ciclo), estorno e fechamento de obra. */
@Injectable({ providedIn: 'root' })
export class ObrasMedicoesService extends BaseService {
  private readonly base = `${environment.apiUrl}/v1/obras`;

  preFechamento(filtro: { q?: string; idBase?: string; apenasPendentes: boolean }): Observable<PreFechamento> {
    let params = new HttpParams().set('apenasPendentes', filtro.apenasPendentes);
    if (filtro.q) params = params.set('q', filtro.q);
    if (filtro.idBase) params = params.set('idBase', filtro.idBase);
    return this.http.get<PreFechamento>(`${this.base}/pre-fechamento`, { params });
  }

  concluirLevantamento(idObra: string): Observable<LinhaPreFechamento> {
    return this.http.post<LinhaPreFechamento>(`${this.base}/${idObra}/levantamento/conclusao`, {});
  }

  reabrirLevantamento(idObra: string): Observable<LinhaPreFechamento> {
    return this.http.post<LinhaPreFechamento>(`${this.base}/${idObra}/levantamento/reabertura`, {});
  }

  obrasAMedir(): Observable<ObraAMedir[]> {
    return this.http.get<ObraAMedir[]>(`${this.base}/medicoes/a-medir`);
  }

  daObra(idObra: string): Observable<MedicaoDaObra> {
    return this.http.get<MedicaoDaObra>(`${this.base}/${idObra}/medicao`);
  }

  ciclo(idObra: string, competencia: string): Observable<CicloMedicao> {
    return this.http.get<CicloMedicao>(`${this.base}/${idObra}/medicao/ciclo`, { params: { competencia } });
  }

  gerar(idObra: string, pedido: GerarMedicaoRequest): Observable<MedicaoDetalhe> {
    return this.http.post<MedicaoDetalhe>(`${this.base}/${idObra}/medicoes`, pedido);
  }

  listar(filtro: FiltroMedicoes): Observable<PagedResponse<MedicaoResumo>> {
    let params = new HttpParams().set('page', filtro.page).set('size', filtro.size);
    for (const campo of ['idObra', 'competenciaDe', 'competenciaAte', 'usuario', 'geradaDe', 'geradaAte', 'q'] as const) {
      const valor = filtro[campo];
      if (valor) params = params.set(campo, valor);
    }
    for (const id of filtro.idsBases ?? []) params = params.append('idsBases', id);
    for (const id of filtro.idsMunicipios ?? []) params = params.append('idsMunicipios', id);
    for (const id of filtro.idsSupervisores ?? []) params = params.append('idsSupervisores', id);
    for (const tipo of filtro.tipos ?? []) params = params.append('tipos', tipo);
    for (const status of filtro.status ?? []) params = params.append('status', status);
    for (const ordem of filtro.sort ?? []) params = params.append('sort', ordem);
    return this.http.get<PagedResponse<MedicaoResumo>>(`${this.base}/medicoes`, { params });
  }

  opcoes(): Observable<OpcoesMedicoes> {
    return this.http.get<OpcoesMedicoes>(`${this.base}/medicoes/opcoes`);
  }

  detalhar(id: string): Observable<MedicaoDetalhe> {
    return this.http.get<MedicaoDetalhe>(`${this.base}/medicoes/${id}`);
  }

  estornar(id: string, justificativa: string): Observable<MedicaoDetalhe> {
    return this.http.post<MedicaoDetalhe>(`${this.base}/medicoes/${id}/estorno`, { justificativa });
  }

  fechamento(codigos: string): Observable<Fechamento> {
    return this.http.post<Fechamento>(`${this.base}/fechamento`, { codigos });
  }

  exportarFechamento(codigos: string): Observable<Blob> {
    return this.http.post(`${this.base}/fechamento/exportacao`, { codigos }, { responseType: 'blob' });
  }

  modeloOrcadoFaturado(): Observable<Blob> {
    return this.http.get(`${this.base}/medicoes/relatorio-detalhado/modelo`, { responseType: 'blob' });
  }

  analisarOrcadoFaturado(arquivo: File): Observable<AnaliseOrcadoFaturado> {
    const corpo = new FormData();
    corpo.append('arquivo', arquivo);
    return this.http.post<AnaliseOrcadoFaturado>(`${this.base}/medicoes/relatorio-detalhado/analise`, corpo);
  }

  /** A planilha vai de novo: nada dela fica gravado entre a análise e a geração. */
  gerarRelatorioDetalhado(arquivo: File, pedido: PedidoRelatorioDetalhado): Observable<Blob> {
    const corpo = new FormData();
    corpo.append('arquivo', arquivo);
    for (const id of pedido.idsObras) corpo.append('idsObras', id);
    corpo.append('dataCorte', pedido.dataCorte);
    corpo.append('incluirFotos', String(pedido.incluirFotos));
    if (pedido.fiscal.trim()) corpo.append('fiscal', pedido.fiscal.trim());
    if (pedido.responsavel.trim()) corpo.append('responsavel', pedido.responsavel.trim());
    return this.http.post(`${this.base}/medicoes/relatorio-detalhado`, corpo, { responseType: 'blob' });
  }
}
