import { Injectable, inject } from '@angular/core';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { CentrosCustoLookupService } from '../../../core/services/centros-custo-lookup.service';
import {
  ProcessoNegocioResponse,
  BaseOperacionalResponse,
  EquipeResponse,
  VeiculoResponse,
} from '../../../core/models/rh.models';
import { CentroFaturamentoResponse } from '../../../core/models/rh.models';
import { FormularioInspecaoResumoResponse } from '../../../core/models/sst.models';

export interface DesviosLookups {
  processos: ProcessoNegocioResponse[];
  bases: BaseOperacionalResponse[];
  equipes: EquipeResponse[];
  centrosCusto: CentroFaturamentoResponse[];
  formularios: FormularioInspecaoResumoResponse[];
  veiculos: VeiculoResponse[];
}

@Injectable({
  providedIn: 'root',
})
export class DesviosLookupsService {
  private readonly rh = inject(RhService);
  private readonly sst = inject(SstService);
  private readonly centrosCustoLookup = inject(CentrosCustoLookupService);

  carregar(): Observable<DesviosLookups> {
    return forkJoin({
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
      centrosCusto: this.centrosCustoLookup
        .listar()
        .pipe(catchError(() => of([] as CentroFaturamentoResponse[]))),
      formularios: this.sst.listarFormulariosAtivos().pipe(
        map((res) => res.content),
        catchError(() => of([] as FormularioInspecaoResumoResponse[])),
      ),
      veiculos: this.rh.listarVeiculos({ ativo: true, page: 0, size: 30, sort: 'placa,asc' }).pipe(
        map((res) => res.content),
        catchError(() => of([] as VeiculoResponse[])),
      ),
    });
  }
}
