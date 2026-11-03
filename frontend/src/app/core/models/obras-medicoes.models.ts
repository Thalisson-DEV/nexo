/** Medições das obras, pré-fechamento do levantamento e fechamento de obra. */

export type TipoMedicao = 'PARCIAL' | 'CICLO';
export type StatusMedicao = 'GERADA' | 'ESTORNADA';
export type OperacaoItem = 'INSTALAR' | 'RETIRAR';
export type SituacaoExecucaoObra = 'NAO_INICIADA' | 'EM_ANDAMENTO' | 'CONCLUIDA';

/** Onde a obra está na carteira, com os nomes resolvidos. */
export interface ContextoObra {
  idBaseOperacional: string | null;
  baseOperacional: string | null;
  idMunicipio: string | null;
  municipio: string | null;
  idSupervisor: string | null;
  supervisor: string | null;
  statusObra: string | null;
  prioridade: string | null;
}

export interface OpcaoFiltro {
  id: string;
  nome: string | null;
}

export interface OpcoesMedicoes {
  municipios: OpcaoFiltro[];
  supervisores: OpcaoFiltro[];
}

export interface ObraAMedir {
  idObra: string;
  numero: string;
  titulo: string;
  contexto: ContextoObra;
  servicos: number;
  valor: number;
  /** dd/MM/yyyy */
  ultimaExecucao: string | null;
}

/** Serviço concluído pendente de medição. Sem valorTotal: o serviço não tinha valor ao ser programado. */
export interface ItemPendenteMedicao {
  idItem: string;
  codigo: string | null;
  descricao: string | null;
  unidade?: string | null;
  operacao: OperacaoItem;
  codigoKit: string | null;
  local: string | null;
  quantidade: number;
  valorTotal?: number | null;
  /** dd/MM/yyyy */
  concluidoEm: string | null;
}

export interface MedicaoResumo {
  id: string;
  numero: number;
  idObra: string;
  numeroObra: string | null;
  tituloObra: string | null;
  tipo: TipoMedicao;
  /** yyyy-MM */
  competencia: string;
  status: StatusMedicao;
  valorTotal: number;
  servicos: number;
  observacao?: string | null;
  dataCriacao: string;
  usuarioCriacao: string | null;
  justificativaEstorno?: string | null;
  dataEstorno?: string | null;
  usuarioEstorno?: string | null;
}

export interface ItemMedido {
  id: string;
  idItem: string;
  codigo: string | null;
  descricao: string | null;
  unidade?: string | null;
  operacao: OperacaoItem;
  codigoKit: string | null;
  local: string | null;
  quantidade: number;
  valorUnitario?: number | null;
  valorTotal?: number | null;
  concluidoEm: string | null;
}

export interface MedicaoDetalhe {
  medicao: MedicaoResumo;
  itens: ItemMedido[];
}

export interface MedicaoDaObra {
  idObra: string;
  numero: string;
  titulo: string;
  municipio: string | null;
  valorProjeto: number | null;
  pendentes: ItemPendenteMedicao[];
  valorPendente: number;
  servicosSemValor: number;
  valorMedido: number;
  saldo: number | null;
  medicoes: MedicaoResumo[];
}

export interface CicloMedicao {
  idObra: string;
  numero: string;
  titulo: string;
  competencia: string;
  inicio: string;
  fim: string;
  valorProjeto: number | null;
  medidoAnterior: number;
  saldoAnterior: number | null;
  medidoNoCiclo: number;
  saldoAposCiclo: number | null;
  aMedirNoCiclo: ItemPendenteMedicao[];
  valorAMedir: number;
  medicoesDoCiclo: MedicaoResumo[];
}

export interface GerarMedicaoRequest {
  tipo: TipoMedicao;
  competencia?: string;
  idsItens?: string[];
  observacao?: string;
}

export interface FiltroMedicoes {
  idObra?: string;
  tipos?: TipoMedicao[];
  status?: StatusMedicao[];
  competenciaDe?: string;
  competenciaAte?: string;
  idsBases?: string[];
  idsMunicipios?: string[];
  idsSupervisores?: string[];
  usuario?: string;
  geradaDe?: string;
  geradaAte?: string;
  q?: string;
  page: number;
  size: number;
  sort?: string[];
}

export interface LinhaPreFechamento {
  idObra: string;
  numero: string;
  titulo: string;
  contexto: ContextoObra;
  tipoObra: string | null;
  pontos: number;
  vaos: number;
  pontosSemVao: number;
  pontosSemKit: number;
  vaosSemKit: number;
  semDesligamento: number;
  comDesligamento: number;
  naoDefinido: number;
  levantadores: string[];
  concluido: boolean;
  concluidoEm: string | null;
  concluidoPor: string | null;
  prontoParaConcluir: boolean;
}

export interface PreFechamento {
  linhas: LinhaPreFechamento[];
  obras: number;
  pendentes: number;
  prontas: number;
  concluidas: number;
  pontosSemKit: number;
  vaosSemKit: number;
}

export interface LinhaServicoFechamento {
  codigo: string | null;
  descricao: string | null;
  operacao: OperacaoItem;
  unidade: string | null;
  lancado: number;
  executado: number;
  valorExecutado: number;
}

export interface LinhaMaterialFechamento {
  codigo: string | null;
  descricao: string | null;
  operacao: OperacaoItem;
  unidade: string | null;
  lancado: number;
  aplicado: number;
}

export interface LinhaKitFechamento {
  codigoKit: string | null;
  descricao: string | null;
  lancados: number;
  concluidos: number;
  locais: string[];
}

export interface FechamentoObra {
  idObra: string;
  numero: string;
  titulo: string;
  municipio: string | null;
  situacao: SituacaoExecucaoObra;
  kitsLancados: number;
  kitsConcluidos: number;
  valorPlanejado: number;
  valorExecutado: number;
  valorMedido: number;
  medicoesAtivas: number;
  servicos: LinhaServicoFechamento[];
  materiais: LinhaMaterialFechamento[];
  kits: LinhaKitFechamento[];
  medicoes: MedicaoResumo[];
}

export interface Fechamento {
  obras: FechamentoObra[];
  naoEncontrados: string[];
}

/** Obra reconhecida na planilha orçado × faturado. Com erros, o relatório dela não é gerado. */
export interface ObraNaPlanilha {
  idObra: string;
  numero: string;
  titulo: string;
  municipio: string | null;
  itensMaoDeObra: number;
  itensMateriais: number;
  lancamentosFaturados: number;
  orcadoMaoDeObra: number;
  orcadoMateriais: number;
  faturado: number;
  ultimoFaturamento: string | null;
  paraConferir: number;
  erros: string[];
}

export interface AnaliseOrcadoFaturado {
  obras: ObraNaPlanilha[];
  naoIdentificadas: string[];
  errosSemObra: string[];
  linhasMaoDeObra: number;
  linhasMateriais: number;
  linhasFaturado: number;
}

export interface PedidoRelatorioDetalhado {
  idsObras: string[];
  dataCorte: string;
  fiscal: string;
  responsavel: string;
  incluirFotos: boolean;
}
