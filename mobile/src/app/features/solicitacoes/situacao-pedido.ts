import { OutboxItem, SolicitacaoLocal } from '../../core/db/models';

export type TomSituacao = 'neutro' | 'destaque' | 'ok' | 'atencao' | 'erro';
export type FiltroPedido = 'todos' | 'andamento' | 'entregues' | 'recusados' | 'problema';

export interface SituacaoPedido {
  rotulo: string;
  tom: TomSituacao;
  explicacao: string;
  filtro: FiltroPedido;
}

/**
 * Situação de um pedido em linguagem de campo. O envio pelo aparelho vem antes
 * da situação no almoxarifado: enquanto não sai do aparelho, o almoxarifado não sabe dele.
 */
export function situacaoPedido(sol: SolicitacaoLocal, fila: OutboxItem | null | undefined, opts: { backoffice: boolean; incerto: boolean }): SituacaoPedido {
  if (opts.backoffice) {
    return { rotulo: 'Com o escritório', tom: 'atencao', filtro: 'problema',
      explicacao: 'Recebido e guardado para o escritório analisar. Não dá para mudar pelo app.' };
  }
  if (opts.incerto) {
    return { rotulo: 'A confirmar', tom: 'atencao', filtro: 'problema',
      explicacao: 'O sinal caiu durante o envio. Confira no servidor antes de mudar.' };
  }
  if (fila?.status === 'ERRO') {
    return { rotulo: 'Não enviado', tom: 'erro', filtro: 'problema',
      explicacao: fila.erroDetalhes || 'O servidor não aceitou o pedido. Corrija e envie de novo.' };
  }
  if (fila?.status === 'PENDENTE') {
    return { rotulo: 'Na fila', tom: 'destaque', filtro: 'andamento',
      explicacao: 'Guardado no aparelho. Sai sozinho quando houver internet.' };
  }
  switch (sol.status) {
    case 'PENDENTE':
      return { rotulo: 'Esperando aprovação', tom: 'neutro', filtro: 'andamento', explicacao: 'O almoxarifado ainda vai analisar.' };
    case 'APROVADO':
      return { rotulo: 'Aprovado', tom: 'destaque', filtro: 'andamento', explicacao: 'Aprovado. Aguardando a retirada no almoxarifado.' };
    case 'ENTREGUE':
      return { rotulo: 'Entregue', tom: 'ok', filtro: 'entregues', explicacao: 'Material entregue e lançado no estoque da equipe.' };
    case 'ENTREGUE_PARCIAL':
      return { rotulo: 'Entregue em parte', tom: 'ok', filtro: 'entregues', explicacao: 'Parte do material foi entregue.' };
    case 'REJEITADO':
      return { rotulo: 'Recusado', tom: 'erro', filtro: 'recusados', explicacao: 'O almoxarifado recusou este pedido.' };
    case 'ESTORNADO':
      return { rotulo: 'Estornado', tom: 'erro', filtro: 'recusados', explicacao: 'A entrega foi desfeita pelo almoxarifado.' };
    default:
      return { rotulo: sol.status, tom: 'neutro', filtro: 'andamento', explicacao: '' };
  }
}
