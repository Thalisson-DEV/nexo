import { CesTone } from '@ces/ui';
import { StatusExecucao } from '../../../core/models/execucao.models';

/** Rótulo, tom e ícone de cada status de execução (cor nunca sozinha). */
export const STATUS_EXECUCAO: Record<StatusExecucao, { rotulo: string; tom: CesTone; icone: string }> = {
  INICIADO: { rotulo: 'Em andamento', tom: 'warning', icone: 'pi pi-clock' },
  CONCLUIDO: { rotulo: 'Concluído', tom: 'success', icone: 'pi pi-check-circle' },
  EDITADO: { rotulo: 'Editado', tom: 'info', icone: 'pi pi-pencil' }
};
