import {
  EventoSstFilter,
  RegistroEvidencia,
  StatusEventoSst,
  TipoEventoSst
} from '../../../core/models/sst-evento.models';

/**
 * O recorte da listagem como a tela o monta: datas são `Date` e
 * seleções são sempre array. A conversão para o contrato da API acontece em
 * {@link montarEventoFiltro} — mesmo desenho já usado na listagem de desvios.
 */
export interface EventosFiltrosView {
  tipo: TipoEventoSst[];
  status: StatusEventoSst[];
  idClassificacao: string[];
  idAtividade: string[];
  idGestor: string[];
  idProcesso: string[];
  idBaseOperacional: string[];
  idCentroCusto: string[];
  idEquipe: string[];
  idVeiculo: string[];
  idTecnico: string[];
  idColaboradorEnvolvido: string[];
  idLocalLesao: string[];
  telemetria: RegistroEvidencia[];
  filmagem: RegistroEvidencia[];
  possuiCat: boolean | null;
  comLesao: boolean | null;
  dataOcorrenciaDe: Date | null;
  dataOcorrenciaAte: Date | null;
  dataComunicacaoDe: Date | null;
  dataComunicacaoAte: Date | null;
  nomeColaborador: string;
  nomeTerceiro: string;
}

/** Início do dia para a borda inicial; fim do dia para a final — bordas inclusivas. */
function isoDe(date: Date | null, fimDoDia: boolean): string | null {
  if (!date) return null;
  const copia = new Date(date);
  copia.setHours(fimDoDia ? 23 : 0, fimDoDia ? 59 : 0, fimDoDia ? 59 : 0, fimDoDia ? 999 : 0);
  return copia.toISOString();
}

export function montarEventoFiltro(
  filtros: EventosFiltrosView,
  busca: string,
  first: number,
  rows: number,
  sort: string
): EventoSstFilter {
  return {
    ...recortesDe(filtros),
    buscaGlobal: busca.trim() || null,
    page: Math.floor(first / rows),
    size: rows,
    sort
  };
}

/** Os mesmos recortes, sem paginação — é o que a exportação usa. */
export function montarEventoFiltroExportacao(
  filtros: EventosFiltrosView,
  busca: string
): EventoSstFilter {
  return { ...recortesDe(filtros), buscaGlobal: busca.trim() || null };
}

function recortesDe(filtros: EventosFiltrosView): EventoSstFilter {
  return {
    tipo: filtros.tipo.length ? filtros.tipo : null,
    status: filtros.status.length ? filtros.status : null,
    idClassificacao: filtros.idClassificacao.length ? filtros.idClassificacao : null,
    idAtividade: filtros.idAtividade.length ? filtros.idAtividade : null,
    idGestor: filtros.idGestor.length ? filtros.idGestor : null,
    idProcesso: filtros.idProcesso.length ? filtros.idProcesso : null,
    idBaseOperacional: filtros.idBaseOperacional.length ? filtros.idBaseOperacional : null,
    idCentroCusto: filtros.idCentroCusto.length ? filtros.idCentroCusto : null,
    idEquipe: filtros.idEquipe.length ? filtros.idEquipe : null,
    idVeiculo: filtros.idVeiculo.length ? filtros.idVeiculo : null,
    idTecnico: filtros.idTecnico.length ? filtros.idTecnico : null,
    idColaboradorEnvolvido: filtros.idColaboradorEnvolvido.length ? filtros.idColaboradorEnvolvido : null,
    idLocalLesao: filtros.idLocalLesao.length ? filtros.idLocalLesao : null,
    telemetria: filtros.telemetria.length ? filtros.telemetria : null,
    filmagem: filtros.filmagem.length ? filtros.filmagem : null,
    possuiCat: filtros.possuiCat,
    comLesao: filtros.comLesao,
    dataOcorrenciaDe: isoDe(filtros.dataOcorrenciaDe, false),
    dataOcorrenciaAte: isoDe(filtros.dataOcorrenciaAte, true),
    dataComunicacaoDe: isoDe(filtros.dataComunicacaoDe, false),
    dataComunicacaoAte: isoDe(filtros.dataComunicacaoAte, true),
    nomeColaborador: filtros.nomeColaborador.trim() || null,
    nomeTerceiro: filtros.nomeTerceiro.trim() || null
  };
}

/** Baixa um CSV já recebido como blob, com o nome informado. */
export function baixarCsv(blob: Blob, nomeArquivo: string): void {
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = nomeArquivo;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  window.URL.revokeObjectURL(url);
}
