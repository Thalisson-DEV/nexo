import { Injectable } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import { PagedResponse } from '../models/shared.models';
import {
  AcaoGovernancaResumo,
  AtualizarCorpoGovernancaRequest,
  CategoriaDocumentoGovernanca,
  CelulaMatrizTreinamento,
  CorpoGovernancaDetalhe,
  CorpoGovernancaResumo,
  CriarCorpoGovernancaRequest,
  InclusaoMembroCorpoGovernancaRequest,
  MembroCorpoGovernanca,
  DashboardGovernanca,
  DocumentoGovernancaResumo,
  EvidenciaGovernanca,
  ModeloAtaGovernanca,
  ModeloChecklistGovernanca,
  ReuniaoGovernancaResumo,
  TreinamentoGovernanca,
  ParticipacaoReuniaoGovernanca,
  VersaoAtaGovernanca,
  NormalizacaoAtaGovernanca,
  PerfilNormativoGovernanca,
  SaidaMembroCorpoGovernancaRequest,
  SubstituicaoMembroCorpoGovernancaRequest,
  ResumoDocumentoGovernanca
} from '../models/governanca-sst.models';
import { toHttpParams } from '../utils/http-params.util';

/**
 * Cliente do prontuário de CIPA e Brigada.
 *
 * Registros legais, transcrições e arquivos não entram no cache/fila offline genérica:
 * uma resposta 409 ou uma revisão humana não pode ser silenciosamente reprocessada.
 */
@Injectable({ providedIn: 'root' })
export class GovernancaSstService extends BaseService {
  private readonly url = `${environment.apiUrl}/v1/sst/governanca`;

  dashboard(filtros: Record<string, unknown>): Observable<DashboardGovernanca> {
    return this.http.get<DashboardGovernanca>(`${this.url}/dashboard`, {
      params: toHttpParams(filtros)
    });
  }

  listarCorpos(filtros: Record<string, unknown>): Observable<PagedResponse<CorpoGovernancaResumo>> {
    return this.getList<CorpoGovernancaResumo>(`${this.url}/corpos`, filtros);
  }

  buscarCorpo(id: string): Observable<CorpoGovernancaDetalhe> {
    return this.http.get<CorpoGovernancaDetalhe>(`${this.url}/corpos/${id}`);
  }

  criarCorpo(request: CriarCorpoGovernancaRequest): Observable<CorpoGovernancaDetalhe> {
    return this.http.post<CorpoGovernancaDetalhe>(`${this.url}/corpos`, request);
  }

  listarPerfisNormativos(
    filtros: Record<string, unknown>
  ): Observable<PagedResponse<PerfilNormativoGovernanca>> {
    return this.getList<PerfilNormativoGovernanca>(`${this.url}/perfis-normativos`, filtros);
  }

  atualizarCorpo(
    id: string,
    request: AtualizarCorpoGovernancaRequest
  ): Observable<CorpoGovernancaDetalhe> {
    return this.http.put<CorpoGovernancaDetalhe>(`${this.url}/corpos/${id}`, request);
  }

  adicionarMembro(
    idCorpo: string,
    request: InclusaoMembroCorpoGovernancaRequest
  ): Observable<MembroCorpoGovernanca> {
    return this.http.post<MembroCorpoGovernanca>(`${this.url}/corpos/${idCorpo}/membros`, request);
  }

  registrarSaidaMembro(
    idCorpo: string,
    idMembro: string,
    request: SaidaMembroCorpoGovernancaRequest
  ): Observable<MembroCorpoGovernanca> {
    return this.http.post<MembroCorpoGovernanca>(
      `${this.url}/corpos/${idCorpo}/membros/${idMembro}/saida`,
      request
    );
  }

  substituirMembro(
    idCorpo: string,
    idMembro: string,
    request: SubstituicaoMembroCorpoGovernancaRequest
  ): Observable<MembroCorpoGovernanca> {
    return this.http.post<MembroCorpoGovernanca>(
      `${this.url}/corpos/${idCorpo}/membros/${idMembro}/substituicao`,
      request
    );
  }

  ativarCorpo(id: string, versao: number): Observable<CorpoGovernancaDetalhe> {
    return this.http.post<CorpoGovernancaDetalhe>(`${this.url}/corpos/${id}/ativacao`, { versao });
  }

  encerrarCorpo(id: string, request: Record<string, unknown>): Observable<CorpoGovernancaDetalhe> {
    return this.http.post<CorpoGovernancaDetalhe>(`${this.url}/corpos/${id}/encerramento`, request);
  }

  listarReunioes(
    filtros: Record<string, unknown>
  ): Observable<PagedResponse<ReuniaoGovernancaResumo>> {
    return this.getList<ReuniaoGovernancaResumo>(`${this.url}/reunioes`, filtros);
  }

  buscarReuniao(id: string): Observable<ReuniaoGovernancaResumo> {
    return this.http.get<ReuniaoGovernancaResumo>(`${this.url}/reunioes/${id}`);
  }

  criarReuniao(request: Record<string, unknown>): Observable<ReuniaoGovernancaResumo> {
    return this.http.post<ReuniaoGovernancaResumo>(`${this.url}/reunioes`, request);
  }

  executarComandoReuniao<T>(
    id: string,
    comando: string,
    request: Record<string, unknown>
  ): Observable<T> {
    return this.http.post<T>(`${this.url}/reunioes/${id}/${comando}`, request);
  }

  listarParticipacoes(id: string): Observable<ParticipacaoReuniaoGovernanca[]> {
    return this.http.get<ParticipacaoReuniaoGovernanca[]>(`${this.url}/reunioes/${id}/participacoes`);
  }

  registrarPresenca(id: string, idParticipacao: string, request: Record<string, unknown>) {
    return this.http.put<ParticipacaoReuniaoGovernanca>(
      `${this.url}/reunioes/${id}/participacoes/${idParticipacao}/presenca`,
      request
    );
  }

  concluirPresencas(id: string) {
    return this.http.post(`${this.url}/reunioes/${id}/presencas/conclusao`, {});
  }

  adicionarConvidado(id: string, request: Record<string, unknown>) {
    return this.http.post(`${this.url}/reunioes/${id}/convidados`, request);
  }

  designarSecretario(id: string, tipo: 'PARTICIPANTE' | 'CONVIDADO', idPessoaNaReuniao: string) {
    return this.http.put(`${this.url}/reunioes/${id}/secretario`, { tipo, idPessoaNaReuniao });
  }

  anexarFotoReuniao(id: string, arquivo: File, legenda?: string): Observable<EvidenciaGovernanca> {
    const dados = new FormData();
    dados.append('arquivo', arquivo, arquivo.name);
    if (legenda?.trim()) dados.append('legenda', legenda.trim());
    return this.http.post<EvidenciaGovernanca>(`${this.url}/reunioes/${id}/evidencias`, dados);
  }

  listarFotosReuniao(id: string): Observable<EvidenciaGovernanca[]> {
    return this.http.get<EvidenciaGovernanca[]>(`${this.url}/reunioes/${id}/evidencias`);
  }

  baixarFotoReuniao(id: string, idFoto: string) {
    return this.http.get(`${this.url}/reunioes/${id}/evidencias/${idFoto}/arquivo`, { responseType: 'blob' });
  }

  removerFotoReuniao(id: string, idFoto: string) {
    return this.http.delete<void>(`${this.url}/reunioes/${id}/evidencias/${idFoto}`);
  }

  salvarTranscricao(id: string, request: Record<string, unknown>) {
    return this.http.post(`${this.url}/reunioes/${id}/transcricao`, request);
  }

  listarTranscricoes(id: string) {
    return this.http.get<Array<Record<string, unknown>>>(`${this.url}/reunioes/${id}/transcricoes`);
  }

  confirmarTranscricao(id: string, idRevisao: string) {
    return this.http.post(`${this.url}/reunioes/${id}/transcricoes/${idRevisao}/confirmacao`, {});
  }

  normalizarTranscricao(id: string, request: Record<string, unknown>) {
    return this.http.post<NormalizacaoAtaGovernanca>(`${this.url}/reunioes/${id}/normalizacoes`, request);
  }

  listarNormalizacoes(id: string) {
    return this.http.get<NormalizacaoAtaGovernanca[]>(`${this.url}/reunioes/${id}/normalizacoes`);
  }

  confirmarNormalizacao(id: string, idNormalizacao: string) {
    return this.http.post<NormalizacaoAtaGovernanca>(
      `${this.url}/reunioes/${id}/normalizacoes/${idNormalizacao}/confirmacao`,
      {}
    );
  }

  listarVersoesAta(id: string) {
    return this.http.get<VersaoAtaGovernanca[]>(`${this.url}/reunioes/${id}/atas`);
  }

  criarAtaManual(id: string, request: Record<string, unknown>) {
    return this.http.post<VersaoAtaGovernanca>(`${this.url}/reunioes/${id}/atas/manual`, request);
  }

  gerarAta(id: string, request: Record<string, unknown>) {
    return this.http.post<Record<string, unknown>>(`${this.url}/reunioes/${id}/atas/geracao`, request);
  }

  aprovarAta(id: string, idVersao: string) {
    return this.http.post<VersaoAtaGovernanca>(
      `${this.url}/reunioes/${id}/atas/${idVersao}/aprovacao`,
      {}
    );
  }

  gerarDocxAta(id: string, idVersao: string) {
    return this.http.post<Record<string, unknown>>(`${this.url}/reunioes/${id}/atas/${idVersao}/docx`, {});
  }

  baixarDocxAta(id: string, idArquivo: string) {
    return this.http.get(`${this.url}/reunioes/${id}/atas/docx/${idArquivo}/arquivo`, { responseType: 'blob' });
  }

  anexarCopiaAssinada(id: string, idVersao: string, arquivo: File) {
    const form = new FormData();
    form.append('arquivo', arquivo, arquivo.name);
    return this.http.post(`${this.url}/reunioes/${id}/atas/${idVersao}/copia-assinada`, form);
  }

  baixarCopiaAssinada(id: string, idVersao: string) {
    return this.http.get(`${this.url}/reunioes/${id}/atas/${idVersao}/copia-assinada/arquivo`, { responseType: 'blob' });
  }

  finalizarReuniao(id: string) {
    return this.http.post<void>(`${this.url}/reunioes/${id}/finalizacao`, {});
  }

  reabrirReuniao(id: string, motivo: string) {
    return this.http.post<void>(`${this.url}/reunioes/${id}/reabertura`, { motivo });
  }

  buscarCapabilitiesReuniao(): Observable<any> {
    return this.http.get<any>(`${this.url}/reunioes/capabilities`);
  }

  listarModelosAta(filtros: Record<string, unknown>): Observable<ModeloAtaGovernanca[]> {
    return this.http.get<ModeloAtaGovernanca[]>(`${this.url}/modelos-ata`, {
      params: toHttpParams(filtros)
    });
  }

  listarModelosAtaAplicaveis(filtros: Record<string, unknown>): Observable<ModeloAtaGovernanca[]> {
    return this.http.get<ModeloAtaGovernanca[]>(`${this.url}/modelos-ata/aplicaveis`, {
      params: toHttpParams(filtros)
    });
  }

  buscarModeloAta(id: string): Observable<ModeloAtaGovernanca> {
    return this.http.get<ModeloAtaGovernanca>(`${this.url}/modelos-ata/${id}`);
  }

  salvarModeloAta(request: Record<string, unknown>, id?: string): Observable<ModeloAtaGovernanca> {
    return id
      ? this.http.put<ModeloAtaGovernanca>(`${this.url}/modelos-ata/${id}`, request)
      : this.http.post<ModeloAtaGovernanca>(`${this.url}/modelos-ata`, request);
  }

  comandoModeloAta(
    id: string,
    comando: 'duplicacao' | 'nova-revisao' | 'publicacao' | 'retirada',
    request: Record<string, unknown> = {}
  ): Observable<ModeloAtaGovernanca> {
    return this.http.post<ModeloAtaGovernanca>(`${this.url}/modelos-ata/${id}/${comando}`, request);
  }



  enviarBaseModeloAta(id: string, arquivo: File, mapeamentoSlots?: string) {
    const form = new FormData();
    form.append('arquivo', arquivo, arquivo.name);
    if (mapeamentoSlots) form.append('mapeamentoSlots', mapeamentoSlots);
    return this.http.post(`${this.url}/modelos-ata/${id}/base-docx`, form);
  }

  listarCategoriasDocumento(
    filtros: Record<string, unknown>
  ): Observable<PagedResponse<CategoriaDocumentoGovernanca>> {
    return this.getList<CategoriaDocumentoGovernanca>(`${this.url}/categorias-documento`, filtros);
  }

  salvarCategoriaDocumento(request: Record<string, unknown>, id?: string) {
    return id
      ? this.http.post(`${this.url}/categorias-documento/${id}/nova-versao`, request)
      : this.http.post(`${this.url}/categorias-documento`, request);
  }

  listarDocumentos(
    filtros: Record<string, unknown>
  ): Observable<PagedResponse<DocumentoGovernancaResumo>> {
    return this.getList<DocumentoGovernancaResumo>(`${this.url}/documentos`, filtros);
  }

  resumoDocumentos(filtros: Record<string, unknown>): Observable<ResumoDocumentoGovernanca> {
    return this.http.get<ResumoDocumentoGovernanca>(`${this.url}/documentos/resumo`, {
      params: toHttpParams(filtros)
    });
  }

  historicoDocumento(id: string) {
    return this.http.get<DocumentoGovernancaResumo[]>(`${this.url}/documentos/${id}/historico`);
  }

  enviarDocumento(arquivo: File, dados: Record<string, unknown>): Observable<DocumentoGovernancaResumo> {
    const form = new FormData();
    form.append('arquivo', arquivo, arquivo.name);
    form.append('dados', new Blob([JSON.stringify(dados)], { type: 'application/json' }));
    return this.http.post<DocumentoGovernancaResumo>(`${this.url}/documentos`, form);
  }

  conferirDocumento(id: string, request: Record<string, unknown>) {
    return this.http.post<DocumentoGovernancaResumo>(`${this.url}/documentos/${id}/conferencia`, request);
  }

  baixarDocumento(id: string): Observable<Blob> {
    return this.http.get(`${this.url}/documentos/${id}/arquivo`, { responseType: 'blob' });
  }

  listarTreinamentos(
    filtros: Record<string, unknown>
  ): Observable<PagedResponse<TreinamentoGovernanca>> {
    return this.getList<TreinamentoGovernanca>(`${this.url}/treinamentos`, filtros);
  }

  salvarTreinamento(request: Record<string, unknown>, id?: string) {
    return id
      ? this.http.put(`${this.url}/treinamentos/${id}`, request)
      : this.http.post(`${this.url}/treinamentos`, request);
  }

  listarExigencias(filtros: Record<string, unknown>) {
    return this.getList<Record<string, unknown>>(`${this.url}/exigencias-treinamento`, filtros);
  }

  salvarExigencia(request: Record<string, unknown>, id?: string) {
    return id
      ? this.http.post(`${this.url}/exigencias-treinamento/${id}/nova-versao`, request)
      : this.http.post(`${this.url}/exigencias-treinamento`, request);
  }

  confirmarFonteExigencia(id: string) {
    return this.http.post(`${this.url}/exigencias-treinamento/${id}/confirmacao-fonte`, {});
  }

  resumoMatriz(filtros: Record<string, unknown>) {
    return this.http.get<Record<string, number>>(`${this.url}/matriz/resumo`, {
      params: toHttpParams(filtros)
    });
  }

  listarInstituicoes() {
    return this.http.get<Array<Record<string, unknown>>>(`${this.url}/instituicoes-treinamento`);
  }

  salvarInstituicao(request: Record<string, unknown>) {
    return this.http.post(`${this.url}/instituicoes-treinamento`, request);
  }

  confirmarHomologacao(idInstituicao: string, id: string, request: Record<string, unknown>) {
    return this.http.post(`${this.url}/instituicoes-treinamento/${idInstituicao}/homologacoes/${id}/confirmacao`, request);
  }

  listarProcessosTreinamento() {
    return this.http.get<Array<Record<string, unknown>>>(`${this.url}/processos-treinamento`);
  }

  listarAtivos(idBase: string) {
    return this.http.get<Array<Record<string, unknown>>>(`${this.url}/ativos-inspecionaveis`, {
      params: { idBase }
    });
  }

  salvarAtivo(request: Record<string, unknown>) {
    return this.http.post(`${this.url}/ativos-inspecionaveis`, request);
  }

  confirmarAtivo(id: string) {
    return this.http.post(`${this.url}/ativos-inspecionaveis/${id}/confirmacao`, {});
  }

  importarReferencia(tipo: 'matriz-treinamentos' | 'escolas' | 'ativos-inspecionaveis', idBase?: string) {
    return this.http.post<Record<string, unknown>>(`${this.url}/importacoes/${tipo}`, {}, {
      params: idBase ? { idBase } : undefined
    });
  }

  listarMatriz(
    filtros: Record<string, unknown>
  ): Observable<PagedResponse<CelulaMatrizTreinamento>> {
    return this.getList<CelulaMatrizTreinamento>(`${this.url}/matriz`, filtros);
  }

  listarAcoes(filtros: Record<string, unknown>): Observable<PagedResponse<AcaoGovernancaResumo>> {
    return this.getList<AcaoGovernancaResumo>(`${this.url}/acoes`, filtros);
  }

  buscarAcao(id: string): Observable<AcaoGovernancaResumo> {
    return this.http.get<AcaoGovernancaResumo>(`${this.url}/acoes/${id}`);
  }

  criarAcao(request: Record<string, unknown>): Observable<AcaoGovernancaResumo> {
    return this.http.post<AcaoGovernancaResumo>(`${this.url}/acoes`, request);
  }

  comandoAcao(id: string, comando: string, request: Record<string, unknown>) {
    return this.http.post<AcaoGovernancaResumo>(`${this.url}/acoes/${id}/${comando}`, request);
  }

  listarModelosChecklist(
    filtros: Record<string, unknown>
  ): Observable<PagedResponse<ModeloChecklistGovernanca>> {
    return this.getList<ModeloChecklistGovernanca>(`${this.url}/modelos-checklist`, filtros);
  }

  salvarModeloChecklist(request: Record<string, unknown>, id?: string): Observable<ModeloChecklistGovernanca> {
    return id
      ? this.http.put<ModeloChecklistGovernanca>(`${this.url}/modelos-checklist/${id}`, request)
      : this.http.post<ModeloChecklistGovernanca>(`${this.url}/modelos-checklist`, request);
  }

  duplicarModeloChecklist(
    id: string,
    request: Record<string, unknown>
  ): Observable<ModeloChecklistGovernanca> {
    return this.http.post<ModeloChecklistGovernanca>(
      `${this.url}/modelos-checklist/${id}/duplicacao`,
      request
    );
  }

  anexarEvidencia(
    recurso: 'acoes' | 'checklists/execucoes',
    id: string,
    arquivo: File
  ): Observable<EvidenciaGovernanca> {
    const form = new FormData();
    form.append('arquivo', arquivo, arquivo.name);
    return this.http.post<EvidenciaGovernanca>(`${this.url}/${recurso}/${id}/evidencias`, form);
  }

  baixarArquivo(urlRelativa: string, params?: Record<string, string>): Observable<Blob> {
    return this.http.get(`${this.url}/${urlRelativa}`, {
      params: new HttpParams({ fromObject: params }),
      responseType: 'blob'
    });
  }
}
