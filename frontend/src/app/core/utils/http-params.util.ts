import { HttpParams } from '@angular/common/http';

/**
 * Serialização de filtros para query string.
 *
 * Filtros de múltipla seleção viajam como **parâmetro repetido**
 * (`?idBase=a&idBase=b`) — é o que o `HttpParams` do Angular gera e o que o
 * backend espera. Use sempre `append`: o `set` substitui o valor anterior e
 * derruba silenciosamente todas as opções menos a última.
 *
 * Seleção vazia (`null`, `[]`, `['']`) não envia o parâmetro: mantém a URL
 * limpa e a chave de cache do backend estável.
 */

export type ValorFiltro = string | number | boolean;

/** Acrescenta a seleção de um filtro múltiplo, descartando valores vazios. */
export function comSelecao(
  params: HttpParams,
  chave: string,
  valores?: readonly (ValorFiltro | null | undefined)[] | null
): HttpParams {
  const limpos = (valores ?? []).filter((valor) => valor !== null && valor !== undefined && valor !== '');
  return limpos.reduce((acumulado, valor) => acumulado.append(chave, String(valor)), params);
}

/** `true` quando a dimensão tem ao menos um valor selecionado. */
export function temSelecao(valores?: readonly unknown[] | null): boolean {
  return (valores ?? []).some((valor) => valor !== null && valor !== undefined && valor !== '');
}

/**
 * Normaliza a seleção lida da URL: aceita tanto o parâmetro repetido
 * (`?id=a&id=b`, o que `ParamMap.getAll` devolve) quanto a forma separada por
 * vírgula, e descarta valores vazios.
 */
export function normalizarSelecao(valores?: readonly (string | null)[] | null): string[] {
  return (valores ?? [])
    .flatMap((valor) => (valor ?? '').split(','))
    .map((valor) => valor.trim())
    .filter((valor) => valor.length > 0);
}

export interface OpcoesSerializacao {
  /** Chaves que não devem ir para a query string (ex.: paginação numa exportação). */
  ignorar?: readonly string[];
  /** Transforma o valor antes de serializar; retorne `null` para descartá-lo. */
  transformar?: (chave: string, valor: ValorFiltro) => ValorFiltro | null;
}

/**
 * Converte um objeto de filtros em `HttpParams`. Arrays viram parâmetros
 * repetidos; `null`, `undefined` e string vazia são descartados.
 */
export function toHttpParams(
  filtros: Record<string, unknown>,
  opcoes: OpcoesSerializacao = {}
): HttpParams {
  const { ignorar = [], transformar } = opcoes;
  let params = new HttpParams();

  Object.entries(filtros).forEach(([chave, valor]) => {
    if (ignorar.includes(chave)) return;

    if (Array.isArray(valor)) {
      const convertidos = transformar
        ? valor
            .filter((item): item is ValorFiltro => item !== null && item !== undefined && item !== '')
            .map((item) => transformar(chave, item))
        : valor;
      params = comSelecao(params, chave, convertidos as ValorFiltro[]);
      return;
    }

    if (valor === null || valor === undefined || valor === '') return;

    const convertido = transformar ? transformar(chave, valor as ValorFiltro) : (valor as ValorFiltro);
    if (convertido === null || convertido === undefined || convertido === '') return;
    params = params.append(chave, String(convertido));
  });

  return params;
}
