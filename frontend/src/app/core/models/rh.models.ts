export interface BaseOperacionalResponse {
  id: string;
  nome: string;
  centroFaturamento?: CentroFaturamentoResponse;
  latitude?: number;
  longitude?: number;
  raio?: number;
  ativo: boolean;
}

export interface CentroFaturamentoResponse {
  id: string;
  nome: string;
  ativo: boolean;
}

export interface CentroFaturamentoRequest {
  nome: string;
  ativo: boolean;
}

export interface CentroFaturamentoImportacaoDTO {
  linha?: number;
  nome: string;
  status?: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoCentroFaturamentoResponse {
  registros: CentroFaturamentoImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoCentroFaturamentoRequest {
  centros: CentroFaturamentoImportacaoDTO[];
}

export interface BaseOperacionalRequest {
  nome: string;
  centroCustoId: string;
  latitude?: number;
  longitude?: number;
  raio?: number;
}

export interface BaseOperacionalUpdateRequest {
  nome: string;
  centroCustoId: string;
  latitude?: number;
  longitude?: number;
  raio?: number;
}

export interface BaseOperacionalImportacaoDTO {
  linha?: number;
  nome: string;
  centroCustoId?: string;
  nomeCentroFaturamento?: string;
  status?: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoBaseOperacionalResponse {
  registros: BaseOperacionalImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoBaseRequest {
  bases: BaseOperacionalImportacaoDTO[];
}

export interface TipoEquipeResponse {
  id: string;
  nome: string;
  ativo: boolean;
  jornadaMinutos?: number | null;
  plataformaMinutos?: number | null;
}

export interface TipoEquipeRequest {
  nome: string;
  ativo: boolean;
  jornadaMinutos?: number | null;
  plataformaMinutos?: number | null;
}

export interface TipoEquipeImportacaoDTO {
  linha?: number;
  nome: string;
  status?: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoTipoEquipeResponse {
  registros: TipoEquipeImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoTipoEquipeRequest {
  tiposEquipe: TipoEquipeImportacaoDTO[];
}

export type FuncaoMembro = 'RESPONSAVEL' | 'PARCEIRO';

export interface EquipeMembroResponse {
  id: string;
  idColaborador: string;
  nomeColaborador: string;
  funcao?: FuncaoMembro;
  dataEntrada?: string;
  dataSaida?: string | null;
  vinculoAtivo: boolean;
}

export interface EquipeMembroRequest {
  idColaborador: string;
  funcao: FuncaoMembro;
}

export interface EquipeResponse {
  id: string;
  codigo: string;
  processo?: ProcessoNegocioResponse;
  tipoEquipe: TipoEquipeResponse;
  baseOperacional: BaseOperacionalResponse;
  veiculo?: VeiculoResponse;
  coordenador?: CoordenadorResponse;
  supervisor?: SupervisorResponse;
  idContrato?: string | null;
  ativo: boolean;
  membros?: EquipeMembroResponse[];
  /** Meta da janela vigente hoje (ciclo comercial ou mês), vinda de Metas de faturamento. */
  metaFaturamentoMensal?: number | null;
  /** Meta diária da janela vigente: valor dividido pelos dias úteis dela (R$). */
  metaFaturamentoDiaria?: number | null;
  jornadaMinutos?: number | null;
  plataformaMinutos?: number | null;
}

export interface EquipeRequest {
  codigo: string;
  idTipoEquipe: string;
  idBaseOperacional: string;
  idVeiculo: string;
  idCoordenador: string;
  idSupervisor: string;
  idProcesso: string;
  idContrato?: string | null;
  membros?: EquipeMembroRequest[];
  jornadaMinutos?: number | null;
  plataformaMinutos?: number | null;
}

export interface EquipeFilter {
  codEquipe?: string;
  idTipoEquipe?: string;
  idProcesso?: string;
  idBaseOperacional?: string;
  idCoordenador?: string;
  idSupervisor?: string;
  idVeiculo?: string;
  ativo?: boolean | null;
  idMembro?: string;
  idResponsavel?: string;
  idParceiro?: string;
  buscaGlobal?: string;
}

export interface EquipeImportacaoDTO {
  linha?: number;
  codigo: string;
  nomeTipoEquipe: string;
  nomeBaseOperacional?: string;
  nomeBase?: string;
  placaVeiculo?: string;
  nomeCoordenador?: string;
  nomeSupervisor?: string;
  nomeResponsavel?: string;
  nomeProcesso?: string;
  status?: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoEquipeResponse {
  registros: EquipeImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoEquipeRequest {
  equipes: EquipeImportacaoDTO[];
}

export interface FuncaoResponse {
  id: string;
  nome: string;
  descricao?: string;
  ativo: boolean;
}

export interface FuncaoRequest {
  nome: string;
  descricao?: string;
}

export interface FuncaoImportacaoDTO {
  linha?: number;
  nome: string;
  descricao?: string;
  status?: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoFuncaoResponse {
  registros: FuncaoImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoFuncaoRequest {
  funcoes: FuncaoImportacaoDTO[];
}

export interface ProcessoNegocioResponse {
  id: string;
  nome: string;
  ativo: boolean;
}

export interface ProcessoNegocioRequest {
  nome: string;
}

export interface ProcessoNegocioImportacaoDTO {
  linha?: number;
  nome: string;
  status?: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoProcessoNegocioResponse {
  registros: ProcessoNegocioImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoProcessoNegocioRequest {
  processos: ProcessoNegocioImportacaoDTO[];
}

export interface ColaboradorResponse {
  id: string;
  idUsuario?: string;
  primeiroNome: string;
  ultimoNome: string;
  nomeCompleto: string;
  cpf: string;
  processo: ProcessoNegocioResponse;
  baseOperacional: BaseOperacionalResponse;
  funcao: FuncaoResponse;
  dataContratacao: string;
  ativo: boolean;
}

export interface ColaboradorRequest {
  primeiroNome: string;
  ultimoNome: string;
  nomeCompleto: string;
  cpf: string;
  dataContratacao: string;
  idProcesso: string;
  idBaseOperacional: string;
  idFuncao: string;
}

export interface ColaboradorUpdateRequest {
  primeiroNome: string;
  ultimoNome: string;
  nomeCompleto: string;
  idProcesso: string;
  idBaseOperacional: string;
  idFuncao: string;
}

export interface ColaboradorMinResponse {
  id: string;
  nomeCompleto: string;
}

// User related models for the last step of the stepper
export interface PerfilAcessoResponse {
  id: string;
  nome: string;
  ativo: boolean;
}

export interface UsuarioRequest {
  login: string;
  email?: string;
  telefone?: string;
  senha?: string;
  idPerfil: string;
  tipo: 'ADMINISTRATIVO' | 'CAMPO';
  tema?: 'light' | 'dark';
  corPrimaria?: string;
  animacoesAtivadas?: boolean;
}

export interface VincularAcessoRequest {
  colaboradorId: string;
  usuario: UsuarioRequest;
}

export interface ImportacaoErro {
  linha: number;
  mensagem: string;
}

export interface ImportacaoResultadoResponse {
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
  erros: ImportacaoErro[];
}

export type ImportacaoStatus = 'AGUARDANDO_PROCESSAMENTO' | 'COM_ERRO' | 'EM_PROCESSAMENTO';

export interface ColaboradorImportacaoDTO {
  linha?: number;
  primeiroNome: string;
  ultimoNome: string;
  nomeCompleto: string;
  cpf: string;
  dataContratacao: string;
  nomeProcesso: string;
  nomeBase: string;
  nomeFuncao: string;
  status?: ImportacaoStatus;
  mensagemErro?: string;
  // Local fields for UI
  editing?: boolean;
}

export interface ImportacaoColaboradorResponse {
  registros: ColaboradorImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoRequest {
  colaboradores: ColaboradorImportacaoDTO[];
}

// Veículos
export interface VeiculoRequest {
  placa: string;
  modelo: string;
  fabricante?: string;
  baseOperacionalId: string;
  ativo?: boolean;
}

export interface VeiculoResponse {
  id: string;
  placa: string;
  modelo: string;
  fabricante?: string;
  baseOperacional: BaseOperacionalResponse;
  ativo: boolean;
}

export interface VeiculoImportacaoDTO {
  linha?: number;
  placa: string;
  modelo: string;
  fabricante: string;
  nomeBaseOperacional: string;
  status?: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean; // UI only
}

export interface ImportacaoVeiculoResponse {
  registros: VeiculoImportacaoDTO[];
  totalProcessados: number;
  totalSucessos: number;
  totalFalhas: number;
}

export interface ConfirmarImportacaoVeiculoRequest {
  veiculos: VeiculoImportacaoDTO[];
}

// Municípios
export interface MunicipioResponse {
  id: string;
  nome: string;
  baseOperacional: BaseOperacionalResponse;
  ativo: boolean;
  dataCriacao?: string;
  dataDesativacao?: string;
}

export interface MunicipioRequest {
  nome: string;
  idBaseOperacional: string;
}

export interface MunicipioImportacaoDTO {
  linha?: number;
  nome: string;
  nomeBaseOperacional: string;
  idBaseOperacional?: string; // Local UI state
  status?: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoMunicipioResponse {
  registros: MunicipioImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoMunicipioRequest {
  municipios: MunicipioImportacaoDTO[];
}

// Coordenadores
export interface CoordenadorResponse {
  id: string;
  colaborador: ColaboradorResponse;
  dataInicio: string; // YYYY-MM-DD
  dataFim: string | null; // YYYY-MM-DD
  ativo: boolean;
}

export interface CoordenadorRequest {
  idColaborador: string;
  dataInicio: string; // YYYY-MM-DD
  dataFim?: string | null; // YYYY-MM-DD
}

export interface CoordenadorFilter {
  idColaborador?: string;
  nomeColaborador?: string;
  dataInicio?: string;
  dataFim?: string;
  ativo?: boolean | null;
  buscaGlobal?: string;
}

export interface CoordenadorMinResponse {
  id: string;
  idColaborador: string;
  nomeColaborador: string;
}

export interface CoordenadorImportacaoDTO {
  linha: number;
  cpfColaborador?: string;
  nomeColaborador?: string;
  dataInicio: string;
  dataFim?: string;
  status: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoCoordenadorResponse {
  registros: CoordenadorImportacaoDTO[];
  totalRegistros: number;
  totalSucessos: number;
  totalFalhas: number;
}

export interface ConfirmarImportacaoCoordenadorRequest {
  coordenadores: CoordenadorImportacaoDTO[];
}

// Supervisores
export interface SupervisorResponse {
  id: string;
  colaborador: ColaboradorResponse;
  coordenador: CoordenadorResponse;
  dataInicio: string; // YYYY-MM-DD
  dataFim: string | null; // YYYY-MM-DD
  ativo: boolean;
}

export interface SupervisorRequest {
  idColaborador: string;
  idCoordenador: string;
  dataInicio: string; // YYYY-MM-DD
  dataFim?: string | null; // YYYY-MM-DD
}

export interface SupervisorFilter {
  idColaborador?: string;
  nomeColaborador?: string;
  idCoordenador?: string;
  nomeCoordenador?: string;
  dataInicio?: string;
  dataFim?: string;
  ativo?: boolean | null;
  buscaGlobal?: string;
}

export interface SupervisorMinResponse {
  id: string;
  idColaborador: string;
  nomeColaborador: string;
  idCoordenador: string;
  nomeCoordenador: string;
}

export interface SupervisorImportacaoDTO {
  linha: number;
  cpfColaborador?: string;
  nomeColaborador?: string;
  cpfCoordenador?: string;
  nomeCoordenador?: string;
  dataInicio: string;
  dataFim?: string;
  status: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoSupervisorResponse {
  registros: SupervisorImportacaoDTO[];
  totalRegistros: number;
  totalSucessos: number;
  totalFalhas: number;
}

export interface ConfirmarImportacaoSupervisorRequest {
  supervisores: SupervisorImportacaoDTO[];
}
