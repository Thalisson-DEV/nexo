import { SaldoSerieResponse } from '../../../../core/models/almoxarifado.models';

/** Onde a série digitada está, do ponto de vista da entrega desta solicitação. */
export type SituacaoSerie = 'disponivel' | 'reservada' | 'outro-estoque' | 'outro-material' | 'sucata' | 'fora-do-sistema';

export interface ContextoSerie {
  readonly idOrigem: string;
  readonly nomeOrigem: string;
  readonly idMaterial: string;
  /** A solicitação que está entregando: a reserva dela não impede. */
  readonly idSolicitacao: string | null;
}

export interface LeituraSerie {
  readonly situacao: SituacaoSerie;
  readonly mensagem: string;
}

/** Separa o que foi digitado ou bipado em séries, sem repetir e sem espaços. */
export function separarSeries(texto: string | null | undefined): string[] {
  return [...new Set((texto ?? '').split(/[\s,;]+/).map((s) => s.trim()).filter(Boolean))];
}

/**
 * Lê uma série a partir do que o sistema sabe dela (a verificação devolve onde está ativa).
 * Só a série nova do material, na origem e sem reserva de outra solicitação pode sair.
 */
export function lerSerie(numSerie: string, achadas: readonly SaldoSerieResponse[], ctx: ContextoSerie): LeituraSerie {
  const ativa = achadas.find((s) => s.numSerie === numSerie);
  if (!ativa) {
    return { situacao: 'fora-do-sistema', mensagem: `Não consta em nenhum estoque. Dá para registrar a entrada dela em ${ctx.nomeOrigem}.` };
  }
  if (ativa.material?.id !== ctx.idMaterial) {
    return { situacao: 'outro-material', mensagem: `É de outro material (${ativa.material?.codigo ?? 'sem código'}).` };
  }
  if (ativa.estoque?.id !== ctx.idOrigem) {
    return { situacao: 'outro-estoque', mensagem: `Está em ${ativa.estoque?.nomeIdentificador ?? 'outro estoque'}, não em ${ctx.nomeOrigem}.` };
  }
  if (ativa.condicao && ativa.condicao !== 'NOVO') {
    return { situacao: 'sucata', mensagem: 'Está como sucata; só sai material novo.' };
  }
  if (ativa.reservadaParaSolicitacao && ativa.reservadaParaSolicitacao !== ctx.idSolicitacao) {
    return { situacao: 'reservada', mensagem: `Reservada para a solicitação #${ativa.reservadaParaSolicitacao.slice(0, 8)}.` };
  }
  return { situacao: 'disponivel', mensagem: `Disponível em ${ctx.nomeOrigem}.` };
}
