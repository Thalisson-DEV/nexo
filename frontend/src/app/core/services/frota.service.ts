import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AcoplamentoImplemento,
  AcoplarImplementoRequest,
  Condutor,
  CondutorRequest,
  DocumentoFrota,
  DocumentoFrotaRequest,
  FiltroCondutores,
  FiltroDocumentosFrota,
  FiltroImplementos,
  FiltroLeiturasOdometro,
  FiltroVeiculosFrota,
  Implemento,
  ImplementoRequest,
  LeituraOdometro,
  OpcoesFrota,
  ParametrosFrota,
  RegistrarLeituraRequest,
  TipoImplemento,
  TipoImplementoRequest,
  TipoVeiculo,
  TipoVeiculoRequest,
  VeiculoFrotaDetalhe,
  VeiculoFrotaRequest,
  VeiculoFrotaResumo,
  VincularCondutorRequest,
  VinculoCondutor
} from '../models/frota.models';
import { PagedResponse } from '../models/shared.models';
import { comSelecao } from '../utils/http-params.util';

/** Paginação aceita pelos endpoints da frota (`sort` no formato `campo,asc|desc`). */
export interface PaginaFrota {
  page?: number;
  size?: number;
  sort?: string[];
}

/** Página grande para listas de opção (selects de veículo/implemento), não para listagens. */
export const PAGINA_OPCOES: PaginaFrota = { size: 500 };

/**
 * Cliente HTTP do módulo de frota. A frota é tela de escritório: leituras e escritas
 * vão direto ao servidor (sem fila offline); o app de campo terá seu próprio fluxo.
 */
@Injectable({ providedIn: 'root' })
export class FrotaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/v1/frota`;
  private opcoesCache$?: Observable<OpcoesFrota>;

  // ── Configuração ──────────────────────────────────────────────────────────
  opcoes(): Observable<OpcoesFrota> {
    this.opcoesCache$ ??= this.http.get<OpcoesFrota>(`${this.url}/opcoes`).pipe(shareReplay(1));
    return this.opcoesCache$;
  }

  parametros(): Observable<ParametrosFrota> {
    return this.http.get<ParametrosFrota>(`${this.url}/parametros`);
  }

  atualizarParametros(request: ParametrosFrota): Observable<ParametrosFrota> {
    return this.http.put<ParametrosFrota>(`${this.url}/parametros`, request);
  }

  tiposVeiculo(apenasAtivos = false): Observable<PagedResponse<TipoVeiculo>> {
    return this.http.get<PagedResponse<TipoVeiculo>>(`${this.url}/tipos-veiculo`, {
      params: this.params({ ativo: apenasAtivos ? true : null }, { ...PAGINA_OPCOES, sort: ['nome'] })
    });
  }

  salvarTipoVeiculo(request: TipoVeiculoRequest, id?: string | null): Observable<TipoVeiculo> {
    return id
      ? this.http.put<TipoVeiculo>(`${this.url}/tipos-veiculo/${id}`, request)
      : this.http.post<TipoVeiculo>(`${this.url}/tipos-veiculo`, request);
  }

  alternarTipoVeiculo(tipo: TipoVeiculo): Observable<void> {
    return tipo.ativo
      ? this.http.delete<void>(`${this.url}/tipos-veiculo/${tipo.id}`)
      : this.http.put<void>(`${this.url}/tipos-veiculo/${tipo.id}/ativar`, {});
  }

  tiposImplemento(apenasAtivos = false): Observable<PagedResponse<TipoImplemento>> {
    return this.http.get<PagedResponse<TipoImplemento>>(`${this.url}/tipos-implemento`, {
      params: this.params({ ativo: apenasAtivos ? true : null }, { ...PAGINA_OPCOES, sort: ['nome'] })
    });
  }

  salvarTipoImplemento(request: TipoImplementoRequest, id?: string | null): Observable<TipoImplemento> {
    return id
      ? this.http.put<TipoImplemento>(`${this.url}/tipos-implemento/${id}`, request)
      : this.http.post<TipoImplemento>(`${this.url}/tipos-implemento`, request);
  }

  alternarTipoImplemento(tipo: TipoImplemento): Observable<void> {
    return tipo.ativo
      ? this.http.delete<void>(`${this.url}/tipos-implemento/${tipo.id}`)
      : this.http.put<void>(`${this.url}/tipos-implemento/${tipo.id}/ativar`, {});
  }

  // ── Veículos ──────────────────────────────────────────────────────────────
  listarVeiculos(filtro: FiltroVeiculosFrota, pagina: PaginaFrota = {}): Observable<PagedResponse<VeiculoFrotaResumo>> {
    return this.http.get<PagedResponse<VeiculoFrotaResumo>>(`${this.url}/veiculos`, {
      params: this.params(filtro, { sort: ['placa'], ...pagina })
    });
  }

  detalharVeiculo(idVeiculo: string): Observable<VeiculoFrotaDetalhe> {
    return this.http.get<VeiculoFrotaDetalhe>(`${this.url}/veiculos/${idVeiculo}`);
  }

  salvarVeiculo(request: VeiculoFrotaRequest, idVeiculo?: string | null): Observable<VeiculoFrotaDetalhe> {
    return idVeiculo
      ? this.http.put<VeiculoFrotaDetalhe>(`${this.url}/veiculos/${idVeiculo}`, request)
      : this.http.post<VeiculoFrotaDetalhe>(`${this.url}/veiculos`, request);
  }

  historicoCondutoresVeiculo(idVeiculo: string): Observable<PagedResponse<VinculoCondutor>> {
    return this.http.get<PagedResponse<VinculoCondutor>>(`${this.url}/veiculos/${idVeiculo}/condutores`, {
      params: this.params({}, { size: 200 })
    });
  }

  vincularCondutor(idVeiculo: string, request: VincularCondutorRequest): Observable<VinculoCondutor> {
    return this.http.post<VinculoCondutor>(`${this.url}/veiculos/${idVeiculo}/condutores`, request);
  }

  encerrarVinculo(idVinculo: string, fim: string): Observable<VinculoCondutor> {
    return this.http.post<VinculoCondutor>(`${this.url}/vinculos-condutor/${idVinculo}/encerramento`, { fim });
  }

  historicoImplementosVeiculo(idVeiculo: string): Observable<AcoplamentoImplemento[]> {
    return this.http.get<AcoplamentoImplemento[]>(`${this.url}/veiculos/${idVeiculo}/implementos`);
  }

  leiturasVeiculo(idVeiculo: string): Observable<PagedResponse<LeituraOdometro>> {
    return this.http.get<PagedResponse<LeituraOdometro>>(`${this.url}/veiculos/${idVeiculo}/odometro`, {
      params: this.params({}, { size: 200 })
    });
  }

  registrarLeitura(idVeiculo: string, request: RegistrarLeituraRequest): Observable<LeituraOdometro> {
    return this.http.post<LeituraOdometro>(`${this.url}/veiculos/${idVeiculo}/odometro`, request);
  }

  // ── Odômetro ──────────────────────────────────────────────────────────────
  listarLeituras(filtro: FiltroLeiturasOdometro, pagina: PaginaFrota = {}): Observable<PagedResponse<LeituraOdometro>> {
    return this.http.get<PagedResponse<LeituraOdometro>>(`${this.url}/odometro`, {
      params: this.params(filtro, { sort: ['lidoEm,desc'], ...pagina })
    });
  }

  anularLeitura(id: string, justificativa: string): Observable<LeituraOdometro> {
    return this.http.post<LeituraOdometro>(`${this.url}/odometro/${id}/anulacao`, { justificativa });
  }

  confirmarLeitura(id: string, justificativa: string): Observable<LeituraOdometro> {
    return this.http.post<LeituraOdometro>(`${this.url}/odometro/${id}/confirmacao`, { justificativa });
  }

  // ── Implementos ───────────────────────────────────────────────────────────
  listarImplementos(filtro: FiltroImplementos, pagina: PaginaFrota = {}): Observable<PagedResponse<Implemento>> {
    return this.http.get<PagedResponse<Implemento>>(`${this.url}/implementos`, {
      params: this.params(filtro, { sort: ['identificacao'], ...pagina })
    });
  }

  salvarImplemento(request: ImplementoRequest, id?: string | null): Observable<Implemento> {
    return id
      ? this.http.put<Implemento>(`${this.url}/implementos/${id}`, request)
      : this.http.post<Implemento>(`${this.url}/implementos`, request);
  }

  alternarImplemento(implemento: Implemento): Observable<void> {
    return implemento.ativo
      ? this.http.delete<void>(`${this.url}/implementos/${implemento.id}`)
      : this.http.put<void>(`${this.url}/implementos/${implemento.id}/ativar`, {});
  }

  acoplarImplemento(id: string, request: AcoplarImplementoRequest): Observable<AcoplamentoImplemento> {
    return this.http.post<AcoplamentoImplemento>(`${this.url}/implementos/${id}/acoplamentos`, request);
  }

  desacoplarImplemento(id: string, fim: string): Observable<AcoplamentoImplemento> {
    return this.http.post<AcoplamentoImplemento>(`${this.url}/implementos/${id}/desacoplamento`, { fim });
  }

  historicoImplemento(id: string): Observable<AcoplamentoImplemento[]> {
    return this.http.get<AcoplamentoImplemento[]>(`${this.url}/implementos/${id}/acoplamentos`);
  }

  // ── Condutores ────────────────────────────────────────────────────────────
  listarCondutores(filtro: FiltroCondutores, pagina: PaginaFrota = {}): Observable<PagedResponse<Condutor>> {
    return this.http.get<PagedResponse<Condutor>>(`${this.url}/condutores`, {
      params: this.params(filtro, { sort: ['cnhValidade'], ...pagina })
    });
  }

  registrarCnh(idColaborador: string, request: CondutorRequest): Observable<Condutor> {
    return this.http.put<Condutor>(`${this.url}/condutores/${idColaborador}`, request);
  }

  desativarCondutor(idColaborador: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/condutores/${idColaborador}`);
  }

  historicoVeiculosCondutor(idColaborador: string): Observable<PagedResponse<VinculoCondutor>> {
    return this.http.get<PagedResponse<VinculoCondutor>>(`${this.url}/condutores/${idColaborador}/veiculos`, {
      params: this.params({}, { size: 200 })
    });
  }

  // ── Documentos ────────────────────────────────────────────────────────────
  listarDocumentos(filtro: FiltroDocumentosFrota, pagina: PaginaFrota = {}): Observable<PagedResponse<DocumentoFrota>> {
    return this.http.get<PagedResponse<DocumentoFrota>>(`${this.url}/documentos`, {
      params: this.params(filtro, { sort: ['dataVencimento'], ...pagina })
    });
  }

  criarDocumento(request: DocumentoFrotaRequest, arquivo?: File | null): Observable<DocumentoFrota> {
    const corpo = new FormData();
    corpo.append('dados', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    if (arquivo) corpo.append('arquivo', arquivo, arquivo.name);
    return this.http.post<DocumentoFrota>(`${this.url}/documentos`, corpo);
  }

  atualizarDocumento(id: string, request: DocumentoFrotaRequest): Observable<DocumentoFrota> {
    return this.http.put<DocumentoFrota>(`${this.url}/documentos/${id}`, request);
  }

  anexarArquivoDocumento(id: string, arquivo: File): Observable<DocumentoFrota> {
    const corpo = new FormData();
    corpo.append('arquivo', arquivo, arquivo.name);
    return this.http.post<DocumentoFrota>(`${this.url}/documentos/${id}/arquivo`, corpo);
  }

  baixarArquivoDocumento(id: string): Observable<Blob> {
    return this.http.get(`${this.url}/documentos/${id}/arquivo`, { responseType: 'blob' });
  }

  excluirDocumento(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/documentos/${id}`);
  }

  private params(filtro: object, pagina: PaginaFrota): HttpParams {
    let params = new HttpParams();
    for (const [chave, valor] of Object.entries(filtro)) {
      if (Array.isArray(valor)) {
        params = comSelecao(params, chave, valor);
      } else if (valor !== null && valor !== undefined && valor !== '') {
        params = params.set(chave, String(valor));
      }
    }
    if (pagina.page !== undefined) params = params.set('page', pagina.page);
    if (pagina.size !== undefined) params = params.set('size', pagina.size);
    for (const ordem of pagina.sort ?? []) params = params.append('sort', ordem);
    return params;
  }
}
