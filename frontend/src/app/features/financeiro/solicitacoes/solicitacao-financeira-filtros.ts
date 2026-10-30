import { QueryField, QuerySchema, qp } from '../../../core/navigation';
import {
  SolicitacaoFinanceiraFilter,
  StatusSolicitacao,
  TipoSolicitacao
} from '../../../core/models/financeiro.models';

export const CAMPOS_DATA_LOCAL = ['vencimentoInicio', 'vencimentoFim'] as const;
export const CAMPOS_DATA_HORA = [
  'dataSubmissaoInicio',
  'dataSubmissaoFim',
  'dataDecisaoInicio',
  'dataDecisaoFim',
  'dataCancelamentoInicio',
  'dataCancelamentoFim',
  'dataUltimaAlteracaoInicio',
  'dataUltimaAlteracaoFim',
  'dataDesativacaoInicio',
  'dataDesativacaoFim',
  'dataCriacaoInicio',
  'dataCriacaoFim'
] as const;

type CampoDataLocal = (typeof CAMPOS_DATA_LOCAL)[number];
type CampoDataHora = (typeof CAMPOS_DATA_HORA)[number];
type CampoData = CampoDataLocal | CampoDataHora;

export type SolicitacaoFiltrosView = Omit<
  SolicitacaoFinanceiraFilter,
  CampoData | 'page' | 'size' | 'sort' | 'buscaGlobal'
> & {
  tipo?: TipoSolicitacao | 'AMBOS';
} & Record<CampoData, Date | null>;

const CAMPOS_NUMERICOS = [
  'numero',
  'valorMinimo',
  'valorMaximo',
  'quantidadeMinima',
  'quantidadeMaxima',
  'parcelasMinimas',
  'parcelasMaximas',
  'valorAprovadoMinimo',
  'valorAprovadoMaximo'
] as const satisfies readonly (keyof SolicitacaoFinanceiraFilter)[];

const CAMPOS_BOOLEANOS = [
  'apenasMinhas',
  'temNf',
  'possuiDocumentos',
  'ativo',
  'possuiCompraPrevia'
] as const satisfies readonly (keyof SolicitacaoFinanceiraFilter)[];

const CAMPOS_TEXTO = [
  'formularioId', 'baseId', 'processoId', 'obraId', 'fornecedorId', 'veiculoId',
  'solicitanteColaboradorId', 'prioridade', 'localAquisicao', 'formaPagamento',
  'regimeVeiculo', 'naturezaServico', 'motivoServico', 'tipoDespesa',
  'usuarioSolicitanteId', 'numeroNf', 'usuarioDecisaoId', 'usuarioCancelamentoId',
  'solicitacaoCompraId', 'motivoSemCompra'
] as const satisfies readonly (keyof SolicitacaoFinanceiraFilter)[];

const STATUS: StatusSolicitacao[] = ['RASCUNHO', 'SOLICITADO', 'APROVADO', 'REJEITADO', 'CANCELADO'];

/**
 * Filtros da listagem na URL, com os mesmos nomes já usados pelo drill do dashboard e pelos
 * links externos (`status`, `dataCriacaoInicio`...). IDs, nunca nomes ou documentos.
 */
export function schemaFiltrosSolicitacao(
  tipo: TipoSolicitacao | 'AMBOS',
  modo: 'minhas' | 'aprovacao' | 'todas'
): QuerySchema<SolicitacaoFiltrosView> {
  const schema: Record<string, QueryField<unknown>> = {
    ativo: qp.tristate('ativo', true) as QueryField<unknown>,
  };
  // Na fila de aprovação o status é fixo (SOLICITADO); em rota de um tipo só, o tipo também.
  if (modo !== 'aprovacao') schema['status'] = qp.oneOf('status', STATUS) as QueryField<unknown>;
  if (tipo === 'AMBOS') schema['tipo'] = qp.oneOf('tipo', ['COMPRA', 'PAGAMENTO']) as QueryField<unknown>;
  for (const chave of CAMPOS_NUMERICOS) schema[chave] = qp.number(chave) as QueryField<unknown>;
  for (const chave of CAMPOS_BOOLEANOS) {
    if (chave !== 'ativo') schema[chave] = qp.boolean(chave, { omitFalse: false }) as QueryField<unknown>;
  }
  for (const chave of [...CAMPOS_DATA_LOCAL, ...CAMPOS_DATA_HORA]) {
    schema[chave] = qp.date(chave) as QueryField<unknown>;
  }
  for (const chave of CAMPOS_TEXTO) schema[chave] = qp.text(chave) as QueryField<unknown>;
  return schema as QuerySchema<SolicitacaoFiltrosView>;
}

interface LeitorQuery {
  get(chave: string): string | null;
}

export function criarFiltrosSolicitacao(
  tipo: TipoSolicitacao | 'AMBOS',
  modo: 'minhas' | 'aprovacao' | 'todas',
  query: LeitorQuery
): SolicitacaoFiltrosView {
  const filtros = {
    tipo,
    status: modo === 'aprovacao' ? 'SOLICITADO' : statusValido(query.get('status')),
    ativo: booleanoDaQuery(query.get('ativo')) ?? true
  } as SolicitacaoFiltrosView;

  for (const chave of CAMPOS_NUMERICOS) {
    const valor = numeroDaQuery(query.get(chave));
    if (valor !== null) filtros[chave] = valor as never;
  }
  for (const chave of CAMPOS_BOOLEANOS) {
    if (chave === 'ativo') continue;
    const valor = booleanoDaQuery(query.get(chave));
    if (valor !== null) filtros[chave] = valor as never;
  }
  for (const chave of [...CAMPOS_DATA_LOCAL, ...CAMPOS_DATA_HORA]) {
    filtros[chave] = dataDaQuery(query.get(chave));
  }

  for (const chave of CAMPOS_TEXTO) {
    const valor = query.get(chave)?.trim();
    if (valor) (filtros as unknown as Record<string, unknown>)[chave] = valor;
  }

  return filtros;
}

export function normalizarFiltrosSolicitacao(
  filtros: SolicitacaoFiltrosView
): SolicitacaoFinanceiraFilter {
  const normalizados: Record<string, unknown> = {};

  for (const [chave, valor] of Object.entries(filtros)) {
    if (chave === 'tipo' || valor === null || valor === undefined || valor === '') continue;
    if (valor instanceof Date) {
      normalizados[chave] = (CAMPOS_DATA_LOCAL as readonly string[]).includes(chave)
        ? formatarDataLocal(valor)
        : formatarLimiteDataHora(valor, chave.endsWith('Fim'));
      continue;
    }
    normalizados[chave] = typeof valor === 'string' ? valor.trim() : valor;
  }

  return normalizados as SolicitacaoFinanceiraFilter;
}

export function formatarDataLocal(data: Date): string {
  const ano = data.getFullYear();
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}

export function formatarLimiteDataHora(data: Date, fimDoDia: boolean): string {
  const limite = new Date(data);
  limite.setHours(fimDoDia ? 23 : 0, fimDoDia ? 59 : 0, fimDoDia ? 59 : 0, fimDoDia ? 999 : 0);

  const offsetTotal = -limite.getTimezoneOffset();
  const sinal = offsetTotal >= 0 ? '+' : '-';
  const offsetAbsoluto = Math.abs(offsetTotal);
  const offsetHoras = String(Math.floor(offsetAbsoluto / 60)).padStart(2, '0');
  const offsetMinutos = String(offsetAbsoluto % 60).padStart(2, '0');
  const hora = String(limite.getHours()).padStart(2, '0');
  const minuto = String(limite.getMinutes()).padStart(2, '0');
  const segundo = String(limite.getSeconds()).padStart(2, '0');
  const milissegundo = String(limite.getMilliseconds()).padStart(3, '0');

  return `${formatarDataLocal(limite)}T${hora}:${minuto}:${segundo}.${milissegundo}${sinal}${offsetHoras}:${offsetMinutos}`;
}

function statusValido(valor: string | null): StatusSolicitacao | null {
  return STATUS.includes(valor as StatusSolicitacao) ? (valor as StatusSolicitacao) : null;
}

function booleanoDaQuery(valor: string | null): boolean | null {
  if (valor === 'true') return true;
  if (valor === 'false') return false;
  return null;
}

function numeroDaQuery(valor: string | null): number | null {
  if (valor === null || valor.trim() === '') return null;
  const numero = Number(valor);
  return Number.isFinite(numero) ? numero : null;
}

function dataDaQuery(valor: string | null): Date | null {
  const resultado = /^(\d{4})-(\d{2})-(\d{2})/.exec(valor ?? '');
  if (!resultado) return null;
  const data = new Date(Number(resultado[1]), Number(resultado[2]) - 1, Number(resultado[3]));
  return Number.isNaN(data.getTime()) ? null : data;
}
