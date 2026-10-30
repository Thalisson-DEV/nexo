export interface TurnoResponse {
  id: string;
  idEquipe: string;
  idUsuarioAbertura: string;
  dataInicio: string;
  dataFim?: string;
  status: 'ABERTO' | 'FECHADO';
  /** A equipe esqueceu o turno aberto e o servidor o encerrou no horário de corte. */
  encerradoAutomaticamente?: boolean;
  motivoEncerramento?: string | null;
}

export interface AbrirTurnoRequest {
  idEquipe: string;
  kmInicial?: number;
  latitudeInicio?: number;
  longitudeInicio?: number;
}

export interface FinalizarTurnoRequest {
  kmFinal?: number;
  latitudeFim?: number;
  longitudeFim?: number;
}

