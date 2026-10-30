/**
 * Contratos da API do Copiloto (`/v1/assistente`). O backend omite campos nulos
 * (`default-property-inclusion: non_null`), por isso quase tudo é opcional.
 */

export type AssistentePapel = 'USUARIO' | 'ASSISTENTE';

export type AssistenteStatus =
  | 'PROCESSANDO'
  | 'CONCLUIDA'
  | 'INDISPONIVEL'
  | 'DADOS_INSUFICIENTES'
  | 'CANCELADA'
  | 'FALHOU';

/** De onde o usuário abriu o Copiloto (pista de contexto, nunca autorização). */
export interface AssistenteContexto {
  pagina?: string;
  rota?: string;
  entidadeTipo?: string;
  entidadeId?: string;
  rotulo?: string;
  filtros?: Record<string, string>;
  /** Contexto estruturado do dashboard em tela (Copiloto contextual). */
  painel?: ContextoPainel;
}

/**
 * Contexto estruturado de um painel, no formato do backend (`ContextoPainel`). Descreve o que o usuário
 * está vendo — identidade, período, filtros, KPIs, agregações visíveis dos gráficos, seleção e datasets.
 * Não carrega a base de dados completa: o agente aprofunda pelas ferramentas.
 */
export interface ContextoPainel {
  id: string;
  nome: string;
  modulo?: string;
  descricao?: string;
  periodo?: PeriodoPainel;
  filtros: FiltroPainel[];
  metricas?: MetricaPainel[];
  graficos?: GraficoPainel[];
  entidades?: EntidadePainel[];
  datasets?: string[];
  foco?: FocoPainel;
  extras?: Record<string, string>;
  /** Tabelas que aceitam filtros sugeridos pelo Copiloto (`APPLY_FILTER`), com os campos permitidos. */
  tabelas?: TabelaPainel[];
}

export type TipoColunaPainel = 'numero' | 'percentual' | 'texto' | 'booleano';
export type OperadorFiltroPainel = 'eq' | 'ne' | 'lt' | 'lte' | 'gt' | 'gte' | 'contains';

export interface ColunaPainel {
  campo: string;
  rotulo?: string;
  tipo: TipoColunaPainel;
  /** Omitido = todos os operadores do tipo. */
  operadores?: OperadorFiltroPainel[];
}

export interface TabelaPainel {
  /** Id estável (`<painel>#<tabela>`), alvo de `APPLY_FILTER`. */
  id: string;
  rotulo?: string;
  colunas: ColunaPainel[];
}

export interface PeriodoPainel {
  /** Data ISO (aaaa-mm-dd). */
  inicio?: string;
  fim?: string;
  rotulo?: string;
}

export interface FiltroPainel {
  chave: string;
  rotulo: string;
  /** Texto exibido na tela; `null` = todos. */
  valor: string | null;
  /** Identificador usado nas consultas (ex.: id da base). */
  valorId?: string | null;
}

export interface MetricaPainel {
  id: string;
  rotulo: string;
  valor: number | null;
  valorFormatado?: string;
  unidade?: string;
  descricao?: string;
  comparacao?: {
    rotulo: string;
    valorAnterior?: number | null;
    variacaoPercentual?: number | null;
    melhorQuando?: 'maior' | 'menor';
  };
}

export interface GraficoPainel {
  id: string;
  tipo: string;
  titulo: string;
  metrica?: string;
  dimensao?: string;
  formato?: string;
  dados: { rotulo: string; valor: number | null; serie?: string }[];
  totalCategorias?: number;
}

export interface EntidadePainel {
  tipo: string;
  id?: string;
  rotulo: string;
}

export interface FocoPainel {
  tipo: 'grafico' | 'metrica' | 'tabela' | 'secao';
  id: string;
  rotulo?: string;
}

export type OrigemConversa = 'WORKSPACE' | 'DASHBOARD';

export interface ConversaResumo {
  id: string;
  titulo: string;
  ultimaAtividadeEm: string;
  dataCriacao: string;
  emAndamento: boolean;
  origem?: OrigemConversa;
  /** Id do painel quando a conversa começou em um dashboard. */
  origemId?: string;
}

export interface ConversaDetalhe {
  id: string;
  titulo: string;
  dataCriacao: string;
  ultimaAtividadeEm: string;
  contexto?: AssistenteContexto;
  mensagens: MensagemAssistente[];
  artefatos: ArtefatoAssistente[];
  arquivos: ArquivoAssistente[];
  /** Id da resposta em geração (retomável em GET /stream). */
  execucaoEmAndamento?: string;
  origem?: OrigemConversa;
  origemId?: string;
}

export interface MetricasMensagem {
  modelo?: string;
  tokensEntrada?: number;
  tokensSaida?: number;
  duracaoMs?: number;
  ciclos?: number;
  chamadasFerramenta?: number;
}

export interface MensagemAssistente {
  id: string;
  clientMessageId: string;
  papel: AssistentePapel;
  status: AssistenteStatus;
  conteudo: string;
  pacotes?: PacoteAssistente[];
  acoes?: AssistenteAcao[];
  arquivos?: ArquivoAssistente[];
  dataCriacao: string;
  metricas?: MetricasMensagem;
  /** Snapshot do contexto de tela usado nesta pergunta (perguntas feitas em dashboards). */
  contexto?: AssistenteContexto;
}

/** Filtro proposto pelo agente para uma tabela do painel (validado no backend e de novo na tela). */
export interface FiltroPropostoAcao {
  campo: string;
  operador: OperadorFiltroPainel | string;
  valor: unknown;
}

/**
 * Ação validada pelo backend — apenas oferece um botão, nunca executa nada.
 * `NAVIGATE` abre uma tela do catálogo; `APPLY_FILTER` filtra uma tabela do painel aberto.
 */
export interface AssistenteAcao {
  tipo: 'NAVIGATE' | 'NAVEGAR' | 'APPLY_FILTER' | string;
  chaveDestino?: string;
  /** @deprecated use chaveDestino */
  destino?: string;
  rotulo: string;
  autoridade: string;
  parametros?: Record<string, unknown> | null;
  /** `APPLY_FILTER`: id da tabela do painel. */
  alvo?: string;
  /** `APPLY_FILTER`: filtros combinados (E). */
  filtros?: FiltroPropostoAcao[];
}

export type CategoriaArquivo =
  | 'PLANILHA'
  | 'CSV'
  | 'PDF'
  | 'TEXTO'
  | 'JSON'
  | 'IMAGEM'
  | 'DOCUMENTO'
  | 'OUTRO';

export interface ArquivoAssistente {
  id: string;
  codigo?: string;
  conversaId?: string;
  nome: string;
  tipoConteudo: string;
  categoria: CategoriaArquivo;
  tamanhoBytes: number;
  origem: 'UPLOAD' | 'GERADO';
  status?: 'PRONTO' | 'FALHOU';
  erro?: string;
  resumo?: Record<string, unknown>;
  criadoEm?: string;
  /** Resultados rN criados a partir de planilhas geradas pelo Python. */
  resultados?: string[];
}

export type TipoArtefato =
  | 'DASHBOARD'
  | 'GRAFICO'
  | 'TABELA'
  | 'RELATORIO'
  | 'DOCUMENTO'
  | 'PLANILHA'
  | 'IMAGEM'
  | 'CODIGO';

export type FormatoValor = 'numero' | 'inteiro' | 'moeda' | 'percentual';

export type TipoGraficoArtefato =
  | 'barra'
  | 'barra_horizontal'
  | 'barra_empilhada'
  | 'linha'
  | 'area'
  | 'pizza'
  | 'rosca'
  | 'dispersao';

interface ComponenteBase {
  titulo?: string;
  descricao?: string;
  resultado?: string;
  procedencia?: string;
  largura?: 'inteira' | 'metade';
}

export interface KpiArtefato extends ComponenteBase {
  tipo: 'kpi';
  valor: number | null;
  formato: FormatoValor;
  agregacao?: string;
  melhorQuando?: 'maior' | 'menor';
  comparacao?: { valor: number | null; variacao?: number | null; diferenca?: number | null; rotulo: string };
}

export interface GraficoArtefato extends ComponenteBase {
  tipo: 'grafico';
  grafico: TipoGraficoArtefato;
  rotulos?: string[];
  series?: { nome: string; valores: (number | null)[] }[];
  pontos?: { x: number; y: number; rotulo?: string }[];
  eixoX?: string;
  eixoY?: string;
  formato: FormatoValor;
  empilhado?: boolean;
  agregacao?: string;
  observacao?: string;
}

export interface TabelaArtefato extends ComponenteBase {
  tipo: 'tabela';
  colunas: { nome: string; rotulo: string; tipo: string }[];
  linhas: unknown[][];
  total: number;
  exibidas: number;
  observacao?: string;
}

export interface TextoArtefato extends ComponenteBase {
  tipo: 'texto';
  conteudo: string;
}

export type ComponenteArtefato = KpiArtefato | GraficoArtefato | TabelaArtefato | TextoArtefato;

export interface ProcedenciaArtefato {
  codigo: string;
  titulo: string;
  natureza: 'INTERNO' | 'EXTERNO' | 'ARQUIVO' | 'CALCULO';
  origem?: string;
  linhas?: number;
  consulta?: Record<string, unknown>;
}

export interface SecaoRelatorio {
  titulo: string;
  texto?: string;
  componentes: ComponenteArtefato[];
}

export interface EspecificacaoArtefato {
  tipo: string;
  componentes?: ComponenteArtefato[];
  procedencia?: ProcedenciaArtefato[];
  subtitulo?: string;
  periodo?: string;
  resumo?: string;
  secoes?: SecaoRelatorio[];
  conclusoes?: string;
  arquivo?: ArquivoAssistente;
  formato?: string;
  abas?: { nome: string; resultado: string; linhas: number; colunas: number }[];
  avisos?: string[];
}

export interface ArtefatoAssistente {
  id: string;
  codigo: string;
  tipo: TipoArtefato;
  titulo: string;
  descricao?: string;
  especificacao: EspecificacaoArtefato;
  arquivoId?: string;
  mensagemId?: string;
  dataCriacao?: string;
}

export interface ResultadoTabular {
  codigo: string;
  titulo: string;
  natureza: string;
  origem?: string;
  consulta?: Record<string, unknown>;
  colunas: { nome: string; tipo: string }[];
  linhas: unknown[][];
  total: number;
  truncado: boolean;
}

/** Uma linha do stream NDJSON: `{placement:{turnIndex,tabIndex}, obj:{type,...}}`. */
export interface PacoteAssistente {
  placement: { turnIndex: number; tabIndex: number };
  obj: { type: string; [campo: string]: unknown };
}

export interface CapacidadesAssistente {
  disponivel: boolean;
  motivoIndisponibilidade?: string;
  modelo?: string;
  python: boolean;
  pesquisaWeb: boolean;
  leituraWeb: boolean;
  ferramentas: { nome: string; titulo: string; tipo: string }[];
  competencias: { nome: string; titulo: string; descricao: string }[];
  sugestoes: { titulo: string; descricao: string; pergunta: string; icone: string }[];
  limites: { arquivoMaxBytes: number; anexosPorMensagem: number; formatosAceitos: string[] };
}

export interface CriarConversaRequest {
  titulo?: string;
  contexto?: AssistenteContexto;
}

export interface EnviarMensagemRequest {
  clientMessageId: string;
  conteudo: string;
  arquivos?: string[];
  contexto?: AssistenteContexto;
}

// ---------------------------------------------------------------------------------------------
// Cartões de cadastro do Copiloto: o agente prepara, o usuário revisa e confirma.

export type TipoCampoCadastro =
  | 'TEXTO' | 'TEXTO_LONGO' | 'INTEIRO' | 'DECIMAL' | 'MOEDA' | 'DATA' | 'DATA_HORA' | 'HORA'
  | 'BOOLEANO' | 'OPCAO' | 'REFERENCIA' | 'LISTA' | 'OBJETO' | 'UUID' | 'EMAIL' | 'CPF' | 'CNPJ' | 'SENHA';

export interface OpcaoCadastro {
  valor: string;
  rotulo: string;
  detalhe?: string;
}

export interface CampoCadastro {
  caminho: string;
  rotulo: string;
  tipo: TipoCampoCadastro;
  obrigatorio: boolean;
  multiplo?: boolean;
  referencia?: string;
  descricao?: string;
  tamanhoMaximo?: number;
  valor?: unknown;
  exibicao?: string;
  detalhe?: string;
  problema?: string;
  alterado?: boolean;
  candidatos?: OpcaoCadastro[];
  opcoes?: OpcaoCadastro[];
  /** Lista de objetos: cada item é a lista dos seus campos. */
  itens?: CampoCadastro[][];
  /** Campos de um item novo da lista. */
  modelo?: CampoCadastro[];
  /** Campos de um bloco (formulário dinâmico). */
  grupo?: CampoCadastro[];
  /** Valores de um campo múltiplo (vários colaboradores, várias equipes). */
  valores?: { valor?: unknown; exibicao?: string; problema?: string; candidatos?: OpcaoCadastro[] }[];
}

export interface RegistroCadastro {
  indice: number;
  titulo: string;
  pronto: boolean;
  pendencias: number;
  campos: CampoCadastro[];
  avisos?: string[];
}

export interface PreviaCadastro {
  tipo: 'cadastro';
  chave: string;
  operacao: string;
  modo: 'CRIAR' | 'EDITAR';
  modulo: string;
  titulo: string;
  registro: string;
  permiteLote?: boolean;
  registros: RegistroCadastro[];
  pronta: boolean;
  total: number;
  prontos: number;
}

export interface RegistroGravado {
  indice: number;
  titulo: string;
  status: 'GRAVADO' | 'FALHOU';
  id?: string;
  rotulo?: string;
  destino?: string;
  parametros?: Record<string, string>;
  mensagem?: string;
}

export interface ResultadoCadastro {
  tipo: 'cadastro_resultado';
  operacao: string;
  modo: 'CRIAR' | 'EDITAR';
  registro: string;
  gravados: number;
  falhas: number;
  registros: RegistroGravado[];
}

export type StatusAcaoCartao = 'PENDENTE' | 'CONFIRMADA' | 'FALHOU' | 'REJEITADA' | 'EXPIRADA';

export interface AcaoCartao {
  acaoId: string;
  mensagemId?: string;
  ferramenta: string;
  status: StatusAcaoCartao;
  previa?: PreviaCadastro | null;
  resultado?: ResultadoCadastro | null;
  /** Só na resposta da confirmação (senhas provisórias); nunca volta depois. */
  sigiloso?: { senhas?: { registro: string; rotulo: string; valor: string }[] } | null;
  mensagem?: string | null;
  decididaEm?: string | null;
}

/** Ajuste do cartão: troca um valor, inclui item (caminho "itens.#") ou remove registro/item. */
export interface AlteracaoCartao {
  registro: number;
  caminho?: string;
  valor?: unknown;
  remover?: boolean;
}
