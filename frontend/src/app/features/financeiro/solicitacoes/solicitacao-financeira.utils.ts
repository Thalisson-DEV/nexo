import { SolicitacaoFinanceira } from '../../../core/models/financeiro.models';

/**
 * Visão compatível com as respostas antes da inclusão do fornecedor resolvido.
 * O campo é opcional de propósito: durante a implantação do contrato novo o
 * frontend precisa continuar renderizando respostas antigas sem nova consulta.
 */
export interface FornecedorSolicitacaoView {
  id?: string | null;
  nome?: string | null;
  razaoSocial?: string | null;
  cadastrado?: boolean | null;
}

interface SolicitacaoComFornecedor {
  fornecedor?: FornecedorSolicitacaoView | null;
}

type IdentificacaoSolicitante = Pick<
  SolicitacaoFinanceira,
  'nomeColaboradorSolicitante' | 'usuarioSolicitante'
>;

export function nomeSolicitante(solicitacao: IdentificacaoSolicitante): string {
  return (
    solicitacao.nomeColaboradorSolicitante ??
    solicitacao.usuarioSolicitante ??
    'Solicitante não identificado'
  );
}

function textoFornecedor(valor: unknown): string | null {
  return typeof valor === 'string' && valor.trim().length > 0 ? valor.trim() : null;
}

function fornecedorDa(solicitacao: SolicitacaoComFornecedor): FornecedorSolicitacaoView | null {
  const fornecedor = solicitacao.fornecedor;
  return fornecedor && typeof fornecedor === 'object' ? fornecedor : null;
}

/** Nome principal do fornecedor, com fallback para respostas legadas ou incompletas. */
export function fornecedorLabel(solicitacao: SolicitacaoComFornecedor): string {
  const fornecedor = fornecedorDa(solicitacao);
  return textoFornecedor(fornecedor?.nome) ?? textoFornecedor(fornecedor?.razaoSocial) ?? '—';
}

/** Razão social somente quando é uma informação secundária útil e diferente do nome. */
export function fornecedorRazaoSocial(solicitacao: SolicitacaoComFornecedor): string | null {
  const fornecedor = fornecedorDa(solicitacao);
  const nome = textoFornecedor(fornecedor?.nome);
  const razaoSocial = textoFornecedor(fornecedor?.razaoSocial);
  if (!razaoSocial || !nome || razaoSocial.localeCompare(nome, undefined, { sensitivity: 'base' }) === 0) {
    return null;
  }
  return razaoSocial;
}

/** O marcador informa fornecedor digitado livremente, sem depender da cor. */
export function fornecedorEhNaoCadastrado(solicitacao: SolicitacaoComFornecedor): boolean {
  return fornecedorDa(solicitacao)?.cadastrado === false;
}

/** Descarta busca vazia, preservando caracteres especiais e espaços internos. */
export function buscaGlobalParaConsulta(value: string | null | undefined): string | null {
  const valorNormalizado = value?.trim();
  return valorNormalizado ? valorNormalizado : null;
}
