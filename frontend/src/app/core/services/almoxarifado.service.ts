import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ExportacaoPainelSolicitacoes,
  PainelSolicitacoesFiltros,
  PainelSolicitacoesResponse,
} from '../models/painel-solicitacoes.models';
import {
  KanbanSolicitacaoTecnicaApiResponse,
  KanbanSolicitacaoTecnicaResponse,
  SolicitacaoMaterialFilter,
  SolicitacaoMaterialListaItemResponse,
  SolicitacaoMaterialSummaryResponse,
  StatusSeparacao,
} from '../models/solicitacao-material-tecnica.models';
import { HEADER_IDEMPOTENCIA, novaChaveIdempotencia } from '../utils/idempotencia.util';
import { toHttpParams } from '../utils/http-params.util';
import {
  ConsumoAnteriorDetalheResponse,
  ConsumoAnteriorSolicitacaoResponse,
  MaterialRequest,
  MaterialResponse,
  PageResponseMaterialResponse,
  ImportacaoMaterialResponse,
  ConfirmarImportacaoMaterialRequest,
  TipoEstoqueResponse,
  TipoEstoqueRequest,
  PageResponseTipoEstoqueResponse,
  ImportacaoTipoEstoqueResponse,
  ConfirmarImportacaoTipoEstoqueRequest,
  AvaliacaoMaterialResponse,
  AvaliacaoMaterialRequest,
  PageResponseAvaliacaoMaterialResponse,
  ImportacaoAvaliacaoMaterialResponse,
  ConfirmarImportacaoAvaliacaoMaterialRequest,
  CentroInvestimentoResponse,
  CentroInvestimentoRequest,
  PageResponseCentroInvestimentoResponse,
  ImportacaoCentroInvestimentoResponse,
  ConfirmarImportacaoCentroInvestimentoRequest,
  SolicitacaoRequest,
  SolicitacaoResponse,
  SolicitacaoSummaryResponse,
  PageResponseSolicitacaoSummaryResponse,
  MotivoRejeicaoResponse,
  MotivoRejeicaoRequest,
  PageResponseMotivoRejeicaoResponse,
  RejeitarSolicitacaoRequest,
  MotivoPendenciaResponse,
  MotivoPendenciaRequest,
  RegistrarPendenciaRequest,
  AprovarSolicitacaoRequest,
  EntregaSolicitacaoRequest,
  EstoqueResponse,
  MovimentacaoBulkRequest,
  MovimentacaoEstoqueFilter,
  MovimentacaoEstoqueResponse,
  MovimentacaoOperacaoResponse,
  MovimentacaoOperacaoSummaryResponse,
  PageResponseMovimentacaoOperacaoSummaryResponse,
  PageResponseSaldoResponse,
  SaldoResponse,
  PageResponseSaldoSerieResponse,
  SaldoSerieResponse,
  DashboardSolicitacaoResponse,
  EstoqueRequest,
  ConfirmarImportacaoEstoqueRequest,
  ImportacaoEstoqueResponse,
  EstornoParcialRequest,
  SolicitacaoComercialImportacaoDTO,
  ImportacaoSolicitacaoComercialResponse,
  ConfirmarImportacaoSolicitacaoComercialRequest,
  DevolucaoComercialRequest,
  ImportacaoMovimentacaoResponse,
  ConfirmarImportacaoMovimentacaoRequest,
} from '../models/almoxarifado.models';
import { PagedResponse } from '../models/shared.models';
import { UsuarioResponse } from '../models/iam.models';
import { LookupCacheService } from '../lookup/services/lookup-cache.service';

@Injectable({
  providedIn: 'root',
})
export class AlmoxarifadoService {
  private http = inject(HttpClient);
  private readonly lookupCache = inject(LookupCacheService);
  private apiUrl = `${environment.apiUrl}/v1`;

  // --- Avaliações de Material ---

  listarAvaliacoes(params: any): Observable<PageResponseAvaliacaoMaterialResponse> {
    let httpParams = new HttpParams();
    Object.keys(params).forEach((key) => {
      if (params[key] !== null && params[key] !== undefined) {
        httpParams = httpParams.set(key, params[key]);
      }
    });
    return this.http.get<PageResponseAvaliacaoMaterialResponse>(
      `${this.apiUrl}/avaliacoes-material`,
      { params: httpParams },
    );
  }

  buscarAvaliacaoPorId(id: string): Observable<AvaliacaoMaterialResponse> {
    return this.http.get<AvaliacaoMaterialResponse>(`${this.apiUrl}/avaliacoes-material/${id}`);
  }

  criarAvaliacao(request: AvaliacaoMaterialRequest): Observable<AvaliacaoMaterialResponse> {
    return this.http.post<AvaliacaoMaterialResponse>(`${this.apiUrl}/avaliacoes-material`, request);
  }

  atualizarAvaliacao(
    id: string,
    request: AvaliacaoMaterialRequest,
  ): Observable<AvaliacaoMaterialResponse> {
    return this.http.put<AvaliacaoMaterialResponse>(
      `${this.apiUrl}/avaliacoes-material/${id}`,
      request,
    );
  }

  desativarAvaliacao(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/avaliacoes-material/${id}`);
  }

  ativarAvaliacao(id: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/avaliacoes-material/${id}/ativar`, {});
  }

  importarAvaliacoes(file: File): Observable<ImportacaoAvaliacaoMaterialResponse> {
    const formData = new FormData();
    formData.append('arquivo', file);
    return this.http.post<ImportacaoAvaliacaoMaterialResponse>(
      `${this.apiUrl}/avaliacoes-material/importar`,
      formData,
    );
  }

  confirmarImportacaoAvaliacoes(
    request: ConfirmarImportacaoAvaliacaoMaterialRequest,
  ): Observable<ImportacaoAvaliacaoMaterialResponse> {
    return this.http.post<ImportacaoAvaliacaoMaterialResponse>(
      `${this.apiUrl}/avaliacoes-material/confirmar-importacao`,
      request,
    );
  }

  // --- Centros de Investimento ---

  listarCentrosInvestimento(params: any): Observable<PageResponseCentroInvestimentoResponse> {
    let httpParams = new HttpParams();
    Object.keys(params).forEach((key) => {
      if (params[key] !== null && params[key] !== undefined) {
        httpParams = httpParams.set(key, params[key]);
      }
    });
    return this.http.get<PageResponseCentroInvestimentoResponse>(
      `${this.apiUrl}/centros-investimento`,
      { params: httpParams },
    );
  }

  buscarCentroInvestimentoPorId(id: string): Observable<CentroInvestimentoResponse> {
    return this.http.get<CentroInvestimentoResponse>(`${this.apiUrl}/centros-investimento/${id}`);
  }

  criarCentroInvestimento(
    request: CentroInvestimentoRequest,
  ): Observable<CentroInvestimentoResponse> {
    return this.http.post<CentroInvestimentoResponse>(
      `${this.apiUrl}/centros-investimento`,
      request,
    );
  }

  atualizarCentroInvestimento(
    id: string,
    request: CentroInvestimentoRequest,
  ): Observable<CentroInvestimentoResponse> {
    return this.http.put<CentroInvestimentoResponse>(
      `${this.apiUrl}/centros-investimento/${id}`,
      request,
    );
  }

  desativarCentroInvestimento(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/centros-investimento/${id}`);
  }

  ativarCentroInvestimento(id: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/centros-investimento/${id}/ativar`, {});
  }

  importarCentrosInvestimento(file: File): Observable<ImportacaoCentroInvestimentoResponse> {
    const formData = new FormData();
    formData.append('arquivo', file);
    return this.http.post<ImportacaoCentroInvestimentoResponse>(
      `${this.apiUrl}/centros-investimento/importar`,
      formData,
    );
  }

  confirmarImportacaoCentrosInvestimento(
    request: ConfirmarImportacaoCentroInvestimentoRequest,
  ): Observable<ImportacaoCentroInvestimentoResponse> {
    return this.http.post<ImportacaoCentroInvestimentoResponse>(
      `${this.apiUrl}/centros-investimento/confirmar-importacao`,
      request,
    );
  }

  // --- Materiais ---

  listarMateriais(params: any): Observable<PageResponseMaterialResponse> {
    let httpParams = new HttpParams();
    Object.keys(params).forEach((key) => {
      if (params[key] !== null && params[key] !== undefined) {
        httpParams = httpParams.set(key, params[key]);
      }
    });
    return this.http.get<PageResponseMaterialResponse>(`${this.apiUrl}/materiais`, {
      params: httpParams,
    });
  }

  buscarMaterialPorId(id: string): Observable<MaterialResponse> {
    return this.http.get<MaterialResponse>(`${this.apiUrl}/materiais/${id}`);
  }

  criarMaterial(request: MaterialRequest): Observable<MaterialResponse> {
    return this.http
      .post<MaterialResponse>(`${this.apiUrl}/materiais`, request)
      .pipe(tap(() => this.lookupCache.clear('materials')));
  }

  atualizarMaterial(id: string, request: MaterialRequest): Observable<MaterialResponse> {
    return this.http
      .put<MaterialResponse>(`${this.apiUrl}/materiais/${id}`, request)
      .pipe(tap(() => this.lookupCache.clear('materials')));
  }

  desativarMaterial(id: string): Observable<void> {
    return this.http
      .delete<void>(`${this.apiUrl}/materiais/${id}`)
      .pipe(tap(() => this.lookupCache.clear('materials')));
  }

  ativarMaterial(id: string): Observable<void> {
    return this.http
      .patch<void>(`${this.apiUrl}/materiais/${id}/ativar`, {})
      .pipe(tap(() => this.lookupCache.clear('materials')));
  }

  importarMateriais(file: File): Observable<ImportacaoMaterialResponse> {
    const formData = new FormData();
    formData.append('arquivo', file);
    return this.http.post<ImportacaoMaterialResponse>(
      `${this.apiUrl}/materiais/importar`,
      formData,
    );
  }

  confirmarImportacaoMateriais(
    request: ConfirmarImportacaoMaterialRequest,
  ): Observable<ImportacaoMaterialResponse> {
    return this.http
      .post<ImportacaoMaterialResponse>(`${this.apiUrl}/materiais/confirmar-importacao`, request)
      .pipe(tap(() => this.lookupCache.clear('materials')));
  }

  // --- Tipos de Estoque ---

  listarTiposEstoque(params: any): Observable<PageResponseTipoEstoqueResponse> {
    let httpParams = new HttpParams();
    Object.keys(params).forEach((key) => {
      if (params[key] !== null && params[key] !== undefined) {
        httpParams = httpParams.set(key, params[key]);
      }
    });
    return this.http.get<PageResponseTipoEstoqueResponse>(`${this.apiUrl}/tipos-estoque`, {
      params: httpParams,
    });
  }

  buscarTipoEstoquePorId(id: string): Observable<TipoEstoqueResponse> {
    return this.http.get<TipoEstoqueResponse>(`${this.apiUrl}/tipos-estoque/${id}`);
  }

  criarTipoEstoque(request: TipoEstoqueRequest): Observable<TipoEstoqueResponse> {
    return this.http.post<TipoEstoqueResponse>(`${this.apiUrl}/tipos-estoque`, request);
  }

  atualizarTipoEstoque(id: string, request: TipoEstoqueRequest): Observable<TipoEstoqueResponse> {
    return this.http.put<TipoEstoqueResponse>(`${this.apiUrl}/tipos-estoque/${id}`, request);
  }

  desativarTipoEstoque(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/tipos-estoque/${id}`);
  }

  ativarTipoEstoque(id: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/tipos-estoque/${id}/ativar`, {});
  }

  importarTiposEstoque(file: File): Observable<ImportacaoTipoEstoqueResponse> {
    const formData = new FormData();
    formData.append('arquivo', file);
    return this.http.post<ImportacaoTipoEstoqueResponse>(
      `${this.apiUrl}/tipos-estoque/importar`,
      formData,
    );
  }

  confirmarImportacaoTiposEstoque(
    request: ConfirmarImportacaoTipoEstoqueRequest,
  ): Observable<ImportacaoTipoEstoqueResponse> {
    return this.http.post<ImportacaoTipoEstoqueResponse>(
      `${this.apiUrl}/tipos-estoque/confirmar-importacao`,
      request,
    );
  }

  // --- Motivos de Rejeição ---

  listarMotivosRejeicao(params: any): Observable<PageResponseMotivoRejeicaoResponse> {
    let httpParams = new HttpParams();
    Object.keys(params).forEach((key) => {
      if (params[key] !== null && params[key] !== undefined) {
        httpParams = httpParams.set(key, params[key]);
      }
    });
    return this.http.get<PageResponseMotivoRejeicaoResponse>(`${this.apiUrl}/motivos-rejeicao`, {
      params: httpParams,
    });
  }

  buscarMotivoRejeicaoPorId(id: string): Observable<MotivoRejeicaoResponse> {
    return this.http.get<MotivoRejeicaoResponse>(`${this.apiUrl}/motivos-rejeicao/${id}`);
  }

  criarMotivoRejeicao(request: MotivoRejeicaoRequest): Observable<MotivoRejeicaoResponse> {
    return this.http.post<MotivoRejeicaoResponse>(`${this.apiUrl}/motivos-rejeicao`, request);
  }

  atualizarMotivoRejeicao(
    id: string,
    request: MotivoRejeicaoRequest,
  ): Observable<MotivoRejeicaoResponse> {
    return this.http.put<MotivoRejeicaoResponse>(`${this.apiUrl}/motivos-rejeicao/${id}`, request);
  }

  desativarMotivoRejeicao(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/motivos-rejeicao/${id}`);
  }

  ativarMotivoRejeicao(id: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/motivos-rejeicao/${id}/ativar`, {});
  }

  // --- Estoques ---

  listarEstoques(params: any): Observable<PagedResponse<EstoqueResponse>> {
    let httpParams = new HttpParams();
    Object.keys(params).forEach((key) => {
      if (params[key] !== null && params[key] !== undefined) {
        httpParams = httpParams.set(key, params[key]);
      }
    });
    return this.http.get<PagedResponse<EstoqueResponse>>(`${this.apiUrl}/estoques`, {
      params: httpParams,
    });
  }

  buscarEstoquePorId(id: string): Observable<EstoqueResponse> {
    return this.http.get<EstoqueResponse>(`${this.apiUrl}/estoques/${id}`);
  }

  criarEstoque(request: EstoqueRequest): Observable<EstoqueResponse> {
    return this.http.post<EstoqueResponse>(`${this.apiUrl}/estoques`, request);
  }

  atualizarEstoque(id: string, request: EstoqueRequest): Observable<EstoqueResponse> {
    return this.http.put<EstoqueResponse>(`${this.apiUrl}/estoques/${id}`, request);
  }

  desativarEstoque(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/estoques/${id}`);
  }

  ativarEstoque(id: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/estoques/${id}/ativar`, {});
  }

  importarEstoques(file: File): Observable<ImportacaoEstoqueResponse> {
    const formData = new FormData();
    formData.append('arquivo', file);
    return this.http.post<ImportacaoEstoqueResponse>(`${this.apiUrl}/estoques/importar`, formData);
  }

  confirmarImportacaoEstoques(
    request: ConfirmarImportacaoEstoqueRequest,
  ): Observable<ImportacaoEstoqueResponse> {
    return this.http.post<ImportacaoEstoqueResponse>(
      `${this.apiUrl}/estoques/confirmar-importacao`,
      request,
    );
  }

  // --- Solicitações de Material ---

  listarSolicitacoes(params: any): Observable<PageResponseSolicitacaoSummaryResponse> {
    let httpParams = new HttpParams();
    Object.keys(params).forEach((key) => {
      if (params[key] !== null && params[key] !== undefined) {
        if (params[key] instanceof Array) {
          params[key].forEach((val: string) => {
            httpParams = httpParams.append(key, val);
          });
        } else {
          httpParams = httpParams.set(key, params[key]);
        }
      }
    });
    return this.http.get<PageResponseSolicitacaoSummaryResponse>(
      `${this.apiUrl}/solicitacoes-material`,
      { params: httpParams },
    );
  }

  buscarSolicitacaoPorId(id: string): Observable<SolicitacaoResponse> {
    return this.http.get<SolicitacaoResponse>(`${this.apiUrl}/solicitacoes-material/${id}`);
  }

  /** O que o estoque pegou na última solicitação de cada material e quanto usou desde então. */
  buscarConsumoAnterior(id: string): Observable<ConsumoAnteriorSolicitacaoResponse> {
    return this.http.get<ConsumoAnteriorSolicitacaoResponse>(`${this.apiUrl}/solicitacoes-material/${id}/consumo-anterior`);
  }

  /** As movimentações que explicam o consumo de um material desde a última solicitação. */
  buscarConsumoAnteriorDetalhe(id: string, idMaterial: string): Observable<ConsumoAnteriorDetalheResponse> {
    return this.http.get<ConsumoAnteriorDetalheResponse>(`${this.apiUrl}/solicitacoes-material/${id}/consumo-anterior/${idMaterial}`);
  }

  criarSolicitacao(request: SolicitacaoRequest): Observable<string> {
    return this.http.post<string>(`${this.apiUrl}/solicitacoes-material`, request);
  }

  criarSolicitacaoDireta(request: SolicitacaoRequest): Observable<string> {
    return this.http.post<string>(`${this.apiUrl}/solicitacoes-material/direta`, request);
  }

  aprovarSolicitacao(id: string, request: AprovarSolicitacaoRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/solicitacoes-material/${id}/aprovar`, request);
  }

  rejeitarSolicitacao(id: string, request: RejeitarSolicitacaoRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/solicitacoes-material/${id}/rejeitar`, request);
  }

  registrarEntrega(id: string, request: EntregaSolicitacaoRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/solicitacoes-material/${id}/entregar`, request);
  }

  buscarAssinaturaSolicitacao(id: string, parte: 'ALMOX' | 'EQUIPE'): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/solicitacoes-material/${id}/assinaturas/${parte}`, {
      responseType: 'blob',
    });
  }

  estornarSolicitacao(id: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/solicitacoes-material/${id}/estornar`, {});
  }

  estornarParcial(id: string, request: EstornoParcialRequest): Observable<void> {
    return this.http.patch<void>(
      `${this.apiUrl}/solicitacoes-material/${id}/estorno-parcial`,
      request,
    );
  }

  obterKanban(
    filtros: Partial<SolicitacaoMaterialFilter> = {},
  ): Observable<KanbanSolicitacaoTecnicaResponse> {
    return this.http
      .get<KanbanSolicitacaoTecnicaApiResponse>(`${this.apiUrl}/solicitacoes-material/kanban`, {
        params: toHttpParams(filtros as Record<string, unknown>),
      })
      .pipe(
        map((response) => ({
          colunas: response.colunas.map((col) => ({
            etapa: col.etapa,
            label: col.label,
            icone: col.icone,
            metricas: col.metricas,
            subColunas: (col.subColunas ?? []).map((sub) => ({
              subEtapa: sub.subEtapa,
              label: sub.label,
              corPadrao: sub.corPadrao,
              total: sub.total,
              itens: (sub.itens ?? []).map((item) => this.normalizarResumoComercial(item)),
            })),
          })),
        })),
      );
  }

  iniciarSeparacao(
    idSolicitacao: string,
    request: { versaoEsperada: number },
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<StatusSeparacao> {
    return this.http.post<StatusSeparacao>(
      `${this.apiUrl}/solicitacoes-material/${idSolicitacao}/separacao/iniciar`,
      request,
      { headers: { [HEADER_IDEMPOTENCIA]: chaveIdempotencia } },
    );
  }

  concluirSeparacao(
    idSolicitacao: string,
    request: { versaoEsperada: number },
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<StatusSeparacao> {
    return this.http.post<StatusSeparacao>(
      `${this.apiUrl}/solicitacoes-material/${idSolicitacao}/separacao/concluir`,
      request,
      { headers: { [HEADER_IDEMPOTENCIA]: chaveIdempotencia } },
    );
  }

  pularSeparacao(
    idSolicitacao: string,
    request: { versaoEsperada: number },
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<StatusSeparacao> {
    return this.http.post<StatusSeparacao>(
      `${this.apiUrl}/solicitacoes-material/${idSolicitacao}/separacao/pular`,
      request,
      { headers: { [HEADER_IDEMPOTENCIA]: chaveIdempotencia } },
    );
  }

  private normalizarResumoComercial(
    item: SolicitacaoMaterialListaItemResponse,
  ): SolicitacaoMaterialSummaryResponse {
    const tecnico = item.tecnico;
    const totais = tecnico?.totais;
    const reserva = tecnico?.reserva;
    return {
      id: item.id,
      possuiPendencia: item.possuiPendencia ?? false,
      tipo: tecnico?.tipoTecnico ?? null,
      tipoObra: tecnico?.tipoObra ?? null,
      idObra: tecnico?.idObra ?? null,
      ocorrencia: tecnico?.ocorrencia ?? null,
      contexto: item.contexto,
      dataSolicitacao: item.dataSolicitacao,
      dataEntrega: item.dataEntrega ?? null,
      estoqueOrigemNome: item.estoqueOrigemNome ?? null,
      estoqueDestinoNome: item.estoqueDestinoNome ?? null,
      idRequisitante: item.idRequisitante,
      idEquipe: tecnico?.idEquipe ?? null,
      idColaboradorSaque: tecnico?.idColaboradorSaque ?? null,
      saqueForaEquipe: tecnico?.saqueForaEquipe ?? false,
      idAlmoxarifadoAtendimento: tecnico?.idAlmoxarifadoAtendimento ?? null,
      almoxarifadoNome: item.estoqueDestinoNome ?? item.estoqueOrigemNome ?? null,
      status: item.status,
      statusSeparacao: item.statusSeparacao ?? tecnico?.statusSeparacao ?? null,
      dataPrevista: tecnico?.dataPrevista ?? null,
      etapaOperacional: tecnico?.etapaOperacional ?? item.etapaOperacional ?? null,
      numeroReserva: reserva?.numero ?? null,
      idAgenteReserva: reserva?.idAgenteResponsavel ?? null,
      nomeAgenteReserva: reserva?.nomeAgente ?? null,
      dataCriacaoReserva: reserva?.dataCriacao ?? null,
      registradaEmReserva: reserva?.registradaEm ?? null,
      referencia: item.referencia ?? null,
      numSerie: item.numSerie ?? null,
      quantidadeItens: item.totalItens,
      quantidadeSolicitada: totais?.solicitada ?? item.totalItens,
      quantidadeEntregaLiquida: totais?.entregaLiquida ?? 0,
      quantidadeDevolucaoLiquida: totais?.devolucaoLiquida ?? 0,
      quantidadePendente: totais?.pendente ?? 0,
      totalOperacoes: tecnico?.operacoes?.length ?? 0,
      atrasada: tecnico?.atrasada ?? false,
      encerradaEm: tecnico?.encerradaEm ?? null,
      motivoEncerramento: tecnico?.motivoEncerramento ?? null,
      versao: item.versao,
      acoesPermitidas: item.acoesPermitidas ?? tecnico?.acoesPermitidas ?? [],
    };
  }

  exportarSolicitacoesResumo(params: any): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/solicitacoes-material/exportar/resumo`, {
      params: this.paramsExportacao(params),
      responseType: 'blob',
    });
  }

  exportarSolicitacoesDetalhado(params: any): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/solicitacoes-material/exportar/detalhado`, {
      params: this.paramsExportacao(params),
      responseType: 'blob',
    });
  }

  private paramsExportacao(params: any): HttpParams {
    let httpParams = new HttpParams();
    if (!params) return httpParams;
    Object.keys(params).forEach((key) => {
      const val = params[key];
      if (val !== null && val !== undefined && val !== '') {
        if (val instanceof Date) {
          httpParams = httpParams.set(key, val.toISOString());
        } else if (Array.isArray(val)) {
          val.forEach((item: any) => {
            if (item !== null && item !== undefined && item !== '') {
              httpParams = httpParams.append(key, String(item));
            }
          });
        } else {
          httpParams = httpParams.set(key, String(val));
        }
      }
    });
    return httpParams;
  }

  // --- Movimentações de Estoque ---

  listarMovimentacoes(
    params: MovimentacaoEstoqueFilter,
  ): Observable<PageResponseMovimentacaoOperacaoSummaryResponse> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== null && value !== undefined) {
        httpParams = httpParams.set(key, String(value));
      }
    });
    return this.http.get<PageResponseMovimentacaoOperacaoSummaryResponse>(
      `${this.apiUrl}/movimentacoes-estoque`,
      { params: httpParams },
    );
  }

  buscarMovimentacaoPorId(id: string): Observable<MovimentacaoEstoqueResponse> {
    return this.http.get<MovimentacaoEstoqueResponse>(`${this.apiUrl}/movimentacoes-estoque/${id}`);
  }

  buscarOperacaoPorIdReferencia(idReferencia: string): Observable<MovimentacaoOperacaoResponse> {
    return this.http.get<MovimentacaoOperacaoResponse>(
      `${this.apiUrl}/movimentacoes-estoque/operacao/${idReferencia}`,
    );
  }

  registrarTransferenciaLote(request: MovimentacaoBulkRequest): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/movimentacoes-estoque/transferencia`, request);
  }

  buscarAssinaturaTransferencia(idReferencia: string, parte: 'ENTREGADOR' | 'RECEBEDOR'): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/movimentacoes-estoque/operacao/${idReferencia}/assinaturas/${parte}`, {
      responseType: 'blob',
    });
  }

  registrarEntradaLote(request: MovimentacaoBulkRequest): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/movimentacoes-estoque/entrada`, request);
  }

  registrarBaixaLote(request: MovimentacaoBulkRequest): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/movimentacoes-estoque/baixa`, request);
  }

  registrarDevolucaoComercial(request: DevolucaoComercialRequest): Observable<string> {
    return this.http.post<string>(`${this.apiUrl}/movimentacoes-estoque/devolucao-comercial`, request);
  }

  // --- Lookups ---

  listarAvaliacoesAtivas(): Observable<PagedResponse<AvaliacaoMaterialResponse>> {
    const params = new HttpParams()
      .set('ativo', 'true')
      .set('size', '1000')
      .set('sort', 'nome,asc');
    return this.http.get<PagedResponse<AvaliacaoMaterialResponse>>(
      `${this.apiUrl}/avaliacoes-material`,
      { params },
    );
  }

  listarCentrosInvestimentoAtivos(): Observable<PagedResponse<CentroInvestimentoResponse>> {
    const params = new HttpParams()
      .set('ativo', 'true')
      .set('size', '1000')
      .set('sort', 'codigo,asc');
    return this.http.get<PagedResponse<CentroInvestimentoResponse>>(
      `${this.apiUrl}/centros-investimento`,
      { params },
    );
  }

  listarEstoquesAtivos(): Observable<PagedResponse<EstoqueResponse>> {
    const params = new HttpParams()
      .set('ativo', 'true')
      .set('size', '1000')
      .set('sort', 'nomeIdentificador,asc');
    return this.http.get<PagedResponse<EstoqueResponse>>(`${this.apiUrl}/estoques`, { params });
  }

  listarUsuariosAtivos(): Observable<PagedResponse<UsuarioResponse>> {
    const params = new HttpParams()
      .set('ativo', 'true')
      .set('size', '1000')
      .set('sort', 'login,asc');
    return this.http.get<PagedResponse<UsuarioResponse>>(`${environment.apiUrl}/v1/iam/usuarios`, {
      params,
    });
  }

  listarMotivosRejeicaoAtivos(): Observable<PageResponseMotivoRejeicaoResponse> {
    const params = new HttpParams()
      .set('ativo', 'true')
      .set('size', '1000')
      .set('sort', 'nome,asc');
    return this.http.get<PageResponseMotivoRejeicaoResponse>(`${this.apiUrl}/motivos-rejeicao`, {
      params,
    });
  }

  listarMotivosPendencia(apenasAtivos = false): Observable<MotivoPendenciaResponse[]> {
    return this.http.get<MotivoPendenciaResponse[]>(`${this.apiUrl}/motivos-pendencia`, {
      params: { apenasAtivos },
    });
  }

  criarMotivoPendencia(request: MotivoPendenciaRequest): Observable<MotivoPendenciaResponse> {
    return this.http.post<MotivoPendenciaResponse>(`${this.apiUrl}/motivos-pendencia`, request);
  }

  atualizarMotivoPendencia(id: string, request: MotivoPendenciaRequest): Observable<MotivoPendenciaResponse> {
    return this.http.put<MotivoPendenciaResponse>(`${this.apiUrl}/motivos-pendencia/${id}`, request);
  }

  alterarAtivacaoMotivoPendencia(id: string, ativar: boolean): Observable<void> {
    return ativar
      ? this.http.patch<void>(`${this.apiUrl}/motivos-pendencia/${id}/ativar`, {})
      : this.http.delete<void>(`${this.apiUrl}/motivos-pendencia/${id}`);
  }

  ativarMotivoPendencia(id: string): Observable<void> {
    return this.alterarAtivacaoMotivoPendencia(id, true);
  }

  desativarMotivoPendencia(id: string): Observable<void> {
    return this.alterarAtivacaoMotivoPendencia(id, false);
  }

  registrarPendencia(idSolicitacao: string, request: RegistrarPendenciaRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/solicitacoes-material/${idSolicitacao}/pendencia`, request);
  }

  removerPendencia(idSolicitacao: string, idItem?: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/solicitacoes-material/${idSolicitacao}/pendencia`, {
      params: idItem ? { idItem } : {},
    });
  }

  listarTiposEstoqueAtivos(): Observable<PageResponseTipoEstoqueResponse> {
    const params = new HttpParams()
      .set('ativo', 'true')
      .set('size', '1000')
      .set('sort', 'nome,asc');
    return this.http.get<PageResponseTipoEstoqueResponse>(`${this.apiUrl}/tipos-estoque`, {
      params,
    });
  }

  // --- Saldos ---
  listarSaldos(params?: any): Observable<PageResponseSaldoResponse> {
    let httpParams = new HttpParams();
    if (params) {
      Object.keys(params).forEach((key) => {
        if (params[key] !== null && params[key] !== undefined && params[key] !== '') {
          httpParams = httpParams.set(key, params[key]);
        }
      });
    }
    return this.http.get<PageResponseSaldoResponse>(`${this.apiUrl}/saldos`, {
      params: httpParams,
    });
  }

  /**
   * Saldos negativos de um estoque (ou do estoque de uma equipe). Usado como aviso
   * informativo nas entregas: o campo aplicou material além do saldo da equipe.
   */
  listarSaldosNegativos(filtro: { idEstoque?: string | null; idEquipe?: string | null }): Observable<SaldoResponse[]> {
    return this.listarSaldos({
      idEstoque: filtro.idEstoque,
      idEquipe: filtro.idEquipe,
      negativo: true,
      ativo: true,
      size: 500,
    }).pipe(map((res) => res.content ?? []));
  }

  /** Onde cada série está ativa hoje (estoque, material, condição e reserva). Séries fora do sistema não voltam. */
  verificarSeries(series: string[]): Observable<SaldoSerieResponse[]> {
    return this.http.post<SaldoSerieResponse[]>(`${this.apiUrl}/saldos/seriais/verificar`, { series });
  }

  /** Define as séries reservadas de um item antes da entrega; a lista substitui a anterior. */
  definirSeriesReservadas(idSolicitacao: string, idItem: string, series: string[]): Observable<string[]> {
    return this.http.put<string[]>(`${this.apiUrl}/solicitacoes-material/${idSolicitacao}/itens/${idItem}/series-reservadas`, { series });
  }

  listarSaldosSeriais(params?: any): Observable<PageResponseSaldoSerieResponse> {
    let httpParams = new HttpParams();
    if (params) {
      Object.keys(params).forEach((key) => {
        if (params[key] !== null && params[key] !== undefined && params[key] !== '') {
          httpParams = httpParams.set(key, params[key]);
        }
      });
    }
    return this.http.get<PageResponseSaldoSerieResponse>(`${this.apiUrl}/saldos/seriais`, {
      params: httpParams,
    });
  }

  // Alias para compatibilidade com outros componentes
  listarSeriais(params?: any): Observable<PageResponseSaldoSerieResponse> {
    return this.listarSaldosSeriais(params);
  }

  // --- Dashboards ---
  obterDashboardSolicitacoes(params?: any): Observable<DashboardSolicitacaoResponse> {
    let httpParams = new HttpParams();
    if (params) {
      Object.keys(params).forEach((key) => {
        if (params[key] !== null && params[key] !== undefined && params[key] !== '') {
          if (params[key] instanceof Date) {
            httpParams = httpParams.set(key, params[key].toISOString());
          } else {
            httpParams = httpParams.set(key, params[key]);
          }
        }
      });
    }
    return this.http.get<DashboardSolicitacaoResponse>(`${this.apiUrl}/dashboard/solicitacoes`, {
      params: httpParams,
    });
  }

  /** Painel analítico de solicitações (comercial, técnico ou os dois). */
  obterPainelSolicitacoes(filtros: PainelSolicitacoesFiltros): Observable<PainelSolicitacoesResponse> {
    return this.http.get<PainelSolicitacoesResponse>(`${this.apiUrl}/dashboard/solicitacoes/analitico`, {
      params: paramsDoPainel(filtros),
    });
  }

  /** Exportação do painel em CSV ou XLSX: conteúdo e nível escolhidos, com o recorte informado. */
  exportarPainelSolicitacoes(filtros: PainelSolicitacoesFiltros, opcoes: ExportacaoPainelSolicitacoes): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/dashboard/solicitacoes/analitico/exportar`, {
      params: paramsDoPainel({ ...filtros, ...opcoes }),
      responseType: 'blob',
    });
  }

  // --- Importação de Solicitações Comerciais ---

  validarImportacaoSolicitacoesComerciais(arquivo: File): Observable<ImportacaoSolicitacaoComercialResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoSolicitacaoComercialResponse>(
      `${this.apiUrl}/solicitacoes-material/comerciais/importar/validar`,
      formData,
    );
  }

  confirmarImportacaoSolicitacoesComerciais(
    request: ConfirmarImportacaoSolicitacaoComercialRequest,
  ): Observable<ImportacaoSolicitacaoComercialResponse> {
    return this.http.post<ImportacaoSolicitacaoComercialResponse>(
      `${this.apiUrl}/solicitacoes-material/comerciais/importar/confirmar`,
      request,
    );
  }

  baixarModeloCsvSolicitacoesComerciais(): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/solicitacoes-material/comerciais/importar/modelo`, {
      responseType: 'blob',
    });
  }

  // --- Importação de Movimentações de Estoque ---

  validarImportacaoMovimentacoes(arquivo: File): Observable<ImportacaoMovimentacaoResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoMovimentacaoResponse>(
      `${this.apiUrl}/movimentacoes-estoque/importar/validar`,
      formData,
    );
  }

  confirmarImportacaoMovimentacoes(
    request: ConfirmarImportacaoMovimentacaoRequest,
  ): Observable<ImportacaoMovimentacaoResponse> {
    return this.http.post<ImportacaoMovimentacaoResponse>(
      `${this.apiUrl}/movimentacoes-estoque/importar/confirmar`,
      request,
    );
  }

  baixarModeloCsvMovimentacoes(): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/movimentacoes-estoque/importar/modelo`, {
      responseType: 'blob',
    });
  }
}

function paramsDoPainel(filtros: object): HttpParams {
  let params = new HttpParams();
  for (const [chave, valor] of Object.entries(filtros)) {
    if (valor !== null && valor !== undefined && valor !== '') params = params.set(chave, String(valor));
  }
  return params;
}
