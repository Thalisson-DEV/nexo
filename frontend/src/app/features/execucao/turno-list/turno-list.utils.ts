import { CesTone } from '@ces/ui';
import { TurnoConsulta } from '../../../core/models/dashboard.models';

export interface SituacaoTurnoLista {
  rotulo: string;
  tom: CesTone;
  icone: string;
}

const DATA_CURTA = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit' });

/**
 * Situação do turno na consulta. Um turno aberto desde outro dia ganha destaque de aviso:
 * quase sempre é um turno que a equipe esqueceu de fechar. O que o sistema encerrou no
 * horário de corte, por esquecimento da equipe, também fica em destaque.
 */
export function situacaoDoTurno(
  turno: Pick<TurnoConsulta, 'status' | 'abertoDesdeOutroDia' | 'dataInicio' | 'encerradoAutomaticamente'>
): SituacaoTurnoLista {
  if (turno.status === 'FECHADO') {
    return turno.encerradoAutomaticamente
      ? { rotulo: 'Encerrado pelo sistema', tom: 'warning', icone: 'pi pi-clock' }
      : { rotulo: 'Encerrado', tom: 'neutral', icone: 'pi pi-check-circle' };
  }
  if (turno.abertoDesdeOutroDia) {
    return {
      rotulo: `Aberto desde ${DATA_CURTA.format(new Date(turno.dataInicio))}`,
      tom: 'warning',
      icone: 'pi pi-exclamation-triangle'
    };
  }
  return { rotulo: 'Em turno', tom: 'success', icone: 'pi pi-play-circle' };
}
