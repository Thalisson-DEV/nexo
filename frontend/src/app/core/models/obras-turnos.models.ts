import { TipoItemKit } from './obras-kit.models';
import { SituacaoProgramacao } from './obras-programacao.models';
import { PagedResponse } from './shared.models';

export type StatusTurnoObra = 'ABERTO' | 'FECHADO';
export type MetaTurnoFiltro = 'BATEU' | 'NAO_BATEU';
export type TipoOcorrenciaTurno = 'SEGURANCA' | 'CLIMA' | 'MATERIAL' | 'EQUIPAMENTO' | 'ACESSO' | 'DESLIGAMENTO' | 'TERCEIROS' | 'OUTROS';
export type StatusAprTurno = 'RASCUNHO' | 'CONCLUIDA';
export type SituacaoEquipeBoletim = 'EM_TURNO' | 'ENCERRADO' | 'SEM_TURNO';
export type SituacaoLiberacao = 'PENDENTE' | 'EM_PREENCHIMENTO' | 'LIBERADA' | 'IMPEDIDA';
export type RespostaApr = 'SIM' | 'NAO' | 'NAO_SE_APLICA';

export interface IndicadoresTurnosObra {
  turnos: number;
  emAndamento: number;
  minutosTotais: number;
  comMeta: number;
  bateuMeta: number;
  semRelatorio: number;
  comOcorrencias: number;
  servicosExecutados: number;
  valorExecutado: number;
}

export interface TurnoObraLinha {
  id: string;
  idEquipe: string;
  codigoEquipe: string | null;
  tipoEquipe: string | null;
  baseOperacional: string | null;
  placa: string | null;
  status: StatusTurnoObra;
  dataInicio: string;
  dataFim: string | null;
  duracaoMinutos: number;
  abertoDesdeOutroDia: boolean;
  encerradoAutomaticamente: boolean;
  motivoEncerramento: string | null;
  abertoPor: string | null;
  membros: number;
  substitutos: number;
  realizado: number;
  metaDiaria: number | null;
  percentualMeta: number | null;
  bateuMeta: boolean;
  semRelatorio: boolean;
  ocorrencias: number;
  aprs: number;
  aprsEmRascunho: number;
  servicos: number;
  obras: string[];
}

export interface ListaTurnosObra extends PagedResponse<TurnoObraLinha> {
  indicadores: IndicadoresTurnosObra;
}

export interface FiltroTurnosObra {
  idEquipe?: string;
  idBaseOperacional?: string[];
  status?: StatusTurnoObra;
  meta?: MetaTurnoFiltro;
  obra?: string;
  de?: string;
  ate?: string;
  semRelatorio?: boolean;
  comOcorrencia?: boolean;
  q?: string;
  page: number;
  size: number;
  sort?: string[];
}

export interface MembroTurno {
  idColaborador: string;
  nome: string;
  funcao: string | null;
  lider: boolean;
  substituto: boolean;
}

export interface ProgramacaoDoTurno {
  id: string;
  numero: number;
  idObra: string;
  numeroObra: string;
  tituloObra: string;
  descricao: string;
  inicio: string;
  fim: string;
  situacao: SituacaoProgramacao;
  valor: number;
  itens: number;
  itensConcluidos: number;
  liberacao: SituacaoLiberacao;
}

export interface AtividadeDoTurno {
  id: string;
  idObra: string;
  numeroObra: string;
  tituloObra: string;
  idProgramacao: string | null;
  numeroProgramacao: number | null;
  programada: boolean;
  observacao: string | null;
  liberacao: SituacaoLiberacao;
}

export interface ServicoApontado {
  id: string;
  data: string;
  idProgramacao: string;
  idObra: string;
  numeroObra: string;
  local: string | null;
  tipo: TipoItemKit;
  codigo: string | null;
  descricao: string;
  unidade: string | null;
  quantidade: number;
  excedente: number;
  valor: number | null;
  justificativa: string | null;
  usuario: string | null;
  dataRegistro: string;
  estornavel: boolean;
}

export interface FotoTurno {
  id: string;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
}

export interface OcorrenciaDoTurno {
  id: string;
  tipo: TipoOcorrenciaTurno;
  descricao: string;
  idObra: string | null;
  numeroObra: string | null;
  impedeExecucao: boolean;
  usuario: string | null;
  data: string;
}

export interface FormularioApr {
  local: string | null;
  riscos: string[];
  outroRisco: string | null;
  medidas: string[];
  medidasAdicionais: string | null;
  verificacoes: { pergunta: string; resposta: RespostaApr | null }[];
  idsParticipantes: string[];
}

export interface AprDoTurno {
  idProgramacao?: string | null;
  numeroProgramacao?: number | null;
  id: string;
  atividade: string;
  idObra: string | null;
  numeroObra: string | null;
  idResponsavel: string | null;
  responsavel: string | null;
  status: StatusAprTurno;
  dataConclusao: string | null;
  usuario: string | null;
  data: string;
  anexos: FotoTurno[];
  formulario: FormularioApr | null;
}

export interface TurnoObraDetalhe {
  id: string;
  idEquipe: string;
  codigoEquipe: string;
  tipoEquipe: string | null;
  baseOperacional: string | null;
  idVeiculo: string | null;
  placa: string | null;
  status: StatusTurnoObra;
  dataInicio: string;
  dataFim: string | null;
  duracaoMinutos: number;
  abertoDesdeOutroDia: boolean;
  encerradoAutomaticamente: boolean;
  motivoEncerramento: string | null;
  abertoPor: string | null;
  composicao: MembroTurno[];
  realizado: number;
  metaDiaria: number | null;
  percentualMeta: number | null;
  bateuMeta: boolean;
  programacoes: ProgramacaoDoTurno[];
  atividades: AtividadeDoTurno[];
  apontamentos: ServicoApontado[];
  relatorio: { observacao: string; usuario: string | null; data: string } | null;
  fotos: FotoTurno[];
  ocorrencias: OcorrenciaDoTurno[];
  aprs: AprDoTurno[];
}

export interface ValoresBoletim {
  programado: number;
  executado: number;
}

export interface EquipeBoletim {
  idEquipe: string;
  codigo: string;
  tipoEquipe: string | null;
  idBase: string | null;
  baseOperacional: string | null;
  situacao: SituacaoEquipeBoletim;
  idTurno: string | null;
  abertura: string | null;
  fechamento: string | null;
  minutosEmTurno: number;
  abertoDesdeOutroDia: boolean;
  placa: string | null;
  lider: MembroTurno | null;
  componentes: MembroTurno[];
  noCiclo: ValoresBoletim;
  noDia: ValoresBoletim;
  diaAnterior: ValoresBoletim;
  metaDiaria: number | null;
  percentualMeta: number | null;
  agendamentoSemTurno: boolean;
  agenda: AgendaDoBoletim[];
}

export interface AgendaDoBoletim {
  idProgramacao: string;
  numero: number;
  idObra: string;
  numeroObra: string;
  tituloObra: string | null;
  descricao: string;
  valor: number;
  itens: number;
  itensConcluidos: number;
  liberacao: SituacaoLiberacao;
}

export interface BoletimEquipes {
  data: string;
  diaAnterior: string;
  hoje: boolean;
  geradoEm: string;
  resumo: {
    equipes: number;
    emTurno: number;
    encerrados: number;
    semTurno: number;
    agendamentosSemTurno: number;
    programadoDia: number;
    executadoDia: number;
  };
  equipes: EquipeBoletim[];
}
