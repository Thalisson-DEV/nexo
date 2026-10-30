import { Injectable, inject } from '@angular/core';
import { Observable, from, map, shareReplay, catchError, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import { DatabaseService } from './database.service';
import {
  BaseOperacionalImportacaoDTO,
  BaseOperacionalRequest,
  BaseOperacionalResponse,
  BaseOperacionalUpdateRequest,
  CentroFaturamentoResponse,
  CentroFaturamentoRequest,
  CentroFaturamentoImportacaoDTO,
  ImportacaoCentroFaturamentoResponse,
  ConfirmarImportacaoCentroFaturamentoRequest,
  ConfirmarImportacaoEquipeRequest,
  ConfirmarImportacaoVeiculoRequest,
  EquipeImportacaoDTO,
  EquipeMembroRequest,
  EquipeRequest,
  EquipeResponse,
  ImportacaoEquipeResponse,
  ImportacaoVeiculoResponse,
  TipoEquipeRequest,
  TipoEquipeResponse,
  VeiculoRequest,
  VeiculoResponse,
  ColaboradorRequest,
  ColaboradorResponse,
  ColaboradorUpdateRequest,
  ConfirmarImportacaoBaseRequest,
  ConfirmarImportacaoFuncaoRequest,
  ConfirmarImportacaoRequest,
  FuncaoImportacaoDTO,
  FuncaoRequest,
  FuncaoResponse,
  ImportacaoBaseOperacionalResponse,
  ImportacaoFuncaoResponse,
  ImportacaoColaboradorResponse,
  ImportacaoResultadoResponse,
  PerfilAcessoResponse,
  ProcessoNegocioResponse,
  VincularAcessoRequest,
  ProcessoNegocioRequest,
  ImportacaoProcessoNegocioResponse,
  ConfirmarImportacaoProcessoNegocioRequest,
  ImportacaoTipoEquipeResponse,
  ConfirmarImportacaoTipoEquipeRequest,
  MunicipioResponse,
  MunicipioRequest,
  MunicipioImportacaoDTO,
  ImportacaoMunicipioResponse,
  ConfirmarImportacaoMunicipioRequest,
  CoordenadorResponse,
  CoordenadorRequest,
  CoordenadorFilter,
  CoordenadorMinResponse,
  ImportacaoCoordenadorResponse,
  ConfirmarImportacaoCoordenadorRequest,
  EquipeFilter,
  EquipeMembroResponse,
  SupervisorResponse,
  SupervisorRequest,
  SupervisorFilter,
  SupervisorMinResponse,
  ImportacaoSupervisorResponse,
  ConfirmarImportacaoSupervisorRequest,
} from '../models/rh.models';
import { PagedResponse } from '../models/shared.models';
import { LookupCacheService } from '../lookup/services/lookup-cache.service';

@Injectable({
  providedIn: 'root',
})
export class RhService extends BaseService {
  private db = inject(DatabaseService);
  private readonly lookupCache = inject(LookupCacheService);
  private apiUrl = environment.apiUrl;

  private basesAtivas$?: Observable<PagedResponse<BaseOperacionalResponse>>;
  private processosAtivos$?: Observable<PagedResponse<ProcessoNegocioResponse>>;
  private funcoesAtivas$?: Observable<PagedResponse<FuncaoResponse>>;
  private tiposEquipeAtivos$?: Observable<PagedResponse<TipoEquipeResponse>>;
  private perfisAtivos$?: Observable<PerfilAcessoResponse[]>;
  private veiculosAtivos$?: Observable<PagedResponse<VeiculoResponse>>;
  private centrosFaturamentoAtivos$?: Observable<PagedResponse<CentroFaturamentoResponse>>;
  private municipiosAtivos$?: Observable<PagedResponse<MunicipioResponse>>;

  // Municípios
  listarMunicipios(params: any): Observable<PagedResponse<MunicipioResponse>> {
    return this.getList<MunicipioResponse>(`${this.apiUrl}/v1/municipios`, params);
  }

  buscarMunicipioPorId(id: string): Observable<MunicipioResponse> {
    return this.getOne<MunicipioResponse>(`${this.apiUrl}/v1/municipios`, id);
  }

  criarMunicipio(request: MunicipioRequest): Observable<MunicipioResponse | null> {
    this.municipiosAtivos$ = undefined;
    return this.createSync<MunicipioResponse>(`${this.apiUrl}/v1/municipios`, request);
  }

  atualizarMunicipio(id: string, request: MunicipioRequest): Observable<MunicipioResponse | null> {
    this.municipiosAtivos$ = undefined;
    return this.updateSync<MunicipioResponse>(`${this.apiUrl}/v1/municipios`, id, request);
  }

  desativarMunicipio(id: string): Observable<void | null> {
    this.municipiosAtivos$ = undefined;
    return this.deleteSync(`${this.apiUrl}/v1/municipios`, id);
  }

  ativarMunicipio(id: string): Observable<void | null> {
    this.municipiosAtivos$ = undefined;
    return this.updateSync<void>(`${this.apiUrl}/v1/municipios`, `${id}/ativar`, {});
  }

  importarMunicipios(arquivo: File): Observable<ImportacaoMunicipioResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoMunicipioResponse>(
      `${this.apiUrl}/v1/municipios/importar`,
      formData,
    );
  }

  confirmarImportacaoMunicipios(
    request: ConfirmarImportacaoMunicipioRequest,
  ): Observable<ImportacaoMunicipioResponse> {
    this.municipiosAtivos$ = undefined;
    return this.http.post<ImportacaoMunicipioResponse>(
      `${this.apiUrl}/v1/municipios/confirmar-importacao`,
      request,
    );
  }

  listarMunicipiosAtivos(forceRefresh = false): Observable<PagedResponse<MunicipioResponse>> {
    if (!this.municipiosAtivos$ || forceRefresh) {
      this.municipiosAtivos$ = this.getList<MunicipioResponse>(`${this.apiUrl}/v1/municipios`, {
        ativo: true,
        size: 1000,
      }).pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.municipiosAtivos$;
  }

  // Colaboradores
  listarColaboradores(params: any): Observable<PagedResponse<ColaboradorResponse>> {
    return this.getList<ColaboradorResponse>(
      `${this.apiUrl}/v1/rh/colaboradores`,
      params,
      this.db.colaboradores,
    );
  }

  listarColaboradoresParaSelecao(
    params: Record<string, unknown>,
  ): Observable<PagedResponse<ColaboradorResponse>> {
    return this.getList<ColaboradorResponse>(`${this.apiUrl}/v1/rh/colaboradores`, params);
  }

  buscarColaboradorPorId(id: string): Observable<ColaboradorResponse> {
    return this.getOne<ColaboradorResponse>(
      `${this.apiUrl}/v1/rh/colaboradores`,
      id,
      this.db.colaboradores,
    );
  }

  buscarColaboradorPorUsuarioId(idUsuario: string): Observable<ColaboradorResponse | null> {
    return this.listarColaboradores({ idUsuario, page: 0, size: 1 }).pipe(
      map((response) => response.content[0] ?? null),
    );
  }

  criarColaborador(request: ColaboradorRequest): Observable<ColaboradorResponse | null> {
    return this.createSync<ColaboradorResponse>(
      `${this.apiUrl}/v1/rh/colaboradores`,
      request,
      this.db.colaboradores,
    ).pipe(tap(() => this.lookupCache.clear('collaborators')));
  }

  atualizarColaborador(
    id: string,
    request: ColaboradorUpdateRequest,
  ): Observable<ColaboradorResponse | null> {
    return this.updateSync<ColaboradorResponse>(
      `${this.apiUrl}/v1/rh/colaboradores`,
      id,
      request,
      this.db.colaboradores,
    ).pipe(tap(() => this.lookupCache.clear('collaborators')));
  }

  desativarColaborador(id: string): Observable<void | null> {
    return this.deleteSync(`${this.apiUrl}/v1/rh/colaboradores`, id, this.db.colaboradores).pipe(
      tap(() => this.lookupCache.clear('collaborators')),
    );
  }

  ativarColaborador(id: string): Observable<void | null> {
    return this.updateSync<void>(
      `${this.apiUrl}/v1/rh/colaboradores`,
      `${id}/ativar`,
      {},
      this.db.colaboradores,
    ).pipe(tap(() => this.lookupCache.clear('collaborators')));
  }

  gerarAcesso(request: VincularAcessoRequest): Observable<ColaboradorResponse | null> {
    return this.createSync<ColaboradorResponse>(
      `${this.apiUrl}/v1/rh/colaboradores/gerar-acesso`,
      request,
    );
  }

  importarColaboradores(arquivo: File): Observable<ImportacaoColaboradorResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoColaboradorResponse>(
      `${this.apiUrl}/v1/rh/colaboradores/importar`,
      formData,
    );
  }

  confirmarImportacao(
    request: ConfirmarImportacaoRequest,
  ): Observable<ImportacaoColaboradorResponse> {
    return this.http
      .post<ImportacaoColaboradorResponse>(
        `${this.apiUrl}/v1/rh/colaboradores/confirmar-importacao`,
        request,
      )
      .pipe(tap(() => this.lookupCache.clear('collaborators')));
  }

  listarColaboradoresComUsuario(): Observable<ColaboradorResponse[]> {
    return this.getList<ColaboradorResponse>(`${this.apiUrl}/v1/rh/colaboradores`, {
      ativo: true,
      size: 2000,
    }).pipe(map((res) => res.content.filter((c) => !!c.idUsuario)));
  }

  // Bases Operacionais
  listarBasesOperacionais(params: any): Observable<PagedResponse<BaseOperacionalResponse>> {
    return this.getList<BaseOperacionalResponse>(`${this.apiUrl}/v1/bases-operacionais`, params);
  }

  buscarBaseOperacionalPorId(id: string): Observable<BaseOperacionalResponse> {
    return this.getOne<BaseOperacionalResponse>(`${this.apiUrl}/v1/bases-operacionais`, id);
  }

  criarBaseOperacional(
    request: BaseOperacionalRequest,
  ): Observable<BaseOperacionalResponse | null> {
    this.basesAtivas$ = undefined;
    return this.createSync<BaseOperacionalResponse>(
      `${this.apiUrl}/v1/bases-operacionais`,
      request,
    );
  }

  atualizarBaseOperacional(
    id: string,
    request: BaseOperacionalUpdateRequest,
  ): Observable<BaseOperacionalResponse | null> {
    this.basesAtivas$ = undefined;
    return this.updateSync<BaseOperacionalResponse>(
      `${this.apiUrl}/v1/bases-operacionais`,
      id,
      request,
    );
  }

  desativarBaseOperacional(id: string): Observable<void | null> {
    this.basesAtivas$ = undefined;
    return this.deleteSync(`${this.apiUrl}/v1/bases-operacionais`, id);
  }

  ativarBaseOperacional(id: string): Observable<void | null> {
    this.basesAtivas$ = undefined;
    return this.updateSync<void>(`${this.apiUrl}/v1/bases-operacionais`, `${id}/ativar`, {});
  }

  importarBasesOperacionais(arquivo: File): Observable<ImportacaoBaseOperacionalResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoBaseOperacionalResponse>(
      `${this.apiUrl}/v1/bases-operacionais/importar`,
      formData,
    );
  }

  confirmarImportacaoBases(
    request: ConfirmarImportacaoBaseRequest,
  ): Observable<ImportacaoBaseOperacionalResponse> {
    this.basesAtivas$ = undefined;
    return this.http.post<ImportacaoBaseOperacionalResponse>(
      `${this.apiUrl}/v1/bases-operacionais/confirmar-importacao`,
      request,
    );
  }

  // Centros de Faturamento
  listarCentrosFaturamento(params: any): Observable<PagedResponse<CentroFaturamentoResponse>> {
    return this.getList<CentroFaturamentoResponse>(`${this.apiUrl}/v1/centros-faturamento`, params);
  }

  buscarCentroFaturamentoPorId(id: string): Observable<CentroFaturamentoResponse> {
    return this.getOne<CentroFaturamentoResponse>(`${this.apiUrl}/v1/centros-faturamento`, id);
  }

  criarCentroFaturamento(
    request: CentroFaturamentoRequest,
  ): Observable<CentroFaturamentoResponse | null> {
    this.centrosFaturamentoAtivos$ = undefined;
    return this.createSync<CentroFaturamentoResponse>(
      `${this.apiUrl}/v1/centros-faturamento`,
      request,
    );
  }

  atualizarCentroFaturamento(
    id: string,
    request: CentroFaturamentoRequest,
  ): Observable<CentroFaturamentoResponse | null> {
    this.centrosFaturamentoAtivos$ = undefined;
    return this.updateSync<CentroFaturamentoResponse>(
      `${this.apiUrl}/v1/centros-faturamento`,
      id,
      request,
    );
  }

  desativarCentroFaturamento(id: string): Observable<void | null> {
    this.centrosFaturamentoAtivos$ = undefined;
    return this.deleteSync(`${this.apiUrl}/v1/centros-faturamento`, id);
  }

  ativarCentroFaturamento(id: string): Observable<void | null> {
    this.centrosFaturamentoAtivos$ = undefined;
    return this.updateSync<void>(`${this.apiUrl}/v1/centros-faturamento`, `${id}/ativar`, {});
  }

  importarCentrosFaturamento(arquivo: File): Observable<ImportacaoCentroFaturamentoResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoCentroFaturamentoResponse>(
      `${this.apiUrl}/v1/centros-faturamento/importar`,
      formData,
    );
  }

  confirmarImportacaoCentrosFaturamento(
    request: ConfirmarImportacaoCentroFaturamentoRequest,
  ): Observable<ImportacaoCentroFaturamentoResponse> {
    this.centrosFaturamentoAtivos$ = undefined;
    return this.http.post<ImportacaoCentroFaturamentoResponse>(
      `${this.apiUrl}/v1/centros-faturamento/confirmar-importacao`,
      request,
    );
  }

  listarCentrosFaturamentoAtivos(
    forceRefresh = false,
  ): Observable<PagedResponse<CentroFaturamentoResponse>> {
    if (!this.centrosFaturamentoAtivos$ || forceRefresh) {
      this.centrosFaturamentoAtivos$ = this.getList<CentroFaturamentoResponse>(
        `${this.apiUrl}/v1/centros-faturamento`,
        { ativo: true, size: 1000 },
      ).pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.centrosFaturamentoAtivos$;
  }

  // Tipos de Equipe
  listarTiposEquipe(params: any): Observable<PagedResponse<TipoEquipeResponse>> {
    return this.getList<TipoEquipeResponse>(`${this.apiUrl}/v1/rh/tipos-equipe`, params);
  }

  buscarTipoEquipePorId(id: string): Observable<TipoEquipeResponse> {
    return this.getOne<TipoEquipeResponse>(`${this.apiUrl}/v1/rh/tipos-equipe`, id);
  }

  criarTipoEquipe(request: TipoEquipeRequest): Observable<TipoEquipeResponse | null> {
    return this.createSync<TipoEquipeResponse>(`${this.apiUrl}/v1/rh/tipos-equipe`, request);
  }

  atualizarTipoEquipe(
    id: string,
    request: TipoEquipeRequest,
  ): Observable<TipoEquipeResponse | null> {
    return this.updateSync<TipoEquipeResponse>(`${this.apiUrl}/v1/rh/tipos-equipe`, id, request);
  }

  desativarTipoEquipe(id: string): Observable<void | null> {
    return this.deleteSync(`${this.apiUrl}/v1/rh/tipos-equipe`, id);
  }

  ativarTipoEquipe(id: string): Observable<void | null> {
    return this.updateSync<void>(`${this.apiUrl}/v1/rh/tipos-equipe`, `${id}/ativar`, {});
  }

  importarTiposEquipe(arquivo: File): Observable<ImportacaoTipoEquipeResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoTipoEquipeResponse>(
      `${this.apiUrl}/v1/rh/tipos-equipe/importar`,
      formData,
    );
  }

  confirmarImportacaoTiposEquipe(
    request: ConfirmarImportacaoTipoEquipeRequest,
  ): Observable<ImportacaoTipoEquipeResponse> {
    return this.http.post<ImportacaoTipoEquipeResponse>(
      `${this.apiUrl}/v1/rh/tipos-equipe/importar/confirmar`,
      request,
    );
  }

  // Equipes
  listarEquipes(params: any): Observable<PagedResponse<EquipeResponse>> {
    return this.getList<EquipeResponse>(`${this.apiUrl}/v1/equipes`, params).pipe(
      map((res) => ({
        ...res,
        content: res.content.map((equipe) => ({
          ...equipe,
          membros: equipe.membros?.filter((m) => m.vinculoAtivo),
        })),
      })),
    );
  }

  buscarEquipePorId(id: string): Observable<EquipeResponse> {
    return this.getOne<EquipeResponse>(`${this.apiUrl}/v1/equipes`, id).pipe(
      map((equipe) => ({
        ...equipe,
        membros: equipe.membros?.filter((m) => m.vinculoAtivo),
      })),
    );
  }

  criarEquipe(request: EquipeRequest): Observable<EquipeResponse | null> {
    return this.createSync<EquipeResponse>(`${this.apiUrl}/v1/equipes`, request).pipe(
      map((equipe) =>
        equipe
          ? {
              ...equipe,
              membros: equipe.membros?.filter((m) => m.vinculoAtivo),
            }
          : null,
      ),
    );
  }

  atualizarEquipe(id: string, request: EquipeRequest): Observable<EquipeResponse | null> {
    return this.updateSync<EquipeResponse>(`${this.apiUrl}/v1/equipes`, id, request).pipe(
      map((equipe) =>
        equipe
          ? {
              ...equipe,
              membros: equipe.membros?.filter((m) => m.vinculoAtivo),
            }
          : null,
      ),
    );
  }

  desativarEquipe(id: string): Observable<void | null> {
    return this.deleteSync(`${this.apiUrl}/v1/equipes`, id);
  }

  ativarEquipe(id: string): Observable<void | null> {
    return this.updateSync<void>(`${this.apiUrl}/v1/equipes`, `${id}/ativar`, {});
  }

  importarEquipes(arquivo: File): Observable<ImportacaoEquipeResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoEquipeResponse>(`${this.apiUrl}/v1/equipes/importar`, formData);
  }

  confirmarImportacaoEquipes(
    request: ConfirmarImportacaoEquipeRequest,
  ): Observable<ImportacaoEquipeResponse> {
    return this.http.post<ImportacaoEquipeResponse>(
      `${this.apiUrl}/v1/equipes/confirmar-importacao`,
      request,
    );
  }

  listarMembrosAtivos(equipeId: string): Observable<EquipeMembroResponse[]> {
    return this.http.get<EquipeMembroResponse[]>(`${this.apiUrl}/v1/equipes/${equipeId}/membros`);
  }

  alocarMembro(equipeId: string, request: EquipeMembroRequest): Observable<void | null> {
    return from(
      this.sync.execute<void>('POST', `${this.apiUrl}/v1/equipes/${equipeId}/membros`, request),
    );
  }

  vincularMembrosBatch(equipeId: string, membros: EquipeMembroRequest[]): Observable<void | null> {
    return from(
      this.sync.execute<void>(
        'POST',
        `${this.apiUrl}/v1/equipes/${equipeId}/membros/batch`,
        membros,
      ),
    );
  }

  desalocarMembro(equipeId: string, colaboradorId: string): Observable<void | null> {
    return from(
      this.sync.execute<void>(
        'DELETE',
        `${this.apiUrl}/v1/equipes/${equipeId}/membros/${colaboradorId}`,
        {},
      ),
    );
  }

  transferirResponsabilidade(equipeId: string, colaboradorId: string): Observable<void | null> {
    return from(
      this.sync.execute<void>(
        'PATCH',
        `${this.apiUrl}/v1/equipes/${equipeId}/responsabilidade/${colaboradorId}`,
        {},
      ),
    );
  }

  listarTiposEquipeAtivos(forceRefresh = false): Observable<PagedResponse<TipoEquipeResponse>> {
    if (!this.tiposEquipeAtivos$ || forceRefresh) {
      this.tiposEquipeAtivos$ = this.getList<TipoEquipeResponse>(
        `${this.apiUrl}/v1/rh/tipos-equipe`,
        { ativo: true, size: 1000 },
      ).pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.tiposEquipeAtivos$;
  }

  // Lookups (Performance-first cache via SW & in-memory shareReplay)
  listarBasesOperacionaisAtivas(
    forceRefresh = false,
  ): Observable<PagedResponse<BaseOperacionalResponse>> {
    if (!this.basesAtivas$ || forceRefresh) {
      this.basesAtivas$ = this.getList<BaseOperacionalResponse>(
        `${this.apiUrl}/v1/bases-operacionais`,
        { ativo: true, size: 1000 },
      ).pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.basesAtivas$;
  }

  listarProcessosAtivos(forceRefresh = false): Observable<PagedResponse<ProcessoNegocioResponse>> {
    if (!this.processosAtivos$ || forceRefresh) {
      this.processosAtivos$ = this.getList<ProcessoNegocioResponse>(
        `${this.apiUrl}/v1/processos-negocio`,
        { ativo: true, size: 1000 },
      ).pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.processosAtivos$;
  }

  // Processos de Negócio
  listarProcessos(params: any): Observable<PagedResponse<ProcessoNegocioResponse>> {
    return this.getList<ProcessoNegocioResponse>(`${this.apiUrl}/v1/processos-negocio`, params);
  }

  buscarProcessoPorId(id: string): Observable<ProcessoNegocioResponse> {
    return this.getOne<ProcessoNegocioResponse>(`${this.apiUrl}/v1/processos-negocio`, id);
  }

  criarProcesso(request: ProcessoNegocioRequest): Observable<ProcessoNegocioResponse | null> {
    this.processosAtivos$ = undefined;
    return this.createSync<ProcessoNegocioResponse>(`${this.apiUrl}/v1/processos-negocio`, request);
  }

  atualizarProcesso(
    id: string,
    request: ProcessoNegocioRequest,
  ): Observable<ProcessoNegocioResponse | null> {
    this.processosAtivos$ = undefined;
    return this.updateSync<ProcessoNegocioResponse>(
      `${this.apiUrl}/v1/processos-negocio`,
      id,
      request,
    );
  }

  desativarProcesso(id: string): Observable<void | null> {
    this.processosAtivos$ = undefined;
    return this.deleteSync(`${this.apiUrl}/v1/processos-negocio`, id);
  }

  ativarProcesso(id: string): Observable<void | null> {
    this.processosAtivos$ = undefined;
    return this.updateSync<void>(`${this.apiUrl}/v1/processos-negocio`, `${id}/ativar`, {});
  }

  importarProcessos(arquivo: File): Observable<ImportacaoProcessoNegocioResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoProcessoNegocioResponse>(
      `${this.apiUrl}/v1/processos-negocio/importar`,
      formData,
    );
  }

  confirmarImportacaoProcessos(
    request: ConfirmarImportacaoProcessoNegocioRequest,
  ): Observable<ImportacaoProcessoNegocioResponse> {
    this.processosAtivos$ = undefined;
    return this.http.post<ImportacaoProcessoNegocioResponse>(
      `${this.apiUrl}/v1/processos-negocio/confirmar-importacao`,
      request,
    );
  }

  listarFuncoesAtivas(forceRefresh = false): Observable<PagedResponse<FuncaoResponse>> {
    if (!this.funcoesAtivas$ || forceRefresh) {
      this.funcoesAtivas$ = this.getList<FuncaoResponse>(`${this.apiUrl}/v1/rh/funcoes`, {
        ativo: true,
        size: 1000,
      }).pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.funcoesAtivas$;
  }

  listarPerfisAcessoAtivos(forceRefresh = false): Observable<PerfilAcessoResponse[]> {
    if (!this.perfisAtivos$ || forceRefresh) {
      this.perfisAtivos$ = this.http
        .get<PerfilAcessoResponse[]>(`${this.apiUrl}/v1/iam/perfis`)
        .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.perfisAtivos$;
  }

  // Funções
  listarFuncoes(params: any): Observable<PagedResponse<FuncaoResponse>> {
    return this.getList<FuncaoResponse>(`${this.apiUrl}/v1/rh/funcoes`, params);
  }

  buscarFuncaoPorId(id: string): Observable<FuncaoResponse> {
    return this.getOne<FuncaoResponse>(`${this.apiUrl}/v1/rh/funcoes`, id);
  }

  criarFuncao(request: FuncaoRequest): Observable<FuncaoResponse | null> {
    this.funcoesAtivas$ = undefined;
    return this.createSync<FuncaoResponse>(`${this.apiUrl}/v1/rh/funcoes`, request);
  }

  atualizarFuncao(id: string, request: FuncaoRequest): Observable<FuncaoResponse | null> {
    this.funcoesAtivas$ = undefined;
    return this.updateSync<FuncaoResponse>(`${this.apiUrl}/v1/rh/funcoes`, id, request);
  }

  desativarFuncao(id: string): Observable<void | null> {
    this.funcoesAtivas$ = undefined;
    return this.deleteSync(`${this.apiUrl}/v1/rh/funcoes`, id);
  }

  ativarFuncao(id: string): Observable<void | null> {
    this.funcoesAtivas$ = undefined;
    return this.updateSync<void>(`${this.apiUrl}/v1/rh/funcoes`, `${id}/ativar`, {});
  }

  importarFuncoes(arquivo: File): Observable<ImportacaoFuncaoResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoFuncaoResponse>(
      `${this.apiUrl}/v1/rh/funcoes/importar`,
      formData,
    );
  }

  confirmarImportacaoFuncoes(
    request: ConfirmarImportacaoFuncaoRequest,
  ): Observable<ImportacaoFuncaoResponse> {
    this.funcoesAtivas$ = undefined;
    return this.http.post<ImportacaoFuncaoResponse>(
      `${this.apiUrl}/v1/rh/funcoes/confirmar-importacao`,
      request,
    );
  }

  // Veículos
  listarVeiculos(params: any): Observable<PagedResponse<VeiculoResponse>> {
    return this.getList<VeiculoResponse>(`${this.apiUrl}/v1/veiculos`, params);
  }

  buscarVeiculoPorId(id: string): Observable<VeiculoResponse> {
    return this.getOne<VeiculoResponse>(`${this.apiUrl}/v1/veiculos`, id);
  }

  criarVeiculo(request: VeiculoRequest): Observable<VeiculoResponse | null> {
    this.veiculosAtivos$ = undefined;
    return this.createSync<VeiculoResponse>(`${this.apiUrl}/v1/veiculos`, request);
  }

  atualizarVeiculo(id: string, request: VeiculoRequest): Observable<VeiculoResponse | null> {
    this.veiculosAtivos$ = undefined;
    return this.updateSync<VeiculoResponse>(`${this.apiUrl}/v1/veiculos`, id, request);
  }

  desativarVeiculo(id: string): Observable<void | null> {
    this.veiculosAtivos$ = undefined;
    return this.deleteSync(`${this.apiUrl}/v1/veiculos`, id);
  }

  ativarVeiculo(id: string): Observable<void | null> {
    this.veiculosAtivos$ = undefined;
    return this.updateSync<void>(`${this.apiUrl}/v1/veiculos`, `${id}/ativar`, {});
  }

  importarVeiculos(arquivo: File): Observable<ImportacaoVeiculoResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoVeiculoResponse>(
      `${this.apiUrl}/v1/veiculos/importar`,
      formData,
    );
  }

  confirmarImportacaoVeiculos(
    request: ConfirmarImportacaoVeiculoRequest,
  ): Observable<ImportacaoVeiculoResponse> {
    this.veiculosAtivos$ = undefined;
    return this.http.post<ImportacaoVeiculoResponse>(
      `${this.apiUrl}/v1/veiculos/confirmar-importacao`,
      request,
    );
  }

  listarVeiculosAtivos(forceRefresh = false): Observable<PagedResponse<VeiculoResponse>> {
    if (!this.veiculosAtivos$ || forceRefresh) {
      this.veiculosAtivos$ = this.getList<VeiculoResponse>(`${this.apiUrl}/v1/veiculos`, {
        ativo: true,
        size: 1000,
      }).pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.veiculosAtivos$;
  }

  // Coordenadores
  listarCoordenadores(params: any): Observable<PagedResponse<CoordenadorResponse>> {
    return this.getList<CoordenadorResponse>(`${this.apiUrl}/v1/rh/coordenadores`, params);
  }

  listarCoordenadoresAtivosMin(): Observable<CoordenadorMinResponse[]> {
    return this.http
      .get<CoordenadorMinResponse[]>(`${this.apiUrl}/v1/rh/coordenadores/ativos-min`)
      .pipe(
        catchError(() =>
          this.http.get<CoordenadorMinResponse[]>(`${this.apiUrl}/v1/rh/coordenadores/min`),
        ),
      );
  }

  listarCoordenadoresDropdown(): Observable<CoordenadorMinResponse[]> {
    return this.listarCoordenadoresAtivosMin();
  }

  buscarCoordenadorPorId(id: string): Observable<CoordenadorResponse> {
    return this.getOne<CoordenadorResponse>(`${this.apiUrl}/v1/rh/coordenadores`, id);
  }

  criarCoordenador(request: CoordenadorRequest): Observable<CoordenadorResponse | null> {
    return this.createSync<CoordenadorResponse>(`${this.apiUrl}/v1/rh/coordenadores`, request);
  }

  atualizarCoordenador(
    id: string,
    request: CoordenadorRequest,
  ): Observable<CoordenadorResponse | null> {
    return this.updateSync<CoordenadorResponse>(`${this.apiUrl}/v1/rh/coordenadores`, id, request);
  }

  desativarCoordenador(id: string): Observable<void | null> {
    return this.deleteSync(`${this.apiUrl}/v1/rh/coordenadores`, id);
  }

  ativarCoordenador(id: string): Observable<void | null> {
    return this.updateSync<void>(`${this.apiUrl}/v1/rh/coordenadores`, `${id}/ativar`, {});
  }

  importarCoordenadores(arquivo: File): Observable<ImportacaoCoordenadorResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoCoordenadorResponse>(
      `${this.apiUrl}/v1/rh/coordenadores/importar`,
      formData,
    );
  }

  confirmarImportacaoCoordenadores(
    request: ConfirmarImportacaoCoordenadorRequest,
  ): Observable<ImportacaoCoordenadorResponse> {
    return this.http.post<ImportacaoCoordenadorResponse>(
      `${this.apiUrl}/v1/rh/coordenadores/confirmar-importacao`,
      request,
    );
  }

  // Supervisores
  listarSupervisores(params: any): Observable<PagedResponse<SupervisorResponse>> {
    return this.getList<SupervisorResponse>(`${this.apiUrl}/v1/rh/supervisores`, params);
  }

  listarSupervisoresAtivosMin(): Observable<SupervisorMinResponse[]> {
    return this.http
      .get<SupervisorMinResponse[]>(`${this.apiUrl}/v1/rh/supervisores/ativos-min`)
      .pipe(
        catchError(() =>
          this.http.get<SupervisorMinResponse[]>(`${this.apiUrl}/v1/rh/supervisores/min`),
        ),
      );
  }

  listarSupervisoresDropdown(): Observable<SupervisorMinResponse[]> {
    return this.listarSupervisoresAtivosMin();
  }

  buscarSupervisorPorId(id: string): Observable<SupervisorResponse> {
    return this.getOne<SupervisorResponse>(`${this.apiUrl}/v1/rh/supervisores`, id);
  }

  criarSupervisor(request: SupervisorRequest): Observable<SupervisorResponse | null> {
    return this.createSync<SupervisorResponse>(`${this.apiUrl}/v1/rh/supervisores`, request);
  }

  atualizarSupervisor(
    id: string,
    request: SupervisorRequest,
  ): Observable<SupervisorResponse | null> {
    return this.updateSync<SupervisorResponse>(`${this.apiUrl}/v1/rh/supervisores`, id, request);
  }

  desativarSupervisor(id: string): Observable<void | null> {
    return this.deleteSync(`${this.apiUrl}/v1/rh/supervisores`, id);
  }

  ativarSupervisor(id: string): Observable<void | null> {
    return this.updateSync<void>(`${this.apiUrl}/v1/rh/supervisores`, `${id}/ativar`, {});
  }

  importarSupervisores(arquivo: File): Observable<ImportacaoSupervisorResponse> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);
    return this.http.post<ImportacaoSupervisorResponse>(
      `${this.apiUrl}/v1/rh/supervisores/importar`,
      formData,
    );
  }

  confirmarImportacaoSupervisores(
    request: ConfirmarImportacaoSupervisorRequest,
  ): Observable<ImportacaoSupervisorResponse> {
    return this.http.post<ImportacaoSupervisorResponse>(
      `${this.apiUrl}/v1/rh/supervisores/confirmar-importacao`,
      request,
    );
  }
}
