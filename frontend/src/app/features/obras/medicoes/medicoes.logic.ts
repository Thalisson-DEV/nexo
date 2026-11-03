import { CesTone } from '@ces/ui';
import {
  ContextoObra,
  ItemPendenteMedicao,
  LinhaPreFechamento,
  MedicaoResumo,
  ObraNaPlanilha,
  SituacaoExecucaoObra,
  StatusMedicao,
  TipoMedicao
} from '../../../core/models/obras-medicoes.models';

export interface Rotulo {
  rotulo: string;
  tom: CesTone;
  icone: string;
}

/** Concluído, pronto para concluir (nada sem kit) ou pendente; a forma vem do ícone, não só da cor. */
export function situacaoLevantamento(l: Pick<LinhaPreFechamento, 'concluido' | 'prontoParaConcluir' | 'pontos'>): Rotulo {
  if (l.concluido) return { rotulo: 'Concluído', tom: 'success', icone: 'pi-check-circle' };
  if (l.prontoParaConcluir) return { rotulo: 'Pronto para concluir', tom: 'info', icone: 'pi-flag' };
  if (!l.pontos) return { rotulo: 'Sem levantamento', tom: 'neutral', icone: 'pi-minus-circle' };
  return { rotulo: 'Pendente', tom: 'warning', icone: 'pi-clock' };
}

/** O que falta para concluir, em uma frase curta. */
export function pendenciasLevantamento(l: Pick<LinhaPreFechamento, 'pontos' | 'pontosSemKit' | 'vaosSemKit' | 'concluido'>): string | null {
  if (l.concluido) return null;
  if (!l.pontos) return 'Nenhum ponto lançado no planejamento';
  const partes: string[] = [];
  if (l.pontosSemKit) partes.push(l.pontosSemKit === 1 ? '1 ponto sem kit' : `${l.pontosSemKit} pontos sem kit`);
  if (l.vaosSemKit) partes.push(l.vaosSemKit === 1 ? '1 vão sem kit' : `${l.vaosSemKit} vãos sem kit`);
  return partes.length ? partes.join(' e ') : null;
}

export const TIPO_MEDICAO: Record<TipoMedicao, string> = { PARCIAL: 'Parcial', CICLO: 'Por ciclo' };

export const STATUS_MEDICAO: Record<StatusMedicao, Rotulo> = {
  GERADA: { rotulo: 'Gerada', tom: 'success', icone: 'pi-check' },
  ESTORNADA: { rotulo: 'Estornada', tom: 'neutral', icone: 'pi-undo' }
};

export const SITUACAO_EXECUCAO: Record<SituacaoExecucaoObra, Rotulo> = {
  NAO_INICIADA: { rotulo: 'Não iniciada', tom: 'neutral', icone: 'pi-circle' },
  EM_ANDAMENTO: { rotulo: 'Em andamento', tom: 'info', icone: 'pi-spinner' },
  CONCLUIDA: { rotulo: 'Concluída', tom: 'success', icone: 'pi-check-circle' }
};

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/** "2026-11" → "nov/2026". */
export function rotuloCompetencia(competencia: string | null | undefined): string {
  if (!competencia) return '—';
  const [ano, mes] = competencia.split('-').map(Number);
  return ano && mes ? `${MESES[mes - 1]}/${ano}` : competencia;
}

export function competenciaDe(data: Date): string {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}`;
}

/** Competências para escolher no ciclo: a atual e as onze anteriores. */
export function competenciasRecentes(hoje: Date, quantidade = 12): { label: string; value: string }[] {
  const lista: { label: string; value: string }[] = [];
  for (let i = 0; i < quantidade; i++) {
    const valor = competenciaDe(new Date(hoje.getFullYear(), hoje.getMonth() - i, 1));
    lista.push({ label: rotuloCompetencia(valor), value: valor });
  }
  return lista;
}

/** dd/MM/yyyy → yyyy-MM, para comparar com a competência. */
function mesDaData(data: string | null): string | null {
  if (!data) return null;
  const [, mes, ano] = data.split('/');
  return ano && mes ? `${ano}-${mes}` : null;
}

/** No ciclo só entra o que foi concluído até o fim da competência. */
export function cabeNoCiclo(item: Pick<ItemPendenteMedicao, 'concluidoEm'>, competencia: string): boolean {
  const mes = mesDaData(item.concluidoEm);
  return mes == null || mes <= competencia;
}

export function somarItens(itens: readonly Pick<ItemPendenteMedicao, 'valorTotal'>[]): number {
  return itens.reduce((total, i) => total + (i.valorTotal ?? 0), 0);
}

export function somarGeradas(medicoes: readonly Pick<MedicaoResumo, 'status' | 'valorTotal'>[]): number {
  return medicoes.filter((m) => m.status === 'GERADA').reduce((total, m) => total + m.valorTotal, 0);
}

/** Percentual do valor de projeto já medido; nulo sem valor de projeto. */
export function percentualMedido(medido: number, projeto: number | null | undefined): number | null {
  return projeto ? Math.round((medido / projeto) * 1000) / 10 : null;
}

/** Quantos códigos o texto colado tem, pelo mesmo critério do servidor. */
export function contarCodigos(texto: string): number {
  return new Set(texto.split(/[\r\n,;\t ]+/).map((c) => c.trim()).filter(Boolean)).size;
}

export type SituacaoLevantamento = 'PENDENTE' | 'PRONTO' | 'SEM_LEVANTAMENTO' | 'CONCLUIDO';
export type PendenciaLevantamento = 'PONTO_SEM_KIT' | 'VAO_SEM_KIT' | 'PONTO_SEM_VAO';
export type ExecucaoPonto = 'COM_DESLIGAMENTO' | 'SEM_DESLIGAMENTO' | 'NAO_DEFINIDO';

export const SITUACOES_LEVANTAMENTO: { label: string; value: SituacaoLevantamento }[] = [
  { label: 'Pendente', value: 'PENDENTE' },
  { label: 'Pronto para concluir', value: 'PRONTO' },
  { label: 'Sem levantamento', value: 'SEM_LEVANTAMENTO' },
  { label: 'Concluído', value: 'CONCLUIDO' }
];
export const PENDENCIAS: { label: string; value: PendenciaLevantamento }[] = [
  { label: 'Ponto sem kit', value: 'PONTO_SEM_KIT' },
  { label: 'Vão sem kit', value: 'VAO_SEM_KIT' },
  { label: 'Ponto sem vão', value: 'PONTO_SEM_VAO' }
];
export const EXECUCOES: { label: string; value: ExecucaoPonto }[] = [
  { label: 'Com desligamento', value: 'COM_DESLIGAMENTO' },
  { label: 'Sem desligamento', value: 'SEM_DESLIGAMENTO' },
  { label: 'Não definido', value: 'NAO_DEFINIDO' }
];
export const PRIORIDADES: { label: string; value: string }[] = [
  { label: 'Urgente', value: 'URGENTE' },
  { label: 'Alta', value: 'ALTA' },
  { label: 'Normal', value: 'NORMAL' },
  { label: 'Baixa', value: 'BAIXA' }
];

export function codigoSituacao(l: Pick<LinhaPreFechamento, 'concluido' | 'prontoParaConcluir' | 'pontos'>): SituacaoLevantamento {
  if (l.concluido) return 'CONCLUIDO';
  if (l.prontoParaConcluir) return 'PRONTO';
  return l.pontos ? 'PENDENTE' : 'SEM_LEVANTAMENTO';
}

/** Recorte da carteira: lista vazia não restringe. */
export interface RecorteObra {
  bases: readonly string[];
  municipios: readonly string[];
  supervisores: readonly string[];
  status: readonly string[];
  prioridades: readonly string[];
}

function cabe(lista: readonly string[], valor: string | null | undefined): boolean {
  return !lista.length || (valor != null && lista.includes(valor));
}

export function noRecorte(c: ContextoObra | null | undefined, r: RecorteObra): boolean {
  return (
    cabe(r.bases, c?.idBaseOperacional) &&
    cabe(r.municipios, c?.idMunicipio) &&
    cabe(r.supervisores, c?.idSupervisor) &&
    cabe(r.status, c?.statusObra) &&
    cabe(r.prioridades, c?.prioridade)
  );
}

export interface RecortePreFechamento extends RecorteObra {
  levantadores: readonly string[];
  pendencias: readonly PendenciaLevantamento[];
  execucoes: readonly ExecucaoPonto[];
}

/** Recorte do pré-fechamento sem a situação, que é atalho dos indicadores e filtra só a tabela. */
export function noRecortePreFechamento(l: LinhaPreFechamento, r: RecortePreFechamento): boolean {
  if (!noRecorte(l.contexto, r)) return false;
  if (r.levantadores.length && !l.levantadores.some((u) => r.levantadores.includes(u))) return false;
  const temPendencia: Record<PendenciaLevantamento, boolean> = {
    PONTO_SEM_KIT: l.pontosSemKit > 0,
    VAO_SEM_KIT: l.vaosSemKit > 0,
    PONTO_SEM_VAO: l.pontosSemVao > 0
  };
  if (r.pendencias.length && !r.pendencias.some((p) => temPendencia[p])) return false;
  const temExecucao: Record<ExecucaoPonto, boolean> = {
    COM_DESLIGAMENTO: l.comDesligamento > 0,
    SEM_DESLIGAMENTO: l.semDesligamento > 0,
    NAO_DEFINIDO: l.naoDefinido > 0
  };
  return !r.execucoes.length || r.execucoes.some((e) => temExecucao[e]);
}

/** Opções distintas, em ordem alfabética, a partir dos dados carregados. */
export function opcoesDistintas<T>(
  itens: readonly T[],
  id: (i: T) => string | null | undefined,
  nome: (i: T) => string | null | undefined
): { label: string; value: string }[] {
  const mapa = new Map<string, string>();
  for (const item of itens) {
    const chave = id(item);
    if (chave && !mapa.has(chave)) mapa.set(chave, nome(item) || chave);
  }
  return [...mapa].map(([value, label]) => ({ value, label })).sort((a, b) => a.label.localeCompare(b.label, 'pt-BR'));
}

export const LIMITE_RELATORIO_DETALHADO = 10;

/** Obra na planilha orçado × faturado: com erro não gera; com lançamento a conferir, gera com aviso. */
export function situacaoNaPlanilha(o: Pick<ObraNaPlanilha, 'erros' | 'paraConferir'>): Rotulo {
  if (o.erros.length) return { rotulo: o.erros.length === 1 ? '1 linha com erro' : `${o.erros.length} linhas com erro`, tom: 'danger', icone: 'pi-times-circle' };
  if (o.paraConferir) return { rotulo: o.paraConferir === 1 ? '1 lançamento a conferir' : `${o.paraConferir} lançamentos a conferir`, tom: 'warning', icone: 'pi-exclamation-triangle' };
  return { rotulo: 'Pronta', tom: 'success', icone: 'pi-check-circle' };
}

/** Por que o pacote ainda não pode ser gerado; null quando pode. */
export function bloqueioDoRelatorio(escolhidas: readonly Pick<ObraNaPlanilha, 'numero' | 'erros'>[], errosSemObra: number): string | null {
  if (errosSemObra) return 'Há linhas sem o número da obra na planilha. Corrija e envie de novo.';
  if (!escolhidas.length) return 'Escolha ao menos uma obra.';
  if (escolhidas.length > LIMITE_RELATORIO_DETALHADO) return `Gere até ${LIMITE_RELATORIO_DETALHADO} obras por pacote.`;
  const comErro = escolhidas.filter((o) => o.erros.length).map((o) => o.numero);
  if (comErro.length === 1) return `Corrija a planilha da obra ${comErro[0]} ou tire-a da seleção.`;
  if (comErro.length) return `Corrija a planilha das obras ${comErro.join(', ')} ou tire-as da seleção.`;
  return null;
}

