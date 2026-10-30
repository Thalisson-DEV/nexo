import { BaseOperacionalResponse, EquipeResponse, ProcessoNegocioResponse } from './rh.models';
import {
  AnexoDesvioResponse,
  GravidadeDesvio,
  StatusDesvio
} from './sst.models';

export interface MeuDesvioFilter {
  status?: StatusDesvio[] | null;
  /**
   * Atalho para "diferente de RESOLVIDO". Combinar com `status` é permitido e
   * resulta na interseção, mas na tela use um dos dois controles, nunca ambos.
   */
  apenasPendentes?: boolean | null;
  gravidade?: GravidadeDesvio[] | null;
  apenasVencidos?: boolean | null;
  idProcesso?: string[] | null;
  idProcessoInspetor?: string[] | null;
  idBaseOperacional?: string[] | null;
  /** Regional comercial da base operacional (entidade Centro de Faturamento). */
  idCentroCusto?: string[] | null;
  idEquipe?: string[] | null;
  dataInspecaoDe?: string | null;
  dataInspecaoAte?: string | null;
  buscaGlobal?: string | null;
  page?: number;
  size?: number;
  sort?: string;
}

export interface MeuDesvioResponse {
  id: string;
  idInspecao: string;
  dataInspecao: string;
  processo: ProcessoNegocioResponse | null;
  processoInspetor?: ProcessoNegocioResponse | null;
  baseOperacional: BaseOperacionalResponse | null;
  equipe: EquipeResponse | null;
  idPergunta: string;
  pergunta: string | null;
  gravidade: GravidadeDesvio;
  descricao: string | null;
  acaoCorretiva: string | null;
  prazo: string | null;
  status: StatusDesvio;
  dataResolucao: string | null;
  vencido: boolean;
  anexos: AnexoDesvioResponse[];
}

export interface TratarMeuDesvioRequest {
  status: StatusDesvio;
  acaoCorretiva?: string | null;
  prazo?: string | null;
}

export interface TratarDesviosEmMassaRequest extends TratarMeuDesvioRequest {
  idsDesvios: string[];
}

export interface TratamentoDesviosEmMassaResponse {
  totalAtualizados: number;
  idsAtualizados: string[];
}
