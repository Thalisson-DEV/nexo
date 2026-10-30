/**
 * DTOs do motor de automações (`/v1/automacoes`).
 *
 * A definição da automação segue o formato v3 do motor (o mesmo do TON): chaves em inglês
 * (`trigger`, `steps`, `run_after`…), porque é um documento que o designer, o verificador do
 * servidor e o rascunho do Copiloto compartilham.
 */

export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };
export type Params = Record<string, JsonValue>;

export type TipoAutomacao = 'EMAIL' | 'ALERTA' | 'ROTINA' | 'APROVACAO' | 'DADOS_IA' | 'GERAL';
export type StatusAutomacao = 'RASCUNHO' | 'ATIVA' | 'PAUSADA' | 'ARQUIVADA';
export type OrigemAutomacao = 'USUARIO' | 'COPILOTO' | 'SISTEMA' | 'REGRA_CONVERTIDA';
export type StatusExecucao = 'NA_FILA' | 'EXECUTANDO' | 'AGUARDANDO' | 'SUCESSO' | 'FALHA' | 'CANCELADA' | 'TEMPO_ESGOTADO';
export type ModoExecucao = 'AUTOMATICA' | 'MANUAL' | 'TESTE' | 'REENVIO';
export type StatusEtapa = 'EXECUTANDO' | 'AGUARDANDO' | 'SUCESSO' | 'FALHA' | 'IGNORADA' | 'TEMPO_ESGOTADO' | 'CANCELADA';
export type RunAfter = 'succeeded' | 'failed' | 'skipped' | 'timed_out';

export interface RetryPolicy {
  policy: 'none' | 'fixed' | 'exponential';
  count: number;
  interval_seconds: number;
}

export interface Caso {
  id: string;
  value: string;
  steps: FlowNode[];
}

export interface Ramo {
  id: string;
  label: string;
  steps: FlowNode[];
}

export interface FlowNode {
  id: string;
  type: string;
  label?: string;
  description?: string;
  params: Params;
  run_after?: RunAfter[];
  retry?: RetryPolicy | null;
  timeout_seconds?: number | null;
  then?: FlowNode[];
  else?: FlowNode[];
  cases?: Caso[];
  default?: FlowNode[];
  steps?: FlowNode[];
  branches?: Ramo[];
}

export interface Variavel {
  name: string;
  type: 'string' | 'number' | 'boolean' | 'array' | 'object';
  value: JsonValue;
  description?: string;
}

export interface Definicao {
  schema: 3;
  trigger: { type: string; params: Params };
  variables: Variavel[];
  steps: FlowNode[];
  settings: { notify_on_failure: string[]; timeout_hours: number };
  layout?: Record<string, { x: number; y: number }>;
}

// -------------------------------------------------------------------- catálogo

export interface CampoEspec {
  chave: string;
  rotulo: string;
  tipo: 'text' | 'select' | 'boolean' | 'expression';
  opcoes: [string, string][];
  placeholder: string | null;
}

export interface ParamEspec {
  chave: string;
  rotulo: string;
  tipo: string;
  obrigatorio: boolean;
  padrao: JsonValue;
  ajuda: string | null;
  placeholder: string | null;
  opcoes: [string, string][];
  dinamico: boolean;
  minimo: number | null;
  maximo: number | null;
  campos: CampoEspec[];
  mostrarSe: string[] | null;
  avancado: boolean;
}

export interface SaidaEspec {
  chave: string;
  rotulo: string;
  tipo: string;
  descricao: string;
  campos: SaidaEspec[];
}

export interface NoEspec {
  tipo: string;
  grupo: string;
  subgrupo: string | null;
  rotulo: string;
  descricao: string;
  icone: string;
  params: ParamEspec[];
  saidas: SaidaEspec[];
  conteiner: 'condition' | 'switch' | 'loop' | 'until' | 'scope' | 'parallel' | null;
  gatilho: boolean;
  efeitoExterno: boolean;
  repeticaoPadrao: RetryPolicy;
  ia: boolean;
  palavras: string[];
  saidasDinamicas: string | null;
  chaveEvento: string | null;
  satisfaz: TipoAutomacao[];
}

export interface Catalogo {
  nos: NoEspec[];
  grupos: Record<string, string>;
  tipos: { valor: TipoAutomacao; rotulo: string; descricao: string }[];
  operadores: Record<string, string>;
  funcoes: Record<string, string>;
  blocos: Record<string, string>;
  emailPronto: boolean;
  iaPronta: boolean;
  categorias: Record<string, string>;
  prioridades: Record<string, string>;
  automacoes: { id: string; nome: string }[];
}

// -------------------------------------------------------------------- automações

export interface UltimaExecucao {
  id: string;
  status: StatusExecucao;
  criadaEm: string;
  erro: string | null;
}

export interface AutomacaoResumo {
  id: string;
  nome: string;
  descricao: string | null;
  tipo: TipoAutomacao;
  status: StatusAutomacao;
  origem: OrigemAutomacao;
  gatilhoTipo: string;
  gatilhoRotulo: string;
  gatilhoResumo: string;
  proximaExecucao: string | null;
  ultimaExecucao: UltimaExecucao | null;
  execucoes30Dias: number;
  falhas30Dias: number;
  donoId: string;
  donoNome: string | null;
  numeroVersao: number;
  dataUltimaAlteracao: string | null;
}

export interface VersaoAutomacao {
  numero: number;
  data: string;
  autor: string | null;
  nota: string | null;
}

export interface AutomacaoDetalhe {
  resumo: AutomacaoResumo;
  definicao: Definicao;
  versoes: VersaoAutomacao[];
  problemasAtivacao: string[];
}

export interface AutomacaoRequest {
  nome: string;
  descricao?: string | null;
  tipo?: TipoAutomacao | null;
  definicao?: Definicao | null;
  nota?: string | null;
}

export interface Problema {
  severidade: 'error' | 'warning';
  mensagem: string;
  noId: string | null;
  param: string | null;
}

export interface Validacao {
  problemas: Problema[];
  ativacao: string[];
  erroEstrutura: string | null;
  tipoSugerido: TipoAutomacao | null;
}

export interface ExecucaoResumo {
  id: string;
  automacaoId: string;
  status: StatusExecucao;
  modo: ModoExecucao;
  numeroVersao: number;
  criadaEm: string;
  iniciadaEm: string | null;
  finalizadaEm: string | null;
  duracaoMs: number | null;
  erro: string | null;
  mensagem: string | null;
  aguardando: string | null;
  retomarEm: string | null;
  iniciadaPor: string | null;
  passos: number;
}

export interface Etapa {
  noId: string;
  iteracao: string;
  tipo: string;
  status: StatusEtapa;
  tentativa: number;
  entradas: Record<string, JsonValue> | null;
  saidas: Record<string, JsonValue> | null;
  erro: string | null;
  inicio: string | null;
  fim: string | null;
  proximaEm: string | null;
}

export interface Aprovacao {
  id: string;
  automacaoId: string;
  automacaoNome: string;
  execucaoId: string;
  titulo: string;
  detalhes: string | null;
  opcoes: string[];
  status: string;
  resposta: string | null;
  comentario: string | null;
  respondidaPor: string | null;
  respondidaEm: string | null;
  expiraEm: string | null;
  criadaEm: string;
  podeResponder: boolean;
}

export interface ArquivoExecucao {
  id: string;
  nome: string;
  tipoConteudo: string;
  tamanhoBytes: number;
}

export interface ExecucaoDetalhe {
  resumo: ExecucaoResumo;
  automacaoNome: string;
  definicao: Definicao;
  saidaGatilho: Record<string, JsonValue> | null;
  etapas: Etapa[];
  aprovacoes: Aprovacao[];
  arquivos: ArquivoExecucao[];
}

export interface Rascunho {
  automacaoId: string;
  nome: string;
  tipo: TipoAutomacao;
  resumo: string;
  faltando: string[];
  definicao: Definicao;
  problemas: string[];
}

export interface Opcao {
  valor: string;
  rotulo: string;
  detalhe: string | null;
}

export interface Modelo {
  id: string;
  nome: string;
  descricao: string;
  tipo: TipoAutomacao;
  icone: string;
  definicao: Definicao;
}

// -------------------------------------------------------------------- rótulos

export const TIPO_ROTULO: Record<TipoAutomacao, string> = {
  EMAIL: 'E-mail',
  ALERTA: 'Alerta',
  ROTINA: 'Rotina',
  APROVACAO: 'Aprovação',
  DADOS_IA: 'Dados e IA',
  GERAL: 'Geral'
};

export const TIPO_ICONE: Record<TipoAutomacao, string> = {
  EMAIL: 'pi pi-envelope',
  ALERTA: 'pi pi-bell',
  ROTINA: 'pi pi-calendar-clock',
  APROVACAO: 'pi pi-check-square',
  DADOS_IA: 'pi pi-sparkles',
  GERAL: 'pi pi-bolt'
};

export const STATUS_ROTULO: Record<StatusAutomacao, string> = {
  RASCUNHO: 'Rascunho',
  ATIVA: 'Ativa',
  PAUSADA: 'Pausada',
  ARQUIVADA: 'Arquivada'
};

export const STATUS_EXECUCAO_ROTULO: Record<StatusExecucao, string> = {
  NA_FILA: 'Na fila',
  EXECUTANDO: 'Executando',
  AGUARDANDO: 'Aguardando',
  SUCESSO: 'Sucesso',
  FALHA: 'Falhou',
  CANCELADA: 'Cancelada',
  TEMPO_ESGOTADO: 'Tempo esgotado'
};

export const STATUS_ETAPA_ROTULO: Record<StatusEtapa, string> = {
  EXECUTANDO: 'Executando',
  AGUARDANDO: 'Aguardando',
  SUCESSO: 'Sucesso',
  FALHA: 'Falhou',
  IGNORADA: 'Ignorado',
  TEMPO_ESGOTADO: 'Tempo esgotado',
  CANCELADA: 'Cancelado'
};

export const MODO_ROTULO: Record<ModoExecucao, string> = {
  AUTOMATICA: 'Automática',
  MANUAL: 'Manual',
  TESTE: 'Teste',
  REENVIO: 'Reenvio'
};

export const RUN_AFTER_ROTULO: Record<RunAfter, string> = {
  succeeded: 'Deu certo',
  failed: 'Falhou',
  skipped: 'Foi ignorado',
  timed_out: 'Passou do tempo'
};
