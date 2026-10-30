import { inject, Injectable } from '@angular/core';
import { ParamMap } from '@angular/router';
import { forkJoin } from 'rxjs';

import { MeuDesvioFilter, MeuDesvioResponse } from '../../../core/models/meu-desvio.models';
import { GravidadeDesvio, StatusDesvio } from '../../../core/models/sst.models';
import { RhService } from '../../../core/services/rh.service';
import { normalizarSelecao } from '../../../core/utils/http-params.util';

export interface MeusDesviosFiltrosView {
  status: StatusDesvio[];
  gravidade: GravidadeDesvio[];
  apenasVencidos: boolean | null;
  idProcesso: string[];
  idBaseOperacional: string[];
  /** Regional comercial da base operacional (Centro de Faturamento). */
  idCentroCusto: string[];
  idEquipe: string[];
  dataDe: Date | null;
  dataAte: Date | null;
}

/** Estado inicial: nenhuma dimensão restringindo. */
export function filtrosMeusDesviosVazios(): MeusDesviosFiltrosView {
  return {
    status: [],
    gravidade: [],
    apenasVencidos: null,
    idProcesso: [],
    idBaseOperacional: [],
    idCentroCusto: [],
    idEquipe: [],
    dataDe: null,
    dataAte: null
  };
}

export interface MeusDesviosUrlState {
  status?: StatusDesvio | null;
  filtros: MeusDesviosFiltrosView;
  busca: string;
  rows: number;
  first: number;
  sort: string;
}

export function formatarDataLocal(date: Date | null): string | null {
  if (!date) return null;
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function parsearDataLocal(value: string | null): Date | null {
  if (!value) return null;
  const date = new Date(`${value.substring(0, 10)}T00:00:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function montarMeuDesvioFiltro(
  statusAtalho: StatusDesvio | null,
  filtros: MeusDesviosFiltrosView,
  busca: string,
  first: number,
  rows: number,
  sort: string
): MeuDesvioFilter {
  const statusFinal = statusAtalho
    ? [statusAtalho]
    : (filtros.status?.length ? filtros.status : null);

  return {
    status: statusFinal,
    gravidade: filtros.gravidade.length ? filtros.gravidade : null,
    apenasVencidos: filtros.apenasVencidos,
    idProcesso: filtros.idProcesso.length ? filtros.idProcesso : null,
    idBaseOperacional: filtros.idBaseOperacional.length ? filtros.idBaseOperacional : null,
    idCentroCusto: filtros.idCentroCusto.length ? filtros.idCentroCusto : null,
    idEquipe: filtros.idEquipe.length ? filtros.idEquipe : null,
    dataInspecaoDe: formatarDataLocal(filtros.dataDe),
    dataInspecaoAte: formatarDataLocal(filtros.dataAte),
    buscaGlobal: busca.trim() || null,
    page: Math.floor(first / rows),
    size: rows,
    sort
  };
}

/**
 * Um desvio recém-tratado continua na lista? Seleção vazia numa dimensão não
 * restringe; com valores, basta o item bater com **um** deles (OU dentro da
 * dimensão, E entre dimensões — a mesma semântica do backend).
 *
 * Centro de custo não é conferido aqui: `MeuDesvioResponse` não traz o centro do
 * desvio, então quem filtra por ele é só o servidor.
 */
export function desvioAtendeFiltros(
  item: MeuDesvioResponse,
  statusAtalho: StatusDesvio | null,
  filtros: MeusDesviosFiltrosView
): boolean {
  if (statusAtalho && item.status !== statusAtalho) return false;
  if (!statusAtalho && filtros.status?.length && !filtros.status.includes(item.status)) return false;
  if (filtros.gravidade.length && !filtros.gravidade.includes(item.gravidade)) return false;
  if (filtros.apenasVencidos && !item.vencido) return false;
  if (!atendeDimensao(filtros.idProcesso, item.processo?.id)) return false;
  if (!atendeDimensao(filtros.idBaseOperacional, item.baseOperacional?.id)) return false;
  if (!atendeDimensao(filtros.idEquipe, item.equipe?.id)) return false;
  return true;
}

function atendeDimensao(selecao: string[], valor: string | undefined): boolean {
  return selecao.length === 0 || (!!valor && selecao.includes(valor));
}


@Injectable({ providedIn: 'root' })
export class MeusDesviosLookupService {
  private readonly rh = inject(RhService);

  carregar() {
    return forkJoin({
      processos: this.rh.listarProcessosAtivos(),
      bases: this.rh.listarBasesOperacionais({
        ativo: true,
        page: 0,
        size: 100,
        sort: 'nome,asc'
      })
    });
  }
}
