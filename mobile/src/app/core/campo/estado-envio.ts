import { ExecucaoCampoLocal, OutboxItem } from '../db/models';
import type { EstadoEnvio } from '../../ui/status-envio.component';

/**
 * Traduz o estado técnico de um registro (statusSync + item da fila) para o
 * vocabulário que o encarregado vê. Regra de ouro: nunca dizer "Enviado" para
 * algo que só está na fila.
 */
export function estadoEnvioExecucao(
  exec: Pick<ExecucaoCampoLocal, 'statusSync'>,
  fila: Pick<OutboxItem, 'status' | 'receiptStatus'> | undefined,
  enviandoAgora: boolean,
): EstadoEnvio {
  if (exec.statusSync === 'SINCRONIZADO') return 'synced';
  if (exec.statusSync === 'RESULTADO_INCERTO' || fila?.status === 'RESULTADO_INCERTO') return 'conflict';
  if (exec.statusSync === 'ERRO' || exec.statusSync === 'RECEBIDO_BACKOFFICE' || fila?.status === 'ERRO') return 'error';
  if (!fila) return 'committed';
  if (fila.status === 'SINCRONIZADO' || fila.receiptStatus === 'RESOLVED') return 'synced';
  return enviandoAgora ? 'syncing' : 'queued';
}

export type TipoItemFila = 'execucao' | 'pedido' | 'transferencia' | 'inspecao' | 'anexo' | 'turno' | 'outro';

export interface ItemFilaDescrito {
  id: string;
  tipo: TipoItemFila;
  titulo: string;
  detalhe: string;
  estado: EstadoEnvio;
  /** Mensagem de erro/conflito pronta para o usuário (sem jargão técnico). */
  motivo?: string;
  criadoEm: Date;
}

const TITULOS: Record<TipoItemFila, string> = {
  execucao: 'Execução',
  pedido: 'Pedido de material',
  transferencia: 'Transferência',
  inspecao: 'Inspeção',
  anexo: 'Foto da inspeção',
  turno: 'Turno',
  outro: 'Envio',
};

export function tipoDoItem(endpoint: string): TipoItemFila {
  if (endpoint.includes('execucoes-comerciais')) return 'execucao';
  if (endpoint.includes('inspecao/anexo')) return 'anexo';
  if (endpoint.includes('inspec')) return 'inspecao';
  if (endpoint.includes('solicitac')) return 'pedido';
  if (endpoint.includes('transferencia')) return 'transferencia';
  if (endpoint.includes('turno')) return 'turno';
  return 'outro';
}

/** Linha da folha "Envios": o quê, de quando e em que pé está. */
export function descreverItemFila(item: OutboxItem, enviandoAgora: boolean, agora = new Date()): ItemFilaDescrito {
  const tipo = tipoDoItem(item.endpoint ?? '');
  const payload = (item.originalPayload ?? item.payload ?? {}) as { body?: { referenciaNota?: string }; referenciaNota?: string };
  const nota = payload.body?.referenciaNota ?? payload.referenciaNota;
  const titulo = tipo === 'execucao' && nota ? `Nota ${nota}` : TITULOS[tipo];
  const criadoEm = item.criadoEm instanceof Date ? item.criadoEm : new Date(item.criadoEm);
  const hora = criadoEm.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  const mesmoDia = criadoEm.toDateString() === agora.toDateString();
  const quando = mesmoDia ? hora : `${criadoEm.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })} ${hora}`;

  let estado: EstadoEnvio;
  let motivo: string | undefined;
  if (item.status === 'RESULTADO_INCERTO') {
    estado = 'conflict';
    motivo = 'Pode já ter chegado ao servidor. Confira antes de reenviar.';
  } else if (item.status === 'ERRO') {
    estado = 'error';
    motivo = item.receiptStatus === 'RECEIVED' || item.serverOperationId
      ? 'Chegou ao escritório e está em análise.'
      : (item.erroDetalhes || 'Não foi aceito pelo servidor.');
  } else if (item.status === 'SINCRONIZADO') {
    estado = 'synced';
  } else {
    estado = enviandoAgora ? 'syncing' : 'queued';
    if ((item.tentativas ?? 0) > 0 && item.proximaTentativaEm && item.proximaTentativaEm > agora.getTime()) {
      motivo = 'Servidor instável. Tenta de novo sozinho em instantes.';
    }
  }

  return {
    id: item.id,
    tipo,
    titulo,
    detalhe: tipo === 'execucao'
      ? `${item.endpoint === '/_execucoes-comerciais/alterar' ? 'Alteração' : TITULOS.execucao} · ${quando}`
      : quando,
    estado,
    motivo,
    criadoEm,
  };
}
