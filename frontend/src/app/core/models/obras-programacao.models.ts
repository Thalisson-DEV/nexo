// Programação das obras: itens planejados entregues a uma equipe num período
import { StatusItemPlanejado } from './obras-planejamento.models';
import { TipoItemKit } from './obras-kit.models';

export type TurnoProgramacao = 'DIA_TODO' | 'MANHA' | 'TARDE';
export type SituacaoIntervencao = 'CRIADO' | 'PENDENTE' | 'NAO_SE_APLICA';
export type SituacaoProgramacao = 'PROGRAMADA' | 'ATRASADA' | 'EM_ANDAMENTO' | 'CONCLUIDA' | 'CANCELADA';
export type TipoEventoProgramacao = 'EDITADA' | 'REPROGRAMADA' | 'CANCELADA' | 'EXCLUIDA';

export interface EquipeProjetada {
  id: string;
  codigo: string;
  idTipoEquipe?: string | null;
  tipoEquipe?: string | null;
  idBase?: string | null;
  base?: string | null;
  distanciaKm?: number | null;
  tempoDeslocamentoMin?: number | null;
  deslocamentoEstimado?: boolean | null;
  semDeslocamentoPorque?: string | null;
  jornadaMinutos?: number | null;
  plataformaMinutos?: number | null;
  capacidadeMinutos?: number | null;
  diasUteis: number;
  metaDia?: number | null;
  metaPeriodo?: number | null;
  valorProgramadoPeriodo: number;
  valorSelecao: number;
  percentualOcupacao?: number | null;
  percentualMeta?: number | null;
  conflitos: ConflitoEquipe[];
}

export interface ConflitoEquipe {
  numero: number;
  numeroObra?: string | null;
  inicio: string; // dd/MM/yyyy
  fim: string; // dd/MM/yyyy
  turno: TurnoProgramacao;
}

export interface ResumoPlanejamentoObra {
  idObra: string;
  temKits: boolean;
  valorTotal: number;
  valorProgramado: number;
  valorConcluido: number;
  valorPendente: number;
  percentualProgramado: number;
  percentualConcluido: number;
  servicosPendentes: number;
  pontosSemKit: number;
}

export interface ObraAProgramar {
  idObra: string;
  numero: string;
  titulo: string;
  prioridade?: string | null;
  prazoPrevisto?: string | null;
  prazoRegulado?: string | null;
  valorPendente: number;
  servicosPendentes: number;
  pontosSemKit: number;
  percentualProgramado: number;
}

export interface PreviaProgramacao {
  itens: number;
  servicos: number;
  materiais: number;
  materiaisIncluidos: number;
  ignoradosProgramados: number;
  ignoradosConcluidos: number;
  servicosSemValor: number;
  nomesServicosSemValor: string[];
  valor: number;
  prazoPrevisto?: string | null;
  prazoRegulado?: string | null;
  equipes: EquipeProjetada[];
}

export interface CondicoesProgramacao {
  descricao: string;
  linhaViva: boolean;
  desligamento: boolean;
  finalizaObra: boolean;
  turno: TurnoProgramacao;
  situacaoIntervencao: SituacaoIntervencao;
  observacao?: string | null;
}

export interface ProgramarRequest extends CondicoesProgramacao {
  idsItens: string[];
  idEquipe: string;
  inicio: string; // dd/MM/yyyy
  fim: string; // dd/MM/yyyy
}

export interface ItemProgramado {
  idItem: string;
  local?: string | null;
  tipo: TipoItemKit;
  codigo?: string | null;
  descricao?: string | null;
  unidade?: string | null;
  quantidade: number;
  quantidadeExecutada: number;
  valorUnitario?: number | null;
  valorTotal?: number | null;
  status: StatusItemPlanejado;
}

export interface ProgramacaoDetalhe extends CondicoesProgramacao {
  id: string;
  numero: number;
  idObra: string;
  numeroObra: string;
  tituloObra: string;
  idEquipe: string;
  codigoEquipe?: string | null;
  inicio: string; // dd/MM/yyyy
  fim: string; // dd/MM/yyyy
  situacao: SituacaoProgramacao;
  valor: number;
  criadoPor?: string | null;
  criadoEm?: string | null;
  versao: number;
  itens: ItemProgramado[];
}

export interface ResultadoProgramacao {
  programacao: ProgramacaoDetalhe;
  ignoradosProgramados: number;
  ignoradosConcluidos: number;
  materiaisIncluidos: number;
  servicosSemValor: number;
}

export interface BarraGantt {
  id: string;
  numero: number;
  idObra: string;
  numeroObra?: string | null;
  tituloObra?: string | null;
  inicio: string; // dd/MM/yyyy
  fim: string; // dd/MM/yyyy
  turno: TurnoProgramacao;
  situacao: SituacaoProgramacao;
  valor: number;
  itens: number;
  versao: number;
  idEquipe: string;
  codigoEquipe?: string | null;
  linhaViva: boolean;
  desligamento: boolean;
  finalizaObra: boolean;
  situacaoIntervencao: SituacaoIntervencao;
  municipio?: string | null;
  prioridade?: string | null;
}

export interface LinhaGantt {
  idEquipe: string;
  codigo: string;
  tipoEquipe?: string | null;
  base?: string | null;
  valorProgramado: number;
  percentualMeta?: number | null;
  supervisor?: string | null;
  coordenador?: string | null;
  programacoes: BarraGantt[];
}

export interface GanttProgramacao {
  inicio: string; // dd/MM/yyyy
  fim: string; // dd/MM/yyyy
  equipes: LinhaGantt[];
  /** Abertas que terminaram antes deste período e ainda não foram concluídas. */
  atrasadasAntes: BarraGantt[];
}

export interface EventoProgramacao {
  id: string;
  data: string;
  tipo: TipoEventoProgramacao;
  idProgramacao: string;
  numeroProgramacao: number;
  idObra: string;
  numeroObra?: string | null;
  equipeOrigem?: string | null;
  equipeDestino?: string | null;
  inicioOriginal?: string | null;
  fimOriginal?: string | null;
  inicioNovo?: string | null;
  fimNovo?: string | null;
  usuario?: string | null;
  observacao?: string | null;
}
