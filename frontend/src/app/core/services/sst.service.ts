import { Injectable } from '@angular/core';
import { HttpEvent, HttpParams } from '@angular/common/http';
import { Observable, from } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import { PagedResponse } from '../models/shared.models';
import {
  AnexoDesvioResponse,
  AtualizarFormularioDadosRequest,
  ContextoAnexo,
  CriarFormularioRequest,
  DashboardDesviosResponse,
  DashboardInspecoesResponse,
  DashboardSstFilter,
  EvidenciaInspecaoResponse,
  FormularioInspecaoResponse,
  FormularioInspecaoResumoResponse,
  InspecaoFilters,
  InspecaoSegurancaResponse,
  RegistrarInspecaoRequest,
  ReordenarRequest,
  TipoInspecaoFilter,
  TipoInspecaoRequest,
  TipoInspecaoResponse,
  TratarDesvioRequest,
  AtualizarCabecalhoInspecaoRequest,
  AtualizarInspecaoRequest,
  HistoricoInspecaoResponse
} from '../models/sst.models';
import {
  MeuDesvioFilter,
  MeuDesvioResponse,
  TratamentoDesviosEmMassaResponse,
  TratarDesviosEmMassaRequest,
  TratarMeuDesvioRequest
} from '../models/meu-desvio.models';
import { DesvioFilter, DesvioDetalhadoResponse } from '../models/desvio.models';
import {
  AnexoEventoResponse,
  AtividadeSstRequest,
  AtividadeSstResponse,
  CatalogoSstRequest,
  CatalogoSstResponse,
  ContextoAnexoEvento,
  DashboardEventosSstFilter,
  DashboardEventosSstResponse,
  ConfirmarImportacaoEventoSstRequest,
  EventoSstFilter,
  ImportacaoEventoSstResponse,
  EventoSstRequest,
  EventoSstResponse,
  EventoSstResumoResponse,
  MotivoEventoRequest,
  TipoCatalogoSst,
  TratativaEventoRequest
} from '../models/sst-evento.models';
import { toHttpParams } from '../utils/http-params.util';

/** `csv` (Excel pt-BR) ou `xlsx` (tipos nativos, cabeçalho congelado e aba "Sobre" com a procedência). */
export type ArquivoExportacao = 'csv' | 'xlsx';

@Injectable({
  providedIn: 'root'
})
export class SstService extends BaseService {
  private readonly tiposInspecaoUrl = `${environment.apiUrl}/v1/sst/tipos-inspecao`;
  private readonly formulariosUrl = `${environment.apiUrl}/v1/sst/formularios-inspecao`;
  private readonly inspecoesUrl = `${environment.apiUrl}/v1/sst/inspecoes`;
  private readonly dashboardsUrl = `${environment.apiUrl}/v1/sst/dashboards`;
  private readonly meusDesviosUrl = `${environment.apiUrl}/v1/sst/desvios/meus`;
  private readonly desviosUrl = `${environment.apiUrl}/v1/sst/desvios`;
  private readonly eventosUrl = `${environment.apiUrl}/v1/sst/eventos`;
  private readonly atividadesUrl = `${environment.apiUrl}/v1/sst/atividades`;
  private readonly catalogosUrl = `${environment.apiUrl}/v1/sst/catalogos`;

  // ==========================================================
  // Tipos de Inspeção (SST)
  // ==========================================================
  listarTiposInspecao(params?: Record<string, unknown> | TipoInspecaoFilter): Observable<PagedResponse<TipoInspecaoResponse>> {
    return this.getList<TipoInspecaoResponse>(this.tiposInspecaoUrl, params as Record<string, unknown>);
  }

  listarOpcoesTiposInspecao(): Observable<TipoInspecaoResponse[]> {
    return this.http.get<TipoInspecaoResponse[]>(`${this.tiposInspecaoUrl}/opcoes`);
  }

  buscarTipoInspecaoPorId(id: string): Observable<TipoInspecaoResponse> {
    return this.getOne<TipoInspecaoResponse>(this.tiposInspecaoUrl, id);
  }

  criarTipoInspecao(request: TipoInspecaoRequest): Observable<TipoInspecaoResponse> {
    return this.http.post<TipoInspecaoResponse>(this.tiposInspecaoUrl, request);
  }

  atualizarTipoInspecao(id: string, request: TipoInspecaoRequest): Observable<TipoInspecaoResponse> {
    return this.http.put<TipoInspecaoResponse>(`${this.tiposInspecaoUrl}/${id}`, request);
  }

  desativarTipoInspecao(id: string): Observable<void> {
    return this.http.patch<void>(`${this.tiposInspecaoUrl}/${id}/status`, {});
  }

  ativarTipoInspecao(id: string): Observable<void> {
    return this.http.patch<void>(`${this.tiposInspecaoUrl}/${id}/status`, {});
  }

  /** Listagem geral (SESMT) — todos os desvios da empresa. Sem cache offline: fila de cobrança envelhece mal. */
  listarDesvios(filtros: DesvioFilter): Observable<PagedResponse<DesvioDetalhadoResponse>> {
    return this.getList<DesvioDetalhadoResponse>(this.desviosUrl, filtros);
  }

  /** Recarrega uma linha após tratamento — o PUT de tratamento devolve a inspeção, não o desvio. */
  buscarDesvioPorId(id: string): Observable<DesvioDetalhadoResponse> {
    return this.getOne<DesvioDetalhadoResponse>(this.desviosUrl, id);
  }

  // ==========================================================
  // Eventos de segurança (acidentalidade)
  //
  // Sem cache offline e sem fila de sincronização: o acidente é registrado da base,
  // no desktop, depois do fato — não existe aqui o cenário de campo que justifica o
  // offline-first das inspeções. O `Idempotency-Key` do interceptor já protege contra
  // o duplo envio, que é o risco real desta tela.
  // ==========================================================
  listarEventos(filtros: EventoSstFilter): Observable<PagedResponse<EventoSstResumoResponse>> {
    return this.getList<EventoSstResumoResponse>(this.eventosUrl, filtros);
  }

  buscarEventoPorId(id: string): Observable<EventoSstResponse> {
    return this.getOne<EventoSstResponse>(this.eventosUrl, id);
  }

  registrarEvento(request: EventoSstRequest): Observable<EventoSstResponse> {
    return this.http.post<EventoSstResponse>(this.eventosUrl, request);
  }

  importarEventos(arquivo: File): Observable<ImportacaoEventoSstResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo, arquivo.name);
    return this.http.post<ImportacaoEventoSstResponse>(`${this.eventosUrl}/importar`, formData);
  }

  confirmarImportacaoEventos(
    request: ConfirmarImportacaoEventoSstRequest
  ): Observable<ImportacaoEventoSstResponse> {
    return this.http.post<ImportacaoEventoSstResponse>(
      `${this.eventosUrl}/confirmar-importacao`,
      request
    );
  }

  atualizarEvento(id: string, request: EventoSstRequest): Observable<EventoSstResponse> {
    return this.http.put<EventoSstResponse>(`${this.eventosUrl}/${id}`, request);
  }

  iniciarTratativaEvento(id: string, request: TratativaEventoRequest): Observable<EventoSstResponse> {
    return this.http.put<EventoSstResponse>(`${this.eventosUrl}/${id}/tratativa`, request);
  }

  concluirEvento(id: string, request: TratativaEventoRequest): Observable<EventoSstResponse> {
    return this.http.put<EventoSstResponse>(`${this.eventosUrl}/${id}/conclusao`, request);
  }

  cancelarEvento(id: string, request: MotivoEventoRequest): Observable<EventoSstResponse> {
    return this.http.put<EventoSstResponse>(`${this.eventosUrl}/${id}/cancelamento`, request);
  }

  reabrirEvento(id: string, request: MotivoEventoRequest): Observable<EventoSstResponse> {
    return this.http.put<EventoSstResponse>(`${this.eventosUrl}/${id}/reabertura`, request);
  }

  desativarEvento(id: string): Observable<void> {
    return this.http.delete<void>(`${this.eventosUrl}/${id}`);
  }

  // ----- Evidências do evento (upload pós-registro) -----
  anexarEvidenciaEvento(
    idEvento: string,
    contexto: ContextoAnexoEvento,
    arquivo: File
  ): Observable<AnexoEventoResponse> {
    const fd = new FormData();
    fd.append('arquivo', arquivo, arquivo.name);
    return this.http.post<AnexoEventoResponse>(`${this.eventosUrl}/${idEvento}/anexos`, fd, {
      params: new HttpParams().set('contexto', contexto)
    });
  }

  listarAnexosEvento(idEvento: string): Observable<AnexoEventoResponse[]> {
    return this.http.get<AnexoEventoResponse[]>(`${this.eventosUrl}/${idEvento}/anexos`);
  }

  /** Baixa o binário (autenticado via interceptor — não há URL pública). */
  baixarAnexoEvento(idEvento: string, idAnexo: string): Observable<Blob> {
    return this.http.get(`${this.eventosUrl}/${idEvento}/anexos/${idAnexo}`, { responseType: 'blob' });
  }

  removerAnexoEvento(idEvento: string, idAnexo: string): Observable<void> {
    return this.http.delete<void>(`${this.eventosUrl}/${idEvento}/anexos/${idAnexo}`);
  }

  // ----- Dashboard e exportação -----
  gerarDashboardEventos(filter: DashboardEventosSstFilter): Observable<DashboardEventosSstResponse> {
    return this.http.get<DashboardEventosSstResponse>(`${this.dashboardsUrl}/eventos`, {
      params: toHttpParams(filter as Record<string, unknown>)
    });
  }

  /** Mesma consulta da listagem — a planilha traz exatamente as linhas da tela. */
  exportarEventos(filtros: EventoSstFilter): Observable<Blob> {
    return this.http.get(`${this.eventosUrl}/exportar`, {
      params: toHttpParams(filtros as Record<string, unknown>, { ignorar: ['page', 'size', 'sort'] }),
      responseType: 'blob'
    });
  }

  // ==========================================================
  // Cadastros da governança de eventos
  // ==========================================================
  listarAtividadesSst(params: Record<string, unknown>): Observable<PagedResponse<AtividadeSstResponse>> {
    return this.getList<AtividadeSstResponse>(this.atividadesUrl, params);
  }

  /** Ativas, em ordem alfabética — abastece o formulário de registro. */
  listarOpcoesAtividades(): Observable<AtividadeSstResponse[]> {
    return this.http.get<AtividadeSstResponse[]>(`${this.atividadesUrl}/opcoes`);
  }

  criarAtividadeSst(request: AtividadeSstRequest): Observable<AtividadeSstResponse> {
    return this.http.post<AtividadeSstResponse>(this.atividadesUrl, request);
  }

  atualizarAtividadeSst(id: string, request: AtividadeSstRequest): Observable<AtividadeSstResponse> {
    return this.http.put<AtividadeSstResponse>(`${this.atividadesUrl}/${id}`, request);
  }

  desativarAtividadeSst(id: string): Observable<void> {
    return this.http.delete<void>(`${this.atividadesUrl}/${id}`);
  }

  ativarAtividadeSst(id: string): Observable<void> {
    return this.http.put<void>(`${this.atividadesUrl}/${id}/ativar`, {});
  }

  listarCatalogosSst(params: Record<string, unknown>): Observable<PagedResponse<CatalogoSstResponse>> {
    return this.getList<CatalogoSstResponse>(this.catalogosUrl, params);
  }

  listarOpcoesCatalogo(tipo: TipoCatalogoSst): Observable<CatalogoSstResponse[]> {
    return this.http.get<CatalogoSstResponse[]>(`${this.catalogosUrl}/opcoes`, {
      params: new HttpParams().set('tipo', tipo)
    });
  }

  criarCatalogoSst(request: CatalogoSstRequest): Observable<CatalogoSstResponse> {
    return this.http.post<CatalogoSstResponse>(this.catalogosUrl, request);
  }

  atualizarCatalogoSst(id: string, request: CatalogoSstRequest): Observable<CatalogoSstResponse> {
    return this.http.put<CatalogoSstResponse>(`${this.catalogosUrl}/${id}`, request);
  }

  desativarCatalogoSst(id: string): Observable<void> {
    return this.http.delete<void>(`${this.catalogosUrl}/${id}`);
  }

  ativarCatalogoSst(id: string): Observable<void> {
    return this.http.put<void>(`${this.catalogosUrl}/${id}/ativar`, {});
  }

  // ==========================================================
  // Formulários (construtor de formulários dinâmicos)
  // ==========================================================
  listarFormularios(params: any): Observable<PagedResponse<FormularioInspecaoResumoResponse>> {
    return this.getList<FormularioInspecaoResumoResponse>(this.formulariosUrl, params);
  }

  listarFormulariosAtivos(): Observable<PagedResponse<FormularioInspecaoResumoResponse>> {
    return this.getList<FormularioInspecaoResumoResponse>(this.formulariosUrl, { ativo: true, size: 1000 });
  }

  buscarFormularioPorId(id: string): Observable<FormularioInspecaoResponse> {
    return this.getOne<FormularioInspecaoResponse>(this.formulariosUrl, id);
  }

  criarFormulario(request: CriarFormularioRequest): Observable<FormularioInspecaoResponse | null> {
    return this.createSync<FormularioInspecaoResponse>(this.formulariosUrl, request);
  }

  /** Substitui a árvore inteira — bloqueado (422) quando o formulário está em uso. */
  atualizarEstrutura(id: string, request: CriarFormularioRequest): Observable<FormularioInspecaoResponse | null> {
    return this.updateSync<FormularioInspecaoResponse>(this.formulariosUrl, `${id}/estrutura`, request);
  }

  /** Atualiza apenas título/descrição — sempre permitido. */
  atualizarDados(id: string, request: AtualizarFormularioDadosRequest): Observable<FormularioInspecaoResponse | null> {
    return from(this.sync.execute<FormularioInspecaoResponse>('PATCH', `${this.formulariosUrl}/${id}`, request));
  }

  duplicarFormulario(id: string): Observable<FormularioInspecaoResponse | null> {
    return this.createSync<FormularioInspecaoResponse>(`${this.formulariosUrl}/${id}/duplicar`, {});
  }

  desativarFormulario(id: string): Observable<void | null> {
    return this.deleteSync(this.formulariosUrl, id);
  }

  ativarFormulario(id: string): Observable<void | null> {
    return this.updateSync<void>(this.formulariosUrl, `${id}/ativar`, {});
  }

  // ----- Reordenação dinâmica (drag-and-drop) -----
  reordenarGrupos(id: string, request: ReordenarRequest): Observable<FormularioInspecaoResponse | null> {
    return from(this.sync.execute<FormularioInspecaoResponse>('PATCH', `${this.formulariosUrl}/${id}/grupos/ordenacao`, request));
  }

  reordenarPerguntas(id: string, idGrupo: string, request: ReordenarRequest): Observable<FormularioInspecaoResponse | null> {
    return from(this.sync.execute<FormularioInspecaoResponse>('PATCH', `${this.formulariosUrl}/${id}/grupos/${idGrupo}/perguntas/ordenacao`, request));
  }

  reordenarOpcoes(id: string, idGrupo: string, idPergunta: string, request: ReordenarRequest): Observable<FormularioInspecaoResponse | null> {
    return from(this.sync.execute<FormularioInspecaoResponse>('PATCH', `${this.formulariosUrl}/${id}/grupos/${idGrupo}/perguntas/${idPergunta}/opcoes/ordenacao`, request));
  }

  // ==========================================================
  // Inspeções (lançamento offline-first + tratamento de desvios)
  // ==========================================================
  listarInspecoes(filters: InspecaoFilters): Observable<PagedResponse<InspecaoSegurancaResponse>> {
    return this.getList<InspecaoSegurancaResponse>(this.inspecoesUrl, filters);
  }

  listarMinhasInspecoes(filters: InspecaoFilters): Observable<PagedResponse<InspecaoSegurancaResponse>> {
    return this.getList<InspecaoSegurancaResponse>(`${this.inspecoesUrl}/minhas`, filters);
  }

  buscarInspecaoPorId(id: string): Observable<InspecaoSegurancaResponse> {
    return this.getOne<InspecaoSegurancaResponse>(this.inspecoesUrl, id);
  }

  /** Lança a inspeção inteira (payload único). Suporta fila offline via SyncService. */
  registrarInspecao(request: RegistrarInspecaoRequest): Observable<InspecaoSegurancaResponse | null> {
    return this.createSync<InspecaoSegurancaResponse>(this.inspecoesUrl, request);
  }

  tratarDesvio(
    idInspecao: string,
    idDesvio: string,
    request: TratarDesvioRequest
  ): Observable<InspecaoSegurancaResponse | null> {
    return this.updateSync<InspecaoSegurancaResponse>(
      this.inspecoesUrl,
      `${idInspecao}/desvios/${idDesvio}/tratamento`,
      request
    );
  }

  /** Atualiza dados do cabeçalho da inspeção e colaboradores. Ação restrita ao criador da inspeção. */
  atualizarCabecalhoInspecao(
    id: string,
    request: AtualizarCabecalhoInspecaoRequest
  ): Observable<InspecaoSegurancaResponse | null> {
    return this.updateSync<InspecaoSegurancaResponse>(
      this.inspecoesUrl,
      `${id}/cabecalho`,
      request
    );
  }

  /** Atualiza completamente a inspeção de segurança (cabeçalho, checklist, desvios e evidências). */
  atualizarInspecao(
    id: string,
    request: AtualizarInspecaoRequest
  ): Observable<InspecaoSegurancaResponse | null> {
    return this.updateSync<InspecaoSegurancaResponse>(
      this.inspecoesUrl,
      id,
      request
    );
  }

  /** Consulta o histórico de alterações cronológico e verificável da inspeção. */
  obterHistoricoInspecao(
    id: string,
    page: number = 0,
    size: number = 20
  ): Observable<HistoricoInspecaoResponse> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<HistoricoInspecaoResponse>(`${this.inspecoesUrl}/${id}/historico`, { params });
  }

  listarMeusDesvios(filters: MeuDesvioFilter): Observable<PagedResponse<MeuDesvioResponse>> {
    return this.getList<MeuDesvioResponse>(this.meusDesviosUrl, filters);
  }

  tratarMeuDesvio(
    idDesvio: string,
    request: TratarMeuDesvioRequest
  ): Observable<MeuDesvioResponse> {
    return this.http.put<MeuDesvioResponse>(
      `${this.meusDesviosUrl}/${idDesvio}/tratamento`,
      request
    );
  }

  tratarMeusDesviosEmMassa(
    request: TratarDesviosEmMassaRequest
  ): Observable<TratamentoDesviosEmMassaResponse> {
    return this.http.put<TratamentoDesviosEmMassaResponse>(
      `${this.meusDesviosUrl}/tratamento-em-massa`,
      request
    );
  }

  // ==========================================================
  // Evidências fotográficas da inspeção (obrigatórias, ≥ 1)
  // ==========================================================
  /**
   * Sobe **uma** foto antes do lançamento e devolve o `id` que vai em
   * `RegistrarInspecaoRequest.evidencias`. Uma foto por requisição: cada uma tem
   * retentativa própria e o `POST` do lançamento continua pequeno e atômico.
   *
   * Emite eventos de progresso (`observe: 'events'`) para a barra por arquivo.
   * A foto fica PENDENTE e é expurgada em 72 h se nunca for usada.
   */
  enviarEvidenciaPendente(arquivo: File): Observable<HttpEvent<EvidenciaInspecaoResponse>> {
    const fd = new FormData();
    fd.append('arquivo', arquivo, arquivo.name);
    return this.http.post<EvidenciaInspecaoResponse>(`${this.inspecoesUrl}/anexos`, fd, {
      reportProgress: true,
      observe: 'events'
    });
  }

  /** Descarta uma foto ainda pendente (não vinculada a nenhuma inspeção). */
  descartarEvidenciaPendente(idAnexo: string): Observable<void> {
    return this.http.delete<void>(`${this.inspecoesUrl}/anexos/${idAnexo}`);
  }

  /** Acrescenta uma evidência a uma inspeção já lançada. */
  adicionarEvidenciaInspecao(idInspecao: string, arquivo: File): Observable<EvidenciaInspecaoResponse> {
    const fd = new FormData();
    fd.append('arquivo', arquivo, arquivo.name);
    return this.http.post<EvidenciaInspecaoResponse>(
      `${this.inspecoesUrl}/${idInspecao}/anexos`,
      fd
    );
  }

  /** Baixa o binário da evidência (autenticado via interceptor — não há URL pública). */
  baixarEvidenciaInspecao(idInspecao: string, idAnexo: string): Observable<Blob> {
    return this.http.get(`${this.inspecoesUrl}/${idInspecao}/anexos/${idAnexo}`, {
      responseType: 'blob'
    });
  }

  /** Remove uma evidência da inspeção. A **última** é recusada com 422. */
  removerEvidenciaInspecao(idInspecao: string, idAnexo: string): Observable<void> {
    return this.http.delete<void>(`${this.inspecoesUrl}/${idInspecao}/anexos/${idAnexo}`);
  }

  // ==========================================================
  // Anexos / evidências de desvios (proxy R2)
  // ==========================================================
  private anexosUrl(idInspecao: string, idDesvio: string): string {
    return `${this.inspecoesUrl}/${idInspecao}/desvios/${idDesvio}/anexos`;
  }

  /**
   * Envia uma evidência (foto ou documento) via multipart/form-data.
   * O `Content-Type` é deixado a cargo do browser (boundary automático).
   */
  enviarAnexo(
    idInspecao: string,
    idDesvio: string,
    contexto: ContextoAnexo,
    arquivo: File
  ): Observable<AnexoDesvioResponse> {
    const fd = new FormData();
    fd.append('contexto', contexto);
    fd.append('arquivo', arquivo);
    return this.http.post<AnexoDesvioResponse>(this.anexosUrl(idInspecao, idDesvio), fd);
  }

  /** Baixa o binário de uma evidência (autenticado via interceptor). */
  baixarAnexo(idInspecao: string, idDesvio: string, idAnexo: string): Observable<Blob> {
    return this.http.get(`${this.anexosUrl(idInspecao, idDesvio)}/${idAnexo}`, {
      responseType: 'blob'
    });
  }

  /** Remove uma evidência (do banco e do R2). */
  removerAnexo(idInspecao: string, idDesvio: string, idAnexo: string): Observable<void> {
    return this.http.delete<void>(`${this.anexosUrl(idInspecao, idDesvio)}/${idAnexo}`);
  }

  // ==========================================================
  // Dashboards executivos
  // ==========================================================
  gerarDashboardInspecoes(filter: DashboardSstFilter): Observable<DashboardInspecoesResponse> {
    return this.http.get<DashboardInspecoesResponse>(`${this.dashboardsUrl}/inspecoes`, {
      params: this.toDashboardParams(filter, false)
    });
  }

  gerarDashboardDesvios(filter: DashboardSstFilter): Observable<DashboardDesviosResponse> {
    return this.http.get<DashboardDesviosResponse>(`${this.dashboardsUrl}/desvios`, {
      params: this.toDashboardParams(filter, true)
    });
  }

  private toDashboardParams(filter: DashboardSstFilter, includeDeviationFilters: boolean): HttpParams {
    const commonKeys: (keyof DashboardSstFilter)[] = [
      'inicio', 'fim', 'idTipoInspecao', 'idProcesso', 'idEquipe', 'idBaseOperacional', 'idCentroCusto',
      'idInspetor', 'idFormulario'
    ];
    // Ambos os dashboards aceitam granularidade; gravidade/status existem apenas no de desvios.
    const keys = includeDeviationFilters
      ? [...commonKeys, 'granularidade', 'gravidade', 'status'] as (keyof DashboardSstFilter)[]
      : [...commonKeys, 'granularidade'] as (keyof DashboardSstFilter)[];
    const selecionados = Object.fromEntries(keys.map((key) => [key, filter[key]]));
    return toHttpParams(selecionados);
  }

  // ==========================================================
  // Exportação (CSV)
  // ==========================================================
  exportarInspecoesResumo(filtros: InspecaoFilters, arquivo: ArquivoExportacao = 'csv'): Observable<Blob> {
    return this.http.get(`${this.inspecoesUrl}/exportar/resumo`, {
      params: this.buildExportParams(filtros).set('formato', arquivo),
      responseType: 'blob'
    });
  }

  exportarInspecoesDetalhado(filtros: InspecaoFilters, arquivo: ArquivoExportacao = 'csv'): Observable<Blob> {
    return this.http.get(`${this.inspecoesUrl}/exportar/detalhado`, {
      params: this.buildExportParams(filtros).set('formato', arquivo),
      responseType: 'blob'
    });
  }

  exportarDesviosResumo(filtros: InspecaoFilters, arquivo: ArquivoExportacao = 'csv'): Observable<Blob> {
    return this.http.get(`${this.inspecoesUrl}/desvios/exportar/resumo`, {
      params: this.buildExportParams(filtros).set('formato', arquivo),
      responseType: 'blob'
    });
  }

  exportarDesviosDetalhado(filtros: InspecaoFilters, arquivo: ArquivoExportacao = 'csv'): Observable<Blob> {
    return this.http.get(`${this.inspecoesUrl}/desvios/exportar/detalhado`, {
      params: this.buildExportParams(filtros).set('formato', arquivo),
      responseType: 'blob'
    });
  }

  private buildExportParams(filtros: InspecaoFilters): HttpParams {
    return toHttpParams(filtros as Record<string, unknown>, {
      // A exportação não pagina.
      ignorar: ['page', 'size', 'sort'],
      transformar: (chave, valor) =>
        // Garante formato ISO 8601 completo (com timezone Z ou offset).
        chave === 'dataRealizacaoDe' || chave === 'dataRealizacaoAte'
          ? new Date(valor as string).toISOString()
          : valor
    });
  }
}
