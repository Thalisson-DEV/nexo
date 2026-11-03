import { CesTone } from '@ces/ui';
import {
  AprDoTurno,
  AtividadeDoTurno,
  OcorrenciaDoTurno,
  ProgramacaoDoTurno,
  ServicoApontado,
  SituacaoLiberacao,
  StatusAprTurno,
  TipoOcorrenciaTurno,
  TurnoObraDetalhe
} from '../../../core/models/obras-turnos.models';

export const TIPO_OCORRENCIA: Record<TipoOcorrenciaTurno, { rotulo: string; icone: string }> = {
  SEGURANCA: { rotulo: 'Segurança', icone: 'pi pi-shield' },
  CLIMA: { rotulo: 'Clima', icone: 'pi pi-cloud' },
  MATERIAL: { rotulo: 'Falta de material', icone: 'pi pi-box' },
  EQUIPAMENTO: { rotulo: 'Veículo ou equipamento', icone: 'pi pi-wrench' },
  ACESSO: { rotulo: 'Acesso ao local', icone: 'pi pi-map-marker' },
  DESLIGAMENTO: { rotulo: 'Desligamento', icone: 'pi pi-bolt' },
  TERCEIROS: { rotulo: 'Terceiros ou clientes', icone: 'pi pi-users' },
  OUTROS: { rotulo: 'Outros', icone: 'pi pi-info-circle' }
};

export const STATUS_APR: Record<StatusAprTurno, { rotulo: string; tom: CesTone; icone: string }> = {
  RASCUNHO: { rotulo: 'Rascunho', tom: 'warning', icone: 'pi pi-pencil' },
  CONCLUIDA: { rotulo: 'Concluída', tom: 'success', icone: 'pi pi-check-circle' }
};

export const LIBERACAO: Record<SituacaoLiberacao, { rotulo: string; tom: CesTone; icone: string }> = {
  PENDENTE: { rotulo: 'APR pendente', tom: 'warning', icone: 'pi pi-shield' },
  EM_PREENCHIMENTO: { rotulo: 'APR em preenchimento', tom: 'warning', icone: 'pi pi-pencil' },
  LIBERADA: { rotulo: 'APR concluída', tom: 'success', icone: 'pi pi-check-circle' },
  IMPEDIDA: { rotulo: 'Sem condição de executar', tom: 'neutral', icone: 'pi pi-ban' }
};

/** Uma frente de trabalho do turno: a programação (ou obra fora dela), a liberação e o que já foi apontado. */
export interface CartaoServico {
  chave: string;
  idObra: string;
  numeroObra: string;
  tituloObra: string;
  programacao: ProgramacaoDoTurno | null;
  atividade: AtividadeDoTurno | null;
  liberacao: SituacaoLiberacao;
  aprs: AprDoTurno[];
  aprConcluida: AprDoTurno | null;
  impedimento: OcorrenciaDoTurno | null;
  apontamentos: ServicoApontado[];
  executado: number;
}

/**
 * Monta as frentes do turno. A liberação vale por serviço: cada programação tem a sua APR, e a equipe pode lançar
 * várias no mesmo dia, de manhã ou de tarde. Atividade em obra não programada usa as APRs da obra.
 * Apontamento de programação que saiu do dia continua aparecendo, pela atividade que ele gerou.
 */
export function cartoesDoTurno(t: TurnoObraDetalhe): CartaoServico[] {
  const cartao = (chave: string, idObra: string, numeroObra: string, tituloObra: string, programacao: ProgramacaoDoTurno | null, atividade: AtividadeDoTurno | null, liberacao: SituacaoLiberacao, idProgramacao: string | null): CartaoServico => {
    const aprs = t.aprs.filter((a) => a.idObra === idObra && (!idProgramacao || a.idProgramacao === idProgramacao));
    const apontamentos = idProgramacao ? t.apontamentos.filter((a) => a.idProgramacao === idProgramacao) : [];
    return {
      chave,
      idObra,
      numeroObra,
      tituloObra,
      programacao,
      atividade,
      liberacao,
      aprs,
      aprConcluida: aprs.filter((a) => a.status === 'CONCLUIDA').at(-1) ?? null,
      impedimento: liberacao === 'IMPEDIDA' ? t.ocorrencias.filter((o) => o.impedeExecucao && o.idObra === idObra).at(-1) ?? null : null,
      apontamentos,
      executado: apontamentos.reduce((total, a) => total + (a.valor ?? 0), 0)
    };
  };
  const idsDoDia = new Set(t.programacoes.map((p) => p.id));
  return [
    ...t.programacoes.map((p) => cartao(p.id, p.idObra, p.numeroObra, p.tituloObra, p, t.atividades.find((a) => a.idProgramacao === p.id) ?? null, p.liberacao, p.id)),
    ...t.atividades
      .filter((a) => !a.idProgramacao || !idsDoDia.has(a.idProgramacao))
      .map((a) => cartao(a.id, a.idObra, a.numeroObra, a.tituloObra, null, a, a.liberacao, a.idProgramacao))
  ];
}

/** Horas totais com a unidade, como no indicador da listagem: minutos não ajudam a ler um mês inteiro. */
export function horasTotais(minutos: number): string {
  return `${Math.round(minutos / 60).toLocaleString('pt-BR')} h`;
}
