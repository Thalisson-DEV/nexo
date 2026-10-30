import { ClasseTensao } from './obras-kit.models';
import { PagedResponse } from './shared.models';

/**
 * Tipos de vínculo aceitos para subprojetos (obras filhas).
 */
export enum TipoVinculoSubprojeto {
  MEDIDOR = 'MEDIDOR',
  KIT = 'KIT'
}

/**
 * Status do fluxo de importação em lote.
 */
export enum ImportacaoStatus {
  AGUARDANDO_PROCESSAMENTO = 'AGUARDANDO_PROCESSAMENTO',
  EM_PROCESSAMENTO = 'EM_PROCESSAMENTO',
  CONCLUIDO = 'CONCLUIDO',
  PROCESSADO_SUCESSO = 'CONCLUIDO',
  COM_ERRO = 'COM_ERRO'
}

/**
 * DTO resumido para listagem de obras paginadas.
 */
export interface ObraResumoResponse {
  id: string;
  numero: string;
  titulo: string;
  tipoObra?: 'OBRA' | 'CORRETIVA' | 'PREVENTIVA' | 'LPT';
  notaObra?: string;
  valorProjeto?: number;
  dataInicio: string; // dd/MM/yyyy
  dataPrevisaoFim: string; // dd/MM/yyyy
  dataFimEfetiva?: string | null; // dd/MM/yyyy
  statusId: string;
  statusNome: string;
  statusConcluida: boolean;
  processoId?: string;
  processoNome?: string;
  baseOperacionalId?: string;
  baseOperacionalNome?: string;
  municipioId?: string;
  municipioNome?: string;
  centroCustoId?: string;
  centroCustoNome?: string;
  supervisorId?: string;
  supervisorNome?: string;
  coordenadorId?: string;
  coordenadorNome?: string;
  agenteResponsavelId?: string;
  agenteResponsavelNome?: string;
  obraPaiId?: string | null;
  obraPaiNumero?: string | null;
  tipoVinculoPai?: TipoVinculoSubprojeto | null;
  totalEquipes: number;
  totalSubprojetos: number;
  totalAnexos: number;
  carteira?: CarteiraObraResponse | null;
  ativo: boolean;
}

/**
 * DTO com dados completos de uma obra individual.
 */
export interface ObraResponse {
  id: string;
  numero: string;
  titulo: string;
  tipoObra?: 'OBRA' | 'CORRETIVA' | 'PREVENTIVA' | 'LPT';
  descricao?: string;
  notaObra?: string;
  valorProjeto?: number;
  dataInicio: string; // dd/MM/yyyy
  dataPrevisaoFim: string; // dd/MM/yyyy
  dataFimEfetiva?: string | null; // dd/MM/yyyy
  latitude?: number;
  longitude?: number;
  processoId?: string;
  processoNome?: string;
  baseOperacionalId?: string;
  baseOperacionalNome?: string;
  municipioId?: string;
  municipioNome?: string;
  centroCustoId?: string;
  centroCustoNome?: string;
  statusId: string;
  statusNome: string;
  statusConcluida: boolean;
  supervisorId?: string;
  supervisorNome?: string;
  coordenadorId?: string;
  coordenadorNome?: string;
  agenteResponsavelId?: string;
  agenteResponsavelNome?: string;
  agenteResponsavelEmail?: string;
  obraPaiId?: string | null;
  obraPaiNumero?: string | null;
  obraPaiTitulo?: string | null;
  tipoVinculoPai?: TipoVinculoSubprojeto | null;
  equipes: string[];
  projetosRelacionados?: ProjetoRelacionadoResponse[];
  subprojetos: SubprojetoResponse[];
  anexos: AnexoObraResponse[];
  carteira?: CarteiraObraResponse | null;
  ativo: boolean;
  dataCriacao: string;
  dataUltimaAlteracao?: string;
  usuarioCriacao?: string;
  usuarioUltimaAlteracao?: string;
}

export interface ProjetoRelacionadoResponse {
  idRelacionamento: string;
  idProjeto: string;
  numero: string;
  titulo: string;
  tipoObra: 'OBRA' | 'CORRETIVA' | 'PREVENTIVA' | 'LPT';
  tipoVinculo: 'KIT' | 'MEDIDOR';
  ativo: boolean;
}

/**
 * DTO para subprojetos vinculados a uma obra pai.
 */
export interface SubprojetoResponse {
  id: string;
  numero: string;
  titulo: string;
  notaObra?: string;
  valorProjeto?: number;
  dataInicio: string;
  dataPrevisaoFim: string;
  dataFimEfetiva?: string | null;
  tipoVinculo: TipoVinculoSubprojeto;
  statusId: string;
  statusNome: string;
}

/**
 * DTO para anexos técnicos da obra.
 */
export interface AnexoObraResponse {
  id: string;
  idObra: string;
  idTipoAnexo: string;
  tipoAnexoNome: string;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  observacoes?: string;
  dataCriacao: string;
  usuarioCriacao?: string;
}

/**
 * Status de Obra (Tabela de Apoio).
 */
export interface StatusObraResponse {
  id: string;
  nome: string;
  ordem: number;
  concluida: boolean;
  ativo: boolean;
  dataCriacao?: string;
  dataUltimaAlteracao?: string;
}

/**
 * Agente Responsável / Fiscal da Concessionária (Tabela de Apoio).
 */
export interface AgenteResponsavelResponse {
  id: string;
  nome: string;
  email?: string;
  telefone?: string;
  empresaOrgao?: string;
  ativo: boolean;
  dataCriacao?: string;
  dataUltimaAlteracao?: string;
}

/**
 * Tipo de Anexo de Obra (Tabela de Apoio).
 */
export interface TipoAnexoObraResponse {
  id: string;
  nome: string;
  descricao?: string;
  ativo: boolean;
  dataCriacao?: string;
  dataUltimaAlteracao?: string;
}

// ═══════════════════════════════════════════════════════════════════════
// Requests
// ═══════════════════════════════════════════════════════════════════════

export interface ObraRequest {
  numero: string;
  titulo: string;
  descricao?: string;
  notaObra?: string;
  valorProjeto?: number;
  dataInicio: string; // dd/MM/yyyy
  dataPrevisaoFim: string; // dd/MM/yyyy
  dataFimEfetiva?: string | null; // dd/MM/yyyy
  latitude?: number;
  longitude?: number;
  idProcesso: string;
  idBaseOperacional: string;
  idMunicipio: string;
  idCentroCusto: string;
  idSupervisor: string; // Obrigatório
  idCoordenador: string; // Obrigatório
  idStatusObra: string;
  idAgenteResponsavel?: string;
  idObraPai?: string | null;
  tipoVinculoPai?: TipoVinculoSubprojeto | null;
  equipes?: string[];
}

export interface SubprojetoVinculoRequest {
  idObraFilha: string;
  tipoVinculo: TipoVinculoSubprojeto;
}

export interface StatusObraRequest {
  nome: string;
  ordem?: number;
  concluida?: boolean;
}

export interface AgenteResponsavelRequest {
  nome: string;
  email?: string;
  telefone?: string;
  empresaOrgao?: string;
}

export interface TipoAnexoObraRequest {
  nome: string;
  descricao?: string;
}

// ═══════════════════════════════════════════════════════════════════════
// Filtros
// ═══════════════════════════════════════════════════════════════════════

export interface ObraFilter {
  buscaGlobal?: string;
  numero?: string;
  titulo?: string;
  notaObra?: string;
  statusId?: string;
  statusIds?: string[];
  processoId?: string;
  processoIds?: string[];
  baseOperacionalId?: string;
  baseOperacionalIds?: string[];
  municipioId?: string;
  municipioIds?: string[];
  centroCustoId?: string;
  centroCustoIds?: string[];
  supervisorId?: string;
  supervisorIds?: string[];
  coordenadorId?: string;
  coordenadorIds?: string[];
  agenteResponsavelId?: string;
  equipeId?: string;
  apenasPrincipais?: boolean;
  obraPaiId?: string;
  dataInicioDe?: string; // dd/MM/yyyy
  dataInicioAte?: string;
  dataPrevisaoFimDe?: string;
  dataPrevisaoFimAte?: string;
  valorProjetoMin?: number;
  valorProjetoMax?: number;
  ativo?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export interface StatusObraFilter {
  buscaGlobal?: string;
  nome?: string;
  concluida?: boolean;
  ativo?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export interface AgenteResponsavelFilter {
  buscaGlobal?: string;
  nome?: string;
  empresaOrgao?: string;
  ativo?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export interface TipoAnexoObraFilter {
  buscaGlobal?: string;
  nome?: string;
  ativo?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

// ═══════════════════════════════════════════════════════════════════════
// Importação em Lote CSV
// ═══════════════════════════════════════════════════════════════════════

export interface ObraImportacaoDTO {
  linha?: number;
  numero?: string;
  titulo?: string;
  descricao?: string;
  notaObra?: string;
  valorProjeto?: string;
  dataInicio?: string;
  dataPrevisaoFim?: string;
  latitude?: string;
  longitude?: string;
  nomeProcesso?: string; // Obrigatório
  nomeBaseOperacional?: string; // Obrigatório
  nomeMunicipio?: string; // Obrigatório
  nomeCentroCusto?: string; // Obrigatório
  nomeSupervisor?: string; // Obrigatório
  nomeCoordenador?: string; // Obrigatório
  nomeStatus?: string; // Opcional (Default: PLANEJADA)
  nomeAgenteResponsavel?: string; // Opcional (Apenas Nome)
  codigosEquipes?: string; // Opcional (ex: EQ-01, EQ-02)
  status?: 'AGUARDANDO_PROCESSAMENTO' | 'EM_PROCESSAMENTO' | 'CONCLUIDO' | 'COM_ERRO' | ImportacaoStatus;
  mensagemErro?: string;
}

export interface ImportacaoObraResponse {
  registros: ObraImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoObraRequest {
  registros: ObraImportacaoDTO[];
}

// ═══════════════════════════════════════════════════════════════════════
// Métricas para Dashboard de Obras
// ═══════════════════════════════════════════════════════════════════════

export interface ObraMetricas {
  totalAtivas: number;
  emExecucao: number;
  planejadas: number;
  concluidasMes: number;
  valorTotalExecucao: number;
}

// ═══════════════════════════════════════════════════════════════════════
// Carteira de obras (dados de carteira, status operacional, deslocamento,
// obras próximas, indicadores e orçamento previsto)
// ═══════════════════════════════════════════════════════════════════════

export type PrioridadeObra = 'BAIXA' | 'NORMAL' | 'ALTA' | 'URGENTE';

export const PRIORIDADES_OBRA: { label: string; value: PrioridadeObra }[] = [
  { label: 'Baixa', value: 'BAIXA' },
  { label: 'Normal', value: 'NORMAL' },
  { label: 'Alta', value: 'ALTA' },
  { label: 'Urgente', value: 'URGENTE' }
];

export interface CarteiraObraResponse {
  idContrato?: string | null;
  classeTensao?: ClasseTensao | null;
  classeTensaoRotulo?: string | null;
  classeTensaoInformadaPor?: string | null;
  classeTensaoInformadaEm?: string | null;
  prazoRegulado?: string | null; // dd/MM/yyyy
  prioridade?: PrioridadeObra | null;
  lotePrevisto?: string | null;
  situacaoCarteira?: string | null;
  valorMaterialPrevisto?: number | null;
  valorServicoPrevisto?: number | null;
  observacaoStatus?: string | null;
  distanciaBaseKm?: number | null;
  tempoDeslocamentoMin?: number | null;
  deslocamentoEstimado?: boolean | null;
  deslocamentoCalculadoEm?: string | null;
}

export interface CarteiraObraRequest {
  idContrato?: string | null;
  classeTensao?: ClasseTensao | null;
  prazoRegulado?: string | null; // dd/MM/yyyy
  prioridade?: PrioridadeObra | null;
  lotePrevisto?: string | null;
  situacaoCarteira?: string | null;
  valorProjeto?: number | null;
  valorMaterialPrevisto?: number | null;
  valorServicoPrevisto?: number | null;
}

export interface StatusOperacionalObraRequest {
  idStatusObra: string;
  observacao?: string | null;
  dataFimEfetiva?: string | null; // dd/MM/yyyy
}

export interface HistoricoStatusObraResponse {
  id: string;
  statusAnteriorId?: string | null;
  statusAnteriorNome?: string | null;
  statusNovoId: string;
  statusNovoNome: string;
  observacao?: string | null;
  usuario?: string | null;
  data?: string | null;
}

export interface ObraProximaResponse {
  id: string;
  numero: string;
  titulo: string;
  statusNome?: string | null;
  municipioNome?: string | null;
  distanciaKm: number;
  latitude: number;
  longitude: number;
}

export interface IndicadoresCarteiraResponse {
  quantidade: number;
  valorProjeto: number;
  valorMaterialPrevisto: number;
  valorServicoPrevisto: number;
  municipios: number;
}

export interface ItemOrcamentoObraResponse {
  id: string;
  codigo: string;
  descricao?: string | null;
  unidade?: string | null;
  quantidade: number;
  valorUnitario?: number | null;
  valorTotal: number;
  idAcao?: string | null;
}

export interface OrcamentoObraResponse {
  idObra: string;
  importado: boolean;
  dataImportacao?: string | null;
  valorMaterialPrevisto?: number | null;
  valorServicoPrevisto?: number | null;
  totalServicosImportados: number;
  itens: ItemOrcamentoObraResponse[];
}
