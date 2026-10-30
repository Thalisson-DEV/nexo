import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { CentrosCustoLookupService } from '../../../core/services/centros-custo-lookup.service';
import {
  BaseOperacionalResponse,
  CentroFaturamentoResponse,
  EquipeResponse,
  ProcessoNegocioResponse,
  VeiculoResponse,
} from '../../../core/models/rh.models';
import { AtividadeSstResponse, CatalogoSstResponse } from '../../../core/models/sst-evento.models';

/**
 * Tudo o que os filtros e o formulário de evento precisam ter em mão, resolvido em uma
 * rodada só.
 *
 * As quatro listas de catálogo vêm de `/opcoes`, que devolve apenas as entradas ativas
 * na ordem de exibição — uma entrada desativada continua aparecendo nos eventos que já
 * a referenciam, mas não é oferecida em registro novo.
 */
export interface EventosLookups {
  atividades: AtividadeSstResponse[];
  classificacoes: CatalogoSstResponse[];
  locaisLesao: CatalogoSstResponse[];
  naturezasLesao: CatalogoSstResponse[];
  agentesCausadores: CatalogoSstResponse[];
  processos: ProcessoNegocioResponse[];
  bases: BaseOperacionalResponse[];
  equipes: EquipeResponse[];
  veiculos: VeiculoResponse[];
  centrosCusto: CentroFaturamentoResponse[];
}

@Injectable({ providedIn: 'root' })
export class EventosLookupsService {
  private readonly rh = inject(RhService);
  private readonly sst = inject(SstService);
  private readonly centrosCustoLookup = inject(CentrosCustoLookupService);

  /**
   * Uma falha isolada não derruba a tela: cada fonte cai para lista vazia. Sem isso,
   * um perfil que não pode listar veículos ficaria sem conseguir registrar acidente
   * nenhum por causa de um campo opcional.
   */
  carregar(): Observable<EventosLookups> {
    return forkJoin({
      atividades: this.sst
        .listarOpcoesAtividades()
        .pipe(catchError(() => of([] as AtividadeSstResponse[]))),
      classificacoes: this.sst
        .listarOpcoesCatalogo('CLASSIFICACAO_EVENTO')
        .pipe(catchError(() => of([] as CatalogoSstResponse[]))),
      locaisLesao: this.sst
        .listarOpcoesCatalogo('LOCAL_LESAO')
        .pipe(catchError(() => of([] as CatalogoSstResponse[]))),
      naturezasLesao: this.sst
        .listarOpcoesCatalogo('NATUREZA_LESAO')
        .pipe(catchError(() => of([] as CatalogoSstResponse[]))),
      agentesCausadores: this.sst
        .listarOpcoesCatalogo('AGENTE_CAUSADOR')
        .pipe(catchError(() => of([] as CatalogoSstResponse[]))),
      processos: this.rh.listarProcessosAtivos().pipe(
        map((res) => res.content),
        catchError(() => of([] as ProcessoNegocioResponse[])),
      ),
      bases: this.rh
        .listarBasesOperacionais({ ativo: true, page: 0, size: 100, sort: 'nome,asc' })
        .pipe(
          map((res) => res.content),
          catchError(() => of([] as BaseOperacionalResponse[])),
        ),
      equipes: this.rh.listarEquipes({ ativo: true, page: 0, size: 30, sort: 'codigo,asc' }).pipe(
        map((res) => res.content),
        catchError(() => of([] as EquipeResponse[])),
      ),
      veiculos: this.rh.listarVeiculos({ ativo: true, page: 0, size: 30, sort: 'placa,asc' }).pipe(
        map((res) => res.content),
        catchError(() => of([] as VeiculoResponse[])),
      ),
      centrosCusto: this.centrosCustoLookup
        .listar()
        .pipe(catchError(() => of([] as CentroFaturamentoResponse[]))),
    });
  }
}
