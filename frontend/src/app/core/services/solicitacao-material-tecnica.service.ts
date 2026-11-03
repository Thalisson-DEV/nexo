import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AlterarSeparacaoTecnicaRequest,
  CodigoTipoTecnico,
  ConfirmacaoImportacaoSolicitacaoTecnicaResponse,
  ConfirmarDevolucaoTecnicaRequest,
  ConfirmarEntregaTecnicaRequest,
  ConfirmarImportacaoSolicitacaoTecnicaRequest,
  CriacaoSolicitacaoTecnicaResponse,
  CriarSolicitacaoTecnicaRequest,
  EncerrarSaldoTecnicoRequest,
  EstornarOperacaoTecnicaRequest,
  HistoricoSolicitacaoResponse,
  ImportacaoSolicitacaoTecnicaResponse,
  MensagemImportacaoTecnica,
  OperacaoTecnicaResponse,
  KanbanSolicitacaoTecnicaApiResponse,
  KanbanSolicitacaoTecnicaResponse,
  PaginaSolicitacoesMaterial,
  ParChaveTipoImportacao,
  PlanejamentoSolicitacaoTecnicaApiResponse,
  PlanejamentoSolicitacaoTecnicaResponse,
  PlanejarSolicitacaoTecnicaRequest,
  SaldoObra,
  ProjecaoObraSolicitacoesResponse,
  ProjetoRelacionadoSolicitacao,
  RegistrarReservaTecnicaRequest,
  ReservaTecnicaResponse,
  SolicitacaoMaterialDetalheApiResponse,
  SolicitacaoMaterialListaItemResponse,
  SolicitacaoMaterialDetalheResponse,
  SolicitacaoMaterialFilter,
  SolicitacaoMaterialSummaryResponse,
  StatusSeparacao,
} from '../models/solicitacao-material-tecnica.models';
import { HEADER_IDEMPOTENCIA, novaChaveIdempotencia } from '../utils/idempotencia.util';
import { toHttpParams } from '../utils/http-params.util';

@Injectable({ providedIn: 'root' })
export class SolicitacaoMaterialTecnicaService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/v1/solicitacoes-material`;

  obterKanban(filtros: Partial<SolicitacaoMaterialFilter>): Observable<KanbanSolicitacaoTecnicaResponse> {
    return this.http
      .get<KanbanSolicitacaoTecnicaApiResponse>(`${this.baseUrl}/tecnicas/kanban`, {
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
              itens: (sub.itens ?? []).map((item) => this.normalizarResumo(item)),
            })),
          })),
        }))
      );
  }

  listar(filtros: SolicitacaoMaterialFilter): Observable<PaginaSolicitacoesMaterial> {
    return this.http.get<{ content: SolicitacaoMaterialListaItemResponse[]; page: PaginaSolicitacoesMaterial['page'] }>(this.baseUrl, {
      params: toHttpParams(filtros as Record<string, unknown>),
    }).pipe(
      map((response) => ({
        ...response,
        content: response.content.map((item) => this.normalizarResumo(item)),
      })),
    );
  }

  buscarPorId(idSolicitacao: string): Observable<SolicitacaoMaterialDetalheResponse> {
    return this.http
      .get<SolicitacaoMaterialDetalheApiResponse>(`${this.baseUrl}/${idSolicitacao}`)
      .pipe(map((response) => this.normalizarDetalhe(response)));
  }

  exportarResumo(filtros: any): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/exportar/resumo`, {
      params: toHttpParams(filtros as Record<string, unknown>),
      responseType: 'blob',
    });
  }

  exportarDetalhado(filtros: any): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/exportar/detalhado`, {
      params: toHttpParams(filtros as Record<string, unknown>),
      responseType: 'blob',
    });
  }

  criar(
    request: CriarSolicitacaoTecnicaRequest,
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<CriacaoSolicitacaoTecnicaResponse> {
    return this.http.post<CriacaoSolicitacaoTecnicaResponse>(`${this.baseUrl}/tecnicas`, request, {
      headers: this.headers(chaveIdempotencia),
    });
  }

  substituirPlanejamento(
    idSolicitacao: string,
    request: PlanejarSolicitacaoTecnicaRequest,
  ): Observable<PlanejamentoSolicitacaoTecnicaResponse> {
    return this.http
      .put<PlanejamentoSolicitacaoTecnicaApiResponse>(
        `${this.baseUrl}/tecnicas/${idSolicitacao}/planejamento`,
        request,
      )
      .pipe(
        map((response) => ({
          solicitacaoAtualizada: this.normalizarDetalhe(response.solicitacaoAtualizada),
        })),
      );
  }

  registrarReserva(
    idSolicitacao: string,
    request: RegistrarReservaTecnicaRequest,
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<ReservaTecnicaResponse> {
    return this.http.put<ReservaTecnicaResponse>(
      `${this.baseUrl}/tecnicas/${idSolicitacao}/reserva`,
      request,
      { headers: this.headers(chaveIdempotencia) },
    );
  }

  iniciarSeparacao(
    idSolicitacao: string,
    request: AlterarSeparacaoTecnicaRequest,
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<StatusSeparacao> {
    return this.http.post<StatusSeparacao>(
      `${this.baseUrl}/tecnicas/${idSolicitacao}/separacao/iniciar`,
      request,
      { headers: this.headers(chaveIdempotencia) },
    );
  }

  concluirSeparacao(
    idSolicitacao: string,
    request: AlterarSeparacaoTecnicaRequest,
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<StatusSeparacao> {
    return this.http.post<StatusSeparacao>(
      `${this.baseUrl}/tecnicas/${idSolicitacao}/separacao/concluir`,
      request,
      { headers: this.headers(chaveIdempotencia) },
    );
  }

  pularSeparacao(
    idSolicitacao: string,
    request: AlterarSeparacaoTecnicaRequest,
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<StatusSeparacao> {
    return this.http.post<StatusSeparacao>(
      `${this.baseUrl}/tecnicas/${idSolicitacao}/separacao/pular`,
      request,
      { headers: this.headers(chaveIdempotencia) },
    );
  }

  entregar(
    idSolicitacao: string,
    request: ConfirmarEntregaTecnicaRequest,
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<OperacaoTecnicaResponse> {
    return this.http.post<OperacaoTecnicaResponse>(
      `${this.baseUrl}/tecnicas/${idSolicitacao}/entregas`,
      request,
      { headers: this.headers(chaveIdempotencia) },
    );
  }

  devolver(
    idSolicitacao: string,
    request: ConfirmarDevolucaoTecnicaRequest,
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<OperacaoTecnicaResponse> {
    return this.http.post<OperacaoTecnicaResponse>(
      `${this.baseUrl}/tecnicas/${idSolicitacao}/devolucoes`,
      request,
      { headers: this.headers(chaveIdempotencia) },
    );
  }

  estornar(
    idSolicitacao: string,
    request: EstornarOperacaoTecnicaRequest,
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<OperacaoTecnicaResponse> {
    return this.http.post<OperacaoTecnicaResponse>(
      `${this.baseUrl}/tecnicas/${idSolicitacao}/estornos`,
      request,
      { headers: this.headers(chaveIdempotencia) },
    );
  }

  encerrar(
    idSolicitacao: string,
    request: EncerrarSaldoTecnicoRequest,
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<void> {
    return this.http.post<void>(
      `${this.baseUrl}/tecnicas/${idSolicitacao}/encerramento`,
      request,
      { headers: this.headers(chaveIdempotencia) },
    );
  }

  historico(
    idSolicitacao: string,
    page = 0,
    size = 20,
  ): Observable<HistoricoSolicitacaoResponse> {
    return this.http.get<HistoricoSolicitacaoResponse>(
      `${this.baseUrl}/${idSolicitacao}/historico`,
      { params: { page, size, sort: 'sequencia,desc' } },
    );
  }

  baixarModelo(): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.baseUrl}/importacoes/modelo`, {
      params: { formato: 'csv' },
      observe: 'response',
      responseType: 'blob',
    });
  }

  validarImportacao(arquivo: File): Observable<ImportacaoSolicitacaoTecnicaResponse> {
    const corpo = new FormData();
    corpo.append('arquivo', arquivo);
    return this.http
      .post<ImportacaoSolicitacaoTecnicaResponse & { solicitacoesPrevistas?: Array<{ chave: string; subgrupos?: Array<{ tipo: CodigoTipoTecnico; linhasOrigem?: number[] }>; mensagens?: MensagemImportacaoTecnica[] }> }>(
        `${this.baseUrl}/importacoes/validacao`,
        corpo,
      )
      .pipe(
        map((response) => {
          const solicitacoes = response.solicitacoesPrevistas ?? [];
          const paresDisponiveis: ParChaveTipoImportacao[] = [];
          const errosPorLinha: MensagemImportacaoTecnica[] = [];

          solicitacoes.forEach((sol) => {
            (sol.mensagens ?? []).forEach((msg) => {
              if (msg.linha != null) {
                errosPorLinha.push(msg);
              } else {
                const linhas = new Set((sol.subgrupos ?? []).flatMap((sub) => sub.linhasOrigem ?? []));
                if (linhas.size === 0) errosPorLinha.push(msg);
                linhas.forEach((linha) => errosPorLinha.push({ ...msg, linha }));
              }
            });
            (sol.subgrupos ?? []).forEach((sub) => {
              const temErro = (sol.mensagens ?? []).some((m) => m.severidade === 'ERRO');
              if (!temErro) {
                paresDisponiveis.push({ chave: sol.chave, tipo: sub.tipo });
              }
            });
          });

          return {
            ...response,
            paresDisponiveis: response.paresDisponiveis ?? paresDisponiveis,
            errosPorLinha: response.errosPorLinha ?? errosPorLinha,
          };
        }),
      );
  }

  confirmarImportacao(
    request: ConfirmarImportacaoSolicitacaoTecnicaRequest,
    chaveIdempotencia = novaChaveIdempotencia(),
  ): Observable<ConfirmacaoImportacaoSolicitacaoTecnicaResponse> {
    return this.http.post<ConfirmacaoImportacaoSolicitacaoTecnicaResponse>(
      `${this.baseUrl}/importacoes/confirmacao`,
      request,
      { headers: this.headers(chaveIdempotencia) },
    );
  }

  obterSaldoObra(idObra: string): Observable<SaldoObra> {
    return this.http.get<SaldoObra>(`${environment.apiUrl}/v1/obras/${idObra}/saldo-material`);
  }

  obterProjecaoObra(idObra: string): Observable<ProjecaoObraSolicitacoesResponse> {
    return this.http.get<ProjecaoObraSolicitacoesResponse>(
      `${environment.apiUrl}/v1/obras/${idObra}/solicitacoes-material`,
    );
  }

  projetosRelacionados(idObra: string): Observable<ProjetoRelacionadoSolicitacao[]> {
    return this.http.get<ProjetoRelacionadoSolicitacao[]>(
      `${environment.apiUrl}/v1/obras/${idObra}/projetos-relacionados`,
    );
  }

  private headers(chaveIdempotencia: string): HttpHeaders {
    return new HttpHeaders().set(HEADER_IDEMPOTENCIA, chaveIdempotencia);
  }

  normalizarResumo(item: SolicitacaoMaterialListaItemResponse): SolicitacaoMaterialSummaryResponse {
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

  private normalizarDetalhe(
    response: SolicitacaoMaterialDetalheApiResponse,
  ): SolicitacaoMaterialDetalheResponse {
    const tipoTecnico = response.tecnico?.tipoTecnico;
    const itens = response.itens.map((item) => {
      const tipoMovimentacao = item.tipoMovimentacao ?? tipoTecnico;

      if (!tipoMovimentacao) {
        throw new Error(
          `Solicitacao tecnica ${response.id} sem tipoTecnico ou tipoMovimentacao nos itens.`,
        );
      }

      return { ...item, tipoMovimentacao };
    });

    return {
      ...response,
      etapaOperacional: response.etapaOperacional ?? response.tecnico?.etapaOperacional ?? null,
      itens,
    };
  }
}
