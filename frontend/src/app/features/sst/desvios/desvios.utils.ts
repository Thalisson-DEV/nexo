import { ParamMap } from '@angular/router';
import { DesvioFilter, DesvioDetalhadoResponse } from '../../../core/models/desvio.models';
import { GravidadeDesvio, StatusDesvio } from '../../../core/models/sst.models';
import { normalizarSelecao } from '../../../core/utils/http-params.util';

export interface DesviosFiltrosView {
  status: StatusDesvio[];
  gravidade: GravidadeDesvio[];
  apenasVencidos: boolean | null;
  semPrazo: boolean | null;
  idInspetor: string[];
  idColaborador: string[];
  idFormulario: string[];
  idProcesso: string[];
  idProcessoInspetor: string[];
  idBaseOperacional: string[];
  idCentroCusto: string[];
  idEquipe: string[];
  idVeiculo: string[];
  idPergunta: string[];
  dataInspecaoDe: Date | null;
  dataInspecaoAte: Date | null;
  prazoDe: Date | null;
  prazoAte: Date | null;
  dataResolucaoDe: Date | null;
  dataResolucaoAte: Date | null;
}

export interface DesviosUrlState {
  filtros: DesviosFiltrosView;
  busca: string;
  rows: number;
  first: number;
  sort: string;
}

export function filtrosDesviosVazios(): DesviosFiltrosView {
  return {
    status: [],
    gravidade: [],
    apenasVencidos: null,
    semPrazo: null,
    idInspetor: [],
    idColaborador: [],
    idFormulario: [],
    idProcesso: [],
    idProcessoInspetor: [],
    idBaseOperacional: [],
    idCentroCusto: [],
    idEquipe: [],
    idVeiculo: [],
    idPergunta: [],
    dataInspecaoDe: null,
    dataInspecaoAte: null,
    prazoDe: null,
    prazoAte: null,
    dataResolucaoDe: null,
    dataResolucaoAte: null
  };
}

export function formatarDataLocal(date: Date | null): string | null {
  if (!date) return null;
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function formatarDataHoraIso(date: Date | null): string | null {
  if (!date) return null;
  return date.toISOString();
}

export function parsearDataLocal(value: string | null): Date | null {
  if (!value) return null;
  const date = new Date(`${value.substring(0, 10)}T00:00:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function parsearDataHoraIso(value: string | null): Date | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function montarDesvioFiltro(
  filtros: DesviosFiltrosView,
  busca: string,
  first: number,
  rows: number,
  sort: string
): DesvioFilter {
  return {
    status: filtros.status.length ? filtros.status : null,
    gravidade: filtros.gravidade.length ? filtros.gravidade : null,
    apenasVencidos: filtros.apenasVencidos,
    semPrazo: filtros.semPrazo,
    idInspetor: filtros.idInspetor.length ? filtros.idInspetor : null,
    idColaborador: filtros.idColaborador.length ? filtros.idColaborador : null,
    idFormulario: filtros.idFormulario.length ? filtros.idFormulario : null,
    idProcesso: filtros.idProcesso.length ? filtros.idProcesso : null,
    idProcessoInspetor: filtros.idProcessoInspetor.length ? filtros.idProcessoInspetor : null,
    idBaseOperacional: filtros.idBaseOperacional.length ? filtros.idBaseOperacional : null,
    idCentroCusto: filtros.idCentroCusto.length ? filtros.idCentroCusto : null,
    idEquipe: filtros.idEquipe.length ? filtros.idEquipe : null,
    idVeiculo: filtros.idVeiculo?.length ? filtros.idVeiculo : null,
    idPergunta: filtros.idPergunta?.length ? filtros.idPergunta : null,
    dataInspecaoDe: formatarDataHoraIso(filtros.dataInspecaoDe),
    dataInspecaoAte: formatarDataHoraIso(filtros.dataInspecaoAte),
    prazoDe: formatarDataLocal(filtros.prazoDe),
    prazoAte: formatarDataLocal(filtros.prazoAte),
    dataResolucaoDe: formatarDataHoraIso(filtros.dataResolucaoDe),
    dataResolucaoAte: formatarDataHoraIso(filtros.dataResolucaoAte),
    buscaGlobal: busca.trim() || null,
    page: Math.floor(first / rows),
    size: rows,
    sort
  };
}


export function desvioAtendeFiltros(
  item: DesvioDetalhadoResponse,
  filtros: DesviosFiltrosView,
  buscaGlobal: string
): boolean {
  if (filtros.status.length && !filtros.status.includes(item.status)) return false;
  if (filtros.gravidade.length && !filtros.gravidade.includes(item.gravidade)) return false;
  if (filtros.apenasVencidos && !item.vencido) return false;
  if (filtros.semPrazo && item.prazo !== null) return false;

  if (!atendeDimensao(filtros.idInspetor, item.idInspetor)) return false;
  if (filtros.idColaborador.length > 0) {
    const ids = item.colaboradores.map(c => c.id);
    if (!filtros.idColaborador.some(id => ids.includes(id))) return false;
  }
  
  if (!atendeDimensao(filtros.idFormulario, item.idFormulario)) return false;
  if (!atendeDimensao(filtros.idProcesso, item.processo?.id)) return false;
  if (!atendeDimensao(filtros.idProcessoInspetor, item.processoInspetor?.id)) return false;
  if (!atendeDimensao(filtros.idBaseOperacional, item.baseOperacional?.id)) return false;
  if (!atendeDimensao(filtros.idEquipe, item.equipe?.id)) return false;
  if (!atendeDimensao(filtros.idVeiculo, item.veiculo?.id)) return false;
  if (!atendeDimensao(filtros.idPergunta, item.idPergunta)) return false;

  if (buscaGlobal) {
    const termo = buscaGlobal.toLowerCase();
    const descricao = (item.descricao || '').toLowerCase();
    const acaoCorretiva = (item.acaoCorretiva || '').toLowerCase();
    if (!descricao.includes(termo) && !acaoCorretiva.includes(termo)) return false;
  }

  // Datas são difíceis de verificar localmente com 100% de precisão (fuso, timezone do backend)
  // Como o tratamento geralmente só afeta status e resolução, focamos no que importa
  return true;
}

function atendeDimensao(selecao: string[], valor: string | undefined | null): boolean {
  return selecao.length === 0 || (!!valor && selecao.includes(valor));
}
