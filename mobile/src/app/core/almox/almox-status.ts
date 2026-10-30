import { NomeIcone } from '../../ui/icone.component';
import { Condicao, ResumoSolicitacao, Solicitacao, StatusSolicitacao, TipoMovimentacao } from './almox.models';

/** Tom visual do app (sempre junto de ícone e texto — cor nunca é o único sinal). */
export type Tom = 'ok' | 'atencao' | 'erro' | 'destaque' | 'neutro';

export interface DefStatus {
  valor: StatusSolicitacao;
  rotulo: string;
  /** O que falta fazer — usado nos atalhos de filtro. */
  atalho: string;
  tom: Tom;
  icone: NomeIcone;
}

/** Mesmo vocabulário do web (tabela, quadro, calendário e detalhe). */
export const STATUS: readonly DefStatus[] = [
  { valor: 'PENDENTE', rotulo: 'Pendente', atalho: 'Aguardando aprovação', tom: 'atencao', icone: 'relogio' },
  { valor: 'APROVADO', rotulo: 'Aprovada', atalho: 'Aguardando retirada', tom: 'destaque', icone: 'check' },
  { valor: 'ENTREGUE_PARCIAL', rotulo: 'Entregue em parte', atalho: 'Entregue em parte', tom: 'atencao', icone: 'caminhao' },
  { valor: 'ENTREGUE', rotulo: 'Entregue', atalho: 'Entregue', tom: 'ok', icone: 'check' },
  { valor: 'REJEITADO', rotulo: 'Rejeitada', atalho: 'Rejeitada', tom: 'erro', icone: 'fechar' },
  { valor: 'ESTORNADO', rotulo: 'Estornada', atalho: 'Estornada', tom: 'neutro', icone: 'desfazer' },
];

export const ORDEM_STATUS = STATUS.map(s => s.valor);

/** Ainda há trabalho do almoxarifado: aprovar ou entregar. */
export const EM_ABERTO: readonly StatusSolicitacao[] = ['PENDENTE', 'APROVADO', 'ENTREGUE_PARCIAL'];

const POR_VALOR = new Map(STATUS.map(s => [s.valor as string, s]));

export function defStatus(status?: string | null): DefStatus {
  return POR_VALOR.get(status ?? '') ?? { valor: status as StatusSolicitacao, rotulo: status || '—', atalho: status || '—', tom: 'neutro', icone: 'info' };
}

/** `aaaa-mm-dd` no fuso do aparelho (data civil, sem deslocar para UTC). */
export function isoCivil(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function dataCivil(iso: string): Date {
  const [a, m, d] = iso.substring(0, 10).split('-').map(Number);
  return new Date(a, m - 1, d);
}

/** Saque previsto vencido com trabalho em aberto (saldo encerrado não conta). */
export function saqueAtrasado(s: Pick<ResumoSolicitacao, 'dataPrevista' | 'status' | 'encerrada'> | null | undefined, hoje = new Date()): boolean {
  if (!s?.dataPrevista || s.encerrada || !EM_ABERTO.includes(s.status)) return false;
  return s.dataPrevista.substring(0, 10) < isoCivil(hoje);
}

/** "hoje", "amanhã", "em 3 dias", "há 2 dias". */
export function prazoRelativo(dataPrevista: string, hoje = new Date()): string {
  const base = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());
  const dias = Math.round((dataCivil(dataPrevista).getTime() - base.getTime()) / 86_400_000);
  if (dias === 0) return 'hoje';
  if (dias === 1) return 'amanhã';
  if (dias === -1) return 'ontem';
  return dias > 0 ? `em ${dias} dias` : `há ${-dias} dias`;
}

/** A política do backend manda; sem ela, a regra pelo status (igual ao web). */
export function pode(s: Pick<Solicitacao, 'acoesPermitidas' | 'encerrada' | 'status'> | Pick<ResumoSolicitacao, 'acoesPermitidas' | 'encerrada' | 'status'> | null | undefined,
  acao: 'APROVAR' | 'REJEITAR' | 'ENTREGAR'): boolean {
  if (!s) return false;
  if (s.acoesPermitidas) return s.acoesPermitidas.includes(acao);
  const porStatus: Record<string, StatusSolicitacao[]> = {
    APROVAR: ['PENDENTE'], REJEITAR: ['PENDENTE', 'APROVADO'], ENTREGAR: ['APROVADO', 'ENTREGUE_PARCIAL'],
  };
  return !s.encerrada && porStatus[acao].includes(s.status);
}

export function nomeOrigem(s: ResumoSolicitacao): string {
  return s.estoqueOrigemNome || s.estoqueOrigemNomeIdentificador || 'Origem';
}

export function nomeDestino(s: ResumoSolicitacao): string {
  return s.estoqueDestinoNome || s.estoqueDestinoNomeIdentificador || 'Destino';
}

export function idCurto(id: string | null | undefined): string {
  return (id ?? '').substring(0, 8).toUpperCase();
}

export const CONDICOES: { valor: Condicao; rotulo: string }[] = [
  { valor: 'NOVO', rotulo: 'Novo' },
  { valor: 'RECUPERADO', rotulo: 'Recuperado' },
  { valor: 'USADO', rotulo: 'Usado' },
  { valor: 'SUCATA', rotulo: 'Sucata' },
];

export function rotuloCondicao(c?: string | null): string {
  return CONDICOES.find(x => x.valor === c)?.rotulo ?? 'Novo';
}

export interface DefTipoMov { rotulo: string; icone: NomeIcone; tom: Tom }

export const TIPOS_MOVIMENTACAO: Record<TipoMovimentacao, DefTipoMov> = {
  ENTRADA_AVULSA: { rotulo: 'Entrada', icone: 'entrada', tom: 'ok' },
  BAIXA_AVULSA: { rotulo: 'Saída', icone: 'saida', tom: 'erro' },
  TRANSFERENCIA: { rotulo: 'Transferência', icone: 'transferencia', tom: 'destaque' },
  ENTREGA: { rotulo: 'Entrega de solicitação', icone: 'caminhao', tom: 'destaque' },
  ESTORNO: { rotulo: 'Estorno', icone: 'desfazer', tom: 'neutro' },
  APLICACAO: { rotulo: 'Aplicação em campo', icone: 'servicos', tom: 'neutro' },
  RETIRADA: { rotulo: 'Retirada em campo', icone: 'servicos', tom: 'atencao' },
  DEVOLUCAO: { rotulo: 'Devolução', icone: 'desfazer', tom: 'neutro' },
};

export function defTipoMov(tipo?: string | null): DefTipoMov {
  return TIPOS_MOVIMENTACAO[tipo as TipoMovimentacao] ?? { rotulo: tipo || 'Movimentação', icone: 'transferencia', tom: 'neutro' };
}
