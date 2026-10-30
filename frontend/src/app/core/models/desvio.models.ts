import { BaseOperacionalResponse, EquipeResponse, ProcessoNegocioResponse, VeiculoResponse } from './rh.models';
import { AnexoDesvioResponse, GravidadeDesvio, StatusDesvio, StatusInspecao } from './sst.models';

export interface DesvioFilter {
  status?: StatusDesvio[] | null;
  apenasPendentes?: boolean | null;
  gravidade?: GravidadeDesvio[] | null;
  apenasVencidos?: boolean | null;
  idInspetor?: string[] | null;
  idColaborador?: string[] | null;
  idFormulario?: string[] | null;
  idPergunta?: string[] | null;
  idProcesso?: string[] | null;
  idProcessoInspetor?: string[] | null;
  idBaseOperacional?: string[] | null;
  idCentroCusto?: string[] | null;
  idEquipe?: string[] | null;
  idVeiculo?: string[] | null;
  dataInspecaoDe?: string | null;
  dataInspecaoAte?: string | null;
  prazoDe?: string | null;
  prazoAte?: string | null;
  semPrazo?: boolean | null;
  dataResolucaoDe?: string | null;
  dataResolucaoAte?: string | null;
  buscaGlobal?: string | null;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ResumoColaborador {
  id: string;
  nomeCompleto: string;
  funcao: string | null;
  baseOperacional: string | null;
  ativo: boolean;
}

export interface DesvioDetalhadoResponse {
  id: string;

  idInspecao: string;
  dataInspecao: string;
  statusInspecao: StatusInspecao;

  idFormulario: string;
  formulario: string | null;

  idInspetor: string;
  inspetor: string | null;

  processo: ProcessoNegocioResponse | null;
  processoInspetor?: ProcessoNegocioResponse | null;
  baseOperacional: BaseOperacionalResponse | null;
  equipe: EquipeResponse | null;
  veiculo: VeiculoResponse | null;

  idPergunta: string;
  grupo: string | null;
  pergunta: string | null;
  idOpcaoResposta: string | null;
  opcaoResposta: string | null;

  gravidade: GravidadeDesvio;
  descricao: string | null;
  acaoCorretiva: string | null;
  prazo: string | null;
  status: StatusDesvio;
  dataResolucao: string | null;

  vencido: boolean;
  diasEmAtraso: number | null;

  colaboradores: ResumoColaborador[];
  anexos: AnexoDesvioResponse[];

  dataRegistro: string;
  dataUltimaAlteracao: string | null;
}
