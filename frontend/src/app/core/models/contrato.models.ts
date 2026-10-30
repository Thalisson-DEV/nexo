import { ImportacaoStatus } from './rh.models';

export interface ContratoResponse {
  id: string;
  numeroContrato: string;
  cliente: string;
  dataVigencia: string;
  dataEncerramento: string;
  coeficienteFaturamento: number;
  ativo: boolean;
  dataCriacao?: string;
  dataDesativacao?: string | null;
}

export interface ContratoRequest {
  numeroContrato: string;
  cliente: string;
  dataVigencia: string;
  dataEncerramento: string;
  coeficienteFaturamento: number;
}

export interface ContratoImportacaoDTO {
  linha?: number;
  numeroContrato: string;
  cliente: string;
  dataVigencia: string;
  dataEncerramento: string;
  coeficienteFaturamento: number;
  status?: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoContratoResponse {
  registros: ContratoImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoContratoRequest {
  contratos: ContratoImportacaoDTO[];
}

// Ações
export interface AcaoResponse {
  id: string;
  codigo: string;
  nome: string;
  ativo: boolean;
  dataCriacao?: string;
  dataDesativacao?: string;
}

export interface AcaoRequest {
  codigo: string;
  nome: string;
}

export interface AcaoImportacaoDTO {
  linha?: number;
  codigo: string;
  nome: string;
  status?: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoAcaoResponse {
  registros: AcaoImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoAcaoRequest {
  acoes: AcaoImportacaoDTO[];
}

// Ações Valores
export interface AcaoValorResponse {
  id: string;
  acao: AcaoResponse;
  contrato: ContratoResponse;
  valorBase: number;
  dataVigencia: string;
  ativo: boolean;
  dataCriacao?: string;
  dataDesativacao?: string;
}

export interface AcaoValorRequest {
  idAcao: string;
  idContrato: string;
  valorBase: number;
  dataVigencia: string;
}

export interface AcaoValorImportacaoDTO {
  linha?: number;
  codigoAcao: string;
  numeroContrato: string;
  valorBase: number;
  dataVigencia: string;
  status?: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoAcaoValorResponse {
  registros: AcaoValorImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoAcaoValorRequest {
  registros: AcaoValorImportacaoDTO[];
}

