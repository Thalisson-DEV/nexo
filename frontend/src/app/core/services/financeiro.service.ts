import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, forkJoin } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { toHttpParams } from '../utils/http-params.util';
import {
  DashboardFinanceiroFilter,
  DashboardFinanceiroResponse
} from '../models/financeiro-dashboard.models';
import {
  CampoCanonico,
  CampoRequest,
  ApontarPendenciaDocumentoRequest,
  ConferirDocumentoRequest,
  DocumentoFinanceiro,
  DocumentoFinanceiroFilter,
  DecisaoEmail,
  DecisaoEmailRequest,
  DocumentoRequest,
  EventoSolicitacao,
  DocumentoResumo,
  FinanceiroPage,
  FormularioResumo,
  FormularioSchema,
  Fornecedor,
  FornecedorRequest,
  PrePreenchimentoPagamento,
  ReferenciaOpcao,
  SolicitacaoFinanceira,
  SolicitacaoFinanceiraFilter,
  SolicitacaoRequest,
  TipoDocumentoFinanceiro,
  TipoSolicitacao
} from '../models/financeiro.models';
import { HEADER_IDEMPOTENCIA } from '../utils/idempotencia.util';
import { PagedResponse } from '../models/shared.models';
import { REMOTE_LOOKUP_PAGE_SIZE } from '../utils/remote-lookup.util';

@Injectable({ providedIn: 'root' })
export class FinanceiroService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/v1/financeiro`;

  /** Estado global de abertura do navegador de solicitações para persistir durante navegação */
  readonly navegadorAberto = signal(false);

  listarVariantes(tipo: TipoSolicitacao): Observable<FormularioResumo[]> {
    return this.http.get<FormularioResumo[]>(`${this.api}/formularios/variantes`, {
      params: { tipo }
    });
  }

  buscarSchema(id: string): Observable<FormularioSchema> {
    return this.http.get<FormularioSchema>(`${this.api}/formularios/${id}/schema`);
  }

  listarSolicitacoes(
    tipo: TipoSolicitacao | 'AMBOS',
    filtros: SolicitacaoFinanceiraFilter
  ): Observable<FinanceiroPage<SolicitacaoFinanceira>> {
    const params = this.paramsDaSolicitacao(filtros);
    if (tipo === 'AMBOS') {
      return forkJoin([
        this.http.get<FinanceiroPage<SolicitacaoFinanceira>>(this.solicitacoesUrl('COMPRA'), {
          params
        }),
        this.http.get<FinanceiroPage<SolicitacaoFinanceira>>(this.solicitacoesUrl('PAGAMENTO'), {
          params
        })
      ]).pipe(
        map(([compras, pagamentos]) => {
          const content = [...compras.content, ...pagamentos.content];
          const sortVal = typeof filtros['sort'] === 'string' ? filtros['sort'] : 'dataCriacao,desc';
          const [field, dir] = sortVal.split(',');

          content.sort((a, b) => {
            let valA = this.valorDeOrdenacao(a, field);
            let valB = this.valorDeOrdenacao(b, field);

            const left = valA ?? '';
            const right = valB ?? '';
            if (left < right) return dir === 'asc' ? -1 : 1;
            if (left > right) return dir === 'asc' ? 1 : -1;
            return 0;
          });

          const size = Number(filtros['size']) || 20;
          return {
            content: content.slice(0, size),
            page: {
              size,
              totalElements: compras.page.totalElements + pagamentos.page.totalElements,
              totalPages: Math.max(compras.page.totalPages, pagamentos.page.totalPages),
              number: compras.page.number
            }
          };
        })
      );
    }

    return this.http.get<FinanceiroPage<SolicitacaoFinanceira>>(this.solicitacoesUrl(tipo as TipoSolicitacao), {
      params
    });
  }

  exportarSolicitacoesResumo(
    tipo: TipoSolicitacao,
    filtros: SolicitacaoFinanceiraFilter
  ): Observable<HttpResponse<Blob>> {
    const params = this.paramsExportacao(filtros);
    return this.http.get(`${this.solicitacoesUrl(tipo)}/exportar/resumo`, {
      params,
      responseType: 'blob',
      observe: 'response'
    });
  }

  exportarSolicitacoesDetalhado(
    tipo: TipoSolicitacao,
    filtros: SolicitacaoFinanceiraFilter
  ): Observable<HttpResponse<Blob>> {
    const params = this.paramsExportacao(filtros);
    return this.http.get(`${this.solicitacoesUrl(tipo)}/exportar/detalhado`, {
      params,
      responseType: 'blob',
      observe: 'response'
    });
  }

  buscarSolicitacao(tipo: TipoSolicitacao, id: string): Observable<SolicitacaoFinanceira> {
    return this.http.get<SolicitacaoFinanceira>(`${this.solicitacoesUrl(tipo)}/${id}`);
  }

  criarSolicitacao(
    tipo: TipoSolicitacao,
    request: SolicitacaoRequest
  ): Observable<SolicitacaoFinanceira> {
    return this.http.post<SolicitacaoFinanceira>(this.solicitacoesUrl(tipo), request);
  }

  atualizarSolicitacao(
    tipo: TipoSolicitacao,
    id: string,
    request: SolicitacaoRequest
  ): Observable<SolicitacaoFinanceira> {
    return this.http.put<SolicitacaoFinanceira>(`${this.solicitacoesUrl(tipo)}/${id}`, request);
  }

  submeter(tipo: TipoSolicitacao, id: string): Observable<SolicitacaoFinanceira> {
    return this.http.patch<SolicitacaoFinanceira>(
      `${this.solicitacoesUrl(tipo)}/${id}/submeter`,
      {}
    );
  }

  aprovar(
    tipo: TipoSolicitacao,
    id: string,
    valorAprovado?: number | null,
    observacao?: string | null
  ): Observable<SolicitacaoFinanceira> {
    return this.http.patch<SolicitacaoFinanceira>(
      `${this.solicitacoesUrl(tipo)}/${id}/aprovar`,
      { valorAprovado, observacao }
    );
  }

  rejeitar(tipo: TipoSolicitacao, id: string, motivo: string): Observable<SolicitacaoFinanceira> {
    return this.http.patch<SolicitacaoFinanceira>(
      `${this.solicitacoesUrl(tipo)}/${id}/rejeitar`,
      { motivo }
    );
  }

  cancelar(tipo: TipoSolicitacao, id: string, motivo: string): Observable<SolicitacaoFinanceira> {
    return this.http.patch<SolicitacaoFinanceira>(
      `${this.solicitacoesUrl(tipo)}/${id}/cancelar`,
      { motivo }
    );
  }

  /** Devolve uma solicitação aprovada ou rejeitada à fila de aprovação. */
  desfazerDecisao(tipo: TipoSolicitacao, id: string, motivo: string): Observable<SolicitacaoFinanceira> {
    return this.http.patch<SolicitacaoFinanceira>(
      `${this.solicitacoesUrl(tipo)}/${id}/desfazer-decisao`,
      { motivo }
    );
  }

  /** Página do link de decisão do e-mail: rota pública, o token é a credencial. */
  consultarDecisaoEmail(token: string): Observable<DecisaoEmail> {
    return this.http.get<DecisaoEmail>(`${environment.apiUrl}/v1/publico/financeiro/decisoes/${encodeURIComponent(token)}`);
  }

  decidirPorEmail(token: string, decisao: DecisaoEmailRequest): Observable<DecisaoEmail> {
    return this.http.post<DecisaoEmail>(
      `${environment.apiUrl}/v1/publico/financeiro/decisoes/${encodeURIComponent(token)}`,
      decisao
    );
  }

  historico(tipo: TipoSolicitacao, id: string): Observable<EventoSolicitacao[]> {
    return this.http.get<EventoSolicitacao[]>(`${this.solicitacoesUrl(tipo)}/${id}/historico`);
  }

  // ── Documentos vistos de dentro da solicitação ────────────────────────────────
  //
  // Anexar deixou de exigir que a solicitação esteja editável: a nota fiscal e o
  // boleto chegam justamente depois da aprovação, e antes o sistema recusava
  // guardá-los exatamente no momento em que eles apareciam.

  anexarDocumento(
    tipo: TipoSolicitacao,
    id: string,
    arquivos: readonly File[],
    tipoDocumento: TipoDocumentoFinanceiro,
    chaveIdempotencia?: string
  ): Observable<SolicitacaoFinanceira> {
    if (arquivos.length === 0) {
      throw new Error('Selecione ao menos um arquivo.');
    }

    const data = new FormData();
    for (const arquivo of arquivos) {
      data.append('arquivo', arquivo);
    }

    return this.http.post<SolicitacaoFinanceira>(
      `${this.solicitacoesUrl(tipo)}/${id}/documentos`,
      data,
      { params: { tipo: tipoDocumento }, ...this.opcoesMutacao(chaveIdempotencia) }
    );
  }

  removerDocumentoDaSolicitacao(
    tipo: TipoSolicitacao,
    id: string,
    idDocumento: string,
    chaveIdempotencia?: string
  ): Observable<SolicitacaoFinanceira | void> {
    return this.http.delete<SolicitacaoFinanceira | void>(
      `${this.solicitacoesUrl(tipo)}/${id}/documentos/${idDocumento}`,
      this.opcoesMutacao(chaveIdempotencia)
    );
  }

  baixarDocumentoDaSolicitacao(
    tipo: TipoSolicitacao,
    id: string,
    idDocumento: string
  ): Observable<Blob> {
    return this.http.get(`${this.solicitacoesUrl(tipo)}/${id}/documentos/${idDocumento}`, {
      responseType: 'blob'
    });
  }

  listarDocumentosDaSolicitacao(tipo: TipoSolicitacao, id: string): Observable<DocumentoResumo[]> {
    return this.http.get<DocumentoResumo[]>(`${this.solicitacoesUrl(tipo)}/${id}/documentos`);
  }

  // ── Repositório documental ────────────────────────────────────────────────────

  listarDocumentos(
    filtros: DocumentoFinanceiroFilter & Record<string, unknown>
  ): Observable<FinanceiroPage<DocumentoFinanceiro>> {
    return this.http.get<FinanceiroPage<DocumentoFinanceiro>>(`${this.api}/documentos`, {
      params: toHttpParams(filtros)
    });
  }

  buscarDocumento(id: string): Observable<DocumentoFinanceiro> {
    return this.http.get<DocumentoFinanceiro>(`${this.api}/documentos/${id}`);
  }

  /**
   * Envio avulso. Os metadados vão numa segunda parte multipart e **precisam** ir
   * como Blob com `type: application/json` — sem o content-type explícito o Spring
   * recusa a parte, porque não tem como saber que aquilo é JSON.
   */
  enviarDocumento(
    arquivo: File,
    dados?: DocumentoRequest,
    chaveIdempotencia?: string
  ): Observable<DocumentoFinanceiro> {
    const data = new FormData();
    data.append('arquivo', arquivo);
    if (dados) {
      data.append('dados', new Blob([JSON.stringify(dados)], { type: 'application/json' }));
    }
    return this.http.post<DocumentoFinanceiro>(`${this.api}/documentos`, data, this.opcoesMutacao(chaveIdempotencia));
  }

  atualizarDocumento(
    id: string,
    request: DocumentoRequest,
    chaveIdempotencia?: string
  ): Observable<DocumentoFinanceiro> {
    return this.http.put<DocumentoFinanceiro>(
      `${this.api}/documentos/${id}`,
      this.documentoRequestCompleto(request),
      this.opcoesMutacao(chaveIdempotencia)
    );
  }

  vincularDocumento(
    id: string,
    tipo: TipoSolicitacao,
    solicitacaoId: string,
    chaveIdempotencia?: string
  ): Observable<DocumentoFinanceiro> {
    return this.http.patch<DocumentoFinanceiro>(
      `${this.api}/documentos/${id}/vincular`,
      {},
      { params: { tipo, solicitacaoId }, ...this.opcoesMutacao(chaveIdempotencia) }
    );
  }

  desvincularDocumento(id: string, chaveIdempotencia?: string): Observable<DocumentoFinanceiro> {
    return this.http.patch<DocumentoFinanceiro>(
      `${this.api}/documentos/${id}/desvincular`,
      {},
      this.opcoesMutacao(chaveIdempotencia)
    );
  }

  conferirDocumento(
    id: string,
    observacaoOuRequest?: string | ConferirDocumentoRequest | null,
    chaveIdempotencia?: string
  ): Observable<DocumentoFinanceiro> {
    const request: ConferirDocumentoRequest =
      typeof observacaoOuRequest === 'string'
        ? { observacao: observacaoOuRequest }
        : observacaoOuRequest ?? {};
    return this.http.patch<DocumentoFinanceiro>(
      `${this.api}/documentos/${id}/conferir`,
      request,
      this.opcoesMutacao(chaveIdempotencia)
    );
  }

  apontarPendenciaDocumento(
    id: string,
    motivoOuRequest: string | ApontarPendenciaDocumentoRequest,
    chaveIdempotencia?: string
  ): Observable<DocumentoFinanceiro> {
    const request: ApontarPendenciaDocumentoRequest =
      typeof motivoOuRequest === 'string' ? { motivo: motivoOuRequest } : motivoOuRequest;
    return this.http.patch<DocumentoFinanceiro>(
      `${this.api}/documentos/${id}/pendencia`,
      request,
      this.opcoesMutacao(chaveIdempotencia)
    );
  }

  arquivarDocumento(id: string, chaveIdempotencia?: string): Observable<DocumentoFinanceiro> {
    return this.http.patch<DocumentoFinanceiro>(
      `${this.api}/documentos/${id}/arquivar`,
      {},
      this.opcoesMutacao(chaveIdempotencia)
    );
  }

  reabrirDocumento(id: string, chaveIdempotencia?: string): Observable<DocumentoFinanceiro> {
    return this.http.patch<DocumentoFinanceiro>(
      `${this.api}/documentos/${id}/reabrir`,
      {},
      this.opcoesMutacao(chaveIdempotencia)
    );
  }

  removerDocumento(id: string, chaveIdempotencia?: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/documentos/${id}`, this.opcoesMutacao(chaveIdempotencia));
  }

  baixarDocumento(id: string): Observable<Blob> {
    return this.http.get(`${this.api}/documentos/${id}/conteudo`, { responseType: 'blob' });
  }

  // ── Dashboard executivo ───────────────────────────────────────────────────────

  gerarDashboard(filtro: DashboardFinanceiroFilter): Observable<DashboardFinanceiroResponse> {
    return this.http.get<DashboardFinanceiroResponse>(`${this.api}/dashboards/executivo`, {
      params: toHttpParams(filtro as Record<string, unknown>)
    });
  }

  buscarPrePreenchimento(compraId: string, formularioId: string) {
    return this.http.get<PrePreenchimentoPagamento>(
      `${this.api}/solicitacoes-pagamento/pre-preenchimento`,
      { params: { compraId, formularioId } }
    );
  }

  buscarOpcoesReferencia(
    endpoint: string,
    buscaGlobal: string,
    apenasUtilizaveis = false,
    page = 0,
    size = REMOTE_LOOKUP_PAGE_SIZE
  ): Observable<PagedResponse<ReferenciaOpcao>> {
    const url = endpoint.startsWith('http') ? endpoint : `${environment.apiUrl}${endpoint}`;
    const params: Record<string, string | boolean | number> = { buscaGlobal, page, size };
    if (apenasUtilizaveis) params['apenasUtilizaveis'] = true;
    return this.http.get<unknown>(url, { params }).pipe(
      map((response) => {
        const paged = response as Partial<PagedResponse<unknown>>;
        const items = Array.isArray(response) ? response : (paged.content ?? []);
        const content = items.map((item) =>
          this.toReferenciaOpcao(item as Record<string, unknown>)
        );

        return {
          content,
          page: Array.isArray(response)
            ? {
                number: 0,
                size: content.length,
                totalElements: content.length,
                totalPages: content.length > 0 ? 1 : 0
              }
            : {
                number: paged.page?.number ?? page,
                size: paged.page?.size ?? size,
                totalElements: paged.page?.totalElements ?? content.length,
                totalPages: paged.page?.totalPages ?? (content.length > 0 ? 1 : 0)
              }
        };
      })
    );
  }

  listarFornecedores(filtros: Record<string, unknown>) {
    return this.http.get<FinanceiroPage<Fornecedor>>(`${this.api}/fornecedores`, {
      params: toHttpParams(filtros)
    });
  }

  buscarFornecedorPorId(id: string) {
    return this.http.get<Fornecedor>(`${this.api}/fornecedores/${id}`);
  }

  criarFornecedor(request: FornecedorRequest) {
    return this.http.post<Fornecedor>(`${this.api}/fornecedores`, request);
  }

  atualizarFornecedor(id: string, request: FornecedorRequest) {
    return this.http.put<Fornecedor>(`${this.api}/fornecedores/${id}`, request);
  }

  acaoFornecedor(id: string, acao: 'homologar' | 'desbloquear' | 'ativar') {
    return this.http.patch<Fornecedor>(`${this.api}/fornecedores/${id}/${acao}`, {});
  }

  bloquearFornecedor(id: string, motivo: string) {
    return this.http.patch<Fornecedor>(`${this.api}/fornecedores/${id}/bloquear`, { motivo });
  }

  desativarFornecedor(id: string) {
    return this.http.delete<void>(`${this.api}/fornecedores/${id}`);
  }

  listarFormularios(filtros: Record<string, unknown>) {
    return this.http.get<FinanceiroPage<FormularioResumo>>(`${this.api}/formularios`, {
      params: toHttpParams(filtros)
    });
  }

  criarFormulario(request: Partial<FormularioResumo>) {
    return this.http.post<FormularioSchema>(`${this.api}/formularios`, request);
  }

  atualizarFormulario(id: string, request: Partial<FormularioResumo>) {
    return this.http.put<FormularioSchema>(`${this.api}/formularios/${id}`, request);
  }

  duplicarFormulario(id: string, request: Partial<FormularioResumo>) {
    return this.http.post<FormularioSchema>(`${this.api}/formularios/${id}/duplicar`, request);
  }

  acaoFormulario(id: string, ativar: boolean): Observable<FormularioSchema | void> {
    return ativar
      ? this.http.patch<FormularioSchema>(`${this.api}/formularios/${id}/ativar`, {})
      : this.http.delete<void>(`${this.api}/formularios/${id}`);
  }

  listarCamposCanonicos(id: string) {
    return this.http.get<CampoCanonico[]>(`${this.api}/formularios/${id}/campos-canonicos`);
  }

  criarCampo(id: string, request: CampoRequest) {
    return this.http.post<FormularioSchema>(`${this.api}/formularios/${id}/campos`, request);
  }

  atualizarCampo(id: string, campoId: string, request: CampoRequest) {
    return this.http.put<FormularioSchema>(
      `${this.api}/formularios/${id}/campos/${campoId}`,
      request
    );
  }

  removerCampo(id: string, campoId: string) {
    return this.http.delete<FormularioSchema>(`${this.api}/formularios/${id}/campos/${campoId}`);
  }

  reordenarCampos(id: string, ids: string[]) {
    return this.http.patch<FormularioSchema>(
      `${this.api}/formularios/${id}/campos/ordenacao`,
      { ids }
    );
  }

  private solicitacoesUrl(tipo: TipoSolicitacao): string {
    return `${this.api}/solicitacoes-${tipo === 'COMPRA' ? 'compra' : 'pagamento'}`;
  }

  /**
   * Mantém a busca textual sem espaços artificiais e deixa `HttpParams` fazer o
   * encoding RFC 3986 (em especial `+` e `@` usados em chaves PIX). Uma busca só
   * com espaços não deve aparecer na URL.
   */
  private paramsDaSolicitacao(filtros: SolicitacaoFinanceiraFilter) {
    const normalizados = Object.fromEntries(
      Object.entries(filtros).flatMap(([chave, valor]) => {
        if (typeof valor !== 'string') return [[chave, valor]];
        const texto = valor.trim();
        return texto ? [[chave, texto]] : [];
      })
    );

    return toHttpParams(normalizados);
  }

  private paramsExportacao(filtros: SolicitacaoFinanceiraFilter): HttpParams {
    const normalizados = Object.fromEntries(
      Object.entries(filtros).flatMap(([chave, valor]) => {
        if (typeof valor !== 'string') return [[chave, valor]];
        const texto = valor.trim();
        return texto ? [[chave, texto]] : [];
      })
    );

    return toHttpParams(normalizados, { ignorar: ['page', 'size', 'sort', 'tipo'] });
  }

  private valorDeOrdenacao(item: SolicitacaoFinanceira, campo: string): string | number | null {
    if (campo === 'fornecedor') return item.fornecedor?.nome?.toLocaleLowerCase() ?? null;
    if (!campo.includes('.')) return (item as unknown as Record<string, unknown>)[campo] as string | number | null;
    return campo.split('.').reduce<unknown>((valor, parte) => {
      return valor && typeof valor === 'object'
        ? (valor as Record<string, unknown>)[parte]
        : null;
    }, item) as string | number | null;
  }

  /** Header opcional para callers que precisam reutilizar a mesma chave entre retentativas. */
  private opcoesMutacao(chaveIdempotencia?: string): { headers?: HttpHeaders } {
    return chaveIdempotencia
      ? { headers: new HttpHeaders().set(HEADER_IDEMPOTENCIA, chaveIdempotencia) }
      : {};
  }

  /** O PUT de metadados é substitutivo: campos ausentes devem ser enviados como null. */
  private documentoRequestCompleto(request: DocumentoRequest): DocumentoRequest {
    return {
      tipo: request.tipo ?? null,
      numeroDocumento: request.numeroDocumento ?? null,
      chaveAcesso: request.chaveAcesso ?? null,
      codigoPagamento: request.codigoPagamento ?? null,
      fornecedorId: request.fornecedorId ?? null,
      emissorNome: request.emissorNome ?? null,
      dataEmissao: request.dataEmissao ?? null,
      dataVencimento: request.dataVencimento ?? null,
      competencia: request.competencia ?? null,
      valor: request.valor ?? null,
      baseId: request.baseId ?? null,
      processoId: request.processoId ?? null,
      obraId: request.obraId ?? null,
      descricao: request.descricao ?? null,
      observacoes: request.observacoes ?? null
    };
  }

  private toReferenciaOpcao(item: Record<string, unknown>): ReferenciaOpcao {
    const id = String(item['id'] ?? '');
    const label =
      item['nomeFantasia'] ??
      item['nomeCompleto'] ??
      item['titulo'] ??
      item['login'] ??
      item['email'] ??
      item['nome'] ??
      item['numero'] ??
      item['placa'] ??
      item['numeroContrato'] ??
      id;
    const complemento = item['modelo'] ?? item['cliente'];
    return {
      id,
      label: complemento ? `${String(label)} — ${String(complemento)}` : String(label),
      raw: item
    };
  }
}
