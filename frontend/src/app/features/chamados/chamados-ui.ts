import { CesTone } from '@ces/ui';
import {
  Chamado,
  ChamadoPrioridade,
  ChamadoStatus,
  InteracaoChamadoTipo
} from '../../core/models/chamado.models';

export const CHAMADO_STATUS_LABELS: Record<ChamadoStatus, string> = {
  ABERTO: 'Aberto',
  EM_ATENDIMENTO: 'Em atendimento',
  AGUARDANDO_SOLICITANTE: 'Aguardando solicitante',
  RESOLVIDO: 'Resolvido',
  FECHADO: 'Fechado',
  CANCELADO: 'Cancelado'
};

export const CHAMADO_PRIORIDADE_LABELS: Record<ChamadoPrioridade, string> = {
  BAIXA: 'Baixa',
  NORMAL: 'Normal',
  ALTA: 'Alta',
  CRITICA: 'Crítica'
};

export const INTERACAO_LABELS: Record<InteracaoChamadoTipo, string> = {
  ABERTURA: 'Abertura',
  MENSAGEM_PUBLICA: 'Mensagem pública',
  COMENTARIO_INTERNO: 'Comentário interno',
  ALTERACAO_STATUS: 'Alteração de status',
  ALTERACAO_PRIORIDADE: 'Alteração de prioridade',
  ATRIBUICAO: 'Atribuição',
  ENCAMINHAMENTO: 'Encaminhamento',
  REABERTURA: 'Reabertura',
  CANCELAMENTO: 'Cancelamento',
  EVIDENCIA_ADICIONADA: 'Evidência adicionada'
};

export const STATUS_OPTIONS = Object.entries(CHAMADO_STATUS_LABELS).map(([value, label]) => ({
  value: value as ChamadoStatus,
  label
}));

export const PRIORIDADE_OPTIONS = Object.entries(CHAMADO_PRIORIDADE_LABELS).map(
  ([value, label]) => ({ value: value as ChamadoPrioridade, label })
);

export function statusSeverity(status: ChamadoStatus): 'info' | 'warn' | 'success' | 'danger' | 'secondary' {
  const severities: Record<ChamadoStatus, 'info' | 'warn' | 'success' | 'danger' | 'secondary'> = {
    ABERTO: 'info',
    EM_ATENDIMENTO: 'warn',
    AGUARDANDO_SOLICITANTE: 'secondary',
    RESOLVIDO: 'success',
    FECHADO: 'secondary',
    CANCELADO: 'danger'
  };
  return severities[status];
}

export function prioridadeClass(prioridade: ChamadoPrioridade): string {
  const classes: Record<ChamadoPrioridade, string> = {
    BAIXA: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300',
    NORMAL: 'bg-blue-50 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300',
    ALTA: 'bg-amber-50 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300',
    CRITICA: 'bg-red-50 text-red-700 dark:bg-red-950/50 dark:text-red-300'
  };
  return classes[prioridade];
}

export function formatarBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function fimDoDiaIso(date: Date, fim: boolean): string {
  const value = new Date(date);
  value.setHours(fim ? 23 : 0, fim ? 59 : 0, fim ? 59 : 0, fim ? 999 : 0);
  return value.toISOString();
}

export function contagemSlaLabel(
  chamado: Pick<Chamado, 'status' | 'slaPausado' | 'slaResolucaoVencido'>
): string {
  if (chamado.status === 'CANCELADO') {
    return 'Cancelada';
  }
  if (chamado.status === 'RESOLVIDO' || chamado.status === 'FECHADO') {
    return chamado.slaResolucaoVencido ? 'Concluída fora do prazo' : 'Concluída no prazo';
  }
  if (chamado.slaPausado) {
    return 'Pausada';
  }
  return chamado.slaResolucaoVencido ? 'Vencida' : 'Em andamento';
}

export function contagemSlaClass(
  chamado: Pick<Chamado, 'status' | 'slaPausado' | 'slaResolucaoVencido'>
): string {
  if (chamado.status === 'CANCELADO') {
    return '!text-slate-500 dark:!text-slate-400';
  }
  if (chamado.status === 'RESOLVIDO' || chamado.status === 'FECHADO') {
    return chamado.slaResolucaoVencido
      ? '!text-red-600 dark:!text-red-400'
      : '!text-emerald-600 dark:!text-emerald-400';
  }
  if (chamado.slaPausado) {
    return '!text-slate-500 dark:!text-slate-400';
  }
  return chamado.slaResolucaoVencido
    ? '!text-red-600 dark:!text-red-400'
    : '!text-blue-600 dark:!text-blue-400';
}

export function prazoClass(
  chamado: Pick<Chamado, 'status' | 'slaResolucaoVencido' | 'slaPausado' | 'dataLimiteResolucao'>
): string {
  if (chamado.status === 'CANCELADO') {
    return 'text-slate-500 bg-slate-100 dark:bg-slate-800 dark:text-slate-400';
  }
  if (chamado.status === 'RESOLVIDO' || chamado.status === 'FECHADO') {
    return chamado.slaResolucaoVencido
      ? 'text-red-700 bg-red-50 dark:bg-red-950/50 dark:text-red-300'
      : 'text-emerald-700 bg-emerald-50 dark:bg-emerald-950/50 dark:text-emerald-300';
  }
  if (chamado.slaResolucaoVencido) {
    return 'text-red-700 bg-red-50 dark:bg-red-950/50 dark:text-red-300';
  }
  if (chamado.slaPausado) {
    return 'text-slate-600 bg-slate-100 dark:bg-slate-800 dark:text-slate-300';
  }
  const horas = (new Date(chamado.dataLimiteResolucao).getTime() - Date.now()) / 3_600_000;
  return horas <= 4
    ? 'text-amber-700 bg-amber-50 dark:bg-amber-950/50 dark:text-amber-300'
    : 'text-emerald-700 bg-emerald-50 dark:bg-emerald-950/50 dark:text-emerald-300';
}

export function prazoIcon(
  chamado: Pick<Chamado, 'status' | 'slaResolucaoVencido' | 'slaPausado'>
): string {
  if (chamado.status === 'CANCELADO') return 'pi pi-times-circle';
  if (chamado.status === 'RESOLVIDO' || chamado.status === 'FECHADO') {
    return chamado.slaResolucaoVencido ? 'pi pi-exclamation-circle' : 'pi pi-check-circle';
  }
  if (chamado.slaResolucaoVencido) return 'pi pi-exclamation-circle';
  if (chamado.slaPausado) return 'pi pi-pause-circle';
  return 'pi pi-clock';
}

/** Tom do DS (`ces-status`) para o status do chamado. */
export function statusTom(status: ChamadoStatus): CesTone {
  const tons: Record<ChamadoStatus, CesTone> = {
    ABERTO: 'info',
    EM_ATENDIMENTO: 'warning',
    AGUARDANDO_SOLICITANTE: 'neutral',
    RESOLVIDO: 'success',
    FECHADO: 'neutral',
    CANCELADO: 'danger'
  };
  return tons[status];
}

/** Tom do DS (`ces-status`) para a prioridade. */
export function prioridadeTom(prioridade: ChamadoPrioridade): CesTone {
  const tons: Record<ChamadoPrioridade, CesTone> = { BAIXA: 'neutral', NORMAL: 'info', ALTA: 'warning', CRITICA: 'danger' };
  return tons[prioridade];
}

/** Tom do DS para o prazo de resolução (mesma regra de `prazoClass`). */
export function prazoTom(
  chamado: Pick<Chamado, 'status' | 'slaResolucaoVencido' | 'slaPausado' | 'dataLimiteResolucao'>
): CesTone {
  if (chamado.status === 'CANCELADO') return 'neutral';
  if (chamado.status === 'RESOLVIDO' || chamado.status === 'FECHADO') return chamado.slaResolucaoVencido ? 'danger' : 'success';
  if (chamado.slaResolucaoVencido) return 'danger';
  if (chamado.slaPausado) return 'neutral';
  const horas = (new Date(chamado.dataLimiteResolucao).getTime() - Date.now()) / 3_600_000;
  return horas <= 4 ? 'warning' : 'success';
}
