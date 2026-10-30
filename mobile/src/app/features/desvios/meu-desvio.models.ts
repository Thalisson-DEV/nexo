export type StatusDesvio = 'ABERTO' | 'EM_TRATAMENTO' | 'RESOLVIDO';
export type GravidadeDesvio = 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA';

export interface PageResponse<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface ProcessoResumo {
  id: string;
  nome: string;
  ativo: boolean;
}

export interface BaseOperacionalResumo {
  id: string;
  nome: string;
  ativo: boolean;
}

export interface EquipeResumo {
  id: string;
  codigo?: string | null;
  nome?: string | null;
}

export interface AnexoDesvio {
  id: string;
  contexto: 'DESVIO' | 'ACAO_CORRETIVA';
  tipo: 'FOTO' | 'DOCUMENTO';
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  dataCriacao: string;
}

export interface MeuDesvio {
  id: string;
  idInspecao: string;
  dataInspecao: string;
  processo: ProcessoResumo | null;
  baseOperacional: BaseOperacionalResumo | null;
  equipe: EquipeResumo | null;
  idPergunta: string;
  pergunta: string | null;
  gravidade: GravidadeDesvio;
  descricao: string | null;
  acaoCorretiva: string | null;
  prazo: string | null;
  status: StatusDesvio;
  dataResolucao: string | null;
  vencido: boolean;
  anexos: AnexoDesvio[];
}

export interface MeuDesvioFilter {
  status?: StatusDesvio;
  apenasPendentes?: boolean;
  gravidade?: GravidadeDesvio;
  apenasVencidos?: boolean;
  idProcesso?: string;
  idBaseOperacional?: string;
  idEquipe?: string;
  dataInspecaoDe?: string;
  dataInspecaoAte?: string;
  buscaGlobal?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface TratarDesvioRequest {
  status: StatusDesvio;
  acaoCorretiva?: string | null;
  prazo?: string | null;
}

export interface TratarDesviosEmMassaRequest extends TratarDesvioRequest {
  idsDesvios: string[];
}

export interface TratamentoDesviosEmMassaResponse {
  totalAtualizados: number;
  idsAtualizados: string[];
}
