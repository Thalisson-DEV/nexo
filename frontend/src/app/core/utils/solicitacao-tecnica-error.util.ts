import { HttpErrorResponse } from '@angular/common/http';
import { TipoObraSolicitacao } from '../models/solicitacao-material-tecnica.models';

export interface FieldProblem {
  code: string;
  message: string;
}
export interface SolicitacaoTecnicaProblem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  recurso?: 'SOLICITACAO' | string;
  versaoEsperada?: number;
  versaoAtual?: number;
  erros?: Record<string, string>;
  fieldErrors?: Record<string, FieldProblem>;
}

const CONTROL_CHARS_REGEX = /[\x00-\x1F\x7F-\x9F]/;
export const LIMITE_CARACTERES_OCORRENCIA = 100;

export function normalizeOcorrencia(input: string | null | undefined): string {
  if (input === null || input === undefined) return '';
  return input.normalize('NFKC').trim().toUpperCase().replace(/\s+/g, ' ');
}

export interface ValidacaoOcorrenciaResult {
  valida: boolean;
  erro?: string;
  normalizada: string | null;
}

export function validarOcorrencia(
  input: string | null | undefined,
  tipoObra?: TipoObraSolicitacao | null,
): ValidacaoOcorrenciaResult {
  const normalizada = normalizeOcorrencia(input);

  if (CONTROL_CHARS_REGEX.test(normalizada)) {
    return {
      valida: false,
      erro: 'Ocorrencia nao pode conter caracteres de controle.',
      normalizada: null,
    };
  }

  if (tipoObra === 'OBRA' || tipoObra === 'LPT') {
    return { valida: true, normalizada: null };
  }

  if (tipoObra === 'CORRETIVA' || tipoObra === 'PREVENTIVA') {
    if (!normalizada) {
      return {
        valida: false,
        erro: 'Ocorrencia e obrigatoria para obras corretivas e preventivas.',
        normalizada: null,
      };
    }
    if (normalizada.length > LIMITE_CARACTERES_OCORRENCIA) {
      return {
        valida: false,
        erro: `Ocorrencia nao pode exceder ${LIMITE_CARACTERES_OCORRENCIA} caracteres.`,
        normalizada: null,
      };
    }
    return { valida: true, normalizada };
  }

  if (normalizada.length > LIMITE_CARACTERES_OCORRENCIA) {
    return {
      valida: false,
      erro: `Ocorrencia nao pode exceder ${LIMITE_CARACTERES_OCORRENCIA} caracteres.`,
      normalizada: null,
    };
  }

  return { valida: true, normalizada: normalizada || null };
}

export function problemaTecnico(error: unknown): SolicitacaoTecnicaProblem | null {
  if (!(error instanceof HttpErrorResponse)) return null;
  return (error.error ?? null) as SolicitacaoTecnicaProblem | null;
}

export function errosDeCampo(error: unknown): Record<string, FieldProblem> {
  const problem = problemaTecnico(error);
  if (problem?.fieldErrors) return problem.fieldErrors;
  return Object.fromEntries(
    Object.entries(problem?.erros ?? {}).map(([field, message]) => [
      field,
      { code: 'PREENCHIMENTO_INVALIDO', message },
    ]),
  );
}

export function ehConflitoDeVersao(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse)) return false;
  const problem = problemaTecnico(error);
  return (
    problem?.code === 'VERSAO_DESATUALIZADA' ||
    (problem?.versaoEsperada !== undefined && problem?.versaoAtual !== undefined)
  );
}

export function obterVersaoAtualConflito(error: unknown): number | null {
  const problem = problemaTecnico(error);
  return problem?.versaoAtual ?? null;
}

export function ehFalhaDeTransporte(error: unknown): boolean {
  return (
    error instanceof HttpErrorResponse &&
    (error.status === 0 || error.status === 408 || error.status === 504)
  );
}

export function mensagemErroSolicitacao(
  error: unknown,
  fallback = 'Ocorreu um erro ao processar a solicitacao.',
): string {
  if (ehConflitoDeVersao(error)) {
    const versaoAtual = obterVersaoAtualConflito(error);
    return versaoAtual !== null
      ? `A solicitacao foi modificada por outro operador (versao no servidor: ${versaoAtual}). Os dados foram recarregados.`
      : 'A solicitacao foi modificada concorrentemente por outro operador. Os dados foram recarregados.';
  }

  if (ehFalhaDeTransporte(error)) {
    return 'Falha de conexao com o servidor. A operacao pode ser repetida com a mesma chave de seguranca.';
  }

  const problem = problemaTecnico(error);
  if (problem?.detail) return problem.detail;
  if (problem?.title) return problem.title;
  if (error instanceof HttpErrorResponse && error.message) return error.message;
  if (error instanceof Error && error.message) return error.message;

  return fallback;
}

/**
 * Dicionário de mensagens técnicas → mensagens amigáveis para o usuário.
 * Cobre as validações mais comuns devolvidas pelo backend.
 */
const MENSAGENS_AMIGAVEIS: Record<string, string> = {
  'O tipo do item deve ser o mesmo tipo imutavel da solicitacao.': 'O tipo de movimentação de cada item é fixo e segue o tipo da solicitação.',
  'O tipo do item deve ser o mesmo tipo imutavel da solicitacao': 'O tipo de movimentação de cada item é fixo e segue o tipo da solicitação.',
};

/**
 * Transforma as chaves e mensagens técnicas de erro de campo em
 * rótulos compreensíveis pelo usuário final.
 *
 * - Paths como `itens[0].tipoMovimentacao` viram `itens`
 * - Mensagens conhecidas são substituídas por versões amigáveis
 */
export function humanizarErrosDeCampo(
  raw: Record<string, FieldProblem>,
): Record<string, string> {
  const result: Record<string, string> = {};
  for (const [rawKey, problem] of Object.entries(raw)) {
    const simplifiedKey = rawKey.replace(/\[\d+\].*$/, '').replace(/\[.*$/, '');
    const friendlyMessage =
      MENSAGENS_AMIGAVEIS[problem.message.trim()] ??
      MENSAGENS_AMIGAVEIS[problem.message.replace(/\.\s*$/, '').trim()] ??
      problem.message;
    // If multiple field errors map to the same simplified key, keep the first one
    if (!result[simplifiedKey]) {
      result[simplifiedKey] = friendlyMessage;
    }
  }
  return result;
}
